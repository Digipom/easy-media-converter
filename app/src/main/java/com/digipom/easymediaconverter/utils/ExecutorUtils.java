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
package com.digipom.easymediaconverter.utils;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorUtils {
    @NonNull
    public static ExecutorService newSingleThreadExecutorWithTimeout() {
        return newSingleThreadExecutorWithTimeoutAndCapacity(Integer.MAX_VALUE);
    }

    @NonNull
    public static ExecutorService newSingleThreadExecutorWithTimeoutAndCapacity(int capacity) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1,
                15L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(capacity),
                new ThreadFactory() {
                    final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

                    @Override
                    public Thread newThread(@NonNull final Runnable r) {
                        return defaultThreadFactory.newThread(new Runnable() {
                            @Override
                            public void run() {
                                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                                r.run();
                            }
                        });
                    }
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
