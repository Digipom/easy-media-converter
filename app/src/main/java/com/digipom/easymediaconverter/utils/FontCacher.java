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
package com.digipom.easymediaconverter.utils;

import android.graphics.Typeface;

import androidx.annotation.NonNull;

class FontCacher {
    private static Typeface sSansSerifCondensed;
    private static Typeface sSansSerifCondensedItalic;

    // Avoids memory leak issues on some devices.
    @NonNull
    static synchronized Typeface getSansSerifCondensed() {
        if (sSansSerifCondensed == null) {
            sSansSerifCondensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        }

        return sSansSerifCondensed;
    }

    @NonNull
    static synchronized Typeface getSansSerifCondensedMediumItalic() {
        if (sSansSerifCondensedItalic == null) {
            sSansSerifCondensedItalic = Typeface.create("sans-serif-condensed-medium", Typeface.ITALIC);
        }

        return sSansSerifCondensedItalic;
    }
}
