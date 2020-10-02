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

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.NumberPicker;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;

import com.digipom.easymediaconverter.R;

import java.util.Locale;
import java.util.Objects;

import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.getFormattedDuration;
import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.getFormattedDurationWithTenths;
import static com.digipom.easymediaconverter.utils.DurationFormatterUtils.getTimeDurationForAccessibility;

public abstract class SimpleTimePickerDialogFragment extends DialogFragment {
    private static final String BUNDLE_TITLE = "BUNDLE_TITLE";
    private static final String BUNDLE_DEFAULT_VALUE_MS = "BUNDLE_DEFAULT_VALUE_MS";
    private static final String BUNDLE_MIN_VALUE_MS = "BUNDLE_MIN_VALUE_MS";
    private static final String BUNDLE_MAX_VALUE_MS = "BUNDLE_MAX_VALUE_MS";
    private NumberPicker hoursPicker;
    private NumberPicker minutesPicker;
    private NumberPicker secondsPicker;
    private NumberPicker tenthsPicker;
    private long minValueMs;
    private long maxValueMs;

    static @NonNull
    Bundle prepArgs(@NonNull String title,
                    long defaultValueMs,
                    long minValueMs,
                    long maxValueMs) {
        final Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);
        bundle.putLong(BUNDLE_DEFAULT_VALUE_MS, defaultValueMs);
        bundle.putLong(BUNDLE_MIN_VALUE_MS, minValueMs);
        bundle.putLong(BUNDLE_MAX_VALUE_MS, maxValueMs);
        return bundle;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Bundle args = Objects.requireNonNull(getArguments());
        final String title = args.getString(BUNDLE_TITLE);
        final long defaultValueMs = args.getLong(BUNDLE_DEFAULT_VALUE_MS);
        minValueMs = args.getLong(BUNDLE_MIN_VALUE_MS);
        maxValueMs = args.getLong(BUNDLE_MAX_VALUE_MS);

