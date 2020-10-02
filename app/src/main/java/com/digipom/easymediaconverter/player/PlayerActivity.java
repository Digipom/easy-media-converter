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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.player.MainButtonInterfaces.HandleMainButtonTapListener;
import com.digipom.easymediaconverter.player.MainButtonInterfaces.HandleSecondaryFABTapListener;
import com.digipom.easymediaconverter.player.add_silence.AddSilenceActionFragment;
import com.digipom.easymediaconverter.player.adjust_speed.AdjustSpeedActionFragment;
import com.digipom.easymediaconverter.player.adjust_volume.AdjustVolumeActionFragment;
import com.digipom.easymediaconverter.player.combine.CombineActionFragment;
import com.digipom.easymediaconverter.player.convert.ConvertActionFragment;
import com.digipom.easymediaconverter.player.cut.CutActionFragment;
import com.digipom.easymediaconverter.player.extract_audio.ExtractAudioActionFragment;
import com.digipom.easymediaconverter.player.make_video.MakeVideoActionFragment;
import com.digipom.easymediaconverter.player.normalize.NormalizeActionFragment;
import com.digipom.easymediaconverter.player.ringtone.SetAsRingtoneActionFragment;
import com.digipom.easymediaconverter.player.split.SplitActionFragment;
import com.digipom.easymediaconverter.player.trim.TrimActionFragment;
import com.digipom.easymediaconverter.utils.logger.Logger;
import com.google.android.material.animation.AnimationUtils;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.digipom.easymediaconverter.utils.FontUtils.setToolbarFontToSansSerifCondensedItalic;

