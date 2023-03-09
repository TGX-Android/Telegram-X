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
 * File created on 13/11/2016
 */
package org.thunderdog.challegram.emoji;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.text.Spannable;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coremedia.iso.Hex;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.EmojiCode;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.util.LocalVar;

public class Emoji {
  private static Emoji instance;

  public static Emoji instance () {
    if (instance == null) {
      synchronized (Emoji.class) {
        if (instance == null)
          instance = new Emoji();
      }
    }
    return instance;
  }

  private final HashMap<String, EmojiInfo> rects;
  private final ReferenceList<EmojiChangeListener> emojiChangeListeners = new ReferenceList<>();

  private final CountLimiter singleLimiter = new org.thunderdog.challegram.emoji.Emoji.CountLimiter() {
    @Override
    public int getEmojiCount () {
      return 0;
    }

    @Override
    public boolean incrementEmojiCount () {
      return false;
    }
  };

  public static boolean equals (String a, String b) {
    int end1 = a.length();
    while (end1 > 0 && a.charAt(end1 - 1) == '\uFE0F') {
      end1--;
    }
    int end2 = b.length();
    while (end2 > 0 && b.charAt(end2 - 1) == '\uFE0F') {
      end2--;
    }
    return (end1 == a.length() ? a : a.substring(0, end1)).equals(end2 == b.length() ? b : b.substring(0, end2));
  }

  private EmojiBitmaps bitmaps;

  private final LocalVar<StringBuilder> emojiText;

  private ArrayList<RecentEmoji> recents;
  private Map<String, String> colors;
  private Map<String, String[]> otherColors;
  private String defaultTone;
  private HashMap<String, RecentInfo> infos;

  public void addEmojiChangeListener (EmojiChangeListener listener) {
    emojiChangeListeners.add(listener);
  }

  public void removeEmojiChangeListener (EmojiChangeListener listener) {
    emojiChangeListeners.remove(listener);
  }

  public final int emojiOriginalSize;

  private Emoji () {
    this.bitmaps = new EmojiBitmaps(Settings.instance().getEmojiPackIdentifier());
    this.emojiText = new LocalVar<>();

    getRecents();
    getColors();
    getOtherColors();

    this.defaultTone = Settings.instance().getEmojiDefaultTone();

    final int sampleSize = EmojiBitmaps.calculateSampleSize();
    emojiOriginalSize = (int) (30 * EmojiCode.SCALE) / sampleSize;

    int totalCount = EmojiData.getTotalDataCount();
    this.rects = new HashMap<>(totalCount);
    for (int sectionIndex = 0; sectionIndex < EmojiData.data.length; sectionIndex++) {
      int count2 = (int) Math.ceil(EmojiData.data[sectionIndex].length / (float) EmojiCode.SPLIT_COUNT);
      for (int emojiIndex = 0; emojiIndex < EmojiData.data[sectionIndex].length; emojiIndex++) {
        int page = emojiIndex / count2;
        int position = emojiIndex - page * count2;
        int row = position % EmojiCode.COLUMNS[sectionIndex][page];
        int col = position / EmojiCode.COLUMNS[sectionIndex][page];

        int margin = (int) (EmojiCode.MARGINS[sectionIndex][page] * (EmojiCode.SCALE / sampleSize));

        int marginLeft = margin * row;
        int marginTop = margin * col;

        int left = row * emojiOriginalSize + marginLeft;
        int top = col * emojiOriginalSize + marginTop;

        Rect rect = new Rect(left, top, left + emojiOriginalSize, top + emojiOriginalSize);
        rects.put(EmojiData.data[sectionIndex][emojiIndex], new EmojiInfo(rect, sectionIndex, page));
      }
    }
  }

  public void changeEmojiPack (Settings.EmojiPack emojiPack) {
    Settings.instance().setEmojiPack(emojiPack);
    if (!emojiPack.identifier.equals(bitmaps.identifier)) {
      bitmaps.recycle();
      bitmaps = new EmojiBitmaps(emojiPack.identifier);
      TGLegacyManager.instance().notifyEmojiChanged(true);
    }
  }

  public int getReduceSize () {
    return bitmaps.scaleDp != 0f ? Screen.dp(Math.abs(bitmaps.scaleDp)) * (int) Math.signum(bitmaps.scaleDp) : 0;
  }

