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
 * File created on 26/02/2017
 */
package org.thunderdog.challegram.data;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;

import java.util.ArrayList;
import java.util.Collections;

import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.SparseLongArray;
import me.vkryl.td.ChatId;

public class TGStorageStats {
  public static final int FILE_TYPE_PHOTOS = 0;
  public static final int FILE_TYPE_VIDEOS = 1;
  public static final int FILE_TYPE_VOICE = 2;
  public static final int FILE_TYPE_VIDEO_MESSAGE = 3;
  public static final int FILE_TYPE_DOCUMENTS = 4;
  public static final int FILE_TYPE_MUSIC = 5;
  public static final int FILE_TYPE_ANIMATIONS = 6;
  public static final int FILE_TYPE_SECRET = 7;
  public static final int FILE_TYPE_THUMBNAILS = 8;
  public static final int FILE_TYPE_STICKERS = 9;
  public static final int FILE_TYPE_PROFILE_PHOTOS = 10;
  public static final int FILE_TYPE_OTHER = 11;
  public static final int FILE_TYPE_WALLPAPER = 12;
  public static final int FILE_TYPE_AS_DATABASE = 13;

  private final Tdlib tdlib;
  private final LongSparseArray<Entry> chatEntries;
  private final Entry databaseEntry, totalFilesEntry;
  private final Entry otherChatsEntry, otherFilesEntry;

  private final ArrayList<Entry> chatList;

