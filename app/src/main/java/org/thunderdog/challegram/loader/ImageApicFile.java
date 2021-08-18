package org.thunderdog.challegram.loader;

import com.google.android.exoplayer2.metadata.id3.ApicFrame;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Date: 1/21/18
 * Author: default
 */

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
