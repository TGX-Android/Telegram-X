/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/02/2018
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

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
