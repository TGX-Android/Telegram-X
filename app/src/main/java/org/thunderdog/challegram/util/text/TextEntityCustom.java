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
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.util.text;

import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.Td;

// TODO merge with TextEntityMessage into one type
public class TextEntityCustom extends TextEntity {
  public static final int FLAG_BOLD = 1;
  public static final int FLAG_ITALIC = 1 << 1;
  public static final int FLAG_UNDERLINE = 1 << 2;
  public static final int FLAG_MONOSPACE = 1 << 3;
  public static final int FLAG_STRIKETHROUGH = 1 << 4;
  public static final int FLAG_SUBSCRIPT = 1 << 5;
  public static final int FLAG_SUPERSCRIPT = 1 << 6;
  public static final int FLAG_MARKED = 1 << 7;
  public static final int FLAG_SPOILER = 1 << 9;
  public static final int FLAG_DIFF_INSERTED = 1 << 10;
  public static final int FLAG_DIFF_DELETED = 1 << 11;
  public static final int FLAG_DIFF_REPLACED = 1 << 12;
  public static final int FLAG_CLICKABLE = 1 << 20;
  public static final int FLAG_ANCHOR = 1 << 21;
  public static final int FLAG_REFERENCE = 1 << 22;

  public static final int LINK_TYPE_NONE = 0;
  public static final int LINK_TYPE_EMAIL = 1;
  public static final int LINK_TYPE_URL = 2;
  public static final int LINK_TYPE_PHONE_NUMBER = 3;
  public static final int LINK_TYPE_BANK_CARD_NUMBER = 4;
  public static final int LINK_TYPE_BOT_COMMAND = 5;
  public static final int LINK_TYPE_DATE_TIME = 6;
  public static final int LINK_TYPE_CASHTAG = 7;
  public static final int LINK_TYPE_HASHTAG = 8;
  public static final int LINK_TYPE_MENTION = 9;
  public static final int LINK_TYPE_MENTION_NAME = 10;

  public static final int LINK_TYPE_ANCHOR = 100;
  public static final int LINK_TYPE_REFERENCE = 101;
  public static final int LINK_TYPE_BUTTON = 102;

  private final ViewController<?> context; // TODO move to TextEntity

  private int flags;

  private int linkOffset = -1;
  private int[] linkLength;
  private int linkType;
  private String link;
  private boolean linkCached;
  private TdApi.InlineButton button;

  private ClickableSpan onClickListener;
  private String anchorOrReferenceName;
  private boolean anchorIsReference;
  private String referenceAnchorName;
  private TdApi.RichTextIcon icon;
  private TdApi.RichTextCustomEmoji emoji;
  private TdApi.RichTextMathematicalExpression mathematicalExpression;
  private String copyLink;
  private TdApi.TextEntity spoiler;

  public TextEntityCustom (@Nullable ViewController<?> context, @Nullable Tdlib tdlib, String in, int offset, int end, int flags, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    this(context, tdlib, (flags & FLAG_BOLD) != 0 && Text.needFakeBold(in), offset, end, flags, openParameters);
  }

  private TextEntityCustom (@Nullable ViewController<?> context, @Nullable Tdlib tdlib, boolean needFakeBold, int offset, int end, int flags, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(tdlib, offset, end, needFakeBold, openParameters);
    this.context = context;
    this.flags = flags;
  }

  public TextEntityCustom setIcon (TdApi.RichTextIcon icon) {
    this.icon = icon;
    return this;
  }

  public TextEntityCustom setEmoji (TdApi.RichTextCustomEmoji emoji) {
    this.emoji = emoji;
    return this;
  }

  public TextEntityCustom setMathematicalExpression (TdApi.RichTextMathematicalExpression mathematicalExpression) {
    this.mathematicalExpression = mathematicalExpression;
    return this;
  }

  public TextEntityCustom setAnchorOrReferenceName (String name, boolean isReference) {
    this.anchorOrReferenceName = name;
    this.anchorIsReference = isReference;
    return this;
  }

  public TextEntityCustom setReferenceAnchorName (String referenceAnchorName) {
    this.referenceAnchorName = referenceAnchorName;
    return this;
  }

  public TextEntityCustom setCopyLink (String copyLink) {
    this.copyLink = copyLink;
    return this;
  }

  public String getLinkIfUrl () {
    return linkType == LINK_TYPE_URL ? link : null;
  }

