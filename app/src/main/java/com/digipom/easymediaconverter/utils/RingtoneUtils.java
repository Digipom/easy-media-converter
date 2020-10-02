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
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

public class RingtoneUtils {
    public static void setAsRingtone(@NonNull Context context, @NonNull Uri uri,
                                     int ringtoneType) {
        RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri);
        // Double-check the ring-tone was actually set.
        final Uri currentRingtone = RingtoneManager.getActualDefaultRingtoneUri(context, ringtoneType);

        if (currentRingtone != null && currentRingtone.equals(uri)) {
            Logger.v("Set " + uri + " as type: " + getLoggerStringForType(ringtoneType));
        } else {
            Logger.w("We set " + uri + " as type: " + getLoggerStringForType(ringtoneType)
                    + ", but the system still reports " + currentRingtone
                    + " as the saved sound.");
        }
    }

    private static String getLoggerStringForType(int ringtoneType) {
        if (ringtoneType == RingtoneManager.TYPE_NOTIFICATION) {
            return "notification";
        } else if (ringtoneType == RingtoneManager.TYPE_ALARM) {
            return "alarm";
        } else {
            return "ringtone";
        }
    }
}
