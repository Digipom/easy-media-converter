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
package com.digipom.easymediaconverter.player;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.digipom.easymediaconverter.utils.logger.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.MediaPlayer.MEDIA_ERROR_IO;
import static android.media.MediaPlayer.MEDIA_ERROR_MALFORMED;
import static android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED;
import static android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT;
import static android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN;
import static android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED;
import static android.media.MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING;
import static android.media.MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING;
import static android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END;
import static android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START;
import static android.media.MediaPlayer.MEDIA_INFO_METADATA_UPDATE;
import static android.media.MediaPlayer.MEDIA_INFO_NOT_SEEKABLE;
import static android.media.MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT;
import static android.media.MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT;
import static android.media.MediaPlayer.MEDIA_INFO_UNKNOWN;
import static android.media.MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE;
import static android.media.MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING;
import static android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START;
import static android.media.MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING;
import static com.digipom.easymediaconverter.config.StaticConfig.LOGCAT_LOGGING_ON;

// TODO error handling

class MediaPlayerController implements MediaPlayer.OnPreparedListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnVideoSizeChangedListener {
    private static final String TAG = MediaPlayerController.class.getName();
    private final Context context;
    private final BecomingNoisyReceiver becomingNoisyReceiver = new BecomingNoisyReceiver();
    private final AudioFocusHelper audioFocusHelper;
    private MediaPlayer mediaPlayer;

    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLooping = new MutableLiveData<>();
    private final MutableLiveData<Integer> elapsedTimeMs = new MutableLiveData<>();
    private final MutableLiveData<Integer> durationMs = new MutableLiveData<>();
    private final MutableLiveData<Float> speed = new MutableLiveData<>();
    private final MutableLiveData<Size> videoSize = new MutableLiveData<>();

    MediaPlayerController(@NonNull Context context) {
        this.context = context;
        this.audioFocusHelper = new AudioFocusHelper((AudioManager) Objects.requireNonNull(context.getSystemService(Context.AUDIO_SERVICE)));
        isPlaying.setValue(false);
        isLooping.setValue(false);
        elapsedTimeMs.setValue(0);
        durationMs.setValue(0);
        speed.setValue(1.0f);
        videoSize.setValue(null);       // No video size by default
    }

