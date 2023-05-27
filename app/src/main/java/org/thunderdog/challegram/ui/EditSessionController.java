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
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.SessionIconKt;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

@SuppressLint("NonConstantResourceId")
public class EditSessionController extends EditBaseController<EditSessionController.Args> implements View.OnClickListener {
  private DoubleHeaderView headerCell;
  private SettingsAdapter adapter;

  private TdApi.Session session;
  private boolean allowSecretChats;
  private boolean allowCalls;

  public EditSessionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }

    return false;
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !hasAnyChanges();
  }

  private void checkDoneButton () {
    setDoneVisible(hasAnyChanges());
  }

  private boolean hasAnyChanges () {
    return allowSecretChats != session.canAcceptSecretChats || allowCalls != session.canAcceptCalls;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.session = args.session;
    this.allowSecretChats = args.session.canAcceptSecretChats;
    this.allowCalls = args.session.canAcceptCalls;
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_sessionPlatform || viewId == R.id.btn_sessionCountry || viewId == R.id.btn_sessionIp || viewId == R.id.btn_sessionFirstLogin || viewId == R.id.btn_sessionLastLogin) {
      UI.copyText((CharSequence) v.getTag(), R.string.CopiedText);
    } else if (viewId == R.id.btn_sessionDevice) {
      UI.copyText(session.deviceModel, R.string.CopiedText);
    } else if (viewId == R.id.btn_sessionApp) {
      UI.copyText(session.applicationName + " " + session.applicationVersion, R.string.CopiedText);
    } else if (viewId == R.id.btn_sessionAcceptSecretChats) {
      this.allowSecretChats = adapter.toggleView(v);
      adapter.updateValuedSettingById(R.id.btn_sessionAcceptSecretChats);
      checkDoneButton();
    } else if (viewId == R.id.btn_sessionAcceptCalls) {
      this.allowCalls = adapter.toggleView(v);
      adapter.updateValuedSettingById(R.id.btn_sessionAcceptCalls);
      checkDoneButton();
    } else if (viewId == R.id.btn_sessionLogout) {
      if (session.isCurrent) {
        navigateTo(new SettingsLogOutController(context, tdlib));
      } else {
        showOptions(null, new int[] {R.id.btn_terminateSession, R.id.btn_cancel}, new String[] {Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_dangerous_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_terminateSession) {
            navigateBack();
            getArgumentsStrict().sessionTerminationListener.run();
          }

          return true;
        });
      }
    }
  }

  @Override
  protected boolean onDoneClick () {
    setDoneInProgress(true);

    List<TdApi.Function<?>> functions = new ArrayList<>();

    if (allowSecretChats != session.canAcceptSecretChats) {
      functions.add(new TdApi.ToggleSessionCanAcceptSecretChats(session.id, allowSecretChats));
    }

    if (allowCalls != session.canAcceptCalls) {
      functions.add(new TdApi.ToggleSessionCanAcceptCalls(session.id, allowCalls));
    }

    tdlib.sendAll(functions.toArray(new TdApi.Function<?>[0]), (obj) -> {

    }, () -> {
      runOnUiThreadOptional(() -> {
        session.canAcceptSecretChats = allowSecretChats;
        session.canAcceptCalls = allowCalls;
        getArgumentsStrict().sessionChangeListener.runWithData(session);
        navigateBack();
      });
    });

    return true;
  }

  @Override
  public int getId () {
    return R.id.controller_editSession;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    if (!session.isPasswordPending && !session.isCurrent) {
      headerCell = new DoubleHeaderView(context());
      headerCell.setThemedTextColor(this);
      headerCell.initWithMargin(Screen.dp(49f), true);
      headerCell.setTitle(R.string.SessionDetails);
      headerCell.setSubtitle(Lang.getRelativeDate(
        session.lastActiveDate, TimeUnit.SECONDS,
        tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
        true, 60, R.string.session_LastActive, false
      ));
    }

    setDoneIcon(R.drawable.baseline_check_24);
    setInstantDoneVisible(true);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_sessionLogout) {
          view.setIconColorId(ColorId.textNegative);
        } else {
          view.setIconColorId(ColorId.icon);
        }

        final int itemId = item.getId();
        if (itemId == R.id.btn_sessionDevice) {
          view.setText(new TextWrapper(session.deviceModel, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL));
        } else if (itemId == R.id.btn_sessionApp) {
          view.setText(new TextWrapper(session.applicationName + " " + session.applicationVersion, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL));
        } else if (itemId == R.id.btn_sessionPlatform) {
          view.setData(R.string.SessionSystem);
        } else if (itemId == R.id.btn_sessionCountry) {
          view.setData(R.string.SessionLocation);
        } else if (itemId == R.id.btn_sessionIp) {
          view.setData(R.string.SessionIP);
        } else if (itemId == R.id.btn_sessionFirstLogin) {
          view.setData(session.isPasswordPending ? R.string.SessionAttempt : R.string.SessionFirstLogin);
        } else if (itemId == R.id.btn_sessionLastLogin) {
          view.setData(R.string.SessionLastLogin);
        } else if (itemId == R.id.btn_sessionLogout) {
          view.setData((session.isCurrent || session.isPasswordPending) ? null : Lang.getReverseRelativeDateBold(
            session.lastActiveDate + TimeUnit.DAYS.toSeconds(getArgumentsStrict().inactiveSessionTtlDays), TimeUnit.SECONDS,
            tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
            true, 0, R.string.session_TerminatesIn, false
          ));
        } else if (itemId == R.id.btn_sessionAcceptSecretChats) {
          view.getToggler().setRadioEnabled(allowSecretChats, isUpdate);
          view.setData(allowSecretChats ? R.string.SessionAccept : R.string.SessionReject);
        } else if (itemId == R.id.btn_sessionAcceptCalls) {
          view.getToggler().setRadioEnabled(allowCalls, isUpdate);
          view.setData(allowCalls ? R.string.SessionAccept : R.string.SessionReject);
        }

        view.setTag(item.getString());
      }
    };

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_sessionApp, R.drawable.baseline_apps_24, R.string.SessionApp, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_sessionDevice, R.drawable.baseline_devices_other_24, R.string.SessionDevice, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionPlatform, SessionIconKt.asIcon(session), (session.platform + " " + session.systemVersion).trim(), false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionCountry, R.drawable.baseline_location_on_24, StringUtils.isEmpty(session.country) ? Lang.getString(R.string.SessionLocationUnknown) : session.country, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionIp, R.drawable.baseline_router_24, StringUtils.isEmpty(session.ip) ? Lang.getString(R.string.SessionIpUnknown) : session.ip, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (!session.isPasswordPending) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SessionAccepts));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_sessionAcceptSecretChats, R.drawable.baseline_lock_24, R.string.SessionSecretChats));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_sessionAcceptCalls, R.drawable.baseline_call_24, R.string.SessionCalls));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionFirstLogin, R.drawable.baseline_exit_to_app_24, Lang.getTimestamp(session.logInDate, TimeUnit.SECONDS), false));
    if (!session.isPasswordPending && !session.isCurrent) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionLastLogin, R.drawable.baseline_history_24, Lang.getTimestamp(session.lastActiveDate, TimeUnit.SECONDS), false));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem((session.isPasswordPending || session.isCurrent) ? ListItem.TYPE_SETTING : ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionLogout, R.drawable.baseline_dangerous_24, session.isCurrent ? R.string.LogOut : (session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession)).setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    checkDoneButton();
  }

  public static class Args {
    public final TdApi.Session session;
    public final int inactiveSessionTtlDays;
    public final Runnable sessionTerminationListener;
    public final RunnableData<TdApi.Session> sessionChangeListener;

    public Args (TdApi.Session session, int inactiveSessionTtlDays, Runnable sessionTerminationListener, RunnableData<TdApi.Session> sessionChangeListener) {
      this.session = session;
      this.inactiveSessionTtlDays = inactiveSessionTtlDays;
      this.sessionTerminationListener = sessionTerminationListener;
      this.sessionChangeListener = sessionChangeListener;
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(session.isPasswordPending ? R.string.SessionAttemptDetails : R.string.SessionDetails);
  }
}
