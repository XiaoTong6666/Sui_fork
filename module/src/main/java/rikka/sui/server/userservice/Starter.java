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

package rikka.sui.server.userservice;

import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Pair;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.server.UserService;

public class Starter {

    private static final String TAG = "SuiUserServiceStarter";
    private static final int BRIDGE_TRANSACTION_CODE = ('_' << 24) | ('S' << 16) | ('U' << 8) | 'I';
    private static final String BRIDGE_SERVICE_DESCRIPTOR = "android.app.IActivityManager";
    private static final String BRIDGE_SERVICE_NAME = "activity";
    private static final int BRIDGE_ACTION_GET_BINDER = 2;
    private static final int SERVER_UID_ROOT = 0;
    private static final int SERVER_UID_SHELL = 2000;

    private static int parseServerUid(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--server-uid=")) {
                try {
                    int uid = Integer.parseInt(arg.substring(13));
                    if (uid == SERVER_UID_ROOT || uid == SERVER_UID_SHELL) {
                        return uid;
                    }
                    Log.w(TAG, "Unsupported --server-uid=" + uid);
                    return -1;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid --server-uid argument", e);
                    return -1;
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            if (Looper.getMainLooper() == null) {
                prepareMainLooper();
            } else {
                Looper.prepare();
            }
        }

        IBinder service;
        String token;
        int serverUid = parseServerUid(args);

        UserService.setTag(TAG);
        Pair<IBinder, String> result = UserService.create(args);

        if (result == null) {
            System.exit(1);
            return;
        }

        service = result.first;
        token = result.second;

        if (!sendBinder(service, token, serverUid)) {
            System.exit(1);
        }

        Looper.loop();
        System.exit(0);

        Log.i(TAG, "service exited");
    }

    @SuppressWarnings("deprecation")
    private static void prepareMainLooper() {
        Looper.prepareMainLooper();
    }

    private static IBinder requestBinderFromBridge(int serverUid) {
        IBinder binder = ServiceManager.getService(BRIDGE_SERVICE_NAME);
        if (binder == null) return null;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BRIDGE_SERVICE_DESCRIPTOR);
            data.writeInt(BRIDGE_ACTION_GET_BINDER);
            if (serverUid == SERVER_UID_ROOT || serverUid == SERVER_UID_SHELL) {
                data.writeInt(serverUid);
            }
            binder.transact(BRIDGE_TRANSACTION_CODE, data, reply, 0);
            reply.readException();
            IBinder received = reply.readStrongBinder();
            if (received != null) {
                return received;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            data.recycle();
            reply.recycle();
        }
        return null;
    }

    private static boolean sendBinder(IBinder binder, String token, int serverUid) {
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(requestBinderFromBridge(serverUid));
        if (shizukuService == null) {
            return false;
        }

        Bundle data = new Bundle();
        data.putString(USER_SERVICE_ARG_TOKEN, token);
        try {
            shizukuService.attachUserService(binder, data);
        } catch (Throwable e) {
            Log.w(TAG, Log.getStackTraceString(e));
            return false;
        }
        return true;
    }
}
