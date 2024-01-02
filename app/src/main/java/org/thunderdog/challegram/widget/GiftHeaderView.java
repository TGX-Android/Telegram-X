package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessageGiveawayBase;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;

import kotlin.random.Random;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class GiftHeaderView extends View {
  private final GiftHeaderView.ParticlesDrawable particlesDrawable;
  private TGMessageGiveawayBase.Content content;
  private int contentY;

  private @StringRes int headerRes = R.string.GiftLink;
  private @StringRes int descriptionRes = R.string.GiftLinkDesc;

  public GiftHeaderView (Context context) {
    super(context);
    this.particlesDrawable = new ParticlesDrawable();
  }

  public void setTexts (@StringRes int headerRes, @StringRes int descriptionRes) {
    if (this.headerRes != headerRes || this.descriptionRes != descriptionRes) {
      this.headerRes = headerRes;
      this.descriptionRes = descriptionRes;
      if (getMeasuredWidth() > 0) {
        buildContent();
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    particlesDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    buildContent();
  }

  private void buildContent () {
    content = new TGMessageGiveawayBase.Content(getMeasuredWidth() - Screen.dp(120));
    content.padding(Screen.dp(10));
    content.add(new TGMessageGiveawayBase.ContentDrawable(R.drawable.baseline_gift_72));
    content.padding(Screen.dp(22));
    content.add(new TextWrapper(Lang.getString(headerRes), getHeaderStyleProvider(), () -> Theme.getColor(ColorId.text)).setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true));
    content.padding(Screen.dp(8));
    content.add(new TextWrapper(null, TD.toFormattedText(Lang.getMarkdownString(null, descriptionRes), false), getTextStyleProvider(), () -> Theme.getColor(ColorId.text), null, null).setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true));
    contentY = (getMeasuredHeight() - content.getHeight()) / 2;
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
    particlesDrawable.draw(canvas);

    content.draw(canvas, null, Screen.dp(60), contentY);
  }

  public static int getDefaultHeight () {
    return Screen.dp(231);
  }

  public static class ParticlesDrawable extends Drawable {
    private Particle[] particles;
    private int width;
    private int height;

    public ParticlesDrawable () {
      this(0, 0);
    }

    public ParticlesDrawable (int width, int height) {
      if (particle1 == null || particle2 == null) {
        particle1 = Drawables.get(R.drawable.giveaway_particle_1);
        particle2 = Drawables.get(R.drawable.giveaway_particle_2);
      }
      setSize(width, height);
    }

    @Override
    protected void onBoundsChange (@NonNull Rect bounds) {
      super.onBoundsChange(bounds);
      setSize(bounds.width(), bounds.height());
    }

    private void setSize (int width, int height) {
      if (this.width == width && this.height == height) {
        return;
      }

      this.width = width;
      this.height = height;

      int particlesCount = (int) (Screen.px(width) * Screen.px(height) / 4000f);
      particles = new Particle[particlesCount];
      for (int a = 0; a < particlesCount; a++) {
        particles[a] = new Particle(
          MathUtils.random(0, 3),
          particleColors[MathUtils.random(0, 5)],
          MathUtils.random(0, width),
          MathUtils.random(0, height),
          0.75f + Random.Default.nextFloat() * 0.5f,
          Random.Default.nextFloat() * 360f
        );
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      final float radius = Screen.dp(3.5f);
      for (Particle particle : particles) {
        c.save();
        c.scale(1.5f * particle.scale, 1.5f * particle.scale, particle.x, particle.y);
        c.rotate(particle.angle, particle.x, particle.y);

        final int color = ColorUtils.alphaColor(0.4f, Theme.getColor(particle.color));
        if (particle.type == 0) {
          c.drawCircle(particle.x, particle.y, radius, Paints.fillingPaint(color));
        } else if (particle.type == 1) {
          c.drawRect(
            particle.x - radius, particle.y - radius,
            particle.x + radius, particle.y + radius,
            Paints.fillingPaint(color));
        } else if (particle.type == 2) {
          Drawables.drawCentered(c, particle1, particle.x, particle.y, Paints.getPorterDuffPaint(color));
        }  else if (particle.type == 3) {
          Drawables.drawCentered(c, particle2, particle.x, particle.y, Paints.getPorterDuffPaint(color));
        }

        c.restore();
      }
    }

    @Override
    public void setAlpha (int alpha) {

    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }

    private static Drawable particle1;
    private static Drawable particle2;

    private static final int[] particleColors = new int[]{
      ColorId.confettiGreen,
      ColorId.confettiBlue,
      ColorId.confettiYellow,
      ColorId.confettiRed,
      ColorId.confettiCyan,
      ColorId.confettiPurple
    };

    private static class Particle {
      public final int type;
      public final int color;
      public final float scale;
      public final float angle;
      public final int x;
      public final int y;

      public Particle (int type, int color, int x, int y, float scale, float angle) {
        this.type = type;
        this.color = color;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.angle = angle;
      }
    }
  }


  private static TextStyleProvider headerStyleProvider;

  protected static TextStyleProvider getHeaderStyleProvider () {
    if (headerStyleProvider == null) {
      TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      tp.setTypeface(Fonts.getRobotoMedium());
      headerStyleProvider = new TextStyleProvider(tp).setTextSize(20f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(headerStyleProvider);
    }
    return headerStyleProvider;
  }


  private static TextStyleProvider textStyleProvider;

  protected static TextStyleProvider getTextStyleProvider () {
    if (textStyleProvider == null) {

      textStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(textStyleProvider);
    }
    return textStyleProvider;
  }
}
