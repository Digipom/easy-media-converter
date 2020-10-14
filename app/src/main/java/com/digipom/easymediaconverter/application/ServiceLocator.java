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
package com.digipom.easymediaconverter.application;

import android.content.Context;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.notifications.NotificationsController;
import com.digipom.easymediaconverter.prefs.AppPreferences;

public class ServiceLocator {
    private final AppPreferences appPreferences;
    private final NotificationsController notificationsController;
    private final FFMpegController ffMpegController;

    ServiceLocator(@NonNull Context context) {
        appPreferences = new AppPreferences(context);
        notificationsController = new NotificationsController(context);
        ffMpegController = new FFMpegController(context, appPreferences, notificationsController);
    }

    @NonNull
    public AppPreferences getAppPreferences() {
        return appPreferences;
    }

    @NonNull
    public NotificationsController getNotificationsController() {
        return notificationsController;
    }

    @NonNull
    public FFMpegController getFFMpegController() {
        return ffMpegController;
    }
}
