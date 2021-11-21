package org.thunderdog.challegram.data;

import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.preview.PreviewLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.List;
import java.util.regex.Pattern;

import me.vkryl.core.StringUtils;

/**
 * Date: 2019-09-20
 * Author: default
 */
public class EmbeddedService {
  public static final int TYPE_UNKNOWN = 0;
  public static final int TYPE_YOUTUBE = 1;
  public static final int TYPE_VIMEO = 2;
  public static final int TYPE_DAILYMOTION = 3;
  public static final int TYPE_COUB = 4;
  public static final int TYPE_SOUNDCLOUD = 5;
  public static final int TYPE_CUSTOM_EMBED = 99;

  public final int type;
  public final String viewUrl, embedUrl, embedType;

  public final int width, height;
  public final TdApi.Photo thumbnail;

  public EmbeddedService (int type, String url, int width, int height, TdApi.Photo thumbnail, @Nullable String embedUrl, @Nullable String embedType) {
    this.type = type;
    this.viewUrl = url;
    this.width = width;
    this.height = height;
    this.thumbnail = thumbnail;
    this.embedUrl = embedUrl;
    this.embedType = embedType;
  }

  public @DrawableRes
  int getIcon () {
    switch (type) {
      case TYPE_YOUTUBE:
        return R.drawable.logo_youtube;
      case TYPE_VIMEO:
        return R.drawable.logo_vimeo;
      case TYPE_DAILYMOTION:
        return R.drawable.logo_dailymotion;
    }
    return 0;
  }

  public float getPaddingHorizontal () {
    switch (type) {
      case TYPE_YOUTUBE:
        return 1f;
      case TYPE_VIMEO:
        return 5f;
      case TYPE_DAILYMOTION:
        return 4f;
    }
    return 0f;
  }

  public float getPaddingTop () {
    switch (type) {
      case TYPE_YOUTUBE:
        return 1f;
      case TYPE_VIMEO:
        return 5f;
      case TYPE_DAILYMOTION:
        return 4f;
    }
    return 0f;
  }

  public float getPaddingBottom () {
    switch (type) {
      case TYPE_YOUTUBE:
        return 1f;
      case TYPE_VIMEO:
        return 6f;
      case TYPE_DAILYMOTION:
        return 4f;
    }
    return 0f;
  }

  public void open (BaseActivity context) {
    ViewController<?> c = context.navigation().getCurrentStackItem();
    boolean needConfirmation = c instanceof MessagesController && ((MessagesController) c).isSecretChat();
    if (c == null || !PreviewLayout.show(c, this, needConfirmation)) {
      UI.openUrl(viewUrl);
    }
  }

  // static

  private static final Pattern YOUTUBE_PATTERN = Pattern.compile("^(?:.+\\.)?(?:youtube\\.com|youtu\\.be)$");
  private static final Pattern VIMEO_PATTERN = Pattern.compile("^(?:.+\\.)?(?:vimeo\\.com)$");
  private static final Pattern DAILYMOTION_PATTERN = Pattern.compile("^(?:.+\\.)?(?:dailymotion\\.com)$");
  private static final Pattern COUB_PATTERN = Pattern.compile("^(?:.+\\.)?(?:coub\\.com)$");
  private static final Pattern SOUNDCLOUD_PATTERN = Pattern.compile("^(?:.+\\.)?(?:soundcloud\\.com)$");

  private static int resolveTypeForHost (String host) {
    if (!StringUtils.isEmpty(host)) {
      if (host.startsWith("www.")) {
        host = host.substring("www".length());
      } else if (host.startsWith("m.")) {
        host = host.substring("m.".length());
      }
      int viewType = TYPE_UNKNOWN;
      Pattern[] patterns = new Pattern[] {YOUTUBE_PATTERN, VIMEO_PATTERN, DAILYMOTION_PATTERN, COUB_PATTERN, SOUNDCLOUD_PATTERN};
      for (Pattern pattern : patterns) {
        viewType++;
        if (pattern.matcher(host).matches())
          return viewType;
      }
    }
    return TYPE_UNKNOWN;
  }

