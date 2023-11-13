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
 * File created on 06/01/2023
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.FlowListAnimator;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;

public class SelectChatsController extends RecyclerViewController<SelectChatsController.Arguments> implements View.OnClickListener, ChatListListener {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_SELECT_CHATS, MODE_FOLDER_INCLUDE_CHATS, MODE_FOLDER_EXCLUDE_CHATS})
  public @interface Mode {
  }

  public static final int MODE_SELECT_CHATS = 0;
  public static final int MODE_FOLDER_INCLUDE_CHATS = 1;
  public static final int MODE_FOLDER_EXCLUDE_CHATS = 2;

  public static class Arguments {
    private final @Mode int mode;
    private final int chatFolderId;
    private final @Nullable TdApi.ChatFolder chatFolder;
    private final @Nullable Delegate delegate;
    private final Set<Long> selectedChatIds;
    private final Set<Integer> selectedChatTypes;

    private Arguments (@Mode int mode, @Nullable Delegate delegate, int chatFolderId, @Nullable TdApi.ChatFolder chatFolder, Set<Long> selectedChatIds, Set<Integer> selectedChatTypes) {
      this.mode = mode;
      this.delegate = delegate;
      this.chatFolder = chatFolder;
      this.chatFolderId = chatFolderId;
      this.selectedChatIds = selectedChatIds;
      this.selectedChatTypes = selectedChatTypes;
    }

    public static Arguments includedChats (int chatFolderId, TdApi.ChatFolder chatFolder) {
      return includedChats(null, chatFolderId, chatFolder);
    }

    public static Arguments includedChats (@Nullable Delegate delegate, int chatFolderId, TdApi.ChatFolder chatFolder) {
      Set<Long> selectedChatIds = unmodifiableLinkedHashSetOf(chatFolder.pinnedChatIds, chatFolder.includedChatIds);
      Set<Integer> selectedChatTypes = U.unmodifiableTreeSetOf(TD.includedChatTypes(chatFolder));
      return new Arguments(MODE_FOLDER_INCLUDE_CHATS, delegate, chatFolderId, chatFolder, selectedChatIds, selectedChatTypes);
    }

    public static Arguments excludedChats (@Nullable Delegate delegate, int chatFolderId, TdApi.ChatFolder chatFolder) {
      Set<Long> selectedChatIds = unmodifiableLinkedHashSetOf(chatFolder.excludedChatIds);
      Set<Integer> selectedChatTypes = U.unmodifiableTreeSetOf(TD.excludedChatTypes(chatFolder));
      return new Arguments(MODE_FOLDER_EXCLUDE_CHATS, delegate, chatFolderId, chatFolder, selectedChatIds, selectedChatTypes);
    }

    private static Set<Long> unmodifiableLinkedHashSetOf (long[]... arrays) {
      int count = 0;
      for (long[] array : arrays) {
        count += array.length;
      }
      LinkedHashSet<Long> set = new LinkedHashSet<>(count);
      for (long[] array : arrays) {
        for (long value : array) {
          set.add(value);
        }
      }
      return Collections.unmodifiableSet(set);
    }
  }

  private @Mode int mode;
  private @Nullable Delegate delegate;
  private SettingsAdapter adapter;
  private TdlibChatListSlice chatListSlice;
  private boolean loadingMore, chatListInitialized;

  private final @IdRes int chatsHeaderId = ViewCompat.generateViewId();
  private final @IdRes int chatsFooterId = ViewCompat.generateViewId();

  private Set<Long> selectedChatIds = Collections.emptySet();
  private Set<Integer> selectedChatTypes = Collections.emptySet();

  private int secretChatCount;
  private int nonSecretChatCount;

  private final BoolAnimator chipGroupVisibilityAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    RecyclerView recyclerView = getRecyclerView();
    recyclerView.post(recyclerView::invalidateItemDecorations);
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public SelectChatsController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
    setNeedSearch();
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    mode = args.mode;
    delegate = args.delegate;
    selectedChatIds = new LinkedHashSet<>(args.selectedChatIds);
    selectedChatTypes = new TreeSet<>(args.selectedChatTypes);

    secretChatCount = 0;
    nonSecretChatCount = 0;
    for (long selectedChatId : selectedChatIds) {
      if (ChatId.isSecret(selectedChatId)) {
        secretChatCount++;
      } else {
        nonSecretChatCount++;
      }
    }
  }

  @Override
  public int getId () {
    return R.id.controller_selectChats;
  }

  @Override
  public CharSequence getName () {
    Arguments arguments = getArgumentsStrict();
    switch (arguments.mode) {
      case MODE_FOLDER_INCLUDE_CHATS:
        return Lang.getString(R.string.IncludeChats);
      case MODE_FOLDER_EXCLUDE_CHATS:
        return Lang.getString(R.string.ExcludeChats);
      case MODE_SELECT_CHATS:
        return Lang.getString(R.string.SelectChats);
      default:
        throw new IllegalArgumentException("mode=" + arguments.mode);
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !chatListInitialized;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    Arguments arguments = getArgumentsStrict();
    adapter = new Adapter(this);

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CUSTOM, R.id.input));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (arguments.mode == MODE_FOLDER_INCLUDE_CHATS || arguments.mode == MODE_FOLDER_EXCLUDE_CHATS) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      if (mode == MODE_FOLDER_INCLUDE_CHATS) {
        CharSequence description = Lang.pluralBold(R.string.IncludeChatsInfo, tdlib.chatFolderChosenChatCountMax());
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, description));
      } else if (mode == MODE_FOLDER_EXCLUDE_CHATS) {
        CharSequence description = Lang.pluralBold(R.string.ExcludeChatsInfo, tdlib.chatFolderChosenChatCountMax());
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, description));
      }

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatTypes));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (arguments.mode == MODE_FOLDER_INCLUDE_CHATS) {
        for (int chatType : TD.CHAT_TYPES_TO_INCLUDE) {
          items.add(chatTypeItem(chatType));
        }
      }
      if (arguments.mode == MODE_FOLDER_EXCLUDE_CHATS) {
        for (int chatType : TD.CHAT_TYPES_TO_EXCLUDE) {
          items.add(chatTypeItem(chatType));
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, chatsHeaderId));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, chatsFooterId));
    }

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    int initialChunkSize = Screen.calculateLoadingItems(Screen.dp(72f), 5) + 5;
    int chunkSize = Screen.calculateLoadingItems(Screen.dp(72f), 25);
    loadingMore = true;
    chatListSlice = new TdlibChatListSlice(tdlib, ChatPosition.CHAT_LIST_MAIN, null, true);


    chatListSlice.initializeList(this, this::processChats, initialChunkSize, () -> {
      runOnUiThreadOptional(() -> {
        chatListInitialized = true;
        executeScheduledAnimation();
      });
    });

    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy > 0 && !loadingMore && !inSearchMode() && chatListSlice.canLoad()) {
          int lastVisiblePosition = findLastVisiblePosition();
          if (lastVisiblePosition == adapter.getItemCount() - 1) {
            chatListSlice.loadMore(chunkSize, /* after */ null);
          }
        }
      }
    });
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (view instanceof ChipGroup) {
          int height = ((ChipGroup) view).measureHeight(ViewCompat.isLaidOut(parent) ? parent.getWidth() : Screen.currentWidth());
          int totalHeight = height + SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM);
          int offsetTop = -Math.round(totalHeight * (1f - chipGroupVisibilityAnimator.getFloatValue()));
          outRect.set(0, offsetTop, 0, 0);
        } else {
          outRect.setEmpty();
        }
      }
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    chatListSlice.unsubscribeFromUpdates(this);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.chat) {
      ListItem item = (ListItem) v.getTag();
      long chatId = item.getLongId();
      toggleChatSelection(chatId, v, /* removeOnly */ false);
    } else if (ArrayUtils.contains(TD.CHAT_TYPES, id)) {
      toggleChatTypeSelection(id, v, /* removeOnly */ false);
    }
  }

  @Override
  protected void onDoneClick () {
    if (inSearchMode()) {
      closeSearchMode(null);
    } else {
      saveChanges(this::navigateBack);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private void updateDoneButton () {
    setDoneVisible(hasChanges(), true);
  }

  private void processChats (List<TdlibChatListSlice.Entry> entries) {
    if (entries.isEmpty()) {
      return;
    }
    List<TGFoundChat> chats = new ArrayList<>(entries.size());
    for (TdlibChatListSlice.Entry entry : entries) {
      chats.add(foundChat(entry));
    }
    runOnUiThreadOptional(() -> {
      loadingMore = false;
      displayChats(chats);
    });
  }

  private void displayChats (List<TGFoundChat> chats) {
    if (chats.isEmpty()) {
      return;
    }
    List<ListItem> chatItems = new ArrayList<>(chats.size() * 2);
    for (TGFoundChat chat : chats) {
      chatItems.add(chatItem(chat));
    }
    adapter.addItems(indexOfLastChat() + 1, chatItems.toArray(new ListItem[0]));
  }

  private ListItem chatTypeItem (@IdRes int id) {
    TdlibAccentColor accentColor = tdlib.accentColor(TD.chatTypeAccentColorId(id));
    return new ListItem(ListItem.TYPE_CHAT_BETTER, id, TD.chatTypeIcon24(id), TD.chatTypeName(id))
      .setAccentColor(accentColor);
  }

  private ListItem chatItem (TGFoundChat foundChat) {
    ListItem item = new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat);
    item.setLongId(foundChat.getChatId());
    item.setData(foundChat);
    return item;
  }

  private TGFoundChat foundChat (TdlibChatListSlice.Entry entry) {
    return foundChat(entry.chatList, entry.chat);
  }

  private TGFoundChat foundChat (TdApi.ChatList chatList, TdApi.Chat chat) {
    TGFoundChat foundChat = new TGFoundChat(tdlib, chatList, chat, true, null);
    modifyChat(foundChat);
    return foundChat;
  }

  private int indexOfFistChat () {
    return adapter.indexOfViewById(chatsHeaderId) + 1;
  }

  private int indexOfLastChat () {
    return adapter.indexOfViewById(chatsFooterId) - 1;
  }

  private boolean hasChanges () {
    Arguments arguments = getArgumentsStrict();
    return !selectedChatTypes.equals(arguments.selectedChatTypes) || !selectedChatIds.equals(arguments.selectedChatIds);
  }

  private void saveChanges (@Nullable Runnable after) {
    if (delegate != null) {
      delegate.onSelectedChatsChanged(mode, selectedChatIds, selectedChatTypes);
      if (after != null) {
        after.run();
      }
    } else {
      Arguments arguments = getArgumentsStrict();
      if (arguments.chatFolder != null && (mode == MODE_FOLDER_INCLUDE_CHATS || mode == MODE_FOLDER_EXCLUDE_CHATS)) {
        int chatFolderId = arguments.chatFolderId;
        TdApi.ChatFolder chatFolder = TD.copyOf(arguments.chatFolder);
        if (mode == MODE_FOLDER_INCLUDE_CHATS) {
          TD.updateIncludedChats(chatFolder, selectedChatIds);
          TD.updateIncludedChatTypes(chatFolder, selectedChatTypes);
        } else {
          TD.updateExcludedChats(chatFolder, selectedChatIds);
          TD.updateExcludedChatTypes(chatFolder, selectedChatTypes);
        }
        tdlib.send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (chatFolderInfo, error) -> {
          if (after != null) {
            executeOnUiThreadOptional(after);
          }
        });
      }
    }
  }

  private boolean toggleChatSelection (long chatId, @Nullable View view, boolean removeOnly) {
    boolean selected = selectedChatIds.contains(chatId);
    if (!selected && removeOnly) {
      return false;
    }
    boolean isSecretChat = ChatId.isSecret(chatId);
    if (selected) {
      selectedChatIds.remove(chatId);
      if (isSecretChat) {
        secretChatCount--;
      } else {
        nonSecretChatCount--;
      }
    } else {
      long chosenChatCountMax = tdlib.chatFolderChosenChatCountMax();
      long chosenChatCount = isSecretChat ? secretChatCount : nonSecretChatCount;
      if (chosenChatCount >= chosenChatCountMax) {
        if (tdlib.hasPremium()) {
          CharSequence text = Lang.getMarkdownString(this, R.string.ChatsInFolderLimitReached, chosenChatCountMax);
          UI.showCustomToast(text, Toast.LENGTH_LONG, 0);
        } else {
          tdlib.send(new TdApi.GetPremiumLimit(new TdApi.PremiumLimitTypeChatFolderChosenChatCount()), (premiumLimit, error) -> runOnUiThreadOptional(() -> {
            CharSequence text;
            if (premiumLimit != null) {
              text = Lang.getMarkdownString(this, R.string.PremiumRequiredChatsInFolder, premiumLimit.defaultValue, premiumLimit.premiumValue);
            } else {
              text = Lang.getMarkdownString(this, R.string.ChatsInFolderLimitReached, chosenChatCountMax);
            }
            UI.showCustomToast(text, Toast.LENGTH_LONG, 0);
          }));
        }
        return false;
      }
      selectedChatIds.add(chatId);
      if (isSecretChat) {
        secretChatCount++;
      } else {
        nonSecretChatCount++;
      }
    }
    updateDoneButton();
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(!selected, true);
    } else {
      adapter.updateCheckOptionByLongId(chatId, !selected);
    }
    adapter.updateSimpleItemById(R.id.input);
    return !selected;
  }

  private void toggleChatTypeSelection (@IdRes int chatType, @Nullable View view, boolean removeOnly) {
    boolean selected = selectedChatTypes.contains(chatType);
    if (!selected && removeOnly) {
      return;
    }
    if (selected) {
      selectedChatTypes.remove(chatType);
    } else {
      selectedChatTypes.add(chatType);
    }
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(!selected, true);
    } else {
      adapter.updateCheckOptionById(chatType, !selected);
    }
    updateDoneButton();
    adapter.updateSimpleItemById(R.id.input);
  }

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    boolean isChatSelected = toggleChatSelection(chat.getChatId(), null, /* removeOnly */ false);
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(isChatSelected, true);
    } else {
      closeSearchMode(null);
    }
    return true;
  }

  @Override
  protected void modifyFoundChat (TGFoundChat chat) {
    modifyChat(chat);
  }

  @Override
  protected void modifyFoundChatView (ListItem item, int position, BetterChatView chatView) {
    modifyChatView((TGFoundChat) item.getData(), chatView);
  }

  @Override
  public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      TGFoundChat foundChat = foundChat(chatList.chatList(), chat);
      adapter.addItems(indexOfFistChat() + atIndex, chatItem(foundChat));
    });
  }

  @Override
  public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      adapter.removeItem(indexOfFistChat() + fromIndex);
    });
  }

  @Override
  public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      int firstChatIndex = indexOfFistChat();
      adapter.moveItem(firstChatIndex + fromIndex, firstChatIndex + toIndex);
    });
  }

  private void modifyChat (TGFoundChat chat) {
    chat.setNoUnread();
    if (mode == MODE_FOLDER_INCLUDE_CHATS || mode == MODE_FOLDER_EXCLUDE_CHATS) {
      chat.setForcedSubtitle(buildFolderListSubtitle(tdlib, chat));
    }
  }

  private void modifyChatView (TGFoundChat chat, BetterChatView chatView) {
    chatView.setAllowMaximizePreview(false);
    chatView.setIsChecked(selectedChatIds.contains(chat.getChatId()), false);
    if (mode == MODE_FOLDER_INCLUDE_CHATS || mode == MODE_FOLDER_EXCLUDE_CHATS) {
      chatView.setNoSubtitle(StringUtils.isEmpty(chat.getForcedSubtitle()));
    }
  }

  private static @Nullable String buildFolderListSubtitle (Tdlib tdlib, TGFoundChat foundChat) {
    TdApi.Chat chat = foundChat.getChat();
    if (chat == null) {
      chat = tdlib.chat(foundChat.getChatId());
    }
    return chat != null ? buildFolderListSubtitle(tdlib, chat) : null;
  }

  private static @Nullable String buildFolderListSubtitle (Tdlib tdlib, TdApi.Chat chat) {
    TdApi.ChatPosition[] chatPositions = chat.positions;
    if (chatPositions != null && chatPositions.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (TdApi.ChatPosition chatPosition : chatPositions) {
        if (!TD.isChatListFolder(chatPosition.list))
          continue;
        TdApi.ChatListFolder chatListFilter = (TdApi.ChatListFolder) chatPosition.list;
        TdApi.ChatFolderInfo chatFolderInfo = tdlib.chatFolderInfo(chatListFilter.chatFolderId);
        if (chatFolderInfo == null || StringUtils.isEmptyOrBlank(chatFolderInfo.title))
          continue;
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(chatFolderInfo.title);
      }
      return sb.toString();
    }
    return null;
  }

  public interface Delegate {
    void onSelectedChatsChanged (@Mode int mode, Set<Long> chatIds, Set<Integer> chatTypes);
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }

    @Override
    protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
      int spacing = Screen.dp(8f);
      ChipGroup chipGroup = new ChipGroup(parent.getContext());
      chipGroup.setSpacing(spacing);
      chipGroup.setPadding(spacing, spacing, spacing, spacing);
      chipGroup.setDelegate(new ChipGroup.Delegate() {
        @Override
        public void onCrossClick (Chip chip) {
          if (chip.type == Chip.TYPE_CHAT_TYPE) {
            int chatType = (int) chip.id;
            toggleChatTypeSelection(chatType, null, /* removeOnly */ true);
          } else if (chip.type == Chip.TYPE_CHAT) {
            long chatId = chip.id;
            toggleChatSelection(chatId, null, /* removeOnly */ true);
          } else {
            throw new UnsupportedOperationException();
          }
        }
      });

      return new SettingHolder(chipGroup);
    }

    @Override
    protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
      ChipGroup chipGroup = (ChipGroup) view;
      List<Chip> chips = new ArrayList<>(selectedChatIds.size() + selectedChatTypes.size());
      for (int selectedChatType : selectedChatTypes) {
        chips.add(chipGroup.chatType(tdlib, selectedChatType));
      }
      for (long selectedChatId : selectedChatIds) {
        chips.add(chipGroup.chat(tdlib, selectedChatId));
      }
      chipGroup.setChips(chips);
      chipGroupVisibilityAnimator.setValue(!chips.isEmpty(), isFocused());
    }

    @Override
    protected void setChatData (ListItem item, int position, BetterChatView chatView) {
      if (item.getId() == R.id.chat) {
        TGFoundChat foundChat = (TGFoundChat) item.getData();
        chatView.setChat(foundChat);
        SelectChatsController.this.modifyChatView(foundChat, chatView);
      } else if (ArrayUtils.contains(TD.CHAT_TYPES, item.getId())) {
        chatView.setTitle(item.getString());
        chatView.setSubtitle(null);
        chatView.setNoSubtitle(true);
        chatView.setAvatar(null, new AvatarPlaceholder.Metadata(item.getAccentColor(), item.getIconResource()));
        chatView.setIsChecked(selectedChatTypes.contains(item.getId()), false);
        chatView.clearPreviewChat();
      } else {
        throw new IllegalArgumentException();
      }
    }
  }
}

