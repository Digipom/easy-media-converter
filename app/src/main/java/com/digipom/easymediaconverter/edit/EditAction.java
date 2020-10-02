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
package com.digipom.easymediaconverter.edit;

import androidx.annotation.StringRes;

import com.digipom.easymediaconverter.R;

public enum EditAction {
    CONVERT, CONVERT_TO_VIDEO, EXTRACT_AUDIO, TRIM, CUT, ADJUST_SPEED, ADJUST_VOLUME, ADD_SILENCE, NORMALIZE,
    SPLIT, COMBINE, SET_AS_RINGTONE;

    @StringRes
    public int descriptionForOngoingTaskType() {
        switch (this) {
            case CONVERT:
                return R.string.converting;
            case CONVERT_TO_VIDEO:
                return R.string.converting_to_video;
            case EXTRACT_AUDIO:
                return R.string.extracting_audio;
            case TRIM:
                return R.string.trimming;
            case CUT:
                return R.string.cutting;
            case ADJUST_SPEED:
                return R.string.adjusting_speed;
            case ADJUST_VOLUME:
                return R.string.adjusting_volume;
            case ADD_SILENCE:
                return R.string.adding_silence;
            case NORMALIZE:
                return R.string.normalizing;
            case SPLIT:
                return R.string.splitting;
            case COMBINE:
                return R.string.combining;
            case SET_AS_RINGTONE:
                return R.string.setting_ringtone;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @StringRes
    public int descriptionForCompletedTaskType() {
        switch (this) {
            case CONVERT:
                return R.string.converted;
            case CONVERT_TO_VIDEO:
                return R.string.converted_to_video;
            case EXTRACT_AUDIO:
                return R.string.extracted_audio;
            case TRIM:
                return R.string.trimmed;
            case CUT:
                return R.string.was_cut;
            case ADJUST_SPEED:
                return R.string.adjusted_speed;
            case ADJUST_VOLUME:
                return R.string.adjusted_volume;
            case ADD_SILENCE:
                return R.string.added_silence;
            case NORMALIZE:
                return R.string.normalized;
            case SPLIT:
                return R.string.was_split;
            case COMBINE:
                return R.string.combined;
            case SET_AS_RINGTONE:
                return R.string.was_set_as_ringtone;
            default:
                throw new UnsupportedOperationException();
        }
    }
}
