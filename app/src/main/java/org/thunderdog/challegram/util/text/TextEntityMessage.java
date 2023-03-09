/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.util.text;

import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.HashtagChatController;
import org.thunderdog.challegram.ui.HashtagController;
import org.thunderdog.challegram.util.StringList;

import java.util.List;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

// TODO merge with TextEntityCustom into one type
public class TextEntityMessage extends TextEntity {
  private static final int FLAG_CLICKABLE = 1;
  private static final int FLAG_ESSENTIAL = 1 << 1;
  private static final int FLAG_MONOSPACE = 1 << 2;
  private static final int FLAG_BOLD = 1 << 3;
  private static final int FLAG_ITALIC = 1 << 4;
  private static final int FLAG_UNDERLINE = 1 << 5;
  private static final int FLAG_STRIKETHROUGH = 1 << 6;
  private static final int FLAG_FULL_WIDTH = 1 << 7;
  private static final int FLAG_SPOILER = 1 << 8;
  private static final int FLAG_CUSTOM_EMOJI = 1 << 9;

  private final TdApi.TextEntity clickableEntity, spoilerEntity, emojiEntity;
  private int flags;
  private ClickableSpan onClickListener;

  private static boolean hasEntityType (List<TdApi.TextEntity> entities, @TdApi.TextEntityType.Constructors int typeConstructor) {
    if (entities != null) {
      for (TdApi.TextEntity entity : entities) {
        if (entity.type.getConstructor() == typeConstructor)
          return true;
      }
    }
    return false;
  }

