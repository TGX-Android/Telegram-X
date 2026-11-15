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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.receiver.CallBroadcastReceiver;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPServerConfig;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.SdkVersion;
import me.vkryl.core.reference.ReferenceList;
import tgx.td.ChatId;

public class CallManager implements GlobalCallListener, UI.StateListener {
  public interface CurrentCallListener {
    void onCurrentCallChanged (Tdlib tdlib, @Nullable TdApi.Call call);
  }

  // Impl

  private final ReferenceList<CurrentCallListener> listeners = new ReferenceList<>();

  CallManager (TdlibManager context) {
    context.global().addCallListener(this);
    UI.addStateListener(this);
  }

  private boolean isWaitingForService;
  private int callIdWithoutService;

  public boolean isShownWithoutService (Tdlib tdlib, int callId) {
    return callIdWithoutService == callId && currentCallTdlib != null &&
      currentCallTdlib.id() == tdlib.id();
  }

  public boolean isAnotherCallShownWithoutService (Tdlib tdlib, int callId) {
    return callIdWithoutService != 0 && !isShownWithoutService(tdlib, callId);
  }

  @Override
  public void onUiStateChanged (int newState) {
    if (newState == UI.State.RESUMED) {
      startServiceIfMissing();
    }
  }

  private void startServiceIfMissing () {
    startServiceIfMissing(false);
  }

  private void startServiceIfMissing (boolean isAnswered) {
    // Without its service an answered call hangs on connecting, e.g. after Android refused its
    // start from background, which it allows while the app is visible
    if (currentCall == null || currentCallTdlib == null || TD.isFinished(currentCall) ||
      UI.getUiState() != UI.State.RESUMED || TGCallService.currentInstance() != null) {
      return;
    }
    // Started before the answer, the service would add a second notification to the ringing one
    if (!isAnswered && !currentCall.isOutgoing &&
      currentCall.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR &&
      isShownWithoutService(currentCallTdlib, currentCall.id)) {
      return;
    }
    Log.i(Log.TAG_VOIP, "#%d: starting the missing service of the call", currentCall.id);
    Intent intent = new Intent(AppContext.get(), TGCallService.class);
    intent.putExtra(Intents.KEY_ACCOUNT_ID, currentCallTdlib.id());
    intent.putExtra(Intents.KEY_CALL_ID, currentCall.id);
    UI.startService(intent, false, true, serviceCancellationSignal);
  }

