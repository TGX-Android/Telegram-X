package org.thunderdog.challegram.component.dialogs;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumChatPreview;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.ForumUnreadCounter;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One visible chat row. Owns rendering/subscriptions, never owns forum records or issues requests from draw. */
final class ForumChatTopics {
  private final ChatView view;
  private final Tdlib tdlib;
  private final ComplexReceiver receiver;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path tail = new Path();
  private TGChat chat;
  private boolean attached;
  private int generation;
  private ForumTopicStore.ListSession session;
  private ForumUnreadCounter.Subscription unreadSubscription;
  private ForumTopicStore.TopicSubscription leadingSubscription;
  private int leadingId;
  private TdApi.Error leadingError;
  private List<ForumChatPreview.Item> items = Collections.emptyList();
  private final ArrayList<Text> labels = new ArrayList<>();
  private final ArrayList<Text> icons = new ArrayList<>();
  private final ArrayList<Integer> offsets = new ArrayList<>();
  private Text placeholder;
  private int width, mode, counterWidth;
  private boolean layoutDirty = true;
  private String language;
  private boolean refreshing;

  ForumChatTopics (ChatView view, Tdlib tdlib, RefreshRateLimiter refreshRateLimiter) {
    this.view = view;
    this.tdlib = tdlib;
    receiver = new ComplexReceiver(view).setUpdateListener(refreshRateLimiter);
    receiver.detach();
  }

  void setChat (TGChat chat) {
    if (this.chat != chat) {
      close();
      this.chat = chat;
      items = Collections.emptyList();
      layoutDirty = true;
      layout(width, mode);
    }
    update();
  }

  void attach () {
    attached = true;
    receiver.attach();
    update();
  }

  void detach () {
    attached = false;
    close();
    receiver.detach();
  }

  void setAnimationDisabled (boolean disabled) { receiver.setAnimationDisabled(disabled); }

  private void close () {
    generation++;
    if (session != null) { session.close(); session = null; }
    if (unreadSubscription != null) { unreadSubscription.close(); unreadSubscription = null; }
    if (leadingSubscription != null) { leadingSubscription.close(); leadingSubscription = null; }
    leadingId = 0;
    leadingError = null;
  }

  void update () {
    if (chat == null || !chat.canShowForumPreview()) {
      close();
      items = Collections.emptyList();
      layoutDirty = true;
      layout(width, mode);
      return;
    }
    if (attached && session == null) {
      final int ticket = generation;
      session = tdlib.topics().openPreview(chat.getChatId(), snapshot -> {
        if (ticket == generation && attached && session != null) refresh();
      });
      unreadSubscription = tdlib.topics().observeUnread(chat.getChatId(), count -> {
        if (ticket == generation && attached) chat.updateForumUnread();
      });
    }
    refresh();
  }

  private void refresh () {
    if (refreshing || chat == null || !chat.canShowForumPreview()) return;
    refreshing = true;
    try {
      ForumTopicStore.Snapshot snapshot = session != null ? session.getSnapshot() : tdlib.topics().cachedList(chat.getChatId(), "");
      boolean unavailable = ForumChatPreview.inaccessible(snapshot.error);
      chat.updateForumDraft(unavailable ? null : ForumChatPreview.latestDraft(chat.getChatId(), snapshot.topics));
      int id = unavailable ? 0 : chat.getPreviewTopicId();
      if (attached && id != leadingId) {
        if (leadingSubscription != null) { leadingSubscription.close(); leadingSubscription = null; }
        leadingId = id;
        leadingError = null;
        if (id != 0) {
          final int ticket = generation;
          leadingSubscription = tdlib.topics().observeTopic(new TdlibForumTopicManager.Key(chat.getChatId(), id), (topic, error) -> {
            if (ticket == generation && attached && leadingId == id) { leadingError = error; refresh(); }
          });
        }
      }
      TdlibForumTopicManager.Entry leading = id != 0 ? tdlib.topics().find(new TdlibForumTopicManager.Key(chat.getChatId(), id)) : null;
      List<ForumChatPreview.Item> next = unavailable ? Collections.emptyList() :
        ForumChatPreview.select(chat.getChatId(), id, snapshot.topics,
          leading != null ? leading.value : null, ForumChatPreview.inaccessible(leadingError));
      if (!items.equals(next)) {
        items = next;
        layoutDirty = true;
        layout(width, mode);
        view.invalidate();
      }
    } finally {
      refreshing = false;
    }
  }

  int lineHeight () { return Screen.dp(mode == Settings.CHAT_MODE_3LINE_BIG ? 20 : 19); }

