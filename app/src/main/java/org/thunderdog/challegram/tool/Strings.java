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
 * File created on 25/04/2015 at 13:16
 */
package org.thunderdog.challegram.tool;

import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.td.Td;

@SuppressWarnings(value = "SpellCheckingInspection")
public class Strings {
  public static final String LTR_CHAR = "\u200E";
  public static final String RTL_CHAR = "\u200F";
  public static final String NBSP_CHAR = "\u00A0";
  // public static final int DIRECTION_UNKNOWN = -1;
  public static final int DIRECTION_NEUTRAL = 0;
  public static final int DIRECTION_LTR = 1;
  public static final int DIRECTION_RTL = 2;

  public static String DOT_SEPARATOR = " • ";

  public static String ELLIPSIS = "…";

  public static final String SCOPE_END = "\u2069";
  // public static final String INVISIBLE_SEPARATOR = "\u2063";

  public static boolean hostsEqual (String url1, String url2) {
    if (!StringUtils.isEmpty(url1) && !StringUtils.isEmpty(url2)) {
      try {
        Uri u1 = wrapHttps(url1);
        if (u1 == null)
          return false;
        Uri u2 = wrapHttps(url2);
        if (u2 == null)
          return false;
        String h1 = unwrapWww(u1.getHost());
        String h2 = unwrapWww(u2.getHost());
        return h1.equals(h2) || h1.endsWith(h2) || h2.endsWith(h1);
      } catch (Throwable t) {
        return false;
      }
    }
    return false;
  }

  public interface CharacterCounter {
    boolean accept (char c);
  }

  public static int countCharacters (String text, int startIndex, int endIndex, CharacterCounter counter) {
    int count = 0;
    for (int i = startIndex; i < endIndex; i++) {
      if (counter.accept(text.charAt(i))) {
        count++;
      }
    }
    return count;
  }

  public static String toString (float x) {
    StringBuilder b = new StringBuilder();
    b.append((int) x);
    float fp = x - (int) x;
    String str = Float.toString(fp);
    int i = str.indexOf('.');
    if (i != -1) {
      int length = str.length();
      b.append('.');
      b.append(str, i + 1, Math.min(length, i + 3));
      length -= i + 1;
      while (length < 2) {
        b.append('0');
        length++;
      }
    }
    return b.toString();
  }

  public static boolean isWhitespace (char c) {
    return c == ' ' || c == '\n';
  }

  public static boolean isTrue (String str) {
    return !StringUtils.isEmpty(str) && (StringUtils.equalsOrBothEmpty(str, "1") || StringUtils.equalsOrBothEmpty(str, "true"));
  }

  public static boolean isHex (char c) {
    return StringUtils.isNumeric(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  public static boolean isHex (String in) {
    if (StringUtils.isEmpty(in))
      return false;
    int len = in.length();
    for (int i = 0; i < len;) {
      int codePoint = in.codePointAt(i);
      int codePointSize = Character.charCount(codePoint);
      if (codePointSize != 1 || !isHex((char) codePoint))
        return false;
      i += codePointSize;
    }
    return true;
  }

  public static boolean isTrimmed (CharSequence full, CharSequence trimmed) {
    return full != null && trimmed != null && (full.length() > trimmed.length());
  }

  public static int indexOfNumber (String in) {
    if (in == null) {
      return -1;
    }
    int i = 0;
    int len = in.length();
    while (i < len) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (size == 1 && StringUtils.isNumeric((char) codePoint)) {
        return i;
      }
      i += size;
    }
    return -1;
  }

  public static String wrapRtlLtr (String rtl) {
    return Lang.rtl() ? wrapLtr(rtl) : wrapRtl(rtl);
  }

  public static String wrapRtl (String rtl) {
    if (rtl == null) {
      return null;
    }
    if (Fonts.isLtrCharSupported() || Fonts.isScopeEndSupported()) {
      StringBuilder b = new StringBuilder();
      if (Fonts.isLtrCharSupported()) {
        b.append(LTR_CHAR);
      }
      b.append(rtl);
      if (Fonts.isScopeEndSupported()) {
        b.append(SCOPE_END);
      }
      return b.toString();
    }
    return rtl;
  }

  public static String wrapLtr (String rtl) {
    if (rtl == null) {
      return null;
    }
    if (Fonts.isRtlCharSupported() || Fonts.isScopeEndSupported()) {
      StringBuilder b = new StringBuilder();
      if (Fonts.isRtlCharSupported()) {
        b.append(RTL_CHAR);
      }
      b.append(rtl);
      if (Fonts.isScopeEndSupported()) {
        b.append(SCOPE_END);
      }
      return b.toString();
    }
    return rtl;
  }

  /*public static String random (String... elems) {
    return elems[(int) (Math.random() * (double) (elems.length - 1))];
  }*/

  public interface Modifier<T> {
    T modify (T item);
  }

  public static <T> String join (CharSequence delimiter, T[] tokens, Modifier<T> itemModifier) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (T token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(itemModifier.modify(token));
    }
    return sb.toString();
  }

