/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.CheckBoxView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import tgx.td.Td;
import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** Official shareMessage flow: cached message preview, peer/topic selection, then acknowledged sending. */
final class MiniAppPreparedMessage {
  interface Gateway { void send (TdApi.Function<?> function, Client.ResultHandler handler); }
  interface PreviewLoader { void load (long queryId, String resultId, PreviewCallback callback); }
  interface PreviewCallback { void loaded (MiniAppInlinePreview preview, TdApi.Error error); }
  private final MiniAppController host;
  private final Tdlib tdlib;
  private final Gateway gateway;
  private final PreviewLoader previews;
  private Operation operation;

  MiniAppPreparedMessage (MiniAppController host) {
    this(host, (function, handler) -> host.tdlib().client().send(function, handler), null);
  }

  MiniAppPreparedMessage (MiniAppController host, Gateway gateway, PreviewLoader previews) {
    this.host = host; tdlib = host.tdlib(); this.gateway = gateway;
    this.previews = previews != null ? previews : (queryId, resultId, callback) -> {
      TdApi.Function<?> function = MiniAppInlinePreview.request(queryId, resultId);
      if (function == null) { callback.loaded(null, new TdApi.Error(501, "UNSUPPORTED")); return; }
      gateway.send(function, result -> callback.loaded(MiniAppInlinePreview.parse(result), result instanceof TdApi.Error ? (TdApi.Error) result : null));
    };
  }

  boolean active () { return operation != null; }

  void cancel () {
    if (operation == null) return;
    // A picker deliberately minimizes its Mini App. A submitted send cannot be recalled by closing a sheet.
    if (operation.current() && (operation.selecting || operation.sending)) return;
    operation.finish("USER_DECLINED");
  }

  void request (MiniAppDocument.Request origin, JSONObject data) {
    if (active()) return;
    if (!MiniAppPreparedMessagePolicy.validId(data.opt("id"))) {
      host.reply(origin, "prepared_message_failed", object("error", "MESSAGE_EXPIRED")); return;
    }
    Operation next = new Operation(origin, (String) data.opt("id")); operation = next;
    if (!host.canShowNativeUi()) { next.finish("USER_DECLINED"); return; }
    host.getValue().postDelayed(() -> { if (next.current() && next.prepared == null && host.isShowing()) next.loading(); }, 500);
    next.send(new TdApi.GetPreparedInlineMessage(host.botId(), next.id), response -> {
      if (!(response instanceof TdApi.PreparedInlineMessage)) { next.finish("MESSAGE_EXPIRED"); return; }
      next.prepared = (TdApi.PreparedInlineMessage) response;
      next.resultId = MiniAppPreparedMessagePolicy.resultId(next.prepared.result);
      if (next.resultId == null || next.resultId.isEmpty()) { next.finish("MESSAGE_EXPIRED"); return; }
      previews.load(next.prepared.inlineQueryId, next.resultId, (preview, error) -> tdlib.ui().post(() -> {
        if (!next.current()) return;
        if (!host.isShowing()) { next.finish("USER_DECLINED"); return; }
        if (preview == null || preview.content == null) {
          boolean unsupported = error != null && error.code == 501;
          if (unsupported) UI.showToast(R.string.MiniAppMessageUnsupported, Toast.LENGTH_LONG);
          next.finish(unsupported ? "UNSUPPORTED" : "MESSAGE_EXPIRED"); return;
        }
        next.preview = preview;
        next.send(new TdApi.CreatePrivateChat(tdlib.myUserId(), false), self -> {
          if (!(self instanceof TdApi.Chat)) { next.finish("UNKNOWN_ERROR"); return; }
          next.selfChat = (TdApi.Chat) self;
          next.showPreview();
        });
      }));
    });
  }

