package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.ReactionsConfigComponent;
import org.thunderdog.challegram.component.chat.ReactionsOverlayComponent;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawModifier;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditChatReactionsController extends EditBaseController<EditChatReactionsController.Args> implements View.OnClickListener {
  private final ArrayList<String> selectedReactions = new ArrayList<>();
  private ReactionsOverlayComponent overlays;

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
  private ReactionsConfigComponent gridAdapter;

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.btn_manageReactionsGlobal) {
      if (selectedReactions.isEmpty()) {
        // select all
        selectedReactions.addAll(Arrays.asList(tdlib.getActiveReactions()));
      } else {
        // deselect
        selectedReactions.clear();
      }

      ((SettingView) view).findCheckBox().toggle();
      gridAdapter.notifyItemRangeChanged(0, gridAdapter.getItemCount(), true);
      adapter.updateValuedSettingById(R.id.btn_manageReactionsGlobal);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (hasAnyChanges()) {
      tdlib.client().send(new TdApi.SetChatAvailableReactions(getArgumentsStrict().chatId, selectedReactions.toArray(new String[0])), result -> {});
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
    overlays = new ReactionsOverlayComponent(context);
    overlays.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    contentView.addView(overlays);

    gridAdapter = new ReactionsConfigComponent(new ReactionsConfigComponent.Delegate() {
      @Override
      public Tdlib provideTdlib () {
        return tdlib;
      }

      @Override
      public String[] getAvailableReactions () {
        return tdlib.getActiveReactions();
      }

      @Override
      public boolean isReactionEnabled (String emoji) {
        return selectedReactions.contains(emoji);
      }

      @Override
      public void toggleReaction (String emoji, int[] coords, Runnable onFirstFrameEnabled) {
        if (selectedReactions.contains(emoji)) {
          selectedReactions.remove(emoji);
          overlays.updateReactionOverlayAlpha(emoji, false);
        } else {
          selectedReactions.add(emoji);
          overlays.addReactionToOverlay(tdlib, emoji, tdlib.getReaction(emoji), onFirstFrameEnabled);
          overlays.updateReactionOverlayLocation(emoji, coords[0], coords[1] - (HeaderView.getHeaderHeight(EditChatReactionsController.this) + HeaderView.getTopOffset()), false);
          overlays.updateReactionOverlayAlpha(emoji, true);
        }

        adapter.updateValuedSettingById(R.id.btn_manageReactionsGlobal);
      }

      @Override
      public TdApi.Reaction getReaction (String emoji) {
        return tdlib.getReaction(emoji);
      }
    });

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        if (item.getId() == R.id.btn_manageReactionsGlobal) {
          int size = selectedReactions.size();
          boolean idState = (size == 0 && view.findCheckBox().isIndeterminate()) || (size != 0 && size != tdlib.getActiveReactionCount());
          item.setSelected(!selectedReactions.isEmpty());
          view.setName(size == 0 ? Lang.getString(R.string.ReactionManageDisabled) : Lang.pluralBold(R.string.ReactionManageEnabled, size));
          view.findCheckBox().setIndeterminate(idState, size != 1 && isUpdate);
          view.findCheckBox().setChecked(item.isSelected(), isUpdate);
        }
      }

      @Override
      protected void setRecyclerViewData (ListItem item, RecyclerView recyclerView, boolean isInitialization) {
        recyclerView.setItemAnimator(null);
        recyclerView.setLayoutManager(new GridLayoutManager(context, calculateSpanCount(Screen.currentWidth(), Screen.currentHeight())));
        recyclerView.setAdapter(gridAdapter);
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }

      private int calculateSpanCount (int width, int height) {
        int minSide = Math.min(width, height);
        int minWidth = minSide / 4;
        return minWidth != 0 ? width / minWidth : 5;
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
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        overlays.onScrolled(dy);
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

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    adapter.updateItemById(R.id.msg_list);
  }

  private void buildCells () {
    Args args = getArgumentsStrict();
    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_manageReactionsGlobal, 0, R.string.ReactionManageEnable));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, tdlib.isChannel(args.chatId) ? R.string.ReactionManageEnableHintChannel : R.string.ReactionManageEnableHintGroup));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ReactionManageList));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL, R.id.msg_list));
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
  public CharSequence getName () {
    return Lang.getString(R.string.Reactions);
  }
}
