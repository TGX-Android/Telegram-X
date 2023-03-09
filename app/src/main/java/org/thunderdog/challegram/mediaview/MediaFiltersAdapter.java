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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.theme.ThemeColorId;

import java.util.ArrayList;

public class MediaFiltersAdapter extends RecyclerView.Adapter<MediaFiltersAdapter.Holder> implements ColorPickerWrap.Listener, SliderFilterWrapView.Callback, BlurButtonsWrap.Listener {
  private final Context context;
  private final RecyclerView.LayoutManager manager;
  private final ArrayList<Item> items;
  private FiltersState state;

  private Item shadowsColorIdItem, highlightsColorIdItem;

  public MediaFiltersAdapter (Context context, RecyclerView.LayoutManager manager) {
    this.context = context;
    this.manager = manager;

    this.items = new ArrayList<>();
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_ENHANCE, R.string.Enhance));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_EXPOSURE, R.string.Exposure));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_CONTRAST, R.string.Contrast));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_WARMTH, R.string.Warmth));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_SATURATION, R.string.Saturation));

    this.items.add(shadowsColorIdItem = new Item(Item.TYPE_COLOR_PICKER, FiltersState.KEY_SHADOWS_COLOR_ID, R.string.Shadows));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_SHADOWS, 0));
    this.items.add(highlightsColorIdItem = new Item(Item.TYPE_COLOR_PICKER, FiltersState.KEY_HIGHLIGHTS_COLOR_ID, R.string.Highlights));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_HIGHLIGHTS, 0));

    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_FADE, R.string.Fade));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_VIGNETTE, R.string.Vignette));
    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_GRAIN, R.string.Grain));

    // TODO this.items.add(new Item(Item.TYPE_BLUR_PICKER, FiltersState.KEY_BLUR_TYPE, R.string.Blur));
    // TODO this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_BLUR_STRENGTH, 0));

    this.items.add(new Item(Item.TYPE_SLIDER, FiltersState.KEY_SHARPEN, R.string.Sharpen));
  }

  public void setFilterState (FiltersState state) {
    int oldItemCount = getItemCount();
    this.state = state;

    int highlightsColorId = state.getValue(FiltersState.KEY_HIGHLIGHTS_COLOR_ID);
    highlightsColorIdItem.setFillingColorId(highlightsColorId == 0 ? R.id.theme_color_white : highlightsColorId);
    int shadowsColorId = state.getValue(FiltersState.KEY_SHADOWS_COLOR_ID);
    shadowsColorIdItem.setFillingColorId(shadowsColorId == 0 ? R.id.theme_color_white : shadowsColorId);


    U.notifyItemsReplaced(this, oldItemCount);
  }

  public int indexOfItemByKey (int key) {
    int i = 0;
    for (Item item : items) {
      if (item.getKey() == key) {
        return i;
      }
      i++;
    }
    return -1;
  }

  @Override
  public Holder onCreateViewHolder (ViewGroup parent, int viewType) {
    switch (viewType) {
      case Item.TYPE_SLIDER: {
        SliderFilterWrapView wrapView = new SliderFilterWrapView(context);
        wrapView.setCallback(this);
        return new Holder(wrapView);
      }
      case Item.TYPE_COLOR_PICKER: {
        ColorPickerWrap pickerWrap = new ColorPickerWrap(context);
        pickerWrap.setListener(this);
        return new Holder(pickerWrap);
      }
      case Item.TYPE_BLUR_PICKER: {
        BlurButtonsWrap blurWrap = new BlurButtonsWrap(context);
        blurWrap.setListener(this);
        return new Holder(blurWrap);
      }
    }
    throw new RuntimeException("viewType == " + viewType);
  }

  @Override
  public void onBindViewHolder (Holder holder, int position) {
    Item item = items.get(position);
    holder.itemView.setTag(item);
    switch (holder.getItemViewType()) {
      case Item.TYPE_SLIDER: {
        String name = item.getString();
        int key = item.getKey();
        int value = state.getValue(key); // , key == FiltersState.KEY_BLUR_STRENGTH ? 50 : 0
        int anchorMode = FiltersState.canBeNegative(key) ? SliderView.ANCHOR_MODE_CENTER : SliderView.ANCHOR_MODE_START;
        ((SliderFilterWrapView) holder.itemView).setData(name, value, (float) value / 100f, anchorMode, item.getFillingColorId(), true);
        //  key != FiltersState.KEY_BLUR_STRENGTH || state.getValue(FiltersState.KEY_BLUR_TYPE) != 0
        break;
      }
      case Item.TYPE_COLOR_PICKER: {
        String name = item.getString();
        int key = item.getKey();
        int value = state.getValue(key);
        int[] colorIds = key == FiltersState.KEY_HIGHLIGHTS ? FiltersState.HIGHLIGHTS_TINT_COLOR_IDS : FiltersState.SHADOWS_TINT_COLOR_IDS;
        ((ColorPickerWrap) holder.itemView).setData(name, colorIds, value);
        break;
      }
      case Item.TYPE_BLUR_PICKER: {
        int key = item.getKey();
        int value = state.getValue(key);
        ((BlurButtonsWrap) holder.itemView).setData(value);
        break;
      }
    }
  }

  @Override
  public boolean allowColorChanges () {
    return callback == null || callback.canApplyChanges();
  }

  @Override
  public void onColorChanged (ColorPickerWrap wrap, @ThemeColorId int newColorId) {
    Item item = (Item) wrap.getTag();
    int i = indexOfItemByKey(item.getKey());
    if (i != -1) {
      View view = manager.findViewByPosition(i + 1);
      if (view != null) {
        ((SliderFilterWrapView) view).setColorId(newColorId == 0 ? R.id.theme_color_white : newColorId);
      } else {
        notifyItemChanged(i + 1);
      }
    }

    if (state.setValue(item.getKey(), newColorId)) {
      if (callback != null) {
        callback.onRequestRender(item.getKey());
      }
    }
  }

  @Override
  public boolean allowBlurChanges () {
    return callback == null || callback.canApplyChanges();
  }

  @Override
  public void onBlurModeChanged (BlurButtonsWrap wrap, int newMode) {
    /* TODO
    Item item = (Item) wrap.getTag();
    int i = indexOfItemByKey(item.getKey());
    if (i != -1) {
      View view = manager.findViewByPosition(i + 1);
      if (view != null) {
        ((SliderWrapView) view).setSlideEnabled(newMode != 0);
      } else {
        notifyItemChanged(i + 1);
      }
    }*/

    // TODO show/hide blur controls
  }

  @Override
  public boolean canApplySliderChanges () {
    return callback == null || callback.canApplyChanges();
  }

  @Override
  public void onChangeStarted (SliderFilterWrapView wrapView) {

  }

  @Override
  public void onChangeEnded (SliderFilterWrapView wrapView) {

  }

  public interface Callback {
    boolean canApplyChanges ();
    void onRequestRender (int changedKey);
  }

  private Callback callback;

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  public void onValueChanged (SliderFilterWrapView wrapView, int value) {
    Item item = (Item) wrapView.getTag();
    if (state.setValue(item.getKey(), value)) {
      if (callback != null) {
        callback.onRequestRender(item.getKey());
      }
    }
  }

  @Override
  public int getItemViewType (int position) {
    return items.get(position).getType();
  }

  @Override
  public int getItemCount () {
    return state != null ? items.size() : 0;
  }

  public static class Holder extends RecyclerView.ViewHolder {
    public Holder (View itemView) {
      super(itemView);
    }
  }

  private static class Item {
    public static final int TYPE_SLIDER = 0;
    public static final int TYPE_COLOR_PICKER = 1;
    public static final int TYPE_BLUR_PICKER = 2;

    private int type;
    private int key;
    private @StringRes int stringRes;

    private @ThemeColorId int fillingColorId;

    public Item (int type, int key, int stringRes) {
      this.type = type;
      this.key = key;
      this.stringRes = stringRes;
      this.fillingColorId = R.id.theme_color_white;
    }

    public boolean setFillingColorId (int colorId) {
      if (this.fillingColorId != colorId) {
        this.fillingColorId = colorId;
        return true;
      }
      return false;
    }

    public @ThemeColorId int getFillingColorId () {
      return fillingColorId;
    }

    public int getKey () {
      return key;
    }

    public int getType () {
      return type;
    }

    public String getString () {
      return stringRes != 0 ? Lang.getString(stringRes) : "";
    }
  }
}
