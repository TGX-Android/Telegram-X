package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.SessionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.ProgressComponentView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

/**
 * Date: 17/11/2016
 * Author: default
 */

public class SettingsSessionsController extends RecyclerViewController<Void> implements View.OnClickListener, OptionDelegate, CameraController.QrCodeListener, SessionListener {
  public SettingsSessionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_sessions;
  }

  private Tdlib.SessionsInfo sessions;

  private void setSessions (Tdlib.SessionsInfo sessions) {
    this.sessions = sessions;
  }

  private SettingsAdapter adapter;

  private void buildCells () {
    if (sessions == null || sessions.currentSession == null) {
      return;
    }

    ArrayList<ListItem> items = new ArrayList<>();

    if (tdlib.allowQrLoginCamera()) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_qrLogin, R.drawable.xt3000_baseline_qrcode_scan_24, R.string.ScanQR).setTextColorId(R.id.theme_color_textNeutral));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, R.string.ThisDevice));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SESSION, R.id.btn_currentSession, 0, 0));

    if (sessions.onlyCurrent) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SESSIONS_EMPTY));
    } else {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_terminateAllSessions, R.drawable.baseline_cancel_24, R.string.TerminateAllSessions).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      boolean first = true;

      // Incomplete login attempts
      for (TdApi.Session session : sessions.incompleteLoginAttempts) {
        if (first) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SessionsIncompleteTitle));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_SESSION, R.id.btn_session, 0, 0).setLongId(session.id).setData(session));
      }
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SessionsIncompleteInfo));

        first = true;
      }

      // Other sessions
      for (TdApi.Session session : sessions.otherActiveSessions) {
        if (first) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, sessions.incompleteLoginAttempts.length > 0 ? R.string.ActiveDevices : R.string.OtherDevices));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_SESSION, R.id.btn_session, 0, 0).setLongId(session.id).setData(session));
      }
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }

    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  private void clearSessionList () {
    if (sessions == null || sessions.onlyCurrent) {
      return;
    }
    List<ListItem> items = adapter.getItems();

    int index = -1;
    final int itemCount = items.size();
    for (int i = 0; i < itemCount; i++) {
      if (items.get(i).getViewType() == ListItem.TYPE_SEPARATOR_FULL) {
        index = i;
        break;
      }
    }

    if (index != -1) {
      for (int i = itemCount - 1; i >= index; i--) {
        items.remove(i);
      }
      adapter.notifyItemRangeRemoved(index, itemCount - index);

      index = items.size();
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SESSIONS_EMPTY));
      adapter.notifyItemRangeInserted(index, 2);
    }

    sessions = new Tdlib.SessionsInfo(new TdApi.Sessions(new TdApi.Session[] {sessions.currentSession}));
  }

  private static CharSequence getTitle (TdApi.Session session) {
    return session.deviceModel;
  }

  private static CharSequence getSubtext (TdApi.Session session, boolean isFull) {
    SpannableStringBuilder b = new SpannableStringBuilder();

    Lang.SpanCreator commonCreator = isFull ? Lang.boldCreator() : null;
    Lang.SpanCreator mainCreator = null;
    Lang.SpanCreator versionCreator = isFull ? Lang.codeCreator() : null;

    if (isFull) {
      b.append(Lang.getString(R.string.session_Device, commonCreator, getTitle(session)));
    }

    if (b.length() > 0) {
      b.append('\n');
    }
    CharSequence appNameAndVersion = Lang.wrap(Strings.concat(" ",
      Lang.wrap(!StringUtils.isEmpty(session.applicationName) ? session.applicationName : "App #" + session.apiId, mainCreator),
      Lang.wrap(session.applicationVersion, versionCreator)
    ), commonCreator);
    if (isFull) {
      b.append(Lang.getCharSequence(R.string.session_App, appNameAndVersion));
    } else {
      b.append(appNameAndVersion);
    }

    CharSequence platformAndVersion = Lang.wrap(Strings.concat(" ",
      Lang.wrap(session.platform, mainCreator),
      Lang.wrap(session.systemVersion, versionCreator)
    ), commonCreator);
    if (!StringUtils.isEmpty(platformAndVersion)) {
      if (b.length() > 0) {
        b.append('\n');
      }
      if (isFull) {
        b.append(Lang.getCharSequence(R.string.session_System, platformAndVersion));
      } else {
        b.append(platformAndVersion);
      }
    }

    if (isFull || session.isCurrent) {
      b.append('\n').append(Lang.getString(R.string.SessionLogInDate, versionCreator, Lang.getTimestamp(session.logInDate, TimeUnit.SECONDS)));
    }

    if (isFull) {
      b.append('\n').append(Lang.getString(R.string.SessionLastActiveDate, versionCreator, Lang.getTimestamp(session.lastActiveDate, TimeUnit.SECONDS)));
      b.append('\n').append(Strings.concatIpLocation(Lang.codify(session.ip), session.country));
    }
    return b;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getViewType() == ListItem.TYPE_VALUED_SETTING_COMPACT) {
          view.forcePadding(Screen.dp(63f), 0);
        }
        int iconColorId = item.getTextColorId(ThemeColorId.NONE);
        if (iconColorId == R.id.theme_color_textNegative) {
          iconColorId = R.id.theme_color_iconNegative;
        }
        view.setIconColorId(iconColorId);
        switch (item.getId()) {
          case R.id.btn_terminateAllSessions: {
            view.setData(R.string.ClearOtherSessionsHelp);
            break;
          }
          case R.id.btn_qrLogin: {
            view.setData(Lang.getStringSecure(R.string.ScanQRLogInInfo));
            break;
          }
        }
      }

      @Override
      protected void setSession (ListItem item, int position, RelativeLayout parent, boolean isUpdate, TextView timeView, TextView titleView, TextView subtextView, TextView locationView, ProgressComponentView progressView, AvatarView avatarView) {
        switch (item.getId()) {
          case R.id.btn_currentSession: {
            parent.setTag(null);
            timeView.setText("");
            titleView.setText(getTitle(sessions.currentSession));
            subtextView.setText(getSubtext(sessions.currentSession, false));
            locationView.setText(Strings.concatIpLocation(sessions.currentSession.ip, sessions.currentSession.country));
            progressView.forceFactor(0f);
            parent.setEnabled(false);
            break;
          }
          case R.id.btn_session: {
            TdApi.Session session = (TdApi.Session) item.getData();
            parent.setTag(session);
            String date = Lang.timeOrDateShort(session.lastActiveDate, TimeUnit.SECONDS);
            if (!DateUtils.isToday(session.lastActiveDate, TimeUnit.SECONDS)) {
              date += " " + Lang.time(session.lastActiveDate, TimeUnit.SECONDS);
            }
            timeView.setText(date);
            titleView.setText(getTitle(session));
            subtextView.setText(getSubtext(session, false));
            locationView.setText(Strings.concatIpLocation(session.ip, session.country));

            final boolean inProgress = terminatingSessions != null && terminatingSessions.get(session.id) != null;
            parent.setEnabled(!inProgress);
            if (isUpdate) {
              progressView.animateFactor(inProgress ? 1f : 0f);
            } else {
              progressView.forceFactor(inProgress ? 1f : 0f);
            }

            break;
          }
        }
      }
    };
    if (sessions != null) {
      buildCells();
    }

    if (getArguments() == null) {
      RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
        @Override
        public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
          if (position < 0 || position >= adapter.getItems().size()) {
            return false;
          }

          ListItem item = adapter.getItems().get(position);
          return item.getId() == R.id.btn_session && !terminatingAll && (terminatingSessions == null || terminatingSessions.get(item.getLongId()) == null);
        }

        @Override
        public void onRemove (RecyclerView.ViewHolder viewHolder) {
          killSession((TdApi.Session) viewHolder.itemView.getTag(), false);
        }
      });
    }

    if (getArguments() == null) {
      requestActiveSessions();
    }

    recyclerView.setAdapter(adapter);
    tdlib.listeners().subscribeToSessionUpdates(this);
  }

  @Override
  public void onSessionListChanged (Tdlib tdlib, boolean isWeakGuess) {
    requestActiveSessions();
  }

  @Override
  public void onSessionCreatedViaQrCode (Tdlib tdlib, TdApi.Session session) {
    runOnUiThreadOptional(() -> {
      if (indexOfSession(session.id) != -1)
        return;
      TdApi.Session[] newSessions = new TdApi.Session[sessions.allSessions.length + 1];
      System.arraycopy(sessions.allSessions, 0, newSessions, 0, sessions.allSessions.length);
      newSessions[sessions.allSessions.length] = session;
      Td.sort(newSessions);
      this.sessions = new Tdlib.SessionsInfo(new TdApi.Sessions(newSessions));
      buildCells();
      UI.showCustomToast(Lang.getStringSecure(session.isPasswordPending ? R.string.ScanQRAuthorizedToastPasswordPending : R.string.ScanQRAuthorizedToast, Lang.boldCreator(), session.applicationName), Toast.LENGTH_LONG, 0);
    });
  }

  @Override
  public void onSessionTerminated (Tdlib tdlib, TdApi.Session session) {
    runOnUiThreadOptional(() -> {
      if (terminatingSessions != null) {
        terminatingSessions.remove(session.id);
      }
      removeSessionFromList(session);
    });
  }

  @Override
  public void onAllOtherSessionsTerminated (Tdlib tdlib, TdApi.Session currentSession) {
    runOnUiThreadOptional(() -> {
      if (terminatingSessions != null) {
        terminatingSessions.clear();
      }
      terminatingAll = false;
      clearSessionList();
      if (sessions != null) {
        for (TdApi.Session session : sessions.allSessions) {
          updateSessionById(session.id);
        }
      }
    });
  }

  private void requestActiveSessions () {
    tdlib.getSessions(false, sessionsInfo -> {
      if (sessionsInfo != null) {
        runOnUiThreadOptional(() -> {
          setSessions(sessionsInfo);
          buildCells();
        });
      }
    });
  }

  private boolean terminatingAll;

  private void terminateOtherSessions () {
    if (sessions == null || sessions.onlyCurrent || terminatingAll) {
      return;
    }
    terminatingAll = true;

    if (terminatingSessions == null) {
      terminatingSessions = new LongSparseArray<>();
    }
    for (TdApi.Session session : sessions.allSessions) {
      if (!session.isCurrent) {
        terminatingSessions.put(session.id, session);
        updateSessionById(session.id);
      }
    }

    tdlib.terminateAllOtherSessions(sessions.currentSession, error -> {
      if (error != null) {
        UI.showError(error); // TODO tooltip?
      }
    });
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return sessions == null;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 400l;
  }

  private LongSparseArray<TdApi.Session> terminatingSessions;

  private void updateSessionById (long id) {
    int adapterPosition = indexOfSessionInAdapter(id);
    if (adapterPosition != -1) {
      adapter.updateSessionByPosition(adapterPosition);
    }
  }

  private void terminateSession (final TdApi.Session session) {
    if (terminatingSessions == null) {
      terminatingSessions = new LongSparseArray<>();
    } else if (terminatingSessions.get(session.id) != null) {
      return;
    }

    terminatingSessions.put(session.id, session);

    final int adapterPosition = indexOfSessionInAdapter(session.id);
    if (adapterPosition != -1) {
      adapter.updateSessionByPosition(adapterPosition);
    }

    tdlib.terminateSession(session, error -> {
      if (error != null) {
        if (terminatingSessions != null) {
          runOnUiThreadOptional(() -> {
            terminatingSessions.remove(session.id);

            int newAdapterPosition = indexOfSessionInAdapter(session.id);
            if (newAdapterPosition != -1) {
              adapter.updateSessionByPosition(newAdapterPosition);
            }
          });
        }

        UI.showError(error); // TODO tooltip?
      }
    });
  }

  private int indexOfSessionInAdapter (long sessionId) {
    int index = indexOfSession(sessionId);
    return index != -1 ? adapter.indexOfViewByData(sessions.allSessions[index]) : -1;
  }

  private int indexOfSession (long sessionId) {
    int i = 0;
    for (TdApi.Session check : sessions.allSessions) {
      if (check.id == sessionId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private void removeSessionFromList (TdApi.Session session) {
    if (sessions == null || sessions.onlyCurrent)
      return;
    if (sessions.allSessions.length == 1) {
      clearSessionList();
      return;
    }

    int index = indexOfSession(session.id);
    if (index == -1) {
      return;
    }

    TdApi.Session[] newSessions = ArrayUtils.removeElement(sessions.allSessions, index, new TdApi.Session[sessions.allSessions.length - 1]);
    this.sessions = new Tdlib.SessionsInfo(new TdApi.Sessions(newSessions));

    final int itemIndex = adapter.indexOfViewByData(session);
    if (itemIndex == -1)
      return;

    boolean first = adapter.getItems().get(itemIndex - 1).getViewType() == ListItem.TYPE_SHADOW_TOP;
    boolean last = adapter.getItems().get(itemIndex + 1).getViewType() == ListItem.TYPE_SHADOW_BOTTOM;
    if (first && last) { // section removed
      int count = 4;
      if (itemIndex + 2 < adapter.getItems().size() && adapter.getItems().get(itemIndex + 2).getViewType() == ListItem.TYPE_DESCRIPTION)
        count++;
      adapter.removeRange(itemIndex - 1 /*shadow*/ - 1 /*title*/, count);
    } else if (first) {
      adapter.removeRange(itemIndex, 2);
    } else {
      adapter.removeRange(itemIndex - 1, 2);
    }
  }

  private TdApi.Session sessionToTerminate;

  private void killSession (final TdApi.Session session, boolean alert) {
    showOptions(Strings.concat("\n\n", Lang.boldify(Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSessionQuestion : R.string.TerminateSessionQuestion)), getSubtext(session, true)), new int[]{R.id.btn_terminateSession, R.id.btn_cancel, R.id.btn_copyText}, new String[]{Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession), Lang.getString(R.string.Cancel), Lang.getString(R.string.Copy)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24, R.drawable.baseline_content_copy_24}, (itemView, id) -> {
      switch (id) {
        case R.id.btn_terminateSession: {
          terminateSession(session);
          break;
        }
        case R.id.btn_copyText: {
          UI.copyText(getSubtext(session, true), R.string.CopiedText);
          break;
        }
      }
      return true;
    });
    /*if (alert) {

    } else {
      sessionToTerminate = session;
      showOptions(null, new int[] {R.id.btn_terminateSession, R.id.btn_cancel}, new String[] {Lang.getString(R.string.TerminateSession), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24});
    }*/
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    if (id == R.id.btn_terminateSession && sessionToTerminate != null) {
      terminateSession(sessionToTerminate);
      sessionToTerminate = null;
    }
    return true;
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_terminateAllSessions: {
        showOptions(Lang.getString(R.string.AreYouSureSessions), new int[]{R.id.btn_terminateAllSessions, R.id.btn_cancel}, new String[]{Lang.getString(R.string.TerminateAllSessions), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_terminateAllSessions) {
            terminateOtherSessions();
          }
          return true;
        });
        break;
      }
      case R.id.btn_qrLogin: {
        openInAppCamera(new CameraOpenOptions().anchor(v).noTrace(true).allowSystem(false).optionalMicrophone(true).mode(CameraController.MODE_QR).qrCodeListener(this));
        break;
      }
      case R.id.btn_session: {
        Object tag = v.getTag();
        if (tag instanceof TdApi.Session) {
          killSession((TdApi.Session) tag, true);
        }
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromSessionUpdates(this);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Devices);
  }

  @Override
  public void onQrCodeScanned (String qrCode) {
    if (!qrCode.startsWith("tg://")) return;
    tdlib().client().send(new TdApi.GetInternalLinkType(qrCode), result -> {
      if (result.getConstructor() == TdApi.InternalLinkTypeQrCodeAuthentication.CONSTRUCTOR) {
        tdlib.confirmQrCodeAuthentication(qrCode, null, UI::showError);
      }
    });
  }
}
