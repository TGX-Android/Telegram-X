package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
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

import me.vkryl.core.DateUtils;
import me.vkryl.td.Td;

/**
 * Date: 17/11/2016
 * Author: default
 */

public class SettingsSessionsController extends RecyclerViewController<SettingsPrivacyController> implements SettingsPrivacyController.AuthorizationsLoadListener, View.OnClickListener, OptionDelegate {
  public SettingsSessionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_sessions;
  }

  @Override
  public void setArguments (SettingsPrivacyController args) {
    super.setArguments(args);
    TdApi.Sessions sessions = args.getSessions();
    if (sessions == null || sessions.sessions.length == 0) {
      args.setAuthorizationsLoadListener(this);
    } else {
      setSessions(sessions.sessions);
    }
  }

  private TdApi.Session currentSession;
  private ArrayList<TdApi.Session> sessions;

  private void setSessions (TdApi.Session[] sessions) {
    this.sessions = new ArrayList<>(Math.max(sessions.length - 1, 0));
    for (TdApi.Session session : sessions) {
      if (session.isCurrent) {
        currentSession = session;
      } else {
        this.sessions.add(session);
      }
    }
  }

  private SettingsAdapter adapter;

  private void buildCells () {
    if (sessions == null || currentSession == null) {
      return;
    }
    if (sessions.isEmpty()) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL),
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ThisDevice),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_SESSION, R.id.btn_currentSession, 0, 0),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_SESSIONS_EMPTY)
      }, false);
      return;
    }

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ThisDevice));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SESSION, R.id.btn_currentSession, 0, 0));

    if (sessions.isEmpty()) {
      if (tdlib.allowQrLoginCamera()) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_RED, R.id.btn_qrLogin, 0, R.string.ScanQR).setTextColorId(R.id.theme_color_textNeutral));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SESSIONS_EMPTY));
    } else {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_RED, R.id.btn_terminateAllSessions, 0, R.string.TerminateAllSessions).setTextColorId(R.id.theme_color_textNegative));
      if (tdlib.allowQrLoginCamera()) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_RED, R.id.btn_qrLogin, 0, R.string.ScanQR).setTextColorId(R.id.theme_color_textNeutral));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      boolean first = true;
      boolean isPending = false;
      for (TdApi.Session session : sessions) {
        if (!first && isPending != session.isPasswordPending) {
          first = true;
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          if (isPending) {
            items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SessionsIncompleteInfo));
          }
        }
        if (first) {
          isPending = session.isPasswordPending;
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, session.isPasswordPending ? R.string.SessionsIncompleteTitle : R.string.SessionsTitle));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_SESSION, R.id.btn_session, 0, 0).setLongId(session.id).setData(session));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  private void clearSessionList () {
    if (sessions == null || sessions.isEmpty()) {
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

    sessions.clear();
    if (getArguments() != null) {
      getArguments().updateAuthorizations(sessions, currentSession);
    }
  }

  private static String getTitle (TdApi.Session session) {
    StringBuilder b = new StringBuilder(session.applicationName.length() + session.applicationVersion.length() + 3);
    if (session.applicationName.isEmpty()) {
      b.append("Unknown App (#");
      b.append(session.apiId);
      b.append(")");
    } else {
      b.append(session.applicationName);
    }
    if (!session.applicationVersion.isEmpty()) {
      b.append(" ");
      b.append(session.applicationVersion);
    }
    return b.toString();
  }

  private static String getSubtext (TdApi.Session session, boolean full) {
    StringBuilder b = new StringBuilder(session.deviceModel.length() + session.systemVersion.length() + 2);
    b.append(getTitle(session));
    if (!session.deviceModel.isEmpty()) {
      b.append('\n').append(session.deviceModel);
    }
    if (!session.systemVersion.isEmpty() || !session.platform.isEmpty()) {
      if (b.length() > 0) {
        b.append(", ");
      }
      b.append(session.platform);
      if (!session.systemVersion.isEmpty() && !session.platform.isEmpty()) {
        b.append(" ");
      }
      b.append(session.systemVersion);
    }
    if (full || session.isCurrent)
      b.append('\n').append(Lang.getString(R.string.SessionLogInDate, Lang.getTimestamp(session.logInDate, TimeUnit.SECONDS)));
    if (full) {
      b.append('\n').append(Lang.getString(R.string.SessionLastActiveDate, Lang.getTimestamp(session.lastActiveDate, TimeUnit.SECONDS)));
      b.append('\n').append(Strings.concatIpLocation(session.ip, session.country));
    }
    return b.toString();
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_terminateAllSessions: {
            view.setData(R.string.ClearOtherSessionsHelp);
            break;
          }
          case R.id.btn_qrLogin: {
            view.setData(R.string.ScanQRLogInInfo);
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
            titleView.setText(getTitle(currentSession));
            subtextView.setText(getSubtext(currentSession, false));
            locationView.setText(Strings.concatIpLocation(currentSession.ip, currentSession.country));
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
    /*RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder.getItemViewType() == SettingItem.TYPE_SESSION) {
          SettingItem item = adapter.getItems().get(position);
          if (item.getId() != R.id.btn_currentSession && !terminatingAll && (terminatingSessions == null || terminatingSessions.get(item.getLongId()) == null)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        killSession((TdApi.Session) viewHolder.itemView.getTag(), false);
      }
    });*/

    if (getArguments() == null) {
      tdlib.client().send(new TdApi.GetActiveSessions(), object -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          switch (object.getConstructor()) {
            case TdApi.Sessions.CONSTRUCTOR: {
              TdApi.Session[] sessions = ((TdApi.Sessions) object).sessions;
              Td.sort(sessions);
              setSessions(sessions);
              buildCells();
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
            default: {
              Log.unexpectedTdlibResponse(object, TdApi.GetActiveSessions.class, TdApi.Sessions.class);
              break;
            }
          }
        }
      }));
    }

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onAuthorizationsLoaded (TdApi.Sessions sessions) {
    if (!isDestroyed()) {
      setSessions(sessions.sessions);
      buildCells();
    }
  }

  private boolean terminatingAll;

  private void terminateOtherSessions () {
    if (sessions == null || sessions.isEmpty() || terminatingAll) {
      return;
    }
    terminatingAll = true;

    if (terminatingSessions == null) {
      terminatingSessions = new LongSparseArray<>();
    }
    for (TdApi.Session session : sessions) {
      terminatingSessions.put(session.id, session);
      updateSessionById(session.id);
    }

    tdlib.client().send(new TdApi.TerminateAllOtherSessions(), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        terminatingSessions.clear();
        terminatingAll = false;

        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            clearSessionList();
            return;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.TerminateAllOtherSessions.class, TdApi.Ok.class);
            break;
          }
        }
        for (TdApi.Session session : sessions) {
          updateSessionById(session.id);
        }
      }
    }));
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

    tdlib.client().send(new TdApi.TerminateSession(session.id), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        terminatingSessions.remove(session.id);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            removeSessionFromList(session);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            updateSessionById(session.id);
            break;
          }
          default: {
            updateSessionById(session.id);
            Log.unexpectedTdlibResponse(object, TdApi.TerminateSession.class, TdApi.Ok.class);
            break;
          }
        }
      }
    }));
  }

  private int indexOfSessionInAdapter (long sessionId) {
    int index = indexOfSession(sessionId);
    return index != -1 ? adapter.indexOfViewByData(sessions.get(index)) : -1;
  }

  private int indexOfSession (long sessionId) {
    int i = 0;
    for (TdApi.Session check : sessions) {
      if (check.id == sessionId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private void removeSessionFromList (TdApi.Session session) {
    if (sessions.size() == 1) {
      clearSessionList();
      return;
    }

    int index = indexOfSession(session.id);
    if (index == -1) {
      return;
    }

    sessions.remove(index);
    if (getArguments() != null) {
      getArguments().updateAuthorizations(sessions, currentSession);
    }

    final int itemIndex = adapter.indexOfViewByData(session);
    if (itemIndex == -1)
      return;

    //boolean first = index == 0 || sessions.get(index - 1).isPasswordPending != session.isPasswordPending;
    //boolean last = index == sessions.size() || sessions.get(index).isPasswordPending != session.isPasswordPending;
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
    showOptions(Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSessionQuestion : R.string.TerminateSessionQuestion) + "\n\n" + getSubtext(session, true), new int[] {R.id.btn_terminateSession, R.id.btn_cancel, R.id.btn_copyText}, new String[] {Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession), Lang.getString(R.string.Cancel), Lang.getString(R.string.Copy)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24, R.drawable.baseline_content_copy_24}, (itemView, id) -> {
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
        showOptions(Lang.getString(R.string.AreYouSureSessions), new int[] {R.id.btn_terminateAllSessions, R.id.btn_cancel}, new String[] {Lang.getString(R.string.TerminateAllSessions), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_terminateAllSessions) {
            terminateOtherSessions();
          }
          return true;
        });
        break;
      }
      case R.id.btn_qrLogin: {
        openInAppCamera(new CameraOpenOptions().anchor(v).noTrace(true).mode(CameraController.MODE_QR));
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
    SettingsPrivacyController controller = getArguments();
    if (controller != null) {
      controller.setAuthorizationsLoadListener(null);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Devices);
  }
}
