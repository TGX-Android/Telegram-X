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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatFolderInviteLinkController.Arguments;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.LongSet;

public class ChatFolderInviteLinkControllerPage extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<Arguments> implements View.OnClickListener {

  private static final int NO_CHAT_FOLDER_ID = 0;
  private static final float BUTTON_HEIGHT_DP = 56f;

  private final BottomSheetViewController<?> parent;
  private final LongSet selectedChatIds = new LongSet();

  private @ChatFolderInviteLinkController.Mode int mode = ChatFolderInviteLinkController.MODE_INVITE_LINK;
  private int chatFolderId = NO_CHAT_FOLDER_ID;
  private String chatFolderTitle;
  private long[] selectableChatIds = ArrayUtils.EMPTY_LONGS;
  private @Nullable String inviteLinkUrl;
  private @Nullable TdApi.ChatFolderInviteLinkInfo inviteLinkInfo;
  private SettingsAdapter adapter;
  private FrameLayoutFix actionButton;

  public ChatFolderInviteLinkControllerPage (BottomSheetViewController<?> parent) {
    super(parent.context(), parent.tdlib());
    this.parent = parent;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    mode = args.mode;
    chatFolderId = args.chatFolderId;
    chatFolderTitle = args.chatFolderTitle;
    inviteLinkUrl = args.inviteLinkUrl;
    inviteLinkInfo = args.inviteLinkInfo;
    selectableChatIds = args.selectableChatIds;
    selectedChatIds.addAll(selectableChatIds);
  }

  @Override
  public int getId () {
    return R.id.controller_chatFolderInviteLink;
  }

  public HeaderView getHeaderView () {
    if (headerView == null) {
      headerView = new HeaderView(context);
      headerView.initWithSingleController(this, false);
      headerView.getFilling().setColor(Theme.fillingColor());
      headerView.getFilling().setShadowAlpha(1f);
      headerView.setWillNotDraw(false);
      addThemeInvalidateListener(headerView);
    }
    return headerView;
  }

  @Override
  public int getItemsHeight (RecyclerView parent) {
    int totalHeight = 0;
    List<ListItem> items = adapter.getItems();
    for (ListItem item : items) {
      int itemHeight;
      if (item.getViewType() == ListItem.TYPE_DESCRIPTION) {
        itemHeight = Math.max(Screen.dp(20f), item.getHeight());
      } else {
        itemHeight = SettingHolder.measureHeightForType(item);
      }
      totalHeight += itemHeight;
    }
    return totalHeight;
  }

  @Override
  protected View onCreateView (Context context) {
    View view = super.onCreateView(context);

    FrameLayoutFix wrap = (FrameLayoutFix) view;
    wrap.addView(actionButton = createActionButton(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BUTTON_HEIGHT_DP, Gravity.BOTTOM));
    updateActionButton();

    SeparatorView bottomShadowView = SeparatorView.simpleSeparator(context, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f, Gravity.BOTTOM), false);
    bottomShadowView.setAlignBottom();
    wrap.addView(bottomShadowView);

