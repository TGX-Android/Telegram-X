package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawModifier;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditChatReactionsController extends EditBaseController<EditChatReactionsController.Args> implements View.OnClickListener {
  private final ArrayList<String> selectedReactions = new ArrayList<>();
  private boolean hasAnyChanges;

  public static class Args {
    public long chatId;
    public String[] availableReactions;

    public Args (long chatId, String[] availableReactions) {
      this.chatId = chatId;
      this.availableReactions = availableReactions;
    }
  }

  public EditChatReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    selectedReactions.clear();
    selectedReactions.addAll(Arrays.asList(args.availableReactions));
  }

  @Override
  public int getId () {
    return R.id.controller_manageReactions;
  }

  private SettingsAdapter adapter;

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();

    if (isDoneInProgress()) {
      return;
    }

    if (view.getId() == R.id.btn_manageReactionsEntry) {
      if (selectedReactions.contains(item.getStringValue())) {
        selectedReactions.remove(item.getStringValue());
      } else {
        selectedReactions.add(item.getStringValue());
      }

      item.setSelected(selectedReactions.contains(item.getStringValue()));
      ((SettingView) view).findCheckBox().setChecked(item.isSelected(), true);

      adapter.updateValuedSettingById(R.id.btn_manageReactionsGlobal);
      checkDoneButton();
    } else if (view.getId() == R.id.btn_manageReactionsGlobal) {
      boolean selectAll = ((SettingView) view).getToggler().toggle(true);

      selectedReactions.clear();
      if (selectAll) {
        selectedReactions.addAll(Arrays.asList(tdlib.getActiveReactionsEmoji()));
      }

      adapter.updateAllValuedSettingsById(R.id.btn_manageReactionsEntry);
      checkDoneButton();
    }
  }

  @Override
  protected boolean onDoneClick () {
    performRequest();
    return true;
  }

  private void performRequest () {
    if (isDoneInProgress()) {
      return;
    }

    setDoneInProgress(true);
    setStackLocked(true);
    tdlib.client().send(new TdApi.SetChatAvailableReactions(getArgumentsStrict().chatId, selectedReactions.toArray(new String[0])), (result) -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setStackLocked(false);
        setDoneInProgress(false);
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          navigateBack();
        } else {
          showError(TD.toErrorString(result));
        }
      }
    }));
  }

  private void showError (CharSequence text) {
    context.tooltipManager()
      .builder(getDoneButton())
      .show(EditChatReactionsController.this,
        tdlib,
        R.drawable.baseline_error_24,
        text
      );
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        if (item.getId() == R.id.btn_manageReactionsEntry) {
          ImageFile staticIconFile = new ImageFile(tdlib, tdlib.getReaction(item.getStringValue()).staticIcon.sticker);
          staticIconFile.setSize(Screen.dp(32f));
          staticIconFile.setNoBlur();
          view.getReceiver().requestFile(staticIconFile);
          view.forcePadding(Screen.dp(56f), 0);
          item.setSelected(selectedReactions.contains(item.getStringValue()));
          view.findCheckBox().setChecked(item.isSelected(), isUpdate);
        } else if (item.getId() == R.id.btn_manageReactionsGlobal) {
          item.setSelected(!selectedReactions.isEmpty());
          view.getToggler().setRadioEnabled(item.isSelected(), isUpdate);
        }
      }
    };

    buildCells();

    recyclerView.setAdapter(adapter);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.bottom = ((ListItem) view.getTag()).getViewType() == ListItem.TYPE_ZERO_VIEW ? Screen.dp(56f) + Screen.dp(16f) * 2 : 0;
      }
    });

    setDoneVisible(false);
    setDoneIcon(R.drawable.baseline_check_24);
  }

  @Override
  protected void setDoneVisible (boolean isVisible) {
    if (isVisible != isDoneVisible()) {
      super.setDoneVisible(isVisible);
      recyclerView.invalidateItemDecorations();
      adapter.notifyItemChanged(adapter.getItemCount() - 1);
    }
  }

  private void buildCells () {
    Args args = getArgumentsStrict();
    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_manageReactionsGlobal, 0, R.string.ReactionManageEnable, args.availableReactions.length > 0));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, tdlib.isChannel(args.chatId) ? R.string.ReactionManageEnableHintChannel : R.string.ReactionManageEnableHintGroup));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ReactionManageList));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    for (TdApi.Reaction supportedReaction : tdlib.getActiveReactions()) {
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_manageReactionsEntry, 0, supportedReaction.title, false).setStringValue(supportedReaction.reaction).setDrawModifier(new DrawModifier() {
        @Override
        public void afterDraw (View view, Canvas c) {
          ImageReceiver receiver = ((SettingView) view).getReceiver();
          int left = Screen.dp(18f);
          int size = Screen.dp(24f);
          receiver.setBounds(left, view.getMeasuredHeight() / 2 - size / 2, left + size, view.getMeasuredHeight() / 2 + size / 2);
          receiver.draw(c);
        }
      }));

      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    items.remove(items.size() - 1);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  @Override
  protected boolean needShowAnimationDelay () {
    return false;
  }

  private String[] asArray () {
    return selectedReactions.toArray(new String[0]);
  }

  private boolean hasAnyChanges () {
    if (selectedReactions.isEmpty() && getArgumentsStrict().availableReactions.length == 0) {
      return false;
    }

    String[] selected = asArray();
    String[] goldenMaster = getArgumentsStrict().availableReactions;
    Arrays.sort(selected, String::compareTo);
    Arrays.sort(goldenMaster, String::compareTo);

    return !Arrays.equals(selected, goldenMaster);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }

    return false;
  }

  private void checkDoneButton () {
    hasAnyChanges = hasAnyChanges();
    setDoneVisible(hasAnyChanges);
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !hasAnyChanges;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Reactions);
  }
}
