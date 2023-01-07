package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.FlowListAnimator;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatPosition;

public class SelectChatsController extends RecyclerViewController<SelectChatsController.Arguments> implements View.OnClickListener, ChatListListener {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_SELECT_CHATS, MODE_FOLDER_INCLUDE_CHATS, MODE_FOLDER_EXCLUDE_CHATS})
  public @interface Mode {
  }

  public static final int MODE_SELECT_CHATS = 0;
  public static final int MODE_FOLDER_INCLUDE_CHATS = 1;
  public static final int MODE_FOLDER_EXCLUDE_CHATS = 2;

  public static class Arguments {
    private final @Mode int mode;
    private final int chatFilterId;
    private final @Nullable TdApi.ChatFilter chatFilter;
    private final @Nullable Delegate delegate;
    private final Set<Long> selectedChatIds;
    private final Set<Integer> selectedChatTypes;

    private Arguments (@Mode int mode, @Nullable Delegate delegate, int chatFilterId, @Nullable TdApi.ChatFilter chatFilter, Set<Long> selectedChatIds, Set<Integer> selectedChatTypes) {
      this.mode = mode;
      this.delegate = delegate;
      this.chatFilter = chatFilter;
      this.chatFilterId = chatFilterId;
      this.selectedChatIds = selectedChatIds;
      this.selectedChatTypes = selectedChatTypes;
    }

    public static Arguments includedChats (int chatFilterId, TdApi.ChatFilter chatFilter) {
      return includedChats(null, chatFilterId, chatFilter);
    }

    public static Arguments includedChats (@Nullable Delegate delegate, int chatFilterId, TdApi.ChatFilter chatFilter) {
      Set<Long> selectedChatIds = unmodifiableLinkedHashSetOf(chatFilter.pinnedChatIds, chatFilter.includedChatIds);
      Set<Integer> selectedChatTypes = unmodifiableTreeSetOf(TD.includedChatTypes(chatFilter));
      return new Arguments(MODE_FOLDER_INCLUDE_CHATS, delegate, chatFilterId, chatFilter, selectedChatIds, selectedChatTypes);
    }

    public static Arguments excludedChats (@Nullable Delegate delegate, int chatFilterId, TdApi.ChatFilter chatFilter) {
      Set<Long> selectedChatIds = unmodifiableLinkedHashSetOf(chatFilter.excludedChatIds);
      Set<Integer> selectedChatTypes = unmodifiableTreeSetOf(TD.excludedChatTypes(chatFilter));
      return new Arguments(MODE_FOLDER_EXCLUDE_CHATS, delegate, chatFilterId, chatFilter, selectedChatIds, selectedChatTypes);
    }

    private static Set<Integer> unmodifiableTreeSetOf (int[] array) {
      if (array.length == 0)
        return Collections.emptySet();
      if (array.length == 1)
        return Collections.singleton(array[0]);
      Set<Integer> set = new TreeSet<>();
      for (int value : array) {
        set.add(value);
      }
      return Collections.unmodifiableSet(set);
    }

    private static Set<Long> unmodifiableLinkedHashSetOf (long[]... arrays) {
      int count = 0;
      for (long[] array : arrays) {
        count += array.length;
      }
      LinkedHashSet<Long> set = new LinkedHashSet<>(count);
      for (long[] array : arrays) {
        for (long value : array) {
          set.add(value);
        }
      }
      return Collections.unmodifiableSet(set);
    }
  }

  private @Mode int mode;
  private @Nullable Delegate delegate;
  private SettingsAdapter adapter;
  private TdlibChatListSlice chatListSlice;
  private boolean loadingMore, chatListInitialized;

  private final @IdRes int chatsHeaderId = ViewCompat.generateViewId();
  private final @IdRes int chatsFooterId = ViewCompat.generateViewId();

  private Set<Long> selectedChatIds = Collections.emptySet();
  private Set<Integer> selectedChatTypes = Collections.emptySet();

  public SelectChatsController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
    setNeedSearch();
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    mode = args.mode;
    delegate = args.delegate;
    selectedChatIds = new LinkedHashSet<>(args.selectedChatIds);
    selectedChatTypes = new TreeSet<>(args.selectedChatTypes);
  }

  @Override
  public int getId () {
    return R.id.controller_selectChats;
  }

  @Override
  public CharSequence getName () {
    Arguments arguments = getArgumentsStrict();
    switch (arguments.mode) {
      case MODE_FOLDER_INCLUDE_CHATS:
        return Lang.getString(R.string.IncludeChats);
      case MODE_FOLDER_EXCLUDE_CHATS:
        return Lang.getString(R.string.ExcludeChats);
      case MODE_SELECT_CHATS:
        return Lang.getString(R.string.SelectChats);
      default:
        throw new IllegalArgumentException("mode=" + arguments.mode);
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !chatListInitialized;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    Arguments arguments = getArgumentsStrict();
    adapter = new Adapter(this);

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CUSTOM, R.id.input));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (arguments.mode == MODE_FOLDER_INCLUDE_CHATS || arguments.mode == MODE_FOLDER_EXCLUDE_CHATS) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      if (mode == MODE_FOLDER_INCLUDE_CHATS) {
        CharSequence description = Lang.pluralBold(R.string.IncludeChatsInfo, tdlib.chatFilterChosenChatCountMax());
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, description));
      } else if (mode == MODE_FOLDER_EXCLUDE_CHATS) {
        CharSequence description = Lang.pluralBold(R.string.ExcludeChatsInfo, tdlib.chatFilterChosenChatCountMax());
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, description));
      }

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatTypes));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (arguments.mode == MODE_FOLDER_INCLUDE_CHATS) {
        for (int chatType : TD.CHAT_TYPES_TO_INCLUDE) {
          items.add(chatTypeItem(chatType));
        }
      }
      if (arguments.mode == MODE_FOLDER_EXCLUDE_CHATS) {
        for (int chatType : TD.CHAT_TYPES_TO_EXCLUDE) {
          items.add(chatTypeItem(chatType));
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, chatsHeaderId));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, chatsFooterId));
    }

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    int initialChunkSize = Screen.calculateLoadingItems(Screen.dp(72f), 5) + 5;
    int chunkSize = Screen.calculateLoadingItems(Screen.dp(72f), 25);
    loadingMore = true;
    chatListSlice = new TdlibChatListSlice(tdlib, ChatPosition.CHAT_LIST_MAIN, null, true);


    chatListSlice.initializeList(this, this::processChats, initialChunkSize, () -> {
      runOnUiThreadOptional(() -> {
        chatListInitialized = true;
        executeScheduledAnimation();
      });
    });

    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy > 0 && !loadingMore && !inSearchMode() && chatListSlice.canLoad()) {
          int lastVisiblePosition = findLastVisiblePosition();
          if (lastVisiblePosition == adapter.getItemCount() - 1) {
            chatListSlice.loadMore(chunkSize, /* after */ null);
          }
        }
      }
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    chatListSlice.unsubscribeFromUpdates(this);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.chat) {
      ListItem item = (ListItem) v.getTag();
      long chatId = item.getLongId();
      toggleChatSelection(chatId, v, /* removeOnly */ false);
    } else if (ArrayUtils.contains(TD.CHAT_TYPES, id)) {
      toggleChatTypeSelection(id, v, /* removeOnly */ false);
    }
  }

  @Override
  protected void onDoneClick () {
    if (inSearchMode()) {
      closeSearchMode(null);
    } else {
      saveChanges(this::navigateBack);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private void updateDoneButton () {
    setDoneVisible(hasChanges(), true);
  }

  private void processChats (List<TdlibChatListSlice.Entry> entries) {
    if (entries.isEmpty()) {
      return;
    }
    List<TGFoundChat> chats = new ArrayList<>(entries.size());
    for (TdlibChatListSlice.Entry entry : entries) {
      chats.add(foundChat(entry));
    }
    runOnUiThreadOptional(() -> {
      loadingMore = false;
      displayChats(chats);
    });
  }

  private void displayChats (List<TGFoundChat> chats) {
    if (chats.isEmpty()) {
      return;
    }
    List<ListItem> chatItems = new ArrayList<>(chats.size() * 2);
    for (TGFoundChat chat : chats) {
      chatItems.add(chatItem(chat));
    }
    adapter.addItems(indexOfLastChat() + 1, chatItems.toArray(new ListItem[0]));
  }

  private ListItem chatTypeItem (@IdRes int id) {
    return new ListItem(ListItem.TYPE_CHAT_BETTER, id, TD.chatTypeIcon24(id), TD.chatTypeName(id)).setIntValue(TD.chatTypeColor(id));
  }

  private ListItem chatItem (TGFoundChat foundChat) {
    ListItem item = new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat);
    item.setLongId(foundChat.getChatId());
    item.setData(foundChat);
    return item;
  }

  private TGFoundChat foundChat (TdlibChatListSlice.Entry entry) {
    return foundChat(entry.chatList, entry.chat);
  }

  private TGFoundChat foundChat (TdApi.ChatList chatList, TdApi.Chat chat) {
    TGFoundChat foundChat = new TGFoundChat(tdlib, chatList, chat, true, null);
    foundChat.setNoUnread();
    return foundChat;
  }

  private int indexOfFistChat () {
    return adapter.indexOfViewById(chatsHeaderId) + 1;
  }

  private int indexOfLastChat () {
    return adapter.indexOfViewById(chatsFooterId) - 1;
  }

  private boolean hasChanges () {
    Arguments arguments = getArgumentsStrict();
    return !selectedChatTypes.equals(arguments.selectedChatTypes) || !selectedChatIds.equals(arguments.selectedChatIds);
  }

  private void saveChanges (@Nullable Runnable after) {
    if (delegate != null) {
      delegate.onSelectedChatsChanged(mode, selectedChatIds, selectedChatTypes);
      if (after != null) {
        after.run();
      }
    } else {
      Arguments arguments = getArgumentsStrict();
      if (arguments.chatFilter != null && (mode == MODE_FOLDER_INCLUDE_CHATS || mode == MODE_FOLDER_EXCLUDE_CHATS)) {
        int chatFilterId = arguments.chatFilterId;
        TdApi.ChatFilter chatFilter = TD.copyOf(arguments.chatFilter);
        if (mode == MODE_FOLDER_INCLUDE_CHATS) {
          TD.updateIncludedChats(chatFilter, selectedChatIds);
          TD.updateIncludedChatTypes(chatFilter, selectedChatTypes);
        } else {
          TD.updateExcludedChats(chatFilter, selectedChatIds);
          TD.updateExcludedChatTypes(chatFilter, selectedChatTypes);
        }
        tdlib.send(new TdApi.EditChatFilter(chatFilterId, chatFilter), tdlib.okHandler(TdApi.ChatFilterInfo.class, after != null ? () -> {
          if (!isDestroyed()) {
            after.run();
          }
        } : null));
      }
    }
  }

  private boolean toggleChatSelection (long chatId, @Nullable View view, boolean removeOnly) {
    boolean selected = selectedChatIds.contains(chatId);
    if (!selected && removeOnly) {
      return false;
    }
    if (selected) {
      selectedChatIds.remove(chatId);
    } else {
      selectedChatIds.add(chatId);
    }
    updateDoneButton();
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(!selected, true);
    } else {
      int index = adapter.indexOfViewByLongId(chatId);
      if (index != RecyclerView.NO_POSITION) {
        adapter.updateSimpleItemByPosition(index);
      }
    }
    adapter.updateSimpleItemById(R.id.input);
    return !selected;
  }

  private void toggleChatTypeSelection (@IdRes int chatType, @Nullable View view, boolean removeOnly) {
    boolean selected = selectedChatTypes.contains(chatType);
    if (!selected && removeOnly) {
      return;
    }
    if (selected) {
      selectedChatTypes.remove(chatType);
    } else {
      selectedChatTypes.add(chatType);
    }
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(!selected, true);
    } else {
      adapter.updateSimpleItemById(chatType);
    }
    updateDoneButton();
    adapter.updateSimpleItemById(R.id.input);
  }

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    boolean isChatSelected = toggleChatSelection(chat.getChatId(), null, /* removeOnly */ false);
    if (view instanceof BetterChatView) {
      ((BetterChatView) view).setIsChecked(isChatSelected, true);
    } else {
      closeSearchMode(null);
    }
    return true;
  }

  @Override
  protected void modifyFoundChat (TGFoundChat chat) {
    chat.setNoUnread();
  }

  @Override
  protected void modifyFoundChatView (ListItem item, int position, BetterChatView chatView) {
    chatView.setAllowMaximizePreview(false);
    chatView.setIsChecked(selectedChatIds.contains(item.getLongId()), false);
  }


  @Override
  public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      TGFoundChat foundChat = foundChat(chatList.chatList(), chat);
      adapter.addItems(indexOfFistChat() + atIndex, chatItem(foundChat));
    });
  }

  @Override
  public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      adapter.removeItem(indexOfFistChat() + fromIndex);
    });
  }

  @Override
  public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      int firstChatIndex = indexOfFistChat();
      adapter.moveItem(firstChatIndex + fromIndex, firstChatIndex + toIndex);
    });
  }

  public interface Delegate {
    void onSelectedChatsChanged (@Mode int mode, Set<Long> chatIds, Set<Integer> chatTypes);
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }

    @Override
    protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
      int spacing = Screen.dp(8f);
      ChipGroup chipGroup = new ChipGroup(parent.getContext());
      chipGroup.setSpacing(spacing);
      chipGroup.setPadding(spacing, spacing, spacing, spacing);
      chipGroup.setDelegate(new ChipGroup.Delegate() {
        @Override
        public void onCrossClick (Chip chip) {
          if (chip.type == Chip.TYPE_CHAT_TYPE) {
            int chatType = (int) chip.id;
            toggleChatTypeSelection(chatType, null, /* removeOnly */ true);
          } else if (chip.type == Chip.TYPE_CHAT) {
            long chatId = chip.id;
            toggleChatSelection(chatId, null, /* removeOnly */ true);
          } else {
            throw new UnsupportedOperationException();
          }
        }
      });

      SettingHolder holder = new SettingHolder(chipGroup);
      holder.setIsRecyclable(false);
      return holder;
    }

    @Override
    protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
      ChipGroup chipGroup = (ChipGroup) view;
      List<Chip> chips = new ArrayList<>(selectedChatIds.size() + selectedChatTypes.size());
      for (int selectedChatType : selectedChatTypes) {
        chips.add(chipGroup.chatType(selectedChatType));
      }
      for (long selectedChatId : selectedChatIds) {
        chips.add(chipGroup.chat(tdlib, selectedChatId));
      }
      chipGroup.setChips(chips);
    }

    @Override
    protected void setChatData (ListItem item, int position, BetterChatView chatView) {
      if (item.getId() == R.id.chat) {
        TGFoundChat foundChat = (TGFoundChat) item.getData();
        chatView.setChat(foundChat);
        chatView.setNoSubtitle(false);
        chatView.setIsChecked(selectedChatIds.contains(item.getLongId()), chatView.isLaidOut());
        chatView.setAllowMaximizePreview(false);
      } else if (ArrayUtils.contains(TD.CHAT_TYPES, item.getId())) {
        float avatarRadius = ChatView.getAvatarSizeDp(Settings.CHAT_MODE_2LINE) / 2f;
        AvatarPlaceholder avatarPlaceholder = new AvatarPlaceholder(avatarRadius, new AvatarPlaceholder.Metadata(item.getIntValue(), item.getIconResource()), chatView);
        chatView.setTitle(item.getString());
        chatView.setSubtitle(null);
        chatView.setNoSubtitle(true);
        chatView.setAvatar((ImageFile) null, avatarPlaceholder);
        chatView.setIsChecked(selectedChatTypes.contains(item.getId()), chatView.isLaidOut());
        chatView.clearPreviewChat();
      } else {
        throw new IllegalArgumentException();
      }
    }
  }
}

