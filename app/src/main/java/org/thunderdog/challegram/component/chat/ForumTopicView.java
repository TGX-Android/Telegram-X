package org.thunderdog.challegram.component.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;

import androidx.core.view.ViewCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.ForumPresentation;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import tgx.td.Td;

/** Two text lines. Topic counters and outgoing delivery are intentionally independent. */
public final class ForumTopicView extends View {
  private final Tdlib tdlib;
  private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final ComplexReceiver receiver = new ComplexReceiver(this);
  private final Drawable pin, closed, hidden, mute, pending, failed, sent, read;
  private final ArrayList<Drawable> stateIcons = new ArrayList<>();
  private final ArrayList<String> counters = new ArrayList<>();
  private TdApi.ForumTopic topic;
  private Text customEmoji;
  private long emojiId;
  private float emojiSize;
  private String preview = "", time = "";
  private boolean hasDraft, muted, selectionMode;
  private float selectionFactor;
  private FactorAnimator selectionAnimator;
  private Drawable delivery;
  private int deliveryColor = ColorId.iconLight;
  private int counterColumns = 1;
  private float counterWidth, firstHeight, secondHeight;
  private boolean wrapStates;

  public ForumTopicView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    pin = drawable(R.drawable.deproko_baseline_pin_16);
    closed = drawable(R.drawable.baseline_lock_16);
    hidden = drawable(R.drawable.baseline_eye_off_24);
    mute = drawable(R.drawable.baseline_notifications_off_16);
    pending = drawable(R.drawable.baseline_schedule_24);
    failed = drawable(R.drawable.baseline_error_18);
    sent = drawable(R.drawable.deproko_baseline_check_single_24);
    read = drawable(R.drawable.deproko_baseline_check_double_24);
    setBackground(Theme.fillingSelector());
    setMinimumHeight(Screen.dp(64));
    setFocusable(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  private Drawable drawable (int id) { return Drawables.get(getResources(), id); }
  public TdApi.ForumTopic getTopic () { return topic; }
  private float scaled (float dp) { return Screen.dp(dp * Math.max(1f, getResources().getConfiguration().fontScale)); }

  public void setSelection (boolean mode, boolean selected, boolean animate) {
    if (selectionMode == mode && isSelected() == selected) return;
    selectionMode = mode;
    setSelected(selected);
    float target = mode ? 1f : 0f;
    if (animate && !Settings.instance().needReduceMotion() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) && ViewCompat.isAttachedToWindow(this) && selectionFactor != target) {
      if (selectionAnimator == null) {
        selectionAnimator = new FactorAnimator(0, (id, factor, fraction, animator) -> {
          selectionFactor = factor;
          invalidate();
        }, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 180L, selectionFactor);
      }
      selectionAnimator.animateTo(target);
    } else {
      if (selectionAnimator != null) selectionAnimator.forceFactor(target);
      selectionFactor = target;
    }
    requestLayout(); invalidate();
  }

