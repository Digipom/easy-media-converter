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

import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

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

public class MimeTypeUtils {
    @NonNull
    public static String getMimeTypeForAudioExtension(@NonNull String extension) {
        final MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        final String mimeTypeFromExtension = mimeTypeMap.getMimeTypeFromExtension(extension);

        if (mimeTypeFromExtension != null) {
            return mimeTypeFromExtension;
        }

        //noinspection IfCanBeSwitch
        if (extension.equals(FILETYPE_MP3)) {
            return "audio/mpeg";
        } else if (extension.equals(FILETYPE_M4A)
                || extension.equals(FILETYPE_MP4)) {
            return "audio/mp4";
        } else if (extension.equals(FILETYPE_AAC)) {
            return "audio/aac";
        } else if (extension.equals(FILETYPE_OGG)) {
            return "audio/ogg";
        } else if (extension.equals(FILETYPE_OPUS)) {
            return "audio/opus";
        } else if (extension.equals(FILETYPE_FLAC)) {
            return "audio/flac";
        } else if (extension.equals(FILETYPE_WAVE)) {
            return "audio/pcm";
        } else {
            // Don't know
            return "audio/*";
        }
    }

    @NonNull
    public static String getMimeTypeForVideoExtension(@NonNull String extension) {
        final MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        final String mimeTypeFromExtension = mimeTypeMap.getMimeTypeFromExtension(extension);

        if (mimeTypeFromExtension != null) {
            return mimeTypeFromExtension;
        }

        //noinspection IfCanBeSwitch
        if (extension.equals(FILETYPE_MP4)) {
            return "video/mp4";
        } else if (extension.equals(FILETYPE_MKV)) {
            return "video/mkv";
        } else if (extension.equals(FILETYPE_MOV)) {
            return "video/mov";
        } else if (extension.equals(FILETYPE_WEBM)) {
            return "video/webm";
        } else {
            // Don't know
            return "video/*";
        }
    }
}