  void layout (int width, int mode) {
    String language = Lang.packId();
    int counterWidth = chat != null ? chat.getPreviewCounterWidth() : 0;
    if (!layoutDirty && this.width == width && this.mode == mode && this.counterWidth == counterWidth && language.equals(this.language)) return;
    layoutDirty = false;
    this.language = language;
    this.counterWidth = counterWidth;
    this.width = width;
    this.mode = mode;
    for (Text icon : icons) if (icon != null) icon.performDestroy();
    labels.clear(); icons.clear(); offsets.clear();
    receiver.clear();
    placeholder = null;
    if (chat == null || !chat.canShowForumPreview()) return;
    int available = Math.max(0, width - ChatView.getLeftPadding(mode) - ChatView.getRightPadding() - counterWidth);
    float size = mode == Settings.CHAT_MODE_3LINE_BIG ? 16 : 15;
    if (available <= 0) return;
    if (items.isEmpty()) {
      placeholder = new Text.Builder(Lang.getString(R.string.ForumChatTopics), available, Paints.robotoStyleProvider(size), TextColorSets.Regular.LIGHT)
        .singleLine().allBold(false).build();
      return;
    }
    int x = 0;
    for (ForumChatPreview.Item item : items) {
      int iconWidth = Screen.dp(size + 1);
      int nameWidth = available - x - iconWidth - Screen.dp(4);
      if (nameWidth < Screen.dp(16)) break;
      offsets.add(x);
      String name = item.name.isEmpty() ? Lang.getString(R.string.ForumTopicTitle) : item.name;
      Text label = new Text.Builder(name, nameWidth, Paints.robotoStyleProvider(size), TextColorSets.Regular.LIGHT)
        .singleLine().ignoreNewLines().noClickable().build();
      labels.add(label);
      Text emoji = null;
      if (item.customEmojiId != 0) {
        final int slot = icons.size();
        TdApi.FormattedText value = new TdApi.FormattedText("*", new TdApi.TextEntity[] {
          new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(item.customEmojiId))
        });
        emoji = new Text.Builder(tdlib, value, null, iconWidth * 2, Paints.robotoStyleProvider(size), TextColorSets.WHITE,
          (text, media) -> {
            // Text.equals compares only the source string ("*" for every icon).
            // Use identity, including after a row is rebound, to keep emoji slots isolated.
            if (slot < icons.size() && icons.get(slot) == text) {
              text.requestMedia(receiver, slot * 16L, 16);
              view.invalidate();
            }
          }).singleLine().noClickable().build();
      }
      icons.add(emoji);
      if (emoji != null) emoji.requestMedia(receiver, (icons.size() - 1) * 16L, 16);
      x += iconWidth + Screen.dp(4) + label.getWidth() + Screen.dp(10);
    }
  }

  void draw (Canvas c) {
    if (chat == null || !chat.canShowForumPreview()) return;
    int start = ChatView.getLeftPadding(mode), top = ChatView.getTextTop(mode);
    boolean rtl = Lang.rtl();
    if (placeholder != null) {
      int x = rtl ? width - start - placeholder.getWidth() : start;
      placeholder.draw(c, x, top);
      return;
    }
    float iconSize = Screen.dp(mode == Settings.CHAT_MODE_3LINE_BIG ? 17 : 16);
    for (int i = 0; i < labels.size(); i++) {
      ForumChatPreview.Item item = items.get(i);
      Text label = labels.get(i), emoji = icons.get(i);
      int logicalX = start + offsets.get(i);
      float iconX = rtl ? width - logicalX - iconSize : logicalX;
      float iconY = top + (lineHeight() - iconSize) / 2f;
      if (emoji != null) {
        emoji.draw(c, Math.round(iconX + (iconSize - emoji.getWidth()) / 2f), Math.round(iconY + (iconSize - emoji.getHeight()) / 2f), null, 1f, receiver);
      } else {
        paint.setColor(0xff000000 | item.color);
        DrawAlgorithms.drawRoundRect(c, Screen.dp(5), iconX, iconY, iconX + iconSize, iconY + iconSize - Screen.dp(2), paint);
        tail.reset(); tail.moveTo(iconX + Screen.dp(2), iconY + iconSize - Screen.dp(5));
        tail.lineTo(iconX + Screen.dp(2), iconY + iconSize); tail.lineTo(iconX + Screen.dp(7), iconY + iconSize - Screen.dp(3)); tail.close();
        c.drawPath(tail, paint);
        String letter = item.general ? "#" : item.name.isEmpty() ? "·" : item.name.substring(0, item.name.offsetByCodePoints(0, 1));
        paint.setColor(0xffffffff); paint.setTypeface(Fonts.getRobotoMedium()); paint.setTextSize(Screen.dp(10)); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(letter, iconX + iconSize / 2, iconY + (iconSize - Screen.dp(2) - paint.ascent() - paint.descent()) / 2, paint);
      }
      int textX = logicalX + Math.round(iconSize) + Screen.dp(4);
      if (rtl) textX = width - textX - label.getWidth();
      label.draw(c, textX, top);
    }
  }
}
