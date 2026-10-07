/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Resolves persistent remote references as well as IDs from the current TDLib session. */
public final class ArticleFiles {
  private ArticleFiles () { }

  public static String key (TdApi.InputFile file) {
    if (file instanceof TdApi.InputFileId) return "id:" + ((TdApi.InputFileId) file).id;
    if (file instanceof TdApi.InputFileRemote) return "remote:" + ((TdApi.InputFileRemote) file).id;
    return null;
  }

  public static final class Resolved {
    public final Map<String, TdApi.File> files = new ConcurrentHashMap<>();
    public final Map<String, String> names = new ConcurrentHashMap<>();
  }

  /** Completion runs on the TDLib callback thread (or immediately for a document without remote files). */
  public static void resolve (Tdlib tdlib, TdApi.Object[] sources, boolean names, Consumer<Resolved> callback) {
    Map<String, TdApi.Function<TdApi.File>> requests = new HashMap<>();
    for (TdApi.Object source : sources) ArticleCodec.visit(source, (value, depth) -> {
      if (value instanceof TdApi.InputFileId) requests.put(key((TdApi.InputFile) value), new TdApi.GetFile(((TdApi.InputFileId) value).id));
      else if (value instanceof TdApi.InputFileRemote) requests.put(key((TdApi.InputFile) value), new TdApi.GetRemoteFile(((TdApi.InputFileRemote) value).id, null));
    });
    Resolved result = new Resolved();
    if (requests.isEmpty()) { callback.accept(result); return; }
    AtomicInteger remaining = new AtomicInteger(requests.size());
    Runnable done = () -> { if (remaining.decrementAndGet() == 0) callback.accept(result); };
    for (Map.Entry<String, TdApi.Function<TdApi.File>> entry : requests.entrySet()) tdlib.send(entry.getValue(), (file, error) -> {
      if (file != null) result.files.put(entry.getKey(), file);
      if (file == null || !names) { done.run(); return; }
      tdlib.send(new TdApi.GetSuggestedFileName(file.id, ""), (name, nameError) -> {
        if (name != null) result.names.put(entry.getKey(), name.text);
        done.run();
      });
    });
  }
}
