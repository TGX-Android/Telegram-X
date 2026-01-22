# MPEG-H decoder module

The MPEG-H module provides `MpeghAudioRenderer`, which uses the libmpegh native
library to decode MPEG-H audio.

## License note

Please note that while the code in this repository is licensed under
[Apache 2.0][], using this module also requires building and including the
[Fraunhofer GitHub MPEG-H decoder][] which is licensed under the Fraunhofer
GitHub MPEG-H decoder project.

[Apache 2.0]: ../../LICENSE
[Fraunhofer GitHub MPEG-H decoder]: https://github.com/Fraunhofer-IIS/mpeghdec

## Build instructions (Linux, macOS)

To use the module you need to clone this GitHub project and depend on its
modules locally. Instructions for doing this can be found in the
[top level README][].

In addition, it's necessary to fetch libmpegh library as follows:

*   Set the following environment variables:

```
cd "<path to project checkout>"
MPEGH_MODULE_PATH="$(pwd)/libraries/decoder_mpegh/src/main"
```

*   Fetch libmpegh library:

```
cd "${MPEGH_MODULE_PATH}/jni" && \
git clone https://github.com/Fraunhofer-IIS/mpeghdec.git --branch r2.0.0 libmpegh
```

*   [Install CMake][].

Having followed these steps, gradle will build the module automatically when run
on the command line or via Android Studio, using [CMake][] and [Ninja][] to
configure and build mpeghdec and the module's [JNI wrapper library][].

[top level README]: ../../README.md
[Install CMake]: https://developer.android.com/studio/projects/install-ndk
[CMake]: https://cmake.org/
[Ninja]: https://ninja-build.org
[JNI wrapper library]: src/main/jni/mpeghdec_jni.cc

## Build instructions (Windows)

We do not provide support for building this module on Windows, however it should
be possible to follow the Linux instructions in [Windows PowerShell][].

[Windows PowerShell]: https://docs.microsoft.com/en-us/powershell/scripting/getting-started/getting-started-with-windows-powershell

## Using the module with ExoPlayer

Once you've followed the instructions above to check out, build and depend on
the module, the next step is to tell ExoPlayer to use `MpeghAudioRenderer`. How
you do this depends on which player API you're using:

*   If you're passing a `DefaultRenderersFactory` to `ExoPlayer.Builder`, you
    can enable using the module by setting the `extensionRendererMode` parameter
    of the `DefaultRenderersFactory` constructor to
    `EXTENSION_RENDERER_MODE_ON`. This will use `MpeghAudioRenderer` for
    playback if `MediaCodecAudioRenderer` doesn't support the input format. Pass
    `EXTENSION_RENDERER_MODE_PREFER` to give `MpeghAudioRenderer` priority over
    `MediaCodecAudioRenderer`.
*   If you've subclassed `DefaultRenderersFactory`, add a `MpeghAudioRenderer`
    to the output list in `buildAudioRenderers`. ExoPlayer will use the first
    `Renderer` in the list that supports the input media format.
*   If you've implemented your own `RenderersFactory`, return a
    `MpeghAudioRenderer` instance from `createRenderers`. ExoPlayer will use the
    first `Renderer` in the returned array that supports the input media format.
*   If you're using `ExoPlayer.Builder`, pass a `MpeghAudioRenderer` in the
    array of `Renderer`s. ExoPlayer will use the first `Renderer` in the list
    that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `MpeghAudioRenderer` to the player,
then implement your own logic to use the renderer for a given track.

## Links

*   [Troubleshooting using decoding extensions][]

[Troubleshooting using decoding extensions]: https://developer.android.com/media/media3/exoplayer/troubleshooting#how-can-i-get-a-decoding-library-to-load-and-be-used-for-playback