  private static final class Target {
    final TdApi.Chat chat;
    final TdApi.MessageTopic topic;
    final String title;
    Target (TdApi.Chat chat, TdApi.MessageTopic topic, String title) { this.chat = chat; this.topic = topic; this.title = title; }
    String key () { return chat.id + ":" + (topic instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) topic).forumTopicId : 0); }
  }

  private final class Operation {
    final MiniAppDocument.Request origin;
    final String id;
    TdApi.PreparedInlineMessage prepared;
    String resultId;
    MiniAppInlinePreview preview;
    TdApi.Chat selfChat;
    Dialog dialog;
    org.thunderdog.challegram.widget.PopupLayout optionsPopup;
    LinearLayout content, rows;
    TextView submit, errorView, loadMore;
    EditText search, comment;
    MiniAppMessagePreview previewView;
    final List<AvatarView> avatars = new ArrayList<>();
    final LinkedHashMap<String, Target> selected = new LinkedHashMap<>();
    final LinkedHashMap<Long, TdApi.Chat> chats = new LinkedHashMap<>();
    final List<MiniAppMessageSender> senders = new ArrayList<>();
    final List<TdApi.ForumTopicInfo> topics = new ArrayList<>();
    TdApi.Chat topicChat;
    TdApi.ForumTopics topicPage;
    boolean selecting, sending, loading, completeList;
    int generation, limit = 100, pending, succeeded;
    Runnable searchTask;
    TdApi.Error sendError;
    String commentText = "";

    Operation (MiniAppDocument.Request origin, String id) { this.origin = origin; this.id = id; }
    boolean current () { return operation == this && host.current(origin); }
    boolean interactive () { return current() && !sending && host.context().getActivityState() == UI.State.RESUMED; }
    void send (TdApi.Function<?> function, Client.ResultHandler handler) {
      gateway.send(function, result -> tdlib.ui().post(() -> {
        if (!current()) return;
        if (!selecting && (!host.isShowing() || host.context().getActivityState() != UI.State.RESUMED)) { finish("USER_DECLINED"); return; }
        handler.onResult(result);
      }));
    }
    void finish (String error) {
      if (operation != this) return;
      operation = null; generation++; dismiss();
      for (MiniAppMessageSender sender : senders) sender.cancel();
      senders.clear();
      host.reply(origin, error == null ? "prepared_message_sent" : "prepared_message_failed", error == null ? null : object("error", error));
      if (error == null && host.current(origin)) host.restore();
    }
    void dismiss () {
      if (searchTask != null && search != null) search.removeCallbacks(searchTask);
      searchTask = null;
      if (optionsPopup != null) { optionsPopup.setDismissListener(null); optionsPopup.hideWindow(false); optionsPopup = null; }
      if (dialog != null) { dialog.setOnDismissListener(null); dialog.dismiss(); dialog = null; }
      if (previewView != null) { previewView.destroy(); previewView = null; }
      releaseAvatars();
    }
    void releaseAvatars () { for (AvatarView avatar : avatars) avatar.performDestroy(); avatars.clear(); }
    TextView label (CharSequence text, int size) {
      TextView view = new TextView(host.context()); view.setText(text); view.setTextSize(size); view.setTextColor(Theme.textAccentColor()); return view;
    }
    void sheet (String title, boolean full) {
      dismiss(); generation++;
      dialog = new Dialog(host.context(), Theme.dialogTheme()); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      content = new LinearLayout(host.context()); content.setOrientation(LinearLayout.VERTICAL); content.setFitsSystemWindows(true);
      GradientDrawable bg = new GradientDrawable(); bg.setColor(Theme.fillingColor()); bg.setCornerRadii(new float[] {Screen.dp(16),Screen.dp(16),Screen.dp(16),Screen.dp(16),0,0,0,0}); content.setBackground(bg);
      TextView heading = label(title, 20); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heading.setPadding(Screen.dp(22), Screen.dp(20), Screen.dp(22), Screen.dp(16)); content.addView(heading);
      if (full) heading.setOnClickListener(v -> { if (interactive() && topicChat != null) { topicChat = null; showPicker(); } });
      errorView = label("", 14); errorView.setTextColor(Theme.getColor(ColorId.textNegative)); errorView.setPadding(Screen.dp(22), Screen.dp(8), Screen.dp(22), Screen.dp(8)); errorView.setVisibility(View.GONE);
      dialog.setContentView(content); dialog.setCanceledOnTouchOutside(!full);
      dialog.setOnDismissListener(ignored -> { if (!sending) finish("USER_DECLINED"); });
      dialog.setOnKeyListener((ignored, key, event) -> {
        if (key == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP && topicChat != null && !sending) { topicChat = null; showPicker(); return true; }
        return false;
      });
    }
    void show (boolean full) {
      content.addView(errorView); dialog.show();
      Window window = dialog.getWindow();
      if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent); window.setGravity(Gravity.BOTTOM);
        window.setLayout(-1, full ? -1 : -2); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p = window.getAttributes(); p.dimAmount = .3f; window.setAttributes(p);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      }
    }
    TextView button (String text, Runnable action) {
      TextView button = label(text, 16); button.setGravity(Gravity.CENTER); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setTextColor(Color.WHITE);
      GradientDrawable bg = new GradientDrawable(); bg.setColor(Theme.getColor(ColorId.fillingPositive)); bg.setCornerRadius(Screen.dp(24)); button.setBackground(bg);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Screen.dp(48)); p.setMargins(Screen.dp(10), Screen.dp(10), Screen.dp(10), Screen.dp(10)); content.addView(button, p);
      button.setOnClickListener(v -> { if (interactive()) action.run(); }); return button;
    }
    void loading () {
      sheet(Lang.getString(R.string.MiniAppShareMessage), false);
      ProgressBar progress = new ProgressBar(host.context()); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Screen.dp(40), Screen.dp(40)); p.gravity = Gravity.CENTER; p.bottomMargin = Screen.dp(24); content.addView(progress, p); show(false);
    }
    void showPreview () {
      sheet(Lang.getString(R.string.MiniAppShareMessage), false);
      previewView = new MiniAppMessagePreview(host, selfChat, preview);
      ScrollView scroll = new ScrollView(host.context()) {
        @Override protected void onMeasure (int widthSpec, int heightSpec) {
          int maximum = Math.max(Screen.dp(100), host.context().getRootView().getHeight() * 3 / 4 - Screen.dp(180));
          super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST));
        }
      };
      scroll.addView(previewView); content.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
      TextView info = label(Lang.getString(R.string.MiniAppShareMessageInfo, host.botName()), 14); info.setTextColor(Theme.textDecentColor()); info.setPadding(Screen.dp(22), Screen.dp(12), Screen.dp(22), Screen.dp(12)); content.addView(info);
      submit = button(Lang.getString(R.string.MiniAppShareMessageChoose), () -> {
        selecting = true; dismiss(); host.minimize(); showPicker();
      }); show(false);
    }
    boolean accepts (TdApi.Chat chat) {
      return MiniAppChatPolicy.allows(prepared.chatTypes, chat, chat != null && tdlib.isBotChat(chat)) &&
        (tdlib.canSendBasicMessage(chat) || tdlib.canSendSendSomeMedia(chat));
    }
    CharSequence restriction (TdApi.Chat chat) {
      if (!accepts(chat)) return Lang.getString(R.string.MiniAppUnsupportedChat);
      CharSequence inline = tdlib.getInlineRestrictionText(chat);
      if (inline != null) return inline;
      return tdlib.getRestrictionText(chat, TD.newFakeMessage(chat.id, new TdApi.MessageSenderUser(tdlib.myUserId()), preview.content));
    }
    EditText input (int hint) {
      EditText view = new EditText(host.context()); view.setSingleLine(); view.setTextSize(16); view.setTextColor(Theme.textAccentColor()); view.setHintTextColor(Theme.textDecentColor()); view.setHint(Lang.getString(hint));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Screen.dp(48)); p.setMargins(Screen.dp(16), 0, Screen.dp(16), 0); content.addView(view, p); return view;
    }
    void showPicker () {
      if (comment != null) commentText = comment.getText().toString();
      sheet(Lang.getString(topicChat == null ? R.string.SendTo : R.string.MiniAppSelectTopic), true);
      search = input(R.string.Search);
      ScrollView scroll = new ScrollView(host.context()); rows = new LinearLayout(host.context()); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
      content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
      loadMore = label(Lang.getString(R.string.MiniAppLoadMore), 14); loadMore.setGravity(Gravity.CENTER); loadMore.setTextColor(Theme.getColor(ColorId.fillingPositive));
      content.addView(loadMore, new LinearLayout.LayoutParams(-1, Screen.dp(40))); loadMore.setOnClickListener(v -> { if (interactive() && !loading) loadMore(); });
      comment = input(R.string.AddComment); comment.setText(commentText); comment.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(tdlib.maxMessageTextLength())});
      submit = button(Lang.getString(R.string.Send), () -> submit(Td.newSendOptions())); updateSubmit();
      submit.setOnLongClickListener(v -> {
        if (!interactive() || selected.isEmpty()) return false;
        showSendOptions(); return true;
      });
      show(true);
      search.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override public void afterTextChanged (Editable text) { }
        @Override public void onTextChanged (CharSequence s, int start, int before, int count) {
          generation++; if (searchTask != null) search.removeCallbacks(searchTask);
          searchTask = () -> { if (topicChat == null) loadChats(s.toString(), true); else loadTopics(s.toString(), false); };
          search.postDelayed(searchTask, 250);
        }
      });
      scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
        if (interactive() && !loading && !completeList && scroll.getScrollY() > 0 && scroll.getScrollY() + scroll.getHeight() >= rows.getHeight() - Screen.dp(80)) loadMore();
      });
      completeList = false; loading = false;
      if (topicChat == null) loadChats("", true); else loadTopics("", false);
    }
    void showSendOptions () {
      Keyboard.hide(comment); dialog.hide();
      boolean[] nextPicker = {false};
      optionsPopup = host.showOptions(null, new int[] {R.id.btn_sendNoSound, R.id.btn_sendScheduledCustom},
        new String[] {Lang.getString(R.string.SendNoSound), Lang.getString(R.string.SendSchedule)}, null,
        new int[] {R.drawable.baseline_notifications_off_24, R.drawable.baseline_date_range_24}, (view, id) -> {
          if (!interactive()) return true;
          if (id == R.id.btn_sendNoSound) { dialog.show(); submit(Td.newSendOptions(null, null, true)); }
          else if (id == R.id.btn_sendScheduledCustom) {
            nextPicker[0] = true;
            optionsPopup = host.showDateTimePicker(Lang.getString(R.string.SendSchedule), R.string.SendTodayAt, R.string.SendTomorrowAt, R.string.SendDateAt,
              millis -> { if (interactive()) { dialog.show(); submit(Td.newSendOptions(null, new TdApi.MessageSchedulingStateSendAtDate((int) (millis / 1000), 0))); } }, null);
            if (optionsPopup != null) optionsPopup.setDismissListener(new org.thunderdog.challegram.widget.PopupLayout.DismissListener() {
              @Override public void onPopupDismiss (org.thunderdog.challegram.widget.PopupLayout popup) { optionsPopup = null; if (current() && dialog != null && !sending) dialog.show(); }
            });
          }
          return true;
        });
      if (optionsPopup == null) { dialog.show(); return; }
      optionsPopup.setDismissListener(new org.thunderdog.challegram.widget.PopupLayout.DismissListener() {
        @Override public void onPopupDismiss (org.thunderdog.challegram.widget.PopupLayout popup) { if (!nextPicker[0]) { optionsPopup = null; if (current() && dialog != null && !sending) dialog.show(); } }
      });
    }
    void loadMore () {
      if (topicChat == null) { limit += 100; loadChats(search.getText().toString(), true); }
      else loadTopics(search.getText().toString(), true);
    }
    void loadChats (String query, boolean fetch) {
      int requestGeneration = ++generation; loading = true; loadMore.setEnabled(false);
      TdApi.Function<?> function = query.isEmpty() ? new TdApi.GetChats(new TdApi.ChatListMain(), limit) : new TdApi.SearchChats(query, null, limit);
      send(function, result -> {
        if (requestGeneration != generation) return;
        loading = false; loadMore.setEnabled(true);
        if (!(result instanceof TdApi.Chats)) { error(result); return; }
        chats.clear();
        if (accepts(selfChat) && (query.isEmpty() || Lang.getString(R.string.SavedMessages).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))) chats.put(selfChat.id, selfChat);
        long[] ids = ((TdApi.Chats) result).chatIds;
        for (long id : ids) { TdApi.Chat chat = tdlib.chat(id); if (accepts(chat)) chats.put(id, chat); }
        renderChats();
        if (!query.isEmpty()) completeList = ids.length < limit;
        if (fetch && query.isEmpty() && !completeList && ids.length < limit) {
          loading = true; send(new TdApi.LoadChats(new TdApi.ChatListMain(), 100), loaded -> {
            if (requestGeneration != generation) return;
            if (loaded instanceof TdApi.Error && ((TdApi.Error) loaded).code != 404) { loading = false; error(loaded); return; }
            completeList = loaded instanceof TdApi.Error; loadChats(query, false);
          });
        }
        loadMore.setVisibility(completeList ? View.GONE : View.VISIBLE);
      });
    }
    void renderChats () {
      releaseAvatars(); rows.removeAllViews();
      for (TdApi.Chat chat : chats.values()) {
        Target target = new Target(chat, null, tdlib.isSelfChat(chat.id) ? Lang.getString(R.string.SavedMessages) : chat.title);
        row(target, selected.containsKey(target.key()) || selected.values().stream().anyMatch(t -> t.chat.id == chat.id), () -> {
          CharSequence error = restriction(chat); if (error != null) { errorText(error); return; }
          if (tdlib.isForum(chat.id)) { topicChat = chat; showPicker(); }
          else { toggle(target); renderChats(); }
        });
      }
      if (chats.isEmpty()) empty();
    }
    void loadTopics (String query, boolean more) {
      int requestGeneration = ++generation; loading = true;
      TdApi.ForumTopics offset = more ? topicPage : null;
      if (!more) topics.clear();
      send(new TdApi.GetForumTopics(topicChat.id, query, offset == null ? 0 : offset.nextOffsetDate, offset == null ? 0 : offset.nextOffsetMessageId, offset == null ? 0 : offset.nextOffsetForumTopicId, 100), result -> {
        if (requestGeneration != generation) return;
        loading = false;
        if (!(result instanceof TdApi.ForumTopics)) { error(result); return; }
        topicPage = (TdApi.ForumTopics) result;
        for (TdApi.ForumTopic topic : topicPage.topics) if (!topic.info.isHidden) topics.add(topic.info);
        completeList = topicPage.topics.length == 0 || topicPage.nextOffsetForumTopicId == 0;
        loadMore.setVisibility(completeList ? View.GONE : View.VISIBLE); renderTopics();
      });
    }
    void renderTopics () {
      releaseAvatars(); rows.removeAllViews();
      for (TdApi.ForumTopicInfo topic : topics) {
        Target target = new Target(topicChat, new TdApi.MessageTopicForum(topic.forumTopicId), topic.name);
        row(target, selected.containsKey(target.key()), () -> {
          if (topic.isClosed && !(tdlib.chatStatus(topicChat.id) instanceof TdApi.ChatMemberStatusCreator) && !(tdlib.chatStatus(topicChat.id) instanceof TdApi.ChatMemberStatusAdministrator)) { errorText(Lang.getString(R.string.MiniAppUnsupportedChat)); return; }
          toggle(target); renderTopics();
        });
      }
      if (topics.isEmpty()) empty();
    }
    void row (Target target, boolean checked, Runnable action) {
      LinearLayout row = new LinearLayout(host.context()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Screen.dp(16), 0, Screen.dp(16), 0);
      AvatarView avatar = new AvatarView(host.context()); avatar.setChat(tdlib, target.chat); avatars.add(avatar); row.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
      TextView title = label(target.title, 16); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMarginStart(Screen.dp(12)); row.addView(title, p);
      CheckBoxView check = new CheckBoxView(host.context()); check.setCircular(true, false); check.setChecked(checked, false); row.addView(check, new LinearLayout.LayoutParams(Screen.dp(24), Screen.dp(24)));
      row.setContentDescription(target.title); row.setOnClickListener(v -> { if (interactive()) action.run(); }); rows.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(64)));
    }
    void empty () { TextView empty = label(Lang.getString(R.string.NoChats), 16); empty.setGravity(Gravity.CENTER); rows.addView(empty, new LinearLayout.LayoutParams(-1, Screen.dp(80))); }
    void toggle (Target target) { if (selected.containsKey(target.key())) selected.remove(target.key()); else selected.put(target.key(), target); updateSubmit(); }
    void updateSubmit () { submit.setEnabled(!selected.isEmpty()); submit.setText(selected.isEmpty() ? Lang.getString(R.string.Send) : Lang.getString(R.string.MiniAppShareCount, selected.size())); }
    void error (TdApi.Object result) { errorText(result instanceof TdApi.Error ? TD.toErrorString(result) : Lang.getString(R.string.MiniAppUnsupportedChat)); }
    void errorText (CharSequence text) { errorView.setText(text); errorView.setVisibility(View.VISIBLE); }

    void submit (TdApi.MessageSendOptions options) {
      if (!interactive() || selected.isEmpty()) return;
      for (Target target : selected.values()) {
        CharSequence restriction = restriction(tdlib.chat(target.chat.id));
        if (restriction == null) restriction = tdlib.getSlowModeRestrictionText(target.chat.id, options.schedulingState);
        if (restriction != null) { errorText(restriction); return; }
      }
      sending = true; submit.setEnabled(false); submit.setText(Lang.getString(R.string.MiniAppSendingMessage));
      search.setEnabled(false); comment.setEnabled(false); Keyboard.hide(comment);
      dialog.setCancelable(false); dialog.setCanceledOnTouchOutside(false);
      pending = selected.size();
      String text = comment.getText().toString();
      for (Target target : selected.values()) {
        TdApi.InputMessageReplyTo reply = host.preparedReplyTo(target.chat.id, target.topic);
        TdApi.SendInlineQueryResultMessage function = new TdApi.SendInlineQueryResultMessage(target.chat.id, target.topic, reply, Td.newSendOptions(options), prepared.inlineQueryId, resultId, false);
        MiniAppMessageSender sender = new MiniAppMessageSender(tdlib, target.chat.id, function, gateway::send, error -> {
          if (!current()) return;
          if (error != null) { sent(error); return; }
          succeeded++;
          if (text.isEmpty()) { sent(null); return; }
          // The optional comment is sent only after its prepared message was accepted, with the same topic/options.
          TdApi.SendMessage commentFunction = new TdApi.SendMessage(target.chat.id, target.topic, reply, Td.newSendOptions(options), null, new TdApi.InputMessageText(new TdApi.FormattedText(text, null), null, false));
          MiniAppMessageSender commentSender = new MiniAppMessageSender(tdlib, target.chat.id, commentFunction, gateway::send, this::sent);
          senders.add(commentSender); commentSender.send();
        });
        senders.add(sender); sender.send();
      }
    }
    void sent (TdApi.Error error) {
      if (!current()) return;
      if (error != null) sendError = error;
      if (--pending != 0) return;
      if (sendError != null) UI.showError(sendError);
      finish(succeeded > 0 ? null : "MESSAGE_SEND_FAILED");
    }
  }
}
