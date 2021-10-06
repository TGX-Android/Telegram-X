package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.widget.FileProgressComponent;

import me.vkryl.android.util.ViewProvider;

public abstract class BaseComponent {
  protected @Nullable ViewProvider viewProvider;

  public void setViewProvider (@Nullable ViewProvider provider) {
    this.viewProvider = provider;
  }

  abstract public <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, @ColorInt int backgroundColor, int contentReplaceColor, float alpha, float checkFactor);

  abstract public void buildLayout (int maxWidth);

  abstract public void requestPreview (DoubleImageReceiver receiver);

  abstract public void requestContent (ImageReceiver receiver);

  abstract public int getHeight ();

  abstract public int getWidth ();

  abstract public int getContentRadius (int defaultValue);

  abstract public boolean onTouchEvent (View view, MotionEvent event);

  public @Nullable TdApi.File getFile () {
    return null;
  }

  public @Nullable FileProgressComponent getFileProgress () {
    return null;
  }
}
