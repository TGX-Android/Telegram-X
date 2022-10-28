package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.CircleButton;

import me.vkryl.android.widget.FrameLayoutFix;

public class CircleCounterBadgeView extends FrameLayout {
  public static final int BUTTON_WRAPPER_WIDTH = Screen.dp(118);
  public static final int BUTTON_PADDING = Screen.dp(24f);
  public static final int PADDING = Screen.dp(4);

  private final CircleButton circleButton;
  private final CounterBadgeView counterBadgeView;

  public CircleCounterBadgeView (ViewController<?> controller, int id, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
    super(controller.context());
    Context context = controller.context();

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(Screen.dp(BUTTON_WRAPPER_WIDTH), Screen.dp(74f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - PADDING;

    FrameLayoutFix.LayoutParams fParams = FrameLayoutFix.newParams(Screen.dp(24f) * 2 + PADDING * 2, Screen.dp(24f) * 2 + PADDING * 2, Gravity.RIGHT | Gravity.BOTTOM);
    FrameLayoutFix.LayoutParams fParams2 = FrameLayoutFix.newParams(BUTTON_PADDING + fParams.width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
    fParams2.bottomMargin = Screen.dp(24f) * 2 - Screen.dp(28f) / 2;

    setLayoutParams(params);

    counterBadgeView = new CounterBadgeView(context);
    counterBadgeView.setLayoutParams(fParams2);
    counterBadgeView.setPadding(BUTTON_PADDING, 0, 0, 0);

    circleButton = new CircleButton(context);
    circleButton.setId(id);
    if (onClickListener != null) {
      circleButton.setOnClickListener(onClickListener);
    }
    if (onLongClickListener != null) {
      circleButton.setOnLongClickListener(onLongClickListener);
    }
    circleButton.setTag(counterBadgeView);
    circleButton.setLayoutParams(fParams);

    controller.addThemeInvalidateListener(counterBadgeView);
    controller.addThemeInvalidateListener(circleButton);

    addView(circleButton);
    addView(counterBadgeView);
  }

  public void init (@DrawableRes int icon, float size, float padding, @ThemeColorId int backgroundColorId, @ThemeColorId int iconColorId) {
    circleButton.init(icon, size, padding, backgroundColorId, iconColorId);
  }

  public void setInProgress (boolean inProgress) {
    circleButton.setInProgress(inProgress);
  }
}
