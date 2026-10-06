/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import android.content.Context;
import androidx.core.util.AtomicFile;
import org.drinkless.tdlib.TdApi;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;

/** Crash-safe local recovery. Account, topic and edited-message drafts never share a key. */
public final class ArticleDraftStore {
  private static final int MAGIC = 0x41525432;
  private static final int MAX_DOCUMENT = 16 * 1024 * 1024;
  public static final class Snapshot {
    public final ArticleDocument document, baseline;
    public final long pendingMessageId;
    public Snapshot (ArticleDocument document, ArticleDocument baseline, long pendingMessageId) {
      this.document = document; this.baseline = baseline; this.pendingMessageId = pendingMessageId;
    }
  }
  private final AtomicFile file;
  public ArticleDraftStore (Context context, int accountId, long userId, long chatId, TdApi.MessageTopic topic, long editMessageId) {
    File directory = new File(context.getFilesDir(), "article-drafts");
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create article draft directory");
    file = new AtomicFile(new File(directory, key(accountId, userId, chatId, topic, editMessageId) + ".article"));
  }
  public static String key (int accountId, long userId, long chatId, TdApi.MessageTopic topic, long editMessageId) {
    if (userId == 0) throw new IllegalArgumentException("Article drafts require an authorized user");
    return accountId + "_" + userId + "_" + chatId + "_" + topicKey(topic) + "_" + editMessageId;
  }
  public static String topicKey (TdApi.MessageTopic topic) {
    if (topic == null) return "chat";
    if (topic instanceof TdApi.MessageTopicForum) return "forum_" + ((TdApi.MessageTopicForum) topic).forumTopicId;
    if (topic instanceof TdApi.MessageTopicThread) return "thread_" + ((TdApi.MessageTopicThread) topic).messageThreadId;
    if (topic instanceof TdApi.MessageTopicDirectMessages) return "direct_" + ((TdApi.MessageTopicDirectMessages) topic).directMessagesChatTopicId;
    if (topic instanceof TdApi.MessageTopicSavedMessages) return "saved_" + ((TdApi.MessageTopicSavedMessages) topic).savedMessagesTopicId;
    throw new IllegalArgumentException("Unknown article topic");
  }
  public synchronized Snapshot read () throws IOException {
    if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
    try (FileInputStream stream = file.openRead(); DataInputStream in = new DataInputStream(stream)) {
      if (stream.getChannel().size() > 2L * MAX_DOCUMENT + 20 || in.readInt() != MAGIC) throw new IOException("Unknown article recovery format");
      long pendingId = in.readLong();
      ArticleDocument document = readDocument(in), baseline = readDocument(in);
      if (in.read() != -1) throw new IOException("Trailing article recovery data");
      return new Snapshot(document, baseline, pendingId);
    }
  }
  private static ArticleDocument readDocument (DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > MAX_DOCUMENT) throw new IOException("Invalid article recovery size");
    byte[] bytes = new byte[length]; in.readFully(bytes);
    return ArticleDocument.restore(bytes);
  }
  public synchronized void write (Snapshot snapshot) throws IOException {
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      DataOutputStream out = new DataOutputStream(stream);
      out.writeInt(MAGIC); out.writeLong(snapshot.pendingMessageId);
      for (ArticleDocument document : new ArticleDocument[] {snapshot.document, snapshot.baseline}) {
        byte[] bytes = document.save(); out.writeInt(bytes.length); out.write(bytes);
      }
      out.flush();
      file.finishWrite(stream);
    } catch (IOException e) {
      file.failWrite(stream);
      throw e;
    }
  }
  public synchronized void clearIfUnchanged (ArticleDocument expected) throws IOException {
    Snapshot saved = read();
    if (saved != null && saved.document.equals(expected)) clear();
  }
  public synchronized void clear () { file.delete(); }
}
