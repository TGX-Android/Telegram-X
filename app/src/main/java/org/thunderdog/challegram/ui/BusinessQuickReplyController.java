/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

import tgx.td.Td;

final class BusinessQuickReplyController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final long chatId, recipientId, account;
  private final int shortcutId;
  private final BooleanSupplier validChat;
  private TdApi.QuickReplyShortcut shortcut;
  private TdApi.QuickReplyMessage[] messages;
  private SettingsAdapter adapter;
  private final Set<Long> selected = new LinkedHashSet<>();
  private boolean loading, sending;

  BusinessQuickReplyController (Context context, Tdlib tdlib, long chatId, long recipientId,
                                TdApi.QuickReplyShortcut shortcut, BooleanSupplier validChat) {
    super(context, tdlib);
    this.chatId = chatId;
    this.recipientId = recipientId;
    this.account = tdlib.myUserId();
    this.shortcut = shortcut;
    this.shortcutId = shortcut.id;
    this.validChat = validChat;
  }

  @Override public int getId () { return R.id.controller_tgxQuickReplyPreview; }
  @Override public CharSequence getName () { return "/" + shortcut.name; }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this) {
      @Override protected SettingHolder initCustom (ViewGroup parent) {
        return new SettingHolder(new Thumbnail(context));
      }

      @Override protected void setCustom (ListItem item, SettingHolder holder, int position) {
        ((Thumbnail) holder.itemView).bind((MediaPreview) item.getData());
      }

      @Override public void onViewRecycled (SettingHolder holder) {
        if (holder.itemView instanceof Thumbnail thumbnail) {
          thumbnail.bind(null);
        }
        super.onViewRecycled(holder);
      }
    };
    recycler.setAdapter(adapter);
    load();
  }

  private void load () {
    if (loading || sending || account == 0 || account != tdlib.myUserId()) {
      return;
    }
    loading = true;
    messages = null;
    selected.clear();
    render();
    tdlib.send(new TdApi.LoadQuickReplyShortcutMessages(shortcutId), (ok, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      if (account != tdlib.myUserId()) {
        return;
      }
      TdApi.QuickReplyShortcut fresh = findShortcut();
      TdApi.QuickReplyMessage[] loaded = tdlib.quickReplyMessages(shortcutId);
      if (error != null) {
        UI.showError(error);
      } else if (fresh != null && completeSnapshot(loaded, fresh.messageCount)) {
        shortcut = fresh;
        messages = loaded;
        for (TdApi.QuickReplyMessage message : messages) {
          selected.add(message.id);
        }
      }
      render();
    }));
  }

  private TdApi.QuickReplyShortcut findShortcut () {
    for (TdApi.QuickReplyShortcut item : tdlib.quickReplyShortcuts()) {
      if (item.id == shortcutId) {
        return item;
      }
    }
    return null;
  }

  static boolean completeSnapshot (TdApi.QuickReplyMessage[] items, int count) {
    if (items == null || count < 1 || items.length != count) {
      return false;
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (TdApi.QuickReplyMessage item : items) {
      if (item == null || item.id <= 0 || item.sendingState != null || item.content == null || !ids.add(item.id)) {
        return false;
      }
    }
    return true;
  }

  static ArrayList<TdApi.QuickReplyMessage> orderedSelection (TdApi.QuickReplyMessage[] messages, Set<Long> selected) {
    ArrayList<TdApi.QuickReplyMessage> result = new ArrayList<>();
    for (TdApi.QuickReplyMessage message : messages) {
      if (selected.contains(message.id)) {
        result.add(message);
      }
    }
    return result;
  }

  private void render () {
    ArrayList<ListItem> rows = new ArrayList<>();
    if (loading || sending) {
      rows.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else if (messages == null) {
      rows.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxBusinessChanged));
      rows.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxQuickReplyReload, 0, R.string.RetryChatList));
    } else {
      rows.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getString(R.string.TgxBusinessQuickReplyTarget, tdlib.chatTitle(chatId))));
      for (TdApi.QuickReplyMessage message : messages) {
        TdApi.Message preview = new TdApi.Message();
        preview.chatId = chatId;
        preview.isOutgoing = true;
        preview.senderId = new TdApi.MessageSenderUser(account);
        preview.content = message.content;
        ContentPreview summary = ContentPreview.getChatListPreview(tdlib, chatId, preview, false);
        rows.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE, R.id.btn_tgxQuickReplySelect, 0,
          summary != null ? summary.toString() : Lang.getString(R.string.TgxBusinessQuickReplyMedia))
          .setSelected(selected.contains(message.id)).setLongId(message.id));
        MediaPreview media = mediaPreview(preview);
        if (media != null) {
          rows.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE).setData(media));
        }
      }
      if (!selected.isEmpty()) {
        rows.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_tgxQuickReplyConfirm, 0,
          Lang.getString(R.string.TgxBusinessQuickReplySendSelected, Lang.formatNumber(selected.size()))));
      }
    }
    adapter.setItems(rows, false);
  }

  private MediaPreview mediaPreview (TdApi.Message message) {
    return switch (message.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR, TdApi.MessageVideo.CONSTRUCTOR, TdApi.MessageAnimation.CONSTRUCTOR,
           TdApi.MessageSticker.CONSTRUCTOR, TdApi.MessageDocument.CONSTRUCTOR, TdApi.MessageAudio.CONSTRUCTOR,
           TdApi.MessageVideoNote.CONSTRUCTOR, TdApi.MessageText.CONSTRUCTOR ->
        MediaPreview.valueOf(tdlib, message, null, Screen.dp(68), Screen.dp(5));
      default -> null;
    };
  }

  @Override
  public void onClick (View view) {
    if (!current() || loading || sending) {
      return;
    }
    int id = view.getId();
    if (id == R.id.btn_tgxQuickReplyReload) {
      load();
    } else if (messages != null && id == R.id.btn_tgxQuickReplySelect && view.getTag() instanceof ListItem item) {
      if (!selected.remove(item.getLongId())) {
        selected.add(item.getLongId());
      }
      render();
    } else if (messages != null && id == R.id.btn_tgxQuickReplyConfirm && !selected.isEmpty()) {
      ArrayList<TdApi.QuickReplyMessage> chosen = orderedSelection(messages, selected);
      boolean whole = chosen.size() == messages.length;
      ArrayList<TdApi.InputMessageContent[]> batches = whole ? null : prepareSubset(chosen);
      if (!whole && batches == null) {
        UI.showToast(R.string.TgxBusinessQuickReplySubsetUnavailable, Toast.LENGTH_LONG);
        return;
      }
      showConfirm(Lang.getString(R.string.TgxBusinessQuickReplyConfirmTarget, Lang.formatNumber(chosen.size()), tdlib.chatTitle(chatId)),
        Lang.getString(R.string.TgxBusinessSendShortcut), () -> send(batches));
    }
  }

  private ArrayList<TdApi.InputMessageContent[]> prepareSubset (ArrayList<TdApi.QuickReplyMessage> chosen) {
    ArrayList<TdApi.InputMessageContent[]> batches = new ArrayList<>();
    for (int i = 0; i < chosen.size();) {
      TdApi.QuickReplyMessage first = chosen.get(i);
      ArrayList<TdApi.InputMessageContent> batch = new ArrayList<>();
      do {
        TdApi.QuickReplyMessage message = chosen.get(i++);
        if (!canCopySelected(message)) {
          return null;
        }
        TdApi.InputMessageContent content = toInput(message.content);
        if (content == null) {
          return null;
        }
        batch.add(content);
      } while (i < chosen.size() && first.mediaAlbumId != 0 && chosen.get(i).mediaAlbumId == first.mediaAlbumId);
      batches.add(batch.toArray(new TdApi.InputMessageContent[0]));
    }
    return batches;
  }

  static boolean canCopySelected (TdApi.QuickReplyMessage message) {
    return message.viaBotUserId == 0 && message.replyMarkup == null && message.replyToMessageId == 0;
  }

  static TdApi.InputMessageContent toInput (TdApi.MessageContent content) {
    if (content instanceof TdApi.MessageText text) {
      return new TdApi.InputMessageText(text.text, text.linkPreviewOptions, false);
    }
    if (content instanceof TdApi.MessagePhoto photo && !photo.isSecret && !photo.photo.hasStickers) {
      TdApi.PhotoSize largest = Td.findBiggest(photo.photo);
      if (largest != null && largest.photo.id > 0) {
        return new TdApi.InputMessagePhoto(new TdApi.InputPhoto(new TdApi.InputFileId(largest.photo.id),
          null, null, new int[0], largest.width, largest.height), photo.caption, photo.showCaptionAboveMedia, null, photo.hasSpoiler);
      }
    }
    if (content instanceof TdApi.MessageVideo video && !video.isSecret && !video.video.hasStickers) {
      TdApi.Video file = video.video;
      TdApi.PhotoSize cover = video.cover != null ? Td.findBiggest(video.cover) : null;
      return new TdApi.InputMessageVideo(new TdApi.InputVideo(new TdApi.InputFileId(file.video.id), null,
        cover != null ? new TdApi.InputFileId(cover.photo.id) : null, video.startTimestamp, new int[0],
        file.duration, file.width, file.height, file.supportsStreaming),
        video.caption, video.showCaptionAboveMedia, null, video.hasSpoiler);
    }
    if (content instanceof TdApi.MessageAnimation animation && !animation.isSecret && !animation.animation.hasStickers) {
      TdApi.Animation file = animation.animation;
      return new TdApi.InputMessageAnimation(new TdApi.InputAnimation(new TdApi.InputFileId(file.animation.id), null,
        new int[0], file.duration, file.width, file.height), animation.caption, animation.showCaptionAboveMedia, animation.hasSpoiler);
    }
    if (content instanceof TdApi.MessageDocument document) {
      return new TdApi.InputMessageDocument(new TdApi.InputDocument(new TdApi.InputFileId(document.document.document.id),
        null, true), document.caption);
    }
    if (content instanceof TdApi.MessageAudio audio) {
      TdApi.Audio file = audio.audio;
      return new TdApi.InputMessageAudio(new TdApi.InputAudio(new TdApi.InputFileId(file.audio.id), null,
        file.duration, file.title, file.performer), audio.caption);
    }
    if (content instanceof TdApi.MessageSticker sticker) {
      TdApi.Sticker file = sticker.sticker;
      return new TdApi.InputMessageSticker(new TdApi.InputSticker(new TdApi.InputFileId(file.sticker.id), null,
        file.width, file.height), file.emoji);
    }
    if (content instanceof TdApi.MessageVoiceNote note) {
      TdApi.VoiceNote file = note.voiceNote;
      return new TdApi.InputMessageVoiceNote(new TdApi.InputVoiceNote(new TdApi.InputFileId(file.voice.id),
        file.duration, file.waveform), note.caption, null);
    }
    if (content instanceof TdApi.MessageVideoNote note && !note.isSecret) {
      TdApi.VideoNote file = note.videoNote;
      return new TdApi.InputMessageVideoNote(new TdApi.InputVideoNote(new TdApi.InputFileId(file.video.id),
        null, file.duration, file.length), null);
    }
    return null;
  }

  private boolean current () {
    return isFocused() && !isDestroyed() && account != 0 && account == tdlib.myUserId() && validChat.getAsBoolean();
  }

  private boolean sameTemplate () {
    TdApi.QuickReplyShortcut fresh = findShortcut();
    return fresh != null && fresh.messageCount == shortcut.messageCount && fresh.name.equals(shortcut.name) &&
      tdlib.quickReplyMessages(shortcutId) == messages;
  }

  private void send (ArrayList<TdApi.InputMessageContent[]> batches) {
    if (!current() || sending || messages == null) {
      return;
    }
    sending = true;
    render();
    tdlib.send(new TdApi.GetUserFullInfo(recipientId), (profile, error) -> runOnUiThreadOptional(() -> {
      if (!current() || error != null || profile == null) {
        finish(error, false, 0);
      } else if (profile.outgoingPaidMessageStarCount != 0) {
        UI.showToast(R.string.TgxBusinessQuickReplyPaid, Toast.LENGTH_SHORT);
        finish(null, false, 0);
      } else if (!sameTemplate()) {
        changed(0);
      } else if (batches == null) {
        tdlib.send(new TdApi.SendQuickReplyShortcutMessages(chatId, shortcutId, 0),
          (sent, sendError) -> runOnUiThreadOptional(() -> finish(sendError, sent != null && sent.messages.length > 0, 0)));
      } else {
        sendBatch(batches, 0, 0);
      }
    }));
  }

  private void changed (int queued) {
    sending = false;
    if (current()) {
      UI.showToast(queued > 0 ? R.string.TgxBusinessQuickReplyPartial : R.string.TgxBusinessChanged, Toast.LENGTH_LONG);
      if (queued > 0) {
        navigateBack();
      } else {
        load();
      }
    }
  }

  private void sendBatch (ArrayList<TdApi.InputMessageContent[]> batches, int index, int queued) {
    if (!current() || !sameTemplate()) {
      changed(queued);
      return;
    }
    TdApi.InputMessageContent[] batch = batches.get(index);
    if (batch.length == 1) {
      tdlib.send(new TdApi.SendMessage(chatId, null, null, Td.newSendOptions(), null, batch[0]),
        (message, error) -> runOnUiThreadOptional(() -> advance(batches, index, queued, message != null ? 1 : 0, error)));
    } else {
      tdlib.send(new TdApi.SendMessageAlbum(chatId, null, null, Td.newSendOptions(), batch),
        (sent, error) -> runOnUiThreadOptional(() -> advance(batches, index, queued, sent != null ? sent.messages.length : 0, error)));
    }
  }

  private void advance (ArrayList<TdApi.InputMessageContent[]> batches, int index, int queued, int accepted, TdApi.Error error) {
    if (error != null || accepted != batches.get(index).length) {
      finish(error, false, queued + accepted);
    } else if (index + 1 == batches.size()) {
      finish(null, true, 0);
    } else {
      sendBatch(batches, index + 1, queued + accepted);
    }
  }

  private void finish (TdApi.Error error, boolean success, int queued) {
    sending = false;
    if (current()) {
      if (error != null) {
        UI.showError(error);
      }
      if (success || queued > 0) {
        if (queued > 0) {
          UI.showToast(R.string.TgxBusinessQuickReplyPartial, Toast.LENGTH_LONG);
        }
        navigateBack();
      } else {
        render();
      }
    }
  }

  private static final class Thumbnail extends SparseDrawableView {
    private final ComplexReceiver receiver = new ComplexReceiver(this);
    private MediaPreview media;

    Thumbnail (Context context) {
      super(context);
    }

    void bind (MediaPreview media) {
      this.media = media;
      if (media != null) {
        media.requestFiles(receiver, false);
      } else {
        receiver.clear();
      }
      invalidate();
    }

    @Override protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override protected void onDetachedFromWindow () {
      receiver.detach();
      super.onDetachedFromWindow();
    }

    @Override protected void onMeasure (int widthSpec, int heightSpec) {
      setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), Screen.dp(78));
    }

    @Override protected void onDraw (Canvas canvas) {
      if (media != null) {
        float x = Lang.rtl() ? getWidth() - Screen.dp(56) - media.getWidth() : Screen.dp(56);
        media.draw(this, canvas, receiver, x, Screen.dp(3), 1f);
      }
    }
  }
}
