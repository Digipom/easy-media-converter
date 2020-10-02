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

public class FileTypeExtensions {
    public static final String FILETYPE_MP3 = "mp3";
    public static final String FILETYPE_M4A = "m4a";
    public static final String FILETYPE_MP4 = "mp4";
    public static final String FILETYPE_AAC = "aac";
    public static final String FILETYPE_OGG = "ogg";
    public static final String FILETYPE_OPUS = "opus";
    public static final String FILETYPE_FLAC = "flac";
    public static final String FILETYPE_WAVE = "wav";
    private static final String FILETYPE_3GP = "3gp";
    public static final String FILETYPE_MKV = "mkv";
    public static final String FILETYPE_MOV = "mov";
    public static final String FILETYPE_WEBM = "webm";

    public static boolean isFileTypeSupportedForRingtone(@NonNull String fileType) {
        return fileType.equals(FILETYPE_MP3) || fileType.equals(FILETYPE_M4A) || fileType.equals(FILETYPE_MP4)
                || fileType.equals(FILETYPE_AAC) || fileType.equals(FILETYPE_FLAC) || fileType.equals(FILETYPE_WAVE)
                || fileType.equals(FILETYPE_3GP);
    }

    public static boolean isFileTypeForAacAudio(@NonNull String fileType) {
        return fileType.equals(FILETYPE_AAC) || fileType.equals(FILETYPE_MP4) || fileType.equals(FILETYPE_M4A);
    }

    public static boolean isFileTypeForMp4Video(@NonNull String fileType) {
        return fileType.equals(FILETYPE_MP4);
    }
}
