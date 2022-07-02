package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactedUsersLayout extends FrameLayoutFix {
  private ReactedUserListController controller;
  private TdApi.Message message;
  private TdApi.Chat chat;
  private Context context;
  private Tdlib tdlib;

  public ReactedUsersLayout (@NonNull Context context, Tdlib tdlib) {
    super(context);
    this.context = context;
    this.tdlib = tdlib;
  }

  public void init (TdApi.Message message, TdApi.Chat chat) {
    this.message = message;
    this.chat = chat;
    controller = new ReactedUserListController(context, tdlib);
    controller.setArguments(this);
    controller.get().setLayoutParams(new FrameLayoutFix.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(controller.get());
  }

  public void setReaction (String reaction) {
    controller.setReaction(reaction);
  }

  public TdApi.Message getMessage () {
    return message;
  }

  public TdApi.Chat getChat () {
    return chat;
  }
}