  private void showIncomingCallWithoutService (Tdlib tdlib, TdApi.Call call) {
    // Android 12+ refuses to start the service from background outside its exemptions: the call
    // rings by its notification alone, under the id of the service's, which ends it as its own
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    Context context = AppContext.get();
    NotificationManager manager =
      (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    String channelId = tdlib.notifications().ensureCallChannel(ChatId.fromUserId(call.userId));
    if (channelId == null || !tdlib.notifications().canShowOnCallChannel(channelId)) {
      channelId = U.getIncomingCallsInAppNotificationChannel();
    }
    TdApi.User user = tdlib.cache().user(call.userId);
    Intent declineIntent = new Intent();
    Intents.secureIntent(declineIntent, false);
    declineIntent.setAction(Intents.ACTION_DECLINE_CALL);
    declineIntent.putExtra(Intents.KEY_ACCOUNT_ID, tdlib.id());
    declineIntent.putExtra(Intents.KEY_CALL_ID, call.id);
    int flags = PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(false);
    // Extras don't make a pending intent another one, so those of an earlier call are replaced
    int callFlags = flags | PendingIntent.FLAG_UPDATE_CURRENT;
    // Not one-shot: the notification isn't posted again while it rings, so its taps work again
    int reusableFlags = PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false);
    // "Answer" by a broadcast needs no PIN of the phone, and its tap lets the service of the call
    // start from background with the microphone; only an activity asks for the microphone
    boolean isLocked = Passcode.instance().isLockedForIncomingCalls();
    Intent callIntent = Intents.valueOfCall(tdlib.id(), call.id);
    PendingIntent answerAction;
    if (isLocked) {
      answerAction = PendingIntent.getActivity(context, Intents.REQUEST_CODE_CALL_ANSWER,
        callIntent, reusableFlags);
    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED) {
      Intent answerIntent = new Intent(context, CallBroadcastReceiver.class);
      answerIntent.setAction(Intents.ACTION_ANSWER_CALL);
      answerIntent.putExtra(Intents.KEY_ACCOUNT_ID, tdlib.id());
      answerIntent.putExtra(Intents.KEY_CALL_ID, call.id);
      answerAction = PendingIntent.getBroadcast(context, Intents.REQUEST_CODE_CALL_ANSWER,
        answerIntent, reusableFlags);
    } else {
      answerAction = PendingIntent.getActivity(context, Intents.REQUEST_CODE_CALL_ANSWER,
        Intents.valueOfAnswerCall(tdlib.id(), call.id), reusableFlags);
    }
    PendingIntent declineAction = PendingIntent.getBroadcast(context,
      Intents.REQUEST_CODE_CALL_DECLINE, declineIntent, callFlags);
    PendingIntent screenAction = PendingIntent.getActivity(context,
      Intents.REQUEST_CODE_CALL_SCREEN, callIntent, reusableFlags);
    PendingIntent fullScreenAction = PendingIntent.getActivity(context,
      Intents.REQUEST_CODE_CALL_FULL_SCREEN, callIntent, callFlags);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Only this style rings at the volume of calls; posted without it if the system refuses it
      Bitmap icon = user != null && !isLocked ? TdlibNotificationUtils.buildLargeIcon(tdlib,
        ChatId.fromUserId(user.id), user.profilePhoto != null ? user.profilePhoto.small : null,
        tdlib.cache().userAccentColor(user), TD.getLetters(user), false, true) : null;
      try {
        android.app.Person caller =
          TdlibNotificationUtils.newCallPerson(tdlib, user, isLocked, icon);
        Notification.Builder builder = newIncomingCallBuilder(context, channelId, tdlib, user,
          isLocked, screenAction, fullScreenAction)
          .setContentTitle(caller.getName())
          .setContentText(Lang.getString(
            isLocked ? R.string.CallUnlockToAnswer : R.string.CallBrandingIncoming
          ))
          .setLargeIcon(icon)
          .setStyle(Notification.CallStyle.forIncomingCall(caller, declineAction, answerAction)
            .setIsVideo(call.isVideo));
        manager.notify(TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION,
          newIncomingCallNotification(builder));
        callIdWithoutService = call.id;
        return;
      } catch (IllegalArgumentException e) {
        Log.e(Log.TAG_VOIP, "The style of calls refused", e);
      }
    }
    Notification.Builder builder = newIncomingCallBuilder(context, channelId, tdlib, user,
      isLocked, screenAction, fullScreenAction)
      .setContentText(isLocked ? Lang.getString(R.string.CallUnlockToAnswer) :
        user != null ? TD.getUserName(user) : null)
      .addAction(R.drawable.round_call_end_24_white, Lang.getString(R.string.DeclineCall),
        declineAction)
      .addAction(R.drawable.round_call_24_white,
        Lang.getString(isLocked ? R.string.CallUnlock : R.string.AnswerCall), answerAction);
    manager.notify(TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION,
      newIncomingCallNotification(builder));
    callIdWithoutService = call.id;
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private static Notification.Builder newIncomingCallBuilder (
    Context context,
    String channelId,
    Tdlib tdlib,
    @Nullable TdApi.User user,
    boolean isLocked,
    PendingIntent screenAction,
    PendingIntent fullScreenAction
  ) {
    Notification.Builder builder = new Notification.Builder(context, channelId)
      .setContentTitle(Lang.getString(
        isLocked ? R.string.IncomingCall : R.string.CallBrandingIncoming
      ))
      .setSmallIcon(R.drawable.baseline_phone_24_white)
      .setCategory(Notification.CATEGORY_CALL)
      .setOngoing(true)
      .setContentIntent(screenAction)
      .setFullScreenIntent(fullScreenAction, true)
      // Even when the process that posted it is gone: a call rings 90 s at most on Telegram
      .setTimeoutAfter(TimeUnit.SECONDS.toMillis(90));
    if (user != null) {
      TdlibNotificationUtils.addCallPerson(builder, tdlib, user, isLocked);
    }
    return builder;
  }

