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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.AddSilenceAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.AdjustSpeedAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.AdjustVolumeAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.CombineAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.ConversionAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.CutAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.ExtractAudioAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.MakeVideoAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.NormalizeAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.SetAsRingtoneAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.SplitAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegActions.TrimAction;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.CancellableRequest;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.CompletedRequest;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.FailedRequest;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.notifications.NotificationsController;
import com.digipom.easymediaconverter.prefs.AppPreferences;
import com.digipom.easymediaconverter.services.MediaExportService;
import com.digipom.easymediaconverter.utils.ExecutorUtils;
import com.digipom.easymediaconverter.utils.FileUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class FFMpegController {
    private final Context context;
    private final Executor backgroundExecutor = ExecutorUtils.newSingleThreadExecutorWithTimeout();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final RequestsTracker requestsTracker = new RequestsTracker();
    private final AppPreferences appPreferences;
    private final NotificationsController notificationsController;

    // An observeable that can be used to observe the requests state from the outside world.
    private final MutableLiveData<RequestsState> requestsState = new MutableLiveData<>();

    public FFMpegController(@NonNull Context context,
                            @NonNull AppPreferences appPreferences,
                            @NonNull NotificationsController notificationsController) {
        this.context = context;
        this.appPreferences = appPreferences;
        this.notificationsController = notificationsController;
    }

    @NonNull
    public LiveData<RequestsState> requestsState() {
        return requestsState;
    }

    @MainThread
    public void submitConversionRequest(@NonNull MediaItem input,
                                        @NonNull Uri targetUri,
                                        @NonNull String targetFileName,
                                        @NonNull OutputFormatType outputFormatType) {
        Logger.v("Adding conversion request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + " and type " + outputFormatType);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.CONVERT, input,
                        new ConversionAction(context, input.getUri(), input.getFilename(), targetUri,
                                targetFileName, outputFormatType)));
        processPendingRequests();
    }

    @MainThread
    public void submitMakeVideoRequest(@NonNull MediaItem input,
                                       @NonNull Uri targetUri,
                                       @NonNull String targetFileName,
                                       @Nullable Uri customCoverImageUri,
                                       @Nullable String customCoverImageFileName) {
        Logger.v("Adding make video request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + (customCoverImageUri != null ? ", custom image uri: " + customCoverImageUri : "")
                + (customCoverImageFileName != null ? ", custom image name: " + customCoverImageFileName : ""));
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.CONVERT_TO_VIDEO, input,
                        new MakeVideoAction(context, input.getUri(), input.getFilename(),
                                targetUri, targetFileName, customCoverImageUri,
                                customCoverImageFileName)));
        processPendingRequests();
    }

    public void submitExtractAudioRequest(@NonNull MediaItem input,
                                          @NonNull Uri targetUri,
                                          @NonNull String targetFileName,
                                          @NonNull OutputFormatType outputFormatType) {
        Logger.v("Adding extract audio request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + " and type " + outputFormatType);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.EXTRACT_AUDIO, input,
                        new ExtractAudioAction(context, input.getUri(), input.getFilename(), targetUri,
                                targetFileName, outputFormatType)));
        processPendingRequests();
    }

    @MainThread
    public void submitTrimRequest(@NonNull MediaItem input,
                                  @NonNull Uri targetUri,
                                  @NonNull String targetFileName,
                                  long trimBeforeMs,
                                  long trimAfterMs) {
        Logger.v("Adding trim request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + "; trimming before " + trimBeforeMs + " and after " + trimAfterMs);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.TRIM, input,
                        new TrimAction(context, input.getUri(), input.getFilename(), targetUri, targetFileName,
                                trimBeforeMs, trimAfterMs)));
        processPendingRequests();
    }

    @MainThread
    public void submitCutRequest(@NonNull MediaItem input,
                                 @NonNull Uri targetUri,
                                 @NonNull String targetFileName,
                                 long cutStartMs,
                                 long cutEndMs,
                                 long durationMs) {
        Logger.v("Adding cut request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + "; cutting starting at " + cutStartMs + " and ending at " + cutEndMs);
        if (cutStartMs == 0) {
            Logger.v("Handling cut request as a trim request.");
            addRequest(
                    new CancellableRequest(getNextRequestId(), EditAction.CUT, input,
                            new TrimAction(context, input.getUri(), input.getFilename(), targetUri, targetFileName,
                                    0, cutEndMs)));
        } else if (cutEndMs == durationMs) {
            Logger.v("Handling cut request as a trim request.");
            addRequest(
                    new CancellableRequest(getNextRequestId(), EditAction.CUT, input,
                            new TrimAction(context, input.getUri(), input.getFilename(), targetUri, targetFileName,
                                    0, cutStartMs)));
        } else {
            addRequest(
                    new CancellableRequest(getNextRequestId(), EditAction.CUT, input,
                            new CutAction(context, input.getUri(), input.getFilename(), targetUri, targetFileName,
                                    cutStartMs, cutEndMs)));
        }
        processPendingRequests();
    }

    @MainThread
    public void submitSpeedAdjustmentRequest(@NonNull MediaItem input,
                                             @NonNull Uri targetUri,
                                             @NonNull String targetFileName,
                                             float relativeSpeed) {
        Logger.v("Adding speed adjustment request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + " with speed adjustment: " + relativeSpeed + "x");
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.ADJUST_SPEED, input,
                        new AdjustSpeedAction(context, input.getUri(), input.getFilename(),
                                targetUri, targetFileName, relativeSpeed)));
        processPendingRequests();
    }

    @MainThread
    public void submitVolumeAdjustmentRequest(@NonNull MediaItem input,
                                              @NonNull Uri targetUri,
                                              @NonNull String targetFileName,
                                              float db) {
        Logger.v("Adding volume adjustment request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + " with volume adjustment: " + db + " dB");
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.ADJUST_VOLUME, input,
                        new AdjustVolumeAction(context, input.getUri(), input.getFilename(), targetUri, targetFileName, db)));
        processPendingRequests();
    }

    @MainThread
    public void submitAddSilenceRequest(@NonNull MediaItem input,
                                        @NonNull Uri targetUri,
                                        @NonNull String targetFileName,
                                        long silenceInsertionPointMs,
                                        long silenceDurationMs) {
        Logger.v("Adding add silence request for input " + input
                + ", output " + targetUri + " with name " + targetFileName
                + " with silence insertion point:" + silenceInsertionPointMs + "ms,"
                + " silence duration " + silenceDurationMs + "ms");
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.ADD_SILENCE, input,
                        new AddSilenceAction(context, input.getUri(), input.getFilename(),
                                targetUri, targetFileName, silenceInsertionPointMs, silenceDurationMs)));
        processPendingRequests();
    }

    @MainThread
    public void submitNormalizeRequest(@NonNull MediaItem input,
                                       @NonNull Uri targetUri,
                                       @NonNull String targetFileName) {
        Logger.v("Adding normalize request for input " + input
                + ", output " + targetUri + " with name " + targetFileName);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.NORMALIZE, input,
                        new NormalizeAction(context, input.getUri(), input.getFilename(),
                                targetUri, targetFileName)));
        processPendingRequests();
    }

    @MainThread
    public void submitSplitRequest(@NonNull MediaItem input,
                                   @NonNull Uri firstTargetUri,
                                   @NonNull String firstTargetFileName,
                                   @NonNull Uri secondTargetUri,
                                   @NonNull String secondTargetFileName,
                                   long splitAtMs) {
        Logger.v("Adding split request for input " + input
                + ", outputs " + firstTargetUri + " with name " + firstTargetFileName
                + " and " + secondTargetUri + " with name " + secondTargetFileName + "; splitting at " + splitAtMs);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.SPLIT, input,
                        new SplitAction(context, input.getUri(), input.getFilename(), firstTargetUri, firstTargetFileName,
                                secondTargetUri, secondTargetFileName, splitAtMs)));
        processPendingRequests();
    }

    @MainThread
    public void submitCombineRequest(@NonNull MediaItem[] inputs,
                                     @NonNull Uri targetUri,
                                     @NonNull String targetFileName) {
        Logger.v("Adding combine request with input {" + TextUtils.join(", ", inputs)
                + "}, output " + targetUri + " with name " + targetFileName);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.COMBINE, inputs,
                        new CombineAction(context, inputs, targetUri, targetFileName)));
        processPendingRequests();
    }

    @MainThread
    public void submitSetAsRingtoneRequest(@NonNull MediaItem input,
                                           @NonNull Uri targetUri,
                                           @NonNull String targetFileName,
                                           @NonNull RingtoneType ringtoneType,
                                           boolean transcodeToAac) {
        Logger.v("Adding set as ringtone request for input " + input
                + ", output " + targetUri + " with name " + targetFileName + ", ringtone type "
                + ringtoneType + ", transcode to aac: " + transcodeToAac);
        addRequest(
                new CancellableRequest(getNextRequestId(), EditAction.SET_AS_RINGTONE, input,
                        new SetAsRingtoneAction(context, input.getUri(), input.getFilename(),
                                targetUri, targetFileName, ringtoneType, transcodeToAac)));
        processPendingRequests();
    }

    private int getNextRequestId() {
        return requestsTracker.getNextRequestId();
    }

    private void addRequest(@NonNull CancellableRequest request) {
        requestsTracker.addRequest(request);
        requestsTracker.postRequestState();
    }

    @Nullable
    public CancellableRequest getCurrentRequest() {
        return requestsTracker.getCurrentlyExecutingRequest();
    }

    @MainThread
    public void cancelRequest(@NonNull CancellableRequest request) {
        // Cancel the request.
        request.requestCancellation();
        // Also remove if still in the queue.
        if (removeRequest(request)) {
            Logger.d("Cancelled request " + request + ": removed from the queue");
        }
        requestsTracker.postRequestState();
    }

    @MainThread
    public void cancelAllRequests() {
        if (requestsTracker.cancelAllRequests()) {
            Logger.d("Cancelled all ongoing and queued requests");
            requestsTracker.postRequestState();
        }
    }

    private boolean removeRequest(@NonNull CancellableRequest request) {
        final boolean result = requestsTracker.removeRequest(request);
        if (result) {
            requestsTracker.postRequestState();
        }
        return result;
    }

    @MainThread
    public void removeCompletedRequest(@NonNull CompletedRequest request) {
        requestsTracker.removeCompletedRequest(request);
        requestsTracker.postRequestState();
    }

    @MainThread
    public void removeCompletedRequest(int requestId) {
        requestsTracker.removeCompletedRequest(requestId);
        requestsTracker.postRequestState();
    }

    @MainThread
    public void removeFailedRequest(@NonNull FailedRequest request) {
        requestsTracker.removeFailedRequest(request);
        requestsTracker.postRequestState();
    }

    @MainThread
    private void processPendingRequests() {
        // We have to do this part synchronously, to pass all the source URIs to the service so that
        // we can retain access to them here without running into a SecurityException.
        if (!requestsTracker.hasQueuedRequests()) {
            Logger.v("No more queuedRequests to process.");
            return;
        }

        MediaExportService.startForMediaExport(context, requestsTracker.getAllSourceUris());

        // Go through the queuedRequests and execute.
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (requestsTracker.hasQueuedRequests()) {
                        final CancellableRequest nextRequest = requestsTracker.getNextQueuedRequest();
                        processRequest(nextRequest);
                    }
                } catch (NoSuchElementException e) {
                    Logger.v("No more jobs to process");
                } catch (Exception e) {
                    Logger.w(e);
                }

                // Ensure final state update.
                requestsTracker.postRequestState();
                MediaExportService.stopService(context);
            }
        });
    }

    @WorkerThread
    private void processRequest(@NonNull CancellableRequest request) {
        Logger.v("Processing request " + request);

        try {
            requestsTracker.setCurrentlyExecuting(request);
            requestsTracker.postRequestState();
            MediaExportService.updateForCurrentRequest(context);

            ensureAllCachesCleared();
            request.execute();

            final CompletedRequest completedRequest = CompletedRequest.createFromRequest(context, request);
            Logger.v("Request " + request + " completed: " + completedRequest);
            requestsTracker.addCompletedRequestAndClearOngoingRequest(completedRequest);
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    appPreferences.incrementSuccessfulOpCount();
                }
            });

            // If there's no active observers, also post a notification.
            if (!requestsState.hasActiveObservers()) {
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notificationsController.notifyActionComplete(completedRequest);
                    }
                });
            }
        } catch (RequestCancelledException rce) {
            Logger.d("Request " + request + " was cancelled", rce);
            requestsTracker.clearOngoingRequest();
        } catch (Exception e) {
            Logger.w("Request " + request + " failed.", e);
            final FailedRequest failedRequest = FailedRequest.createFromRequest(request, e);
            requestsTracker.addFailedRequestAndClearOngoingRequest(failedRequest);

            // If there's no active observers, also post a notification.
            if (!requestsState.hasActiveObservers()) {
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notificationsController.notifyOfError(failedRequest);
                    }
                });
            }
        }
        // We don't update the external state here since we'll be processing another task soon.
        ensureAllCachesCleared();
    }

    // Utility functions

    private void ensureAllCachesCleared() {
        try {
            Logger.v("Ensuring internal cache is cleared...");
            FileUtils.deleteChildren(context.getCacheDir());
            final File externalCacheDir = context.getExternalCacheDir();
            if (externalCacheDir != null) {
                Logger.v("Ensuring external cache is cleared...");
                FileUtils.deleteChildren(externalCacheDir);
            }
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    private final class RequestsTracker {
        private final AtomicInteger requestId = new AtomicInteger(0);

        private final ArrayList<CancellableRequest> queuedRequests = new ArrayList<>();
        private final ArrayList<CompletedRequest> completedRequests = new ArrayList<>();
        private final ArrayList<FailedRequest> failedRequests = new ArrayList<>();
        private CancellableRequest currentlyExecutingRequest = null;

        int getNextRequestId() {
            return requestId.getAndIncrement();
        }

        @Nullable
        synchronized CancellableRequest getCurrentlyExecutingRequest() {
            return currentlyExecutingRequest;
        }

        synchronized void removeCompletedRequest(@NonNull CompletedRequest request) {
            completedRequests.remove(request);
        }

        synchronized void removeCompletedRequest(int requestId) {
            for (int i = 0; i < completedRequests.size(); ++i) {
                final CompletedRequest completedRequest = completedRequests.get(i);
                if (completedRequest.id == requestId) {
                    completedRequests.remove(i);
                    break;
                }
            }
        }

        synchronized void removeFailedRequest(@NonNull FailedRequest request) {
            failedRequests.remove(request);
        }

        synchronized void addRequest(@NonNull CancellableRequest request) {
            queuedRequests.add(request);
        }

        synchronized boolean removeRequest(@NonNull CancellableRequest request) {
            return queuedRequests.remove(request);
        }

        synchronized boolean hasQueuedRequests() {
            return !queuedRequests.isEmpty();
        }

        synchronized boolean cancelAllRequests() {
            boolean cancelledSomething = false;

            if (currentlyExecutingRequest != null) {
                currentlyExecutingRequest.requestCancellation();
                cancelledSomething = true;
            }

            for (CancellableRequest request : queuedRequests) {
                request.requestCancellation();
                cancelledSomething = true;
            }

            queuedRequests.clear();

            return cancelledSomething;
        }

        @NonNull
        synchronized ArrayList<Uri> getAllSourceUris() {
            final ArrayList<Uri> uris = new ArrayList<>();
            for (CancellableRequest request : queuedRequests) {
                for (MediaItem source : request.sources) {
                    uris.add(source.getUri());
                }
            }
            return uris;
        }

        @NonNull
        synchronized CancellableRequest getNextQueuedRequest() {
            if (queuedRequests.isEmpty()) {
                throw new NoSuchElementException();
            }
            return queuedRequests.remove(0);
        }

        synchronized void setCurrentlyExecuting(@NonNull CancellableRequest request) {
            currentlyExecutingRequest = request;
        }

        synchronized void addCompletedRequestAndClearOngoingRequest(@NonNull CompletedRequest request) {
            completedRequests.add(request);
            currentlyExecutingRequest = null;
        }

        synchronized void addFailedRequestAndClearOngoingRequest(@NonNull FailedRequest request) {
            failedRequests.add(request);
            currentlyExecutingRequest = null;
        }

        synchronized void clearOngoingRequest() {
            currentlyExecutingRequest = null;
        }

        synchronized void postRequestState() {
            requestsState.postValue(new RequestsState(
                    queuedRequests, completedRequests, failedRequests, currentlyExecutingRequest));
        }
    }

    // Works like a snapshot.
    public static final class RequestsState {
        @NonNull
        public final List<CancellableRequest> queuedRequests;
        @NonNull
        public final List<CompletedRequest> completedRequests;
        @NonNull
        public final List<FailedRequest> failedRequests;
        @Nullable
        public final CancellableRequest currentlyExecutingRequest;

        RequestsState(@NonNull List<CancellableRequest> queuedRequests,
                      @NonNull List<CompletedRequest> completedRequests,
                      @NonNull List<FailedRequest> failedRequests,
                      @Nullable CancellableRequest currentlyExecutingRequest) {
            this.queuedRequests = new ArrayList<>(queuedRequests);
            this.completedRequests = new ArrayList<>(completedRequests);
            this.failedRequests = new ArrayList<>(failedRequests);
            this.currentlyExecutingRequest = currentlyExecutingRequest;
        }
    }
}
