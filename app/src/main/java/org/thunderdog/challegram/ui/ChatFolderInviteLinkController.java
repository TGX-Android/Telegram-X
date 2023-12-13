package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.WindowManager;

import androidx.annotation.IntDef;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ViewPager;

import me.vkryl.android.widget.FrameLayoutFix;

public class ChatFolderInviteLinkController extends BottomSheetViewController<ChatFolderInviteLinkController.Arguments> {

  @IntDef({MODE_INVITE_LINK, MODE_NEW_CHATS, MODE_DELETE_FOLDER})
  public @interface Mode {
  }

  public static final int MODE_INVITE_LINK = 0;
  public static final int MODE_NEW_CHATS = 1;
  public static final int MODE_DELETE_FOLDER = 2;

  public static class Arguments {
    public final @Mode int mode;
    public final int chatFolderId;
    public String chatFolderTitle;
    public final long[] selectableChatIds;
    public final String inviteLinkUrl;
    public final TdApi.ChatFolderInviteLinkInfo inviteLinkInfo;

    public Arguments (String inviteLink, TdApi.ChatFolderInviteLinkInfo inviteLinkInfo) {
      this.mode = MODE_INVITE_LINK;
      this.chatFolderId = inviteLinkInfo.chatFolderInfo.id;
      this.chatFolderTitle = inviteLinkInfo.chatFolderInfo.title;
      this.selectableChatIds = inviteLinkInfo.missingChatIds;
      this.inviteLinkUrl = inviteLink;
      this.inviteLinkInfo = inviteLinkInfo;
    }

    private Arguments (@Mode int mode, int chatFolderId, String chatFolderTitle, long[] chatIds) {
      this.mode = mode;
      this.chatFolderId = chatFolderId;
      this.chatFolderTitle = chatFolderTitle;
      this.selectableChatIds = chatIds;
      this.inviteLinkUrl = null;
      this.inviteLinkInfo = null;
    }

    public static Arguments newChats (TdApi.ChatFolderInfo chatFolderInfo, long[] chatIds) {
      return newChats(chatFolderInfo.id, chatFolderInfo.title, chatIds);
    }

    public static Arguments newChats (int chatFolderId, String chatFolderTitle, long[] chatIds) {
      return new Arguments(MODE_NEW_CHATS, chatFolderId, chatFolderTitle, chatIds);
    }

    public static Arguments deleteFolder (TdApi.ChatFolderInfo chatFolderInfo, long[] chatIds) {
      return deleteFolder(chatFolderInfo.id, chatFolderInfo.title, chatIds);
    }

    public static Arguments deleteFolder (int chatFolderId, String chatFolderTitle, long[] chatIds) {
      return new Arguments(MODE_DELETE_FOLDER, chatFolderId, chatFolderTitle, chatIds);
    }
  }

  private final ChatFolderInviteLinkControllerPage singlePage;

  public ChatFolderInviteLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.singlePage = new ChatFolderInviteLinkControllerPage(this);
  }

  @Override
  public int getId () {
    return R.id.controller_chatFolderInviteLink;
  }

  @Override
  protected int getPagerItemCount () {
    return 1;
  }

  @Override
  protected void onBeforeCreateView () {
    singlePage.getValue();
  }

  @Override
  protected void onAfterCreateView () {
    setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    singlePage.setArguments(args);
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    pager.setOffscreenPageLimit(1);
    tdlib.ui().post(this::launchOpenAnimation);
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    if (position != 0) return null;
    setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
    setDefaultListenersAndDecorators(singlePage);
    return singlePage;
  }

  @Override
  protected int getHeaderHeight () {
    return Screen.dp(56f);
  }

  @Override
  protected int getContentOffset () {
    return (getTargetHeight() - getHeaderHeight(true)) / 2;
  }

  @Override
  protected boolean canHideByScroll () {
    return true;
  }

  @Override
  protected HeaderView onCreateHeaderView () {
    return singlePage.getHeaderView();
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  protected void setupPopupLayout (PopupLayout popupLayout) {
    popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    popupLayout.setBoundController(singlePage);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    popupLayout.setIgnoreHorizontal();
  }
}
