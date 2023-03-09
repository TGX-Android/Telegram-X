/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ActivityPermissionResult;
import org.thunderdog.challegram.voip.VoIPController;
import org.thunderdog.challegram.voip.VoIPServerConfig;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.util.ArrayDeque;
import java.util.Queue;

import me.vkryl.core.reference.ReferenceList;

public class CallManager implements GlobalCallListener {
  public interface CurrentCallListener {
    void onCurrentCallChanged (Tdlib tdlib, @Nullable TdApi.Call call);
  }

  // Impl

  private final ReferenceList<CurrentCallListener> listeners = new ReferenceList<>();

  CallManager (TdlibManager context) {
    context.global().addCallListener(this);
  }

  private Tdlib currentCallTdlib;
  private @Nullable TdApi.Call currentCall;
  private boolean currentCallAcknowledged;

  public void addCurrentCallListener (CurrentCallListener listener) {
    listeners.add(listener);
  }

  public void removeCurrentCallListener (CurrentCallListener listener) {
    listeners.remove(listener);
  }

  private CancellationSignal serviceCancellationSignal;

  private void setCurrentCall (final Tdlib tdlib, @Nullable final TdApi.Call call) {
    if (currentCall == null && call == null) {
      return;
    }
    if (currentCall == null || call == null) {
      this.currentCallTdlib = tdlib;
      this.currentCall = call;
      this.currentCallAcknowledged = call == null || UI.getUiState() != UI.STATE_RESUMED || UI.isNavigationBusyWithSomething();
      if (currentCallAcknowledged) {
        notifyCallListeners();
      }
      if (serviceCancellationSignal != null) {
        serviceCancellationSignal.cancel();
        serviceCancellationSignal = null;
      }
      if (call != null) {
        Intent intent = new Intent(UI.getAppContext(), TGCallService.class);
        intent.putExtra("account_id", tdlib.id());
        intent.putExtra("call_id", call.id);
        serviceCancellationSignal = new CancellationSignal();
        UI.startService(intent, UI.getUiState() != UI.STATE_RESUMED, true, serviceCancellationSignal);

        navigateToCallController(currentCallTdlib, currentCall);
      }
    } else if (currentCallTdlib.id() != tdlib.id() || currentCall.id != call.id) {
      discardCall(tdlib, call.id, call.isVideo);
    } else {
      currentCallTdlib = tdlib;
      currentCall = call;
    }
  }

