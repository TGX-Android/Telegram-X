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
 * File created for story ring color customization
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class StoryColorPickerController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener {

  private static final int MAX_COLORS = 3;
  private static final int MIN_COLORS = 1;

  private int[] currentColors;
  private SettingsAdapter adapter;
  private PreviewRingView previewView;

  public StoryColorPickerController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    currentColors = Settings.instance().getStoryRingColors().clone();
  }

  @Override
  public int getId () {
    return R.id.controller_storyColorPicker;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StoryRingColors);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_storyColor) {
          int colorIndex = item.getIntValue();
          if (colorIndex >= 0 && colorIndex < currentColors.length) {
            view.setData(String.format("#%06X", currentColors[colorIndex] & 0xFFFFFF));
            view.setIconColorId(ColorId.NONE);
            // Set the color swatch
            view.setTag(R.id.data_color, currentColors[colorIndex]);
          }
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        previewView = new PreviewRingView(context);
        previewView.setColors(currentColors);
        previewView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(100)));
        return new SettingHolder(previewView);
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();

    // Preview section
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StoryRingPreview));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.preview));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Colors section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StoryRingColors));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addColorItems(items);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Actions
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_resetStoryColors, R.drawable.baseline_undo_24, R.string.ResetToDefault));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, true);
    recyclerView.setAdapter(adapter);
  }

  private void addColorItems (ArrayList<ListItem> items) {
    for (int i = 0; i < currentColors.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_storyColor, R.drawable.baseline_palette_24,
        Lang.getString(R.string.xColorN, i + 1)).setIntValue(i));
    }
    if (currentColors.length < MAX_COLORS) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_addStoryColor, R.drawable.baseline_add_24, R.string.AddColor));
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_storyColor) {
      ListItem item = (ListItem) v.getTag();
      int colorIndex = item.getIntValue();
      if (colorIndex >= 0 && colorIndex < currentColors.length) {
        showColorPicker(colorIndex, currentColors[colorIndex]);
      }
    } else if (id == R.id.btn_addStoryColor) {
      addColor();
    } else if (id == R.id.btn_resetStoryColors) {
      resetToDefault();
    }
  }

  @Override
  public boolean onLongClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_storyColor && currentColors.length > MIN_COLORS) {
      ListItem item = (ListItem) v.getTag();
      int colorIndex = item.getIntValue();
      showOptions(null, new int[] { R.id.btn_delete }, new String[] { Lang.getString(R.string.Delete) }, new int[] { OptionColor.RED }, new int[] { R.drawable.baseline_delete_24 }, (optView, optId) -> {
        if (optId == R.id.btn_delete) {
          removeColor(colorIndex);
        }
        return true;
      });
      return true;
    }
    return false;
  }

  private void showColorPicker (int colorIndex, int currentColor) {
    // Show visual color picker popup
    PopupLayout popup = new PopupLayout(context());
    popup.init(true);
    popup.setNeedRootInsets();

    ColorPickerPopupView pickerView = new ColorPickerPopupView(context(), currentColor, color -> {
      setColor(colorIndex, color);
      popup.hideWindow(true);
    }, () -> popup.hideWindow(true));

    popup.showSimplePopupView(pickerView, Screen.dp(350));
  }

  /**
   * Custom color picker popup with HSV selection
   */
  private static class ColorPickerPopupView extends FrameLayoutFix {
    private static final int[] HUE_COLORS = {
      0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
      0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private final Paint huePaint;
    private final Paint satValPaint;
    private final Paint circlePaint;
    private final Paint circleStrokePaint;
    private final RectF hueRect;
    private final RectF satValRect;

    private float hue = 0f;
    private float saturation = 1f;
    private float value = 1f;
    private int selectedColor;

    private final ColorSelectedCallback callback;
    private final Runnable cancelCallback;

    private boolean draggingHue = false;
    private boolean draggingSatVal = false;

    interface ColorSelectedCallback {
      void onColorSelected (int color);
    }

    public ColorPickerPopupView (Context context, int initialColor, ColorSelectedCallback callback, Runnable cancelCallback) {
      super(context);
      this.callback = callback;
      this.cancelCallback = cancelCallback;
      this.selectedColor = initialColor;

      setWillNotDraw(false);
      setBackgroundColor(Theme.fillingColor());

      // Parse initial color to HSV
      float[] hsv = new float[3];
      Color.colorToHSV(initialColor, hsv);
      this.hue = hsv[0];
      this.saturation = hsv[1];
      this.value = hsv[2];

      huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      satValPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      circleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      circleStrokePaint.setStyle(Paint.Style.STROKE);
      circleStrokePaint.setStrokeWidth(Screen.dp(2));
      circleStrokePaint.setColor(0xFFFFFFFF);

      hueRect = new RectF();
      satValRect = new RectF();

      // Add title
      TextView title = new TextView(context);
      title.setText(Lang.getString(R.string.StoryRingColors));
      title.setTextSize(17);
      title.setTypeface(Fonts.getRobotoMedium());
      title.setTextColor(Theme.textAccentColor());
      title.setPadding(Screen.dp(16), Screen.dp(16), Screen.dp(16), Screen.dp(8));
      addView(title, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

      // Add buttons at bottom
      LinearLayout buttonLayout = new LinearLayout(context);
      buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
      buttonLayout.setGravity(Gravity.END);
      buttonLayout.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));

      TextView cancelBtn = new TextView(context);
      cancelBtn.setText(Lang.getString(R.string.Cancel));
      cancelBtn.setTextSize(15);
      cancelBtn.setTypeface(Fonts.getRobotoMedium());
      cancelBtn.setTextColor(Theme.getColor(ColorId.textNeutral));
      cancelBtn.setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
      cancelBtn.setOnClickListener(v -> cancelCallback.run());
      Views.setClickable(cancelBtn);
      buttonLayout.addView(cancelBtn);

      TextView okBtn = new TextView(context);
      okBtn.setText(Lang.getString(R.string.Done));
      okBtn.setTextSize(15);
      okBtn.setTypeface(Fonts.getRobotoMedium());
      okBtn.setTextColor(Theme.getColor(ColorId.textNeutral));
      okBtn.setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
      okBtn.setOnClickListener(v -> callback.onColorSelected(selectedColor));
      Views.setClickable(okBtn);
      buttonLayout.addView(okBtn);

      addView(buttonLayout, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      int padding = Screen.dp(16);
      int topOffset = Screen.dp(50);
      int bottomOffset = Screen.dp(60);
      int hueHeight = Screen.dp(24);
      int gap = Screen.dp(16);

      // Saturation/Value square
      int satValSize = Math.min(w - padding * 2, h - topOffset - bottomOffset - hueHeight - gap * 2);
      int satValLeft = (w - satValSize) / 2;
      satValRect.set(satValLeft, topOffset, satValLeft + satValSize, topOffset + satValSize);

      // Hue bar below sat/val
      hueRect.set(padding, satValRect.bottom + gap, w - padding, satValRect.bottom + gap + hueHeight);

      // Update hue gradient
      huePaint.setShader(new LinearGradient(hueRect.left, 0, hueRect.right, 0, HUE_COLORS, null, Shader.TileMode.CLAMP));

      updateSatValShader();
    }

    private void updateSatValShader () {
      if (satValRect.width() <= 0 || satValRect.height() <= 0) return;

      int pureColor = Color.HSVToColor(new float[] { hue, 1f, 1f });

      // Create saturation/value gradient
      int[] colorsH = { 0xFFFFFFFF, pureColor };
      int[] colorsV = { 0x00000000, 0xFF000000 };

      LinearGradient satGradient = new LinearGradient(satValRect.left, 0, satValRect.right, 0, colorsH, null, Shader.TileMode.CLAMP);
      LinearGradient valGradient = new LinearGradient(0, satValRect.top, 0, satValRect.bottom, colorsV, null, Shader.TileMode.CLAMP);

      // Combine gradients using ComposeShader
      android.graphics.ComposeShader combined = new android.graphics.ComposeShader(satGradient, valGradient, android.graphics.PorterDuff.Mode.MULTIPLY);
      satValPaint.setShader(combined);
    }

    private void updateSelectedColor () {
      float[] hsv = { hue, saturation, value };
      selectedColor = Color.HSVToColor(hsv);
      invalidate();
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      float x = event.getX();
      float y = event.getY();

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          if (hueRect.contains(x, y)) {
            draggingHue = true;
            updateHue(x);
            return true;
          } else if (satValRect.contains(x, y)) {
            draggingSatVal = true;
            updateSatVal(x, y);
            return true;
          }
          break;
        case MotionEvent.ACTION_MOVE:
          if (draggingHue) {
            updateHue(x);
            return true;
          } else if (draggingSatVal) {
            updateSatVal(x, y);
            return true;
          }
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          draggingHue = false;
          draggingSatVal = false;
          break;
      }
      return super.onTouchEvent(event);
    }

    private void updateHue (float x) {
      float ratio = (x - hueRect.left) / hueRect.width();
      ratio = Math.max(0, Math.min(1, ratio));
      hue = ratio * 360f;
      updateSatValShader();
      updateSelectedColor();
    }

    private void updateSatVal (float x, float y) {
      saturation = Math.max(0, Math.min(1, (x - satValRect.left) / satValRect.width()));
      value = Math.max(0, Math.min(1, 1f - (y - satValRect.top) / satValRect.height()));
      updateSelectedColor();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      // Draw sat/val square
      canvas.drawRoundRect(satValRect, Screen.dp(8), Screen.dp(8), satValPaint);

      // Draw sat/val indicator
      float svX = satValRect.left + saturation * satValRect.width();
      float svY = satValRect.top + (1f - value) * satValRect.height();
      circlePaint.setColor(selectedColor);
      canvas.drawCircle(svX, svY, Screen.dp(10), circlePaint);
      canvas.drawCircle(svX, svY, Screen.dp(10), circleStrokePaint);

      // Draw hue bar
      canvas.drawRoundRect(hueRect, Screen.dp(4), Screen.dp(4), huePaint);

      // Draw hue indicator
      float hueX = hueRect.left + (hue / 360f) * hueRect.width();
      float hueY = hueRect.centerY();
      int hueColor = Color.HSVToColor(new float[] { hue, 1f, 1f });
      circlePaint.setColor(hueColor);
      canvas.drawCircle(hueX, hueY, Screen.dp(12), circlePaint);
      canvas.drawCircle(hueX, hueY, Screen.dp(12), circleStrokePaint);

      // Draw color preview circle
      float previewX = getWidth() - Screen.dp(40);
      float previewY = Screen.dp(32);
      circlePaint.setColor(selectedColor);
      canvas.drawCircle(previewX, previewY, Screen.dp(16), circlePaint);
      canvas.drawCircle(previewX, previewY, Screen.dp(16), circleStrokePaint);
    }
  }

  private void setColor (int index, int color) {
    if (index >= 0 && index < currentColors.length) {
      currentColors[index] = color;
      saveColors();
      updateColorsList();
      updatePreview();
    }
  }

  private void addColor () {
    if (currentColors.length < MAX_COLORS) {
      int[] newColors = Arrays.copyOf(currentColors, currentColors.length + 1);
      // Default new color to a nice blue
      newColors[newColors.length - 1] = 0xFF6B7AFF;
      currentColors = newColors;
      saveColors();
      updateColorsList();
      updatePreview();
    }
  }

  private void removeColor (int index) {
    if (currentColors.length > MIN_COLORS && index >= 0 && index < currentColors.length) {
      int[] newColors = new int[currentColors.length - 1];
      for (int i = 0, j = 0; i < currentColors.length; i++) {
        if (i != index) {
          newColors[j++] = currentColors[i];
        }
      }
      currentColors = newColors;
      saveColors();
      updateColorsList();
      updatePreview();
    }
  }

  private void resetToDefault () {
    currentColors = Settings.DEFAULT_STORY_RING_COLORS.clone();
    saveColors();
    updateColorsList();
    updatePreview();
  }

  private void saveColors () {
    Settings.instance().setStoryRingColors(currentColors);
  }

  private void updateColorsList () {
    List<ListItem> items = adapter.getItems();

    // Find the colors section
    int startIndex = -1;
    int endIndex = -1;
    for (int i = 0; i < items.size(); i++) {
      ListItem item = items.get(i);
      if (item.getId() == R.id.btn_storyColor || item.getId() == R.id.btn_addStoryColor) {
        if (startIndex == -1) {
          startIndex = i;
        }
        endIndex = i;
      }
    }

    if (startIndex != -1 && endIndex != -1) {
      // Remove old color items (including separators between them)
      int removeStart = startIndex;
      int removeEnd = endIndex + 1;
      for (int i = removeEnd - 1; i >= removeStart; i--) {
        items.remove(i);
      }

      // Add new color items
      ArrayList<ListItem> newItems = new ArrayList<>();
      addColorItems(newItems);
      items.addAll(removeStart, newItems);

      adapter.notifyDataSetChanged();
    }
  }

  private void updatePreview () {
    if (previewView != null) {
      previewView.setColors(currentColors);
    }
  }

  /**
   * Custom view to preview the story ring gradient
   */
  private static class PreviewRingView extends FrameLayoutFix {
    private final Paint ringPaint;
    private final RectF ringRect;
    private final Paint bgPaint;
    private int[] colors;

    private static final int RING_SIZE_DP = 64;
    private static final int RING_WIDTH_DP = 4;

    public PreviewRingView (Context context) {
      super(context);
      setWillNotDraw(false);

      ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      ringRect = new RectF();

      bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      bgPaint.setColor(Theme.fillingColor());

      colors = Settings.DEFAULT_STORY_RING_COLORS;
    }

    public void setColors (int[] colors) {
      this.colors = colors;
      updateGradient();
      invalidate();
    }

    private void updateGradient () {
      int width = getWidth();
      int height = getHeight();
      if (width > 0 && height > 0 && colors != null && colors.length > 0) {
        if (colors.length == 1) {
          ringPaint.setShader(null);
          ringPaint.setColor(colors[0]);
        } else {
          LinearGradient gradient = new LinearGradient(
            0, 0, width, height,
            colors,
            null,
            Shader.TileMode.CLAMP
          );
          ringPaint.setShader(gradient);
        }
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      updateGradient();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      int centerX = getWidth() / 2;
      int centerY = getHeight() / 2;
      int ringSize = Screen.dp(RING_SIZE_DP);
      int ringWidth = Screen.dp(RING_WIDTH_DP);

      // Draw background circle
      float bgRadius = ringSize / 2f - ringWidth / 2f;
      canvas.drawCircle(centerX, centerY, bgRadius, bgPaint);

      // Draw gradient ring
      float ringRadius = ringSize / 2f;
      ringRect.set(
        centerX - ringRadius,
        centerY - ringRadius,
        centerX + ringRadius,
        centerY + ringRadius
      );
      canvas.drawOval(ringRect, ringPaint);
    }
  }
}
