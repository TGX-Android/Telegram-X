/**
 * File created on 25/04/15 at 09:10
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.util.StateSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiDrawable;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.util.CustomStateListDrawable;

// FIXME memory usage for all vector drawings
public class Drawables {
  public static final int[] STATE_PRESSED = {android.R.attr.state_pressed};
  public static final int[] STATE_SELECTED = {android.R.attr.state_selected};
  public static final int[] STATE_ACTIVATED = {android.R.attr.state_activated};

  public static Drawable createRoundDrawable (float rad, int color) {
    ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[] {rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
    defaultDrawable.getPaint().setColor(color);
    return defaultDrawable;
  }

  public static boolean needMirror (@DrawableRes int resId) {
    if (!Lang.rtl())
      return false;
    switch (resId) {
      case R.drawable.baseline_forward_24:
      case R.drawable.baseline_content_copy_24:
      case R.drawable.baseline_reply_24:
      case R.drawable.baseline_share_24:
      case R.drawable.baseline_arrow_forward_24:
      case R.drawable.deproko_baseline_send_24:
        return true;
    }
    return false;
  }

  public static Drawable getColorSelector (Drawable defaultDrawable, Drawable pressedDrawable) {
    CustomStateListDrawable drawable = new CustomStateListDrawable();

    drawable.addState(STATE_PRESSED, pressedDrawable);
    drawable.addState(STATE_SELECTED, pressedDrawable);
    drawable.addState(STATE_ACTIVATED, pressedDrawable);
    if (defaultDrawable != null) {
      drawable.addState(StateSet.WILD_CARD, defaultDrawable);
    }

    /*if (Build.VERSION.SDK_INT >= 11 && useExitFade)
      drawable.setExitFadeDuration(EXIT_FADE_DURATION);*/

    return drawable;
  }

  public static void setAlpha (Drawable d, int alpha) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (d.getAlpha() != alpha) {
        d.setAlpha(alpha);
      }
    } else {
      d.setAlpha(alpha);
    }
  }

  public static void drawRtl (Canvas c, Drawable d, float x, float y, Paint paint, int viewWidth, boolean rtl) {
    if (rtl) {
      x = viewWidth - x - d.getMinimumWidth();
      draw(c, d, x, y, paint);
    } else {
      draw(c, d, x, y, paint);
    }
  }

  public static void prepare (Drawable d, @Nullable Paint paint) {
    if (paint == null)
      return;
    int alpha = paint.getAlpha();
    ColorFilter filter = paint.getColorFilter();

    if (d instanceof BitmapDrawable) {
      BitmapDrawable b = (BitmapDrawable) d;
      if (b.getPaint().getColorFilter() != filter) {
        d.setColorFilter(filter);
      }
      b.setAlpha(alpha);
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        if (filter != d.getColorFilter()) {
          d.setColorFilter(filter);
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        if (alpha != d.getAlpha()) {
          d.setAlpha(alpha);
        }
      }
    }
  }

  public static void drawCentered (Canvas c, Drawable d, float cx, float cy, @Nullable Paint paint) {
    draw(c, d, cx - d.getMinimumWidth() / 2f, cy - d.getMinimumHeight() / 2f, paint);
  }

  public static void draw (Canvas c, Drawable d, float x, float y, @Nullable Paint paint) {
    if (d == null)
      return;
    Rect bounds = d.getBounds();
    int minWidth = d.getMinimumWidth();
    int minHeight = d.getMinimumHeight();
    if (bounds.top != 0 || bounds.left != 0 || bounds.right != minWidth || bounds.bottom != minHeight) {
      d.setBounds(0, 0, minWidth, minHeight);
    }
    prepare(d, paint);
    final int saveCount;
    final boolean needRestore = x != 0 || y != 0;
    if (needRestore) {
      saveCount = Views.save(c);
      c.translate(x, y);
    } else {
      saveCount = -1;
    }
    d.draw(c);
    if (needRestore) {
      Views.restore(c, saveCount);
    }
  }

  public static Drawable emojiDrawable (String emoji) {
    EmojiInfo info = Emoji.instance().getEmojiInfo(emoji);
    if (info == null)
      return null;
    return new EmojiDrawable(info);
  }

  public static Drawable bitmapDrawable (Context context, Bitmap bitmap) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return new BitmapDrawable(context.getResources(), bitmap);
    } else {
      return new BitmapDrawable(bitmap);
    }
  }

  public static Drawable get (int res) {
    return get(UI.getResources(), res);
  }

  @SuppressWarnings ("deprecation")
  public static Drawable get (Resources resources, int res) {
    Drawable d = load(resources, res);
    return d != null ? d.mutate() : null;
  }

  public static Bitmap toBitmap (Drawable d) {
    if (d instanceof BitmapDrawable) {
      return ((BitmapDrawable) d).getBitmap();
    }
    Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    d.setBounds(0, 0, c.getWidth(), c.getHeight());
    d.draw(c);
    c.setBitmap(null);
    return bitmap;
  }

  public static Drawable load (int res) {
    return load(UI.getResources(), res);
  }

  public static Drawable load (Resources resources, int res) {
    if (res == 0)
      return null;
    Drawable drawable = ResourcesCompat.getDrawable(resources, res, null);
    if (drawable == null)
      throw new Resources.NotFoundException("res == " + res);
    return drawable;
  }

  public static Bitmap getBitmap (int res) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Drawable drawable = get(res);
      Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
      drawable.draw(canvas);
      return bitmap;
    } else {
      return BitmapFactory.decodeResource(UI.getAppContext().getResources(), res);
    }
  }

  // Custom drawable utilities

  public static String getAuthorForResource (@DrawableRes int resId) {
    switch (resId) {


      case R.drawable.templarian_baseline_broom_24:
      case R.drawable.templarian_baseline_calculator_18:
      case R.drawable.templarian_baseline_foursquare_24:
      case R.drawable.templarian_baseline_gamepad_variant_16:
        return "templarian";

      case R.drawable.mrgrigri_baseline_textbox_password_24:
        return "mrgrigri";

      case R.drawable.itsspelledhaley_baseline_lock_pattern_24:
        return "japanyoshilol";

      case R.drawable.ivanliana_baseline_video_collections_16:
      case R.drawable.ivanliana_baseline_audio_collections_16:
      case R.drawable.ivanliana_baseline_file_collections_16:
        return "ivanliana";

      case R.drawable.vkryl_baseline_lock_pin_24:
        return "vkryl";

      case R.drawable.logo_dailymotion:
        return "dailymotion";

      case R.drawable.logo_vimeo:
        return "Vimeo";

      case R.drawable.logo_youtube:
      case R.drawable.logo_youtube_tube:
        return "YouTube";
    }
    return null;
  }
}
