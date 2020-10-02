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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.errors.ErrorDialogFragment;
import com.digipom.easymediaconverter.main.recents.RecentsListFragment;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.player.PlayerActivity;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public class MainActivity extends AppCompatActivity implements
        RecentsListFragment.RecentsListFragmentInteractionListener {
    private static final String ACTION_LAUNCH_ACTIVITY = "ACTION_LAUNCH_ACTIVITY";
    private static final String ACTION_LAUNCH_TO_ERROR_DIALOG = "ACTION_LAUNCH_TO_ERROR_DIALOG";
    private static final int IMPORT_CONTENT_REQUEST_CODE = 1;

    private MainViewModel viewModel;

    @NonNull
    public static Intent getLaunchToMainActivityIntent(@NonNull Context context) {
        final Intent intent = new Intent(context, MainActivity.class);
        // Still need an action, otherwise PendingIntent.getActivity for other intents will match
        // against this intent and won't work properly.
        intent.setAction(ACTION_LAUNCH_ACTIVITY);
        return intent;
    }

    @NonNull
    public static Intent getLaunchToErrorIntent(@NonNull Context context,
                                                @NonNull String title, @NonNull String message) {
        final Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_LAUNCH_TO_ERROR_DIALOG);
        intent.putExtra(Intent.EXTRA_TITLE, title);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setContentView(R.layout.activity_main);

        final BottomAppBar bottomAppBar = findViewById(R.id.bottom_app_bar);
        bottomAppBar.replaceMenu(R.menu.main_menu);
        bottomAppBar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return false;
            }
        });
        bottomAppBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final BottomNavDrawerFragment fragment = new BottomNavDrawerFragment();
                fragment.show(getSupportFragmentManager(), BottomNavDrawerFragment.TAG);
            }
        });

        final FloatingActionButton fab = findViewById(R.id.main_button);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = IntentUtils.getSingleMediaContentRequest();
                startActivityForResult(Intent.createChooser(intent, null),
                        IMPORT_CONTENT_REQUEST_CODE);
            }
        });

        if (savedInstanceState == null) {
            if (ACTION_LAUNCH_TO_ERROR_DIALOG.equals(getIntent().getAction())) {
                final String title = Objects.requireNonNull(getIntent().getStringExtra(Intent.EXTRA_TITLE));
                final String message = Objects.requireNonNull(getIntent().getStringExtra(Intent.EXTRA_TEXT));
                ErrorDialogFragment.showDialog(getSupportFragmentManager(), title, message);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            if (ACTION_LAUNCH_TO_ERROR_DIALOG.equals(intent.getAction())) {
                final String title = Objects.requireNonNull(intent.getStringExtra(Intent.EXTRA_TITLE));
                final String message = Objects.requireNonNull(intent.getStringExtra(Intent.EXTRA_TEXT));
                ErrorDialogFragment.showDialog(getSupportFragmentManager(), title, message);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_CONTENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    Logger.v("User opened document: " + data.getData());
                    final MediaItem item = MediaItem.constructFromGetContentResponse(this, data);
                    // NOTE: Buggy devices that require external storage permission -- might need to handle that.
                    showPlayerActivityForItem(item);
                } catch (Exception e) {
                    Logger.w(e);
                    ErrorDialogFragment.showCouldNotOpenDocumentError(this, getSupportFragmentManager());
                }
            } else if (resultCode == RESULT_CANCELED) {
                Logger.d("User cancelled opening document.");
            } else {
                // Some other error
                Logger.w("Couldn't open document. Result code: " + resultCode);
                ErrorDialogFragment.showCouldNotOpenDocumentError(this, getSupportFragmentManager());
            }
        }
    }

    // RecentsListFragment.RecentsListFragmentInteractionListener

    @Override
    public void onRecentlyOpenedItemTapped(@NonNull MediaItem item) {
        showPlayerActivityForItem(item);
    }

    @Override
    public void onCompletedItemTapped(@NonNull MediaItem[] targets) {
        showPlayerActivityForItem(targets[0]);
    }

    private void showPlayerActivityForItem(@NonNull MediaItem item) {
        viewModel.onUserOpenedItem(item);
        PlayerActivity.show(this, item);
    }
}
