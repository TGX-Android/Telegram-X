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
 * File created on 30/04/2015 at 17:35
 */
package org.thunderdog.challegram.data;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.CustomEmojiId;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibEntitySpan;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.HashtagController;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.html.HtmlEncoder;
import me.vkryl.android.html.HtmlParser;
import me.vkryl.android.html.HtmlTag;
import me.vkryl.android.text.AcceptFilter;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.Future;
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
      case RightId.READ_MESSAGES:
      case RightId.SEND_BASIC_MESSAGES:
      case RightId.SEND_AUDIO:
      case RightId.SEND_DOCS:
      case RightId.SEND_PHOTOS:
      case RightId.SEND_VIDEOS:
      case RightId.SEND_VOICE_NOTES:
      case RightId.SEND_VIDEO_NOTES:
      case RightId.SEND_OTHER_MESSAGES:
      case RightId.SEND_POLLS:
      case RightId.EMBED_LINKS:
      case RightId.CHANGE_CHAT_INFO:
      case RightId.EDIT_MESSAGES:
      case RightId.DELETE_MESSAGES:
      case RightId.BAN_USERS:
      case RightId.INVITE_USERS:
      case RightId.PIN_MESSAGES:
      case RightId.MANAGE_VIDEO_CHATS:
      case RightId.MANAGE_TOPICS:
      case RightId.POST_STORIES:
      case RightId.EDIT_STORIES:
      case RightId.DELETE_STORIES:
      case RightId.ADD_NEW_ADMINS:
      case RightId.REMAIN_ANONYMOUS:
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
      case RightId.READ_MESSAGES:
        return true;
      case RightId.SEND_BASIC_MESSAGES:
        return permissions.canSendBasicMessages;
      case RightId.SEND_AUDIO:
        return permissions.canSendAudios;
      case RightId.SEND_DOCS:
        return permissions.canSendDocuments;
      case RightId.SEND_PHOTOS:
        return permissions.canSendPhotos;
      case RightId.SEND_VIDEOS:
        return permissions.canSendVideos;
      case RightId.SEND_VOICE_NOTES:
        return permissions.canSendVoiceNotes;
      case RightId.SEND_VIDEO_NOTES:
        return permissions.canSendVideoNotes;
      case RightId.SEND_OTHER_MESSAGES:
        return permissions.canSendOtherMessages;
      case RightId.SEND_POLLS:
        return permissions.canSendPolls;
      case RightId.EMBED_LINKS:
        return permissions.canAddWebPagePreviews;
      case RightId.INVITE_USERS:
        return permissions.canInviteUsers;
      case RightId.PIN_MESSAGES:
        return permissions.canPinMessages;
      case RightId.CHANGE_CHAT_INFO:
        return permissions.canChangeInfo;
      // Admin-only
      case RightId.ADD_NEW_ADMINS:
      case RightId.BAN_USERS:
      case RightId.DELETE_MESSAGES:
      case RightId.EDIT_MESSAGES:
      case RightId.MANAGE_VIDEO_CHATS:
      case RightId.MANAGE_TOPICS:
      case RightId.POST_STORIES:
      case RightId.EDIT_STORIES:
      case RightId.DELETE_STORIES:
      case RightId.REMAIN_ANONYMOUS:
        break;
    }
    throw new IllegalArgumentException(Lang.getResourceEntryName(rightId));
  }

  public static boolean isSameSource (TdApi.Message a, TdApi.Message b, boolean splitAuthors) {
    if (a == null || b == null)
      return false;
    if ((a.forwardInfo == null) == (b.forwardInfo != null))
      return false;
    if (!Td.equalsTo(a.importInfo, b.importInfo, false)) {
      return false;
    }
    if (a.forwardInfo == null) {
      return a.chatId == b.chatId;
    }
    if (a.forwardInfo.origin.getConstructor() != b.forwardInfo.origin.getConstructor() || a.forwardInfo.fromChatId != b.forwardInfo.fromChatId)
      return false;
    if (splitAuthors) {
      switch (a.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageOriginUser.CONSTRUCTOR:
          return ((TdApi.MessageOriginUser) a.forwardInfo.origin).senderUserId == ((TdApi.MessageOriginUser) b.forwardInfo.origin).senderUserId;
        case TdApi.MessageOriginChannel.CONSTRUCTOR:
          return ((TdApi.MessageOriginChannel) a.forwardInfo.origin).chatId == ((TdApi.MessageOriginChannel) b.forwardInfo.origin).chatId &&
            StringUtils.equalsOrBothEmpty(((TdApi.MessageOriginChannel) a.forwardInfo.origin).authorSignature, ((TdApi.MessageOriginChannel) b.forwardInfo.origin).authorSignature);
        case TdApi.MessageOriginChat.CONSTRUCTOR:
          return ((TdApi.MessageOriginChat) a.forwardInfo.origin).senderChatId == ((TdApi.MessageOriginChat) b.forwardInfo.origin).senderChatId &&
            StringUtils.equalsOrBothEmpty(((TdApi.MessageOriginChat) a.forwardInfo.origin).authorSignature, ((TdApi.MessageOriginChat) b.forwardInfo.origin).authorSignature);
        case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
          return ((TdApi.MessageOriginHiddenUser) a.forwardInfo.origin).senderName.equals(((TdApi.MessageOriginHiddenUser) b.forwardInfo.origin).senderName);
        default:
          Td.assertMessageOrigin_f2224a59();
          throw Td.unsupported(a.forwardInfo.origin);
      }
    }
    return false;
  }

  public static CharSequence formatString (@Nullable TdlibDelegate context, String text, TdApi.TextEntity[] entities, @Nullable Typeface defaultTypeface, @Nullable CustomTypefaceSpan.OnClickListener onClickListener) {
    if (entities == null || entities.length == 0)
      return text;

    SpannableStringBuilder b = null;
    for (TdApi.TextEntity entity : entities) {
      Object span;
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
          span = null; // nothing to do?
          break;
        case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
        case TdApi.TextEntityTypeCashtag.CONSTRUCTOR: {
          String hashtag = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
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
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
                Intents.sendEmail(emailAddress);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
          String username = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
                if (context != null)
                  context.tdlib().ui().openPublicChat(context, username, null);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: {
          String cardNumber = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
                if (context != null)
                  context.tdlib().ui().openCardNumber(context, cardNumber);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR: {
          String phoneNumber = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
                Intents.openNumber(phoneNumber);
              }
              return true;
            });
          break;
        }
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
          String url = Td.substring(text, entity);
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
                if (context != null)
                  context.tdlib().ui().openUrl(context, url, null);
              }
              return true;
            });
          break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
          String textUrl = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink)
            .setOnClickListener((view, span1, clickedText) -> {
              if (onClickListener == null || !onClickListener.onClick(view, span1, clickedText)) {
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
        if (span instanceof CustomTypefaceSpan) {
          CustomTypefaceSpan customSpan = (CustomTypefaceSpan) span;
          customSpan.setTextEntityType(entity.type);
          customSpan.setRemoveUnderline(true);
          if (customSpan.getOnClickListener() != null) {
            final String entityText = Td.substring(text, entity);
            b.setSpan(new ClickableSpan() {
              @Override
              public void onClick (@NonNull View widget) {
                customSpan.getOnClickListener().onClick(widget, customSpan, entityText);
              }
            }, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
        }
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
        if (Td.isUrl(entity.type)) {
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
            if (Td.isTextUrl(existingEntity.type)) {
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
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      // Only because custom emoji aren't displayed in the notification
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
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
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
        return true;

      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return allowInternal;

      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(type);
    }
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
        gifFile.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
        return gifFile;
      }
      case TdApi.ThumbnailFormatWebm.CONSTRUCTOR:
      case TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR: {
        GifFile gifFile = new GifFile(tdlib, thumbnail.file,
          thumbnail.format.getConstructor() == TdApi.ThumbnailFormatWebm.CONSTRUCTOR ?
            GifFile.TYPE_WEBM :
            GifFile.TYPE_MPEG4
        );
        gifFile.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
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
    if (Td.isAnimated(sticker.format))
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

  public static String getPhoneNumber (String in) {
    return StringUtils.isEmpty(in) || in.startsWith("+") ? in : "+" + in;
  }

  public static void saveMessageThreadInfo (Bundle bundle, String prefix, @Nullable TdApi.MessageThreadInfo threadInfo) {
    if (threadInfo == null) {
      return;
    }
    bundle.putLong(prefix + "_chatId", threadInfo.chatId);
    bundle.putLong(prefix + "_messageThreadId", threadInfo.messageThreadId);
    bundle.putInt(prefix + "_unreadMessageCount", threadInfo.unreadMessageCount);
    Td.put(bundle, prefix + "_replyInfo", threadInfo.replyInfo);
    Td.put(bundle, prefix + "_draftMessage", threadInfo.draftMessage);
    bundle.putInt(prefix + "_messagesLength", threadInfo.messages.length);
    for (int index = 0; index < threadInfo.messages.length; index++) {
      bundle.putLong(prefix + "_messageId_" + index, threadInfo.messages[index].id);
    }
  }

  public static @Nullable TdApi.MessageThreadInfo restoreMessageThreadInfo (Tdlib tdlib, Bundle bundle, String prefix) {
    long chatId = bundle.getLong(prefix + "_chatId");
    long messageThreadId = bundle.getLong(prefix + "_messageThreadId");
    if (chatId == 0 || messageThreadId == 0) {
      return null;
    }
    TdApi.MessageReplyInfo replyInfo = Td.restoreMessageReplyInfo(bundle, prefix + "_replyInfo");
    if (replyInfo == null) {
      return null;
    }
    int unreadMessageCount = bundle.getInt(prefix + "_unreadMessageCount");
    int messagesLength = bundle.getInt(prefix + "_messagesLength");
    ArrayList<TdApi.Message> messages = new ArrayList<>(Math.max(messagesLength, 0));
    for (int index = 0; index < messagesLength; index++) {
      long messageId = bundle.getLong(prefix + "_messageId_" + index);
      TdApi.Message message = tdlib.getMessageLocally(chatId, messageId);
      if (message != null) {
        messages.add(message);
      } else {
        return null;
      }
    }
    TdApi.DraftMessage draftMessage = Td.restoreDraftMessage(bundle, prefix + "_draftMessage");
    return new TdApi.MessageThreadInfo(chatId, messageThreadId, replyInfo, unreadMessageCount, messages.toArray(new TdApi.Message[0]), draftMessage);
  }

  public static final String KEY_PREFIX_FOLDER = "filter";
  public static TdApi.ChatList chatListFromKey (String key) {
    if (StringUtils.isEmpty(key))
      return null;
    switch (key) {
      case "main":
        return new TdApi.ChatListMain();
      case "archive":
        return new TdApi.ChatListArchive();
      default:
        if (key.startsWith(KEY_PREFIX_FOLDER)) {
          return new TdApi.ChatListFolder(StringUtils.parseInt(key.substring(KEY_PREFIX_FOLDER.length())));
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
      case TdApi.ChatListFolder.CONSTRUCTOR:
        return KEY_PREFIX_FOLDER + ((TdApi.ChatListFolder) chatList).chatFolderId;
      default:
        Td.assertChatList_db6c93ab();
        throw Td.unsupported(chatList);
    }
  }

  public static TdApi.ReactionType toReactionType (String key) {
    if (key.startsWith("custom_")) {
      return new TdApi.ReactionTypeCustomEmoji(Long.parseLong(key.substring("custom_".length())));
    }
    return new TdApi.ReactionTypeEmoji(key);
  }

  public static String makeReactionKey (TdApi.ReactionType reactionType) {
    switch (reactionType.getConstructor()) {
      case TdApi.ReactionTypeEmoji.CONSTRUCTOR:
        return ((TdApi.ReactionTypeEmoji) reactionType).emoji;
      case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR:
        return "custom_" + ((TdApi.ReactionTypeCustomEmoji) reactionType).customEmojiId;
      default:
        Td.assertReactionType_7dcca074();
        throw Td.unsupported(reactionType);
    }
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
    if (!StringUtils.isEmpty(prefix)) {
      return Td.concat(new TdApi.FormattedText(prefix, null), text);
    } else {
      return text;
    }
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

  public static boolean needThemedColorFilter (TdApi.Sticker sticker) {
    return (sticker != null && sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji
      && ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).needsRepainting);
  }

  public static class Size {
    public final int width, height;

    public Size (int width, int height) {
      this.width = width;
      this.height = height;
    }

    public int getWidth () {
      return width;
    }

    public int getHeight () {
      return height;
    }
  }
  public static Size getFinalResolution (TdApi.Document document, @Nullable BitmapFactory.Options options, boolean isRotated) {
    int width, height;
    if (options != null && Math.min(options.outWidth, options.outHeight) > 0) {
      if (isRotated) {
        //noinspection SuspiciousNameCombination
        width = options.outHeight; height = options.outWidth;
      } else {
        width = options.outWidth;  height = options.outHeight;
      }
    } else if (document.thumbnail != null) {
      width = document.thumbnail.width;
      height = document.thumbnail.height;
      float scale = 2f;
      width *= scale;
      height *= scale;
    } else {
      width = height = 0;
    }
    return new Size(width, height);
  }

  public static TdApi.Animation convertToAnimation (TdApi.Document document, @Nullable BitmapFactory.Options options, boolean isRotated, U.MediaMetadata mediaMetadata) {
    Size size = getFinalResolution(document, options, isRotated);
    return new TdApi.Animation(
      mediaMetadata != null ? (int) TimeUnit.MILLISECONDS.toSeconds(mediaMetadata.durationMs) : 0,
      size.getWidth(), size.getHeight(),
      document.fileName, document.mimeType, false,
      document.minithumbnail, document.thumbnail,
      document.document
    );
  }

  public static TdApi.Video convertToVideo (TdApi.Document document, @Nullable BitmapFactory.Options options, boolean isRotated, U.MediaMetadata mediaMetadata) {
    Size size = getFinalResolution(document, options, isRotated);
    return new TdApi.Video(
      mediaMetadata != null ? (int) TimeUnit.MILLISECONDS.toSeconds(mediaMetadata.durationMs) : 0,
      size.getWidth(), size.getHeight(),
      document.fileName, document.mimeType,
      false, true,
      document.minithumbnail, document.thumbnail,
      document.document
    );
  }

  public static TdApi.Photo convertToPhoto (TdApi.Document document, @Nullable BitmapFactory.Options options, boolean isRotated) {
    Size size = getFinalResolution(document, options, isRotated);
    TdApi.PhotoSize thumbnailSize = toThumbnailSize(document.thumbnail);
    TdApi.PhotoSize[] sizes = new TdApi.PhotoSize[thumbnailSize != null ? 2 : 1];
    TdApi.PhotoSize targetSize = new TdApi.PhotoSize("w", document.document, size.getWidth(), size.getHeight(), null);
    if (thumbnailSize != null) {
      sizes[0] = thumbnailSize;
      sizes[1] = targetSize;
    } else {
      sizes[0] = targetSize;
    }
    return new TdApi.Photo(false, null, sizes);
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

  public static boolean canSendToSecretChat (TdApi.MessageContent content) {
    //noinspection SwitchIntDef
    switch (content.getConstructor()) {
      case TdApi.MessagePoll.CONSTRUCTOR:
      case TdApi.MessageGame.CONSTRUCTOR:
      case TdApi.MessageStory.CONSTRUCTOR:
      case TdApi.MessageInvoice.CONSTRUCTOR:
      case TdApi.MessageDice.CONSTRUCTOR:
      case TdApi.MessagePremiumGiveaway.CONSTRUCTOR:
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
      case TdApi.MessagePremiumGiveawayCreated.CONSTRUCTOR:
      case TdApi.MessagePremiumGiveawayCompleted.CONSTRUCTOR:
      case TdApi.MessagePremiumGiveawayWinners.CONSTRUCTOR: {
        return false;
      }
      default: {
        Td.assertMessageContent_d40af239();
      }
    }
    return true;
  }

  public static int getViewCount (TdApi.MessageInteractionInfo interactionInfo) {
    return interactionInfo != null ? interactionInfo.viewCount : 0;
  }

  public static int getReplyCount (TdApi.MessageInteractionInfo interactionInfo) {
    TdApi.MessageReplyInfo replyInfo = getReplyInfo(interactionInfo);
    return replyInfo != null ? replyInfo.replyCount : 0;
  }

  public static @Nullable TdApi.MessageReplyInfo getReplyInfo (TdApi.MessageInteractionInfo interactionInfo) {
    return interactionInfo != null ? interactionInfo.replyInfo : null;
  }

  public static boolean isGeneralUser (TdApi.User user) {
    return user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR;
  }

  public static TdApi.InputMessageContent toInputMessageContent (String filePath, TdApi.InputFile inputFile, @NonNull FileInfo info, TdApi.FormattedText caption, boolean hasSpoiler) {
    return toInputMessageContent(filePath, inputFile, info, caption, true, true, true, true, hasSpoiler);
  }

  public static TdApi.InputMessageContent toInputMessageContent (String filePath, TdApi.InputFile inputFile, @NonNull FileInfo info, TdApi.FormattedText caption, boolean allowAudio, boolean allowAnimation, boolean allowVideo, boolean allowDocs, boolean hasSpoiler) {
    if (!StringUtils.isEmpty(info.mimeType)) {
      if (allowAudio && info.mimeType.startsWith("audio/") && !info.mimeType.equals("audio/ogg")) {
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
            duration = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000;
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
              if (allowAnimation && durationSeconds < 30 && info.knownSize < ByteUnit.MB.toBytes(10) && !metadata.hasAudio) {
                return new TdApi.InputMessageAnimation(inputFile, null, null, durationSeconds, videoWidth, videoHeight, caption, hasSpoiler);
              } else if (allowVideo && durationSeconds > 0) {
                return new TdApi.InputMessageVideo(inputFile, null, null, durationSeconds, videoWidth, videoHeight, U.canStreamVideo(inputFile), caption, null, hasSpoiler);
              }
            }
          }
        } catch (Throwable t) {
          Log.w("Cannot extract media metadata", t);
        }
      }
    }
    if (allowDocs) {
      return new TdApi.InputMessageDocument(inputFile, null, false, caption);
    }
    return null;
  }

  public static TdApi.InputFile createInputFile (String path) {
    return createInputFile(path, null, null);
  }

  public static TdApi.InputFile createInputFile (String path, @Nullable String type) {
    return createInputFile(path, type, null);
  }

  public static boolean hasRestrictions (TdApi.ChatPermissions a, TdApi.ChatPermissions defaultPermissions) {
    return
      (a.canSendBasicMessages != defaultPermissions.canSendBasicMessages && defaultPermissions.canSendBasicMessages) ||
      (a.canSendAudios != defaultPermissions.canSendAudios && defaultPermissions.canSendAudios) ||
      (a.canSendDocuments != defaultPermissions.canSendDocuments && defaultPermissions.canSendDocuments) ||
      (a.canSendPhotos != defaultPermissions.canSendPhotos && defaultPermissions.canSendPhotos) ||
      (a.canSendVideos != defaultPermissions.canSendVideos && defaultPermissions.canSendVideos) ||
      (a.canSendVoiceNotes != defaultPermissions.canSendVoiceNotes && defaultPermissions.canSendVoiceNotes) ||
      (a.canSendVideoNotes != defaultPermissions.canSendVideoNotes && defaultPermissions.canSendVideoNotes) ||
      (a.canSendOtherMessages != defaultPermissions.canSendOtherMessages && defaultPermissions.canSendOtherMessages) ||
      (a.canAddWebPagePreviews != defaultPermissions.canAddWebPagePreviews && defaultPermissions.canAddWebPagePreviews) ||
      (a.canSendPolls != defaultPermissions.canSendPolls && defaultPermissions.canSendPolls) ||
      (a.canInviteUsers != defaultPermissions.canInviteUsers && defaultPermissions.canInviteUsers) ||
      (a.canPinMessages != defaultPermissions.canPinMessages && defaultPermissions.canPinMessages) ||
      (a.canChangeInfo != defaultPermissions.canChangeInfo && defaultPermissions.canChangeInfo);
  }

  public static int getCombineMode (TdApi.Message message) {
    if (message != null) {
      if (!Td.isSecret(message.content)) {
        //noinspection SwitchIntDef
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
    }
    return COMBINE_MODE_NONE;
  }

  public static int getCombineMode (TdApi.InputMessageContent content) {
    if (content != null) {
      switch (content.getConstructor()) {
        case TdApi.InputMessagePhoto.CONSTRUCTOR:
          return ((TdApi.InputMessagePhoto) content).selfDestructType == null ? COMBINE_MODE_MEDIA : COMBINE_MODE_NONE;
        case TdApi.InputMessageVideo.CONSTRUCTOR:
          return ((TdApi.InputMessageVideo) content).selfDestructType == null ? COMBINE_MODE_MEDIA : COMBINE_MODE_NONE;
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
      case TdApi.UserPrivacySettingShowBio.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyShowBioNobodyExcept;
        nobodyRes = R.string.PrivacyShowBioNobody;
        contactsExceptRes = R.string.PrivacyShowBioContactsExcept;
        contactsRes = R.string.PrivacyShowBioContacts;
        everybodyExceptRes = R.string.PrivacyShowBioEverybodyExcept;
        everybodyRes = R.string.PrivacyShowBioEverybody;
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
      case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR:
        nobodyExceptRes = R.string.PrivacyVoiceVideoNobodyExcept;
        nobodyRes = R.string.PrivacyVoiceVideoNobody;
        contactsExceptRes = R.string.PrivacyVoiceVideoContactsExcept;
        contactsRes = R.string.PrivacyVoiceVideoContacts;
        everybodyExceptRes = R.string.PrivacyVoiceVideoEverybodyExcept;
        everybodyRes = R.string.PrivacyVoiceVideoEverybody;
        break;
      default:
        Td.assertUserPrivacySetting_21d3f4();
        throw new UnsupportedOperationException(Integer.toString(privacyKey));
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

  public static <T extends TdApi.Object> TdApi.Function<T>[] toArray (Collection<TdApi.Function<T>> collection) {
    //noinspection unchecked
    return (TdApi.Function<T>[]) collection.toArray(new TdApi.Function<?>[0]);
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

  public static boolean forwardMessages (long toChatId, long toMessageThreadId, TdApi.Message[] messages, boolean sendCopy, boolean removeCaption, TdApi.MessageSendOptions options, ArrayList<TdApi.Function<?>> out) {
    if (messages.length == 0) {
      return false;
    }
    long fromChatId = 0;
    int index = 0;
    int size = 0;
    for (TdApi.Message message : messages) {
      if (message.chatId != fromChatId) {
        if (size > 0) {
          out.add(new TdApi.ForwardMessages(toChatId, toMessageThreadId, fromChatId, getMessageIds(messages, index, size), options, sendCopy, removeCaption));
        }
        fromChatId = message.chatId;
        index += size;
        size = 0;
      }
      size++;
    }
    if (size > 0) {
      out.add(new TdApi.ForwardMessages(toChatId, toMessageThreadId, fromChatId, getMessageIds(messages, index, size), options, sendCopy, removeCaption));
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
        TdApi.ChatAdministratorRights rights = admin.rights;
        return !(
          rights.canChangeInfo &&
          rights.canDeleteMessages &&
          rights.canInviteUsers &&
          rights.canRestrictMembers &&
          rights.canPinMessages &&
          rights.canManageVideoChats &&
          !rights.canPromoteMembers &&
          StringUtils.isEmpty(admin.customTitle) &&
          !rights.isAnonymous
        );
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
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canPromoteMembers;
    }
    return false;
  }

  public static boolean canAddMembers (TdApi.ChatMemberStatus status) {
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canInviteUsers;
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
        default: {
          Td.assertDiceStickers_bd2aa513();
          throw Td.unsupported(stickers);
        }
      }
    }
    return false;
  }

  public static boolean isFileLoaded (TdApi.Message message) {
    return message != null && isFileLoaded(getFile(message));
  }

  public static boolean isFileLoadedAndExists (TdApi.File file) {
    if (isFileLoaded(file)) {
      File check = new File(file.local.path);
      if (!check.exists()) {
        return false;
      }
      long length = check.length();
      return !(length < file.local.downloadedSize && length > 0); // FIXME: length = file.local.downloadedSize?
    }
    return false;
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

  public static TdApi.InputMessageAnimation toInputMessageContent (TdApi.Animation animation) {
    return new TdApi.InputMessageAnimation(new TdApi.InputFileId(animation.animation.id), null, null, animation.duration, animation.width, animation.height, null, false);
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
    return new TdApi.User(
      userId,
      firstName,
      lastName,
      null,
      "",
      new TdApi.UserStatusEmpty(),
      null,
      TdlibAccentColor.defaultAccentColorIdForUserId(userId), 0,
      0, 0,
      null,
      false,
      false,
      false,
      false,
      false,
      false,
      null,
      false,
      false,
      false, false,
      true,
      new TdApi.UserTypeRegular(),
      null,
      false
    );
  }

  public static TdApi.Audio newFakeAudio (TdApi.Document doc) {
    return new TdApi.Audio(0, doc.fileName, "", doc.fileName, doc.mimeType, doc.minithumbnail, doc.thumbnail, null, doc.document);
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

  public static long getFileSize (TdApi.File file) {
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
    if (state != null) {
      switch (state.getConstructor()) {
        case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
          return Td.codeLength(((TdApi.AuthorizationStateWaitCode) state).codeInfo.type, TdConstants.DEFAULT_CODE_LENGTH);
        case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR:
          return getCodeLength(((TdApi.AuthorizationStateWaitEmailCode) state).codeInfo);
      }
    }
    return TdConstants.DEFAULT_CODE_LENGTH;
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
          return "@" + Td.primaryUsername(user);
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
      //noinspection SwitchIntDef
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
      if (StringUtils.isEmpty(error.message)) {
        return "Empty error " + error.code;
      }
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
      case "PHONE_CODE_INVALID": case "EMAIL_CODE_INVALID": res = R.string.InvalidCode; break;
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
      case "Can't access the chat": res = R.string.errorChatInaccessible; break;
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
        } else if (((TdApi.ChatMemberStatusAdministrator) myStatus).rights.canPromoteMembers) {
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
        if (((TdApi.ChatMemberStatusAdministrator) me).rights.canRestrictMembers) {
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

  public static void processAlbum (Tdlib tdlib, long chatId, TdApi.MessageSendOptions options, List<TdApi.Function<?>> functions, List<TdApi.InputMessageContent> album) {
    if (album == null || album.isEmpty())
      return;
    if (album.size() == 1) {
      processSingle(tdlib, chatId, options, functions, album.get(0));
    } else {
      TdApi.InputMessageContent[] array = new TdApi.InputMessageContent[album.size()];
      album.toArray(array);
      functions.add(new TdApi.SendMessageAlbum(chatId, 0, null, options, array));
    }
    album.clear();
  }

  public static List<TdApi.Function<?>> toFunctions (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.InputMessageContent[] content, boolean needGroupMedia) {
    if (content.length == 0)
      return Collections.emptyList();

    List<TdApi.Function<?>> functions = new ArrayList<>();

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
        functions.add(new TdApi.SendMessage(chatId, messageThreadId, functions.isEmpty() ? replyTo : null, options, null, slice[0]));
      } else {
        for (TdApi.InputMessageContent inputContent : slice) {
          if (inputContent.getConstructor() == TdApi.InputMessageDocument.CONSTRUCTOR) {
            ((TdApi.InputMessageDocument) inputContent).disableContentTypeDetection = true;
          }
        }
        functions.add(new TdApi.SendMessageAlbum(chatId, messageThreadId, functions.isEmpty() ? replyTo : null, options, slice));
      }

      remaining -= sliceSize;
      processed += sliceSize;
    } while (remaining > 0);

    return functions;
  }

  public static void processSingle (Tdlib tdlib, long chatId, TdApi.MessageSendOptions options, List<TdApi.Function<?>> functions, TdApi.InputMessageContent content) {
    functions.add(new TdApi.SendMessage(chatId, 0, null, options, null, content));
  }

  public static boolean withinDistance (TdApi.File file, long offset) {
    return offset >= file.local.downloadOffset && offset <= file.local.downloadOffset + file.local.downloadedPrefixSize + ByteUnit.KIB.toBytes(512);
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

  public static boolean isLocalLanguagePackId (String languagePackId) {
    return languagePackId.startsWith("X");
  }

  @Deprecated
  public static boolean isRawLanguage (String id) {
    return !id.startsWith("X") && id.endsWith("-raw");
  }

  public static TdApi.File newFile (File file) {
    final String path = file.getPath();
    final long size = file.length();
    return newFile(0, path, path, size);
  }

  public static TdApi.File newFile (int id, String remoteId, String path, long size) {
    return new TdApi.File(id, size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile(remoteId, "", false, false, 0));
  }

  public static String normalizePath (String path) {
    return StringUtils.isEmpty(path) || path.charAt(path.length() - 1) == '/' ? path : path + '/';
  }

  public static boolean isTelegramMeHost (Uri uri) {
    String host = uri.getHost();
    if (host != null) {
      for (String knownHost : TdConstants.TME_HOSTS) {
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

    if (!FileUtils.createDirectory(dir)) {
      return null;
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
            String clickedText = spanned.subSequence(startIndex, endIndex).toString();
            if (b == null)
              b = spanned instanceof SpannableStringBuilder ? (SpannableStringBuilder) spanned : new SpannableStringBuilder(spanned);
            b.removeSpan(span);
            b.setSpan(new ClickableSpan() {
              @Override
              public void onClick (@NonNull View widget) {
                span.onClick(widget, clickedText);
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

  public static int calculateUnreadStickerSetCount (TdApi.TrendingStickerSets stickerSets) {
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
        if (Td.isHashtag(entity.type)) {
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

  public static boolean isChannel (TdApi.InviteLinkChatType type) {
    return type != null && type.getConstructor() == TdApi.InviteLinkChatTypeChannel.CONSTRUCTOR;
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
    return defaultValue != null ? defaultValue.getValue() : null;
  }

  public static @Nullable String findOrdinary (TdApi.LanguagePackString[] strings, String key, Future<String> defaultValue) {
    for (TdApi.LanguagePackString string : strings) {
      if (key.equals(string.key)) {
        return string.value instanceof TdApi.LanguagePackStringValueOrdinary ? ((TdApi.LanguagePackStringValueOrdinary) string.value).value : defaultValue != null ? defaultValue.getValue() : null;
      }
    }
    return defaultValue != null ? defaultValue.getValue() : null;
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

  public static String getTextFromMessageSpoilerless (TdApi.Message msg) {
    return msg != null ? getTextFromMessageSpoilerless(msg.content) : null;
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
    return canBeEdited(content) && !Td.isLocation(content);
  }

  public static boolean canBeEdited (TdApi.MessageContent content) {
    //noinspection SwitchIntDef
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

  public static String getTextFromMessageSpoilerless (TdApi.MessageContent content) {
    TdApi.FormattedText formattedText = Td.textOrCaption(content);
    return formattedText != null ? TD.toCharSequence(formattedText, false, false).toString() : null;
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

  public static boolean canCopyText (TdApi.Message msg) {
    TdApi.FormattedText text = msg != null ? Td.textOrCaption(msg.content) : null;
    return !Td.isEmpty(Td.trim(text));
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
    if (content == null || !Td.isText(content)) {
      return content;
    }
    TdApi.MessageText messageText = (TdApi.MessageText) content;
    String linkPreviewUrl = Td.findLinkPreviewUrl(messageText);
    if (StringUtils.isEmpty(linkPreviewUrl)) {
      return messageText;
    }
    String linkPreviewText = "[" + Lang.getString(R.string.LinkPreview) + "]";
    // TODO maybe: show changes in LinkPreviewOptions?
    TdApi.FormattedText newText = Td.concat(
      messageText.text,
      new TdApi.FormattedText("\n", null),
      new TdApi.FormattedText(linkPreviewText, new TdApi.TextEntity[] {
        new TdApi.TextEntity(0, linkPreviewText.length(), new TdApi.TextEntityTypeItalic()),
        new TdApi.TextEntity(0, linkPreviewText.length(), new TdApi.TextEntityTypeTextUrl(linkPreviewUrl))
      })
    );
    return new TdApi.MessageText(newText, null, null);
  }

  public static class DownloadedFile {
    private final TdApi.File file;
    private final String fileName;
    private final String mimeType;
    private final long fileSize;
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

    public long getFileSize () {
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
    @TdApi.MessageContent.Constructors int constructor = message.content.getConstructor();
    if (constructor == TdApi.MessageText.CONSTRUCTOR) {
      constructor = convertToMessageContent(((TdApi.MessageText) message.content).webPage);
    }
    //noinspection SwitchIntDef
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

  public static void saveFiles (BaseActivity context, List<DownloadedFile> files) {
    if (context.permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> {
      if (granted) {
        saveFiles(context, files);
      }
    })) {
      return;
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

  public static void saveFile (BaseActivity context, DownloadedFile file) {
    switch (file.getFileType().getConstructor()) {
      case TdApi.FileTypeAnimation.CONSTRUCTOR: {
        U.copyToGallery(context, file.getPath(), U.TYPE_GIF);
        break;
      }
      case TdApi.FileTypeVideo.CONSTRUCTOR: {
        U.copyToGallery(context, file.getPath(), U.TYPE_VIDEO);
        break;
      }
      case TdApi.FileTypePhoto.CONSTRUCTOR: {
        U.copyToGallery(context, file.getPath(), U.TYPE_PHOTO);
        break;
      }
      default: {
        saveToDownloads(context, file);
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

    if (!FileUtils.createDirectory(destDir)) {
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
    if (!FileUtils.createDirectory(destDir)) {
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
      if (context.permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> {
        if (granted) {
          saveToDownloads(file, mimeType);
        }
      })) {
        return;
      }
    }

    Background.instance().post(() -> saveToDownloadsImpl(file, mimeType));
  }

  public static void saveToDownloads (final BaseActivity context, final DownloadedFile file) {
    if (file == null) {
      return;
    }

    if (context.permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> {
      if (granted) {
        saveToDownloads(context, file);
      }
    })) {
      return;
    }

    Background.instance().post(() -> {
      File savedFile = saveToDownloadsImpl(file);
      if (savedFile != null) {
        UI.showToast(Lang.getString(R.string.DownloadedToPath, savedFile.getPath()), Toast.LENGTH_LONG);
      }
    });
  }

  // other

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
    CustomTypefaceSpan span = new CustomTypefaceSpan(null, 0);
    span.setTextEntityType(type);
    return span;
  }

  public static Object newBoldSpan (@NonNull String text) {
    boolean needFakeBold = Text.needFakeBold(text);
    CustomTypefaceSpan span = new CustomTypefaceSpan(needFakeBold ? null : Fonts.getRobotoMedium(), 0).setFakeBold(needFakeBold);
    span.setTextEntityType(new TdApi.TextEntityTypeBold());
    return span;
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
      Object span = toDisplaySpan(entity.type, defaultTypeface, Text.needFakeBold(text.text, entity.offset, entity.offset + entity.length));
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
    TdApi.FormattedText formattedText;
    try {
      formattedText = Client.execute(new TdApi.GetMarkdownText(text));
    } catch (Client.ExecutionException error) {
      Log.w("getMarkdownText: %s", TD.toErrorString(error.error));
      return text.text;
    }
    return toCharSequence(formattedText, true, true);
  }

  private static HtmlTag toHtmlTag (TdApi.TextEntityType entityType) {
    switch (entityType.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        return new HtmlTag("b");
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        return new HtmlTag("i");
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        return new HtmlTag("code");
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
        return new HtmlTag("pre");
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        return new HtmlTag("spoiler");
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        return new HtmlTag("s");
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        return new HtmlTag("u");
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
        return new HtmlTag("blockquote");
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return new HtmlTag(
          "<tg-user-mention data-user-id=\"" + ((TdApi.TextEntityTypeMentionName) entityType).userId + "\">",
          "</tg-user-mention>"
        );
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR: {
        String language = ((TdApi.TextEntityTypePreCode) entityType).language;
        return new HtmlTag(
          "<tg-pre-code data-language=\"" + (language != null ? Html.escapeHtml(language) : "") + "\">",
          "</tg-pre-code>"
        );
      }
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        return new HtmlTag(
          // intentionally matching tag to other apps to allow cross-app copy/paste
          "<animated-emoji data-document-id=\"" + ((TdApi.TextEntityTypeCustomEmoji) entityType).customEmojiId + "\">",
          "</animated-emoji>"
        );
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
        String hrefAttribute = Html.escapeHtml(((TdApi.TextEntityTypeTextUrl) entityType).url);
        return new HtmlTag(
          "<a href=\"" + hrefAttribute + "\">",
          "</a>"
        );
      }
      // automatically highlighted
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return null;
      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(entityType);
    }
  }

  private static HtmlTag[] toHtmlTag (Object span) {
    TdApi.TextEntityType[] entityTypes = toEntityType(span);
    if (entityTypes != null && entityTypes.length > 0) {
      List<HtmlTag> tags = new ArrayList<>();
      for (TdApi.TextEntityType entityType : entityTypes) {
        HtmlTag tag = toHtmlTag(entityType);
        if (tag != null) {
          tags.add(tag);
        }
      }
      if (!tags.isEmpty()) {
        return tags.toArray(new HtmlTag[0]);
      }
    }
    return null;
  }

  @Nullable
  public static String toHtmlCopyText (Spanned spanned) {
    HtmlEncoder.EncodeResult encodeResult = HtmlEncoder.toHtml(spanned, Object.class, TD::toHtmlTag);
    return encodeResult.tagCount > 0 ? encodeResult.htmlText : null;
  }

  @Nullable
  public static CharSequence htmlToCharSequence (String htmlText) {
    HtmlParser.Replacer<TdApi.TextEntityType> entityReplacer = (text, start, end, mark) -> {
      Object span = toSpan(mark);
      if (span != null) {
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    };
    return HtmlParser.fromHtml(htmlText, null, (opening, tag, output, xmlReader, attributes) -> {
      // <tg-user-mention data-user-id="ID">...</tg-user-mention>
      // <tg-pre-code data-language="LANG">...</tg-pre-code>
      // <animated-emoji data-document-id="ID">...</animated-emoji>
      // <spoiler>...</spoiler>
      // <pre>...</pre>
      // <code>...</code>
      // <tg-pre-code language="...">...</tg-pre-code>
      if (opening) {
        if (tag.equalsIgnoreCase("tg-user-mention")) {
          long userId = StringUtils.parseLong(attributes.getValue("", "data-user-id"));
          TdApi.TextEntityTypeMentionName mention = new TdApi.TextEntityTypeMentionName(userId);
          HtmlParser.start(output, mention);
        } else if (tag.equalsIgnoreCase("animated-emoji")) {
          long customEmojiId = StringUtils.parseLong(attributes.getValue("", "data-document-id"));
          TdApi.TextEntityTypeCustomEmoji customEmoji = new TdApi.TextEntityTypeCustomEmoji(customEmojiId);
          HtmlParser.start(output, customEmoji);
        } else if (tag.equalsIgnoreCase("spoiler")) {
          HtmlParser.start(output, new TdApi.TextEntityTypeSpoiler());
        } else if (tag.equalsIgnoreCase("pre")) {
          HtmlParser.start(output, new TdApi.TextEntityTypePre());
        } else if (tag.equalsIgnoreCase("code")) {
          HtmlParser.start(output, new TdApi.TextEntityTypeCode());
        } else if (tag.equalsIgnoreCase("tg-pre-code")) {
          String language = attributes.getValue("", "data-language");
          HtmlParser.start(output, new TdApi.TextEntityTypePreCode(language != null ? language : ""));
        } else {
          return false;
        }
        return true;
      } else if (
        tag.equalsIgnoreCase("tg-user-mention") ||
        tag.equalsIgnoreCase("animated-emoji") ||
        tag.equalsIgnoreCase("spoiler") ||
        tag.equalsIgnoreCase("pre") ||
        tag.equalsIgnoreCase("code") ||
        tag.equalsIgnoreCase("tg-pre-code")
      ) {
        HtmlParser.end(output, TdApi.TextEntityType.class, entityReplacer);
        return true;
      } else {
        return false;
      }
    });
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
      boolean isSpoiler = Td.isSpoiler(entity.type);
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
        if (Td.isSpoiler(entity.type)) {
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

  public static Object toDisplaySpan (TdApi.TextEntityType type) {
    return toDisplaySpan(type, null, false);
  }

  public static Object tempToBlockQuoteSpan (boolean isDisplay, boolean needFakeBold) {
    return new QuoteSpan(); // TODO
  }

  public static Object toDisplaySpan (TdApi.TextEntityType type, @Nullable Typeface defaultTypeface, boolean needFakeBold) {
    if (type == null)
      return null;
    Object span;
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        span = new CustomTypefaceSpan(defaultTypeface, ColorId.textLink);
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
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
        span = tempToBlockQuoteSpan(true, needFakeBold);
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
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        span = new CustomTypefaceSpan(null, 0);
        break;
      // automatically detected
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return null;
      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(type);
    }
    if (span instanceof TdlibEntitySpan) {
      ((TdlibEntitySpan) span).setTextEntityType(type);
    }
    return span;
  }

  public static void handleLegacyClick (TdlibDelegate context, String clickedText, Object span) {
    // One day rework to properly designed code instead of this mess collected over time.
    TdApi.TextEntityType type = span instanceof TdlibEntitySpan ? ((TdlibEntitySpan) span).getTextEntityType() : null;
    if (type != null) {
      //noinspection SwitchIntDef
      switch (type.getConstructor()) {
        case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: {
          TdApi.TextEntityTypeMentionName mentionName = (TdApi.TextEntityTypeMentionName) type;
          context.tdlib().ui().openPrivateProfile(context, mentionName.userId, null);
          break;
        }
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
          UI.openUrl(clickedText);
          break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
          TdApi.TextEntityTypeTextUrl textUrl = (TdApi.TextEntityTypeTextUrl) type;
          UI.openUrl(textUrl.url);
          break;
        }
      }
    }
  }

  public static Object toSpan (TdApi.TextEntityType type) {
    return toSpan(type, true);
  }

  private static final String SPOILER_REPLACEMENT_CHAR = "▒";
  private static final int SPOILER_BACKGROUND_COLOR = 0xffa9a9a9;

  public static boolean canConvertToSpan (TdApi.TextEntityType type) {
    if (type == null) {
      return false;
    }
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        return true;
      // auto-detected entities
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return false;
      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(type);
    }
  }

  public static Object toSpan (TdApi.TextEntityType type, boolean allowInternal) {
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
        if (allowInternal) {
          CustomTypefaceSpan span = new CustomTypefaceSpan(null, ColorId.textLink);
          span.setTextEntityType(type);
          return span;
        }
        return null;
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        return new CustomEmojiId(((TdApi.TextEntityTypeCustomEmoji) type).customEmojiId);
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
        return tempToBlockQuoteSpan(false, false);
      // auto-detected entities
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return null;
      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(type);
    }
  }

  public static TdApi.TextEntityType[] toEntityType (Object span) {
    if (!canConvertToEntityType(span))
      return null;
    if (span instanceof CustomTypefaceSpan)
      return new TdApi.TextEntityType[] {((CustomTypefaceSpan) span).getTextEntityType()};
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
    } else if (span instanceof TypefaceSpan) {
      return toEntityType((TypefaceSpan) span);
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
    } else if (span instanceof EmojiSpan && ((EmojiSpan) span).isCustomEmoji()) {
      return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeCustomEmoji(((EmojiSpan) span).getCustomEmojiId())};
    }
    return null;
  }

  @Nullable
  private static TdApi.TextEntityType[] toEntityType (TypefaceSpan span) {
    if ("monospace".equals(span.getFamily())) {
      return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeCode()};
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Typeface typeface = span.getTypeface();
      if (typeface == null) {
        return null;
      }
      if (typeface == Fonts.getRobotoMono()) {
        return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeCode()};
      } else if (typeface == Fonts.getRobotoBold() || typeface == Fonts.getRobotoMedium()) {
        return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeBold()};
      } else if (typeface == Fonts.getRobotoItalic()) {
        return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeItalic()};
      }
      switch (typeface.getStyle()) {
        case Typeface.NORMAL:
          break;
        case Typeface.BOLD:
          return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeBold()};
        case Typeface.ITALIC:
          return new TdApi.TextEntityType[] {new TdApi.TextEntityTypeItalic()};
        case Typeface.BOLD_ITALIC:
          return new TdApi.TextEntityType[] {
            new TdApi.TextEntityTypeBold(),
            new TdApi.TextEntityTypeItalic()
          };
      }
    }
    return null;
  }

  private static boolean canConvertToEntityType (TypefaceSpan span) {
    if ("monospace".equals(span.getFamily())) {
      return true;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Typeface typeface = span.getTypeface();
      if (typeface == null) {
        return false;
      }
      if (
        typeface == Fonts.getRobotoMono() ||
        typeface == Fonts.getRobotoBold() || typeface == Fonts.getRobotoMedium() ||
        typeface == Fonts.getRobotoItalic()
      ) {
        return true;
      }
      switch (typeface.getStyle()) {
        case Typeface.NORMAL:
          return false;
        case Typeface.BOLD:
        case Typeface.ITALIC:
        case Typeface.BOLD_ITALIC:
          return true;
      }
    }
    return false;
  }

  public static Object cloneSpan (Object span) {
    if (span instanceof CustomTypefaceSpan) {
      CustomTypefaceSpan customTypefaceSpan = (CustomTypefaceSpan) span;
      return new CustomTypefaceSpan(customTypefaceSpan);
    }
    if (span instanceof URLSpan) {
      URLSpan urlSpan = (URLSpan) span;
      return new URLSpan(urlSpan.getURL());
    }
    if (span instanceof StyleSpan) {
      StyleSpan styleSpan = (StyleSpan) span;
      return new StyleSpan(styleSpan.getStyle());
    }
    if (span instanceof TypefaceSpan) {
      TypefaceSpan typefaceSpan = (TypefaceSpan) span;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface typeface = typefaceSpan.getTypeface();
        if (typeface != null) {
          return new TypefaceSpan(typeface);
        }
      }
      return new TypefaceSpan(typefaceSpan.getFamily());
    }
    if (span instanceof BackgroundColorSpan) {
      BackgroundColorSpan backgroundColorSpan = (BackgroundColorSpan) span;
      return new BackgroundColorSpan(backgroundColorSpan.getBackgroundColor());
    }
    if (span instanceof StrikethroughSpan) {
      return new StrikethroughSpan();
    }
    if (span instanceof UnderlineSpan) {
      return new UnderlineSpan();
    }
    throw new UnsupportedOperationException(span.toString());
  }

  public static boolean canConvertToEntityType (Object span) {
    if (span instanceof CustomTypefaceSpan)
      return ((CustomTypefaceSpan) span).getTextEntityType() != null;
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
      return canConvertToEntityType((TypefaceSpan) span);
    if (span instanceof BackgroundColorSpan) {
      final int backgroundColor = ((BackgroundColorSpan) span).getBackgroundColor();
      if (backgroundColor == SPOILER_BACKGROUND_COLOR) {
        return true;
      }
    }
    if (span instanceof EmojiSpan) {
      EmojiSpan emojiSpan = (EmojiSpan) span;
      return emojiSpan.isCustomEmoji() && emojiSpan.getCustomEmojiId() != 0;
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
    Object[] spans = ((Spanned) cs).getSpans(0, cs.length(), Object.class);
    if (spans == null || spans.length == 0)
      return null;

    List<TdApi.TextEntity> entities = null;
    for (Object span : spans) {
      TdApi.TextEntityType[] types = toEntityType(span);
      if (types == null || types.length == 0)
        continue;
      int start = ((Spanned) cs).getSpanStart(span);
      int end = ((Spanned) cs).getSpanEnd(span);
      for (TdApi.TextEntityType type : types) {
        if (onlyLinks && !Td.isTextUrl(type))
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

  public static List<TdApi.SendMessage> sendMessageText (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions sendOptions, @NonNull TdApi.InputMessageContent content, int maxCodePointCount) {
    List<TdApi.InputMessageContent> list = explodeText(content, maxCodePointCount);
    List<TdApi.SendMessage> result = new ArrayList<>(list.size());
    for (TdApi.InputMessageContent item : list) {
      result.add(new TdApi.SendMessage(chatId, messageThreadId, replyTo, sendOptions, null, item));
    }
    return result;
  }

  public static List<TdApi.InputMessageContent> explodeText (@NonNull TdApi.InputMessageContent content, int maxCodePointCount) {
    if (content.getConstructor() != TdApi.InputMessageText.CONSTRUCTOR) {
      return Collections.singletonList(content);
    }
    TdApi.InputMessageText textContent = (TdApi.InputMessageText) content;
    TdApi.FormattedText text = textContent.text;
    final int codePointCount = text.text.codePointCount(0, text.text.length());
    List<TdApi.InputMessageContent> list = new ArrayList<>();
    if (codePointCount <= maxCodePointCount) {
      // Nothing to split, send as is
      list.add(textContent);
      return list;
    }
    final int textLength = text.text.length();
    int start = 0, end = 0;
    int currentCodePointCount = 0;
    while (start < textLength) {
      final int codePoint = text.text.codePointAt(end);
      currentCodePointCount++;
      end += Character.charCount(codePoint);
      if (currentCodePointCount == maxCodePointCount || end == textLength) {
        TdApi.FormattedText substring;
        if (end < textLength) {
          // Find a good place to split message within last 33% of the text to avoid word breaking:
          // If newline is present, text will be split at the last newline
          // otherwise, if whitespace is present, text will be split at the last whitespace
          // otherwise, text will be split at the last "splitter".
          // otherwise, if none of the above found, maximum chunk that fits the limit will be sent.
          int lastNewLineIndex = -1;
          int lastWhitespaceIndex = -1;
          int lastSplitterIndex = -1;
          int subStart = end - (end - start) / 3;
          int subEnd = end;
          do {
            subEnd = Text.lastIndexOfSplitter(text.text, subStart, subEnd, null);
            if (subEnd != -1) {
              int endCodePoint = text.text.codePointAt(subEnd);
              if (lastSplitterIndex == -1) {
                lastSplitterIndex = subEnd;
              }
              if (endCodePoint == (int) '\n') {
                lastNewLineIndex = subEnd;
                break;
              } else if (lastWhitespaceIndex == -1 && Character.isWhitespace(endCodePoint)) {
                lastWhitespaceIndex = subEnd;
              }
            }
          } while (subEnd != -1 && subEnd > subStart);
          if (lastNewLineIndex != -1) {
            end = lastNewLineIndex;
          } else if (lastWhitespaceIndex != -1) {
            end = lastWhitespaceIndex;
          } else if (lastSplitterIndex != -1) {
            end = lastSplitterIndex;
          }
        }
        // Send chunk between start ... end
        substring = Td.substring(text, start, end);
        boolean first = list.isEmpty();
        list.add(new TdApi.InputMessageText(substring, textContent.linkPreviewOptions, first && textContent.clearDraft));
        // Reset loop state
        start = end;
        currentCodePointCount = 0;
      }
    }
    return list;
  }



  public static boolean hasIncompleteLoginAttempts (TdApi.Session[] sessions) {
    for (TdApi.Session session : sessions) {
      if (session.isPasswordPending)
        return true;
    }
    return false;
  }

  public static long[] getUniqueEmojiIdList (@Nullable TdApi.FormattedText text) {
    if (text == null || text.text == null || text.entities == null || text.entities.length == 0) return new long[0];

    LongSet emojis = new LongSet();
    for (TdApi.TextEntity entity : text.entities) {
      if (Td.isCustomEmoji(entity.type)) {
        emojis.add(((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId);
      }
    }

    return emojis.toArray();
  }

  public static String stickerEmoji (TdApi.Sticker sticker) {
    return !StringUtils.isEmpty(sticker.emoji) ? sticker.emoji : "\uD83D\uDE00" /*😀*/;
  }

  public static TdApi.FormattedText toSingleEmojiText (TdApi.Sticker sticker) {
    String emoji = stickerEmoji(sticker);
    return new TdApi.FormattedText(emoji, new TdApi.TextEntity[]{
      new TdApi.TextEntity(0, emoji.length(), new TdApi.TextEntityTypeCustomEmoji(Td.customEmojiId(sticker)))
    });
  }

  public static int getStickerSetsUnreadCount (TdApi.StickerSetInfo[] stickerSets) {
    int unreadCount = 0;
    for (TdApi.StickerSetInfo stickerSet : stickerSets) {
      if (!stickerSet.isViewed) {
        unreadCount++;
      }
    }
    return unreadCount;
  }

  public static boolean containsMention (TdApi.FormattedText text, TdApi.User user) {
    if (text == null || user == null || text.entities == null || StringUtils.isEmpty(text.text)) {
      return false;
    }

    for (TdApi.TextEntity entity: text.entities) {
      TdApi.TextEntityType type = entity.type;
      if (type.getConstructor() == TdApi.TextEntityTypeMention.CONSTRUCTOR) {
        if (entity.length > 1 && Td.findUsername(user.usernames, text.text.substring(entity.offset + 1, entity.offset + entity.length), true)) {
          return true;
        }
      } else if (type.getConstructor() == TdApi.TextEntityTypeMentionName.CONSTRUCTOR) {
        if (user.id == ((TdApi.TextEntityTypeMentionName) type).userId) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean isScreenshotSensitive (TdApi.Message message) {
    if (message == null) {
      return false;
    }
    if (Td.isSecret(message.content)) {
      return true;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
        return true;
      default:
        Td.assertMessageContent_d40af239();
        break;
    }
    return false;
  }

  public static boolean hasCustomEmoji (TdApi.FormattedText text) {
    if (text == null || text.entities == null) {
      return false;
    }

    for (TdApi.TextEntity entity: text.entities) {
      if (entity.type.getConstructor() == TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR) {
        return true;
      }
    }

    return false;
  }

  public static boolean isStickerFromAnimatedEmojiPack (@Nullable TdApi.MessageContent content) {
    if (content == null || content.getConstructor() != TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
      return false;
    }
    return isStickerFromAnimatedEmojiPack(((TdApi.MessageAnimatedEmoji) content).animatedEmoji.sticker);
  }

  public static boolean isStickerFromAnimatedEmojiPack (@Nullable TdApi.Sticker sticker) {
    return sticker != null && sticker.setId == TdConstants.TELEGRAM_ANIMATED_EMOJI_STICKER_SET_ID;
  }
  
  public static boolean isChatListMain (@Nullable TdApi.ChatList chatList) {
    return chatList != null && chatList.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR;
  }

  public static boolean isChatListArchive (@Nullable TdApi.ChatList chatList) {
    return chatList != null && chatList.getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR;
  }

  public static boolean isChatListFolder (@Nullable TdApi.ChatList chatList) {
    return chatList != null && chatList.getConstructor() == TdApi.ChatListFolder.CONSTRUCTOR;
  }

  public static void saveChatFolder (Bundle bundle, String prefix, @Nullable TdApi.ChatFolder chatFolder) {
    if (chatFolder == null) {
      return;
    }
    bundle.putString(prefix + "_title", chatFolder.title);
    if (chatFolder.icon != null) {
      bundle.putString(prefix + "_iconName", chatFolder.icon.name);
    }
    bundle.putLongArray(prefix + "_pinnedChatIds", chatFolder.pinnedChatIds);
    bundle.putLongArray(prefix + "_includedChatIds", chatFolder.includedChatIds);
    bundle.putLongArray(prefix + "_excludedChatIds", chatFolder.excludedChatIds);
    bundle.putIntArray(prefix + "_includedChatTypes", includedChatTypes(chatFolder));
    bundle.putIntArray(prefix + "_excludedChatTypes", excludedChatTypes(chatFolder));
  }

  public static @Nullable TdApi.ChatFolder restoreChatFolder (Bundle bundle, String prefix) {
    String title = bundle.getString(prefix + "_title");
    if (title == null) {
      return null;
    }
    TdApi.ChatFolder chatFolder = newChatFolder(title);
    String iconName = bundle.getString(prefix + "_iconName", null);
    if (iconName != null) {
      chatFolder.icon = new TdApi.ChatFolderIcon(iconName);
    }
    chatFolder.pinnedChatIds = bundle.getLongArray(prefix + "_pinnedChatIds");
    chatFolder.includedChatIds = bundle.getLongArray(prefix + "_includedChatIds");
    chatFolder.excludedChatIds = bundle.getLongArray(prefix + "_excludedChatIds");
    int[] includedChatTypes = bundle.getIntArray(prefix + "_includedChatTypes");
    int[] excludedChatTypes = bundle.getIntArray(prefix + "_excludedChatTypes");
    updateIncludedChatTypes(chatFolder, (chatType) -> ArrayUtils.contains(includedChatTypes, chatType));
    updateExcludedChatTypes(chatFolder, (chatType) -> ArrayUtils.contains(excludedChatTypes, chatType));
    return chatFolder;
  }

  public static TdApi.ChatFolder newChatFolder () {
    return new TdApi.ChatFolder("", null, false, ArrayUtils.EMPTY_LONGS, ArrayUtils.EMPTY_LONGS, ArrayUtils.EMPTY_LONGS, false, false, false, false, false, false, false, false);
  }

  public static TdApi.ChatFolder newChatFolder (String title) {
    TdApi.ChatFolder chatFolder = newChatFolder();
    chatFolder.title = title;
    return chatFolder;
  }

  public static TdApi.ChatFolder newChatFolder (long[] includedChatIds) {
    TdApi.ChatFolder chatFolder = newChatFolder();
    chatFolder.includedChatIds = includedChatIds;
    return chatFolder;
  }

  public static void updateIncludedChats (TdApi.ChatFolder chatFolder, Set<Long> chatIds) {
    updateIncludedChats(chatFolder, null, chatIds);
  }

  public static void updateIncludedChats (TdApi.ChatFolder chatFolder, @Nullable TdApi.ChatFolder originChatFolder, Set<Long> chatIds) {
    if (chatIds.isEmpty()) {
      chatFolder.pinnedChatIds = ArrayUtils.EMPTY_LONGS;
      chatFolder.includedChatIds = ArrayUtils.EMPTY_LONGS;
    } else {
      LongList pinnedChatIds = new LongList(chatIds.size());
      LongList includedChatIds = new LongList(chatIds.size());
      for (long chatId : chatIds) {
        if (ArrayUtils.contains(chatFolder.pinnedChatIds, chatId) || (originChatFolder != null && ArrayUtils.contains(originChatFolder.pinnedChatIds, chatId))) {
          pinnedChatIds.append(chatId);
        } else {
          includedChatIds.append(chatId);
        }
      }
      chatFolder.pinnedChatIds = pinnedChatIds.get();
      chatFolder.includedChatIds = includedChatIds.get();
    }
    chatFolder.excludedChatIds = U.removeAll(chatFolder.excludedChatIds, chatIds);
  }

  public static void updateExcludedChats (TdApi.ChatFolder chatFolder, Set<Long> chatIds) {
    chatFolder.pinnedChatIds = U.removeAll(chatFolder.pinnedChatIds, chatIds);
    chatFolder.includedChatIds = U.removeAll(chatFolder.includedChatIds, chatIds);
    chatFolder.excludedChatIds = U.toArray(chatIds);
  }

  public static TdApi.ChatFolder copyOf (TdApi.ChatFolder folder) {
    return new TdApi.ChatFolder(
      folder.title,
      folder.icon,
      folder.isShareable,
      folder.pinnedChatIds,
      folder.includedChatIds,
      folder.excludedChatIds,
      folder.excludeMuted,
      folder.excludeRead,
      folder.excludeArchived,
      folder.includeContacts,
      folder.includeNonContacts,
      folder.includeBots,
      folder.includeGroups,
      folder.includeChannels
    );
  }

  public static boolean contentEquals (TdApi.ChatFolder lhs, TdApi.ChatFolder rhs) {
    if (lhs == rhs) {
      return true;
    }
    if (!ObjectUtils.equals(lhs.title, rhs.title)) return false;
    String a = lhs.icon != null ? lhs.icon.name : null;
    String b = rhs.icon != null ? rhs.icon.name : null;
    return ObjectUtils.equals(a, b) &&
      lhs.includeContacts == rhs.includeContacts &&
      lhs.includeNonContacts == rhs.includeNonContacts &&
      lhs.includeGroups == rhs.includeGroups &&
      lhs.includeChannels == rhs.includeChannels &&
      lhs.includeBots == rhs.includeBots &&
      lhs.excludeMuted == rhs.excludeMuted &&
      lhs.excludeRead == rhs.excludeRead &&
      lhs.excludeArchived == rhs.excludeArchived &&
      lhs.pinnedChatIds.length == rhs.pinnedChatIds.length &&
      lhs.includedChatIds.length == rhs.includedChatIds.length &&
      lhs.excludedChatIds.length == rhs.excludedChatIds.length &&
      Arrays.equals(lhs.pinnedChatIds, rhs.pinnedChatIds) &&
      U.unmodifiableTreeSetOf(lhs.includedChatIds).equals(U.unmodifiableTreeSetOf(rhs.includedChatIds)) &&
      U.unmodifiableTreeSetOf(lhs.excludedChatIds).equals(U.unmodifiableTreeSetOf(rhs.excludedChatIds));
  }

  public static int countIncludedChatTypes (@Nullable TdApi.ChatFolder chatFolder) {
    if (chatFolder == null)
      return 0;
    int count = 0;
    if (chatFolder.includeContacts) count++;
    if (chatFolder.includeNonContacts) count++;
    if (chatFolder.includeGroups) count++;
    if (chatFolder.includeChannels) count++;
    if (chatFolder.includeBots) count++;
    return count;
  }

  public static int countExcludedChatTypes (@Nullable TdApi.ChatFolder chatFolder) {
    if (chatFolder == null)
      return 0;
    int count = 0;
    if (chatFolder.excludeMuted) count++;
    if (chatFolder.excludeRead) count++;
    if (chatFolder.excludeArchived) count++;
    return count;
  }

  public static int[] includedChatTypes (@Nullable TdApi.ChatFolder chatFolder) {
    if (chatFolder == null)
      return ArrayUtils.EMPTY_INTS;
    IntList chatTypes = new IntList(countIncludedChatTypes(chatFolder));
    if (chatFolder.includeContacts) chatTypes.append(R.id.chatType_contact);
    if (chatFolder.includeNonContacts) chatTypes.append(R.id.chatType_nonContact);
    if (chatFolder.includeGroups) chatTypes.append(R.id.chatType_group);
    if (chatFolder.includeChannels) chatTypes.append(R.id.chatType_channel);
    if (chatFolder.includeBots) chatTypes.append(R.id.chatType_bot);
    return chatTypes.get();
  }

  public static int[] excludedChatTypes (@Nullable TdApi.ChatFolder chatFolder) {
    if (chatFolder == null)
      return ArrayUtils.EMPTY_INTS;
    IntList chatTypes = new IntList(countExcludedChatTypes(chatFolder));
    if (chatFolder.excludeMuted) chatTypes.append(R.id.chatType_muted);
    if (chatFolder.excludeRead) chatTypes.append(R.id.chatType_read);
    if (chatFolder.excludeArchived) chatTypes.append(R.id.chatType_archived);
    return chatTypes.get();
  }

  public static void updateIncludedChatTypes (TdApi.ChatFolder chatFolder, Set<Integer> chatTypes) {
    updateIncludedChatTypes(chatFolder, chatTypes::contains);
  }

  public static void updateIncludedChatTypes (TdApi.ChatFolder chatFolder, Filter<Integer> filter) {
    chatFolder.includeContacts = filter.accept(R.id.chatType_contact);
    chatFolder.includeNonContacts = filter.accept(R.id.chatType_nonContact);
    chatFolder.includeGroups = filter.accept(R.id.chatType_group);
    chatFolder.includeChannels = filter.accept(R.id.chatType_channel);
    chatFolder.includeBots = filter.accept(R.id.chatType_bot);
  }

  public static void updateExcludedChatTypes (TdApi.ChatFolder chatFolder, Set<Integer> chatTypes) {
    updateExcludedChatTypes(chatFolder, chatTypes::contains);
  }

  public static void updateExcludedChatTypes (TdApi.ChatFolder chatFolder, Filter<Integer> filter) {
    chatFolder.excludeMuted = filter.accept(R.id.chatType_muted);
    chatFolder.excludeRead = filter.accept(R.id.chatType_read);
    chatFolder.excludeArchived = filter.accept(R.id.chatType_archived);
  }

  public static final int[] CHAT_TYPES = {
    R.id.chatType_contact,
    R.id.chatType_nonContact,
    R.id.chatType_group,
    R.id.chatType_channel,
    R.id.chatType_bot,
    R.id.chatType_muted,
    R.id.chatType_read,
    R.id.chatType_archived
  };

  public static final int[] CHAT_TYPES_TO_INCLUDE = {
    R.id.chatType_contact,
    R.id.chatType_nonContact,
    R.id.chatType_group,
    R.id.chatType_channel,
    R.id.chatType_bot
  };

  public static final int[] CHAT_TYPES_TO_EXCLUDE = {
    R.id.chatType_muted,
    R.id.chatType_read,
    R.id.chatType_archived
  };

  public static @StringRes int chatTypeName (@IdRes int chatType) {
    if (chatType == R.id.chatType_contact) return R.string.CategoryContacts;
    if (chatType == R.id.chatType_nonContact) return R.string.CategoryNonContacts;
    if (chatType == R.id.chatType_group) return R.string.CategoryGroups;
    if (chatType == R.id.chatType_channel) return R.string.CategoryChannels;
    if (chatType == R.id.chatType_bot) return R.string.CategoryBots;
    if (chatType == R.id.chatType_muted) return R.string.CategoryMuted;
    if (chatType == R.id.chatType_read) return R.string.CategoryRead;
    if (chatType == R.id.chatType_archived) return R.string.CategoryArchived;
    throw new IllegalArgumentException();
  }

  public static @DrawableRes int chatTypeIcon16 (@IdRes int chatType) {
    if (chatType == R.id.chatType_contact) return R.drawable.baseline_account_circle_16;
    if (chatType == R.id.chatType_nonContact) return R.drawable.baseline_help_16;
    if (chatType == R.id.chatType_group) return R.drawable.baseline_group_16;
    if (chatType == R.id.chatType_channel) return R.drawable.baseline_bullhorn_16;
    if (chatType == R.id.chatType_bot) return R.drawable.deproko_baseline_bots_16;
    if (chatType == R.id.chatType_muted) return R.drawable.baseline_notifications_off_16;
    if (chatType == R.id.chatType_read) return R.drawable.andrejsharapov_baseline_message_check_16;
    if (chatType == R.id.chatType_archived) return R.drawable.baseline_archive_16;
    throw new IllegalArgumentException();
  }

  public static @DrawableRes int chatTypeIcon24 (@IdRes int chatType) {
    if (chatType == R.id.chatType_contact) return R.drawable.baseline_account_circle_24;
    if (chatType == R.id.chatType_nonContact) return R.drawable.baseline_help_24;
    if (chatType == R.id.chatType_group) return R.drawable.baseline_group_24;
    if (chatType == R.id.chatType_channel) return R.drawable.baseline_bullhorn_24;
    if (chatType == R.id.chatType_bot) return R.drawable.deproko_baseline_bots_24;
    if (chatType == R.id.chatType_muted) return R.drawable.baseline_notifications_off_24;
    if (chatType == R.id.chatType_read) return R.drawable.andrejsharapov_baseline_message_check_24;
    if (chatType == R.id.chatType_archived) return R.drawable.baseline_archive_24;
    throw new IllegalArgumentException();
  }

  public static int chatTypeAccentColorId (@IdRes int chatType) {
    if (chatType == R.id.chatType_contact) return TdlibAccentColor.BuiltInId.BLUE;
    if (chatType == R.id.chatType_nonContact) return TdlibAccentColor.BuiltInId.CYAN;
    if (chatType == R.id.chatType_group) return TdlibAccentColor.BuiltInId.GREEN;
    if (chatType == R.id.chatType_channel) return TdlibAccentColor.BuiltInId.ORANGE;
    if (chatType == R.id.chatType_bot) return TdlibAccentColor.BuiltInId.RED;
    if (chatType == R.id.chatType_muted) return TdlibAccentColor.BuiltInId.PINK;
    if (chatType == R.id.chatType_read) return TdlibAccentColor.BuiltInId.BLUE;
    if (chatType == R.id.chatType_archived) return TdlibAccentColor.InternalId.ARCHIVE;
    throw new IllegalArgumentException();
  }

  public static @Nullable TdApi.ChatFolderIcon chatTypeIcon (@IdRes int chatType) {
    if (chatType == R.id.chatType_contact || chatType == R.id.chatType_nonContact) {
      return new TdApi.ChatFolderIcon("Private");
    }
    if (chatType == R.id.chatType_group) {
      return new TdApi.ChatFolderIcon( "Groups");
    }
    if (chatType == R.id.chatType_channel) {
      return new TdApi.ChatFolderIcon("Channels");
    }
    if (chatType == R.id.chatType_bot) {
      return new TdApi.ChatFolderIcon("Bots");
    }
    return null;
  }

  public static @DrawableRes int findFolderIcon (TdApi.ChatFolderIcon icon, @DrawableRes int defaultIcon) {
    if (icon != null && !StringUtils.isEmpty(icon.name)) {
      return iconByName(icon.name, defaultIcon);
    } else {
      return defaultIcon;
    }
  }

  public static @DrawableRes int iconByName (String iconName, @DrawableRes int defaultIcon) {
    if (StringUtils.isEmpty(iconName))
      return defaultIcon;
    switch (iconName) {
      case "All":
        return R.drawable.baseline_forum_24;
      case "Unmuted":
        return R.drawable.baseline_notifications_24;
      case "Bots":
        return R.drawable.deproko_baseline_bots_24;
      case "Channels":
        return R.drawable.baseline_bullhorn_24;
      case "Groups":
        return R.drawable.baseline_group_24;
      case "Private":
        return R.drawable.baseline_person_24;
      case "Setup":
        return R.drawable.baseline_assignment_24;
      case "Cat":
        return R.drawable.templarian_baseline_cat_24;
      case "Crown":
        return R.drawable.baseline_crown_circle_24;
      case "Favorite":
        return R.drawable.baseline_star_24;
      case "Flower":
        return R.drawable.baseline_local_florist_24;
      case "Game":
        return R.drawable.baseline_sports_esports_24;
      case "Home":
        return R.drawable.baseline_home_24;
      case "Love":
        return R.drawable.baseline_favorite_24;
      case "Mask":
        return R.drawable.deproko_baseline_masks_24;
      case "Party":
        return R.drawable.baseline_party_popper_24;
      case "Sport":
        return R.drawable.baseline_sports_soccer_24;
      case "Study":
        return R.drawable.baseline_school_24;
      case "Work":
        return R.drawable.baseline_work_24;
      case "Airplane":
        // return R.drawable.baseline_flight_24;
        return R.drawable.baseline_logo_telegram_24;
      case "Book":
        return R.drawable.baseline_book_24;
      case "Light":
        return R.drawable.deproko_baseline_lamp_filled_24;
      case "Like":
        return R.drawable.baseline_thumb_up_24;
      case "Money":
        return R.drawable.baseline_currency_bitcoin_24;
      case "Note":
        return R.drawable.baseline_music_note_24;
      case "Palette":
        // return R.drawable.baseline_palette_24;
        return R.drawable.baseline_brush_24;
      case "Unread":
        return R.drawable.baseline_mark_chat_unread_24;
      case "Travel":
        // return R.drawable.baseline_explore_24;
        return R.drawable.baseline_flight_24;
      case "Custom":
        return R.drawable.baseline_folder_24;
      case "Trade":
        return R.drawable.baseline_finance_24;
      default:
        return defaultIcon;
    }
  }

  public static final String[] ICON_NAMES = {"All", "Unread", "Unmuted", "Bots", "Channels", "Groups", "Private", "Custom", "Setup", "Cat", "Crown", "Favorite", "Flower", "Game", "Home", "Love", "Mask", "Party", "Sport", "Study", "Trade", "Travel", "Work", "Airplane", "Book", "Light", "Like", "Money", "Note", "Palette"};

  public static boolean isParticipating (@Nullable TdApi.PremiumGiveawayInfo info) {
    if (info == null || info.getConstructor() != TdApi.PremiumGiveawayInfoOngoing.CONSTRUCTOR) {
      return false;
    }

    TdApi.PremiumGiveawayInfoOngoing infoOngoing = (TdApi.PremiumGiveawayInfoOngoing) info;
    return infoOngoing.status.getConstructor() == TdApi.PremiumGiveawayParticipantStatusParticipating.CONSTRUCTOR;
  }
}
