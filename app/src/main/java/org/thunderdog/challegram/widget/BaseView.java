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
 * File created on 22/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;

public class BaseView extends SparseDrawableView implements ClickHelper.Delegate, View.OnClickListener, TdlibDelegate {
  public interface ActionListProvider {
    @Deprecated
    ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target);

    default ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, ArrayList<ActionItem> items, ViewController<?> target) {
      IntList ids = new IntList(5);
      IntList icons = new IntList(5);
      StringList strings = new StringList(5);
      ForceTouchView.ActionListener result = onCreateActions(v, context, ids, icons, strings, target);
      for (int i = 0; i < ids.size(); i++) {
        items.add(new ActionItem(ids.get(i), icons.get(i), strings.get()[i]));
      }
      return result;
    };
  }

  public static class ActionItem {
    public final int id;
    public final int iconRes;
    public final String title;
    public final TdApi.MessageSender messageSender;

    public ActionItem (int id, int iconRes, String title) {
      this.id = id;
      this.iconRes = iconRes;
      this.title = title;
      this.messageSender = null;
    }

    public ActionItem (int id, String title, TdApi.MessageSender messageSender) {
      this.id = id;
      this.iconRes = 0;
      this.title = title;
      this.messageSender = messageSender;
    }
  }

  public interface CustomControllerProvider {
    boolean needsForceTouch (BaseView v, float x, float y);
    boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview);
    ViewController<?> createForceTouchPreview (BaseView v, float x, float y);
  }

  public interface SlideOffListener {
    boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview);
  }

  protected final Tdlib tdlib;
  protected final ClickHelper forceTouchHelper;
  private boolean usesDefaultClickListener;
  private SlideOffListener slideOffListener;

  public BaseView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.forceTouchHelper = new ClickHelper(this);
    this.forceTouchHelper.setNoSound(true);
    setUseDefaultClickListener(true);
  }

  @Override
  public BaseActivity context () {
    return UI.getContext(getContext());
  }

  @Override
  public Tdlib tdlib () {
    return tdlib;
  }

  public interface TranslationChangeListener {
    void onTranslationChanged (BaseView view, float x, float y);
  }

  private List<TranslationChangeListener> translationChangeListeners;

  public void addOnTranslationChangeListener (@Nullable TranslationChangeListener listener) {
    if (this.translationChangeListeners == null)
      this.translationChangeListeners = new ArrayList<>();
    if (!this.translationChangeListeners.contains(listener))
      this.translationChangeListeners.add(listener);
  }

  public void removeOnTranslationChangeListener (TranslationChangeListener listener) {
    if (this.translationChangeListeners != null) {
      this.translationChangeListeners.remove(listener);
    }
  }

  @Override
  public void setTranslationX (float translationX) {
    if (this.translationChangeListeners != null && !this.translationChangeListeners.isEmpty()) {
      float oldTranslationX = getTranslationX();
      super.setTranslationX(translationX);
      if (oldTranslationX != translationX) {
        float translationY = getTranslationY();
        for (TranslationChangeListener listener : translationChangeListeners) {
          listener.onTranslationChanged(this, translationX, translationY);
        }
      }
    } else {
      super.setTranslationX(translationX);
    }
  }

  @Override
  public void setTranslationY (float translationY) {
    if (this.translationChangeListeners != null && !this.translationChangeListeners.isEmpty()) {
      float oldTranslationY = getTranslationY();
      super.setTranslationY(translationY);
      if (oldTranslationY != translationY) {
        float translationX = getTranslationX();
        for (TranslationChangeListener listener : translationChangeListeners) {
          listener.onTranslationChanged(this, translationX, translationY);
        }
      }
    } else {
      super.setTranslationY(translationY);
    }
  }

  public final void setSlideOffListener (SlideOffListener slideOffListener) {
    this.slideOffListener = slideOffListener;
  }

  protected final void setUseDefaultClickListener (boolean use) {
    this.usesDefaultClickListener = use;
    super.setOnClickListener(use ? this : null);
  }

  protected final boolean usesDefaultClickListener () {
    return usesDefaultClickListener;
  }

  private @Nullable View.OnClickListener onClickListener;
  private @Nullable View.OnLongClickListener onLongClickListener;
  private @Nullable ActionListProvider actionListProvider;
  private @Nullable CustomControllerProvider customControllerProvider;

  public void setCustomControllerProvider (@Nullable CustomControllerProvider customControllerProvider) {
    this.customControllerProvider = customControllerProvider;
  }

  // Clicks & long press

  @Override
  public void onClick (View view) {
    // Do nothing here.
  }

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    this.onClickListener = l;
  }

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    this.onLongClickListener = l;
    this.actionListProvider = l instanceof ActionListProvider ? (ActionListProvider) l : null;
  }

  public final void setPreviewActionListProvider (@Nullable ActionListProvider provider) {
    this.actionListProvider = provider;
  }

  private boolean dispatchingEvents;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res;
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        dispatchingEvents = res = usesDefaultClickListener && super.onTouchEvent(e);
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        res = dispatchingEvents && super.onTouchEvent(e);
        dispatchingEvents = false;
        break;
      }
      default: {
        res = dispatchingEvents && super.onTouchEvent(e);
        break;
      }
    }
    if (isEnabled()) {
      return forceTouchHelper.onTouchEvent(this, e) || res;
    }
    return false;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return isEnabled() && onClickListener != null;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (isEnabled() && onClickListener != null) {
      onClickListener.onClick(view);
    }
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return isEnabled() && (onLongClickListener != null || needForceTouch(x, y) != FORCE_TOUCH_NONE);
  }

  @Override
  public long getLongPressDuration () {
    return ViewConfiguration.getLongPressTimeout();
  }

  @Override
  public boolean ignoreHapticFeedbackSettings (float x, float y) {
    return needForceTouch(x, y) != FORCE_TOUCH_NONE;
  }

  @Override
  public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    if (awaitingSlideOff) {
      boolean ok = false;
      if (customControllerProvider != null) {
        ok = customControllerProvider.onSlideOff(this, x, y, currentOpenPreview);
      } else if (slideOffListener != null) {
        ok = slideOffListener.onSlideOff(this, x, y, currentOpenPreview);
      }
      if (ok) {
        awaitingSlideOff = false;
        closePreview();
      }
    }
    if (currentOpenPreview != null) {
      UI.getContext(getContext()).processForceTouchMoveEvent(x, y, startX, startY);
    }
  }

  public interface LongPressInterceptor {
    boolean onInterceptLongPress (BaseView v, float x, float y);
  }

  private LongPressInterceptor longPressInterceptor;

  public void setLongPressInterceptor (LongPressInterceptor avatarLongPressListener) {
    this.longPressInterceptor = avatarLongPressListener;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (longPressInterceptor != null && longPressInterceptor.onInterceptLongPress(this, x, y)) {
      return true;
    }
    int forceTouchMode = needForceTouch(x, y);
    if (forceTouchMode != FORCE_TOUCH_NONE) {
      switch (forceTouchMode) {
        case FORCE_TOUCH_CHAT: {
          TdApi.Chat chat = tdlib.chat(chatId);
          if (chat != null) {
            if (threadMessages != null && threadMessages.length > 0) {
              cancelAsyncPreview();
              tdlib.client().send(new TdApi.GetMessageThread(chatId, threadMessages[0].id), result -> {
                switch (result.getConstructor()) {
                  case TdApi.MessageThreadInfo.CONSTRUCTOR: {
                    TdApi.MessageThreadInfo threadInfo = (TdApi.MessageThreadInfo) result;
                    tdlib.ui().post(() -> {
                      if (pendingTask == null && pendingController == null) {
                        openChatPreviewAsync(chatList, chat, ThreadInfo.openedFromChat(tdlib, threadInfo, chatId, contextChatId), filter, x, y);
                      }
                    });
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    Log.i("Message thread unavailable %d %d: %s", chatId, threadMessages[0].id, TD.toErrorString(result));
                    break;
                  }
                }
              });
            } else {
              openChatPreviewAsync(chatList, chat, null, filter, x, y);
            }
            return false;
          }
          break;
        }
        case FORCE_TOUCH_CUSTOM: {
          if (customControllerProvider != null) {
            ViewController<?> controller = customControllerProvider.createForceTouchPreview(this, x, y);
            if (controller != null) {
              if (controller.needAsynchronousAnimation()) {
                openPreviewAsync(controller, x, y);
              } else {
                openPreview(controller, x, y);
              }
              return false;
            }
          }
          return false;
        }
      }
    }

    boolean ok = onLongClickListener != null && onLongClickListener.onLongClick(view);

    if (ok) {
      setInLongPress(true, x, y);
    }

    return ok;
  }

  private ViewParent requestedDisallowOnParent;
  private boolean awaitingSlideOff;
  private float slideOffStartY;

  public void dropPress (float x, float y) {
    setInLongPress(false, x, y);
  }

  private void setInLongPress (boolean inLongPress, float x, float y) {
    if (inLongPress) {
      setPressed(false);
      dispatchingEvents = false;
      requestedDisallowOnParent = getParent();
      awaitingSlideOff = true;
      slideOffStartY = y;
    }
    if (requestedDisallowOnParent != null) {
      requestedDisallowOnParent.requestDisallowInterceptTouchEvent(inLongPress);
    }
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    cancelAsyncPreview();
    closePreview();
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    setInLongPress(false, x, y);
    closePreview();
  }

  // MessagesController-specific

  private long chatId, contextChatId;
  private TdApi.ChatList chatList;
  private TdApi.Message[] threadMessages;
  private TdApi.SearchMessagesFilter filter;
  private int highlightMode;
  private MessageId highlightMessageId;
  private boolean allowMaximizePreview;

  public final void setPreviewChatId (TdApi.ChatList chatList, long chatId, TdApi.Message[] threadMessages) {
    setPreviewChatId(chatList, chatId, threadMessages, null, null);
  }

  public final void clearPreviewChat () {
    setPreviewChatId(null, 0, null);
  }

  public final void setPreviewChatId (TdApi.ChatList chatList, long chatId, TdApi.Message[] threadMessages, MessageId highlightMessageId, TdApi.SearchMessagesFilter filter) {
    setPreviewChatId(chatList, chatId, chatId, threadMessages, highlightMessageId, filter, true);
  }

  public final void setPreviewChatId (TdApi.ChatList chatList, long chatId, long contextChatId, TdApi.Message[] threadMessages, MessageId highlightMessageId, TdApi.SearchMessagesFilter filter, boolean allowMaximize) {
    this.chatList = chatList;
    this.chatId = chatId;
    this.contextChatId = contextChatId;
    this.threadMessages = threadMessages;
    this.highlightMessageId = highlightMessageId;
    this.filter = filter;
    if (highlightMessageId != null) {
      this.highlightMode = MessagesManager.HIGHLIGHT_MODE_NORMAL;
    } else {
      this.highlightMode = MessagesManager.HIGHLIGHT_MODE_NONE;
    }
    this.allowMaximizePreview = allowMaximize;
  }

  public final TdApi.ChatList getPreviewChatList () {
    return chatList;
  }

  public final long getPreviewChatId () {
    return chatId;
  }

  public final MessageId getPreviewHighlightMessageId () {
    return highlightMessageId;
  }

  public final TdApi.SearchMessagesFilter getPreviewFilter () {
    return filter;
  }

  public final TdApi.Message[] getPreviewThreadMessages () {
    return threadMessages;
  }

  private ViewController<?> pendingController;
  private CancellableRunnable pendingTask;

  private void openChatPreviewAsync (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable ThreadInfo messageThread, TdApi.SearchMessagesFilter filter, float x, float y) {
    if (currentOpenPreview != null) {
      return;
    }
    cancelAsyncPreview();
    final MessagesController controller = new MessagesController(getContext(), tdlib);
    controller.setArguments(createChatPreviewArguments(chatList, chat, messageThread, filter));
    openPreviewAsync(controller, x, y);
  }

  public final MessagesController.Arguments createChatPreviewArguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable ThreadInfo messageThread, TdApi.SearchMessagesFilter filter) {
    ViewController<?> controller = context().navigation().getCurrentStackItem();
    if (controller != null) {
      String query = controller.getLastSearchInput();
      if (!StringUtils.isEmpty(query) && highlightMode != MessagesManager.HIGHLIGHT_MODE_NONE) {
        controller.preventLeavingSearchMode();
        return new MessagesController.Arguments(chatList, chat, messageThread, highlightMessageId, highlightMode, filter, highlightMessageId, query);
      }
    }
    if (filter != null) {
      return new MessagesController.Arguments(chatList, chat, null, null, filter, highlightMessageId, highlightMode);
    } else if (highlightMode != MessagesManager.HIGHLIGHT_MODE_NONE) {
      return new MessagesController.Arguments(chatList, chat, messageThread, highlightMessageId, highlightMode, filter);
    } else {
      return new MessagesController.Arguments(tdlib, chatList, chat, messageThread, filter);
    }
  }

  // Controller-independent

  private static final int FORCE_TOUCH_NONE = 0;
  private static final int FORCE_TOUCH_CHAT = 1;
  private static final int FORCE_TOUCH_CUSTOM = 2;

  private int needForceTouch (final float x, final float y) {
    if (!Config.FORCE_TOUCH_ENABLED) {
      return FORCE_TOUCH_NONE;
    }
    if (tdlib != null && chatId != 0 && Settings.instance().needPreviewChatOnHold()) {
      if (ChatId.isSecret(chatId))
        return FORCE_TOUCH_NONE;
      TdApi.Chat chat = tdlib.chatSync(chatId, 100l);
      if (chat == null) {
        if (!ChatId.isUserChat(chatId))
          return FORCE_TOUCH_NONE;
        tdlib.client().send(new TdApi.CreatePrivateChat(ChatId.toUserId(chatId), true), tdlib.silentHandler());
      }
      return FORCE_TOUCH_CHAT;
    }
    if (customControllerProvider != null && customControllerProvider.needsForceTouch(this, x, y)) {
      return FORCE_TOUCH_CUSTOM;
    }
    return FORCE_TOUCH_NONE;
  }

  private void openPreviewAsync (final ViewController<?> controller, final float x, final float y) {
    controller.setInForceTouchMode(true);
    if (controller.wouldHideKeyboardInForceTouchMode()) {
      UI.getContext(getContext()).hideSoftwareKeyboard();
    }
    pendingController = controller;
    pendingTask = new CancellableRunnable() {
      @Override
      public void act () {
        if (pendingController == controller) {
          pendingController = null;
          pendingTask = null;
          openPreview(controller, x, y);
        }
      }
    };
    pendingTask.removeOnCancel(UI.getAppHandler());
    controller.scheduleAnimation(pendingTask, 600l);
    controller.get();
  }

  private void cancelAsyncPreview () {
    if (pendingController != null) {
      pendingController.destroy();
      pendingController = null;
    }
    if (pendingTask != null) {
      pendingTask.cancel();
      pendingTask = null;
    }
  }

  private ViewController<?> currentOpenPreview;

  private void openPreview (ViewController<?> controller, float x, float y) {
    ViewController<?> ancestor = ViewController.findAncestor(this);
    if ((ancestor != null && tdlib != null && ancestor.tdlib() != null && ancestor.tdlib().id() != tdlib.id())) {
      return;
    }
    int[] location = Views.getLocationOnScreen(this);
    int sourceX = location[0] + Math.round(x);
    int sourceY = location[1] + Math.round(y);
    ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, this, controller.get(), controller);
    context.setStateListener(controller);
    context.setAnimationSourcePoint(sourceX, sourceY);

    if (controller instanceof ForceTouchView.PreviewDelegate) {
      ((ForceTouchView.PreviewDelegate) controller).onPrepareForceTouchContext(context);
    }

    // context.setAdditionalOffsetView(UI.getCurrentStackItem(getContext()).getViewForApplyingOffsets());

    if (controller instanceof MessagesController && allowMaximizePreview) {
      context.setMaximizeListener((target, animateToWhenReady, arg) -> MessagesController.maximizeFrom(tdlib, getContext(), target, animateToWhenReady, arg));
    }

    ArrayList<ActionItem> actions = new ArrayList<>(5);
    ForceTouchView.ActionListener listener = null;

    if (actionListProvider != null) {
      listener = actionListProvider.onCreateActions(this, context, actions, controller);
    } else if (controller instanceof MessagesController && ancestor != null) {
      // TODO move MessagesController-related stuff somewhere out of BaseView
      if (getPreviewFilter() instanceof TdApi.SearchMessagesFilterPinned && getPreviewHighlightMessageId() != null && tdlib.canPinMessages(tdlib.chat(chatId))) {
        actions.add(new ActionItem(R.id.btn_messageUnpin, R.drawable.deproko_baseline_pin_undo_24, Lang.getString(R.string.Unpin)));
        MessageId messageId = getPreviewHighlightMessageId();
        listener = new ForceTouchView.ActionListener() {
          @Override
          public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
            if (actionId == R.id.btn_messageUnpin) {
              ancestor.showOptions(new ViewController.Options.Builder().item(new ViewController.OptionItem(R.id.btn_messageUnpin, Lang.getString(R.string.UnpinMessage), ViewController.OPTION_COLOR_RED, R.drawable.deproko_baseline_pin_undo_24)).cancelItem().build(), (optionItemView, id) -> {
                if (id == R.id.btn_messageUnpin) {
                  tdlib.client().send(new TdApi.UnpinChatMessage(messageId.getChatId(), messageId.getMessageId()), tdlib.okHandler());
                }
                return true;
              });
            }
          }

          @Override
          public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }
        };
      } else {
        IntList ids = new IntList(5);
        IntList icons = new IntList(5);
        StringList strings = new StringList(5);
        listener = ancestor.tdlib().ui().createSimpleChatActions(ancestor, getPreviewChatList(), getPreviewChatId(), ((MessagesController) controller).getMessageThread(), new TdApi.MessageSourceOther(), ids, icons, strings, ancestor instanceof MainController || ancestor instanceof ChatsController, false, false, null);
        for (int i = 0; i < ids.size(); i++) {
          actions.add(new ActionItem(ids.get(i), icons.get(i), strings.get()[i]));
        }
      }
    }
    if (listener != null) {
      context.setButtons(listener, controller, actions);
    }

    if (UI.getContext(getContext()).openForceTouch(context)) {
      currentOpenPreview = controller;
      forceTouchHelper.onLongPress(this, x, y);
      setInLongPress(true, x, y);
    } else {
      controller.destroy();
    }
  }

  private void closePreview () {
    if (currentOpenPreview != null) {
      UI.getContext(getContext()).closeForceTouch();
      currentOpenPreview = null;
    }
  }

  // Utils

  protected final boolean isParentVisible () {
    ViewParent parent = getParent();
    if (parent != null) {
      parent = ((View) parent).getAlpha() > 0f && ((View) parent).getVisibility() == View.VISIBLE ? parent.getParent() : null;
      return parent != null && ((View) parent).getAlpha() > 0f && ((View) parent).getVisibility() == View.VISIBLE;
    }
    return false;
  }
}
