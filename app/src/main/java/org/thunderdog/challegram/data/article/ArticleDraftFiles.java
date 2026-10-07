/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/** Resolves TDLib's deduplicated local file paths without confusing different attachments. */
public final class ArticleDraftFiles {
  private ArticleDraftFiles () { }

  /** TDLib can merge IDs after uploading or deduplicating an already received file. */
  public static Map<String, Integer> identities (Map<Integer, TdApi.File> files) {
    Map<String, Integer> result = new HashMap<>(), canonical = new HashMap<>();
    for (Map.Entry<Integer, TdApi.File> entry : files.entrySet()) {
      TdApi.File file = entry.getValue();
      String unique = file.remote != null && file.remote.uniqueId != null && !file.remote.uniqueId.isEmpty() ? "remote:" + file.remote.uniqueId :
        file.local != null && file.local.path != null && !file.local.path.isEmpty() ? "path:" + file.local.path : "id:" + file.id;
      Integer id = canonical.get(unique);
      if (id == null) { id = file.id; canonical.put(unique, id); }
      result.put("id:" + entry.getKey(), id); result.put("id:" + file.id, id);
      if (file.local != null && file.local.path != null && !file.local.path.isEmpty()) result.put(file.local.path, id);
    }
    return result;
  }

  /** Disk IO: call on the import executor, never the UI or TDLib callback thread. */
  public static Map<String, Integer> aliases (ArticleDocument local, Map<String, Integer> received) {
    Map<String, Integer> result = new HashMap<>(received);
    Map<String, String> fingerprints = new HashMap<>();
    ArticleCodec.visit(local.toInput(), (value, depth) -> {
      if (!(value instanceof TdApi.InputFileLocal)) return;
      String path = ((TdApi.InputFileLocal) value).path;
      if (result.containsKey(path)) return;
      File file = new File(path);
      for (Map.Entry<String, Integer> candidate : received.entrySet()) {
        File other = new File(candidate.getKey());
        if (!file.isFile() || !other.isFile() || file.length() != other.length()) continue;
        String left = fingerprint(path, fingerprints), right = fingerprint(candidate.getKey(), fingerprints);
        if (left != null && left.equals(right)) { result.put(path, candidate.getValue()); break; }
      }
    });
    return result;
  }

  private static String fingerprint (String path, Map<String, String> cache) {
    if (cache.containsKey(path)) return cache.get(path);
    String result = null;
    try (FileInputStream stream = new FileInputStream(path)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536]; int length;
      while ((length = stream.read(buffer)) != -1) digest.update(buffer, 0, length);
      result = java.util.Arrays.toString(digest.digest());
    } catch (IOException | NoSuchAlgorithmException ignored) { }
    cache.put(path, result);
    return result;
  }
}
