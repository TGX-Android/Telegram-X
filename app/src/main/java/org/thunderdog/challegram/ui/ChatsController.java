/**
 * File created on 23/04/15 at 18:34
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.os.CancellationSignal;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.dialogs.ChatsAdapter;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.helper.LiveLocationHelper;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ContentFrameLayout;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.RecyclerViewProvider;
import org.thunderdog.challegram.navigation.SelectDelegate;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatFilter;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.MessageEditListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.telegram.TdlibContactManager;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.ChatsRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.JoinedUsersView;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class ChatsController extends TelegramViewController<ChatsController.Arguments> implements Menu,
  View.OnClickListener, View.OnLongClickListener, ChatsRecyclerView.LoadMoreCallback,
  ChatListener, ConnectionListener, MessageListener, MessageEditListener, NotificationSettingsListener,
  TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, TdlibCache.UserDataChangeListener, TdlibCache.SecretChatDataChangeListener,
  ChatListListener,
  RecyclerViewProvider,
  TGLegacyManager.EmojiLoadListener,
  ViewPagerController.ScrollToTopDelegate, BaseView.ActionListProvider,
  TdlibContactManager.Listener, FactorAnimator.Target,
  ForceTouchView.PreviewDelegate, LiveLocationHelper.Callback,
  BaseView.LongPressInterceptor, TdlibCache.UserStatusChangeListener,
  Settings.ChatListModeChangeListener, CounterChangeListener,
  TdlibSettingsManager.PreferenceChangeListener, SelectDelegate, MoreDelegate {

  private boolean progressVisible, initialLoadFinished;
  @Nullable
  private ChatFilter filter;

  public TdApi.Chat filter (TdApi.Chat chat) {
    return chat != null && (filter == null || filter.accept(chat)) && ChatPosition.findPosition(chat, chatList()) != null ? chat : null;
  }

  public ChatFilter getFilter () {
    return filter;
  }

  public boolean isFiltered () {
    return filter != null;
  }

  private FrameLayoutFix contentView;
  private @Nullable ProgressComponentView spinnerView;
  private @Nullable ChatsRecyclerView chatsView;
  private ChatsAdapter adapter;

  private Intent shareIntent;

  public ChatsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private MainController parentController;

  public ChatsController setParent (MainController c) {
    this.parentController = c;
    return this;
  }

  public MainController getParentController () {
    return parentController;
  }

  public interface PickerDelegate {
    boolean onChatPicked (TdApi.Chat chat, Runnable onDone);
    default Object getShareItem () { return null; }
    default void modifyChatOpenParams (TdlibUi.ChatOpenParameters params) { }
    default int getTitleStringRes () {
      return R.string.SelectChat;
    }
  }

  private PickerDelegate pickerDelegate;

  public boolean isPicker () {
    return pickerDelegate != null;
  }

  private TdApi.ChatList chatList;

  public @NonNull TdApi.ChatList chatList () {
    if (chatList == null) {
      chatList = ChatPosition.CHAT_LIST_MAIN;
    }
    return chatList;
  }

  private boolean needMessagesSearch;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    if (this.list != null)
      throw new IllegalStateException();
    if (args == null) {
      this.shareIntent = null;
      this.filter = null;
      this.pickerDelegate = null;
      this.chatList = ChatPosition.CHAT_LIST_MAIN;
      this.needMessagesSearch = false;
    } else {
      this.filter = args.filter;
      this.pickerDelegate = args.pickerDelegate;
      this.chatList = args.chatList != null ? args.chatList : ChatPosition.CHAT_LIST_MAIN;
      this.needMessagesSearch = args.needMessagesSearch;
    }
  }

  private static class ChatPinSeparatorDecoration extends RecyclerView.ItemDecoration {
    private final ChatsController context;
    private final Paint topShadowPaint, bottomShadowPaint;

    public ChatPinSeparatorDecoration (ChatsController context) {
      this.context = context;
      this.topShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] topShadowColors = ShadowView.topShadowColors();
      topShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleTopShadowHeight(), topShadowColors, null, Shader.TileMode.CLAMP));

      this.bottomShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] bottomShadowColors = ShadowView.bottomShadowColors();
      bottomShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleBottomShadowHeight(), bottomShadowColors, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void onDraw (Canvas c, RecyclerView parent, RecyclerView.State state) {
      final int childCount = parent.getChildCount();
      final int separatorLeft = Screen.dp(72f);
      final int separatorHeight = Math.max(1, Screen.dp(.5f, 3f));
      final int separatorColor = Theme.separatorColor();
      final float maxAlpha = Theme.getShadowDepth();
      final float lineFactor = Theme.getSeparatorReplacement();
      final float shadowFactor = 1f - lineFactor;

      boolean needLiveLocation = false;
      int liveLocationClipTop = 0, liveLocationRight = 0, liveLocationTop = 0, liveLocationBottom = 0;

      for (int i = 0; i < childCount; i++) {
        View view = parent.getChildAt(i);
        if (view instanceof ChatView) {
          final int offsetTop = ((ChatView) view).isDragging() || (context.animatorFlags & ANIMATOR_FLAG_DRAGGING) != 0 ? 0 : (int) view.getTranslationY();
          TGChat chat = ((ChatView) view).getChat();
          final int right = view.getMeasuredWidth();
          boolean needSeparator = true;
          if (chat != null) {
            int adapterPosition = parent.getChildAdapterPosition(view);
            if (adapterPosition != RecyclerView.NO_POSITION) {
              TGChat nextChat = context.adapter.getChatAt(adapterPosition + 1);
              boolean needSplit = nextChat != null && (chat.isArchive() != nextChat.isArchive() || chat.isPinnedOrSpecial() != nextChat.isPinnedOrSpecial());

              if (needSplit) {
                final int top = view.getBottom() + offsetTop;
                View nextView = parent.getLayoutManager().findViewByPosition(adapterPosition + 1);
                final int bottom = (nextView != null ? nextView.getTop() : parent.getLayoutManager().getDecoratedBottom(view)) + offsetTop;

                c.drawRect(0, top, right, bottom, Paints.fillingPaint(Theme.backgroundColor()));

                if (shadowFactor != 0f) {
                  final int alpha = (int) (255f * maxAlpha * shadowFactor);

                  topShadowPaint.setAlpha(alpha);
                  bottomShadowPaint.setAlpha(alpha);

                  c.save();
                  int shadowTopTop = bottom - ShadowView.simpleTopShadowHeight();
                  c.translate(0, shadowTopTop);
                  c.drawRect(0, 0, right, ShadowView.simpleTopShadowHeight(), topShadowPaint);
                  c.translate(0, top - shadowTopTop);
                  c.drawRect(0, 0, right, ShadowView.simpleBottomShadowHeight(), bottomShadowPaint);
                  c.restore();
                }

                if (lineFactor != 0f) {
                  final int color = ColorUtils.alphaColor(lineFactor, Theme.separatorColor());
                  c.drawRect(0, top, right, top + separatorHeight, Paints.fillingPaint(color));
                  c.drawRect(0, bottom - separatorHeight, right, bottom, Paints.fillingPaint(color));
                }

                needSeparator = false;
              }

              if (context.liveLocationHelper != null && (adapterPosition == context.getLiveLocationPosition())) {
                int decoratedTop = parent.getLayoutManager().getDecoratedTop(view) + offsetTop;
                int top = view.getTop() + offsetTop;
                int fillingTop = decoratedTop;
                if (adapterPosition > 0) {
                  View prevView = parent.getLayoutManager().findViewByPosition(adapterPosition - 1);
                  if (prevView != null) {
                    fillingTop = Math.max(fillingTop, prevView.getBottom() + (int) prevView.getTranslationY());
                  }
                }
                if (decoratedTop < top && top > 0) {
                  c.drawRect(0, fillingTop, right, top, Paints.fillingPaint(Theme.backgroundColor()));
                  needLiveLocation = true;
                  liveLocationClipTop = fillingTop;
                  liveLocationRight = right;
                  liveLocationBottom = top;
                  liveLocationTop = decoratedTop;

                  if (adapterPosition == 0) {
                    if (shadowFactor != 0f) {
                      final int alpha = (int) (255f * maxAlpha * shadowFactor);
                      topShadowPaint.setAlpha(alpha);
                      c.save();
                      c.translate(0, top);
                      c.drawRect(0, 0, right, ShadowView.simpleTopShadowHeight(), topShadowPaint);
                      c.restore();
                    }

                    if (lineFactor != 0f) {
                      final int color = ColorUtils.alphaColor(lineFactor, Theme.separatorColor());
                      c.drawRect(0, top - separatorHeight, right, top, Paints.fillingPaint(color));
                    }
                  }
                }
              }
            }
          }
          if (needSeparator) {
            int separatorTop = view.getBottom() + offsetTop;
            if (Lang.rtl()) {
              c.drawRect(0, separatorTop, right - separatorLeft, separatorTop + separatorHeight, Paints.fillingPaint(separatorColor));
            } else {
              c.drawRect(separatorLeft, separatorTop, right, separatorTop + separatorHeight, Paints.fillingPaint(separatorColor));
            }
          }
        }
      }

      if (needLiveLocation) {
        c.save();
        c.clipRect(0, liveLocationClipTop, liveLocationRight, liveLocationBottom);
        context.liveLocationHelper.draw(c, liveLocationTop);
        c.restore();
      }
    }

    @Override
    public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION) {
        outRect.bottom = outRect.top = 0;
        return;
      }

      TGChat current = context.adapter.getChatAt(position);
      if (current == null) {
        if (context.adapter.getItemCount() > 0 && context.adapter.hasArchive() && context.hideArchive && position == context.adapter.getItemCount() - 1) {
          outRect.bottom = Math.max(0, parent.getMeasuredHeight() - context.calculateTotalScrollContentHeight());
        }
        return;
      }

      TGChat next = context.adapter.getChatAt(position + 1);
      if (next != null && (current.isArchive() != next.isArchive() || current.isPinnedOrSpecial() != next.isPinnedOrSpecial())) {
        outRect.bottom = Screen.dp(12f);
      } else {
        outRect.bottom = Screen.separatorSize();
      }

      outRect.top = 0;

      if (context.liveLocationHelper != null && context.liveLocationHelper.isVisible()) {
        int liveLocationPosition = context.getLiveLocationPosition();
        if (position == liveLocationPosition) {
          outRect.top = LiveLocationHelper.height();
        } else if (position == liveLocationPosition - 1) {
          outRect.bottom = Screen.dp(1f);
        }
      }

      if (context.archiveCollapsed && outRect.top == 0 && current.isArchive()) {
        outRect.top = -ChatView.getViewHeight(current.getListMode());
        if (context.liveLocationHelper != null && context.liveLocationHelper.isVisible()) {
          outRect.top -= Screen.dp(1f);
        } else {
          outRect.top -= Screen.dp(12f);
        }
      }
    }
  }

  private long initializationTime;
  private boolean listInitalized;

  private ItemTouchHelper touchHelper;
  private LiveLocationHelper liveLocationHelper;
  private TdlibChatListSlice list;

  public TdlibChatListSlice list () {
    if (list == null) {
      this.list = new TdlibChatListSlice(tdlib, chatList(), filter);
    }
    return list;
  }

  @Override
  protected View onCreateView (Context context) {
    list();
    updateNetworkStatus(tdlib.connectionState());

    contentView = new ContentFrameLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    chatsView = (ChatsRecyclerView) Views.inflate(context(), R.layout.recycler_chats, contentView);
    chatsView.setMeasureListener((v, oldWidth, oldHeight, newWidth, newHeight) -> {
      if (newHeight != oldHeight && adapter.hasArchive() && hideArchive && adapter.getItemCount() > 0) {
        adapter.notifyItemChanged(adapter.getItemCount() - 1);
      }
    });
    chatsView.setItemAnimator(null);
    if (isInForceTouchMode()) {
      chatsView.setVerticalScrollBarEnabled(false);
    }
    chatsView.setHasFixedSize(true);
    chatsView.addItemDecoration(new ChatPinSeparatorDecoration(this));
    ViewSupport.setThemedBackground(chatsView, R.id.theme_color_filling, this);

    touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == ChatsAdapter.VIEW_TYPE_CHAT) {
          TGChat chat = ((ChatView) viewHolder.itemView).getChat();
          if (chat != null && chat.isPinned() && adapter.canDragPinnedChats() && filter == null) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            return makeMovementFlags(dragFlags, 0);
          }
        }
        return 0;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return false;
      }

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
        if (dragTooltip != null) {
          dragTooltip.reposition();
        }
      }

      private int dragFrom = -1;
      private int dragTo = -1;

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        TGChat fromChat = adapter.getChatAt(fromPosition);
        TGChat toChat = adapter.getChatAt(toPosition);

        if (fromChat == null || toChat == null || !fromChat.isPinned() || !toChat.isPinned()) {
          return false;
        }

        if (dragFrom == -1) {
          dragFrom = fromPosition;
        }
        dragTo = toPosition;
        adapter.movePinnedChat(fromPosition, toPosition);
        if (dragTooltip != null) {
          dragTooltip.hideNow();
        }

        return false;
      }

      @Override
      public void onSelectedChanged (RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (viewHolder != null && viewHolder.getItemViewType() == ChatsAdapter.VIEW_TYPE_CHAT) {
          ((ChatView) viewHolder.itemView).setIsDragging(true);
          setAnimatorFlag(ANIMATOR_FLAG_DRAGGING, true);
        }
      }

      @Override
      public void clearView (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
          adapter.savePinnedChats();
        }
        dragFrom = dragTo = -1;
        chatsView.setItemAnimator(null);
        setAnimatorFlag(ANIMATOR_FLAG_DRAGGING, false);
        ((ChatView) viewHolder.itemView).setIsDragging(false);
        if (dragTooltip != null) {
          dragTooltip.hideDelayed();
        }
      }

      @Override
      public void onSwiped (@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
    });
    touchHelper.attachToRecyclerView(chatsView);

    adapter = chatsView.initWithController(this, this);
    chatsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (filter != null)
      chatsView.setTotalRes(filter.getTotalStringRes());
    contentView.addView(chatsView);

    Views.setScrollBarPosition(chatsView);

    checkDisplayProgress();

    if (!isBaseController()) {
      generateChatSearchView(contentView);
    }

    updateNetworkStatus(tdlib.connectionState());

    tdlib.listeners().subscribeForAnyUpdates(this);
    tdlib.cache().subscribeToAnyUpdates(this);

    Settings.instance().addChatListModeListener(this);
    TGLegacyManager.instance().addEmojiListener(this);

    list.initializeList(this, this::displayChats, chatsView.getInitialLoadCount(), () ->
      runOnUiThreadOptional(() -> {
        this.listInitalized = true;
        checkListState();
        executeScheduledAnimation();
      })
    );

    if (isBaseController()) {
      tdlib.contacts().addListener(this);
      if (filter == null && chatList().getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
        chatsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
          @Override
          public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
            if (hideArchive && archiveCollapsed && chatScrollState == RecyclerView.SCROLL_STATE_IDLE && newState != RecyclerView.SCROLL_STATE_IDLE) {
              LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
              int firstVisiblePosition = manager.findFirstVisibleItemPosition();
              if (firstVisiblePosition == 1) {
                View view = manager.findViewByPosition(1);
                if (view != null && manager.getDecoratedTop(view) == 0) {
                  setArchiveCollapsed(false);
                }
              }
            }
            chatScrollState = newState;
            if (hideArchive) {
              if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int firstVisiblePosition = manager.findFirstVisibleItemPosition();
                if (firstVisiblePosition == 0) {
                  setArchiveCollapsed(false);
                  View view = manager.findViewByPosition(firstVisiblePosition);
                  int top = view != null ? -manager.getDecoratedTop(view) : 0;
                  int itemHeight = ChatView.getViewHeight(Settings.instance().getChatListMode());
                  if (top < itemHeight / 2) {
                    chatsView.smoothScrollBy(0, -top);
                  } else {
                    onScrollToTopRequested();
                  }
                } else if (firstVisiblePosition == 1) {
                  View view = manager.findViewByPosition(firstVisiblePosition);
                  setArchiveCollapsed(view == null || manager.getDecoratedTop(view) < 0);
                } else {
                  setArchiveCollapsed(true);
                }
              }
            }
          }

          @Override
          public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (hideArchive && !archiveCollapsed && chatScrollState == RecyclerView.SCROLL_STATE_SETTLING && dy > 0 && ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() > 0) {
              setArchiveCollapsed(true);
            }
          }
        });
        hideArchive = archiveCollapsed = tdlib.settings().needHideArchive();
        archiveList = tdlib.chatList(ChatPosition.CHAT_LIST_ARCHIVE);
        archiveListListener = new ChatListListener() {
          @Override
          public void onChatListChanged (TdlibChatList chatList, @ChangeFlags int changeFlags) {
            if (BitwiseUtils.setFlag(changeFlags, ChangeFlags.ITEM_METADATA_CHANGED, false) != 0) {
              runOnUiThreadOptional(() -> {
                adapter.setNeedArchive(chatList.totalCount() > 0);
                adapter.updateArchive(ChatsAdapter.ARCHIVE_UPDATE_ALL);
              });
            }
          }

          @Override
          public void onChatListItemChanged (TdlibChatList chatList, TdApi.Chat chat, @ItemChangeType int changeType) {
            int reason;
            switch (changeType) {
              case ItemChangeType.TITLE:
              case ItemChangeType.UNREAD_AVAILABILITY_CHANGED:
                reason = ChatsAdapter.ARCHIVE_UPDATE_ALL;
                break;
              case ItemChangeType.READ_INBOX:
                reason = ChatsAdapter.ARCHIVE_UPDATE_COUNTER;
                break;
              case ItemChangeType.LAST_MESSAGE:
              case ItemChangeType.DRAFT:
                reason = ChatsAdapter.ARCHIVE_UPDATE_MESSAGE;
                break;
              default:
                return;
            }
            runOnUiThreadOptional(() ->
              adapter.updateArchive(reason)
            );
          }
        };
        adapter.setNeedArchive(archiveList.totalCount() > 0);
        archiveList.subscribeToUpdates(archiveListListener);
        archiveList.loadAtLeast(null, 3, null);
        tdlib.settings().addUserPreferenceChangeListener(this);
      }
    }

    if (filter == null) {
      liveLocationHelper = new LiveLocationHelper(this.context, tdlib, 0, 0, chatsView, true, this);
      liveLocationHelper.init();
    }

    initializationTime = SystemClock.uptimeMillis();

    return contentView;
  }

  @TdlibThread
  private void displayChats (List<TdApi.Chat> entries) {
    int initialLoadCount = chatsView.getInitialLoadCount();
    List<TGChat> parsedChats = new ArrayList<>(entries.size());
    for (TdApi.Chat chat : entries) {
      parsedChats.add(new TGChat(this, chatList(), chat, initialLoadCount-- >= 0));
    }
    runOnUiThreadOptional(() -> {
      initialLoadFinished = true;
      adapter.addMore(parsedChats.toArray(new TGChat[0]));
    });
  }

  private boolean hideArchive, archiveCollapsed;
  private TdlibChatList archiveList;
  private ChatListListener archiveListListener;

  private int chatScrollState = RecyclerView.SCROLL_STATE_IDLE;

  public boolean isPullingArchive () {
    if (!hideArchive || adapter == null || !adapter.hasArchive())
      return false;
    LinearLayoutManager manager = (LinearLayoutManager) chatsView.getLayoutManager();
    return manager.findFirstVisibleItemPosition() == 0;
  }

  private void setArchiveCollapsed (boolean collapsed) {
    if (this.archiveCollapsed != collapsed) {
      if (chatsView == null) {
        this.archiveCollapsed = collapsed;
      } else {
        this.archiveCollapsed = collapsed;
        UI.post(() -> {
          if (!isDestroyed()) {
            chatsView.saveScrollPosition();
            adapter.notifyItemChanged(0);
            chatsView.restoreScrollPosition();
          }
        });
      }
    }
  }

  private void setHideArchive (boolean hide) {
    if (this.archiveList != null && this.hideArchive != hide) {
      this.hideArchive = hide;
      if (chatsView == null) {
        return;
      }
      adapter.notifyItemChanged(adapter.getItemCount() - 1);
      if (hide) {
        onHideArchiveRequested();
        // TODO scroll by so it becomes invisible
        // if it is not visible, just invalidate decoration
      }
    }
  }

  private void onHideArchiveRequested () {
    LinearLayoutManager manager = (LinearLayoutManager) chatsView.getLayoutManager();
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == 0) {
      View view = manager.findViewByPosition(0);
      if (view != null) {
        int top = Math.max(0, manager.getDecoratedTop(view));
        int bottom = Math.max(0, manager.getDecoratedBottom(view));
        chatsView.smoothScrollBy(0, (bottom - top));
      }
      if (parentController != null) {
        parentController.showComposeWrap(this);
      }
    } else {
      setArchiveCollapsed(true);
    }
  }

  @Override
  public void onChatListModeChanged (int newChatListMode) {
    if (adapter != null) {
      adapter.checkChatListMode();
    }
  }

  public boolean isLaunching () {
    return initializationTime == 0 || SystemClock.uptimeMillis() - initializationTime <= 1000l;
  }

  public boolean needLiveLocationClick () {
    return chatsView != null && liveLocationHelper != null && liveLocationHelper.isVisible();
  }

  public void onLiveLocationClick (float x, float y) {
    if (needLiveLocationClick() && chatsView != null) {
      RecyclerView.LayoutManager manager = chatsView.getLayoutManager();
      View view = manager.findViewByPosition(getLiveLocationPosition());
      int top = view != null ? view.getTop() : 0;
      if (top > 0) {
        int decoratedTop = manager.getDecoratedTop(view);
        if (view instanceof ChatView && decoratedTop < top && y < top && y >= decoratedTop) {
          y -= decoratedTop;
          chatsView.stopScroll();
          liveLocationHelper.onClickAt(x, y);
        }
      }
    }
  }

  @Override
  public boolean onBeforeVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate) {
    if (chatsView == null || !(parentController != null ? parentController.isFocused() : isFocused()) || adapter.getChats().isEmpty()) {
      return false;
    }

    LinearLayoutManager manager = (LinearLayoutManager) chatsView.getLayoutManager();
    int i = manager.findFirstVisibleItemPosition();
    if (i != 0) {
      return false;
    }

    if (willAnimate) {
      setAnimatorFlag(ANIMATOR_FLAG_LOCATION, true);
      return true;
    }

    return false;
  }

  @Override
  public void onAfterVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate) {
    if (chatsView != null && !adapter.getChats().isEmpty()) {
      int position = getLiveLocationPosition();
      adapter.notifyItemChanged(position);
      if (position > 0) {
        adapter.notifyItemChanged(position - 1);
      }
      if (adapter.hasArchive() && hideArchive) {
        adapter.notifyItemChanged(adapter.getItemCount() - 1);
      }
    }
  }

  public int getLiveLocationPosition () {
    return adapter.hasArchive() ? 1 : 0;
  }

  @Override
  public void onApplyVisibility (LiveLocationHelper helper, boolean isVisible, float visibilityFactor, boolean finished) {
    if (finished) {
      setAnimatorFlag(ANIMATOR_FLAG_LOCATION, false);
    }
  }

  private static final int ANIMATOR_FLAG_DRAGGING = 1;
  private static final int ANIMATOR_FLAG_LOCATION = 1 << 1;

  private int animatorFlags;

  private CustomItemAnimator chatsAnimator;

  private void setAnimatorFlag (int flag, boolean isEnabled) {
    if (isDestroyed() || chatsView == null) {
      return;
    }
    int flags = BitwiseUtils.setFlag(animatorFlags, flag, isEnabled);
    if (this.animatorFlags != flags) {
      boolean hadAnimator = this.animatorFlags != 0;
      boolean hasAnimator = flags != 0;
      this.animatorFlags = flags;
      if (hadAnimator != hasAnimator) {
        if (hasAnimator) {
          if (chatsAnimator == null) {
            chatsAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l) {
              @Override
              protected void setAlpha (View view, float value) {
                if ((animatorFlags & ANIMATOR_FLAG_LOCATION) == 0) {
                  super.setAlpha(view, value);
                }
              }
            };
          }
          chatsView.setItemAnimator(chatsAnimator);
        } else {
          chatsView.setItemAnimator(null);
        }
      }
    }
  }

  @Override
  public RecyclerView provideRecyclerView () {
    return chatsView;
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    return super.onKeyboardStateChanged(visible);/*if (composeWrap != null) {
      composeWrap.setVisibility(visible ? View.GONE : View.VISIBLE);
    }*/
  }

  private String getHeaderText () {
    return Lang.getString(getHeaderResource());
  }

  private int getHeaderResource () {
    if (pickerDelegate != null) {
      return pickerDelegate.getTitleStringRes();
    }
    if (filter != null) {
      return R.string.Chats;
    }
    switch (chatList().getConstructor()) {
      case TdApi.ChatListArchive.CONSTRUCTOR:
        return R.string.ArchiveTitle;
      case TdApi.ChatListMain.CONSTRUCTOR:
      default:
        return R.string.general_Messages;
    }
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  public void onScrollToTopRequested () {
    if (adapter != null && adapter.hasChats() && chatsView != null) {
      chatsView.stopScroll();

      LinearLayoutManager manager = (LinearLayoutManager) chatsView.getLayoutManager();
      int pinnedItemCount = adapter.getHeaderChatCount(true, null);
      int itemHeight = ChatView.getViewHeight(Settings.instance().getChatListMode());

      int firstVisiblePosition = manager.findFirstVisibleItemPosition();
      if (firstVisiblePosition == RecyclerView.NO_POSITION) {
        return;
      }

      int totalScrollBy = itemHeight * firstVisiblePosition;
      int separatorItemCount = firstVisiblePosition;
      int chatsCount = adapter.getItemCount() - 1;

      if (adapter.hasArchive() && chatsCount > 1 && firstVisiblePosition > 0) {
        separatorItemCount--;
        totalScrollBy += (liveLocationHelper != null && liveLocationHelper.isVisible() ? LiveLocationHelper.height() + Screen.dp(1f) : Screen.dp(12f));
        pinnedItemCount--;
        chatsCount--;
      }

      if (!adapter.hasArchive() && liveLocationHelper != null && liveLocationHelper.isVisible() && firstVisiblePosition >= getLiveLocationPosition()) {
        totalScrollBy += LiveLocationHelper.height();
      }

      if (pinnedItemCount != 0 && chatsCount > pinnedItemCount && firstVisiblePosition > pinnedItemCount) {
        separatorItemCount--;
        totalScrollBy += Screen.dp(12f);
      }

      totalScrollBy += separatorItemCount * Screen.separatorSize();

      if (adapter.hasArchive() && hideArchive) {
        totalScrollBy -= itemHeight + (liveLocationHelper != null && liveLocationHelper.isVisible() ? Screen.dp(1f) : Screen.dp(12f));
      }

      View firstView = manager.findViewByPosition(firstVisiblePosition);
      if (firstView != null) {
        totalScrollBy -= manager.getDecoratedTop(firstView); // firstView.getTop();
      }
      chatsView.smoothScrollBy(0, -totalScrollBy);
    }
  }

  protected int calculateTotalScrollContentHeight () {
    int pinnedItemCount = adapter.getHeaderChatCount(true, null);
    int itemHeight = ChatView.getViewHeight(Settings.instance().getChatListMode());

    int chatsCount = adapter.getItemCount() - 1;

    int totalScrollBy = itemHeight * chatsCount;
    int separatorItemCount = chatsCount;

    if (adapter.hasArchive() && chatsCount > 1) {
      separatorItemCount--;
      totalScrollBy += (liveLocationHelper != null && liveLocationHelper.isVisible() ? LiveLocationHelper.height() + Screen.dp(1f) : Screen.dp(12f));
      pinnedItemCount--;
      chatsCount--;
    }

    if (!adapter.hasArchive() && liveLocationHelper != null && liveLocationHelper.isVisible()) {
      totalScrollBy += LiveLocationHelper.height();
    }

    if (pinnedItemCount != 0 && chatsCount > pinnedItemCount) {
      separatorItemCount--;
      totalScrollBy += Screen.dp(12f);
    }

    totalScrollBy += separatorItemCount * Screen.separatorSize();

    if (adapter.hasArchive() && hideArchive) {
      totalScrollBy -= itemHeight + (liveLocationHelper != null && liveLocationHelper.isVisible() ? Screen.dp(1f) : Screen.dp(12f));
    }

    totalScrollBy += SettingHolder.measureHeightForType(ListItem.TYPE_LIST_INFO_VIEW);

    return totalScrollBy;
  }

  public void updateNetworkStatus (int state) {
    if (state == Tdlib.STATE_CONNECTED) {
      shareIntentIfReady();
      setName(getHeaderText());
    } else {
      setName(TdlibUi.stringForConnectionState(state));
    }
  }

  @Override
  public int getId () {
    return R.id.controller_chats;
  }

  @Override
  public int getBackButton () {
    return isBaseController() ? BackHeaderButton.TYPE_MENU : BackHeaderButton.TYPE_BACK;
  }

  private boolean isBaseController () {
    return pickerDelegate == null && ((getArguments() != null && getArguments().isBaseController) || chatList().getConstructor() == TdApi.ChatListMain.CONSTRUCTOR); // FIXME replace with indexInStack() == 0
  }

  @Override
  protected int getMenuId () {
    return isBaseController() ? R.id.menu_passcode : R.id.menu_search;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return chatsView;
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (isBaseController()) {
      if (headerView != null) {
        headerView.updateLockButton(getMenuId());
      }
    }
    updateNetworkStatus(tdlib.connectionState());
    updateSelectButtons();
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_passcode: {
        if (isBaseController()) {
          header.addLockButton(menu);
        }
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_search: {
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, getSearchHeaderIconColorId(), getSearchBackButtonResource());
        break;
      }
      case R.id.menu_chatBulkActions: {
        // Pin / unpin
        // Mute / unmute
        // Delete
        // More

        int totalButtonsCount = 0;
        boolean value;
        int mode;

        int iconColorId = getSelectHeaderIconColorId();

        mode = canPinUnpinSelectedChats();
        header.addButton(menu, R.id.menu_btn_pinUnpin, mode == ACTION_MODE_ALL_ENABLED ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24, iconColorId, this, Screen.dp(52f))
                .setVisibility((value = shouldShowPin(mode)) ? View.VISIBLE : View.GONE);
        if (value) totalButtonsCount++;

        mode = canMuteUnmuteSelectedChats();
        header.addButton(menu, R.id.menu_btn_muteUnmute, mode == ACTION_MODE_ALL_ENABLED ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24, iconColorId, this, Screen.dp(52f))
                .setVisibility((value = mode != ACTION_MODE_NONE) ? View.VISIBLE : View.GONE);
        if (value) totalButtonsCount++;

        mode = canDeleteSelectedChats();
        header.addDeleteButton(menu, this, iconColorId)
                .setVisibility((value = mode != ACTION_MODE_NONE && mode != ACTION_MODE_MIXED) ? View.VISIBLE : View.GONE);
        if (value) totalButtonsCount++;

        header.addMoreButton(menu, this, getSelectHeaderIconColorId());

        break;
      }
    }
  }

  public final void updateSelectButtons () {
    NavigationController navigation = getParentOrSelf().navigationController();
    HeaderView headerView = navigation != null ? navigation.getHeaderView() : null;
    if (headerView != null) {
      int totalButtonsCount = 0;
      boolean value;
      int mode;
      mode = canPinUnpinSelectedChats();
      headerView.updateButton(R.id.menu_chatBulkActions, R.id.menu_btn_pinUnpin, (value = shouldShowPin(mode)) ? View.VISIBLE : View.GONE, mode == ACTION_MODE_ALL_ENABLED ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
      if (value) totalButtonsCount++;
      mode = canMuteUnmuteSelectedChats();
      headerView.updateButton(R.id.menu_chatBulkActions, R.id.menu_btn_muteUnmute, (value = mode != ACTION_MODE_NONE) ? View.VISIBLE : View.GONE, mode == ACTION_MODE_ALL_ENABLED ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
      if (value) totalButtonsCount++;
      mode = canDeleteSelectedChats();
      headerView.updateButton(R.id.menu_chatBulkActions, R.id.menu_btn_delete, (value = mode != ACTION_MODE_NONE && mode != ACTION_MODE_MIXED) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
    }
  }

  private static final int ACTION_MODE_NONE = 0;
  private static final int ACTION_MODE_ALL_ENABLED = 1;
  private static final int ACTION_MODE_ALL_DISABLED = 2;
  private static final int ACTION_MODE_MIXED = 3;

  private int getActionMode (Filter<TdApi.Chat> filter, @Nullable Filter<TdApi.Chat> ignoreFilter) {
    if (selectedChats == null || selectedChats.size() == 0)
      return ACTION_MODE_NONE;
    boolean baseValue = false;
    int count = 0;
    for (int i = 0; i < selectedChats.size(); i++) {
      TdApi.Chat chat = tdlib.chatStrict(selectedChats.keyAt(i));
      if (ignoreFilter != null && !ignoreFilter.accept(chat))
        continue;
      boolean value = filter.accept(chat);
      if (count == 0) {
        baseValue = value;
      } else if (value != baseValue) {
        return ACTION_MODE_MIXED;
      }
      count++;
    }
    return count == 0 ? ACTION_MODE_NONE : baseValue ? ACTION_MODE_ALL_ENABLED : ACTION_MODE_ALL_DISABLED;
  }

  private boolean shouldShowPin (int mode) {
    return mode != ACTION_MODE_NONE && mode != ACTION_MODE_MIXED;
  }

  private int canDeleteSelectedChats () {
    return getActionMode(chat -> tdlib.isSelfChat(chat.id), tdlib::chatAvailable);
  }

  private int canPinUnpinSelectedChats () {
    return getActionMode(chat -> ChatPosition.isPinned(chat, chatList), null);
  }

  private int canMuteUnmuteSelectedChats () {
    return getActionMode(tdlib::chatNotificationsEnabled, chat -> !tdlib.isSelfChat(chat.id));
  }

  @Override
  protected int getSelectMenuId () {
    return R.id.menu_chatBulkActions;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        openSearchMode();
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }

      case R.id.menu_btn_more: {
        // Archive / unarchive
        // Mark as Read / Unread
        // Report
        // Clear History
        // Block user
        // Clear from cache?

        int size = 2;
        IntList ids = new IntList(size);
        StringList strings = new StringList(size);
        IntList icons = new IntList(size);

        int canArchive = 0, canUnarchive = 0;
        int canMarkAsRead = 0, canMarkAsUnread = 0;
        int canReportSpam = 0;
        int canBlock = 0, canUnblock = 0;
        int canClearHistory = 0;
        for (int i = 0; i < selectedChats.size(); i++) {
          TdApi.Chat chat = selectedChats.valueAt(i);
          if (tdlib.canArchiveChat(chatList(), chat)) {
            if (ChatPosition.isArchived(chat)) {
              canUnarchive++;
            } else {
              canArchive++;
            }
          }
          if (!tdlib.isSelfChat(chat.id) && tdlib.canClearHistory(chat)) {
            canClearHistory++;
          }
          if (tdlib.canMarkAsRead(chat)) {
            canMarkAsRead++;
          } else {
            canMarkAsUnread++;
          }
          if (tdlib.canReportChatSpam(chat))
            canReportSpam++;
          if (tdlib.chatBlocked(chat)) {
            canUnblock++;
          } else {
            canBlock++;
          }
        }

        if (selectedChats.size() < tdlib.getCounter(chatList()).totalChatCount) {
          ids.append(R.id.more_btn_selectAll);
          strings.append(R.string.SelectMore);
          icons.append(R.drawable.baseline_playlist_add_check_24);
        }

        if (canArchive + canUnarchive > 0) {
          ids.append(R.id.more_btn_archiveUnarchive);
          strings.append(canUnarchive > 0 ? R.string.Unarchive : R.string.Archive);
          icons.append(canUnarchive > 0 ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
        }

        if (canMarkAsRead > 0) {
          ids.append(R.id.more_btn_markAsRead);
          strings.append(R.string.MarkAsRead);
          icons.append(Config.ICON_MARK_AS_READ);
        } else if (canMarkAsUnread > 0) {
          ids.append(R.id.more_btn_markAsUnread);
          strings.append(R.string.MarkAsUnread);
          icons.append(Config.ICON_MARK_AS_UNREAD);
        }

        if (canReportSpam == selectedChats.size()) {
          ids.append(R.id.more_btn_report);
          strings.append(R.string.Report);
          icons.append(R.drawable.baseline_report_24);
        }

        if (canUnblock > 0) {
          ids.append(R.id.more_btn_unblock);
          strings.append(R.string.Unblock);
          icons.append(R.drawable.baseline_block_24);
        } else if (canBlock == selectedChats.size()) {
          ids.append(R.id.more_btn_block);
          strings.append(R.string.BlockContact);
          icons.append(R.drawable.baseline_block_24);
        }

        if (canClearHistory == selectedChats.size()) {
          ids.append(R.id.more_btn_clearHistory);
          strings.append(R.string.ClearHistory);
          icons.append(R.drawable.baseline_delete_24);
        }

        ids.append(R.id.more_btn_clearCache);
        strings.append(R.string.DeleteChatCache);
        icons.append(R.drawable.templarian_baseline_broom_24);

        getParentOrSelf().showMore(ids.get(), strings.get(), icons.get());
        break;
      }

      case R.id.menu_btn_pinUnpin: {
        int mode = canPinUnpinSelectedChats();
        int cloudPinCount = 0, secretPinCount = 0;
        long lastChatId = 0;
        if (mode != ACTION_MODE_NONE) {
          for (int i = 0; i < selectedChats.size(); i++) {
            if (mode == ACTION_MODE_MIXED && ChatPosition.isPinned(selectedChats.valueAt(i), chatList))
              continue;
            lastChatId = selectedChats.keyAt(i);
            if (ChatId.isSecret(lastChatId))
              secretPinCount++;
            else
              cloudPinCount++;
          }
        }
        if ((cloudPinCount + secretPinCount) == 1 && tdlib.chatPinned(chatList, lastChatId)) {
          tdlib.ui().processChatAction(this, chatList(), selectedChats.keyAt(0), null, R.id.btn_pinUnpinChat, this::onSelectionActionComplete);
          return;
        }
        if (cloudPinCount > 0 || secretPinCount > 0) {
          boolean isUnpin = mode == ACTION_MODE_ALL_ENABLED;
          int maxPinnedCount = chatList().getConstructor() == TdApi.ChatListMain.CONSTRUCTOR ? tdlib.pinnedChatsMaxCount() : tdlib.pinnedArchivedChatsMaxCount();
          int pinnedCloudCount = adapter.getPinnedChatCount(false), pinnedSecretCount = adapter.getPinnedChatCount(true);
          if (!isUnpin && (pinnedCloudCount + cloudPinCount > maxPinnedCount || pinnedSecretCount + secretPinCount > maxPinnedCount)) {
            CharSequence message = chatList().getConstructor() == TdApi.ChatListMain.CONSTRUCTOR ? Lang.pluralBold(R.string.PinTooMuchWarn, maxPinnedCount) : Lang.plural(R.string.ErrorPinnedChatsLimit, maxPinnedCount);
            context.tooltipManager().builder(view).controller(getParentOrSelf()).icon(R.drawable.baseline_error_24).show(tdlib, message);
            return;
          }
          showOptions((secretPinCount + cloudPinCount) == 1 ? tdlib.chatTitle(lastChatId) : Lang.pluralBold(isUnpin ? R.string.UnpinXChats : R.string.PinXChats, secretPinCount + cloudPinCount), new int[] {
            R.id.btn_pinUnpinChat,
            R.id.btn_cancel
          }, new String[] {
            Lang.getString(isUnpin ? R.string.UnpinFromTop : R.string.PinToTop),
            Lang.getString(R.string.Cancel)
          }, null, new int[] {isUnpin ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
            if (optionId == R.id.btn_pinUnpinChat) {
              if (isUnpin) {
                int remainingCount = selectedChats.size();
                for (int i = 0; i < selectedChats.size(); i++) {
                  if (!ChatPosition.isPinned(selectedChats.valueAt(i), chatList)) {
                    remainingCount--;
                  }
                }
                AtomicInteger remaining = new AtomicInteger(remainingCount);
                Client.ResultHandler handler = object -> {
                  switch (object.getConstructor()) {
                    case TdApi.Ok.CONSTRUCTOR:
                      if (remaining.decrementAndGet() == 0) {
                        tdlib.ui().post(this::onSelectionActionComplete);
                      }
                      break;
                    case TdApi.Error.CONSTRUCTOR:
                      UI.showError(object);
                      break;
                  }
                };
                for (int i = 0; i < selectedChats.size(); i++) {
                  long chatId = selectedChats.keyAt(i);
                  if (ChatPosition.isPinned(selectedChats.valueAt(i), chatList)) {
                    tdlib.client().send(new TdApi.ToggleChatIsPinned(chatList, chatId, false), handler);
                  }
                }
              } else {
                List<Long> pinnedChats = tdlib.getPinnedChats(chatList);
                TdApi.Chat[] chats = ArrayUtils.asArray(selectedChats, new TdApi.Chat[selectedChats.size()]);
                Td.sort(chats, chatList);
                for (TdApi.Chat chat : chats) {
                  if (!ChatPosition.isPinned(chat, chatList)) {
                    pinnedChats.add(chat.id);
                  }
                }
                performSelectAction(new TdApi.SetPinnedChats(chatList, ArrayUtils.asArray(pinnedChats)));
              }
            }
            return true;
          });
        }
        break;
      }
      case R.id.menu_btn_muteUnmute: {
        /*if (getSelectedChatCount() == 1) {
          tdlib.ui().processChatAction(this, selectedChats.keyAt(0), R.id.btn_notifications, this::onSelectionActionComplete);
          return;
        }*/

        int mode = canMuteUnmuteSelectedChats();
        switch (mode) {
          case ACTION_MODE_ALL_ENABLED: {
            // Mute all

            int muteCount = 0;
            boolean hasBlocked = false;

            SparseIntArray scopeCounters = new SparseIntArray(3);
            for (int i = 0; i < selectedChats.size(); i++) {
              TdApi.Chat chat = selectedChats.valueAt(i);
              if (!tdlib.isSelfChat(chat.id)) {
                muteCount++;

                TdApi.ScopeNotificationSettings scopeNotificationSettings = tdlib.scopeNotificationSettings(chat);
                int count = scopeCounters.get(scopeNotificationSettings.getConstructor());
                scopeCounters.put(scopeNotificationSettings.getConstructor(), count + 1);
                if (!hasBlocked && tdlib.notifications().areNotificationsBlocked(chat.id, true))
                  hasBlocked = true;
              }
            }
            TdApi.ScopeNotificationSettings defaultSettings = scopeCounters.size() == 3 ? tdlib.scopeNotificationSettings(Td.constructNotificationSettingsScope(scopeCounters.keyAt(0))) : null;

            RunnableInt act = muteFor -> {
              int mutedCount = 0;
              for (int i = 0; i < selectedChats.size(); i++) {
                TdApi.Chat chat = selectedChats.valueAt(i);
                if (tdlib.isSelfChat(chat.id))
                  continue;
                if (muteFor == -1) {
                  chat.notificationSettings.useDefaultMuteFor = true;
                  tdlib.setChatNotificationSettings(chat.id, chat.notificationSettings);
                } else {
                  tdlib.setMuteFor(chat.id, muteFor);
                }
                mutedCount++;
              }
              UI.showToast(Lang.plural(R.string.MutedXChats, mutedCount), Toast.LENGTH_SHORT);
              onSelectionActionComplete();
            };

            int size = 3;
            IntList ids = new IntList(size);
            IntList icons = new IntList(size);
            IntList colors = hasBlocked ? new IntList(size) : null;
            StringList strings = new StringList(size);
            TdlibUi.fillMuteOptions(ids, icons, strings, colors, false, defaultSettings == null || !TD.isMutedForever(defaultSettings.muteFor), true, false, false, defaultSettings != null ? TdlibUi.getValueForSettings(defaultSettings.muteFor, true) : null, hasBlocked);
            showOptions(Lang.pluralBold(R.string.MuteXChats, muteCount), ids.get(), strings.get(), colors != null ? colors.get() : null, icons.get(), (itemView, optionId) -> {
              act.runWithInt(optionId == R.id.btn_menu_resetToDefault ? -1 : TdlibUi.getMuteDurationForId(optionId));
              return true;
            });
            break;
          }
          case ACTION_MODE_ALL_DISABLED:
          case ACTION_MODE_MIXED: {
            // Unmute all

            int overrideCount = 0;
            int unmuteCount = 0;
            int minScopeMute = 0;

            for (int i = 0; i < selectedChats.size(); i++) {
              TdApi.Chat chat = selectedChats.valueAt(i);
              if (tdlib.isSelfChat(chat.id))
                continue;
              if (tdlib.chatMuteFor(chat) > 0) {
                unmuteCount++;
              }
              int scopeMute = tdlib.scopeNotificationSettings(chat).muteFor;
              if (scopeMute > 0) {
                minScopeMute = overrideCount == 0 ? scopeMute : Math.min(scopeMute, minScopeMute);
                overrideCount++;
              }
            }

            Runnable act = () -> {
              int unmutedCount = 0, overriddenCount = 0;
              for (int i = 0; i < selectedChats.size(); i++) {
                TdApi.Chat chat = selectedChats.valueAt(i);
                if (tdlib.isSelfChat(chat.id))
                  continue;
                if (tdlib.chatMuteFor(chat) > 0)
                  unmutedCount++;
                if (tdlib.scopeNotificationSettings(chat).muteFor > 0)
                  overriddenCount++;
                tdlib.setMuteFor(chat.id, 0);
              }
              UI.showToast(Lang.plural(overriddenCount > 0 ? R.string.NotificationsOnXChats : R.string.UnmutedXChats, unmutedCount), Toast.LENGTH_SHORT);
              onSelectionActionComplete();
            };
            if (overrideCount > 0) {
              showOptions(Lang.getString(selectedChats.size() > overrideCount ? R.string.NotificationsEnableOverride3 : R.string.NotificationsEnableOverride2, Lang.lowercase(TdlibUi.getValueForSettings(minScopeMute))),
                new int[]{R.id.btn_unmute, R.id.btn_cancel},
                new String[]{Lang.plural(R.string.EnableNotifications2, unmuteCount), Lang.getString(R.string.Cancel)}, null,
                new int[]{R.drawable.baseline_notifications_24, R.drawable.baseline_cancel_24},
                (v, optionId) -> {
                  if (optionId == R.id.btn_unmute) {
                    act.run();
                  }
                  return true;
                }
              );
            } else {
              act.run();
            }
          }
          break;
        }
        break;
      }
      case R.id.menu_btn_delete: {
        bulkDeleteChat(false);
        break;
      }
    }
  }

  private void bulkDeleteChat (boolean clearHistory) {
    long lastChatId = 0;
    int count = 0;
    for (int i = 0; i < selectedChats.size(); i++) {
      if (tdlib.chatAvailable(selectedChats.valueAt(i))) {
        count++;
        lastChatId = selectedChats.keyAt(i);
      }
    }
    if (count == 1) {
      tdlib.ui().processChatAction(this, chatList(), lastChatId, null, clearHistory ? R.id.btn_clearChatHistory : R.id.btn_removeChatFromList, this::onSelectionActionComplete);
      return;
    }

    int revokeCount = 0;
    int losingAdminRights = 0;
    int closingSecretChatCount = 0;
    int noReturnGroupCount = 0, noReturnChannelCount = 0;
    int totalCount = 0;

    for (int i = 0; i < selectedChats.size(); i++) {
      TdApi.Chat chat = selectedChats.valueAt(i);
      if (!tdlib.chatAvailable(chat))
        continue;
      totalCount++;
      if (tdlib.canRevokeChat(chat.id)) {
        revokeCount++;
      }
      if (!clearHistory && ChatId.isSecret(chat.id)) {
        TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chat.id);
        if (secretChat != null && secretChat.state.getConstructor() != TdApi.SecretChatStateClosed.CONSTRUCTOR) {
          closingSecretChatCount++;
        }
      }
      TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
      if (status != null) {
        if (TD.isAdmin(status)) {
          losingAdminRights++;
        }
      }
      if (!tdlib.isUserChat(chat) && !tdlib.chatPublic(chat.id)) {
        if (tdlib.isChannelChat(chat))
          noReturnChannelCount++;
        else
          noReturnGroupCount++;
      }
    }

    SpannableStringBuilder info = new SpannableStringBuilder(Lang.pluralBold(clearHistory ? R.string.ClearXHistoriesConfirm : R.string.DeleteXChatsConfirm, totalCount));

    if (noReturnChannelCount + noReturnGroupCount + losingAdminRights > 0 || (!clearHistory && closingSecretChatCount > 0)) {
      info.append("\n");
      if (losingAdminRights > 0)
        info.append("\n").append(Lang.pluralBold(R.string.LosingXAdminRights, losingAdminRights));
      if (noReturnChannelCount + noReturnGroupCount > 0)
        info.append("\n").append(Lang.pluralBold(noReturnChannelCount == 0 ? R.string.LeaveXPrivateGroup : noReturnGroupCount == 0 ? R.string.LeaveXPrivateChannel : R.string.LeaveXChats, noReturnChannelCount + noReturnGroupCount));
      if (!clearHistory && closingSecretChatCount > 0)
        info.append("\n").append(Lang.pluralBold(R.string.ClosingXSecretChats, closingSecretChatCount));
    }

    String actionStr = Lang.plural(clearHistory ? R.string.ClearXHistories : R.string.DeleteXChats, selectedChats.size());

    RunnableBool deleter = needRevoke -> {
      showOptions(Lang.getString(R.string.NoUndoWarn), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {actionStr, Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {clearHistory ? R.drawable.templarian_baseline_broom_24 : R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
        if (optionId == R.id.btn_delete) {
          final int size = selectedChats.size();
          AtomicInteger remaining = new AtomicInteger(size);
          Runnable after = () -> {
            if (remaining.decrementAndGet() == 0) {
              UI.showToast(Lang.plural(clearHistory ? R.string.ClearedXHistories : R.string.DeletedXChats, size), Toast.LENGTH_SHORT);
              onSelectionActionComplete();
            }
          };
          for (int i = 0; i < size; i++) {
            tdlib.deleteChat(selectedChats.keyAt(i), needRevoke, after);
          }
        }
        return true;
      });
    };

    if (revokeCount > 0) {
      showSettings(new SettingsWrapBuilder(R.id.btn_removeChatFromList)
        .setAllowResize(false)
        .addHeaderItem(info)
        .setRawItems(new ListItem[]{
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearChatHistory, 0, Lang.pluralBold(R.string.RevokeForX, revokeCount), R.id.btn_clearChatHistory, false)
        })
        .setSaveColorId(R.id.theme_color_textNegative)
        .setSaveStr(clearHistory ? R.string.ClearHistoryBtn : R.string.Delete)
        .setIntDelegate((id, result) -> {
          boolean needRevoke = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
          deleter.runWithBool(needRevoke);
        })
      );
    } else {
      showOptions(info,
        new int[]{R.id.btn_delete, R.id.btn_cancel},
        new String[]{actionStr, Lang.getString(R.string.Cancel)},
        new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL},
        new int[]{R.drawable.templarian_baseline_broom_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
          if (optionId == R.id.btn_delete) {
            deleter.runWithBool(false);
          }
          return true;
        });
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.more_btn_archiveUnarchive:
      case R.id.more_btn_markAsRead:
      case R.id.more_btn_markAsUnread:
      case R.id.more_btn_report:
      case R.id.more_btn_block:
      case R.id.more_btn_unblock: {
        final int completeStr, count;
        int botCount = 0;
        switch (id) {
          case R.id.more_btn_archiveUnarchive:
            completeStr = chatList().getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR ? R.string.UnarchivedXChats : R.string.ArchivedXChats;
            count = getSelectedChatCount();
            break;
          case R.id.more_btn_markAsRead:
            completeStr = R.string.ReadAllChatsDone;
            count = getSelectedChatCount();
            break;
          case R.id.more_btn_markAsUnread:
            completeStr = R.string.MarkedXChats;
            count = getSelectedChatCount();
            break;
          case R.id.more_btn_report:
            completeStr = 0; // R.string.ReportedXChats;
            count = getSelectedChatCount();
            break;
          case R.id.more_btn_block:
          case R.id.more_btn_unblock:
            Set<Long> userIds = new HashSet<>();
            for (int i = 0; i < selectedChats.size(); i++) {
              long userId = tdlib.chatUserId(selectedChats.valueAt(i));
              userIds.add(userId);
              if (tdlib.cache().userBot(userId)) {
                botCount++;
              }
            }
            count = userIds.size();
            completeStr = count == botCount ? (id == R.id.more_btn_unblock ? R.string.UnblockedXBots : R.string.BlockedXBots) : id == R.id.more_btn_unblock ? R.string.UnblockedXUsers : R.string.BlockedXUsers;
            break;
          default:
            return;
        }
        Runnable onDone = () -> {
          if (completeStr != 0) {
            UI.showToast(Lang.plural(completeStr, count), Toast.LENGTH_SHORT);
          }
          onSelectionActionComplete();
        };
        if (count == 1) {
          int simpleActionId = 0;
          switch (id) {
            case R.id.more_btn_markAsUnread:
              simpleActionId = R.id.btn_markChatAsUnread;
              break;
            case R.id.more_btn_markAsRead:
              simpleActionId = R.id.btn_markChatAsRead;
              break;
            case R.id.more_btn_archiveUnarchive:
              simpleActionId = R.id.btn_archiveUnarchiveChat;
              break;
            case R.id.more_btn_report:
              TdlibUi.reportChat(getParentOrSelf(), selectedChats.keyAt(0), null, false, onDone, null);
              return;
          }
          if (simpleActionId != 0) {
            tdlib.ui().processChatAction(this, chatList(), selectedChats.keyAt(0), null, simpleActionId, onDone);
            return;
          }
        }
        AtomicInteger remaining = new AtomicInteger(selectedChats.size());
        Runnable after = () -> {
          if (remaining.decrementAndGet() == 0) {
            tdlib.ui().post(onDone);
          }
        };
        switch (id) {
          case R.id.more_btn_archiveUnarchive: {
            boolean isUnarchvie = chatList().getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR;
            showOptions(
              Lang.pluralBold(isUnarchvie ? R.string.UnarchiveXChats : R.string.ArchiveXChats, selectedChats.size()),
              new int[] { R.id.btn_archiveUnarchiveChat, R.id.btn_cancel },
              new String[] {Lang.getString(isUnarchvie ? R.string.Unarchive : R.string.Archive), Lang.getString(R.string.Cancel) }, null,
              new int[] {isUnarchvie ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
                if (optionId == R.id.btn_archiveUnarchiveChat) {
                  TdApi.ChatList chatList = isUnarchvie ? new TdApi.ChatListMain() : new TdApi.ChatListArchive();
                  for (int i = 0; i < selectedChats.size(); i++) {
                    tdlib.client().send(new TdApi.AddChatToList(selectedChats.keyAt(i), chatList), result -> {
                      switch (result.getConstructor()) {
                        case TdApi.Ok.CONSTRUCTOR:
                          after.run();
                          break;
                        case TdApi.Error.CONSTRUCTOR:
                          UI.showError(result);
                          break;
                      }
                    });
                  }
                }
                return true;
              }
            );
            break;
          }
          case R.id.more_btn_report: {
            long[] chatIds = ArrayUtils.keys(selectedChats);
            TdlibUi.reportChats(getParentOrSelf(), chatIds, onDone);
            break;
          }
          case R.id.more_btn_block:
          case R.id.more_btn_unblock: {
            boolean isUnblock = id == R.id.more_btn_unblock;
            if (isUnblock) {
              for (int i = selectedChats.size() - 1; i >= 0; i--) {
                long chatId = selectedChats.keyAt(i);
                tdlib.blockSender(tdlib.sender(chatId), false, tdlib.okHandler());
              }
            } else {
              showOptions(
                Lang.pluralBold(botCount == count ? R.string.BlockXBots : R.string.BlockXUsers, count),
                new int[]{R.id.btn_blockUser, R.id.btn_cancel},
                new String[]{Lang.getString(R.string.BlockContact), Lang.getString(R.string.Cancel)},
                new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL},
                new int[]{R.drawable.baseline_block_24, R.drawable.baseline_cancel_24},
                (v, optionId) -> {
                  if (optionId == R.id.btn_unblockUser || optionId == R.id.btn_blockUser) {
                    for (int i = selectedChats.size() - 1; i >= 0; i--) {
                      long chatId = selectedChats.keyAt(i);
                      tdlib.blockSender(tdlib.sender(chatId), optionId == R.id.btn_blockUser, tdlib.okHandler(after));
                    }
                  }
                  return true;
                }
              );
            }
            break;
          }
          default: {
            for (int i = 0; i < selectedChats.size(); i++) {
              switch (id) {
                case R.id.more_btn_markAsRead:
                  tdlib.markChatAsRead(selectedChats.keyAt(i), 0, after);
                  break;
                case R.id.more_btn_markAsUnread:
                  tdlib.markChatAsUnread(selectedChats.valueAt(i), after);
                  break;
              }
            }
            break;
          }
        }
        break;
      }
      case R.id.more_btn_selectAll: {
        int canMarkAsRead = 0;
        for (int i = 0; i < selectedChats.size(); i++) {
          TdApi.Chat chat = selectedChats.valueAt(i);
          if (tdlib.canMarkAsRead(chat)) {
            canMarkAsRead++;
          }
        }

        IntList ids = new IntList(3);
        StringList strings = new StringList(3);
        IntList icons = new IntList(3);

        ids.append(R.id.btn_selectAll);
        strings.append(R.string.SelectAll);
        icons.append(R.drawable.baseline_playlist_add_check_24);

        if (canMarkAsRead == selectedChats.size() && canMarkAsRead < tdlib.getCounter(chatList()).chatCount) {
          ids.append(R.id.btn_selectUnread);
          strings.append(R.string.SelectUnread);
          icons.append(Config.ICON_MARK_AS_UNREAD);
        }

        ids.append(R.id.btn_selectMuted);
        strings.append(R.string.SelectMuted);
        icons.append(R.drawable.baseline_notifications_off_24);

        showOptions(Lang.getString(R.string.SelectMore), ids.get(), strings.get(), null, icons.get(), (v, optionId) -> {
          switch (optionId) {
            case R.id.btn_selectAll: {
              selectChats(chat -> true, tdlib.getCounter(chatList()).totalChatCount);
              break;
            }
            case R.id.btn_selectUnread: {
              selectChats(chat -> chat.unreadCount > 0 || chat.isMarkedAsUnread, tdlib.getCounter(chatList()).chatCount);
              break;
            }
            case R.id.btn_selectMuted: {
              selectChats(tdlib::chatNeedsMuteIcon, tdlib.getCounter(chatList()).totalChatCount);
              break;
            }
          }
          return true;
        });

        // showOptions()
        break;
      }
      case R.id.more_btn_clearCache: {
        showOptions(Lang.pluralBold(R.string.ClearXChats, selectedChats.size()),
          new int[] {R.id.btn_clearCache, R.id.btn_cancel},
          new String[] {Lang.getString(R.string.DeleteChatCache), Lang.getString(R.string.Cancel)}, null,
          new int[] {R.drawable.templarian_baseline_broom_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
          if (optionId == R.id.btn_clearCache) {
            long[] chatIds = ArrayUtils.keys(selectedChats);
            UI.showToast(Lang.plural(R.string.ClearingXChats, chatIds.length), Toast.LENGTH_SHORT);
            tdlib.client().send(new TdApi.OptimizeStorage(0, 0, 0, 0, null, chatIds, null, true, 0), result -> {
              switch (result.getConstructor()) {
                case TdApi.StorageStatistics.CONSTRUCTOR: {
                  long totalSize = ((TdApi.StorageStatistics) result).size;
                  if (totalSize > 0) {
                    UI.showToast(Lang.plural(R.string.ClearedSizeChats, chatIds.length, Strings.buildSize(totalSize)), Toast.LENGTH_SHORT);
                  } else {
                    UI.showToast(Lang.plural(R.string.ClearedNoneChats, chatIds.length), Toast.LENGTH_SHORT);
                  }
                  break;
                }
                case TdApi.Error.CONSTRUCTOR:
                  UI.showError(result);
                  break;
              }
            });
            onSelectionActionComplete();
          }
          return true;
        });
        break;
      }
      case R.id.more_btn_clearHistory: {
        bulkDeleteChat(true);
        break;
      }
    }
  }

  @Override
  public void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    cancelSelectionSignals();
  }

  private List<CancellationSignal> pendingSelections;

  private void cancelSelectionSignals () {
    if (pendingSelections != null) {
      for (CancellationSignal signal : pendingSelections)
        signal.cancel();
      pendingSelections.clear();
    }
  }

  private void selectChats (Filter<TdApi.Chat> filter, int totalCount) {
    CancellationSignal selectionSignal = new CancellationSignal();
    for (TGChat parsedChat : adapter.getChats()) {
      if (!canSelectChat(parsedChat))
        continue;
      TdApi.Chat chat = filter(parsedChat.getChat());
      if (chat != null && filter.accept(chat)) {
        if (totalCount > 0) {
          totalCount--;
        }
        if (selectedChats.indexOfKey(chat.id) < 0) {
          selectUnselectChat(chat, false);
        }
      }
    }
    if (pendingSelections == null)
      pendingSelections = new ArrayList<>();
    pendingSelections.add(selectionSignal);
    RunnableBool after = isFinal -> {
      if (!isDestroyed() && !selectionSignal.isCanceled()) {
        getParentOrSelf().setSelectedCount(selectedChats.size());
        updateSelectButtons();
        if (isFinal) {
          pendingSelections.remove(selectionSignal);
        }
      }
    };
    if (totalCount > 0 || totalCount == -1) {
      tdlib.getAllChats(chatList, chat -> {
        if (filter(chat) != null && filter.accept(chat)) {
          tdlib.ui().post(() -> {
            if (!isDestroyed() && !selectionSignal.isCanceled()) {
              if (selectedChats.indexOfKey(chat.id) < 0) {
                selectUnselectChat(chat, false);
              }
            }
          });
        }
      }, isFinal -> tdlib.ui().post(() -> after.runWithBool(isFinal)), true);
    } else {
      after.runWithBool(true);
    }
  }

  private void onSelectionActionComplete () {
    getParentOrSelf().addOneShotFocusListener(() -> {
      if (!isDestroyed()) {
        getParentOrSelf().closeSelectMode();
        finishSelectMode(-1);
      }
    });
  }

  private void performSelectAction (TdApi.Function function) {
    tdlib.client().send(function, result -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          runOnUiThreadOptional(this::onSelectionActionComplete);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    });
  }

  @Override
  public boolean passNameToHeader () {
    return true;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (chatsView != null) {
      Views.setScrollBarPosition(chatsView);
      adapter.notifyAllChanged();
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    updateNetworkStatus(tdlib.connectionState());
    if (chatsView != null) {
      chatsView.updateLocale(Lang.hasDirectionChanged(event, arg1));
    }
  }

  @Override
  protected boolean useDrawer () {
    return true;
  }

  // load more

  @Override
  public boolean ableToLoadMore () {
    return list.canLoad();
  }

  public boolean isEndReached () {
    return list.isEndReached();
  }

  @Override
  public void requestLoadMore () {
    list.loadMore(chatsView != null ? chatsView.getLoadCount() : 40, null);
  }

  // Search mode

  @Override
  protected int getChatSearchFlags () {
    int flags = SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_WRITABLE;
    if (needMessagesSearch) {
      flags |= SearchManager.FLAG_NEED_MESSAGES;
    }
    return flags;
  }

  @Override
  protected TdApi.ChatList getChatMessagesSearchChatList () {
    TdApi.ChatList chatList = chatList();
    return chatList instanceof TdApi.ChatListMain ? null : chatList;
  }

  @Override
  protected int getChatMessagesSearchTitle () {
    if (filter != null && filter.canFilterMessages()) {
      return filter.getMessagesStringRes(chatList().getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR);
    }
    if (chatList().getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR) {
      return R.string.MessagesArchive;
    }
    return super.getChatMessagesSearchTitle();
  }

  @Override
  protected boolean filterChatMessageSearchResult (TdApi.Chat chat) {
    return chat != null && (filter == null || !filter.canFilterMessages() || filter.accept(chat)) && ChatPosition.findPosition(chat, chatList()) != null;
  }

  @Override
  protected View getSearchAntagonistView () {
    return chatsView;
  }

  @Override
  protected int getSearchHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getSearchHeaderIconColorId () {
    return R.id.theme_color_headerLightIcon;
  }

  // click listeners

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.chat: {
        final TGChat chat = ((ChatView) v).getChat();
        if (chat != null) {
          if (isChatSelected(chat) || getSelectedChatCount() > 0) {
            selectUnselectChat(chat, true);
            return;
          }
          if (chat.isArchive()) {
            ChatsController c = new ChatsController(context, tdlib);
            c.setArguments(new Arguments(new TdApi.ChatListArchive()).setNeedMessagesSearch(true));
            context.navigation().navigateTo(c);
          } else {
            onClick(chat.getChat());
          }
        }
        break;
      }
    }
  }

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    onClick(tdlib.chat(chat.getAnyId()));
    return true;
  }

  private void openChat (TdApi.Chat chat) {
    int highlightMode;
    Object shareItem = pickerDelegate != null ? pickerDelegate.getShareItem() : null;
    TdlibUi.ChatOpenParameters params;
    if ((highlightMode = MessagesManager.getAnchorHighlightMode(tdlib.id(), chat, null)) != MessagesManager.HIGHLIGHT_MODE_NONE) {
      params = new TdlibUi.ChatOpenParameters().shareItem(shareItem).highlightMessage(highlightMode, MessagesManager.getAnchorMessageId(tdlib.id(), chat, null, highlightMode));
    } else {
      params = new TdlibUi.ChatOpenParameters().shareItem(shareItem);
    }
    if (pickerDelegate != null) {
      pickerDelegate.modifyChatOpenParams(params);
    }
    if (chatList().getConstructor() != TdApi.ChatListMain.CONSTRUCTOR) {
      params.keepStack();
    }
    tdlib.ui().openChat(this, chat, params);
  }

  private void onClick (final TdApi.Chat chat) {
    if (Test.NEED_CLICK) {
      if (Test.onChatClick(tdlib, chat)) {
        return;
      }
    }
    if (pickerDelegate == null) {
      openChat(chat);
      return;
    }

    final Runnable onDone = () -> {
      if (!isDestroyed() && isFocused()) {
        openChat(chat);
      }
    };
    if (pickerDelegate.onChatPicked(chat, onDone)) {
      onDone.run();
    }
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    TGChat chat = v instanceof ChatView ? ((ChatView) v).getChat() : null;

    if (chat != null && chat.isArchive()) {
      if (tdlib.hasUnreadChats(ChatPosition.CHAT_LIST_ARCHIVE)) {
        ids.append(R.id.btn_markChatAsRead);
        strings.append(R.string.ArchiveRead);
        icons.append(Config.ICON_MARK_AS_READ);
      }

      final boolean needHide = tdlib.settings().needHideArchive();

      ids.append(R.id.btn_pinUnpinChat);
      if (needHide) {
        icons.append(R.drawable.deproko_baseline_pin_24);
        strings.append(R.string.ArchivePin);
      } else {
        icons.append(R.drawable.baseline_arrow_upward_24);
        strings.append(R.string.ArchiveHide);
      }

      context.setTdlib(tdlib);
      context.setHeaderAvatar(null, new AvatarPlaceholder(ComplexHeaderView.getBaseAvatarRadiusDp(), new AvatarPlaceholder.Metadata(R.id.theme_color_avatarArchive, R.drawable.baseline_archive_24), null));
      context.setHeader(Lang.getString(R.string.ArchiveTitle), Lang.plural(R.string.xChats, tdlib.getTotalChatsCount(ChatPosition.CHAT_LIST_ARCHIVE)));

      context.setMaximizeListener((target1, animateToWhenReady, arg) -> {
        ChatsController c = new ChatsController(context(), tdlib);
        c.setArguments(new Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setNeedMessagesSearch(true));
        c.postOnAnimationReady(() -> target1.animateTo(animateToWhenReady));
        c.forceFastAnimationOnce();
        context().navigation().navigateTo(c);
        return true;
      });

      return new ForceTouchView.ActionListener() {
        @Override
        public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

        }

        @Override
        public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
          switch (actionId) {
            case R.id.btn_markChatAsRead: {
              tdlib.readAllChats(new TdApi.ChatListArchive(), readCount -> UI.showToast(Lang.plural(R.string.ReadAllChatsDone, readCount), Toast.LENGTH_SHORT));
              break;
            }
            case R.id.btn_pinUnpinChat: {
              tdlib.settings().toggleUserPreference(TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE);
              break;
            }
          }
        }
      };
    }

    return tdlib.ui().createSimpleChatActions(this, ((BaseView) v).getPreviewChatList(), ((BaseView) v).getPreviewChatId(), null, ids, icons, strings, getSelectedChatCount() == 0, canSelectChat(chat), isChatSelected(chat), () -> selectUnselectChat(chat, true));
  }

  @Override
  public void finishSelectMode (int position) {
    cancelSelectionSignals();
    if (selectedChats != null) {
      while (selectedChats.size() > 0) {
        selectUnselectChat(selectedChats.valueAt(0), false);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.chat: {
        TGChat chat = ((ChatView) v).getChat();
        if (chat != null) {
          if (chat.isArchive()) {
            if (getSelectedChatCount() == 0) {
              tdlib.ui().showArchiveOptions(this, tdlib.chatList(ChatPosition.CHAT_LIST_ARCHIVE));
            }
          } else {
            tdlib.ui().showChatOptions(this, chatList(), chat.getChatId(), null, canSelectChat(chat), isChatSelected(chat), () -> selectUnselectChat(chat, true));
          }
          return true;
        }
        break;
      }
    }
    return false;
  }

  private LongSparseArray<TdApi.Chat> selectedChats;

  private int getSelectedChatCount () {
    return selectedChats != null ? selectedChats.size() : 0;
  }

  public boolean canSelectChat (TGChat chat) {
    return chat != null && !chat.isArchive() && tdlib.chatAvailable(chat.getChat());
  }

  public boolean isChatSelected (TGChat chat) {
    return canSelectChat(chat) && selectedChats != null && selectedChats.indexOfKey(chat.getChatId()) >= 0;
  }

  private void selectUnselectChat (TGChat chat, boolean updateHeader) {
    if (canSelectChat(chat))
      selectUnselectChat(chat.getChat(), updateHeader);
  }

  private void checkChatSelected (long chatId) {
    if (getSelectedChatCount() > 0) {
      TdApi.Chat chat = selectedChats.get(chatId);
      if (chat != null && !tdlib.chatAvailable(chat)) {
        selectUnselectChat(chat, true);
      }
    }
  }

  private void selectUnselectChat (TdApi.Chat chat, boolean updateHeader) {
    int index = selectedChats != null ? selectedChats.indexOfKey(chat.id) : -1;
    boolean isSelected = index >= 0;
    if (!tdlib.chatAvailable(chat) && !isSelected)
      return;
    isSelected = !isSelected;
    if (isSelected) {
      if (selectedChats == null)
        selectedChats = new LongSparseArray<>();
      selectedChats.put(chat.id, tdlib.chatStrict(chat.id));
      if (updateHeader) {
        if (selectedChats.size() == 1)
          getParentOrSelf().openSelectMode(1);
        else
          getParentOrSelf().setSelectedCount(selectedChats.size());
        updateSelectButtons();
      }
    } else {
      selectedChats.removeAt(index);
      if (updateHeader) {
        if (selectedChats.size() == 0) {
          getParentOrSelf().closeSelectMode();
        } else {
          getParentOrSelf().setSelectedCount(selectedChats.size());
          updateSelectButtons();
        }
      }
    }
    if (chatsView != null) {
      chatsView.updateChatSelectionState(chat.id, isSelected);
    }
  }

  // other stuff

  public void showProgressView () {
    if (!progressVisible) {
      if (spinnerView == null) {
        spinnerView = new ProgressComponentView(context());
        spinnerView.initLarge(1f);
        spinnerView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER));
      }
      if (spinnerView.getParent() == null) {
        contentView.addView(spinnerView);
      }
      progressVisible = true;
    }
  }

  public void hideProgressView () {
    if (spinnerView != null && progressVisible) {
      progressVisible = false;
      contentView.removeView(spinnerView);
      spinnerView.performDestroy();
      spinnerView = null;
    }
    shareIntentIfReady();
  }

  public @Nullable ChatsRecyclerView getChatsView () {
    return chatsView;
  }

  public ChatsAdapter getChatsAdapter () {
    return adapter;
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    if (spinnerView != null) {
      spinnerView.detach();
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (spinnerView != null) {
      spinnerView.attach();
    }
  }

  // Placeholder

  public final void checkDisplayProgress () {
    if (list.needProgressPlaceholder()) {
      showProgressView();
    } else {
      hideProgressView();
    }
  }

  public final void checkDisplayNoChats () {
    adapter.checkArchive();
    boolean noChats = list.isEndReached() && adapter.getChats().size() == 0;
    setDisplayNoChats(noChats);
  }

  public void checkListState () {
    checkDisplayProgress();
    checkDisplayNoChats();
  }

  private static final int ANIMATOR_PLACEHOLDER = 0;

  private boolean displayNoChats;
  private RecyclerView noChatsView;
  private SettingsAdapter noChatsAdapter;
  private BoolAnimator noChatsAnimator;

  private void setDisplayNoChats (boolean displayNoChats) {
    if (this.displayNoChats != displayNoChats) {
      this.displayNoChats = displayNoChats;

      if (noChatsAnimator == null) {
        noChatsAnimator = new BoolAnimator(ANIMATOR_PLACEHOLDER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, !displayNoChats);
      }
      if (displayNoChats) {
        prepareNoChats();
        if (parentController != null) {
          parentController.showComposeWrap(this);
        }
      }
      boolean animated = chatsView != null && Math.max(chatsView.getMeasuredWidth(), chatsView.getMeasuredHeight()) > 0 && initializationTime != 0 && SystemClock.uptimeMillis() - initializationTime >= 600l && (parentController != null ? parentController.isFocused() : isFocused());
      noChatsAnimator.setValue(displayNoChats, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_PLACEHOLDER: {
        if (noChatsView != null) {
          noChatsView.setAlpha(factor);
        }
        /*if (parentController != null) {
          parentController.setComposeAlpha(1f - factor);
        }*/
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_PLACEHOLDER: {
        if (finalFactor == 0f) {
          destroyNoChats();
        }
        break;
      }
    }
  }

  private void prepareNoChats () {
    if (noChatsView != null) {
      return;
    }

    noChatsAdapter = new SettingsAdapter(this, v -> {
      switch (v.getId()) {
        case R.id.btn_invite: {
          tdlib.contacts().startSyncIfNeeded(context(), true, () -> {
            if (parentController != null) {
              parentController.navigateTo(new PeopleController(context, tdlib).setNeedSearch().setNeedTutorial());
            }
          });
          break;
        }
        case R.id.btn_archive: {
          ChatsController c = new ChatsController(context, tdlib);
          c.setArguments(new Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setNeedMessagesSearch(true));
          if (parentController != null) {
            parentController.navigateTo(c);
          }
          break;
        }
      }
    }, this);
    ArrayList<ListItem> items = new ArrayList<>(5);

    if (filter != null) {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, filter.getEmptyStringRes()));
    } else if (chatList() instanceof TdApi.ChatListArchive) {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NoArchive));
    } else if (archiveList != null && archiveList.totalCount() > 0) {
      items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, R.id.changePhoneText, R.drawable.baseline_archive_96, Lang.getMarkdownString(this, R.string.OpenArchiveHint), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_archive, 0, R.string.OpenArchive));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      int count = tdlib.contacts().getRegisteredCount();
      items.add(new ListItem(ListItem.TYPE_CHATS_PLACEHOLDER, R.id.inviteFriendsText, R.drawable.baseline_forum_96, makeContactsDesc(), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_invite, 0, count > 0 ? R.string.ShowContacts : R.string.InviteContacts));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    /*if (ContactsManager.instance().getAvailableRegisteredCount() > 0) {
      items.add(newContactsDesc());
    }*/

    noChatsAdapter.setItems(items, false);

    noChatsView = new RecyclerView(context()) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        return getAlpha() == 0f || super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return getAlpha() > 0f && super.onTouchEvent(e);
      }
    };
    noChatsView.setHasFixedSize(true);
    noChatsView.setAlpha(0f);
    noChatsView.setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
    // noChatsView.setItemAnimator(null);
    noChatsView.setAdapter(noChatsAdapter);
    noChatsView.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(noChatsView, R.id.theme_color_background);
    noChatsView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(noChatsView, contentView.indexOfChild(chatsView) + 1);
  }

  private CharSequence makeContactsDesc () {
    long[] userIds = tdlib.contacts().getRegisteredUserIds();
    int count = tdlib.contacts().getAvailableRegisteredCount();
    switch (count) {
      case 1:
        // Paul Durov is using Telegram
        return Lang.getStringBold(R.string.ContactsOnTelegramSingle, tdlib.cache().userName(userIds[0]));
      case 2:
        // Mike and Olga are using Telegram
        return Lang.getStringBold(R.string.ContactsOnTelegramSeveral, tdlib.cache().userFirstName(userIds[0]),
                                                                    tdlib.cache().userFirstName(userIds[1]));
      case 3:
        // Mike, Paul and Olga are using Telegram
        return Lang.getStringBold(R.string.ContactsOnTelegramSeveral, Strings.join(Lang.getConcatSeparator(),
                                                                    tdlib.cache().userFirstName(userIds[0]), tdlib.cache().userFirstName(userIds[1])),
                                                                    tdlib.cache().userFirstName(userIds[2]));
      case 4:
        // Mike, Paul, Olga and Igor are using Telegram
        return Lang.getStringBold(R.string.ContactsOnTelegramSeveral, Strings.join(Lang.getConcatSeparator(),
                                                                      tdlib.cache().userFirstName(userIds[0]), tdlib.cache().userFirstName(userIds[1]), tdlib.cache().userFirstName(userIds[2])),
                                                                    tdlib.cache().userFirstName(userIds[3]));
    }
    if (count >= 5) {
      // Mike, Paul, Olga and X others are using Telegram
      return Lang.plural(R.string.ContactsOnTelegramMany, count - 3, Lang.boldCreator(), Strings.join(Lang.getConcatSeparator(),
        tdlib.cache().userFirstName(userIds[0]), tdlib.cache().userFirstName(userIds[1]), tdlib.cache().userFirstName(userIds[2])));
    }
    return null;
  }

  private void destroyNoChats () {
    if (noChatsView != null) {
      removeThemeListenerByTarget(noChatsView);
      Views.destroyRecyclerView(noChatsView);
      contentView.removeView(noChatsView);
      noChatsAdapter = null;
      noChatsView = null;
      if (scheduledUserIdsChange != null) {
        scheduledUserIdsChange.cancel();
        scheduledUserIdsChange = null;
      }
    }
  }

  private CancellableRunnable scheduledUserIdsChange;

  @Override
  public void onRegisteredContactsChanged (final long[] userIds, final int totalCount, boolean newArrival) {
    if (isDestroyed()) {
      return;
    }
    boolean animated = parentController != null && parentController.isFocused() && noChatsAnimator != null && noChatsAnimator.getFloatValue() > 0f;
    checkListState();
    if (noChatsAdapter != null) {
      if (scheduledUserIdsChange != null) {
        scheduledUserIdsChange.cancel();
        scheduledUserIdsChange = null;
      }
      if (animated && userIds != null && userIds.length > 0 && newArrival) {
        scheduledUserIdsChange = new CancellableRunnable() {
          @Override
          public void act () {
            if (scheduledUserIdsChange == this && noChatsAdapter != null) {
              setUserIds(userIds, totalCount, true);
            }
          }
        };
        scheduledUserIdsChange.removeOnCancel(UI.getAppHandler());
        UI.post(scheduledUserIdsChange, 1300);
      } else {
        setUserIds(userIds, totalCount, animated);
      }
    }
  }

  private void setUserIds (long[] userIds, int totalCount, boolean animated) {
    int i = noChatsAdapter.indexOfViewById(R.id.inviteFriendsText);
    if (i != -1) {
      ListItem item = noChatsAdapter.getItems().get(i);
      CharSequence newDesc = makeContactsDesc();
      View view = noChatsView.getLayoutManager().findViewByPosition(i);
      if (newDesc != null && !newDesc.equals(item.getString())) {
        item.setString(newDesc);
        noChatsAdapter.updateValuedSettingByPosition(i);
      }
      if (view != null) {
        ((JoinedUsersView) ((ViewGroup) ((ViewGroup) view).getChildAt(0)).getChildAt(0)).setUserIds(userIds, totalCount, animated);
      }
    }
    i = noChatsAdapter.indexOfViewById(R.id.btn_invite);
    if (i != -1) {
      ListItem item = noChatsAdapter.getItems().get(i);
      int res = totalCount > 0 ? R.string.ShowContacts : R.string.InviteContacts;
      if (item.getStringResource() != res) {
        item.setString(res);
        noChatsAdapter.updateValuedSettingByPosition(i);
      }
    }
  }

  @Override
  public void onUnregisteredContactsChanged (int oldTotalCount, ArrayList<TdlibContactManager.UnregisteredContact> contacts, int totalCount) { }

  // Data load

  public boolean isInitialLoadFinished () {
    return initialLoadFinished;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !listInitalized;
  }

  @Override
  public void onChatListStateChanged (TdlibChatList chatList, @TdlibChatList.State int newState, int oldState) {
    runOnUiThreadOptional(() -> {
      if (newState == TdlibChatList.State.END_REACHED) {
        adapter.updateInfo();
      }
      checkListState();
      if (oldState == TdlibChatList.State.LOADING) {
        hideProgressView();
        if (!initialLoadFinished) {
          initialLoadFinished = true;
          adapter.checkArchive();
        }
      }
      if (chatsView != null && newState == TdlibChatList.State.END_NOT_REACHED) {
        LinearLayoutManager manager = (LinearLayoutManager) chatsView.getLayoutManager();
        int lastVisiblePosition = manager.findLastVisibleItemPosition();
        if (lastVisiblePosition == adapter.getItemCount() - 1) {
          requestLoadMore();
        }
      }
    });
  }

  @Override
  public void onChatListChanged (TdlibChatList chatList, @ChangeFlags int changeFlags) {
    runOnUiThreadOptional(() -> {
      if (BitwiseUtils.getFlag(changeFlags, ChangeFlags.ITEM_MOVED | ChangeFlags.ITEM_ADDED)) {
        checkListState();
      }
    });
  }

  @Override
  public void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      chatsView.processChatUpdate(
        adapter.updateChat(chat, index, changeInfo)
      );
    });
  }

  @Override
  public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() ->
      chatsView.processChatUpdate(
        adapter.addChat(chat, atIndex, changeInfo)
      )
    );
  }

  @Override
  public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() ->
      chatsView.processChatUpdate(
        adapter.removeChatById(chat, fromIndex, changeInfo)
      )
    );
  }

  @Override
  public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() ->
      chatsView.processChatUpdate(
        adapter.moveChat(chat, fromIndex, toIndex, changeInfo)
      )
    );
  }

  // Destructor

  @Override
  public void destroy () {
    super.destroy();
    if (liveLocationHelper != null) {
      liveLocationHelper.destroy();
    }
    if (archiveList != null) {
      archiveList.unsubscribeFromUpdates(archiveListListener);
    }
    if (adapter != null) {
      List<TGChat> chats = adapter.getChats();
      for (TGChat chat : chats) {
        chat.performDestroy();
      }
    }
    Settings.instance().removeChatListModeListener(this);
    tdlib.settings().removeUserPreferenceChangeListener(this);
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
    tdlib.cache().unsubscribeFromAnyUpdates(this);
    list.unsubscribeFromUpdates(this);
    TGLegacyManager.instance().removeEmojiListener(this);
    tdlib.contacts().removeListener(this);
  }

  // Updates

  @Override
  public void onEmojiPartLoaded () {
    if (chatsView != null) {
      chatsView.invalidateAll();
    }
  }

  @Override
  public void onChatTopMessageChanged (final long chatId, @Nullable final TdApi.Message topMessage) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatTopMessage(chatId, topMessage);
      }
    });
  }

  @Override
  public void onChatPositionChanged (final long chatId, final TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatPosition(chatId, position, orderChanged, sourceChanged, pinStateChanged);
        if (position.order == 0) {
          checkChatSelected(chatId);
        }
      }
    });
  }

  @Override
  public void onChatTitleChanged (final long chatId, final String title) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatTitle(chatId, title);
      }
    });
  }

  @Override
  public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatPermissionsChanged(chatId, permissions);
      }
    });
  }

  @Override
  public void onChatClientDataChanged (long chatId, @Nullable String clientData) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatClientData(chatId, clientData);
      }
    });
  }

  @Override
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatMarkedAsUnread(chatId, isMarkedAsUnread);
      }
    });
  }

  @Override
  public void onChatPhotoChanged (final long chatId, final @Nullable TdApi.ChatPhotoInfo photo) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatPhoto(chatId, photo);
      }
    });
  }

  @Override
  public void onChatReadInbox (final long chatId, final long lastReadInboxMessageId, final int unreadCount, boolean availabilityChanged) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatReadInbox(chatId, lastReadInboxMessageId, unreadCount);
      }
    });
  }

  @Override
  public void onChatUnreadMentionCount(final long chatId, final int unreadMentionCount, boolean availabilityChanged) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatUnreadMentionCount(chatId, unreadMentionCount);
      }
    });
  }

  @Override
  public void onChatHasScheduledMessagesChanged (long chatId, boolean hasScheduledMessages) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatHasScheduledMessages(chatId, hasScheduledMessages);
      }
    });
  }

  @Override
  public void onChatReadOutbox (final long chatId, final long lastReadOutboxMessageId) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatReadOutbox(chatId, lastReadOutboxMessageId);
      }
    });
  }

  @Override
  public void onChatDraftMessageChanged (final long chatId, final @Nullable TdApi.DraftMessage draftMessage) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateChatDraftMessage(chatId, draftMessage);
      }
    });
  }

  // Secret Chat updates

  @Override
  public void onSecretChatUpdated (final TdApi.SecretChat secretChat) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateSecretChat(secretChat);
      }
    });
  }


  // Connection updates

  @Override
  public void onConnectionStateChanged (int newState, int oldState) {
    runOnUiThreadOptional(() -> updateNetworkStatus(newState));
  }

  // Setting updates

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateNotificationSettings(scope, settings);
      }
    });
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateNotificationSettings(chatId, settings);
      }
    });
  }

  @Override
  public void onPreferenceChanged(Tdlib tdlib, long key, boolean value) {
    if (this.tdlib.id() == tdlib.id() && key == TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE) {
      setHideArchive(value);
    }
  }

  // Message updates (usually affect only data)

  @Override
  public void onMessageSendAcknowledged (final long chatId, final long messageId) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.refreshLastMessage(chatId, messageId, false);
      }
    });
  }

  @Override
  public void onMessageSendSucceeded (final TdApi.Message message, final long oldMessageId) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateMessageSendSucceeded(message, oldMessageId);
      }
    });
  }

  @Override
  public void onMessageContentChanged (final long chatId, final long messageId, final TdApi.MessageContent newContent) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateMessageContent(chatId, messageId, newContent);
      }
    });
  }

  @Override
  public void onMessagePendingContentChanged (long chatId, long messageId) {
    tdlib.uiExecute(() -> {
      if (chatsView != null) {
        chatsView.refreshLastMessage(chatId, messageId, true);
      }
    });
  }

  @Override
  public void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateMessageInteractionInfo(chatId, messageId, interactionInfo);
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateMessagesDeleted(chatId, messageIds);
      }
    });
  }

  // Counter updates

  @Override
  public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    if (totalCount == 0 && chatList.getConstructor() != TdApi.ChatListMain.CONSTRUCTOR && Td.equalsTo(this.chatList, chatList)) {
      runOnUiThreadOptional(() -> {
        if (!isDestroyed() && !isBaseController()) {
          navigateBack();
        }
      });
    }
  }

  // Data updates

  @Override
  public void onSupergroupUpdated (final TdApi.Supergroup supergroup) {
    if (!TD.isMember(supergroup.status)) {
      runOnUiThreadOptional(() -> {
        checkChatSelected(ChatId.fromSupergroupId(supergroup.id));
      });
    }
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    if (!TD.isMember(basicGroup.status)) {
      runOnUiThreadOptional(() -> {
        checkChatSelected(ChatId.fromBasicGroupId(basicGroup.id));
      });
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    runOnUiThreadOptional(() -> {
      if (chatsView != null) {
        chatsView.updateUser(user);
      }
    });
  }

  @Override
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (chatsView != null) {
      chatsView.updateUserStatus(userId);
    }
  }

  // System sharing

  public void shareIntent (Intent intent) {
    shareIntent(intent, true);
  }

  private void shareIntent (Intent intent, boolean share) {
    shareIntent = intent;
    if (share) {
      shareIntentIfReady();
    }
  }

  private void shareIntentIfReady () {
    if (shareIntent == null || progressVisible) {
      return;
    }
    final Intent intent = shareIntent;
    shareIntent = null;
    final String type = intent.getType() == null ? "" : intent.getType();
    switch (type) {
      case "text/plain":
      case "message/rfc822": {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
          CharSequence sequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
          if (sequence != null) {
            text = sequence.toString();
          }
        }
        if (text != null && (text = text.trim()).length() > 0) {
          ShareController c = new ShareController(context, tdlib);
          c.setArguments(new ShareController.Args(text));
          c.show();
        }
        break;
      }
    }
  }

  // Arguments that may be passed to ChatsController

  public static class Arguments {
    public ChatFilter filter;
    public PickerDelegate pickerDelegate;
    public TdApi.ChatList chatList;
    public boolean isBaseController;
    public boolean needMessagesSearch;

    public Arguments (ChatFilter filter) {
      this.filter = filter;
    }

    public Arguments (TdApi.ChatList chatList) {
      this.chatList = chatList;
    }

    public Arguments (PickerDelegate delegate) {
      this.pickerDelegate = delegate;
    }

    public Arguments (ChatFilter filter, PickerDelegate pickerDelegate) {
      this.filter = filter;
      this.pickerDelegate = pickerDelegate;
    }

    public Arguments setNeedMessagesSearch (boolean needMessagesSearch) {
      this.needMessagesSearch = needMessagesSearch;
      return this;
    }

    public Arguments setChatList (TdApi.ChatList chatList) {
      this.chatList = chatList;
      return this;
    }

    public Arguments setIsBase (boolean isBase) {
      this.isBaseController = isBase;
      return this;
    }
  }

  // Force

  @Override
  public void onPrepareForceTouchContext (ForceTouchView.ForceTouchContext context) {
    context.setIsMatchParent(true);
  }

  private TooltipOverlayView.TooltipInfo dragTooltip;

  @Override
  public boolean onInterceptLongPress (BaseView v, float x, float y) {
    if (!(v instanceof ChatView) || !((ChatView) v).canStartDrag(x, y))
      return false;
    TGChat chat = ((ChatView) v).getChat();
    if (chatsView != null && chat.isPinned() && adapter.canDragPinnedChats()) {
      RecyclerView.ViewHolder viewHolder = chatsView.getChildViewHolder(v);
      if (viewHolder != null) {
        v.dropPress(x, y);
        context.hideContextualPopups(true);
        touchHelper.startDrag(viewHolder);
        if (dragTooltip != null)
          dragTooltip.hide(true);
        dragTooltip = context.tooltipManager().builder(v).controller(getParentOrSelf()).locate((targetView, outRect) -> ((ChatView) targetView).getAvatarReceiver().toRect(outRect)).show(tdlib, R.string.DragChatsHint);
        return true;
      }
    }
    return false;
  }
}
