// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Map;

public abstract class ExoPlayerEventListener implements Player.Listener {
  static final long DURATION_UNSET_INITIALIZATION_TIMEOUT_MS = 2000;
  private boolean isInitialized = false;
  private boolean isWaitingForValidDuration = false;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Runnable initializationFallback =
      () -> {
        if (!isInitialized && isWaitingForValidDuration) {
          isWaitingForValidDuration = false;
          isInitialized = true;
          sendInitialized();
        }
      };
  protected final ExoPlayer exoPlayer;
  protected final VideoPlayerCallbacks events;

  protected enum RotationDegrees {
    ROTATE_0(0),
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270);

    private final int degrees;

    RotationDegrees(int degrees) {
      this.degrees = degrees;
    }

    public static RotationDegrees fromDegrees(int degrees) {
      for (RotationDegrees rotationDegrees : RotationDegrees.values()) {
        if (rotationDegrees.degrees == degrees) {
          return rotationDegrees;
        }
      }
      throw new IllegalArgumentException("Invalid rotation degrees specified: " + degrees);
    }

    public int getDegrees() {
      return this.degrees;
    }
  }

  public ExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @NonNull VideoPlayerCallbacks events) {
    this.exoPlayer = exoPlayer;
    this.events = events;
  }

  protected abstract void sendInitialized();

  /** Cancels pending initialization callbacks when the player is disposed. */
  public void dispose() {
    isWaitingForValidDuration = false;
    mainHandler.removeCallbacks(initializationFallback);
  }

  private boolean hasValidDuration() {
    return exoPlayer.getDuration() != C.TIME_UNSET;
  }

  private boolean shouldWaitForValidDuration() {
    return !exoPlayer.isCurrentMediaItemLive() && !exoPlayer.isCurrentMediaItemDynamic();
  }

  private void maybeSendInitialized() {
    if (isInitialized) {
      return;
    }

    if (!hasValidDuration() && shouldWaitForValidDuration()) {
      if (!isWaitingForValidDuration) {
        isWaitingForValidDuration = true;
        mainHandler.postDelayed(initializationFallback, DURATION_UNSET_INITIALIZATION_TIMEOUT_MS);
      }
      return;
    }

    isWaitingForValidDuration = false;
    isInitialized = true;
    mainHandler.removeCallbacks(initializationFallback);
    sendInitialized();
  }

  @Override
  public void onPlaybackStateChanged(final int playbackState) {
    PlatformPlaybackState platformState = PlatformPlaybackState.UNKNOWN;
    switch (playbackState) {
      case Player.STATE_BUFFERING:
        platformState = PlatformPlaybackState.BUFFERING;
        break;
      case Player.STATE_READY:
        platformState = PlatformPlaybackState.READY;
        maybeSendInitialized();
        break;
      case Player.STATE_ENDED:
        platformState = PlatformPlaybackState.ENDED;
        break;
      case Player.STATE_IDLE:
        platformState = PlatformPlaybackState.IDLE;
        break;
    }
    events.onPlaybackStateChanged(platformState);
  }

  @Override
  public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
    if (isWaitingForValidDuration && exoPlayer.getPlaybackState() == Player.STATE_READY) {
      maybeSendInitialized();
    }
  }

  @Override
  public void onPlayerError(@NonNull final PlaybackException error) {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
      // See
      // https://exoplayer.dev/live-streaming.html#behindlivewindowexception-and-error_code_behind_live_window
      exoPlayer.seekToDefaultPosition();
      exoPlayer.prepare();
    } else {
      // FORK PATCH: report errorCode / HTTP status / failing URL / active decoder instead of just
      // error.toString(). See formatPlaybackError below.
      events.onError("VideoError", formatPlaybackError(error), null);
    }
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    events.onIsPlayingStateUpdate(isPlaying);
  }

  @Override
  public void onTracksChanged(@NonNull Tracks tracks) {
    // Find the currently selected audio track and notify
    String selectedAudioTrackId = findSelectedAudioTrackId(tracks);
    events.onAudioTrackChanged(selectedAudioTrackId);

    // Find the currently selected video track and notify
    String selectedVideoTrackId = findSelectedVideoTrackId(tracks);
    events.onVideoTrackChanged(selectedVideoTrackId);
  }

  /**
   * Finds the ID of the currently selected audio track.
   *
   * @param tracks The current tracks
   * @return The track ID in format "groupIndex_trackIndex", or null if no audio track is selected
   */
  @Nullable
  private String findSelectedAudioTrackId(@NonNull Tracks tracks) {
    // Keep this ID format in sync with android_video_player.dart::_parseAndroidTrackId.
    int groupIndex = 0;
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_AUDIO && group.isSelected()) {
        // Find the selected track within this group
        for (int i = 0; i < group.length; i++) {
          if (group.isTrackSelected(i)) {
            return groupIndex + "_" + i;
          }
        }
      }
      groupIndex++;
    }
    return null;
  }

  /**
   * Finds the ID of the currently selected video track.
   *
   * @param tracks The current tracks
   * @return The track ID in format "groupIndex_trackIndex", or null if no video track is selected
   */
  @Nullable
  private String findSelectedVideoTrackId(@NonNull Tracks tracks) {
    // Keep this ID format in sync with android_video_player.dart::_parseAndroidTrackId.
    int groupIndex = 0;
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_VIDEO && group.isSelected()) {
        // Find the selected track within this group
        for (int i = 0; i < group.length; i++) {
          if (group.isTrackSelected(i)) {
            return groupIndex + "_" + i;
          }
        }
      }
      groupIndex++;
    }
    return null;
  }

  /**
   * FORK PATCH (Hana-ame/twitter-pic-flutter): turns a {@link PlaybackException} into a message
   * that carries every field needed to locate the failure.
   *
   * <p>Upstream reported {@code "Video player had error " + error} only. Media3's {@code
   * PlaybackException.toString()} is just {@code "<obfuscated class>: <one-line summary>"} — under
   * R8 that reads {@code "v.j: Source error"} — so {@code errorCode}, the HTTP status code and the
   * failing URL were all discarded before reaching Dart. A 403, a 404, a connect timeout and a
   * connection reset therefore looked identical in the app, and users could only report "some
   * videos won't play" with nothing actionable behind it.
   *
   * <p>Output shape (Dart side reads the status with {@code HTTP (\d{3})}, see {@code
   * lib/utils/video_failure.dart} — keep the two in sync):
   *
   * <pre>
   * Video player had error v.j: Source error [ERROR_CODE_IO_BAD_HTTP_STATUS, HTTP 403]
   *   uri=http://127.0.0.1:8443/media/xxx.mp4 decoder=c2.android.avc.decoder decoderMode=auto
   *   (InvalidResponseCodeException: Response code: 403)
   * </pre>
   *
   * <p>The {@code "Video player had error "} prefix and the original {@code toString()} must stay:
   * the Dart side still matches on keywords inside it ({@code format_supported=}, {@code Source
   * error}), and replacing the wording outright would silently break every existing judgement.
   *
   * @param error the failure reported by ExoPlayer.
   * @return a single-line, machine-readable message.
   */
  @NonNull
  static String formatPlaybackError(@NonNull PlaybackException error) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("Video player had error ").append(error);

    // `[<errorCodeName>, HTTP <status>]`：前段是 media3 自己的枚举名（ERROR_CODE_IO_*），
    // 后段是真正能分清 403/404 的状态码。
    sb.append(" [").append(error.getErrorCodeName());
    HttpDataSource.InvalidResponseCodeException badStatus =
        findCause(error, HttpDataSource.InvalidResponseCodeException.class);
    if (badStatus != null) {
      sb.append(", HTTP ").append(badStatus.responseCode);
    }
    sb.append(']');

    // 失败的 URL：403/404 到底是媒体本体，还是重定向后的另一跳，只有这一条能分清。
    // 没有状态码时（连接被拒/重置/超时）也尽量带上，便于区分"媒体"与"别的请求"。
    if (badStatus != null && badStatus.dataSpec != null) {
      sb.append(" uri=").append(sanitize(String.valueOf(badStatus.dataSpec.uri)));
    } else {
      HttpDataSource.HttpDataSourceException transport =
          findCause(error, HttpDataSource.HttpDataSourceException.class);
      if (transport != null && transport.dataSpec != null) {
        sb.append(" uri=").append(sanitize(String.valueOf(transport.dataSpec.uri)));
      }
    }

    // 这次到底走的软解还是硬解。软解解不动的视频会以各种面目失败，排障第一步就是排除它。
    Map<String, Object> decoder = DecoderTelemetry.getLastInfo();
    if (decoder != null) {
      sb.append(" decoder=").append(sanitize(String.valueOf(decoder.get("decoderName"))));
      sb.append(" decoderMode=").append(sanitize(String.valueOf(decoder.get("decoderMode"))));
    }

    String causes = describeCauseChain(error);
    if (!causes.isEmpty()) {
      sb.append(" (").append(causes).append(')');
    }
    return sb.toString();
  }

  /** 沿 cause 链最多走这么多层：media3 一般套 2~3 层，设上限是为了防环。 */
  private static final int MAX_CAUSE_DEPTH = 8;

  @NonNull
  private static String describeCauseChain(@NonNull Throwable error) {
    StringBuilder chain = new StringBuilder(96);
    Throwable current = error.getCause();
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_DEPTH) {
      String message = sanitize(current.getMessage());
      if (!message.isEmpty()) {
        if (chain.length() > 0) {
          chain.append(" <- ");
        }
        chain.append(current.getClass().getSimpleName());
        if (!"-".equals(message)) {
          chain.append(": ").append(message);
        }
      }
      current = current.getCause();
      depth++;
    }
    return chain.toString();
  }

  @Nullable
  private static <T extends Throwable> T findCause(
      @Nullable Throwable error, @NonNull Class<T> type) {
    Throwable current = error;
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_DEPTH) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
      depth++;
    }
    return null;
  }

  /**
   * 压成单行：报错会被塞进一行日志与卡片上的 SelectableText，换行会把反馈包打散；
   * 括号是外层字段块的定界符，必须换掉，否则 Dart 侧与 grep 都会错位。
   */
  @NonNull
  private static String sanitize(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return "-";
    }
    return value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('(', '[')
        .replace(')', ']');
  }
}
