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
package com.digipom.easymediaconverter.player.add_silence;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.FilenameUtils;

import static com.digipom.easymediaconverter.player.FilenamingUtils.getAppendNamingForOutput;

public class AddSilenceActionViewModel extends AndroidViewModel {
    private MediaItem mediaItem;
    private long durationMs = 1;
    private final MutableLiveData<Long> insertionPointMs = new MutableLiveData<>();
    private final MutableLiveData<Long> silenceDurationMs = new MutableLiveData<>();

    public AddSilenceActionViewModel(@NonNull Application application) {
        super(application);
        insertionPointMs.setValue(0L);
        silenceDurationMs.setValue(60L * 1000L);
    }

    @NonNull
    LiveData<Long> insertionPointMs() {
        return insertionPointMs;
    }

    @NonNull
    LiveData<Long> silenceDurationMs() {
        return silenceDurationMs;
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return mediaItem.getMimeType();
    }

    @NonNull
    String getDefaultOutputFilename() {
        return FilenameUtils.appendToFilename(mediaItem.getFilename(),
                getAppendNamingForOutput(getApplication(), EditAction.ADD_SILENCE));
    }

    void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    void onSeekBarChanged(float ratio) {
        this.insertionPointMs.setValue(seekBarRatioToMs(ratio));
    }

    void onSilenceDurationUpdated(long durationMs) {
        silenceDurationMs.setValue(Math.max(Math.min(durationMs, getMaxSilenceDurationMs()), getMinSilenceDurationMs()));
    }

    @SuppressWarnings("SameReturnValue")
    long getMinSilenceDurationMs() {
        return 1000L;
    }

    long getMaxSilenceDurationMs() {
        return 60L * 60L * 1000L;
    }

    float getRatioForMs(long ms) {
        return (float) ms / durationMs;
    }

    void onInsertionPointModified(long ms) {
        adjustInsertionPointTo(ms);
    }

    private void adjustInsertionPointTo(long newValue) {
        long newInsertionPointMs = newValue;
        newInsertionPointMs = Math.max(0, newInsertionPointMs);
        newInsertionPointMs = Math.min(newInsertionPointMs, durationMs);
        insertionPointMs.setValue(newInsertionPointMs);
    }

    private long seekBarRatioToMs(float ratio) {
        return (long) (ratio * durationMs);
    }
}