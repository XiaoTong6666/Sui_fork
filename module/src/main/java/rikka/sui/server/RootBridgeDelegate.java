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

package rikka.sui.server;

import static rikka.sui.server.ServerConstants.LOGGER;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ServiceManager;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.server.ClientRecord;
import rikka.sui.util.BridgeConstants;

public class RootBridgeDelegate {

    public static void delegatePermissionConfirmationToRoot(
            SuiClientManager clientManager, int requestCode, String packageName, int callingUid, int callingPid) {

        Binder callback = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == 1) {
                    boolean allowed = data.readInt() != 0;
                    LOGGER.i("Received delegated permission result for uid=%d: allowed=%s", callingUid, allowed);
                    ClientRecord record = clientManager.findClient(callingUid, callingPid);
                    if (record != null) {
                        record.allowed = allowed;
                        record.dispatchRequestPermissionResult(requestCode, allowed);
                    }
                    return true;
                }
                return false;
            }
        };

        Parcel data = null;
        Parcel reply = null;
        try {
            IBinder bridgeService = ServiceManager.getService(BridgeConstants.SERVICE_NAME);
            if (bridgeService == null) return;
            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeInterfaceToken(BridgeConstants.SERVICE_DESCRIPTOR);
            data.writeInt(BridgeConstants.ACTION_GET_BINDER);
            data.writeInt(BridgeConstants.SERVER_UID_ROOT);
            bridgeService.transact(BridgeConstants.TRANSACTION_CODE, data, reply, 0);
            reply.readException();
            IBinder rootBinder = reply.readStrongBinder();
            data.recycle();
            data = null;
            reply.recycle();
            reply = null;

            if (rootBinder != null) {
                data = Parcel.obtain();
                data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR);
                data.writeInt(requestCode);
                data.writeString(packageName);
                data.writeInt(callingUid);
                data.writeInt(callingPid);
                data.writeStrongBinder(callback);
                rootBinder.transact(
                        ServerConstants.BINDER_TRANSACTION_requestPermissionFromShell, data, null, IBinder.FLAG_ONEWAY);
                data.recycle();
                data = null;
            } else {
                LOGGER.e("root binder is null, cannot delegate.");
            }
        } catch (Throwable e) {
            LOGGER.e(e, "delegatePermissionConfirmationToRoot");
        } finally {
            if (data != null) {
                data.recycle();
            }
            if (reply != null) {
                reply.recycle();
            }
        }
    }
}
