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
package com.digipom.easymediaconverter.player.combine;

import android.app.Application;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.DurationLoader;
import com.digipom.easymediaconverter.utils.FilenameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.digipom.easymediaconverter.player.FilenamingUtils.getAppendNamingForOutput;
import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.formatDuration;
import static com.digipom.easymediaconverter.utils.DurationLoader.DURATION_NOT_LOADED;

public class CombineActionViewModel extends AndroidViewModel {
    private final LoadableDurationCache loadableDurationCache;
    private final MutableLiveData<Boolean> showMergeFab = new MutableLiveData<>();
    private final List<ListItem> items = new ArrayList<>();
    private MediaItem firstMediaItem;

    public CombineActionViewModel(@NonNull Application application) {
        super(application);
        loadableDurationCache = new LoadableDurationCache(new DurationLoader(application));
        showMergeFab.setValue(false);
    }

    @NonNull
    LiveData<Boolean> showMergeFab() {
        return showMergeFab;
    }

    void setFirstMediaItem(@NonNull MediaItem firstMediaItem) {
        this.firstMediaItem = firstMediaItem;
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return firstMediaItem.getMimeType();
    }

    @NonNull
    String getDefaultOutputFilename() {
        return FilenameUtils.appendToFilename(firstMediaItem.getFilename(),
                getAppendNamingForOutput(getApplication(), EditAction.COMBINE));
    }

    // List handling

    @NonNull
    LoadableDurationCache getLoadableDurationCache() {
        return loadableDurationCache;
    }

    @NonNull
    ListItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    MediaItem[] getAllMediaItemsInList() {
        final ArrayList<MediaItem> arrayList = new ArrayList<>();
        for (ListItem item : items) {
            arrayList.add(item.mediaItem);
        }
        return arrayList.toArray(new MediaItem[0]);
    }

    int getItemCount() {
        return items.size();
    }

    int onItemAdded(@NonNull MediaItem mediaItem) {
        for (ListItem item : items) {
            if (item.mediaItem.equals(mediaItem)) {
                return AdapterView.INVALID_POSITION;
            }
        }

        final ListItem listItem = new ListItem(mediaItem);
        items.add(listItem);
        updateFabState();
        return items.size() - 1;
    }

    void onItemRemoved(int position) {
        items.remove(position);
        updateFabState();
    }

    void onItemMoved(int fromPosition, int toPosition) {
        final ListItem item = items.get(fromPosition);
        items.remove(fromPosition);
        items.add(toPosition, item);
    }

    private void updateFabState() {
        final boolean showFab = getItemCount() > 1;
        if (!showFab == Objects.requireNonNull(showMergeFab.getValue())) {
            showMergeFab.setValue(showFab);
        }
    }

    static class ListItem {
        final MediaItem mediaItem;

        ListItem(@NonNull MediaItem item) {
            this.mediaItem = item;
        }
    }

    static class LoadableDurationCache {
        private final DurationLoader durationLoader;
        private final Map<MediaItem, LiveData<Long>> loadableDurationMap = new HashMap<>();

        LoadableDurationCache(@NonNull DurationLoader durationLoader) {
            this.durationLoader = durationLoader;
        }

        @NonNull
        LiveData<Long> getLoadableDuration(@NonNull MediaItem item) {
            LiveData<Long> loadableDuration = loadableDurationMap.get(item);
            if (loadableDuration != null) {
                return loadableDuration;
            }
            loadableDuration = createLoadableDuration(item);
            loadableDurationMap.put(item, loadableDuration);
            return loadableDuration;
        }

        private LiveData<Long> createLoadableDuration(@NonNull MediaItem item) {
            if (item.getOptionalDurationMs() >= 0) {
                // We already have a duration, so no need to load one.
                final MutableLiveData<Long> durationMs = new MutableLiveData<>();
                durationMs.setValue(item.getOptionalDurationMs());
                return durationMs;
            } else {
                return durationLoader.loadDuration(item.getUri());
            }
        }
    }

    static class DurationViewHolderHelper {
        public interface OnDurationLoadedListener {
            void onDurationLoadedForItemAndViewHolderHasChanged();
        }

        private final StringBuilder builder = new StringBuilder();
        private final TextView durationView;
        private final LifecycleOwner owner;
        private final LoadableDurationCache loadableDurationCache;
        private final OnDurationLoadedListener listener;
        private MediaItem boundItem;

        DurationViewHolderHelper(@NonNull TextView durationView,
                                 @NonNull LifecycleOwner owner,
                                 @NonNull LoadableDurationCache loadableDurationCache,
                                 @NonNull OnDurationLoadedListener listener) {
            this.owner = owner;
            this.loadableDurationCache = loadableDurationCache;
            this.listener = listener;
            this.durationView = durationView;
        }

        void bindWithMediaItem(@NonNull final MediaItem item) {
            boundItem = item;

            if (item.getOptionalDurationMs() >= 0) {
                // We already have a duration -- no need to go through loading a duration.
                formatDuration(builder, durationView, item.getOptionalDurationMs());
            } else {
                final LiveData<Long> loadableDuration = loadableDurationCache.getLoadableDuration(item);
                final long durationMs = Objects.requireNonNull(loadableDuration.getValue());
                if (durationMs > 0) {
                    formatDuration(builder, durationView, durationMs);
                } else if (durationMs == DURATION_NOT_LOADED) {
                    durationView.setText("");
                    // Observe the value, taking care to remove any current observers for this value.
                    loadableDuration.removeObservers(owner);
                    loadableDuration.observe(owner, new Observer<Long>() {
                        @Override
                        public void onChanged(@Nullable Long newValue) {
                            if (Objects.requireNonNull(newValue) != DURATION_NOT_LOADED) {
                                if (item.equals(boundItem)) {
                                    // Viewholder is still bound to the same item, so we can do a direct
                                    // update.
                                    if (newValue >= 0) {
                                        formatDuration(builder, durationView, newValue);
                                        durationView.setAlpha(0f);
                                        durationView.animate().alpha(1f);
                                    } else {
                                        durationView.setText("--:--");
                                    }
                                } else {
                                    // Viewholder is bound to another item.
                                    listener.onDurationLoadedForItemAndViewHolderHasChanged();
                                }
                                // No longer need to observe -- not expecting any further changes.
                                loadableDuration.removeObservers(owner);
                            }
                        }
                    });
                } else {
                    durationView.setText("--:--");
                }
            }
        }
    }
}
