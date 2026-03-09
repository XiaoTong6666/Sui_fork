/*
 * This file is part of Sui.
 *
 * Sui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sui.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2021-2026 Sui Contributors
 */

#include <cstdio>
#include <cstring>
#include <chrono>
#include <fcntl.h>
#include <unistd.h>
#include <sys/vfs.h>
#include <sys/stat.h>
#include <dirent.h>
#include <jni.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/uio.h>
#include <mntent.h>
#include <sys/mount.h>
#include <sys/sendfile.h>
#include <dlfcn.h>
#include <cinttypes>
#include <string>
#include <vector>
#include <unordered_set>
#include <shared_mutex>

#include "android.h"
#include "logging.h"
#include "misc.h"
#include "dex_file.h"
#include "bridge_service.h"
#include "binder_hook.h"
#include "config.h"

typedef uid_t (*AIBinder_getCallingUid_t)();
typedef pid_t (*AIBinder_getCallingPid_t)();

namespace SystemServer {

static jclass mainClass = nullptr;
static jmethodID my_execTransactMethodID;

static jclass javaBinderClass = nullptr;
static jmethodID getCallingUidMethodID = nullptr;

static jint startShortcutTransactionCode = -1;

static std::unordered_set<uid_t> hiddenUids;
static std::shared_mutex hiddenUidsMutex;

static void setHiddenUids(JNIEnv* env, jclass, jintArray uids) {
    std::unique_lock lock(hiddenUidsMutex);
    if (!uids) {
        hiddenUids.clear();
        return;
    }

    jsize len = env->GetArrayLength(uids);
    jint* body = env->GetIntArrayElements(uids, nullptr);
    if (body == nullptr) {
        return;
    }

    hiddenUids.clear();
    for (jsize i = 0; i < len; i++) {
        hiddenUids.insert((uid_t)body[i]);
    }
    env->ReleaseIntArrayElements(uids, body, JNI_ABORT);
    LOGD("updated %zu hidden uids", hiddenUids.size());
}

static bool installDex(JNIEnv* env, Dex* dexFile) {
    if (android_get_device_api_level() < 27) {
        dexFile->setPre26Paths("/data/system/sui/" DEX_NAME, "/data/system/sui/oat");
    }
    dexFile->createClassLoader(env);

    mainClass = dexFile->findClass(env, SYSTEM_PROCESS_CLASSNAME);
    if (!mainClass) {
        LOGE("unable to find main class");
        return false;
    }
    mainClass = (jclass)env->NewGlobalRef(mainClass);

    JNINativeMethod methods[] = {
        {"setHiddenUids", "([I)V", (void*)setHiddenUids},
    };

    if (env->RegisterNatives(mainClass, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        LOGE("unable to register natives");
        return false;
    }

    auto mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("unable to find main method");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    my_execTransactMethodID =
        env->GetStaticMethodID(mainClass, "execTransact", "(Landroid/os/Binder;IJJI)Z");
    if (!my_execTransactMethodID) {
        LOGE("unable to find execTransact");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    jclass binderCls = env->FindClass("android/os/Binder");
    if (binderCls) {
        javaBinderClass = (jclass)env->NewGlobalRef(binderCls);
        getCallingUidMethodID = env->GetStaticMethodID(javaBinderClass, "getCallingUid", "()I");
        if (!getCallingUidMethodID) {
            env->ExceptionClear();
            LOGE("unable to find Binder.getCallingUid");
        }
        env->DeleteLocalRef(binderCls);
    } else {
        env->ExceptionClear();
        LOGE("unable to find android.os.Binder class");
    }

    auto args = env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);

    env->CallStaticVoidMethod(mainClass, mainMethod, args);
    if (env->ExceptionCheck()) {
        LOGE("unable to call main method");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    return true;
}

/*
 * return true = consumed
 */
static bool ExecTransact(jboolean* res, JNIEnv* env, jobject obj, va_list args) {
    jint code;
    jlong dataObj;
    jlong replyObj;
    jint flags;

    va_list copy;
    va_copy(copy, args);
    code = va_arg(copy, jint);
    dataObj = va_arg(copy, jlong);
    replyObj = va_arg(copy, jlong);
    flags = va_arg(copy, jint);
    va_end(copy);

    if (code == BridgeService::BRIDGE_TRANSACTION_CODE) {
        static void* libbinder_ndk = dlopen("libbinder_ndk.so", RTLD_NOW);
        static AIBinder_getCallingUid_t get_uid_ndk = nullptr;

        if (libbinder_ndk && !get_uid_ndk) {
            get_uid_ndk = (AIBinder_getCallingUid_t)dlsym(libbinder_ndk, "AIBinder_getCallingUid");
        }

        uid_t uid = -1;

        if (get_uid_ndk) {
            uid = get_uid_ndk();
        } else if (getCallingUidMethodID) {
            uid = (uid_t)env->CallStaticIntMethod(javaBinderClass, getCallingUidMethodID);
        }

        if (uid != (uid_t)-1 && uid >= 10000) {
            uid_t app_id = uid % 100000;

            if (app_id >= 99000 && app_id <= 99999) {
                return false;
            }

            std::shared_lock lock(hiddenUidsMutex);
            if (hiddenUids.find(uid) != hiddenUids.end()) {
                return false;
            }
        }

        *res = env->CallStaticBooleanMethod(mainClass, my_execTransactMethodID, obj, code, dataObj,
                                            replyObj, flags);
        return true;
    } /* else if (startShortcutTransactionCode != -1 && code == startShortcutTransactionCode) {
         *res = env->CallStaticBooleanMethod(mainClass, my_execTransactMethodID, obj, code, dataObj,
     replyObj, flags); if (*res) return true;
     }*/

    return false;
}

void main(JNIEnv* env, Dex* dexFile) {
    if (!dexFile->valid()) {
        LOGE("no dex");
        return;
    }

    LOGV("main: system server");

    LOGV("install dex");

    if (!installDex(env, dexFile)) {
        LOGE("can't install dex");
        return;
    }

    LOGV("install dex finished");

    JavaVM* javaVm;
    env->GetJavaVM(&javaVm);

    BinderHook::Install(javaVm, env, ExecTransact);

    /*if (android_get_device_api_level() >= 26) {
        jclass launcherAppsClass;
        jfieldID startShortcutId;

        launcherAppsClass = env->FindClass("android/content/pm/ILauncherApps$Stub");
        if (!launcherAppsClass) goto clean;
        startShortcutId = env->GetStaticFieldID(launcherAppsClass, "TRANSACTION_startShortcut",
    "I"); if (!startShortcutId) goto clean; startShortcutTransactionCode =
    env->GetStaticIntField(launcherAppsClass, startShortcutId);

        clean:
        env->ExceptionClear();
    }*/
}
}  // namespace SystemServer
