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

import com.digipom.easymediaconverter.edit.Bitrates;
import com.digipom.easymediaconverter.edit.Bitrates.BitrateRange;
import com.digipom.easymediaconverter.edit.Bitrates.BitrateType;
import com.digipom.easymediaconverter.edit.Bitrates.BitrateWithValue;
import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.media.MediaItem;

import java.util.HashMap;
import java.util.Map;

import static com.digipom.easymediaconverter.edit.Bitrates.BitrateType.ABR;
import static com.digipom.easymediaconverter.edit.Bitrates.BitrateType.CBR;
import static com.digipom.easymediaconverter.edit.Bitrates.BitrateType.VBR;
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

    @NonNull
    private final HashMap<OutputFormatType, Map<BitrateType, BitrateRange>> bitrateSpecs = new HashMap<>();
    // Remember the most recently selected bitrate state by the user
    @NonNull
    private final HashMap<OutputFormatType, BitrateState> bitrateStates = new HashMap<>();
    @NonNull
    private OutputFormatType selectedType = MP3;

    public ConvertActionViewModel(@NonNull Application application) {
        super(application);
        bitrateSpecs.put(MP3, Bitrates.getMp3BitrateSpecs());
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

    boolean hasSelectableBitratesForCurrentFormat() {
        return bitrateSpecs.containsKey(selectedType);
    }

    @Nullable
    BitrateState getCurrentBitrateState() {
        return bitrateStates.get(selectedType);
    }

    @Nullable
    BitrateWithValue getSelectedBitrate() {
        final BitrateState existingState = bitrateStates.get(selectedType);
        if (existingState != null) {
            return new BitrateWithValue(
                    existingState.forBitrateType,
                    existingState.bitrateSpec.bitrateValueForStep(existingState.currentStep));
        }

        return null;
    }

    void updateSelectedBitrateType(@Nullable BitrateType type) {
        if (type == null) {
            bitrateStates.remove(selectedType);
        } else {
            // See if we have an existing state.
            final BitrateState existingState = bitrateStates.get(selectedType);
            // Specs could be null if this is called after a rotation, even if the bitrate section
            // is currently hidden.
            final Map<BitrateType, BitrateRange> specs = bitrateSpecs.get(selectedType);
            if (specs != null) {
                final BitrateRange cbrBitrateSpec = specs.get(CBR);
                final BitrateRange abrBitrateSpec = specs.get(ABR);
                final BitrateRange vbrBitrateSpec = specs.get(VBR);

                switch (type) {
                    case CBR:
                        if (cbrBitrateSpec != null) {
                            if (existingState == null || existingState.forBitrateType == VBR) {
                                bitrateStates.put(selectedType, new BitrateState(
                                        CBR, cbrBitrateSpec, cbrBitrateSpec.defaultStep()));
                            } else if (existingState.forBitrateType == ABR) {
                                final int currentKbps = existingState.bitrateSpec.bitrateValueForStep(
                                        existingState.currentStep);
                                bitrateStates.put(selectedType, new BitrateState(
                                        CBR, cbrBitrateSpec, cbrBitrateSpec.closestStepForBitrate(currentKbps)));
                            }
                        }
                        break;
                    case ABR:
                        if (abrBitrateSpec != null) {
                            if (existingState == null || existingState.forBitrateType == VBR) {
                                bitrateStates.put(selectedType, new BitrateState(
                                        ABR, abrBitrateSpec, abrBitrateSpec.defaultStep()));
                            } else if (existingState.forBitrateType == CBR) {
                                final int currentKbps = existingState.bitrateSpec.bitrateValueForStep(
                                        existingState.currentStep);
                                bitrateStates.put(selectedType, new BitrateState(
                                        ABR, abrBitrateSpec, abrBitrateSpec.closestStepForBitrate(currentKbps)));
                            }
                        }
                        break;
                    case VBR:
                        if (vbrBitrateSpec != null) {
                            if (existingState == null || existingState.forBitrateType != VBR) {
                                bitrateStates.put(selectedType, new BitrateState(
                                        VBR, vbrBitrateSpec, vbrBitrateSpec.defaultStep()));
                            }
                        }
                        break;
                }
            }
        }
    }

    void updateCurrentBitrateStep(int currentStep) {
        final BitrateState existingState = bitrateStates.get(selectedType);
        // Existing state could be null if this is called after a rotation, for example, even if the
        // progressbar is hidden.
        if (existingState != null) {
            bitrateStates.put(selectedType, new BitrateState(
                    existingState.forBitrateType, existingState.bitrateSpec, currentStep));
        }
    }

    static class BitrateState {
        final BitrateType forBitrateType;
        final BitrateRange bitrateSpec;
        final int currentStep;

        BitrateState(@NonNull BitrateType forBitrateType,
                     @NonNull BitrateRange bitrateSpec,
                     int currentStep) {
            this.forBitrateType = forBitrateType;
            this.bitrateSpec = bitrateSpec;
            this.currentStep = currentStep;
        }
    }
}
