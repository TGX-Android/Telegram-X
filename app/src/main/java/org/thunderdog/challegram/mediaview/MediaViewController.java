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
 * File created on 01/09/2015 at 00:51
 */
package org.thunderdog.challegram.mediaview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.InlineResultsWrap;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.preview.FlingDetector;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFilteredFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.Watcher;
import org.thunderdog.challegram.loader.WatcherReference;
import org.thunderdog.challegram.mediaview.crop.CropAreaView;
import org.thunderdog.challegram.mediaview.crop.CropLayout;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.crop.CropTargetView;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.mediaview.gl.EGLEditorView;
import org.thunderdog.challegram.mediaview.paint.PaintMode;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.mediaview.paint.SimpleDrawing;
import org.thunderdog.challegram.mediaview.paint.widget.ColorDirectionView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorPickerView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorPreviewView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorToneView;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.StopwatchHeaderButton;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SetSenderController;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.CheckView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.VideoTimelineView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class MediaViewController extends ViewController<MediaViewController.Args> implements
  PopupLayout.AnimatedPopupProvider, FactorAnimator.Target, View.OnClickListener,
  MediaStackCallback, MediaFiltersAdapter.Callback, Watcher, RotationControlView.Callback, MediaView.ClickListener,
  EmojiLayout.Listener, InputView.InputListener, InlineResultsWrap.OffsetProvider,
  MediaCellView.Callback, SliderView.Listener, TGLegacyManager.EmojiLoadListener, Menu, MoreDelegate, PopupLayout.TouchSectionProvider, FlingDetector.Callback, CallManager.CurrentCallListener, ColorPreviewView.BrushChangeListener, PaintState.UndoStateListener, MediaView.FactorChangeListener, EmojiToneHelper.Delegate, MessageListener {

  private static final long REVEAL_ANIMATION_DURATION = /*BuildConfig.DEBUG ? 1800l :*/ 180;
  private static final long REVEAL_OPEN_ANIMATION_DURATION = /*BuildConfig.DEBUG ? 1800l :*/ 180l;

  public static final int MODE_MESSAGES = 0; // opened from chat
  public static final int MODE_PROFILE = 1; // opened from profile or chat (in case of groups and channels)
  public static final int MODE_CHAT_PROFILE = 2;
  public static final int MODE_GALLERY = 3; // opened from gallery, need photo editor and stuff
  public static final int MODE_SECRET = 4; // just single photo, no animations and etc
  public static final int MODE_SIMPLE = 5;

  public MediaViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean reverseMode;

  public int getMode () {
    return this.mode;
  }

  public static class Args {
    public final ViewController<?> parentController;
    public final int mode;
    public MediaViewDelegate delegate;
    public final MediaSelectDelegate selectDelegate;
    public final MediaSendDelegate sendDelegate;
    public final MediaStack stack;
    public String caption;
    private boolean noLoadMore;
    private String customSubtitle;

    private boolean forceThumbs, forceOpenIn;

    private String copyLink;

    private boolean reverseMode;

    private long receiverChatId, messageThreadId;

    private boolean areOnlyScheduled, deleteOnExit;

    public Args (ViewController<?> parentController, int mode, MediaViewDelegate delegate, MediaSelectDelegate selectDelegate, MediaSendDelegate sendDelegate, MediaStack stack) {
      this.parentController = parentController;
      this.mode = mode;
      this.delegate = delegate;
      this.selectDelegate = selectDelegate;
      this.sendDelegate = sendDelegate;
      this.stack = stack;
    }

    public Args (ViewController<?> parentController, int mode, MediaStack stack) {
      this(parentController, mode, null, null, null, stack);
    }

    public Args setOnlyScheduled (boolean onlyScheduled) {
      this.areOnlyScheduled = onlyScheduled;
      return this;
    }

    public Args setDeleteOnExit (boolean deleteOnExit) {
      this.deleteOnExit = deleteOnExit;
      return this;
    }

    public Args setForceThumbs (boolean forceThumbs) {
      this.forceThumbs = forceThumbs;
      return this;
    }

    public Args setForceOpenIn (boolean forceOpenIn) {
      this.forceOpenIn = forceOpenIn;
      return this;
    }

    public Args setReceiverChatId (long chatId) {
      this.receiverChatId = chatId;
      return this;
    }

    public Args setMessageThreadId (long messageThreadId) {
      this.messageThreadId = messageThreadId;
      return this;
    }

    public @Nullable TdApi.SearchMessagesFilter filter;

    public void setFilter (@Nullable TdApi.SearchMessagesFilter filter) {
      this.filter = filter;
    }

    public static Args fromGallery (ViewController<?> context, MediaViewDelegate delegate, MediaSelectDelegate selectDelegate, MediaSendDelegate sendDelegate, MediaStack galleryStack, boolean areOnlyScheduled) {
      return new Args(context, MODE_GALLERY, delegate, selectDelegate, sendDelegate, galleryStack).setOnlyScheduled(areOnlyScheduled);
    }
  }

  // Controller-related

  @Override
  public int getId () {
    return R.id.controller_mediaView;
  }

  // Stack-related stuff

  private int mode;
  private @Nullable MediaViewDelegate delegate;
  private @Nullable MediaSelectDelegate selectDelegate;
  private @Nullable MediaSendDelegate sendDelegate;
  private MediaStack stack;
  private @Nullable TdApi.SearchMessagesFilter filter;
  private long messageThreadId;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.delegate = args.delegate;
    this.selectDelegate = args.selectDelegate;
    this.sendDelegate = args.sendDelegate;
    this.stack = args.stack;
    this.reverseMode = args.reverseMode;
    this.filter = args.filter;
    this.messageThreadId = args.messageThreadId;
  }

  public MediaViewThumbLocation getCurrentTargetLocation () {
    return delegate != null && !UI.isNavigationAnimating() ? delegate.getTargetLocation(stack.getCurrentIndex(), stack.getCurrent()) : null;
  }

  // Appear animation

  private static final int ANIMATION_TYPE_FADE = 0;
  private static final int ANIMATION_TYPE_REVEAL = 1;
  private static final int ANIMATION_TYPE_SLIDEOFF = 2;
  private static final int ANIMATION_TYPE_PIP_CLOSE = 3;
  private static final int ANIMATION_TYPE_SECRET_CLOSE = 4;
  private static final int ANIMATION_TYPE_CAMERA = 5;

  private static final int ANIMATOR_REVEAL = 0;
  private FactorAnimator revealAnimator;
  private int revealAnimationType;
  private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(.97f);
  private static final Interpolator OVERSHOOT_INTERPOLATOR_2 = new OvershootInterpolator(.82f);

  private MediaViewThumbLocation currentThumb;

  private boolean isFromCamera;

  public boolean isFromCamera () {
    return isFromCamera;
  }

  private boolean isInstant;

  public void forceCameraAnimationType (boolean isInstant) {
    this.isInstant = isInstant;
    this.forceAnimationType = ANIMATION_TYPE_CAMERA;
    this.isFromCamera = true;
  }

  @Override
  public void prepareShowAnimation () {
    revealAnimator = new FactorAnimator(ANIMATOR_REVEAL, this, AnimatorUtils.DECELERATE_INTERPOLATOR, REVEAL_ANIMATION_DURATION);
    setLowProfile(true);
    onAppear();

    MediaViewThumbLocation location = getCurrentTargetLocation();

    if (location != null && !isInstant) {
      currentThumb = location;
      setAnimatorType(ANIMATION_TYPE_REVEAL, true);
      mediaView.setTarget(location, 0f);
      hideCurrentCell();

      switch (mode) {
        case MODE_GALLERY: {
          setEditComponentsAlpha(0f);
          break;
        }
      }
    } else {
      currentThumb = null;
      if (isFromCamera || isInstant) {
        setAnimatorType(ANIMATION_TYPE_CAMERA, true);
      } else {
        setAnimatorType(ANIMATION_TYPE_FADE, true);
        mediaView.setAlpha(0f);
      }
    }

    initHeaderStyle();
  }

  private void setEditComponentsAlpha (float alpha) {
    editWrap.setAlpha(alpha);
    overlayView.setAlpha(alpha * otherFactor);
    if (othersView != null) {
      othersView.setAlpha(alpha * otherFactor);
    }
    switch (currentSection) {
      case SECTION_CAPTION: {
        setBottomAlpha(alpha);
        setCheckAlpha(alpha * getAllowedCheckAlpha());
        setCounterAlpha(alpha * getAllowedCounterAlpha());
        break;
      }
      case SECTION_FILTERS: {
        filtersView.setAlpha(alpha);
        break;
      }
      case SECTION_QUALITY: {
        qualityControlWrap.setAlpha(alpha);
        break;
      }
    }
  }

  private boolean hasCaption () {
    if (captionView != null) {
      if (captionView instanceof TextView) {
        return ((TextView) captionView).getText().length() > 0;
      }
      if (captionView instanceof CustomTextView) {
        return !StringUtils.isEmpty(((CustomTextView) captionView).getText());
      }
    }
    return false;
  }

  private void setBottomAlpha (float alpha) {
    if (hasCaption() || mode == MODE_GALLERY) {
      captionWrapView.setAlpha(alpha * headerVisible.getFloatValue() * (1f - pipFactor));
    }
    if (videoSliderView != null) {
      videoSliderView.setAlpha(alpha * headerVisible.getFloatValue() * (1f - pipFactor));
    }
    if (thumbsRecyclerView != null) {
      thumbsRecyclerView.setAlpha(alpha * headerVisible.getFloatValue() * (1f - pipFactor));
    }
  }

  private void updateMainItemsAlpha () {
    setCheckAlpha(getAllowedCheckAlpha());
    setCounterAlpha(getAllowedCounterAlpha());
  }

  // Caption

  @Override
  public long provideInlineSearchChatId (InputView v) {
    return getArgumentsStrict().receiverChatId;
  }

  @Override
  public TdApi.Chat provideInlineSearchChat (InputView v) {
    long chatId = getArgumentsStrict().receiverChatId;
    if (chatId != 0) {
      return tdlib.chat(chatId);
    }
    return null;
  }

  @Override
  public long provideInlineSearchChatUserId (InputView v) {
    TdApi.Chat chat = tdlib.chat(provideInlineSearchChatId(v));
    return chat != null ? TD.getUserId(chat) : 0;
  }

  private InlineResultsWrap inlineResultsView;

  @Override
  public int provideOffset (InlineResultsWrap v) {
    final int offset = emojiLayout != null && emojiLayout.getVisibility() == View.VISIBLE && emojiLayout.getParent() != null ? emojiLayout.getMeasuredHeight() : 0;
    return (captionView.getMeasuredHeight() /*- Screen.dp(50f)*/) + offset;
  }

  @Override
  public int provideParentHeight (InlineResultsWrap v) {
    return popupView.getMeasuredHeight();
  }

  @Override
  public void showInlineResults (InputView v, ArrayList<InlineResult<?>> results, boolean isContent) {
    if (inlineResultsView == null) {
      if (results == null || results.isEmpty()) {
        return;
      }

      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
      // params.bottomMargin = Screen.dp(50f);
      inlineResultsView = new InlineResultsWrap(context()) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, popupView.getMeasuredHeight());
        }
      };
      inlineResultsView.setListener((InlineResultsWrap.PickListener) captionView);
      inlineResultsView.setAlpha(inCaptionFactor);
      inlineResultsView.setOffsetProvider(this);
      inlineResultsView.setUseDarkMode(true);
      inlineResultsView.setLayoutParams(params);
    }

    if (results != null && !results.isEmpty()) {
      for (InlineResult<?> result : results) {
        result.setForceDarkMode(true);
      }
      if (inlineResultsView.getParent() == null) {
        popupView.addView(inlineResultsView);
        // FIXME inlineResultsView.addLick(popupView);
      }
    }

    inlineResultsView.showItems(this, results, isContent, ((InputView) captionView).getInlineSearchContext(), false);
  }

  @Override
  public void addInlineResults (InputView v, ArrayList<InlineResult<?>> items) {
    inlineResultsView.addItems(this, items, null);
  }

  @Override
  public boolean canSearchInline (InputView v) {
    return true;
  }

  @Override
  public void onInputChanged (InputView v, String input) {
    if (emojiLayout != null) {
      emojiLayout.onTextChanged(input);
    }
  }

  private float inCaptionFactor;

  private void setInCaptionFactor (float factor) {
    if (this.inCaptionFactor != factor) {
      this.inCaptionFactor = factor;
      updateMainItemsAlpha();
      if (inlineResultsView != null) {
        inlineResultsView.setAlpha(factor);
      }
      captionEmojiButton.setTranslationX(-captionEmojiButton.getMeasuredWidth() * (1f - factor));
      captionEmojiButton.setAlpha(factor);
      captionDoneButton.setAlpha(factor);
      captionView.setTranslationX(-(Screen.dp(55f) - Screen.dp(14f)) * (1f - factor));
    }
  }

  private void onCaptionDone () {
    closeCaption();
  }

  private boolean inCaption;
  private FactorAnimator inCaptionAnimator;
  private static final int ANIMATOR_ID_CAPTION = 17;

  public boolean isInCaption () {
    return inCaption;
  }

  @Override
  public void onClick (MediaView mediaView, float x, float y) {
    closeCaption();
  }

  private void closeCaption () {
    if (inCaption) {
      if (emojiShown) {
        forceCloseEmojiKeyboard();
      } else {
        Keyboard.hide(captionView);
      }
    }
  }

  private EmojiLayout emojiLayout;

  private boolean emojiShown, emojiState;

  private void processEmojiClick () {
    if (emojiShown) {
      setInCaption(emojiState || getKeyboardState());
      closeEmojiKeyboard(false);
    } else {
      openEmojiKeyboard();
      setInCaption();
    }
  }

  public boolean isEmojiVisible () {
    return emojiShown;
  }

  @Override
  public void onEnterEmoji (String emoji) {
    ((InputView) captionView).onEmojiSelected(emoji);
  }

  @Override
  public long getOutputChatId () {
    return selectDelegate != null ? selectDelegate.getOutputChatId() : 0;
  }

  @Override
  public boolean isEmojiInputEmpty () {
    return StringUtils.isEmpty(((TextView) captionView).getText());
  }

  @Override
  public void onDeleteEmoji () {
    if (((TextView) captionView).getText().length() > 0) {
      captionView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
    }
  }

  @Override
  public void onSearchRequested (EmojiLayout layout, boolean areStickers) { }

  private void openEmojiKeyboard () {
    if (!emojiShown) {
      if (emojiLayout == null) {
        emojiLayout = new EmojiLayout(context());
        emojiLayout.initWithMediasEnabled(this, false, this, this, false); // FIXME shall we use dark mode?
        emojiLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        bottomWrap.addView(emojiLayout);
        popupView.getViewTreeObserver().addOnPreDrawListener(emojiLayout);
      } else if (emojiLayout.getParent() == null) {
        bottomWrap.addView(emojiLayout);
      }

      emojiState = getKeyboardState();

      emojiShown = true;
      if (emojiState) {
        captionEmojiButton.setImageResource(R.drawable.baseline_keyboard_24);
        emojiLayout.hideKeyboard((EditText) captionView);
      } else {
        captionEmojiButton.setImageResource(R.drawable.baseline_direction_arrow_down_24);
      }
    }
  }

  private void removeEmojiView () {
    if (emojiLayout != null && emojiLayout.getParent() != null) {
      ViewGroup parent = ((ViewGroup) emojiLayout.getParent());
      parent.removeView(emojiLayout);
      parent.requestLayout();
    }
  }

  private void forceCloseEmojiKeyboard () {
    if (emojiShown) {
      removeEmojiView();
      emojiShown = false;
      captionEmojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
      setInCaption();
    }
  }

  private void closeEmojiKeyboard (boolean eventually) {
    if (emojiShown) {
      if (emojiLayout != null) {
        removeEmojiView();
        if (emojiState) {
          if (eventually) {
            emojiLayout.showKeyboard((EditText) captionView);
          } else {
            emojiLayout.showKeyboard((EditText) captionView);
          }
        }
      }
      emojiShown = false;
      captionEmojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
    }
  }

  // Emoji tones

  @Override
  public int[] displayBaseViewWithAnchor (EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset) {
    return EmojiToneHelper.defaultDisplay(context, anchorView, viewToDisplay, viewWidth, viewHeight, horizontalMargin, horizontalOffset, verticalOffset, contentView, bottomWrap, emojiLayout);
  }

  @Override
  public void removeView (EmojiToneHelper context, View displayedView) {
    contentView.removeView(displayedView);
  }

  // Other

  private void checkCaptionButtonsY () {
    final int offset = emojiLayout != null && emojiLayout.getVisibility() == View.VISIBLE && emojiLayout.getParent() != null ? emojiLayout.getMeasuredHeight() : 0;
    captionEmojiButton.setTranslationY(-offset);
    captionDoneButton.setTranslationY(-offset);
    captionWrapView.setTranslationY(-offset);
    if (inlineResultsView != null) {
      inlineResultsView.updatePosition(true);
    }
    /*FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) captionWrapView.getLayoutParams();
    if (params.bottomMargin != offset) {
      params.bottomMargin = offset;
      captionWrapView.setLayoutParams(params);
    }*/
  }

  private void setInCaption () {
    setInCaption(getKeyboardState() || emojiShown);
  }

  private int getBottomWrapMargin () {
    return (inCaption ? 0 : Screen.dp(56f)) + controlsMargin;
  }

  private void setInCaption (boolean inCaption) {
    inCaption = inCaption && currentSection == SECTION_CAPTION;
    if (this.inCaption != inCaption) {
      this.inCaption = inCaption;

      if (!inCaption && !StringUtils.isEmptyOrBlank(((TextView) captionView).getText())) {
        selectMediaIfItsNot();
      }

      updateCaptionLayout();
      captionEmojiButton.setEnabled(inCaption);
      captionDoneButton.setEnabled(inCaption);
      mediaView.setDisableTouch(inCaption);
      mediaView.setButStillNeedClick(inCaption ? this : null);

      Views.setBottomMargin(bottomWrap, getBottomWrapMargin());
      editWrap.setVisibility(inCaption ? View.GONE : View.VISIBLE);
      updateSliderAlpha();
      if (this.inCaptionAnimator == null) {
        this.inCaptionAnimator = new FactorAnimator(ANIMATOR_ID_CAPTION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.inCaptionFactor);
      }
      inCaptionAnimator.animateTo(inCaption ? 1f : 0f);

      if (!inCaption) {
        UI.post(() -> {
          if (!isDestroyed() && !MediaViewController.this.inCaption) {
            setLowProfile(true);
          }
        }, 100);
      }
    }
  }

  private void applyInCaption (boolean inCaption) {
    // TODO apply margins, reset translations
  }

  private float getAllowedCheckAlpha () {
    return (1f - otherFactor) * (1f - mainSectionDisappearFactor) * (1f - inCaptionFactor) * (1f - getCameraFactor()) * (selectDelegate != null ? 1f : 0f);
  }

  private float getCameraFactor () {
    return isCurrentCamera() ? 1f : 0f;
  }

  private float getAllowedCounterAlpha () {
    return Math.max(0f, Math.min(1f, counterFactor)) * (1f - otherFactor) * (1f - mainSectionDisappearFactor) * (1f - inCaptionFactor) * (1f - getCameraFactor());
  }

  private void showCurrentCell () {
    if (hiddenCell != null && delegate != null) {
      delegate.setMediaItemVisible(hiddenCellIndex, hiddenCell, true);
    }
    hiddenCell = null;
  }

  private int hiddenCellIndex;
  private MediaItem hiddenCell;

  private void hideCurrentCell () {
    MediaItem currentItem = stack.getCurrent();
    if (hiddenCell == null || hiddenCell != currentItem) {
      if (hiddenCell != null && delegate != null) {
        delegate.setMediaItemVisible(hiddenCellIndex, hiddenCell, true);
      }
      hiddenCell = currentItem;
      hiddenCellIndex = stack.getCurrentIndex();
      if (delegate != null) {
        delegate.setMediaItemVisible(hiddenCellIndex, hiddenCell, false);
      }
    }
  }

  private void setAnimatorType (int type, boolean isOpen) {
    mediaView.setDisableAnimations(isOpen);
    this.revealAnimationType = type;
    switch (revealAnimationType) {
      case ANIMATION_TYPE_FADE: {
        revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        revealAnimator.setDuration(mode != MODE_SECRET && !animationAlreadyDone ? 180l : 0l);
        break;
      }
      case ANIMATION_TYPE_CAMERA:
        revealAnimator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
        revealAnimator.setDuration(0);
        break;
      case ANIMATION_TYPE_SECRET_CLOSE: {
        revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        revealAnimator.setDuration(220l);
        break;
      }
      case ANIMATION_TYPE_PIP_CLOSE: {
        revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        revealAnimator.setDuration(190l);
        break;
      }
      case ANIMATION_TYPE_REVEAL: {
        if ((mode == MODE_GALLERY || isOpen) && !isFromCamera) {
          //revealAnimator.setInterpolator(isOpen ? OVERSHOOT_INTERPOLATOR : OVERSHOOT_INTERPOLATOR_2);
          //revealAnimator.setDuration(280l);
          revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          revealAnimator.setDuration(REVEAL_OPEN_ANIMATION_DURATION);
        } else {
          revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          revealAnimator.setDuration(REVEAL_ANIMATION_DURATION);
        }
        break;
      }
      case ANIMATION_TYPE_SLIDEOFF: {
        // TODO
        break;
      }
    }
  }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    if (revealAnimationType == ANIMATION_TYPE_REVEAL) {
      mediaView.setPendingOpenAnimator(revealAnimator);
    } else {
      revealAnimator.animateTo(1f);
    }
  }

  private void setLowProfile (boolean isLowProfile) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (isLowProfile) {
        if (mode == MODE_SECRET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Config.CUTOUT_ENABLED) {
          context().setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE, true);
        } else {
          context().setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, true);
        }
      } else {
        context().setWindowDecorSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE, true);
      }
    }
  }

  private void setFullScreen (boolean isFullscreen) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (mode != MODE_GALLERY && Config.CUTOUT_ENABLED) {
        if (isFullscreen && (mode == MODE_MESSAGES || mode == MODE_SIMPLE)) {
          ViewController<?> c = context.navigation().getCurrentStackItem();
          if (c != null) {
            c.hideSoftwareKeyboard();
          }
        }
        if (isFullscreen) {
          context().addFullScreenView(this, true);
        } else {
          context().removeFullScreenView(this, true);
        }
      }
    }
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    if (mode == MODE_GALLERY) {
      if (visible && !getKeyboardState()) {
        closeEmojiKeyboard(true);
      }
      boolean res = super.onKeyboardStateChanged(visible);
      if (emojiLayout != null) {
        emojiLayout.onKeyboardStateChanged(visible);
      }
      setInCaption(visible || emojiShown);
      mediaView.layoutCells();
      return res;
    }
    return super.onKeyboardStateChanged(visible);
  }

  private int forceAnimationType = -1;

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator ignored) {
    MediaViewThumbLocation location;

    if (forceAnimationType != -1) {
      location = null;
    } else {
      location = getCurrentTargetLocation();
    }

    if (mode != MODE_SECRET || forceAnimationType == -1) {
      setLowProfile(false);
    }

    revealAnimator.cancel();
    currentThumb = null;

    /*if (revealAnimator.getFactor() == 0f) {
      popup.onCustomHideAnimationComplete();
      return true;
    }*/

    currentThumb = location;
    int animationType = forceAnimationType != -1 ? forceAnimationType : location != null ? ANIMATION_TYPE_REVEAL : ANIMATION_TYPE_FADE;

    if (animationType != ANIMATION_TYPE_PIP_CLOSE) {
      mediaView.setTarget(location, 1f);
    }

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && revealAnimationType != ANIMATION_TYPE_PIP_CLOSE && !Config.DISABLE_VIEWER_ELEVATION) {
      mediaView.setTranslationZ(0);
      mediaView.invalidateOutline();
    }

    switch (animationType) {
      case ANIMATION_TYPE_REVEAL: {
        mediaView.getBaseCell().getDetector().preparePositionReset();
        hideCurrentCell();
        setAnimatorType(ANIMATION_TYPE_REVEAL, false);
        break;
      }
      case ANIMATION_TYPE_FADE:
      case ANIMATION_TYPE_SECRET_CLOSE: {
        setAnimatorType(animationType, false);
        mediaView.setAlpha(1f);
        break;
      }
      case ANIMATION_TYPE_PIP_CLOSE: {
        setAnimatorType(ANIMATION_TYPE_PIP_CLOSE, false);
        break;
      }
    }

    if (animationType == ANIMATION_TYPE_SECRET_CLOSE) {
      revealAnimator.setStartDelay(revealAnimator.getFactor() == 0f ? 0l : 70l);
    } else if (revealAnimationType != ANIMATION_TYPE_PIP_CLOSE) {
      revealAnimator.setStartDelay(100);
    }
    revealAnimator.animateTo(0f);

    return true;
  }

  private float fadeFactor;

  private void setFadeFactor (float factor) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;
      mediaView.setAlpha(Math.max(0f, Math.min(1f, factor)));
    }
  }

  private Path path = new Path();

  private void layoutPath () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null && currentThumb != null && commonFactor > 0f && commonFactor < 1f) {

      // v1
      float factor = Math.max(0f, Math.min(1f, commonFactor));

      int fromX = currentThumb.centerX();
      // int fromY = currentThumb.centerY();

      int width = contentView.getMeasuredWidth();
      int height = contentView.getMeasuredHeight();
      /*int targetX = width / 2;
      int targetY = height / 2;*/

      Receiver receiver = mediaView.getBaseReceiver();

      /*int imageWidth = receiver.getRight() - receiver.getLeft();
      int imageHeight = receiver.getBottom() - receiver.getTop();*/

      float startRadius = Math.min(currentThumb.width(), currentThumb.height()) / 2; //(float) Math.sqrt(imageWidth * imageWidth + imageHeight * imageHeight) * .5f;
      float radius = (float) Math.sqrt(width * width + height * height) * .5f;

      /*float centerX = fromX + (float) (targetX - fromX) * factor;
      float centerY = fromY + (float) (targetY - fromY) * factor;*/
      float targetRadius = startRadius + (radius - startRadius) * factor;


      RectF rectF = Paints.getRectF();
      rectF.set(receiver.centerX() - targetRadius, receiver.centerY() - targetRadius, receiver.centerX() + targetRadius, receiver.centerY() + targetRadius);

      path.reset();
      path.addRoundRect(rectF, targetRadius, targetRadius, Path.Direction.CCW);
    }
  }

  private float commonFactor;

  private void updatePhotoRevealFactor () {
    float factor = commonFactor * (1f - pipFactor);
    context.setPhotoRevealFactor(factor);
  }

  private void setCommonFactor (float factor) {
    if (this.commonFactor != factor) {
      if (Float.isNaN(factor))
        throw new IllegalArgumentException();
      this.commonFactor = factor;
      updatePhotoRevealFactor();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null && currentThumb != null && commonFactor > 0f && commonFactor < 1f) {
        layoutPath();
      }
      contentView.setWillNotDraw(factor == 0f);
      contentView.invalidate();
      checkThumbsItemAnimator();
    }
  }

  public void onMediaZoomStart () {
    if (mode == MODE_GALLERY && currentSection == SECTION_PAINT) {
      paintView.getContentWrap().cancelDrawingByZoom();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_REVEAL: {
        switch (revealAnimationType) {
          case ANIMATION_TYPE_FADE: {
            setFadeFactor(factor);
            break;
          }
          case ANIMATION_TYPE_SECRET_CLOSE: {
            if (secretView != null) {
              secretView.setAlpha(factor);
            }
            // mediaView.forceZoom(.75f + .25f * factor);
            mediaView.setAlpha(factor);
            break;
          }
          case ANIMATION_TYPE_REVEAL: {
            mediaView.getBaseCell().getDetector().setPositionFactor(fraction);
            mediaView.setRevealFactor(factor);
            break;
          }
          case ANIMATION_TYPE_PIP_CLOSE: {
            setPipDismissFactor(factor);
            break;
          }
          case ANIMATION_TYPE_SLIDEOFF: {
            // TODO
            break;
          }
        }
        setCommonFactor(factor);
        setHeaderAlpha(MathUtils.clamp(factor));
        switch (mode) {
          case MODE_GALLERY: {
            float alpha = Math.max(0f, Math.min(1f, factor));
            setEditComponentsAlpha(alpha);
            break;
          }
          case MODE_MESSAGES:
          case MODE_SIMPLE: {
            setBottomAlpha(MathUtils.clamp(factor));
            break;
          }
        }
        break;
      }
      case ANIMATOR_COUNTER: {
        setCounterFactor(factor);
        break;
      }
      case ANIMATOR_OTHER: {
        setOtherFactor(factor);
        break;
      }
      case ANIMATOR_SECTION: {
        setSectionChangeFactor(factor, fraction);
        break;
      }
      case ANIMATOR_VIDEO: {
        setVideoFactor(factor);
        break;
      }
      case ANIMATOR_CAPTION: {
        setCaptionFactor(factor);
        break;
      }
      case ANIMATOR_HEADER: {
        setHeaderVisibilityFactor(factor);
        break;
      }
      case ANIMATOR_PIP: {
        setPipFactor(factor, fraction);
        break;
      }
      case ANIMATOR_PIP_UP: {
        setPipUpFactor(factor);
        break;
      }
      case ANIMATOR_PIP_CONTROLS: {
        setPipControlsFactor(factor);
        break;
      }
      case ANIMATOR_PIP_POSITION: {
        setPipPositionFactor(factor);
        break;
      }
      case ANIMATOR_PIP_HIDE: {
        setPipHideFactor(factor);
        break;
      }
      case ANIMATOR_PRIVACY: {
        mediaView.getBaseReceiver().setAlpha(1f - factor);
        break;
      }
      case ANIMATOR_SLIDE: {
        setSlideFactor(factor);
        break;
      }
      case ANIMATOR_ID_CAPTION: {
        setInCaptionFactor(factor);
        break;
      }
      case ANIMATOR_CROP: {
        setCropFactor(factor);
        break;
      }
      case ANIMATOR_IMAGE_ROTATE: {
        setImageRotateFactor(factor);
        break;
      }
      case ANIMATOR_PAINT_HIDE: {
        setHidePaint(factor);
        break;
      }
      case ANIMATOR_THUMBS: {
        setThumbsFactor(factor);
        break;
      }
      case ANIMATOR_THUMBS_SCROLL: {
        setThumbsScrollFactor(factor);
        break;
      }
      case ANIMATOR_THUMBS_AUTO_SCROLLER: {
        setAutoThumbScrollFactor(factor);
        break;
      }
    }
  }

  private void onAppear () {
    // Nothing to do?
  }

  private void onHide () {
    // Nothing to do anymore?
  }

  private static final boolean SET_FULLSCREEN_ON_OPEN = true;

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_REVEAL: {
        if (finalFactor == 0f) {
          showCurrentCell();
          popupView.onCustomHideAnimationComplete();
          setFullScreen(false);
          if (mode == MODE_SECRET && revealAnimationType == ANIMATION_TYPE_SECRET_CLOSE) {
            setLowProfile(false);
          }
          onHide();
        } else if (finalFactor == 1f) {
          popupView.onCustomShowComplete();
          mediaView.setDisableAnimations(false);
          if (!SET_FULLSCREEN_ON_OPEN) {
            setFullScreen(true);
          }
          mediaView.autoplayIfNeeded(false);
          if (mode == MODE_SECRET) {
            final MediaItem secretItem = stack.getCurrent();
            UI.post(() -> secretItem.viewSecretContent(), 20);
          }
          if (canSendAsFile() != SEND_MODE_NONE && Settings.instance().needTutorial(Settings.TUTORIAL_SEND_AS_FILE)) {
            Settings.instance().markTutorialAsShown(Settings.TUTORIAL_SEND_AS_FILE);
            context().tooltipManager().builder(sendButton).color(context().tooltipManager().overrideColorProvider(getForcedTheme())).locate((targetView, outRect) -> {
              outRect.top += Screen.dp(8f);
              outRect.bottom -= Screen.dp(8f);
              outRect.left -= Screen.dp(4f);
              outRect.right -= Screen.dp(4f);
            }).controller(this).show(tdlib, R.string.HoldToSendAsFile).hideDelayed();
          }
        }
        break;
      }
      case ANIMATOR_OTHER: {
        if (finalFactor == 0f) {
          otherAdapter.setImages(null);
        }
        break;
      }
      case ANIMATOR_SECTION: {
        applySection();
        break;
      }
      case ANIMATOR_CAPTION: {
        if (finalFactor == 0f) {
          setCaption("", null);
        }
        break;
      }
      case ANIMATOR_PIP: {
        applyPipMode();
        break;
      }
      case ANIMATOR_PIP_POSITION: {
        applyPipPosition();
        break;
      }
      case ANIMATOR_PRIVACY: {
        /*forceAnimationType = ANIMATION_TYPE_SECRET_CLOSE;
        ((PopupLayout) getWrap()).hideWindow(true);*/
        break;
      }
      case ANIMATOR_SLIDE: {
        if (finalFactor == 1f && (toSlideY != 0f || toSlideX != 0f)) {
          applySlide();
        }
        break;
      }
      case ANIMATOR_ID_CAPTION: {
        applyInCaption(finalFactor == 1f);
        break;
      }
      case ANIMATOR_CROP: {
        applyCropMode(finalFactor == 1f);
        break;
      }
      case ANIMATOR_IMAGE_ROTATE: {
        applyImageRotation();
        break;
      }
      case ANIMATOR_THUMBS: {
        if (finalFactor == 0f) {
          clearThumbsView();
        }
        break;
      }
    }
  }

  private void setCaption (String text, TextEntity[] entities) {
    if (captionView instanceof TextView) {
      ((TextView) captionView).setText(text);
    } else if (captionView instanceof CustomTextView) {
      ((CustomTextView) captionView).setText(text, entities, false);
    }
  }

  private void updateCaption (boolean animated) {
    MediaItem item = stack.getCurrent();
    switch (mode) {
      case MODE_GALLERY: {
        ignoreCaptionUpdate = true;
        TdApi.FormattedText caption = item.getCaption();
        if (caption != null) {
          ((InputView) captionView).setInput(TD.toCharSequence(caption), true, false);
        } else {
          ((InputView) captionView).setInput("", true, false);
        }
        ignoreCaptionUpdate = false;
        break;
      }
      case MODE_MESSAGES:
      case MODE_SIMPLE: {
        boolean isVisible = item.getCaption() != null;
        if (isVisible) {
          ((CustomTextView) captionView).setText(item.getCaption().text, item.getCaptionEntities(), false);
          if (!animated && !this.isCaptionVisible) {
            this.isCaptionVisible = true;
            this.captionFactor = 1f;
          }
        }
        setCaptionVisible(isVisible, animated);
        break;
      }
    }
  }

  private boolean isCaptionVisible;
  private float captionFactor;
  private FactorAnimator captionAnimator;
  private static final int ANIMATOR_CAPTION = 6;

  private void setCaptionVisible (boolean isVisible, boolean animated) {
    if (this.isCaptionVisible != isVisible) {
      this.isCaptionVisible = isVisible;
      if (animated) {
        animateCaptionFactor(isVisible ? 1f : 0f);
      } else {
        forceCaptionFactor(isVisible ? 1f : 0f);
      }
    }
  }

  private static final long BOTTOM_ANIMATION_DURATION = 150l;

  private void animateCaptionFactor (float toFactor) {
    if (captionAnimator == null) {
      captionAnimator = new FactorAnimator(ANIMATOR_CAPTION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, BOTTOM_ANIMATION_DURATION, captionFactor);
    }
    captionAnimator.animateTo(toFactor);
  }

  private void forceCaptionFactor (float factor) {
    if (captionAnimator != null) {
      captionAnimator.forceFactor(factor);
    }
    setCaptionFactor(factor);
  }

  private void setCaptionFactor (float factor) {
    if (this.captionFactor != factor) {
      this.captionFactor = factor;
      captionWrapView.setAlpha(factor * headerVisible.getFloatValue());
    }
  }

  private void toggleMute () {
    if (Config.MUTE_VIDEO_AVAILABLE) {
      boolean isMuted = stack.getCurrent().toggleMute();
      paintOrMuteButton.setActive(isMuted, true);
      mediaView.getBaseCell().updateMute();
      updateQualityInfo();
    } else {
      UI.showApiLevelWarning(Build.VERSION_CODES.JELLY_BEAN_MR2);
    }
  }

  private void updateVideoState (boolean animated) {
    MediaItem item = stack.getCurrent();
    setVideoVisible(item.isVideo(), Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE || item.isLoaded(), item.getVideoDuration(true, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS, animated);
    if (mode == MODE_GALLERY) {
      boolean isVideo = item.isVideo();
      if (isVideo) {
        adjustOrTextButton.setIcon(R.drawable.baseline_settings_24, animated, false);
        cropOrStickerButton.setIcon(R.drawable.baseline_rotate_90_degrees_ccw_24, animated, false);
        paintOrMuteButton.setIcon(R.drawable.baseline_volume_up_24, animated, item.needMute());
      } else {
        adjustOrTextButton.setIcon(R.drawable.baseline_tune_24, animated, false);
        cropOrStickerButton.setIcon(R.drawable.baseline_crop_rotate_24, animated, false);
        paintOrMuteButton.setIcon(R.drawable.baseline_brush_24, animated, false);
      }
      // paintButton.setVisibility(isVideo ? View.GONE : View.VISIBLE);
      // setHidePaint(isVideo, animated);
    }
  }

  private static final int ANIMATOR_PAINT_HIDE = 20;
  private boolean hidePaint;
  private BoolAnimator hideAnimator;

  private void setHidePaint (boolean hidePaint, boolean animated) {
    if (this.hidePaint != hidePaint) {
      this.hidePaint = hidePaint;
      if (hideAnimator == null) {
        hideAnimator = new BoolAnimator(ANIMATOR_PAINT_HIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 290l);
      } else {
        hideAnimator.setDuration(hidePaint ? 290 : 380);
      }
      hideAnimator.setValue(hidePaint, animated);
    }
  }

  private void setHidePaint (float factor) {
    paintOrMuteButton.setAlpha(1f - factor);
    float scale = .6f + .4f * (1f - factor);
    paintOrMuteButton.setScaleX(scale);
    paintOrMuteButton.setScaleY(scale);
    int width = Views.getParamsWidth(paintOrMuteButton);
    boolean found = false;
    for (int i = 0; i < editButtons.getChildCount(); i++) {
      View view = editButtons.getChildAt(i);
      if (view == paintOrMuteButton) {
        found = true;
      } else if (found) {
        view.setTranslationX(-width / 2 * factor);
      } else {
        view.setTranslationX(width / 2 * factor);
      }
    }
  }

  // Current photo changed

  @Override
  public void onMediaChanged (int index, int estimatedTotalSize, MediaItem currentItem, boolean itemCountChanged) {
    switch (mode) {
      case MODE_GALLERY: {
        checkView.setChecked(selectDelegate != null && selectDelegate.isMediaItemSelected(index, currentItem));
        updateIconStates(true);
        updateVideoState(true);
        updateCaption(true);

        // TODO set styles

        break;
      }
      case MODE_MESSAGES:
      case MODE_SIMPLE: {
        if (!itemCountChanged) {
          updateVideoState(true);
        }
        updateCaption(true);

        updateHeaderButtons();
        onMediaStackChanged(itemCountChanged);

        loadMoreIfNeeded();

        break;
      }
      case MODE_PROFILE:
      case MODE_CHAT_PROFILE: {
        onMediaStackChanged(itemCountChanged);
        loadMoreIfNeeded();
        break;
      }
    }
  }

  // Controller-related stuff

  @Override
  protected int getHeaderColorId () {
    return ColorId.transparentEditor;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.white;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.white;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_photo;
  }

  @Override
  protected int getBackButtonResource () {
    return R.drawable.bg_btn_header_light;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private boolean canGoPip () {
    MediaItem current = stack.getCurrent();
    return current != null && current.isVideo() && !current.isGifType();
  }

  private boolean canShare () {
    return false;
  }

  private boolean canViewMasks () {
    return false;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (Config.MASKS_TEXTS_AVAILABLE) {
      HeaderButton masksButton = header.genButton(R.id.menu_btn_masks, R.drawable.deproko_baseline_masks_24, ColorId.white, null, Screen.dp(49f), header);
      masksButton.setBackgroundResource(R.drawable.bg_btn_header_light);
      masksButton.setVisibility(canViewMasks() ? View.VISIBLE : View.GONE);
      menu.addView(masksButton);
    }

    HeaderButton pipButton = header.genButton(R.id.menu_btn_pictureInPicture, R.drawable.deproko_baseline_outinline_24, ColorId.white, null, Screen.dp(49f), header);
    pipButton.setBackgroundResource(R.drawable.bg_btn_header_light);
    pipButton.setVisibility(canGoPip() ? View.VISIBLE : View.GONE);
    menu.addView(pipButton);

    HeaderButton shareButton = header.addForwardButton(menu, null, ColorId.white);
    shareButton.setBackgroundResource(R.drawable.bg_btn_header_light);
    shareButton.setVisibility(canShare() ? View.VISIBLE : View.GONE);

    HeaderButton moreButton = header.addMoreButton(menu, null, ColorId.white);
    moreButton.setBackgroundResource(R.drawable.bg_btn_header_light);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_pictureInPicture) {
      enterPictureInPicture();
    } else if (id == R.id.menu_btn_forward) {
      // ...
    } else if (id == R.id.menu_btn_more) {
      IntList ids = new IntList(4);
      StringList strings = new StringList(4);

      MediaItem item = stack.getCurrent();

      TdApi.Chat chat = tdlib.chat(item.getSourceChatId());

      if (item.isLoaded() && item.canBeSaved()) {
        if ((item.isVideo() && !item.isGifType()) || (getArgumentsStrict().forceOpenIn)) {
          ids.append(R.id.btn_open);
          strings.append(R.string.OpenInExternalApp);
        }
        ids.append(R.id.btn_saveToGallery);
        strings.append(R.string.SaveToGallery);
      }

      if (mode != MODE_SECRET && mode != MODE_GALLERY && item.canBeSaved() && item.canBeShared()) {
        ids.append(R.id.btn_share);
        strings.append(R.string.Share);
      }

      if (item.isGifType() && item.canBeSaved()) {
        ids.append(R.id.btn_saveGif);
        strings.append(R.string.SaveGif);
      }

      if (!StringUtils.isEmpty(getArgumentsStrict().copyLink) || (chat != null && tdlib.canCopyPostLink(item.getMessage()))) {
        ids.append(R.id.btn_copyLink);
        strings.append(R.string.CopyLink);
      }

      if (item.getSourceChatId() != 0 && item.getSourceMessageId() != 0 && mode == MODE_MESSAGES) {
        ids.append(R.id.btn_showInChat);
        strings.append(R.string.ShowInChat);
      }

      if (item.canBeReported() && (item.getMessage() != null || stack.getCurrentIndex() == 0)) {
        ids.append(R.id.btn_messageReport);
        strings.append(R.string.Report);
      }

      boolean isSelfProfile = mode == MODE_PROFILE && tdlib.isSelfSender(item.getSourceSender());
      boolean canDelete = isSelfProfile;
      if (!canDelete && mode == MODE_CHAT_PROFILE) {
        canDelete = chat != null && tdlib.canChangeInfo(chat);
      }
      if (isSelfProfile && stack.getCurrentIndex() != 0) {
        ids.append(R.id.btn_setProfilePhoto);
        strings.append(R.string.SetAsCurrent);
      }
      if (canDelete) {
        ids.append(R.id.btn_deleteProfilePhoto);
        strings.append(R.string.Delete);
      }

      if (!ids.isEmpty()) {
        showMore(ids.get(), strings.get(), 0, canRunFullscreen());
      }
    } else if (id == R.id.menu_btn_masks) {
    }
  }
  
  private ThemeDelegate getForcedTheme () { // TODO actually move this to ViewController?
    return ThemeSet.getBuiltinTheme(ThemeId.NIGHT_BLACK);
  }

  @Override
  public boolean shouldDisallowScreenshots () {
    return mode == MODE_SECRET || !stack.getCurrent().canBeSaved();
  }

  @Override
  public void onMoreItemPressed (int id) {
    MediaItem item = stack.getCurrent();
    if (id == R.id.btn_saveToGallery) {
      TdApi.File file = item.getTargetFile();
      tdlib.files().isFileLoadedAndExists(file, isLoadedAndExists -> {
        if (isLoadedAndExists) {
          runOnUiThreadOptional(() -> {
            U.copyToGallery(context, file.local.path, item.isAnimatedAvatar() || item.isGifType() ? U.TYPE_GIF : item.isVideo() ? U.TYPE_VIDEO : U.TYPE_PHOTO);
          });
        }
      });
    } else if (id == R.id.btn_saveGif) {
      TdApi.File file = item.getTargetFile();
      if (file != null) {
        tdlib.ui().saveGif(file.id);
      }
    } else if (id == R.id.btn_messageReport) {
      TdApi.Message message = item.getMessage();
      if (message != null) {
        TdlibUi.reportChat(this, item.getSourceChatId(), new TdApi.Message[] {message}, null, getForcedTheme());
      } else {
        final long chatId = Td.getSenderId(item.getSourceSender());
        final RunnableData<TdApi.PhotoSize> act = (photoSize) -> {
          if (photoSize != null) {
            tdlib.ui().post(() ->
              TdlibUi.reportChatPhoto(this, chatId, photoSize.photo.id, null, getForcedTheme())
            );
          }
        };
        switch (ChatId.getType(chatId)) {
          case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
            tdlib.cache().basicGroupFull(ChatId.toBasicGroupId(chatId), groupFull -> {
              if (groupFull != null && groupFull.photo != null) {
                act.runWithData(Td.findBiggest(groupFull.photo.sizes));
              }
            });
            break;
          }
          case TdApi.ChatTypePrivate.CONSTRUCTOR:
          case TdApi.ChatTypeSecret.CONSTRUCTOR: {
            final long userId = tdlib.chatUserId(chatId);
            tdlib.cache().userFull(userId, userFull -> {
              if (userFull != null && userFull.photo != null) {
                act.runWithData(Td.findBiggest(userFull.photo.sizes));
              }
            });
            break;
          }
          case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
            tdlib.cache().supergroupFull(ChatId.toSupergroupId(chatId), supergroupFull -> {
              if (supergroupFull != null && supergroupFull.photo != null) {
                act.runWithData(Td.findBiggest(supergroupFull.photo.sizes));
              }
            });
            break;
          }
        }
      }
    } else if (id == R.id.btn_copyLink) {
      if (!StringUtils.isEmpty(getArgumentsStrict().copyLink)) {
        UI.copyText(getArgumentsStrict().copyLink, R.string.CopiedLink);
      } else if (item.getSourceChatId() != 0) {
        if (tdlib.canCopyPostLink(item.getMessage())) {
          tdlib.getMessageLink(item.getMessage(), false, messageThreadId != 0, link -> UI.copyText(link.url, link.isPublic ? R.string.CopiedLink : R.string.CopiedLinkPrivate));
        }
      }
    } else if (id == R.id.btn_open) {
      if (item.getSourceVideo() != null) {
        TdApi.Video video = item.getSourceVideo();
        U.openFile(this, video);
      } else if (item.getSourceDocument() != null) {
        TdApi.Document document = item.getSourceDocument();
        U.openFile(this, document.fileName, new File(document.document.local.path), document.mimeType, 0);
      }
    } else if (id == R.id.btn_share) {
      ShareController c;
      if (item.getMessage() != null) {
        c = new ShareController(context, tdlib);
        if (item.getMessage().content.getConstructor() != TdApi.MessageText.CONSTRUCTOR) {
          c.setArguments(new ShareController.Args(item.getMessage()));
        } else {
          TdApi.WebPage webPage = ((TdApi.MessageText) item.getMessage().content).webPage;
          c.setArguments(new ShareController.Args(item, webPage.displayUrl, webPage.displayUrl));
        }
      } else if (item.getShareFile() != null) {
        c = new ShareController(context, tdlib);
        CharSequence caption = null, exportCaption = null;
        switch (mode) {
          case MODE_PROFILE: {
            long userId = Td.getSenderUserId(stack.getCurrent().getSourceSender());
            String userName = tdlib.cache().userName(userId);
            if (!StringUtils.isEmpty(userName)) {
              exportCaption = Lang.getString(R.string.ShareTextProfile, userName);
            }
            String username = tdlib.cache().userUsername(userId);
            if (!StringUtils.isEmpty(username)) {
              exportCaption = Lang.getString(R.string.format_ShareTextSignature, exportCaption, tdlib.tMeUrl(username));
            }
            break;
          }
          case MODE_CHAT_PROFILE: {
            long chatId = stack.getCurrent().getSourceChatId();
            String chatTitle = tdlib.chatTitle(chatId);
            if (!StringUtils.isEmpty(chatTitle)) {
              if (tdlib.isChannel(chatId)) {
                exportCaption = Lang.getString(R.string.ShareTextChannel, chatTitle);
              } else {
                exportCaption = Lang.getString(R.string.ShareTextChat, chatTitle);
              }
              String username = tdlib.chatUsername(chatId);
              if (!StringUtils.isEmpty(username)) {
                exportCaption = Lang.getString(R.string.format_ShareTextSignature, exportCaption, tdlib.tMeUrl(username));
              }
            }
            break;
          }
          case MODE_SIMPLE: {
            caption = exportCaption = Td.isEmpty(item.getCaption()) ? null : TD.toCharSequence(item.getCaption());
            break;
          }
        }
        c.setArguments(new ShareController.Args(item, caption, exportCaption));
      } else {
        return;
      }

      c.show();

      forceAnimationType = ANIMATION_TYPE_FADE;
      close();
    } else if (id == R.id.btn_showInChat) {
      forceAnimationType = ANIMATION_TYPE_FADE;

      ViewController<?> c = context.navigation().getCurrentStackItem();
      if (c instanceof MessagesController && c.getChatId() == item.getSourceChatId() && ((MessagesController) c).getMessageThreadId() == messageThreadId) {
        ((MessagesController) c).highlightMessage(new MessageId(item.getSourceChatId(), item.getSourceMessageId()));
      } else {
        tdlib.ui().openMessage(this, item.getSourceChatId(), new MessageId(item.getSourceChatId(), item.getSourceMessageId()), null);
      }

      close();
    } else if (id == R.id.btn_setProfilePhoto) {
      final long photoId = item.getPhotoId();
      tdlib.client().send(new TdApi.SetProfilePhoto(new TdApi.InputChatPhotoPrevious(photoId), false), tdlib.okHandler());
      close();
    } else if (id == R.id.btn_deleteProfilePhoto) {
      if (mode == MODE_PROFILE) {
        tdlib.client().send(new TdApi.DeleteProfilePhoto(item.getPhotoId()), tdlib.okHandler());
      } else if (mode == MODE_CHAT_PROFILE) {
        tdlib.client().send(new TdApi.SetChatPhoto(item.getSourceChatId(), null), tdlib.okHandler());
      }
      forceAnimationType = ANIMATION_TYPE_FADE;
      close();
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private int measureButtonsPadding () {
    int width = Screen.dp(49f);
    if (canShare()) {
      width += Screen.dp(49f);
    }
    if (canGoPip()) {
      width += Screen.dp(49f);
    }
    if (canViewMasks()) {
      width += Screen.dp(49f);
    }
    return width;
  }

  private void updateHeaderButtons () {
    if (headerView != null) {
      headerView.updateButton(R.id.menu_photo, R.id.menu_btn_masks, canViewMasks() ? View.VISIBLE : View.GONE, 0);
      headerView.updateButton(R.id.menu_photo, R.id.menu_btn_pictureInPicture, canGoPip() ? View.VISIBLE : View.GONE, 0);
      headerView.updateButton(R.id.menu_photo, R.id.menu_btn_forward, canShare() ? View.VISIBLE : View.GONE, 0);
      updateTitleMargins();
    }
  }

  private void updateTitleMargins () {
    if (headerCell != null) {
      int rightMargin = measureButtonsPadding();
      int leftMargin = Screen.dp(68f);
      if (Views.setMargins((FrameLayout.LayoutParams) headerCell.getLayoutParams(), Lang.rtl() ? rightMargin : leftMargin, headerView.needOffsets() ? HeaderView.getTopOffset() : 0, Lang.rtl() ? leftMargin : rightMargin, 0)) {
        Views.updateLayoutParams(headerCell);
      }
    }
  }

  private boolean toggleHeaderVisibility () {
    if (headerView != null) {
      boolean isVisible = headerVisible.toggleValue(true);
      // FIXME: currently there is a "jump" (by the height of navigation bar) effect upon entering/leaving full screen mode
      // In order to properly fix it:
      // 1. Bug in third-party dependency has to be fixed (caused by SubsamplingScaleImageView.java:1435-1436)
      // 2. MediaViewController.dispatchInnerMargins should start passing non-zero values to MediaView
      // 3. MediaCellView.setOffsets should start properly handling non-zero parameters (currently some of them are unsupported)
      // For now, it is considered that having proper full-screen mode is more important
      // than not seeing this visual "glitch".
      //
      // Leaving this comment for whoever going to invest time to properly resolve this issue in the future.
      if (isVisible) {
        context().removeHideNavigationView(this);
      } else {
        context().addHideNavigationView(this);
      }
      return true;
    }
    return false;
  }

  private static final int ANIMATOR_HEADER = 7;
  private final BoolAnimator headerVisible = new BoolAnimator(ANIMATOR_HEADER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, BOTTOM_ANIMATION_DURATION, true);

  private void updateCaptionAlpha () {
    if (captionWrapView != null) {
      float alpha = captionFactor * headerVisible.getFloatValue() * (1f - pipFactor);
      captionWrapView.setAlpha(alpha);
    }
  }

  private void updateSliderAlpha () {
    if (videoSliderView != null) {
      float alpha = headerVisible.getFloatValue() * (1f - pipFactor) * (inCaption ? 0f : 1f);
      videoSliderView.setAlpha(alpha);
    }
  }

  private float headerAlpha;

  private void setHeaderAlphaImpl (float alpha) {
    if (this.headerAlpha != alpha) {
      this.headerAlpha = alpha;
      updateThumbsAlpha();
    }
  }

  private void updateThumbsAlpha () {
    if (thumbsRecyclerView != null) {
      thumbsRecyclerView.setAlpha(headerAlpha * headerVisible.getFloatValue() * (1f - pipFactor));
    }
  }

  private void updateHeaderAlpha () {
    float alpha = MathUtils.clamp(headerVisible.getFloatValue() * (1f - pipFactor));
    if (headerView != null) {
      headerView.setAlpha(alpha);
    }
    /*if (bottomPaddingView != null) {
      bottomPaddingView.setAlpha(alpha);
    }*/
  }

  private void setHeaderVisibilityFactor (float factor) {
    updateHeaderAlpha();
    updateCaptionAlpha();
    updateSliderAlpha();
    updateThumbsAlpha();
  }

  private void onMediaStackChanged (boolean itemCountChanged) {
    switch (mode) {
      case MODE_MESSAGES:
      case MODE_SIMPLE: {
        String currentIndex = Strings.buildCounter(stack.getEstimatedIndex() + 1);
        String totalIndex = Strings.buildCounter(stack.getEstimatedSize());
        if (getArgumentsStrict().noLoadMore && stack.getEstimatedSize() == 1) {
          if (stack.getCurrent().isVideo()) {
            headerCell.setTitle(R.string.Video);
          } else if (stack.getCurrent().isGif()) {
            headerCell.setTitle(R.string.Gif);
          } else {
            headerCell.setTitle(R.string.Photo);
          }
        } else {
          headerCell.setTitle(Lang.getString(R.string.XofY, currentIndex, totalIndex));
        }
        headerCell.setSubtitle(genSubtitle());
        break;
      }
      case MODE_PROFILE:
      case MODE_CHAT_PROFILE: {
        headerCell.setSubtitle(genSubtitle());
        break;
      }
    }
    if (!itemCountChanged) {
      checkNeedThumbs();
    }
  }

  private String getXofY () {
    String currentIndex = Strings.buildCounter(stack.getEstimatedIndex() + 1);
    String totalIndex = Strings.buildCounter(stack.getEstimatedSize());
    return Lang.getString(R.string.XofY, currentIndex, totalIndex);
  }

  private void initHeaderStyle () {
    if (headerView != null) {
      if (revealAnimationType == ANIMATION_TYPE_REVEAL) {
        headerView.setTranslationY(-HeaderView.getSize(headerView.needOffsets()));
      } else {
        headerView.setAlpha(0f);
      }
      setHeaderAlphaImpl(0f);
    }
  }

  private void setHeaderAlpha (float alpha) {
    setHeaderAlphaImpl(alpha);
    switch (revealAnimationType) {
      case ANIMATION_TYPE_REVEAL: {
        if (headerView != null) {
          headerView.setTranslationY(-HeaderView.getSize(headerView.needOffsets()) * (1f - alpha));
        }
        break;
      }
      case ANIMATION_TYPE_FADE: {
        if (headerView != null) {
          headerView.setAlpha(alpha);
        }
        break;
      }
    }
  }

  private String getAuthorText (MediaItem item) {
    if (item.getSourceSender() != null) {
      return tdlib.senderName(item.getSourceSender());
    } else if (item.getSourceChatId() != 0) {
      TdApi.Chat chat = tdlib.chat(item.getSourceChatId());
      if (chat != null) {
        return chat.title;
      }
    }
    return null;
  }

  private CharSequence genSubtitle () {
    String customSubtitle = getArgumentsStrict().customSubtitle;
    if (!StringUtils.isEmpty(customSubtitle)) {
      return customSubtitle;
    }
    MediaItem item = stack.getCurrent();
    String time = item.getSourceDate() != 0 ? Lang.getMessageTimestamp(item.getSourceDate(), TimeUnit.SECONDS) : null;
    switch (mode) {
      case MODE_MESSAGES: {
        TdApi.Message message = item.getMessage();
        if (message != null && message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
          TdApi.MessageText messageText = (TdApi.MessageText) message.content;
          if (messageText.webPage != null) {
            if (!StringUtils.isEmpty(messageText.webPage.author)) {
              return messageText.webPage.author;
            }
            return messageText.webPage.displayUrl;
          }
        }
        String authorText = getAuthorText(item);
        if (authorText != null) {
          SpannableStringBuilder b = new SpannableStringBuilder(authorText);
          b.setSpan(Lang.newBoldSpan(Text.needFakeBold(authorText)), 0, authorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          if (time != null) {
            b.append(' ');
            b.append(time);
          }
          return b;
        } else {
          return time != null ? time : "";
        }
      }
      case MODE_SIMPLE: {
        return StringUtils.trim(getArgumentsStrict().caption);
      }
      case MODE_PROFILE:
      case MODE_CHAT_PROFILE: {
        if (time != null) {
          return stack.getEstimatedSize() != 1 ? Lang.getString(R.string.format_mediaIndexAndTime, getXofY(), time) : time;
        }
        int resId;
        if (mode == MODE_CHAT_PROFILE) {
          resId = (tdlib.isChannel(stack.getCurrent().getSourceChatId()) ? R.string.ChannelPhoto : R.string.GroupPhoto);
        } else {
          resId = R.string.ProfilePhoto;
        }
        String content = Lang.getString(resId);
        if (stack.getEstimatedSize() != 1) {
          return getXofY();
        } else {
          return content;
        }
      }
    }
    return "";
  }

  // Load more stuff

  private boolean loadedInitialChunk;
  private boolean isLoading;

  private static final int LOAD_THRESHOLD = 4;
  private static final int LOAD_COUNT = 40;
  private static final int LOAD_COUNT_PROFILE = 100;

  private void processRequestedEndReached () {
    // TODO load newer messages when possible
  }

  private void loadMoreIfNeeded () {
    loadMoreIfNeeded(false, false);
  }

  private TdApi.SearchMessagesFilter searchFilter () {
    return this.filter != null ? this.filter : mode == MODE_CHAT_PROFILE ? new TdApi.SearchMessagesFilterChatPhoto() : new TdApi.SearchMessagesFilterPhotoAndVideo();
  }

  private Client.ResultHandler foundChatMessagesHandler (long chatId, long fromMessageId, int loadCount) {
    return result -> {
      switch (result.getConstructor()) {
        case TdApi.FoundChatMessages.CONSTRUCTOR: {
          TdApi.FoundChatMessages foundChatMessages = (TdApi.FoundChatMessages) result;
          runOnUiThreadOptional(() ->
            addItems(chatId, fromMessageId, loadCount, foundChatMessages)
          );
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(result);
          break;
        }
      }
    };
  }

  private void loadMoreIfNeeded (boolean edgeReached, boolean isEnd) {
    if (isLoading || getArgumentsStrict().noLoadMore) {
      return;
    }
    switch (mode) {
      case MODE_MESSAGES: {
        long chatId = stack.getCurrent().getSourceChatId();
        if (chatId == 0 || getArgumentsStrict().areOnlyScheduled) {
          return;
        }
        if (!loadedInitialChunk || (reverseMode ? (edgeReached && isEnd) || stack.getCurrentIndex() >= stack.getCurrentSize() - LOAD_THRESHOLD : (edgeReached && !isEnd) || stack.getCurrentIndex() <= LOAD_THRESHOLD)) {
          isLoading = true;
          MediaItem item = reverseMode ? stack.lastAvalable() : stack.firstAvailable();
          long initialFromMessageId = item.getSourceMessageId();
          TdApi.SearchChatMessages searchFunction = new TdApi.SearchChatMessages(
            chatId, null, null,
            initialFromMessageId, 0,
            LOAD_COUNT, searchFilter(),
            messageThreadId
          );
          tdlib.client().send(searchFunction, foundChatMessagesHandler(chatId, initialFromMessageId, LOAD_COUNT));
        }
        break;
      }
      case MODE_CHAT_PROFILE: {
        long chatId = stack.getCurrent().getSourceChatId();
        if (!loadedInitialChunk || (edgeReached && isEnd) || stack.getCurrentIndex() <= stack.getCurrentSize() - LOAD_THRESHOLD) {
          isLoading = true;
          MediaItem item = stack.lastAvalable();
          long initialFromMessageId = item.getSourceMessageId();
          TdApi.SearchChatMessages searchFunction = new TdApi.SearchChatMessages(
            chatId, null, null,
            initialFromMessageId, 0,
            LOAD_COUNT_PROFILE, searchFilter(),
            messageThreadId
          );
          tdlib.client().send(searchFunction, foundChatMessagesHandler(chatId, initialFromMessageId, LOAD_COUNT_PROFILE));
        }
        break;
      }
      case MODE_PROFILE: {
        long userId = Td.getSenderUserId(stack.getCurrent().getSourceSender());
        if (!loadedInitialChunk || (edgeReached && isEnd) || stack.getCurrentIndex() <= stack.getCurrentSize() - LOAD_THRESHOLD) {
          isLoading = true;
          TdApi.GetUserProfilePhotos searchFunction = new TdApi.GetUserProfilePhotos(
            userId,
            loadedInitialChunk ? stack.getCurrentSize() : 0,
            LOAD_COUNT_PROFILE
          );
          tdlib.client().send(searchFunction, result -> {
            switch (result.getConstructor()) {
              case TdApi.ChatPhotos.CONSTRUCTOR: {
                runOnUiThreadOptional(() ->
                  addItems((TdApi.ChatPhotos) result)
                );
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(result);
                break;
              }
            }
          });
        }
        break;
      }
    }
  }

  private void addItems (TdApi.ChatPhotos photos) {
    if (photos.photos.length == 0) {
      return;
    }

    int skipCount = 0;

    if (!loadedInitialChunk) {
      stack.setEstimatedSize(0, photos.totalCount - stack.getCurrentSize());
      loadedInitialChunk = true;
      if (photos.photos.length > 0 && photos.photos[0].id == stack.get(0).getPhotoId()) {
        stack.get(0).setChatPhoto(photos.photos[0]);
        skipCount = 1;
      }
    }

    long sourceUserId = Td.getSenderUserId(stack.getCurrent().getSourceSender());
    ArrayList<MediaItem> items = new ArrayList<>(photos.photos.length);
    for (TdApi.ChatPhoto photo : photos.photos) {
      if (skipCount > 0) {
        skipCount--;
        continue;
      }
      items.add(new MediaItem(context(), tdlib, sourceUserId, 0, photo));
    }

    if (!items.isEmpty()) {
      stack.insertItems(items, false);
      addMoreThumbItems(items, false);
    } else {
      headerCell.setSubtitle(genSubtitle());
    }
    isLoading = false;
  }

  private long subscribedToChatId;

  private void subscribeToChatId (long chatId) {
    if (this.subscribedToChatId != chatId) {
      if (this.subscribedToChatId != 0) {
        tdlib.listeners().unsubscribeFromMessageUpdates(this.subscribedToChatId, this);
      }
      this.subscribedToChatId = chatId;
      if (chatId != 0) {
        tdlib.listeners().subscribeToMessageUpdates(chatId, this);
      }
    }
  }

  private void addItems (long chatId, long fromMessageId, int loadCount, TdApi.FoundChatMessages messages) {
    long messagesChatId = TD.getChatId(messages.messages);
    if (messagesChatId == 0) {
      messagesChatId = chatId;
    }
    List<TdApi.Message> list = new ArrayList<>(messages.messages.length);
    for (TdApi.Message message : messages.messages) {
      if (!Td.isSecret(message.content)) {
        list.add(message);
      }
    }
    int addedCount = addItemsImpl(list, messages.totalCount);
    if (messagesChatId != 0) {
      subscribeToChatId(messagesChatId);
    }
    if (messages.nextFromMessageId == 0 || (addedCount == 0 && messages.nextFromMessageId == fromMessageId)) {
      stack.onEndReached(reverseMode);
      getArgumentsStrict().noLoadMore = true;
    } else if (addedCount == 0) {
      TdApi.SearchChatMessages retryFunction = new TdApi.SearchChatMessages(
        chatId, null, null,
        messages.nextFromMessageId, 0,
        loadCount, searchFilter(),
        messageThreadId
      );
      tdlib.client().send(retryFunction, foundChatMessagesHandler(chatId, messages.nextFromMessageId, loadCount));
      return;
    }
    isLoading = false;
  }

  private int addItemsImpl (List<TdApi.Message> messages, final int totalCount) {
    if (messages.isEmpty()) {
      processRequestedEndReached();
      return 0;
    }

    int skipCount = 0;
    if (!loadedInitialChunk) {
      loadedInitialChunk = true;

      switch (mode) {
        case MODE_MESSAGES: {
          if (reverseMode) {
            stack.setEstimatedSize(0, totalCount - stack.getCurrentSize());
          } else {
            stack.setEstimatedSize(totalCount - stack.getCurrentSize(), 0);
          }
          break;
        }
        case MODE_CHAT_PROFILE:
        case MODE_PROFILE: {
          stack.setEstimatedSize(0, totalCount - stack.getCurrentSize());
          if (stack.firstAvailable().getSourceMessageId() == 0) {
            skipCount = 1;
            stack.firstAvailable().setSourceMessageId(messages.get(0).chatId, messages.get(0).id);
            stack.firstAvailable().setSourceDate(messages.get(0).date);
          }
          break;
        }
      }
    }

    ArrayList<MediaItem> items = new ArrayList<>(messages.size());
    for (TdApi.Message msg : messages) {
      if (skipCount != 0) {
        skipCount--;
        continue;
      }
      if (TD.isSecret(msg) || (!ChatId.isSecret(msg.chatId) && msg.selfDestructTime != 0)) // skip self-destructing images
        continue;

      MediaItem item = MediaItem.valueOf(context(), tdlib, msg);
      if (item == null) {
        continue;
      }

      if (reverseMode) {
        if (mode == MODE_MESSAGES) {
          items.add(item);
        } else {
          items.add(0, item);
        }
      } else {
        if (mode == MODE_MESSAGES) {
          items.add(0, item);
        } else {
          items.add(item);
        }
      }
    }
    boolean onTop = mode == MODE_MESSAGES && !reverseMode;
    stack.insertItems(items, onTop);
    addMoreThumbItems(items, onTop);

    return items.size();
  }

  private void deleteMedia (int index, MediaItem deletedMedia) {
    mediaView.replaceMedia(deletedMedia, null);
    stack.deleteItemAt(index);
    if (thumbsAnimator != null && thumbsAnimator.getValue()) {
      if (!needThumbPreviews()) {
        checkNeedThumbs();
      } else {
        thumbsAdapter.items.deleteItem(index, deletedMedia);
        onFactorChanged(mediaView, slideFactor);
      }
    }
  }

  private void replaceMedia (int index, MediaItem oldMedia, MediaItem newMedia) {
    mediaView.replaceMedia(oldMedia, newMedia);
    stack.setItemAt(index, newMedia);
    if (thumbsAnimator != null && thumbsAnimator.getValue()) {
      thumbsAdapter.items.replaceItem(index, oldMedia, newMedia);
      onFactorChanged(mediaView, slideFactor);
    }
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    runOnUiThreadOptional(() -> {
      int index = stack.indexOfMessage(chatId, messageId);
      if (index != -1) {
        MediaItem oldItem = stack.get(index);
        TdApi.Message message = oldItem.getMessage();
        message.content = newContent;
        MediaItem newItem = MediaItem.valueOf(context(), tdlib, message);
        if (newItem != null) {
          replaceMedia(index, oldItem, newItem);
          headerCell.setSubtitle(genSubtitle());
        } else if (stack.getCurrentIndex() == index) {
          forceClose();
        } else {
          deleteMedia(index, oldItem);
        }
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    runOnUiThreadOptional(() -> {
      List<MediaItem> items = stack.getAll();
      int remainingCount = messageIds.length;
      for (int index = items.size() - 1; index >= 0 && remainingCount > 0; index--) {
        MediaItem item = items.get(index);
        if (item.getSourceChatId() == chatId && item.getSourceMessageId() != 0 && ArrayUtils.indexOf(messageIds, item.getSourceMessageId()) != -1) {
          remainingCount--;
          if (stack.getCurrentIndex() == index) {
            forceClose();
          } else {
            deleteMedia(index, item);
          }
        }
      }
    });
  }

  // Picture-in-picture stuff

  private void enterPictureInPicture () {
    if (mediaView.isStill()) {
      setInPictureInPicture(true);
    }
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    if (mode == MODE_GALLERY) {
      closeCaption();
    }
  }

  private void backFromPictureInPicture () {
    ViewController<?> c = context.navigation().getCurrentStackItem();
    if (c != null) {
      c.hideSoftwareKeyboard();
    }
    if (mediaView.isStill()) {
      setInPictureInPicture(false);
    }
  }

  private float pipStartScale;

  private void closePictureInPicture () {
    pipStartScale = mediaView.getScaleX();
    forceAnimationType = ANIMATION_TYPE_PIP_CLOSE;
    popupView.hideWindow(true);
  }

  private void setPipDismissFactor (float x) {
    float origScale = (.6f + .4f * x);
    float scale = pipStartScale * origScale;
    mediaView.setScaleX(scale);
    mediaView.setScaleY(scale);
    mediaView.setAlpha(x);
    pipControlsWrap.setScaleX(origScale);
    pipControlsWrap.setScaleY(origScale);
    pipControlsWrap.setAlpha(x);
  }

  private boolean inPictureInPicture;
  private FactorAnimator pipAnimator;
  private float pipFactor;

  private static final int ANIMATOR_PIP = 8;

  private MediaItem pipItem;

  private float pipXFactor, pipYFactor;

  private void setInPictureInPicture (boolean in) {
    if (this.inPictureInPicture != in) {
      this.inPictureInPicture = in;

      /*if (in && pipFactor == 0f) {
        pipXFactor = 1f;
        pipYFactor = -1f;
      }*/

      mediaView.getBaseCell().getDetector().preparePositionReset();
      pipItem = stack.getCurrent();

      if (in) {
        // remember in onFactorChangeFinished
        context().pretendYouDontKnowThisWindow(popupView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
          mediaView.invalidateOutline();
        }
      } else {
        setDifferentStuffVisible(true);
      }

      if (pipAnimator == null) {
        pipAnimator = new FactorAnimator(ANIMATOR_PIP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 198, pipFactor);
      }

      pipAnimator.animateTo(in ? 1f : 0f);
    }
  }

  private boolean getPipSize (int[] out) {
    int viewWidth = mediaView.getMeasuredWidth();
    int viewHeight = mediaView.getMeasuredHeight();

    if (viewHeight == 0 || viewWidth == 0 || pipItem == null) {
      return false;
    }

    int mediaWidth = pipItem.getWidth();
    int mediaHeight = pipItem.getHeight();

    float viewRatio = Math.min((float) viewWidth / (float) mediaWidth, (float) viewHeight / (float) mediaHeight);
    mediaWidth *= viewRatio;
    mediaHeight *= viewRatio;

    out[0] = mediaWidth;
    out[1] = mediaHeight;

    return true;
  }

  private boolean isPlayingVideo;

  private void updatePipState (boolean isPlaying) {
    if (pipPlayPauseButton != null) {
      pipPlayPauseButton.setIsPlaying(isPlaying, pipFactor > 0f && pipPlayPauseButton.getAlpha() > 0f);
    }
  }

  private int pipWidth, pipHeight;
  private float pipX, pipY;

  private void updatePipLayout (int viewWidth, int viewHeight, boolean byLayout, boolean allowControlsHide) {
    /*int viewWidth = mediaView.getMeasuredWidth();
    int viewHeight = mediaView.getMeasuredHeight();*/

    if (viewHeight == 0 || viewWidth == 0 || pipItem == null) {
      return;
    }

    int mediaWidth = pipItem.getWidth();
    int mediaHeight = pipItem.getHeight();

    float viewRatio = Math.min((float) viewWidth / (float) mediaWidth, (float) viewHeight / (float) mediaHeight);
    mediaWidth *= viewRatio;
    mediaHeight *= viewRatio;

    // mediaView styles

    int pipSize = Screen.dp(220f);
    float pipScale = Math.min((float) pipSize / (float) mediaWidth, (float) pipSize / (float) mediaHeight);

    // scale
    float scale = pipScale + (1f - pipScale) * (1f - pipFactor);
    mediaView.setScaleX(scale);
    mediaView.setScaleY(scale);
    /*pipControlsWrap.setScaleX(scale);
    pipControlsWrap.setScaleY(scale);*/

    // translate
    int currentWidth = (int) ((float) mediaWidth * pipScale);
    int currentHeight = (int) ((float) mediaHeight * pipScale);

    pipWidth = currentWidth;
    pipHeight = currentHeight;

    int offsetX = Screen.dp(10f) / 2;
    int offsetTopY = (HeaderView.getSize(true) + Screen.dp(11f)) / 2;
    int offsetBottomY = (Screen.dp(49f) + Screen.dp(11f)) - offsetTopY;

    int fullX = viewWidth / 2 - currentWidth / 2 - offsetX;
    int fullY = viewHeight / 2 - currentHeight / 2 - offsetTopY;

    float pipXFactor = this.pipXFactor < -1f ? -1f : this.pipXFactor > 1f ? 1f : this.pipXFactor;
    float x = ((offsetX * -pipXFactor) + (fullX * pipXFactor) + pipAddX) * pipFactor;
    float y = (((pipYFactor < 0f ? offsetTopY : offsetBottomY) * -pipYFactor) + (fullY * pipYFactor) + pipAddY) * pipFactor;

    if (Math.abs(this.pipXFactor) == 2f) {
      x = this.pipXFactor == -2f ? x - currentWidth - offsetX + Screen.dp(24f) : x + currentWidth + offsetX - Screen.dp(24f);
    }

    if (allowControlsHide) {
      float bound = currentWidth / 6;
      float leftX = calculatePipPosition(offsetX, fullX, pipFactor, -1f);
      float rightX = calculatePipPosition(offsetX, fullX, pipFactor, 1f);

      boolean hiddenLeft = x + currentWidth / 2 < leftX + bound;
      setPipHidden(pipFactor == 1f && (hiddenLeft || x + currentWidth / 2 > rightX + currentWidth - bound), hiddenLeft);
    }

    pipX = x;
    pipY = y;

    mediaView.setTranslationX(x);
    mediaView.setTranslationY(y);

    float addY = ((mediaHeight * scale) - currentHeight) / 2;

    pipControlsWrap.setTranslationX(viewWidth / 2 + x - currentWidth / 2);
    pipControlsWrap.setTranslationY(viewHeight / 2 + y - currentHeight / 2 + addY);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      mediaView.invalidateOutline();
    }

    // inline controls check

    pipControlsWrap.setAlpha(pipFactor);
    int visibility = pipFactor == 0f ? View.GONE : View.VISIBLE;
    if (pipControlsWrap.getVisibility() != visibility) {
      pipControlsWrap.setVisibility(visibility);
    }

    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) pipControlsWrap.getLayoutParams();
    if (params.width != currentWidth || params.height != currentHeight) {
      params.width = currentWidth;
      params.height = currentHeight;
      if (!byLayout) {
        pipControlsWrap.setLayoutParams(params);
      }
    }
  }

  private void setPipFactor (float factor, float fraction) {
    if (this.pipFactor != factor) {
      if (Float.isNaN(factor))
        throw new IllegalArgumentException();

      this.pipFactor = factor;

      updatePipLayout(mediaView.getMeasuredWidth(), mediaView.getMeasuredHeight(), false, false);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
        mediaView.setTranslationZ(factor * (float) Screen.dp(1f));
      }

      pipItem.setComponentsAlpha(1f - factor);

      if (mediaView.getBaseCell().getDetector().setPositionFactor(fraction)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          mediaView.invalidateOutline();
        }
      }
      mediaView.setDisableTouch(factor != 0f);

      contentView.invalidate();
      updateHeaderAlpha();
      updateCaptionAlpha();
      updateSliderAlpha();
      updateThumbsAlpha();

      updatePhotoRevealFactor();
    }
  }

  private void setDifferentStuffVisible (boolean isVisible) {
    if (headerView != null) {
      headerView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    if (bottomWrap != null) {
      bottomWrap.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
  }

  private void applyPipMode () {
    if (pipFactor == 0f) {
      context().letsRememberAboutThisWindow(popupView);
      setFullScreen(true);
      setLowProfile(true);
      pipItem = null;
    } else if (pipFactor == 1f) {
      setFullScreen(false);
      setLowProfile(false);
      setDifferentStuffVisible(false);
    }
  }

  // PiP touch stuff

  private boolean listenCatchPip;
  private boolean isPipUp;

  private void setPipUp (boolean isUp, float velocityX, float velocityY) {
    if (isPipUp != isUp) {
      this.isPipUp = isUp;
      if (isUp) {
        preparePipMovement();
      } else {
        normalizePipPosition(velocityX, velocityY, true);
      }
      animatePipUpFactor(isUp);
    }
  }

  private static final int ANIMATOR_PIP_UP = 9;
  private FactorAnimator pipUpAnimator;

  private void animatePipUpFactor (boolean isUp) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (pipUpAnimator == null) {
        pipUpAnimator = new FactorAnimator(ANIMATOR_PIP_UP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      pipUpAnimator.animateTo(isUp ? 1f : 0f);
    }
  }

  private void setPipUpFactor (float factor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
      mediaView.setTranslationZ(Screen.dp(1f) + (float) Screen.dp(2f) * factor);
    }
  }

  private boolean pipHidden;
  private FactorAnimator pipHideAnimator;
  private static final int ANIMATOR_PIP_HIDE = 12;

  private void setPipHidden (boolean isHidden, boolean isLeft) {
    if (this.pipHidden != isHidden) {
      this.pipHidden = isHidden;
      if (pipHideAnimator == null) {
        pipHideAnimator = new FactorAnimator(ANIMATOR_PIP_HIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      if (isHidden) {
        pipOverlayView.setIsLeft(isLeft);
      }
      pipHideAnimator.animateTo(isHidden ? 1f : 0f);
    }
  }

  private float pipHideFactor;

  private void setPipHideFactor (float factor) {
    if (this.pipHideFactor != factor) {
      this.pipHideFactor = factor;
      updatePipControlsAlpha();
      pipOverlayView.setAlpha(factor);

    }
  }

  private float pipStartX;
  private float pipStartY;

  private void preparePipMovement () {
    pipStartX = pipAddX;
    pipStartY = pipAddY;
  }

  private float pipAddX, pipAddY;

  private static float calculatePipPosition (int offset, int full, float pipFactor, float position) {
    return ((offset * -position) + (full * position)) * pipFactor;
  }

  private FactorAnimator pipPositionAnimator;
  private float fromPipAddX, fromPipAddY, toPipAddX, toPipAddY;
  private float toPipXFactor, toPipYFactor;

  private void dropPip (float velocityX, float velocityY) {
    setPipUp(false, velocityX, velocityY);
  }

  private void normalizePipPosition (float velocityX, float velocityY, boolean allowHide) {
    int offsetX = Screen.dp(10f) / 2;
    int offsetTopY = (HeaderView.getSize(true) + Screen.dp(11f)) / 2;
    int offsetBottomY = (Screen.dp(49f) + Screen.dp(11f)) - offsetTopY;

    int viewWidth = mediaView.getMeasuredWidth();
    int viewHeight = mediaView.getMeasuredHeight();

    int fullX = viewWidth / 2 - pipWidth / 2 - offsetX;
    int fullY = viewHeight / 2 - pipHeight / 2 - offsetTopY;

    float leftX = calculatePipPosition(offsetX, fullX, pipFactor, -1f);
    float rightX = calculatePipPosition(offsetX, fullX, pipFactor, 1f);

    float topY = calculatePipPosition(offsetTopY, fullY, pipFactor, -1f);
    float bottomY = calculatePipPosition(offsetBottomY, fullY, pipFactor, 1f);

    float currentX = pipX + pipWidth / 2;
    float currentY = pipY + pipHeight / 2;

    float targetX, targetY;
    float targetXFactor, targetYFactor;
    float bound = pipWidth / 6;

    boolean shouldBeHidden = allowHide && (currentX < leftX + bound || currentX > rightX + pipWidth - bound);
    boolean hideLeft = shouldBeHidden && currentX < leftX + bound;
    float hiddenLeftX = leftX - pipWidth - offsetX + Screen.dp(24f);
    float hiddenRightX = rightX + pipWidth + offsetX - Screen.dp(24f);

    double degrees = velocityX != 0 || velocityY != 0 ? Math.toDegrees(Math.atan2(velocityY, velocityX)) : 0;
    double absDegrees = Math.abs(degrees);
    boolean allowVelocityY = true;

    if (Math.abs(velocityX) > Screen.getTouchSlopBig() && (absDegrees < 65 || absDegrees > 115)) {
      if (absDegrees > 115 && pipX <= leftX) {
        targetX = hiddenLeftX;
        targetXFactor = -2f;
        allowVelocityY = false;
      } else if (absDegrees < 65 && pipX >= rightX) {
        targetX = hiddenRightX;
        targetXFactor = 2f;
        allowVelocityY = false;
      } else if (shouldBeHidden && absDegrees > 115 && !hideLeft) {
        targetX = rightX;
        targetXFactor = 1f;
        allowVelocityY = false;
      } else if (shouldBeHidden && absDegrees < 65 && hideLeft) {
        targetX = leftX;
        targetXFactor = -1f;
        allowVelocityY = false;
      } else {
        targetX = absDegrees > 115 ? leftX : rightX;
        targetXFactor = targetX == leftX ? -1f : 1f;
      }
    } else {
      targetX = shouldBeHidden ? (hideLeft ? hiddenLeftX : hiddenRightX) : Math.abs(leftX + pipWidth / 2 - currentX) < Math.abs(rightX + pipWidth / 2 - currentX) ? leftX : rightX;
      targetXFactor = shouldBeHidden ? (hideLeft ? -2f : 2f) : targetX == leftX ? -1f : 1f;
    }

    setPipHidden(Math.abs(targetXFactor) == 2f, targetXFactor == -2f);

    if (allowVelocityY && Math.abs(velocityY) > Screen.dp(80f) && (absDegrees >= 45 && absDegrees <= 135)) {
      targetY = degrees > 0 ? bottomY : topY;
    } else {
      targetY = Math.abs(topY + pipHeight / 2 - currentY) < Math.abs(bottomY + pipHeight / 2 - currentY) ? topY : bottomY;
    }

    targetYFactor = targetY == topY ? -1f : 1f;

    if (pipPositionAnimator == null) {
      pipPositionAnimator = new FactorAnimator(ANIMATOR_PIP_POSITION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180);
    } else {
      pipPositionAnimator.forceFactor(0f);
    }

    fromPipAddX = pipAddX;
    fromPipAddY = pipAddY;

    toPipAddX = targetX - (pipX - pipAddX);
    toPipAddY = targetY - (pipY - pipAddY);

    toPipXFactor = targetXFactor;
    toPipYFactor = targetYFactor;

    pipPositionAnimator.animateTo(1f);
  }

  private void applyPipPosition () {
    pipXFactor = toPipXFactor;
    pipYFactor = toPipYFactor;
    pipAddX = pipAddY = 0;
    savePipFactors();
  }

  private void restorePipFactors () {
    long position = Settings.instance().getPipPosition();
    pipXFactor = BitwiseUtils.splitLongToFirstInt(position);
    pipYFactor = BitwiseUtils.splitLongToSecondInt(position);
  }

  private void savePipFactors () {
    Settings.instance().setPipPosition(pipXFactor, pipYFactor);
  }

  private static final int ANIMATOR_PIP_POSITION = 11;

  private void setPipPositionFactor (float factor) {
    pipAddX = fromPipAddX + (toPipAddX - fromPipAddX) * factor;
    pipAddY = fromPipAddY + (toPipAddY - fromPipAddY) * factor;
    updatePipLayout(mediaView.getMeasuredWidth(), mediaView.getMeasuredHeight(), false, false);
  }

  private void movePip (float dx, float dy, int viewWidth, int viewHeight) {
    pipAddX = pipStartX + dx;
    pipAddY = pipStartY + dy;
    updatePipLayout(viewWidth, viewHeight, false, true);
  }

  private boolean pipControlsVisible = true;
  private float pipControlsFactor = 1f;

  private void togglePipControlsVisibility () {
    if (Math.abs(pipXFactor) == 2f) {
      normalizePipPosition(0f, 0f, false);
    } else {
      setPipControlsVisible(!pipControlsVisible, true);
    }
  }

  private void setPipControlsVisible (boolean visible, boolean animated) {
    if (this.pipControlsVisible != visible) {
      this.pipControlsVisible = visible;
      if (animated) {
        animatePipControlsFactor(visible ? 1f : 0f);
      } else {
        forcePipControlsFactor(visible ? 1f : 0f);
      }
    }
  }

  private static final int ANIMATOR_PIP_CONTROLS = 10;
  private FactorAnimator pipControlsAnimator;

  private void animatePipControlsFactor (float toFactor) {
    if (pipControlsAnimator == null) {
      pipControlsAnimator = new FactorAnimator(ANIMATOR_PIP_CONTROLS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, pipControlsFactor);
    }
    pipControlsAnimator.animateTo(toFactor);
  }

  private void forcePipControlsFactor (float toFactor) {
    if (pipControlsAnimator != null) {
      pipControlsAnimator.forceFactor(toFactor);
    }
    setPipControlsFactor(toFactor);
  }

  private void setPipControlsFactor (float factor) {
    if (this.pipControlsFactor != factor) {
      this.pipControlsFactor = factor;
      updatePipControlsAlpha();
    }
  }

  private void updatePipControlsAlpha () {
    float alpha = pipControlsFactor * (1f - pipHideFactor);
    pipOpenButton.setAlpha(alpha);
    pipCloseButton.setAlpha(alpha);
    pipPlayPauseButton.setAlpha(alpha);
    pipBackgroundView.setAlpha(alpha * .7f);

    pipOpenButton.setEnabled(alpha == 1f);
    pipCloseButton.setEnabled(alpha == 1f);
    pipPlayPauseButton.setEnabled(alpha == 1f);
  }

  // View-related stuff

  private static class SecretTimerView extends View implements TGMessage.HotListener {
    private TGMessageMedia secretPhoto;
    private String text;
    private int textWidth;

    private Paint timerPaint;

    public SecretTimerView (Context context) {
      super(context);

      timerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      timerPaint.setTypeface(Fonts.getRobotoMedium());
      timerPaint.setTextSize(Screen.dp(19f));
      timerPaint.setColor(0xffffffff);
    }

    public void setSecretPhoto (TGMessageMedia photo) {
      if (!photo.isOutgoing()) {
        secretPhoto = photo;
        setText(photo.getHotTimerText());
        photo.setHotListener(this);
      }
    }

    @Override
    public void onHotInvalidate (boolean secondsChanged) {
      if (secretPhoto != null) {
        if (secondsChanged) {
          setText(secretPhoto.getHotTimerText());
        }
        invalidate();
      }
    }

    private void setText (String text) {
      if (this.text == null || !this.text.equals(text)) {
        this.text = text;
        this.textWidth = (int) U.measureText(text, timerPaint);
      }
    }

    public void destroy () {
      if (secretPhoto != null) {
        secretPhoto.setHotListener(null);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (secretPhoto == null) {
        return;
      }
      int offset = Screen.dp(18f);
      int radius = Screen.dp(10f);
      int centerX = offset + radius;
      int centerY = offset + radius;

      RectF rectF = Paints.getRectF();

      int offset2 = Screen.dp(16f);
      int padding = Screen.dp(4f);
      /*rectF.set(offset - padding, offset - padding, offset + radius + radius + offset2 + textWidth + padding, offset + radius + radius + padding);
      int radius2 = (int) (rectF.height() / 2);
      c.drawRoundRect(rectF, radius2, radius2, Paints.fillingPaint(0x4c000000));*/

      rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
      c.drawArc(rectF, -90f, -360f * secretPhoto.getHotExpiresFactor(), true, Paints.fillingPaint(0xffffffff));
      c.drawText(text, offset + radius + radius + offset2, Screen.dp(35.5f), timerPaint);
    }
  }

  private static class PipOverlayView extends View {
    private Drawable backIcon;

    public PipOverlayView (Context context) {
      super(context);
      backIcon = Drawables.get(getResources(), R.drawable.baseline_keyboard_arrow_left_24);
    }

    private boolean isLeft;

    public void setIsLeft (boolean isLeft) {
      if (this.isLeft != isLeft) {
        this.isLeft = isLeft;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      int y = getMeasuredHeight() / 2 - backIcon.getMinimumHeight() / 2;
      if (isLeft) {
        c.save();
        c.rotate(180, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
        Drawables.draw(c, backIcon, 0, y, Paints.getPorterDuffPaint(0xffffffff));
        c.restore();
      } else {
        Drawables.draw(c, backIcon, 0, y, Paints.getPorterDuffPaint(0xffffffff));
      }
    }
  }

  private FrameLayoutFix contentView;
  private MediaView mediaView;
  private FrameLayoutFix pipControlsWrap;
  private EditButton pipOpenButton, pipCloseButton;
  private View pipBackgroundView;
  private PlayPauseButton pipPlayPauseButton;
  private PipOverlayView pipOverlayView;

  private EditButton backButton, sendButton;
  private FrameLayoutFix editWrap;
  private LinearLayout editButtons;
  private EditButton cropOrStickerButton;
  private EditButton paintOrMuteButton;
  private EditButton adjustOrTextButton;
  private StopwatchHeaderButton stopwatchButton;
  private @Nullable MediaLayout.SenderSendIcon senderSendIcon;

  private FrameLayoutFix bottomWrap;
  private LinearLayout captionWrapView;
  private View captionView;
  private ImageView captionEmojiButton, captionDoneButton;
  private @Nullable VideoControlView videoSliderView;

  private SecretTimerView secretView;

  private CheckView checkView;
  private CounterView counterView;
  private View overlayView;

  private LinearLayout receiverView;

  private DoubleHeaderView headerCell;
  private HeaderView headerView;

  private RecyclerView filtersView;
  private MediaFiltersAdapter filtersAdapter;
  private FrameLayoutFix qualityControlWrap;
  private SliderView qualitySlider;
  private TextView qualityInfo;

  private FrameLayoutFix cropControlsWrap;
  private EditButton proportionButton;
  private EditButton rotateButton;
  private RotationControlView rotationControlView;

  private FrameLayoutFix paintControlsWrap;
  private ColorPickerView colorPickerView;
  private ColorToneView colorToneView;
  private ShadowView colorToneShadow;
  private ImageView undoButton;
  private EditButton paintTypeButton;
  private ColorPreviewView colorPreviewView;

  private static final float COUNTER_SCALE = .7f;

  private static final int ANIMATOR_COUNTER = 1;

  private float counterFactor;

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSlideMode || (slideAnimator != null && slideAnimator.isAnimating())) {
      return true;
    }
    if (showOtherMedias) {
      setShowOtherMedias(false);
      return true;
    }
    if (currentSection != SECTION_CAPTION) {
      goBackToCaption(true);
      return true;
    }
    if (emojiShown) {
      forceCloseEmojiKeyboard();
      return true;
    }
    if (mediaView.isZoomed()) {
      mediaView.normalizeZoom();
      return true;
    }
    return false;
  }

  private void setCounterFactor (float counterFactor) {
    if (this.counterFactor != counterFactor) {
      this.counterFactor = counterFactor;
      setCounterAlpha(getAllowedCounterAlpha());
      final float scale = COUNTER_SCALE + (1f - COUNTER_SCALE) * counterFactor;
      counterView.setScaleX(scale);
      counterView.setScaleY(scale);
    }
  }

  private FactorAnimator counterAnimator;

  private void animateCounterFactor (float factor) {
    if (this.counterAnimator == null) {
      counterAnimator = new FactorAnimator(ANIMATOR_COUNTER, this, new OvershootInterpolator(3.8f), 260l, counterFactor);
    }
    counterAnimator.animateTo(factor);
  }

  private void forceCounterFactor (float factor) {
    if (counterAnimator != null) {
      counterAnimator.forceFactor(factor);
    }
    this.counterFactor = -1f;
    setCounterFactor(factor);
  }

  private boolean videoVisible;

  private boolean needTrim () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && mode == MODE_GALLERY;
  }

  private void setVideoVisible (boolean isVisible, boolean isLoaded, long duration, TimeUnit unit, boolean animated) {
    if (videoSliderView != null) {
      MediaCellView cellView = mediaView != null ? mediaView.findCellForItem(stack.getCurrent()) : null;
      if (this.videoVisible != isVisible) {
        this.videoVisible = isVisible;
        if (isVisible) {
          long timeNow, timeTotal;
          if (isLoaded && cellView != null) {
            timeNow = cellView.getTimeNow();
            timeTotal = cellView.getTimeTotal();
          } else {
            timeNow = timeTotal = -1;
          }
          if (timeNow == -1 | timeTotal == -1) {
            videoSliderView.resetDuration(unit.toMillis(duration), 0, isLoaded, animated && videoFactor != 0f);
          } else {
            videoSliderView.resetDuration(timeTotal, timeNow, isLoaded, animated && videoFactor != 0f);
          }
          videoSliderView.setFile(stack.getCurrent().getSourceGalleryFile());
          boolean value = isLoaded && (stack.getCurrent().isGifType() || (commonFactor < 1f && !MediaItem.isGalleryType(stack.getCurrent().getType())));
          videoSliderView.setShowPlayPause(value, animated && videoFactor != 0f);
          if (value && commonFactor < 1f) {
            videoSliderView.setIsPlaying(true, animated && videoFactor != 0f);
            updatePipState(true);
          }
        }
        if (animated) {
          animateVideoFactor(isVisible ? 1f : 0f);
        } else {
          forceVideoFactor(isVisible ? 1f : 0f);
        }
      } else if (isVisible) {
        long timeNow, timeTotal;
        if (isLoaded && cellView != null) {
          timeNow = cellView.getTimeNow();
          timeTotal = cellView.getTimeTotal();
        } else {
          timeNow = timeTotal = -1;
        }
        if (timeNow == -1 || timeTotal == -1) {
          videoSliderView.resetDuration(unit.toMillis(duration), 0, isLoaded, animated);
        } else {
          videoSliderView.resetDuration(timeTotal, timeNow, isLoaded, animated);
        }
        videoSliderView.setFile(stack.getCurrent().getSourceGalleryFile());
        boolean value = isLoaded && (stack.getCurrent().isGifType() || commonFactor < 1f);
        videoSliderView.setShowPlayPause(value, animated);
        if (value && (commonFactor < 1f || stack.getCurrent().isAutoplay())) {
          videoSliderView.setIsPlaying(true, animated);
          updatePipState(true);
        }
      }
    }
  }

  @Override
  public void onPlayStarted (MediaItem item, boolean isPlaying) {
    if (videoSliderView != null && stack.getCurrent() == item) {
      videoSliderView.setIsPlaying(isPlaying, true);
      updatePipState(isPlaying);
      videoSliderView.setShowPlayPause(true, true);
    }
  }

  private float videoFactor;
  private FactorAnimator videoAnimator;
  private static final int ANIMATOR_VIDEO = 5;

  private boolean needVideoMargin;

  private void updateCaptionLayout () {
    boolean needMargin = videoFactor == 1f && !inCaption;
    if (this.needVideoMargin != needMargin) {
      this.needVideoMargin = needMargin;
      captionWrapView.setTranslationY((needMargin ? 0 : (float) -Screen.dp(56f) * videoFactor * (inCaption ? 0f : 1f)) + (thumbsFactor * Screen.dp(THUMBS_PADDING)));
      Views.setBottomMargin(captionWrapView, (needVideoMargin ? Screen.dp(56f) : 0));
    }
  }

  private void checkBottomComponentOffsets () {
    float thumbOffset = thumbsFactor * Screen.dp(THUMBS_PADDING);
    if (captionWrapView != null) {
      updateCaptionLayout();
      if (!needVideoMargin) {
        captionWrapView.setTranslationY((float) -Screen.dp(56f) * videoFactor + thumbOffset);
      }
    }
    if (videoSliderView != null) {
      videoSliderView.setTranslationY((float) Screen.dp(56f) * (1f - videoFactor) + thumbOffset);
    }
  }

  private void setVideoFactor (float factor) {
    if (this.videoFactor != factor) {
      this.videoFactor = factor;
      checkBottomComponentOffsets();
      if (videoSliderView != null) {
        videoSliderView.setInnerAlpha(factor);
      }
    }
  }

  private void forceVideoFactor (float factor) {
    if (videoAnimator != null) {
      videoAnimator.forceFactor(factor);
    }
    setVideoFactor(factor);
  }

  @Override
  public void onCanSeekChanged (MediaItem item, boolean canSeek) {
    if (stack.getCurrent() == item && videoSliderView != null) {
      videoSliderView.setSlideEnabled(canSeek);
    }
  }

  @Override
  public void onSeekProgress (MediaItem item, long nowMs, long durationMs, float progress) {
    if (stack.getCurrent() == item && videoSliderView != null) {
      videoSliderView.updateSeek(nowMs, durationMs, progress);
    }
  }

  @Override
  public void onSeekSecondaryProgress (MediaItem item, float offset, float progress) {
    if (stack.getCurrent() == item && videoSliderView != null) {
      videoSliderView.updateSecondarySeek(offset, progress);
    }
  }

  @Override
  public void onPlayPause (MediaItem item, boolean isPlaying) {
    if (stack.getCurrent() != item) {
      return;
    }
    if (videoSliderView != null) {
      videoSliderView.setIsPlaying(isPlaying, videoFactor > 0f);
    }
    isPlayingVideo = isPlaying;
    switch (mode) {
      case MODE_MESSAGES:
      case MODE_SIMPLE: {
        updatePipState(isPlaying);
        if (!isPlaying) {
          setPipControlsVisible(true, true);
        }
        break;
      }
    }
  }

  public void pauseVideoIfPlaying () {
    if (isPlayingVideo) {
      stack.getCurrent().performClick(pipPlayPauseButton);
    }
  }

  private void animateVideoFactor (float toFactor) {
    if (videoAnimator == null) {
      videoAnimator = new FactorAnimator(ANIMATOR_VIDEO, this, AnimatorUtils.DECELERATE_INTERPOLATOR, BOTTOM_ANIMATION_DURATION, videoFactor);
    }
    videoAnimator.animateTo(toFactor);
  }

  private PopupLayout popupView;
  private boolean ignoreCaptionUpdate;

  private boolean canRunFullscreen () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.CUTOUT_ENABLED && mode == MODE_MESSAGES) {
      return true;
    }
    return mode == MODE_SECRET && Build.VERSION.SDK_INT < Build.VERSION_CODES.O && Config.CUTOUT_ENABLED; // mode != MODE_GALLERY && mode != MODE_MESSAGES; // mode == MODE_PROFILE || mode == MODE_CHAT_PROFILE;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (captionView != null) {
      captionView.invalidate();
    }
  }

  private boolean needHeader () {
    return mode == MODE_MESSAGES || mode == MODE_SIMPLE || mode == MODE_CHAT_PROFILE || mode == MODE_PROFILE;
  }

  private FlingDetector flingDetector;

  @Override
  public boolean onFling (float velocityX, float velocityY) {
    if (isPipUp) {
      dropPip(velocityX, velocityY);
      return true;
    }
    if (inSlideMode) {
      dropSlideMode(velocityX, velocityY, Math.max(Math.abs(velocityX), Math.abs(velocityY)) > Screen.dp(50f));
    }
    return false;
  }

  private boolean canCloseBySlide () {
    return mode != MODE_SECRET && (mode != MODE_GALLERY || currentSection == SECTION_CAPTION) && !mediaView.isZoomed() && !inCaption;
  }

  private boolean listenCloseBySlide;
  private boolean inSlideMode;

  private MediaItem slideItem;

  private void setInSlideMode (float x, float y) {
    slideItem = stack.getCurrent();
    inSlideMode = true;
  }

  private int measureBottomWrapHeight () {
    int height = (int) ((float) Screen.dp(56f) * videoFactor);
    if (captionFactor == 1f && captionWrapView != null) {
      height += captionWrapView.getMeasuredHeight();
    }
    return height;
  }

  private float lastSlideX, lastSlideY, lastSlideSourceX;
  private float dismissFactor;

  private void setSlideDismissFactor (float factor) {
    if (this.dismissFactor != factor) {
      this.dismissFactor = factor;
      setCommonFactor(1f - dismissFactor);

      if (headerView != null) {
        headerView.setTranslationY(-HeaderView.getSize(headerView.needOffsets()) * dismissFactor);
      }

      if (mode == MODE_GALLERY) {
        setEditComponentsAlpha(1f - dismissFactor);
      } else {
        checkBottomWrapY();
      }
    }
  }

  private void checkBottomWrapY () {
    int thumbsDistance = Screen.dp(THUMBS_PADDING) * 2 + Screen.dp(THUMBS_HEIGHT);
    float offsetDistance = (float) measureBottomWrapHeight() * dismissFactor;
    // int appliedBottomPadding = -this.appliedBottomPadding;
    if (bottomWrap != null) {
      bottomWrap.setTranslationY(offsetDistance - (thumbsFactor * (float) thumbsDistance) * (1f - dismissFactor) - appliedBottomPadding);
    }
    if (thumbsRecyclerView != null) {
      float dy = ((float) thumbsDistance * Math.max((1f - thumbsFactor), dismissFactor));
      thumbsRecyclerView.setTranslationY(offsetDistance + dy - appliedBottomPadding);
    }
  }

  private void setSlide (float x, float y, float sourceX, boolean noRotation, boolean byTouch) {
    lastSlideX = x;
    lastSlideY = y;
    lastSlideSourceX = sourceX;

    float dismissFactor = Math.abs(Math.min(1f, y / (float) Screen.dp(125f)));
    if (Float.isNaN(dismissFactor)) // TODO: find out why it could become NaN
      dismissFactor = 0f;
    if (noRotation || dismissFactor > this.dismissFactor || byTouch) {
      setSlideDismissFactor(dismissFactor);
    }

    mediaView.setTranslationX(x);
    mediaView.setTranslationY(y);

    if (noRotation) {
      return;
    }

    if (sourceX == 0f || y == 0f) {
      mediaView.setRotation(0f);
    } else {
      mediaView.setRotation(calculateRotationForXY(x, y, sourceX));
    }
  }

  private float calculateRotationForXY (float x, float y, float sourceX) {
    int viewWidth = mediaView.getMeasuredWidth();
    int viewHeight = mediaView.getMeasuredHeight();

    int mediaWidth = slideItem.getWidth();
    int mediaHeight = slideItem.getHeight();

    float viewRatio = Math.min((float) viewWidth / (float) mediaWidth, (float) viewHeight / (float) mediaHeight);
    mediaWidth *= viewRatio;
    mediaHeight *= viewRatio;

    sourceX -= viewWidth / 2;
    sourceX *= -1;

    float rotation = -35f * (y / (viewHeight * .5f)) * (Math.min(1f, Math.max(-1f, x / (mediaWidth * .2f))) * Math.signum(sourceX));

    return (sourceX / ((float) viewWidth * .5f)) * rotation;
  }

  private float fromSlideX, fromSlideY, fromSlideRotation, fromSlideSourceX;
  private float toSlideX, toSlideY, toSlideRotation;
  private FactorAnimator slideAnimator;
  private static final int ANIMATOR_SLIDE = 15;

  private void dropSlideMode (float velocityX, float velocityY, boolean apply) {
    inSlideMode = false;

    fromSlideX = lastSlideX;
    fromSlideY = lastSlideY;
    fromSlideRotation = mediaView.getRotation();
    fromSlideSourceX = lastSlideSourceX;

    if (fromSlideX == 0f && fromSlideY == 0f) {
      return;
    }

    if (slideAnimator == null) {
      slideAnimator = new FactorAnimator(ANIMATOR_SLIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280l);
    } else {
      slideAnimator.forceFactor(0f);
    }

    if (apply) {
      double direction = Math.atan2(velocityY, velocityX);

      float viewWidth = mediaView.getMeasuredWidth();
      float viewHeight = mediaView.getMeasuredHeight();

      int mediaWidth = slideItem.getWidth();
      int mediaHeight = slideItem.getHeight();

      float viewRatio = Math.min(viewWidth / (float) mediaWidth, viewHeight / (float) mediaHeight);
      mediaWidth *= viewRatio;
      mediaHeight *= viewRatio;

      double cos = Math.cos(direction);
      double sin = Math.sin(direction);

      toSlideX = (float) (viewWidth * cos);
      toSlideY = (float) (viewHeight * sin);
      toSlideRotation = fromSlideRotation * 1.5f;// calculateRotationForXY(toSlideX, toSlideY, fromSlideSourceX);

      float neededWidth = (float) (Math.abs(viewWidth * Math.sin(toSlideRotation)) + Math.abs(viewHeight * Math.cos(toSlideRotation)));
      float neededHeight = (float) (Math.abs(viewWidth * Math.cos(toSlideRotation)) + Math.abs(viewHeight * Math.sin(toSlideRotation)));

      toSlideX += Math.abs(viewWidth - neededWidth) * Math.signum(toSlideX);
      toSlideY += Math.abs(viewHeight - neededHeight) * Math.signum(toSlideY);

    } else {
      toSlideX = toSlideY = toSlideRotation = 0f;
    }

    slideAnimator.animateTo(1f);
  }

  private void setSlideFactor (float factor) {
    float x = fromSlideX + (toSlideX - fromSlideX) * factor;
    float y = fromSlideY + (toSlideY - fromSlideY) * factor;
    boolean noRotation = toSlideRotation != -1;
    setSlide(x, y, fromSlideSourceX, noRotation, false);

    if (noRotation) {
      mediaView.setRotation(fromSlideRotation + (toSlideRotation - fromSlideRotation) * factor);
    }
  }

  private boolean animationAlreadyDone;

  private void applySlide () {
    forceAnimationType = ANIMATION_TYPE_FADE;
    animationAlreadyDone = true;
    close();
  }

  // Thumb previews

  private float thumbsFactor;
  private BoolAnimator thumbsAnimator;
  private static final int ANIMATOR_THUMBS = 21;
  private ThumbRecyclerView thumbsRecyclerView;
  private CustomItemAnimator thumbItemAnimator;
  private ThumbAdapter thumbsAdapter;
  private LinearLayoutManager thumbsLayoutManager;

  private static final float THUMBS_PADDING = 9f;
  private static final float THUMBS_HEIGHT = 43f;
  private static final float THUMBS_SPACING_BETWEEN = 1f;
  private static final float THUMBS_SPACING_ADD = 5f;
  private static final float THUMBS_WIDTH_SMALL = 22f;
  private static final float THUMBS_WIDTH_BIG = 26f;
  private static final float THUMBS_WIDTH_MAX = THUMBS_HEIGHT * 2f;

  // PADDING: 10dp
  // HEIGHT: 36dp
  // SPACING: 1dp
  // SPACING_ADD: 5dp

  // __
  // DECORATION_BETWEEN: 1dp
  // DECORATION_SIDE = recyclerWidth / 2 - cellWidth / 2

  private void setThumbsFactor (float factor) {
    if (this.thumbsFactor != factor) {
      this.thumbsFactor = factor;
      checkBottomComponentOffsets();
      checkBottomWrapY();
      checkThumbsItemAnimator();
      updateThumbsAlpha();
    }
  }

  private void checkNeedThumbs () {
    setNeedThumbs(needThumbPreviews());
  }

  private void setNeedThumbs (boolean needThumbs) {
    boolean prevNeedThumbs = thumbsAnimator != null && thumbsAnimator.getValue();
    if (prevNeedThumbs != needThumbs) {
      if (thumbsAnimator == null) {
        thumbsAnimator = new BoolAnimator(ANIMATOR_THUMBS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, BOTTOM_ANIMATION_DURATION);
      }
      if (needThumbs) {
        initThumbsRecyclerView();
        fillThumbItems();
      }
      thumbsAnimator.setValue(needThumbs, commonFactor > 0f);
    } else if (needThumbs && mode == MODE_MESSAGES && !getArgumentsStrict().forceThumbs && thumbsAdapter.items.getMediaGroupId() != stack.getCurrent().getMessage().mediaAlbumId) {
      fillThumbItems();
    }
  }

  private void addMoreThumbItems (ArrayList<MediaItem> items, boolean onLeftSide) {
    if (items == null || items.isEmpty()) {
      return;
    }
    if (thumbsAnimator == null || !thumbsAnimator.getValue()) {
      checkNeedThumbs();
    } else {
      thumbsAdapter.items.addItems(items, onLeftSide, mode == MODE_MESSAGES && !getArgumentsStrict().forceThumbs);
    }
  }

  private boolean needThumbPreviews () {
    if (stack.getCurrentSize() <= 1) {
      return false;
    }
    if (mode == MODE_PROFILE || mode == MODE_CHAT_PROFILE || getArgumentsStrict().forceThumbs) {
      return true;
    }
    if (mode == MODE_MESSAGES) {
      MediaItem current = stack.getCurrent();
      MediaItem prev = stack.getPrevious();
      MediaItem next = stack.getNext();

      TdApi.Message currentMessage = current.getMessage();
      TdApi.Message prevMessage = prev != null ? prev.getMessage() : null;
      TdApi.Message nextMessage = next != null ? next.getMessage() : null;

      return currentMessage != null && currentMessage.mediaAlbumId != 0 && ((prevMessage != null && prevMessage.mediaAlbumId == currentMessage.mediaAlbumId) || (nextMessage != null && nextMessage.mediaAlbumId == currentMessage.mediaAlbumId));
    }
    return false;
  }

  private void fillThumbItems () {
    if (stack.getCurrentSize() <= 1) {
      throw new IllegalStateException();
    }
    int focusItemIndex = -1;
    MediaItem focusItem = stack.getCurrent();
    int indexInStack = -1;

    ArrayList<MediaItem> items = null;

    if (mode == MODE_PROFILE || mode == MODE_CHAT_PROFILE || getArgumentsStrict().forceThumbs) {
      items = new ArrayList<>(stack.getCurrentSize());
      items.addAll(stack.getAll());
      focusItemIndex = stack.getCurrentIndex();
      indexInStack = 0;
    } else if (mode == MODE_MESSAGES) {
      items = new ArrayList<>(TdConstants.MAX_MESSAGE_GROUP_SIZE);
      MediaItem currentItem = stack.getCurrent();
      long mediaGroupId = currentItem.getMessage().mediaAlbumId;
      if (mediaGroupId == 0) {
        throw new IllegalStateException();
      }
      int index = stack.getCurrentIndex();
      while (index - 1 >= 0) {
        MediaItem item = stack.get(index - 1);
        if (item.getMessage().mediaAlbumId != mediaGroupId) {
          break;
        }
        index--;
      }
      indexInStack = index;
      int stackSize = stack.getCurrentSize();
      while (index < stackSize) {
        MediaItem item = stack.get(index);
        if (item.getMessage().mediaAlbumId != mediaGroupId) {
          break;
        }
        if (item == focusItem) {
          focusItemIndex = items.size();
        }
        items.add(item);
        index++;
      }
    } else {
      throw new IllegalStateException();
    }

    if (indexInStack == -1) {
      throw new IllegalArgumentException();
    }

    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException();
    }

    if (focusItemIndex == -1) {
      throw new IllegalArgumentException();
    }

    ThumbItems thumbItems = new ThumbItems(thumbsAdapter, items, indexInStack);
    thumbItems.setFocusItem(focusItem, focusItemIndex, 1f);
    thumbsAdapter.setItems(thumbItems);
    ensureThumbsPosition(false, false);
  }

  private void clearThumbsView () {
    if (thumbsAdapter != null) {
      thumbsAdapter.setItems(null);
      thumbsRecyclerView.setItemAnimator(null);
    }
  }

  private void checkThumbsItemAnimator () {
    if (thumbsRecyclerView != null) {
      RecyclerView.ItemAnimator desiredItemAnimator = commonFactor == 1f && thumbsFactor > 0f ? thumbItemAnimator : null;
      if (thumbsRecyclerView.getItemAnimator() != desiredItemAnimator) {
        thumbsRecyclerView.setItemAnimator(desiredItemAnimator);
      }
    }
  }

  private static class ThumbItemDecoration extends RecyclerView.ItemDecoration {
    private final ThumbAdapter context;

    public ThumbItemDecoration (ThumbAdapter context) {
      this.context = context;
    }

    @Override
    public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      ThumbView thumbView = ThumbViewHolder.getThumbView(view);
      MediaItem item = thumbView.getItem();
      ThumbItems items = thumbView.getItems();
      int parentWidth = parent.getMeasuredWidth();
      int itemWidth = context.controller.calculateThumbWidth();
      int distance = parentWidth / 2 - itemWidth / 2;
      if (items == null || item == null) {
        int adapterPosition = parent.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          int itemCount = parent.getAdapter().getItemCount();
          if (Lang.rtl()) {
            outRect.left = adapterPosition == itemCount - 1 ? distance : 0;
            outRect.right = adapterPosition == 0 ? distance : 0;
          } else {
            outRect.left = adapterPosition == 0 ? distance : 0;
            outRect.right = adapterPosition == itemCount - 1 ? distance : 0;
          }
        }
        return;
      }
      if (Lang.rtl()) {
        outRect.left = items.getLast() == item ? distance : 0;
        outRect.right = items.getFirst() == item ? distance : 0;
      } else {
        outRect.left = items.getFirst() == item ? distance : 0;
        outRect.right = items.getLast() == item ? distance : 0;
      }
    }

    @Override
    public void onDraw (Canvas c, RecyclerView parent, RecyclerView.State state) {
      final int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = parent.getChildAt(i);
        if (view == null) {
          continue;
        }
        ThumbView thumbView = ThumbViewHolder.getThumbView(view);
        ThumbItems thumbItems = thumbView.getItems();
        MediaItem thumbItem = thumbView.getItem();
        float expandAllowance = 1f - context.controller.thumbsScrollFactor;
        if (thumbItems != null && thumbItem != null) {
          float x = thumbView.getLeft() + thumbView.getMeasuredWidth() / 2f;
          int position = parent.getChildAdapterPosition(view);
          if (position == RecyclerView.NO_POSITION) {
            Integer tag = (Integer) view.getTag();
            if (tag != null) {
              position = tag;
            } else {
              position = thumbItems.indexOf(thumbItem);
              view.setTag(position);
            }
          } else {
            view.setTag(null);
          }
          if (position != RecyclerView.NO_POSITION) {
            float dx = thumbItems.getTranslationX(thumbItem, position, expandAllowance);
            if (Lang.rtl()) {
              x -= dx;
            } else {
              x += dx;
            }
          }
          thumbView.drawImage(c, (int) x, Screen.dp(THUMBS_PADDING), view.getAlpha(), expandAllowance);
        }
      }
    }
  }

  private static final int ANIMATOR_THUMBS_SCROLL = 22;
  private BoolAnimator thumbsScrollAnimator;
  private boolean applyingThumbScrollFix;
  private float thumbScrollFixStartValue;
  private int thumbScrollFixDistance, thumbScrollFixDone;

  private boolean areThumbsScrolling () {
    return thumbsScrollAnimator != null && thumbsScrollAnimator.getValue();
  }

  private boolean isAutoScrolling () {
    return autoThumbScroller != null && autoThumbScroller.isAnimating();
  }

  private void setThumbsScrolling (boolean isScrolling) {
    boolean prevIsScrolling = thumbsScrollAnimator != null && thumbsScrollAnimator.getValue();
    isScrolling = isScrolling && !isAutoScrolling();
    if (prevIsScrolling != isScrolling) {
      if (thumbsScrollAnimator == null) {
        thumbsScrollAnimator = new BoolAnimator(ANIMATOR_THUMBS_SCROLL, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);
      }
      applyingThumbScrollFix = false;
      if (!isScrolling) {
        thumbRequestTime = 0;
        float startValue = thumbsScrollAnimator.getFloatValue();
        ensureThumbsPosition(true, true);
        if (startValue == 0f || desiredThumbScrollByX == 0) {
          ensureThumbsPosition(true, false);
        } else if (desiredThumbScrollByX != 0) {
          applyingThumbScrollFix = true;
          thumbScrollFixStartValue = startValue;
          thumbScrollFixDistance = desiredThumbScrollByX;
          thumbScrollFixDone = 0;
        }
      } else {
        cancelAutoThumbScroller();
        thumbsRecyclerView.cancelClick();
      }
      thumbsScrollAnimator.setValue(isScrolling, true);
    }
  }

  private boolean dropAutoThumbScroll () {
    return false;
    /*float factor = autoThumbScroller.getFactor();
    if (factor <= .5f) {
      factor
    }

    if (isAutoScrolling()) {

      controller.cancelAutoThumbScroller();
      controller.setThumbsScrolling(true);
    }*/
  }

  private long thumbRequestTime;
  private float getThumbStrength () {
    if (thumbRequestTime == 0) {
      return 1f;
    }
    long now = SystemClock.uptimeMillis();
    long ms = now - thumbRequestTime;
    if (ms > 100l) {
      return 1f;
    }
    return Math.max(0f, (float) ms / 200f);
  }

  private void detectThumbPosition () {
    ensureThumbsPosition(true, true);
    if (totalThumbScrollX == -1 || thumbsAdapter.items == null) {
      return;
    }
    int thumbsWidth = calculateThumbWidth();
    int index = Math.round((float) (totalThumbScrollX) / (float) thumbsWidth);
    if (fastShowMediaItem(thumbsAdapter.items.get(index), thumbsAdapter.items, index, false)) {
      thumbRequestTime = SystemClock.uptimeMillis();
    }
  }

  private boolean thumbsScrolled;

  private float thumbsScrollFactor;

  private void setThumbsScrollFactor (float factor) {
    if (this.thumbsScrollFactor != factor) {
      this.thumbsScrollFactor = factor;
      if (applyingThumbScrollFix) {
        float fixFactor = (thumbScrollFixStartValue - factor) / thumbScrollFixStartValue;
        int distance = (int) (fixFactor * (float) thumbScrollFixDistance);
        int distanceDiff = distance - thumbScrollFixDone;
        if (distanceDiff != 0) {
          thumbScrollFixDone = distance;
          thumbsRecyclerView.scrollBy(distanceDiff, 0);
        }
      }
      thumbsRecyclerView.invalidate();
    }
  }

  private void initThumbsRecyclerView () {
    if (thumbsRecyclerView != null) {
      return;
    }

    thumbItemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);

    thumbsAdapter = new ThumbAdapter(context(), this);

    thumbsLayoutManager = new LinearLayoutManager(context(), LinearLayoutManager.HORIZONTAL, Lang.rtl());

    thumbsRecyclerView = new ThumbRecyclerView(context());
    thumbsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      private boolean thumbsScrolling;

      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        boolean isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
        if (!isScrolling) {
          thumbsScrolled = false;
          if (thumbsScrolling) {
            contentView.requestDisallowInterceptTouchEvent(false);
          }
        }
        thumbsScrolling = isScrolling;
        setThumbsScrolling(thumbsScrolling && thumbsScrolled);
      }

      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        if (!thumbsScrolled) {
          thumbsScrolled = Math.abs(dx) > 1 && thumbsScrolling;
          setThumbsScrolling(thumbsScrolling && thumbsScrolled);
        }
        if (dx != 0 && thumbsScrolling) {
          detectThumbPosition();
        }
      }
    });
    thumbsRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    thumbsRecyclerView.setController(this);
    thumbsRecyclerView.addItemDecoration(new ThumbItemDecoration(thumbsAdapter));
    thumbsRecyclerView.setItemAnimator(null);
    thumbsRecyclerView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
    thumbsRecyclerView.setLayoutManager(thumbsLayoutManager);
    thumbsRecyclerView.setAdapter(thumbsAdapter);

    thumbsRecyclerView.setAlpha(0f);
    thumbsRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(THUMBS_PADDING) * 2 + Screen.dp(THUMBS_HEIGHT), Gravity.BOTTOM));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
      thumbsRecyclerView.setElevation(Screen.dp(3f));
    }

    checkBottomWrapY();
    contentView.addView(thumbsRecyclerView);
  }

  private int savedThumbsPosition = -1, savedThumbsOffset;

  private void saveThumbsPosition () {
    savedThumbsPosition = thumbsLayoutManager.findFirstVisibleItemPosition();
    View savedThumbView = thumbsLayoutManager.findViewByPosition(savedThumbsPosition);
    savedThumbsOffset = savedThumbView != null ? thumbsLayoutManager.getDecoratedLeft(savedThumbView) : 0;
  }

  private void restoreThumbsPosition (int shift) {
    if (savedThumbsPosition != -1) {
      savedThumbsPosition += shift;
      if (thumbsRecyclerView.getMeasuredWidth() > 0) {
        if (areThumbsScrolling()) {
          thumbsLayoutManager.scrollToPositionWithOffset(savedThumbsPosition, savedThumbsOffset);
        } else {
          ensureThumbsPosition(false, false);
        }
      }
    }
  }

  private int calculateThumbWidth () {
    return Screen.dp((mode == MediaViewController.MODE_PROFILE || mode == MediaViewController.MODE_CHAT_PROFILE) ? THUMBS_WIDTH_BIG : THUMBS_WIDTH_SMALL) + Screen.dp(THUMBS_SPACING_BETWEEN);
  }

  // Called when recyclerView width has been changed
  private void resetThumbsPositionByLayout () {
    if (thumbsRecyclerView == null || thumbsAnimator == null || !thumbsAnimator.getValue()) {
      return;
    }
    cancelAutoThumbScroller();
    thumbsRecyclerView.invalidateItemDecorations();
    ensureThumbsPosition(false, false);
  }

  private FactorAnimator autoThumbScroller;
  private int autoScrollDistance, autoScrollDistanceLast;
  private ThumbItems autoScrollItems;
  private float autoScrollStartValue;
  private static final int ANIMATOR_THUMBS_AUTO_SCROLLER = 23;

  private void cancelAutoThumbScroller () {
    if (autoThumbScroller != null) {
      autoThumbScroller.forceFactor(0f);
      autoScrollItems = null;
    }
  }

  private void setAutoThumbScrollFactor (float factor) {
    int distance = (int) ((float) autoScrollDistance * factor);
    int distanceDiff = distance - autoScrollDistanceLast;
    if (distance != 0) {
      autoScrollDistanceLast = distance;
      thumbsRecyclerView.scrollBy(distanceDiff * (Lang.rtl() ? -1 : 1), 0);
    }
    float expandFactor = autoScrollStartValue + (1f - autoScrollStartValue) * factor;
    if (autoScrollItems.expandFactor != expandFactor) {
      autoScrollItems.expandFactor = expandFactor;
      thumbsRecyclerView.invalidate();
    }
  }

  // Called when thumbnail was clicked
  private boolean fastShowMediaItem (MediaItem item, ThumbItems items, int index, boolean animatePosition) {
    if (item == null || items == null) {
      return false;
    }
    if (index == -1) {
      return false;
    }
    if (mediaView == null || mediaView.isMovingItem()) {
      return false;
    }
    /*TODO?
    if (animatePosition && items.expandFactor != 1f && items.expandFactor != 0f) {
      return false;
    }*/
    boolean changed = items.getFocusItemPosition() != index;
    if (changed && animatePosition) {
      items.swapFocusWithSecondary();
      items.setFocusItem(item, index, items.expandFactor);
    } else {
      if (items.setExpandingItems(index, -1, items.expandFactor)) {
        thumbsRecyclerView.invalidate();
      }
    }
    if (changed) {
      mediaView.getBaseCell().setMedia(item, true, Screen.dp(THUMBS_HEIGHT) + Screen.dp(THUMBS_PADDING) * 2, animatePosition ? 1.0f : getThumbStrength());
      stack.forceIndex(items.indexInStack + index);
    }
    if (animatePosition) {
      float expandFactor = items.expandFactor;
      items.expandFactor = 1f;
      ensureThumbsPosition(true, true);
      items.expandFactor = expandFactor;
      cancelAutoThumbScroller();
      thumbsRecyclerView.stopScroll();
      if (desiredThumbScrollByX == 0) {
        ensureThumbsPosition(true, false);
        thumbsRecyclerView.invalidate();
      } else {
        autoScrollItems = items;
        autoScrollStartValue = items.expandFactor;
        autoScrollDistance = desiredThumbScrollByX;
        autoScrollDistanceLast = 0;
        if (autoThumbScroller == null) {
          autoThumbScroller = new FactorAnimator(ANIMATOR_THUMBS_AUTO_SCROLLER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        }
        autoThumbScroller.animateTo(1f);
      }
    }
    return changed;
  }

  private int totalThumbScrollX, desiredThumbScrollX;
  private int desiredThumbScrollByX;

  private void ensureThumbsPosition (boolean scrollBy, boolean onlyMeasure) {
    if (thumbsRecyclerView.getMeasuredWidth() > 0) {
      ensureThumbsPosition(scrollBy, onlyMeasure, thumbsAdapter.items.getFocusItemPosition());
    }
  }

  private void ensureThumbsPosition (boolean scrollBy, boolean onlyMeasure, int desiredPosition) {
    int firstVisiblePosition = thumbsLayoutManager.findFirstVisibleItemPosition();
    View firstVisibleView = firstVisiblePosition != RecyclerView.NO_POSITION ? thumbsLayoutManager.findViewByPosition(firstVisiblePosition) : null;
    int firstVisibleOffset = firstVisibleView != null ? Lang.rtl() ? thumbsRecyclerView.getMeasuredWidth() - thumbsLayoutManager.getDecoratedRight(firstVisibleView) : thumbsLayoutManager.getDecoratedLeft(firstVisibleView) : 0;

    final int thumbWidth = calculateThumbWidth();
    int halfParentWidth = thumbsRecyclerView.getMeasuredWidth() / 2;
    int offsetX = halfParentWidth - thumbWidth / 2;

    int totalScrollX;
    if (firstVisiblePosition != RecyclerView.NO_POSITION && firstVisibleView != null) {
      totalScrollX = -firstVisibleOffset + thumbWidth * firstVisiblePosition;
      if (firstVisiblePosition > 0) {
        totalScrollX += offsetX;
      }
      if (totalScrollX < 0) {
        totalScrollX = 0;
      }
    } else {
      totalScrollX = -1;
    }
    int desiredScrollX = -1;

    if (desiredPosition != -1) {
      int desiredOffset = desiredPosition != 0 ? offsetX : 0;
      int secondaryPosition = thumbsAdapter.items.getSecondaryItemPosition();
      if (secondaryPosition != -1) {
        float expandFactor = thumbsAdapter.items.getExpandFactor();
        if (secondaryPosition < desiredPosition) {
          desiredOffset += (int) ((float) (thumbWidth * (desiredPosition - secondaryPosition)) * (1f - expandFactor));
        } else if (secondaryPosition > desiredPosition) {
          desiredOffset -= (int) ((float) (thumbWidth * (secondaryPosition - desiredPosition)) * (1f - expandFactor));
        }
        // TODO skip items, if (desiredOffset / thumbsWidth) != 0
      }
      if (scrollBy && totalScrollX != -1) {
        desiredScrollX = desiredPosition * thumbWidth - desiredOffset;
        if (desiredPosition != 0) {
          desiredScrollX += offsetX;
        }
        if (desiredScrollX != totalScrollX && !onlyMeasure) {
          thumbsRecyclerView.scrollBy((desiredScrollX - totalScrollX) * (Lang.rtl() ? -1 : 1), 0);
        } else {
          desiredThumbScrollByX = desiredScrollX - totalScrollX;
        }
      } else {
        if (!onlyMeasure) {
          thumbsLayoutManager.scrollToPositionWithOffset(desiredPosition, desiredOffset);
        } else {
          desiredThumbScrollByX = 0;
        }
      }
    }

    if (onlyMeasure) {
      totalThumbScrollX = totalScrollX;
      desiredThumbScrollX = desiredScrollX;
    }
  }

  private float slideFactor;

  @Override
  public void onFactorChanged (MediaView view, float factor) {
    if (thumbsAnimator == null || !thumbsAnimator.getValue() || thumbsAdapter.items == null) {
      return;
    }

    this.slideFactor = factor;

    int focusItemIndex = stack.getCurrentIndex();

    int secondaryItemIndex = -1;
    float expandFactor = 1f;
    if (factor < 0f && focusItemIndex > 0) {
      secondaryItemIndex = thumbsAdapter.items.stackIndexToLocalIndex(focusItemIndex - 1);
      expandFactor = 1f + factor;
    } else if (factor > 0f && focusItemIndex + 1 < stack.getCurrentSize()) {
      secondaryItemIndex = thumbsAdapter.items.stackIndexToLocalIndex(focusItemIndex + 1);
      expandFactor = 1f - factor;
    }
    if (secondaryItemIndex == -1) {
      expandFactor = 1f;
    }
    focusItemIndex = thumbsAdapter.items.stackIndexToLocalIndex(focusItemIndex);
    if (focusItemIndex == -1) {
      return;
    }

    if (thumbsAdapter.items.setExpandingItems(focusItemIndex, secondaryItemIndex, expandFactor)) {
      ensureThumbsPosition(true, false);
      thumbsRecyclerView.invalidate();
    }
  }

  private static class ThumbRecyclerView extends RecyclerView implements Runnable, ClickHelper.Delegate {
    private final ClickHelper clickHelper;

    public ThumbRecyclerView (Context context) {
      super(context);
      this.clickHelper = new ClickHelper(this);
      this.clickHelper.setNoSound(true);
    }

    private boolean cancelClick;

    private MediaViewController controller;

    public void setController (MediaViewController controller) {
      this.controller = controller;
    }

    private int lastWidth;

    @Override
    protected void onMeasure (int widthSpec, int heightSpec) {
      super.onMeasure(widthSpec, heightSpec);
      int newWidth = getMeasuredWidth();
      if (lastWidth != newWidth) {
        lastWidth = newWidth;
        post(this);
      }
    }

    public void cancelClick () {
      cancelClick = true;
    }

    @Override
    public void run () {
      controller.resetThumbsPositionByLayout();
    }

    private boolean allowTouch () {
      return controller != null && (!controller.mediaView.isMovingItem() && controller.thumbsFactor == 1f) && Views.isValid(this);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (e.getAction() != MotionEvent.ACTION_DOWN || allowTouch()) {
        boolean down = e.getAction() == MotionEvent.ACTION_DOWN;
        if (down && controller.isAutoScrolling()) {
          if (!controller.dropAutoThumbScroll()) {
            return false;
          }
        }
        boolean res = super.onTouchEvent(e);
        if (down || !(cancelClick = cancelClick || controller.areThumbsScrolling())) {
          cancelClick = false;
          res = clickHelper.onTouchEvent(this, e) || res;
        }
        return res;
      }
      return false;
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent e) {
      return super.onInterceptTouchEvent(e) || (e.getAction() == MotionEvent.ACTION_DOWN && !allowTouch());
    }

    // Click

    @Override
    public boolean needClickAt (View view, float x, float y) {
      return true;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      if (controller.areThumbsScrolling() || y < 0 || y > getMeasuredHeight() || controller.thumbsScrolled || controller.isAutoScrolling()) {
        return;
      }
      final int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = getChildAt(i);
        if (child == null) {
          continue;
        }
        ThumbView thumbView = ThumbViewHolder.getThumbView(child);
        MediaItem item = thumbView.getItem();
        ThumbItems items = thumbView.getItems();
        if (items != null && thumbView.preview.isInsideReceiver(x, y)) {
          if (controller.fastShowMediaItem(item, items, items.indexOf(item), true)) {
            ViewUtils.onClick(this);
            return;
          }
        }
      }
    }
  }

  private static class ThumbView extends View implements AttachDelegate, MediaItem.ThumbExpandChangeListener, Destroyable, InvalidateContentProvider {
    private final DoubleImageReceiver preview;
    private final AvatarReceiver avatarReceiver;

    private ThumbItems items;
    private MediaItem item;

    public ThumbView (Context context, RecyclerView drawTarget) {
      super(context);
      preview = new DoubleImageReceiver(drawTarget, 0);
      avatarReceiver = new AvatarReceiver(drawTarget);
      avatarReceiver.setFullScreen(true, false);
      avatarReceiver.setScaleMode(AvatarReceiver.ScaleMode.CENTER_CROP);
    }

    @Override
    public void performDestroy () {
      preview.destroy();
      avatarReceiver.destroy();
    }

    @Override
    public void onThumbExpandFactorChanged (MediaItem item) {
      if (getParent() != null) {
        ((ViewGroup) getParent()).invalidate();
      }
    }

    private boolean isAttached = true;

    @Override
    public void attach () {
      preview.attach();
      avatarReceiver.attach();
      isAttached = true;
      if (item != null) {
        item.attachToThumbView(this);
      }
    }

    @Override
    public void detach () {
      preview.detach();
      avatarReceiver.detach();
      isAttached = false;
      if (item != null) {
        item.detachFromThumbView(this);
      }
    }

    @Override
    public boolean invalidateContent (Object cause) {
      if (this.item != null && this.item.getPreviewImageFile() == null && (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE || this.item.isLoaded())) {
        if (this.item.isAvatar()) {
          this.item.requestAvatar(this.avatarReceiver, false);
          preview.clear();
        } else {
          this.preview.getImageReceiver().requestFile(item.getThumbImageFile(Screen.dp(THUMBS_HEIGHT) + Screen.dp(THUMBS_PADDING) * 2, false));
          avatarReceiver.clear();
        }
        return true;
      }
      return false;
    }

    public MediaItem getItem () {
      return item;
    }

    public ThumbItems getItems () {
      return items;
    }

    public void setItem (MediaItem item, ThumbItems items) {
      if (this.item != item) {
        if (this.item != null && isAttached) {
          this.item.detachFromThumbView(this);
        }
        this.item = item;
        this.items = items;
        if (item.isAvatar()) {
          item.requestAvatar(avatarReceiver, false);
          preview.clear();
        } else {
          preview.requestFile(item.getThumbImageMiniThumb(), item.getThumbImageFile(Screen.dp(THUMBS_HEIGHT) + Screen.dp(THUMBS_PADDING) * 2, false));
          avatarReceiver.clear();
        }
        // preview.requestFile(item != null ? item.getThumbImageFile(Screen.dp(THUMBS_HEIGHT) + Screen.dp(THUMBS_PADDING) * 2, false) : null);
        layoutImage();
        if (isAttached) {
          item.attachToThumbView(this);
        }
        invalidate();
      } else if (this.items != items) {
        this.items = items;
        invalidate();
      }
    }

    private int thumbStartWidth, thumbEndWidth, thumbHeight;

    private boolean layoutImage () {
      int totalPaddingHorizontal = Screen.dp(THUMBS_SPACING_BETWEEN);
      int paddingVertical = Screen.dp(THUMBS_PADDING);
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();

      if (width == 0 || height == 0) {
        return false;
      }

      int thumbHeight = (height - paddingVertical * 2);

      int startWidth = (width - totalPaddingHorizontal);
      int endWidth = ThumbItems.getEndWidth(item, startWidth, thumbHeight);

      if (this.thumbStartWidth != startWidth || this.thumbEndWidth != endWidth || this.thumbHeight != thumbHeight) {
        this.thumbStartWidth = startWidth;
        this.thumbEndWidth = endWidth;
        this.thumbHeight = thumbHeight;
        return true;
      }
      return false;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      layoutImage();
    }

    public void drawImage (Canvas c, int centerX, int startY, float alpha, float expandAllowance) {
      layoutImage();

      if (thumbStartWidth == 0 || thumbHeight == 0) {
        return;
      }

      float expandFactor = items != null ? items.getExpandFactor(item) * expandAllowance : 0f;
      int thumbWidth = thumbStartWidth + (int) ((float) (thumbEndWidth - thumbStartWidth) * expandFactor);

      Receiver preview = item != null && item.isAvatar() ? avatarReceiver : this.preview;
      if (alpha != 1f) {
        preview.setPaintAlpha(alpha);
      }
      int startX = centerX - thumbWidth / 2;
      preview.setBounds(startX, startY, startX + thumbWidth, startY + thumbHeight);
      if (preview.needPlaceholder()) {
        preview.drawPlaceholderRounded(c, 0, 0x10ffffff);
      }
      preview.draw(c);

      if (alpha != 1f) {
        preview.restorePaintAlpha();
      }
    }
  }

  private static class ThumbViewHolder extends RecyclerView.ViewHolder {
    public ThumbViewHolder (View itemView) {
      super(itemView);
    }

    public static ThumbViewHolder create (Context context, MediaViewController controller) {
      ThumbView thumbView = new ThumbView(context, controller.thumbsRecyclerView);
      thumbView.setLayoutParams(new RecyclerView.LayoutParams(controller.calculateThumbWidth(), ViewGroup.LayoutParams.MATCH_PARENT));
      return new ThumbViewHolder(thumbView);
      /*thumbView.setLayoutParams(FrameLayout.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      FrameLayout wrapView = new FrameLayout(context);
      wrapView.setLayoutParams(new RecyclerView.LayoutParams(controller.calculateThumbWidth(), ViewGroup.LayoutParams.MATCH_PARENT));
      wrapView.addView(thumbView);
      return new ThumbViewHolder(wrapView);*/
    }

    private static ThumbView getThumbView (View v) {
      return (ThumbView) v; // v instanceof ThumbView ? (ThumbView) v : (ThumbView) ((ViewGroup) v).getChildAt(0);
    }
  }

  private static class ThumbItems {
    private final ThumbAdapter adapter;
    private final ArrayList<MediaItem> items;
    private int indexInStack;

    public ThumbItems (ThumbAdapter adapter, ArrayList<MediaItem> items, int indexInStack) {
      this.adapter = adapter;
      this.items = items;
      this.indexInStack = indexInStack;
    }

    public int stackIndexToLocalIndex (int stackIndex) {
      return items == null || stackIndex < indexInStack || stackIndex >= indexInStack + items.size() ? -1 : stackIndex - indexInStack;
    }

    public int getIndexInStack () {
      return indexInStack;
    }

    public long getMediaGroupId () {
      return items.get(0).getMessage().mediaAlbumId;
    }

    public int indexOf (MediaItem item) {
      return items.indexOf(item);
    }

    public int size () {
      return items != null ? items.size() : 0;
    }

    public MediaItem get (int i) {
      return items != null && i >= 0 && i < items.size() ? items.get(i) : null;
    }

    public MediaItem getFirst () {
      return get(0);
    }

    public MediaItem getLast () {
      return items != null && !items.isEmpty() ? items.get(items.size() - 1) : null;
    }

    public void deleteItem (int indexInStack, MediaItem item) {
      int index = indexOf(item);
      if (index != -1) {
        items.remove(index);
        if (this.indexInStack > indexInStack) {
          this.indexInStack--;
        }
        adapter.notifyItemRemoved(index);
        adapter.controller.thumbsRecyclerView.invalidateItemDecorations();
      }
    }

    public void replaceItem (int indexInStack, MediaItem oldItem, MediaItem newItem) {
      if (secondaryItem == oldItem) {
        secondaryItem = newItem;
      }
      if (focusItem == oldItem) {
        focusItem = newItem;
      }
      int index = indexOf(oldItem);
      if (index != -1) {
        items.set(index, newItem);
        adapter.notifyItemChanged(index);
        adapter.controller.thumbsRecyclerView.invalidateItemDecorations();
      }
    }

    public void addItems (ArrayList<MediaItem> items, boolean onTop, boolean checkMediaGroupId) {
      final int totalAddedCount = items.size();
      if (checkMediaGroupId) {
        ArrayList<MediaItem> addItems = null;

        long mediaGroupId = this.items.get(0).getMessage().mediaAlbumId;
        int estimatedSize = Math.max(1, TdConstants.MAX_MESSAGE_GROUP_SIZE - this.items.size());
        if (onTop) {
          final int size = items.size();
          for (int i = size - 1; i >= 0; i--) {
            MediaItem item = items.get(i);
            if (item.getMessage().mediaAlbumId != mediaGroupId) {
              break;
            }
            if (addItems == null) {
              addItems = new ArrayList<>(estimatedSize);
            }
            addItems.add(item);
          }
        } else {
          for (MediaItem item : items) {
            if (item.getMessage().mediaAlbumId != mediaGroupId) {
              break;
            }
            if (addItems == null) {
              addItems = new ArrayList<>(estimatedSize);
            }
            addItems.add(item);
          }
        }

        if (addItems == null) {
          if (onTop) {
            indexInStack += totalAddedCount;
          }
          return;
        }

        items = addItems;
      }

      int addedItemCount = items.size();
      if (onTop) {
        indexInStack += (totalAddedCount - addedItemCount);
      }

      int insertIndex;
      int changedIndex;
      if (onTop) {
        insertIndex = 0;
        changedIndex = items.size();
        this.items.addAll(0, items);
      } else {
        insertIndex = this.items.size();
        changedIndex = insertIndex - 1;
        this.items.addAll(items);
      }

      if (adapter.items == this) {
        if (onTop) {
          shiftPositions(addedItemCount);
        }
        adapter.controller.saveThumbsPosition();
        adapter.notifyItemRangeInserted(insertIndex, addedItemCount);
        adapter.notifyItemChanged(insertIndex);
        adapter.controller.thumbsRecyclerView.invalidateItemDecorations();
        adapter.controller.restoreThumbsPosition(onTop ? addedItemCount : 0);
      }
    }

    private MediaItem focusItem;
    private int focusItemPosition = -1;

    private MediaItem secondaryItem;
    private int secondaryItemPosition = -1;

    private void shiftPositions (int shift) {
      if (focusItemPosition != -1) {
        focusItemPosition += shift;
      }
      if (secondaryItemPosition != -1) {
        secondaryItemPosition += shift;
      }
    }

    public boolean setExpandingItems (int focusItemPosition, int secondaryItemPosition, float expandFactor) {
      if (this.focusItemPosition != focusItemPosition || this.secondaryItemPosition != secondaryItemPosition || this.expandFactor != expandFactor) {
        this.focusItemPosition = focusItemPosition;
        this.focusItem = get(focusItemPosition);
        this.secondaryItemPosition = secondaryItemPosition;
        this.secondaryItem = get(secondaryItemPosition);
        this.expandFactor = expandFactor;
        return true;
      }
      return false;
    }

    public int getFocusItemPosition () {
      return focusItemPosition;
    }

    public int getSecondaryItemPosition () {
      return secondaryItemPosition;
    }

    public float getExpandFactor () {
      return expandFactor;
    }

    public void setFocusItem (MediaItem item, int position, float expandFactor) {
      this.focusItem = item;
      this.focusItemPosition = position;
      this.expandFactor = expandFactor;
    }

    public void setSecondaryItem (MediaItem item, int position) {
      this.secondaryItem = item;
      this.secondaryItemPosition = position;
    }

    public static int getEndWidth (MediaItem item, int startWidth, int thumbHeight) {
      int endWidth = 0;
      if (item != null) {
        int sourceWidth = item.getWidth();
        int sourceHeight = item.getHeight();
        float scale = sourceHeight != 0 ? (float) thumbHeight / (float) sourceHeight : 1f;
        endWidth = (int) ((float) sourceWidth * scale);
      }
      return Math.min(Math.max(startWidth, endWidth), Screen.dp(THUMBS_WIDTH_MAX));
    }

    private float expandFactor;

    public void swapFocusWithSecondary () {
      int tempPosition = secondaryItemPosition;
      MediaItem tempItem = secondaryItem;

      secondaryItemPosition = focusItemPosition;
      secondaryItem = focusItem;

      focusItemPosition = tempPosition;
      focusItem = tempItem;

      expandFactor = 1f - expandFactor;
    }

    public float getExpandFactor (MediaItem item) {
      return item == null ? 0f : item == focusItem ? expandFactor : item == secondaryItem ? 1f - expandFactor : 0f;
    }

    public float getTranslationX (MediaItem item, int position, float expandAllowance) {
      float x = 0;
      float add = Screen.dp(THUMBS_SPACING_ADD);
      float expandFactor = this.expandFactor * expandAllowance;
      int thumbHeight = Screen.dp(THUMBS_HEIGHT);
      int startWidth = adapter.controller.calculateThumbWidth();
      if (focusItem != null && focusItem != item && expandFactor > 0f) {
        int endWidth = getEndWidth(focusItem, startWidth, thumbHeight);
        x += (add + (endWidth - startWidth) / 2) * expandFactor * (position < focusItemPosition ? -1f : 1f);
      }
      if (secondaryItem != null && secondaryItem != item && expandFactor < 1f) {
        int endWidth = getEndWidth(secondaryItem, startWidth, thumbHeight);
        x += (add + (endWidth - startWidth) / 2) * (1f - expandFactor) * (position < secondaryItemPosition ? -1f : 1f);
      }
      return x;
    }
  }

  private static class ThumbAdapter extends RecyclerView.Adapter<ThumbViewHolder> {
    private final Context context;
    private final MediaViewController controller;

    private ThumbItems items;

    public ThumbAdapter (Context context, MediaViewController controller) {
      this.context = context;
      this.controller = controller;
    }

    public void setItems (ThumbItems items) {
      if (this.items == null) {
        this.items = items;
        if (items != null) {
          notifyItemRangeInserted(0, items.size());
        }
      } else if (items == null) {
        int oldItemCount = this.items.size();
        this.items = null;
        if (oldItemCount > 0) {
          notifyItemRangeRemoved(0, oldItemCount);
        }
      } else if (this.items.size() == items.size()) {
        this.items = items;
        notifyItemRangeChanged(0, items.size());
      } else {
        int oldItemCount = this.items.size();
        this.items = null;
        notifyItemRangeRemoved(0, oldItemCount);
        this.items = items;
        notifyItemRangeInserted(0, items.size());
      }
    }

    @Override
    public ThumbViewHolder onCreateViewHolder (ViewGroup viewGroup, int i) {
      return ThumbViewHolder.create(context, controller);
    }

    @Override
    public int getItemCount () {
      return items != null ? items.size() : 0;
    }

    @Override
    public void onBindViewHolder (ThumbViewHolder holder, int index) {
      ThumbView thumbView = ThumbViewHolder.getThumbView(holder.itemView);
      if (items != null) {
        MediaItem item = items.get(index);
        thumbView.setItem(item, items);
        int threshold = Math.max(LOAD_THRESHOLD, controller.thumbsRecyclerView.getChildCount());
        if (index >= items.size() - threshold) {
          controller.loadMoreIfNeeded(true, true);
        } else if (index - threshold <= 0) {
          controller.loadMoreIfNeeded(true, false);
        }
      } else {
        thumbView.setItem(null, null);
      }
    }

    @Override
    public void onViewAttachedToWindow (ThumbViewHolder holder) {
      Views.attach(ThumbViewHolder.getThumbView(holder.itemView));
    }

    @Override
    public void onViewDetachedFromWindow (ThumbViewHolder holder) {
      Views.detach(ThumbViewHolder.getThumbView(holder.itemView));
    }
  }

  // CreateView

  @Override
  protected View onCreateView (Context context) {
    context().closeOtherPips();

    if (SET_FULLSCREEN_ON_OPEN) {
      setFullScreen(true);
    }

    restorePipFactors();

    popupView = new PopupLayout(context);
    popupView.setOverlayStatusBar(true);
    if (mode == MODE_SECRET) {
      popupView.setIgnoreHorizontal();
    }
    if (mode == MODE_GALLERY) {
      popupView.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    if (isFromCamera) {
      popupView.setIgnoreSystemNavigationBar(true);
    }
    popupView.setTouchProvider(this);
    popupView.setActivityListener(new BaseActivity.ActivityListener() {
      @Override
      public void onActivityPause () {
        mediaView.onMediaActivityPause();
      }

      @Override
      public void onActivityResume () {
        mediaView.onMediaActivityResume();
      }

      @Override
      public void onActivityDestroy () { }

      @Override
      public void onActivityPermissionResult (int code, boolean granted) { }
    });
    if (!canRunFullscreen()) {
      popupView.setNeedRootInsets();
      popupView.setHideKeyboard();
      popupView.init(false);
    } else {
      popupView.setNeedRootInsets();
      popupView.init(true);
      popupView.setIgnoreAllInsets(true);
    }
    popupView.setBoundController(this);

    flingDetector = new FlingDetector(context, this);
    contentView = new FrameLayoutFix(context) {
      private int lastWidth, lastHeight;

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        updatePipLayout(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec), true, true);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mode == MODE_GALLERY) {
          int newWidth = getMeasuredWidth();
          int newHeight = getMeasuredHeight();
          if (lastWidth == 0 || lastHeight == 0) {
            lastWidth = newWidth;
            lastHeight = newHeight;
          } else if (lastWidth != newWidth || lastHeight != newHeight) {
            lastWidth = newWidth;
            lastHeight = newHeight;
            resetMediaPaddings(currentSection);
          }
        }
      }

      private float startX, startY, diffX, diffY;
      private float slideStartY;
      private float slideStartX;

      private boolean disallowIntercept;

      @Override
      public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        this.disallowIntercept = disallowIntercept;
      }

      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        if (slideAnimator != null && slideAnimator.isAnimating()) {
          return true;
        }
        if (mode == MODE_SECRET || inCaption || (disallowIntercept && e.getAction() != MotionEvent.ACTION_DOWN)) {
          return super.onInterceptTouchEvent(e);
        }
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            startX = e.getX();
            startY = e.getY();
            listenCloseBySlide = canCloseBySlide() && pipFactor == 0f && !mediaView.isZoomed() && mediaView.isBaseVisible();
            break;
          }
          case MotionEvent.ACTION_MOVE: {
            if (listenCloseBySlide) {
              float x = e.getX();
              float y = e.getY();
              if (Math.abs(startY - y) >= Screen.getTouchSlopBig() && Math.abs(startX - x) < Screen.getTouchSlop() * 1.65f) {
                mediaView.dropPreview(MediaView.DIRECTION_AUTO, 0f);
                listenCloseBySlide = false;
                slideStartX = x;
                slideStartY = y;
                setInSlideMode(x, y);
                return true;
              } else if (Math.abs(startX - x) >= Screen.getTouchSlopY()) {
                listenCloseBySlide = false;
                break;
              }
            } else if (inSlideMode) {
              return true;
            } else if (listenCatchPip) {
              float x = e.getX();
              float y = e.getY();
              if (Math.max(Math.abs(startX - x), Math.abs(startY - y)) > Screen.getTouchSlopBig()) {
                listenCatchPip = false;
                diffX = startX - x;
                diffY = startY - y;
                startX = x;
                startY = y;
                setPipUp(true, 0f, 0f);
                return true;
              }
            } else if (isPipUp) {
              return true;
            }
            break;
          }
        }
        flingDetector.onTouchEvent(e);
        return super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (slideAnimator != null && slideAnimator.isAnimating()) {
          return true;
        }
        if (mode == MODE_SECRET) {
          return true;
        }
        flingDetector.onTouchEvent(e);
        switch (e.getAction()) {
          case MotionEvent.ACTION_MOVE: {
            if (isPipUp) {
              float x = e.getX();
              float y = e.getY();
              movePip(x - startX, y - startY, mediaView.getMeasuredWidth(), mediaView.getMeasuredHeight());
              return true;
            }
            if (inSlideMode) {
              float x = e.getX();
              float y = e.getY();
              setSlide(x - slideStartX, y - slideStartY, slideStartX, false, true);
              return true;
            }
            break;
          }
          case MotionEvent.ACTION_CANCEL:
          case MotionEvent.ACTION_UP: {
            if (isPipUp) {
              setPipUp(false, 0f, 0f);
              return true;
            }
            if (inSlideMode) {
              dropSlideMode(0f, 0f, false);
            }
            break;
          }
        }
        return super.onTouchEvent(e);
      }

      @Override
      protected void onDraw (Canvas c) {
        if (commonFactor == 0f) {
          return;
        }
        float alpha = Math.max(0f, Math.min(1f, commonFactor));

        final boolean saved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null && !inSlideMode && lastSlideX == 0f && lastSlideY == 0f && currentThumb != null && commonFactor > 0f && commonFactor < 1f && !currentThumb.noBounce();
        final int saveCount = saved ? ViewSupport.clipPath(c, path) : Integer.MIN_VALUE;

        if (currentThumb != null && commonFactor < 1f && !inSlideMode && lastSlideX == 0f && lastSlideY == 0f) {
          currentThumb.drawPlaceholder(c);
        }

        alpha *= (1f - pipFactor);

        if (alpha > 0f) {
          int color = (int) (255f * alpha) << 24;
          c.drawColor(color);
        }

        if (saved) {
          ViewSupport.restoreClipPath(c, saveCount);
        }
      }
    };
    if (Config.HARDWARE_MEDIA_VIEWER) {
      Views.setLayerType(contentView, View.LAYER_TYPE_HARDWARE);
    }
    contentView.setWillNotDraw(false);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    mediaView = new MediaView(context);
    mediaView.setFactorChangeListener(this);
    if (mode == MODE_GALLERY) {
      mediaView.setDisableDoubleTapZoom(true);
    }
    mediaView.prepare(mode != MODE_SECRET);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && (mode == MODE_MESSAGES || mode == MODE_SIMPLE) && !Config.DISABLE_VIEWER_ELEVATION) {
      mediaView.setElevation(Screen.dp(2f));
      mediaView.setOutlineProvider(new android.view.ViewOutlineProvider() {
        private final int[] size = new int[2];
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          if (getPipSize(size)) {
            // size[0] /= view.getScaleX();
            // size[1] /= view.getScaleY();

            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();

            int left = width / 2 - size[0] / 2;
            int right = width / 2 + size[0] / 2;
            int top = height / 2 - size[1] / 2;
            int bottom = height / 2 + size[1] / 2;
            outline.setRect(left, top, right, bottom);
          } else {
            outline.setEmpty();
          }
        }
      });
    }
    mediaView.setCellCallback(this);
    mediaView.setBoundController(this);
    mediaView.initWithStack(stack);
    stack.setCallback(this);
    mediaView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(mediaView);

    if (needHeader()) {
      headerCell = new DoubleHeaderView(context);
      headerCell.setThemedTextColor(this);
      headerCell.initWithMargin(measureButtonsPadding(), true);
      if (mode == MODE_PROFILE) {
        MediaItem item = stack.getCurrent();
        headerCell.setTitle(tdlib.senderName(item.getSourceSender()));
      } else if (mode == MODE_CHAT_PROFILE) {
        MediaItem item = stack.getCurrent();
        TdApi.Chat chat = tdlib.chat(item.getSourceChatId());
        headerCell.setTitle(chat != null ? chat.title : "Chat#" + item.getSourceChatId());
      }
      onMediaStackChanged(false);

      headerView = new HeaderView(context);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && (mode == MODE_MESSAGES || mode == MODE_SIMPLE) && !Config.DISABLE_VIEWER_ELEVATION) {
        headerView.setElevation(Screen.dp(3f));
      }
      attachHeaderViewWithoutNavigation(headerView);
      headerView.initWithSingleController(this, (SET_FULLSCREEN_ON_OPEN || canRunFullscreen()) && !Config.CUTOUT_ENABLED);
      headerView.getFilling().setShadowAlpha(0f);
      int leftMargin = Screen.dp(68f);
      int rightMargin = measureButtonsPadding();
      Views.setMargins((FrameLayout.LayoutParams) headerCell.getLayoutParams(), Lang.rtl() ? rightMargin : leftMargin, headerView.needOffsets() ? HeaderView.getTopOffset() : 0, Lang.rtl() ? leftMargin : rightMargin, 0);
      contentView.addView(headerView);
    }

    switch (mode) {
      case MODE_GALLERY: {
        TdApi.Chat chat = getArgumentsStrict().receiverChatId != 0 ? tdlib.chat(getArgumentsStrict().receiverChatId) : null;

        mediaView.setOffsets(0, 0, 0, 0, 0); // Screen.dp(56f)
        editWrap = new FrameLayoutFix(context);
        editWrap.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
        editWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM));

        backButton = new EditButton(context);
        backButton.setId(R.id.btn_back);
        backButton.setIcon(R.drawable.baseline_arrow_back_24, false, false);
        backButton.setOnClickListener(this);
        backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        editWrap.addView(backButton);

        sendButton = new EditButton(context);
        sendButton.setId(R.id.btn_send);
        sendButton.setIcon(R.drawable.deproko_baseline_send_24, false, false);
        sendButton.setOnClickListener(this);
        sendButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
        sendButton.setBackgroundResource(R.drawable.bg_btn_header_light);
        editWrap.addView(sendButton);

        if (chat != null && chat.messageSenderId != null) {
          senderSendIcon = new MediaLayout.SenderSendIcon(context, tdlib(), chat.id);
          senderSendIcon.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(19), Screen.dp(19), Gravity.RIGHT | Gravity.BOTTOM, 0, 0, Screen.dp(11), Screen.dp(8)));
          senderSendIcon.setBackgroundColorId(getHeaderColorId());
          senderSendIcon.update(chat.messageSenderId);
          editWrap.addView(senderSendIcon);
        }

        if (chat != null) {
          tdlib.ui().createSimpleHapticMenu(this, chat.id, () -> currentActiveButton == 0, this::canDisableMarkdown, () -> true, hapticItems -> {
            if (sendDelegate != null && sendDelegate.allowHideMedia()) {
              hapticItems.add(0,
                new HapticMenuHelper.MenuItem(R.id.btn_spoiler, Lang.getString(R.string.HideMedia), R.drawable.deproko_baseline_whatshot_24)
                  .setIsCheckbox(true, sendDelegate.isHideMediaEnabled())
                  .setOnClickListener(new HapticMenuHelper.OnItemClickListener() {
                    private TooltipOverlayView.TooltipInfo hotTooltipInfo;

                    @Override
                    public boolean onHapticMenuItemClick (View view, View parentView, HapticMenuHelper.MenuItem item) {
                      if (view.getId() == R.id.btn_spoiler) {
                        if (item.isCheckboxSelected) {
                          hotTooltipInfo = context().tooltipManager().builder(view)
                            .icon(R.drawable.baseline_whatshot_24)
                            .color(context().tooltipManager().overrideColorProvider(getForcedTheme()))
                            .locate((targetView, outRect) -> {
                              int centerX = outRect.left + Screen.dp(29f);
                              int centerY = outRect.centerY();
                              int radius = Screen.dp(11f);
                              outRect.left = centerX - radius;
                              outRect.right = centerX + radius;
                              outRect.top = centerY - radius;
                              outRect.bottom = centerY + radius;
                            })
                            .show(tdlib, R.string.MediaSpoilerHint)
                            .hideDelayed();
                        } else {
                          if (hotTooltipInfo != null) {
                            hotTooltipInfo.hideNow();
                            hotTooltipInfo = null;
                          }
                        }
                        sendDelegate.onHideMediaStateChanged(item.isCheckboxSelected);
                        return true;
                      }
                      return false;
                    }
                  })
              );
            }
            int sendAsFile = canSendAsFile();
            if (sendAsFile != SEND_MODE_NONE) {
              boolean onlyVideos = sendAsFile == SEND_MODE_VIDEOS;
              int count = selectDelegate != null ? selectDelegate.getSelectedMediaCount() : 1;
              hapticItems.add(new HapticMenuHelper.MenuItem(R.id.btn_sendAsFile, count <= 1 ? Lang.getString(onlyVideos ? R.string.SendOriginal : R.string.SendAsFile) : Lang.plural(onlyVideos ? R.string.SendXOriginals : R.string.SendAsXFiles, count), R.drawable.baseline_insert_drive_file_24).setOnClickListener((view, parentView, item) -> {
                if (view.getId() == R.id.btn_sendAsFile) {
                  send(sendButton, Td.newSendOptions(), false, true);
                }
                return true;
              }).bindTutorialFlag(Settings.TUTORIAL_SEND_AS_FILE));
            }
            if (senderSendIcon != null) {
              hapticItems.add(0, senderSendIcon.createHapticSenderItem(chat).setOnClickListener((view, parentView, item) -> {
                openSetSenderPopup(chat);
                return true;
              }));
            }
          }, (sendOptions, disableMarkdown) -> {
            send(sendButton, sendOptions, disableMarkdown, false);
          }, getForcedTheme()).attachToView(sendButton);
        }

        editButtons = new LinearLayout(context);
        editButtons.setOrientation(LinearLayout.HORIZONTAL);
        editButtons.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        cropOrStickerButton = new EditButton(context);
        cropOrStickerButton.setOnClickListener(this);
        cropOrStickerButton.setId(R.id.btn_crop);
        // cropOrStickerButton.setSecondIcon(R.drawable.deproko_baseline_insert_sticker_24);
        cropOrStickerButton.setIcon(R.drawable.baseline_crop_rotate_24, false, false);
        cropOrStickerButton.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
        editButtons.addView(cropOrStickerButton);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
        params.leftMargin = Screen.dp(8f);
        params.rightMargin = Screen.dp(8f);

        paintOrMuteButton = new EditButton(context);
        paintOrMuteButton.setOnClickListener(this);
        paintOrMuteButton.setId(R.id.btn_paint);
        paintOrMuteButton.setIcon(R.drawable.baseline_brush_24, false, false);
        paintOrMuteButton.setLayoutParams(params);
        editButtons.addView(paintOrMuteButton);

        adjustOrTextButton = new EditButton(context);
        adjustOrTextButton.setId(R.id.btn_adjust);
        adjustOrTextButton.setOnClickListener(this);
        adjustOrTextButton.setSecondIcon(R.drawable.deproko_baseline_text_add_24);
        adjustOrTextButton.setIcon(R.drawable.baseline_tune_24, false, false);
        adjustOrTextButton.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
        editButtons.addView(adjustOrTextButton);

        if (chat != null && chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR && !tdlib.isBotChat(chat)) {
          stopwatchButton = new StopwatchHeaderButton(context);
          stopwatchButton.setBackgroundResource(R.drawable.bg_btn_header_light);
          stopwatchButton.forceValue(null, true);
          stopwatchButton.setId(R.id.menu_btn_stopwatch);
          stopwatchButton.setOnClickListener(this);
          stopwatchButton.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
          editButtons.addView(stopwatchButton);
        }


        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          editWrap.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void getOutline (View view, android.graphics.Outline outline) {
              int centerX = view.getMeasuredWidth() / 2;
              int centerY = view.getMeasuredHeight() / 2;
              int radius = Screen.dp(16f);
              outline.setRoundRect(centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius);
            }
          });
        }*/

        editWrap.addView(editButtons);

        updateIconStates(false);

        contentView.addView(editWrap);

        // Bottom wrap

        FrameLayoutFix.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
        fp.bottomMargin = Screen.dp(56f);

        bottomWrap = new FrameLayoutFix(context);
        bottomWrap.setLayoutParams(fp);
        checkBottomWrapY();

        InputView captionView = new InputView(context, tdlib, this) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (inlineResultsView != null) {
              inlineResultsView.updatePosition(true);
            }
          }

          private boolean isDown;

          @Override
          public boolean onTouchEvent (MotionEvent event) {
            boolean res = Views.onTouchEvent(this, event) && super.onTouchEvent(event);
            switch (event.getAction()) {
              case MotionEvent.ACTION_DOWN:
                isDown = true;
                break;
              case MotionEvent.ACTION_CANCEL:
              case MotionEvent.ACTION_UP:
                isDown = false;
                break;
            }
            return res;
          }

          @Override
          protected void onScrollChanged (int horiz, int vert, int oldHoriz, int oldVert) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
            contentView.requestDisallowInterceptTouchEvent(isDown);
          }
        };
        if (chat != null) {
          captionView.setNoPersonalizedLearning(Settings.instance().needsIncognitoMode(chat));
        }
        captionView.setHighlightColor(ColorUtils.alphaColor(0.2f, Theme.fillingTextSelectionColor()));
        captionView.setHighlightColor(getForcedTheme().getColor(ColorId.textSelectionHighlight));
        // addThemeHighlightColorListener(captionView, ColorId.textSelectionHighlight);
        captionView.setMaxCodePointCount(tdlib.maxCaptionLength());
        captionView.setIgnoreCustomStuff(false);
        captionView.getInlineSearchContext().setIsCaption(true);
        captionView.setInputListener(this);
        captionView.setBackgroundColor(0);
        captionView.addTextChangedListener(new TextWatcher() {
          @Override
          public void beforeTextChanged (CharSequence s, int start, int count, int after) {

          }

          @Override
          public void onTextChanged (CharSequence s, int start, int before, int count) {
            if (ignoreCaptionUpdate) {
              ignoreCaptionUpdate = false;
            } else {
              stack.getCurrent().setCaption(captionView.getOutputText(false));
            }
          }

          @Override
          public void afterTextChanged (Editable s) {

          }
        });
        captionView.setSpanChangeListener(v -> {
          if (!ignoreCaptionUpdate) {
            stack.getCurrent().setCaption(v.getOutputText(false));
          }
        });
        captionView.setHint(Lang.getString(R.string.AddCaption));
        captionView.setMaxLines(4);
        captionView.setId(R.id.input);
        captionView.setPadding(Screen.dp(55f), Screen.dp(15f), Screen.dp(55f), Screen.dp(14f));
        captionView.setTranslationX(-(Screen.dp(55f) - Screen.dp(14f)));
        captionView.setHintTextColor(0xbaffffff);
        captionView.setTextColor(0xffffffff);
        captionView.setTypeface(Fonts.getRobotoRegular());
        captionView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        captionView.setInputType(captionView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        this.captionView = captionView;

        captionDoneButton = new ImageView(context);
        captionDoneButton.setId(R.id.btn_caption_done);
        captionDoneButton.setOnClickListener(this);
        captionDoneButton.setScaleType(ImageView.ScaleType.CENTER);
        captionDoneButton.setImageResource(R.drawable.baseline_check_24);
        captionDoneButton.setColorFilter(0xffffffff);
        captionDoneButton.setAlpha(0f);
        captionDoneButton.setEnabled(false);
        captionDoneButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(55f), Screen.dp(52f), Gravity.RIGHT | Gravity.BOTTOM));

        captionEmojiButton = new ImageView(context());
        captionEmojiButton.setId(R.id.btn_caption_emoji);
        captionEmojiButton.setOnClickListener(this);
        captionEmojiButton.setScaleType(ImageView.ScaleType.CENTER);
        captionEmojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
        captionEmojiButton.setColorFilter(0xffffffff);
        captionEmojiButton.setAlpha(0f);
        captionEmojiButton.setEnabled(false);
        captionEmojiButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(55f), Screen.dp(52f), Gravity.LEFT | Gravity.BOTTOM));

        captionWrapView = new LinearLayout(context) {
          @Override
          public boolean onInterceptTouchEvent (MotionEvent ev) {
            return getVisibility() != View.VISIBLE || getAlpha() != 1f;
          }

          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            checkCaptionButtonsY();
          }

          @Override
          protected void onLayout (boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            checkCaptionButtonsY();
          }

          @Override
          public boolean onTouchEvent (MotionEvent event) {
            return getVisibility() == View.VISIBLE && getAlpha() == 1f && super.onTouchEvent(event);
          }
        };
        captionWrapView.setOrientation(LinearLayout.VERTICAL);
        captionWrapView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
        captionWrapView.addView(captionView);
        captionWrapView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        bottomWrap.addView(captionWrapView);

        bottomWrap.addView(captionDoneButton);
        bottomWrap.addView(captionEmojiButton);

        videoSliderView = new VideoControlView(context);
        videoSliderView.setSliderListener(this);
        videoSliderView.setOnPlayPauseClick(v -> {
          FileProgressComponent c = stack.getCurrent().getFileProgress();
          if (c != null) {
            c.performClick(v);
          }
        });
        videoSliderView.setInnerAlpha(0f);
        videoSliderView.setTranslationY(Screen.dp(56f));
        videoSliderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoSliderView.setSlideEnabled(stack.getCurrent().canSeekVideo());
        bottomWrap.addView(videoSliderView);
        if (needTrim()) {
          videoSliderView.addTrim(new VideoTimelineView.TimelineDelegate() {
            @Override
            public boolean canTrimTimeline (VideoTimelineView v) {
              return true; // TODO check if video is rendered
            }

            @Override
            public void onVideoLoaded (VideoTimelineView v, double totalDuration, double width, double height, int frameRate, long bitrate) {
              stack.getCurrent().getSourceGalleryFile().setVideoInformation((long) (totalDuration * 1_000_000.0), width, height, frameRate, bitrate);
            }

            private boolean needResume;

            @Override
            public void onTrimStartEnd (VideoTimelineView v, boolean isStarted) {
              if (isStarted) {
                if (needResume = isPlayingVideo) {
                  pauseVideoIfPlaying();
                }
              } else if (needResume) {
                stack.getCurrent().performClick(pipPlayPauseButton);
              }
            }

            @Override
            public void onSeekTo (VideoTimelineView v, float progress) {
              mediaView.setSeekProgress(progress);
            }

            @Override
            public void onTimelineTrimChanged (VideoTimelineView v, double totalDuration, double startTimeSeconds, double endTimeSeconds) {
              MediaItem item = stack.getCurrent();
              boolean changed;
              if (startTimeSeconds == 0 && endTimeSeconds == totalDuration) {
                changed = item.getSourceGalleryFile().setTrim(-1, -1, (long) (totalDuration * 1_000_000.0));
              } else {
                changed = item.getSourceGalleryFile().setTrim((long) (startTimeSeconds * 1_000_000.0), (long) (endTimeSeconds * 1_000_000.0), (long) (totalDuration * 1_000_000.0));
              }
              if (changed) {
                boolean needFrameUpdate = item.checkTrim();
                MediaCellView cellView = mediaView != null ? mediaView.findCellForItem(stack.getCurrent()) : null;
                if (cellView != null) {
                  cellView.checkTrim(needFrameUpdate);
                  long timeNow = cellView.getTimeNow();
                  long timeTotal = cellView.getTimeTotal();
                  if (timeNow == -1 || timeTotal == -1) {
                    timeNow = 0;
                    timeTotal = (long) ((endTimeSeconds - startTimeSeconds) * 1000.0);
                  }
                  videoSliderView.resetDuration(timeTotal, timeNow, true, true);
                } else {
                  item.invalidateContent(item);
                }
              }
            }
          }, getForcedTheme());
        }

        contentView.addView(bottomWrap);

        // Image overlay

        overlayView = new View(context) {
          @Override
          public boolean onTouchEvent (MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && showOtherMedias) {
              setShowOtherMedias(false);
              return true;
            }
            return false;
          }
        };
        overlayView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayView.setBackgroundColor(0xa0000000);
        overlayView.setAlpha(0f);
        contentView.addView(overlayView);

        // Checks

        int innerPadding = Screen.dp(9f);
        int size = Screen.dp(20f) * 2 + Screen.dp(1f) * 2 + innerPadding * 2;
        fp = FrameLayoutFix.newParams(size, size, Gravity.TOP | Gravity.RIGHT);
        fp.rightMargin = Screen.dp(20f) - innerPadding;
        fp.topMargin = Screen.dp(20f) - innerPadding + HeaderView.getTopOffset() / 2;

        checkView = new CheckView(context);
        checkView.setId(R.id.btn_check);
        checkView.initWithMode(CheckView.MODE_GALLERY);
        checkView.setPadding(innerPadding, innerPadding, 0, 0);
        checkView.setLayoutParams(fp);
        checkView.setOnClickListener(this);
        checkView.forceSetChecked(isCurrentItemSelected());
        contentView.addView(checkView);

        fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(30f), Gravity.RIGHT);
        fp.rightMargin = Screen.dp(78f);
        fp.topMargin = Screen.dp(26f) + HeaderView.getTopOffset() / 2;

        counterView = new CounterView(context);
        counterView.setId(R.id.btn_counter);
        counterView.setOnClickListener(this);
        counterView.setLayoutParams(fp);

        int count = getSelectedMediaCount();
        counterView.initCounter(Math.max(count, 1), false);
        forceCounterFactor(count == 0 ? 0f : 1f);
        contentView.addView(counterView);

        if (chat != null) {
          fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.getStatusBarHeight());
          if (HeaderView.getTopOffset() > 0) {
            fp.leftMargin = Screen.dp(8f);
            fp.topMargin = HeaderView.getTopOffset() + Screen.dp(4f);
          } else {
            fp.leftMargin = Screen.dp(12f);
            fp.topMargin = Screen.dp(4f);
          }

          receiverView = new LinearLayout(context);
          receiverView.setOrientation(LinearLayout.HORIZONTAL);
          receiverView.setAlpha(0f);
          receiverView.setLayoutParams(fp);

          LinearLayout.LayoutParams lp;

          lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(17f));

          ImageView imageView = new ImageView(context);
          imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
          imageView.setImageResource(R.drawable.baseline_arrow_upward_18);
          imageView.setColorFilter(0xffffffff);
          imageView.setAlpha((float) 0xaa / (float) 0xff);
          imageView.setLayoutParams(lp);
          receiverView.addView(imageView);

          lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          lp.leftMargin = Screen.dp(6f);

          TextView textView = new NoScrollTextView(context);
          textView.setTextColor(0xaaffffff);
          textView.setSingleLine(true);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          textView.setTypeface(Fonts.getRobotoMedium());
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
          textView.setText(tdlib.chatTitle(chat));
          textView.setLayoutParams(lp);
          receiverView.addView(textView);

          contentView.addView(receiverView);
        }

        break;
      }
      case MODE_SIMPLE:
      case MODE_MESSAGES: {
        FrameLayoutFix.LayoutParams fp;

        pipControlsWrap = new FrameLayoutFix(context) {
          private float startX, startY;

          @Override
          public boolean onTouchEvent (MotionEvent e) {
            switch (e.getAction()) {
              case MotionEvent.ACTION_DOWN: {
                if (pipFactor == 1f && (pipPositionAnimator == null || !pipPositionAnimator.isAnimating())) {
                  listenCatchPip = true;
                  startX = e.getX();
                  startY = e.getY();
                  return true;
                }
                break;
              }
              case MotionEvent.ACTION_CANCEL: {
                if (listenCatchPip) {
                  listenCatchPip = false;
                  return true;
                }
                break;
              }
              case MotionEvent.ACTION_UP: {
                if (listenCatchPip) {
                  if (Math.max(Math.abs(startX - e.getX()), Math.abs(startY - e.getY())) < Screen.getTouchSlop()) {
                    togglePipControlsVisibility();
                  }
                  listenCatchPip = false;
                  return true;
                }
                break;
              }
            }
            return super.onTouchEvent(e);
          }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
          pipControlsWrap.setTranslationZ(Screen.dp(10f));
        }
        pipControlsWrap.setAlpha(0f);
        pipControlsWrap.setVisibility(View.GONE);
        pipControlsWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pipBackgroundView = new View(context);
        pipBackgroundView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (Screen.dp(45f) * 1.2f), Gravity.BOTTOM));
        pipBackgroundView.setAlpha(.7f);
        ViewUtils.setBackground(pipBackgroundView, ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false));
        pipControlsWrap.addView(pipBackgroundView);

        fp = FrameLayoutFix.newParams(Screen.dp(45f), Screen.dp(45f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.rightMargin = Screen.dp(45f);
        pipOpenButton = new EditButton(context);
        pipOpenButton.setOnClickListener(this);
        pipOpenButton.setId(R.id.btn_inlineOpen);
        pipOpenButton.setIcon(R.drawable.deproko_baseline_outinline_24, false, false);
        pipOpenButton.setLayoutParams(fp);
        pipControlsWrap.addView(pipOpenButton);

        fp = FrameLayoutFix.newParams(Screen.dp(45f), Screen.dp(45f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pipPlayPauseButton = new PlayPauseButton(context);
        pipPlayPauseButton.setIsPlaying(isPlayingVideo, false);
        pipPlayPauseButton.setOnClickListener(this);
        pipPlayPauseButton.setId(R.id.btn_inlinePlayPause);
        pipPlayPauseButton.setLayoutParams(fp);
        pipControlsWrap.addView(pipPlayPauseButton);

        fp = FrameLayoutFix.newParams(Screen.dp(45f), Screen.dp(45f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.leftMargin = Screen.dp(45f);
        pipCloseButton = new EditButton(context);
        pipCloseButton.setOnClickListener(this);
        pipCloseButton.setId(R.id.btn_inlineClose);
        pipCloseButton.setIcon(R.drawable.baseline_close_24, false, false);
        pipCloseButton.setLayoutParams(fp);
        pipControlsWrap.addView(pipCloseButton);

        pipOverlayView = new PipOverlayView(context);
        pipOverlayView.setAlpha(0f);
        pipOverlayView.setBackgroundColor(0x77000000);
        pipOverlayView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pipControlsWrap.addView(pipOverlayView);

        contentView.addView(pipControlsWrap);

        fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);

        bottomWrap = new FrameLayoutFix(context);
        bottomWrap.setLayoutParams(fp);
        checkBottomWrapY();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DISABLE_VIEWER_ELEVATION) {
          bottomWrap.setElevation(Screen.dp(3f));
        }

        CustomTextView captionView = new CustomTextView(context, tdlib) {
          @Override
          public boolean onTouchEvent (MotionEvent event) {
            return Views.onTouchEvent(this, event) && super.onTouchEvent(event);
          }
        };
        captionView.setPadding(Screen.dp(14f), Screen.dp(14f), Screen.dp(14f), Screen.dp(14f));
        captionView.setTextColorId(ColorId.white);
        captionView.setTextSize(Screen.dp(16f));
        captionView.setTextStyleProvider(TGMessage.getTextStyleProvider());
        captionView.setLinkColorId(ColorId.caption_textLink, ColorId.caption_textLinkPressHighlight);
        captionView.setForcedTheme(getForcedTheme());
        captionView.setId(R.id.input);
        captionView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.captionView = captionView;

        captionWrapView = new LinearLayout(context);
        captionWrapView.setOrientation(LinearLayout.VERTICAL);
        captionWrapView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
        captionWrapView.setAlpha(0f);
        captionWrapView.addView(captionView);
        captionWrapView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        bottomWrap.addView(captionWrapView);

        videoSliderView = new VideoControlView(context);
        videoSliderView.setOnPlayPauseClick(v -> {
          FileProgressComponent c = stack.getCurrent().getFileProgress();
          if (c != null) {
            c.performClick(v);
          }
        });
        videoSliderView.setSliderListener(this);
        videoSliderView.setInnerAlpha(0f);
        videoSliderView.setAlpha(0f);
        videoSliderView.setTranslationY(Screen.dp(56f));
        videoSliderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoSliderView.setSlideEnabled(stack.getCurrent().canSeekVideo());
        bottomWrap.addView(videoSliderView);

        contentView.addView(bottomWrap);

        break;
      }
      case MODE_SECRET: {
        if (!stack.getCurrent().isSecretOutgoing()) {
          secretView = new SecretTimerView(context);
          secretView.setSecretPhoto(stack.getCurrent().getSecretPhoto());
          secretView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize(), Gravity.TOP));
          contentView.addView(secretView);
        }
        break;
      }
    }

    updateVideoState(false);
    updateCaption(false);

    loadMoreIfNeeded();

    TGLegacyManager.instance().addEmojiListener(this);
    tdlib.context().calls().addCurrentCallListener(this);
    if (stack.getCurrent().getSourceChatId() != 0) {
      subscribeToChatId(stack.getCurrent().getSourceChatId());
    }

    return contentView;
  }

  private int controlsMargin;

  private void setControlsMargin (int margin) {
    if (this.controlsMargin != margin) {
      this.controlsMargin = margin;
      Views.setBottomMargin(editWrap, margin);
      Views.setBottomMargin(bottomWrap, getBottomWrapMargin());
    }
  }

  private int bottomInnerMargin;

  public int getBottomInnerMargin () {
    return bottomInnerMargin;
  }

  private int appliedBottomPadding;

  private void setAppliedBottomPadding (int padding) {
    if (this.appliedBottomPadding != padding) {
      this.appliedBottomPadding = padding;
      checkBottomWrapY();
    }
  }

  @Override
  public void dispatchInnerMargins (int left, int top, int right, int bottom) {
    boolean changed = this.bottomInnerMargin != bottom;
    this.bottomInnerMargin = bottom;
    if (mode == MODE_GALLERY && isFromCamera) {
      setControlsMargin(bottom > Screen.getNavigationBarHeight() ? 0 : bottom);
      int bottomOffset = getSectionBottomOffset(SECTION_CROP);
      Views.setBottomMargin(cropTargetView, bottomOffset);
      if (cropAreaView != null) {
        cropAreaView.setOffsetBottom(bottomOffset);
      }
    }
    if (mediaView != null) {
      if (changed) {
        int offsetBottom = getSectionBottomOffset(currentSection);
        mediaView.setNavigationalOffsets(0, 0, offsetBottom);
        if (canRunFullscreen() && mode != MODE_SECRET) {
          setAppliedBottomPadding(offsetBottom);
        }
      }
      mediaView.layoutCells();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (!isMediaSent && getArguments() != null && getArgumentsStrict().deleteOnExit && stack != null) {
      for (int i = 0; i < stack.getCurrentSize(); i++) {
        MediaItem item = stack.get(i);
        if (item.getSourceGalleryFile().isFromCamera()) {
          item.deleteFiles();
        }
      }
    }
    if (thumbsRecyclerView != null) {
      Views.destroyRecyclerView(thumbsRecyclerView);
    }
    TGLegacyManager.instance().removeEmojiListener(this);
    if (mediaView != null) {
      mediaView.destroy();
    }
    if (secretView != null) {
      secretView.destroy();
    }
    tdlib.context().calls().removeCurrentCallListener(this);
    context.removeFullScreenView(this, true);
    context.removeHideNavigationView(this);
    if (captionView instanceof Destroyable) {
      ((Destroyable) captionView).performDestroy();
    }
    subscribeToChatId(0);
  }

  @Override
  public void onCurrentCallChanged (Tdlib tdlib, @Nullable TdApi.Call call) {
    if (call != null && !call.isOutgoing && !TD.isActive(call) && !TD.isFinished(call)) {
      minimizeOrClose();
    }
  }

  @Override
  public void onSetStateChanged (SliderView view, boolean isSetting) {
    if (isSetting) {
      mediaView.pauseIfPlaying();
    } else {
      mediaView.resumeIfNeeded(view.getValue());
    }
  }

  @Override
  public void onValueChanged (SliderView view, float factor) {
    // TODO? mediaView.setSeekProgress(factor);
  }

  @Override
  public boolean allowSliderChanges (SliderView view) {
    return stack.getCurrent().isVideo() && (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE || stack.getCurrent().isLoaded());
  }

  private int getSelectedMediaCount () {
    return selectDelegate != null ? selectDelegate.getSelectedMediaCount() : 0;
  }

  private boolean isCurrentItemSelected () {
    return selectDelegate != null && selectDelegate.isMediaItemSelected(stack.getCurrentIndex(), stack.getCurrent());
  }

  public TdApi.File getCurrentFile () {
    return stack.getCurrent().getTargetFile();
  }

  // Clicks

  private boolean showOtherMedias;

  private FactorAnimator otherAnimator;
  private float otherFactor;
  private static final int ANIMATOR_OTHER = 2;
  private void animateOtherFactor (float toFactor) {
    if (otherAnimator == null) {
      otherAnimator = new FactorAnimator(ANIMATOR_OTHER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, otherFactor);
    }
    otherAnimator.animateTo(toFactor);
  }

  private MediaOtherRecyclerView othersView;
  private MediaOtherAdapter otherAdapter;

  private void setOtherFactor (float factor) {
    if (this.otherFactor != factor) {
      this.otherFactor = factor;
      final float alpha = Math.max(0f, Math.min(1f, factor));
      overlayView.setAlpha(alpha);
      othersView.setAlpha(alpha);
      setCheckAlpha(1f - alpha);
      setCounterAlpha((1f - alpha) * Math.max(0f, Math.min(1f, counterFactor)));
    }
  }

  private void setCounterAlpha (float alpha) {
    counterView.setAlpha(alpha);
    counterView.setEnabled(alpha == 1f);
  }

  private void setCheckAlpha (float alpha) {
    checkView.setAlpha(alpha);
    checkView.setEnabled(alpha == 1f);
    if (receiverView != null) {
      receiverView.setAlpha(alpha);
    }
  }

  public boolean isFullyShown () {
    return commonFactor == 1f;
  }

  private void setSelectedImages (@Nullable ArrayList<ImageFile> images) {
    if (othersView == null) {
      otherAdapter = new MediaOtherAdapter(context(), this);
      otherAdapter.setImages(images);

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112f) + HeaderView.getTopOffset(), Gravity.TOP);

      othersView = new MediaOtherRecyclerView(context());
      othersView.setPadding(Screen.dp(2f), HeaderView.getTopOffset(), Screen.dp(2f), 0);
      othersView.setLayoutManager(new LinearLayoutManager(context(), LinearLayoutManager.HORIZONTAL, true));
      othersView.setHasFixedSize(true);
      othersView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
      othersView.setClipToPadding(false);
      othersView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
      othersView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      othersView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
          int position = parent.getChildAdapterPosition(view);
          outRect.set(position == otherAdapter.getItemCount() - 1 ? Screen.dp(4f) : 0, Screen.dp(6f), Screen.dp(4f), Screen.dp(6f));
        }
      });
      othersView.setAlpha(0f);
      othersView.setAdapter(otherAdapter);
      othersView.setLayoutParams(params);
      contentView.addView(othersView);
    } else {
      otherAdapter.setImages(images);
    }
  }

  private void setShowOtherMedias (boolean showOtherMedias) {
    if (this.showOtherMedias != showOtherMedias) {
      this.showOtherMedias = showOtherMedias;
      if (showOtherMedias && selectDelegate != null) {
        ArrayList<ImageFile> selectedFiles = selectDelegate.getSelectedMediaItems(true);
        setSelectedImages(selectedFiles);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
          return;
        }
      }
      animateOtherFactor(showOtherMedias ? 1f : 0f);
    }
  }

  private void clearFilters (MediaItem item, int index) {
    if (item != null && item.getFiltersState() != null && !item.getFiltersState().isEmpty()) {
      FiltersState state = item.getFiltersState();
      item.setFiltersState(null);
      tdlib.filegen().removeFilteredBitmap(ImageFilteredFile.getPath(state));
    }
  }

  private void unselectImage (ImageFile imageFile) {
    int i = stack.indexOfImageFile(imageFile);
    if (i != -1) {
      if (i == stack.getCurrentIndex()) {
        checkView.setChecked(false);
      }
      if (selectDelegate != null) {
        MediaItem item = stack.get(i);
        clearFilters(item, i);
        selectDelegate.setMediaItemSelected(i, item, false);
        int selectedCount = selectDelegate.getSelectedMediaCount();

        if (selectedCount == 0) {
          forceCounterFactor(0f);
        } else {
          counterView.setCounter(selectedCount);
        }
      }

      otherAdapter.removeImage(imageFile);
      if (otherAdapter.getItemCount() == 0) {
        setShowOtherMedias(false);
      }
    }
  }

  // Section change

  public static final int SECTION_CAPTION = 0; // default
  public static final int SECTION_FILTERS = 1;
  public static final int SECTION_CROP = 2;
  public static final int SECTION_PAINT = 3;
  public static final int SECTION_QUALITY = 4;

  private static final float CROP_PADDING_HORIZONTAL = 22f;

  private int currentSection, fromSection;

  private int getHorizontalOffsets (int section) {
    int futureOffset = 0;

    switch (section) {
      case SECTION_CROP: {
        futureOffset = Screen.dp(CROP_PADDING_HORIZONTAL);
        break;
      }
    }

    ImageGalleryFile file = stack.getCurrent().getSourceGalleryFile();
    int origWidth = file.getWidthCropRotated();
    int origHeight = file.getHeightCropRotated();

    int actualWidth = mediaView.getActualImageWidth();
    int actualHeight = mediaView.getActualImageHeight();

    float scale = Math.min((float) actualWidth / (float) origWidth, (float) actualHeight / (float) origHeight);
    int nowWidth = (int) ((float) origWidth * scale);
    // int nowHeight = (int) ((float) origHeight * scale);

    int futureActualWidth = mediaView.getMeasuredWidth() - futureOffset - futureOffset;
    int futureActualHeight = mediaView.getMeasuredHeight() - getSectionBottomOffset(section) - getSectionTopOffset(section);

    scale = Math.min((float) futureActualWidth / (float) origWidth, (float) futureActualHeight / (float) origHeight);
    int futureWidth = (int) ((float) origWidth * scale);
    // int futureHeight = (int) ((float) origHeight * scale);

    return futureOffset + Math.max(0, ((nowWidth - futureWidth) / 2) - futureOffset);
  }

  private int getSectionBottomOffset (int section) {
    if (mode != MODE_GALLERY) {
      return 0;
    }
    int add = isFromCamera ? this.bottomInnerMargin : 0;
    switch (section) {
      case SECTION_CAPTION: {
        return 0; // Screen.dp(56f);
      }
      case SECTION_QUALITY: {
        return getSectionHeight(section) + Screen.dp(12f) + add;
      }
      case SECTION_FILTERS: {
        return Screen.dp(220f) + add;
      }
      case SECTION_PAINT: {
        return Screen.dp(136f) + add;
      }
      case SECTION_CROP: {
        return Screen.dp(160f) + add;
      }
    }
    return 0;
  }

  private static int getSectionTopOffset (int section) {
    switch (section) {
      case SECTION_CROP: {
        return Screen.getStatusBarHeight() * 2;
      }
    }
    return 0;
  }

  public static int getSectionHeight (int section) {
    switch (section) {
      /*case SECTION_CAPTION: {
        return captionView.getMeasuredHeight();
      }*/
      case SECTION_FILTERS: {
        return Screen.dp(164f);
      }
      case SECTION_CROP: {
        return Screen.dp(56f + CROP_PADDING_TOP);
      }
      case SECTION_PAINT: {
        return Screen.dp(64f); // TODO check
      }
      case SECTION_QUALITY: {
        return Screen.dp(72f) + Screen.dp(24f);
      }
    }
    return 0;
  }

  private int fromTopOffset, toTopOffset, fromBottomOffset, toBottomOffset, fromHorOffset, toHorOffset;
  private FactorAnimator sectionChangeAnimator;
  private static final int ANIMATOR_SECTION = 4;

  private EGLEditorView editorView;

  private ImageGalleryFile currentSourceFile;
  private Bitmap sourceBitmap;

  private void clearSourceBitmapReference () {
    if (currentSourceFile != null) {
      if (sourceBitmap != null) {
        ImageCache.instance().removeReference(currentTargetImageFile, sourceBitmap);
      }
      sourceBitmap = null;
      currentSourceFile = null;
    }
  }

  private void setSourceBitmap (ImageFile targetFile, ImageGalleryFile file, Bitmap bitmap) {
    clearSourceBitmapReference();
    this.currentSourceFile = file;
    this.currentTargetImageFile = targetFile;
    this.sourceBitmap = bitmap;
    if (!TEST_LOAD_ORIGINAL && file != null && bitmap != null) {
      ImageCache.instance().addReference(targetFile, bitmap);
    }
  }

  private ImageFile currentTargetImageFile;
  private FiltersState currentFiltersState;
  private ImageReceiver currentTargetReceiver;
  private int currentBitmapWidth, currentBitmapHeight;

  private Bitmap prepareBitmapForEditing () {
    if (!(mediaView.getBaseReceiver() instanceof ImageReceiver)) {
      return null;
    }

    currentTargetReceiver = (ImageReceiver) mediaView.getBaseReceiver();
    Bitmap bitmap = currentTargetReceiver.getCurrentBitmap();
    if (bitmap == null || bitmap.isRecycled()) {
      return null;
    }

    currentBitmapWidth = bitmap.getWidth();
    currentBitmapHeight = bitmap.getHeight();

    return bitmap;
  }

  private boolean prepareFilters () {
    Bitmap bitmap = prepareBitmapForEditing();
    if (bitmap == null) {
      return false;
    }

    currentFiltersState = getFilterState(true);
    oldFiltersState = new FiltersState(currentFiltersState);

    ImageGalleryFile galleryFile = stack.getCurrent().getSourceGalleryFile();

    if (currentSourceFile != galleryFile) {
      cancelSourceLoad();

      ImageFile targetFile = stack.getCurrent().getTargetImageFile(false);
      setSourceBitmap(targetFile, galleryFile, !TEST_LOAD_ORIGINAL && currentTargetReceiver.getCurrentFile() == targetFile ? bitmap : null);
      // image will load when filters will show completely in applyFiltersView() if sourceBitmap is not available now
    }

    return true;
  }

  private WatcherReference reference;

  private void cancelSourceLoad () {
    ImageLoader.instance().removeWatcher(reference);
  }

  private void loadSourceAsync (ImageFile sourceFile) {
    if (TEST_LOAD_ORIGINAL) {
      ImageReader.instance().post(() -> {
        String path = sourceFile.getFilePath();
        // BitmapFactory.Options opts = new BitmapFactory.Options();
        // opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path);// ImageReader.decodeFile(path, opts);
        setEditBitmap(sourceFile, bitmap);
      });
    } else {
      if (reference == null) {
        reference = new WatcherReference(this);
      }
      ImageLoader.instance().requestFile(sourceFile, reference);
    }
  }

  private void applyFilteredBitmap (ImageFile targetFile, Bitmap bitmap) {
    currentTargetReceiver.setBundle(targetFile, bitmap, true);
  }

  private void setEditBitmap (ImageFile file, Bitmap bitmap) {
    UI.post(() -> {
      if (currentTargetImageFile == file) {
        setSourceBitmap(currentTargetImageFile, currentSourceFile, (Bitmap) bitmap);
        editorView.reset(currentSourceFile, sourceBitmap.getWidth(), sourceBitmap.getHeight(), sourceBitmap, currentFiltersState, currentSourceFile.getPaintState());
        editorView.setEditorVisible(true);
      }
    });
  }

  @Override
  public void imageLoaded (final ImageFile file, boolean successful, final Bitmap bitmap) {
    if (successful) {
      setEditBitmap(file, bitmap);
    }
  }

  @Override
  public void imageProgress (ImageFile file, float progress) { }

  private boolean initSection (int section) {
    switch (section) {
      case SECTION_FILTERS: {
        return isCurrentReady() && prepareFilters();
      }
      case SECTION_CROP: {
        return isCurrentReady() && prepareCrop();
      }
      case SECTION_PAINT: {
        return isCurrentReady() && preparePaint();
      }
      case SECTION_QUALITY: {
        return isCurrentReady() && prepareQuality();
      }
    }
    return true;
  }

  private int indexAfterMediaView () {
    return contentView.indexOfChild(mediaView) + 1;
  }

  private static final boolean TEST_LOAD_ORIGINAL = false; // BuildConfig.DEBUG;

  private void showEditorView () {
    if (editorView == null) {
      editorView = new EGLEditorView(context());
      editorView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    if (sourceBitmap != null) {
      editorView.reset(currentSourceFile, sourceBitmap.getWidth(), sourceBitmap.getHeight(), sourceBitmap, currentFiltersState, currentSourceFile.getPaintState());
    } else {
      editorView.setSizes(currentBitmapWidth, currentBitmapHeight, currentSourceFile.getVisualRotationWithCropRotation(), currentSourceFile.getCropState());
    }

    mediaView.getBaseCell().addView(editorView);
    if (sourceBitmap != null) {
      editorView.setEditorVisible(true);
    } else {
      loadSourceAsync(currentTargetImageFile);
    }
  }

  private void hideEditorView () {
    cancelSourceLoad();
    editorView.setEditorVisible(false);
    editorView.pause();
    editorView.setScaleX(1f);
    editorView.setScaleY(1f);
  }

  private void hideQualityView () {

  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return pipFactor != 0f;
  }

  public void onMediaItemClick (MediaView mediaView, float x, float y) {
    if (inPictureInPicture) {
      setInPictureInPicture(false);
      ViewUtils.onClick(contentView);
      return;
    }
    if (mode == MODE_GALLERY && currentSection != SECTION_CAPTION) {
      return;
    }
    boolean clicked = stack.getCurrent().onClick(mediaView, x, y) || toggleHeaderVisibility();
    if (!clicked && mode == MODE_GALLERY) {
      clicked = toggleCheck();
    }
    if (clicked) {
      ViewUtils.onClick(mediaView);
    }
  }

  private FiltersState oldFiltersState;
  private boolean hasEditedFilters;

  private void resetEditorState () {
    oldFiltersState = null;
  }

  private boolean hasEditorChanges () {
    return hasEditedFilters && !oldFiltersState.compare(currentFiltersState);
  }

  @Override
  public boolean canApplyChanges () {
    return !isUIBlocked;
  }

  @Override
  public void onRequestRender (int changedKey) {
    hasEditedFilters = true;
    if (editorView != null) {
      editorView.requestRenderByChange(changedKey == FiltersState.KEY_BLUR_TYPE);
    }
  }

  private static final float CROP_PADDING_TOP = 16f;

  private void prepareSectionToShow (int section) {
    switch (section) {
      case SECTION_FILTERS: {
        if (filtersView == null) {
          LinearLayoutManager manager = new LinearLayoutManager(context(), RecyclerView.VERTICAL, false);
          filtersAdapter = new MediaFiltersAdapter(context(), manager);
          filtersAdapter.setFilterState(currentFiltersState);
          filtersAdapter.setCallback(this);

          filtersView = new MediaFiltersRecyclerView(context());
          filtersView.setItemAnimator(null);
          filtersView.setOverScrollMode(View.OVER_SCROLL_NEVER);
          filtersView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
              int position = parent.getChildAdapterPosition(view);
              outRect.set(0, position == 0 ? Screen.dp(16f) : 0, 0, 0); // position == filtersAdapter.getItemCount() - 1 ? Screen.dp(16f) : 0);
            }
          });
          filtersView.setLayoutManager(manager);
          filtersView.setAdapter(filtersAdapter);
          filtersView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
          filtersView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getSectionHeight(SECTION_FILTERS), Gravity.BOTTOM));
          filtersView.setTranslationY(getSectionHeight(SECTION_FILTERS));
          filtersView.setAlpha(0f);
        } else {
          ((LinearLayoutManager) filtersView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
          filtersAdapter.setFilterState(currentFiltersState);
        }
        bottomWrap.addView(filtersView);

        break;
      }
      case SECTION_QUALITY: {
        if (qualityControlWrap == null) {
          int totalHeight = getSectionHeight(SECTION_QUALITY);
          int sliderHeight = Screen.dp(56f);
          int hintHeight = Screen.dp(16f);
          int infoHeight = Screen.dp(18f);

          qualityControlWrap = new FrameLayoutFix(context());
          qualityControlWrap.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
          qualityControlWrap.setAlpha(0f);
          qualityControlWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, totalHeight, Gravity.BOTTOM));

          qualitySlider = new SliderView(context());
          qualitySlider.setListener(new SliderView.Listener() {
            int targetIndex = -1;

            @Override
            public void onSetStateChanged (SliderView view, boolean isSetting) {
              if (!isSetting) {
                float value = (float) targetIndex * (1f / (float) (videoLimits.size() - 1));
                qualitySlider.animateValue(value);
              }
            }

            @Override
            public void onValueChanged (SliderView view, float factor) {
              int newIndex = Math.round(factor * (videoLimits.size() - 1));
              if (targetIndex != newIndex) {
                targetIndex = newIndex;
                currentVideoLimit = videoLimits.get(targetIndex);
                if (targetIndex == videoLimits.size() - 1 && (prevVideoLimit.size.isUnlimited() || (!currentVideoLimit.size.isUnlimited() && currentVideoLimit.size.majorSize < prevVideoLimit.size.majorSize))) {
                  currentVideoLimit = new Settings.VideoLimit(prevVideoLimit);
                }
                updateQualityInfo();
              }
            }

            @Override
            public boolean allowSliderChanges (SliderView view) {
              return true;
            }
          });
          qualitySlider.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeight, Gravity.BOTTOM, 0, 0, 0, infoHeight));
          qualitySlider.setAnchorMode(SliderView.ANCHOR_MODE_START);
          qualitySlider.setAddPaddingLeft(Screen.dp(18f));
          qualitySlider.setAddPaddingRight(Screen.dp(18f));
          qualitySlider.setColorId(ColorId.white, false);
          qualityControlWrap.addView(qualitySlider);

          TextView textView = Views.newTextView(context(), 14f, Theme.getColor(ColorId.white), Gravity.LEFT, Views.TEXT_FLAG_SINGLE_LINE);
          textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, Screen.dp(15f), Screen.dp(10f), Screen.dp(15f), 0));
          textView.setText(R.string.QualityWorse);
          qualityControlWrap.addView(textView);
          textView = Views.newTextView(context(), 14f, Theme.getColor(ColorId.white), Gravity.RIGHT, Views.TEXT_FLAG_SINGLE_LINE);
          textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, Screen.dp(15f), Screen.dp(10f), Screen.dp(15f), 0));
          textView.setText(R.string.QualityBetter);
          qualityControlWrap.addView(textView);

          qualityInfo = Views.newTextView(context(), 15f, Theme.getColor(ColorId.white), Gravity.CENTER, Views.TEXT_FLAG_SINGLE_LINE);
          qualityInfo.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, Screen.dp(8f)));
          qualityControlWrap.addView(qualityInfo);
        }
        bottomWrap.addView(qualityControlWrap);
        updateQualitySlider();
        break;
      }
      case SECTION_CROP: {
        if (cropControlsWrap == null) {
          FrameLayoutFix.LayoutParams params;

          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getSectionHeight(SECTION_CROP), Gravity.BOTTOM);

          cropControlsWrap = new FrameLayoutFix(context());
          cropControlsWrap.setPadding(0, Screen.dp(CROP_PADDING_TOP), 0, 0);
          cropControlsWrap.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
          cropControlsWrap.setLayoutParams(params);
          cropControlsWrap.setAlpha(0f);

          rotateButton = new EditButton(context());
          rotateButton.setId(R.id.btn_rotate);
          rotateButton.setOnClickListener(this);
          rotateButton.setIcon(R.drawable.baseline_rotate_90_degrees_ccw_24, false, false);
          rotateButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
          cropControlsWrap.addView(rotateButton);

          proportionButton = new EditButton(context());
          proportionButton.setId(R.id.btn_proportion);
          proportionButton.setOnClickListener(this);
          proportionButton.setIcon(R.drawable.baseline_image_aspect_ratio_24, false, false);
          proportionButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
          cropControlsWrap.addView(proportionButton);

          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
          params.leftMargin = Screen.dp(56f);
          params.rightMargin = Screen.dp(56f);

          rotationControlView = new RotationControlView(context());
          rotationControlView.setCallback(this);
          rotationControlView.setLayoutParams(params);
          cropControlsWrap.addView(rotationControlView);

          prepareCropLayout();
        }

        cropLayout.setAlpha(0f);
        prepareCropState();

        bottomWrap.addView(cropControlsWrap);
        contentView.addView(cropLayout, indexAfterMediaView());


        break;
      }
      case SECTION_PAINT: {
        if (paintControlsWrap == null) {
          FrameLayoutFix.LayoutParams params;

          // FIXME use less height
          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);

          paintControlsWrap = new FrameLayoutFix(context());
          paintControlsWrap.setAlpha(0f);
          paintControlsWrap.setLayoutParams(params);

          int margin = Screen.dp(56f);
          int padding = Screen.dp(18f);

          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(12f) + padding * 2, Gravity.BOTTOM);
          View backgroundView = new View(context());
          backgroundView.setBackgroundColor(Theme.getColor(ColorId.transparentEditor));
          backgroundView.setLayoutParams(params);
          paintControlsWrap.addView(backgroundView);

          params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(48f), Gravity.LEFT | Gravity.BOTTOM);
          undoButton = new ImageView(context());
          undoButton.setId(R.id.paint_undo);
          undoButton.setOnClickListener(this);
          undoButton.setOnLongClickListener(v -> {
            if (paintView != null && !paintView.getContentWrap().isBusy()) {
              showOptions(null, new int[]{R.id.paint_clear, R.id.btn_cancel}, new String[]{Lang.getString(R.string.ClearDrawing), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                if (id == R.id.paint_clear) {
                  undoAllPaintActions();
                }
                return true;
              }, getForcedTheme());
              return true;
            }
            return false;
          });
          undoButton.setScaleType(ImageView.ScaleType.CENTER);
          undoButton.setImageResource(R.drawable.baseline_undo_24);
          undoButton.setColorFilter(0xffffffff);
          Views.setClickable(undoButton);
          undoButton.setBackgroundResource(R.drawable.bg_btn_header_light);
          undoButton.setLayoutParams(params);
          paintControlsWrap.addView(undoButton);

          params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(48f), Gravity.RIGHT | Gravity.BOTTOM);
          paintTypeButton = new EditButton(context());
          paintTypeButton.setId(R.id.btn_paintType);
          paintTypeButton.setUseFastAnimations();
          paintTypeButton.setOnClickListener(this);
          paintTypeButton.setIcon(getIconForPaintType(PaintMode.PATH), false, false);
          paintTypeButton.setLayoutParams(params);
          paintControlsWrap.addView(paintTypeButton);

          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(12f) + padding * 2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
          params.leftMargin = params.rightMargin = margin - padding;
          colorPickerView = new ColorPickerView(context());
          colorPickerView.setToneEventListener(new ColorPickerView.ToneEventListener() {
            @Override
            public void onLongTapStateChanged (ColorPickerView v, boolean inLongTap) {
              showDefaultBrushHint();
            }

            @Override
            public void onTonePicking (ColorPickerView v, boolean pickingTone) {
              if (pickingTone) {
                Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_BRUSH_COLOR_TONE);
              }
            }
          });
          colorPickerView.setPadding(padding, padding, padding, padding);
          colorPickerView.setLayoutParams(params);
          paintControlsWrap.addView(colorPickerView);

          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(220f), Gravity.BOTTOM);
          params.bottomMargin = Screen.dp(12f) + padding * 2;

          colorToneView = new ColorToneView(context());
          colorToneView.setAlpha(0f);
          colorToneView.setLayoutParams(params);
          paintControlsWrap.addView(colorToneView);

          colorToneShadow = new ShadowView(context());
          colorToneShadow.setSimpleTopShadow(true);
          colorToneShadow.setAlpha(0f);
          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, colorToneShadow.getLayoutParams().height, Gravity.BOTTOM);
          params.bottomMargin = Screen.dp(220f) + Screen.dp(12f) + padding * 2;
          colorToneShadow.setLayoutParams(params);
          paintControlsWrap.addView(colorToneShadow);

          int addY = Screen.dp(78f);
          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(220f) + padding + addY, Gravity.BOTTOM);
          params.bottomMargin = Screen.dp(12f) + padding;

          ColorDirectionView colorDirectionView = new ColorDirectionView(context());
          colorDirectionView.setPadding(0, addY, 0, 0);
          colorDirectionView.setLayoutParams(params);
          paintControlsWrap.addView(colorDirectionView);

          params = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.LEFT | Gravity.BOTTOM);
          params.leftMargin = margin - Screen.dp(48f) / 2;
          params.bottomMargin = (Screen.dp(12f) + padding * 2) / 2 - Screen.dp(48f) / 2;
          colorPreviewView = new ColorPreviewView(context());
          colorPreviewView.setColorChangeListener((v, color) -> {
            showDefaultBrushHint();
          });
          colorPreviewView.setPositionChangeListener(() -> {
            if (this.brushToneHint != null) {
              this.brushToneHint.reposition();
            }
          });
          colorPreviewView.setBrushChangeListener(this);
          colorPreviewView.setTone(colorToneView);
          colorPreviewView.setDirection(colorDirectionView);
          colorPreviewView.setLayoutParams(params);
          paintControlsWrap.addView(colorPreviewView);

          colorToneView.setPreview(colorPreviewView);
          colorPickerView.setPreview(colorPreviewView);
          colorPickerView.setTone(colorToneView, colorToneShadow);
          colorPickerView.setDirection(colorDirectionView);
        }

        setPaintType(PaintMode.restore(), false);
        checkCanUndo();
        colorPreviewView.reset(true);

        bottomWrap.addView(paintControlsWrap);

        break;
      }
      case SECTION_CAPTION: {
        // bottomWrap.addView(captionView);
        break;
      }
    }
  }

  private FiltersState getFilterState (boolean createIfEmpty) {
    MediaItem item = stack.getCurrent();
    FiltersState state = item.getFiltersState();
    if (state == null && createIfEmpty) {
      state = new FiltersState();
    }
    return state;
  }

  // Crop

  private boolean inCrop;

  private CropLayout cropLayout;
  private CropAreaView cropAreaView;
  private CropTargetView cropTargetView;

  private static final int ANIMATOR_CROP = 18;

  private BoolAnimator cropAnimator = new BoolAnimator(ANIMATOR_CROP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);

  private int sectionAfterCrop = -1;
  private boolean sectionAfterCropReady;

  private void onCloseCrop () {
    if (sectionAfterCrop != -1) {
      if (resettingCrop) {
        onCropResetComplete();
      }
      sectionAfterCropReady = true;
      changeSectionImpl(sectionAfterCrop);
      sectionAfterCrop = -1;
    }
  }

  private boolean closeCropAsync (int futureSection) {
    if (sectionAfterCropReady) {
      sectionAfterCropReady = false;
      return false;
    }
    if (sectionAfterCrop != -1) {
      return true;
    }
    sectionAfterCrop = futureSection;
    if (!resettingCrop) {
      setInCrop(false);
    }
    return true;
  }

  private static final long CROP_IN_DURATION = 198;
  private static final long CROP_OUT_DURATION = 160l;

  private void setInCrop (boolean inCrop) {
    if (this.inCrop != inCrop) {
      this.inCrop = inCrop;
      if (!inCrop) {
        prepareSectionToHide(SECTION_CROP);
        mediaView.setVisibility(View.VISIBLE);
      }
      cropAnimator.setDuration(inCrop ? (currentCropState.isEmpty() ? CROP_OUT_DURATION : CROP_IN_DURATION) : 120l);
      cropAnimator.setValue(inCrop, true);
    }
  }

  private void setCropFactor (float factor) {
    cropLayout.setAlpha(factor);
  }

  private void applyCropMode (boolean inCrop) {
    if (inCrop) {
      mediaView.setVisibility(View.INVISIBLE);
    } else {
      onCloseCrop();
    }
  }

  @Override
  public boolean allowPreciseRotation () {
    return (sectionChangeAnimator == null || !sectionChangeAnimator.isAnimating()) && inCrop && (cropAreaView.canRotate());
  }

  private static final int ANIMATOR_IMAGE_ROTATE = 19;
  private float imageRotateFactor;
  private FactorAnimator imageRotateAnimator;

  private void setImageRotateFactor (float factor) {
    if (this.imageRotateFactor != factor) {
      this.imageRotateFactor = factor;
      float rotation = (float) rotatingByDegrees * factor;

      float scaleX, scaleY;

      if (U.isRotated(rotatingByDegrees)) {
        int fromWidth = cropTargetView.getMeasuredWidth();
        int fromHeight = cropTargetView.getMeasuredHeight();

        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) cropTargetView.getLayoutParams();

        int targetWidth = cropTargetView.getTargetHeight();
        int targetHeight = cropTargetView.getTargetWidth();

        int availWidth = cropLayout.getMeasuredWidth() - params.leftMargin - params.rightMargin;
        int availHeight = cropLayout.getMeasuredHeight() - params.topMargin - params.bottomMargin;

        float ratio = Math.min((float) availWidth / (float) targetWidth, (float) availHeight / (float) targetHeight);

        int toWidth = (int) ((float) targetWidth * ratio);
        int toHeight = (int) ((float) targetHeight * ratio);

        int width = fromWidth + (int) ((float) (toHeight - fromWidth) * factor);
        int height = fromHeight + (int) ((float) (toWidth - fromHeight) * factor);

        scaleX = (float) width / (float) fromWidth;
        scaleY = (float) height / (float) fromHeight;
      } else {
        scaleX = 1f;
        scaleY = 1f;
      }

      cropTargetView.setBaseRotation(rotation);
      cropTargetView.setBaseScale(scaleX, scaleY);

      cropAreaView.setRotation(rotation);
      cropAreaView.setScaleX(scaleX);
      cropAreaView.setScaleY(scaleY);
    }
  }

  private int rotatingByDegrees;

  private void applyImageRotation () {
    if (rotatingByDegrees == 0) {
      return;
    }

    cropTargetView.setBaseRotation(0);
    cropTargetView.setBaseScale(1f, 1f);
    cropTargetView.rotateTargetBy(rotatingByDegrees);

    cropAreaView.setRotation(0);
    cropAreaView.setScaleX(1f);
    cropAreaView.setScaleY(1f);
    cropAreaView.rotateValues(rotatingByDegrees);

    resetMediaPaddings(SECTION_CROP);

    rotatingByDegrees = 0;
  }

  private void cancelImageRotation () {
    if (imageRotateAnimator != null) {
      imageRotateAnimator.cancel();
    }
    imageRotateFactor = 0f;
  }

  private void cropRotateByDegrees (int degrees) {
    if (imageRotateAnimator == null) {
      imageRotateAnimator = new FactorAnimator(ANIMATOR_IMAGE_ROTATE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else if (!imageRotateAnimator.isAnimating()) {
      imageRotateAnimator.forceFactor(0f);
      imageRotateFactor = 0f;
    } else {
      return;
    }
    if (degrees != 0) {
      int newPostRotate = currentCropState.rotateBy(degrees);
      this.rotatingByDegrees = degrees;
      imageRotateAnimator.animateTo(1f);
    }
  }

  @Override
  public void onPreciseActiveStateChanged (boolean isActive) {
    cropAreaView.setMode(isActive ? CropAreaView.MODE_PRECISE : CropAreaView.MODE_NONE, false);
  }

  @Override
  public void onPreciseActiveFactorChanged (float activeFactor) {
    cropAreaView.forceActiveFactor(activeFactor);
  }

  @Override
  public void onPreciseRotationChanged (float newValue) {
    currentCropState.setDegreesAroundCenter(newValue);
    cropTargetView.setDegreesAroundCenter(newValue);
  }

  private CropState currentCropState;
  private CropState oldCropState;

  private Bitmap cropBitmap;
  private int cropRotation;

  private CropState obtainCropState (boolean createIfEmpty) {
    MediaItem item = stack.getCurrent();
    CropState cropState = item.getCropState();
    if (cropState == null && createIfEmpty) {
      cropState = new CropState();
    }
    return cropState;
  }

  private void prepareCropLayout () {
    if (cropLayout == null) {
      cropLayout = new CropLayout(context());
      cropLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
      params.topMargin = getSectionTopOffset(SECTION_CROP);
      params.bottomMargin = getSectionBottomOffset(SECTION_CROP);
      params.leftMargin = params.rightMargin = Screen.dp(CROP_PADDING_HORIZONTAL);

      cropTargetView = new CropTargetView(context());
      cropTargetView.setLayoutParams(params);
      cropLayout.addView(cropTargetView);

      cropAreaView = new CropAreaView(context());
      cropAreaView.setRectChangeListener((left, top, right, bottom) -> {
        if (inCrop) {
          currentCropState.setRect(left, top, right, bottom);
        }
      });
      cropAreaView.setNormalizeListener(new CropAreaView.NormalizeListener() {
        @Override
        public void onCropNormalization (float factor) {
          setCropResetFactor(factor);
        }

        @Override
        public void onCropNormalizationComplete () {
          onCropResetComplete();
        }
      });
      cropAreaView.setRotateModeChangeListener(rotateInternally -> cropTargetView.setRotateInternally(rotateInternally));
      cropAreaView.setOffsets(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin);
      cropAreaView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      cropLayout.addView(cropAreaView);
    }
  }

  private void prepareCropState () {
    proportionButton.setActive(false, false);
    int cropRotation = MathUtils.modulo(this.cropRotation + (oldCropState != null ? oldCropState.getRotateBy() : 0), 360);
    cropTargetView.resetState(cropBitmap, cropRotation, currentCropState.getDegreesAroundCenter(), currentPaintState);
    rotationControlView.reset(currentCropState.getDegreesAroundCenter(), false);
    cropAreaView.resetProportion();
    cropAreaView.resetState(U.getWidth(cropBitmap, cropRotation), U.getHeight(cropBitmap, cropRotation), currentCropState.getLeft(), currentCropState.getTop(), currentCropState.getRight(), currentCropState.getBottom(), false);
  }

  private boolean prepareCrop () {
    if (!Config.CROP_ENABLED) {
      return false;
    }
    MediaItem item = stack.getCurrent();
    ImageReceiver receiver = mediaView.getBaseCell().getImageReceiver();
    if (item.isVideo() || item.isGif() || receiver == null) {
      UI.showToast(R.string.MediaTypeUnsupported, Toast.LENGTH_SHORT);
      return false;
    }

    cropBitmap = receiver.getCurrentBitmap();
    if (cropBitmap == null || cropBitmap.isRecycled()) {
      return false;
    }
    cropRotation = item.getSourceGalleryFile().getRotation();
    currentCropState = obtainCropState(true);
    currentPaintState = obtainPaintState(false);
    oldCropState = new CropState(currentCropState);
    return true;
  }

  private boolean hasCropChanges () {
    return !oldCropState.compare(currentCropState);
  }

  private void resetCropState () {
    oldCropState = null;
  }

  private void setCropProportion (int big, int small) {
    cropAreaView.setFixedProportion(big, small);
    proportionButton.setActive(big != 0 && small != 0, true);
  }

  private float cropStartDegrees, cropEndDegrees;
  private boolean resetCropDegrees;

  private boolean resettingCrop;
  private boolean closeCropAfterReset;

  private void resetCrop (boolean zero) {
    if (resettingCrop) { // Awaiting previous animation to complete
      return;
    }

    cancelImageRotation();

    cropStartDegrees = currentCropState.getDegreesAroundCenter();
    cropEndDegrees = zero || oldCropState == null ? 0 : oldCropState.getDegreesAroundCenter();
    resetCropDegrees = (cropEndDegrees - cropStartDegrees) != 0f;

    rotatingByDegrees = (zero || oldCropState == null ? 0 : oldCropState.getRotateBy()) - currentCropState.getRotateBy();
    if (rotatingByDegrees < -180) {
      rotatingByDegrees = 360 + rotatingByDegrees;
    }
    currentCropState.rotateBy(rotatingByDegrees);
    proportionButton.setActive(false, true);
    resettingCrop = resetCropDegrees || rotatingByDegrees != 0;
    closeCropAfterReset = !zero;
    if (zero || oldCropState == null || oldCropState.isEmpty()) {
      if (cropAreaView.resetArea(resettingCrop, !zero)) {
        resettingCrop = true;
      }
    } else {
      if (cropAreaView.animateArea(oldCropState.getLeft(), oldCropState.getTop(), oldCropState.getRight(), oldCropState.getBottom(), true, true)) {
        resettingCrop = true;
      }
    }
    closeCropAfterReset = closeCropAfterReset && resettingCrop;
  }

  private void setCropResetFactor (float factor) {
    if (resetCropDegrees) {
      rotationControlView.reset(cropStartDegrees + (cropEndDegrees - cropStartDegrees) * factor, true);
    }
    if (rotatingByDegrees != 0) {
      setImageRotateFactor(factor);
    }
  }

  private void onCropResetComplete () {
    resetCropDegrees = false;
    resettingCrop = false;
    applyImageRotation();
    if (closeCropAfterReset) {
      closeCropAfterReset = false;
      setInCrop(false);
    }
  }

  // Video rotation

  private FactorAnimator rotateAnimator;
  private static final int ANIMATOR_ROTATION = 18;

  private void rotateBy90Degrees () {
    if (!mediaView.isBaseVisible() || !mediaView.isStill()) {
      return;
    }

    MediaItem item = stack.getCurrent();
    if (item == null) {
      return;
    }

    if (!item.isVideo()) {
      cropRotateByDegrees(-90);
      return;
    }

    if (!Config.USE_VIDEO_COMPRESSION) {
      UI.showApiLevelWarning(Build.VERSION_CODES.JELLY_BEAN_MR2);
      return;
    }

    if (rotateAnimator == null) {
      rotateAnimator = new FactorAnimator(ANIMATOR_ROTATION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else if (rotateAnimator.isAnimating()) {
      // Wait until previous video rotation will be applied
      return;
    }

    MediaCellView cellView = mediaView.getBaseCell();
    item.postRotateBy90Degrees();
    cellView.checkPostRotation(true);
    if (currentSection == SECTION_QUALITY) {
      updateQualityInfo();
    }
  }

  // Paint

  private boolean hasPaintChanges () {
    return !oldPaintState.compare(currentPaintState);
  }

  private MediaItem paintingItem;
  private int paintingWidth, paintingHeight;

  private boolean preparePaint () {
    MediaItem item = stack.getCurrent();
    ImageReceiver receiver = mediaView.getBaseCell().getImageReceiver();
    if (item.isVideo() || item.isGif() || receiver == null) {
      UI.showToast(R.string.MediaTypeUnsupported, Toast.LENGTH_SHORT);
      return false;
    }

    Bitmap bitmap = prepareBitmapForEditing();
    if (bitmap == null) {
      return false;
    }

    currentPaintState = obtainPaintState(true);
    currentPaintState.addUndoStateListener(this);
    oldPaintState = new PaintState(currentPaintState);

    currentCropState = obtainCropState(false);

    paintingItem = stack.getCurrent();
    paintingWidth = bitmap.getWidth();
    paintingHeight = bitmap.getHeight();

    return true;
  }

  private PaintState currentPaintState;
  private PaintState oldPaintState;

  private PaintState obtainPaintState (boolean createIfEmpty) {
    MediaItem item = stack.getCurrent();
    PaintState paintState = item.getPaintState();
    if (paintState == null && createIfEmpty) {
      paintState = new PaintState();
    }
    return paintState;
  }

  private float paintSectionFactor;

  private void setPaintSectionFactor (float factor) {
    if (Config.MASKS_TEXTS_AVAILABLE && this.paintSectionFactor != factor) {
      this.paintSectionFactor = factor;
      cropOrStickerButton.setSecondFactor(factor);
      adjustOrTextButton.setSecondFactor(factor);
      if (stopwatchButton != null) {
        float alpha = 1f - factor;
        stopwatchButton.setAlpha(alpha);
        float scale = .6f + .4f * alpha;
        stopwatchButton.setScaleX(scale);
        stopwatchButton.setScaleY(scale);
        editButtons.setTranslationX(Views.getParamsWidth(stopwatchButton) / 2 * factor);
      }
    }
  }

  private EGLEditorView paintView;

  private void showPaintEditor () {
    if (paintView == null) {
      paintView = new EGLEditorView(context());
      paintView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
      applyPaintingMode(paintType);
    }

    paintView.getContentWrap().setBrushParameters(colorPreviewView.getBrushColor(), colorPreviewView.getBrushRadius(getBrushScale()));

    paintView.reset(paintingItem.getSourceGalleryFile(), paintingWidth, paintingHeight, null, null, currentPaintState);
    boolean changed = paintingItem.setPaintState(null, false);

    mediaView.getBaseCell().addView(paintView);
    paintView.setEditorVisible(true);

    paintView.getContentWrap().setDrawingListener((v, mode) -> {
      showDefaultBrushHint();
    });

    if (changed) {
      mediaView.getBaseReceiver().invalidate();
    }

    showDefaultBrushHint();
  }

  public void showDefaultBrushHint () {
    if (paintView != null && paintView.isEditorVisible() && !paintView.getContentWrap().hasEffectiveDrawing()) {
      CharSequence text;
      int textFlags = 0;
      if (colorPickerView.isInLongTap()) {
        float hue = colorPreviewView.getHue();
        float[] hsv = colorPreviewView.getHsv();
        int color = colorPreviewView.getBrushColor();
        text =
          Strings.getColorRepresentation(Settings.COLOR_FORMAT_HEX, hue, hsv[1], hsv[2], color, true) + "\n" +
          Strings.getColorRepresentation(Settings.COLOR_FORMAT_RGB, hue, hsv[1], hsv[2], color, true) + "\n" +
          Strings.getColorRepresentation(Settings.COLOR_FORMAT_HSL, hue, hsv[1], hsv[2], color, true) + "\n" +
          Lang.getStringBold(R.string.BrushSize, U.formatFloat(colorPreviewView.getBrushRadius(1f), false));
        textFlags = Text.FLAG_ALIGN_CENTER;
      } else if (Settings.instance().needTutorial(Settings.TUTORIAL_BRUSH_COLOR_TONE)) {
        text = Lang.getString(R.string.HoldToTone);
      } else {
        text = null;
      }
      if (!StringUtils.isEmpty(text)) {
        if (brushToneHint != null) {
          if (!text.equals(brushToneHint.getContentText())) {
            brushToneHint.reset(context().tooltipManager().newContent(tdlib, text, textFlags), 0);
          }
          brushToneHint.show();
        } else {
          brushToneHint = context().tooltipManager().builder(colorPreviewView).preventHideOnTouch(true).color(context().tooltipManager().overrideColorProvider(getForcedTheme())).show(context().tooltipManager().newContent(tdlib, text, textFlags));
        }
        return;
      }
    }
    if (brushToneHint != null) {
      brushToneHint.hideNow();
    }
  }

  private TooltipOverlayView.TooltipInfo brushToneHint;

  private void hidePaintEditor () {
    paintView.setEditorVisible(false);
    if (paintingItem.setPaintState(paintingItem.getPaintState(), false)) {
      mediaView.getBaseReceiver().invalidate();
    }
    paintView.pause();
    paintView.setScaleX(1f);
    paintView.setScaleY(1f);
    showDefaultBrushHint();
  }

  private static int getIconForPaintType (int type) {
    switch (type) {
      case PaintMode.PATH:
        return R.drawable.baseline_adjust_24;
      case PaintMode.FREE_MOVEMENT:
        return R.drawable.baseline_zoom_out_map_24;
      case PaintMode.ARROW:
        return R.drawable.baseline_arrow_upward_24;
      case PaintMode.RECTANGLE:
        return R.drawable.baseline_crop_3_2_24;
    }
    return R.drawable.baseline_bubble_chart_24;
  }

  private int paintType;

  private void setPaintType (int mode, boolean changeByUser) {
    if (this.paintType != mode) {
      this.paintType = mode;
      if (changeByUser) {
        PaintMode.save(mode);
      }
      paintTypeButton.setIcon(getIconForPaintType(mode), changeByUser, false);
      applyPaintingMode(mode);
    }
  }

  private void showPaintTypes () {
    // TODO design

    boolean canErase = currentPaintState != null && !currentPaintState.isEmpty();

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    StringList strings = new StringList(5);

    ids.append(R.id.paint_mode_path);
    icons.append(R.drawable.baseline_adjust_24);
    strings.append(R.string.PaintModeDoodle);

    ids.append(R.id.paint_mode_arrow);
    icons.append(R.drawable.baseline_arrow_upward_24);
    strings.append(R.string.PaintModeArrow);

    ids.append(R.id.paint_mode_rect);
    icons.append(R.drawable.baseline_crop_3_2_24);
    strings.append(R.string.PaintModeRect);

    if (currentPaintState != null && currentPaintState.isEmpty()) {
      ids.append(R.id.paint_mode_fill);
      icons.append(R.drawable.baseline_format_color_fill_24);
      strings.append(R.string.PaintModeFill);
    }

    /*ids.append(R.id.paint_mode_zoom);
    icons.append(R.drawable.ic_zoom_out_map_black_24dp);
    strings.append(R.string.PaintModeZoomArea);*/

    /*if (canErase) {
      ids.append(R.id.paint_clear);
      icons.append(R.drawable.ic_delete_white);
      strings.append("Clear all");
    }*/

    showOptions(null, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      final int paintMode;
      if (id == R.id.paint_mode_path) {
        paintMode = PaintMode.PATH;
      } else if (id == R.id.paint_mode_arrow) {
        paintMode = PaintMode.ARROW;
      } else if (id == R.id.paint_mode_rect) {
        paintMode = PaintMode.RECTANGLE;
      } else if (id == R.id.paint_mode_zoom) {
        paintMode = PaintMode.FREE_MOVEMENT;
      } else if (id == R.id.paint_mode_fill) {
        int color = colorPickerView.getPreview().getBrushColor();
        SimpleDrawing drawing = new SimpleDrawing(SimpleDrawing.TYPE_FILLING);
        drawing.setBrushParameters(color, 0);
        currentPaintState.addSimpleDrawing(drawing);
        currentPaintState.trackSimpleDrawingAction(drawing);
        return true;
        /*case R.id.paint_clear: {
          undoAllPaintActions();
          return true;
        }*/
      } else {
        return true;
      }
      setPaintType(paintMode, true);
      return true;
    }, getForcedTheme());
  }

  private void checkCanUndo () {
    setCanUndo(currentPaintState != null && !currentPaintState.isEmpty());
  }

  private boolean canUndo = true;

  private void setCanUndo (boolean canUndo) {
    if (this.canUndo != canUndo) {
      this.canUndo = canUndo;
      undoButton.setAlpha(canUndo ? 1f : .4f);
      undoButton.setEnabled(canUndo);
    }
  }

  public boolean allowMediaViewGestures (boolean isTouchDown) {
    if (thumbsScrollAnimator == null || !thumbsScrollAnimator.getValue()) {
      return true;
    }
    if (isTouchDown) {
      thumbsRecyclerView.stopScroll();
      setThumbsScrolling(false);
      return false;
    }
    return false;
  }

  public boolean allowMovingZoomedView () {
    return mode != MODE_GALLERY || currentSection != SECTION_PAINT || paintType == PaintMode.FREE_MOVEMENT;
  }

  // Painting process

  private float getBrushScale () {
    return 1f; // currentCropState != null ? (float) Math.min(currentCropState.getRegionWidth(), currentCropState.getRegionHeight()) : 1f;
  }

  @Override
  public void onBrushChanged (ColorPreviewView v) {
    if (paintView != null) {
      paintView.getContentWrap().setBrushParameters(v.getBrushColor(), v.getBrushRadius(getBrushScale()));
    }
  }

  private boolean undoAllPaintActions () {
    if (paintView != null && !paintView.getContentWrap().isBusy()) {
      currentPaintState.undoAllActions();
      return true;
    }
    return false;
  }

  private void undoLastPaintAction () {
    if (paintView != null && !paintView.getContentWrap().isBusy()) {
      currentPaintState.undoLastAction();
    }
  }

  private void applyPaintingMode (int mode) {
    if (paintView != null) {
      paintView.setPaintingMode(mode);
    }
  }

  @Override
  public void onUndoAvailableStateChanged (PaintState state, boolean isAvailable, int totalActionsCount) {
    setCanUndo(isAvailable);
  }

  // etc

  private void hideSection (int section) {
    switch (section) {
      case SECTION_CAPTION: {
        // bottomWrap.removeView(captionView);
        break;
      }
      case SECTION_FILTERS: {
        resetEditorState();
        bottomWrap.removeViewInLayout(filtersView);
        mediaView.getBaseCell().removeView(editorView);
        break;
      }
      case SECTION_QUALITY: {
        resetVideoLimit();
        bottomWrap.removeViewInLayout(qualityControlWrap);
        break;
      }
      case SECTION_CROP: {
        resetCropState();
        bottomWrap.removeView(cropControlsWrap);
        contentView.removeView(cropLayout);
        break;
      }
      case SECTION_PAINT: {
        if (currentPaintState != null) {
          currentPaintState.removeUndoStateListener(this);
        }
        bottomWrap.removeView(paintControlsWrap);
        mediaView.getBaseCell().removeView(paintView);
        break;
      }
    }
  }

  private void applySection () {
    setLowProfile(true);
    hideSection(fromSection);

    switch (currentSection) {
      case SECTION_CAPTION: {
        mediaView.setDisallowMove(false);
        break;
      }
      case SECTION_FILTERS: {
        showEditorView();
        break;
      }
      case SECTION_CROP: {
        setInCrop(true);
        break;
      }
      case SECTION_PAINT: {
        showPaintEditor();
        break;
      }
    }
  }

  private boolean scheduleSectionChange (int fromSection, int toSection) {
    switch (fromSection) {
      case SECTION_CROP: {
        return closeCropAsync(toSection);
      }
    }
    return false;
  }

  private void prepareSectionToHide (int section) {
    switch (section) {
      case SECTION_CAPTION: {
        mediaView.setDisallowMove(true);
        break;
      }
      case SECTION_FILTERS: {
        hideEditorView();
        break;
      }
      case SECTION_QUALITY: {
        hideQualityView();
        break;
      }
      case SECTION_CROP: {
        // resetMediaPaddings(section);
        break;
      }
      case SECTION_PAINT: {
        hidePaintEditor();
        break;
      }
    }
  }

  private void resetMediaPaddings (int section) {
    mediaView.setOffsets(getHorizontalOffsets(section), 0, getSectionTopOffset(section), 0, getSectionBottomOffset(section));
  }

  private static final int MODE_BACK_PRESS = 0;
  private static final int MODE_OK = 1;
  private static final int MODE_CANCEL = 2;

  private void showYesNo () {
    showOptions(Lang.getString(R.string.DiscardCurrentChanges), new int[]{R.id.btn_discard, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Discard), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_discard) {
        changeSection(SECTION_CAPTION, MODE_CANCEL);
      }
      return true;
    }, getForcedTheme());
  }

  private boolean hasChanges () {
    switch (currentSection) {
      case SECTION_FILTERS: {
        return hasEditorChanges();
      }
      case SECTION_CROP: {
        return hasCropChanges();
      }
      case SECTION_PAINT: {
        return hasPaintChanges();
      }
      case SECTION_QUALITY: {
        return hasQualityChanges();
      }
    }
    return false;
  }

  private boolean isUIBlocked;

  public void setUIBlocked (boolean blocked) {
    this.isUIBlocked = blocked;
  }

  private boolean saveSectionData (int section) {
    switch (currentSection) {
      case SECTION_FILTERS: {
        if (currentFiltersState.isEmpty()) {
          ImageFilteredFile file = stack.getCurrent().getFilteredFile();
          if (file != null) {
            tdlib.filegen().removeFilteredBitmap(file.getFilePath());
          }
          stack.getCurrent().setFiltersState(null);
          applyFilteredBitmap(currentTargetImageFile, sourceBitmap);
        } else {
          selectMediaIfItsNot();
          setUIBlocked(true);
          applyFiltersAsync(section);
          return true;
        }
        break;
      }
      case SECTION_CROP: {
        if (!currentCropState.isEmpty()) {
          selectMediaIfItsNot();
        }
        stack.getCurrent().setCropState(currentCropState);
        break;
      }
      case SECTION_PAINT: {
        if (!currentPaintState.isEmpty()) {
          selectMediaIfItsNot();
        }
        if (stack.getCurrent().setPaintState(currentPaintState, true)) {
          mediaView.getBaseReceiver().invalidate();
        }
        break;
      }
      case SECTION_QUALITY: {
        Settings.instance().setPreferredVideoLimit(currentVideoLimit);
        break;
      }
    }
    return false;
  }

  private void resetChangedData () {
    switch (currentSection) {
      case SECTION_FILTERS: {
        if (oldFiltersState != null && hasEditedFilters) {
          currentFiltersState.reset(oldFiltersState);
        }
        break;
      }
      case SECTION_QUALITY: {
        currentVideoLimit = new Settings.VideoLimit(prevVideoLimit);
        break;
      }
      case SECTION_CROP: {
        if (oldCropState != null && !oldCropState.isEmpty()) {
          if (!oldCropState.compare(currentCropState)) {
            resetCrop(false);
          }
          stack.getCurrent().setCropState(oldCropState);
        } else {
          if (currentCropState.hasRotations()) {
            resetCrop(false);
          }
          stack.getCurrent().setCropState(null);
        }
        break;
      }
      case SECTION_PAINT: {
        boolean changed;
        if (oldPaintState != null && !oldPaintState.isEmpty()) {
          changed = stack.getCurrent().setPaintState(oldPaintState, true);
        } else {
          changed = stack.getCurrent().setPaintState(null, true);
        }
        if (changed) {
          mediaView.getBaseReceiver().invalidate();
        }
        break;
      }
    }
  }

  private boolean isCurrentReady () {
    MediaItem item = stack.getCurrent();
    return item != null && (item.getSourceGalleryFile() == null || item.getSourceGalleryFile().isReady());
  }

  private boolean isCurrentCamera () {
    return isFromCamera;
    /*MediaItem item = stack.getCurrent();
    return item != null && item.getSourceGalleryFile() != null && item.getSourceGalleryFile().isFromCamera();*/
  }

  private boolean allowDataChanges () {
    return (sectionChangeAnimator == null || !sectionChangeAnimator.isAnimating()) && !isUIBlocked && isCurrentReady();
  }

  private void changeSection (int section, int mode) {
    if (currentSection == section || !allowDataChanges()) {
      return;
    }

    boolean hasChanges = hasChanges();
    boolean applyChanges;

    switch (mode) {
      case MODE_BACK_PRESS: {
        if (hasChanges) {
          showYesNo();
          return;
        }
        applyChanges = false;
        break;
      }
      case MODE_OK: {
        applyChanges = true;
        break;
      }
      case MODE_CANCEL: {
        applyChanges = false;
        break;
      }
      default: {
        return;
      }
    }

    if (hasChanges) {
      if (applyChanges) {
        if (saveSectionData(section)) {
          return;
        }
      } else {
        resetChangedData();
      }
    }

    changeSectionImpl(section);
  }

  private void applyFiltersAsync (final int futureSection) {
    editorView.getBitmapAsync(bitmap -> {
      setUIBlocked(false);
      if (bitmap != null) {
        ImageFilteredFile filteredFile = stack.getCurrent().setFiltersState(currentFiltersState);
        tdlib.filegen().saveFilteredBitmap(filteredFile, bitmap);
        applyFilteredBitmap(filteredFile, bitmap);
        changeSectionImpl(futureSection);
      } else {
        UI.showToast("Error while saving changes, sorry", Toast.LENGTH_SHORT);
      }
    });
  }

  private void changeSectionImpl (int section) {
    if (scheduleSectionChange(currentSection, section)) {
      return;
    }
    if (!initSection(section)) {
      return;
    }

    fromSection = currentSection;
    fromTopOffset = getSectionTopOffset(fromSection);
    fromBottomOffset = getSectionBottomOffset(fromSection);
    fromHorOffset = /*fromSection == SECTION_CROP ? getHorizontalOffsets(fromSection) :*/ mediaView.getPaddingHorizontal(); // getHorizontalOffsets(fromSection);

    mediaView.getBaseCell().getDetector().preparePositionReset();

    prepareSectionToHide(fromSection);
    prepareSectionToShow(section);

    fillIcons(section);

    currentSection = section;
    toTopOffset = getSectionTopOffset(currentSection);
    toBottomOffset = getSectionBottomOffset(currentSection);
    toHorOffset = getHorizontalOffsets(currentSection);

    updateIconStates(true);

    if (sectionChangeAnimator == null) {
      sectionChangeAnimator = new FactorAnimator(ANIMATOR_SECTION, this, AnimatorUtils.LINEAR_INTERPOLATOR, 380l);
    } else {
      sectionChangeAnimator.forceFactor(0f);
    }
    sectionChangeAnimator.animateTo(1f);
  }

  private int prevActiveButtonId;

  private void fillIcons (int section) {
    int activeButtonId = 0;
    boolean isSticker = false;
    switch (section) {
      case SECTION_FILTERS:
      case SECTION_QUALITY: {
        activeButtonId = R.id.btn_adjust;
        break;
      }
      case SECTION_CROP: {
        activeButtonId = R.id.btn_crop;
        break;
      }
      case SECTION_PAINT: {
        activeButtonId = R.id.btn_paint;
        isSticker = true;
        break;
      }
      case SECTION_CAPTION: {
        break;
      }
    }

    if (isSticker) {
      // adjustOrTextButton.setIcon(R.drawable.ic_addtext, true, false);
      // cropOrStickerButton.setIcon(R.drawable.ic_addsticker, true, false);
    } else {
      if (stack.getCurrent().isVideo()) {
        adjustOrTextButton.setIcon(R.drawable.baseline_settings_24, true, section == SECTION_QUALITY);
        cropOrStickerButton.setIcon(R.drawable.baseline_rotate_90_degrees_ccw_24, true, false);
      } else {
        adjustOrTextButton.setIcon(R.drawable.baseline_tune_24, true, section == SECTION_FILTERS);
        cropOrStickerButton.setIcon(R.drawable.baseline_crop_rotate_24, true, section == SECTION_CROP);
      }
    }

    if (prevActiveButtonId != 0 && activeButtonId != prevActiveButtonId) {
      setButtonActive(prevActiveButtonId, false);
    }

    prevActiveButtonId = activeButtonId;

    if (activeButtonId != 0) {
      backButton.setIcon(R.drawable.baseline_close_24, true, false);
      sendButton.setIcon(R.drawable.baseline_check_24, true, false);
    } else {
      backButton.setIcon(R.drawable.baseline_arrow_back_24, true, false);
      sendButton.setIcon(R.drawable.deproko_baseline_send_24, true, false);
    }
  }

  private boolean hasAppliedFilters () {
    FiltersState state = stack.getCurrent().getFiltersState();
    return state != null && !state.isEmpty();
  }

  private boolean hasAppliedCrop () {
    CropState cropState = stack.getCurrent().getCropState();
    return cropState != null && !cropState.isEmpty();
  }

  private boolean hasAppliedPaints () {
    PaintState paintState = stack.getCurrent().getPaintState();
    return paintState != null && !paintState.isEmpty();
  }

  private void updateIconStates (boolean animated) {
    boolean allowEditState = !Config.MASKS_TEXTS_AVAILABLE || currentSection != SECTION_PAINT;
    adjustOrTextButton.setEdited(allowEditState && hasAppliedFilters(), animated);
    cropOrStickerButton.setEdited(allowEditState && hasAppliedCrop(), animated);
    paintOrMuteButton.setEdited(hasAppliedPaints(), animated);
    if (stopwatchButton != null) {
      int ttl = stack.getCurrent().getTTL();
      String value = ttl != 0 ? TdlibUi.getDuration(ttl, TimeUnit.SECONDS, false) : null;
      if (animated) {
        stopwatchButton.setValue(value, false);
      } else {
        stopwatchButton.forceValue(value, true);
      }
    }
  }

  private void setButtonActive (int id, boolean isActive) {
    if (id == R.id.btn_crop) {
      cropOrStickerButton.setActive(isActive, true);
    } else if (id == R.id.btn_paint) {
      paintOrMuteButton.setActive(isActive, true);
    } else if (id == R.id.btn_adjust) {
      adjustOrTextButton.setActive(isActive, true);
    }
  }

  private int currentActiveButton;

  private void setSectionChangeFactor (float factor, float fraction) {
    float sectionFromFactor = factor >= .5f ? 0f : 1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f);
    float sectionToFactor = factor < .5f ? 0f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);

    if (factor >= .5f && currentActiveButton != prevActiveButtonId) {
      if (prevActiveButtonId != 0) {
        setButtonActive(prevActiveButtonId, true);
      }
      currentActiveButton = prevActiveButtonId;
    }

    setSectionFactor(fromSection, sectionFromFactor);
    setSectionFactor(currentSection, sectionToFactor);

    if (fromSection == SECTION_CAPTION || currentSection == SECTION_CAPTION) {
      if (fromSection == SECTION_PAINT) {
        setPaintSectionFactor(sectionFromFactor);
      } else if (currentSection == SECTION_PAINT) {
        setPaintSectionFactor(sectionToFactor);
      }
    } else {
      float rangeFactor = factor <= .25f ? 0f : (factor - .25f) / .75f;
      if (fromSection == SECTION_PAINT) {
        setPaintSectionFactor(1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(rangeFactor));
      } else if (currentSection == SECTION_PAINT) {
        setPaintSectionFactor(AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(rangeFactor));
      }
    }

    mediaView.getBaseCell().getDetector().setPositionFactor(fraction);

    int currentHorOffset;
    if (toHorOffset > fromHorOffset) {
      currentHorOffset = fromHorOffset + (int) ((float) (toHorOffset - fromHorOffset) * sectionToFactor);
    } else if (toHorOffset < fromHorOffset) {
      currentHorOffset = fromHorOffset + (int) ((float) (toHorOffset - fromHorOffset) * (1f - sectionFromFactor));
    } else {
      currentHorOffset = fromHorOffset;
    }

    int currentBottomOffset;
    if (toBottomOffset > fromBottomOffset) {
      currentBottomOffset = fromBottomOffset + (int) ((float) (toBottomOffset - fromBottomOffset) * sectionToFactor);
    } else if (toBottomOffset < fromBottomOffset) {
      currentBottomOffset = fromBottomOffset + (int) ((float) (toBottomOffset - fromBottomOffset) * (1f - sectionFromFactor));
    } else {
      currentBottomOffset = fromBottomOffset;
    }

    int currentTopOffset;
    if (toTopOffset > fromTopOffset) {
      currentTopOffset = fromTopOffset + (int) ((float) (toTopOffset - fromTopOffset) * sectionToFactor);
    } else if (toTopOffset < fromTopOffset) {
      currentTopOffset = fromTopOffset + (int) ((float) (toTopOffset - fromTopOffset) * (1f - sectionFromFactor));
    } else {
      currentTopOffset = fromTopOffset;
    }

    mediaView.setOffsets(currentHorOffset, 0, currentTopOffset, 0, currentBottomOffset);
  }

  private float mainSectionDisappearFactor;

  private void setSectionFactor (int section, float factor) {
    switch (section) {
      case SECTION_CAPTION: {
        mainSectionDisappearFactor = 1f - factor;
        updateMainItemsAlpha();

        captionWrapView.setAlpha(factor);
        if (videoSliderView != null) {
          videoSliderView.setAlpha(factor);
        }

        // setTranslationY(captionView.getMeasuredHeight() * (1f - factor));
        break;
      }
      case SECTION_FILTERS: {
        filtersView.setAlpha(MathUtils.clamp(factor));
        filtersView.setTranslationY(getSectionHeight(SECTION_FILTERS) * (1f - factor));
        break;
      }
      case SECTION_QUALITY: {
        qualityControlWrap.setAlpha(MathUtils.clamp(factor));
        qualityControlWrap.setTranslationY(getSectionHeight(SECTION_QUALITY) * (1f - factor));
        break;
      }
      case SECTION_CROP: {
        cropControlsWrap.setAlpha(factor);
        mediaView.getBaseCell().getImageReceiver().setCropApplyFactor(1f - factor);
        break;
      }
      case SECTION_PAINT: {
        paintControlsWrap.setAlpha(factor);
        break;
      }
    }
  }

  // Quality stuff

  private Settings.VideoLimit prevVideoLimit;
  private Settings.VideoLimit currentVideoLimit;
  private List<Settings.VideoLimit> videoLimits;

  private void resetVideoLimit () {
    prevVideoLimit = null;
  }

  private boolean hasQualityChanges () {
    return !currentVideoLimit.equals(prevVideoLimit);
  }

  private void updateQualityInfo () {
    if (qualityInfo == null)
      return;

    ImageGalleryFile file = stack.getCurrent().getSourceGalleryFile();
    int width = file.getWidth();
    int height = file.getHeight();
    final int inputFrameRate = file.getVideoFrameRate();
    final long inputBitrate = file.getVideoBitrate();
    final double accurateWidth = file.getVideoWidth();
    final double accurateHeight = file.getVideoHeight();
    final long videoDurationMs = file.getVideoDuration(true, TimeUnit.MILLISECONDS);
    final double duration = (double) videoDurationMs / 1000.0;
    if (accurateWidth != 0 && accurateHeight != 0) {
      if (width > height) {
        width = (int) Math.max(accurateWidth, accurateHeight);
        height = (int) Math.min(accurateWidth, accurateHeight);
      } else {
        width = (int) Math.min(accurateWidth, accurateHeight);
        height = (int) Math.max(accurateWidth, accurateHeight);
      }
    }

    final int outputFrameRate = currentVideoLimit.getOutputFrameRate(inputFrameRate);

    // File size = bitrate * number of minutes * .0075.
    // Bitrate = file size / (number of minutes * .0075).
    // Number of minutes = file size / (bitrate * .0075).

    Settings.VideoSize scaled = currentVideoLimit.getOutputSize(width, height);
    if (scaled == null) {
      scaled = new Settings.VideoSize(Math.max(width, height), Math.min(width, height));
    }

    final long outputBitrate = currentVideoLimit.getOutputBitrate(scaled, outputFrameRate, inputBitrate);
    long outputFileSize = 0;
    outputFileSize += (outputBitrate * duration) / 8;
    if (!file.shouldMuteVideo()) {
      outputFileSize += (62000 * duration) / 8;
    }

    StringBuilder b = new StringBuilder();
    b.append(Strings.buildSize(outputFileSize, true));
    b.append(" • ");
    b.append(width > height ? scaled.majorSize + "x" + scaled.minorSize : scaled.minorSize + "x" + scaled.majorSize);
    b.append(" • ");
    b.append(outputFrameRate).append(" FPS");
    b.append(" • ").append((outputBitrate + (file.shouldMuteVideo() ? 0 : 6200)) / 1000).append(" kbps");
    qualityInfo.setText(b.toString());
  }

  private void updateQualitySlider () {
    if (qualitySlider == null)
      return;

    if (this.videoLimits != null) {
      this.videoLimits.clear();
    } else {
      this.videoLimits = new ArrayList<>();
    }

    ImageGalleryFile file = stack.getCurrent().getSourceGalleryFile();
    int width = file.getWidth();
    int height = file.getHeight();
    final double accurateWidth = file.getVideoWidth();
    final double accurateHeight = file.getVideoHeight();
    if (accurateWidth != 0 && accurateHeight != 0) {
      if (width > height) {
        width = (int) Math.max(accurateWidth, accurateHeight);
        height = (int) Math.min(accurateWidth, accurateHeight);
      } else {
        width = (int) Math.min(accurateWidth, accurateHeight);
        height = (int) Math.max(accurateWidth, accurateHeight);
      }
    }

    int currentValue = -1;
    int minDiff = -1;
    for (Settings.VideoLimit videoLimit : Settings.instance().videoLimits()) {
      Settings.VideoSize scaled = videoLimit.getOutputSize(width, height);
      if (scaled == null) {
        break;
      }
      int diff = Math.abs(currentVideoLimit.size.majorSize - scaled.majorSize);
      if (currentValue == -1 || diff < minDiff) {
        currentValue = videoLimits.size();
        minDiff = diff;
      }
      videoLimits.add(videoLimit);
    }
    if (currentVideoLimit.size.isUnlimited()) {
      currentValue = videoLimits.size() - 1;
    }
    qualitySlider.setValueCount(videoLimits.size());
    qualitySlider.setValue((float) currentValue / (float) (videoLimits.size() - 1));
    qualitySlider.setSlideEnabled(videoLimits.size() > 1, false);

    updateQualityInfo();
  }

  private void openQuality () {
    if (currentSection != SECTION_QUALITY) {
      if (!Config.USE_VIDEO_COMPRESSION) {
        UI.showApiLevelWarning(Build.VERSION_CODES.JELLY_BEAN_MR2);
        return;
      }
      changeSection(SECTION_QUALITY, MODE_OK);
    }
  }

  private boolean prepareQuality () {
    this.prevVideoLimit = Settings.instance().getPreferredVideoLimit();
    this.currentVideoLimit = new Settings.VideoLimit(prevVideoLimit);
    return true;
  }

  // Filters stuff

  private void openFilters () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (currentSection != SECTION_FILTERS) {
        changeSection(SECTION_FILTERS, MODE_OK);
      }
    } else {
      UI.showToast("Sorry, this feature is available only on Android 4.0+", Toast.LENGTH_SHORT);
    }
  }

  private void openPaintCanvas () {
    if (stack.getCurrent().isVideo()) {
      return;
    }
    if (currentSection != SECTION_PAINT) {
      changeSection(SECTION_PAINT, MODE_OK);
    }
  }

  private void openCrop () {
    if (Config.CROP_ENABLED) {
      if (currentSection != SECTION_CROP) {
        changeSection(SECTION_CROP, MODE_OK);
      }
    } else {
      // UI.showToast(R.string.FeatureDisabled, Toast.LENGTH_SHORT);
    }
  }

  private void goBackToCaption (boolean byBackPress) {
    if (currentSection != SECTION_CAPTION) {
      changeSection(SECTION_CAPTION, byBackPress || currentSection == SECTION_PAINT ? MODE_BACK_PRESS : MODE_CANCEL);
    }
  }

  // etc

  private void selectMediaIfItsNot () {
    if (selectDelegate == null) {
      return;
    }
    if (!checkView.isChecked()) {
      setMediaSelected(checkView.toggleChecked(), false);
    }
  }

  private void setMediaSelected (boolean isSelected, boolean animated) {
    if (selectDelegate == null) {
      return;
    }
    final int index = stack.getCurrentIndex();
    final MediaItem item = stack.getCurrent();
    if (!isSelected) {
      clearFilters(item, index);
    }
    selectDelegate.setMediaItemSelected(index, item, isSelected);
    int selectedCount = selectDelegate.getSelectedMediaCount();

    if (isSelected && selectedCount == 1) {
      if (animated) {
        animateCounterFactor(1f);
      } else {
        forceCounterFactor(1f);
      }
    } else if (selectedCount == 0 && !isSelected) {
      if (animated) {
        animateCounterFactor(0f);
      } else {
        forceCounterFactor(0f);
      }
    } else {
      counterView.setCounter(selectedCount);
    }
  }

  private void openMasks () {
    // TODO
  }

  private void addText () {
    // TODO
  }

  private boolean toggleCheck () {
    if (selectDelegate != null) {
      setMediaSelected(checkView.toggleChecked(), true);
      return true;
    }
    return false;
  }

  private static final int[][] PROPORTION_MODES = {
    {1, 1, R.id.btn_proportion_square, R.drawable.baseline_crop_square_24}, // Square
    {3, 2, R.id.btn_proportion_3_2, R.drawable.baseline_crop_5_4_24}, // 3:2
    {4, 3, R.id.btn_proportion_4_3, R.drawable.baseline_crop_3_2_24}, // 4:3
    // {7, 5, R.id.btn_proportion_16_10, R.drawable.ic_crop_7_5_black_24dp}, // 16:10
    {16, 9, R.id.btn_proportion_16_9, R.drawable.baseline_crop_16_9_24}, // 16:9

    // 5.0f / 3.0f, // 5:3
    // 5.0f / 4.0f, // 5:4
    // 7.0f / 5.0f, // 7:5
  };

  @Override
  public void onClick (View v) {
    if (isUIBlocked) {
      return;
    }

    final int viewId = v.getId();
    if (viewId == R.id.menu_btn_stopwatch) {
      showTTLOptions();
    } else if (viewId == R.id.btn_inlineOpen) {
      backFromPictureInPicture();
    } else if (viewId == R.id.btn_caption_done) {
      onCaptionDone();
    } else if (viewId == R.id.btn_caption_emoji) {
      processEmojiClick();
    } else if (viewId == R.id.btn_inlineClose) {
      closePictureInPicture();
    } else if (viewId == R.id.btn_inlinePlayPause) {
      stack.getCurrent().performClick(v);
    } else if (viewId == R.id.btn_check) {
      toggleCheck();
    } else if (viewId == R.id.btn_counter) {
      if (selectDelegate != null && (!selectDelegate.isMediaItemSelected(stack.getCurrentIndex(), stack.getCurrent()) || selectDelegate.getSelectedMediaCount() > 1)) {
        setShowOtherMedias(true);
      }
    } else if (viewId == R.id.btn_removePhoto) {
      ImageFile imageFile = ((MediaOtherView) v.getParent()).getImage();
      unselectImage(imageFile);
    } else if (viewId == R.id.btn_back) {
      if (currentSection != SECTION_CAPTION) {
        goBackToCaption(false);
      } else {
        close();
      }
    } else if (viewId == R.id.btn_send) {
      if (currentSection != SECTION_CAPTION) {
        changeSection(SECTION_CAPTION, MODE_OK);
      } else {
        send(v, Td.newSendOptions(), false, false);
      }
    } else if (viewId == R.id.btn_crop) {
      if (!Config.MASKS_TEXTS_AVAILABLE || currentSection != SECTION_PAINT) {
        if (stack.getCurrent().isVideo()) {
          rotateBy90Degrees();
        } else {
          openCrop();
        }
      } else {
        openMasks();
      }
    } else if (viewId == R.id.btn_rotate) {
      rotateBy90Degrees();
    } else if (viewId == R.id.btn_proportion) {
      if (allowDataChanges() && currentSection == SECTION_CROP) {
        IntList ids = new IntList(PROPORTION_MODES.length + 2);
        StringList strings = new StringList(PROPORTION_MODES.length + 2);
        IntList icons = new IntList(PROPORTION_MODES.length + 2);
        IntList colors = new IntList(PROPORTION_MODES.length + 2);

        MediaItem item = stack.getCurrent();

        final int width = item.getWidth();
        final int height = item.getHeight();
        // final boolean flipSides = Utils.isVertical(width, height, currentCropState.getRotateBy());

        float proportion = 0f;

        if (proportionButton.isActive()) {
          proportion = cropAreaView.getFixedProportion();

          icons.append(R.drawable.baseline_crop_free_24);
          ids.append(R.id.btn_proportion_free);
          strings.append(R.string.CropFree);
          colors.append(OPTION_COLOR_NORMAL);
        }

        float originalProportion = cropAreaView.getOriginalProportion();
        int[] proportionExists = null;
        for (int[] proportionMode : PROPORTION_MODES) {
          if ((float) proportionMode[0] / (float) proportionMode[1] == originalProportion) {
            proportionExists = proportionMode;
            break;
          }
        }
        if (originalProportion != 0f) {
          icons.append(R.drawable.baseline_crop_original_24);
          ids.append(R.id.btn_proportion_original);
          if (proportionExists != null) {
            if (proportionExists[2] == R.id.btn_proportion_square) {
              strings.append(Lang.getString(R.string.CropOriginal) + " (" + Lang.getString(R.string.CropSquare) + ")");
            } else {
              strings.append(Lang.getString(R.string.CropOriginal) + " (" + proportionExists[0] + ":" + proportionExists[1] + ")");
            }
          } else {
            strings.append(R.string.CropOriginal);
          }
          colors.append(originalProportion == proportion ? OPTION_COLOR_BLUE : OPTION_COLOR_NORMAL);
        }

        if ((float) width / (float) height != 1f) {
          // TODO ids.append(R.id.btn_proportion_flipSides);
        }

        for (int[] proportionMode : PROPORTION_MODES) {
          int id = proportionMode[2];
          int verb1 = proportionMode[0];
          int verb2 = proportionMode[1];
          int verb3 = proportionMode[3];
          if (proportionExists != null && (float) verb1 / (float) verb2 == originalProportion) {
            continue;
          }
          ids.append(id);
          if (id == R.id.btn_proportion_square) {
            strings.append(R.string.CropSquare);
          } else {
            strings.append(verb1 + ":" + verb2);
          }
          icons.append(verb3);
          colors.append((float) verb1 / (float) verb2 == proportion ? OPTION_COLOR_BLUE : OPTION_COLOR_NORMAL);
        }

        if (!currentCropState.isEmpty()) {
          colors.append(OPTION_COLOR_RED);
          ids.append(R.id.btn_crop_reset);
          strings.append(R.string.Reset);
          icons.append(R.drawable.baseline_cancel_24);
        }

        showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
          if (id == R.id.btn_crop_reset) {
            resetCrop(true);
          } else if (id == R.id.btn_proportion_free) {
            setCropProportion(0, 0);
          } else if (id == R.id.btn_proportion_original) {
            int targetWidth = cropAreaView.getTargetWidth();
            int targetHeight = cropAreaView.getTargetHeight();
            setCropProportion(Math.max(targetWidth, targetHeight), Math.min(targetWidth, targetHeight));
          } else {
            int[] mode = null;
            for (int[] proportionMode : PROPORTION_MODES) {
              if (proportionMode[2] == id) {
                mode = proportionMode;
                break;
              }
            }
            if (mode != null) {
              setCropProportion(mode[0], mode[1]);
            }
          }
          return true;
        }, getForcedTheme());
      }
    } else if (viewId == R.id.btn_adjust) {
      if (!Config.MASKS_TEXTS_AVAILABLE || currentSection != SECTION_PAINT) {
        MediaItem item = stack.getCurrent();
        switch (item.getType()) {
          case MediaItem.TYPE_GALLERY_PHOTO: {
            openFilters();
            break;
          }
          case MediaItem.TYPE_GALLERY_VIDEO: {
            openQuality();
            break;
          }
          case MediaItem.TYPE_GALLERY_GIF: {
            // TODO ?
            break;
          }
        }
      } else {
        addText();
      }
    } else if (viewId == R.id.btn_paint) {
      switch (stack.getCurrent().getType()) {
        case MediaItem.TYPE_GALLERY_PHOTO: {
          openPaintCanvas();
          break;
        }
        case MediaItem.TYPE_GALLERY_VIDEO: {
          toggleMute();
          break;
        }
      }
    } else if (viewId == R.id.btn_paintType) {
      showPaintTypes();
    } else if (viewId == R.id.paint_undo) {
      undoLastPaintAction();
    }
  }

  // TTL

  private void showTTLOptions () {
    final MediaItem item = stack.getCurrent();
    tdlib.ui().showTTLPicker(context(), item.getTTL(), true, true, item.isVideo() ? R.string.MessageLifetimeVideo : R.string.MessageLifetimePhoto, result -> {
      if (stack.getCurrent() == item) {
        int newTTL = result.getTtlTime();
        item.setTTL(newTTL);
        stopwatchButton.setValue(newTTL != 0 ? TdlibUi.getDuration(newTTL, TimeUnit.SECONDS, false) : null);
        if (newTTL != 0) {
          selectMediaIfItsNot();
        }
      }
    });
  }

  // Etc

  public void open () {
    getValue();
    popupView.showAnimatedPopupView(contentView, this);
  }

  public void minimizeOrClose () {
    if (inPictureInPicture) {
      return;
    }
    pauseVideoIfPlaying();
    if (canGoPip()) {
      enterPictureInPicture();
      return;
    }
    close();
  }

  public void close () {
    if (inPictureInPicture) {
      closePictureInPicture();
    } else if (forceAnimationType != -1 || !onBackPressed(false)) {
      popupView.hideWindow(true);
    }
  }

  public void forceClose () {
    if (forceAnimationType == -1) {
      forceAnimationType = ANIMATION_TYPE_FADE;
    }
    if (inPictureInPicture) {
      closePictureInPicture();
    } else {
      popupView.hideWindow(true);
    }
  }

  private static final int ANIMATOR_PRIVACY = 14;

  public void closeByDelete () {
    FactorAnimator animator = new FactorAnimator(ANIMATOR_PRIVACY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    animator.animateTo(1f);
    forceAnimationType = ANIMATION_TYPE_SECRET_CLOSE;
    popupView.hideWindow(true);
  }

  private boolean isMediaSent;

  public boolean isMediaSent () {
    return isMediaSent;
  }

  private boolean canDisableMarkdown () {
    if (selectDelegate == null)
      return false;
    ArrayList<ImageFile> imageFiles = selectDelegate.getSelectedMediaItems(false);
    if (imageFiles != null) {
      for (ImageFile file : imageFiles) {
        if (file instanceof ImageGalleryFile && ((ImageGalleryFile) file).canDisableMarkdown())
          return true;
      }
      return false;
    }
    ImageGalleryFile file = stack.getCurrent().getSourceGalleryFile();
    if (file.canDisableMarkdown())
      return true;
    return selectDelegate.canDisableMarkdown();
  }

  private static final int SEND_MODE_NONE = 0;
  private static final int SEND_MODE_FILES = 1;
  private static final int SEND_MODE_VIDEOS = 2;

  private int canSendAsFile () {
    if (selectDelegate == null)
      return SEND_MODE_NONE;
    ArrayList<ImageFile> imageFiles = selectDelegate.getSelectedMediaItems(false);
    if (imageFiles != null) {
      for (ImageFile file : imageFiles) {
        if (!(file instanceof ImageGalleryFile) || !((ImageGalleryFile) file).canSendAsFile())
          return SEND_MODE_NONE;
      }
      if (!imageFiles.isEmpty()) {
        for (ImageFile file : imageFiles) {
          if (!(file instanceof ImageGalleryFile && ((ImageGalleryFile) file).isVideo()))
            return SEND_MODE_FILES;
        }
        return SEND_MODE_VIDEOS;
      }
    } else {
      ImageGalleryFile file = stack.getCurrent().getSourceGalleryFile();
      if (file.canSendAsFile()) {
        return file.isVideo() ? SEND_MODE_VIDEOS : SEND_MODE_FILES;
      }
    }
    return SEND_MODE_NONE;
  }

  public void send (View view, TdApi.MessageSendOptions initialSendOptions, boolean disableMarkdown, boolean asFiles) {
    if (sendDelegate == null) {
      return;
    }

    if (initialSendOptions.schedulingState == null && getArgumentsStrict().areOnlyScheduled) {
      tdlib.ui().showScheduleOptions(this, getOutputChatId(), false, (modifiedSendOptions, disableMarkdown1) -> {
        send(view, modifiedSendOptions, disableMarkdown, asFiles);
      }, initialSendOptions, getForcedTheme());
      return;
    }

    ArrayList<ImageFile> imageFiles = selectDelegate != null ? selectDelegate.getSelectedMediaItems(true) : null;

    if (imageFiles == null) {
      imageFiles = new ArrayList<>();
      imageFiles.add(stack.getCurrent().getSourceGalleryFile());
    } else if (imageFiles.isEmpty()) {
      imageFiles.add(stack.getCurrent().getSourceGalleryFile());
    }

    if (sendDelegate.sendSelectedItems(view, imageFiles, initialSendOptions, disableMarkdown, asFiles, sendDelegate.isHideMediaEnabled())) {
      forceAnimationType = ANIMATION_TYPE_FADE;
      isMediaSent = true;
      setUIBlocked(true);
      popupView.hideWindow(true);
    }
  }

  private boolean isProfileStack () {
    return mode == MODE_PROFILE || mode == MODE_CHAT_PROFILE;
  }

  // Opening and collecting photos

  public static void openFromMedia (ViewController<?> context, MediaItem item, @Nullable TdApi.SearchMessagesFilter filter, boolean forceOpenIn) {
    MediaStack stack = null;

    if (context.isStackLocked()) {
      return;
    }
    if (filter == null && item.isGifType()) {
      filter = new TdApi.SearchMessagesFilterAnimation();
    }
    if (context instanceof MediaCollectorDelegate) {
      stack = ((MediaCollectorDelegate) context).collectMedias(item.getSourceMessageId(), filter);
    }

    if (stack == null) {
      stack = new MediaStack(context.context(), context.tdlib());
      stack.set(MediaItem.copyOf(item));
    }

    Args args = new Args(context, MODE_MESSAGES, stack);
    args.reverseMode = stack.getReverseModeHint(true);
    args.forceThumbs = stack.getForceThumbsHint(true);
    args.forceOpenIn = forceOpenIn || (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterDocument.CONSTRUCTOR);
    args.filter = filter;
    if (context instanceof MediaCollectorDelegate) {
      ((MediaCollectorDelegate) context).modifyMediaArguments(item, args);
    }

    openWithArgs(context, args);
  }

  private static MediaViewController openWithArgs (ViewController<?> context, Args args) {
    MediaViewController controller = new MediaViewController(context.context(), context.tdlib());
    controller.setArguments(args);
    controller.open();
    return controller;
  }

  public static void openFromProfile (ViewController<?> context, TdApi.User user, MediaCollectorDelegate delegate) {
    if (context.isStackLocked()) {
      return;
    }

    if (user.profilePhoto == null) {
      return;
    }

    MediaStack stack;

    stack = new MediaStack(context.context(), context.tdlib());
    stack.set(new MediaItem(context.context(), context.tdlib(), user.id, user.profilePhoto));

    Args args = new Args(context, MODE_PROFILE, stack);
    if (delegate != null) {
      delegate.modifyMediaArguments(user, args);
    }

    openWithArgs(context, args);
  }

  public static void openFromChat (ViewController<?> context, TdApi.Chat chat, MediaCollectorDelegate delegate) {
    if (ChatId.isUserChat(chat.id)) {
      openFromProfile(context, context.tdlib().chatUser(chat.id), delegate);
      return;
    }

    if (context.isStackLocked()) {
      return;
    }

    MediaItem item;
    TdApi.ChatPhoto chatPhotoFull = context.tdlib().chatPhoto(chat.id);
    if (chatPhotoFull != null) {
      item = new MediaItem(context.context(), context.tdlib(), chat.id, 0, chatPhotoFull);
    } else {
      TdApi.ChatPhotoInfo chatPhotoInfo = chat.photo;
      if (chatPhotoInfo != null) {
        item = new MediaItem(context.context(), context.tdlib(), chat.id, chatPhotoInfo);
      } else {
        return;
      }
    }

    MediaStack stack = new MediaStack(context.context(), context.tdlib());
    stack.set(item);

    Args args = new Args(context, MODE_CHAT_PROFILE, stack);
    if (delegate != null) {
      delegate.modifyMediaArguments(chat, args);
    }

    openWithArgs(context, args);
  }

  public static void openWithStack (ViewController<?> context, MediaStack stack, String caption, MediaCollectorDelegate delegate, boolean forceThumbs) {
    if (context.isStackLocked()) {
      return;
    }

    Args args = new Args(context, MODE_SIMPLE, stack);
    args.caption = caption;
    args.forceThumbs = forceThumbs;
    if (delegate != null) {
      delegate.modifyMediaArguments(stack, args);
    }

    openWithArgs(context, args);
  }

  public static void openFromMessage (TGMessage message, MediaItem item) {
    ViewController<?> context = message.controller();
    if (context.isStackLocked()) {
      return;
    }

    item.setSourceMessage(message);

    MediaStack stack;

    stack = new MediaStack(context.context(), context.tdlib());
    stack.set(item);

    Args args = new Args(context, MODE_CHAT_PROFILE, stack);
    args.reverseMode = true;
    if (context instanceof MediaCollectorDelegate) {
      ((MediaCollectorDelegate) context).modifyMediaArguments(message, args);
    }
    args.noLoadMore = message.isEventLog();
    args.areOnlyScheduled = message.isScheduled();

    openWithArgs(context, args);
  }

  public static void openFromMessage (TGMessageText msg) {
    ViewController<?> context = msg.controller();
    if (context.isStackLocked()) {
      return;
    }

    TGWebPage parsedWebPage = msg.getParsedWebPage();
    if (parsedWebPage == null) {
      return;
    }
    TdApi.WebPage webPage = parsedWebPage.getWebPage();
    MediaStack stack;

    stack = new MediaStack(context.context(), context.tdlib());

    ArrayList<MediaItem> items = parsedWebPage.getInstantItems();
    if (items != null) {
      stack.set(parsedWebPage.getInstantPosition(), items);
    } else {
      MediaItem item = MediaItem.valueOf(context.context(), context.tdlib(), msg.getMessage());
      if (item == null) {
        return;
      }
      stack.set(item);
    }

    Args args = new Args(context, MODE_MESSAGES, stack);
    args.noLoadMore = true;
    args.copyLink = webPage.url;
    args.forceThumbs = true;
    args.areOnlyScheduled = msg.isScheduled();
    if (context instanceof MediaCollectorDelegate) {
      ((MediaCollectorDelegate) context).modifyMediaArguments(msg, args);
    }
    args.setMessageThreadId(msg.messagesController().getMessageThreadId());

    openWithArgs(context, args);
  }

  public static MediaViewController openSecret (TGMessageMedia photo) {
    ViewController<?> context = photo.controller();
    MediaItem item = MediaItem.valueOf(context.context(), context.tdlib(), photo.getMessage());
    if (item == null) {
      return null;
    }
    MediaStack stack = new MediaStack(context.context(), context.tdlib());
    stack.set(item);
    item.setSecretPhoto(photo);
    Args args = new Args(context, MODE_SECRET, stack);
    return openWithArgs(context, args);
  }

  public static void openFromMessage (TGMessageMedia messageContainer, long messageId) {
    ViewController<?> context = messageContainer.controller();
    TdApi.Message msg = messageContainer.getMessage(messageId);
    MediaItem item = MediaItem.valueOf(context.context(), context.tdlib(), msg);
    if (item == null) {
      return;
    }

    TdApi.SearchMessagesFilter filter = null;
    switch (msg.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        filter = new TdApi.SearchMessagesFilterPhotoAndVideo();
        break;
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        filter = new TdApi.SearchMessagesFilterChatPhoto();
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
          TdApi.Video video = ((TdApi.MessageVideo) msg.content).video;
          UI.openFile(messageContainer.controller(), null, new File(video.video.local.path), "video/mp4", TD.getViewCount(msg.interactionInfo));
        }
        filter = new TdApi.SearchMessagesFilterPhotoAndVideo();
        break;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        filter = new TdApi.SearchMessagesFilterAnimation();
        break;
      }
      case TdApi.MessageText.CONSTRUCTOR: {
        filter = new TdApi.SearchMessagesFilterUrl();
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        filter = new TdApi.SearchMessagesFilterDocument();
        break;
      }
    }

    MediaStack stack = null;

    if (context.isStackLocked()) {
      return;
    }
    if (context instanceof MediaCollectorDelegate) {
      stack = ((MediaCollectorDelegate) context).collectMedias(msg.id, filter);
    }

    if (stack == null) {
      stack = new MediaStack(context.context(), context.tdlib());
      stack.set(item);
    }

    Args args = new Args(context, MODE_MESSAGES, stack);
    args.noLoadMore = messageContainer.isEventLog();
    if (context instanceof MediaCollectorDelegate) {
      ((MediaCollectorDelegate) context).modifyMediaArguments(msg, args);
    }
    args.setFilter(filter);
    args.setMessageThreadId(messageContainer.messagesController().getMessageThreadId());
    args.areOnlyScheduled = TD.isScheduled(msg);

    openWithArgs(context, args);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    updateTitleMargins();
    if (captionView instanceof RtlCheckListener) {
      ((RtlCheckListener) captionView).checkRtl();
    }
    if (thumbsLayoutManager != null)
      thumbsLayoutManager.setReverseLayout(Lang.rtl());
    if (thumbsRecyclerView != null) {
      thumbsRecyclerView.invalidateItemDecorations();
      ensureThumbsPosition(false, false);
    }
  }

  private void openSetSenderPopup (TdApi.Chat chat) {
    if (chat == null) return;

    tdlib().send(new TdApi.GetChatAvailableMessageSenders(chat.id), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.ChatMessageSenders.CONSTRUCTOR) {
          final SetSenderController c = new SetSenderController(context, tdlib());
          c.setArguments(new SetSenderController.Args(chat, ((TdApi.ChatMessageSenders) result).senders, chat.messageSenderId));
          c.setShowOverEverything(true);
          c.setDelegate((s) -> setNewMessageSender(chat, s));
          c.show();
        }
      });
    });
  }

  private void setNewMessageSender (TdApi.Chat chat, TdApi.ChatMessageSender sender) {
    tdlib().send(new TdApi.SetChatMessageSender(chat.id, sender.sender), o -> {
      UI.post(() -> {
        if (senderSendIcon != null) {
          senderSendIcon.update(chat.messageSenderId);
        }
      });
    });
  }
}
