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
package com.digipom.easymediaconverter.player.ringtone;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Objects;

import static com.digipom.easymediaconverter.utils.AlertDialogUtils.createWithCondensedFontUsingOnShowListener;

public class SetAsRingtoneActionFragment extends Fragment implements
        MainButtonInterfaces.HandleMainButtonTapListener {
    public interface OnSetAsRingtoneActionFragmentInteractionListener {
        void onSetAsRingtoneActionSelected(@NonNull Uri targetUri,
                                           @NonNull String targetFileName,
                                           @NonNull RingtoneType ringtoneType,
                                           boolean transcodeToAac);
    }

    private static final int CREATE_DOCUMENT_IN_RINGTONES_DIR_REQUEST_CODE = 2;

    @NonNull
    public static SetAsRingtoneActionFragment create() {
        return new SetAsRingtoneActionFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_set_as_ringtone_action, container, false);
    }

    private SetAsRingtoneActionViewModel viewModel;
    private PlayerViewModel sharedViewModel;
    private ChipGroup chipGroup;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SetAsRingtoneActionViewModel.class);
        sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        chipGroup = view.findViewById(R.id.chipgroup);
        final Chip ringtoneTypePhone = view.findViewById(R.id.phone_chip);
        final Chip ringtoneTypeNotification = view.findViewById(R.id.notification_chip);
        final Chip ringtoneTypeAlarm = view.findViewById(R.id.alarm_chip);
        chipGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                if (getActivity() != null) {
                    ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
                }
            }
        });
        ringtoneTypePhone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onPhoneRingtoneTypeChecked();
                }
            }
        });
        ringtoneTypeNotification.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onNotificationRingtoneTypeChecked();
                }
            }
        });
        ringtoneTypeAlarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    viewModel.onAlarmRingtoneTypeChecked();
                }
            }
        });

        requestPermissionsNeeded(getActivity());
    }

    @Override
    public boolean shouldShowMainButton() {
        return chipGroup.getCheckedChipId() != View.NO_ID;
    }

    @Override
    public void onMainButtonTapped() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (!hasPermissionForUpdatingSystemRingtone(activity)) {
            requestPermissionsNeeded(activity);
        } else {
            final String mimeType = viewModel.getMimeTypeForOutputSelection();
            final String outputFilename = viewModel.getDefaultOutputFilename();
            final RingtoneType ringtoneType = viewModel.getRingtoneType();

            // We need a destination URI.
            final Intent createDocumentIntent = IntentUtils.getCreateDocumentRequestForRingtones(mimeType, outputFilename, ringtoneType);
            startActivityForResult(createDocumentIntent, CREATE_DOCUMENT_IN_RINGTONES_DIR_REQUEST_CODE);
        }
    }

    // Permissions

    private void requestPermissionsNeeded(@NonNull Activity activity) {
        final FragmentManager fragmentManager = getChildFragmentManager();
        // Need main permissions: Updating the system entry.

        // Check for the system entry.
        if (!hasPermissionForUpdatingSystemRingtone(activity)) {
            NeedsSettingPermissionForRingtones.showDialog(fragmentManager, sharedViewModel.getMediaItem().getDisplayName());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCUMENT_IN_RINGTONES_DIR_REQUEST_CODE) {
            final FragmentActivity activity = getActivity();
            IntentUtils.handleNewlyCreatedDocumentFromActivityResult(
                    Objects.requireNonNull(activity), resultCode, data, new IntentUtils.NewDocumentHandler() {
                        @Override
                        public void onReceivedUriForNewDocument(@NonNull Uri target) {
                            final String outputFilename = viewModel.getDefaultOutputFilename();
                            final RingtoneType type = viewModel.getRingtoneType();
                            final boolean transcodeToAac = viewModel.needsConversionToAac();
                            ((OnSetAsRingtoneActionFragmentInteractionListener) activity).onSetAsRingtoneActionSelected(target, outputFilename, type, transcodeToAac);
                        }
                    });
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean hasPermissionForUpdatingSystemRingtone(@NonNull Context context) {
        return Settings.System.canWrite(context);
    }

    public static class NeedsSettingPermissionForRingtones extends DialogFragment {
        private static final String TAG = NeedsSettingPermissionForRingtones.class.getName();
        private static final String BUNDLE_RECORDING_NAME = "BUNDLE_RECORDING_NAME";

        static void showDialog(@NonNull FragmentManager fragmentManager, @NonNull String recordingName) {
            final NeedsSettingPermissionForRingtones fragment = new NeedsSettingPermissionForRingtones();
            final Bundle args = new Bundle();
            args.putString(BUNDLE_RECORDING_NAME, recordingName);
            fragment.setArguments(args);
            fragment.show(fragmentManager, TAG);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String recordingName = Objects.requireNonNull(getArguments()).getString(BUNDLE_RECORDING_NAME);
            final AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(
                            Objects.requireNonNull(getActivity()), R.style.AppTheme_MaterialAlertDialog));
            builder.setMessage(getString(R.string.permissionRationaleForRingtone, recordingName));
            builder.setPositiveButton(R.string.openSystemSettings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getActivity() != null) {
                        Activity activity = getActivity();
                        try {
                            final Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            intent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Logger.w("No permissions settings screen found.", e);
                            Toast.makeText(activity, activity.getString(R.string.noPermissionsSettingsScreenFound), Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
            return createWithCondensedFontUsingOnShowListener(builder);
        }
    }
}
