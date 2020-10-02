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
package com.digipom.easymediaconverter.player.convert;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.media.MediaItem;

import static com.digipom.easymediaconverter.edit.OutputFormatType.AAC;
import static com.digipom.easymediaconverter.edit.OutputFormatType.FLAC;
import static com.digipom.easymediaconverter.edit.OutputFormatType.M4A;
import static com.digipom.easymediaconverter.edit.OutputFormatType.MKV;
import static com.digipom.easymediaconverter.edit.OutputFormatType.MOV;
import static com.digipom.easymediaconverter.edit.OutputFormatType.MP3;
import static com.digipom.easymediaconverter.edit.OutputFormatType.MP4;
import static com.digipom.easymediaconverter.edit.OutputFormatType.OGG;
import static com.digipom.easymediaconverter.edit.OutputFormatType.OPUS;
import static com.digipom.easymediaconverter.edit.OutputFormatType.WAVE_PCM;
import static com.digipom.easymediaconverter.edit.OutputFormatType.WEBM;
import static com.digipom.easymediaconverter.edit.OutputFormatType.getMatchingOutputType;
import static com.digipom.easymediaconverter.player.FilenamingUtils.getAppendNamingForOutput;
import static com.digipom.easymediaconverter.utils.FilenameUtils.appendToFilename;
import static com.digipom.easymediaconverter.utils.FilenameUtils.getCanonicalExtension;
import static com.digipom.easymediaconverter.utils.FilenameUtils.replaceExtension;
import static com.digipom.easymediaconverter.utils.MimeTypeUtils.getMimeTypeForAudioExtension;
import static com.digipom.easymediaconverter.utils.MimeTypeUtils.getMimeTypeForVideoExtension;

public class ConvertActionViewModel extends AndroidViewModel {
    private MediaItem mediaItem;
    private OutputFormatType selectedType = MP3;

    public ConvertActionViewModel(@NonNull Application application) {
        super(application);
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    void onMp3Selected() {
        selectedType = MP3;
    }

    void onM4aSelected() {
        selectedType = M4A;
    }

    void onAacSelected() {
        selectedType = AAC;
    }

    void onOggSelected() {
        selectedType = OGG;
    }

    void onOpusSelected() {
        selectedType = OPUS;
    }

    void onFlacSelected() {
        selectedType = FLAC;
    }

    void onPcmSelected() {
        selectedType = WAVE_PCM;
    }

    void onMp4Selected() {
        selectedType = MP4;
    }

    void onMkvSelected() {
        selectedType = MKV;
    }

    void onMovSelected() {
        selectedType = MOV;
    }

    void onWebmSelected() {
        selectedType = WEBM;
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        if (selectedType.isVideoOutputType()) {
            return getMimeTypeForVideoExtension(selectedType.getExtensionForOutputType());
        } else {
            return getMimeTypeForAudioExtension(selectedType.getExtensionForOutputType());
        }
    }

    @NonNull
    String getDefaultOutputFilename() {
        final String filename = mediaItem.getFilename();
        final String targetExtension = selectedType.getExtensionForOutputType();
        return replaceExtension(appendToFilename(filename,
                getAppendNamingForOutput(getApplication(), EditAction.CONVERT)), targetExtension);
    }

    @NonNull
    OutputFormatType getOutputType() {
        return selectedType;
    }

    @Nullable
    OutputFormatType getTypeToDisableDueToSameAsSource() {
        return getMatchingOutputType(getCanonicalExtension(mediaItem.getFilename()));
    }
}
