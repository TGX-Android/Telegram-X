/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 20/08/2015 at 06:03
 */
package org.thunderdog.challegram.helper;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.SystemClock;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.data.TGRecord;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.AudioRecordJNI;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.core.lambda.CancellableRunnable;

public class Recorder implements Runnable {
  private static Recorder instance;

  public static Recorder instance () {
    if (instance == null) {
      instance = new Recorder();
    }
    return instance;
  }

  public static final int PROGRESS_FRAME_DELAY = 57;
  public static final int START_DELAY = 150;

  private static BaseThread recordThread, encodeThread;
  private Tdlib.Generation currentGeneration;
  private Tdlib.Generation generationToRemove;
  private boolean isRecording;
  private long samplesCount;
  private short[] recordSamples = new short[1024];

  private AutomaticGainControl agc;
  private NoiseSuppressor ns;
  private AcousticEchoCanceler aec;

  private Recorder () {
    recordThread = new BaseThread("RecorderThread");
    encodeThread = new BaseThread("EncoderThread");
  }

  // Public callers

  private CancellableRunnable startRunnable;

  private void setRecording (final boolean isRecording) {
    synchronized (this) {
      this.isRecording = isRecording;
    }
  }

  public void record (final Tdlib tdlib, final boolean isSecret, final Listener listener) {
    setRecording(true);
    encodeThread.post(() -> recordThread.post(startRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        startRunnable = null;
        synchronized (Recorder.this) {
          if (!isRecording) {
            return;
          }
        }
        startRecording(tdlib, isSecret, listener);
      }
    }, START_DELAY), 0);
  }

  public void save () {
    setRecording(false);
    if (SystemClock.elapsedRealtime() - recordStart < 700l) {
      cancel();
      return;
    }
    stopRecording(false);
  }

  public void cancel () {
    setRecording(false);
    stopRecording(true);
  }

  public void delete (final TGRecord record) {
    recordThread.post(() -> record.delete(), 0);
  }

  // Internal

  private void dispatchError () {
    if (currentGeneration != null) {
      tdlib.finishGeneration(currentGeneration, new TdApi.Error());
      currentGeneration = null;
    }
    encodeThread.post(() -> cleanupRecording(true), 0);
    UI.post(() -> listener.onFail());
  }

  private long recordStart;
  private int recordTimeCount;
  private long lastDispatchTime;

  public boolean isRecording () {
    return isRecording;
  }

  private void dispatchProgress () {
    if (!isRecording) {
      return;
    }
    long ms = System.currentTimeMillis();

    if (ms - lastDispatchTime >= PROGRESS_FRAME_DELAY) {
      lastDispatchTime = ms;
      recordThread.post(() -> processProgress(), PROGRESS_FRAME_DELAY);
    }
  }

  private AudioRecord recorder;
  private Tdlib tdlib;
  private Recorder.Listener listener;
  private ArrayList<ByteBuffer> buffers;
  private ByteBuffer fileBuffer;
  private int bufferSize;

  private void startRecording (Tdlib tdlib, boolean isSecret, Recorder.Listener listener) {
    this.tdlib = tdlib;
    this.listener = listener;

    final String id = "voice" + GenerationInfo.randomStamp();
    Tdlib.Generation generation = tdlib.generateFile(id, new TdApi.FileTypeVoiceNote(), isSecret, 1, 5000);

    if (generation == null) {
      dispatchError();
      return;
    }

    currentGeneration = generation;

    if (generationToRemove != null && new File(generationToRemove.destinationPath).delete()) {
      generationToRemove = null;
    }

    try {
      if (N.startRecord(generation.destinationPath) == 0) {
        dispatchError();
        return;
      }

      if (bufferSize == 0) {
        bufferSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize <= 0) {
          bufferSize = 1280;
        }
      }

      if (buffers == null) {
        buffers = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
          ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
          buffer.order(ByteOrder.nativeOrder());
          buffers.add(buffer);
        }
      }

      if (fileBuffer == null) {
        fileBuffer = ByteBuffer.allocateDirect(1920);
        fileBuffer.order(ByteOrder.nativeOrder());
      } else {
        fileBuffer.rewind();
      }

      recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 10);
    } catch (Throwable t) {
      Log.e("Couldn't set up recorder", t);
      dispatchError();
      return;
    }

    try {
      tryInitEnhancers();
      recordStart = SystemClock.elapsedRealtime();
      recordTimeCount = 0;
      removeFile = true;
      recorder.startRecording();
      initMaxAmplitude();
      dispatchRecord();
    } catch (Throwable t) {
      if (recorder != null) {
        try {
          recorder.stop();
        } catch (Throwable ignored) { }
      }
      Log.e("Couldn't start recording", t);
      dispatchError();
    }
  }

  // private File fileToRemove;

  private void cleanupRecording (boolean removeFile) {
    N.stopRecord();
    setRecording(false);
    if (currentGeneration != null) {
      tdlib.finishGeneration(currentGeneration, removeFile ? new TdApi.Error(-1, "Canceled") : null);
      if (removeFile) {
        generationToRemove = currentGeneration;
      } else if (listener != null) {
        listener.onSave(currentGeneration, Math.round((float) recordTimeCount / 1000f), getWaveform());
      }
    }
    if (recorder != null) {
      recorder.release();
      recorder = null;
    }
  }

  // Recording internal

  private void dispatchRecord () {
    recordThread.post(this, 0);
  }

  @Override
  public void run () {
    if (recorder == null) {
      return;
    }

    ByteBuffer buffer;
    if (buffers.isEmpty()) {
      buffer = ByteBuffer.allocateDirect(bufferSize);
      buffer.order(ByteOrder.nativeOrder());
    } else {
      buffer = buffers.get(0);
      buffers.remove(0);
    }
    buffer.rewind();

    int length = recorder.read(buffer, buffer.capacity());

    if (length <= 0) {
      buffers.add(buffer);
      encodeThread.post(() -> cleanupRecording(removeFile), 0);
      return;
    }

    buffer.limit(length);
    final ByteBuffer finalBuffer = buffer;
    final boolean flush = length != buffer.capacity();

    calculateMaxAmplitude(buffer, length);

    encodeThread.post(() -> processBuffer(finalBuffer, flush), 0);

    dispatchRecord();
    dispatchProgress();
  }

  private void processBuffer (final ByteBuffer buffer, boolean flush) {
    while (buffer.hasRemaining()) {
      int oldLimit = -1;
      if (buffer.remaining() > fileBuffer.remaining()) {
        oldLimit = buffer.limit();
        buffer.limit(fileBuffer.remaining() + buffer.position());
      }
      fileBuffer.put(buffer);
      if (fileBuffer.position() == fileBuffer.limit() || flush) {
        if (N.writeFrame(fileBuffer, !flush ? fileBuffer.limit() : buffer.position()) != 0) {
          fileBuffer.rewind();
          recordTimeCount += fileBuffer.limit() / 3 / 2 / 16;
        }
      }
      if (oldLimit != -1) {
        buffer.limit(oldLimit);
      }
    }
    recordThread.post(() -> buffers.add(buffer), 0);
  }

  private boolean removeFile;

  private void stopRecording (final boolean removeFile) {
    encodeThread.post(() -> {
      final boolean started;
      if (startRunnable != null) {
        startRunnable.cancel();
        startRunnable = null;
        started = false;
      } else {
        started = true;
      }
      recordThread.post(() -> {
        if (started) {
          Recorder.this.removeFile = removeFile;
          if (recorder == null) {
            return;
          }
          try {
            recorder.stop();
            tryReleaseEnhancers();
          } catch (Throwable t) {
            Log.e("Cannot stop recorder", t);
          }
        } else {
          cleanupRecording(removeFile);
        }
      }, 0);
    }, 0);
  }

  public byte[] getWaveform () {
    return N.getWaveform2(recordSamples, recordSamples.length);
  }

  // Progress

  private void processProgress () {
    if (recorder != null) {
      final float amplitude = getMaxAmplitude();
      if (listener != null && isRecording) {
        UI.post(() -> {
          listener.onAmplitude(amplitude);
          // listener.onProgress(System.currentTimeMillis() - recordStart);
        });
      }
    }
  }

  // Amplitude

  private float lastAmplitude;

  private void initMaxAmplitude () {
    lastAmplitude = 0;
    if (samplesCount > 0) {
      Arrays.fill(recordSamples, (short) 0);
      samplesCount = 0;
    }
  }

  /*private static final int MAX_PCM = 8000;
  private static final int MIN_PCM = 5500;*/

  private void calculateMaxAmplitude (ByteBuffer buffer, int length) {
    double sum = 0;
    try {
      long newSamplesCount = samplesCount + length / 2;
      int currentPart = (int) (((double) samplesCount / (double) newSamplesCount) * recordSamples.length);
      int newPart = recordSamples.length - currentPart;
      float sampleStep;
      if (currentPart != 0) {
        sampleStep = (float) recordSamples.length / (float) currentPart;
        float currentNum = 0;
        for (int a = 0; a < currentPart; a++) {
          recordSamples[a] = recordSamples[(int) currentNum];
          currentNum += sampleStep;
        }
      }
      int currentNum = currentPart;
      float nextNum = 0;
      sampleStep = (float) length / 2 / (float) newPart;
      for (int i = 0; i < length / 2; i++) {
        short peak = buffer.getShort();
        if (peak > 2500) {
          sum += peak * peak;
        }
        if (i == (int) nextNum && currentNum < recordSamples.length) {
          recordSamples[currentNum] = peak;
          nextNum += sampleStep;
          currentNum++;
        }
      }
      samplesCount = newSamplesCount;
    } catch (Throwable t) {
      Log.e(Log.TAG_VOICE, "Cannot calculate max amplitude", t);
    }
    buffer.position(0);

    lastAmplitude = (float) Math.sqrt(sum / length / 2);
  }

  private float getMaxAmplitude () {
    return lastAmplitude;
  }

  private void tryInitEnhancers () {
    try {
      if (AutomaticGainControl.isAvailable()) {
        agc = AutomaticGainControl.create(recorder.getAudioSessionId());
        if (agc != null)
          agc.setEnabled(true);
      } else {
        Log.w(Log.TAG_VOICE, "AutomaticGainControl is not available on this device");
      }
    } catch (Throwable x) {
      Log.e(Log.TAG_VOICE,"Error creating AutomaticGainControl", x);
    }

    try {
      if (NoiseSuppressor.isAvailable()) {
        ns = NoiseSuppressor.create(recorder.getAudioSessionId());
        if (ns != null)
          ns.setEnabled(AudioRecordJNI.isGoodAudioEffect(ns));
      } else {
        Log.w(Log.TAG_VOICE, "NoiseSuppressor is not available on this device");
      }
    } catch (Throwable x) {
      Log.e(Log.TAG_VOICE, "Error creating NoiseSuppressor", x);
    }

    try {
      if (AcousticEchoCanceler.isAvailable()) {
        aec = AcousticEchoCanceler.create(recorder.getAudioSessionId());
        if (aec != null)
          aec.setEnabled(AudioRecordJNI.isGoodAudioEffect(aec));
      } else {
        Log.w(Log.TAG_VOICE, "AcousticEchoCanceler is not available on this device");
      }
    } catch (Throwable x) {
      Log.e(Log.TAG_VOICE, "Error creating AcousticEchoCanceler", x);
    }
  }

  private void tryReleaseEnhancers () {
    if (agc != null) {
      agc.release();
      agc = null;
    }

    if (ns != null) {
      ns.release();
      ns = null;
    }

    if (aec != null) {
      aec.release();
      aec = null;
    }
  }

  public interface Listener {
    void onAmplitude (float amplitude);
    void onFail ();
    void onSave (Tdlib.Generation generation, int duration, byte[] waveform);
  }
}
