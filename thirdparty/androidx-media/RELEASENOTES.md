# Release notes

## 1.6

### 1.6.1 (2025-04-14)

This release includes the following changes since the
[1.6.0 release](#160-2025-03-26):

*   Common Library:
    *   Add `PlaybackParameters.withPitch(float)` method for easily copying a
        `PlaybackParameters` with a new `pitch` value
        ([#2257](https://github.com/androidx/media/issues/2257)).
*   ExoPlayer:
    *   Fix issue where media item transition fails due to recoverable renderer
        error during initialization of the next media item
        ([#2229](https://github.com/androidx/media/issues/2229)).
    *   Fix issue where `ProgressiveMediaPeriod` throws an
        `IllegalStateException` as `PreloadMediaSource` attempts to call its
        `getBufferedDurationUs()` before it is prepared
        ([#2315](https://github.com/androidx/media/issues/2315)).
    *   Fix sending `CmcdData` in manifest requests for DASH, HLS, and
        SmoothStreaming ([#2253](https://github.com/androidx/media/pull/2253)).
    *   Ensure `AdPlaybackState.withAdDurationsUs(long[][])` can be used after
        ad groups have been removed. The user still needs to pass in an array of
        durations for removed ad groups which can be empty or null
        ([#2267](https://github.com/androidx/media/issues/2267)).
*   Extractors:
    *   MP4: Parse `alternate_group` from the `tkhd` box and expose it as an
        `Mp4AlternateGroupData` entry in each track's `Format.metadata`
        ([#2242](https://github.com/androidx/media/issues/2242)).
*   Audio:
    *   Fix offload issue where the position might get stuck when playing a
        playlist of short content
        ([#1920](https://github.com/androidx/media/issues/1920)).
*   Session:
    *   Lower aggregation timeout for platform `MediaSession` callbacks from 500
        to 100 milliseconds and add an experimental setter to allow apps to
        configure this value.
    *   Fix issue where notifications reappear after they have been dismissed by
        the user ([#2302](https://github.com/androidx/media/issues/2302)).
    *   Fix a bug where the session returned a single-item timeline when the
        wrapped player is actually empty. This happened when the wrapped player
        doesn't have `COMMAND_GET_TIMELINE` available while
        `COMMAND_GET_CURRENT_MEDIA_ITEM` is available and the wrapped player is
        empty ([#2320](https://github.com/androidx/media/issues/2320)).
    *   Fix a bug where calling
        `MediaSessionService.setMediaNotificationProvider` is silently ignored
        after other interactions with the service like
        `setForegroundServiceTimeoutMs`
        ([#2305](https://github.com/androidx/media/issues/2305)).
*   UI:
    *   Enable `PlayerSurface` to work with `ExoPlayer.setVideoEffects` and
        `CompositionPlayer`.
    *   Fix bug where `PlayerSurface` can't be recomposed with a new `Player`.
*   HLS extension:
    *   Fix issue where chunk duration wasn't set in `CmcdData` for HLS media,
        causing an assertion failure when processing encrypted media segments
        ([#2312](https://github.com/androidx/media/issues/2312)).
*   RTSP extension:
    *   Add support for URI with RTSPT scheme as a way to configure the RTSP
        session to use TCP
        ([#1484](https://github.com/androidx/media/issues/1484)).
*   Cast extension:
    *   Add support for playlist metadata
        ([#2235](https://github.com/androidx/media/pull/2235)).

### 1.6.0 (2025-03-26)

This release includes the following changes since the
[1.5.1 release](#151-2024-12-19):

*   Common Library:
    *   Add `AudioManagerCompat` and `AudioFocusRequestCompat` to replace the
        equivalent classes in `androidx.media`.
    *   Upgrade Kotlin from 1.9.20 to 2.0.20 and use Compose Compiler Gradle
        plugin. Upgrade KotlinX Coroutines library from 1.8.1 to 1.9.0.
    *   Remove `Format.toBundle(boolean excludeMetadata)` method, use
        `Format.toBundle()` instead.
    *   Fix bug in `SimpleBasePlayer` where setting a new
        `currentMediaItemIndex` in `State` after `setPlaylist` with `null`
        `MediaMetadata` does not reevaluate the metadata
        ([#1940](https://github.com/androidx/media/issues/1940)).
    *   Change `SimpleBasePlayer.State` access from protected to public to make
        it easier to handle updates in other classes
        ([#2128](https://github.com/androidx/media/issues/2128)).
*   ExoPlayer:
    *   Add `MediaExtractorCompat`, a new class that provides equivalent
        features to platform `MediaExtractor`.
    *   Add experimental 'ExoPlayer' pre-warming support for playback using
        `MediaCodecVideoRenderer`. You can configure `DefaultRenderersFactory`
        through `experimentalSetEnableMediaCodecVideoRendererPrewarming` to
        provide a secondary `MediaCodecVideoRenderer` to `ExoPlayer`. If
        enabled, `ExoPlayer` pre-processes the video of consecutive media items
        during playback to reduce media item transition latency.
    *   Reduce default values for `bufferForPlaybackMs` and
        `bufferForPlaybackAfterRebufferMs` in `DefaultLoadControl` to 1000 and
        2000 ms respectively.
    *   Initialize `DeviceInfo` and device volume asynchronously (if enabled
        using `setDeviceVolumeControlEnabled`). These values aren't available
        instantly after `ExoPlayer.Builder.build()`, and `Player.Listener`
        notifies changes through `onDeviceInfoChanged` and
        `onDeviceVolumeChanged`.
    *   Initial audio session id is no longer immediately available after
        creating the player. You can use
        `AnalyticsListener.onAudioSessionIdChanged` to listen to the initial
        update if required.
    *   Consider language when selecting a video track. By default, select a
        'main' video track that matches the language of the selected audio
        track, if available. Explicit video language preferences can be
        expressed with
        `TrackSelectionParameters.Builder.setPreferredVideoLanguage(s)`.
    *   Add `selectedAudioLanguage` parameter to
        `DefaultTrackSelector.selectVideoTrack()` method.
    *   Add `retryCount` parameter to `MediaSourceEventListener.onLoadStarted`
        and corresponding `MediaSourceEventListener.EventDispatcher` methods.
    *   Fix bug where playlist items or periods in multi-period DASH streams
        with durations that don't match the actual content could cause frame
        freezes at the end of the item
        ([#1698](https://github.com/androidx/media/issues/1698)).
    *   Move `BasePreloadManager.Listener` to a top-level
        `PreloadManagerListener`.
    *   `RenderersFactory.createSecondaryRenderer` can be implemented to provide
        secondary renderers for pre-warming. Pre-warming enables quicker media
        item transitions during playback.
    *   Enable sending `CmcdData` for manifest requests in adaptive streaming
        formats DASH, HLS, and SmoothStreaming
        ([#1951](https://github.com/androidx/media/issues/1951)).
    *   Provide `MediaCodecInfo` of the codec that will be initialized in
        `MediaCodecRenderer.onReadyToInitializeCodec`
        ([#1963](https://github.com/androidx/media/pull/1963)).
    *   Change `AdsMediaSource` to allow the `AdPlaybackStates` to grow by
        appending ad groups. Invalid modifications are detected and throw an
        exception.
    *   Fix issue where additional decode-only frames may be displayed in quick
        succession when transitioning to content media after a mid-roll ad.
    *   Make `DefaultRenderersFactory` add two `MetadataRenderer` instances to
        enable apps to receive two different schemes of metadata by default.
    *   Reevaluate whether the ongoing load of a chunk should be cancelled when
        playback is paused
        ([#1785](https://github.com/androidx/media/pull/1785)).
    *   Add option to `ClippingMediaSource` to allow clipping in unseekable
        media.
    *   Fix bug where seeking with pre-warming could block following media item
        transition.
    *   Fix a bug where `ExoPlayer.isLoading()` remains `true` while it has
        transitioned to `STATE_IDLE` or `STATE_ENDED`
        ([#2133](https://github.com/androidx/media/issues/2133)).
    *   Add `lastRebufferRealtimeMs` to `LoadControl.Parameter`
        ([#2113](https://github.com/androidx/media/pull/2113)).
*   Transformer:
    *   Add support for transmuxing into alternative backward compatible
        formats.
    *   Add support for transcoding and transmuxing Dolby Vision (profile 8)
        format.
    *   Update parameters of `VideoFrameProcessor.registerInputStream` and
        `VideoFrameProcessor.Listener.onInputStreamRegistered` to use `Format`.
    *   Generate HDR static metadata when using `DefaultEncoderFactory`.
    *   Enable support for Android platform diagnostics using
        `MediaMetricsManager`. Transformer forwards editing events and
        performance data to the platform, which helps to provide system
        performance and debugging information on the device. This data may also
        be collected by Google
        [if sharing usage and diagnostics data is enabled](https://support.google.com/accounts/answer/6078260)
        by the user of the device. Apps can opt-out of contributing to platform
        diagnostics for Transformer with
        `Transformer.Builder.setUsePlatformDiagnostics(false)`.
    *   Split `InAppMuxer` into `InAppMp4Muxer` and `InAppFragmentedMp4Muxer`.
        You use `InAppMp4Muxer` to produce a non-fragmented MP4 file, while
        `InAppFragmentedMp4Muxer` is for producing a fragmented MP4 file.
    *   Move `Muxer` interface from `media3-muxer` to `media3-transformer`.
    *   Add `MediaProjectionAssetLoader`, which provides media from a
        `MediaProjection` for screen recording, and add support for screen
        recording to the Transformer demo app.
    *   Add `#getInputFormat()` to `Codec` interface.
    *   Shift the responsibility to release the `GlObjectsProvider` onto the
        caller in `DefaultVideoFrameProcessor` and `DefaultVideoCompositor` when
        possible.
*   Extractors:
    *   AVI: Fix handling of files with constant bitrate compressed audio where
        the stream header stores the number of bytes instead of the number of
        chunks.
    *   Fix handling of NAL units with lengths expressed in 1 or 2 bytes (rather
        than 4).
    *   Fix `ArrayIndexOutOfBoundsException` in MP4 edit lists when the edit
        list starts at a non-sync frame with no preceding sync frame
        ([#2062](https://github.com/androidx/media/issues/2062)).
    *   Fix issue where TS streams can get stuck on some devices
        ([#2069](https://github.com/androidx/media/issues/2069)).
    *   FLAC: Add support for 32-bit FLAC files. Previously these would fail to
        play with `IllegalStateException: Playback stuck buffering and not
        loading` ([#2197](https://github.com/androidx/media/issues/2197)).
*   Audio:
    *   Fix `onAudioPositionAdvancing` to be called when playback resumes
        (previously it was called when playback was paused).
    *   Don't bypass `SonicAudioProcessor` when `SpeedChangingAudioProcessor` is
        configured with default parameters.
    *   Fix underflow in `Sonic#getOutputSize()` that could cause
        `DefaultAudioSink` to stall.
    *   Fix `MediaCodecAudioRenderer.getDurationToProgressUs()` and
        `DecoderAudioRenderer.getDurationToProgressUs()` so that seeks correctly
        reset the provided durations.
    *   Make `androidx.media3.common.audio.SonicAudioProcessor` final.
    *   Add support for float PCM to `ChannelMappingAudioProcessor` and
        `TrimmingAudioProcessor`.
*   Video:
    *   Change `MediaCodecVideoRenderer.shouldUsePlaceholderSurface` to
        protected so that applications can override to block usage of
        placeholder surfaces
        ([#1905](https://github.com/androidx/media/pull/1905)).
    *   Add experimental `ExoPlayer` AV1 sample dependency parsing to speed up
        seeking. Enable it with the new
        `DefaultRenderersFactory.experimentalSetParseAv1SampleDependencies` API.
    *   Add experimental `ExoPlayer` API to drop late `MediaCodecVideoRenderer`
        decoder input buffers that are not depended on. Enable it with
        `DefaultRenderersFactory.experimentalSetLateThresholdToDropDecoderInputUs`.
    *   Fix issue where a player without a surface was ready immediately and
        very slow decoding any pending frames
        ([#1973](https://github.com/androidx/media/issues/1973)).
    *   Exclude Xiaomi and OPPO devices from detached surface mode to avoid
        screen flickering
        ([#2059](https://github.com/androidx/media/issues/2059)).
*   Text:
    *   Add support for VobSub subtitles
        ([#8260](https://github.com/google/ExoPlayer/issues/8260)).
    *   Stop eagerly loading all subtitle files configured with
        `MediaItem.Builder.setSubtitleConfigurations`, and instead only load one
        if it is selected by track selection
        ([#1721](https://github.com/androidx/media/issues/1721)).
    *   TTML: Add support for referencing `tts:origin` and `tts:extent` using
        `style` ([#2953](https://github.com/google/ExoPlayer/issues/2953)).
    *   Restrict WebVTT and SubRip timestamps to exactly 3 decimal places.
        Previously we incorrectly parsed any number of decimal places but always
        assumed the value was in milliseconds, leading to incorrect timestamps
        ([#1997](https://github.com/androidx/media/issues/1997)).
    *   Fix playback hanging when a playlist contains clipped items with CEA-608
        or CEA-708 captions.
    *   Fix `IllegalStateException` when an SSA file contains a cue with zero
        duration (start and end time equal)
        ([#2052](https://github.com/androidx/media/issues/2052)).
    *   Suppress (and log) subtitle parsing errors when subtitles are muxed into
        the same container as audio and video
        ([#2052](https://github.com/androidx/media/issues/2052)).
    *   Fix handling of multi-byte UTF-8 characters in WebVTT files using CR
        line endings ([#2167](https://github.com/androidx/media/issues/2167)).
*   DRM:
    *   Fix `MediaCodec$CryptoException: Operation not supported in this
        configuration` error when playing ClearKey content on API < 27 devices
        ([#1732](https://github.com/androidx/media/issues/1732)).
*   Effect:
    *   Moved the functionality of `OverlaySettings` into
        `StaticOverlaySettings`. `OverlaySettings` can be subclassed to allow
        dynamic overlay settings.
*   Muxers:
    *   Moved `MuxerException` out of `Muxer` interface to avoid a very long
        fully qualified name.
    *   Renamed `setSampleCopyEnabled()` method to `setSampleCopyingEnabled()`
        in both `Mp4Muxer.Builder` and `FragmentedMp4Muxer.Builder`.
    *   `Mp4Muxer.addTrack()` and `FragmentedMp4Muxer.addTrack()` now return an
        `int` track ID instead of a `TrackToken`.
    *   `Mp4Muxer` and `FragmentedMp4Muxer` no longer implement `Muxer`
        interface.
    *   Disable `Mp4Muxer` sample batching and copying by default.
    *   Fix a bug in `FragmentedMp4Muxer` that creates a lot of fragments when
        only audio track is written.
*   Session:
    *   Keep foreground service state for an additional 10 minutes when playback
        pauses, stops or fails. This allows users to resume playback within this
        timeout without risking foreground service restrictions on various
        devices. Note that simply calling `player.pause()` can no longer be used
        to stop the foreground service before `stopSelf()` when overriding
        `onTaskRemoved`, use `MediaSessionService.pauseAllPlayersAndStopSelf()`
        instead.
    *   Keep notification visible when playback enters an error or stopped
        state. The notification is only removed if the playlist is cleared or
        the player is released.
    *   Improve handling of Android platform MediaSession actions ACTION_PLAY
        and ACTION_PAUSE to only set one of them according to the available
        commands and also accept if only one of them is set.
    *   Add `Context` as a parameter to
        `MediaButtonReceiver.shouldStartForegroundService`
        ([#1887](https://github.com/androidx/media/issues/1887)).
    *   Fix bug where calling a `Player` method on a `MediaController` connected
        to a legacy session dropped changes from a pending update.
    *   Make `MediaSession.setSessionActivity(PendingIntent)` accept null
        ([#2109](https://github.com/androidx/media/issues/2109)).
    *   Fix bug where a stale notification stays visible when the playlist is
        cleared ([#2211](https://github.com/androidx/media/issues/2211)).
*   UI:
    *   Add state holders and composables to the `media3-ui-compose` module for
        `PlayerSurface`, `PresentationState`, `PlayPauseButtonState`,
        `NextButtonState`, `PreviousButtonState`, `RepeatButtonState`,
        `ShuffleButtonState` and `PlaybackSpeedState`.
*   Downloads:
    *   Fix bug in `CacheWriter` that leaves data sources open and cache areas
        locked in case the data source throws an `Exception` other than
        `IOException`
        ([#9760](https://github.com/google/ExoPlayer/issues/9760)).
*   HLS extension:
    *   Add a first version of `HlsInterstitialsAdsLoader`. The ads loader reads
        the HLS interstitials of an HLS media playlist and maps them to the
        `AdPlaybackState` that is passed to the `AdsMediaSource`. This initial
        version only supports HLS VOD streams with `X-ASSET-URI` attributes.
    *   Add `HlsInterstitialsAdsLoader.AdsMediaSourceFactory`. Apps can use it
        to create `AdsMediaSource` instances that use an
        `HlsInterstitialsAdsLoader` in a convenient and safe way.
    *   Parse `SUPPLEMENTAL-CODECS` tag from HLS playlist to detect Dolby Vision
        formats ([#1785](https://github.com/androidx/media/pull/1785)).
    *   Loosen the condition for seeking to sync positions in an HLS stream
        ([#2209](https://github.com/androidx/media/issues/2209)).
*   DASH extension:
    *   Add AC-4 Level-4 format support for DASH
        ([#1898](https://github.com/androidx/media/pull/1898)).
    *   Fix issue when calculating the update interval for ad insertion in
        multi-period live streams
        ([#1698](https://github.com/androidx/media/issues/1698)).
    *   Parse `scte214:supplementalCodecs` attribute from DASH manifest to
        detect Dolby Vision formats
        ([#1785](https://github.com/androidx/media/pull/1785)).
    *   Improve handling of period transitions in live streams where the period
        contains media samples beyond the declared period duration
        ([#1698](https://github.com/androidx/media/issues/1698)).
    *   Fix issue where adaptation sets marked with `adaptation-set-switching`
        but different languages or role flags are merged together
        ([#2222](https://github.com/androidx/media/issues/2222)).
*   Decoder extensions (FFmpeg, VP9, AV1, etc.):
    *   Add the MPEG-H decoder module which uses the native MPEG-H decoder
        module to decode MPEG-H audio
        ([#1826](https://github.com/androidx/media/pull/1826)).
*   MIDI extension:
    *   Plumb custom `AudioSink` and `AudioRendererEventListener` instances into
        `MidiRenderer`.
*   Cast extension:
    *   Bump the `play-services-cast-framework` dependency to 21.5.0 to fix a
        `FLAG_MUTABLE` crash in apps targeting API 34+ on devices with Google
        Play services installed but disabled
        ([#2178](https://github.com/androidx/media/issues/2178)).
*   Demo app:
    *   Extend `demo-compose` with additional buttons and enhance
        `PlayerSurface` integration with scaling and shutter support.
*   Remove deprecated symbols:
    *   Remove deprecated `AudioMixer.create()` method. Use
        `DefaultAudioMixer.Factory().create()` instead.
    *   Remove the following deprecated `Transformer.Builder` methods:
        *   `setTransformationRequest()`, use `setAudioMimeType()`,
            `setVideoMimeType()`, and `setHdrMode()` instead.
        *   `setAudioProcessors()`, set the audio processor in an
            `EditedMediaItem.Builder.setEffects()`, and pass it to
            `Transformer.start()` instead.
        *   `setVideoEffects()`, set video effect in an
            `EditedMediaItem.Builder.setEffects()`, and pass it to
            `Transformer.start()` instead.
        *   `setRemoveAudio()`, use `EditedMediaItem.Builder.setRemoveAudio()`
            to remove the audio from the `EditedMediaItem` passed to
            `Transformer.start()` instead.
        *   `setRemoveVideo()`, use `EditedMediaItem.Builder.setRemoveVideo()`
            to remove the video from the `EditedMediaItem` passed to
            `Transformer.start()` instead.
        *   `setFlattenForSlowMotion()`, use
            `EditedMediaItem.Builder.setFlattenForSlowMotion()` to flatten the
            `EditedMediaItem` passed to `Transformer.start()` instead.
        *   `setListener()`, use `addListener()`, `removeListener()` or
            `removeAllListeners()` instead.
    *   Remove the following deprecated `Transformer.Listener` methods:
        *   `onTransformationCompleted(MediaItem)`, use
            `onCompleted(Composition, ExportResult)` instead.
        *   `onTransformationCompleted(MediaItem, TransformationResult)`, use
            `onCompleted(Composition, ExportResult)` instead.
        *   `onTransformationError(MediaItem, Exception)`, use
            `onError(Composition, ExportResult, ExportException)` instead.
        *   `onTransformationError(MediaItem, TransformationException)`, use
            `onError(Composition, ExportResult, ExportException)` instead.
        *   `onTransformationError(MediaItem, TransformationResult,
            TransformationException)`, use `onError(Composition, ExportResult,
            ExportException)` instead.
        *   `onFallbackApplied(MediaItem, TransformationRequest,
            TransformationRequest)`, use `onFallbackApplied(Composition,
            TransformationRequest, TransformationRequest)` instead.
    *   Remove deprecated `TransformationResult` class. Use `ExportResult`
        instead.
    *   Remove deprecated `TransformationException` class. Use `ExportException`
        instead.
    *   Remove deprecated `Transformer.PROGRESS_STATE_NO_TRANSFORMATION`. Use
        `Transformer.PROGRESS_STATE_NOT_STARTED` instead.
    *   Remove deprecated `Transformer.setListener()`. Use
        `Transformer.addListener()`, `Transformer.removeListener()` or
        `Transformer.removeAllListeners()` instead.
    *   Remove deprecated `Transformer.startTransformation()`. Use
        `Transformer.start(MediaItem, String)` instead.
    *   Remove deprecated `SingleFrameGlShaderProgram`. Use
        `BaseGlShaderProgram` instead.
    *   Remove `Transformer.flattenForSlowMotion`. Use
        `EditedMediaItem.flattenForSlowMotion` instead.
    *   Removed `ExoPlayer.VideoComponent`, `ExoPlayer.AudioComponent`,
        `ExoPlayer.TextComponent` and `ExoPlayer.DeviceComponent`.
    *   Removed `androidx.media3.exoplayer.audio.SonicAudioProcessor`.
    *   Removed the following deprecated `DownloadHelper` methods:
        *   Constructor `DownloadHelper(MediaItem, @Nullable MediaSource,
            TrackSelectionParameters, RendererCapabilities[])`, use
            `DownloadHelper(MediaItem, @Nullable MediaSource,
            TrackSelectionParameters, RendererCapabilitiesList)` instead.
        *   `getRendererCapabilities(RenderersFactory)`, equivalent
            functionality can be achieved by creating a
            `DefaultRendererCapabilitiesList` with a `RenderersFactory`, and
            calling `DefaultRendererCapabilitiesList.getRendererCapabilities()`.
    *   Removed
        `PlayerNotificationManager.setMediaSessionToken(MediaSessionCompat)`
        method. Use
        `PlayerNotificationManager.setMediaSessionToken(MediaSession.Token)` and
        pass in `(MediaSession.Token) compatToken.getToken()` instead.

### 1.6.0-rc02 (2025-03-18)

Use the 1.6.0 [stable version](#160-2025-03-26).

### 1.6.0-rc01 (2025-03-12)

Use the 1.6.0 [stable version](#160-2025-03-26).

### 1.6.0-beta01 (2025-02-26)

Use the 1.6.0 [stable version](#160-2025-03-26).

### 1.6.0-alpha03 (2025-02-06)

Use the 1.6.0 [stable version](#160-2025-03-26).

### 1.6.0-alpha02 (2025-01-30)

Use the 1.6.0 [stable version](#160-2025-03-26).

### 1.6.0-alpha01 (2024-12-20)

Use the 1.6.0 [stable version](#160-2025-03-26).

## 1.5

### 1.5.1 (2024-12-19)

This release includes the following changes since the
[1.5.0 release](#150-2024-11-27):

*   ExoPlayer:
    *   Disable use of asynchronous decryption in MediaCodec to avoid reported
        codec timeout issues with this platform API
        ([#1641](https://github.com/androidx/media/issues/1641)).
*   Extractors:
    *   MP3: Don't stop playback early when a `VBRI` frame's table of contents
        doesn't cover all the MP3 data in a file
        ([#1904](https://github.com/androidx/media/issues/1904)).
*   Video:
    *   Rollback of using `MediaCodecAdapter` supplied pixel aspect ratio values
        when provided while processing `onOutputFormatChanged`
        ([#1371](https://github.com/androidx/media/pull/1371)).
*   Text:
    *   Fix bug in `ReplacingCuesResolver.discardCuesBeforeTimeUs` where the cue
        active at `timeUs` (started before but not yet ended) was incorrectly
        discarded ([#1939](https://github.com/androidx/media/issues/1939)).
*   Metadata:
    *   Extract disc/track numbering and genre from Vorbis comments into
        `MediaMetadata`
        ([#1958](https://github.com/androidx/media/issues/1958)).

### 1.5.0 (2024-11-27)

This release includes the following changes since the
[1.4.1 release](#141-2024-08-23):

*   Common Library:
    *   Add `ForwardingSimpleBasePlayer` that allows forwarding to another
        player with small adjustments while ensuring full consistency and
        listener handling
        ([#1183](https://github.com/androidx/media/issues/1183)).
    *   Replace `SimpleBasePlayer.State.playlist` by `getPlaylist()` method.
    *   Add override for `SimpleBasePlayer.State.Builder.setPlaylist()` to
        directly specify a `Timeline` and current `Tracks` and `Metadata`
        instead of building a playlist structure.
    *   Increase `minSdk` to 21 (Android Lollipop). This is aligned with all
        other AndroidX libraries.
    *   Add `androidx.media3:media3-common-ktx` artifact which provides
        Kotlin-specific functionality built on top of the Common library
    *   Add `Player.listen` suspending extension function to spin a coroutine to
        listen to `Player.Events` to the `media3-common-ktx` library.
    *   Remove `@DoNotInline` annotations from manually out-of-lined inner
        classes designed to avoid
        [runtime class verification failures](https://chromium.googlesource.com/chromium/src/+/HEAD/build/android/docs/class_verification_failures.md).
        Recent versions of [R8](https://developer.android.com/build/shrink-code)
        now automatically out-of-line calls like these to avoid the runtime
        failures (so the manual out-of-lining is no longer required). All Gradle
        users of the library must already be a using a version of the Android
        Gradle Plugin that uses a version of R8 which does this,
        [due to `compileSdk = 35`](https://issuetracker.google.com/345472586#comment7).
        Users of the library with non-Gradle build systems will need to ensure
        their R8-equivalent shrinking/obfuscating step does a similar automatic
        out-of-lining process in order to avoid runtime class verification
        failures. This change has
        [already been done in other AndroidX libraries](http://r.android.com/3156141).
*   ExoPlayer:
    *   `MediaCodecRenderer.onProcessedStreamChange()` can now be called for
        every media item. Previously it was not called for the first one. Use
        `MediaCodecRenderer.experimentalEnableProcessedStreamChangedAtStart()`
        to enable this.
    *   Add `PreloadMediaSource.PreloadControl.onPreloadError` to allow
        `PreloadMediaSource.PreloadControl` implementations to take actions when
        error occurs.
    *   Add `BasePreloadManager.Listener` to propagate preload events to apps.
    *   Allow changing SNTP client timeout and retry alternative addresses on
        timeout ([#1540](https://github.com/androidx/media/issues/1540)).
    *   Remove `MediaCodecAdapter.Configuration.flags` as the field was always
        zero.
    *   Allow the user to select the built-in speaker for playback on Wear OS
        API 35+ (where the device advertises support for this).
    *   Defer the blocking call to
        `Context.getSystemService(Context.AUDIO_SERVICE)` until audio focus
        handling is enabled. This ensures the blocking call isn't done if audio
        focus handling is not enabled
        ([#1616](https://github.com/androidx/media/pull/1616)).
    *   Allow playback regardless of buffered duration when loading fails
        ([#1571](https://github.com/androidx/media/issues/1571)).
    *   Add `AnalyticsListener.onRendererReadyChanged()` to signal when
        individual renderers allow playback to be ready.
    *   Fix `MediaCodec.CryptoException` sometimes being reported as an
        "unexpected runtime error" when `MediaCodec` is operated in asynchronous
        mode (default behaviour on API 31+).
    *   Pass `bufferedDurationUs` instead of `bufferedPositionUs` with
        `PreloadMediaSource.PreloadControl.onContinueLoadingRequested()`. Also
        changes `DefaultPreloadManager.Status.STAGE_LOADED_TO_POSITION_MS` to
        `DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS`, apps then
        need to pass a value representing a specific duration from the default
        start position for which the corresponding media source has to be
        preloaded with this IntDef, instead of a position.
    *   Add `ForwardingRenderer` implementation that forwards all method calls
        to another renderer
        ([1703](https://github.com/androidx/media/pull/1703)).
    *   Add playlist preloading for the next item in the playlist. Apps can
        enable preloading by calling
        `ExoPlayer.setPreloadConfiguration(PreloadConfiguration)` accordingly.
        By default preloading is disabled. When opted-in and to not interfere
        with playback, `DefaultLoadControl` restricts preloading to start and
        continue only when the player is not loading for playback. Apps can
        change this behaviour by implementing
        `LoadControl.shouldContinuePreloading()` accordingly (like when
        overriding this method in `DefaultLoadControl`). The default
        implementation of `LoadControl` disables preloading in case an app is
        using a custom implementation of `LoadControl`.
    *   Add method `MediaSourceEventListener.EventDispatcher.dispatchEvent()` to
        allow invoking events of subclass listeners
        ([1736](https://github.com/androidx/media/pull/1736)).
    *   Add `DefaultPreloadManager.Builder` that builds the
        `DefaultPreloadManager` and `ExoPlayer` instances with consistently
        shared configurations.
    *   Remove `Renderer[]` parameter from `LoadControl.onTracksSelected()` as
        `DefaultLoadControl` implementation can retrieve the stream types from
        `ExoTrackSelection[]`.
    *   Deprecated `DefaultLoadControl.calculateTargetBufferBytes(Renderer[],
        ExoTrackSelection[])` and marked method as final to prevent overrides.
        The new
        `DefaultLoadControl.calculateTargetBufferBytes(ExoTrackSelection[])`
        should be used instead.
    *   Report `MediaSourceEventListener` events from secondary sources in
        `MergingMediaSource`. This will result in load
        start/error/cancelled/completed events being reported for sideloaded
        subtitles (those added with
        `MediaItem.LocalConfiguration.subtitleConfigurations`), which may appear
        as duplicate load events emitted from `AnalyticsListener`.
    *   Prevent subtitle & metadata errors from completely stopping playback.
        Instead the problematic track is disabled and playback of the remaining
        tracks continues
        ([#1722](https://github.com/google/ExoPlayer/issues/1722)).
        *   In new subtitle handling (during extraction), associated parse (e.g.
            invalid subtitle data) and load errors (e.g. HTTP 404) are emitted
            via `onLoadError` callbacks.
        *   In legacy subtitle handling (during rendering), only associated load
            errors are emitted via `onLoadError` callbacks while parse errors
            are silently ignored (this is pre-existing behaviour).
    *   Fix bug where playlist items or periods in multi-period DASH streams
        with durations that don't match the actual content could cause frame
        freezes at the end of the item
        ([#1698](https://github.com/androidx/media/issues/1698)).
    *   Add a setter to `SntpClient` to set the max elapsed time since the last
        update after which the client is re-initialized
        ([#1794](https://github.com/androidx/media/pull/1794)).
*   Transformer:
    *   Add `SurfaceAssetLoader`, which supports queueing video data to
        Transformer via a `Surface`.
    *   `ImageAssetLoader` reports unsupported input via `AssetLoader.onError`
        instead of throwing an `IllegalStateException`.
    *   Make setting the image duration using
        `MediaItem.Builder.setImageDurationMs` mandatory for image export.
    *   Add export support for gaps in sequences of audio EditedMediaItems.
*   Track Selection:
    *   `DefaultTrackSelector`: Prefer object-based audio over channel-based
        audio when other factors are equal.
*   Extractors:
    *   Allow `Mp4Extractor` and `FragmentedMp4Extractor` to identify H264
        samples that are not used as reference by subsequent samples.
    *   Add option to enable index-based seeking in `AmrExtractor`.
    *   Treat MP3 files with more than 128kB between valid frames as truncated
        (instead of invalid). This means files with non-MP3 data at the end,
        with no other metadata to indicate the length of the MP3 bytes, now stop
        playback at the end of the MP3 data instead of failing with
        `ParserException: Searched too many bytes.{contentIsMalformed=true,
        dataType=1}` ([#1563](https://github.com/androidx/media/issues/1563)).
    *   Fix preroll sample handling for non-keyframe media start positions when
        processing edit lists in MP4 files
        ([#1659](https://github.com/google/ExoPlayer/issues/1659)).
    *   Improved frame rate calculation by using media duration from the `mdhd`
        box in `Mp4Extractor` and `FragmentedMp4Extractor`
        ([#1531](https://github.com/androidx/media/issues/1531)).
    *   Fix incorrect scaling of `media_time` in MP4 edit lists. While
        `segment_duration` was already correctly scaled using the movie
        timescale, `media_time` is now properly scaled using the track
        timescale, as specified by the MP4 format standard
        ([#1792](https://github.com/androidx/media/issues/1792)).
    *   Handle out-of-order frames in `endIndices` calculation for MP4 with edit
        list ([#1797](https://github.com/androidx/media/issues/1797)).
    *   Fix media duration parsing in `mdhd` box of MP4 files to handle `-1`
        values ([#1819](https://github.com/androidx/media/issues/1819)).
    *   Add support for identifying `h263` box in MP4 files for H.263 video
        ([#1821](https://github.com/androidx/media/issues/1821)).
    *   Add AC-4 Level-4 ISO base media file format support
        ([#1265](https://github.com/androidx/media/pull/1265)).
*   DataSource:
    *   Update `HttpEngineDataSource` to allow use starting at version S
        extension 7 instead of API level 34
        ([#1262](https://github.com/androidx/media/issues/1262)).
    *   `DataSourceContractTest`: Assert that `DataSource.getUri()` returns the
        resolved URI (as documented). Where this is different to the requested
        URI, tests can indicate this using the new
        `DataSourceContractTest.TestResource.Builder.setResolvedUri()` method.
    *   `DataSourceContractTest`: Assert that `DataSource.getUri()` and
        `getResponseHeaders()` return their 'open' value after a failed call to
        `open()` (due to a 'not found' resource) and before a subsequent
        `close()` call.
        *   Overriding `DataSourceContractTest.getNotFoundResources()` allows
            test sub-classes to provide multiple 'not found' resources, and to
            provide any expected headers too. This allows to distinguish between
            HTTP 404 (with headers) and "server not found" (no headers).
*   Audio:
    *   Automatically configure CTA-2075 loudness metadata on the codec if
        present in the media.
    *   Ensure smooth volume ramp down when seeking.
    *   Fix pop sounds that may occur during seeks.
    *   Fix truncation error accumulation for Sonic's
        time-stretching/pitch-shifting algorithm.
    *   Fix bug in `SpeedChangingAudioProcessor` that causes dropped output
        frames.
*   Video:
    *   `MediaCodecVideoRenderer` avoids decoding samples that are neither
        rendered nor used as reference by other samples.
    *   On API 35 and above, `MediaCodecAdapter` may now receive a `null`
        `Surface` in `configure` and calls to a new method `detachOutputSurface`
        to remove a previously set `Surface` if the codec supports this
        (`MediaCodecInfo.detachedSurfaceSupported`).
    *   Use `MediaCodecAdapter` supplied pixel aspect ratio values if provided
        when processing `onOutputFormatChanged`
        ([#1371](https://github.com/androidx/media/pull/1371)).
    *   Add workaround for a device issue on Galaxy Tab S7 FE that causes 60fps
        secure H264 streams to be marked as unsupported
        ([#1619](https://github.com/androidx/media/issues/1619)).
    *   Add workaround for codecs that get stuck after the last sample without
        returning an end-of-stream signal.
*   Text:
    *   Add a custom `VoiceSpan` and populate it for
        [WebVTT voice spans](https://www.w3.org/TR/webvtt1/#webvtt-cue-voice-span)
        ([#1632](https://github.com/androidx/media/issues/1632)).
    *   Ensure WebVTT in HLS with very large subtitle timestamps (which overflow
        a 64-bit `long` when represented as microseconds and multiplied by the
        `90,000` MPEG timebase) are displayed
        ([#1763](https://github.com/androidx/media/issues/1763)).
    *   Support CEA-608 subtitles in Dolby Vision content
        ([#1820](https://github.com/androidx/media/issues/1820)).
    *   Fix playback hanging on DASH multi-period streams when CEA-608 subtitles
        are enabled ([#1863](https://github.com/androidx/media/issues/1863)).
*   Metadata:
    *   Assign the `C.TRACK_TYPE_METADATA` type to tracks containing icy or
        vnd.dvb.ait content.
*   Image:
    *   Add `ExternallyLoadedImageDecoder` for simplified integration with
        external image loading libraries like Glide or Coil.
*   DataSource:
    *   Add `FileDescriptorDataSource`, a new `DataSource` that can be used to
        read from a `FileDescriptor`
        ([#3757](https://github.com/google/ExoPlayer/issues/3757)).
*   Effect:
    *   Add `DefaultVideoFrameProcessor` workaround for minor `SurfaceTexture`
        scaling. `SurfaceTexture` may include a small scaling that cuts off a
        1-texel border around the edge of a cropped buffer. This is now handled
        such that output is closer to expected.
    *   Speed up `DefaultVideoFrameProcessor.queueInputBitmap()`. As a result,
        exporting images to videos with `Transformer` is faster.
*   IMA extension:
    *   Fix bug where clearing the playlist may cause an
        `ArrayIndexOutOfBoundsException` in
        `ImaServerSideAdInsertionMediaSource`.
    *   Fix bug where server-side inserted DAI streams without a preroll can
        result in an `ArrayIndexOutOfBoundsException` when playing past the last
        midroll ([#1741](https://github.com/androidx/media/issues/1741)).
*   Session:
    *   Add `MediaButtonReceiver.shouldStartForegroundService(Intent)` to allow
        apps to suppress a play command coming in for playback resumption by
        overriding this method. By default, the service is always started and
        playback can't be suppressed without the system crashing the service
        with a `ForegroundServiceDidNotStartInTimeException`
        ([#1528](https://github.com/google/ExoPlayer/issues/1528)).
    *   Fix bug that caused custom commands sent from a `MediaBrowser` being
        dispatched to the `MediaSessionCompat.Callback` instead of the
        `MediaBrowserServiceCompat` variant of the method when connected to a
        legacy service. This prevented the `MediaBrowser` to receive the actual
        return value sent back by the legacy service
        ([#1474](https://github.com/androidx/media/issues/1474)).
    *   Handle `IllegalArgumentException` thrown by devices of certain
        manufacturers when setting the broadcast receiver for media button
        intents ([#1730](https://github.com/androidx/media/issues/1730)).
    *   Add command buttons for media items. This adds the Media3 API for what
        was known as `Custom browse actions` with the legacy library with
        `MediaBrowserCompat`. Note that with Media3 command buttons for media
        items are available for both, `MediaBrowser` and `MediaController`. See
        [Custom Browse actions of AAOS](https://developer.android.com/training/cars/media#custom_browse_actions).
    *   Fix bug where a Media3 controller was sometimes unable to let a session
        app start a foreground service after requesting `play()`.
    *   Restrict `CommandButton.Builder.setIconUri` to only accept content Uris.
    *   Pass connection hints of a Media3 browser to the initial
        `MediaBrowserCompat` when connecting to a legacy `MediaBrowserCompat`.
        The service can receive the connection hints passed in as root hints
        with the first call to `onGetRoot()`.
    *   Fix bug where a `MediaBrowser` connected to a legacy browser service,
        didn't receive an error sent by the service after the browser has
        subscribed to a `parentid`.
    *   Improve interoperability behavior, so that a Media3 browser that is
        connected to a legacy `MediaBrowserService` doesn't request the children
        of a `parentId` twice when subscribing to a parent.
*   UI:
    *   Make the stretched/cropped video in
        `PlayerView`-in-Compose-`AndroidView` workaround opt-in, due to issues
        with XML-based shared transitions. Apps using `PlayerView` inside
        `AndroidView` need to call
        `PlayerView.setEnableComposeSurfaceSyncWorkaround` in order to opt-in
        ([#1237](https://github.com/androidx/media/issues/1237),
        [#1594](https://github.com/androidx/media/issues/1594)).
    *   Add `setFullscreenButtonState` to `PlayerView` to allow updates of
        fullscreen button's icon on demand, i.e. out-of-band and not reactively
        to a click interaction
        ([#1590](https://github.com/androidx/media/issues/1590),
        [#184](https://github.com/androidx/media/issues/184)).
    *   Fix bug where the "None" choice in the text selection is not working if
        there are app-defined text track selection preferences.
*   DASH extension:
    *   Add support for periods starting in the middle of a segment
        ([#1440](https://github.com/androidx/media/issues/1440)).
*   Smooth Streaming extension:
    *   Fix a `Bad magic number for Bundle` error when playing SmoothStreaming
        streams with text tracks
        ([#1779](https://github.com/androidx/media/issues/1779)).
*   RTSP extension:
    *   Fix user info removal for URLs that contain encoded @ characters
        ([#1138](https://github.com/androidx/media/pull/1138)).
    *   Fix crashing when parsing of RTP packets with header extensions
        ([#1225](https://github.com/androidx/media/pull/1225)).
*   Decoder extensions (FFmpeg, VP9, AV1, etc.):
    *   Add the IAMF decoder module, which provides support for playback of MP4
        files containing IAMF tracks using the libiamf native library to
        synthesize audio.
        *   Playback is enabled with a stereo layout as well as 5.1 with
            spatialization together with optional head tracking enabled, but
            binaural playback support is currently not available.
    *   Add 16 KB page support for decoder extensions on Android 15
        ([#1685](https://github.com/androidx/media/issues/1685)).
*   Cast extension:
    *   Stop cleaning the timeline after the CastSession disconnects, which
        enables the sender app to resume playback locally after a disconnection.
    *   Populate CastPlayer's `DeviceInfo` when a `Context` is provided. This
        enables linking the `MediaSession` to a `RoutingSession`, which is
        necessary for integrating Output Switcher
        ([#1056](https://github.com/androidx/media/issues/1056)).
*   Test Utilities:
    *   `DataSourceContractTest` now includes tests to verify:
        *   Input stream `read position` is updated.
        *   Output buffer `offset` is applied correctly.
*   Demo app
    *   Resolve the memory leaks in demo short-form app
        ([#1839](https://github.com/androidx/media/issues/1839)).
*   Remove deprecated symbols:
    *   Remove deprecated `Player.hasPrevious`, `Player.hasPreviousWindow()`.
        Use `Player.hasPreviousMediaItem()` instead.
    *   Remove deprecated `Player.previous()`method. Use
        `Player.seekToPreviousMediaItem()` instead.
    *   Remove deprecated `DrmSessionEventListener.onDrmSessionAcquired` method.
    *   Remove deprecated `DefaultEncoderFactory` constructors. Use
        `DefaultEncoderFactory.Builder` instead.

### 1.5.0-rc02 (2024-11-19)

Use the 1.5.0 [stable version](#150-2024-11-27).

### 1.5.0-rc01 (2024-11-13)

Use the 1.5.0 [stable version](#150-2024-11-27).

### 1.5.0-beta01 (2024-10-30)

Use the 1.5.0 [stable version](#150-2024-11-27).

### 1.5.0-alpha01 (2024-09-06)

Use the 1.5.0 [stable version](#150-2024-11-27).

## 1.4

### 1.4.1 (2024-08-23)

This release includes the following changes since the
[1.4.0 release](#140-2024-07-24):

*   ExoPlayer:
    *   Handle preload callbacks asynchronously in `PreloadMediaSource`
        ([#1568](https://github.com/androidx/media/issues/1568)).
    *   Allow playback regardless of buffered duration when loading fails
        ([#1571](https://github.com/androidx/media/issues/1571)).
*   Extractors:
    *   MP3: Fix `Searched too many bytes` error by correctly ignoring trailing
        non-MP3 data based on the length field in an `Info` frame
        ([#1480](https://github.com/androidx/media/issues/1480)).
*   Text:
    *   TTML: Fix handling of percentage `tts:fontSize` values to ensure they
        are correctly inherited from parent nodes with percentage `tts:fontSize`
        values.
    *   Fix `IndexOutOfBoundsException` in `LegacySubtitleUtil` due to
        incorrectly handling the case of the requested output start time being
        greater than or equal to the final event time in the `Subtitle`
        ([#1516](https://github.com/androidx/media/issues/1516)).
*   DRM:
    *   Fix `android.media.MediaCodec$CryptoException: Operation not supported
        in this configuration: ERROR_DRM_CANNOT_HANDLE` error on API 31+ devices
        playing L1 Widevine content. This error is caused by an incomplete
        implementation of the framework
        [`MediaDrm.requiresSecureDecoder`](https://developer.android.com/reference/android/media/MediaDrm#requiresSecureDecoder\(java.lang.String\))
        method ([#1603](https://github.com/androidx/media/issues/1603)).
*   Effect:
    *   Add a `release()` method to `GlObjectsProvider`.
*   Session:
    *   Transform a double-tap of `KEYCODE_HEADSETHOOK` into a 'seek to next'
        action, as
        [documented](https://developer.android.com/reference/androidx/media3/session/MediaSession#media-key-events-mapping)
        ([#1493](https://github.com/androidx/media/issues/1493)).
    *   Handle `KEYCODE_HEADSETHOOK` as a 'play' command in
        `MediaButtonReceiver` when deciding whether to ignore it to avoid a
        `ForegroundServiceDidNotStartInTimeException`
        ([#1581](https://github.com/androidx/media/issues/1581)).
*   RTSP extension:
    *   Skip invalid Media Descriptions in SDP parsing
        ([#1087](https://github.com/androidx/media/issues/1472)).

### 1.4.0 (2024-07-24)

This release includes the following changes since the
[1.3.1 release](#131-2024-04-11):

*   Common Library:
    *   Forward presumed no-op seek calls to the protected `BasePlayer.seekTo()`
        and `SimpleBasePlayer.handleSeek()` methods instead of ignoring them. If
        you are implementing these methods in a custom player, you may need to
        handle these additional calls with `mediaItemIndex == C.INDEX_UNSET`.
    *   Remove compile dependency on enhanced Java 8 desugaring
        ([#1312](https://github.com/androidx/media/issues/1312)).
    *   Ensure the duration passed to `MediaItem.Builder.setImageDurationMs()`
        is ignored for a non-image `MediaItem` (as documented).
    *   Add `Format.customData` to store app-provided custom information about
        `Format` instances.
*   ExoPlayer:
    *   Add `BasePreloadManager` which coordinates the preloading for multiple
        sources based on the priorities defined by their `rankingData`.
        Customization is possible by extending this class. Add
        `DefaultPreloadManager` which uses `PreloadMediaSource` to preload media
        samples of the sources into memory, and uses an integer `rankingData`
        that indicates the index of an item on the UI.
    *   Add `PlayerId` to most methods of `LoadControl` to enable `LoadControl`
        implementations to support multiple players.
    *   Remove `Buffer.isDecodeOnly()` and `C.BUFFER_FLAG_DECODE_ONLY`. There is
        no need to set this flag as renderers and decoders will decide to skip
        buffers based on timestamp. Custom `Renderer` implementations should
        check if the buffer time is at least
        `BaseRenderer.getLastResetPositionUs()` to decide whether a sample
        should be shown. Custom `SimpleDecoder` implementations can check
        `isAtLeastOutputStartTimeUs()` if needed or mark other buffers with
        `DecoderOutputBuffer.shouldBeSkipped` to skip them.
    *   Allow a null value to be returned by
        `TargetPreloadStatusControl.getTargetPreloadStatus(T)` to indicate not
        to preload a `MediaSource` with the given `rankingData`.
    *   Add `remove(MediaSource)` to `BasePreloadManager`.
    *   Add `reset()` to `BasePreloadManager` to release all the holding sources
        while keep the preload manager instance.
    *   Add `ExoPlayer.setPriority()` (and `Builder.setPriority()`) to define
        the priority value used in `PriorityTaskManager` and for MediaCodec
        importance from API 35.
    *   Fix issue with updating the last rebuffer time which resulted in
        incorrect `bs` (buffer starvation) key in CMCD
        ([#1124](https://github.com/androidx/media/issues/1124)).
    *   Add
        `PreloadMediaSource.PreloadControl.onLoadedToTheEndOfSource(PreloadMediaSource)`
        to indicate that the source has loaded to the end. This allows the
        `DefaultPreloadManager` and the custom
        `PreloadMediaSource.PreloadControl` implementations to preload the next
        source or take other actions.
    *   Fix bug where silence skipping at the end of items can trigger a
        playback exception.
    *   Add `clear` to `PreloadMediaSource` to discard the preloading period.
    *   Add new error code
        `PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED` that is used
        when codec resources are reclaimed for higher priority tasks.
    *   Let `AdsMediaSource` load preroll ads before initial content media
        preparation completes
        ([#1358](https://github.com/androidx/media/issues/1358)).
    *   Fix bug where playback moved to `STATE_ENDED` when re-preparing a
        multi-period DASH live stream after the original period was already
        removed from the manifest.
    *   Rename `onTimelineRefreshed()` to `onSourcePrepared()` and
        `onPrepared()` to `onTracksSelected()` in
        `PreloadMediaSource.PreloadControl`. Also rename the IntDefs in
        `DefaultPreloadManager.Stage` accordingly.
    *   Add experimental support for dynamic scheduling to better align work
        with CPU wake-cycles and delay waking up to when renderers can progress.
        You can enable this using `experimentalSetDynamicSchedulingEnabled()`
        when setting up your ExoPlayer instance.
    *   Add `Renderer.getDurationToProgressUs()`. A `Renderer` can implement
        this method to return to ExoPlayer the duration that playback must
        advance for the renderer to progress. If `ExoPlayer` is set with
        `experimentalSetDynamicSchedulingEnabled()` then `ExoPlayer` will call
        this method when calculating the time to schedule its work task.
    *   Add `MediaCodecAdapter#OnBufferAvailableListener` to alert when input
        and output buffers are available for use by `MediaCodecRenderer`.
        `MediaCodecRenderer` will signal `ExoPlayer` when receiving these
        callbacks and if `ExoPlayer` is set with
        `experimentalSetDynamicSchedulingEnabled()`, then `ExoPlayer` will
        schedule its work loop as renderers can make progress.
    *   Use data class for `LoadControl` methods instead of individual
        parameters.
    *   Add `ExoPlayer.isReleased()` to check whether `Exoplayer.release()` has
        been called.
    *   Add `ExoPlayer.Builder.setMaxSeekToPreviousPositionMs()` to configure
        the maximum position for which `seekToPrevious()` seeks to the previous
        item ([#1425](https://github.com/androidx/media/issues/1425)).
    *   Fix some audio focus inconsistencies, e.g. not reporting full or
        transient focus loss while the player is paused
        ([#1436](https://github.com/androidx/media/issues/1436)).
    *   Fix potential `IndexOutOfBoundsException` caused by extractors reporting
        additional tracks after the initial preparation step
        ([#1476](https://github.com/androidx/media/issues/1476)).
    *   `Effects` in `ExoPlayer.setVideoEffect()` will receive the timestamps
        with the renderer offset removed
        ([#1098](https://github.com/androidx/media/issues/1098)).
    *   Fix potential `IllegalArgumentException` when handling player error that
        happened while reading ahead into another playlist item
        ([#1483](https://github.com/androidx/media/issues/1483)).
*   Transformer:
    *   Add `audioConversionProcess` and `videoConversionProcess` to
        `ExportResult` indicating how the respective track in the output file
        was made.
    *   Relax trim optimization H.264 level checks.
    *   Add support for changing between SDR and HDR input media in a sequence.
    *   Add support for composition-level audio effects.
    *   Add support for transcoding Ultra HDR images into HDR videos.
    *   Fix issue where the `DefaultAudioMixer` does not output the correct
        amount of bytes after being reset and reused.
    *   Work around a decoder bug where the number of audio channels was capped
        at stereo when handling PCM input.
    *   When selecting tracks in `ExoPlayerAssetLoader`, ignore audio channel
        count constraints as they only apply for playback.
    *   Replace `androidx.media3.transformer.Muxer` interface with
        `androidx.media3.muxer.Muxer` and remove
        `androidx.media3.transformer.Muxer`.
    *   Fix HEIC image loading from content URI schemes.
        ([#1373](https://github.com/androidx/media/issues/1373)).
    *   Adjust audio track duration in `AudioGraphInput` to improve AV sync.
    *   Remove `ExportResult.processedInputs` field. If you use this field for
        codec details, then use `DefaultDecoderFactory.listener` instead. In
        case of a codec exception, codec details will be available in the
        `ExportException.codecInfo`.
*   Extractors:
    *   MPEG-TS: Roll forward the change ensuring the last frame is rendered by
        passing the last access unit of a stream to the sample queue
        ([#7909](https://github.com/google/ExoPlayer/issues/7909)).
        Incorporating fixes to resolve the issues that emerged in I-frame only
        HLS streams([#1150](https://github.com/androidx/media/issues/1150)) and
        H.262 HLS streams
        ([#1126](https://github.com/androidx/media/issues/1126)).
    *   MP3: Prefer the data size from an `Info` frame over the size reported by
        the underlying stream (e.g. file size, or HTTP `Content-Length` header).
        This helps to exclude non-playable trailer data (e.g. album artwork)
        from constant bitrate seeking calculations, making seeks more accurate
        ([#1376](https://github.com/androidx/media/issues/1376)).
    *   MP3: Use the frame count and other data in an `Info` frame (if present)
        to compute an average bitrate for constant bitrate seeking, rather than
        extrapolating from the bitrate of the frame after the `Info` frame,
        which may be artificially small, e.g. `PCUT` frame
        ([#1376](https://github.com/androidx/media/issues/1376)).
    *   Fix PCM audio format extraction in AVI containers.
*   Audio:
    *   Fix DTS:X Profile 2 encoding attributes for passthrough playback
        ([#1299](https://github.com/androidx/media/pull/1299)).
    *   For offloaded playback, reset the tracking field for stream completion
        in `DefaultAudioSink` prior to calling `AudioTrack.stop()` so that
        `AudioTrack.StreamEventCallback#onPresentationEnded` correctly
        identifies when all pending data has been played.
    *   Fix bug in `SilenceSkippingAudioProcessor` where transitions between
        different audio formats (for example stereo to mono) can cause the
        processor to throw an exception
        ([#1352](https://github.com/androidx/media/issues/1352)).
    *   Implement `MediaCodecAudioRenderer.getDurationToProgressUs()` so that
        ExoPlayer will dynamically schedule its main work loop to when the
        MediaCodecAudioRenderer can make progress.
*   Video:
    *   Fix issue where `Listener.onRenderedFirstFrame()` arrives too early when
        switching surfaces mid-playback.
    *   Fix decoder fallback logic for Dolby Vision to use a compatible AV1
        decoder if needed
        ([#1389](https://github.com/androidx/media/pull/1389)).
    *   Fix codec exception that may be caused by enabling a video renderer
        mid-playback.
*   Text:
    *   Fix issue where subtitles starting before a seek position are skipped.
        This issue was only introduced in Media3 1.4.0-alpha01.
    *   Change default subtitle parsing behavior so it happens during extraction
        instead of during rendering (see
        [ExoPlayer's architecture diagram](https://developer.android.com/media/media3/exoplayer/glossary#exoplayer)
        for the difference between extraction and rendering).
        *   This change can be overridden by calling **both**
            `MediaSource.Factory.experimentalParseSubtitlesDuringExtraction(false)`
            and `TextRenderer.experimentalSetLegacyDecodingEnabled(true)`. See
            the
            [docs on customization](https://developer.android.com/media/media3/exoplayer/customization)
            for how to plumb these components into an `ExoPlayer` instance.
            These methods (and all support for legacy subtitle decoding) will be
            removed in a future release.
        *   Apps with custom `SubtitleDecoder` implementations need to update
            them to implement `SubtitleParser` instead (and
            `SubtitleParser.Factory` instead of `SubtitleDecoderFactory`).
    *   PGS: Fix run-length decoding to resolve `0` as a color index, instead of
        a literal color value
        ([#1367](https://github.com/androidx/media/pull/1367)).
    *   CEA-708: Ignore `rowLock` value. The CEA-708-E S-2023 spec states that
        `rowLock` and `columnLock` should both be assumed to be true, regardless
        of the values present in the stream (`columnLock` support is not
        implemented, so it's effectively assumed to always be false).
        *   This was originally included in the `1.3.0-alpha01` release notes,
            but the change was accidentally reverted before the `1.3.0-rc01`
            release. This is now fixed, so the change is present again.
    *   CEA-708: Avoid duplicate newlines being added by ExoPlayer's naive
        handling of the 'set pen location' command
        ([#1315](https://github.com/androidx/media/pull/1315)).
    *   Fix an `IllegalArgumentException` from `LegacySubtitleUtil` when a
        WebVTT subtitle sample contains no cues, e.g. as part of a DASH stream
        ([#1516](https://github.com/androidx/media/issues/1516)).
*   Metadata:
    *   Fix mapping of MP4 to ID3 sort tags. Previously the 'album sort'
        (`soal`), 'artist sort' (`soar`) and 'album artist sort' (`soaa`) MP4
        tags were wrongly mapped to the `TSO2`, `TSOA` and `TSOP` ID3 tags
        ([#1302](https://github.com/androidx/media/issues/1302)).
    *   Fix reading of MP4 (/iTunes) numeric `gnre` (genre) and `tmpo` (tempo)
        tags when the value is more than one byte long.
    *   Propagate ID3 `TCON` frame to `MediaMetadata.genre`
        ([#1305](https://github.com/androidx/media/issues/1305)).
*   Image:
    *   Add support for non-square DASH thumbnail grids
        ([#1300](https://github.com/androidx/media/pull/1300)).
    *   Add support for AVIF for API 34+.
    *   Allow `null` as parameter for `ExoPlayer.setImageOutput()` to clear a
        previously set `ImageOutput`.
*   DataSource:
    *   Implement support for `android.resource://package/id` raw resource URIs
        where `package` is different to the package of the current application.
        This wasn't previously documented to work, but is a more efficient way
        of accessing resources in another package than by name.
    *   Eagerly check `url` is non-null in the `DataSpec` constructors. This
        parameter was already annotated to be non-null.
    *   Allow `ByteArrayDataSource` to resolve a URI to a byte array during
        `open()`, instead of being hard-coded at construction
        ([#1405](https://github.com/androidx/media/issues/1405)).
*   DRM:
    *   Allow setting a `LoadErrorHandlingPolicy` on
        `DefaultDrmSessionManagerProvider`
        ([#1271](https://github.com/androidx/media/issues/1271)).
*   Effect:
    *   Support multiple speed changes within the same `EditedMediaItem` or
        `Composition` in `SpeedChangeEffect`.
    *   Support for HLG and PQ output from ultra HDR bitmap input.
    *   Add support for EGL_GL_COLORSPACE_BT2020_HLG_EXT, which improves HLG
        surface output in ExoPlayer.setVideoEffect and Transformer's Debug
        SurfaceView.
    *   Update Overlay matrix implementation to make it consistent with the
        documentation by flipping the x and y values applied in
        `setOverlayFrameAnchor()`. If using
        `OverlaySettings.Builder.setOverlayFrameAnchor()`, flip their x and y
        values by multiplying them by `-1`.
    *   Fix bug where `TimestampWrapper` crashes when used with
        `ExoPlayer#setVideoEffects`
        ([#821](https://github.com/androidx/media/issues/821)).
    *   Change default SDR color working space from linear colors to electrical
        BT 709 SDR video. Also provide third option to retain the original
        colorspace.
    *   Allow defining indeterminate z-order of EditedMediaItemSequences
        ([#1055](https://github.com/androidx/media/pull/1055)).
    *   Maintain a consistent luminance range across different pieces of HDR
        content (uses the HLG range).
    *   Add support for Ultra HDR (bitmap) overlays on HDR content.
    *   Allow `SeparableConvolution` effects to be used before API 26.
    *   Remove unused `OverlaySettings.useHdr` since dynamic range of overlay
        and frame must match.
    *   Add HDR support for `TextOverlay`. Luminance of the text overlay can be
        adjusted with `OverlaySettings.Builder.setHdrLuminanceMultiplier()`.
*   IMA extension:
    *   Promote API that is required for apps to play
        [DAI ad streams](https://developers.google.com/ad-manager/dynamic-ad-insertion/full-service)
        to stable.
    *   Add `replaceAdTagParameters(Map <String, String>)` to
        `ImaServerSideAdInsertionMediaSource.AdLoader` that allows replacing ad
        tag parameters at runtime.
    *   Fix bug where `VideoAdPlayer.VideoAdPlayerCallback.onError()` was not
        called when a player error happened during ad playback
        ([#1334](https://github.com/androidx/media/issues/1334)).
    *   Bump IMA SDK version to 3.33.0 to fix a `NullPointerException` when
        using `data://` ad tag URIs
        ([#700](https://github.com/androidx/media/issues/700)).
*   Session:
    *   Change default of `CommandButton.enabled` to `true` and ensure the value
        can stay false for controllers even if the associated command is
        available.
    *   Add icon constants for `CommandButton` that should be used instead of
        custom icon resources.
    *   Add `MediaSessionService.isPlaybackOngoing()` to let apps query whether
        the service needs to be stopped in `onTaskRemoved()`
        ([#1219](https://github.com/androidx/media/issues/1219)).
    *   Add `MediaSessionService.pauseAllPlayersAndStopSelf()` that conveniently
        allows to pause playback of all sessions and call `stopSelf()` to
        terminate the lifecycle of the `MediaSessionService`.
    *   Override `MediaSessionService.onTaskRemoved(Intent)` to provide a safe
        default implementation that keeps the service running in the foreground
        if playback is ongoing or stops the service otherwise.
    *   Hide seekbar in the media notification for live streams by not setting
        the duration into the platform session metadata
        ([#1256](https://github.com/androidx/media/issues/1256)).
    *   Align conversion of `MediaMetadata` to `MediaDescriptionCompat`, to use
        the same preferred order and logic when selecting metadata properties as
        in media1.
    *   Add `MediaSession.sendError()` that allows sending non-fatal errors to
        Media3 controller. When using the notification controller (see
        `MediaSession.getMediaNotificationControllerInfo()`), the custom error
        is used to update the `PlaybackState` of the platform session to an
        error state with the given error information
        ([#543](https://github.com/androidx/media/issues/543)).
    *   Add `MediaSession.Callback.onPlayerInteractionFinished()` to inform
        sessions when a series of player interactions from a specific controller
        finished.
    *   Add `SessionError` and use it in `SessionResult` and `LibraryResult`
        instead of the error code to provide more information about the error
        and how to resolve the error if possible.
    *   Publish the code for the media3 controller test app that can be used to
        test interactions with apps publishing a media session.
    *   Propagate extras passed to media3's
        `MediaSession[Builder].setSessionExtras()` to a media1 controller's
        `PlaybackStateCompat.getExtras()`.
    *   Map fatal and non-fatal errors to and from the platform session. A
        `PlaybackException` is mapped to a fatal error state of the
        `PlaybackStateCompat`. A `SessionError` sent to the media notification
        controller with `MediaSession.sendError(ControllerInfo, SessionError)`
        is mapped to a non-fatal error in `PlaybackStateCompat` which means that
        error code and message are set but the state of the platform session
        remains different to `STATE_ERROR`.
    *   Allow the session activity to be set per controller to override the
        global session activity. The session activity can be defined for a
        controller at connection time by creating a `ConnectionResult` with
        `AcceptedResultBuilder.setSessionActivivty(PendingIntent)`. Once
        connected, the session activity can be updated with
        `MediaSession.setSessionActivity(ControllerInfo, PendingIntent)`.
    *   Improve error replication of calls to `MediaLibrarySession.Callback`.
        Error replication can now be configured by using
        `MediaLibrarySession.Builder.setLibraryErrorReplicationMode()` for
        choosing the error type or opt-ing out of error replication which is on
        by default.
*   UI:
    *   Add image display support to `PlayerView` when connected to an
        `ExoPlayer` ([#1144](https://github.com/androidx/media/issues/1144)).
    *   Add customization of various icons in `PlayerControlView` through xml
        attributes to allow different drawables per `PlayerView` instance,
        rather than global overrides
        ([#1200](https://github.com/androidx/media/issues/1200)).
    *   Work around a platform bug causing stretched/cropped video when using
        `SurfaceView` inside a Compose `AndroidView` on API 34
        ([#1237](https://github.com/androidx/media/issues/1237)).
*   Downloads:
    *   Ensure that `DownloadHelper` does not leak unreleased `Renderer`
        instances, which can eventually result in an app crashing with
        `IllegalStateException: Too many receivers, total of 1000, registered
        for pid` ([#1224](https://github.com/androidx/media/issues/1224)).
*   Cronet extension:
    *   Fix `SocketTimeoutException` in `CronetDataSource`. In some versions of
        Cronet, the request provided by the callback is not always the same.
        This leads to callback not completing and request timing out
        (https://issuetracker.google.com/328442628).
*   HLS extension:
    *   Fix bug where pending EMSG samples waiting for a discontinuity were
        delegated in `HlsSampleStreamWrapper` with an incorrect offset causing
        an `IndexOutOfBoundsException` or an `IllegalArgumentException`
        ([#1002](https://github.com/androidx/media/issues/1002)).
    *   Fix bug where non-primary playlists keep reloading for LL-HLS streams
        ([#1240](https://github.com/androidx/media/issues/1240)).
    *   Fix bug where enabling CMCD for HLS with initialization segments
        resulted in `Source Error` and `IllegalArgumentException`.
    *   Fix bug where non-primary playing playlists are not refreshed during
        live playback ([#1240](https://github.com/androidx/media/issues/1240)).
    *   Fix bug where enabling CMCD for HLS live streams causes
        `ArrayIndexOutOfBoundsException`
        ([#1395](https://github.com/androidx/media/issues/1395)).
*   DASH extension:
    *   Fix bug where re-preparing a multi-period live stream can throw an
        `IndexOutOfBoundsException`
        ([#1329](https://github.com/androidx/media/issues/1329)).
    *   Add support for `dashif:Laurl` license urls
        ([#1345](https://github.com/androidx/media/issues/1345)).
*   Cast extension:
    *   Fix bug that converted the album title of the `MediaQueueItem` to the
        artist in the Media3 media item
        ([#1255](https://github.com/androidx/media/pull/1255)).
*   Test Utilities:
    *   Implement `onInit()` and `onRelease()` in `FakeRenderer`.
    *   Change `TestPlayerRunHelper.runUntil()/playUntil()` methods to fail on
        nonfatal errors (e.g. those reported to
        `AnalyticsListener.onVideoCodecError()`). Use the new
        `TestPlayerRunHelper.run(player).ignoringNonFatalErrors().untilXXX()`
        method chain to disable this behavior.
*   Demo app:
    *   Use `DefaultPreloadManager` in the short form demo app.
    *   Allow setting repeat mode with `Intent` arguments from command line
        ([#1266](https://github.com/androidx/media/pull/1266)).
    *   Use `HttpEngineDataSource` as the `HttpDataSource` when supported by the
        device.
*   Remove deprecated symbols:
    *   Remove `CronetDataSourceFactory`. Use `CronetDataSource.Factory`
        instead.
    *   Remove some `DataSpec` constructors. Use `DataSpec.Builder` instead.
    *   Remove `setContentTypePredicate(Predicate)` method from
        `DefaultHttpDataSource`, `OkHttpDataSource` and `CronetDataSource`. Use
        the equivalent method on each `XXXDataSource.Factory` instead.
    *   Remove `OkHttpDataSource` constructors and `OkHttpDataSourceFactory`.
        Use `OkHttpDataSource.Factory` instead.
    *   Remove `PlayerMessage.setHandler(Handler)`. Use `setLooper(Looper)`
        instead.
    *   Remove `Timeline.Window.isLive` field. Use the `isLive()` method
        instead.
    *   Remove `DefaultHttpDataSource` constructors. Use
        `DefaultHttpDataSource.Factory` instead.
    *   Remove `DashMediaSource.DEFAULT_LIVE_PRESENTATION_DELAY_MS`. Use
        `DashMediaSource.DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS` instead.
    *   Remove `MediaCodecInfo.isSeamlessAdaptationSupported(Format, Format,
        boolean)`. Use `MediaCodecInfo.canReuseCodec(Format, Format)` instead.
    *   Remove `DrmSessionManager.DUMMY` and `getDummyDrmSessionManager()`
        method. Use `DrmSessionManager.DRM_UNSUPPORTED` instead.
    *   Remove `AnalyticsListener.onAudioInputFormatChanged(EventTime, Format)`,
        `AnalyticsListener.onVideoInputFormatChanged(EventTime, Format)`,
        `AudioRendererEventListener.onAudioInputFormatChanged(Format)`,
        `VideoRendererEventListener.onVideoInputFormatChanged(Format)`. Use the
        overloads that take a `DecoderReuseEvaluation` instead.
    *   Remove `RendererSupport.FormatSupport` IntDef and `FORMAT_HANDLED`,
        `FORMAT_EXCEEDS_CAPABILITIES`, `FORMAT_UNSUPPORTED_DRM`,
        `FORMAT_UNSUPPORTED_SUBTYPE`, `FORMAT_UNSUPPORTED_TYPE` constants. Use
        the equivalent IntDef and constants in `androidx.media3.common.C`
        instead (e.g. `C.FORMAT_HANDLED`).
    *   Remove `Bundleable` interface. This includes removing all
        `Bundleable.Creator<Foo> CREATOR` constant fields. Callers should use
        the `Bundle toBundle()` and `static Foo fromBundle(Bundle)` methods on
        each type instead.

### 1.4.0-rc01 (2024-07-11)

Use the 1.4.0 [stable version](#140-2024-07-24).

### 1.4.0-beta01 (2024-06-21)

Use the 1.4.0 [stable version](#140-2024-07-24).

### 1.4.0-alpha02 (2024-06-06)

Use the 1.4.0 [stable version](#140-2024-07-24).

### 1.4.0-alpha01 (2024-04-11)

Use the 1.4.0 [stable version](#140-2024-07-24).

## 1.3

### 1.3.1 (2024-04-11)

This release includes the following changes since the
[1.3.0 release](#130-2024-03-06):

*   Common Library:
    *   Add `Format.labels` to allow localized or other alternative labels.
*   ExoPlayer:
    *   Fix issue where `PreloadMediaPeriod` cannot retain the streams when it
        is preloaded again.
    *   Apply the correct corresponding `TrackSelectionResult` to the playing
        period in track reselection.
    *   Start early-enabled renderers only after advancing the playing period
        when transitioning between media items
        ([#1017](https://github.com/androidx/media/issues/1017)).
    *   Add missing return type to proguard `-keepclasseswithmembers` rule for
        `DefaultVideoFrameProcessor.Factory.Builder.build()`
        ([#1187](https://github.com/androidx/media/issues/1187)).
*   Transformer:
    *   Add workaround for exception thrown due to `MediaMuxer` not supporting
        negative presentation timestamps before API 30.
*   Track Selection:
    *   `DefaultTrackSelector`: Prefer video tracks with a 'reasonable' frame
        rate (>=10fps) over those with a lower or unset frame rate. This ensures
        the player selects the 'real' video track in MP4s extracted from motion
        photos that can contain two HEVC tracks where one has a higher
        resolution but a very small number of frames
        ([#1051](https://github.com/androidx/media/issues/1051)).
*   Extractors:
    *   Fix issue where padding was not skipped when reading odd-sized chunks
        from WAV files ([#1117](https://github.com/androidx/media/pull/1117)).
    *   MP3: Populate `Format.averageBitrate` from metadata frames such as
        `XING` and `VBRI`.
    *   MPEG-TS: Revert a change that aimed to ensure the last frame is rendered
        by passing the last access unit of a stream to the sample queue
        ([#7909](https://github.com/google/ExoPlayer/issues/7909)). This is due
        to the change causing new problems with I-frame only HLS streams
        ([#1150](https://github.com/androidx/media/issues/1150)) and H.262 HLS
        streams ([#1126](https://github.com/androidx/media/issues/1126)).
*   Audio:
    *   Allow renderer recovery by disabling offload if audio track fails to
        initialize in offload mode.
    *   For offloaded playback, use the `AudioTrack.StreamEventCallback` method
        `onPresentationEnded` to identify when all pending data has been played.
*   Video:
    *   Add workaround for a device issue on Galaxy Tab S7 FE, Chromecast with
        Google TV, and Lenovo M10 FHD Plus that causes 60fps H265 streams to be
        marked as unsupported
    *   Add workaround that ensures the first frame is always rendered while
        tunneling even if the device does not do this automatically as required
        by the API ([#1169](https://github.com/androidx/media/issues/1169)).
        ([#966](https://github.com/androidx/media/issues/966)).
    *   Fix issue where HDR color info handling causes codec misbehavior and
        prevents adaptive format switches for SDR video tracks
        ([#1158](https://github.com/androidx/media/issues/1158)).
*   Text:
    *   WebVTT: Prevent directly consecutive cues from creating spurious
        additional `CuesWithTiming` instances from `WebvttParser.parse`
        ([#1177](https://github.com/androidx/media/issues/1177)).
*   DRM:
    *   Work around a `NoSuchMethodError` which can be thrown by the `MediaDrm`
        framework instead of `ResourceBusyException` or
        `NotProvisionedException` on some Android 14 devices
        ([#1145](https://github.com/androidx/media/issues/1145)).
*   Effect:
    *   Improved PQ to SDR tone-mapping by converting color spaces.
*   Session:
    *   Fix issue where the current position jumps back when the controller
        replaces the current item
        ([#951](https://github.com/androidx/media/issues/951)).
    *   Fix issue where `MediaMetadata` with just non-null `extras` is not
        transmitted between media controllers and sessions
        ([#1176](https://github.com/androidx/media/issues/1176)).
*   UI:
    *   Fallback to include audio track language name if `Locale` cannot
        identify a display name
        ([#988](https://github.com/androidx/media/issues/988)).
*   DASH extension:
    *   Populate all `Label` elements from the manifest into `Format.labels`
        ([#1054](https://github.com/androidx/media/pull/1054)).
*   RTSP extension:
    *   Skip empty session information values (i-tags) in SDP parsing
        ([#1087](https://github.com/androidx/media/issues/1087)).
*   Decoder extensions (FFmpeg, VP9, AV1, MIDI, etc.):
    *   Disable the MIDI extension as a local dependency by default because it
        requires an additional Maven repository to be configured. Users who need
        this module from a local dependency
        [can re-enable it](https://github.com/androidx/media/blob/main/README.md#midi-module).

### 1.3.0 (2024-03-06)

This release includes the following changes since the
[1.2.1 release](#121-2024-01-09):

*   Common Library:
    *   Implement support for `android.resource://package/[type/]name` raw
        resource URIs where `package` is different to the package of the current
        application. This has always been documented to work, but wasn't
        correctly implemented until now.
    *   Normalize MIME types set by app code or read from media to be fully
        lower-case.
    *   Define ads with a full `MediaItem` instead of a single `Uri` in
        `AdPlaybackState`.
    *   Increase `minSdk` to 19 (Android KitKat). This is
        [aligned with all other AndroidX libraries](https://android-developers.googleblog.com/2023/10/androidx-minsdkversion-19.html),
        and is required for us to upgrade to the latest versions of our AndroidX
        dependencies.
    *   Populate both `artworkUri` and `artworkData` in
        `MediaMetadata.Builder.populate(MediaMetadata)` when at least one of
        them is non-null ([#964](https://github.com/androidx/media/issues/964)).
*   ExoPlayer:
    *   Add `PreloadMediaSource` and `PreloadMediaPeriod` that allows apps to
        preload a content media source at a specific start position before
        playback. `PreloadMediaSource` takes care of preparing the content media
        source to receive the `Timeline`, preparing and caching the period at
        the given start position, selecting tracks and loading media data for
        the period. Apps control the preload progress by implementing
        `PreloadMediaSource.PreloadControl` and set the preloaded source to the
        player for playback.
    *   Add `ExoPlayer.setImageOutput` that allows apps to set
        `ImageRenderer.ImageOutput`.
    *   `DefaultRenderersFactory` now provides an `ImageRenderer` to the player
        by default with null `ImageOutput` and `ImageDecoder.Factory.DEFAULT`.
    *   Emit `Player.Listener.onPositionDiscontinuity` event when silence is
        skipped ([#765](https://github.com/androidx/media/issues/765)).
    *   Add experimental support for parsing subtitles during extraction. You
        can enable this using
        `MediaSource.Factory.experimentalParseSubtitlesDuringExtraction()`.
    *   Support adaptive media sources with `PreloadMediaSource`.
    *   Implement `HttpEngineDataSource`, an `HttpDataSource` using the
        [HttpEngine](https://developer.android.com/reference/android/net/http/HttpEngine)
        API.
    *   Prevent subclassing `CompositeSequenceableLoader`. This component was
        [previously made extensible](https://github.com/androidx/media/commit/0de57cbfae7165dd3bb829e323d089cd312b4b1b)
        but was never subclassed within the library. Customizations can be done
        by wrapping an instance using the
        [decorator pattern](https://en.wikipedia.org/wiki/Decorator_pattern) and
        implementing a custom `CompositeSequenceableLoaderFactory`.
    *   Fix issue where repeating the same time causes metadata from this item
        to be cleared ([#1007](https://github.com/androidx/media/issues/1007)).
    *   Rename `experimentalSetSubtitleParserFactory` methods on
        `BundledChunkExtractor.Factory` and `DefaultHlsExtractorFactory` to
        `setSubtitleParserFactory` and disallow passing `null`. Use the new
        `experimentalParseSubtitlesDuringExtraction(boolean)` methods to control
        parsing behaviour.
    *   Add support for customising the `SubtitleParser.Factory` used during
        extraction. This can be achieved with
        `MediaSource.Factory.setSubtitleParserFactory()`.
    *   Add source prefix to all `Format.id` fields generated from
        `MergingMediaSource`. This helps to identify which source produced a
        `Format` ([#883](https://github.com/androidx/media/issues/883)).
    *   Fix the regex used for validating custom Common Media Client Data (CMCD)
        key names by modifying it to only check for hyphen
        ([#1028](https://github.com/androidx/media/issues/1028)).
    *   Stop double-encoding CMCD query parameters
        ([#1075](https://github.com/androidx/media/issues/1075)).
*   Transformer:
    *   Add support for flattening H.265/HEVC SEF slow motion videos.
    *   Increase transmuxing speed, especially for 'remove video' edits.
    *   Add API to ensure that the output file starts on a video frame. This can
        make the output of trimming operations more compatible with player
        implementations that don't show the first video frame until its
        presentation timestamp
        ([#829](https://github.com/androidx/media/issues/829)).
    *   Add support for optimizing single asset mp4 trim operations.
    *   Add support to ensure a video frame has the first timestamp in the
        output file. Fixes output files beginning with black frame on iOS based
        players ([#829](https://github.com/androidx/media/issues/829)).
*   Track Selection:
    *   Add `DefaultTrackSelector.selectImageTrack` to enable image track
        selection.
    *   Add `TrackSelectionParameters.isPrioritizeImageOverVideoEnabled` to
        determine whether to select an image track if both an image track and a
        video track are available. The default value is `false` which means
        selecting a video track is prioritized.
*   Extractors:
    *   Add additional AV1C parsing to MP4 extractor to retrieve
        `ColorInfo.colorSpace`, `ColorInfo.colorTransfer`, and
        `ColorInfo.colorRange` values
        ([#692](https://github.com/androidx/media/pull/692)).
    *   MP3: Use constant bitrate (CBR) seeking for files with an `Info` header
        (the CBR equivalent of the `Xing` header). Previously we used the seek
        table from the `Info` header, but this results in less precise seeking
        than if we ignore it and assume the file is CBR.
    *   MPEG2-TS: Add DTS, DTS-LBR and DTS:X Profile2 support
        ([#275](https://github.com/androidx/media/pull/275)).
    *   Extract audio types from TS descriptors and map them to role flags,
        allowing users to make better-informed audio track selections
        ([#973](https://github.com/androidx/media/pull/973)).
*   Audio:
    *   Improve silence skipping algorithm with smooth volume ramp; retained
        minimal silence and more natural silence durations
        ([#7423](https://github.com/google/ExoPlayer/issues/7423)).
    *   Report the skipped silence more deterministically
        ([#1035](https://github.com/androidx/media/issues/1035)).
*   Video:
    *   Change the `MediaCodecVideoRenderer` constructor that takes a
        `VideoFrameProcessor.Factory` argument and replace it with a constructor
        that takes a `VideoSinkProvider` argument. Apps that want to inject a
        custom `VideoFrameProcessor.Factory` can instantiate a
        `CompositingVideoSinkProvider` that uses the custom
        `VideoFrameProcessor.Factory` and pass the video sink provider to
        `MediaCodecVideoRenderer`.
*   Text:
    *   Fix serialization of bitmap cues to resolve `Tried to marshall a Parcel
        that contained Binder objects` error when using
        `DefaultExtractorsFactory.setTextTrackTranscodingEnabled`
        ([#836](https://github.com/androidx/media/issues/836)).
    *   CEA-708: Ignore `rowLock` value. The CEA-708-E S-2023 spec states that
        `rowLock` and `columnLock` should both be assumed to be true, regardless
        of the values present in the stream (`columnLock` support is not
        implemented, so it's effectively assumed to always be false).
*   Image:
    *   Add support for DASH thumbnails. Grid images are cropped and individual
        thumbnails are provided to `ImageOutput` close to their presentation
        times.
*   DRM:
    *   Play 'clear lead' unencrypted samples in DRM content immediately by
        default, even if the keys for the later encrypted samples aren't ready
        yet. This may lead to mid-playback stalls if the keys still aren't ready
        when the playback position reaches the encrypted samples (but previously
        playback wouldn't have started at all by this point). This behavior can
        be disabled with
        [`MediaItem.DrmConfiguration.Builder.setPlayClearContentWithoutKey`](https://developer.android.com/reference/androidx/media3/common/MediaItem.DrmConfiguration.Builder#setPlayClearContentWithoutKey\(boolean\))
        or
        [`DefaultDrmSessionManager.Builder.setPlayClearSamplesWithoutKeys`](https://developer.android.com/reference/androidx/media3/exoplayer/drm/DefaultDrmSessionManager.Builder#setPlayClearSamplesWithoutKeys\(boolean\)).
*   IMA extension:
    *   Fix issue where DASH and HLS ads without the appropriate file extension
        can't be played.
*   Session:
    *   Disable double-click detection for TV apps
        ([#962](https://github.com/androidx/media/issues/962)).
    *   Fix issue where `MediaItem.RequestMetadata` with just non-null extras is
        not transmitted between media controllers and sessions.
    *   Add constructor to `MediaLibrarySession.Builder` that only takes a
        `Context` instead of a `MediaLibraryService`.
*   HLS extension:
    *   Reduce `HlsMediaPeriod` to package-private visibility. This type
        shouldn't be directly depended on from outside the HLS package.
    *   Resolve seeks to beginning of a segment more efficiently
        ([#1031](https://github.com/androidx/media/pull/1031)).
*   Decoder extensions (FFmpeg, VP9, AV1, MIDI, etc.):
    *   MIDI decoder: Ignore SysEx event messages
        ([#710](https://github.com/androidx/media/pull/710)).
*   Test Utilities:
    *   Don't pause playback in `TestPlayerRunHelper.playUntilPosition`. The
        test keeps the playback in a playing state, but suspends progress until
        the test is able to add assertions and further actions.
*   Demo app:
    *   Add a shortform demo module to demo the usage of `PreloadMediaSource`
        with the short-form content use case.

### 1.3.0-rc01 (2024-02-22)

Use the 1.3.0 [stable version](#130-2024-03-06).

### 1.3.0-beta01 (2024-02-07)

Use the 1.3.0 [stable version](#130-2024-03-06).

### 1.3.0-alpha01 (2024-01-15)

Use the 1.3.0 [stable version](#130-2024-03-06).

## 1.2

### 1.2.1 (2024-01-09)

This release includes the following changes since the
[1.2.0 release](#120-2023-11-15):

*   ExoPlayer:
    *   Fix issue where manual seeks outside of the
        `LiveConfiguration.min/maxOffset` range keep adjusting the offset back
        to `min/maxOffset`.
    *   Fix issue that OPUS and VORBIS channel layouts are wrong for 3, 5, 6, 7
        and 8 channels
        ([#8396](https://github.com/google/ExoPlayer/issues/8396)).
    *   Fix issue where track selections after seek to zero in a live stream
        incorrectly let the stream start at its default position
        ([#9347](https://github.com/google/ExoPlayer/issues/9347)).
    *   Fix the issue where new instances of `CmcdData.Factory` were receiving
        negative values for `bufferedDurationUs` from chunk sources, resulting
        in an `IllegalArgumentException`
        ([#888](https://github.com/androidx/media/issues/888)).
*   Transformer:
    *   Work around an issue where the encoder would throw at configuration time
        due to setting a high operating rate.
*   Extractors:
    *   Mark secondary (unplayable) HEVC tracks in JPEG motion photos as
        `ROLE_FLAG_ALTERNATE` to prevent them being automatically selected for
        playback because of their higher resolution.
    *   Fix wrong keyframe detection for TS H264 streams
        ([#864](https://github.com/androidx/media/pull/864)).
    *   Fix duration estimation of TS streams that are longer than 47721 seconds
        ([#855](https://github.com/androidx/media/issues/855)).
*   Audio:
    *   Fix handling of EOS for `SilenceSkippingAudioProcessor` when called
        multiple times ([#712](https://github.com/androidx/media/issues/712)).
*   Video:
    *   Add workaround for a device issue on Galaxy Tab S7 FE, Chromecast with
        Google TV, and Lenovo M10 FHD Plus that causes 60fps AVC streams to be
        marked as unsupported
        ([#693](https://github.com/androidx/media/issues/693)).
*   Metadata:
    *   Fix bug where `MediaMetadata` was only populated from Vorbis comments
        with upper-case keys
        ([#876](https://github.com/androidx/media/issues/876)).
    *   Catch `OutOfMemoryError` when parsing very large ID3 frames, meaning
        playback can continue without the tag info instead of playback failing
        completely.
*   DRM:
    *   Extend workaround for spurious ClearKey `https://default.url` license
        URL to API 33+ (previously the workaround only applied on API 33
        exactly) ([#837](https://github.com/androidx/media/pull/837)).
    *   Fix `ERROR_DRM_SESSION_NOT_OPENED` when switching from encrypted to
        clear content without a surface attached to the player. The error was
        due to incorrectly using a secure decoder to play the clear content.
*   Session:
    *   Put the custom keys and values in `MediaMetadataCompat` to
        `MediaMetadata.extras` and `MediaMetadata.extras` to
        `MediaMetadataCompat`
        ([#756](https://github.com/androidx/media/issues/756),
        [#802](https://github.com/androidx/media/issues/802)).
    *   Fix broadcasting `notifyChildrenChanged` for legacy controllers
        ([#644](https://github.com/androidx/media/issues/644)).
    *   Fix a bug where setting a negative time for a disabled `setWhen` timer
        of the notification caused a crash on some devices
        ([#903](https://github.com/androidx/media/issues/903)).
    *   Fix `IllegalStateException` when the media notification controller
        hasn't completed connecting when the first notification update is
        requested ([#917](https://github.com/androidx/media/issues/917)).
*   UI:
    *   Fix issue where forward and rewind buttons are not visible when used
        with Material Design in a BottomSheetDialogFragment
        ([#511](https://github.com/androidx/media/issues/511)).
    *   Fix issue where the numbers in the fast forward button of the
        `PlayerControlView` were misaligned
        ([#547](https://github.com/androidx/media/issues/547)).
*   DASH extension:
    *   Parse "f800" as channel count of 5 for Dolby in DASH manifest
        ([#688](https://github.com/androidx/media/issues/688)).
*   Decoder extensions (FFmpeg, VP9, AV1, MIDI, etc.):
    *   MIDI: Fix issue where seeking forward skips the Program Change events
        ([#704](https://github.com/androidx/media/issues/704)).
    *   Migrate to FFmpeg 6.0 and update supported NDK to `r26b`
        ([#707](https://github.com/androidx/media/pull/707),
        [#867](https://github.com/androidx/media/pull/867)).
*   Cast extension:
    *   Sanitize creation of a `Timeline` to not crash the app when loading
        media fails on the cast device
        ([#708](https://github.com/androidx/media/issues/708)).

### 1.2.0 (2023-11-15)

This release includes the following changes since the
[1.1.1 release](#111-2023-08-14):

*   Common Library:
    *   Add a `@Nullable Throwable` parameter to the methods in the `Log.Logger`
        interface. The `message` parameter to these methods no longer contains
        any information about the `Throwable` passed to the `Log.{d,i,w,e}()`
        methods, so implementations will need to manually append this
        information if desired (possibly using
        `Logger.appendThrowableString(String, Throwable)`).
    *   Fix Kotlin compatibility issue where nullable generic type parameters
        and nullable array element types are not detected as nullable. Examples
        are `TrackSelectorResult` and `SimpleDecoder` method parameters
        ([#6792](https://github.com/google/ExoPlayer/issues/6792)).
    *   Change default UI and notification behavior in
        `Util.shouldShowPlayButton` to show a "play" button while playback is
        temporarily suppressed (e.g. due to transient audio focus loss). The
        legacy behavior can be maintained by using
        `PlayerView.setShowPlayButtonIfPlaybackIsSuppressed(false)` or
        `MediaSession.Builder.setShowPlayButtonIfPlaybackIsSuppressed(false)`
        ([#11213](https://github.com/google/ExoPlayer/issues/11213)).
    *   Upgrade `androidx.annotation:annotation-experimental` to `1.3.1` to fix
        https://issuetracker.google.com/251172715.
    *   Move `ExoPlayer.setAudioAttributes` to the `Player` interface.
*   ExoPlayer:
    *   Fix seeking issues in AC4 streams caused by not identifying decode-only
        samples correctly
        ([#11000](https://github.com/google/ExoPlayer/issues/11000)).
    *   Add suppression of playback on unsuitable audio output devices (e.g. the
        built-in speaker on Wear OS devices) when this feature is enabled via
        `ExoPlayer.Builder.setSuppressPlaybackOnUnsuitableOutput`. The playback
        suppression reason will be updated as
        `Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT` if playback
        is attempted when no suitable audio outputs are available, or if all
        suitable outputs are disconnected during playback. The suppression
        reason will be removed when a suitable output is connected.
    *   Add `MediaSource.canUpdateMediaItem` and `MediaSource.updateMediaItem`
        to accept `MediaItem` updates after creation via
        `Player.replaceMediaItem(s)`.
    *   Allow `MediaItem` updates for all `MediaSource` classes provided by the
        library via `Player.replaceMediaItem(s)`
        ([#33](https://github.com/androidx/media/issues/33),
        [#9978](https://github.com/google/ExoPlayer/issues/9978)).
    *   Rename `MimeTypes.TEXT_EXOPLAYER_CUES` to
        `MimeTypes.APPLICATION_MEDIA3_CUES`.
    *   Add `PngExtractor` that sends and reads a whole PNG file into the
        `TrackOutput` as one sample.
    *   Enhance `SequenceableLoader.continueLoading(long)` method in the
        `SequenceableLoader` interface to
        `SequenceableLoader.continueLoading(LoadingInfo loadingInfo)`.
        `LoadingInfo` contains additional parameters, including `playbackSpeed`
        and `lastRebufferRealtimeMs` in addition to the existing
        `playbackPositionUs`.
    *   Enhance `ChunkSource.getNextChunk(long, long, List, ChunkHolder)` method
        in the `ChunkSource` interface to `ChunkSource.getNextChunk(LoadingInfo,
        long, List, ChunkHolder)`.
    *   Add additional fields to Common Media Client Data (CMCD) logging: buffer
        starvation (`bs`), deadline (`dl`), playback rate (`pr`) and startup
        (`su`) ([#8699](https://github.com/google/ExoPlayer/issues/8699)).
    *   Add luma and chroma bitdepth to `ColorInfo`
        ([#491](https://github.com/androidx/media/pull/491)).
    *   Add additional fields to Common Media Client Data (CMCD) logging: next
        object request (`nor`) and next range request (`nrr`)
        ([#8699](https://github.com/google/ExoPlayer/issues/8699)).
    *   Add functionality to transmit Common Media Client Data (CMCD) data using
        query parameters ([#553](https://github.com/androidx/media/issues/553)).
    *   Fix `ConcurrentModificationException` in `ExperimentalBandwidthMeter`
        ([#612](https://github.com/androidx/media/issues/612)).
    *   Add `MediaPeriodId` parameter to
        `CompositeMediaSource.getMediaTimeForChildMediaTime`.
    *   Support `ClippingMediaSource` (and other sources with period/window time
        offsets) in `ConcatenatingMediaSource2`
        ([#11226](https://github.com/google/ExoPlayer/issues/11226)).
    *   Change `BaseRenderer.onStreamChanged()` to also receive a
        `MediaPeriodId` argument.
*   Transformer:
    *   Parse EXIF rotation data for image inputs.
    *   Remove `TransformationRequest.HdrMode` annotation type and its
        associated constants. Use `Composition.HdrMode` and its associated
        constants instead.
    *   Simplify the `OverlaySettings` to fix rotation issues.
    *   Changed `frameRate` and `durationUs` parameters of
        `SampleConsumer.queueInputBitmap` to `TimestampIterator`.
*   Track Selection:
    *   Add `DefaultTrackSelector.Parameters.allowAudioNonSeamlessAdaptiveness`
        to explicitly allow or disallow non-seamless adaptation. The default
        stays at its current behavior of `true`.
*   Extractors:
    *   MPEG-TS: Ensure the last frame is rendered by passing the last access
        unit of a stream to the sample queue
        ([#7909](https://github.com/google/ExoPlayer/issues/7909)).
    *   Fix typo when determining `rotationDegrees`. Changed
        `projectionPosePitch` to `projectionPoseRoll`
        ([#461](https://github.com/androidx/media/pull/461)).
    *   Remove the assumption that `Extractor` instances can be directly
        inspected with `instanceof`. If you want runtime access to the
        implementation details of an `Extractor` you must first call
        `Extractor.getUnderlyingInstance`.
    *   Add `BmpExtractor`.
    *   Add `WebpExtractor`.
    *   Add `HeifExtractor`.
    *   Add
        [QuickTime classic](https://developer.apple.com/standards/qtff-2001.pdf)
        support to `Mp4Extractor`.
*   Audio:
    *   Add support for 24/32-bit big-endian PCM in MP4 and Matroska, and parse
        PCM encoding for `lpcm` in MP4.
    *   Add support for extracting Vorbis audio in MP4.
    *   Add `AudioSink.getFormatOffloadSupport(Format)` that retrieves level of
        offload support the sink can provide for the format through a
        `DefaultAudioOffloadSupportProvider`. It returns the new
        `AudioOffloadSupport` that contains `isFormatSupported`,
        `isGaplessSupported`, and `isSpeedChangeSupported`.
    *   Add `AudioSink.setOffloadMode()` through which the offload configuration
        on the audio sink is configured. Default is
        `AudioSink.OFFLOAD_MODE_DISABLED`.
    *   Offload can be enabled through `setAudioOffloadPreference` in
        `TrackSelectionParameters`. If the set preference is to enable, the
        device supports offload for the format, and the track selection is a
        single audio track, then audio offload will be enabled.
    *   If `audioOffloadModePreference` is set to
        `AUDIO_OFFLOAD_MODE_PREFERENCE_REQUIRED`, then the
        `DefaultTrackSelector` will only select an audio track and only if that
        track's format is supported in offload. If no audio track is supported
        in offload, then no track will be selected.
    *   Disabling gapless support for offload when pre-API level 33 due to
        playback position issue after track transition.
    *   Remove parameter `enableOffload` from
        `DefaultRenderersFactory.buildAudioSink` method signature.
    *   Remove method `DefaultAudioSink.Builder.setOffloadMode`.
    *   Remove intdef value
        `DefaultAudioSink.OffloadMode.OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED`.
    *   Add support for Opus gapless metadata during offload playback.
    *   Allow renderer recovery by disabling offload if failed at first write
        ([#627](https://github.com/androidx/media/issues/627)).
    *   Enable Offload Scheduling by default for audio-only offloaded playback.
    *   Delete `ExoPlayer.experimentalSetOffloadSchedulingEnabled` and
        `AudioOffloadListener.onExperimentalOffloadSchedulingEnabledChanged`.
    *   Renamed `onExperimentalSleepingForOffloadChanged` as
        `onSleepingForOffloadChanged` and `onExperimentalOffloadedPlayback` as
        `onOffloadedPlayback`.
    *   Move audio offload mode related `TrackSelectionParameters` interfaces
        and definitions to an inner `AudioOffloadPreferences` class.
    *   Add `onAudioTrackInitialized` and `onAudioTrackReleased` callbacks to
        `AnalyticsListener`, `AudioRendererEventListener` and
        `AudioSink.Listener`.
    *   Fix DTS Express audio buffer underflow issue
        ([#650](https://github.com/androidx/media/pull/650)).
    *   Fix bug where the capabilities check for E-AC3-JOC throws an
        `IllegalArgumentException`
        ([#677](https://github.com/androidx/media/issues/677)).
*   Video:
    *   Allow `MediaCodecVideoRenderer` to use a custom
        `VideoFrameProcessor.Factory`.
    *   Fix bug where the first frame couldn't be rendered if the audio stream
        starts with negative timestamps
        ([#291](https://github.com/androidx/media/issues/291)).
*   Text:
    *   Remove `ExoplayerCuesDecoder`. Text tracks with `sampleMimeType =
        application/x-media3-cues` are now directly handled by `TextRenderer`
        without needing a `SubtitleDecoder` instance.
*   Metadata:
    *   `MetadataDecoder.decode` will no longer be called for "decode-only"
        samples as the implementation must return null anyway.
*   Effect:
    *   Add `VideoFrameProcessor.queueInputBitmap(Bitmap, Iterator<Long>)`
        queuing bitmap input by timestamp.
    *   Change `VideoFrameProcessor.registerInputStream()` to be non-blocking.
        Apps must implement
        `VideoFrameProcessor.Listener#onInputStreamRegistered()`.
    *   Changed `frameRate` and `durationUs` parameters of
        `VideoFrameProcessor.queueInputBitmap` to `TimestampIterator`.
*   IMA extension:
    *   Fix bug where a multi-period DASH live stream that is not the first item
        in a playlist can throw an exception
        ([#571](https://github.com/androidx/media/issues/571)).
    *   Release StreamManager before calling `AdsLoader.destroy()`
    *   Bump IMA SDK version to 3.31.0.
*   Session:
    *   Set the notifications foreground service behavior to
        `FOREGROUND_SERVICE_IMMEDIATE` in `DefaultMediaNotificationProvider`
        ([#167](https://github.com/androidx/media/issues/167)).
    *   Use only
        `android.media.session.MediaSession.setMediaButtonBroadcastReceiver()`
        above API 31 to avoid problems with deprecated API on Samsung devices
        ([#167](https://github.com/androidx/media/issues/167)).
    *   Use the media notification controller as proxy to set available commands
        and custom layout used to populate the notification and the platform
        session.
    *   Convert media button events that are received by
        `MediaSessionService.onStartCommand()` within Media3 instead of routing
        them to the platform session and back to Media3. With this, the caller
        controller is always the media notification controller and apps can
        easily recognize calls coming from the notification in the same way on
        all supported API levels.
    *   Fix bug where `MediaController.getCurrentPosition()` is not advancing
        when connected to a legacy `MediaSessionCompat`.
    *   Add `MediaLibrarySession.getSubscribedControllers(mediaId)` for
        convenience.
    *   Override `MediaLibrarySession.Callback.onSubscribe()` to assert the
        availability of the parent ID for which the controller subscribes. If
        successful, the subscription is accepted and `notifyChildrenChanged()`
        is called immediately to inform the browser
        ([#561](https://github.com/androidx/media/issues/561)).
    *   Add session demo module for Automotive OS and enable session demo for
        Android Auto.
    *   Do not set the queue of the framework session when
        `COMMAND_GET_TIMELINE` is not available for the media notification
        controller. With Android Auto as the client controller reading from the
        framework session, this has the effect that the `queue` button in the UI
        of Android Auto is not displayed
        ([#339](https://github.com/androidx/media/issues/339)).
    *   Use `DataSourceBitmapLoader` by default instead of `SimpleBitmapLoader`
        ([#271](https://github.com/androidx/media/issues/271),
        [#327](https://github.com/androidx/media/issues/327)).
    *   Add `MediaSession.Callback.onMediaButtonEvent(Intent)` that allows apps
        to override the default media button event handling.
*   UI:
    *   Add a `Player.Listener` implementation for Wear OS devices that handles
        playback suppression due to
        `Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT` by
        launching a system dialog to allow a user to connect a suitable audio
        output (e.g. bluetooth headphones). The listener will auto-resume
        playback if a suitable device is connected within a configurable timeout
        (default is 5 minutes).
*   Downloads:
    *   Declare "data sync" foreground service type for `DownloadService` for
        Android 14 compatibility. When using this service, the app also needs to
        add `dataSync` as `foregroundServiceType` in the manifest and add the
        `FOREGROUND_SERVICE_DATA_SYNC` permission
        ([#11239](https://github.com/google/ExoPlayer/issues/11239)).
*   HLS extension:
    *   Refresh the HLS live playlist with an interval calculated from the last
        load start time rather than the last load completed time
        ([#663](https://github.com/androidx/media/issues/663)).
*   DASH extension:
    *   Allow multiple of the same DASH identifier in segment template url.
    *   Add experimental support for parsing subtitles during extraction. This
        has better support for merging overlapping subtitles, including
        resolving flickering when transitioning between subtitle segments. You
        can enable this using
        `DashMediaSource.Factory.experimentalParseSubtitlesDuringExtraction()`
        ([#288](https://github.com/androidx/media/issues/288)).
*   RTSP extension:
    *   Fix a race condition that could lead to `IndexOutOfBoundsException` when
        falling back to TCP, or playback hanging in some situations.
    *   Check state in RTSP setup when returning loading state of
        `RtspMediaPeriod`
        ([#577](https://github.com/androidx/media/issues/577)).
    *   Ignore custom Rtsp request methods in Options response public header
        ([#613](https://github.com/androidx/media/issues/613)).
    *   Use RTSP Setup Response timeout value in time interval of sending
        keep-alive RTSP Options requests
        ([#662](https://github.com/androidx/media/issues/662)).
*   Decoder extensions (FFmpeg, VP9, AV1, MIDI, etc.):
    *   Release the MIDI decoder module, which provides support for playback of
        standard MIDI files using the Jsyn library to synthesize audio.
    *   Add `DecoderOutputBuffer.shouldBeSkipped` to directly mark output
        buffers that don't need to be presented. This is preferred over
        `C.BUFFER_FLAG_DECODE_ONLY` that will be deprecated.
    *   Add `Decoder.setOutputStartTimeUs` and
        `SimpleDecoder.isAtLeastOutputStartTimeUs` to allow decoders to drop
        decode-only samples before the start time. This should be preferred to
        `Buffer.isDecodeOnly` that will be deprecated.
    *   Fix bug publishing MIDI decoder artifact to Maven repository. The
        artifact is renamed to `media3-exoplayer-midi`
        ([#734](https://github.com/androidx/media/issues/734)).
*   Leanback extension:
    *   Fix bug where disabling a surface can cause an `ArithmeticException` in
        Leanback code ([#617](https://github.com/androidx/media/issues/617)).
*   Test Utilities:
    *   Make `TestExoPlayerBuilder` and `FakeClock` compatible with Espresso UI
        tests and Compose UI tests. This fixes a bug where playback advances
        non-deterministically during Espresso or Compose view interactions.
*   Remove deprecated symbols:
    *   Remove
        `TransformationRequest.Builder.setEnableRequestSdrToneMapping(boolean)`
        and
        `TransformationRequest.Builder.experimental_setEnableHdrEditing(boolean)`.
        Use `Composition.Builder.setHdrMode(int)` and pass the `Composition` to
        `Transformer.start(Composition, String)` instead.
    *   Remove deprecated `DownloadNotificationHelper.buildProgressNotification`
        method, use a non deprecated method that takes a `notMetRequirements`
        parameter instead.

### 1.2.0-rc01 (2023-11-01)

Use the 1.2.0 [stable version](#120-2023-11-15).

### 1.2.0-beta01 (2023-10-18)

Use the 1.2.0 [stable version](#120-2023-11-15).

### 1.2.0-alpha02 (2023-09-29)

Use the 1.2.0 [stable version](#120-2023-11-15).

### 1.2.0-alpha01 (2023-08-17)

Use the 1.2.0 [stable version](#120-2023-11-15).

## 1.1

### 1.1.1 (2023-08-14)

This release corresponds to the
[ExoPlayer 2.19.1 release](https://github.com/google/ExoPlayer/releases/tag/r2.19.1).

This release includes the following changes since the
[1.1.0 release](#110-2023-07-05):

*   Common Library:
    *   Remove accidentally added `multidex` dependency from all modules
        ([#499](https://github.com/androidx/media/issues/499)).
*   ExoPlayer:
    *   Fix issue in `PlaybackStatsListener` where spurious `PlaybackStats` are
        created after the playlist is cleared.
    *   Add additional fields to Common Media Client Data (CMCD) logging:
        streaming format (sf), stream type (st), version (v), top birate (tb),
        object duration (d), measured throughput (mtp) and object type (ot)
        ([#8699](https://github.com/google/ExoPlayer/issues/8699)).
*   Audio:
    *   Fix a bug where `Player.getState()` never transitioned to `STATE_ENDED`
        when playing very short files
        ([#538](https://github.com/androidx/media/issues/538)).
*   Audio Offload:
    *   Prepend Ogg ID Header and Comment Header Pages to bitstream for
        offloaded Opus playback in accordance with RFC 7845.
*   Video:
    *   H.265/HEVC: Fix parsing SPS short and long term reference picture info.
*   Text:
    *   CEA-608: Change cue truncation logic to only consider visible text.
        Previously indent and tab offset were included when limiting the cue
        length to 32 characters (which was technically correct by the spec)
        ([#11019](https://github.com/google/ExoPlayer/issues/11019)).
*   IMA extension:
    *   Bump IMA SDK version to 3.30.3.
*   Session:
    *   Add custom layout to the state of the controller and provide a getter to
        access it. When the custom layout changes,
        `MediaController.Listener.onCustomLayoutChanged` is called. Apps that
        want to send different custom layouts to different Media3 controller can
        do this in `MediaSession.Callback.onConnect` by using an
        `AcceptedResultBuilder` to make sure the custom layout is available to
        the controller when connection completes.
    *   Fix cases where `MediaLibraryServiceLegacyStub` sent an error to a
        `Result` that didn't support this which produced an
        `UnsupportedOperationException`
        ([#78](https://github.com/androidx/media/issues/78)).
    *   Fix the way `PlayerWrapper` creates a `VolumeProviderCompat` by
        determining `volumeControlType` through both legacy commands
        (`COMMAND_ADJUST_DEVICE_VOLUME` and `COMMAND_SET_DEVICE_VOLUME`) and new
        commands (`COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS` and
        `COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS`)
        ([#554](https://github.com/androidx/media/issues/554)).

### 1.1.0 (2023-07-05)

This release corresponds to the
[ExoPlayer 2.19.0 release](https://github.com/google/ExoPlayer/releases/tag/r2.19.0).

This release contains the following changes since the
[1.0.2 release](#102-2023-05-18):

*   Common Library:
    *   Add suppression reason for unsuitable audio route and play when ready
        change reason for suppressed too long.
        ([#15](https://github.com/androidx/media/issues/15)).
    *   Add commands to Player:
        *   `COMMAND_GET_METADATA`
        *   `COMMAND_SET_PLAYLIST_METADATA`
        *   `COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS`
        *   `COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS`
    *   Add overloaded methods to Player which allow users to specify volume
        flags:
        *   `void setDeviceVolume(int, int)`
        *   `void increaseDeviceVolume(int)`
        *   `void decreaseDeviceVolume(int)`
        *   `void setDeviceMuted(boolean, int)`
    *   Add `Builder` for `DeviceInfo` and deprecate existing constructor.
    *   Add `DeviceInfo.routingControllerId` to specify the routing controller
        ID for remote playbacks.
    *   Add `Player.replaceMediaItem(s)` as a shortcut to adding and removing
        items at the same position
        ([#8046](https://github.com/google/ExoPlayer/issues/8046)).
*   ExoPlayer:
    *   Allow ExoPlayer to have control of device volume methods only if
        explicitly opted in. Use
        `ExoPlayer.Builder.setDeviceVolumeControlEnabled` to have access to:
        *   `getDeviceVolume()`
        *   `isDeviceMuted()`
        *   `setDeviceVolume(int)` and `setDeviceVolume(int, int)`
        *   `increaseDeviceVolume(int)` and `increaseDeviceVolume(int, int)`
        *   `decreaseDeviceVolume(int)` and `decreaseDeviceVolume(int, int)`
    *   Add `FilteringMediaSource` that allows to filter available track types
        from a `MediaSource`.
    *   Add support for including Common Media Client Data (CMCD) in the
        outgoing requests of adaptive streaming formats DASH, HLS, and
        SmoothStreaming. The following fields, `br`, `bl`, `cid`, `rtp`, and
        `sid`, have been incorporated
        ([#8699](https://github.com/google/ExoPlayer/issues/8699)). API
        structure and API methods:
        *   CMCD logging is disabled by default, use
            `MediaSource.Factory.setCmcdConfigurationFactory(CmcdConfiguration.Factory
            cmcdConfigurationFactory)` to enable it.
        *   All keys are enabled by default, override
            `CmcdConfiguration.RequestConfig.isKeyAllowed(String key)` to filter
            out which keys are logged.
        *   Override `CmcdConfiguration.RequestConfig.getCustomData()` to enable
            custom key logging.
    *   Add additional action to manifest of main demo to make it easier to
        start the demo app with a custom `*.exolist.json` file
        ([#439](https://github.com/androidx/media/pull/439)).
    *   Add `ExoPlayer.setVideoEffects()` for using `Effect` during video
        playback.
    *   Update `SampleQueue` to store `sourceId` as a `long` rather than an
        `int`. This changes the signatures of public methods
        `SampleQueue.sourceId` and `SampleQueue.peekSourceId`.
    *   Add parameters to `LoadControl` methods `shouldStartPlayback` and
        `onTracksSelected` that allow associating these methods with the
        relevant `MediaPeriod`.
    *   Change signature of
        `ServerSideAdInsertionMediaSource.setAdPlaybackStates(Map<Object,
        AdPlaybackState>)` by adding a timeline parameter that contains the
        periods with the UIDs used as keys in the map. This is required to avoid
        concurrency issues with multi-period live streams.
    *   Deprecate `EventDispatcher.withParameters(int windowIndex, @Nullable
        MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs)` and
        `BaseMediaSource.createEventDispatcher(..., long mediaTimeOffsetMs)`.
        The variant of the methods without the `mediaTimeOffsetUs` can be called
        instead. Note that even for the deprecated variants, the offset is not
        anymore added to `startTimeUs` and `endTimeUs` of the `MediaLoadData`
        objects that are dispatched by the dispatcher.
    *   Rename `ExoTrackSelection.blacklist` to `excludeTrack` and
        `isBlacklisted` to `isTrackExcluded`.
    *   Fix inconsistent behavior between `ExoPlayer.setMediaItem(s)` and
        `addMediaItem(s)` when called on an empty playlist.
*   Transformer:
    *   Remove `Transformer.Builder.setMediaSourceFactory(MediaSource.Factory)`.
        Use `ExoPlayerAssetLoader.Factory(MediaSource.Factory)` and
        `Transformer.Builder.setAssetLoaderFactory(AssetLoader.Factory)`
        instead.
    *   Remove `Transformer.startTransformation(MediaItem,
        ParcelFileDescriptor)`.
    *   Fix a bug where transformation could get stuck (leading to muxer
        timeout) if the end of the video stream was signaled at the moment when
        an input frame was pending processing.
    *   Query codecs via `MediaCodecList` instead of using
        `findDecoder/EncoderForFormat` utilities, to expand support.
    *   Remove B-frame configuration in `DefaultEncoderFactory` because it
        doesn't work on some devices.
*   Track selection:
    *   Add
        `DefaultTrackSelector.Parameters.allowInvalidateSelectionsForRendererCapabilitiesChange`
        which is disabled by default. When enabled, the `DefaultTrackSelector`
        will trigger a new track selection when the renderer capabilities
        changed.
*   Extractors:
    *   Ogg: Fix bug when seeking in files with a long duration
        ([#391](https://github.com/androidx/media/issues/391)).
    *   FMP4: Fix issue where `TimestampAdjuster` initializes a wrong timestamp
        offset with metadata sample time from emsg atom
        ([#356](https://github.com/androidx/media/issues/356)).
*   Audio:
    *   Fix bug where some playbacks fail when tunneling is enabled and
        `AudioProcessors` are active, e.g. for gapless trimming
        ([#10847](https://github.com/google/ExoPlayer/issues/10847)).
    *   Encapsulate Opus frames in Ogg packets in direct playbacks (offload).
    *   Extrapolate current position during sleep with offload scheduling.
    *   Add `Renderer.release()` and `AudioSink.release()` for releasing the
        resources at the end of player's lifecycle.
    *   Listen to audio capabilities changes in `DefaultAudioSink`. Add a
        required parameter `context` in the constructor of `DefaultAudioSink`,
        with which the `DefaultAudioSink` will register as the listener to the
        `AudioCapabilitiesReceiver` and update its `audioCapabilities` property
        when informed with a capabilities change.
    *   Propagate audio capabilities changes via a new event
        `onAudioCapabilitiesChanged` in `AudioSink.Listener` interface, and a
        new interface `RendererCapabilities.Listener` which triggers
        `onRendererCapabilitiesChanged` events.
    *   Add `ChannelMixingAudioProcessor` for applying scaling/mixing to audio
        channels.
    *   Add new int value `DISCARD_REASON_AUDIO_BYPASS_POSSIBLE` to
        `DecoderDiscardReasons` to discard audio decoder when bypass mode is
        possible after audio capabilities change.
    *   Add direct playback support for DTS Express and DTS:X
        ([#335](https://github.com/androidx/media/pull/335)).
*   Video:
    *   Make `MediaCodecVideoRenderer` report a `VideoSize` with a width and
        height of 0 when the renderer is disabled.
        `Player.Listener.onVideoSizeChanged` is called accordingly when
        `Player.getVideoSize()` changes. With this change, ExoPlayer's video
        size with `MediaCodecVideoRenderer` has a width and height of 0 when
        `Player.getCurrentTracks` does not support video, or the size of the
        supported video track is not yet determined.
*   DRM:
    *   Reduce the visibility of several internal-only methods on
        `DefaultDrmSession` that aren't expected to be called from outside the
        DRM package:
        *   `void onMediaDrmEvent(int)`
        *   `void provision()`
        *   `void onProvisionCompleted()`
        *   `onProvisionError(Exception, boolean)`
*   Muxer:
    *   Add a new muxer library which can be used to create an MP4 container
        file.
*   IMA extension:
    *   Enable multi-period live DASH streams for DAI. Please note that the
        current implementation does not yet support seeking in live streams
        ([#10912](https://github.com/google/ExoPlayer/issues/10912)).
    *   Fix a bug where a new ad group is inserted in live streams because the
        calculated content position in consecutive timelines varies slightly.
*   Session:
    *   Add helper method `MediaSession.getControllerForCurrentRequest` to
        obtain information about the controller that is currently calling
        a`Player` method.
    *   Add `androidx.media3.session.MediaButtonReceiver` to enable apps to
        implement playback resumption with media button events sent by, for
        example, a Bluetooth headset
        ([#167](https://github.com/androidx/media/issues/167)).
    *   Add default implementation to `MediaSession.Callback.onAddMediaItems` to
        allow requested `MediaItems` to be passed onto `Player` if they have
        `LocalConfiguration` (e.g. URI)
        ([#282](https://github.com/androidx/media/issues/282)).
    *   Add "seek to previous" and "seek to next" command buttons on compact
        media notification view by default for Android 12 and below
        ([#410](https://github.com/androidx/media/issues/410)).
*   UI:
    *   Add Util methods `shouldShowPlayButton` and
        `handlePlayPauseButtonAction` to write custom UI elements with a
        play/pause button.
*   RTSP extension:
    *   For MPEG4-LATM, use default profile-level-id value if absent in Describe
        Response SDP message
        ([#302](https://github.com/androidx/media/issues/302)).
    *   Use base Uri for relative path resolution from the RTSP session if
        present in DESCRIBE response header
        ([#11160](https://github.com/google/ExoPlayer/issues/11160)).
*   DASH extension:
    *   Remove the media time offset from `MediaLoadData.startTimeMs` and
        `MediaLoadData.endTimeMs` for multi period DASH streams.
    *   Fix a bug where re-preparing a multi-period live Dash media source
        produced a `IndexOutOfBoundsException`
        ([#10838](https://github.com/google/ExoPlayer/issues/10838)).
*   HLS extension:
    *   Add
        `HlsMediaSource.Factory.setTimestampAdjusterInitializationTimeoutMs(long)`
        to set a timeout for the loading thread to wait for the
        `TimestampAdjuster` to initialize. If the initialization doesn't
        complete before the timeout, a `PlaybackException` is thrown to avoid
        the playback endless stalling. The timeout is set to zero by default
        ([#323](https://github.com/androidx/media/issues//323)).
*   Test Utilities:
    *   Check for URI scheme case insensitivity in `DataSourceContractTest`.
*   Remove deprecated symbols:
    *   Remove `DefaultAudioSink` constructors, use `DefaultAudioSink.Builder`
        instead.
    *   Remove `HlsMasterPlaylist`, use `HlsMultivariantPlaylist` instead.
    *   Remove `Player.stop(boolean)`. Use `Player.stop()` and
        `Player.clearMediaItems()` (if `reset` is `true`) instead.
    *   Remove two deprecated `SimpleCache` constructors, use a non-deprecated
        constructor that takes a `DatabaseProvider` instead for better
        performance.
    *   Remove `DefaultBandwidthMeter` constructor, use
        `DefaultBandwidthMeter.Builder` instead.
    *   Remove `DefaultDrmSessionManager` constructors, use
        `DefaultDrmSessionManager.Builder` instead.
    *   Remove two deprecated `HttpDataSource.InvalidResponseCodeException`
        constructors, use a non-deprecated constructor that accepts additional
        fields(`cause`, `responseBody`) to enhance error logging.
    *   Remove `DownloadHelper.forProgressive`, `DownloadHelper.forHls`,
        `DownloadHelper.forDash`, and `DownloadHelper.forSmoothStreaming`, use
        `DownloadHelper.forMediaItem` instead.
    *   Remove deprecated `DownloadService` constructor, use a non deprecated
        constructor that includes the option to provide a
        `channelDescriptionResourceId` parameter.
    *   Remove deprecated String constants for Charsets (`ASCII_NAME`,
        `UTF8_NAME`, `ISO88591_NAME`, `UTF16_NAME` and `UTF16LE_NAME`), use
        Kotlin Charsets from the `kotlin.text` package, the
        `java.nio.charset.StandardCharsets` or the
        `com.google.common.base.Charsets` instead.
    *   Remove deprecated `WorkManagerScheduler` constructor, use a non
        deprecated constructor that includes the option to provide a `Context`
        parameter instead.
    *   Remove the deprecated methods `createVideoSampleFormat`,
        `createAudioSampleFormat`, `createContainerFormat`, and
        `createSampleFormat`, which were used to instantiate the `Format` class.
        Instead use `Format.Builder` for creating instances of `Format`.
    *   Remove the deprecated methods `copyWithMaxInputSize`,
        `copyWithSubsampleOffsetUs`, `copyWithLabel`,
        `copyWithManifestFormatInfo`, `copyWithGaplessInfo`,
        `copyWithFrameRate`, `copyWithDrmInitData`, `copyWithMetadata`,
        `copyWithBitrate` and `copyWithVideoSize`, use `Format.buildUpon()` and
        setter methods instead.
    *   Remove deprecated `ExoPlayer.retry()`, use `prepare()` instead.
    *   Remove deprecated zero-arg `DefaultTrackSelector` constructor, use
        `DefaultTrackSelector(Context)` instead.
    *   Remove deprecated `OfflineLicenseHelper` constructor, use
        `OfflineLicenseHelper(DefaultDrmSessionManager,
        DrmSessionEventListener.EventDispatcher)` instead.
    *   Remove deprecated `DownloadManager` constructor, use the constructor
        that takes an `Executor` instead.
    *   Remove deprecated `Cue` constructors, use `Cue.Builder` instead.
    *   Remove deprecated `OfflineLicenseHelper` constructor, use
        `OfflineLicenseHelper(DefaultDrmSessionManager,
        DrmSessionEventListener.EventDispatcher)` instead.
    *   Remove four deprecated `AnalyticsListener` methods:
        *   `onDecoderEnabled`, use `onAudioEnabled` and/or `onVideoEnabled`
            instead.
        *   `onDecoderInitialized`, use `onAudioDecoderInitialized` and/or
            `onVideoDecoderInitialized` instead.
        *   `onDecoderInputFormatChanged`, use `onAudioInputFormatChanged`
            and/or `onVideoInputFormatChanged` instead.
        *   `onDecoderDisabled`, use `onAudioDisabled` and/or `onVideoDisabled`
            instead.
    *   Remove the deprecated `Player.Listener.onSeekProcessed` and
        `AnalyticsListener.onSeekProcessed`, use `onPositionDiscontinuity` with
        `DISCONTINUITY_REASON_SEEK` instead.
    *   Remove `ExoPlayer.setHandleWakeLock(boolean)`, use `setWakeMode(int)`
        instead.
    *   Remove deprecated
        `DefaultLoadControl.Builder.createDefaultLoadControl()`, use `build()`
        instead.
    *   Remove deprecated `MediaItem.PlaybackProperties`, use
        `MediaItem.LocalConfiguration` instead. Deprecated field
        `MediaItem.playbackProperties` is now of type
        `MediaItem.LocalConfiguration`.

### 1.1.0-rc01 (2023-06-21)

Use the 1.1.0 [stable version](#110-2023-07-05).

### 1.1.0-beta01 (2023-06-07)

Use the 1.1.0 [stable version](#110-2023-07-05).

### 1.1.0-alpha01 (2023-05-10)

Use the 1.1.0 [stable version](#110-2023-07-05).

## 1.0

### 1.0.2 (2023-05-18)

This release corresponds to the
[ExoPlayer 2.18.7 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.7).

This release contains the following changes since the
[1.0.1 release](#101-2023-04-18):

*   Core library:
    *   Add `Buffer.isLastSample()` that denotes if `Buffer` contains flag
        `C.BUFFER_FLAG_LAST_SAMPLE`.
    *   Fix issue where last frame may not be rendered if the last sample with
        frames is dequeued without reading the 'end of stream' sample.
        ([#11079](https://github.com/google/ExoPlayer/issues/11079)).
*   Extractors:
    *   Fix parsing of H.265 SPS in MPEG-TS files by re-using the parsing logic
        already used by RTSP and MP4 extractors
        ([#303](https://github.com/androidx/media/issues/303)).
*   Text:
    *   SSA: Add support for UTF-16 files if they start with a byte order mark
        ([#319](https://github.com/androidx/media/issues/319)).
*   Session:
    *   Fix issue where `MediaController` doesn't update its available commands
        when connected to a legacy `MediaSessionCompat` that updates its
        actions.
    *   Fix bug that prevented the `MediaLibraryService` from returning null for
        a call from System UI to `Callback.onGetLibraryRoot` with
        `params.isRecent == true` on API 30
        ([#355](https://github.com/androidx/media/issues/355)).
    *   Fix memory leak of `MediaSessionService` or `MediaLibraryService`
        ([#346](https://github.com/androidx/media/issues/346)).
    *   Fix bug where a combined `Timeline` and position update in a
        `MediaSession` may cause a `MediaController` to throw an
        `IllegalStateException`.

### 1.0.1 (2023-04-18)

This release corresponds to the
[ExoPlayer 2.18.6 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.6).

*   Core library:
    *   Reset target live stream override when seeking to default position
        ([#11051](https://github.com/google/ExoPlayer/pull/11051)).
    *   Fix bug where empty sample streams in the media could cause playback to
        be stuck.
*   Session:
    *   Fix bug where multiple identical queue items published by a legacy
        `MediaSessionCompat` result in an exception in `MediaController`
        ([#290](https://github.com/androidx/media/issues/290)).
    *   Add missing forwarding of `MediaSession.broadcastCustomCommand` to the
        legacy `MediaControllerCompat.Callback.onSessionEvent`
        ([#293](https://github.com/androidx/media/issues/293)).
    *   Fix bug where calling `MediaSession.setPlayer` doesn't update the
        available commands.
    *   Fix issue that `TrackSelectionOverride` instances sent from a
        `MediaController` are ignored if they reference a group with
        `Format.metadata`
        ([#296](https://github.com/androidx/media/issues/296)).
    *   Fix issue where `Player.COMMAND_GET_CURRENT_MEDIA_ITEM` needs to be
        available to access metadata via the legacy `MediaSessionCompat`.
    *   Fix issue where `MediaSession` instances on a background thread cause
        crashes when used in `MediaSessionService`
        ([#318](https://github.com/androidx/media/issues/318)).
    *   Fix issue where a media button receiver was declared by the library
        without the app having intended this
        ([#314](https://github.com/androidx/media/issues/314)).
*   DASH:
    *   Fix handling of empty segment timelines
        ([#11014](https://github.com/google/ExoPlayer/issues/11014)).
*   RTSP:
    *   Retry with TCP if RTSP Setup with UDP fails with RTSP Error 461
        UnsupportedTransport
        ([#11069](https://github.com/google/ExoPlayer/issues/11069)).

### 1.0.0 (2023-03-22)

This release corresponds to the
[ExoPlayer 2.18.5 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.5).

There are no changes since 1.0.0-rc02.

### 1.0.0-rc02 (2023-03-02)

This release corresponds to the
[ExoPlayer 2.18.4 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.4).

*   Core library:
    *   Fix network type detection on API 33
        ([#10970](https://github.com/google/ExoPlayer/issues/10970)).
    *   Fix `NullPointerException` when calling `ExoPlayer.isTunnelingEnabled`
        ([#10977](https://github.com/google/ExoPlayer/issues/10977)).
*   Downloads:
    *   Make the maximum difference of the start time of two segments to be
        merged configurable in `SegmentDownloader` and subclasses
        ([#248](https://github.com/androidx/media/pull/248)).
*   Audio:
    *   Fix broken gapless MP3 playback on Samsung devices
        ([#8594](https://github.com/google/ExoPlayer/issues/8594)).
    *   Fix bug where playback speeds set immediately after disabling audio may
        be overridden by a previous speed change
        ([#10882](https://github.com/google/ExoPlayer/issues/10882)).
*   Video:
    *   Map HEVC HDR10 format to `HEVCProfileMain10HDR10` instead of
        `HEVCProfileMain10`.
    *   Add workaround for a device issue on Chromecast with Google TV and
        Lenovo M10 FHD Plus that causes 60fps AVC streams to be marked as
        unsupported
        ([#10898](https://github.com/google/ExoPlayer/issues/10898)).
    *   Fix frame release performance issues when playing media with a frame
        rate far higher than the screen refresh rate.
*   Cast:
    *   Fix transient `STATE_IDLE` when transitioning between media items
        ([#245](https://github.com/androidx/media/issues/245)).
*   RTSP:
    *   Catch the IllegalArgumentException thrown in parsing of invalid RTSP
        Describe response messages
        ([#10971](https://github.com/google/ExoPlayer/issues/10971)).
*   Session:
    *   Fix a bug where notification play/pause button doesn't update with
        player state ([#192](https://github.com/androidx/media/issues/192)).
*   IMA extension:
    *   Fix a bug which prevented DAI streams without any ads from starting
        because the first (and in the case without ads the only) `LOADED` event
        wasn't received.

### 1.0.0-rc01 (2023-02-16)

This release corresponds to the
[ExoPlayer 2.18.3 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.3).

*   Core library:
    *   Tweak the renderer's decoder ordering logic to uphold the
        `MediaCodecSelector`'s preferences, even if a decoder reports it may not
        be able to play the media performantly. For example with default
        selector, hardware decoder with only functional support will be
        preferred over software decoder that fully supports the format
        ([#10604](https://github.com/google/ExoPlayer/issues/10604)).
    *   Add `ExoPlayer.Builder.setPlaybackLooper` that sets a pre-existing
        playback thread for a new ExoPlayer instance.
    *   Allow download manager helpers to be cleared
        ([#10776](https://github.com/google/ExoPlayer/issues/10776)).
    *   Add parameter to `BasePlayer.seekTo` to also indicate the command used
        for seeking.
    *   Use theme when loading drawables on API 21+
        ([#220](https://github.com/androidx/media/issues/220)).
    *   Add `ConcatenatingMediaSource2` that allows combining multiple media
        items into a single window
        ([#247](https://github.com/androidx/media/issues/247)).
*   Extractors:
    *   Throw a `ParserException` instead of a `NullPointerException` if the
        sample table (stbl) is missing a required sample description (stsd) when
        parsing trak atoms.
    *   Correctly skip samples when seeking directly to a sync frame in fMP4
        ([#10941](https://github.com/google/ExoPlayer/issues/10941)).
*   Audio:
    *   Use the compressed audio format bitrate to calculate the min buffer size
        for `AudioTrack` in direct playbacks (passthrough).
*   Text:
    *   Fix `TextRenderer` passing an invalid (negative) index to
        `Subtitle.getEventTime` if a subtitle file contains no cues.
    *   SubRip: Add support for UTF-16 files if they start with a byte order
        mark.
*   Metadata:
    *   Parse multiple null-separated values from ID3 frames, as permitted by
        ID3 v2.4.
    *   Add `MediaMetadata.mediaType` to denote the type of content or the type
        of folder described by the metadata.
    *   Add `MediaMetadata.isBrowsable` as a replacement for
        `MediaMetadata.folderType`. The folder type will be deprecated in the
        next release.
*   DASH:
    *   Add full parsing for image adaptation sets, including tile counts
        ([#3752](https://github.com/google/ExoPlayer/issues/3752)).
*   UI:
    *   Fix the deprecated
        `PlayerView.setControllerVisibilityListener(PlayerControlView.VisibilityListener)`
        to ensure visibility changes are passed to the registered listener
        ([#229](https://github.com/androidx/media/issues/229)).
    *   Fix the ordering of the center player controls in `PlayerView` when
        using a right-to-left (RTL) layout
        ([#227](https://github.com/androidx/media/issues/227)).
*   Session:
    *   Add abstract `SimpleBasePlayer` to help implement the `Player` interface
        for custom players.
    *   Add helper method to convert platform session token to Media3
        `SessionToken` ([#171](https://github.com/androidx/media/issues/171)).
    *   Use `onMediaMetadataChanged` to trigger updates of the platform media
        session ([#219](https://github.com/androidx/media/issues/219)).
    *   Add the media session as an argument of `getMediaButtons()` of the
        `DefaultMediaNotificationProvider` and use immutable lists for clarity
        ([#216](https://github.com/androidx/media/issues/216)).
    *   Add `onSetMediaItems` callback listener to provide means to modify/set
        `MediaItem` list, starting index and position by session before setting
        onto Player ([#156](https://github.com/androidx/media/issues/156)).
    *   Avoid double tap detection for non-Bluetooth media button events
        ([#233](https://github.com/androidx/media/issues/233)).
    *   Make `QueueTimeline` more robust in case of a shady legacy session state
        ([#241](https://github.com/androidx/media/issues/241)).
*   Cast extension:
    *   Bump Cast SDK version to 21.2.0.
*   IMA extension:
    *   Map `PLAYER_STATE_LOADING` to `STATE_BUFFERING`
        ([#245](\(https://github.com/androidx/media/issues/245\)).
*   IMA extension
    *   Remove player listener of the `ImaServerSideAdInsertionMediaSource` on
        the application thread to avoid threading issues.
    *   Add a property `focusSkipButtonWhenAvailable` to the
        `ImaServerSideAdInsertionMediaSource.AdsLoader.Builder` to request
        focusing the skip button on TV devices and set it to true by default.
    *   Add a method `focusSkipButton()` to the
        `ImaServerSideAdInsertionMediaSource.AdsLoader` to programmatically
        request to focus the skip button.
    *   Fix a bug which prevented playback from starting for a DAI stream
        without any ads.
    *   Bump IMA SDK version to 3.29.0.
*   Demo app:
    *   Request notification permission for download notifications at runtime
        ([#10884](https://github.com/google/ExoPlayer/issues/10884)).

### 1.0.0-beta03 (2022-11-22)

This release corresponds to the
[ExoPlayer 2.18.2 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.2).

*   Core library:
    *   Add `ExoPlayer.isTunnelingEnabled` to check if tunneling is enabled for
        the currently selected tracks
        ([#2518](https://github.com/google/ExoPlayer/issues/2518)).
    *   Add `WrappingMediaSource` to simplify wrapping a single `MediaSource`
        ([#7279](https://github.com/google/ExoPlayer/issues/7279)).
    *   Discard back buffer before playback gets stuck due to insufficient
        available memory.
    *   Close the Tracing "doSomeWork" block when offload is enabled.
    *   Fix session tracking problem with fast seeks in `PlaybackStatsListener`
        ([#180](https://github.com/androidx/media/issues/180)).
    *   Send missing `onMediaItemTransition` callback when calling `seekToNext`
        or `seekToPrevious` in a single-item playlist
        ([#10667](https://github.com/google/ExoPlayer/issues/10667)).
    *   Add `Player.getSurfaceSize` that returns the size of the surface on
        which the video is rendered.
    *   Fix bug where removing listeners during the player release can cause an
        `IllegalStateException`
        ([#10758](https://github.com/google/ExoPlayer/issues/10758)).
*   Build:
    *   Enforce minimum `compileSdkVersion` to avoid compilation errors
        ([#10684](https://github.com/google/ExoPlayer/issues/10684)).
    *   Avoid publishing block when included in another gradle build.
*   Track selection:
    *   Prefer other tracks to Dolby Vision if display does not support it.
        ([#8944](https://github.com/google/ExoPlayer/issues/8944)).
*   Downloads:
    *   Fix potential infinite loop in `ProgressiveDownloader` caused by
        simultaneous download and playback with the same `PriorityTaskManager`
        ([#10570](https://github.com/google/ExoPlayer/pull/10570)).
    *   Make download notification appear immediately
        ([#183](https://github.com/androidx/media/pull/183)).
    *   Limit parallel download removals to 1 to avoid excessive thread creation
        ([#10458](https://github.com/google/ExoPlayer/issues/10458)).
*   Video:
    *   Try alternative decoder for Dolby Vision if display does not support it.
        ([#9794](https://github.com/google/ExoPlayer/issues/9794)).
*   Audio:
    *   Use `SingleThreadExecutor` for releasing `AudioTrack` instances to avoid
        OutOfMemory errors when releasing multiple players at the same time
        ([#10057](https://github.com/google/ExoPlayer/issues/10057)).
    *   Adds `AudioOffloadListener.onExperimentalOffloadedPlayback` for the
        AudioTrack offload state.
        ([#134](https://github.com/androidx/media/issues/134)).
    *   Make `AudioTrackBufferSizeProvider` a public interface.
    *   Add `ExoPlayer.setPreferredAudioDevice` to set the preferred audio
        output device ([#135](https://github.com/androidx/media/issues/135)).
    *   Rename `androidx.media3.exoplayer.audio.AudioProcessor` to
        `androidx.media3.common.audio.AudioProcessor`.
    *   Map 8-channel and 12-channel audio to the 7.1 and 7.1.4 channel masks
        respectively on all Android versions
        ([#10701](https://github.com/google/ExoPlayer/issues/10701)).
*   Metadata:
    *   `MetadataRenderer` can now be configured to render metadata as soon as
        they are available. Create an instance with
        `MetadataRenderer(MetadataOutput, Looper, MetadataDecoderFactory,
        boolean)` to specify whether the renderer will output metadata early or
        in sync with the player position.
*   DRM:
    *   Work around a bug in the Android 13 ClearKey implementation that returns
        a non-empty but invalid license URL.
    *   Fix `setMediaDrmSession failed: session not opened` error when switching
        between DRM schemes in a playlist (e.g. Widevine to ClearKey).
*   Text:
    *   CEA-608: Ensure service switch commands on field 2 are handled correctly
        ([#10666](https://github.com/google/ExoPlayer/issues/10666)).
*   DASH:
    *   Parse `EventStream.presentationTimeOffset` from manifests
        ([#10460](https://github.com/google/ExoPlayer/issues/10460)).
*   UI:
    *   Use current overrides of the player as preset in
        `TrackSelectionDialogBuilder`
        ([#10429](https://github.com/google/ExoPlayer/issues/10429)).
*   Session:
    *   Ensure commands are always executed in the correct order even if some
        require asynchronous resolution
        ([#85](https://github.com/androidx/media/issues/85)).
    *   Add `DefaultMediaNotificationProvider.Builder` to build
        `DefaultMediaNotificationProvider` instances. The builder can configure
        the notification ID, the notification channel ID and the notification
        channel name used by the provider. Also, add method
        `DefaultMediaNotificationProvider.setSmallIcon(int)` to set the
        notifications small icon.
        ([#104](https://github.com/androidx/media/issues/104)).
    *   Ensure commands sent before `MediaController.release()` are not dropped
        ([#99](https://github.com/androidx/media/issues/99)).
    *   `SimpleBitmapLoader` can load bitmap from `file://` URIs
        ([#108](https://github.com/androidx/media/issues/108)).
    *   Fix assertion that prevents `MediaController` to seek over an ad in a
        period ([#122](https://github.com/androidx/media/issues/122)).
    *   When playback ends, the `MediaSessionService` is stopped from the
        foreground and a notification is shown to restart playback of the last
        played media item
        ([#112](https://github.com/androidx/media/issues/112)).
    *   Don't start a foreground service with a pending intent for pause
        ([#167](https://github.com/androidx/media/issues/167)).
    *   Manually hide the 'badge' associated with the notification created by
        `DefaultNotificationProvider` on API 26 and API 27 (the badge is
        automatically hidden on API 28+)
        ([#131](https://github.com/androidx/media/issues/131)).
    *   Fix bug where a second binder connection from a legacy MediaSession to a
        Media3 MediaController causes IllegalStateExceptions
        ([#49](https://github.com/androidx/media/issues/49)).
*   RTSP:
    *   Add H263 fragmented packet handling
        ([#119](https://github.com/androidx/media/pull/119)).
    *   Add support for MP4A-LATM
        ([#162](https://github.com/androidx/media/pull/162)).
*   IMA:
    *   Add timeout for loading ad information to handle cases where the IMA SDK
        gets stuck loading an ad
        ([#10510](https://github.com/google/ExoPlayer/issues/10510)).
    *   Prevent skipping mid-roll ads when seeking to the end of the content
        ([#10685](https://github.com/google/ExoPlayer/issues/10685)).
    *   Correctly calculate window duration for live streams with server-side
        inserted ads, for example IMA DAI
        ([#10764](https://github.com/google/ExoPlayer/issues/10764)).
*   FFmpeg extension:
    *   Add newly required flags to link FFmpeg libraries with NDK 23.1.7779620
        and above ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   AV1 extension:
    *   Update CMake version to avoid incompatibilities with the latest Android
        Studio releases
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Cast extension:
    *   Implement `getDeviceInfo()` to be able to identify `CastPlayer` when
        controlling playback with a `MediaController`
        ([#142](https://github.com/androidx/media/issues/142)).
*   Transformer:
    *   Add muxer watchdog timer to detect when generating an output sample is
        too slow.
*   Remove deprecated symbols:
    *   Remove `Transformer.Builder.setOutputMimeType(String)`. This feature has
        been removed. The MIME type will always be MP4 when the default muxer is
        used.

### 1.0.0-beta02 (2022-07-21)

This release corresponds to the
[ExoPlayer 2.18.1 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.1).

*   Core library:
    *   Ensure that changing the `ShuffleOrder` with `ExoPlayer.setShuffleOrder`
        results in a call to `Player.Listener#onTimelineChanged` with
        `reason=Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED`
        ([#9889](https://github.com/google/ExoPlayer/issues/9889)).
    *   For progressive media, only include selected tracks in buffered position
        ([#10361](https://github.com/google/ExoPlayer/issues/10361)).
    *   Allow custom logger for all ExoPlayer log output
        ([#9752](https://github.com/google/ExoPlayer/issues/9752)).
    *   Fix implementation of `setDataSourceFactory` in
        `DefaultMediaSourceFactory`, which was non-functional in some cases
        ([#116](https://github.com/androidx/media/issues/116)).
*   Extractors:
    *   Fix parsing of H265 short term reference picture sets
        ([#10316](https://github.com/google/ExoPlayer/issues/10316)).
    *   Fix parsing of bitrates from `esds` boxes
        ([#10381](https://github.com/google/ExoPlayer/issues/10381)).
*   DASH:
    *   Parse ClearKey license URL from manifests
        ([#10246](https://github.com/google/ExoPlayer/issues/10246)).
*   UI:
    *   Ensure TalkBack announces the currently active speed option in the
        playback controls menu
        ([#10298](https://github.com/google/ExoPlayer/issues/10298)).
*   RTSP:
    *   Add VP8 fragmented packet handling
        ([#110](https://github.com/androidx/media/pull/110)).
    *   Support frames/fragments in VP9
        ([#115](https://github.com/androidx/media/pull/115)).
*   Leanback extension:
    *   Listen to `playWhenReady` changes in `LeanbackAdapter`
        ([10420](https://github.com/google/ExoPlayer/issues/10420)).
*   Cast:
    *   Use the `MediaItem` that has been passed to the playlist methods as
        `Window.mediaItem` in `CastTimeline`
        ([#25](https://github.com/androidx/media/issues/25),
        [#8212](https://github.com/google/ExoPlayer/issues/8212)).
    *   Support `Player.getMetadata()` and `Listener.onMediaMetadataChanged()`
        with `CastPlayer` ([#25](https://github.com/androidx/media/issues/25)).

### 1.0.0-beta01 (2022-06-16)

This release corresponds to the
[ExoPlayer 2.18.0 release](https://github.com/google/ExoPlayer/releases/tag/r2.18.0).

*   Core library:
    *   Enable support for Android platform diagnostics via
        `MediaMetricsManager`. ExoPlayer will forward playback events and
        performance data to the platform, which helps to provide system
        performance and debugging information on the device. This data may also
        be collected by Google
        [if sharing usage and diagnostics data is enabled](https://support.google.com/accounts/answer/6078260)
        by the user of the device. Apps can opt-out of contributing to platform
        diagnostics for ExoPlayer with
        `ExoPlayer.Builder.setUsePlatformDiagnostics(false)`.
    *   Fix bug that tracks are reset too often when using `MergingMediaSource`,
        for example when side-loading subtitles and changing the selected
        subtitle mid-playback
        ([#10248](https://github.com/google/ExoPlayer/issues/10248)).
    *   Stop detecting 5G-NSA network type on API 29 and 30. These playbacks
        will assume a 4G network.
    *   Disallow passing `null` to
        `MediaSource.Factory.setDrmSessionManagerProvider` and
        `MediaSource.Factory.setLoadErrorHandlingPolicy`. Instances of
        `DefaultDrmSessionManagerProvider` and `DefaultLoadErrorHandlingPolicy`
        can be passed explicitly if required.
    *   Add `MediaItem.RequestMetadata` to represent metadata needed to play
        media when the exact `LocalConfiguration` is not known. Also remove
        `MediaMetadata.mediaUrl` as this is now included in `RequestMetadata`.
    *   Add `Player.Command.COMMAND_SET_MEDIA_ITEM` to enable players to allow
        setting a single item.
*   Track selection:
    *   Flatten `TrackSelectionOverrides` class into `TrackSelectionParameters`,
        and promote `TrackSelectionOverride` to a top level class.
    *   Rename `TracksInfo` to `Tracks` and `TracksInfo.TrackGroupInfo` to
        `Tracks.Group`. `Player.getCurrentTracksInfo` and
        `Player.Listener.onTracksInfoChanged` have also been renamed to
        `Player.getCurrentTracks` and `Player.Listener.onTracksChanged`. This
        includes 'un-deprecating' the `Player.Listener.onTracksChanged` method
        name, but with different parameter types.
    *   Change `DefaultTrackSelector.buildUponParameters` and
        `DefaultTrackSelector.Parameters.buildUpon` to return
        `DefaultTrackSelector.Parameters.Builder` instead of the deprecated
        `DefaultTrackSelector.ParametersBuilder`.
    *   Add
        `DefaultTrackSelector.Parameters.constrainAudioChannelCountToDeviceCapabilities`
        which is enabled by default. When enabled, the `DefaultTrackSelector`
        will prefer audio tracks whose channel count does not exceed the device
        output capabilities. On handheld devices, the `DefaultTrackSelector`
        will prefer stereo/mono over multichannel audio formats, unless the
        multichannel format can be
        [Spatialized](https://developer.android.com/reference/android/media/Spatializer)
        (Android 12L+) or is a Dolby surround sound format. In addition, on
        devices that support audio spatialization, the `DefaultTrackSelector`
        will monitor for changes in the
        [Spatializer properties](https://developer.android.com/reference/android/media/Spatializer.OnSpatializerStateChangedListener)
        and trigger a new track selection upon these. Devices with a
        `television`
        [UI mode](https://developer.android.com/guide/topics/resources/providing-resources#UiModeQualifier)
        are excluded from these constraints and the format with the highest
        channel count will be preferred. To enable this feature, the
        `DefaultTrackSelector` instance must be constructed with a `Context`.
*   Video:
    *   Rename `DummySurface` to `PlaceholderSurface`.
    *   Add AV1 support to the `MediaCodecVideoRenderer.getCodecMaxInputSize`.
*   Audio:
    *   Use LG AC3 audio decoder advertising non-standard MIME type.
    *   Change the return type of `AudioAttributes.getAudioAttributesV21()` from
        `android.media.AudioAttributes` to a new `AudioAttributesV21` wrapper
        class, to prevent slow ART verification on API < 21.
    *   Query the platform (API 29+) or assume the audio encoding channel count
        for audio passthrough when the format audio channel count is unset,
        which occurs with HLS chunkless preparation
        ([#10204](https://github.com/google/ExoPlayer/issues/10204)).
    *   Configure `AudioTrack` with channel mask
        `AudioFormat.CHANNEL_OUT_7POINT1POINT4` if the decoder outputs 12
        channel PCM audio
        ([#10322](#https://github.com/google/ExoPlayer/pull/10322)).
*   DRM
    *   Ensure the DRM session is always correctly updated when seeking
        immediately after a format change
        ([#10274](https://github.com/google/ExoPlayer/issues/10274)).
*   Text:
    *   Change `Player.getCurrentCues()` to return `CueGroup` instead of
        `List<Cue>`.
    *   SSA: Support `OutlineColour` style setting when `BorderStyle == 3` (i.e.
        `OutlineColour` sets the background of the cue)
        ([#8435](https://github.com/google/ExoPlayer/issues/8435)).
    *   CEA-708: Parse data into multiple service blocks and ignore blocks not
        associated with the currently selected service number.
    *   Remove `RawCcExtractor`, which was only used to handle a Google-internal
        subtitle format.
*   Extractors:
    *   Add support for AVI
        ([#2092](https://github.com/google/ExoPlayer/issues/2092)).
    *   Matroska: Parse `DiscardPadding` for Opus tracks.
    *   MP4: Parse bitrates from `esds` boxes.
    *   Ogg: Allow duplicate Opus ID and comment headers
        ([#10038](https://github.com/google/ExoPlayer/issues/10038)).
*   UI:
    *   Fix delivery of events to `OnClickListener`s set on `PlayerView`, in the
        case that `useController=false`
        ([#9605](https://github.com/google/ExoPlayer/issues/9605)). Also fix
        delivery of events to `OnLongClickListener` for all view configurations.
    *   Fix incorrectly treating a sequence of touch events that exit the bounds
        of `PlayerView` before `ACTION_UP` as a click
        ([#9861](https://github.com/google/ExoPlayer/issues/9861)).
    *   Fix `PlayerView` accessibility issue where tapping might toggle playback
        rather than hiding the controls
        ([#8627](https://github.com/google/ExoPlayer/issues/8627)).
    *   Rewrite `TrackSelectionView` and `TrackSelectionDialogBuilder` to work
        with the `Player` interface rather than `ExoPlayer`. This allows the
        views to be used with other `Player` implementations, and removes the
        dependency from the UI module to the ExoPlayer module. This is a
        breaking change.
    *   Don't show forced text tracks in the `PlayerView` track selector, and
        keep a suitable forced text track selected if "None" is selected
        ([#9432](https://github.com/google/ExoPlayer/issues/9432)).
*   DASH:
    *   Parse channel count from DTS `AudioChannelConfiguration` elements. This
        re-enables audio passthrough for DTS streams
        ([#10159](https://github.com/google/ExoPlayer/issues/10159)).
    *   Disallow passing `null` to
        `DashMediaSource.Factory.setCompositeSequenceableLoaderFactory`.
        Instances of `DefaultCompositeSequenceableLoaderFactory` can be passed
        explicitly if required.
*   HLS:
    *   Fallback to chunkful preparation if the playlist CODECS attribute does
        not contain the audio codec
        ([#10065](https://github.com/google/ExoPlayer/issues/10065)).
    *   Disallow passing `null` to
        `HlsMediaSource.Factory.setCompositeSequenceableLoaderFactory`,
        `HlsMediaSource.Factory.setPlaylistParserFactory`, and
        `HlsMediaSource.Factory.setPlaylistTrackerFactory`. Instances of
        `DefaultCompositeSequenceableLoaderFactory`,
        `DefaultHlsPlaylistParserFactory`, or a reference to
        `DefaultHlsPlaylistTracker.FACTORY` can be passed explicitly if
        required.
*   Smooth Streaming:
    *   Disallow passing `null` to
        `SsMediaSource.Factory.setCompositeSequenceableLoaderFactory`. Instances
        of `DefaultCompositeSequenceableLoaderFactory` can be passed explicitly
        if required.
*   RTSP:
    *   Add RTP reader for H263
        ([#63](https://github.com/androidx/media/pull/63)).
    *   Add RTP reader for MPEG4
        ([#35](https://github.com/androidx/media/pull/35)).
    *   Add RTP reader for HEVC
        ([#36](https://github.com/androidx/media/pull/36)).
    *   Add RTP reader for AMR. Currently only mono-channel, non-interleaved AMR
        streams are supported. Compound AMR RTP payload is not supported.
        ([#46](https://github.com/androidx/media/pull/46))
    *   Add RTP reader for VP8
        ([#47](https://github.com/androidx/media/pull/47)).
    *   Add RTP reader for WAV
        ([#56](https://github.com/androidx/media/pull/56)).
    *   Fix RTSP basic authorization header.
        ([#9544](https://github.com/google/ExoPlayer/issues/9544)).
    *   Stop checking mandatory SDP fields as ExoPlayer doesn't need them
        ([#10049](https://github.com/google/ExoPlayer/issues/10049)).
    *   Throw checked exception when parsing RTSP timing
        ([#10165](https://github.com/google/ExoPlayer/issues/10165)).
    *   Add RTP reader for VP9
        ([#47](https://github.com/androidx/media/pull/64)).
    *   Add RTP reader for OPUS
        ([#53](https://github.com/androidx/media/pull/53)).
*   Session:
    *   Replace `MediaSession.MediaItemFiller` with
        `MediaSession.Callback.onAddMediaItems` to allow asynchronous resolution
        of requests.
    *   Support `setMediaItems(s)` methods when `MediaController` connects to a
        legacy media session.
    *   Remove `MediaController.setMediaUri` and
        `MediaSession.Callback.onSetMediaUri`. The same functionality can be
        achieved by using `MediaController.setMediaItem` and
        `MediaSession.Callback.onAddMediaItems`.
    *   Forward legacy `MediaController` calls to play media to
        `MediaSession.Callback.onAddMediaItems` instead of `onSetMediaUri`.
    *   Add `MediaNotification.Provider` and `DefaultMediaNotificationProvider`
        to provide customization of the notification.
    *   Add `BitmapLoader` and `SimpleBitmapLoader` for downloading artwork
        images.
    *   Add `MediaSession.setCustomLayout()` to provide backwards compatibility
        with the legacy session.
    *   Add `MediaSession.setSessionExtras()` to provide feature parity with
        legacy session.
    *   Rename `MediaSession.MediaSessionCallback` to `MediaSession.Callback`,
        `MediaLibrarySession.MediaLibrarySessionCallback` to
        `MediaLibrarySession.Callback` and
        `MediaSession.Builder.setSessionCallback` to `setCallback`.
    *   Fix NPE in `MediaControllerImplLegacy`
        ([#59](https://github.com/androidx/media/pull/59)).
    *   Update session position info on timeline
        change([#51](https://github.com/androidx/media/issues/51)).
    *   Fix NPE in `MediaControllerImplBase` after releasing controller
        ([#74](https://github.com/androidx/media/issues/74)).
    *   Fix `IndexOutOfBoundsException` when setting less media items than in
        the current playlist
        ([#86](https://github.com/androidx/media/issues/86)).
*   Ad playback / IMA:
    *   Decrease ad polling rate from every 100ms to every 200ms, to line up
        with Media Rating Council (MRC) recommendations.
*   FFmpeg extension:
    *   Update CMake version to `3.21.0+` to avoid a CMake bug causing
        AndroidStudio's gradle sync to fail
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Remove deprecated symbols:
    *   Remove `Player.Listener.onTracksChanged(TrackGroupArray,
        TrackSelectionArray)`. Use `Player.Listener.onTracksChanged(Tracks)`
        instead.
    *   Remove `Player.getCurrentTrackGroups` and
        `Player.getCurrentTrackSelections`. Use `Player.getCurrentTracks`
        instead. You can also continue to use `ExoPlayer.getCurrentTrackGroups`
        and `ExoPlayer.getCurrentTrackSelections`, although these methods remain
        deprecated.
    *   Remove `DownloadHelper`
        `DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_VIEWPORT` and
        `DEFAULT_TRACK_SELECTOR_PARAMETERS` constants. Use
        `getDefaultTrackSelectorParameters(Context)` instead when possible, and
        `DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT` otherwise.
    *   Remove constructor `DefaultTrackSelector(ExoTrackSelection.Factory)`.
        Use `DefaultTrackSelector(Context, ExoTrackSelection.Factory)` instead.
    *   Remove `Transformer.Builder.setContext`. The `Context` should be passed
        to the `Transformer.Builder` constructor instead.

### 1.0.0-alpha03 (2022-03-14)

This release corresponds to the
[ExoPlayer 2.17.1 release](https://github.com/google/ExoPlayer/releases/tag/r2.17.1).

*   Audio:
    *   Fix error checking audio capabilities for Dolby Atmos (E-AC3-JOC) in
        HLS.
*   Extractors:
    *   FMP4: Fix issue where emsg sample metadata could be output in the wrong
        order for streams containing both v0 and v1 emsg atoms
        ([#9996](https://github.com/google/ExoPlayer/issues/9996)).
*   Text:
    *   Fix the interaction of `SingleSampleMediaSource.Factory.setTrackId` and
        `MediaItem.SubtitleConfiguration.Builder.setId` to prioritise the
        `SubtitleConfiguration` field and fall back to the `Factory` value if
        it's not set
        ([#10016](https://github.com/google/ExoPlayer/issues/10016)).
*   Ad playback:
    *   Fix audio underruns between ad periods in live HLS SSAI streams.

### 1.0.0-alpha02 (2022-03-02)

This release corresponds to the
[ExoPlayer 2.17.0 release](https://github.com/google/ExoPlayer/releases/tag/r2.17.0).

*   Core Library:
    *   Add protected method `DefaultRenderersFactory.getCodecAdapterFactory()`
        so that subclasses of `DefaultRenderersFactory` that override
        `buildVideoRenderers()` or `buildAudioRenderers()` can access the codec
        adapter factory and pass it to `MediaCodecRenderer` instances they
        create.
    *   Propagate ICY header fields `name` and `genre` to
        `MediaMetadata.station` and `MediaMetadata.genre` respectively so that
        they reach the app via `Player.Listener.onMediaMetadataChanged()`
        ([#9677](https://github.com/google/ExoPlayer/issues/9677)).
    *   Remove null keys from `DefaultHttpDataSource#getResponseHeaders`.
    *   Sleep and retry when creating a `MediaCodec` instance fails. This works
        around an issue that occurs on some devices when switching a surface
        from a secure codec to another codec
        ([#8696](https://github.com/google/ExoPlayer/issues/8696)).
    *   Add `MediaCodecAdapter.getMetrics()` to allow users obtain metrics data
        from `MediaCodec`
        ([#9766](https://github.com/google/ExoPlayer/issues/9766)).
    *   Fix Maven dependency resolution
        ([#8353](https://github.com/google/ExoPlayer/issues/8353)).
    *   Disable automatic speed adjustment for live streams that neither have
        low-latency features nor a user request setting the speed
        ([#9329](https://github.com/google/ExoPlayer/issues/9329)).
    *   Rename `DecoderCounters#inputBufferCount` to `queuedInputBufferCount`.
    *   Make `SimpleExoPlayer.renderers` private. Renderers can be accessed via
        `ExoPlayer.getRenderer`.
    *   Updated some `AnalyticsListener.EventFlags` constant values to match
        values in `Player.EventFlags`.
    *   Split `AnalyticsCollector` into an interface and default implementation
        to allow it to be stripped by R8 if an app doesn't need it.
*   Track selection:
    *   Support preferred video role flags in track selection
        ([#9402](https://github.com/google/ExoPlayer/issues/9402)).
    *   Update video track selection logic to take preferred MIME types and role
        flags into account when selecting multiple video tracks for adaptation
        ([#9519](https://github.com/google/ExoPlayer/issues/9519)).
    *   Update video and audio track selection logic to only choose formats for
        adaptive selections that have the same level of decoder and hardware
        support ([#9565](https://github.com/google/ExoPlayer/issues/9565)).
    *   Update video track selection logic to prefer more efficient codecs if
        multiple codecs are supported by primary, hardware-accelerated decoders
        ([#4835](https://github.com/google/ExoPlayer/issues/4835)).
    *   Prefer audio content preferences (for example, the "default" audio track
        or a track matching the system locale language) over technical track
        selection constraints (for example, preferred MIME type, or maximum
        channel count).
    *   Fix track selection issue where overriding one track group did not
        disable other track groups of the same type
        ([#9675](https://github.com/google/ExoPlayer/issues/9675)).
    *   Fix track selection issue where a mixture of non-empty and empty track
        overrides is not applied correctly
        ([#9649](https://github.com/google/ExoPlayer/issues/9649)).
    *   Prohibit duplicate `TrackGroup`s in a `TrackGroupArray`. `TrackGroup`s
        can always be made distinguishable by setting an `id` in the
        `TrackGroup` constructor. This fixes a crash when resuming playback
        after backgrounding the app with an active track override
        ([#9718](https://github.com/google/ExoPlayer/issues/9718)).
    *   Amend logic in `AdaptiveTrackSelection` to allow a quality increase
        under sufficient network bandwidth even if playback is very close to the
        live edge ([#9784](https://github.com/google/ExoPlayer/issues/9784)).
*   Video:
    *   Fix decoder fallback logic for Dolby Vision to use a compatible
        H264/H265 decoder if needed.
*   Audio:
    *   Fix decoder fallback logic for Dolby Atmos (E-AC3-JOC) to use a
        compatible E-AC3 decoder if needed.
    *   Change `AudioCapabilities` APIs to require passing explicitly
        `AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES` instead of `null`.
    *   Allow customization of the `AudioTrack` buffer size calculation by
        injecting an `AudioTrackBufferSizeProvider` to `DefaultAudioSink`
        ([#8891](https://github.com/google/ExoPlayer/issues/8891)).
    *   Retry `AudioTrack` creation if the requested buffer size was > 1MB
        ([#9712](https://github.com/google/ExoPlayer/issues/9712)).
*   Extractors:
    *   WAV: Add support for RF64 streams
        ([#9543](https://github.com/google/ExoPlayer/issues/9543)).
    *   Fix incorrect parsing of H.265 SPS NAL units
        ([#9719](https://github.com/google/ExoPlayer/issues/9719)).
    *   Parse Vorbis Comments (including `METADATA_BLOCK_PICTURE`) in Ogg Opus
        and Ogg Vorbis files.
*   Text:
    *   Add a `MediaItem.SubtitleConfiguration.id` field which is propagated to
        the `Format.id` field of the subtitle track created from the
        configuration
        ([#9673](https://github.com/google/ExoPlayer/issues/9673)).
    *   Add basic support for WebVTT subtitles in Matroska containers
        ([#9886](https://github.com/google/ExoPlayer/issues/9886)).
    *   Prevent `Cea708Decoder` from reading more than the declared size of a
        service block.
*   DRM:
    *   Remove `playbackLooper` from `DrmSessionManager.(pre)acquireSession`.
        When a `DrmSessionManager` is used by an app in a custom `MediaSource`,
        the `playbackLooper` needs to be passed to `DrmSessionManager.setPlayer`
        instead.
*   Ad playback / IMA:
    *   Add support for
        [IMA Dynamic Ad Insertion (DAI)](https://support.google.com/admanager/answer/6147120)
        ([#8213](https://github.com/google/ExoPlayer/issues/8213)).
    *   Add a method to `AdPlaybackState` to allow resetting an ad group so that
        it can be played again
        ([#9615](https://github.com/google/ExoPlayer/issues/9615)).
    *   Enforce playback speed of 1.0 during ad playback
        ([#9018](https://github.com/google/ExoPlayer/issues/9018)).
    *   Fix issue where an ad group that failed to load caused an immediate
        playback reset
        ([#9929](https://github.com/google/ExoPlayer/issues/9929)).
*   UI:
    *   Fix the color of the numbers in `StyledPlayerView` rewind and
        fastforward buttons when using certain themes
        ([#9765](https://github.com/google/ExoPlayer/issues/9765)).
    *   Correctly translate playback speed strings
        ([#9811](https://github.com/google/ExoPlayer/issues/9811)).
*   DASH:
    *   Add parsed essential and supplemental properties to the `Representation`
        ([#9579](https://github.com/google/ExoPlayer/issues/9579)).
    *   Support the `forced-subtitle` track role
        ([#9727](https://github.com/google/ExoPlayer/issues/9727)).
    *   Stop interpreting the `main` track role as `C.SELECTION_FLAG_DEFAULT`.
    *   Fix base URL exclusion logic for manifests that do not declare the DVB
        namespace ([#9856](https://github.com/google/ExoPlayer/issues/9856)).
    *   Support relative `MPD.Location` URLs
        ([#9939](https://github.com/google/ExoPlayer/issues/9939)).
*   HLS:
    *   Correctly populate `Format.label` for audio only HLS streams
        ([#9608](https://github.com/google/ExoPlayer/issues/9608)).
    *   Use chunkless preparation by default to improve start up time. If your
        renditions contain muxed closed-caption tracks that are **not** declared
        in the master playlist, you should add them to the master playlist to be
        available for playback, or turn off chunkless preparation with
        `HlsMediaSource.Factory.setAllowChunklessPreparation(false)`.
    *   Support key-frame accurate seeking in HLS
        ([#2882](https://github.com/google/ExoPlayer/issues/2882)).
*   RTSP:
    *   Provide a client API to override the `SocketFactory` used for any server
        connection ([#9606](https://github.com/google/ExoPlayer/pull/9606)).
    *   Prefer DIGEST authentication method over BASIC if both are present
        ([#9800](https://github.com/google/ExoPlayer/issues/9800)).
    *   Handle when RTSP track timing is not available
        ([#9775](https://github.com/google/ExoPlayer/issues/9775)).
    *   Ignore invalid RTP-Info header values
        ([#9619](https://github.com/google/ExoPlayer/issues/9619)).
*   Transformer:
    *   Increase required min API version to 21.
    *   `TransformationException` is now used to describe errors that occur
        during a transformation.
    *   Add `TransformationRequest` for specifying the transformation options.
    *   Allow multiple listeners to be registered.
    *   Fix Transformer being stuck when the codec output is partially read.
    *   Fix potential NPE in `Transformer.getProgress` when releasing the muxer
        throws.
    *   Add a demo app for applying transformations.
*   MediaSession extension:
    *   By default, `MediaSessionConnector` now clears the playlist on stop.
        Apps that want the playlist to be retained can call
        `setClearMediaItemsOnStop(false)` on the connector.
*   Cast extension:
    *   Fix bug that prevented `CastPlayer` from calling `onIsPlayingChanged`
        correctly ([#9792](https://github.com/google/ExoPlayer/issues/9792)).
    *   Support audio metadata including artwork with
        `DefaultMediaItemConverter`
        ([#9663](https://github.com/google/ExoPlayer/issues/9663)).
*   FFmpeg extension:
    *   Make `build_ffmpeg.sh` depend on LLVM's bin utils instead of GNU's
        ([#9933](https://github.com/google/ExoPlayer/issues/9933)).
*   Android 12 compatibility:
    *   Upgrade the Cast extension to depend on
        `com.google.android.gms:play-services-cast-framework:20.1.0`. Earlier
        versions of `play-services-cast-framework` are not compatible with apps
        targeting Android 12, and will crash with an `IllegalArgumentException`
        when creating `PendingIntent`s
        ([#9528](https://github.com/google/ExoPlayer/issues/9528)).
*   Remove deprecated symbols:
    *   Remove `Player.EventListener`. Use `Player.Listener` instead.
    *   Remove `MediaSourceFactory.setDrmSessionManager`,
        `MediaSourceFactory.setDrmHttpDataSourceFactory`, and
        `MediaSourceFactory.setDrmUserAgent`. Use
        `MediaSourceFactory.setDrmSessionManagerProvider` instead.
    *   Remove `MediaSourceFactory.setStreamKeys`. Use
        `MediaItem.Builder.setStreamKeys` instead.
    *   Remove `MediaSourceFactory.createMediaSource(Uri)`. Use
        `MediaSourceFactory.createMediaSource(MediaItem)` instead.
    *   Remove `setTag` from `DashMediaSource`, `HlsMediaSource` and
        `SsMediaSource`. Use `MediaItem.Builder.setTag` instead.
    *   Remove `DashMediaSource.setLivePresentationDelayMs(long, boolean)`. Use
        `MediaItem.Builder.setLiveConfiguration` and
        `MediaItem.LiveConfiguration.Builder.setTargetOffsetMs` to override the
        manifest, or `DashMediaSource.setFallbackTargetLiveOffsetMs` to provide
        a fallback value.
    *   Remove `(Simple)ExoPlayer.setThrowsWhenUsingWrongThread`. Opting out of
        the thread enforcement is no longer possible.
    *   Remove `ActionFile` and `ActionFileUpgradeUtil`. Use ExoPlayer 2.16.1 or
        before to use `ActionFileUpgradeUtil` to merge legacy action files into
        `DefaultDownloadIndex`.
    *   Remove `ProgressiveMediaSource.setExtractorsFactory`. Use
        `ProgressiveMediaSource.Factory(DataSource.Factory, ExtractorsFactory)`
        constructor instead.
    *   Remove `ProgressiveMediaSource.Factory.setTag` and
        `ProgressiveMediaSource.Factory.setCustomCacheKey`. Use
        `MediaItem.Builder.setTag` and `MediaItem.Builder.setCustomCacheKey`
        instead.
    *   Remove `DefaultRenderersFactory(Context, @ExtensionRendererMode int)`
        and `DefaultRenderersFactory(Context, @ExtensionRendererMode int, long)`
        constructors. Use the `DefaultRenderersFactory(Context)` constructor,
        `DefaultRenderersFactory.setExtensionRendererMode`, and
        `DefaultRenderersFactory.setAllowedVideoJoiningTimeMs` instead.
    *   Remove all public `CronetDataSource` constructors. Use
        `CronetDataSource.Factory` instead.
*   Change the following `IntDefs` to `@Target(TYPE_USE)` only. This may break
    the compilation of usages in Kotlin, which can be fixed by moving the
    annotation to annotate the type (`Int`).
    *   `@AacAudioObjectType`
    *   `@Ac3Util.SyncFrameInfo.StreamType`
    *   `@AdLoadException.Type`
    *   `@AdtsExtractor.Flags`
    *   `@AmrExtractor.Flags`
    *   `@AspectRatioFrameLayout.ResizeMode`
    *   `@AudioFocusManager.PlayerCommand`
    *   `@AudioSink.SinkFormatSupport`
    *   `@BinarySearchSeeker.TimestampSearchResult.Type`
    *   `@BufferReplacementMode`
    *   `@C.BufferFlags`
    *   `@C.ColorRange`
    *   `@C.ColorSpace`
    *   `@C.ColorTransfer`
    *   `@C.CryptoMode`
    *   `@C.Encoding`
    *   `@C.PcmEncoding`
    *   `@C.Projection`
    *   `@C.SelectionReason`
    *   `@C.StereoMode`
    *   `@C.VideoOutputMode`
    *   `@CacheDataSource.Flags`
    *   `@CaptionStyleCompat.EdgeType`
    *   `@DataSpec.Flags`
    *   `@DataSpec.HttpMethods`
    *   `@DecoderDiscardReasons`
    *   `@DecoderReuseResult`
    *   `@DefaultAudioSink.OutputMode`
    *   `@DefaultDrmSessionManager.Mode`
    *   `@DefaultTrackSelector.SelectionEligibility`
    *   `@DefaultTsPayloadReaderFactory.Flags`
    *   `@EGLSurfaceTexture.SecureMode`
    *   `@EbmlProcessor.ElementType`
    *   `@ExoMediaDrm.KeyRequest.RequestType`
    *   `@ExtensionRendererMode`
    *   `@Extractor.ReadResult`
    *   `@FileTypes.Type`
    *   `@FlacExtractor.Flags` (in `com.google.android.exoplayer2.ext.flac`
        package)
    *   `@FlacExtractor.Flags` (in
        `com.google.android.exoplayer2.extractor.flac` package)
    *   `@FragmentedMp4Extractor.Flags`
    *   `@HlsMediaPlaylist.PlaylistType`
    *   `@HttpDataSourceException.Type`
    *   `@IllegalClippingException.Reason`
    *   `@IllegalMergeException.Reason`
    *   `@LoadErrorHandlingPolicy.FallbackType`
    *   `@MatroskaExtractor.Flags`
    *   `@Mp3Extractor.Flags`
    *   `@Mp4Extractor.Flags`
    *   `@NotificationUtil.Importance`
    *   `@PlaybackException.FieldNumber`
    *   `@PlayerNotificationManager.Priority`
    *   `@PlayerNotificationManager.Visibility`
    *   `@PlayerView.ShowBuffering`
    *   `@Renderer.State`
    *   `@RendererCapabilities.AdaptiveSupport`
    *   `@RendererCapabilities.Capabilities`
    *   `@RendererCapabilities.DecoderSupport`
    *   `@RendererCapabilities.FormatSupport`
    *   `@RendererCapabilities.HardwareAccelerationSupport`
    *   `@RendererCapabilities.TunnelingSupport`
    *   `@SampleStream.ReadDataResult`
    *   `@SampleStream.ReadFlags`
    *   `@StyledPlayerView.ShowBuffering`
    *   `@SubtitleView.ViewType`
    *   `@TextAnnotation.Position`
    *   `@TextEmphasisSpan.MarkFill`
    *   `@TextEmphasisSpan.MarkShape`
    *   `@Track.Transformation`
    *   `@TrackOutput.SampleDataPart`
    *   `@Transformer.ProgressState`
    *   `@TsExtractor.Mode`
    *   `@TsPayloadReader.Flags`
    *   `@WebvttCssStyle.FontSizeUnit`

### 1.0.0-alpha01

AndroidX Media is the new home for media support libraries, including ExoPlayer.
The first alpha contains early, functional implementations of libraries for
implementing media use cases, including:

*   ExoPlayer, an application-level media player for Android that is easy to
    customize and extend.
*   Media session functionality, for exposing and controlling playbacks. This
    new session module uses the same `Player` interface as ExoPlayer.
*   UI components for building media playback user interfaces.
*   Modules wrapping functionality in other libraries for use with ExoPlayer,
    for example, ad insertion via the IMA SDK.

ExoPlayer was previously hosted in a separate
[ExoPlayer GitHub project](https://github.com/google/ExoPlayer). In AndroidX
Media its package name is `androidx.media3.exoplayer`. We plan to continue to
maintain and release the ExoPlayer GitHub project for a while to give apps time
to migrate. AndroidX Media has replacements for all the ExoPlayer modules,
except for the legacy media2 and mediasession extensions, which are together
replaced by the new `media3-session` module. This provides direct integration
between players and media sessions without needing to use an adapter/connector
class.
