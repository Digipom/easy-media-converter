/*
 * Copyright (c) 2020 Kevin Brothaler. All rights reserved.
 *
 * https://github.com/Digipom/easy-media-converter
 *
 * This file is part of Easy Media Converter.
 *
 * Easy Media Converter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Easy Media Converter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Easy Media Converter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.digipom.easymediaconverter.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.Objects;

import static android.content.Context.ACTIVITY_SERVICE;

public class BatteryUtils {
    public static void logBatteryRestrictions(@NonNull Context context, boolean showVerboseLogs) {
        logIfIsBackgroundRestricted(context, showVerboseLogs);
        logBatteryOptimizations(context, showVerboseLogs);
    }

    private static void logIfIsBackgroundRestricted(@NonNull Context context, boolean showVerboseLogs) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                final ActivityManager am = Objects.requireNonNull((ActivityManager) context.getSystemService(ACTIVITY_SERVICE));
                if (am.isBackgroundRestricted()) {
                    Logger.w("Application is background restricted");
                } else if (showVerboseLogs) {
                    Logger.v("User has not enforced background restrictions for this app.");
                }
            }
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    private static void logBatteryOptimizations(@NonNull Context context, boolean showVerboseLogs) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                final PowerManager pm = Objects.requireNonNull((PowerManager) context.getSystemService(Context.POWER_SERVICE));
                if (!pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                    Logger.d("Battery optimization is enabled (normally this won't cause an issue).");
                } else if (showVerboseLogs) {
                    Logger.d("App is on the device power whitelist.");
                }
            }
        } catch (Exception e) {
            Logger.w(e);
        }
    }
}
