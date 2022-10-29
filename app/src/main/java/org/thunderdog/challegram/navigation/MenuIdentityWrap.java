package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.thunderdog.challegram.component.chat.IdentityItemView;
import org.thunderdog.challegram.component.chat.MoreIdentitiesItemView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.ViewUtils;

public class MenuIdentityWrap extends MenuWrap {
  private static final float TRIANGLE_HEIGHT = 8;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Path trianglePath = new Path();
  private int globalTargetCenterX;
  private Consumer<View> onIdentitySelected;
  private Runnable onMoreSelected;

  public MenuIdentityWrap (Context context) {
    super(context);
  }

  @Override
  public void init (@Nullable ThemeListenerList themeProvider, ThemeDelegate forcedTheme) {
    super.init(themeProvider, forcedTheme);
    setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f) + Screen.dp(TRIANGLE_HEIGHT));
    setBackgroundColor(Color.TRANSPARENT);
  }

  @Override
  public int getItemsHeight () {
    return super.getItemsHeight() + Screen.dp(TRIANGLE_HEIGHT);
  }

  public void updateDirection () {
    if (Views.setGravity(this, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
      Views.updateLayoutParams(this);
  }

  public View addMoreButton (Tdlib tdlib, Runnable onMoreSelected) {
    this.onMoreSelected = onMoreSelected;
    MoreIdentitiesItemView moreButton = new MoreIdentitiesItemView(getContext(), tdlib);
    moreButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    moreButton.setPadding(Screen.dp(21f), 0, Screen.dp(21f), 0);
    RippleSupport.setTransparentSelector(moreButton);
    addView(moreButton, 0);
    moreButton.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    moreButton.setTag(moreButton.getMeasuredWidth());
    return moreButton;
  }

  public View addItem (Tdlib tdlib, int id, Identity identity, Consumer<View> onIdentitySelected) {
    this.onIdentitySelected = onIdentitySelected;
    IdentityItemView menuItem = new IdentityItemView(getContext(), tdlib);
    menuItem.setId(id);
    menuItem.setIdentity(identity);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    menuItem.setPadding(Screen.dp(21f), 0, Screen.dp(21f), 0);
    RippleSupport.setTransparentSelector(menuItem);
    addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }

  public void setTarget (View target) {
    int targetWidth = target.getMeasuredWidth();
    int[] out = Views.getLocationInWindow(target);
    this.globalTargetCenterX = out[0] + targetWidth / 2;
  }

  public boolean processTouchEvent (MotionEvent e) {
    boolean shouldClose = touchIsFarFromMenu(e);
    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        handleActionMove(e);
        break;
      }
      case MotionEvent.ACTION_UP: {
        handleActionUp(e);
        shouldClose = true;
        break;
      }
    }
    return shouldClose;
  }

  private void handleActionMove (MotionEvent e) {
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v instanceof IdentityItemView) {
        IdentityItemView identityItemView = (IdentityItemView) v;
        if (identityItemView.getIdentity().isLocked()) {
          continue;
        }
      }
      Drawable background = v.getBackground();
      if (Build.VERSION.SDK_INT >= 21 && background instanceof RippleDrawable) {
        RippleDrawable rippleDrawable = (RippleDrawable) background;
        if (isTouchedView(v, e)) {
          rippleDrawable.setState(new int[] { android.R.attr.state_focused });
        } else {
          rippleDrawable.setState(new int[] {});
        }
      } else {
        if (isTouchedView(v, e)) {
          v.setBackgroundColor(Theme.pressedFillingColor());
        } else {
          v.setBackgroundColor(Color.TRANSPARENT);
        }
      }
    }
  }

  private void handleActionUp(MotionEvent e) {
    View touchedView =  getSelectedViewByTouch(e);
    if (touchedView != null) {
      if (touchedView instanceof IdentityItemView) {
        IdentityItemView identityItemView = (IdentityItemView) touchedView;
        if (identityItemView.getIdentity().isLocked()) {
          return;
        }
      }
      Drawable background = touchedView.getBackground();
      if (Build.VERSION.SDK_INT >= 21 && background instanceof RippleDrawable) {
        RippleDrawable rippleDrawable = (RippleDrawable) background;
        if (isTouchedView(touchedView, e)) {
          rippleDrawable.setState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled });
          if (touchedView instanceof IdentityItemView) {
            onIdentitySelected.accept(touchedView);
          } else if (touchedView instanceof MoreIdentitiesItemView) {
            onMoreSelected.run();
          }
        } else {
          rippleDrawable.setState(new int[] {});
        }
      } else {
        if (isTouchedView(touchedView, e)) {
          touchedView.setBackgroundColor(Theme.pressedFillingColor());
        } else {
          touchedView.setBackgroundColor(Color.TRANSPARENT);
        }
      }
    }
  }

  private View getSelectedViewByTouch (MotionEvent e) {
    float touchLocalX = e.getRawX() - getX();
    float touchLocalY = e.getRawY() - getY();
    if (touchLocalX < 0 || touchLocalX > getMeasuredWidth()) {
      return null;
    }
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      float startY = v.getY();
      float endY = startY + v.getMeasuredHeight();
      if (touchLocalY >= startY && touchLocalY <= endY) {
        return v;
      }
    }
    return null;
  }

  private boolean isTouchedView (View view, MotionEvent e) {
    float touchLocalX = e.getRawX() - getX();
    float touchLocalY = e.getRawY() - getY();
    if (touchLocalX < 0 || touchLocalX > getMeasuredWidth()) {
      return false;
    }
    float startY = view.getY();
    float endY = startY + view.getMeasuredHeight();
    return touchLocalY >= startY && touchLocalY <= endY;
  }

  private boolean touchIsFarFromMenu (MotionEvent e) {
    float touchLocalX = e.getRawX() - getX();
    float touchLocalY = e.getRawY() - getY();
    if (touchLocalX < 0 || touchLocalX > getMeasuredWidth()) {
      return true;
    }
    if (touchLocalY < 0) {
      return true;
    }
    return false;
  }

  @Override
  public void onDraw (Canvas c) {
    super.onDraw(c);

    trianglePath.setFillType(Path.FillType.EVEN_ODD);
    paint.setColor(Theme.fillingColor());
    paint.setStyle(Paint.Style.FILL);

    RectF backgroundRect = new RectF(
      Screen.dp(8f),
      Screen.dp(8f),
      getMeasuredWidth() - Screen.dp(8f),
      getMeasuredHeight() - Screen.dp(8f) - Screen.dp(TRIANGLE_HEIGHT));
    c.drawRoundRect(backgroundRect, Screen.dp(2f), Screen.dp(2f), paint);

    int thisGlobalX = (int) getX();
    int targetCenterX = globalTargetCenterX - thisGlobalX;
    trianglePath.moveTo(targetCenterX - Screen.dp(8f), getMeasuredHeight() - Screen.dp(8f) - Screen.dp(TRIANGLE_HEIGHT));
    trianglePath.lineTo(targetCenterX + Screen.dp(8f), getMeasuredHeight() - Screen.dp(8f) - Screen.dp(TRIANGLE_HEIGHT));
    trianglePath.lineTo(targetCenterX, getMeasuredHeight() - Screen.dp(8f));
    trianglePath.close();

    c.drawPath(trianglePath, paint);
  }
}
