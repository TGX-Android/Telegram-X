/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/05/2015 at 22:31
 */
package org.thunderdog.challegram.core;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;

@SuppressWarnings ("JniMissingFunction")
@Deprecated
public class Audio implements MediaPlayer.OnCompletionListener {

  public static int[] readArgs = new int[3];

  private static class AudioBuffer {
    public AudioBuffer(int capacity) {
      buffer = ByteBuffer.allocateDirect(capacity);
      bufferBytes = new byte[capacity];
    }

    ByteBuffer buffer;
    byte[] bufferBytes;
    int size;
    int finished;
    long pcmOffset;
  }

  private boolean isPaused = false;

  private MediaPlayer audioPlayer = null;
  private AudioTrack audioTrackPlayer = null;
  private int lastProgress = 0;
  private TGAudio currentAudio;
  private int playerBufferSize = 0;
  private boolean decodingFinished = false;
  private long currentTotalPcmDuration;
  private long lastPlayPcm;
  private int ignoreFirstProgress = 0;
  private int buffersUsed;
  private ArrayList<AudioBuffer> usedPlayerBuffers = new ArrayList<>();
  private ArrayList<AudioBuffer> freePlayerBuffers = new ArrayList<>();

  private final Object playerSync = new Object();
  private final Object playerObjectSync = new Object();
  private final Object sync = new Object();

  private BaseThread playerQueue;
  private BaseThread fileDecodingQueue;

  private static Audio instance;

  public static Audio instance () {
    if (instance == null) {
      instance = new Audio();
    }
    return instance;
  }

  private Audio () {
    playerBufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (playerBufferSize <= 0) {
      playerBufferSize = 3840;
    }

    for (int a = 0; a < 3; a++) {
      freePlayerBuffers.add(new AudioBuffer(playerBufferSize));
    }

    playerQueue = new BaseThread("playerQueue");
    fileDecodingQueue = new BaseThread("fileDecodingQueue");
  }

  private void checkDecoderQueue () {
    fileDecodingQueue.post(() -> {
      if (decodingFinished) {
        checkPlayerQueue();
        return;
      }
      boolean was = false;
      while (true) {
        AudioBuffer buffer = null;
        synchronized (playerSync) {
          if (!freePlayerBuffers.isEmpty()) {
            buffer = freePlayerBuffers.get(0);
            freePlayerBuffers.remove(0);
          }
          if (!usedPlayerBuffers.isEmpty()) {
            was = true;
          }
        }
        if (buffer != null) {
          N.readOpusFile(buffer.buffer, playerBufferSize, readArgs);
          buffer.size = readArgs[0];
          buffer.pcmOffset = readArgs[1];
          buffer.finished = readArgs[2];
          if (buffer.finished == 1) {
            decodingFinished = true;
          }
          if (buffer.size != 0) {
            buffer.buffer.rewind();
            buffer.buffer.get(buffer.bufferBytes);
            synchronized (playerSync) {
              usedPlayerBuffers.add(buffer);
            }
          } else {
            synchronized (playerSync) {
              freePlayerBuffers.add(buffer);
              break;
            }
          }
          was = true;
        } else {
          break;
        }
      }
      if (was) {
        checkPlayerQueue();
      }
    }, 0);
  }

  private void checkPlayerQueue() {
    playerQueue.post(() -> {
      synchronized (playerObjectSync) {
        if (audioTrackPlayer == null || audioTrackPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
          return;
        }
      }
      AudioBuffer buffer = null;
      synchronized (playerSync) {
        if (!usedPlayerBuffers.isEmpty()) {
          buffer = usedPlayerBuffers.get(0);
          usedPlayerBuffers.remove(0);
        }
      }

      if (buffer != null) {
        int count = 0;
        try {
          count = audioTrackPlayer.write(buffer.bufferBytes, 0, buffer.size);
        } catch (Throwable t) {
          Log.e("Cannot write data to audio buffer", t);
        }

        buffersUsed++;

        if (count > 0) {
          final long pcm = buffer.pcmOffset;
          final int marker = buffer.finished == 1 ? buffer.size : -1;
          final int finalBuffersUsed = buffersUsed;
          UI.post(() -> {
            lastPlayPcm = pcm;
            if (marker != -1) {
              if (audioTrackPlayer != null) {
                if (audioTrackPlayer != null) {
                  try {
                    audioTrackPlayer.setNotificationMarkerPosition(1);
                  } catch (Throwable ignored) {
                    Log.w(Log.TAG_VOICE, "setNotificationMarkerForPosition", ignored);
                  }
                }
                if (finalBuffersUsed == 1) {
                  cleanupPlayer();
                }
              }
            }
          });
        }

        if (buffer.finished != 1) {
          checkPlayerQueue();
        }
      }
      if (buffer == null || buffer.finished != 1) {
        checkDecoderQueue();
      }

      if (buffer != null) {
        synchronized (playerSync) {
          freePlayerBuffers.add(buffer);
        }
      }
    }, 0);
  }

