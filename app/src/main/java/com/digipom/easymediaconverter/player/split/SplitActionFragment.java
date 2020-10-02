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

public class SplitActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnSplitActionFragmentInteractionListener {
        void onSplitActionSelected(@NonNull Uri firstTargetUri, @NonNull String firstTargetFileName,
                                   @NonNull Uri secondTargetUri, @NonNull String secondTargetFileName,
                                   long splitAtMs);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;
    private static final int UPDATE_SPLIT_POINT_REQUEST_CODE = 2;

    @NonNull
    public static SplitActionFragment create() {
        return new SplitActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_split_action, container, false);
    }

    private final StringBuilder builder = new StringBuilder();
    private SplitActionViewModel viewModel;
    private PlayerViewModel sharedViewModel;
    private TextView splitAtTextView;
    private SeekBar seekBar;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SplitActionViewModel.class);
        sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        splitAtTextView = view.findViewById(R.id.split_point_textview);
        seekBar = view.findViewById(R.id.seekbar);
        final TextView splitTooShortWarning = view.findViewById(R.id.split_too_short_warning);

        sharedViewModel.getObservableDurationMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long newValue) {
                viewModel.setDurationMs(Objects.requireNonNull(newValue));
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
        final AtomicBoolean isSyncingRangeBarToTimes = new AtomicBoolean(false);
        final AtomicBoolean isUserModifyingSeekBar = new AtomicBoolean(false);
        viewModel.splitPointMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long splitPointMs) {
                updateSplitText();
                // Also sync the seek bar, if the user isn't currently manipulating it.
                if (!isUserModifyingSeekBar.get()) {
                    final float ratio = viewModel.getRatioForMs(Objects.requireNonNull(splitPointMs));
                    final int index = (int) (ratio * seekBar.getMax());
                    if (index != seekBar.getProgress()) {
                        isSyncingRangeBarToTimes.set(true);
                        seekBar.setProgress(index);
                        isSyncingRangeBarToTimes.set(false);
                    }
                }
            }
        });
        viewModel.shouldShowSplitTooShortWarning().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean shouldShowWarning) {
                splitTooShortWarning.setVisibility(Objects.requireNonNull(shouldShowWarning) ? View.VISIBLE : View.INVISIBLE);
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
                sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.splitPointMs().getValue()));
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
        splitAtTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final FragmentManager fragmentManager = getParentFragmentManager();
                    TimePickerDialogFragment.show(fragmentManager, SplitActionFragment.this,
                            UPDATE_SPLIT_POINT_REQUEST_CODE,
                            getString(R.string.select_split_position),
                            Objects.requireNonNull(viewModel.splitPointMs().getValue()),
                            0,
                            Objects.requireNonNull(sharedViewModel.getObservableDurationMs().getValue()));
                }
            }
        });

        // Set initial state
        updateSplitText();
        ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
    }

    @Override
    public boolean shouldShowMainButton() {
        return Objects.requireNonNull(viewModel.shouldShowFab().getValue());
    }

    @Override
    public void onMainButtonTapped() {
        // We need TWO destination URIs. Get the first one here.
        createDocument(viewModel.getDefaultOutputFilenameForFirstDocument());
    }

    private void createDocument(@NonNull String outputFilename) {
        final String mimeType = viewModel.getMimeTypeForOutputSelection();
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
                            viewModel.onReceivedNextDocument(target);
                            if (viewModel.needsMoreDocuments()) {
                                // We obtained one document. Now obtain the second one.
                                createDocument(viewModel.getDefaultOutputFilenameForSecondDocument());
                            } else {
                                viewModel.setNoLongerNeedCleanup();
                                final long splitAtMs = Objects.requireNonNull(viewModel.splitPointMs().getValue());
                                ((OnSplitActionFragmentInteractionListener) activity).onSplitActionSelected(viewModel.getTargetUri(0),
                                        viewModel.getDefaultOutputFilenameForFirstDocument(),
                                        viewModel.getTargetUri(1),
                                        viewModel.getDefaultOutputFilenameForSecondDocument(), splitAtMs);
                            }
                        }

                        @Override
                        public void onFailedToReceiveUriForNewDocument() {
                            viewModel.onFailedToReceiveNextDocument();
                        }
                    });
        } else if (requestCode == UPDATE_SPLIT_POINT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                final long ms = data.getLongExtra(INTENT_EXTRA_MS, -1);
                if (ms >= 0) {
                    viewModel.onSplitPointModified(ms);
                    sharedViewModel.onActionWantsToChangePlaybackPositionToOffset(Objects.requireNonNull(viewModel.splitPointMs().getValue()));
                }
            }
        }
    }

    private void updateSplitText() {
        final long splitTimeMs = Objects.requireNonNull(viewModel.splitPointMs().getValue());
        DurationFormatterUtils.formatDurationWithTenths(builder, splitAtTextView, splitTimeMs);
    }
}
