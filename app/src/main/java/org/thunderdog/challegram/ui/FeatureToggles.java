package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.Arrays;

public final class FeatureToggles {
  public static boolean SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES = true;
  public static boolean SHOW_VIEW_X_COMMENTS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU = false;
  public static boolean SHOW_DISCUSS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU = true;
  public static boolean COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK = false;
  public static boolean COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH = true;
  public static boolean CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP = true;

  public static class Controller extends RecyclerViewController<Void> implements View.OnClickListener {

    private SettingsAdapter adapter;

    public Controller (Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    @Override
    public int getId () {
      return R.id.controller_featureToggles;
    }

    @Override
    public CharSequence getName () {
      return "Feature Toggles (Not Persistent)";
    }

    @Override
    protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
      adapter = new SettingsAdapter(this) {
        @Override
        protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
          switch (item.getId()) {
            case 0x01:
              view.getToggler().setRadioEnabled(COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK, isUpdate);
              break;
            case 0x02:
              view.getToggler().setRadioEnabled(COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH, isUpdate);
              break;
            case 0x11:
              view.getToggler().setRadioEnabled(SHOW_DISCUSS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU, isUpdate);
              break;
            case 0x12:
              view.getToggler().setRadioEnabled(SHOW_VIEW_X_COMMENTS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU, isUpdate);
              break;
            case 0x21:
              view.getToggler().setRadioEnabled(CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP, isUpdate);
              break;
            case 0x31:
              view.getToggler().setRadioEnabled(SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES, isUpdate);
              break;
          }
        }
      };
      adapter.setItems(Arrays.asList(
        new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL),
        new ListItem(ListItem.TYPE_HEADER, 0, 0, "Comment Button", false),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x01, 0, "Bubble button always dark", COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x02, 0, "Bubble button has min width (" + Config.COMMENTS_BUBBLE_BUTTON_MIN_WIDTH + "dp)", COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, "Channel Post", false),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x11, 0, "Show \"Discuss\" button in context menu", SHOW_DISCUSS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x12, 0, "Show \"View X Comments\" button in context menu", SHOW_VIEW_X_COMMENTS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, "Channel Profile", false),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x21, 0, "Floating button opens discussion group", CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, "Replies Chat", false),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, 0x31, 0, "Show \"View in chat\" button like for channel comments", SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      ), true);
      recyclerView.setAdapter(adapter);
    }

    @Override
    public void onClick (View v) {
      adapter.toggleView(v);
      switch (v.getId()) {
        case 0x01:
          COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK = !COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK;
          break;
        case 0x02:
          COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH = !COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH;
          break;
        case 0x11:
          SHOW_DISCUSS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU = !SHOW_DISCUSS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU;
          break;
        case 0x12:
          SHOW_VIEW_X_COMMENTS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU = !SHOW_VIEW_X_COMMENTS_BUTTON_IN_CHANNEL_POST_CONTEXT_MENU;
          break;
        case 0x21:
          CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP = !CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP;
          break;
        case 0x31:
          SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES = !SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES;
          break;
      }
    }
  }
}