  public void cleanupPlayer () {
    // stopProximitySensor();
    if (audioPlayer != null || audioTrackPlayer != null) {
      if (audioPlayer != null) {
        try { audioPlayer.stop(); } catch (Throwable t) { Log.e("Cannot stop audio player", t); }
        try { audioPlayer.release(); audioPlayer = null; } catch (Throwable t) { Log.e("Cannot release audio player", t); }
      } else {
        synchronized (playerObjectSync) {
          try { audioTrackPlayer.pause(); audioTrackPlayer.flush(); } catch (Throwable t) { Log.e("Cannot pause audio player", t); }
          try { audioTrackPlayer.release(); audioTrackPlayer = null; } catch (Throwable t) { Log.e("Cannot release audio player", t); }
        }
      }
      stopProgressTimer();
      loopCount = 0;
      lastLoopCount = 0;
      lastProgress = 0;
      buffersUsed = 0;
      isPaused = false;
      currentAudio.setPlaying(false);
      currentAudio.setSeekProgress(0f, 0);
      currentAudio = null;
    }
  }

  private int loopCount, lastLoopCount;

  @Override
  public void onCompletion (MediaPlayer mp) {
    if (mp.isLooping()) {
      loopCount++;
    } else {
      Media.instance().post(() -> {
        Audio.instance().cleanupPlayer();
        TdlibManager.instance().player().playNextMessageInQueue();
      });
    }
  }

  public boolean playAudio (TGAudio audio) {
    return playAudio(audio, 0, false);
  }

