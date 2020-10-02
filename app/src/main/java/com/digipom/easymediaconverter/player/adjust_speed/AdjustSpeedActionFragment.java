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
package com.digipom.easymediaconverter.player.adjust_speed;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.utils.IntentUtils;

import java.util.Locale;
import java.util.Objects;

public class AdjustSpeedActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnAdjustSpeedActionFragmentInteractionListener {
        void onAdjustSpeedSelected(@NonNull Uri targetUri, @NonNull String targetFileName, float speed);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;

    @NonNull
    public static AdjustSpeedActionFragment create() {
        return new AdjustSpeedActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_adjust_speed_action, container, false);
    }

    private AdjustSpeedActionViewModel viewModel;
    private TextView relativeSpeedTextView;
    private SeekBar seekBar;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AdjustSpeedActionViewModel.class);
        final PlayerViewModel sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        relativeSpeedTextView = view.findViewById(R.id.relative_speed_textview);
        seekBar = view.findViewById(R.id.seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSpeedText();
                if (getActivity() != null) {
                    ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No-op
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No-op
            }
        });
        final ImageView leftAdjustButton = view.findViewById(R.id.left_adjust_arrow);
        final ImageView rightAdjustButton = view.findViewById(R.id.right_adjust_arrow);
        leftAdjustButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float speed = seekBarPositionToRelativeSpeed();
                speed = Math.round((speed - 0.05f) * 20f) / 20f;
                seekBar.setProgress(relativeSpeedToSeekBarPosition(speed));
            }
        });
        rightAdjustButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float speed = seekBarPositionToRelativeSpeed();
                speed = Math.round((speed + 0.05f) * 20f) / 20f;
                seekBar.setProgress(relativeSpeedToSeekBarPosition(speed));
            }
        });

        // Set initial state
        updateSpeedText();
        ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
    }

    @Override
    public boolean shouldShowMainButton() {
        return seekBarPositionToRelativeSpeed() <= 0.99f
                || seekBarPositionToRelativeSpeed() >= 1.01f;
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
                            final float speed = seekBarPositionToRelativeSpeed();
                            final String outputFilename = viewModel.getDefaultOutputFilename();
                            ((OnAdjustSpeedActionFragmentInteractionListener) activity).onAdjustSpeedSelected(target, outputFilename, speed);
                        }
                    });
        }
    }

    private void updateSpeedText() {
        final float relativeSpeed = seekBarPositionToRelativeSpeed();
        relativeSpeedTextView.setText(String.format(Locale.getDefault(), "%1.2fx", relativeSpeed));
    }

    private float seekBarPositionToRelativeSpeed() {
        final float ratio = (float) seekBar.getProgress() / seekBar.getMax();
        return seekBarRatioToRelativeSpeed(ratio);
    }

    private float seekBarRatioToRelativeSpeed(float ratio) {
        // Exponential method
        return (float) Math.pow(2, ratio * 2 - 1);
    }

    private int relativeSpeedToSeekBarPosition(float speed) {
        final float ratio = relativeSpeedToSeekBarRatio(speed);
        int position = (int) (ratio * seekBar.getMax());
        position = Math.max(0, Math.min(position, seekBar.getMax()));
        return position;
    }

    private float relativeSpeedToSeekBarRatio(float speed) {
        // Reverse of exponential method, using Wolfram Alpha
        return (float) ((Math.log(speed) + Math.log(2)) / (2 * Math.log(2)));
    }
}
