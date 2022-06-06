/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.PollListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.telegram.UserListManager;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CenterDecoration;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.td.ChatId;

public class PollResultsController extends RecyclerViewController<PollResultsController.Args> implements PollListener, UserListManager.ChangeListener, View.OnClickListener {
  public static class Args {
    public TdApi.Poll poll;
    public final long chatId, messageId;

    public Args(TdApi.Poll poll, long chatId, long messageId) {
      this.poll = poll;
      this.chatId = chatId;
      this.messageId = messageId;
    }
  }

  public PollResultsController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId() {
    return R.id.controller_pollResults;
  }

  private DoubleHeaderView headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private TdApi.Poll getPoll () {
    return getArgumentsStrict().poll;
  }

  private boolean isQuiz () {
    return getPoll().type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
  }

  private SettingsAdapter adapter;

  private void updateHeader (boolean isUpdate) {
    switch (getPoll().type.getConstructor()) {
      case TdApi.PollTypeRegular.CONSTRUCTOR: {
        if (!isUpdate)
          headerCell.setTitle(R.string.PollResultsTitle);
        headerCell.setSubtitle(Lang.plural(R.string.xVotes, getPoll().totalVoterCount));
        break;
      }
      case TdApi.PollTypeQuiz.CONSTRUCTOR: {
        if (!isUpdate)
          headerCell.setTitle(R.string.QuizResultsTitle);
        headerCell.setSubtitle(Lang.plural(R.string.xAnswers, getPoll().totalVoterCount));
        break;
      }
    }
  }

  private static final boolean NEED_CENTER_DECORATION = false;

  private static class ListCache implements UserListManager.ChangeListener {
    private final Tdlib tdlib;
    private final UserListManager voters;
    private final SettingsAdapter adapter;

    public ListCache (ViewController<?> context, long chatId, long messageId, int optionId) {
      this.tdlib = context.tdlib();
      this.adapter = new SettingsAdapter(context) {
        @Override
        protected void setChatData (ListItem item, VerticalChatView chatView) {
          // chatView.setPreviewActionListProvider(CallListController.this);
          chatView.setChat((TGFoundChat) item.getData());
        }
      };
      this.adapter.setNoEmptyProgress();
      this.voters = new UserListManager(tdlib, 50, 50, this) {
        @Override
        protected TdApi.Function<?> nextLoadFunction (boolean reverse, int itemCount, int loadCount) {
          return new TdApi.GetPollVoters(chatId, messageId, optionId, itemCount, loadCount);
        }
      };
      this.voters.loadInitialChunk(null);
    }

    @Override
    public void onItemsAdded (ListManager<Long> list, List<Long> items, int startIndex, boolean isInitialChunk) {
      List<ListItem> itemsToAdd = new ArrayList<>(items.size());
      for (long userId : items) {
        itemsToAdd.add(new ListItem(ListItem.TYPE_CHAT_VERTICAL, R.id.user).setData(new TGFoundChat(tdlib, userId).setNoUnread()).setLongId(ChatId.fromUserId(userId)));
      }
      adapter.getItems().addAll(startIndex, itemsToAdd);
      adapter.notifyItemRangeInserted(startIndex, itemsToAdd.size());
      if (NEED_CENTER_DECORATION) {
        adapter.invalidateItemDecorations();
      }
    }

    @Override
    public void onItemAdded (ListManager<Long> list, Long userId, int toIndex) {
      adapter.addItem(toIndex, new ListItem(ListItem.TYPE_CHAT_VERTICAL).setData(new TGFoundChat(tdlib, userId).setNoUnread()).setLongId(ChatId.fromUserId(userId)));
      if (NEED_CENTER_DECORATION) {
        adapter.invalidateItemDecorations();
      }
    }

    @Override
    public void onItemChanged (ListManager<Long> list, Long item, int index, int cause) {
      // Do nothing
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.user: {
        tdlib.ui().openPrivateProfile(this, ((VerticalChatView) v).getUserId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
        break;
      }
    }
  }

  @Override
  public boolean canSlideBackFrom(NavigationController navigationController, float originalX, float originalY) {
    float x = originalX - (Views.getLocationInWindow(getRecyclerView())[0] - Views.getLocationInWindow(navigationController.get())[0]);
    float y = originalY - (Views.getLocationInWindow(getRecyclerView())[1] - Views.getLocationInWindow(navigationController.get())[1]);
    if (x >= 0 && y >= 0 && x < getRecyclerView().getMeasuredWidth() && y < getRecyclerView().getMeasuredHeight()) {
      View view = getRecyclerView().findChildViewUnder(x, y);
      if (view instanceof RecyclerView) {
        LinearLayoutManager manager = (LinearLayoutManager) ((RecyclerView) view).getLayoutManager();
        if (manager.findFirstCompletelyVisibleItemPosition() != 0) {
          return false;
        }
      }
    }
    return super.canSlideBackFrom(navigationController, originalX, originalY);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);
    updateHeader(false);

