package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

/**
 * Date: 8/19/17
 * Author: default
 */

public class EditChatReactionsController extends EditBaseController<EditChatReactionsController.Args> implements View.OnClickListener, TdlibCache.BasicGroupDataChangeListener {
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
  }

  @Override
  public int getId () {
    return R.id.controller_manageReactions;
  }

  private SettingsAdapter adapter;

  @Override
  @SuppressWarnings("WrongConstant")
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();

    if (isDoneInProgress()) {
      return;
    }


  }

  @Override
  protected boolean onDoneClick () {
    performRequest(false);
    return true;
  }

  private void performRequest (boolean force) {
    if (isDoneInProgress() && !force) {
      return;
    }

    /**
     * if (!isDestroyed()) {
     *           setStackLocked(false);
     *           setDoneInProgress(false);
     *           if (success) {
     *             ViewController<?> c = previousStackItem();
     *             if (c instanceof ContactsController) {
     *               destroyStackItemAt(stackSize() - 2);
     *             }
     *             onSaveCompleted();
     *           } else {
     *             showError(error != null && TD.ERROR_USER_PRIVACY.equals(error.message) ?
     *               Lang.getString(R.string.errorPrivacyAddMember) :
     *               TD.toErrorString(error)
     *             );
     *           }
     *         }
     */
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      @SuppressWarnings("WrongConstant")
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {

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

    if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
      tdlib.cache().subscribeToGroupUpdates(ChatId.toBasicGroupId(getArgumentsStrict().chatId), this);
    }
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
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_manageReactionsEntry, 0, supportedReaction.reaction + " / " +supportedReaction.title, false));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  @Override
  protected boolean needShowAnimationDelay () {
    return false;
  }

  private boolean hasAnyChanges () {
    return false;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }
    return false;
  }

  private void checkDoneButton () {
    //if (!isNewRuleSet()) {
      setDoneVisible(hasAnyChanges());
    //}
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Reactions);
  }
}
