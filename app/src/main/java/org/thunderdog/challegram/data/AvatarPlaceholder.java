/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/10/2019
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class AvatarPlaceholder {
  public static class Metadata {
    public final @ThemeColorId int colorId;
    public final @Nullable String letters;
    public final @DrawableRes int drawableRes, extraDrawableRes;

    public Metadata (int colorId, @Nullable Letters letters, int drawableRes, int extraDrawableRes) {
      this(colorId, letters != null ? letters.text : null, drawableRes, extraDrawableRes);
    }

    public Metadata () {
      this(R.id.theme_color_avatarInactive);
    }

    public Metadata (int colorId) {
      this(colorId, Strings.ELLIPSIS, 0, 0);
    }

    public Metadata (int colorId, int iconRes) {
      this(colorId, (Letters) null, iconRes, 0);
    }

    public Metadata (int colorId, @Nullable Letters letters) {
      this(colorId, letters, 0, 0);
    }

    public Metadata (int colorId, @Nullable String letters) {
      this(colorId, letters, 0, 0);
    }

    public Metadata (int colorId, @Nullable String letters, int drawableRes, int extraDrawableRes) {
      this.colorId = colorId;
      this.letters = letters;
      this.drawableRes = drawableRes;
      this.extraDrawableRes = extraDrawableRes;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof Metadata && ((Metadata) obj).colorId == colorId && StringUtils.equalsOrBothEmpty(((Metadata) obj).letters, letters) && ((Metadata) obj).colorId == colorId;
    }
  }

  private final float radius;
  private final Text letters;
  private final Drawable drawable;

  @NonNull
  public final Metadata metadata;

  public AvatarPlaceholder (float radius, @ThemeColorId int colorId) {
    this(radius, new Metadata(colorId), null);
  }

  public AvatarPlaceholder (float radius, @Nullable Metadata metadata, @Nullable DrawableProvider provider) {
    if (metadata == null) {
      metadata = new AvatarPlaceholder.Metadata();
    }
    this.metadata = metadata;
    this.radius = radius;
    this.letters = StringUtils.isEmpty(metadata.letters) ? null : new Text.Builder(metadata.letters, Screen.dp(radius) * 3, Paints.robotoStyleProvider((int) (radius * .75f)), TextColorSets.Regular.AVATAR_CONTENT).allBold().singleLine().build();
    if (provider != null) {
      this.drawable = provider.getSparseDrawable(metadata.drawableRes, R.id.theme_color_avatar_content);
    } else {
      switch (metadata.drawableRes) {
        case R.drawable.baseline_bookmark_24: {
          this.drawable = Icons.getChatSelfDrawable();
          break;
        }
        default: {
          this.drawable = Drawables.get(metadata.drawableRes);
          break;
        }
      }
    }
  }

  public int getRadius () {
    return Screen.dp(radius);
  }

  public int getColor () {
    return Theme.getColor(metadata.colorId);
  }

  public void draw (Canvas c, float centerX, float centerY) {
    draw(c, centerX, centerY, 1f, getRadius(), true);
  }

  public void draw (Canvas c, float centerX, float centerY, float alpha) {
    draw(c, centerX, centerY, alpha, getRadius(), true);
  }

  public void draw (Canvas c, float centerX, float centerY, float alpha, float radiusPx) {
    draw(c, centerX, centerY, alpha, radiusPx, true);
  }

  public void draw (Canvas c, float centerX, float centerY, float alpha, float radiusPx, boolean drawCircle) {
    if (alpha <= 0f)
      return;
    if (drawCircle && metadata.colorId != 0) {
      c.drawCircle(centerX, centerY, radiusPx, Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.getColor(metadata.colorId))));
    }
    if (letters != null) {
      int currentRadiusPx = Screen.dp(this.radius);
      float scale = radiusPx < currentRadiusPx ? radiusPx / (float) currentRadiusPx : 1f;
      scale *= Math.min(1f, (radiusPx * 2f) / (float) (Math.max(letters.getWidth(), letters.getHeight())));
      final boolean needRestore = scale != 1f;
      final int saveCount;
      if (needRestore) {
        saveCount = Views.save(c);
        c.scale(scale, scale, centerX, centerY);
      } else {
        saveCount = -1;
      }
      letters.draw(c, (int) (centerX - letters.getWidth() / 2),  (int) (centerY - letters.getHeight() / 2), null, alpha);
      if (needRestore) {
        Views.restore(c, saveCount);
      }
    } else if (drawable != null) {
      int currentRadiusPx = Screen.dp(this.radius);
      float scale = radiusPx < currentRadiusPx ? radiusPx / (float) currentRadiusPx : 1f;
      scale *= Math.min(1f, (radiusPx * 2f) / (float) Math.max(drawable.getMinimumWidth(), drawable.getMinimumHeight()));
      final boolean needRestore = scale != 1f;
      final int saveCount;
      if (needRestore) {
        saveCount = Views.save(c);
        c.scale(scale, scale, centerX, centerY);
      } else {
        saveCount = -1;
      }
      Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2f, centerY - drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(R.id.theme_color_avatar_content, alpha));
      if (needRestore) {
        Views.restore(c, saveCount);
      }
    }
  }
}
