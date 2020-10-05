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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.digipom.easymediaconverter.utils.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.digipom.easymediaconverter.config.StaticConfig.LOGCAT_LOGGING_ON;
import static com.digipom.easymediaconverter.utils.StreamUtils.silentlyClose;

final class FileAppenderHandlerThread extends HandlerThread {
    private static final String TAG = FileAppenderHandlerThread.class.getName();
    private static final String FILENAME = "log";

    private final Context context;
    private final Handler handler;
    private final int maxLogSizeInBytes;
    private final StringBuilder builder = new StringBuilder();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);
    private final PrintStream printStream = new PrintStream(new OutputStream() {
        @Override
        public void write(int oneByte) {
            builder.append((char) oneByte);
        }
    }, true);

    private FileOutputStream fileOutputStream;
    private File currentFile;

    FileAppenderHandlerThread(@NonNull Context context, int maxLogSizeInBytes) {
        super(TAG, android.os.Process.THREAD_PRIORITY_LOWEST);

        this.context = context.getApplicationContext();
        this.maxLogSizeInBytes = maxLogSizeInBytes;

        start();
        handler = new Handler(getLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    selectLogFile();
                } catch (Exception e) {
                    if (LOGCAT_LOGGING_ON) {
                        Log.w(TAG, e);
                    }
                }
            }
        });
    }

    @WorkerThread
    @NonNull
    private File logFileOne() {
        return new File(context.getFilesDir(), FILENAME + ".1.txt");
    }

    @WorkerThread
    @NonNull
    private File logFileTwo() {
        return new File(context.getFilesDir(), FILENAME + ".2.txt");
    }

    @WorkerThread
    void getLogs(@NonNull StringBuilder builderForThisThread) {
        final File logFileOne = logFileOne();
        final File logFileTwo = logFileTwo();

        if (logFileOne.exists()) {
            builderForThisThread.append("*** BEGIN: LOG FILE ONE ***\n")
                    .append(ResourceUtils.getTextFromFile(logFileOne))
                    .append("*** END: LOG FILE ONE ***\n\n");
        }

        if (logFileTwo.exists()) {
            builderForThisThread.append("*** BEGIN: LOG FILE TWO ***\n")
                    .append(ResourceUtils.getTextFromFile(logFileTwo))
                    .append("*** END: LOG FILE TWO ***\n\n");
        }
    }

    void logMessageToFile(int level, @NonNull String tag, @NonNull String message) {
        logMessageToFile(level, tag, message, null);
    }

    @SuppressWarnings("SameParameterValue")
    void logExceptionToFile(int level, @NonNull String tag, @NonNull Throwable tr) {
        logMessageToFile(level, tag, null, tr);
    }

    void logMessageToFile(final int level, final @NonNull String tag, final @Nullable String message, final @Nullable Throwable tr) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    selectLogFile();

                    if (fileOutputStream != null) {
                        builder.setLength(0);

                        builder.append(dateFormat.format(new Date(System.currentTimeMillis()))).append("  :  ");

                        switch (level) {
                            case Log.ASSERT:
                                builder.append("ASSERT: ");
                                break;
                            case Log.DEBUG:
                                builder.append("DEBUG: ");
                                break;
                            case Log.ERROR:
                                builder.append("ERROR: ");
                                break;
                            case Log.INFO:
                                builder.append("INFO: ");
                                break;
                            case Log.VERBOSE:
                                builder.append("VERBOSE: ");
                                break;
                            case Log.WARN:
                                builder.append("WARN: ");
                                break;
                            default:
                                builder.append("UNKNOWN: ");
                                break;
                        }

                        builder.append(tag);

                        if (message != null) {
                            builder.append(" : ").append(message);
                        }

                        builder.append('\n');

                        printException(tr);

                        try {
                            fileOutputStream.write(builder.toString().getBytes());
                            fileOutputStream.flush();
                        } catch (IOException e) {
                            if (LOGCAT_LOGGING_ON) {
                                Log.w(TAG, "Could not write to logging stream.", e);
                            }

                            fileOutputStream = null;
                        }
                    }
                } catch (Exception e) {
                    if (LOGCAT_LOGGING_ON) {
                        Log.w(TAG, e);
                    }
                }
            }
        });
    }

    private void printException(@Nullable Throwable tr) {
        if (tr != null) {
            tr.printStackTrace(printStream);
        }
    }

    @WorkerThread
    private void selectLogFile() {
        final File logDir = context.getFilesDir();
        if (logDir.mkdirs()) {
            Logger.d("Creating log dir");
        }

        final File logFileOne = logFileOne();
        final File logFileTwo = logFileTwo();

        final boolean oneExistsAndIsOverCapacity = logFileOne.exists() && logFileOne.length() > maxLogSizeInBytes;
        final boolean twoExistsAndIsOverCapacity = logFileTwo.exists() && logFileTwo.length() > maxLogSizeInBytes;

        if (currentFile == null) {
            if (oneExistsAndIsOverCapacity && !twoExistsAndIsOverCapacity) {
                switchToLogFile(logFileTwo);
            } else {
                switchToLogFile(logFileOne);
            }
        } else {
            if (currentFile.equals(logFileTwo) && twoExistsAndIsOverCapacity) {
                switchToLogFile(logFileOne);
            } else if (currentFile.equals(logFileOne) && oneExistsAndIsOverCapacity) {
                switchToLogFile(logFileTwo);
            }
        }

        // Capacity checks -- truncate the log file we're not using
        if (currentFile != null && currentFile.equals(logFileOne) && twoExistsAndIsOverCapacity) {
            keepEndOfFileToCapacity(logFileTwo);
        } else if (currentFile != null && currentFile.equals(logFileTwo) && oneExistsAndIsOverCapacity) {
            keepEndOfFileToCapacity(logFileOne);
        }
    }

    @WorkerThread
    private void keepEndOfFileToCapacity(@NonNull File file) {
        try {
            final RandomAccessFile r = new RandomAccessFile(file, "rw");
            final FileChannel fc = r.getChannel();
            try {
                Logger.d("Truncating too-large log file " + file + " with size " + file.length());
                final ByteBuffer buffer = ByteBuffer.allocate(maxLogSizeInBytes);
                fc.position(fc.size() - maxLogSizeInBytes);
                fc.read(buffer);
                fc.position(0);
                fc.write(buffer);
                fc.truncate(maxLogSizeInBytes);
            } finally {
                silentlyClose(r);
            }
        } catch (Exception e) {
            Logger.w("Could not truncate log file " + file, e);
        }
    }

    @WorkerThread
    private void switchToLogFile(@NonNull File file) {
        if (LOGCAT_LOGGING_ON) {
            Log.v(TAG, "switchLogFile(" + file + ")");
        }

        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                if (LOGCAT_LOGGING_ON) {
                    Log.d(TAG, "Could not close file output stream.");
                }
            }

            fileOutputStream = null;
        }

        currentFile = file;

        if (file.exists() && file.length() >= maxLogSizeInBytes) {
            if (!file.delete()) {
                if (LOGCAT_LOGGING_ON) {
                    Log.d(TAG, "Could not delete log file " + file);
                }
            }
        }

        try {
            fileOutputStream = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            if (LOGCAT_LOGGING_ON) {
                Log.w(TAG, "Cannot perform file logging -- unable to open file for output.");
            }
        }
    }
}
