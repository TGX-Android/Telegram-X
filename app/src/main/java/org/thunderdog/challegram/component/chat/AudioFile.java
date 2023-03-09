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
 * File created on 21/02/2018
 */
package org.thunderdog.challegram.component.chat;

import android.media.MediaMetadataRetriever;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;

import java.io.File;

public class AudioFile {
  String filePath;

  String mimeType;
  String fileName;

  int duration;
  String title, performer;

  public AudioFile (String filePath) {
    this.filePath = filePath;
    File file = new File(filePath);
    fileName = file.getName();
    parseFileName();
  }

  public AudioFile (String mimeType, String fileName, String filePath) {
    this.mimeType = mimeType;
    this.fileName = fileName;
    this.filePath = filePath;
    parseFileName();
  }

  private void parseFileName () {
    if (fileName != null && fileName.length() > 0) {
      int j = fileName.indexOf(".mp3");
      String parsed = j != -1 && j == fileName.length() - 4 && fileName.length() > 4 ? fileName.substring(0, j) : fileName;
      int i = parsed.indexOf('–');
      if (i == -1) {
        i = fileName.indexOf('—');
        if (i == -1) {
          i = fileName.indexOf('-');
        }
      }
      if (i != -1 && i != parsed.length() - 1) {
        performer = parsed.substring(0, i).trim();
        title = parsed.substring(i + 1);
      } else {
        title = null;
        performer = null;
      }
    }
  }

  public void loadId3Tags () {
    MediaMetadataRetriever retriever = null;

    try {
      retriever = U.openRetriever(filePath);

      String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
      String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      String author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);

      if (duration != null) {
        try {
          long parsed = Long.parseLong(duration);
          if (parsed > 1000l) {
            this.duration = (int) (parsed / 1000l);
          } else {
            this.duration = (int) parsed;
          }
        } catch (NumberFormatException t) {
          Log.w("Cannot parse ID3 duration: %s", t, duration);
        }
      }

      if (title != null && (title = title.trim()).length() > 0) {
        if (this.title == null) {
          this.title = title;
        }
        if (artist != null) {
          artist = artist.trim();
          if (artist.length() > 0) {
            this.title = title;
            this.performer = artist;
          }
        } else if (author != null) {
          author = author.trim();
          if (author.length() > 0) {
            this.title = title;
            this.performer = author;
          }
        }
      }
    } catch (Throwable t) {
      Log.w("cannot read id3 tags", t);
    }

    U.closeRetriever(retriever);

    if (this.title == null) {
      this.title = "";
    } else {
      this.title = title.trim();
    }
    if (this.performer == null) {
      this.performer = "";
    } else {
      this.performer = performer.trim();
    }
  }

  public int getDuration () {
    return duration;
  }

  public String getTitle () {
    return title;
  }

  public String getPerformer () {
    return performer;
  }
}
