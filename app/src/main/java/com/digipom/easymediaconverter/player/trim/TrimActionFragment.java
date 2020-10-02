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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.appyvet.materialrangebar.RangeBar;
import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.simple_time_picker.TimePickerDialogFragment;
import com.digipom.easymediaconverter.utils.IntentUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Activity.RESULT_OK;
import static com.digipom.easymediaconverter.config.StaticConfig.LOGCAT_LOGGING_ON;
import static com.digipom.easymediaconverter.simple_time_picker.TimePickerDialogFragment.INTENT_EXTRA_MS;
import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.formatDurationWithTenths;

public class TrimActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    private static final String TAG = TrimActionFragment.class.getName();

    public interface OnTrimActionFragmentInteractionListener {
        void onTrimSelected(@NonNull Uri targetUri,
                            @NonNull String targetFileName,
                            long trimBeforeMs,
                            long trimAfterMs);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;
    private static final int UPDATE_TRIM_BEFORE_TIME_REQUEST_CODE = 2;
    private static final int UPDATE_TRIM_AFTER_TIME_REQUEST_CODE = 3;

    @NonNull
    public static TrimActionFragment create() {
        return new TrimActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trim_action, container, false);
    }

    private TrimActionViewModel viewModel;
    private PlayerViewModel sharedViewModel;
    private RangeBar rangeSeekBar;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TrimActionViewModel.class);
        sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        final TextView trimBeforeTextView = view.findViewById(R.id.trim_before_textview);
        final TextView trimAfterTextView = view.findViewById(R.id.trim_after_textview);
        final String trimBeforeContentDescription = getString(R.string.trimBeforeContentDescription);
        final String trimAfterContentDescription = getString(R.string.trimAfterContentDescription);
        rangeSeekBar = view.findViewById(R.id.range_seekbar);
        final TextView recordingTooShortWarning = view.findViewById(R.id.recording_too_short_warning);

        rangeSeekBar.setRangePinsByIndices(0, (int) rangeSeekBar.getTickEnd());

        sharedViewModel.getObservableDurationMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long durationMs) {
                viewModel.setDurationMs(Objects.requireNonNull(durationMs));
                // The range seek bar now represents different times, so we need an update for this
                // as well.
                viewModel.onRangeSeekBarChanged(getLeftRangebarRatio(), getRightRangebarRatio());
            }
        });
        viewModel.shouldShowFab().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean shouldShowFab) {
                if (getActivity() != null) {
                    ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
                }
            }
        });
        viewModel.shouldShowRecordingTooShortWarning().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean shouldShowWarning) {
                recordingTooShortWarning.setVisibility(Objects.requireNonNull(shouldShowWarning) ? View.VISIBLE : View.INVISIBLE);
            }
        });
        final AtomicBoolean isSyncingRangeBarToTimes = new AtomicBoolean(false);
        final AtomicBoolean isUserModifyingRangeSeekBar = new AtomicBoolean(false);
        final StringBuilder builder = new StringBuilder();
        viewModel.trimBeforeMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long trimBeforeTimeMs) {
                formatDurationWithTenths(builder, trimBeforeTextView, Objects.requireNonNull(trimBeforeTimeMs), trimBeforeContentDescription);

                // Also sync the range seek bar.
                // To prevent cascades, we do a direct update of the range seekbar here.
                if (!isUserModifyingRangeSeekBar.get()) {
                    final float ratio = viewModel.getRatioForMs(trimBeforeTimeMs);
                    final int index = (int) (ratio * rangeSeekBar.getTickEnd());
                    if (index != rangeSeekBar.getLeftIndex()) {
                        if (LOGCAT_LOGGING_ON) {
                            Log.v(TAG, "trimBeforeMs updating seekbar index to " + index);
                        }
                        isSyncingRangeBarToTimes.set(true);
                        rangeSeekBar.setRangePinsByIndices(index, rangeSeekBar.getRightIndex());
                        isSyncingRangeBarToTimes.set(false);
                    }
                }
            }
        });
        viewModel.trimAfterMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long trimAfterTimeMs) {
                formatDurationWithTenths(builder, trimAfterTextView, Objects.requireNonNull(trimAfterTimeMs), trimAfterContentDescription);

                // Also sync the range seek bar.
                // To prevent cascades, we do a direct update of the range seekbar here.
                if (!isUserModifyingRangeSeekBar.get()) {
                    final float ratio = viewModel.getRatioForMs(trimAfterTimeMs);
                    final int index = (int) (ratio * rangeSeekBar.getTickEnd());
                    if (index != rangeSeekBar.getRightIndex()) {
                        if (LOGCAT_LOGGING_ON) {
                            Log.v(TAG, "trimAfterMs updating seekbar index to " + index);
                        }
                        isSyncingRangeBarToTimes.set(true);
                        rangeSeekBar.setRangePinsByIndices(rangeSeekBar.getLeftIndex(), index);
                        isSyncingRangeBarToTimes.set(false);
                    }
                }
            }
        });
        rangeSeekBar.setOnRangeBarChangeListener(new RangeBar.OnRangeBarChangeListener() {
            int previousLeftPinIndex;
            int previousRightPinIndex;

            @Override
            public void onRangeChangeListener(RangeBar rangeBar, int leftPinIndex, int rightPinIndex,
                                              String leftPinValue, String rightPinValue) {
                if (isSyncingRangeBarToTimes.get()) {
                    // Only handle callbacks of the rangebar that were caused by direct user
                    // interaction.
                    return;
                }

                isUserModifyingRangeSeekBar.set(true);

                float leftRangebarRatio = getLeftRangebarRatio();
                float rightRangebarRatio = getRightRangebarRatio();

                if (leftPinIndex != previousLeftPinIndex || rightPinIndex != previousRightPinIndex) {
                    viewModel.onRangeSeekBarChanged(leftRangebarRatio, rightRangebarRatio);

                    // If user modifies the range bar directly, then also update the current playback
                    // position.
                    if (leftPinIndex != previousLeftPinIndex) {
                        sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.trimBeforeMs().getValue()));
                        previousLeftPinIndex = leftPinIndex;
                    } else if (rightPinIndex != previousRightPinIndex) {
                        sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.trimAfterMs().getValue()));
                        previousRightPinIndex = rightPinIndex;
                    }
                }

                isUserModifyingRangeSeekBar.set(false);
            }
        });
        trimBeforeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final FragmentManager fragmentManager = getParentFragmentManager();
                    final long defaultValue = Objects.requireNonNull(viewModel.trimBeforeMs().getValue());
                    final long minValue = 0;
                    final long maxValue = Objects.requireNonNull(viewModel.trimAfterMs().getValue());

                    TimePickerDialogFragment.show(fragmentManager, TrimActionFragment.this,
                            UPDATE_TRIM_BEFORE_TIME_REQUEST_CODE,
                            getString(R.string.trimBefore),
                            defaultValue, minValue, maxValue);
                }
            }
        });
        trimAfterTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final FragmentManager fragmentManager = getParentFragmentManager();
                    final long defaultValue = Objects.requireNonNull(viewModel.trimAfterMs().getValue());
                    final long minValue = Objects.requireNonNull(viewModel.trimBeforeMs().getValue());
                    final long maxValue = Objects.requireNonNull(sharedViewModel.getObservableDurationMs().getValue());

                    TimePickerDialogFragment.show(fragmentManager, TrimActionFragment.this,
                            UPDATE_TRIM_AFTER_TIME_REQUEST_CODE,
                            getString(R.string.trimAfter),
                            defaultValue, minValue, maxValue);
                }
            }
        });
        // TODO making sure view model is in sync with us if process was destroyed?
    }

    @Override
    public boolean shouldShowMainButton() {
        return viewModel != null && Objects.requireNonNull(viewModel.shouldShowFab().getValue());
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
                            final long trimBeforeMs = Objects.requireNonNull(viewModel.trimBeforeMs().getValue());
                            final long trimAfterMs = Objects.requireNonNull(viewModel.trimAfterMs().getValue());
                            ((OnTrimActionFragmentInteractionListener) activity).onTrimSelected(target, outputFilename, trimBeforeMs, trimAfterMs);
                        }
                    });
        } else if (requestCode == UPDATE_TRIM_BEFORE_TIME_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                final long ms = data.getLongExtra(INTENT_EXTRA_MS, -1);
                if (ms >= 0) {
                    viewModel.onTrimBeforeModified(ms);
                    sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.trimBeforeMs().getValue()));
                }
            }
        } else if (requestCode == UPDATE_TRIM_AFTER_TIME_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                final long ms = data.getLongExtra(INTENT_EXTRA_MS, -1);
                if (ms >= 0) {
                    viewModel.onTrimAfterModified(ms);
                    sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.trimAfterMs().getValue()));
                }
            }
        }
    }

    private float getLeftRangebarRatio() {
        return (float) rangeSeekBar.getLeftIndex() / rangeSeekBar.getTickEnd();
    }

    private float getRightRangebarRatio() {
        return (float) rangeSeekBar.getRightIndex() / rangeSeekBar.getTickEnd();
    }
}
