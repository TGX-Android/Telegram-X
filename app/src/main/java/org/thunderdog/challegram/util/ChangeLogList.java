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
 * File created on 10/05/2024
 */
package org.thunderdog.challegram.util;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tgx.td.Td;

public class ChangeLogList {
  public static final class Release {
    public final int primary, major, minor, buildNo;
    public final String changeLogUrl;

    public Release (int primary, int major, int minor, int buildNo, String changeLogUrl) {
      this.primary = primary;
      this.major = major;
      this.minor = minor;
      this.buildNo = buildNo;
      this.changeLogUrl = changeLogUrl;
    }
  }

  private static boolean checkVersion (int version, int checkVersion, boolean isTest) {
    return version < checkVersion && (checkVersion <= BuildConfig.ORIGINAL_VERSION_CODE || isTest || BuildConfig.DEBUG) && checkVersion < Integer.MAX_VALUE;
  }

  private static TdApi.FormattedText makeUpdateText (String version, String changeLog) {
    String text = Lang.getStringSecure(R.string.ChangeLogText, version, changeLog);
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text, null);
    //noinspection UnsafeOptInUsageError
    Td.parseMarkdown(formattedText);
    return formattedText;
  }

  private static void makeUpdateText (Release release, List<TdApi.Function<?>> functions, List<TdApi.InputMessageContent> messages, boolean isLast) {
    TdApi.FormattedText text = makeUpdateText(String.format(Locale.US, "%d.%d.%d.%d", release.primary, release.major, release.minor, release.buildNo), release.changeLogUrl);
    functions.add(new TdApi.GetLinkPreview(text, new TdApi.LinkPreviewOptions(false, release.changeLogUrl, false, false, false)));
    functions.add(new TdApi.GetWebPageInstantView(release.changeLogUrl, false));
    messages.add(new TdApi.InputMessageText(text, null, false));
  }

  public static void collectChangeLogs (int prevVersion, List<TdApi.Function<?>> functions, List<TdApi.InputMessageContent> updates, boolean test) {
    List<Release> releases = new ArrayList<>();
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_MARCH, test)) {
      releases.add(new Release(0, 20, 6, APP_RELEASE_VERSION_2018_MARCH, "http://telegra.ph/Telegram-X-03-26"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_JULY, test)) {
      releases.add(new Release(0, 20, 10, APP_RELEASE_VERSION_2018_JULY, "http://telegra.ph/Telegram-X-07-27"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_OCTOBER, test)) {
      releases.add(new Release(0, 21, 1, APP_RELEASE_VERSION_2018_OCTOBER,  "https://telegra.ph/Telegram-X-10-14"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_OCTOBER_2, test)) {
      // makeUpdateText("0.21.1." + APP_RELEASE_VERSION_OCTOBER_2,  "https://t.me/tgx_android/129", functions, updates);
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2019_APRIL, test)) {
      releases.add(new Release(0, 21, 7, APP_RELEASE_VERSION_2019_APRIL, "https://telegra.ph/Telegram-X-04-25"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2019_SEPTEMBER, test)) {
      releases.add(new Release(0, 21, 7, APP_RELEASE_VERSION_2019_SEPTEMBER, "https://telegra.ph/Telegram-X-09-21"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_JANUARY, test)) {
      releases.add(new Release(0, 22, 4, APP_RELEASE_VERSION_2020_JANUARY, "https://telegra.ph/Telegram-X-01-23-2"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_FEBRUARY, test)) {
      releases.add(new Release(0, 22, 5, APP_RELEASE_VERSION_2020_FEBRUARY, "https://telegra.ph/Telegram-X-02-29"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_SPRING, test)) {
      releases.add(new Release(0, 22, 8, APP_RELEASE_VERSION_2020_SPRING, "https://telegra.ph/Telegram-X-04-23"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2021_NOVEMBER, test)) {
      releases.add(new Release(0, 24, 2, APP_RELEASE_VERSION_2021_NOVEMBER, "https://telegra.ph/Telegram-X-11-08"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2022_JUNE, test)) {
      releases.add(new Release(0, 24, 9, APP_RELEASE_VERSION_2022_JUNE, "https://telegra.ph/Telegram-X-06-11"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2022_OCTOBER, test)) {
      releases.add(new Release(0, 25, 1, APP_RELEASE_VERSION_2022_OCTOBER, "https://telegra.ph/Telegram-X-10-06"));
    }
    boolean haveMarch2023ChangeLog = false;
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_MARCH, test)) {
      releases.add(new Release(0, 25, 6, APP_RELEASE_VERSION_2023_MARCH, "https://telegra.ph/Telegram-X-03-08"));
      haveMarch2023ChangeLog = true;
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_MARCH_2, test) && !haveMarch2023ChangeLog) {
      releases.add(new Release(0, 25, 6, APP_RELEASE_VERSION_2023_MARCH_2, "https://t.me/tgx_android/305"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_APRIL, test)) {
      releases.add(new Release(0, 25, 6, APP_RELEASE_VERSION_2023_APRIL, "https://telegra.ph/Telegram-X-04-02"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_AUGUST, test)) {
      releases.add(new Release(0, 25, 10, APP_RELEASE_VERSION_2023_AUGUST, "https://telegra.ph/Telegram-X-08-02"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_DECEMBER, test)) {
      releases.add(new Release(0, 26, 3, APP_RELEASE_VERSION_2023_DECEMBER, "https://telegra.ph/Telegram-X-2023-12-31"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2024_MAY, test)) {
      releases.add(new Release(0, 26, 8, APP_RELEASE_VERSION_2024_MAY, "https://telegra.ph/Telegram-X-05-10"));
    }
    if (checkVersion(prevVersion, APP_RELEASE_VERSION_2024_JUNE, test)) {
      releases.add(new Release(0, 26, 9, APP_RELEASE_VERSION_2024_JUNE, "https://telegra.ph/Telegram-X-06-08"));
    }
    for (int i = 0; i < releases.size(); i++) {
      Release release = releases.get(i);
      makeUpdateText(release, functions, updates, i + 1 == releases.size());
    }
  }

  private static final int APP_RELEASE_VERSION_2018_MARCH = 906; // 31st March, 2018: http://telegra.ph/Telegram-X-03-26
  private static final int APP_RELEASE_VERSION_2018_JULY = 967; // 27th July, 2018: http://telegra.ph/Telegram-X-07-27
  private static final int APP_RELEASE_VERSION_2018_OCTOBER = 1005; // 15th October, 2018: https://telegra.ph/Telegram-X-10-14
  private static final int APP_RELEASE_VERSION_2018_OCTOBER_2 = 1010; // 21th October, 2018: https://t.me/tgx_android/129

  private static final int APP_RELEASE_VERSION_2019_APRIL = 1149; // 26th April, 2019: https://telegra.ph/Telegram-X-04-25
  private static final int APP_RELEASE_VERSION_2019_SEPTEMBER = 1205; // 21st September, 2019: https://telegra.ph/Telegram-X-09-21

  private static final int APP_RELEASE_VERSION_2020_JANUARY = 1270; // 23 January, 2020: https://telegra.ph/Telegram-X-01-23-2
  private static final int APP_RELEASE_VERSION_2020_FEBRUARY = 1302; // 3 March, 2020: https://telegra.ph/Telegram-X-02-29 // 6th, Actually. Production version is 1305
  private static final int APP_RELEASE_VERSION_2020_SPRING = 1361; // 15 May, 2020: https://telegra.ph/Telegram-X-04-23

  private static final int APP_RELEASE_VERSION_2021_NOVEMBER = 1470; // 12 November, 2021: https://telegra.ph/Telegram-X-11-08

  private static final int APP_RELEASE_VERSION_2022_JUNE = 1530; // Going open source. 16 June, 2022: https://telegra.ph/Telegram-X-06-11

  private static final int APP_RELEASE_VERSION_2022_OCTOBER = 1560; // Reactions. 7 October, 2022: https://telegra.ph/Telegram-X-10-06

  private static final int APP_RELEASE_VERSION_2023_MARCH = 1605; // Dozens of stuff. 8 March, 2023: https://telegra.ph/Telegram-X-03-08
  private static final int APP_RELEASE_VERSION_2023_MARCH_2 = 1615; // Bugfixes to the previous release. 15 March, 2023: https://t.me/tgx_android/305
  private static final int APP_RELEASE_VERSION_2023_APRIL = 1624; // Emoji 15.0, more recent stickers & more + critical TDLIb upgrade. 2 April, 2023: https://telegra.ph/Telegram-X-04-02
  private static final int APP_RELEASE_VERSION_2023_AUGUST = 1646; // Translation, Advanced Text Formatting, Emoji Status, tgcalls, reproducible TDLib & more. 3 August, 2023: https://telegra.ph/Telegram-X-08-02
  private static final int APP_RELEASE_VERSION_2023_DECEMBER = 1674; // Custom emoji, select link preview, archive settings, in-app avatar picker, group chat tools, & more. 31st December, 2023 (full roll-out in January 2024): https://telegra.ph/Telegram-X-2023-12-31
  private static final int APP_RELEASE_VERSION_2024_MAY = 1717; // Replace media messages, files captions, birthdates, dozens of changes. 10 May, 2024: https://telegra.ph/Telegram-X-05-10
  private static final int APP_RELEASE_VERSION_2024_JUNE = 1730; // Chat folders. 10 June, 2024: https://telegra.ph/Telegram-X-06-08
  private static final int APP_RELEASE_VERSION_2025_JUNE = 1752; // https://github.com/TGX-Android/Telegram-X/releases/tag/v0.27.10.1752
  private static final int APP_RELEASE_VERSION_2025_OCTOBER = 1771; // https://github.com/TGX-Android/Telegram-X/releases/tag/v0.28.1.1771
}
