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
 * File created on 25/04/2015 at 09:12
 */
package org.thunderdog.challegram.tool;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.DiffMatchPatch;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewTranslator;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.util.WebViewHolder;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.lang.reflect.Field;
import java.util.LinkedList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.Destroyable;

public class Views {
  public static void setClickable (View view) {
    view.setClickable(true);
    view.setFocusableInTouchMode(false);
    view.setFocusable(false);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      if (view instanceof ViewGroup) {
        ((ViewGroup) view).setMotionEventSplittingEnabled(false);
      }
    }
  }

  public static void setScrollBarPosition (View view) {
    if (view != null) {
      view.setVerticalScrollbarPosition(Lang.rtl() ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
    }
  }

  public static void setSelection (android.widget.EditText editText, int selection) {
    if (editText != null) {
      try {
        editText.setSelection(selection);
      } catch (Throwable ignored) { }
    }
  }

  public static void setSelection (android.widget.EditText editText, int selectionStart, int selectionEnd) {
    if (editText != null) {
      try {
        editText.setSelection(selectionStart, selectionEnd);
      } catch (Throwable ignored) { }
    }
  }

  public static void attach (View view) {
    if (view != null && view instanceof AttachDelegate) {
      ((AttachDelegate) view).attach();
    }
  }

  public static void detach (View view) {
    if (view != null && view instanceof AttachDelegate) {
      ((AttachDelegate) view).detach();
    }
  }

  public static void setSimpleShadow (TextView view) {
    view.setShadowLayer(Screen.dp(3f), 0, Screen.dp(.666666667f), 0x4c000000);
  }

  public static void selectAll (android.widget.EditText view) {
    if (view != null) {
      try {
        view.setSelection(0, view.getText().length());
      } catch (Throwable ignored) { }
    }
  }

  public static void setText (android.widget.EditText v, String text) {
    if (v != null) {
      v.setText(text);
      try {
        v.setSelection(text.length());
      } catch (Throwable ignored) { }
    }
  }

  public static void attachDetach (ViewGroup group, boolean attach) {
    final int childCount = group.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = group.getChildAt(i);
      if (view != null) {
        if (view instanceof AttachDelegate) {
          if (attach) {
            ((AttachDelegate) view).attach();
          } else {
            ((AttachDelegate) view).detach();
          }
        }
        if (view instanceof ViewGroup) {
          attachDetach((ViewGroup) view, attach);
        }
      }
    }
  }

  public static ImageView newImageButton (Context context, @DrawableRes int icon, @ThemeColorId int colorId, @Nullable ViewController<?> themeProvider) {
    ImageView imageView = new ImageView(context);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setImageResource(icon);
    imageView.setColorFilter(Theme.getColor(colorId));
    setClickable(imageView);
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(imageView, colorId);
    }
    return imageView;
  }

  public static void destroy (ViewGroup group) {
    final int childCount = group.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = group.getChildAt(i);
      if (view != null) {
        if (view instanceof Destroyable) {
          ((Destroyable) view).performDestroy();
        }
        if (view instanceof ViewGroup) {
          destroy(((ViewGroup) view));
        }
      }
    }
  }

  public static void setSingleLine (android.widget.EditText editText, boolean singleLine) {
    int savedCursorStart = editText.getSelectionStart();
    int savedCursorEnd = editText.getSelectionEnd();
    editText.setSingleLine(singleLine);
    if (savedCursorEnd != 0 || savedCursorStart != 0) {
      try {
        editText.setSelection(savedCursorStart, savedCursorEnd);
      } catch (Throwable t) {
        Log.w("Cannot move cursor", t);
      }
    }
  }

  public static void removeFromParent (View view) {
    if (view != null && view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
  }

  @Deprecated
  public static int getParentsTop (View view, int limit) {
    return getParentsTop(view, limit, false);
  }

  private static int getParentsTop (View view, int limit, boolean includeTranslation) {
    int top = 0;
    ViewParent parent = view.getParent();
    int i = 0;
    while (parent != null && parent instanceof ViewGroup && i < limit) {
      top += ((ViewGroup) parent).getTop();
      if (includeTranslation) {
        top += ((View) parent).getTranslationY();
      }
      parent = parent.getParent();
      i++;
    }
    return top;
  }

  public static final int TEXT_FLAG_BOLD = 0x01;
  public static final int TEXT_FLAG_SINGLE_LINE = 0x02;
  public static final int TEXT_FLAG_HORIZONTAL_PADDING = 0x04;

  public static TextView newTextView (Context context, float dp, int color, int gravity, int flags) {
    TextView textView = new NoScrollTextView(context);
    textView.setTypeface((flags & TEXT_FLAG_BOLD) != 0 ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    textView.setTextColor(color);
    textView.setGravity(gravity);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, dp);
    if ((flags & TEXT_FLAG_SINGLE_LINE) != 0) {
      textView.setSingleLine(true);
      textView.setEllipsize(TextUtils.TruncateAt.END);
    }
    boolean needHorizontalPadding = (flags & TEXT_FLAG_HORIZONTAL_PADDING) != 0;
    textView.setPadding(needHorizontalPadding ? Screen.dp(16f) : 0, 0, needHorizontalPadding ? Screen.dp(16f) : 0, 0);
    return textView;
  }

  @Deprecated
  public static void getPosition (View view, int[] output) {
    int x = view.getLeft();
    int y = view.getTop();

    ViewParent parent;
    while ((parent = view.getParent()) != null && parent instanceof View) {
      view = (View) parent;
      x += view.getLeft();
      y += view.getTop();
    }

    output[0] = x;
    output[1] = y - Screen.getStatusBarHeight();
  }

  private static int[] temp;

  public static int[] getLocationInWindow (View view) {
    if (temp == null)
      temp = new int[2];
    view.getLocationInWindow(temp);
    return temp;
  }

  public static int[] getLocationOnScreen (View view) {
    if (temp == null)
      temp = new int[2];
    view.getLocationOnScreen(temp);
    return temp;
  }

  public static void replaceText (Editable editable, String source, String target) {
    LinkedList<DiffMatchPatch.Diff> diffs = DiffMatchPatch.instance().diff_main(source, target);
    int index = 0;
    for (DiffMatchPatch.Diff diff : diffs) {
      switch (diff.operation) {
        case DELETE: {
          editable.delete(index, index + diff.text.length());
          break;
        }
        case INSERT: {
          editable.insert(index, diff.text);
          index += diff.text.length();
          break;
        }
        case EQUAL: {
          index += diff.text.length();
          break;
        }
      }
    }
  }

  public static int getParamsWidth (View view) {
    if (view != null) {
      ViewGroup.LayoutParams params = view.getLayoutParams();
      if (params != null) {
        if (params instanceof ViewGroup.MarginLayoutParams) {
          ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) params;
          return params.width + mp.leftMargin + mp.rightMargin;
        } else {
          return params.width;
        }
      }
    }
    return 0;
  }

  private static ViewTranslator translator;
  public static void translateView (View view, float to, float from) {
    if (view != null) {
      if (Anim.ANIMATORS) {
        view.setTranslationX(to);
      } else {
        if (translator == null)
          translator = new ViewTranslator(from, to, 0, 0);
        else
          translator.updateValues(from, to, 0, 0);
        view.startAnimation(translator);
      }
    }
  }

  private static Canvas viewObtainer;
  public static Bitmap getBitmapFromView (View view) {
    if (view == null)
      return null;

    if (viewObtainer == null)
      viewObtainer = new Canvas();

    Bitmap bitmap;

    bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.RGB_565);
    viewObtainer.setBitmap(bitmap);

    view.draw(viewObtainer);

    return bitmap;
  }

  public static void setChildrenVisibility (ViewGroup group, int visibility) {
    if (group != null) {
      for (int i = 0; i < group.getChildCount(); i++) {
        View v = group.getChildAt(i);
        v.setVisibility(visibility);
      }
    }
  }

  public static View inflate (Context context, int resource, ViewGroup contentView) {
    return LayoutInflater.from(context).inflate(resource, contentView, false);
  }

  public static int save (Canvas c) {
    return c.save(); // 191
  }

  public static void restore (Canvas c, int saveCount) {
    try {
      // c.restore(); // 182
      c.restoreToCount(saveCount); // 9
    } catch (IllegalArgumentException e) {
      Tracer.canvasFailure(e, saveCount);
    }
  }

  public static final boolean HARDWARE_LAYER_ENABLED = true;
  private static final Paint LAYER_PAINT = HARDWARE_LAYER_ENABLED ? new Paint(Paint.FILTER_BITMAP_FLAG) : null;
  
  public static Paint getLayerPaint () {
    return LAYER_PAINT;
  }

  public static void setLayerTypeOptionally (final View view, final int type) {
    if (HARDWARE_LAYER_ENABLED) {
      view.post(() -> view.setLayerType(type, getLayerPaint()));
    }
  }

  public static void setLayerType (final View view, final int type) {
    view.post(() -> view.setLayerType(type, getLayerPaint()));
  }

  public static void print (View view) {
    print(view, 0);
  }

  private static void print (View view, int level) {
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View v = group.getChildAt(i);
        StringBuilder b = new StringBuilder();
        for (int c = 0; c < level; c++) {
          b.append("+ ");
        }
        b.append(v.getClass().getName());
        switch (v.getLayerType()) {
          case View.LAYER_TYPE_NONE: {
            b.append(" NONE");
            Log.v(b.toString());
            break;
          }
          case View.LAYER_TYPE_SOFTWARE: {
            b.append(" SOFTWARE");
            Log.i(b.toString());
            break;
          }
          case View.LAYER_TYPE_HARDWARE: {
            b.append(" HARDWARE");
            Log.w(b.toString());
            break;
          }
          default: {
            b.append(" WTF ");
            b.append(v.getLayerType());
            break;
          }
        }
        if (v instanceof ViewGroup) {
          print(v, level + 1);
        }
      }
    }
  }

  public static SurfaceView findSurfaceView (View view) {
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View v = group.getChildAt(i);
        if (v instanceof SurfaceView) {
          return (SurfaceView) v;
        }
        SurfaceView result = findSurfaceView(v);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  // Animations

  private static final boolean DO_NOT_EAT_SHIT = true;

  public static void animateAlpha (final View view, final float toAlpha, final long duration, final Interpolator interpolator, final Animator.AnimatorListener listener) {
    animateAlpha(view, toAlpha, duration, 0l, interpolator, listener);
  }

  public static void animateAlpha (final View view, final float toAlpha, final long duration, final long startDelay, final Interpolator interpolator, final Animator.AnimatorListener listener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && DO_NOT_EAT_SHIT) {
      android.view.ViewPropertyAnimator animator = view.animate().alpha(toAlpha).setDuration(duration).setInterpolator(interpolator).setListener(listener);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && startDelay > 0l) {
        animator.setStartDelay(startDelay);
      }
      return;
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setInterpolator(interpolator);
    animator.setDuration(duration);
    if (listener != null) {
      animator.addListener(listener);
    }
    if (startDelay > 0) {
      animator.setStartDelay(startDelay);
    }
    final float startAlpha = view.getAlpha();
    final float diffAlpha = toAlpha - startAlpha;
    animator.addUpdateListener(animation -> view.setAlpha(startAlpha + diffAlpha * (Float) animation.getAnimatedValue()));
    animator.start();
  }

  public static void animateY (final View view, final float toY, final long duration, final Interpolator interpolator, final Animator.AnimatorListener listener) {
    animateY(view, toY, duration, 0l, interpolator, listener);
  }

  public static void animateY (final View view, final float toY, final long duration, final long startDelay, final Interpolator interpolator, final Animator.AnimatorListener listener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && DO_NOT_EAT_SHIT) {
      android.view.ViewPropertyAnimator animator = view.animate().translationY(toY).setDuration(duration).setInterpolator(interpolator).setListener(listener);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && startDelay >= 0l) {
        animator.setStartDelay(startDelay);
      }
      return;
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setInterpolator(interpolator);
    animator.setDuration(duration);
    if (listener != null) {
      animator.addListener(listener);
    }
    if (startDelay > 0) {
      animator.setStartDelay(startDelay);
    }
    final float startY = view.getTranslationY();
    final float diffY = toY - startY;
    animator.addUpdateListener(animation -> view.setTranslationY(startY + diffY * (Float) animation.getAnimatedValue()));
    animator.start();
  }

  public static void animate (final View view, final float scaleX, final float scaleY, final float alpha, long duration, Interpolator interpolator, Animator.AnimatorListener listener) {
    animate(view, scaleX, scaleY, alpha, duration, 0l, interpolator, listener);
  }

  public static void animate (final View view, final float scaleX, final float scaleY, final float alpha, long duration, long startDelay, Interpolator interpolator, Animator.AnimatorListener listener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && DO_NOT_EAT_SHIT) {
      android.view.ViewPropertyAnimator animator = view.animate().scaleX(scaleX).scaleY(scaleY).alpha(alpha).setInterpolator(interpolator).setDuration(duration).setListener(listener);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && startDelay >= 0l) {
        animator.setStartDelay(startDelay);
      }
      return;
    }

    final float startX = view.getScaleX();
    final float diffX = scaleX - startX;

    final float startY = view.getScaleY();
    final float diffY = scaleY - startY;

    final float startAlpha = view.getAlpha();
    final float diffAlpha = alpha - startAlpha;

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(135l);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    if (listener != null) {
      animator.addListener(listener);
    }
    if (startDelay > 0l) {
      animator.setStartDelay(startDelay);
    }
    animator.addUpdateListener(animation -> {
      float factor = (Float) animation.getAnimatedValue();
      view.setScaleX(startX + diffX * factor);
      view.setScaleY(startY + diffY * factor);
      view.setAlpha(startAlpha + diffAlpha * factor);
    });
    animator.start();
  }

  public static void rotateBy (final View view, final float byDegrees, final long duration, Interpolator interpolator, final Animator.AnimatorListener listener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && DO_NOT_EAT_SHIT) {
      view.animate().rotationBy(byDegrees).setDuration(duration).setInterpolator(interpolator).setListener(listener);
      return;
    }
    final float fromDegrees = view.getRotation();
    final ValueAnimator obj = AnimatorUtils.simpleValueAnimator();
    obj.setDuration(duration);
    obj.setInterpolator(interpolator);
    if (listener != null) {
      obj.addListener(listener);
    }
    obj.addUpdateListener(animation -> view.setRotation(fromDegrees + byDegrees * AnimatorUtils.getFraction(animation)));
    obj.start();
  }

  public static void clearAnimations (View view) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      // FIXME check what happens on 13 target API
      view.animate().cancel();
    }
  }

  public static void setCursorDrawable (android.widget.EditText editText, @DrawableRes int res) {
    if (editText != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        editText.setTextCursorDrawable(res);
      } else {
        try {
          //noinspection JavaReflectionMemberAccess
          Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
          mCursorDrawableRes.setAccessible(true);
          mCursorDrawableRes.setInt(editText, res);
        } catch (Throwable t) {
          Log.e("Cannot set cursor drawable", t);
        }
      }
    }
  }

  public static void clearCursorDrawable (android.widget.EditText editText) {
    setCursorDrawable(editText, 0);
  }

  public static boolean isValid (View view) {
    return view != null && view.getParent() != null && view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f;
  }

  public static boolean onTouchEvent (View view, MotionEvent e) {
    return (e.getAction() != MotionEvent.ACTION_DOWN || (Views.isValid(view) && Views.isValid((View) view.getParent())));
  }

  public static View tryFindAndroidView (Context context, Dialog dialog, String name) {
    if (dialog == null)
      return null;
    try {
      int titleId = context.getResources().getIdentifier(name, "id", "android");
      return titleId != 0 ? dialog.findViewById(titleId) : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void makeFakeBold (View view) {
    if (view instanceof TextView) {
      TextView textView = (TextView) view;
      if (Text.needFakeBold(textView.getText())) {
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setPaintFlags(textView.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
      }
    }
  }

  public static void invalidateChildren (ViewGroup group) {
    if (group != null) {
      int childCount = group.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = group.getChildAt(i);
        if (view != null) {
          view.invalidate();
        }
      }
    }
  }

  public static void moveView (View view, ViewGroup toGroup, int index) {
    if (view != null && view.getParent() != toGroup) {
      if (view.getParent() != null) {
        ((ViewGroup) view.getParent()).removeView(view);
      }
      toGroup.addView(view, index);
    }
  }

  public static FrameLayoutFix.LayoutParams newLayoutParams (View view, int gravity) {
    ViewGroup.LayoutParams oldParams = view.getLayoutParams();
    return FrameLayoutFix.newParams(oldParams.width, oldParams.height, gravity);
  }

  public static void setSimpleStateListAnimator (final View view) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      android.animation.StateListAnimator animator = new android.animation.StateListAnimator();

      final int fromZ = Screen.dp(1.5f);
      final int toZ = Screen.dp(3f);
      final int diffZ = toZ - fromZ;

      ValueAnimator obj;

      obj = AnimatorUtils.simpleValueAnimator();
      obj.setDuration(180l);
      obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      obj.addUpdateListener(animation -> {
        float z = fromZ + (float) diffZ * AnimatorUtils.getFraction(animation);
        if (z < view.getTranslationZ()) {
          view.setTranslationZ(z);
          if (view.getTag() instanceof View) {
            ((View) view.getTag()).setTranslationZ(z);
          }
        } else {
          if (view.getTag() instanceof View) {
            ((View) view.getTag()).setTranslationZ(z);
          }
          view.setTranslationZ(z);
        }
      });
      animator.addState(new int[] {android.R.attr.state_pressed}, obj);

      obj = AnimatorUtils.simpleValueAnimator();
      obj.setDuration(180l);
      obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      obj.addUpdateListener(animation -> {
        float z = toZ - (float) diffZ * AnimatorUtils.getFraction(animation);
        if (z < view.getTranslationZ()) {
          view.setTranslationZ(z);
          if (view.getTag() instanceof View) {
            ((View) view.getTag()).setTranslationZ(z);
          }
        } else {
          if (view.getTag() instanceof View) {
            ((View) view.getTag()).setTranslationZ(z);
          }
          view.setTranslationZ(z);
        }
      });

      animator.addState(new int[] {}, obj);

      view.setStateListAnimator(animator);
    }
  }

  public static void initCircleButton (final View view, final float size, final float padding) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setSimpleStateListAnimator(view);
      view.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          int p = Screen.dp(padding);
          int s = Screen.dp(size);
          outline.setOval(p, p, p + s, p + s);
        }
      });
    }
  }

  public static void initRectButton (final View view, final float size, final float padding) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setSimpleStateListAnimator(view);
      view.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          int p = Screen.dp(padding);
          outline.setRoundRect(p, p, view.getMeasuredWidth() - p, view.getMeasuredHeight() - p, Screen.dp(size));
        }
      });
    }
  }

  public static View simpleProgressView (Context context) {
    return simpleProgressView(context, FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER));
  }

  public static View simpleProgressView (Context context, ViewGroup.LayoutParams params) {
    View view;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      android.widget.ProgressBar bar = new android.widget.ProgressBar(context);
      bar.setIndeterminate(true);
      view = bar;
    } else {
      org.thunderdog.challegram.widget.SpinnerView spinnerView = new org.thunderdog.challegram.widget.SpinnerView(context);
      spinnerView.setImageResource(R.drawable.spinner_48_inner);
      view = spinnerView;
    }
    if (params != null) {
      view.setLayoutParams(params);
    }
    return view;
  }

  public static void destroyRecyclerView (RecyclerView recyclerView) {
    if (recyclerView == null || !(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
      return;
    }
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int last = manager.findLastVisibleItemPosition();

    if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
      for (int i = first; i <= last; i++) {
        View view = manager.findViewByPosition(i);
        if (view instanceof WebViewHolder) {
          ((WebViewHolder) view).destroyWebView();
        }
        if (view instanceof Destroyable) {
          ((Destroyable) view).performDestroy();
        } else if (view instanceof RecyclerView) {
          destroyRecyclerView((RecyclerView) view);
        }
      }
    }
    recyclerView.removeAllViewsInLayout();
  }

  public static boolean setGravity (View view, int gravity) {
    return view != null && setGravity((FrameLayout.LayoutParams) view.getLayoutParams(), gravity);
  }

  public static boolean setGravity (FrameLayoutFix.LayoutParams params, int gravity) {
    if (params.gravity != gravity) {
      params.gravity = gravity;
      return true;
    }
    return false;
  }

  public static void updateLayoutParams (View view) {
    if (view != null) {
      view.setLayoutParams(view.getLayoutParams());
    }
  }

  public static void removeRule (RelativeLayout.LayoutParams params, int verb) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      params.removeRule(verb);
    } else {
      params.addRule(verb, 0);
    }
  }

  public static boolean setMargins (View view, int left, int top, int right, int bottom) {
    return view != null && setMargins((ViewGroup.MarginLayoutParams) view.getLayoutParams(), left, top, right, bottom);
  }

  public static void translateMarginsToPadding (View view) {
    if (view != null) {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      if (params.leftMargin != 0 || params.topMargin != 0 || params.rightMargin != 0 || params.bottomMargin != 0) {
        view.setPadding(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin);
        params.width += params.leftMargin + params.rightMargin;
        params.height += params.topMargin + params.bottomMargin;
        params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = 0;
      }
    }
  }

  public static boolean setMargins (ViewGroup.MarginLayoutParams params, int left, int top, int right, int bottom) {
    if (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
      params.leftMargin = left;
      params.topMargin = top;
      params.rightMargin = right;
      params.bottomMargin = bottom;
      return true;
    }
    return false;
  }

  public static void setTopMargin (View view, int margin) {
    if (view != null) {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      if (params.topMargin != margin) {
        params.topMargin = margin;
        view.setLayoutParams(params);
      }
    }
  }

  public static void setBottomMargin (View view, int margin) {
    if (view != null) {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      if (params.bottomMargin != margin) {
        params.bottomMargin = margin;
        view.setLayoutParams(params);
      }
    }
  }

  public static void setRightMargin (View view, int margin) {
    if (view != null) {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      if (params.rightMargin != margin) {
        params.rightMargin = margin;
        view.setLayoutParams(params);
      }
    }
  }

  public static int getBottomMargin (View view) {
    if (view != null) {
      ViewGroup.LayoutParams params = view.getLayoutParams();
      if (params instanceof ViewGroup.MarginLayoutParams) {
        return ((ViewGroup.MarginLayoutParams) params).bottomMargin;
      }
    }
    return 0;
  }

  public static boolean isRtl (TextView textView) {
    final CharSequence text = textView.getText();
    return text != null && Strings.getTextDirection(text.toString(), 0, text.length()) == Strings.DIRECTION_RTL;
  }

  public static void setIsRtl (ImageView imageView, boolean isRtl) {
    imageView.setScaleX(isRtl ? -1f : 1f);
  }

  public static boolean setAlignParent (View view, boolean isRtl) {
    return setAlignParent(view, isRtl ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);
  }

  public static boolean setAlignParent (View view, int align) {
    if (view != null) {
      RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
      int reverseAlign = align == RelativeLayout.ALIGN_PARENT_RIGHT ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT;
      int[] rules = params.getRules();
      if (rules[align] != RelativeLayout.TRUE || rules[reverseAlign] == RelativeLayout.TRUE) {
        removeRule(params, reverseAlign);
        params.addRule(align);
        return true;
      }
    }
    return false;
  }

  public static void setTextGravity (TextView view, int gravity) {
    if (view != null && view.getGravity() != gravity) {
      view.setGravity(gravity);
    }
  }

  public static void setMediumText (TextView view, CharSequence text) {
    if (view != null) {
      updateMediumTypeface(view, text);
      view.setText(text);
    }
  }

  public static void updateMediumTypeface (TextView view, CharSequence text) {
    Typeface typeface = view.getTypeface();
    boolean fakeBold = Text.needFakeBold(text);

    Typeface newTypeface = fakeBold ? Fonts.getRobotoRegular() : Fonts.getRobotoMedium();
    if (newTypeface != typeface) {
      view.setTypeface(newTypeface);
    }

    int flags = view.getPaintFlags();
    int newFlags = BitwiseUtils.setFlag(flags, Paint.FAKE_BOLD_TEXT_FLAG, fakeBold);
    if (flags != newFlags) {
      view.setPaintFlags(newFlags);
    }
  }
}