  private static Notification newIncomingCallNotification (Notification.Builder builder) {
    Notification notification = builder.build();
    notification.flags |= Notification.FLAG_INSISTENT;
    return notification;
  }

  public void cancelIncomingCallWithoutService () {
    NotificationManager manager =
      (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
    manager.cancel(TdlibNotificationManager.ID_FOREGROUND_INCOMING_CALL_NOTIFICATION);
  }

  public void declineCallWithoutService (Tdlib tdlib, int callId) {
    // In a process started by the broadcast TDLib may not know the call yet
    if (TGCallService.currentInstance() == null) {
      TdApi.Call call = tdlib.cache().getCall(callId);
      if (call == null) {
        tdlib.client().send(new TdApi.DiscardCall(callId, false, null, 0, false, 0),
          tdlib.silentHandler());
      } else if (!call.isOutgoing &&
        call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
        hangUp(tdlib, callId);
      }
    }
    cancelIncomingCallWithoutService();
  }

  public void answerCallWithoutService (Context context, Tdlib tdlib, int callId) {
    // A call that this process doesn't know, e.g. one of a process now gone, no longer rings
    TdApi.Call call = tdlib.cache().getCall(callId);
    if (call == null || call.isOutgoing ||
      call.state.getConstructor() != TdApi.CallStatePending.CONSTRUCTOR) {
      cancelIncomingCallWithoutService();
    } else if (!Passcode.instance().isLockedForIncomingCalls()) {
      // The tap on the notification lets the service start in foreground from background
      Intent intent = new Intent(context, TGCallService.class);
      intent.putExtra(Intents.KEY_ACCOUNT_ID, tdlib.id());
      intent.putExtra(Intents.KEY_CALL_ID, callId);
      UI.startService(intent, true, false, null);
      acceptCall(context, tdlib, callId);
    }
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
      this.currentCallAcknowledged = call == null || UI.getUiState() != UI.State.RESUMED || UI.isNavigationBusyWithSomething();
      if (currentCallAcknowledged) {
        notifyCallListeners();
      }
      if (serviceCancellationSignal != null) {
        serviceCancellationSignal.cancel();
        serviceCancellationSignal = null;
      }
      callIdWithoutService = 0;
      if (call != null) {
        Intent intent = new Intent(AppContext.get(), TGCallService.class);
        intent.putExtra("account_id", tdlib.id());
        intent.putExtra("call_id", call.id);
        serviceCancellationSignal = new CancellationSignal();
        boolean isStarted = UI.startService(intent, UI.getUiState() != UI.State.RESUMED, true,
          serviceCancellationSignal);
        isWaitingForService = !isStarted;
        if (!isStarted && !call.isOutgoing) {
          showIncomingCallWithoutService(tdlib, call);
        }

        navigateToCallController(currentCallTdlib, currentCall);
      } else if (isWaitingForService) {
        isWaitingForService = false;
        cancelIncomingCallWithoutService();
      }
    } else if (currentCallTdlib.id() != tdlib.id() || currentCall.id != call.id) {
      discardCall(tdlib, call.id, call.isVideo);
    } else {
      currentCallTdlib = tdlib;
      currentCall = call;
      if (call.state.getConstructor() != TdApi.CallStatePending.CONSTRUCTOR) {
        cancelIncomingCallWithoutService();
      }
      startServiceIfMissing();
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
            tdlib.send(new TdApi.DiscardCall(call.id, false, null, 0, call.isVideo, 0), tdlib.typedOkHandler());
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
      // The end of another call leaves the current one, unless it's gone from its account's cache
      boolean isOtherCall = currentCall != null &&
        (currentCallTdlib.id() != tdlib.id() || currentCall.id != call.id);
      if (isOtherCall && currentCallTdlib.cache().getCall(currentCall.id) != null) {
        return;
      }
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
    if (activity != null && activity.getActivityState() == UI.State.RESUMED) {
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
    tdlib.client().send(new TdApi.DiscardCall(callId, false, null, 0, isVideo, 0), object -> Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object));
  }

  /*
createCall user_id:int protocol:callProtocol = CallId;
acceptCall id:long protocol:callProtocol = Ok;
discardCall id:long reason:CallDiscardReason duration:int connection_id:long = Ok;
rateCall id:long rating:int comment:string = Ok;
debugCall id:long debug:string = Ok;
*/

  private static final boolean CHECK_CONNECTION = true;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    AlertType.MICROPHONE_HARDWARE_MISSING,
    AlertType.MICROPHONE_PERMISSION_MISSING
  })
  private @interface AlertType {
    int MICROPHONE_HARDWARE_MISSING = 1,
        MICROPHONE_PERMISSION_MISSING = 2,
        ANDROID_VERSION_UNSUPPORTED = 3;
  }

  private void showAlert (@AlertType int alertType) {
    final Context context = UI.getContext();
    AlertDialog.Builder b;
    b = new AlertDialog.Builder(context, Theme.dialogTheme());
    switch (alertType) {
      case AlertType.ANDROID_VERSION_UNSUPPORTED -> {
        b.setTitle(Lang.getString(R.string.AndroidVersionWarningTitle));
        b.setMessage(Lang.getString(R.string.AndroidVersionWarning, SdkVersion.getPrettyName(Build.VERSION_CODES.LOLLIPOP), SdkVersion.getPrettyVersionCode(Build.VERSION_CODES.LOLLIPOP)));
      }
      case AlertType.MICROPHONE_HARDWARE_MISSING -> {
        b.setTitle(Lang.getString(R.string.MicrophonePermission));
        b.setMessage(Lang.getString(R.string.MicrophoneMissing));
      }
      case AlertType.MICROPHONE_PERMISSION_MISSING -> {
        b.setTitle(Lang.getString(R.string.MicrophonePermission));
        b.setMessage(Lang.getString(R.string.MicrophonePermissionDesc));
      }
      default ->
        throw new UnsupportedOperationException(Integer.toString(alertType));
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
    acceptCall(AppContext.get(), tdlib, callId);
  }

  public void declineIncomingCall (Tdlib tdlib, int callId, boolean isVideo) {
    discardCall(tdlib, callId, isVideo);
  }

  public boolean checkRecordPermissions (final Context context, final Tdlib tdlib,
    final @Nullable TdApi.Call call) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (AppContext.get().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        BaseActivity activity = UI.getUiContext();
        if (activity != null) {
          activity.requestMicPermissionForCall((code, permissions, grantResults, grantCount) -> {
            if (grantCount == permissions.length) {
              if (call != null) {
                TdApi.Call updatedCall = tdlib.cache().getCall(call.id);
                if (updatedCall != null &&
                  updatedCall.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
                  acceptCall(context, tdlib, call.id);
                }
              }
            } else {
              showAlert(AlertType.MICROPHONE_PERMISSION_MISSING);
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
    if (!BuildConfig.CALLS_AVAILABLE) {
      showAlert(AlertType.ANDROID_VERSION_UNSUPPORTED);
      return;
    }
    if (!U.deviceHasMicrophone(AppContext.get())) {
      showAlert(AlertType.MICROPHONE_HARDWARE_MISSING);
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
      context.tdlib().send(new TdApi.GetUserFullInfo(userId), (remoteUserFull, error) -> {
        if (error != null) {
          UI.showError(error);
        } else {
          makeCall(context, userId, remoteUserFull, needPrompt);
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
    if (!checkRecordPermissions(context.context(), context.tdlib(), null)) {
      return;
    }
    context.context().closeAllMedia(false);
    context.tdlib().send(new TdApi.CreateCall(userId, VoIP.getProtocol(), false), (callId, error) -> {
      if (error != null) {
        Log.e(Log.TAG_VOIP, "Failed to create call: %s", TD.toErrorString(error));
        UI.showError(error);
      } else {
        Log.v(Log.TAG_VOIP, "#%d: call created, user_id:%d", callId.id, userId);
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
      if (!checkRecordPermissions(context, tdlib, tdlib.cache().getCall(callId))) {
        return;
      }
      Log.v(Log.TAG_VOIP, "#%d: AcceptCall requested", callId);
      startServiceIfMissing(true);
      tdlib.client().send(new TdApi.AcceptCall(callId, VoIP.getProtocol()), object -> Log.v(Log.TAG_VOIP, "#%d: AcceptCall completed: %s", callId, object));
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
    tdlib.client().send(new TdApi.DiscardCall(callId, isDisconnect, null, Math.max(0, duration), false, connectionId), object -> {
      Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object);
    });
  }
}
