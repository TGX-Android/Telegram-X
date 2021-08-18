package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 2/20/18
 * Author: default
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, TdlibFilesManager.DOWNLOAD_FLAG_VOICE, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, TdlibFilesManager.DOWNLOAD_FLAG_FILE, TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, TdlibFilesManager.DOWNLOAD_FLAG_GIF, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE})
public @interface MediaDownloadType {}
