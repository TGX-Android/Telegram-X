/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/03/2017
 */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.gif.LottieCache;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.voip.VoIPController;

import java.io.File;

import me.vkryl.core.FileUtils;

public class TGStorageStatsFast {
  // TDLib
  private final long filesSize, databaseSize, languagePackDatabaseSize;

  // Application
  private final long pmcSize, accountsConfigSize, voipConfigSize, paintsSize, privateCameraMediaSize;
  private final long debugJunkSize, testJunkSize, oldTdlibLogJunkSize;
  private final File[] internalJunk, externalJunk, privateCameraMedia;
  private final long emojiUnusedSize, emojiUsedSize;
  private final File[] lottieFiles;
  private final long lottieSize;

  // Logs
  private final long tdlibLogsSize;
  private final Log.LogFiles logFiles;

  public TGStorageStatsFast (@Nullable TdApi.StorageStatisticsFast stats, @Nullable TGStorageStatsFast prevStats) {
    if (prevStats != null && prevStats.isEmpty())
      prevStats = null;

    // TDLib
    if (stats != null) {
      this.filesSize = stats.filesSize;
      this.databaseSize = stats.databaseSize;
      this.tdlibLogsSize = stats.logSize;
      this.languagePackDatabaseSize = stats.languagePackDatabaseSize;
    } else {
      this.filesSize = this.databaseSize = this.tdlibLogsSize = this.languagePackDatabaseSize = 0;
    }

    // Logs
    this.logFiles = Log.getLogFiles();

    // Application
    this.pmcSize = prevStats != null ? prevStats.pmcSize : Settings.instance().pmc().length();
    this.paintsSize = FileUtils.getSize(PaintState.getPaintsDir());
    this.accountsConfigSize = TdlibManager.getAccountConfigFileSize();
    this.voipConfigSize = VoIPController.getVoipConfigFileSize();

    this.lottieFiles = FileUtils.getAllFiles(LottieCache.getCacheDir());
    long lottieSize = 0;
    for (File file : lottieFiles) {
      if (file.isFile())
        lottieSize += file.length();
    }
    this.lottieSize = lottieSize;

    // Stickers

    File[] internalJunk;
    try {
      internalJunk = UI.getAppContext().getFilesDir().listFiles((dir, name) ->
        "vcf".equals(name) || "tdlib_accounts_debug.bin".equals(name) || (name.startsWith("tdlib") && name.endsWith("_debug"))
      );
    } catch (Throwable t) {
      Log.e("Unable to obtain internal junk", t);
      internalJunk = null;
    }
    this.internalJunk = internalJunk;

    File[] externalJunk;
    try {
      File file = UI.getAppContext().getExternalFilesDir(null);
      if (file != null) {
        externalJunk = file.listFiles(
          (dir, name) -> name.startsWith("x_account") && name.endsWith("_debug")
        );
      } else {
        externalJunk = null;
      }
    } catch (Throwable t) {
      Log.e("Unable to obtain external junk", t);
      externalJunk = null;
    }
    this.externalJunk = externalJunk;

    String inUseEmojiPack = Settings.instance().getEmojiPackIdentifier();
    boolean emojiBuiltIn = inUseEmojiPack.equals(BuildConfig.EMOJI_BUILTIN_ID);

    File emojiDir = Emoji.getEmojiPackDirectory();
    File[] emojiFilesUnused;
    try {
      emojiFilesUnused = emojiBuiltIn ? emojiDir.listFiles() : emojiDir.listFiles(
        (dir, name) -> !name.equals(inUseEmojiPack)
      );
    } catch (Throwable t) {
      Log.e("Unable to obtain emoji files", t);
      emojiFilesUnused = null;
    }
    this.emojiUnusedSize = FileUtils.getSize(emojiFilesUnused);
    this.emojiUsedSize = emojiBuiltIn ? 0 : FileUtils.getSize(new File(emojiDir, inUseEmojiPack));

    File[] internalMediaFiles;
    try {
      File internalMedia = U.getAlbumDir(true);
      internalMediaFiles = internalMedia.listFiles();
    } catch (Throwable t) {
      Log.e("Unable to obtain internal media files", t);
      internalMediaFiles = null;
    }
    this.privateCameraMedia = internalMediaFiles;
    this.privateCameraMediaSize = FileUtils.getSize(internalMediaFiles);

    this.debugJunkSize = FileUtils.getSize(internalJunk) + FileUtils.getSize(externalJunk);
    this.testJunkSize = FileUtils.getSize(Test.getTestDBDir());
    this.oldTdlibLogJunkSize = FileUtils.getSize(TdlibManager.getLegacyLogFile(false)) + FileUtils.getSize(TdlibManager.getLegacyLogFile(true));
  }

