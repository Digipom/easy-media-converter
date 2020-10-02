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

import android.content.DialogInterface;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

public class AlertDialogUtils {
    @NonNull
    public static AlertDialog createWithCondensedFontUsingOnShowListener(@NonNull AlertDialog.Builder builder) {
        return createWithCondensedFontUsingOnShowListener(builder, null);
    }

    @NonNull
    public static AlertDialog createWithCondensedFontUsingOnShowListener(
            @NonNull AlertDialog.Builder builder,
            @Nullable final DialogInterface.OnShowListener wrappedListener) {
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                final TextView message = dialog.findViewById(android.R.id.message);
                if (message != null) {
                    message.setTypeface(FontCacher.getSansSerifCondensed());
                }
                if (wrappedListener != null) {
                    wrappedListener.onShow(dialogInterface);
                }
            }
        });
        return dialog;
    }
}
