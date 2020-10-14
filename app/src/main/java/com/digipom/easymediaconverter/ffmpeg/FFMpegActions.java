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
package com.digipom.easymediaconverter.ffmpeg;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import android.text.format.Formatter;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.digipom.easymediaconverter.edit.Bitrates.BitrateWithValue;
import com.digipom.easymediaconverter.edit.OutputFormatType;
import com.digipom.easymediaconverter.edit.RingtoneType;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.FileUtils;
import com.digipom.easymediaconverter.utils.FilenameUtils;
import com.digipom.easymediaconverter.utils.RingtoneUtils;
import com.digipom.easymediaconverter.utils.UriUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.digipom.easymediaconverter.edit.Bitrates.BitrateType.CBR;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_AAC;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_M4A;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MP3;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_MP4;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_OGG;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.FILETYPE_WAVE;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.isFileTypeForAacAudio;
import static com.digipom.easymediaconverter.utils.FileTypeExtensions.isFileTypeForMp4Video;
import static com.digipom.easymediaconverter.utils.FilenameUtils.getCanonicalExtension;

class FFMpegActions {
    static abstract class FFMpegAction {
        private final AtomicBoolean isCancelled = new AtomicBoolean(false);
        private final Context context;
        private final FFMpegTaskWrapper ffMpegTask;

        private final LiveData<Float> progress;

        FFMpegAction(@NonNull Context context) {
            this.context = context;
            ffMpegTask = new FFMpegTaskWrapper();
            progress = Transformations.map(ffMpegTask.progressMs(), new Function<Long, Float>() {
                @Override
                public Float apply(Long progressMs) {
                    if (progressMs != null) {
                        final long durationMs = ffMpegTask.durationMs();
                        if (durationMs > 0) {
                            final float progress = (float) progressMs / (float) durationMs;
                            return Math.max(0, Math.min(1, progress));
                        }
                    }

                    return null;
                }
            });
        }

        @NonNull
        LiveData<Float> progress() {
            return progress;
        }

        long estimatedTimeRemainingMs() {
            return ffMpegTask.estimatedTimeRemainingMs();
        }

        @WorkerThread
        abstract void execute() throws IOException, InterruptedException, JSONException;

        @WorkerThread
        @NonNull
        abstract Uri[] getTargets();

        @WorkerThread
        abstract void deleteTargets();

        // Note: This can't be run on the same executor that's actually executing FFMPEG stuff, as
        // that executor will be blocked while FFMPEG stuff is running.
        @MainThread
        void requestCancellation() {
            Logger.d("Requesting to cancel action " + this);
            isCancelled.set(true);
            ffMpegTask.requestCancellation();
        }

        // Local helpers
        @WorkerThread
        @NonNull
        String doFFMpegTask(@NonNull List<String> commands) throws InterruptedException {
            return doFFMpegTask(commands, true);
        }

        // Local helpers
        @WorkerThread
        @NonNull
        String doFFMpegTask(@NonNull List<String> commands, boolean throwOnFailure) throws InterruptedException {
            checkCancelState();
            return ffMpegTask.runTask(commands, throwOnFailure);
        }

        void checkCancelState() throws RequestCancelledException {
            if (isCancelled.get()) {
                throw new RequestCancelledException("Request is cancelled");
            }
        }

        @NonNull
        File setupCacheDir() {
            final File cacheDir = chooseCacheDir(context);
            if (!cacheDir.mkdirs()) {
                Logger.d("Created cache dir " + cacheDir);
            }
            return cacheDir;
        }

