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
package com.digipom.easymediaconverter.player.cut;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.FilenameUtils;

import java.util.Objects;

import static com.digipom.easymediaconverter.player.FilenamingUtils.getAppendNamingForOutput;

public class CutActionViewModel extends AndroidViewModel {
    private MediaItem mediaItem;
    private long durationMs = 1;
    private final MutableLiveData<Boolean> shouldShowFab = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldShowRecordingTooShortWarning = new MutableLiveData<>();
    private final MutableLiveData<Long> cutStartMs = new MutableLiveData<>();
    private final MutableLiveData<Long> cutEndMs = new MutableLiveData<>();

    public CutActionViewModel(@NonNull Application application) {
        super(application);
        shouldShowFab.setValue(false);
        shouldShowRecordingTooShortWarning.setValue(false);
        cutStartMs.setValue(0L);
        cutEndMs.setValue(1L);
    }

    @NonNull
    LiveData<Boolean> shouldShowFab() {
        return shouldShowFab;
    }

    @NonNull
    LiveData<Boolean> shouldShowRecordingTooShortWarning() {
        return shouldShowRecordingTooShortWarning;
    }

    @NonNull
    LiveData<Long> cutStartMs() {
        return cutStartMs;
    }

    @NonNull
    LiveData<Long> cutEndMs() {
        return cutEndMs;
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return mediaItem.getMimeType();
    }

    @NonNull
    String getDefaultOutputFilename() {
        return FilenameUtils.appendToFilename(mediaItem.getFilename(),
                getAppendNamingForOutput(getApplication(), EditAction.CUT));
    }

    void onRangeSeekBarChanged(float leftRatio, float rightRatio) {
        this.cutStartMs.setValue(rangeBarRatioToMs(leftRatio));
        this.cutEndMs.setValue(rangeBarRatioToMs(rightRatio));
        updateFabAndWarningState();
    }

    private void updateFabAndWarningState() {
        final long cutStartMs = Objects.requireNonNull(this.cutStartMs.getValue());
        final long cutEndMs = Objects.requireNonNull(this.cutEndMs.getValue());
        final boolean wouldCutAtLeastSomething = (cutEndMs - cutStartMs) > 0;
        final boolean atLeast250msRemaining = durationMs - (cutEndMs - cutStartMs) >= 250;

        final boolean shouldShowFab = wouldCutAtLeastSomething && atLeast250msRemaining;
        final boolean shouldShowWarning = !atLeast250msRemaining;

        if (shouldShowFab != Objects.requireNonNull(this.shouldShowFab.getValue())) {
            this.shouldShowFab.setValue(shouldShowFab);
        }

        if (shouldShowWarning != Objects.requireNonNull(this.shouldShowRecordingTooShortWarning.getValue())) {
            this.shouldShowRecordingTooShortWarning.setValue(shouldShowWarning);
        }
    }

    float getRatioForMs(long ms) {
        return (float) ms / durationMs;
    }

    void onCutStartModified(long ms) {
        adjustCutStartTo(ms);
    }

    void onCutEndModified(long ms) {
        adjustCutEndTo(ms);
    }

    private void adjustCutStartTo(long newValue) {
        long currentCutEndMs = Objects.requireNonNull(cutEndMs.getValue());
        long newCutStartMs = newValue;
        newCutStartMs = Math.max(0, newCutStartMs);
        newCutStartMs = Math.min(newCutStartMs, durationMs);
        newCutStartMs = Math.min(newCutStartMs, currentCutEndMs);
        cutStartMs.setValue(newCutStartMs);
        updateFabAndWarningState();
    }

    private void adjustCutEndTo(long newValue) {
        long currentCutStartMs = Objects.requireNonNull(cutStartMs.getValue());
        long newCutEndMs = newValue;
        newCutEndMs = Math.max(0, newCutEndMs);
        newCutEndMs = Math.min(newCutEndMs, durationMs);
        newCutEndMs = Math.max(newCutEndMs, currentCutStartMs);
        cutEndMs.setValue(newCutEndMs);
        updateFabAndWarningState();
    }

    private long rangeBarRatioToMs(float ratio) {
        return (long) (ratio * durationMs);
    }
}