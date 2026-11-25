// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompatRenderersFactoryTest {
  @Test
  public void normalizeDecoderMode_recognizesKnownModesAndFallsBackToAuto() {
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_AUTO,
        VideoPlayerOptions.normalizeDecoderMode(null));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_AUTO,
        VideoPlayerOptions.normalizeDecoderMode(""));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_AUTO,
        VideoPlayerOptions.normalizeDecoderMode("unknown"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_AUTO,
        VideoPlayerOptions.normalizeDecoderMode("auto"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_SMART,
        VideoPlayerOptions.normalizeDecoderMode("smart"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_PREFER_SOFTWARE,
        VideoPlayerOptions.normalizeDecoderMode("prefer_software"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_PREFER_SOFTWARE,
        VideoPlayerOptions.normalizeDecoderMode("software"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_PREFER_SOFTWARE,
        VideoPlayerOptions.normalizeDecoderMode("software_only"));
    assertEquals(
        VideoPlayerOptions.DECODER_MODE_HARDWARE_ONLY,
        VideoPlayerOptions.normalizeDecoderMode("hardware_only"));
  }

  @Test
  public void isUnalignedResolution_detectsNonMultipleOf16Dimensions() {
    assertFalse(CompatRenderersFactory.isUnalignedResolution(1280, 720));
    assertFalse(CompatRenderersFactory.isUnalignedResolution(1080, 1920));
    assertFalse(CompatRenderersFactory.isUnalignedResolution(-1, -1));
    assertFalse(CompatRenderersFactory.isUnalignedResolution(0, 0));

    assertTrue(CompatRenderersFactory.isUnalignedResolution(720, 1100));
    assertTrue(CompatRenderersFactory.isUnalignedResolution(848, 478));
    assertTrue(CompatRenderersFactory.isUnalignedResolution(688, 1040));
  }

  @Test
  public void videoPlayerOptions_copyConstructorPreservesDecoderMode() {
    VideoPlayerOptions original = new VideoPlayerOptions();
    original.decoderMode = VideoPlayerOptions.DECODER_MODE_SMART;

    VideoPlayerOptions copy = new VideoPlayerOptions(original);
    assertEquals(VideoPlayerOptions.DECODER_MODE_SMART, copy.decoderMode);
  }
}
