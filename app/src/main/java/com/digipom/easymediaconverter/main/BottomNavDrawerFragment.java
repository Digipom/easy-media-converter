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
package com.digipom.easymediaconverter.main;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.utils.ContactUsUtils;
import com.digipom.easymediaconverter.utils.IntentLauncher;
import com.digipom.easymediaconverter.utils.ResourceUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import static com.digipom.easymediaconverter.utils.AlertDialogUtils.createWithCondensedFontUsingOnShowListener;

public class BottomNavDrawerFragment extends BottomSheetDialogFragment {
    static final String TAG = BottomSheetDialogFragment.class.getName();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_nav_bottomsheet, container, false);
        final NavigationView navView = view.findViewById(R.id.navigation_view);
        navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                final Activity activity = getActivity();
                if (activity == null) {
                    return false;
                }

                if (item.getItemId() == R.id.nav_send_feedback) {
                    ContactUsUtils.openSendLogsEmail(activity);
                    return true;
                } else if (item.getItemId() == R.id.nav_rate_the_app) {
                    IntentLauncher.launchUrlInBrowserOrShowError(activity, getString(R.string.marketPage), getString(R.string.noBrowserApp));
                    return true;
                } else if (item.getItemId() == R.id.nav_suggest_app_to_a_friend) {
                    final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareAppText,
                            getString(R.string.app_name), getString(R.string.marketPageForShareShort)));
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.shareAppSubject));
                    try {
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.suggest_the_app_to_a_friend)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, R.string.noShareApp, Toast.LENGTH_LONG).show();
                    }
                    return true;
                } else if (item.getItemId() == R.id.nav_more_apps) {
                    IntentLauncher.launchUrlInBrowserOrShowError(activity, getString(R.string.moreAppsMarketPage), getString(R.string.noBrowserApp));
                    return true;
                } else if (item.getItemId() == R.id.nav_about) {
                    AboutDialogFragment.showDialog(getChildFragmentManager());
                }

                return false;
            }
        });
        return view;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // Material Design guidelines for bottom drawer layout
                ((BottomSheetDialog) dialog).getBehavior().setPeekHeight(getResources().getDisplayMetrics().heightPixels / 2);
            }
        });
        return dialog;
    }

    public static class AboutDialogFragment extends DialogFragment {
        private static final String TAG = AboutDialogFragment.class.getName();

        public static void showDialog(@NonNull FragmentManager fragmentManager) {
            final AboutDialogFragment fragment = new AboutDialogFragment();
            fragment.show(fragmentManager, TAG);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(
                            Objects.requireNonNull(getActivity()), R.style.AppTheme_MaterialAlertDialog));
            final SpannableStringBuilder text = new SpannableStringBuilder(
                    new SpannableStringBuilder(HtmlCompat.fromHtml(
                            ResourceUtils.getTextFromResource(requireContext(), R.raw.about),
                            HtmlCompat.FROM_HTML_MODE_COMPACT)));

            builder.setMessage(text);
            return createWithCondensedFontUsingOnShowListener(builder, new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    ((TextView) Objects.requireNonNull(getDialog()).findViewById(android.R.id.message))
                            .setMovementMethod(LinkMovementMethod.getInstance());
                }
            });
        }

    }
}
