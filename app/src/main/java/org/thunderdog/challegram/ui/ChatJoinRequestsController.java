package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.popups.JoinRequestsComponent;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;

public class ChatJoinRequestsController extends RecyclerViewController<ChatJoinRequestsController.Args> implements View.OnClickListener {
  private JoinRequestsComponent component;

  public ChatJoinRequestsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    component = new JoinRequestsComponent(this, args.chatId, args.inviteLink);
  }

  @Override
  public void onClick (View v) {
    component.onClick(v);
  }

  @Override
  public int getId () {
    return R.id.controller_chatJoinRequests;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLinkRequests);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    component.onCreateView(context, recyclerView);
  }

  @Override
  public void destroy () {
    super.destroy();
    component.destroy();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return component.needAsynchronousAnimation();
  }

  public static class Args {
    private final long chatId;
    private final String inviteLink;

    public Args (long chatId, String inviteLink) {
      this.chatId = chatId;
      this.inviteLink = inviteLink;
    }
  }
}
