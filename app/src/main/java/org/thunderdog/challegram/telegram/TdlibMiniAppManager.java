/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.telegram;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.miniapps.MiniAppController;
import org.thunderdog.challegram.miniapps.MiniAppLaunchRequest;
import org.thunderdog.challegram.miniapps.MiniAppPlatform;
import org.thunderdog.challegram.miniapps.MiniAppTabs;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import tgx.td.ChatId;

/** Account-scoped entry point. All UI state and results are serialized on the UI thread. */
public final class TdlibMiniAppManager implements CleanupStartupDelegate {
  private final Tdlib tdlib;
  private final List<MiniAppController> sessions = new ArrayList<>();
  private final WeakHashMap<TdlibDelegate, Long> pending = new WeakHashMap<>();
  private long nextRequest;
  private int epoch;
  private SharedPreferences consent;
  private final WeakHashMap<org.thunderdog.challegram.BaseActivity, MiniAppTabs> tabs = new WeakHashMap<>();

  public TdlibMiniAppManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    tdlib.listeners().addCleanupListener(this);
  }

  public void open (TdlibDelegate context, MiniAppLaunchRequest request) {
    open(context, request, null);
  }

  public void open (TdlibDelegate context, MiniAppLaunchRequest request, Runnable onOpened) {
    if (context == null || context.tdlib() != tdlib || !alive(context)) return;
    if (!MiniAppPlatform.isSupported()) {
      UI.showToast(R.string.MiniAppWebViewUnavailable, Toast.LENGTH_LONG);
      return;
    }
    if (ChatId.isSecret(request.chatId)) {
      UI.showToast(R.string.MiniAppSecretChat, Toast.LENGTH_LONG);
      return;
    }
    if (!request.forceConfirmation) {
      for (MiniAppController session : sessions) {
        if (session.context() == context.context() && session.canRestore(request)) {
          session.restore();
          if (onOpened != null) onOpened.run();
          return;
        }
      }
    }
    final long token = ++nextRequest;
    final int generation = epoch;
    pending.put(context, token);
    tdlib.send(new TdApi.GetUser(request.botUserId), (user, error) -> tdlib.ui().post(() -> {
      if (!current(context, token, generation)) return;
      if (error != null) { UI.showError(error); return; }
      if (!(user.type instanceof TdApi.UserTypeBot)) return;
      if (Settings.instance().needRestrictContent() && user.restrictionInfo != null && !user.restrictionInfo.restrictionReason.isEmpty()) {
        UI.showToast(user.restrictionInfo.restrictionReason, Toast.LENGTH_LONG);
        return;
      }
      if (request.source == MiniAppLaunchRequest.Source.DIRECT_LINK) {
        tdlib.send(new TdApi.SearchWebApp(request.botUserId, request.shortName), (found, searchError) -> tdlib.ui().post(() -> {
          if (!current(context, token, generation)) return;
          if (searchError != null) { UI.showError(searchError); return; }
          confirm(context, request, token, generation, found.requestWriteAccess, found.skipConfirmation, onOpened);
        }));
      } else {
        confirm(context, request, token, generation, false, false, onOpened);
      }
    }));
  }

  private void confirm (TdlibDelegate context, MiniAppLaunchRequest request, long token, int generation, boolean requestWriteAccess, boolean skipConfirmation, Runnable onOpened) {
    if (!current(context, token, generation)) return;
    if (consent == null) consent = context.context().getSharedPreferences("miniapp_consent_" + tdlib.myUserId(), Context.MODE_PRIVATE);
    boolean trusted = consent.getBoolean(Long.toString(request.botUserId), false);
    if (!request.forceConfirmation && !requestWriteAccess && (trusted || skipConfirmation)) {
      launch(context, request, token, generation, false, onOpened);
      return;
    }
    CheckBox writeAccess = new CheckBox(context.context());
    writeAccess.setText(Lang.getString(R.string.MiniAppAllowMessages));
    writeAccess.setPadding(Screen.dp(24), Screen.dp(8), Screen.dp(24), Screen.dp(8));
    writeAccess.setChecked(false);
    AlertDialog.Builder builder = new AlertDialog.Builder(context.context())
      .setTitle(tdlib.cache().userName(request.botUserId))
      .setMessage(Lang.getString(R.string.MiniAppDisclaimer))
      .setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> { if (current(context, token, generation)) pending.remove(context); })
      .setPositiveButton(Lang.getString(R.string.Open), (dialog, which) -> {
        if (!current(context, token, generation)) return;
        consent.edit().putBoolean(Long.toString(request.botUserId), true).apply();
        launch(context, request, token, generation, requestWriteAccess && writeAccess.isChecked(), onOpened);
      });
    if (requestWriteAccess) builder.setView(writeAccess);
    ViewController<?> parent = context instanceof ViewController ? (ViewController<?>) context : context.context().navigation().getCurrentStackItem();
    if (parent != null) parent.showAlert(builder); else builder.show();
  }

  private void launch (TdlibDelegate context, MiniAppLaunchRequest request, long token, int generation, boolean allowWriteAccess, Runnable onOpened) {
    if (!current(context, token, generation)) return;
    pending.remove(context);
    MiniAppController controller = new MiniAppController(context.context(), tdlib, request, onOpened);
    sessions.add(controller);
    controller.open(allowWriteAccess);
  }

  public void onClosed (MiniAppController controller) {
    sessions.remove(controller);
    updateTabs(controller.context());
  }

  public void updateTabs (org.thunderdog.challegram.BaseActivity activity) {
    MiniAppTabs strip = tabs.get(activity);
    if (strip == null) {
      if (sessions.stream().noneMatch(controller -> controller.context() == activity && controller.isMinimized())) return;
      tabs.put(activity, strip = new MiniAppTabs(activity));
    }
    strip.update(sessions);
    if (sessions.stream().noneMatch(controller -> controller.context() == activity && controller.isMinimized())) tabs.remove(activity);
    updateTabsVisibility(activity);
    for (MiniAppController controller : sessions) if (controller.context() == activity) controller.onTabsChanged();
  }

  public void updateTabsVisibility (org.thunderdog.challegram.BaseActivity activity) {
    MiniAppTabs strip = tabs.get(activity);
    if (strip != null) strip.setVisibility(sessions.stream().anyMatch(controller -> controller.context() == activity && controller.isShowingFullscreen()) ? android.view.View.GONE : android.view.View.VISIBLE);
  }

  public int tabsHeight (org.thunderdog.challegram.BaseActivity activity) {
    long count = sessions.stream().filter(controller -> controller.context() == activity && controller.isMinimized()).count();
    return count == 0 ? 0 : Screen.dp(count > 1 ? 68 : 60);
  }

  public void onMinimized (MiniAppController controller) {
    // The most recently minimized app is the front card, including restored older sessions.
    sessions.remove(controller);
    sessions.add(controller);
    updateTabs(controller.context());
  }

  public void activate (MiniAppController selected) {
    for (MiniAppController controller : new ArrayList<>(sessions)) {
      if (controller != selected && controller.context() == selected.context() && controller.isShowing()) controller.minimize();
    }
  }

  public void raiseTabs (org.thunderdog.challegram.BaseActivity activity) {
    MiniAppTabs strip = tabs.get(activity);
    if (strip != null) strip.raiseAboveSheet();
  }

  public void openMainApp (TdlibDelegate context, long botUserId, long chatId) {
    open(context, new MiniAppLaunchRequest(MiniAppLaunchRequest.Source.MAIN_APP, botUserId, chatId, "", "", "", "", null, null, null, false));
  }

  public void onWebAppMessageSent (long launchId) {
    tdlib.ui().post(() -> {
      for (MiniAppController session : new ArrayList<>(sessions)) {
        if (launchId != 0 && session.launchId() == launchId) session.close();
      }
    });
  }

  public void openBotMenu (MessagesController controller, TdApi.BotMenuButton menu) {
    final long chatId = controller.getChatId();
    tdlib.send(new TdApi.GetInternalLinkType(menu.url), (type, error) -> tdlib.ui().post(() -> {
      if (!alive(controller) || controller.getChatId() != chatId) return;
      if (type instanceof TdApi.InternalLinkTypeWebApp || type instanceof TdApi.InternalLinkTypeMainWebApp) {
        openLink(controller, type, null);
      } else {
        controller.openMiniApp(MiniAppLaunchRequest.Source.BOT_MENU, tdlib.chatUserId(controller.getChatId()), menu.url, menu.text);
      }
    }));
  }

  public void openLink (TdlibDelegate context, TdApi.InternalLinkType type, TdlibUi.UrlOpenParameters options) {
    final boolean main = type instanceof TdApi.InternalLinkTypeMainWebApp;
    final String username = main ? ((TdApi.InternalLinkTypeMainWebApp) type).botUsername : ((TdApi.InternalLinkTypeWebApp) type).botUsername;
    final String shortName = main ? "" : ((TdApi.InternalLinkTypeWebApp) type).webAppShortName;
    final String startParameter = main ? ((TdApi.InternalLinkTypeMainWebApp) type).startParameter : ((TdApi.InternalLinkTypeWebApp) type).startParameter;
    final TdApi.WebAppOpenMode mode = main ? ((TdApi.InternalLinkTypeMainWebApp) type).mode : ((TdApi.InternalLinkTypeWebApp) type).mode;
    final long chatId = context instanceof MessagesController ? ((MessagesController) context).getChatId() :
      options != null && options.messageId != null ? options.messageId.getChatId() : 0;
    final int generation = epoch;
    tdlib.send(new TdApi.SearchPublicChat(username), (chat, error) -> tdlib.ui().post(() -> {
      if (epoch != generation || !alive(context)) return;
      if (error != null) { UI.showError(error); return; }
      long botId = tdlib.chatUserId(chat);
      if (botId == 0) return;
      open(context, new MiniAppLaunchRequest(main ? MiniAppLaunchRequest.Source.MAIN_APP : MiniAppLaunchRequest.Source.DIRECT_LINK,
        botId, chatId, "", "", shortName, startParameter, null, null, mode, options != null && options.requireOpenPrompt));
    }));
  }

  private static boolean alive (TdlibDelegate context) {
    return context != null && context.tdlib().isCurrent() && !context.context().isFinishing() && (!(context instanceof ViewController) || !((ViewController<?>) context).isDestroyed());
  }

  private boolean current (TdlibDelegate context, long token, int generation) {
    Long active = pending.get(context);
    return generation == epoch && active != null && active == token && alive(context);
  }

  @Override
  public void onPerformUserCleanup () {
    tdlib.ui().post(() -> {
      if (consent != null) { consent.edit().clear().apply(); consent = null; }
      reset();
    });
  }

  @Override
  public void onPerformRestart () { tdlib.ui().post(this::reset); }

  private void reset () {
    epoch++;
    pending.clear();
    for (MiniAppController session : new ArrayList<>(sessions)) session.close();
  }
}
