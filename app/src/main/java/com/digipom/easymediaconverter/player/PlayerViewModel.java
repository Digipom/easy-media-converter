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
package com.digipom.easymediaconverter.player;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Size;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.edit.Bitrates.BitrateWithValue;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.media.MediaItem;

import java.io.IOException;
import java.util.Objects;

public final class PlayerViewModel extends AndroidViewModel {
    private final MediaPlayerController mediaPlayerController;
    private final FFMpegController ffMpegController;
    private LiveData<Long> durationMsLiveData;
    private MediaItem item;
    private long defaultDurationMs;

    public PlayerViewModel(@NonNull Application application) {
        super(application);
        final Context applicationContext = application.getApplicationContext();
        this.mediaPlayerController = new MediaPlayerController(applicationContext);
        this.ffMpegController = (((BaseApplication) application).getServiceLocator()).getFFMpegController();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mediaPlayerController.release();
    }

    // Init for media item

    void setMediaItem(@NonNull MediaItem item) throws IOException {
        this.item = item;
        // TODO
        ensurePlayerLoaded(item.getUri(), 0);
    }

    public @NonNull
    MediaItem getMediaItem() {
        return item;
    }

    @SuppressWarnings("SameParameterValue")
    private void ensurePlayerLoaded(@NonNull Uri uri, long defaultDurationMs) throws IOException {
        this.defaultDurationMs = defaultDurationMs;
        mediaPlayerController.ensurePlayerLoaded(uri);
    }

    // Observable data

    @NonNull
    LiveData<Integer> getObservableElapsedTimeMs() {
        return mediaPlayerController.getObservableElaspedTimeMs();
    }

    @NonNull
    public LiveData<Long> getObservableDurationMs() {
        if (durationMsLiveData == null) {
            durationMsLiveData = Transformations.map(mediaPlayerController.getObservableDurationMs(),
                    new Function<Integer, Long>() {
                        @Override
                        public Long apply(Integer input) {
                            // Currently we only handle positive durations
                            return Objects.requireNonNull(input) < 1 ? Math.max(1, defaultDurationMs) : input;
                        }
                    });

        }
        return durationMsLiveData;
    }

    @NonNull
    LiveData<Boolean> getObservableIsPlaying() {
        return mediaPlayerController.getObservableIsPlaying();
    }

    @NonNull
    LiveData<Boolean> getObservableIsLooping() {
        return mediaPlayerController.getObservableIsLooping();
    }

    @NonNull
    LiveData<Float> getObservablePlaybackSpeed() {
        return mediaPlayerController.getObservablePlaybackSpeed();
    }

    @NonNull
    public LiveData<Size> getObservableVideoSize() {
        return mediaPlayerController.getObservableVideoSize();
    }

    // Getters

    long getDurationMs() {
        // Just return the observable's current value
        return Objects.requireNonNull(getObservableDurationMs().getValue());
    }

    // Setters

    void setVideoSurfaceForPlayback(SurfaceHolder holder) {
        mediaPlayerController.setVideoSurfaceForPlayback(holder);
    }

    // UI actions

    public void onActionWantsToChangePlaybackPositionToOffset(long offsetMs) {
        // TODO see if we also need to direct-update the seekbar to avoid lag
        mediaPlayerController.seeToOffsetMs(offsetMs);
    }

    void onSeekbarMovedToRatio(float ratio) {
        mediaPlayerController.seekToRatio(ratio);
    }

    void onLoopClicked() {
        mediaPlayerController.toggleLoop();
    }

    void onRewindClicked() {
        mediaPlayerController.seekByRelativeMs(-5000);
    }

    void onPlayPausedClicked() {
        mediaPlayerController.togglePlayPause();
    }

    void onFastForwardClicked() {
        mediaPlayerController.seekByRelativeMs(5000);
    }

    void onSpeedClicked() {
        final float currentSpeed = Objects.requireNonNull(getObservablePlaybackSpeed().getValue());
        final float newSpeed;
        if (currentSpeed < 0.75f) {
            newSpeed = 0.75f;
        } else if (currentSpeed < 1f) {
            newSpeed = 1f;
        } else if (currentSpeed < 1.25f) {
            newSpeed = 1.25f;
        } else if (currentSpeed < 1.5f) {
            newSpeed = 1.5f;
        } else if (currentSpeed < 1.75f) {
            newSpeed = 1.75f;
        } else if (currentSpeed < 2f) {
            newSpeed = 2f;
        } else {
            newSpeed = 0.5f;
        }
        mediaPlayerController.setSpeed(newSpeed);
    }

