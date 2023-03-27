package org.thunderdog.challegram.voip;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.SystemClock;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import me.vkryl.core.StringUtils;

public class VoIPController{
  public static final int PEER_CAP_GROUP_CALLS=1;

  protected long nativeInst=0;
  protected long callStartTime;
  protected ConnectionStateListener listener;

  public VoIPController(){
    nativeInst=nativeInit(VoIPPersistentConfig.getVoipConfigFile().getAbsolutePath());
  }

  public void start(){
    ensureNativeInstance();
    nativeStart(nativeInst);
  }

  public void connect(){
    ensureNativeInstance();
    nativeConnect(nativeInst);
  }

  public void setRemoteEndpoints(TdApi.CallServer[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer){
    if(endpoints.length==0){
      throw new IllegalArgumentException("endpoints size is 0");
    }
    boolean needFilter = false;
    for(TdApi.CallServer endpoint : endpoints){
      if (endpoint.type.getConstructor() != TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR) {
        needFilter = true;
        continue;
      }
      if(StringUtils.isEmpty(endpoint.ipAddress) && StringUtils.isEmpty(endpoint.ipv6Address)){
        throw new IllegalArgumentException("endpoint "+endpoint+" has empty/null ipv4");
      }
      byte[] peerTag = ((TdApi.CallServerTypeTelegramReflector) endpoint.type).peerTag;
      if(peerTag!=null && peerTag.length!=16){
        throw new IllegalArgumentException("endpoint "+endpoint+" has peer_tag of wrong length");
      }
    }
    if (needFilter) {
      List<TdApi.CallServer> callServers = new ArrayList<>();
      for (TdApi.CallServer endpoint : endpoints) {
        if (endpoint.type.getConstructor() == TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR) {
          callServers.add(endpoint);
        }
      }
      endpoints = callServers.toArray(new TdApi.CallServer[0]);
      if (endpoints.length == 0)
        throw new IllegalArgumentException("no CallServerTypeTelegramReflector found");
    }
    ensureNativeInstance();
    nativeSetRemoteEndpoints(nativeInst, endpoints, allowP2p, tcp, connectionMaxLayer);
  }

  public void setEncryptionKey(byte[] key, boolean isOutgoing){
    if(key.length!=256){
      throw new IllegalArgumentException("key length must be exactly 256 bytes but is "+key.length);
    }
    ensureNativeInstance();
    nativeSetEncryptionKey(nativeInst, key, isOutgoing);
  }

  public static void setNativeBufferSize(int size){
    nativeSetNativeBufferSize(size);
  }

  public void release(){
    ensureNativeInstance();
    nativeRelease(nativeInst);
    nativeInst=0;
  }

  public String getDebugString(){
    ensureNativeInstance();
    return nativeGetDebugString(nativeInst);
  }

  public String getUsedVersion(){
    return getVersion();
  }

  protected void ensureNativeInstance(){
    if(nativeInst==0){
      throw new IllegalStateException("Native instance is not valid");
    }
  }

  public void setConnectionStateListener(ConnectionStateListener connectionStateListener){
    listener=connectionStateListener;
  }

  // called from native code
  private void handleStateChange(@CallState int state){
    if(state== CallState.ESTABLISHED && callStartTime==0)
      callStartTime=SystemClock.elapsedRealtime();
    if(listener!=null){
      listener.onConnectionStateChanged(state);
    }
  }

  // called from native code
  private void handleSignalBarsChange(int count){
    if(listener!=null)
      listener.onSignalBarCountChanged(count);
  }

  // called from native code
  private void groupCallKeyReceived(byte[] key){
    if(listener!=null)
      listener.onGroupCallKeyReceived(key);
  }

  // called from native code
  private void groupCallKeySent(){
    if(listener!=null)
      listener.onGroupCallKeySent();
  }

  // called from native code
  private void callUpgradeRequestReceived(){
    if(listener!=null)
      listener.onCallUpgradeRequestReceived();
  }

  private int netType = CallNetworkType.UNKNOWN;

  public @CallNetworkType int getNetworkType () {
    return netType;
  }

  public void setNetworkType(@CallNetworkType int type){
    ensureNativeInstance();
    nativeSetNetworkType(nativeInst, netType = type);
  }

  public long getCallDuration () {
    return callStartTime != 0 ? SystemClock.elapsedRealtime() - callStartTime : -1;
  }

  public void setMicMute(boolean mute){
    ensureNativeInstance();
    nativeSetMicMute(nativeInst, mute);
  }

  public void setConfig(double recvTimeout, double initTimeout, @DataSavingOption int dataSavingOption, long callID){
    ensureNativeInstance();
    boolean sysAecAvailable=false, sysNsAvailable=false;
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
      try{
        sysAecAvailable=AcousticEchoCanceler.isAvailable();
      }catch(Throwable x){  }
      try {
        sysNsAvailable=NoiseSuppressor.isAvailable();
      } catch (Throwable x) { }
    }
    nativeSetConfig(nativeInst, recvTimeout, initTimeout, dataSavingOption == DataSavingOption.ROAMING ? (U.isRoaming() ? DataSavingOption.MOBILE : DataSavingOption.NEVER) : dataSavingOption,
      !(sysAecAvailable && VoIPServerConfig.getBoolean("use_system_aec", true)),
      !(sysNsAvailable && VoIPServerConfig.getBoolean("use_system_ns", true)),
      true, getLogFilePath(), null,
      false);
  }

