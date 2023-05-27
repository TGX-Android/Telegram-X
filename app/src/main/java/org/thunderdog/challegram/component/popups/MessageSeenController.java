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
 */
package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;

public class MessageSeenController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  private final TGMessage msg;
  private final TdApi.MessageViewers viewers;

  public static CharSequence getViewString (TGMessage msg, int count) {
    switch (msg.getMessage().content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.MessageSeenXListened, count);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.MessageSeenXPlayed, count);
      }
      default: {
        return Lang.pluralBold(R.string.xViews, count);
      }
    }
  }

  public static String getNobodyString (TGMessage msg) {
    switch (msg.getMessage().content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        return Lang.getString(R.string.MessageSeenNobodyListened);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        return Lang.getString(R.string.MessageSeenNobodyPlayed);
      }
      default: {
        return Lang.getString(R.string.MessageSeenNobody);
      }
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  public MessageSeenController (MediaLayout context, TGMessage msg, TdApi.MessageViewers viewers) {
    super(context, getViewString(msg, viewers.viewers.length).toString());
    this.msg = msg;
    this.viewers = viewers;
  }

  private boolean allowExpand;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_openLink) {
          view.setIconColorId(ColorId.textNeutral);
        } else {
          view.setIconColorId(ColorId.icon);
        }
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        infoView.showInfo(getViewString(msg, viewers.viewers.length));
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TGUser user = new TGUser(tdlib, tdlib.chatUser(item.getLongId()));
        user.setActionDateStatus(item.getIntValue(), msg.getMessage());
        userView.setUser(user);
      }
    };

    ViewSupport.setThemedBackground(recyclerView, ColorId.background);

    ArrayList<ListItem> items = new ArrayList<>();

    for (TdApi.MessageViewer viewer : viewers.viewers) {
      items.add(new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(viewer.userId).setIntValue(viewer.viewDate));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, R.string.MessageSeenPrivacy));
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));

    adapter.setItems(items.toArray(new ListItem[0]), false);
    initMetrics();

    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();
    if (allowExpand) {
      adapter.removeItem(adapter.getItemCount() - 1);
    }

    setAdapter(adapter);

    return contentView;
  }

  @Override
  protected int getInitialContentHeight () {
    if (viewers != null) {
      int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_USER) * viewers.viewers.length;
      for (int i = viewers.viewers.length; i < adapter.getItemCount(); i++) {
        ListItem item = adapter.getItems().get(i);
        if (item.getViewType() == ListItem.TYPE_DESCRIPTION) {
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
    return allowExpand;
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
    }
  }
}