  public static EmbeddedService parse (TdApi.WebPage webPage) {
    EmbeddedService service = parse(webPage.url, webPage.embedWidth, webPage.embedHeight, webPage.photo, webPage.embedUrl);
    if (service != null)
      return service;
    if ("iframe".equals(webPage.embedType) && !StringUtils.isEmpty(webPage.embedUrl)) {
      if ((
           ("gif".equals(webPage.type) && webPage.animation != null) ||
           ("video".equals(webPage.type) && webPage.photo != null && webPage.animation == null) ||
           ("photo".equals(webPage.type) && webPage.photo != null && webPage.animation == null)
          ) && webPage.video == null && webPage.videoNote == null && webPage.document == null && webPage.audio == null) {
        return new EmbeddedService(resolveTypeForHost(StringUtils.domainOf(webPage.url)), webPage.url, webPage.embedWidth, webPage.embedHeight, webPage.photo, webPage.embedUrl, webPage.embedType);
      }
    }
    // if ("type".equals(webPage.type) && webpage)
    return null;
  }

  public static EmbeddedService parse (TdApi.PageBlockEmbedded embedded) {
    return parse(embedded.url, embedded.width, embedded.height, embedded.posterPhoto, null);
  }

  private static EmbeddedService parse (String webPageUrl, int width, int height, TdApi.Photo thumbnail, @Nullable String embedUrl) {
    if (StringUtils.isEmpty(webPageUrl))
      return null;
    try {
      if (!webPageUrl.startsWith("https://") && !webPageUrl.startsWith("http://"))
        webPageUrl = "https://" + webPageUrl;
      Uri uri = Uri.parse(webPageUrl);
      String host = uri.getHost();
      List<String> segmentsList = uri.getPathSegments();
      if (StringUtils.isEmpty(host) || segmentsList == null || segmentsList.isEmpty())
        return null;
      String[] segments = segmentsList.toArray(new String[0]);
      if (host.startsWith("www.")) {
        host = host.substring("www.".length());
      } else if (host.startsWith("m.")) {
        host = host.substring("m.".length());
      }
      int viewType = 0;
      String viewIdentifier;
      switch (host) {
        case "youtube.com": {
          // https://youtu.be/zg-HMBwYckc
          // https://www.youtube.com/embed/zg-HMBwYckc
          // https://www.youtube.com/watch?v=zg-HMBwYckc&feature=player_embedded
          viewType = TYPE_YOUTUBE;
          if (segments.length == 1 && "watch".equals(segments[0]) && !StringUtils.isEmpty(viewIdentifier = uri.getQueryParameter("v"))) {
            embedUrl = "https://www.youtube.com/embed/" + viewIdentifier;
          }
          break;
        }
        case "youtu.be": {
          // https://youtu.be/zg-HMBwYckc
          viewType = TYPE_YOUTUBE;
          if (segments.length == 1 && !StringUtils.isEmpty(viewIdentifier = segments[0])) {
            embedUrl = "https://www.youtube.com/embed/" + viewIdentifier;
          }
          break;
        }
        case "coub.com": {
          if (segments.length == 2 && !StringUtils.isEmpty(segments[1]) && "view".equals(segments[0])) {
            viewType = TYPE_CUSTOM_EMBED;
            embedUrl = "https://coub.com/embed/" + segments[1] + "?muted=false&autostart=true&originalSize=false&startWithHD=false";
          }
          break;
        }
        case "open.spotify.com": {
          // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
          // https://open.spotify.com/track/1S1c300Ip9sd3468pjyZrw?si=bc58b541be9c4e74
          // https://open.spotify.com/show/0DbCZ1Xs8K1AElDii3RX3v?si=9799ae6776d7433a
          // https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M
          if (segments.length == 2 && !StringUtils.isEmpty(segments[1])) {
            String embedVerb;

            if (segments[0].equals("show") || segments[0].equals("episode")) {
              embedVerb = "embed-podcast";
              width = height = Screen.dp(231); // hardcoded on Spotify's CSS
            } else {
              embedVerb = "embed";
              width = height = 1;
            }

            embedUrl = "https://open.spotify.com/" + embedVerb + "/" + segments[0] + "/" + segments[1];

            // Needed if the service is not supported for embedding using TDLib (height will be normal anyway)
            viewType = TYPE_CUSTOM_EMBED;
          }
        }
        case "music.apple.com":
        case "podcasts.apple.com": {
          // NOTE: I haven't found any public documentation for AM's embedding, so the research is done from macOS Music.app
          // https://music.apple.com/ru/album/audacity-feat-headie-one/1487951013?i=1487951015
          // https://music.apple.com/ru/playlist/need-for-speed-2015-in-game-tracks/pl.u-GgA5kAghobyv8l
          // https://embed.music.apple.com/ru/album/audacity-feat-headie-one/1487951013?i=1487951015

          // NOTE: I haven't found any public documentation for AP's embedding, so the research is done from macOS Podcasts.app
          // https://podcasts.apple.com/ru/podcast/%D0%B4%D1%83%D1%88%D0%B5%D0%B2%D0%BD%D1%8B%D0%B9-%D0%BF%D0%BE%D0%B4%D0%BA%D0%B0%D1%81%D1%82/id1130785672?i=1000538952815
          // https://podcasts.apple.com/ru/podcast/zavtracast-%D0%B7%D0%B0%D0%B2%D1%82%D1%80%D0%B0%D0%BA%D0%B0%D1%81%D1%82/id1068329384

          if (uri.getPath().matches("^(?:/[^/]*)?/(?:podcast|album|playlist)/[^/]+/[^/]+")) {
            embedUrl = uri.buildUpon()
              .authority(uri.getAuthority().replaceAll("([a-zA-Z0-9\\-]+)(?=\\.apple\\.com$)", "embed.$1"))
              .path(uri.getPath().replaceAll("^/?/([^/]+/[^/]+/[^/]+)$", "/us/$1"))
              .build()
              .toString();
            // Needed if the service is not supported for embedding using TDLib (height will be normal anyway)
            if (uri.getQueryParameter("i") != null) {
              width = height = Screen.dp(175); // reference to a specific track, AM uses smaller player
            } else {
              width = height = 1;
            }
            viewType = TYPE_CUSTOM_EMBED;
          }
          break;
        }
        case "tidal.com": {
          if (segments.length == 3 && segments[0].equals("browse") && !StringUtils.isEmpty(segments[2])) {
            String dataType = segments[1];

            if (dataType.equals("track") || dataType.equals("album") || dataType.equals("playlist")) {
              width = height = Screen.dp(250);
              viewType = TYPE_CUSTOM_EMBED;
              embedUrl = new Uri.Builder()
                .scheme("https")
                .authority("embed.tidal.com")
                .path("/" + dataType +  "s/" + segments[2])
                .appendQueryParameter("layout", "gridify")
                .appendQueryParameter("disableAnalytics", "true")
                .build()
                .toString();
            }
          }

          break;
        }
        case "music.yandex.ru": {
          if (segments.length == 2) {
            // https://music.yandex.ru/album/8415032
            viewType = TYPE_CUSTOM_EMBED;
            width = height = 1;
            embedUrl = "https://music.yandex.ru/iframe/#" + segments[0] + "/" + segments[1];
          } else if (segments.length == 4 && (segments[2].equals("playlists") || segments[0].equals("album"))) {
            // https://music.yandex.ru/album/8415032/track/56685586
            // https://music.yandex.ru/users/music-blog/playlists/2465
            viewType = TYPE_CUSTOM_EMBED;
            width = height = 1;
            if (segments[0].equals("users")) {
              embedUrl = "https://music.yandex.ru/iframe/#playlist/" + segments[1] + "/" + segments[3];
            } else {
              embedUrl = "https://music.yandex.ru/iframe/#" + segments[0] + "/" + segments[1] + "/" + segments[3];
            }
          }
          break;
        }
      }
      if (viewType != 0 && !StringUtils.isEmpty(embedUrl)) {
        if (width == 0 || height == 0) {
          width = height = 1;
        }

        return new EmbeddedService(viewType, webPageUrl, width, height, thumbnail, embedUrl, null);
      }
    } catch (Throwable t) {
      Log.e("Unable to parse embedded service", t);
    }
    return null;
  }

  // Creates embed data from a page's URL (if the site is supported by TGX in-app parser)
  public static EmbeddedService parseUrl (String pageUrl) {
    return parse(pageUrl, 0, 0, null, null);
  }

  // If YouTube URL and YouTube app is installed - better wait for Telegram preview to get a webpage
  public static boolean isYoutubeUrl (String pageUrl) {
    if (StringUtils.isEmpty(pageUrl))
      return false;

    try {
      if (!pageUrl.startsWith("https://") && !pageUrl.startsWith("http://"))
        pageUrl = "https://" + pageUrl;
      Uri uri = Uri.parse(pageUrl);
      String host = uri.getHost();
      return host.matches("^(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)$");
    } catch (Throwable t) {
      Log.e("Unable to parse embedded service", t);
    }

    return false;
  }
}
