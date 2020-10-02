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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.media.MediaItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RecentlyOpenedRepository {
    private final List<RecentItem> recents = new ArrayList<>();
    private final MutableLiveData<List<RecentItem>> recentlyOpenedLiveData = new MutableLiveData<>();

    public RecentlyOpenedRepository() {
        recentlyOpenedLiveData.setValue(recents);
    }

    // Observable data

    @NonNull
    LiveData<List<RecentItem>> recentlyOpenedItems() {
        return recentlyOpenedLiveData;
    }

    public void addOrUpdateRecentItem(@NonNull MediaItem newItem) {
        for (RecentItem item : recents) {
            if (item.mediaItem.getUri().equals(newItem.getUri())) {
                // Already present -- just update the timestamp.
                item.updateLongAccessTimestamp();
                return;
            }
        }

        recents.add(new RecentItem(newItem));
        recentlyOpenedLiveData.setValue(new ArrayList<>(recents));
    }

    void removeRecentItem(@NonNull RecentItem item) {
        recents.remove(item);
        recentlyOpenedLiveData.setValue(new ArrayList<>(recents));
    }

    void clearAll() {
        recents.clear();
        recentlyOpenedLiveData.setValue(new ArrayList<>(recents));
    }

    void removeMatchingRecents(@NonNull Uri uri) {
        final Iterator<RecentItem> it = recents.iterator();
        while (it.hasNext()) {
            final RecentItem item = it.next();
            if (item.mediaItem.getUri().equals(uri)) {
                it.remove();
                recentlyOpenedLiveData.setValue(new ArrayList<>(recents));
                return;
            }
        }
    }

    static class RecentItem {
        final MediaItem mediaItem;
        private long lastAccessTimestamp;

        RecentItem(@NonNull MediaItem mediaItem) {
            this.mediaItem = mediaItem;
            this.lastAccessTimestamp = System.currentTimeMillis();
        }

        long getLastAccessTimestamp() {
            return lastAccessTimestamp;
        }

        void updateLongAccessTimestamp() {
            lastAccessTimestamp = System.currentTimeMillis();
        }
    }
}