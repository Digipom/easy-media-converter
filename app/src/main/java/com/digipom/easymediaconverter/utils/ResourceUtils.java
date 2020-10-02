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

import android.content.Context;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public abstract class ResourceUtils {
    @NonNull
    public static String getTextFromResource(@NonNull Context context, int resourceId) {
        try {
            return getTextFromInputStream(context.getResources().openRawResource(resourceId));
        } catch (Exception e) {
            Logger.w(e);
            return e.toString();
        }
    }

    @NonNull
    public static String getTextFromFile(@NonNull File file) {
        try {
            return getTextFromInputStream(new FileInputStream(file));
        } catch (Exception e) {
            Logger.w(e);
            return e.toString();
        }
    }

    @NonNull
    private static String getTextFromInputStream(@NonNull InputStream inputStream) throws IOException {
        final StringBuilder body = new StringBuilder();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String nextLine;

            while ((nextLine = bufferedReader.readLine()) != null) {
                body.append(nextLine);
                body.append('\n');
            }
        }

        return body.toString();
    }
}
