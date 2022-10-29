package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.BaseView;

public class MoreIdentitiesItemView extends BaseView {
  private static final float RADIUS = 10f;
  private static final float LOCK_ICON_RADIUS = 7f;
  private static final float TEXT_PADDING_X = 20f;

  private Text text;
  private Drawable icon;

  public MoreIdentitiesItemView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);

    icon = Drawables.get(R.drawable.baseline_more_horiz_24);
    DrawableCompat.setTint(icon, Theme.iconColor());
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int radius = Screen.dp(RADIUS);
    final int centerY = getMeasuredHeight() / 2;
    icon.setBounds(
      getPaddingLeft(),
      centerY - radius,
      getPaddingLeft() + radius * 2,
      centerY + radius
    );
    buildText();
  }

  private void buildText () {
    final int totalPaddingLeft = getPaddingLeft() + Screen.dp(RADIUS * 2) + Screen.dp(TEXT_PADDING_X);
    final int totalPaddingRight = getPaddingRight() + Screen.dp(TEXT_PADDING_X) + Screen.dp(LOCK_ICON_RADIUS * 2);
    final int availWidth = getMeasuredWidth() - totalPaddingLeft - totalPaddingRight;
    text = new Text.Builder(
      Lang.getString(R.string.MoreIdentities), availWidth, Paints.robotoStyleProvider(16), Theme::textAccentColor
    ).singleLine().build();
  }

  @Override
  protected void onDraw (Canvas c) {
    icon.draw(c);

    TextPaint paint = Paints.getSmallTitlePaint();;
    final int sourceColor = paint.getColor();
    int startX = getPaddingLeft() + Screen.dp(RADIUS * 2) + Screen.dp(TEXT_PADDING_X);
    int startY = getMeasuredHeight() / 2 - text.getHeight() / 2;
    text.draw(c, startX, startY);
    paint.setColor(sourceColor);
  }
}
