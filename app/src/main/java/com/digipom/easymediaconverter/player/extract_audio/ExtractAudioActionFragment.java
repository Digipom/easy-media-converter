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
package com.digipom.easymediaconverter.player.extract_audio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Objects;

public class ExtractAudioActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnExtractAudioActionFragmentInteractionListener {
        void onExtractAudioSelected(@NonNull Uri targetUri,
                                    @NonNull String targetFileName,
                                    @NonNull OutputFormatType outputFormatType);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;

    @NonNull
    public static ExtractAudioActionFragment create() {
        return new ExtractAudioActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_extract_audio_action, container, false);
    }

    private ExtractAudioActionViewModel viewModel;
    private ChipGroup audioChipGroup;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ExtractAudioActionViewModel.class);
        final PlayerViewModel sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());
        final View view = Objects.requireNonNull(getView());

        audioChipGroup = view.findViewById(R.id.audio_conversions_chipgroup);

        final Chip mp3Chip = view.findViewById(R.id.chip_button_mp3);
        final Chip m4aChip = view.findViewById(R.id.chip_button_m4a);
        final Chip aacChip = view.findViewById(R.id.chip_button_aac);
        final Chip oggChip = view.findViewById(R.id.chip_button_ogg);
        final Chip opusChip = view.findViewById(R.id.chip_button_opus);
        final Chip flacChip = view.findViewById(R.id.chip_button_flac);
        final Chip pcmChip = view.findViewById(R.id.chip_button_pcm);

        audioChipGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                if (getActivity() != null) {
                    ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
                }
            }
        });
        mp3Chip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onMp3Selected();
                }
            }
        });
        m4aChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onM4aSelected();
                }
            }
        });
        aacChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onAacSelected();
                }
            }
        });
        oggChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onOggSelected();
                }
            }
        });
        opusChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onOpusSelected();
                }
            }
        });
        flacChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onFlacSelected();
                }
            }
        });
        pcmChip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onPcmSelected();
                }
            }
        });

        if (savedInstanceState == null) {
            if (viewModel.isInputMP4()) {
                m4aChip.setChecked(true);
            } else {
                mp3Chip.setChecked(true);
            }
        }

        // TODO making sure view model is in sync with us if process was destroyed?
    }

    @Override
    public boolean shouldShowMainButton() {
        return (audioChipGroup.getVisibility() == View.VISIBLE && audioChipGroup.getCheckedChipId() != View.NO_ID);
    }

    @Override
    public void onMainButtonTapped() {
        final String mimeType = viewModel.getMimeTypeForOutputSelection();
        final String outputFilename = viewModel.getDefaultOutputFilename();

        // We need a destination URI.
        final Intent createDocumentIntent = IntentUtils.getCreateDocumentRequest(mimeType, outputFilename);
        startActivityForResult(createDocumentIntent, CREATE_DOCUMENT_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCUMENT_REQUEST_CODE) {
            final FragmentActivity activity = getActivity();
            IntentUtils.handleNewlyCreatedDocumentFromActivityResult(
                    Objects.requireNonNull(activity), resultCode, data, new IntentUtils.NewDocumentHandler() {
                        @Override
                        public void onReceivedUriForNewDocument(@NonNull Uri target) {
                            final String outputFilename = viewModel.getDefaultOutputFilename();
                            ((OnExtractAudioActionFragmentInteractionListener) activity).onExtractAudioSelected(
                                    target, outputFilename, viewModel.getOutputType());
                        }
                    });
        }
    }
}
