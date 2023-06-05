/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.widget.FileProgressComponent;

import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.lambda.Destroyable;

public abstract class BaseComponent implements Destroyable {
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
