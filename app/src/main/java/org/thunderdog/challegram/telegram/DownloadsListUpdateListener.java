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
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface DownloadsListUpdateListener {
  default void updateFileAddedToDownloads (TdApi.FileDownload fileDownload, TdApi.DownloadedFileCounts counts) { }
  default void updateFileDownload (int fileId, int completeDate, boolean isPaused, TdApi.DownloadedFileCounts counts) { }
  default void updateFileDownloads (long totalSize, int totalCount, long downloadedSize) { }
  default void updateFileRemovedFromDownloads (int fileId, TdApi.DownloadedFileCounts counts) { }
}
