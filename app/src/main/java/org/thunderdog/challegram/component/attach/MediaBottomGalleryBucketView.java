/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class MediaBottomGalleryBucketView extends FrameLayoutFix implements Destroyable {
  private final ImageReceiver receiver;
  private final TextView textView;

  private Media.GalleryBucket bucket;

  public MediaBottomGalleryBucketView (Context context) {
    super(context);

    int paddingTop = Screen.dp(9f);
    int paddingLeft = Screen.dp(8f);
    int imageSize = Screen.dp(30f);

    receiver = new ImageReceiver(this, 0);
    receiver.setBounds(paddingLeft, paddingTop, paddingLeft + imageSize, paddingTop + imageSize);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
    params.leftMargin = paddingLeft + imageSize + Screen.dp(17f);
    params.rightMargin = paddingLeft;
    textView = new NoScrollTextView(context);
    textView.setTextColor(Theme.textAccentColor());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setSingleLine(true);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setLayoutParams(params);
    addView(textView);

    setWillNotDraw(false);
    Views.setClickable(this);
    RippleSupport.setSimpleWhiteBackground(this);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, paddingTop + imageSize + paddingTop));
  }

  public Media.GalleryBucket getBucket () {
    return bucket;
  }

  public void setBucket (Media.GalleryBucket bucket) {
    if (this.bucket == null || this.bucket.getId() != bucket.getId()) {
      this.bucket = bucket;
      textView.setText(bucket.getName());
      receiver.requestFile(bucket.getPreviewImage());
    }
  }

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (receiver.needPlaceholder()) {
      c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.getPlaceholderPaint());
    }
    receiver.draw(c);
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
  }
}
