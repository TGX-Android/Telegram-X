package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Views;

public class ClippingSubsamplingImageView extends SubsamplingScaleImageView {
  private float imageWidth, imageHeight;
  private final RectF clip = new RectF();
  private float topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius;

  public ClippingSubsamplingImageView (Context context) {
    super(context);
  }

  public void setClipping (float imageWidth, float imageHeight,
                           float clipLeft, float clipTop, float clipRight, float clipBottom,
                           float topLeftRadius, float topRightRadius,
                           float bottomRightRadius, float bottomLeftRadius) {
    if (this.imageWidth != imageWidth || this.imageHeight != imageHeight ||
      U.setRect(clip, clipLeft, clipTop, clipRight, clipBottom) ||
      this.topLeftRadius != topLeftRadius || this.topRightRadius != topRightRadius ||
      this.bottomRightRadius != bottomRightRadius || this.bottomLeftRadius != bottomLeftRadius) {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.topLeftRadius = topLeftRadius;
      this.topRightRadius = topRightRadius;
      this.bottomRightRadius = bottomRightRadius;
      this.bottomLeftRadius = bottomLeftRadius;
      invalidate();
    }
  }

  public void resetClipping () {
    setClipping(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private final Path path = new Path();

  @Override
  public void onDraw (Canvas canvas) {
    if (imageWidth == 0 || imageHeight == 0 || (clip.left == 0 && clip.top == 0 && clip.right == 0 && clip.bottom == 0 && topLeftRadius == 0 && topRightRadius == 0 && bottomRightRadius == 0 && bottomLeftRadius == 0)) {
      super.onDraw(canvas);
      return;
    }
    float centerX = getMeasuredWidth() / 2f;
    float centerY = getMeasuredHeight() / 2f;
    int saveCount = Views.save(canvas);
    RectF rectF = Paints.getRectF();
    rectF.set(
      centerX - imageWidth / 2f + clip.left,
      centerY - imageHeight / 2f + clip.top,
      centerX + imageWidth / 2f - clip.right,
      centerY + imageHeight / 2f - clip.bottom
    );
    canvas.clipRect(rectF);
    if (topLeftRadius != 0 || topRightRadius != 0 || bottomRightRadius != 0 || bottomLeftRadius != 0) {
      int imageWidth = getSWidth();
      int imageHeight = getSHeight();
      if (U.isRotated(getOrientation())) {
        int temp = imageHeight;
        imageHeight = imageWidth;
        imageWidth = temp;
      }
      float scale = Math.min(
        (float) getMeasuredWidth() / (float) imageWidth,
        (float) getMeasuredHeight() / (float) imageHeight
      );
      imageWidth *= scale;
      imageHeight *= scale;
      if (imageWidth > 0 && imageHeight > 0) {
        rectF.left = Math.max(rectF.left, centerX - imageWidth / 2f);
        rectF.top = Math.max(rectF.top, centerY - imageHeight / 2f);
        rectF.right = Math.min(rectF.right, centerX + imageWidth / 2f);
        rectF.bottom = Math.min(rectF.bottom, centerY + imageHeight / 2f);
      }

      path.reset();
      DrawAlgorithms.buildPath(path, rectF, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
      ViewSupport.clipPath(canvas, path);
    }
    super.onDraw(canvas);

    Views.restore(canvas, saveCount);
  }
}
