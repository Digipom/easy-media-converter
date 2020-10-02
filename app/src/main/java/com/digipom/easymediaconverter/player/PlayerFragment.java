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

import android.os.Bundle;
import android.os.Handler;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.Observer;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.formatDuration;

public class PlayerFragment extends Fragment implements SurfaceHolder.Callback {
    private final PlayerInfoUpdater playerInfoUpdater = new PlayerInfoUpdater();
    private final PauseOnEnteringBackgroundObserver pauseOnEnteringBackgroundObserver = new PauseOnEnteringBackgroundObserver();
    private PlayerViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        getLifecycle().addObserver(playerInfoUpdater);
        getLifecycle().addObserver(pauseOnEnteringBackgroundObserver);

        final View view = Objects.requireNonNull(getView());
        final View surfaceViewContainer = view.findViewById(R.id.surface_view_container);
        final SurfaceView surfaceView = view.findViewById(R.id.surface_view);
        final TextView elapsedTimeTextView = view.findViewById(R.id.current_position_textview);
        final TextView totalDurationTextView = view.findViewById(R.id.total_duration_textview);
        final SeekBar playerSeekBar = view.findViewById(R.id.player_seekbar);
        final ImageButton loopButton = view.findViewById(R.id.loop_imageview);
        final ImageButton rewindButton = view.findViewById(R.id.rewind_imageview);
        final ImageButton playPauseImageButton = view.findViewById(R.id.play_pause_imageview);
        final ImageButton fastForwardButton = view.findViewById(R.id.fastforward_imageview);
        final Button speedTextButton = view.findViewById(R.id.playback_speed_textview);

        // Elapsed and total time
        final String elapsedTimeContentDescriptionFormatString = getString(R.string.elaspedTimeContentDescription);
        final String totalTimeContentDescriptionFormatString = getString(R.string.totalTimeContentDescription);

        final StringBuilder stringBuilder = new StringBuilder();
//        formatDuration(stringBuilder, totalDurationTextView, item.durationMs,
//                totalTimeContentDescriptionFormatString);
        // TODO
        formatDuration(stringBuilder, totalDurationTextView, 0,
                totalTimeContentDescriptionFormatString);

        totalDurationTextView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (elapsedTimeTextView.getWidth() != totalDurationTextView.getWidth()) {
                    elapsedTimeTextView.setWidth(totalDurationTextView.getWidth());
                    return false;
                }