    void onWantsUpdatedPlaybackPosition() {
        mediaPlayerController.updateCurrentPlaybackPosition();
    }

    void onUIAboutToDisappear() {
        mediaPlayerController.pause();
    }

    // Actions which result in a call to FFMPEG

    void onConvertActionClicked(@NonNull Uri targetUri,
                                @NonNull String targetFileName,
                                @NonNull OutputFormatType outputFormatType,
                                @Nullable BitrateWithValue selectedBitrate) {
        ffMpegController.submitConversionRequest(item,
                targetUri, targetFileName, outputFormatType, selectedBitrate);
    }

    void onMakeVideoActionClicked(@NonNull Uri targetUri,
                                  @NonNull String targetFileName,
                                  @Nullable Uri customCoverImageUri,
                                  @Nullable String customCoverImageFileName) {
        ffMpegController.submitMakeVideoRequest(item, targetUri, targetFileName, customCoverImageUri,
                customCoverImageFileName);
    }

    void onExtractAudioActionClicked(@NonNull Uri targetUri,
                                     @NonNull String targetFileName,
                                     @NonNull OutputFormatType outputFormatType) {
        ffMpegController.submitExtractAudioRequest(item,
                targetUri, targetFileName, outputFormatType);
    }

    void onTrimActionClicked(@NonNull Uri targetUri,
                             @NonNull String targetFileName,
                             long trimBeforeMs,
                             long trimAfterMs) {
        ffMpegController.submitTrimRequest(item, targetUri, targetFileName,
                trimBeforeMs, trimAfterMs);
    }

    void onCutActionClicked(@NonNull Uri targetUri,
                            @NonNull String targetFileName,
                            long cutStartMs,
                            long cutEndMs,
                            long durationMs) {
        ffMpegController.submitCutRequest(item, targetUri, targetFileName,
                cutStartMs, cutEndMs, durationMs);
    }

    void onAdjustSpeedActionClicked(@NonNull Uri targetUri, @NonNull String targetFileName, float speed) {
        ffMpegController.submitSpeedAdjustmentRequest(item, targetUri, targetFileName, speed);
    }

    void onAdjustVolumeActionClicked(@NonNull Uri targetUri, @NonNull String targetFileName, float db) {
        ffMpegController.submitVolumeAdjustmentRequest(item, targetUri, targetFileName, db);
    }

    void onAddSilenceActionClicked(@NonNull Uri targetUri,
                                   @NonNull String targetFileName,
                                   long silenceInsertionPointMs, long silenceDurationMs) {
        ffMpegController.submitAddSilenceRequest(item, targetUri, targetFileName, silenceInsertionPointMs, silenceDurationMs);
    }

    void onNormalizeActionClicked(@NonNull Uri targetUri, @NonNull String targetFileName) {
        ffMpegController.submitNormalizeRequest(item, targetUri, targetFileName);
    }

    void onSplitActionClicked(@NonNull Uri firstTargetUri, @NonNull String firstTargetFileName,
                              @NonNull Uri secondTargetUri, @NonNull String secondTargetFileName,
                              long splitAtMs) {
        ffMpegController.submitSplitRequest(item, firstTargetUri, firstTargetFileName,
                secondTargetUri, secondTargetFileName, splitAtMs);
    }

    void onCombineActionClicked(@NonNull MediaItem[] inputItems,
                                @NonNull Uri targetUri,
                                @NonNull String targetFileName) {
        ffMpegController.submitCombineRequest(inputItems, targetUri, targetFileName);
    }

    void onSetAsRingtoneActionClicked(@NonNull Uri targetUri,
                                      @NonNull String targetFileName,
                                      @NonNull RingtoneType ringtoneType,
                                      boolean transcodeToAac) {
        ffMpegController.submitSetAsRingtoneRequest(item, targetUri, targetFileName, ringtoneType, transcodeToAac);
    }
}
