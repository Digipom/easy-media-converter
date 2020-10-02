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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;

import static com.digipom.easymediaconverter.utils.FilenameUtils.getExtension;
import static com.digipom.easymediaconverter.utils.FilenameUtils.replaceExtension;

public class UriUtils {
    public static void deleteResource(@NonNull Context context, @NonNull Uri uri) {
        try {
            Logger.v("Deleting " + uri);
            if (Objects.requireNonNull(uri.getScheme()).equals(ContentResolver.SCHEME_FILE)) {
                final File file = new File(Objects.requireNonNull(uri.getPath()));
                if (!file.delete()) {
                    Logger.w("Couldn't delete " + uri);
                }
            } else {
                // Handle it as a SAF document.
                DocumentsContract.deleteDocument(context.getContentResolver(), uri);
            }
            Logger.v("Deleted " + uri);
        } catch (Exception e) {
            Logger.w("Couldn't delete " + uri, e);
        }
    }

    @NonNull
    public static Uri ensureResourceHasExtension(@NonNull Context context,
                                                 @NonNull Uri uri,
                                                 @NonNull String extension) throws FileNotFoundException {
        if (Objects.requireNonNull(uri.getScheme()).equals(ContentResolver.SCHEME_FILE)) {
            final File file = new File(Objects.requireNonNull(uri.getPath()));
            final String displayName = file.getName();
            final String fileExtension = getExtension(displayName);
            if (fileExtension.equals(extension)) {
                // Extension is already good -- no need to change it.
                return uri;
            } else {
                Logger.d("Renaming file " + uri + " to have extension " + extension);
                final File dest = new File(file.getParent(), replaceExtension(displayName, extension));
                if (file.renameTo(dest)) {
                    return Uri.fromFile(dest);
                } else {
                    Logger.w("Unable to rename file " + uri);
                    return uri;
                }
            }
        } else {
            // First, get the current display name.
            final Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);

            if (cursor != null) {
                try {
                    cursor.moveToFirst();
                    if (!cursor.isAfterLast()) {
                        final String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        final String documentExtension = getExtension(displayName);
                        if (documentExtension.equals(extension)) {
                            // Extension is already good -- no need to change it.
                            return uri;
                        } else {
                            if (!Objects.requireNonNull(uri.getAuthority()).equals("com.google.android.apps.docs.storage")) {
                                Logger.d("Renaming document " + uri + " with display name "
                                        + displayName + " to have extension " + extension);
                                final Uri newDocumentUri = DocumentsContract.renameDocument(
                                        context.getContentResolver(), uri, replaceExtension(displayName, extension));
                                if (newDocumentUri != null) {
                                    Logger.d("Renamed " + uri + ", new uri: " + newDocumentUri);
                                    return newDocumentUri;
                                } else {
                                    Logger.w("Unable to rename document " + uri);
                                    return uri;
                                }
                            } else {
                                Logger.w("Not renaming to force the file extension since this "
                                        + "is a Google Drive URI, and something is broken with "
                                        + "Google Drive as we lose access to the URI after renaming "
                                        + "the file, even if we take persistable URI permissions.");
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }

            Logger.w("Display name not found for uri " + uri);
            return uri;
        }
    }
}
