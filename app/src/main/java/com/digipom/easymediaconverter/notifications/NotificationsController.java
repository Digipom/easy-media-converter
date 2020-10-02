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
package com.digipom.easymediaconverter.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests;
import com.digipom.easymediaconverter.main.MainActivity;
import com.digipom.easymediaconverter.receiver.NotificationsReceiver;
import com.digipom.easymediaconverter.utils.IntentUtils;

import java.util.Locale;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

@MainThread
public class NotificationsController {
    private static final String MAIN_NOTIFICATION_CHANNEL_ID = "main_notification_channel";
    private static final String IMPORTANT_MESSAGES_NOTIFICATION_CHANNEL_ID = "important_messages_notification_channel";
    private static final String ERROR_CHANNEL_NOTIFICATION_ID = "errors_notification_channel";

    public static final int SERVICE_NOTIFICATION_ID = 1;
    public static final int CONVERSION_COMPLETE_NOTIFICATION_ID = 2;
    private static final int ERROR_NOTIFICATION_ID = 3;

    private final Context context;

    public NotificationsController(@NonNull Context context) {
        this.context = context;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);

            notificationManagerCompat.createNotificationChannel(
                    new NotificationChannel(
                            MAIN_NOTIFICATION_CHANNEL_ID,
                            context.getString(R.string.main_notification_channel),
                            NotificationManager.IMPORTANCE_LOW));

            notificationManagerCompat.createNotificationChannel(
                    new NotificationChannel(
                            IMPORTANT_MESSAGES_NOTIFICATION_CHANNEL_ID,
                            context.getString(R.string.important_messages_channel),
                            NotificationManager.IMPORTANCE_HIGH));

            notificationManagerCompat.createNotificationChannel(
                    new NotificationChannel(
                            ERROR_CHANNEL_NOTIFICATION_ID,
                            context.getString(R.string.errors_notification_channel),
                            NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @NonNull
    public Notification getMediaServiceStartingNotification() {
        return getDefaultBuilder(MAIN_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(getLaunchMainActivityPendingIntent())
                .setContentText(context.getString(R.string.about_to_start))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @NonNull
    public Notification getMediaServiceIsProcessingFileNotification(@NonNull EditAction editAction,
                                                                    @NonNull String firstSourceDisplayName,
                                                                    int sourceCount,
                                                                    boolean hasDeterminateProgress,
                                                                    float progress,
                                                                    long estimatedTimeRemainingMs) {
        final String contentTitle = context.getString(R.string.editing_template,
                context.getString(editAction.descriptionForOngoingTaskType()),
                context.getResources().getQuantityString(R.plurals.items, sourceCount,
                        firstSourceDisplayName, sourceCount));
        final int maxProgress = 10000;
        final int progressInt = (int) (progress * maxProgress);

        final NotificationCompat.Builder builder = getDefaultBuilder(MAIN_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(getLaunchMainActivityPendingIntent())
                .setContentTitle(contentTitle);

        if (hasDeterminateProgress) {
            final long now = System.currentTimeMillis();
            final long end = now + estimatedTimeRemainingMs;

            if (end - now > 0) {
                builder.setContentText(DateUtils.getRelativeTimeSpanString(end, now, 0)
                        .toString().toLowerCase(Locale.getDefault()));
            } else {
                // We can't do a reasonable time estimation, so just show indeterminate progress.
                hasDeterminateProgress = false;
            }
        }

        return builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_close_white_24dp, context.getString(R.string.stop),
                        getCancelAllRequestPendingIntent())
                .setProgress(maxProgress, progressInt, !hasDeterminateProgress)
                .build();
    }

    public void notifyActionComplete(@NonNull FFMpegRequests.CompletedRequest completedRequest) {
        final NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(CONVERSION_COMPLETE_NOTIFICATION_ID,
                getDefaultBuilder(IMPORTANT_MESSAGES_NOTIFICATION_CHANNEL_ID)
                        .setContentIntent(getLaunchMainActivityPendingIntent())
                        // Set as title rather than text to make it more "important".
                        .setContentTitle(context.getString(R.string.editing_template,
                                context.getString(completedRequest.editAction.descriptionForCompletedTaskType()),
                                context.getResources().getQuantityString(R.plurals.items, completedRequest.sources.length,
                                        completedRequest.sources[0].getDisplayName(), completedRequest.sources.length)))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .addAction(R.drawable.ic_menu_share, context.getString(R.string.share),
                                getShareItemPendingIntent(completedRequest))
                        .build());
    }

    public void notifyOfError(@NonNull FFMpegRequests.FailedRequest failedRequest) {
        final String title = context.getString(R.string.error_doing_action,
                context.getString(R.string.editing_template,
                        context.getString(failedRequest.editAction.descriptionForOngoingTaskType()).toLowerCase(Locale.getDefault()),
                        context.getResources().getQuantityString(R.plurals.items, failedRequest.sources.length,
                                failedRequest.sources[0].getDisplayName(), failedRequest.sources.length)));
        final String shortText = failedRequest.getShortMessage();
        final String longText = failedRequest.getFullMessage();

        final NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(ERROR_NOTIFICATION_ID,
                getBuilderForSwipeableError(title, shortText, longText)
                        .setContentIntent(getLaunchMainActivityToErrorPendingIntent(failedRequest.id, title, longText))
                        .build());
    }

    @NonNull
    private NotificationCompat.Builder getBuilderForSwipeableError(@NonNull String title,
                                                                   @NonNull String shortText,
                                                                   @NonNull String longText) {
        return getDefaultBuilder(ERROR_CHANNEL_NOTIFICATION_ID)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentTitle(title)
                .setContentText(shortText)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(longText))
                .setPriority(NotificationCompat.PRIORITY_MAX);
    }

    private NotificationCompat.Builder getDefaultBuilder(@NonNull String channel) {
        return new NotificationCompat.Builder(context, channel)
                .setColor(context.getColor(R.color.colorPrimary));
    }

    @NonNull
    private PendingIntent getLaunchMainActivityPendingIntent() {
        final Intent intent = MainActivity.getLaunchToMainActivityIntent(context);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent, 0);
    }

    @NonNull
    private PendingIntent getLaunchMainActivityToErrorPendingIntent(int requestId,
                                                                    @NonNull String title,
                                                                    @NonNull String message) {
        final Intent intent = MainActivity.getLaunchToErrorIntent(context, title, message);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @NonNull
    private PendingIntent getShareItemPendingIntent(@NonNull FFMpegRequests.CompletedRequest request) {
        final Intent sendIntent = IntentUtils.getShareItemIntent(request.targets);
        sendIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, request.id, sendIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @NonNull
    private PendingIntent getCancelAllRequestPendingIntent() {
        final Intent intent = NotificationsReceiver.getIntentForCancellingAllRequests(context);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }
}
