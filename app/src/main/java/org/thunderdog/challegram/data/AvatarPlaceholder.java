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
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
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
    public final @NonNull TdlibAccentColor accentColor;
    public final @ColorId int iconColorId;
    public final @Nullable Letters letters;
    public final @DrawableRes int drawableRes, extraDrawableRes;

    public Metadata () {
      this(new TdlibAccentColor(TdlibAccentColor.InternalId.INACTIVE));
    }

    public Metadata (@NonNull TdlibAccentColor accentColor) {
      this(accentColor, new Letters(Strings.ELLIPSIS), 0, 0);
    }

    public Metadata (@NonNull TdlibAccentColor accentColor, int iconRes) {
      this(accentColor, null, iconRes, 0);
    }

    public Metadata (@NonNull TdlibAccentColor accentColor, @Nullable Letters letters) {
      this(accentColor, letters, 0, 0);
    }

    public Metadata (@NonNull TdlibAccentColor accentColor, @Nullable Letters letters, int drawableRes, int extraDrawableRes) {
      this(accentColor, letters, drawableRes, extraDrawableRes, ColorId.avatar_content);
    }

    public Metadata (@NonNull TdlibAccentColor accentColor, @Nullable Letters letters, int drawableRes, int extraDrawableRes, @ColorId int iconColorId) {
      this.accentColor = accentColor;
      this.letters = letters != null && !StringUtils.isEmpty(letters.text) ? letters : null;
      this.drawableRes = drawableRes;
      this.extraDrawableRes = extraDrawableRes;
      this.iconColorId = iconColorId;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (obj instanceof Metadata) {
        Metadata other = (Metadata) obj;
        return other.accentColor.equals(this.accentColor) && StringUtils.equalsOrBothEmpty(
          other.letters != null ? other.letters.text : null,
          this.letters != null ? this.letters.text : null
        );
      }
      return false;
    }
  }

  private final float radius;
  private final Text letters;
  private final Drawable drawable;

  @NonNull
  public final Metadata metadata;

  public AvatarPlaceholder (float radius, @NonNull TdlibAccentColor accentColor) {
    this(radius, new Metadata(accentColor), null);
  }

  public AvatarPlaceholder (float radius, @Nullable Metadata metadata, @Nullable DrawableProvider provider) {
    if (metadata == null) {
      metadata = new AvatarPlaceholder.Metadata();
    }
    this.metadata = metadata;
    this.radius = radius;
    this.letters = metadata.letters == null ? null : new Text.Builder(metadata.letters.text, Screen.dp(radius) * 3, Paints.robotoStyleProvider((int) (radius * .75f)), TextColorSets.Regular.AVATAR_CONTENT).allBold().singleLine().build();
    if (provider != null) {
      this.drawable = provider.getSparseDrawable(metadata.drawableRes, ColorId.avatar_content);
    } else {
      if (metadata.drawableRes == R.drawable.baseline_bookmark_24) {
        this.drawable = Icons.getChatSelfDrawable();
      } else {
        this.drawable = Drawables.get(metadata.drawableRes);
      }
    }
  }

  public int getRadius () {
    return Screen.dp(radius);
  }

  public TdlibAccentColor getAccentColor () {
    return metadata.accentColor;
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
    if (drawCircle) {
      c.drawCircle(centerX, centerY, radiusPx, Paints.fillingPaint(ColorUtils.alphaColor(alpha, metadata.accentColor.getPrimaryColor())));
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
      Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2f, centerY - drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(metadata.iconColorId, alpha));
      if (needRestore) {
        Views.restore(c, saveCount);
      }
    }
  }
}
