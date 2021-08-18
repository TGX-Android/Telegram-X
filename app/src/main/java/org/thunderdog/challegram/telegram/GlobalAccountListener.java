package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/21/18
 * Author: default
 */
public interface GlobalAccountListener {
  default void onAccountProfileChanged (TdlibAccount account, TdApi.User profile, boolean isCurrent, boolean isLoaded) { }
  default void onAccountProfilePhotoChanged (TdlibAccount account, boolean big, boolean isCurrent) { }
  default void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) { }
  default void onAuthorizationStateChanged (TdlibAccount account, TdApi.AuthorizationState authorizationState, int status) { }
  default void onTdlibOptimizing (Tdlib tdlib, boolean isOptimizing) { }
  default void onActiveAccountRemoved (TdlibAccount account, int position) { }
  default void onActiveAccountAdded (TdlibAccount account, int position) { }
  default void onActiveAccountMoved (TdlibAccount account, int fromPosition, int toPosition) { }
}
