/**
 * File created on 23/05/15 at 12:20
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.core;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.widget.RelativeLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationGroup;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;

@SuppressWarnings(value = "SpellCheckingInspection")
public class Lang {
  private static final int LANGUAGE_CODE_DEFAULT = 0x656e; // en

  @Deprecated
  public static final boolean isRtl = false;

  private static int builtInLanguageCode;
  private static String builtInLanguageCodeRaw;

  private static int fallbackLanguageCode () {
    String code = getStringImpl(null, R.string.language_code, false);
    if (builtInLanguageCode == 0 || !code.equals(builtInLanguageCodeRaw)) {
      builtInLanguageCode = makeLanguageCode(code);
      builtInLanguageCodeRaw = code;
    }
    return builtInLanguageCode;
  }

  private Lang () { }

  public static final String INTERNAL_ID_KEY = "language_code";

  public static String getResourceEntryName (int resource) {
    try {
      return UI.getAppContext().getResources().getResourceEntryName(resource);
    } catch (Throwable t) {
      Log.e("Unable to find resource entry name (shitty modified APK?)");
      return "";
    }
  }

  // String

  public interface SpanCreator {
    @Nullable
    Object onCreateSpan (CharSequence target, int argStart, int argEnd, int argIndex, boolean needFakeBold);
  }

  public static @StringRes int getStringResourceIdentifier (String key) {
    try {
      Context context = UI.getAppContext();
      return context.getResources().getIdentifier(key, "string", context.getPackageName());
    } catch (Throwable ignored) {
      return 0;
    }
  }

  public static @Nullable String getStringByKey (String key) {
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c =='_')
        continue;
      return null;
    }
    int resId = getStringResourceIdentifier(key);
    if (resId == 0 || ArrayUtils.indexOf(LangUtils.getBlacklistedKeys(), key) >= 0)
      return null;
    return Lang.getString(resId);
  }

  public static String[] getKeys (@StringRes int[] stringResources) {
    String[] keys = new String[stringResources.length];
    int i = 0;
    for (int stringRes : stringResources) {
      keys[i] = getResourceEntryName(stringRes);
      i++;
    }
    return keys;
  }

  public static CharSequence getMarkdownString (TdlibDelegate context, @StringRes int resId, SpanCreator spanCreator, Object... formatArgs) {
    return Strings.buildMarkdown(context, Lang.getString(resId, spanCreator, formatArgs), null);
  }

  public static CharSequence getMarkdownString (TdlibDelegate context, @StringRes int resId, Object... formatArgs) {
    return Strings.buildMarkdown(context, Lang.getString(resId, formatArgs), null);
  }

  public static CharSequence getMarkdownStringSecure (TdlibDelegate context, @StringRes int resId, Object... formatArgs) {
    return Strings.buildMarkdown(context, Lang.getStringSecure(resId, formatArgs), null);
  }

  public static String getString (@StringRes int resId) {
    return getStringImpl(null, resId, true);
  }

  public static String getString (@Nullable TdApi.LanguagePackInfo languagePackInfo, @StringRes int resId) {
    return getStringImpl(languagePackInfo, resId, true);
  }

  @Nullable
  private  static String getCloudString (String key) {
    TdApi.LanguagePackInfo languagePackInfo = Settings.instance().getLanguagePackInfo();
    TdApi.LanguagePackStringValueOrdinary string = getStringValue(key, languagePackInfo);
    return string != null ? string.value : null;
  }

  public static CharSequence getTutorial (ViewController<?> context, @NonNull TdApi.ChatSource source) {
    switch (source.getConstructor()) {
      case TdApi.ChatSourcePublicServiceAnnouncement.CONSTRUCTOR: {
        TdApi.ChatSourcePublicServiceAnnouncement psa = (TdApi.ChatSourcePublicServiceAnnouncement) source;
        return getPsaInfo(context, psa.type, true);
      }
      case TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR:
        return getMarkdownString(context, R.string.ProxySponsorAlert);
    }
    throw new UnsupportedOperationException(source.toString());
  }

  public static String getPsaType (TdApi.ChatSourcePublicServiceAnnouncement source) {
    return getSuffixString(R.string.PsaType, source.type).toString();
  }

  public static CharSequence getPsaHideConfirm (@NonNull TdApi.ChatSourcePublicServiceAnnouncement source, String chatTitle) {
    return getSuffixString(R.string.PsaHideConfirm, source.type, chatTitle);
  }

  public static CharSequence getPsaNotificationType (TdlibDelegate context, String type) {
    return Strings.buildMarkdown(context, getSuffixString(R.string.PsaForward, type).toString(), null);
  }

  public static CharSequence getPsaInfo (TdlibDelegate context, String type, boolean isChat) {
    return Strings.buildMarkdown(context, getSuffixString(isChat ? R.string.PsaChatInfo : R.string.PsaInfo, type).toString(), null);
  }

  public static String getErrorString (String errorMessage) {
    String key = "error_" + errorMessage;
    int resId = getStringResourceIdentifier(key);
    if (resId != 0)
      return getString(resId);
    String value = getCloudString(key);
    if (value != null) {
      return value;
    }
    return null;
  }

  private static CharSequence getSuffixString (int mainRes, String suffix, Object... formatArgs) {
    if (!StringUtils.isEmpty(suffix) && suffix.matches("^[A-Za-z0-9_]+$")) {
      String key = getResourceEntryName(mainRes) + StringUtils.ucfirst(suffix.toLowerCase(), dateFormatLocale());
      int resId = getStringResourceIdentifier(key);
      if (resId != 0)
        return getStringBold(resId, formatArgs);
      String value = getCloudString(key);
      if (value != null) {
        if (formatArgs != null && formatArgs.length > 0) {
          try {
            return formatString(value, hasSpanned(formatArgs), boldCreator(), formatArgs);
          } catch (Throwable t) {
            warnResource(true, pluralCode(), mainRes);
          }
        } else {
          return value;
        }
      }
    }
    if (mainRes != 0)
      return getStringBold(mainRes, formatArgs);
    return null;
  }

  private static String getStringImpl (@Nullable TdApi.LanguagePackInfo languagePackInfo, @StringRes int resId, boolean allowCloud) {
    if (resId == 0)
      throw new Resources.NotFoundException("resId == 0");
    if (allowCloud) {
      if (languagePackInfo == null)
        languagePackInfo = Settings.instance().getLanguagePackInfo();
      final String key = getResourceEntryName(resId);
      TdApi.LanguagePackStringValueOrdinary string = getStringValue(key, languagePackInfo);
      if (string != null)
        return string.value;
    }
    try {
      return getAndroidString(resId);
    } catch (Resources.NotFoundException e) {
      Log.e("Resource not found (shitty modified lang pack?): %d %s", resId, getResourceEntryName(resId));
      return "";
    }
  }

  private static String getAndroidString (@StringRes int resId) throws Resources.NotFoundException {
    // TODO non-current languagePackInfo
    return UI.getAppContext().getResources().getString(resId);
  }

  private static String getAndroidString (@StringRes int resId, Object... formatArgs) {
    // TODO non-current languagePackInfo
    return UI.getAppContext().getResources().getString(resId, formatArgs);
  }

  private static final int FLAG_LOWERCASE = 1;

  private static String applyFlags (String string, int flags) {
    if (flags != 0) {
      if ((flags & FLAG_LOWERCASE) != 0) {
        string = lowercase(string);
      }
    }
    return string;
  }

  private static boolean isTrustedLangauge () {
    return !Lang.packId().startsWith("X");
  }

  public static String getStringSecure (@StringRes int resource, Object... formatArgs) {
    if (isTrustedLangauge()) {
      return getString(resource, formatArgs);
    } else {
      return getStringImpl(null, resource, false, 0, null, formatArgs).toString();
    }
  }

  public static CharSequence getStringSecure (@StringRes int resource, SpanCreator creator, Object... formatArgs) {
    if (isTrustedLangauge()) {
      return getString(resource, creator, formatArgs);
    } else {
      return getStringImpl(null, resource, false, 0, creator, formatArgs).toString();
    }
  }

  public static String getString (@Nullable TdApi.LanguagePackInfo languagePackInfo, @StringRes int resource, Object... formatArgs) {
    return getStringImpl(languagePackInfo, resource, true, 0, null, formatArgs).toString();
  }

  public static CharSequence getCharSequence (@StringRes int resource, Object... formatArgs) {
    return getStringImpl(null, resource, true, 0, null, formatArgs);
  }

  public static String getString (@StringRes int resource, Object... formatArgs) {
    return getCharSequence(resource, formatArgs).toString();
  }

  public static TextEntity[] toEntities (CharSequence text) {
    if (text instanceof Spanned) {
      TextEntity[] entities = ((Spanned) text).getSpans(0, text.length(), TextEntity.class);
      return entities != null && entities.length > 0 ? entities : null;
    }
    return null;
  }

  public static CharSequence boldify (CharSequence text) {
    return wrap(text, boldCreator());
  }

  public static CharSequence codify (CharSequence text) {
    return wrap(text, codeCreator());
  }

  public static CharSequence wrap (CharSequence text, SpanCreator spanCreator) {
    return spanCreator != null ? formatString("%s", spanCreator, text) : text;
    /*Object span = spanCreator.onCreateSpan(text, 0, text.length(), 0, Text.needFakeBold(text));
    if (span != null) {
      SpannableStringBuilder str = new SpannableStringBuilder(text);
      str.setSpan(span, 0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return str;
    }
    return text;*/
  }

  public static CharSequence getStringBold (@StringRes int resource, Object... formatArgs) {
    return getStringImpl(null, resource, true, 0, boldCreator(), formatArgs);
  }

  public static CharSequence getStringBoldLowercase (@StringRes int resource, Object... formatArgs) {
    return getStringImpl(null, resource, true, FLAG_LOWERCASE, boldCreator(), formatArgs);
  }

  private static boolean hasSpanned (Object[] args) {
    if (args != null) {
      for (Object arg : args) {
        if (arg instanceof Spanned) {
          return true;
        }
      }
    }
    return false;
  }

  public static CharSequence getStringImpl (@Nullable TdApi.LanguagePackInfo languagePackInfo, @StringRes int resId, boolean allowCloud, int flags, SpanCreator creator, Object... formatArgs) {
    if (resId == 0)
      throw new Resources.NotFoundException("resId == 0");
    if (formatArgs == null || formatArgs.length == 0)
      return getStringImpl(null, resId, allowCloud);
    final boolean hasSpanned = hasSpanned(formatArgs);
    if (allowCloud) {
      if (languagePackInfo == null)
        languagePackInfo = Settings.instance().getLanguagePackInfo();
      final String key = getResourceEntryName(resId);
      TdApi.LanguagePackStringValueOrdinary string = getStringValue(key, languagePackInfo);
      if (string != null) {
        try {
          return formatString(applyFlags(string.value, flags), hasSpanned, creator, formatArgs);
        } catch (Throwable t) {
          warnResource(true, pluralCode(), resId);
        }
      }
    }
    try {
      if (creator != null || flags != 0 || hasSpanned) {
        String format = applyFlags(getAndroidString(resId), flags);
        return formatString(format, hasSpanned, creator, formatArgs);
      } else {
        return getAndroidString(resId, formatArgs);
      }
    } catch (Resources.NotFoundException e) {
      Log.e("Resource not found (shitty modified lang pack?): %d %s", resId, getResourceEntryName(resId));
      return "";
    } catch (Throwable t) {
      String str = getAndroidString(resId);
      Log.e("Resource format is broken (shitty modified lang pack?): %s, format: %s", t, getResourceEntryName(resId), str);
      return str;
    }
  }

  public static Object newBoldSpan (boolean needFakeBold) {
    return TD.toDisplaySpan(new TdApi.TextEntityTypeBold(), null, needFakeBold);
  }

  public static SpanCreator boldCreator () {
    return (target, argStart, argEnd, argIndex, needFakeBold) -> newBoldSpan(needFakeBold);
  }

  public static Object newCodeSpan (boolean needFakeBold) {
    return TD.toDisplaySpan(new TdApi.TextEntityTypeCode(), null, needFakeBold);
  }

  public static Object newItalicSpan (boolean needFakeBold) {
    return TD.toDisplaySpan(new TdApi.TextEntityTypeItalic(), null, needFakeBold);
  }

  public static SpanCreator codeCreator () {
    return (target, argStart, argEnd, argIndex, needFakeBold) -> newCodeSpan(needFakeBold);
  }

  public static SpanCreator italicCreator () {
    return (target, argStart, argEnd, argIndex, needFakeBold) -> newItalicSpan(needFakeBold);
  }

  public static SpanCreator entityCreator (TdApi.TextEntityType entity) {
    return (target, argStart, argEnd, argIndex, needFakeBold) -> TD.toSpan(entity);
  }

  public static CustomTypefaceSpan newSenderSpan (TdlibDelegate context, TdApi.MessageSender senderId) {
    switch (senderId.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        return newUserSpan(context, ((TdApi.MessageSenderUser) senderId).userId);
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        return null; // TODO
    }
    throw new UnsupportedOperationException(senderId.toString());
  }

  public static CustomTypefaceSpan newUserSpan (TdlibDelegate context, long userId) {
    return TD.toDisplaySpan(new TdApi.TextEntityTypeMentionName(userId)).setOnClickListener((view, span) -> {
      context.tdlib().ui().openPrivateProfile(context, userId, null);
      return true;
    });
  }

  public static CharSequence getString (@StringRes int resId, SpanCreator creator, Object... formatArgs) {
    return getStringImpl(null, resId, true, 0, creator, formatArgs);
  }

  private static void assertFormatArgumentType (char c, Object arg) {
    boolean ok;
    switch (c) {
      case 's':
        ok = true;
        break;
      case 'd':
        ok = arg instanceof Integer || arg instanceof Long;
        break;
      case 'f':
        ok = arg instanceof Float || arg instanceof Double;
        break;
      default:
        ok = false;
        break;
    }
    if (!ok) {
      throw new IllegalArgumentException(arg.getClass() + " != %" + c);
    }
  }

  private static final boolean ALLOW_ICU = true;
  private static Object decimalFormat;
  private static Locale decimalFormatLocale;

  public static String formatDecimal (double n) {
    Locale locale = Settings.instance().forceArabicNumbers() ? Locale.US : Lang.dateFormatLocale();
    synchronized (Lang.class) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
        android.icu.text.DecimalFormat format = (android.icu.text.DecimalFormat) decimalFormat;
        if (format == null || decimalFormatLocale != locale) {
          android.icu.text.DecimalFormatSymbols symbols = new android.icu.text.DecimalFormatSymbols(decimalFormatLocale = locale);
          if (Lang.isSpanish()) {
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator(' ');
          }
          if (format != null) {
            format.setDecimalFormatSymbols(symbols);
          } else {
            decimalFormat = format = new android.icu.text.DecimalFormat("###,###.00", symbols);
          }
        }
        return format.format(n);
      } else {
        java.text.DecimalFormat format = (java.text.DecimalFormat) decimalFormat;
        if (format == null || decimalFormatLocale != locale) {
          java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(decimalFormatLocale = locale);
          if (Lang.isSpanish()) {
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator(' ');
          }
          if (format != null) {
            format.setDecimalFormatSymbols(symbols);
          } else {
            decimalFormat = format = new java.text.DecimalFormat("###,###.00", symbols);
          }
        }
        return format.format(n);
      }
    }
  }

  private static String fixNumber (String str) {
    if (Lang.isSpanish()) {
      str = str.replace('.', ' ');
      int beforeNum = 0;
      int separatorCount = 0;
      int len = str.length();
      int separatorIndex = -1;
      for (int i = 0; i < len; i++) {
        char c = str.charAt(i);
        if (StringUtils.isNumeric(c)) {
          beforeNum++;
        } else if (c != ' ' || ++separatorCount > 1) {
          break;
        } else {
          separatorIndex = i;
        }
      }
      if (beforeNum == 4 && separatorCount == 1 && separatorIndex != -1) {
        str = str.substring(0, separatorIndex) + str.substring(separatorIndex + 1);
      }
    }
    return str;
  }

  public static String formatNumber (double n) {
    if (n % 1.0 == 0)
      return formatNumber((long) n);
    else
      return formatDecimal(n);
  }

  public static String formatNumber (long n) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        return fixNumber(android.icu.text.NumberFormat.getInstance(Lang.dateFormatLocale()).format(n));
      } catch (Throwable ignored) { }
    }
    return fixNumber(java.text.NumberFormat.getInstance(Lang.dateFormatLocale()).format(n));
  }

  public static String compactNumber (long n) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU && n >= 0) {
      try {
        return android.icu.text.CompactDecimalFormat.getInstance(dateFormatLocale(), android.icu.text.CompactDecimalFormat.CompactStyle.SHORT).format(n);
      } catch (Throwable ignored) { }
    }
    return null;
  }

  private static String dateFormat (String pattern, long timeInMillis) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        return new android.icu.text.SimpleDateFormat(pattern, dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
      } catch (ExceptionInInitializerError e) {
        Log.e("Vendor i18n implementation bug in SimpleDateFormat", e);
      }
    }
    return new java.text.SimpleDateFormat(pattern, dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
  }

  private static boolean isSpecifier (char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isYearSpecifier (char c) {
    return c == 'y' || c == 'Y';
  }

  public static String patternWithoutYear (String pattern) {
    StringBuilder b = new StringBuilder(pattern);
    boolean inQuote = false;
    for (int i = pattern.length() - 1; i >= 0; i--) {
      char c = pattern.charAt(i);
      if (c == '\'') {
        inQuote = !inQuote;
      } else if (!inQuote && isSpecifier(c) && isYearSpecifier(c)) {
        int endIndex = i + 1;
        int startIndex = i;
        boolean inQuoteInner = false;
        while (endIndex < b.length()) { // removing relevant stuff on the right
          c = b.charAt(endIndex);
          if (c == '\'') {
            inQuoteInner = !inQuoteInner;
          } else if (!inQuoteInner) {
            if (isSpecifier(c) && !isYearSpecifier(c))
              break;
          }
          endIndex++;
        }
        inQuoteInner = false;
        while (i > 0) { // removing relevant stuff on the left
          c = pattern.charAt(i - 1);
          if (c == '\'') {
            inQuoteInner = !inQuoteInner;
          } else if (!inQuoteInner && isSpecifier(c) && !isYearSpecifier(c)) {
            break;
          }
          i--;
          startIndex--;
        }
        b.delete(startIndex, endIndex);
      }
    }
    return b.toString();
  }

  private static boolean isDaySpecifier (char c) {
    return c == 'd' || c == 'D';
  }

  public static String patternWithoutDay (String pattern) {
    StringBuilder b = new StringBuilder(pattern);
    boolean inQuote = false;
    for (int i = pattern.length() - 1; i >= 0; i--) {
      char c = pattern.charAt(i);
      if (c == '\'') {
        inQuote = !inQuote;
      } else if (!inQuote && isSpecifier(c) && isDaySpecifier(c)) {
        int endIndex = i + 1;
        int startIndex = i;
        boolean inQuoteInner = false;
        while (endIndex < b.length()) { // removing relevant stuff on the right
          c = b.charAt(endIndex);
          if (c == '\'') {
            inQuoteInner = !inQuoteInner;
          } else if (!inQuoteInner) {
            if (isSpecifier(c) && !isDaySpecifier(c))
              break;
          }
          endIndex++;
        }
        inQuoteInner = false;
        while (i > 0) { // removing relevant stuff on the left
          c = pattern.charAt(i - 1);
          if (c == '\'') {
            inQuoteInner = !inQuoteInner;
          } else if (!inQuoteInner /*&& c != ' '*/ && c != '.' && c != '`') {
            break;
          }
          i--;
          startIndex--;
        }
        b.delete(startIndex, endIndex);
      } else if (!inQuote && c == 'M') {
        b.setCharAt(i, 'L');
      }
    }
    return b.toString();
  }

  private static final int STYLE_LONG = 1;
  private static final int STYLE_MEDIUM = 2;
  private static final int STYLE_SHORT = 3;

  private static int translateStyle (int style, boolean allowIcu) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU && allowIcu) {
      switch (style) {
        case STYLE_LONG:
          return android.icu.text.DateFormat.LONG;
        case STYLE_MEDIUM:
          return android.icu.text.DateFormat.MEDIUM;
        case STYLE_SHORT:
          return android.icu.text.DateFormat.SHORT;
      }
    } else {
      switch (style) {
        case STYLE_LONG:
          return java.text.DateFormat.LONG;
        case STYLE_MEDIUM:
          return java.text.DateFormat.MEDIUM;
        case STYLE_SHORT:
          return java.text.DateFormat.SHORT;
      }
    }
    throw new IllegalArgumentException("style == " + style);
  }

  private static String systemDate (long timeInMillis, int style, String fallbackPattern) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        return android.icu.text.DateFormat.getDateInstance(translateStyle(style, true), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
      } catch (Throwable ignored) { }
    }
    try {
      return java.text.DateFormat.getDateInstance(translateStyle(style, false), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
    } catch (Throwable ignored) {
      return dateFormat(fallbackPattern, timeInMillis);
    }
  }

  private static String systemTime (long timeInMillis, int style, String fallbackPattern) {
    try {
      String language = dateFormatLocale().getLanguage();
      if (language.equals(Locale.getDefault().getLanguage())) {
        Formatter f = new Formatter(new StringBuilder(50), dateFormatLocale());
        return android.text.format.DateUtils.formatDateRange(UI.getAppContext(), f, timeInMillis, timeInMillis, android.text.format.DateUtils.FORMAT_SHOW_TIME).toString();
      }
      if (language.equals("en")) {
        return dateFormat(fallbackPattern, timeInMillis);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
        try {
          return android.icu.text.DateFormat.getTimeInstance(translateStyle(style, true), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
        } catch (Throwable ignored) { }
      }
      return java.text.DateFormat.getTimeInstance(translateStyle(style, false), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
    } catch (Throwable ignored) {
      return dateFormat(fallbackPattern, timeInMillis);
    }
  }

  private static String systemDateTime (long timeInMillis, int dateStyle, int timeStyle, String fallbackPattern) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        return android.icu.text.DateFormat.getDateTimeInstance(translateStyle(dateStyle, true), translateStyle(timeStyle, true), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
      } catch (Throwable ignored) { }
    }
    try {
      return java.text.DateFormat.getDateTimeInstance(translateStyle(dateStyle, false), translateStyle(timeStyle, false), dateFormatLocale()).format(DateUtils.dateInstance(timeInMillis));
    } catch (Throwable ignored) {
      return dateFormat(fallbackPattern, timeInMillis);
    }
  }

  private static String systemDateWithoutYear (long timeInMillis, int style, String fallbackPattern) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        String pattern = ((android.icu.text.SimpleDateFormat) android.icu.text.DateFormat.getDateInstance(translateStyle(style, true), dateFormatLocale())).toPattern();
        return dateFormat(patternWithoutYear(pattern), timeInMillis);
      } catch (Throwable ignored) { }
    }
    try {
      String pattern = ((java.text.SimpleDateFormat) java.text.DateFormat.getDateInstance(translateStyle(style, false), dateFormatLocale())).toPattern();
      return dateFormat(patternWithoutYear(pattern), timeInMillis);
    } catch (Throwable ignored) {
      return dateFormat(fallbackPattern, timeInMillis);
    }
  }

  private static String systemDateWithoutDay (long timeInMillis, int style, String fallbackPattern) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALLOW_ICU) {
      try {
        String pattern = ((android.icu.text.SimpleDateFormat) android.icu.text.DateFormat.getDateInstance(translateStyle(style, true), dateFormatLocale())).toPattern();
        return dateFormat(patternWithoutDay(pattern), timeInMillis);
      } catch (Throwable ignored) { }
    }
    try {
      String pattern = ((java.text.SimpleDateFormat) java.text.DateFormat.getDateInstance(translateStyle(style, false), dateFormatLocale())).toPattern();
      return dateFormat(patternWithoutDay(pattern), timeInMillis);
    } catch (Throwable ignored) {
      return dateFormat(fallbackPattern, timeInMillis);
    }
  }

  public static CharSequence formatString (String format, @Nullable SpanCreator creator, Object... formatArgs) {
    return formatString(format, hasSpanned(formatArgs), creator, formatArgs);
  }

  public static CharSequence formatString (String format, boolean hasSpanned, @Nullable SpanCreator creator, Object... formatArgs) {
    if (creator == null && !hasSpanned) {
      return String.format(locale(), format, formatArgs);
    }
    SpannableStringBuilder str = new SpannableStringBuilder(format);
    int spanCount = 0;
    int i = 0;
    int argumentIndex = 0; // used for arguments without index
    while ((i = StringUtils.indexOf(str, "%", i)) != -1) {
      int len = str.length();
      int formatStart = i;
      if (++i == len)
        throw new IllegalArgumentException("Invalid specifier format: %");
      char c = str.charAt(i);
      Object argument; int index; String replacement;
      switch (c) {
        case '%': { // %%
          i = formatStart + 1;
          str.delete(formatStart, i); // removing first %
          continue;
        }
        case 'f': case 'd': case 's': { // %f, %d, %s
          index = argumentIndex++;
          argument = formatArgs[index];
          assertFormatArgumentType(c, argument);
          replacement = String.valueOf(argument);
          str.replace(formatStart, formatStart + 2, replacement);
          i = formatStart + replacement.length();
          break;
        }
        default: { // %1$s, %1$d, %1$s, %2$02d
          int numSize = 0;
          while (i < len && StringUtils.isNumeric(c = str.charAt(i++)))
            numSize++;
          if (numSize == 0)
            throw new IllegalArgumentException();
          if (c != '$')
            throw new IllegalArgumentException();
          if (i == len)
            throw new IllegalArgumentException();
          c = str.charAt(i++);
          index = Integer.parseInt(str.subSequence(formatStart + 1, formatStart + 1 + numSize).toString()) - 1;
          argument = formatArgs[index];
          replacement = String.valueOf(argument);

          int specSize;
          switch (c) {
            case 'f':
            case 'd':
            case 's':
              assertFormatArgumentType(c, argument);
              specSize = 1;
              break;
            case '0':
              int widthSize = 0;
              int numStart = i;
              while (i < len && StringUtils.isNumeric(c = str.charAt(i++)))
                widthSize++;
              if (widthSize == 0)
                throw new IllegalArgumentException();
              assertFormatArgumentType(c, argument);
              int width = Integer.parseInt(str.subSequence(numStart, numStart + widthSize).toString());
              int paddingSize = width - replacement.length();
              if (paddingSize > 0) {
                StringBuilder b = new StringBuilder(width);
                do {
                  b.append('0');
                } while (--paddingSize > 0);
                b.append(replacement);
                replacement = b.toString();
              }
              specSize = 1 + widthSize + 1;
              break;
            default:
              throw new IllegalArgumentException();
          }

          str.replace(formatStart, formatStart + 1 + numSize + 1 + specSize, replacement);
          i = formatStart + replacement.length();
          break;
        }
      }

      if (creator != null) {
        int formatEnd = formatStart + replacement.length();
        Object span = creator.onCreateSpan(str, formatStart, formatEnd, index, Text.needFakeBold(replacement));
        if (span != null) {
          str.setSpan(span, formatStart, formatEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          spanCount++;
          continue;
        }
      }
      if (argument instanceof Spanned) {
        Spanned spanned = (Spanned) argument;
        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        if (spans != null && spans.length > 0) {
          for (Object span : spans) {
            int startIndex = spanned.getSpanStart(span);
            int endIndex = spanned.getSpanEnd(span);
            str.setSpan(span, formatStart + startIndex, formatStart + endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanCount++;
          }
        }
      }
    }
    return spanCount > 0 ? str : str.toString();
  }

  // Counters

  public static String badgeCounter (int count) {
    boolean trySystem = !Settings.instance().forceArabicNumbers();
    if (trySystem) {
      String system = compactNumber(count);
      if (system != null) {
        return system;
      }
    }
    if (Math.abs(count) < 1000) {
      return trySystem ? formatNumber(count) : String.valueOf(count);
    }

    boolean isNegative;
    if (count < 0) {
      count = -count;
      isNegative = true;
    } else {
      isNegative = false;
    }

    int number;
    int decimal;

    int score = 0;
    int attempt = 1000;
    do {
      number = count / attempt;
      decimal = (count % attempt) / (attempt / 10);
      score++;
      attempt *= 1000;
    } while (score < 3 && count >= attempt);

    int suffixRes;
    switch (score) {
      case 0: suffixRes = 0; break;
      case 1: suffixRes = R.string.format_numberSuffix_thousand; break;
      case 2: suffixRes = R.string.format_numberSuffix_million; break;
      case 3: suffixRes = R.string.format_numberSuffix_billion; break;
      default:
        throw new NumberFormatException();
    }

    if (trySystem) {
      if (decimal == 0) {
        return formatNumber(number) + getString(suffixRes);
      } else {
        return formatNumber((double) number + ((double) decimal / 10.0)) + getString(suffixRes);
      }
    }

    StringBuilder b = new StringBuilder();
    if (isNegative)
      b.append('-');
    b.append(number);
    if (decimal != 0) {
      b.append(getDecimalSeparator());
      b.append(decimal);
    }
    if (suffixRes != 0)
      b.append(getString(suffixRes));
    return b.toString();
  }

  // Unread badge

  public static String getUnreadBadge (int unreadCount, int fromChatsCount) {
    if (fromChatsCount <= 1) {
      return plural(R.string.xNewMessages, unreadCount);
    } else {
      return getString(R.string.format_newMessagesFromChats, plural(R.string.xNewMessages, unreadCount), plural(R.string.FromXChats, fromChatsCount));
    }
  }

  public static CharSequence getNotificationTitle (long chatId, String chatTitle, int notificationCount, boolean isSelfChat, boolean isMultiChat, boolean isChannel, boolean areMentions, boolean onlyPinned, boolean areOnlyScheduled, boolean areOnlySilent) {
    CharSequence result;
    if (areMentions && onlyPinned) {
      result = Lang.getCharSequence(R.string.format_notificationTitlePinned, chatTitle);
    } else if (notificationCount > 1 || areMentions) {
      result = Lang.getCharSequence(R.string.format_notificationTitleShort, chatTitle, Lang.plural(areMentions ? R.string.mentionCount : R.string.messagesCount, notificationCount));
    } else if (StringUtils.isEmpty(chatTitle)) {
      result = ChatId.toString(chatId);
    } else {
      result = chatTitle;
    }
    return getSilentNotificationTitle(result, true, isSelfChat, isMultiChat, isChannel, areOnlyScheduled, areOnlySilent);
  }

  public static CharSequence getSilentNotificationTitle (CharSequence title, boolean isTitle, boolean isSelfChat, boolean isMultiChat, boolean isChannel, boolean isScheduled, boolean isSilent) {
    if (isSelfChat && isTitle)
      title = Lang.getString(R.string.Reminder);
    if (isScheduled && !isSelfChat)
      title = Lang.getCharSequence(isTitle ? (isChannel ? R.string.format_notificationScheduledChannel : isMultiChat ? R.string.format_notificationScheduledGroup : R.string.format_notificationScheduledPrivate) : R.string.format_notificationScheduledText, title);
    if (isSilent)
      title = Lang.getCharSequence(isTitle ? R.string.format_notificationSilentTitle : R.string.format_notificationSilentText, title);
    return title;
  }

  public static String getNotificationCategory (int category) {
    switch (category) {
      case TdlibNotificationGroup.CATEGORY_PRIVATE:
        return Lang.getString(R.string.CategoryPrivate);
      case TdlibNotificationGroup.CATEGORY_SECRET:
        return Lang.getString(R.string.CategorySecret);
      case TdlibNotificationGroup.CATEGORY_GROUPS:
        return Lang.getString(R.string.CategoryGroup);
      case TdlibNotificationGroup.CATEGORY_CHANNELS:
        return Lang.getString(R.string.CategoryChannels);
    }
    throw new IllegalArgumentException("category == " + category);
  }

  // Build no

  public static String getAppBuildAndVersion (@Nullable Tdlib tdlib) {
    String msg = Lang.getString(R.string.AppNameAndVersion, BuildConfig.VERSION_NAME);
    if (tdlib != null && tdlib.isEmulator()) {
      msg += " (emulator)";
    }
    return msg;
  }

  // Pinned message

  public static String getPinnedMessageText (Tdlib tdlib, TdApi.MessageSender sender, @Nullable TdApi.Message message, boolean needPerson) {
    String userName = sender != null ? tdlib.senderName(sender) : null;
    if (message == null) {
      if (userName != null) {
        return Lang.getString(R.string.NotificationActionPinnedNoTextChannel, userName);
      } else {
        return Lang.getString(R.string.PinnedMessageChanged);
      }
    }
    String text = TD.getTextFromMessage(message);
    if (!needPerson) {
      if (StringUtils.isEmpty(text))
        text = Lang.lowercase(TD.buildShortPreview(tdlib, message, true));
      return Lang.getString(R.string.format_pinned, text);
    }
    if (userName == null) {
      if (StringUtils.isEmpty(text))
        text = Lang.lowercase(TD.buildShortPreview(tdlib, message, true));
      return Lang.getString(R.string.NewPinnedMessage, text);
    }
    if (!StringUtils.isEmpty(text)) {
      return Lang.getString(R.string.ActionPinnedText, userName, text);
    }
    int res = R.string.ActionPinnedNoText;
    switch (message.content.getConstructor()) {
      case TdApi.MessageAnimation.CONSTRUCTOR:
        res = R.string.ActionPinnedGif;
        break;
      case TdApi.MessageAudio.CONSTRUCTOR:
        res = R.string.ActionPinnedMusic;
        break;
      case TdApi.MessageDocument.CONSTRUCTOR:
        res = R.string.ActionPinnedFile;
        break;
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
        res = R.string.ActionPinnedPhoto;
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
        res = R.string.ActionPinnedVideo;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        res = R.string.ActionPinnedVoice;
        break;
      case TdApi.MessageContact.CONSTRUCTOR:
        res = R.string.ActionPinnedContact;
        break;
      case TdApi.MessageSticker.CONSTRUCTOR:
        res = R.string.ActionPinnedSticker;
        break;
      case TdApi.MessagePoll.CONSTRUCTOR:
        res = ((TdApi.MessagePoll) message.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? R.string.ActionPinnedQuiz : R.string.ActionPinnedPoll;
        break;
      case TdApi.MessageLocation.CONSTRUCTOR:
        res = ((TdApi.MessageLocation) message.content).livePeriod > 0 ? R.string.ActionPinnedGeoLive : R.string.ActionPinnedGeo;
        break;
      case TdApi.MessageVenue.CONSTRUCTOR:
        res = R.string.ActionPinnedGeo;
        break;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        res = R.string.ActionPinnedRound;
        break;
      case TdApi.MessageGame.CONSTRUCTOR: {
        String gameName = TD.getGameName(((TdApi.MessageGame) message.content).game, true);
        if (!StringUtils.isEmpty(gameName))
          return Lang.getString(R.string.ActionPinnedGame, userName, gameName);
        res = R.string.ActionPinnedGameNoName;
        break;
      }
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatSetTtl.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageText.CONSTRUCTOR:
      case TdApi.MessageUnsupported.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessageInvoice.CONSTRUCTOR:
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
      case TdApi.MessageWebsiteConnected.CONSTRUCTOR:
        break;
    }
    String format = Lang.getString(res);
    int startIndex = format.indexOf("**");
    int endIndex = startIndex != -1 ? format.indexOf("**", startIndex + 2) : -1;
    if (startIndex != -1 && endIndex != -1) {
      String arg = format.substring(startIndex + 2, endIndex);
      format = format.substring(0, startIndex) + "%2$s" + format.substring(endIndex + 2);
      return formatString(format, null, userName, arg).toString();
    } else {
      return getString(res, userName);
    }
  }

  // Plurals

  private static Set<String> brokenResources;

  private static void warnResource (boolean isCloud, int languageCode, int res) {
    String key = (isCloud ? 1 : 0) + "_" + languageCode + "_" + res;
    if (brokenResources == null) {
      brokenResources = new HashSet<>();
      brokenResources.add(key);
    } else if (!brokenResources.contains(key)) {
      brokenResources.add(key);
    } else {
      return;
    }
    Log.e("Langpack fix required. languageCode: %s, entry: %s", Integer.toString(languageCode, 16), getResourceEntryName(res));
  }

  private static @StringRes int pluralRes (int lc, @StringRes int res, long num) {
    return LangUtils.getPluralForm(res, numberPluralizationForm(lc, num));
  }

  public static CharSequence pluralBold (@StringRes int res, final long num) {
    return plural(res, num, boldCreator());
  }

  public static CharSequence pluralBold (@StringRes int res, final long num, Object... args) {
    return plural(res, num, boldCreator(), args);
  }

  public static String plural (@StringRes int res, final long num) {
    return pluralImpl(res, num, 0, null, Strings.buildCounter(num)).toString();
  }

  public static String plural (@StringRes int res, final long num, Object... args) {
    return plural(res, num, null, args).toString();
  }

  public static String lowercase (String string) {
    if (Lang.allowLowercase()) {
      return string.toLowerCase();
    } else {
      return string;
    }
  }

  public static String getTryAgainIn (int seconds) {
    if (seconds < 120) {
      return plural(R.string.TryAgainSeconds, seconds);
    } else if ((seconds /= 60) < 60) {
      return plural(R.string.TryAgainMinutes, seconds);
    } else {
      seconds /= 60;
      return plural(R.string.TryAgainHours, seconds);
    }
  }

  public static CharSequence plural (@StringRes int res, final long num, SpanCreator creator, Object... args) {
    if (args != null && args.length > 0) {
      final Object[] newArgs = new Object[args.length + 1];
      newArgs[0] = Strings.buildCounter(num);
      System.arraycopy(args, 0, newArgs, 1, args.length);
      return pluralImpl(res, num, 0, creator, newArgs);
    } else {
      return pluralImpl(res, num, 0, creator, Strings.buildCounter(num));
    }
  }

  public static class PackString {
    public final TdApi.LanguagePackString string;
    public boolean translated;

    public PackString (TdApi.LanguagePackString string, boolean translated) {
      this.string = string;
      this.translated = translated;
    }

    public void rebuild (Pack langPack) {
      switch (string.value.getConstructor()) {
        case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
          TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string.value;
          TdApi.LanguagePackStringValueOrdinary updated = Lang.queryTdlibStringValue(string.key, langPack.languageInfo.id);
          translated = updated != null;
          value.value = translated ? updated.value : getBuiltinValue().value;
          break;
        }
        case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
          TdApi.LanguagePackStringValuePluralized pluralized = (TdApi.LanguagePackStringValuePluralized) string.value;
          TdApi.LanguagePackStringValuePluralized updated = Lang.queryTdlibStringPluralized(string.key, langPack.languageInfo.id);
          translated = updated != null;
          if (updated == null)
            updated = Lang.getBuiltinStringPluralized(string.key, langPack.untranslatedRules.forms);
          pluralized.zeroValue = updated.zeroValue;
          pluralized.oneValue = updated.oneValue;
          pluralized.twoValue = updated.twoValue;
          pluralized.fewValue = updated.fewValue;
          pluralized.manyValue = updated.manyValue;
          pluralized.otherValue = updated.otherValue;
          break;
        }
        case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
          break;
      }
    }

    public int getSection () {
      return Pack.getStringSection(string);
    }

    public String getKey () {
      return string.key;
    }

    private TdApi.LanguagePackStringValue builtinValue;

    public TdApi.LanguagePackStringValueOrdinary getBuiltinValue () {
      if (builtinValue != null)
        return (TdApi.LanguagePackStringValueOrdinary) builtinValue;
      TdApi.LanguagePackStringValueOrdinary value = getBuiltinStringValue(getKey());
      builtinValue = value;
      return value;
    }

    public TdApi.LanguagePackStringValuePluralized getBuiltinPluralized (List<PluralizationForm> forms) {
      if (builtinValue != null)
        return (TdApi.LanguagePackStringValuePluralized) builtinValue;
      TdApi.LanguagePackStringValuePluralized value = getBuiltinStringPluralized(getKey(), forms);
      builtinValue = value;
      return value;
    }
  }

  public static class Pack {
    public final TdApi.LanguagePackInfo languageInfo;

    public final PluralizationRules rules;
    public final PluralizationRules untranslatedRules;
    public final List<PackString> strings;

    private Pack (TdApi.LanguagePackInfo languageInfo, PluralizationRules rules, PluralizationRules untranslatedRules, List<PackString> strings) {
      this.languageInfo = languageInfo;
      this.rules = rules;
      this.untranslatedRules = untranslatedRules;
      this.strings = strings;
    }

    public void rebuild () {
      for (PackString string : strings) {
        string.rebuild(this);
      }
    }

    public TdApi.SetCustomLanguagePack getSaveMethod () {
      List<TdApi.LanguagePackString> strings = new ArrayList<>(this.strings.size());
      for (PackString string : this.strings) {
        if (string.translated)
          strings.add(string.string);
      }
      if (strings.isEmpty())
        return null;
      TdApi.LanguagePackString[] result = new TdApi.LanguagePackString[strings.size()];
      strings.toArray(result);
      return new TdApi.SetCustomLanguagePack(languageInfo, result);
    }

    public int getTranslationPercentage () {
      int totalCount = strings.size();
      int untranslatedCount = getUntranslatedCount();
      return (int) Math.floor((float) (totalCount - untranslatedCount) / (float) totalCount * 100f);
    }

    public PackString findNextUntranslatedString (@Nullable PackString otherString) {
      int startIndex = otherString != null ? strings.indexOf(otherString) : -1;
      if (startIndex == -1)
        startIndex = 0;
      if (startIndex == 0) {
        for (PackString string : strings) {
          if (string != otherString && !string.translated)
            return string;
        }
        return null;
      }
      int count = strings.size();
      for (int i = startIndex + 1; i < count; i++) {
        PackString string = strings.get(i);
        if (string != otherString && !string.translated)
          return string;
      }
      int index = 0;
      for (PackString string : strings) {
        if (string != otherString && !string.translated)
          return string;
        if (++index == startIndex)
          break;
      }
      return null;
    }

    public int getUntranslatedCount () {
      int count = 0;
      for (PackString string : strings) {
        if (!string.translated)
          count++;
      }
      return count;
    }

    // Sections

    public static final int SECTION_MAIN = 1;
    public static final int SECTION_URL = 2;
    public static final int SECTION_JSON = 3;
    public static final int SECTION_FORMAT = 4;
    public static final int SECTION_RELATIVE_DATE = 5;
    public static final int SECTION_PLURAL = 6;
    public static final int SECTION_OTHER_FORMATTED = 7;
    public static final int SECTION_OTHER = 8;

    public static @StringRes int getSectionName (int section) {
      switch (section) {
        case SECTION_MAIN:
          return R.string.ToolsStringSectionMain;
        case SECTION_URL:
          return R.string.ToolsStringSectionUrl;
        case SECTION_FORMAT:
          return R.string.ToolsStringSectionFormat;
        case SECTION_RELATIVE_DATE:
          return R.string.ToolsStringSectionRelativeDate;
        case SECTION_PLURAL:
          return R.string.ToolsStringSectionPlural;
        case SECTION_OTHER_FORMATTED:
          return R.string.ToolsStringSectionSimpleFormatted;
        case SECTION_OTHER:
          return R.string.ToolsStringSectionSimple;
        case SECTION_JSON:
          return R.string.ToolsStringSectionJson;
      }
      throw new IllegalArgumentException("section == " + section);
    }

    public static int getStringSection (TdApi.LanguagePackString string) {
      String key = string.key;
      if (key.startsWith("language_"))
        return SECTION_MAIN;
      if (key.startsWith("url_"))
        return SECTION_URL;
      if (key.startsWith("format_"))
        return SECTION_FORMAT;
      if (key.startsWith("json_"))
        return SECTION_JSON;
      String[] suffixes = {"now", "minutes", "hours", "today", "yesterday", "weekday", "date"};
      for (String suffix : suffixes) {
        if (key.endsWith("_" + suffix))
          return SECTION_RELATIVE_DATE;
      }
      if (string.value instanceof TdApi.LanguagePackStringValuePluralized)
        return SECTION_PLURAL;
      if (string.value instanceof TdApi.LanguagePackStringValueOrdinary) {
        String value = ((TdApi.LanguagePackStringValueOrdinary) string.value).value;
        if (StringUtils.hasFormatArguments(value))
          return SECTION_OTHER_FORMATTED;
      }
      return SECTION_OTHER;
    }

    // String

    public void makeString (Lang.PackString string, SpannableStringBuilder out, boolean useNewLines) {
      switch (string.string.value.getConstructor()) {
        case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
          TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string.string.value;
          makeString(value.value, out, useNewLines, -1);
          break;
        }
        case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
          TdApi.LanguagePackStringValuePluralized plural = (TdApi.LanguagePackStringValuePluralized) string.string.value;
          Lang.PluralizationRules rules = string.translated ? this.rules : this.untranslatedRules;
          for (Lang.PluralizationForm form : rules.forms) {
            String str;
            switch (form.form) {
              case Lang.PluralForm.FEW:
                str = plural.fewValue;
                break;
              case Lang.PluralForm.MANY:
                str = plural.manyValue;
                break;
              case Lang.PluralForm.ONE:
                str = plural.oneValue;
                break;
              case Lang.PluralForm.OTHER:
                str = plural.otherValue;
                break;
              case Lang.PluralForm.TWO:
                str = plural.twoValue;
                break;
              case Lang.PluralForm.ZERO:
                str = plural.zeroValue;
                break;
              default:
                continue;
            }
            if (!StringUtils.isEmpty(str) && form.numbers.length > 0) {
              makeString(str, out, useNewLines, form.numbers[0]);
            }
          }
          break;
        }
      }
    }

    private Pattern _pattern;
    public Pattern getPattern () {
      if (_pattern == null)
        _pattern = Pattern.compile("(%%|%\\d+\\$(?:\\d+)?\\w)");
      return _pattern;
    }

    public void makeString (String str, SpannableStringBuilder out, boolean useNewLines, int num) {
      if (StringUtils.isEmpty(str)) {
        return;
      }
      int newLineIndex = str.indexOf("\n");
      if (newLineIndex != -1) {
        if (useNewLines) {
          if (num >= 0 && out.length() > 0) {
            str = "\n" + str;
          }
        } else {
          str = str.substring(0, newLineIndex) + "…";
        }
      }

      if (out.length() > 0) {
        out.append(useNewLines ? "\n" : ", ");
      }
      int startIndex = out.length();
      out.append(str);
      Matcher matcher = getPattern().matcher(str);
      while (matcher.find()) {
        int start = matcher.start();
        int end = matcher.end();
        out.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), R.id.theme_color_textNeutral).setEntityType(new TdApi.TextEntityTypeBold()).setFakeBold(Text.needFakeBold(str, start, end)), startIndex + start, startIndex + end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (num >= 0) {
        int index = StringUtils.indexOf(out, "%1$s", startIndex);
        if (index != -1) {
          int endIndex = index + "%1$s".length();
          CustomTypefaceSpan[] found = out.getSpans(index, endIndex, CustomTypefaceSpan.class);
          if (found != null && found.length == 1) {
            out.removeSpan(found[0]);
            String replacement = Lang.formatNumber(num);
            found[0].setFakeBold(Text.needFakeBold(replacement));
            out.replace(index, endIndex, replacement);
            out.setSpan(found[0], index, index + replacement.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
        }
      }
      int endIndex = out.length();
      if (startIndex != endIndex) {
        int spaceStartCount = 0;
        while (startIndex + spaceStartCount < endIndex && Strings.isWhitespace(out.charAt(startIndex + spaceStartCount))) {
          spaceStartCount++;
        }
        int spaceEndCount = 0;
        while (endIndex - spaceEndCount - 1 > startIndex && Strings.isWhitespace(out.charAt(endIndex - spaceEndCount - 1))) {
          spaceEndCount++;
        }
        int color = 0xaaff0000; // U.alphaColor(.5f, Theme.getColor(R.id.theme_color_textNegativeAction));
        if (spaceStartCount > 0) {
          out.setSpan(new BackgroundColorSpan(color), startIndex, startIndex + spaceStartCount, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (spaceEndCount > 0) {
          out.setSpan(new BackgroundColorSpan(color), endIndex - spaceEndCount, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }
  }

  public static int[] getRequiredKeys () {
    return new int[] {
      R.string.language_code,
      R.string.language_emoji,
      R.string.language_name,
      R.string.language_nameInEnglish,
      R.string.language_dateFormatLocale,
      R.string.language_continueInLanguage,
      R.string.language_continueInLanguagePopupText
    };
  }

  public static Pack getLanguagePack (@NonNull TdApi.LanguagePackInfo languageInfo) {
    String[][] keys = LangUtils.getAllKeys();
    List<PackString> strings = new ArrayList<>();
    PluralizationRules untranslatedRules = getPluralizationRules(makeLanguageCode(getStringImpl(null, R.string.language_code, false)));
    TdApi.LanguagePackStringValueOrdinary languageCodeCloud = queryTdlibStringValue(Lang.INTERNAL_ID_KEY, languageInfo.id);
    PluralizationRules pluralizationRules = languageCodeCloud != null ? getPluralizationRules(makeLanguageCode(languageCodeCloud.value)) : untranslatedRules;
    for (String valueKey : keys[0]) {
      TdApi.LanguagePackStringValue value = queryTdlibStringValue(valueKey, languageInfo.id);
      boolean translated = true;
      if (value == null && !StringUtils.isEmpty(languageInfo.baseLanguagePackId)) {
        value = queryTdlibStringValue(valueKey, languageInfo.baseLanguagePackId);
        translated = false;
      }
      if (value != null) {
        strings.add(new PackString(new TdApi.LanguagePackString(valueKey, value), translated));
      } else {
        strings.add(new PackString(new TdApi.LanguagePackString(valueKey, getBuiltinStringValue(valueKey)), false));
      }
    }
    for (String pluralKey : keys[1]) {
      TdApi.LanguagePackStringValuePluralized plural = queryTdlibStringPluralized(pluralKey, languageInfo.id);
      boolean translated = true;
      if (plural == null && !StringUtils.isEmpty(languageInfo.baseLanguagePackId)) {
        plural = queryTdlibStringPluralized(pluralKey, languageInfo.baseLanguagePackId);
        translated = false;
      }
      if (plural != null) {
        strings.add(new PackString(new TdApi.LanguagePackString(pluralKey, plural), translated));
      } else {
        strings.add(new PackString(new TdApi.LanguagePackString(pluralKey, getBuiltinStringPluralized(pluralKey, untranslatedRules.forms)), false));
      }
    }
    Collections.sort(strings, (a, b) -> {
      int s1 = Pack.getStringSection(a.string);
      int s2 = Pack.getStringSection(b.string);
      if (s1 != s2)
        return Integer.compare(s1, s2);
      else
        return a.string.key.compareTo(b.string.key);
    });
    return new Pack(languageInfo, pluralizationRules, untranslatedRules, strings);
  }

  public static TdApi.LanguagePackStringValueOrdinary getBuiltinStringValue (String key) {
    return new TdApi.LanguagePackStringValueOrdinary(getStringImpl(null, getStringResourceIdentifier(key), false));
  }

  public static String getBuiltinString (int resId) {
    return getStringImpl(null, resId, false);
  }

  public static TdApi.LanguagePackStringValuePluralized getBuiltinStringPluralized (String key, List<PluralizationForm> forms) {
    TdApi.LanguagePackStringValuePluralized pluralized = new TdApi.LanguagePackStringValuePluralized("", "", "", "", "", "");
    for (PluralizationForm form : forms) {
      switch (form.form) {
        case PluralForm.FEW:
          pluralized.fewValue = getStringImpl(null, getStringResourceIdentifier(key + "_few"), false);
          break;
        case PluralForm.MANY:
          pluralized.manyValue = getStringImpl(null, getStringResourceIdentifier(key + "_many"), false);
          break;
        case PluralForm.ONE:
          pluralized.oneValue = getStringImpl(null, getStringResourceIdentifier(key + "_one"), false);
          break;
        case PluralForm.OTHER:
          pluralized.otherValue = getStringImpl(null, getStringResourceIdentifier(key + "_other"), false);
          break;
        case PluralForm.TWO:
          pluralized.twoValue = getStringImpl(null, getStringResourceIdentifier(key + "_two"), false);
          break;
        case PluralForm.ZERO:
          pluralized.zeroValue = getStringImpl(null, getStringResourceIdentifier(key + "_zero"), false);
          break;
        default:
          throw new IllegalArgumentException("form == " + form);
      }
    }
    return pluralized;
  }

  private static CharSequence pluralImpl (@StringRes int resId, long num, int flags, SpanCreator creator, Object... formatArgs) {
    if (resId == 0)
      throw new Resources.NotFoundException("resId == 0");

    String key = getResourceEntryName(resId);
    TdApi.LanguagePackStringValuePluralized string = getStringPluralized(key, Settings.instance().getLanguagePackInfo());
    if (string != null) {
      int languageCode = pluralCode();
      int pluralForm = numberPluralizationForm(languageCode, num);
      String value = getPluralForm(string, pluralForm);
      if (StringUtils.isEmpty(value) && pluralForm != PluralForm.OTHER) {
        pluralForm = PluralForm.OTHER;
        value = string.otherValue;
      }
      try {
        return formatString(value, creator, formatArgs);
      } catch (Throwable t) {
        warnResource(true, languageCode, LangUtils.getPluralForm(resId, pluralForm));
      }
    }

    final int languageCode = fallbackLanguageCode();
    final int pluralRes = pluralRes(languageCode, resId, num);
    try {
      return getStringImpl(null, pluralRes, false, flags, creator, formatArgs);
    } catch (Throwable t) {
      if (languageCode == LANGUAGE_CODE_DEFAULT)
        throw new IllegalStateException("Broken plural: " + getResourceEntryName(resId), t);
      warnResource(false, languageCode, pluralRes);
      int newRes = pluralRes(LANGUAGE_CODE_DEFAULT, resId, num);
      return getStringImpl(null, newRes, false, flags, creator, formatArgs);
    }
  }

  public static String getPluralForm (TdApi.LanguagePackStringValuePluralized string, @PluralForm int form) {
    switch (form) {
      case PluralForm.ZERO:
        return string.zeroValue;
      case PluralForm.ONE:
        return string.oneValue;
      case PluralForm.TWO:
        return string.twoValue;
      case PluralForm.FEW:
        return string.fewValue;
      case PluralForm.MANY:
        return string.manyValue;
      case PluralForm.OTHER:
        return string.otherValue;
    }
    throw new UnsupportedOperationException("form == " + form);
  }

  // Duration

  public static boolean preferTimeForDuration (int seconds) {
    return (seconds / 60 / 60 / 24 / 7) > 2 || seconds <= 0;
  }

  public static String getCallDuration (int seconds) {
    if (seconds < 60)
      return Lang.plural(R.string.xSec, seconds);
    int minutes = seconds / 60;
    if (minutes < 60) {
      int remain = seconds % 60;
      if (remain == 0) {
        return Lang.plural(R.string.xMin, minutes);
      } else {
        return Lang.getString(R.string.format_minutesAndSeconds, Lang.plural(R.string.xMin, minutes), Lang.plural(R.string.xSec, remain));
      }
    }
    return Strings.buildDuration(seconds);
  }

  public static String getDurationFull (int seconds) {
    return getDuration(seconds, 0, 0, true);
  }

  public static String getDuration (int seconds) {
    return getDuration(seconds, 0, 0, false);
  }

  public static String getDuration (final int seconds, int fullDateRes, int fullDateSeconds, boolean needFull) {
    if (preferTimeForDuration(seconds) && (fullDateRes != 0 || fullDateSeconds != 0)) {
      if (fullDateSeconds != 0) {
        return fullDateRes != 0 ? getString(fullDateRes, getDate(fullDateSeconds, TimeUnit.SECONDS)) : getDate(fullDateSeconds, TimeUnit.SECONDS);
      } else {
        return fullDateRes != 0 ? getString(fullDateRes) : null;
      }
    }
    if (seconds < 60) {
      return plural(R.string.xSeconds, seconds);
    }
    int minutes = seconds / 60;
    if (minutes < 60) {
      return plural(R.string.xMinutes, minutes);
    }
    int hours = minutes / 60;
    if (needFull) {
      return Strings.buildDuration(seconds);
    }
    if (hours < 24) {
      return plural(R.string.xHours, hours);
    }
    int days = hours / 24;
    if (days < 7) {
      return plural(R.string.xDays, days);
    }
    int weeks = days / 7;
    if (weeks < 4) {
      return plural(R.string.xWeeks, weeks);
    }
    int months = days / 30;
    if (months < 12) {
      return plural(R.string.xMonths, months);
    }
    int years = days / 365;
    return plural(R.string.xYears, years);
  }

  public static String getDuration (int seconds, @StringRes int zeroRes) {
    if (seconds <= 0 && zeroRes != 0) {
      return getString(zeroRes);
    }
    if (seconds < 60) {
      return plural(R.string.xSeconds, seconds);
    }
    int minutes = seconds / 60;
    if (minutes < 60) {
      return plural(R.string.xMinutes, minutes);
    }
    int hours = minutes / 60;
    if (hours < 24) {
      return plural(R.string.xHours, hours);
    }
    int days = hours / 24;
    if (days < 7) {
      return plural(R.string.xDays, days);
    }
    if (days < 30) {
      return plural(R.string.xWeeks, days / 7);
    }
    int months = days / 30;
    if (months < 12) {
      return plural(R.string.xMonths, months);
    }
    int years = days / 365;
    return plural(R.string.xYears, years);
  }

  // Counter

  public static String format (int resource, int... numbers) {
    if (numbers == null || numbers.length == 0) {
      return getString(resource);
    }
    final Object[] args = new Object[numbers.length];
    int i = 0;
    for (int x : numbers) {
      args[i++] = counter(resource, x);
    }
    return getString(resource, args);
  }

  private static String counter (int resource, int count) {
    if (count < 1000) {
      return String.valueOf(count);
    }
    /*if (count >= 10000 ) {
      switch (resource) {
        case R.string.xMembers:
        case R.string.xOnline: {
          return Strings.buildShortCounter(count, false);
        }
      }
    }*/
    return Strings.buildCounter(count);
  }

  // Special cases

  public static String getXofY (int x, int y) {
    return getString(R.string.XofY, counter(R.string.XofY, x), counter(R.string.XofY, y));
  }

  public static CharSequence pluralMembers (int members, int online, boolean isChannel) {
    if (online > 0) {
      final int membersRes, formatRes = R.string.format_membersAndOnline;
      if (isChannel) {
        membersRes = Config.CHANNEL_MEMBER_STRING;
      } else {
        membersRes = R.string.xMembers;
      }
      return getCharSequence(formatRes, plural(membersRes, members, boldCreator()), plural(R.string.xOnline, online, boldCreator()));
    } else {
      return plural(isChannel ? Config.CHANNEL_MEMBER_STRING : R.string.xMembers, members, boldCreator());
    }
  }

  public static String pluralPeopleNames (List<String> names, int others) {
    String concat = TextUtils.join(Lang.getConcatSeparator(), names);
    if (others == 0)
      return concat;
    return getString(R.string.format_peopleNamesAndOthers, concat, plural(R.string.xOtherPeopleNames, others));
  }

  public static String pluralChatTitles (List<String> names, int others) {
    String concat = TextUtils.join(Lang.getConcatSeparator(), names);
    if (others == 0)
      return concat;
    return getString(R.string.format_chatTitlesAndOthers, concat, plural(R.string.xOtherChatTitles, others));
  }

  public static CharSequence pluralPhotosAndVideos (int photosCount, int videosCount) {
    if (photosCount > 0 && videosCount > 0)
      return getCharSequence(R.string.format_photosAndVideos, pluralBold(R.string.xPhotos, photosCount), pluralBold(R.string.xVideos, videosCount));
    if (videosCount > 0)
      return pluralBold(R.string.xVideos, videosCount);
    if (photosCount > 0)
      return pluralBold(R.string.xPhotos, photosCount);
    return getString(R.string.NoMediaYet);
  }

  // OK

  public static String getOK () {
    return getString(R.string.OK);
  }

  public static String beautifyCoordinates (double latitude, double longitude) {
    return String.format(Locale.US, "%f, %f", MathUtils.roundDouble(latitude), MathUtils.roundDouble(longitude));
  }

  public static String beautifyDouble (double x) {
    if (x == (long) x) {
      return Long.toString((long) x);
    } else {
      return String.format(Locale.US, "%.2f", x);
    }
  }

  // Date & Time defined by system

  public static String time (final Calendar c) {
    return time(c.getTimeInMillis(), TimeUnit.MILLISECONDS);
  }
  public static String time (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return systemTime(timeMs, STYLE_SHORT, UI.needAmPm() ? "h:mm a" : "H:mm");
  }

  public static String hour (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return dateFormat(UI.needAmPm() ? "hh a" : "HH", timeMs);
  }

  public static String weekShort (final Calendar c) {
    return dateFormat("ccc", c.getTimeInMillis());
  }

  public static String weekFull (final Calendar c) {
    return StringUtils.ucfirst(dateFormat("cccc", c.getTimeInMillis()), dateFormatLocale());
  }

  private static String dateShort (Calendar c) {
    return systemDateWithoutYear(c.getTimeInMillis(), STYLE_MEDIUM, "d MMM");
  }

  private static String dateYearShort (Calendar c) {
    return systemDate(c.getTimeInMillis(), STYLE_MEDIUM, "d MMM ''yy");
  }

  public static String dateYearShortTime (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return systemDateTime(timeMs, STYLE_MEDIUM, STYLE_SHORT, UI.needAmPm() ? "d MMM ''yy h:mm a" : "d MMM ''yy H:mm");
  }

  private static String dateFull (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return systemDateWithoutYear(timeMs, STYLE_LONG, "d MMMM");
  }

  private static String dateYearFull (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return systemDate(timeMs, STYLE_LONG, "d MMMM yyyy");
  }

  private static String monthYearFull (long unixTime, TimeUnit unit) {
    long timeMs = unit.toMillis(unixTime);
    return StringUtils.ucfirst(systemDateWithoutDay(timeMs, STYLE_LONG, "LLLL yyyy"), dateFormatLocale());
  }

  public static String timeOrDateShort (long unixTime, TimeUnit unit) {
    return timeOrDateShort(DateUtils.calendarInstance(unit.toMillis(unixTime)));
  }

  public static String timeOrDateShort (Calendar c) {
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
    int year = c.get(Calendar.YEAR);
    Calendar now = DateUtils.getNowCalendar();
    if (now.get(Calendar.DAY_OF_YEAR) == dayOfYear && now.get(Calendar.YEAR) == year) {
      return time(c);
    }
    int days = (int) TimeUnit.MILLISECONDS.toDays(DateUtils.getStartOfDay(now) - DateUtils.getStartOfDay(c));
    if (days > 0 && days <= 7) {
      return weekShort(c);
    }
    return dateFullShort(c);
  }

  public static String dateFullShort (long unixTime, TimeUnit unit) {
    return dateFullShort(DateUtils.calendarInstance(unit.toMillis(unixTime)));
  }

  public static String dateFullShort (Calendar c) {
    Calendar now = DateUtils.getNowCalendar();
    int yearNow = now.get(Calendar.YEAR);
    if (c.get(Calendar.YEAR) == yearNow) {
      return dateShort(c);
    } else {
      return dateYearShort(c);
    }
  }

  // Relative date

  public static String getLastSeen (Tdlib tdlib, long unixTime, TimeUnit unit, boolean allowDuration) {
    return getRelativeDate(
      unixTime, unit,
      tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
      allowDuration, 60, R.string.status_LastSeen, false
    );
  }

  public static String getFileTimestamp (long unixTime, TimeUnit unit, long sizeInBytes) {
    if (unixTime <= 0) {
      return Strings.buildSize(sizeInBytes);
    } else {
      return getString(R.string.format_fileSizeAndModifiedDate, Strings.buildSize(sizeInBytes), getRelativeTimestamp(unixTime, unit));
    }
  }

  public static String getMessageTimestamp (long unixTime, TimeUnit unit) {
    return getRelativeTimestamp(unixTime, unit);
  }


  public static CharSequence pluralDuration (long duration, TimeUnit unit,
                                             @StringRes int secondsRes, @StringRes int minutesRes, @StringRes int hoursRes,
                                             @StringRes int daysRes, @StringRes int weeksRes, @StringRes int monthsRes,
                                             Object... args) {
    final long days = unit.toDays(duration);
    final long months = days / 30;
    final long weeks = days / 7;
    final long hours = unit.toHours(duration);
    final long minutes = unit.toMinutes(duration);
    final long seconds = unit.toSeconds(duration);
    if (monthsRes != 0 && months > 0) {
      return Lang.pluralBold(monthsRes, months, args);
    }
    if (weeksRes != 0 && weeks > 0) {
      return Lang.pluralBold(weeksRes, weeks, args);
    }
    if (daysRes != 0 && days > 0) {
      return Lang.pluralBold(daysRes, days, args);
    }
    if (hoursRes != 0 && hours > 0) {
      return Lang.pluralBold(hoursRes, hours, args);
    }
    if (minutesRes != 0 && minutes > 0) {
      return Lang.pluralBold(minutesRes, minutes, args);
    }
    if (secondsRes != 0) {
      return Lang.pluralBold(secondsRes, seconds, args);
    }
    throw new IllegalArgumentException();
  }

  public static String getModifiedTimestamp (long unixTime, TimeUnit unit) {
    return getRelativeDate(unixTime, unit, System.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 30, R.string.modified, false);
  }

  public static String getRelativeTimestampShort (long unixTime, TimeUnit unit) {
    return getRelativeDate(unixTime, unit, System.currentTimeMillis(), TimeUnit.MILLISECONDS, false, 0, R.string.timestampShort, false);
  }

  public static String getRelativeTimestamp (long unixTime, TimeUnit unit) {
    return getRelativeTimestamp(unixTime, unit, false, 0);
  }

  public static String getRelativeTimestamp (long unixTime, TimeUnit unit, boolean allowDuration, int justNowSeconds) {
    return getRelativeTimestamp(unixTime, unit, System.currentTimeMillis(), TimeUnit.MILLISECONDS, allowDuration, justNowSeconds);
  }

  public static String getRelativeTimestamp (long unixTime, TimeUnit unit, long fromUnixTime, TimeUnit fromUnit, boolean allowDuration, int justNowSeconds) {
    return getRelativeDate(unixTime, unit, fromUnixTime, fromUnit, allowDuration, justNowSeconds, R.string.timestamp, false);
  }

  public static CharSequence getReverseRelativeDateBold (long futureUnixTime, TimeUnit futureUnit, long fromUnixTime, TimeUnit fromUnit, boolean allowDuration, int justNowSeconds, @StringRes int res, boolean approximate) {
    if (allowDuration) {
      long difference = futureUnit.toSeconds(futureUnixTime) - fromUnit.toSeconds(fromUnixTime);
      if (difference >= -5 * 60) {
        if (difference < justNowSeconds)
          return getStringBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.NOW));
        if (difference < 60)
          return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.SECONDS), (int) difference);
        difference /= 60;
        if (difference < 60)
          return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.MINUTES), (int) difference);
        difference /= 60;
        if (difference < 4)
          return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.HOURS), (int) difference);
      }
    }

    Calendar c;
    c = DateUtils.calendarInstance(fromUnit.toMillis(fromUnixTime));
    int fromYear = c.get(Calendar.YEAR);
    int fromMonth = c.get(Calendar.MONTH);
    DateUtils.resetToStartOfDay(c);
    long unixTimeFromStartMs = c.getTimeInMillis();

    c = DateUtils.calendarInstance(futureUnit.toMillis(futureUnixTime));
    int futureYear = c.get(Calendar.YEAR);
    int futureMonth = c.get(Calendar.MONTH);
    long unixTimeFutureStartMs = DateUtils.getStartOfDay(c);

    final String time = time(futureUnixTime, futureUnit);
    int days = (int) TimeUnit.MILLISECONDS.toDays(unixTimeFutureStartMs - unixTimeFromStartMs);
    if (days == 0) { // Today
      return getStringBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.TODAY), time);
    }
    if (days == 1) { // Tomorrow
      return getStringBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.TOMORROW), time);
    }
    if (approximate) {
      if (days < 14) { // Less than 2 weeks
        return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.DAYS), days);
      }
      if (days < 30) {
        return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.WEEKS), days / 7);
      }
      int months = (futureYear - fromYear) * 12 + (futureMonth - fromMonth);
      if (months < 12) {
        return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.MONTHS), months);
      }
      return pluralBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.YEARS), months / 12);
    } else {
      if (days < 7) { // Less than a week
        return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.WEEKDAY), weekShort(c), time);
      }
      String date;
      if (fromYear == futureYear) {
        date = dateShort(c);
      } else {
        date = dateYearShort(c);
      }
      return getStringBold(LangUtils.getRelativeDateForm(res, RelativeDateForm.DATE), date, time);
    }
  }

  public static long getNextReverseRelativeDateUpdateMs (long unixTime, TimeUnit futureUnit, long fromUnixTime, TimeUnit fromUnit, boolean allowDuration, int justNowSeconds) {
    long fromUnixTimeMs = fromUnit.toMillis(fromUnixTime);
    long futureUnixTimeMs = futureUnit.toMillis(unixTime);
    if (allowDuration) {
      long differenceMs = futureUnixTimeMs - fromUnixTimeMs;
      long difference = TimeUnit.MILLISECONDS.toSeconds(differenceMs);
      if (difference >= -5 * 60) {
        if (difference < justNowSeconds) // just now -> seconds
          return TimeUnit.SECONDS.toMillis(justNowSeconds) - differenceMs;
        if (difference < 60) // seconds
          return 1000 - differenceMs % 1000;
        difference /= 60;
        if (difference < 60) // minutes
          return (difference + 1l) * 60000l - differenceMs;
        difference /= 60;
        if (difference < 4) // hours
          return (difference + 1) * 60l * 60000l - differenceMs;
      }
    }
    Calendar c;
    c = DateUtils.calendarInstance(futureUnixTimeMs);
    DateUtils.resetToStartOfDay(c);
    long unixTimeFutureStartMs = c.getTimeInMillis();

    c = DateUtils.calendarInstance(fromUnixTimeMs);
    DateUtils.resetToStartOfDay(c);
    long unixTimeFromStartMs = c.getTimeInMillis();

    int days = (int) TimeUnit.MILLISECONDS.toDays(unixTimeFutureStartMs - unixTimeFromStartMs);

    if (days == 0 || days == 1) {
      c.add(Calendar.DAY_OF_MONTH, 1);
      DateUtils.resetToStartOfDay(c);
      return Math.max(-1, c.getTimeInMillis() - fromUnixTimeMs);
    }
    return -1;
  }

  public static String getRelativeDate (long unixTime, TimeUnit unit, long fromUnixTime, TimeUnit fromUnit, boolean allowDuration, int justNowSeconds, @StringRes int res, boolean approximate) {
    if (allowDuration) {
      long difference = fromUnit.toSeconds(fromUnixTime) - unit.toSeconds(unixTime);
      if (difference >= -5 * 60) {
        if (difference < justNowSeconds)
          return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.NOW));
        if (difference < 60)
          return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.SECONDS), (int) difference);
        difference /= 60;
        if (difference < 60)
          return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.MINUTES), (int) difference);
        difference /= 60;
        if (difference < 4)
          return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.HOURS), (int) difference);
      }
    }

    Calendar c;
    c = DateUtils.calendarInstance(fromUnit.toMillis(fromUnixTime));
    int fromYear = c.get(Calendar.YEAR);
    int fromMonth = c.get(Calendar.MONTH);
    DateUtils.resetToStartOfDay(c);
    long unixTimeFromStartMs = c.getTimeInMillis();

    c = DateUtils.calendarInstance(unit.toMillis(unixTime));
    int year = c.get(Calendar.YEAR);
    int month = c.get(Calendar.MONTH);
    long unixTimeStartMs = DateUtils.getStartOfDay(c);

    final String time = time(unixTime, unit);
    int days = (int) TimeUnit.MILLISECONDS.toDays(unixTimeFromStartMs - unixTimeStartMs);
    if (days == 0) { // Today
      return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.TODAY), time);
    }
    if (days == 1) { // Yesterday
      return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.YESTERDAY), time);
    }
    if (approximate) {
      if (days < 14) { // Less than 2 weeks
        return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.DAYS), days);
      }
      if (days < 30) {
        return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.WEEKS), days / 7);
      }
      int months = (fromYear - year) * 12 + (fromMonth - month);
      if (months < 12) {
        return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.MONTHS), months);
      }
      return plural(LangUtils.getRelativeDateForm(res, RelativeDateForm.YEARS), months / 12);
    } else {
      if (days < 7) { // Less than a week
        return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.WEEKDAY), weekShort(c), time);
      }
      String date;
      if (fromYear == year) {
        date = dateShort(c);
      } else {
        date = dateYearShort(c);
      }
      return getString(LangUtils.getRelativeDateForm(res, RelativeDateForm.DATE), date, time);
    }
  }

  public static long getNextRelativeDateUpdateMs (long unixTime, TimeUnit unit, long fromUnixTime, TimeUnit fromUnit, boolean allowDuration, int justNowSeconds) {
    long fromUnixTimeMs = fromUnit.toMillis(fromUnixTime);
    long unixTimeMs = unit.toMillis(unixTime);
    if (allowDuration) {
      long differenceMs = fromUnixTimeMs - unixTimeMs;
      long difference = TimeUnit.MILLISECONDS.toSeconds(differenceMs);
      if (difference >= -5 * 60) {
        if (difference < justNowSeconds) // just now -> seconds
          return TimeUnit.SECONDS.toMillis(justNowSeconds) - differenceMs;
        if (difference < 60) // seconds
          return 1000 - differenceMs % 1000;
        difference /= 60;
        if (difference < 60) // minutes
          return (difference + 1l) * 60000l - differenceMs;
        difference /= 60;
        if (difference < 4) // hours
          return (difference + 1) * 60l * 60000l - differenceMs;
      }
    }
    Calendar c;
    c = DateUtils.calendarInstance(unixTimeMs);
    DateUtils.resetToStartOfDay(c);
    long unixTimeStartMs = c.getTimeInMillis();

    c = DateUtils.calendarInstance(fromUnixTimeMs);
    DateUtils.resetToStartOfDay(c);
    long unixTimeFromStartMs = c.getTimeInMillis();

    int days = (int) TimeUnit.MILLISECONDS.toDays(unixTimeFromStartMs - unixTimeStartMs);

    if (days == 0 || days == 1) {
      c.add(Calendar.DAY_OF_MONTH, 1);
      DateUtils.resetToStartOfDay(c);
      return Math.max(-1, c.getTimeInMillis() - fromUnixTimeMs);
    }
    return -1;
  }

  // Online

  public static String getUserStatus (Tdlib tdlib, TdApi.UserStatus status, boolean allowDuration) {
    switch (status.getConstructor()) {
      case TdApi.UserStatusRecently.CONSTRUCTOR:
        return getString(R.string.status_LastSeenRecently);
      case TdApi.UserStatusLastWeek.CONSTRUCTOR:
        return getString(R.string.status_LastSeenWithinWeek);
      case TdApi.UserStatusLastMonth.CONSTRUCTOR:
        return getString(R.string.status_LastSeenWithinMonth);
      case TdApi.UserStatusOnline.CONSTRUCTOR:
        return getString(R.string.status_Online);
      case TdApi.UserStatusOffline.CONSTRUCTOR: {
        int wasOnline = ((TdApi.UserStatusOffline) status).wasOnline;
        if (wasOnline > 0)
          return getLastSeen(tdlib, wasOnline, TimeUnit.SECONDS, allowDuration);
        else
          return getString(R.string.status_LastSeenUnknown);
      }
      case TdApi.UserStatusEmpty.CONSTRUCTOR:
        return getString(R.string.status_LastSeenUnknown);
    }
    throw new IllegalArgumentException("status == " + status);
  }

  // Numbers

  public static String getConcatSeparator () {
    return getString(R.string.format_concatSeparator);
  }

  public static String getConcatSeparatorLast (boolean isPerson) {
    return getString(isPerson ? R.string.format_concatSeparatorLastPerson : R.string.format_concatSeparatorLast);
  }

  public static String getDecimalSeparator () {
    return getString(R.string.format_decimalSeparator);
  }

  public static String getThousandsSeparator () {
    return getString(R.string.format_thousandsSeparator);
  }

  // Metrics

  public static String shortDistance (float distanceInMeters) {
    return shortDistance(distanceInMeters, R.string.location_Nearby, R.string.location_AwayMeters, R.string.location_AwayKilometers);
  }

  public static String shortDistanceToPerson (float distanceInMeters) {
    return shortDistance(distanceInMeters, R.string.location_personNearby, R.string.location_personAwayMeters, R.string.location_personAwayKilometers);
  }

  public static String shortDistance (float distanceInMeters, @StringRes int nearbyRes, @StringRes int metersRes, @StringRes int kmRes) {
    if (distanceInMeters < 10) {
      return getString(nearbyRes);
    }
    distanceInMeters = Math.round(distanceInMeters);
    distanceInMeters -= distanceInMeters % 10;
    if (distanceInMeters >= 1000) {
      int km = (int) Math.floor(distanceInMeters / 1000f);
      int meters = (int) (distanceInMeters - km * 1000f) / 100;
      StringBuilder kmStr = new StringBuilder(Strings.buildCounter(km));
      if (meters != 0 && km < 1000) {
        kmStr.append(Lang.getDecimalSeparator());
        kmStr.append(meters);
      }
      return getString(kmRes, kmStr.toString());
    } else {
      return getString(metersRes, String.valueOf((int) distanceInMeters));
    }
  }

  // Download status

  public static String getDownloadProgress (long progress, long total, boolean needDownloading) {
    int percent = total != 0 ? (int) ((double) progress / (double) total * 100.0) : 0;
    return getString(needDownloading ? R.string.Downloading : R.string.DownloadingOf, percent + "%", Strings.buildSize(total));
  }

  public static String getDownloadStatus (TdApi.File file, int downloadedRes, boolean forceDownload) {
    if (file == null || (!forceDownload && TD.isFileLoaded(file))) {
      return Lang.getString(downloadedRes);
    } else if (file.local.isDownloadingActive) {
      return getDownloadProgress(file.local.downloadedSize, file.size, true);
    } else {
      return Lang.getString(R.string.CloudDownload, Strings.buildSize(file.size));
    }
  }

  // Dates

  public static String getDate (long unixDate, TimeUnit unit) {
    if (DateUtils.isThisYear(unixDate, unit)) {
      return dateFull(unixDate, unit);
    } else {
      return dateYearFull(unixDate, unit);
    }
  }

  public static String getUntilDate (long unixTime, TimeUnit unit) {
    if (DateUtils.isToday(unixTime, unit)) {
      return time(unixTime, unit);
    } else if (DateUtils.isTomorrow(unixTime, unit)) {
      return Lang.getString(R.string.format_tomorrow, time(unixTime, unit));
    } else if (DateUtils.isThisYear(unixTime, unit)) {
      return Lang.getString(R.string.format_dateTime, dateFull(unixTime, unit), time(unixTime, unit));
    } else {
      return Lang.getString(R.string.format_dateTime, dateYearFull(unixTime, unit), time(unixTime, unit));
    }
  }

  public static String getDateRange (long timeStart, long timeEnd, TimeUnit unit, boolean needTime) {
    Formatter f = new Formatter(new StringBuilder(50), locale());
    return android.text.format.DateUtils.formatDateRange(UI.getAppContext(), f, unit.toMillis(timeStart), unit.toMillis(timeEnd), (needTime ? android.text.format.DateUtils.FORMAT_ABBREV_ALL | android.text.format.DateUtils.FORMAT_SHOW_TIME : android.text.format.DateUtils.FORMAT_ABBREV_ALL)).toString();
  }

  public static String getDatestamp (long time, TimeUnit unit) {
    long unixTime = unit.toMillis(time);
    if (!Settings.instance().forceArabicNumbers()) {
      return systemDate(unixTime, STYLE_SHORT, "dd.MM.yyyy");
    }
    Calendar target = DateUtils.calendarInstance(unixTime);
    int day = target.get(Calendar.DAY_OF_MONTH);
    int month = target.get(Calendar.MONTH) + 1;
    int year = target.get(Calendar.YEAR) % 100;
    return getString(R.string.format_datestamp, day, month, year);
  }

  public static String getTimestamp (long time, TimeUnit unit) {
    long unixTime = unit.toMillis(time);
    if (!Settings.instance().forceArabicNumbers()) {
      return systemDateTime(unixTime, STYLE_SHORT, STYLE_SHORT, UI.needAmPm() ? "dd.MM.yyyy h:mm a" : "dd.MM.yyyy H:mm");
    }
    Calendar target = DateUtils.calendarInstance(unixTime);
    int day = target.get(Calendar.DAY_OF_MONTH);
    int month = target.get(Calendar.MONTH) + 1;
    int year = target.get(Calendar.YEAR) % 100;
    return getString(R.string.format_timestamp, day, month, year, time(target));
  }

  public static String getRelativeMonth (long unixTime, TimeUnit unit, boolean allowWeeks) {
    final int date = (int) unit.toSeconds(unixTime);
    if (allowWeeks && DateUtils.isPastWeek(date)) {
      return getString(R.string.PastWeek);
    } else if (DateUtils.isWithinWeek(date)) {
      if (DateUtils.isToday(date, TimeUnit.SECONDS)) {
        return getString(R.string.Today);
      } else if (DateUtils.isYesterday(date, TimeUnit.SECONDS)) {
        return getString(R.string.Yesterday);
      } else {
        Calendar target = DateUtils.calendarInstance(TimeUnit.SECONDS.toMillis(date));
        return weekFull(target);
      }
    } else if (!allowWeeks) {
      if (DateUtils.isThisYear(unixTime, unit)) {
        return dateFull(unixTime, unit);
      } else {
        return dateYearFull(unixTime, unit);
      }
    } else {
      return monthYearFull(unixTime, unit);
    }
  }

  // Plural form

  public static String cleanLanguageCode (String languageCode) {
    if (StringUtils.isEmpty(languageCode)) {
      return languageCode;
    }
    if (languageCode.charAt(0) == 'X') {
      int i = languageCode.indexOf('X', 1);
      languageCode = i != -1 ? languageCode.substring(1, i) : languageCode.substring(1);
    }
    int len = languageCode.length();
    for (int i = 0; i < len; i++) {
      switch (languageCode.charAt(i)) {
        case '_':
        case '-':
          return languageCode.substring(0, i).toLowerCase();
      }
    }
    return languageCode.toLowerCase();
  }

  public static int makeLanguageCode (String languageCode) {
    String rawCode = cleanLanguageCode(languageCode);
    int len = rawCode.length();
    int lc = 0;
    for (int i = 0; i < len; i++) {
      lc = (lc << 8) + rawCode.charAt(i);
    }
    return lc;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PluralForm.ZERO,
    PluralForm.ONE,
    PluralForm.TWO,
    PluralForm.FEW,
    PluralForm.MANY,
    PluralForm.OTHER
  })
  public @interface PluralForm {
    int ZERO = 0;
    int ONE = 1;
    int TWO = 2;
    int FEW = 3;
    int MANY = 4;
    int OTHER = 5;
  }
  
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RelativeDateForm.NOW,
    RelativeDateForm.SECONDS,
    RelativeDateForm.MINUTES,
    RelativeDateForm.HOURS,

    RelativeDateForm.TODAY,
    RelativeDateForm.YESTERDAY,
    RelativeDateForm.TOMORROW,

    RelativeDateForm.WEEKDAY,
    RelativeDateForm.DATE,

    RelativeDateForm.DAYS,
    RelativeDateForm.WEEKS,
    RelativeDateForm.MONTHS,
    RelativeDateForm.YEARS
  })
  public @interface RelativeDateForm {
    int NOW = 0;
    int SECONDS = 1;
    int MINUTES = 2;
    int HOURS = 3;
    int TODAY = 4;
    int YESTERDAY = 5;
    int TOMORROW = 6;
    
    int WEEKDAY = 10;
    int DATE = 11;

    int DAYS = 21;
    int WEEKS = 22;
    int MONTHS = 23;
    int YEARS = 24;
  }

  public static class PluralizationForm {
    public final @PluralForm int form;
    public final int[] numbers;

    public PluralizationForm (int form, int... numbers) {
      this.form = form;
      this.numbers = numbers;
    }

    public String get (TdApi.LanguagePackStringValuePluralized plural) {
      return get(plural, form);
    }

    public static String get (TdApi.LanguagePackStringValuePluralized plural, @PluralForm int form) {
      switch (form) {
        case Lang.PluralForm.FEW:
          return plural.fewValue;
        case Lang.PluralForm.MANY:
          return plural.manyValue;
        case Lang.PluralForm.ONE:
          return plural.oneValue;
        case Lang.PluralForm.OTHER:
          return plural.otherValue;
        case Lang.PluralForm.TWO:
          return plural.twoValue;
        case Lang.PluralForm.ZERO:
          return plural.zeroValue;
      }
      throw new IllegalArgumentException("form == " + form);
    }

    public static void set (TdApi.LanguagePackStringValuePluralized plural, @PluralForm int form, String value) {
      switch (form) {
        case Lang.PluralForm.FEW:
          plural.fewValue = value;
          break;
        case Lang.PluralForm.MANY:
          plural.manyValue = value;
          break;
        case Lang.PluralForm.ONE:
          plural.oneValue = value;
          break;
        case Lang.PluralForm.OTHER:
          plural.otherValue = value;
          break;
        case Lang.PluralForm.TWO:
          plural.twoValue = value;
          break;
        case Lang.PluralForm.ZERO:
          plural.zeroValue = value;
          break;
        default:
          throw new IllegalArgumentException("form == " + form);
      }
    }
  }

  public static class PluralizationRules {
    public final int languageCode;
    public final List<PluralizationForm> forms = new ArrayList<>();

    public PluralizationRules (int languageCode) {
      this.languageCode = languageCode;
    }

    public PluralizationRules addForm (int form, int... examples) {
      forms.add(new PluralizationForm(form, examples));
      return this;
    }
  }

  public static PluralizationRules getPluralizationRules (int lc) {
    switch (lc) {
      // set1
      case 0x6c74: // lt
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 21, 101, 1001)  // n mod 10 is 1 and n mod 100 not in 11..19
          .addForm(PluralForm.FEW, 2, 22, 102, 1002)  // n mod 10 in 2..9 and n mod 100 not in 11..19
          .addForm(PluralForm.OTHER, 0, 10, 100, 1000);

      // set2
      case 0x6c76: // lv
        return new PluralizationRules(lc)
          .addForm(PluralForm.ZERO, 0, 20, 100, 1000) // n is 0
          .addForm(PluralForm.ONE, 1, 21, 101, 1001) // n mod 10 is 1 and n mod 100 is not 11
          .addForm(PluralForm.OTHER, 2, 22, 102, 1002);

      // set3
      case 0x6379: // cy
        return new PluralizationRules(lc)
          .addForm(PluralForm.TWO, 2)  // n is 2
          .addForm(PluralForm.FEW, 3)  // n is 3
          .addForm(PluralForm.ZERO, 0) // n is 0
          .addForm(PluralForm.ONE, 1)  // n is 1
          .addForm(PluralForm.MANY, 6) // n is 6
          .addForm(PluralForm.OTHER, 10);

      // set4
      case 0x6265: // be
      case 0x6273: // bs
      case 0x6872: // hr
      case 0x7275: // ru
      case 0x7368: // sh
      case 0x7372: // sr
      case 0x756b: // uk
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 21, 101, 1001) // n mod 10 is 1 and n mod 100 is not 11
          .addForm(PluralForm.FEW, 2, 22, 102, 1002) // n mod 10 in 2..4 and n mod 100 not in 12..14
          .addForm(PluralForm.MANY, 0, 5, 100, 1000) // n mod 10 is 0 or n mod 10 in 5..9 or n mod 100 in 11..14
          .addForm(PluralForm.OTHER);

      // set5
      case 0x6b7368: // ksh
        return new PluralizationRules(lc)
          .addForm(PluralForm.ZERO, 0) // n is 0
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.OTHER, 10, 100);

      // set6
      case 0x736869: // shi
        return new PluralizationRules(lc)
          .addForm(PluralForm.FEW, 2, 10) // n in 2..10
          .addForm(PluralForm.ONE, 0, 1) // n within 0..1
          .addForm(PluralForm.OTHER, 11, 100);

      // set7
      case 0x6865: // he
        return new PluralizationRules(lc)
          .addForm(PluralForm.TWO, 2) // n is 2
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.MANY, 20, 100, 1000) // n is not 0 AND n mod 10 is 0
          .addForm(PluralForm.OTHER, 0, 17, 10001);

      // set8
      case 0x6373: // cs
      case 0x736b: // sk
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.FEW, 2, 3, 4) // n in 2..4
          .addForm(PluralForm.OTHER, 0, 10, 100);

      // set9
      case 0x6272: // br
        return new PluralizationRules(lc)
          .addForm(PluralForm.MANY, 1000000) // n is not 0 and n mod 1000000 is 0
          .addForm(PluralForm.ONE, 1, 21, 101, 1001) // n mod 10 is 1 and n mod 100 not in 11,71,91
          .addForm(PluralForm.TWO, 2, 22, 102, 1002) // n mod 10 is 2 and n mod 100 not in 12,72,92
          .addForm(PluralForm.FEW, 3, 24, 103, 1003) // n mod 10 in 3..4,9 and n mod 100 not in 10..19,70..79,90..99
          .addForm(PluralForm.OTHER, 0, 10, 100, 1000);

      // set10
      case 0x736c: // sl
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 101, 1001) // n mod 100 is 1
          .addForm(PluralForm.TWO, 2, 102, 1002) // n mod 100 is 2
          .addForm(PluralForm.FEW, 3, 103, 1003) // n mod 100 in 3..4
          .addForm(PluralForm.OTHER, 0, 5, 100, 1000);

      // set11
      case 0x6c6167: // lag
        return new PluralizationRules(lc)
          .addForm(PluralForm.ZERO, 0) // n is 0
          .addForm(PluralForm.ONE, 1) // n within 0..2 and n is not 0 and n is not 2
          .addForm(PluralForm.OTHER, 2, 17, 100, 1000);

      // set12
      case 0x706c: // pl
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.FEW, 2, 102, 1002) // n mod 10 in 2..4 and n mod 100 not in 12..14
          .addForm(PluralForm.MANY, 0, 5, 100, 1000) // n is not 1 and n mod 10 in 0..1 or n mod 10 in 5..9 or n mod 100 in 12..14
          .addForm(PluralForm.OTHER);

      // set13
      case 0x6764: // gd
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 11) // n in 1,11
          .addForm(PluralForm.TWO, 2, 12) // n in 2,12
          .addForm(PluralForm.FEW, 3, 13, 19) // n in 3..10,13..19
          .addForm(PluralForm.OTHER, 0, 24, 100, 1000);

      // set14
      case 0x6776: // gv
        // FIXME according to http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 11, 101, 1001) // n mod 10 in 1..2 or n mod 20 is 0
          .addForm(PluralForm.OTHER, 5, 55, 155);

      // set15
      case 0x6d6b: // mk
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1, 21, 31) // n mod 10 is 1 and n is not 11
          .addForm(PluralForm.OTHER, 11, 105, 1008);

      // set16
      case 0x6d74: // mt
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1)
          .addForm(PluralForm.FEW, 0, 10, 102, 1002)
          .addForm(PluralForm.MANY, 11, 111, 1011)
          .addForm(PluralForm.OTHER, 20, 100, 1000);

      // set17
      case 0x6d6f: // mo
      case 0x726f: // ro
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.FEW, 0, 2, 16) // n is 0 OR n is not 1 AND n mod 100 in 1..19
          .addForm(PluralForm.OTHER, 20, 100, 1000);

      // set18
      case 0x6761: // ga
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1)
          .addForm(PluralForm.TWO, 2)
          .addForm(PluralForm.FEW, 3, 6)
          .addForm(PluralForm.MANY, 7, 10)
          .addForm(PluralForm.OTHER, 0, 11, 100, 1000);

      // set19
      case 0x6666: // ff
      case 0x6672: // fr
      case 0x6b6162: // kab
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 0, 1)
          .addForm(PluralForm.OTHER, 2, 17, 100, 1000);

      // set20
      case 0x6975: // iu
      case 0x6b77: // kw
      case 0x7365: // se
      case 0x6e6171: // naq
      case 0x736d61: // sma
      case 0x736d69: // smi
      case 0x736d6a: // smj
      case 0x736d6e: // smn
      case 0x736d73: // sms
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1)
          .addForm(PluralForm.TWO, 2)
          .addForm(PluralForm.OTHER, 0, 3, 100, 1000);

      // set21
      case 0x616b: // ak
      case 0x616d: // am
      case 0x6268: // bh
      case 0x6869: // hi
      case 0x6c6e: // ln
      case 0x6d67: // mg
      case 0x7469: // ti
      case 0x746c: // tl
      case 0x7761: // wa
      case 0x66696c: // fil
      case 0x677577: // guw
      case 0x6e736f: // nso
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 0, 1)
          .addForm(PluralForm.OTHER, 2, 100, 1000);

      // set22
      case 0x747a6d: // tzm
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 0, 1, 11)
          .addForm(PluralForm.OTHER, 2, 24, 100);

      // set23
      case 0x6166: // af
      case 0x6267: // bg
      case 0x626e: // bn
      case 0x6361: // ca
      case 0x6461: // da
      case 0x6465: // de
      case 0x6476: // dv
      case 0x6565: // ee
      case 0x656c: // el
      case 0x656e: // en
      case 0x656f: // eo
      case 0x6573: // es
      case 0x6574: // et
      case 0x6575: // eu
      case 0x6669: // fi
      case 0x666f: // fo
      case 0x6679: // fy
      case 0x676c: // gl
      case 0x6775: // gu
      case 0x6861: // ha
      case 0x6973: // is
      case 0x6974: // it
      case 0x6b6b: // kk
      case 0x6b6c: // kl
      case 0x6b73: // ks
      case 0x6b75: // ku
      case 0x6b79: // ky
      case 0x6c62: // lb
      case 0x6c67: // lg
      case 0x6d6c: // ml
      case 0x6d6e: // mn
      case 0x6d72: // mr
      case 0x6e62: // nb
      case 0x6e64: // nd
      case 0x6e65: // ne
      case 0x6e6c: // nl
      case 0x6e6e: // nn
      case 0x6e6f: // no
      case 0x6e72: // nr
      case 0x6e79: // ny
      case 0x6f6d: // om
      case 0x6f72: // or
      case 0x6f73: // os
      case 0x7061: // pa
      case 0x7073: // ps
      case 0x7074: // pt
      case 0x726d: // rm
      case 0x736e: // sn
      case 0x736f: // so
      case 0x7371: // sq
      case 0x7373: // ss
      case 0x7374: // st
      case 0x7376: // sv
      case 0x7377: // sw
      case 0x7461: // ta
      case 0x7465: // te
      case 0x746b: // tk
      case 0x746e: // tn
      case 0x7473: // ts
      case 0x7572: // ur
      case 0x7665: // ve
      case 0x766f: // vo
      case 0x7868: // xh
      case 0x7a75: // zu
      case 0x617361: // asa
      case 0x617374: // ast
      case 0x62656d: // bem
      case 0x62657a: // bez
      case 0x627278: // brx
      case 0x636767: // cgg
      case 0x636872: // chr
      case 0x636b62: // ckb
      case 0x667572: // fur
      case 0x677377: // gsw
      case 0x686177: // haw
      case 0x6a676f: // jgo
      case 0x6a6d63: // jmc
      case 0x6b616a: // kaj
      case 0x6b6367: // kcg
      case 0x6b6b6a: // kkj
      case 0x6b7362: // ksb
      case 0x6d6173: // mas
      case 0x6d676f: // mgo
      case 0x6e6168: // nah
      case 0x6e6e68: // nnh
      case 0x6e796e: // nyn
      case 0x706170: // pap
      case 0x726f66: // rof
      case 0x72776b: // rwk
      case 0x736171: // saq
      case 0x736568: // seh
      case 0x737379: // ssy
      case 0x737972: // syr
      case 0x74656f: // teo
      case 0x746967: // tig
      case 0x76756e: // vun
      case 0x776165: // wae
      case 0x786f67: // xog
        return new PluralizationRules(lc)
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.OTHER, 0, 10, 100, 1000);

      // set24
      case 0x6172: // ar
        return new PluralizationRules(lc)
          .addForm(PluralForm.ZERO, 0) // n is 0
          .addForm(PluralForm.ONE, 1) // n is 1
          .addForm(PluralForm.TWO, 2) // n is 2
          .addForm(PluralForm.FEW, 3, 103, 1003) // n mod 100 in 3..10
          .addForm(PluralForm.MANY, 11, 111, 1011) // n mod 100 in 11..99
          .addForm(PluralForm.OTHER, 100, 202, 1000);
    }

    return new PluralizationRules(lc)
      .addForm(PluralForm.OTHER, 1, 5, 100);
  }

  public static @PluralForm int numberPluralizationForm (int lc, long n) {
    switch (lc) {
      // set1
      case 0x6c74: // lt
        if (((n % 10) == 1) && (((n % 100) < 11 || (n % 100) > 19))) // n mod 10 is 1 and n mod 100 not in 11..19
          return PluralForm.ONE;
        if ((((n % 10) >= 2 && (n % 10) <= 9)) && (((n % 100) < 11 || (n % 100) > 19))) // n mod 10 in 2..9 and n mod 100 not in 11..19
          return PluralForm.FEW;
        break;

      // set2
      case 0x6c76: // lv
        if (n == 0) // n is 0
          return PluralForm.ZERO;
        if (((n % 10) == 1) && ((n % 100) != 11)) // n mod 10 is 1 and n mod 100 is not 11
          return PluralForm.ONE;
        break;

      // set3
      case 0x6379: // cy
        if (n == 2) // n is 2
          return PluralForm.TWO;
        if (n == 3) // n is 3
          return PluralForm.FEW;
        if (n == 0) // n is 0
          return PluralForm.ZERO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if (n == 6) // n is 6
          return PluralForm.MANY;
        break;

      // set4
      case 0x6265: // be
      case 0x6273: // bs
      case 0x6872: // hr
      case 0x7275: // ru
      case 0x7368: // sh
      case 0x7372: // sr
      case 0x756b: // uk
        if (((n % 10) == 1) && ((n % 100) != 11)) // n mod 10 is 1 and n mod 100 is not 11
          return PluralForm.ONE;
        if ((((n % 10) >= 2 && (n % 10) <= 4)) && (((n % 100) < 12 || (n % 100) > 14))) // n mod 10 in 2..4 and n mod 100 not in 12..14
          return PluralForm.FEW;
        if (((n % 10) == 0) || (((n % 10) >= 5 && (n % 10) <= 9)) || (((n % 100) >= 11 && (n % 100) <= 14))) // n mod 10 is 0 or n mod 10 in 5..9 or n mod 100 in 11..14
          return PluralForm.MANY;
        break;

      // set5
      case 0x6b7368: // ksh
        if (n == 0) // n is 0
          return PluralForm.ZERO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        break;

      // set6
      case 0x736869: // shi
        if ((n >= 2 && n <= 10)) // n in 2..10
          return PluralForm.FEW;
        if ((n >= 0 && n <= 1)) // n within 0..1
          return PluralForm.ONE;
        break;

      // set7
      case 0x6865: // he
        if (n == 2) // n is 2
          return PluralForm.TWO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if ((n != 0) && ((n % 10) == 0)) // n is not 0 AND n mod 10 is 0
          return PluralForm.MANY;
        break;

      // set8
      case 0x6373: // cs
      case 0x736b: // sk
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if ((n >= 2 && n <= 4)) // n in 2..4
          return PluralForm.FEW;
        break;

      // set9
      case 0x6272: // br
        if ((n != 0) && ((n % 1000000) == 0)) // n is not 0 and n mod 1000000 is 0
          return PluralForm.MANY;
        if (((n % 10) == 1) && (((n % 100) != 11) && ((n % 100) != 71) && ((n % 100) != 91))) // n mod 10 is 1 and n mod 100 not in 11,71,91
          return PluralForm.ONE;
        if (((n % 10) == 2) && (((n % 100) != 12) && ((n % 100) != 72) && ((n % 100) != 92))) // n mod 10 is 2 and n mod 100 not in 12,72,92
          return PluralForm.TWO;
        if ((((n % 10) >= 3 && (n % 10) <= 4) || ((n % 10) == 9)) && (((n % 100) < 10 || (n % 100) > 19) && ((n % 100) < 70 || (n % 100) > 79) && ((n % 100) < 90 || (n % 100) > 99))) // n mod 10 in 3..4,9 and n mod 100 not in 10..19,70..79,90..99
          return PluralForm.FEW;
        break;

      // set10
      case 0x736c: // sl
        if ((n % 100) == 2) // n mod 100 is 2
          return PluralForm.TWO;
        if ((n % 100) == 1) // n mod 100 is 1
          return PluralForm.ONE;
        if (((n % 100) >= 3 && (n % 100) <= 4)) // n mod 100 in 3..4
          return PluralForm.FEW;
        break;

      // set11
      case 0x6c6167: // lag
        if (n == 0) // n is 0
          return PluralForm.ZERO;
        if (((n >= 0 && n <= 2)) && (n != 0) && (n != 2)) // n within 0..2 and n is not 0 and n is not 2
          return PluralForm.ONE;
        break;

      // set12
      case 0x706c: // pl
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if ((((n % 10) >= 2 && (n % 10) <= 4)) && (((n % 100) < 12 || (n % 100) > 14))) // n mod 10 in 2..4 and n mod 100 not in 12..14
          return PluralForm.FEW;
        if (((n != 1) && (((n % 10) >= 0 && (n % 10) <= 1))) || (((n % 10) >= 5 && (n % 10) <= 9)) || (((n % 100) >= 12 && (n % 100) <= 14))) // n is not 1 and n mod 10 in 0..1 or n mod 10 in 5..9 or n mod 100 in 12..14
          return PluralForm.MANY;
        break;

      // set13
      case 0x6764: // gd
        if ((n == 2) || (n == 12)) // n in 2,12
          return PluralForm.TWO;
        if ((n == 1) || (n == 11)) // n in 1,11
          return PluralForm.ONE;
        if ((n >= 3 && n <= 10) || (n >= 13 && n <= 19)) // n in 3..10,13..19
          return PluralForm.FEW;
        break;

      // set14
      case 0x6776: // gv
        // FIXME according to http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html
        if ((((n % 10) >= 1 && (n % 10) <= 2)) || ((n % 20) == 0)) // n mod 10 in 1..2 or n mod 20 is 0
          return PluralForm.ONE;
        break;

      // set15
      case 0x6d6b: // mk
        if (((n % 10) == 1) && (n != 11)) // n mod 10 is 1 and n is not 11
          return PluralForm.ONE;
        break;

      // set16
      case 0x6d74: // mt
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if (((n % 100) >= 11 && (n % 100) <= 19)) // n mod 100 in 11..19
          return PluralForm.MANY;
        if ((n == 0) || (((n % 100) >= 2 && (n % 100) <= 10))) // n is 0 or n mod 100 in 2..10
          return PluralForm.FEW;
        break;

      // set17
      case 0x6d6f: // mo
      case 0x726f: // ro
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if ((n == 0) || ((n != 1) && (((n % 100) >= 1 && (n % 100) <= 19)))) // n is 0 OR n is not 1 AND n mod 100 in 1..19
          return PluralForm.FEW;
        break;

      // set18
      case 0x6761: // ga
        if (n == 2) // n is 2
          return PluralForm.TWO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if ((n >= 3 && n <= 6)) // n in 3..6
          return PluralForm.FEW;
        if ((n >= 7 && n <= 10)) // n in 7..10
          return PluralForm.MANY;
        break;

      // set19
      case 0x6666: // ff
      case 0x6672: // fr
      case 0x6b6162: // kab
        if (((n >= 0 && n <= 2)) && (n != 2)) // n within 0..2 and n is not 2
          return PluralForm.ONE;
        break;

      // set20
      case 0x6975: // iu
      case 0x6b77: // kw
      case 0x7365: // se
      case 0x6e6171: // naq
      case 0x736d61: // sma
      case 0x736d69: // smi
      case 0x736d6a: // smj
      case 0x736d6e: // smn
      case 0x736d73: // sms
        if (n == 2) // n is 2
          return PluralForm.TWO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        break;

      // set21
      case 0x616b: // ak
      case 0x616d: // am
      case 0x6268: // bh
      case 0x6869: // hi
      case 0x6c6e: // ln
      case 0x6d67: // mg
      case 0x7469: // ti
      case 0x746c: // tl
      case 0x7761: // wa
      case 0x66696c: // fil
      case 0x677577: // guw
      case 0x6e736f: // nso
        if ((n >= 0 && n <= 1)) // n in 0..1
          return PluralForm.ONE;
        break;

      // set22
      case 0x747a6d: // tzm
        if (((n >= 0 && n <= 1)) || ((n >= 11 && n <= 99))) // n in 0..1 or n in 11..99
          return PluralForm.ONE;
        break;

      // set23
      case 0x6166: // af
      case 0x6267: // bg
      case 0x626e: // bn
      case 0x6361: // ca
      case 0x6461: // da
      case 0x6465: // de
      case 0x6476: // dv
      case 0x6565: // ee
      case 0x656c: // el
      case 0x656e: // en
      case 0x656f: // eo
      case 0x6573: // es
      case 0x6574: // et
      case 0x6575: // eu
      case 0x6669: // fi
      case 0x666f: // fo
      case 0x6679: // fy
      case 0x676c: // gl
      case 0x6775: // gu
      case 0x6861: // ha
      case 0x6973: // is
      case 0x6974: // it
      case 0x6b6b: // kk
      case 0x6b6c: // kl
      case 0x6b73: // ks
      case 0x6b75: // ku
      case 0x6b79: // ky
      case 0x6c62: // lb
      case 0x6c67: // lg
      case 0x6d6c: // ml
      case 0x6d6e: // mn
      case 0x6d72: // mr
      case 0x6e62: // nb
      case 0x6e64: // nd
      case 0x6e65: // ne
      case 0x6e6c: // nl
      case 0x6e6e: // nn
      case 0x6e6f: // no
      case 0x6e72: // nr
      case 0x6e79: // ny
      case 0x6f6d: // om
      case 0x6f72: // or
      case 0x6f73: // os
      case 0x7061: // pa
      case 0x7073: // ps
      case 0x7074: // pt
      case 0x726d: // rm
      case 0x736e: // sn
      case 0x736f: // so
      case 0x7371: // sq
      case 0x7373: // ss
      case 0x7374: // st
      case 0x7376: // sv
      case 0x7377: // sw
      case 0x7461: // ta
      case 0x7465: // te
      case 0x746b: // tk
      case 0x746e: // tn
      case 0x7473: // ts
      case 0x7572: // ur
      case 0x7665: // ve
      case 0x766f: // vo
      case 0x7868: // xh
      case 0x7a75: // zu
      case 0x617361: // asa
      case 0x617374: // ast
      case 0x62656d: // bem
      case 0x62657a: // bez
      case 0x627278: // brx
      case 0x636767: // cgg
      case 0x636872: // chr
      case 0x636b62: // ckb
      case 0x667572: // fur
      case 0x677377: // gsw
      case 0x686177: // haw
      case 0x6a676f: // jgo
      case 0x6a6d63: // jmc
      case 0x6b616a: // kaj
      case 0x6b6367: // kcg
      case 0x6b6b6a: // kkj
      case 0x6b7362: // ksb
      case 0x6d6173: // mas
      case 0x6d676f: // mgo
      case 0x6e6168: // nah
      case 0x6e6e68: // nnh
      case 0x6e796e: // nyn
      case 0x706170: // pap
      case 0x726f66: // rof
      case 0x72776b: // rwk
      case 0x736171: // saq
      case 0x736568: // seh
      case 0x737379: // ssy
      case 0x737972: // syr
      case 0x74656f: // teo
      case 0x746967: // tig
      case 0x76756e: // vun
      case 0x776165: // wae
      case 0x786f67: // xog
        if (n == 1) // n is 1
          return PluralForm.ONE;
        break;

      // set24
      case 0x6172: // ar
        if (n == 2) // n is 2
          return PluralForm.TWO;
        if (n == 1) // n is 1
          return PluralForm.ONE;
        if (n == 0) // n is 0
          return PluralForm.ZERO;
        if (((n % 100) >= 3 && (n % 100) <= 10)) // n mod 100 in 3..10
          return PluralForm.FEW;
        if (((n % 100) >= 11 && (n % 100) <= 99)) // n mod 100 in 11..99
          return PluralForm.MANY;
        break;
    }

    return PluralForm.OTHER;
  }

  // Internal

  private static boolean languageSettingsLoaded;
  private static boolean languageAllowLowercase, languageRtl;
  private static Locale dateLocale;

  public static boolean allowLowercase () {
    if (!languageSettingsLoaded)
      checkLanguageSettings(false);
    return languageAllowLowercase;
  }

  public static boolean rtl () {
    if (!languageSettingsLoaded)
      checkLanguageSettings(false);
    return languageRtl;
  }

  public static int gravity () {
    return rtl() ? Gravity.RIGHT : Gravity.LEFT;
  }

  public static int alignParent () {
    return rtl() ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT;
  }

  public static int gravity (int gravity) {
    return gravity() | gravity;
  }

  private static void setLanguageAllowLowercase (boolean allowLowercase, boolean sendEvents) {
    if (Lang.languageAllowLowercase != allowLowercase) {
      Lang.languageAllowLowercase = allowLowercase;
      // TODO update affected strings?
    }
  }

  private static void setLanguageRtl (boolean isRtl, boolean sendEvents) {
    if (Lang.languageRtl != isRtl) {
      Lang.languageRtl = isRtl;
      if (sendEvents) {
        sendLanguageEvent(EVENT_DIRECTION_CHANGED, isRtl ? 1 : 0);
      }
    }
  }

  private static void setDateLocale (Locale locale, boolean sendEvents) {
    if ((Lang.dateLocale == null && locale != null) || (Lang.dateLocale != null && locale == null) || (locale != null && !locale.equals(Lang.dateLocale))) {
      Lang.dateLocale = locale;
      if (sendEvents) {
        sendLanguageEvent(EVENT_DATE_FORMAT_CHANGED, 0);
      }
    }
  }

  public static void checkLanguageSettings () {
    checkLanguageSettings(true);
  }

  private static void checkLanguageSettings (boolean sendEvents) {
    setLanguageAllowLowercase(!"1".equals(Lang.getString(R.string.language_disable_lowercase)), sendEvents);
    setLanguageRtl(Settings.instance().needRtl(packId(), getLanguageDirection() == LANGUAGE_DIRECTION_RTL), sendEvents);
    Locale dateLocale = null;
    String dateFormatLocale = Lang.getString(R.string.language_dateFormatLocale);
    if (!StringUtils.isEmpty(dateFormatLocale) && !"0".equals(dateFormatLocale)) {
      try {
        String language = Lang.cleanLanguageCode(dateFormatLocale);
        if (language.length() == dateFormatLocale.length()) {
          dateLocale = new Locale(language);
        } else {
          dateLocale = new Locale(language, Lang.cleanLanguageCode(dateFormatLocale.substring(language.length() + 1)));
        }
      } catch (Throwable ignored) { }
    }
    setDateLocale(dateLocale, sendEvents);
    languageSettingsLoaded = true;
  }

  public static final int LANGUAGE_DIRECTION_LTR = 0;
  public static final int LANGUAGE_DIRECTION_RTL = 1;
  public static final int LANGUAGE_DIRECTION_LTR_RTL = 2;

  public static int getLanguageDirection () {
    if (Config.RTL_BETA)
      return LANGUAGE_DIRECTION_LTR;
    if (Settings.instance().getLanguagePackInfo().isRtl)
      return LANGUAGE_DIRECTION_RTL;
    return getLanguageDirection(Lang.getString(R.string.language_rtl));
  }

  public static int getLanguageDirection (String directionValue) {
    if (Config.RTL_BETA) {
      return LANGUAGE_DIRECTION_LTR;
    }
    if (StringUtils.isEmpty(directionValue) || directionValue.length() != 1) {
      return LANGUAGE_DIRECTION_LTR;
    }
    switch (directionValue.charAt(0)) {
      case '1':
        return LANGUAGE_DIRECTION_RTL;
      case '2':
        return LANGUAGE_DIRECTION_LTR_RTL;
    }
    return LANGUAGE_DIRECTION_LTR;
  }

  public static int pluralCode () {
    return Settings.instance().getLanguagePluralCode();
  }

  private static boolean isSpanish () {
    return Lang.pluralCode() == 0x6573;
  }

  public static String packId () {
    return Settings.instance().getLanguagePackInfo().id;
  }

  public static String basePackId () {
    return Settings.instance().getLanguagePackInfo().baseLanguagePackId;
  }

  public static TdApi.LanguagePackInfo newLanguagePackInfo (String id, @Nullable String baseId, String pluralCode, boolean rtl) {
    return new TdApi.LanguagePackInfo(id, baseId, null, null, pluralCode, false, rtl, false, false, 0, 0, 0, null);
  }

  public static TdApi.LanguagePackInfo getBuiltinLanguage () {
    String id = getBuiltinLanguagePackId();
    String pluralCode = cleanLanguageCode(id);
    boolean rtl = getBuiltinLanguagePackRtl();
    return newLanguagePackInfo(id, null, pluralCode, rtl);
  }

  public static boolean isBuiltinLanguage (String languagePackId) {
    return getBuiltinLanguagePackId().equals(languagePackId);
  }

  public static String getBuiltinLanguagePackId () {
    return getBuiltinString(R.string.language_code);
  }

  public static String getLanguageEmoji () {
    String emoji = Emoji.extractSingleEmoji(getString(R.string.language_emoji));
    if (emoji != null)
      return emoji;
    return getBuiltinLanguageEmoji();
  }

  public static String getBuiltinLanguageEmoji () {
    String emoji = Emoji.extractSingleEmoji(getBuiltinString(R.string.language_emoji));
    if (emoji != null)
      return emoji;
    return "\uD83C\uDDE7"; // UK flag
  }

  private static boolean getBuiltinLanguagePackRtl () {
    return getLanguageDirection(Lang.getBuiltinString(R.string.language_rtl)) == Lang.LANGUAGE_DIRECTION_RTL;
  }

  public static TdApi.LanguagePackInfo getBuiltinSuggestedLanguage () {
    String id = getBuiltinSuggestedLanguagePackId();
    if (StringUtils.isEmpty(id) || id.equals(getBuiltinLanguagePackId())) {
      return getBuiltinLanguage();
    }
    String baseId = getBuiltinString(R.string.suggested_language_code_base);
    if (StringUtils.isEmpty(baseId))
      baseId = null;
    String pluralCode = getBuiltinString(R.string.suggested_language_code_plural);
    if (StringUtils.isEmpty(pluralCode))
      pluralCode = cleanLanguageCode(id);
    boolean rtl = getBuiltinSuggestedLanguagePackRtl();
    return newLanguagePackInfo(id, baseId, pluralCode, rtl);
  }

  public static String getBuiltinSuggestedLanguagePackId () {
    String languageCode = getBuiltinString(R.string.suggested_language_code);
    return StringUtils.isEmpty(languageCode) ? getBuiltinLanguagePackId() : languageCode;
  }

  public static boolean getBuiltinSuggestedLanguagePackRtl () {
    String languageDirection = getBuiltinString(R.string.suggested_language_rtl);
    return StringUtils.isEmpty(languageDirection) ? getBuiltinLanguagePackRtl() : getLanguageDirection(languageDirection) == Lang.LANGUAGE_DIRECTION_RTL;
  }

  public static Locale locale () {
    return Settings.instance().getLanguage().locale;
  }

  public static Locale dateFormatLocale () {
    if (!languageSettingsLoaded)
      checkLanguageSettings(false);
    Locale defaultLocale = Locale.getDefault();
    if (dateLocale != null && !dateLocale.getLanguage().equals(defaultLocale.getLanguage()))
      return dateLocale;
    return defaultLocale;
  }

  public static @Nullable TdApi.LanguagePackStringValueOrdinary getStringValue (String key, @NonNull TdApi.LanguagePackInfo language) {
    String cacheKey = null;
    if (packId().equals(language.id)) {
      cacheKey = makeStringCacheKey(language.id, key);
    }
    if (cacheKey != null) {
      TdApi.LanguagePackStringValue cachedString = getCachedString(cacheKey);
      if (cachedString instanceof TdApi.LanguagePackStringValueOrdinary)
        return (TdApi.LanguagePackStringValueOrdinary) cachedString;
      if (cachedString instanceof TdApi.LanguagePackStringValueDeleted)
        return null;
    }
    TdApi.LanguagePackStringValueOrdinary string = queryTdlibStringValue(key, language.id);
    if (string == null && !StringUtils.isEmpty(language.baseLanguagePackId))
      string = queryTdlibStringValue(key, language.baseLanguagePackId);
    if (cacheKey != null) {
      putCachedString(cacheKey, string);
    }
    return string;
  }

  public static @Nullable TdApi.LanguagePackStringValueOrdinary queryTdlibStringValue (String key, @Nullable String languagePackId) {
    return TdlibManager.instance().getStringValue(key, languagePackId != null ? languagePackId : packId());
  }

  public static String normalizeLanguageCode (String languagePackId) {
    if (languagePackId.startsWith("X")) {
      TdApi.LanguagePackStringValue value = TdlibManager.getString(TdlibManager.getLanguageDatabasePath(), Lang.INTERNAL_ID_KEY, languagePackId);
      if (value instanceof TdApi.LanguagePackStringValueOrdinary) {
        languagePackId = ((TdApi.LanguagePackStringValueOrdinary) value).value;
      }
    }
    return Lang.cleanLanguageCode(languagePackId);
  }

  public static @Nullable TdApi.LanguagePackStringValuePluralized getStringPluralized (String key, @NonNull TdApi.LanguagePackInfo language) {
    String cacheKey = null;
    if (packId().equals(language.id)) {
      cacheKey = makeStringCacheKey(language.id, key);
    }
    if (cacheKey != null) {
      TdApi.LanguagePackStringValue cachedString = getCachedString(cacheKey);
      if (cachedString instanceof TdApi.LanguagePackStringValuePluralized)
        return (TdApi.LanguagePackStringValuePluralized) cachedString;
      if (cachedString instanceof TdApi.LanguagePackStringValueDeleted)
        return null;
    }
    TdApi.LanguagePackStringValuePluralized string = queryTdlibStringPluralized(key, language.id);
    if (string == null && !StringUtils.isEmpty(language.baseLanguagePackId))
      string = queryTdlibStringPluralized(key, language.baseLanguagePackId);
    if (cacheKey != null) {
      putCachedString(cacheKey, string);
    }
    return string;
  }

  public static @Nullable TdApi.LanguagePackStringValuePluralized queryTdlibStringPluralized (String key, @Nullable String languagePackId) {
    return TdlibManager.instance().getStringPluralized(key, languagePackId != null ? languagePackId : packId());
  }

  @UiThread
  public static void changeLanguage (@NonNull TdApi.LanguagePackInfo languagePack) {
    Settings.instance().setLanguage(languagePack);
    boolean iterated = false;
    for (TdlibAccount account : TdlibManager.instance()) {
      if (account.hasTdlib(true)) {
        account.tdlib().setLanguage(languagePack);
        iterated = true;
      }
    }
    if (!iterated) {
      TdlibManager.instance().current().setLanguage(languagePack);
    }
    dispatchLanguagePackChanged();
  }

  @UiThread
  public static void updateLanguagePack (TdApi.UpdateLanguagePackStrings update) {
    String languagePackId = packId();
    boolean isBase = false;
    boolean updated = update.languagePackId.equals(languagePackId);
    if (!updated && update.languagePackId.equals(basePackId())) {
      updated = true;
      isBase = true;
    }
    if (updated) {
      Log.i("received updateLanguagePack, stringCount:%d", update.strings != null ? update.strings.length : 0);
      if (update.strings == null || update.strings.length == 0 || update.strings.length > 25) {
        dispatchLanguagePackChanged();
      } else {
        dispatchLanguagePackStringChanged(update.languagePackId, update.strings, isBase ? languagePackId : null);
      }
    }
  }

  public static void checkLanguageCode () {
    boolean changedLanguageCode = false;
    TdApi.LanguagePackInfo languagePackInfo = Settings.instance().getLanguagePackInfo();
    for (TdlibAccount tdlib : TdlibManager.instance()) {
      if (tdlib.hasTdlib(true) && tdlib.tdlib().setLanguage(languagePackInfo)) {
        changedLanguageCode = true;
      }
    }
    if (changedLanguageCode) {
      dispatchLanguagePackChanged();
    }
  }

  @UiThread
  private static void dispatchLanguagePackChanged () {
    boolean wasRtl = languageRtl;
    Lang.clearCachedStrings();
    checkLanguageSettings(false);
    sendLanguageEvent(EVENT_PACK_CHANGED, languageRtl != wasRtl ? 1 : 0);
  }

  @UiThread
  private static void dispatchLanguagePackStringChanged (String languageCode, TdApi.LanguagePackString[] strings, String actualLanguagePackId) {
    Lang.putCachedStrings(actualLanguagePackId != null ? actualLanguagePackId : languageCode, strings);
    checkLanguageSettings(true);
    if (hasLanguageListeners()) {
      for (TdApi.LanguagePackString string : strings) {
        int resId = getStringResourceIdentifier(string.key);
        if (resId != 0) {
          sendLanguageEvent(EVENT_STRING_CHANGED, resId);
        }
      }
    } else {
      sendLanguageEvent(EVENT_STRING_CHANGED, 0);
    }
  }

  /**
   * Sent when all string displayed on the screen should be immediately reloaded.
   * */
  public static final int EVENT_PACK_CHANGED = 0;

  /**
   * Sent when a layout direction has changed.
   * This event is sent before {@link #EVENT_PACK_CHANGED},
   * when this happened due to the language pack change.
   *
   * arg1 == 1, if current language is RTL
   */
  public static final int EVENT_DIRECTION_CHANGED = 1;

  /**
   * Sent when a specific string should be immediately reloaded
   *
   * arg1 == stringRes of the changed string
   */
  public static final int EVENT_STRING_CHANGED = 2;

  /**
   * Sent when all date format strings should be invalidated
   */
  public static final int EVENT_DATE_FORMAT_CHANGED = 3;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({EVENT_PACK_CHANGED, EVENT_DIRECTION_CHANGED, EVENT_STRING_CHANGED, EVENT_DATE_FORMAT_CHANGED})
  public @interface EventType { }

  public interface Listener {
    void onLanguagePackEvent (@EventType int event, int arg1);
  }

  private static ReferenceList<Listener> languageListeners;

  public static void addLanguageListener (Listener listener) {
    if (languageListeners == null) {
      synchronized (Lang.class) {
        if (languageListeners == null)
          languageListeners = new ReferenceList<>(true);
      }
    }
    languageListeners.add(listener);
  }

  public static void removeLanguageListener (Listener listener) {
    if (languageListeners == null) {
      synchronized (Lang.class) {
        if (languageListeners == null)
          return;
      }
    }
    languageListeners.remove(listener);
  }

  private static void sendLanguageEvent (@EventType int eventType, int arg1) {
    TGMessage.processLanguageEvent(eventType, arg1);
    if (languageListeners == null) {
      synchronized (Lang.class) {
        if (languageListeners == null) {
          return;
        }
      }
    }
    for (Listener listener : languageListeners) {
      listener.onLanguagePackEvent(eventType, arg1);
    }
  }

  private static boolean hasLanguageListeners () {
    if (languageListeners == null) {
      synchronized (Lang.class) {
        if (languageListeners == null) {
          return false;
        }
      }
    }
    return !languageListeners.isEmpty();
  }

  public static boolean hasDirectionChanged (@EventType int event, int arg1) {
    return (event == Lang.EVENT_PACK_CHANGED && arg1 == 1) || event == Lang.EVENT_DIRECTION_CHANGED;
  }

  public static boolean fixLanguageCode (String languageCode, TdApi.LanguagePackInfo languageInfo) {
    switch (languageCode) {
      case "en": languageInfo.name = "English"; languageInfo.nativeName = "English"; return true;
      case "en-GB": languageInfo.name = "English (UK)"; languageInfo.nativeName = "English (UK)"; return true;
      case "en-US": languageInfo.name = "English (US)"; languageInfo.nativeName = "English (US)"; return true;

      case "es": case "es-LA": languageInfo.name = "Spanish"; languageInfo.nativeName = "Español"; return true;
      case "es-ES": languageInfo.name = "Spanish (Spain)"; languageInfo.nativeName = "Español (España)"; return true;

      case "et": case "et-EE": languageInfo.name = "Estonian"; languageInfo.nativeName = "Eesti"; return true;
      case "eu": case "eu-ES": languageInfo.name = "Basque"; languageInfo.nativeName = "Euskara"; return true;
      case "fa": case "fa-IR": languageInfo.name = "Persian"; languageInfo.nativeName = "فارسی"; return true;
      case "ff": case "ff-NG": languageInfo.name = "Fula"; languageInfo.nativeName = "Fula"; return true;
      case "fi": case "fi-FI": languageInfo.name = "Finnish"; languageInfo.nativeName = "Suomi"; return true;
      case "fo": case "fo-FO": languageInfo.name = "Faroese"; languageInfo.nativeName = "Føroyskt"; return true;

      case "fr": languageInfo.name = "French"; languageInfo.nativeName = "Français"; return true;
      case "fr-CA": languageInfo.name = "French (Canada)"; languageInfo.nativeName = "Français (Canada)"; return true;
      case "fr-FR": languageInfo.name = "French (France)"; languageInfo.nativeName = "Français (France)"; return true;

      case "ja": case "ja-JP": languageInfo.name = "Japanese"; languageInfo.nativeName = "日本語"; return true;
      case "ja-KS": languageInfo.name = "Japanese (Kansai)"; languageInfo.nativeName = "日本語(関西)"; return true;

      case "nl": case "nl-NL": languageInfo.name = "Dutch"; languageInfo.nativeName = "Nederlands"; return true;
      case "nl-BE": languageInfo.name = "Dutch (België)"; languageInfo.nativeName = "Nederlands (België)"; return true;

      case "pt": languageInfo.name = "Portuguese"; languageInfo.nativeName = "Português"; return true;
      case "pt-BR": languageInfo.name = "Portuguese (Brazil)"; languageInfo.nativeName = "Português (Brasil)"; return true;
      case "pt-PT": languageInfo.name = "Portuguese (Portugal)"; languageInfo.nativeName = "Português (Portugal)"; return true;

      case "zh":  languageInfo.name = "Simplified Chinese"; languageInfo.nativeName = "中文"; return true;
      case "zh-CN": languageInfo.name = "Simplified Chinese (China)"; languageInfo.nativeName = "中文(简体)"; return true;
      case "zh-HK": languageInfo.name = "Traditional Chinese (Hong Kong)"; languageInfo.nativeName = "中文(香港)"; return true;
      case "zh-TW": languageInfo.name = "Traditional Chinese (Taiwan)"; languageInfo.nativeName = "中文(台灣)"; return true;

      case "af": case "af-ZA": languageInfo.name = "Afrikaans"; languageInfo.nativeName = "Afrikaans"; return true;
      case "am": case "am-ET": languageInfo.name = "Amharic"; languageInfo.nativeName = "አማርኛ"; return true;
      case "ar": case "ar-AR": languageInfo.name = "Arabic"; languageInfo.nativeName = "العربية"; return true;
      case "as": case "as-IN": languageInfo.name = "Assamese"; languageInfo.nativeName = "অসমীয়া"; return true;
      case "az": case "az-AZ": languageInfo.name = "Azerbaijani"; languageInfo.nativeName = "Azərbaycan dili"; return true;
      case "be": case "be-BY": languageInfo.name = "Belarusian"; languageInfo.nativeName = "Беларуская"; return true;
      case "bg": case "bg-BG": languageInfo.name = "Bulgarian"; languageInfo.nativeName = "Български"; return true;
      case "bn": case "bn-IN": languageInfo.name = "Bengali"; languageInfo.nativeName = "বাংলা"; return true;
      case "br": case "br-FR": languageInfo.name = "Breton"; languageInfo.nativeName = "Brezhoneg"; return true;
      case "bs": case "bs-BA": languageInfo.name = "Bosnian"; languageInfo.nativeName = "Bosanski"; return true;
      case "ca": case "ca-ES": languageInfo.name = "Catalan"; languageInfo.nativeName = "Català"; return true;
      case "cb": case "cb-IQ": languageInfo.name = "Sorani Kurdish"; languageInfo.nativeName = "کوردیی ناوەندی"; return true;
      case "co": case "co-FR": languageInfo.name = "Corsican"; languageInfo.nativeName = "Corsu"; return true;
      case "cs": case "cs-CZ": languageInfo.name = "Czech"; languageInfo.nativeName = "Čeština"; return true;
      case "cx": case "cx-PH": languageInfo.name = "Cebuano"; languageInfo.nativeName = "Bisaya"; return true;
      case "cy": case "cy-GB": languageInfo.name = "Welsh"; languageInfo.nativeName = "Cymraeg"; return true;
      case "da": case "da-DK": languageInfo.name = "Danish"; languageInfo.nativeName = "Dansk"; return true;
      case "de": case "de-DE": languageInfo.name = "German"; languageInfo.nativeName = "Deutsch"; return true;
      case "el": case "el-GR": languageInfo.name = "Greek"; languageInfo.nativeName = "Ελληνικά"; return true;
      case "fy": case "fy-NL": languageInfo.name = "Frisian"; languageInfo.nativeName = "Frysk"; return true;
      case "ga": case "ga-IE": languageInfo.name = "Irish"; languageInfo.nativeName = "Gaeilge"; return true;
      case "gl": case "gl-ES": languageInfo.name = "Galician"; languageInfo.nativeName = "Galego"; return true;
      case "gn": case "gn-PY": languageInfo.name = "Guarani"; languageInfo.nativeName = "Guarani"; return true;
      case "gu": case "gu-IN": languageInfo.name = "Gujarati"; languageInfo.nativeName = "ગુજરાતી"; return true;
      case "ha": case "ha-NG": languageInfo.name = "Hausa"; languageInfo.nativeName = "Hausa"; return true;
      case "he": case "he-IL": languageInfo.name = "Hebrew"; languageInfo.nativeName = "עברית"; return true;
      case "hi": case "hi-IN": languageInfo.name = "Hindi"; languageInfo.nativeName = "हिन्दी"; return true;
      case "hr": case "hr-HR": languageInfo.name = "Croatian"; languageInfo.nativeName = "Hrvatski"; return true;
      case "ht": case "ht-HT": languageInfo.name = "Haitian Creole"; languageInfo.nativeName = "Kreyòl Ayisyen"; return true;
      case "hu": case "hu-HU": languageInfo.name = "Hungarian"; languageInfo.nativeName = "Magyar"; return true;
      case "hy": case "hy-AM": languageInfo.name = "Armenian"; languageInfo.nativeName = "Հայերեն"; return true;
      case "id": case "id-ID": languageInfo.name = "Indonesian"; languageInfo.nativeName = "Bahasa Indonesia"; return true;
      case "is": case "is-IS": languageInfo.name = "Icelandic"; languageInfo.nativeName = "Íslenska"; return true;
      case "it": case "it-IT": languageInfo.name = "Italian"; languageInfo.nativeName = "Italiano"; return true;
      case "jv": case "jv-ID": languageInfo.name = "Javanese"; languageInfo.nativeName = "Basa Jawa"; return true;
      case "ka": case "ka-GE": languageInfo.name = "Georgian"; languageInfo.nativeName = "ქართული"; return true;
      case "kk": case "kk-KZ": languageInfo.name = "Kazakh"; languageInfo.nativeName = "Қазақша"; return true;
      case "km": case "km-KH": languageInfo.name = "Khmer"; languageInfo.nativeName = "ភាសាខ្មែរ"; return true;
      case "kn": case "kn-IN": languageInfo.name = "Kannada"; languageInfo.nativeName = "ಕನ್ನಡ"; return true;
      case "ko": case "ko-KR": languageInfo.name = "Korean"; languageInfo.nativeName = "한국어"; return true;
      case "ku": case "ku-TR": languageInfo.name = "Kurdish (Kurmanji)"; languageInfo.nativeName = "Kurdî (Kurmancî)"; return true;
      case "ky": case "ky-KG": languageInfo.name = "Kyrgyz"; languageInfo.nativeName = "кыргызча"; return true;
      case "lo": case "lo-LA": languageInfo.name = "Lao"; languageInfo.nativeName = "ພາສາລາວ"; return true;
      case "lt": case "lt-LT": languageInfo.name = "Lithuanian"; languageInfo.nativeName = "Lietuvių"; return true;
      case "lv": case "lv-LV": languageInfo.name = "Latvian"; languageInfo.nativeName = "Latviešu"; return true;
      case "mg": case "mg-MG": languageInfo.name = "Malagasy"; languageInfo.nativeName = "Malagasy"; return true;
      case "mk": case "mk-MK": languageInfo.name = "Macedonian"; languageInfo.nativeName = "Македонски"; return true;
      case "ml": case "ml-IN": languageInfo.name = "Malayalam"; languageInfo.nativeName = "മലയാളം"; return true;
      case "mn": case "mn-MN": languageInfo.name = "Mongolian"; languageInfo.nativeName = "Монгол"; return true;
      case "mr": case "mr-IN": languageInfo.name = "Marathi"; languageInfo.nativeName = "मराठी"; return true;
      case "ms": case "ms-MY": languageInfo.name = "Malay"; languageInfo.nativeName = "Bahasa Melayu"; return true;
      case "mt": case "mt-MT": languageInfo.name = "Maltese"; languageInfo.nativeName = "Malti"; return true;
      case "my": case "my-MM": languageInfo.name = "Burmese"; languageInfo.nativeName = "မြန်မာဘာသာ"; return true;
      case "nb": case "nb-NO": languageInfo.name = "Norwegian (bokmal)"; languageInfo.nativeName = "Norsk (bokmål)"; return true;
      case "ne": case "ne-NP": languageInfo.name = "Nepali"; languageInfo.nativeName = "नेपाली"; return true;
      case "nn": case "nn-NO": languageInfo.name = "Norwegian (nynorsk)"; languageInfo.nativeName = "Norsk (nynorsk)"; return true;
      case "or": case "or-IN": languageInfo.name = "Oriya"; languageInfo.nativeName = "ଓଡ଼ିଆ"; return true;
      case "pa": case "pa-IN": languageInfo.name = "Punjabi"; languageInfo.nativeName = "ਪੰਜਾਬੀ"; return true;
      case "pl": case "pl-PL": languageInfo.name = "Polish"; languageInfo.nativeName = "Polski"; return true;
      case "ps": case "ps-AF": languageInfo.name = "Pashto"; languageInfo.nativeName = "پښتو"; return true;
      case "ro": case "ro-RO": languageInfo.name = "Romanian"; languageInfo.nativeName = "Română"; return true;
      case "ru": case "ru-RU": languageInfo.name = "Russian"; languageInfo.nativeName = "Русский"; return true;
      case "rw": case "rw-RW": languageInfo.name = "Kinyarwanda"; languageInfo.nativeName = "Ikinyarwanda"; return true;
      case "sc": case "sc-IT": languageInfo.name = "Sardinian"; languageInfo.nativeName = "Sardu"; return true;
      case "si": case "si-LK": languageInfo.name = "Sinhala"; languageInfo.nativeName = "සිංහල"; return true;
      case "sk": case "sk-SK": languageInfo.name = "Slovak"; languageInfo.nativeName = "Slovenčina"; return true;
      case "sl": case "sl-SI": languageInfo.name = "Slovenian"; languageInfo.nativeName = "Slovenščina"; return true;
      case "sn": case "sn-ZW": languageInfo.name = "Shona"; languageInfo.nativeName = "Shona"; return true;
      case "so": case "so-SO": languageInfo.name = "Somali"; languageInfo.nativeName = "Af-Soomaali"; return true;
      case "sq": case "sq-AL": languageInfo.name = "Albanian"; languageInfo.nativeName = "Shqip"; return true;
      case "sr": case "sr-RS": languageInfo.name = "Serbian"; languageInfo.nativeName = "Српски"; return true;
      case "sv": case "sv-SE": languageInfo.name = "Swedish"; languageInfo.nativeName = "Svenska"; return true;
      case "sw": case "sw-KE": languageInfo.name = "Swahili"; languageInfo.nativeName = "Kiswahili"; return true;
      case "sz": case "sz-PL": languageInfo.name = "Silesian"; languageInfo.nativeName = "ślōnskŏ gŏdka"; return true;
      case "ta": case "ta-IN": languageInfo.name = "Tamil"; languageInfo.nativeName = "தமிழ்"; return true;
      case "te": case "te-IN": languageInfo.name = "Telugu"; languageInfo.nativeName = "తెలుగు"; return true;
      case "tg": case "tg-TJ": languageInfo.name = "Tajik"; languageInfo.nativeName = "Тоҷикӣ"; return true;
      case "th": case "th-TH": languageInfo.name = "Thai"; languageInfo.nativeName = "ภาษาไทย"; return true;
      case "tl": case "tl-PH": languageInfo.name = "Filipino"; languageInfo.nativeName = "Filipino"; return true;
      case "tr": case "tr-TR": languageInfo.name = "Turkish"; languageInfo.nativeName = "Türkçe"; return true;
      case "tz": case "tz-MA": languageInfo.name = "Tamazight"; languageInfo.nativeName = "ⵜⴰⵎⴰⵣⵉⵖⵜ"; return true;
      case "uk": case "uk-UA": languageInfo.name = "Ukrainian"; languageInfo.nativeName = "Українська"; return true;
      case "ur": case "ur-PK": languageInfo.name = "Urdu"; languageInfo.nativeName = "اردو"; return true;
      case "uz": case "uz-UZ": languageInfo.name = "Uzbek"; languageInfo.nativeName = "O'zbek"; return true;
      case "vi": case "vi-VN": languageInfo.name = "Vietnamese"; languageInfo.nativeName = "Tiếng Việt"; return true;
      case "zz": case "zz-TR": languageInfo.name = "Zaza"; languageInfo.nativeName = "Zaza"; return true;
    }
    return false;
  }

  // Strings Cache

  private static Map<String, TdApi.LanguagePackStringValue> cachedStrings;

  private static Map<String, TdApi.LanguagePackStringValue> cachedStrings () {
    if (cachedStrings == null) {
      synchronized (Lang.class) {
        if (cachedStrings == null) {
          cachedStrings = new ConcurrentHashMap<>();
        }
      }
    }
    return cachedStrings;
  }

  public static @Nullable TdApi.LanguagePackStringValue getCachedString (String cacheKey) {
    return cachedStrings().get(cacheKey);
  }

  private static TdApi.LanguagePackStringValueDeleted STRING_DELETED;
  public static TdApi.LanguagePackStringValueDeleted STRING_DELETED () {
    if (STRING_DELETED == null) {
      synchronized (Lang.class) {
        if (STRING_DELETED == null) {
          STRING_DELETED = new TdApi.LanguagePackStringValueDeleted();
        }
      }
    }
    return STRING_DELETED;
  }

  public static void putCachedString (String cacheKey, @Nullable TdApi.LanguagePackStringValue string) {
    if (string == null) {
      string = STRING_DELETED();
    }
    cachedStrings().put(cacheKey, string);
  }

  public static void putCachedStrings (String languageCode, TdApi.LanguagePackString[] strings) {
    if (!cachedStrings().isEmpty()) {
      for (TdApi.LanguagePackString string : strings) {
        String cacheKey = makeStringCacheKey(languageCode, string.key);
        if (cachedStrings.containsKey(cacheKey)) {
          if (string.value.getConstructor() == TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR) {
            cachedStrings.put(cacheKey, STRING_DELETED());
          } else {
            cachedStrings.put(cacheKey, string.value);
          }
        }
      }
    }
  }

  public static void clearCachedStrings () {
    cachedStrings().clear();
  }

  public static String makeStringCacheKey (String languageCode, String key) {
    return languageCode + "|" + key;
  }

  public static String getDebugString (String text, boolean isDebug) {
    if (isDebug)
      return "[DEBUG] " + text;
    return text;
  }
}