  @Override
  public TextEntity setOnClickListener (ClickableSpan span) {
    this.onClickListener = span;
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
    TextEntityCustom copy = new TextEntityCustom(context, tdlib, needFakeBold, start, end, flags, openParameters);
    copy.setLink(linkOffset, linkLength, linkType, link, linkCached);
    copy.setButton(button);
    if (customColorSet != null) {
      copy.setCustomColorSet(customColorSet);
    }
    if (onClickListener != null) {
      copy.setOnClickListener(onClickListener);
    }
    if (copyLink != null) {
      copy.setCopyLink(copyLink);
    }
    if (referenceAnchorName != null) {
      copy.setReferenceAnchorName(referenceAnchorName);
    }
    if (anchorOrReferenceName != null) {
      copy.setAnchorOrReferenceName(anchorOrReferenceName, anchorIsReference);
    }
    if (icon != null) {
      copy.setIcon(icon);
    }
    if (emoji != null) {
      copy.setEmoji(emoji);
    }
    if (mathematicalExpression != null) {
      copy.setMathematicalExpression(mathematicalExpression);
    }
    return copy;
  }

  private TextColorSetOverride cachedLinkSet;
  private TextColorSetOverride cachedDiffSet;

  public boolean isDiffReplacement () { return (flags & FLAG_DIFF_REPLACED) != 0; }

