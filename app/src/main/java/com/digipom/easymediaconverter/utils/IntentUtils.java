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
package com.digipom.easymediaconverter.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.errors.ErrorDialogFragment;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;

public class IntentUtils {
    @NonNull
    public static Intent getSingleMediaContentRequest() {
        return getGetMediaContentRequest(false);
    }

    @NonNull
    public static Intent getSingleImageContentRequest() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        // Undocumented -- should allow auto-enabling the internal storage picker.
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        return intent;
    }

    @NonNull
    public static Intent getMultipleMediaContentRequest() {
        return getGetMediaContentRequest(true);
    }

    @NonNull
    private static Intent getGetMediaContentRequest(boolean allowMultiple) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Sometimes MP4 files are audio-only, or the user only wants to work with the audio,
        // so still allow them to be selected.
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
        // Undocumented -- should allow auto-enabling the internal storage picker.
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        if (allowMultiple) {
            intent.putExtra(EXTRA_ALLOW_MULTIPLE, true);
        }

        return intent;
    }

    @NonNull
    public static Intent getCreateDocumentRequest(@NonNull String mimeType,
                                                  @NonNull String selectedName) {
        // We need a destination URI.
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setTypeAndNormalize(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, selectedName);
        // Undocumented -- should allow auto-enabling the internal storage picker.
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        return intent;
    }

    @NonNull
    public static Intent getCreateDocumentRequestForRingtones(@NonNull String mimeType,
                                                              @NonNull String selectedName,
                                                              @NonNull RingtoneType ringtoneType) {
        // We need a destination URI.
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setTypeAndNormalize(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, selectedName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switch (ringtoneType) {
                case PHONE:
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ARingtones"));
                    break;
                case NOTIFICATION:
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ANotifications"));
                    break;
                case ALARM:
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                            Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAlarms"));
                    break;
            }

        }
        // Undocumented -- should allow auto-enabling the internal storage picker.
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        return intent;
    }

    public static abstract class NewDocumentHandler {
        public abstract void onReceivedUriForNewDocument(@NonNull Uri target);

        public void onFailedToReceiveUriForNewDocument() {
            // To override if needed.
        }
    }

    public static void handleNewlyCreatedDocumentFromActivityResult(@NonNull FragmentActivity activity,
                                                                    int resultCode,
                                                                    @Nullable Intent data,
                                                                    @NonNull NewDocumentHandler handler) {
        try {
            final Uri target = getNewlyCreatedDocumentFromActivityResult(resultCode, data);
            // Because only the calling activity will have access to the created target, we need to
            // grant ourselves permission to access the target throughout the app. We can't just use
            // the same tricks we used for source URIs, because, for example, if we try to pass both
            // source URIs and target URIs to MediaExportService, then we have to add both
            // FLAG_GRANT_READ_URI_PERMISSION and FLAG_GRANT_WRITE_URI_PERMISSION, which blows up
            // since some of the URIs in the clip data are source URIS, and we can only grant read
            // permission to those. To keep it simple, we simply grant app-wide access below.
            // Note: We are not currently revoking this grant anywhere in the app (this may not be
            // necessary since we aren't asking the system for a persistable URI permission to the
            // target).
            activity.grantUriPermission(activity.getPackageName(), target,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            handler.onReceivedUriForNewDocument(target);
        } catch (CouldNotCreateDocumentException e) {
            Logger.w(e);
            handler.onFailedToReceiveUriForNewDocument();
            if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                ErrorDialogFragment.showCouldNotCreateDocumentError(activity, activity.getSupportFragmentManager());
            }
        } catch (UserCancelledDocumentCreationException e) {
            Logger.d("User cancelled new document creation");
            // That's fine.
        }
    }

    @NonNull
    private static Uri getNewlyCreatedDocumentFromActivityResult(int resultCode, @Nullable Intent data)
            throws CouldNotCreateDocumentException, UserCancelledDocumentCreationException {
        if (resultCode == RESULT_OK) {
            final Uri target = data != null ? data.getData() : null;
            if (target != null) {
                Logger.v("Obtained uri from document creation: " + target);
                return target;
            } else {
                // Not sure when we would run into this with a status of RESULT_OK.
                throw new CouldNotCreateDocumentException("Received RESULT_OK but intent data was null:" + data);
            }
        } else if (resultCode == RESULT_CANCELED) {
            Logger.d("User cancelled document creation.");
            throw new UserCancelledDocumentCreationException();
        } else {
            throw new CouldNotCreateDocumentException("Could not create document. Result code: " + resultCode + ", data: " + data);
        }
    }

    public static class CouldNotCreateDocumentException extends IOException {
        CouldNotCreateDocumentException(@NonNull String message) {
            super(message);
        }
    }

    public static class UserCancelledDocumentCreationException extends IOException {
        UserCancelledDocumentCreationException() {
        }
    }

    @NonNull
    public static Intent getShareItemIntent(@NonNull MediaItem[] targets) {
        final Intent sendIntent = new Intent();
        if (targets.length == 1) {
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, targets[0].getUri());
            sendIntent.setType(targets[0].getMimeType());
        } else {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            final ArrayList<Uri> uris = new ArrayList<>(targets.length);
            final Set<String> mimeTypes = new HashSet<>();
            for (MediaItem item : targets) {
                uris.add(item.getUri());
                mimeTypes.add(item.getMimeType());
            }
            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

            if (mimeTypes.size() == 1) {
                sendIntent.setType(mimeTypes.iterator().next());
            } else {
                boolean containsAudio = false;
                boolean containsVideo = false;

                for (String mimeType : mimeTypes) {
                    if (mimeType.startsWith("audio/")) {
                        containsAudio = true;
                    } else if (mimeType.startsWith("video/")) {
                        containsVideo = true;
                    }
                }

                final boolean moreThanOneAggregateType = containsAudio && containsVideo;

                if (moreThanOneAggregateType) {
                    sendIntent.setType("*/*");
                } else if (containsVideo) {
                    sendIntent.setType("video/*");
                } else {
                    sendIntent.setType("audio/*");
                }
            }
        }
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return sendIntent;
    }
}
