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
package com.digipom.easymediaconverter.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

public enum OutputFormatType {
    MP3, M4A, AAC, OGG, OPUS, FLAC, WAVE_PCM,
    MP4, MKV, MOV, WEBM;

    @NonNull
    public String getExtensionForOutputType() {
        switch (this) {
            case MP3:
                return FILETYPE_MP3;
            case M4A:
                return FILETYPE_M4A;
            case AAC:
                return FILETYPE_AAC;
            case OGG:
                return FILETYPE_OGG;
            case OPUS:
                return FILETYPE_OPUS;
            case FLAC:
                return FILETYPE_FLAC;
            case WAVE_PCM:
                return FILETYPE_WAVE;
            case MP4:
                return FILETYPE_MP4;
            case MKV:
                return FILETYPE_MKV;
            case MOV:
                return FILETYPE_MOV;
            case WEBM:
                return FILETYPE_WEBM;
        }

        // Should never reach here
        throw new IllegalArgumentException();
    }

    public boolean isVideoOutputType() {
        switch (this) {
            case MP4:
            case MKV:
            case MOV:
            case WEBM:
                return true;
            default:
                return false;
        }
    }

    @Nullable
    public static OutputFormatType getMatchingOutputType(@NonNull String extension) {
        //noinspection IfCanBeSwitch
        if (extension.equals(FILETYPE_MP3)) {
            return MP3;
        } else if (extension.equals(FILETYPE_M4A)) {
            return M4A;
        } else if (extension.equals(FILETYPE_AAC)) {
            return AAC;
        } else if (extension.equals(FILETYPE_OGG)) {
            return OGG;
        } else if (extension.equals(FILETYPE_OPUS)) {
            return OPUS;
        } else if (extension.equals(FILETYPE_FLAC)) {
            return FLAC;
        } else if (extension.equals(FILETYPE_MP4)) {
            return MP4;
        } else if (extension.equals(FILETYPE_MKV)) {
            return MKV;
        } else if (extension.equals(FILETYPE_MOV)) {
            return MOV;
        } else if (extension.equals(FILETYPE_WEBM)) {
            return WEBM;
        }

        return null;
    }
}