public class PlayerActivity extends AppCompatActivity implements
        MainButtonInterfaces.MainButtonController,
        EditActionsFragment.OnEditActionsFragmentInteractionListener,
        ConvertActionFragment.OnConvertActionFragmentInteractionListener,
        MakeVideoActionFragment.OnMakeVideoActionFragmentInteractionListener,
        ExtractAudioActionFragment.OnExtractAudioActionFragmentInteractionListener,
        TrimActionFragment.OnTrimActionFragmentInteractionListener,
        CutActionFragment.OnCutActionFragmentInteractionListener,
        AdjustSpeedActionFragment.OnAdjustSpeedActionFragmentInteractionListener,
        AdjustVolumeActionFragment.OnAdjustVolumeActionFragmentInteractionListener,
        AddSilenceActionFragment.OnAddSilenceActionFragmentInteractionListener,
        NormalizeActionFragment.OnNormalizeActionFragmentInteractionListener,
        SplitActionFragment.OnSplitActionFragmentInteractionListener,
        CombineActionFragment.OnCombineActionFragmentInteractionListener,
        SetAsRingtoneActionFragment.OnSetAsRingtoneActionFragmentInteractionListener {
    private static final String EXTRA_MEDIA_ITEM = "EXTRA_MEDIA_ITEM";

    private Toolbar toolbar;
    private View foregroundLayout;
    private ExtendedFloatingActionButton mainButton;
    private FloatingActionButton secondaryFab;
    private PlayerViewModel viewModel;

    public static void show(@NonNull Activity caller, @NonNull MediaItem item) {
        final Intent intent = new Intent(caller, PlayerActivity.class);
        intent.putExtra(EXTRA_MEDIA_ITEM, item);
        caller.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        findViewById(R.id.app_bar_layout).setOutlineProvider(null);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setToolbarFontToSansSerifCondensedItalic(toolbar);

        final View backgroundLayout = findViewById(R.id.background_layout);
        foregroundLayout = findViewById(R.id.foreground_layout);
        foregroundLayout.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Block touch events from passing behind us
                return true;
            }
        });
        final View foregroundHeaderLayout = findViewById(R.id.foreground_header_layout);
        final TextView headerTextView = findViewById(R.id.header_textview);

        foregroundHeaderLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (foregroundHeaderLayout.getHeight() <= 0) {
                    return false;
                }

                backgroundLayout.setPaddingRelative(0, 0, 0, foregroundHeaderLayout.getHeight());
                foregroundHeaderLayout.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });

        // TODO take the height of the background content into account? So we don't open all the way, for example, if we don't need to?
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (foregroundLayout.getTranslationY() <= 0) {
                    openBackgroundLayout(foregroundHeaderLayout);
                } else {
                    closeBackgroundLayout();
                }
            }
        });
        foregroundLayout.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                updateScreenTitle();
            }
        });

        mainButton = findViewById(R.id.main_button);
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Ask the fragment to handle it.
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final Fragment lastFragment = getLastHandler(HandleMainButtonTapListener.class);
                    if (lastFragment instanceof HandleMainButtonTapListener) {
                        ((HandleMainButtonTapListener) lastFragment).onMainButtonTapped();
                    }
                }
            }
        });
        secondaryFab = findViewById(R.id.secondary_fab);
        secondaryFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ask the fragment to handle it.
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    final Fragment lastFragment = getLastHandler(HandleSecondaryFABTapListener.class);
                    if (lastFragment instanceof HandleSecondaryFABTapListener) {
                        ((HandleSecondaryFABTapListener) lastFragment).onSecondaryFABTapped();
                    }
                }
            }
        });

        // Instantiate our fragments
        if (getSupportFragmentManager().findFragmentById(R.id.action_fragment) == null) {
            onActionSelected(EditAction.CONVERT);
        }

        // See if we were launched as a share target
        // Get intent, action and MIME type
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();
        MediaItem item = null;

        // See if we were launched via a share intent
        if (type != null && (type.startsWith("audio/") || type.startsWith("video/"))) {
            if (Intent.ACTION_SEND.equals(action)) {
                try {
                    final Uri mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (mediaUri != null) {
                        item = MediaItem.constructFromUri(this, mediaUri);
                    } else {
                        Logger.w("EXTRA_STREAM was null");
                    }
                } catch (Exception e) {
                    Logger.w(e);
                }
            } else if (Intent.ACTION_VIEW.equals(action)) {
                final Uri data = intent.getData();
                if (data != null) {
                    try {
                        item = MediaItem.constructFromUri(this, data);
                    } catch (Exception e) {
                        Logger.w(e);
                    }
                } else {
                    Logger.w("Launched with ACTION_VIEW with null data, intent: " + intent);
                }
            }
        } else {
            Logger.w("Launched with " + action + " for unsupported type " + type + ", intent: " + intent);
        }

        // Initialize media item
        if (item == null) {
            item = getIntent().getParcelableExtra(EXTRA_MEDIA_ITEM);
        }
        if (item == null) {
            Logger.w("Couldn't read item from intent: " + getIntent());
            Toast.makeText(this, R.string.couldnt_open_media_item, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            viewModel.setMediaItem(item);
        } catch (IOException e) {
            // TODO handle player init error
            Logger.w(e);
        }
        headerTextView.setText(item.getDisplayName());

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentActivityCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
                super.onFragmentActivityCreated(fm, f, savedInstanceState);
                if (f instanceof HandleMainButtonTapListener
                        || f instanceof HandleSecondaryFABTapListener)
                    updateFABVisibility();
            }
        }, false);

        // Sync UI state
        updateScreenTitle();

        final Fragment editActionFragment = getLastHandler(HandleMainButtonTapListener.class);
        if (editActionFragment instanceof HandleMainButtonTapListener) {
            highlightActionOnEditActionsList(getEditActionForCurrentlyShowingFragment((HandleMainButtonTapListener) editActionFragment));
            updateFabTitleAndIcon((HandleMainButtonTapListener) editActionFragment);
        }
        if (editActionFragment instanceof HandleSecondaryFABTapListener) {
            updateSecondaryFabIcon((HandleSecondaryFABTapListener) editActionFragment);
        }
    }

    private void openBackgroundLayout(@NonNull View foregroundHeaderLayout) {
        foregroundLayout
                .animate()
                .translationY(foregroundLayout.getHeight() - foregroundHeaderLayout.getHeight())
                .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
        setTitle(R.string.chooseAnAction);
    }

    private void closeBackgroundLayout() {
        foregroundLayout
                .animate()
                .translationY(0)
                .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
    }

    private void updateScreenTitle() {
        if (foregroundLayout.getTranslationY() > 0) {
            toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
            setTitle(R.string.chooseAnAction);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp);
            final Fragment lastFragment = getLastHandler(HandleMainButtonTapListener.class);
            if (lastFragment instanceof HandleMainButtonTapListener) {
                setTitle(getStringResForEditAction((HandleMainButtonTapListener) lastFragment));
            }
        }
    }

    // Should not be called from onCreate, because this calls into the fragments, which may not
    // yet be ready to be called.
    private void updateFABVisibility() {
        final Fragment fabHandler = getLastHandler(HandleMainButtonTapListener.class);
        if (fabHandler instanceof HandleMainButtonTapListener
                && ((HandleMainButtonTapListener) fabHandler).shouldShowMainButton()) {
            mainButton.setEnabled(true);
        } else {
            mainButton.setEnabled(false);
        }

        final Fragment secondFabHandler = getLastHandler(HandleSecondaryFABTapListener.class);
        if (secondFabHandler instanceof HandleSecondaryFABTapListener
                && ((HandleSecondaryFABTapListener) secondFabHandler).shouldShowSecondaryFAB()) {
            secondaryFab.show();
        } else {
            secondaryFab.hide();
        }
    }

    private void updateFabTitleAndIcon(@NonNull HandleMainButtonTapListener handler) {
        mainButton.setText(getStringResForEditAction(handler));
        mainButton.setIconResource(getDrawableResForEditAction(handler));
    }

    private void updateSecondaryFabIcon(@NonNull HandleSecondaryFABTapListener handler) {
        secondaryFab.setImageResource(getDrawableResForSecondaryAction(handler));
    }

    @Nullable
    private Fragment getLastHandler(@NonNull Class<?> listenerInterface) {
        final List<Fragment> fragmentList = getSupportFragmentManager().getFragments();
        for (int i = fragmentList.size() - 1; i > 0; --i) {
            final Fragment fragment = fragmentList.get(i);
            if (listenerInterface.isInstance(fragment)) {
                return fragment;
            }
        }

        return null;
    }

    // MainButtonController

    @Override
    public void onButtonStateUpdated() {
        updateFABVisibility();
    }

    // EditActionsFragment.OnEditActionsFragmentInteractionListener

    @Override
    public void onActionSelected(@NonNull EditAction action) {
        switch (action) {
            case CONVERT:
                handleShowActionFragment(ConvertActionFragment.create());
                break;
            case CONVERT_TO_VIDEO:
                handleShowActionFragment(MakeVideoActionFragment.create());
                break;
            case EXTRACT_AUDIO:
                handleShowActionFragment(ExtractAudioActionFragment.create());
                break;
            case TRIM:
                handleShowActionFragment(TrimActionFragment.create());
                break;
            case CUT:
                handleShowActionFragment(CutActionFragment.create());
                break;
            case ADJUST_SPEED:
                handleShowActionFragment(AdjustSpeedActionFragment.create());
                break;
            case ADJUST_VOLUME:
                handleShowActionFragment(AdjustVolumeActionFragment.create());
                break;
            case ADD_SILENCE:
                handleShowActionFragment(AddSilenceActionFragment.create());
                break;
            case NORMALIZE:
                handleShowActionFragment(NormalizeActionFragment.create());
                break;
            case SPLIT:
                handleShowActionFragment(SplitActionFragment.create());
                break;
            case COMBINE:
                handleShowActionFragment(CombineActionFragment.create());
                break;
            case SET_AS_RINGTONE:
                handleShowActionFragment(SetAsRingtoneActionFragment.create());
                break;
        }
        closeBackgroundLayout();
        updateFABVisibility();
        highlightActionOnEditActionsList(action);
    }

    private void handleShowActionFragment(@NonNull Fragment fragment) {
        // Replace, only if this action isn't current.
        final Fragment current = getSupportFragmentManager().findFragmentById(R.id.action_fragment);
        if (current == null || !(current.getClass().equals(fragment.getClass()))) {
            final int buttonText = getStringResForEditAction((HandleMainButtonTapListener) fragment);
            final int icon = getDrawableResForEditAction((HandleMainButtonTapListener) fragment);
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.action_fragment, fragment)
                    .commit();
            setTitle(buttonText);
            mainButton.setText(buttonText);
            mainButton.setIconResource(icon);

            if (fragment instanceof HandleSecondaryFABTapListener) {
                final int secondaryIcon = getDrawableResForSecondaryAction((HandleSecondaryFABTapListener) fragment);
                secondaryFab.setImageResource(secondaryIcon);
            }
        }
    }

    private void highlightActionOnEditActionsList(@NonNull EditAction action) {
        final EditActionsFragment editActionsFragment = (EditActionsFragment) getSupportFragmentManager().findFragmentById(R.id.background_layout);
        Objects.requireNonNull(editActionsFragment).highlightAction(action);
    }

    // ConvertActionFragment.OnConvertActionFragmentInteractionListener

    @Override
    public void onConversionSelected(@NonNull Uri targetUri, @NonNull String targetFileName, @NonNull OutputFormatType outputFormatType) {
        viewModel.onConvertActionClicked(targetUri, targetFileName, outputFormatType);
        finish();
    }

    // MakeVideoActionFragment.OnMakeVideoActionFragmentInteractionListener

    @Override
    public void onMakeVideoSelected(@NonNull Uri targetUri,
                                    @NonNull String targetFileName,
                                    @Nullable Uri customCoverImageUri,
                                    @Nullable String customCoverImageFileName) {
        viewModel.onMakeVideoActionClicked(targetUri, targetFileName, customCoverImageUri,
                customCoverImageFileName);
        finish();
    }

    // ExtractAudioActionFragment.OnExtractAudioActionFragmentInteractionListener

    @Override
    public void onExtractAudioSelected(@NonNull Uri targetUri, @NonNull String targetFileName, @NonNull OutputFormatType outputFormatType) {
        viewModel.onExtractAudioActionClicked(targetUri, targetFileName, outputFormatType);
        finish();
    }

    // TrimActionFragment.OnTrimActionFragmentInteractionListener

    @Override
    public void onTrimSelected(@NonNull Uri targetUri, @NonNull String targetFileName, long trimBeforeMs, long trimAfterMs) {
        viewModel.onTrimActionClicked(targetUri, targetFileName, trimBeforeMs, trimAfterMs);
        finish();
    }

    // CutActionFragment.OnCutActionFragmentInteractionListener

    @Override
    public void onCutSelected(@NonNull Uri targetUri, @NonNull String targetFileName, long cutStartMs, long cutEndMs,
                              long durationMs) {
        viewModel.onCutActionClicked(targetUri, targetFileName, cutStartMs, cutEndMs, durationMs);
        finish();
    }

    // AdjustSpeedActionFragment.OnAdjustSpeedActionFragmentInteractionListener,

    @Override
    public void onAdjustSpeedSelected(@NonNull Uri targetUri, @NonNull String targetFileName, float speed) {
        viewModel.onAdjustSpeedActionClicked(targetUri, targetFileName, speed);
        finish();
    }

    // AdjustVolumeActionFragment.OnAdjustVolumeActionFragmentInteractionListener

    @Override
    public void onAdjustVolumeSelected(@NonNull Uri targetUri, @NonNull String targetFileName, float db) {
        viewModel.onAdjustVolumeActionClicked(targetUri, targetFileName, db);
        finish();
    }

    // AddSilenceActionFragment.OnAddSilenceActionFragmentInteractionListener

    @Override
    public void onAddSilenceSelected(@NonNull Uri targetUri, @NonNull String targetFileName, long silenceInsertionPointMs, long silenceDurationMs) {
        viewModel.onAddSilenceActionClicked(targetUri, targetFileName, silenceInsertionPointMs, silenceDurationMs);
        finish();
    }

    // NormalizeActionFragment.OnNormalizeActionFragmentInteractionListener

    @Override
    public void onNormalizeSelected(@NonNull Uri targetUri, @NonNull String targetFileName) {
        viewModel.onNormalizeActionClicked(targetUri, targetFileName);
        finish();
    }

    // SplitActionFragment.OnSplitActionFragmentInteractionListener

    @Override
    public void onSplitActionSelected(@NonNull Uri firstTargetUri, @NonNull String firstTargetFileName,
                                      @NonNull Uri secondTargetUri, @NonNull String secondTargetFileName, long splitAtMs) {
        viewModel.onSplitActionClicked(firstTargetUri, firstTargetFileName, secondTargetUri, secondTargetFileName, splitAtMs);
        finish();
    }

    // CombineActionFragment.OnCombineActionFragmentInteractionListener

    @Override
    public void onCombineActionSelected(@NonNull MediaItem[] inputItems,
                                        @NonNull Uri targetUri,
                                        @NonNull String targetFileName) {
        viewModel.onCombineActionClicked(inputItems, targetUri, targetFileName);
        finish();
    }

    // SetAsRingtoneActionFragment.OnSetAsRingtoneActionFragmentInteractionListener

    @Override
    public void onSetAsRingtoneActionSelected(@NonNull Uri targetUri, @NonNull String targetFileName,
                                              @NonNull RingtoneType ringtoneType,
                                              boolean transcodeToAac) {
        viewModel.onSetAsRingtoneActionClicked(targetUri, targetFileName, ringtoneType, transcodeToAac);
        finish();
    }

    // Static helpers

    @NonNull
    private static EditAction getEditActionForCurrentlyShowingFragment(@NonNull HandleMainButtonTapListener handler) {
        if (handler instanceof ConvertActionFragment) {
            return EditAction.CONVERT;
        } else if (handler instanceof MakeVideoActionFragment) {
            return EditAction.CONVERT_TO_VIDEO;
        } else if (handler instanceof ExtractAudioActionFragment) {
            return EditAction.EXTRACT_AUDIO;
        } else if (handler instanceof TrimActionFragment) {
            return EditAction.TRIM;
        } else if (handler instanceof CutActionFragment) {
            return EditAction.CUT;
        } else if (handler instanceof AdjustSpeedActionFragment) {
            return EditAction.ADJUST_SPEED;
        } else if (handler instanceof AdjustVolumeActionFragment) {
            return EditAction.ADJUST_VOLUME;
        } else if (handler instanceof AddSilenceActionFragment) {
            return EditAction.ADD_SILENCE;
        } else if (handler instanceof NormalizeActionFragment) {
            return EditAction.NORMALIZE;
        } else if (handler instanceof SplitActionFragment) {
            return EditAction.SPLIT;
        } else if (handler instanceof CombineActionFragment) {
            return EditAction.COMBINE;
        } else if (handler instanceof SetAsRingtoneActionFragment) {
            return EditAction.SET_AS_RINGTONE;
        }

        throw new IllegalArgumentException();
    }

    @DrawableRes
    private static int getDrawableResForEditAction(@NonNull HandleMainButtonTapListener handler) {
        if (handler instanceof ConvertActionFragment) {
            return R.drawable.ic_convert_black_24dp;
        } else if (handler instanceof MakeVideoActionFragment) {
            return R.drawable.ic_music_video_black_24dp;
        } else if (handler instanceof ExtractAudioActionFragment) {
            return R.drawable.ic_music_note_black_24dp;
        } else if (handler instanceof TrimActionFragment) {
            return R.drawable.ic_trim_black_24dp;
        } else if (handler instanceof CutActionFragment) {
            return R.drawable.ic_cut_black_24dp;
        } else if (handler instanceof AdjustSpeedActionFragment) {
            return R.drawable.ic_adjust_speed_24dp;
        } else if (handler instanceof AdjustVolumeActionFragment) {
            return R.drawable.ic_adjust_volume_black_24dp;
        } else if (handler instanceof AddSilenceActionFragment) {
            return R.drawable.ic_add_silence_black_24dp;
        } else if (handler instanceof NormalizeActionFragment) {
            return R.drawable.ic_normalize_24dp;
        } else if (handler instanceof SplitActionFragment) {
            return R.drawable.ic_split_black_24dp;
        } else if (handler instanceof CombineActionFragment) {
            return R.drawable.ic_combine_black_24dp;
        } else if (handler instanceof SetAsRingtoneActionFragment) {
            return R.drawable.ic_set_as_ringtone_black_24dp;
        }

        throw new IllegalArgumentException();
    }

    @DrawableRes
    private static int getDrawableResForSecondaryAction(@NonNull HandleSecondaryFABTapListener handler) {
        if (handler instanceof MakeVideoActionFragment) {
            return R.drawable.ic_insert_photo_black_24dp;
        } else if (handler instanceof CombineActionFragment) {
            return R.drawable.ic_add_black_24dp;
        }

        throw new IllegalArgumentException();
    }

    @StringRes
    private static int getStringResForEditAction(@NonNull HandleMainButtonTapListener handler) {
        if (handler instanceof ConvertActionFragment) {
            return R.string.convert;
        } else if (handler instanceof MakeVideoActionFragment) {
            return R.string.convert_to_video;
        } else if (handler instanceof ExtractAudioActionFragment) {
            return R.string.extract_audio;
        } else if (handler instanceof TrimActionFragment) {
            return R.string.trim;
        } else if (handler instanceof CutActionFragment) {
            return R.string.cut;
        } else if (handler instanceof AdjustSpeedActionFragment) {
            return R.string.adjust_speed;
        } else if (handler instanceof AdjustVolumeActionFragment) {
            return R.string.adjust_volume;
        } else if (handler instanceof AddSilenceActionFragment) {
            return R.string.add_silence;
        } else if (handler instanceof NormalizeActionFragment) {
            return R.string.normalize;
        } else if (handler instanceof SplitActionFragment) {
            return R.string.split;
        } else if (handler instanceof CombineActionFragment) {
            return R.string.combine;
        } else if (handler instanceof SetAsRingtoneActionFragment) {
            return R.string.set_as_ringtone;
        }

        throw new IllegalArgumentException();
    }
}
