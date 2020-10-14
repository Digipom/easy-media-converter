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
package com.digipom.easymediaconverter.errors;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.utils.ContactUsUtils;

import java.util.Objects;

import static com.digipom.easymediaconverter.utils.AlertDialogUtils.createWithCondensedFontUsingOnShowListener;
import static com.digipom.easymediaconverter.utils.ContactUsUtils.FeedbackType.ERROR;

public class ErrorDialogFragment extends DialogFragment {
    private static final String TAG = ErrorDialogFragment.class.getName();

    public static void showCouldNotCreateDocumentError(@NonNull Context context, @NonNull FragmentManager fragmentManager) {
        showDialog(fragmentManager, context.getString(R.string.error_could_not_create_new_document));
    }

    public static void showCouldNotOpenDocumentError(@NonNull Context context, @NonNull FragmentManager fragmentManager) {
        showDialog(fragmentManager, context.getString(R.string.error_could_not_open_document));
    }

    public static void showDialog(@NonNull FragmentManager fragmentManager,
                                  @NonNull String title, @NonNull String message) {
        final ErrorDialogFragment fragment = new ErrorDialogFragment();
        final Bundle args = new Bundle();
        args.putString(Intent.EXTRA_TITLE, title);
        args.putString(Intent.EXTRA_TEXT, message);
        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    public static void showDialog(@NonNull FragmentManager fragmentManager,
                                  @NonNull String message) {
        final ErrorDialogFragment fragment = new ErrorDialogFragment();
        final Bundle args = new Bundle();
        args.putString(Intent.EXTRA_TEXT, message);
        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = Objects.requireNonNull(getArguments());
        final String title = args.getString(Intent.EXTRA_TITLE);
        final String header = getString(R.string.errorHeader, getString(R.string.send_feedback));
        final String message = header + "\n\n" + args.getString(Intent.EXTRA_TEXT);

        final AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(
                        Objects.requireNonNull(getActivity()), R.style.AppTheme_MaterialAlertDialog));
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.send_feedback, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    ContactUsUtils.openSendLogsEmail(requireActivity(), ERROR);
                }
            }
        });
        builder.setNegativeButton(R.string.close, null);
        return createWithCondensedFontUsingOnShowListener(builder);
    }
}
