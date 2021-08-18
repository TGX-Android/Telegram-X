package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 7/4/18
 * Author: default
 */
public interface TdlibOptionListener {
  default void onTopChatsDisabled (boolean areDisabled) { }
  default void onSentScheduledMessageNotificationsDisabled (boolean areDisabled) { }
  default void onSuggestedLanguagePackChanged (String suggestedLanguagePackId, TdApi.LanguagePackInfo suggestedLanguagePack) { }
  default void onContactRegisteredNotificationsDisabled (boolean areDisabled) { }
  default void onSuggestedActionsChanged (TdApi.SuggestedAction[] addedActions, TdApi.SuggestedAction[] removedActions) { }
  default void onArchiveAndMuteChatsFromUnknownUsersEnabled (boolean enabled) { }
}
