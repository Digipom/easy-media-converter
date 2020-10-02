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
package com.digipom.easymediaconverter.utils.logger;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import static com.digipom.easymediaconverter.config.StaticConfig.LOGCAT_LOGGING_ON;

public final class FileAppender implements Appender {
    private static final String TAG = FileAppender.class.getName();
    private final FileAppenderHandlerThread handlerThread;

    public FileAppender(@NonNull Context context,
                        int maxLogSizeInBytes) {
        handlerThread = new FileAppenderHandlerThread(context, maxLogSizeInBytes);
    }

    @WorkerThread
    @NonNull
    public String getLogs() {
        final StringBuilder builder = new StringBuilder();

        try {
            handlerThread.getLogs(builder);
        } catch (Exception e) {
            if (LOGCAT_LOGGING_ON) {
                Log.w(TAG, "Could not get log file data.");
            }

            builder.append("Could not get log file data: ").append(e).append("\n");
        }

        return builder.toString();
    }

    @Override
    public void d(@NonNull String tag, @NonNull String msg) {
        handlerThread.logMessageToFile(Log.DEBUG, tag, msg);
    }

    @Override
    public void d(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        handlerThread.logMessageToFile(Log.DEBUG, tag, msg, tr);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String msg) {
        handlerThread.logMessageToFile(Log.ERROR, tag, msg);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        handlerThread.logMessageToFile(Log.ERROR, tag, msg, tr);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String msg) {
        handlerThread.logMessageToFile(Log.INFO, tag, msg);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        handlerThread.logMessageToFile(Log.INFO, tag, msg, tr);
    }

    @Override
    public void v(@NonNull String tag, @NonNull String msg) {
        handlerThread.logMessageToFile(Log.VERBOSE, tag, msg);
    }

    @Override
    public void v(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        handlerThread.logMessageToFile(Log.VERBOSE, tag, msg, tr);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String msg) {
        handlerThread.logMessageToFile(Log.WARN, tag, msg);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        handlerThread.logMessageToFile(Log.WARN, tag, msg, tr);
    }

    @Override
    public void w(@NonNull String tag, @NonNull Throwable tr) {
        handlerThread.logExceptionToFile(Log.WARN, tag, tr);
    }
}