        void copyInputToTemp(@NonNull Uri inputUri, @NonNull File output) throws IOException {
            Logger.d("Copying " + inputUri + " to " + output);
            // We have to copy the input so that FFMPEG can process it.
            try (InputStream is = new BufferedInputStream(
                    Objects.requireNonNull(context.getContentResolver().openInputStream(inputUri)))) {
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(output))) {
                    copyStreams(is, os);
                }
            }
        }

        @NonNull
        Uri copyTempToOutputAndUpdateExtensionIfNecessary(@NonNull File tempOutputFile,
                                                          @NonNull Uri targetUri,
                                                          @NonNull String expectedExtension) throws IOException {
            Logger.v("Copying " + tempOutputFile + " to " + targetUri);
            try (InputStream is = new BufferedInputStream(new FileInputStream(tempOutputFile))) {
                try (OutputStream os = new BufferedOutputStream(Objects.requireNonNull(context.getContentResolver().openOutputStream(targetUri)))) {
                    copyStreams(is, os);
                }
            }
            try {
                return UriUtils.ensureResourceHasExtension(context, targetUri, expectedExtension);
            } catch (Exception e) {
                Logger.w("Couldn't update extension for " + targetUri + " to " + expectedExtension);
                // I noticed a bug on the Pixel 3 where when dealing with an overwritten document
                // from ACTION_CREATE_DOCUMENT on the external storage provider, calling
                // DocumentsContract.renameDocument would throw a FileNotFoundException, but
                // actually, the rename DID happen. Almost seems like it was executed twice or
                // something. Also, we don't have permission to access the renamed file.
                return targetUri;
            }
        }

        @SuppressWarnings({"SameParameterValue"})
        void copyAssetToFile(@NonNull String assetPath, @NonNull File output) throws IOException {
            Logger.d("Copying " + assetPath + " to " + output);
            try (InputStream is = new BufferedInputStream(context.getAssets().open(assetPath))) {
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(output))) {
                    copyStreams(is, os);
                }
            }
        }

        void copyUriToUri(@NonNull Uri input, @NonNull Uri output) throws IOException {
            Logger.d("Copying " + input + " to " + output);
            try (InputStream is = new BufferedInputStream(Objects.requireNonNull(context.getContentResolver().openInputStream(input)))) {
                try (OutputStream os = new BufferedOutputStream(Objects.requireNonNull(context.getContentResolver().openOutputStream(output)))) {
                    copyStreams(is, os);
                }
            }
        }

        void deleteDocument(@NonNull Uri uri) {
            Logger.v("Deleting target uri " + uri);
            UriUtils.deleteResource(context, uri);
        }

        private static void copyStreams(@NonNull InputStream is, @NonNull OutputStream os) throws IOException {
            final byte[] buffer = new byte[8192];
            int len;
            long totalWritten = 0;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
                totalWritten += len;
            }
            Logger.d("Wrote " + totalWritten + " bytes");
        }
    }

    static abstract class ActionWithSingleInput extends ActionWithTarget {
        @NonNull
        final Uri inputUri;
        @NonNull
        final String inputFileName;

        ActionWithSingleInput(@NonNull Context context,
                              @NonNull Uri inputUri, @NonNull String inputFileName,
                              @NonNull Uri targetUri, @NonNull String targetFileName) {
            super(context, targetUri, targetFileName);
            this.inputUri = inputUri;
            this.inputFileName = inputFileName;
        }

        @NonNull
        @Override
        public String toString() {
            return "ActionWithSingleInput{" +
                    "inputUri=" + inputUri +
                    ", inputFileName='" + inputFileName + '\'' +
                    "} " + super.toString();
        }
    }

    static abstract class ActionWithTarget extends FFMpegAction {
        @NonNull
        Uri targetUri;
        @NonNull
        final String targetFileName;

        ActionWithTarget(@NonNull Context context, @NonNull Uri targetUri, @NonNull String targetFileName) {
            super(context);
            this.targetUri = targetUri;
            this.targetFileName = targetFileName;
        }

        @NonNull
        @Override
        Uri[] getTargets() {
            return new Uri[]{targetUri};
        }

        @Override
        void deleteTargets() {
            deleteDocument(targetUri);
        }

        void doStandardFFMpegTaskAndUpdateTargetUri(@NonNull Uri inputUri, @NonNull String inputFileName,
                                                    @NonNull List<String> taskCommands) throws IOException, InterruptedException {
            checkCancelState();

            // The setup is always the same. First, we process the input:
            final List<String> commands = new ArrayList<>();
            final File cacheDir = setupCacheDir();
            final File tempInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);

            addInputCommands(tempInput, commands);
            commands.addAll(taskCommands);
            addOutputCommands(tempOutput, commands);

            copyInputToTemp(inputUri, tempInput);

            doFFMpegTask(commands);
            checkCancelState();
            targetUri = copyTempToOutputAndUpdateExtensionIfNecessary(tempOutput, targetUri, getCanonicalExtension(targetFileName));
        }

        void copyTempToOutputAndUpdateTargetUri(@NonNull File tempOutputFile) throws IOException {
            targetUri = copyTempToOutputAndUpdateExtensionIfNecessary(tempOutputFile, targetUri, getCanonicalExtension(targetFileName));
        }

        @NonNull
        @Override
        public String toString() {
            return "ActionWithTarget{" +
                    "targetUri=" + targetUri +
                    ", targetFileName='" + targetFileName + '\'' +
                    "} " + super.toString();
        }
    }

    static class ConversionAction extends ActionWithSingleInput {
        @NonNull
        final OutputFormatType outputFormatType;
        @Nullable
        final BitrateWithValue optionalSelectedBitrate;

        ConversionAction(@NonNull Context context,
                         @NonNull Uri inputUri, @NonNull String inputFileName,
                         @NonNull Uri targetUri, @NonNull String targetFileName,
                         @NonNull OutputFormatType outputFormatType,
                         @Nullable BitrateWithValue selectedBitrate) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.outputFormatType = outputFormatType;
            this.optionalSelectedBitrate = selectedBitrate;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final List<String> taskCommands = new ArrayList<>();
            final String inputExtension = getCanonicalExtension(inputFileName);
            final boolean sourceIsAac = !outputFormatType.isVideoOutputType() && isFileTypeForAacAudio(inputExtension);

            if (!outputFormatType.isVideoOutputType()) {
                // Strip any input video.
                taskCommands.add("-vn");
            }

            // It appears that FFMPEG will automatically select the container type based on the
            // output file extension.
            switch (outputFormatType) {
                // Audio formats
                case MP3:
                    if (optionalSelectedBitrate != null) {
                        switch (optionalSelectedBitrate.type) {
                            case ABR:
                                taskCommands.add("-abr");
                                taskCommands.add("1");
                            case CBR:
                                taskCommands.add("-b:a");
                                taskCommands.add(optionalSelectedBitrate.value + "k");
                                break;
                            case VBR:
                                taskCommands.add("-qscale:a");
                                taskCommands.add(String.valueOf(optionalSelectedBitrate.value));
                        }
                    }
                    break;
                case M4A:
                case AAC:
                    if (optionalSelectedBitrate != null
                            && optionalSelectedBitrate.type == CBR) {
                        taskCommands.add("-b:a");
                        taskCommands.add(optionalSelectedBitrate.value + "k");
                    } else if (sourceIsAac) {
                        Logger.d("Copying audio streams for AAC");
                        // Just copy over the stream.
                        taskCommands.add("-codec:a");
                        taskCommands.add("copy");
                    }
                    break;
                case WAVE_PCM:
                    taskCommands.add("-codec:a");
                    taskCommands.add("pcm_s16le");
                    // Defaults for the rest
                    break;
                // For all of the other audio formats, we just use defaults.

                // Video formats
                case MKV:
                    // For all of the other formats, just do stream copy.
                    taskCommands.add("-c");
                    taskCommands.add("copy");
                    break;

                case MOV:
                    if (inputExtension.equals(FILETYPE_MP4)) {
                        // Stream-copy
                        taskCommands.add("-c");
                        taskCommands.add("copy");
                    }
                    break;
            }

            doStandardFFMpegTaskAndUpdateTargetUri(inputUri, inputFileName, taskCommands);
        }

        @NonNull
        @Override
        public String toString() {
            return "ConversionAction{" +
                    "outputFormatType=" + outputFormatType +
                    "} " + super.toString();
        }
    }

    static class MakeVideoAction extends ActionWithSingleInput {
        @Nullable
        final Uri customCoverImageUri;
        @Nullable
        final String customCoverImageFileName;

        MakeVideoAction(@NonNull Context context,
                        @NonNull Uri inputUri, @NonNull String inputFileName,
                        @NonNull Uri targetUri, @NonNull String targetFileName,
                        @Nullable Uri customCoverImageUri, @Nullable String customCoverImageFileName) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.customCoverImageUri = customCoverImageUri;
            this.customCoverImageFileName = customCoverImageFileName;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final String extension = getCanonicalExtension(inputFileName);
            final File cacheDir = setupCacheDir();
            final List<String> commands = new ArrayList<>();

            final File tempImageInput = createTempFileWithName(cacheDir, "easy-audio-converter.png");
            @Nullable final File tempCustomCoverInput;

            if (customCoverImageUri != null) {
                if (customCoverImageFileName != null) {
                    tempCustomCoverInput = createTempFileForInput(cacheDir, customCoverImageFileName);
                } else {
                    tempCustomCoverInput = createTempFileForInput(cacheDir, "custom-cover-image");
                }
            } else {
                tempCustomCoverInput = null;
            }

            final File tempAudioInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);

            commands.add("-loop");
            commands.add("1");
            addInputCommands(tempImageInput, commands);
            if (customCoverImageUri != null) {
                commands.add("-loop");
                commands.add("1");
                addInputCommands(tempCustomCoverInput, commands);
            }
            addInputCommands(tempAudioInput, commands);

            if (customCoverImageUri != null) {
                commands.add("-filter_complex");
                commands.add("[1:v]scale='min(1920,iw)':'min(1080,ih)':force_original_aspect_ratio=decrease,pad='min(1920,iw)':'min(1080,ih)':(ow-iw)/2:(oh-ih)/2[video-in];\n"
                        + "[0:v]colorchannelmixer=aa=0.7[logo-in];\n"
                        + "[logo-in][video-in]scale2ref=w=oh*mdar:h=ih/4[logo-out][video-out];\n"
                        + "[video-out][logo-out]overlay=x=main_w-overlay_w-10:y=main_h-overlay_h-10[final-video-out]");
                commands.add("-map");
                commands.add("[final-video-out]:v");

                commands.add("-map");
                commands.add("2:a");
            } else {
                // Although this command shouldn't be used with video input, if it is, ensure that our
                // video is used.
                commands.add("-map");
                commands.add("0:v");

                commands.add("-map");
                commands.add("1:a");
            }

            commands.add("-acodec");

            if (isFileTypeForAacAudio(extension)) {
                commands.add("copy");
            } else {
                // If the input is not AAC, then we should convert the audio to AAC first.
                commands.add(FILETYPE_AAC);
            }

            commands.add("-vcodec");
            commands.add("libx264");
            commands.add("-tune");
            commands.add("stillimage");
            commands.add("-pix_fmt");
            commands.add("yuv420p");
            commands.add("-shortest");

            addOutputCommands(tempOutput, commands);

            copyAssetToFile("easy-audio-converter.png", tempImageInput);

            if (customCoverImageUri != null) {
                copyInputToTemp(customCoverImageUri, tempCustomCoverInput);
            }

            copyInputToTemp(inputUri, tempAudioInput);
            doFFMpegTask(commands);
            copyTempToOutputAndUpdateTargetUri(tempOutput);
        }

        @NonNull
        @Override
        public String toString() {
            return "MakeVideoAction{} " + super.toString();
        }
    }

    static class ExtractAudioAction extends ActionWithSingleInput {
        @NonNull
        final OutputFormatType outputFormatType;

        ExtractAudioAction(@NonNull Context context,
                           @NonNull Uri inputUri, @NonNull String inputFileName,
                           @NonNull Uri targetUri, @NonNull String targetFileName,
                           @NonNull OutputFormatType outputFormatType) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.outputFormatType = outputFormatType;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final List<String> taskCommands = new ArrayList<>();
            final String inputExtension = getCanonicalExtension(inputFileName);
            final boolean sourceIsMP4 = outputFormatType.isVideoOutputType() && isFileTypeForMp4Video(inputExtension);

            // Strip any input video.
            taskCommands.add("-vn");

            // It appears that FFMPEG will automatically select the container type based on the
            // output file extension.
            switch (outputFormatType) {
                // Audio formats
                case M4A:
                case AAC:
                    if (sourceIsMP4) {
                        Logger.d("Copying audio streams for AAC");
                        // Just copy over the stream.
                        taskCommands.add("-codec:a");
                        taskCommands.add("copy");
                    }
                    break;
                case WAVE_PCM:
                    taskCommands.add("-codec:a");
                    taskCommands.add("pcm_s16le");
                    // Defaults for the rest
                    break;
                // For all of the other audio formats, we just use defaults.
            }

            doStandardFFMpegTaskAndUpdateTargetUri(inputUri, inputFileName, taskCommands);
        }

        @NonNull
        @Override
        public String toString() {
            return "ExtractAudioAction{" +
                    "outputFormatType=" + outputFormatType +
                    "} " + super.toString();
        }
    }

    static class TrimAction extends ActionWithSingleInput {
        final long trimBeforeMs;
        final long trimAfterMs;

        TrimAction(@NonNull Context context,
                   @NonNull Uri inputUri, @NonNull String inputFileName,
                   @NonNull Uri targetUri, @NonNull String targetFileName,
                   long trimBeforeMs, long trimAfterMs) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.trimBeforeMs = trimBeforeMs;
            this.trimAfterMs = trimAfterMs;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final List<String> commands = new ArrayList<>();

            final String inputExtension = getCanonicalExtension(inputFileName);
            final boolean trimWithoutReencode = shouldBeAbleToTrimWithoutReencode(inputExtension);

            if (trimWithoutReencode) {
                // Copy audio data
                commands.add("-codec:a");
                commands.add("copy");

                // Copy video data
                commands.add("-codec:v");
                commands.add("copy");
            }

            // Start time
            commands.add("-ss");
            commands.add(convertMsToFFMpegTime(trimBeforeMs));
            // End time (via copy duration)
            final long copyDurationMs = trimAfterMs - trimBeforeMs + 1;
            commands.add("-t");
            commands.add(convertMsToFFMpegTime(copyDurationMs));

            doStandardFFMpegTaskAndUpdateTargetUri(inputUri, inputFileName, commands);
        }

        @NonNull
        @Override
        public String toString() {
            return "TrimAction{" +
                    "trimBeforeMs=" + trimBeforeMs +
                    ", trimAfterMs=" + trimAfterMs +
                    "} " + super.toString();
        }
    }

    private static boolean shouldBeAbleToTrimWithoutReencode(@NonNull String fileType) {
        return fileType.equals(FILETYPE_MP3) || fileType.equals(FILETYPE_MP4) || fileType.equals(FILETYPE_M4A)
                || fileType.equals(FILETYPE_AAC) || fileType.equals(FILETYPE_OGG) || fileType.equals(FILETYPE_WAVE);
        // TODO haven't tested OPUS
        // TODO more video types?
    }

    static class CutAction extends ActionWithSingleInput {
        final long cutStartMs;
        final long cutEndMs;

        CutAction(@NonNull Context context,
                  @NonNull Uri inputUri, @NonNull String inputFileName,
                  @NonNull Uri targetUri, @NonNull String targetFileName,
                  long cutStartMs, long cutEndMs) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.cutStartMs = cutStartMs;
            this.cutEndMs = cutEndMs;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final File cacheDir = setupCacheDir();

            if (shouldBeAbleToCutWithoutReencode(getCanonicalExtension(inputFileName))) {
                final File tempInput = createTempFileForInput(cacheDir, inputFileName);
                final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);
                copyInputToTemp(inputUri, tempInput);

                Logger.d("Will cut by using the concatenate demuxer (no re-encoding)");
                final File listingFile = new File(cacheDir, "listing.txt");
                try (BufferedWriter listingWriter = new BufferedWriter(new FileWriter(listingFile))) {
                    final String extension = FilenameUtils.getCanonicalExtension(inputFileName);
                    Logger.d("Cutting: extracting first part");
                    copySectionToTemporaryFile(cacheDir, 0, cutStartMs, tempInput, "temp1." + extension, listingWriter);

                    Logger.d("Cutting: extracting second part");
                    copySectionToTemporaryFile(cacheDir, cutEndMs, Integer.MAX_VALUE, tempInput, "temp2." + extension, listingWriter);
                }

                Logger.d("Cutting: Concatenating");
                executeConcat(listingFile, tempOutput);
                copyTempToOutputAndUpdateTargetUri(tempOutput);
            } else {
                Logger.d("Will cut by using the concatenate filter");

                // First, identify what streams the input has
                final File tempInput = createTempFileForInput(cacheDir, inputFileName);
                final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);
                copyInputToTemp(inputUri, tempInput);

                final StreamInfo streamInfo = StreamInfo.evaluate(this, tempInput);
                final boolean containsAudio = streamInfo.containsAudio;
                final boolean containsVideo = streamInfo.containsVideo;

                final StringBuilder filter = new StringBuilder();
                if (containsVideo) {
                    filter.append("[0:v]trim=end=").append(convertMsToFFMpegSeconds(cutStartMs)).append(",setpts=N/FRAME_RATE/TB[v0];\n");
                }
                if (containsAudio) {
                    filter.append("[0:a]atrim=end=").append(convertMsToFFMpegSeconds(cutStartMs)).append(",asetpts=N/SR/TB[a0];\n");
                }
                if (containsVideo) {
                    filter.append("[0:v]trim=start=").append(convertMsToFFMpegSeconds(cutEndMs)).append(",setpts=N/FRAME_RATE/TB[v1];\n");
                }
                if (containsAudio) {
                    filter.append("[0:a]atrim=start=").append(convertMsToFFMpegSeconds(cutEndMs)).append(",asetpts=N/SR/TB[a1];\n");
                }
                if (containsVideo) {
                    filter.append("[v0]");
                }
                if (containsAudio) {
                    filter.append("[a0]");
                }
                if (containsVideo) {
                    filter.append("[v1]");
                }
                if (containsAudio) {
                    filter.append("[a1]");
                }

                filter.append("concat=n=2:v=");
                filter.append(containsVideo ? "1" : "0");
                filter.append(":a=");
                filter.append(containsAudio ? "1" : "0");

                if (containsVideo) {
                    filter.append("[v]");
                }
                if (containsAudio) {
                    filter.append("[a]");
                }

                final List<String> commands = new ArrayList<>();
                addInputCommands(tempInput, commands);
                commands.add("-filter_complex");
                commands.add(filter.toString());

                if (containsVideo) {
                    commands.add("-map");
                    commands.add("[v]");
                }
                if (containsAudio) {
                    commands.add("-map");
                    commands.add("[a]");
                }

                addOutputCommands(tempOutput, commands);
                doFFMpegTask(commands);
                copyTempToOutputAndUpdateTargetUri(tempOutput);
            }
        }

        private void copySectionToTemporaryFile(@NonNull File cacheDir, long startTime, long endTime,
                                                @NonNull File input, @NonNull String outputName,
                                                @NonNull BufferedWriter listingWriter) throws IOException, InterruptedException {
            final List<String> commands = new ArrayList<>();
            addInputCommands(input, commands);
            // Copy video data up until the cut begin point.
            commands.add("-codec:v");
            commands.add("copy");
            // Copy audio data up until the cut begin point.
            commands.add("-codec:a");
            commands.add("copy");
            // Start time
            commands.add("-ss");
            commands.add(convertMsToFFMpegTime(startTime));
            // End time (end of the file)
            commands.add("-t");
            commands.add(convertMsToFFMpegTime(endTime));
            commands.add(cacheDir.getAbsolutePath() + "/" + outputName);
            addLineToListing(listingWriter, outputName);
            doFFMpegTask(commands);
        }

        private void executeConcat(@NonNull File listingFile, @NonNull File tempOutput) throws InterruptedException {
            final List<String> commands = new ArrayList<>();
            commands.add("-y");
            commands.add("-f");
            commands.add("concat");
            commands.add("-safe");
            commands.add("0");

            addInputCommands(listingFile, commands);
            // Concatenate by copying data
            commands.add("-c");
            commands.add("copy");
            addOutputCommands(tempOutput, commands);
            doFFMpegTask(commands);
        }

        @NonNull
        @Override
        public String toString() {
            return "CutAction{" +
                    "cutStartMs=" + cutStartMs +
                    ", cutEndMs=" + cutEndMs +
                    "} " + super.toString();
        }
    }

    private static class StreamInfo {
        final boolean containsAudio;
        final boolean containsVideo;

        static StreamInfo evaluate(@NonNull FFMpegAction action, @NonNull File tempInput) throws InterruptedException {
            Logger.d("Looking up stream info for " + tempInput + ". We're not outputting anything at the moment.");
            final List<String> commands = new ArrayList<>();
            addInputCommands(tempInput, commands);
            final String output = action.doFFMpegTask(commands, false);
            boolean containsAudio = output.contains(" Audio:");
            boolean containsVideo = output.contains(" Video:");
            return new StreamInfo(containsAudio, containsVideo);
        }

        private StreamInfo(boolean containsAudio, boolean containsVideo) {
            this.containsAudio = containsAudio;
            this.containsVideo = containsVideo;
        }
    }

    private static boolean shouldBeAbleToCutWithoutReencode(@NonNull String fileType) {
        return fileType.equals(FILETYPE_MP3) || fileType.equals(FILETYPE_MP4) || fileType.equals(FILETYPE_M4A)
                || fileType.equals(FILETYPE_AAC) || fileType.equals(FILETYPE_OGG);
        // TODO haven't tested OPUS
        // Note: It doesn't hurt to re-encode FLAC and WAVE, and by doing so we avoid temporary files.
        // TODO video types?
    }

    static class AdjustSpeedAction extends ActionWithSingleInput {
        private final float relativeSpeed;

        AdjustSpeedAction(@NonNull Context context,
                          @NonNull Uri inputUri, @NonNull String inputFileName,
                          @NonNull Uri targetUri, @NonNull String targetFileName,
                          float relativeSpeed) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.relativeSpeed = relativeSpeed;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final File cacheDir = setupCacheDir();

            // First, identify what streams the input has
            final File tempInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);
            copyInputToTemp(inputUri, tempInput);

            final StreamInfo streamInfo = StreamInfo.evaluate(this, tempInput);
            final boolean containsAudio = streamInfo.containsAudio;
            final boolean containsVideo = streamInfo.containsVideo;
            final float inverseSpeed = 1 / relativeSpeed;

            final StringBuilder filter = new StringBuilder();
            if (containsVideo) {
                filter.append("[0:v]setpts=").append(inverseSpeed).append("*PTS,minterpolate='mi_mode=blend'[v]");
                if (containsAudio) {
                    filter.append(';');
                }
            }
            if (containsAudio) {
                filter.append("[0:a]atempo=").append(relativeSpeed).append("[a]");
            }

            final List<String> commands = new ArrayList<>();
            addInputCommands(tempInput, commands);
            commands.add("-filter_complex");
            commands.add(filter.toString());

            if (containsVideo) {
                commands.add("-map");
                commands.add("[v]");
            }
            if (containsAudio) {
                commands.add("-map");
                commands.add("[a]");
            }

            addOutputCommands(tempOutput, commands);
            doFFMpegTask(commands);
            copyTempToOutputAndUpdateTargetUri(tempOutput);
        }

        @NonNull
        @Override
        public String toString() {
            return "AdjustSpeedAction{" +
                    "relativeSpeed=" + relativeSpeed +
                    "} " + super.toString();
        }
    }

    static class AdjustVolumeAction extends ActionWithSingleInput {
        private final float db;

        AdjustVolumeAction(@NonNull Context context,
                           @NonNull Uri inputUri, @NonNull String inputFileName,
                           @NonNull Uri targetUri, @NonNull String targetFileName, float db) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.db = db;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final List<String> commands = new ArrayList<>();
            commands.add("-af");
            commands.add("volume=" + db + "dB");
            doStandardFFMpegTaskAndUpdateTargetUri(inputUri, inputFileName, commands);
        }

        @NonNull
        @Override
        public String toString() {
            return "AdjustVolumeAction{" +
                    "db=" + db +
                    "} " + super.toString();
        }
    }

    static class AddSilenceAction extends ActionWithSingleInput {
        private final long silenceInsertionPointMs;
        private final long silenceDurationMs;

        // NOTE: As a possible enhancement, this could work like trim or cut, but right now it
        // re-encodes everything.
        AddSilenceAction(@NonNull Context context,
                         @NonNull Uri inputUri, @NonNull String inputFileName,
                         @NonNull Uri targetUri, @NonNull String targetFileName,
                         long silenceInsertionPointMs, long silenceDurationMs) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.silenceInsertionPointMs = silenceInsertionPointMs;
            this.silenceDurationMs = silenceDurationMs;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final File cacheDir = setupCacheDir();
            final List<String> commands = new ArrayList<>();

            final File tempInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);

            // Example:
            // ffmpeg -i file1.wav -i file2.wav -f lavfi -t 5 -i anullsrc
            //       -filter_complex "[0][2][1]concat=n=3:v=0:a=1" file3.wav
            // https://stackoverflow.com/questions/38611059/how-to-add-additional-5-seconds-duration-time-to-wav-file-using-ffmpeg-in-c-shar

            // Specify a null input for the silence
            commands.add("-f");
            commands.add("lavfi");
            commands.add("-t");
            commands.add(convertMsToFFMpegSeconds(silenceDurationMs));
            commands.add("-i");
            commands.add("anullsrc");
            // Add the input
            addInputCommands(tempInput, commands);
            // Chain the silence using a complex filter
            commands.add("-filter_complex");
            commands.add(
                    // Portion before silence
                    "[1:a]atrim=end=" + convertMsToFFMpegSeconds(silenceInsertionPointMs) + ",asetpts=N/SR/TB[a0];"
                            // Portion after silence
                            + "[1:a]atrim=start=" + convertMsToFFMpegSeconds(silenceInsertionPointMs) + ",asetpts=N/SR/TB[a1];"
                            + "[a0][0][a1]concat=n=3:v=0:a=1"
            );
            addOutputCommands(tempOutput, commands);
            copyInputToTemp(inputUri, tempInput);
            doFFMpegTask(commands);
            copyTempToOutputAndUpdateTargetUri(tempOutput);
        }

        @NonNull
        @Override
        public String toString() {
            return "AddSilenceAction{" +
                    "silenceInsertionPointMs=" + silenceInsertionPointMs +
                    ", silenceDurationMs=" + silenceDurationMs +
                    "} " + super.toString();
        }
    }

    // TODO Normalization seems to fail for some files (i.e. scanning returns -inf or +inf for some
    // values. Should probably return an appropriate failure?
    static class NormalizeAction extends ActionWithSingleInput {
        NormalizeAction(@NonNull Context context,
                        @NonNull Uri inputUri, @NonNull String inputFileName,
                        @NonNull Uri targetUri, @NonNull String targetFileName) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
        }

        @Override
        void execute() throws IOException, InterruptedException, JSONException {
            final File cacheDir = setupCacheDir();
            final List<String> commands = new ArrayList<>();

            final File tempInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);

            // First pass
            addInputCommands(tempInput, commands);
            commands.add("-af");
            commands.add("loudnorm=print_format=json");
            commands.add("-f");
            commands.add("null");
            commands.add("/dev/null");

            copyInputToTemp(inputUri, tempInput);
            final String output = doFFMpegTask(commands);
            final int loudnormIndex = output.lastIndexOf("Parsed_loudnorm");
            if (loudnormIndex == -1) {
                throw new RuntimeException("Parsed_loudnorm block not found.");
            }
            final int jsonBlockBegin = output.indexOf('{', loudnormIndex);
            if (jsonBlockBegin == -1) {
                throw new RuntimeException("Parsed_loudnorm JSON block not found.");
            }
            final String jsonBlock = output.substring(jsonBlockBegin);
            final JSONObject json = new JSONObject(jsonBlock);

            final String measuredI = (String) json.get("input_i");
            final String measuredTp = (String) json.get("input_tp");
            final String measuredLra = (String) json.get("input_lra");
            final String measuredThresh = (String) json.get("input_thresh");
            final String targetOffset = (String) json.get("target_offset");

            final String[] tokens = output.split(" ");
            int sampleRateHzIndex = sequentialSearch(tokens, "Hz,");
            if (sampleRateHzIndex == -1) {
                throw new RuntimeException("Couldn't find input sample rate in Hz");
            }
            final String inputSampleRate = tokens[sampleRateHzIndex - 1];

            // Second pass
            commands.clear();
            addInputCommands(tempInput, commands);
            commands.add("-af");
            commands.add("loudnorm=I=-16:TP=-1.5:LRA=11:measured_I=" + measuredI + ":measured_TP="
                    + measuredTp + ":measured_LRA=" + measuredLra + ":measured_thresh=" + measuredThresh
                    + ":offset=" + targetOffset + ":linear=true:print_format=summary");
            commands.add("-ar");
            commands.add(inputSampleRate);
            addOutputCommands(tempOutput, commands);
            doFFMpegTask(commands);
            copyTempToOutputAndUpdateTargetUri(tempOutput);
        }

        @NonNull
        @Override
        public String toString() {
            return "NormalizeAction{} " + super.toString();
        }
    }

    static class SplitAction extends ActionWithSingleInput {
        @NonNull
        Uri secondTargetUri;
        @NonNull
        final String secondTargetFileName;
        final long splitAtMs;

        SplitAction(@NonNull Context context,
                    @NonNull Uri inputUri, @NonNull String inputFileName,
                    @NonNull Uri targetUri, @NonNull String targetFileName,
                    @NonNull Uri secondTargetUri, @NonNull String secondTargetFileName,
                    long splitAtMs) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.secondTargetUri = secondTargetUri;
            this.secondTargetFileName = secondTargetFileName;
            this.splitAtMs = splitAtMs;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final File cacheDir = setupCacheDir();
            final File tempInput = createTempFileForInput(cacheDir, inputFileName);
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);

            copyInputToTemp(inputUri, tempInput);

            Logger.d("Splitting: extracting first part");
            executeFirstSplit(tempInput, tempOutput);
            copyTempToOutputAndUpdateTargetUri(tempOutput);

            Logger.d("Splitting: extracting second part");
            executeSecondSplit(tempInput, tempOutput);
            secondTargetUri = copyTempToOutputAndUpdateExtensionIfNecessary(tempOutput, secondTargetUri, getCanonicalExtension(targetFileName));
        }

        private void executeFirstSplit(@NonNull File tempInput, @NonNull File tempOutput) throws InterruptedException {
            final List<String> commands = new ArrayList<>();
            addInputCommands(tempInput, commands);
            // Pretty much the same as a trim command.
            // Copy data
            commands.add("-codec");
            commands.add("copy");
            // Start time
            commands.add("-ss");
            commands.add(convertMsToFFMpegTime(0));
            // End time
            commands.add("-t");
            commands.add(convertMsToFFMpegTime(splitAtMs));

            addOutputCommands(tempOutput, commands);
            doFFMpegTask(commands);
        }

        private void executeSecondSplit(@NonNull File tempInput, @NonNull File tempOutput) throws InterruptedException {
            final List<String> commands = new ArrayList<>();
            addInputCommands(tempInput, commands);
            // Pretty much the same as a trim command.
            // Copy data
            commands.add("-codec");
            commands.add("copy");
            // Start time
            commands.add("-ss");
            commands.add(convertMsToFFMpegTime(splitAtMs));
            // End time
            commands.add("-t");
            commands.add(convertMsToFFMpegTime(Integer.MAX_VALUE));

            addOutputCommands(tempOutput, commands);
            doFFMpegTask(commands);
        }

        @NonNull
        @Override
        Uri[] getTargets() {
            final List<Uri> targets = new ArrayList<>(Arrays.asList(super.getTargets()));
            targets.add(secondTargetUri);
            return targets.toArray(new Uri[0]);
        }

        @Override
        void deleteTargets() {
            super.deleteTargets();
            deleteDocument(secondTargetUri);
        }

        @NonNull
        @Override
        public String toString() {
            return "SplitAction{" +
                    "secondTargetUri=" + secondTargetUri +
                    ", secondTargetFileName='" + secondTargetFileName + '\'' +
                    ", splitAtMs=" + splitAtMs +
                    "} " + super.toString();
        }
    }

    // TODO combine takes on the characteristics of the first stream rather than the best of each stream
    static class CombineAction extends ActionWithTarget {
        @NonNull
        private final MediaItem[] itemsToCombine;

        CombineAction(@NonNull Context context,
                      @NonNull MediaItem[] itemsToCombine,
                      @NonNull Uri targetUri,
                      @NonNull String targetFileName) {
            super(context, targetUri, targetFileName);
            this.itemsToCombine = itemsToCombine;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            final File cacheDir = setupCacheDir();
            final File tempOutput = createTempFileForOutput(cacheDir, targetFileName);
            final List<File> tempInputs = new ArrayList<>();

            int count = 1;
            for (MediaItem item : itemsToCombine) {
                // Don't use the input filename, to avoid having to worry about escaping special
                // chars.
                final File tempInput = createTempFileWithName(cacheDir, "input" + count);
                copyInputToTemp(item.getUri(), tempInput);
                tempInputs.add(tempInput);
            }

//            if (tryCombineWithDemuxerConcat(itemsToCombine)) {
//                try {
//                    combineWithDemuxerConcat(cacheDir, tempOutput, tempInputs);
//                    return;
//                } catch (FFMpegTaskWrapper.FFMpegFailedException e) {
//                    // Maybe cause the codecs mis-match
//                    Logger.w("Could not combine using concat demuxer; will try with complex filter.", e);
//                }
//            }

            combineWithComplexFilter(tempOutput, tempInputs);
        }

        // TODO we should only do this if the files have the same specifications (sample rate etc...)
        // because otherwise it will write a corrupted file.
//        private void combineWithDemuxerConcat(@NonNull File cacheDir,
//                                              @NonNull File tempOutput,
//                                              @NonNull List<File> tempInputs) throws IOException, InterruptedException {
//            Logger.d("Combining with demuxer concat");
//            // https://trac.ffmpeg.org/wiki/Concatenate
//            final File listingFile = new File(cacheDir, "listing.txt");
//            final BufferedWriter listingWriter = new BufferedWriter(new FileWriter(listingFile));
//            try {
//                for (File tempInput : tempInputs) {
//                    addLineToListing(listingWriter, tempInput.getAbsolutePath());
//                }
//            } finally {
//                listingWriter.close();
//            }
//
//            final List<String> commands = new ArrayList<>();
//            // First, try a straight concat
//            commands.add("-y");
//            commands.add("-f");
//            commands.add("concat");
//            commands.add("-safe");
//            commands.add("0");
//
//            addInputCommands(listingFile, commands);
//            // Concatenate by copying data
//            commands.add("-c");
//            commands.add("copy");
//            addOutputCommands(tempOutput, commands);
//
//            doFFMpegTask(commands);
//            copyTempToOutputAndUpdateTargetUri(tempOutput);
//        }

        private void combineWithComplexFilter(@NonNull File tempOutput,
                                              @NonNull List<File> tempInputs) throws InterruptedException, IOException {
            // Try again with a re-encoding concat
            Logger.d("Combining with filter concat");
            final List<String> commands = new ArrayList<>();
            commands.clear();
            commands.add("-y");

            for (File path : tempInputs) {
                commands.add("-i");
                commands.add(path.getAbsolutePath());
            }

            commands.add("-filter_complex");
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tempInputs.size(); ++i) {
                builder.append('[').append(i).append(":a]");
            }
            builder.append("concat=n=").append(tempInputs.size()).append(":v=0:a=1");
            commands.add(builder.toString());
            addOutputCommands(tempOutput, commands);

            doFFMpegTask(commands);
            copyTempToOutputAndUpdateTargetUri(tempOutput);
        }

        @NonNull
        @Override
        public String toString() {
            return "CombineAction{" +
                    "itemsToCombine=" + Arrays.toString(itemsToCombine) +
                    "} " + super.toString();
        }
    }

    // TODO we should only do this if the files have the same specifications (sample rate etc...)
    // because otherwise it will write a corrupted file.
