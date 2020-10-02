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

import android.app.Application;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.FileAppender;
import com.digipom.easymediaconverter.utils.logger.LogCatAppender;
import com.digipom.easymediaconverter.utils.logger.Logger;

import static com.digipom.easymediaconverter.config.StaticConfig.ENABLE_LOGGER_LOGCAT_LOGGING;
import static com.digipom.easymediaconverter.utils.BatteryUtils.logBatteryRestrictions;

public class BaseApplication extends Application {
    private FileAppender fileAppender;
    private ServiceLocator serviceLocator;

    @Override
    public void onCreate() {
        super.onCreate();

        // The very first thing we need to do is get the logger up and running
        fileAppender = new FileAppender(getApplicationContext(), 524288);
        initializeLogger();

        Logger.d("*** Application onCreate() ***");

        serviceLocator = new ServiceLocator(this);

        // General logging
        logBatteryRestrictions(this, false);
    }

    private void initializeLogger() {
        if (ENABLE_LOGGER_LOGCAT_LOGGING) {
            Logger.addAppender(new LogCatAppender());
        }
        Logger.addAppender(fileAppender);

        // The logger will now be able to log uncaught exceptions.
        Logger.installUncaughtExceptionHandler();
    }

    @NonNull
    public String getLogs() {
        return fileAppender.getLogs();
    }

    @NonNull
    public final ServiceLocator getServiceLocator() {
        return serviceLocator;
    }
}
