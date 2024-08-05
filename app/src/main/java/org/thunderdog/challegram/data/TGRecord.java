/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 20/08/2015 at 06:57
 */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

public class TGRecord {
  private final Tdlib.Generation generation;
  private final int duration;
  private byte[] waveform;

  public TGRecord (Tdlib tdlib, Tdlib.Generation generation, int duration, byte[] waveform) {
    this.generation = generation;
    this.duration = duration;
    this.waveform = waveform;
  }

  @Override
  public boolean equals (Object o) {
    return o != null && o instanceof TGRecord && ((TGRecord) o).generation == generation;
  }

  public int getDuration () {
    return duration;
  }

  public String getPath () {
    if (generation.file.local != null && generation.file.local.isDownloadingCompleted) {
      return generation.file.local.path;
    }

    return generation.destinationPath;
  }

  public void setWaveform (byte[] waveform) {
    this.waveform = waveform;
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
}
