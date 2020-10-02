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
import androidx.annotation.Nullable;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.File;
import java.io.IOException;

import static com.digipom.easymediaconverter.utils.FilenameUtils.getExtension;
import static com.digipom.easymediaconverter.utils.FilenameUtils.getFilenameWithoutExtension;

public class FileUtils {
    public static File createTempFile(@NonNull String fileName, @NonNull File dir) throws IOException {
        return File.createTempFile(getFilenameWithoutExtension(fileName), "." + getExtension(fileName), dir);
    }

    public static void deleteChildren(@NonNull File dir) {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (File child : files) {
                recursiveDelete(child);
            }
        }
    }

    private static void recursiveDelete(@Nullable File item) {
        if (item == null) {
            return;
        }

        Logger.v("Deleting " + item);

        if (item.isDirectory()) {
            final File[] files = item.listFiles();
            if (files != null) {
                for (File child : files) {
                    recursiveDelete(child);
                }
            }
        }

        if (item.delete()) {
            Logger.v("Deleted " + item);
        } else {
            Logger.v("Could not delete " + item);
        }
    }
}
