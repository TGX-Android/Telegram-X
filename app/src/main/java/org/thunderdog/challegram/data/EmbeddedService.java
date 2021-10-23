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

  private static EmbeddedService parse (String url, int width, int height, TdApi.Photo thumbnail) {
    if (StringUtils.isEmpty(url))
      return null;
    try {
      if (!url.startsWith("https://") && !url.startsWith("http://"))
        url = "https://" + url;
      Uri uri = Uri.parse(url);
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
            viewUrl = url;
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
        /*case "vimeo.com": {
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
        return new EmbeddedService(viewType, viewUrl, width, height, thumbnail, null, null);
      }
    } catch (Throwable t) {
      Log.e("Unable to parse embedded service", t);
    }
    return null;
  }
}
