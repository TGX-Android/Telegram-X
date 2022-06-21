package org.thunderdog.challegram.component.payments;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Fonts;

public class PaymentPricePartView extends FrameLayout implements ThemeInvalidateListener {
  private final TextView title, summary;
  private boolean isBold;

  public PaymentPricePartView (Context context) {
    super(context);

    title = new TextView(context);
    title.setTextSize(15);
    addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

    summary = new TextView(context);
    summary.setTextSize(13);
    summary.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    addView(summary, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

    recolor();
  }

  public void setData (PartData data) {
    final Typeface font = data.bold ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular();
    this.isBold = data.bold;

    title.setTypeface(font);
    title.setText(data.label);
    title.setTextColor(isBold ? Theme.textAccentColor() : Theme.textDecentColor());

    summary.setTypeface(font);
    summary.setText(data.amount);
  }

  private void recolor () {
    title.setTextColor(isBold ? Theme.textAccentColor() : Theme.textDecentColor());
    summary.setTextColor(Theme.textAccentColor());
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
    invalidate();
  }

  public static class PartData {
    public final String label;
    public final String amount;
    public final boolean bold;

    public PartData (String label, String amount, boolean bold) {
      this.label = label;
      this.amount = amount;
      this.bold = bold;
    }
  }
}
