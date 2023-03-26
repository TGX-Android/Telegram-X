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
 * File created on 25/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.EmojiListController;
import org.thunderdog.challegram.ui.EmojiMediaListController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class EmojiLayout extends FrameLayoutFix implements ViewTreeObserver.OnPreDrawListener, ViewPager.OnPageChangeListener, FactorAnimator.Target, View.OnClickListener, View.OnLongClickListener, Lang.Listener {
  public interface Listener {
    void onEnterEmoji (String emoji);
    default boolean onSendSticker (@Nullable View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
      return false;
    }
    default boolean onSendGIF (@Nullable View view, TdApi.Animation animation) {
      return false;
    }
    boolean isEmojiInputEmpty ();
    void onDeleteEmoji ();
    void onSearchRequested (EmojiLayout layout, boolean areStickers);
    default long getOutputChatId () { return 0; }

    default void onSectionSwitched (EmojiLayout layout, @EmojiMediaType int section, @EmojiMediaType int prevSection) { }
    default void onSectionInteracted (EmojiLayout layout, @EmojiMediaType int section, boolean interactionFinished) { }
  }

  private ViewController<?> parentController;
  private @Nullable Listener listener;

  private RtlViewPager pager;
  private Adapter adapter;

  private FrameLayoutFix headerView;

  public static int getHeaderSize () {
    return Screen.dp(47f);
  }

  public static int getHeaderPadding () {
    return Screen.dp(6f);
  }

  public static int getHeaderImagePadding () {
    return Screen.dp(10f);
  }

  public static int getHorizontalPadding () {
    return Screen.dp(2.5f);
  }

  private ShadowView shadowView;
  private FrameLayoutFix emojiSectionsView;

  private RecyclerView mediaSectionsView;

  private ArrayList<EmojiSection> emojiSections;
  private int currentEmojiSection;

  public void setCurrentEmojiSection (int section) {
    if (this.currentEmojiSection != section && section != -1) {
      emojiSections.get(currentEmojiSection).setFactor(0f, headerHideFactor != 1f && currentPageFactor != 1f);
      this.currentEmojiSection = section;
      emojiSections.get(currentEmojiSection).setFactor(1f, headerHideFactor != 1f && currentPageFactor != 1f);
    }
  }

  private static final int OFFSET = 2;

  public void removeStickerSection (int section) {
    mediaAdapter.removeStickerSet(section - mediaAdapter.getAddItemCount(true));
  }

  private void clearRecentStickers () {
    if (themeProvider != null && mediaAdapter.hasRecents) {
      themeProvider.showOptions(null, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearRecentStickers), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_auto_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          setShowRecents(false);
          ViewController<?> c = adapter.getCachedItem(1);
          if (c != null) {
            ((EmojiMediaListController) c).removeRecentStickers();
          }
          parentController.tdlib().client().send(new TdApi.ClearRecentStickers(), parentController.tdlib().okHandler());
        }
        return true;
      });
    }
  }

  private void clearRecentEmoji () {
    if (themeProvider != null) {
      themeProvider.showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearRecentEmojiAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_auto_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          Emoji.instance().clearRecents();
          ViewController<?> c = adapter.getCachedItem(0);
          if (c != null) {
            ((EmojiListController) c).resetRecentEmoji();
          }
        }
        return true;
      });
    }
  }

  private void removeStickerSet (final TGStickerSetInfo info) {
    if (themeProvider != null) {
      themeProvider.showOptions(null, new int[] {R.id.btn_copyLink, R.id.btn_archive, R.id.more_btn_delete}, new String[] {Lang.getString(R.string.CopyLink), Lang.getString(R.string.ArchivePack), Lang.getString(R.string.DeletePack)}, new int[] {ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_RED}, new int[] {R.drawable.baseline_link_24, R.drawable.baseline_archive_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
        switch (id) {
          case R.id.more_btn_delete: {
            if (themeProvider != null) {
              themeProvider.showOptions(Lang.getStringBold(R.string.RemoveStickerSet, info.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RemoveStickerSetAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
                if (resultId == R.id.btn_delete) {
                  parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), false, false), parentController.tdlib().okHandler());
                }
                return true;
              });
            }
            break;
          }
          case R.id.btn_archive: {
            if (themeProvider != null) {
              themeProvider.showOptions(Lang.getStringBold(R.string.ArchiveStickerSet, info.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] { Lang.getString(R.string.ArchiveStickerSetAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_archive_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
                if (resultId == R.id.btn_delete) {
                  parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), false, true), parentController.tdlib().okHandler());
                }
                return true;
              });
            }
            break;
          }
          case R.id.btn_copyLink: {
            UI.copyText(TD.getStickerPackLink(info.getName()), R.string.CopiedLink);
            break;
          }
        }
        return true;
      });
    }
  }

  public void addStickerSection (int section, TGStickerSetInfo info) {
    mediaAdapter.addStickerSet(section - mediaAdapter.getAddItemCount(true), info);
  }

  public void moveStickerSection (int fromSection, int toSection) {
    int addItems = mediaAdapter.getAddItemCount(true);
    mediaAdapter.moveStickerSet(fromSection - addItems, toSection - addItems);
  }

  public void setCurrentStickerSectionByPosition (int i, boolean isStickerSection, boolean animated) {
    if (mediaAdapter.hasRecents && mediaAdapter.hasFavorite && isStickerSection && i >= 1) {
      i--;
    }
    if (isStickerSection) {
      i += mediaAdapter.headerItems.size() - mediaAdapter.getAddItemCount(false);
    }
    setCurrentStickerSection(mediaAdapter.getObject(i), animated);
  }

  private void setCurrentStickerSection (Object obj, boolean animated) {
    if (mediaAdapter.setSelectedObject(obj, animated, mediaSectionsView.getLayoutManager())) {
      int section = mediaAdapter.indexOfObject(obj);
      int first = ((LinearLayoutManager) mediaSectionsView.getLayoutManager()).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) mediaSectionsView.getLayoutManager()).findLastVisibleItemPosition();
      int itemWidth = (Screen.currentWidth() - getHorizontalPadding() * 2) / emojiSections.size();

      if (first != -1) {
        int scrollX = first * itemWidth;
        View v = mediaSectionsView.getLayoutManager().findViewByPosition(first);
        if (v != null) {
          scrollX += -v.getLeft();
        }

        if (section - OFFSET < first) {
          int desiredScrollX = section * itemWidth - itemWidth / 2 - itemWidth * (OFFSET - 1);
          if (animated && headerHideFactor != 1f) {
            mediaSectionsView.smoothScrollBy(desiredScrollX - scrollX, 0);
          } else {
            mediaSectionsView.scrollBy(desiredScrollX - scrollX, 0);
          }
        } else if (section + OFFSET > last) {
          int desiredScrollX = Math.max(0, (section - emojiSections.size()) * itemWidth + itemWidth * OFFSET + itemWidth / 2);
          if (animated && headerHideFactor != 1f) {
            mediaSectionsView.smoothScrollBy(desiredScrollX - scrollX, 0);
          } else {
            mediaSectionsView.scrollBy(desiredScrollX - scrollX, 0);
          }
        }
      }
    }
  }

  private CircleButton circleButton;

  public void onEnterEmoji (String emoji) {
    if (listener != null) {
      listener.onEnterEmoji(emoji);
    }
  }

  public static class EmojiSection implements FactorAnimator.Target {
    public final int index;
    public float selectionFactor;

    private int iconRes;
    public Drawable icon;
    public @Nullable Drawable activeIcon;

    private boolean activeDisabled;

    private @Nullable View view;
    private EmojiLayout parent;

    private final int activeIconRes;

    public EmojiSection (EmojiLayout parent, int sectionIndex, @DrawableRes int iconRes, @DrawableRes int activeIconRes) {
      this.parent = parent;
      this.index = sectionIndex;
      this.activeIconRes = activeIconRes;
      this.activeIcon = Drawables.get(parent.getResources(), activeIconRes);
      changeIcon(iconRes);
    }

    public EmojiSection setActiveDisabled () {
      activeDisabled = true;
      return this;
    }

    private void changeIcon (final int iconRes) {
      if (this.iconRes != iconRes) {
        this.icon = Drawables.get(parent.getResources(), this.iconRes = iconRes);
        if (view != null) {
          view.invalidate();
        }
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    private @Nullable FactorAnimator animator;

    public EmojiSection setFactor (float toFactor, boolean animated) {
      if (selectionFactor != toFactor && animated && view != null) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180, selectionFactor);
        }
        animator.animateTo(toFactor);
      } else {
        if (animator != null) {
          animator.forceFactor(toFactor);
        }
        setFactor(toFactor);
      }
      return this;
    }

    private void setFactor (float factor) {
      if (this.selectionFactor != factor) {
        this.selectionFactor = factor;

        if (isPanda) {
          if (factor == 1f) {
            startPandaTimer();
          } else {
            cancelPandaTimer();
          }
        }

        if (view != null) {
          view.invalidate();
        }
      }
    }

    public void setCurrentView (View view) {
      this.view = view;
    }

    private boolean makeFirstTransparent;

    public EmojiSection setMakeFirstTransparent () {
      this.makeFirstTransparent = true;
      return this;
    }

    private int offsetHalf;

    public EmojiSection setOffsetHalf (boolean fromRight) {
      this.offsetHalf = fromRight ? 1 : -1;
      return this;
    }

    private boolean isPanda, doesPandaBlink, isPandaBlinking;
    private Runnable pandaBlink;

    public EmojiSection setIsPanda (boolean isPanda) {
      this.isPanda = isPanda;
      return this;
    }

    private void setPandaBlink (boolean inBlink) {
      if (this.doesPandaBlink != inBlink) {
        this.doesPandaBlink = inBlink;
        this.activeIcon = Drawables.get(parent.getResources(), inBlink ? R.drawable.deproko_baseline_animals_filled_blink_24 : activeIconRes);
        if (view != null) {
          view.invalidate();
        }
      }
    }

    private void startPandaTimer () {
      if (!isPandaBlinking) {
        this.isPandaBlinking = true;
        if (pandaBlink == null) {
          this.pandaBlink = () -> {
            if (isPandaBlinking || doesPandaBlink) {
              setPandaBlink(!doesPandaBlink);
              if (isPandaBlinking) {
                scheduleBlink(false);
              }
            }
          };
        }
        blinkNum = 0;
        scheduleBlink(true);
      }
    }

    private int blinkNum;

    private void scheduleBlink (boolean firstTime) {
      if (view != null) {
        long delay;
        switch (blinkNum++) {
          case 0: {
            setPandaBlink(false);
            delay = firstTime ? 6000 : 1000;
            break;
          }
          case 1: case 3: case 5: {
            delay = 140;
            break;
          }
          case 2:
          case 4: {
            delay = 4000;
            break;
          }
          case 6: {
            delay = 370;
            break;
          }
          case 7: {
            delay = 130;
            break;
          }
          case 8: {
            delay = 4000;
            blinkNum = 0;
            break;
          }
          default: {
            delay = 1000;
            blinkNum = 0;
            break;
          }
        }
        view.postDelayed(pandaBlink, delay);
      }

    }

    private void cancelPandaTimer () {
      if (isPandaBlinking) {
        isPandaBlinking = false;
        setPandaBlink(false);
        if (view != null) {
          view.removeCallbacks(pandaBlink);
        }
      }
    }

    public void draw (Canvas c, int cx, int cy) {
      if (selectionFactor == 0f || activeDisabled) {
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, parent.useDarkMode ? Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_icon, ThemeId.NIGHT_BLACK)) : Paints.getIconGrayPorterDuffPaint());
      } else if (selectionFactor == 1f) {
        final Drawable icon = this.activeIcon != null ? activeIcon : this.icon;
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, parent.useDarkMode ? Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_iconActive, ThemeId.NIGHT_BLACK)) : Paints.getActiveKeyboardPaint());
      } else {
        final Paint grayPaint = parent.useDarkMode ? Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_icon, ThemeId.NIGHT_BLACK)) : Paints.getIconGrayPorterDuffPaint();
        final int grayAlpha = grayPaint.getAlpha();

        if (makeFirstTransparent) {
          int newAlpha = (int) ((float) grayAlpha * (1f - selectionFactor));
          grayPaint.setAlpha(newAlpha);
        } else if (isPanda) {
          int newAlpha = (int) ((float) grayAlpha * (1f - (1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(1f - selectionFactor))));
          grayPaint.setAlpha(newAlpha);
        }

        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, grayPaint);
        grayPaint.setAlpha(grayAlpha);

        final Drawable icon = this.activeIcon != null ? activeIcon : this.icon;
        final Paint iconPaint = Paints.getActiveKeyboardPaint();
        final int sourceIconAlpha = iconPaint.getAlpha();
        int alpha = (int) ((float) sourceIconAlpha * selectionFactor);
        iconPaint.setAlpha(alpha);
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, iconPaint);
        iconPaint.setAlpha(sourceIconAlpha);
      }
    }
  }

  public static class EmojiSectionView extends View {
    public EmojiSectionView (Context context) {
      super(context);
    }

    private int itemCount;

    public void setItemCount (int count) {
      this.itemCount = count;
    }

    private EmojiSection section;

    public void setSection (EmojiSection section) {
      if (this.section != null) {
        this.section.setCurrentView(null);
      }
      this.section = section;
      if (section != null) {
        section.setCurrentView(this);
      }
    }

    public EmojiSection getSection () {
      return section;
    }

    private boolean needTranslate;

    public void setNeedTranslate () {
      this.needTranslate = true;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int itemsSize = Screen.currentWidth();
      int itemWidth = (itemsSize - getHorizontalPadding() * 2) / itemCount; // FIXME MeasureSpec.getSize()
      setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
      if (section != null && needTranslate) {
        setTranslationX(Lang.rtl() ? itemsSize - itemWidth * (section.index + 1) : section.index * itemWidth);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (section != null) {
        section.draw(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
      }
    }
  }

  private MediaAdapter mediaAdapter;

  private static class MediaHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_EMOJI_SECTION = 0;
    public static final int TYPE_STICKER_SECTION = 1;

    public MediaHolder (View itemView) {
      super(itemView);
    }

    public static MediaHolder create (Context context, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, int emojiSectionCount, @Nullable ViewController<?> themeProvider) {
      switch (viewType) {
        case TYPE_EMOJI_SECTION: {
          EmojiSectionView sectionView = new EmojiSectionView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(sectionView);
          }
          sectionView.setId(R.id.btn_section);
          sectionView.setOnClickListener(onClickListener);
          sectionView.setItemCount(emojiSectionCount);
          sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new MediaHolder(sectionView);
        }
        case TYPE_STICKER_SECTION: {
          StickerSectionView sectionView = new StickerSectionView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(sectionView);
          }
          sectionView.setOnLongClickListener(onLongClickListener);
          sectionView.setId(R.id.btn_stickerSet);
          sectionView.setOnClickListener(onClickListener);
          sectionView.setItemCount(emojiSectionCount);
          sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new MediaHolder(sectionView);
        }
      }
      throw new RuntimeException("viewType == " + viewType);
    }
  }

  private static class StickerSectionView extends View implements Destroyable, FactorAnimator.Target {
    private final ImageReceiver receiver;
    private final GifReceiver gifReceiver;

    private int itemCount;

    private float selectionFactor;

    public StickerSectionView (Context context) {
      super(context);
      receiver = new ImageReceiver(this, 0);
      gifReceiver = new GifReceiver(this);
    }

    public void setItemCount (int itemCount) {
      this.itemCount = itemCount;
    }

    public void attach () {
      receiver.attach();
      gifReceiver.attach();
    }

    public void detach () {
      receiver.detach();
      gifReceiver.detach();
    }

    @Override
    public void performDestroy () {
      receiver.destroy();
      gifReceiver.destroy();
    }

    private TGStickerSetInfo info;
    private Path contour;

    public void setStickerSet (@NonNull TGStickerSetInfo info) {
      this.info = info;
      this.contour = info.getPreviewContour(Math.min(receiver.getWidth(), receiver.getHeight()));
      receiver.requestFile(info.getPreviewImage());
      gifReceiver.requestFile(info.getPreviewAnimation());
    }

    private FactorAnimator animator;

    public void setSelectionFactor (float factor, boolean animated) {
      if (animated && this.selectionFactor != factor) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectionFactor);
        }
        animator.animateTo(factor);
      } else {
        if (animator != null) {
          animator.forceFactor(factor);
        }
        setSelectionFactor(factor);
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case 0: {
          setSelectionFactor(factor);
          break;
        }
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

    }

    private void setSelectionFactor (float factor) {
      if (this.selectionFactor != factor) {
        this.selectionFactor = factor;
        invalidate();
      }
    }

    public @Nullable TGStickerSetInfo getStickerSet () {
      return info;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int itemWidth = (Screen.currentWidth() - getHorizontalPadding() * 2) / itemCount; // FIXME MeasureSpec.getSize()
      setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
      setBounds();
    }

    private void setBounds () {
      int padding = getHeaderImagePadding();
      int width = receiver.getWidth(), height = receiver.getHeight();
      receiver.setBounds(padding, padding, getMeasuredWidth() - padding, getMeasuredHeight() - padding);
      gifReceiver.setBounds(padding, padding, getMeasuredWidth() - padding, getMeasuredHeight() - padding);
      if (info != null && (width != receiver.getWidth() || height != receiver.getHeight())) {
        this.contour = info.getPreviewContour(Math.min(receiver.getWidth(), receiver.getHeight()));
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      int cx = getMeasuredWidth() / 2;
      int cy = getMeasuredHeight() / 2;
      final boolean saved = selectionFactor != 0f;
      if (saved) {
        final int selectionColor = Theme.chatSelectionColor();
        final int selectionAlpha = Color.alpha(selectionColor);
        int color = ColorUtils.color((int) ((float) selectionAlpha * selectionFactor), selectionColor);
        int radius = Screen.dp(18f) - (int) ((float) Screen.dp(4f) * (1f - selectionFactor));

        c.drawCircle(cx, cy, radius, Paints.fillingPaint(color));
        c.save();
        float scale = .85f + .15f * (1f - selectionFactor);
        c.scale(scale, scale, cx, cy);
      }

      if (info != null && info.isAnimated()) {
        if (gifReceiver.needPlaceholder()) {
          if (receiver.needPlaceholder()) {
            receiver.drawPlaceholderContour(c, contour);
          }
          receiver.draw(c);
        }
        gifReceiver.draw(c);
      } else {
        if (receiver.needPlaceholder()) {
          receiver.drawPlaceholderContour(c, contour);
        }
        receiver.draw(c);
      }
      if (Config.DEBUG_STICKER_OUTLINES) {
        receiver.drawPlaceholderContour(c, contour);
      }
      if (saved) {
        c.restore();
      }
    }
  }

  private static class MediaAdapter extends RecyclerView.Adapter<MediaHolder> implements View.OnLongClickListener {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final ArrayList<EmojiSection> headerItems;
    private final int sectionItemCount;
    private final EmojiLayout parent;

    private final @Nullable ViewController<?> themeProvider;

    private Object selectedObject;
    private boolean hasRecents, hasFavorite;

    public MediaAdapter (Context context, EmojiLayout parent, OnClickListener onClickListener, int sectionItemCount, boolean selectedIsGifs, @Nullable ViewController<?> themeProvider) {
      this.context = context;
      this.parent = parent;
      this.onClickListener = onClickListener;
      this.themeProvider = themeProvider;
      this.headerItems = new ArrayList<>();
      this.headerItems.add(new EmojiSection(parent, -1, R.drawable.baseline_emoticon_outline_24, 0).setActiveDisabled());
      this.headerItems.add(new EmojiSection(parent, -2, R.drawable.deproko_baseline_gif_24, R.drawable.deproko_baseline_gif_filled_24));
      this.headerItems.add(new EmojiSection(parent, -3, R.drawable.outline_whatshot_24, R.drawable.baseline_whatshot_24).setMakeFirstTransparent());
      // this.favoriteSection = new EmojiSection(parent, -4, R.drawable.baseline_star_border_24, R.drawable.baseline_star_24).setMakeFirstTransparent();
      this.recentSection = new EmojiSection(parent, -4, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24).setMakeFirstTransparent();

      this.selectedObject = selectedIsGifs ? headerItems.get(1) : recentSection;
      if (selectedIsGifs) {
        this.headerItems.get(1).setFactor(1f, false);
      } else {
        this.recentSection.setFactor(1f, false);
      }

      this.sectionItemCount = sectionItemCount;
      this.stickerSets = new ArrayList<>();
    }

    public void setHasRecents (boolean hasRecents) {
      if (this.hasRecents != hasRecents) {
        this.hasRecents = hasRecents;
        checkRecent();
      }
    }

    public int getAddItemCount (boolean allowHidden) {
      int i = 0;
      if (allowHidden) {
        if (hasFavorite) {
          i++;
        }
        if (hasRecents) {
          i++;
        }
      } else {
        if (showingRecentSection) {
          i++;
        }
      }
      return i;
    }

    private boolean showingRecentSection;

    private void checkRecent () {
      boolean showRecent = hasFavorite || hasRecents;
      if (this.showingRecentSection != showRecent) {
        this.showingRecentSection = showRecent;
        if (showRecent) {
          headerItems.add(recentSection);
          notifyItemInserted(headerItems.size() - 1);
        } else {
          int i = headerItems.indexOf(recentSection);
          if (i != -1) {
            headerItems.remove(i);
            notifyItemRemoved(i);
          }
        }
      } else if (selectedObject != null) {
        int i = indexOfObject(selectedObject);
        if (i != -1) {
          notifyItemRangeChanged(i, 2);
        }
      }
    }

    public void setHasFavorite (boolean hasFavorite) {
      if (this.hasFavorite != hasFavorite) {
        this.hasFavorite = hasFavorite;
        checkRecent();
      }
      /*if (this.showFavorite != showFavorite) {
        this.showFavorite = showFavorite;
        if (showFavorite) {
          int i = showRecents ? headerItems.size() - 1 : headerItems.size();
          headerItems.add(i, favoriteSection);
          notifyItemInserted(i);
        } else {
          int i = headerItems.indexOf(favoriteSection);
          if (i != -1) {
            headerItems.remove(i);
            notifyItemRemoved(i);
          }
        }
      }*/
    }

    private boolean hasNewHots;

    public void setHasNewHots (boolean hasHots) {
      if (this.hasNewHots != hasHots) {
        this.hasNewHots = hasHots;
        // TODO
      }
    }

    public boolean setSelectedObject (Object obj, boolean animated, RecyclerView.LayoutManager manager) {
      if (this.selectedObject != obj) {
        setSelected(this.selectedObject, false, animated, manager);
        this.selectedObject = obj;
        setSelected(obj, true, animated, manager);
        return true;
      }
      return false;
    }

    private Object getObject (int i) {
      if (i < headerItems.size()) {
        return headerItems.get(i);
      } else {
        int index = i - headerItems.size();
        return index >= 0 && index < stickerSets.size() ? stickerSets.get(index) : null;
      }
    }

    private int indexOfObject (Object obj) {
      int itemCount = getItemCount();
      for (int i = 0; i < itemCount; i++) {
        if (getObject(i) == obj) {
          return i;
        }
      }
      return -1;
    }

    private void setSelected (Object obj, boolean selected, boolean animated, RecyclerView.LayoutManager manager) {
      int index = indexOfObject(obj);
      if (index != -1) {
        switch (getItemViewType(index)) {
          case MediaHolder.TYPE_EMOJI_SECTION: {
            if (index >= 0 && index < headerItems.size()) {
              headerItems.get(index).setFactor(selected ? 1f : 0f, animated);
            }
            break;
          }
          case MediaHolder.TYPE_STICKER_SECTION: {
            View view = manager.findViewByPosition(index);
            if (view != null && view instanceof StickerSectionView) {
              ((StickerSectionView) view).setSelectionFactor(selected ? 1f : 0f, animated);
            } else {
              notifyItemChanged(index);
            }
            break;
          }
        }
      }
    }

    private final ArrayList<TGStickerSetInfo> stickerSets;
    private final EmojiSection recentSection; // favoriteSection

    public void removeStickerSet (int index) {
      if (index >= 0 && index < stickerSets.size()) {
        stickerSets.remove(index);
        notifyItemRemoved(index + headerItems.size());
      }
    }

    public void addStickerSet (int index, TGStickerSetInfo info) {
      stickerSets.add(index, info);
      notifyItemInserted(index + headerItems.size());
    }

    public void moveStickerSet (int fromIndex, int toIndex) {
      TGStickerSetInfo info = stickerSets.remove(fromIndex);
      stickerSets.add(toIndex, info);
      fromIndex += headerItems.size();
      toIndex += headerItems.size();
      notifyItemMoved(fromIndex, toIndex);
    }

    public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
      if (!stickerSets.isEmpty()) {
        int removedCount = stickerSets.size();
        stickerSets.clear();
        notifyItemRangeRemoved(headerItems.size(), removedCount);
      }
      if (stickers != null && !stickers.isEmpty()) {
        int addedCount;
        if (!stickers.get(0).isSystem()) {
          stickerSets.addAll(stickers);
          addedCount = stickers.size();
        } else {
          addedCount = 0;
          for (int i = 0; i < stickers.size(); i++) {
            TGStickerSetInfo stickerSet = stickers.get(i);
            if (stickerSet.isSystem()) {
              continue;
            }
            stickerSets.add(stickerSet);
            addedCount++;
          }
        }
        notifyItemRangeInserted(headerItems.size(), addedCount);
      }
    }

    @Override
    public MediaHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return MediaHolder.create(context, viewType, onClickListener, this, sectionItemCount, themeProvider);
    }

    @Override
    public boolean onLongClick (View v) {
      if (v instanceof StickerSectionView) {
        StickerSectionView sectionView = (StickerSectionView) v;
        TGStickerSetInfo info = sectionView.getStickerSet();
        if (parent != null) {
          parent.removeStickerSet(info);
          return true;
        }
        return false;
      }
      if ((v instanceof EmojiSectionView)) {
        EmojiSectionView sectionView = (EmojiSectionView) v;
        EmojiSection section = sectionView.getSection();

        if (parent != null) {
          if (section == recentSection) {
            parent.clearRecentStickers();
            return true;
          }
        }
      }

      return false;
    }

    @Override
    public void onBindViewHolder (MediaHolder holder, int position) {
      switch (holder.getItemViewType()) {
        case MediaHolder.TYPE_EMOJI_SECTION: {
          EmojiSection section = headerItems.get(position);
          ((EmojiSectionView) holder.itemView).setSection(section);
          holder.itemView.setOnLongClickListener(section == recentSection ? this : null);
          break;
        }
        case MediaHolder.TYPE_STICKER_SECTION: {
          Object obj = getObject(position);
          ((StickerSectionView) holder.itemView).setSelectionFactor(selectedObject == obj ? 1f : 0f, false);
          ((StickerSectionView) holder.itemView).setStickerSet((TGStickerSetInfo) obj);
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      if (position < headerItems.size()) {
        return MediaHolder.TYPE_EMOJI_SECTION;
      } else {
        return MediaHolder.TYPE_STICKER_SECTION;
      }
    }

    @Override
    public int getItemCount () {
      return headerItems.size() + (stickerSets != null ? stickerSets.size() : 0);
    }

    @Override
    public void onViewAttachedToWindow (MediaHolder holder) {
      switch (holder.getItemViewType()) {
        case MediaHolder.TYPE_STICKER_SECTION: {
          ((StickerSectionView) holder.itemView).attach();
          break;
        }
      }
    }

    @Override
    public void onViewDetachedFromWindow (MediaHolder holder) {
      switch (holder.getItemViewType()) {
        case MediaHolder.TYPE_STICKER_SECTION: {
          ((StickerSectionView) holder.itemView).detach();
          break;
        }
      }
    }

    @Override
    public void onViewRecycled (MediaHolder holder) {
      if (holder.getItemViewType() == MediaHolder.TYPE_STICKER_SECTION) {
        ((StickerSectionView) holder.itemView).performDestroy();
      }
    }
  }

  private @Nullable ViewController<?> themeProvider;
  private boolean allowMedia;
  private boolean useDarkMode;

  public EmojiToneHelper.Delegate getToneDelegate () {
    return parentController != null && parentController instanceof EmojiToneHelper.Delegate ? (EmojiToneHelper.Delegate) parentController : null;
  }

  public boolean useDarkMode () {
    return useDarkMode;
  }

  public EmojiLayout (Context context) {
    super(context);
  }

  public void initWithMediasEnabled (ViewController<?> context, boolean allowMedia, @NonNull Listener listener, @Nullable ViewController<?> themeProvider, boolean useDarkMode) {
    this.parentController = context;
    this.listener = listener;
    this.themeProvider = themeProvider;
    this.allowMedia = allowMedia;
    this.useDarkMode = useDarkMode;

    this.emojiSections = new ArrayList<>();
    this.emojiSections.add(new EmojiSection(this, 0, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24).setFactor(1f, false).setMakeFirstTransparent().setOffsetHalf(false));
    this.emojiSections.add(new EmojiSection(this, 1, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_emoticon_24).setMakeFirstTransparent());
    this.emojiSections.add(new EmojiSection(this, 2, R.drawable.deproko_baseline_animals_outline_24, R.drawable.deproko_baseline_animals_24).setIsPanda(!useDarkMode));
    this.emojiSections.add(new EmojiSection(this, 3, R.drawable.baseline_restaurant_menu_24, R.drawable.baseline_restaurant_menu_24));
    this.emojiSections.add(new EmojiSection(this, 4, R.drawable.baseline_directions_car_24, R.drawable.baseline_directions_car_24));
    this.emojiSections.add(new EmojiSection(this, 5, R.drawable.deproko_baseline_lamp_24, R.drawable.deproko_baseline_lamp_filled_24));
    this.emojiSections.add(new EmojiSection(this, 6, R.drawable.deproko_baseline_flag_outline_24, R.drawable.deproko_baseline_flag_filled_24).setMakeFirstTransparent());

    if (allowMedia) {
      this.emojiSections.add(new EmojiSection(this, 7, R.drawable.deproko_baseline_stickers_24, /*R.drawable.ic_gif*/ 0).setActiveDisabled().setOffsetHalf(true));
    } else {
      this.emojiSections.get(this.emojiSections.size() - 1).setOffsetHalf(true);
    }

    adapter = new Adapter(context, this, allowMedia, themeProvider);
    pager = new RtlViewPager(getContext());
    pager.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(this);
    pager.setAdapter(adapter);
    pager.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    int headerSize = getHeaderSize();
    headerView = new FrameLayoutFix(getContext()) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        return true;
      }
    };
    if (useDarkMode) {
      headerView.setBackgroundColor(Theme.getColor(R.id.theme_color_filling, ThemeId.NIGHT_BLACK));
    } else {
      ViewSupport.setThemedBackground(headerView, R.id.theme_color_filling, themeProvider);
    }
    headerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerSize));

    // Emoji sections

    emojiSectionsView = new FrameLayoutFix(getContext());
    emojiSectionsView.setPadding(getHorizontalPadding(), 0, getHorizontalPadding(), 0);
    emojiSectionsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerSize));

    for (EmojiSection section : emojiSections) {
      EmojiSectionView sectionView = new EmojiSectionView(getContext());
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(sectionView);
      }
      sectionView.setId(R.id.btn_section);
      sectionView.setNeedTranslate();
      sectionView.setOnClickListener(this);
      sectionView.setOnLongClickListener(this);
      sectionView.setSection(section);
      sectionView.setItemCount(emojiSections.size());
      sectionView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      emojiSectionsView.addView(sectionView);
    }

    headerView.addView(emojiSectionsView);

    // Media sections

    if (allowMedia) {
      mediaSectionsView = new RecyclerView(getContext());
      mediaSectionsView.setHasFixedSize(true);
      mediaSectionsView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
      mediaSectionsView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS :OVER_SCROLL_NEVER);
      mediaSectionsView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl()));
      mediaSectionsView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
          int position = parent.getChildAdapterPosition(view);
          outRect.left = position == 0 ? getHorizontalPadding() : 0;
          outRect.right = position == mediaAdapter.getItemCount() - 1 ? getHorizontalPadding() : 0;
        }
      });
      mediaSectionsView.setAdapter(mediaAdapter = new MediaAdapter(getContext(), this, this, emojiSections.size(), Settings.instance().getEmojiMediaSection() == EmojiMediaType.GIF, themeProvider));
      mediaSectionsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerSize));

      headerView.addView(mediaSectionsView);
    } else {
      mediaSectionsView = null;
      mediaAdapter = null;
    }

    // Shadow and etc

    shadowView = new ShadowView(getContext());
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(shadowView);
    }
    shadowView.setSimpleBottomTransparentShadow(true);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(shadowView.getLayoutParams().width, shadowView.getLayoutParams().height);
    params.topMargin = headerSize;
    shadowView.setLayoutParams(params);

    int position;
    if (allowMedia) {
      position = Settings.instance().getEmojiPosition();
      if (pager.getCurrentItem() != position) {
        pager.setCurrentItem(position, false);
      }
    } else {
      position = 0;
    }

    final int padding = Screen.dp(4);
    params = FrameLayoutFix.newParams(Screen.dp(23f) * 2 + padding * 2, Screen.dp(23f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    circleButton = new CircleButton(getContext());
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(circleButton);
    }
    circleButton.setId(R.id.btn_circle);
    if (position == 0) {
      circleButton.init(R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET), 46f, 4f, R.id.theme_color_circleButtonOverlay, R.id.theme_color_circleButtonOverlayIcon);
      setCircleVisible(hasLeftButton(), false, 0, 0);
    } else {
      circleButton.init(R.drawable.baseline_search_24, 46f, 4f, R.id.theme_color_circleButtonOverlay, R.id.theme_color_circleButtonOverlayIcon);
      setCircleVisible(hasRightButton(), false, 0, 0);
    }
    circleButton.setOnClickListener(this);
    circleButton.setLayoutParams(params);
    updateCircleStyles();

    addView(pager);
    addView(headerView);
    addView(shadowView);
    addView(circleButton);

    if (useDarkMode) {
      setBackgroundColor(Theme.getColor(R.id.theme_color_chatKeyboard, ThemeId.NIGHT_BLACK));
    } else {
      ViewSupport.setThemedBackground(this, R.id.theme_color_chatKeyboard, themeProvider);
    }
    // NewEmoji.instance().loadAllEmoji();

    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void onTextChanged (CharSequence charSequence) {
    if (pager.getCurrentItem() == 0) {
      setCircleVisible(charSequence.length() > 0, true, R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET));
    }
  }

  private static final float BACKSPACE_OFFSET = 1.5f;

  private static final int CIRCLE_ANIMATOR = 1;
  private FactorAnimator circleAnimator;

  private boolean isCircleButtonVisible = true;
  private float circleFactor = 1f;

  private void setCircleVisible (boolean isVisible, boolean animated, @DrawableRes int resourceIfNotVisible, int offsetLeft) {
    if (this.isCircleButtonVisible != isVisible) {
      if (isVisible && resourceIfNotVisible != 0) {
        circleButton.setIcon(resourceIfNotVisible, offsetLeft);
      }
      this.isCircleButtonVisible = isVisible;
      setCircleFactor(isVisible ? 1f : 0f, animated);
    }
  }

  public void setCircleVisible (boolean isVisible, boolean isSearch) {
    if (!isSearch || !noInlineSearch) {
      setCircleVisible(isVisible, true, isSearch ? R.drawable.baseline_search_24 : R.drawable.baseline_backspace_24, isSearch ? 0 : -Screen.dp(BACKSPACE_OFFSET));
    }
  }

  public int getCurrentItem () {
    return pager.getCurrentItem();
  }

  private void setCircleFactor (float toFactor, boolean animated) {
    if (this.circleFactor != toFactor && animated && getVisibility() == View.VISIBLE) {
      if (circleAnimator == null) {
        circleAnimator = new FactorAnimator(CIRCLE_ANIMATOR, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210, circleFactor);
      }

      if (toFactor == 1f && circleFactor == 0f) {
        circleAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
        circleAnimator.setDuration(210l);
      } else {
        circleAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        circleAnimator.setDuration(100l);
      }

      circleAnimator.animateTo(toFactor);
    } else {
      if (circleAnimator != null) {
        circleAnimator.forceFactor(toFactor);
      }
      setCircleFactor(toFactor);
    }
  }

  private static final float MIN_SCALE = .6f;

  private void setCircleFactor (float factor) {
    if (this.circleFactor != factor) {
      this.circleFactor = factor;
      updateCircleStyles();
    }
  }

  private void updateCircleStyles () {
    if (circleButton != null) {
      float factor = circleFactor;
      final float scale = MIN_SCALE + (1f - MIN_SCALE) * factor;
      final float alpha = Math.min(1f, Math.max(0f, factor));
      circleButton.setAlpha(alpha);
      circleButton.setScaleX(scale);
      circleButton.setScaleY(scale);
    }
  }

  public void setShowRecents (boolean showRecents) {
    mediaAdapter.setHasRecents(showRecents);
  }

  public void setShowFavorite (boolean showFavorite) {
    mediaAdapter.setHasFavorite(showFavorite);
  }

  public void setHasNewHots (boolean hasHots) {
    mediaAdapter.setHasNewHots(hasHots);
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers, boolean showFavorite, boolean showRecents) {
    mediaAdapter.setHasFavorite(showFavorite);
    mediaAdapter.setHasRecents(showRecents);
    mediaAdapter.setStickerSets(stickers);
  }

  private void scrollToStickerSet (@NonNull TGStickerSetInfo stickerSet) {
    ViewController<?> c = adapter.getCachedItem(1);
    if (c != null) {
      ((EmojiMediaListController) c).showStickerSet(stickerSet);
    }
  }

  private void scrollToEmojiSection (int sectionIndex) {
    ViewController<?> c = adapter.getCachedItem(0);
    if (c != null) {
      ((EmojiListController) c).showEmojiSection(sectionIndex);
    }
  }

  public boolean sendSticker (View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    return listener != null && listener.onSendSticker(view, sticker, sendOptions);
  }

  public long findOutputChatId () {
    return listener != null ? listener.getOutputChatId() : 0;
  }

  public boolean sendGif (View view, TdApi.Animation animation) {
    return listener != null && listener.onSendGIF(view, animation);
  }

  public void resetScrollState () {
    resetScrollState(false);
  }

  public void resetScrollState (boolean silent) {
    switch (pager.getCurrentItem()) {
      case 0: {
        ViewController<?> c = adapter.getCachedItem(0);
        if (c != null) {
          resetScrollingCache(((EmojiListController) c).getCurrentScrollY(), silent);
        }
        break;
      }
      case 1: {
        ViewController<?> c = adapter.getCachedItem(1);
        if (c != null) {
          resetScrollingCache(((EmojiMediaListController) c).getCurrentScrollY(), silent);
        }
        break;
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_section: {
        EmojiSection section = ((EmojiSectionView) v).getSection();
        if (emojiSections.get(0) == section && Emoji.instance().canClearRecents()) {
          clearRecentEmoji();
          return true;
        }
        break;
      }
    }
    return false;
  }

  @Override
  public void onClick (View v) {
    if (scrollState != ViewPager.SCROLL_STATE_IDLE) {
      return;
    }

    switch (v.getId()) {
      case R.id.btn_stickerSet: {
        TGStickerSetInfo info = ((StickerSectionView) v).getStickerSet();

        if (info != null) {
          scrollToStickerSet(info);
        }

        break;
      }
      case R.id.btn_circle: {
        switch (pager.getCurrentItem()) {
          case 0: {
            if (listener != null) {
              listener.onDeleteEmoji();
            }
            break;
          }
          case 1: {
            if (listener != null) {
              listener.onSearchRequested(this, false);
            }
            break;
          }
        }
        break;
      }
      case R.id.btn_section: {
        EmojiSection section = ((EmojiSectionView) v).getSection();

        int prevSection = getCurrentEmojiSection();
        int newSection = -1;

        if (section.index >= 0) {
          if (allowMedia && section.index == emojiSections.size() - 1) {
            pager.setCurrentItem(1, true);
            newSection = getCurrentMediaEmojiSection();
          } else {
            scrollToEmojiSection(section.index);
            newSection = EmojiMediaType.EMOJI;
          }
        } else {
          int index = -(section.index) - 1;

          switch (index) {
            case 0: {
              pager.setCurrentItem(0, true);
              newSection = EmojiMediaType.EMOJI;
              break;
            }
            case 1: {
              ViewController<?> c = adapter.getCachedItem(1);
              if (c != null) {
                boolean shownGifs = ((EmojiMediaListController) c).showGIFs();
                if (!shownGifs && listener != null) {
                  listener.onSearchRequested(this, false);
                }
              }
              break;
            }
            case 2: {
              ViewController<?> c = adapter.getCachedItem(1);
              if (c != null) {
                ((EmojiMediaListController) c).showHot();
              }
              break;
            }
            case 3: {
              ViewController<?> c = adapter.getCachedItem(1);
              if (c != null) {
                ((EmojiMediaListController) c).showSystemStickers();
              }
              break;
            }
          }
        }

        if (listener != null && newSection != -1) {
          listener.onSectionSwitched(this, newSection, prevSection);
        }

        break;
      }
    }
  }

  private float headerHideFactor;

  public float getHeaderHideFactor () {
    return headerHideFactor;
  }

  public void setHeaderHideFactor (float factor) {
    if (this.headerHideFactor != factor) {
      this.headerHideFactor = factor;
      float y = ((float) -getHeaderSize()) * headerHideFactor;
      headerView.setTranslationY(y);
      shadowView.setTranslationY(y);
      float alpha = 1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(Math.max(0f, Math.min(1f, factor / .5f)));
      emojiSectionsView.setAlpha(alpha);
      if (mediaSectionsView != null) {
        mediaSectionsView.setAlpha(alpha);
      }
    }
  }

  public int getHeaderBottom () {
    return (int) (getHeaderSize() * (1f - headerHideFactor)) + Screen.dp(12f);
  }

  private void showOrHideHeader () {
    if (headerHideFactor != 0f && headerHideFactor != 1f) {
      float hideFactor = headerHideFactor > .25f && lastY - getHeaderSize() > 0 ? 1f : 0f;
      moveHeader(hideFactor, true);
    }
  }

  private void moveHeader (float factor, boolean animated) {
    if (factor == 1f) {
      lastHeaderVisibleY = Math.max(0, lastY - getHeaderSize());
    } else  {
      lastHeaderVisibleY = lastDesiredHeaderVisibleY = lastY;
    }
    setHeaderHideFactor(factor, animated);
  }

  private static final int HIDE_ANIMATOR = 0;
  private FactorAnimator hideAnimator;

  private void setHeaderHideFactor (float factor, boolean animated) {
    if (animated) {
      if (hideAnimator == null) {
        hideAnimator = new FactorAnimator(HIDE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 210l, headerHideFactor);
      }
      hideAnimator.animateTo(factor);
    } else {
      if (hideAnimator != null) {
        hideAnimator.forceFactor(factor);
      }
      setHeaderHideFactor(factor);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case HIDE_ANIMATOR: {
        setHeaderHideFactor(factor);
        break;
      }
      case CIRCLE_ANIMATOR: {
        setCircleFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private int lastY;
  private int lastHeaderVisibleY;
  private int lastDesiredHeaderVisibleY;
  private boolean ignoreMovement;

  private void resetScrollingCache (int scrollY, boolean silent) {
    lastY = scrollY;
    int headerOffset = (int) (headerHideFactor * (float) getHeaderSize());
    lastHeaderVisibleY = lastDesiredHeaderVisibleY = Math.max(0, scrollY - headerOffset);
    if (scrollY - headerOffset < 0) {
      setHeaderHideFactor(0f);
    }
  }

  private boolean ignoreFirstScrollEvent;

  public boolean isWatchingMovements () {
    return !ignoreMovement;
  }

  public void setIgnoreMovement (boolean ignoreMovement) {
    if (this.ignoreMovement != ignoreMovement) {
      this.ignoreMovement = ignoreMovement;

      if (ignoreMovement) {
        ignoreFirstScrollEvent = true;
      } else {
        resetScrollState();
      }
    }
  }

  public void moveHeaderFull (int y) {
    if (ignoreFirstScrollEvent) {
      resetScrollState();
      ignoreFirstScrollEvent = false;
      return;
    }
    if (ignoreMovement || scrollState != ViewPager.SCROLL_STATE_IDLE) {
      return;
    }
    lastHeaderVisibleY = 0;
    lastY = y;
    setHeaderHideFactor(Math.min(1f, Math.max(0f, (float) y / (float) getHeaderSize())), false);
    setCircleVisible(headerHideFactor == 0f, true);
  }

  private void moveHeader (int y) {
    lastY = y;
    if (ignoreFirstScrollEvent) {
      resetScrollState();
      ignoreFirstScrollEvent = false;
      return;
    }
    if (ignoreMovement || scrollState != ViewPager.SCROLL_STATE_IDLE) {
      return;
    }

    float hideFactor = Math.max(0f, Math.min(1f, (float) (y - lastHeaderVisibleY) / (float) getHeaderSize()));

    if (hideFactor == 1f) {
      lastDesiredHeaderVisibleY = Math.max(0, y - getHeaderSize());
    } else if (hideFactor == 0f) {
      lastHeaderVisibleY = lastDesiredHeaderVisibleY = y;
    }
    setHeaderHideFactor(hideFactor, false);
  }

  public void applyHeaderVisibleY () {
    lastHeaderVisibleY = lastDesiredHeaderVisibleY;
  }

  private boolean isScrolling;

  public void setIsScrolling (boolean isScrolling) {
    if (this.isScrolling != isScrolling) {
      this.isScrolling = isScrolling;

      if (!isScrolling) {
        applyHeaderVisibleY();
        showOrHideHeader();
      }
    }
  }

  public void onScroll (int totalDy) {
    moveHeader(totalDy);
  }

  public void onSectionInteracted (@EmojiMediaType int mediaType, boolean interactionFinished) {
    if (listener != null) {
      listener.onSectionInteracted(this, mediaType, interactionFinished);
    }
  }

  public void onSectionScroll (@EmojiMediaType int mediaType, boolean moved) {
    if (moved) {
      onSectionInteracted(mediaType, false);
    }
  }

  private static class Adapter extends PagerAdapter {
    private final ViewController<?> context;
    private final EmojiLayout parent;
    private final SparseArrayCompat<ViewController<?>> cachedItems;
    private final boolean allowMedia;
    private final ViewController<?> themeProvider;

    public Adapter (ViewController<?> context, EmojiLayout parent, boolean allowMedia, @Nullable ViewController<?> themeProvider) {
      this.context = context;
      this.parent = parent;
      this.cachedItems = new SparseArrayCompat<>(2);
      this.themeProvider = themeProvider;
      this.allowMedia = allowMedia;
    }

    public @Nullable ViewController<?> getCachedItem (int position) {
      return cachedItems.get(position);
    }

    public void updateCachedItemsSpanCounts () {
      for (int i = 0; i < cachedItems.size(); i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        switch (c.getId()) {
          case R.id.controller_emoji: {
            ((EmojiListController) c).checkSpanCount();
            break;
          }
          case R.id.controller_emojiMedia: {
            ((EmojiMediaListController) c).checkSpanCount();
            break;
          }
        }
      }
      parent.resetScrollState();
    }

    public void invalidateCachedItems () {
      for (int i = 0; i < cachedItems.size(); i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (c.getId() == R.id.controller_emoji) {
          ((EmojiListController) c).invalidateItems();
        }
      }
    }

    public void destroyCachedItems () {
      final int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        cachedItems.valueAt(i).destroy();
      }
      cachedItems.clear();
    }

    @Override
    public int getCount () {
      return allowMedia ? 2 : 1;
    }

    @Override
    public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
      container.removeView(((ViewController<?>) object).getValue());
    }

    @Override
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      ViewController<?> c = cachedItems.get(position);
      if (c == null) {
        if (position == 0) {
          EmojiListController emojiListController = new EmojiListController(context.context(), context.tdlib());
          emojiListController.setArguments(parent);
          c = emojiListController;
        } else if (position == 1) {
          EmojiMediaListController mediaListController = new EmojiMediaListController(context.context(), context.tdlib());
          mediaListController.setArguments(parent);
          c = mediaListController;
        } else {
          throw new RuntimeException("position == " + position);
        }
        cachedItems.put(position, c);
        if (themeProvider != null) {
          c.bindThemeListeners(themeProvider);
        }
      }
      container.addView(c.getValue());
      return c;
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return object instanceof ViewController && ((ViewController<?>) object).getValue() == view;
    }
  }

  private int scrollState;
  private float currentPageFactor;

  private void setCurrentPageFactor (float factor) {
    if (this.currentPageFactor != factor) {
      this.currentPageFactor = factor;
      updatePositions();
    }
  }

  private boolean hasLeftButton () {
    return listener != null && !listener.isEmojiInputEmpty();
  }

  private boolean noInlineSearch;

  public void setNoInlineSearch () {
    noInlineSearch = true;
  }

  private boolean hasRightButton () {
    ViewController<?> c = adapter.getCachedItem(1);
    return c != null && !noInlineSearch && ((EmojiMediaListController) c).needSearchButton();
  }

  private void updatePositions () {
    emojiSectionsView.setTranslationX((float) (emojiSectionsView.getMeasuredWidth()) * currentPageFactor * (Lang.rtl() ? 1f : -1f));
    if (mediaSectionsView != null) {
      mediaSectionsView.setTranslationX(mediaSectionsView.getMeasuredWidth() * (1f - currentPageFactor) * (Lang.rtl() ? -1f : 1f));
    }
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    setCurrentPageFactor((float) position + positionOffset);

    if (affectHeight) {
      float factor = fromHeightHideFactor + Math.abs(fromPageFactor - currentPageFactor) * heightFactorDiff;
      moveHeader(factor, false);
    }
  }

  @Override
  public void onPageSelected (int position) {
    Settings.instance().setEmojiPosition(position);
    boolean hasLeft = hasLeftButton();
    boolean hasRight = hasRightButton();
    if (hasLeft && hasRight) {
      if (position == 0) {
        circleButton.replaceIcon(R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET));
      } else {
        circleButton.replaceIcon(R.drawable.baseline_search_24);
      }
    } else if (hasLeft || hasRight) {
      setCircleVisible((hasLeft && position == 0) || (hasRight && position == 1), true, position == 0 ? R.drawable.baseline_backspace_24 : R.drawable.baseline_search_24, position == 0 ? -Screen.dp(BACKSPACE_OFFSET) : 0);
    }
    resetScrollState();
  }

  private boolean affectHeight;
  private float fromHeightHideFactor, heightFactorDiff, fromPageFactor;

  private float getDesiredPageFactor (int pageIndex) {
    if (pageIndex == 1) {
      ViewController<?> c = adapter.getCachedItem(1);
      if (c != null) {
        return ((EmojiMediaListController) c).getDesiredHeaderHideFactor();
      }
    }
    return 0f;
  }

  private void setAffectHeight (boolean affectHeight) {
    if (this.affectHeight != affectHeight) {
      this.affectHeight = affectHeight;
      if (affectHeight) {
        fromHeightHideFactor = headerHideFactor;
        fromPageFactor = currentPageFactor;
        float toFactor = getDesiredPageFactor(1 - Math.round(currentPageFactor));
        heightFactorDiff = toFactor - headerHideFactor;
      }
    }
  }

  @Override
  public void onPageScrollStateChanged (int state) {
    this.scrollState = state;
    setAffectHeight(state != ViewPager.SCROLL_STATE_IDLE);
  }

  public boolean canSlideBack () {
    return scrollState == ViewPager.SCROLL_STATE_IDLE && currentPageFactor == 0f;
  }

  // layout and callback

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  private int lastMeasuredWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Keyboard.getSize(), MeasureSpec.EXACTLY));
    int width = getMeasuredWidth();
    if (width != 0 && lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      updatePositions();
      adapter.updateCachedItemsSpanCounts();
    }
  }

  // Icon

  private int getCurrentMediaEmojiSection () {
    ViewController<?> c = adapter.getCachedItem(1);
    if (c instanceof EmojiMediaListController) {
      return ((EmojiMediaListController) c).getMediaSection();
    }
    return Settings.instance().getEmojiMediaSection();
  }

  public @EmojiMediaType int getCurrentEmojiSection () {
    if (allowMedia) {
      int currentItem = pager.getCurrentItem();
      if (currentItem == 1) {
        return getCurrentMediaEmojiSection();
      }
    }
    return EmojiMediaType.EMOJI;
  }

  public void setPreferredSection (@EmojiMediaType int section) {
    if (allowMedia) {
      Settings.instance().setEmojiMediaSection(section);
      setMediaSection(section == EmojiMediaType.GIF);
    }
  }

  public Listener getListener () {
    return listener;
  }

  public void setMediaSection (boolean isGif) {
    if (emojiSections.size() > 7) {
      emojiSections.get(7).changeIcon(isGif ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24);
    }
  }

  public static @EmojiMediaType int getTargetSection () {
    int position = Settings.instance().getEmojiPosition();
    if (position == 0) {
      return EmojiMediaType.EMOJI;
    }
    return Settings.instance().getEmojiMediaSection();
  }

  public static @DrawableRes int getTargetIcon (boolean isMessage) {
    int position = Settings.instance().getEmojiPosition();

    if (position == 0) {
      return isMessage ? R.drawable.deproko_baseline_insert_emoticon_26 : R.drawable.baseline_emoticon_outline_24;
    }

    int section = Settings.instance().getEmojiMediaSection();
    if (isMessage) {
      return section == EmojiMediaType.GIF ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_insert_sticker_26;
    } else {
      return section == EmojiMediaType.GIF ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24;
    }
  }

  // Legacy

  public void reset () {
    ViewController<?> c = adapter.getCachedItem(1);
    if (c != null) {
      ((EmojiMediaListController) c).applyScheduledChanges();
    }
  }

  public void destroy () {
    adapter.destroyCachedItems();
  }

  public void rebuildLayout () {
    // Nothing to do?
  }

  public void invalidateAll () {
    if (adapter != null) {
      adapter.invalidateCachedItems();
    }
  }

  public int getSize () {
    return Keyboard.getSize();
  }

  private static final int STATE_NONE = 0;
  private static final int STATE_AWAITING_SHOW = 1;
  private static final int STATE_AWAITING_HIDE = 2;

  private int keyboardState;

  public void showKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_SHOW;
    Keyboard.show(input);
  }

  public void hideKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_HIDE;
    Keyboard.hide(input);
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (keyboardState == STATE_AWAITING_SHOW && visible) {
      framesDropped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 45 : 55;
    } else if (keyboardState == STATE_AWAITING_HIDE && !visible) {
      keyboardState = STATE_NONE;
    }
  }

  private int framesDropped;

  @Override
  public boolean onPreDraw () {
    if (keyboardState == STATE_AWAITING_SHOW || keyboardState == STATE_AWAITING_HIDE) {
      if (++framesDropped >= 60) {
        framesDropped = 0;
        keyboardState = STATE_NONE;
        return true;
      }
      return false;
    }

    return true;
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (Lang.hasDirectionChanged(event, arg1)) {
      if (mediaSectionsView != null) {
        ((LinearLayoutManager) mediaSectionsView.getLayoutManager()).setReverseLayout(Lang.rtl());
      }
      if (pager != null) {
        pager.checkRtl();
      }
    }
  }
}
