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
 * File created on 16/05/2015 at 16:02
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Strings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.DoubleTextView;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.android.widget.FrameLayoutFix;

public class MenuMoreWrap extends LinearLayout implements Animated {
  public static final float START_SCALE = .56f;

  public static final long REVEAL_DURATION = 258l;
  public static final Interpolator REVEAL_INTERPOLATOR = AnimatorUtils.DECELERATE_INTERPOLATOR;

  public static final int ANCHOR_MODE_RIGHT = 0;
  public static final int ANCHOR_MODE_HEADER = 1;
  public static final int ANCHOR_MODE_CENTER = 2;

  // private int currentWidth;
  private int anchorMode;

  private @Nullable ThemeListenerList themeListeners;
  private @Nullable ThemeDelegate forcedTheme;


  public MenuMoreWrap (Context context) {
    super(context);
  }

  public void updateDirection () {
    if (Views.setGravity(this, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
      Views.updateLayoutParams(this);
  }


  public void init (@Nullable ThemeListenerList themeProvider, ThemeDelegate forcedTheme) {
    this.themeListeners = themeProvider;
    this.forcedTheme = forcedTheme;

    setMinimumWidth(Screen.dp(196f));
    Drawable drawable;
    if (forcedTheme != null) {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(forcedTheme.getColor(R.id.theme_color_overlayFilling), PorterDuff.Mode.MULTIPLY));
    } else {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.headerFloatBackgroundColor(), PorterDuff.Mode.MULTIPLY));
    }
    ViewUtils.setBackground(this, drawable);

    if (themeProvider != null && forcedTheme == null) {
      themeProvider.addThemeSpecialFilterListener(drawable, R.id.theme_color_overlayFilling);
      themeProvider.addThemeInvalidateListener(this);
    }

