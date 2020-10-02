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
package com.digipom.easymediaconverter.player.split;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.FilenameUtils;
import com.digipom.easymediaconverter.utils.UriUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SplitActionViewModel extends AndroidViewModel {
    private final List<Uri> targets = new ArrayList<>();
    private MediaItem mediaItem;
    private long durationMs = 1;
    private boolean doCleanup = true;
    private final MutableLiveData<Boolean> shouldShowFab = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldShowSplitTooShortWarning = new MutableLiveData<>();
    private final MutableLiveData<Long> splitPointMs = new MutableLiveData<>();

    public SplitActionViewModel(@NonNull Application application) {
        super(application);
        shouldShowFab.setValue(false);
        shouldShowSplitTooShortWarning.setValue(false);
        splitPointMs.setValue(1L);
    }

    @NonNull
    LiveData<Boolean> shouldShowFab() {
        return shouldShowFab;
    }

    @NonNull
    LiveData<Boolean> shouldShowSplitTooShortWarning() {
        return shouldShowSplitTooShortWarning;
    }

    @NonNull
    LiveData<Long> splitPointMs() {
        return splitPointMs;
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    void setDurationMs(long durationMs) {
        if (this.durationMs <= 1) {
            splitPointMs.setValue(durationMs / 2);
        }

        this.durationMs = durationMs;
        updateFabState();
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return mediaItem.getMimeType();
    }

    @NonNull
    String getDefaultOutputFilenameForFirstDocument() {
        return FilenameUtils.appendToFilename(mediaItem.getFilename(), " (1)");
    }

    @NonNull
    String getDefaultOutputFilenameForSecondDocument() {
        return FilenameUtils.appendToFilename(mediaItem.getFilename(), " (2)");
    }

    void onSeekBarChanged(float ratio) {
        this.splitPointMs.setValue(seekBarRatioToMs(ratio));
        updateFabState();
    }

    void onSplitPointModified(long ms) {
        adjustSplitPointTo(ms);
    }

    private void adjustSplitPointTo(long newValue) {
        long newSplitPointMs = newValue;
        newSplitPointMs = Math.max(0, newSplitPointMs);
        newSplitPointMs = Math.min(newSplitPointMs, durationMs);
        splitPointMs.setValue(newSplitPointMs);
        updateFabState();
    }

    private void updateFabState() {
        final long splitPoint = (Objects.requireNonNull(splitPointMs.getValue()));
        final boolean shouldShowFab = splitPoint >= 250 && splitPoint <= durationMs - 250;

        if (shouldShowFab != Objects.requireNonNull(this.shouldShowFab.getValue())) {
            this.shouldShowFab.setValue(shouldShowFab);
            this.shouldShowSplitTooShortWarning.setValue(!shouldShowFab);
        }
    }

    float getRatioForMs(long ms) {
        return (float) ms / durationMs;
    }

    void onReceivedNextDocument(Uri target) {
        targets.add(target);
    }

    boolean needsMoreDocuments() {
        return targets.size() < 2;
    }

    void onFailedToReceiveNextDocument() {
        cleanup();
    }

    void setNoLongerNeedCleanup() {
        doCleanup = false;
    }

    @NonNull
    Uri getTargetUri(int index) {
        return targets.get(index);
    }

    @Override
    protected void onCleared() {
        cleanup();
        super.onCleared();
    }

    // Note: This won't do the cleanup in every possible circumstance (i.e. force-stopping the app).
    private void cleanup() {
        if (doCleanup) {
            for (Uri target : targets) {
                Logger.v("Cleaning up: Removing newly created document " + target);
                UriUtils.deleteResource(getApplication(), target);
            }
            targets.clear();
        }
    }

    private long seekBarRatioToMs(float ratio) {
        return (long) (ratio * durationMs);
    }
}