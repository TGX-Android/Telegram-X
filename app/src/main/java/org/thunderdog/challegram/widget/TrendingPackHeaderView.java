package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextMedia;

public class TrendingPackHeaderView extends RelativeLayout implements Text.TextMediaListener {
  private final android.widget.TextView newView;
  private final NonMaterialButton button;
  private final TextView subtitleView;
  private final View premiumLockIcon;
  private final Drawable lockDrawable;

  public TrendingPackHeaderView (Context context) {
    super(context);

    lockDrawable = Drawables.get(R.drawable.baseline_lock_16);

    RelativeLayout.LayoutParams params;
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(16f));
    params.addRule(Lang.alignParent());
    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(6f);
    } else {
      params.rightMargin = Screen.dp(6f);
    }
    params.topMargin = Screen.dp(3f);
    newView = new NoScrollTextView(context);
    newView.setId(R.id.btn_new);
    newView.setSingleLine(true);
    newView.setPadding(Screen.dp(4f), Screen.dp(1f), Screen.dp(4f), 0);
    newView.setTextColor(Theme.getColor(ColorId.promoContent));
    newView.setTypeface(Fonts.getRobotoBold());
    newView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
    newView.setText(Lang.getString(R.string.New).toUpperCase());
    newView.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(16f);
    } else {
      params.leftMargin = Screen.dp(16f);
    }
    params.topMargin = Screen.dp(5f);
    params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
    button = new NonMaterialButton(context);
    button.setId(R.id.btn_addStickerSet);
    button.setText(R.string.Add);
    button.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(16f);
    } else {
      params.leftMargin = Screen.dp(16f);
    }
    params.topMargin = Screen.dp(5f);
    params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
    params.width = params.height = Screen.dp(16);
    premiumLockIcon = new View(context) {
      @Override
      protected void dispatchDraw (Canvas canvas) {
        super.dispatchDraw(canvas);
        Drawables.draw(canvas, lockDrawable, 0, 0, Paints.getPorterDuffPaint(Theme.getColor(ColorId.text)));
      }
    };
    premiumLockIcon.setId(R.id.btn_addStickerSet);
    premiumLockIcon.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
    } else {
      params.rightMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
    }

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(Lang.alignParent());
    params.topMargin = Screen.dp(22f);
    subtitleView = new NoScrollTextView(context);
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    subtitleView.setTextColor(Theme.textDecentColor());
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setLayoutParams(params);

    addView(newView);
    addView(button);
    addView(premiumLockIcon);
    addView(subtitleView);
  }

  public void setButtonOnClickListener (View.OnClickListener listener) {
    button.setOnClickListener(listener);
    premiumLockIcon.setOnClickListener(listener);
  }

  public void setThemeProvider (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(newView, ColorId.promoContent);
      themeProvider.addThemeInvalidateListener(newView);
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeInvalidateListener(button);
      themeProvider.addThemeInvalidateListener(premiumLockIcon);
      themeProvider.addThemeTextDecentColorListener(subtitleView);
      ViewSupport.setThemedBackground(newView, ColorId.promo, themeProvider).setCornerRadius(3f);
    }
  }

  private FormattedText title;

  public void setStickerSetInfo (TdlibDelegate context, @Nullable TGStickerSetInfo stickerSet, @Nullable String highlight, boolean isInProgress, boolean isNew) {
    setTag(stickerSet);

    boolean needLock = stickerSet != null && stickerSet.isEmoji() && !stickerSet.isInstalled() && !context.tdlib().account().isPremium();

    newView.setVisibility(!isNew ? View.GONE : View.VISIBLE);
    button.setVisibility(needLock ? GONE: VISIBLE);
    button.setInProgress(stickerSet != null && !stickerSet.isRecent() && isInProgress, false);
    button.setIsDone(stickerSet != null && stickerSet.isInstalled(), false);
    button.setTag(stickerSet);

    premiumLockIcon.setVisibility(needLock ? VISIBLE: GONE);
    premiumLockIcon.setTag(stickerSet);


    String t = stickerSet != null ? stickerSet.getTitle() : "";
    title = FormattedText.valueOf(context, t, null);
    titleHighlight = Highlight.valueOf(t, highlight);

    subtitleView.setText(stickerSet != null ? Lang.plural(stickerSet.isEmoji() ? R.string.xEmoji: R.string.xStickers, stickerSet.getFullSize()) : "");

    if (Views.setAlignParent(newView, Lang.rtl())) {
      int rightMargin = Screen.dp(6f);
      int topMargin = Screen.dp(3f);
      Views.setMargins(newView, Lang.rtl() ? rightMargin : 0, topMargin, Lang.rtl() ? 0 : rightMargin, 0);
      Views.updateLayoutParams(newView);
    }

    if (Views.setAlignParent(button, Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT)) {
      int leftMargin = Screen.dp(16f);
      int topMargin = Screen.dp(5f);
      Views.setMargins(button, Lang.rtl() ? 0 : leftMargin, topMargin, Lang.rtl() ? leftMargin : 0, 0);
      Views.updateLayoutParams(button);
    }

    if (Views.setAlignParent(subtitleView, Lang.rtl())) {
      Views.updateLayoutParams(subtitleView);
    }
    buildTitle();
  }

  private int titleX, titleY;
  private Highlight titleHighlight;
  private Text displayTitle;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildTitle();
  }

  private void buildTitle () {
    if (title == null) return;

    int isNewWidth = newView.getVisibility() == VISIBLE ? newView.getMeasuredWidth() + Screen.dp(6): 0;
    int addButtonWidth = button.getVisibility() == VISIBLE ? button.getMeasuredWidth() + Screen.dp(12): 0;
    int premiumLockWidth = premiumLockIcon.getVisibility() == VISIBLE ? premiumLockIcon.getMeasuredWidth() + Screen.dp(12): 0;

    int width = getMeasuredWidth();
    int avail = width - Screen.dp(24) - isNewWidth - addButtonWidth - premiumLockWidth;

    this.titleX = Screen.dp(12 + 4) + isNewWidth;
    this.titleY = getPaddingTop() + Screen.dp(2);
    this.displayTitle = new Text.Builder(
      title, avail,
      Paints.robotoStyleProvider(16f),
      TextColorSets.Regular.NORMAL,
      this
    ).singleLine()
      .highlight(titleHighlight)
      .allBold()
      .view(this)
      .noClickable()
      .build();
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    super.dispatchDraw(canvas);
    if (displayTitle != null) {
      displayTitle.draw(canvas, titleX, titleY);
    }
  }

  @Override
  public void onInvalidateTextMedia (Text text, @Nullable TextMedia specificMedia) {
    invalidate();
  }
}
