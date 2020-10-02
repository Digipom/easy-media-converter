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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

public abstract class Logger {
    private static final String TAG = Logger.class.getName();
    private static final String CLASS_NAME = Logger.class.getName();

    private static final List<Appender> APPENDERS = new ArrayList<>();
    private static final UncaughtExceptionHandler SYSTEM_DEFAULT_UNCAUGHT_EXCEPTION_HANDLER = Thread.getDefaultUncaughtExceptionHandler();

    @Nullable
    private static final UncaughtExceptionHandler sDefaultUncaughtExceptionHandler = SYSTEM_DEFAULT_UNCAUGHT_EXCEPTION_HANDLER;

    public static void installUncaughtExceptionHandler() {
        try {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
                    w(TAG, "Last chance: handling uncaught exception " + ex + " for thread " + thread, ex);

                    if (sDefaultUncaughtExceptionHandler != null) {
                        sDefaultUncaughtExceptionHandler.uncaughtException(thread, ex);
                    }
                }
            });
        } catch (Exception e) {
            w(TAG, "Could not set uncaught exception handler!");
        }
    }

    public static void addAppender(@NonNull Appender appender) {
        APPENDERS.add(appender);
    }

    public static void d(@NonNull String msg) {
        d(getCallingClass(), msg);
    }

    public static void d(@NonNull String msg, @NonNull Throwable tr) {
        d(getCallingClass(), msg, tr);
    }

    public static void e(@NonNull String msg) {
        e(getCallingClass(), msg);
    }

    public static void e(@NonNull String msg, @NonNull Throwable tr) {
        e(getCallingClass(), msg, tr);
    }

    public static void i(@NonNull String msg) {
        i(getCallingClass(), msg);
    }

    public static void i(@NonNull String msg, @NonNull Throwable tr) {
        i(getCallingClass(), msg, tr);
    }

    public static void v(@NonNull String msg) {
        v(getCallingClass(), msg);
    }

    public static void v(@NonNull String msg, @NonNull Throwable tr) {
        v(getCallingClass(), msg, tr);
    }

    public static void w(@NonNull String msg) {
        w(getCallingClass(), msg);
    }

    public static void w(@NonNull String msg, @NonNull Throwable tr) {
        w(getCallingClass(), msg, tr);
    }

    public static void w(@NonNull Throwable tr) {
        wt(getCallingClass(), tr);
    }

    @NonNull
    private static String getCallingClass() {
        final Throwable throwable = new Throwable();
        final StackTraceElement[] stackTrace = throwable.getStackTrace();

        int i = 0;
        while (i < stackTrace.length) {
            final String className = stackTrace[i].getClassName();
            if (className != null && !className.equals(CLASS_NAME))
                return className;
            ++i;
        }

        return "Unknown";
    }

    public static void d(@NonNull String tag, @NonNull String msg) {
        for (Appender appender : APPENDERS) {
            appender.d(tag, msg);
        }
    }

    public static void d(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.d(tag, msg, tr);
        }
    }

    public static void e(@NonNull String tag, @NonNull String msg) {
        for (Appender appender : APPENDERS) {
            appender.e(tag, msg);
        }
    }

    public static void e(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.e(tag, msg, tr);
        }
    }

    public static void i(@NonNull String tag, @NonNull String msg) {
        for (Appender appender : APPENDERS) {
            appender.i(tag, msg);
        }
    }

    public static void i(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.i(tag, msg, tr);
        }
    }

    public static void v(@NonNull String tag, @NonNull String msg) {
        for (Appender appender : APPENDERS) {
            appender.v(tag, msg);
        }
    }

    public static void v(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.v(tag, msg, tr);
        }
    }

    public static void w(@NonNull String tag, @NonNull String msg) {
        for (Appender appender : APPENDERS) {
            appender.w(tag, msg);
        }
    }

    public static void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.w(tag, msg, tr);
        }
    }

    private static void wt(@NonNull String tag, @NonNull Throwable tr) {
        for (Appender appender : APPENDERS) {
            appender.w(tag, tr);
        }
    }
}
