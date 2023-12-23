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
 * File created on 28/02/2016 at 13:30
 */
package org.thunderdog.challegram.component.chat;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
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

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.inputmethod.InputConnectionCompat;

import org.drinkless.tdlib.TdApi;
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
import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.emoji.PreserveCustomEmojiFilter;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.helper.FoundUrls;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.LocaleChanger;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.ExternalEmojiFilter;
import org.thunderdog.challegram.util.FinalNewLineFilter;
import org.thunderdog.challegram.util.TextSelection;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.InputWrapperWrapper;
import org.thunderdog.challegram.widget.NoClipEditText;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class InputView extends NoClipEditText implements InlineSearchContext.Callback, InlineResultsWrap.PickListener, RtlCheckListener, FinalNewLineFilter.Callback, CustomEmojiSurfaceProvider, Destroyable {
  public static final boolean USE_ANDROID_SELECTION_FIX = true;
  private final TextPaint paint;

  private Text placeholderTitle;
  private Text placeholderSubTitle;
  private Drawable placeholderIcon;

  private CharSequence placeholderTitleText;
  private CharSequence placeholderSubtitleText;

  private final BoolAnimator showPlaceholder = new BoolAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final BoolAnimator hasSubPlaceholder = new BoolAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final ReplaceAnimator<Text> subtitleReplaceAnimator = new ReplaceAnimator<>(a -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

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
  private final RefreshRateLimiter refreshRateLimiter;
  private final ViewController<?> boundController;

  public interface SelectionChangeListener {
    void onInputSelectionExistChanged (InputView v, boolean hasSelection);
    void onInputSelectionChanged (InputView v, int start, int end);
  }

  private SelectionChangeListener selectionChangeListener;
  private boolean actionModeVisibility = true;
  private ActionMode currentActionMode;
  private boolean hasSelection;

  public InputView (Context context, Tdlib tdlib, ViewController<?> boundController) {
    super(context);
    this.tdlib = tdlib;
    this.refreshRateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    this.mediaHolder = new ComplexMediaHolder<>(this);
    this.mediaHolder.setUpdateListener((usages, displayMediaKey) ->
      refreshRateLimiter.invalidate()
    );
    this.inlineContext = new InlineSearchContext(UI.getContext(context), tdlib, this, boundController);
    this.boundController = boundController;
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      setCustomInsertionActionModeCallback(new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode (ActionMode actionMode, Menu menu) {
          currentActionMode = actionMode;
          return true;
        }

        @Override
        public boolean onPrepareActionMode (ActionMode actionMode, Menu menu) {
          updateMenuVisibility(menu);
          return true;
        }

        @Override
        public boolean onActionItemClicked (ActionMode actionMode, MenuItem menuItem) {
          return false;
        }

        @Override
        public void onDestroyActionMode (ActionMode actionMode) {
          if (currentActionMode == actionMode) {
            currentActionMode = null;
          }
        }
      });
    }

    setCustomSelectionActionModeCallback(new ActionMode.Callback() {
      @Override
      public boolean onCreateActionMode (ActionMode mode, Menu menu) {
        currentActionMode = mode;
        if (!Config.USE_CUSTOM_INPUT_STYLING) {
          return true;
        }

        MenuInflater inflater = mode.getMenuInflater();
        if (inflater == null) {
          return true;
        }
        inflater.inflate(R.menu.text, menu);
        try {
          for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            final int overrideResId;
            final TdApi.TextEntityType type;
            final int itemId = item.getItemId();
            if (itemId == R.id.btn_plain) {
              overrideResId = R.string.TextFormatClear;
              type = null;
            } else if (itemId == R.id.btn_bold) {
              overrideResId = R.string.TextFormatBold;
              type = new TdApi.TextEntityTypeBold();
            } else if (itemId == R.id.btn_italic) {
              overrideResId = R.string.TextFormatItalic;
              type = new TdApi.TextEntityTypeItalic();
            } else if (itemId == R.id.btn_spoiler) {
              overrideResId = R.string.TextFormatSpoiler;
              type = new TdApi.TextEntityTypeSpoiler();
            } else if (itemId == R.id.btn_underline) {
              overrideResId = R.string.TextFormatUnderline;
              type = new TdApi.TextEntityTypeUnderline();
            } else if (itemId == R.id.btn_strikethrough) {
              overrideResId = R.string.TextFormatStrikethrough;
              type = new TdApi.TextEntityTypeStrikethrough();
            } else if (itemId == R.id.btn_monospace) {
              overrideResId = R.string.TextFormatMonospace;
              type = new TdApi.TextEntityTypeCode();
            } else if (itemId == R.id.btn_link) {
              overrideResId = R.string.TextFormatLink;
              type = null;
            } else {
              if (BuildConfig.DEBUG) {
                Log.i("Menu item: %s %s", UI.getAppContext().getResources().getResourceName(item.getItemId()), item.getTitle());
              }
              continue;
            }
            item.setTitle(type != null ? Lang.wrap(Lang.getString(overrideResId), Lang.entityCreator(type)) : Lang.getString(overrideResId));
          }
        } catch (Throwable ignored) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          menu.removeItem(android.R.id.shareText);
        }
        if (!canClearTextFormat()) {
          menu.removeItem(R.id.btn_plain);
        }
        return true;
      }

      @Override
      public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
        updateMenuVisibility(menu);
        return true;
      }

      @Override
      public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
        return setSpan(item.getItemId());
      }

      @Override
      public void onDestroyActionMode (ActionMode mode) {
        if (currentActionMode == mode) {
          currentActionMode = null;
        }
      }
    });
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
        ((BaseActivity) getContext()).updateEmojiSuggestionsPosition(false);
      });
    }

    showPlaceholder.setValue(true, false);
  }

  private void updateMenuVisibility (Menu menu) {
    final int menuSize = menu.size();
    for (int i = 0; i < menuSize; i++) {
      MenuItem item = menu.getItem(i);
      if (item != null) {
        item.setVisible(actionModeVisibility);
      }
    }
  }

  @Override
  public EmojiSpan onCreateNewSpan (CharSequence emojiCode, EmojiInfo info, long customEmojiId) {
    return Emoji.instance().newCustomSpan(emojiCode, info, this, tdlib, customEmojiId);
  }

  @Override
  public void onInvalidateSpan (EmojiSpan span, boolean requiresLayoutUpdate) {
    if (requiresLayoutUpdate) {
      EmojiUpdater.invalidateEmojiSpan(this, span);
    }
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

  public boolean setSpan (@IdRes int id) {
    TextSelection selection = getTextSelection();
    if (selection == null || selection.isEmpty()) {
      return false;
    }
    TdApi.TextEntityType type;
    if (id == R.id.btn_plain) {
      clearSpans(selection.start, selection.end);
      return true;
    } else if (id == R.id.btn_bold) {
      type = new TdApi.TextEntityTypeBold();
    } else if (id == R.id.btn_italic) {
      type = new TdApi.TextEntityTypeItalic();
    } else if (id == R.id.btn_spoiler) {
      type = new TdApi.TextEntityTypeSpoiler();
    } else if (id == R.id.btn_strikethrough) {
      type = new TdApi.TextEntityTypeStrikethrough();
    } else if (id == R.id.btn_underline) {
      type = new TdApi.TextEntityTypeUnderline();
    } else if (id == R.id.btn_monospace) {
      type = new TdApi.TextEntityTypeCode();
    } else if (id == R.id.btn_link) {
      URLSpan[] existingSpans = getText().getSpans(selection.start, selection.end, URLSpan.class);
      URLSpan existingSpan = existingSpans != null && existingSpans.length > 0 ? existingSpans[0] : null;
      createTextUrl(existingSpan, selection.start, selection.end);
      return true;
    } else {
      return false;
    }
    setSpan(selection.start, selection.end, type);
    return true;
  }

  public void removeSpan (TdApi.TextEntityType type) {
    TextSelection selection = getTextSelection();
    if (selection != null && !selection.isEmpty()) {
      clearSpans(selection.start, selection.end, type);
    }
  }

  public boolean canClearTextFormat () {
    TextSelection selection = getTextSelection();
    if (selection != null && !selection.isEmpty()) {
      Editable editable = getText();
      Object[] spans = editable.getSpans(selection.start, selection.end, Object.class);
      if (spans != null) {
        for (Object span : spans) {
          if (span instanceof NoCopySpan || span instanceof EmojiSpan || isComposingSpan(editable, span) || !TD.canConvertToEntityType(span)) {
            continue;
          }
          return true;
        }
      }
    }
    return false;
  }

  private void clearSpans (int start, int end) {
    clearSpans(start, end, null);
  }

  private void clearSpans (int start, int end, @Nullable TdApi.TextEntityType typeForRemove) {
    Editable editable = getText();
    Object[] spans = editable.getSpans(start, end, Object.class);
    boolean updated = false;
    if (spans != null) {
      for (Object existingSpan : spans) {
        if (existingSpan instanceof NoCopySpan || existingSpan instanceof EmojiSpan || isComposingSpan(editable, existingSpan) || !TD.canConvertToEntityType(existingSpan)) {
          continue;
        }

        if (typeForRemove != null) {
          boolean needContinue = true;
          TdApi.TextEntityType[] textEntityTypes = TD.toEntityType(existingSpan);
          if (textEntityTypes != null) {
            for (TdApi.TextEntityType textEntityType : textEntityTypes) {
              if (textEntityType.getConstructor() == typeForRemove.getConstructor()) {
                needContinue = false;
                break;
              }
            }
          }
          if (needContinue) {
            continue;
          }
        }

        int existingSpanStart = editable.getSpanStart(existingSpan);
        int existingSpanEnd = editable.getSpanEnd(existingSpan);
        boolean reused = false;

        editable.removeSpan(existingSpan);

        boolean keepSpanBeforeStart = start > existingSpanStart;
        boolean keepSpanAfterEnd = existingSpanEnd > end;

        if (keepSpanBeforeStart && keepSpanAfterEnd) {
          editable.setSpan(TD.cloneSpan(existingSpan), existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          editable.setSpan(TD.cloneSpan(existingSpan), end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (keepSpanBeforeStart) {
          editable.setSpan(existingSpan, existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          reused = true;
        } else if (keepSpanAfterEnd) {
          editable.setSpan(existingSpan, end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          reused = true;
        }

        if (existingSpan instanceof Destroyable && !reused) {
          ((Destroyable) existingSpan).performDestroy();
        }
        updated = true;
      }
    }
    setSelection(start, end);
    if (updated) {
      inlineContext.forceCheck();
      if (spanChangeListener != null) {
        spanChangeListener.onSpansChanged(this);
      }
    }
  }

  private static boolean isComposingSpan (Spanned spanned, Object span) {
    return BitwiseUtils.hasFlag(spanned.getSpanFlags(span), Spanned.SPAN_COMPOSING);
  }

  private boolean setSpanImpl (int start, int end, TdApi.TextEntityType newType) {
    if (end - start <= 0 || !TD.canConvertToSpan(newType)) {
      return false;
    }
    Object newSpan = TD.toSpan(newType);
    Editable editable = getText();
    Object[] existingSpansArray = editable.getSpans(start, end, Object.class);
    List<Object> existingSpans = null;
    if (existingSpansArray != null && existingSpansArray.length > 0) {
      for (Object existingSpan : existingSpansArray) {
        if (existingSpan instanceof NoCopySpan || isComposingSpan(editable, existingSpan) || !TD.canConvertToEntityType(existingSpan)) {
          continue;
        }
        int existingSpanStart = editable.getSpanStart(existingSpan);
        int existingSpanEnd = editable.getSpanEnd(existingSpan);
        TdApi.TextEntityType[] existingTypes = TD.toEntityType(existingSpan);
        if (existingTypes == null || existingTypes.length == 0) {
          continue;
        }
        boolean matchingStyleSpans = false;
        if (newSpan instanceof StyleSpan && existingSpan instanceof StyleSpan) {
          StyleSpan existingStyleSpan = (StyleSpan) existingSpan;
          StyleSpan newStyleSpan = (StyleSpan) newSpan;
          if (newStyleSpan.getStyle() == existingStyleSpan.getStyle()) {
            matchingStyleSpans = true;
          }
        }
        boolean haveExactMatch = matchingStyleSpans;
        if (!haveExactMatch) {
          for (TdApi.TextEntityType existingType : existingTypes) {
            if (Td.equalsTo(existingType, newType)) {
              haveExactMatch = true;
              break;
            }
          }
        }
        if (haveExactMatch) {
          if (existingTypes.length == 1 || matchingStyleSpans) {
            if (start < existingSpanStart || end > existingSpanEnd) {
              // Medium path: extend existing span indexes if needed
              editable.removeSpan(existingSpan);
              editable.setSpan(
                existingSpan,
                Math.min(start, existingSpanStart),
                Math.max(end, existingSpanEnd),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
              );
              return true;
            }
            // Easy path: do nothing, because entire selection already has the same entity
            return false;
          }
          if (start >= existingSpanStart && end <= existingSpanEnd) {
            // Easy path: do nothing, because entire selection already has the same entity
            return false;
          }
          // Medium path: apply entity only to areas that do not have the exactly matching entity
          boolean changed;
          changed = setSpanImpl(start, existingSpanStart, newType);
          changed = setSpanImpl(existingSpanEnd, end, newType) || changed;
          return changed;
        }
        if (existingSpans == null) {
          existingSpans = new ArrayList<>(existingSpansArray.length);
        }
        existingSpans.add(existingSpan);
      }
    }
    if (existingSpans == null || existingSpans.isEmpty()) {
      // Easy path: just set new span at start .. end
      editable.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return true;
    }
    boolean canBeNested = Td.canBeNested(newType);
    for (Object existingSpan : existingSpans) {
      int existingSpanStart = editable.getSpanStart(existingSpan);
      int existingSpanEnd = editable.getSpanEnd(existingSpan);
      TdApi.TextEntityType[] existingTypes = TD.toEntityType(existingSpan);
      if (existingTypes == null || existingTypes.length == 0) {
        continue; // Unreachable
      }
      if (existingSpan instanceof EmojiSpan) {
        if (!((EmojiSpan) existingSpan).isCustomEmoji()) {
          throw new IllegalStateException(); // Unreachable
        }
        if (!canBeNested || Td.isTextUrl(newType)) {
          editable.removeSpan(existingSpan);
          if (existingSpan instanceof Destroyable) {
            ((Destroyable) existingSpan).performDestroy();
          }
          parseEmoji(editable, existingSpanStart, existingSpanEnd);
        }
        continue;
      }
      boolean moveExistingEntity = !canBeNested;
      for (TdApi.TextEntityType existingType : existingTypes) {
        if (!Td.canBeNested(existingType) || (Td.isTextUrl(existingType) && Td.isTextUrl(newType))) {
          moveExistingEntity = true;
        }
      }
      if (moveExistingEntity) {
        if (existingSpanStart < start && existingSpanEnd > end) {
          // Existing entity range covers start .. end fully, so we need it on both edges
          editable.removeSpan(existingSpan);
          editable.setSpan(existingSpan, existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          editable.setSpan(TD.cloneSpan(existingSpan), end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (existingSpanStart < start) {
          // Existing entity starts before start, so we update its position to existingSpanStart .. start
          editable.removeSpan(existingSpan);
          editable.setSpan(existingSpan, existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (existingSpanEnd > end) {
          // Existing entity ends after ens, so we update its position to end .. existingSpanEnd
          editable.removeSpan(existingSpan);
          editable.setSpan(existingSpan, end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
          // Existing entity is fully inside start .. end, so we have to remove it
          editable.removeSpan(existingSpan);
          if (existingSpan instanceof Destroyable) {
            ((Destroyable) existingSpan).performDestroy();
          }
        }
      } else if (existingSpan instanceof StyleSpan) {
        // Simplify work for getOutputText() if StyleSpan is located at the edges
        if (existingSpanStart < start && existingSpanEnd < end) {
          editable.removeSpan(existingSpan);
          editable.setSpan(TD.cloneSpan(existingSpan), existingSpanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          editable.setSpan(existingSpan, start, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (existingSpanEnd > end && existingSpanStart > start) {
          editable.removeSpan(existingSpan);
          editable.setSpan(existingSpan, existingSpanStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          editable.setSpan(TD.cloneSpan(existingSpan), end, existingSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }
    editable.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return true;
  }

  private void setSpan (int start, int end, TdApi.TextEntityType newType) {
    if (!TD.canConvertToSpan(newType)) {
      return;
    }
    boolean spansChanged = setSpanImpl(start, end, newType);
    setSelection(start, end);
    if (spansChanged) {
      inlineContext.forceCheck();
      if (spanChangeListener != null) {
        spanChangeListener.onSpansChanged(this);
      }
    }
  }

  private static void parseEmoji (Editable editable, int start, int end) {
    CharSequence cs = Emoji.instance().replaceEmoji(editable, start, end, null);
    if (cs != editable && cs instanceof Spanned) {
      Spanned emojiText = (Spanned) cs;
      EmojiSpan[] parsedEmojis = emojiText.getSpans(0, emojiText.length(), EmojiSpan.class);
      if (parsedEmojis != null) {
        for (EmojiSpan parsedEmoji : parsedEmojis) {
          int emojiStart = emojiText.getSpanStart(parsedEmoji);
          int emojiEnd = emojiText.getSpanEnd(parsedEmoji);
          editable.setSpan(
            parsedEmoji,
            start + emojiStart,
            start + emojiEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          );
        }
      }
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
    TextSelection selection = getTextSelection();
    inlineContext.onTextChanged(cs, str, selection != null && selection.isEmpty() ? selection.start : -1);
  }

  @Override
  protected void onSelectionChanged (int selStart, int selEnd) {
    super.onSelectionChanged(selStart, selEnd);
    if (selectionChangeListener != null) {
      selectionChangeListener.onInputSelectionChanged(this, selStart, selEnd);
    }
    if (inlineContext != null) {
      inlineContext.onCursorPositionChanged(selStart == selEnd ? selStart : -1);
    }
    boolean newHasSelection = selStart != selEnd;
    if (hasSelection != newHasSelection) {
      hasSelection = newHasSelection;
      if (selectionChangeListener != null) {
        selectionChangeListener.onInputSelectionExistChanged(this, hasSelection);
      }
    }
  }

  public void setSelectionChangeListener (SelectionChangeListener selectionChangeListener) {
    this.selectionChangeListener = selectionChangeListener;
  }

  public void setActionModeVisibility (boolean actionModeVisibility) {
    this.actionModeVisibility = actionModeVisibility;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setShowSoftInputOnFocus(actionModeVisibility);
    }

    if (currentActionMode != null) {
      currentActionMode.invalidate();
    }
  }

  public void hideSelectionCursors () {
    TextSelection selection = getTextSelection();
    if (selection == null || selection.isEmpty()) return;
    final int start = selection.start;
    final int end = selection.end;

    clearFocus();
    requestFocus();
    setSelection(start, end);
  }

  public boolean canFormatText () {
    TextSelection selection = getTextSelection();
    return selection != null && !selection.isEmpty() && selection.end <= getText().length();
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

    TextSelection selection = getTextSelection();
    if (controller != null) {
      inlineContext.onTextChanged(s, str, selection != null && selection.isEmpty() ? selection.start : -1);
      controller.onInputTextChange(s, !ignoreDraft && byUserAction);
    } else if (inputListener != null) {
      if (inputListener.canSearchInline(InputView.this)) {
        inlineContext.onTextChanged(s, str, selection != null && selection.isEmpty() ? selection.start : -1);
      }
      inputListener.onInputChanged(InputView.this, str);
    }
    if (!isSettingText && blockedText != null && !blockedText.equals(str)) {
      setInput(blockedText, true, false);
    }

    final boolean hasText = s.length() > 0;
    setAllowsAnyGravity(hasText);
    showPlaceholder.setValue(!hasText, !hasText && needAnimateChanges());
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
    setInputPlaceholder(placeholder, null, 0);
    /*if (controller == null) {
      // setHint(placeholder);
    } else {
      this.rawPlaceholderWidth = U.measureText(rawPlaceholder, getPaint());
      this.lastPlaceholderAvailWidth = 0;
      checkPlaceholderWidth();
    }*/
  }

  public void setInputPlaceholder (CharSequence placeholder, CharSequence placeholderSubtitle, @DrawableRes int iconId) {
    this.placeholderTitleText = placeholder;
    this.placeholderSubtitleText = placeholderSubtitle;
    this.placeholderIcon = iconId != 0 ? Drawables.get(getResources(), iconId) : null;
    if (controller != null) {
      this.lastPlaceholderAvailWidth = 0;
      checkPlaceholderWidth();
    }
    invalidate();
  }

  private boolean needAnimateChanges () {
    return UI.inUiThread() && boundController.getParentOrSelf().needsTempUpdates() && boundController.getParentOrSelf().isFocused();
  }

  public void checkPlaceholderWidth () {
    if ((lastPlaceholderRes != 0 || !StringUtils.isEmpty(placeholderTitleText) || placeholderIcon != null) && controller != null) {
      int availWidth = Math.max(0, getMeasuredWidth() - controller.getHorizontalInputPadding() - getPaddingLeft() - Screen.dp(placeholderIcon != null ? 20 : 0));
      if (this.lastPlaceholderAvailWidth != availWidth) {
        this.lastPlaceholderAvailWidth = availWidth;

        placeholderTitle = !StringUtils.isEmpty(placeholderTitleText) ? new Text.Builder(tdlib, placeholderTitleText, null, availWidth, Paints.robotoStyleProvider(Screen.px(getTextSize())), TextColorSets.PLACEHOLDER, null)
          .singleLine().clipTextArea().build() : null;

        placeholderSubTitle = !StringUtils.isEmpty(placeholderSubtitleText) ? new Text.Builder(tdlib, placeholderSubtitleText, null, availWidth, Paints.robotoStyleProvider(Screen.px(getTextSize()) / 3f * 2f), TextColorSets.PLACEHOLDER, null)
          .singleLine().clipTextArea().build() : null;

        boolean needAnimateChanges = needAnimateChanges();

        subtitleReplaceAnimator.replace(placeholderSubTitle, needAnimateChanges);

        hasSubPlaceholder.setValue(placeholderSubTitle != null, needAnimateChanges);

        if (rawPlaceholderWidth <= availWidth) {
          //setHint(rawPlaceholder);
        } else {
          //setHint(TextUtils.ellipsize(rawPlaceholder, getPaint(), availWidth, TextUtils.TruncateAt.END));
        }
      }
    }
  }

  private boolean needSendByEnter () {
    return Settings.instance().needSendByEnter() && !isSettingText && controller != null && controller.inSimpleSendMode();
  }

  private void sendByEnter () {
    if (controller != null && needSendByEnter()) {
      controller.pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) -> controller.sendText(true, sendOptions));
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

  private boolean isCaptionEditing () {
    return controller == null || controller.isEditingCaption();
  }

  @Override
  public boolean enableLinkPreview () {
    return !isCaptionEditing() && tdlib.canAddWebPagePreviews(controller.getChat());
  }

  @Override
  public void showLinkPreview (@Nullable FoundUrls foundUrls) {
    if (controller != null) {
      controller.showLinkPreview(foundUrls);
    }
  }

  @Override
  public boolean needsInlineBots () {
    return !isCaptionEditing() && tdlib.canSendMessage(controller.getChat(), RightId.SEND_OTHER_MESSAGES);
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
  public void showInlineStickers (ArrayList<TGStickerObj> stickers, String foundByEmoji, boolean isEmoji, boolean isMore) {
    if (controller != null) {
      if (!isEmoji) {
        controller.showStickerSuggestions(stickers, isMore);
      } else {
        controller.showEmojiSuggestions(stickers, foundByEmoji, isMore);
      }
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
      controller.onHideEmojiAndStickerSuggestionsFinally();
    }
    if (inputListener != null && inputListener.canSearchInline(this)) {
      inputListener.showInlineResults(this, null, false);
    } else {
      ((BaseActivity) getContext()).showInlineResults(controller, tdlib, null, false, null);
    }
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
    TextSelection selection = getTextSelection();
    if (selection == null || !isEnabled())
      return;
    int after = selection.start + emoji.length();
    SpannableString s = new SpannableString(emoji);
    s.setSpan(Emoji.instance().newSpan(emoji, null), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (selection.isEmpty()) {
      getText().insert(selection.start, s);
    } else {
      getText().replace(selection.start, selection.end, s);
    }
    setSelection(after);
  }

  public void onCustomEmojiSelected (TGStickerObj stickerObj) {
    onCustomEmojiSelected(stickerObj, false);
  }

  public void onCustomEmojiSelected (TdApi.Sticker sticker) {
    onCustomEmojiSelected(sticker, false);
  }

  public void onCustomEmojiSelected (TGStickerObj stickerObj, boolean needReplace) {
    onCustomEmojiSelected(stickerObj.getSticker(), needReplace);
  }

  public void onCustomEmojiSelected (TdApi.Sticker stickerObj, boolean needReplace) {
    TextSelection selection = getTextSelection();
    if (selection == null || !isEnabled())
      return;

    final String emoji = TD.stickerEmoji(stickerObj);
    final Editable editable = getText();
    final EmojiSpan oldEmojiSpan = needReplace ? Emoji.findPrecedingEmojiSpan(editable, selection.start) : null;

    final int start = oldEmojiSpan != null ? editable.getSpanStart(oldEmojiSpan) : selection.start;
    final int end = oldEmojiSpan != null ? editable.getSpanEnd(oldEmojiSpan) : selection.end;

    if (oldEmojiSpan != null) {
      editable.removeSpan(oldEmojiSpan);
      if (oldEmojiSpan instanceof Destroyable) {
        ((Destroyable) oldEmojiSpan).performDestroy();
      }
    }

    if (oldEmojiSpan != null && needReplace && Config.KEEP_ORIGINAL_EMOJI_WHEN_INPUT_CUSTOM_EMOJI) {
      editable.setSpan(Emoji.instance().newCustomSpan(emoji, null, this, tdlib, Td.customEmojiId(stickerObj)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      setSelection(start + emoji.length());
      if (inlineContext != null) {
        inlineContext.reset();
      }
      return;
    }

    SpannableString s = new SpannableString(emoji);
    s.setSpan(Emoji.instance().newCustomSpan(emoji, null, this, tdlib,
      Td.customEmojiId(stickerObj)), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    if (needReplace || start != end) {
      editable.replace(start, end, s);
    } else {
      editable.insert(start, s);
    }
    setSelection(start + s.length());
  }

  private boolean textChangedSinceChatOpened;

  public void setChat (TdApi.Chat chat, @Nullable ThreadInfo messageThread, @Nullable String customInputField, boolean isSilent) {
    textChangedSinceChatOpened = false;
    updateMessageHint(chat, messageThread, customInputField, isSilent);
    setDraft(!tdlib.canSendBasicMessage(chat) ? null :
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

  public void updateMessageHint (TdApi.Chat chat, @Nullable ThreadInfo messageThread, @Nullable String customInputField, boolean isSilent) {
    if (chat == null) {
      setInputPlaceholder(R.string.Message);
      return;
    }
    int resource;
    int icon = 0;
    CharSequence subplaceholder = null;
    Object[] args = null;
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);

    if (!tdlib.canSendBasicMessage(chat)) {
      resource = R.string.MessageInputTextDisabled;
      icon = R.drawable.baseline_block_18;
    } else if (tdlib.isChannel(chat.id)) {
      resource = isSilent ? R.string.ChannelSilentBroadcast : R.string.ChannelBroadcast;
    } /*else if (tdlib.isMultiChat(chat) && Td.isAnonymous(status)) {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.CommentAnonymously : R.string.MessageReplyAnonymously) :  R.string.MessageAnonymously;
    } else if (chat.messageSenderId != null && !tdlib.isSelfSender(chat.messageSenderId)) {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.CommentAsX : R.string.MessageReplyAsX) : R.string.MessageAsX;
      args = new Object[] { tdlib.senderName(chat.messageSenderId) };
    }*/ else {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.Comment : R.string.MessageReply) : R.string.Message;
    }
    if (!tdlib.isChannel(chat.id) && tdlib.isMultiChat(chat) && Td.isAnonymous(status) && !tdlib.isChannel(chat.messageSenderId)) {
      subplaceholder = Lang.getStringBold(R.string.AnyAsX, Lang.getString(R.string.AnonymousAdmin)); // "as Anonymous Admin";
    } else if (!tdlib.isChannel(chat.id) && chat.messageSenderId != null && !tdlib.isSelfSender(chat.messageSenderId)) {
      subplaceholder = Lang.getStringBold(R.string.AnyAsX, tdlib.senderName(chat.messageSenderId));
    }
    String text;
    if (StringUtils.isEmpty(customInputField)) {
      text = Lang.getString(resource);
    } else {
      text = customInputField;
    }
    setInputPlaceholder(text, subplaceholder, icon);
  }

  public void setDraft (@Nullable TdApi.InputMessageContent draftContent) {
    CharSequence draft;
    if (draftContent != null && draftContent.getConstructor() == TdApi.InputMessageText.CONSTRUCTOR) {
      TdApi.InputMessageText textDraft = (TdApi.InputMessageText) draftContent;
      draft = TD.toCharSequence(textDraft.text);
    } else {
      draft = "";
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
      setSelection(text != null ? text.length() : 0);
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
    final int x = getPaddingLeft() + Screen.dp(placeholderIcon != null ? 20 : 0);
    final float alpha = showPlaceholder.getFloatValue();
    final int offset = (int) (hasSubPlaceholder.getFloatValue() * (getTextSize() / 18 * 8));
    final int baseline = getBaseline();

    if (alpha > 0f) {
      if (placeholderTitle != null) {
        final int titleHeight = placeholderTitle.getHeight();
        final int titleBaseline = (int)(titleHeight * 0.75f);
        final int y = baseline - titleBaseline - offset;
        placeholderTitle.draw(c, x, y, null, alpha);
      }
      for (ListAnimator.Entry<Text> entry : subtitleReplaceAnimator) {
        final int offset2 = (int) ((!entry.isAffectingList() ?
          ((entry.getVisibility() - 1f) * (getTextSize() / 18f * 14f)):
          ((1f - entry.getVisibility()) * (getTextSize() / 18f * 14f))));
        entry.item.draw(c, x, baseline - offset / 2 + offset2, null, Math.min(alpha, entry.getVisibility()));
      }
      if (placeholderIcon != null) {
        Drawables.draw(c, placeholderIcon, getPaddingLeft(), (getMeasuredHeight() - placeholderIcon.getMinimumHeight()) / 2f, PorterDuffPaint.get(ColorId.iconLight) /*Paints.getPorterDuffPaint(ColorId.textPlaceholder)*/);
      }
    }

    super.onDraw(c);
    drawEmojiOverlay(c);
    if (this.displaySuffix.length() > 0 && this.prefix.length() > 0 && getLineCount() == 1) {
      String text = getText().toString();
      if (text.equalsIgnoreCase(prefix)) {
        checkPrefix(text);
        c.drawText(displaySuffix, x + prefixWidth, getBaseline(), paint);
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
    try { setInput(hashtag.replaceInTarget(b, within), false, true); setSelection(hashtag.getTargetStart() + within.length()); } catch (Throwable ignored) { }
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
    try { setInput(suggestion.replaceInTarget(b, within), false, true); setSelection(suggestion.getTargetStart() + within.length()); } catch (Throwable ignored) { }
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
    try { setInput(mention.replaceInTarget(b, within), false, true); setSelection(mention.getTargetStart() + within.length()); } catch (Throwable ignored) { }
  }

  @Override
  public void onInlineQueryResultPick (InlineResult<?> result) {
    if (controller != null) {
      controller.sendInlineQueryResult(result.getQueryId(), result.getId(), true, true, Td.newSendOptions());
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
        if (controller == null)
          return false;

        final long chatId = controller.getChatId();
        final TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) {
          return false;
        }

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
        long messageThreadId = controller.getMessageThreadId();
        TdApi.InputMessageReplyTo replyTo = controller.obtainReplyTo();
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
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageAnimation(generated, null, null, 0, imageWidth, imageHeight, null, false), isSecretChat);
          } else if ((mediaType != MediaType.JPEG && (mediaType == MediaType.WEBP || path.contains("sticker") || Math.max(imageWidth, imageHeight) <= 512))) {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, true, 512);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageSticker(generated, null, imageWidth, imageHeight, null), isSecretChat);
          } else {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, false, 0);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessagePhoto(generated, null, null, imageWidth, imageHeight, null, null, false), isSecretChat);
          }

          UI.post(() -> {
            if (controller.showRestriction(this, tdlib.getRestrictionText(chat, content))) {
              return;
            }
            if (needMenu) {
              tdlib.ui().showScheduleOptions(controller, chatId, false,
                (sendOptions, disableMarkdown) ->
                  tdlib.sendMessage(chatId, messageThreadId, replyTo,
                    Td.newSendOptions(sendOptions, silent),
                    content,
                    null
                  ),
                null, null);
            } else {
              tdlib.sendMessage(chatId, messageThreadId, replyTo, Td.newSendOptions(silent), content);
            }
          });
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
        new PreserveCustomEmojiFilter(),
        new ExternalEmojiFilter(),
        new CodePointCountFilter(maxCodePointCount),
        new EmojiFilter(this),
        new CharacterStyleFilter(true),
        new FinalNewLineFilter(this)
      });
    } else {
      setFilters(new InputFilter[] {
        new PreserveCustomEmojiFilter(),
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
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text.toString(), TD.toEntities(text, false));
    if (applyMarkdown) {
      //noinspection UnsafeOptInUsageError
      Td.parseMarkdown(formattedText);
    }
    return formattedText;
  }

  public final boolean hasOnlyPremiumFeatures () {
    return TD.hasCustomEmoji(getOutputText(false));
  }

  // Android-related workarounds

  @Override
  public boolean onTextContextMenuItem (@IdRes int id) {
    try {
      TextSelection selection = getTextSelection();
      if (selection == null) {
        return super.onTextContextMenuItem(id);
      }
      Editable editable = getText();
      switch (id) {
        case android.R.id.cut: {
          if (!selection.isEmpty()) {
            CharSequence copyText = editable.subSequence(selection.start, selection.end);
            editable.delete(selection.start, selection.end);
            U.copyText(copyText);
            setSelection(selection.start);
            return true;
          }
          break;
        }
        case android.R.id.copy: {
          if (!selection.isEmpty()) {
            CharSequence copyText = editable.subSequence(selection.start, selection.end);
            U.copyText(copyText);
            setSelection(selection.end);
            return true;
          }
          break;
        }
        case android.R.id.paste: {
          CharSequence pasteText = U.getPasteText(getContext());
          if (pasteText != null) {
            paste(pasteText, false);
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

  public void paste (TdApi.FormattedText pasteText, boolean needSelectPastedText) {
    paste(TD.toCharSequence(pasteText), needSelectPastedText);
  }

  public void paste (CharSequence pasteText, boolean needSelectPastedText) {
    paste(getTextSelection(), pasteText, needSelectPastedText);
  }

  private void paste (TextSelection selection, CharSequence pasteText, boolean needSelectPastedText) {
    if (selection == null) return;
    final int start = selection.start;
    final int end = selection.end;

    Editable editable = getText();
    if (selection.isEmpty()) {
      editable.insert(start, pasteText);
    } else {
      editable.replace(start, end, pasteText);
    }
    if (pasteText instanceof Spanned) {
      // TODO: should this be a part of EmojiFilter?
      removeCustomEmoji(editable, start, start + pasteText.length());
    }
    if (needSelectPastedText) {
      setSelection(start, start + pasteText.length());
    } else {
      setSelection(start + pasteText.length());
    }
  }

  private static void removeCustomEmoji (Editable editable, int start, int end) {
    URLSpan[] urlSpans = editable.getSpans(start, end, URLSpan.class);
    if (urlSpans != null) {
      for (URLSpan urlSpan : urlSpans) {
        int urlStart = editable.getSpanStart(urlSpan);
        int urlEnd = editable.getSpanEnd(urlSpan);
        EmojiSpan[] emojiSpans = editable.getSpans(urlStart, urlEnd, EmojiSpan.class);
        for (EmojiSpan emojiSpan : emojiSpans) {
          if (emojiSpan.isCustomEmoji()) {
            int emojiStart = editable.getSpanStart(emojiSpan);
            int emojiEnd = editable.getSpanEnd(emojiSpan);
            editable.removeSpan(emojiSpan);
            if (emojiSpan instanceof Destroyable) {
              ((Destroyable) emojiSpan).performDestroy();
            }
            parseEmoji(editable, emojiStart, emojiEnd);
          }
        }
      }
    }
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

  private final int[]
    cords1 = new int[2],
    cords2 = new int[2],
    cords3 = new int[2];

  public void getSymbolUnderCursorPosition (int[] coordinates) {
    TextSelection selection = getTextSelection();
    if (selection == null) {
      coordinates[0] = coordinates[1] = 0;
      return;
    }

    Views.getCharacterCoordinates(this, selection.start, cords1);
    cords2[0] = cords1[0];
    cords2[1] = cords1[1];
    int[] cords2 = this.cords2;

    for (int a = selection.start - 1; a >= 0; a--) {
      Views.getCharacterCoordinates(this, a, cords3);
      if (cords3[1] != cords1[1]) {
        cords2[0] /= 2;
        break;
      }
      if (cords3[0] == cords1[0]) continue;
      cords2 = cords3;
      break;
    }

    coordinates[0] = (cords1[0] + cords2[0]) / 2;
    coordinates[1] = cords1[1];
  }
}
