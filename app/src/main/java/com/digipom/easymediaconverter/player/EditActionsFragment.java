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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.edit.EditAction;

public class EditActionsFragment extends Fragment {
    public interface OnEditActionsFragmentInteractionListener {
        void onActionSelected(@NonNull EditAction action);
    }

    private Button convertButton;
    private Button convertToVideoButton;
    private Button extractAudioButton;
    private Button trimButton;
    private Button cutButton;
    private Button adjustSpeedButton;
    private Button adjustVolumeButton;
    private Button addSilenceButton;
    private Button normalizeButton;
    private Button splitButton;
    private Button combineButton;
    private Button setAsRingtoneButton;

    private ColorStateList defaultBackgroundTintList;
    private ColorStateList defaultTextColors;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_edit_actions, container, false);
        final PlayerViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);
        convertButton = view.findViewById(R.id.convert_button);
        convertToVideoButton = view.findViewById(R.id.convert_to_video_button);
        extractAudioButton = view.findViewById(R.id.extract_audio_button);
        trimButton = view.findViewById(R.id.trim_button);
        cutButton = view.findViewById(R.id.cut_button);
        adjustSpeedButton = view.findViewById(R.id.adjust_speed_button);
        adjustVolumeButton = view.findViewById(R.id.adjust_volume_button);
        addSilenceButton = view.findViewById(R.id.add_silence_button);
        normalizeButton = view.findViewById(R.id.normalize_button);
        splitButton = view.findViewById(R.id.split_button);
        combineButton = view.findViewById(R.id.combine_button);
        setAsRingtoneButton = view.findViewById(R.id.set_as_ringtone_button);

        defaultBackgroundTintList = convertButton.getBackgroundTintList();
        defaultTextColors = convertButton.getTextColors();

        convertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.CONVERT);
            }
        });
        convertToVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.CONVERT_TO_VIDEO);
            }
        });
        extractAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.EXTRACT_AUDIO);
            }
        });
        trimButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.TRIM);
            }
        });
        cutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.CUT);
            }
        });
        adjustSpeedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.ADJUST_SPEED);
            }
        });
        adjustVolumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.ADJUST_VOLUME);
            }
        });
        addSilenceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.ADD_SILENCE);
            }
        });
        normalizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.NORMALIZE);
            }
        });
        splitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.SPLIT);
            }
        });
        combineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.COMBINE);
            }
        });
        setAsRingtoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEditAction(EditAction.SET_AS_RINGTONE);
            }
        });

        extractAudioButton.setVisibility(View.GONE);
        viewModel.getObservableVideoSize().observe(getViewLifecycleOwner(), new Observer<Size>() {
            @Override
            public void onChanged(Size size) {
                final boolean adjustForVideo = size != null && size.getWidth() > 0 && size.getHeight() > 0;
                if (adjustForVideo) {
                    convertToVideoButton.setVisibility(View.GONE);
                    extractAudioButton.setVisibility(View.VISIBLE);
                    addSilenceButton.setVisibility(View.GONE);
                    combineButton.setVisibility(View.GONE);
                }
            }
        });

        return view;
    }

    private void handleEditAction(@NonNull EditAction action) {
        final OnEditActionsFragmentInteractionListener listener = (OnEditActionsFragmentInteractionListener) getActivity();
        if (listener != null && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            listener.onActionSelected(action);
        }
    }

    void highlightAction(@NonNull EditAction action) {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        updateButtonStyle(context, convertButton, action == EditAction.CONVERT);
        updateButtonStyle(context, convertToVideoButton, action == EditAction.CONVERT_TO_VIDEO);
        updateButtonStyle(context, extractAudioButton, action == EditAction.EXTRACT_AUDIO);
        updateButtonStyle(context, trimButton, action == EditAction.TRIM);
        updateButtonStyle(context, cutButton, action == EditAction.CUT);
        updateButtonStyle(context, adjustSpeedButton, action == EditAction.ADJUST_SPEED);
        updateButtonStyle(context, adjustVolumeButton, action == EditAction.ADJUST_VOLUME);
        updateButtonStyle(context, addSilenceButton, action == EditAction.ADD_SILENCE);
        updateButtonStyle(context, normalizeButton, action == EditAction.NORMALIZE);
        updateButtonStyle(context, splitButton, action == EditAction.SPLIT);
        updateButtonStyle(context, combineButton, action == EditAction.COMBINE);
        updateButtonStyle(context, setAsRingtoneButton, action == EditAction.SET_AS_RINGTONE);
    }

    private void updateButtonStyle(@NonNull Context context, @NonNull Button button, boolean setHighlighted) {
        if (setHighlighted) {
            button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.highlightedActionBackgroundTintColor)));
            button.setTextColor(Color.WHITE);
            TextViewCompat.setCompoundDrawableTintList(button, ColorStateList.valueOf(Color.WHITE));
        } else {
            button.setBackgroundTintList(defaultBackgroundTintList);
            button.setTextColor(defaultTextColors);
            TextViewCompat.setCompoundDrawableTintList(button, defaultTextColors);
        }
    }
}
