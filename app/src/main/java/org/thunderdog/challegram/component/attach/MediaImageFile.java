package org.thunderdog.challegram.component.attach;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.TdlibProvider;

/**
 * Date: 26/10/2016
 * Author: default
 */
class MediaImageFile extends ImageFile {
  private final long queryId;
  private final String resultId;

  public MediaImageFile (TdlibProvider tdlib, TdApi.File file, long queryId, String resultId) {
    super(tdlib, file);
    this.queryId = queryId;
    this.resultId = resultId;
  }

  public long getQueryId () {
    return queryId;
  }

  public String getResultId () {
    return resultId;
  }
}
