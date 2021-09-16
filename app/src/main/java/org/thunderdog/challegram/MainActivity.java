/**
 * File created on 23/04/15 at 18:23
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.helper.LiveLocationHelper;
import org.thunderdog.challegram.loader.gif.LottieCache;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.AccountSwitchReason;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.LiveLocationManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.ui.CreateChannelController;
import org.thunderdog.challegram.ui.CreateGroupController;
import org.thunderdog.challegram.ui.EditNameController;
import org.thunderdog.challegram.ui.IntroController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.PasscodeController;
import org.thunderdog.challegram.ui.PasswordController;
import org.thunderdog.challegram.ui.PhoneController;
import org.thunderdog.challegram.ui.PlaybackController;
import org.thunderdog.challegram.ui.PrivacyExceptionController;
import org.thunderdog.challegram.ui.ProfileController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.ui.SettingsBugController;
import org.thunderdog.challegram.ui.SettingsCacheController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.ui.SettingsDataController;
import org.thunderdog.challegram.ui.SettingsNetworkStatsController;
import org.thunderdog.challegram.ui.SettingsNotificationController;
import org.thunderdog.challegram.ui.SettingsPrivacyController;
import org.thunderdog.challegram.ui.SettingsPrivacyKeyController;
import org.thunderdog.challegram.ui.SettingsThemeController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.GearView;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.DeviceUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.MessageId;

@SuppressWarnings(value = "SpellCheckingInspection")
public class MainActivity extends BaseActivity implements GlobalAccountListener {
  private Bundle tempSavedInstanceState;

  private TdlibAccount account;

  private Handler handler;

  @Override
  public void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Log.i("MainActivity.onCreate");

    handler = new Handler();

    TdlibManager.instance().global().addAccountListener(this);
    reloadTdlib();

    createMessagesController(tdlib).get();

    tempSavedInstanceState = savedInstanceState;

    Settings.CrashInfo crashInfo = TdlibManager.instance().getRecoveryCrashInfo();
    if (crashInfo != null) {
      SettingsBugController c = new SettingsBugController(this, tdlib);
      c.setArguments(new SettingsBugController.Args(crashInfo));
      navigation.initController(c);
    } else {
      initController(account.tdlib(), account.tdlib().authorizationStatus());
    }

    Tdlib currentTdlib = TdlibManager.instance().current();
    currentTdlib.awaitConnection(() -> {
      long pushId = Settings.instance().newPushId();
      TDLib.Tag.notifications(pushId, currentTdlib.id(), "Syncing other accounts, since user launched the app.");
      AtomicBoolean sentChangeLogs = new AtomicBoolean(currentTdlib.checkChangeLogs(false, false));
      TdlibManager.instance().sync(pushId, TdlibAccount.NO_ID, null, false, false, 3, tdlib -> {
        if (tdlib.checkChangeLogs(sentChangeLogs.get(), false))
          sentChangeLogs.set(true);
        LottieCache.instance().gc();
      });
    });
  }

  public void proceedFromRecovery () {
    if (TdlibManager.instance().exitRecoveryMode()) {
      navigation.reuse();
      initController(account.tdlib(), account.tdlib().authorizationStatus());
    }
  }

  public void forceSwitchToDebug (boolean debug) {
    int accountId = tdlib.context().newAccount(debug);
    this.account = TdlibManager.instance().account(accountId);
    account.tdlib().wakeUp();
    setTdlib(account.tdlib());
  }

  private void reloadTdlib () {
    this.account = TdlibManager.instance().currentAccount();
    account.tdlib().wakeUp();
    setTdlib(account.tdlib());
  }

  // Blank view

  private void setBlankViewVisible (boolean isVisible, boolean animated) {

  }

  /*private View blankView;
  private void setBlankViewVisible (boolean isVisible, boolean animated) {
    if (isVisible) {
      if (blankView == null) {
        blankView = new View(this) {
          @Override
          public boolean onTouchEvent (MotionEvent event) {
            return getAlpha() == 1f;
          }
        };
        ViewSupport.setThemedBackground(blankView, R.id.theme_color_filling);
        blankView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      }
      if (blankView.getParent() == null) {
        rootView.addView(blankView);
      }
      return;
    }
    if (blankView != null && blankView.getParent() != null) {
      if (animated) {
        new FactorAnimator(0, new FactorAnimator.Target() {
          @Override
          public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
            if (blankView != null) {
              blankView.setAlpha(factor);
            }
          }

          @Override
          public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
            if (blankView != null) {
              rootView.removeView(blankView);
              blankView = null;
            }
          }
        }, Anim.DECELERATE_INTERPOLATOR, 180l, 1f).animateTo(0f);
      } else {
        if (blankView != null) {
          rootView.removeView(blankView);
          blankView = null;
        }
      }
    }
  }*/

  // Account management

  @Override
  public void onAccountProfileChanged (TdlibAccount account, TdApi.User profile, boolean isCurrent, boolean isLoaded) { }

  private void cleanupStack (ViewController<?> c, int accountId) {
    if (this.account.id != accountId) {
      return;
    }
    NavigationStack stack = navigation.getStack();
    stack.destroyAllButSaveLast(2);
    MainController chats = new MainController(c.context(), c.tdlib());
    chats.get();
    stack.replace(0, chats);
  }

  @Override
  public void onAccountSwitched (final TdlibAccount newAccount, TdApi.User profile, @AccountSwitchReason int reason, TdlibAccount oldAccount) {
    if (this.account.id == newAccount.id) {
      return;
    }

    this.account = newAccount;
    setTdlib(newAccount.tdlib());
    // tdlib.contacts().makeSilentPermissionCheck(this);

    switch (reason) {
      case TdlibManager.SWITCH_REASON_CHAT_OPEN:
      case TdlibManager.SWITCH_REASON_CHAT_FOCUS:
        return;
      case TdlibManager.SWITCH_REASON_EXISTING_NUMBER:
        break;
      case TdlibManager.SWITCH_REASON_NAVIGATION:
        break;
      case TdlibManager.SWITCH_REASON_UNAUTHORIZED:
        break;
      case TdlibManager.SWITCH_REASON_USER_CLICK:
        break;
    }

    destroyMessageControllers(newAccount.id);

    final ViewController<?> current = navigation.getCurrentStackItem();
    if (current != null && current.tdlib() != null && current.tdlib().id() == newAccount.id) {
      /*if (current.isFocused()) {
        cleanupStack(current, newAccount.id);
      } else {
        current.addFocusListener(new ViewController.FocusStateListener() {
          @Override
          public void onFocusStateChanged (ViewController c, boolean isFocused) {
            if (isFocused) {
              c.removeFocusListener(this);
              cleanupStack(current, newAccount.id);
            }
          }
        });
      }*/

      return;
    }
    MainController c = new MainController(this, newAccount.tdlib());
    if (navigation.isEmpty()) {
      navigation.setController(c);
    } else {
      navigation.setControllerAnimated(c, false, false);
    }
  }

  private void processAuthorizationStateChange (TdlibAccount account, TdApi.AuthorizationState authorizationState, int status) {
    if (this.account.id != account.id) {
      if (navigation.isEmpty() || !navigation.getCurrentStackItem().isSameAccount(account)) {
        return;
      }
    }
    if (navigation.isEmpty()) {
      initController(this.account.tdlib(), this.account.tdlib().authorizationStatus());
      return;
    }

    ViewController<?> current = navigation.getStack().getCurrent();

    if (status == Tdlib.STATUS_READY) {
      ViewController<?> first = navigation.getStack().get(0);
      boolean needThemeSwitch = this.account.id != account.id && isUnauthorizedController(current) && !isUnauthorizedController(first) && first.tdlibId() != account.id && current.tdlibId() == account.id;

      if (isUnauthorizedController(first) || !first.isSameAccount(account)) {
        ViewController<?> c = new MainController(this, account.tdlib());
        if (needThemeSwitch) {
          account.tdlib().settings().replaceGlobalTheme(this.account.tdlib().settings());
        }
        navigation.setControllerAnimated(c, false, false);
      }
      return;
    }

    if (status == Tdlib.STATUS_UNAUTHORIZED && this.account.id == account.id) {
      int nextAccountId = tdlib.context().findNextAccountId(this.account.id);
      if (nextAccountId != TdlibAccount.NO_ID) {
        tdlib.context().changePreferredAccountId(nextAccountId, TdlibManager.SWITCH_REASON_UNAUTHORIZED);
        return;
      }
    }

    ViewController<?> unauthorizedController = generateUnauthorizedController(account.tdlib());
    if (unauthorizedController != null) {
      if (current == null || current.getId() != unauthorizedController.getId()) {
        navigation.navigateTo(unauthorizedController);
      }
      return;
    }

    if (UI.inTestMode() && authorizationState.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR && current instanceof PhoneController) {
      ((PhoneController) current).onAuthorizationReady();
    }

    ViewController<?> first = navigation.getStack().get(0);
    if (!isUnauthorizedController(first) && first.isSameAccount(account)) {
      if (navigation.isAnimating()) {
        ViewController<?> c = navigation.getPendingController();
        if (c != null && c.isSameAccount(account) && c.isUnauthorized())
          return;
        c = navigation.getStack().getCurrent();
        if (c != null && c.isSameAccount(account) && c.isUnauthorized() && c instanceof PhoneController && !((PhoneController) c).isAccountAdd())
          return;
      }
      PhoneController c = new PhoneController(this, account.tdlib());
      navigation.setControllerAnimated(c, true, false);
      return;
    }
  }

  @Override
  public void onTdlibOptimizing (Tdlib tdlib, boolean isOptimizing) {
    handler.post(() -> {
      if (currentTdlib() == tdlib) {
        if (isOptimizing) {
          setShowOptimizing(true);
        } else {
          tdlib.awaitInitialization(() -> {
            if (currentTdlib() == tdlib && !tdlib.isOptimizing()) {
              setShowOptimizing(false);
            }
          });
        }
      }
    });
  }

  @Override
  protected void onTdlibChanged () {
    setShowOptimizing(currentTdlib().isOptimizing());
  }

  private BoolAnimator optimizeAnimator;
  private ViewGroup optimizeLayout;

  private void setShowOptimizing (boolean isOptimizing) {
    if (!isOptimizing && optimizeAnimator == null)
      return;
    if (optimizeLayout == null) {
      LinearLayout ll = new LinearLayout(this);
      ll.setOrientation(LinearLayout.VERTICAL);
      ll.setGravity(Gravity.CENTER);
      ll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      ViewSupport.setThemedBackground(ll, R.id.theme_color_filling);

      GearView gearView = new GearView(this);
      ll.addView(gearView);

      NoScrollTextView textView;

      textView = new NoScrollTextView(this);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
      textView.setPadding(Screen.dp(12f), Screen.dp(14f), Screen.dp(12f), Screen.dp(14f));
      textView.setTextColor(Theme.textAccentColor());
      textView.setGravity(Gravity.CENTER);
      Views.setMediumText(textView, Lang.getString(R.string.Optimizing));
      ll.addView(textView);

      textView = new NoScrollTextView(this);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
      textView.setGravity(Gravity.CENTER);
      textView.setPadding(Screen.dp(24f), 0, Screen.dp(24f), 0);
      textView.setTextColor(Theme.textAccentColor());
      textView.setText(Lang.getString(R.string.OptimizingInfo));
      ll.addView(textView);

      int index = -1;
      if (statusBar != null) {
        index = rootView.indexOfChild(statusBar);
      }
      rootView.addView(ll, index);
      optimizeLayout = ll;
      optimizeLayout.setAlpha(0f);
    }
    if (optimizeAnimator == null) {
      optimizeAnimator = new BoolAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          if (optimizeLayout != null) {
            ((GearView) optimizeLayout.getChildAt(0)).setLooping(factor > 0f);
            optimizeLayout.setAlpha(factor);
          }
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
          if (finalFactor == 0f && optimizeLayout != null) {
            ((GearView) optimizeLayout.getChildAt(0)).setLooping(false);
            rootView.removeView(optimizeLayout);
            optimizeLayout = null;
          }
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
    }
    optimizeAnimator.setStartDelay(isOptimizing ? 0 : 180l);
    optimizeAnimator.setValue(isOptimizing, true);
  }

  @Override
  public void onAuthorizationStateChanged (final TdlibAccount account, final TdApi.AuthorizationState authorizationState, final int status) {
    processAuthorizationStateChange(account, authorizationState, status);
  }

  // Stack initialization

  private void initController (Tdlib tdlib, int status) {
    switch (status) {
      case Tdlib.STATUS_UNKNOWN:
        // setBlankViewVisible(true, false);
        break;
      case Tdlib.STATUS_UNAUTHORIZED:
        int nextAccountId = tdlib.context().findNextAccountId(tdlib.id());
        if (nextAccountId == TdlibAccount.NO_ID) {
          initUnauthorizedController();
        } else {
          tdlib.context().changePreferredAccountId(nextAccountId, TdlibManager.SWITCH_REASON_NAVIGATION);
        }
        break;
      case Tdlib.STATUS_READY:
        initAuthorizedController();
        break;
    }
  }

  public final ViewController<?> generateUnauthorizedController (Tdlib tdlib) {
    TdApi.AuthorizationState authState = tdlib.authorizationState();
    switch (authState.getConstructor()) {
      case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR: {
        TdApi.AuthorizationStateWaitCode state = (TdApi.AuthorizationStateWaitCode) authState;
        PasswordController c = new PasswordController(this, tdlib);
        c.setArguments(new PasswordController.Args(PasswordController.MODE_CODE, state, tdlib.authPhoneNumberFormatted()));
        return c;
      }
      case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR: {
        TdApi.AuthorizationStateWaitRegistration state = (TdApi.AuthorizationStateWaitRegistration) authState;
        EditNameController c = new EditNameController(this, tdlib);
        c.setArguments(new EditNameController.Args(EditNameController.MODE_SIGNUP, state, tdlib.authPhoneNumberFormatted()));
        return c;
      }
      case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
        TdApi.AuthorizationStateWaitPassword state = (TdApi.AuthorizationStateWaitPassword) authState;
        PasswordController c = new PasswordController(this, tdlib);
        c.setArguments(new PasswordController.Args(PasswordController.MODE_LOGIN, state));
        return c;
      }
    }
    return null;
  }

  // Stack initialization (unauthorized)

  private static <T extends ViewController<?>> boolean isUnauthorizedController (T controller) {
    return controller.isUnauthorized();
  }

  private void initUnauthorizedController () {
    ViewController<?> c = generateUnauthorizedController(account.tdlib());
    if (c != null) {
      navigation.initController(c);
      c = new PhoneController(this, account.tdlib());
      navigation.insertController(c, 0);
      return;
    }
    if (IntroController.isIntroAttemptedButFailed()) {
      navigation.initController(new PhoneController(this, account.tdlib()));
    } else {
      navigation.initController(new IntroController(this));
    }
  }

  // Stack initialization (authorized)

  private void initAuthorizedController () {
    final Intent intent = getIntent();
    final String action = intent != null ? intent.getAction() : null;
    if (!StringUtils.isEmpty(action)) {
      if (handleIntent(action, intent, true)) {
        return;
      }
    }
    if (tempSavedInstanceState != null) {
      int resultCode = restoreInstanceState(tempSavedInstanceState);
      tempSavedInstanceState = null;

      if (resultCode == INSTANCE_RESTORED_NEEDS_MAIN) {
        insertMainController();
      }

      if (resultCode != INSTANCE_NOT_RESTORED) {
        return;
      }
    }

    initMainController(null, null, null);
  }

  private void initMainController (@Nullable Tdlib intentTdlib, @Nullable String intentAction, @Nullable Intent intent) {
    MainController c = new MainController(this, account.tdlib());
    if (intent != null) {
      c.shareIntent(intentTdlib, intentAction, intent);
    }
    initWith(c);
  }

  private void initWith (MainController c) {
    if (true) {
      navigation.initController(c);
      return;
    }
    c.get();
    ViewController<?> child = c.getPreparedControllerForPosition(0);
    if (child != null && child.needAsynchronousAnimation()) {
      setBlankViewVisible(true, false);
      child.postOnAnimationExecute(() -> {
        if (navigation.isEmpty()) {
          navigation.initController(c);
        }
        setBlankViewVisible(false, false);
      });
    } else {
      navigation.initController(c);
    }
  }

  private void insertMainController () {
    MainController c = new MainController(this, account.tdlib());
    c.get();
    navigation.insertController(c, 0);
  }

  @Override
  public void onPause () {
    super.onPause();
    Log.i("MainActivity.onPause");
  }

  @Override
  protected boolean needDrawer () {
    return true;
  }

  // Viewing

  @Override
  protected void onNewIntent (Intent intent) {
    super.onNewIntent(intent);
    String action = intent != null ? intent.getAction() : null;
    if (!StringUtils.isEmpty(action)) {
      handleIntent(action, intent, false);
    }
  }

  private boolean handleIntent (String actionRaw, final Intent intent, boolean fromCreate) {
    final String action = Intents.getCleanAction(actionRaw);

    if (StringUtils.isEmpty(action) || isNavigationBusy() || StringUtils.equalsOrBothEmpty(action, Intent.ACTION_MAIN) || TdlibManager.instance().inRecoveryMode()) {
      return false;
    }

    intent.setAction("");
    setIntent(intent);

    if (isPasscodeShowing()) {
      addPasscodeListener(new PasscodeListener() {
        @Override
        public void onPasscodeShowing (BaseActivity context, boolean isShowing) {
          if (!isShowing) {
            handleIntent(actionRaw, intent, false);
            removePasscodeListener(this);
          }
        }
      });
      return false;
    }

    if (Log.checkLogLevel(Log.LEVEL_INFO)) {
      Log.i("handleIntent action=%s intent=%s fromCreate=%b stackSize=%d", action, intent, fromCreate, navigation != null ? navigation.getStackSize() : -1);
    }

    // Custom

    if (action.startsWith(Intents.ACTION_OPEN_MAIN)) {
      int accountId = intent.getIntExtra("account_id", TdlibAccount.NO_ID);
      if (accountId == TdlibAccount.NO_ID) {
        if (action.length() > Intents.ACTION_OPEN_MAIN.length() + 1) {
          accountId = StringUtils.parseInt(action.substring(Intents.ACTION_OPEN_MAIN.length() + 1));
          if (!TdlibManager.instance().hasAccount(accountId)) {
            accountId = TdlibAccount.NO_ID;
          }
        }
        if (accountId == TdlibAccount.NO_ID) {
          accountId = TdlibManager.instance().currentAccount().id;
        }
        Log.w("Received unknown accountId: %s", action);
      }
      Tdlib tdlib = TdlibManager.instanceForAccountId(accountId).account(accountId).tdlib();
      tdlib.awaitInitialization(() -> {
        tdlib.incrementUiReferenceCount();
        handler.post(() -> {
          openMainController(tdlib.id());
          tdlib.decrementUiReferenceCount();
        });
      });
      return true;
    }

    if (action.startsWith(Intents.ACTION_OPEN_CHAT)) {
      int accountId = intent.getIntExtra("account_id", TdlibAccount.NO_ID);
      long chatId = intent.getLongExtra("chat_id", 0);
      if (chatId == 0) {
        long localChatId = intent.getLongExtra("local_id", 0);
        if (localChatId != 0) {
          chatId = TdlibSettingsManager.getRemoteChatId(accountId, localChatId);
        }
      }
      long messageId = intent.getLongExtra("message_id", 0);
      if (accountId != TdlibAccount.NO_ID && chatId != 0) {
        openMessagesController(accountId, chatId, messageId);
        return true;
      } else {
        Log.e("Cannot open chat, no information found: %s", intent);
      }
      return false;
    }

    if (action.startsWith(Intents.ACTION_OPEN_LOGS)) {
      SettingsBugController c = new SettingsBugController(this, account.tdlib());
      if (navigation.isEmpty()) {
        navigation.initController(c);
      } else {
        navigation.navigateTo(c);
      }
      return true;
    }

    if (Intents.ACTION_LOCATION_RESOLVE.equals(action)) {
      resolveLiveLocationError(true);
      return true;
    }

    if (Intents.ACTION_LOCATION_VIEW.equals(action)) {
      openLiveLocation();
      return true;
    }

    if (Intents.ACTION_OPEN_CALL.equals(action)) {
      openCallController();
      return true;
    }

    if (action.startsWith(Intents.ACTION_OPEN_PLAYER)) {
      openPlayerController(intent.getIntExtra("account_id", TdlibAccount.NO_ID));
      return true;
    }

    checkPasscode(false);
    if (isPasscodeShowing()) {
      addPasscodeListener(new PasscodeListener() {
        @Override
        public void onPasscodeShowing (BaseActivity context, boolean isShowing) {
          if (!isShowing) {
            handleIntent(actionRaw, intent, false);
            removePasscodeListener(this);
          }
        }
      });
      return false;
    }

    // System shit

    RunnableData<TdlibAccount> consumer = null;
    String text = null;
    String actionText = null;

    switch (action) {
      case Intent.ACTION_SEND:
      case Intent.ACTION_SEND_MULTIPLE:
        if (navigation.isEmpty()) {
          initMainController(null, null, null);
        }
        consumer = account -> {
          shareIntent(account, action, intent);
        };
        text = Lang.getString(R.string.ShareAs);
        actionText = Lang.getString(R.string.Share);
        break;
      case Intent.ACTION_VIEW: {
        Uri data = intent.getData();
        if (data == null || StringUtils.isEmpty(data.getScheme()))
          return false;
        String url = data.toString();
        if (account.tdlib().ui().needViewInBrowser(url) && Intents.openUriInBrowser(data)) {
          return false;
        }
        if (navigation.isEmpty()) {
          initMainController(null, null, null);
        }
        consumer = account -> {
          ViewController<?> context = navigation.getCurrentStackItem();
          if (context != null) {
            account.tdlib().awaitInitialization(() -> account.tdlib().ui().openTelegramUrl(new TdlibContext(MainActivity.this, tdlib), url, null, null));
          }
        };
        text = Lang.getString(R.string.OpenLinkAs);
        actionText = Lang.getString(R.string.Open);
        break;
      }
    }

    if (consumer != null) {
      performAs(text, actionText, consumer);
      return true;
    }

    return false;
  }

  public void performAs (@Nullable CharSequence prompt, @Nullable String action, final @NonNull RunnableData<TdlibAccount> callback) {
    performAs(TdlibManager.instance().accountsQueue(), prompt, action, callback);
  }

  public void batchPerformFor (@Nullable CharSequence prompt, @Nullable String action, final @NonNull RunnableData<List<TdlibAccount>> callback) {
    performAs(TdlibManager.instance().accountsQueue(), prompt, action, true, callback);
  }

  private void performAs (List<TdlibAccount> accounts, @Nullable CharSequence prompt, @Nullable String action, final @NonNull RunnableData<TdlibAccount> callback) {
    performAs(accounts, prompt, action, false, accountList -> {
      for (TdlibAccount account : accountList) {
        callback.runWithData(account);
      }
    });
  }

  private void performAs (List<TdlibAccount> accounts, @Nullable CharSequence prompt, @Nullable String action, boolean multiSelect, final @NonNull RunnableData<List<TdlibAccount>> callback) {
    if (accounts.size() <= 1) {
      callback.runWithData(ArrayUtils.asList(currentTdlib().account()));
      return;
    }
    boolean inRecoveryMode = currentTdlib().context().inRecoveryMode();
    final int currentAccountId = currentTdlib().id();

    final List<ListItem> items = new ArrayList<>(accounts.size() + 2 + (multiSelect ? 1 : 0));
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)).setBoolValue(true));
    if (multiSelect) {
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_selectAll, 0, R.string.SelectAll, R.id.btn_selectAll, inRecoveryMode));
    }
    boolean hasCurrent = false;
    for (TdlibAccount account : accounts) {
      String name = account.getLongName();
      boolean isCurrent = currentAccountId == account.id;
      if (isCurrent) {
        hasCurrent = true;
      }
      items.add(new ListItem(
        multiSelect ? ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR : ListItem.TYPE_RADIO_OPTION_WITH_AVATAR,
        account.id + 1, 0,
        isCurrent ? Lang.getString(inRecoveryMode ? R.string.LastUsedAccount : R.string.CurrentAccount, name) : name,
        multiSelect ? account.id + 1 : R.id.account,
        isCurrent || (inRecoveryMode && multiSelect)
      ).setData(account)
       .setIntValue(account.getKnownUserId()));
    }
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)).setBoolValue(true));

    if (!hasCurrent && !multiSelect) {
      items.get(1).setSelected(true);
    }

    if (StringUtils.isEmpty(prompt)) {
      prompt = Lang.getString(R.string.PerformAs);
    }
    if (StringUtils.isEmpty(action)) {
      action = Lang.getString(R.string.Proceed);
    }

    final AtomicBoolean needListener = new AtomicBoolean(true);
    final AtomicReference<GlobalAccountListener> accountListener = new AtomicReference<>();

    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.account)
      .addHeaderItem(prompt)
      .setRawItems(items)
      .setSaveStr(action)
      .setNeedSeparators(false)
      .setOnSettingItemClick(multiSelect ? new ViewController.OnSettingItemClick() {
        @Override
        public void onSettingItemClick (View view, int settingsId, ListItem item, TextView doneButton, SettingsAdapter settingsAdapter) {
          switch (item.getViewType()) {
            case ListItem.TYPE_CHECKBOX_OPTION:
            case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
              break;
            default:
              return;
          }
          final boolean isSelect = item.isSelected(); // settingsAdapter.toggleView(view);

          final List<ListItem> allItems = settingsAdapter.getItems();

          if (item.getId() == R.id.btn_selectAll) {
            int index = 0;
            for (ListItem listItem : allItems) {
              if (listItem.getViewType() == ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR && listItem.isSelected() != isSelect) {
                listItem.setSelected(isSelect);
                settingsAdapter.updateValuedSettingByPosition(index);
              }
              index++;
            }
          } else if (isSelect) {
            boolean selectedAll = true;
            int allPosition = -1;
            int index = 0;
            for (ListItem listItem : allItems) {
              if (listItem.getId() == R.id.btn_selectAll) {
                allPosition = index;
              }
              if (listItem.getViewType() == ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR && !listItem.isSelected()) {
                selectedAll = false;
                break;
              }
              index++;
            }
            if (selectedAll && allPosition != -1 && !allItems.get(allPosition).isSelected()) {
              allItems.get(allPosition).setSelected(true);
              settingsAdapter.updateValuedSettingByPosition(allPosition);
            }
          } else {
            int index = 0;
            for (ListItem listItem : allItems) {
              if (listItem.getId() == R.id.btn_selectAll) {
                if (listItem.isSelected()) {
                  listItem.setSelected(false);
                  settingsAdapter.updateValuedSettingByPosition(index);
                }
                break;
              }
              index++;
            }
          }
        }
      } : null)
      .setIntDelegate((id, result) -> {
        List<TdlibAccount> selected;
        if (multiSelect) {
          selected = ArrayUtils.filter(accounts, account -> result.get(account.id + 1) == account.id + 1);
        } else {
          int accountId = result.get(R.id.account) - 1;
          selected = ArrayUtils.filter(accounts, account -> account.id == accountId);
        }
        if (!selected.isEmpty()) {
          callback.runWithData(selected);
        }
      })
      .setDismissListener(popup -> {
        if (accountListener.get() != null) {
          TdlibManager.instance().global().removeAccountListener(accountListener.get());
          needListener.set(false);
        }
      });
    ViewController<?> c = navigation.getCurrentStackItem();
    if (c != null) {
      SettingsWrap wrap = c.showSettings(b);
      if (wrap != null && wrap.adapter != null) {
        GlobalAccountListener listener = new GlobalAccountListener() {
          @Override
          public void onAccountProfileChanged (TdlibAccount account, TdApi.User profile, boolean isCurrent, boolean isLoaded) {
            if (wrap.adapter != null) {
              int index = wrap.adapter.indexOfViewByData(account);
              if (index != -1) {
                wrap.adapter.notifyItemChanged(index);
              }
            }
          }
        };
        if (needListener.get()) {
          accountListener.set(listener);
          TdlibManager.instance().global().addAccountListener(listener);
        }
      }
    }
  }

  // Live Location

  public void openLiveLocation () {
    if (navigation.isEmpty()) {
      initDefault(currentTdlib().id(), false);
    }
    LinkedList<TdlibAccount> accounts = new LinkedList<>();

    for (TdlibAccount account : TdlibManager.instance()) {
      if (account.hasTdlib(true) && account.tdlib().cache().hasOutputLocations()) {
        accounts.add(account);
      }
    }
    if (accounts.isEmpty()) {
      return;
    }

    if (accounts.size() == 1) {
      new LiveLocationHelper(this, accounts.get(0).tdlib(), 0, 0, null, false, null).init().openLiveLocationList(false).destroy();
      return;
    }

    performAs(accounts, null, null, account -> account.tdlib().awaitInitialization(() -> new LiveLocationHelper(MainActivity.this, account.tdlib(), 0, 0, null, false, null).init().openLiveLocationList(true).destroy()));
  }

  private void resolveLiveLocationError (boolean force) {
    LiveLocationManager manager = tdlib.context().liveLocation();
    if (!manager.hasResolvableError()) {
      return;
    }
    manager.resolveError(this);
  }

  // Sharing

  private void shareIntent (final TdlibAccount account, final String action, Intent intent) {
    if (navigation.isEmpty()) {
      initMainController(account.tdlib(), action, intent);
    } else {
      ViewController<?> c = navigation.getStack().get(0);
      if (c instanceof MainController) {
        ((MainController) c).shareIntent(account.tdlib(), action, intent);
      }
    }
  }

  // Controller storage

  private static final String BUNDLE_ACCOUNT_ID = "nav_account_id";

  private static final String BUNDLE_RESTORE_TYPE = "nav_stack_type";
  private static final int BUNDLE_RESTORE_TYPE_NONE = 0;
  private static final int BUNDLE_RESTORE_TYPE_COMPLEX = 2;

  private static final String BUNDLE_ITEM_COUNT = "nav_item_count";
  private static final String BUNDLE_ITEM_PREFIX = "nav_item_";

  private static String makeBundleItemPrefix (int index) {
    return BUNDLE_ITEM_PREFIX + index;
  }

  @Override
  public void onSaveInstanceState (Bundle outState) {
    if (outState == null) {
      super.onSaveInstanceState(null);
      return;
    }

    int stackSize = navigation != null ? navigation.getStackSize() : 0;
    if (stackSize > 1) {
      ViewController<?> c;
      while ((c = navigation.getStack().get(stackSize - 1)) != null && c.tdlib() != account.tdlib()) {
        stackSize--;
      }
    }

    ViewController<?> current = stackSize > 1 ? navigation.getStack().get(stackSize - 1) : null;
    if (stackSize <= 1 || current == null) {
      outState.putInt(BUNDLE_RESTORE_TYPE, BUNDLE_RESTORE_TYPE_NONE);
      super.onSaveInstanceState(outState);
      return;
    }

    outState.putInt(BUNDLE_ACCOUNT_ID, account.id);

    /*if (current.getId() == R.id.controller_messages ||
       (current.getId() == R.id.controller_passcode && !((PasscodeController) current).inSetupMode() && current.getChatId() != 0)) {
      outState.putInt(BUNDLE_RESTORE_TYPE, BUNDLE_RESTORE_TYPE_CHAT);
      outState.putLong(BUNDLE_CHAT_ID, current.getChatId());
      super.onSaveInstanceState(outState);
      return;
    }*/

    int savedCount = 0;
    for (int i = stackSize - 1; i >= 0; i--) {
      ViewController<?> c = navigation.getStack().get(i);
      if (c == null) {
        continue;
      }
      String keyPrefix = makeBundleItemPrefix(savedCount);
      int controllerId = c.getId();
      if (canSaveController(controllerId, c) || c.saveInstanceState(outState, keyPrefix + "_")) {
        outState.putInt(keyPrefix, controllerId);
        savedCount++;
      }
    }

    if (savedCount > 0) {
      outState.putInt(BUNDLE_RESTORE_TYPE, BUNDLE_RESTORE_TYPE_COMPLEX);
      outState.putInt(BUNDLE_ITEM_COUNT, savedCount);
    } else {
      outState.putInt(BUNDLE_RESTORE_TYPE, BUNDLE_RESTORE_TYPE_NONE);
    }

    super.onSaveInstanceState(outState);
  }

  private static final int INSTANCE_NOT_RESTORED = 0;
  private static final int INSTANCE_RESTORED_FULLY = 1;
  private static final int INSTANCE_RESTORED_NEEDS_MAIN = 2;

  private int restoreInstanceState (Bundle in) {
    if (in == null) {
      return INSTANCE_NOT_RESTORED;
    }

    int restoreType = in.getInt(BUNDLE_RESTORE_TYPE, BUNDLE_RESTORE_TYPE_NONE);
    if (restoreType == BUNDLE_RESTORE_TYPE_NONE) {
      return INSTANCE_NOT_RESTORED;
    }

    int accountId = in.getInt(BUNDLE_ACCOUNT_ID, 0);

    if (account.id != accountId) {
      return INSTANCE_NOT_RESTORED;
      /*TdlibAccount desiredAccount = TdlibManager.instance().account(accountId);
      if (desiredAccount.isUnauthorized()) {
        return INSTANCE_NOT_RESTORED;
      }
      this.account = desiredAccount;
      TdlibManager.instance().changePreferredAccountId(desiredAccount.id, true);*/
    }

    /*if (restoreType == BUNDLE_RESTORE_TYPE_CHAT) {
      long chatId = in.getLong(BUNDLE_CHAT_ID, 0);
      if (chatId != 0) {
        prepareMessagesController(account.tdlib(), chatId);
        return INSTANCE_RESTORED_FULLY;
      }
      return INSTANCE_NOT_RESTORED;
    }*/

    if (restoreType == BUNDLE_RESTORE_TYPE_COMPLEX) {
      int stackSize = in.getInt(BUNDLE_ITEM_COUNT);
      if (stackSize <= 0) {
        return INSTANCE_NOT_RESTORED;
      }

      int restoredCount = 0;
      for (int i = 0; i < stackSize; i++) {
        String keyPrefix = makeBundleItemPrefix(i);
        int controllerId = in.getInt(keyPrefix);
        ViewController<?> c = restoreController(this, account.tdlib(), controllerId, in, keyPrefix + "_");
        if (c == null) {
          continue;
        }
        c.get();
        if (restoredCount == 0) {
          navigation.initController(c);
        } else {
          navigation.insertController(c, 0);
        }
        restoredCount++;
      }
      if (restoredCount > 0) {
        return INSTANCE_RESTORED_NEEDS_MAIN;
      }
    }

    return INSTANCE_NOT_RESTORED;
  }

  private static boolean canSaveController (int id, ViewController<?> c) {
    switch (id) {
      case R.id.controller_settings:
      case R.id.controller_wallpaper:
      case R.id.controller_fontSize:
      case R.id.controller_storageSettings:
        return true;
    }
    return false;
  }

  private static ViewController<?> restoreController (BaseActivity context, Tdlib tdlib, int id, Bundle in, String keyPrefix) {
    ViewController<?> restore;
    switch (id) {
      case R.id.controller_settings:
        return new SettingsController(context, tdlib);
      case R.id.controller_storageSettings:
        return new SettingsCacheController(context, tdlib);
      case R.id.controller_wallpaper:
      case R.id.controller_fontSize: {
        MessagesController m = new MessagesController(context, tdlib);
        m.setArguments(new MessagesController.Arguments(id == R.id.controller_fontSize ? MessagesController.PREVIEW_MODE_FONT_SIZE : MessagesController.PREVIEW_MODE_WALLPAPER, null, null));
        return m;
      }
      case R.id.controller_passcode:
        restore = new PasscodeController(context, tdlib);
        break;
      case R.id.controller_messages:
        restore = new MessagesController(context, tdlib);
        break;
      case R.id.controller_profile:
        restore = new ProfileController(context, tdlib);
        break;
      case R.id.controller_themeSettings:
        restore = new SettingsThemeController(context, tdlib);
        break;
      case R.id.controller_newChannel:
        restore = new CreateChannelController(context, tdlib);
        break;
      case R.id.controller_newGroup:
        restore = new CreateGroupController(context, tdlib);
        break;
      case R.id.controller_notificationSettings:
        restore = new SettingsNotificationController(context, tdlib);
        break;
      case R.id.controller_privacySettings:
        restore = new SettingsPrivacyController(context, tdlib);
        break;
      case R.id.controller_chatSettings:
        restore = new SettingsDataController(context, tdlib);
        break;
      case R.id.controller_privacyKey:
        restore = new SettingsPrivacyKeyController(context, tdlib);
        break;
      case R.id.controller_privacyException:
        restore = new PrivacyExceptionController(context, tdlib);
        break;
      case R.id.controller_networkStats:
        restore = new SettingsNetworkStatsController(context, tdlib);
        break;
      default: {
        return null;
      }
    }
    if (restore.restoreInstanceState(in, keyPrefix)) {
      if (!(restore instanceof PasscodeController) && restore.getChatId() != 0 && tdlib.hasPasscode(restore.getChatId())) {
        if (restore instanceof MessagesController) {
          PasscodeController c = new PasscodeController(context, tdlib);
          TdApi.Chat chat = tdlib.chatStrict(restore.getChatId());
          c.setArguments(new PasscodeController.Args(chat, tdlib.chatPasscode(chat), null));
          return c;
        } else {
          return null;
        }
      }
      return restore;
    }
    return null;
  }

  // Other

  @Override
  protected void onActivityResult (final int requestCode, final int resultCode, final Intent data) {
    if (navigation.isEmpty()) {
      navigation.getStack().addChangeListener(new NavigationStack.ChangeListener() {
        @Override
        public void onStackChanged (NavigationStack stack) {
          if (!stack.isEmpty()) {
            stack.removeChangeListener(this);
            MainActivity.super.onActivityResult(requestCode, resultCode, data);
          }
        }
      });
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private final SparseArrayCompat<MessagesController> messageControllers = new SparseArrayCompat<>();

  private MessagesController createMessagesController (Tdlib tdlib) {
    MessagesController m = new MessagesController(this, tdlib);
    m.setReuseEnabled(true);
    m.get();
    messageControllers.put(tdlib.id(), m);
    return m;
  }

  public void destroyMessageControllers (int exceptAccountId) {
    int size = messageControllers.size();
    for (int i = size - 1; i >= 0; i--) {
      int accountId = messageControllers.keyAt(i);
      if (accountId != exceptAccountId) {
        MessagesController c = messageControllers.valueAt(i);
        c.setDestroyInstance();
        c.destroy();
        messageControllers.removeAt(i);
      }
    }
  }

  public void destroyMessageControllers () {
    final int size = messageControllers.size();
    for (int i = size - 1; i >= 0; i--) {
      MessagesController c = messageControllers.valueAt(i);
      c.setDestroyInstance();
      c.destroy();
      messageControllers.removeAt(i);
    }
  }

  public MessagesController getMessagesController (Tdlib tdlib, boolean createIfNeeded) {
    MessagesController m = messageControllers.get(tdlib.id());
    return m != null ? m : createIfNeeded ? createMessagesController(tdlib) : null;
  }

  private void openMainController (int accountId) {
    if (navigation.isEmpty()) {
      initDefault(accountId, true);
    } else {
      hideSoftwareKeyboard();
      final int stackSize = navigation.getStackSize();
      if (navigation.getStack().isLocked()) {
        return;
      }
      for (int i = stackSize - 2; i >= 1; i--) {
        navigation.getStack().destroy(i);
      }
      ViewController<?> first = navigation.getStack().get(0);
      ViewController<?> replaced = null;
      if (first.getId() != R.id.controller_main || first.tdlibId() != accountId) {
        replaced = new MainController(this, TdlibManager.getTdlib(accountId));
      }
      if (stackSize > 1) {
        if (replaced != null) {
          navigation.getStack().replace(0, replaced);
        }
        navigation.navigateBack();
      } else if (replaced != null) {
        navigation.setControllerAnimated(replaced, false, false);
      }
    }
  }

  private void openMessagesController (int accountId, long chatId, long specificMessageId) {
    final Tdlib tdlib = TdlibManager.instanceForAccountId(accountId).account(accountId).tdlib();
    tdlib.awaitInitialization(() -> {
      tdlib.incrementUiReferenceCount();
      handler.post(() -> {
        final TdlibContext context = new TdlibContext(this, tdlib);
        final TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().onDone(tdlib::decrementUiReferenceCount);
        if (specificMessageId != 0)
          params.highlightMessage(new MessageId(chatId, specificMessageId));
        tdlib.ui().openChat(context, chatId, params);
      });
    });
  }

  private void openPlayerController (int accountId) {
    if (accountId == TdlibAccount.NO_ID) {
      return;
    }
    TdlibAccount account = TdlibManager.instanceForAccountId(accountId).account(accountId);
    PlaybackController c = new PlaybackController(this, account.tdlib());
    if (c.prepare() == -1) {
      if (navigation.isEmpty()) {
        initDefault(tdlib.id(), false);
      }
      return;
    }
    ViewController<?> current = navigation.getCurrentStackItem();
    if (!(current instanceof PlaybackController)) {
      navigateToSafely(c);
    }
  }

  private void openCallController () {
    TdApi.Call call = TdlibManager.instance().calls().getCurrentCall();
    Tdlib tdlib = TdlibManager.instance().calls().getCurrentCallTdlib();

    if (call == null) {
      if (navigation.isEmpty()) {
        initDefault(currentTdlib().id(), false);
      }
      UI.showToast(R.string.CallNoLongerActive, Toast.LENGTH_SHORT);
      return;
    }

    ViewController<?> c = navigation.getCurrentStackItem();
    if (c != null && c.tdlibId() == tdlib.id() && c instanceof CallController && ((CallController) c).compareUserId(call.userId)) {
      ((CallController) c).replaceCall(call);
      return;
    }

    CallController controller = new CallController(this, tdlib);
    controller.setArguments(new CallController.Arguments(call));

    navigateToSafely(controller);
  }

  public void navigateToSafely (@NonNull ViewController<?> c) {
    if (isActivityBusyWithSomething()) {
      c.get();
      c.destroy();
      return;
    }
    if (navigation.isEmpty()) {
      navigation.initController(c);
      insertMainController();
    } else {
      hideSoftwareKeyboard();
      navigation.navigateTo(c);
    }
  }

  public void initDefault (int accountId, boolean allowAsync) {
    if (!navigation.isEmpty()) return;

    TdlibAccount account = TdlibManager.instance().account(accountId);

    MainController c = new MainController(this, account.tdlib());
    if (allowAsync) {
      initWith(c);
    } else {
      navigation.initController(c);
    }
  }

  private boolean madeEmulatorChecks;

  @Override
  public void onResume () {
    super.onResume();
    Log.i("MainActivity.onResume");
    // Log.e("%s", Strings.getHexColor(U.compositeColor(Theme.headerColor(), Theme.getColor(R.id.theme_color_statusBar)), false));
    tdlib.contacts().makeSilentPermissionCheck(this);
    UI.startNotificationService();
    if (!madeEmulatorChecks && !Settings.instance().isEmulator()) {
      madeEmulatorChecks = true;
      Background.instance().post(() -> {
        boolean isEmulator = DeviceUtils.detectEmulator(MainActivity.this);
        if (isEmulator) {
          Settings.instance().markAsEmulator();
        }
      });
    }
  }

  @Override
  public void onDestroy () {
    TdlibManager.instance().global().removeAccountListener(this);
    destroyMessageControllers();

    Log.i("MainActivity.onDestroy");
    Log.close();

    super.onDestroy();
  }
}