class Chip extends Drawable implements FlowListAnimator.Measurable, Drawable.Callback, TextColorSet {
  public static final int TYPE_CHAT = 1;
  public static final int TYPE_CHAT_TYPE = 2;

  private static final float AVATAR_RADIUS = 12f;

  private static final int[] STATE_DEFAULT = new int[] {android.R.attr.state_enabled};
  private static final int[] STATE_PRESSED = new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed};

  public final long id;
  public final int type;
  private final Text label;
  private final AvatarPlaceholder avatarPlaceholder;
  private final @Nullable ImageFile avatarFile;
  private final ComplexReceiver complexReceiver;
  private final DrawableProvider drawableProvider;
  private final boolean isSecretChat;

  private Drawable crossIcon;
  private Drawable crossIconRipple;

  private int alpha = 0xFF;

  public Chip (DrawableProvider drawableProvider, ComplexReceiver complexReceiver, Tdlib tdlib, long chatId) {
    this.id = chatId;
    this.type = TYPE_CHAT;
    this.label = buildLabel(tdlib.chatTitle(chatId));
    this.isSecretChat = ChatId.isSecret(chatId);
    if (tdlib.isSelfChat(chatId)) {
      this.avatarFile = null;
      this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.ARCHIVE), R.drawable.baseline_bookmark_16), drawableProvider);
    } else if (tdlib.isRepliesChat(chatId)) {
      this.avatarFile = null;
      this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.REPLIES), R.drawable.baseline_reply_16), drawableProvider);
    } else {
      this.avatarFile = tdlib.chatAvatar(chatId, Screen.dp(AVATAR_RADIUS * 2));
      this.avatarPlaceholder = tdlib.chatPlaceholder(chatId, tdlib.chat(chatId), true, AVATAR_RADIUS, drawableProvider);
    }
    this.drawableProvider = drawableProvider;
    this.complexReceiver = complexReceiver;
    initCrossDrawable();
  }

  public Chip (DrawableProvider drawableProvider, @IdRes int chatType, Tdlib tdlib) {
    this.id = chatType;
    this.type = TYPE_CHAT_TYPE;
    this.label = buildLabel(Lang.getString(TD.chatTypeName(chatType)));
    this.isSecretChat = false;
    this.avatarFile = null;
    this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(tdlib.accentColor(TD.chatTypeAccentColorId(chatType)), TD.chatTypeIcon16(chatType)), drawableProvider);
    this.drawableProvider = drawableProvider;
    this.complexReceiver = null;
    initCrossDrawable();
  }

  public boolean inCrossIconTouchBounds (float x, float y) {
    Rect bounds = getBounds();
    return bounds.contains(Math.round(x), Math.round(y)) && x >= bounds.right - Screen.dp(34f);
  }

  public void setCrossIconPressed (boolean pressed) {
    crossIconRipple.setState(pressed ? STATE_PRESSED : STATE_DEFAULT);
  }

  private Text buildLabel (String text) {
    int maxWidth = (Screen.currentWidth() - Screen.dp(8f) * 3) / 2 - getIntrinsicWidth(/* labelWidth */ 0, /* hasIcon */ isSecretChat); // (´・ᴗ・ ` )
    return new Text.Builder(text, maxWidth, Paints.robotoStyleProvider(14f), this)
      .noClickable()
      .ignoreNewLines()
      .ignoreContinuousNewLines()
      .clipTextArea()
      .singleLine()
      .allBold()
      .build();
  }

  private void initCrossDrawable () {
    crossIcon = drawableProvider.getSparseDrawable(R.drawable.baseline_close_18, ColorId.NONE);
    ShapeDrawable mask = new ShapeDrawable(new OvalShape());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      mask.setTint(Color.WHITE);
      crossIconRipple = new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE_COLOR), /* content */ null, mask);
    } else {
      mask.getPaint().setColor(Theme.RIPPLE_COLOR);
      crossIconRipple = Drawables.getColorSelector(null, mask);
    }
    crossIconRipple.setCallback(this);
    crossIconRipple.setState(STATE_DEFAULT);
  }

  public void requestFiles () {
    if (complexReceiver != null && avatarFile != null) {
      ImageReceiver imageReceiver = complexReceiver.getImageReceiver(id);
      imageReceiver.setRadius(Screen.dp(AVATAR_RADIUS));
      imageReceiver.requestFile(avatarFile);
    }
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Chip that = (Chip) o;
    return id == that.id && type == that.type;
  }

  @Override
  public int hashCode () {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + type;
    return result;
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(32f);
  }

  @Override
  public int getIntrinsicWidth () {
    int width = getIntrinsicWidth(label.getWidth(), isSecretChat);
    int minWidth = Screen.dp(48f);
    return Math.max(width, minWidth);
  }

  private static int getIntrinsicWidth (int labelWidth, boolean hasIcon) {
    int width = Screen.dp(4f + AVATAR_RADIUS * 2 + 8f + 8f + 18f + 8f) + labelWidth;
    if (hasIcon) {
      width += Screen.dp(15f);
    }
    return width;
  }

  @Override
  public void draw (Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds.isEmpty() || alpha == 0) {
      return;
    }
    int saveCount;
    if (alpha < 0xFF) {
      saveCount = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha, Canvas.ALL_SAVE_FLAG);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    int outlineColor = Theme.inlineOutlineColor(false);
    Paint outlinePaint = Paints.strokeSmallPaint(outlineColor);
    float outlineInset = outlinePaint.getStrokeWidth() / 2f;
    int radius = Screen.dp(8f);
    RectF roundRect = Paints.getRectF();
    roundRect.set(bounds.left + outlineInset, bounds.top + outlineInset, bounds.right - outlineInset, bounds.bottom - outlineInset);
    canvas.drawRoundRect(roundRect, radius, radius, Paints.fillingPaint(Theme.fillingColor()));
    canvas.drawRoundRect(roundRect, radius, radius, outlinePaint);

    int avatarRadius = Screen.dp(AVATAR_RADIUS);
    int avatarX = bounds.left + avatarRadius + Screen.dp(4f);
    int avatarY = bounds.centerY();
    ImageReceiver imageReceiver = avatarFile != null && complexReceiver != null ? complexReceiver.getImageReceiver(id) : null;
    if (imageReceiver != null) {
      imageReceiver.setBounds(avatarX - avatarRadius, avatarY - avatarRadius, avatarX + avatarRadius, avatarY + avatarRadius);
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderRounded(canvas, avatarRadius, Theme.placeholderColor());
      }
      imageReceiver.draw(canvas);
    } else {
      avatarPlaceholder.draw(canvas, avatarX, avatarY);
    }

    int labelX = bounds.left + avatarRadius * 2 + Screen.dp(8f + 4f);
    int labelY = bounds.centerY() - label.getLineCenterY();
    if (isSecretChat) {
      Drawable secureDrawable = Icons.getSecureDrawable();
      int secureIconX = labelX - Screen.dp(7f);
      int secureIconY = bounds.centerY() - secureDrawable.getMinimumHeight() / 2;
      Drawables.draw(canvas, secureDrawable, secureIconX, secureIconY, Paints.getGreenPorterDuffPaint());
      labelX += Screen.dp(15f);
    }
    label.draw(canvas, labelX, labelY);

    int iconX = bounds.right - Screen.dp(17f);
    int iconY = bounds.centerY();
    if (crossIconRipple != null) {
      int rippleRadius = Screen.dp(28f) / 2;
      crossIconRipple.setBounds(iconX - rippleRadius, iconY - rippleRadius, iconX + rippleRadius, iconY + rippleRadius);
      crossIconRipple.draw(canvas);
    }
    Drawables.drawCentered(canvas, crossIcon, iconX, iconY, PorterDuffPaint.get(ColorId.inlineIcon));

    if (alpha < 0xFF) {
      canvas.restoreToCount(saveCount);
    }
  }

  @Override
  public void setAlpha (int alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidateSelf();
    }
  }

  @Override
  public int getAlpha () {
    return alpha;
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {
  }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getWidth () {
    return getIntrinsicWidth();
  }

  @Override
  public int getHeight () {
    return getIntrinsicHeight();
  }

  @Override
  public void invalidateDrawable (@NonNull Drawable who) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.invalidateDrawable(this);
    }
  }

  @Override
  public void scheduleDrawable (@NonNull Drawable who, @NonNull Runnable what, long when) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.scheduleDrawable(this, what, when);
    }
  }

  @Override
  public void unscheduleDrawable (@NonNull Drawable who, @NonNull Runnable what) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.unscheduleDrawable(this, what);
    }
  }

  @Override
  public int defaultTextColor () {
    return Theme.textAccentColor();
  }
}

