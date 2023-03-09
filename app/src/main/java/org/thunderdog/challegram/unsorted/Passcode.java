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
 * File created on 04/08/2015 at 11:34
 */
package org.thunderdog.challegram.unsorted;

import android.content.SharedPreferences;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.reference.ReferenceList;

public class Passcode implements UI.StateListener {
  public interface LockListener {
    void onPasscodeLocked (boolean isLocked);
  }

  private static Passcode instance;

  public static Passcode instance () {
    if (instance == null) {
      instance = new Passcode();
    }
    return instance;
  }

  public static final int MODE_NONE = 0x00;
  public static final int MODE_PINCODE  = 0x01;
  public static final int MODE_PASSWORD = 0x02;
  public static final int MODE_PATTERN  = 0x03;
  public static final int MODE_GESTURE  = 0x04;
  public static final int MODE_FINGERPRINT = 0x05;

  public static boolean isValidMode (int mode) {
    switch (mode) {
      case MODE_PINCODE:
      case MODE_PASSWORD:
      case MODE_PATTERN:
      case MODE_GESTURE:
      case MODE_FINGERPRINT:
        return true;
    }
    return false;
  }

  public static final int AUTOLOCK_MODE_NONE = 0x00;
  public static final int AUTOLOCK_MODE_1MIN = 0x01;
  public static final int AUTOLOCK_MODE_5MINS = 0x02;
  public static final int AUTOLOCK_MODE_1HOUR = 0x03;
  public static final int AUTOLOCK_MODE_5HOURS = 0x04;
  public static final int AUTOLOCK_MODE_INSTANT = 0x05;

  public static final int STATE_UNLOCK  = 0x01;
  public static final int STATE_CHOOSE  = 0x02;
  public static final int STATE_CONFIRM = 0x03;

  public static final int MIN_PASSWORD_SIZE = 1;
  public static final int MIN_PATTERN_SIZE = 4;
  public static final int PINCODE_SIZE = 4;

  @SuppressWarnings("SpellCheckingInspection")
  private static final String SALT_OLD = "VGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNhbHQgb2YgQ2hhbGxlZ3JhbS4=";
  private static final String SALT_NEW = "VGhpcyBpcyB0aGUgbW9yZSBzZWN1cmUgc2FsdCBvZiBUZWxlZ3JhbSBY";

  private int mode;
  private Boolean _visible, _allowScreenshots, _displayNotifications;

  private int autolockMode;
  private long autolockTime;

  private String passcodeHash, fingerprintHash;

  private boolean isLocked;
  private final ReferenceList<LockListener> listeners = new ReferenceList<>(true);

  public static final String KEY_PASSCODE_MODE = "pc_mode";
  public static final String KEY_PASSCODE_HASH = "pc_hash";
  private static final String KEY_PASSCODE_LOCKED = "pc_locked";
  private static final String KEY_PASSCODE_VISIBLE = "pc_visible";
  private static final String KEY_PASSCODE_ALLOW_SCREENSHOTS = "pc_allow_ss";
  private static final String KEY_PASSCODE_AUTOLOCK_MODE = "pc_al_mode";
  private static final String KEY_PASSCODE_AUTOLOCK_TIME = "pc_time";
  private static final String KEY_PASSCODE_DISPLAY_NOTIFICATIONS = "pc_notifications";
  public static final String KEY_PASSCODE_FINGERPRINT_HASH = "pc_finger_hash";

  private Passcode () {
    Settings prefs = Settings.instance();
    mode = prefs.getInt(KEY_PASSCODE_MODE, MODE_NONE);
    autolockMode = prefs.getInt(KEY_PASSCODE_AUTOLOCK_MODE, AUTOLOCK_MODE_NONE);

    fingerprintHash = prefs.getString(KEY_PASSCODE_FINGERPRINT_HASH, null);

    if (mode != MODE_NONE) {
      isLocked = prefs.getBoolean(KEY_PASSCODE_LOCKED, false);
      if (autolockMode != AUTOLOCK_MODE_NONE) {
        autolockTime = prefs.getLong(KEY_PASSCODE_AUTOLOCK_TIME, 0);
        checkAutoLock();
      }
      passcodeHash = prefs.getString(KEY_PASSCODE_HASH, null);
    }

    UI.addStateListener(this);
  }

