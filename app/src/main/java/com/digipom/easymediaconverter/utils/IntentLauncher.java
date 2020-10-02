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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public final class IntentLauncher {
    public static void launchUrlInBrowserOrShowError(@NonNull Context context, @NonNull String uri,
                                                     @NonNull String noBrowserApp) {
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

        try {
            context.startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            // This should never happen on a real phone.
            Toast.makeText(context, noBrowserApp, Toast.LENGTH_LONG).show();
            Logger.w("No browser app", e);
        }
    }

    @NonNull
    public static Intent getChooserForEmailAppsOnly(@NonNull Context context,
                                                    @NonNull Intent sendEmailIntent,
                                                    @NonNull String chooserTitle) {
        final List<Intent> shareIntents = new ArrayList<>();

        final Intent emailOnlyIntent = new Intent(Intent.ACTION_SENDTO);
        emailOnlyIntent.setData(Uri.parse("mailto:"));

        final List<ResolveInfo> resolvedEmailApps = context.getPackageManager().queryIntentActivities(emailOnlyIntent, 0);
        if (resolvedEmailApps.isEmpty()) {
            // Weird situation to run into. Just return the original intent.
            return sendEmailIntent;
        }
        for (ResolveInfo resInfo : resolvedEmailApps) {
            Intent intent = new Intent(sendEmailIntent);
            intent.setComponent(new ComponentName(resInfo.activityInfo.packageName, resInfo.activityInfo.name));
            shareIntents.add(intent);
        }
        final Intent chooserIntent = Intent.createChooser(shareIntents.remove(shareIntents.size() - 1), chooserTitle);
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, shareIntents.toArray(new Intent[0]));
        return chooserIntent;
    }
}
