# video\_player\_android

The Android implementation of [`video_player`][1].

## Fork note

This is a fork of upstream `video_player_android` **2.12.2** with one patch:
the ExoPlayer instances built in `TextureVideoPlayer` / `PlatformViewVideoPlayer`
set `new DefaultRenderersFactory(context).setEnableDecoderFallback(true)`.
Upstream leaves `enableDecoderFallback` at its default (`false`), so a video the
hardware AVC decoder rejects (format exceeds capabilities) or cannot allocate
(all hardware codec instances busy) fails outright instead of using the
software decoder (`c2.android.*`). With fallback enabled hardware is still
preferred; software is only a fallback. Consumed by
`Hana-ame/twitter-pic-flutter` through `dependency_overrides`.

## Usage

This package is [endorsed][2], which means you can simply use `video_player`
normally. This package will be automatically included in your app when you do,
so you do not need to add it to your `pubspec.yaml`.

However, if you `import` this package to use any of its APIs directly, you
should add it to your `pubspec.yaml` as usual.

## Known issues

Using `VideoViewType.platformView` is not currently recommended on Android due to a known [issue][3] affecting platform views on Android.

[1]: https://pub.dev/packages/video_player
[2]: https://flutter.dev/to/endorsed-federated-plugin
[3]: https://github.com/flutter/flutter/issues/164899
