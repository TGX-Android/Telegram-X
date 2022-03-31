package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.widget.CheckBox;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class MessageReactorsSmallController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  private final List<TdApi.AddedReaction> reactions = new ArrayList<>();
  private final int totalCount;
  private final long chatId, msgId;

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  public MessageReactorsSmallController (MediaLayout context, long chatId, long msgId, int reactionCount) {
    super(context, MessageReactorsController.getViewString(reactionCount).toString());
    this.totalCount = reactionCount;
    this.chatId = chatId;
    this.msgId = msgId;
  }

  private boolean allowExpand;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    adapter = new SettingsAdapter(this) {
      @Override
      public int measureScrollTop (int position) {
        if (position == 0) return 0;
        return super.measureScrollTop(position);
      }

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
        chatView.setDrawModifier(item.getDrawModifier());
        ImageFile staticFile = new ImageFile(tdlib, tdlib.getReaction(item.getStringValue()).staticIcon.sticker);
        staticFile.setSize(Screen.dp(48f));
        staticFile.setScaleType(ImageFile.FIT_CENTER);
        staticFile.setNoBlur();
        chatView.getReceiver().requestFile(staticFile);
      }
    };

    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_background);
    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();

    setAdapter(adapter);
    recyclerView.setAdapter(adapter);
    loadInitial();

    return contentView;
  }

  @Override
  protected int getInitialContentHeight () {
    int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_CHAT_SMALL) * totalCount;
    initialContentHeight += Screen.dp(24f);
    return Math.min(super.getInitialContentHeight(), initialContentHeight);
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
    return R.id.controller_messageReactedSingle;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      mediaLayout.hide(false);
      tdlib.ui().openPrivateProfile(this, (long) v.getTag(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }

  private void loadInitial () {
    load(result -> {
      reactions.addAll(Arrays.asList(result.reactions));
      buildCells();
    });
  }

  private void load (RunnableData<TdApi.AddedReactions> rsCallback) {
    tdlib().client().send(new TdApi.GetMessageAddedReactions(chatId, msgId, "", "", 20), result -> {
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
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }

    if (!items.isEmpty()) items.remove(items.size() - 1);

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (allowExpand) items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));

    adapter.setItems(items, false);
  }
}