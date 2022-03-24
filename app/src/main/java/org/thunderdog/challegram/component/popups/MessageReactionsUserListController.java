package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBox;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.td.Td;

public class MessageReactionsUserListController extends RecyclerViewController<MessageReactionsUserListController.Args> implements View.OnClickListener {
  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      getArgumentsStrict().closeListener.run();
      tdlib.ui().openPrivateProfile(this, (Long) v.getTag(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }

  public static class Args {
    private final long chatId, msgId;
    private final String srcReaction;
    private final Runnable closeListener;

    public Args (long chatId, long msgId, String srcReaction, Runnable closeListener) {
      this.chatId = chatId;
      this.msgId = msgId;
      this.srcReaction = srcReaction;
      this.closeListener = closeListener;
    }
  }

  private SettingsAdapter adapter;

  private List<TdApi.AddedReaction> reactions = new ArrayList<>();
  private boolean canLoadMore;
  private int totalCount = -1;

  private String loadOffset;

  public MessageReactionsUserListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_messageReactedSingle;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        infoView.showInfo(MessageReactorsController.getViewString(totalCount));
      }

      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBox checkBox, boolean isUpdate) {
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, item.getLongId(), true);
        //wrapper.setSubtitle(Lang.pluralBold(R.string.xLinks, item.getIntValue()));
        //wrapper.setIgnoreOnline(true);
        chatView.setChat(wrapper);
        chatView.setTag(item.getLongId());
        chatView.clearPreviewChat();
        chatView.setOnLongClickListener(null);
        chatView.setPreviewActionListProvider(null);
      }
    };

    recyclerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    recyclerView.setAdapter(adapter);

    loadInitial();
  }

  private void loadInitial () {
    tdlib().client().send(new TdApi.GetMessageAddedReactions(getArgumentsStrict().chatId, getArgumentsStrict().msgId, getArgumentsStrict().srcReaction, loadOffset, 20), result -> {
      if (result.getConstructor() == TdApi.AddedReactions.CONSTRUCTOR) {
        TdApi.AddedReactions addedReactions = (TdApi.AddedReactions) result;

        reactions.addAll(Arrays.asList(addedReactions.reactions));

        tdlib().ui().post(() -> {
          if (!isDestroyed()) {
            if (this.totalCount == -1) this.totalCount = addedReactions.totalCount;
            this.loadOffset = addedReactions.nextOffset;
            this.canLoadMore = !addedReactions.nextOffset.isEmpty();
            buildCells();
          }
        });
      }
    });
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    for (TdApi.AddedReaction r : reactions) {
      items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.user, 0, 0).setLongId(Td.getSenderUserId(r.senderId)).setDrawModifier(new DrawModifier() {
        @Override
        public void afterDraw (View view, Canvas c) {
          DrawModifier.super.afterDraw(view, c);
        }
      }));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));

    adapter.setItems(items, false);
  }
}
