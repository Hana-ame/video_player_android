// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * FORK PATCH (Hana-ame/twitter-pic-flutter): stores the most recently initialized Android video
 * decoder metadata so Dart logs and user feedback dumps can verify whether hardware or software
 * decoding was actually selected.
 */
final class DecoderTelemetry {
  private DecoderTelemetry() {}

  @Nullable private static volatile Map<String, Object> lastInfo;

  static void record(
      @NonNull String decoderName,
      int width,
      int height,
      @NonNull String decoderMode,
      boolean unaligned) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("decoderName", decoderName);
    snapshot.put("width", width);
    snapshot.put("height", height);
    snapshot.put("decoderMode", decoderMode);
    snapshot.put("unaligned", unaligned);
    lastInfo = snapshot;
  }

  @Nullable
  static Map<String, Object> getLastInfo() {
    Map<String, Object> current = lastInfo;
    return current == null ? null : new HashMap<>(current);
  }
}
