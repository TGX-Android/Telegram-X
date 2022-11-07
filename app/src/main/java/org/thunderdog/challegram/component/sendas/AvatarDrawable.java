package org.thunderdog.challegram.component.sendas;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

public class AvatarDrawable extends Drawable {

  private final boolean hasPhoto;
  private final @NonNull ImageReceiver receiver;
  private @Nullable AvatarPlaceholder placeholder;
  private final float radius;

  public AvatarDrawable (View view, float radius, @Nullable ImageFile photo, @Nullable AvatarPlaceholder.Metadata placeholderMetadata) {
    this.radius = radius;

    receiver = new ImageReceiver(view, Screen.dp(radius));
    if (view == null) {
      receiver.setUpdateListener(receiver -> {
        invalidateSelf();
      });
    }
    //receiver.setAnimationDisabled(true);
    receiver.setBounds(0, 0, Screen.dp(2 * radius), Screen.dp(2 * radius));

    hasPhoto = photo != null;
    if (hasPhoto) {
      receiver.requestFile(photo);
    } else {
      placeholder = new AvatarPlaceholder(radius, placeholderMetadata, null);
    }
  }

  @Override
  public void setBounds (int left, int top, int right, int bottom) {
    //android.util.Log.i("AD", String.format("setBounds to %d %d %d %d", left, top, right, bottom));
    super.setBounds(left, top, right, bottom);
    var centerX = (left + right) / 2;
    var centerY = (top + bottom) / 2;
    receiver.setBounds(centerX - Screen.dp(radius), centerY - Screen.dp(radius), centerX + Screen.dp(radius), centerY + Screen.dp(radius));
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(2 * radius);
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(2 * radius);
  }

  @Override
  public void draw (@NonNull Canvas canvas) {
    if (hasPhoto) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(canvas, Screen.dp(radius));
      }
      receiver.draw(canvas);
    } else if (placeholder != null) {
      placeholder.draw(canvas, receiver.centerX(), receiver.centerY());
    }/* else {
      canvas.drawCircle(receiver.centerX(), receiver.centerY(), RADIUS, );
    }*/
  }

  @Override
  public void setAlpha (int alpha) {}

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {}

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }
}
