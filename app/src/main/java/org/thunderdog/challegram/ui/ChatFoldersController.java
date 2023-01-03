package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.ChatFiltersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.AdapterSubListUpdateCallback;
import org.thunderdog.challegram.util.ListItemDiffUtilCallback;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import me.vkryl.core.MathUtils;
import me.vkryl.core.collection.IntList;

public class ChatFoldersController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener, ChatFiltersListener {
  private static final int MAIN_CHAT_FILTER_ID = Integer.MIN_VALUE;

  private final @IdRes int chatFiltersPreviousItemId = ViewCompat.generateViewId();
  private final @IdRes int recommendedChatFiltersPreviousItemId = ViewCompat.generateViewId();

  private int chatFilterCount, chatFilterGroupItemCount, recommendedChatFilterGroupItemCount;
  private boolean recommendedChatFiltersInitialized;

  private SettingsAdapter adapter;
  private ItemTouchHelper itemTouchHelper;

  public ChatFoldersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_chatFolders;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !recommendedChatFiltersInitialized;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChatFolders);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    itemTouchHelper = RemoveHelper.attach(recyclerView, new ItemTouchHelperCallback());

    TdApi.ChatFilterInfo[] chatFilters = tdlib.chatFilterInfos();
    int mainChatListPosition = tdlib.mainChatListPosition();
    List<ListItem> chatFilterItemList = buildChatFilterItemList(chatFilters, mainChatListPosition);
    chatFilterCount = chatFilters.length;
    chatFilterGroupItemCount = chatFilterItemList.size();

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, chatFiltersPreviousItemId, 0, R.string.ChatFoldersInfo));

    items.addAll(chatFilterItemList);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setDoubleText (ListItem item, int position, DoubleTextView textView, boolean isUpdate) {
        if (item.getId() == R.id.recommendedChatFilter) {
          textView.setAvatarPlaceholder(new AvatarPlaceholder.Metadata(0, item.getIconResource(), R.id.theme_color_icon));
          textView.setText(item.getString(), item.getStringValue());
          textView.setButton(R.string.Add, ChatFoldersController.this);
          //noinspection ConstantConditions
          textView.getButton().setTag(item.getData());
        }
      }

      @SuppressLint("ClickableViewAccessibility")
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setIconColorId(item.getId() == R.id.btn_createNewFolder ? R.id.theme_color_inlineIcon : 0);
        if (isChatFilter(item)) {
          boolean isMainChatFilter = isMainChatFilter(item);
          view.setOnTouchListener(new ChatFilterOnTouchListener());
          view.setClickable(!isMainChatFilter);
          view.setLongClickable(!isMainChatFilter);
        } else {
          view.setOnTouchListener(null);
          view.setClickable(true);
          view.setLongClickable(true);
        }
      }
    };
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    tdlib.listeners().subscribeToChatFiltersUpdates(this);
    updateRecommendedChatFilters();
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromChatFiltersUpdates(this);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    return true;
  }

  private boolean shouldUpdateRecommendedChatFilters = false;

  @Override
  protected void onFocusStateChanged () {
    if (isFocused()) {
      if (shouldUpdateRecommendedChatFilters) {
        shouldUpdateRecommendedChatFilters = false;
        updateRecommendedChatFilters();
      }
    } else {
      shouldUpdateRecommendedChatFilters = true;
    }
  }

  @Override
  public void onChatFiltersChanged (TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition) {
    runOnUiThreadOptional(() -> {
      updateChatFilters(chatFilters, mainChatListPosition);
      if (isFocused()) {
        tdlib.ui().postDelayed(() -> {
          if (!isDestroyed() && isFocused()) {
            updateRecommendedChatFilters();
          }
        }, /* ¯\_(ツ)_/¯ */ 500L);
      }
    });
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_createNewFolder) {
      navigateTo(EditChatFolderController.newFolder(context, tdlib));
    } else if (v.getId() == R.id.chatFilter) {
      ListItem item = (ListItem) v.getTag();
      if (isMainChatFilter(item)) {
        return;
      }
      editChatFilter((TdApi.ChatFilterInfo) item.getData());
    } else if (v.getId() == R.id.recommendedChatFilter) {
      ListItem item = (ListItem) v.getTag();
      TdApi.ChatFilter chatFilter = (TdApi.ChatFilter) item.getData();
      navigateTo(EditChatFolderController.newFolder(context, tdlib, chatFilter));
    } else if (v.getId() == R.id.btn_double) {
      Object tag = v.getTag();
      if (tag instanceof TdApi.ChatFilter) {
        TdApi.ChatFilter chatFilter = (TdApi.ChatFilter) tag;
        createChatFilter(chatFilter);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.chatFilter) {
      ListItem item = (ListItem) v.getTag();
      if (isMainChatFilter(item)) {
        return false;
      }
      showChatFilterOptions((TdApi.ChatFilterInfo) item.getData());
      return true;
    }
    return false;
  }

  private void startDrag (RecyclerView.ViewHolder viewHolder) {
    if (viewHolder == null)
      return;
    ListItem listItem = (ListItem) viewHolder.itemView.getTag();
    if (isMainChatFilter(listItem) && !tdlib.hasPremium()) {
      UI.forceVibrateError(viewHolder.itemView);
      CharSequence markdown = Lang.getString(R.string.PremiumRequiredMoveFolder, Lang.getString(R.string.CategoryMain));
      context()
        .tooltipManager()
        .builder(viewHolder.itemView)
        .icon(R.drawable.msg_folder_reorder)
        .controller(this)
        .show(tdlib, Strings.buildMarkdown(this, markdown))
        .hideDelayed();
      return;
    }
    itemTouchHelper.startDrag(viewHolder);
  }

  private void showChatFilterOptions (TdApi.ChatFilterInfo chatFilterInfo) {
    Options options = new Options.Builder()
      .info(chatFilterInfo.title)
      .item(new OptionItem(R.id.btn_edit, Lang.getString(R.string.EditFolder), OPTION_COLOR_NORMAL, R.drawable.baseline_edit_24))
      .item(new OptionItem(R.id.btn_delete, Lang.getString(R.string.RemoveFolder), OPTION_COLOR_RED, R.drawable.baseline_delete_24))
      .build();
    showOptions(options, (optionItemView, id) -> {
      if (id == R.id.btn_edit) {
        editChatFilter(chatFilterInfo);
      } else if (id == R.id.btn_delete) {
        showRemoveFolderConfirm(chatFilterInfo.id);
      }
      return true;
    });
  }

  private void showRemoveFolderConfirm (int chatFilterId) {
    showConfirm(Lang.getString(R.string.RemoveFolderConfirm), Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OPTION_COLOR_RED, () -> {
      deleteChatFilter(chatFilterId);
    });
  }

  private void editChatFilter (TdApi.ChatFilterInfo chatFilterInfo) {
    tdlib.send(new TdApi.GetChatFilter(chatFilterInfo.id), (result) -> runOnUiThreadOptional(() -> {
      switch (result.getConstructor()) {
        case TdApi.ChatFilter.CONSTRUCTOR:
          TdApi.ChatFilter chatFilter = (TdApi.ChatFilter) result;
          EditChatFolderController controller = new EditChatFolderController(context, tdlib);
          controller.setArguments(new EditChatFolderController.Arguments(chatFilterInfo.id, chatFilter));
          navigateTo(controller);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
        default:
          Log.unexpectedTdlibResponse(result, TdApi.GetChatFilter.class, TdApi.ChatFilter.class, TdApi.Error.class);
          break;
      }
    }));
  }

  private void createChatFilter (TdApi.ChatFilter chatFilter) {
    tdlib.send(new TdApi.CreateChatFilter(chatFilter), tdlib.okHandler(TdApi.ChatFilterInfo.class));
  }

  private void deleteChatFilter (int chatFilterId) {
    tdlib.send(new TdApi.DeleteChatFilter(chatFilterId), tdlib.okHandler());
  }

  private void reorderChatFilters () {
    int firstIndex = indexOfFirstChatFilter();
    int lastIndex = indexOfLastChatFilter();
    if (firstIndex == RecyclerView.NO_POSITION || lastIndex == RecyclerView.NO_POSITION)
      return;
    int mainChatListPosition = 0;
    IntList chatFilterIds = new IntList(chatFilterCount - 1);
    int filterIndex = 0;
    for (int index = firstIndex; index <= lastIndex; index++) {
      ListItem item = adapter.getItem(index);
      if (item == null) {
        updateChatFilters();
        return;
      }
      if (item.getId() == R.id.chatFilter) {
        if (isMainChatFilter(item)) {
          mainChatListPosition = filterIndex;
        } else {
          filterIndex++;
          chatFilterIds.append(item.getIntValue());
        }
      }
    }
    if (mainChatListPosition != 0 && !tdlib.hasPremium()) {
      updateChatFilters();
      return;
    }
    tdlib.send(new TdApi.ReorderChatFilters(chatFilterIds.get(), mainChatListPosition), (result) -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.showError(result);
        runOnUiThreadOptional(this::updateChatFilters);
      }
    });
  }

  private boolean isChatFilter (ListItem item) {
    return item.getId() == R.id.chatFilter;
  }

  private boolean isMainChatFilter (ListItem item) {
    return isChatFilter(item) && item.getIntValue() == MAIN_CHAT_FILTER_ID;
  }

  private boolean canMoveChatFilter (ListItem item) {
    return isChatFilter(item) && (tdlib.hasPremium() || !isMainChatFilter(item));
  }

  private int indexOfFirstChatFilter () {
    int index = indexOfChatFilterGroup();
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 2 /* header, shadowTop */;
  }

  private int indexOfLastChatFilter () {
    int index = indexOfChatFilterGroup();
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + chatFilterGroupItemCount - 2 /* shadowBottom, separator */;
  }

  private int indexOfChatFilterGroup () {
    int index = adapter.indexOfViewById(chatFiltersPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private int indexOfRecommendedChatFilterGroup () {
    int index = adapter.indexOfViewById(recommendedChatFiltersPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private List<ListItem> buildChatFilterItemList (TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition) {
    List<ListItem> itemList = new ArrayList<>(chatFilters.length + 5);
    itemList.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatFolders));
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (TdApi.ChatFilterInfo chatFilter : chatFilters) {
      itemList.add(chatFilterItem(chatFilter));
    }
    int mainChatFilterIndex = MathUtils.clamp(mainChatListPosition, 0, chatFilters.length) + itemList.size() - chatFilters.length;
    itemList.add(mainChatFilterIndex, mainChatFilterItem());
    itemList.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createNewFolder, R.drawable.baseline_create_new_folder_24, R.string.CreateNewFolder).setTextColorId(R.id.theme_color_inlineText));
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, recommendedChatFiltersPreviousItemId));
    return itemList;
  }

  private List<ListItem> buildRecommendedChatFilterItemList (TdApi.RecommendedChatFilter[] recommendedChatFilters) {
    if (recommendedChatFilters.length == 0) {
      return Collections.emptyList();
    }
    List<ListItem> itemList = new ArrayList<>(recommendedChatFilters.length * 2 - 1 + 3);
    itemList.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RecommendedFolders));
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int index = 0; index < recommendedChatFilters.length; index++) {
      if (index > 0) {
        itemList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      itemList.add(recommendedChatFilterItem(recommendedChatFilters[index]));
    }
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return itemList;
  }

  private ListItem mainChatFilterItem () {
    ListItem mainChatFilter = new ListItem(ListItem.TYPE_SETTING, R.id.chatFilter);
    mainChatFilter.setString(R.string.CategoryMain);
    mainChatFilter.setIntValue(MAIN_CHAT_FILTER_ID);
    mainChatFilter.setIconRes(tdlib.hasPremium() ? R.drawable.baseline_drag_handle_24 : R.drawable.deproko_baseline_lock_24);
    return mainChatFilter;
  }

  private ListItem chatFilterItem (TdApi.ChatFilterInfo chatFilterInfo) {
    ListItem item = new ListItem(ListItem.TYPE_SETTING, R.id.chatFilter, R.drawable.baseline_drag_handle_24, Emoji.instance().replaceEmoji(chatFilterInfo.title));
    item.setIntValue(chatFilterInfo.id);
    item.setData(chatFilterInfo);
    return item;
  }

  private ListItem recommendedChatFilterItem (TdApi.RecommendedChatFilter recommendedChatFilter) {
    ListItem item = new ListItem(ListItem.TYPE_DOUBLE_TEXTVIEW, R.id.recommendedChatFilter);
    item.setData(recommendedChatFilter.filter);
    item.setString(recommendedChatFilter.filter.title);
    item.setStringValue(recommendedChatFilter.description);
    item.setIconRes(tdlib.chatFilterIcon(recommendedChatFilter.filter, R.drawable.baseline_folder_24));
    return item;
  }

  private void updateChatFilters () {
    updateChatFilters(tdlib.chatFilterInfos(), tdlib.mainChatListPosition());
  }

  private void updateChatFilters (TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition) {
    int fromIndex = indexOfChatFilterGroup();
    if (fromIndex == RecyclerView.NO_POSITION)
      return;
    List<ListItem> subList = adapter.getItems().subList(fromIndex, fromIndex + chatFilterGroupItemCount);
    List<ListItem> newList = buildChatFilterItemList(chatFilters, mainChatListPosition);
    chatFilterCount = chatFilters.length + 1 /* All Chats */;
    chatFilterGroupItemCount = newList.size();
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(chatFiltersDiff(subList, newList));
    subList.clear();
    subList.addAll(newList);
    diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, fromIndex));
  }

  private void updateRecommendedChatFilters () {
    tdlib.send(new TdApi.GetRecommendedChatFilters(), (result) -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.RecommendedChatFilters.CONSTRUCTOR) {
          updateRecommendedChatFilters(((TdApi.RecommendedChatFilters) result).chatFilters);
        }
        if (!recommendedChatFiltersInitialized) {
          recommendedChatFiltersInitialized = true;
          executeScheduledAnimation();
        }
      });
    });
  }

  private void updateRecommendedChatFilters (TdApi.RecommendedChatFilter[] chatFilters) {
    int fromIndex = indexOfRecommendedChatFilterGroup();
    if (fromIndex == RecyclerView.NO_POSITION)
      return;
    List<ListItem> subList = adapter.getItems().subList(fromIndex, fromIndex + recommendedChatFilterGroupItemCount);
    List<ListItem> newList = buildRecommendedChatFilterItemList(chatFilters);
    if (subList.isEmpty() && newList.isEmpty()) {
      return;
    }
    recommendedChatFilterGroupItemCount = newList.size();
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(recommendedChatFiltersDiff(subList, newList));
    subList.clear();
    subList.addAll(newList);
    diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, fromIndex));
  }

  private static DiffUtil.Callback chatFiltersDiff (List<ListItem> oldList, List<ListItem> newList) {
    return new ListItemDiffUtilCallback(oldList, newList) {
      @Override
      public boolean areItemsTheSame (ListItem oldItem, ListItem newItem) {
        return oldItem.getViewType() == newItem.getViewType() &&
          oldItem.getId() == newItem.getId() &&
          oldItem.getIntValue() == newItem.getIntValue();
      }

      @Override
      public boolean areContentsTheSame (ListItem oldItem, ListItem newItem) {
        return Objects.equals(oldItem.getString(), newItem.getString());
      }
    };
  }

  private static DiffUtil.Callback recommendedChatFiltersDiff (List<ListItem> oldList, List<ListItem> newList) {
    return new ListItemDiffUtilCallback(oldList, newList) {
      @Override
      public boolean areItemsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getViewType() == newItem.getViewType() && oldItem.getId() == newItem.getId()) {
          if (oldItem.getId() == R.id.recommendedChatFilter) {
            return Objects.equals(oldItem.getString(), newItem.getString());
          }
          return true;
        }
        return false;
      }

      @Override
      public boolean areContentsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getId() == R.id.recommendedChatFilter) {
          return oldItem.getIconResource() == newItem.getIconResource() &&
            Objects.equals(oldItem.getString(), newItem.getString()) &&
            Objects.equals(oldItem.getStringValue(), newItem.getStringValue());
        }
        return Objects.equals(oldItem.getString(), newItem.getString());
      }
    };
  }

  private class ItemTouchHelperCallback implements RemoveHelper.ExtendedCallback {
    @Override
    public boolean isLongPressDragEnabled () {
      return false;
    }

    @Override
    public int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      return isChatFilter(item) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
    }

    @Override
    public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      return isChatFilter(item) && !isMainChatFilter(item);
    }

    @Override
    public void onRemove (RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      showRemoveFolderConfirm(item.getIntValue());
    }

    @Override
    public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
      int sourcePosition = source.getAbsoluteAdapterPosition();
      int targetPosition = target.getAbsoluteAdapterPosition();
      if (sourcePosition == RecyclerView.NO_POSITION || targetPosition == RecyclerView.NO_POSITION) {
        return false;
      }
      int firstChatFilterIndex = indexOfFirstChatFilter();
      int lastChatFilterIndex = indexOfLastChatFilter();
      if (firstChatFilterIndex == RecyclerView.NO_POSITION || lastChatFilterIndex == RecyclerView.NO_POSITION) {
        return false;
      }
      if (targetPosition < firstChatFilterIndex || targetPosition > lastChatFilterIndex) {
        return false;
      }
      adapter.moveItem(sourcePosition, targetPosition, /* notify */ true);
      return true;
    }

    @Override
    public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
      ListItem sourceItem = (ListItem) source.itemView.getTag();
      ListItem targetItem = (ListItem) target.itemView.getTag();
      return isChatFilter(sourceItem) && isChatFilter(targetItem) && canMoveChatFilter(targetItem);
    }

    @Override
    public void onCompleteMovement (int fromPosition, int toPosition) {
      reorderChatFilters();
    }
  }

  private class ChatFilterOnTouchListener implements View.OnTouchListener {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch (View view, MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        float paddingStart = ((SettingView) view).getMeasuredPaddingStart();
        boolean shouldStartDrag = Lang.rtl() ? event.getX() > view.getWidth() - paddingStart : event.getX() < paddingStart;
        if (shouldStartDrag) {
          startDrag(getRecyclerView().getChildViewHolder(view));
        }
      }
      return false;
    }
  }
}