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
 * File created on 26/02/2017
 */
package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.ListItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class TGNetworkStats {
  public static final int TYPE_MOBILE = 0;
  public static final int TYPE_WIFI = 1;
  public static final int TYPE_ROAMING = 2;

  private static final int ENTRY_TOTAL_MEDIA = 0;
  private static final int ENTRY_TOTAL = 1;

  private static final int ENTRY_COMMON = 2;
  private static final int ENTRY_SECRET = 3;
  private static final int ENTRY_CALLS = 4;

  private static final int ENTRY_PHOTOS = 5;
  private static final int ENTRY_VIDEOS = 6;
  private static final int ENTRY_ANIMATIONS = 7;
  private static final int ENTRY_FILES = 8;
  private static final int ENTRY_MUSIC = 9;
  private static final int ENTRY_VOICE = 10;
  private static final int ENTRY_VIDEO_NOTES = 11;
  private static final int ENTRY_STICKERS = 12;
  private static final int ENTRY_OTHER_MEDIA = 13;

  private static final int ENTRIES_COUNT = 14;

  private static final int IN = 0;
  private static final int OUT = 1;
  private static final int EXTRA = 2;

  private final long[][] wifiStats = {new long[ENTRIES_COUNT], new long[ENTRIES_COUNT], new long[1]};
  private final long[][] mobileStats = {new long[ENTRIES_COUNT], new long[ENTRIES_COUNT], new long[1]};
  private final long[][] roamingStats = {new long[ENTRIES_COUNT], new long[ENTRIES_COUNT], new long[1]};
  private final long[][] otherStats = {new long[ENTRIES_COUNT], new long[ENTRIES_COUNT], new long[1]};

  private int date;

  private long[][] pickTarget (TdApi.NetworkType networkType) {
    switch (networkType.getConstructor()) {
      case TdApi.NetworkTypeMobile.CONSTRUCTOR: {
        return mobileStats;
      }
      case TdApi.NetworkTypeWiFi.CONSTRUCTOR: {
        return wifiStats;
      }
      case TdApi.NetworkTypeMobileRoaming.CONSTRUCTOR: {
        return roamingStats;
      }
      case TdApi.NetworkTypeOther.CONSTRUCTOR: {
        return otherStats;
      }
      default: {
        return null;
      }
    }
  }

  public TGNetworkStats (TdApi.NetworkStatistics stats) {
    this.date = stats.sinceDate;
    for (TdApi.NetworkStatisticsEntry abstractEntry : stats.entries) {
      switch (abstractEntry.getConstructor()) {
        case TdApi.NetworkStatisticsEntryFile.CONSTRUCTOR: {
          TdApi.NetworkStatisticsEntryFile entry = (TdApi.NetworkStatisticsEntryFile) abstractEntry;
          long[][] target = pickTarget(entry.networkType);
          if (target == null) {
            continue;
          }
          switch (entry.fileType.getConstructor()) {
            case TdApi.FileTypeNone.CONSTRUCTOR: {
              inc(target, ENTRY_COMMON, entry, false);
              break;
            }
            case TdApi.FileTypeSecret.CONSTRUCTOR: {
              inc(target, ENTRY_SECRET, entry, false);
              break;
            }
            case TdApi.FileTypeSticker.CONSTRUCTOR: {
              inc(target, ENTRY_STICKERS, entry, true);
              break;
            }
            case TdApi.FileTypePhoto.CONSTRUCTOR: {
              inc(target, ENTRY_PHOTOS, entry, true);
              break;
            }
            case TdApi.FileTypeVoiceNote.CONSTRUCTOR: {
              inc(target, ENTRY_VOICE, entry, true);
              break;
            }
            case TdApi.FileTypeVideo.CONSTRUCTOR: {
              inc(target, ENTRY_VIDEOS, entry, true);
              break;
            }
            case TdApi.FileTypeVideoNote.CONSTRUCTOR: {
              inc(target, ENTRY_VIDEO_NOTES, entry, true);
              break;
            }
            case TdApi.FileTypeDocument.CONSTRUCTOR: {
              inc(target, ENTRY_FILES, entry, true);
              break;
            }
            case TdApi.FileTypeAudio.CONSTRUCTOR: {
              inc(target, ENTRY_MUSIC, entry, true);
              break;
            }
            case TdApi.FileTypeAnimation.CONSTRUCTOR: {
              inc(target, ENTRY_ANIMATIONS, entry, true);
              break;
            }
            case TdApi.FileTypeThumbnail.CONSTRUCTOR:
            case TdApi.FileTypeProfilePhoto.CONSTRUCTOR:
            case TdApi.FileTypeUnknown.CONSTRUCTOR: {
              inc(target, ENTRY_OTHER_MEDIA, entry, true);
              break;
            }
          }
          break;
        }
        case TdApi.NetworkStatisticsEntryCall.CONSTRUCTOR: {
          TdApi.NetworkStatisticsEntryCall entry = (TdApi.NetworkStatisticsEntryCall) abstractEntry;
          long[][] target = pickTarget(entry.networkType);
          if (target == null) {
            continue;
          }
          inc(target, ENTRY_CALLS, entry, false);
          target[EXTRA][0] += entry.duration;
          break;
        }
      }
    }
  }

  private static void inc (long[][] target, int entryKey, TdApi.NetworkStatisticsEntryCall entry, boolean isMedia) {
    target[IN][entryKey] += entry.receivedBytes;
    target[OUT][entryKey] += entry.sentBytes;
    target[IN][ENTRY_TOTAL] += entry.receivedBytes;
    target[OUT][ENTRY_TOTAL] += entry.sentBytes;
    if (isMedia) {
      target[IN][ENTRY_TOTAL_MEDIA] += entry.receivedBytes;
      target[OUT][ENTRY_TOTAL_MEDIA] += entry.sentBytes;
    }
  }

  private static void inc (long[][] target, int entryKey, TdApi.NetworkStatisticsEntryFile entry, boolean isMedia) {
    target[IN][entryKey] += entry.receivedBytes;
    target[OUT][entryKey] += entry.sentBytes;
    target[IN][ENTRY_TOTAL] += entry.receivedBytes;
    target[OUT][ENTRY_TOTAL] += entry.sentBytes;
    if (isMedia) {
      target[IN][ENTRY_TOTAL_MEDIA] += entry.receivedBytes;
      target[OUT][ENTRY_TOTAL_MEDIA] += entry.sentBytes;
    }
  }

  private static String buildTotalEntry (long[][] stats) {
    long in = stats[IN][ENTRY_TOTAL];
    long out = stats[OUT][ENTRY_TOTAL];
    return buildTotalEntry(in, out);
  }

  public static String buildTotalEntry (long in, long out) {
    return Strings.buildSize(in + out);
    /*if (merge) {

    } else if (in == out && in == 0) {
      return Strings.buildSize(0l);
    } else if (in == 0) {
      return Lang.getString(R.string.SentX, Strings.buildSize(out));
    } else if (out == 0) {
      return Lang.getString(R.string.ReceivedX, Strings.buildSize(in));
    } else {
      return Lang.getString(R.string.format_receivedAndSent, Lang.getString(R.string.ReceivedX, Strings.buildSize(in)), Lang.getString(R.string.SentX, Strings.buildSize(out)));
    }*/
  }

  public String getTotalEntry () {
    long in = mobileStats[IN][ENTRY_TOTAL] + roamingStats[IN][ENTRY_TOTAL] + wifiStats[IN][ENTRY_TOTAL];
    long out = mobileStats[OUT][ENTRY_TOTAL] + roamingStats[OUT][ENTRY_TOTAL] + wifiStats[OUT][ENTRY_TOTAL];
    return buildTotalEntry(in, out);
  }

  public String getMobileEntry () {
    return buildTotalEntry(mobileStats);
  }

  public String getRoamingEntry () {
    return buildTotalEntry(roamingStats);
  }

  public String getWiFiEntry () {
    return buildTotalEntry(wifiStats);
  }

  public String getDateEntry () {
    return Lang.getString(R.string.NetworkUsageSince, getDate());
  }

  public String getDate () {
    return Lang.dateYearShortTime(date, TimeUnit.SECONDS);
  }

  private long[][] pickTarget (int type) {
    switch (type) {
      case TYPE_MOBILE: {
        return mobileStats;
      }
      case TYPE_ROAMING: {
        return roamingStats;
      }
      case TYPE_WIFI: {
        return wifiStats;
      }
    }
    return otherStats;
  }

  public boolean needShowTotalMedia (int type) {
    long[][] target = pickTarget(type);

    int mediaTypeCount = 0;
    for (int i = ENTRY_PHOTOS; i <= ENTRY_OTHER_MEDIA; i++) {
      long in = target[IN][i];
      long out = target[OUT][i];

      if (in != 0 || out != 0) {
        mediaTypeCount++;
        if (mediaTypeCount == 2) {
          break;
        }
      }
    }
    return mediaTypeCount > 1;
  }

  /*public String getTotalMediaEntry (int type) {
    return buildEntry(pickTarget(type), ENTRY_TOTAL_MEDIA, false);
  }*/

  private static void addEntry (ArrayList<ListItem> out, long bytes, int res) {
    out.add(new ListItem(ListItem.TYPE_VALUED_SETTING, 0, 0, res)
      .setStringValue(Strings.buildSize(bytes)));
  }

  private static void addEntry (ArrayList<ListItem> out, long[][] target, int key, int sentRes, int recvRes) {
    addEntry(out, target[OUT][key], sentRes);
    out.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    addEntry(out, target[IN][key], recvRes);
  }

  private static boolean check (long[][] target, int key) {
    return target[IN][key] != 0 || target[OUT][key] != 0;
  }

  public void buildEntries (ArrayList<ListItem> out, int type) {
    final long[][] target = pickTarget(type);
    //out.add(new SettingItem(SettingItem.TYPE_EMPTY_OFFSET_SMALL));
    // out.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.Total));
    // out.add(new SettingItem(SettingItem.TYPE_SHADOW_TOP));
    /*addEntry(out, target, ENTRY_TOTAL, R.string.TotalBytesSent, R.string.TotalBytesReceived);
    out.add(new SettingItem(SettingItem.TYPE_SHADOW_BOTTOM));*/

    /*if (check(target, ENTRY_TOTAL_MEDIA) && needShowTotalMedia(type)) {
      out.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.Media));
      out.add(new SettingItem(SettingItem.TYPE_SHADOW_TOP));
      addEntry(out, target, ENTRY_TOTAL_MEDIA, R.string, R.string.TotalMediaReceived);
    }*/

    boolean first = true;

    Integer[] entries = {
      ENTRY_COMMON, ENTRY_SECRET,
      ENTRY_CALLS,
      ENTRY_PHOTOS, ENTRY_VIDEOS,
      ENTRY_ANIMATIONS, ENTRY_FILES,
      ENTRY_MUSIC, ENTRY_VOICE, ENTRY_VIDEO_NOTES,
      ENTRY_STICKERS,
      ENTRY_OTHER_MEDIA
    };

    Arrays.sort(entries, (o1, o2) -> {
      long t1 = target[IN][o1] + target[OUT][o2];
      long t2 = target[IN][o2] + target[OUT][o2];
      return t1 < t2 ? 1 : t1 > t2 ? -1 : 0;
    });

    for (Integer entryObj : entries) {
      final int entry = entryObj;
      if (!check(target, entry)) {
        continue;
      }
      int headerRes;
      switch (entry) {
        case ENTRY_TOTAL:
          headerRes = R.string.TotalDataUsage;
          break;
        case ENTRY_COMMON:
          headerRes = R.string.MessagesAndData;
          break;
        case ENTRY_SECRET:
          headerRes = R.string.SecretChats;
          break;
        case ENTRY_CALLS:
          headerRes = R.string.Calls;
          break;
        case ENTRY_PHOTOS:
          headerRes = R.string.Photos;
          break;
        case ENTRY_VIDEOS:
          headerRes = R.string.Videos;
          break;
        case ENTRY_ANIMATIONS:
          headerRes = R.string.GIFs;
          break;
        case ENTRY_FILES:
          headerRes = R.string.Files;
          break;
        case ENTRY_MUSIC:
          headerRes = R.string.Music;
          break;
        case ENTRY_VOICE:
          headerRes = R.string.Voice;
          break;
        case ENTRY_VIDEO_NOTES:
          headerRes = R.string.VideoMessages;
          break;
        case ENTRY_STICKERS:
          headerRes = R.string.Stickers;
          break;
        case ENTRY_OTHER_MEDIA:
          headerRes = R.string.OtherMedia;
          break;
        default:
          continue;
      }
      if (first) {
        first = false;
        out.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      }
      out.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, headerRes));
      out.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      addEntry(out, target, entry, R.string.Sent, R.string.CountReceived);
      if (entry == ENTRY_CALLS && target[EXTRA][0] != 0) {
        out.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        out.add(new ListItem(ListItem.TYPE_VALUED_SETTING, 0, 0, R.string.Duration)
          .setStringValue(Strings.buildDuration(target[EXTRA][0])));
      }
      out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (first) {
      first = false;
      out.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    }
    out.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TotalDataUsage));
    out.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addEntry(out, target, ENTRY_TOTAL, R.string.Sent, R.string.CountReceived);
    out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }
}
