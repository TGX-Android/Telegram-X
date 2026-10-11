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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.CheckBoxView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** Prepared keyboard buttons are resolved by Telegram, never interpreted from untrusted JS fields. */
final class MiniAppRequestedChat {
  interface Gateway { void send (TdApi.Function<?> function, Client.ResultHandler handler); }
  private final MiniAppController host;
  private final Tdlib tdlib;
  private final Gateway gateway;
  private Operation operation;

  MiniAppRequestedChat (MiniAppController host) { this(host, (function, handler) -> host.tdlib().client().send(function, handler)); }
  MiniAppRequestedChat (MiniAppController host, Gateway gateway) { this.host = host; this.tdlib = host.tdlib(); this.gateway = gateway; }
  boolean active () { return operation != null; }
  void cancel () {
    if (operation == null) return;
    // Minimizing cannot undo an already submitted share. Keep its result for the same document.
    if (operation.sharing && operation.current()) operation.dismiss();
    else operation.finish(false);
  }

  void request (MiniAppDocument.Request origin, JSONObject data) {
    Object value = data.opt("req_id");
    if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 1024 || active()) return;
    Operation next = new Operation(origin, (String) value); operation = next;
    if (!host.canShowNativeUi()) { next.finish(false); return; }
    next.send(new TdApi.GetPreparedKeyboardButton(host.botId(), next.id), result -> {
      if (!(result instanceof TdApi.KeyboardButton)) { next.finish(false); return; }
      next.button = ((TdApi.KeyboardButton) result).type;
      if (next.button instanceof TdApi.KeyboardButtonTypeRequestUsers || next.button instanceof TdApi.KeyboardButtonTypeRequestChat) next.picker();
      else if (next.button instanceof TdApi.KeyboardButtonTypeRequestManagedBot) {
        // TDLib 42e6a525: RequestedDialogType::check_shared_dialog rejects Type::CreateBot.
        // Do not create a real bot that this TDLib cannot subsequently share to complete the request.
        UI.showToast(R.string.MiniAppManagedBotUnavailable, android.widget.Toast.LENGTH_LONG); next.finish(false);
      }
      else next.finish(false);
    });
  }

  private final class Operation {
    final MiniAppDocument.Request origin;
    final String id;
    final TdApi.KeyboardButtonSource source;
    TdApi.KeyboardButtonType button;
    Dialog dialog;
    LinearLayout content, rows;
    TextView errorView, submit, loadMore;
    EditText search;
    final LinkedHashMap<Long, TdApi.Chat> selected = new LinkedHashMap<>();
    final List<AvatarView> avatars = new ArrayList<>();
    int searchGeneration, limit = 100;
    Runnable searchTask;
    boolean working, loading, listComplete, sharing;
    TdApi.Chat createdChat;

    Operation (MiniAppDocument.Request origin, String id) { this.origin = origin; this.id = id; source = new TdApi.KeyboardButtonSourceWebApp(host.botId(), id); }
    boolean current () { return operation == this && host.current(origin); }
    boolean interactive () { return current() && host.isShowing() && host.context().getActivityState() == UI.State.RESUMED; }
    void send (TdApi.Function<?> function, Client.ResultHandler result) {
      gateway.send(function, response -> tdlib.ui().post(() -> {
        if (!current()) { if (operation == this) finish(false); return; }
        if (!sharing && !interactive()) { finish(false); return; }
        result.onResult(response);
      }));
    }
    void finish (boolean sent) {
      if (operation != this) return;
      operation = null; dismiss();
      host.reply(origin, sent ? "requested_chat_sent" : "requested_chat_failed", object("req_id", id));
    }
    void dismiss () {
      searchGeneration++;
      if (searchTask != null && search != null) { search.removeCallbacks(searchTask); searchTask = null; }
      if (dialog != null) { dialog.setOnDismissListener(null); dialog.dismiss(); dialog = null; }
      releaseAvatars();
    }
    void releaseAvatars () { for (AvatarView avatar : avatars) avatar.performDestroy(); avatars.clear(); }
    void sheet (String title) {
      dismiss(); working = false;
      dialog = new Dialog(host.context(), Theme.dialogTheme()); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      content = new LinearLayout(host.context()); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, Screen.dp(12), 0, Screen.dp(12));
      GradientDrawable background = new GradientDrawable(); background.setColor(Theme.fillingColor()); background.setCornerRadii(new float[] {Screen.dp(16),Screen.dp(16),Screen.dp(16),Screen.dp(16),0,0,0,0}); content.setBackground(background);
      TextView heading = label(title, 20); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heading.setPadding(Screen.dp(22), Screen.dp(8), Screen.dp(22), Screen.dp(16)); content.addView(heading);
      errorView = label("", 14); errorView.setTextColor(Theme.getColor(ColorId.textNegative)); errorView.setPadding(Screen.dp(22), Screen.dp(8), Screen.dp(22), Screen.dp(8)); errorView.setVisibility(View.GONE);
      dialog.setContentView(content); dialog.setCanceledOnTouchOutside(true); dialog.setOnDismissListener(ignored -> finish(false));
    }
    void show () {
      content.addView(errorView);
      dialog.show();
      Window window = dialog.getWindow();
      if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.transparent); window.setGravity(Gravity.BOTTOM);
        window.setLayout(-1, -2); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes(); params.dimAmount = .3f; window.setAttributes(params);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      }
    }
    void error (TdApi.Object result) {
      working = false; if (submit != null) submit.setEnabled(true);
      errorView.setText(result instanceof TdApi.Error ? ((TdApi.Error) result).message : Lang.getString(R.string.MiniAppUnsupportedChat)); errorView.setVisibility(View.VISIBLE);
    }
    void shared (TdApi.Object result) {
      if (result instanceof TdApi.Error) UI.showError(result);
      finish(result instanceof TdApi.Ok);
    }
    void submitShare () {
      working = sharing = true; submit.setEnabled(false);
      if (dialog != null) { dialog.setCancelable(false); dialog.setCanceledOnTouchOutside(false); }
      send(share(false), this::shared);
    }
    TextView button (String text, Runnable action) {
      TextView view = label(text, 16); view.setGravity(Gravity.CENTER); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setTextColor(Color.WHITE);
      GradientDrawable bg = new GradientDrawable(); bg.setColor(Theme.getColor(ColorId.fillingPositive)); bg.setCornerRadius(Screen.dp(8)); view.setBackground(bg);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Screen.dp(48)); params.setMargins(Screen.dp(10), Screen.dp(10), Screen.dp(10), 0); content.addView(view, params);
      view.setOnClickListener(v -> { if (interactive() && !working) action.run(); }); return view;
    }
    void info (String text) { TextView view = label(text, 14); view.setPadding(Screen.dp(22), 0, Screen.dp(22), Screen.dp(12)); content.addView(view); }
    void rightsInfo (TdApi.ChatAdministratorRights rights) {
      TextView view = label(rightsDescription(rights), 14); view.setPadding(Screen.dp(22), 0, Screen.dp(22), Screen.dp(12));
      ScrollView scroll = new ScrollView(host.context()) {
        @Override protected void onMeasure (int widthSpec, int heightSpec) {
          super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(Screen.dp(132), View.MeasureSpec.AT_MOST));
        }
      };
      scroll.addView(view); content.addView(scroll);
    }
    EditText input (int hint, String value, int maximum) {
      EditText view = new EditText(host.context()); view.setSingleLine(); view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17); view.setTextColor(Theme.textAccentColor()); view.setHintTextColor(Theme.textDecentColor());
      view.setHint(Lang.getString(hint)); view.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(maximum)}); view.setText(value);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Screen.dp(56)); params.setMargins(Screen.dp(22), 0, Screen.dp(22), Screen.dp(8)); content.addView(view, params); return view;
    }
    int quantity () { return button instanceof TdApi.KeyboardButtonTypeRequestUsers ? Math.min(10, Math.max(1, ((TdApi.KeyboardButtonTypeRequestUsers) button).maxQuantity)) : 1; }
    boolean accepts (TdApi.Chat chat) {
      if (button instanceof TdApi.KeyboardButtonTypeRequestUsers) return chat != null && chat.type instanceof TdApi.ChatTypePrivate && MiniAppRequestedPeerPolicy.user((TdApi.KeyboardButtonTypeRequestUsers) button, tdlib.cache().user(((TdApi.ChatTypePrivate) chat.type).userId));
      return button instanceof TdApi.KeyboardButtonTypeRequestChat && chat != null && MiniAppRequestedPeerPolicy.chat((TdApi.KeyboardButtonTypeRequestChat) button, chat, tdlib.chatStatus(chat.id), tdlib.isForum(chat.id), !android.text.TextUtils.isEmpty(tdlib.chatUsername(chat)));
    }
    void picker () {
      String title = Lang.getString(R.string.SelectChat);
      if (button instanceof TdApi.KeyboardButtonTypeRequestUsers) title = Lang.getString(R.string.MiniAppChooseUsers);
      else title = Lang.getString(((TdApi.KeyboardButtonTypeRequestChat) button).chatIsChannel ? R.string.MiniAppChooseChannel : R.string.MiniAppChooseGroup);
      sheet(title); info(Lang.getString(R.string.MiniAppRequestedBy, host.botName()));
      search = input(R.string.Search, "", 256);
      ScrollView scroll = new ScrollView(host.context()); rows = new LinearLayout(host.context()); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
      content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(Screen.dp(360), host.context().getRootView().getHeight() / 2)));
      loadMore = label(Lang.getString(R.string.MiniAppLoadMore), 14); loadMore.setGravity(Gravity.CENTER); loadMore.setTextColor(Theme.getColor(ColorId.fillingPositive));
      content.addView(loadMore, new LinearLayout.LayoutParams(-1, Screen.dp(40)));
      loadMore.setOnClickListener(v -> { if (!working && !loading && interactive()) { limit += 100; load(""); } });
      if (button instanceof TdApi.KeyboardButtonTypeRequestChat) {
        TdApi.KeyboardButtonTypeRequestChat chat = (TdApi.KeyboardButtonTypeRequestChat) button;
        TextView create = label(Lang.getString(chat.chatIsChannel ? R.string.NewChannel : R.string.NewGroup), 16); create.setPadding(Screen.dp(22), Screen.dp(12), Screen.dp(22), Screen.dp(12)); create.setTextColor(Theme.getColor(ColorId.fillingPositive)); content.addView(create);
        create.setOnClickListener(v -> { if (!working && interactive()) createChat(); });
      }
      submit = button(Lang.getString(R.string.Share), this::checkSelection); submit.setEnabled(false);
      show();
      search.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged (CharSequence s, int start, int before, int count) {
          searchGeneration++; if (searchTask != null) search.removeCallbacks(searchTask);
          searchTask = () -> load(s.toString()); search.postDelayed(searchTask, 250);
        }
        @Override public void afterTextChanged (Editable s) { }
      });
      scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
        if (current() && search.length() == 0 && scroll.getScrollY() > 0 && scroll.getScrollY() + scroll.getHeight() >= rows.getHeight() - Screen.dp(80) && !listComplete && !working && !loading) { limit += 100; load(""); }
      });
      load("");
    }
    void load (String query) { load(query, true); }
    void load (String query, boolean fetch) {
      final int generation = ++searchGeneration;
      loading = true; loadMore.setEnabled(false); loadMore.setVisibility(query.isEmpty() && !listComplete ? View.VISIBLE : View.GONE);
      TdApi.Function<?> function = query.isEmpty() ? new TdApi.GetChats(new TdApi.ChatListMain(), limit) : new TdApi.SearchChats(query, null, 100);
      send(function, result -> {
        if (generation != searchGeneration || rows == null) return;
        loading = false; loadMore.setEnabled(true);
        if (!(result instanceof TdApi.Chats)) { error(result); return; }
        releaseAvatars(); rows.removeAllViews();
        LinkedHashMap<Long, TdApi.Chat> found = new LinkedHashMap<>();
        for (long id : ((TdApi.Chats) result).chatIds) { TdApi.Chat chat = tdlib.chat(id); if (accepts(chat)) found.put(id, chat); }
        render(found);
        if (button instanceof TdApi.KeyboardButtonTypeRequestUsers) send(new TdApi.SearchContacts(query, 100), contacts -> {
          if (generation != searchGeneration || !(contacts instanceof TdApi.Users)) return;
          for (long userId : ((TdApi.Users) contacts).userIds) {
            TdApi.User user = tdlib.cache().user(userId);
            if (!MiniAppRequestedPeerPolicy.user((TdApi.KeyboardButtonTypeRequestUsers) button, user)) continue;
            send(new TdApi.CreatePrivateChat(userId, false), value -> {
              if (generation == searchGeneration && value instanceof TdApi.Chat && !found.containsKey(((TdApi.Chat) value).id)) { found.put(((TdApi.Chat) value).id, (TdApi.Chat) value); render(found); }
            });
          }
        });
        if (fetch && query.isEmpty() && !listComplete && ((TdApi.Chats) result).chatIds.length < limit) {
          loading = true; loadMore.setEnabled(false);
          send(new TdApi.LoadChats(new TdApi.ChatListMain(), 100), loaded -> {
            if (generation != searchGeneration) return;
            listComplete = loaded instanceof TdApi.Error && ((TdApi.Error) loaded).code == 404;
            // Refresh after updates even if filtering left no rows to scroll. A button also permits retry.
            load(query, false);
          });
        }
      });
    }
    void render (LinkedHashMap<Long, TdApi.Chat> chats) {
      releaseAvatars(); rows.removeAllViews();
      for (TdApi.Chat chat : chats.values()) {
        LinearLayout row = new LinearLayout(host.context()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Screen.dp(16), 0, Screen.dp(16), 0);
        AvatarView avatar = new AvatarView(host.context()); avatar.setChat(tdlib, chat); avatars.add(avatar); row.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
        TextView name = label(chat.title, 16); name.setSingleLine(); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMarginStart(Screen.dp(12)); row.addView(name, p);
        CheckBoxView check = new CheckBoxView(host.context()); check.setCircular(true, false); check.setChecked(selected.containsKey(chat.id), false); check.setClickable(false); row.addView(check, new LinearLayout.LayoutParams(Screen.dp(24), Screen.dp(24)));
        row.setContentDescription(chat.title); row.setOnClickListener(v -> {
          if (working || !accepts(chat)) return;
          if (selected.containsKey(chat.id)) selected.remove(chat.id);
          else { if (quantity() == 1) selected.clear(); if (selected.size() < quantity()) selected.put(chat.id, chat); }
          render(chats); submit.setEnabled(!selected.isEmpty()); submit.setText(selected.isEmpty() ? Lang.getString(R.string.Share) : Lang.getString(R.string.MiniAppShareCount, selected.size()));
        });
        rows.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(64)));
      }
    }
    TdApi.Function<?> share (boolean onlyCheck) {
      if (button instanceof TdApi.KeyboardButtonTypeRequestUsers) {
        long[] users = new long[selected.size()]; int i = 0;
        for (TdApi.Chat chat : selected.values()) users[i++] = ((TdApi.ChatTypePrivate) chat.type).userId;
        return new TdApi.ShareUsersWithBot(source, ((TdApi.KeyboardButtonTypeRequestUsers) button).id, users, onlyCheck);
      }
      return new TdApi.ShareChatWithBot(source, ((TdApi.KeyboardButtonTypeRequestChat) button).id, selected.keySet().iterator().next(), onlyCheck);
    }
    void checkSelection () {
      if (selected.isEmpty() || selected.size() > quantity()) return;
      for (TdApi.Chat chat : selected.values()) if (!accepts(chat)) { error(null); return; }
      working = true; submit.setEnabled(false);
      send(share(true), result -> {
        if (!(result instanceof TdApi.Ok)) { error(result); return; }
        if (button instanceof TdApi.KeyboardButtonTypeRequestChat) checkBotMembership(); else confirm();
      });
    }
    void checkBotMembership () {
      TdApi.KeyboardButtonTypeRequestChat requested = (TdApi.KeyboardButtonTypeRequestChat) button;
      if (!requested.botIsMember && requested.botAdministratorRights == null) { confirm(); return; }
      TdApi.Chat chat = selected.values().iterator().next();
      send(new TdApi.GetChatMember(chat.id, new TdApi.MessageSenderUser(host.botId())), result -> {
        TdApi.ChatMemberStatus botStatus = result instanceof TdApi.ChatMember ? ((TdApi.ChatMember) result).status : null;
        TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(chat.id);
        boolean permitted = requested.botAdministratorRights != null ?
          MiniAppRequestedPeerPolicy.rights(botStatus, requested.botAdministratorRights) || myStatus != null && TD.canPromoteMembers(myStatus) && MiniAppRequestedPeerPolicy.rights(myStatus, requested.botAdministratorRights) :
          MiniAppRequestedPeerPolicy.member(botStatus) || tdlib.canInviteUsers(chat);
        if (permitted) confirm(); else error(null);
      });
    }
    void confirm () {
      sheet(Lang.getString(R.string.MiniAppSharePeerTitle));
      StringBuilder names = new StringBuilder(); for (TdApi.Chat chat : selected.values()) { if (names.length() > 0) names.append(", "); names.append(chat.title); }
      info(Lang.getString(R.string.MiniAppSharePeerConfirm, names.toString(), host.botName()));
      if (button instanceof TdApi.KeyboardButtonTypeRequestChat) {
        TdApi.KeyboardButtonTypeRequestChat requested = (TdApi.KeyboardButtonTypeRequestChat) button;
        if (requested.botIsMember || requested.botAdministratorRights != null) info(Lang.getString(R.string.MiniAppSharePeerAddBot, host.botName()));
        if (requested.botAdministratorRights != null) { info(Lang.getString(R.string.MiniAppGrantBotRights, host.botName())); rightsInfo(requested.botAdministratorRights); }
      }
      submit = button(Lang.getString(R.string.Share), this::submitShare); show();
    }
    void createChat () {
      TdApi.KeyboardButtonTypeRequestChat requested = (TdApi.KeyboardButtonTypeRequestChat) button;
      sheet(Lang.getString(requested.chatIsChannel ? R.string.NewChannel : R.string.NewGroup));
      EditText name = input(requested.chatIsChannel ? R.string.ChannelName : R.string.GroupName, createdChat != null ? createdChat.title : "", 128);
      EditText username = requested.restrictChatHasUsername && requested.chatHasUsername ? input(R.string.Username, "", 32) : null;
      info(Lang.getString(R.string.MiniAppCreateAndShareInfo, host.botName()));
      if (requested.botAdministratorRights != null) { info(Lang.getString(R.string.MiniAppGrantBotRights, host.botName())); rightsInfo(requested.botAdministratorRights); }
      else if (requested.botIsMember) info(Lang.getString(R.string.MiniAppSharePeerAddBot, host.botName()));
      submit = button(Lang.getString(R.string.MiniAppCreateAndShare), () -> {
        String title = name.getText().toString().trim(), handle = username != null ? username.getText().toString().trim().replaceFirst("^@", "") : "";
        if (title.isEmpty()) { name.setError(Lang.getString(R.string.GroupName)); return; }
        if (username != null && handle.isEmpty()) { username.setError(Lang.getString(R.string.UsernameInvalid)); return; }
        working = true; submit.setEnabled(false);
        Runnable create = () -> {
          if (createdChat != null) { finishCreatedChat(handle); return; }
          send(new TdApi.CreateNewSupergroupChat(title, requested.restrictChatIsForum && requested.chatIsForum, requested.chatIsChannel, "", null, 0, false), result -> {
            if (!(result instanceof TdApi.Chat)) { error(result); return; }
            createdChat = (TdApi.Chat) result; name.setEnabled(false); finishCreatedChat(handle);
          });
        };
        if (username != null) send(new TdApi.CheckChatUsername(createdChat == null ? 0 : createdChat.id, handle), result -> { if (result instanceof TdApi.CheckChatUsernameResultOk) create.run(); else error(new TdApi.Error(400, Lang.getString(R.string.UsernameInvalid))); });
        else create.run();
      }); show();
    }
    void finishCreatedChat (String username) {
      Runnable share = () -> {
        selected.clear(); selected.put(createdChat.id, createdChat);
        send(share(true), checked -> { if (!(checked instanceof TdApi.Ok)) { error(checked); return; } submitShare(); });
      };
      if (!username.isEmpty() && createdChat.type instanceof TdApi.ChatTypeSupergroup) send(new TdApi.SetSupergroupUsername(((TdApi.ChatTypeSupergroup) createdChat.type).supergroupId, username), result -> { if (result instanceof TdApi.Ok) share.run(); else error(result); });
      else share.run();
    }
  }

  private TextView label (String text, int size) {
    TextView view = new TextView(host.context()); view.setText(text); view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size); view.setTextColor(Theme.textAccentColor()); return view;
  }
  private static String rightsDescription (TdApi.ChatAdministratorRights rights) {
    List<String> names = new ArrayList<>();
    if (rights.canManageChat) names.add(Lang.getString(R.string.MiniAppRightManage));
    if (rights.canChangeInfo) names.add(Lang.getString(R.string.MiniAppRightInfo));
    if (rights.canPostMessages) names.add(Lang.getString(R.string.MiniAppRightPost));
    if (rights.canEditMessages) names.add(Lang.getString(R.string.MiniAppRightEdit));
    if (rights.canDeleteMessages) names.add(Lang.getString(R.string.MiniAppRightDelete));
    if (rights.canInviteUsers) names.add(Lang.getString(R.string.MiniAppRightInvite));
    if (rights.canRestrictMembers) names.add(Lang.getString(R.string.MiniAppRightRestrict));
    if (rights.canPinMessages) names.add(Lang.getString(R.string.MiniAppRightPin));
    if (rights.canManageTopics) names.add(Lang.getString(R.string.MiniAppRightTopics));
    if (rights.canPromoteMembers) names.add(Lang.getString(R.string.MiniAppRightPromote));
    if (rights.canManageVideoChats) names.add(Lang.getString(R.string.MiniAppRightCalls));
    if (rights.canPostStories) names.add(Lang.getString(R.string.MiniAppRightPostStories));
    if (rights.canEditStories) names.add(Lang.getString(R.string.MiniAppRightEditStories));
    if (rights.canDeleteStories) names.add(Lang.getString(R.string.MiniAppRightDeleteStories));
    if (rights.canManageDirectMessages) names.add(Lang.getString(R.string.MiniAppRightDirect));
    if (rights.canManageTags) names.add(Lang.getString(R.string.MiniAppRightTags));
    if (rights.canSendWelcomeMessages) names.add(Lang.getString(R.string.MiniAppRightWelcome));
    if (rights.isAnonymous) names.add(Lang.getString(R.string.MiniAppRightAnonymous));
    return android.text.TextUtils.join("\n", names);
  }
}
