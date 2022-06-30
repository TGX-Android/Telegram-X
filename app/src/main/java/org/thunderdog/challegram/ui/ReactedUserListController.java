package org.thunderdog.challegram.ui;

import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.reaction.ReactedUsersAdapter;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ReactionListController;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class ReactedUserListController extends ViewController<ReactedUsersLayout> {
  private CustomRecyclerView recyclerView;
  private LinearLayoutManager manager;
  private ReactedUsersAdapter adapter;
  private List<Pair<TGUser, String>> users;
  private TdApi.Message message;

  public ReactedUserListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_reactedUsers;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.requestLayout();
  }

  @Override
  protected View onCreateView (Context context) {
    message = getArgumentsStrict().getMessage();
    users = new ArrayList<>();

    if (message.canGetAddedReactions) {
      tdlib.client().send(new TdApi.GetMessageAddedReactions(
          message.chatId,
          message.id,
          "",
          "",
          20),
          new AddedReactionHandler()
      );
    }


    manager = new LinearLayoutManager(context);
    manager.setOrientation(RecyclerView.VERTICAL);
    adapter = new ReactedUsersAdapter(this, null, 0, null);
    adapter.setUsers(users);
    recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, getArguments());
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
    recyclerView.setAdapter(adapter);

    return recyclerView;
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
  }

  private class UserHandler implements Client.ResultHandler {
    private final String reaction;

    public UserHandler (String reaction) {
      this.reaction = reaction;
    }

    @Override
    public void onResult (TdApi.Object object) {
      if (object.getConstructor() != TdApi.User.CONSTRUCTOR) {
        Log.e(object.toString());
        return;
      }
      TdApi.User user = (TdApi.User) object;
      if (users != null) {
        TGUser tgUser = new TGUser(tdlib, user);
        users.add(new Pair<>(tgUser, reaction));
        adapter.notifyItemInserted(users.size() - 1);
      }
    }
  }

  private class AddedReactionHandler implements Client.ResultHandler {
    @Override
    public void onResult (TdApi.Object object) {
      if (object.getConstructor() != TdApi.AddedReactions.CONSTRUCTOR) {
        Log.e(object.toString());
        return;
      }
      TdApi.AddedReactions addedReactions = (TdApi.AddedReactions) object;
      for (int i = 0; i < addedReactions.reactions.length; i++) {
        TdApi.MessageSender messageSender = addedReactions.reactions[i].senderId;
        if (messageSender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
          TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) messageSender;
          String reaction = addedReactions.reactions[i].reaction;
          tdlib.client().send(new TdApi.GetUser(userSender.userId), new UserHandler(reaction));
        }
      }
      if (!addedReactions.nextOffset.equals("")) {
        tdlib.client().send(new TdApi.GetMessageAddedReactions(
            message.chatId,
            message.id,
            "",
            addedReactions.nextOffset,
            20),
            this
        );
      }
    }
  }
}
