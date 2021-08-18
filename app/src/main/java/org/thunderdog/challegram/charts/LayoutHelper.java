package org.thunderdog.challegram.charts;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import org.thunderdog.challegram.tool.Screen;

@SuppressWarnings({"unused", "WeakerAccess"})
public class LayoutHelper {

  public static final int MATCH_PARENT = -1;
  public static final int WRAP_CONTENT = -2;

  private static int getSize(float size) {
    return (int) (size < 0 ? size : Screen.dp(size));
  }

  //endregion

  //region ScrollView

  public static ScrollView.LayoutParams createScroll(int width, int height, int gravity) {
    return new ScrollView.LayoutParams(getSize(width), getSize(height), gravity);
  }

  public static ScrollView.LayoutParams createScroll(int width, int height, int gravity, float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
    ScrollView.LayoutParams layoutParams = new ScrollView.LayoutParams(getSize(width), getSize(height), gravity);
    layoutParams.leftMargin = Screen.dp(leftMargin);
    layoutParams.topMargin = Screen.dp(topMargin);
    layoutParams.rightMargin = Screen.dp(rightMargin);
    layoutParams.bottomMargin = Screen.dp(bottomMargin);
    return layoutParams;
  }

  //endregion

  //region FrameLayout

  public static FrameLayout.LayoutParams createFrame(int width, float height, int gravity, float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
    layoutParams.setMargins(Screen.dp(leftMargin), Screen.dp(topMargin), Screen.dp(rightMargin), Screen.dp(bottomMargin));
    return layoutParams;
  }

  public static FrameLayout.LayoutParams createFrame(int width, int height, int gravity) {
    return new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
  }

  public static FrameLayout.LayoutParams createFrame(int width, float height) {
    return new FrameLayout.LayoutParams(getSize(width), getSize(height));
  }

  public static FrameLayout.LayoutParams createFrame(float width, float height, int gravity) {
    return new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
  }

  //endregion

  //region RelativeLayout

  public static RelativeLayout.LayoutParams createRelative(float width, float height, int leftMargin, int topMargin, int rightMargin, int bottomMargin, int alignParent, int alignRelative, int anchorRelative) {
    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(getSize(width), getSize(height));
    if (alignParent >= 0) {
      layoutParams.addRule(alignParent);
    }
    if (alignRelative >= 0 && anchorRelative >= 0) {
      layoutParams.addRule(alignRelative, anchorRelative);
    }
    layoutParams.leftMargin = Screen.dp(leftMargin);
    layoutParams.topMargin = Screen.dp(topMargin);
    layoutParams.rightMargin = Screen.dp(rightMargin);
    layoutParams.bottomMargin = Screen.dp(bottomMargin);
    return layoutParams;
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height, int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
    return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, -1, -1, -1);
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height, int leftMargin, int topMargin, int rightMargin, int bottomMargin, int alignParent) {
    return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, alignParent, -1, -1);
  }

  public static RelativeLayout.LayoutParams createRelative(float width, float height, int leftMargin, int topMargin, int rightMargin, int bottomMargin, int alignRelative, int anchorRelative) {
    return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, -1, alignRelative, anchorRelative);
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height, int alignParent, int alignRelative, int anchorRelative) {
    return createRelative(width, height, 0, 0, 0, 0, alignParent, alignRelative, anchorRelative);
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height) {
    return createRelative(width, height, 0, 0, 0, 0, -1, -1, -1);
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height, int alignParent) {
    return createRelative(width, height, 0, 0, 0, 0, alignParent, -1, -1);
  }

  public static RelativeLayout.LayoutParams createRelative(int width, int height, int alignRelative, int anchorRelative) {
    return createRelative(width, height, 0, 0, 0, 0, -1, alignRelative, anchorRelative);
  }

  //endregion

  //region LinearLayout

  public static LinearLayout.LayoutParams createLinear(int width, int height, float weight, int gravity, int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height), weight);
    layoutParams.setMargins(Screen.dp(leftMargin), Screen.dp(topMargin), Screen.dp(rightMargin), Screen.dp(bottomMargin));
    layoutParams.gravity = gravity;
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, float weight, int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height), weight);
    layoutParams.setMargins(Screen.dp(leftMargin), Screen.dp(topMargin), Screen.dp(rightMargin), Screen.dp(bottomMargin));
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, int gravity, int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height));
    layoutParams.setMargins(Screen.dp(leftMargin), Screen.dp(topMargin), Screen.dp(rightMargin), Screen.dp(bottomMargin));
    layoutParams.gravity = gravity;
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height));
    layoutParams.setMargins(Screen.dp(leftMargin), Screen.dp(topMargin), Screen.dp(rightMargin), Screen.dp(bottomMargin));
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, float weight, int gravity) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height), weight);
    layoutParams.gravity = gravity;
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, int gravity) {
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(getSize(width), getSize(height));
    layoutParams.gravity = gravity;
    return layoutParams;
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height, float weight) {
    return new LinearLayout.LayoutParams(getSize(width), getSize(height), weight);
  }

  public static LinearLayout.LayoutParams createLinear(int width, int height) {
    return new LinearLayout.LayoutParams(getSize(width), getSize(height));
  }

  //endregion
}
