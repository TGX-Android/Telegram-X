package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTopicEditorState;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetThemed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tgx.td.Td;

/** One fullscreen form for every Create/Edit entry point. Mutations belong to ForumTopicActions. */
public final class ForumTopicEditController extends ViewController<ForumTopicEditController.Arguments>
  implements Menu, ChatListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.MyUserDataChangeListener {
  private static final long PICKER_TIMEOUT_MS = 12000;

  public static final class Arguments {
    public final long chatId;
    public final int topicId;
    public Arguments (long chatId, int topicId) {
      if (chatId == 0 || topicId < 0) throw new IllegalArgumentException("Invalid topic editor context");
      this.chatId = chatId;
      this.topicId = topicId;
    }
  }

  /** topicId == 0 creates; a positive topicId edits only that topic. */
  public static void open (ViewController<?> owner, long chatId, int topicId) {
    if (owner.isDestroyed()) return;
    ForumTopicEditController controller = new ForumTopicEditController(owner.context(), owner.tdlib());
    controller.setArguments(new Arguments(chatId, topicId));
    if (topicId == 0) {
      controller.originTabsHost = ForumTabsNavigation.findHost(owner, chatId);
      controller.returnToTabs = controller.originTabsHost != null;
    }
    if (!owner.navigateTo(controller)) controller.destroy();
  }

  public ForumTopicEditController (Context context, Tdlib tdlib) { super(context, tdlib); }

  private ForumTopicEditorState form;
  private TdApi.ForumTopicInfo currentInfo;
  private ForumTopicStore.TopicSubscription topicSubscription;
  private ForumTopicStore.ListSession reconcileSession;
  private long supergroupId;
  private boolean subscribed, metadataFailed, metadataMissing, binding, defaultsLoaded, pickerLoading;
  private boolean nameTouched, checking, finishing, catalogFailed;
  private boolean returnToTabs, finishScheduled;
  private MessagesController originTabsHost;
  private int pickerGeneration, catalogGeneration, pendingCatalog, mutationGeneration;
  private String query = "", category = "", pickerError = "", selectionError = "", reconcileText = "";
  private long selectedSet;
  private TdApi.Sticker[] defaultIcons = new TdApi.Sticker[0];
  private final List<TdApi.Sticker> visibleIcons = new ArrayList<>();
  private final List<IconCell> cells = new ArrayList<>();
  private final List<TextView> colorButtons = new ArrayList<>();
  private final List<View> sectionButtons = new ArrayList<>();
  private final List<TdApi.ForumTopicInfo> candidates = new ArrayList<>();
  private RecyclerView recycler;
  private GridLayoutManager layoutManager;
  private EditorAdapter adapter;
  private LinearLayout content, pickerHeader, sections, colors, results;
  private HorizontalScrollView colorStrip;
  private EditText nameInput, searchInput;
  private ImageButton clearSearchButton;
  private TextView action, nameError, error, count, hint, pickerStatus, retryPicker, checkResult, retryTopic;
  private IconCell preview;
  private Parcelable restoredScroll;
  private int restoredFocus, restoredSelection = -1;

  @Override public int getId () { return R.id.controller_forumTopicEdit; }
  @Override public long getChatId () { return getArgumentsStrict().chatId; }
  @Override protected int getBackButton () { return BackHeaderButton.TYPE_BACK; }
  @Override protected int getMenuId () { return R.id.menu_forumTopicEdit; }
  // The Save view and listener belong to this editor. Reusing a cached menu skips
  // fillMenuItems(), leaving action null and the listener bound to the old editor.
  @Override protected boolean allowMenuReuse () { return false; }
  @Override public int getRootColorId () { return ColorId.background; }
  @Override public CharSequence getName () {
    return Lang.getString(getArgumentsStrict().topicId == 0 ? R.string.ForumEditorNew : R.string.ForumEditorEdit);
  }

  @Override public void setArguments (Arguments args) {
    super.setArguments(args);
    if (args.topicId == 0) form = ForumTopicEditorState.create();
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id != R.id.menu_forumTopicEdit) return;
    action = label(16, getHeaderTextColorId());
    action.setId(R.id.btn_forumTopicSave);
    action.setTypeface(Fonts.getRobotoMedium());
    action.setGravity(Gravity.CENTER);
    action.setPadding(Screen.dp(16), 0, Screen.dp(16), 0);
    action.setMinWidth(Screen.dp(80));
    action.setMinimumHeight(Screen.dp(56));
    action.setBackgroundResource(getBackButtonResource());
    action.setOnClickListener(v -> submit());
    menu.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    updateAction();
  }

  @Override public void onMenuItemPressed (int id, View view) { if (id == R.id.btn_forumTopicSave) submit(); }

  @Override protected View onCreateView (Context context) {
    recycler = new RecyclerView(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      recycler.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }
    ViewSupport.setThemedBackground(recycler, ColorId.background, this);
    recycler.setClipToPadding(false);
    recycler.setPadding(Screen.dp(12), Screen.dp(12), Screen.dp(12), Screen.dp(12) + extraBottomInset);
    recycler.setItemAnimator(null);
    layoutManager = new GridLayoutManager(context, 6);
    layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override public int getSpanSize (int position) { return position == 0 ? layoutManager.getSpanCount() : 1; }
    });
    recycler.setLayoutManager(layoutManager);
    createForm();
    adapter = new EditorAdapter();
    recycler.setAdapter(adapter);
    recycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      int spans = Math.max(3, (r - l - recycler.getPaddingLeft() - recycler.getPaddingRight()) / Screen.dp(52));
      if (spans != layoutManager.getSpanCount()) layoutManager.setSpanCount(spans);
    });
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.cache().addMyUserListener(this);
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup) {
      supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
      tdlib.cache().subscribeToSupergroupUpdates(supergroupId, this);
    }
    subscribed = true;
    if (getArgumentsStrict().topicId != 0) {
      topicSubscription = tdlib.topics().observeTopic(key(), (topic, failure) -> {
        if (isDestroyed()) return;
        metadataFailed = failure != null;
        metadataMissing = failure != null && (failure.code == 400 || failure.code == 404);
        currentInfo = topic != null && topic.info.chatId == getChatId() && topic.info.forumTopicId == getArgumentsStrict().topicId ? topic.info : null;
        if (currentInfo != null && (form == null || form.editable() && !form.hasChanges())) {
          form = ForumTopicEditorState.edit(currentInfo.name, currentInfo.icon.color, currentInfo.icon.customEmojiId, currentInfo.isGeneral);
        }
        updateForm();
      });
    }
    loadCatalog();
    updateForm();
    if (restoredScroll != null) layoutManager.onRestoreInstanceState(restoredScroll);
    return recycler;
  }

  private TextView label (float size, int color) {
    TextView view = new TextView(context());
    view.setTextSize(size);
    view.setTypeface(Fonts.getRobotoRegular());
    view.setTextColor(Theme.getColor(color));
    view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    addThemeTextColorListener(view, color);
    return view;
  }

  private TextView button (CharSequence title, Runnable click) {
    TextView view = label(14, ColorId.textLink);
    view.setText(title);
    view.setTypeface(Fonts.getRobotoMedium());
    view.setGravity(Gravity.CENTER);
    view.setPadding(Screen.dp(12), Screen.dp(8), Screen.dp(12), Screen.dp(8));
    view.setMinimumHeight(Screen.dp(48));
    view.setFocusable(true);
    view.setBackground(Theme.fillingSelector());
    addThemeInvalidateListener(view);
    view.setOnClickListener(v -> click.run());
    return view;
  }

  private LinearLayout column () {
    LinearLayout view = new LinearLayout(context());
    view.setOrientation(LinearLayout.VERTICAL);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      view.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }
    return view;
  }

  private EditText input (int hintId) {
    EditText view = new EditText(context());
    view.setTextSize(17);
    view.setTypeface(Fonts.getRobotoRegular());
    view.setSingleLine(true);
    view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    view.setTextColor(Theme.textAccentColor());
    view.setHintTextColor(Theme.textDecentColor());
    view.setHint(Lang.getString(hintId));
    view.setContentDescription(Lang.getString(hintId));
    view.setMinimumHeight(Screen.dp(52));
    view.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    view.setBackground(null);
    view.setSaveEnabled(false); // Saved explicitly in the isolated form, never the message draft.
    addThemeTextColorListener(view, ColorId.text);
    addThemeHintTextColorListener(view, ColorId.textLight);
    return view;
  }

  private void createForm () {
    content = column();
    content.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout card = column();
    card.setPadding(Screen.dp(14), Screen.dp(12), Screen.dp(14), Screen.dp(8));
    ViewSupport.setThemedBackground(card, ColorId.filling, this).setCornerRadius(16);
    TextView title = label(14, ColorId.textLink);
    title.setTypeface(Fonts.getRobotoMedium());
    title.setText(Lang.getString(R.string.ForumEditorCard));
    card.addView(title);
    LinearLayout row = new LinearLayout(context());
    row.setGravity(Gravity.CENTER_VERTICAL);
    preview = new IconCell(context());
    preview.setContentDescription(Lang.getString(R.string.ForumEditorPreview));
    preview.setOnClickListener(v -> {
      if (form != null && !form.general && form.editable()) searchInput.requestFocus();
    });
    row.addView(preview, new LinearLayout.LayoutParams(Screen.dp(52), Screen.dp(56)));
    nameInput = input(R.string.ForumEditorNameHint);
    nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    nameInput.setOnEditorActionListener((v, id, event) -> {
      if (id != EditorInfo.IME_ACTION_DONE) return false;
      submit(); return true;
    });
    row.addView(nameInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    card.addView(row);
    count = label(12, ColorId.textLight);
    count.setGravity(Gravity.END);
    card.addView(count);
    nameError = label(13, ColorId.textNegative);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      nameError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    card.addView(nameError);
    colors = new LinearLayout(context());
    int[] colorLabels = {R.string.ForumColorBlue, R.string.ForumColorYellow, R.string.ForumColorPurple, R.string.ForumColorGreen, R.string.ForumColorPink, R.string.ForumColorRed};
    for (int i = 0; i < ForumTopicPolicy.ICON_COLORS.length; i++) {
      final int color = ForumTopicPolicy.ICON_COLORS[i];
      TextView choice = button("●", () -> { if (form != null && form.setColor(color)) updateForm(); });
      choice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 26);
      choice.setPadding(0, 0, 0, 0);
      choice.setTextColor(0xff000000 | color);
      // These six RGBs are TDLib's topic colors, not theme colors.
      removeThemeListenerByTarget(choice);
      choice.setContentDescription(Lang.getString(colorLabels[i]));
      colorButtons.add(choice);
      colors.addView(choice, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
    }
    colorStrip = new HorizontalScrollView(context());
    colorStrip.setHorizontalScrollBarEnabled(false);
    colorStrip.addView(colors);
    card.addView(colorStrip);
    content.addView(card);
    hint = label(13, ColorId.textLight);
    hint.setPadding(Screen.dp(4), Screen.dp(10), Screen.dp(4), Screen.dp(10));
    content.addView(hint);
    error = label(14, ColorId.textNegative);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    content.addView(error);
    retryTopic = button(Lang.getString(R.string.ForumEditorRetry), () -> {
      metadataMissing = false; metadataFailed = false; tdlib.topics().retryTopic(key()); updateForm();
    });
    content.addView(retryTopic);
    checkResult = button(Lang.getString(R.string.ForumEditorCheckResult), this::reconcile);
    content.addView(checkResult);
    results = column();
    content.addView(results);
    pickerHeader = column();
    pickerHeader.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    ViewSupport.setThemedBackground(pickerHeader, ColorId.filling, this).setCornerRadius(12);
    HorizontalScrollView tabs = new HorizontalScrollView(context());
    tabs.setHorizontalScrollBarEnabled(false);
    sections = new LinearLayout(context());
    tabs.addView(sections);
    pickerHeader.addView(tabs);
    searchInput = input(R.string.ForumEditorSearch);
    searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
    searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchInput.setOnEditorActionListener((v, id, event) -> {
      if (id != EditorInfo.IME_ACTION_SEARCH) return false;
      loadPicker(); hideSoftwareKeyboard(); return true;
    });
    pickerHeader.addView(createSearchRow(Lang.getString(R.string.Clear)));
    TextView heading = label(14, ColorId.textLight);
    heading.setText(Lang.getString(R.string.ForumEditorIconHeading));
    heading.setGravity(Gravity.CENTER);
    heading.setPadding(0, Screen.dp(12), 0, Screen.dp(6));
    pickerHeader.addView(heading);
    pickerStatus = label(14, ColorId.textLight);
    pickerStatus.setGravity(Gravity.CENTER);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      pickerStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    pickerHeader.addView(pickerStatus);
    retryPicker = button(Lang.getString(R.string.ForumEditorRetry), this::loadCatalog);
    pickerHeader.addView(retryPicker);
    content.addView(pickerHeader);
    nameInput.addTextChangedListener(watcher(() -> {
      if (!binding && form != null && form.setName(nameInput.getText().toString())) { nameTouched = true; updateForm(); }
    }));
    searchInput.setText(query);
    searchInput.addTextChangedListener(watcher(() -> {
      if (binding) return;
      query = searchInput.getText().toString();
      category = ""; selectedSet = 0;
      // Clearing restores defaults immediately and invalidates any outstanding search.
      if (query.isEmpty()) { loadPicker(); return; }
      final int ticket = ++pickerGeneration;
      tdlib.ui().postDelayed(() -> { if (!isDestroyed() && ticket == pickerGeneration) loadPicker(); }, 250);
    }));
  }

  private LinearLayout createSearchRow (CharSequence clearDescription) {
    Context context = searchInput.getContext();
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      row.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }
    ViewSupport.setThemedBackground(row, ColorId.background, this).setCornerRadius(24);
    searchInput.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      searchInput.setPaddingRelative(Screen.dp(16), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    } else {
      searchInput.setPadding(Screen.dp(16), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    }
    row.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    clearSearchButton = new ImageButton(context);
    clearSearchButton.setImageResource(R.drawable.baseline_close_24);
    clearSearchButton.setScaleType(ImageView.ScaleType.CENTER);
    clearSearchButton.setPadding(0, 0, 0, 0);
    clearSearchButton.setColorFilter(Theme.iconColor());
    clearSearchButton.setBackground(Theme.circleSelector(48f, ColorId.background));
    clearSearchButton.setContentDescription(clearDescription);
    clearSearchButton.setFocusable(true);
    clearSearchButton.setOnClickListener(v -> {
      if (!searchInput.isEnabled()) return;
      searchInput.requestFocus();
      searchInput.setText("");
    });
    addThemeFilterListener(clearSearchButton, ColorId.icon);
    addThemeInvalidateListener(clearSearchButton);
    row.addView(clearSearchButton, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
    // Also observe programmatic/restored text while the picker callback is suppressed.
    searchInput.addTextChangedListener(watcher(this::updateSearchClear));
    updateSearchClear();
    return row;
  }

  private void updateSearchClear () {
    if (clearSearchButton == null) return;
    clearSearchButton.setVisibility(searchInput.length() > 0 ? View.VISIBLE : View.GONE);
    clearSearchButton.setEnabled(searchInput.isEnabled());
    clearSearchButton.setAlpha(searchInput.isEnabled() ? 1f : .45f);
  }

  private static TextWatcher watcher (Runnable changed) {
    return new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
      @Override public void afterTextChanged (Editable s) { changed.run(); }
    };
  }

  private TdlibForumTopicManager.Key key () { return new TdlibForumTopicManager.Key(getChatId(), getArgumentsStrict().topicId); }

  private boolean available () {
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat == null || !tdlib.isForum(chat.id)) return false;
    return getArgumentsStrict().topicId == 0 ? ForumTopicPolicy.canCreate(tdlib.chatStatus(chat.id), chat.permissions) :
      !metadataFailed && currentInfo != null && ForumTopicPolicy.canEdit(tdlib.chatStatus(chat.id), currentInfo);
  }

  private boolean iconAllowed () { return form != null && ForumTopicPolicy.allowedIcon(form.emoji(), tdlib.hasPremium(), defaultIcons); }

  private void updateAction () {
    if (action == null) return;
    boolean pending = form != null && form.phase() == ForumTopicEditorState.Phase.PENDING;
    action.setText(Lang.getString(pending ? R.string.ForumEditorSending : getArgumentsStrict().topicId == 0 ? R.string.ForumEditorCreate : R.string.Save));
    action.setEnabled(form != null && form.canSubmit(available(), iconAllowed(), defaultsLoaded));
    action.setAlpha(action.isEnabled() || pending ? 1f : .45f);
  }

  private void updateForm () {
    updateAction();
    if (nameInput == null) return;
    boolean editable = form != null && form.editable();
    nameInput.setEnabled(editable);
    searchInput.setEnabled(editable);
    updateSearchClear();
    colorStrip.setVisibility(form != null && form.creating ? View.VISIBLE : View.GONE);
    boolean showPicker = form != null && !form.general;
    if (adapter != null) adapter.setPickerVisible(showPicker);
    pickerHeader.setVisibility(showPicker ? View.VISIBLE : View.GONE);
    retryTopic.setVisibility(metadataFailed && !metadataMissing ? View.VISIBLE : View.GONE);
    boolean unknown = form != null && form.phase() == ForumTopicEditorState.Phase.UNKNOWN;
    checkResult.setVisibility(unknown ? View.VISIBLE : View.GONE);
    checkResult.setEnabled(!checking);
    results.setVisibility(unknown ? View.VISIBLE : View.GONE);
    String message = "";
    if (form == null) {
      message = Lang.getString(metadataMissing ? R.string.ForumEditorUnavailable : metadataFailed ? R.string.ForumEditorLoadError : R.string.ForumEditorLoading);
    } else {
      binding = true;
      if (!nameInput.getText().toString().equals(form.name())) nameInput.setText(form.name());
      binding = false;
      count.setText(Lang.getString(R.string.ForumEditorNameCount, form.submittedName().codePointCount(0, form.submittedName().length())));
      ForumTopicEditorState.Error validation = form.validate(available(), iconAllowed(), defaultsLoaded);
      String nameMessage = validation == ForumTopicEditorState.Error.EMPTY_NAME && nameTouched ? Lang.getString(R.string.ForumEditorEmptyName) :
        validation == ForumTopicEditorState.Error.LONG_NAME ? Lang.getString(R.string.ForumEditorLongName) : "";
      nameError.setText(nameMessage);
      nameError.setVisibility(nameMessage.isEmpty() ? View.GONE : View.VISIBLE);
      if (unknown) message = Lang.getString(R.string.ForumEditorUnknown);
      else if (form.phase() == ForumTopicEditorState.Phase.PENDING) message = Lang.getString(R.string.ForumEditorSending);
      else if (!available()) message = Lang.getString(!form.creating && currentInfo == null && !metadataFailed ? R.string.ForumEditorLoading :
        metadataFailed && !metadataMissing ? R.string.ForumEditorLoadError : form.creating ? R.string.ForumEditorCreateUnavailable : R.string.ForumEditorUnavailable);
      else if (!form.serverError().isEmpty()) message = form.serverError();
      else if (!selectionError.isEmpty()) message = selectionError;
      else if (validation == ForumTopicEditorState.Error.PREMIUM) message = Lang.getString(R.string.ForumEditorPremiumRequired);
      else if (validation == ForumTopicEditorState.Error.ICON_LOADING) message = Lang.getString(R.string.ForumEditorIconLoading);
      else if (validation == ForumTopicEditorState.Error.INVALID_COLOR) message = Lang.getString(R.string.ForumEditorColorInvalid);
      String iconHint = Lang.getString(form.general ? R.string.ForumEditorGeneralHint : R.string.ForumEditorPremiumHint);
      hint.setText(form.creating ? iconHint : Lang.getString(R.string.ForumEditorEditHint,
        iconHint, Lang.getString(R.string.ForumEditorColorLocked)));
      preview.bind(form.emoji(), form.general ? "#" : form.submittedName(), false, false, Lang.getString(R.string.ForumEditorPreview));
      preview.setEnabled(editable && !form.general);
      for (int i = 0; i < colorButtons.size(); i++) {
        TextView choice = colorButtons.get(i);
        choice.setEnabled(editable);
        choice.setSelected(form.color() == ForumTopicPolicy.ICON_COLORS[i]);
        choice.setText(choice.isSelected() ? "◉" : "●");
      }
    }
    error.setText(message);
    error.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    for (View section : sectionButtons) section.setEnabled(editable);
    if (adapter != null && adapter.getItemCount() > 1) adapter.notifyItemRangeChanged(1, adapter.getItemCount() - 1);
    renderPickerStatus();
    restoreFocusIfReady();
  }

  private void submit () {
    nameTouched = true;
    selectionError = "";
    if (form == null || !form.beginSubmit(available(), iconAllowed(), defaultsLoaded)) { updateForm(); return; }
    hideSoftwareKeyboard();
    updateForm();
    final ForumTopicEditorState submitted = form;
    final int mutation = ++mutationGeneration;
    // Store timeouts normally finish first. Its account-reset guard may discard a callback,
    // so the view also bounds waiting without ever repeating the mutation.
    tdlib.ui().postDelayed(() -> {
      if (isDestroyed() || mutation != mutationGeneration || form != submitted || form.phase() != ForumTopicEditorState.Phase.PENDING) return;
      form.failed(408, Lang.getString(R.string.ForumEditorServerError));
      updateForm();
      if (form.phase() == ForumTopicEditorState.Phase.UNKNOWN) reconcile();
    }, 18000);
    if (form.creating) {
      tdlib.topics().actions.create(getChatId(), form.submittedName(), false, new TdApi.ForumTopicIcon(form.color(), form.emoji()), (value, failure) -> {
        if (isDestroyed() || mutation != mutationGeneration || submitted != form) return;
        if (failure != null) form.failed(failure.code, failureText(failure));
        else if (value != null && value.chatId == getChatId() && value.forumTopicId > 0) form.succeeded(value.forumTopicId);
        else form.failed(502, Lang.getString(R.string.ForumEditorServerError));
        updateForm();
        if (form.phase() == ForumTopicEditorState.Phase.UNKNOWN) reconcile();
        finishIfReady();
      });
    } else {
      tdlib.topics().actions.edit(key(), form.submittedName(), form.changesEmoji(), form.emoji(), (value, failure) -> {
        if (isDestroyed() || mutation != mutationGeneration || submitted != form) return;
        if (failure != null) form.failed(failure.code, failureText(failure));
        else form.succeeded(getArgumentsStrict().topicId);
        updateForm(); finishIfReady();
      });
    }
  }

  private String failureText (TdApi.Error failure) {
    // Avoid rendering raw protocol strings; errors are localized and never contain diagnostic data.
    return Lang.getString(failure.code == 400 || failure.code == 403 ? R.string.ForumEditorPermissionError : R.string.ForumEditorServerError);
  }

  private void finishIfReady () {
    if (isDestroyed() || !isFocused() || finishing || form == null || form.phase() != ForumTopicEditorState.Phase.SUCCEEDED) return;
    final NavigationController navigation = context().navigation();
    if (navigation == null || navigation.getCurrentStackItem() != this) return;
    if (context().isNavigationBusy() || navigation.getStack().isLocked()) { scheduleFinish(); return; }
    if (form.creating && returnToTabs && ForumTabsNavigation.isTabForum(this, getChatId())) {
      if (!ForumTabsNavigation.isLiveHost(this, getChatId(), originTabsHost)) originTabsHost = ForumTabsNavigation.findHost(this, getChatId());
      if (originTabsHost != null) {
        finishing = true;
        ForumTabsNavigation.Result result = ForumTabsNavigation.requestAndReturn(this, originTabsHost, form.completedTopicId());
        if (result == ForumTabsNavigation.Result.RETURNED || result == ForumTabsNavigation.Result.QUEUED && navigateBack()) {
          form.claimCompletion();
          return;
        }
        finishing = false;
        if (result == ForumTabsNavigation.Result.RETRY || result == ForumTabsNavigation.Result.QUEUED) scheduleFinish();
        // A live host rejecting a request must not create a duplicate host. A
        // later focus can retry navigation, but never repeat the Create mutation.
        return;
      }
    }
    if (!form.claimCompletion()) return;
    finishing = true;
    if (!form.creating) { navigateBack(); return; }
    tdlib.ui().openChat(this, getChatId(), new TdlibUi.ChatOpenParameters()
      .messageTopic(new TdApi.MessageTopicForum(form.completedTopicId())).keepStack().after(chatId ->
        tdlib.ui().post(() -> removeCompletedEditor(navigation))));
  }

  private void scheduleFinish () {
    if (finishScheduled) return;
    finishScheduled = true;
    tdlib.ui().postDelayed(() -> { finishScheduled = false; finishIfReady(); }, 120);
  }

  boolean canReturnToForumTabs () { return ForumTabsNavigation.canLeaveEditor(form); }

  private void removeCompletedEditor (NavigationController navigation) {
    if (isDestroyed() || navigation == null) return;
    // ChatOpenParameters.after runs at animation readiness, not at completion. The
    // navigation processor still needs the editor as getPrevious() to detach its
    // view. Removing it earlier strands the editor above every subsequent page.
    if (navigation.isAnimating()) {
      tdlib.ui().postDelayed(() -> removeCompletedEditor(navigation), 100);
      return;
    }
    final int index = navigation.getStack().indexOf(this);
    if (index >= 0 && index < navigation.getStackSize() - 1) {
      if (getValue().getParent() != null) navigation.removeChildWrapper(this);
      navigation.getStack().destroy(index);
    }
  }

  private void reconcile () {
    if (isDestroyed() || checking || form == null || form.phase() != ForumTopicEditorState.Phase.UNKNOWN) return;
    if (reconcileSession != null) reconcileSession.close();
    checking = true;
    candidates.clear();
    reconcileText = Lang.getString(R.string.ForumEditorChecking);
    renderResults(); updateForm();
    reconcileSession = tdlib.topics().openList(getChatId(), form.submittedName(), snapshot -> {
      if (isDestroyed() || form.phase() != ForumTopicEditorState.Phase.UNKNOWN) return;
      if (snapshot.error != null) {
        checking = false;
        reconcileText = Lang.getString(R.string.ForumEditorCheckFailed);
      } else if (snapshot.initialized && !snapshot.stale && !snapshot.refreshing && !snapshot.loadingInitial) {
        candidates.clear();
        for (TdApi.ForumTopic topic : snapshot.topics) {
          TdApi.ForumTopicInfo info = topic.info;
          if (info.chatId == getChatId() && form.matchesCandidate(info.name, info.icon.color, info.icon.customEmojiId, info.isOutgoing)) candidates.add(info);
        }
        if (!snapshot.endReached && snapshot.topics.size() < 200 && reconcileSession != null) { reconcileSession.loadMore(); return; }
        checking = false;
        reconcileText = Lang.getString(candidates.isEmpty() ? R.string.ForumEditorNoMatch : R.string.ForumEditorMatches);
      }
      renderResults(); updateForm();
    });
    reconcileSession.refresh();
  }

  private void renderResults () {
    for (int i = 0; i < results.getChildCount(); i++) removeThemeListenerByTarget(results.getChildAt(i));
    results.removeAllViews();
    TextView text = label(14, ColorId.textLight);
    text.setText(reconcileText);
    results.addView(text);
    for (int i = 0; i < candidates.size(); i++) {
      TdApi.ForumTopicInfo candidate = candidates.get(i);
      final int topicId = candidate.forumTopicId;
      String date = Lang.getDate(candidate.creationDate, TimeUnit.SECONDS) + ", " + Lang.time(candidate.creationDate, TimeUnit.SECONDS);
      results.addView(button(Lang.getString(R.string.ForumEditorOpenMatch, i + 1) + "\n" + date, () -> {
        if (form.resolveToExisting(topicId)) finishIfReady();
      }));
    }
  }

  private void loadCatalog () {
    final int generation = ++catalogGeneration;
    pendingCatalog = 3;
    catalogFailed = false;
    pickerError = "";
    pickerLoading = true;
    for (View section : sectionButtons) removeThemeListenerByTarget(section);
    sections.removeAllViews(); sectionButtons.clear();
    addSection(Lang.getString(R.string.ForumEditorDefaults), "", 0);
    tdlib.send(new TdApi.GetForumTopicDefaultIcons(), (icons, failure) -> tdlib.ui().post(() -> {
      if (isDestroyed() || generation != catalogGeneration) return;
      defaultsLoaded = icons != null;
      if (icons != null) defaultIcons = icons.stickers;
      else pickerError = Lang.getString(R.string.ForumEditorEmojiError);
      catalogReturned(icons == null);
      loadPicker(); updateForm();
    }));
    tdlib.send(new TdApi.GetEmojiCategories(new TdApi.EmojiCategoryTypeDefault()), (value, failure) -> tdlib.ui().post(() -> {
      if (isDestroyed() || generation != catalogGeneration) return;
      if (value != null) for (TdApi.EmojiCategory item : value.categories) {
        if (item.source instanceof TdApi.EmojiCategorySourceSearch) {
          addSection(item.name, TextUtils.join(" ", ((TdApi.EmojiCategorySourceSearch) item.source).emojis), 0);
        }
      }
      catalogReturned(value == null);
    }));
    tdlib.send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), (value, failure) -> tdlib.ui().post(() -> {
      if (isDestroyed() || generation != catalogGeneration) return;
      if (value != null) for (TdApi.StickerSetInfo set : value.sets) addSection(set.title, "", set.id);
      catalogReturned(value == null);
    }));
    tdlib.ui().postDelayed(() -> {
      if (isDestroyed() || generation != catalogGeneration || pendingCatalog == 0) return;
      ++catalogGeneration; // Discard late results from this timed-out catalog load.
      pendingCatalog = 0;
      catalogFailed = true;
      if (pickerLoading) showIcons(++pickerGeneration, null);
      renderPickerStatus();
    }, PICKER_TIMEOUT_MS);
    renderPickerStatus();
  }

  private void catalogReturned (boolean failed) {
    pendingCatalog--;
    catalogFailed |= failed;
    renderPickerStatus();
  }

  private void addSection (String title, String emojiQuery, long setId) {
    TextView view = button(title, () -> {
      binding = true; searchInput.setText(""); binding = false;
      query = ""; category = emojiQuery; selectedSet = setId;
      hideSoftwareKeyboard(); loadPicker();
    });
    view.setMaxLines(1);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setMaxWidth(Screen.dp(160));
    view.setTag(new String[] {emojiQuery, Long.toString(setId)});
    sectionButtons.add(view);
    sections.addView(view);
    updateSections();
  }

  private void updateSections () {
    for (View view : sectionButtons) {
      String[] target = (String[]) view.getTag();
      boolean selected = query.isEmpty() && category.equals(target[0]) && Long.toString(selectedSet).equals(target[1]);
      view.setSelected(selected);
      view.setAlpha(selected ? 1f : .65f);
      view.setEnabled(form != null && form.editable());
      view.setContentDescription(selected ? Lang.getString(R.string.ForumEditorSelected, ((TextView) view).getText()) : ((TextView) view).getText());
    }
  }

  private void loadPicker () {
    final int ticket = ++pickerGeneration;
    pickerLoading = true;
    adapter.replaceIcons(Collections.emptyList());
    updateSections(); renderPickerStatus();
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed() && ticket == pickerGeneration && pickerLoading) showIcons(++pickerGeneration, null);
    }, PICKER_TIMEOUT_MS);
    if (selectedSet != 0 && query.isEmpty()) {
      tdlib.send(new TdApi.GetStickerSet(selectedSet), (set, failure) -> tdlib.ui().post(() -> showIcons(ticket, set != null ? set.stickers : null)));
    } else if (!category.isEmpty()) {
      searchEmoji(ticket, category);
    } else if (!query.trim().isEmpty()) {
      final String text = query.trim();
      tdlib.send(new TdApi.SearchEmojis(text, U.getInputLanguages()), (keywords, failure) -> tdlib.ui().post(() -> {
        if (isDestroyed() || ticket != pickerGeneration) return;
        if (failure != null) { showIcons(ticket, null); return; }
        String[] emoji = Td.findUniqueEmojis(keywords.emojiKeywords);
        // Literal emoji input also works when it has no keyword entry in the current language.
        searchEmoji(ticket, emoji.length > 0 ? TextUtils.join(" ", emoji) : text);
      }));
    } else {
      showIcons(ticket, defaultsLoaded ? defaultIcons : null);
    }
  }

  private void searchEmoji (int ticket, String emojiQuery) {
    tdlib.send(new TdApi.SearchStickers(new TdApi.StickerTypeCustomEmoji(), emojiQuery, null, U.getInputLanguages(), 0, 200),
      (stickers, failure) -> tdlib.ui().post(() -> showIcons(ticket, stickers != null ? stickers.stickers : null)));
  }

  private void showIcons (int ticket, TdApi.Sticker[] stickers) {
    if (isDestroyed() || ticket != pickerGeneration) return;
    pickerLoading = false;
    pickerError = stickers == null ? Lang.getString(R.string.ForumEditorEmojiError) : "";
    LinkedHashMap<Long, TdApi.Sticker> unique = new LinkedHashMap<>();
    if (stickers != null) {
      for (TdApi.Sticker sticker : stickers) if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji) {
        unique.put(((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId, sticker);
      }
    }
    adapter.replaceIcons(new ArrayList<>(unique.values()));
    renderPickerStatus();
  }

  private void renderPickerStatus () {
    if (pickerStatus == null) return;
    String text = pickerLoading ? Lang.getString(R.string.ForumEditorEmojiLoading) : !pickerError.isEmpty() ? pickerError :
      catalogFailed ? Lang.getString(R.string.ForumEditorEmojiPartial) :
      visibleIcons.isEmpty() ? Lang.getString(R.string.ForumEditorEmojiEmpty) : "";
    pickerStatus.setText(text);
    pickerStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    retryPicker.setVisibility(!pickerLoading && (!pickerError.isEmpty() || catalogFailed) ? View.VISIBLE : View.GONE);
  }

  private void chooseIcon (long emojiId) {
    if (form == null || !form.editable() || form.general) return;
    if (!ForumTopicPolicy.allowedIcon(emojiId, tdlib.hasPremium(), defaultIcons)) {
      selectionError = Lang.getString(defaultsLoaded ? R.string.ForumEditorPremiumRequired : R.string.ForumEditorIconLoading);
      updateForm(); return;
    }
    if (form.setEmoji(emojiId)) { selectionError = ""; updateForm(); }
  }

  @Override public void onFocus () {
    super.onFocus();
    updateForm(); finishIfReady();
    if (form != null && form.phase() == ForumTopicEditorState.Phase.UNKNOWN && reconcileSession == null) reconcile();
  }

  private void restoreFocusIfReady () {
    if (isFocused() && restoredFocus != 0 && nameInput != null) {
      EditText target = restoredFocus == 2 ? searchInput : nameInput;
      if (!target.isEnabled()) return;
      target.requestFocus();
      if (restoredSelection >= 0) target.setSelection(Math.min(restoredSelection, target.length()));
      restoredFocus = 0;
    }
  }

  @Override public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) { tdlib.ui().post(this::refreshAccess); }
  @Override public void onSupergroupUpdated (TdApi.Supergroup supergroup) { tdlib.ui().post(this::refreshAccess); }
  @Override public void onMyUserUpdated (TdApi.User user) { tdlib.ui().post(this::refreshAccess); }
  private void refreshAccess () { if (!isDestroyed()) updateForm(); }

  @Override public boolean supportsBottomInset () { return true; }
  @Override protected void onBottomInsetChanged (int inset, int withoutIme, boolean isIme) {
    super.onBottomInsetChanged(inset, withoutIme, isIme);
    if (recycler != null) {
      recycler.setPadding(Screen.dp(12), Screen.dp(12), Screen.dp(12), Screen.dp(12) + inset);
      if (nameInput != null && nameInput.hasFocus()) nameInput.requestRectangleOnScreen(new android.graphics.Rect(0, 0, nameInput.getWidth(), nameInput.getHeight()));
      if (searchInput != null && searchInput.hasFocus()) searchInput.requestRectangleOnScreen(new android.graphics.Rect(0, 0, searchInput.getWidth(), searchInput.getHeight()));
    }
  }

  @Override public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (super.performOnBackPressed(fromTop, commit)) return true;
    if (finishing || form == null || !form.hasChanges()) return false;
    if (!commit) return true;
    if (form.phase() == ForumTopicEditorState.Phase.PENDING) {
      selectionError = Lang.getString(R.string.ForumEditorSendingBack); updateForm(); return true;
    }
    boolean unknown = form.phase() == ForumTopicEditorState.Phase.UNKNOWN;
    showAlert(new AlertDialog.Builder(context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumEditorDiscardTitle))
      .setMessage(Lang.getString(unknown ? R.string.ForumEditorUnknownLeave : R.string.ForumEditorDiscardText))
      .setNegativeButton(Lang.getString(R.string.ForumEditorKeepEditing), null)
      .setPositiveButton(Lang.getString(unknown ? R.string.ForumEditorLeave : R.string.ForumEditorDiscard), (dialog, which) -> { finishing = true; navigateBack(); }));
    return true;
  }

  @Override public boolean canSlideBackFrom (NavigationController navigation, float x, float y) {
    return form == null || !form.hasChanges();
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    out.putInt(prefix + "editor_account", tdlib.id());
    out.putLong(prefix + "editor_chat", getChatId());
    out.putInt(prefix + "editor_topic", getArgumentsStrict().topicId);
    out.putBoolean(prefix + "editor_tabs_origin", returnToTabs);
    if (form != null) out.putSerializable(prefix + "editor_form", form.snapshot());
    out.putString(prefix + "editor_query", query);
    out.putString(prefix + "editor_category", category);
    out.putLong(prefix + "editor_set", selectedSet);
    out.putBoolean(prefix + "editor_touched", nameTouched);
    if (layoutManager != null) out.putParcelable(prefix + "editor_scroll", layoutManager.onSaveInstanceState());
    int focus = searchInput != null && searchInput.hasFocus() ? 2 : nameInput != null && nameInput.hasFocus() ? 1 : 0;
    out.putInt(prefix + "editor_focus", focus);
    if (focus != 0) out.putInt(prefix + "editor_selection", (focus == 2 ? searchInput : nameInput).getSelectionStart());
    return true;
  }

  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    long chatId = in.getLong(prefix + "editor_chat");
    int topicId = in.getInt(prefix + "editor_topic", -1);
    if (chatId == 0 || topicId < 0 || in.getInt(prefix + "editor_account", -1) != tdlib.id()) return false;
    super.restoreInstanceState(in, prefix);
    setArguments(new Arguments(chatId, topicId));
    returnToTabs = topicId == 0 && in.getBoolean(prefix + "editor_tabs_origin", false);
    originTabsHost = null; // Resolve only within the restored account/chat stack.
    Object saved = in.getSerializable(prefix + "editor_form");
    if (saved instanceof ForumTopicEditorState.Snapshot) {
      ForumTopicEditorState.Snapshot snapshot = (ForumTopicEditorState.Snapshot) saved;
      if (snapshot.creating != (topicId == 0)) return false;
      form = ForumTopicEditorState.restore(snapshot);
      finishing = snapshot.completionConsumed;
      if (!snapshot.creating && snapshot.phase == ForumTopicEditorState.Phase.PENDING) selectionError = Lang.getString(R.string.ForumEditorRestoredEdit);
    }
    query = in.getString(prefix + "editor_query", "");
    category = in.getString(prefix + "editor_category", "");
    selectedSet = in.getLong(prefix + "editor_set");
    nameTouched = in.getBoolean(prefix + "editor_touched");
    restoredScroll = in.getParcelable(prefix + "editor_scroll");
    restoredFocus = in.getInt(prefix + "editor_focus");
    restoredSelection = in.getInt(prefix + "editor_selection", -1);
    return true;
  }

  @Override public void onThemeColorsChanged (boolean temporary, ColorState state) {
    super.onThemeColorsChanged(temporary, state);
    if (recycler != null) recycler.invalidate();
    for (IconCell cell : cells) cell.invalidate();
  }

  @Override public void destroy () {
    if (topicSubscription != null) topicSubscription.close();
    if (reconcileSession != null) reconcileSession.close();
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      tdlib.cache().removeMyUserListener(this);
      if (supergroupId != 0) tdlib.cache().unsubscribeFromSupergroupUpdates(supergroupId, this);
    }
    ++pickerGeneration; ++catalogGeneration;
    for (IconCell cell : cells) cell.destroyCell();
    cells.clear();
    super.destroy();
  }

  private final class EditorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final LinkedHashMap<Long, Long> stableIds = new LinkedHashMap<>();
    private boolean pickerVisible = form != null && !form.general;
    EditorAdapter () { setHasStableIds(true); }
    @Override public int getItemCount () { return pickerVisible ? visibleIcons.size() + 2 : 1; }
    void setPickerVisible (boolean visible) {
      if (pickerVisible == visible) return;
      pickerVisible = visible;
      // The form header is always row 0; the ordinary icon and custom icons follow it.
      int count = visibleIcons.size() + 1;
      if (visible) notifyItemRangeInserted(1, count);
      else notifyItemRangeRemoved(1, count);
    }
    void replaceIcons (List<TdApi.Sticker> icons) {
      int removedCount = visibleIcons.size();
      visibleIcons.clear();
      if (pickerVisible && removedCount > 0) notifyItemRangeRemoved(2, removedCount);
      visibleIcons.addAll(icons);
      if (pickerVisible && !visibleIcons.isEmpty()) notifyItemRangeInserted(2, visibleIcons.size());
    }
    @Override public int getItemViewType (int position) { return position == 0 ? 0 : 1; }
    @Override public long getItemId (int position) {
      if (position < 2) return position;
      long emojiId = ((TdApi.StickerFullTypeCustomEmoji) visibleIcons.get(position - 2).fullType).customEmojiId;
      // Protocol identifiers are opaque; do not reserve values from their namespace for rows.
      Long stable = stableIds.get(emojiId);
      if (stable == null) stableIds.put(emojiId, stable = stableIds.size() + 2L);
      return stable;
    }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int type) {
      if (type == 0) {
        RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(content) { };
        holder.setIsRecyclable(false);
        return holder;
      }
      IconCell cell = new IconCell(parent.getContext());
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56)));
      return new RecyclerView.ViewHolder(cell) { };
    }
    @Override public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      if (position == 0) return;
      IconCell cell = (IconCell) holder.itemView;
      long emojiId = 0;
      String description = Lang.getString(R.string.ForumEditorRegular);
      if (position > 1) {
        TdApi.Sticker sticker = visibleIcons.get(position - 2);
        emojiId = ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId;
        description = TextUtils.isEmpty(sticker.emoji) ? Lang.getString(R.string.ForumEditorCustomEmoji) : sticker.emoji;
      }
      boolean locked = !ForumTopicPolicy.allowedIcon(emojiId, tdlib.hasPremium(), defaultIcons);
      cell.bind(emojiId, form.submittedName(), emojiId == form.emoji(), locked, description);
      cell.setEnabled(form.editable());
      final long selected = emojiId;
      cell.setOnClickListener(v -> chooseIcon(selected));
    }
    @Override public void onViewRecycled (@NonNull RecyclerView.ViewHolder holder) {
      if (holder.itemView instanceof IconCell) ((IconCell) holder.itemView).clearEmoji();
    }
  }

  /** Uses the same custom-emoji Text/receiver pipeline as topic rows, with selector semantics. */
  private final class IconCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tail = new Path();
    private final ComplexReceiver receiver = new ComplexReceiver(this);
    private Text emoji;
    private long emojiId;
    private String letter = "";
    private boolean locked;

    IconCell (Context context) {
      super(context);
      cells.add(this);
      setFocusable(true);
      setMinimumHeight(Screen.dp(48));
      setBackground(Theme.fillingSelector());
    }

    void bind (long id, String name, boolean selected, boolean locked, String description) {
      if (emojiId != id || id != 0 && emoji == null) {
        clearEmoji(); emojiId = id;
        if (id != 0) {
          TdApi.FormattedText text = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(id))});
          emoji = new Text.Builder(tdlib, text, null, Screen.dp(48), Paints.robotoStyleProvider(32), (TextColorSetThemed) () -> ColorId.icon, (value, media) -> {
            if (value == emoji) { value.requestMedia(receiver); invalidate(); }
          }).singleLine().build();
          emoji.requestMedia(receiver);
        }
      }
      letter = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1));
      this.locked = locked;
      setSelected(selected);
      setContentDescription(selected ? Lang.getString(R.string.ForumEditorSelected, description) :
        locked ? Lang.getString(R.string.ForumEditorLocked, description) : description);
      invalidate();
    }

    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      if (isSelected()) {
        paint.setColor(Theme.getColor(ColorId.textLink));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2));
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(10), Screen.dp(3), Screen.dp(3), getWidth()-Screen.dp(3), getHeight()-Screen.dp(3), paint);
        paint.setStyle(Paint.Style.FILL);
      }
      if (emoji != null) emoji.draw(canvas, (int) (cx - emoji.getWidth()/2f), (int) (cy - emoji.getHeight()/2f), null, locked ? .55f : 1f, receiver);
      else {
        float r = Screen.dp(18);
        paint.setColor(0xff000000 | (form != null ? form.color() : ForumTopicPolicy.ICON_COLORS[0]));
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(11), cx-r, cy-r, cx+r, cy+r-Screen.dp(3), paint);
        tail.reset(); tail.moveTo(cx-r+Screen.dp(3), cy+r-Screen.dp(8));
        tail.lineTo(cx-r+Screen.dp(3), cy+r+Screen.dp(2)); tail.lineTo(cx-r+Screen.dp(15), cy+r-Screen.dp(4)); tail.close();
        canvas.drawPath(tail, paint);
        paint.setColor(Theme.getColor(ColorId.white)); paint.setTextSize(Screen.dp(20));
        paint.setTypeface(Fonts.getRobotoMedium()); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(letter, cx, cy-Screen.dp(2)-(paint.ascent()+paint.descent())/2, paint);
      }
      if (locked) {
        paint.setColor(Theme.getColor(ColorId.iconLight));
        float x = getWidth()-Screen.dp(12), y = getHeight()-Screen.dp(12);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(1.5f));
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(3), x-Screen.dp(3), y-Screen.dp(7), x+Screen.dp(3), y, paint);
        paint.setStyle(Paint.Style.FILL);
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(2), x-Screen.dp(5), y-Screen.dp(2), x+Screen.dp(5), y+Screen.dp(5), paint);
      }
    }

    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName("android.widget.RadioButton");
      info.setCheckable(true); info.setChecked(isSelected());
    }
    void clearEmoji () { if (emoji != null) emoji.performDestroy(); emoji = null; emojiId = 0; receiver.clear(); }
    void destroyCell () { clearEmoji(); receiver.performDestroy(); }
    @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); receiver.attach(); }
    @Override protected void onDetachedFromWindow () { receiver.detach(); super.onDetachedFromWindow(); }
  }
}
