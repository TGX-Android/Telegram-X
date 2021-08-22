package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibWallpaperManager;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 01/04/2017
 * Author: default
 */

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.ViewHolder> implements View.OnClickListener, ChatStyleChangeListener {
  private static final int[] SOLID_COLORS = {
    0x1F2327,
    0x2D2033,
    0x882B41,
    0x604D52,
    0x5A7D7F,
    0x57626F,
    0x395586,
    0x478FB7,
    0x76BBCC,
    0x7ECFD4,
    0xC2E3E4,
    0xB5D7C9,
    0x8BC7A8,
    0xFDEFB1,
    0xFFFDAD,
    0xEB566A,
    0xEE706E,
    0xF19F9E,
    0xD07356,
    0xF8D29E,
    0xE5E0B6,
    0xD3D9C1,
    0xE7E8D4,
    0xCDDAEB,
    0xD4D1ED,
    0xCECECE,
    0xEDF0F1
  };
  
  public static final int TYPE_WALLPAPER = 0;
  public static final int TYPE_PROGRESS = 1;

  private final ViewController<?> context;

  private @Nullable ArrayList<TGBackground> wallpapers;
  private TGBackground selectedWallpaper;

  private final ArrayList<RecyclerView> attachedRecyclers;
  private final int themeId;

  public WallpaperAdapter (ViewController<?> context, int themeId) {
    this.context = context;
    this.themeId = themeId;
    this.selectedWallpaper = context.tdlib().settings().getWallpaper(Theme.getWallpaperIdentifier());
    this.attachedRecyclers = new ArrayList<>();
    context.tdlib().wallpaper().getBackgrounds(this::setWallpapers, Theme.isDarkTheme(themeId));
    ThemeManager.instance().addChatStyleListener(this);
  }

  public void destroy () {
    ThemeManager.instance().removeChatStyleListener(this);
  }

  @Override
  public void onAttachedToRecyclerView (@NonNull RecyclerView recyclerView) {
    attachedRecyclers.add(recyclerView);
    int i = indexOfWallpaper(selectedWallpaper);
    if (i != -1) {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(i, Screen.currentWidth() / 2 - Screen.dp(105f) / 2);
    }
  }

  @Override
  public void onDetachedFromRecyclerView (RecyclerView recyclerView) {
    attachedRecyclers.remove(recyclerView);
  }

  private TGBackground lastFoundWallpaper;
  private int lastFoundIndex = -1;

  private int indexOfWallpaper (TGBackground wallpaper) {
    if (wallpapers == null)
      return -1;
    if (lastFoundIndex != -1 && TGBackground.compare(lastFoundWallpaper, wallpaper))
      return lastFoundIndex;
    int i = 0;
    for (TGBackground w : wallpapers) {
      if (TGBackground.compare(wallpaper, w)) {
        lastFoundWallpaper = w;
        lastFoundIndex = i;
        return i;
      }
      i++;
    }
    return -1;
  }

  private void setSelected (TGBackground wallpaper, boolean isSelected) {
    int i = indexOfWallpaper(wallpaper);
    if (i != -1) {
      int foundCount = 0;

      for (RecyclerView recyclerView : attachedRecyclers) {
        View view = recyclerView.getLayoutManager().findViewByPosition(i);
        if (view != null && view instanceof WallpaperView && TGBackground.compare(((WallpaperView) view).getWallpaper(), wallpaper)) {
          ((WallpaperView) view).setWallpaperSelected(isSelected);
          foundCount++;
        }
      }

      if (foundCount == 0 || foundCount < attachedRecyclers.size()) {
        notifyItemChanged(i);
      }
    }
  }

  private void setSelected (TGBackground wallpaper) {
    if (!TGBackground.compare(this.selectedWallpaper, wallpaper)) {
      TGBackground oldWallpaper = this.selectedWallpaper;
      this.selectedWallpaper = wallpaper;
      setSelected(oldWallpaper, false);
      setSelected(wallpaper, true);
      centerWallpapers(true);
    }
  }

  private static ArrayList<TGBackground> injectCustomWallpaper (Tdlib tdlib, List<TGBackground> wallpapers, int themeId) {
    TGBackground customWallpaper = null;
    try {
      TGBackground wallpaper = tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier());
      String path = null;
      if (wallpaper != null && wallpaper.isCustom()) {
        path = wallpaper.getCustomPath();
      }
      if (StringUtils.isEmpty(path)) {
        ImageFile thumb = Media.instance().getGalleryRepresentation();
        if (thumb != null) {
          path = thumb.getFilePath();
        } else {
          path = "custom";
        }
      }
      customWallpaper = new TGBackground(tdlib, path);
    } catch (Throwable t) {
      Log.w("Cannot add custom wallpaper option", t);
    }

    boolean needEmpty = Theme.getDefaultWallpaper() == null;
    boolean needCustom = customWallpaper != null;
    ArrayList<TGBackground> newList = new ArrayList<>(wallpapers.size() + (needCustom ? 1 : 0) + (needEmpty ? 1 : 0) + SOLID_COLORS.length);
    if (needCustom)
      newList.add(customWallpaper);
    if (needEmpty)
      newList.add(TGBackground.newEmptyWallpaper(tdlib));
    newList.addAll(wallpapers);
    boolean isDark = Theme.isDarkTheme(themeId);
    int[] legacyWallpaperIds = TGBackground.getLegacyWallpaperIds();
    newList.ensureCapacity(newList.size() + legacyWallpaperIds.length);
    for (int legacyWallpaperId : legacyWallpaperIds) {
      TGBackground legacyBackground = TGBackground.newLegacyWallpaper(tdlib, legacyWallpaperId);
      if (legacyBackground == null)
        continue;
      boolean found = false;
      for (TGBackground existingBackground : newList) {
        if (StringUtils.equalsOrBothEmpty(existingBackground.getName(), legacyBackground.getName())) {
          found = true;
          existingBackground.setLegacyWallpaperId(legacyBackground.getLegacyWallpaperId());
          break;
        }
      }
      if (!found)
        newList.add(legacyBackground);
    }
    String defaultWallpaper = TdlibWallpaperManager.extractWallpaperName(Theme.getDefaultWallpaper(themeId));
    if (defaultWallpaper != null) {
      boolean found = false;
      for (TGBackground background : newList) {
        if (defaultWallpaper.equals(background.getName())) {
          found = true;
          break;
        }
      }
      if (!found) {
        if (defaultWallpaper.length() == 6) {
          try {
            int solidColor = Color.parseColor("#" + defaultWallpaper);
            newList.add(new TGBackground(tdlib, solidColor));
            found = true;
          } catch (IllegalArgumentException ignored) { }
        }
        if (!found) {
          newList.add(TGBackground.newUnknownWallpaper(tdlib, defaultWallpaper));
        }
      }
    }
    Collections.sort(newList, (a, b) -> {
      if (a.isCustom() != b.isCustom())
        return a.isCustom() ? -1 : 1;
      if (a.isEmpty() != b.isEmpty())
        return a.isEmpty() ? -1 : 1;
      if (defaultWallpaper != null) {
        boolean d1 = defaultWallpaper.equals(a.getName());
        boolean d2 = defaultWallpaper.equals(b.getName());
        if (d1 != d2)
          return d1 ? -1 : 1;
      }
      if (!isDark) {
        if (a.isBuiltIn() != b.isBuiltIn())
          return a.isBuiltIn() ? -1 : 1;
        if (a.isCat() != b.isCat())
          return a.isCat() ? -1 : 1;
      }
      if (a.isFill() != b.isFill())
        return a.isFill() ? 1 : -1;
      if (!a.isFill()) {
        if (a.isLegacy() != b.isLegacy())
          return a.isLegacy() ? 1 : -1;
        if (a.isLegacy()) {
          if (isDark) {
            if (a.isBuiltIn() != b.isBuiltIn())
              return a.isBuiltIn() ? -1 : 1;
            if (a.isCat() != b.isCat())
              return a.isCat() ? -1 : 1;
          }
          int s1 = TGBackground.getScore(a.getLegacyWallpaperId(), isDark);
          int s2 = TGBackground.getScore(b.getLegacyWallpaperId(), isDark);
          if (s1 != s2)
            return s1 < s2 ? 1 : -1;
        }
      }
      return 0;
    });
    final int startIndex = newList.size() - 1;
    for (int i = SOLID_COLORS.length - 1; i >= 0; i--) {
      int color = SOLID_COLORS[i];
      int checkIndex = startIndex;
      boolean found = false;
      TGBackground check;
      while (checkIndex >= 0) {
        check = newList.get(checkIndex--);
        if (!check.isFillSolid())
          break;
        if (check.getBackgroundColor() == color) {
          found = true;
          break;
        }
      }
      if (!found) {
        newList.add(new TGBackground(tdlib, color));
      }
    }
    return newList;
  }

  private void setWallpapers (final @Nullable List<TGBackground> wallpapers) {
    if (wallpapers == null) {
      return;
    }
    Media.instance().post(() -> {
      final ArrayList<TGBackground> list = injectCustomWallpaper(context.tdlib(), wallpapers, themeId);
      UI.post(() -> replaceWallpapers(list));
    });
  }

  private void replaceWallpapers (ArrayList<TGBackground> wallpapers) {
    int oldItemCount = getItemCount();
    this.wallpapers = wallpapers;
    this.selectedWallpaper = context.tdlib().settings().getWallpaper(Theme.getWallpaperIdentifier());
    U.notifyItemsReplaced(this, oldItemCount);
    centerWallpapers(false);
  }

  /*rprivate int calculateTotalScrollX (LinearLayoutManager manager) {
    int i = manager.findFirstVisibleItemPosition();
    View view = manager.findViewByPosition(i);
    // int scrollX = i *
    // manager.getDecoratedLeft(view)
  }*/

  private static int calculateTotalScrollX (RecyclerView recyclerView, LinearLayoutManager manager, int cellSize, int spacing) {
    int i = manager.findFirstVisibleItemPosition();
    int scrollX = (cellSize + spacing) * i;
    View view = manager.findViewByPosition(i);
    if (view != null) {
      if (Lang.rtl()) {
        scrollX += manager.getDecoratedRight(view) - recyclerView.getMeasuredWidth();
      } else {
        scrollX -= manager.getDecoratedLeft(view);
      }
    }
    return scrollX;
  }

  public void centerWallpapers (boolean smooth) {
    int position = indexOfWallpaper(selectedWallpaper);
    if (position == -1)
      return;
    for (RecyclerView recyclerView : attachedRecyclers) {
      LinearLayoutManager manager = ((LinearLayoutManager) recyclerView.getLayoutManager());
      if (recyclerView.getMeasuredWidth() == 0) {
        manager.scrollToPosition(position);
        continue;
      }
      int centerX = recyclerView.getMeasuredWidth() / 2;
      int cellSize = Screen.dp(105f);
      int spacing = Screen.dp(3f);
      if (smooth) {
        int scrollX = calculateTotalScrollX(recyclerView, manager, cellSize, spacing);
        int availScrollX = spacing + (cellSize + spacing) * wallpapers.size() - recyclerView.getMeasuredWidth();
        int desiredScrollX = Math.max(0, Math.min(availScrollX, (cellSize + spacing) * position - centerX + cellSize / 2 + spacing));
        if (desiredScrollX != scrollX) {
          recyclerView.stopScroll();
          recyclerView.smoothScrollBy(Lang.rtl() ? scrollX - desiredScrollX : desiredScrollX - scrollX, 0);
        }
        continue;
      }
      manager.scrollToPositionWithOffset(position, centerX - cellSize / 2 - spacing);
    }
  }

  @Override
  public ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return ViewHolder.create(context.context(), viewType, this, null);
  }

  @Override
  public void onViewAttachedToWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).attach();
        break;
      }
      case TYPE_WALLPAPER: {
        ((WallpaperView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).detach();
        break;
      }
      case TYPE_WALLPAPER: {
        ((WallpaperView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public void onBindViewHolder (ViewHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case TYPE_WALLPAPER: {
        if (wallpapers != null) {
          TGBackground wallpaper = wallpapers.get(position);
          ((WallpaperView) holder.itemView).setWallpaper(wallpaper, TGBackground.compare(wallpaper, selectedWallpaper));
        }
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    return wallpapers != null ? wallpapers.size() : 1;
  }

  @Override
  public int getItemViewType (int position) {
    if (wallpapers != null) {
      return TYPE_WALLPAPER;
    } else {
      return TYPE_PROGRESS;
    }
  }

  @Override
  public void onClick (View v) {
    if (!(v instanceof WallpaperView)) {
      return;
    }

    TGBackground wallpaper = ((WallpaperView) v).getWallpaper();
    if (wallpaper != null) {
      if (wallpaper.isCustom()) {
        Intents.openGallery(false);
      } else {
        context.tdlib().settings().setWallpaper(wallpaper, true, Theme.getWallpaperIdentifier());
      }
    }
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) { }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageId) {
    if (this.context.tdlib() != tdlib || Theme.getWallpaperIdentifier() != usageId) {
      return;
    }
    if (wallpapers != null && !wallpapers.isEmpty() && wallpaper != null && wallpaper.isCustom() && !TGBackground.compare(wallpapers.get(0), wallpaper)) {
      TGBackground oldWallpaper = wallpapers.get(0);
      wallpapers.set(0, wallpaper);
      int i = indexOfWallpaper(wallpaper);
      if (i != -1) {
        int foundCount = 0;
        for (RecyclerView recyclerView : attachedRecyclers) {
          View view = recyclerView.getLayoutManager().findViewByPosition(i);
          if (view != null && view instanceof WallpaperView && TGBackground.compare(((WallpaperView) view).getWallpaper(), oldWallpaper)) {
            ((WallpaperView) view).replaceWallpaper(wallpaper);
            foundCount++;
          }
        }
        if (foundCount != attachedRecyclers.size()) {
          notifyItemChanged(i);
        }
      }
    }
    setSelected(wallpaper);
  }

  public static class WallpaperView extends SparseDrawableView implements FactorAnimator.Target, Destroyable {
    private final ImageReceiver preview, receiver;
    private boolean isSelected;

    public WallpaperView (Context context) {
      super(context);
      this.preview = new ImageReceiver(this, 0);
      this.receiver = new ImageReceiver(this, 0);
    }

    public TGBackground getWallpaper () {
      return wallpaper;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(heightMeasureSpec, heightMeasureSpec);
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      preview.setBounds(0, 0, width, height);
      receiver.setBounds(0, 0, width, height);
    }

    private TGBackground wallpaper;

    public void setWallpaper (TGBackground wallpaper, boolean isSelected) {
      this.wallpaper = wallpaper;
      if (wallpaper != null) {
        if (wallpaper.isPattern()) {
          int color = wallpaper.getPatternColor();
          receiver.setColorFilter(color);
          preview.requestFile(null);
        } else {
          receiver.disableColorFilter();
          preview.requestFile(wallpaper.getPreview(true));
        }
        receiver.requestFile(wallpaper.getPreview(false));
      } else {
        preview.requestFile(null);
        receiver.requestFile(null);
      }
      setSelected(isSelected, false);
    }

    public void replaceWallpaper (TGBackground wallpaper) {
      this.wallpaper = wallpaper;
      preview.requestFile(wallpaper.getPreview(true));
      receiver.requestFile(wallpaper.getPreview(false));
    }

    public void attach () {
      preview.attach();
      receiver.attach();
    }

    public void detach () {
      preview.detach();
      receiver.detach();
    }

    @Override
    public void performDestroy () {
      preview.destroy();
      receiver.destroy();
    }

    public void setWallpaperSelected (boolean isSelected) {
      setSelected(isSelected, true);
    }

    private void setSelected (boolean isSelected, boolean animated) {
      if (this.isSelected != isSelected) {
        this.isSelected = isSelected;
        if (animated) {
          animateFactor(isSelected ? 1f : 0f);
        } else {
          forceFactor(isSelected ? 1f : 0f);
        }
      }
    }

    private float factor;

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    private FactorAnimator animator;

    private void animateFactor (float factor) {
      if (this.animator == null) {
        this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
      }
      animator.animateTo(factor);
    }

    private void forceFactor (float factor) {
      if (animator != null) {
        animator.forceFactor(factor);
      }
      setFactor(factor);
    }

    private final DrawAlgorithms.GradientCache cache = new DrawAlgorithms.GradientCache();

    @Override
    protected void onDraw (Canvas c) {
      if (wallpaper == null || wallpaper.isEmpty()) {
        c.drawColor(Theme.backgroundColor());
      } else if (wallpaper.isFillSolid()) {
        c.drawColor(wallpaper.getBackgroundColor());
      } else if (wallpaper.isFillGradient()) {
        DrawAlgorithms.drawGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), 1f);
      } else if (wallpaper.isFillFreeformGradient()) {
        DrawAlgorithms.drawMulticolorGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getFreeformColors(), 1f);
      } else if (wallpaper.isPattern()) {
        if (wallpaper.isPatternBackgroundGradient()) {
          DrawAlgorithms.drawGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), 1f);
        } else if (wallpaper.isPatternBackgroundFreeformGradient()) {
          DrawAlgorithms.drawMulticolorGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getFreeformColors(), 1f);
        } else {
          c.drawColor(wallpaper.getBackgroundColor());
        }
        float alpha = wallpaper.getPatternIntensity();
        if (alpha != 1f) {
          receiver.setPaintAlpha(alpha * receiver.getPaintAlpha());
        }
        receiver.draw(c);
        if (alpha != 1f) {
          receiver.restorePaintAlpha();
        }
      } else {
        if (receiver.needPlaceholder()) {
          if (preview.needPlaceholder()) {
            preview.drawPlaceholder(c);
          }
          preview.draw(c);
        }
        receiver.draw(c);
      }

      float checkFactor = isSelected ? factor : 1f;

      int centerX = getMeasuredWidth() / 2;
      int centerY = getMeasuredHeight() / 2;

      boolean isCustom = wallpaper != null && wallpaper.isCustom();
      float circleFactor = isCustom ? 1f : factor;

      if (circleFactor != 0f) {
        // TODO better color
        c.drawCircle(centerX, centerY, Screen.dp(28f), Paints.fillingPaint(ColorUtils.color((int) (86f * circleFactor), 0)));

        if (isCustom) {
          Paint paint = Paints.getPorterDuffPaint(0xffffffff);
          paint.setAlpha((int) (255f * (1f - factor)));
          Drawable drawable = getSparseDrawable(R.drawable.baseline_image_24, 0);
          Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2, centerY - drawable.getMinimumHeight() / 2, paint);
          paint.setAlpha(255);
        }

        final float fx = checkFactor <= .3f ? 0f : (checkFactor - .3f) / .7f;

        if (fx > 0) {
          final int checkColor = ColorUtils.color((int) (255f * factor), 0xffffffff);

          final float t1;
          final float f1, f2;

          t1 = .3f;
          f1 = fx <= t1 ? fx / t1 : 1f;
          f2 = fx <= t1 ? 0f : (fx - t1) / (1f - t1);

          // check
          c.save();

          c.translate(centerX / 2 + Screen.dp(13f), centerY + Screen.dp(1f));
          c.rotate(-45f);

          final int w2max, h1max;

          w2max = Screen.dp(14f);
          h1max = Screen.dp(7f);

          final int w2 = (int) ((float) w2max * f2);
          final int h1 = (int) ((float) h1max * f1);

          final int x1, y1;

          x1 = Screen.dp(4f);
          y1 = Screen.dp(11f);

          int lineSize = Screen.dp(2f);
          c.drawRect(x1, y1 - h1max, x1 + lineSize, y1 - h1max + h1, Paints.fillingPaint(checkColor));
          c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(checkColor));

          c.restore();
        }
      }
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder (View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
      switch (viewType) {
        case TYPE_WALLPAPER: {
          WallpaperView view = new WallpaperView(context);
          view.setOnClickListener(onClickListener);
          view.setOnLongClickListener(onLongClickListener);
          return new ViewHolder(view);
        }
        case TYPE_PROGRESS: {
          ProgressComponentView view = new ProgressComponentView(context);
          view.initBig(1f);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new ViewHolder(view);
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }
}
