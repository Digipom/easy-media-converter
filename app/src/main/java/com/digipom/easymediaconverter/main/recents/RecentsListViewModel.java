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
package com.digipom.easymediaconverter.main.recents;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.CancellableRequest;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.CompletedRequest;
import com.digipom.easymediaconverter.ffmpeg.FFMpegRequests.FailedRequest;
import com.digipom.easymediaconverter.main.recents.RecentlyOpenedRepository.RecentItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RecentsListViewModel extends AndroidViewModel {
    private final FFMpegController ffMpegController;
    private RecentlyOpenedRepository recentlyOpenedRepository;

    private MediatorLiveData<List<ListItem>> list;
    private final ProgressLiveData progressForCurrentItem = new ProgressLiveData();

    private List<RecentItem> recentlyOpened;
    private FFMpegController.RequestsState requestsState;

    public RecentsListViewModel(@NonNull Application application) {
        super(application);
        this.ffMpegController = ((BaseApplication) application).getServiceLocator().getFFMpegController();
    }

    // List handling

    void setRecentlyOpenedRepository(@NonNull RecentlyOpenedRepository recentlyOpenedRepository) {
        this.recentlyOpenedRepository = recentlyOpenedRepository;
    }

    @NonNull
    LiveData<List<ListItem>> list() {
        if (list == null) {
            list = new MediatorLiveData<>();
            list.addSource(
                    recentlyOpenedRepository.recentlyOpenedItems(),
                    new Observer<List<RecentItem>>() {
                        @Override
                        public void onChanged(@Nullable List<RecentItem> list) {
                            recentlyOpened = list;
                            rebuildList();
                        }
                    }
            );
            list.addSource(
                    ffMpegController.requestsState(),
                    new Observer<FFMpegController.RequestsState>() {
                        @Override
                        public void onChanged(FFMpegController.RequestsState state) {
                            requestsState = state;
                            rebuildList();
                        }
                    }
            );
        }
        return list;
    }

    @NonNull
    LiveData<Float> progressForCurrentItem() {
        return progressForCurrentItem;
    }

    void refreshList() {
        rebuildList();
    }

    void onClearPreviousTapped() {
        recentlyOpenedRepository.clearAll();
    }

    void onUserSwipedAwayItem(int position) {
        final ListItem item = Objects.requireNonNull(list.getValue()).get(position);
        if (item instanceof RecentlyOpenedListItem) {
            recentlyOpenedRepository.removeRecentItem(((RecentlyOpenedListItem) item).recentItem);
        } else if (item instanceof CancellableRequestListItem) {
            onUserCancelledRequest((CancellableRequestListItem) item);
        } else if (item instanceof CompletedRequestListItem) {
            onUserRemovedItem((CompletedRequestListItem) item);
        } else if (item instanceof FailedRequestListItem) {
            onUserRemovedItem((FailedRequestListItem) item);
        }
    }

    private void onUserCancelledRequest(@NonNull CancellableRequestListItem item) {
        ffMpegController.cancelRequest(item.request);
    }

    private void onUserRemovedItem(@NonNull CompletedRequestListItem item) {
        ffMpegController.removeCompletedRequest(item.request);
    }

    private void onUserRemovedItem(@NonNull FailedRequestListItem item) {
        ffMpegController.removeFailedRequest(item.request);
    }

    private boolean hasNonCancelledRequests(@Nullable CancellableRequest currentlyExecutingRequest,
                                            @NonNull List<CancellableRequest> queuedRequests) {
        if (currentlyExecutingRequest != null && !currentlyExecutingRequest.isCancelled()) {
            return true;
        }
        for (CancellableRequest queuedRequest : queuedRequests) {
            if (!queuedRequest.isCancelled()) {
                return true;
            }
        }

        return false;
    }

    private void rebuildList() {
        final ArrayList<ListItem> newItems = new ArrayList<>();

        // Recents
        if (requestsState != null) {
            // Anything currently executing or queued goes first
            if (hasNonCancelledRequests(requestsState.currentlyExecutingRequest, requestsState.queuedRequests)
                    || !requestsState.completedRequests.isEmpty()
                    || !requestsState.failedRequests.isEmpty()) {
                newItems.add(new RecentHeaderListItem());
            }

            if (requestsState.currentlyExecutingRequest != null
                    && !requestsState.currentlyExecutingRequest.isCancelled()) {
                newItems.add(new CancellableRequestListItem(requestsState.currentlyExecutingRequest, true));
                progressForCurrentItem.linkToProgress(requestsState.currentlyExecutingRequest.progress());
            } else {
                progressForCurrentItem.clearLinkToProgress();
            }

            for (CancellableRequest request : requestsState.queuedRequests) {
                if (!request.isCancelled()) {
                    newItems.add(new CancellableRequestListItem(request, false));
                }
            }

            // All completed and failed items follow -- sorted by timestamp
            final ArrayList<ListItem> completedOrFailed = new ArrayList<>();
            for (CompletedRequest request : requestsState.completedRequests) {
                completedOrFailed.add(new CompletedRequestListItem(request));
            }
            for (FailedRequest request : requestsState.failedRequests) {
                completedOrFailed.add(new FailedRequestListItem(request));
            }
            sortByTimestamp(completedOrFailed);
            newItems.addAll(completedOrFailed);
        }

        // Previous -- here we put stuff we opened previously. Sorted by timestamp.
        if (recentlyOpened != null && !recentlyOpened.isEmpty()) {
            newItems.add(new PreviousHeaderListItem());

            final ArrayList<ListItem> previousItems = new ArrayList<>();
            for (RecentItem item : recentlyOpened) {
                previousItems.add(new RecentlyOpenedListItem(item));
            }
            sortByTimestamp(previousItems);
            newItems.addAll(previousItems);
        }
        if (!newItems.isEmpty()) {
            newItems.add(new FooterListItem());
        }
        list.setValue(newItems);
    }

    interface ListItem {
        // Marker interface
    }

    static class RecentHeaderListItem implements ListItem {
        // Marker class
    }

    static class PreviousHeaderListItem implements ListItem {
        // Marker class
    }

    static class RecentlyOpenedListItem implements ListItem {
        final RecentItem recentItem;

        RecentlyOpenedListItem(@NonNull RecentItem recentItem) {
            this.recentItem = recentItem;
        }
    }

    abstract static class RequestListItem implements ListItem {
        final int id;

        RequestListItem(int id) {
            this.id = id;
        }
    }

    static class CancellableRequestListItem extends RequestListItem {
        final CancellableRequest request;
        final boolean isCurrent;

        CancellableRequestListItem(@NonNull CancellableRequest request, boolean isCurrent) {
            super(request.id);
            this.request = request;
            this.isCurrent = isCurrent;
        }
    }

    static class CompletedRequestListItem extends RequestListItem {
        final CompletedRequest request;

        CompletedRequestListItem(@NonNull CompletedRequest request) {
            super(request.id);
            this.request = request;
        }
    }

    static class FailedRequestListItem extends RequestListItem {
        final FailedRequest request;

        FailedRequestListItem(@NonNull FailedRequest request) {
            super(request.id);
            this.request = request;
        }
    }

    private static class FooterListItem implements ListItem {
        // Marker class
    }

    private static class ProgressLiveData extends MediatorLiveData<Float> {
        private LiveData<Float> existingLink;

        @MainThread
        void linkToProgress(@NonNull LiveData<Float> progressLiveData) {
            clearLinkToProgress();
            addSource(progressLiveData, new Observer<Float>() {
                @Override
                public void onChanged(Float possibleProgress) {
                    setValue(possibleProgress);
                }
            });
            existingLink = progressLiveData;
        }

        @MainThread
        void clearLinkToProgress() {
            if (existingLink != null) {
                removeSource(existingLink);
                existingLink = null;
            }
        }
    }

    private void sortByTimestamp(@NonNull ArrayList<ListItem> list) {
        Collections.sort(list, new Comparator<ListItem>() {
            @Override
            public int compare(ListItem o1, ListItem o2) {
                long o1Timestamp = 0;
                long o2Timestamp = 0;

                if (o1 instanceof RecentlyOpenedListItem) {
                    o1Timestamp = ((RecentlyOpenedListItem) o1).recentItem.getLastAccessTimestamp();
                } else if (o1 instanceof CompletedRequestListItem) {
                    o1Timestamp = ((CompletedRequestListItem) o1).request.timestamp;
                } else if (o1 instanceof FailedRequestListItem) {
                    o1Timestamp = ((FailedRequestListItem) o1).request.timestamp;
                }

                if (o2 instanceof RecentlyOpenedListItem) {
                    o2Timestamp = ((RecentlyOpenedListItem) o2).recentItem.getLastAccessTimestamp();
                } else if (o2 instanceof CompletedRequestListItem) {
                    o2Timestamp = ((CompletedRequestListItem) o2).request.timestamp;
                } else if (o2 instanceof FailedRequestListItem) {
                    o2Timestamp = ((FailedRequestListItem) o2).request.timestamp;
                }

                return Long.compare(o2Timestamp, o1Timestamp);
            }
        });
    }
}