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
package com.digipom.easymediaconverter.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.services.MediaExportService;
import com.digipom.easymediaconverter.utils.logger.Logger;

public class NotificationsReceiver extends BroadcastReceiver {
    private static final String ACTION_CANCEL_ALL_REQUESTS = "ACTION_CANCEL_ALL_REQUESTS";

    public static Intent getIntentForCancellingAllRequests(@NonNull Context context) {
        final Intent intent = new Intent(context, NotificationsReceiver.class);
        intent.setAction(ACTION_CANCEL_ALL_REQUESTS);
        return intent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_CANCEL_ALL_REQUESTS)) {
            Logger.d("Got user request to cancel all requests");
            final FFMpegController controller = ((BaseApplication) context.getApplicationContext()).getServiceLocator().getFFMpegController();
            controller.cancelAllRequests();
            // Ensure service is stopped so the notification is removed immediately.
            MediaExportService.stopService(context);
        }
    }
}