  public static <T> String join (CharSequence delimiter, Iterable<T> tokens, Modifier<T> itemModifier) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (T token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(itemModifier.modify(token));
    }
    return sb.toString();
  }

  public static String join (CharSequence delimiter, Iterable<?> tokens) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(token == null ? "null" : token.toString());
    }
    return sb.toString();
  }

  public static String join (CharSequence delimiter, Object... tokens) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(token == null ? "null" : token.toString());
    }
    return sb.toString();
  }

  public static String translateNewLinesToSpaces (String in) {
    return translateNewLinesToSpaces(in, 0);
  }

  public static String translateNewLinesToSpaces (String in, int skipCount) {
    if (StringUtils.isEmpty(in)) {
      return in;
    }

    final int length = in.length();

    int i = in.indexOf('\n');
    if (i == -1) {
      return in;
    }

    boolean first = false;

    StringBuilder b = new StringBuilder(in.length());
    b.append(in, 0, i);
    if (skipCount > 0) {
      skipCount--;
      b.append('\n');
      first = true;
    } else {
      b.append(' ');
    }

    int start = i + 1;

    do {
      i = in.indexOf('\n', start);
      if (i != -1) {
        b.append(in, start, i);
        if (skipCount > 0) {
          skipCount--;
          b.append('\n');
          first = true;
        } else if (!first || i > start) {
          first = false;
          b.append(' ');
        }
        start = i + 1;
      } else {
        b.append(in, start, length);
        start = length;
      }
    } while (start < length);

    return b.toString();
  }

  public static String vcardEscape (String text) {
    if (StringUtils.isEmpty(text))
      return text;
    StringBuilder b = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); ) {
      int codePoint = text.codePointAt(i);
      switch (codePoint) {
        case '\n':
          b.append("\\n");
          break;
        case ';': case ',': case '\\':
          b.append('\\');
          b.appendCodePoint(codePoint);
          break;
        default:
          b.appendCodePoint(codePoint);
          break;
      }
      i += Character.charCount(codePoint);
    }
    return b.toString();
  }

  public static Uri wrapHttps (String url) {
    if (StringUtils.isEmpty(url))
      return null;
    try {
      Uri uri = Uri.parse(url);
      String scheme = uri.getScheme();
      if (StringUtils.isEmpty(scheme)) {
        return Uri.parse("https://" + url);
      } else if (!scheme.toLowerCase().equals(scheme)) {
        return uri.buildUpon().scheme(scheme.toLowerCase()).build();
      } else {
        return uri;
      }
    } catch (Throwable t) {
      Log.e("Unable to parse uri: %s", t, url);
      return null;
    }
  }

  public static String unwrapWww (String url) {
    if (StringUtils.isEmpty(url)) {
      return url;
    }
    if (url.startsWith("www."))
      return url.substring("www.".length());
    return url;
  }

  public static String any (String... any) {
    for (String str : any) {
      if (str != null) {
        str = str.trim();
        if (!str.isEmpty()) {
          return str;
        }
      }
    }
    return "";
  }

  public static String replaceNumbers (String in) {
    if (StringUtils.isEmpty(in)) {
      return in;
    }
    int num = 0;
    StringBuilder b = new StringBuilder(in);
    int i = b.indexOf(" ");
    if (i == -1) {
      i = 0;
    }
    for (; i < b.length(); i++) {
      char c = b.charAt(i);
      if (StringUtils.isNumeric(c)) {
        if (++num == 10) {
          num = 1;
        }
        b.setCharAt(i, Character.forDigit(num, 10));
      }
    }
    return b.toString();
  }

  public static String limit (String str, int limit) {
    int len = str.length();
    if (len > limit) {
      int i = 0;
      do {
        int codePoint = str.codePointAt(i);
        i += Character.charCount(codePoint);
        limit--;
      } while (i < len && limit > 0);
      if (i < len) {
        StringBuilder b = new StringBuilder();
        b.append(str, 0, i);
        b.append('…');
        return b.toString();
      }
    }
    return str;
  }

  public static String replaceNewLines (String str) {
    final int length = str.length();
    StringBuilder b = new StringBuilder(length);

    int prevCodePointType = 0;
    for (int i = 0; i < length; ) {
      int codePoint = str.codePointAt(i);
      int size = Character.charCount(codePoint);

      if (size == 1) {
        if (codePoint == '\n') {
          if (prevCodePointType != Character.SPACE_SEPARATOR) {
            b.append(' ');
          }
          prevCodePointType = 0;
        } else {
          prevCodePointType = Character.getType(codePoint);
          b.append((char) codePoint);
        }
      } else {
        b.appendCodePoint(codePoint);
      }

      i += size;
    }
    return b.toString();
  }

  public static boolean isEmpty (@Nullable Letters letters) {
    return letters == null || StringUtils.isEmpty(letters.text);
  }

  @Deprecated
  public static boolean findWord (String text, String word) {
    return highlightWords(text, word, 0, null) != text;
  }

  @Deprecated
  public static CharSequence highlightWords (String text, String highlight, int startIndex, @Nullable char[] special) {
    return highlightWords(text, highlight, startIndex, special, 0);
  }

  @Deprecated
  public static void forceHighlightSpansThemeId (CharSequence text, @ThemeId int forceThemeId) {
    CustomTypefaceSpan[] args = null;
    if (text instanceof Spannable) {
      args = ((Spannable) text).getSpans(0, text.length(), CustomTypefaceSpan.class);
    }
    if (args != null) {
      for (CustomTypefaceSpan span : args) {
        if (span != null) {
          span.setForceThemeId(forceThemeId);
        }
      }
    }
  }

  @Deprecated
  private static CharSequence highlightWords (String text, String highlight, int startIndex, @Nullable char[] special, @ThemeId int forceThemeId) {
    if (StringUtils.isEmpty(text) || StringUtils.isEmpty(highlight)) {
      return text;
    }
    Spannable b = null;
    final int end = text.length();
    final String lowercaseText = text.toLowerCase();
    final String lowercaseHighlight = highlight.toLowerCase();
    ArrayList<String> splitted = null;

    int j = Text.indexOfSplitter(highlight, startIndex, special);
    int highlightIndex = 0;
    while (j != -1 || (splitted != null && highlightIndex < highlight.length())) {
      if (j == -1) {
        splitted.add(lowercaseHighlight.substring(highlightIndex));
        break;
      }
      if (splitted == null) {
        splitted = new ArrayList<>();
      }
      String token = lowercaseHighlight.substring(highlightIndex, j);
      if (!StringUtils.isEmpty(token)) {
        splitted.add(token);
      }
      highlightIndex = j + 1;
      j = Text.indexOfSplitter(highlight, highlightIndex);
    }
    if (splitted != null) {
      Collections.sort(splitted, (o1, o2) -> Integer.compare(o2.length(), o1.length()));
    }
    while (startIndex < end) {
      boolean found = lowercaseText.startsWith(lowercaseHighlight, startIndex);
      String foundToken = lowercaseHighlight;
      if (!found && splitted != null) {
        for (String token : splitted) {
          if (lowercaseText.startsWith(token, startIndex)) {
            foundToken = token;
            found = true;
            break;
          }
        }
      }
      if (found) {
        if (b == null) {
          b = Spannable.Factory.getInstance().newSpannable(text);
        }
        b.setSpan(new CustomTypefaceSpan(null, ColorId.textSearchQueryHighlight).setForceThemeId(forceThemeId), startIndex, startIndex + foundToken.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      int i = Text.indexOfSplitter(text, startIndex, special);
      startIndex = i != -1 ? i + 1 : end;
    }
    return b != null ? b : text;
  }

  private static final char[] WORDS_LOOKUP = { ' ', '\n' };
  public static boolean anyWordStartsWith (String source, String prefix, @Nullable int[] level) {
    if (source.startsWith(prefix)) {
      if (level != null) {
        level[0] = 0;
      }
      return true;
    }

    int i;
    int startIndex = 0;
    int l = 0;

    while ((i = indexOfAnyChar(source, startIndex, WORDS_LOOKUP)) != -1) {
      startIndex = i + 1;
      l++;
      if (source.startsWith(prefix, startIndex)) {
        if (level != null) {
          level[0] = l;
        }
        return true;
      }
    }
    return false;
  }

  public static int indexOfAnyChar (String lookup, int startIndex, char[] chars) {
    final int length = lookup.length();
    for (int i = startIndex; i < length; i++) {
      char c = lookup.charAt(i);
      for (char check : chars) {
        if (c == check) {
          return i;
        }
      }
    }
    return -1;
  }

  public static String getPhoneNumber (String input) {
    StringBuilder b = new StringBuilder(input.length());
    char c;
    for (int i = 0; i < input.length(); i++) {
      c = input.charAt(i);
      switch (c) {
        case '0': case '1':
        case '2': case '3':
        case '4': case '5':
        case '6': case '7':
        case '8': case '9':
        case '+': {
          b.append(c);
          break;
        }
      }
    }
    return b.toString();
  }

  public static boolean isHost (String in) {
    if (StringUtils.isEmpty(in)) {
      return false;
    }
    if (in.startsWith("http://") || in.startsWith("https://")) {
      return false;
    }
    /*if (in.endsWith("/")) {
      in = in.substring(0, in.length() - 1);
    }*/
    return in.indexOf('/') == -1;
  }

  public static boolean isValidLink (String in) {
    if (StringUtils.isEmpty(in)) {
      return false;
    }
    TdApi.TextEntity[] entities = Td.findEntities(in);
    return entities != null && entities.length == 1 && entities[0].offset == 0 && entities[0].length == in.length() && entities[0].type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR;
  }

  public static boolean isValidEmail (String in) {
    if (StringUtils.isEmpty(in)) {
      return false;
    }
    TdApi.TextEntity[] entities = Td.findEntities(in);
    if (entities != null && entities.length == 1 && entities[0].offset == 0 && entities[0].length == in.length() && entities[0].type.getConstructor() == TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR) {
      return true;
    }
    try {
      return android.util.Patterns.EMAIL_ADDRESS.matcher(in).matches();
    } catch (Throwable t) {
      Log.w("Cannot find email address", t);
    }
    return false;
  }

  public static String clean (String input) {
    StringBuilder b = new StringBuilder(input.length());
    char c;
    for (int i = 0; i < input.length(); i++) {
      c = input.charAt(i);
      if ((c >= 33 && c <= 47) || (c >= 58 && c <= 63) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126)) {
        continue;
      }
      b.append(c);
    }
    return b.toString();
  }

  public static int getNumberLength (String input) {
    if (StringUtils.isEmpty(input))
      return 0;
    int count = 0;
    for (int i = 0; i < input.length();) {
      int codePoint = input.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (size == 1 && codePoint >= '0' && codePoint <= '9') {
        count++;
      }
      i += size;
    }
    return count;
  }

  public static String getNumber (String input) {
    if (input == null) {
      return null;
    }
    if (input.isEmpty()) {
      return "";
    }
    // TODO optimize case when all characters are already filtered
    StringBuilder b = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c >= '0' && c <= '9') {
        b.append(c);
      }
    }
    return b.toString();
  }

  public static CharSequence replaceBoldTokens (final String input) {
    return replaceBoldTokens(input, 0);
  }

  public static CharSequence replaceBoldTokens (final String input, @ColorId int colorId) {
    String token = "**";
    int tokenLen = token.length();

    SpannableStringBuilder ssb = new SpannableStringBuilder(input);
    int count = 0;

    int start, end;
    do {
      start = ssb.toString().indexOf(token) + tokenLen;
      end = ssb.toString().indexOf(token, start);

      if (start > -1 && end > -1) {
        boolean fakeBold = Text.needFakeBold(ssb, start, end);
        CustomTypefaceSpan span = new CustomTypefaceSpan(fakeBold ? Fonts.getRobotoRegular() : Fonts.getRobotoMedium(), colorId).setFakeBold(fakeBold);
        ssb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Delete the tokens before and after the span
        ssb.delete(end, end + tokenLen);
        ssb.delete(start - tokenLen, start);
        count++;
      }
    } while (start > -1 && end > -1);

    return count > 0 ? ssb : input;
  }

  public static String buildCounter (long count) {
    if (Settings.instance().forceArabicNumbers()) {
      StringBuilder b = new StringBuilder(length(count));
      buildCounter(count, Lang.getThousandsSeparator(), b);
      return b.toString();
    }
    return Lang.formatNumber(count);
  }

  private static void buildCounter (long count, String delimiter, StringBuilder b) {
    b.ensureCapacity(b.length() + length(count));
    final boolean isNegative = count < 0;
    if (isNegative) {
      count = -count;
    }
    if (count < 1000) {
      b.append(isNegative ? -count : count);
      return;
    }
    int startOffset = b.length();
    while (count != 0) {
      long c = count % 1000;
      count = count / 1000;

      b.insert(startOffset, c);
      if (count != 0) {
        if (c == 0) {
          b.insert(startOffset, "00");
        } else {
          while ((c *= 10) < 1000) {
            b.insert(startOffset, '0');
          }
        }
        b.insert(startOffset, delimiter);
      }
    }
    if (isNegative) {
      b.insert(0, '-');
    }
  }

  public static String formatNumberPart (String code, String number) {
    if (code.length() == 0 || number.length() == 0) {
      return number;
    }
    String formatted = formatPhone(code + number, false);
    if (formatted == null) {
      return number;
    }
    int remaining = code.length();
    int startIndex = 0;
    while (remaining > 0 && startIndex < formatted.length()) {
      if (Character.isDigit(formatted.charAt(startIndex))) {
        remaining--;
      }
      startIndex++;
    }
    char c;
    while (startIndex < formatted.length() && ((c = formatted.charAt(startIndex)) == ')' || c == ' ')) {
      startIndex++;
    }
    if (startIndex < code.length() || startIndex == formatted.length()) {
      return number;
    }
    return formatted.substring(startIndex);
  }

  public static String formatPhone (final String rawNumber) {
    return formatPhone(rawNumber, true, true);
  }

  public static String formatPhone (final String rawNumber, boolean needEnd) {
    return formatPhone(rawNumber, true, needEnd);
  }

  public static String formatPhone (final String rawNumber, final boolean forcePlus, boolean needEnd) {
    if (StringUtils.isEmpty(rawNumber))
      return rawNumber;

    final String phoneNumber = getNumber(rawNumber);
    final int length = phoneNumber.length();

    if (length == 0) {
      return rawNumber;
    }

    String parsed = TGPhoneFormat.build(phoneNumber, needEnd);
    int dropped = 0;
    while (parsed == null && dropped < length) {
      parsed = TGPhoneFormat.build(phoneNumber.substring(0, length - (++dropped)), needEnd);
    }
    if (parsed != null) {
      return parsed;
    }

    String formatPhone = forcePlus ? '+' + phoneNumber : phoneNumber;
    return systemFormat(formatPhone);
  }

  public static char translateLatinToNumber (char c) {
    c = Character.toLowerCase(c);
    if (c >= 'a' && c <= 'c') {
      return '2';
    } else if (c >= 'd' && c <= 'f') {
      return '3';
    } else if (c >= 'g' && c <= 'i') {
      return '4';
    } else if (c >= 'j' && c <= 'l') {
      return '5';
    } else if (c >= 'm' && c <= 'o') {
      return '6';
    } else if (c >= 'p' && c <= 's') {
      return '7';
    } else if (c >= 't' && c <= 'v') {
      return '8';
    } else if (c >= 'w' && c <= 'z') {
      return '9';
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static String systemFormat (String formatPhone) {
    String result;
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        result = PhoneNumberUtils.formatNumber(formatPhone, "US");
      } else {
        //noinspection deprecation
        result = PhoneNumberUtils.formatNumber(formatPhone);
      }
    } catch (Throwable t) {
      Log.e("Couldn't format the phone number", t);
      result = null;
    }

    return StringUtils.isEmpty(result) || Strings.getNumber(result).length() < Strings.getNumber(formatPhone).length() ? formatPhone : result;
  }

  public static final int SCRIPT_COMMON = 0;
  public static final int SCRIPT_HEBREW = 1;
  public static final int SCRIPT_ARABIC = 2;
  public static final int SCRIPT_SYRIAC = 3;
  public static final int SCRIPT_THAANA = 4;

  /*public static int getScript (String s) {
    if (s == null) return SCRIPT_COMMON;

    for (int i = 0; i < s.length(); i++) {
      int j = getScript(s.charAt(i));
      if (j != SCRIPT_COMMON) {
        return j;
      }
    }

    return SCRIPT_COMMON;
  }*/

  @SuppressWarnings ("ConstantConditions")
  public static int getScript (char c) {
    if (c >= 0x591 && c <= 0x5C7) return SCRIPT_HEBREW;
    if (c >= 0x5D0 && c <= 0x5EA) return SCRIPT_HEBREW;
    if (c >= 0x5F0 && c <= 0x5F4) return SCRIPT_HEBREW;
    if (c >= 0xFB1D && c <= 0xFB36) return SCRIPT_HEBREW;
    if (c >= 0xFB38 && c <= 0xFB3C) return SCRIPT_HEBREW;
    if (c == 0xFB3E) return SCRIPT_HEBREW;
    if (c >= 0xFB40 && c <= 0xFB41) return SCRIPT_HEBREW;
    if (c >= 0xFB43 && c <= 0xFB44) return SCRIPT_HEBREW;
    if (c >= 0xFB46 && c <= 0xFB4F) return SCRIPT_HEBREW;
    if (c >= 0x600 && c <= 0x6FF) return SCRIPT_ARABIC;
    if (c >= 0x750 && c <= 0x77F) return SCRIPT_ARABIC;
    if (c >= 0xFB50 && c <= 0xFBB1) return SCRIPT_ARABIC;
    if (c >= 0xFBD3 && c <= 0xFD3D) return SCRIPT_ARABIC;
    if (c >= 0xFD50 && c <= 0xFD8F) return SCRIPT_ARABIC;
    if (c >= 0xFD92 && c <= 0xFDC7) return SCRIPT_ARABIC;
    if (c >= 0xFDF0 && c <= 0xFDFC) return SCRIPT_ARABIC;
    if (c >= 0xFE70 && c <= 0xFE74) return SCRIPT_ARABIC;
    if (c >= 0xFE76 && c <= 0xFEFC) return SCRIPT_ARABIC;
    if (c >= 0x700 && c <= 0x70D) return SCRIPT_SYRIAC;
    if (c >= 0x70F && c <= 0x74A) return SCRIPT_SYRIAC;
    if (c >= 0x74D && c <= 0x74F) return SCRIPT_SYRIAC;
    if (c >= 0x780 && c <= 0x7B1) return SCRIPT_THAANA;
    
    return SCRIPT_COMMON;
  }

  public static boolean isSeparator (char c) {
    switch (c) {
      case '.':
      case ' ':
      case '\n':
        return true;
    }
    return false;
  }

  public static String buildDuration (long durationSeconds) {
    StringBuilder builder = new StringBuilder(4);
    buildDuration(durationSeconds, TimeUnit.SECONDS, false, builder);
    return builder.toString();
  }

  public static StringBuilder buildDuration (long time, TimeUnit unit, boolean includeMillis, StringBuilder builder) {
    int duration = (int) unit.toSeconds(time);

    int hours = 0;
    int minutes = duration / 60;

    if (minutes > 60) {
      hours = minutes / 60;
      minutes = minutes % 60;
    }

    int seconds = duration % 60;

    if (hours > 0) {
      builder.append(hours);
      builder.append(':');
      if (minutes < 10) {
        builder.append('0');
      }
    }
    builder.append(minutes);
    builder.append(':');
    if (seconds < 10) {
      builder.append('0');
    }
    builder.append(seconds);

    if (includeMillis) {
      builder.append(Lang.getDecimalSeparator());
      builder.append((unit.toMillis(time) % 1000l) / 100l);
    }

    return builder;
  }

  public static String buildSize (long size) {
    return buildSize(size, true);
  }

  public static String buildSize (long size, boolean allowFloat) {
    return buildSize(size, allowFloat, Settings.instance().getNewSetting(Settings.SETTING_FLAG_USE_METRIC_FILE_SIZE_UNITS));
  }

  public static String buildSize (long size, boolean allowFloat, boolean useMetricUnits) {
    @StringRes int stringRes; double value;
    if (useMetricUnits) {
      if (size < ByteUnit.KB.toBytes(1))
        return Lang.plural(R.string.xBytes, (int) size);
      if (size < ByteUnit.MB.toBytes(1)) {
        stringRes = R.string.fileSize_KB;
        value = ByteUnit.BYTE.toKB(size);
      } else if (size < ByteUnit.GB.toBytes(1)) {
        stringRes = R.string.fileSize_MB;
        value = ByteUnit.BYTE.toMB(size);
      } else {
        stringRes = R.string.fileSize_GB;
        value = ByteUnit.BYTE.toGB(size);
      }
    } else {
      if (size < ByteUnit.KIB.toBytes(1))
        return Lang.plural(R.string.xBytes, (int) size);
      if (size < ByteUnit.MIB.toBytes(1)) {
        stringRes = R.string.fileSize_KiB;
        value = ByteUnit.BYTE.toKiB(size);
      } else if (size < ByteUnit.GIB.toBytes(1)) {
        stringRes = R.string.fileSize_MiB;
        value = ByteUnit.BYTE.toMiB(size);
      } else {
        stringRes = R.string.fileSize_GiB;
        value = ByteUnit.BYTE.toGiB(size);
      }
    }
    return Lang.getString(stringRes, (allowFloat ? Lang.formatNumber(value) : Strings.buildCounter((long) value)));
  }

  public static CharSequence setSpanColorId (CharSequence str, @ColorId int colorId) {
    if (str instanceof Spannable) {
      CustomTypefaceSpan[] spans = ((Spannable) str).getSpans(0, str.length(), CustomTypefaceSpan.class);
      if (spans != null && spans.length > 0) {
        for (CustomTypefaceSpan span : spans) {
          span.setColorId(colorId);
        }
      }
    }
    return str;
  }

  public static CharSequence buildMarkdown (TdlibDelegate context, CharSequence text, @Nullable CustomTypefaceSpan.OnClickListener onClickListener) {
    if (text == null)
      return null;
    TdApi.FormattedText formattedText = TD.toFormattedText(text, false);
    if (TD.parseMarkdownWithEntities(formattedText) && formattedText.entities.length > 0) {
      return TD.formatString(context, formattedText.text, formattedText.entities, null, onClickListener);
    } else {
      return text;
    }
  }

  public static CharSequence getTitleAndText (CharSequence title, CharSequence text) {
    SpannableStringBuilder b = new SpannableStringBuilder(title).append("\n\n").append(text);
    b.setSpan(Lang.boldCreator().onCreateSpan(b, 0, title.length(), 0, Text.needFakeBold(title)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return b;
  }

  public static SpannableStringBuilder buildHtml (String html) {
    final CharSequence sequence = Html.fromHtml(html);
    final SpannableStringBuilder b = new SpannableStringBuilder(sequence);
    final URLSpan[] urls = b.getSpans(0, sequence.length(), URLSpan.class);
    for (final URLSpan span : urls) {
      int start = b.getSpanStart(span);
      int end = b.getSpanEnd(span);
      int flags = b.getSpanFlags(span);
      ClickableSpan clickable = new ClickableSpan() {
        public void onClick (@NonNull View view) {
          Intents.openLink(span.getURL());
        }
      };
      b.setSpan(clickable, start, end, flags);
      b.removeSpan(span);
    }
    return b;
  }

  private static boolean isWeakRtl (int codePoint) {
    switch (codePoint) {
      case 0x5d1:
      case 0x5d8:
      case 0x5db:
      case 0x5dc:
      case 0x5de:
      case 0x5e1:
      case 0x5ea:
      case 0xfb31:
      case 0xfb38:
      case 0xfb3c:
      case 0xfb3e:
      case 0xfb41:
      case 0xfb4a:
      case 0xfe91:
      case 0xfb8c:
      case 0x5dd:
      case 0xfea1:
      case 0x623:
      case 0x628:
      case 0x62d:
      case 0x6a1:
      case 0xfeaa:
      case 0x642:
      case 0xfea7:
      case 0xfea8:
      case 0x6aa:
      case 0x6c3:
      case 0xfe95:
        return true;
    }
    return false;
  }

  public static boolean isEuropeanNumber (int codePoint) {
    return Character.getDirectionality(codePoint) == Character.DIRECTIONALITY_EUROPEAN_NUMBER;
  }

  public static boolean isArabicNumber (int codePoint) {
    return Character.getDirectionality(codePoint) == Character.DIRECTIONALITY_ARABIC_NUMBER;
  }

  public static int getCodePointDirection (int codePoint) {
    /*switch (codePoint) {
      case '.'
      return DIRECTION_NEUTRAL;
    }*/

    int directionality = Character.getDirectionality(codePoint);
    switch (directionality) {
      case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
      case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
      case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
        return DIRECTION_LTR;
      case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
      case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
      case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
      case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
        return isWeakRtl(codePoint) ? DIRECTION_NEUTRAL : DIRECTION_RTL;
    }
    return DIRECTION_NEUTRAL;
  }

  public static int getTextDirection (CharSequence in) {
    return in != null ? getTextDirection(in, 0, in.length()) : DIRECTION_NEUTRAL;
  }

  public static int getTextDirection (CharSequence in, int start, int end) {
    for (int i = start; i < end;) {
      int codePoint = Character.codePointAt(in, i);
      int direction = getCodePointDirection(codePoint);
      switch (direction) {
        case DIRECTION_LTR:
          return DIRECTION_LTR;
        case DIRECTION_RTL:
          return DIRECTION_RTL;
      }
      i += Character.charCount(codePoint);
    }

    return DIRECTION_NEUTRAL;
  }

  public static String generateHint (String password) {
    if (password.isEmpty()) {
      return password;
    }
    StringBuilder b = new StringBuilder(password.length());
    if (password.length() < 3) {
      final int length = password.length();
      if (length == 2) {
        b.append(password.charAt(0));
      } else {
        return "";
      }
    } else {
      b.append(password.charAt(0));
      final int length = password.length() - 2;
      for (int i = 0; i < length; i++) {
        b.append('•');
      }
      b.append(password.charAt(password.length() - 1));
    }
    return b.toString();
  }

  public static CharSequence concatIpLocation (CharSequence ip, CharSequence location) {
    return concat(" – ", ip, location);
  }

  public static CharSequence concat (CharSequence separator, CharSequence... params) {
    int totalLength = 0;
    int separatorLen = separator.length();
    boolean hasSpannable = false;
    for (CharSequence param : params) {
      if (totalLength > 0) {
        totalLength += separatorLen;
        if (separator instanceof Spanned) {
          hasSpannable = true;
        }
      }
      totalLength += param != null ? param.length() : 0;
      if (param instanceof Spanned) {
        hasSpannable = true;
      }
    }
    CharSequence b = hasSpannable ? new SpannableStringBuilder() : new StringBuilder(totalLength);
    boolean first = true;
    for (CharSequence param : params) {
      if (!StringUtils.isEmpty(param)) {
         if (first) {
           first = false;
         } else {
           if (hasSpannable) {
             ((SpannableStringBuilder) b).append(separator);
           } else {
             ((StringBuilder) b).append(separator);
           }
         }
        if (hasSpannable) {
          ((SpannableStringBuilder) b).append(param);
        } else {
          ((StringBuilder) b).append(param);
        }
      }
    }
    return b;
  }

  public static void buildHexColor (int color, boolean allowShort, StringBuilder b) {
    b.append('#');
    int alphaValue = Color.alpha(color);
    String alpha = alphaValue < 255 ? U.hexWithZero(alphaValue) : null;
    String red = U.hexWithZero(Color.red(color));
    String green = U.hexWithZero(Color.green(color));
    String blue = U.hexWithZero(Color.blue(color));

    if (allowShort) {
      if (red.charAt(0) == red.charAt(1) && green.charAt(0) == green.charAt(1) && blue.charAt(0) == blue.charAt(1) && (alpha == null || (alpha.charAt(0) == alpha.charAt(1)))) {
        b.append(red.charAt(0));
        b.append(green.charAt(0));
        b.append(blue.charAt(0));
        if (alpha != null)
          b.append(alpha.charAt(0));
        return;
      }
    }

    b.append(red);
    b.append(green);
    b.append(blue);
    if (alpha != null) {
      b.append(alpha);
    }
  }

  public static String getHexColor (int color, boolean allowShort) {
    StringBuilder b = new StringBuilder();
    buildHexColor(color, allowShort, b);
    return b.toString();
  }

  public static String wrap (String str, boolean forceWrap) {
    str = str == null ? "" : str.replace("\n", "\\n").replace("'", "\\'").replace("\"", "\\\"");
    if (forceWrap || str.startsWith(" ") || str.endsWith(" "))
      str = "\"" + str + "\"";
    return str;
  }

  public static String unwrap (String key, String value) throws IllegalArgumentException {
    if (StringUtils.isEmpty(value)) {
      return value;
    }
    StringBuilder b = new StringBuilder(value);
    if (b.length() >= 2) {
      int length = b.length();
      if (b.charAt(0) == '"' && b.charAt(length - 1) == '"') {
        b.delete(length - 1, length);
        b.delete(0, 1);
      }
    }
    int startIndex = 0;
    do {
      int i = b.indexOf("\\", startIndex);
      if (i == -1)
        break;
      if (i == b.length() - 1)
        throw new IllegalArgumentException("Illegal character escape at the end of the string, key: " + key + ", value: " + value);
      char c = b.charAt(i + 1);
      String replacement;
      switch (c) {
        case '\'': replacement = "'"; break;
        case '"': replacement = "\""; break;

        case 'n': replacement = "\n"; break;
        case '\\': replacement = "\\"; break;

        /*case 'b': replacement = "\b"; break;
        case 't': replacement = "\t"; break;
        case 'r': replacement = "\r"; break;
        case 'f': replacement = "\f"; break;*/

        default:
          throw new IllegalArgumentException("Illegal character escape: \\" + c + ", key: " + key + ", value: " + value);
      }
      b.replace(i, i + 2, replacement);
      startIndex = i + replacement.length();
    } while (true);

    return b.toString();
  }

  private static StringBuilder appendRgb (StringBuilder b, int color) {
    /*if (color < 10)
      b.append("  ");
    else if (color < 100)
      b.append(" ");*/
    b.append(color);
    return b;
  }

  private static void appendHsl (StringBuilder b, float value) {
    b.append(U.formatFloat(value, false));
  }

  public static String getColorRepresentation (int format, float hue, float saturation, float value, int color, boolean needFull) {
    int alpha = Color.alpha(color);
    StringBuilder b = new StringBuilder();
    switch (format) {
      case Settings.COLOR_FORMAT_HEX:
        buildHexColor(color, true, b);
        break;
      case Settings.COLOR_FORMAT_RGB:
        b.append(alpha != 255 ? "rgba(" : "rgb(");
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        appendRgb(b, red).append(", ");
        appendRgb(b, green).append(", ");
        appendRgb(b, blue);
        if (alpha != 255)
          b.append(", ").append(U.formatFloat((float) alpha / 255f, needFull));
        b.append(")");
        break;
      case Settings.COLOR_FORMAT_HSL:
        b.append(alpha != 255 ? "hsla(" : "hsl(");
        appendHsl(b, hue);
        b.append(", ");
        appendHsl(b, saturation * 100f);
        b.append("%, ");
        appendHsl(b, value * 100f);
        b.append(alpha != 255 ? "%, " : "%)");
        if (alpha != 255)
          b.append(U.formatFloat((float) alpha / 255f, needFull)).append(")");
        break;
    }
    return b.toString();
  }

  public static int length (long i) {
    if (i < 0) {
      i = -i;
      if (i < 100000) {
        if (i < 100) {
          if (i < 10)
            return 2;
          else
            return 3;
        } else {
          if (i < 1000)
            return 4;
          else {
            if (i < 10000)
              return 5;
            else
              return 6;
          }
        }
      } else {
        if (i < 10000000) {
          if (i < 1000000)
            return 7;
          else
            return 8;
        } else {
          if (i < 100000000)
            return 9;
          else {
            if (i < 1000000000)
              return 10;
            else
              return 11;
          }
        }
      }
    }

    if (i < 100000) {
      if (i < 100) {
        if (i < 10)
          return 1;
        else
          return 2;
      } else {
        if (i < 1000)
          return 3;
        else {
          if (i < 10000)
            return 4;
          else
            return 5;
        }
      }
    } else {
      if (i < 10000000) {
        if (i < 1000000)
          return 6;
        else
          return 7;
      } else {
        if (i < 100000000)
          return 8;
        else {
          if (i < 1000000000)
            return 9;
          else
            return 10;
        }
      }
    }
  }
}
