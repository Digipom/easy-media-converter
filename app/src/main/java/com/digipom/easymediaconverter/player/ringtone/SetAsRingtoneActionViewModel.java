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
package com.digipom.easymediaconverter.player.ringtone;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.MimeTypeUtils;

import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_AAC;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.isFileTypeSupportedForRingtone;
import static com.digipom.easymediaconverter.utils.FilenameUtils.getCanonicalExtension;
import static com.digipom.easymediaconverter.utils.FilenameUtils.replaceExtension;

public class SetAsRingtoneActionViewModel extends AndroidViewModel {
    private MediaItem mediaItem;
    private RingtoneType selectedType = RingtoneType.PHONE;

    public SetAsRingtoneActionViewModel(@NonNull Application application) {
        super(application);
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return MimeTypeUtils.getMimeTypeForAudioExtension(FILETYPE_AAC);
    }

    @NonNull
    String getDefaultOutputFilename() {
        if (needsConversionToAac()) {
            return replaceExtension(mediaItem.getFilename(), FILETYPE_AAC);
        } else {
            return mediaItem.getFilename();
        }
    }

    boolean needsConversionToAac() {
        return !isFileTypeSupportedForRingtone(getCanonicalExtension(mediaItem.getFilename()));
    }

    @NonNull
    RingtoneType getRingtoneType() {
        return selectedType;
    }

    void onPhoneRingtoneTypeChecked() {
        selectedType = RingtoneType.PHONE;
    }

    void onNotificationRingtoneTypeChecked() {
        selectedType = RingtoneType.NOTIFICATION;
    }

    void onAlarmRingtoneTypeChecked() {
        selectedType = RingtoneType.ALARM;
    }
}