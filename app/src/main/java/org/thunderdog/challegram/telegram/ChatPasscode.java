package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;

public class ChatPasscode {
  public static final int FLAG_INVISIBLE = 1;
  public static final int FLAG_STRONG_BIOMETRICS = 1 << 1;

  public int mode;
  private int flags;
  public String hash;
  public @Nullable String biometricsHash;

  public ChatPasscode (int mode, int flags, String hash, @Nullable String biometricsHash) {
    this.mode = mode;
    this.flags = flags;
    this.hash = hash;
    this.biometricsHash = biometricsHash;
  }

  public boolean isVisible () {
    return (flags & FLAG_INVISIBLE) == 0;
  }

  public void setIsVisible (boolean isVisible) {
    flags = BitwiseUtils.setFlag(flags, FLAG_INVISIBLE, !isVisible);
  }

  public void setBiometrics (String hash, boolean strong) {
    biometricsHash = hash;
    setRequireStrongBiometrics(strong);
  }
  public void setRequireStrongBiometrics (boolean require) {
    flags = BitwiseUtils.setFlag(flags, FLAG_STRONG_BIOMETRICS, require);
  }

  public boolean requireStrongBiometrics () {
    return BitwiseUtils.hasFlag(flags, FLAG_STRONG_BIOMETRICS);
  }

  public boolean unlock (int mode, String hash) {
    return this.mode == mode && this.hash.equals(hash);
  }

  public boolean unlockWitBiometrics (String biometricsHash, boolean strong) {
    return !StringUtils.isEmpty(this.biometricsHash) && this.biometricsHash.equals(biometricsHash) && (strong || !requireStrongBiometrics());
  }

  @Override
  @NonNull
  public final String toString () {
    StringBuilder b = new StringBuilder()
      .append(Tdlib.CLIENT_DATA_VERSION)
      .append('_')
      .append(mode)
      .append('_')
      .append(flags)
      .append('_')
      .append(hash.length())
      .append('_')
      .append(hash);
    if (!StringUtils.isEmpty(biometricsHash)) {
      b.append('_').append(biometricsHash.length()).append('_').append(biometricsHash);
    }
    return b.toString();
  }

  public static int makeFlags (boolean isVisible) {
    int flags = 0;
    if (!isVisible) {
      flags |= FLAG_INVISIBLE;
    }
    return flags;
  }
}