  public boolean deleteJunk () {
    boolean success = true;
    success = FileUtils.delete(internalJunk, true) && success;
    success = FileUtils.delete(externalJunk, true) && success;
    success = FileUtils.delete(Test.getTestDBDir(), true) && success;
    success = FileUtils.delete(TdlibManager.getLegacyLogFile(false), false) && success;
    success = FileUtils.delete(TdlibManager.getLegacyLogFile(true), false) && success;
    return success;
  }

  public boolean deleteLottieFiles () {
    return LottieCache.instance().clear();
  }

  public boolean deletePrivateCameraMedia () {
    return FileUtils.delete(privateCameraMedia, true);
  }

  public boolean deleteEmoji () {
    String inUseEmojiPack = Settings.instance().getEmojiPackIdentifier();
    boolean emojiBuiltIn = inUseEmojiPack.equals(BuildConfig.EMOJI_BUILTIN_ID);

    Settings.instance().uninstallEmojiPacks(emojiBuiltIn ? null : inUseEmojiPack);

    File emojiDir = Emoji.getEmojiPackDirectory();
    File[] emojiFilesUnused;
    try {
      emojiFilesUnused = emojiBuiltIn ? emojiDir.listFiles() : emojiDir.listFiles(
        (dir, name) -> !name.equals(inUseEmojiPack)
      );
    } catch (Throwable t) {
      Log.e("Unable to obtain emoji files", t);
      emojiFilesUnused = null;
    }

    return FileUtils.delete(emojiFilesUnused, true);
  }

  public boolean isEmpty () {
    return getTotalSize() == 0;
  }

  public long getDatabaseSize (long addSize) {
    return databaseSize + addSize;
  }

  public long getPaintsSize () {
    return paintsSize;
  }

  public long getEmojiSize () {
    return emojiUnusedSize + emojiUsedSize;
  }

  public long getPrivateCameraMediaSize () {
    return privateCameraMediaSize;
  }

  public long getEmojiUnusedSize () {
    return emojiUnusedSize;
  }

  public long getJunkSize () {
    return debugJunkSize + testJunkSize + oldTdlibLogJunkSize;
  }

  public long getLanguagePackDatabaseSize () {
    return languagePackDatabaseSize;
  }

  public long getSettingsSize () {
    return pmcSize + accountsConfigSize + voipConfigSize;
  }

  public long getTotalSize () {
    return /*TDLib*/ filesSize + databaseSize + languagePackDatabaseSize +
           /*App*/ pmcSize + accountsConfigSize + voipConfigSize + paintsSize + emojiUsedSize + emojiUnusedSize + lottieSize +
           /*Junk*/ debugJunkSize + testJunkSize + oldTdlibLogJunkSize;
  }

  public long getLogsSize () {
    return tdlibLogsSize + (logFiles != null ? logFiles.totalSize : 0);
  }

  public long getLottieSize () {
    return lottieSize;
  }

  public String getTotalSizeEntry () {
    long totalSize = getTotalSize();
    long logsSize = getLogsSize();
    if (totalSize == 0 || logsSize == 0)
      return Strings.buildSize(Math.max(totalSize, logsSize));
    else
      return Lang.getString(R.string.format_usageAndLogsSize, Strings.buildSize(totalSize), Strings.buildSize(logsSize));
  }
}