class ChipGroup extends SparseDrawableView implements ClickHelper.Delegate, AttachDelegate, Destroyable {
  private final ComplexReceiver complexReceiver = new ComplexReceiver(this);
  private final FlowListAnimator<Chip> animator = new FlowListAnimator<>(animator -> {
    if (getHeight() != animator.getMetadata().getTotalHeight()) {
      requestLayout();
    }
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);
  private final ClickHelper clickHelper = new ClickHelper(this);

  private List<Chip> chips = Collections.emptyList();
  private int spacing;
  private Delegate delegate;

  public interface Delegate {
    default void onCrossClick (Chip chip) {}
  }

  public ChipGroup (Context context) {
    super(context);
    setWillNotDraw(false);
    animator.setLineSpacing(Screen.dp(8f));
    animator.setItemSpacing(Screen.dp(8f));
  }

  public void setSpacing (int spacing) {
    if (this.spacing != spacing) {
      this.spacing = spacing;
      requestLayout();
    }
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public ComplexReceiver getComplexReceiver () {
    return complexReceiver;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return delegate != null && clickHelper.onTouchEvent(this, event);
  }

  public Chip chat (Tdlib tdlib, long chatId) {
    for (Chip chip : chips) {
      if (chip.type == Chip.TYPE_CHAT && chip.id == chatId) {
        return chip;
      }
    }
    return new Chip(this, complexReceiver, tdlib, chatId);
  }

  public Chip chatType (Tdlib tdlib, @IdRes int chatType) {
    for (Chip chip : chips) {
      if (chip.type == Chip.TYPE_CHAT_TYPE && chip.id == chatType) {
        return chip;
      }
    }
    return new Chip(this, chatType, tdlib);
  }

  public void setChips (List<Chip> chips) {
    for (Chip chip : this.chips) {
      chip.setCallback(null);
    }
    this.chips = chips;
    for (Chip chip : this.chips) {
      chip.setCallback(this);
      chip.requestFiles();
    }
    animator.reset(chips, ViewCompat.isLaidOut(this));
  }

  public int measureHeight (int maxWidth) {
    int contentWidth = maxWidth - getPaddingLeft() - getPaddingRight();
    if (contentWidth != animator.getMaxWidth()) {
      animator.setMaxWidth(contentWidth);
      animator.measure(ViewCompat.isLaidOut(this));
    }
    int contentHeight = Math.max(Screen.dp(32f), (int) animator.getMetadata().getTotalHeight());
    return contentHeight + getPaddingTop() + getPaddingBottom();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
    int measuredHeight = measureHeight(measuredWidth);
    setMeasuredDimension(measuredWidth, measuredHeight);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    canvas.drawRect(0, 0, getWidth(), getHeight(), Paints.fillingPaint(Theme.fillingColor()));
    canvas.translate(getPaddingLeft(), getPaddingTop());
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      int alpha = Math.round(entry.getVisibility() * 0xFF);
      Rect bounds = Paints.getRect();
      entry.getBounds(bounds);
      entry.item.setAlpha(MathUtils.clamp(alpha, 0x00, 0xFF));
      entry.item.setBounds(bounds);
      entry.item.draw(canvas);
    }
    canvas.translate(-getPaddingLeft(), -getPaddingTop());
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    if (delegate != null) {
      Chip chip = findChipAt(x, y);
      return chip != null && chip.inCrossIconTouchBounds(x, y);
    }
    return false;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (delegate != null) {
      Chip chip = findChipAt(x, y);
      if (chip != null /*&& chip.inCrossIconTouchBounds(x, y)*/) {
        delegate.onCrossClick(chip);
      }
    }
  }

  private @Nullable Chip pressedChip;

  @Override
  public void onClickTouchDown (View view, float x, float y) {
    pressedChip = findChipAt(x, y);
    if (pressedChip != null && pressedChip.inCrossIconTouchBounds(x, y)) {
      pressedChip.setCrossIconPressed(true);
    }
  }

  @Override
  public void onClickTouchUp (View view, float x, float y) {
    if (pressedChip != null) {
      pressedChip.setCrossIconPressed(false);
      pressedChip = null;
    }
  }

  private @Nullable Chip findChipAt (float x, float y) {
    int rx = Math.round(x), ry = Math.round(y);
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      Rect bounds = entry.item.getBounds();
      if (bounds.contains(rx, ry)) {
        return entry.item;
      }
    }
    return null;
  }

  @Override
  public void attach () {
    complexReceiver.attach();
  }

  @Override
  public void detach () {
    complexReceiver.detach();
  }


  @Override
  public void performDestroy () {
    complexReceiver.performDestroy();
  }

  @Override
  protected boolean verifyDrawable (@NonNull Drawable who) {
    if (super.verifyDrawable(who)) {
      return true;
    }
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      if (entry.item == who) {
        return true;
      }
    }
    return false;
  }
}