    int buttonHeight = Screen.dp(BUTTON_HEIGHT_DP);
    Views.setBottomMargin(getRecyclerView(), buttonHeight);
    Views.setBottomMargin(bottomShadowView, buttonHeight);
    return view;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    getHeaderView();
    addThemeInvalidateListener(recyclerView);

    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));

    adapter = new Adapter(this);
    adapter.setNoEmptyProgress();
    recyclerView.setAdapter(adapter);
    buildCells();
  }

  private FrameLayoutFix createActionButton () {
    TextView button = new TextView(context);
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    button.setTypeface(Fonts.getRobotoMedium());
    button.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    button.setEllipsize(TextUtils.TruncateAt.END);
    button.setGravity(Gravity.CENTER);
    button.setAllCaps(true);

    FrameLayoutFix frame = new FrameLayoutFix(context);
    frame.setOnClickListener(this);
    frame.setId(R.id.btn_done);
    frame.addView(button, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
    RippleSupport.setSimpleWhiteBackground(frame, this);
    return frame;
  }

  private void updateActionButton () {
    boolean enabled = !isFinishing();
    actionButton.setEnabled(enabled);
    int textColorId;
    if (enabled) {
      textColorId = mode == ChatFolderInviteLinkController.MODE_DELETE_FOLDER ? ColorId.textNegative : ColorId.textLink;
    } else {
      textColorId = ColorId.textLight;
    }
    TextView textView = (TextView) actionButton.getChildAt(0);
    textView.setTextColor(Theme.getColor(textColorId));
    removeThemeListenerByTarget(textView);
    addThemeTextColorListener(textView, textColorId);
    CharSequence text;
    switch (mode) {
      case ChatFolderInviteLinkController.MODE_INVITE_LINK:
      case ChatFolderInviteLinkController.MODE_NEW_CHATS:
        text = getJoinChatsActionText();
        break;
      case ChatFolderInviteLinkController.MODE_DELETE_FOLDER:
        text = getDeleteFolderActionText();
        break;
      default:
        throw new IllegalStateException("mode = " + mode);
    }
    Views.setMediumText(textView, text);
  }

  private CharSequence getJoinChatsActionText () {
    boolean hasFolder = chatFolderId != NO_CHAT_FOLDER_ID;
    if (selectableChatIds.length == 0) {
      return Lang.getString(R.string.OK);
    }
    int selectedChatCount = selectedChatIds.size();
    if (selectedChatCount == 0) {
      return Lang.getString(hasFolder ? R.string.DoNotJoinAnyChats : R.string.DoNotAddFolder);
    }
    return Lang.plural(hasFolder ? R.string.JoinXChats : R.string.AddFolderAndJoinXChats, selectedChatCount);
  }

  private CharSequence getDeleteFolderActionText () {
    int selectedChatCount = selectedChatIds.size();
    if (selectedChatCount == 0) {
      return Lang.getString(R.string.DeleteFolder);
    }
    return Lang.plural(R.string.DeleteFolderAndLeaveXChats, selectedChatCount);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();

    int selectableChatCount = selectableChatIds.length;
    if (selectableChatCount > 0) {
      int headerPluralRes;
      int headerViewType;
      switch (mode) {
        case ChatFolderInviteLinkController.MODE_INVITE_LINK:
          headerPluralRes = R.string.xChatsToJoin;
          headerViewType = ListItem.TYPE_HEADER_WITH_CHECKBOX;
          break;
        case ChatFolderInviteLinkController.MODE_NEW_CHATS:
          headerPluralRes = R.string.xNewChatsToJoin;
          headerViewType = ListItem.TYPE_HEADER_WITH_CHECKBOX;
          break;
        case ChatFolderInviteLinkController.MODE_DELETE_FOLDER:
          headerPluralRes = R.string.xChatsToLeave;
          headerViewType = ListItem.TYPE_HEADER_WITH_CHECKBOX;
          break;
        default:
          throw new IllegalStateException("mode = " + mode);
      }
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(headerViewType, R.id.btn_check, 0, Lang.plural(headerPluralRes, selectableChatCount)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      addChats(items, selectableChatIds, true);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      int descriptionRes;
      switch (mode) {
        case ChatFolderInviteLinkController.MODE_INVITE_LINK:
        case ChatFolderInviteLinkController.MODE_NEW_CHATS:
          descriptionRes = R.string.ChatFolderInviteLinkJoinChatsHint;
          break;
        case ChatFolderInviteLinkController.MODE_DELETE_FOLDER:
          descriptionRes = R.string.ChatFolderInviteLinkLeaveChatsHint;
          break;
        default:
          descriptionRes = ResourcesCompat.ID_NULL;
          break;
      }
      if (descriptionRes != ResourcesCompat.ID_NULL) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, descriptionRes));
      }
    }

    int addedChatCount = inviteLinkInfo != null ? inviteLinkInfo.addedChatIds.length : 0;
    if (addedChatCount > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, Lang.plural(R.string.xChatsAlreadyJoined, addedChatCount)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      addChats(items, inviteLinkInfo.addedChatIds, false);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.replaceItems(items);
  }

  private void addChats (List<ListItem> outList, long[] chatIds, boolean selectable) {
    for (int index = 0; index < chatIds.length; index++) {
      if (index > 0) {
        outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      long chatId = chatIds[index];
      outList.add(chatItem(chatId, selectable));
    }
  }

  private ListItem chatItem (long chatId, boolean selectable) {
    TdApi.Chat chat = tdlib.chatStrict(chatId);
    boolean isPublic = tdlib.chatPublic(chatId);
    CharSequence subtitle;
    if (tdlib.isChannel(chatId)) {
      subtitle = Lang.getString(isPublic ? R.string.ChannelPublic : R.string.ChannelPrivate);
    } else if (tdlib.isMultiChat(chat)) {
      subtitle = Lang.getString(isPublic ? R.string.GroupPublic : R.string.GroupPrivate);
    } else {
      subtitle = "";
    }
    DoubleTextWrapper data = new DoubleTextWrapper(tdlib, chat);
    data.setForcedSubtitle(subtitle);
    data.setAdminSignVisible(false, false);
    data.setIsChecked(selectedChatIds.has(chatId), /* animated */ false);

    ListItem item = new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.chat);
    item.setBoolValue(selectable);
    item.setLongId(chatId);
    item.setData(data);
    return item;
  }

  @Override
  public CharSequence getName () {
    return chatFolderTitle;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  protected void openMoreMenu () {
    if (isFinishing()) return;
    Options.Builder builder = new Options.Builder();
    builder.item(new OptionItem(R.id.btn_copyLink, Lang.getString(R.string.InviteLinkCopy), OptionColor.NORMAL, R.drawable.baseline_content_copy_24));
    builder.item(new OptionItem(R.id.btn_shareFolder, Lang.getString(R.string.ShareFolder), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
    Options options = builder.build();
    showOptions(options, (view, id) -> {
      if (id == R.id.btn_copyLink) {
        UI.copyText(inviteLinkUrl, R.string.CopiedLink);
      } else if (id == R.id.btn_shareFolder) {
        parent.setDismissListener((popup) -> {
          popup.setDismissListener(null);
          tdlib.ui().shareUrl(this, inviteLinkUrl);
        });
        parent.hidePopupWindow(true);
      } else {
        throw new UnsupportedOperationException();
      }
      return true;
    });
  }

  private boolean isFinishing () {
    return parent.getPopupLayout().isWindowHidden();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected int getMenuId () {
    Arguments arguments = getArgumentsStrict();
    return arguments.mode == ChatFolderInviteLinkController.MODE_INVITE_LINK ? R.id.menu_more : 0;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.icon;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onClick (View v) {
    if (isFinishing()) return;
    if (v.getId() == R.id.chat) {
      onChatItemClicked((ListItem) v.getTag());
    } else if (v.getId() == R.id.btn_done) {
      switch (mode) {
        case ChatFolderInviteLinkController.MODE_INVITE_LINK:
          if (selectedChatIds.isEmpty()) {
            parent.hidePopupWindow(true);
          } else {
            joinSelectedChats();
          }
          break;
        case ChatFolderInviteLinkController.MODE_NEW_CHATS:
          processNewChats();
          break;
        case ChatFolderInviteLinkController.MODE_DELETE_FOLDER:
          showDeleteFolderConfirm();
          break;
      }
    } else if (v.getId() == R.id.btn_check) {
      if (selectedChatIds.size() < selectableChatIds.length) {
        selectedChatIds.addAll(selectableChatIds);
      } else {
        selectedChatIds.clear();
      }
      adapter.updateAllValuedSettingsById(R.id.chat);
      onSelectedChatCountChanged();
    }
  }

  private void showDeleteFolderConfirm () {
    TdApi.ChatFolderInfo chatFolderInfo = tdlib.chatFolderInfo(chatFolderId);
    boolean hasMyInviteLinks = chatFolderInfo != null && chatFolderInfo.hasMyInviteLinks;
    tdlib.ui().showDeleteChatFolderConfirm(this, hasMyInviteLinks, this::deleteFolder);
  }

  private void onChatItemClicked (ListItem item) {
    boolean selectable = item.getBoolValue();
    if (!selectable) {
      return;
    }
    long chatId = item.getLongId();
    boolean isChecked = selectedChatIds.add(chatId) || !selectedChatIds.remove(chatId);
    DoubleTextWrapper data = (DoubleTextWrapper) item.getData();
    data.setIsChecked(isChecked, /* animated */ true);
    onSelectedChatCountChanged();
  }

  private void onSelectedChatCountChanged () {
    adapter.updateValuedSettingById(R.id.btn_check);
    updateActionButton();
  }

  private void joinSelectedChats () {
    if (mode != ChatFolderInviteLinkController.MODE_INVITE_LINK) {
      throw new IllegalStateException("mode = " + mode);
    }
    if (selectedChatIds.isEmpty()) {
      return;
    }
    if (chatFolderId == NO_CHAT_FOLDER_ID) {
      if (!tdlib.canAddShareableFolder()) {
        UI.forceVibrateError(actionButton);
        if (tdlib.hasPremium()) {
          showTooltip(actionButton, R.string.ShareableFoldersLimitReached, tdlib.addedShareableChatFolderCountMax());
        } else {
          tdlib.ui().showPremiumLimitInfo(this, actionButton, TdlibUi.PremiumLimit.SHAREABLE_FOLDER_COUNT);
        }
        return;
      }

      if (!tdlib.canCreateChatFolder()) {
        UI.forceVibrateError(actionButton);
        if (tdlib.hasPremium()) {
          showTooltip(actionButton, R.string.ChatFolderLimitReached, tdlib.chatFolderCountMax());
        } else {
          tdlib.ui().showPremiumLimitInfo(this, actionButton, TdlibUi.PremiumLimit.CHAT_FOLDER_COUNT);
        }
        return;
      }
    }
    long[] chatIds = selectedChatIds.toArray();
    tdlib.send(new TdApi.AddChatFolderByInviteLink(inviteLinkUrl, chatIds), tdlib.typedOkHandler(() -> {
      UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
    }));
    parent.hidePopupWindow(true);
  }

  private void processNewChats () {
    if (mode != ChatFolderInviteLinkController.MODE_NEW_CHATS) {
      throw new IllegalStateException("mode = " + mode);
    }
    long[] chatIds = selectedChatIds.toArray();
    tdlib.processChatFolderNewChats(chatFolderId, chatIds, tdlib.typedOkHandler(() -> {
      UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
    }));
    parent.hidePopupWindow(true);
  }

  private void deleteFolder () {
    if (mode != ChatFolderInviteLinkController.MODE_DELETE_FOLDER) {
      throw new IllegalStateException("mode = " + mode);
    }
    long[] leaveChatIds = selectedChatIds.toArray();
    tdlib.send(new TdApi.DeleteChatFolder(chatFolderId, leaveChatIds), tdlib.typedOkHandler(() -> {
      UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
    }));
    parent.hidePopupWindow(true);
  }

  private void showTooltip (View view, @StringRes int markdownStringRes, Object... formatArgs) {
    showTooltip(view, Lang.getMarkdownString(this, markdownStringRes, formatArgs));
  }

  private void showTooltip (View view, CharSequence text) {
    context()
      .tooltipManager()
      .builder(view)
      .controller(this)
      .show(tdlib, text)
      .hideDelayed();
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }

    @NonNull
    @Override
    public SettingHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      SettingHolder viewHolder = super.onCreateViewHolder(parent, viewType);
      if (viewType == ListItem.TYPE_DESCRIPTION) {
        viewHolder.itemView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          ListItem item = (ListItem) v.getTag();
          item.setHeight(bottom - top);
        });
      }
      return viewHolder;
    }

    @Override
    protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
      if (item.getId() == R.id.chat) {
        chatView.setEnabled(item.getBoolValue());

        DoubleTextWrapper data = (DoubleTextWrapper) item.getData();
        data.setIsChecked(selectedChatIds.has(item.getLongId()), isUpdate);
      }
    }

    @Override
    protected void setHeaderCheckBoxState (ListItem item, CheckBoxView checkBox, boolean isUpdate) {
      if (item.getId() == R.id.btn_check) {
        checkBox.setChecked(selectedChatIds.size() > 0, isUpdate);
        checkBox.setDisabled(selectableChatIds.length == 0, isUpdate);
        checkBox.setPartially(selectedChatIds.size() < selectableChatIds.length, isUpdate);
      } else {
        super.setHeaderCheckBoxState(item, checkBox, isUpdate);
      }
    }
  }
}