    setOrientation(VERTICAL);
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));
  }

  public void setRightNumber (int number) {
    setTranslationX(-Screen.dp(49f) * number);
  }

  public void setAnchorMode (int anchorMode) {
    if (this.anchorMode != anchorMode) {
      this.anchorMode = anchorMode;
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) getLayoutParams();
      switch (anchorMode) {
        case ANCHOR_MODE_RIGHT:
        case ANCHOR_MODE_CENTER: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
          break;
        }
        case ANCHOR_MODE_HEADER: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
          setTranslationX(Lang.rtl() ? -Screen.dp(46f) : Screen.dp(46f));
          break;
        }
      }
    }
  }

  public int getAnchorMode () {
    return anchorMode;
  }

  public void updateItem (int index, int id, CharSequence title, int icon, OnClickListener listener, @Nullable ThemeListenerList themeProvider) {
    TextView menuItem = (TextView) getChildAt(index);
    menuItem.setId(id);
    menuItem.setOnClickListener(listener);
    menuItem.setText(title);
    Drawable drawable = icon != 0 ? Drawables.get(getResources(), icon) : null;
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setVisibility(View.VISIBLE);
    if (drawable != null) {
      applyIconColor(drawable, themeProvider, null);
      if (Drawables.needMirror(icon)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
      }
    } else {
      menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
  }

  public TextView addItem (int id, CharSequence title, int iconRes, Drawable icon, OnClickListener listener) {
    TextView menuItem = new NoScrollTextView(getContext());
    menuItem.setId(id);
    menuItem.setTypeface(Fonts.getRobotoRegular());
    menuItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    if (forcedTheme != null) {
      menuItem.setTextColor(forcedTheme.getColor(R.id.theme_color_text));
    } else {
      menuItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(menuItem);
      }
    }
    menuItem.setText(title);
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setSingleLine(true);
    menuItem.setEllipsize(TextUtils.TruncateAt.END);
    menuItem.setOnClickListener(listener);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    menuItem.setPadding(Screen.dp(17f), 0, Screen.dp(17f), 0);
    menuItem.setCompoundDrawablePadding(Screen.dp(18f));
    icon = iconRes != 0 ? Drawables.get(getResources(), iconRes) : icon;
    if (icon != null) {
      applyIconColor(icon, themeListeners, forcedTheme);
      if (Drawables.needMirror(iconRes)) {
        // TODO
      }
    }
    if (icon != null) {
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
      }
    }
    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);
    addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }

  public View addItem2 (int id, CharSequence title, @Nullable CharSequence subtitle, int iconRes, Drawable icon, int rightIconRes, boolean preserveRightIconSpace, OnClickListener listener) {
    var menuItem = new SenderIdentityView(getContext());
    menuItem.setId(id);
    menuItem.init(themeListeners, forcedTheme);
    menuItem.setSenderIdentity(iconRes, icon, title, subtitle, rightIconRes, null, preserveRightIconSpace);
    menuItem.setOnClickListener(listener);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    menuItem.setPadding(Screen.dp(17f), 0, Screen.dp(17f), 0);
    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);
    addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }

  private static void applyIconColor(@NonNull Drawable icon, @Nullable ThemeListenerList themeProvider, @Nullable ThemeDelegate forcedTheme) {
    if (forcedTheme != null) {
      icon.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(R.id.theme_color_icon)));
    } else {
      icon.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));
      if (themeProvider != null) {
        themeProvider.addThemeFilterListener(icon, R.id.theme_color_icon);
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  public int getItemsWidth () {
    int padding = Screen.dp(8f);
    int childCount = getChildCount();
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    return Math.max(getMinimumWidth(), maxWidth + padding + padding);
  }

  private boolean shouldPivotBottom;

  public void setShouldPivotBottom (boolean shouldPivotBottom) {
    this.shouldPivotBottom = shouldPivotBottom;
  }

  public boolean shouldPivotBottom () {
    return shouldPivotBottom;
  }

  public int getItemsHeight () {
    int itemHeight = Screen.dp(48f);
    int padding = Screen.dp(8f);
    int total = 0;
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        total += itemHeight;
      }
    }
    return total + padding + padding;
  }

  public float getRevealRadius () {
    return (float) Math.hypot(getItemsWidth(), getItemsHeight());
  }

  public void scaleIn (Animator.AnimatorListener listener) {
    Views.animate(this, 1f, 1f, 1f, 135l, 10l, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  public void scaleOut (Animator.AnimatorListener listener) {
    Views.animate(this, START_SCALE, START_SCALE, 0f, 120l, 0l, AnimatorUtils.ACCELERATE_INTERPOLATOR, listener);
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private class SenderIdentityView extends LinearLayout {

    private final ImageView avatar;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView extra;

    public SenderIdentityView (Context context) {
      super(context);

      //setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f)));
      setGravity(Gravity.CENTER_VERTICAL);

      avatar = new ImageView(context);
      avatar.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));

      title = new TextView(context);
      title.setTypeface(Fonts.getRobotoRegular());
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      title.setTextColor(Theme.textAccentColor());
      title.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      title.setMaxWidth(Screen.dp(200f));

      subtitle = new TextView(context);
      subtitle.setTypeface(Fonts.getRobotoRegular());
      subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
      subtitle.setTranslationY(-Screen.dp(3f));
      subtitle.setTextColor(Theme.textDecentColor());
      subtitle.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      subtitle.setSingleLine(true);
      subtitle.setEllipsize(TextUtils.TruncateAt.END);
      subtitle.setMaxWidth(Screen.dp(200f));

      extra = new ImageView(context);
      extra.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_text)));

      var titlesLayout = new LinearLayout(context);
      titlesLayout.setOrientation(VERTICAL);
      titlesLayout.addView(title);
      titlesLayout.addView(subtitle);

      var lp = new LayoutParams(Screen.dp(24f), ViewGroup.LayoutParams.WRAP_CONTENT);
      avatar.setLayoutParams(lp);

      lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = Screen.dp(18f);
      lp.weight = 1f;
      titlesLayout.setLayoutParams(lp);

      lp = new LayoutParams(Screen.dp(16f), ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = Screen.dp(18f);
      extra.setLayoutParams(lp);

      addView(avatar);
      addView(titlesLayout);
      addView(extra);
    }

    public void init (@Nullable ThemeListenerList themeProvider, ThemeDelegate forcedTheme) {
      if (forcedTheme != null) {
        avatar.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(R.id.theme_color_icon)));
        title.setTextColor(forcedTheme.getColor(R.id.theme_color_text));
        subtitle.setTextColor(forcedTheme.getColor(R.id.theme_color_textLight));
        extra.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(R.id.theme_color_text)));
      } else {
        avatar.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));
        title.setTextColor(Theme.textAccentColor());
        subtitle.setTextColor(Theme.textDecentColor());
        extra.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_text)));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(avatar, R.id.theme_color_icon);
          themeProvider.addThemeTextAccentColorListener(title);
          themeProvider.addThemeTextDecentColorListener(subtitle);
          themeProvider.addThemeFilterListener(extra, R.id.theme_color_text);
        }
      }
    }

    public void setSenderIdentity (int avatarRes, Drawable avatar, CharSequence title, CharSequence subtitle, int extraRes, Drawable extra, boolean preserveExtraIconSpace) {
      if (avatarRes != 0) {
        this.avatar.setImageResource(avatarRes);
      } else if (avatar != null) {
        this.avatar.setImageDrawable(avatar);
      } else {
        this.avatar.setImageResource(0);
        //this.avatar.setVisibility(INVISIBLE);
      }

      this.title.setText(title);

      if (subtitle != null) {
        this.subtitle.setText(subtitle);
        this.subtitle.setVisibility(VISIBLE);
      } else {
        this.subtitle.setVisibility(GONE);
      }

      if (extraRes != 0) {
        this.extra.setImageResource(extraRes);
        this.extra.setVisibility(VISIBLE);
      } else if (extra != null) {
        this.extra.setImageDrawable(extra);
        this.extra.setVisibility(VISIBLE);
      } else {
        this.extra.setVisibility(preserveExtraIconSpace ? INVISIBLE : GONE);
      }
    }

    @Override
    protected void onDraw (Canvas canvas) {
      avatar.invalidate();
      //extra.invalidate();

      super.onDraw(canvas);
    }
  }
}