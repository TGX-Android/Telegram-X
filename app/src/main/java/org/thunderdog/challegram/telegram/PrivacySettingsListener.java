package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface PrivacySettingsListener {
  void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules);
}