//    private static boolean tryCombineWithDemuxerConcat(@NonNull MediaItem[] itemsToCombine) {
    // TODO
//        return false;
        /*
        final Set<String> extensions = new HashSet<>();
        for (MediaItem item : itemsToCombine) {
            extensions.add(getCanonicalExtension(item.displayName));
        }
        if (extensions.size() == 1) {
            // If WAVE or FLAC, we'll still re-encode, but otherwise try the concat demuxer.
            final String extension = extensions.iterator().next();
            return !extension.equals(FILETYPE_WAVE) && !extension.equals(FILETYPE_FLAC);
        }
        for (String extension : extensions) {
            if (!isFileTypeForAacAudio(extension)) {
                // If more than one extension and they're not all AAC, don't do demuxer concat.
                return false;
            }
        }
        // If all are AAC filetypes, we can still try the demuxer concat.
        return true;*/
//    }

    // TODO: Need to test for both types (those needing conversion and those that don't need conversion)
    static class SetAsRingtoneAction extends ActionWithSingleInput {
        @NonNull
        private final Context context;
        @NonNull
        final RingtoneType ringtoneType;
        final boolean convertToAac;

        SetAsRingtoneAction(@NonNull Context context,
                            @NonNull Uri inputUri, @NonNull String inputFileName,
                            @NonNull Uri targetUri, @NonNull String targetFileName,
                            @NonNull RingtoneType ringtoneType,
                            boolean convertToAac) {
            super(context, inputUri, inputFileName, targetUri, targetFileName);
            this.context = context;
            this.ringtoneType = ringtoneType;
            this.convertToAac = convertToAac;
        }

        @Override
        void execute() throws IOException, InterruptedException {
            if (convertToAac) {
                // Transcode to AAC first, then copy the data.
                final List<String> commands = new ArrayList<>();
                commands.add("-codec:a");
                commands.add(FILETYPE_AAC);
                doStandardFFMpegTaskAndUpdateTargetUri(inputUri, inputFileName, commands);
            } else {
                // Only copy the data directly. Don't need to involve FFMPEG.
                // We also don't need to worry about the user messing with the extension since we
                // control that directly with the ringtones API.
                copyUriToUri(inputUri, targetUri);
            }

            RingtoneUtils.setAsRingtone(context, targetUri, ringtoneType.toAndroidRingtoneType());
        }

        @NonNull
        @Override
        public String toString() {
            return "SetAsRingtoneAction{" +
                    "context=" + context +
                    ", ringtoneType=" + ringtoneType +
                    ", convertToAac=" + convertToAac +
                    "} " + super.toString();
        }
    }

    // Helper functions
    private static void addLineToListing(@NonNull BufferedWriter writer, @NonNull String line) throws IOException {
        writer.write("file '");
        writer.write(line);
        writer.write("'");
        writer.newLine();
    }

    @NonNull
    private static String convertMsToFFMpegSeconds(long ms) {
        // We want to format this as ss[.xxx]
        int ms_part = (int) (ms % 1000);
        long seconds = ms / 1000;
        return String.format(Locale.US, "%d.%03d", seconds, ms_part);
    }

    @NonNull
    private static String convertMsToFFMpegTime(long ms) {
        // We want to format this as hh:mm:ss[.xxx]
        int ms_part = (int) (ms % 1000);
        int seconds = (int) (ms / 1000) % 60;
        int minutes = (int) ((ms / (1000 * 60)) % 60);
        int hours = (int) ((ms / (1000 * 60 * 60)) % 24);

        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms_part);
    }

    @SuppressWarnings("SameParameterValue")
    private static int sequentialSearch(@NonNull String[] arr, @NonNull String key) {
        for (int i = 0; i < arr.length; ++i) {
            if (arr[i].equals(key)) {
                return i;
            }
        }

        return -1;
    }

    @NonNull
    private static File chooseCacheDir(@NonNull Context context) {
        // Choose whichever of the internal and external has more space.
        final File internalCacheDir = context.getCacheDir();
        final long internalFreeSpace = getFreeSpaceInBytes(internalCacheDir);
        Logger.v("Internal cache free space: " + Formatter.formatFileSize(context, internalFreeSpace));

        final File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir == null) {
            Logger.v("Selecting internal cache");
            return internalCacheDir;
        }

        final long externalFreeSpace = getFreeSpaceInBytes(externalCacheDir);
        Logger.v("External cache free space: " + Formatter.formatFileSize(context, externalFreeSpace));

        if (externalFreeSpace > internalFreeSpace) {
            Logger.v("Selecting external cache");
            return externalCacheDir;
        } else {
            Logger.v("Selecting internal cache");
            return internalCacheDir;
        }
    }

    private static long getFreeSpaceInBytes(@NonNull File path) {
        final StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return blockSize * availableBlocks;
    }

    @NonNull
    private static File createTempFileForInput(@NonNull File cacheDir, @NonNull String fileName) throws IOException {
        return File.createTempFile("input", "." + getCanonicalExtension(fileName), cacheDir);
    }

    @NonNull
    private static File createTempFileForOutput(@NonNull File cacheDir, @NonNull String fileName) throws IOException {
        return File.createTempFile("output", "." + getCanonicalExtension(fileName), cacheDir);
    }

    @NonNull
    private static File createTempFileWithName(@NonNull File cacheDir, @NonNull String fileName) throws IOException {
        // Watch out for names that are too short. Prefix has to be at least 3 characters long.
        return FileUtils.createTempFile(fileName, cacheDir);
    }

    private static void addInputCommands(@NonNull File input, @NonNull List<String> commands) {
        addInputCommands(input.getAbsolutePath(), commands);
    }

    private static void addInputCommands(@NonNull String input, @NonNull List<String> commands) {
        // Always overwrite the temp output file (that will be appended later to the commands list).
        commands.add("-y");
        commands.add("-i");
        commands.add(input);
    }

    private static void addOutputCommands(@NonNull File output, @NonNull List<String> commands) {
        commands.add(output.getAbsolutePath());
    }
}