  public boolean displayNotifications () {
    if (_displayNotifications == null)
      _displayNotifications = Settings.instance().getBoolean(KEY_PASSCODE_DISPLAY_NOTIFICATIONS, false);
    return _displayNotifications;
  }

  public void setDisplayNotifications (boolean display) {
    this._displayNotifications = display;
    if (display)
      Settings.instance().putBoolean(KEY_PASSCODE_DISPLAY_NOTIFICATIONS, display);
    else
      Settings.instance().remove(KEY_PASSCODE_DISPLAY_NOTIFICATIONS);
  }

  @Override
  public void onUiStateChanged (int newState) {
    if (newState == UI.STATE_PAUSED) {
      trackUserActivity(true);
    }
  }

  public void addLockListener (LockListener listener) {
    listeners.add(listener);
  }

  public void removeLockListener (LockListener listener) {
    listeners.remove(listener);
  }

  private void notifyLockListeners (boolean isLocked) {
    for (LockListener listener : listeners) {
      listener.onPasscodeLocked(isLocked);
    }
  }

  private void setLocked (boolean isLocked) {
    if (this.isLocked != isLocked) {
      this.isLocked = isLocked;
      SharedPreferences.Editor editor = Settings.instance().edit();
      editor.putBoolean(KEY_PASSCODE_LOCKED, isLocked);
      if (!isLocked) {
        autolockTime = System.currentTimeMillis() + getAutolockTime();
        editor.putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
      }
      editor.apply();
      UI.checkDisallowScreenshots();
      notifyLockListeners(isLocked);
    }
  }

  public boolean toggleLock () {
    setLocked(!isLocked);
    return isLocked;
  }

  public void setAutolockMode (int mode) {
    if (this.autolockMode != mode) {
      this.autolockMode = mode;
      this.autolockTime = mode == AUTOLOCK_MODE_NONE ? 0l : System.currentTimeMillis() + getAutolockTime();
      SharedPreferences.Editor prefs = Settings.instance().edit();
      prefs.putInt(KEY_PASSCODE_AUTOLOCK_MODE, mode);
      prefs.putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
      prefs.apply();
      UI.checkDisallowScreenshots();
    }
  }

  public long getAutolockTime () {
    switch (autolockMode) {
      case AUTOLOCK_MODE_1MIN: return TimeUnit.MINUTES.toMillis(1);
      case AUTOLOCK_MODE_5MINS: return TimeUnit.MINUTES.toMillis(5);
      case AUTOLOCK_MODE_1HOUR: return TimeUnit.HOURS.toMillis(1);
      case AUTOLOCK_MODE_5HOURS: return TimeUnit.HOURS.toMillis(5);
      case AUTOLOCK_MODE_INSTANT: return TimeUnit.MILLISECONDS.toMillis(170);
    }
    return 0;
  }

  public boolean needUnlockByFingerprint () {
    return fingerprintHash != null;
  }

  public void disableUnlockByFingerprint () {
    if (fingerprintHash != null) {
      fingerprintHash = null;
      Settings.instance().remove(KEY_PASSCODE_FINGERPRINT_HASH);
    }
  }

  public void enableUnlockByFingerprint (int fingerId) {
    this.fingerprintHash = getPasscodeHashOld(String.valueOf(fingerId));
    Settings.instance().putString(KEY_PASSCODE_FINGERPRINT_HASH, fingerprintHash);
  }

  public int getAutolockMode () {
    return autolockMode;
  }

  private long lastAutolockTraceTime;

  public void trackUserActivity (boolean force) {
    if (autolockMode != AUTOLOCK_MODE_NONE) {
      long time = System.currentTimeMillis();
      if (force || lastAutolockTraceTime == 0 || time - lastAutolockTraceTime >= 10000l) {
        lastAutolockTraceTime = time;
        autolockTime = time + getAutolockTime();
        Settings.instance().putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
        if (force) {
          checkAutoLock();
        }
      }
    }
  }

