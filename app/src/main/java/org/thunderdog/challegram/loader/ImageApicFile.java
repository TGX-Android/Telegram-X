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
 * File created on 21/01/2018
 */
package org.thunderdog.challegram.loader;

import androidx.media3.extractor.metadata.id3.ApicFrame;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;

public class ImageApicFile extends ImageFile {
  private final TdApi.Message message;

  public ImageApicFile (Tdlib tdlib, TdApi.Message message) {
    super(tdlib, TD.getFile(message));
    this.message = message;
  }

  public TdApi.Message getMessage () {
    return message;
  }

  private ApicFrame resultFrame;

  public void setApicFrame (ApicFrame frame) {
    this.resultFrame = frame;
  }

  public ApicFrame getApic () {
    return resultFrame;
  }

  @Override
  protected String buildImageKey () {
    return accountId() +
      "_apic_" +
      file.id +
      "_" +
      message.chatId +
      "_" +
      message.id;
  }
}
