/**
 * File created on 23/04/15 at 19:23
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SwitchDrawable;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.ToggleDelegate;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.widget.ClearButton;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.util.ColorChanger;

@SuppressWarnings("unused")
public class HeaderView extends FrameLayoutFix implements View.OnClickListener, View.OnLongClickListener, Destroyable, Lang.Listener, Screen.StatusBarHeightChangeListener, TGLegacyManager.EmojiLoadListener {
  private static boolean BACK_BUTTON_ON_TOP = true;

  private static final float TRANSLATION_FACTOR = .14f;
  private static final float TRANSLATION_VERTICAL_FACTOR = .28f;

  private static final int TEXT_SWITCH_NONE = 0;
  private static final int TEXT_SWITCH_NORMAL = 1;
  private static final int TEXT_SWITCH_TOGGLE = 2;
  private static final int TEXT_SWITCH_CUSTOM = 3;
  // private static final int TEXT_SWITCH_SIMPLE = 4;

  private NavigationStack stack;
  private boolean isOwningStack;
  private @Nullable NavigationController navigation;

  private LinearLayout menu;
  private LinearLayout menuPreview;
  // private boolean menuProgressShown;
  // private SpinnerView menuProgress;

  private BackHeaderButton backButton;

  private View title;
  private View preview;
  private ViewController<?> previewItem, baseItem;

  private TextView textTitle;
  private TextView textPreview;

  private float height;

  // Player

  private HeaderFilling filling;

  public HeaderView (Context context) {
    super(context);

    backButton = new BackHeaderButton(context);
    backButton.setParentHeader(this);
    backButton.setOnClickListener(backButton);
    backButton.setVisibility(View.GONE);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Size.getHeaderPortraitSize(), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT)));

    addView(backButton);

    menu = genButtonsWrap(context);

    addView(menu);

    textTitle = genTextTitle(context);
    height = Size.getHeaderPortraitSize();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          Rect bounds = filling.getBounds();
          outline.setRect(bounds.left, bounds.top, bounds.right, bounds.top + filling.getOutlineBottom() + getCurrentHeaderOffset());
          outline.setAlpha(0f);
        }
      });
    }
  }

  @Override
  public void onEmojiPartLoaded () {
    if (textTitle != null) {
      textTitle.invalidate();
    }
    if (textPreview != null) {
      textPreview.invalidate();
    }
  }

  public BackHeaderButton getBackButton () {
    return backButton;
  }

  private boolean needOffsets;

  public boolean needOffsets () {
    return needOffsets;
  }

  public void initWithSingleController (@NonNull ViewController<?> controller, boolean needOffsets) {
    this.navigation = null;
    this.stack = new NavigationStack(controller);
    this.isOwningStack = true;
    this.needOffsets = needOffsets;
    this.filling = new HeaderFilling(this, null);
    if (needOffsets) {
      filling.setNeedOffsets();
      setHeaderOffset(getTopOffset());
      setClipToPadding(false);
    } else {
      setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getSize(false) + filling.getExtraHeight(), Gravity.TOP));
    }
    this.filling.layout((int) height, getHeightFactor());
    ViewUtils.setBackground(this, filling);
    setTitle(controller);
    TGLegacyManager.instance().addEmojiListener(this);
    Lang.addLanguageListener(this);
    controller.addDestroyListener(this);
    if (needOffsets) {
      Screen.addStatusBarHeightListener(this);
    }
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    setHeaderOffset(getTopOffset());
  }

  public void initWithController (NavigationController controller) {
    this.navigation = controller;
    this.needOffsets = true;
    this.stack = controller.getStack();
    this.filling = new HeaderFilling(this, controller);
    this.filling.setNeedOffsets();
    this.filling.layout((int) height, getHeightFactor());
    setHeaderOffset(getTopOffset());
    ViewUtils.setBackground(this, filling);
    Screen.addStatusBarHeightListener(this);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  private int currentHeaderOffset = -1;

  private int getCurrentHeaderOffset () {
    return currentHeaderOffset != -1 ? currentHeaderOffset : 0;
  }

  private void setHeaderOffset (int headerOffset) {
    if (this.currentHeaderOffset != headerOffset) {
      this.currentHeaderOffset = headerOffset;
      Views.setTopMargin(backButton, headerOffset);
      Views.setTopMargin(menu, headerOffset);
      Views.setTopMargin(menuPreview, headerOffset);
      Views.setTopMargin(textTitle, Screen.dp(15f) + headerOffset);
      Views.setTopMargin(textPreview, Screen.dp(15f) + headerOffset);
      if (moreWrap != null) {
        moreWrap.setTranslationY(getTranslationY() + getCurrentHeaderOffset());
      }
      if (title != textTitle) {
        dispatchOffset(title);
      }
      if (preview != null && preview != textPreview) {
        dispatchOffset(preview);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      if (isOwningStack) {
        setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getSize(true) + filling.getExtraHeight(), Gravity.TOP));
      } else {
        setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getMaximumHeaderSize() + getTopOffset() + filling.getExtraHeight() + Size.getHeaderPortraitSize(), Gravity.TOP));
      }
      // requestLayout();
    }
  }

  public interface OffsetChangeListener {
    void onHeaderOffsetChanged (HeaderView headerView, int newOffset);
  }

  // Theme stuff

  private void invalidateHeader () {
    invalidate(0, 0, getMeasuredWidth(), filling.getBottom() + filling.getExtraHeight());
  }

  public void resetColors (ViewController<?> c, ViewController<?> preview) {
    boolean animating = (navigation != null && navigation.isAnimating());
    if (animating) {
      if (previewOpened) {
        resetAnimationColors();
      } else {
        filling.setColor(c.getHeaderColor());
      }
      if (floatSwitch != null) {
        floatSwitch.invalidate();
      }
      invalidateHeader();
    }

    final int backButtonColor = getBackButtonColor(c, true);
    final int menuId = getMenuId(c, true);
    if (menuId != 0) {
      updateButtonColors(c, menuId, backButtonColor);
    }
    if (c.inTransformMode()) {
      final int realMenuId = c.getMenuId();
      if (realMenuId != 0) {
        updateButtonColors(c, realMenuId, c.getHeaderIconColor());
      }
    }
    int searchMenuId = c.getSearchMenuId();
    if (searchMenuId != 0) {
      updateButtonColors(c, searchMenuId, c.getSearchHeaderIconColor());
    }
    int selectMenuId = c.getSelectMenuId();
    if (selectMenuId != 0) {
      updateButtonColors(c, selectMenuId, c.getSelectHeaderIconColor());
    }

    if (floatSwitch != null) {
      floatSwitch.invalidate();
    }

    if (preview == null)
      animating = false;

    if (!animating || preview.getHeaderIconColorId() == c.getHeaderIconColorId()) {
      backButton.setColor(backButtonColor);
    }
    if (!animating || preview.getHeaderTextColorId() == c.getHeaderTextColorId()) {
      final int headerTextColor = getHeaderTextColor(c, true);
      if (textTitle != null) {
        textTitle.setTextColor(headerTextColor);
      }
      if (textPreview != null) {
        textPreview.setTextColor(headerTextColor);
      }
    }
    if (!animating || preview.getHeaderColorId() == c.getHeaderColorId()) {
      filling.setColor(getHeaderColor(c, true));
      invalidateHeader();
    }
  }

  /* optimization */

  private boolean preventLayout;
  private boolean layoutRequested;

  public void preventLayout () {
    preventLayout = true;
  }

  public void layoutIfRequested () {
    preventLayout = false;
    if (layoutRequested) {
      layoutRequested = false;
      requestLayout();
    }
  }

  public void cancelLayout () {
    preventLayout = false;
    layoutRequested = false;
  }

  public boolean isLayoutRequested () {
    return layoutRequested;
  }

  @Override
  public void requestLayout () {
    if (!preventLayout) {
      if (layoutLimit == -1) {
        super.requestLayout();
      } else if (layoutComplete < layoutLimit) {
        layoutComplete++;
        super.requestLayout();
      }
    } else {
      layoutRequested = true;
    }
  }

  private int layoutLimit = -1;
  private int layoutComplete;

  public void preventNextLayouts (int limit) {
    layoutLimit = limit;
    layoutComplete = 0;
  }

  public void completeNextLayout () {
    layoutLimit = -1;
    layoutComplete = 0;
  }

  /* optimization end */

  public void setBackgroundHeight (int height) {
    if (this.height != height) {
      this.height = height;
      filling.layout(height, getHeightFactor());
      invalidate();
    }
  }

  @Override
  public void onClick (View v) {
    if (navigation == null || (!navigation.isDestroyed() && !navigation.isAnimating())) {
      ViewController<?> item = stack.getCurrent();
      if (item != null && ((!item.inSelectMode() && item.getMenuId() != 0) || (item.inSelectMode() && item.getSelectMenuId() != 0))) {
        ((Menu) item).onMenuItemPressed(v.getId(), v);
      }
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      filling.layout(getMeasuredWidth(), (int) height, getHeightFactor());
      invalidate();
    }
  }

  private SwitchDrawable findSwitchDrawable (LinearLayout layout, int menuId, int buttonId) {
    if (layout == null || layout.getId() != menuId) {
      return null;
    }
    View button = layout.findViewById(buttonId);
    return button != null ? (SwitchDrawable) ((HeaderButton) button).getDrawable() : null;
  }

  public SwitchDrawable getSwitchDrawable (int menuId, int buttonId) {
    if (navigation != null && navigation.isAnimating()) { // preview is priority
      SwitchDrawable drawable = findSwitchDrawable(menuPreview, menuId, buttonId);
      return drawable == null ? findSwitchDrawable(menu, menuId, buttonId) : drawable;
    } else { // base is priority
      SwitchDrawable drawable = findSwitchDrawable(menu, menuId, buttonId);
      return drawable == null ? findSwitchDrawable(menuPreview, menuId, buttonId) : drawable;
    }
  }

  public void updateLockButton (int menuId) {
    if (menu.getId() == menuId) {
      View button = menu.findViewById(R.id.menu_btn_lock);
      if (button != null) {
        ((LockHeaderButton) button).update();
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View button = menuPreview.findViewById(R.id.menu_btn_lock);
      if (button != null) {
        ((LockHeaderButton) button).update();
      }
    }
  }

  public int getButtonsWidth (int menuId) {
    if (menuPreview != null && menuPreview.getId() == menuId) {
      return menuPreview.getMeasuredWidth();
    }
    if (menu.getId() == menuId) {
      return menu.getMeasuredWidth();
    }
    return -1;
  }

  public void updateButtonsTransform (int menuId, ViewController<?> c, float factor) {
    if (menu.getId() == menuId) {
      c.applyHeaderMenuTransform(menu, factor);
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      c.applyHeaderMenuTransform(menuPreview, factor);
    }
  }

  public void updateBackButtonColor (ViewController<?> c, int colorFilter) {
    if (c.isFocused()) {
      backButton.setColor(colorFilter);
    }
  }

  private static void updateButtonColors (ViewGroup menu, int colorFilter) {
    int count = menu.getChildCount();
    for (int i = 0; i < count; i++) {
      View view = menu.getChildAt(i);
      if (view instanceof ViewGroup) {
        updateButtonColors((ViewGroup) view, colorFilter);
      } else if (view instanceof HeaderButton) {
        view.invalidate();
      }
    }
  }

  public void updateButtonColors (ViewController<?> c, int menuId, int colorFilter) {
    if (menu != null && menu.getId() == menuId) {
      updateButtonColors(menu, colorFilter);
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      updateButtonColors(menuPreview, colorFilter);
    }
  }

  public void updateButtonColorFactor (ViewController<?> c, int menuId, float colorFactor) {
    if (menu.getId() == menuId) {
      final int childCount = menu.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = menu.getChildAt(i);
        if (view != null) {
          if (view instanceof HeaderButton) {
            ((HeaderButton) view).setThemeColorFactor(colorFactor);
            view.invalidate();
          } else {
            c.updateCustomButtonColorFactor(view, menuId, colorFactor);
          }
        }
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      final int childCount = menuPreview.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = menuPreview.getChildAt(i);
        if (view != null) {
          if (view instanceof HeaderButton) {
            ((HeaderButton) view).setThemeColorFactor(colorFactor);
            view.invalidate();
          } else {
            c.updateCustomButtonColorFactor(view, menuId, colorFactor);
          }
        }
      }
    }
  }

  public void updateButton (int menuId, int id, RunnableData<HeaderButton> buttonCallback) {
    if (menu.getId() == menuId) {
      View button = menu.findViewById(id);
      if (button != null) {
        buttonCallback.runWithData((HeaderButton) button);
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View button = menuPreview.findViewById(id);
      if (button != null) {
        buttonCallback.runWithData((HeaderButton) button);
      }
    }
  }

  public void updateButton (int menuId, int id, int visibility, int image) {
    if (menu.getId() == menuId) {
      View button = menu.findViewById(id);
      if (button != null) {
        button.setVisibility(visibility);
        if (image != 0) {
          ((HeaderButton) button).setImageResource(image);
        }
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View button = menuPreview.findViewById(id);
      if (button != null) {
        button.setVisibility(visibility);
        if (image != 0) {
          ((HeaderButton) button).setImageResource(image);
        }
      }
    }
  }

  public void updateButtonAlpha (int menuId, int id, float alpha) {
    if (menu.getId() == menuId) {
      View button = menu.findViewById(id);
      if (button != null) {
        button.setAlpha(alpha);
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View button = menuPreview.findViewById(id);
      if (button != null) {
        button.setAlpha(alpha);
      }
    }
  }

  public void updateCustomButtons (ViewController<?> c, int menuId) {
    if (menu.getId() == menuId) {
      c.updateCustomMenu(menuId, menu);
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      c.updateCustomMenu(menuId, menuPreview);
    }
  }

  public void updateCustomButton (int menuId, int id, RunnableData<View> callback) {
    if (menu.getId() == menuId) {
      View view = menu.findViewById(id);
      if (view != null)
        callback.runWithData(view);
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View view = menuPreview.findViewById(id);
      if (view != null)
        callback.runWithData(view);
    }
  }

  public void updateMenuClear (int menuId, int id, boolean visible, boolean animated) {
    if (menu.getId() == menuId) {
      View clear = menu.findViewById(id);
      if (clear != null) {
        ((ClearButton) clear).setVisible(visible, animated);
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View clear = menuPreview.findViewById(id);
      if (clear != null) {
        ((ClearButton) clear).setVisible(visible, animated);
      }
    }
  }

  public void updateMenuInProgress (int menuId, int id, boolean inProgress) {
    if (menu.getId() == menuId) {
      View clear = menu.findViewById(id);
      if (clear != null) {
        ((ClearButton) clear).setInProgress(inProgress);
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View clear = menuPreview.findViewById(id);
      if (clear != null) {
        ((ClearButton) clear).setInProgress(inProgress);
      }
    }
  }

  public void updateMenuHint (int menuId, int id, String text, boolean enabled) {
    if (menu.getId() == menuId) {
      View hint = menu.findViewById(id);
      if (hint != null) {
        ((TextView) hint).setText(text);
        ((TextView) hint).setTextColor(enabled ? ACTIVE_HINT_COLOR : INACTIVE_HINT_COLOR);
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View hint = menuPreview.findViewById(id);
      if (hint != null) {
        ((TextView) hint).setText(text);
        ((TextView) hint).setTextColor(enabled ? ACTIVE_HINT_COLOR : INACTIVE_HINT_COLOR);
      }
    }
  }

  public void updateMenuStopwatch (int menuId, int id, @Nullable String value, boolean isVisible, boolean force) {
    if (menu.getId() == menuId) {
      View stopwatch = menu.findViewById(id);
      if (stopwatch != null) {
        if (force) {
          ((StopwatchHeaderButton) stopwatch).forceValue(value, isVisible);
        } else {
          ((StopwatchHeaderButton) stopwatch).setIsVisible(isVisible);
          ((StopwatchHeaderButton) stopwatch).setValue(value);
        }
      }
    }
    if (menuPreview != null && menuPreview.getId() == menuId) {
      View stopwatch = menuPreview.findViewById(id);
      if (stopwatch != null) {
        if (force) {
          ((StopwatchHeaderButton) stopwatch).forceValue(value, isVisible);
        } else {
          ((StopwatchHeaderButton) stopwatch).setIsVisible(isVisible);
          ((StopwatchHeaderButton) stopwatch).setValue(value);
        }
      }
    }
  }

  public HeaderButton genButton (@IdRes int id, @DrawableRes int drawableRes, @ThemeColorId int themeColorId, @Nullable ViewController<?> themeProvider, int width, OnClickListener listener) {
    return genButton(id, drawableRes, themeColorId, themeProvider, width, ThemeDeprecated.headerSelector(), listener);
  }

  public HeaderButton genButton (@IdRes int id, @DrawableRes int image, @ThemeColorId int themeColorId, @Nullable ViewController<?> themeProvider, int width, int resource, OnClickListener listener) {
    HeaderButton btn = new HeaderButton(getContext());
    btn.setButtonBackground(resource);
    btn.setId(id);
    if (image != 0) {
      btn.setImageResource(image);
    }
    if (themeColorId != 0) {
      btn.setThemeColorId(themeColorId);
    }
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(btn);
    }
    if (listener != null) {
      btn.setOnClickListener(listener);
    }
    btn.setOnLongClickListener(this);
    btn.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
    return btn;
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getTag() != null && v.getTag() instanceof String) {
      String text = (String) v.getTag();
      if (!StringUtils.isEmpty(text)) {
        Toast toast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.RIGHT | Gravity.TOP, v.getRight(), HeaderView.getSize(true) - Screen.dp(8f));
        toast.show();
        return true;
      }
    }
    return false;
  }

  /*private void prepareMenuProgress () {
    if (menuProgress == null) {
      menuProgress = new SpinnerView(getContext());
      menuProgress.setImageResource(R.drawable.spinner_20_outer);
      menuProgress.start();
      menuProgress.setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
    } else {
      menuProgress.setVisibility(View.VISIBLE);
    }
  }*/

  /*public void showMenuProgress () {
    if (!menuProgressShown) {
      menuProgressShown = true;
      prepareMenuProgress();
      Views.setChildrenVisibility(menu, View.GONE);
      menu.setVisibility(View.VISIBLE);
      menu.addView(menuProgress);
    }
  }*/

  /*public void hideMenuProgress () {
    if (menuProgressShown) {
      menuProgressShown = false;
      menuProgress.setVisibility(View.GONE);
      menu.removeView(menuProgress);
      ViewController c = stack.getCurrent();
      menu.setVisibility(c != null && ((!c.inSelectMode() && c.getMenuId() != 0) || (c.inSelectMode() && c.getSelectMenuId() != 0)) ? View.VISIBLE : View.GONE);
      Views.setChildrenVisibility(menu, View.VISIBLE);
    }
  }*/

  // Buttons

  public HeaderButton addDoneButton (LinearLayout menu, @NonNull ViewController<?> themeProvider) {
    return addDoneButton(menu, themeProvider, themeProvider.getHeaderIconColorId());
  }

  public HeaderButton addDoneButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_done, R.drawable.baseline_check_24, colorId, themeProvider, Screen.dp(56f), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addReplyButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_reply, R.drawable.baseline_reply_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerSelector(), this).setThemeColorId(colorId), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addEditButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_edit, R.drawable.baseline_edit_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addCopyButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_copy, R.drawable.baseline_content_copy_24, colorId, themeProvider, Screen.dp(50f), ThemeDeprecated.headerSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addViewButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_view, R.drawable.baseline_visibility_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerLightSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addRetryButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_retry, R.drawable.baseline_repeat_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addDeleteButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_delete, R.drawable.baseline_delete_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addForwardButton (LinearLayout menu, @NonNull ViewController<?> themeProvider) {
    return addForwardButton(menu, themeProvider, themeProvider.getHeaderIconColorId());
  }

  public HeaderButton addForwardButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_forward, R.drawable.baseline_forward_24, colorId, themeProvider, Screen.dp(52f), ThemeDeprecated.headerSelector(), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addMoreButton (LinearLayout menu, @NonNull ViewController<?> themeProvider) {
    return addMoreButton(menu, themeProvider, themeProvider.getHeaderIconColorId());
  }

  public HeaderButton addMoreButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_more, R.drawable.baseline_more_vert_24, colorId, themeProvider, Screen.dp(49f), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addSearchButton (LinearLayout menu, @NonNull ViewController<?> themeProvider) {
    return addSearchButton(menu, themeProvider, themeProvider.getHeaderIconColorId());
  }

  public HeaderButton addSearchButton (LinearLayout menu, @Nullable ViewController<?> themeProvider, @ThemeColorId int colorId) {
    HeaderButton button;
    menu.addView(button = genButton(R.id.menu_btn_search, R.drawable.baseline_search_24, colorId, themeProvider, Screen.dp(49f), this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public LockHeaderButton addLockButton (LinearLayout menu) {
    LockHeaderButton button;
    button = new LockHeaderButton(getContext());
    menu.addView(button);
    return button;
  }

  public StopwatchHeaderButton addStopwatchButton (LinearLayout menu, @Nullable ViewController<?> themeProvider) {
    StopwatchHeaderButton button;
    button = new StopwatchHeaderButton(getContext());
    button.setOnClickListener(this);
    if (themeProvider != null) {
      button.setThemeColorId(themeProvider.getHeaderIconColorId());
      themeProvider.addThemeInvalidateListener(button);
    }
    menu.addView(button);
    return button;
  }

  // Buttons core

  public HeaderButton addButton (LinearLayout menu, @IdRes int id, @DrawableRes int drawableRes, @ThemeColorId int themeColorId, @Nullable ViewController<?> themeProvider, int width) {
    HeaderButton button;
    menu.addView(button = genButton(id, drawableRes, themeColorId, themeProvider, width, this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public HeaderButton addButton (LinearLayout menu, @IdRes int id, @ThemeColorId int themeColorId, @Nullable ViewController<?> themeProvider, @DrawableRes int drawableRes, int width, int resource) {
    HeaderButton button;
    menu.addView(button = genButton(id, drawableRes, themeColorId, themeProvider, width, resource, this), Lang.rtl() ? 0 : -1);
    return button;
  }

  public void addButton (LinearLayout menu, View button) {
    menu.addView(button, Lang.rtl() ? 0 : -1);
  }

  public ClearButton addClearButton (LinearLayout menu, ViewController<?> c) {
    return addClearButton(menu, c.getHeaderIconColorId(), c.getBackButtonResource());
  }

  public ClearButton addClearButton (LinearLayout menu, @ThemeColorId int colorId, int background) {
    ClearButton button;
    button = new ClearButton(getContext());
    button.setId(R.id.menu_btn_clear);
    button.setColorId(colorId);
    button.setButtonBackground(background);
    button.setOnClickListener(this);
    menu.addView(button, Lang.rtl() ? 0 : -1);
    return button;
  }

  private static final int ACTIVE_HINT_COLOR = 0xffffffff;
  private static final int INACTIVE_HINT_COLOR = 0xffd2eafc;

  // Buttons wrap

  private LinearLayout genButtonsWrap (Context context) {
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setVisibility(View.GONE);
    FrameLayout.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Size.getHeaderPortraitSize(), Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
    params.topMargin = getCurrentHeaderOffset();
    l.setLayoutParams(params);
    return l;
  }

  // Text

  public TextView genTextTitle (Context context) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.topMargin = Screen.dp(15f) + getCurrentHeaderOffset();
    TextView text = new NoScrollTextView(context);
    text.setTag(this);
    text.setMovementMethod(LinkMovementMethod.getInstance());
    text.setHighlightColor(Theme.textLinkHighlightColor());
    text.setTypeface(Fonts.getRobotoMedium());
    text.setGravity(Gravity.LEFT);
    text.setSingleLine();
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f);
    text.setTextColor(0xffffffff);
    text.setLayoutParams(params);
    return text;
  }

  // Toggler

  private View.OnClickListener onToggleClick;

  public ToggleHeaderView genToggleTitle (Context context, ViewController<?> controller) {
    if (onToggleClick == null) {
      onToggleClick = v -> {
        ViewController<?> c = stack.getCurrent();
        if (c instanceof ToggleDelegate) {
          showToggleOptions(((ToggleDelegate) c).getToggleSections());
        }
      };
    }
    return genToggleTitle(context, controller, onToggleClick);
  }

  public ToggleHeaderView genToggleTitle (Context context, ViewController<?> controller, View.OnClickListener onClickListener) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(38f), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.topMargin = Screen.dp(15f) + getCurrentHeaderOffset();
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    ToggleHeaderView view = new ToggleHeaderView(context);

    if (controller != null) {
      view.setTextColor(controller.getHeaderTextColor());
      view.setTriangleColor(controller.getHeaderIconColor());
    }

    view.setLayoutParams(params);
    view.setOnClickListener(onClickListener);
    return view;
  }

  private View.OnClickListener onToggleItemClick;

  private void showToggleOptions (String[] options) {
    if (moreWrap == null) {
      moreWrap = new MenuMoreWrap(getContext());
      moreWrap.init(null, null);
    }
    if (onToggleItemClick == null) {
      onToggleItemClick = v -> {
        ((PopupLayout) v.getParent().getParent()).hideWindow(true);
        ViewController<?> c = stack.getCurrent();
        if (c instanceof ToggleDelegate) {
          ((ToggleDelegate) c).onToggle(v.getId());
        }
      };
    }
    moreWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_HEADER);
    moreWrap.setTranslationY(getTranslationY() + getCurrentHeaderOffset());
    showMore(null, options, null, onToggleItemClick, false, getThemeListeners());
  }

  private ThemeListenerList themeListenerList;
  private ThemeListenerList getThemeListeners () {
    if (themeListenerList == null)
      themeListenerList = new ThemeListenerList();
    return themeListenerList;
  }

  // Counter

  public static CounterHeaderView genCounterHeader (Context context, @ThemeColorId int colorId) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(53f), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    CounterHeaderView view = new CounterHeaderView(context);
    view.initDefault(colorId);
    view.setLayoutParams(params);
    return view;
  }

  public HeaderEditText genSearchHeader (boolean isLight, ViewController<?> themeProvider) {
    return genSearchHeader(this, isLight, themeProvider);
  }

  public static HeaderEditText genSearchHeader (ViewGroup parent, boolean isLight, @Nullable ViewController<?> themeProvider) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(53f),  Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    HeaderEditText view = HeaderEditText.createStyled(parent, isLight);
    view.setTextColor(Theme.getColor(R.id.theme_color_headerText));
    if (themeProvider != null)
      themeProvider.addThemeHighlightColorListener(view, R.id.theme_color_textSelectionHighlight);
    view.checkRtl();
    if (themeProvider != null)
      themeProvider.addThemeTextColorListener(view, R.id.theme_color_headerText);
    view.setHintTextColor(ColorUtils.alphaColor(Theme.HEADER_TEXT_DECENT_ALPHA, Theme.headerTextColor()));
    if (themeProvider != null)
      themeProvider.addThemeHintTextColorListener(view, R.id.theme_color_headerText).setAlpha(Theme.HEADER_TEXT_DECENT_ALPHA);
    view.setLayoutParams(params);
    return view;
  }

  public HeaderEditText genGreySearchHeader (ViewController<?> themeProvider) {
    return genGreySearchHeader(this, themeProvider);
  }

  public static HeaderEditText genGreySearchHeader (ViewGroup parent, ViewController<?> themeProvider) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(53f), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    HeaderEditText view = HeaderEditText.createGreyStyled(parent);
    view.setTextColor(Theme.textAccentColor());
    themeProvider.addThemeHighlightColorListener(view, R.id.theme_color_textSelectionHighlight);
    view.checkRtl();
    themeProvider.addThemeTextColorListener(view, R.id.theme_color_text);
    view.setHintTextColor(Theme.textDecentColor());
    themeProvider.addThemeHintTextColorListener(view, R.id.theme_color_textLight);
    view.setLayoutParams(params);
    return view;
  }

  // More

  private MenuMoreWrap moreWrap;
  private View.OnClickListener onMoreItemClick;

  public void showMore (int[] ids, String[] titles, int[] icons, int buttonIndex, boolean isLayered, @Nullable ViewController<?> themeProvider) {
    if (ids.length == 0) {
      return;
    }
    ThemeListenerList themeListenerList = navigation != null ? navigation.getThemeListeners() : themeProvider != null ? themeProvider.getThemeListeners() : null;
    if (moreWrap == null) {
      moreWrap = new MenuMoreWrap(getContext());
      moreWrap.init(themeListenerList, null);
    }
    if (onMoreItemClick == null) {
      onMoreItemClick = v -> {
        ((PopupLayout) v.getParent().getParent()).hideWindow(true);
        ViewController<?> c = stack.getCurrent();
        if (c instanceof MoreDelegate) {
          ((MoreDelegate) c).onMoreItemPressed(v.getId());
        }
      };
    }
    moreWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_RIGHT);
    moreWrap.setRightNumber(buttonIndex);
    moreWrap.setTranslationY(getTranslationY() + getCurrentHeaderOffset());
    showMore(ids, titles, icons, onMoreItemClick, isLayered, themeListenerList);
  }

  private void showMore (int[] ids, String[] titles, int[] icons, View.OnClickListener onItemClick, boolean isLayered, @Nullable ThemeListenerList themeListeners) {
    int size = ids == null ? titles.length : ids.length;
    int i, j;
    int childCount = moreWrap.getChildCount();
    for (i = 0, j = 0; j < childCount; j++) {
      if (i < size) {
        moreWrap.updateItem(i, ids == null ? i : ids[i], titles[i], icons != null ? icons[i] : 0, onItemClick, themeListeners);
        i++;
      } else {
        for (j = childCount - 1; j >= size; j--)
          moreWrap.removeViewAt(j);
        break;
      }
    }
    if (size > moreWrap.getChildCount()) {
      for (; i < size; i++) {
        moreWrap.addItem(ids == null ? i : ids[i], titles[i], icons != null ? icons[i] : 0, null, onItemClick);
      }
    }
    PopupLayout popupLayout = new PopupLayout(getContext());
    popupLayout.init(true);
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(true);
    /*if (!isLayered) {
      popupLayout.setNeedRootInsets();
    }*/
    popupLayout.showMoreView(moreWrap);
  }

  // Filling getter

  public HeaderFilling getFilling () {
    return filling;
  }

  public float getCurrentHeight () {
    return height;
  }

  // Overlay color

  private static class OverlayHeaderView extends View {
    private Paint paint;
    private HeaderFilling filling;

    public OverlayHeaderView (Context context) {
      super(context);
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      paint.setColor(0x00000000);
    }

    public void setFilling (HeaderFilling filling) {
      this.filling = filling;
    }

    private int color;

    public void setColor (int color) {
      if (this.color != color) {
        this.color = color;
        paint.setColor(color);
        invalidate();
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (paint.getAlpha() == 0) {
        return false;
      }
      if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < filling.getBottom()) {
        ViewController<?> c = UI.getCurrentStackItem();
        if (c != null) {
          c.dismissIntercept();
        }
        return true;
      }
      return false;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (color != 0) {
        c.drawRect(0f, 0f, getMeasuredWidth(), filling.getBottom(), paint);
      }
    }
  }

  // private OverlayHeaderView overlayView;
  private int overlayColor;

  public void setOverlayColor (int color) {
    if (this.overlayColor != color) {
      this.overlayColor = color;
      setWillNotDraw(Color.alpha(color) <= 0);
      invalidate();
    }
  }

  @Override
  public void draw (Canvas c) {
    super.draw(c);
    if (Color.alpha(overlayColor) > 0) {
      c.drawRect(0, 0, getMeasuredWidth(), filling.getBottom(), Paints.fillingPaint(overlayColor));
    }
  }

  // Internal stuff

  private static void updateTextMargins (View textTitle, ViewController<?> item, int menuWidth, int currentHeaderOffset) {
    if (textTitle == null || item == null)
      return;
    boolean updated;
    int top = Screen.dp(15f) + currentHeaderOffset;
    if (item.getBackButton() != BackHeaderButton.TYPE_NONE) {
      if (Lang.rtl()) {
        updated = Views.setMargins((FrameLayout.LayoutParams) textTitle.getLayoutParams(), menuWidth, top, Screen.dp(68f), 0);
      } else {
        updated = Views.setMargins((FrameLayout.LayoutParams) textTitle.getLayoutParams(), Screen.dp(68f), top, menuWidth, 0);
      }
    } else {
      if (Lang.rtl()) {
        updated = Views.setMargins((FrameLayout.LayoutParams) textTitle.getLayoutParams(), menuWidth, top, Screen.dp(18f), 0);
      } else {
        updated = Views.setMargins((FrameLayout.LayoutParams) textTitle.getLayoutParams(), Screen.dp(18f), top, menuWidth, 0);
      }
    }
    if (updated)
      Views.updateLayoutParams(textTitle);
  }

  private void dispatchOffset (View view) {
    int headerOffset = getCurrentHeaderOffset();
    if (view instanceof OffsetChangeListener) {
      ((OffsetChangeListener) view).onHeaderOffsetChanged(this, headerOffset);
    } else if (view instanceof ToggleHeaderView) {
      Views.setTopMargin(view, Screen.dp(15f) + headerOffset);
    } else {
      Views.setTopMargin(view, headerOffset);
    }
  }

  private void genTitle (ViewController<?> item) {
    View newTitle = item.getCustomHeaderCell();

    if (newTitle == null) {
      updateTextMargins(textTitle, item, 0, getCurrentHeaderOffset());
      textTitle.setId(item.getId());
      Views.setMediumText(textTitle, item.getName());
      newTitle = textTitle;
    } else {
      dispatchOffset(newTitle);
    }

    if (title != null && title != newTitle) {
      removeView(title);
    }

    title = newTitle;
    if (title.getParent() == null) {
      addView(title, BACK_BUTTON_ON_TOP ? 0 : 1);
    }
  }

  private boolean menuPreviewUsed;

  @SuppressWarnings ("ResourceType")
  private void genPreview (ViewController<?> left, ViewController<?> right, boolean forward) {
    if (forward) {
      previewItem = right;
      baseItem = left;
    } else {
      previewItem = left;
      baseItem = right;
    }

    translateForward = forward;
    if (right == null) {
      preview = forward ? left.getTransformHeaderView(this) : left.getCustomHeaderCell();
    } else {
      preview = previewItem == null ? null : previewItem.inTransformMode() ? previewItem.getTransformHeaderView(this) : previewItem.getCustomHeaderCell();
    }
    if (right == null && left.disableHeaderTransformation()) {
      translationMode = MODE_NONE;
      shareHeader = false;
      preview = title;
    } else if (right == null || right.forceFadeMode()) {
      shareHeader = false;
      translationMode = MODE_FADE;
    } else {
      shareHeader = preview != null && right.shareCustomHeaderView() && !right.inTransformMode() && !left.inTransformMode();
      translationMode = shareHeader || !right.useHeaderTranslation() ? MODE_NONE : translationMode;
    }

    boolean previewIsText;

    if (previewIsText = preview == null) {
      if (textPreview == null) {
        textPreview = genTextTitle(getContext());
      }
      assert previewItem != null;
      textPreview.setId(previewItem.getId());
      Views.setMediumText(textPreview, previewItem.getName());
      preview = textPreview;
    } else {
      dispatchOffset(preview);
    }

    if (preview != title) {
      if (preview.getParent() != null) {
        removeView(preview);
      }
      if (forward) {
        addView(preview, BACK_BUTTON_ON_TOP ? 0 : 1);
      } else {
        addView(preview, BACK_BUTTON_ON_TOP ? 1 : 2);
      }
    }

    if (shareHeader) {
      title.setVisibility(View.GONE);
    }

    int menuWidth = 0;

    int leftMenuId = getMenuId(left, right != null);
    int rightMenuId = right == null ? getTransformMenuId(left) : getMenuId(right, true);
    int previewMenuId = forward ? rightMenuId : leftMenuId;

    if (previewMenuId != 0 && leftMenuId != rightMenuId) {
      menuPreviewUsed = true;

      if (menuPreview == null) {
        menuPreview = genButtonsWrap(getContext());
      }

      boolean allowReuse = right == null && forward ? baseItem != null && baseItem.allowMenuReuse() : previewItem != null && previewItem.allowMenuReuse();

      if (menuPreview.getId() != previewMenuId || !allowReuse) {
        menuPreview.removeAllViews();
        menuPreview.setId(previewMenuId);
        Menu menu;
        if (right == null && forward) {
          menu = (Menu) baseItem;
        } else {
          menu = (Menu) previewItem;
        }
        menu.fillMenuItems(previewMenuId, this, menuPreview);
      }

      if (previewIsText) {
        for (int i = 0; i < menuPreview.getChildCount(); i++) {
          View v = menuPreview.getChildAt(i);
          if (v != null) {
            menuWidth += v.getLayoutParams().width;
          }
        }
      }

      if (menuPreview.getParent() != null) {
        removeView(menuPreview);
      }

      if (forward) {
        addView(menuPreview, -1);
      } else {
        addView(menuPreview, 3);
      }
    } else {
      menuPreviewUsed = false;
    }

    if (previewIsText) {
      updateTextMargins(preview, previewItem, menuWidth, getCurrentHeaderOffset());
    }
  }

  public void updateTextTitle (int id, CharSequence newTitle) {
    if (textTitle != null && textTitle.getId() == id) {
      Views.setMediumText(textTitle, newTitle);
    }
    if (textPreview != null && textPreview.getId() == id) {
      Views.setMediumText(textPreview, newTitle);
    }
  }

  public void updateTextTitleColor (int id, int newColor) {
    if (textTitle != null && textTitle.getId() == id) {
      textTitle.setTextColor(newColor);
    }
    if (textPreview != null && textPreview.getId() == id) {
      textPreview.setTextColor(newColor);
    }
  }

  // Animation

  private static final int MODE_NONE = 0; // Values must be the same as in NavigationController
  private static final int MODE_HORIZONTAL = 1;
  private static final int MODE_VERTICAL = 2;
  private static final int MODE_FADE = 3;

  private static final int BACK_SWITCH_NONE = 0;
  private static final int BACK_SWITCH_NORMAL = 1;
  private static final int BACK_SWITCH_CLOSE = 2;

  private int translationMode;
  private boolean shareHeader;
  private boolean translateForward;
  private boolean useBackFade;
  private float fromBackButtonFactor, toBackButtonFactor;
  private boolean backFade;

  private boolean useMenuSwitch;
  private boolean useMenu, useMenuPreview;

  private boolean useHeightSwitch;
  // private boolean usePreviewHeightSwitch, useBaseHeightSwitch;
  private float previewHeightFactor, baseHeightFactor;
  private float fromHeight, heightDiff;

  private int useFloatSwitch; // 0 - none, 1 - height only, 2 - height and factor
  private boolean baseHasFloat;
  private FloatingButton floatSwitch;

  private boolean useColorSwitch;
  private ColorChanger headerChanger;

  private boolean useTextSwitch;
  private int baseTextSwitch, previewTextSwitch;
  private ColorChanger textChanger;

  private boolean useBackColorSwitch;
  private ColorChanger backChanger;

  private Window window;
  private boolean useBarSwitch;
  private ColorChanger barChanger;

  private boolean useShadowSwitch, shadowSwitch;
  private boolean usePlayerSwitch, playerSwitch;
  private float translationFactor;

  public float getTranslation () {
    return translationFactor;
  }

  public void setTranslation (float raptor) {
    this.translationFactor = raptor;

    if (translateForward) {
      raptor = 1f - raptor;
    }

    if (useHeightSwitch) {
      height = fromHeight + heightDiff * raptor;
      float heightFactor = (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();

      filling.layout((int) height, heightFactor);

      if (preview instanceof StretchyHeaderView) {
        ((StretchyHeaderView) preview).setScaleFactor(heightFactor, baseHeightFactor, previewHeightFactor, false);
      }

      if (title instanceof StretchyHeaderView) {
        ((StretchyHeaderView) title).setScaleFactor(heightFactor, previewHeightFactor, baseHeightFactor, false);
      }

      if (useFloatSwitch != 0) {
        floatSwitch.setHeightFactor(heightFactor, baseHasFloat ? 1f - raptor : raptor, useFloatSwitch != 1);
        if (translationMode == MODE_VERTICAL) {
          float changeFactor = translateForward ? getHeightFactor(fromHeight) : getHeightFactor(fromHeight + heightDiff);
          floatSwitch.setTranslationY(floatSwitch.getTranslationY() - (Size.getHeaderPortraitSize() * changeFactor) * (1f - heightFactor));
        }
      }
    } else if (useFloatSwitch == 1) {
      floatSwitch.setHeightFactor(getHeightFactor(fromHeight), baseHasFloat ? 1f - raptor : raptor, false);
    }

    switch (translationMode) {
      case MODE_HORIZONTAL: {
        if (Lang.rtl()) {
          if (translateForward) {
            title.setTranslationX(currentX * raptor);
            preview.setTranslationX(-currentX * (1f - raptor));
          } else {
            title.setTranslationX(-currentX * raptor);
            preview.setTranslationX((currentX * (1f - raptor)));
          }
        } else {
          if (translateForward) {
            title.setTranslationX(-currentX * raptor);
            preview.setTranslationX(currentX * (1f - raptor));
          } else {
            title.setTranslationX(currentX * raptor);
            preview.setTranslationX(-currentX * (1f - raptor));
          }
        }

        title.setAlpha(1f - raptor);
        preview.setAlpha(raptor);

        break;
      }
      case MODE_VERTICAL: {
        if (translateForward) {
          title.setTranslationY(-(Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * raptor);
          preview.setTranslationY(currentY * (1f - raptor));
          if (previewItem != null) {
            previewItem.applyCustomHeaderAnimations(raptor);
          }
        } else {
          title.setTranslationY(currentY * raptor);
          preview.setTranslationY(-((Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * (1f - raptor)));
          if (baseItem != null) {
            baseItem.applyCustomHeaderAnimations(1f - raptor);
          }
        }

        title.setAlpha(1f - raptor);
        preview.setAlpha(raptor);

        break;
      }
      case MODE_FADE: {
        title.setAlpha(1f - raptor);
        preview.setAlpha(raptor);

        break;
      }
    }

    if (useMenuSwitch) {
      if (useMenu) {
        menu.setAlpha(1f - raptor);
        if (translationMode == MODE_VERTICAL) {
          //noinspection ResourceType
          menu.setTranslationY(translateForward ? -(Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * raptor : currentY * raptor);
        }
      }
      if (useMenuPreview) {
        menuPreview.setAlpha(raptor);
        if (translationMode == MODE_VERTICAL) {
          //noinspection ResourceType
          menuPreview.setTranslationY(translateForward ? currentY * (1f - raptor) : -((Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * (1f - raptor)));
        }
      }
    }

    if (useBackFade) {
      if (backFade) {
        backButton.setAlpha(raptor);
        if (translationMode == MODE_VERTICAL) {
          backButton.setTranslationY(-(Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * (1f - raptor));
        }
        backButton.setTranslationX(preview.getTranslationX());
      } else {
        backButton.setAlpha(1f - raptor);
        if (translationMode == MODE_VERTICAL) {
          backButton.setTranslationY((Size.getHeaderPortraitSize() + HeaderView.getTopOffset()) * raptor);
        }
        backButton.setTranslationX(title.getTranslationX());
      }
    } else {
      if (fromBackButtonFactor != toBackButtonFactor) {
        backButton.setFactor(fromBackButtonFactor + (toBackButtonFactor - fromBackButtonFactor) * raptor);
      }
    }

    if (useFloatSwitch == 2) {
      floatSwitch.setFactor(raptor);
    }

    if (useColorSwitch) {
      filling.setColor(headerChanger.getColor(raptor));
      if (transformMode == TRANSFORM_MODE_SEARCH) {
        if (translateForward) {
          filling.setRadiusFactor(raptor, headerChanger.getColor(1f));
        } else {
          filling.setRadiusFactor(1f - raptor, headerChanger.getColor(0f));
        }
      }
    }

    if (useTextSwitch) {
      int c = textChanger.getColor(raptor);
      switch (baseTextSwitch) {
        case TEXT_SWITCH_NONE: {
          break;
        }
        case TEXT_SWITCH_NORMAL: {
          ((TextView) title).setTextColor(c);
          break;
        }
        case TEXT_SWITCH_TOGGLE: {
          ((ToggleHeaderView) title).setTextColor(c);
          break;
        }
        case TEXT_SWITCH_CUSTOM: {
          ((TextChangeDelegate) title).setTextColor(c);
          break;
        }
      }
      switch (previewTextSwitch) {
        case TEXT_SWITCH_NONE: {
          break;
        }
        case TEXT_SWITCH_NORMAL: {
          ((TextView) preview).setTextColor(c);
          break;
        }
        case TEXT_SWITCH_TOGGLE: {
          ((ToggleHeaderView) preview).setTextColor(c);
          break;
        }
        case TEXT_SWITCH_CUSTOM: {
          ((TextChangeDelegate) preview).setTextColor(c);
          break;
        }
      }
    }

    if (useBackColorSwitch) {
      int c = backChanger.getColor(raptor);
      backButton.setColor(c);
      if (useTextSwitch) {
        if (baseTextSwitch == TEXT_SWITCH_TOGGLE) {
          ((ToggleHeaderView) title).setTriangleColor(c);
        }
        if (previewTextSwitch == TEXT_SWITCH_TOGGLE) {
          ((ToggleHeaderView) preview).setTriangleColor(c);
        }
      }
    }

    if (useShadowSwitch) {
      filling.setShadowAlpha(shadowSwitch ? raptor : 1f - raptor);
    }
    if (usePlayerSwitch) {
      filling.setPlayerAllowance(playerSwitch ? raptor : 1f - raptor);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (useBarSwitch) {
        window.setStatusBarColor(barChanger.getColor(raptor));
      }
    }

    if (useHeightSwitch || useColorSwitch || useShadowSwitch || useBackColorSwitch) {
      invalidate();
    }

    if (useTextSwitch) {
      if (baseTextSwitch == TEXT_SWITCH_TOGGLE) {
        title.invalidate();
      }
      if (previewTextSwitch == TEXT_SWITCH_TOGGLE) {
        preview.invalidate();
      }
    }
  }

  void applyPreview (ViewController<?> current) {
    this.previewOpened = false;

    if (shareHeader) {
      title.setVisibility(View.VISIBLE);
    }

    View tempView;
    if (preview != title) {
      tempView = title;
      title = preview;
      preview = tempView;

      removeView(preview);

      tempView = textTitle;
      textTitle = textPreview;
      textPreview = (TextView) tempView;
    }

    if (current != null) {
      int type = getBackButton(current, true);
      int bg = getBackButtonResource(current, true);
      if (type == BackHeaderButton.TYPE_NONE) {
        backButton.setVisibility(View.GONE);
      } else {
        backButton.setButtonFactor(type);
        backButton.invalidate();
      }
      backButton.setButtonBackground(bg);
      filling.forceBigOutline(current.useBigHeaderButtons());
    }

    if (menuPreviewUsed) {
      tempView = menu;
      menu = menuPreview;
      menuPreview = (LinearLayout) tempView;

      removeView(menuPreview);
      menuPreviewUsed = false;
    }

    int menuId = getMenuId(current, true);
    if (current == null || menuId == 0) {
      menu.setVisibility(View.GONE);
    }

    previewItem = null;
    baseItem = null;
  }

  // Transformation

  protected static final int TRANSFORM_MODE_NONE = 0;
  protected static final int TRANSFORM_MODE_SELECT = 1;
  protected static final int TRANSFORM_MODE_SEARCH = 2;
  protected static final int TRANSFORM_MODE_CUSTOM = 3;

  private boolean isAnimating;

  public boolean isAnimating () {
    return isAnimating;
  }

  public final void onBackTouchDown (MotionEvent e) {
    ViewController<?> c = null;
    if (navigation != null) {
      c = navigation.getCurrentStackItem();
    } else if (isOwningStack && stack != null && !stack.isEmpty()) {
      c = stack.getCurrent();
    }
    if (c != null && getBackButton(c, true) == BackHeaderButton.TYPE_BACK && (c.getKeyboardState() || c.needPreventiveKeyboardHide())) {
      c.hideSoftwareKeyboard();
    }
  }

  private int transformMode;

  private void transform (final ViewController<?> controller, final int mode, int arg, final boolean open, boolean animated, final Runnable after) {
    this.transformMode = mode;

    openPreview(controller, null, open, MODE_FADE, translationFactor);

    if (open) {
      switch (mode) {
        case TRANSFORM_MODE_SELECT: {
          controller.initSelectedCount(arg);
          controller.onEnterSelectMode();
          break;
        }
        case TRANSFORM_MODE_SEARCH: {
          controller.onEnterSearchMode();
          break;
        }
        case TRANSFORM_MODE_CUSTOM: {
          break;
        }
      }
    } else {
      switch (mode) {
        case TRANSFORM_MODE_SEARCH: {
          controller.onLeaveSearchMode();
          controller.updateSearchMode(false);
          break;
        }
        case TRANSFORM_MODE_CUSTOM: {
          controller.updateCustomMode(false);
          break;
        }
      }
    }

    final Animator.AnimatorListener listener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator ignored) {
        if (!open) {
          switch (mode) {
            case TRANSFORM_MODE_SELECT: {
              controller.leaveSelectMode();
              controller.onLeaveSelectMode();
              break;
            }
            case TRANSFORM_MODE_SEARCH: {
              controller.leaveSearchMode();
              break;
            }
            case TRANSFORM_MODE_CUSTOM: {
              controller.leaveCustomMode();
              break;
            }
          }
        } else {
          switch (mode) {
            case TRANSFORM_MODE_SEARCH: {
              controller.updateSearchMode(true);
              break;
            }
            case TRANSFORM_MODE_CUSTOM: {
              controller.updateCustomMode(true);
              break;
            }
          }
        }
        applyPreview(controller);
        filling.resetRadius();
        transformMode = TRANSFORM_MODE_NONE;
        isAnimating = false;
        if (after != null) {
          after.run();
        }
      }
    };

    if (!animated) {
      if (open) {
        setTranslation(0f);
        switch (mode) {
          case TRANSFORM_MODE_SEARCH: {
            controller.setSearchTransformFactor(1f, true);
            break;
          }
        }
      } else {
        setTranslation(1f);
        switch (mode) {
          case TRANSFORM_MODE_SEARCH: {
            controller.setSearchTransformFactor(0f, false);
            break;
          }
        }
      }
      listener.onAnimationEnd(null);;
      return;
    }

    if (controller.launchCustomHeaderTransformAnimator(open, mode, listener)) {
      return;
    }

    ValueAnimator modeAnimator;

    final float startFactor = getTranslation();
    modeAnimator = AnimatorUtils.simpleValueAnimator(); //this, "translation", open ? 0f : 1f);
    if (open) {
      modeAnimator.addUpdateListener(animation -> {
        float factor = startFactor - startFactor * AnimatorUtils.getFraction(animation);
        setTranslation(factor);
        switch (mode) {
          case TRANSFORM_MODE_SEARCH: {
            controller.setSearchTransformFactor(1f - factor, true);
            break;
          }
        }
      });
    } else {
      final float diffFactor = 1f - startFactor;
      modeAnimator.addUpdateListener(animation -> {
        float factor = startFactor + diffFactor * AnimatorUtils.getFraction(animation);
        setTranslation(factor);
        switch (mode) {
          case TRANSFORM_MODE_SEARCH: {
            controller.setSearchTransformFactor(1f - factor, false);
            break;
          }
        }
      });
    }
    modeAnimator.setInterpolator(controller.getSearchTransformInterpolator());
    modeAnimator.setDuration(controller.getSearchTransformDuration());
    modeAnimator.addListener(listener);
    controller.startHeaderTransformAnimator(modeAnimator, mode, open);
  }

  public int getCurrentTransformMode () {
    ViewController<?> c = stack.getCurrent();
    return c == null ? TRANSFORM_MODE_NONE : c.inSearchMode() ? TRANSFORM_MODE_SEARCH : c.inSelectMode() ? TRANSFORM_MODE_SELECT : TRANSFORM_MODE_NONE;
  }

  // Select mode

  public void openSelectMode (int startCount, boolean animated) {
    final ViewController<?> controller = stack.getCurrent();

    if (isAnimating || controller == null || controller.inSelectMode() || controller.inSearchMode()) {
      return;
    }

    isAnimating = true;
    controller.enterSelectMode();
    translationFactor = 1f;

    transform(controller, TRANSFORM_MODE_SELECT, startCount, true, animated, null);
  }

  public void closeSelectMode () { // Used by controllers
    closeSelectMode(false, true);
  }

  public void finishSelectMode () {
    closeSelectMode(true, true);
  }

  void closeSelectMode (boolean performCallback, boolean animated) {
    final ViewController<?> controller = stack.getCurrent();

    if (isAnimating || controller == null || !controller.inSelectMode()) {
      return;
    }

    isAnimating = true;
    translationFactor = 0f;

    if (performCallback && controller instanceof SelectDelegate) {
      ((SelectDelegate) controller).finishSelectMode(-1);
    }

    transform(controller, TRANSFORM_MODE_SELECT, 0, false, animated, null);
  }

  public boolean inSelectMode () {
    ViewController<?> c = stack.getCurrent();
    return c != null && c.inSelectMode();
  }

  // Custom mode

  public void openCustomMode () {
    final ViewController<?> c = stack.getCurrent();

    if (isAnimating || c == null || c.inTransformMode()) {
      return;
    }

    isAnimating = true;
    c.enterCustomMode();
    translationFactor = 1f;

    transform(c, TRANSFORM_MODE_CUSTOM, 0, true, true, null);
  }

  public void closeCustomMode () {
    final ViewController<?> controller = stack.getCurrent();

    if (isAnimating || controller == null || !controller.inCustomMode()) {
      return;
    }

    isAnimating = true;
    translationFactor = 0f;

    transform(controller, TRANSFORM_MODE_CUSTOM, 0, false, true, null);
  }

  // Search mode

  public void openSearchMode () {
    final ViewController<?> controller = stack.getCurrent();

    if (isAnimating || controller == null || controller.inTransformMode() || Color.alpha(overlayColor) > 0) {
      return;
    }

    isAnimating = true;
    controller.enterSearchMode();
    translationFactor = 1f;

    transform(controller, TRANSFORM_MODE_SEARCH, 0, true, true, null);
  }

  public void closeSearchMode (boolean animated, Runnable after) {
    final ViewController<?> controller = stack.getCurrent();

    if (isAnimating || controller == null || !controller.inSearchMode()) {
      return;
    }

    isAnimating = true;
    translationFactor = 0f;

    transform(controller, TRANSFORM_MODE_SEARCH, 0, false, animated, after);
  }

  public boolean inSearchMode () {
    ViewController<?> c = stack.getCurrent();
    return c != null && c.inSearchMode();
  }

  public boolean inCustomMode () {
    ViewController<?> c = stack.getCurrent();
    return c != null && c.inCustomMode();
  }

  // back button color changer

  private float switchFactor;
  private ValueAnimator switchAnimator;

  public void switchBackColor (ViewController<?> current) {
    if (current == null) {
      return;
    }

    boolean isAnimating = navigation != null && navigation.isAnimating();

    if (isAnimating) {
      if ((translateForward && current != previewItem) || (!translateForward && current != baseItem)) {
        return;
      }
    } else {
      if (stack.getCurrent() != current) {
        return;
      }
    }

    if (switchAnimator != null) {
      switchAnimator.cancel();
      switchAnimator = null;
    }

    int oldColor = backButton.getColor();
    int newColor = current.getHeaderIconColor();

    if (backChanger == null) {
      backChanger = new ColorChanger(oldColor, newColor);
    } else {
      backChanger.setFromTo(oldColor, newColor);
    }

    if (isAnimating) {
      useBackColorSwitch = backChanger.getFrom() != backChanger.getTo();
      return;
    }

    switchFactor = 0f;
    final float startFactor = getBackFactor();
    final float diffFactor = 1f - startFactor;
    switchAnimator = AnimatorUtils.simpleValueAnimator();
    switchAnimator.addUpdateListener(animation -> setBackFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    switchAnimator.setDuration(180);
    switchAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    switchAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        switchAnimator = null;
      }
    });
    switchAnimator.start();
  }

  public void setBackFactor (float factor) {
    if (this.switchFactor != factor) {
      this.switchFactor = factor;
      backButton.setColor(backChanger.getColor(factor));
    }
  }

  public float getBackFactor () {
    return switchFactor;
  }

  // utils

  public void resetState () {
    // TODO force exit search/etc modes
  }

  public void setTitle (ViewController<?> item) {
    if (isOwningStack) {
      stack.resetSilently(item);
    }
    genTitle(item);
    if (item.getMenuId() != menu.getId()) {
      menu.removeAllViews();
      menu.setId(item.getMenuId());
      if (item.getMenuId() != 0) {
        ((Menu) item).fillMenuItems(item.getMenuId(), this, menu);
        menu.setVisibility(View.VISIBLE);
        int width = 0;
        for (int i = 0; i < menu.getChildCount(); i++) {
          width += menu.getChildAt(i).getLayoutParams().width;
        }
        if (title == textTitle) {
          updateTextMargins(title, item, width, getCurrentHeaderOffset());
        }
      } else {
        menu.setVisibility(View.GONE);
        if (title == textTitle) {
          updateTextMargins(title, item, 0, getCurrentHeaderOffset());
        }
      }
    }

    View v = item.getCustomHeaderCell();
    if (v != null && v instanceof StretchyHeaderView) {
      int height = item.getHeaderHeight();
      float factor = getHeightFactor(height);
      if (factor > 0f) {
        ((StretchyHeaderView) v).setScaleFactor(factor, factor, factor, false);
      }
    }

    // title.setPadding(item.getBackButton() != BackHeaderButton.TYPE_NONE ? Size._68 : Size._18, 0, -, 0);

    if (item.getBackButton() != BackHeaderButton.TYPE_NONE) {
      backButton.setButtonFactor(item.getBackButton());
      backButton.setVisibility(View.VISIBLE);
      if (item.getBackButtonResource() != 0) {
        backButton.setBackgroundResource(item.getBackButtonResource());
      }
      backButton.setColor(Theme.getColor(item.getHeaderIconColorId()));
    } else {
      backButton.setVisibility(View.GONE);
    }
    backButton.setColor(item.getHeaderIconColor());

    if (item.getCustomHeaderCell() != null) {
      View headerCell = item.getCustomHeaderCell();
      if (headerCell instanceof ToggleHeaderView) {
        ((ToggleHeaderView) headerCell).setTextColor(item.getHeaderTextColor());
        ((ToggleHeaderView) headerCell).setTriangleColor(item.getHeaderIconColor());
      } else if (headerCell instanceof CounterHeaderView) {
        ((CounterHeaderView) headerCell).setTextColorId(item.getHeaderTextColorId());
      }
    } else {
      textTitle.setTextColor(item.getHeaderTextColor());
    }

    setBackgroundHeight(item.getHeaderHeight());
    if (navigation != null) {
      int floatingButtonId = item.getFloatingButtonId();
      if (floatingButtonId != 0) {
        FloatingButton button = navigation.getFloatingButton();
        button.setIcons(0, floatingButtonId);
        button.initHeightFactor(getHeightFactor(), 0f, 1f, false, false);
      }
    }
    filling.setColor(item.getHeaderColor());
    filling.setShadowAlpha(item.useDropShadow() ? 1f : 0f);
    filling.setPlayerAllowance(item.useDropPlayer() ? 1f : 0f);
    // TODO other options

    if (item.usePopupMode()) {
      title.setTranslationY(-Size.getHeaderPortraitSize());
      filling.collapseFilling(0);
    }

    item.executeAnimationReadyListeners();
  }

  private float currentX, currentY;

  private int getTextSwitchModeForView (View view) {
    if (view instanceof TextView && view.getTag() == this) {
      return TEXT_SWITCH_NORMAL;
    }
    if (view instanceof TextChangeDelegate) {
      return TEXT_SWITCH_CUSTOM;
    }
    if (view instanceof ToggleHeaderView) {
      return TEXT_SWITCH_TOGGLE;
    }
    return TEXT_SWITCH_NONE;
  }

  private void resetAnimationColors () {
    ViewController<?> left, right;
    boolean forward = translateForward;
    if (translateForward) {
      right = previewItem;
      left = baseItem;
    } else {
      left = previewItem;
      right = baseItem;
    }
    boolean previewIsPopup = previewItem != null && previewItem.usePopupMode() && !forward;
    boolean baseIsPopup = baseItem != null && baseItem.usePopupMode() && forward;
    int transformMode = getTransformMode(left);
    boolean inTransformMode = right == null && transformMode != TRANSFORM_MODE_NONE;

    final int baseColor, previewColor;

    if (useColorSwitch) {
      baseColor = inTransformMode && !forward ? getHeaderColor(left, true) : baseIsPopup ? ColorUtils.color(0, Theme.headerColor()) : getHeaderColor(baseItem, !inTransformMode);
      previewColor = inTransformMode && forward ? getHeaderColor(left, true) : previewIsPopup ? ColorUtils.color(0, Theme.headerColor()) : getHeaderColor(previewItem, !inTransformMode);
      headerChanger.setFromTo(baseColor, previewColor);
    } else {
      baseColor = previewColor = 0;
      filling.setColor(getHeaderColor(previewItem, true));
    }

    if (useTextSwitch) {
      int leftHeaderTextColor = inTransformMode && !forward ? getHeaderTextColor(left, false) : getHeaderTextColor(left, !inTransformMode);
      int rightHeaderTextColor = inTransformMode && forward ? getHeaderTextColor(left, true) : getHeaderTextColor(right, !inTransformMode);
      int baseTextColor = forward ? leftHeaderTextColor : rightHeaderTextColor;
      int previewTextColor = forward ? rightHeaderTextColor : leftHeaderTextColor;
      textChanger.setFromTo(baseTextColor, previewTextColor);
      if (title instanceof ColorSwitchPreparator) {
        ((ColorSwitchPreparator) title).prepareColorChangers(baseTextColor, previewTextColor);
      }
      if (preview instanceof ColorSwitchPreparator) {
        ((ColorSwitchPreparator) preview).prepareColorChangers(baseTextColor, previewTextColor);
      }
    }

    final int headerTextColor = getHeaderTextColor(previewItem, true);
    if (textTitle != null && (!useTextSwitch || title != textTitle)) {
      textTitle.setTextColor(headerTextColor);
    }
    if (textPreview != null && (!useTextSwitch || preview != textPreview)) {
      textPreview.setTextColor(headerTextColor);
    }

    if (useBackColorSwitch) {
      int leftBackColor = getBackButtonColor(left, !inTransformMode);
      int rightBackColor = inTransformMode ? getBackButtonColor(left, true) : getBackButtonColor(right, !inTransformMode);

      int baseBackColor = forward ? leftBackColor : rightBackColor;
      int previewBackColor = forward ? rightBackColor : leftBackColor;

      backChanger.setFromTo(baseBackColor, previewBackColor);
    } else {
      final int backButtonColor = getBackButtonColor(previewItem, true);
      backButton.setColor(backButtonColor);
    }

    setTranslation(translationFactor);

    if (useColorSwitch && (translationFactor == 0f)) {
      if (baseItem != null && baseItem.usePopupMode()) {
        getFilling().setColor(forward ? baseColor : previewColor);
      }
    }
  }

  private boolean previewOpened = false;

  void openPreview (ViewController<?> left, ViewController<?> right, boolean forward, int direction, float factor) {
    this.previewOpened = true;
    this.currentX = (float) getMeasuredWidth() * TRANSLATION_FACTOR;
    this.translationMode = direction;
    this.translationFactor = factor;

    if (right != null && !forward && right.usePopupMode()) {
      this.currentY = left.getHeaderHeight() + HeaderView.getTopOffset();
    } else {
      this.currentY = getCurrentHeight() + HeaderView.getTopOffset();
    }

    genPreview(left, right, forward); // Starting the preparation

    boolean previewIsPopup = previewItem != null && previewItem.usePopupMode() && !forward;
    boolean baseIsPopup = baseItem != null && baseItem.usePopupMode() && forward;

    float axisShift;

    switch (translationMode) {
      case MODE_HORIZONTAL: {
        preview.setAlpha(0f);
        preview.setTranslationX(axisShift = forward ? currentX : -currentX);
        preview.setTranslationY(0f);

        break;
      }
      case MODE_VERTICAL: {
        preview.setAlpha(0f);
        preview.setTranslationX(0f);
        preview.setTranslationY(axisShift = forward ? currentY : -currentY);

        break;
      }
      case MODE_NONE: {
        preview.setAlpha(1f);
        axisShift = 0f;

        break;
      }
      case MODE_FADE: {
        preview.setTranslationX(0f);
        preview.setTranslationY(0f);
        preview.setAlpha(0f);
        axisShift = 0f;

        break;
      }
      default: {
        axisShift = 0f;
        break;
      }
    }

    // boolean isInSelectMode = right == null && left.inSelectMode();

    int transformMode = getTransformMode(left);
    boolean inTransformMode = right == null && transformMode != TRANSFORM_MODE_NONE;

    int leftMenuId = getMenuId(left, !inTransformMode);
    int rightMenuId = right == null ? getTransformMenuId(left) : getMenuId(right, inTransformMode);

    int leftBackButton = getBackButton(left, !inTransformMode);
    int rightBackButton = inTransformMode ? (transformMode == TRANSFORM_MODE_SEARCH ? left.getSearchBackButton() : transformMode == TRANSFORM_MODE_CUSTOM ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_CLOSE) : getBackButton(right, true);

    if (forward) {
      if (leftMenuId != rightMenuId && rightMenuId != 0) {
        menuPreview.setAlpha(0f);
        menuPreview.setVisibility(View.VISIBLE);
      }
      if (leftBackButton != rightBackButton) {
        if (leftBackButton == BackHeaderButton.TYPE_NONE) {
          backButton.setAlpha(0f);
          backButton.setTranslationX(currentX);
          backButton.setTranslationY(0);
          backButton.setButtonFactor(rightBackButton);
          backButton.setVisibility(View.VISIBLE);
        }
      }
    } else {
      if (leftMenuId != rightMenuId && leftMenuId != 0) {
        menuPreview.setAlpha(0f);
        menuPreview.setVisibility(View.VISIBLE);
      }
      if (leftBackButton != rightBackButton && leftBackButton != BackHeaderButton.TYPE_NONE) {
        if (rightBackButton == BackHeaderButton.TYPE_NONE) {
          backButton.setAlpha(0f);
          backButton.setTranslationY(0);
          backButton.setButtonFactor(leftBackButton);
          backButton.setTranslationX(-currentX);
        }
      }
    }

    if (leftBackButton != rightBackButton && leftBackButton != BackHeaderButton.TYPE_NONE && rightBackButton != BackHeaderButton.TYPE_NONE) {
      boolean isVertical = translationMode == MODE_VERTICAL;
      switch (leftBackButton) {
        case BackHeaderButton.TYPE_MENU: {
          backButton.setIsReverse(isVertical);
          break;
        }
        case BackHeaderButton.TYPE_BACK: {
          switch (rightBackButton) {
            case BackHeaderButton.TYPE_MENU: {
              backButton.setIsReverse(!isVertical);
              break;
            }
            case BackHeaderButton.TYPE_CLOSE: {
              backButton.setIsReverse(!isVertical && forward);
              break;
            }
          }
          break;
        }
        case BackHeaderButton.TYPE_CLOSE: {
          backButton.setIsReverse(!isVertical);
          break;
        }
        default: {
          backButton.setIsReverse(isVertical);
          break;
        }
      }
    }

    if (leftMenuId != rightMenuId) {
      useMenuSwitch = true;
      int previewMenuId = forward ? rightMenuId : leftMenuId;
      int baseMenuId = forward ? leftMenuId : rightMenuId;
      if (previewMenuId != 0) {
        menuPreview.setAlpha(0f);
        menuPreview.setVisibility(View.VISIBLE);
        if (translationMode == MODE_VERTICAL) {
          menuPreview.setTranslationY(axisShift);
        } else {
          menuPreview.setTranslationY(0f);
        }
        useMenuPreview = true;
      } else {
        useMenuPreview = false;
      }
      useMenu = baseMenuId != 0;
    } else {
      useMenuSwitch = false;
    }

    if (leftBackButton != rightBackButton) {
      final int baseBack = forward ? leftBackButton : rightBackButton;
      final int previewBack = forward ? rightBackButton : leftBackButton;

      if (baseBack == BackHeaderButton.TYPE_NONE) { // TODO vertical support
        backButton.setAlpha(0f);
        if (forward) {
          backButton.setTranslationX(currentX);
          backButton.setTranslationY(0);
        } else {
          if (translationMode == MODE_VERTICAL) {
            backButton.setTranslationY(-(Size.getHeaderPortraitSize() + HeaderView.getTopOffset()));
          } else {
            backButton.setTranslationY(0);
          }
        }
        backButton.setButtonFactor(previewBack);
        backButton.setVisibility(View.VISIBLE);
      }

      if (previewBack == BackHeaderButton.TYPE_NONE || baseBack == BackHeaderButton.TYPE_NONE) {
        fromBackButtonFactor = toBackButtonFactor = BackHeaderButton.toButtonFactor(previewBack != BackHeaderButton.TYPE_NONE ? previewBack : baseBack);
        useBackFade = true;
        backFade = baseBack == BackHeaderButton.TYPE_NONE;
      } else {
        fromBackButtonFactor = BackHeaderButton.toButtonFactor(baseBack);
        toBackButtonFactor = BackHeaderButton.toButtonFactor(previewBack);
        useBackFade = false;
      }
    } else {
      fromBackButtonFactor = toBackButtonFactor = BackHeaderButton.toButtonFactor(leftBackButton);
      useBackFade = false;
    }

    fromHeight = height;
    /*usePreviewHeightSwitch = preview instanceof StretchyHeaderView;
    useBaseHeightSwitch = title instanceof StretchyHeaderView;*/

    float heightFactor = getHeightFactor();
    int leftHeaderHeight = getHeaderHeight(left);
    int rightHeaderHeight = right == null && left.inCustomMode() ? left.getCustomHeaderHeight() : right == null ? left.getTransformHeaderHeight() : getHeaderHeight(right);
    int previewHeaderHeight = forward ? rightHeaderHeight : leftHeaderHeight;
    float leftHeaderHeightFactor = getHeightFactor(leftHeaderHeight);
    float rightHeaderHeightFactor = getHeightFactor(rightHeaderHeight);
    previewHeightFactor = forward ? rightHeaderHeightFactor : leftHeaderHeightFactor;
    baseHeightFactor = forward ? leftHeaderHeightFactor : rightHeaderHeightFactor;

    if (leftHeaderHeight != rightHeaderHeight && previewHeaderHeight != height) {
      useHeightSwitch = true;
      heightDiff = previewHeaderHeight - height;
    } else {
      useHeightSwitch = false;
    }

    if (preview instanceof StretchyHeaderView) {
      ((StretchyHeaderView) preview).setScaleFactor(heightFactor, baseHeightFactor, previewHeightFactor, false);
    }

    if (title instanceof StretchyHeaderView) {
      ((StretchyHeaderView) title).setScaleFactor(heightFactor, previewHeightFactor, baseHeightFactor, false);
    }

    int leftFloatingButtonId = right == null ? getFloatingButtonId(left) : left.inCustomMode() ? left.getCustomFloatingButtonId() : getFloatingButtonId(left);
    int rightFloatingButtonId = right == null && left.inCustomMode() ? left.getCustomFloatingButtonId() : getFloatingButtonId(right);

    if (leftFloatingButtonId != 0 && rightFloatingButtonId != 0) {
      useFloatSwitch = 2;
    } else if ((leftFloatingButtonId != 0 && leftHeaderHeightFactor > 0f) || (rightFloatingButtonId != 0 && rightHeaderHeightFactor > 0f)) {
      useFloatSwitch = 1;
    } else {
      useFloatSwitch = 0;
    }
    baseHasFloat = false;

    if (useFloatSwitch != 0) {
      int baseItemButtonId = forward ? leftFloatingButtonId : rightFloatingButtonId;
      int previewItemButtonId = forward ? rightFloatingButtonId : leftFloatingButtonId;

      baseHasFloat = useFloatSwitch == 1 && baseItemButtonId != 0;

      if (baseHasFloat && previewItemButtonId == 0 && floatSwitch != null && floatSwitch.getScaleX() == 0 && floatSwitch.getScaleY() == 0) { // float button is hidden
        useFloatSwitch = 0;
      } else if (!baseHasFloat && previewItemButtonId != 0 && !useHeightSwitch && heightFactor == 0f) {
        useFloatSwitch = 0;
      } else {
        floatSwitch = navigation.getFloatingButton();
        floatSwitch.setIcons(baseItemButtonId, previewItemButtonId);
        floatSwitch.initHeightFactor(getHeightFactor(), previewItem == null ? 0f : getHeightFactor(previewItem.getHeaderHeight()), baseHasFloat ? 1f : 0f, useFloatSwitch != 1, previewItem != null && previewItem.usePopupMode());
      }
    }

    final int baseColor, previewColor;

    baseColor = inTransformMode && !forward ? getHeaderColor(left, true) : baseIsPopup ? ColorUtils.color(0, Theme.headerColor()) : getHeaderColor(baseItem, !inTransformMode);
    previewColor = inTransformMode && forward ? getHeaderColor(left, true) : previewIsPopup ? ColorUtils.color(0, Theme.headerColor()) : getHeaderColor(previewItem, !inTransformMode);

    if (baseColor != previewColor) {
      useColorSwitch = true;
      if (headerChanger == null) {
        headerChanger = new ColorChanger(baseColor, previewColor);
      } else {
        headerChanger.setFromTo(baseColor, previewColor);
      }
    } else {
      useColorSwitch = false;
    }

    if (baseItem != null && baseItem.usePopupMode()) {
      getFilling().setColor(forward ? baseColor : previewColor);
    }

    int leftHeaderTextColor = inTransformMode && !forward ? getHeaderTextColor(left, false) : getHeaderTextColor(left, !inTransformMode);
    int rightHeaderTextColor = inTransformMode && forward ? getHeaderTextColor(left, true) : getHeaderTextColor(right, !inTransformMode);
    int baseTextColor = forward ? leftHeaderTextColor : rightHeaderTextColor;
    int previewTextColor = forward ? rightHeaderTextColor : leftHeaderTextColor;
    boolean previewInSelect = previewItem != null && previewItem.inSelectMode();
    boolean baseInSelect = baseItem != null && baseItem.inSelectMode();

    baseTextSwitch = getTextSwitchModeForView(title);
    previewTextSwitch = getTextSwitchModeForView(preview);

    if (leftHeaderTextColor != rightHeaderTextColor && !inTransformMode && !previewInSelect && !baseInSelect) {
      useTextSwitch = baseTextSwitch != TEXT_SWITCH_NONE || previewTextSwitch != TEXT_SWITCH_NONE;
      if (textChanger == null) {
        textChanger = new ColorChanger(baseTextColor, previewTextColor);
      } else {
        textChanger.setFromTo(baseTextColor, previewTextColor);
      }
      if (title instanceof ColorSwitchPreparator) {
        ((ColorSwitchPreparator) title).prepareColorChangers(baseTextColor, previewTextColor);
      }
      if (preview instanceof ColorSwitchPreparator) {
        ((ColorSwitchPreparator) preview).prepareColorChangers(baseTextColor, previewTextColor);
      }
      /*switch (baseTextSwitch) {
        case TEXT_SWITCH_COMPLEX: {
          ((ComplexHeaderView) title).prepareColorChangers(baseTextColor, previewTextColor);
          break;
        }
        case TEXT_SWITCH_SIMPLE: {
          ((SimpleHeaderView) title).prepareColorChangers(baseTextColor, previewTextColor);
          break;
        }
      }
      switch (previewTextSwitch) {
        case TEXT_SWITCH_COMPLEX: {
          ((ComplexHeaderView) preview).prepareColorChangers(baseTextColor, previewTextColor);
          break;
        }
        case TEXT_SWITCH_SIMPLE: {
          ((SimpleHeaderView) preview).prepareColorChangers(baseTextColor, previewTextColor);
          break;
        }
      }*/
    } else {
      useTextSwitch = false;

      /*if (preview instanceof TextView && preview.getTag() == this) {
        ((TextView) preview).setTextColor(previewTextColor);
      }*/

      switch (previewTextSwitch) {
        case TEXT_SWITCH_NONE: {
          break;
        }
        case TEXT_SWITCH_NORMAL: {
          ((TextView) preview).setTextColor(previewTextColor);
          break;
        }
        case TEXT_SWITCH_TOGGLE: {
          ((ToggleHeaderView) preview).setTextColor(previewTextColor);
          break;
        }
        case TEXT_SWITCH_CUSTOM: {
          ((TextChangeDelegate) preview).setTextColor(previewTextColor);
          break;
        }
      }
    }

    int leftBackColor = getBackButtonColor(left, !inTransformMode);
    int rightBackColor = inTransformMode ? getBackButtonColor(left, true) : getBackButtonColor(right, !inTransformMode);

    if (leftBackColor != rightBackColor) {
      int baseBackColor = forward ? leftBackColor : rightBackColor;
      int previewBackColor = forward ? rightBackColor : leftBackColor;
      useBackColorSwitch = true;
      if (backChanger == null) {
        backChanger = new ColorChanger(baseBackColor, previewBackColor);
      } else {
        backChanger.setFromTo(baseBackColor, previewBackColor);
      }
    } else {
      useBackColorSwitch = false;
    }

    boolean baseShadow = (baseItem == null ? useDropShadow(previewItem) : useDropShadow(baseItem)) && !baseIsPopup;
    boolean previewShadow = (previewItem == null ? baseShadow : useDropShadow(previewItem)) && !previewIsPopup;
    boolean basePlayer = (baseItem == null ? useDropPlayer(previewItem) : useDropPlayer(baseItem)) && !baseIsPopup;
    boolean previewPlayer = (previewItem == null ? basePlayer : useDropPlayer(previewItem)) && !previewIsPopup;
    if (previewShadow != baseShadow) {
      useShadowSwitch = true;
      shadowSwitch = previewShadow;
    } else {
      useShadowSwitch = false;
    }
    if (useDropShadow(baseItem) && (baseItem != null || useDropShadow(previewItem))) {
      getFilling().setShadowAlpha(baseShadow ? 1f : 0f);
    }
    if (previewPlayer != basePlayer) {
      usePlayerSwitch = true;
      playerSwitch = previewPlayer;
    } else {
      usePlayerSwitch = false;
    }
    if (useDropPlayer(baseItem) && (baseItem != null || useDropPlayer(previewItem))) {
      getFilling().setPlayerAllowance(basePlayer ? 1f : 0f);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      int leftStatusColor = getStatusBarColor(left, !inTransformMode);
      int rightStatusColor = inTransformMode ? getStatusBarColor(left, true) : getStatusBarColor(right, !inTransformMode);
      int baseStatusColor = forward ? leftStatusColor : rightStatusColor;
      int previewStatusColor = forward ? rightStatusColor : leftStatusColor;

      if (leftStatusColor != rightStatusColor) {
        useBarSwitch = true;
        window = UI.getWindow();
        if (barChanger == null) {
          barChanger = new ColorChanger(baseStatusColor, previewStatusColor);
        } else {
          barChanger.setFromTo(baseStatusColor, previewStatusColor);
        }
      } else {
        useBarSwitch = false;
      }
    }
  }

  void clearPreview () {
    this.previewOpened = false;
    previewItem = null;
    baseItem = null;
    if (shareHeader) {
      title.setVisibility(View.VISIBLE);
    }
    title.setAlpha(1f);
    title.setTranslationX(0f);
    removeView(preview);
    removeView(menuPreview);
    ViewController<?> current = stack.getCurrent();
    if (current != null) {
      if (current.getBackButton() == BackHeaderButton.TYPE_NONE) {
        backButton.setVisibility(View.GONE);
      } else {
        backButton.setButtonFactor(current.getBackButton());
        backButton.invalidate();
      }
      int menuId = getMenuId(current, true);
      if (menuId == 0) {
        menu.setVisibility(View.GONE);
      }
      filling.setColor(getHeaderColor(current, true));
    }
    height = fromHeight;
    filling.layout((int) height, getHeightFactor());
    if (useHeightSwitch && title instanceof StretchyHeaderView) {
      ((StretchyHeaderView) title).setScaleFactor(getHeightFactor(), getHeightFactor(), getHeightFactor(), false);
    }
    invalidate();
  }

  private float getHeightFactor () {
    return (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();
  }

  private static float getHeightFactor (float height) {
    return (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();
  }

  private boolean headerDisabled;

  public void setHeaderDisabled (boolean disabled) {
    if (headerDisabled != disabled) {
      this.headerDisabled = disabled;
      setVisibility(headerDisabled ? INVISIBLE : VISIBLE);
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return shouldIgnoreTouches();
  }

  private boolean shouldIgnoreTouches () {
    return headerDisabled || Color.alpha(overlayColor) > 0 || getAlpha() == 0f || getVisibility() != View.VISIBLE;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (shouldIgnoreTouches()) {
      return false;
    }
    boolean res = filling.onTouchEvent(e);
    return super.onTouchEvent(e) || res;
  }

  public static int getTransformMode (ViewController<?> c) {
    return c.inCustomMode() ? TRANSFORM_MODE_CUSTOM : c.inSelectMode() ? TRANSFORM_MODE_SELECT : c.inSearchMode() ? TRANSFORM_MODE_SEARCH : TRANSFORM_MODE_NONE;
  }

  public static int getMenuId (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return 0;
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return c.getSelectMenuId();
      }
      if (c.inSearchMode()) {
        return c.getSearchMenuId();
      }
      if (c.inCustomMode()) {
        return 0;
      }
    }
    return c.getMenuId();
  }

  public static int getTransformMenuId (ViewController<?> c) {
    return c == null || c.inCustomMode() ? 0 : c.inSelectMode() ? c.getSelectMenuId() : c.inSearchMode() ? c.getSearchMenuId() : 0;
  }

  public static int getBackButton (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return BackHeaderButton.TYPE_NONE;
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return BackHeaderButton.TYPE_CLOSE;
      }
      if (c.inSearchMode()) {
        return c.getSearchBackButton();
      }
      if (c.inCustomMode()) {
        return BackHeaderButton.TYPE_BACK;
      }
    }
    return c.getBackButton();
  }

  public static int getHeaderHeight (ViewController<?> c) {
    return c == null ? Size.getHeaderPortraitSize() : c.getHeaderHeight();
  }

  public static int getFloatingButtonId (ViewController<?> c) {
    return c == null ? 0 : c.getFloatingButtonId();
  }

  public static int getHeaderColor (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return Theme.headerColor();
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return c.getSelectHeaderColor();
      }
      if (c.inSearchMode()) {
        return c.getSearchHeaderColor();
      }
    }
    return c.getHeaderColor();
  }

  public static int getHeaderTextColor (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return Theme.headerTextColor();
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return Theme.getColor(c.getSelectTextColorId());
      }
      if (c.inSearchMode()) {
        return /*c.inChatSearchMode() ? c.getHeaderTextColor() : */c.getSearchTextColor();
      }
    }
    return c.getHeaderTextColor();
  }

  public static int getBackButtonColor (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return Theme.headerBackColor();
    }
    if (allowTransform) {
      if (c.inSearchMode()) {
        return c.getSearchHeaderIconColor();
      }
      if (c.inSelectMode()) {
        return c.getSelectHeaderIconColor();
      }
    }
    return c.getHeaderIconColor();
  }

  public static int getBackButtonResource (ViewController<?> c, boolean allowTransform) {
    if (c == null) {
      return ThemeDeprecated.headerSelector();
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return c.getSelectBackButtonResource();
      }
      if (c.inSearchMode()) {
        return c.getSearchBackButtonResource();
      }
    }
    return c.getBackButtonResource();
  }

  public static boolean useDropShadow (ViewController<?> c) {
    return c == null || c.useDropShadow();
  }

  public static boolean useDropPlayer (ViewController<?> c) {
    return c == null || c.useDropPlayer();
  }

  private static final int STATUS_OVERLAY_COLOR = 0x33000000;

  public static int computeStatusBarColor (int color) {
    return ColorUtils.compositeColor(color, STATUS_OVERLAY_COLOR);
  }

  public static int computeStatusBarColor (ViewController<?> c, int alpha) {
    return ColorUtils.compositeColor(c.getStatusBarColor(), Color.argb((int) ((float) alpha / 255f * (float) Color.alpha(STATUS_OVERLAY_COLOR)), Color.red(STATUS_OVERLAY_COLOR), Color.green(STATUS_OVERLAY_COLOR), Color.blue(STATUS_OVERLAY_COLOR)));
  }

  public static int defaultStatusColor () {
    return computeStatusBarColor(Theme.headerColor());
  }

  public static final int DEFAULT_STATUS_COLOR = 0x4c000000;

  public static @ColorInt int whiteStatusColor () {
    return computeStatusBarColor(0xffffffff);
  }

  public static @ColorInt int getStatusBarColor (ViewController<?> c, boolean allowTransform) {
    return c == null ? DEFAULT_STATUS_COLOR : c.getNewStatusBarColor();
    /*if (Config.USE_FULLSCREEN_NAVIGATION) {
      return c == null ? DEFAULT_STATUS_COLOR : c.getNewStatusBarColor();
    }

    if (c == null) {
      return defaultStatusColor();
    }
    if (allowTransform) {
      if (c.inSelectMode()) {
        return c.getSelectStatusBarColor();
      }
      if (c.inSearchMode()) {
        return c.getSearchStatusBarColor();
      }
    }
    return c.getStatusBarColor();*/
  }

  public static int getSize (boolean big, boolean includeOffset) {
    return big ? getBigSize(includeOffset) : getSize(includeOffset);
  }

  public static int getSize (boolean includeOffset) {
    return includeOffset ? Size.getHeaderPortraitSize() + getTopOffset() : Size.getHeaderPortraitSize();
  }

  public static int getBigSize (boolean includeOffset) {
    return includeOffset ? Size.getMaximumHeaderSize() + getTopOffset() : Size.getMaximumHeaderSize();
  }

  public static int getPlayerSize () {
    return Size.getHeaderPlayerSize();
  }

  public static int getTopOffset () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION) {
      return Screen.getStatusBarHeight();
    }
    return 0;
  }

  // Language

  @Override
  public void performDestroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
    Lang.removeLanguageListener(this);
    Screen.removeStatusBarHeightListener(this);
    if (filling != null) {
      filling.performDestroy();
    }
  }

  public static void reverseChildrenDirection (ViewGroup group) {
    if (group != null) {
      int childCount = group.getChildCount();
      if (childCount > 1) {
        List<View> children = new ArrayList<>(childCount);
        for (int i = childCount - 1; i >= 0; i--) {
          children.add(group.getChildAt(i));
          group.removeViewAt(i);
        }
        for (View child : children) {
          if (child != null)
            group.addView(child);
        }
      }
    }
  }

  public static void updateEditTextDirection (View view, int leftMargin, int rightMargin) {
    if (view instanceof RtlCheckListener) {
      ((RtlCheckListener) view).checkRtl();
    }
    if (view instanceof HeaderEditText) {
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) view.getLayoutParams();
      int gravity = Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
      boolean changed = params.gravity != gravity;
      params.gravity = gravity;
      if (Lang.rtl()) {
        changed = changed || params.leftMargin != rightMargin || params.rightMargin != leftMargin;
        params.rightMargin = leftMargin;
        params.leftMargin = rightMargin;
      } else {
        changed = changed || params.leftMargin != leftMargin || params.rightMargin != rightMargin;
        params.leftMargin = leftMargin;
        params.rightMargin = rightMargin;
      }
      if (changed) {
        Views.updateLayoutParams(view);
      }
    }
  }

  public static void updateLayoutMargins (View view, int leftMargin, int rightMargin) {
    if (view instanceof CounterHeaderView) {
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) view.getLayoutParams();
      int gravity = Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
      boolean changed = params.gravity != gravity;
      params.gravity = gravity;
      if (Lang.rtl()) {
        changed = changed || params.leftMargin != rightMargin || params.rightMargin != leftMargin;
        params.rightMargin = leftMargin;
        params.leftMargin = rightMargin;
      } else {
        changed = changed || params.leftMargin != leftMargin || params.rightMargin != rightMargin;
        params.leftMargin = leftMargin;
        params.rightMargin = rightMargin;
      }
      if (changed) {
        Views.updateLayoutParams(view);
      }
    }
  }

  public static void reversePadding (View view) {
    if (view != null) {
      int paddingLeft = view.getPaddingLeft();
      int paddingRight = view.getPaddingRight();
      view.setPadding(paddingRight, view.getPaddingTop(), paddingLeft, view.getPaddingBottom());
    }
  }

  public static void reverseMargins (View view) {
    if (view != null) {
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
      if (params.leftMargin != params.rightMargin) {
        int right = params.rightMargin;
        params.rightMargin = params.leftMargin;
        params.leftMargin = right;
        Views.updateLayoutParams(view);
      }
    }
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    boolean directionChanged = Lang.hasDirectionChanged(event, arg1);

    if (directionChanged) {
      if (Views.setGravity(backButton, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT)))
        Views.updateLayoutParams(backButton);
      if (Views.setGravity(menu, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT))) {
        Views.updateLayoutParams(menu);
        reverseChildrenDirection(menu);
      }
      if (Views.setGravity(menuPreview, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT))) {
        Views.updateLayoutParams(menuPreview);
        reverseChildrenDirection(menuPreview);
      }
      if (Views.setGravity(textTitle, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
        reverseMargins(textTitle);
        /*if (textTitle != null)
          textTitle.setGravity(Lang.gravity());*/
      }
      if (Views.setGravity(textPreview, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
        reverseMargins(textPreview);
        /*if (textPreview != null)
          textPreview.setGravity(Lang.gravity());*/
      }
      if (moreWrap != null) {
        moreWrap.updateDirection();
      }
    }
    // TODO more
  }
}
