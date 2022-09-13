/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/02/2016 at 13:30
 */
package org.thunderdog.challegram.component.chat;

import android.app.AlertDialog;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.inputmethod.InputConnectionCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ComplexMediaHolder;
import org.thunderdog.challegram.data.ComplexMediaItem;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.data.InlineResultEmojiSuggestion;
import org.thunderdog.challegram.data.InlineResultHashtag;
import org.thunderdog.challegram.data.InlineResultMention;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.emoji.CustomEmojiSurfaceProvider;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.LocaleChanger;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.ExternalEmojiFilter;
import org.thunderdog.challegram.util.FinalNewLineFilter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.InputWrapperWrapper;
import org.thunderdog.challegram.widget.NoClipEditText;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class InputView extends NoClipEditText implements InlineSearchContext.Callback, InlineResultsWrap.PickListener, RtlCheckListener, FinalNewLineFilter.Callback, CustomEmojiSurfaceProvider, Destroyable {
  public static final boolean USE_ANDROID_SELECTION_FIX = true;
  private final TextPaint paint;

  // TODO: get rid of chat-related logic inside of InputView
  private @Nullable MessagesController controller;
  private boolean ignoreDraft;

  private String suffix = "", prefix = "", displaySuffix = "";
  private int prefixWidth, suffixWidth;

  private final Tdlib tdlib;
  private final InlineSearchContext inlineContext;

  public interface InputListener {
    boolean canSearchInline (InputView v);
    void onInputChanged (InputView v, String input);
    long provideInlineSearchChatId (InputView v);
    TdApi.Chat provideInlineSearchChat (InputView v);
    long provideInlineSearchChatUserId (InputView v);
    void showInlineResults (InputView v, ArrayList<InlineResult<?>> items, boolean isContent);
    void addInlineResults (InputView v, ArrayList<InlineResult<?>> items);
  }

  private InputListener inputListener;

  public InputView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.mediaHolder = new ComplexMediaHolder<>(this);
    this.mediaHolder.setUpdateListener((usages, displayMediaKey) ->
      scheduleCustomEmojiInvalidate()
    );
    this.inlineContext = new InlineSearchContext(UI.getContext(context), tdlib, this);
    this.paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    this.paint.setColor(Theme.textPlaceholderColor());
    this.paint.setTypeface(Fonts.getRobotoRegular());
    this.paint.setTextSize(Screen.sp(18f));
    setGravity(Lang.gravity() | Gravity.TOP);
    setTypeface(Fonts.getRobotoRegular());
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
    final int verticalPadding = Screen.dp(12f);
    if (Lang.rtl()) {
      setPadding(Screen.dp(55f), verticalPadding, Screen.dp(60f), verticalPadding);
    } else {
      setPadding(Screen.dp(60f), verticalPadding, Screen.dp(55f), verticalPadding);
    }
    setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    setInputType(getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    setSingleLine(false);
    setMaxLines(8);
    setMinimumHeight(Screen.dp(49f));
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    Views.clearCursorDrawable(this);
    setMaxCodePointCount(0);
    addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

      @Override
      public void onTextChanged (CharSequence s, int start, int before, int count) {
        processTextChange(s, !isSettingText || settingByUserAction);
      }

      @Override
      public void afterTextChanged (Editable s) { }
    });

    if (Config.USE_CUSTOM_INPUT_STYLING) {
      setCustomSelectionActionModeCallback(new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode (ActionMode mode, Menu menu) {
          MenuInflater inflater = mode.getMenuInflater();
          if (inflater == null) {
            return true;
          }
          inflater.inflate(R.menu.text, menu);
          try {
            for (int i = 0; i < menu.size(); i++) {
              MenuItem item = menu.getItem(i);
              int overrideResId;
              TdApi.TextEntityType type;
              switch (item.getItemId()) {
                case R.id.btn_bold: {
                  overrideResId = R.string.TextFormatBold;
                  type = new TdApi.TextEntityTypeBold();
                  break;
                }
                case R.id.btn_italic: {
                  overrideResId = R.string.TextFormatItalic;
                  type = new TdApi.TextEntityTypeItalic();
                  break;
                }
                case R.id.btn_spoiler: {
                  overrideResId = R.string.TextFormatSpoiler;
                  type = new TdApi.TextEntityTypeSpoiler();
                  break;
                }
                case R.id.btn_underline: {
                  overrideResId = R.string.TextFormatUnderline;
                  type = new TdApi.TextEntityTypeUnderline();
                  break;
                }
                case R.id.btn_strikethrough: {
                  overrideResId = R.string.TextFormatStrikethrough;
                  type = new TdApi.TextEntityTypeStrikethrough();
                  break;
                }
                case R.id.btn_monospace: {
                  overrideResId = R.string.TextFormatMonospace;
                  type = new TdApi.TextEntityTypeCode();
                  break;
                }
                case R.id.btn_link: {
                  overrideResId = R.string.TextFormatLink;
                  type = null;
                  break;
                }
                default: {
                  if (BuildConfig.DEBUG) {
                    Log.i("Menu item: %s %s",  UI.getAppContext().getResources().getResourceName(item.getItemId()), item.getTitle());
                  }
                  continue;
                }
              }
              item.setTitle(type != null ? Lang.wrap(Lang.getString(overrideResId), Lang.entityCreator(type)) : Lang.getString(overrideResId));
            }
            final int start = getSelectionStart();
            final int end = getSelectionEnd();
            final String str = getText().toString();
            if (Text.needFakeBoldFull(str, start, end)) {
              menu.removeItem(R.id.btn_monospace);
            }
          } catch (Throwable ignored) { }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            menu.removeItem(android.R.id.shareText);
          }
          return true;
        }

        @Override
        public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
          return false;
        }

        @Override
        public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
          return setSpan(item.getItemId());
        }

        @Override
        public void onDestroyActionMode (ActionMode mode) {

        }
      });
    }
  }

  private boolean isScheduled;

  private void scheduleCustomEmojiInvalidate () {
    if (!isScheduled) {
      invalidateCustomEmoji();
      postDelayed(() -> {
        invalidateCustomEmoji();
        isScheduled = false;
      }, (long) (1000.0f / Math.min(Screen.refreshRate(), 60.0f)));
    }
  }

  private void invalidateCustomEmoji () {
    invalidate();
  }

  @Override
  public EmojiSpan onCreateNewSpan (CharSequence emojiCode, EmojiInfo info, long customEmojiId) {
    return Emoji.instance().newCustomSpan(emojiCode, info, this, tdlib, customEmojiId);
  }

  @Override
  public void onInvalidateSpan (EmojiSpan span) {
    invalidate();
  }

  private final ComplexMediaHolder<EmojiSpan> mediaHolder;

  @Override
  public ComplexReceiver provideComplexReceiverForSpan (EmojiSpan span) {
    return mediaHolder.receiver;
  }

  @Override
  public int getDuplicateMediaItemCount (EmojiSpan span, ComplexMediaItem mediaItem) {
    return mediaHolder.getMediaUsageCount(mediaItem);
  }

  @Override
  public long attachToReceivers (EmojiSpan span, ComplexMediaItem mediaItem) {
    return mediaHolder.attachMediaUsage(mediaItem, span);
  }

  @Override
  public void detachFromReceivers (EmojiSpan span, ComplexMediaItem mediaItem, long mediaKey) {
    mediaHolder.detachMediaUsage(mediaItem, span, mediaKey);
  }

  @Override
  public void performDestroy () {
    super.performDestroy();
    mediaHolder.performDestroy();
  }

  public boolean setSpan (int id) {
    final int selectionStart = getSelectionStart();
    final int selectionEnd = getSelectionEnd();
    if (selectionStart == selectionEnd) {
      return false;
    }
    TdApi.TextEntityType type;
    switch (id) {
      case R.id.btn_bold:
        type = new TdApi.TextEntityTypeBold();
        break;
      case R.id.btn_italic:
        type = new TdApi.TextEntityTypeItalic();
        break;
      case R.id.btn_spoiler:
        type = new TdApi.TextEntityTypeSpoiler();
        break;
      case R.id.btn_strikethrough:
        type = new TdApi.TextEntityTypeStrikethrough();
        break;
      case R.id.btn_underline:
        type = new TdApi.TextEntityTypeUnderline();
        break;
      case R.id.btn_monospace:
        type = new TdApi.TextEntityTypeCode();
        break;
      case R.id.btn_link: {
        URLSpan[] existingSpans = getText().getSpans(selectionStart, selectionEnd, URLSpan.class);
        URLSpan existingSpan = existingSpans != null && existingSpans.length > 0 ? existingSpans[0] : null;
        createTextUrl(existingSpan, selectionStart, selectionEnd);
        return true;
      }
      default: {
        return false;
      }
    }
    setSpan(selectionStart, selectionEnd, type);
    return true;
  }

  private void setSpan (int start, int end, TdApi.TextEntityType type) {
    CharacterStyle span = TD.toSpan(type);
    if (span == null)
      return;
    boolean canBeNested = true;
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        canBeNested = false;
        break;
    }
    boolean addSpan = true;
    CharacterStyle[] existingSpans = getText().getSpans(start, end, CharacterStyle.class);
    if (existingSpans != null) {
      for (CharacterStyle existingSpan : existingSpans) {
        TdApi.TextEntityType[] existingTypes = TD.toEntityType(existingSpan);
        if (existingTypes == null || existingTypes.length == 0)
          continue;
        for (TdApi.TextEntityType existingType : existingTypes) {
          if (Td.equalsTo(existingType, type)) {
            int existingSpanStart = getText().getSpanStart(existingSpan);
            int existingSpanEnd = getText().getSpanEnd(existingSpan);
            SpannableStringBuilder sb = new SpannableStringBuilder(getText());
            sb.removeSpan(existingSpan);
            // Check start
            if (existingSpanStart < start) {
              for (TdApi.TextEntityType copyType : existingTypes) {
                sb.setSpan(TD.toSpan(copyType), existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              }
            }

            // Check end
            if (existingSpanEnd > end) {
              for (TdApi.TextEntityType copyType : existingTypes) {
                sb.setSpan(TD.toSpan(copyType), end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              }
            }

            setText(SpannableString.valueOf(sb));
            addSpan = false;
            break;
          }
        }
      }
    }
    if (addSpan) {
      if (existingSpans != null && existingSpans.length > 0) {
        SpannableStringBuilder sb = null;

        for (CharacterStyle existingSpan : existingSpans) {
          TdApi.TextEntityType[] existingTypes = TD.toEntityType(existingSpan);
          if (existingTypes == null || existingTypes.length == 0)
            continue;
          int existingSpanStart = getText().getSpanStart(existingSpan);
          int existingSpanEnd = getText().getSpanEnd(existingSpan);
          boolean removeSpan = !canBeNested;
          if (!removeSpan) {
            for (TdApi.TextEntityType existingType : existingTypes) {
              switch (existingType.getConstructor()) {
                case TdApi.TextEntityTypeCode.CONSTRUCTOR:
                case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
                case TdApi.TextEntityTypePre.CONSTRUCTOR:
                  removeSpan = true;
                  break;
                case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
                  removeSpan = type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR;
                  break;
              }
              if (removeSpan)
                break;
            }
          }
          if (removeSpan) {
            if (sb == null)
              sb = new SpannableStringBuilder(getText());
            sb.removeSpan(existingSpan);
            if (existingSpanStart < start || existingSpanEnd > end) {
              for (TdApi.TextEntityType copyType : existingTypes) {
                if (existingSpanStart < start)
                  sb.setSpan(TD.toSpan(copyType), existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (existingSpanEnd > end)
                  sb.setSpan(TD.toSpan(copyType), end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              }
            }
          }
        }
        if (sb != null)
          setText(SpannableString.valueOf(sb));
      }

      getText().setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    Views.setSelection(this, end);
    inlineContext.forceCheck();
    if (spanChangeListener != null) {
      spanChangeListener.onSpansChanged(this);
    }
  }

  private SpanChangeListener spanChangeListener;

  public void setSpanChangeListener (SpanChangeListener listener) {
    this.spanChangeListener = listener;
  }

  public interface SpanChangeListener {
    void onSpansChanged (InputView view);
  }

  public void createTextUrl (URLSpan existingSpan, int start, int end) {
    if (start < 0 || end < 0 || start > getText().length() || end > getText().length()) {
      return;
    }
    ViewController<?> c = controller;
    if (c == null && inputListener instanceof ViewController<?>) {
      c = (ViewController<?>) inputListener;
    }
    if (c != null) {
      String existingUrl = existingSpan != null ? existingSpan.getURL() : null;
      c.openInputAlert(Lang.getString(R.string.CreateLink), Lang.getString(R.string.URL), R.string.CreateLinkDone, R.string.CreateLinkCancel, existingUrl, (inputView, result) -> {
        if (StringUtils.isEmpty(result)) {
          if (existingSpan != null) {
            getText().removeSpan(existingSpan);
            inlineContext.forceCheck();
          }
          return true;
        } else if (Strings.isValidLink(result)) {
          setSpan(start, end, new TdApi.TextEntityTypeTextUrl(result));
          return true;
        } else {
          return false;
        }
      }, false);
    }
  }

  public void restartTextChange () {
    CharSequence cs = getText();
    String str = cs.toString();
    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    inlineContext.onTextChanged(cs, str, selectionStart == selectionEnd ? selectionStart : -1);
  }

  @Override
  protected void onSelectionChanged (int selStart, int selEnd) {
    super.onSelectionChanged(selStart, selEnd);
    if (inlineContext != null) {
      inlineContext.onCursorPositionChanged(selStart == selEnd ? selStart : -1);
    }
  }

  public boolean canFormatText () {
    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    return selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= getText().length();
  }

  private boolean allowsAnyGravity;
  private void setAllowsAnyGravity (boolean allows) {
    if (this.allowsAnyGravity != allows) {
      this.allowsAnyGravity = allows;
      setGravity(allows ? Gravity.TOP : Lang.gravity() | Gravity.TOP);
    }
  }

  private void processTextChange (CharSequence s, boolean byUserAction) {
    String str = s.toString();

    if (byUserAction && blockedText == null) {
      setTextChangedSinceChatOpened(true);
    }

    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    if (controller != null) {
      inlineContext.onTextChanged(s, str, selectionStart == selectionEnd ? selectionStart : -1);
      controller.onInputTextChange(s, !ignoreDraft && byUserAction);
    } else if (inputListener != null) {
      if (inputListener.canSearchInline(InputView.this)) {
        inlineContext.onTextChanged(s, str, selectionStart == selectionEnd ? selectionStart : -1);
      }
      inputListener.onInputChanged(InputView.this, str);
    }
    if (!isSettingText && blockedText != null && !blockedText.equals(str)) {
      setInput(blockedText, true, false);
    }

    setAllowsAnyGravity(str.length() > 0);
  }

  @Override
  public void checkRtl () {
    if (!allowsAnyGravity) {
      setGravity(Lang.gravity() | Gravity.TOP);
    }
  }

  // ETc

  private int lastPlaceholderRes;
  private Object[] lastPlaceholderArgs;
  private CharSequence rawPlaceholder;
  private float rawPlaceholderWidth;
  private int lastPlaceholderAvailWidth;

  public void setInputPlaceholder (@StringRes int resId, Object... args) {
    String placeholder = Lang.getString(resId, args);
    this.lastPlaceholderRes = resId;
    this.lastPlaceholderArgs = args;
    if (StringUtils.equalsOrBothEmpty(placeholder, this.rawPlaceholder)) {
      return;
    }
    this.rawPlaceholder = placeholder;
    if (controller == null) {
      setHint(placeholder);
    } else {
      this.rawPlaceholderWidth = U.measureText(rawPlaceholder, getPaint());
      this.lastPlaceholderAvailWidth = 0;
      checkPlaceholderWidth();
    }
  }

  public void checkPlaceholderWidth () {
    if (lastPlaceholderRes != 0 && controller != null) {
      int availWidth = Math.max(0, getMeasuredWidth() - controller.getHorizontalInputPadding() - getPaddingLeft());
      if (this.lastPlaceholderAvailWidth != availWidth) {
        this.lastPlaceholderAvailWidth = availWidth;
        if (rawPlaceholderWidth <= availWidth) {
          setHint(rawPlaceholder);
        } else {
          setHint(TextUtils.ellipsize(rawPlaceholder, getPaint(), availWidth, TextUtils.TruncateAt.END));
        }
      }
    }
  }

  private boolean needSendByEnter () {
    return Settings.instance().needSendByEnter() && !isSettingText && controller != null && controller.inSimpleSendMode();
  }

  private void sendByEnter () {
    if (controller != null && needSendByEnter()) {
      controller.pickDateOrProceed(false, null, (forceDisableNotification, schedulingState, disableMarkdown) -> controller.sendText(true, forceDisableNotification, schedulingState));
    }
  }

  public void setInputListener (InputListener inputListener) {
    this.inputListener = inputListener;
  }

  public Paint getPlaceholderPaint () {
    return paint;
  }

  // Inline results

  public InlineSearchContext getInlineSearchContext () {
    return inlineContext;
  }

  @Override
  public long provideInlineSearchChatId () {
    return controller != null ? controller.getChatId() : inputListener != null && inputListener.canSearchInline(this) ? inputListener.provideInlineSearchChatId(this) : 0;
  }

  @Override
  public TdApi.WebPage provideExistingWebPage (TdApi.FormattedText currentText) {
    return controller != null ? controller.getEditingWebPage(currentText) : null;
  }

  private boolean isCaptionEditing () {
    return controller == null || controller.isEditingCaption();
  }

  @Override
  public boolean needsLinkPreview () {
    return !isCaptionEditing() && tdlib.canAddWebPagePreviews(controller.getChat());
  }

  @Override
  public boolean needsInlineBots () {
    return !isCaptionEditing() && tdlib.canSendOtherMessages(controller.getChat());
  }

  @Override
  public TdApi.Chat provideInlineSearchChat () {
    if (controller != null) {
      return controller.getChat();
    } else if (inputListener != null && inputListener.canSearchInline(this)) {
      return inputListener.provideInlineSearchChat(this);
    }
    return null;
  }

  @Override
  public long provideInlineSearchChatUserId () {
    return controller != null ? controller.getChatUserId() : inputListener != null && inputListener.canSearchInline(this) ? inputListener.provideInlineSearchChatUserId(this) : 0;
  }

  @Override
  public void showInlinePlaceholder (@NonNull String username, @NonNull String placeholder) {
    setSuffix(username.isEmpty() ? username : "@" + username + " ", placeholder);
  }

  @Override
  public void updateInlineMode (boolean isInInlineMode, boolean isInProgress) {
    if (controller != null) {
      controller.getSendButton().setInInlineMode(isInInlineMode, isInProgress);
    }
  }

  @Override
  public void showInlineStickers (ArrayList<TGStickerObj> stickers, boolean isMore) {
    if (controller != null) {
      controller.showStickerSuggestions(stickers, isMore);
    }
  }

  @Override
  public void showInlineResults (ArrayList<InlineResult<?>> items, boolean isContent) {
    if (inputListener != null && inputListener.canSearchInline(this)) {
      inputListener.showInlineResults(this, items, isContent);
    } else {
      ((BaseActivity) getContext()).showInlineResults(controller, tdlib, items, isContent, inlineContext);
    }
  }

  @Override
  public void addInlineResults (ArrayList<InlineResult<?>> items) {
    if (inputListener != null && inputListener.canSearchInline(this)) {
      inputListener.addInlineResults(this, items);
    } else {
      ((BaseActivity) getContext()).addInlineResults(controller, items, inlineContext);
    }
  }

  @Override
  public boolean isDisplayingItems () {
    return inputListener == null && ((BaseActivity) getContext()).areInlineResultsVisible();
  }

  @Override
  public void hideInlineResults () {
    if (controller != null) {
      controller.hideStickerSuggestions();
    }
    if (inputListener != null && inputListener.canSearchInline(this)) {
      inputListener.showInlineResults(this, null, false);
    } else {
      ((BaseActivity) getContext()).showInlineResults(controller, tdlib, null, false, null);
    }
  }

  @Override
  public boolean showLinkPreview (@Nullable String link, @Nullable TdApi.WebPage webPage) {
    if (controller == null) {
      return false;
    }
    if (ignoreFirstLinkPreview) {
      ignoreFirstLinkPreview = false;
      controller.ignoreLinkPreview(link, webPage);
      return false;
    } else {
      controller.showLinkPreview(link, webPage);
      return true;
    }
  }

  private AlertDialog linkWarningDialog;

  @Override
  public int showLinkPreviewWarning (final int contextId, @Nullable final String link) {
    if (controller == null || !controller.isSecretChat()) {
      return InlineSearchContext.WARNING_OK;
    }
    if (Settings.instance().needTutorial(Settings.TUTORIAL_SECRET_LINK_PREVIEWS)) {
      if (linkWarningDialog == null || !linkWarningDialog.isShowing()) {
        AlertDialog.Builder b = new AlertDialog.Builder(controller.context(), Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.AppName));
        b.setMessage(Lang.getString(R.string.SecretLinkPreviewAlert));
        b.setPositiveButton(Lang.getString(R.string.SecretLinkPreviewEnable), (dialog, which) -> {
          linkWarningDialog = null;
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_SECRET_LINK_PREVIEWS);
          Settings.instance().setUseSecretLinkPreviews(true);
          inlineContext.forceCheck();
        });
        b.setNegativeButton(Lang.getString(R.string.SecretLinkPreviewDisable), (dialog, which) -> {
          linkWarningDialog = null;
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_SECRET_LINK_PREVIEWS);
          Settings.instance().setUseSecretLinkPreviews(false);
          inlineContext.forceCheck();
        });
        b.setCancelable(false);
        linkWarningDialog = controller.showAlert(b);
      }
      return InlineSearchContext.WARNING_CONFIRM;
    }
    return Settings.instance().needSecretLinkPreviews() ? InlineSearchContext.WARNING_OK : InlineSearchContext.WARNING_BLOCK;
  }

  public void setIsInEditMessageMode (boolean isInEditMessageMode, String futureText) {
    this.inlineContext.setDisallowInlineResults(isInEditMessageMode, getText().toString().equals(futureText));
  }

  public void resetState () {
    inlineContext.setDisallowInlineResults(false, false);
  }

  public void setCommandListProvider (@Nullable InlineSearchContext.CommandListProvider provider) {
    this.inlineContext.setCommandListProvider(provider);
  }

  // Other

  private @Nullable String blockedText;

  public void setInputBlocked (boolean isBlocked) {
    this.blockedText = isBlocked ? getInput() : null;
  }

  public LocaleChanger setController (MessagesController controller) {
    this.controller = controller;
    return new LocaleChanger(this, new LocaleChanger.CustomCallback() {
      @Override
      public void onLocaleChange (int arg1) {
        if (lastPlaceholderRes != 0) {
          setInputPlaceholder(lastPlaceholderRes, lastPlaceholderArgs);
        }
      }

      @Override
      public int provideCurrentStringResource () {
        return lastPlaceholderRes;
      }
    });
  }

  public void onEmojiSelected (String emoji) {
    int start = getSelectionStart();
    int end = getSelectionEnd();
    int after = start + emoji.length();
    SpannableString s = new SpannableString(emoji);
    s.setSpan(Emoji.instance().newSpan(emoji, null), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (start == end)
      getText().insert(start, s);
    else
      getText().replace(start, end, s);
    Views.setSelection(this, after);
  }

  private boolean textChangedSinceChatOpened, ignoreFirstLinkPreview;

  public void setChat (TdApi.Chat chat, @Nullable ThreadInfo messageThread, boolean isSilent) {
    textChangedSinceChatOpened = false;
    updateMessageHint(chat, messageThread, isSilent);
    setDraft(!tdlib.hasWritePermission(chat) ? null :
      messageThread != null ? messageThread.getDraftContent() :
      chat.draftMessage != null ? chat.draftMessage.inputMessageText : null
    );
  }

  public boolean isEmpty () {
    return getInput().trim().isEmpty();
  }

  public boolean textChangedSinceChatOpened () {
    return textChangedSinceChatOpened;
  }

  public void setTextChangedSinceChatOpened (boolean force) {
    if (controller != null && !controller.isEditingMessage() && (!isEmpty() || force)) {
      textChangedSinceChatOpened = true;
    }
  }

  public void updateMessageHint (TdApi.Chat chat, @Nullable ThreadInfo messageThread, boolean isSilent) {
    if (chat == null) {
      setInputPlaceholder(R.string.Message);
      return;
    }
    int resource;
    Object[] args = null;
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    if (tdlib.isChannel(chat.id)) {
      resource = isSilent ? R.string.ChannelSilentBroadcast : R.string.ChannelBroadcast;
    } else if (tdlib.isMultiChat(chat) && Td.isAnonymous(status)) {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.CommentAnonymously : R.string.MessageReplyAnonymously) :  R.string.MessageAnonymously;
    } else if (chat.messageSenderId != null && !tdlib.isSelfSender(chat.messageSenderId)) {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.CommentAsX : R.string.MessageReplyAsX) : R.string.MessageAsX;
      args = new Object[] { tdlib.senderName(chat.messageSenderId) };
    } else {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.Comment : R.string.MessageReply) : R.string.Message;
    }
    setInputPlaceholder(resource, args);
  }

  public void setDraft (@Nullable TdApi.InputMessageContent draftContent) {
    CharSequence draft;
    if (draftContent != null && draftContent.getConstructor() == TdApi.InputMessageText.CONSTRUCTOR) {
      TdApi.InputMessageText textDraft = (TdApi.InputMessageText) draftContent;
      draft = TD.toCharSequence(textDraft.text);
      ignoreFirstLinkPreview = textDraft.disableWebPagePreview;
    } else {
      draft = "";
      ignoreFirstLinkPreview = false;
    }
    String current = getInput().trim();
    controller.setInputVisible(true, current.length() > 0);
    if (!draft.equals(current)) {
      ignoreDraft = true;
      setInput(draft, draft.length() > 0, false);
      controller.updateSendButton(draft.toString(), false);
    }
  }

  // Suffix stuff

  private boolean isSettingText, settingByUserAction;

  public void setInput (CharSequence text, boolean moveCursor, boolean byUserAction) {
    this.isSettingText = true;
    this.settingByUserAction = byUserAction;
    setText(text);
    if (moveCursor) {
      Views.setSelection(this, text != null ? text.length() : 0);
    }
    this.isSettingText = false;
  }

  public String getInput () {
    String input = getText().toString();
    return suffix.length() == 0 || prefix.length() == 0 ? input : input.endsWith(suffix) ? input.substring(0, input.lastIndexOf(suffix)) : input;
  }

  private void checkPrefix (String text) {
    String actualPrefix = text.substring(0, prefix.length());
    if (!actualPrefix.equals(prefix)) {
      this.prefix = actualPrefix;
      this.prefixWidth = prefix.length() > 0 ? (int) U.measureText(this.prefix, paint) : 0;
      layoutSuffix();
    }
  }

  private void setSuffix (String prefix, String suffix) {
    if (!this.suffix.equals(suffix) || !this.prefix.equals(prefix)) {
      this.suffix = suffix;
      this.prefix = prefix;
      this.prefixWidth = prefix.length() > 0 ? (int) U.measureText(this.prefix, paint) : 0;
      this.suffixWidth = suffix.length() > 0 ? (int) U.measureText(this.suffix, paint) : 0;
      layoutSuffix();
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    checkPlaceholderWidth();
    layoutSuffix();
  }

  private void layoutSuffix () {
    displaySuffix = suffix;
    if (suffixWidth > 0) {
      int maxWidth = getMeasuredWidth() - prefixWidth - Screen.dp(110f);
      if (suffixWidth > maxWidth) {
        displaySuffix = (String) TextUtils.ellipsize(suffix, paint, maxWidth, TextUtils.TruncateAt.END);
      }
    }
  }

  private void drawEmojiOverlay (Canvas c) {
    Layout layout = getLayout();
    for (EmojiSpan span : mediaHolder.defaultLayerUsages()) {
      span.onOverlayDraw(c, this, layout);
    }
    for (EmojiSpan span : mediaHolder.topLayerUsages()) {
      span.onOverlayDraw(c, this, layout);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);
    drawEmojiOverlay(c);
    if (this.displaySuffix.length() > 0 && this.prefix.length() > 0 && getLineCount() == 1) {
      String text = getText().toString();
      if (text.equalsIgnoreCase(prefix)) {
        checkPrefix(text);
        c.drawText(displaySuffix, getPaddingLeft() + prefixWidth, getBaseline(), paint);
      }
    }
  }

  // Inline query

  @Override
  public void onCommandPick (InlineResultCommand command, boolean isLongPress) {
    if (controller != null) {
      TdApi.Chat chat = controller.getChat();
      if (isLongPress) {
        String str = (chat != null && ChatId.isUserChat(ChatId.toUserId(chat.id))) || command.getUsername() == null ? command.getCommand() + ' ' : command.getCommand() + '@' + command.getUsername() + ' ';
        setInput(str, true, true);
      } else {
        controller.sendCommand(command);
      }
    }
  }

  @Override
  public void onHashtagPick (InlineResultHashtag hashtag) {
    String resultHashtag = hashtag.data();

    if (resultHashtag == null || !hashtag.hasTarget()) {
      return;
    }

    Editable editable = getText();
    SpannableStringBuilder b = new SpannableStringBuilder(editable);
    CharSequence within = resultHashtag + " ";
    try { editable.replace(hashtag.getTargetStart(), hashtag.getTargetEnd(), within); } catch (Throwable ignored) {  }
    try { setInput(hashtag.replaceInTarget(b, within), false, true); Views.setSelection(this, hashtag.getTargetStart() + within.length()); } catch (Throwable ignored) { }
  }

  @Override
  public void onEmojiSuggestionPick (InlineResultEmojiSuggestion suggestion) {
    String resultEmoji = suggestion.getEmoji();
    if (resultEmoji == null || !suggestion.hasTarget()) {
      return;
    }

    Editable editable = getText();
    SpannableStringBuilder b = new SpannableStringBuilder(editable);
    CharSequence within = editable.length() == suggestion.getTargetEnd() && suggestion.getTargetStart() == 0 ? resultEmoji : resultEmoji + " ";
    try { editable.replace(suggestion.getTargetStart(), suggestion.getTargetEnd(), within); } catch (Throwable ignored) { }
    try { setInput(suggestion.replaceInTarget(b, within), false, true); Views.setSelection(this, suggestion.getTargetStart() + within.length()); } catch (Throwable ignored) { }
  }

  @Override
  public void onMentionPick (InlineResultMention mention, @Nullable String usernamelessText) {
    boolean isUsernameless = !StringUtils.isEmpty(usernamelessText) || mention.isUsernameless();
    String resultMention = !StringUtils.isEmpty(usernamelessText) ? usernamelessText : mention.getMention(false);
    if (resultMention == null || mention.getTargetEnd() == -1 || mention.getTargetStart() == -1) {
      return;
    }

    Editable editable = getText();
    SpannableStringBuilder b = new SpannableStringBuilder(editable);
    CharSequence within;

    if (isUsernameless) {
      SpannableStringBuilder span = new SpannableStringBuilder(resultMention + " ");
      span.setSpan(TD.toSpan(new TdApi.TextEntityTypeMentionName(mention.getUser().id)), 0, resultMention.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      within = span;
    } else {
      within = resultMention + " ";
    }

    try { editable.replace(mention.getTargetStart(), Math.min(editable.length(), mention.getTargetEnd()), within); } catch (Throwable ignored) { }
    try { setInput(mention.replaceInTarget(b, within), false, true); Views.setSelection(this, mention.getTargetStart() + within.length()); } catch (Throwable ignored) { }
  }

  @Override
  public void onInlineQueryResultPick (InlineResult<?> result) {
    if (controller != null) {
      controller.sendInlineQueryResult(result.getQueryId(), result.getId(), true, true, false, null);
    }
  }

  // Media processing

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    MediaType.WEBP,
    MediaType.PNG,
    MediaType.GIF,
    MediaType.JPEG
  })
  private @interface MediaType {
    int
      WEBP = 1,
      PNG = 2,
      GIF = 3,
      JPEG = 4;
  }

  @Override
  protected InputConnection createInputConnection (EditorInfo editorInfo) {
    InputConnection ic = super.createInputConnection(editorInfo);
    if (isCaptionEditing()) {
      return ic;
    }
    String[] mimeTypes = new String[] {
      "image/webp",
      "image/png",
      "image/gif",
      "image/jpeg",
      "image/*"
    };
    InputWrapperWrapper.setContentMimeTypes(editorInfo, mimeTypes);
    if (ic == null)
      return null;
    final InputConnectionCompat.OnCommitContentListener callback =
      (inputContentInfo, flags, bundle) -> {
        if (controller == null || !controller.hasWritePermission())
          return false;

        ClipDescription description = inputContentInfo.getDescription();
        @MediaType int mediaType;
        if (description.hasMimeType("image/webp")) {
          mediaType = MediaType.WEBP;
        } else if (description.hasMimeType("image/png")) {
          mediaType = MediaType.PNG;
        } else if (description.hasMimeType("image/gif")) {
          mediaType = MediaType.GIF;
        } else if (description.hasMimeType("image/jpeg")) {
          mediaType = MediaType.JPEG;
        } else {
          return false;
        }

        // read and display inputContentInfo asynchronously
        boolean needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && (flags &
          InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0;
        if (needPermission) {
          try {
            inputContentInfo.requestPermission();
          } catch (Throwable t) {
            return false; // return false if failed
          }
        }
        Uri uri = inputContentInfo.getContentUri();
        long timestamp = System.currentTimeMillis();
        long chatId = controller.getChatId();
        long messageThreadId = controller.getMessageThreadId();
        long replyToMessageId = controller.obtainReplyId();
        boolean silent = controller.obtainSilentMode();
        boolean needMenu = controller.areScheduledOnly();

        Background.instance().post(() -> {
          int imageWidth, imageHeight;
          try (InputStream is = UI.getContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            imageWidth = opts.outWidth;
            imageHeight = opts.outHeight;
          } catch (Throwable t) {
            Log.e("Unable to read image", t);
            return;
          }
          if (imageWidth == 0 || imageHeight == 0) {
            Log.e("Unknown image bounds, aborting");
            return;
          }
          String path = uri.toString();
          boolean isAnimatedGif = true;
          if (mediaType == MediaType.GIF) {
            try (InputStream is = UI.getContext().getContentResolver().openInputStream(uri)) {
              isAnimatedGif = U.isAnimatedGIF(is);
            } catch (Throwable t) {
              Log.e("Unable to read GIF", t);
              isAnimatedGif = false;
            }
          }
          final TdApi.InputMessageContent content;
          final boolean isSecretChat = ChatId.isSecret(chatId);
          if (mediaType == MediaType.GIF && isAnimatedGif) {
            TdApi.InputFileGenerated generated = TD.newGeneratedFile(null, path, 0, timestamp);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageAnimation(generated, null, null, 0, imageWidth, imageHeight, null), isSecretChat);
          } else if ((mediaType != MediaType.JPEG && (mediaType == MediaType.WEBP || path.contains("sticker") || Math.max(imageWidth, imageHeight) <= 512))) {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, true, 512);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageSticker(generated, null, imageWidth, imageHeight, null), isSecretChat);
          } else {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, false, 0);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessagePhoto(generated, null, null, imageWidth, imageHeight, null, 0), isSecretChat);
          }
          if (needMenu) {
            tdlib.ui().post(() -> {
              tdlib.ui().showScheduleOptions(controller, chatId, false, (forceDisableNotification, schedulingState, disableMarkdown) -> tdlib.sendMessage(chatId, messageThreadId, replyToMessageId, new TdApi.MessageSendOptions(forceDisableNotification || silent, false, false, schedulingState), content, null), null);
            });
          } else {
            tdlib.sendMessage(chatId, messageThreadId, replyToMessageId, silent, false, content);
          }
        });
        // read and display inputContentInfo asynchronously.
        // call inputContentInfo.releasePermission() as needed.

        return true;  // return true if succeeded
      };
    return InputWrapperWrapper.createWrapper(ic, editorInfo, callback);
  }

  // Send by enter

  @Override
  public boolean needRemoveFinalNewLine (FinalNewLineFilter filter) {
    return needSendByEnter();
  }

  @Override
  public void onFinalNewLineBeingRemoved (FinalNewLineFilter filter) {
    post(InputView.this::sendByEnter);
  }

  // == Rework ==

  // IME flags & view options

  public void setMaxCodePointCount (int maxCodePointCount) {
    if (maxCodePointCount > 0) {
      setFilters(new InputFilter[] {
        new ExternalEmojiFilter(),
        new CodePointCountFilter(maxCodePointCount),
        new EmojiFilter(this),
        new CharacterStyleFilter(true),
        new FinalNewLineFilter(this)
      });
    } else {
      setFilters(new InputFilter[] {
        new ExternalEmojiFilter(),
        new EmojiFilter(this),
        new CharacterStyleFilter(true),
        new FinalNewLineFilter(this)
      });
    }
  }

  public final void setNoPersonalizedLearning (boolean noPersonalizedLearning) {
    final int secrecyFlag;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      secrecyFlag = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    } else {
      secrecyFlag = 0x1000000;
    }
    final int imeOptions = getImeOptions();
    final int newImeOptions = BitwiseUtils.setFlag(imeOptions, secrecyFlag, noPersonalizedLearning);
    if (imeOptions != newImeOptions) {
      setImeOptions(newImeOptions);
    }
  }

  // FormattedText generation

  public final TdApi.FormattedText getOutputText (boolean applyMarkdown) {
    SpannableStringBuilder text = new SpannableStringBuilder(getText());
    BaseInputConnection.removeComposingSpans(text);
    TdApi.FormattedText result = new TdApi.FormattedText(text.toString(), TD.toEntities(text, false));
    if (applyMarkdown) {
      Td.parseMarkdown(result);
    }
    return result;
  }

  // Android-related workarounds

  @Override
  public boolean onTextContextMenuItem (@IdRes int id) {
    try {
      int selectionStart = getSelectionStart();
      int selectionEnd = getSelectionEnd();
      Editable editable = getText();
      int length = selectionEnd - selectionStart;

      switch (id) {
        case android.R.id.cut: {
          if (length > 0) {
            CharSequence copyText = editable.subSequence(selectionStart, selectionEnd);
            editable.delete(selectionStart, selectionEnd);
            U.copyText(copyText);
            return true;
          }
          break;
        }
        case android.R.id.copy: {
          if (length > 0) {
            CharSequence copyText = editable.subSequence(selectionStart, selectionEnd);
            U.copyText(copyText);
            return true;
          }
          break;
        }
        case android.R.id.paste: {
          CharSequence pasteText = U.getPasteText(getContext());
          if (pasteText != null) {
            if (length > 0) {
              editable.replace(selectionStart, selectionEnd, pasteText);
            } else {
              editable.insert(selectionStart, pasteText);
            }
            return true;
          }
          break;
        }
      }
    } catch (Throwable t) {
      Log.e("onTextContextMenuItem failed for id %s", t, Lang.getResourceEntryName(id));
    }
    return super.onTextContextMenuItem(id);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    try {
      return super.onTouchEvent(event);
    } catch (Throwable t) {
      Log.e("System bug", t);
      return false;
    }
  }

  // Android bug https://code.google.com/p/android/issues/detail?id=208169R

  private boolean mEnabled;

  private void doBugfix () {
    if (!mEnabled) return;
    try {
      super.setEnabled(false);
    } catch (Throwable t) {
      Log.w(t);
    }
    try {
      super.setEnabled(mEnabled);
    } catch (Throwable t) {
      Log.w(t);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    doBugfix();
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.mEnabled = enabled;
    try {
      super.setEnabled(enabled);
    } catch (Throwable t) {
      Log.w(t);
    }
  }

  @Override
  public void setVisibility (int visibility) {
    super.setVisibility(visibility);
    if (visibility == View.VISIBLE) {
      doBugfix();
    }
  }
}
