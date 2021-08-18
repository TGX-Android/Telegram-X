/**
 * File created on 20/08/15 at 06:57
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

import java.io.File;

public class TGRecord {
  private final Tdlib.Generation generation;
  private File file;
  private final int duration;
  private byte[] waveform;
  private final TGAudio audio;

  public TGRecord (Tdlib tdlib, Tdlib.Generation generation, int duration, byte[] waveform) {
    this.generation = generation;
    this.file = new File(generation.destinationPath);
    this.duration = duration;
    this.audio = new TGAudio(tdlib, this);
    this.waveform = waveform;
  }

  @Override
  public boolean equals (Object o) {
    return o != null && o instanceof TGRecord && ((TGRecord) o).generation == generation;
  }

  public int getDuration () {
    return duration;
  }

  public File getFile () {
    return file;
  }

  public String getPath () {
    return file.getPath();
  }

  public TGAudio getAudio () {
    return audio;
  }

  public void setWaveform (byte[] waveform) {
    this.waveform = waveform;
    this.audio.setWaveform(waveform);
  }

  public byte[] getWaveform () {
    return waveform;
  }

  public int getFileId () {
    return generation.file.id;
  }

  public TdApi.InputFile toInputFile () {
    return new TdApi.InputFileId(generation.file.id);
  }

  public void delete () {
    if (file != null && file.delete()) {
      file = null;
    }
  }
}
