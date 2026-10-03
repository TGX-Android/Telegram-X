package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTopicNotifications;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.function.Function;
import tgx.td.ChatId;

/** A topic target is required; no path from this screen sends SetChatNotificationSettings. */
public final class ForumTopicNotificationController extends ViewController<ForumTopicProfileController.Arguments> implements CleanupStartupDelegate, TdlibCache.SupergroupDataChangeListener {
  private ForumTopicStore.TopicSubscription subscription;
  private TdApi.ForumTopic topic;
  private TdApi.Error failure;
  private boolean busy;
  private LinearLayout column;
  private TextView status, hint;
  private SettingRow mute, sound, preview, pinned, mentions;
  private ScrollView scroll;
  private boolean terminal;
  private int soundRequest;

  public ForumTopicNotificationController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_forumTopicNotifications; }
  @Override public long getChatId () { return getArgumentsStrict().chatId; }
  @Override public CharSequence getName () { return Lang.getString(R.string.ForumNotifications); }
  @Override protected int getBackButton () { return BackHeaderButton.TYPE_BACK; }
  @Override public boolean supportsBottomInset () { return true; }
  @Override protected void onBottomInsetChanged (int inset, int withoutIme, boolean isIme) {
    super.onBottomInsetChanged(inset, withoutIme, isIme); Views.applyBottomInset(scroll, inset);
  }
  private TdlibForumTopicManager.Key key () { return new TdlibForumTopicManager.Key(getChatId(), getArgumentsStrict().forumTopicId); }

  @Override protected View onCreateView (Context context) {
    scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    Views.applyBottomInset(scroll, extraBottomInset);
    scroll.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(scroll, ColorId.background);
    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    int pad = Screen.dp(16); column.setPadding(0, 0, 0, pad);
    scroll.addView(column);
    status = new TextView(context); status.setTextSize(16); status.setTextColor(Theme.textAccentColor());
    status.setTypeface(Fonts.getRobotoMedium()); status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    status.setMinHeight(Screen.dp(56)); status.setFocusable(true);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    status.setBackground(Theme.transparentSelector());
    addThemeTextColorListener(status, ColorId.text);
    status.setPadding(pad, pad, pad, pad); column.addView(status, new LinearLayout.LayoutParams(-1, -2));
    status.setOnClickListener(v -> { if (!terminal && !busy) { failure = null; tdlib.topics().retryTopic(key()); render(); } });
    mute = row(R.string.ForumNotifications, this::chooseMute, true);
    sound = row(R.string.ForumProfileSound, this::chooseSound, true);
    preview = row(R.string.ForumProfilePreview, () -> chooseField(ForumTopicNotifications.Field.PREVIEW), true);
    pinned = row(R.string.ForumProfilePinnedNotifications, () -> chooseField(ForumTopicNotifications.Field.PINNED), true);
    mentions = row(R.string.ForumProfileMentionNotifications, () -> chooseField(ForumTopicNotifications.Field.MENTIONS), false);
    hint = new TextView(context); hint.setText(Lang.getString(R.string.ForumProfileSettingsHint));
    hint.setTypeface(Fonts.getRobotoRegular()); hint.setGravity(Gravity.START);
    hint.setTextSize(14); hint.setPadding(pad, pad, pad, 0); hint.setTextColor(Theme.textDecentColor());
    addThemeTextColorListener(hint, ColorId.textLight); column.addView(hint, new LinearLayout.LayoutParams(-1, -2));
    subscription = tdlib.topics().observeTopic(key(), (value, error) -> {
      if (isDestroyed() || terminal) return;
      topic = value; failure = error; render();
    });
    tdlib.listeners().addCleanupListener(this);
    tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    render();
    return scroll;
  }

  private SettingRow row (int label, Runnable action, boolean divider) {
    SettingRow view = new SettingRow(context, label, divider);
    column.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    view.setOnClickListener(v -> { if (ready()) action.run(); });
    return view;
  }

  /** TGX list styling with wrapping SP text, so long labels and 2x font scale remain readable. */
  private final class SettingRow extends LinearLayout {
    private final int labelId;
    private final boolean divider;
    private final TextView label, value;

    SettingRow (Context context, int labelId, boolean divider) {
      super(context);
      this.labelId = labelId; this.divider = divider;
      setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL); setMinimumHeight(Screen.dp(64));
      setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
      setFocusable(true); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      setWillNotDraw(false); RippleSupport.setSimpleWhiteBackground(this, ForumTopicNotificationController.this);
      label = text(16, ColorId.text); value = text(14, ColorId.textLight);
      value.setPadding(0, Screen.dp(4), 0, 0);
      addView(label, new LinearLayout.LayoutParams(-1, -2)); addView(value, new LinearLayout.LayoutParams(-1, -2));
      addThemeInvalidateListener(this); setValue("");
    }
    private TextView text (int size, int colorId) {
      TextView text = new TextView(getContext()); text.setTextSize(size); text.setTypeface(Fonts.getRobotoRegular());
      text.setGravity(Gravity.START); text.setSingleLine(false); text.setTextColor(Theme.getColor(colorId));
      text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      addThemeTextColorListener(text, colorId); return text;
    }
    void setValue (CharSequence current) {
      CharSequence name = Lang.getString(labelId);
      label.setText(name); value.setText(current); value.setVisibility(current.length() == 0 ? View.GONE : View.VISIBLE);
      setContentDescription(Lang.getString(R.string.ForumProfileSettingValue, name, current));
    }
    @Override public void setEnabled (boolean enabled) {
      super.setEnabled(enabled);
      if (label != null) label.setAlpha(enabled ? 1f : .55f);
      if (value != null) value.setAlpha(enabled ? 1f : .55f);
    }
    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (divider) canvas.drawRect(getPaddingLeft(), getHeight() - Math.max(1, Screen.dp(.5f)),
        getWidth() - getPaddingRight(), getHeight(), Paints.fillingPaint(Theme.separatorColor()));
    }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
    }
  }

  private boolean ready () { return !isDestroyed() && !terminal && !busy && failure == null && topic != null && topic.notificationSettings != null && tdlib.chatAvailable(tdlib.chat(getChatId())); }
  private String state (boolean inherit, boolean enabled) {
    return Lang.getString(inherit ? R.string.ForumProfileDefault : enabled ? R.string.ForumProfileEnabled : R.string.ForumProfileDisabled);
  }

  private void render () {
    if (status == null) return;
    status.setText(terminal || topic != null && !tdlib.chatAvailable(tdlib.chat(getChatId())) ? Lang.getString(R.string.ForumProfileUnavailable) : busy ? Lang.getString(R.string.ForumSaving) : failure != null ? Lang.getString(R.string.ForumProfileRetry) : topic == null ? Lang.getString(R.string.ForumProfileLoading) : topic.info.name);
    for (SettingRow row : new SettingRow[] {mute, sound, preview, pinned, mentions}) row.setEnabled(ready());
    TdApi.ChatNotificationSettings s = topic != null ? topic.notificationSettings : null;
    if (s == null) return;
    String muteState = !s.useDefaultMuteFor && s.muteFor > 0 ? (s.muteFor > 366 * 86400 ? Lang.getString(R.string.ForumMuteForever) : Lang.getString(R.string.ForumProfileDuration, Lang.getDuration(s.muteFor))) : state(s.useDefaultMuteFor, s.muteFor == 0);
    mute.setValue(muteState);
    sound.setValue(Lang.getString(s.useDefaultSound ? R.string.ForumProfileDefault : s.soundId == 0 ? R.string.ForumProfileSilent : R.string.ForumProfileCustomSound));
    preview.setValue(state(s.useDefaultShowPreview, s.showPreview));
    pinned.setValue(state(s.useDefaultDisablePinnedMessageNotifications, !s.disablePinnedMessageNotifications));
    mentions.setValue(state(s.useDefaultDisableMentionNotifications, !s.disableMentionNotifications));
  }

  private void apply (Function<TdApi.ChatNotificationSettings, TdApi.ChatNotificationSettings> edit) {
    if (!ready()) return;
    // Read immediately before send; never reuse settings captured when a dialog was opened.
    TdlibForumTopicManager.Entry current = tdlib.topics().find(key());
    if (current == null || current.value == null || current.value.notificationSettings == null) return;
    busy = true; render();
    tdlib.topics().actions.setNotifications(key(), edit.apply(current.value.notificationSettings), (ok, error) -> {
      if (isDestroyed() || terminal) return;
      busy = false; render();
      if (error != null) showError(error);
    });
  }

  private void chooseMute () {
    String[] labels = {Lang.getString(R.string.ForumProfileDefault), Lang.getString(R.string.ForumNotificationsOn),
      Lang.getString(R.string.ForumMuteHour), Lang.getString(R.string.ForumMuteDay), Lang.getString(R.string.ForumMuteForever)};
    int[] seconds = {0, 0, 3600, 86400, Integer.MAX_VALUE};
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(getName()).setItems(labels,
      (dialog, which) -> apply(s -> ForumTopicNotifications.mute(s, which == 0, seconds[which])))
      .setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  private void chooseField (ForumTopicNotifications.Field field) {
    String[] labels = {Lang.getString(R.string.ForumProfileDefault), Lang.getString(R.string.ForumProfileEnabled), Lang.getString(R.string.ForumProfileDisabled)};
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(getName()).setItems(labels,
      (dialog, which) -> apply(s -> ForumTopicNotifications.field(s, field, which == 0, which == 1)))
      .setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  private void chooseSound () {
    busy = true; render(); final int ticket = ++soundRequest;
    tdlib.send(new TdApi.GetSavedNotificationSounds(), (sounds, error) -> tdlib.ui().post(() -> {
      if (isDestroyed() || terminal || ticket != soundRequest) return;
      ++soundRequest;
      busy = false; render();
      if (error != null) { showError(error); return; }
      ArrayList<String> labels = new ArrayList<>();
      labels.add(Lang.getString(R.string.ForumProfileDefault)); labels.add(Lang.getString(R.string.ForumProfileSilent));
      for (TdApi.NotificationSound value : sounds.notificationSounds) labels.add(value.title);
      showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(Lang.getString(R.string.ForumProfileSound))
        .setItems(labels.toArray(new String[0]), (dialog, which) -> apply(s -> ForumTopicNotifications.sound(s, which == 0, which < 2 ? 0 : sounds.notificationSounds[which - 2].id)))
        .setNegativeButton(Lang.getString(R.string.Cancel), null));
    }));
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed() && !terminal && ticket == soundRequest && busy) {
        ++soundRequest; busy = false; render(); showError(new TdApi.Error(408, Lang.getString(R.string.ForumProfileRetry)));
      }
    }, 15000);
  }

  private void showError (TdApi.Error error) {
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(getName())
      .setMessage(Lang.getString(tdlib.isConnected() ? R.string.LaunchSubtitleFatalError : R.string.prompt_network))
      .setPositiveButton(Lang.getString(R.string.OK), null));
  }

  @Override protected void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (column != null) {
      column.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
      hint.setText(Lang.getString(R.string.ForumProfileSettingsHint)); render();
    }
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    ForumTopicProfileController.saveArguments(out, prefix, getArgumentsStrict());
    return true;
  }
  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    ForumTopicProfileController.Arguments args = ForumTopicProfileController.restoreArguments(in, prefix);
    if (args == null || tdlib.chatSync(args.chatId) == null) return false;
    setArguments(args); super.restoreInstanceState(in, prefix); return true;
  }
  @Override public void destroy () {
    ++soundRequest;
    if (subscription != null) subscription.close();
    tdlib.listeners().removeCleanupListener(this);
    tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    super.destroy();
  }
  @Override public void onSupergroupUpdated (TdApi.Supergroup supergroup) { runOnUiThreadOptional(this::render); }
  @Override public void onPerformUserCleanup () {
    terminal = true; ++soundRequest;
    tdlib.ui().post(() -> { if (isDestroyed()) return; if (subscription != null) subscription.close(); topic = null; busy = false; render(); });
  }
  @Override public void onPerformRestart () { onPerformUserCleanup(); }
  @Override public void onPerformStartup (boolean afterRestart) { }
}
