package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.TextViewCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumPresentation;
import org.thunderdog.challegram.data.ForumTopicMedia;
import org.thunderdog.challegram.data.ForumTopicNotifications;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.data.ForumTopicProfileLink;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.ForumTopicActions;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import tgx.td.ChatId;
import tgx.td.Td;

/** Topic identity/settings plus existing shared-media pages, not a second group profile. */
public final class ForumTopicProfileController extends ViewController<ForumTopicProfileController.Arguments>
  implements Menu, ChatListener, MessageListener, NotificationSettingsListener, TdlibCache.SupergroupDataChangeListener, CleanupStartupDelegate {
  public static final class Arguments {
    public final long chatId;
    public final int forumTopicId;
    public final TdApi.ChatList chatList;
    public Arguments (long chatId, int forumTopicId, @Nullable TdApi.ChatList chatList) {
      if (chatId == 0 || forumTopicId <= 0) throw new IllegalArgumentException("A typed forum topic is required");
      this.chatId = chatId; this.forumTopicId = forumTopicId; this.chatList = chatList;
    }
  }

  private static final int[] TAB_NAMES = {R.string.ForumProfileMedia, R.string.ForumProfileFiles, R.string.ForumProfileLinks,
    R.string.ForumProfileMusic, R.string.ForumProfilePolls, R.string.ForumProfileGifs, R.string.ForumProfileVoice};
  private final ForumTopicMedia.Category[] categories = new ForumTopicMedia.Category[ForumTopicMedia.CATEGORY_COUNT];
  private final SharedBaseController<?>[] pages = new SharedBaseController<?>[ForumTopicMedia.CATEGORY_COUNT];
  private final Parcelable[] restoredPageStates = new Parcelable[ForumTopicMedia.CATEGORY_COUNT];
  private final ForumTopicProfileLink profileLink = new ForumTopicProfileLink();
  private List<Integer> visibleTabs = Collections.emptyList();
  private int activeTab = -1, restoreTab = -1, restoreScroll;
  private String query = "";
  private boolean restoreSearch, restoreScrollPending;
  private boolean busy, subscribed, materialsStarted, refreshScheduled, terminal;
  private volatile Object epoch = new Object();
  private ForumTopicStore.TopicSubscription subscription;
  private ForumTopicStore.ListSession pinsSession;
  private TdApi.ForumTopic topic;
  private TdApi.Error metadataError;
  private ForumTopicUi topicUi;
  private NestedScrollView scroll;
  private LinearLayout tabs, actions, linkRow;
  private HorizontalScrollView tabScroll;
  private FrameLayout materialFrame, iconFrame;
  private TextView title, group, state, materialState, regularIcon;
  private TextView linkText, linkLabel, linkHint;
  private ImageView linkCopy;
  private CustomTextView customIcon;
  private ActionView messageButton, muteButton, pinButton;
  private View editButton, moreButton;
  private long renderedEmoji = Long.MIN_VALUE;
  private static final int MAX_RECENT_CONTENT = 128, MAX_PENDING_CONTENT = 32, MAX_CONTENT_REQUESTS = 2;
  private final Map<Long, TdApi.Message> recentContentMessages = new LinkedHashMap<>();
  private final Map<Long, TdApi.MessageContent> pendingContent = new LinkedHashMap<>(), activeContent = new LinkedHashMap<>();
  private final Map<Long, Long> recentContentRequests = new LinkedHashMap<>();
  private boolean contentPumpScheduled;

  public ForumTopicProfileController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    for (int i = 0; i < categories.length; i++) categories[i] = new ForumTopicMedia.Category();
  }
  @Override public int getId () { return R.id.controller_forumTopicProfile; }
  @Override public long getChatId () { return getArgumentsStrict().chatId; }
  public int getForumTopicId () { return getArgumentsStrict().forumTopicId; }
  public TdApi.ChatList getChatList () { return getArgumentsStrict().chatList; }
  public TdApi.MessageTopicForum getTopicId () { return new TdApi.MessageTopicForum(getForumTopicId()); }
  private TdlibForumTopicManager.Key key () { return new TdlibForumTopicManager.Key(getChatId(), getForumTopicId()); }
  @Override public CharSequence getName () { return Lang.getString(R.string.ForumTopicTitle); }
  @Override protected int getBackButton () { return BackHeaderButton.TYPE_BACK; }
  @Override protected int getMenuId () { return R.id.menu_forumTopicProfile; }
  @Override protected int getSearchMenuId () { return R.id.menu_clear; }
  @Override public boolean supportsBottomInset () { return true; }
  @Override protected void onBottomInsetChanged (int inset, int withoutIme, boolean isIme) {
    super.onBottomInsetChanged(inset, withoutIme, isIme);
    Views.applyBottomInset(scroll, inset);
  }

  @Override protected View onCreateView (Context context) {
    topicUi = new ForumTopicUi(this, getChatId());
    scroll = new NestedScrollView(context);
    scroll.setFillViewport(true);
    // Keep initial focus on the profile, not on a newly attached shared-media RecyclerView.
    // Descendant actions remain focusable for keyboard and accessibility navigation.
    scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    scroll.setFocusableInTouchMode(true);
    scroll.requestFocus();
    scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> {
      if (y != oldY) restoreScrollPending = false;
    });
    Views.applyBottomInset(scroll, extraBottomInset);
    scroll.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(scroll, ColorId.background);
    LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout profile = new LinearLayout(context); profile.setOrientation(LinearLayout.VERTICAL); profile.setGravity(Gravity.CENTER_HORIZONTAL);
    int pad = Screen.dp(12); profile.setPadding(pad, pad, pad, pad); content.addView(profile);
    iconFrame = new FrameLayout(context);
    profile.addView(iconFrame, new LinearLayout.LayoutParams(Screen.dp(88), Screen.dp(88)));
    regularIcon = text(40, ColorId.text); regularIcon.setGravity(Gravity.CENTER);
    iconFrame.addView(regularIcon, new FrameLayout.LayoutParams(-1, -1));
    customIcon = new CustomTextView(context, tdlib); customIcon.setTextSize(64); customIcon.setTextColorId(ColorId.text);
    customIcon.setPadding(Screen.dp(7), Screen.dp(3), 0, 0); customIcon.setSingleLine(true);
    iconFrame.addView(customIcon, new FrameLayout.LayoutParams(-1, -1));
    iconFrame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    title = text(24, ColorId.text); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setGravity(Gravity.CENTER);
    title.setPadding(0, pad, 0, 0); profile.addView(title, new LinearLayout.LayoutParams(-1, -2));
    group = text(16, ColorId.textLight); group.setGravity(Gravity.CENTER); group.setMinHeight(Screen.dp(48));
    group.setFocusable(true); group.setOnClickListener(v -> tdlib.ui().openChatProfile(this, getChatId(), null, null));
    profile.addView(group, new LinearLayout.LayoutParams(-1, -2));
    state = text(14, ColorId.textLight); state.setGravity(Gravity.CENTER); state.setMinHeight(Screen.dp(48));
    state.setOnClickListener(v -> retryMetadata()); profile.addView(state, new LinearLayout.LayoutParams(-1, -2));
    actions = new LinearLayout(context); actions.setOrientation(LinearLayout.VERTICAL); profile.addView(actions, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout row = actionRow();
    messageButton = button(row, R.string.ForumProfileMessage, R.drawable.baseline_chat_bubble_24, this::navigateBack);
    muteButton = button(row, R.string.ForumProfileMute, R.drawable.baseline_notifications_off_24, this::toggleMute);
    pinButton = button(row, R.string.ForumPinTopic, R.drawable.deproko_baseline_pin_24, this::togglePin);
    createLinkRow(profile);
    tabScroll = new HorizontalScrollView(context); tabScroll.setHorizontalScrollBarEnabled(false);
    tabs = new LinearLayout(context); tabs.setGravity(Gravity.CENTER_VERTICAL); tabScroll.addView(tabs);
    content.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));
    materialState = text(16, ColorId.textLight); materialState.setGravity(Gravity.CENTER); materialState.setMinHeight(Screen.dp(64));
    materialState.setPadding(pad, pad, pad, pad); materialState.setOnClickListener(v -> refreshMaterials());
    content.addView(materialState, new LinearLayout.LayoutParams(-1, -2));
    materialFrame = new FrameLayout(context); content.addView(materialFrame, new LinearLayout.LayoutParams(-1, Screen.dp(360)));
    scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot) {
        ViewGroup.LayoutParams params = materialFrame.getLayoutParams();
        params.height = Math.max(Screen.dp(200), b - t - Screen.dp(56)); materialFrame.setLayoutParams(params);
      }
    });
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.listeners().subscribeToMessageUpdates(getChatId(), this);
    tdlib.listeners().subscribeToSettingsUpdates(this);
    tdlib.listeners().addCleanupListener(this);
    tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    subscribed = true;
    observeTopic(); render();
    loadLink();
    if (restoreScrollPending) restoreScrollAfterLayout();
    return scroll;
  }

  private void restoreScrollAfterLayout () {
    scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override public boolean onPreDraw () {
        if (restoreScrollPending && !isDestroyed() && !terminal) {
          if (!ready() || !materialsStarted) return true;
          // Wait for the restored page (or a settled empty/error result), not the loading header.
          if (restoreTab >= 0 || activeTab < 0) {
            for (ForumTopicMedia.Category category : categories) {
              if (category.availability == ForumTopicMedia.Availability.LOADING) return true;
            }
          }
        }
        scroll.getViewTreeObserver().removeOnPreDrawListener(this);
        if (restoreScrollPending && !isDestroyed()) {
          restoreScrollPending = false;
          scroll.scrollTo(0, restoreScroll);
          restoreScroll = 0;
        }
        return true;
      }
    });
  }

  private TextView text (float size, int color) {
    TextView view = new TextView(context); view.setTextSize(size); view.setTextColor(Theme.getColor(color));
    addThemeTextColorListener(view, color); return view;
  }
  private void createLinkRow (LinearLayout profile) {
    linkRow = new LinearLayout(context); linkRow.setGravity(Gravity.CENTER_VERTICAL);
    linkRow.setMinimumHeight(Screen.dp(72)); linkRow.setFocusable(true);
    linkRow.setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
    linkRow.setBackground(Theme.fillingSelector(ColorId.filling, 12f)); addThemeInvalidateListener(linkRow);
    linkRow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    LinearLayout texts = new LinearLayout(context); texts.setOrientation(LinearLayout.VERTICAL);
    linkText = text(16, ColorId.text); linkText.setGravity(Gravity.START); linkText.setSingleLine(false);
    linkLabel = text(13, ColorId.textLight); linkLabel.setGravity(Gravity.START); linkLabel.setSingleLine(false);
    linkLabel.setPadding(0, Screen.dp(4), 0, 0);
    texts.addView(linkText, new LinearLayout.LayoutParams(-1, -2));
    texts.addView(linkLabel, new LinearLayout.LayoutParams(-1, -2));
    texts.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    linkRow.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
    linkCopy = new ImageView(context); linkCopy.setImageResource(R.drawable.baseline_content_copy_24);
    linkCopy.setColorFilter(Theme.getColor(ColorId.icon)); addThemeFilterListener(linkCopy, ColorId.icon);
    linkCopy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Screen.dp(24), Screen.dp(24));
    iconParams.setMarginStart(Screen.dp(16)); linkRow.addView(linkCopy, iconParams);
    linkRow.setOnClickListener(v -> copyLink());
    linkRow.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo (View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
        if (host.isEnabled()) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,
          Lang.getString(profileLink.state() == ForumTopicProfileLink.State.READY ? R.string.CopyLink : R.string.FirebaseErrorResolveTryAgain)));
      }
    });
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
    rowParams.setMargins(Screen.dp(4), Screen.dp(12), Screen.dp(4), Screen.dp(4));
    profile.addView(linkRow, rowParams);
    linkHint = text(12, ColorId.textLight); linkHint.setGravity(Gravity.START);
    linkHint.setPadding(Screen.dp(4), Screen.dp(4), Screen.dp(4), 0);
    profile.addView(linkHint, new LinearLayout.LayoutParams(-1, -2));
    renderLink();
  }
  private LinearLayout actionRow () {
    LinearLayout row = new LinearLayout(context); row.setBaselineAligned(false);
    actions.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
  }
  private ActionView button (LinearLayout row, int label, int icon, Runnable action) {
    ActionView button = new ActionView(context); button.setText(Lang.getString(label)); button.setIcon(icon);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    params.setMargins(Screen.dp(4), Screen.dp(4), Screen.dp(4), Screen.dp(4));
    row.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
  }
  private final class ActionView extends TextView {
    private int iconId;
    ActionView (Context context) {
      super(context);
      setGravity(Gravity.CENTER); setTypeface(Fonts.getRobotoMedium()); setTextSize(15);
      setSingleLine(false); setMaxLines(Integer.MAX_VALUE); setMinWidth(0); setMinHeight(Screen.dp(76));
      setPadding(Screen.dp(8), Screen.dp(12), Screen.dp(8), Screen.dp(12));
      setCompoundDrawablePadding(Screen.dp(6)); setFocusable(true);
      setBackground(Theme.fillingSelector(ColorId.filling, 12f));
      setTextColor(Theme.getColor(ColorId.textLink)); addThemeTextColorListener(this, ColorId.textLink);
      TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Theme.getColor(ColorId.textLink)));
      addThemeCompoundDrawableColorListener(this, ColorId.textLink); addThemeInvalidateListener(this);
    }
    void setIcon (int icon) {
      if (iconId != icon) { iconId = icon; setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0); }
    }
    @Override public void setEnabled (boolean enabled) {
      super.setEnabled(enabled); setAlpha(enabled ? 1f : .45f);
    }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
    }
  }
  private static final class MaterialTab extends TextView {
    MaterialTab (Context context) {
      super(context);
      setGravity(Gravity.CENTER); setSingleLine(true); setTextSize(15); setTypeface(Fonts.getRobotoMedium());
      setMinHeight(Screen.dp(48)); setMinWidth(Screen.dp(64));
      setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
      setBackground(Theme.transparentSelector()); setFocusable(true);
    }
    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (isSelected()) {
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(1.5f), Screen.dp(12), getHeight() - Screen.dp(3), getWidth() - Screen.dp(12), getHeight(),
          Paints.fillingPaint(Theme.getColor(ColorId.textLink)));
      }
    }
  }

  private void observeTopic () {
    if (subscription != null) subscription.close();
    Object ticket = epoch;
    subscription = tdlib.topics().observeTopic(key(), (value, error) -> {
      if (isDestroyed() || epoch != ticket) return;
      topic = value; metadataError = error;
      terminal = error != null && (error.code == 404 || error.code == 403 || error.message.contains("TOPIC_DELETED") || error.message.contains("TOPIC_NOT_FOUND"));
      render();
      if (ready() && !materialsStarted) { materialsStarted = true; refreshMaterials(); }
    });
  }
  private void retryMetadata () {
    if (terminal || busy) return;
    metadataError = null; tdlib.topics().retryTopic(key()); render();
  }
  private TdApi.ForumTopic currentTopic () {
    TdlibForumTopicManager.Entry entry = tdlib.topics().find(key());
    return entry != null ? entry.value : null;
  }
  private boolean ready () { return !isDestroyed() && !terminal && metadataError == null && topic != null && tdlib.chatAvailable(tdlib.chat(getChatId())); }
  private boolean canEdit () { TdApi.ForumTopic current = currentTopic(); return ready() && current != null && ForumTopicPolicy.canEdit(tdlib.chatStatus(getChatId()), current.info); }
  private boolean canManage () { return ready() && ForumTopicPolicy.canManage(tdlib.chatStatus(getChatId())); }

  private void render () {
    if (title == null) return;
    boolean available = ready();
    group.setText(Lang.getString(R.string.ForumProfileGroupLink, tdlib.chatTitle(getChatId())));
    title.setText(topic != null ? topic.info.name : Lang.getString(R.string.ForumProfileLoading));
    state.setText(Lang.getString(terminal || topic != null && !tdlib.chatAvailable(tdlib.chat(getChatId())) ? R.string.ForumProfileUnavailable :
      metadataError != null ? R.string.ForumProfileRetry : topic == null ? R.string.ForumProfileLoading : topic.info.isClosed ? R.string.ForumTopicClosed :
        topic.info.isGeneral ? R.string.ForumProfileGeneralHint : R.string.ForumTopicTitle));
    state.setVisibility(available && !topic.info.isClosed && !topic.info.isGeneral ? View.GONE : View.VISIBLE);
    actions.setVisibility(available ? View.VISIBLE : View.GONE);
    messageButton.setEnabled(available); muteButton.setEnabled(available && !busy && topic.notificationSettings != null);
    pinButton.setEnabled(canManage() && !busy);
    pinButton.setVisibility(canManage() ? View.VISIBLE : View.GONE);
    if (topic != null) {
      boolean muted = ForumPresentation.isMuted(topic.notificationSettings, tdlib.chatMuteFor(getChatId()) > 0);
      muteButton.setText(Lang.getString(muted ? R.string.ForumProfileUnmute : R.string.ForumProfileMute));
      muteButton.setIcon(muted ? R.drawable.baseline_notifications_24 : R.drawable.baseline_notifications_off_24);
      pinButton.setText(Lang.getString(topic.isPinned ? R.string.ForumUnpinTopic : R.string.ForumPinTopic));
      pinButton.setIcon(topic.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
      boolean emoji = topic.info.icon.customEmojiId != 0;
      regularIcon.setVisibility(emoji ? View.GONE : View.VISIBLE); customIcon.setVisibility(emoji ? View.VISIBLE : View.GONE);
      if (emoji && renderedEmoji != topic.info.icon.customEmojiId) {
        renderedEmoji = topic.info.icon.customEmojiId;
        TdApi.FormattedText glyph = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(renderedEmoji))});
        customIcon.setText(glyph.text, TextEntity.valueOf(tdlib, glyph, null), false);
      }
      if (!emoji) {
        regularIcon.setText(topic.info.isGeneral ? "#" : topic.info.name.isEmpty() ? "" : topic.info.name.substring(0, topic.info.name.offsetByCodePoints(0, 1)));
        GradientDrawable background = new GradientDrawable(); background.setShape(GradientDrawable.OVAL); background.setColor(0xff000000 | topic.info.icon.color);
        regularIcon.setBackground(background);
      }
    } else { regularIcon.setText(""); customIcon.setVisibility(View.GONE); }
    if (editButton != null) { editButton.setVisibility(canEdit() ? View.VISIBLE : View.GONE); editButton.setEnabled(!busy); }
    if (moreButton != null) moreButton.setEnabled(available && !busy);
    if (!available) { materialFrame.setVisibility(View.GONE); tabScroll.setVisibility(View.GONE); materialState.setVisibility(View.GONE); }
    else renderTabs();
    if (available && !profileLink.matches(getChatId(), getForumTopicId())) loadLink();
    renderLink();
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_clear) { header.addClearButton(menu, this); return; }
    if (id != R.id.menu_forumTopicProfile) return;
    header.addSearchButton(menu, this);
    header.addButton(menu, R.id.forum_profile_edit, R.drawable.baseline_edit_24, getHeaderIconColorId(), this, Screen.dp(49));
    header.addButton(menu, R.id.forum_profile_more, R.drawable.baseline_more_vert_24, getHeaderIconColorId(), this, Screen.dp(49));
    editButton = menu.findViewById(R.id.forum_profile_edit); moreButton = menu.findViewById(R.id.forum_profile_more);
    editButton.setContentDescription(Lang.getString(R.string.ForumEditTopic)); moreButton.setContentDescription(Lang.getString(R.string.ForumProfileMore));
    render();
  }
  @Override public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_clear) { clearSearchInput(); return; }
    if (id == R.id.menu_btn_search) { if (ready()) openSearchMode(); return; }
    if (busy || !ready()) return;
    if (id == R.id.forum_profile_edit) openEditor();
    else if (id == R.id.forum_profile_more) {
      if (topic.isPinned && canManage()) withPins(this::showOverflow);
      else showOverflow(Collections.emptyList());
    }
  }

  @Override protected void onSearchInputChanged (String text) {
    super.onSearchInputChanged(text); query = text;
    if (activeTab >= 0 && pages[activeTab] != null) pages[activeTab].search(text);
  }
  @Override protected void onLeaveSearchMode () { onSearchInputChanged(""); }

  private void openEditor () {
    if (!canEdit()) return;
    ForumTopicEditController.open(this, getChatId(), getForumTopicId());
  }

  private void run (Consumer<ForumTopicActions.Callback<TdApi.Ok>> operation) {
    if (busy || !ready()) return;
    busy = true; render(); Object ticket = epoch;
    operation.accept((ok, error) -> {
      if (isDestroyed() || epoch != ticket) return;
      busy = false; render(); if (error != null) error(error.message);
    });
  }
  private void error (String error) {
    if (!isDestroyed()) showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(getName()).setMessage(error).setPositiveButton(Lang.getString(R.string.OK), null));
  }
  private void toggleMute () {
    TdApi.ForumTopic current = currentTopic();
    if (current == null || current.notificationSettings == null) return;
    run(done -> tdlib.topics().actions.setNotifications(key(), ForumTopicNotifications.toggleMute(current.notificationSettings, tdlib.chatMuteFor(getChatId()) > 0), done));
  }
  private void copyLink () {
    if (!ready()) return;
    if (profileLink.matches(getChatId(), getForumTopicId()) && profileLink.state() == ForumTopicProfileLink.State.READY) {
      UI.copyText(profileLink.url(), R.string.CopiedLink);
    } else if (profileLink.state() != ForumTopicProfileLink.State.LOADING) loadLink();
  }
  private void loadLink () {
    if (isDestroyed() || terminal || !tdlib.chatAvailable(tdlib.chat(getChatId()))) return;
    ForumTopicProfileLink.Request request = profileLink.begin(getChatId(), getForumTopicId());
    if (request == null) return;
    Object ticket = epoch; renderLink();
    // The existing helper uses read-only GetForumTopicLink with the typed forum topic ID.
    tdlib.topics().actions.getLink(new TdlibForumTopicManager.Key(request.chatId, request.forumTopicId), (link, error) -> tdlib.ui().post(() -> {
      if (!acceptLinkResult(ticket, request)) return;
      if (profileLink.complete(request, error == null && link != null ? link.link : null, error == null && link != null && link.isPublic)) renderLink();
    }));
    tdlib.ui().postDelayed(() -> {
      if (acceptLinkResult(ticket, request) && profileLink.complete(request, null, false)) renderLink();
    }, 15000);
  }
  private boolean acceptLinkResult (Object ticket, ForumTopicProfileLink.Request request) {
    return !isDestroyed() && !terminal && epoch == ticket && getChatId() == request.chatId && getForumTopicId() == request.forumTopicId;
  }
  private void renderLink () {
    if (linkRow == null) return;
    boolean unavailable = terminal || metadataError != null || !tdlib.chatAvailable(tdlib.chat(getChatId()));
    boolean hasLink = !unavailable && profileLink.matches(getChatId(), getForumTopicId()) && profileLink.state() == ForumTopicProfileLink.State.READY;
    boolean failed = unavailable || profileLink.state() == ForumTopicProfileLink.State.UNAVAILABLE;
    linkRow.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    linkText.setTextDirection(hasLink ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_INHERIT);
    linkText.setText(hasLink ? profileLink.displayUrl() : Lang.getString(failed ? R.string.NoLinkInfo : R.string.LoadingInformation));
    linkLabel.setText(Lang.getString(failed && ready() ? R.string.FirebaseErrorResolveTryAgain :
      hasLink && !profileLink.isPublic() ? R.string.ForumProfileTopicLink : R.string.InviteLink));
    linkCopy.setVisibility(hasLink ? View.VISIBLE : View.GONE);
    linkRow.setEnabled(ready() && (hasLink || failed));
    linkRow.setContentDescription(Lang.getString(R.string.ForumProfileSettingValue, linkLabel.getText(), linkText.getText()));
    linkHint.setText(Lang.getString(R.string.ForumProfileLinkHint));
    linkHint.setLayoutDirection(linkRow.getLayoutDirection());
    linkHint.setVisibility(hasLink && !profileLink.isPublic() ? View.VISIBLE : View.GONE);
  }
  private void togglePin () {
    if (!canManage()) return;
    withPins(topics -> {
      TdApi.ForumTopic current = currentTopic(); if (current == null || !canManage()) return;
      int count = 0; for (TdApi.ForumTopic value : topics) if (value.isPinned) count++;
      if (!current.isPinned && count >= tdlib.options().pinnedForumTopicCountMax) {
        error(Lang.getString(R.string.ForumPinLimit, tdlib.options().pinnedForumTopicCountMax)); return;
      }
      run(done -> tdlib.topics().actions.setPinned(key(), !current.isPinned, done));
    });
  }
  private void closePins () { if (pinsSession != null) { pinsSession.close(); pinsSession = null; } }
  private void withPins (Consumer<List<TdApi.ForumTopic>> action) {
    if (busy || !canManage()) return;
    busy = true; render(); Object ticket = epoch;
    pinsSession = tdlib.topics().openList(getChatId(), "", snapshot -> {
      if (isDestroyed() || ticket != epoch || pinsSession == null) return;
      if (snapshot.error != null) { closePins(); busy = false; render(); error(snapshot.error.message); }
      else if (snapshot.initialized && !snapshot.refreshing && !snapshot.stale) {
        if (!ForumTopicPolicy.completePinnedPrefix(snapshot.topics, snapshot.endReached)) { pinsSession.loadMore(); return; }
        closePins(); busy = false; render(); if (canManage()) action.accept(snapshot.topics);
      }
    });
    pinsSession.refresh();
    ForumTopicStore.ListSession requested = pinsSession;
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed() && epoch == ticket && requested == pinsSession) {
        closePins(); busy = false; render(); error(Lang.getString(R.string.ForumProfileRetry));
      }
    }, 15000);
  }
  private void movePin (int direction) {
    withPins(topics -> {
      int[] ids = ForumTopicPolicy.movePin(topics, getForumTopicId(), direction);
      if (ids != null && canManage()) run(done -> tdlib.topics().actions.setPinnedOrder(getChatId(), ids, done));
    });
  }
  private static void add (List<String> labels, List<Runnable> actions, int label, Runnable action) { labels.add(Lang.getString(label)); actions.add(action); }
  private void showOverflow (List<TdApi.ForumTopic> pins) {
    if (!ready() || busy) return;
    TdApi.ForumTopic current = currentTopic(); if (current == null) return;
    List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
    if (canEdit()) add(labels, actions, current.info.isClosed ? R.string.ForumReopenTopic : R.string.ForumCloseTopic, () -> {
      TdApi.ForumTopic fresh = currentTopic(); if (fresh != null && canEdit()) run(done -> tdlib.topics().actions.setClosed(key(), !fresh.info.isClosed, done));
    });
    if (current.isPinned && canManage()) {
      if (ForumTopicPolicy.movePin(pins, getForumTopicId(), -1) != null) add(labels, actions, R.string.ForumMovePinUp, () -> movePin(-1));
      if (ForumTopicPolicy.movePin(pins, getForumTopicId(), 1) != null) add(labels, actions, R.string.ForumMovePinDown, () -> movePin(1));
    }
    add(labels, actions, R.string.ForumNotifications, () -> {
      ForumTopicNotificationController controller = new ForumTopicNotificationController(context, tdlib);
      controller.setArguments(getArgumentsStrict()); navigateTo(controller);
    });
    add(labels, actions, R.string.ForumShowAllMessages, () -> topicUi.setViewMode(false));
    add(labels, actions, R.string.ForumShowTopics, () -> topicUi.setViewMode(true));
    if (!current.info.isGeneral && ForumTopicPolicy.canOfferDelete(tdlib.chatStatus(getChatId()), current.info)) add(labels, actions, R.string.ForumDeleteTopic, this::deleteTopic);
    if (ForumTopicPolicy.isMember(tdlib.chatStatus(getChatId()))) add(labels, actions, R.string.ForumProfileLeaveGroup, () -> {
      if (ForumTopicPolicy.isMember(tdlib.chatStatus(getChatId()))) tdlib.ui().processLeaveButton(this, getChatList(), getChatId(), R.id.btn_removeChatFromList, () -> tdlib.ui().exitToChatScreen(this, getChatId()));
    });
    if (canDeleteGroup()) add(labels, actions, R.string.ForumProfileDeleteGroup, this::deleteGroup);
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(current.info.name).setItems(labels.toArray(new String[0]),
      (dialog, which) -> { if (ready() && !busy) actions.get(which).run(); }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }
  private void deleteTopic () {
    TdApi.ForumTopic current = currentTopic();
    if (current == null || current.info.isGeneral || !ForumTopicPolicy.canOfferDelete(tdlib.chatStatus(getChatId()), current.info)) return;
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(Lang.getString(R.string.ForumDeleteTopic))
      .setMessage(Lang.getString(R.string.ForumProfileDeleteTopicConfirm, current.info.name, tdlib.chatTitle(getChatId())))
      .setNegativeButton(Lang.getString(R.string.Cancel), null).setPositiveButton(Lang.getString(R.string.Delete), (dialog, which) -> {
        TdApi.ForumTopic fresh = currentTopic();
        if (fresh != null && !fresh.info.isGeneral && ForumTopicPolicy.canOfferDelete(tdlib.chatStatus(getChatId()), fresh.info))
          run(done -> tdlib.topics().actions.delete(key(), (ok, error) -> { done.onResult(ok, error); if (error == null && !isDestroyed()) { terminal = true; render(); } }));
      }));
  }
  private boolean canDeleteGroup () { TdApi.Chat chat = tdlib.chat(getChatId()); return ready() && chat != null && chat.canBeDeletedForAllUsers; }
  private void deleteGroup () {
    if (!canDeleteGroup()) return;
    showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(Lang.getString(R.string.ForumProfileDeleteGroup))
      .setMessage(Lang.getString(R.string.ForumProfileDeleteGroupConfirm, tdlib.chatTitle(getChatId())))
      .setNegativeButton(Lang.getString(R.string.Cancel), null).setPositiveButton(Lang.getString(R.string.Delete), (dialog, which) -> {
        if (!canDeleteGroup()) return;
        showAlert(new AlertDialog.Builder(context, Theme.dialogTheme()).setTitle(tdlib.chatTitle(getChatId())).setMessage(Lang.getString(R.string.DestroyGroupHint))
          .setNegativeButton(Lang.getString(R.string.Cancel), null).setPositiveButton(Lang.getString(R.string.ForumProfileDeleteGroup), (lastDialog, lastWhich) -> {
            if (!canDeleteGroup() || busy) return;
            run(done -> tdlib.send(new TdApi.DeleteChat(getChatId()), (ok, error) -> tdlib.ui().post(() -> {
              done.onResult(ok, error); if (error == null && !isDestroyed()) tdlib.ui().exitToChatScreen(this, getChatId());
            })));
          }));
      }));
  }

  private void refreshMaterials () {
    if (!ready()) return;
    for (int i = 0; i < categories.length; i++) refreshCategory(i);
    renderTabs();
  }
  private void refreshCategory (int index) {
    ForumTopicMedia.Category category = categories[index];
    int ticket = category.begin(); Object lifecycle = epoch;
    tdlib.send(new TdApi.GetChatMessageCount(getChatId(), getTopicId(), ForumTopicMedia.filter(index), false), (count, error) -> tdlib.ui().post(() -> {
      if (isDestroyed() || epoch != lifecycle) return;
      category.count(ticket, count != null ? count.count : -1, error);
    }));
    probeCategory(index, ticket, lifecycle, 0, true);
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed() && epoch == lifecycle && category.availability == ForumTopicMedia.Availability.LOADING &&
          category.confirm(ticket, false, new TdApi.Error(408, "Material availability request timed out"))) renderTabs();
    }, 15000);
  }
  private void probeCategory (int index, int ticket, Object lifecycle, long offset, boolean mayRetry) {
    ForumTopicMedia.Category category = categories[index];
    // At most two one-message pages. A partial/unknown result is not a reason to scan history.
    tdlib.send(new TdApi.SearchChatMessages(getChatId(), getTopicId(), "", null, offset, 0, 1, ForumTopicMedia.filter(index)), (messages, error) -> tdlib.ui().post(() -> {
      if (isDestroyed() || epoch != lifecycle || !category.isCurrent(ticket)) return;
      boolean found = false;
      if (messages != null) for (TdApi.Message message : messages.messages) {
        if (ForumTopicMedia.matches(getChatId(), getTopicId(), message) && ProfileController.filterMediaMessage(message) && Td.matchesFilter(message, ForumTopicMedia.filter(index))) { found = true; break; }
      }
      if (error == null && messages != null && !found) {
        if (messages.nextFromMessageId != 0 && messages.nextFromMessageId != offset && mayRetry) {
          probeCategory(index, ticket, lifecycle, messages.nextFromMessageId, false); return;
        }
        if (messages.totalCount < 0 || messages.nextFromMessageId != 0) {
          if (category.unknown(ticket)) renderTabs(); return;
        }
      }
      if (category.confirm(ticket, found, error)) renderTabs();
    }));
  }

  private void renderTabs () {
    if (tabs == null || !ready()) return;
    List<Integer> visible = ForumTopicMedia.visible(categories);
    int selected = ForumTopicMedia.nearest(visible, activeTab);
    if (restoreTab >= 0 && visible.contains(restoreTab)) { selected = restoreTab; restoreTab = -1; }
    if (!visible.equals(visibleTabs)) {
      visibleTabs = visible; tabs.removeAllViews();
      for (int index : visible) {
        MaterialTab tab = new MaterialTab(context); tab.setText(Lang.getString(TAB_NAMES[index])); tab.setTag(index);
        addThemeInvalidateListener(tab); tabs.addView(tab, new LinearLayout.LayoutParams(-2, -2));
        tab.setOnClickListener(v -> selectTab(index));
      }
    }
    tabScroll.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
    boolean loading = false, failed = false, unknown = false;
    for (ForumTopicMedia.Category category : categories) {
      loading |= category.availability == ForumTopicMedia.Availability.LOADING;
      failed |= category.error != null || category.availability == ForumTopicMedia.Availability.ERROR;
      unknown |= category.availability == ForumTopicMedia.Availability.UNKNOWN;
    }
    materialState.setVisibility(visible.isEmpty() || failed || unknown ? View.VISIBLE : View.GONE);
    materialState.setText(Lang.getString(failed ? tdlib.isConnected() ? R.string.ForumProfileMediaRetry : R.string.ForumProfileMediaOffline :
      loading ? R.string.ForumProfileMediaLoading : unknown ? R.string.ForumProfileMediaUnknown : R.string.ForumProfileMediaEmpty));
    materialFrame.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
    if (selected != activeTab || selected >= 0 && materialFrame.getChildCount() == 0) selectTab(selected);
    else updateSelectedTab();
  }
  private void selectTab (int index) {
    if (index == activeTab && materialFrame.getChildCount() > 0) { updateSelectedTab(); return; }
    if (activeTab >= 0 && pages[activeTab] != null) pages[activeTab].onBlur();
    materialFrame.removeAllViews(); activeTab = index;
    if (index >= 0) {
      SharedBaseController<?> page = pages[index];
      if (page == null) {
        page = SharedBaseController.valueOf(context, tdlib, ForumTopicMedia.filter(index));
        page.setArguments(new SharedBaseController.Args(getChatId(), getTopicId())); page.setStandaloneParent(this);
        pages[index] = page;
      }
      View pageView = page.getValue();
      // A list container must not move the outer profile to its first item when data arrives.
      // Item views retain their own keyboard/accessibility focus behavior.
      page.getRecyclerView().setFocusable(false);
      page.getRecyclerView().setFocusableInTouchMode(false);
      page.getRecyclerView().setPreserveFocusAfterLayout(false);
      materialFrame.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
      page.search(query);
      page.getRecyclerView().setNestedScrollingEnabled(true);
      if (restoredPageStates[index] != null) {
        page.getRecyclerView().getLayoutManager().onRestoreInstanceState(restoredPageStates[index]); restoredPageStates[index] = null;
      }
      if (isFocused()) page.onFocus();
    }
    updateSelectedTab();
  }
  private void updateSelectedTab () {
    for (int i = 0; i < tabs.getChildCount(); i++) {
      TextView tab = (TextView) tabs.getChildAt(i); boolean selected = (Integer) tab.getTag() == activeTab;
      tab.setSelected(selected);
      int color = selected ? ColorId.textLink : ColorId.textLight;
      tab.setTextColor(Theme.getColor(color)); addOrUpdateThemeTextColorListener(tab, color); tab.invalidate();
    }
  }
  private void scheduleMaterialsRefresh () {
    if (refreshScheduled || !ready()) return;
    refreshScheduled = true; Object ticket = epoch;
    tdlib.ui().postDelayed(() -> {
      if (isDestroyed() || ticket != epoch) return;
      refreshScheduled = false; refreshMaterials();
    }, 400);
  }
  @Override public void onNewMessage (TdApi.Message message) {
    if (message.chatId != getChatId()) return;
    Object ticket = epoch;
    tdlib.ui().post(() -> {
      if (isDestroyed() || epoch != ticket || !ready()) return;
      rememberContentMessage(message);
      if (!ForumTopicMedia.matches(getChatId(), getTopicId(), message) || !ProfileController.filterMediaMessage(message)) return;
      for (int i = 0; i < categories.length; i++) if (Td.matchesFilter(message, ForumTopicMedia.filter(i))) categories[i].arrived();
      renderTabs();
    });
  }
  @Override public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) { onNewMessage(message); }
  @Override public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent content) {
    if (chatId != getChatId()) return;
    Object ticket = epoch;
    tdlib.ui().post(() -> {
      if (isDestroyed() || epoch != ticket || !ready()) return;
      TdApi.Message cached = recentContentMessages.get(messageId);
      if (cached != null) { onCachedContentChanged(cached, content); return; }
      if (activeContent.containsKey(messageId)) { activeContent.put(messageId, content); return; }
      Long lastRequest = recentContentRequests.get(messageId);
      if (lastRequest != null && SystemClock.uptimeMillis() - lastRequest < 5000) return;
      if (!pendingContent.containsKey(messageId) && pendingContent.size() >= MAX_PENDING_CONTENT) {
        // Overflow becomes one debounced, typed availability refresh, never a history scan.
        scheduleMaterialsRefresh(); return;
      }
      pendingContent.put(messageId, content); scheduleContentPump();
    });
  }
  private void rememberContentMessage (TdApi.Message message) {
    recentContentMessages.remove(message.id); recentContentMessages.put(message.id, message);
    if (recentContentMessages.size() > MAX_RECENT_CONTENT) recentContentMessages.remove(recentContentMessages.keySet().iterator().next());
  }
  /** UI-thread seam: embedded pages can supply membership/metadata before replacing a cached row. */
  void onCachedContentChanged (TdApi.Message message, TdApi.MessageContent content) {
    if (!ready() || message.chatId != getChatId()) return;
    message.content = content; rememberContentMessage(message); pendingContent.remove(message.id);
    if (activeContent.containsKey(message.id)) activeContent.put(message.id, content);
    if (!ForumTopicMedia.matches(getChatId(), getTopicId(), message)) return;
    for (SharedBaseController<?> page : pages) if (page != null) {
      page.editMessage(message.id, content); page.addMessage(message);
    }
    scheduleMaterialsRefresh();
  }
  private void scheduleContentPump () {
    if (contentPumpScheduled || pendingContent.isEmpty() || activeContent.size() >= MAX_CONTENT_REQUESTS || !ready()) return;
    contentPumpScheduled = true; Object ticket = epoch;
    tdlib.ui().postDelayed(() -> {
      if (isDestroyed() || epoch != ticket) return;
      contentPumpScheduled = false;
      if (!ready()) { pendingContent.clear(); return; }
      while (!pendingContent.isEmpty() && activeContent.size() < MAX_CONTENT_REQUESTS) {
        long messageId = pendingContent.keySet().iterator().next();
        activeContent.put(messageId, pendingContent.remove(messageId));
        recentContentRequests.remove(messageId); recentContentRequests.put(messageId, SystemClock.uptimeMillis());
        if (recentContentRequests.size() > MAX_RECENT_CONTENT) recentContentRequests.remove(recentContentRequests.keySet().iterator().next());
        tdlib.send(new TdApi.GetMessage(getChatId(), messageId), (message, error) -> tdlib.ui().post(() -> {
          if (isDestroyed() || epoch != ticket) return;
          TdApi.MessageContent latest = activeContent.remove(messageId);
          if (ready() && latest != null) {
            if (error == null && message != null) onCachedContentChanged(message, latest);
            else scheduleMaterialsRefresh();
          }
          scheduleContentPump();
        }));
      }
    }, 200);
  }
  @Override public void onMessagesDeleted (long chatId, long[] ids) {
    if (chatId != getChatId()) return;
    Object ticket = epoch;
    tdlib.ui().post(() -> {
      if (isDestroyed() || epoch != ticket) return;
      for (long id : ids) {
        recentContentMessages.remove(id); pendingContent.remove(id);
        // Retain the slot until its callback arrives, but never resurrect a deleted row.
        if (activeContent.containsKey(id)) activeContent.put(id, null);
      }
      scheduleMaterialsRefresh();
    });
  }
  @Override public void onChatTitleChanged (long chatId, String title) { runOnUiThreadOptional(this::render); }
  @Override public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) { runOnUiThreadOptional(this::render); }
  @Override public void onSupergroupUpdated (TdApi.Supergroup group) { runOnUiThreadOptional(this::render); }
  @Override public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) { if (chatId == getChatId()) runOnUiThreadOptional(this::render); }
  @Override public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    if (Td.matchesScope(tdlib.chatType(getChatId()), scope)) runOnUiThreadOptional(this::render);
  }
  @Override public void onFocus () {
    super.onFocus(); render(); if (activeTab >= 0 && pages[activeTab] != null && !pages[activeTab].isFocused()) pages[activeTab].onFocus();
    if (restoreSearch) { restoreSearch = false; String saved = query; openSearchMode(); setSearchInput(saved); }
  }
  @Override public void onBlur () { if (activeTab >= 0 && pages[activeTab] != null) pages[activeTab].onBlur(); super.onBlur(); }
  @Override protected void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1); visibleTabs = Collections.emptyList(); render();
  }

  public static void saveArguments (Bundle out, String prefix, Arguments args) {
    out.putLong(prefix + "forum_profile_chat", args.chatId); out.putInt(prefix + "forum_profile_topic", args.forumTopicId);
    out.putString(prefix + "forum_profile_list", args.chatList != null ? TD.makeChatListKey(args.chatList) : "");
  }
  @Nullable public static Arguments restoreArguments (Bundle in, String prefix) {
    long chatId = in.getLong(prefix + "forum_profile_chat"); int topicId = in.getInt(prefix + "forum_profile_topic");
    return chatId != 0 && topicId > 0 ? new Arguments(chatId, topicId, TD.chatListFromKey(in.getString(prefix + "forum_profile_list"))) : null;
  }
  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix); saveArguments(out, prefix, getArgumentsStrict());
    out.putInt(prefix + "forum_profile_tab", activeTab); out.putInt(prefix + "forum_profile_scroll", scroll != null && !restoreScrollPending ? scroll.getScrollY() : restoreScroll);
    out.putString(prefix + "forum_profile_query", query);
    for (int i = 0; i < pages.length; i++) {
      Parcelable state = pages[i] != null && pages[i].getRecyclerView() != null ? pages[i].getRecyclerView().getLayoutManager().onSaveInstanceState() : restoredPageStates[i];
      if (state != null) out.putParcelable(prefix + "forum_profile_page_" + i, state);
    }
    return true;
  }
  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    Arguments args = restoreArguments(in, prefix);
    if (args == null || tdlib.chatSync(args.chatId) == null || !tdlib.isForum(args.chatId)) return false;
    setArguments(args); restoreTab = in.getInt(prefix + "forum_profile_tab", -1); restoreScroll = in.getInt(prefix + "forum_profile_scroll", 0);
    restoreScrollPending = restoreScroll > 0;
    query = in.getString(prefix + "forum_profile_query", ""); restoreSearch = !query.isEmpty();
    for (int i = 0; i < pages.length; i++) restoredPageStates[i] = in.getParcelable(prefix + "forum_profile_page_" + i);
    super.restoreInstanceState(in, prefix); return true;
  }
  @Override public void onPerformUserCleanup () {
    epoch = new Object();
    tdlib.ui().post(() -> {
      if (isDestroyed()) return;
      if (subscription != null) { subscription.close(); subscription = null; }
      profileLink.invalidate();
      clearContentUpdates();
      closePins(); for (ForumTopicMedia.Category category : categories) category.invalidate();
      for (int i = 0; i < pages.length; i++) if (pages[i] != null) { pages[i].destroy(); pages[i] = null; }
      materialFrame.removeAllViews(); topic = null; terminal = true; busy = false; render();
    });
  }
  @Override public void onPerformRestart () { onPerformUserCleanup(); }
  @Override public void onPerformStartup (boolean afterRestart) { /* Re-open explicitly after an account lifecycle boundary. */ }
  private void clearContentUpdates () {
    recentContentMessages.clear(); recentContentRequests.clear(); pendingContent.clear(); activeContent.clear(); contentPumpScheduled = false;
  }
  @Override public void destroy () {
    epoch = new Object(); closePins(); if (subscription != null) subscription.close();
    profileLink.invalidate();
    clearContentUpdates();
    for (SharedBaseController<?> page : pages) if (page != null) page.destroy();
    if (customIcon != null) customIcon.performDestroy();
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this); tdlib.listeners().unsubscribeFromMessageUpdates(getChatId(), this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(this); tdlib.listeners().removeCleanupListener(this);
      tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    }
    super.destroy();
  }
}
