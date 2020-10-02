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
package com.digipom.easymediaconverter.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.FilenameUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.digipom.easymediaconverter.utils.FilenameUtils.getCanonicalExtension;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_AAC;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_FLAC;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_M4A;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MKV;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MOV;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MP3;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MP4;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_OGG;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_OPUS;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_WAVE;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_WEBM;
import static com.digipom.easymediaconverter.config.StaticConfig.LOGCAT_LOGGING_ON;

public final class MediaItem implements Parcelable {
    private static final String TAG = MediaItem.class.getName();

    @NonNull private final Uri uri;
    @NonNull private final String displayName;
    @NonNull private final String mimeType;
    private final long size;
    private final long optionalLastModifiedDate;
    private final long optionalDurationMs;

    @NonNull
    public static MediaItem[] constructMediaItemsFromGetContentResponse(@NonNull Context context,
                                                                        @NonNull Intent data) throws IOException {
        final List<MediaItem> list = new ArrayList<>();
        if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
            for (int i = 0; i < data.getClipData().getItemCount(); ++i) {
                final Uri itemUri = data.getClipData().getItemAt(i).getUri();
                Logger.v("Constructing media item from get content response: " + itemUri);
                final MediaItem item = constructFromUri(context, itemUri);
                Logger.v("Item: " + item);
                list.add(item);
            }
            return list.toArray(new MediaItem[0]);
        } else {
            return new MediaItem[]{constructFromGetContentResponse(context, data)};
        }
    }

    @NonNull
    public static MediaItem constructFromGetContentResponse(@NonNull Context context,
                                                            @NonNull Intent data) throws IOException {
        Logger.v("Constructing media item from get content response: " + data.getData());
        final MediaItem item = constructFromUri(context, Objects.requireNonNull(data.getData()));
        Logger.v("Item: " + item);
        return item;
    }

    @NonNull
    public static MediaItem constructFromUri(@NonNull Context context, @NonNull Uri uri) throws IOException {
        if (LOGCAT_LOGGING_ON) {
            Log.v(TAG, "Constructing media item from uri " + uri);
        }

        final String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            throw new IOException("Couldn't obtain mime type for " + uri);
        }

        // Pretty much just DISPLAY_NAME and SIZE are guaranteed to be there. For the others, we do
        // an optional query.
        String displayName = Objects.requireNonNull(uri.getLastPathSegment());
        long size = -1;
        final Cursor cursor = context.getContentResolver().query(uri,
                new String[]{
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE},
                null, null, null);

        if (cursor != null) {
            try {
                cursor.moveToFirst();
                if (!cursor.isAfterLast()) {
                    int displayNameColumnIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                    if (!cursor.isNull(displayNameColumnIdx)) {
                        displayName = cursor.getString(displayNameColumnIdx);
                    }
                    if (!cursor.isNull(sizeColumnIndex)) {
                        size = cursor.getLong(sizeColumnIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }


        final long lastModifiedDate = optionalQueryForLong(context, uri,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED, -1);
        @SuppressLint("InlinedApi")
        final long durationMs = optionalQueryForLong(context, uri,
                MediaStore.MediaColumns.DURATION, -1);

        Logger.d("Obtained media item with uri " + uri + ", display name: " + displayName
               + ", mime type: " + mimeType + ", size: " + size + ", last modified: " + lastModifiedDate
               + ", duration in ms: " + durationMs);
        return new MediaItem(uri, displayName, mimeType, size, lastModifiedDate, durationMs);
    }

    private static long optionalQueryForLong(@NonNull Context context, @NonNull Uri uri,
                                             @NonNull String columnName,
                                             @SuppressWarnings("SameParameterValue") long defaultValue) {
        try {
            // Last modified and duration might not be available
            final Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{columnName},
                    null, null, null);

            if (cursor != null) {
                try {
                    cursor.moveToFirst();
                    if (!cursor.isAfterLast()) {
                        return cursor.getLong(cursor.getColumnIndex(columnName));
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Logger.w("Couldn't read " + columnName + " for uri " + uri, e);
            return defaultValue;
        }

        Logger.d("Couldn't obtain " + columnName + " for uri " + uri);
        return defaultValue;
    }

    private MediaItem(@NonNull Uri uri, @NonNull String displayName, @NonNull String mimeType,
                      long size, long optionalLastModifiedDate, long optionalDurationMs) {
        this.uri = uri;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.size = size;
        this.optionalLastModifiedDate = optionalLastModifiedDate;
        this.optionalDurationMs = optionalDurationMs;
    }

    private MediaItem(Parcel in) {
        uri = (Uri) Objects.requireNonNull(in.readParcelable(Uri.class.getClassLoader()));
        displayName = Objects.requireNonNull(in.readString());
        mimeType = Objects.requireNonNull(in.readString());
        size = in.readLong();
        optionalLastModifiedDate = in.readLong();
        optionalDurationMs = in.readLong();
    }

    @NonNull
    public Uri getUri() {
        return uri;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getFilename() {
        // If the provider starts with com.android.providers.media, then we always synthesize the
        // extension.
        if (uri.getAuthority() != null && uri.getAuthority().startsWith("com.android.providers.media")) {
            Logger.v("Synthesizing extension for authority " + uri.getAuthority());
            return FilenameUtils.getAppendedFilename(displayName, "", synthesizeExtensionFromMimeType());
        }

        // For other providers, only synthesize if the current extension derived from the display
        // name doesn't seem to fit the normal bounds of a file extension.
        final String currentExtension = getCanonicalExtension(displayName);
        if (currentExtension.length() < 3 || currentExtension.length() > 4) {
            Logger.v("Synthesizing extension as the current extension of " + currentExtension
                   + " doesn't seem to match the standard length for a file extension.");
            return FilenameUtils.getAppendedFilename(displayName, "", synthesizeExtensionFromMimeType());
        }

        return displayName;
    }

    @NonNull
    private String synthesizeExtensionFromMimeType() {
        final MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = mimeTypeMap.getExtensionFromMimeType(mimeType);
        if (extension != null) {
            Logger.v("Obtained extension " + extension + " for mime type " + mimeType);
        } else {
            extension = getStandardExtensionForMimeType();
            Logger.v("Synthesized extension " + extension + " for mime type " + mimeType);
        }
        return extension;
    }

    @SuppressWarnings("IfCanBeSwitch")
    @NonNull
    private String getStandardExtensionForMimeType() {
        final String lowerCased = mimeType.toLowerCase(Locale.US).trim();
        if (lowerCased.equals("audio/mpeg")) {
            return FILETYPE_MP3;
        } else if (lowerCased.equals("audio/mp4")) {
            return FILETYPE_M4A;
        } else if (lowerCased.equals("audio/aac")) {
            return FILETYPE_AAC;
        } else if (lowerCased.equals("audio/ogg")) {
            return FILETYPE_OGG;
        } else if (lowerCased.equals("audio/opus")) {
            return FILETYPE_OPUS;
        } else if (lowerCased.equals("audio/flac")) {
            return FILETYPE_FLAC;
        } else if (lowerCased.equals("audio/pcm")) {
            return FILETYPE_WAVE;
        } else if (lowerCased.equals("video/mp4")) {
            return FILETYPE_MP4;
        } else if (lowerCased.equals("video/mkv")) {
            return FILETYPE_MKV;
        } else if (lowerCased.equals("video/mov")) {
            return FILETYPE_MOV;
        } else if (lowerCased.equals("video/webm")) {
            return FILETYPE_WEBM;
        } else {
            // Guess based on the mimetype
            return lowerCased.substring(6);
        }
    }
    
    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public long getOptionalLastModifiedDate() {
        return optionalLastModifiedDate;
    }

    public long getOptionalDurationMs() {
        return optionalDurationMs;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(displayName);
        dest.writeString(mimeType);
        dest.writeLong(size);
        dest.writeLong(optionalLastModifiedDate);
        dest.writeLong(optionalDurationMs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MediaItem> CREATOR = new Creator<MediaItem>() {
        @Override
        public MediaItem createFromParcel(Parcel in) {
            return new MediaItem(in);
        }

        @Override
        public MediaItem[] newArray(int size) {
            return new MediaItem[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaItem mediaItem = (MediaItem) o;

        if (size != mediaItem.size) return false;
        if (optionalLastModifiedDate != mediaItem.optionalLastModifiedDate) return false;
        if (optionalDurationMs != mediaItem.optionalDurationMs) return false;
        if (!uri.equals(mediaItem.uri)) return false;
        if (!displayName.equals(mediaItem.displayName)) return false;
        return mimeType.equals(mediaItem.mimeType);
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + displayName.hashCode();
        result = 31 * result + mimeType.hashCode();
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + (int) (optionalLastModifiedDate ^ (optionalLastModifiedDate >>> 32));
        result = 31 * result + (int) (optionalDurationMs ^ (optionalDurationMs >>> 32));
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "MediaItem{" +
                "uri=" + uri +
                ", displayName='" + displayName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                ", optionalLastModifiedDate=" + optionalLastModifiedDate +
                ", optionalDurationMs=" + optionalDurationMs +
                '}';
    }
}
