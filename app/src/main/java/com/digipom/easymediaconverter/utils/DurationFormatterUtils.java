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

import android.text.format.DateUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Formatter;
import java.util.Locale;

public class DurationFormatterUtils {
    public static void formatDurationWithTenths(@NonNull StringBuilder builder, @NonNull TextView textView, long durationMs) {
        final String formattedDuration = setFormattedDurationWithTenths(builder, textView, durationMs);
        textView.setContentDescription(getTimeDurationForAccessibility(formattedDuration));
    }

    public static void formatDurationWithTenths(@NonNull StringBuilder builder, @NonNull TextView textView, long durationMs,
                                                @NonNull String contentDescriptionFormat) {
        final String formattedDuration = setFormattedDurationWithTenths(builder, textView, durationMs);
        textView.setContentDescription(String.format(contentDescriptionFormat, getTimeDurationForAccessibility(formattedDuration)));
    }

    @NonNull
    private static String setFormattedDurationWithTenths(@NonNull StringBuilder builder, @NonNull TextView textView, long durationMs) {
        final String formattedDuration = getFormattedDuration(builder, durationMs);
        final String formattedDurationMs = getFormattedDurationWithTenths(builder, durationMs);
        textView.setText(formattedDurationMs);
        return formattedDuration;
    }

    public static void formatDuration(@NonNull StringBuilder builder, @NonNull TextView textView, long durationMs) {
        final String formattedElapsedTime = getFormattedDuration(builder, durationMs);
        textView.setText(formattedElapsedTime);
        textView.setContentDescription(getTimeDurationForAccessibility(formattedElapsedTime));
    }

    public static void formatDuration(@NonNull StringBuilder builder, @NonNull TextView textView, long durationMs,
                                      @NonNull String contentDescriptionFormat) {
        final String formattedElapsedTime = getFormattedDuration(builder, durationMs);
        textView.setText(formattedElapsedTime);
        textView.setContentDescription(String.format(contentDescriptionFormat,
                getTimeDurationForAccessibility(formattedElapsedTime)));
    }

    @NonNull
    public static String getFormattedDuration(@NonNull StringBuilder stringBuilder, long durationMs) {
        return DateUtils.formatElapsedTime(stringBuilder, durationMs / 1000);
    }

    @NonNull
    public static String getFormattedDurationWithTenths(@NonNull StringBuilder stringBuilder, long durationMs) {
        DateUtils.formatElapsedTime(stringBuilder, durationMs / 1000);
        Formatter f = new Formatter(stringBuilder, Locale.getDefault());
        f.format(".%01d", (durationMs / 100) % 10);
        return stringBuilder.toString();
    }

    @NonNull
    public static String getTimeDurationForAccessibility(@NonNull String formattedDuration) {
        if (formattedDuration.contains(":") && formattedDuration.length() == 5) {
            // Heuristic for a format like "00:00".
            return "00:" + formattedDuration;
        } else {
            return formattedDuration;
        }
    }
}