class Chip extends Drawable implements FlowListAnimator.Measurable, Drawable.Callback, TextColorSet {
  public static final int TYPE_CHAT = 1;
  public static final int TYPE_CHAT_TYPE = 2;

  private static final float AVATAR_RADIUS = 12f;

  private static final int[] STATE_DEFAULT = new int[] {android.R.attr.state_enabled};
  private static final int[] STATE_PRESSED = new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed};

  public final long id;
  public final int type;
  private final Text label;
  private final AvatarPlaceholder avatarPlaceholder;
  private final @Nullable ImageFile avatarFile;
  private final ComplexReceiver complexReceiver;
  private final DrawableProvider drawableProvider;

  private Drawable crossIcon;
  private Drawable crossIconRipple;

  private int alpha = 0xFF;

  public Chip (DrawableProvider drawableProvider, ComplexReceiver complexReceiver, Tdlib tdlib, long chatId) {
    this.id = chatId;
    this.type = TYPE_CHAT;
    this.label = buildLabel(tdlib.chatTitle(chatId));
    if (tdlib.isSelfChat(chatId)) {
      this.avatarFile = null;
      this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(R.id.theme_color_avatarSavedMessages, R.drawable.baseline_bookmark_16), drawableProvider);
    } else if (tdlib.isRepliesChat(chatId)) {
      this.avatarFile = null;
      this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(R.id.theme_color_avatarReplies, R.drawable.baseline_reply_16), drawableProvider);
    } else {
      this.avatarFile = tdlib.chatAvatar(chatId, Screen.dp(AVATAR_RADIUS * 2));
      this.avatarPlaceholder = tdlib.chatPlaceholder(chatId, tdlib.chat(chatId), true, AVATAR_RADIUS, drawableProvider);
    }
    this.drawableProvider = drawableProvider;
    this.complexReceiver = complexReceiver;
    initCrossDrawable();
  }

  public Chip (DrawableProvider drawableProvider, @IdRes int chatType) {
    this.id = chatType;
    this.type = TYPE_CHAT_TYPE;
    this.label = buildLabel(Lang.getString(TD.chatTypeName(chatType)));
    this.avatarFile = null;
    this.avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, new AvatarPlaceholder.Metadata(TD.chatTypeColor(chatType), TD.chatTypeIcon16(chatType)), drawableProvider);
    this.drawableProvider = drawableProvider;
    this.complexReceiver = null;
    initCrossDrawable();
  }

  public boolean inCrossIconTouchBounds (float x, float y) {
    Rect bounds = getBounds();
    return bounds.contains(Math.round(x), Math.round(y)) && x >= bounds.right - Screen.dp(34f);
  }

  public void setCrossIconPressed (boolean pressed) {
    crossIconRipple.setState(pressed ? STATE_PRESSED : STATE_DEFAULT);
  }

  private Text buildLabel (String text) {
    int maxWidth = (Screen.currentWidth() - Screen.dp(8f) * 3) / 2 - getIntrinsicWidth(/* labelWidth */ 0); // (´・ᴗ・ ` )
    return new Text.Builder(text, maxWidth, Paints.robotoStyleProvider(14f), this)
      .noClickable()
      .ignoreNewLines()
      .ignoreContinuousNewLines()
      .clipTextArea()
      .singleLine()
      .allBold()
      .build();
  }

  private void initCrossDrawable () {
    crossIcon = drawableProvider.getSparseDrawable(R.drawable.baseline_close_18, ThemeColorId.NONE);
    ShapeDrawable mask = new ShapeDrawable(new OvalShape());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      mask.setTint(Color.WHITE);
      crossIconRipple = new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE_COLOR), /* content */ null, mask);
    } else {
      mask.getPaint().setColor(Theme.RIPPLE_COLOR);
      crossIconRipple = Drawables.getColorSelector(null, mask);
    }
    crossIconRipple.setCallback(this);
    crossIconRipple.setState(STATE_DEFAULT);
  }

  public void requestFiles () {
    if (complexReceiver != null && avatarFile != null) {
      ImageReceiver imageReceiver = complexReceiver.getImageReceiver(id);
      imageReceiver.setRadius(Screen.dp(AVATAR_RADIUS));
      imageReceiver.requestFile(avatarFile);
    }
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Chip that = (Chip) o;
    return id == that.id && type == that.type;
  }

  @Override
  public int hashCode () {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + type;
    return result;
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(32f);
  }

  @Override
  public int getIntrinsicWidth () {
    int width = getIntrinsicWidth(label.getWidth());
    int minWidth = Screen.dp(48f);
    return Math.max(width, minWidth);
  }

  private static int getIntrinsicWidth (int labelWidth) {
    return Screen.dp(4f + AVATAR_RADIUS * 2 + 8f + 8f + 18f + 8f) + labelWidth;
  }

  @Override
  public void draw (Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds.isEmpty() || alpha == 0) {
      return;
    }
    int saveCount;
    if (alpha < 0xFF) {
      saveCount = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha, Canvas.ALL_SAVE_FLAG);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    int outlineColor = Theme.inlineOutlineColor(false);
    Paint outlinePaint = Paints.strokeSmallPaint(outlineColor);
    float outlineInset = outlinePaint.getStrokeWidth() / 2f;
    int radius = Screen.dp(8f);
    RectF roundRect = Paints.getRectF();
    roundRect.set(bounds.left + outlineInset, bounds.top + outlineInset, bounds.right - outlineInset, bounds.bottom - outlineInset);
    canvas.drawRoundRect(roundRect, radius, radius, Paints.fillingPaint(Theme.fillingColor()));
    canvas.drawRoundRect(roundRect, radius, radius, outlinePaint);

    int avatarRadius = Screen.dp(AVATAR_RADIUS);
    int avatarX = bounds.left + avatarRadius + Screen.dp(4f);
    int avatarY = bounds.centerY();
    ImageReceiver imageReceiver = avatarFile != null && complexReceiver != null ? complexReceiver.getImageReceiver(id) : null;
    if (imageReceiver != null) {
      imageReceiver.setBounds(avatarX - avatarRadius, avatarY - avatarRadius, avatarX + avatarRadius, avatarY + avatarRadius);
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderRounded(canvas, avatarRadius, Theme.placeholderColor());
      }
      imageReceiver.draw(canvas);
    } else {
      avatarPlaceholder.draw(canvas, avatarX, avatarY);
    }

    label.draw(canvas, bounds.left + avatarRadius * 2 + Screen.dp(8f + 4f), bounds.centerY() - label.getLineCenterY());

    int iconX = bounds.right - Screen.dp(17f);
    int iconY = bounds.centerY();
    if (crossIconRipple != null) {
      int rippleRadius = Screen.dp(28f) / 2;
      crossIconRipple.setBounds(iconX - rippleRadius, iconY - rippleRadius, iconX + rippleRadius, iconY + rippleRadius);
      crossIconRipple.draw(canvas);
    }
    Drawables.drawCentered(canvas, crossIcon, iconX, iconY, PorterDuffPaint.get(R.id.theme_color_inlineIcon));

    if (alpha < 0xFF) {
      canvas.restoreToCount(saveCount);
    }
  }

  @Override
  public void setAlpha (int alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidateSelf();
    }
  }

  @Override
  public int getAlpha () {
    return alpha;
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {
  }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getWidth () {
    return getIntrinsicWidth();
  }

  @Override
  public int getHeight () {
    return getIntrinsicHeight();
  }

  @Override
  public void invalidateDrawable (@NonNull Drawable who) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.invalidateDrawable(this);
    }
  }

  @Override
  public void scheduleDrawable (@NonNull Drawable who, @NonNull Runnable what, long when) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.scheduleDrawable(this, what, when);
    }
  }

  @Override
  public void unscheduleDrawable (@NonNull Drawable who, @NonNull Runnable what) {
    Callback callback = getCallback();
    if (callback != null) {
      callback.unscheduleDrawable(this, what);
    }
  }

  @Override
  public int defaultTextColor () {
    return Theme.textAccentColor();
  }
}

