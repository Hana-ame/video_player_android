// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.HttpDataSource;
import java.util.Map;

/**
 * FORK PATCH (Hana-ame/twitter-pic-flutter): turns a {@link PlaybackException} into a message that
 * carries every field needed to locate the failure.
 *
 * <p>Upstream reported {@code "Video player had error " + error} only. Media3's {@code
 * PlaybackException.toString()} is just {@code "<obfuscated class>: <one-line summary>"} — with R8
 * on that reads like {@code "v.j: Source error"} — so {@code errorCode}, the HTTP status code and
 * the failing URL were all discarded before reaching Dart. Result: a 403, a 404, a connect timeout
 * and a connection reset all looked identical in the app, and users could only report "some videos
 * won't play" with nothing actionable behind it.
 *
 * <p>Output format (Dart side reads {@code code=} / {@code name=} / {@code http=} with a regex —
 * keep {@code lib/utils/video_failure.dart} in sync if this changes):
 *
 * <pre>
 * Video player had error v.j: Source error [code=2004 name=ERROR_CODE_IO_BAD_HTTP_STATUS
 *   http=403 httpMessage="Forbidden" uri=https://127.0.0.1:8123/media/xxx.mp4
 *   decoder=OMX.qcom.video.decoder.avc decoderMode=auto
 *   cause=InvalidResponseCodeException: Invalid response code: 403]
 * </pre>
 *
 * <p>The {@code "Video player had error "} prefix must stay: the Dart side matches on keywords
 * inside the original {@code error.toString()} (e.g. {@code format_supported=}, {@code Source
 * error}) and replacing the wording outright would silently break every existing judgement.
 */
final class PlaybackErrorReport {
  /** Cause chains in media3 are 2-3 deep; 8 levels is plenty and bounds a pathological cycle. */
  private static final int MAX_CAUSE_DEPTH = 8;

  private PlaybackErrorReport() {}

  /**
   * Builds the full message for a playback failure.
   *
   * @param error the failure reported by ExoPlayer.
   * @return a single-line, machine-readable message (never null, never multi-line).
   */
  @NonNull
  static String describe(@NonNull PlaybackException error) {
    StringBuilder fields = new StringBuilder(192);
    appendField(fields, "code", String.valueOf(error.errorCode));
    appendField(fields, "name", safe(error.getErrorCodeName()));

    // A 4xx/5xx reaches us as HttpDataSource.InvalidResponseCodeException buried in the cause
    // chain; that object is the only place the real status code survives.
    HttpDataSource.InvalidResponseCodeException badStatus =
        findCause(error, HttpDataSource.InvalidResponseCodeException.class);
    if (badStatus != null) {
      appendField(fields, "http", String.valueOf(badStatus.responseCode));
      appendField(fields, "httpMessage", quoted(badStatus.responseMessage));
      appendField(fields, "uri", safe(String.valueOf(badStatus.dataSpec.uri)));
    } else {
      // No status code (connect refused, reset, timeout ...) — the URL still tells us whether we
      // failed on the media itself or on a redirect hop.
      DataSourceException dataSourceError = findCause(error, DataSourceException.class);
      if (dataSourceError instanceof HttpDataSource.HttpDataSourceException) {
        appendField(
            fields,
            "uri",
            safe(String.valueOf(((HttpDataSource.HttpDataSourceException) dataSourceError).dataSpec.uri)));
      }
    }

    // Which decoder actually ran. Without this the Dart log cannot tell whether a failure happened
    // under hardware or under the software fallback, which is the first thing to rule out.
    Map<String, Object> decoder = DecoderTelemetry.getLastInfo();
    if (decoder != null) {
      appendField(fields, "decoder", safe(stringOf(decoder.get("decoderName"))));
      appendField(fields, "decoderMode", safe(stringOf(decoder.get("decoderMode"))));
    }

    appendField(fields, "cause", safe(describeCauseChain(error)));

    return "Video player had error " + error + " [" + fields + "]";
  }

  @NonNull
  private static String describeCauseChain(@NonNull Throwable error) {
    StringBuilder chain = new StringBuilder(96);
    Throwable current = error.getCause();
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_DEPTH) {
      if (chain.length() > 0) {
        chain.append(" <- ");
      }
      chain.append(current.getClass().getSimpleName()).append(": ").append(safe(current.getMessage()));
      current = current.getCause();
      depth++;
    }
    return chain.length() == 0 ? "none" : chain.toString();
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

  private static void appendField(
      @NonNull StringBuilder out, @NonNull String key, @NonNull String value) {
    if (out.length() > 0) {
      out.append(' ');
    }
    out.append(key).append('=').append(value);
  }

  /** Keeps the message single-line and free of the brackets that delimit the field block. */
  @NonNull
  private static String safe(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return "-";
    }
    return value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('[', '(')
        .replace(']', ')');
  }

  @NonNull
  private static String quoted(@Nullable String value) {
    return value == null ? "-" : "\"" + safe(value).replace('"', '\'') + "\"";
  }

  @NonNull
  private static String stringOf(@Nullable Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
