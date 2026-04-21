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

package rikka.sui.util;

import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import androidx.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.ShizukuApiConstants;
import rikka.sui.server.ServerConstants;

public class BridgeServiceClient {

    private static volatile IBinder binder;
    private static volatile IShizukuService service;
    private static volatile String shortcutTokenCache;
    private static final Object shortcutTokenLock = new Object();
    private static FutureTask<String> shortcutTokenTask;
    private static volatile int shortcutTokenGeneration;

    private static final int SHORTCUT_TOKEN_FETCH_RETRY_COUNT = 5;
    private static final long SHORTCUT_TOKEN_FETCH_RETRY_DELAY_MS = 400;
    private static final long SHORTCUT_TOKEN_MAIN_THREAD_WAIT_MS = 2000;

    private static final int BRIDGE_TRANSACTION_CODE = ('_' << 24) | ('S' << 16) | ('U' << 8) | 'I';
    private static final String BRIDGE_SERVICE_DESCRIPTOR = "android.app.IActivityManager";
    private static final String BRIDGE_SERVICE_NAME = "activity";
    private static final int BRIDGE_ACTION_GET_BINDER = 2;

    private static final IBinder.DeathRecipient DEATH_RECIPIENT = () -> {
        binder = null;
        service = null;
        invalidateShortcutToken();
    };

    private static IBinder requestBinderFromBridge() {
        IBinder binder = ServiceManager.getService(BRIDGE_SERVICE_NAME);
        if (binder == null) return null;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BRIDGE_SERVICE_DESCRIPTOR);
            data.writeInt(BRIDGE_ACTION_GET_BINDER);
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

    protected static void setBinder(@Nullable IBinder binder) {
        if (BridgeServiceClient.binder == binder) return;

        if (BridgeServiceClient.binder != null) {
            BridgeServiceClient.binder.unlinkToDeath(DEATH_RECIPIENT, 0);
        }

        if (binder == null) {
            BridgeServiceClient.binder = null;
            BridgeServiceClient.service = null;
        } else {
            BridgeServiceClient.binder = binder;
            BridgeServiceClient.service = IShizukuService.Stub.asInterface(binder);

            try {
                BridgeServiceClient.binder.linkToDeath(DEATH_RECIPIENT, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void invalidateShortcutToken() {
        synchronized (shortcutTokenLock) {
            shortcutTokenGeneration++;
            shortcutTokenCache = null;
            shortcutTokenTask = null;
        }
    }

    public static IShizukuService getService() {
        if (service == null) {
            setBinder(requestBinderFromBridge());
        }
        return service;
    }

    public static ParcelFileDescriptor openApk() {
        ParcelFileDescriptor result;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService");
            try {
                getService().asBinder().transact(ServerConstants.BINDER_TRANSACTION_openApk, data, reply, 0);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            reply.readException();
            if (reply.readInt() != 0) {
                result = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
            } else {
                result = null;
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
        return result;
    }

    public static void prefetchShortcutToken() {
        if (shortcutTokenCache != null) {
            return;
        }
        startShortcutTokenFetch();
    }

    public static String getShortcutToken() {
        if (shortcutTokenCache != null) {
            return shortcutTokenCache;
        }

        FutureTask<String> task = startShortcutTokenFetch();
        if (task == null) {
            return shortcutTokenCache;
        }

        try {
            String token;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                token = task.get(SHORTCUT_TOKEN_MAIN_THREAD_WAIT_MS, TimeUnit.MILLISECONDS);
            } else {
                token = task.get();
            }
            if (token != null) {
                shortcutTokenCache = token;
            }
            return token;
        } catch (TimeoutException e) {
            return shortcutTokenCache;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return shortcutTokenCache;
        }
    }

    private static FutureTask<String> startShortcutTokenFetch() {
        synchronized (shortcutTokenLock) {
            if (shortcutTokenCache != null) {
                return null;
            }

            if (shortcutTokenTask != null && !shortcutTokenTask.isDone()) {
                return shortcutTokenTask;
            }

            final int generation = shortcutTokenGeneration;
            shortcutTokenTask = new FutureTask<>(() -> fetchShortcutTokenWithRetry(generation));
            Thread thread = new Thread(shortcutTokenTask, "SuiShortcutToken");
            thread.setDaemon(true);
            thread.start();
            return shortcutTokenTask;
        }
    }

    private static String fetchShortcutTokenWithRetry(int generation) {
        for (int attempt = 0; attempt < SHORTCUT_TOKEN_FETCH_RETRY_COUNT; attempt++) {
            if (generation != shortcutTokenGeneration) {
                return null;
            }

            String token = fetchShortcutTokenOnce();
            if (token != null) {
                synchronized (shortcutTokenLock) {
                    if (generation != shortcutTokenGeneration) {
                        return null;
                    }
                    shortcutTokenCache = token;
                }
                return token;
            }

            if (attempt == SHORTCUT_TOKEN_FETCH_RETRY_COUNT - 1) {
                break;
            }

            try {
                Thread.sleep(SHORTCUT_TOKEN_FETCH_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String fetchShortcutTokenOnce() {
        IShizukuService service = getService();
        if (service == null) {
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR);
            service.asBinder().transact(ServerConstants.BINDER_TRANSACTION_getShortcutToken, data, reply, 0);
            reply.readException();
            return reply.readString();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
