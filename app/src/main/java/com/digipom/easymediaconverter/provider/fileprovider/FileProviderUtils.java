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
package com.digipom.easymediaconverter.provider.fileprovider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileProviderUtils {
    @NonNull
    private static String getAuthority(@NonNull Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    @NonNull
    private static File getTempLogDir(@NonNull Context context) {
        return new File(context.getCacheDir(), "tmp_logs");
    }

    @NonNull
    public static Uri getUriForInternalFile(@NonNull Context context,
                                            @NonNull Intent intent,
                                            @NonNull File file) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return FileProvider.getUriForFile(context, getAuthority(context), file);
    }

    @Nullable
    public static File writeLogsToTempFile(@NonNull Context context,
                                           @NonNull String prefix,
                                           @NonNull String logs) {
        File tempDir = getTempLogDir(context);
        if (!tempDir.exists() && !tempDir.mkdir())
            return null;

        try {
            final File tempFile = File.createTempFile(prefix, ".txt", tempDir);
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(logs.getBytes());
            }

            return tempFile;
        } catch (IOException e) {
            return null;
        }
    }
}
