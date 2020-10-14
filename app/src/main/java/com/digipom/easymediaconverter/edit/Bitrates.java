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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public abstract class Bitrates {
    public enum BitrateType {
        CBR, VBR, ABR
    }

    public static abstract class BitrateRange {
        public abstract int numSteps();

        public abstract int defaultStep();

        public abstract int bitrateValueForStep(int step);

        public abstract int closestStepForBitrate(int value);
    }

    private static class CbrBitrateRange extends BitrateRange {
        @NonNull
        private final int[] kbpsOptions;
        private final int defaultStep;

        CbrBitrateRange(@NonNull int[] kbpsOptions, int defaultKbps) {
            this.kbpsOptions = kbpsOptions;
            this.defaultStep = indexOf(kbpsOptions, defaultKbps);
        }

        @Override
        public int numSteps() {
            return kbpsOptions.length;
        }

        @Override
        public int defaultStep() {
            return defaultStep;
        }

        @Override
        public int bitrateValueForStep(int step) {
            return kbpsOptions[Math.max(0, Math.min(numSteps() - 1, step))];
        }

        @Override
        public int closestStepForBitrate(int kbps) {
            return indexOfClosestMatch(kbpsOptions, kbps);
        }

        static int indexOf(@NonNull int[] kbpsOptions, int kbps) {
            for (int i = 0; i < kbpsOptions.length; ++i) {
                if (kbpsOptions[i] == kbps) {
                    return i;
                }
            }
            throw new IllegalArgumentException("kbps " + kbps + " not found in array "
                    + Arrays.toString(kbpsOptions));
        }

        static int indexOfClosestMatch(@NonNull int[] kbpsOptions, int kbps) {
            int closestMatchIdx = 0;
            int closestDelta = Integer.MAX_VALUE;

            for (int i = 0; i < kbpsOptions.length; ++i) {
                final int delta = Math.abs(kbpsOptions[i] - kbps);
                if (delta < closestDelta) {
                    closestMatchIdx = i;
                    closestDelta = delta;
                }
            }

            return closestMatchIdx;
        }
    }

    private static class AbrBitrateRange extends BitrateRange {
        public final int minKbps;
        public final int maxKbps;
        public final int defaultKbps;
        public final int stepSizeKbps;

        AbrBitrateRange(int minKbps, int maxKbps, int defaultKbps, int stepSizeKbps) {
            this.minKbps = minKbps;
            this.maxKbps = maxKbps;
            this.defaultKbps = defaultKbps;
            this.stepSizeKbps = stepSizeKbps;
        }

        @Override
        public int numSteps() {
            return (maxKbps - minKbps) / stepSizeKbps;
        }

        @Override
        public int defaultStep() {
            return (defaultKbps - minKbps) / stepSizeKbps;
        }

        @Override
        public int bitrateValueForStep(int step) {
            if (step <= 0) {
                return minKbps;
            } else if (step >= numSteps()) {
                return maxKbps;
            } else {
                return minKbps + step * stepSizeKbps;
            }
        }

        @Override
        public int closestStepForBitrate(int kbps) {
            if (kbps <= minKbps) {
                return 0;
            } else if (kbps >= maxKbps) {
                return numSteps() - 1;
            } else {
                return Math.round((kbps - minKbps) / (float) stepSizeKbps);
            }
        }
    }

    private static class VbrBitrateRange extends BitrateRange {
        public final int minQuality;
        public final int maxQuality;
        public final int defaultQuality;
        public final int stepSize;

        VbrBitrateRange(int minQuality, int maxQuality, int defaultQuality, int stepSize) {
            this.minQuality = minQuality;
            this.maxQuality = maxQuality;
            this.defaultQuality = defaultQuality;
            this.stepSize = stepSize;
        }

        @Override
        public int numSteps() {
            return Math.abs(maxQuality - minQuality) / Math.abs(stepSize);
        }

        @Override
        public int defaultStep() {
            return Math.abs(defaultQuality - minQuality) / Math.abs(stepSize);
        }

        @Override
        public int bitrateValueForStep(int step) {
            if (step <= 0) {
                return minQuality;
            } else if (step >= numSteps()) {
                return maxQuality;
            } else {
                return minQuality + step * stepSize;
            }
        }

        @Override
        public int closestStepForBitrate(int value) {
            throw new UnsupportedOperationException("not implemented");
        }
    }

    public static class BitrateWithValue {
        @NonNull
        public final BitrateType type;
        public final int value;

        public BitrateWithValue(@NonNull BitrateType type, int value) {
            this.type = type;
            this.value = value;
        }
    }

    @NonNull
    public static HashMap<BitrateType, BitrateRange> getMp3BitrateSpecs() {
        final HashMap<BitrateType, BitrateRange> map = new HashMap<>();
        map.put(BitrateType.CBR,
                new CbrBitrateRange(
                        new int[]{8, 16, 24, 32, 40, 48, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
                        192));
        map.put(BitrateType.ABR,
                new AbrBitrateRange(8, 320, 192, 8));
        map.put(BitrateType.VBR,
                new VbrBitrateRange(9, 0, 2, -1));
        return map;
    }

    @NonNull
    public static HashMap<BitrateType, BitrateRange> getAacBitrateSpecs() {
        final HashMap<BitrateType, BitrateRange> map = new HashMap<>();
        final ArrayList<Integer> cbrRatesList = new ArrayList<>();
        for (int i = 16; i < 40; i += 2) {
            cbrRatesList.add(i);
        }
        for (int i = 40; i < 128; i += 4) {
            cbrRatesList.add(i);
        }
        for (int i = 128; i <= 320; i += 8) {
            cbrRatesList.add(i);
        }
        final int[] cbrRates = new int[cbrRatesList.size()];
        for (int i = 0; i < cbrRatesList.size(); ++i) {
            cbrRates[i] = cbrRatesList.get(i);
        }

        map.put(BitrateType.CBR, new CbrBitrateRange(cbrRates, 128));
        return map;
    }
}