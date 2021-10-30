package org.thunderdog.challegram.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Date: 25/01/2019
 * Author: default
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".sync.provider";
  private static final String ACCOUNT_TYPE = BuildConfig.APPLICATION_ID + ".sync.account";
  private static final String ACCOUNT_NAME = "Telegram";

  private static final String EXTRA_ACCOUNT_ID = "account_id";

  SyncAdapter (Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync (Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
    if (Config.NEED_SYSTEM_SYNC) {
      int accountId = extras != null ? extras.getInt(EXTRA_ACCOUNT_ID, TdlibAccount.NO_ID) : TdlibAccount.NO_ID;
      try {
        TdlibManager.makeSync(getContext(), accountId, TdlibManager.SYNC_CAUSE_SYSTEM_SYNC, 0, !TdlibManager.inUiThread(), 0);
      } catch (Throwable t) {
        Log.e("Failed to perform sync", t);
      }
    }
  }

  private static Account getAccount (Context context) {
    Account newAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
    accountManager.addAccountExplicitly(newAccount, null, null);
    return newAccount;
  }

  static void register (Context context) {
    try {
      Account account = getAccount(context);
      ContentResolver.setIsSyncable(account, AUTHORITY, 1);
      ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
      ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, Settings.instance().getPeriodicSyncFrequencySeconds());
    } catch (SecurityException t) {
      Log.e("Cannot register stub account", t);
    }
  }

  public static boolean isSyncEnabledGlobally () {
    try {
      return ContentResolver.getMasterSyncAutomatically();
    } catch (SecurityException e) {
      Log.e(e);
      return true;
    }
  }

  public static boolean isSyncEnabled (Context context) {
    try {
      return ContentResolver.getSyncAutomatically(getAccount(context), AUTHORITY);
    } catch (SecurityException e) {
      Log.e(e);
      return true;
    }
  }

  public static void turnOnSync (Context context, Tdlib tdlib, boolean needGlobal) {
    try {
      if (needGlobal)
        ContentResolver.setMasterSyncAutomatically(true);
      register(context);
      tdlib.listeners().updateNotificationGlobalSettings();
    } catch (Throwable t) {
      Log.e(t);
    }
  }

  static void performSync (Context context, int accountId) {
    try {
      ContentResolver.requestSync(getAccount(context), AUTHORITY, makeBundle(accountId));
    } catch (SecurityException e) {
      Log.e(e);
    }
  }

  private static Bundle makeBundle (int accountId) {
    Bundle extras;
    if (accountId != TdlibAccount.NO_ID) {
      extras = new Bundle();
      extras.putInt(EXTRA_ACCOUNT_ID, accountId);
    } else {
      extras = Bundle.EMPTY;
    }
    return extras;
  }
}