class ChipGroup extends SparseDrawableView implements ClickHelper.Delegate, AttachDelegate, Destroyable {
  private final ComplexReceiver complexReceiver = new ComplexReceiver(this);
  private final FlowListAnimator<Chip> animator = new FlowListAnimator<>(animator -> {
    if (getHeight() != animator.getMetadata().getTotalHeight()) {
      requestLayout();
    }
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);
  private final ClickHelper clickHelper = new ClickHelper(this);

  private List<Chip> chips = Collections.emptyList();
  private int spacing;
  private Delegate delegate;

  public interface Delegate {
    default void onCrossClick (Chip chip) {}
  }

  public ChipGroup (Context context) {
    super(context);
    setWillNotDraw(false);
    animator.setLineSpacing(Screen.dp(8f));
    animator.setItemSpacing(Screen.dp(8f));
  }

  public void setSpacing (int spacing) {
    if (this.spacing != spacing) {
      this.spacing = spacing;
      requestLayout();
    }
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public ComplexReceiver getComplexReceiver () {
    return complexReceiver;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return delegate != null && clickHelper.onTouchEvent(this, event);
  }

  public Chip chat (Tdlib tdlib, long chatId) {
    for (Chip chip : chips) {
      if (chip.type == Chip.TYPE_CHAT && chip.id == chatId) {
        return chip;
      }
    }
    return new Chip(this, complexReceiver, tdlib, chatId);
  }

  public Chip chatType (@IdRes int chatType) {
    for (Chip chip : chips) {
      if (chip.type == Chip.TYPE_CHAT_TYPE && chip.id == chatType) {
        return chip;
      }
    }
    return new Chip(this, chatType);
  }

  public void setChips (List<Chip> chips) {
    for (Chip chip : this.chips) {
      chip.setCallback(null);
    }
    this.chips = chips;
    for (Chip chip : this.chips) {
      chip.setCallback(this);
      chip.requestFiles();
    }
    animator.reset(chips, isLaidOut());
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
    int maxWidth = measuredWidth - getPaddingLeft() - getPaddingRight();
    if (maxWidth != animator.getMaxWidth()) {
      animator.setMaxWidth(maxWidth);
      animator.measure(isLaidOut());
    }
    int measuredHeight = (int) animator.getMetadata().getTotalHeight() + getPaddingTop() + getPaddingBottom();
    setMeasuredDimension(measuredWidth, measuredHeight);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    canvas.drawRect(0, 0, getWidth(), getHeight(), Paints.fillingPaint(Theme.fillingColor()));
    canvas.translate(getPaddingLeft(), getPaddingTop());
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      int alpha = Math.round(entry.getVisibility() * 0xFF);
      Rect bounds = Paints.getRect();
      entry.getBounds(bounds);
      entry.item.setAlpha(MathUtils.clamp(alpha, 0x00, 0xFF));
      entry.item.setBounds(bounds);
      entry.item.draw(canvas);
    }
    canvas.translate(-getPaddingLeft(), -getPaddingTop());
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    if (delegate != null) {
      Chip chip = findChipAt(x, y);
      return chip != null && chip.inCrossIconTouchBounds(x, y);
    }
    return false;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (delegate != null) {
      Chip chip = findChipAt(x, y);
      if (chip != null /*&& chip.inCrossIconTouchBounds(x, y)*/) {
        delegate.onCrossClick(chip);
      }
    }
  }

  private @Nullable Chip pressedChip;

  @Override
  public void onClickTouchDown (View view, float x, float y) {
    pressedChip = findChipAt(x, y);
    if (pressedChip != null && pressedChip.inCrossIconTouchBounds(x, y)) {
      pressedChip.setCrossIconPressed(true);
    }
  }

  @Override
  public void onClickTouchUp (View view, float x, float y) {
    if (pressedChip != null) {
      pressedChip.setCrossIconPressed(false);
      pressedChip = null;
    }
  }

  private @Nullable Chip findChipAt (float x, float y) {
    int rx = Math.round(x), ry = Math.round(y);
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      Rect bounds = entry.item.getBounds();
      if (bounds.contains(rx, ry)) {
        return entry.item;
      }
    }
    return null;
  }

  @Override
  public void attach () {
    complexReceiver.attach();
  }

  @Override
  public void detach () {
    complexReceiver.detach();
  }


  @Override
  public void performDestroy () {
    complexReceiver.performDestroy();
  }

  @Override
  protected boolean verifyDrawable (@NonNull Drawable who) {
    if (super.verifyDrawable(who)) {
      return true;
    }
    for (FlowListAnimator.Entry<Chip> entry : animator) {
      if (entry.item == who) {
        return true;
      }
    }
    return false;
  }
}