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
 * Copyright (c) 2026 Sui Contributors
 */

package rikka.sui.util;

import static rikka.sui.server.ServerConstants.LOGGER;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility class for launching applications across different Android versions using hidden reflection APIs.
 */
public class AppLaunchUtils {

    /**
     * Dynamically restarts a target application by retrieving explicitly its ComponentName and invoking
     * IActivityManager/IActivityTaskManager.startActivityAsUser.
     *
     * @param packageName the application to start.
     * @param userId      the user handle id.
     */
    public static void startAppAsUser(String packageName, int userId) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            // Dynamically resolve the explicit ComponentName to avoid AMS returning -91 (implicit intent not
            // resolved/aborted)
            try {
                Object iPackageManager = Class.forName("android.app.ActivityThread")
                        .getMethod("getPackageManager")
                        .invoke(null);
                Method resolveIntent = null;
                for (Method m : iPackageManager.getClass().getMethods()) {
                    if ("resolveIntent".equals(m.getName()) && m.getParameterCount() >= 4) {
                        resolveIntent = m;
                        break;
                    }
                }
                if (resolveIntent != null) {
                    Object resolveInfo = null;
                    if (resolveIntent.getParameterCount() == 4) {
                        // resolveIntent(Intent intent, String resolvedType, int flags, int userId)
                        resolveInfo = resolveIntent.invoke(iPackageManager, intent, null, 0, userId);
                    } else if (resolveIntent.getParameterCount() == 5) {
                        // Android 13+: resolveIntent(Intent intent, String resolvedType, long flags, int userId)
                        resolveInfo = resolveIntent.invoke(iPackageManager, intent, null, 0L, userId);
                    }

                    if (resolveInfo != null) {
                        Field aiField = resolveInfo.getClass().getField("activityInfo");
                        Object activityInfo = aiField.get(resolveInfo);
                        if (activityInfo != null) {
                            String aiPackageName = (String) activityInfo
                                    .getClass()
                                    .getField("packageName")
                                    .get(activityInfo);
                            String aiName = (String)
                                    activityInfo.getClass().getField("name").get(activityInfo);
                            intent.setComponent(new ComponentName(aiPackageName, aiName));
                            LOGGER.i("Resolved explicit ComponentName: %s/%s", aiPackageName, aiName);
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.w(e, "Failed to resolve explicit ComponentName for %s", packageName);
            }

            Object iActivityManager;
            if (Build.VERSION.SDK_INT >= 29) {
                iActivityManager = Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
            } else {
                iActivityManager = Class.forName("android.app.ActivityManager")
                        .getMethod("getService")
                        .invoke(null);
            }

            Method targetMethod = null;
            for (Method m : iActivityManager.getClass().getMethods()) {
                if ("startActivityAsUser".equals(m.getName())) {
                    targetMethod = m;
                    break;
                }
            }

            if (targetMethod != null) {
                Class<?>[] paramTypes = targetMethod.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                boolean packageSet = false;

                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> pType = paramTypes[i];
                    if (pType == Intent.class) {
                        args[i] = intent;
                    } else if (pType == int.class) {
                        if (i == paramTypes.length - 1) { // userId is always the very last int parameter
                            args[i] = userId;
                        } else {
                            args[i] = 0; // requestCode, flags
                        }
                    } else if (pType == String.class) {
                        if (!packageSet) {
                            // Spoofing the calling package to bypass AppsFilter restrictions and null assertions
                            args[i] = "com.android.shell";
                            packageSet = true;
                        } else {
                            args[i] = null; // resolvedType, resultWho, callingFeatureId
                        }
                    } else {
                        args[i] = null; // IApplicationThread, IBinder, ProfilerInfo, Bundle
                    }
                }
                Object res = targetMethod.invoke(iActivityManager, args);
                LOGGER.i("startActivityAsUser returned: " + res);
            } else {
                LOGGER.w("startActivityAsUser method not found on IActivityManager/IActivityTaskManager");
            }
        } catch (Throwable e) {
            LOGGER.w(e, "Failed to invoke startActivityAsUser dynamically for package %s", packageName);
        }
    }
}
