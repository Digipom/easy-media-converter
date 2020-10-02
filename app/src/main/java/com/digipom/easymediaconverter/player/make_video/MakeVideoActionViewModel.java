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
package com.digipom.easymediaconverter.player.make_video;

import android.app.Application;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.InputStream;
import java.util.Objects;

import static com.digipom.easymediaconverter.player.FilenamingUtils.getAppendNamingForOutput;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MP4;
import static com.digipom.easymediaconverter.utils.FilenameUtils.appendToFilename;
import static com.digipom.easymediaconverter.utils.FilenameUtils.replaceExtension;
import static com.digipom.easymediaconverter.utils.StreamUtils.silentlyClose;

public class MakeVideoActionViewModel extends AndroidViewModel {
    private final MutableLiveData<Drawable> coverImage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isCoverImageDefault = new MutableLiveData<>();
    private Uri customCoverImageUri;
    private MediaItem mediaItem;

    public MakeVideoActionViewModel(@NonNull Application application) {
        super(application);
        setCoverImageToDefault();
    }

    @NonNull
    LiveData<Drawable> coverImage() {
        return coverImage;
    }

    @NonNull
    LiveData<Boolean> isCoverImageDefault() {
        return isCoverImageDefault;
    }

    void setMediaItem(@NonNull MediaItem mediaItem) {
        this.mediaItem = mediaItem;
    }

    void setCoverImage(@NonNull Uri imageUri) {
        try {
            setCoverImageFromStream(Objects.requireNonNull(getApplication().getContentResolver().openInputStream(imageUri)));
            isCoverImageDefault.setValue(false);
            customCoverImageUri = imageUri;
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    void setCoverImageToDefault() {
        try {
            setCoverImageFromStream(getApplication().getAssets().open("easy-audio-converter.png"));
            isCoverImageDefault.setValue(true);
            customCoverImageUri = null;
        } catch (Exception e) {
            Logger.w(e);
        }
    }

    private void setCoverImageFromStream(@NonNull InputStream is) {
        try {
            coverImage.setValue(Drawable.createFromStream(is, null));
        } finally {
            silentlyClose(is);
        }
    }

    @NonNull
    String getMimeTypeForOutputSelection() {
        return "video/mp4";
    }

    @NonNull
    String getDefaultOutputFilename() {
        final String filename = mediaItem.getFilename();
        final String targetExtension = getExtensionForSelectedOutputType();
        return replaceExtension(appendToFilename(filename,
                getAppendNamingForOutput(getApplication(), EditAction.CONVERT_TO_VIDEO)), targetExtension);
    }

    @Nullable
    Uri getCustomCoverImageUri() {
        return customCoverImageUri;
    }

    @Nullable
    String getCustomCoverImageFilename() {
        if (customCoverImageUri != null) {
            try {
                final MediaItem item = MediaItem.constructFromUri(getApplication(), customCoverImageUri);
                return item.getFilename();
            } catch (Exception e) {
                Logger.w(e);
            }
        }

        return null;
    }

    @NonNull
    private String getExtensionForSelectedOutputType() {
        return FILETYPE_MP4;
    }
}
