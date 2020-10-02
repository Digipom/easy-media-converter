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
package com.digipom.easymediaconverter.services;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.CancellableRequest;
import com.digipom.easymediaconverter.notifications.NotificationsController;
import com.digipom.easymediaconverter.utils.ObjectUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.digipom.easymediaconverter.notifications.NotificationsController.SERVICE_NOTIFICATION_ID;
import static com.digipom.easymediaconverter.utils.BatteryUtils.logBatteryRestrictions;

public class MediaExportService extends LifecycleService {
    private static final String ACTION_START_MEDIA_EXPORT = "com.digipom.easymediaconverter.services.action.ACTION_START_MEDIA_EXPORT";
    private static final String ACTION_UPDATE_ONGOING_REQUEST = "com.digipom.easymediaconverter.services.action.ACTION_UPDATE_ONGOING_REQUEST";

    public static void startForMediaExport(@NonNull Context caller,
                                           @NonNull ArrayList<Uri> sourceUris) {
        final Intent intent = new Intent(caller, MediaExportService.class);
        intent.setAction(ACTION_START_MEDIA_EXPORT);

        // We need to do an extra step to retain access to any URIs that we had opened with
        // ACTION_GET_CONTENT.
        // See:
        // https://commonsware.com/blog/2016/08/10/uri-access-lifetime-shorter-than-you-might-think.html
        // https://stackoverflow.com/questions/48187647/exception-java-lang-securityexception-reading-mediadocumentsprovider-requ
        // https://stackoverflow.com/questions/38839879/permission-denial-mediadocumentsprovider/38839910?noredirect=1#comment65045803_38839910
        if (!sourceUris.isEmpty()) {
            final Set<String> mimeTypes = new HashSet<>();
            for (Uri uri : sourceUris) {
                mimeTypes.add(caller.getContentResolver().getType(uri));
            }
            final ClipData clipData = new ClipData("uris", mimeTypes.toArray(new String[0]),
                    new ClipData.Item(sourceUris.remove(0)));
            for (Uri uri : sourceUris) {
                clipData.addItem(new ClipData.Item(uri));
            }
            // Forward the read permission on the URIs.
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(clipData);
        }

        ContextCompat.startForegroundService(caller, intent);
    }

    public static void updateForCurrentRequest(@NonNull Context caller) {
        final Intent intent = new Intent(caller, MediaExportService.class);
        intent.setAction(ACTION_UPDATE_ONGOING_REQUEST);
        ContextCompat.startForegroundService(caller, intent);
    }

    public static void stopService(@NonNull Context caller) {
        final Intent intent = new Intent(caller, MediaExportService.class);
        caller.stopService(intent);
    }

    private NotificationsController notificationsController;
    private FFMpegController ffMpegController;
    private PowerManager.WakeLock wakeLock;
    private boolean isStartedForeground;

    private LiveData<Float> existingProgressObservable;

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // TODO Not currently handling or warning on interruptions due to the battery saver.

    @Override
    public void onCreate() {
        super.onCreate();
        notificationsController = ((BaseApplication) getApplication()).getServiceLocator().getNotificationsController();
        ffMpegController = ((BaseApplication) getApplication()).getServiceLocator().getFFMpegController();
        logBatteryRestrictions(this, true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_START_MEDIA_EXPORT)) {
                // Don't process ACTION_START_MEDIA_EXPORT more than once, to avoid stepping over
                // notifications.
                if (!isStartedForeground) {
                    Logger.v("Starting foreground service");
                    grabWakelockIfNeeded();
                    startForeground(SERVICE_NOTIFICATION_ID,
                            notificationsController.getMediaServiceStartingNotification());
                    isStartedForeground = true;
                }
            } else if (intent.getAction().equals(ACTION_UPDATE_ONGOING_REQUEST)) {
                // Remove any existing observers
                if (existingProgressObservable != null) {
                    existingProgressObservable.removeObservers(this);
                    existingProgressObservable = null;
                }

                final CancellableRequest currentRequest = ffMpegController.getCurrentRequest();
                if (currentRequest != null) {
                    final EditAction editAction = currentRequest.editAction;
                    final String firstSourceDisplayName = currentRequest.sources[0].getDisplayName();
                    final int sourceCount = currentRequest.sources.length;

                    final LiveData<Float> progressLiveData = currentRequest.progress();
                    final Float possibleProgress = progressLiveData.getValue();
                    final boolean hasDeterminateProgress = possibleProgress != null;
                    final float progress = ObjectUtils.returnDefaultIfNull(possibleProgress, -1f);
                    final long estimatedTimeRemaining = currentRequest.estimatedTimeRemainingMs();

                    grabWakelockIfNeeded();
                    startForeground(SERVICE_NOTIFICATION_ID,
                            notificationsController.getMediaServiceIsProcessingFileNotification(
                                    editAction, firstSourceDisplayName, sourceCount,
                                    hasDeterminateProgress, progress, estimatedTimeRemaining));
                    isStartedForeground = true;

                    // Also observe for updates to the progress
                    progressLiveData.observe(this, new Observer<Float>() {
                        @Override
                        public void onChanged(Float possibleProgress) {
                            final boolean hasDeterminateProgress = possibleProgress != null;
                            final float progress = ObjectUtils.returnDefaultIfNull(possibleProgress, -1f);
                            final long estimatedTimeRemaining = currentRequest.estimatedTimeRemainingMs();

                            grabWakelockIfNeeded();
                            startForeground(SERVICE_NOTIFICATION_ID,
                                    notificationsController.getMediaServiceIsProcessingFileNotification(
                                            editAction, firstSourceDisplayName, sourceCount,
                                            hasDeterminateProgress, progress, estimatedTimeRemaining));
                            isStartedForeground = true;
                        }
                    });
                    existingProgressObservable = progressLiveData;
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.v("Destroying foreground service");
        isStartedForeground = false;
        if (existingProgressObservable != null) {
            existingProgressObservable.removeObservers(this);
            existingProgressObservable = null;
        }

        releaseWakelockIfHeld();
        super.onDestroy();
    }

    @SuppressLint("WakelockTimeout")
    private void grabWakelockIfNeeded() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            Logger.v("Acquiring wakelock");
            final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = Objects.requireNonNull(powerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "EasyMediaConverter::MediaConversionWakelock");
            wakeLock.acquire();
        }
    }

    private void releaseWakelockIfHeld() {
        if (wakeLock != null && wakeLock.isHeld()) {
            Logger.v("Releasing wakelock");
            wakeLock.release();
            wakeLock = null;
        }
    }
}
