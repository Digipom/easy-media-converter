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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.provider.fileprovider.FileProviderUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.File;
import java.util.ArrayList;

public class ContactUsUtils {
    public static void openSendLogsEmail(@NonNull Context context) {
        final Intent sendEmailIntent = getIntentForSendLogs(context);

        final String infoBody = getMarketLine(context)
                + SystemUtils.getSystemAndPackageInformation(context) + "\n\n";
        final String logsBody = ((BaseApplication) context.getApplicationContext()).getLogs();
        final File tempLogFile = FileProviderUtils.writeLogsToTempFile(context, "logs", infoBody + "\n\n" + logsBody);

        if (tempLogFile != null) {
            final ArrayList<Uri> attachments = new ArrayList<>(2);
            attachments.add(FileProviderUtils.getUriForInternalFile(context, sendEmailIntent, tempLogFile));
            sendEmailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments);
            sendEmailIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.sendLogsText) + "\n\n");
            // No longer manually deleting the file. Let Android's cache subsystem take care of it.
        } else {
            sendEmailIntent.putExtra(Intent.EXTRA_TEXT, infoBody + "\n\n"
                    + logsBody
                    + context.getString(R.string.sendLogsText) + "\n\n");
        }

        openEmailForm(context, sendEmailIntent);
    }

    @NonNull
    private static String getMarketLine(@NonNull Context context) {
        return "Market: " + context.getString(R.string.marketName) + "\n";
    }

    @NonNull
    private static Intent getIntentForSendLogs(@NonNull Context context) {
        final Intent intent = getIntentForSendEmail(context);
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sendEmailSubject, context.getString(R.string.app_name)));
        return intent;
    }

    @NonNull
    private static Intent getIntentForSendEmail(@NonNull Context context) {
        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{context.getString(R.string.sendEmailRecipient)});
        return intent;
    }

    private static void openEmailForm(@NonNull Context context, @NonNull Intent sendEmailIntent) {
        try {
            context.startActivity(IntentLauncher.getChooserForEmailAppsOnly(context, sendEmailIntent,
                    context.getString(R.string.send_feedback)));
        } catch (ActivityNotFoundException e) {
            Logger.d("No email app found");
            Toast.makeText(context, R.string.noEmailApp, Toast.LENGTH_LONG).show();
        }
    }
}
