package org.thunderdog.challegram.ui;

import static org.thunderdog.challegram.telegram.TdlibSettingsManager.CHAT_FOLDER_STYLE_ICON_AND_TITLE;
import static org.thunderdog.challegram.telegram.TdlibSettingsManager.CHAT_FOLDER_STYLE_ICON_ONLY;
import static org.thunderdog.challegram.telegram.TdlibSettingsManager.CHAT_FOLDER_STYLE_TITLE_ONLY;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.ChatFiltersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
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
  private static final long MAIN_CHAT_FILTER_ID = Long.MIN_VALUE;
  private static final long ARCHIVE_CHAT_FILTER_ID = Long.MIN_VALUE + 1;

  private final @IdRes int chatFiltersPreviousItemId = ViewCompat.generateViewId();
  private final @IdRes int recommendedChatFiltersPreviousItemId = ViewCompat.generateViewId();

  private int chatFilterGroupItemCount, recommendedChatFilterGroupItemCount;
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
    int archiveChatListPosition = tdlib.settings().archiveChatListPosition();
    List<ListItem> chatFilterItemList = buildChatFilterItemList(chatFilters, mainChatListPosition, archiveChatListPosition);
    chatFilterGroupItemCount = chatFilterItemList.size();

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, chatFiltersPreviousItemId, 0, R.string.ChatFoldersInfo));

    items.addAll(chatFilterItemList);

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatFoldersSettings));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatFolderStyle, 0, R.string.ChatFoldersStyle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));

    adapter = new SettingsAdapter(this) {
      @SuppressLint("ClickableViewAccessibility")
      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        SettingView settingView = new SettingView(context, tdlib);
        settingView.setType(SettingView.TYPE_SETTING);
        settingView.addToggler();
        addThemeInvalidateListener(settingView);
        settingView.setOnTouchListener(new ChatFilterOnTouchListener());
        settingView.setOnClickListener(ChatFoldersController.this);
        settingView.setOnLongClickListener(ChatFoldersController.this);
        settingView.getToggler().setOnClickListener(v -> {
          ListItem item = (ListItem) settingView.getTag();
          boolean enabled = settingView.getToggler().toggle(true);
          settingView.setVisuallyEnabled(enabled, true);
          settingView.setIconColorId(enabled ? R.id.theme_color_icon : R.id.theme_color_iconLight);
          if (isMainChatFilter(item)) {
            throw new UnsupportedOperationException();
          } else if (isArchiveChatFilter(item)) {
            tdlib.settings().setArchiveChatListEnabled(enabled);
          } else if (isChatFilter(item)) {
            tdlib.settings().setChatFilterEnabled(item.getIntValue(), enabled);
          } else {
            throw new IllegalArgumentException();
          }
        });
        return new SettingHolder(settingView);
      }

      @Override
      protected void setCustom (ListItem item, SettingHolder holder, int position) {
        SettingView settingView = (SettingView) holder.itemView;
        settingView.setIcon(item.getIconResource());
        settingView.setName(item.getString());
        settingView.setTextColorId(item.getTextColorId(R.id.theme_color_text));
        settingView.setIgnoreEnabled(true);
        settingView.setEnabled(true);

        boolean isEnabled;
        if (isMainChatFilter(item)) {
          throw new UnsupportedOperationException();
        } else if (isArchiveChatFilter(item)) {
          isEnabled = tdlib.settings().isArchiveChatListEnabled();
          settingView.setClickable(false);
          settingView.setLongClickable(false);
        } else if (isChatFilter(item)) {
          isEnabled = tdlib.settings().isChatFilterEnabled(item.getIntValue());
          settingView.setClickable(true);
          settingView.setLongClickable(true);
        } else {
          throw new IllegalArgumentException();
        }
        settingView.setVisuallyEnabled(isEnabled, false);
        settingView.getToggler().setRadioEnabled(isEnabled, false);
        settingView.setIconColorId(isEnabled ? R.id.theme_color_icon : R.id.theme_color_iconLight);
      }

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
          if (item.getId() == R.id.btn_chatFolderStyle) {
            int stringRes;
            switch (tdlib.settings().chatFolderStyle()) {
              case CHAT_FOLDER_STYLE_ICON_AND_TITLE:
                stringRes = R.string.IconAndTitle;
                break;
              case CHAT_FOLDER_STYLE_ICON_ONLY:
                stringRes = R.string.IconOnly;
                break;
              default:
              case CHAT_FOLDER_STYLE_TITLE_ONLY:
                stringRes = R.string.TitleOnly;
                break;
            }
            view.setData(stringRes);
          }
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
      updateChatFilters(chatFilters, mainChatListPosition, tdlib.settings().archiveChatListPosition());
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
      if (isMainChatFilter(item) || isArchiveChatFilter(item)) {
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
    } else if (v.getId() == R.id.btn_chatFolderStyle) {
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      showSettings(R.id.btn_chatFolderStyle, new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_titleOnly, 0, R.string.TitleOnly, R.id.btn_chatFolderStyle, chatFolderStyle == CHAT_FOLDER_STYLE_TITLE_ONLY),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_iconOnly, 0, R.string.IconOnly, R.id.btn_chatFolderStyle, chatFolderStyle == CHAT_FOLDER_STYLE_ICON_ONLY),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_iconAndTitle, 0, R.string.IconAndTitle, R.id.btn_chatFolderStyle, chatFolderStyle == CHAT_FOLDER_STYLE_ICON_AND_TITLE),
      }, (id, result) -> {
        int selection = result.get(R.id.btn_chatFolderStyle);
        int style;
        if (selection == R.id.btn_iconOnly) {
          style = CHAT_FOLDER_STYLE_ICON_ONLY;
        } else if (selection == R.id.btn_iconAndTitle) {
          style = CHAT_FOLDER_STYLE_ICON_AND_TITLE;
        } else {
          style = CHAT_FOLDER_STYLE_TITLE_ONLY;
        }
        tdlib.settings().setChatFolderStyle(style);
        adapter.updateValuedSettingById(R.id.btn_chatFolderStyle);
      });
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.chatFilter) {
      ListItem item = (ListItem) v.getTag();
      if (isMainChatFilter(item) || isArchiveChatFilter(item)) {
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
    int position = -1;
    TdApi.ChatFilterInfo[] chatFilters = tdlib.chatFilterInfos();
    for (int index = 0; index < chatFilters.length; index++) {
      TdApi.ChatFilterInfo chatFilter = chatFilters[index];
      if (chatFilter.id == chatFilterId) {
          position = index;
          break;
      }
    }
    if (position != -1) {
      int archiveChatListPosition = tdlib.settings().archiveChatListPosition();
      if (position >= tdlib.mainChatListPosition()) position++;
      if (position >= archiveChatListPosition) position++;
      boolean affectsArchiveChatListPosition = position < archiveChatListPosition && archiveChatListPosition < chatFilters.length + 2;
      tdlib.send(new TdApi.DeleteChatFilter(chatFilterId), tdlib.okHandler(() -> {
        if (affectsArchiveChatListPosition && archiveChatListPosition == tdlib.settings().archiveChatListPosition()) {
          tdlib.settings().setArchiveChatListPosition(archiveChatListPosition - 1);
          if (!isDestroyed()) {
            updateChatFilters();
          }
        }
      }));
    }
  }

  private void reorderChatFilters () {
    int firstIndex = indexOfFirstChatFilter();
    int lastIndex = indexOfLastChatFilter();
    if (firstIndex == RecyclerView.NO_POSITION || lastIndex == RecyclerView.NO_POSITION)
      return;
    int mainChatListPosition = 0;
    int archiveChatListPosition = 0;
    IntList chatFilterIds = new IntList(tdlib.chatFilterInfos().length);
    int filterPosition = 0;
    for (int index = firstIndex; index <= lastIndex; index++) {
      ListItem item = adapter.getItem(index);
      if (item == null) {
        updateChatFilters();
        return;
      }
      if (isChatFilter(item)) {
        if (isMainChatFilter(item)) {
          mainChatListPosition = filterPosition;
        } else if (isArchiveChatFilter(item)) {
          archiveChatListPosition = filterPosition;
        } else {
          chatFilterIds.append(item.getIntValue());
        }
        filterPosition++;
      }
    }
    if (mainChatListPosition > archiveChatListPosition) {
      mainChatListPosition--;
    }
    if (archiveChatListPosition > chatFilterIds.size()) {
      archiveChatListPosition = Integer.MAX_VALUE;
    }
    if (mainChatListPosition != 0 && !tdlib.hasPremium()) {
      updateChatFilters();
      return;
    }
    tdlib.settings().setArchiveChatListPosition(archiveChatListPosition);
    if (chatFilterIds.size() > 0) {
      tdlib.send(new TdApi.ReorderChatFilters(chatFilterIds.get(), mainChatListPosition), (result) -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
          runOnUiThreadOptional(this::updateChatFilters);
        }
      });
    }
  }

  private boolean isChatFilter (ListItem item) {
    return item.getId() == R.id.chatFilter;
  }

  private boolean isMainChatFilter (ListItem item) {
    return isChatFilter(item) && item.getLongId() == MAIN_CHAT_FILTER_ID;
  }

  private boolean isArchiveChatFilter (ListItem item) {
    return isChatFilter(item) && item.getLongId() == ARCHIVE_CHAT_FILTER_ID;
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

  private List<ListItem> buildChatFilterItemList (TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition, int archiveChatListPosition) {
    List<ListItem> itemList = new ArrayList<>(chatFilters.length + 6);
    itemList.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatFolders));
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    int chatFilterCount = chatFilters.length + 2; /* All Chats, Archived */
    int chatFilterIndex = 0;
    mainChatListPosition = MathUtils.clamp(mainChatListPosition, 0, chatFilters.length);
    archiveChatListPosition = MathUtils.clamp(archiveChatListPosition, 0, chatFilterCount - 1);
    if (mainChatListPosition == archiveChatListPosition) {
      mainChatListPosition++;
    }
    for (int position = 0; position < chatFilterCount; position++) {
      if (position == mainChatListPosition) {
        itemList.add(mainChatFilterItem());
      } else if (position == archiveChatListPosition) {
        itemList.add(archiveChatFilterItem());
      } else if (chatFilterIndex < chatFilters.length) {
        TdApi.ChatFilterInfo chatFilter = chatFilters[chatFilterIndex++];
        itemList.add(chatFilterItem(chatFilter));
      } else if (BuildConfig.DEBUG) {
        throw new RuntimeException();
      }
    }
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
    ListItem item = new ListItem(ListItem.TYPE_SETTING, R.id.chatFilter);
    item.setString(R.string.CategoryMain);
    item.setLongId(MAIN_CHAT_FILTER_ID);
    item.setIconRes(tdlib.hasPremium() ? R.drawable.baseline_drag_handle_24 : R.drawable.deproko_baseline_lock_24);
    return item;
  }

  private ListItem archiveChatFilterItem () {
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.chatFilter);
    item.setString(R.string.CategoryArchive);
    item.setLongId(ARCHIVE_CHAT_FILTER_ID);
    item.setIconRes(R.drawable.baseline_drag_handle_24);
    return item;
  }

  private ListItem chatFilterItem (TdApi.ChatFilterInfo chatFilterInfo) {
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.chatFilter, R.drawable.baseline_drag_handle_24, Emoji.instance().replaceEmoji(chatFilterInfo.title));
    item.setIntValue(chatFilterInfo.id);
    item.setLongId(chatFilterInfo.id);
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
    updateChatFilters(tdlib.chatFilterInfos(), tdlib.mainChatListPosition(), tdlib.settings().archiveChatListPosition());
  }

  private void updateChatFilters (TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition, int archiveChatListPosition) {
    int fromIndex = indexOfChatFilterGroup();
    if (fromIndex == RecyclerView.NO_POSITION)
      return;
    List<ListItem> subList = adapter.getItems().subList(fromIndex, fromIndex + chatFilterGroupItemCount);
    List<ListItem> newList = buildChatFilterItemList(chatFilters, mainChatListPosition, archiveChatListPosition);
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
          oldItem.getLongId() == newItem.getLongId();
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
      return isChatFilter(item) && !isMainChatFilter(item) && !isArchiveChatFilter(item);
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
      return isChatFilter(sourceItem) && isChatFilter(targetItem) && (canMoveChatFilter(targetItem) || BuildConfig.DEBUG && isArchiveChatFilter(sourceItem));
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