  public boolean checkAutoLock () {
    if (autolockMode != AUTOLOCK_MODE_NONE && autolockTime > 0l && System.currentTimeMillis() >= autolockTime) {
      setLocked(true);
      return true;
    }
    return false;
  }

  public boolean allowScreenshots () {
    if (_allowScreenshots == null)
      _allowScreenshots = Settings.instance().getBoolean(KEY_PASSCODE_ALLOW_SCREENSHOTS, false);
    return _allowScreenshots;
  }

  public void setAllowScreenshots (boolean allowScreenshots) {
    this._allowScreenshots = allowScreenshots;
    if (allowScreenshots) {
      Settings.instance().putBoolean(KEY_PASSCODE_ALLOW_SCREENSHOTS, true);
    } else {
      Settings.instance().remove(KEY_PASSCODE_ALLOW_SCREENSHOTS);
    }
  }

  public boolean shouldDisallowScreenshots () {
    return !allowScreenshots() && (isLocked() || (autolockMode != AUTOLOCK_MODE_NONE && mode != MODE_NONE));
  }

  public boolean isLocked () {
    return isEnabled() && (checkAutoLock() || isLocked);
  }

  public boolean isLockedAndVisible () {
    if (isLocked()) {
      BaseActivity activity = UI.getUiContext();
      return UI.getUiState() != UI.STATE_RESUMED || activity == null || activity.isPasscodeShowing();
    }
    return false;
  }

  public void setVisible (boolean visible) {
    this._visible = visible;
    Settings.instance().putBoolean(KEY_PASSCODE_VISIBLE, visible);
  }

  public boolean isVisible () {
    if (_visible == null)
      _visible = Settings.instance().getBoolean(KEY_PASSCODE_VISIBLE, true);
    return _visible;
  }

  public boolean canBeInvisible () {
    return canBeInvisible(mode);
  }

  public static boolean canBeInvisible (int mode) {
    return mode == MODE_PINCODE || (!Config.DISABLE_PASSWORD_INVISIBILITY && mode == MODE_PASSWORD) || mode == MODE_PATTERN || mode == MODE_GESTURE;
  }

  public boolean isEnabled () {
    return mode != MODE_NONE;
  }

  public void disable () {
    if (mode != MODE_NONE) {
      mode = MODE_NONE;
      Settings.instance().putInt(KEY_PASSCODE_MODE, MODE_NONE);
      setLocked(false);
    }
  }

  public int getMode () {
    return mode;
  }

  public static String getPasscodeHash (String passcode) {
    return passcode != null ? U.sha256(U.sha256(passcode + SALT_NEW)) : null;
  }

  private static String getPasscodeHashOld (String passcode) {
    return passcode != null ? U.md5(U.md5(passcode + SALT_OLD)) : null;
  }

  public void setPasscodeHash (int mode, String passcode) {
    boolean turnedOn = this.mode == MODE_NONE && mode != MODE_NONE;
    this.mode = mode;
    this.passcodeHash = getPasscodeHashOld(passcode);
    SharedPreferences.Editor edit = Settings.instance().edit();
    edit.putInt(KEY_PASSCODE_MODE, mode);
    if (passcodeHash != null) {
      edit.putString(KEY_PASSCODE_HASH, passcodeHash);
    } else {
      edit.remove(KEY_PASSCODE_HASH);
    }
    edit.apply();
    if (turnedOn) {
      TdlibManager.instance().onUpdateAllNotifications();
    }
  }

  public boolean comparePincode (String pincode) {
    return passcodeHash != null && pincode != null && pincode.length() == PINCODE_SIZE && passcodeHash.equals(getPasscodeHashOld(pincode));
  }

  public boolean comparePassword (String password) {
    return passcodeHash != null && password != null && password.length() >= MIN_PASSWORD_SIZE && passcodeHash.equals(getPasscodeHashOld(password));
  }

