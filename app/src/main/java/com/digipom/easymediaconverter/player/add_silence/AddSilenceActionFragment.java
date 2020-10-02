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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.simple_time_picker.TimePickerDialogFragment;
import com.digipom.easymediaconverter.utils.DurationFormatterUtils;
import com.digipom.easymediaconverter.utils.IntentUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Activity.RESULT_OK;
import static com.digipom.easymediaconverter.simple_time_picker.TimePickerDialogFragment.INTENT_EXTRA_MS;

public class AddSilenceActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnAddSilenceActionFragmentInteractionListener {
        void onAddSilenceSelected(@NonNull Uri targetUri,
                                  @NonNull String targetFileName,
                                  long silenceInsertionPointMs, long silenceDurationMs);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;
    private static final int UPDATE_INSERTION_POINT_REQUEST_CODE = 2;
    private static final int UPDATE_SILENCE_DURATION_REQUEST_CODE = 3;

    @NonNull
    public static AddSilenceActionFragment create() {
        return new AddSilenceActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_silence_action, container, false);
    }

    private final StringBuilder builder = new StringBuilder();
    private AddSilenceActionViewModel viewModel;
    private PlayerViewModel sharedViewModel;
    private TextView silenceInsertionPointTextView;
    private SeekBar seekBar;
    private TextView silenceTimeDurationTextView;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Also scope the viewModel to the activity, so we can refer to it from our picker dialog.
        viewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(AddSilenceActionViewModel.class);
        sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        silenceInsertionPointTextView = view.findViewById(R.id.silence_insertion_point_textview);
        seekBar = view.findViewById(R.id.seekbar);
        silenceTimeDurationTextView = view.findViewById(R.id.silence_time_duration_textview);

        sharedViewModel.getObservableDurationMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long newValue) {
                viewModel.setDurationMs(Objects.requireNonNull(newValue));
            }
        });
        final AtomicBoolean isSyncingRangeBarToTimes = new AtomicBoolean(false);
        final AtomicBoolean isUserModifyingSeekBar = new AtomicBoolean(false);
        viewModel.insertionPointMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long insertionPointMs) {
                updateSilenceInsertionPointText();
                // Also sync the seek bar, if the user isn't currently manipulating it.
                if (!isUserModifyingSeekBar.get()) {
                    final float ratio = viewModel.getRatioForMs(Objects.requireNonNull(insertionPointMs));
                    final int index = (int) (ratio * seekBar.getMax());
                    if (index != seekBar.getProgress()) {
                        isSyncingRangeBarToTimes.set(true);
                        seekBar.setProgress(index);
                        isSyncingRangeBarToTimes.set(false);
                    }
                }
            }
        });
        viewModel.silenceDurationMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long newValue) {
                DurationFormatterUtils.formatDurationWithTenths(builder, silenceTimeDurationTextView, Objects.requireNonNull(newValue));

            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isSyncingRangeBarToTimes.get()) {
                    return;
                }

                final float ratio = (float) seekBar.getProgress() / seekBar.getMax();
                viewModel.onSeekBarChanged(ratio);
                sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.insertionPointMs().getValue()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserModifyingSeekBar.set(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserModifyingSeekBar.set(false);
            }
        });
        silenceInsertionPointTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final FragmentManager fragmentManager = getParentFragmentManager();
                    TimePickerDialogFragment.show(fragmentManager, AddSilenceActionFragment.this,
                            UPDATE_INSERTION_POINT_REQUEST_CODE,
                            getString(R.string.select_time_to_insert_silence),
                            Objects.requireNonNull(viewModel.insertionPointMs().getValue()),
                            0,
                            Objects.requireNonNull(sharedViewModel.getObservableDurationMs().getValue()));
                }
            }
        });
        silenceTimeDurationTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final FragmentManager fragmentManager = getParentFragmentManager();
                    TimePickerDialogFragment.show(fragmentManager, AddSilenceActionFragment.this,
                            UPDATE_SILENCE_DURATION_REQUEST_CODE,
                            getString(R.string.silence_duration),
                            Objects.requireNonNull(viewModel.silenceDurationMs().getValue()),
                            viewModel.getMinSilenceDurationMs(),
                            viewModel.getMaxSilenceDurationMs());
                }
            }
        });

        // TODO making sure view model is in sync with us if process was destroyed?
    }

    @Override
    public boolean shouldShowMainButton() {
        return true;
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
                            final long silenceInsertionPointMs = Objects.requireNonNull(viewModel.insertionPointMs().getValue());
                            final long silenceDurationMs = Objects.requireNonNull(viewModel.silenceDurationMs().getValue());
                            ((OnAddSilenceActionFragmentInteractionListener) activity).onAddSilenceSelected(target, outputFilename, silenceInsertionPointMs, silenceDurationMs);
                        }
                    });
        } else if (requestCode == UPDATE_INSERTION_POINT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                final long ms = data.getLongExtra(INTENT_EXTRA_MS, -1);
                if (ms >= 0) {
                    viewModel.onInsertionPointModified(ms);
                    sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.insertionPointMs().getValue()));
                }
            }
        } else if (requestCode == UPDATE_SILENCE_DURATION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                final long ms = data.getLongExtra(INTENT_EXTRA_MS, -1);
                if (ms > 0) {
                    viewModel.onSilenceDurationUpdated(ms);
                }
            }
        }
    }

    private void updateSilenceInsertionPointText() {
        final long insertionPointMs = Objects.requireNonNull(viewModel.insertionPointMs().getValue());
        DurationFormatterUtils.formatDurationWithTenths(builder, silenceInsertionPointTextView, insertionPointMs);
    }
}