  public boolean playAudio (TGAudio audio, final int startTime, boolean forceRaiseState) {
    if (audio == null || StringUtils.isEmpty(audio.getPath())) {
      return false;
    }
    if ((audioTrackPlayer != null || audioPlayer != null) && currentAudio != null && audio.compare(currentAudio)) {
      if (isPaused) {
        resumeAudio(audio);
      }
      return true;
    }
    cleanupPlayer();
    final File cacheFile = new File(audio.getPath());

    inRaiseMode = false; // inRaiseMode = RaiseHelper.instance().inRaiseMode();

    if (N.isOpusFile(cacheFile.getAbsolutePath()) == 1) {
      synchronized (playerObjectSync) {
        try {
          ignoreFirstProgress = 3;
          final Semaphore semaphore = new Semaphore(0);
          final Boolean[] result = new Boolean[1];
          fileDecodingQueue.post(() -> {
            result[0] = N.openOpusFile(cacheFile.getAbsolutePath()) != 0;
            semaphore.release();
          }, 0);
          semaphore.acquire();

          if (!result[0]) {
            return false;
          }
          currentTotalPcmDuration = N.getTotalPcmDuration();

          audioTrackPlayer = new AudioTrack(inRaiseMode ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playerBufferSize, AudioTrack.MODE_STREAM);
          //noinspection deprecation
          audioTrackPlayer.setStereoVolume(1.0f, 1.0f);
          audioTrackPlayer.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached (AudioTrack audioTrack) {
              Media.instance().post(() -> TdlibManager.instance().player().playNextMessageInQueue());
            }

            @Override
            public void onPeriodicNotification (AudioTrack audioTrack) {

            }
          });
          audioTrackPlayer.play();
          startProgressTimer();
        } catch (Throwable t) {
          Log.e("Cannot open audio", t);
          if (audioTrackPlayer != null) {
            audioTrackPlayer.release();
            audioTrackPlayer = null;
            isPaused = false;
            currentAudio = null;
          }
          return false;
        }
      }
    } else {
      try {
        audioPlayer = new MediaPlayer();
        audioPlayer.setAudioStreamType(inRaiseMode ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        audioPlayer.setDataSource(cacheFile.getAbsolutePath());
        // FIXME Player.instance().modifyMediaPlayer(audioPlayer);
        audioPlayer.prepare();
        audioPlayer.start();
        audioPlayer.setOnCompletionListener(this);
        startProgressTimer();
      } catch (Throwable t) {
        Log.e(t);
        if (audioPlayer != null) {
          audioPlayer.release();
          audioPlayer = null;
          isPaused = false;
          currentAudio = null;
        }
        return false;
      }
    }

    isPaused = false;
    lastProgress = 0;
    lastPlayPcm = 0;
    currentAudio = audio;

    if (audioPlayer != null) {
      try {
        if (startTime > 0l) {
          audioPlayer.seekTo(startTime);
        } else if (currentAudio.getSeekProgress() != 0f) {
          int seekTo = (int) (audioPlayer.getDuration() * currentAudio.getSeekProgress());
          audioPlayer.seekTo(seekTo);
        }
      } catch (Throwable t2) {
        currentAudio.setSeekProgress(0f, 0);
        Log.e("Cannot seek audio", t2);
      }
    } else if (audioTrackPlayer != null) {
      if (currentAudio.getSeekProgress() == 1f) {
        currentAudio.setSeekProgress(0f, 0);
      }
      fileDecodingQueue.post(() -> {
        try {
          if (startTime > 0l) {
            lastPlayPcm = startTime;
            N.seekOpusFile((float) lastPlayPcm / (float) currentTotalPcmDuration);
          } else if (currentAudio != null && currentAudio.getSeekProgress() != 0f) {
            lastPlayPcm = (long) (currentTotalPcmDuration * currentAudio.getSeekProgress());
            N.seekOpusFile(currentAudio.getSeekProgress());
          }
        } catch (Throwable t) {
          Log.e(t);
        }
        synchronized (playerSync) {
          freePlayerBuffers.addAll(usedPlayerBuffers);
          usedPlayerBuffers.clear();
        }
        decodingFinished = false;
        checkPlayerQueue();
      }, 0);
    }

    return true;
  }

  // OLD

  private boolean inRaiseMode;

  public void changeAudioStream (boolean inRaiseMode) {
    final TGAudio audio = currentAudio;
    final boolean wasPaused = isPaused;
    if (audio == null || (audioPlayer == null && audioTrackPlayer == null)) {
      return;
    }
    if (inRaiseMode) {
      if (this.inRaiseMode) {
        if (isPaused) {
          playAudio(currentAudio);
        }
      } else {
        final int currentTime = lastProgress;
        stopAudio();
        playAudio(audio, currentTime, true);

        if (wasPaused) {
          pauseAudio(currentAudio);
        }
      }
    }
  }

  public void stopVoice () {
    if (currentAudio != null && currentAudio.isVoice() && currentAudio.getId() != 0) {
      stopAudio();
    }
  }

  public void stopAudio () {
    if (audioTrackPlayer == null && audioPlayer == null || currentAudio == null) {
      return;
    }
    try {
      if (audioPlayer != null) {
        audioPlayer.stop();
      } else {
        audioTrackPlayer.pause();
        audioTrackPlayer.flush();
      }
    } catch (Throwable t) {
      Log.e(t);
    }
    try {
      if (audioPlayer != null) {
        audioPlayer.release();
        audioPlayer = null;
      } else if (audioTrackPlayer != null) {
        synchronized (playerObjectSync) {
          audioTrackPlayer.release();
          audioTrackPlayer = null;
        }
      }
    } catch (Throwable e) {
      Log.e(e);
    }
    stopProgressTimer();
    if (currentAudio != null) {
      try {
        currentAudio.setPlaying(false);
        currentAudio.setSeekProgress(0f, 0);
      } catch (Throwable t) {
        Log.e(t);
      }
    }
    currentAudio = null;
    isPaused = false;
  }

  public boolean pauseAudio (TGAudio audio) {
    if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null || !currentAudio.compare(audio)) {
      return false;
    }
    try {
      if (audioPlayer != null) {
        audioPlayer.pause();
      } else if (audioTrackPlayer != null) {
        audioTrackPlayer.pause();
      }
      isPaused = true;
      stopProgressTimer();
    } catch (Throwable e) {
      Log.e(e);
      isPaused = false;
      return false;
    }
    return true;
  }

  public boolean resumeAudio (TGAudio audio) {
    if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null || !currentAudio.compare(audio)) {
      return false;
    }
    try {
      if (audioPlayer != null) {
        audioPlayer.start();
      } else if (audioTrackPlayer != null) {
        audioTrackPlayer.play();
        checkPlayerQueue();
      }
      isPaused = false;
      startProgressTimer();
    } catch (Throwable e) {
      Log.e(e);
      return false;
    }
    return true;
  }

  public void setLooping (TGAudio audio, boolean looping) {
    if (audioPlayer != null && currentAudio != null && audio != null && currentAudio.compare(audio)) {
      audioPlayer.setLooping(looping);
    }
  }

  private void seekOpusPlayer (final float progress) {
    if (currentTotalPcmDuration * progress == currentTotalPcmDuration) {
      return;
    }
    if (!isPaused) {
      audioTrackPlayer.pause();
    }
    audioTrackPlayer.flush();
    fileDecodingQueue.post(() -> {
      N.seekOpusFile(progress);
      synchronized (playerSync) {
        freePlayerBuffers.addAll(usedPlayerBuffers);
        usedPlayerBuffers.clear();
      }
      UI.post(() -> {
        if (!isPaused) {
          ignoreFirstProgress = 3;
          lastPlayPcm = (long) (currentTotalPcmDuration * progress);
          if (audioTrackPlayer != null) {
            audioTrackPlayer.play();
          }
          lastProgress = (int) (currentTotalPcmDuration / 48.0f * progress);
          checkPlayerQueue();
        }
      });
    }, 0);
  }

  public boolean seekToProgress (TGAudio audio, float progress) {
    if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null || !currentAudio.compare(audio)) {
      return false;
    }
    try {
      if (audioPlayer != null) {
        int seekTo = (int) (audioPlayer.getDuration() * progress);
        audioPlayer.seekTo(seekTo);
        lastProgress = seekTo;
      } else if (audioTrackPlayer != null) {
        seekOpusPlayer(progress);
      }
      if (isPaused) {
        currentAudio.setSeekProgress(progress, lastProgress / 1000);
      } else {
        startProgressTimer();
      }
    } catch (Throwable t) {
      Log.e("Cannot seek audio player", t);
      return false;
    }

    return true;
  }

  private CancellableRunnable progressTask;

  public void startProgressTimer () {
    if (progressTask != null) {
      progressTask.cancel();
      progressTask = null;
    }
    progressTask = new CancellableRunnable() {
      @Override
      public void act () {
        synchronized (sync) {
          if (currentAudio != null && (audioPlayer != null || audioTrackPlayer != null) && !isPaused) {
            final int progress;
            final float duration;

            if (audioPlayer != null) {
              progress = audioPlayer.getCurrentPosition();
              duration = (float) audioPlayer.getDuration();
            } else {
              progress = (int) (lastPlayPcm / 48.0f);
              duration = 0;
            }

            UI.post(() -> {
              if (currentAudio != null && (audioPlayer != null || audioTrackPlayer != null) && !isPaused) {
                try {
                  if (ignoreFirstProgress != 0) {
                    ignoreFirstProgress--;
                    return;
                  }

                  float value;
                  if (audioPlayer != null) {
                    value = duration == 0 ? 0 : (float) lastProgress / duration;
                    if (loopCount != lastLoopCount) {
                      if (progress >= lastProgress) {
                        return;
                      }
                      lastLoopCount = loopCount;
                      lastProgress = 0;
                    }
                    if (progress <= lastProgress) {
                      return;
                    }
                  } else {
                    value = (float) lastPlayPcm / (float) currentTotalPcmDuration;
                    if (progress == lastProgress) {
                      return;
                    }
                  }

                  synchronized (sync) {
                    if (isPending()) {
                      lastProgress = progress;
                      currentAudio.setSeekProgress(value, lastProgress / 1000);
                    }
                  }
                } catch (Throwable t) {
                  Log.e("Cannot set progress of an audio", t);
                }
              }
            });
          }
          if (isPending()) {
            Media.instance().post(progressTask, PROGRESS_DELAY);
          }
        }
      }
    };
    synchronized (sync) {
      if (progressTask.isPending()) {
        Media.instance().post(progressTask, PROGRESS_DELAY);
      }
    }
  }

  private static final int PROGRESS_DELAY = 40;

  public void stopProgressTimer () {
    synchronized (sync) {
      if (progressTask != null) {
        progressTask.cancel();
      }
    }
  }

}
