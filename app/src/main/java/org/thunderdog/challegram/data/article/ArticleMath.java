/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.LruCache;
import androidx.annotation.Nullable;
import ru.noties.jlatexmath.JLatexMathAndroid;
import ru.noties.jlatexmath.JLatexMathDrawable;

/** Alpha masks follow the active theme without reparsing TeX on each draw. */
public final class ArticleMath {
  private ArticleMath () { }
  private static boolean initialized;
  private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
    @Override protected int sizeOf (String key, Bitmap value) { return value.getByteCount(); }
  };

  public static synchronized @Nullable Bitmap render (Context context, String source, float textSize) {
    if (source == null || source.isEmpty() || source.length() > 32768) return null;
    int nesting = 0;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{' && ++nesting > 128) return null;
      if (c == '}') nesting--;
    }
    String key = textSize + ":" + source;
    Bitmap image = cache.get(key);
    if (image != null) return image;
    if (!initialized) {
      JLatexMathAndroid.init(context.getApplicationContext());
      initialized = true;
    }
    try {
      JLatexMathDrawable drawable = JLatexMathDrawable.builder(source).textSize(textSize).build();
      int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
      if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 2 * 1024 * 1024) return null;
      image = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
      drawable.setBounds(0, 0, width, height);
      drawable.draw(new Canvas(image));
      cache.put(key, image);
      return image;
    } catch (RuntimeException | StackOverflowError e) {
      // Invalid TeX remains visible as source text; never discard the block.
      return null;
    }
  }
}
