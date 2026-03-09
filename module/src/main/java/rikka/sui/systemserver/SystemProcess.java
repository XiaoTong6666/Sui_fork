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

package rikka.sui.systemserver;

import static rikka.sui.systemserver.SystemServerConstants.LOGGER;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import java.util.Arrays;
import rikka.sui.util.ParcelUtils;

public final class SystemProcess {

    private static final BridgeService SERVICE = new BridgeService();
    private static volatile int[] hiddenUids = new int[0];

    private static boolean execActivityTransaction(
            @NonNull Binder binder, int code, Parcel data, Parcel reply, int flags) {
        return SERVICE.onTransact(code, data, reply, flags);
    }

    public static boolean execTransact(@NonNull Binder binder, int code, long dataObj, long replyObj, int flags) {
        if (!SERVICE.isServiceTransaction(code)) {
            return false;
        }

        Parcel data = ParcelUtils.fromNativePointer(dataObj);
        Parcel reply = ParcelUtils.fromNativePointer(replyObj);

        if (data == null) {
            return false;
        }

        boolean res;
        try {
            res = execActivityTransaction(binder, code, data, reply, flags);
        } catch (Exception e) {
            if ((flags & IBinder.FLAG_ONEWAY) != 0) {
                LOGGER.w(e, "Caught a Exception from the binder stub implementation.");
            } else {
                if (reply != null) {
                    reply.setDataPosition(0);
                    reply.writeException(e);
                }
            }
            res = false;
        } finally {
            data.setDataPosition(0);
            if (reply != null) reply.setDataPosition(0);
        }

        if (res) {
            data.recycle();
            if (reply != null) reply.recycle();
        }

        return res;
    }

    public static void main(String[] args) {
        LOGGER.d("main: %s", Arrays.toString(args));

        try {
            moe.shizuku.server.IShizukuService service = BridgeService.get();
            if (service != null) {
                int[] uids = service.getHiddenUids();
                LOGGER.d("syncing %d hidden uids to native and Java cache", uids.length);
                updateHiddenUids(uids);
            } else {
                LOGGER.w("IShizukuService is null in SystemProcess.main");
            }
        } catch (Throwable e) {
            LOGGER.w(e, "failed to sync hidden uids");
        }
    }

    public static void updateHiddenUids(int[] uids) {
        if (uids == null) uids = new int[0];
        Arrays.sort(uids);
        hiddenUids = uids;
        LOGGER.d("syncing %d hidden uids to native", uids.length);
        setHiddenUids(uids);
    }

    public static boolean isHidden(int uid) {
        int[] uids = hiddenUids;
        return Arrays.binarySearch(uids, uid) >= 0;
    }

    @Keep
    @SuppressWarnings("JavaJniMissingFunction")
    private static native void setHiddenUids(int[] uids);
}
