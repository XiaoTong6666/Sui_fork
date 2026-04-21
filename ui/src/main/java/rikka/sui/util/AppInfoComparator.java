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

import java.util.Comparator;
import rikka.sui.model.AppInfo;
import rikka.sui.server.SuiConfig;

public class AppInfoComparator implements Comparator<AppInfo> {

    private final AppNameComparator<AppInfo> appNameComparator = new AppNameComparator<>(new AppInfoProvider());

    private static int getSortOrder(int flags) {
        if ((flags & SuiConfig.FLAG_ALLOWED) != 0) {
            return 0;
        }
        if ((flags & SuiConfig.FLAG_ALLOWED_SHELL) != 0) {
            return 1;
        }
        if ((flags & SuiConfig.FLAG_DENIED) != 0) {
            return 2;
        }
        if ((flags & SuiConfig.FLAG_HIDDEN) != 0) {
            return 3;
        }
        return 4;
    }

    @Override
    public int compare(AppInfo o1, AppInfo o2) {
        int o1f = getSortOrder(o1.flags);
        int o2f = getSortOrder(o2.flags);
        int c = Integer.compare(o1f, o2f);
        if (c == 0) return appNameComparator.compare(o1, o2);
        return c;
    }

    private static class AppInfoProvider implements AppNameComparator.InfoProvider<AppInfo> {

        @Override
        public CharSequence getTitle(AppInfo item) {
            if (item.label != null) return item.label;
            return item.packageInfo.packageName;
        }

        @Override
        public String getPackageName(AppInfo item) {
            return item.packageInfo.packageName;
        }

        @Override
        public int getUserId(AppInfo item) {
            return UserHandleCompat.getUserId(item.packageInfo.applicationInfo.uid);
        }
    }
}
