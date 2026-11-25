// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;
import java.util.List;

/**
 * FORK PATCH (Hana-ame/twitter-pic-flutter): builds a {@link DefaultRenderersFactory} according to
 * {@link VideoPlayerOptions#decoderMode}.
 *
 * <ul>
 *   <li>{@link VideoPlayerOptions#DECODER_MODE_AUTO}: hardware preferred + software fallback on
 *       (default, zero impact on unaffected devices).
 *   <li>{@link VideoPlayerOptions#DECODER_MODE_SMART}: keeps 16px-aligned videos on hardware, and
 *       reorders software decoders ahead of hardware only when width or height is not divisible by
 *       16 (hardware remains available as fallback).
 *   <li>{@link VideoPlayerOptions#DECODER_MODE_PREFER_SOFTWARE}: reorders software video decoders
 *       ahead of hardware decoders while preserving hardware fallback.
 *   <li>{@link VideoPlayerOptions#DECODER_MODE_HARDWARE_ONLY}: upstream default without fallback.
 * </ul>
 */
@UnstableApi
public final class CompatRenderersFactory extends DefaultRenderersFactory {
  private final String decoderMode;

  private CompatRenderersFactory(@NonNull Context context, @NonNull String decoderMode) {
    super(context);
    this.decoderMode = decoderMode;
  }

  /** Returns true when positive video dimensions are not multiples of 16px. */
  public static boolean isUnalignedResolution(int width, int height) {
    return width > 0 && height > 0 && ((width % 16) != 0 || (height % 16) != 0);
  }

  /**
   * Reorders a decoder list so software decoders are tried first while keeping hardware decoders as
   * fallback (never drops hardware decoders completely).
   */
  @NonNull
  static List<MediaCodecInfo> preferSoftwareOrder(@NonNull List<MediaCodecInfo> all) {
    List<MediaCodecInfo> software = new ArrayList<>();
    List<MediaCodecInfo> hardware = new ArrayList<>();
    for (MediaCodecInfo info : all) {
      if (info.softwareOnly) {
        software.add(info);
      } else {
        hardware.add(info);
      }
    }
    if (software.isEmpty()) {
      return all;
    }
    software.addAll(hardware);
    return software;
  }

  private static final MediaCodecSelector PREFER_SOFTWARE_VIDEO_SELECTOR =
      (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
        List<MediaCodecInfo> all =
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        if (!MimeTypes.isVideo(mimeType)) {
          return all;
        }
        return preferSoftwareOrder(all);
      };

  @NonNull
  public static DefaultRenderersFactory create(
      @NonNull Context context, @NonNull VideoPlayerOptions options) {
    String mode = VideoPlayerOptions.normalizeDecoderMode(options.decoderMode);
    CompatRenderersFactory factory = new CompatRenderersFactory(context, mode);
    if (VideoPlayerOptions.DECODER_MODE_PREFER_SOFTWARE.equals(mode)) {
      factory.setEnableDecoderFallback(true);
      factory.setMediaCodecSelector(PREFER_SOFTWARE_VIDEO_SELECTOR);
    } else if (VideoPlayerOptions.DECODER_MODE_HARDWARE_ONLY.equals(mode)) {
      factory.setEnableDecoderFallback(false);
    } else {
      factory.setEnableDecoderFallback(true);
    }
    return factory;
  }

  @Override
  protected void buildVideoRenderers(
      @NonNull Context context,
      int extensionRendererMode,
      @NonNull MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      @NonNull Handler eventHandler,
      @NonNull VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      @NonNull ArrayList<Renderer> out) {
    if (!VideoPlayerOptions.DECODER_MODE_SMART.equals(decoderMode)) {
      super.buildVideoRenderers(
          context,
          extensionRendererMode,
          mediaCodecSelector,
          enableDecoderFallback,
          eventHandler,
          eventListener,
          allowedVideoJoiningTimeMs,
          out);
      return;
    }

    UnalignedAwareVideoRenderer videoRenderer =
        new UnalignedAwareVideoRenderer(
            context,
            getCodecAdapterFactory(),
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    out.add(videoRenderer);
  }

  /**
   * Video renderer that prefers software decoders when the input video resolution is not aligned to
   * 16px macroblocks, while keeping standard aligned videos on hardware decoders (and preserving
   * hardware fallback if software cannot handle a format).
   */
  private static final class UnalignedAwareVideoRenderer extends MediaCodecVideoRenderer {
    @SuppressWarnings("deprecation")
    UnalignedAwareVideoRenderer(
        Context context,
        MediaCodecAdapter.Factory codecAdapterFactory,
        MediaCodecSelector mediaCodecSelector,
        long allowedJoiningTimeMs,
        boolean enableDecoderFallback,
        @Nullable Handler eventHandler,
        @Nullable VideoRendererEventListener eventListener,
        int maxDroppedFramesToNotify) {
      super(
          context,
          codecAdapterFactory,
          mediaCodecSelector,
          allowedJoiningTimeMs,
          enableDecoderFallback,
          eventHandler,
          eventListener,
          maxDroppedFramesToNotify);
    }

    @NonNull
    @Override
    protected List<MediaCodecInfo> getDecoderInfos(
        @NonNull MediaCodecSelector mediaCodecSelector,
        @NonNull Format format,
        boolean requiresSecureDecoder)
        throws MediaCodecUtil.DecoderQueryException {
      List<MediaCodecInfo> infos =
          super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder);
      if (isUnalignedResolution(format.width, format.height)) {
        return preferSoftwareOrder(infos);
      }
      return infos;
    }
  }
}
