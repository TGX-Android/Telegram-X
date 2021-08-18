package org.thunderdog.challegram.sync;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/**
 * Date: 25/01/2019
 * Author: default
 */
public class StubAuthenticator extends AbstractAccountAuthenticator {
  public StubAuthenticator (Context context) {
    super(context);
  }

  // Editing properties is not supported
  @Override
  public Bundle editProperties(
    AccountAuthenticatorResponse r, String s) {
    throw new UnsupportedOperationException();
  }
  // Don't add additional accounts
  @Override
  public Bundle addAccount(
    AccountAuthenticatorResponse r,
    String s,
    String s2,
    String[] strings,
    Bundle bundle) throws NetworkErrorException {
    return null;
  }
  // Ignore attempts to confirm credentials
  @Override
  public Bundle confirmCredentials(
    AccountAuthenticatorResponse r,
    Account account,
    Bundle bundle) throws NetworkErrorException {
    return null;
  }
  // Getting an authentication token is not supported
  @Override
  public Bundle getAuthToken(
    AccountAuthenticatorResponse r,
    Account account,
    String s,
    Bundle bundle) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }
  // Getting a label for the auth token is not supported
  @Override
  public String getAuthTokenLabel(String s) {
    throw new UnsupportedOperationException();
  }
  // Updating user credentials is not supported
  @Override
  public Bundle updateCredentials(
    AccountAuthenticatorResponse r,
    Account account,
    String s, Bundle bundle) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }
  // Checking features for the account is not supported
  @Override
  public Bundle hasFeatures(
    AccountAuthenticatorResponse r,
    Account account, String[] strings) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }
}