  private static int addFlags (TdApi.TextEntityType type) {
    int flags = 0;
    if (isEssential(type)) {
      flags |= FLAG_ESSENTIAL;
    }
    if (isMonospace(type)) {
      flags |= FLAG_MONOSPACE;
    }
    if (isFullWidth(type)) {
      flags |= FLAG_FULL_WIDTH;
    }
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        flags |= FLAG_BOLD;
        break;
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        flags |= FLAG_ITALIC;
        break;
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        flags |= FLAG_UNDERLINE;
        break;
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        flags |= FLAG_STRIKETHROUGH;
        break;
    }
    return flags;
  }

  public TextEntityMessage (@Nullable Tdlib tdlib, String in, TdApi.TextEntity entity, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    this(tdlib, in, entity.offset, entity.offset + entity.length, entity, null, openParameters);
  }

  public TextEntityMessage (@Nullable Tdlib tdlib, String in, int offset, int end, TdApi.TextEntity entity, @Nullable List<TdApi.TextEntity> parentEntities, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    this(tdlib,
      (entity.type.getConstructor() == TdApi.TextEntityTypeBold.CONSTRUCTOR || hasEntityType(parentEntities, TdApi.TextEntityTypeBold.CONSTRUCTOR)) && Text.needFakeBold(in, offset, end),
      offset, end,
      entity, parentEntities,
      openParameters
    );
  }

  private TextEntityMessage (@Nullable Tdlib tdlib, boolean needFakeBold, int offset, int end, TdApi.TextEntity entity, @Nullable List<TdApi.TextEntity> parentEntities, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(tdlib, offset, end, needFakeBold, openParameters);
    TdApi.TextEntity clickableEntity = isClickable(entity.type) ? entity : null;
    TdApi.TextEntity spoilerEntity = entity.type.getConstructor() == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR ? entity : null;
    TdApi.TextEntity emojiEntity = entity.type.getConstructor() == TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR ? entity : null;
    int flags = addFlags(entity.type);
    if (parentEntities != null) {
      for (int i = parentEntities.size() - 1; i >= 0; i--) {
        TdApi.TextEntity parentEntity = parentEntities.get(i);
        flags |= addFlags(parentEntity.type);
        if (clickableEntity == null && isClickable(parentEntity.type)) {
          clickableEntity = parentEntity;
        } else if (spoilerEntity == null && parentEntity.type.getConstructor() == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR) {
          spoilerEntity = parentEntity;
        }
      }
    }
    this.clickableEntity = clickableEntity;
    if (clickableEntity != null) {
      flags |= FLAG_CLICKABLE;
    }
    this.spoilerEntity = spoilerEntity;
    if (spoilerEntity != null) {
      flags |= FLAG_SPOILER;
    }
    this.emojiEntity = emojiEntity;
    if (emojiEntity != null) {
      flags |= FLAG_CUSTOM_EMOJI;
    }
    this.flags = flags;
  }

  private TextEntityMessage (@Nullable Tdlib tdlib, boolean needFakeBold, int offset, int end, TdApi.TextEntity clickableEntity, TdApi.TextEntity spoilerEntity, TdApi.TextEntity emojiEntity, int flags, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(tdlib, offset, end, needFakeBold, openParameters);
    this.clickableEntity = clickableEntity;
    this.spoilerEntity = spoilerEntity;
    this.emojiEntity = emojiEntity;
    this.flags = flags;
  }

  @Override
  public TextEntity setOnClickListener (ClickableSpan onClickListener) {
    this.onClickListener = onClickListener;
    this.flags |= FLAG_CLICKABLE;
    return this;
  }

  @Override
  public ClickableSpan getOnClickListener () {
    return onClickListener;
  }

  @Override
  public TextEntity makeBold (boolean needFakeBold) {
    this.flags |= FLAG_BOLD;
    this.needFakeBold = needFakeBold;
    return this;
  }

  @Override
  public TextEntity createCopy () {
    TextEntityMessage copy = new TextEntityMessage(tdlib, needFakeBold, start, end, clickableEntity, spoilerEntity, emojiEntity, flags, openParameters);
    if (customColorSet != null) {
      copy.setCustomColorSet(customColorSet);
    }
    if (onClickListener != null) {
      copy.setOnClickListener(onClickListener);
    }
    if (BitwiseUtils.getFlag(flags, FLAG_BOLD) && !BitwiseUtils.getFlag(copy.flags, FLAG_BOLD)) {
      copy.makeBold(needFakeBold);
    }
    return copy;
  }

  private TextColorSetOverride monospaceColorSet;

  @Override
  public TextColorSet getSpecialColorSet (@NonNull TextColorSet defaultColorSet) {
    if (customColorSet != null) {
      return customColorSet;
    }
    if (isMonospace()) {
      if (monospaceColorSet == null || monospaceColorSet.originalColorSet() != defaultColorSet) {
        monospaceColorSet = new TextColorSetOverride(defaultColorSet) {
          @Override
          public int clickableTextColor (boolean isPressed) {
            return isPressed ? super.clickableTextColor(true) : defaultTextColor();
          }
        };
      }
      return monospaceColorSet;
    }
    return null;
  }

  @Override
  public boolean isSmall () {
    return false;
  }

  @Override
  public boolean isIcon () {
    return false;
  }

  @Override
  public boolean isCustomEmoji () {
    return BitwiseUtils.getFlag(flags, FLAG_CUSTOM_EMOJI);
  }

  @Override
  public boolean hasMedia () {
    return isCustomEmoji();
  }

  @Override
  public long getCustomEmojiId () {
    if (isCustomEmoji()) {
      return ((TdApi.TextEntityTypeCustomEmoji) emojiEntity.type).customEmojiId;
    }
    return 0;
  }

  public static boolean isClickable (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:

      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR: {
        return true;
      }
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR: // TODO

      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR: {
        return false;
      }
    }
    return false;
  }

  private static boolean isEssential (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      // case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      // case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
        return false;
    }
    return true;
  }

  @Override
  public boolean isFullWidth () {
    return BitwiseUtils.getFlag(flags, FLAG_FULL_WIDTH);
  }

  private static boolean isMonospace (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  private static boolean isFullWidth (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  @Override
  public int getType () {
    return TYPE_MESSAGE_ENTITY;
  }

  @Override
  public boolean equals (TextEntity bRaw, int compareMode, @Nullable String originalText) {
    TextEntityMessage b = (TextEntityMessage) bRaw;
    switch (compareMode) {
      case COMPARE_MODE_NORMAL:
        return this.flags == b.flags && this.customColorSet == b.customColorSet;
      case COMPARE_MODE_CLICK_HIGHLIGHT:
        return Td.equalsTo(this.clickableEntity, b.clickableEntity) && this.onClickListener == b.onClickListener;
      case COMPARE_MODE_SPOILER: {
        if (Td.equalsTo(this.spoilerEntity, b.spoilerEntity)) {
          return true;
        }
        if (this.spoilerEntity != null && b.spoilerEntity != null) {
          if (
            this.spoilerEntity.offset == b.spoilerEntity.offset + b.spoilerEntity.length ||
            b.spoilerEntity.offset == this.spoilerEntity.offset + this.spoilerEntity.length
          ) {
            return true;
          }
          if (!StringUtils.isEmpty(originalText)) {
            Strings.CharacterCounter counter = (c) -> c == ' ';
            if (this.spoilerEntity.offset > b.spoilerEntity.offset + b.spoilerEntity.length) {
              int count = this.spoilerEntity.offset - (b.spoilerEntity.offset + b.spoilerEntity.length);
              if (count == Strings.countCharacters(originalText, b.spoilerEntity.offset + b.spoilerEntity.length, this.spoilerEntity.offset, counter)) {
                return true;
              }
            } else if (b.spoilerEntity.offset > this.spoilerEntity.offset + this.spoilerEntity.length) {
              int count = b.spoilerEntity.offset - (this.spoilerEntity.offset + this.spoilerEntity.length);
              if (count == Strings.countCharacters(originalText, this.spoilerEntity.offset + this.spoilerEntity.length, b.spoilerEntity.offset, counter)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    }
    throw new UnsupportedOperationException(Integer.toString(compareMode));
  }

  @Override
  public TdApi.TextEntity getSpoiler () {
    return spoilerEntity;
  }

  @Override
  public boolean isClickable () {
    return (flags & FLAG_CLICKABLE) != 0;
  }

  @Override
  public boolean isEssential () {
    return (flags & FLAG_ESSENTIAL) != 0;
  }

  @Override
  public boolean isMonospace () {
    return (flags & FLAG_MONOSPACE) != 0;
  }

  @Override
  public boolean isBold () {
    return BitwiseUtils.getFlag(flags, FLAG_BOLD);
  }

  @Override
  public boolean isItalic () {
    return BitwiseUtils.getFlag(flags, FLAG_ITALIC);
  }

  @Override
  public boolean isUnderline () {
    return BitwiseUtils.getFlag(flags, FLAG_UNDERLINE);
  }

  @Override
  public boolean isStrikethrough () {
    return BitwiseUtils.getFlag(flags, FLAG_STRIKETHROUGH);
  }

  @Override
  public boolean hasAnchor (String anchor) {
    return false;
  }

  @Override
  public void performClick (View view, Text text, TextPart part, @Nullable Text.ClickCallback callback) {
    final ViewController<?> context = findRoot(view);
    if (context == null) {
      Log.v("performClick ignored, because ancestor not found");
      return;
    }
    if (onClickListener != null) {
      onClickListener.onClick(view);
      return;
    }
    switch (clickableEntity.type.getConstructor()) {
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {

        String link = Td.substring(text.getText(), clickableEntity);
        TdlibUi.UrlOpenParameters openParameters = this.openParameters(view, text, part);
        if (callback == null || !callback.onUrlClick(view, link, false, openParameters)) {
          if (tdlib != null) {
            tdlib.ui().openUrl(context, link, modifyUrlOpenParameters(openParameters, callback, link));
          }
        }
        break;
      }
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
        String link = ((TdApi.TextEntityTypeTextUrl) clickableEntity.type).url;
        TdlibUi.UrlOpenParameters openParameters = this.openParameters(view, text, part);
        if (callback == null || !callback.onUrlClick(view, link, true, openParameters)) {
          context.openLinkAlert(link, modifyUrlOpenParameters(openParameters, callback, link));
        }
        break;
      }
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR: {
        String cmd = Td.substring(text.getText(), clickableEntity);
        if (callback != null && !callback.onCommandClick(view, text, part, cmd, false)) {
          Log.w("Unhandled bot command...");
        }
        break;
      }
      case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
        String username = Td.substring(text.getText(), clickableEntity);
        if (callback == null || !callback.onUsernameClick(username)) {
          if (tdlib != null) {
            tdlib.ui().openPublicChat(context, username, this.openParameters(view, text, part));
          }
        }
        break;
      }
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: {
        TdApi.TextEntityTypeMentionName mentionEntity = (TdApi.TextEntityTypeMentionName) clickableEntity.type;
        if (callback == null || !callback.onUserClick(mentionEntity.userId)) {
          if (tdlib != null) {
            tdlib.ui().openPrivateProfile(context, mentionEntity.userId, this.openParameters(view, text, part));
          }
        }
        break;
      }
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: {
        String email = Td.substring(text.getText(), clickableEntity);
        if (callback == null || !callback.onEmailClick(email)) {
          Intents.sendEmail(email);
        }
        break;
      }
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR: {
        String phoneNumber = Td.substring(text.getText(), clickableEntity);
        if (callback == null || !callback.onPhoneNumberClick(phoneNumber)) {
          Intents.openNumber(phoneNumber);
        }
        break;
      }
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: {
        String cardNumber = Td.substring(text.getText(), clickableEntity);
        if (callback == null || !callback.onBankCardNumberClick(cardNumber)) {
          if (tdlib != null) {
            tdlib.ui().openCardNumber(context, cardNumber);
          }
        }
        break;
      }
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR: {
        if (tdlib == null)
          break;
        String hashtag = Td.substring(text.getText(), clickableEntity);
        if (callback == null || !callback.onHashtagClick(hashtag)) {
          final long chatId = context.getChatId();
          ViewController<?> check = context.getParentOrSelf();
          boolean isViewingSameHashtag;
          if (check instanceof HashtagChatController) {
            HashtagChatController.Arguments args = ((HashtagChatController) check).getArgumentsStrict();
            isViewingSameHashtag = args.chatId == chatId && hashtag.equals(args.searchQuery) && args.searchSender == null;
          } else if (chatId == 0 && check instanceof HashtagController) {
            String args = ((HashtagController) check).getArgumentsStrict();
            isViewingSameHashtag = hashtag.equals(args);
          } else {
            isViewingSameHashtag = false;
          }
          if (isViewingSameHashtag) {
            return;
          }
          if (chatId != 0 && (!ChatId.isUserChat(chatId) || tdlib.isSelfChat(chatId))) {
            HashtagChatController c = new HashtagChatController(context.context(), tdlib);
            c.setArguments(new HashtagChatController.Arguments(null, chatId, hashtag, null, false));
            context.context().navigation().navigateTo(c);
          } else {
            HashtagController c = new HashtagController(context.context(), tdlib);
            c.setArguments(hashtag);
            context.context().navigation().navigateTo(c);
          }
        }
        break;
      }
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        // Non-clickable
        break;
    }
  }

  @Override
  public boolean performLongPress (final View view, final Text text, final TextPart part, boolean allowShare, final Text.ClickCallback clickCallback) {
    final ViewController<?> context = findRoot(view);
    if (context == null) {
      Log.v("performLongPress ignored, because ancestor not found");
      return false;
    }
    if (onClickListener != null) {
      return false;
    }

    if (clickableEntity.type.getConstructor() == TdApi.TextEntityTypeBotCommand.CONSTRUCTOR) {
      String command = Td.substring(text.getText(), clickableEntity);
      return clickCallback != null && clickCallback.onCommandClick(view, text, part, command, true);
    }

    final String copyText;
    if (clickableEntity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
      copyText = ((TdApi.TextEntityTypeTextUrl) clickableEntity.type).url;
    } else {
      copyText = Td.substring(text.getText(), clickableEntity);
    }

    final boolean canShare = clickableEntity.type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR || clickableEntity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR;
    final int size = canShare ? 3 : 2;
    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList icons = new IntList(size);

    switch (clickableEntity.type.getConstructor()) {
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:

      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:

      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: {
        ids.append(R.id.btn_openLink);
        strings.append(clickableEntity.type.getConstructor() == TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR ? R.string.OpenInExternalApp : R.string.Open);
        icons.append(R.drawable.baseline_open_in_browser_24);
        break;
      }
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR: {
        break;
      }
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR: // Unreachable because of the condition above
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR: // TODO

      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      default: {
        Log.i("Long press is unsupported for entity: %s", clickableEntity);
        return false;
      }
    }

    if (clickableEntity.type.getConstructor() != TdApi.TextEntityTypeMentionName.CONSTRUCTOR) {
      ids.append(R.id.btn_copyText);
      strings.append(clickableEntity.type.getConstructor() == TdApi.TextEntityTypeMention.CONSTRUCTOR ? R.string.CopyUsername : R.string.Copy);
      icons.append(R.drawable.baseline_content_copy_24);
    }

    final String copyLink;

    if (clickableEntity.type.getConstructor() == TdApi.TextEntityTypeMention.CONSTRUCTOR && copyText != null) {
      ids.append(R.id.btn_copyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);
      copyLink = TD.getLink(copyText.substring(1));
    } else {
      copyLink = null;
    }

    if (canShare && allowShare) {
      ids.append(R.id.btn_shareLink);
      strings.append(R.string.Share);
      icons.append(R.drawable.baseline_forward_24);
    }

    final int[] shareState = {0};

    context.showOptions(copyText, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_copyLink: {
          UI.copyText(copyLink != null ? copyLink : copyText, R.string.CopiedLink);
          break;
        }
        case R.id.btn_copyText: {
          int message;
          switch (clickableEntity.type.getConstructor()) {
            case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
              message = R.string.CopiedUsername;
              break;
            }
            case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
              message = R.string.CopiedHashtag;
              break;
            case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
              message = R.string.CopiedCashtag;
              break;
            case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
            case TdApi.TextEntityTypeCode.CONSTRUCTOR:
            case TdApi.TextEntityTypePre.CONSTRUCTOR: {
              message = R.string.CopiedText;
              break;
            }
            default: {
              message = R.string.CopiedLink;
              break;
            }
          }
          UI.copyText(copyText, message);
          break;
        }
        case R.id.btn_shareLink: {
          if (shareState[0] == 0) {
            shareState[0] = 1;
            TD.shareLink(new TdlibContext(context.context(), tdlib), copyText);
          }
          break;
        }
        case R.id.btn_openLink: {
          performClick(itemView, text, part, clickCallback);
          break;
        }
      }
      return true;
    }, clickCallback != null ? clickCallback.getForcedTheme(view, text) : null);

    return true;
  }

}
