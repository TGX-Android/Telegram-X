package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface DownloadsListUpdateListener {
  default void updateFileAddedToDownloads (TdApi.FileDownload fileDownload, TdApi.DownloadedFileCounts counts) { }
  default void updateFileDownload (int fileId, int completeDate, boolean isPaused, TdApi.DownloadedFileCounts counts) { }
  default void updateFileDownloads (long totalSize, int totalCount, long downloadedSize) { }
  default void updateFileRemovedFromDownloads (int fileId, TdApi.DownloadedFileCounts counts) { }
}
