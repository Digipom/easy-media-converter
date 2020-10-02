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
package com.digipom.easymediaconverter.player.adjust_volume;

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

public class AdjustVolumeActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnAdjustVolumeActionFragmentInteractionListener {
        void onAdjustVolumeSelected(@NonNull Uri targetUri, @NonNull String targetFileName, float db);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;

    @NonNull
    public static AdjustVolumeActionFragment create() {
        return new AdjustVolumeActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_adjust_volume_action, container, false);
    }

    private AdjustVolumeActionViewModel viewModel;
    private TextView relativeVolumeTextView;
    private TextView dbVolumeTextView;
    private SeekBar seekBar;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AdjustVolumeActionViewModel.class);
        final PlayerViewModel sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        relativeVolumeTextView = view.findViewById(R.id.relative_volume_textview);
        dbVolumeTextView = view.findViewById(R.id.db_volume_textview);
        seekBar = view.findViewById(R.id.seekbar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText();
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
                float db = seekBarPositionToDb();
                db = Math.round((db - 0.1f) * 10f) / 10f;
                seekBar.setProgress(dbToSeekBarPosition(db));
            }
        });
        rightAdjustButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float db = seekBarPositionToDb();
                db = Math.round((db + 0.1f) * 10f) / 10f;
                seekBar.setProgress(dbToSeekBarPosition(db));
            }
        });

        // Set initial state
        updateVolumeText();
        ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
    }

    @Override
    public boolean shouldShowMainButton() {
        final float db = seekBarPositionToDb();
        final float ratio = dbToRelativeAmplitude(db);
        return ratio <= 0.99f || ratio >= 1.01f;
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
                            final float db = seekBarPositionToDb();
                            final String outputFilename = viewModel.getDefaultOutputFilename();
                            ((OnAdjustVolumeActionFragmentInteractionListener) activity).onAdjustVolumeSelected(target, outputFilename, db);
                        }
                    });
        }
    }

    private void updateVolumeText() {
        final float db = seekBarPositionToDb();
        relativeVolumeTextView.setText(String.format(Locale.getDefault(), "%1.2fx", dbToRelativeAmplitude(db)));
        dbVolumeTextView.setText(String.format(Locale.getDefault(), "%.1f dB", db));
    }

    private float seekBarPositionToDb() {
        final float ratio = (float) seekBar.getProgress() / seekBar.getMax();
        return seekBarRatioToDb(ratio);
    }

    private float seekBarRatioToDb(float ratio) {
        return ratio * 40 - 20;
    }

    private static float dbToRelativeAmplitude(float dbGain) {
        return (float) (Math.pow(10, dbGain / 20.0));
    }

    private int dbToSeekBarPosition(float db) {
        final float ratio = dbToSeekBarRatio(db);
        int position = (int) (ratio * seekBar.getMax());
        position = Math.max(0, Math.min(position, seekBar.getMax()));
        return position;
    }

    private float dbToSeekBarRatio(float db) {
        // Reverse of seekBarRatioToDb
        return (db + 20) / 40;
    }
}
