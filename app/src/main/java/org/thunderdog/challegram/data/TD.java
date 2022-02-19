/**
 * File created on 30/04/15 at 17:35
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.HashtagController;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.android.text.AcceptFilter;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TD {

  public static final int COMBINE_MODE_NONE = 0;
  public static final int COMBINE_MODE_MEDIA = 1;
  public static final int COMBINE_MODE_AUDIO = 2;
  public static final int COMBINE_MODE_FILES = 3;

  public static boolean isValidRight (@RightId int rightId) {
    switch (rightId) {
      case R.id.right_readMessages:
      case R.id.right_sendMessages:
      case R.id.right_sendMedia:
      case R.id.right_sendStickersAndGifs:
      case R.id.right_sendPolls:
      case R.id.right_embedLinks:
      case R.id.right_inviteUsers:
      case R.id.right_pinMessages:
      case R.id.right_changeChatInfo:
      case R.id.right_addNewAdmins:
      case R.id.right_banUsers:
      case R.id.right_deleteMessages:
      case R.id.right_editMessages:
        return true;
    }
    return false;
  }

  public static void saveMessageId (Bundle outState, String prefix, MessageId messageId) {
    if (messageId != null) {
      outState.putLong(prefix + "_chatId", messageId.getChatId());
      outState.putLong(prefix + "_id", messageId.getMessageId());
      outState.putLongArray(prefix + "_otherIds", messageId.getOtherMessageIds());
    }
  }

  public static MessageId restoreMessageId (Bundle inState, String prefix) {
    long messageId = inState.getLong(prefix + "_id");
    if (messageId != 0) {
      return new MessageId(inState.getLong(prefix + "_chatId"), messageId, inState.getLongArray(prefix + "_otherIds"));
    }
    return null;
  }

  public static boolean checkRight (TdApi.ChatPermissions permissions, @RightId int rightId) {
    switch (rightId) {
      case R.id.right_readMessages:
        return true;
      case R.id.right_sendMessages:
        return permissions.canSendMessages;
      case R.id.right_sendMedia:
        return permissions.canSendMediaMessages;
      case R.id.right_sendStickersAndGifs:
        return permissions.canSendOtherMessages;
      case R.id.right_sendPolls:
        return permissions.canSendPolls;
      case R.id.right_embedLinks:
        return permissions.canAddWebPagePreviews;
      case R.id.right_inviteUsers:
        return permissions.canInviteUsers;
      case R.id.right_pinMessages:
        return permissions.canPinMessages;
      case R.id.right_changeChatInfo:
        return permissions.canChangeInfo;
      case R.id.right_addNewAdmins:
      case R.id.right_banUsers:
      case R.id.right_deleteMessages:
      case R.id.right_editMessages:
        break;
    }
    throw new IllegalArgumentException(Lang.getResourceEntryName(rightId));
  }

  private static final int[] color_ids =  {
    R.id.theme_color_avatarRed      /* red 0 */,
    R.id.theme_color_avatarOrange   /* orange 1 */,
    R.id.theme_color_avatarYellow   /* yellow 2 */,
    R.id.theme_color_avatarGreen    /* green 3 */,
    R.id.theme_color_avatarCyan     /* cyan 4 */,
    R.id.theme_color_avatarBlue     /* blue 5 */,
    R.id.theme_color_avatarViolet   /* violet 6 */,
    R.id.theme_color_avatarPink     /* pink 7 */
  };

  public static boolean isSameSource (TdApi.Message a, TdApi.Message b, boolean splitAuthors) {
    if (a == null || b == null)
      return false;
    if ((a.forwardInfo == null) == (b.forwardInfo != null))
      return false;
    if (a.forwardInfo == null){
      return a.chatId == b.chatId;
    }
    if (a.forwardInfo.origin.getConstructor() != b.forwardInfo.origin.getConstructor() || a.forwardInfo.fromChatId != b.forwardInfo.fromChatId)
      return false;
    if (splitAuthors) {
      switch (a.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageForwardOriginUser.CONSTRUCTOR:
          return ((TdApi.MessageForwardOriginUser) a.forwardInfo.origin).senderUserId == ((TdApi.MessageForwardOriginUser) b.forwardInfo.origin).senderUserId;

        case TdApi.MessageForwardOriginChannel.CONSTRUCTOR:
          return ((TdApi.MessageForwardOriginChannel) a.forwardInfo.origin).chatId == ((TdApi.MessageForwardOriginChannel) b.forwardInfo.origin).chatId &&
            StringUtils.equalsOrBothEmpty(((TdApi.MessageForwardOriginChannel) a.forwardInfo.origin).authorSignature, ((TdApi.MessageForwardOriginChannel) b.forwardInfo.origin).authorSignature);
        case TdApi.MessageForwardOriginChat.CONSTRUCTOR:
          return ((TdApi.MessageForwardOriginChat) a.forwardInfo.origin).senderChatId == ((TdApi.MessageForwardOriginChat) b.forwardInfo.origin).senderChatId &&
            StringUtils.equalsOrBothEmpty(((TdApi.MessageForwardOriginChat) a.forwardInfo.origin).authorSignature, ((TdApi.MessageForwardOriginChat) b.forwardInfo.origin).authorSignature);

        case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR:
          return ((TdApi.MessageForwardOriginHiddenUser) a.forwardInfo.origin).senderName.equals(((TdApi.MessageForwardOriginHiddenUser) b.forwardInfo.origin).senderName);
        case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR:
          return StringUtils.equalsOrBothEmpty(((TdApi.MessageForwardOriginMessageImport) a.forwardInfo.origin).senderName, ((TdApi.MessageForwardOriginMessageImport) b.forwardInfo.origin).senderName);
      }
    }
    return false;
  }

  public static boolean isSecret (TdApi.InputMessageContent content) {
    int ttl = 0;
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        ttl = ((TdApi.InputMessagePhoto) content).ttl;
        break;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        ttl = ((TdApi.InputMessageVideo) content).ttl;
        break;
    }
    return ttl != 0 && ttl <= 60;
  }

  public static CharSequence formatString (@Nullable TdlibDelegate context, String text, TdApi.TextEntity[] entities, @Nullable Typeface defaultTypeface, @Nullable CustomTypefaceSpan.OnClickListener onClickListener) {
    if (entities == null || entities.length == 0)
      return text;

    SpannableStringBuilder b = null;
    for (TdApi.TextEntity entity : entities) {
      CustomTypefaceSpan span;
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
          span = null; // nothing to do?
          break;
        case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
        case TdApi.TextEntityTypeCashtag.CONSTRUCTOR: {
          String hashtag = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                if (context != null) {
                  HashtagController c = new HashtagController(context.context(), context.tdlib());
                  c.setArguments(hashtag);
                  context.context().navigation().navigateTo(c);
                }
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: {
          String emailAddress = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                Intents.sendEmail(emailAddress);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
          String username = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                if (context != null)
                  context.tdlib().ui().openPublicChat(context, username, null);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: {
          String cardNumber = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                if (context != null)
                  context.tdlib().ui().openCardNumber(context, cardNumber);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR: {
          String phoneNumber = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                Intents.openNumber(phoneNumber);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
          String url = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                if (context != null)
                  context.tdlib().ui().openUrl(context, url, null);
              }
              return true;
            });
          break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
          String textUrl = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink)
            .setOnClickListener((view, span1) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1)) {
                if (context != null)
                  context.tdlib().ui().openUrl(context, textUrl, null);
              }
              return true;
            });
          break;
        case TdApi.TextEntityTypeBold.CONSTRUCTOR:
          if (Text.needFakeBold(text, entity.offset, entity.offset + entity.length))
            span = new CustomTypefaceSpan(null, 0).setFakeBold(true);
          else
            span = new CustomTypefaceSpan(Fonts.getRobotoMedium(), 0);
          break;
        default:
          span = TD.toDisplaySpan(entity.type, defaultTypeface, false); // TODO move all above stuff to this method
          break;
      }
      if (span != null) {
        if (b == null)
          b = new SpannableStringBuilder(text);
        if (span.getOnClickListener() != null) {
          b.setSpan(new ClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              span.getOnClickListener().onClick(widget, span);
            }
          }, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        span.setEntityType(entity.type).setRemoveUnderline(true);
        b.setSpan(span, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return b != null ? b : text;
  }

  public static String getLanguageKeyLink (String key) {
    return Lang.getString(R.string.url_translationsPrefix) + key;
  }

  private static List<String> findUrls (String text, @Nullable List<String> urls) {
    TdApi.TextEntity[] entities = Td.findEntities(text);
    if (entities != null) {
      for (TdApi.TextEntity entity : entities) {
        if (entity.type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR) {
          if (urls == null)
            urls = new ArrayList<>();
          urls.add(text.substring(entity.offset, entity.offset + entity.length));
        }
      }
    }
    return urls;
  }

  public static boolean parseMarkdownWithEntities (TdApi.FormattedText text) {
    if (Td.isEmpty(text))
      return false;
    boolean ret = Td.parseMarkdown(text);
    ret = parseEntities(text) || ret;
    return ret;
  }

  public static boolean parseEntities (TdApi.FormattedText text) {
    if (Td.isEmpty(text))
      return false;
    if (text.entities == null || text.entities.length == 0) {
      TdApi.TextEntity[] foundEntities = Td.findEntities(text.text);
      if (foundEntities != null) {
        text.entities = foundEntities;
        return true;
      }
      return false;
    }
    List<TdApi.TextEntity> entities = new ArrayList<>();
    int start = 0;
    for (TdApi.TextEntity entity : text.entities) {
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        case TdApi.TextEntityTypePre.CONSTRUCTOR:
        case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:

        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
        case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
        case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
        case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
        case TdApi.TextEntityTypeMention.CONSTRUCTOR:
          if (entity.offset > start) {
            TdApi.TextEntity[] foundEntities = Td.findEntities(text.text.substring(start, entity.offset));
            if (foundEntities != null)
              Collections.addAll(entities, foundEntities);
          }
          start = entity.offset + entity.length;
          break;
      }
    }
    if (start < text.text.length()) {
      TdApi.TextEntity[] foundEntities = Td.findEntities(start == 0 ? text.text : text.text.substring(start));
      if (foundEntities != null)
        Collections.addAll(entities, foundEntities);
    }
    if (!entities.isEmpty()) {
      Collections.addAll(entities, text.entities);
      text.entities = entities.toArray(new TdApi.TextEntity[0]);
      Td.sort(text.entities);
      return true;
    }
    return false;
  }

  public static List<String> findUrls (TdApi.FormattedText text) {
    if (Td.isEmpty(text))
      return null;

    List<String> links = null;
    if (text.entities != null && text.entities.length > 0) {
      int start = 0;
      for (TdApi.TextEntity existingEntity : text.entities) {
        switch (existingEntity.type.getConstructor()) {
          case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:

          case TdApi.TextEntityTypeCode.CONSTRUCTOR:
          case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
          case TdApi.TextEntityTypePre.CONSTRUCTOR: {
            // process every link before this offset
            if (existingEntity.offset > start) {
              links = findUrls(text.text.substring(start, existingEntity.offset), links);
            }

            // add textUrl
            if (existingEntity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
              if (links == null)
                links = new ArrayList<>();
              links.add(((TdApi.TextEntityTypeTextUrl) existingEntity.type).url);
            }

            // next time, look after the end of this entity
            start = existingEntity.offset + existingEntity.length;

            break;
          }
        }
      }
      if (start < text.text.length()) {
        links = findUrls(text.text.substring(start), links);
      }
    } else {
      links = findUrls(text.text, links);
    }

    return links;
  }

  public static boolean isVisual (TdApi.TextEntityType type, boolean allowInternal) {
    if (type == null)
      return false;
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
        return false;

      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return true;

      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return allowInternal;
    }

    return false;
  }

  public static GifFile toGifFile (Tdlib tdlib, TdApi.Thumbnail thumbnail) {
    if (thumbnail == null)
      return null;
    switch (thumbnail.format.getConstructor()) {
      case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
      case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
      case TdApi.ThumbnailFormatGif.CONSTRUCTOR:
      case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
        return null;
      case TdApi.ThumbnailFormatTgs.CONSTRUCTOR: {
        GifFile gifFile = new GifFile(tdlib, thumbnail.file, GifFile.TYPE_TG_LOTTIE);
        gifFile.setOptimize(true);
        return gifFile;
      }
      case TdApi.ThumbnailFormatWebm.CONSTRUCTOR:
      case TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR: {
        GifFile gifFile = new GifFile(tdlib, thumbnail.file,
          thumbnail.format.getConstructor() == TdApi.ThumbnailFormatWebm.CONSTRUCTOR ?
            GifFile.TYPE_WEBM :
            GifFile.TYPE_MPEG4
        );
        gifFile.setOptimize(true);
        return gifFile;
      }
    }
    return null;
  }

  public static ImageFile toImageFile (Tdlib tdlib, TdApi.Thumbnail thumbnail) {
    if (thumbnail == null)
      return null;
    switch (thumbnail.format.getConstructor()) {
      case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
      case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
      case TdApi.ThumbnailFormatGif.CONSTRUCTOR:
        return new ImageFile(tdlib, thumbnail.file);
      case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
        ImageFile file = new ImageFile(tdlib, thumbnail.file);
        file.setWebp();
        return file;
      case TdApi.ThumbnailFormatTgs.CONSTRUCTOR: // TODO 1-frame
      case TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR:
      case TdApi.ThumbnailFormatWebm.CONSTRUCTOR:
        return null;

    }
    return null;
  }

  public static TdApi.Thumbnail toThumbnail (TdApi.Sticker sticker) {
    if (sticker == null) {
      return null;
    }
    if (sticker.thumbnail != null)
      return sticker.thumbnail;
    if (Td.isAnimated(sticker.type))
      return null;
    return new TdApi.Thumbnail(new TdApi.ThumbnailFormatWebp(), sticker.width, sticker.height, sticker.sticker);
  }

  public static TdApi.Thumbnail toThumbnail (TdApi.PhotoSize photoSize) {
    if (photoSize == null) {
      return null;
    }
    return new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), photoSize.width, photoSize.height, photoSize.photo);
  }

  public static boolean canRetractVote (TdApi.Poll poll) {
    switch (poll.type.getConstructor()) {
      case TdApi.PollTypeRegular.CONSTRUCTOR: {
        for (TdApi.PollOption option : poll.options) {
          if (option.isChosen) {
            return true;
          }
        }
        break;
      }
      case TdApi.PollTypeQuiz.CONSTRUCTOR:
        return false;
    }
    return false;
  }

  public static TextEntity[] collectAllEntities (ViewController<?> context, Tdlib tdlib, CharSequence cs, boolean onlyLinks, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (StringUtils.isEmpty(cs))
      return null;
    String str = cs.toString();
    List<TextEntity> entities = null;
    TextEntity[] telegramEntities = TextEntity.valueOf(tdlib, str, toEntities(cs, onlyLinks), openParameters);
    if (telegramEntities != null && telegramEntities.length > 0) {
      entities = new ArrayList<>(telegramEntities.length);
      entities.addAll(Arrays.asList(telegramEntities));
    }
    /*if (cs instanceof Spanned) {
      Spanned spanned = (Spanned) cs;
      ClickableSpan[] clickableSpans = spanned.getSpans(0, cs.length(), ClickableSpan.class);
      if (clickableSpans != null && clickableSpans.length > 0) {
        if (entities == null)
          entities = new ArrayList<>(clickableSpans.length);
        for (ClickableSpan span : clickableSpans) {
          int spanStart = spanned.getSpanStart(span);
          int spanEnd = spanned.getSpanEnd(span);
          entities.add(new TextEntityCustom(context, tdlib, str, spanStart, spanEnd, TextEntityCustom.FLAG_CLICKABLE, openParameters).setOnClickListener(span));
        }
      }
    }*/
    if (entities != null) {
      TextEntity[] result = entities.toArray(new TextEntity[0]);
      Arrays.sort(result, (a, b) -> Integer.compare(a.getStart(), b.getStart()));
      return result;
    }
    return null;
  }

  public static int getColorIdForString (String string) {
    switch (Math.abs(string.hashCode()) % 3) {
      case 0: return R.id.theme_color_fileYellow;
      case 1: return R.id.theme_color_fileRed;
      case 2: return R.id.theme_color_fileGreen;
    }
    return R.id.theme_color_file;
  }

  public static int getColorIdForName (String name) {
    return color_ids[MathUtils.pickNumber(color_ids.length, name)];
  }

  public static int getFileColorId (TdApi.Document doc, boolean isOutBubble) {
    return getFileColorId(doc.fileName, doc.mimeType, isOutBubble);
  }

  public static int getFileColorId (String fileName, @Nullable  String mimeType, boolean isOutBubble) {
    String mime = mimeType != null ? mimeType.toLowerCase() : null;
    int i = fileName.lastIndexOf('.');
    String ext = i != -1 ? fileName.substring(i + 1).toLowerCase() : "";

    // Android APKs
    if ("application/vnd.android.package-archive".equals(mime) || "apk".equals(ext)) {
      return R.id.theme_color_fileGreen;
    }

    if (
      "7z".equals(ext) || "application/x-7z-compressed".equals(mime) ||
      "zip".equals(ext) || "application/zip".equals(mime) ||
      "rar".equals(ext) || "application/x-rar-compressed".equals(mime)
      ) {
      return R.id.theme_color_fileYellow;
    }

    if (
      "pdf".equals(ext) || "application/pdf".equals(mime)
      ) {
      return R.id.theme_color_fileRed;
    }

    return isOutBubble ? R.id.theme_color_bubbleOut_file : R.id.theme_color_file;
  }

  public static String getPhoneNumber (String in) {
    return StringUtils.isEmpty(in) || in.startsWith("+") ? in : "+" + in;
  }

  public static void saveSender (Bundle bundle, String prefix, TdApi.MessageSender sender) {
    if (sender == null)
      return;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        bundle.putLong(prefix + "chat_id", ((TdApi.MessageSenderChat) sender).chatId);
        break;
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        bundle.putLong(prefix + "user_id", ((TdApi.MessageSenderUser) sender).userId);
        break;
      default:
        throw new RuntimeException(sender.toString());
    }
  }

  public static TdApi.MessageSender restoreSender (Bundle bundle, String prefix) {
    long chatId = bundle.getLong(prefix + "chat_id");
    if (chatId != 0)
      return new TdApi.MessageSenderChat(chatId);
    long userId = bundle.getLong(prefix + "user_id");
    if (userId != 0)
      return new TdApi.MessageSenderUser(userId);
    return null;
  }

  public static void saveFilter (Bundle bundle, String prefix, TdApi.SearchMessagesFilter filter) {
    if (filter == null)
      return;
    int type;
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        type = 1;
        break;
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
        type = 2;
        break;
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
        type = 3;
        break;
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
        type = 4;
        break;
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
        type = 5;
        break;
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
        type = 6;
        break;
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
        type = 7;
        break;
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
        type = 8;
        break;
      case TdApi.SearchMessagesFilterChatPhoto.CONSTRUCTOR:
        type = 9;
        break;
      /*case TdApi.SearchMessagesFilterCall.CONSTRUCTOR:
        type = 10;
        break;
      case TdApi.SearchMessagesFilterMissedCall.CONSTRUCTOR:
        type = 11;
        break;*/
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
        type = 12;
        break;
      case TdApi.SearchMessagesFilterVoiceAndVideoNote.CONSTRUCTOR:
        type = 13;
        break;
      case TdApi.SearchMessagesFilterMention.CONSTRUCTOR:
        type = 14;
        break;
      case TdApi.SearchMessagesFilterUnreadMention.CONSTRUCTOR:
        type = 15;
        break;
      case TdApi.SearchMessagesFilterFailedToSend.CONSTRUCTOR:
        type = 16;
        break;
      case TdApi.SearchMessagesFilterPinned.CONSTRUCTOR:
        type = 17;
        break;
      case TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR:
      default:
        type = 0;
        break;
    }
    if (type != 0) {
      bundle.putInt(prefix + "type", type);
    }
  }

  public static TdApi.SearchMessagesFilter restoreFilter (Bundle bundle, String prefix) {
    int type = bundle.getInt(prefix + "type", 0);
    if (type != 0) {
      switch (type) {
        case 1: return new TdApi.SearchMessagesFilterAnimation();
        case 2: return new TdApi.SearchMessagesFilterAudio();
        case 3: return new TdApi.SearchMessagesFilterDocument();
        case 4: return new TdApi.SearchMessagesFilterPhoto();
        case 5: return new TdApi.SearchMessagesFilterVideo();
        case 6: return new TdApi.SearchMessagesFilterVoiceNote();
        case 7: return new TdApi.SearchMessagesFilterPhotoAndVideo();
        case 8: return new TdApi.SearchMessagesFilterUrl();
        case 9: return new TdApi.SearchMessagesFilterChatPhoto();
        /*case 10: return new TdApi.SearchMessagesFilterCall();
        case 11: return new TdApi.SearchMessagesFilterMissedCall();*/
        case 12: return new TdApi.SearchMessagesFilterVideoNote();
        case 13: return new TdApi.SearchMessagesFilterVoiceAndVideoNote();
        case 14: return new TdApi.SearchMessagesFilterMention();
        case 15: return new TdApi.SearchMessagesFilterUnreadMention();
        case 16: return new TdApi.SearchMessagesFilterFailedToSend();
        case 17: return new TdApi.SearchMessagesFilterPinned();
      }
    }
    return null;
  }

  public static TdApi.ChatList chatListFromKey (String key) {
    if (StringUtils.isEmpty(key))
      return null;
    switch (key) {
      case "main":
        return new TdApi.ChatListMain();
      case "archive":
        return new TdApi.ChatListArchive();
      default:
        if (key.startsWith("filter")) {
          return new TdApi.ChatListFilter(StringUtils.parseInt(key.substring("filter".length())));
        }
        break;
    }
    return null;
  }

  public static String makeSenderKey (TdApi.MessageSender sender) {
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        return "user_" + ((TdApi.MessageSenderUser) sender).userId;
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        return "chat_" + ((TdApi.MessageSenderChat) sender).chatId;
    }
    throw new IllegalArgumentException(sender.toString());
  }

  public static String makeChatListKey (TdApi.ChatList chatList) {
    // reserved for future: folders, etc
    switch (chatList.getConstructor()) {
      case TdApi.ChatListMain.CONSTRUCTOR:
        return "main";
      case TdApi.ChatListArchive.CONSTRUCTOR:
        return "archive";
      case TdApi.ChatListFilter.CONSTRUCTOR:
        return "filter" + ((TdApi.ChatListFilter) chatList).chatFilterId;
    }
    throw new UnsupportedOperationException(chatList.toString());
  }

  public static int getColorIndex (long selfUserId, long id) {
    if (id >= 0 && id < color_ids.length) {
      return (int) id;
    }
    try {
      String str;
      if (id >= 0 && selfUserId != 0) {
        str = String.format(Locale.US, "%d%d", id, selfUserId);
      } else {
        str = String.format(Locale.US, "%d", id);
      }
      if (str.length() > 15) {
        str = str.substring(0, 15);
      }
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(str.getBytes(StringUtils.UTF_8));
      int b = digest[(int) Math.abs(id % 16)];
      if (b < 0) {
        b += 256;
      }
      return Math.abs(b) % color_ids.length;
    } catch (Throwable t) {
      Log.e("Cannot calculate user color", t);
    }

    return (int) Math.abs(id % color_ids.length);
  }

  public static ImageFile getAvatar (Tdlib tdlib, TdApi.User user) {
    return user != null ? getAvatar(tdlib, user.profilePhoto) : null;
  }

  public static ImageFile getAvatar (Tdlib tdlib, TdApi.ProfilePhoto profilePhoto) {
    if (profilePhoto != null) {
      ImageFile file = new ImageFile(tdlib, profilePhoto.small);
      file.setSize(ChatView.getDefaultAvatarCacheSize());
      return file;
    }
    return null;
  }

  public static @StringRes int getCallName (TdApi.MessageCall call, boolean isOut, boolean isFull) {
    switch (call.discardReason.getConstructor()) {
      case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
        if (isFull)
          return isOut ? R.string.OutgoingCall : R.string.CallMessageIncomingDeclined;
        else
          return isOut ? R.string.Busy : R.string.Declined;
      case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
        if (isFull)
          return isOut ? R.string.CallMessageOutgoingMissed : R.string.MissedCall;
        else
          return isOut ? R.string.Cancelled : R.string.Missed;
      case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
      case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
      case TdApi.CallDiscardReasonEmpty.CONSTRUCTOR:
      default:
        if (isFull)
          return isOut ? R.string.OutgoingCall : R.string.IncomingCall;
        else
          return isOut ? R.string.Outgoing : R.string.Incoming;
    }
  }

  public static int defaultCompare (TdApi.User left, TdApi.User right) {
    int attempt = left.firstName.compareToIgnoreCase(right.firstName);
    if (attempt != 0) {
      return attempt;
    }
    attempt = left.lastName.compareToIgnoreCase(right.lastName);
    if (attempt != 0) {
      return attempt;
    }
    return Long.compare(left.id, right.id);
  }

  public static TdApi.FormattedText withPrefix (String prefix, TdApi.FormattedText text) {
    if (StringUtils.isEmpty(prefix))
      return text;
    if (text.entities == null || text.entities.length == 0)
      return new TdApi.FormattedText(prefix + text.text, text.entities);
    TdApi.FormattedText newText = new TdApi.FormattedText(prefix + text.text, new TdApi.TextEntity[text.entities.length]);
    int prefixLength = prefix.length();
    for (int i = 0; i < text.entities.length; i++) {
      TdApi.TextEntity entity = text.entities[i];
      newText.entities[i] = new TdApi.TextEntity(entity.offset + prefixLength, entity.length, entity.type);
    }
    return newText;
  }

  public static boolean isAnimatedSticker (String mimeType) {
    return TdConstants.ANIMATED_STICKER_MIME_TYPE.equals(mimeType);
  }

  public static boolean isAnimatedSticker (TdApi.Document doc) {
    return isAnimatedSticker(doc.mimeType) && U.compareExtension("tgs", doc.fileName) && doc.thumbnail != null && doc.document.size <= ByteUnit.KIB.toBytes(64);
  }

  public static int getProgress (@NonNull TdApi.ChatAction a) {
    switch (a.getConstructor()) {
      case TdApi.ChatActionUploadingVideo.CONSTRUCTOR:
        return ((TdApi.ChatActionUploadingVideo) a).progress;
      case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR:
        return ((TdApi.ChatActionUploadingVoiceNote) a).progress;
      case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR:
        return ((TdApi.ChatActionUploadingPhoto) a).progress;
      case TdApi.ChatActionUploadingDocument.CONSTRUCTOR:
        return ((TdApi.ChatActionUploadingDocument) a).progress;
      case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR:
        return ((TdApi.ChatActionUploadingVideoNote) a).progress;
    }
    return -1;
  }

  public static boolean compareContents (@NonNull TdApi.Poll a, @NonNull TdApi.Poll b) {
    if (a.options.length != b.options.length || !StringUtils.equalsOrBothEmpty(a.question, b.question))
      return false;
    int index = 0;
    for (TdApi.PollOption option : a.options) {
      if (!StringUtils.equalsOrBothEmpty(option.text, b.options[index].text))
        return false;
      index++;
    }
    return true;
  }

  public static boolean isAll (TdApi.ChatEventLogFilters filters) {
    return filters == null || (
      filters.messageEdits &&
      filters.messageDeletions &&
      filters.messagePins &&
      filters.memberJoins &&
      filters.memberLeaves &&
      filters.memberInvites &&
      filters.memberPromotions &&
      filters.memberRestrictions &&
      filters.infoChanges &&
      filters.settingChanges &&
      filters.inviteLinkChanges &&
      filters.videoChatChanges
    );
  }

  public static ImageFile getAvatar (Tdlib tdlib, TdApi.Chat chat) {
    return chat != null ? getAvatar(tdlib, chat.photo) : null;
  }

  public static ImageFile getAvatar (Tdlib tdlib, TdApi.ChatPhotoInfo chatPhoto) {
    if (chatPhoto != null) {
      ImageFile file = new ImageFile(tdlib, chatPhoto.small);
      file.setSize(ChatView.getDefaultAvatarCacheSize());
      return file;
    }
    return null;
  }

  public static boolean isScheduled (TdApi.Message message) {
    return message != null && message.schedulingState != null;
  }

  public static TdApi.PhotoSize toThumbnailSize (TdApi.Thumbnail thumbnail) {
    if (thumbnail == null)
      return null;
    switch (thumbnail.format.getConstructor()) {
      case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
      case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
      case TdApi.ThumbnailFormatWebp.CONSTRUCTOR: {
        int maxSize = Math.max(thumbnail.width, thumbnail.height);
        String type = maxSize <= 100 ? "s" : maxSize <= 320 ? "m" : "x";
        return new TdApi.PhotoSize(type, thumbnail.file, thumbnail.width, thumbnail.height, null);
      }
      default: {
        return null;
      }
    }
  }

  public static TdApi.Photo convertToPhoto (TdApi.Sticker sticker) {
    TdApi.PhotoSize thumbnailSize = toThumbnailSize(sticker.thumbnail);
    TdApi.PhotoSize[] sizes = new TdApi.PhotoSize[thumbnailSize != null ? 2 : 1];
    if (thumbnailSize != null) {
      sizes[0] = thumbnailSize;
      sizes[1] = new TdApi.PhotoSize("w", sticker.sticker, sticker.width, sticker.height, null);
    } else {
      sizes[0] = new TdApi.PhotoSize("w", sticker.sticker, sticker.width, sticker.height, null);
    }
    return new TdApi.Photo(false, null, sizes);
  }

  public static int getAvatarColorId (TdApi.User user, long selfUserId) {
    return getAvatarColorId(isUserDeleted(user) ? -1 : user == null ? 0 : user.id, selfUserId);
  }

  public static int getNameColorId (int avatarColorId) {
    switch (avatarColorId) {
      case R.id.theme_color_avatarRed:
        return R.id.theme_color_nameRed;
      case R.id.theme_color_avatarOrange:
        return R.id.theme_color_nameOrange;
      case R.id.theme_color_avatarYellow:
        return R.id.theme_color_nameYellow;
      case R.id.theme_color_avatarGreen:
        return R.id.theme_color_nameGreen;
      case R.id.theme_color_avatarCyan:
        return R.id.theme_color_nameCyan;
      case R.id.theme_color_avatarBlue:
        return R.id.theme_color_nameBlue;
      case R.id.theme_color_avatarViolet:
        return R.id.theme_color_nameViolet;
      case R.id.theme_color_avatarPink:
        return R.id.theme_color_namePink;
      case R.id.theme_color_avatarSavedMessages:
        return R.id.theme_color_messageAuthor;
      case R.id.theme_color_avatarInactive:
        return R.id.theme_color_nameInactive;
    }
    return R.id.theme_color_messageAuthor;
  }

  public static boolean isMultiChat (TdApi.Chat chat) {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          return !((TdApi.ChatTypeSupergroup) chat.type).isChannel;
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          return true;
      }
    }
    return false;
  }

  /*public static int getAuthorColorId () {
    return getAuthorColorId(true, 0);
  }

  public static int getAuthorColorId (int selfUserId, int id) {
    return selfUserId != 0 && selfUserId == id ? R.id.theme_color_chatAuthor : id == -1 ? R.id.theme_color_chatAuthorDead : color_ids[getColorIndex(selfUserId, id)];
  }*/

  public static int getAvatarColorId (long id, long selfUserId) {
    return id == -1 ? R.id.theme_color_avatarInactive : color_ids[getColorIndex(selfUserId, id)];
  }

  public static boolean hasEncryptionKey (TdApi.SecretChat secretChat) {
    return secretChat != null && secretChat.state.getConstructor() == TdApi.SecretChatStateReady.CONSTRUCTOR && secretChat.keyHash != null && secretChat.keyHash.length > 0;
  }

  public static boolean hasTitle (TdApi.Audio music) {
    return !StringUtils.isEmpty(music.title);
  }

  public static String getTitle (TdApi.Audio music) {
    return StringUtils.isEmpty(music.title) ? Lang.getString(R.string.UnknownTrack) : music.title;
  }

  public static boolean hasSubtitle (TdApi.Audio music) {
    return !StringUtils.isEmpty(music.performer) || (!StringUtils.isEmpty(music.fileName) && !StringUtils.equalsOrBothEmpty(music.fileName, music.title));
  }

  public static String getSubtitle (TdApi.Audio music) {
    return !StringUtils.isEmpty(music.performer) ? music.performer : StringUtils.isEmpty(music.fileName) || StringUtils.equalsOrBothEmpty(music.fileName, music.title) ? Lang.getString(R.string.AudioUnknownArtist) : music.fileName;
  }

  public static boolean canReplyTo (TdApi.Message message) {
    return message != null && message.sendingState == null && !isScheduled(message);
  }

  // DialogId.cpp

  public static boolean isValidLanguagePackId (String languagePackId) {
    return !StringUtils.isEmpty(languagePackId) && languagePackId.matches("[A-Za-z0-9-]{1,64}");
  }

  public static boolean isMutedForever (int muteForSeconds) {
    return TimeUnit.SECONDS.toDays(muteForSeconds) / 365 > 0;
  }

  public static boolean isSecret (TdApi.Message message) {
    return Td.isSecret(message.content);
  }

  public static boolean canSendToSecretChat (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePoll.CONSTRUCTOR:
      case TdApi.MessageGame.CONSTRUCTOR: {
        return false;
      }
    }
    return true;
  }

  public static int getViewCount (TdApi.MessageInteractionInfo interactionInfo) {
    return interactionInfo != null ? interactionInfo.viewCount : 0;
  }

  public static TdApi.MessageReplyInfo getReplyInfo (TdApi.MessageInteractionInfo interactionInfo) {
    return interactionInfo != null ? interactionInfo.replyInfo : null;
  }

  public static boolean isGeneralUser (TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR;
  }

  public static TdApi.InputMessageContent toInputMessageContent (String filePath, TdApi.InputFile inputFile, @NonNull FileInfo info, TdApi.FormattedText caption) {
    if (!StringUtils.isEmpty(info.mimeType)) {
      if (info.mimeType.startsWith("audio/") && !info.mimeType.equals("audio/ogg")) {
        String title = null, performer = null;
        int duration = 0;
        MediaMetadataRetriever retriever;
        try {
          retriever = U.openRetriever(filePath);
        } catch (Throwable ignored) {
          retriever = null;
        }
        if (retriever != null) {
          try {
            duration = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (StringUtils.isEmpty(performer)) {
              performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
            }
          } finally {
            U.closeRetriever(retriever);
          }
        }
        return new TdApi.InputMessageAudio(inputFile, null, duration, title, performer, caption);
      }
      if (info.mimeType.startsWith("video/")) {
        try {
          U.MediaMetadata metadata = U.getMediaMetadata(filePath);
          if (metadata != null) {
            int durationSeconds = (int) metadata.getDuration(TimeUnit.SECONDS);
            if (metadata.hasVideo) {
              int videoWidth = metadata.width;
              int videoHeight = metadata.height;
              if (U.isRotated(metadata.rotation)) {
                int temp = videoWidth;
                videoWidth = videoHeight;
                videoHeight = temp;
              }
              if (durationSeconds < 30 && info.knownSize < ByteUnit.MB.toBytes(10) && !metadata.hasAudio) {
                return new TdApi.InputMessageAnimation(inputFile, null, null, durationSeconds, videoWidth, videoHeight, null);
              } else if (durationSeconds > 0) {
                return new TdApi.InputMessageVideo(inputFile, null, null, durationSeconds, videoWidth, videoHeight, U.canStreamVideo(inputFile), caption, 0);
              }
            }
          }
        } catch (Throwable t) {
          Log.w("Cannot extract media metadata", t);
        }
      }
    }
    return new TdApi.InputMessageDocument(inputFile, null, false, caption);
  }

  public static TdApi.InputFile createInputFile (String path) {
    return createInputFile(path, null, null);
  }

  public static TdApi.InputFile createInputFile (String path, @Nullable String type) {
    return createInputFile(path, type, null);
  }

  public static boolean hasRestrictions (TdApi.ChatPermissions a, TdApi.ChatPermissions defaultPermissions) {
    return
      (a.canSendMessages != defaultPermissions.canSendMessages && defaultPermissions.canSendMessages) ||
        (a.canSendMediaMessages != defaultPermissions.canSendMediaMessages && defaultPermissions.canSendMediaMessages) ||
        (a.canSendOtherMessages != defaultPermissions.canSendOtherMessages && defaultPermissions.canSendOtherMessages) ||
        (a.canAddWebPagePreviews != defaultPermissions.canAddWebPagePreviews && defaultPermissions.canAddWebPagePreviews) ||
        (a.canSendPolls != defaultPermissions.canSendPolls && defaultPermissions.canSendPolls) ||
        (a.canInviteUsers != defaultPermissions.canInviteUsers && defaultPermissions.canInviteUsers) ||
        (a.canPinMessages != defaultPermissions.canPinMessages && defaultPermissions.canPinMessages) ||
        (a.canChangeInfo != defaultPermissions.canChangeInfo && defaultPermissions.canChangeInfo);
  }

  public static int getCombineMode (TdApi.Message message) {
    if (message != null && !isSecret(message)) {
      switch (message.content.getConstructor()) {
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageVideo.CONSTRUCTOR:
        case TdApi.MessageAnimation.CONSTRUCTOR:
          return COMBINE_MODE_MEDIA;
        case TdApi.MessageDocument.CONSTRUCTOR:
          return COMBINE_MODE_FILES;
        case TdApi.MessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_AUDIO;
      }
    }
    return COMBINE_MODE_NONE;
  }

  public static int getCombineMode (TdApi.InputMessageContent content) {
    if (content != null) {
      switch (content.getConstructor()) {
        case TdApi.InputMessagePhoto.CONSTRUCTOR:
          return ((TdApi.InputMessagePhoto) content).ttl == 0 ? COMBINE_MODE_MEDIA : COMBINE_MODE_NONE;
        case TdApi.InputMessageVideo.CONSTRUCTOR:
          return ((TdApi.InputMessageVideo) content).ttl == 0 ? COMBINE_MODE_MEDIA : COMBINE_MODE_NONE;
        case TdApi.InputMessageAnimation.CONSTRUCTOR:
          return COMBINE_MODE_MEDIA;
        case TdApi.InputMessageDocument.CONSTRUCTOR:
          return COMBINE_MODE_FILES;
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_AUDIO;
      }
    }
    return COMBINE_MODE_NONE;
  }

  public static class FileInfo {
    public long knownSize;
    public String title;
    public String mimeType;
  }

  public static TdApi.InputFile createInputFile (String path, @Nullable String type, @Nullable FileInfo out) {
    if (path != null) {
      if (path.startsWith("http://") || path.startsWith("https://")) {
        return new TdApi.InputFileRemote(path);
      }
      if (path.startsWith("content://")) {
        String fileName = null;
        int expectedSize = 0;
        long modified = Config.ALLOW_DATE_MODIFIED_RESOLVING ? 0 : System.currentTimeMillis();
        Uri uri = Uri.parse(path);

        Cursor c = null;
        try {
          String[] columns = Config.ALLOW_DATE_MODIFIED_RESOLVING ?
          new String[] {
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
          } : new String[] {
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
          };
          c = UI.getContext().getContentResolver().query(uri, columns, null, null, null);
          if (c != null && c.moveToFirst()) {
            String displayName = c.getString(0).trim();
            long size = c.getInt(1);
            String mimeType = null; try { mimeType = c.getString(2).trim(); } catch (Throwable ignored) { }
            if (Config.ALLOW_DATE_MODIFIED_RESOLVING) {
              try { modified = Math.max(0, c.getLong(3)); } catch (Throwable ignored) { }
            }

            if (!StringUtils.isEmpty(displayName)) {
              fileName = displayName;
              if (!StringUtils.isEmpty(mimeType)) {
                String originalExtension = U.getExtension(path);
                if (!BuildConfig.THEME_FILE_EXTENSION.equals(originalExtension)) {
                  String extension = TGMimeType.extensionForMimeType(mimeType);
                  if (!StringUtils.isEmpty(extension) && StringUtils.isEmpty(U.getExtension(fileName))) {
                    fileName = fileName + "." + extension;
                  }
                }
              }
            }
            if (size > 0) {
              expectedSize = (int) size;
            }
          }
        } catch (Throwable t) {
          Log.w("Cannot resolve display name/size/mime", t);
        }
        if (c != null) {
          try { c.close(); } catch (Throwable ignored) {}
        }

        if (StringUtils.isEmpty(fileName)) {
          fileName = uri.getLastPathSegment();
        }

        if (StringUtils.isEmpty(fileName)) {
          fileName = Lang.getString(R.string.File);
        }

        if (!StringUtils.isEmpty(type)) {
          String originalExtension = U.getExtension(fileName);
          String originalMimeType = TGMimeType.mimeTypeForExtension(originalExtension);
          String extension = TGMimeType.extensionForMimeType(type);
          if (!StringUtils.isEmpty(extension) && !extension.equals(originalExtension) && !"bin".equals(extension) && !BuildConfig.THEME_FILE_EXTENSION.equals(originalExtension) && !StringUtils.equalsOrBothEmpty(originalMimeType, type)) {
            fileName = fileName + "." + extension;
          }
        }

        if (out != null) {
          out.title = fileName;
          String mimeType = TGMimeType.mimeTypeForExtension(U.getExtension(fileName));
          if (StringUtils.isEmpty(out.mimeType)) {
            out.mimeType = StringUtils.isEmpty(mimeType) ? type : mimeType;
          }
          out.knownSize = expectedSize;
        }

        Log.i("Generating file, path: %s, name: %s, expectedSize: %d", path, fileName, expectedSize);

        return newGeneratedFile(fileName, path, expectedSize, modified);
      }
    }
    if (out != null) {
      try {
        String fileName = Uri.parse(path).getLastPathSegment();
        out.title = fileName;
        if (StringUtils.isEmpty(out.mimeType)) {
          String mimeType = TGMimeType.mimeTypeForExtension(U.getExtension(fileName));
          out.mimeType = StringUtils.isEmpty(mimeType) ? type : mimeType;
        }
      } catch (Throwable ignored) {}
    }
    return new TdApi.InputFileLocal(path);
  }

  public static TdApi.InputFileGenerated createFileCopy (TdApi.File file) {
    return new TdApi.InputFileGenerated(file.local.path, "copy" + GenerationInfo.randomStamp(), file.size);
  }

  public static TdApi.InputFileGenerated newGeneratedFile (@Nullable String fileName, String path, int expectedSize, long modifiedDate) {
    return new TdApi.InputFileGenerated(fileName, path + "," + expectedSize + "_" + modifiedDate, expectedSize);
  }

  public static String getPrivacyRulesString (Tdlib tdlib, @TdApi.UserPrivacySetting.Constructors int privacyKey, PrivacySettings privacy) {
    int ruleType = privacy.getMode();
    int minus = privacy.getMinusTotalCount(tdlib);
    int plus = privacy.getPlusTotalCount(tdlib);

    int nobodyExceptRes, nobodyRes;
    int contactsExceptRes, contactsRes;
    int everybodyExceptRes, everybodyRes;

    switch (privacyKey) {
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyShowNumberNobodyExcept;
        nobodyRes = R.string.PrivacyShowNumberNobody;
        contactsExceptRes = R.string.PrivacyShowNumberContactsExcept;
        contactsRes = R.string.PrivacyShowNumberContacts;
        everybodyExceptRes = R.string.PrivacyShowNumberEverybodyExcept;
        everybodyRes = R.string.PrivacyShowNumberEverybody;
        break;
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        nobodyExceptRes = contactsExceptRes = R.string.PrivacyAllowFindingContactsExcept;
        nobodyRes = contactsRes = R.string.PrivacyAllowFindingContacts;
        everybodyExceptRes = R.string.PrivacyAllowFindingEverybodyExcept;
        everybodyRes = R.string.PrivacyAllowFindingEverybody;
        break;
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyAddToGroupsNobodyExcept;
        nobodyRes = R.string.PrivacyAddToGroupsNobody;
        contactsExceptRes = R.string.PrivacyAddToGroupsContactsExcept;
        contactsRes = R.string.PrivacyAddToGroupsContacts;
        everybodyExceptRes = R.string.PrivacyAddToGroupsEverybodyExcept;
        everybodyRes = R.string.PrivacyAddToGroupsEverybody;
        break;
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyLastSeenNobodyExcept;
        nobodyRes = R.string.PrivacyLastSeenNobody;
        contactsExceptRes = R.string.PrivacyLastSeenContactsExcept;
        contactsRes = R.string.PrivacyLastSeenContacts;
        everybodyExceptRes = R.string.PrivacyLastSeenEverybodyExcept;
        everybodyRes = R.string.PrivacyLastSeenEverybody;
        break;
      case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyCallsNobodyExcept;
        nobodyRes = R.string.PrivacyCallsNobody;
        contactsExceptRes = R.string.PrivacyCallsContactsExcept;
        contactsRes = R.string.PrivacyCallsContacts;
        everybodyExceptRes = R.string.PrivacyCallsEverybodyExcept;
        everybodyRes = R.string.PrivacyCallsEverybody;
        break;
      case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyP2PNobodyExcept;
        nobodyRes = R.string.PrivacyP2PNobody;
        contactsExceptRes = R.string.PrivacyP2PContactsExcept;
        contactsRes = R.string.PrivacyP2PContacts;
        everybodyExceptRes = R.string.PrivacyP2PEverybodyExcept;
        everybodyRes = R.string.PrivacyP2PEverybody;
        break;
      case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyForwardLinkNobodyExcept;
        nobodyRes = R.string.PrivacyForwardLinkNobody;
        contactsExceptRes = R.string.PrivacyForwardLinkContactsExcept;
        contactsRes = R.string.PrivacyForwardLinkContacts;
        everybodyExceptRes = R.string.PrivacyForwardLinkEverybodyExcept;
        everybodyRes = R.string.PrivacyForwardLinkEverybody;
        /*if (ruleType == TYPE_NOBODY && plus > 0 && minus == 0) {
          return Lang.plural(R.string.PrivacyForwardLinkNobodyExcept, plus);
        } else if (ruleType == TYPE_EVERYBODY && minus > 0 && plus == 0) {
          return Lang.plural(R.string.PrivacyForwardLinkEverybodyExcept, minus);
        }*/
        break;
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyPhotoNobodyExcept;
        nobodyRes = R.string.PrivacyPhotoNobody;
        contactsExceptRes = R.string.PrivacyPhotoContactsExcept;
        contactsRes = R.string.PrivacyPhotoContacts;
        everybodyExceptRes = R.string.PrivacyPhotoEverybodyExcept;
        everybodyRes = R.string.PrivacyPhotoEverybody;
        break;
      default:
        throw new IllegalArgumentException("privacyKey == " + privacyKey);
    }

    int res, exceptRes;
    switch (ruleType) {
      case PrivacySettings.MODE_NOBODY:
        res = nobodyRes;
        exceptRes = nobodyExceptRes;
        break;
      case PrivacySettings.MODE_CONTACTS:
        res = contactsRes;
        exceptRes = contactsExceptRes;
        break;
      case PrivacySettings.MODE_EVERYBODY:
        res = everybodyRes;
        exceptRes = everybodyExceptRes;
        break;
      default:
        throw new IllegalArgumentException("ruleType == " + ruleType);
    }

    String exception = plus > 0 && minus > 0 ? Lang.getString(R.string.format_minusPlus, minus, plus) :
                       minus > 0 ? Lang.getString(R.string.format_minus, minus) :
                       plus > 0 ? Lang.getString(R.string.format_plus, plus) : null;
    if (exception != null) {
      return exceptRes != 0 ? Lang.getString(exceptRes, exception) : Lang.getString(res) + " " + exception;
    } else {
      return Lang.getString(res);
    }
  }

  public static boolean isPrivateChat (TdApi.ChatType info) {
    return info.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR;
  }

  public static boolean isSecretChat (TdApi.ChatType type) {
    return type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR;
  }

  public static boolean hasInstantView (TdApi.WebPage webPage) {
    return webPage != null && hasInstantView(webPage.instantViewVersion);
  }

  public static boolean hasInstantView (int version) {
    return version > 0 && version <= Config.SUPPORTED_INSTANT_VIEW_VERSION;
  }

  public static long[] getMessageIds (TdApi.Message[] messages, int index, int count) {
    long[] messageIds = new long[count];
    for (int i = 0; i < count; i++) {
      messageIds[i] = messages[index + i].id;
    }
    return messageIds;
  }

  public static boolean forwardMessages (long toChatId, TdApi.Message[] messages, boolean sendCopy, boolean removeCaption, TdApi.MessageSendOptions options, ArrayList<TdApi.Function> out) {
    if (messages.length == 0) {
      return false;
    }
    long fromChatId = 0;
    int index = 0;
    int size = 0;
    for (TdApi.Message message : messages) {
      if (message.chatId != fromChatId) {
        if (size > 0) {
          out.add(new TdApi.ForwardMessages(toChatId, fromChatId, getMessageIds(messages, index, size), options, sendCopy, removeCaption, false));
        }
        fromChatId = message.chatId;
        index += size;
        size = 0;
      }
      size++;
    }
    if (size > 0) {
      out.add(new TdApi.ForwardMessages(toChatId, fromChatId, getMessageIds(messages, index, size), options, sendCopy, removeCaption, false));
    }
    return true;
  }

  public static int getLastSeen (TdApi.User user) {
    if (user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR) {
      return 0;
    }
    switch (user.status.getConstructor()) {
      case TdApi.UserStatusRecently.CONSTRUCTOR: {
        return (int) (System.currentTimeMillis() / 1000l) - 259200; // now 3 days
      }
      case TdApi.UserStatusLastWeek.CONSTRUCTOR: {
        return (int) (System.currentTimeMillis() / 1000l) - 518400; // now -6 days
      }
      case TdApi.UserStatusLastMonth.CONSTRUCTOR: {
        return (int) (System.currentTimeMillis() / 1000l) - 1209600; // now -2 weeks
      }
      case TdApi.UserStatusOnline.CONSTRUCTOR: {
        return ((TdApi.UserStatusOnline) user.status).expires;
      }
      case TdApi.UserStatusOffline.CONSTRUCTOR: {
        return ((TdApi.UserStatusOffline) user.status).wasOnline;
      }
      case TdApi.UserStatusEmpty.CONSTRUCTOR:
      default: {
        return 0;
      }
    }
  }

  public static boolean isUserDeleted (@Nullable TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeDeleted.CONSTRUCTOR;
  }

  public static String getUserName (long userId, @Nullable TdApi.User user) {
    if (userId == TdConstants.TELEGRAM_ACCOUNT_ID) {
      return "Telegram";
    }
    if (user == null) {
      return "User#" + userId;
    }
    if (user.type != null && user.type.getConstructor() == TdApi.UserTypeDeleted.CONSTRUCTOR) {
      return Lang.getString(R.string.HiddenName);
    }
    return getUserName(user.firstName, user.lastName);
  }

  public static String getUserName (@Nullable TdApi.User user) {
    return user == null ? "NULL" : getUserName(user.id, user);
  }

  public static String getUserName (String firstName, String lastName) {
    if (StringUtils.isEmpty(firstName))
      return lastName;
    if (StringUtils.isEmpty(lastName))
      return firstName;
    return firstName + ' ' + lastName;
  }

  public static String getUserSingleName (long userId, @Nullable TdApi.User user) {
    if (userId != 0 && user == null) {
      return "User#" + userId;
    }
    return getFirstName(user);
  }

  public static Letters getLetters () {
    return getLetters(null, null, "?");
  }

  private static boolean isGoodLetter (int codePoint, int codePointType) {
    switch (codePointType) {
      case Character.START_PUNCTUATION:
      case Character.END_PUNCTUATION:
      case Character.CONNECTOR_PUNCTUATION:
      case Character.FINAL_QUOTE_PUNCTUATION:
      case Character.INITIAL_QUOTE_PUNCTUATION:
      case Character.OTHER_PUNCTUATION:
      case Character.DASH_PUNCTUATION:
      case Character.SPACE_SEPARATOR:
        return false;
    }

    // TODO?

    return true;
  }

  private static String pickLetter (@Nullable String in, boolean allowTwo, boolean force) {
    if (StringUtils.isEmpty(in)) {
      return in;
    }
    int length = in.length();
    StringBuilder b = null;
    CharSequence emojified = org.thunderdog.challegram.emoji.Emoji.instance().replaceEmoji(in, 0, in.length(), org.thunderdog.challegram.emoji.Emoji.instance().singleLimiter());
    if (emojified instanceof Spanned) {
      Spanned spanned = (Spanned) emojified;
      EmojiSpan[] spans;
      try {
        spans = spanned.getSpans(0, spanned.length(), EmojiSpan.class);
      } catch (ArrayStoreException e) {
        Log.w("Android Bug", e);
        spans = null;
      }
      if (spans != null && spans.length > 0) {
        int start = spanned.getSpanStart(spans[0]);
        int end = spanned.getSpanEnd(spans[0]);
        if (end > start) {
          if (start == 0) {
            return in.substring(0, end);
          }
          length = start;
        }
      }
    }

    for (int i = 0; i < length;) {
      int codePoint = in.codePointAt(i);
      int type = Character.getType(codePoint);
      int size = Character.charCount(codePoint);

      if (isGoodLetter(codePoint, type)) {
        if (allowTwo) {
          if (b == null) {
            b = new StringBuilder();
            b.append(in, i, i + size);
          } else {
            b.append(in, i, i + size);
            break;
          }
        } else {
          return in.substring(i, i + size).toUpperCase();
        }
      }
      i += size;
    }

    if (allowTwo && b != null) {
      return b.toString().toUpperCase();
    }

    if (force) {
      int codePoint = in.codePointAt(0);
      return in.substring(0, Character.charCount(codePoint)).toUpperCase();
    }
    return null;
  }

  public static Letters getLetters (@Nullable String part1, @Nullable String part2) {
    return getLetters(part1, part2, null);
  }

  public static Letters getLetters (final @Nullable String part1, final @Nullable String part2, String empty) {
    String letter2 = pickLetter(part2, false, false);
    if (StringUtils.isEmpty(letter2) && !StringUtils.isEmpty(part1) && part1.length() > 2) {
      int i = lastIndexOfSpace(part1, false);
      if (i != -1) {
        letter2 = pickLetter(part1.substring(i + 1), false, false);
      }
    }
    String letter1 = pickLetter(part1, false, true);
    String output;
    if (!StringUtils.isEmpty(letter1) && !StringUtils.isEmpty(letter2)) {
      output = letter1 + letter2;
    } else if (!StringUtils.isEmpty(letter1)) {
      output = letter1;
    } else if (!StringUtils.isEmpty(letter2)) {
      int i = lastIndexOfSpace(part2, false);
      if (i != -1) {
        letter1 = pickLetter(part2.substring(i + 1), false, false);
      }
      if (!StringUtils.isEmpty(letter1)) {
        output = letter2 + letter1;
      } else {
        output = letter2;
      }
    } else {
      output = StringUtils.isEmpty(empty) ? "…" : empty;
    }
    return new Letters(output);
  }

  public static TdApi.PhoneNumberAuthenticationSettings defaultPhoneNumberAuthenticationSettings () {
    return new TdApi.PhoneNumberAuthenticationSettings(
      false,
      true,
      false,
      false, // TODO for faster login when SMS method is chosen
      Settings.instance().getAuthenticationTokens()
    );
  }

  public static Letters getLetters (TdApi.User user) {
    // "…"
    if (user != null && user.type != null) {
      switch (user.type.getConstructor()) {
        case TdApi.UserTypeDeleted.CONSTRUCTOR:
          return getLetters(Lang.getString(R.string.HiddenName));
        default:
          return getLetters(user.firstName, user.lastName, "?");
      }
    }
    return new Letters("…");
  }

  public static boolean isKicked (TdApi.ChatMemberStatus status) {
    return status != null && status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR;
  }

  public static boolean isCreator (TdApi.ChatMemberStatus status) {
    return status != null && status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR;
  }

  public static boolean needUpgradeToSupergroup (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) status;
        return !StringUtils.isEmpty(creator.customTitle) || creator.isAnonymous;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) status;
        return !(admin.canChangeInfo && admin.canDeleteMessages && admin.canInviteUsers && admin.canRestrictMembers && admin.canPinMessages && admin.canManageVideoChats && !admin.canPromoteMembers && StringUtils.isEmpty(admin.customTitle) && !admin.isAnonymous);
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
        return false;
    }
    return false;
  }

  public static boolean isNotInChat (TdApi.ChatMemberStatus status) {
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR: {
          return true;
        }
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR: {
          return !((TdApi.ChatMemberStatusRestricted) status).isMember;
        }
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: {
          return !((TdApi.ChatMemberStatusCreator) status).isMember;
        }
      }
    }
    return false;
  }

  public static boolean canReturnToChat (TdApi.ChatMemberStatus status) {
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          return !((TdApi.ChatMemberStatusRestricted) status).isMember;
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return !((TdApi.ChatMemberStatusCreator) status).isMember;
      }
    }
    return false;
  }

  public static boolean isLeft (TdApi.ChatMemberStatus status) {
    return status.getConstructor() == TdApi.ChatMemberStatusLeft.CONSTRUCTOR;
  }

  public static boolean isFinished (TdApi.Call call) {
    if (call == null) {
      return true;
    }
    switch (call.state.getConstructor()) {
      case TdApi.CallStateDiscarded.CONSTRUCTOR:
      case TdApi.CallStateError.CONSTRUCTOR:
      case TdApi.CallStateHangingUp.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static boolean isActive (TdApi.Call call) {
    return !isFinished(call) && (call.isOutgoing || (call.state.getConstructor() == TdApi.CallStateExchangingKeys.CONSTRUCTOR || call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR));
  }

  public static boolean isOk (TdApi.Object object) {
    return object != null && object.getConstructor() == TdApi.Ok.CONSTRUCTOR;
  }

  public static boolean canPromoteMembers (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).canPromoteMembers;
    }
    return false;
  }

  public static boolean canAddMembers (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).canInviteUsers;
    }
    return false;
  }

  public static Letters getLetters (String in) {
    return getLetters(in, null, null);
  }

  public static TdApi.File getPhotoSmall (TdApi.User user) {
    return user.profilePhoto == null || isFileEmpty(user.profilePhoto.small) ? null : user.profilePhoto.small;
  }

  public static TdApi.File getPhotoSmall (TdApi.Chat chat) {
    return chat.photo == null || isFileEmpty(chat.photo.small) ? null : chat.photo.small;
  }

  public static boolean isFileUploaded (TdApi.File file) {
    return file != null && file.remote != null && file.remote.isUploadingCompleted;
  }

  public static boolean isFileLoading (TdApi.File file) {
    return file != null && ((file.remote != null && file.remote.isUploadingActive && !TD.isFileUploaded(file)) || (file.local != null && file.local.isDownloadingActive && !TD.isFileLoaded(file)));
  }

  public static boolean isFileLoaded (TdApi.File file) {
    return file != null && file.local != null && file.local.isDownloadingCompleted;
  }

  public static boolean isFileLoaded (TdApi.DiceStickers stickers) {
    if (stickers != null) {
      switch (stickers.getConstructor()) {
        case TdApi.DiceStickersRegular.CONSTRUCTOR:
          return isFileLoaded(((TdApi.DiceStickersRegular) stickers).sticker.sticker);
        case TdApi.DiceStickersSlotMachine.CONSTRUCTOR: {
          TdApi.DiceStickersSlotMachine slotMachine = (TdApi.DiceStickersSlotMachine) stickers;
          return isFileLoaded(slotMachine.background.sticker) &&
            isFileLoaded(slotMachine.leftReel.sticker) &&
            isFileLoaded(slotMachine.centerReel.sticker) &&
            isFileLoaded(slotMachine.rightReel.sticker) &&
            isFileLoaded(slotMachine.lever.sticker);
        }
      }
      throw new UnsupportedOperationException(stickers.toString());
    }
    return false;
  }

  public static boolean isFileLoaded (TdApi.Message message) {
    return message != null && isFileLoaded(getFile(message));
  }

  public static boolean isFileLoadedAndExists (TdApi.File file) {
    return isFileLoaded(file) && !isFileRedownloadNeeded(file);
  }

  public static TdApi.SearchMessagesFilter makeFilter (TdApi.Message message, boolean isCommon) {
    if (message == null) {
      throw new NullPointerException();
    }
    if (TD.isScheduled(message))
      return null;
    switch (message.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterAudio();
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return isCommon ? new TdApi.SearchMessagesFilterVoiceAndVideoNote() : new TdApi.SearchMessagesFilterVideoNote();
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return isCommon ? new TdApi.SearchMessagesFilterVoiceAndVideoNote() : new TdApi.SearchMessagesFilterVoiceNote();
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterAnimation();
      case TdApi.MessageDocument.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterDocument();
      case TdApi.MessageVideo.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterVideo();
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterPhoto();
    }
    return null;
  }

  public static @TdApi.SearchMessagesFilter.Constructors int makeFilterConstructor (TdApi.Message message, boolean isCommon) {
    if (message == null) {
      throw new NullPointerException();
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR:
        return TdApi.SearchMessagesFilterAudio.CONSTRUCTOR;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return isCommon ? TdApi.SearchMessagesFilterVoiceAndVideoNote.CONSTRUCTOR : TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return isCommon ? TdApi.SearchMessagesFilterVoiceAndVideoNote.CONSTRUCTOR : TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR;
      case TdApi.MessageDocument.CONSTRUCTOR:
        return TdApi.SearchMessagesFilterDocument.CONSTRUCTOR;
      case TdApi.MessageVideo.CONSTRUCTOR:
        return TdApi.SearchMessagesFilterVideo.CONSTRUCTOR;
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR;
    }
    return TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR;
  }

  public static boolean isFileRedownloadNeeded (TdApi.File file) {
    if (isFileLoaded(file)) {
      File check = new File(file.local.path);
      if (!check.exists()) {
        return true;
      }
      long length = check.length();
      return length < file.local.downloadedSize && length > 0;
    }
    return false;
  }

  public static TdApi.MessageSendOptions defaultSendOptions () {
    return new TdApi.MessageSendOptions(false, false, false, null);
  }

  public static TdApi.InputMessageAnimation toInputMessageContent (TdApi.Animation animation) {
    return new TdApi.InputMessageAnimation(new TdApi.InputFileId(animation.animation.id), null, null, animation.duration, animation.width, animation.height, null);
  }

  public static TdApi.InputMessageAudio toInputMessageContent (TdApi.Audio audio) {
    return new TdApi.InputMessageAudio(new TdApi.InputFileId(audio.audio.id), null, audio.duration, audio.title, audio.performer, null);
  }

  private static boolean isSupportedMusicMimeType (@NonNull String mimeType) {
    return
      mimeType.equals("application/ogg") ||
      mimeType.equals("audio/ogg") ||
      mimeType.equals("audio/mpeg") ||
      mimeType.equals("audio/mp4") ||
      mimeType.equals("audio/flac");
  }

  private static boolean isSupportedMusicExtension (@NonNull String extension) {
    return
      extension.equals("opus") ||
      extension.equals("mp3") ||
      extension.equals("flac") ||
      extension.equals("m4a");
  }

  public static boolean isSupportedMusic (@NonNull TdApi.Document doc) {
    if (!StringUtils.isEmpty(doc.mimeType) && isSupportedMusicMimeType(doc.mimeType)) {
      return true;
    }
    String extension = U.getExtension(doc.fileName);
    String mimeType = TGMimeType.mimeTypeForExtension(extension);
    if (!StringUtils.isEmpty(mimeType) && isSupportedMusicMimeType(mimeType)) {
      return true;
    }
    return !StringUtils.isEmpty(extension) && isSupportedMusicExtension(extension);
  }

  public static TdApi.User newFakeUser (long userId, String firstName, String lastName) {
    return new TdApi.User(userId, firstName, lastName, "", "", new TdApi.UserStatusEmpty(), null, false, false, false, false, null, false, false, true, new TdApi.UserTypeRegular(), null);
  }

  public static TdApi.Audio newFakeAudio (TdApi.Document doc) {
    return new TdApi.Audio(0, doc.fileName, "", doc.fileName, doc.mimeType, doc.minithumbnail, doc.thumbnail, doc.document);
  }

  public static TdApi.Message newFakeMessage (TdApi.Audio audio) {
    return newFakeMessage(0, null, new TdApi.MessageAudio(audio, new TdApi.FormattedText("", null)));
  }

  public static TdApi.Message newFakeMessage (TdApi.VoiceNote voiceNote) {
    return newFakeMessage(0, null, new TdApi.MessageVoiceNote(voiceNote, new TdApi.FormattedText("", null), true));
  }

  public static TdApi.Message newFakeMessage (long chatId, TdApi.MessageSender senderId, TdApi.MessageContent content) {
    TdApi.Message message = new TdApi.Message();
    message.chatId = chatId;
    message.senderId = senderId;
    message.content = content;
    return message;
  }

  public static int getFileSize (TdApi.File file) {
    return file == null ? 0 : file.size;
  }

  public static String getFilePath (TdApi.File file) {
    if (file == null) {
      return null;
    }
    if (TD.isFileLoadedAndExists(file)) {
      return file.local.path;
    }
    return null;
  }

  public static int getOffset (TdApi.TextEntity entity) {
    return entity.offset;
  }

  public static int getLength (TdApi.TextEntity entity) {
    return entity.length;
  }

  public static int getCodeLength (TdApi.AuthorizationState state) {
    if (state != null && state.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
      return getCodeLength(((TdApi.AuthorizationStateWaitCode) state).codeInfo.type);
    } else {
      return TdConstants.DEFAULT_CODE_LENGTH;
    }
  }

  public static int getCodeLength (TdApi.EmailAddressAuthenticationCodeInfo info) {
    return info.length == 0 ? 6 : info.length;
  }

  public static int getCodeLength (TdApi.AuthenticationCodeType info) {
    switch (info.getConstructor()) {
      case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR:
        return ((TdApi.AuthenticationCodeTypeCall) info).length;
      case TdApi.AuthenticationCodeTypeSms.CONSTRUCTOR:
        return ((TdApi.AuthenticationCodeTypeSms) info).length;
      case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR:
        return ((TdApi.AuthenticationCodeTypeTelegramMessage) info).length;
      case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR:
        return ((TdApi.AuthenticationCodeTypeMissedCall) info).length;
      /*case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR:
        return ((TdApi.AuthenticationCodeTypeFlashCall) info).pattern;*/
    }
    return TdConstants.DEFAULT_CODE_LENGTH;
  }

  public static boolean isFileEmpty (TdApi.File file) {
    return file == null || file.id == 0;
  }

  public static boolean isPhotoEmpty (TdApi.Photo photo) {
    return photo == null || photo.sizes == null || photo.sizes.length == 0;
  }

  public static boolean isPhotoEmpty (TdApi.ProfilePhoto photo) {
    return photo == null || isFileEmpty(photo.small);
  }

  public static long getMessageId (TdApi.Message message) {
    return message != null ? message.id : 0;
  }

  public static String getChatMemberSubtitle (Tdlib tdlib, long userId, @Nullable TdApi.User user, boolean allowBotState) {
    final long chatId = ChatId.fromUserId(userId);
    if (tdlib.isServiceNotificationsChat(chatId)) {
      return Lang.getString(R.string.ServiceNotifications);
    }
    if (tdlib.isRepliesChat(chatId)) {
      return Lang.getString(R.string.ReplyNotifications);
    }
    if (tdlib.isSelfUserId(userId)) {
      return Lang.getString(R.string.status_Online);
    }
    if (user == null) {
      return Lang.getString(R.string.UserUnavailable);
    }
    switch (user.type.getConstructor()) {
      case TdApi.UserTypeBot.CONSTRUCTOR: {
        if (allowBotState) {
          if (((TdApi.UserTypeBot) user.type).canReadAllGroupMessages) {
            return Lang.getString(R.string.BotStatusRead);
          } else {
            return Lang.getString(R.string.BotStatusCantRead);
          }
        } else {
          return "@" + user.username;
        }
      }
      case TdApi.UserTypeDeleted.CONSTRUCTOR: {
        return Lang.getString(R.string.deletedUser);
      }
      case TdApi.UserTypeUnknown.CONSTRUCTOR: {
        return Lang.getString(R.string.unknownUser);
      }
    }
    return Lang.getUserStatus(tdlib, user.status, true);
  }

  public static boolean isOnline (TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR && TD.isOnline(user.status);
  }

  public static boolean isOnline (TdApi.UserStatus status) {
    return status != null && status.getConstructor() == TdApi.UserStatusOnline.CONSTRUCTOR;
  }

  public static String findLink (TdApi.FormattedText text) {
    if (text.entities == null) {
      return null;
    }
    String url = null;
    for (TdApi.TextEntity entity : text.entities) {
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
          return text.text.substring(entity.offset, entity.offset + entity.length);
        }
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
          url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          break;
        }
      }
    }
    return url;
  }

  public static String findLink (TdApi.MessageText text) {
    if (text.webPage != null) {
      return text.webPage.url;
    }
    if (text.text.entities == null) {
      return null;
    }
    return findLink(text.text);
  }

  public static boolean needMuteIcon (TdApi.ChatNotificationSettings settings, TdApi.ScopeNotificationSettings surroundingScope) {
    if (settings != null) {
      if (settings.useDefaultMuteFor) {
        return false; // surroundingScope != null && surroundingScope.muteFor > 0;
      } else {
        return settings.muteFor > 0;
      }
    }
    return false;
  }

  public static int errorCode (TdApi.Object object) {
    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      return ((TdApi.Error) object).code;
    }
    return 0;
  }

  public static String errorText (TdApi.Object object) {
    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      return ((TdApi.Error) object).message;
    }
    return "";
  }

  public static String toErrorString (@Nullable TdApi.Object object) {
    if (object == null)
      return "Unknown error (null)";
    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      TdApi.Error error = (TdApi.Error) object;
      return translateError(error.code, error.message);
    }
    return "not an error";
  }

  /*public static String makeErrorString (TonApi.Object object) {
    if (object.getConstructor() == TonApi.Error.CONSTRUCTOR) {
      TonApi.Error error = (TonApi.Error) object;
      return translateError(error.code, error.message);
    }
    return "not an error";
  }*/

  public static final String ERROR_USER_PRIVACY = "USER_PRIVACY_RESTRICTED";
  public static final String ERROR_USER_CHANNELS_TOO_MUCH = "USER_CHANNELS_TOO_MUCH";
  public static final String ERROR_CHANNELS_ADMIN_PUBLIC_TOO_MUCH = "CHANNELS_ADMIN_PUBLIC_TOO_MUCH";
  public static final String ERROR_CHANNELS_ADMIN_LOCATED_TOO_MUCH = "CHANNELS_ADMIN_LOCATED_TOO_MUCH";

  public static @Nullable String translateError (int code, String message) {
    if (StringUtils.isEmpty(message)) {
      return null;
    }
    if (message.equalsIgnoreCase("request aborted")) {
      return null;
    }
    int res;
    switch (message) {
      case "USER_RESTRICTED": res = R.string.UserRestricted; break;
      case "USERNAME_INVALID": res = R.string.UsernameInvalid; break;
      case "USERNAME_OCCUPIED": res = R.string.UsernameInUse; break;
      case "USERNAME_NOT_OCCUPIED": res = R.string.UsernameNotOccupiedUnknown; break;
      case "USERNAMES_UNAVAILABLE": res = R.string.FeatureUnavailable; break;
      case ERROR_USER_CHANNELS_TOO_MUCH: res = R.string.error_CHANNELS_TOO_MUCH; break;
      case ERROR_CHANNELS_ADMIN_PUBLIC_TOO_MUCH: res = R.string.TooManyPublicChannels; break;
      case ERROR_CHANNELS_ADMIN_LOCATED_TOO_MUCH: res = R.string.error_CHANNELS_ADMIN_LOCATED_TOO_MUCH; break;
      case "PASSWORD_HASH_INVALID": res = R.string.PasswordIsInvalid; break;
      case "INVITE_HASH_INVALID": case "INVITE_HASH_EXPIRED": res = R.string.InviteLinkInvalid; break;
      case "USERS_TOO_MUCH": res = R.string.GroupIsFull; break;
      case "USERS_TOO_FEW": res = R.string.ErrorUsersTooFew; break;
      case "PHONE_NUMBER_INVALID": res = R.string.login_InvalidPhone; break;
      case "FRESH_RESET_AUTHORISATION_FORBIDDEN": res = R.string.TerminateSessionFreshError; break;
      case "PHONE_NUMBER_OCCUPIED": res = R.string.PhoneNumberInUse; break;
      case "PHONE_NUMBER_BANNED": res = R.string.login_PHONE_NUMBER_BANNED; break;
      case ERROR_USER_PRIVACY: res = R.string.UserPrivacyRestricted; break;
      case "USER_NOT_MUTUAL_CONTACT": res = R.string.error_USER_NOT_MUTUAL_CONTACT; break;
      case "CHAT_SEND_POLL_FORBIDDEN": res = R.string.error_CHAT_SEND_POLL_FORBIDDEN; break;
      case "LANG_CODE_NOT_SUPPORTED": res = R.string.error_LANG_CODE_NOT_SUPPORTED; break;
      case "APP_UPGRADE_NEEDED": res = R.string.error_APP_UPGRADE_NEEDED; break;
      case "INPUT_USER_DEACTIVATED": res = R.string.ErrorUserDeleted; break;
      case "Top chats computation is disabled": res = R.string.ChatSuggestionsDisabled; break;
      case "CHAT_ADMIN_REQUIRED": res = R.string.error_CHAT_ADMIN_REQUIRED; break;
      case "PEER_FLOOD": res = R.string.NobodyLikesSpam2; break;
      case "STICKERSET_INVALID": res = R.string.error_STICKERSET_INVALID; break;
      case "CHANNELS_TOO_MUCH": res = R.string.error_CHANNELS_TOO_MUCH; break;
      case "BOTS_TOO_MUCH": res = R.string.error_BOTS_TOO_MUCH; break;
      case "ADMINS_TOO_MUCH": res = R.string.error_ADMINS_TOO_MUCH; break;
      case "Not enough rights to invite members to the group chat": res = R.string.YouCantInviteMembers; break;
      case "Invalid chat identifier specified": res = R.string.error_ChatInfoNotFound; break;
      case "Message must be non-empty": res = R.string.MessageInputEmpty; break;
      case "Not Found": res = R.string.error_NotFound; break;
      case "The maximum number of pinned chats exceeded": return Lang.plural(R.string.ErrorPinnedChatsLimit, TdlibManager.instance().current().pinnedChatsMaxCount());
      default: {
        String lookup = StringUtils.toCamelCase(message);
        if (lookup.matches("^[A-Za-z0-9_]+$")) {
          String error = Lang.getErrorString(lookup);
          if (error != null)
            return error;
        }
        res = 0;
        break;
      }
    }
    if (res != 0) {
      return Lang.getString(res);
    }
    int floodSeconds = getFloodErrorSeconds(code, message, -1);
    if (floodSeconds > 0) {
      return Lang.getString(R.string.format_TooManyRequests, Lang.getTryAgainIn(floodSeconds));
    }
    return "#" + code + ": " + message;
  }

  public static int getFloodErrorSeconds (int code, String message, int defaultValue) {
    if (code == 429 && message.startsWith("Too Many Requests: retry after ")) {
      return StringUtils.parseInt(message.substring("Too Many Requests: retry after ".length()));
    }
    return defaultValue;
  }

  public static SparseIntArray calculateCounters (Map<String, TdApi.Message> map) {
    SparseIntArray result = new SparseIntArray();
    for (TdApi.Message message : map.values()) {
      int constructor = message.content.getConstructor();
      result.put(constructor, result.get(constructor) + 1);
    }
    return result;
  }

  public static boolean isOut (@Nullable TdApi.Message msg) {
    return msg != null && msg.isOutgoing;
  }

  public static boolean matchUsername (String s) {
    int length = s.length();
    for (int i = 0; i < length; i++) {
      if (!TD.matchUsername(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static long getChatId (TdApi.Message[] messages) {
    if (messages != null && messages.length > 0) {
      long chatId = messages[0].chatId;
      for (TdApi.Message message : messages) {
        if (message.chatId != chatId) {
          return 0;
        }
      }
      return chatId;
    }
    return 0;
  }

  public static TdApi.MessageSender getSender (TdApi.Message[] messages) {
    if (messages != null && messages.length > 0) {
      TdApi.MessageSender sender = messages[0].senderId;
      for (TdApi.Message message : messages) {
        if (!Td.equalsTo(sender, message.senderId)) {
          return null;
        }
      }
      return sender;
    }
    return null;
  }

  public static LongSparseArray<long[]> getMessageIds (TdApi.Message[] messages) {
    LongSparseArray<LongList> temp = new LongSparseArray<>();
    LongList list = null;
    long chatId = 0;
    int remainingCount = messages.length;
    for (TdApi.Message message : messages) {
      if (message.chatId != chatId) {
        chatId = message.chatId;
        list = temp.get(chatId);
        if (list == null) {
          temp.put(chatId, list = new LongList(remainingCount));
        }
      }
      list.append(message.id);
    }
    LongSparseArray<long[]> result = new LongSparseArray<>(temp.size());
    for (int i = 0; i < temp.size(); i++) {
      result.append(temp.keyAt(i), temp.valueAt(i).get());
    }
    return result;
  }

  public static long getChatId (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Chat.CONSTRUCTOR:
        return ((TdApi.Chat) object).id;
    }
    return 0;
  }

  public static String getText (TdApi.RichText richText) {
    StringBuilder out = new StringBuilder();
    getText(richText, out);
    return out.toString();
  }

  public static void getText (TdApi.RichText richText, StringBuilder b) {
    if (richText == null)
      return;
    switch (richText.getConstructor()) {
      case TdApi.RichTextBold.CONSTRUCTOR: {
        getText(((TdApi.RichTextBold) richText).text, b);
        break;
      }
      case TdApi.RichTextStrikethrough.CONSTRUCTOR: {
        getText(((TdApi.RichTextStrikethrough) richText).text, b);
        break;
      }
      case TdApi.RichTextUnderline.CONSTRUCTOR: {
        getText(((TdApi.RichTextUnderline) richText).text, b);
        break;
      }
      case TdApi.RichTextUrl.CONSTRUCTOR: {
        getText(((TdApi.RichTextUrl) richText).text, b);
        break;
      }
      case TdApi.RichTextFixed.CONSTRUCTOR: {
        getText(((TdApi.RichTextFixed) richText).text, b);
        break;
      }
      case TdApi.RichTextEmailAddress.CONSTRUCTOR: {
        getText(((TdApi.RichTextEmailAddress) richText).text, b);
        break;
      }
      case TdApi.RichTextItalic.CONSTRUCTOR: {
        getText(((TdApi.RichTextItalic) richText).text, b);
        break;
      }
      case TdApi.RichTextPlain.CONSTRUCTOR: {
        b.append(((TdApi.RichTextPlain) richText).text);
        break;
      }
      case TdApi.RichTexts.CONSTRUCTOR: {
        TdApi.RichTexts richTexts = (TdApi.RichTexts) richText;
        for (TdApi.RichText text : richTexts.texts) {
          getText(text, b);
        }
        break;
      }
    }
  }

  public static void shareLink (TdlibDelegate context, String link) {
    ShareController c = new ShareController(context.context(), context.tdlib());
    ShareController.Args args = new ShareController.Args(link);
    if (Strings.isValidLink(link)) {
      args.setExport(link);
    }
    c.setArguments(args);
    c.show();
  }

  public static int getAnchorMode (int date) {
    return getAnchorMode(date, true);
  }

  public static int getAnchorMode (int date, boolean allowWeek) {
    return allowWeek && DateUtils.isPastWeek(date) ? 4 + Calendar.SATURDAY : DateUtils.isWithinWeek(date) ? (DateUtils.isToday(date, TimeUnit.SECONDS) ? 1 : DateUtils.isYesterday(date, TimeUnit.SECONDS) ? 2 : 3 + DateUtils.getDayOfWeek(date)) : 0;
  }

  public static boolean shouldSplitDatesByDay (int anchorMode, int prevDate, int sourceDate) {
    return anchorMode == 0 && !DateUtils.isSameDay(sourceDate, prevDate);
  }

  public static boolean shouldSplitDatesByMonth (int anchorMode, int prevDate, int sourceDate) {
    return anchorMode == 0 && !DateUtils.isSameMonth(sourceDate, prevDate);
  }

  public static boolean getCallNeedsFlashing (TdApi.Call call) {
    return !TD.isFinished(call) && call.state.getConstructor() != TdApi.CallStateReady.CONSTRUCTOR;
  }

  public static boolean isAcceptedOnOtherDevice (TdApi.Call call) {
    return call != null && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonEmpty.CONSTRUCTOR;
  }

  public static boolean isCancelled (TdApi.Call call) {
    return call != null && call.isOutgoing && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR;
  }

  public static boolean isCallMissedOrCancelled (TdApi.CallDiscardReason reason) {
    return reason.getConstructor() == TdApi.CallDiscardReasonDeclined.CONSTRUCTOR || reason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR;
  }

  public static boolean isDeclined (TdApi.Call call) {
    return call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonDeclined.CONSTRUCTOR;
  }

  public static boolean isMissed (TdApi.Call call) {
    return call != null && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR;
  }

  public static String getCallState2 (TdApi.Call call, TdApi.CallState prevState, long callDuration, boolean needShort) {
    switch (prevState.getConstructor()) {
      case TdApi.CallStatePending.CONSTRUCTOR:
        return Lang.getString(call.isOutgoing ? R.string.VoipCancelled : R.string.VoipDeclined);
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
      case TdApi.CallStateReady.CONSTRUCTOR:
        return Lang.getString(R.string.VoipEnded);
      default:
        return getCallState(call, callDuration, needShort);
    }
  }

  public static String getCallState (TdApi.Call call, long callDuration, boolean needShort) {
    switch (call.state.getConstructor()) {
      case TdApi.CallStatePending.CONSTRUCTOR:
        TdApi.CallStatePending state = (TdApi.CallStatePending) call.state;
        if (!call.isOutgoing) {
          return Lang.getString(R.string.IncomingCall);
        } else if (!state.isCreated) {
          return Lang.getString(R.string.VoipConnecting);
        } else if (!state.isReceived) {
          return Lang.getString(R.string.VoipWaiting);
        } else {
          return Lang.getString(R.string.VoipRinging);
        }
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
        return Lang.getString(needShort ? R.string.VoipExchangingKeysShort : R.string.VoipExchangingKeys);
      case TdApi.CallStateError.CONSTRUCTOR:
        TdApi.Error error = ((TdApi.CallStateError) call.state).error;
        if (error.code == 4005000) {
          return Lang.getString(R.string.VoipMissedOutgoing);
        } else {
          return Lang.getString(R.string.VoipFailed);
        }
      case TdApi.CallStateReady.CONSTRUCTOR:
        if (callDuration < 0) {
          return Lang.getString(R.string.VoipEstablishing);
        } else {
          return Strings.buildDuration(callDuration);
        }
      case TdApi.CallStateHangingUp.CONSTRUCTOR:
        return Lang.getString(R.string.VoipEnded);
      case TdApi.CallStateDiscarded.CONSTRUCTOR:
        switch (((TdApi.CallStateDiscarded) call.state).reason.getConstructor()) {
          case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
            return Lang.getString(call.isOutgoing ? R.string.VoipBusy : R.string.VoipDeclined);
          case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
            return Lang.getString(R.string.VoipEnded);
          case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
            return Lang.getString(call.isOutgoing ? R.string.VoipCancelled : R.string.VoipMissed);
          case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
            return Lang.getString(R.string.VoipDisconnect);
            /*if (callDuration <= 0) {

            } else {
              return Strings.buildDuration((int) ((SystemClock.elapsedRealtime() - callStartTime) / 1000l)) + " " + UI.getString(R.string.CallStateDisconnect);
            }*/
          case TdApi.CallDiscardReasonEmpty.CONSTRUCTOR:
            return Lang.getString(R.string.VoipUnknown);
        }
        return Lang.getString(R.string.Busy);
    }
    throw new IllegalArgumentException("call.state == " + call.state);
  }

  public static @RawRes int getCallStateSound (TdApi.Call call) {
    switch (call.state.getConstructor()) {
      case TdApi.CallStatePending.CONSTRUCTOR:
        TdApi.CallStatePending state = (TdApi.CallStatePending) call.state;
        if (!call.isOutgoing) {
          return 0; // Incoming call
        } else if (!state.isCreated) {
          return R.raw.voip_connecting; // Connecting
        } else if (!state.isReceived) {
          return R.raw.voip_connecting; // Waiting
        } else {
          return R.raw.voip_ringback; // Ringing
        }
      case TdApi.CallStateExchangingKeys.CONSTRUCTOR:
        return call.isOutgoing ? R.raw.voip_connecting : 0; // Exchanging keys
      case TdApi.CallStateError.CONSTRUCTOR:
        TdApi.Error error = ((TdApi.CallStateError) call.state).error;
        if (error.code == 4005000) {
          return 0; // Call missed (outgoing)
        } else {
          return R.raw.voip_fail; // Failed to connect
        }
      case TdApi.CallStateReady.CONSTRUCTOR:
        return 0; // Establishing / 0:00
      case TdApi.CallStateHangingUp.CONSTRUCTOR:
        return 0;
      case TdApi.CallStateDiscarded.CONSTRUCTOR:
        switch (((TdApi.CallStateDiscarded) call.state).reason.getConstructor()) {
          case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
            return call.isOutgoing ? R.raw.voip_busy : 0; // Line busy / Call cancelled
          case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
            return R.raw.voip_end; // Call ended
          case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
            return call.isOutgoing ? 0 : R.raw.voip_end; // Call cancelled / Call missed
          case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
            return R.raw.voip_fail; // Failed to connect
          case TdApi.CallDiscardReasonEmpty.CONSTRUCTOR:
            return 0; // Accepted on other device
        }
        return R.raw.voip_busy;
    }
    throw new IllegalArgumentException("call.state == " + call.state);
  }

  public static class BotTransferInfo {
    public final int botUserId;
    public final int targetOwnerUserId;

    public BotTransferInfo (int botUserId, int targetOwnerUserId) {
      this.botUserId = botUserId;
      this.targetOwnerUserId = targetOwnerUserId;
    }
  }

  public static BotTransferInfo parseBotTransferInfo (TdApi.InlineKeyboardButtonTypeCallbackWithPassword callbackWithPassword) {
    String info = new String(callbackWithPassword.data, StringUtils.UTF_8);
    if (info.matches("^bots/[0-9]+/trsf/[0-9]+/c$")) {
      String[] args = info.split("/");
      return new BotTransferInfo(StringUtils.parseInt(args[1]), StringUtils.parseInt(args[3]));
    }
    return null;
  }

  public static final int PROMOTE_MODE_NONE = 0;
  public static final int PROMOTE_MODE_NEW = 1;
  public static final int PROMOTE_MODE_EDIT = 2;
  public static final int PROMOTE_MODE_VIEW = 3;

  public static int canPromoteAdmin (TdApi.ChatMemberStatus myStatus, TdApi.ChatMemberStatus otherStatus) {
    if (otherStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      return PROMOTE_MODE_VIEW;
    }
    switch (myStatus.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: {
        if (otherStatus.getConstructor() != TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
          return PROMOTE_MODE_NEW;
        } else {
          return PROMOTE_MODE_EDIT;
        }
      }
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
        if (otherStatus.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
          if (((TdApi.ChatMemberStatusAdministrator) otherStatus).canBeEdited) {
            return PROMOTE_MODE_EDIT;
          } else {
            return PROMOTE_MODE_VIEW;
          }
        } else if (((TdApi.ChatMemberStatusAdministrator) myStatus).canPromoteMembers) {
          return PROMOTE_MODE_NEW;
        }
        break;
      }
      default: {
        if (otherStatus.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
          return PROMOTE_MODE_VIEW;
        }
        break;
      }
    }
    return PROMOTE_MODE_NONE;
  }

  public static final int RESTRICT_MODE_NONE = 0;
  public static final int RESTRICT_MODE_NEW = 1;
  public static final int RESTRICT_MODE_EDIT = 2;
  public static final int RESTRICT_MODE_VIEW = 3;

  public static int canRestrictMember (TdApi.ChatMemberStatus me, TdApi.ChatMemberStatus him) {
    if (him.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      return RESTRICT_MODE_NONE;
    }
    switch (me.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        switch (him.getConstructor()) {
          case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
            return RESTRICT_MODE_EDIT;
          default:
            return RESTRICT_MODE_NEW;
        }
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        if (((TdApi.ChatMemberStatusAdministrator) me).canRestrictMembers) {
          switch (him.getConstructor()) {
            case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
              if (((TdApi.ChatMemberStatusAdministrator) him).canBeEdited) {
                return RESTRICT_MODE_NEW;
              } else {
                return RESTRICT_MODE_NONE;
              }
            case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
            case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
              return RESTRICT_MODE_EDIT;
            case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
            case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
              return RESTRICT_MODE_NEW;
            case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
              return RESTRICT_MODE_NONE;
          }
        }
        break;
      default:
        if (him.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR) {
          return RESTRICT_MODE_VIEW;
        }
        break;
    }
    return RESTRICT_MODE_NONE;
  }

  public static String getTelegramHost () {
    return TdConstants.TELEGRAM_HOSTS[0];
  }

  public static byte[] newRandomWaveform () {
    return new byte[] {0, 4, 17, -50, -93, 86, -103, -45, -12, -26, 63, -25, -3, 109, -114, -54, -4, -1,
      -1, -1, -1, -29, -1, -1, -25, -1, -1, -97, -43, 57, -57, -108, 1, -91, -4, -47, 21, 99, 10, 97, 43,
      45, 115, -112, -77, 51, -63, 66, 40, 34, -122, -116, 48, -124, 16, 66, -120, 16, 68, 16, 33, 4, 1};
    /*Random random = new Random(); // 63, -124, 115
    byte[] waveform = new byte[63];
    random.nextBytes(waveform);
    int i = 0;
    for (byte x : waveform) {
      waveform[i] = (byte) ((float) waveform[i] * Anim.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation((float) i / (float) waveform.length));
      i++;
    }
    return waveform;*/
  }

  public static void processAlbum (Tdlib tdlib, long chatId, TdApi.MessageSendOptions options, List<TdApi.Function> functions, List<TdApi.InputMessageContent> album) {
    if (album == null || album.isEmpty())
      return;
    if (album.size() == 1) {
      processSingle(tdlib, chatId, options, functions, album.get(0));
    } else {
      TdApi.InputMessageContent[] array = new TdApi.InputMessageContent[album.size()];
      album.toArray(array);
      functions.add(new TdApi.SendMessageAlbum(chatId, 0, 0, options, array));
    }
    album.clear();
  }

  public static List<TdApi.Function> toFunctions (long chatId, long messageThreadId, long replyToMessageId, TdApi.MessageSendOptions options, TdApi.InputMessageContent[] content, boolean needGroupMedia) {
    if (content.length == 0)
      return Collections.emptyList();

    List<TdApi.Function> functions = new ArrayList<>();

    int remaining = content.length;
    int processed = 0;
    boolean canGroupAll = needGroupMedia && content.length <= TdConstants.MAX_MESSAGE_GROUP_SIZE;
    do {
      int maxSliceSize = needGroupMedia ? Math.min(remaining, TdConstants.MAX_MESSAGE_GROUP_SIZE) : 1;
      int sliceSize = 0;

      int combineMode = TD.getCombineMode(content[processed + sliceSize]);
      do {
        if (combineMode == TD.COMBINE_MODE_NONE || TD.getCombineMode(content[processed + sliceSize]) != combineMode) {
          if (sliceSize == 0) {
            sliceSize = 1;
          }
          break;
        } else {
          sliceSize++;
          maxSliceSize--;
        }
      } while (maxSliceSize > 0);

      TdApi.InputMessageContent[] slice;
      if (canGroupAll && sliceSize == content.length) {
        slice = content;
      } else {
        slice = new TdApi.InputMessageContent[sliceSize];
        System.arraycopy(content, processed, slice, 0, sliceSize);
      }

      if (sliceSize == 1) {
        functions.add(new TdApi.SendMessage(chatId, messageThreadId, functions.isEmpty() ? replyToMessageId : 0, options, null, slice[0]));
      } else {
        functions.add(new TdApi.SendMessageAlbum(chatId, messageThreadId, functions.isEmpty() ? replyToMessageId : 0, options, slice));
      }

      remaining -= sliceSize;
      processed += sliceSize;
    } while (remaining > 0);

    return functions;
  }

  public static void processSingle (Tdlib tdlib, long chatId, TdApi.MessageSendOptions options, List<TdApi.Function> functions, TdApi.InputMessageContent content) {
    functions.add(new TdApi.SendMessage(chatId, 0, 0, options, null, content));
  }

  public static boolean withinDistance (TdApi.File file, int offset) {
    return offset >= file.local.downloadOffset && offset <= file.local.downloadOffset + file.local.downloadedPrefixSize + ByteUnit.KIB.toBytes(512);
  }

  public static boolean canVote (TdApi.Poll poll) {
    return !needShowResults(poll);
  }

  public static boolean isMultiChoice (TdApi.Poll poll) {
    return poll.type.getConstructor() == TdApi.PollTypeRegular.CONSTRUCTOR && ((TdApi.PollTypeRegular) poll.type).allowMultipleAnswers;
  }

  public static TdApi.FormattedText getExplanation (TdApi.Poll poll) {
    return poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? ((TdApi.PollTypeQuiz) poll.type).explanation : null;
  }

  public static boolean hasAnswer (TdApi.Poll poll) {
    for (TdApi.PollOption option : poll.options) {
      if (option.isChosen)
        return true;
    }
    return false;
  }

  public static boolean hasSelectedOption (TdApi.Poll poll) {
    for (TdApi.PollOption option : poll.options) {
      if (option.isBeingChosen)
        return true;
    }
    return false;
  }

  public static boolean needShowResults (TdApi.Poll poll) {
    if (poll.isClosed)
      return true;
    for (TdApi.PollOption option : poll.options) {
      if (option.isChosen)
        return true;
    }
    return false;
  }

  public static int getMaxVoterCount (TdApi.Poll poll) {
    if (poll.totalVoterCount == 0)
      return 0;
    int maxVoterCount = 0;
    for (TdApi.PollOption option : poll.options) {
      maxVoterCount = Math.max(option.voterCount, maxVoterCount);
    }
    return maxVoterCount;
  }

  public static final class UsernameFilter extends AcceptFilter {
    @Override
    protected boolean accept (char c) {
      return TD.matchUsername(c);
    }
  }

  public static boolean matchUsername (char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
  }

  public static boolean matchCommand (char c) {
    return c != '.' && c != ',' && c != '/' && Character.getType(c) != Character.SPACE_SEPARATOR;
  }

  public static boolean matchHashtag (char c) {
    final int type = Character.getType(c);
    return type != Character.SPACE_SEPARATOR && c != '#';
  }

  /*public static boolean hasWritePermission (TdApi.Chat chat) {
    if (chat == null) {
      return false;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        TdApi.Supergroup channel = TdlibCache.instance().getSupergroup(TD.getChatSupergroupId(chat));
        return hasWritePermission(channel);
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        TdApi.BasicGroup group = TdlibCache.instance().getGroup(TD.getChatBasicGroupId(chat));
        return hasWritePermission(group);
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        TdApi.User user = TD.getUser(chat);
        return user != null && user.type.getConstructor() != TdApi.UserTypeDeleted.CONSTRUCTOR && user.type.getConstructor() != TdApi.UserTypeUnknown.CONSTRUCTOR;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        TdApi.SecretChat secretChat = TD.getSecretChat(chat);
        return secretChat != null && secretChat.state.getConstructor() == TdApi.SecretChatStateReady.CONSTRUCTOR;
      }
    }
    return false;
  }*/

  public static boolean hasWritePermission (TdApi.Supergroup supergroup) {
    if (supergroup != null) {
      if (supergroup.isChannel) {
        switch (supergroup.status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            return ((TdApi.ChatMemberStatusAdministrator) supergroup.status).canPostMessages;
        }
        return false;
      } else {
        return !isNotInChat(supergroup.status);
      }
    }
    return false;
  }

  public static boolean hasWritePermission (TdApi.BasicGroup basicGroup) {
    return basicGroup != null && basicGroup.isActive && hasWritePermission(basicGroup.status);
  }

  public static boolean hasWritePermission (TdApi.ChatMemberStatus status) {
    return !isNotInChat(status) && (status.getConstructor() != TdApi.ChatMemberStatusRestricted.CONSTRUCTOR || hasWritePermission(((TdApi.ChatMemberStatusRestricted) status).permissions));
  }

  public static boolean hasWritePermission (TdApi.ChatPermissions permissions) {
    return permissions != null && permissions.canSendMessages;
  }

  public static boolean isLocalLanguagePackId (String languagePackId) {
    return languagePackId.startsWith("X");
  }

  @Deprecated
  public static boolean isRawLanguage (String id) {
    return !id.startsWith("X") && id.endsWith("-raw");
  }

  public static TdApi.File newFile (File file) {
    final String path = file.getPath();
    final int size = (int) file.length();
    return newFile(0, path, path, size);
  }

  public static TdApi.File newFile (int id, String remoteId, String path, int size) {
    return new TdApi.File(id, size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile(remoteId, "", false, false, 0));
  }

  public static String getFolder (boolean allowExternal, String dirName) {
    File file = allowExternal ? UI.getContext().getExternalFilesDir(null) : null;
    if (file != null) {
      file = new File(file, dirName);
      if (!file.exists() && !file.mkdir()) {
        Log.e("Cannot create %s dir", dirName);
      }
      return normalizePath(file.getPath());
    } else {
      file = new File(UI.getContext().getFilesDir(), dirName);
      if (!file.exists() && !file.mkdir()) {
        Log.e("Cannot create %s dir", dirName);
      }
      return normalizePath(file.getPath());
    }
  }

  public static String getTGDir (boolean allowExternal) {
    File file = allowExternal ? UI.getContext().getExternalFilesDir(null) : null;
    if (file != null) {
      return normalizePath(file.getPath());
    } else {
      file = new File(UI.getContext().getFilesDir(), "tdlib");
      if (!file.exists() && !file.mkdir()) {
        throw new IllegalStateException("Cannot create tdlib dir");
      }
      return normalizePath(file.getPath());
    }
  }

  public static String normalizePath (String path) {
    return StringUtils.isEmpty(path) || path.charAt(path.length() - 1) == '/' ? path : path + '/';
  }

  public static boolean isKnownHost (Uri uri) {
    String host = uri.getHost();
    if (host != null) {
      for (String knownHost : TdConstants.TELEGRAM_HOSTS) {
        if (StringUtils.equalsOrBothEmpty(knownHost, host)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean shouldInlineIv (TdApi.WebPage webPage) {
    return "telegram_album".equals(webPage.type) || shouldInlineIv(webPage.displayUrl);
  }

  public static boolean shouldInlineIv (String url) {
    return url.startsWith("instagram.com/") || url.startsWith("twitter.com/") || (url.startsWith("t.me/") && !url.startsWith("t.me/iv?"));
  }

  public static File getCacheDir (boolean allowExternal) {
    File dir = null;
    if (allowExternal) {
      String state = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(state) && !Environment.isExternalStorageEmulated()) {
        dir = UI.getAppContext().getExternalCacheDir();
      }
    }
    if (dir == null) {
      dir = UI.getAppContext().getCacheDir();
    }
    return dir;
  }

  @SuppressWarnings(value = "SpellCheckingInspection")
  public static File getChallegramDir () {
    File dir;

    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state) && !Environment.isExternalStorageEmulated()) {
      String dirPath = Environment.getExternalStorageDirectory().getPath() + "/Challegram";
      dir = new File(dirPath);
    } else {
      dir = new File(UI.getAppContext().getFilesDir().getPath() + "/Challegram");
    }

    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        return null;
      }
    }

    File noMedia = new File(dir, ".nomedia");
    try {
      if (!noMedia.exists() && !noMedia.createNewFile()) {
        return null;
      }
    } catch (Throwable t) {
      Log.w("Cannot create .nomedia file", t);
    }

    return dir;
  }

  public static boolean isBot (@Nullable TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR;
  }

  public static TdApi.User createFakeUser (int id, String firstName, String lastName, String phoneNumber, TdApi.ProfilePhoto photo) {
    TdApi.User user = new TdApi.User();
    user.id = id;
    user.firstName = firstName;
    user.lastName = lastName;
    user.phoneNumber = phoneNumber;
    user.status = new TdApi.UserStatusOffline(0);
    user.profilePhoto = photo;
    user.type = new TdApi.UserTypeRegular();
    user.haveAccess = true;
    return user;
  }

  public static CharSequence makeClickable (CharSequence sequence) {
    if (sequence instanceof Spanned) {
      Spanned spanned = (Spanned) sequence;
      CustomTypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), CustomTypefaceSpan.class);
      if (spans != null && spans.length > 0) {
        SpannableStringBuilder b = null;
        for (final CustomTypefaceSpan span : spans) {
          int startIndex = spanned.getSpanStart(span);
          int endIndex = spanned.getSpanEnd(span);
          if (span.isClickable()) {
            if (b == null)
              b = spanned instanceof SpannableStringBuilder ? (SpannableStringBuilder) spanned : new SpannableStringBuilder(spanned);
            b.removeSpan(span);
            b.setSpan(new ClickableSpan() {
              @Override
              public void onClick (@NonNull View widget) {
                span.onClick(widget);
              }
            }, startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(span, startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setRemoveUnderline(true);
          }
        }
        return b != null ? b : sequence;
      }
    }
    return sequence;
  }

  private static CharSequence getMemberDescriptionString (TdlibDelegate context, long userId, int date, int resFull, int resShort, int fallbackRes) {
    if (userId != 0 && date != 0) {
      return TD.makeClickable(Lang.getString(resFull, context.context() != null ? ((target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newUserSpan(context, userId) : null) : null, context.tdlib().cache().userName(userId), Lang.getRelativeTimestamp(date, TimeUnit.SECONDS)));
    } else if (userId != 0) {
      return TD.makeClickable(Lang.getString(resShort, context.context() != null ? ((target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newUserSpan(context, userId) : null) : null, context.tdlib().cache().userName(userId)));
    } else if (date != 0) {
      return Lang.getRelativeTimestamp(date, TimeUnit.SECONDS);
    } else {
      return Lang.getString(fallbackRes);
    }
  }

  public static @Nullable CharSequence getMemberJoinDate (TdApi.ChatMember member) {
    if (member.joinedChatDate != 0 && TD.isMember(member.status, false)) {
      return Lang.getString(R.string.MemberSince, Lang.getDate(member.joinedChatDate, TimeUnit.SECONDS), Lang.time(member.joinedChatDate, TimeUnit.SECONDS));
    }
    return null;
  }

  public static @Nullable CharSequence getMemberDescription (TdlibDelegate context, TdApi.ChatMember member, boolean needFull) {
    // long inviterUserId = member.inviterUserId;
    CharSequence result;
    switch (member.status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        result = Lang.getString(R.string.ChannelOwner);
        break;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
        result = getMemberDescriptionString(context, member.inviterUserId, 0 /*FIXME server*/, R.string.PromotedByXOnDate, R.string.PromotedByX, R.string.Administrator);
        break;
      }
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR: {
        result = getMemberDescriptionString(context, member.inviterUserId, ((TdApi.ChatMemberStatusRestricted) member.status).isMember ? /*FIXME server*/ 0 : member.joinedChatDate, R.string.RestrictedByXOnDate, R.string.RestrictedByX, R.string.Restricted);
        break;
      }
      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR: {
        result = getMemberDescriptionString(context, member.inviterUserId, member.joinedChatDate, R.string.BannedByXOnDate, R.string.BannedByX, R.string.Banned);
        break;
      }
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR: {
        if (member.inviterUserId != 0) {
          result = getMemberDescriptionString(context, member.inviterUserId, 0, R.string.InvitedByXOnDate, R.string.InvitedByX, 0);
        } else {
          result = null;
        }
        break;
      }
      default: {
        result = null;
        break;
      }
    }
    CharSequence info = needFull ? getMemberJoinDate(member) : null;
    if (info != null && result != null)
      return Lang.getCharSequence(R.string.format_statusAndDate, org.thunderdog.challegram.emoji.Emoji.instance().replaceEmoji(result), info);
    if (info != null)
      return info;
    return result;
  }

  public static int calculateUnreadStickerSetCount (TdApi.StickerSets stickerSets) {
    int count = 0;
    for (TdApi.StickerSetInfo info : stickerSets.sets) {
      if (!info.isViewed) {
        count++;
      }
    }
    return count;
  }

  public static boolean isMember (TdApi.ChatMemberStatus status, boolean allowLeftCreator /*FIXME*/) {
    if (status == null)
      return false;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return allowLeftCreator || ((TdApi.ChatMemberStatusCreator) status).isMember;
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusRestricted) status).isMember;
      case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        return false;
    }
    return false;
  }

  public static boolean isMember (TdApi.ChatMemberStatus status) {
    return isMember(status, true);
  }

  public static boolean hasHashtag (TdApi.FormattedText text, String hashtag) {
    if (!Td.isEmpty(text) && text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.type.getConstructor() == TdApi.TextEntityTypeHashtag.CONSTRUCTOR) {
          if (hashtag.equals(Td.substring(text.text, entity)))
            return true;
        }
      }
    }
    return false;
  }

  public static boolean matchesFilter (TdApi.SupergroupMembersFilter filter, TdApi.ChatMemberStatus status) {
    switch (filter.getConstructor()) {
      case TdApi.SupergroupMembersFilterRecent.CONSTRUCTOR:
        return isMember(status);
      case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR:
        switch (status.getConstructor()) {
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
              return true;
        }
        return false;
      case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
        return status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR;
      case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
        return status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR;
    }
    return false;
  }

  public static String getLink (TdApi.Supergroup supergroup) {
    return "https://" + getTelegramHost() + "/" + supergroup.username;
  }

  public static String getStickerPackLink (String name) {
    return "https://" + getTelegramHost() + "/addstickers/" + name;
  }

  public static String getLink (TdApi.User user) {
    return "https://" + getTelegramHost() + "/" + user.username;
  }

  public static String getLink (String username) {
    return "https://" + getTelegramHost() + "/" + username;
  }

  public static String getLink (TdApi.LanguagePackInfo languagePackInfo) {
    return "https://" + getTelegramHost() + "/setlanguage/" + languagePackInfo.id;
  }

  public static String getRoleName (@Nullable TdApi.User user, int role) {
    switch (role) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: return Lang.getString(R.string.ChannelOwner);
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
        return Lang.getString(R.string.Administrator);
      }
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR: {
        if (user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR) {
          return Lang.getString(((TdApi.UserTypeBot) user.type).canReadAllGroupMessages ? R.string.BotStatusRead : R.string.BotStatusCantRead);
        }
        return null;
      }
    }
    return null;
  }

  public static boolean isContact (TdApi.User user) {
    return user != null && user.isContact;
  }

  public static boolean hasPhoneNumber (TdApi.User user) {
    return user != null && !StringUtils.isEmpty(user.phoneNumber);
  }

  public static boolean canAddContact (TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR;
  }

  public static boolean isRestricted (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static boolean isAdmin (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static boolean isChannel (TdApi.ChatType type) {
    return type != null && type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && ((TdApi.ChatTypeSupergroup) type).isChannel;
  }

  public static boolean isSupergroup (TdApi.ChatType type) {
    return type != null && type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && !((TdApi.ChatTypeSupergroup) type).isChannel;
  }

  public static float getFileProgress (TdApi.File file) {
    return getFileProgress(file, false);
  }

  public static float getFileProgress (TdApi.File file, boolean needFull) {
    if (file == null) {
      return 0f;
    }
    if (file.remote != null && file.remote.isUploadingActive) {
      if (file.expectedSize != 0) {
        return (float) file.remote.uploadedSize / (float) file.expectedSize;
      }
      /*if (file.localSize != 0f && !Strings.isEmpty(file.path)) {
        double progress = TGFileGenerationManager.instance().getVideoProgress(file.path);
        if (progress != -1) {
          return (float) (progress * (double) ((float) file.remoteSize / (float) file.localSize));
        }
      }*/
      return 0f;
    }
    if (file.local != null && (file.local.isDownloadingActive || (file.local.downloadedSize > 0 && needFull))) {
      return file.expectedSize != 0 ? (float) file.local.downloadedSize / (float) file.expectedSize : 0f;
    }
    return isFileLoaded(file) ? 1f : 0f;
  }

  public static float getFilePrefixProgress (TdApi.File file) {
    if (file == null || file.local == null || file.local.downloadOffset == 0)
      return getFileProgress(file, true);
    return file.expectedSize != 0 ? (float) file.local.downloadedPrefixSize / (float) file.expectedSize : 0f;
  }

  public static float getFileOffsetProgress (TdApi.File file) {
    if (file == null || file.local == null || file.local.downloadOffset == 0)
      return 0f;
    return file.expectedSize != 0f ? (float) file.local.downloadOffset / (float) file.expectedSize : 0f;
  }

  public static boolean isFailed (TdApi.Message msg) {
    return msg != null && msg.sendingState != null && msg.sendingState.getConstructor() == TdApi.MessageSendingStateFailed.CONSTRUCTOR;
  }

  public static boolean isSecretChatReady (TdApi.SecretChat secretChat) {
    return secretChat != null && secretChat.state.getConstructor() == TdApi.SecretChatStateReady.CONSTRUCTOR;
  }

  public static long getUserId (TdApi.ChatType type) {
    switch (type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        return ((TdApi.ChatTypePrivate) type).userId;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        return ((TdApi.ChatTypeSecret) type).userId;
      }
    }
    return 0;
  }

  public static long getUserId (TdApi.Chat chat) {
    return chat != null ? getUserId(chat.type) : 0;
  }

  public static String getGameName (TdApi.Game game, boolean includeEmoji) {
    return (includeEmoji ? "\u200e\ud83c\udfae " : "") + (StringUtils.isEmpty(game.title) ? game.shortName : game.title);
  }

  public static int getSecretChatId (TdApi.Chat chat) {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR ? ((TdApi.ChatTypeSecret) chat.type).secretChatId : 0;
  }

  public static TdApi.InputFile createInputFile (long chatId, TdApi.File file) {
    return ChatId.isSecret(chatId) ? TD.createFileCopy(file) : new TdApi.InputFileId(file.id);
  }

  public static final int PREVIEW_FLAG_ALLOW_CAPTIONS = 1;
  public static final int PREVIEW_FLAG_FORCE_MEDIA_TYPE = 1 << 1;

  private static String buildShortPreview (Tdlib tdlib, @Nullable TdApi.Message m, boolean allowCaptions, boolean multiLine, @Nullable RunnableBool translatable) {
    String str = buildShortPreviewImpl(tdlib, m, allowCaptions ? PREVIEW_FLAG_ALLOW_CAPTIONS : 0, translatable);
    return StringUtils.isEmpty(str) || multiLine ? str : Strings.translateNewLinesToSpaces(str);
  }

  public static String buildShortPreview (Tdlib tdlib, @Nullable TdApi.Message m, boolean allowCaptions) {
    return buildShortPreview(tdlib, m, allowCaptions, false, null);
  }

  private static int getDartRes (int value) {
    switch (value) {
      case 0:
        return R.string.ChatContentDart;
      case 1:
        return R.string.ChatContentDart1;
      case 2:
        return R.string.ChatContentDart2;
      case 3:
        return R.string.ChatContentDart3;
      case 4:
        return R.string.ChatContentDart4;
      case 6:
        return R.string.ChatContentDart6;
      case 5:
      default:
        return R.string.ChatContentDart5;
    }
  }

  private static String buildShortPreviewImpl (Tdlib tdlib, @Nullable TdApi.Message m, int flags, @Nullable RunnableBool isTranslatable) {
    if (m == null || m.content == null) {
      U.set(isTranslatable, true);
      return Lang.getString(R.string.DeletedMessage);
    }
    if (!StringUtils.isEmpty(m.restrictionReason) && Settings.instance().needRestrictContent()) {
      return m.restrictionReason;
    }
    boolean allowCaptions = (flags & PREVIEW_FLAG_ALLOW_CAPTIONS) != 0;
    boolean forceMediaType = (flags & PREVIEW_FLAG_FORCE_MEDIA_TYPE) != 0;
    switch (m.content.getConstructor()) {
      // Common
      case TdApi.MessageText.CONSTRUCTOR: {
        U.set(isTranslatable, false);
        return ((TdApi.MessageText) m.content).text.text;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        String caption = ((TdApi.MessageAnimation) m.content).caption.text;
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentAnimation);
        } else if (forceMediaType) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, Lang.getString(R.string.ChatContentAnimation), caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        String caption = ((TdApi.MessagePhoto) m.content).caption.text;
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentPhoto);
        } else if (forceMediaType) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, Lang.getString(R.string.ChatContentPhoto), caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessageDice.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        String emoji = ((TdApi.MessageDice) m.content).emoji;
        int value = ((TdApi.MessageDice) m.content).value;
        if (TD.EMOJI_DART.textRepresentation.equals(emoji)) {
          return Lang.getString(getDartRes(value));
        }
        if (TD.EMOJI_DICE.textRepresentation.equals(emoji)) {
          if (value != 0) {
            return Lang.plural(R.string.ChatContentDiceRolled, value);
          } else {
            return Lang.getString(R.string.ChatContentDice);
          }
        }
        return emoji;
      }
      case TdApi.MessagePoll.CONSTRUCTOR: {
        String question = ((TdApi.MessagePoll) m.content).poll.question;
        U.set(isTranslatable, false);
        return question;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.MessageDocument doc = (TdApi.MessageDocument) m.content;
        String caption = doc.caption.text;
        String mediaType = doc.document != null && !StringUtils.isEmpty(doc.document.fileName) ? doc.document.fileName : null;
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          if (mediaType != null) {
            U.set(isTranslatable, false);
            return mediaType;
          } else {
            U.set(isTranslatable, true);
            return Lang.getString(R.string.ChatContentFile);
          }
        } else if (forceMediaType) {
          if (mediaType == null) {
            mediaType = Lang.getString(R.string.ChatContentFile);
          }
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, mediaType, caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        String caption = ((TdApi.MessageVoiceNote) m.content).caption.text;
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentVoice);
        } else if (forceMediaType) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, Lang.getString(R.string.ChatContentVoice), caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) m.content).audio;
        String caption = ((TdApi.MessageAudio) m.content).caption.text;
        String mediaType = Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          U.set(isTranslatable, true);
          return mediaType;
        } else if (forceMediaType) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, mediaType, caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        String caption = ((TdApi.MessageVideo) m.content).caption.text;
        if (!allowCaptions || StringUtils.isEmpty(caption)) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentVideo);
        } else if (forceMediaType) {
          U.set(isTranslatable, true);
          return Lang.getString(R.string.ChatContentWithCaption, Lang.getString(R.string.ChatContentVideo), caption); // TODO style
        } else {
          U.set(isTranslatable, false);
          return caption;
        }
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.AttachPhotoExpired);
      }
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        U.set(isTranslatable, false);
        return ((TdApi.MessageInvoice) m.content).title;
      }
      case TdApi.MessageExpiredVideo.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.AttachVideoExpired);
      }
      case TdApi.MessageCall.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        TdApi.MessageCall call = (TdApi.MessageCall) m.content;
        // StringBuilder b = new StringBuilder(/*UI.getString(TGUtils.getCallName(call, TGUtils.isOut(m), true))*/);
        String content;
        boolean isOut = TD.isOut(m);
        switch (call.discardReason.getConstructor()) {
          case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
            content = Lang.getString(isOut ? R.string.OutgoingCallBusy : R.string.CallMessageIncomingDeclined);
            break;
          case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
            content = Lang.getString(isOut ? R.string.CallMessageOutgoingMissed : R.string.MissedCall);
            break;
          case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
          case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
          case TdApi.CallDiscardReasonEmpty.CONSTRUCTOR:
          default:
            content = Lang.getString(isOut ? R.string.OutgoingCall : R.string.IncomingCall);
            break;
        }

        if (call.duration > 0) {
          return Lang.getString(R.string.ChatContentCallWithDuration, content, Lang.getDurationFull(call.duration));
        } else {
          return content;
        }
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.ChatContentRoundVideo);
      }
      case TdApi.MessageWebsiteConnected.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.BotWebsiteAllowed, ((TdApi.MessageWebsiteConnected) m.content).domainName);
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        TdApi.Sticker sticker = m.content != null ? ((TdApi.MessageSticker) m.content).sticker : null;
        return sticker != null && !StringUtils.isEmpty(sticker.emoji) ? Lang.getString(R.string.sticker, sticker.emoji) : Lang.getString(R.string.Sticker);
      }
      case TdApi.MessageVenue.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.Location);
      }
      case TdApi.MessageLocation.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        int resource = ((TdApi.MessageLocation) m.content).livePeriod > 0 ? R.string.AttachLiveLocation : R.string.Location;
        return Lang.getString(resource);
      }
      case TdApi.MessageContact.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.AttachContact);
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        U.set(isTranslatable, false);
        TdApi.Game game = ((TdApi.MessageGame) m.content).game;
        return TD.getGameName(game, false);
      }
      case TdApi.MessageContactRegistered.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.NotificationContactJoined, tdlib.senderName(m.senderId, true));
      }
      case TdApi.MessagePinMessage.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (m.isChannelPost) {
          if (StringUtils.isEmpty(m.authorSignature)) {
            return Lang.getString(R.string.PinnedMessageAction);
          } else {
            return Lang.getString(R.string.NotificationActionPinnedNoTextChannel, m.authorSignature);
          }
        } else {
          return Lang.getString(R.string.NotificationActionPinnedNoTextChannel, tdlib.senderName(m.senderId, true));
        }
      }
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        final int score = ((TdApi.MessageGameScore) m.content).score;
        if (tdlib.isSelfSender(m)) {
          return Lang.plural(R.string.game_ActionYouScored, score);
        } else {
          return Lang.plural(R.string.game_ActionUserScored, score, tdlib.senderName(m.senderId, true));
        }
      }
      // Secret chats
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (TD.isOut(m)) {
          return Lang.getString(R.string.YouTookAScreenshot);
        } else {
          return Lang.getString(R.string.XTookAScreenshot, tdlib.senderName(m.senderId, true));
        }
      }
      case TdApi.MessageChatSetTtl.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        TdApi.MessageChatSetTtl ttl = (TdApi.MessageChatSetTtl) m.content;
        if (ChatId.isUserChat(m.chatId)) {
          if (ttl.ttl == 0) {
            if (TD.isOut(m)) {
              return Lang.getString(R.string.YouDisabledTimer);
            } else {
              return Lang.getString(R.string.XDisabledTimer, tdlib.senderName(m.senderId, true));
            }
          } else {
            if (TD.isOut(m)) {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.YouSetTimerSeconds, R.string.YouSetTimerMinutes, R.string.YouSetTimerHours, R.string.YouSetTimerDays, R.string.YouSetTimerWeeks, R.string.YouSetTimerMonths).toString();
            } else {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.XSetTimerSeconds, R.string.XSetTimerMinutes, R.string.XSetTimerHours, R.string.XSetTimerDays, R.string.XSetTimerWeeks, R.string.XSetTimerMonths, tdlib.senderName(m.senderId, true)).toString();
            }
          }
        } else {
          if (ttl.ttl == 0) {
            if (TD.isOut(m)) {
              return Lang.getString(R.string.YouDisabledAutoDelete);
            } else {
              return Lang.getString(m.isChannelPost ? R.string.XDisabledAutoDeletePosts : R.string.XDisabledAutoDelete, tdlib.senderName(m.senderId, true));
            }
          } else if (m.isChannelPost) {
            if (TD.isOut(m)) {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.YouSetAutoDeletePostsSeconds, R.string.YouSetAutoDeletePostsMinutes, R.string.YouSetAutoDeletePostsHours, R.string.YouSetAutoDeletePostsDays, R.string.YouSetAutoDeletePostsWeeks, R.string.YouSetAutoDeletePostsMonths).toString();
            } else {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.XSetAutoDeletePostsSeconds, R.string.XSetAutoDeletePostsMinutes, R.string.XSetAutoDeletePostsHours, R.string.XSetAutoDeletePostsDays, R.string.XSetAutoDeletePostsWeeks, R.string.XSetAutoDeletePostsMonths, tdlib.senderName(m.senderId, true)).toString();
            }
          } else {
            if (TD.isOut(m)) {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.YouSetAutoDeleteSeconds, R.string.YouSetAutoDeleteMinutes, R.string.YouSetAutoDeleteHours, R.string.YouSetAutoDeleteDays, R.string.YouSetAutoDeleteWeeks, R.string.YouSetAutoDeleteMonths).toString();
            } else {
              return Lang.pluralDuration(ttl.ttl, TimeUnit.SECONDS, R.string.XSetAutoDeleteSeconds, R.string.XSetAutoDeleteMinutes, R.string.XSetAutoDeleteHours, R.string.XSetAutoDeleteDays, R.string.XSetAutoDeleteWeeks, R.string.XSetAutoDeleteMonths, tdlib.senderName(m.senderId, true)).toString();
            }
          }
        }
      }
      // Group
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (TD.isOut(m)) {
          return Lang.getString(R.string.YouCreatedGroup);
        } else {
          return Lang.getString(R.string.XCreatedGroup, tdlib.senderName(m.senderId, true));
        }
      }
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (TD.isOut(m)) {
          return Lang.getString(m.isChannelPost ? R.string.YouCreatedChannel : R.string.YouCreatedGroup);
        } else if (m.isChannelPost) {
          return Lang.getString(R.string.ActionCreateChannel);
        } else {
          return Lang.getString(R.string.XCreatedGroup, tdlib.senderName(m.senderId, true));
        }
      }
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (TD.isOut(m)) {
          return Lang.getString(R.string.YouJoinedByLink);
        } else {
          return Lang.getString(R.string.XJoinedByLink, tdlib.senderName(m.senderId, true));
        }
      }
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (TD.isOut(m)) {
          return Lang.getString(m.isChannelPost ? R.string.YouAcceptedToChannel : R.string.YouAcceptedToGroup);
        } else {
          return Lang.getString(m.isChannelPost ? R.string.XAcceptedToChannel : R.string.XAcceptedToGroup, tdlib.senderName(m.senderId, true));
        }
      }
      // Supergroup migration
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.GroupUpgraded);
      }

      case TdApi.MessageChatChangeTitle.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (m.isChannelPost)
          return Lang.getString(R.string.ActionChannelChangedTitle);
        else
          return Lang.getString(R.string.XChangedGroupTitle, tdlib.senderName(m.senderId, true));
      }

      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (m.isChannelPost)
          return Lang.getString(R.string.ActionChannelChangedPhoto);
        else if (TD.isOut(m))
          return Lang.getString(R.string.group_photo_changed_you);
        else
          return Lang.getString(R.string.group_photo_changed, tdlib.senderName(m.senderId, true));
      }

      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        if (m.isChannelPost) {
          return Lang.getString(R.string.ActionChannelRemovedPhoto);
        } else if (TD.isOut(m)) {
          return Lang.getString(R.string.group_photo_deleted_you);
        } else {
          return Lang.getString(R.string.group_photo_deleted, tdlib.senderName(m.senderId, true));
        }
      }

      case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        final TdApi.MessageChatAddMembers users = (TdApi.MessageChatAddMembers) m.content;
        if (m.isChannelPost) {
          if (users.memberUserIds.length == 1) {
            if (tdlib.isSelfUserId(users.memberUserIds[0])) {
              return Lang.getString(R.string.channel_user_add_self);
            } else {
              return Lang.getString(R.string.channel_user_add, tdlib.cache().userFirstName(users.memberUserIds[0]));
            }
          } else {
            return Lang.plural(R.string.xPeopleJoinedChannel, users.memberUserIds.length);
          }
        } else {
          if (users.memberUserIds.length == 1) {
            long joinedUserId = users.memberUserIds[0];
            if (joinedUserId != Td.getSenderUserId(m)) {
              if (tdlib.isSelfUserId(joinedUserId)) {
                return Lang.getString(R.string.group_user_added_self, tdlib.senderName(m.senderId, true));
              } else if (tdlib.isSelfSender(m.senderId)) {
                return Lang.getString(R.string.group_user_self_added, tdlib.cache().userFirstName(joinedUserId));
              } else {
                return Lang.getString(R.string.group_user_added, tdlib.senderName(m.senderId, true), tdlib.cache().userFirstName(joinedUserId));
              }
            } else {
              if (tdlib.isSelfUserId(joinedUserId)) {
                return Lang.getString(R.string.group_user_add_self);
              } else {
                return Lang.getString(R.string.group_user_add, tdlib.cache().userFirstName(joinedUserId));
              }
            }
          } else {
            return Lang.plural(R.string.xPeopleJoinedGroup, users.memberUserIds.length);
          }
        }
      }

      case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        final long deletedUserId = ((TdApi.MessageChatDeleteMember) m.content).userId;
        if (m.isChannelPost && Td.getSenderUserId(m) == deletedUserId) {
          if (tdlib.isSelfUserId(deletedUserId)) {
            return Lang.getString(R.string.channel_user_remove_self);
          } else {
            return Lang.getString(R.string.channel_user_remove, tdlib.cache().userFirstName(deletedUserId));
          }
        } else {
          if (Td.getSenderUserId(m) == deletedUserId) {
            if (tdlib.isSelfUserId(deletedUserId)) {
              return Lang.getString(R.string.group_user_remove_self);
            } else {
              return Lang.getString(R.string.group_user_remove, tdlib.cache().userFirstName(deletedUserId));
            }
          } else {
            if (tdlib.isSelfSender(m)) {
              return Lang.getString(R.string.group_user_self_removed, tdlib.cache().userFirstName(deletedUserId));
            } else if (tdlib.isSelfUserId(deletedUserId)) {
              return Lang.getString(R.string.group_user_removed_self, tdlib.senderName(m.senderId, true));
            } else {
              return Lang.getString(R.string.group_user_removed, tdlib.senderName(m.senderId, true), tdlib.cache().userFirstName(deletedUserId));
            }
          }
        }
      }

      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        TdApi.MessagePaymentSuccessful successful = (TdApi.MessagePaymentSuccessful) m.content;
        return Lang.getString(R.string.PaymentSuccessfullyPaidNoItem, CurrencyUtils.buildAmount(successful.currency, successful.totalAmount), tdlib.chatTitle(m.chatId));
      }
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR: {
        U.set(isTranslatable, false);
        return ((TdApi.MessageCustomServiceAction) m.content).text;
      }
      case TdApi.MessagePassportDataSent.CONSTRUCTOR: { // TODO
        U.set(isTranslatable, true);
        return Lang.getString(R.string.UnsupportedMessage);
      }
      case TdApi.MessageUnsupported.CONSTRUCTOR: {
        U.set(isTranslatable, true);
        return Lang.getString(R.string.UnsupportedMessageType);
      }
      // Bots only. Unused
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
        break;
    }
    U.set(isTranslatable, false);
    return "(null)";
  }

  public static String getFirstName (TdApi.User user) {
    if (user == null) {
      return "null";
    }
    if (user.type.getConstructor() == TdApi.UserTypeDeleted.CONSTRUCTOR) {
      return Lang.getString(R.string.HiddenNameShort);
    }
    String firstName = user.firstName.trim();
    if (StringUtils.isEmpty(firstName)) {
      return user.lastName.trim();
    } else {
      return firstName;
    }
  }

  public static String getIconUrl (TdApi.Venue venue) {
    if ("foursquare".equals(venue.provider)) {
      if (!StringUtils.isEmpty(venue.type)) {
        return "https://ss3.4sqi.net/img/categories_v2/" + venue.type + "_88.png";
      }
    }
    return null;
  }

  public static final String PHOTO_SIZE_S = "s"; // box	100x100
  public static final String PHOTO_SIZE_M = "m"; // box	320x320
  public static final String PHOTO_SIZE_X = "x"; // box	800x800
  public static final String PHOTO_SIZE_Y = "y"; // box	1280x1280
  public static final String PHOTO_SIZE_W = "w"; // box 2560x2560
  public static final String PHOTO_SIZE_A = "a"; // crop 160x160
  public static final String PHOTO_SIZE_B = "b"; // crop 320x320
  public static final String PHOTO_SIZE_C = "c"; // crop 640x640
  public static final String PHOTO_SIZE_D = "d"; // crop 1280x1280

  public static TdApi.PhotoSize findSize (TdApi.Photo photo, String type) {
    for (TdApi.PhotoSize size : photo.sizes) {
      if (size.type.equals(type)) {
        return size;
      }
    }
    return null;
  }

  public static @Nullable TdApi.PhotoSize findSmallest (TdApi.Photo photo, TdApi.PhotoSize ignoreSize) {
    return findSmallest(photo.sizes, ignoreSize);
  }

  public static @Nullable TdApi.PhotoSize findSmallest (TdApi.PhotoSize[] sizes, TdApi.PhotoSize ignoreSize) {
    if (sizes.length == 1) {
      if (ignoreSize.width == sizes[0].width && ignoreSize.height == sizes[0].height && ignoreSize.photo.id == sizes[0].photo.id) {
        return null;
      }
      return sizes[0];
    }
    TdApi.PhotoSize res = null;
    int w = 0, h = 0;
    boolean first = true;
    for (TdApi.PhotoSize size : sizes) {
      if (size.width == ignoreSize.width && size.height == ignoreSize.height && size.photo.id == ignoreSize.photo.id) {
        continue;
      }
      if (first || size.width < w || size.height < h) {
        first = false;
        res = size;
        w = size.width;
        h = size.height;
      }
    }
    return res;
  }

  public static @Nullable TdApi.PhotoSize findPhotoSize (TdApi.PhotoSize[] sizes, String type) {
    for (TdApi.PhotoSize size : sizes) {
      if (size.type.equals(type)) {
        return size;
      }
    }
    return null;
  }

  public static @Nullable TdApi.PhotoSize findPhotoSize (TdApi.PhotoSize[] sizes, int maxWidth, int maxHeight, int ignoreFileId, @Nullable String orType) {
    int deviceAcceptableSize = (int) (Screen.widestSideDp() * Math.min(Screen.density(), 2f));
    int actualMax = Math.max(800, Math.min(1280, deviceAcceptableSize));

    if (Math.max(maxWidth, maxHeight) != actualMax) {
      float ratio = Math.min((float) actualMax / (float) maxWidth, (float) actualMax / (float) maxHeight);
      maxWidth = (int) ((float) maxWidth * ratio);
      maxHeight = (int) ((float) maxHeight * ratio);
    }

    TdApi.PhotoSize result = null;
    int lastDifference = 0;

    for (TdApi.PhotoSize photoSize : sizes) {
      if (photoSize.photo.id == ignoreFileId) {
        continue;
      }
      if (orType != null && orType.equals(photoSize.type)) {
        if (photoSize.photo.local.canBeDownloaded || TD.isFileLoadedAndExists(photoSize.photo)) {
          return photoSize;
        } else {
          continue;
        }
      }
      int difference = Math.max(Math.abs(photoSize.width - maxWidth), Math.abs(photoSize.height - maxHeight));
      if (result == null || lastDifference > difference) {
        result = photoSize;
        lastDifference = difference;
      }
    }
    return result;
  }

  public static TdApi.PhotoSize findClosest (TdApi.Photo photo, int width, int height) {
    return photo != null ? findClosest(photo.sizes, width, height) : null;
  }

  public static @Nullable String findOrdinary (Map<String, TdApi.LanguagePackString> strings, String key, Future<String> defaultValue) {
    TdApi.LanguagePackString string = strings.get(key);
    if (string != null && string.value instanceof TdApi.LanguagePackStringValueOrdinary) {
      return ((TdApi.LanguagePackStringValueOrdinary) string.value).value;
    }
    return defaultValue != null ? defaultValue.get() : null;
  }

  public static @Nullable String findOrdinary (TdApi.LanguagePackString[] strings, String key, Future<String> defaultValue) {
    for (TdApi.LanguagePackString string : strings) {
      if (key.equals(string.key)) {
        return string.value instanceof TdApi.LanguagePackStringValueOrdinary ? ((TdApi.LanguagePackStringValueOrdinary) string.value).value : defaultValue != null ? defaultValue.get() : null;
      }
    }
    return defaultValue != null ? defaultValue.get() : null;
  }

  public static TdApi.PhotoSize findClosest (TdApi.PhotoSize[] sizes, int width, int height) {
    if (sizes == null)
      return null;
    if (sizes.length == 1) {
      return sizes[0];
    }
    boolean first = true;
    TdApi.PhotoSize result = null;
    final int square = width * height;
    int closestDiff = 0;
    for (TdApi.PhotoSize size : sizes) {
      int minSide = Math.min(size.width, size.height);
      int maxSide = Math.max(size.width, size.height);
      int curSquare = minSide * maxSide;
      int curDiff = Math.abs(square - curSquare);

      if (first || curDiff < closestDiff) {
        closestDiff = curDiff;
        result = size;
        first = false;
      }
    }
    return result;
  }

  public static TdApi.File getWebPagePreviewImage (TdApi.WebPage page) {
    if (isPhotoEmpty(page.photo)) {
      return null;
    }
    int size = Screen.dp(34f);
    TdApi.PhotoSize photoSize = TD.findClosest(page.photo, size, size);
    return photoSize == null ? null : photoSize.photo;
  }

  public static boolean canCopy (String caption) {
    return caption != null && caption.trim().length() > 0;
  }

  public static void copyText (TGMessage msg) {
    final TdApi.FormattedText text = getTextFromMessage(msg);
    if (text != null) {
      UI.copyText(toCopyText(text), R.string.CopiedText);
    }
  }

  public static TdApi.FormattedText getTextFromMessage (TGMessage msg) {
    return msg != null && msg.getMessage() != null ? Td.textOrCaption(msg.getMessage().content) : null;
  }

  public static String getTextFromMessage (TdApi.Message msg) {
    return msg != null ? getTextFromMessage(msg.content) : null;
  }

  public static boolean suggestSharingContact (TdApi.User user) {
    return false; // TODO?
  }

  public static boolean canConvertToVideo (TdApi.Document document) {
    return false;
    // document != null && !Strings.isEmpty(document.mimeType) && document.thumbnail != null && Math.max(document.thumbnail.width, document.thumbnail.height) > 0 && document.mimeType.startsWith("video/") && isSupportedVideoType(document.mimeType);
  }

  public static boolean isSupportedVideoType (@NonNull String mimeType) {
    switch (mimeType) {
      case "video/mp4":
      case "video/x-matroska":
      case "video/webm":
        return true;
    }
    return false;
  }

  public static TdApi.Message[] filterNulls (TdApi.Message[] messages) {
    if (messages == null || messages.length == 0) {
      return null;
    }
    boolean foundNulls = false;
    for (TdApi.Message message : messages) {
      if (message == null) {
        foundNulls = true;
        break;
      }
    }
    if (foundNulls) {
      ArrayList<TdApi.Message> list = null;
      for (TdApi.Message message : messages) {
        if (message != null) {
          if (list == null) {
            list = new ArrayList<>(messages.length - 1);
          }
          list.add(message);
        }
      }
      if (list != null) {
        TdApi.Message[] result = new TdApi.Message[list.size()];
        list.toArray(result);
        return result;
      }
      return null;
    }
    return messages;
  }

  public static boolean canEditText (TdApi.MessageContent content) {
     return canBeEdited(content) && content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR;
  }

  public static boolean canBeEdited (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static String getTextFromMessage (TdApi.MessageContent content) {
    TdApi.FormattedText formattedText = Td.textOrCaption(content);
    return formattedText != null ? formattedText.text : null;
  }

  public static @TdApi.MessageContent.Constructors int convertToMessageContent (TdApi.WebPage webPage) {
    if (webPage == null)
      return TdApi.MessageText.CONSTRUCTOR;
    if (webPage.sticker != null)
      return TdApi.MessageSticker.CONSTRUCTOR;
    if (webPage.video != null)
      return TdApi.MessageVideo.CONSTRUCTOR;
    if (webPage.animation != null)
      return TdApi.MessageAnimation.CONSTRUCTOR;
    if (webPage.audio != null)
      return TdApi.MessageAudio.CONSTRUCTOR;
    if (webPage.document != null)
      return TdApi.MessageDocument.CONSTRUCTOR;
    if (webPage.photo != null)
      return TdApi.MessagePhoto.CONSTRUCTOR;
    return TdApi.MessageText.CONSTRUCTOR;
  }

  public static @Nullable TdApi.File getFile (TdApi.WebPage webPage) {
    if (webPage == null)
      return null;
    if (webPage.sticker != null)
      return webPage.sticker.sticker;
    if (webPage.video != null)
      return webPage.video.video;
    if (webPage.animation != null)
      return webPage.animation.animation;
    if (webPage.audio != null)
      return webPage.audio.audio;
    if (webPage.document != null)
      return webPage.document.document;
    if (webPage.photo != null)
      return getFile(webPage.photo);
    return null;
  }

  public static @Nullable String getMimeType (TdApi.WebPage webPage) {
    if (webPage == null)
      return null;
    if (webPage.sticker != null)
      return "image/webp";
    if (webPage.video != null) {
      String mimeType = webPage.video.mimeType;
      if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("video/"))
        mimeType = "video/*";
      return mimeType;
    }
    if (webPage.animation != null) {
      String mimeType = webPage.animation.mimeType;
      if (StringUtils.isEmpty(mimeType) || !(mimeType.startsWith("video/") || mimeType.equals("image/gif")))
        mimeType = "video/*";
      return mimeType;
    }
    if (webPage.audio != null) {
      String mimeType = webPage.audio.mimeType;
      if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("audio/"))
        mimeType = "audio/*";
      return mimeType;
    }
    if (webPage.document != null) {
      String mimeType = webPage.document.mimeType;
      if (StringUtils.isEmpty(mimeType)) {
        String extension = U.getExtension(webPage.document.fileName);
        mimeType = TGMimeType.mimeTypeForExtension(extension);
      }
      if (StringUtils.isEmpty(mimeType))
        return "application/octet-stream";
      return mimeType;
    }
    if (webPage.photo != null)
      return "image/jpeg";
    return null;
  }
  
  public static @Nullable String getTextOrCaption (TdApi.PushMessageContent content) {
    if (content == null)
      return null;
    switch (content.getConstructor()) {
      case TdApi.PushMessageContentText.CONSTRUCTOR: return ((TdApi.PushMessageContentText) content).text;
      case TdApi.PushMessageContentAnimation.CONSTRUCTOR: return ((TdApi.PushMessageContentAnimation) content).caption;
      case TdApi.PushMessageContentVideo.CONSTRUCTOR: return ((TdApi.PushMessageContentVideo) content).caption;

      case TdApi.PushMessageContentPhoto.CONSTRUCTOR: return ((TdApi.PushMessageContentPhoto) content).caption;
      case TdApi.PushMessageContentPoll.CONSTRUCTOR: return ((TdApi.PushMessageContentPoll) content).question;

      // FIXME: server+TDLIB missing captions in these media kinds
      /*case TdApi.PushMessageContentDocument.CONSTRUCTOR: return ((TdApi.PushMessageContentDocument) content).caption;
      case TdApi.PushMessageContentMediaAlbum.CONSTRUCTOR: return ((TdApi.PushMessageContentMediaAlbum) content).caption;
      case TdApi.PushMessageContentAudio.CONSTRUCTOR: return ((TdApi.PushMessageContentAudio) content).caption;
      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR: return ((TdApi.PushMessageContentVoiceNote) content).caption;*/

      case TdApi.PushMessageContentChatChangeTitle.CONSTRUCTOR: return ((TdApi.PushMessageContentChatChangeTitle) content).title;
      // case TdApi.PushMessageContentChatSetTheme.CONSTRUCTOR: return ((TdApi.PushMessageContentChatSetTheme) content).themeName;
    }
    return null;
  }

  public static boolean canCopyText (TdApi.Message msg) {
    String copyText = getTextFromMessage(msg);
    return copyText != null && copyText.trim().length() > 0;
  }

  public static TdApi.Message removeWebPage (TdApi.Message message) {
    TdApi.MessageContent content = removeWebPage(message.content);
    if (content != message.content) {
      message = Td.copyOf(message);
      message.content = content;
    }
    return message;
  }

  public static TdApi.MessageContent removeWebPage (TdApi.MessageContent content) {
    if (content == null || content.getConstructor() != TdApi.MessageText.CONSTRUCTOR) {
      return content;
    }
    TdApi.MessageText messageText = (TdApi.MessageText) content;
    if (messageText.webPage == null) {
      return messageText;
    }
    TdApi.FormattedText newText = new TdApi.FormattedText();
    int start = messageText.text.text.length() + 1;
    newText.text = messageText.text.text + "\n[" + Lang.getString(R.string.LinkPreview) + "]";
    if (messageText.text.entities != null) {
      newText.entities = ArrayUtils.resize(messageText.text.entities, messageText.text.entities.length + 1, null);
    } else {
      newText.entities = new TdApi.TextEntity[1];
    }
    newText.entities[newText.entities.length - 1] = new TdApi.TextEntity(start, newText.text.length() - start, new TdApi.TextEntityTypeItalic());
    return new TdApi.MessageText(newText, null);
  }

  public static class DownloadedFile {
    private final TdApi.File file;
    private final String fileName;
    private final String mimeType;
    private final int fileSize;
    private final TdApi.FileType fileType;

    public DownloadedFile (TdApi.File file, String fileName, String mimeType, TdApi.FileType fileType) {
      this.file = file;
      this.fileSize = file.size;
      this.fileName = U.getSecureFileName(fileName);
      this.mimeType = mimeType;
      this.fileType = fileType;
    }

    public TdApi.File getFile () {
      return file;
    }

    public static DownloadedFile valueOfPhoto (TdApi.File file, boolean isWebp) {
      return new DownloadedFile(file, isWebp ? "image.webp" : "image.jpg", isWebp ? "image/webp" : "image/jpg", new TdApi.FileTypePhoto());
    }

    public static DownloadedFile valueOf (TdApi.Animation animation) {
      return new DownloadedFile(animation.animation, animation.fileName, animation.mimeType, new TdApi.FileTypeAnimation());
    }

    public static DownloadedFile valueOf (TdApi.Video video) {
      return new DownloadedFile(video.video, video.fileName, video.mimeType, new TdApi.FileTypeVideo());
    }

    public static DownloadedFile valueOf (TdApi.Document document) {
      return new DownloadedFile(document.document, document.fileName, document.mimeType, new TdApi.FileTypeDocument());
    }

    public static DownloadedFile valueOf (TdApi.Audio audio) {
      return new DownloadedFile(audio.audio, audio.fileName, audio.mimeType, new TdApi.FileTypeAudio());
    }

    public static DownloadedFile valueOf (TdApi.VoiceNote voice) {
      return new DownloadedFile(voice.voice, "voice.ogg", voice.mimeType, new TdApi.FileTypeVoiceNote());
    }

    public int getFileId () {
      return file.id;
    }

    public int getFileSize () {
      return fileSize;
    }

    public String getPath () {
      return file.local.path;
    }

    public String getFileName (int copyNumber) {
      String resultName = null;
      if (!StringUtils.isEmpty(fileName)) {
        resultName = fileName;
      } else if (!StringUtils.isEmpty(mimeType)) {
        resultName = TGMimeType.extensionForMimeType(mimeType);
      }
      if (StringUtils.isEmpty(resultName)) {
        resultName = "telegramdownload." + getFileId();
      }
      if (copyNumber != 0) {
        int i = resultName.lastIndexOf('.');
        if (i != -1) {
          return resultName.substring(0, i) + " (" + copyNumber + ")" + resultName.substring(i);
        } else {
          return resultName + " (" + copyNumber + ")";
        }
      }
      return resultName;
    }

    public TdApi.FileType getFileType () {
      return fileType;
    }

    public String getMimeType () {
      return mimeType;
    }
  }

  public static int lastIndexOfSpace (String in, boolean allowEndOccurence) {
    if (StringUtils.isEmpty(in)) {
      return -1;
    }
    int lastFoundIndex = -1;
    int length = in.length();
    for (int i = 0; i < length;) {
      int codePoint = in.codePointAt(i);
      int size = Character.charCount(codePoint);
      if (codePoint != '\n' && (allowEndOccurence || (i + size) != length) && Character.getType(codePoint) == Character.SPACE_SEPARATOR) {
        lastFoundIndex = i;
      }
      i += size;
    }
    return lastFoundIndex;
  }

  public static int getFileId (TdApi.Message msg) {
    TdApi.File file = getFile(msg);
    return file != null ? file.id : 0;
  }

  public static @Nullable TdApi.File getFile (TdApi.Photo photo) {
    TdApi.PhotoSize size = MediaWrapper.buildTargetFile(photo);
    return size != null ? size.photo : null;
  }

  public static boolean isHeavyContent (TdApi.Message message) {
    if (message == null)
      return false;
    int constructor = message.content.getConstructor();
    if (constructor == TdApi.MessageText.CONSTRUCTOR) {
      constructor = convertToMessageContent(((TdApi.MessageText) message.content).webPage);
    }
    switch (constructor) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVideoNote.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static boolean canDeleteFile (TdApi.Message message) {
    return canDeleteFile(message, getFile(message));
  }

  public static boolean canDeleteFile (TdApi.Message message, TdApi.File file) {
    return isHeavyContent(message) && file != null && file.local != null && file.local.canBeDeleted && file.local.downloadedPrefixSize > 0;
  }

  public static @Nullable TdApi.File getFile (TdApi.Message msg) {
    if (msg == null) {
      return null;
    }
    switch (msg.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto photo = (TdApi.MessagePhoto) msg.content;
        return getFile(photo.photo);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        return ((TdApi.MessageAnimation) msg.content).animation.animation;
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        return ((TdApi.MessageVoiceNote) msg.content).voiceNote.voice;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        return ((TdApi.MessageVideoNote) msg.content).videoNote.video;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        return ((TdApi.MessageVideo) msg.content).video.video;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        return ((TdApi.MessageDocument) msg.content).document.document;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        return ((TdApi.MessageAudio) msg.content).audio.audio;
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        return Td.findBiggest(((TdApi.MessageChatChangePhoto) msg.content).photo.sizes).photo;
      }
      case TdApi.MessageText.CONSTRUCTOR: {
        return getFile(((TdApi.MessageText) msg.content).webPage);
      }
    }
    return null;
  }

  public static TdApi.File getFile (TGMessage msg) {
    TdApi.File file = getFile(msg.getMessage());
    if (file != null) {
      return file;
    }
    switch (msg.getMessage().content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        return ((TGMessageMedia) msg).getTargetFile();
      }
      case TdApi.MessageText.CONSTRUCTOR: {
        return ((TGMessageText) msg).getTargetFile();
      }
    }
    return null;
  }

  public static boolean canSaveToDownloads (TGMessage msg) {
    TdApi.File file = getFile(msg);
    return isFileLoaded(file);
  }

  public static void deleteFiles (final ViewController<?> context, List<TD.DownloadedFile> downloadedFiles, final @Nullable Runnable after) {
    TdApi.File[] files = new TdApi.File[downloadedFiles.size()];
    for (int i = 0; i < files.length; i++) {
      files[i] = downloadedFiles.get(i).getFile();
    }
    deleteFiles(context, files, after);
  }

  public static void deleteFiles (final ViewController<?> context, final TdApi.File[] files, final @Nullable Runnable after) {
    if (files == null || files.length == 0) {
      return;
    }
    long totalSize = 0;
    for (TdApi.File file : files) {
      totalSize += file.local.downloadedSize;
    }
    final String size = Strings.buildSize(totalSize);
    final long totalSizeFinal = totalSize;
    context.showOptions(Lang.getString(files.length == 1 ? R.string.DeleteFileHint : R.string.DeleteMultipleFilesHint), new int[]{R.id.btn_deleteFile, R.id.btn_cancel}, new String[]{Lang.getString(R.string.ClearX, size), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_deleteFile) {
        TdlibManager.instance().player().stopPlaybackIfPlayingAnyOf(files);
        context.context().closeFilePip(files);
        for (TdApi.File file : files) {
          final int[] signal = new int[1];
          context.tdlib().client().send(new TdApi.DeleteFile(file.id), object -> {
            signal[0]++;
            if (signal[0] == files.length) {
                /*long freedSize = 0;
                for (TdApi.File file : files) {
                  if (!TD.isFileLoaded(file)) {
                    freedSize += file.local.downloadedSize;
                  }
                }*/
              UI.showToast(Lang.getString(R.string.FreedX, Strings.buildSize(totalSizeFinal)), Toast.LENGTH_SHORT);
            }
          });
        }
        if (after != null) {
          after.run();
        }
      }
      return true;
    });
  }

  public static void saveFiles (List<DownloadedFile> files) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      if (context == null) {
        return;
      }
      if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        context.requestCustomPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, (code, granted) -> {
          if (granted) {
            saveFiles(files);
          }
        });
        return;
      }
    }
    Background.instance().post(() -> {
      int savedCount = 0;
      int allSavedType = -1;
      List<String> savedFiles = new ArrayList<>();
      for (DownloadedFile file : files) {
        boolean ok = false;
        int savedType = -1;
        switch (file.getFileType().getConstructor()) {
          case TdApi.FileTypeAnimation.CONSTRUCTOR: {
            ok = U.copyToGalleryImpl(file.getPath(), savedType = U.TYPE_GIF, null);
            break;
          }
          case TdApi.FileTypeVideo.CONSTRUCTOR: {
            ok = U.copyToGalleryImpl(file.getPath(), savedType = U.TYPE_VIDEO, null);
            break;
          }
          case TdApi.FileTypePhoto.CONSTRUCTOR: {
            ok = U.copyToGalleryImpl(file.getPath(), savedType = U.TYPE_PHOTO, null);
            break;
          }
          default: {
            savedType = U.TYPE_FILE;
            File savedFile = saveToDownloadsImpl(file);
            if (savedFile != null) {
              savedFiles.add(savedFile.getPath());
            }
            ok = savedFile != null;
            break;
          }
        }
        if (ok) {
          savedCount++;
          if (savedCount == 1) {
            allSavedType = savedType;
          } else if (allSavedType != savedType) {
            allSavedType = -1;
          }
        }
      }
      if (savedCount > 0) {
        if (allSavedType != -1) {
          switch (allSavedType) {
            case U.TYPE_PHOTO:
              if (savedCount == 1)
                UI.showToast(R.string.PhotoHasBeenSavedToGallery, Toast.LENGTH_SHORT);
              else
                UI.showToast(Lang.pluralBold(R.string.XPhotoSaved, savedCount), Toast.LENGTH_SHORT);
              break;
            case U.TYPE_VIDEO:
              if (savedCount == 1)
                UI.showToast(R.string.VideoHasBeenSavedToGallery, Toast.LENGTH_SHORT);
              else
                UI.showToast(Lang.pluralBold(R.string.XVideoSaved, savedCount), Toast.LENGTH_SHORT);
              break;
            case U.TYPE_GIF:
              if (savedCount == 1)
                UI.showToast(R.string.GifHasBeenSavedToGallery, Toast.LENGTH_SHORT);
              else
                UI.showToast(Lang.pluralBold(R.string.SavedXGifToGallery, savedCount), Toast.LENGTH_SHORT);
              break;
            case U.TYPE_FILE:
              if (savedCount == 1) {
                UI.showToast(Lang.getStringBold(R.string.DownloadedToPath, savedFiles.get(0)), Toast.LENGTH_LONG);
              } else {
                UI.showToast(Lang.pluralBold(R.string.DownloadedXFiles, savedCount, Strings.join("\n", savedFiles)), Toast.LENGTH_LONG);
              }
              break;
          }
        } else {
          UI.showToast(Lang.pluralBold(R.string.SavedXFiles, savedCount), Toast.LENGTH_SHORT);
        }
      }
    });
  }

  public static void saveFile (DownloadedFile file) {
    switch (file.getFileType().getConstructor()) {
      case TdApi.FileTypeAnimation.CONSTRUCTOR: {
        U.copyToGallery(file.getPath(), U.TYPE_GIF);
        break;
      }
      case TdApi.FileTypeVideo.CONSTRUCTOR: {
        U.copyToGallery(file.getPath(), U.TYPE_VIDEO);
        break;
      }
      case TdApi.FileTypePhoto.CONSTRUCTOR: {
        U.copyToGallery(file.getPath(), U.TYPE_PHOTO);
        break;
      }
      default: {
        saveToDownloads(file);
        break;
      }
    }
  }

  public static @Nullable DownloadedFile getDownloadedFile (TGWebPage webPage) {
    if (webPage == null)
      return null;
    TdApi.WebPage rawPage = webPage.getWebPage();
    FileComponent component = webPage.getFileComponent();
    if (component != null) {
      if (component.isAudio() && rawPage.audio != null) {
        return TD.DownloadedFile.valueOf(rawPage.audio);
      } else if (component.isVoice() && rawPage.voiceNote != null) {
        return TD.DownloadedFile.valueOf(rawPage.voiceNote);
      } else if (component.isDocument() && rawPage.document != null) {
        return TD.DownloadedFile.valueOf(rawPage.document);
      }
    }
    MediaWrapper wrapper = webPage.getMediaWrapper();
    if (wrapper != null) {
      if (wrapper.isGif()) {
        return TD.DownloadedFile.valueOf(wrapper.getAnimation());
      } else if (wrapper.isVideo()) {
        return TD.DownloadedFile.valueOf(wrapper.getVideo());
      } else if (wrapper.isPhoto()) {
        return TD.DownloadedFile.valueOfPhoto(wrapper.getTargetFile(), rawPage.sticker != null);
      }
    }

    switch (webPage.getType()) {
      case TGWebPage.TYPE_GIF: {
        if (rawPage.animation != null) {
          return TD.DownloadedFile.valueOf(rawPage.animation);
        }
        return null;
      }
      case TGWebPage.TYPE_VIDEO: {
        if (rawPage.video != null) {
          return TD.DownloadedFile.valueOf(rawPage.video);
        }
        break;
      }
      case TGWebPage.TYPE_TELEGRAM_BACKGROUND: {
        if (rawPage.document != null) {
          return TD.DownloadedFile.valueOf(rawPage.document);
        }
        return null;
      }
      case TGWebPage.TYPE_PHOTO: {
        if (rawPage.photo != null) {
          return TD.DownloadedFile.valueOfPhoto(webPage.getTargetFile(), false);
        } else if (rawPage.sticker != null) {
          return TD.DownloadedFile.valueOfPhoto(webPage.getTargetFile(), true);
        }
        break;
      }
    }

    return null;
  }

  public static @NonNull List<DownloadedFile> getDownloadedFiles (TdApi.Message[] messages) {
    List<DownloadedFile> list = new ArrayList<>();
    for (TdApi.Message message : messages) {
      DownloadedFile file = getDownloadedFile(message);
      if (file != null)
        list.add(file);
    }
    return list;
  }

  public static @Nullable DownloadedFile getDownloadedFile (TdApi.Message msg) {
    switch (msg.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize size = MediaWrapper.buildTargetFile(((TdApi.MessagePhoto) msg.content).photo);
        if (size != null && TD.isFileLoaded(size.photo)) {
          return DownloadedFile.valueOfPhoto(size.photo, false);
        }
        return null;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) msg.content).animation;
        if (animation != null && TD.isFileLoaded(animation.animation)) {
          return DownloadedFile.valueOf(animation);
        }
        return null;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video video = ((TdApi.MessageVideo) msg.content).video;
        if (video != null && TD.isFileLoaded(video.video)) {
          return DownloadedFile.valueOf(video);
        }
        return null;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) msg.content).document;
        if (document != null && TD.isFileLoaded(document.document)) {
          return DownloadedFile.valueOf(document);
        }
        return null;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) msg.content).audio;
        if (audio != null && TD.isFileLoaded(audio.audio)) {
          return DownloadedFile.valueOf(audio);
        }
        return null;
      }
    }
    return null;
  }

  private static File saveToDownloadsImpl (final DownloadedFile file) {
    final File sourceFile = new File(file.getPath());
    if (!sourceFile.exists()) {
      return null;
    }

    final File destDir;

    switch (file.fileType.getConstructor()) {
      case TdApi.FileTypeAudio.CONSTRUCTOR:
      case TdApi.FileTypeVoiceNote.CONSTRUCTOR: {
        destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        break;
      }
      default: {
        destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        break;
      }
    }

    if (!destDir.exists() && !destDir.mkdir()) {
      return null;
    }

    File destFile;
    int i = 0;
    do {
      destFile = new File(destDir, file.getFileName(i++));
    } while (destFile.exists());

    final File resultFile = destFile;

    if (!FileUtils.copy(sourceFile, destFile))
      return null;

    U.scanFile(destFile);

    if (file.fileType.getConstructor() == TdApi.FileTypeAudio.CONSTRUCTOR) {
      U.addToGallery(resultFile);
      return resultFile;
    }

    final DownloadManager downloadManager = (DownloadManager) UI.getAppContext().getSystemService(Context.DOWNLOAD_SERVICE);
    String name = resultFile.getName();
    String mimeType = file.mimeType;
    if (StringUtils.isEmpty(mimeType)) {
      String extension = U.getExtension(name);
      if (!StringUtils.isEmpty(extension)) {
        mimeType = TGMimeType.mimeTypeForExtension(extension);
      }
    }
    if (StringUtils.isEmpty(name)) {
      if (StringUtils.isEmpty(mimeType)) {
        name = "file";
      } else {
        name = "file." + TGMimeType.extensionForMimeType(mimeType);
      }
    }
    if (downloadManager != null) {
      final String nameFinal = name;
      final String mimeTypeFinal = mimeType;
      try {
        downloadManager.addCompletedDownload(nameFinal, nameFinal, true, mimeTypeFinal, resultFile.getAbsolutePath(), resultFile.length(), true);
      } catch (Throwable t) {
        Log.w("Failed to notify about saved download", t);
      }
    }
    return resultFile;
  }

  private static void saveToDownloadsImpl (final File sourceFile, final String sourceMimeType) {
    if (!sourceFile.exists()) {
      return;
    }
    final File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    if (!destDir.exists() && !destDir.mkdir()) {
      return;
    }
    String extension = U.getExtension(sourceFile.getName());
    final File destFile = StringUtils.isEmpty(extension) ? U.newFile(destDir, sourceFile.getName(), TGMimeType.mimeTypeForExtension(extension)) : U.newFile(destDir, sourceFile.getName());
    if (!FileUtils.copy(sourceFile, destFile))
      return;
    UI.post(() -> {
      final DownloadManager downloadManager = (DownloadManager) UI.getAppContext().getSystemService(Context.DOWNLOAD_SERVICE);
      if (downloadManager != null) {
        Background.instance().post(() -> {
          try {
            downloadManager.addCompletedDownload(destFile.getName(), destFile.getName(), true, sourceMimeType, destFile.getAbsolutePath(), destFile.length(), true);
          } catch (Throwable t) {
            Log.w("Failed to notify about saved download", t);
            UI.showToast("File added to downloads: " + destFile.getPath(), Toast.LENGTH_SHORT);
          }
        });
      }
    });
  }

  public static void saveToDownloads (final File file, final String mimeType) {
    if (file == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      if (context == null) {
        return;
      }
      if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        context.requestCustomPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, (code, granted) -> {
          if (granted) {
            saveToDownloads(file, mimeType);
          }
        });
        return;
      }
    }

    Background.instance().post(() -> saveToDownloadsImpl(file, mimeType));
  }

  public static void saveToDownloads (final DownloadedFile file) {
    if (file == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      if (context == null) {
        return;
      }
      if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        context.requestCustomPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, (code, granted) -> {
          if (granted) {
            saveToDownloads(file);
          }
        });
        return;
      }
    }

    Background.instance().post(() -> {
      File savedFile = saveToDownloadsImpl(file);
      if (savedFile != null) {
        UI.showToast(Lang.getString(R.string.DownloadedToPath, savedFile.getPath()), Toast.LENGTH_LONG);
      }
    });
  }

  // other

  public static boolean isMessageOpened (TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return ((TdApi.MessageVoiceNote) message.content).isListened;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return ((TdApi.MessageVideoNote) message.content).isViewed;
    }
    return false;
  }

  public static void setMessageOpened (TdApi.Message message) {
    // FIXME when TDLib will get a field
    switch (message.content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        ((TdApi.MessageVoiceNote) message.content).isListened = true;
        break;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        ((TdApi.MessageVideoNote) message.content).isViewed = true;
        break;
    }
  }

  public static CustomTypefaceSpan newSpan (@NonNull TdApi.TextEntityType type) {
    return new CustomTypefaceSpan(null, 0).setEntityType(type);
  }

  public static CustomTypefaceSpan newBoldSpan (@NonNull String text) {
    boolean needFakeBold = Text.needFakeBold(text);
    return new CustomTypefaceSpan(needFakeBold ? null : Fonts.getRobotoMedium(), 0).setFakeBold(needFakeBold).setEntityType(new TdApi.TextEntityTypeBold());
  }

  public static TdApi.FormattedText newText (@NonNull CharSequence text) {
    return new TdApi.FormattedText(text.toString(), toEntities(text, false));
  }

  public static CharSequence toDisplayCharSequence (TdApi.FormattedText text) {
    return toDisplayCharSequence(text, null);
  }

  public static CharSequence toDisplayCharSequence (TdApi.FormattedText text, @Nullable Typeface defaultTypeface) {
    if (text == null)
      return null;
    if (text.entities == null || text.entities.length == 0)
      return text.text;
    SpannableStringBuilder b = null;
    for (TdApi.TextEntity entity : text.entities) {
      CustomTypefaceSpan span = toDisplaySpan(entity.type, defaultTypeface, Text.needFakeBold(text.text, entity.offset, entity.offset + entity.length));
      if (span != null) {
        if (b == null)
          b = new SpannableStringBuilder(text.text);
        b.setSpan(span, entity.offset, entity.offset + entity.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return b != null ? SpannableString.valueOf(b) : text.text;
  }

  public static CharSequence toMarkdown (TdApi.FormattedText text) {
    if (text == null)
      return null;
    if (text.entities == null || text.entities.length == 0)
      return text.text;
    TdApi.Object result = Client.execute(new TdApi.GetMarkdownText(text));
    if (!(result instanceof TdApi.FormattedText)) {
      Log.w("getMarkdownText: %s", result);
      return text.text;
    }
    return toCharSequence((TdApi.FormattedText) result, true, true);
  }

  public static CharSequence toCopyText (TdApi.FormattedText text) {
    return toCharSequence(text);
  }

  public static CharSequence toCharSequence (TdApi.FormattedText text) {
    return toCharSequence(text, true, true);
  }

  public static CharSequence toCharSequence (TdApi.FormattedText text, boolean allowInternal, boolean allowSpoilerContent) {
    if (text == null)
      return null;
    if (text.entities == null || text.entities.length == 0)
      return text.text;
    SpannableStringBuilder b = null;
    boolean hasSpoilers = false;
    for (TdApi.TextEntity entity : text.entities) {
      boolean isSpoiler = entity.type.getConstructor() == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR;
      if (isSpoiler) {
        hasSpoilers = true;
      }
      Object span = isSpoiler && !allowSpoilerContent ? null : toSpan(entity.type, allowInternal);
      if (span != null) {
        if (b == null)
          b = new SpannableStringBuilder(text.text);
        b.setSpan(span, entity.offset, entity.offset + entity.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    if (!allowSpoilerContent && hasSpoilers) {
      StringBuilder builder = b != null ? null : new StringBuilder(text.text);
      for (int i = text.entities.length - 1; i >= 0; i--) {
        TdApi.TextEntity entity = text.entities[i];
        if (entity.type.getConstructor() == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR) {
          String replacement = StringUtils.multiply(SPOILER_REPLACEMENT_CHAR, entity.length);
          if (b != null) {
            b.delete(entity.offset, entity.offset + entity.length);
            b.insert(entity.offset, replacement);
          } else {
            builder.delete(entity.offset, entity.offset + entity.length);
            builder.insert(entity.offset, replacement);
          }
        }
      }
      if (builder != null) {
        return builder.toString();
      }
    }
    return b != null ? SpannableString.valueOf(b) : text.text;
  }

  public static CustomTypefaceSpan toDisplaySpan (TdApi.TextEntityType type) {
    return toDisplaySpan(type, null, false);
  }

  public static CustomTypefaceSpan toDisplaySpan (TdApi.TextEntityType type, @Nullable Typeface defaultTypeface, boolean needFakeBold) {
    if (type == null)
      return null;
    CustomTypefaceSpan span;
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        span = new CustomTypefaceSpan(defaultTypeface, R.id.theme_color_textLink);
        break;
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        if (needFakeBold)
          span = new CustomTypefaceSpan(null, 0).setFakeBold(true);
        else
          span = new CustomTypefaceSpan(Fonts.getRobotoMedium(), 0);
        break;
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        span = new CustomTypefaceSpan(Fonts.getRobotoItalic(), 0);
        break;
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        span = new CustomTypefaceSpan(Fonts.getRobotoMono(), 0);
        break;
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        // proper flags are set inside setEntityType
        span = new CustomTypefaceSpan(defaultTypeface, 0);
        break;
      default:
        return null;
    }
    span.setEntityType(type);
    return span;
  }

  public static CharacterStyle toSpan (TdApi.TextEntityType type) {
    return toSpan(type, true);
  }

  private static final String SPOILER_REPLACEMENT_CHAR = "▒";
  private static final int SPOILER_BACKGROUND_COLOR = 0xffa9a9a9;

  public static CharacterStyle toSpan (TdApi.TextEntityType type, boolean allowInternal) {
    if (type == null)
      return null;
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        return new StyleSpan(Typeface.BOLD);
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        return new StyleSpan(Typeface.ITALIC);
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
        return Fonts.FORCE_BUILTIN_MONO ? new TypefaceSpan(Fonts.getRobotoMono()) : new TypefaceSpan("monospace");
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return new URLSpan(((TdApi.TextEntityTypeTextUrl) type).url);
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        return new StrikethroughSpan();
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        return Config.SUPPORT_SYSTEM_UNDERLINE_SPAN ? new UnderlineSpan() : toDisplaySpan(type);
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        return new BackgroundColorSpan(SPOILER_BACKGROUND_COLOR);
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return allowInternal ? new CustomTypefaceSpan(null, R.id.theme_color_textLink).setEntityType(type) : null;
    }
    return null;
  }

  public static TdApi.TextEntityType[] toEntityType (CharacterStyle span) {
    if (!canConvertToEntityType(span))
      return null;
    if (span instanceof CustomTypefaceSpan)
      return new TdApi.TextEntityType[] {((CustomTypefaceSpan) span).getEntityType()};
    if (span instanceof URLSpan) {
      String url = ((URLSpan) span).getURL();
      if (!Strings.isValidLink(url))
        return null;
      return new TdApi.TextEntityType[]{new TdApi.TextEntityTypeTextUrl(url)};
    }
    if (span instanceof StyleSpan) {
      switch (((StyleSpan) span).getStyle()) {
        case Typeface.BOLD:
          return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeBold()};
        case Typeface.BOLD_ITALIC:
          return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeBold(), new TdApi.TextEntityTypeItalic()};
        case Typeface.ITALIC:
          return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeItalic()};
        default:
          return null;
      }
    } else if (span instanceof TypefaceSpan && "monospace".equals(((TypefaceSpan) span).getFamily())) {
      return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeCode()};
    } else if (span instanceof BackgroundColorSpan) {
      final int color = ((BackgroundColorSpan) span).getBackgroundColor();
      if (color == SPOILER_BACKGROUND_COLOR) {
        return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeSpoiler()};
      }
      return null;
    } else if (span instanceof StrikethroughSpan) {
      return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeStrikethrough()};
    } else if (Config.SUPPORT_SYSTEM_UNDERLINE_SPAN && span instanceof UnderlineSpan) {
      return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeUnderline()};
    }
    return null;
  }

  public static boolean canConvertToEntityType (CharacterStyle span) {
    if (span instanceof CustomTypefaceSpan)
      return ((CustomTypefaceSpan) span).getEntityType() != null;
    if (span instanceof URLSpan)
      return Strings.isValidLink(((URLSpan) span).getURL());
    if (span instanceof StyleSpan) {
      StyleSpan styleSpan = (StyleSpan) span;
      switch (styleSpan.getStyle()) {
        case Typeface.BOLD:
        case Typeface.ITALIC:
        case Typeface.BOLD_ITALIC:
          return true;
      }
      return false;
    }
    if (span instanceof TypefaceSpan)
      return "monospace".equals(((TypefaceSpan) span).getFamily());
    if (span instanceof BackgroundColorSpan) {
      final int backgroundColor = ((BackgroundColorSpan) span).getBackgroundColor();
      if (backgroundColor == SPOILER_BACKGROUND_COLOR) {
        return true;
      }
    }
    return span instanceof StrikethroughSpan || (Config.SUPPORT_SYSTEM_UNDERLINE_SPAN && span instanceof UnderlineSpan);
  }

  public static TdApi.FormattedText toFormattedText (CharSequence cs, boolean onlyLinks) {
    TdApi.TextEntity[] entities = toEntities(cs, onlyLinks);
    return new TdApi.FormattedText(cs.toString(), entities);
  }

  public static TdApi.TextEntity[] toEntities (CharSequence cs, boolean onlyLinks) {
    if (!(cs instanceof Spanned))
      return null;
    CharacterStyle[] spans = ((Spanned) cs).getSpans(0, cs.length(), CharacterStyle.class);
    if (spans == null || spans.length == 0)
      return null;

    List<TdApi.TextEntity> entities = null;
    for (CharacterStyle span : spans) {
      TdApi.TextEntityType[] types = toEntityType(span);
      if (types == null || types.length == 0)
        continue;
      int start = ((Spanned) cs).getSpanStart(span);
      int end = ((Spanned) cs).getSpanEnd(span);
      for (TdApi.TextEntityType type : types) {
        if (onlyLinks && type.getConstructor() != TdApi.TextEntityTypeTextUrl.CONSTRUCTOR)
          continue;
        if (entities == null)
          entities = new ArrayList<>();
        entities.add(new TdApi.TextEntity(start, end - start, type));
      }
    }
    if (entities != null && !entities.isEmpty()) {
      fixEntities(entities);
      return entities.toArray(new TdApi.TextEntity[0]);
    }
    return null;
  }

  private static void fixEntities (List<TdApi.TextEntity> entities) {
    if (entities != null && !entities.isEmpty()) {
      int lookupIndex = 0;
      do {
        Td.sort(entities);
        lookupIndex = splitEntities(entities, lookupIndex);
      } while (lookupIndex != -1);
    }
  }

  private static int splitEntities (List<TdApi.TextEntity> entities, int startIndex) {
    for (int i = Math.max(startIndex, 1); i < entities.size(); i++) {
      for (int j = i - 1; j >= 0; j--) {
        TdApi.TextEntity prev = entities.get(j);
        TdApi.TextEntity entity = entities.get(i);
        if (entity.offset >= prev.offset + prev.length || entity.offset + entity.length <= prev.offset + prev.length)
          continue;
        int length = prev.offset + prev.length - entity.offset;
        entities.remove(i);
        entities.addAll(i, Arrays.asList(
          new TdApi.TextEntity(entity.offset, length, entity.type),
          new TdApi.TextEntity(prev.offset + prev.length, entity.length - length, entity.type)
        ));
        return i;
      }
    }
    return -1;
  }

  public static List<TdApi.SendMessage> sendTextMessage (long chatId, long messageThreadId, long replyToMessageId, TdApi.MessageSendOptions sendOptions, @NonNull TdApi.InputMessageContent content, int maxLength) {
    if (content.getConstructor() != TdApi.InputMessageText.CONSTRUCTOR) {
      return Collections.singletonList(new TdApi.SendMessage(chatId, messageThreadId, replyToMessageId, sendOptions, null, content));
    }
    TdApi.InputMessageText textContent = (TdApi.InputMessageText) content;
    TdApi.FormattedText text = textContent.text;
    int textLength = text.text.length();
    List<TdApi.SendMessage> list = new ArrayList<>();
    if (textLength <= maxLength) {
      // Nothing to split, send as is
      list.add(new TdApi.SendMessage(chatId, messageThreadId, replyToMessageId, sendOptions, null, textContent));
      return list;
    }
    int startIndex = 0;
    while (startIndex < textLength) {
      int length = Math.min(maxLength, textLength - startIndex);
      int endIndex = startIndex + length;
      if (length == maxLength) {
        int lastSplitterIndex = Text.lastIndexOfSplitter(text.text, endIndex - length / 4 /*look only within the last quarter of the text*/, endIndex, null);
        if (lastSplitterIndex != -1 && lastSplitterIndex > startIndex && lastSplitterIndex < endIndex) {
          endIndex = lastSplitterIndex;
        }
      }
      TdApi.FormattedText substring = Td.substring(text, startIndex, endIndex);
      boolean first = startIndex == 0;
      list.add(new TdApi.SendMessage(chatId, messageThreadId, replyToMessageId, sendOptions, null, new TdApi.InputMessageText(substring, first && textContent.disableWebPagePreview, first && textContent.clearDraft)));
      startIndex += length;
    }
    return list;
  }

  public static int getForwardDate (TdApi.MessageForwardInfo forward) {
    return forward.date;
  }

  /*public static String addEmoji (Emoji emoji, int stringRes, String text) {
    if (Strings.isEmpty(text)) {
      return stringRes != 0 ? emoji + " " + Lang.getString(stringRes) : emoji.textRepresentation;
    } else if (text.startsWith(emoji.textRepresentation)) {
      return text;
    } else {
      return emoji + " " + text;
    }
  }*/

  public static class ContentPreview {
    public final @Nullable Emoji emoji, parentEmoji;
    public final @StringRes int placeholderText;
    public final @Nullable TdApi.FormattedText formattedText;
    public final boolean isTranslatable;
    public final boolean hideAuthor;

    public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes) {
      this(emoji, placeholderTextRes, (TdApi.FormattedText) null);
    }

    public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable String text) {
      this(emoji, placeholderTextRes, StringUtils.isEmpty(text) ? null : new TdApi.FormattedText(text, null), false);
    }

    public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable TdApi.FormattedText text) {
      this(emoji, placeholderTextRes, text, false);
    }

    public ContentPreview (@Nullable String text, boolean textTranslatable) {
      this(null, 0, text, textTranslatable);
    }

    public ContentPreview (@Nullable TdApi.FormattedText text, boolean textTranslatable) {
      this(null, 0, text, textTranslatable);
    }

    public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable TdApi.FormattedText text, boolean textTranslatable) {
      this(emoji, placeholderTextRes, text, textTranslatable, false, null);
    }

    public ContentPreview (@Nullable Emoji emoji, @StringRes int placeholderTextRes, @Nullable String text, boolean textTranslatable) {
      this(emoji, placeholderTextRes, StringUtils.isEmpty(text) ? null : new TdApi.FormattedText(text, null), textTranslatable, false, null);
    }

    public ContentPreview (@Nullable Emoji emoji, ContentPreview copy) {
      this(copy.emoji, copy.placeholderText, copy.formattedText, copy.isTranslatable, copy.hideAuthor, emoji);
    }

    public ContentPreview (@Nullable Emoji emoji, int placeholderText, @Nullable TdApi.FormattedText formattedText, boolean isTranslatable, boolean hideAuthor, @Nullable Emoji parentEmoji) {
      this.emoji = emoji;
      this.placeholderText = placeholderText;
      this.formattedText = formattedText;
      this.isTranslatable = isTranslatable;
      this.hideAuthor = hideAuthor;
      this.parentEmoji = parentEmoji;
    }

    @NonNull
    public String buildText (boolean allowIcon) {
      if (emoji == null || (allowIcon && emoji.iconRepresentation != 0)) {
        return Td.isEmpty(formattedText) ? (placeholderText != 0 ? Lang.getString(placeholderText) : "") : formattedText.text;
      } else if (Td.isEmpty(formattedText)) {
        return placeholderText != 0 ? emoji.textRepresentation + " " + Lang.getString(placeholderText) : emoji.textRepresentation;
      } else if (formattedText.text.startsWith(emoji.textRepresentation)) {
        return formattedText.text;
      } else {
        return emoji.textRepresentation + " " + formattedText.text;
      }
    }

    public TdApi.FormattedText buildFormattedText (boolean allowIcon) {
      if (emoji == null || (allowIcon && emoji.iconRepresentation != 0)) {
        return Td.isEmpty(formattedText) ? new TdApi.FormattedText(placeholderText != 0 ? Lang.getString(placeholderText) : "", null) : formattedText;
      } else if (Td.isEmpty(formattedText)) {
        return new TdApi.FormattedText(placeholderText != 0 ? emoji.textRepresentation + " " + Lang.getString(placeholderText) : emoji.textRepresentation, null);
      } else if (formattedText.text.startsWith(emoji.textRepresentation)) {
        return formattedText;
      } else {
        return TD.withPrefix(emoji.textRepresentation + " ", formattedText);
      }
    }

    @Override
    @NonNull
    public String toString () {
      return buildText(false);
    }

    private Refresher refresher;
    private boolean isMediaGroup;

    public ContentPreview setRefresher (Refresher refresher, boolean isMediaGroup) {
      this.refresher = refresher;
      this.isMediaGroup = isMediaGroup;
      return this;
    }

    public boolean hasRefresher () {
      return refresher != null;
    }

    public boolean isMediaGroup () {
      return isMediaGroup;
    }

    public void refreshContent (@NonNull RefreshCallback callback) {
      if (refresher != null) {
        refresher.runRefresher(this, callback);
      }
    }

    private Tdlib.Album album;

    @Nullable
    public Tdlib.Album getAlbum () {
      return album;
    }

    public interface Refresher {
      void runRefresher (ContentPreview oldPreview, RefreshCallback callback);
    }

    public interface RefreshCallback {
      void onContentPreviewChanged (long chatId, long messageId, ContentPreview newPreview, ContentPreview oldPreview);
      default void onContentPreviewNotChanged (long chatId, long messageId, ContentPreview oldContent) { }
    }
  }

  @NonNull
  public static ContentPreview getChatListPreview (Tdlib tdlib, long chatId, TdApi.Message message) {
    return getContentPreview(tdlib, chatId, message, true, true);
  }

  @NonNull
  public static ContentPreview getNotificationPreview (Tdlib tdlib, long chatId, TdApi.Message message, boolean allowContent) {
    return getContentPreview(tdlib, chatId, message, allowContent, false);
  }

  private static final int ARG_NONE = 0;
  private static final int ARG_TRUE = 1;
  private static final int ARG_POLL_QUIZ = 1;
  private static final int ARG_CALL_DECLINED = -1;
  private static final int ARG_CALL_MISSED = -2;

  @NonNull
  private static ContentPreview getContentPreview (Tdlib tdlib, long chatId, TdApi.Message message, boolean allowContent, boolean isChatList) {
    if (Settings.instance().needRestrictContent()) {
      if (!StringUtils.isEmpty(message.restrictionReason)) {
        return new ContentPreview(TD.EMOJI_ERROR, 0, message.restrictionReason, false);
      }
      if (!isChatList) { // Otherwise lookup is done inside TGChat for performance reason
        String restrictionReason = tdlib.chatRestrictionReason(chatId);
        if (restrictionReason != null) {
          return new TD.ContentPreview(TD.EMOJI_ERROR, 0, restrictionReason, false);
        }
      }
    }
    int type = message.content.getConstructor();
    TdApi.FormattedText formattedText;
    if (allowContent) {
      formattedText = Td.textOrCaption(message.content);
      if (message.isOutgoing) {
        TdApi.FormattedText pendingText = tdlib.getPendingFormattedText(message.chatId, message.id);
        if (pendingText != null) {
          formattedText = pendingText;
        }
      }
    } else {
      formattedText = null;
    }
    String alternativeText = null;
    boolean alternativeTextTranslatable = false;
    int arg1 = ARG_NONE;
    switch (type) {
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.MessageText messageText = (TdApi.MessageText) message.content;
        if (!Td.isEmpty(messageText.text) && messageText.text.entities != null) {
          boolean isUrl = false;
          for (TdApi.TextEntity entity : messageText.text.entities) {
            switch (entity.type.getConstructor()) {
              case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
              case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
                if (entity.offset == 0 && (
                  entity.length == messageText.text.text.length() ||
                  entity.type.getConstructor() != TdApi.TextEntityTypeTextUrl.CONSTRUCTOR ||
                  !StringUtils.isEmptyOrInvisible(Td.substring(messageText.text.text, entity
                  )))
                ) {
                  isUrl = true;
                  break;
                }
                break;
              }
            }
          }
          if (isUrl) {
            arg1 = ARG_TRUE;
          }
        }
        break;
      }
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
        TdApi.MessageAnimatedEmoji animatedEmoji = (TdApi.MessageAnimatedEmoji) message.content;
        alternativeText = animatedEmoji.emoji;
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageDocument) message.content).document.fileName;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        int duration = ((TdApi.MessageVoiceNote) message.content).voiceNote.duration;
        if (duration > 0) {
          alternativeText = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentVoice), Strings.buildDuration(duration));
          alternativeTextTranslatable = true;
        }
        break;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        int duration = ((TdApi.MessageVideoNote) message.content).videoNote.duration;
        if (duration > 0) {
          alternativeText = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentRoundVideo), Strings.buildDuration(duration));
          alternativeTextTranslatable = true;
        }
        break;
      }
      case TdApi.MessageAudio.CONSTRUCTOR:
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        alternativeText = Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
        alternativeTextTranslatable = !hasTitle(audio) || !hasSubtitle(audio);
        break;
      case TdApi.MessageContact.CONSTRUCTOR: {
        TdApi.Contact contact = ((TdApi.MessageContact) message.content).contact;
        String name = TD.getUserName(contact.firstName, contact.lastName);
        if (!StringUtils.isEmpty(name))
          alternativeText = name;
        break;
      }
      case TdApi.MessagePoll.CONSTRUCTOR:
        alternativeText = ((TdApi.MessagePoll) message.content).poll.question;
        arg1 = ((TdApi.MessagePoll) message.content).poll.type.getConstructor() == TdApi.PollTypeRegular.CONSTRUCTOR ? ARG_NONE : ARG_POLL_QUIZ;
        break;
      case TdApi.MessageDice.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageDice) message.content).emoji;
        arg1 = ((TdApi.MessageDice) message.content).value;
        break;
      case TdApi.MessageCall.CONSTRUCTOR: {
        switch (((TdApi.MessageCall) message.content).discardReason.getConstructor()) {
          case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
            arg1 = ARG_CALL_DECLINED;
            break;
          case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
            arg1 = ARG_CALL_MISSED;
            break;
          default:
            arg1 = ((TdApi.MessageCall) message.content).duration;
            break;
        }
        break;
      }
      case TdApi.MessageLocation.CONSTRUCTOR: {
        TdApi.MessageLocation location = ((TdApi.MessageLocation) message.content);
        alternativeText = location.livePeriod == 0 || location.expiresIn == 0 ? null : "live";
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageGame) message.content).game.title;
        break;
      case TdApi.MessageSticker.CONSTRUCTOR:
        TdApi.Sticker sticker = ((TdApi.MessageSticker) message.content).sticker;
        alternativeText = Td.isAnimated(sticker.type) ? "animated" + sticker.emoji : sticker.emoji;
        break;
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) message.content;
        alternativeText = CurrencyUtils.buildAmount(invoice.currency, invoice.totalAmount);
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR:
        if (((TdApi.MessagePhoto) message.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.SelfDestructPhoto, formattedText);
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        if (((TdApi.MessageVideo) message.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.SelfDestructVideo, formattedText);
        break;
      case TdApi.MessagePinMessage.CONSTRUCTOR: {
        long pinnedMessageId = ((TdApi.MessagePinMessage) message.content).messageId;
        TdApi.Message pinnedMessage = pinnedMessageId != 0 ? tdlib.getMessageLocally(message.chatId, pinnedMessageId) : null;
        if (pinnedMessage != null) {
          return new ContentPreview(EMOJI_PIN, getContentPreview(tdlib, chatId, pinnedMessage, allowContent, isChatList));
        } else {
          return new ContentPreview(EMOJI_PIN, R.string.ChatContentPinned)
            .setRefresher((oldPreview, callback) -> tdlib.getMessage(chatId, pinnedMessageId, remotePinnedMessage -> {
            if (remotePinnedMessage != null) {
              callback.onContentPreviewChanged(chatId, message.id, new ContentPreview(EMOJI_PIN, getContentPreview(tdlib, chatId, remotePinnedMessage, allowContent, isChatList)), oldPreview);
            } else {
              callback.onContentPreviewNotChanged(chatId, message.id, oldPreview);
            }
          }), false);
        }
      }
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        TdApi.MessageGameScore score = (TdApi.MessageGameScore) message.content;
        TdApi.Message gameMessage = tdlib.getMessageLocally(message.chatId, score.gameMessageId);
        String gameTitle = gameMessage != null && gameMessage.content.getConstructor() == TdApi.MessageGame.CONSTRUCTOR ? TD.getGameName(((TdApi.MessageGame) gameMessage.content).game, false) : null;
        if (!StringUtils.isEmpty(gameTitle)) {
          return new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScoredInGame : R.string.game_ActionScoredInGame, score.score, gameTitle), true);
        } else {
          return new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScored : R.string.game_ActionScored, score.score), true)
            .setRefresher(gameMessage != null ? null :
              (oldPreview, callback) -> tdlib.getMessage(message.chatId, score.gameMessageId, remoteGameMessage -> {
              if (remoteGameMessage != null && remoteGameMessage.content.getConstructor() == TdApi.MessageGame.CONSTRUCTOR) {
                String newGameTitle = TD.getGameName(((TdApi.MessageGame) remoteGameMessage.content).game, false);
                if (!StringUtils.isEmpty(newGameTitle)) {
                  callback.onContentPreviewChanged(message.chatId, message.id, new ContentPreview(EMOJI_GAME, 0, Lang.plural(message.isOutgoing ? R.string.game_ActionYouScoredInGame : R.string.game_ActionScoredInGame, score.score, newGameTitle), true), oldPreview);
                  return;
                }
              }
              callback.onContentPreviewNotChanged(message.chatId, message.id, oldPreview);
          }), false);
        }
      }
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
        TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) message.content;
        if (tdlib.isSelfSender(alert.travelerId)) {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityYouKm : R.string.ChatContentProximityYouM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.watcherId, true)), true);
        } else if (tdlib.isSelfSender(alert.watcherId)) {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityFromYouKm : R.string.ChatContentProximityFromYouM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.travelerId, true)), true);
        } else {
          return new ContentPreview(EMOJI_LOCATION, 0, Lang.plural(alert.distance >= 1000 ? R.string.ChatContentProximityKm : R.string.ChatContentProximityM, alert.distance >= 1000 ? alert.distance / 1000 : alert.distance, tdlib.senderName(alert.travelerId, true), tdlib.senderName(alert.watcherId, true)), true);
        }
      }
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR: {
        if (message.isChannelPost) {
          return new ContentPreview(EMOJI_CALL, message.isOutgoing ? R.string.ChatContentLiveStreamStarted_outgoing : R.string.ChatContentLiveStreamStarted);
        } else {
          return new ContentPreview(EMOJI_CALL, message.isOutgoing ? R.string.ChatContentVoiceChatStarted_outgoing : R.string.ChatContentVoiceChatStarted);
        }
      }
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
        TdApi.MessageVideoChatEnded videoChatOrLiveStream = (TdApi.MessageVideoChatEnded) message.content;
        if (message.isChannelPost) {
          return new ContentPreview(EMOJI_CALL_END, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentLiveStreamFinished_outgoing : R.string.ChatContentLiveStreamFinished, Lang.getCallDuration(videoChatOrLiveStream.duration)), true);
        } else {
          return new ContentPreview(EMOJI_CALL_END, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentVoiceChatFinished_outgoing : R.string.ChatContentVoiceChatFinished, Lang.getCallDuration(videoChatOrLiveStream.duration)), true);
        }
      }
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR: {
        TdApi.MessageInviteVideoChatParticipants info = (TdApi.MessageInviteVideoChatParticipants) message.content;
        if (message.isChannelPost) {
          if (info.userIds.length == 1) {
            long userId = info.userIds[0];
            if (tdlib.isSelfUserId(userId)) {
              return new ContentPreview(EMOJI_GROUP_INVITE, R.string.ChatContentLiveStreamInviteYou);
            } else {
              return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentLiveStreamInvite_outgoing : R.string.ChatContentLiveStreamInvite, tdlib.cache().userName(userId)), true);
            }
          } else {
            return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentLiveStreamInviteMulti_outgoing : R.string.ChatContentLiveStreamInviteMulti, info.userIds.length), true);
          }
        } else {
          if (info.userIds.length == 1) {
            long userId = info.userIds[0];
            if (tdlib.isSelfUserId(userId)) {
              return new ContentPreview(EMOJI_GROUP_INVITE, R.string.ChatContentVoiceChatInviteYou);
            } else {
              return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentVoiceChatInvite_outgoing : R.string.ChatContentVoiceChatInvite, tdlib.cache().userName(userId)), true);
            }
          } else {
            return new ContentPreview(EMOJI_GROUP_INVITE, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentVoiceChatInviteMulti_outgoing : R.string.ChatContentVoiceChatInviteMulti, info.userIds.length), true);
          }
        }
      }
      case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
        TdApi.MessageChatAddMembers info = (TdApi.MessageChatAddMembers) message.content;
        if (info.memberUserIds.length == 1) {
          long userId = info.memberUserIds[0];
          if (userId == Td.getSenderUserId(message)) {
            if (ChatId.isSupergroup(message.chatId)) {
              return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupJoinPublic_outgoing : R.string.ChatContentGroupJoinPublic);
            } else { // isReturned
              return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupReturn_outgoing : R.string.ChatContentGroupReturn);
            }
          } else if (tdlib.isSelfUserId(userId)) {
            return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupAddYou);
          } else {
            return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentGroupAdd_outgoing : R.string.ChatContentGroupAdd, tdlib.cache().userName(userId)), true);
          }
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.plural(message.isOutgoing ? R.string.ChatContentGroupAddMembers_outgoing : R.string.ChatContentGroupAddMembers, info.memberUserIds.length), true);
        }
      }
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
        long userId = ((TdApi.MessageChatDeleteMember) message.content).userId;
        if (userId == Td.getSenderUserId(message)) {
          return new ContentPreview(EMOJI_GROUP, message.isOutgoing ? R.string.ChatContentGroupLeft_outgoing : R.string.ChatContentGroupLeft);
        } else if (tdlib.isSelfUserId(userId)) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupKickYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(message.isOutgoing ? R.string.ChatContentGroupKick_outgoing : R.string.ChatContentGroupKick, tdlib.cache().userFirstName(userId)), true);
        }
      }
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageChatChangeTitle) message.content).title;
        break;
      case TdApi.MessageChatSetTtl.CONSTRUCTOR:
        arg1 = ((TdApi.MessageChatSetTtl) message.content).ttl;
        break;
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        alternativeText = ((TdApi.MessageChatSetTheme) message.content).themeName;
        break;
    }
    ContentPreview.Refresher refresher = null;
    if (message.mediaAlbumId != 0 && getCombineMode(message) != COMBINE_MODE_NONE) {
      refresher = (oldContent, callback) -> tdlib.getAlbum(message, true, null, localAlbum -> {
        if (localAlbum.messages.size() == 1 && !localAlbum.mayHaveMoreItems()) {
          callback.onContentPreviewNotChanged(message.chatId, message.id, oldContent);
        } else {
          ContentPreview newPreview = getAlbumPreview(tdlib, message, localAlbum, allowContent);
          if (localAlbum.messages.size() == 1) {
            if (newPreview.hasRefresher()) {
              newPreview.refreshContent(callback);
            } else {
              callback.onContentPreviewNotChanged(message.chatId, message.id, oldContent);
            }
          } else {
            callback.onContentPreviewChanged(message.chatId, message.id, newPreview, oldContent);
          }
        }
      });
    }
    TdApi.FormattedText argument;
    boolean argumentTranslatable;
    if (Td.isEmpty(formattedText)) {
      argument = new TdApi.FormattedText(alternativeText, null);
      argumentTranslatable = alternativeTextTranslatable;
    } else {
      argument = formattedText;
      argumentTranslatable = false;
    }
    ContentPreview preview = getContentPreview(message.content.getConstructor(), tdlib, chatId, message.senderId, null, !message.isChannelPost && message.isOutgoing, isChatList, argument, argumentTranslatable, arg1);
    if (preview != null) {
      return preview.setRefresher(refresher, true);
    }
    if (allowContent) {
      AtomicBoolean translatable = new AtomicBoolean(false);
      String text = buildShortPreview(tdlib, message, true, true, translatable::set);
      return new ContentPreview(new TdApi.FormattedText(text, null), translatable.get());
    } else {
      return new ContentPreview(null, R.string.YouHaveNewMessage);
    }
  }

  public static ContentPreview getAlbumPreview (Tdlib tdlib, TdApi.Message message, Tdlib.Album album, boolean allowContent) {
    SparseIntArray counters = new SparseIntArray();
    for (TdApi.Message m : album.messages) {
      ArrayUtils.increment(counters, m.content.getConstructor());
    }
    int textRes;
    Emoji emoji;
    switch (counters.size() == 1 ? counters.keyAt(0) : 0) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        textRes = R.string.xPhotos;
        emoji = EMOJI_ALBUM_PHOTOS;
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        textRes = R.string.xVideos;
        emoji = EMOJI_ALBUM_VIDEOS;
        break;
      case TdApi.MessageDocument.CONSTRUCTOR:
        textRes = R.string.xFiles;
        emoji = EMOJI_ALBUM_FILES;
        break;
      case TdApi.MessageAudio.CONSTRUCTOR:
        textRes = R.string.xAudios;
        emoji = EMOJI_ALBUM_AUDIO;
        break;
      default:
        textRes = R.string.xMedia;
        emoji = EMOJI_ALBUM_MEDIA;
        break;
    }
    TdApi.Message captionMessage = allowContent ? getAlbumCaptionMessage(tdlib, album.messages) : null;
    TdApi.FormattedText formattedCaption = captionMessage != null ? Td.textOrCaption(captionMessage.content) : null;
    ContentPreview preview = new ContentPreview(emoji, 0, Td.isEmpty(formattedCaption) ? new TdApi.FormattedText(Lang.plural(textRes, album.messages.size()), null) : formattedCaption, Td.isEmpty(formattedCaption));
    preview.album = album;
    if (album.mayHaveMoreItems()) {
      preview.setRefresher((oldPreview, callback) ->
        tdlib.getAlbum(message, false, album, remoteAlbum -> {
          if (remoteAlbum.messages.size() > album.messages.size()) {
            callback.onContentPreviewChanged(message.chatId, message.id, getAlbumPreview(tdlib, message, remoteAlbum, allowContent), oldPreview);
          } else {
            callback.onContentPreviewNotChanged(message.chatId, message.id, oldPreview);
          }
        }), true
      );
    }
    return preview;
  }

  public static TdApi.Message getAlbumCaptionMessage (Tdlib tdlib, List<TdApi.Message> messages) {
    TdApi.Message captionMessage = null;
    for (TdApi.Message message : messages) {
      TdApi.FormattedText currentCaption = tdlib.getFormattedText(message);
      if (!Td.isEmpty(currentCaption)) {
        if (captionMessage != null) {
          captionMessage = null;
          break;
        } else {
          captionMessage = message;
        }
      }
    }
    return captionMessage;
  }

  private static ContentPreview getNotificationPinned(int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, int arg1) {
    return getNotificationPinned(res, type, tdlib, chatId, sender, argument, senderName, false, arg1);
  }

  private static ContentPreview getNotificationPinned(int res, int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable, int arg1) {
    String text;
    if (StringUtils.isEmpty(argument)) {
      try {
        text = Lang.formatString(Strings.replaceBoldTokens(Lang.getString(res)).toString(), null, getSenderName(tdlib, sender, senderName)).toString();
      } catch (Throwable t) {
        text = Lang.getString(res);
      }
    } else {
      ContentPreview contentPreview = getNotificationPreview(type, tdlib, chatId, sender, senderName, argument, argumentTranslatable, arg1);
      String preview = contentPreview != null ? contentPreview.toString() : null;
      if (StringUtils.isEmpty(preview)) {
        preview = argument;
      }
      try {
        text = Lang.formatString(Strings.replaceBoldTokens(Lang.getString(R.string.ActionPinnedText)).toString(), null, getSenderName(tdlib, sender, senderName), preview).toString();
      } catch (Throwable t) {
        text = Lang.getString(R.string.ActionPinnedText);
      }
    }
    // TODO icon?
    return new ContentPreview(null, 0, new TdApi.FormattedText(text, null), true, true, EMOJI_PIN);
  }

  public static ContentPreview getNotificationPreview (Tdlib tdlib, long chatId, TdApi.NotificationTypeNewPushMessage push, boolean allowContent) {
    switch (push.content.getConstructor()) {
      case TdApi.PushMessageContentHidden.CONSTRUCTOR:
        if (((TdApi.PushMessageContentHidden) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);
        else
          return new ContentPreview(Lang.plural(R.string.xNewMessages, 1), true);

      case TdApi.PushMessageContentText.CONSTRUCTOR:
        if (((TdApi.PushMessageContentText) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentText) push.content).text, 0);
        else
          return getNotificationPreview(TdApi.MessageText.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentText) push.content).text, 0);

      case TdApi.PushMessageContentMessageForwards.CONSTRUCTOR:
        return new ContentPreview(Lang.plural(R.string.xForwards, ((TdApi.PushMessageContentMessageForwards) push.content).totalCount), true);

      case TdApi.PushMessageContentPhoto.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentPhoto) push.content).caption;
        if (((TdApi.PushMessageContentPhoto) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedPhoto, TdApi.MessagePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
        else if (((TdApi.PushMessageContentPhoto) push.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.SelfDestructPhoto, caption, false);
        else
          return getNotificationPreview(TdApi.MessagePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
      }

      case TdApi.PushMessageContentVideo.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentVideo) push.content).caption;
        if (((TdApi.PushMessageContentVideo) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedVideo, TdApi.MessageVideo.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
        else if (((TdApi.PushMessageContentVideo) push.content).isSecret)
          return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.SelfDestructVideo, caption);
        else
          return getNotificationPreview(TdApi.MessageVideo.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
      }

      case TdApi.PushMessageContentAnimation.CONSTRUCTOR: {
        String caption = ((TdApi.PushMessageContentAnimation) push.content).caption;
        if (((TdApi.PushMessageContentAnimation) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedGif, TdApi.MessageAnimation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
        else
          return getNotificationPreview(TdApi.MessageAnimation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
      }

      case TdApi.PushMessageContentDocument.CONSTRUCTOR: {
        TdApi.Document media = ((TdApi.PushMessageContentDocument) push.content).document;
        String caption = null; // FIXME server ((TdApi.PushMessageContentDocument) push.content).caption;
        if (StringUtils.isEmpty(caption) && media != null) {
          caption = media.fileName;
        }
        if (((TdApi.PushMessageContentDocument) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedFile, TdApi.MessageDocument.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
        else
          return getNotificationPreview(TdApi.MessageDocument.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, 0);
      }

      case TdApi.PushMessageContentSticker.CONSTRUCTOR:
        if (((TdApi.PushMessageContentSticker) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedSticker, TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentSticker) push.content).emoji, 0);
        else if (((TdApi.PushMessageContentSticker) push.content).sticker != null && Td.isAnimated(((TdApi.PushMessageContentSticker) push.content).sticker.type))
          return getNotificationPreview(TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, "animated" + ((TdApi.PushMessageContentSticker) push.content).emoji, 0);
        else
          return getNotificationPreview(TdApi.MessageSticker.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentSticker) push.content).emoji, 0);

      case TdApi.PushMessageContentLocation.CONSTRUCTOR:
        if (((TdApi.PushMessageContentLocation) push.content).isLive) {
          if (((TdApi.PushMessageContentLocation) push.content).isPinned)
            return getNotificationPinned(R.string.ActionPinnedGeoLive, TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);
          else
            return getNotificationPreview(TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, "live", 0);
        } else {
          if (((TdApi.PushMessageContentLocation) push.content).isPinned)
            return getNotificationPinned(R.string.ActionPinnedGeo, TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);
          else
            return getNotificationPreview(TdApi.MessageLocation.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);
        }

      case TdApi.PushMessageContentPoll.CONSTRUCTOR:
        if (((TdApi.PushMessageContentPoll) push.content).isPinned)
          return getNotificationPinned(((TdApi.PushMessageContentPoll) push.content).isRegular ? R.string.ActionPinnedPoll : R.string.ActionPinnedQuiz, TdApi.MessagePoll.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentPoll) push.content).question, ((TdApi.PushMessageContentPoll) push.content).isRegular ? ARG_NONE : ARG_POLL_QUIZ);
        else
          return getNotificationPreview(TdApi.MessagePoll.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentPoll) push.content).question, ((TdApi.PushMessageContentPoll) push.content).isRegular ? ARG_NONE : ARG_POLL_QUIZ);

      case TdApi.PushMessageContentAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.PushMessageContentAudio) push.content).audio;
        String caption = null; // FIXME server ((TdApi.PushMessageContentAudio) push.content).caption;
        boolean translatable = false;
        if (StringUtils.isEmpty(caption) && audio != null) {
          caption = Lang.getString(R.string.ChatContentSong, TD.getTitle(audio), TD.getSubtitle(audio));
          translatable = !hasTitle(audio) || !hasSubtitle(audio);
        }
        if (((TdApi.PushMessageContentAudio) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedMusic, TdApi.MessageAudio.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, translatable, 0);
        else
          return getNotificationPreview(TdApi.MessageAudio.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, caption, translatable, 0);
      }

      case TdApi.PushMessageContentVideoNote.CONSTRUCTOR: {
        String argument = null;
        boolean argumentTranslatable = false;
        TdApi.VideoNote videoNote = ((TdApi.PushMessageContentVideoNote) push.content).videoNote;
        if (videoNote != null && videoNote.duration > 0) {
          argument = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentRoundVideo), Strings.buildDuration(videoNote.duration));
          argumentTranslatable = true;
        }
        if (((TdApi.PushMessageContentVideoNote) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedRound, TdApi.MessageVideoNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable, 0);
        else
          return getNotificationPreview(TdApi.MessageVideoNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable, 0);
      }

      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR: {
        String argument = null; // FIXME server ((TdApi.PushMessageContentVoiceNote) push.content).caption;
        boolean argumentTranslatable = false;
        if (StringUtils.isEmpty(argument)) {
          TdApi.VoiceNote voiceNote = ((TdApi.PushMessageContentVoiceNote) push.content).voiceNote;
          if (voiceNote != null && voiceNote.duration > 0) {
            argument = Lang.getString(R.string.ChatContentVoiceDuration, Lang.getString(R.string.ChatContentVoice), Strings.buildDuration(voiceNote.duration));
            argumentTranslatable = true;
          }
        }
        if (((TdApi.PushMessageContentVoiceNote) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedVoice, TdApi.MessageVoiceNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable, 0);
        else
          return getNotificationPreview(TdApi.MessageVoiceNote.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, argument, argumentTranslatable, 0);
      }

      case TdApi.PushMessageContentGame.CONSTRUCTOR:
        if (((TdApi.PushMessageContentGame) push.content).isPinned) {
          String gameTitle = ((TdApi.PushMessageContentGame) push.content).title;
          return getNotificationPinned(StringUtils.isEmpty(gameTitle) ? R.string.ActionPinnedGameNoName : R.string.ActionPinnedGame, TdApi.MessageGame.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, StringUtils.isEmpty(gameTitle) ? null : gameTitle, 0);
        } else
          return getNotificationPreview(TdApi.MessageGame.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentGame) push.content).title, 0);

      case TdApi.PushMessageContentContact.CONSTRUCTOR:
        if (((TdApi.PushMessageContentContact) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedContact, TdApi.MessageContact.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentContact) push.content).name, 0);
        else
          return getNotificationPreview(TdApi.MessageContact.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentContact) push.content).name, 0);

      case TdApi.PushMessageContentInvoice.CONSTRUCTOR:
        if (((TdApi.PushMessageContentInvoice) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageInvoice.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0); // TODO
        else
          return getNotificationPreview(TdApi.MessageInvoice.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentInvoice) push.content).price, 0);

      case TdApi.PushMessageContentScreenshotTaken.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageScreenshotTaken.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);

      case TdApi.PushMessageContentGameScore.CONSTRUCTOR:
        if (((TdApi.PushMessageContentGameScore) push.content).isPinned)
          return getNotificationPinned(R.string.ActionPinnedNoText, TdApi.MessageGameScore.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0); // TODO
        else {
          TdApi.PushMessageContentGameScore score = (TdApi.PushMessageContentGameScore) push.content;
          String gameTitle = score.title;
          if (!StringUtils.isEmpty(gameTitle))
            return new ContentPreview(EMOJI_GAME, 0, Lang.plural(R.string.game_ActionScoredInGame, score.score, gameTitle), true);
          else
            return new ContentPreview(EMOJI_GAME, 0, Lang.plural(R.string.game_ActionScored, score.score), true);
        }

      case TdApi.PushMessageContentContactRegistered.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageContactRegistered.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);

      case TdApi.PushMessageContentMediaAlbum.CONSTRUCTOR: {
        TdApi.PushMessageContentMediaAlbum album = ((TdApi.PushMessageContentMediaAlbum) push.content);
        int mediaTypeCount = 0;
        if (album.hasPhotos)
          mediaTypeCount++;
        if (album.hasVideos)
          mediaTypeCount++;
        if (album.hasAudios)
          mediaTypeCount++;
        if (album.hasDocuments)
          mediaTypeCount++;
        if (mediaTypeCount > 1 || mediaTypeCount == 0) {
          return new ContentPreview(EMOJI_ALBUM_MEDIA, 0, Lang.plural(R.string.xMedia, album.totalCount), true);
        } else if (album.hasDocuments) {
          return new ContentPreview(EMOJI_ALBUM_FILES, 0, Lang.plural(R.string.xFiles, album.totalCount), true);
        } else if (album.hasAudios) {
          return new ContentPreview(EMOJI_ALBUM_AUDIO, 0, Lang.plural(R.string.xAudios, album.totalCount), true);
        } else if (album.hasVideos) {
          return new ContentPreview(EMOJI_ALBUM_VIDEOS, 0, Lang.plural(R.string.xVideos, album.totalCount), true);
        } else {
          return new ContentPreview(EMOJI_ALBUM_PHOTOS, 0, Lang.plural(R.string.xPhotos, album.totalCount), true);
        }
      }

      case TdApi.PushMessageContentBasicGroupChatCreate.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);

      case TdApi.PushMessageContentChatAddMembers.CONSTRUCTOR: {
        TdApi.PushMessageContentChatAddMembers info = (TdApi.PushMessageContentChatAddMembers) push.content;
        if (info.isReturned) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupReturn);
        } else if (info.isCurrentUser) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupAddYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(R.string.ChatContentGroupAdd, info.memberName), true);
        }
      }

      case TdApi.PushMessageContentChatDeleteMember.CONSTRUCTOR: {
        TdApi.PushMessageContentChatDeleteMember info = (TdApi.PushMessageContentChatDeleteMember) push.content;
        if (info.isLeft) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupLeft);
        } else if (info.isCurrentUser) {
          return new ContentPreview(EMOJI_GROUP, R.string.ChatContentGroupKickYou);
        } else {
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(R.string.ChatContentGroupKick, info.memberName), true);
        }
      }

      case TdApi.PushMessageContentChatJoinByLink.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatJoinByLink.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0);

      case TdApi.PushMessageContentChatChangePhoto.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatChangePhoto.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, null, 0); // FIXME Server: Missing isRemoved

      case TdApi.PushMessageContentChatChangeTitle.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatChangeTitle.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentChatChangeTitle) push.content).title, 0);

      case TdApi.PushMessageContentChatSetTheme.CONSTRUCTOR:
        return getNotificationPreview(TdApi.MessageChatSetTheme.CONSTRUCTOR, tdlib, chatId, push.senderId, push.senderName, ((TdApi.PushMessageContentChatSetTheme) push.content).themeName, 0);
    }
    throw new AssertionError(push.content);
  }

  public static boolean isPinnedMessagePushType (TdApi.PushMessageContent content) {
    //(?<= case )(PushMessageContent[^.]+).CONSTRUCTOR:(\s+)break;
    //TdApi.$1.CONSTRUCTOR:$2return ((TdApi.$1) content).isPinned;
    switch (content.getConstructor()) {
      case TdApi.PushMessageContentSticker.CONSTRUCTOR:
        return ((TdApi.PushMessageContentSticker) content).isPinned;
      case TdApi.PushMessageContentAnimation.CONSTRUCTOR:
        return ((TdApi.PushMessageContentAnimation) content).isPinned;
      case TdApi.PushMessageContentAudio.CONSTRUCTOR:
        return ((TdApi.PushMessageContentAudio) content).isPinned;
      case TdApi.PushMessageContentContact.CONSTRUCTOR:
        return ((TdApi.PushMessageContentContact) content).isPinned;
      case TdApi.PushMessageContentDocument.CONSTRUCTOR:
        return ((TdApi.PushMessageContentDocument) content).isPinned;
      case TdApi.PushMessageContentGame.CONSTRUCTOR:
        return ((TdApi.PushMessageContentGame) content).isPinned;
      case TdApi.PushMessageContentGameScore.CONSTRUCTOR:
        return ((TdApi.PushMessageContentGameScore) content).isPinned;
      case TdApi.PushMessageContentHidden.CONSTRUCTOR:
        return ((TdApi.PushMessageContentHidden) content).isPinned;
      case TdApi.PushMessageContentInvoice.CONSTRUCTOR:
        return ((TdApi.PushMessageContentInvoice) content).isPinned;
      case TdApi.PushMessageContentLocation.CONSTRUCTOR:
        return ((TdApi.PushMessageContentLocation) content).isPinned;
      case TdApi.PushMessageContentPhoto.CONSTRUCTOR:
        return ((TdApi.PushMessageContentPhoto) content).isPinned;
      case TdApi.PushMessageContentPoll.CONSTRUCTOR:
        return ((TdApi.PushMessageContentPoll) content).isPinned;
      case TdApi.PushMessageContentText.CONSTRUCTOR:
        return ((TdApi.PushMessageContentText) content).isPinned;
      case TdApi.PushMessageContentVideo.CONSTRUCTOR:
        return ((TdApi.PushMessageContentVideo) content).isPinned;
      case TdApi.PushMessageContentVideoNote.CONSTRUCTOR:
        return ((TdApi.PushMessageContentVideoNote) content).isPinned;
      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR:
        return ((TdApi.PushMessageContentVoiceNote) content).isPinned;

      case TdApi.PushMessageContentBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.PushMessageContentChatAddMembers.CONSTRUCTOR:
      case TdApi.PushMessageContentChatChangePhoto.CONSTRUCTOR:
      case TdApi.PushMessageContentChatChangeTitle.CONSTRUCTOR:
      case TdApi.PushMessageContentChatDeleteMember.CONSTRUCTOR:
      case TdApi.PushMessageContentChatJoinByLink.CONSTRUCTOR:
      case TdApi.PushMessageContentContactRegistered.CONSTRUCTOR:
      case TdApi.PushMessageContentMediaAlbum.CONSTRUCTOR:
      case TdApi.PushMessageContentMessageForwards.CONSTRUCTOR:
      case TdApi.PushMessageContentScreenshotTaken.CONSTRUCTOR:
      case TdApi.PushMessageContentChatSetTheme.CONSTRUCTOR:
        return false;
    }
    return false;
  }

  public static String getTextFromMessage (TdApi.PushMessageContent content) {
    //(?<= case )(PushMessageContent[^.]+).CONSTRUCTOR:(\s+)break;
    //TdApi.$1.CONSTRUCTOR:$2return ((TdApi.$1) content).isPinned;
    switch (content.getConstructor()) {
      case TdApi.PushMessageContentText.CONSTRUCTOR:
        return ((TdApi.PushMessageContentText) content).text;
      case TdApi.PushMessageContentAnimation.CONSTRUCTOR:
        return ((TdApi.PushMessageContentAnimation) content).caption;
      case TdApi.PushMessageContentPhoto.CONSTRUCTOR:
        return ((TdApi.PushMessageContentPhoto) content).caption;
      case TdApi.PushMessageContentVideo.CONSTRUCTOR:
        return ((TdApi.PushMessageContentVideo) content).caption;
      /*FIXME server
      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR:
        return ((TdApi.PushMessageContentVoiceNote) content).caption;
      case TdApi.PushMessageContentAudio.CONSTRUCTOR:
        return ((TdApi.PushMessageContentAudio) content).caption;
      case TdApi.PushMessageContentDocument.CONSTRUCTOR:
        return ((TdApi.PushMessageContentDocument) content).caption;*/
      case TdApi.PushMessageContentVoiceNote.CONSTRUCTOR:
      case TdApi.PushMessageContentAudio.CONSTRUCTOR:
      case TdApi.PushMessageContentDocument.CONSTRUCTOR:
        return null;

      case TdApi.PushMessageContentPoll.CONSTRUCTOR:
        // return ((TdApi.PushMessageContentPoll) content).question;
      case TdApi.PushMessageContentContact.CONSTRUCTOR:
      case TdApi.PushMessageContentGame.CONSTRUCTOR:
      case TdApi.PushMessageContentGameScore.CONSTRUCTOR:
      case TdApi.PushMessageContentHidden.CONSTRUCTOR:
      case TdApi.PushMessageContentInvoice.CONSTRUCTOR:
      case TdApi.PushMessageContentLocation.CONSTRUCTOR:
      case TdApi.PushMessageContentSticker.CONSTRUCTOR:
      case TdApi.PushMessageContentVideoNote.CONSTRUCTOR:
      case TdApi.PushMessageContentBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.PushMessageContentChatAddMembers.CONSTRUCTOR:
      case TdApi.PushMessageContentChatChangePhoto.CONSTRUCTOR:
      case TdApi.PushMessageContentChatChangeTitle.CONSTRUCTOR:
      case TdApi.PushMessageContentChatDeleteMember.CONSTRUCTOR:
      case TdApi.PushMessageContentChatJoinByLink.CONSTRUCTOR:
      case TdApi.PushMessageContentContactRegistered.CONSTRUCTOR:
      case TdApi.PushMessageContentMediaAlbum.CONSTRUCTOR:
      case TdApi.PushMessageContentMessageForwards.CONSTRUCTOR:
      case TdApi.PushMessageContentScreenshotTaken.CONSTRUCTOR:
      case TdApi.PushMessageContentChatSetTheme.CONSTRUCTOR:
        return null;
    }
    return null;
  }

  public static final class Emoji {
    public final @NonNull String textRepresentation;
    public final @DrawableRes int iconRepresentation;

    public Emoji (@NonNull String textRepresentation, @DrawableRes int iconRepresentation) {
      this.textRepresentation = textRepresentation;
      this.iconRepresentation = iconRepresentation;
    }

    @NonNull
    @Override
    public String toString () {
      return textRepresentation;
    }

    public Emoji toNewEmoji (String newEmoji) {
      return StringUtils.equalsOrBothEmpty(this.textRepresentation, newEmoji) ? this : new Emoji(newEmoji, iconRepresentation);
    }
  }

  public static final Emoji
    EMOJI_PHOTO = new Emoji("\uD83D\uDDBC", R.drawable.baseline_camera_alt_16), // "\uD83D\uDCF7"
    EMOJI_VIDEO = new Emoji("\uD83C\uDFA5", R.drawable.baseline_videocam_16), // "\uD83D\uDCF9"
    EMOJI_ROUND_VIDEO = new Emoji("\uD83D\uDCF9", R.drawable.deproko_baseline_msg_video_16),
    EMOJI_SECRET_PHOTO = new Emoji("\uD83D\uDD25", R.drawable.deproko_baseline_whatshot_16),
    EMOJI_SECRET_VIDEO = new Emoji("\uD83D\uDD25", R.drawable.deproko_baseline_whatshot_16),
    EMOJI_LINK = new Emoji("\uD83D\uDD17", R.drawable.baseline_link_16),
    EMOJI_GAME = new Emoji("\uD83C\uDFAE", R.drawable.baseline_videogame_asset_16),
    EMOJI_GROUP = new Emoji("\uD83D\uDC65", R.drawable.baseline_group_16),
    EMOJI_THEME = new Emoji("\uD83C\uDFA8", R.drawable.baseline_palette_16),
    EMOJI_GROUP_INVITE = new Emoji("\uD83D\uDC65", R.drawable.baseline_group_add_16),
    EMOJI_CHANNEL = new Emoji("\uD83D\uDCE2", R.drawable.baseline_bullhorn_16), //  "\uD83D\uDCE3"
    EMOJI_FILE = new Emoji("\uD83D\uDCCE", R.drawable.baseline_insert_drive_file_16),
    EMOJI_AUDIO = new Emoji("\uD83C\uDFB5", R.drawable.baseline_music_note_16),
    EMOJI_CONTACT = new Emoji("\uD83D\uDC64", R.drawable.baseline_person_16),
    EMOJI_POLL = new Emoji("\uD83D\uDCCA", R.drawable.baseline_poll_16),
    EMOJI_QUIZ = new Emoji("\u2753", R.drawable.baseline_help_16),
    EMOJI_VOICE = new Emoji("\uD83C\uDFA4", R.drawable.baseline_mic_16),
    EMOJI_GIF = new Emoji("\uD83D\uDC7E", R.drawable.deproko_baseline_gif_filled_16),
    EMOJI_LOCATION = new Emoji("\uD83D\uDCCC", R.drawable.baseline_gps_fixed_16),
    EMOJI_INVOICE = new Emoji("\uD83D\uDCB8", R.drawable.baseline_receipt_16),
    EMOJI_USER_JOINED = new Emoji("\uD83C\uDF89", R.drawable.baseline_party_popper_16),
    EMOJI_SCREENSHOT = new Emoji("\uD83D\uDCF8", R.drawable.round_warning_16),
    EMOJI_PIN = new Emoji("\uD83D\uDCCC", R.drawable.deproko_baseline_pin_16),
    EMOJI_ALBUM_MEDIA = new Emoji("\uD83D\uDDBC", R.drawable.baseline_collections_16),
    EMOJI_ALBUM_PHOTOS = new Emoji("\uD83D\uDDBC", R.drawable.baseline_collections_16),
    EMOJI_ALBUM_AUDIO = new Emoji("\uD83C\uDFB5", R.drawable.ivanliana_baseline_audio_collections_16),
    EMOJI_ALBUM_FILES = new Emoji("\uD83D\uDCCE", R.drawable.ivanliana_baseline_file_collections_16),
    EMOJI_ALBUM_VIDEOS = new Emoji("\uD83C\uDFA5", R.drawable.ivanliana_baseline_video_collections_16),
    EMOJI_FORWARD = new Emoji("\u21A9", R.drawable.baseline_share_arrow_16),
    EMOJI_ABACUS = new Emoji("\uD83E\uDDEE", R.drawable.baseline_bar_chart_24),
    EMOJI_DART = new Emoji("\uD83C\uDFAF", R.drawable.baseline_gps_fixed_16),
    EMOJI_DICE = new Emoji("\uD83C\uDFB2", R.drawable.baseline_casino_16),
    EMOJI_DICE_1 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_1_16),
    EMOJI_DICE_2 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_2_16),
    EMOJI_DICE_3 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_3_16),
    EMOJI_DICE_4 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_4_16),
    EMOJI_DICE_5 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_5_16),
    EMOJI_DICE_6 = new Emoji("\uD83C\uDFB2", R.drawable.belledeboheme_baseline_dice_6_16),
    EMOJI_CALL = new Emoji("\uD83D\uDCDE", R.drawable.baseline_call_16),
    EMOJI_TIMER = new Emoji("\u23F2", R.drawable.baseline_timer_16),
    EMOJI_TIMER_OFF = new Emoji("\u23F2", R.drawable.baseline_timer_off_16),
    EMOJI_CALL_END = new Emoji("\uD83D\uDCDE", R.drawable.baseline_call_end_16),
    EMOJI_CALL_MISSED = new Emoji("\u260E", R.drawable.baseline_call_missed_18),
    EMOJI_CALL_DECLINED = new Emoji("\u260E", R.drawable.baseline_call_received_18),
    EMOJI_WARN = new Emoji("\u26A0", R.drawable.baseline_warning_18),
    EMOJI_INFO = new Emoji("\u2139", R.drawable.baseline_info_18),
    EMOJI_ERROR = new Emoji("\u2139", R.drawable.baseline_error_18),
    EMOJI_LOCK = new Emoji("\uD83D\uDD12", R.drawable.baseline_lock_16)
  ;

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, boolean argumentTranslatable, int arg1) {
    return getContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), argumentTranslatable, arg1);
  }

  private static ContentPreview getNotificationPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, String argument, int arg1) {
    return getContentPreview(type, tdlib, chatId, sender, senderName, tdlib.isSelfSender(sender), false, new TdApi.FormattedText(argument, null), false, arg1);
  }

  private static String getSenderName (Tdlib tdlib, TdApi.MessageSender sender, String senderName) {
    return StringUtils.isEmpty(senderName) ? tdlib.senderName(sender, true) : senderName;
  }

  private static ContentPreview getContentPreview (@TdApi.MessageContent.Constructors int type, Tdlib tdlib, long chatId, TdApi.MessageSender sender, String senderName, boolean isOutgoing, boolean isChatsList, TdApi.FormattedText formattedArgument, boolean argumentTranslatable, int arg1) {
    switch (type) {
      case TdApi.MessageText.CONSTRUCTOR:
        return new ContentPreview(arg1 == ARG_TRUE ? EMOJI_LINK : null, R.string.YouHaveNewMessage, formattedArgument, argumentTranslatable);
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
        return new ContentPreview(null, R.string.YouHaveNewMessage, formattedArgument, argumentTranslatable);
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return new ContentPreview(EMOJI_PHOTO, R.string.ChatContentPhoto, formattedArgument, argumentTranslatable);
      case TdApi.MessageVideo.CONSTRUCTOR:
        return new ContentPreview(EMOJI_VIDEO, R.string.ChatContentVideo, formattedArgument, argumentTranslatable);
      case TdApi.MessageDocument.CONSTRUCTOR:
        return new ContentPreview(EMOJI_FILE, R.string.ChatContentFile, formattedArgument, argumentTranslatable);
      case TdApi.MessageAudio.CONSTRUCTOR:
        return new ContentPreview(EMOJI_AUDIO, 0, formattedArgument, argumentTranslatable); // FIXME: does it need a placeholder or argument is always non-null?
      case TdApi.MessageContact.CONSTRUCTOR:
        return new ContentPreview(EMOJI_CONTACT, R.string.AttachContact, formattedArgument, argumentTranslatable);
      case TdApi.MessagePoll.CONSTRUCTOR:
        if (arg1 == ARG_POLL_QUIZ)
          return new ContentPreview(EMOJI_QUIZ, R.string.Quiz, formattedArgument, argumentTranslatable);
        else
          return new ContentPreview(EMOJI_POLL, R.string.Poll, formattedArgument, argumentTranslatable);
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return new ContentPreview(EMOJI_VOICE, R.string.ChatContentVoice, formattedArgument, argumentTranslatable);
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return new ContentPreview(EMOJI_ROUND_VIDEO, R.string.ChatContentRoundVideo, formattedArgument, argumentTranslatable);
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GIF, R.string.ChatContentAnimation, formattedArgument, argumentTranslatable);
      case TdApi.MessageLocation.CONSTRUCTOR:
        return new ContentPreview(EMOJI_LOCATION, "live".equals(Td.getText(formattedArgument)) ? R.string.AttachLiveLocation : R.string.Location);
      case TdApi.MessageSticker.CONSTRUCTOR: {
        String emoji = Td.getText(formattedArgument);
        boolean isAnimated = false;
        if (emoji != null && emoji.startsWith("animated")) {
          emoji = emoji.substring("animated".length());
          isAnimated = true;
        }
        return new ContentPreview(StringUtils.isEmpty(emoji) ? null : new Emoji(emoji, 0), isAnimated && !isChatsList ? R.string.AnimatedSticker : R.string.Sticker);
      }
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        if (isOutgoing)
          return new ContentPreview(EMOJI_SCREENSHOT, R.string.YouTookAScreenshot);
        else if (isChatsList)
          return new ContentPreview(EMOJI_SCREENSHOT, R.string.ChatContentScreenshot);
        else
          return new ContentPreview(EMOJI_SCREENSHOT, 0, Lang.getString(R.string.XTookAScreenshot, getSenderName(tdlib, sender, senderName)), true);
      case TdApi.MessageGame.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GAME, 0, Lang.getString(ChatId.isMultiChat(chatId) ? (isOutgoing ? R.string.NotificationGame_group_outgoing : R.string.NotificationGame_group) : (isOutgoing ? R.string.NotificationGame_outgoing : R.string.NotificationGame), Td.getText(formattedArgument)), true);
      case TdApi.MessageInvoice.CONSTRUCTOR:
        return new ContentPreview(EMOJI_INVOICE, R.string.Invoice, Td.isEmpty(formattedArgument) ? null : Lang.getString(R.string.InvoiceFor, Td.getText(formattedArgument)), true);
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
        return new ContentPreview(EMOJI_USER_JOINED, 0, Lang.getString(R.string.NotificationContactJoined, getSenderName(tdlib, sender, senderName)), true);
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, R.string.ActionCreateChannel);
        else
          return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupCreate_outgoing : R.string.ChatContentGroupCreate);
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupCreate_outgoing : R.string.ChatContentGroupCreate);
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
        return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupJoin_outgoing : R.string.ChatContentGroupJoin);
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_PHOTO, R.string.ActionChannelChangedPhoto);
        else
          return new ContentPreview(EMOJI_PHOTO, isOutgoing ? R.string.ChatContentGroupPhoto_outgoing : R.string.ChatContentGroupPhoto);
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, R.string.ActionChannelRemovedPhoto);
        else
          return new ContentPreview(EMOJI_GROUP, isOutgoing ? R.string.ChatContentGroupPhotoRemove_outgoing : R.string.ChatContentGroupPhotoRemove);
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        if (tdlib.isChannel(chatId))
          return new ContentPreview(EMOJI_CHANNEL, 0, Lang.getString(R.string.ActionChannelChangedTitleTo, Td.getText(formattedArgument)), true);
        else
          return new ContentPreview(EMOJI_GROUP, 0, Lang.getString(isOutgoing ? R.string.ChatContentGroupName_outgoing : R.string.ChatContentGroupName, Td.getText(formattedArgument)), true);
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        if (isOutgoing)
          return new ContentPreview(EMOJI_THEME, 0, toFormattedText(Lang.getStringBold(R.string.ChatContentThemeSet_outgoing, formattedArgument.text), true));
        else
          return new ContentPreview(EMOJI_THEME, 0, toFormattedText(Lang.getStringBold(R.string.ChatContentThemeSet, formattedArgument.text), true));
      case TdApi.MessageChatSetTtl.CONSTRUCTOR: {
        if (arg1 > 0) {
          final int secondsRes, minutesRes, hoursRes, daysRes, weeksRes, monthsRes;
          if (ChatId.isUserChat(chatId)) {
            secondsRes = R.string.ChatContentTtlSeconds;
            minutesRes = R.string.ChatContentTtlMinutes;
            hoursRes = R.string.ChatContentTtlHours;
            daysRes = R.string.ChatContentTtlDays;
            weeksRes = R.string.ChatContentTtlWeeks;
            monthsRes = R.string.ChatContentTtlMonths;
          } else if (tdlib.isChannel(chatId)) {
            secondsRes = R.string.ChatContentChannelTtlSeconds;
            minutesRes = R.string.ChatContentChannelTtlMinutes;
            hoursRes = R.string.ChatContentChannelTtlHours;
            daysRes = R.string.ChatContentChannelTtlDays;
            weeksRes = R.string.ChatContentChannelTtlWeeks;
            monthsRes = R.string.ChatContentChannelTtlMonths;
          } else {
            secondsRes = R.string.ChatContentGroupTtlSeconds;
            minutesRes = R.string.ChatContentGroupTtlMinutes;
            hoursRes = R.string.ChatContentGroupTtlHours;
            daysRes = R.string.ChatContentGroupTtlDays;
            weeksRes = R.string.ChatContentGroupTtlWeeks;
            monthsRes = R.string.ChatContentGroupTtlMonths;
          }
          final CharSequence text = Lang.pluralDuration(arg1, TimeUnit.SECONDS, secondsRes, minutesRes, hoursRes, daysRes, weeksRes, monthsRes);
          return new ContentPreview(EMOJI_TIMER, 0, toFormattedText(text, false), true);
        } else {
          final int stringRes;
          if (ChatId.isUserChat(chatId)) {
            stringRes = R.string.ChatContentTtlOff;
          } else if (tdlib.isChannel(chatId)) {
            stringRes = R.string.ChatContentChannelTtlOff;
          } else {
            stringRes = R.string.ChatContentGroupTtlOff;
          }
          return new ContentPreview(EMOJI_TIMER_OFF, stringRes);
        }
      }
      case TdApi.MessageDice.CONSTRUCTOR: {
        String diceEmoji = !Td.isEmpty(formattedArgument) && tdlib.isDiceEmoji(formattedArgument.text) ? formattedArgument.text : TD.EMOJI_DICE.textRepresentation;
        if (TD.EMOJI_DART.textRepresentation.equals(diceEmoji)) {
          return new ContentPreview(EMOJI_DART, getDartRes(arg1));
        }
        if (TD.EMOJI_DICE.textRepresentation.equals(diceEmoji)) {
          if (arg1 >= 1 && arg1 <= 6) {
            switch (arg1) {
              case 1:
                return new ContentPreview(EMOJI_DICE_1, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 2:
                return new ContentPreview(EMOJI_DICE_2, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 3:
                return new ContentPreview(EMOJI_DICE_3, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 4:
                return new ContentPreview(EMOJI_DICE_4, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 5:
                return new ContentPreview(EMOJI_DICE_5, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
              case 6:
                return new ContentPreview(EMOJI_DICE_6, 0, Lang.plural(R.string.ChatContentDiceRolled, arg1), true);
            }
          }
          return new ContentPreview(EMOJI_DICE, R.string.ChatContentDice);
        }
        return new ContentPreview(new Emoji(diceEmoji, 0), 0);
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
        return new ContentPreview(EMOJI_SECRET_PHOTO, R.string.AttachPhotoExpired);
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
        return new ContentPreview(EMOJI_SECRET_VIDEO, R.string.AttachVideoExpired);
      case TdApi.MessageCall.CONSTRUCTOR:
        switch (arg1) {
          case ARG_CALL_DECLINED:
            return new ContentPreview(EMOJI_CALL_DECLINED, isOutgoing ? R.string.OutgoingCall : R.string.CallMessageIncomingDeclined);
          case ARG_CALL_MISSED:
            return new ContentPreview(EMOJI_CALL_MISSED, isOutgoing ? R.string.CallMessageOutgoingMissed : R.string.MissedCall);
          default:
            if (arg1 > 0) {
              return new ContentPreview(EMOJI_CALL, 0, Lang.getString(R.string.ChatContentCallWithDuration, Lang.getString(isOutgoing ? R.string.OutgoingCall : R.string.IncomingCall), Lang.getDurationFull(arg1)), true);
            } else {
              return new ContentPreview(EMOJI_CALL, isOutgoing ? R.string.OutgoingCall : R.string.IncomingCall);
            }
        }
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessageVenue.CONSTRUCTOR:
      case TdApi.MessageWebsiteConnected.CONSTRUCTOR:
      case TdApi.MessageUnsupported.CONSTRUCTOR:
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
        break;
    }
    return null;
  }

  public static boolean hasIncompleteLoginAttempts (TdApi.Session[] sessions) {
    for (TdApi.Session session : sessions) {
      if (session.isPasswordPending)
        return true;
    }
    return false;
  }
}
