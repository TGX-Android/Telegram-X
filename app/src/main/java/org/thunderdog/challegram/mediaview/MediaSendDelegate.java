package org.thunderdog.challegram.mediaview;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;

import java.util.ArrayList;

public interface MediaSendDelegate {
  void sendSelectedItems (ArrayList<ImageFile> images, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles);
}