  @Override
  public TextColorSet getSpecialColorSet (@NonNull TextColorSet defaultColorSet) {
    if ((flags & (FLAG_DIFF_INSERTED | FLAG_DIFF_DELETED)) != 0) {
      if (cachedDiffSet == null || cachedDiffSet.originalColorSet() != defaultColorSet) {
        cachedDiffSet = new TextColorSetOverride(defaultColorSet) {
          @Override public int defaultTextColor () { return (flags & FLAG_DIFF_DELETED) != 0 ? org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.textNegative) : org.thunderdog.challegram.theme.Theme.textLinkColor(); }
          @Override public int clickableTextColor (boolean pressed) { return defaultTextColor(); }
        };
      }
      return cachedDiffSet;
    }
    TextColorSet colorSet;
    if (customColorSet != null) {
      colorSet = customColorSet;
    } else if (linkType == LINK_TYPE_REFERENCE) {
      colorSet = TextColorSets.InstantView.REFERENCE;
    } else if (linkType == LINK_TYPE_BUTTON) {
      colorSet = new ArticleButtonColors(button != null ? button.style : null);
    } else if (BitwiseUtils.hasFlag(flags, FLAG_MARKED)) {
      colorSet = TextColorSets.InstantView.Marked.NORMAL;
    } else if (BitwiseUtils.hasFlag(flags, FLAG_MONOSPACE)) {
      colorSet = TextColorSets.InstantView.Monospace.NORMAL;
    } else {
      colorSet = null;
    }
    if (linkCached && linkType != LINK_TYPE_BUTTON) {
      int backgroundColorId = (colorSet != null ? colorSet : defaultColorSet).backgroundColorId(false);
      if (backgroundColorId == 0) {
        if (cachedLinkSet == null || cachedLinkSet.originalColorSet() != defaultColorSet) {
          cachedLinkSet = new TextColorSetOverride(defaultColorSet) {
            @Override
            public int backgroundColor (boolean isPressed) {
              return super.backgroundColor(true);
            }

            @Override
            public int backgroundColorId (boolean isPressed) {
              return super.backgroundColorId(true);
            }
          };
        }
        return cachedLinkSet;
      }
    }
    return colorSet;
  }

  @Override
  public boolean isSmall () {
    return BitwiseUtils.hasFlag(flags, FLAG_SUPERSCRIPT) || BitwiseUtils.hasFlag(flags, FLAG_SUBSCRIPT);
  }

  @Override
  public boolean isIcon () {
    return icon != null;
  }

  @Override
  public boolean isCustomEmoji () {
    return emoji != null;
  }

  @Override
  public long getCustomEmojiId () {
    return emoji != null ? emoji.customEmojiId : 0;
  }

  @Override
  public boolean forceDisableAnimations () {
    return false;
  }

  @Override
  public boolean hasMedia () {
    return isIcon() || isCustomEmoji() || mathematicalExpression != null;
  }

  @Override public String getMathematicalExpression () {
    return mathematicalExpression != null ? mathematicalExpression.expression : null;
  }

  @Override
  public TdApi.RichTextIcon getIcon () {
    return icon;
  }

  @Override
  public float getBaselineShift () {
    float baselineShift;
    if (BitwiseUtils.hasFlag(flags, FLAG_SUPERSCRIPT) && BitwiseUtils.hasFlag(flags, FLAG_SUBSCRIPT)) {
      baselineShift = 0f;
    } else if (BitwiseUtils.hasFlag(flags, FLAG_SUPERSCRIPT)) {
      baselineShift = .4f;
    } else if (BitwiseUtils.hasFlag(flags, FLAG_SUBSCRIPT)) {
      baselineShift = -.4f;
    } else {
      baselineShift = 0f;
    }
    return baselineShift;
  }

  public int getFlags () {
    return flags;
  }

  public void setLink (int offset, int[] linkLength, int type, String link, boolean linkCached) {
    this.linkOffset = offset;
    this.linkLength = linkLength;
    this.linkType = type;
    this.link = link;
    this.linkCached = linkCached;
  }

  public void setButton (TdApi.InlineButton button) {
    this.button = button;
  }

  // Impl

  @Override
  public int getType () {
    return TYPE_CUSTOM;
  }

  @Override
  public boolean isClickable () {
    return (flags & FLAG_CLICKABLE) != 0 || isMonospace();
  }

  @Override
  public TdApi.TextEntity getSpoiler () {
    if (!BitwiseUtils.hasFlag(flags, FLAG_SPOILER)) return null;
    if (spoiler == null) spoiler = new TdApi.TextEntity(start, end - start, new TdApi.TextEntityTypeSpoiler());
    spoiler.offset = start; spoiler.length = end - start;
    return spoiler;
  }

  @Override
  public TdApi.TextEntity getQuote () {
    return null;
  }

  @Override
  public boolean isEssential () {
    return true;
  }

  @Override
  public boolean isMonospace () {
    return (flags & FLAG_MONOSPACE) != 0;
  }

  @Override
  public boolean isBold () {
    return BitwiseUtils.hasFlag(flags, FLAG_BOLD);
  }

  @Override
  public boolean isItalic () {
    return BitwiseUtils.hasFlag(flags, FLAG_ITALIC);
  }

  @Override
  public boolean isUnderline () {
    return BitwiseUtils.hasFlag(flags, FLAG_UNDERLINE);
  }

  @Override
  public boolean isStrikethrough () {
    return BitwiseUtils.hasFlag(flags, FLAG_STRIKETHROUGH);
  }

  @Override
  public boolean hasAnchor (String anchor) {
    return !StringUtils.isEmpty(this.anchorOrReferenceName) && this.anchorOrReferenceName.equals(anchor);
  }

  @Override
  public boolean isFullWidth () {
    return false;
  }

  @Override
  public int getQuoteId () {
    return -1;
  }

  @Override
  public boolean isQuote () {
    return false;
  }

  @Override
  public void performClick (View view, Text text, TextPart part, @Nullable Text.ClickCallback callback, boolean isFromLongPressMenu) {
    switch (linkType) {
      case LINK_TYPE_MENTION: {
        if (callback == null || !callback.onUsernameClick(link)) {
          if (context != null) tdlib.ui().openPublicChat(context, link, openParameters(view, text, part, isFromLongPressMenu));
        }
        break;
      }
      case LINK_TYPE_MENTION_NAME: {
        long userId = StringUtils.parseLong(link);
        if (callback == null || !callback.onUserClick(userId)) {
          if (context != null) tdlib.ui().openPrivateProfile(context, userId, openParameters(view, text, part, isFromLongPressMenu));
        }
        break;
      }
      case LINK_TYPE_BANK_CARD_NUMBER: {
        if ((callback == null || !callback.onBankCardNumberClick(link)) && context != null) tdlib.ui().openCardNumber(context, link);
        break;
      }
      case LINK_TYPE_BOT_COMMAND: {
        if (callback != null) callback.onCommandClick(view, text, part, link, isFromLongPressMenu);
        break;
      }
      case LINK_TYPE_DATE_TIME: {
        int unixTime = StringUtils.parseInt(link);
        if (callback == null || !callback.onDateClick(view, text, part, text.getText().substring(start, end), new TdApi.TextEntityTypeDateTime(unixTime, null), isFromLongPressMenu)) Intents.openDate(unixTime);
        break;
      }
      case LINK_TYPE_HASHTAG:
      case LINK_TYPE_CASHTAG: {
        if ((callback == null || !callback.onHashtagClick(link)) && context != null) {
          org.thunderdog.challegram.ui.HashtagController controller = new org.thunderdog.challegram.ui.HashtagController(context.context(), tdlib);
          controller.setArguments(link);
          context.navigateTo(controller);
        }
        break;
      }
      case LINK_TYPE_EMAIL: {
        if (callback == null || !callback.onEmailClick(link)) {
          Intents.sendEmail(link);
        }
        break;
      }
      case LINK_TYPE_PHONE_NUMBER: {
        if (callback == null || !callback.onPhoneNumberClick(link)) {
          Intents.openNumber(link);
        }
        break;
      }
      case LINK_TYPE_URL: {
        TdlibUi.UrlOpenParameters openParameters = this.openParameters(view, text, part, isFromLongPressMenu);
        if (callback == null || !callback.onUrlClick(view, link, !StringUtils.equalsOrBothEmpty(text.getText(), link), openParameters)) {
          if (context != null) {
            context.openLinkAlert(link, modifyUrlOpenParameters(openParameters, callback, link));
          }
        }
        break;
      }
      case LINK_TYPE_NONE: {
        if (onClickListener != null) {
          onClickListener.onClick(view);
        }
        break;
      }
      case LINK_TYPE_ANCHOR: {
        if (callback == null || !callback.onAnchorClick(view, link)) {
          // TODO scroll to ${link}?
        }
        break;
      }
      case LINK_TYPE_REFERENCE: {
        if (callback != null && !callback.onReferenceClick(view, link, referenceAnchorName, this.openParameters(view, text, part, isFromLongPressMenu))) {
          callback.onAnchorClick(view, referenceAnchorName);
        }
        break;
      }
      case LINK_TYPE_BUTTON: {
        if (callback == null || !(callback.onButtonClick(view, button, this.openParameters(view, text, part, isFromLongPressMenu)))) {
          // TODO
        }
        break;
      }
    }
  }

  @Override
  public boolean performLongPress (final View view, final Text text, final TextPart part, boolean allowShare, Text.ClickCallback clickCallback) {
    if (clickCallback != null && !clickCallback.allowCopyText()) return false;
    final ViewController<?> context = findRoot(view);
    if (context == null) {
      Log.v("performLongPress ignored, because ancestor not found");
      return false;
    }
    if (StringUtils.isEmpty(copyLink)) {
      if (linkType == LINK_TYPE_NONE || StringUtils.isEmpty(link) || ((linkType == LINK_TYPE_ANCHOR || linkType == LINK_TYPE_REFERENCE) && (openParameters == null || StringUtils.isEmpty(openParameters.refererUrl)))) {
        if (isMonospace()) {
          String content = text.getText().substring(getStart(), getEnd());
          context.showOptions(content, new int[] {R.id.btn_copyText}, new String[] {Lang.getString(R.string.Copy)}, null, new int[] {R.drawable.baseline_content_copy_24}, Config.MAX_COPY_TEXT_LINE_COUNT, (itemView, id) -> {
            if (id == R.id.btn_copyText) {
              UI.copyText(content, R.string.CopiedText);
            }
            return true;
          }, null);
          return true;
        }
        return false;
      }
    }

    IntList ids = new IntList(3);
    StringList strings = new StringList(3);
    IntList icons = new IntList(3);

    ids.append(R.id.btn_openLink);
    strings.append(R.string.Open);
    switch (linkType) {
      case LINK_TYPE_PHONE_NUMBER: {
        icons.append(R.drawable.baseline_call_24);
        break;
      }
      case LINK_TYPE_EMAIL: {
        icons.append(R.drawable.baseline_perm_contact_calendar_24);
        break;
      }
      case LINK_TYPE_URL:
      default:{
        icons.append(R.drawable.baseline_open_in_browser_24);
        break;
      }
    }

    ids.append(R.id.btn_copyLink);
    strings.append(R.string.Copy);
    icons.append(R.drawable.baseline_content_copy_24);

    if (allowShare) {
      ids.append(R.id.btn_shareLink);
      strings.append(R.string.Share);
      icons.append(R.drawable.baseline_forward_24);
    }

    final String copyText = !StringUtils.isEmpty(copyLink) ? copyLink : link;
    final int[] shareState = {0};

    context.showOptions(copyText, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyLink) {
        UI.copyText(copyText, R.string.CopiedLink);
      } else if (id == R.id.btn_shareLink) {
        if (shareState[0] == 0) {
          shareState[0] = 1;
          TD.shareLink(new TdlibContext(context.context(), tdlib), copyText);
        }
      } else if (id == R.id.btn_openLink) {
        performClick(view, text, part, clickCallback, true);
      }
      return true;
    }, clickCallback != null ? clickCallback.getForcedTheme(view, text) : null);

    return true;
  }

  @Override
  public boolean equals (TextEntity bRaw, int compareMode, @Nullable String originalText) {
    TextEntityCustom b = (TextEntityCustom) bRaw;
    if (isClickable() != b.isClickable()) {
      return false;
    }
    if (isClickable() && !(
      b.linkType == linkType &&
      b.linkLength == linkLength &&
      b.linkOffset == linkOffset &&
      StringUtils.equalsOrBothEmpty(b.link, link) &&
      b.onClickListener == onClickListener
    )) {
      return false;
    }
    if (compareMode != COMPARE_MODE_CLICK_HIGHLIGHT && !(
      this.flags == b.flags &&
      this.customColorSet == b.customColorSet &&
      Td.equalsTo(icon, b.icon) && Td.equalsTo(emoji, b.emoji) && Td.equalsTo(mathematicalExpression, b.mathematicalExpression) && Td.equalsTo(button, b.button)
    )) {
      return false;
    }
    return true;
  }

}
