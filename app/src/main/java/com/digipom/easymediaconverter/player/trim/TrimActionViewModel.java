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
package com.digipom.easymediaconverter.player.trim;

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

public class TrimActionViewModel extends AndroidViewModel {
    private MediaItem mediaItem;
    private long durationMs = 1;
    private final MutableLiveData<Boolean> shouldShowFab = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldShowRecordingTooShortWarning = new MutableLiveData<>();
    private final MutableLiveData<Long> trimBeforeMs = new MutableLiveData<>();
    private final MutableLiveData<Long> trimAfterMs = new MutableLiveData<>();

    public TrimActionViewModel(@NonNull Application application) {
        super(application);
        shouldShowFab.setValue(false);
        shouldShowRecordingTooShortWarning.setValue(false);
        trimBeforeMs.setValue(0L);
        trimAfterMs.setValue(1L);
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
    LiveData<Long> trimBeforeMs() {
        return trimBeforeMs;
    }

    @NonNull
    LiveData<Long> trimAfterMs() {
        return trimAfterMs;
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
                getAppendNamingForOutput(getApplication(), EditAction.TRIM));
    }

    void onRangeSeekBarChanged(float leftRatio, float rightRatio) {
        this.trimBeforeMs.setValue(rangeBarRatioToMs(leftRatio));
        this.trimAfterMs.setValue(rangeBarRatioToMs(rightRatio));
        updateFabAndWarningState();
    }

    private void updateFabAndWarningState() {
        final long trimBefore = (Objects.requireNonNull(trimBeforeMs.getValue()));
        final long trimAfter = (Objects.requireNonNull(trimAfterMs.getValue()));
        final boolean wouldTrimAtLeastSomething = trimBefore > 0 || trimAfter < durationMs;

        final boolean atLeast250msRemaining = trimAfter - trimBefore >= 250;
        final boolean shouldShowFab = wouldTrimAtLeastSomething && atLeast250msRemaining;
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

    void onTrimBeforeModified(long ms) {
        adjustTrimBeforeTo(ms);
    }

    void onTrimAfterModified(long ms) {
        adjustTrimAfterTo(ms);
    }

    private void adjustTrimBeforeTo(long newValue) {
        long currentTrimAfterMs = Objects.requireNonNull(trimAfterMs.getValue());
        long newTrimBeforeMs = newValue;
        newTrimBeforeMs = Math.max(0, newTrimBeforeMs);
        newTrimBeforeMs = Math.min(newTrimBeforeMs, durationMs);
        newTrimBeforeMs = Math.min(newTrimBeforeMs, currentTrimAfterMs);
        trimBeforeMs.setValue(newTrimBeforeMs);
        updateFabAndWarningState();
    }

    private void adjustTrimAfterTo(long newValue) {
        long currentTrimBeforeMs = Objects.requireNonNull(trimBeforeMs.getValue());
        long newTrimAfterMs = newValue;
        newTrimAfterMs = Math.max(0, newTrimAfterMs);
        newTrimAfterMs = Math.min(newTrimAfterMs, durationMs);
        newTrimAfterMs = Math.max(newTrimAfterMs, currentTrimBeforeMs);
        trimAfterMs.setValue(newTrimAfterMs);
        updateFabAndWarningState();
    }

    private long rangeBarRatioToMs(float ratio) {
        return (long) (ratio * durationMs);
    }
}
