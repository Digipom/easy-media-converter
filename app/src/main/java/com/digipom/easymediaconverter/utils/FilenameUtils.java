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

import androidx.annotation.NonNull;

import java.util.Locale;

public class FilenameUtils {
    @NonNull
    static String getExtension(@NonNull String fileName) {
        final int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return "";
        } else {
            return fileName.substring(lastIndexOfDot + 1);
        }
    }

    @NonNull
    public static String getCanonicalExtension(@NonNull String fileName) {
        return getExtension(fileName).toLowerCase(Locale.US);
    }

    @NonNull
    public static String replaceExtension(@NonNull String fileName, @NonNull String newExtension) {
        final String fileNameWithoutExtension = getFilenameWithoutExtension(fileName);
        return fileNameWithoutExtension + "." + newExtension;
    }

    @NonNull
    static String getFilenameWithoutExtension(@NonNull String fileName) {
        final int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return fileName;
        } else {
            return fileName.substring(0, lastIndexOfDot);
        }
    }

    @NonNull
    public static String appendToFilename(@NonNull String fileName, @NonNull String append) {
        final String fileNameWithoutExtension = getFilenameWithoutExtension(fileName);
        final String extension = getExtension(fileName);

        if (!extension.isEmpty()) {
            return fileNameWithoutExtension + append + "." + extension;
        } else {
            return fileNameWithoutExtension + append;
        }
    }

    @NonNull
    public static String getAppendedFilename(@NonNull String baseName,
                                             @NonNull String append,
                                             @NonNull String extension) {
        final int baseNameLength = baseName.length();
        final int substituteLength = append.length();

        if (baseNameLength + substituteLength > 240)
            baseName = baseName.substring(0, 240 - substituteLength);

        return baseName + append + (extension.isEmpty() ? "" : "." + extension);
    }
}
