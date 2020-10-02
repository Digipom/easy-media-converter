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

import android.content.Context;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.FFMpegAction;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.logger.Logger;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.digipom.easymediaconverter.ffmpeg.FFMpegTaskWrapper.FFMpegFailedException;

public class FFMpegRequests {
    public static class CancellableRequest {
        private final AtomicBoolean isCancelled = new AtomicBoolean(false);

        public final int id;
        public final EditAction editAction;
        public final MediaItem[] sources;
        private final FFMpegAction ffMpegAction;

        CancellableRequest(int id,
                           @NonNull EditAction editAction,
                           @NonNull MediaItem source,
                           @NonNull FFMpegAction ffMpegAction) {
            this(id, editAction, new MediaItem[]{source}, ffMpegAction);
        }

        CancellableRequest(int id,
                           @NonNull EditAction editAction,
                           @NonNull MediaItem[] sources,
                           @NonNull FFMpegAction ffMpegAction) {
            this.id = id;
            this.editAction = editAction;
            this.sources = sources;
            this.ffMpegAction = ffMpegAction;
        }

        @NonNull
        public LiveData<Float> progress() {
            return ffMpegAction.progress();
        }

        public long estimatedTimeRemainingMs() {
            return ffMpegAction.estimatedTimeRemainingMs();
        }

        @WorkerThread
        void execute() throws InterruptedException, JSONException, IOException {
            try {
                ffMpegAction.execute();
                if (isCancelled.get()) {
                    throw new RequestCancelledException("isCancelled is set to true");
                }
            } catch (RequestCancelledException e) {
                // Make sure we clean up the targets.
                Logger.d("Request " + this + " is cancelled, so will clean up targets.");
                ffMpegAction.deleteTargets();
                throw e;
            } catch (Exception e) {
                Logger.w("Request " + this + " failed, so will clean up targets.", e);
                ffMpegAction.deleteTargets();
                throw e;
            }
        }

        @MainThread
        void requestCancellation() {
            Logger.d("User requested to cancel request " + this);
            isCancelled.set(true);
            ffMpegAction.requestCancellation();
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        @MainThread
        public boolean isCancelled() {
            return isCancelled.get();
        }

        @NonNull
        @Override
        public String toString() {
            return "CancellableRequest{" +
                    "isCancelled=" + isCancelled +
                    ", id=" + id +
                    ", editAction=" + editAction +
                    ", sources=" + Arrays.toString(sources) +
                    ", ffMpegAction=" + ffMpegAction +
                    '}';
        }
    }

    public static class CompletedRequest {
        public final int id;
        public final long timestamp;
        public final EditAction editAction;
        public final MediaItem[] sources;
        public final MediaItem[] targets;

        @NonNull
        static CompletedRequest createFromRequest(@NonNull Context context, @NonNull CancellableRequest request) throws IOException {
            return new CompletedRequest(request.id, System.currentTimeMillis(), request.editAction, request.sources,
                    targetUrisToMediaItems(context, request.ffMpegAction.getTargets()));
        }

        private CompletedRequest(int id, long timestamp, EditAction editAction, MediaItem[] sources, MediaItem[] targets) {
            this.id = id;
            this.timestamp = timestamp;
            this.editAction = editAction;
            this.sources = sources;
            this.targets = targets;
        }

        @NonNull
        @Override
        public String toString() {
            return "CompletedRequest{" +
                    "id=" + id +
                    ", editAction=" + editAction +
                    ", sources=" + Arrays.toString(sources) +
                    ", targets=" + Arrays.toString(targets) +
                    '}';
        }
    }

    public static class FailedRequest {
        public final int id;
        public final long timestamp;
        public final EditAction editAction;
        public final MediaItem[] sources;
        private final Exception failure;

        @NonNull
        static FailedRequest createFromRequest(@NonNull CancellableRequest request,
                                               @NonNull Exception failure) {
            return new FailedRequest(request.id, System.currentTimeMillis(), request.editAction, request.sources, failure);
        }

        private FailedRequest(int id, long timestamp, EditAction editAction, MediaItem[] sources, Exception failure) {
            this.id = id;
            this.timestamp = timestamp;
            this.editAction = editAction;
            this.sources = sources;
            this.failure = failure;
        }

        @NonNull
        public String getShortMessage() {
            if (failure instanceof FFMpegFailedException) {
                return ((FFMpegFailedException) failure).getShortMessage();
            } else {
                return failure.getClass().getSimpleName() + ": " + failure.getLocalizedMessage();
            }
        }

        @NonNull
        public String getFullMessage() {
            if (failure instanceof FFMpegFailedException) {
                return ((FFMpegFailedException) failure).getFullMessage();
            } else {
                return getRawExceptionMessagesInSeries(failure);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "FailedRequest{" +
                    "id=" + id +
                    ", editAction=" + editAction +
                    ", sources=" + Arrays.toString(sources) +
                    ", failure=" + failure +
                    '}';
        }

        @NonNull
        private String getRawExceptionMessagesInSeries(@NonNull Throwable t) {
            final StringBuilder builder = new StringBuilder();
            appendRawExceptionMessagesInSeries(builder, t);
            return builder.toString();
        }

        private void appendRawExceptionMessagesInSeries(@NonNull StringBuilder builder, @NonNull Throwable t) {
            if (t.getLocalizedMessage() != null) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append(t.getClass().getName())
                        .append(": ")
                        .append(t.getLocalizedMessage());
            }

            if (t.getCause() != null) {
                appendRawExceptionMessagesInSeries(builder, t.getCause());
            }
        }
    }

    @NonNull
    private static MediaItem[] targetUrisToMediaItems(@NonNull Context context, @NonNull Uri[] targets) throws IOException {
        final List<MediaItem> items = new ArrayList<>();
        for (Uri target : targets) {
            try {
                items.add(MediaItem.constructFromUri(context, target));
            } catch (Exception e) {
                Logger.w("Couldn't construct media item from uri " + target, e);
                throw e;
            }
        }
        return items.toArray(new MediaItem[0]);
    }
}