  public boolean comparePattern (String pattern) {
    return passcodeHash != null && pattern != null && pattern.length() >= MIN_PATTERN_SIZE && passcodeHash.equals(getPasscodeHashOld(pattern));
  }

  public boolean compareFinger (int fingerId) {
    if (mode == MODE_FINGERPRINT) {
      return passcodeHash != null && passcodeHash.equals(getPasscodeHashOld(String.valueOf(fingerId)));
    } else {
      return fingerprintHash != null && fingerprintHash.equals(getPasscodeHashOld(String.valueOf(fingerId)));
    }
  }

  public static boolean isValidPincode (String pincode) {
    return pincode != null && pincode.length() == PINCODE_SIZE;
  }

  public void setPincode (String pincode) {
    setPasscodeHash(MODE_PINCODE, pincode);
  }

  public static boolean isValidPassword (String password) {
    return password != null && password.length() >= MIN_PASSWORD_SIZE;
  }

  public void setPassword (String password) {
    setPasscodeHash(MODE_PASSWORD, password);
  }

  public void setGesture () {
    setPasscodeHash(MODE_GESTURE, "");
  }

  public static boolean isValidPattern (String pattern) {
    return pattern != null && pattern.length() >= MIN_PATTERN_SIZE;
  }

  public void setPattern (String pattern) {
    setPasscodeHash(MODE_PATTERN, pattern);
  }

  public void setFingerprint (int fingerId) {
    setPasscodeHash(MODE_FINGERPRINT, String.valueOf(fingerId));
  }

  public boolean unlockByPassword (String password) {
    if (comparePassword(password)) {
      setLocked(false);
      return true;
    }
    return false;
  }

  public boolean unlockByPincode (String pincode) {
    if (comparePincode(pincode)) {
      setLocked(false);
      return true;
    }
    return false;
  }

  public void unlock () {
    setLocked(false);
  }

  public boolean unlockByPattern (String pattern) {
    if (comparePattern(pattern)) {
      setLocked(false);
      return true;
    }
    return false;
  }

  public boolean unlockByFinger (int fingerId) {
    if (compareFinger(fingerId)) {
      setLocked(false);
      return true;
    }
    return false;
  }

  // Strings

  public String getModeName () { // Used in Settings screen
    return getModeName(mode);
  }

  public static String getModeName (int mode) {
    switch (mode) {
      case MODE_NONE: return Lang.getString(R.string.PasscodeSettingDisabled);
      case MODE_PINCODE: return Lang.getString(R.string.PasscodePIN);
      case MODE_PASSWORD: return Lang.getString(R.string.login_Password);
      case MODE_PATTERN: return Lang.getString(R.string.PasscodePattern);
      case MODE_GESTURE: return Lang.getString(R.string.PasscodeGesture);
      case MODE_FINGERPRINT: return Lang.getString(R.string.PasscodeFingerprint);
    }
    return "ERROR";
  }

  public String[] getAutolockModeNames () { // Auto-Lock
    return new String[] {
      Lang.getString(R.string.AutoLockDisabled),
      Lang.plural(R.string.inXMinutes, 1),
      Lang.plural(R.string.inXMinutes, 5),
      Lang.plural(R.string.inXHours, 1),
      Lang.plural(R.string.inXHours, 5),
      Lang.getString(R.string.AutoLockInstant)
    };
  }

  public static String getActionName (int mode, int state) {
    switch (mode) {
      case MODE_PINCODE: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPIN);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPIN);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPIN);
        }
        return null;
      }
      case MODE_PASSWORD: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPassword);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPassword);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPassword);
        }
        return null;
      }
      case MODE_PATTERN: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPattern);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPattern);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPattern);
        }
        return null;
      }
      case MODE_GESTURE:  {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByGesture);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourGesture);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourGesture);
        }
        return null;
      }
      case MODE_FINGERPRINT: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByFingerprint);
          case STATE_CHOOSE: return Lang.getString(R.string.TouchYourSensor);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourFingerprint);
        }
        return null;
      }
    }
    return null;
  }
}
