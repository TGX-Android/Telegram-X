package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
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

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class MessageReactionsUserListController extends RecyclerViewController<MessageReactionsUserListController.Args> implements View.OnClickListener {
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

  private final List<TdApi.AddedReaction> reactions = new ArrayList<>();
  private int totalCount = -1;
  private int scrolledY = 0;

  private String loadOffset;
  private boolean canLoadMore;
  private boolean isLoadingMore;

  public MessageReactionsUserListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean shouldRenderEmojiInList () {
    return getArgumentsStrict().srcReaction.isEmpty();
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
        chatView.setChat(wrapper);
        chatView.setTag(item.getLongId());
        chatView.clearPreviewChat();
        chatView.setOnLongClickListener(null);
        chatView.setPreviewActionListProvider(null);

        if (shouldRenderEmojiInList()) {
          chatView.setDrawModifier(item.getDrawModifier());
          ImageFile staticFile = new ImageFile(tdlib, tdlib.getReaction(item.getStringValue()).staticIcon.sticker);
          staticFile.setSize(Screen.dp(48f));
          staticFile.setScaleType(ImageFile.FIT_CENTER);
          staticFile.setNoBlur();
          chatView.getReceiver().requestFile(staticFile);
        }
      }
    };

    recyclerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        scrolledY += dy;
        if (canLoadMore && !isLoadingMore && !reactions.isEmpty() && !loadOffset.isEmpty()) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= reactions.size()) {
            loadMore();
          }
        }
      }
    });

    loadInitial();
  }

  private void loadInitial () {
    load(result -> {
      reactions.addAll(Arrays.asList(result.reactions));
      if (this.totalCount == -1) this.totalCount = result.totalCount;
      this.loadOffset = result.nextOffset;
      this.canLoadMore = !result.nextOffset.isEmpty();
      buildCells();
    });
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore || reactions.isEmpty()) {
      return;
    }

    isLoadingMore = true;
    load(result -> {
      reactions.addAll(Arrays.asList(result.reactions));
      this.loadOffset = result.nextOffset;
      this.canLoadMore = !result.nextOffset.isEmpty() && result.reactions.length > 0;
      this.isLoadingMore = false;

      if (result.reactions.length > 0) {
        ArrayList<ListItem> newItems = new ArrayList<>();
        for (int i = 0; i < result.reactions.length; i++) {
          newItems.add(createUserItem(result.reactions[i]));
          newItems.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        newItems.remove(newItems.size() - 1);
        adapter.addItems(adapter.getItemCount() - 2, newItems.toArray(new ListItem[0]));
      }
    });
  }

  private void load (RunnableData<TdApi.AddedReactions> rsCallback) {
    tdlib().client().send(new TdApi.GetMessageAddedReactions(getArgumentsStrict().chatId, getArgumentsStrict().msgId, getArgumentsStrict().srcReaction, loadOffset, 20), result -> {
      if (result.getConstructor() == TdApi.AddedReactions.CONSTRUCTOR) {
        tdlib().ui().post(() -> {
          if (!isDestroyed()) {
            rsCallback.runWithData((TdApi.AddedReactions) result);
          }
        });
      }
    });
  }

  private ListItem createUserItem (TdApi.AddedReaction r) {
    return new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.user, 0, 0).setStringValue(r.reaction).setLongId(Td.getSenderUserId(r.senderId)).setDrawModifier(new DrawModifier() {
      @Override
      public void afterDraw (View view, Canvas c) {
        ImageReceiver receiver = ((SmallChatView) view).getReceiver();
        int right = Screen.dp(18f);
        int size = Screen.dp(24f);
        receiver.setBounds(view.getMeasuredWidth() - right - size, view.getMeasuredHeight() / 2 - size / 2, view.getMeasuredWidth() - right, view.getMeasuredHeight() / 2 + size / 2);
        receiver.draw(c);
      }
    });
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    for (TdApi.AddedReaction r : reactions) {
      items.add(createUserItem(r));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    items.remove(items.size() - 1);

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      getArgumentsStrict().closeListener.run();
      tdlib.ui().openPrivateProfile(this, (Long) v.getTag(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }

  public void dispatchEventToRecycler (MotionEvent event) {
    try {
      int yOffset = HeaderView.getTopOffset() + HeaderView.getHeaderHeight(this);
      MotionEvent modified = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), event.getX(), event.getY() - yOffset, event.getMetaState());
      getRecyclerView().dispatchTouchEvent(modified);
      modified.recycle();
    } catch (IllegalArgumentException iae) {
      // over9000 fingers at the same time can cause native crash, trying to avoid this...
      getRecyclerView().dispatchTouchEvent(event); // sending any touch event even with the wrong offset is better than sending nothing
    }
  }

  public boolean hasScrolled () {
    return scrolledY > 0;
  }
}