  private boolean installImpl (String identifier, TdApi.File file) throws IOException {
    if (!U.getSecureFileName(identifier).equals(identifier)) {
      Log.i("Emoji pack identifier is bad:%s -> %s", identifier, U.getSecureFileName(identifier));
      return false;
    }
    if (!TD.isFileLoaded(file)) {
      Log.i("Emoji pack not loaded:%s", identifier);
      return false;
    }
    File zipFile = new File(file.local.path);
    if (!zipFile.exists()) {
      Log.i("Emoji pack not found:%s", identifier);
      return false;
    }
    File targetDir = new File(getEmojiPackDirectory(), identifier);
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      Log.i("Cannot create emoji dir:%s", identifier);
      return false;
    }
    File nomedia = new File(targetDir, ".nomedia");
    if (!nomedia.exists() && !nomedia.createNewFile()) {
      Log.i("Cannot create .nomedia file:%s", identifier);
      return false;
    }
    if (!FileUtils.delete(targetDir.listFiles((dir, name) -> !name.equals(".nomedia")), true)) {
      Log.i("Cannot delete rudimentary files:%s", identifier);
      return false;
    }
    FileUtils.unzip(zipFile, targetDir);
    return true;
  }

  public void install (Settings.EmojiPack emojiPack, @NonNull RunnableBool callback) {
    Media.instance().post(() -> {
      boolean success = false;
      try {
        success = installImpl(emojiPack.identifier, emojiPack.file);
      } catch (IOException t) {
        Log.e("Unable to install emoji pack:%s", t, emojiPack.identifier);
      }
      if (success) {
        Settings.instance().markEmojiPackInstalled(emojiPack);
        FileUtils.deleteFile(emojiPack.file.local.path);
      }
      callback.runWithBool(success);
    });
  }

  // всякая хуйня с ресентами

  private static final Comparator<RecentEmoji> comparator = (o1, o2) -> {
    int c1 = o1.info.useCount;
    int c2 = o2.info.useCount;
    int t1 = o1.info.lastUseTime;
    int t2 = o2.info.lastUseTime;
    return c1 > c2 ? -1 : c1 < c2 ? 1 : t1 > t2 ? -1 : t1 < t2 ? 1 : o1.emoji.compareTo(o2.emoji);
  };

  public interface EmojiChangeListener {
    void moveEmoji (int oldIndex, int newIndex);
    void addEmoji (int newIndex, RecentEmoji emoji);
    void replaceEmoji (int newIndex, RecentEmoji emoji);
    void onToneChanged (@Nullable String newDefaultTone);
    void onCustomToneApplied (String emoji, @Nullable String newTone, @Nullable String[] newOtherTones);
  }

  public void saveRecentEmoji (String emoji) {
    int time = (int) (System.currentTimeMillis() / 1000l);
    getRecents();

    // emoji = fixEmoji(emoji);

    int oldIndex = -1;
    int i = 0;
    for (RecentEmoji oldEmoji : recents) {
      if (oldEmoji.emoji.equals(emoji)) {
        oldIndex = i;
        break;
      }
      i++;
    }

    boolean changed;

    if (oldIndex != -1) {
      RecentEmoji oldEmoji = recents.remove(oldIndex);
      oldEmoji.info.lastUseTime = time;
      oldEmoji.info.useCount++;
      final int bestIndex = Collections.binarySearch(recents, oldEmoji, comparator);
      if (bestIndex >= 0) {
        recents.add(oldIndex, oldEmoji);
        changed = false;
      } else {
        int newIndex = (-bestIndex) - 1;
        recents.add(newIndex, oldEmoji);
        if (newIndex != oldIndex) {
          for (EmojiChangeListener listener : emojiChangeListeners) {
            listener.moveEmoji(oldIndex, newIndex);
          }
          changed = true;
        } else {
          changed = false;
        }
      }
    } else {
      RecentInfo info = infos.get(emoji);
      if (info == null) {
        info = new RecentInfo(1, time);
        infos.put(emoji, info);
      } else {
        info.lastUseTime = time;
        info.useCount++;
      }

      RecentEmoji newEmoji = new RecentEmoji(emoji, info);
      int bestIndex = Collections.binarySearch(recents, newEmoji, comparator);

      if (bestIndex >= 0) {
        changed = false;
      } else {
        int newIndex = (-bestIndex) - 1;
        if (recents.size() >= RECENT_LIMIT) {
          if (newIndex < RECENT_LIMIT) {
            recents.set(newIndex, newEmoji);
            for (EmojiChangeListener listener : emojiChangeListeners) {
              listener.replaceEmoji(newIndex, newEmoji);
            }
            changed = true;
          } else {
            changed = false;
          }
        } else {
          changed = true;
          recents.add(newIndex, newEmoji);
          for (EmojiChangeListener listener : emojiChangeListeners) {
            listener.addEmoji(newIndex, newEmoji);
          }
        }
      }
    }
    saveRecents(changed);
  }

  private void saveCounters () {
    Settings.instance().setEmojiCounters(infos);
  }

  private static final int RECENT_LIMIT = 8 * 5;

  private void saveRecents (boolean changedOrder) {
    saveCounters();

    if (changedOrder) {
      Settings.instance().setEmojiRecents(recents);
    }
  }

  public boolean canClearRecents () {
    ArrayList<RecentEmoji> recentEmojis = getRecents();

    if (recentEmojis.size() != 4) {
      return true;
    }

    for (RecentEmoji emoji : recentEmojis) {
      if (emoji.info.useCount != 1) {
        return true;
      }
    }

    return false;
  }

  public void clearRecents () {
    Settings.instance().clearEmojiRecents();
    recents = new ArrayList<>();
    fillDefaultRecents();
  }

  public @NonNull ArrayList<RecentEmoji> getRecents () {
    if (recents == null) {
      infos = new HashMap<>();
      recents = new ArrayList<>();
      Settings.instance().getEmojiCounters(infos);
      Settings.instance().getEmojiRecents(infos, recents);
      if (recents.isEmpty()) {
        infos.clear();
        fillDefaultRecents();
      }
    }

    return recents;
  }

  private void fillDefaultRecents () {
    int now = (int) (System.currentTimeMillis() / 1000L);

    recents.add(new RecentEmoji("😊", new RecentInfo(1, now)));
    recents.add(new RecentEmoji("🤔", new RecentInfo(1, now - 1)));
    recents.add(new RecentEmoji("😃", new RecentInfo(1, now - 2)));
    recents.add(new RecentEmoji("👍", new RecentInfo(1, now - 3)));

    for (RecentEmoji recent : recents) {
      infos.put(recent.emoji, recent.info);
    }

    Collections.sort(recents, comparator);
  }

  // толерастия

  public @NonNull Map<String, String> getColors () {
    if (colors == null) {
      colors = new HashMap<>();
      Settings.instance().getEmojiColors(colors);
    }
    return colors;
  }

  public @NonNull Map<String, String[]> getOtherColors () {
    if (otherColors == null) {
      otherColors = new HashMap<>();
      Settings.instance().getEmojiOtherColors(otherColors);
    }
    return otherColors;
  }

  public boolean canApplyDefaultTone (String defaultTone) {
    return !StringUtils.equalsOrBothEmpty(this.defaultTone, defaultTone) || !colors.isEmpty();
  }

  private boolean clearColors () {
    SharedPreferences.Editor editor = !colors.isEmpty() && !otherColors.isEmpty() ? Settings.instance().edit() : null;
    boolean cleared = false;
    if (!colors.isEmpty()) {
      colors.clear();
      saveColors(editor);
      cleared = true;
    }
    if (!otherColors.isEmpty()) {
      otherColors.clear();
      saveOtherColors(editor);
      cleared = true;
    }
    if (editor != null)
      editor.apply();
    return cleared;
  }

  public boolean setDefaultTone (String defaultTone) {
    if (!StringUtils.equalsOrBothEmpty(this.defaultTone, defaultTone)) {
      this.defaultTone = defaultTone;
      Settings.instance().setEmojiDefaultTone(defaultTone, colors);
    } else if (!clearColors()) {
      return false;
    }

    for (EmojiChangeListener listener : emojiChangeListeners) {
      listener.onToneChanged(defaultTone);
    }

    return true;
  }

  public boolean setCustomTone (String emoji, String tone, String[] otherTones) {
    String desiredValue; // null means removed
    if (StringUtils.isEmpty(tone)) {
      desiredValue = StringUtils.isEmpty(defaultTone) ? null : "";
      otherTones = null;
    } else {
      desiredValue = StringUtils.equalsOrBothEmpty(tone, defaultTone) && otherTones == null ? null : tone;
    }
    final boolean changed;
    if (desiredValue == null) {
      changed = colors.remove(emoji) != null;
    } else {
      String currentTone = colors.get(emoji);
      if (changed = (currentTone == null || !StringUtils.equalsOrBothEmpty(currentTone, desiredValue))) {
        colors.put(emoji, desiredValue);
      }
    }
    final boolean changedOther;
    if (otherTones == null) {
      changedOther = otherColors.remove(emoji) != null || changed;
    } else {
      String[] currentOtherTones = otherColors.get(emoji);
      if (changedOther = (currentOtherTones == null || !Arrays.equals(currentOtherTones, otherTones))) {
        otherColors.put(emoji, otherTones);
      }
    }
    if (changed || changedOther) {
      SharedPreferences.Editor editor = changed && changedOther ? Settings.instance().edit() : null;
      if (changed) {
        saveColors(editor);
      }
      if (changedOther) {
        saveOtherColors(editor);
      }
      if (editor != null) {
        editor.apply();
      }
      for (EmojiChangeListener listener : emojiChangeListeners) {
        listener.onCustomToneApplied(emoji, tone, otherTones);
      }
    }
    return changed;
  }

  private void saveColors (SharedPreferences.Editor editor) {
    Settings.instance().setEmojiColors(colors, editor);
  }

  private void saveOtherColors (SharedPreferences.Editor editor) {
    Settings.instance().setEmojiOtherColors(otherColors, editor);
  }

  public String toneForEmoji (String emojiCode) {
    String color = colors.get(emojiCode);
    return color == null ? defaultTone : color.isEmpty() ? null : color;
  }

  public String[] otherTonesForEmoji (String emojiCode) {
    String[] otherTones = otherColors.get(emojiCode);
    return otherTones == null || otherTones.length == 0 ? null : otherTones;
  }

  // emoji

  @Nullable
  public static String extractSingleEmoji (String str) {
    CharSequence emoji = Emoji.instance().replaceEmoji(str);
    if (emoji instanceof Spanned) {
      EmojiSpan[] emojis = ((Spanned) emoji).getSpans(0, emoji.length(), EmojiSpan.class);
      if (emojis != null && emojis.length > 0) {
        int start = ((Spanned) emoji).getSpanStart(emojis[0]);
        int end = ((Spanned) emoji).getSpanEnd(emojis[0]);
        return start == 0 && end == emoji.length() ? emoji.toString() : emoji.subSequence(start, end).toString();
      }
    }
    return null;
  }

  public boolean isSingleEmoji (TdApi.FormattedText text) {
    boolean hasEntities = false;
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.offset != 0 || entity.length < text.text.length()) {
          return false;
        }
        //noinspection SwitchIntDef
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
            return true;
          case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
            return false;
          default:
            hasEntities = true;
            break;
        }
      }
    }
    if (hasEntities) {
      return false;
    }
    return isSingleEmoji(text.text);
  }

  public boolean isSingleEmoji (CharSequence cs) {
    return isSingleEmoji(cs, true);
  }

  public boolean isSingleEmoji (CharSequence cs, boolean allowCustom) {
    if (StringUtils.isEmpty(cs)) {
      return false;
    }
    if (cs instanceof Spanned) {
      Spanned spanned = (Spanned) cs;
      EmojiSpan[] spans = spanned.getSpans(0, cs.length(), EmojiSpan.class);
      if (spans != null) {
        if (spans.length > 1) {
          return false;
        }
        if (spans.length == 1) {
          EmojiSpan span = spans[0];
          int spanStart = spanned.getSpanStart(span);
          int spanEnd = spanned.getSpanEnd(span);
          return spanStart == 0 && spanEnd == cs.length() && (allowCustom || !span.isCustomEmoji());
        }
      }
    }
    String str = cs.toString();
    CharSequence emoji = replaceEmoji(str, 0, str.length(), singleLimiter);
    if (emoji instanceof Spanned) {
      Spanned spanned = (Spanned) emoji;
      EmojiSpan[] emojis = ((Spanned) emoji).getSpans(0, emoji.length(), EmojiSpan.class);
      if (emojis != null && emojis.length == 1) {
        EmojiSpan span = emojis[0];
        int start = spanned.getSpanStart(span);
        int end = spanned.getSpanEnd(span);
        return start == 0 && end == emoji.length() && (allowCustom || !span.isCustomEmoji());
      }
    }
    return false;
  }

  public static String toEmoji (int value) {
    return value == 0 ? "#\u20E3" : value + "\u20E3";
  }

  public static File getEmojiPackDirectory () {
    return new File(UI.getAppContext().getFilesDir(), "emoji");
  }

  public EmojiInfo getEmojiInfo (CharSequence code) {
    return getEmojiInfo(code, true);
  }

  public EmojiInfo getEmojiInfo (CharSequence codeCs, boolean allowRetry) {
    if (StringUtils.isEmpty(codeCs)) {
      return null;
    }
    String code = codeCs.toString();
    EmojiInfo info = rects.get(code);
    if (info == null) {
      String newCode = EmojiData.instance().getEmojiAlias(code);
      if (newCode != null) {
        code = newCode;
        info = rects.get(code);
      }
    }
    if (info == null && allowRetry) {
      char lastChar = code.charAt(code.length() - 1);
      if (lastChar == '\u200D' || lastChar == '\uFE0F') {
        return getEmojiInfo(code.subSequence(0, code.length() - 1), true);
      }
    }
    /*if (info == null) {
      CharSequence fixedEmoji = fixEmoji(code);
      if (!fixedEmoji.equals(code)) {
        return getEmojiInfo(fixedEmoji);
      }
    }*/
    if (info == null) {
      StringBuilder b = new StringBuilder(code.length());
      for (int i = 0; i < code.length(); i++) {
        b.append("\\u").append(Integer.toString(code.charAt(i), 16));
      }
      Log.i("Warning. No drawable for emoji: %s", b.toString());
      return null;
    }

    return info;
  }

  @Nullable
  public EmojiSpan newSpan (CharSequence code, @Nullable EmojiInfo info) {
    if (StringUtils.isEmpty(code))
      return null;
    if (info == null) {
      info = getEmojiInfo(code);
      if (info == null)
        return null;
    }
    return EmojiSpanImpl.newSpan(info);
  }

  @Nullable
  public EmojiSpan newCustomSpan (CharSequence code, @Nullable EmojiInfo info,
                                  CustomEmojiSurfaceProvider customEmojiSurfaceProvider,
                                  Tdlib tdlib, long customEmojiId) {
    if (StringUtils.isEmpty(code))
      return null;
    if (info == null) {
      info = getEmojiInfo(code);
      if (info == null)
        return null;
    }
    return CustomEmojiSpanImpl.newCustomEmojiSpan(info, customEmojiSurfaceProvider, tdlib, customEmojiId);
  }

  public CharSequence replaceEmoji (CharSequence cs) {
    return replaceEmoji(cs, 0, cs != null ? cs.length() : 0, null, null);
  }

  public CharSequence replaceEmoji (CharSequence cs, int start, int end, CountLimiter countLimiter) {
    return replaceEmoji(cs, start, end, countLimiter, null);
  }

  public CountLimiter singleLimiter () {
    return singleLimiter;
  }

  public interface CountLimiter {
    int getEmojiCount ();
    boolean incrementEmojiCount ();
  }

  public interface Callback {
    boolean onEmojiFound (CharSequence input, CharSequence code, EmojiInfo info, int position, int length);
  }

  public CharSequence replaceEmoji (CharSequence cs, int start, int end, CountLimiter countLimiter, Callback callback) {
    if (Settings.instance().useSystemEmoji() || StringUtils.isEmpty(cs)) {
      return cs;
    }
    if (start == end) {
      return "";
    }

    Spannable spannable = callback != null ? null : start == 0 && end == cs.length() && cs instanceof Spannable ? (Spannable) cs : null;
    long buf = 0;
    int emojiCount = countLimiter != null ? countLimiter.getEmojiCount() : 0;
    char c;
    int startIndex = -1;
    int startLength = 0;
    int previousGoodIndex = 0;
    StringBuilder emojiCode = emojiText.get();
    if (emojiCode == null) {
      emojiCode = new StringBuilder(16);
      emojiText.set(emojiCode);
    } else {
      emojiCode.setLength(0);
    }
    boolean doneEmoji = false;
    boolean abort = false;

    try {
      for (int i = start; i < end; i++) {
        c = cs.charAt(i);
        if (c >= 0xD83C && c <= 0xD83E || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (buf & 0xFFFF) == 0xD83C && (c >= 0xDDE6 && c <= 0xDDFF))) {
          if (startIndex == -1) {
            startIndex = i;
          }
          emojiCode.append(c);
          startLength++;
          buf <<= 16;
          buf |= c;
        } else if (emojiCode.length() > 0 && (c == 0x2640 || c == 0x2642 || c == 0x2695)) {
          emojiCode.append(c);
          startLength++;
          buf = 0;
          doneEmoji = true;
        } else if (buf > 0 && (c & 0xF000) == 0xD000) {
          emojiCode.append(c);
          startLength++;
          buf = 0;
          doneEmoji = true;
        } else if (c == 0x20E3) {
          if (i > 0) {
            char c2 = cs.charAt(previousGoodIndex);
            if ((c2 >= '0' && c2 <= '9') || c2 == '#' || c2 == '*') {
              startIndex = previousGoodIndex;
              startLength = i - previousGoodIndex + 1;
              emojiCode.append(c2);
              emojiCode.append(c);
              doneEmoji = true;
            }
          }
        } else if ((c == 0x00A9 || c == 0x00AE || c >= 0x203C && c <= 0x3299) && EmojiData.instance().containsDataChar(c)) {
          if (startIndex == -1) {
            startIndex = i;
          }
          startLength++;
          emojiCode.append(c);
          doneEmoji = true;
        } else if (startIndex != -1) {
          if (emojiCode.length() > 0) {
            // SAME CODE BEGIN
            final String code = emojiCode.toString();
            final EmojiInfo info = getEmojiInfo(code, false);
            if (info != null) {
              if (callback != null) {
                callback.onEmojiFound(cs, code, info, startIndex, startLength);
              } else if (startLength > 0) {
                EmojiSpan span = newSpan(code, info);
                if (spannable == null) {
                  spannable = Spannable.Factory.getInstance().newSpannable(start == 0 && end == cs.length() ? cs : cs.subSequence(start, end));
                }
                spannable.setSpan(span, startIndex - start, startIndex + startLength - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              }

              emojiCount++;
              if (countLimiter != null && !countLimiter.incrementEmojiCount()) {
                abort = true;
              }
            } else if (code.length() > 1) {
              int componentAddIndex = 0;
              for (int componentStartIndex = 0; componentStartIndex < code.length(); ) {
                char componentStartCode = code.charAt(componentStartIndex);
                if (componentStartCode == '\u200D' || componentStartCode == '\uFE0F') {
                  componentStartIndex++;
                  continue;
                }
                int componentLength = code.length() - componentStartIndex;
                if (componentStartIndex == 0) {
                  componentLength--;
                }
                EmojiInfo componentInfo = null;
                while (componentLength > 0 && componentInfo == null) {
                  String componentCode = code.substring(componentStartIndex, componentStartIndex + componentLength);
                  componentInfo = getEmojiInfo(componentCode, false);
                  if (componentInfo == null) {
                    componentLength--;
                    continue;
                  }
                  while (componentStartIndex + componentAddIndex + componentLength < startLength) {
                    char nextComponentChar = cs.charAt(startIndex + componentStartIndex + componentAddIndex + componentLength);
                    if (nextComponentChar == '\uFE0F') {
                      componentLength++;
                    } else {
                      break;
                    }
                  }
                  if (callback != null) {
                    callback.onEmojiFound(cs, componentCode, componentInfo, startIndex + componentStartIndex + componentAddIndex, componentLength);
                  } else {
                    EmojiSpan span = newSpan(code, info);
                    if (spannable == null) {
                      spannable = Spannable.Factory.getInstance().newSpannable(start == 0 && end == cs.length() ? cs : cs.subSequence(start, end));
                    }
                    spannable.setSpan(span, startIndex + componentStartIndex + componentAddIndex - start, startIndex + componentStartIndex + componentAddIndex + componentLength - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                  }
                  emojiCount++;
                  if (countLimiter != null && !countLimiter.incrementEmojiCount()) {
                    abort = true;
                  }
                  componentStartIndex += componentCode.length();
                  componentAddIndex += componentLength - componentCode.length();
                }
                if (componentInfo == null) {
                  componentStartIndex++;
                }
              }
            }
            // SAME CODE END
          }
          startLength = 0;
          startIndex = -1;
          emojiCode.setLength(0);
          doneEmoji = false;
        }/* else if (c != 0xfe0f) {
          if (emojiOnly != null) {
            emojiOnly[0] = 0;
            emojiOnly = null;
          }
        }*/
        if (doneEmoji && i + 2 < end) {
          char next = cs.charAt(i + 1);
          if (next == 0xD83C) {
            next = cs.charAt(i + 2);
            if (next >= 0xDFFB && next <= 0xDFFF) {
              emojiCode.append(cs, i + 1, i + 3);
              startLength += 2;
              i += 2;
            }
          } else if (emojiCode.length() >= 2 && emojiCode.charAt(0) == 0xD83C && emojiCode.charAt(1) == 0xDFF4 && next == 0xDB40) {
            i++;
            while (true) {
              emojiCode.append(cs, i, i + 2);
              startLength += 2;
              i += 2;
              if (i >= cs.length() || cs.charAt(i) != 0xDB40) {
                i--;
                break;
              }
            }
          } else if (next == 0x200D && emojiCode.length() == 2) {
            next = cs.charAt(i + 2);
            char c1 = emojiCode.charAt(0);
            char c2 = emojiCode.charAt(1);
            int successCount = 0;
            if (c1 == 0xD83C && c2 == 0xDFF4 && next == 0x2620) {
              successCount = 2;
            }
            if (successCount > 0) {
              emojiCode.append(cs, i + 1, i + 1 + successCount);
              startLength += successCount;
              i += 2;
            }
          }
        }
        previousGoodIndex = i;
        for (int a = 0; a < 3 && i + 1 < end; a++) {
          c = cs.charAt(i + 1);
          if (a == 1) {
            if (c == 0x200D && emojiCode.length() > 0) {
              emojiCode.append(c);
              i++;
              startLength++;
              doneEmoji = i + 1 == end;
            }
          } else {
            if (c >= 0xFE00 && c <= 0xFE0F) {
              i++;
              startLength++;
            }
          }
        }
        if (doneEmoji && i + 2 < end && cs.charAt(i + 1) == 0xD83C) {
          char next = cs.charAt(i + 2);
          if (next >= 0xDFFB && next <= 0xDFFF) {
            emojiCode.append(cs, i + 1, i + 3);
            startLength += 2;
            i += 2;
          }
        }
        if (doneEmoji) {
          // SAME CODE BEGIN
          final String code = emojiCode.toString();
          final EmojiInfo info = getEmojiInfo(code, false);
          if (info != null) {
            if (callback != null) {
              callback.onEmojiFound(cs, code, info, startIndex, startLength);
            } else if (startLength > 0) {
              EmojiSpan span = newSpan(code, info);
              if (spannable == null) {
                spannable = Spannable.Factory.getInstance().newSpannable(start == 0 && end == cs.length() ? cs : cs.subSequence(start, end));
              }
              spannable.setSpan(span, startIndex - start, startIndex + startLength - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            emojiCount++;
            if (countLimiter != null && !countLimiter.incrementEmojiCount()) {
              abort = true;
            }
          } else if (code.length() > 1) {
            int componentAddIndex = 0;
            for (int componentStartIndex = 0; componentStartIndex < code.length(); ) {
              char componentStartCode = code.charAt(componentStartIndex);
              if (componentStartCode == '\u200D' || componentStartCode == '\uFE0F') {
                componentStartIndex++;
                continue;
              }
              int componentLength = code.length() - componentStartIndex;
              if (componentStartIndex == 0) {
                componentLength--;
              }
              EmojiInfo componentInfo = null;
              while (componentLength > 0 && componentInfo == null) {
                String componentCode = code.substring(componentStartIndex, componentStartIndex + componentLength);
                componentInfo = getEmojiInfo(componentCode, false);
                if (componentInfo == null) {
                  componentLength--;
                  continue;
                }
                while (componentStartIndex + componentAddIndex + componentLength < startLength) {
                  char nextComponentChar = cs.charAt(startIndex + componentStartIndex + componentAddIndex + componentLength);
                  if (nextComponentChar == '\uFE0F') {
                    componentLength++;
                  } else {
                    break;
                  }
                }
                if (callback != null) {
                  callback.onEmojiFound(cs, componentCode, componentInfo, startIndex + componentStartIndex + componentAddIndex, componentLength);
                } else {
                  EmojiSpan span = newSpan(code, info);
                  if (spannable == null) {
                    spannable = Spannable.Factory.getInstance().newSpannable(start == 0 && end == cs.length() ? cs : cs.subSequence(start, end));
                  }
                  spannable.setSpan(span, startIndex + componentStartIndex + componentAddIndex - start, startIndex + componentStartIndex + componentAddIndex + componentLength - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                emojiCount++;
                if (countLimiter != null && !countLimiter.incrementEmojiCount()) {
                  abort = true;
                }
                componentStartIndex += componentCode.length();
                componentAddIndex += componentLength - componentCode.length();
              }
              if (componentInfo == null) {
                componentStartIndex++;
              }
            }
          }
          // SAME CODE END

          startLength = 0;
          startIndex = -1;
          emojiCode.setLength(0);
          doneEmoji = false;
        } else if (startIndex == -1) {
          startLength = 0;
        }
        if (abort || (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && emojiCount >= 1000)) {
          break;
        }
      }
    } catch (Text.LimitReachedException e) {
      throw e;
    } catch (Throwable t) {
      // FIXME
      Log.e("Cannot replace emoji, text:\n%s", t, start != 0 || end != cs.length() ? cs.subSequence(start, end) : cs);
      // throw new RuntimeException("replaceEmoji failed for length=" + cs.length() + " count=" + (end - start) + " start=" + start + " end=" + end + "text=" + cs.toString());
    }
    return spannable != null ? spannable : cs;
  }

  public boolean draw (@NonNull Canvas c, EmojiInfo info, Rect outRect, int alpha) {
    if (alpha == 0)
      return false;
    if (alpha == 255)
      return draw(c, info, outRect);
    Bitmap bitmap = bitmaps.getBitmap(info.page1, info.page2);
    if (bitmap != null) {
      Paint paint = Paints.getBitmapPaint();
      paint.setAlpha(alpha);
      c.drawBitmap(bitmap, info.rect, outRect, paint);
      paint.setAlpha(255);
      return true;
    } else {
      return false;
    }
  }

  public boolean draw (@NonNull Canvas c, @Nullable EmojiInfo info, Rect outRect) {
    if (info == null) {
      return false;
    }
    Bitmap bitmap = bitmaps.getBitmap(info.page1, info.page2);
    if (bitmap != null) {
      c.drawBitmap(bitmap, info.rect, outRect, Paints.getBitmapPaint());
      return true;
    } else {
      return false;
    }
  }

  public static String parseCode (String code, String charset) {
    if (StringUtils.isEmpty(code)) {
      return null;
    }
    int len = code.length();
    if (len % 2 != 0) {
      return null;
    }
    try {
      byte[] hex = Hex.decodeHex(code);
      if (hex != null && hex.length > 0) {
        return new String(hex, charset);
      }
    } catch (Throwable t) {
      Log.v(t);
    }
    return null;
  }

  // Vibration

  public static final int VIBRATION_PATTERN_NONE = 0;
  public static final int VIBRATION_PATTERN_HEARTBEAT = 1;
  public static final int VIBRATION_PATTERN_BROKEN_HEART = 2;
  public static final int VIBRATION_PATTERN_HEART_CUPID = 3;
  public static final int VIBRATION_PATTERN_CAT_IN_LOVE = 4;

  public int getVibrationPatternType (String emoji) {
    if (StringUtils.isEmpty(emoji)) {
      return VIBRATION_PATTERN_NONE;
    }
    switch (cleanEmojiCode(emoji)) {
      case "\u2764": /* ❤ */
      case "\uD83E\uDDE1": /* 🧡 */
      case "\uD83D\uDC9B": /* 💛 */
      case "\uD83D\uDC9A": /* 💚 */
      case "\uD83D\uDC99": /* 💙 */
      case "\uD83D\uDC9C": /* 💜 */
      case "\uD83E\uDD0E": /* 🤎 */
      case "\uD83E\uDD0D": /* 🤍 */
      case "\uD83D\uDDA4": /* 🖤 */
      case "\uD83D\uDE0D": /* 😍 */
        return VIBRATION_PATTERN_HEARTBEAT;
      case "\uD83D\uDC94": /* 💔 */
        return VIBRATION_PATTERN_BROKEN_HEART;
      case "\uD83D\uDC98": /* 💘 */
        return VIBRATION_PATTERN_HEART_CUPID;
      case "\uD83D\uDE3B": /* 😻 */
        return VIBRATION_PATTERN_CAT_IN_LOVE;
    }
    return VIBRATION_PATTERN_NONE;
  }

  public String cleanEmojiCode (String code) {
    if (code.endsWith("\uFE0F"))
      return code.substring(0, code.length() - 1);
    return code;
  }
}
