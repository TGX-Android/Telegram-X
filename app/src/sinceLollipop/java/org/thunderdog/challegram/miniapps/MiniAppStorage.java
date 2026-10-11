/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Bot/account scoped native storage. Serialized IO, atomic replacement, Keystore encryption. */
public final class MiniAppStorage {
  public static final ExecutorService IO = Executors.newSingleThreadExecutor();
  static final int QUOTA = 5 * 1024 * 1024;
  private static final Object LOCK = new Object();
  private final File directory;
  private final long owner, bot;
  private final boolean secure;
  private final android.content.SharedPreferences names;

  public MiniAppStorage (Context context, long owner, long bot, boolean secure) {
    this(context, owner, bot, secure, false);
  }
  public MiniAppStorage (Context context, long owner, long bot, boolean secure, boolean testServer) {
    if (owner <= 0 || bot <= 0) throw new IllegalArgumentException("Missing storage owner");
    directory = new File(context.getNoBackupFilesDir(), "miniapp_storage/" + (testServer ? "test" : "production"));
    names = context.getSharedPreferences("miniapp_storage_names_" + testServer, Context.MODE_PRIVATE);
    this.owner = owner; this.bot = bot; this.secure = secure;
  }

  private File file (long account) { return rawFile(secure ? identity(account) : account); }
  private File rawFile (long identity) { return new File(directory, (secure ? "secure_" : "device_") + identity + "_" + bot + ".json"); }

  // One secure identity owns ALL bots, as in Telegram Android's BotStorage. The
  // atomic index transfers ownership without copying/decrypting unrelated bots.
  private JSONObject identities () {
    File index = new File(directory, "secure_owners.json");
    if (index.exists() || new File(index + ".bak").exists()) return read(index, false);
    JSONObject result = new JSONObject();
    File[] files = directory.listFiles();
    if (files != null) for (File f : files) {
      java.util.regex.Matcher match = java.util.regex.Pattern.compile("secure_([0-9]+)_[0-9]+\\.json").matcher(f.getName());
      if (match.matches()) try { result.put(match.group(1), Long.parseLong(match.group(1))); } catch (Exception ignored) { }
    }
    return result;
  }
  private long identity (long account) {
    JSONObject index = identities();
    String key = Long.toString(account);
    if (index.has(key)) return index.optLong(key);
    long id;
    java.security.SecureRandom random = new java.security.SecureRandom();
    do { id = random.nextLong() & Long.MAX_VALUE; } while (id == 0 || identityExists(id, index));
    try { index.put(key, id); } catch (org.json.JSONException e) { throw new IllegalStateException("UNKNOWN_ERROR"); }
    write(new File(directory, "secure_owners.json"), index, false);
    return id;
  }
  private boolean identityExists (long id, JSONObject index) {
    java.util.Iterator<String> keys = index.keys();
    while (keys.hasNext()) if (index.optLong(keys.next()) == id) return true;
    File[] files = directory.listFiles((dir, name) -> name.startsWith("secure_" + id + "_"));
    return files != null && files.length != 0;
  }
  public void setOwnerName (String name) { names.edit().putString(Long.toString(owner), name).apply(); }
  public String ownerName (long account) { return names.getString(Long.toString(account), ""); }
  private void supported () {
    if (secure && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) throw new IllegalStateException("UNSUPPORTED");
  }
  public String get (String key) {
    synchronized (LOCK) { supported(); JSONObject data = read(file(owner)); return data.has(key) ? data.optString(key, null) : null; }
  }
  public void set (String key, String value) {
    synchronized (LOCK) {
      supported();
      if (key == null) throw new IllegalArgumentException("KEY_INVALID");
      if (key.length() + (value != null ? value.length() : 0) > QUOTA) throw new IllegalStateException("QUOTA_EXCEEDED");
      JSONObject data = read(file(owner));
      try { if (value == null) data.remove(key); else data.put(key, value); }
      catch (org.json.JSONException error) { throw new IllegalArgumentException("VALUE_INVALID"); }
      if (secure && data.length() > 10) throw new IllegalStateException("QUOTA_EXCEEDED");
      write(file(owner), data);
    }
  }
  public void clear () { synchronized (LOCK) { supported(); write(file(owner), new JSONObject()); } }

