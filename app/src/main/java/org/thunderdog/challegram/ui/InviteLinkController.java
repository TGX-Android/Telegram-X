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
 * File created on 18/02/2016 at 18:52
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class InviteLinkController extends ViewController<InviteLinkController.Arguments> implements View.OnClickListener, Client.ResultHandler, Unlockable {
  private static final int FLAG_GETTING_LINK = 0x01;

  private long chatId;
  private @Nullable TdApi.ChatInviteLink inviteLink;
  private Callback callback;
  private LinkAdapter adapter;
  private boolean isChannel;
  private int flags;

  public InviteLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLink);
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);

    chatId = args.chatId;
    inviteLink = args.inviteLink;

    isChannel = tdlib.isChannel(args.chatId);
  }

  @Override
  public int getId () {
    return R.id.controller_inviteLink;
  }

  RecyclerView contentView;

  @Override
  public View getViewForApplyingOffsets () {
    return contentView;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new RecyclerView(context);
    ViewSupport.setThemedBackground(contentView, ColorId.background, this);
    contentView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    contentView.setAdapter(adapter = new LinkAdapter(context, this));

    if (inviteLink == null) {
      tdlib.getPrimaryChatInviteLink(chatId, this);
    }

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);

    return wrapper;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private void exportLink () {
    if ((flags & FLAG_GETTING_LINK) == 0) {
      flags |= FLAG_GETTING_LINK;
      tdlib.client().send(new TdApi.ReplacePrimaryChatInviteLink(chatId), this);
    }
  }

  @Override
  public void unlock () {
    flags &= ~FLAG_GETTING_LINK;
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.ChatInviteLink.CONSTRUCTOR: {
        final TdApi.ChatInviteLink newInviteLink = (TdApi.ChatInviteLink) object;
        UI.post(() -> {
          if (callback != null) {
            callback.onInviteLinkChanged(newInviteLink);
          }
          if (!isDestroyed()) {
            inviteLink = newInviteLink;
            adapter.notifyItemChanged(0);
            flags &= ~FLAG_GETTING_LINK;
          }
        });
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        UI.unlock(this);
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.ReplacePrimaryChatInviteLink.class, TdApi.ChatInviteLink.class);
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_copyLink) {
      if (inviteLink != null) {
        UI.copyText(inviteLink.inviteLink, R.string.CopiedLink);
      } else {
        UI.showToast(R.string.GeneratingLink, Toast.LENGTH_SHORT);
      }
    } else if (viewId == R.id.btn_revokeLink) {
      exportLink();
    } else if (viewId == R.id.btn_share) {
      if (inviteLink != null) {
        String url = inviteLink.inviteLink;
        String chatName = tdlib.chatTitle(chatId);

        String exportText = Lang.getString(tdlib.isChannel(chatId) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, url);
        String text = Lang.getString(R.string.ShareTextLink, chatName, url);

        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(text).setShare(exportText, null));
        c.show();
      } else {
        UI.showToast(R.string.GeneratingLink, Toast.LENGTH_SHORT);
      }
    }
  }

  private static class LinkCell {
    public int type;

    public LinkCell (int type) {
      this.type = type;
    }
  }

  private static class LinkAdapter extends RecyclerView.Adapter<LinkHolder> {
    private final Context context;
    private final InviteLinkController controller;
    private ArrayList<LinkCell> cells;

    public LinkAdapter (Context context, InviteLinkController controller) {
      this.context = context;
      this.controller = controller;
      this.cells = new ArrayList<>(8);
      buildCells();
    }

    private void buildCells () {
      cells.add(new LinkCell(LinkHolder.LINK));
      cells.add(new LinkCell(LinkHolder.SHADOW_BOTTOM));
      cells.add(new LinkCell(LinkHolder.HINT));
      cells.add(new LinkCell(LinkHolder.SHADOW_TOP));
      LinkCell actionCell = new LinkCell(LinkHolder.ACTION);
      LinkCell separator = new LinkCell(LinkHolder.SEPARATOR);
      cells.add(actionCell); // Copy
      cells.add(separator);
      cells.add(actionCell); // Revoke
      cells.add(separator);
      cells.add(actionCell); // Share
      cells.add(new LinkCell(LinkHolder.SHADOW_BOTTOM));
    }

    @Override
    public LinkHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return LinkHolder.create(context, controller.tdlib(), viewType, controller);
    }

    @Override
    public void onBindViewHolder (LinkHolder holder, int position) {
      switch (getItemViewType(position)) {
        case LinkHolder.LINK: {
          ((TextView) holder.itemView).setText(controller.inviteLink != null ? controller.inviteLink.inviteLink : Lang.getString(R.string.GeneratingLink));
          break;
        }
        case LinkHolder.HINT: {
          ((TextView) holder.itemView).setText(Lang.getString(controller.isChannel ? R.string.ChannelLinkInfo : R.string.LinkInfo));
          break;
        }
        case LinkHolder.ACTION: {
          int id, string;
          switch (position) {
            case 4: id = R.id.btn_copyLink; string = R.string.CopyLink; break;
            case 6: id = R.id.btn_revokeLink; string = R.string.RevokeLink; break;
            case 8: id = R.id.btn_share; string = R.string.ShareLink; break;
            default: id = 0; string = 0; break;
          }
          holder.itemView.setId(id);
          ((SettingView) holder.itemView).setName(string);
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      return cells.get(position).type;
    }

    @Override
    public int getItemCount () {
      return cells.size();
    }
  }

  private static class LinkHolder extends RecyclerView.ViewHolder {
    public static final int SHADOW_TOP = 0;
    public static final int SHADOW_BOTTOM = 1;
    public static final int LINK = 2;
    public static final int HINT = 3;
    public static final int ACTION = 4;
    public static final int SEPARATOR = 5;

    public LinkHolder (View itemView) {
      super(itemView);
    }

    public static LinkHolder create (Context context, Tdlib tdlib, int viewType, InviteLinkController controller) {
      switch (viewType) {
        case SHADOW_TOP: {
          ShadowView view;

          view = new ShadowView(context);
          view.setSimpleTopShadow(true, controller);
          controller.addThemeInvalidateListener(view);

          return new LinkHolder(view);
        }
        case SHADOW_BOTTOM: {
          ShadowView view;

          view = new ShadowView(context);
          view.setSimpleBottomTransparentShadow(true);
          controller.addThemeInvalidateListener(view);

          return new LinkHolder(view);
        }
        case HINT: {
          TextView view;

          view = new NoScrollTextView(context);
          view.setTextColor(Theme.textDecent2Color());
          view.setTypeface(Fonts.getRobotoRegular());
          view.setGravity(Lang.gravity());
          view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          view.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(9f));
          controller.addThemeTextColorListener(view, ColorId.background_textLight);

          return new LinkHolder(view);
        }
        case SEPARATOR: {
          SeparatorView view;

          view = new SeparatorView(context);
          view.setUseFilling();
          view.setOffsets(Screen.dp(16f), 0);
          view.setSeparatorHeight(Screen.dp(1f));
          view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f)));
          controller.addThemeInvalidateListener(view);

          return new LinkHolder(view);
        }
        case LINK: {
          final TextView view;

          view = new NoScrollTextView(context);
          view.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
          view.setTypeface(Fonts.getRobotoRegular());
          view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
          view.setTextColor(Theme.textAccentColor());
          controller.addThemeTextAccentColorListener(view);
          ViewSupport.setThemedBackground(view, ColorId.filling, controller);
          view.setPadding(Screen.dp(16f), Screen.dp(17f), Screen.dp(16f), Screen.dp(17f));
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

          return new LinkHolder(view);
        }
        case ACTION: {
          SettingView view;

          view = new SettingView(context, tdlib);
          view.setId(R.id.btn_inviteLink);
          view.setType(SettingView.TYPE_SETTING);
          view.setName(R.string.InviteLink);
          view.setOnClickListener(controller);
          controller.addThemeInvalidateListener(view);

          return new LinkHolder(view);
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }

  public static class Arguments {
    long chatId;
    TdApi.ChatInviteLink inviteLink;

    public Arguments (long chatId, TdApi.ChatInviteLink inviteLink) {
      this.chatId = chatId;
      this.inviteLink = inviteLink;
    }
  }

  public interface Callback {
    void onInviteLinkChanged (TdApi.ChatInviteLink newInviteLink);
  }
}
