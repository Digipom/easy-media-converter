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
package com.digipom.easymediaconverter.simple_time_picker;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Objects;

import static android.app.Activity.RESULT_OK;

public class TimePickerDialogFragment extends SimpleTimePickerDialogFragment {
    public static final String INTENT_EXTRA_MS = "INTENT_EXTRA_MS";

    public static void show(@NonNull FragmentManager fragmentManager, @NonNull Fragment target,
                            int requestCode, @NonNull String title,
                            long defaultValueMs, long minValueMs, long maxValueMs) {
        final TimePickerDialogFragment fragment = new TimePickerDialogFragment();
        fragment.setArguments(SimpleTimePickerDialogFragment.prepArgs(title, defaultValueMs, minValueMs, maxValueMs));
        fragment.setTargetFragment(target, requestCode);
        fragment.show(fragmentManager, TimePickerDialogFragment.class.getName());
    }

    @Override
    protected void onApplySelected(long ms) {
        final Intent data = new Intent();
        data.putExtra(INTENT_EXTRA_MS, ms);
        Objects.requireNonNull(getTargetFragment()).onActivityResult(getTargetRequestCode(), RESULT_OK, data);
    }
}
