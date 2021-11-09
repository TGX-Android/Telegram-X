package org.thunderdog.challegram.util.text;

import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.unit.BitwiseUtils;

/**
 * Date: 23/02/2017
 * Author: default
 */

public class TextEntityCustom extends TextEntity {
  public static final int FLAG_BOLD = 1;
  public static final int FLAG_ITALIC = 1 << 1;
  public static final int FLAG_UNDERLINE = 1 << 2;
  public static final int FLAG_MONOSPACE = 1 << 3;
  public static final int FLAG_STRIKETHROUGH = 1 << 4;
  public static final int FLAG_SUBSCRIPT = 1 << 5;
  public static final int FLAG_SUPERSCRIPT = 1 << 6;
  public static final int FLAG_MARKED = 1 << 7;
  public static final int FLAG_CLICKABLE = 1 << 10;
  public static final int FLAG_ANCHOR = 1 << 11;

  public static final int LINK_TYPE_NONE = 0;
  public static final int LINK_TYPE_EMAIL = 1;
  public static final int LINK_TYPE_URL = 2;
  public static final int LINK_TYPE_PHONE_NUMBER = 3;
  public static final int LINK_TYPE_ANCHOR = 4;
  public static final int LINK_TYPE_REFERENCE = 5;

  private final ViewController<?> context; // TODO move to TextEntity

  private final int flags;

  private int linkOffset = -1;
  private int[] linkLength;
  private int linkType;
  private String link;
  private boolean linkCached;

  private TextColorSet customColorSet;

  private ClickableSpan onClickListener;
  private String anchorName;
  private String referenceAnchorName;
  private TextIcon icon;
  private String copyLink;

  public TextEntityCustom (@Nullable ViewController<?> context, @Nullable Tdlib tdlib, String in, int offset, int end, int flags, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(tdlib, offset, end, (flags & FLAG_BOLD) != 0 && Text.needFakeBold(in), openParameters);
    this.context = context;
    this.flags = flags;
  }

  public TextEntityCustom setIcon (TdApi.RichTextIcon icon) {
    ImageFile miniThumbnail;
    if (icon.document.minithumbnail != null) {
      miniThumbnail = new ImageFileLocal(icon.document.minithumbnail);
      miniThumbnail.setScaleType(ImageFile.FIT_CENTER);
    } else {
      miniThumbnail = null;
    }

    ImageFile thumbnail = TD.toImageFile(tdlib, icon.document.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(Screen.dp(Math.max(icon.width, icon.height)));
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
    }
    GifFile gifFile = null;
    ImageFile imageFile = null;

    if ("video/mp4".equals(icon.document.mimeType)) {
      gifFile = new GifFile(tdlib, icon.document.document, GifFile.TYPE_MPEG4);
      gifFile.setScaleType(GifFile.FIT_CENTER);
    } else if ("image/gif".equals(icon.document.mimeType)) {
      gifFile = new GifFile(tdlib, icon.document.document, GifFile.TYPE_GIF);
      gifFile.setScaleType(GifFile.FIT_CENTER);
    } else {
      imageFile = new ImageFile(tdlib, icon.document.document);
      imageFile.setSize(Screen.dp(Math.max(icon.width, icon.height)));
    }

    this.icon = gifFile != null ? new TextIcon(icon.width, icon.height, miniThumbnail, thumbnail, gifFile) : new TextIcon(icon.width, icon.height, miniThumbnail, thumbnail, imageFile);

    return this;
  }