                totalDurationTextView.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
        viewModel.getObservableElapsedTimeMs().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer elapsedTimeMs) {
                formatDuration(stringBuilder, elapsedTimeTextView, Objects.requireNonNull(elapsedTimeMs),
                        elapsedTimeContentDescriptionFormatString);
            }
        });
        viewModel.getObservableDurationMs().observe(getViewLifecycleOwner(), new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long durationMs) {
                formatDuration(stringBuilder, totalDurationTextView, Objects.requireNonNull(durationMs),
                        totalTimeContentDescriptionFormatString);
            }
        });

        // Seekbar
        final AtomicBoolean isUserTouchingSeekBar = new AtomicBoolean(false);
        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUserTouchingSeekBar.get()) {
                    viewModel.onSeekbarMovedToRatio((float) progress / (float) seekBar.getMax());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserTouchingSeekBar.set(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserTouchingSeekBar.set(false);
            }
        });
        viewModel.getObservableElapsedTimeMs().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer elapsedTimeMs) {
                if (!isUserTouchingSeekBar.get()) {
                    // Only do this update if the user isn't currently touching the seekbar.
                    final long durationMs = viewModel.getDurationMs();
                    final float ratio = (float) Objects.requireNonNull(elapsedTimeMs) / (float) durationMs;
                    playerSeekBar.setProgress((int) (ratio * playerSeekBar.getMax()));
                }
            }
        });

        // Loop button
        loopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.onLoopClicked();
            }
        });
        viewModel.getObservableIsLooping().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isLooping) {
                loopButton.setAlpha(Objects.requireNonNull(isLooping) ? 1f : 0.5f);
                loopButton.setContentDescription(isLooping ? getString(R.string.stop_looping) : getString(R.string.start_looping));
            }
        });

        // Rewind button
        rewindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.onRewindClicked();
            }
        });
        viewModel.getObservableElapsedTimeMs().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer elapsedTimeMs) {
                final boolean shouldBeEnabled = Objects.requireNonNull(elapsedTimeMs) > 0;
                rewindButton.setEnabled(shouldBeEnabled);
                rewindButton.setAlpha(shouldBeEnabled ? 1f : 0.5f);
            }
        });

        // Play/pause button
        playPauseImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.onPlayPausedClicked();
            }
        });
        viewModel.getObservableIsPlaying().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isPlaying) {
                if (Objects.requireNonNull(isPlaying)) {
                    playerInfoUpdater.startUpdating();
                    playPauseImageButton.setImageResource(R.drawable.ic_pause_circle_filled_black_36dp);
                    playPauseImageButton.setContentDescription(getText(R.string.pause));
                } else {
                    playerInfoUpdater.stopUpdating();
                    playPauseImageButton.setImageResource(R.drawable.ic_play_circle_filled_black_36dp);
                    playPauseImageButton.setContentDescription(getText(R.string.play));
                }
            }
        });

        // Fast-forward button
        fastForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.onFastForwardClicked();
            }
        });
        viewModel.getObservableElapsedTimeMs().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer elapsedTimeMs) {
                final long durationMs = viewModel.getDurationMs();
                boolean shouldBeEnabled = Objects.requireNonNull(elapsedTimeMs) < durationMs;
                fastForwardButton.setEnabled(shouldBeEnabled);
                fastForwardButton.setAlpha(shouldBeEnabled ? 1f : 0.5f);
            }
        });

        // Speed button
        viewModel.getObservablePlaybackSpeed().observe(getViewLifecycleOwner(), new Observer<Float>() {
            @Override
            public void onChanged(@Nullable Float speed) {
                final NumberFormat numberFormat = NumberFormat.getInstance();
                // Should always be true
                if (numberFormat instanceof DecimalFormat) {
                    numberFormat.setMinimumFractionDigits(0);
                    numberFormat.setMaximumFractionDigits(2);
                }
                final String formattedSpeed = getString(R.string.formattedSpeed, numberFormat.format(speed));
                final String formattedSpeedContentDescription = getString(R.string.formattedSpeedContentDescription,
                        formattedSpeed);
                speedTextButton.setText(formattedSpeed);
                speedTextButton.setContentDescription(formattedSpeedContentDescription);
            }
        });
        speedTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.onSpeedClicked();
            }
        });

        // Video
        surfaceView.getHolder().addCallback(this);

        viewModel.getObservableVideoSize().observe(getViewLifecycleOwner(), new Observer<Size>() {
            @Override
            public void onChanged(Size size) {
                if (size != null && size.getWidth() > 0 && size.getHeight() > 0) {
                    // Adjust surface layout based on specs
                    final ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
                    final float actualAspect = (float) size.getWidth() / (float) size.getHeight();
                    params.dimensionRatio = String.valueOf(actualAspect);
                    surfaceView.setLayoutParams(params);
                    surfaceViewContainer.setVisibility(View.VISIBLE);
                } else {
                    surfaceViewContainer.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        viewModel.setVideoSurfaceForPlayback(holder);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // No-op
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    // When playback is occurring, we want frequent updates of the elapsed time & seekbar progress
    private class PlayerInfoUpdater implements LifecycleObserver {
        private final Handler mainHandler = new Handler();
        private final Runnable callback = new Runnable() {
            @Override
            public void run() {
                viewModel.onWantsUpdatedPlaybackPosition();
                mainHandler.postDelayed(this, 1000 / 30);
            }
        };

        void startUpdating() {
            mainHandler.removeCallbacks(callback);  // Ensure not double-posted.
            mainHandler.post(callback);
        }

        void stopUpdating() {
            mainHandler.removeCallbacks(callback);
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        void onStop() {
            stopUpdating();
        }
    }

    private class PauseOnEnteringBackgroundObserver implements LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        void onStop() {
            // Only pause the playback if we're not about to be recreated
            if (!Objects.requireNonNull(getActivity()).isChangingConfigurations()) {
                Logger.d("UI is about to disappear");
                viewModel.onUIAboutToDisappear();
            }
        }
    }
}
