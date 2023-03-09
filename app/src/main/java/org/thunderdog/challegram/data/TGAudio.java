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
 * File created on 18/08/2015 at 12:20
 */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.td.Td;

@Deprecated
public class TGAudio {
  private final Tdlib tdlib;
  private final TdApi.Message msg;

  private TdApi.VoiceNote voice;
  private TdApi.Audio audio;
  private TdApi.VideoNote note;

  private float progress;
  private int seconds;

  private ImageFile audioThumb;
  private ImageFile audioCover;

  private PlayListener listener;

  public PlayListener getListener () {
    return listener;
  }

  public TGAudio (Tdlib tdlib, TdApi.Message msg, TdApi.VoiceNote voice) {
    this.tdlib = tdlib;
    this.msg = msg;
    this.voice = voice;
  }

  public TGAudio (Tdlib tdlib, TdApi.Message msg, TdApi.Audio audio) {
    this.tdlib = tdlib;
    this.msg = msg;
    this.audio = audio;
  }

  public TGAudio (Tdlib tdlib, TGRecord record) {
    this.tdlib = tdlib;
    this.msg = null;
    this.voice = new TdApi.VoiceNote(record.getDuration(), null, "audio/ogg", null, TD.newFile(record.getFile()));
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  public boolean compare (TGAudio audio) {
    return audio != null && getId() == audio.getId() && tdlib.id() == audio.tdlib.id();
  }

  public void setPlayListener (PlayListener listener) {
    this.listener = listener;
  }

  public void setPlaying (boolean isPlaying) {
    if (listener != null) {
      UI.setPlayChanged(this, isPlaying);
    }
  }

  public void setSeekProgress (float progress, int seconds) {
    if (this.progress != progress || this.seconds != seconds) {
      this.progress = progress;
      this.seconds = seconds;

      if (listener != null) {
        UI.setPlayProgress(this, progress, seconds);
      }
    }
  }

  public void setWaveform (byte[] waveform) {
    if (voice != null) {
      voice.waveform = waveform;
    }
  }

  public float getSeekProgress () {
    return progress;
  }

  public int getProgressSeconds () {
    return seconds;
  }

  public boolean isVoice () {
    return voice != null;
  }

  public boolean isMusic () {
    return audio != null;
  }

  public boolean isTemp () {
    return voice != null && msg == null;
  }

  public boolean isVideoNote () {
    return note != null;
  }

  public long getChatId () {
    return msg != null ? msg.chatId : 0l;
  }

  public long getMessageId () {
    return msg != null ? msg.id : 0;
  }

  public int getDate () {
    return msg != null ? msg.date : 0;
  }

  public int getId () {
    if (note != null) {
      return Td.getId(note.video);
    } else if (voice != null) {
      return Td.getId(voice.voice);
    } else {
      return audio == null ? 0 : Td.getId(audio.audio);
    }
  }

  public long getSize () {
    if (note != null) {
      return TD.getFileSize(note.video);
    } else if (voice != null) {
      return TD.getFileSize(voice.voice);
    } else {
      return audio == null ? 0 : TD.getFileSize(audio.audio);
    }
  }

  public String getPath () {
    if (note != null) {
      return TD.getFilePath(note.video);
    } else if (voice != null) {
      return TD.getFilePath(voice.voice);
    } else {
      return audio == null ? null : TD.getFilePath(audio.audio);
    }
  }

  public int getDuration () {
    if (note != null) {
      return note.duration;
    } else if (voice != null) {
      return voice.duration;
    } else {
      return audio == null ? 0 : audio.duration;
    }
  }

  public boolean isLoaded () {
    if (isTemp()) {
      return true;
    } else if (note != null) {
      return TD.isFileLoaded(note.video);
    } else if (voice != null) {
      return TD.isFileLoaded(voice.voice);
    } else {
      return audio != null && TD.isFileLoaded(audio.audio);
    }
  }

  public TdApi.Message getMessage () {
    return msg;
  }

  public TdApi.VoiceNote getVoice () {
    return voice;
  }

  public TdApi.Audio getAudio () {
    return audio;
  }

  public @Nullable TdApi.File getFile () {
    if (note != null) {
      return note.video;
    } else if (voice != null) {
      return voice.voice;
    } else {
      return audio != null ? audio.audio : null;
    }
  }

  @Deprecated
  public interface PlayListener {
    void onPlayChanged (TGAudio audio, int fileId, boolean isPlaying);
    void onPlayProgress (TGAudio audio, int fileId, float progress);
  }
}
