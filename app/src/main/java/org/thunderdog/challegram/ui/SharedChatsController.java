package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.widget.EmptySmartView;

import java.util.ArrayList;

import me.vkryl.td.ChatId;

/**
 * Date: 8/17/17
 * Author: default
 */

public class SharedChatsController extends SharedBaseController<DoubleTextWrapper> implements TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, ChatListener {
  public SharedChatsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /*@Override
  public int getIcon () {
    return R.drawable.baseline_group_20;
  }*/

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TabSharedGroups);
  }

  @Override
  protected TdApi.Function buildRequest (long chatId, long messageThreadId, String query, long offset, String secretOffset, int limit) {
    return new TdApi.GetGroupsInCommon(tdlib.chatUserId(chatId), offset, limit);
  }

  @Override
  protected String getExplainedTitle () {
    return Lang.getString(R.string.GroupsInCommon);
  }

  protected boolean supportsMessageContent () {
    return false;
  }

  @Override
  protected boolean needDateSectionSplitting () {
    return false;
  }

  @Override
  protected boolean canSearch () {
    return false;
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<DoubleTextWrapper> data) {
    return Lang.pluralBold(R.string.xGroups, data.size());
  }

  @Override
  protected int getEmptySmartMode () {
    return EmptySmartView.MODE_EMPTY_GROUPS;
  }

  @Override
  protected DoubleTextWrapper parseObject (TdApi.Object object) {
    return new DoubleTextWrapper(tdlib, (TdApi.Chat) object);
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_CHAT_SMALL;
  }

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CHAT_SMALL) {
      tdlib.ui().openChat(this, ((DoubleTextWrapper) item.getData()).getChatId(),null);
    }
  }

  @Override
  protected long getCurrentOffset (ArrayList<DoubleTextWrapper> data, long emptyValue) {
    return data == null || data.isEmpty() ? emptyValue : data.get(data.size() - 1).getChatId();
  }

  @Override
  protected boolean needsDefaultLongPress () {
    return false;
  }

  @Override
  protected boolean supportsMessageClearing () {
    return false;
  }

  @Override
  protected int getItemCellHeight () {
    return Screen.dp(62f);
  }

  @Override
  protected boolean probablyHasEmoji () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);
    tdlib.cache().subscribeToAnyUpdates(this);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromAnyUpdates(this);
  }

  // Updates for texts

  private void updateChatById (final long chatId) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && data != null && !data.isEmpty()) {
        for (DoubleTextWrapper wrapper : data) {
          if (chatId == wrapper.getChatId()) {
            wrapper.updateTitleAndPhoto();
            break;
          }
        }
      }
    });
  }

  private void updateChatSubtitle (final long chatId) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && data != null && !data.isEmpty()) {
        for (DoubleTextWrapper wrapper : data) {
          if (chatId == wrapper.getChatId()) {
            wrapper.updateSubtitle();
            break;
          }
        }
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    updateChatById(chatId);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    updateChatById(chatId);
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    updateChatSubtitle(chatId);
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    updateChatSubtitle(ChatId.fromBasicGroupId(basicGroup.id));
  }

  @Override
  public void onBasicGroupFullUpdated (int basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    updateChatSubtitle(ChatId.fromBasicGroupId(basicGroupId));
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    updateChatSubtitle(ChatId.fromSupergroupId(supergroup.id));
  }

  @Override
  public void onSupergroupFullUpdated (int supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    updateChatSubtitle(ChatId.fromSupergroupId(supergroupId));
  }
}
