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
 * File created on 19/04/2024
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.lambda.Destroyable;

public final class ColumnDataPicker implements Destroyable {
  public static class Column {
    public static class StylingOptions {
      public final float weight;

      public StylingOptions (float weight) {
        this.weight = weight;
      }

      public boolean noPadding;


      public StylingOptions setNoPadding (boolean noPadding) {
        this.noPadding = noPadding;
        return this;
      }
    }

    public final List<SimpleStringItem> rows;
    public final StylingOptions style;
    public int index;

    public Column (List<SimpleStringItem> rows, @NonNull StylingOptions style, int index) {
      this.rows = rows;
      this.style = style;
      this.index = index;
    }

    public InfiniteRecyclerView<SimpleStringItem> view;

    private @Nullable InfiniteRecyclerView.MinMaxProvider<SimpleStringItem> minMaxProvider;

    public void setMinMaxProvider (@Nullable InfiniteRecyclerView.MinMaxProvider<SimpleStringItem> minMaxProvider) {
      this.minMaxProvider = minMaxProvider;
    }

    private @Nullable InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> itemChangeListener;

    public void setItemChangeListener (@Nullable InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> itemChangeListener) {
      this.itemChangeListener = itemChangeListener;
    }
  }

  private List<Column> columns;
  private float spacingStart, spacingEnd;

  public ColumnDataPicker () { }

  public void setColumns (List<Column> columns) {
    this.columns = columns;
  }

  public void setSpacing (float spacingStart, float spacingEnd) {
    this.spacingStart = spacingStart;
    this.spacingEnd = spacingEnd;
  }

  public interface CommitListener {
    boolean onCommitRequested (ColumnDataPicker picker, View commitButtonView);
  }

  private PopupLayout popupLayout;

  public PopupLayout popupLayout () {
    return popupLayout;
  }

  public boolean hasVisiblePopUp () {
    return popupLayout != null && popupLayout.isBoundWindowShowing();
  }

  public void dismissPopup (boolean animated) {
    if (popupLayout != null) {
      popupLayout.hideWindow(animated);
      popupLayout = null;
    }
  }

  @Override
  public void performDestroy () {
    dismissPopup(false);
  }

  public void showPopup (ViewController<?> c, CharSequence title, CharSequence commitButtonText, @Nullable ThemeDelegate forcedTheme, @Nullable CommitListener commitListener) {
    if (popupLayout != null)
      throw new IllegalStateException();
    this.popupLayout = c.showPopup(title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsWrap) -> {
      int contentHeight = 0;
      final int pickerHeight = InfiniteRecyclerView.getItemHeight() * 5;

      LinearLayout columnWrap = new LinearLayout(c.context());
      columnWrap.setOrientation(LinearLayout.HORIZONTAL);
      columnWrap.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pickerHeight));
      ViewUtils.setBackground(columnWrap, new LinesHighlightDrawable(forcedTheme));
      if (forcedTheme == null) {
        c.addThemeInvalidateListener(columnWrap);
      }

      if (spacingStart > 0) {
        View emptyView = new View(c.context());
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, spacingStart));
        columnWrap.addView(emptyView);
      }

      EmojiTextView commitButton = newCommitButton(c, forcedTheme);
      Views.setMediumText(commitButton, commitButtonText.toString().toUpperCase());

      for (Column column : columns) {
        InfiniteRecyclerView<SimpleStringItem> picker = new InfiniteRecyclerView<SimpleStringItem>(c.context(), false);
        picker.setNeedSeparators(false);
        picker.setMinMaxProvider(column.minMaxProvider);
        picker.setItemChangeListener(column.itemChangeListener);
        picker.setForcedTheme(forcedTheme);
        picker.addThemeListeners(c);
        if (column.style.noPadding) {
          picker.setItemPadding(0);
        }
        picker.initWithItems(column.rows, column.index);
        picker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, column.style.weight));
        columnWrap.addView(picker);
        column.view = picker;
      }
      optionsWrap.addView(columnWrap);
      contentHeight += pickerHeight;

      if (spacingEnd > 0) {
        View emptyView = new View(c.context());
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, spacingStart));
        columnWrap.addView(emptyView);
      }

      optionsWrap.addView(commitButton);
      contentHeight += Screen.dp(56f);

      if (commitListener != null) {
        commitButton.setOnClickListener(v -> {
          if (commitListener.onCommitRequested(this, v)) {
            dismissPopup(true);
          }
        });
      }

      return contentHeight;
    }, forcedTheme);
  }

  private static EmojiTextView newCommitButton (ViewController<?> c, @Nullable ThemeDelegate forcedTheme) {
    final EmojiTextView buttonView = new EmojiTextView(c.context());
    Views.setClickable(buttonView);
    buttonView.setGravity(Gravity.CENTER);
    buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    buttonView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));
    FillingDrawable drawable = ViewSupport.setThemedBackground(buttonView, ColorId.fillingPositive, forcedTheme != null ? null : c);
    drawable.setForcedTheme(forcedTheme);
    if (forcedTheme != null) {
      buttonView.setTextColor(forcedTheme.getColor(ColorId.fillingPositiveContent));
    } else {
      buttonView.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      c.addThemeTextColorListener(buttonView, ColorId.fillingPositiveContent);
    }
    return buttonView;
  }

  private static class LinesHighlightDrawable extends Drawable {
    private final @Nullable ThemeDelegate forcedTheme;

    public LinesHighlightDrawable (@Nullable ThemeDelegate forcedTheme) {
      this.forcedTheme = forcedTheme;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      int viewWidth = bounds.width();
      int viewHeight = bounds.height();

      int cy = viewHeight / 2;
      int h2 = InfiniteRecyclerView.getItemHeight() / 2;

      c.drawLine(0, cy - h2, viewWidth, cy - h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
      c.drawLine(0, cy + h2, viewWidth, cy + h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }
}