  public TGStorageStats (Tdlib tdlib, TdApi.StorageStatistics stats) {
    this.tdlib = tdlib;

    // Full
    this.databaseEntry = new Entry(tdlib, 0);
    this.totalFilesEntry = new Entry(tdlib, 0);
    this.otherFilesEntry = new Entry(tdlib, 0);

    // Unknown chat
    this.otherChatsEntry = new Entry(tdlib, 0);

    // Known chats
    this.chatEntries = new LongSparseArray<>();
    for (TdApi.StorageStatisticsByChat entry : stats.byChat) {
      for (TdApi.StorageStatisticsByFileType fileEntry : entry.byFileType) {
        int entryType;
        switch (fileEntry.fileType.getConstructor()) {
          case TdApi.FileTypeAnimation.CONSTRUCTOR: {
            entryType = FILE_TYPE_ANIMATIONS;
            break;
          }
          case TdApi.FileTypeAudio.CONSTRUCTOR: {
            entryType = FILE_TYPE_MUSIC;
            break;
          }
          case TdApi.FileTypeSticker.CONSTRUCTOR: {
            entryType = FILE_TYPE_STICKERS;
            break;
          }
          case TdApi.FileTypePhoto.CONSTRUCTOR: {
            entryType = FILE_TYPE_PHOTOS;
            break;
          }
          case TdApi.FileTypeVideo.CONSTRUCTOR: {
            entryType = FILE_TYPE_VIDEOS;
            break;
          }
          case TdApi.FileTypeVoiceNote.CONSTRUCTOR: {
            entryType = FILE_TYPE_VOICE;
            break;
          }
          case TdApi.FileTypeVideoNote.CONSTRUCTOR: {
            entryType = FILE_TYPE_VIDEO_MESSAGE;
            break;
          }
          case TdApi.FileTypeDocument.CONSTRUCTOR: {
            entryType = FILE_TYPE_DOCUMENTS;
            break;
          }
          case TdApi.FileTypeSecret.CONSTRUCTOR: {
            entryType = FILE_TYPE_SECRET;
            break;
          }
          case TdApi.FileTypeProfilePhoto.CONSTRUCTOR: {
            entryType = FILE_TYPE_PROFILE_PHOTOS;
            break;
          }
          case TdApi.FileTypeThumbnail.CONSTRUCTOR: {
            entryType = FILE_TYPE_THUMBNAILS;
            break;
          }
          case TdApi.FileTypeWallpaper.CONSTRUCTOR: {
            entryType = FILE_TYPE_WALLPAPER;
            break;
          }
          case TdApi.FileTypeSecretThumbnail.CONSTRUCTOR: {
            entryType = FILE_TYPE_AS_DATABASE;
            break;
          }
          case TdApi.FileTypeUnknown.CONSTRUCTOR:
          default: {
            entryType = FILE_TYPE_OTHER;
            break;
          }
        }
        processFileEntry(fileEntry, entryType, entry.chatId);
      }
    }

    final int size = chatEntries.size();
    LongList chatIds = new LongList(size);
    this.chatList = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Entry entry = chatEntries.valueAt(i);
      if (!entry.isEmpty()) {
        chatList.add(entry);
        chatIds.append(entry.getId());
      }
    }
    Collections.sort(chatList);
    otherChatsEntry.setExcludeChatIds(chatIds.get());
  }

  public boolean isLocalDbEmpty () {
    return databaseEntry.isEmpty();
  }

  public String getTotalDbSize () {
    return Strings.buildSize(databaseEntry.totalBytes);
  }

  public boolean isFilesEmpty () {
    return totalFilesEntry.isEmpty();
  }

  public String getFilesSize () {
    return Strings.buildSize(totalFilesEntry.totalBytes);
  }

  public long getDatabaseAddSize () {
    return databaseEntry.getSize();
  }

  private void processFileEntry (TdApi.StorageStatisticsByFileType rawEntry, int key, long chatId) {
    totalFilesEntry.processRawEntry(rawEntry, key);
    Entry entry;
    if (chatId != 0 && !isCommonType(key)) {
      entry = chatEntries.get(chatId);
      if (entry == null) {
        entry = new Entry(tdlib, chatId);
        chatEntries.put(chatId, entry);
      }
    } else {
      if (key == FILE_TYPE_AS_DATABASE) {
        entry = databaseEntry;
      } else if (isCommonType(key)) {
        entry = otherFilesEntry;
      } else {
        entry = otherChatsEntry;
      }
    }
    entry.processRawEntry(rawEntry, key);
  }

  public ArrayList<Entry> getChatList () {
    return chatList;
  }

  public Entry getTotalFilesEntry () {
    return totalFilesEntry;
  }

  public Entry getOtherChatsEntry () {
    return otherChatsEntry;
  }

  public Entry getOtherFilesEntry () {
    return otherFilesEntry;
  }

  private static boolean isCommonType (int type) {
    return type == FILE_TYPE_PROFILE_PHOTOS || type == FILE_TYPE_OTHER || type == FILE_TYPE_STICKERS || type == FILE_TYPE_THUMBNAILS || type == FILE_TYPE_WALLPAPER || type == FILE_TYPE_AS_DATABASE;
  }

  public static boolean isShouldKeepType (int type) {
    return type == FILE_TYPE_PROFILE_PHOTOS || type == FILE_TYPE_STICKERS || type == FILE_TYPE_THUMBNAILS || type == FILE_TYPE_WALLPAPER;
  }

  public static class Entry implements Comparable<Entry> {
    private final Tdlib tdlib;
    private final long id;
    private final String title;
    private final boolean isSecret, isSelfChat;

    private final ImageFile avatarFile;
    private final AvatarPlaceholder.Metadata avatarPlaceholderMetadata;

    private long totalBytes;
    private long totalCount;

    private final SparseLongArray sizes;
    private final SparseIntArray counts;

    private long[] excludeChatIds;

    public Entry (Tdlib tdlib, long chatId) {
      this.tdlib = tdlib;
      this.id = chatId;

      this.title = tdlib.chatTitle(chatId);
      this.isSelfChat = tdlib.isSelfChat(chatId);
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null) {
        this.isSecret = ChatId.isSecret(chatId);
        if (!isSelfChat && chat.photo != null && !TD.isFileEmpty(chat.photo.small)) {
          this.avatarFile = new ImageFile(tdlib, chat.photo.small);
          this.avatarFile.setSize(ChatView.getDefaultAvatarCacheSize());
          this.avatarPlaceholderMetadata = null;
        } else {
          this.avatarFile = null;
          this.avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chat, true);
        }
      } else {
        this.isSecret = false;
        this.avatarPlaceholderMetadata = new AvatarPlaceholder.Metadata();
        this.avatarFile = null;
      }

      this.sizes = new SparseLongArray();
      this.counts = new SparseIntArray();
    }

    public long getId () {
      return id;
    }

    public void setExcludeChatIds (long[] chatIds) {
      this.excludeChatIds = chatIds;
    }

    public long[] getExcludeChatIds () {
      return excludeChatIds;
    }

    public long[] getTargetChatIds () {
      return id != 0 ? new long[] {id} : null;
    }

    public String getTitle () {
      return title;
    }

    public boolean isSecret () {
      return isSecret;
    }

    public boolean isSelfChat () {
      return isSelfChat;
    }

    public ImageFile getAvatarFile () {
      return avatarFile;
    }

    public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
      return avatarPlaceholderMetadata;
    }

    public SparseLongArray getSizes () {
      return sizes;
    }

    public int getSizesCount () {
      final int size = sizes.size();
      int count = 0;
      for (int i = 0; i < size; i++) {
        if (sizes.valueAt(i) > 0) {
          count++;
        }
      }
      return count;
    }

    public SparseIntArray getCounts () {
      return counts;
    }

    public void processRawEntry (TdApi.StorageStatisticsByFileType entry, int entryType) {
      if (id != 0 && isCommonType(entryType)) {
        return;
      }

      totalBytes += entry.size;
      totalCount += entry.count;

      int i = sizes.indexOfKey(entryType);
      if (i >= 0) {
        long currentSize = sizes.valueAt(i);
        int currentCount = counts.valueAt(i);

        sizes.setValueAt(i, currentSize + entry.size);
        // FIXME copy-paste SparseIntArray to local source tree
        // Fuck you, Android. Why the hell this method is @hide?
        // counts.setValueAt(i, currentCount + entry.count)
        counts.put(entryType, currentCount + entry.count);
      } else {
        sizes.put(entryType, entry.size);
        counts.put(entryType, entry.count);
      }
    }

    public long getSize () {
      return totalBytes;
    }

    @Override
    public int compareTo (@NonNull Entry o) {
      return totalBytes > o.totalBytes ? -1 : totalBytes < o.totalBytes ? 1 : totalCount > o.totalCount ? -1 : totalCount < o.totalCount ? 1 : 0;
    }

    public boolean isEmpty () {
      return totalBytes == 0; //  && totalCount == 0;
    }
  }
}