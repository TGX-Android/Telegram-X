package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGSource;
import org.thunderdog.challegram.data.TranslationsManager;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.ToggleHeaderView2;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.TranslationCounterDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.TextView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class TranslationControllerV2 extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<TranslationControllerV2.Args>
 implements BottomSheetViewController.BottomSheetBaseControllerPage, Menu {

  public interface TextClickable {
    TextColorSet getTextColorSet ();
    Text.ClickCallback clickCallback ();
  }

  private final TranslationCounterDrawable translationCounterDrawable;
  private final ReplaceAnimator<TextWrapper> text;
  private ComplexReceiver textMediaReceiver;
  private final Wrapper parent;

  private TranslationsManager mTranslationsManager;
  private TranslationsManager.Translatable messageToTranslate;
  private TdApi.FormattedText originalText;
  private String messageOriginalLanguage;

  private FrameLayoutFix wrapView;
  private CustomRecyclerView recyclerView;
  private MessageTextView messageTextView;
  private ToggleHeaderView2 headerCell;
  private HeaderButton translationHeaderButton;
  private @Nullable View senderAvatarView;
  private @Nullable LinearLayout linearLayout;
  private @Nullable AvatarReceiver avatarReceiver;
  private @Nullable SenderTextView senderTextView;
  private @Nullable TextView dateTextView;
  private boolean isProtected;

  private TranslationControllerV2 (Context context, Tdlib tdlib, Wrapper parent) {
    super(context, tdlib);
    this.parent = parent;

    text = new ReplaceAnimator<>(this::updateTexts, AnimatorUtils.DECELERATE_INTERPOLATOR, 300L);
    translationCounterDrawable = new TranslationCounterDrawable(Drawables.get(R.drawable.baseline_translate_24));
    translationCounterDrawable.setColors(ColorId.icon, ColorId.background ,ColorId.iconActive);
    translationCounterDrawable.setInvalidateCallback(this::updateAnimations);
  }

  protected View onCreateView (Context context) {
    headerView = new HeaderView(context);

    headerCell = new ToggleHeaderView2(context);
    headerCell.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(67f), Gravity.TOP, Screen.dp(56), 0, Screen.dp(60), 0));
    headerCell.setTitle(Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown)), false);
    headerCell.setSubtitle(Lang.getString(R.string.TranslateOriginal), false);
    headerCell.setOnClickListener(v -> showTranslateOptions());
    headerCell.setTranslationY(Screen.dp(7.5f));

    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    headerView.setBackgroundHeight(Screen.dp(67));
    headerView.setWillNotDraw(false);
    addThemeInvalidateListener(headerView);

    wrapView = (FrameLayoutFix) super.onCreateView(context);
    wrapView.setBackgroundColor(0);
    wrapView.setBackground(null);

    TGMessage message = (messageToTranslate instanceof TGMessage) ? ((TGMessage) messageToTranslate) : null;
    if (message != null) {
      isProtected = !message.canBeSaved();
      avatarReceiver = new AvatarReceiver(senderAvatarView);
      senderAvatarView = new View(context) {
        @Override
        protected void onAttachedToWindow () {
          super.onAttachedToWindow();
          avatarReceiver.attach();
        }

        @Override
        protected void onDetachedFromWindow () {
          super.onDetachedFromWindow();
          avatarReceiver.detach();
        }

        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          avatarReceiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }

        @Override
        protected void onDraw (Canvas canvas) {
          super.onDraw(canvas);
          if (avatarReceiver.needPlaceholder())
            avatarReceiver.drawPlaceholder(canvas);
          avatarReceiver.draw(canvas);
        }
      };
      message.requestAvatar(avatarReceiver, true);
      wrapView.addView(senderAvatarView, FrameLayoutFix.newParams(Screen.dp(20), Screen.dp(20), Gravity.LEFT | Gravity.BOTTOM, Screen.dp(18), 0, 0, Screen.dp(16)));

      linearLayout = new LinearLayout(context);
      linearLayout.setOrientation(LinearLayout.HORIZONTAL);

      senderTextView = new SenderTextView(context);

      TGSource forwardInfo = message.getForwardInfo();
      int forwardTime = message.getForwardTimeStamp();

      if (forwardInfo != null) {
        senderTextView.setText(forwardInfo.getAuthorName());
      } else {
        senderTextView.setText(message.getSender().getName());
      }
      linearLayout.addView(senderTextView, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2, Gravity.LEFT | Gravity.CENTER_VERTICAL));

      if (!message.isFakeMessage()) {
        dateTextView = new TextView(context);
        dateTextView.setTextColor(Theme.getColor(ColorId.textLight));
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        dateTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        dateTextView.setText(Lang.dateYearShortTime(forwardTime > 0 ? forwardTime : message.getComparingDate(), TimeUnit.SECONDS));
        dateTextView.setMaxLines(1);
        linearLayout.addView(dateTextView, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, Gravity.RIGHT | Gravity.CENTER_VERTICAL, Screen.dp(12), 0, 0, 0));
      }
      wrapView.addView(linearLayout, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(20), Gravity.BOTTOM, Screen.dp(44), 0, Screen.dp(18), Screen.dp(16)));

    }

    messageTextView = new MessageTextView(context);
    textMediaReceiver = new ComplexReceiver(messageTextView);

    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setAdapter(new RecyclerView.Adapter<>() {
      @NonNull
      @Override
      public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        return new RecyclerView.ViewHolder(messageTextView) {

        };
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {

      }

      @Override
      public int getItemCount () {
        return 1;
      }
    });

    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) recyclerView.getLayoutParams();
    if (message != null) {
      layoutParams.bottomMargin = Screen.dp(48 - 6);
    }

    text.replace(makeTextWrapper(originalText), false);
    mTranslationsManager.requestTranslation(Lang.getDefaultLanguageToTranslateV2(messageOriginalLanguage));
    return wrapView;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.recyclerView = recyclerView;
  }

  public void setHeaderPosition (float y) {
    float y2 = parent.getTargetHeight() - Screen.dp(48);
    float y3 = y + parent.getHeaderHeight();
    float translation = Math.max(y3 - y2, 0);

    if (senderAvatarView != null) senderAvatarView.setTranslationY(translation);
    if (linearLayout != null) linearLayout.setTranslationY(translation);
  }

  private void showTranslateOptions () {
    int y = (int) Math.max(headerView != null ? headerView.getTranslationY(): 0, 0);
    int maxY = parent.getTargetHeight() - Screen.dp(280 + 16);
    int pivotY = Screen.dp(8);
    if (y > maxY) {
      pivotY = Screen.dp(24) + y - maxY;
      y = maxY;
    }


    LanguageSelectorPopup languagePopupLayout = new LanguageSelectorPopup(context, mTranslationsManager::requestTranslation, mTranslationsManager.getCurrentTranslatedLanguage(), messageOriginalLanguage);
    languagePopupLayout.languageRecyclerWrap.setTranslationY(y);
    languagePopupLayout.show();
    languagePopupLayout.languageRecyclerWrap.setPivotY(pivotY);
  }

  private void updateAnimations () {
    messageTextView.invalidate();
    wrapView.invalidate();
    if (translationHeaderButton != null) {
      translationHeaderButton.invalidate();
    }
  }

  int currentHeight = -1;
  int prevHeight = -1;

  private void updateTexts (ReplaceAnimator<?> animator) {
    messageTextView.invalidate();
    if (prevHeight <= 0) {
      return;
    }

    currentHeight = getTextAnimatedHeight();
    int contentOffset = parent.getContentOffset();
    int topEdge = parent.getTopEdge();

    int diff = currentHeight - prevHeight;
    if (diff != 0 || currentHeight != messageTextView.getMeasuredHeight()) {
      messageTextView.requestLayout();
    }
    if (diff > 0 && (topEdge > contentOffset)) {
      scrollCompensation(diff);
    }
    prevHeight = currentHeight;
  }

  private void scrollCompensation (int heightDiff) {
    MessagesManager.OnGlobalLayoutListener listener = new MessagesManager.OnGlobalLayoutListener(recyclerView, messageTextView, heightDiff);
    listener.add();
  }

  private int currentTextWidth = -1;

  private void measureText (int width) {
    currentTextWidth = width;
    for (ListAnimator.Entry<TextWrapper> entry: text) {
      entry.item.prepare(width);
      entry.item.requestMedia(textMediaReceiver, 0, Integer.MAX_VALUE);
    }
  }

  private int getTextAnimatedHeight () {
    float height = 0;
    for (ListAnimator.Entry<TextWrapper> entry: text) {
      height += entry.item.getHeight() * entry.getVisibility();
    }
    return (int) height;
  }

  private TextWrapper makeTextWrapper (TdApi.FormattedText formattedText) {
    TextWrapper textWrapper = new TextWrapper(formattedText.text, TGMessage.getTextStyleProvider(), parent.textColorSet)
      .setEntities(TextEntity.valueOf(tdlib, formattedText, null), (wrapper, text, specificMedia) -> messageTextView.invalidate())
      .setClickCallback(parent.clickCallback)
      .addTextFlags(Text.FLAG_BIG_EMOJI);

    if (currentTextWidth > 0) {
      textWrapper.prepare(currentTextWidth);
    }

    return textWrapper;
  }

  private void setTranslatedStatus (int status, boolean animated) {
    translationCounterDrawable.setStatus(status, animated);
    if (status == TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT) {
      headerCell.setTitle(Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown)), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateOriginal), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS) {
      headerCell.setTitle(Lang.getLanguageName(mTranslationsManager.getCurrentTranslatedLanguage(), Lang.getString(R.string.TranslateLangUnknown)), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslatedFrom, Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown))), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_LOADING) {
      headerCell.setTitle(Lang.getString(R.string.TranslatingTo, Lang.getLanguageName(mTranslationsManager.getCurrentTranslatedLanguage(), Lang.getString(R.string.TranslateLangUnknown))), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateWait), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_ERROR) {
      headerCell.setTitle(Lang.getString(R.string.TranslationFailed), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateTryAgain), animated);
    }
  }

  private void setTranslationResult (TdApi.FormattedText translated) {
    TdApi.FormattedText textToSet = translated != null ? translated : originalText;
    text.replace(makeTextWrapper(textToSet), true);
    updateTexts(text);
  }

  private void onTranslationError (String message) {
    parent.tooltipManager().builder(translationHeaderButton).show(tdlib, message).hideDelayed(3500, TimeUnit.MILLISECONDS);
  }

  @Override
  public void onScrollToTopRequested () {
    if (recyclerView.getAdapter() != null) {
      try {
        LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        getRecyclerView().stopScroll();
        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
          return;
        }
        int scrollTop = 0;
        View view = manager.findViewByPosition(firstVisiblePosition);
        if (view != null) {
          scrollTop -= view.getTop();
        }
        getRecyclerView().smoothScrollBy(0, -scrollTop);
      } catch (Throwable t) {
        Log.w("Cannot scroll to top", t);
      }
    }
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (headerView == null) return;

    translationHeaderButton =  headerView.addButton(menu, R.id.menu_done, getHeaderIconColorId(), this, 0, Screen.dp(60), R.drawable.bg_btn_header);
    translationHeaderButton.setCustomDrawable(translationCounterDrawable);
    headerView.getBackButton().setTranslationY(Screen.dp(7.5f));
    translationHeaderButton.setTranslationY(Screen.dp(7.5f));
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id != R.id.menu_done) return;
    if (mTranslationsManager.getCurrentTranslatedLanguage() != null) {
      mTranslationsManager.stopTranslation();
    } else {
      mTranslationsManager.requestTranslation(mTranslationsManager.getLastTranslatedLanguage());
    }
  }

  @Override
  public int getItemsHeight (RecyclerView parent) {
    return -1;
  }

  protected void onCustomShowComplete () {
    String current = mTranslationsManager.getCurrentTranslatedLanguage();
    if (current == null || StringUtils.equalsOrBothEmpty(current, messageOriginalLanguage)) {
      parent.tooltipManager().builder(headerCell).locate((targetView, outRect) -> {
        outRect.set(0, Screen.dp(8), (int) headerCell.getTitleWidth(), targetView.getMeasuredHeight() - Screen.dp(8));
      }).show(tdlib, Lang.getString(R.string.TapToSelectLanguage)).hideDelayed(3500, TimeUnit.MILLISECONDS);
    }
  }

  public HeaderView getHeaderView () {
    return headerView;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public CustomRecyclerView getRecyclerView () {
    return recyclerView;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.icon;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public int getId () {
    return R.id.controller_msgTranslate;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    messageToTranslate = args.message;
    originalText = args.message.getTextToTranslate();
    messageOriginalLanguage = args.message.getOriginalMessageLanguage();
    mTranslationsManager = new TranslationsManager(tdlib, messageToTranslate, this::setTranslatedStatus, this::setTranslationResult, this::onTranslationError);
    mTranslationsManager.saveCachedTextLanguage(originalText.text, messageOriginalLanguage);
  }

  public void showOptions () {
    StringList strings = new StringList(1);
    IntList icons = new IntList(1);
    IntList ids = new IntList(1);
    IntList colors = new IntList(1);

    if (!isProtected) {
      ids.append(R.id.btn_copyTranslation);
      strings.append(R.string.TranslationCopy);
      icons.append(R.drawable.baseline_content_copy_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    if (ids.isEmpty()) return;

    showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyTranslation) {
        TdApi.FormattedText text = mTranslationsManager.getCachedTextTranslation(originalText.text, mTranslationsManager.getCurrentTranslatedLanguage());
        if (text != null) {
          UI.copyText(text.text, R.string.CopiedText);
        }
      }

      return true;
    });
  }

  public static class Args {
    private final TranslationsManager.Translatable message;
    public Args(TranslationsManager.Translatable message) {
      this.message = message;
    }
  }

  public static class Wrapper extends BottomSheetViewController<TranslationControllerV2.Args> {
    private final TranslationControllerV2 translationControllerFragment;
    private final ViewController<?> parent;

    public Wrapper (Context context, Tdlib tdlib, ViewController<?> parent) {
      super(context, tdlib);
      this.parent = parent;
      translationControllerFragment = new TranslationControllerV2(context, tdlib, this);
    }

    private TextColorSet textColorSet;
    private Text.ClickCallback clickCallback;

    public final void setTextColorSet (TextColorSet textColorSet) {
      this.textColorSet = textColorSet;
    }

    public final void setClickCallback (Text.ClickCallback clickCallback) {
      this.clickCallback = clickCallback;
    }

    @Override
    protected void onBeforeCreateView () {
      translationControllerFragment.setArguments(getArguments());
      translationControllerFragment.getValue();
    }

    @Override
    protected HeaderView onCreateHeaderView () {
      return translationControllerFragment.getHeaderView();
    }

    @Override
    protected void onCustomShowComplete () {
      translationControllerFragment.onCustomShowComplete();
    }

    @Override
    protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
      pager.setOffscreenPageLimit(1);
      tdlib.ui().post(this::launchOpenAnimation);
    }

    @Override
    protected void onAfterCreateView () {
      setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, ColorState state) {
      super.onThemeColorsChanged(areTemp, state);
      setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
    }

    @Override
    protected void setupPopupLayout (PopupLayout popupLayout) {
      popupLayout.setBoundController(translationControllerFragment);
      popupLayout.setPopupHeightProvider(this);
      popupLayout.init(true);
      popupLayout.setTouchProvider(this);
      popupLayout.setTag(parent);
    }

    @Override
    protected void setHeaderPosition (float y) {
      int t = 0; //Math.max(translationControllerFragment.getAnimationTranslationY(), 0);
      super.setHeaderPosition(y + t);
      translationControllerFragment.setHeaderPosition(y + t);
    }

    @Override
    public int getId () {
      return translationControllerFragment.getId();
    }

    @Override
    protected int getPagerItemCount () {
      return 1;
    }

    @Override
    protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
      if (position != 0) return null;
      setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
      setDefaultListenersAndDecorators(translationControllerFragment);
      return translationControllerFragment;
    }

    @Override
    protected int getContentOffset () {
      return (getTargetHeight() - getHeaderHeight(true)) / 3;
    }

    @Override
    protected int getHeaderHeight () {
      return Screen.dp(67);
    }

    @Override
    protected boolean canHideByScroll () {
      return true;
    }

    @Override
    protected int getHideByScrollBorder () {
      return Math.min(translationControllerFragment.getTextAnimatedHeight() / 2 + Screen.dp(48), getTargetHeight() / 3);
    }

    @Override
    protected int getBackgroundColorId () {
      return ColorId.filling;
    }
  }

  private class MessageTextView extends View {
    public MessageTextView (Context context) {
      super(context);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      measureText(MeasureSpec.getSize(widthMeasureSpec) - Screen.dp(36));
      int textHeight = getTextAnimatedHeight();
      if (prevHeight <= 0) {
        currentHeight = textHeight;
        prevHeight = currentHeight;
      }

      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(textHeight + Screen.dp(12), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw (Canvas canvas) {
      float alpha = translationCounterDrawable.getLoadingTextAlpha();
      for (ListAnimator.Entry<TextWrapper> entry: text) {
        entry.item.draw(canvas, Screen.dp(18), Screen.dp(6), null, alpha * entry.getVisibility(), textMediaReceiver);
      }
      invalidate();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      textMediaReceiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      textMediaReceiver.detach();
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      if (super.onTouchEvent(event)) return true;
      for (ListAnimator.Entry<TextWrapper> entry: text) {
        if (entry.getVisibility() == 1f && entry.item.onTouchEvent(this, event)) {
          return true;
        }
      }

      if (event.getAction() == MotionEvent.ACTION_UP) {
        showOptions();
      }

      return true;
    }
  }

  @SuppressLint("ViewConstructor")
  public static class LanguageSelectorPopup extends PopupLayout {
    public final MenuMoreWrap languageRecyclerWrap;
    private final LanguageSelectorPopup.OnLanguageSelectListener delegate;

    public interface OnLanguageSelectListener {
      void onSelect (String langCode);
    }

    public LanguageSelectorPopup (Context context, LanguageSelectorPopup.OnLanguageSelectListener delegate, String selected, String original) {
      super(context);
      this.delegate = delegate;

      RecyclerView languageRecyclerView = new CustomRecyclerView(context);
      languageRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      languageRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
      languageRecyclerView.setAdapter(new LanguageAdapter(context, this::onOptionClick, selected, original));
      languageRecyclerView.setItemAnimator(null);

      languageRecyclerWrap = new MenuMoreWrap(context) {
        @Override
        public int getItemsHeight () {
          return Screen.dp(280);
        }
      };
      languageRecyclerWrap.init(null, null);
      languageRecyclerWrap.addView(languageRecyclerView, FrameLayoutFix.newParams(Screen.dp(178), Screen.dp(280)));
      languageRecyclerWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_HEADER);
    }

    public void show () {
      init(true);
      setIgnoreAllInsets(true);
      showMoreView(languageRecyclerWrap);
    }

    private void onOptionClick (View v) {
      if (!(v instanceof LanguageView)) return;
      LanguageView languageView = (LanguageView) v;
      delegate.onSelect(languageView.langCode);

      ArrayList<String> recentsLanguages = Settings.instance().getTranslateLanguageRecents();
      int index = recentsLanguages.indexOf(languageView.langCode);
      boolean needAddToRecent = index == -1 || index > 0;
      if (needAddToRecent) {
        if (index != -1) {
          recentsLanguages.remove(index);
        }
        recentsLanguages.add(0, languageView.langCode);
        while (recentsLanguages.size() > 4) {
          recentsLanguages.remove(4);
        }
      }
      Settings.instance().setTranslateLanguageRecents(recentsLanguages);

      hideWindow(true);
    }
  }

  public static class LanguageAdapter extends RecyclerView.Adapter<LanguageViewHolder> {
    private final ArrayList<String> languages;
    private final View.OnClickListener listener;
    private final Context context;
    private final ArrayList<String> recents;
    private final int selectedPosition;
    private final int originalPosition;

    public LanguageAdapter (Context context, View.OnClickListener listener, String selected, String original) {
      this.recents = Settings.instance().getTranslateLanguageRecents();
      this.languages = new ArrayList<>(Lang.getSupportedLanguagesForTranslate().length);
      this.listener = listener;
      this.context = context;

      addLanguage(selected);
      addLanguage(original);
      for (String lang: recents) {
        if (StringUtils.equalsOrBothEmpty(lang, selected)) continue;
        if (StringUtils.equalsOrBothEmpty(lang, original)) continue;
        addLanguage(lang);
      }

      for (String lang: Lang.getSupportedLanguagesForTranslate()) {
        if (StringUtils.equalsOrBothEmpty(lang, selected)) continue;
        if (StringUtils.equalsOrBothEmpty(lang, original)) continue;
        if (recents.contains(lang)) continue;
        addLanguage(lang);
      }

      originalPosition = getPosition(original);
      selectedPosition = getPosition(selected);
    }

    private void addLanguage (String lang) {
      if (Lang.getLanguageName(lang, null) != null) {
        languages.add(lang);
      }
    }

    private int getPosition (String language) {
      for (int a = 0; a < languages.size(); a++) {
        if (StringUtils.equalsOrBothEmpty(language, languages.get(a))) {
          return a;
        }
      }
      return -1;
    }

    @NonNull
    @Override
    public LanguageViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return LanguageViewHolder.create(context, listener);
    }

    @Override
    public void onBindViewHolder (@NonNull LanguageViewHolder holder, int position) {
      String lang = languages.get(position);
      holder.bind(lang, position == originalPosition, position == selectedPosition, recents.contains(lang));
    }

    @Override
    public int getItemCount () {
      return languages.size();
    }
  }

  public static class LanguageViewHolder extends RecyclerView.ViewHolder {
    public LanguageViewHolder (View view) {
      super(view);
    }

    public static LanguageViewHolder create (Context context, View.OnClickListener onClickListener) {
      LanguageView view = new LanguageView(context);
      view.setOnClickListener(onClickListener);
      Views.setClickable(view);
      RippleSupport.setSimpleWhiteBackground(view);
      return new LanguageViewHolder(view);
    }

    public void bind (String language, boolean isOriginal, boolean isSelected, boolean isRecent) {
      LanguageView languageView = (LanguageView) itemView;
      languageView.langCode = language;
      languageView.isSelected = isSelected;
      languageView.isOriginal = isOriginal;
      languageView.isRecent = isRecent;
      languageView.titleView.setText(Lang.getLanguageName(language, language));
      /*languageView.titleView.setTranslationY(isOriginal ? -Screen.dp(9.5f): 0);*/
      languageView.subtitleView.setVisibility(/*isOriginal ? View.VISIBLE: */ View.GONE);
      languageView.setPadding(Screen.dp(16), 0, Screen.dp((isSelected || isOriginal || isRecent) ? 40: 16), 0);
      languageView.updateDrawable();
      languageView.invalidate();
    }
  }

  public static class LanguageView extends FrameLayout {
    private final TextView titleView;
    private final TextView subtitleView;
    private String langCode;
    private Drawable drawable;
    private boolean isSelected;
    private boolean isOriginal;
    private boolean isRecent;

    public LanguageView (@NonNull Context context) {
      super(context);

      titleView = new TextView(context);
      titleView.setTextColor(Theme.getColor(ColorId.text));
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      titleView.setEllipsize(TextUtils.TruncateAt.END);
      titleView.setMaxLines(1);
      addView(titleView, FrameLayoutFix.newParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

      subtitleView = new TextView(context);
      subtitleView.setText(Lang.getString(R.string.ChatTranslateOriginal));
      subtitleView.setTextColor(Theme.getColor(ColorId.textLight));
      subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
      subtitleView.setEllipsize(TextUtils.TruncateAt.END);
      subtitleView.setMaxLines(1);
      addView(subtitleView, FrameLayoutFix.newParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, Screen.dp(6)));
    }

    public void updateDrawable () {
      if (isSelected) {
        drawable = Drawables.get(R.drawable.baseline_check_24);
      } else if (isOriginal) {
        drawable = Drawables.get(R.drawable.baseline_translate_off_24);
      } else if (isRecent) {
        drawable = Drawables.get(R.drawable.baseline_recent_24);
      } else {
        drawable = null;
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(178), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(50), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      if (drawable != null) {
        Drawables.draw(canvas, drawable, getMeasuredWidth() - Screen.dp(40), Screen.dp(13), Paints.getPorterDuffPaint(Theme.getColor(isSelected ? ColorId.iconActive: ColorId.icon)));
      }
    }
  }

  private class SenderTextView extends View {
    private String senderString;
    private Text senderText;

    public SenderTextView (Context context) {
      super(context);
    }

    public void setText (String text) {
      senderString = text;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      updateSenderText(getMeasuredWidth());
    }

    protected void updateSenderText (int maxWidth) {
      TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setTypeface(Fonts.getRobotoMedium());
      TextStyleProvider textStyleProvider = new TextStyleProvider(textPaint);
      textStyleProvider.setTextSize(12);
      senderText = new Text.Builder(senderString, maxWidth, textStyleProvider, () -> Theme.getColor(ColorId.textLight))
        .singleLine()
        .clipTextArea()
        .view(this)
        .build();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      senderText.draw(canvas, 0, (getMeasuredHeight() - Screen.dp(12)) / 2 - Screen.dp(1) );
    }
  }
}