    // FillingDecoration decoration = new FillingDecoration(recyclerView, this);
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_TEXT_VIEW, R.id.text_title, 0, getPoll().question, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    int optionId = 0;
    for (TdApi.PollOption option : getPoll().options) {
      if (option.voterCount == 0) {
        optionId++;
        continue;
      }
      items.add(new ListItem(ListItem.TYPE_TEXT_VIEW, R.id.text_subtitle, 0, option.text, false).setIntValue(optionId));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(newRecyclerItem(optionId));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setIntValue(optionId));
      optionId++;
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setInfo(ListItem item, int position, ListInfoView infoView) {
        int correctOptionId = isQuiz() ? ((TdApi.PollTypeQuiz) getPoll().type).correctOptionId : -1;
        TdApi.PollOption option = getPoll().options[item.getIntValue()];
        infoView.showInfo(Lang.formatString("%s — %d%%", null, Lang.pluralBold(isQuiz() ? (item.getIntValue() == correctOptionId ? R.string.xCorrectAnswers : R.string.xAnswers) : R.string.xVotes, option.voterCount), option.votePercentage));
      }

      @Override
      protected void setText(ListItem item, CustomTextView view, boolean isUpdate) {
        super.setText(item, view, isUpdate);

        switch (view.getId()) {
          case R.id.text_title: {
            view.setTextSize(17f);
            view.setPadding(Screen.dp(16f), Screen.dp(13f), Screen.dp(16f), Screen.dp(13f));
            view.setTextColorId(R.id.theme_color_text);
            ViewSupport.setThemedBackground(view, R.id.theme_color_filling, PollResultsController.this);
            break;
          }
          case R.id.text_subtitle: {
            view.setTextSize(15f);
            view.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), Screen.dp(6f));
            view.setTextColorId(R.id.theme_color_background_text);
            ViewSupport.setThemedBackground(view, ThemeColorId.NONE, PollResultsController.this);
            break;
          }
        }
      }

      @Override
      protected void setRecyclerViewData(ListItem item, RecyclerView recyclerView, boolean isInitialization) {
        ListCache listCache = (ListCache) item.getData();
        if (isInitialization) {
          recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
          if (NEED_CENTER_DECORATION) {
            recyclerView.addItemDecoration(new CenterDecoration() {
              @Override
              public int getItemCount() {
                return ((ListCache) ((ListItem) recyclerView.getTag()).getData()).voters.getCount();
              }
            });
            ((CustomRecyclerView) recyclerView).setMeasureListener((v, oldWidth, oldHeight, newWidth, newHeight) -> {
              if (oldWidth != newWidth && oldWidth != 0) {
                v.invalidateItemDecorations();
              }
            });
          }
          recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
              UserListManager listManager = ((ListCache) ((ListItem) recyclerView.getTag()).getData()).voters;
              int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
              if (lastVisiblePosition + 5 >= listManager.getCount()) {
                listManager.loadItems(false, null);
              }
            }
          });
        }
        if (recyclerView.getAdapter() != listCache.adapter) {
          recyclerView.setAdapter(listCache.adapter);
        }
      }
    };
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    // recyclerView.addItemDecoration(decoration);

    tdlib.listeners().addPollListener(getPoll().id, this);
  }

  private int findOption (int optionId) {
    List<ListItem> items = adapter.getItems();
    int index = 0;
    for (ListItem item : items) {
      if (item.getId() == R.id.text_subtitle && item.getIntValue() == optionId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private ListItem newRecyclerItem (int optionId) {
    return new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL).setIntValue(optionId).setData(new ListCache(this, getArgumentsStrict().chatId, getArgumentsStrict().messageId, optionId));
  }

  private int findInsertionIndex (int optionId) {
    List<ListItem> items = adapter.getItems();
    int index = 0;
    for (ListItem item : items) {
      if (item.getId() == R.id.text_subtitle && item.getIntValue() >= optionId) {
        return index;
      }
      index++;
    }
    return items.size();
  }

  @Override
  public void destroy() {
    super.destroy();
    tdlib.listeners().removePollListener(getPoll().id, this);
  }

  private static final int ITEM_COUNT_PER_OPTION = 5;

  @Override
  public void onUpdatePoll (TdApi.Poll updatedPoll) {
    runOnUiThread(() -> {
      if (!isDestroyed() && getPoll().id == updatedPoll.id) {
        getArgumentsStrict().poll = updatedPoll;

        if (updatedPoll.totalVoterCount == 0 || !TD.hasAnswer(updatedPoll)) {
          navigateBack();
          return;
        }

        updateHeader(true);

        List<ListItem> items = adapter.getItems();
        int optionId = 0;
        for (TdApi.PollOption option : updatedPoll.options) {
          int optionIndex = findOption(optionId);
          if (option.voterCount == 0) {
            if (optionIndex != -1) {
              adapter.removeRange(optionIndex, ITEM_COUNT_PER_OPTION);
            }
          } else {
            if (optionIndex == -1) {
              int atIndex = findInsertionIndex(optionId);
              items.addAll(atIndex, Arrays.asList(
                new ListItem(ListItem.TYPE_HEADER, R.id.text_subtitle, 0, option.text, false).setIntValue(optionId),
                new ListItem(ListItem.TYPE_SHADOW_TOP),
                newRecyclerItem(optionId),
                new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
                new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setIntValue(optionId)
              ));
              adapter.notifyItemRangeChanged(atIndex, ITEM_COUNT_PER_OPTION);
            }
          }
          optionId++;
        }

        int index = 0;
        for (ListItem item : items) {
          if (item.getViewType() == ListItem.TYPE_LIST_INFO_VIEW) {
            adapter.updateValuedSettingByPosition(index);
          }
          index++;
        }

      }
    });
  }
}