        final AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(
                        Objects.requireNonNull(getActivity()), R.style.AppTheme_MaterialAlertDialog))
                .setTitle(title)
                .setView(R.layout.fragment_simple_time_picker_dialog)
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (getActivity() != null && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
                                && hoursPicker != null && minutesPicker != null && secondsPicker != null && tenthsPicker != null) {
                            onApplySelected(countMilliseconds(hoursPicker.getValue(), minutesPicker.getValue(), secondsPicker.getValue(), tenthsPicker.getValue()));
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final TableLayout tableLayout = (TableLayout) Objects.requireNonNull(alertDialog.findViewById(R.id.table_layout));

                hoursPicker = (NumberPicker) Objects.requireNonNull(alertDialog.findViewById(R.id.hours_picker));
                minutesPicker = (NumberPicker) Objects.requireNonNull(alertDialog.findViewById(R.id.minutes_picker));
                secondsPicker = (NumberPicker) Objects.requireNonNull(alertDialog.findViewById(R.id.seconds_picker));
                tenthsPicker = (NumberPicker) Objects.requireNonNull(alertDialog.findViewById(R.id.tenths_picker));
                final TextView allowableRangeTextView = (TextView) Objects.requireNonNull(alertDialog.findViewById(R.id.allowable_range_textview));

                final StringBuilder builder = new StringBuilder();
                final String formattedMinDuration = getFormattedDuration(builder, minValueMs);
                final String formattedMaxDuration = getFormattedDuration(builder, maxValueMs);
                final String formattedMinDurationMs = getFormattedDurationWithTenths(builder, minValueMs);
                final String formattedMaxDurationMs = getFormattedDurationWithTenths(builder, maxValueMs);
                allowableRangeTextView.setText(getString(R.string.allowable_time_range, formattedMinDurationMs, formattedMaxDurationMs));
                allowableRangeTextView.setContentDescription(getString(R.string.allowable_time_range, getTimeDurationForAccessibility(formattedMinDuration), getTimeDurationForAccessibility(formattedMaxDuration)));

                final NumberPicker.Formatter singleDigitFormatter = new NumberPicker.Formatter() {
                    @Override
                    public String format(int value) {
                        return String.format(Locale.getDefault(), "%01d", value);
                    }
                };
                final NumberPicker.Formatter doubleDigitFormatter = new NumberPicker.Formatter() {
                    @Override
                    public String format(int value) {
                        return String.format(Locale.getDefault(), "%02d", value);
                    }
                };

                final boolean allowOneHourOrLonger = countHours(msToSeconds(maxValueMs)) >= 1;

                if (!allowOneHourOrLonger) {
                    tableLayout.setColumnCollapsed(0, true);
                    tableLayout.setColumnCollapsed(1, true);
                    minutesPicker.setWrapSelectorWheel(false);
                }

                hoursPicker.setMinValue(0);
                final int hoursRange = getHoursRange(msToSeconds(maxValueMs));
                hoursPicker.setMaxValue(hoursRange);
                hoursPicker.setFormatter(hoursRange >= 10 ? doubleDigitFormatter : singleDigitFormatter);
                hoursPicker.setWrapSelectorWheel(false);

                minutesPicker.setMinValue(0);
                final int minutesRange = getMinutesRange(msToSeconds(maxValueMs));
                minutesPicker.setMaxValue(minutesRange);
                minutesPicker.setFormatter(minutesRange >= 10 ? doubleDigitFormatter : singleDigitFormatter);

                secondsPicker.setMinValue(0);
                final int secondsRange = getSecondsRange(msToSeconds(maxValueMs));
                secondsPicker.setMaxValue(secondsRange);
                secondsPicker.setFormatter(secondsRange >= 10 ? doubleDigitFormatter : singleDigitFormatter);

                tenthsPicker.setMinValue(0);
                final int tenthsRange = getTenthsRange(maxValueMs);
                tenthsPicker.setMaxValue(tenthsRange);
                tenthsPicker.setFormatter(tenthsRange >= 10 ? doubleDigitFormatter : singleDigitFormatter);

                hoursPicker.setValue(getHoursPortion(msToSeconds(defaultValueMs)));
                minutesPicker.setValue(getMinutesPortion(msToSeconds(defaultValueMs)));
                secondsPicker.setValue(getSecondsPortion(msToSeconds(defaultValueMs)));
                tenthsPicker.setValue(getTenthsPortion(defaultValueMs));

                hoursPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(NumberPicker picker, int oldHoursVal, int newHoursVal) {
                        updateTimePickersForNewTime(countMilliseconds(newHoursVal, minutesPicker.getValue(), secondsPicker.getValue(), tenthsPicker.getValue()));
                    }
                });
                minutesPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(NumberPicker picker, int oldMinutesVal, int newMinutesVal) {
                        int hoursVal = handleWrap(hoursPicker.getValue(), oldMinutesVal, newMinutesVal, 59);
                        updateTimePickersForNewTime(countMilliseconds(hoursVal, newMinutesVal, secondsPicker.getValue(), tenthsPicker.getValue()));
                    }
                });
                secondsPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(NumberPicker picker, int oldSecondsVal, int newSecondsVal) {
                        int minutesVal = handleWrap(minutesPicker.getValue(), oldSecondsVal, newSecondsVal, 59);
                        updateTimePickersForNewTime(countMilliseconds(hoursPicker.getValue(), minutesVal, newSecondsVal, tenthsPicker.getValue()));
                    }
                });
                tenthsPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(NumberPicker picker, int oldTenthsVal, int newTenthsVal) {
                        int secondsVal = handleWrap(secondsPicker.getValue(), oldTenthsVal, newTenthsVal, 9);
                        updateTimePickersForNewTime(countMilliseconds(hoursPicker.getValue(), minutesPicker.getValue(), secondsVal, newTenthsVal));
                    }
                });
            }
        });
        return alertDialog;
    }

    private int handleWrap(int superVal, int oldVal, int newVal, int maxVal) {
        if (oldVal == maxVal && newVal == 0) {
            // We wrapped in the positive direction.
            superVal += 1;
        } else if (oldVal == 0 && newVal == maxVal) {
            // We wrapped in the negative direction.
            superVal -= 1;
        }

        return superVal;
    }

    private void updateTimePickersForNewTime(long newTimeMs) {
        if (wouldValueOverflow(newTimeMs, maxValueMs)) {
            updatePickersForTime(maxValueMs);
        } else if (wouldValueUnderflow(newTimeMs, minValueMs)) {
            updatePickersForTime(minValueMs);
        } else {
            updatePickersForTime(newTimeMs);
        }
    }

    private void updatePickersForTime(long ms) {
        int hoursVal = getHoursPortion(msToSeconds(ms));
        int minutesVal = getMinutesPortion(msToSeconds(ms));
        int secondsVal = getSecondsPortion(msToSeconds(ms));
        int tenthsVal = getTenthsPortion(ms);

        if (hoursVal != hoursPicker.getValue()) {
            hoursPicker.setValue(hoursVal);
        }
        if (minutesVal != minutesPicker.getValue()) {
            minutesPicker.setValue(minutesVal);
        }
        if (secondsVal != secondsPicker.getValue()) {
            secondsPicker.setValue(secondsVal);
        }
        if (tenthsVal != tenthsPicker.getValue()) {
            tenthsPicker.setValue(tenthsVal);
        }
    }

    protected void onApplySelected(long ms) {
        // No-op
    }

    private long msToSeconds(long ms) {
        return ms / 1000;
    }

    private int getHoursRange(long seconds) {
        return (int) Math.min(countHours(seconds), 99);
    }

    private int getMinutesRange(long seconds) {
        return (int) Math.min(countMinutes(seconds), 59);
    }

    private int getSecondsRange(long seconds) {
        return (int) Math.min(seconds, 59);
    }

    private int getTenthsRange(long ms) {
        return (int) Math.min(ms / 100, 9);
    }

    private int getHoursPortion(long seconds) {
        return (int) countHours(seconds);
    }

    private int getMinutesPortion(long seconds) {
        return (int) ((seconds / 60) % 60);
    }

    private int getSecondsPortion(long seconds) {
        return (int) (seconds % 60);
    }

    private int getTenthsPortion(long ms) {
        return (int) (ms / 100) % 10;
    }

    private long countHours(long seconds) {
        return seconds / 3600;
    }

    private long countMinutes(long seconds) {
        return seconds / 60;
    }

    private long countMilliseconds(int hours, int minutes, int seconds, int tenths) {
        return ((long) (hours * 3600 + minutes * 60 + seconds)) * 1000 + tenths * 100;
    }

    private boolean wouldValueOverflow(long ms, long maxValueMs) {
        return ms > maxValueMs;
    }

    private boolean wouldValueUnderflow(long ms, long minValueMs) {
        return ms < minValueMs;
    }
}