  public List<Long> restorable (String key, Set<Long> activeAccounts) {
    synchronized (LOCK) {
      supported();
      if (!secure || read(file(owner)).length() != 0) return new ArrayList<>();
      List<Long> result = new ArrayList<>();
      JSONObject index = identities();
      java.util.Iterator<String> accounts = index.keys();
      while (accounts.hasNext()) {
        String name = accounts.next();
        long account;
        try { account = Long.parseLong(name); }
        catch (NumberFormatException malformed) { continue; }
        if (account == owner || activeAccounts.contains(account)) continue;
        if (read(rawFile(index.optLong(name))).has(key)) result.add(account);
      }
      return result;
    }
  }

  /** Caller must obtain native user consent and recheck the active-account set first. */
  public String restore (long from, String key, Set<Long> activeAccounts) {
    synchronized (LOCK) {
      supported();
      if (read(file(owner)).length() != 0) throw new IllegalStateException("STORAGE_NOT_EMPTY");
      if (!restorable(key, activeAccounts).contains(from)) throw new IllegalStateException("RESTORE_UNAVAILABLE");
      JSONObject data = read(file(from));
      JSONObject index = identities();
      try { index.put(Long.toString(owner), index.getLong(Long.toString(from))); }
      catch (org.json.JSONException e) { throw new IllegalStateException("RESTORE_UNAVAILABLE"); }
      index.remove(Long.toString(from));
      write(new File(directory, "secure_owners.json"), index, false);
      return data.optString(key, null);
    }
  }

  private JSONObject read (File file) {
    return read(file, secure);
  }
  private JSONObject read (File file, boolean encrypted) {
    AtomicFile atomic = new AtomicFile(file);
    if (!file.exists() && !new File(file.getPath() + ".bak").exists()) return new JSONObject();
    try (FileInputStream stream = atomic.openRead()) {
      long length = stream.getChannel().size();
      if (length > QUOTA + 64 || length < 0) throw new IllegalStateException("QUOTA_EXCEEDED");
      byte[] bytes = new byte[(int) length];
      int offset = 0, read;
      while (offset < bytes.length && (read = stream.read(bytes, offset, bytes.length - offset)) != -1) offset += read;
      if (offset != bytes.length) throw new java.io.IOException("Incomplete storage");
      if (encrypted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) bytes = Crypto.decrypt(bytes, file.getName());
      return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    } catch (IllegalStateException error) { throw error; }
    catch (Exception error) { throw new IllegalStateException("UNKNOWN_ERROR"); }
  }

  private void write (File file, JSONObject data) {
    write(file, data, secure);
  }
  private void write (File file, JSONObject data, boolean encrypted) {
    byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > QUOTA) throw new IllegalStateException("QUOTA_EXCEEDED");
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream stream = null;
    try {
      if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Storage unavailable");
      if (encrypted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) bytes = Crypto.encrypt(bytes, file.getName());
      stream = atomic.startWrite();
      stream.write(bytes);
      atomic.finishWrite(stream);
    } catch (Exception error) {
      if (stream != null) atomic.failWrite(stream);
      throw new IllegalStateException("UNKNOWN_ERROR");
    }
  }

  public static void clearAccount (Context context, long owner, boolean testServer) {
    IO.execute(() -> {
      synchronized (LOCK) {
        File[] files = new File(context.getNoBackupFilesDir(), "miniapp_storage/" + (testServer ? "test" : "production")).listFiles();
        if (files != null) for (File file : files) {
          if (file.getName().matches("device_" + owner + "_[0-9]+\\.json(?:\\.bak|\\.new)?")) new AtomicFile(file).delete();
        }
        // Secure storage survives logout and requires explicit native consent to transfer to another account.
      }
    });
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
  private static final class Crypto {
    private static SecretKey key () throws Exception {
      KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
      String alias = "tg-miniapps-storage-v1";
      if (!store.containsAlias(alias)) {
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(alias,
          android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build());
        generator.generateKey();
      }
      return (SecretKey) store.getKey(alias, null);
    }
    static byte[] encrypt (byte[] value, String identity) throws Exception {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
      cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(value), iv = cipher.getIV();
      byte[] result = new byte[1 + iv.length + encrypted.length]; result[0] = (byte) iv.length;
      System.arraycopy(iv, 0, result, 1, iv.length); System.arraycopy(encrypted, 0, result, 1 + iv.length, encrypted.length);
      return result;
    }
    static byte[] decrypt (byte[] value, String identity) throws Exception {
      if (value.length < 29 || value[0] != 12) throw new java.io.IOException("Invalid encrypted storage");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, value, 1, 12));
      cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(value, 13, value.length - 13);
    }
  }
}
