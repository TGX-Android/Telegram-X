# Synthetic preview fixtures

These files contain generated test data only. The device checks render them locally and never send them to Telegram.

Recreate with FFmpeg:

```sh
ffmpeg -f lavfi -i 'color=c=0x3685d5:s=160x120:r=12:d=1' -c:v libx264 -pix_fmt yuv420p -an -movflags +faststart preview-video.mp4
ffmpeg -f lavfi -i 'sine=frequency=440:duration=1' -c:a libopus -ar 48000 -ac 1 preview-audio.ogg
```

The test creates its document and WebP thumbnail at runtime and removes all copied files afterward. These fixtures exercise native content rendering and cancellation; they do not establish a prepared-message server success.
