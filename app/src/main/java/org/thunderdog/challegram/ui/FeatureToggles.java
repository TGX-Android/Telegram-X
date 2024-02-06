package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.collection.SparseArrayCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.Arrays;

import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.RunnableBool;

public final class FeatureToggles {
  public static boolean SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES = true;
  public static boolean COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK = false;
  public static boolean COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH = true;
  public static boolean CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP = true;
  public static boolean ALWAYS_SHOW_MARK_AS_READ_ACTION_IN_THREAD_PREVIEW = false;
  public static boolean SCROLL_TO_HEADER_MESSAGE_ON_THREAD_FIRST_OPEN = false;

  public static class Controller extends RecyclerViewController<Void> implements View.OnClickListener {

    private final SparseArrayCompat<FutureBool> valueSuppliers = new SparseArrayCompat<>();
    private final SparseArrayCompat<RunnableBool> valueConsumers = new SparseArrayCompat<>();
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
          if (view.getToggler() != null) {
            FutureBool valueSupplier = valueSuppliers.get(view.getId());
            if (valueSupplier != null) {
              view.getToggler().setRadioEnabled(valueSupplier.getBoolValue(), isUpdate);
            }
          }
        }
      };
      adapter.setItems(Arrays.asList(
        offsetSmall(),

        header("Threads > First Open"),
        shadowTop(),
        toggle("Scroll to header message", () -> SCROLL_TO_HEADER_MESSAGE_ON_THREAD_FIRST_OPEN, (value) -> SCROLL_TO_HEADER_MESSAGE_ON_THREAD_FIRST_OPEN = value),
        descriptionSmall(HtmlCompat.fromHtml("<b>On</b> - Trying to show header message fully unless it takes more than half of the RecyclerView's height in which messages display, in which case it should scroll to the maximum position that fits that half", HtmlCompat.FROM_HTML_MODE_COMPACT)),
        descriptionSmall(HtmlCompat.fromHtml("<b>Off</b> - Showing with displayed header message preview showing and \"Discussion started\" aligned right below it", HtmlCompat.FROM_HTML_MODE_COMPACT)),
        shadowBottom(),

        header("Threads > Preview"),
        shadowTop(),
        toggle("Always show \"Mark as Read\" action", () -> ALWAYS_SHOW_MARK_AS_READ_ACTION_IN_THREAD_PREVIEW, (value) -> ALWAYS_SHOW_MARK_AS_READ_ACTION_IN_THREAD_PREVIEW = value),
        shadowBottom(),

        header("Comment Button"),
        shadowTop(),
        toggle("Bubble button always dark", () -> COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK, (value) -> COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK = value),
        toggle("Bubble button has min width (" + Config.COMMENTS_BUBBLE_BUTTON_MIN_WIDTH + "dp)", () -> COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH, (value) -> COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH = value),
        shadowBottom(),

        header("Channel Profile"),
        shadowTop(),
        toggle("Floating button opens discussion group", () -> CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP, (value) -> CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP = value),
        shadowBottom(),

        header("Replies Chat"),
        shadowTop(),
        toggle("Show \"View in chat\" button like for channel comments", () -> SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES, (value) -> SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES = value),
        shadowBottom()
      ), true);
      recyclerView.setAdapter(adapter);
    }

    @Override
    public void onClick (View v) {
      if (v.getId() != 0) {
        boolean newValue = adapter.toggleView(v);
        RunnableBool valueConsumer = valueConsumers.get(v.getId());
        if (valueConsumer != null) {
          valueConsumer.runWithBool(newValue);
        }
      }
    }

    private ListItem shadowTop () {
      return new ListItem(ListItem.TYPE_SHADOW_TOP);
    }

    private ListItem shadowBottom () {
      return new ListItem(ListItem.TYPE_SHADOW_BOTTOM);
    }

    private ListItem offsetSmall () {
      return new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL);
    }

    private ListItem header (CharSequence text) {
      return new ListItem(ListItem.TYPE_HEADER, 0, 0, text, false);
    }

    private ListItem toggle (CharSequence text, FutureBool valueSupplier, RunnableBool valueConsumer) {
      int id = ViewCompat.generateViewId();
      valueSuppliers.append(id, valueSupplier);
      valueConsumers.append(id, valueConsumer);
      return new ListItem(ListItem.TYPE_RADIO_SETTING, id, 0, text, false);
    }

    private ListItem descriptionSmall (CharSequence text) {
      return new ListItem(ListItem.TYPE_DESCRIPTION_SMALL, 0, 0, text, false);
    }
  }
}