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
      if (host.startsWith("www."))
        host = host.substring("www".length());
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
    EmbeddedService service = parse(webPage.url, webPage.embedWidth, webPage.embedHeight, webPage.photo);
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
    return parse(embedded.url, embedded.width, embedded.height, embedded.posterPhoto);
  }

  private static EmbeddedService parse (String webPageUrl, int width, int height, TdApi.Photo thumbnail) {
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
      if (host.startsWith("www."))
        host = host.substring("www.".length());
      int viewType = 0;
      String viewUrl = null;
      String viewIdentifier = null;
      switch (host) {
        case "youtube.com": {
          // https://www.youtube.com/embed/zg-HMBwYckc
          // https://www.youtube.com/watch?v=zg-HMBwYckc&feature=player_embedded
          viewType = TYPE_YOUTUBE;
          if (segments.length == 2 && "embed".equals(segments[0]) && !StringUtils.isEmpty(segments[1])) {
            viewIdentifier = segments[1];
            String query = uri.getEncodedQuery();
            viewUrl = "https://youtube.com/watch?v=" + viewIdentifier;
            if (!StringUtils.isEmpty(query)) {
              viewUrl += "&" + query;
            }
          } else if (segments.length == 1 && "watch".equals(segments[0]) && !StringUtils.isEmpty(viewIdentifier = uri.getQueryParameter("v"))) {
            viewUrl = webPageUrl;
          }
          break;
        }
        case "youtu.be": {
          // https://youtu.be/zg-HMBwYckc
          viewType = TYPE_YOUTUBE;
          if (segments.length == 1 && !StringUtils.isEmpty(segments[0])) {
            viewIdentifier = segments[0];
            String query = uri.getEncodedQuery();
            viewUrl = "https://youtube.com/watch?v=" + viewIdentifier;
            if (!StringUtils.isEmpty(query)) {
              viewUrl += "&" + query;
            }
          }
          break;
        }
        case "coub.com": {
          if (segments.length == 2 && !StringUtils.isEmpty(segments[1])) {
            if ("view".equals(segments[0])) {
              viewType = TYPE_CUSTOM_EMBED;
              viewUrl = "https://coub.com/embed/" + segments[1] + "?muted=false&autostart=true&originalSize=false&startWithHD=false";
            }
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

            viewUrl = "https://open.spotify.com/" + embedVerb + "/" + segments[0] + "/" + segments[1];

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
            viewUrl = uri.buildUpon()
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
              viewUrl = new Uri.Builder()
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
        /*case "coub.com": {
          // https://coub.com/embed/20k5cb?muted=false&autostart=false&originalSize=false&startWithHD=false
          // https://coub.com/view/20k5cb
          // https://coub.com/api/v2/coubs/20k5cb.json
          viewType = TYPE_COUB;
          if (segments.length == 2 && !StringUtils.isEmpty(segments[1])) {
            if ("view".equals(segments[0])) {
              viewUrl = url;
              viewIdentifier = segments[1];
            } else if ("embed".equals(segments[0])) {
              viewIdentifier = segments[1];
              viewUrl = "https://coub.com/view/" + viewIdentifier;
              String query = uri.getEncodedQuery();
              if (!StringUtils.isEmpty(query)) {
                viewUrl += "?" + query;
              }
            }
          }
          break;
        }
        case "vimeo.com": {
          // https://vimeo.com/360123613
          viewType = TYPE_VIMEO;
          if (segments.length == 1 && StringUtils.isNumeric(segments[0])) {
            viewUrl = url;
            viewIdentifier = segments[0];
          }
          break;
        }
        case "player.vimeo.com": {
          // https://player.vimeo.com/video/360123613
          viewType = TYPE_VIMEO;
          if (segments.length == 2 && "video".equals(segments[0]) && StringUtils.isNumeric(segments[1])) {
            viewUrl = "https://vimeo.com/" + segments[1];
            viewIdentifier = segments[1];
          }
          break;
        }
        case "dailymotion.com": {
          // https://www.dailymotion.com/video/x7gdt1c?playlist=x5v2j4
          // https://www.dailymotion.com/embed/video/x7gdt1c
          viewType = TYPE_DAILYMOTION;
          if (segments.length == 2 && "video".equals(segments[0]) && !StringUtils.isEmpty(segments[1])) {
            viewUrl = url;
            viewIdentifier = segments[1];
          } else if (segments.length == 3 && "embed".equals(segments[0]) && "video".equals(segments[1]) && !StringUtils.isEmpty(segments[2])) {
            viewIdentifier = segments[2];
            viewUrl = "https://dailymotion.com/video/" + viewIdentifier;
            String query = uri.getEncodedQuery();
            if (!StringUtils.isEmpty(query)) {
              viewUrl += "?" + query;
            }
          }
          break;
        }
        case "soundcloud.com": {
          // https://soundcloud.com/leagueoflegends/star-guardian-2019-login-theme
          // https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/tracks/680741072&color=%23ff5500&auto_play=false&hide_related=false&show_comments=true&show_user=true&show_reposts=false&show_teaser=true&visual=true
          viewType = TYPE_SOUNDCLOUD;

          break;
        }*/
      }
      if (viewType != 0 && !StringUtils.isEmpty(viewUrl)) {
        return new EmbeddedService(viewType, webPageUrl, width, height, thumbnail, viewUrl, null);
      }
    } catch (Throwable t) {
      Log.e("Unable to parse embedded service", t);
    }
    return null;
  }
}
