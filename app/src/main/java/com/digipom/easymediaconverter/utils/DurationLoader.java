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

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.concurrent.Executor;

public class DurationLoader {
    public static final long DURATION_NOT_LOADED = -1;
    private static final long DURATION_COULD_NOT_BE_LOADED = -2;
    private final Context context;
    private final Executor backgroundExecutor = ExecutorUtils.newSingleThreadExecutorWithTimeout();

    public DurationLoader(@NonNull Context context) {
        this.context = context;
    }

    @NonNull
    public LiveData<Long> loadDuration(@NonNull final Uri uri) {
        final MutableLiveData<Long> duration = new MutableLiveData<>();
        duration.setValue(DURATION_NOT_LOADED);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                duration.postValue(getDurationMs(uri));
            }
        });
        return duration;
    }

    @WorkerThread
    private long getDurationMs(@NonNull Uri uri) {
        try {
            final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, uri);
                final String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationString != null) {
                    return Long.parseLong(durationString);
                } else {
                    Logger.w("Couldn't read duration for " + uri);
                }
            } finally {
                retriever.release();
            }
        } catch (Exception e) {
            Logger.w(e);
        }

        return DURATION_COULD_NOT_BE_LOADED;
    }
}