  public TextEntityCustom setAnchorName (String anchorName) {
    this.anchorName = anchorName;
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

  public TextEntityCustom setCustomColorSet (TextColorSet colorSet) {
    this.customColorSet = colorSet;
    return this;
  }

  public TextEntityCustom setOnClickListener (ClickableSpan span) {
    this.onClickListener = span;
    return this;
  }

  private TextColorSet cachedLinkSet, lastDefaultColorSet;

  @Override
  public TextColorSet getSpecialColorSet (@NonNull TextColorSet defaultColorSet) {
    TextColorSet colorSet;
    if (customColorSet != null) {
      colorSet = customColorSet;
    } else if (linkType == LINK_TYPE_REFERENCE) {
      colorSet = TextColorSets.InstantView.REFERENCE;
    } else if (BitwiseUtils.getFlag(flags, FLAG_MARKED)) {
      colorSet = TextColorSets.InstantView.Marked.NORMAL;
    } else if (BitwiseUtils.getFlag(flags, FLAG_MONOSPACE)) {
      colorSet = TextColorSets.InstantView.Monospace.NORMAL;
    } else {
      colorSet = null;
    }
    if (linkCached) {
      int backgroundColorId = (colorSet != null ? colorSet : defaultColorSet).backgroundColorId(false);
      if (backgroundColorId == 0) {
        if (cachedLinkSet == null || lastDefaultColorSet != defaultColorSet) {
          lastDefaultColorSet = defaultColorSet;
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
    return BitwiseUtils.getFlag(flags, FLAG_SUPERSCRIPT) || BitwiseUtils.getFlag(flags, FLAG_SUBSCRIPT);
  }

  @Override
  public boolean isIcon () {
    return icon != null;
  }

  @Override
  public TextIcon getIcon () {
    return icon;
  }

  @Override
  public float getBaselineShift () {
    float baselineShift;
    if (BitwiseUtils.getFlag(flags, FLAG_SUPERSCRIPT) && BitwiseUtils.getFlag(flags, FLAG_SUBSCRIPT)) {
      baselineShift = 0f;
    } else if (BitwiseUtils.getFlag(flags, FLAG_SUPERSCRIPT)) {
      baselineShift = .4f;
    } else if (BitwiseUtils.getFlag(flags, FLAG_SUBSCRIPT)) {
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
  public boolean isEssential () {
    return true;
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
    return !StringUtils.isEmpty(this.anchorName) && this.anchorName.equals(anchor);
  }

  @Override
  public boolean isFullWidth () {
    return false;
  }

  @Override
  public void performClick (View view, Text text, TextPart part, @Nullable Text.ClickCallback callback) {
    switch (linkType) {
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
        TdlibUi.UrlOpenParameters openParameters = this.openParameters(view, text, part);
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
        if (callback == null || !(callback.onReferenceClick(view, link, referenceAnchorName, this.openParameters(view, text, part))) || callback.onAnchorClick(view, link)) {
          // TODO open pop-up with ${referenceText}?
        }
        break;
      }
    }
  }

  @Override
  public boolean performLongPress (final View view, final Text text, final TextPart part, boolean allowShare, Text.ClickCallback clickCallback) {
    final ViewController<?> context = findRoot(view);
    if (context == null) {
      Log.v("performLongPress ignored, because ancestor not found");
      return false;
    }
    if (StringUtils.isEmpty(copyLink)) {
      if (linkType == LINK_TYPE_NONE || StringUtils.isEmpty(link) || ((linkType == LINK_TYPE_ANCHOR || linkType == LINK_TYPE_REFERENCE) && (openParameters == null || StringUtils.isEmpty(openParameters.refererUrl)))) {
        if (isMonospace()) {
          String content = text.getText().substring(getStart(), getEnd());
          context.showOptions(content, new int[] {R.id.btn_copyText}, new String[] {Lang.getString(R.string.Copy)}, null, new int[] {R.drawable.baseline_content_copy_24}, (itemView, id) -> {
            if (id == R.id.btn_copyText) {
              UI.copyText(content, R.string.CopiedText);
            }
            return true;
          });
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
      switch (id) {
        case R.id.btn_copyLink: {
          UI.copyText(copyText, R.string.CopiedLink);
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
          performClick(view, text, part, clickCallback);
          break;
        }
      }
      return true;
    }, clickCallback != null ? clickCallback.getForcedTheme(view, text) : null);

    return true;
  }

  @Override
  public boolean equals (TextEntity bRaw, boolean forPressHighlight) {
    TextEntityCustom b = (TextEntityCustom) bRaw;
    return
      b.isClickable() == isClickable() &&
        (!isClickable() || (b.linkType == linkType && b.linkLength == linkLength && b.linkOffset == linkOffset && StringUtils.equalsOrBothEmpty(b.link, link))) &&
        (forPressHighlight || (this.flags == b.flags && this.customColorSet == b.customColorSet));
  }

}
