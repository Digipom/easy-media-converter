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

import android.util.Log;

import androidx.annotation.NonNull;

public class LogCatAppender implements Appender {
    @Override
    public void d(@NonNull String tag, @NonNull String msg) {
        Log.d(tag, msg);
    }

    @Override
    public void d(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        Log.d(tag, msg, tr);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String msg) {
        Log.e(tag, msg);
    }

    @Override
    public void e(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        Log.e(tag, msg, tr);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String msg) {
        Log.i(tag, msg);
    }

    @Override
    public void i(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        Log.i(tag, msg, tr);
    }

    @Override
    public void v(@NonNull String tag, @NonNull String msg) {
        Log.v(tag, msg);
    }

    @Override
    public void v(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        Log.v(tag, msg, tr);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String msg) {
        Log.w(tag, msg);
    }

    @Override
    public void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        Log.w(tag, msg, tr);
    }

    @Override
    public void w(@NonNull String tag, @NonNull Throwable tr) {
        Log.w(tag, tr);
    }
}
