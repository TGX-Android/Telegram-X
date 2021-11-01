package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.ProgressComponentView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.collection.LongSparseIntArray;

/**
 * Date: 2/8/18
 * Author: default
 */

public class SettingsWebsitesController extends RecyclerViewController<SettingsPrivacyController> implements View.OnClickListener, SettingsPrivacyController.WebsitesLoadListener {
  public SettingsWebsitesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_websites;
  }

  @Override
  public void setArguments (SettingsPrivacyController args) {
    super.setArguments(args);
    TdApi.ConnectedWebsites websites = args.getWebsites();
    if (websites == null) {
      args.setWebsitesLoadListener(this);
    } else {
      setWebsites(websites.websites);
    }
  }

  private void buildCells () {
    if (websites == null || isDestroyed() || adapter == null) {
      return;
    }

    if (websites.isEmpty()) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_WEBSITES_EMPTY, R.id.btn_loggedWebsites, 0, Strings.replaceBoldTokens(Lang.getString(R.string.NoActiveLogins), R.id.theme_color_background_textLight), false),
      }, false);
      return;
    }

    ArrayList<ListItem> items = new ArrayList<>(6 + websites.size() * 2);
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_terminateAllSessions, 0, R.string.TerminateAllWebSessions).setTextColorId(R.id.theme_color_textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ClearOtherWebSessionsHelp));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.OtherWebSessions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    boolean isFirst = true;
    for (TdApi.ConnectedWebsite website : websites) {
      if (isFirst) {
        isFirst = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_SESSION_WITH_AVATAR, R.id.btn_session).setLongId(website.id).setData(website));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ConnectedWebsitesDesc));
    adapter.setItems(items, false);
    TGLegacyManager.instance().addEmojiListener(adapter);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_terminateAllSessions: {
            if (isUpdate) {
              view.setEnabledAnimated(!disconnectingAllWebsites);
            } else {
              view.setEnabled(!disconnectingAllWebsites);
            }
            break;
          }
        }
      }

      @Override
      protected void setSession (ListItem item, int position, RelativeLayout parent, boolean isUpdate, TextView timeView, TextView titleView, TextView subtextView, TextView locationView, ProgressComponentView progressView, AvatarView avatarView) {
        TdApi.ConnectedWebsite website = (TdApi.ConnectedWebsite) item.getData();
        titleView.setText(Emoji.instance().replaceEmoji(website.domainName));
        subtextView.setText(Strings.concat(", ", Emoji.instance().replaceEmoji(tdlib.cache().userName(website.botUserId)), website.browser, website.platform));
        locationView.setText(Strings.concatIpLocation(website.ip, website.location));
        timeView.setText(Lang.timeOrDateShort(website.lastActiveDate, TimeUnit.SECONDS));
        avatarView.setUser(tdlib, website.botUserId, false);

        final boolean inProgress = inDisconnectProgress(website.id);
        parent.setEnabled(!inProgress);
        if (isUpdate) {
          progressView.animateFactor(inProgress ? 1f : 0f);
        } else {
          progressView.forceFactor(inProgress ? 1f : 0f);
        }
      }
    };

    if (websites != null) {
      buildCells();
    }

    if (getArguments() == null) {
      tdlib.client().send(new TdApi.GetConnectedWebsites(), object -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          switch (object.getConstructor()) {
            case TdApi.ConnectedWebsites.CONSTRUCTOR: {
              TdApi.ConnectedWebsite[] websites = ((TdApi.ConnectedWebsites) object).websites;
              setWebsites(websites);
              buildCells();
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
            default: {
              Log.unexpectedTdlibResponse(object, TdApi.GetConnectedWebsites.class, TdApi.ConnectedWebsites.class);
              break;
            }
          }
        }
      }));
    }

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(adapter);
    SettingsPrivacyController controller = getArguments();
    if (controller != null) {
      controller.setWebsitesLoadListener(null);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.WebSessionsTitle);
  }

  // Click

  private LongSparseIntArray disconnectingWebsites;
  private boolean disconnectingAllWebsites;

  private void disconnectAllWebsites () {
    if (disconnectingAllWebsites) {
      return;
    }
    disconnectingAllWebsites = true;
    adapter.updateAllSessions();
    tdlib.client().send(new TdApi.DisconnectAllWebsites(), object -> tdlib.ui().post(() -> {
      adapter.updateAllSessions();
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          websites.clear();
          buildCells();
          if (getArguments() != null) {
            getArguments().updateWebsites(websites);
          }
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    }));
  }

  private boolean inDisconnectProgress (long id) {
    return disconnectingAllWebsites || (disconnectingWebsites != null && disconnectingWebsites.get(id) == 1);
  }

  private void removeSession (TdApi.ConnectedWebsite website, int position) {
    int i = 0;
    for (TdApi.ConnectedWebsite existingWebsite : websites) {
      if (existingWebsite.id == website.id) {
        websites.remove(i);
        if (websites.isEmpty()) {
          buildCells();
        } else {
          adapter.removeRange(i != 0 ? position - 1 : position, 2);
        }
        if (getArguments() != null) {
          getArguments().updateWebsites(websites);
        }
        break;
      }
      i++;
    }
  }

  private void terminateSession (final TdApi.ConnectedWebsite website, boolean banUser, boolean needConfirm) {
    if (inDisconnectProgress(website.id)) {
      return;
    }
    if (needConfirm) {
      showSettings(new SettingsWrapBuilder(R.id.btn_terminateSession)
        .setRawItems(new ListItem[] {
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_banUser, 0, Lang.getString(R.string.DisconnectWebsiteBan, tdlib.cache().userName(website.botUserId)), R.id.btn_banUser, banUser)
        })
        .addHeaderItem(Lang.getString(R.string.TerminateWebSessionQuestion, website.domainName))
        .setIntDelegate((id, result) -> {
          boolean banUser1 = result.get(R.id.btn_banUser) == R.id.btn_banUser;
          terminateSession(website, banUser1, false);
        })
        .setSaveStr(R.string.DisconnectWebsite)
        .setSaveColorId(R.id.theme_color_textNegative)
      );
      return;
    }

    if (disconnectingWebsites == null) {
      disconnectingWebsites = new LongSparseIntArray();
    }
    disconnectingWebsites.put(website.id, 1);
    adapter.updateSessionByLongId(website.id);

    if (disconnectingWebsites.size() == websites.size()) {
      disconnectingAllWebsites = true;
      adapter.updateValuedSettingById(R.id.btn_terminateAllSessions);
    }

    tdlib.client().send(new TdApi.DisconnectWebsite(website.id), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        disconnectingWebsites.delete(website.id);
        int i = adapter.indexOfViewByLongId(website.id);
        adapter.updateSessionByPosition(i);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            removeSession(website, i);
            break;
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
        }
      }
    }));

    if (banUser) {
      tdlib.blockSender(new TdApi.MessageSenderUser(website.botUserId), true, tdlib.okHandler());
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_terminateAllSessions: {
        showOptions(Lang.getString(R.string.DisconnectAllWebsitesHint), new int[] {R.id.btn_terminateAllSessions, R.id.btn_cancel}, new String[]{Lang.getString(R.string.TerminateAllWebSessions), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_terminateAllSessions) {
            disconnectAllWebsites();
          }
          return true;
        });
        break;
      }
      case R.id.btn_session: {
        ListItem item = (ListItem) v.getTag();
        final TdApi.ConnectedWebsite website = (TdApi.ConnectedWebsite) item.getData();
        showOptions(website.domainName, new int[] {R.id.btn_terminateSession, R.id.btn_openChat}, new String[] {Lang.getString(R.string.DisconnectWebsiteAction), Lang.getString(R.string.OpenChat)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_chat_bubble_24}, (itemView, id) -> {
          switch (id) {
            case R.id.btn_openChat:
              tdlib.ui().openPrivateChat(this, website.botUserId, new TdlibUi.ChatOpenParameters().keepStack());
              break;
            case R.id.btn_terminateSession:
              terminateSession(website, false, true);
              break;
          }
          return true;
        });
        break;
      }
    }
  }

  // Website loader

  private ArrayList<TdApi.ConnectedWebsite> websites;

  private void setWebsites (TdApi.ConnectedWebsite[] websites) {
    this.websites = new ArrayList<>(websites.length);
    Collections.addAll(this.websites, websites);
  }

  @Override
  public void onWebsitesLoaded (TdApi.ConnectedWebsites websites) {
    if (!isDestroyed()) {
      setWebsites(websites.websites);
      buildCells();
    }
  }
}
