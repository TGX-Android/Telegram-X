package org.thunderdog.challegram.voip;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.voip.annotation.CallNetworkType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TgCallsController extends VoIPInstance {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Version.LEGACY,
    Version.V1,
    Version.V2_4_0_0,
    Version.V2,
    Version.V2_REFERENCE
  })
  public @interface Version {
    int
      LEGACY = 0,
      V1 = 1,
      V2_4_0_0 = 2,
      V2 = 3,
      V2_REFERENCE = 4;
  }

  public static class UnknownVersionException extends IllegalArgumentException {
    public UnknownVersionException (String s) {
      super(s);
    }
  }

  private static @Version int parseVersion (@NonNull String version) throws UnknownVersionException {
    switch (version) {
      case "2.4.4":
        // TODO? InstanceImplLegacy.cpp
        return Version.LEGACY;
      case "2.7.7":
      case "5.0.0":
        // TODO InstanceImpl.cpp
        return Version.V1;
      case "6.0.0":
        // TODO InstanceV2_4_0_0Impl.cpp
        return Version.V2_4_0_0;
      case "7.0.0":
      case "8.0.0":
      case "9.0.0":
        // TODO InstanceV2Impl.cpp
        return Version.V2;
      case "10.0.0":
      case "11.0.0":
        // TODO InstanceV2ReferenceImpl.cpp
        return Version.V2_REFERENCE;
    }
    throw new IllegalArgumentException(version);
  }

  private final String version;
  private final @Version int internalVersion;
  private long nativePtr;
  public TgCallsController (@NonNull CallConfiguration configuration, @NonNull CallOptions options, @NonNull ConnectionStateListener stateListener, String version) {
    super(configuration, options, stateListener);
    this.version = version;
    this.internalVersion = parseVersion(version);
  }

  @Override
  public String getLibraryName () {
    return "tgcalls";
  }

  @Override
  public String getLibraryVersion () {
    return version;
  }

  @Override
  public void initializeAndConnect () {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleAudioOutputGainControlEnabled (boolean isEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleEchoCancellationStrengthChange (int strength) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleMicDisabled (boolean isDisabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleNetworkTypeChange (@CallNetworkType int type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CharSequence collectDebugLog () {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getConnectionId () {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getNetworkStats (NetworkStats out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void performDestroy () {
    throw new UnsupportedOperationException();
  }
}