  public void debugCtl(int request, int param){
    ensureNativeInstance();
    nativeDebugCtl(nativeInst, request, param);
  }

  public long getPreferredRelayID(){
    ensureNativeInstance();
    return nativeGetPreferredRelayID(nativeInst);
  }

  public int getLastError(){
    ensureNativeInstance();
    return nativeGetLastError(nativeInst);
  }

  public void getStats(NetworkStats stats){
    ensureNativeInstance();
    if(stats==null)
      throw new NullPointerException("You're not supposed to pass null here");
    nativeGetStats(nativeInst, stats);
  }

  public static String getVersion(){
    return nativeGetVersion();
  }

  private String getLogFilePath () {
    Calendar c = Calendar.getInstance();
    File dir = Log.getLogDir();
    cleanupLogDir(dir);
    return new File(Log.getLogDir(),
      String.format(Locale.US, "logs/%s%02d_%02d_%04d_%02d_%02d_%02d.log", Log.CALL_PREFIX,
        c.get(Calendar.DATE), c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))).getAbsolutePath();
  }

  private void cleanupLogDir (File dir) {
    if (dir == null) {
      return;
    }
    File[] _logs = dir.listFiles();
    ArrayList<File> logs = new ArrayList<>();
    logs.addAll(Arrays.asList(_logs));
    while (logs.size() > 20) {
      File oldest = logs.get(0);
      for (File file : logs) {
        if (file.getName().startsWith(Log.CALL_PREFIX) && file.getName().endsWith(".log") && file.lastModified() < oldest.lastModified())
          oldest = file;
      }
      oldest.delete();
      logs.remove(oldest);
    }
  }

  public String getDebugLog(){
    ensureNativeInstance();
    return nativeGetDebugLog(nativeInst);
  }

  public void setProxy(String address, int port, String username, String password){
    ensureNativeInstance();
    if(address==null)
      throw new NullPointerException("address can't be null");
    nativeSetProxy(nativeInst, address, port, username, password);
  }

  public void setAudioOutputGainControlEnabled(boolean enabled){
    ensureNativeInstance();
    nativeSetAudioOutputGainControlEnabled(nativeInst, enabled);
  }

  public int getPeerCapabilities(){
    ensureNativeInstance();
    return nativeGetPeerCapabilities(nativeInst);
  }

  public void sendGroupCallKey(byte[] key){
    if(key==null)
      throw new NullPointerException("key can not be null");
    if(key.length!=256)
      throw new IllegalArgumentException("key must be 256 bytes long, got "+key.length);
    ensureNativeInstance();
    nativeSendGroupCallKey(nativeInst, key);
  }

  public void requestCallUpgrade(){
    ensureNativeInstance();
    nativeRequestCallUpgrade(nativeInst);
  }

  public void setEchoCancellationStrength(int strength){
    ensureNativeInstance();
    nativeSetEchoCancellationStrength(nativeInst, strength);
  }

  public boolean needRate(){
    ensureNativeInstance();
    return nativeNeedRate(nativeInst);
  }

  private native long nativeInit(String persistentStateFile);
  private native void nativeStart(long inst);
  private native void nativeConnect(long inst);
  private static native void nativeSetNativeBufferSize(int size);
  private native void nativeSetRemoteEndpoints(long inst, TdApi.CallServer[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer);
  private native void nativeRelease(long inst);
  private native void nativeSetNetworkType(long inst, int type);
  private native void nativeSetMicMute(long inst, boolean mute);
  private native void nativeDebugCtl(long inst, int request, int param);
  private native void nativeGetStats(long inst, NetworkStats stats);
  private native void nativeSetConfig(long inst, double recvTimeout, double initTimeout, int dataSavingOption, boolean enableAEC, boolean enableNS, boolean enableAGC, String logFilePath, String statsDumpPath, boolean logPacketStats);
  private native void nativeSetEncryptionKey(long inst, byte[] key, boolean isOutgoing);
  private native void nativeSetProxy(long inst, String address, int port, String username, String password);
  private native long nativeGetPreferredRelayID(long inst);
  private native int nativeGetLastError(long inst);
  private native String nativeGetDebugString(long inst);
  private static native String nativeGetVersion();
  private native void nativeSetAudioOutputGainControlEnabled(long inst, boolean enabled);
  private native void nativeSetEchoCancellationStrength(long inst, int strength);
  private native String nativeGetDebugLog(long inst);
  private native int nativeGetPeerCapabilities(long inst);
  private native void nativeSendGroupCallKey(long inst, byte[] key);
  private native void nativeRequestCallUpgrade(long inst);
  private static native boolean nativeNeedRate(long inst);

  public static native int getConnectionMaxLayer();

}
