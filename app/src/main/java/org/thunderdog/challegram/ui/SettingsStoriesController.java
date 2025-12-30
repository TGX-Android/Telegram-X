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
 * File created for stories settings
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

public class SettingsStoriesController extends RecyclerViewController<Void> implements View.OnClickListener {

  public SettingsStoriesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_storiesSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StoriesSettings);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_storyRingColors) {
          int[] colors = Settings.instance().getStoryRingColors();
          view.setData(colors.length == 1 ? Lang.getString(R.string.SolidColor) :
                       Lang.plural(R.string.xColors, colors.length));
        }
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();

    // Visibility section
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Visibility));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.HideStories)
      .setLongId(Settings.SETTING_FLAG_HIDE_STORIES));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.HideStoriesInfo));

    // Appearance section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Appearance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showAddStoryBorder, 0, R.string.ShowAddStoryBorder));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_storyRingColors, 0, R.string.StoryRingColors));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StoryRingColorsDesc));

    // Behavior section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Behavior));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.StoryQuickReactions)
      .setLongId(Settings.SETTING_FLAG_STORY_QUICK_REACTIONS));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StoryQuickReactionsInfo));

    adapter.setItems(items, true);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_toggleNewSetting) {
      ListItem item = (ListItem) v.getTag();
      boolean value = adapter.toggleView(v);
      Settings.instance().setNewSetting(item.getLongId(), value);
    } else if (id == R.id.btn_showAddStoryBorder) {
      boolean value = adapter.toggleView(v);
      Settings.instance().setNewSetting(Settings.SETTING_FLAG_SHOW_ADD_STORY_BORDER, value);
    } else if (id == R.id.btn_storyRingColors) {
      navigateTo(new StoryColorPickerController(context, tdlib));
    }
  }
}
