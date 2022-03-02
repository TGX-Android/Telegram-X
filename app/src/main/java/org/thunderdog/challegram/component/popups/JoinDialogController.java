package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.text.SpannableString;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.DetachedChatHeaderView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.CallItem;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.Td;

public class JoinDialogController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private final int DESCRIPTION_PADDING = Screen.dp(16f);

  private SettingsAdapter adapter;

  private final @Nullable TdlibUi.UrlOpenParameters openParameters;
  private final String inviteLink;
  private final TdApi.ChatInviteLinkInfo inviteLinkInfo;
  private final int measuredTextSize;

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  public JoinDialogController (MediaLayout context, String inviteLink, TdApi.ChatInviteLinkInfo inviteLinkInfo, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, "");
    this.inviteLink = inviteLink;
    this.inviteLinkInfo = inviteLinkInfo;
    this.openParameters = openParameters;
    this.measuredTextSize = U.getTextHeight(inviteLinkInfo.description, Screen.currentWidth() - (DESCRIPTION_PADDING * 2), Paints.getRegularTextPaint(15f)) + DESCRIPTION_PADDING;
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_join) {
          view.setIconColorId(R.id.theme_color_textNeutral);
        } else {
          view.setIconColorId(R.id.theme_color_icon);
        }
      }

      @Override
      protected void setText (ListItem item, CustomTextView view, boolean isUpdate) {
        if (item.getId() == R.id.description) {
          view.setPadding(DESCRIPTION_PADDING, DESCRIPTION_PADDING, DESCRIPTION_PADDING, DESCRIPTION_PADDING / 2);
          view.setText(item.getString(), TextEntity.valueOf(tdlib, item.getString().toString(), Td.findEntities(item.getString().toString()), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(view))), false);
          view.setTextSize(15f);
        } else {
          view.setPadding(0, 0, 0, 0);
          super.setText(item, view, isUpdate);
        }
      }

      @Override
      protected void setChatHeader (ListItem item, int position, DetachedChatHeaderView headerView, boolean isLarge) {
        headerView.bindWith(tdlib, inviteLinkInfo);
      }
    };

    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_filling);

    boolean isChannel = TD.isChannel(inviteLinkInfo.type);

    ArrayList<ListItem> items = new ArrayList<>();

    if (inviteLinkInfo.photo != null) {
      items.add(new ListItem(ListItem.TYPE_CHAT_HEADER_LARGE));
    } else {
      //items.add(new ListItem(ListItem.TYPE_CHAT_HEADER_SMALL));
    }

    if (inviteLinkInfo.description != null && inviteLinkInfo.description.length() > 0) {
      items.add(new ListItem(ListItem.TYPE_TEXT_VIEW, R.id.description, 0, inviteLinkInfo.description, false));
    }

    if (inviteLinkInfo.memberUserIds != null && inviteLinkInfo.memberUserIds.length > 0) {

    }

    if (inviteLinkInfo.createsJoinRequest) {
      items.add(new ListItem(ListItem.TYPE_INFO, R.id.message, 0, Lang.getString(isChannel ? R.string.RequestToJoinChannelInfo : R.string.RequestToJoinGroupInfo), false));
    }

    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_join, R.drawable.baseline_person_add_24, inviteLinkInfo.createsJoinRequest ? Lang.getString(isChannel ? R.string.RequestJoinChannelBtn : R.string.RequestJoinGroupBtn) : Lang.getString(isChannel ? R.string.JoinChannel : R.string.JoinChat), false).setTextColorId(R.id.theme_color_textNeutral));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_cancel, R.drawable.baseline_cancel_24, R.string.Cancel));

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = HeaderView.getSize(false);
    params.bottomMargin = HeaderView.getTopOffset();
    recyclerView.setLayoutParams(params);

    adapter.setItems(items.toArray(new ListItem[0]), false);
    initMetrics();

    setAdapter(adapter);

    return contentView;
  }

  @Override
  protected int getInitialContentHeight () {
    if (adapter != null) {
      int initialContentHeight = 0;
      for (int i = 0; i < adapter.getItemCount(); i++) {
        ListItem item = adapter.getItems().get(i);
        if (item.getViewType() == ListItem.TYPE_TEXT_VIEW) {
          initialContentHeight += item.getId() == R.id.description ? measuredTextSize : Screen.dp(24f);
        } else if (item.getViewType() == ListItem.TYPE_DESCRIPTION) {
          initialContentHeight += Screen.dp(24f);
        } else {
          initialContentHeight += SettingHolder.measureHeightForType(item.getViewType());
        }
      }
      return Math.min(super.getInitialContentHeight(), initialContentHeight);
    }
    return super.getInitialContentHeight();
  }

  @Override
  protected boolean canExpandHeight () {
    return false;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  @Override
  public int getId () {
    return R.id.controller_messageSeen;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      mediaLayout.hide(false);
      tdlib.ui().openPrivateProfile(this, ((ListItem) v.getTag()).getLongId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    } else if (v.getId() == R.id.btn_cancel) {
      mediaLayout.hide(false);
    }
  }
}