  @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setCheckable(selectionMode);
    info.setChecked(isSelected());
    info.setSelected(isSelected());
    if (selectionMode) info.setClassName(CheckBox.class.getName());
  }

  @Override protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    setPaint(11, Theme.badgeTextColor(), true);
    counterWidth = 0;
    for (String label : counters) counterWidth = Math.max(counterWidth, paint.measureText(label) + Screen.dp(10));
    float available = Math.max(Screen.dp(48), (width - Screen.dp(selectionMode ? 72 : 32)) * .52f);
    counterColumns = counters.isEmpty() ? 1 : Math.max(1, Math.min(counters.size(), (int) (available / (counterWidth + Screen.dp(3)))));
    firstHeight = Math.max(scaled(24), Screen.dp(24));
    setPaint(12, Theme.textDecentColor(), false);
    float timeAndDelivery = paint.measureText(time) + Screen.dp(time.isEmpty() ? 0 : 4) + Screen.dp(delivery != null ? 18 : 0);
    wrapStates = !stateIcons.isEmpty() && timeAndDelivery + Screen.dp(18 * stateIcons.size()) > available;
    if (wrapStates) firstHeight = Math.max(firstHeight, scaled(14) + Screen.dp(20));
    secondHeight = Math.max(scaled(22), (float) Math.ceil((double) counters.size() / counterColumns) * (scaled(16) + Screen.dp(3)));
    setMeasuredDimension(width, Math.max(Screen.dp(64), (int) (Screen.dp(18) + firstHeight + secondHeight)));
  }

  public void setTopic (TdApi.ForumTopic topic) {
    this.topic = topic;
    receiver.setAnimationDisabled(Settings.instance().needReduceMotion());
    TdlibSettingsManager.LocalForumDraft local = tdlib.settings().getLocalForumDraft(topic.info.chatId, topic.info.forumTopicId);
    TdApi.DraftMessage draft = local != null ? local.draft : topic.draftMessage;
    hasDraft = draft != null && draft.content instanceof TdApi.DraftMessageContentText;
    TdApi.Message message = topic.lastMessage;
    if (hasDraft) {
      preview = Lang.getString(R.string.format_forumPreview, Lang.getString(R.string.Draft), ((TdApi.DraftMessageContentText) draft.content).text.text);
    } else if (message != null) {
      ContentPreview content = ContentPreview.getChatListPreview(tdlib, topic.info.chatId, message, false);
      preview = content.buildText(false);
      if (!content.hideAuthor) {
        String author = message.isOutgoing ? Lang.getString(Td.getSenderId(message) == message.chatId ? R.string.FromYouAnonymous : R.string.FromYou) :
          Td.getMessageAuthorId(message) == message.chatId && TextUtils.isEmpty(message.authorSignature) ? Lang.getString(R.string.FromAnonymous) : tdlib.senderName(message, false, true);
        if (!TextUtils.isEmpty(author)) preview = Lang.getString(R.string.format_forumPreview, author, preview);
      }
    } else preview = Lang.getString(R.string.NoMessages);
    time = message != null && message.date != 0 ? Lang.timeOrDateShort(message.date, TimeUnit.SECONDS) : "";
    delivery = null; deliveryColor = ColorId.iconLight;
    int deliveryLabel = 0;
    if (message != null && message.isOutgoing) {
      if (message.sendingState instanceof TdApi.MessageSendingStateFailed) { delivery = failed; deliveryLabel = R.string.ForumDeliveryFailed; deliveryColor = ColorId.iconNegative; }
      else if (message.sendingState != null) { delivery = pending; deliveryLabel = R.string.ForumDeliveryPending; }
      else if (message.id <= topic.lastReadOutboxMessageId) { delivery = read; deliveryLabel = R.string.ForumDeliveryRead; }
      else { delivery = sent; deliveryLabel = R.string.ForumDeliverySent; }
    }
    ArrayList<String> status = new ArrayList<>();
    stateIcons.clear(); counters.clear();
    if (topic.info.isGeneral) status.add(Lang.getString(R.string.ForumGeneral));
    if (topic.isPinned) { status.add(Lang.getString(R.string.ForumPinned)); stateIcons.add(pin); }
    if (topic.info.isClosed) { status.add(Lang.getString(R.string.ForumTopicClosed)); stateIcons.add(closed); }
    if (topic.info.isHidden) { status.add(Lang.getString(R.string.ForumHidden)); stateIcons.add(hidden); }
    muted = ForumPresentation.isMuted(topic.notificationSettings, tdlib.chatMuteFor(topic.info.chatId) > 0);
    if (muted) { status.add(Lang.getString(R.string.ForumNotificationsMuted)); stateIcons.add(mute); }
    if (deliveryLabel != 0) status.add(Lang.getString(deliveryLabel));
    if (topic.unreadCount > 0) counters.add(Lang.compactNumber(topic.unreadCount));
    if (topic.unreadMentionCount > 0) counters.add("@" + Lang.compactNumber(topic.unreadMentionCount));
    if (topic.unreadReactionCount > 0) counters.add("♥" + Lang.compactNumber(topic.unreadReactionCount));
    if (topic.unreadPollVoteCount > 0) counters.add("✓" + Lang.compactNumber(topic.unreadPollVoteCount));
    long nextEmoji = !topic.info.isGeneral && topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
    float size = scaled(20);
    if (emojiId != nextEmoji || emojiSize != size) {
      clearEmoji(); emojiId = nextEmoji; emojiSize = size;
      if (emojiId != 0) {
        TdApi.FormattedText emoji = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiId))});
        customEmoji = new Text.Builder(tdlib, emoji, null, (int) size * 2, Paints.robotoStyleProvider(20 * Math.max(1f, getResources().getConfiguration().fontScale)), TextColorSets.WHITE, (text, media) -> {
          if (text == customEmoji) { text.requestMedia(receiver); invalidate(); }
        }).singleLine().build();
        customEmoji.requestMedia(receiver);
      }
    }
    setContentDescription(Lang.getString(R.string.ForumTopicAccessibility, topic.info.name, time, TextUtils.join(". ", status), preview,
      Lang.getString(R.string.ForumUnreadCounts, topic.unreadCount, topic.unreadMentionCount, topic.unreadReactionCount, topic.unreadPollVoteCount)));
    requestLayout(); invalidate();
  }

  private void setPaint (float size, int color, boolean medium) {
    paint.setTextSize(scaled(size)); paint.setTypeface(medium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    paint.setColor(color); paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.LEFT);
  }
  private float x (float logical, boolean rtl) { return rtl ? getWidth() - logical : logical; }
  private void text (Canvas c, String value, float start, float end, float cy, float size, int color, boolean medium, boolean rtl) {
    setPaint(size, color, medium);
    paint.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
    CharSequence line = TextUtils.ellipsize(value.replace('\n', ' '), paint, Math.max(0, end - start), TextUtils.TruncateAt.END);
    c.drawText(line.toString(), x(start, rtl), cy - (paint.ascent() + paint.descent()) / 2, paint);
  }
  private void icon (Canvas c, Drawable icon, float start, float cy, float size, int colorId, boolean rtl) {
    int save = c.save();
    c.translate(rtl ? getWidth() - start - size : start, cy - size / 2);
    c.scale(size / icon.getMinimumWidth(), size / icon.getMinimumHeight());
    Drawables.draw(c, icon, 0, 0, PorterDuffPaint.get(colorId)); c.restoreToCount(save);
  }
  private void topicIcon (Canvas c, float start, float cy, boolean rtl) {
    float size = scaled(20), cx = x(start + size / 2, rtl);
    if (customEmoji != null) {
      int save = c.save(); c.translate(cx - size / 2, cy - size / 2);
      c.scale(size / Math.max(1, customEmoji.getWidth()), size / Math.max(1, customEmoji.getHeight()));
      customEmoji.draw(c, 0, 0, null, 1f, receiver); c.restoreToCount(save);
    } else {
      float r = size / 2;
      paint.setColor(0xff000000 | (topic.info.icon != null ? topic.info.icon.color : 0x6fb9f0));
      DrawAlgorithms.drawRoundRect(c, r/2, cx-r, cy-r, cx+r, cy+r-1, paint);
      path.reset(); path.moveTo(cx-r+size*.15f, cy+r-size*.3f); path.lineTo(cx-r+size*.15f, cy+r); path.lineTo(cx, cy+r-size*.15f); path.close(); c.drawPath(path, paint);
      String name = topic.info.name;
      String letter = topic.info.isGeneral || name.isEmpty() ? "#" : name.substring(0, name.offsetByCodePoints(0, 1));
      setPaint(13, 0xffffffff, true); paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(letter, cx, cy-(paint.ascent()+paint.descent())/2, paint);
    }
  }

  @Override protected void onDraw (Canvas c) {
    super.onDraw(c);
    if (topic == null) return;
    boolean rtl = Lang.rtl();
    float start = Screen.dp(14) + Screen.dp(36) * selectionFactor, end = getWidth()-Screen.dp(14);
    float cy1 = Screen.dp(9) + firstHeight/2, cy2 = Screen.dp(9) + firstHeight + secondHeight/2;
    if (selectionFactor > 0) {
      float cx = x(Screen.dp(24), rtl), cy = getHeight()/2f, r = Screen.dp(10);
      // Positive/online tokens can follow the accent; selection explicitly requires green.
      paint.setColor(Theme.getColor(isSelected() ? ColorId.avatarGreen : ColorId.iconLight));
      paint.setAlpha((int) (255 * selectionFactor));
      paint.setStyle(isSelected() ? Paint.Style.FILL : Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2));
      c.drawCircle(cx, cy, r, paint);
      if (isSelected()) {
        paint.setColor(Theme.getColor(ColorId.white)); paint.setStyle(Paint.Style.STROKE);
        path.reset(); path.moveTo(cx-Screen.dp(5), cy); path.lineTo(cx-Screen.dp(1), cy+Screen.dp(4)); path.lineTo(cx+Screen.dp(6), cy-Screen.dp(4)); c.drawPath(path, paint);
      }
      paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
    }
    setPaint(12, Theme.textDecentColor(), false);
    float timeWidth = paint.measureText(time), slot = Screen.dp(18);
    float trailing = end-timeWidth;
    float timeCy = wrapStates ? Screen.dp(9) + scaled(14)/2 : cy1;
    text(c, time, trailing, end, timeCy, 12, Theme.textDecentColor(), false, rtl);
    if (!time.isEmpty()) trailing -= Screen.dp(4);
    if (delivery != null) { trailing -= slot; icon(c, delivery, trailing, timeCy, Screen.dp(16), deliveryColor, rtl); }
    float statesStart = wrapStates ? end : trailing;
    float statesCy = wrapStates ? Screen.dp(9) + scaled(14) + Screen.dp(10) : cy1;
    for (Drawable state : stateIcons) { statesStart -= slot; icon(c, state, statesStart, statesCy, Screen.dp(16), ColorId.iconLight, rtl); }
    trailing = Math.min(trailing, statesStart);
    topicIcon(c, start, cy1, rtl);
    text(c, topic.info.name, start+scaled(20)+Screen.dp(6), trailing-Screen.dp(6), cy1, 17, Theme.textAccentColor(), true, rtl);
    float countersWidth = counters.isEmpty() ? 0 : counterColumns * (counterWidth+Screen.dp(3));
    text(c, preview, start, end-countersWidth-Screen.dp(counters.isEmpty() ? 0 : 6), cy2, 14,
      hasDraft ? Theme.textRedColor() : Theme.textDecentColor(), false, rtl);
    float chipHeight = scaled(16), gap = Screen.dp(3);
    int rows = (counters.size()+counterColumns-1)/counterColumns;
    float top = cy2-(rows*(chipHeight+gap)-gap)/2;
    for (int i = 0; i < counters.size(); i++) {
      float logical = end-counterWidth-(i%counterColumns)*(counterWidth+gap), left = rtl ? getWidth()-logical-counterWidth : logical;
      float y = top+(i/counterColumns)*(chipHeight+gap);
      paint.setColor(muted ? Theme.badgeMutedColor() : Theme.badgeColor());
      DrawAlgorithms.drawRoundRect(c, chipHeight/2, left, y, left+counterWidth, y+chipHeight, paint);
      setPaint(11, Theme.badgeTextColor(), true); paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(counters.get(i), left+counterWidth/2, y+chipHeight/2-(paint.ascent()+paint.descent())/2, paint);
    }
    paint.setColor(Theme.separatorColor()); c.drawRect(rtl ? 0 : start, getHeight()-1, rtl ? getWidth()-start : getWidth(), getHeight(), paint);
  }

  private void clearEmoji () {
    if (customEmoji != null) customEmoji.performDestroy();
    customEmoji = null; emojiId = 0; receiver.clear();
  }
  public void clear () {
    if (selectionAnimator != null) selectionAnimator.forceFactor(0f);
    clearEmoji(); topic = null; selectionFactor = 0; selectionMode = false; setSelected(false); setContentDescription(null);
  }
  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); receiver.attach(); }
  @Override protected void onDetachedFromWindow () {
    selectionFactor = selectionMode ? 1f : 0f;
    if (selectionAnimator != null) selectionAnimator.forceFactor(selectionFactor);
    receiver.detach(); super.onDetachedFromWindow();
  }
}