    void ensurePlayerLoaded(@NonNull final Uri uri) throws IOException {
        if (mediaPlayer == null) {
            Logger.v("Initializing media player for uri " + uri);
            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            } else {
                final AudioAttributes.Builder builder = new AudioAttributes.Builder();
                builder.setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA);
                mediaPlayer.setAudioAttributes(builder.build());
            }
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnInfoListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnSeekCompleteListener(this);
            mediaPlayer.setOnVideoSizeChangedListener(this);
            mediaPlayer.prepareAsync();
        }
    }

    // Observable data

    @NonNull
    LiveData<Integer> getObservableElaspedTimeMs() {
        return elapsedTimeMs;
    }

    @NonNull
    LiveData<Integer> getObservableDurationMs() {
        return durationMs;
    }

    @NonNull
    LiveData<Boolean> getObservableIsPlaying() {
        return isPlaying;
    }

    @NonNull
    LiveData<Boolean> getObservableIsLooping() {
        return isLooping;
    }

    @NonNull
    LiveData<Float> getObservablePlaybackSpeed() {
        return speed;
    }

    @NonNull
    LiveData<Size> getObservableVideoSize() {
        return videoSize;
    }

    // Actions

    void setVideoSurfaceForPlayback(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            Logger.v("Setting media display to holder: " + holder);
            mediaPlayer.setDisplay(holder);
            if (!mediaPlayer.isPlaying()) {
                Logger.v("Seeking to current position " + mediaPlayer.getCurrentPosition() + " to update video frame");
                // Ensure something is displayed on the surface.
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition());
            }
        }
    }

    void seekToRatio(float ratio) {
        if (mediaPlayer != null) {
            Logger.v("Seeking to ratio: " + ratio);
            final int offset = (int) (mediaPlayer.getDuration() * ratio);
            mediaPlayer.seekTo(offset);
            updateElapsedTimeMs();
        }
    }

    void seeToOffsetMs(long offsetMs) {
        if (mediaPlayer != null) {
            Logger.v("Seeking to offset: " + offsetMs + "ms");
            mediaPlayer.seekTo((int) offsetMs);
            updateElapsedTimeMs();
        }
    }

    void seekByRelativeMs(int offsetMs) {
        if (mediaPlayer != null) {
            Logger.v("Seeking by relative ms: " + offsetMs);
            final int currentPosition = mediaPlayer.getCurrentPosition();
            final int newPosition = currentPosition + offsetMs;
            mediaPlayer.seekTo(newPosition);
            // TODO why no updateElapsedTimeMs here?
        }
    }

    void toggleLoop() {
        if (mediaPlayer != null) {
            Logger.v("Toggling loop to : " + !mediaPlayer.isLooping());
            mediaPlayer.setLooping(!mediaPlayer.isLooping());
            updateIsLooping();
        }
    }

    void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                Logger.v("Pausing");
                mediaPlayer.pause();
            } else {
                Logger.v("Playing");
                mediaPlayer.start();
                updatePlayerSpeedIfPlaying(Objects.requireNonNull(speed.getValue()));
            }
            updateIsPlayingAndElapsedTime();
        }
    }

    void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Logger.v("Pausing");
            mediaPlayer.pause();
            updateIsPlayingAndElapsedTime();
        }
    }

    private void duckAudio() {
        if (mediaPlayer != null) {
            Logger.v("Ducking audio");
            mediaPlayer.setVolume(0.2f, 0.2f);
        }
    }

    private void unduckAudio() {
        if (mediaPlayer != null) {
            Logger.v("Unducking audio");
            mediaPlayer.setVolume(1f, 1f);
        }
    }

    void setSpeed(float newSpeed) {
        speed.setValue(newSpeed);
        if (mediaPlayer != null) {
            updatePlayerSpeedIfPlaying(newSpeed);
        }
    }

    void updateCurrentPlaybackPosition() {
        if (mediaPlayer != null) {
            updateElapsedTimeMs();
        }
    }

    void release() {
        if (mediaPlayer != null) {
            Logger.v("Releasing media player");
            mediaPlayer.release();
            mediaPlayer = null;

            isPlaying.setValue(false);
            isLooping.setValue(false);
            elapsedTimeMs.setValue(0);
            durationMs.setValue(0);
            speed.setValue(1f);
        }

        becomingNoisyReceiver.unregister();
        audioFocusHelper.abandonAudioFocus();
    }

    // Helpers

    private void updateIsPlayingAndElapsedTime() {
        updateElapsedTimeMs();
        isPlaying.setValue(mediaPlayer.isPlaying());

        if (mediaPlayer.isPlaying()) {
            audioFocusHelper.requestAudioFocus();
            becomingNoisyReceiver.register();
        } else {
            audioFocusHelper.abandonAudioFocus();
            becomingNoisyReceiver.unregister();
        }
    }

    private void updateIsLooping() {
        isLooping.setValue(mediaPlayer.isLooping());
    }

    private void updateElapsedTimeMs() {
        elapsedTimeMs.setValue(mediaPlayer.getCurrentPosition());
    }

    private void updatePlayerSpeedIfPlaying(float newSpeed) {
        if (mediaPlayer.isPlaying()) {
            // Setting the speed would also cause things to start playing, so only do it if
            // already playing.
            final PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.allowDefaults();
            if (newSpeed != params.getSpeed()) {
                Logger.v("Updating playback speed to " + newSpeed);
                params.setSpeed(newSpeed);

                try {
                    mediaPlayer.setPlaybackParams(params);
                } catch (Exception e) {
                    Logger.w("Couldn't set params: " + params, e);
                }
            }
        }
    }

    // Overrides for interfaces

    @Override
    public void onPrepared(MediaPlayer mp) {
        Logger.v("onPrepared: " + Arrays.toString(mp.getTrackInfo()));
        durationMs.setValue(mp.getDuration());
        updateIsPlayingAndElapsedTime();
        if (!mp.isPlaying()) {
            // Load an initial frame in case we have a video.
            Logger.v("Seeking to 0 to load initial frame for video");
            mp.seekTo(0);
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        Logger.d("onInfo: what: " + mediaPlayerInfoLogString(what) + ", extra: " + extra);
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Logger.w("onError: what: " + mediaPlayerErrorWhatLogString(what)
                + ", extra: " + mediaPlayerErrorExtraLogString(extra));
        updateIsPlayingAndElapsedTime();
        return false;
    }

    @NonNull
    private String mediaPlayerInfoLogString(int what) {
        if (what == MEDIA_INFO_UNKNOWN) {
            return stringWithCode("MEDIA_INFO_UNKNOWN", what);
        } else if (what == MEDIA_INFO_STARTED_AS_NEXT) {
            return stringWithCode("MEDIA_INFO_STARTED_AS_NEXT", what);
        } else if (what == MEDIA_INFO_VIDEO_RENDERING_START) {
            return stringWithCode("MEDIA_INFO_VIDEO_RENDERING_START", what);
        } else if (what == MEDIA_INFO_VIDEO_TRACK_LAGGING) {
            return stringWithCode("MEDIA_INFO_VIDEO_TRACK_LAGGING", what);
        } else if (what == MEDIA_INFO_BUFFERING_START) {
            return stringWithCode("MEDIA_INFO_BUFFERING_START", what);
        } else if (what == MEDIA_INFO_BUFFERING_END) {
            return stringWithCode("MEDIA_INFO_BUFFERING_END", what);
        } else if (what == MEDIA_INFO_BAD_INTERLEAVING) {
            return stringWithCode("MEDIA_INFO_BAD_INTERLEAVING", what);
        } else if (what == MEDIA_INFO_NOT_SEEKABLE) {
            return stringWithCode("MEDIA_INFO_NOT_SEEKABLE", what);
        } else if (what == MEDIA_INFO_METADATA_UPDATE) {
            return stringWithCode("MEDIA_INFO_METADATA_UPDATE", what);
        } else if (what == MEDIA_INFO_AUDIO_NOT_PLAYING) {
            return stringWithCode("MEDIA_INFO_AUDIO_NOT_PLAYING", what);
        } else if (what == MEDIA_INFO_VIDEO_NOT_PLAYING) {
            return stringWithCode("MEDIA_INFO_VIDEO_NOT_PLAYING", what);
        } else if (what == MEDIA_INFO_UNSUPPORTED_SUBTITLE) {
            return stringWithCode("MEDIA_INFO_UNSUPPORTED_SUBTITLE", what);
        } else if (what == MEDIA_INFO_SUBTITLE_TIMED_OUT) {
            return stringWithCode("MEDIA_INFO_SUBTITLE_TIMED_OUT", what);
        } else {
            return String.valueOf(what);
        }
    }

    @NonNull
    private String mediaPlayerErrorWhatLogString(int what) {
        if (what == MEDIA_ERROR_UNKNOWN) {
            return stringWithCode("MEDIA_ERROR_UNKNOWN", what);
        } else if (what == MEDIA_ERROR_SERVER_DIED) {
            return stringWithCode("MEDIA_ERROR_SERVER_DIED", what);
        } else {
            return String.valueOf(what);
        }
    }

    @NonNull
    private String mediaPlayerErrorExtraLogString(int extra) {
        if (extra == MEDIA_ERROR_IO) {
            return stringWithCode("MEDIA_ERROR_IO", extra);
        } else if (extra == MEDIA_ERROR_MALFORMED) {
            return stringWithCode("MEDIA_ERROR_MALFORMED", extra);
        } else if (extra == MEDIA_ERROR_UNSUPPORTED) {
            return stringWithCode("MEDIA_ERROR_UNSUPPORTED", extra);
        } else if (extra == MEDIA_ERROR_TIMED_OUT) {
            return stringWithCode("MEDIA_ERROR_TIMED_OUT", extra);
        } else {
            return String.valueOf(extra);
        }
    }

    @NonNull
    private String stringWithCode(@NonNull String string, int code) {
        return string + " (" + code + ')';
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Logger.v("onCompletion");
        updateIsPlayingAndElapsedTime();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        Logger.v("onSeekComplete");
        updateIsPlayingAndElapsedTime();
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        Logger.v("onVideoSizeChanged(width: " + width + ", height: " + height + ')');
        videoSize.setValue(new Size(width, height));
    }

    // Helper when the audio playback is about to become noisy.
    private class BecomingNoisyReceiver extends BroadcastReceiver {
        private final Handler mainThreadHandler = new Handler();
        private boolean isRegistered;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null
                    && intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Logger.v("Pausing for ACTION_AUDIO_BECOMING_NOISY");
                        pause();
                    }
                });
            }
        }

        void register() {
            if (!isRegistered) {
                final IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
                context.registerReceiver(this, intentFilter);
                isRegistered = true;
            }
        }

        void unregister() {
            if (isRegistered) {
                context.unregisterReceiver(this);
                isRegistered = false;
            }
        }
    }

    // Helper for managing audio focus
    private class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
        private final AudioManager audioManager;

        AudioFocusHelper(@NonNull AudioManager audioManager) {
            this.audioManager = audioManager;
        }

        private void requestAudioFocus() {
            // We don't care about the return value because playing back audio is done as per a
            // user's specific intention, so we always play.
            int result;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = audioManager.requestAudioFocus(buildAudioFocusRequest());
            } else {
                result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            if (LOGCAT_LOGGING_ON) {
                Log.v(TAG, "Requesting audio focus, result: " + audioFocusResultLogString(result));
            } else {
                // Only report if unusual
                if (result != AUDIOFOCUS_REQUEST_GRANTED) {
                    Logger.v("Requesting audio focus, result: " + audioFocusResultLogString(result));
                }
            }
        }

        @TargetApi(Build.VERSION_CODES.O)
        private AudioFocusRequest buildAudioFocusRequest() {
            return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build())
                    .setOnAudioFocusChangeListener(this)
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(false).build();
        }

        private void abandonAudioFocus() {
            int result;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = audioManager.abandonAudioFocusRequest(buildAudioFocusRequest());
            } else {
                result = audioManager.abandonAudioFocus(this);
            }

            if (LOGCAT_LOGGING_ON) {
                Log.v(TAG, "Abandoning audio focus, result: " + audioFocusResultLogString(result));
            } else {
                // Only report if unusual
                if (result != AUDIOFOCUS_REQUEST_GRANTED) {
                    Logger.v("Abandoning audio focus, result: " + audioFocusResultLogString(result));
                }
            }
        }

        @NonNull
        private String audioFocusResultLogString(int result) {
            if (result == AUDIOFOCUS_REQUEST_FAILED) {
                return "AUDIOFOCUS_REQUEST_FAILED";
            } else if (result == AUDIOFOCUS_REQUEST_GRANTED) {
                return "AUDIOFOCUS_REQUEST_GRANTED";
            } else if (result == AUDIOFOCUS_REQUEST_DELAYED) {
                return "AUDIOFOCUS_REQUEST_DELAYED";
            } else {
                return String.valueOf(result);
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                Logger.v("onAudioFocusChange(): AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                duckAudio();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                Logger.v("onAudioFocusChange(): AUDIOFOCUS_LOSS_TRANSIENT");
                pause();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                Logger.v("onAudioFocusChange(): AUDIOFOCUS_LOSS");
                pause();
                // We've lost audio focus completely, so it should be abandoned.
                abandonAudioFocus();
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                Logger.v("onAudioFocusChange(): AUDIOFOCUS_GAIN");
                unduckAudio();
                // We don't automatically resume playback if we hit AUDIOFOCUS_LOSS_TRANSIENT and play-back was paused.
            }
        }
    }
}