  private long getCallDurationImpl (Tdlib tdlib, int callId) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.compareCall(tdlib, callId)) {
      return service.getCallDuration();
    }
    return -1;
  }

  public int getCallBarsCount (Tdlib tdlib, int callId) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.compareCall(tdlib, callId)) {
      return service.getCallBarsCount();
    }
    return -1;
  }

  public int getCallDuration (Tdlib tdlib, int callId) {
    long duration = getCallDurationImpl(tdlib, callId);
    if (duration != -1) {
      return (int) (duration / 1000l);
    }
    return -1;
  }

  public long getTimeTillNextCallDurationUpdate (Tdlib tdlib, int callId) {
    long duration = getCallDurationImpl(tdlib, callId);
    if (duration != -1) {
      return 1000l - duration % 1000l;
    }
    return 1000l;
  }

  private void notifyCallListeners () {
    for (CurrentCallListener listener : listeners) {
      listener.onCurrentCallChanged(currentCallTdlib, currentCall);
    }
  }

  public void openCurrentCall () {
    TdApi.Call call = getCurrentCall();
    if (call != null) {
      navigateToCallController(currentCallTdlib, call);
    }
  }

  public int getCurrentCallId () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCall.id : 0;
  }

  public Tdlib getCurrentCallTdlib () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCallTdlib : null;
  }

  public int getCurrentCallAccountId () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCallTdlib.id() : TdlibAccount.NO_ID;
  }

  public TdApi.Call getCurrentCall () {
    return currentCallAcknowledged && !TD.isFinished(currentCall) ? currentCall : null;
  }

  public void acknowledgeCurrentCall (int callId) {
    if (!currentCallAcknowledged && currentCall != null && currentCall.id == callId) {
      currentCallAcknowledged = true;
      notifyCallListeners();
    }
  }

  private final Queue<Runnable> awaitingCurrentCallCompletion = new ArrayDeque<>();

  @Override
  public void onCallUpdated (final Tdlib tdlib, final TdApi.Call call) {
    if (currentCall != null && TD.isActive(currentCall)) {
      if (currentCallTdlib.id() != tdlib.id()) {
        return;
      }
      if (currentCall.id != call.id) {
        if (!call.isOutgoing) {
          if (call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
            tdlib.client().send(new TdApi.DiscardCall(call.id, false, 0, call.isVideo, 0), tdlib.okHandler());
          }
        }
        return;
      }
    }
    if (Log.isEnabled(Log.TAG_VOIP)) {
      Log.v(Log.TAG_VOIP, "#%d: updateCall, userId:%s isOutgoing:%b state:%s", call.id, call.userId, call.isOutgoing, call.state);
    }
    if (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) {
      VoIPServerConfig.setConfig(((TdApi.CallStateReady) call.state).config);
    }
    if (TD.isFinished(call)) {
      setCurrentCall(null, null);

      Runnable act;
      while ((act = awaitingCurrentCallCompletion.poll()) != null) {
        act.run();
      }
    } else {
      setCurrentCall(tdlib, call);
    }
  }

  @Override
  public void onCallSettingsChanged (Tdlib tdlib, int callId, CallSettings settings) { }

  private boolean navigateToCallController (Tdlib tdlib, TdApi.Call call) {
    BaseActivity activity = UI.getUiContext();
    if (activity != null && activity.getActivityState() == UI.STATE_RESUMED) {
      NavigationController navigation = UI.getNavigation();
      if (navigation != null) {
        ViewController<?> c = !navigation.isAnimating() ? navigation.getCurrentStackItem() : null;
        if (c instanceof CallController && c.tdlib() == tdlib && ((CallController) c).compareUserId(call.userId)) {
          ((CallController) c).replaceCall(call);
          return true;
        }

        if (activity.isNavigationBusy()) {
          return true;
        }

        CallController controller = new CallController(activity, tdlib);
        controller.setArguments(new CallController.Arguments(call));
        navigation.navigateTo(controller);
        return true;
      }
    }
    // TODO start activity
    return false;
  }

  private static void discardCall (Tdlib tdlib, final int callId, final boolean isVideo) {
    Log.v(Log.TAG_VOIP, "#%d: DiscardCall requested, isVideo:%b", callId, isVideo);
    tdlib.client().send(new TdApi.DiscardCall(callId, false, 0, isVideo, 0), object -> Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object));
  }

  /*
createCall user_id:int protocol:callProtocol = CallId;
acceptCall id:long protocol:callProtocol = Ok;
discardCall id:long reason:CallDiscardReason duration:int connection_id:long = Ok;
rateCall id:long rating:int comment:string = Ok;
debugCall id:long debug:string = Ok;
*/

  private static final boolean CHECK_CONNECTION = true;

  private void showNeedMicAlert (boolean missingHardware) {
    final Context context = UI.getContext();
    AlertDialog.Builder b;
    b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.MicrophonePermission));
    if (missingHardware) {
      b.setMessage(Lang.getString(R.string.MicrophoneMissing));
    } else {
      b.setMessage(Lang.getString(R.string.MicrophonePermissionDesc));
    }
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
      Intent intent = new Intent();
      intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      Uri uri = Uri.fromParts("package", context.getPackageName(), null);
      intent.setData(uri);
      context.startActivity(intent);
    });
    BaseActivity.modifyAlert(context, b.show(), null);
  }

  public void acceptIncomingCall (Tdlib tdlib, int callId) {
    acceptCall(UI.getAppContext(), tdlib, callId);
  }

  public void declineIncomingCall (Tdlib tdlib, int callId, boolean isVideo) {
    discardCall(tdlib, callId, isVideo);
  }

  public boolean checkRecordPermissions (final Context context, final Tdlib tdlib, final @Nullable TdApi.Call call, final long userId, final @Nullable ViewController<?> makeCallContext) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (UI.getAppContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        BaseActivity activity = UI.getUiContext();
        if (activity != null) {
          activity.requestMicPermissionForCall((code, permissions, grantResults, grantCount) -> {
            if (grantCount == permissions.length) {
              if (makeCallContext != null) {
                makeCall(makeCallContext, userId, null, false);
              } else {
                if (call != null) {
                  TdApi.Call updatedCall = tdlib.cache().getCall(call.id);
                  if (updatedCall != null && updatedCall.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
                    acceptCall(context, tdlib, call.id);
                  }
                }
              }
            } else {
              showNeedMicAlert(false);
            }
          });
        } else {
          // context.startActivity(new Intent(context, VoIPPermissionActivity.class));
        }
        return false;
      }
    }
    return true;
  }

  public void makeCallDelayed (final ViewController<?> context, final long userId, @Nullable final TdApi.UserFullInfo userFull, final boolean needPrompt) {
    UI.post(() -> makeCall(context, userId, userFull, needPrompt), 180l);
  }

  public boolean hasActiveCall () {
    return getCurrentCall() != null;
  }

  public boolean promptActiveCall () {
    if (hasActiveCall()) {
      Context context = UI.getUiContext();
      if (context != null) {
        AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setMessage(Lang.getString(R.string.SomeCallInProgressDesc));
        b.setNeutralButton(Lang.getString(R.string.HangUp), (dialog, which) -> hangUpCurrentCall());
        b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
        BaseActivity.modifyAlert(context, b.show(), null);
      }
      return true;
    }
    return false;
  }

  public void makeCall (final ViewController<?> context,final long userId, @Nullable TdApi.UserFullInfo userFull) {
    makeCall(context, userId, userFull, Settings.instance().needOutboundCallsPrompt());
  }

  public void makeCall (final ViewController<?> context, final long userId, @Nullable TdApi.UserFullInfo userFull, final boolean needPrompt) {
    if (userId == 0) {
      return;
    }
    if (Looper.myLooper() != Looper.getMainLooper()) {
      final TdApi.UserFullInfo userFullFinal = userFull;
      UI.post(() -> makeCall(context, userId, userFullFinal, needPrompt));
      return;
    }
    if (userFull == null) {
      userFull = context.tdlib().cache().userFull(userId);
    }
    if (!U.deviceHasMicrophone(UI.getAppContext())) {
      showNeedMicAlert(true);
      return;
    }
    /*final TdApi.Call pendingCall = context.tdlib().cache().getPendingCall();
    if (pendingCall != null && pendingCall.userId == userId) {
      navigateToCallController(context.tdlib().cache().getPendingCallTdlib(), pendingCall);
      return;
    }*/
    final TdApi.Call pendingCall = getCurrentCall();
    final Tdlib pendingCallTdlib = pendingCall != null ? currentCallTdlib : null;
    if (pendingCall != null || (userFull != null && !userFull.canBeCalled)|| (CHECK_CONNECTION && !context.tdlib().isConnected())) {
      AlertDialog.Builder b = new AlertDialog.Builder(context.context(), Theme.dialogTheme());
      b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
      if (pendingCall != null) {
        if (pendingCall.userId == userId) {
          openCurrentCall();
          return;
        }
        b.setTitle(Lang.getString(R.string.VoipOngoingAlertTitle));
        TdApi.User pendingUser = pendingCallTdlib.cache().user(pendingCall.userId);
        TdApi.User currentUser = context.tdlib().cache().user(userId);
        final TdApi.UserFullInfo userFullFinal = userFull;
        b.setPositiveButton(Lang.getString(R.string.HangUp), (dialog, which) -> {
          final boolean[] signal = new boolean[1];
          hangUp(pendingCallTdlib, pendingCall.id, () -> {
            if (!signal[0]) {
              signal[0] = true;
              makeCall(context, userId, userFullFinal, false);
            }
          });
          UI.post(() -> {
            if (!signal[0]) {
              signal[0] = true;
              UI.showToast(R.string.VoipFailed, Toast.LENGTH_SHORT);
            }
          }, 1500);
        });
        b.setMessage(Lang.getStringBold(R.string.CallInProgressDesc, TD.getUserName(pendingUser), TD.getFirstName(currentUser)));
        b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        b.setNeutralButton(Lang.getString(R.string.ShowCall), (dialog, which) -> {
          TdApi.Call newCurrentCall = getCurrentCall();
          if (newCurrentCall != null && (newCurrentCall == pendingCall || (newCurrentCall.id == pendingCall.id && !TD.isFinished(newCurrentCall)))) {
            navigateToCallController(pendingCallTdlib, pendingCall);
          }
        });
      } else if (userFull != null && !userFull.canBeCalled) {
        b.setTitle(Lang.getString(R.string.AppName));
        b.setMessage(Lang.getStringBold(R.string.NoRightToCall, context.tdlib().cache().userName(userId)));
        b.setNeutralButton(Lang.getString(R.string.OpenChat), (dialog, which) -> context.tdlib().ui().openPrivateChat(context, userId, null));
      } else if (U.isAirplaneModeOn()) {
        b.setTitle(Lang.getString(R.string.VoipOfflineAirplaneTitle));
        b.setMessage(Lang.getString(R.string.VoipOfflineAirplane));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
            Intents.openAirplaneSettings();
          });
        } else {
          b.setNeutralButton(Lang.getString(R.string.AirplaneModeDisable), (dialog, which) -> android.provider.Settings.System.putInt(context.context().getContentResolver(), android.provider.Settings.System.AIRPLANE_MODE_ON, 0));
        }
      } else {
        b.setTitle(Lang.getString(R.string.VoipOfflineTitle));
        b.setMessage(Lang.getString(R.string.VoipOffline));
      }
      BaseActivity.modifyAlert(context.context(), b.show(), null);
      return;
    }
    if (userFull == null) {
      context.tdlib().client().send(new TdApi.GetUserFullInfo(userId), object -> {
        switch (object.getConstructor()) {
          case TdApi.UserFullInfo.CONSTRUCTOR: {
            makeCall(context, userId, (TdApi.UserFullInfo) object, needPrompt);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.GetUserFullInfo.class, TdApi.UserFullInfo.class);
            break;
          }
        }
      });
      return;
    }
    if (needPrompt) {
      final TdApi.UserFullInfo userFullFinal = userFull;
      context.showOptions(Lang.getStringBold(R.string.CallX, context.tdlib().cache().userName(userId)), new int[]{R.id.btn_phone_call, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Call), Lang.getString(R.string.Cancel)}, null, new int[]{R.drawable.baseline_call_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_phone_call) {
          makeCallDelayed(context, userId, userFullFinal, false);
        }
        return true;
      });
      // UI.getCurrentStackItem(context);
      return;
    }
    if (!checkRecordPermissions(context.context(), context.tdlib(), null, userId, null)) {
      return;
    }
    context.context().closeAllMedia(false);
    context.tdlib().client().send(new TdApi.CreateCall(userId, new TdApi.CallProtocol(true, true, 65, VoIPController.getConnectionMaxLayer(), new String[] {VoIPController.getVersion()}), false), object -> {
      switch (object.getConstructor()) {
        case TdApi.CallId.CONSTRUCTOR:
          Log.v(Log.TAG_VOIP, "#%d: call created, user_id:%d", ((TdApi.CallId) object).id, userId);
          break;
        case TdApi.Error.CONSTRUCTOR:
          Log.e(Log.TAG_VOIP, "Failed to create call: %s", TD.toErrorString(object));
          UI.showError(object);
          break;
        default:
          Log.unexpectedTdlibResponse(object, TdApi.CreateCall.class, TdApi.CallId.class, TdApi.Error.class);
          break;
      }
    });
  }

  private boolean checkConnection (final Context context, final Tdlib tdlib) {
    AlertDialog.Builder b = null;
    if (CHECK_CONNECTION && !tdlib.isConnected()) {
      if (U.isAirplaneModeOn()) {
        b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.VoipOfflineAirplaneTitle));
        b.setMessage(Lang.getString(R.string.VoipOfflineAirplane));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
            Intent i = new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
          });
        } else {
          b.setNeutralButton(Lang.getString(R.string.AirplaneModeDisable), (dialog, which) -> android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.AIRPLANE_MODE_ON, 0));
        }
      } else {
        b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.VoipOfflineTitle));
        b.setMessage(Lang.getString(R.string.VoipOffline));
      }
    }
    if (b != null) {
      try { BaseActivity.modifyAlert(context, b.show(), null); } catch (Throwable ignored) { }
      return false;
    }
    return true;
  }

  public void acceptCall (Context context, Tdlib tdlib, final int callId) {
    if (checkConnection(context, tdlib)) {
      if (!checkRecordPermissions(context, tdlib, tdlib.cache().getCall(callId), 0, null)) {
        return;
      }
      Log.v(Log.TAG_VOIP, "#%d: AcceptCall requested", callId);
      tdlib.client().send(new TdApi.AcceptCall(callId, new TdApi.CallProtocol(true, true, 65, VoIPController.getConnectionMaxLayer(), new String[] {VoIPController.getVersion()})), object -> Log.v(Log.TAG_VOIP, "#%d: AcceptCall completed: %s", callId, object));
    }
  }

  public void hangUpCurrentCall () {
    int currentCall = getCurrentCallId();
    if (currentCall != 0) {
      hangUp(currentCallTdlib, currentCall);
    }
  }

  public void hangUp (final Tdlib tdlib, final int callId) {
    hangUp(tdlib, callId, null);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final @Nullable Runnable after) {
    TGCallService service = TGCallService.currentInstance();
    hangUp(tdlib, callId, false, service != null ? service.getConnectionId() : 0, after);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final boolean isDisconnect, final long connectionId) {
    hangUp(tdlib, callId, isDisconnect, connectionId, null);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final boolean isDisconnect, final long connectionId, final @Nullable Runnable after) {
    TdApi.Call call = tdlib.cache().getCall(callId);
    if (call == null) {
      return;
    }
    if (after != null) {
      if (TD.isFinished(call)) {
        after.run();
      } else {
        awaitingCurrentCallCompletion.offer(after);
      }
    }
    int duration = getCallDuration(tdlib, callId);
    Log.v(Log.TAG_VOIP, "#%d: DiscardCall, isDisconnect: %b, connectionId: %d, duration: %d", callId, isDisconnect, connectionId, duration);
    tdlib.client().send(new TdApi.DiscardCall(callId, isDisconnect, Math.max(0, duration), false, connectionId), object -> {
      Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object);
    });
  }
}
