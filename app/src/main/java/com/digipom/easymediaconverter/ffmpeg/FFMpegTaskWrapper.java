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
package com.digipom.easymediaconverter.ffmpeg;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFmpegExecution;
import com.arthenica.mobileffmpeg.LogCallback;
import com.arthenica.mobileffmpeg.LogMessage;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;
import com.digipom.easymediaconverter.utils.ObjectUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

class FFMpegTaskWrapper {
    private static final TaskTracker TASK_TRACKER = new TaskTracker();

    static {
        Config.enableLogCallback(new LogCallback() {
            @Override
            public void apply(final LogMessage logMessage) {
                TASK_TRACKER.doForTask(logMessage.getExecutionId(), new TaskTracker.TaskCommand() {
                    @Override
                    public void run(@NonNull FFMpegTaskWrapper task) {
                        task.handleLogFragment(logMessage.getText());
                    }
                });
            }
        });
        Config.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(final Statistics statistics) {
                TASK_TRACKER.doForTask(statistics.getExecutionId(), new TaskTracker.TaskCommand() {
                    @Override
                    public void run(@NonNull FFMpegTaskWrapper task) {
                        task.handleProgress(statistics);
                    }
                });
            }
        });
    }

    private final AtomicReference<Long> executionId = new AtomicReference<>(null);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final AtomicLong durationMs = new AtomicLong(-1L);
    private final AtomicLong estimatedTimeRemainingMs = new AtomicLong(-1L);
    private final MutableLiveData<Long> progressMs = new MutableLiveData<>();
    private final LogHelper logHelper = new LogHelper(new LogHelper.LineHandler() {
        @Override
        public void onLogLine(@NonNull String line) {
            handleLogLine(line);
        }
    });

    long durationMs() {
        return durationMs.get();
    }

    long estimatedTimeRemainingMs() {
        return estimatedTimeRemainingMs.get();
    }

    @NonNull
    LiveData<Long> progressMs() {
        return progressMs;
    }

    @WorkerThread
    @NonNull
    String runTask(@NonNull List<String> commands, boolean throwOnFailure) throws InterruptedException {
        final String[] cmd = commands.toArray(new String[0]);
        final Semaphore blocker = new Semaphore(0, true);
        Logger.v("Starting FFMPEG with command line: " + Arrays.toString(cmd));
        final AtomicReference<String> atomicString = new AtomicReference<>("");
        final AtomicBoolean didFail = new AtomicBoolean(false);
        // If we were cancelled before we started the task, make sure we don't start
        // executing it.
        checkCancellationState();

        final long executionId = FFmpeg.executeAsync(cmd, new ExecuteCallback() {
            @Override
            public void apply(final long executionId, final int returnCode) {
                if (returnCode == RETURN_CODE_SUCCESS) {
                    Logger.v("Result code: successful");
                    atomicString.set(Config.getLastCommandOutput());
                } else if (returnCode == RETURN_CODE_CANCEL) {
                    Logger.v("Result code: cancelled");
                    isCancelled.set(true);
                } else {
                    Logger.v("Result code: failed; code: " + returnCode);
                    atomicString.set(Config.getLastCommandOutput());
                    didFail.set(true);
                }

                Logger.v("FFMPEG execution completed for execution id " + executionId);
                blocker.release();
            }
        });
        Logger.d("Started FFMPEG task with execution id " + executionId);
        this.executionId.set(executionId);

        TASK_TRACKER.addTask(executionId, this);
        // Wait for the task to complete.
        blocker.acquire();
        TASK_TRACKER.removeTask(executionId);
        // If we were stopped, then signal that.
        checkCancellationState();
        if (didFail.get() && throwOnFailure) {
            throw new FFMpegFailedException(atomicString.get());
        }
        return atomicString.get();
    }

    @AnyThread
    private void handleLogFragment(@NonNull String text) {
        logHelper.processLogFragment(text);
    }

    @AnyThread
    private void handleLogLine(@NonNull String line) {
        try {
            if (durationMs.get() < 0) {
                // TODO this probably won't work for combine and might not work for split? let's
                //  test and see
                final String durationStr = "Duration: ";
                final int durationStrLength = durationStr.length();
                final int durationIdx = line.indexOf(durationStr);
                final int durationTimestampStartIdx = durationIdx + durationStrLength;
                final int durationTimestampEndIdx = durationTimestampStartIdx + 11;

                if (durationIdx > 0 && line.length() >= durationTimestampEndIdx) {
                    final String durationTimeStamp = line.substring(durationTimestampStartIdx,
                            durationTimestampEndIdx);
                    durationMs.set(convertFFMpegTimeToMs(durationTimeStamp));
                    progressMs.postValue(0L);
                }
            }
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    @AnyThread
    private void handleProgress(@NonNull Statistics statistics) {
        try {
            long duration = durationMs.get();
            if (duration > 0) {
                final int currentTime = statistics.getTime();
                progressMs.postValue((long) currentTime);

                final double currentSpeed = statistics.getSpeed();
                if (currentSpeed > 0) {
                    final long msRemaining = Math.max(0, duration - currentTime);
                    final long adjustedMsRemaining = (long) ((double) msRemaining / currentSpeed);
                    estimatedTimeRemainingMs.set(adjustedMsRemaining);
                }
            }
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    private void checkCancellationState() throws RequestCancelledException {
        if (isCancelled.get()) {
            throw new RequestCancelledException("isCancelled is set to true");
        }
    }

    // Note: This can't be run on the same executor as the one that's running the task, as that
    // one would be blocked while the FFMPEG task is running. So, this can be run from the main
    // thread instead.
    @MainThread
    void requestCancellation() {
        Logger.d("Requesting to cancel FFMpeg task...");
        isCancelled.set(true);
        final Long executionId = this.executionId.get();
        if (executionId != null && hasFFMPEGExecutionMatchingId(executionId)) {
            Logger.d("Cancelling FFMPEG task with id " + executionId + "...");
            FFmpeg.cancel(executionId);
            this.executionId.set(null);
        } else {
            Logger.v("No ongoing FFMPEG task found for id " + executionId);
        }
    }

    boolean hasFFMPEGExecutionMatchingId(long executionId) {
        for (FFmpegExecution execution : FFmpeg.listExecutions()) {
            if (execution.getExecutionId() == executionId) {
                return true;
            }
        }
        return false;
    }

    static class FFMpegFailedException extends RuntimeException {
        FFMpegFailedException(@NonNull String message) {
            super(message);
        }

        @NonNull
        String getShortMessage() {
            final StringBuilder builder = new StringBuilder();
            final String[] lines = ObjectUtils.returnDefaultIfNull(getMessage(), "").split("\n");

            // Return the last 2-3 lines if they contain the words "error" or "fail"
            int count = 0;
            for (int i = lines.length - 1; i >= 0 && count < 3; --i) {
                String toLowerCase = lines[i].toLowerCase(Locale.US);
                if (toLowerCase.contains("error") || toLowerCase.contains("fail")) {
                    builder.insert(0, lines[i] + "\n");
                    count++;
                }
            }

            return builder.toString();
        }

        @NonNull
        String getFullMessage() {
            return ObjectUtils.returnDefaultIfNull(getLocalizedMessage(), "");
        }
    }

    private static long convertFFMpegTimeToMs(@NonNull String time) {
        if (time.startsWith("N/A")) {
            return -1;
        }

        // We want to format this as hh:mm:ss[.xxx]
        final String hourPart = time.substring(0, 2);
        final String minutesPart = time.substring(3, 5);
        final String secondsPart = time.substring(6, 8);
        final String hundredthsPart = time.substring(9, 11);

        final int hours = Integer.parseInt(hourPart);
        final int minutes = Integer.parseInt(minutesPart);
        final int seconds = Integer.parseInt(secondsPart);
        int hundredths = 0;
        try {
            hundredths = Integer.parseInt(hundredthsPart);
        } catch (Exception e) {
            Logger.w(e);
        }

        long timestampInMs = hundredths * 10L;
        timestampInMs += seconds * 1000L;
        timestampInMs += minutes * 1000L * 60L;
        timestampInMs += hours * 1000L * 60L * 60L;
        return timestampInMs;
    }

    private static class LogHelper {
        interface LineHandler {
            void onLogLine(@NonNull String line);
        }

        private final LineHandler handler;
        private final StringBuilder builder = new StringBuilder();

        LogHelper(@NonNull LineHandler handler) {
            this.handler = handler;
        }

        void processLogFragment(@NonNull String text) {
            builder.append(text);
            String line;
            while ((line = getNextLine()) != null) {
                Logger.d(line);
                handler.onLogLine(line);
            }
        }

        @Nullable
        private String getNextLine() {
            int index;
            int separatorSize = 2;
            index = builder.indexOf("\r\n");
            if (index < 0) {
                index = builder.indexOf("\n");
                separatorSize = 1;
            }
            if (index < 0) {
                index = builder.indexOf("\r");
            }
            if (index < 0) {
                return null;
            }
            final String line = builder.substring(0, index);
            builder.delete(0, index + separatorSize);
            return line;
        }
    }

    private static class TaskTracker {
        private interface TaskCommand {
            void run(@NonNull FFMpegTaskWrapper task);
        }

        private final HashMap<Long, WeakReference<FFMpegTaskWrapper>> ONGOING_TASKS = new HashMap<>();

        private void addTask(long id, @NonNull FFMpegTaskWrapper task) {
            synchronized (ONGOING_TASKS) {
                ONGOING_TASKS.put(id, new WeakReference<>(task));
            }
        }

        private void removeTask(long id) {
            synchronized (ONGOING_TASKS) {
                ONGOING_TASKS.remove(id);
            }
        }

        private void doForTask(long id, @NonNull TaskCommand cmd) {
            FFMpegTaskWrapper task = null;
            synchronized (ONGOING_TASKS) {
                final WeakReference<FFMpegTaskWrapper> taskRefForId = ONGOING_TASKS.get(id);
                if (taskRefForId != null) {
                    final FFMpegTaskWrapper taskForId = taskRefForId.get();
                    if (taskForId != null) {
                        task = taskForId;
                    }
                }
            }

            if (task != null) {
                cmd.run(task);
            }
        }
    }
}
