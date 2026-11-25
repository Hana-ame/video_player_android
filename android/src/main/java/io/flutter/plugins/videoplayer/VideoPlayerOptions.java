// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VideoPlayerOptions {
  /** Default: prefer hardware decoder and automatically fall back to software decoder on failure. */
  public static final String DECODER_MODE_AUTO = "auto";

  /**
   * Smart anti-artifact mode: keep 16px-aligned videos on hardware decoders, and prefer software
   * decoders (with hardware still kept as fallback) only when width or height is not divisible by
   * 16.
   */
  public static final String DECODER_MODE_SMART = "smart";

  /**
   * Prefer software video decoders ahead of hardware decoders (MediaCodecSelector.PREFER_SOFTWARE),
   * while keeping hardware decoders as fallback if software cannot handle a format.
   */
  public static final String DECODER_MODE_PREFER_SOFTWARE = "prefer_software";

  /** Upstream behavior: disable decoder fallback (hardware only). */
  public static final String DECODER_MODE_HARDWARE_ONLY = "hardware_only";

  public boolean mixWithOthers;

  /**
   * The duration of the back buffer in milliseconds, used to configure ExoPlayer's load control.
   */
  @Nullable public Long backBufferDurationMs;

  /**
   * FORK PATCH (Hana-ame/twitter-pic-flutter): decoder selection strategy applied when building
   * ExoPlayer's RenderersFactory.
   */
  @NonNull public String decoderMode = DECODER_MODE_AUTO;

  public VideoPlayerOptions() {}

  /** Copy constructor to ensure all options are reliably copied. */
  public VideoPlayerOptions(@NonNull VideoPlayerOptions other) {
    this.mixWithOthers = other.mixWithOthers;
    this.backBufferDurationMs = other.backBufferDurationMs;
    this.decoderMode = other.decoderMode;
  }

  /** Normalizes untrusted decoder mode strings from Dart to a known constant. */
  @NonNull
  public static String normalizeDecoderMode(@Nullable String mode) {
    if (DECODER_MODE_SMART.equals(mode)) {
      return DECODER_MODE_SMART;
    }
    if (DECODER_MODE_PREFER_SOFTWARE.equals(mode)
        || "software".equals(mode)
        || "software_only".equals(mode)) {
      return DECODER_MODE_PREFER_SOFTWARE;
    }
    if (DECODER_MODE_HARDWARE_ONLY.equals(mode)) {
      return DECODER_MODE_HARDWARE_ONLY;
    }
    return DECODER_MODE_AUTO;
  }
}
