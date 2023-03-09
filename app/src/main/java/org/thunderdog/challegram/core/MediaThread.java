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
 * File created on 03/05/2015 at 18:09
 */
package org.thunderdog.challegram.core;

import android.os.Message;

import org.thunderdog.challegram.data.TGAudio;

public class MediaThread extends BaseThread {
  private static final int ACTION_PLAY = 1;
  private static final int ACTION_PAUSE = 2;
  private static final int ACTION_SEEK = 3;
  private static final int ACTION_STOP = 4;
  private static final int ACTION_STOP_VOICE = 5;
  private static final int ACTION_SET_LOOPING = 6;
  private static final int ACTION_CHANGE_STREAM = 7;

  private static final int GET_GALLERY_PHOTOS = 100;
  private static final int GET_GALLERY_PHOTOS_AND_VIDEOS = 101;

  public MediaThread () {
    super("MediaThread");
  }

  public void playAudio (TGAudio audio) {
    sendMessage(Message.obtain(getHandler(), ACTION_PLAY, audio), 0);
  }

  public void changeAudioStream (boolean inRaiseMode) {
    sendMessage(Message.obtain(getHandler(), ACTION_CHANGE_STREAM, inRaiseMode ? 1 : 0, 0), 0);
  }

  public void pauseAudio (TGAudio audio) {
    sendMessage(Message.obtain(getHandler(), ACTION_PAUSE, audio), 0);
  }

  public void seekAudio (TGAudio audio, float progress) {
    sendMessage(Message.obtain(getHandler(), ACTION_SEEK, Float.floatToIntBits(progress), 0, audio), 0);
  }

  public void stopAudio () {
    sendMessage(Message.obtain(getHandler(), ACTION_STOP), 0);
  }

  public void stopVoice () {
    sendMessage(Message.obtain(getHandler(), ACTION_STOP_VOICE), 0);
  }

  public void setLooping (TGAudio audio, boolean looping) {
    sendMessage(Message.obtain(getHandler(), ACTION_SET_LOOPING, looping ? 1 : 0, 0, audio), 0);
  }

  public void getGalleryPhotos (long date, Media.GalleryCallback callback, boolean allowVideo) {
    if (date == 0l) {
      sendMessage(Message.obtain(getHandler(), allowVideo ? GET_GALLERY_PHOTOS_AND_VIDEOS : GET_GALLERY_PHOTOS, 0, 0, callback), 0);
    } else {
      sendMessage(Message.obtain(getHandler(), allowVideo ? GET_GALLERY_PHOTOS_AND_VIDEOS : GET_GALLERY_PHOTOS, (int) (date >> 32), (int) date, callback), 0);
    }
  }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case ACTION_PLAY: {
        Media.instance().playAudio((TGAudio) msg.obj);
        break;
      }
      case ACTION_PAUSE: {
        Media.instance().pauseAudio((TGAudio) msg.obj);
        break;
      }
      case ACTION_SET_LOOPING: {
        Media.instance().setLooping((TGAudio) msg.obj, msg.arg1 == 1);
        break;
      }
      case ACTION_CHANGE_STREAM: {
        Media.instance().changeAudioStream(msg.arg1 == 1);
        break;
      }
      case ACTION_SEEK: {
        Media.instance().seekAudio((TGAudio) msg.obj, Float.intBitsToFloat(msg.arg1));
        break;
      }
      case ACTION_STOP: {
        Media.instance().stopAudio();
        break;
      }
      case ACTION_STOP_VOICE: {
        Media.instance().stopVoice();
        break;
      }
      case GET_GALLERY_PHOTOS:
      case GET_GALLERY_PHOTOS_AND_VIDEOS: {
        Media.instance().getGalleryPhotos(msg.arg1 == 0 && msg.arg2 == 0 ? 0 : (long) msg.arg1 << 32 | msg.arg2 & 0xFFFFFFFFL, (Media.GalleryCallback) msg.obj, msg.what == GET_GALLERY_PHOTOS_AND_VIDEOS);
        break;
      }
    }
  }
}
