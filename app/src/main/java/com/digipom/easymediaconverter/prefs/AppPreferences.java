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
package com.digipom.easymediaconverter.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.R;

@MainThread
public class AppPreferences {
    private static final long ONE_DAY_MS = 1000 * 60 * 60 * 24;
    private static final long DELAY_BEFORE_SHOWING_RATE_REQUEST = 7 * ONE_DAY_MS;
    private final Context context;
    private final SharedPreferences preferences;
    private final MutableLiveData<Boolean> shouldShowRateRequestLiveData = new MutableLiveData<>();

    public AppPreferences(@NonNull Context context) {
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        updateShouldShowRateRequestLiveData();
    }

    @NonNull
    public LiveData<Boolean> shouldShowRateRequestLiveData() {
        return shouldShowRateRequestLiveData;
    }

    public void incrementSuccessfulOpCount() {
        final int currentCount = currentSuccessfulOpCount();
        preferences.edit()
                .putInt(context.getString(R.string.successful_op_count_key), currentCount + 1)
                .apply();
    }

    public int currentSuccessfulOpCount() {
        return preferences.getInt(context.getString(R.string.successful_op_count_key), 0);
    }

    public boolean shouldShowRateRequest() {
        if (hasShownRateRequest()) {
            return false;
        } else {
            int currentSuccessfulOpCount = currentSuccessfulOpCount();
            long firstCheckDate = preferences.getLong(
                    context.getString(R.string.first_check_for_rate_request_date_key), -1);
            if (firstCheckDate == -1) {
                firstCheckDate = System.currentTimeMillis();
                preferences.edit()
                        .putLong(context.getString(R.string.first_check_for_rate_request_date_key),
                                firstCheckDate)
                        .apply();
            }

            return (System.currentTimeMillis() - firstCheckDate > DELAY_BEFORE_SHOWING_RATE_REQUEST)
                    && currentSuccessfulOpCount > 1;
        }
    }

    public void sawRateRequest() {
        preferences.edit()
                .putBoolean(context.getString(R.string.has_shown_rate_request_key), true)
                .apply();
        updateShouldShowRateRequestLiveData();
    }

    private boolean hasShownRateRequest() {
        return preferences.getBoolean(
                context.getString(R.string.has_shown_rate_request_key), false);
    }

    public boolean shouldShowNoThanksOptionForRateRequest() {
        return preferences.getBoolean(
                context.getString(R.string.should_show_no_thanks_for_rate_request_key), false);
    }

    public void resetRateRequest() {
        // Only reset "seen" state, and set time so that the next request will come in 30 days from now.
        preferences.edit()
                .putBoolean(context.getString(R.string.has_shown_rate_request_key), false)
                .putLong(context.getString(R.string.first_check_for_rate_request_date_key),
                        System.currentTimeMillis() + (ONE_DAY_MS * 30) - DELAY_BEFORE_SHOWING_RATE_REQUEST)
                // Next time we show it, add a "No thanks" option.
                .putBoolean(context.getString(R.string.should_show_no_thanks_for_rate_request_key), true)
                .apply();
        updateShouldShowRateRequestLiveData();
    }

    public void dismissRateRequestForNow() {
        // Like resetRateRequest(), but "lighter". For example, if the rate request was swiped away
        // without "not now" being tapped.
        preferences.edit()
                .putBoolean(context.getString(R.string.has_shown_rate_request_key), false)
                .putLong(context.getString(R.string.first_check_for_rate_request_date_key),
                        System.currentTimeMillis() + (ONE_DAY_MS * 7) - DELAY_BEFORE_SHOWING_RATE_REQUEST)
                .apply();
        updateShouldShowRateRequestLiveData();
    }

    private void updateShouldShowRateRequestLiveData() {
        shouldShowRateRequestLiveData.setValue(shouldShowRateRequest());
    }
}
