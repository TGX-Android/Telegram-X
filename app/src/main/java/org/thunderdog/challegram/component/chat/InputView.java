/**
 * File created on 28/02/16 at 13:30
 * Copyright Vyacheslav Krylov, 2014
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
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
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
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.data.InlineResultEmojiSuggestion;
import org.thunderdog.challegram.data.InlineResultHashtag;
import org.thunderdog.challegram.data.InlineResultMention;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.emoji.EmojiInputConnection;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.navigation.LocaleChanger;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.InputWrapperWrapper;
import org.thunderdog.challegram.widget.NoClipEditText;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class InputView extends NoClipEditText implements InlineSearchContext.Callback, Runnable, InlineResultsWrap.PickListener, Comparator<Object>, Emoji.Callback, RtlCheckListener, TGLegacyManager.EmojiLoadListener, Destroyable {
  public static final boolean USE_ANDROID_SELECTION_FIX = true;
  // private static final int HINT_TEXT_COLOR = 0xffa1aab3;

  private @Nullable MessagesController controller;

  private boolean ignoreDraft;
  private TextPaint paint;

  private String suffix = "", prefix = "", displaySuffix = "";
  private int prefixWidth, suffixWidth, suffixLeft/*, suffixTop*/;
  private final int verticalPadding;

  private final Tdlib tdlib;
  private final InlineSearchContext inlineContext;

  public interface InputListener {
    boolean canSearchInline (InputView v);
    void onInputChanged (InputView v, String input);

    long provideInlineSearchChatId (InputView v);
    TdApi.Chat provideInlineSearchChat (InputView v);
    int provideInlineSearchChatUserId (InputView v);
    void showInlineResults (InputView v, ArrayList<InlineResult<?>> items, boolean isContent);
    void addInlineResults (InputView v, ArrayList<InlineResult<?>> items);
  }

  @Override
  public final TdApi.FormattedText getOutputText (boolean applyMarkdown) {
    /*if (lastInputConnection != null) {
      lastInputConnection.finishComposingText();
    }*/
    SpannableStringBuilder text = new SpannableStringBuilder(getText());
    BaseInputConnection.removeComposingSpans(text);
    if (USE_LAYOUT_DIRECTION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      if (getLayoutDirection() == LAYOUT_DIRECTION_RTL && getLayout() != null) {
        SpannableStringBuilder b = new SpannableStringBuilder(text);
        int startIndex = 0;
        int endIndex;
        do {
          endIndex = StringUtils.indexOf(b, "\n", startIndex);
          if (endIndex == -1)
            endIndex = b.length();
          if (Strings.getTextDirection(b, startIndex, endIndex) == Strings.DIRECTION_NEUTRAL) {
            b.insert(startIndex, "\u200F");
            endIndex++;
          }
          startIndex = endIndex + 1;
        } while (endIndex < b.length());
        text = b;
      }
    }
    TdApi.FormattedText result = new TdApi.FormattedText(text.toString(), TD.toEntities(text, false));
    if (applyMarkdown) {
      Td.parseMarkdown(result);
    }
    return result;
  }

  private InputListener inputListener;

  private boolean isSecret;

  public void setIsSecret (boolean isSecret) {
    if (this.isSecret != isSecret) {
      this.isSecret = isSecret;
      int imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
      if (isSecret) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        } else {
          imeOptions |= 0x1000000;
        }
      }
      setImeOptions(imeOptions);
    }
  }

  private static String parseEmojiCode (String source) {
    if (StringUtils.isEmpty(source))
      return null;

    int i;

    // https://abs.twimg.com/emoji/v2/72x72/1f600.png
    i = source.indexOf("twimg.com/emoji/v2/");
    if (i != -1) {
      i = source.lastIndexOf('/');
      int j = source.lastIndexOf('.');
      if (j <= i) {
        j = -1;
      }
      if (j != -1) {
        source = source.substring(i + 1, j);
      } else {
        source = source.substring(i + 1);
      }
      source = fillZero(source, 8);
      return Emoji.parseCode(source, "UTF-32BE");
    }

    // https://m.vk.com/images/emoji/D83DDE0C_2x.png
    i = source.indexOf("vk.com/images/emoji/");
    if (i != -1) {
      i += "vk.com/images/emoji/".length();
      int sourceEnd = source.length();
      if (source.endsWith("_2x.png")) {
        sourceEnd -= "_2x.png".length();
      } else if (source.endsWith(".png")) {
        sourceEnd -= ".png".length();
      } else {
        sourceEnd = -1;
      }
      if (i < sourceEnd) {
        source = fillZero(source.substring(i, sourceEnd), 8);
        return Emoji.parseCode(source, "UTF-16");
      }
    }
    // https://static.xx.fbcdn.net/images/emoji.php/v9/ffb/1/24/1f61a.png
    // do nothing

    return null;
  }

  private static class EditedSpan implements NoCopySpan { }

  private static String fillZero (String source, int count) {
    int remaining = count - source.length() % count;
    if (remaining != 0) {
      StringBuilder b = new StringBuilder(source.length() + remaining);
      for (int j = 0; j < remaining; j++) {
        b.append('0');
      }
      b.append(source);
      return b.toString();
    }
    return source;
  }

  private static final boolean USE_LAYOUT_DIRECTION = false;

  public static boolean isSupportedSpan (Spanned spanned, CharacterStyle span) {
    if (span instanceof NoCopySpan || span instanceof EmojiSpan || span instanceof UnderlineSpan)
      return true;
    if (TD.canConvertToEntityType(span)) {
      if (span instanceof URLSpan) {
        int start = spanned.getSpanStart(span);
        int end = spanned.getSpanEnd(span);
        String text = spanned.subSequence(start, end).toString();
        String url = ((URLSpan) span).getURL();
        if (text.equals(url)) // <a href="example.com">example.com</a>
          return false;
        if (Strings.isValidLink(text)) {
          if (Strings.hostsEqual(url, text))
            return true;
          // Hosts are different. Most likely some <a href="https://youtube.com/redirect?v=${real_url}">${real_url}</a>
          // TODO lookup for this domain in GET arguments? Decision for now: no, because redirects could be like t.co/${id} without real url
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean shouldRemoveSpan (Spanned spanned, CharacterStyle span) {
    return !isSupportedSpan(spanned, span) && !(span instanceof SuggestionSpan) && (spanned.getSpanFlags(span) & Spanned.SPAN_COMPOSING) == 0;
  }

  public InputView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.inlineContext = new InlineSearchContext(UI.getContext(context), tdlib, this);
    this.paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    this.paint.setColor(Theme.textPlaceholderColor());
    this.paint.setTypeface(Fonts.getRobotoRegular());
    this.paint.setTextSize(Screen.sp(18f));
    setGravity(Lang.gravity() | Gravity.TOP);
    if (USE_LAYOUT_DIRECTION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      setLayoutDirection(Lang.rtl() ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
    }
    setTypeface(Fonts.getRobotoRegular());
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
    verticalPadding = Screen.dp(12f);
    if (Lang.isRtl) {
      setPadding(Screen.dp(55f), verticalPadding, this.suffixLeft = Screen.dp(60f), verticalPadding);
    } else {
      setPadding(this.suffixLeft = Screen.dp(60f), verticalPadding, Screen.dp(55f), verticalPadding);
    }
    setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    setInputType(getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    setSingleLine(false);
    setMaxLines(8);
    setMinimumHeight(Screen.dp(49f));
    // setMovementMethod(LinkMovementMethod.getInstance());

    /*setTextIsSelectable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);*/

    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    Views.clearCursorDrawable(this);
    addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

      private void addEditedSpan (Spannable sp, int start, int end) {
        int count = end - start;
        if (count <= 0)
          return;
        boolean onlyLetters = true;
        for (int i = 0; i < count; ) {
          int codePoint = Character.codePointAt(sp, start + i);
          if (!(Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint) || Text.needsFill(codePoint))) {
            onlyLetters = false;
            break;
          }
          int size = Character.charCount(codePoint);
          i += size;
        }
        if (onlyLetters) {
          CharacterStyle[] spans = sp.getSpans(start, start + count, CharacterStyle.class);
          boolean hasRudimentarySpans = false;
          if (spans != null && spans.length > 0) {
            for (CharacterStyle span : spans) {
              if (shouldRemoveSpan(sp, span)) {
                hasRudimentarySpans = true;
                break;
              }
            }
          }
          if (!hasRudimentarySpans)
            return;
        }
        sp.setSpan(new EditedSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      @Override
      public void onTextChanged (CharSequence s, int start, int before, int count) {
        if (ignoreAnyChanges) {
          changesText = s;
          return;
        }
        processTextChange(s);
        if (count > 0 && s instanceof Spannable) {
          Spannable sp = (Spannable) s;
          EmojiSpan[] spans = sp.getSpans(start, start + count, EmojiSpan.class);
          int startIndex = start;
          if (spans != null && spans.length > 0) {
            for (EmojiSpan span : spans) {
              int spanStart = sp.getSpanStart(span);
              int spanEnd = sp.getSpanEnd(span);
              if (startIndex < spanStart) {
                addEditedSpan(sp, startIndex, spanStart);
              }
              startIndex = spanEnd;
            }
          }
          int endIndex = start + count;
          if (startIndex < endIndex) {
            addEditedSpan(sp, startIndex, endIndex);
          }
        }
      }

      private void handleEmojiChanges (final Editable s) {
        EditedSpan[] editedRegions = s.getSpans(0, s.length(), EditedSpan.class);
        if (editedRegions == null || editedRegions.length == 0)
          return;
        List<Object> spansToProcess = null;

        pendingSortSpannable = s;
        for (EditedSpan editedRegion : editedRegions) {
          int editedRegionStart = s.getSpanStart(editedRegion);
          int editedRegionEnd = s.getSpanEnd(editedRegion);
          s.removeSpan(editedRegion);

          if (editedRegionStart == -1 || editedRegionEnd == -1)
            continue;

          CharacterStyle[] spans = s.getSpans(editedRegionStart, editedRegionEnd, CharacterStyle.class);
          if (spans != null && spans.length > 0) {
            for (CharacterStyle span : spans) {
              if (span instanceof ImageSpan) {
                int spanStart = s.getSpanStart(span);
                int spanEnd = s.getSpanEnd(span);
                EmojiSpan newSpan = Emoji.instance().newSpan(parseEmojiCode(((ImageSpan) span).getSource()), null);
                if (newSpan != null) {
                  s.removeSpan(span);
                  s.setSpan(newSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                  if (spansToProcess == null)
                    spansToProcess = new ArrayList<>();
                  spansToProcess.add(newSpan);
                  continue;
                } else if (spanEnd > spanStart) {
                  if (spansToProcess == null)
                    spansToProcess = new ArrayList<>();
                  spansToProcess.add(span);
                  continue;
                }
              }
              if (shouldRemoveSpan(s, span)) {
                s.removeSpan(span);
              }
            }
          }

          Emoji.instance().replaceEmoji(s, editedRegionStart, editedRegionEnd, null, InputView.this);

          if (spansToProcess != null && !spansToProcess.isEmpty()) {
            Collections.sort(spansToProcess, InputView.this);
            int selectionStart = getSelectionStart();
            int selectionEnd = getSelectionEnd();
            for (int i = spansToProcess.size() - 1; i >= 0; i--) {
              Object span = spansToProcess.get(i);
              int spanStart = s.getSpanStart(span);
              int spanEnd = s.getSpanEnd(span);
              if (spanStart == -1 || spanEnd == -1)
                continue;
              int spanLen = spanEnd - spanStart;
              if (span instanceof EmojiSpan) {
                String replacement = ((EmojiSpan) span).getEmojiCode().toString();
                s.replace(spanStart, spanEnd, replacement);
                int newLen = replacement.length();
                if (newLen == spanLen) {
                  continue;
                }
                int diff = newLen - spanLen;
                if (selectionStart >= spanStart)
                  selectionStart += diff;
                if (selectionEnd >= spanStart)
                  selectionEnd += diff;
              } else {
                s.delete(spanStart, spanEnd);
                if (selectionStart >= spanStart)
                  selectionStart -= spanLen;
                if (selectionEnd >= spanStart)
                  selectionEnd -= spanLen;
              }
            }
            if (selectionStart != -1 && selectionEnd != -1) {
              int len = s.length();
              if (selectionStart >= selectionEnd) {
                Views.setSelection(InputView.this, Math.min(selectionStart, len));
              } else {
                Views.setSelection(InputView.this, Math.min(selectionStart, len), Math.max(selectionEnd, len));
              }
            }
            spansToProcess.clear();
          }
        }
      }

      @Override
      public void afterTextChanged (Editable s) {
        if (ignoreChanges || ignoreAnyChanges) {
          return;
        }
        if (ignoreDraft) {
          ignoreChanges = true;
          handleEmojiChanges(s);
          ignoreChanges = false;
          ignoreDraft = false;
        } else if (s.length() > 0) {
          ignoreChanges = true;
          pendingSortSpannable = s;

          handleEmojiChanges(s);

          if (controller != null) {
            controller.updateSendButton(s, true);
          }
          if (needSendByEnter()) {
            InputView.this.post(InputView.this);
          }

          ignoreChanges = false;
        } else {
          if (controller != null) {
            controller.updateSendButton("", true);
          }
        }
      }
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

    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Override
  public void invalidate () {
    super.invalidate();
  }

  @Override
  public void onEmojiPartLoaded () {
    Editable editable = getText();
    if (editable == null || editable.length() == 0)
      return;
    EmojiSpan[] spans = editable.getSpans(0, editable.length(), EmojiSpan.class);
    if (spans == null || spans.length == 0)
      return;
    for (EmojiSpan span : spans) {
      if (span.needRefresh()) {
        int spanStart = editable.getSpanStart(span);
        int spanEnd = editable.getSpanEnd(span);
        editable.removeSpan(span);
        editable.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;
      }
    }
  }

  @Override
  public void performDestroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
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

  private CharSequence changesText;

  public void restartTextChange () {
    CharSequence cs = getText();
    String str = cs.toString();
    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    inlineContext.onTextChanged(cs, str, selectionStart == selectionEnd ? selectionStart : -1);
  }

  public void setTextSilently (String text) {
    boolean origValue = ignoreDraft;
    ignoreDraft = true;
    setText(text);
    ignoreDraft = origValue;
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

  private void processTextChange (CharSequence s) {
    changesText = null;

    String str = s.toString();

    if (str.isEmpty()) {
      textChangedSinceChatOpened = false;
    }

    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    if (controller != null) {
      inlineContext.onTextChanged(s, str, selectionStart == selectionEnd ? selectionStart : -1);
      controller.onInputTextChange(s);
    } else if (inputListener != null) {
      if (inputListener.canSearchInline(InputView.this)) {
        inlineContext.onTextChanged(s, str, selectionStart == selectionEnd ? selectionStart : -1);
      }
      inputListener.onInputChanged(InputView.this, str);
    }
    if (!isSettingText && blockedText != null && !blockedText.equals(str)) {
      setInput(blockedText, true);
    }
    // helper.process(str);

    if (!ignoreDraft && controller != null) {
      controller.setTyping(s.length() > 0);
      if (!controller.isEditingMessage()) {
        textChangedSinceChatOpened = true;
      }
    }
    setAllowsAnyGravity(str.length() > 0);
  }

  @Override
  public void checkRtl () {
    if (!allowsAnyGravity) {
      setGravity(Lang.gravity() | Gravity.TOP);
    }
    if (USE_LAYOUT_DIRECTION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      setLayoutDirection(Lang.rtl() ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
    }
  }

  // Text watcher

  private boolean ignoreChanges;
  private Spannable pendingSortSpannable;

  @Override
  public int compare (Object o1, Object o2) {
    return Integer.compare(pendingSortSpannable.getSpanStart(o1), pendingSortSpannable.getSpanStart(o2));
  }

  @Override
  public boolean onEmojiFound (CharSequence input, CharSequence code, EmojiInfo info, int position, int length) {
    EmojiSpan span = Emoji.instance().newSpan(code, info);
    if (span != null) {
      pendingSortSpannable.setSpan(span, position, position + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return true;
  }

  // ETc

  private int lastPlaceholderRes;
  private String rawPlaceholder;
  private float rawPlaceholderWidth;
  private int lastPlaceholderAvailWidth;

  public void setInputPlaceholder (@StringRes int resId) {
    if (controller == null) {
      setHint(Lang.getString(lastPlaceholderRes = resId));
      return;
    }

    if (this.lastPlaceholderRes == resId) {
      return;
    }

    lastPlaceholderRes = resId;
    rawPlaceholder = Lang.getString(resId);
    rawPlaceholderWidth = U.measureText(rawPlaceholder, getPaint());

    lastPlaceholderAvailWidth = 0;

    checkPlaceholderWidth();
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

  @Override
  public void run () {
    Editable text = getText();
    if (needSendByEnter() && controller != null && !StringUtils.isEmptyOrBlank(text) && text.charAt(text.length() - 1) == '\n') {
      text.delete(text.length() - 1, text.length());
      controller.pickDateOrProceed(false, null, (forceDisableNotification, schedulingState, disableMarkdown) -> controller.sendText(true, forceDisableNotification, schedulingState));
    }
  }

  private boolean needSendByEnter () {
    return Settings.instance().needSendByEnter() && !isSettingText && controller != null && controller.inSimpleSendMode();
  }

  public void setInputListener (InputListener inputListener) {
    this.inputListener = inputListener;
  }

  public Paint getPlaceholderPaint () {
    return paint;
  }

  // Inline results

  private static final int MEDIA_TYPE_WEBP = 1;
  private static final int MEDIA_TYPE_PNG = 2;
  private static final int MEDIA_TYPE_GIF = 3;
  private static final int MEDIA_TYPE_JPEG = 4;

  private InputConnection createInputConnection (EditorInfo editorInfo) {
    InputConnection ic = super.onCreateInputConnection(editorInfo);
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
        int mediaType;
        if (description.hasMimeType("image/webp")) {
          mediaType = MEDIA_TYPE_WEBP;
        } else if (description.hasMimeType("image/png")) {
          mediaType = MEDIA_TYPE_PNG;
        } else if (description.hasMimeType("image/gif")) {
          mediaType = MEDIA_TYPE_GIF;
        } else if (description.hasMimeType("image/jpeg")) {
          mediaType = MEDIA_TYPE_JPEG;
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
          boolean isGif = true;
          if (mediaType == MEDIA_TYPE_GIF) {
            try (InputStream is = UI.getContext().getContentResolver().openInputStream(uri)) {
              isGif = U.isAnimatedGIF(is);
            } catch (Throwable t) {
              Log.e("Unable to read GIF", t);
              isGif = false;
            }
          }
          final TdApi.InputMessageContent content;
          final boolean isSecretChat = ChatId.isSecret(chatId);
          if (mediaType == MEDIA_TYPE_GIF && isGif) {
            TdApi.InputFileGenerated generated = TD.newGeneratedFile(null, path, 0, timestamp);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageAnimation(generated, null, null, 0, imageWidth, imageHeight, null), isSecretChat);
          } else if ((mediaType != MEDIA_TYPE_JPEG && (mediaType == MEDIA_TYPE_WEBP || path.contains("sticker") || Math.max(imageWidth, imageHeight) <= 512))) {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, true, 512);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageSticker(generated, null, imageWidth, imageHeight, null), isSecretChat);
          } else {
            TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, timestamp, false, 0);
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessagePhoto(generated, null, null, imageWidth, imageHeight, null, 0), isSecretChat);
          }
          if (needMenu) {
            tdlib.ui().post(() -> {
              tdlib.ui().showScheduleOptions(controller, chatId, false, (forceDisableNotification, schedulingState, disableMarkdown) -> tdlib.sendMessage(chatId, messageThreadId, replyToMessageId, new TdApi.MessageSendOptions(forceDisableNotification || silent, false, schedulingState), content, null), null);
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

  private InputConnection lastInputConnection;

  @Override
  public InputConnection onCreateInputConnection (EditorInfo editorInfo) {
    InputConnection ic = createInputConnection(editorInfo);
    if (ic == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || ic instanceof EmojiInputConnection)
      return lastInputConnection = ic;
    return lastInputConnection = new EmojiInputConnection(this, ic);
  }

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
  public int provideInlineSearchChatUserId () {
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

  private boolean ignoreAnyChanges;

  public void setIgnoreAnyChanges (boolean ignoreAnyChanges) {
    if (this.ignoreAnyChanges != ignoreAnyChanges) {
      this.ignoreAnyChanges = ignoreAnyChanges;
      if (ignoreAnyChanges) {
        changesText = null;
      } else if (changesText != null) {
        processTextChange(changesText);
      }
    }
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
          int placeholderRes = lastPlaceholderRes;
          lastPlaceholderRes = 0;
          setInputPlaceholder(placeholderRes);
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
    if (!controller.isEditingMessage() && (!isEmpty() || force)) {
      textChangedSinceChatOpened = true;
    }
  }

  public void updateMessageHint (TdApi.Chat chat, @Nullable ThreadInfo messageThread, boolean isSilent) {
    if (chat == null) {
      setInputPlaceholder(R.string.Message);
      return;
    }
    int resource;
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    if (tdlib.isChannel(chat.id)) {
      resource = isSilent ? R.string.ChannelSilentBroadcast : R.string.ChannelBroadcast;
    } else if (tdlib.isMultiChat(chat) && Td.isAnonymous(status)) {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.CommentAnonymously : R.string.MessageReplyAnonymously) :  R.string.MessageAnonymously;
    } else {
      resource = messageThread != null ? (messageThread.areComments() ? R.string.Comment : R.string.MessageReply) : R.string.Message;
    }
    setInputPlaceholder(resource);
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
      setInput(draft, draft.length() > 0);
      controller.updateSendButton(draft.toString(), false);
    }
  }

  // Suffix stuff

  private boolean isSettingText;

  public void setInput (CharSequence text, boolean moveCursor) {
    this.isSettingText = true;
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

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);

    if (this.displaySuffix.length() > 0 && this.prefix.length() > 0 && getLineCount() == 1) {
      String text = getText().toString();
      if (text.toLowerCase().equals(prefix.toLowerCase())) {
        checkPrefix(text);
        c.drawText(displaySuffix, suffixLeft + prefixWidth, getBaseline(), paint);
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

  // Inline query

  @Override
  public void onCommandPick (InlineResultCommand command, boolean isLongPress) {
    if (controller != null) {
      TdApi.Chat chat = controller.getChat();
      if (isLongPress) {
        String str = (chat != null && ChatId.isUserChat(ChatId.toUserId(chat.id))) || command.getUsername() == null ? command.getCommand() + ' ' : command.getCommand() + '@' + command.getUsername() + ' ';
        setInput(str, true);
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
    try { setInput(hashtag.replaceInTarget(b, within), false); Views.setSelection(this, hashtag.getTargetStart() + within.length()); } catch (Throwable ignored) { }
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
    try { setInput(suggestion.replaceInTarget(b, within), false); Views.setSelection(this, suggestion.getTargetStart() + within.length()); } catch (Throwable ignored) { }
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
    try { setInput(mention.replaceInTarget(b, within), false); Views.setSelection(this, mention.getTargetStart() + within.length()); } catch (Throwable ignored) { }
  }

  @Override
  public void onInlineQueryResultPick (InlineResult<?> result) {
    if (controller != null) {
      controller.sendInlineQueryResult(result.getQueryId(), result.getId(), true, true, false, null);
    }
  }
}
