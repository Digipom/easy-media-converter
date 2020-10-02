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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.Arrays;

import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.text.format.DateUtils.FORMAT_SHOW_TIME;
import static android.text.format.DateUtils.FORMAT_SHOW_YEAR;

public final class SystemUtils {
    @NonNull
    public static String getSystemAndPackageInformation(@NonNull Context context) {
        final StringBuilder builder = new StringBuilder();

        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);

            builder.append("Version: ").append(packageInfo.versionName).append('\n')
                    .append("Version code: ").append(packageInfo.versionCode).append('\n')
                    .append("Package name: ").append(context.getPackageName()).append('\n')
                    .append("Package installer: ").append(pm.getInstallerPackageName(context.getPackageName())).append('\n')
                    .append("First installed on: ").append(DateUtils.formatDateTime(context, packageInfo.firstInstallTime,
                    FORMAT_SHOW_DATE | FORMAT_SHOW_TIME | FORMAT_SHOW_YEAR)).append('\n')
                    .append("Last update time: ").append(DateUtils.formatDateTime(context, packageInfo.lastUpdateTime,
                    FORMAT_SHOW_DATE | FORMAT_SHOW_TIME | FORMAT_SHOW_YEAR)).append("\n\n");


            builder.append("\nBoard: ").append(Build.BOARD).append("\n");
            builder.append("Brand: ").append(Build.BRAND).append("\n");
            builder.append("CPU ABI: ").append(Arrays.asList(Build.SUPPORTED_ABIS)).append("\n");
            builder.append("Device: ").append(Build.DEVICE).append("\n");
            builder.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
            builder.append("Model: ").append(Build.MODEL).append("\n");
            builder.append("Product: ").append(Build.PRODUCT).append("\n");
            builder.append("Version codename: ").append(Build.VERSION.CODENAME).append("\n");
            builder.append("Version incremental: ").append(Build.VERSION.INCREMENTAL).append("\n");
            builder.append("Version release: ").append(Build.VERSION.RELEASE).append("\n");
            builder.append("Version code: ").append(Build.VERSION.SDK_INT).append("\n");
        } catch (Exception e) {
            Logger.w(e);
        }

        builder.append("\n\n");

        return builder.toString();
    }
}
