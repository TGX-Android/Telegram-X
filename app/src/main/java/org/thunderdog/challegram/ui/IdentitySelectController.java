package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.component.sendas.AvatarDrawable;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class IdentitySelectController extends ViewController<IdentitySelectController.Args> implements
  Menu,
  PopupLayout.PopupHeightProvider,
  PopupLayout.TouchSectionProvider {

  public static class Args {
    public long chatId;
    public TdApi.MessageSender messageSenderId;
    public @Nullable Callback callback;

    public Args (long chatId, TdApi.MessageSender messageSenderId, @Nullable Callback callback) {
      this.chatId = chatId;
      this.messageSenderId = messageSenderId;
      this.callback = callback;
    }
  };

  public interface Callback {
    void onComplete (TdApi.MessageSender messageSenderId);
  }

  public static TGUser parseSender (Tdlib tdlib, TdApi.MessageSender sender, ArrayList<TGUser> senders) {
    TGUser parsedUser;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        TdApi.Chat chat = tdlib.chatStrict(((TdApi.MessageSenderChat) sender).chatId);
        parsedUser = new TGUser(tdlib, chat);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) sender).userId);
        parsedUser = new TGUser(tdlib, user);
        break;
      }
      default: {
        throw new UnsupportedOperationException(sender.toString());
      }
    }
    parsedUser.setNoBotState();
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  public static TGFoundChat parseSender2 (Tdlib tdlib, TdApi.MessageSender sender) {
    TGFoundChat parsedUser;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        parsedUser = new TGFoundChat(tdlib, null, ((TdApi.MessageSenderChat) sender).chatId, true);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) sender).userId);
        parsedUser = new TGFoundChat(tdlib, user, "", false);
        break;
      }
      default: {
        throw new UnsupportedOperationException(sender.toString());
      }
    }
    return parsedUser;
  }

  private RelativeLayout contentView;
  private CustomRecyclerView recyclerView;
  private MyAdapter adapter;
  private SimpleUsersAdapter adapter2;
  private FrameLayoutFix wrapView;
  private IdentitySelectController.LickView lickView;
  private NoSearchResultsView noSearchResultsView;

  public IdentitySelectController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
    setName(Lang.getString(R.string.SendAs));
  }

  @Override
  public void setArguments (IdentitySelectController.Args args) {
    super.setArguments(args);
    chatId = args.chatId;
    messageSenderId = args.messageSenderId;
    callback = args.callback;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new RelativeLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    adapter = new MyAdapter(chatId, v -> {
      //android.util.Log.i("ISC", "clicked");

      var senderId = ((SenderIdentityView) v).getSenderId();

      if (!tdlib().hasPremium()) {
        var futureSender = Arrays.stream(availableSenders.senders)
          .filter(it -> Td.getSenderId(it.sender) == Td.getSenderId(senderId))
          .findFirst().orElse(null);
        if (futureSender != null && futureSender.needsPremium) {
          UI.showToast(Lang.getString(R.string.PremiumRequired), Toast.LENGTH_LONG);
          return;
        }
      }

      tdlib().client().send(
        new TdApi.SetChatMessageSender(chatId, senderId),
        result -> tdlib.ui().post(() -> {
          if (result instanceof TdApi.Error) {
            //android.util.Log.i("ISC", String.format("SetChatMessageSender failed: %s", ((TdApi.Error) result).toString()));

            UI.showToast(result.toString(), Toast.LENGTH_LONG);
          } else {
            //android.util.Log.i("ISC", String.format("SetChatMessageSender to %s (%s)", senderId.toString(), result.toString()));

            messageSenderId = senderId;

            if (callback != null) {
              callback.onComplete(senderId);
            }

            popupLayout.hideWindow(true);
          }
        })
      );
    });

    var params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(true);
    recyclerView = new CustomRecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY() - HeaderView.getSize(true)) && super.onTouchEvent(e);
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        //post(ShareController.this);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
        /*if (awaitLayout) {
          awaitLayout = false;
          autoScroll(awaitScrollBy);
        }*/
        //launchExpansionAnimation();
      }

      @Override
      public void draw (Canvas c) {
        int top = detectTopRecyclerEdge();
        int bottom = detectBottomRecyclerEdge();
        /*if (top == 0) {
          c.drawColor(Theme.backgroundColor());
          //c.drawColor(Color.GREEN);
        } else */{
          c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
          c.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.backgroundColor()));
          //c.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Color.RED));
        }

        super.draw(c);

        ShadowView.drawDropShadow(c, 0, getWidth(), bottom, 1f);
      }

      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
      }
    };
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        /*if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          setAutoScrollFinished(true);
        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          resetTopEnsuredState();
          hideSoftwareKeyboard();
        }*/
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkHeaderPosition();
      }
    });
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(context));
    recyclerView.setLayoutParams(params);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        final int itemCount = parent.getAdapter().getItemCount();
        final int position = parent.getChildAdapterPosition(view);
        final boolean isUnknown = position == RecyclerView.NO_POSITION;
        int top = 0, bottom = 0;

        if (position == 0 || isUnknown) {
          if (!inSearchMode()) {
            top = getTargetHeight() - itemCount * Screen.dp(63f) - HeaderView.getSize(true);// - Screen.dp(VERTICAL_PADDING_SIZE);//getContentOffset();
            if (top < getTargetHeight() / 2) {
              top = getTargetHeight() / 2 - HeaderView.getSize(true);// - Screen.dp(VERTICAL_PADDING_SIZE);
            }
          }/* else {
            top = Screen.dp(VERTICAL_PADDING_SIZE);
          }*/
        }
        if (position == itemCount - 1 || isUnknown) {
          if (!inSearchMode()) {
            bottom = getTargetHeight() - itemCount * Screen.dp(63f) - HeaderView.getSize(true);// - Screen.dp(VERTICAL_PADDING_SIZE);
          }/* else {
            bottom = Screen.dp(VERTICAL_PADDING_SIZE);
          }*/
        }

        outRect.set(0, Math.max(top, 0), 0, Math.max(bottom, 0));
      }
    });
    //recyclerView.setAdapter(adapter);
    contentView.addView(recyclerView);

    headerView = new HeaderView(context);
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    getSearchHeaderView(headerView);

    noSearchResultsView = new NoSearchResultsView(context);
    noSearchResultsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    noSearchResultsView.setVisibility(View.INVISIBLE);

    wrapView = new FrameLayoutFix(context);
    wrapView.addView(contentView);
    wrapView.addView(noSearchResultsView);
    wrapView.addView(headerView);
    if (HeaderView.getTopOffset() > 0) {
      lickView = new IdentitySelectController.LickView(context);
      addThemeInvalidateListener(lickView);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
      wrapView.addView(lickView);
    }

    tdlib().client().send(
      new TdApi.GetChatAvailableMessageSenders(chatId),
      result -> tdlib.ui().post(() -> {
        if (result instanceof TdApi.ChatMessageSenders) {
          availableSenders = (TdApi.ChatMessageSenders) result;

          var users = Arrays.stream(availableSenders.senders)
            .map(it -> parseSender(tdlib(), it.sender, null))
            .collect(Collectors.toList());
          /*for (int i = 0; i < 3; i++) {
            var users2 = Arrays.stream(availableSenders.senders)
              .map(it -> parseSender2(tdlib(), it))
              .collect(Collectors.toList());
            users.addAll(users2);
          }*/
          adapter.setUsers(users);
        } else {
          availableSenders = new TdApi.ChatMessageSenders(new TdApi.ChatMessageSender[] {});

          adapter.setUsers(new ArrayList<>());
        }

        recyclerView.setAdapter(adapter);
        launchOpenAnimation();
      })
    );

    return wrapView;
  }

  private long chatId;
  private TdApi.MessageSender messageSenderId;
  private @Nullable Callback callback;
  private TdApi.ChatMessageSenders availableSenders;

  private static final float HORIZONTAL_PADDING_SIZE = 8f;
  private static final float VERTICAL_PADDING_SIZE = 4f;
  private boolean autoScrollFinished = true;

  private View lastFirstView;

  private void checkHeaderPosition () {
    View view = lastFirstView;
    if (view == null || recyclerView.getChildAdapterPosition(view) != 0) {
      view = lastFirstView = recyclerView.getLayoutManager().findViewByPosition(0);
    }
    int top = HeaderView.getTopOffset();
    if (view != null) {
      top = Math.max(view.getTop() - HeaderView.getSize(false) + recyclerView.getTop()/* - Screen.dp(VERTICAL_PADDING_SIZE)*/, HeaderView.getTopOffset());
    }
    if (headerView != null) {
      headerView.setTranslationY(top);
      noSearchResultsView.setTranslationY(top);
      final int topOffset = HeaderView.getTopOffset();
      top -= topOffset;
      if (lickView != null) {
        lickView.setTranslationY(top);
        float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
        lickView.setFactor(factor);
        headerView.getFilling().setShadowAlpha(factor);
      }
    }
  }

  private int detectTopRecyclerEdge () {
    var manager =  (LinearLayoutManager) recyclerView.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int top = 0;
    if (first != RecyclerView.NO_POSITION) {
      View topView = manager.findViewByPosition(first);
      if (topView != null) {
        final int topViewPos = topView.getTop();// - Screen.dp(VERTICAL_PADDING_SIZE);
        if (topViewPos > 0) {
          top = topViewPos;
        }
      }
    }
    return top;
  }

  private int detectBottomRecyclerEdge () {
    var manager =  (LinearLayoutManager) recyclerView.getLayoutManager();
    int last = manager.findLastVisibleItemPosition();
    int bottom = 0;
    if (last != RecyclerView.NO_POSITION) {
      View bottomView = manager.findViewByPosition(last);
      if (bottomView != null) {
        final int bottomViewPos = bottomView.getTop() + bottomView.getHeight();
        if (bottomViewPos > 0) {
          bottom = bottomViewPos;
        }
      }
    }
    return bottom;
  }

  private boolean openLaunched;

  private void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(get(), calculateTotalHeight());
    }
  }

  private int getTargetHeight () {
    return Screen.currentHeight(); // UI.getContext(getContext()).getNavigation().getWrap().getMeasuredHeight();
  }

  private int getContentOffset () {
    return getTargetHeight() / 2 - HeaderView.getSize(true)/* - (isExpanded ? calculateMovementDistance() : 0) + (canShareLink ? 0 : Screen.dp(56f) / 2)*/;
  }

  private int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset());
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - detectTopRecyclerEdge() - (int) ((float) HeaderView.getTopOffset() * (1f - (lickView != null ? lickView.factor : 0f))));
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_icon;
  }

  @Override
  public int getId () {
    return R.id.controller_identitySelect;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search: {
        header.addSearchButton(menu, this, getHeaderIconColorId()).setTouchDownListener((v, e) -> {
          //resetTopEnsuredState();
          hideSoftwareKeyboard();
        });
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSearchMode()) {
      closeSearchMode(null);
      return true;
    }
    return false;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        //if (displayingChats != null) {
          openSearchMode();
          recyclerView.smoothScrollBy(0, (int) (headerView.getTranslationY() - HeaderView.getTopOffset()));
          //runOnUiThread(() -> recyclerView.invalidateItemDecorations(), 150);
          //recyclerView.invalidateItemDecorations();  // "locking" scroll
        //}
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected void onSearchInputChanged (String query) {
    if (query.isEmpty()) {
      var users = Arrays.stream(availableSenders.senders)
        .map(it -> parseSender(tdlib(), it.sender, null))
        .collect(Collectors.toList());
      /*for (int i = 0; i < 3; i++) {
        var users2 = Arrays.stream(availableSenders.senders)
          .map(it -> parseSender2(tdlib(), it))
          .collect(Collectors.toList());
        users.addAll(users2);
      }*/
      adapter.setUsers(users);

      noSearchResultsView.setVisibility(users.size() == 0 ? View.VISIBLE : View.INVISIBLE);

      return;
    }

    Background.instance().post(() -> {
      var foundedUsers = Arrays.stream(availableSenders.senders)
        .map(it -> parseSender(tdlib(), it.sender, null))
        .filter(it -> it.getName().toLowerCase().contains(query.toLowerCase()))  // TODO: better search?
        .collect(Collectors.toList());
      runOnUiThread(() -> {
        adapter.setUsers(foundedUsers);

        noSearchResultsView.setVisibility(foundedUsers.size() == 0 ? View.VISIBLE : View.INVISIBLE);
      });
    });
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    super.onAfterLeaveSearchMode();

    var users = Arrays.stream(availableSenders.senders)
      .map(it -> parseSender(tdlib(), it.sender, null))
      .collect(Collectors.toList());
    adapter.setUsers(users);

    noSearchResultsView.setVisibility(users.size() == 0 ? View.VISIBLE : View.INVISIBLE);

    //recyclerView.invalidateItemDecorations();  // "unlocking" scroll
    recyclerView.scrollBy(0, 2000);
  }

  // PopupLayout

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerView.getTranslationY() - HeaderView.getSize(true);
  }

  private PopupLayout popupLayout;

  public void show () {
    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
        if (!isDestroyed()) {
          recyclerView.invalidateItemDecorations();
        }
      }
    };
    popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    //popupLayout.set®(View.LAYER_TYPE_HARDWARE, Views.LAYER_PAINT);
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(false);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    popupLayout.setIgnoreHorizontal();
    get();
    context().addFullScreenView(this, false);
  }

  public static class SenderIdentityView extends LinearLayout {

    private TdApi.MessageSender senderId;

    private ImageView avatar;
    private LinearLayout titlesLayout;
    private TextView title;
    private TextView subtitle;
    private ImageView extraIcon;

    private boolean isChecked;
    private boolean drawBottomSeparator;

    public SenderIdentityView (Context context) {
      super(context);

      setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(63f)));
      setGravity(Gravity.CENTER_VERTICAL);
      setPadding(Screen.dp(11f), 0, Screen.dp(16f), 0);

      avatar = new ImageView(context) {
        @Override
        protected void onDraw (Canvas canvas) {
          super.onDraw(canvas);

          DrawAlgorithms.drawSimplestCheckBox(canvas, getWidth() / 2, getHeight() / 2, getWidth() - Screen.dp(5f), getHeight() - Screen.dp(5f), isChecked ? 1f : 0f, Theme.fillingColor());
        }
      };
      avatar.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));
      avatar.setPadding(0, Screen.dp(3f), Screen.dp(3f), Screen.dp(3f));

      title = new TextView(context);
      title.setTypeface(Fonts.getRobotoMedium());
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      title.setTextColor(Theme.textAccentColor());
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);

      subtitle = new TextView(context);
      subtitle.setTypeface(Fonts.getRobotoRegular());
      subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      subtitle.setTextColor(Theme.textDecentColor());
      subtitle.setSingleLine(true);
      subtitle.setEllipsize(TextUtils.TruncateAt.END);

      extraIcon = new ImageView(context);
      extraIcon.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));

      titlesLayout = new LinearLayout(context);
      titlesLayout.setOrientation(VERTICAL);
      titlesLayout.addView(title);
      titlesLayout.addView(subtitle);

      var lp = new LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.WRAP_CONTENT);
      avatar.setLayoutParams(lp);

      lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = Screen.dp(11f);
      lp.weight = 1f;
      titlesLayout.setLayoutParams(lp);

      lp = new LayoutParams(Screen.dp(24f), ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = Screen.dp(11f);
      extraIcon.setLayoutParams(lp);

      addView(avatar);
      addView(titlesLayout);
      addView(extraIcon);
    }

    public TdApi.MessageSender getSenderId () {
      return senderId;
    }

    public void setSenderId (TdApi.MessageSender senderId) {
      this.senderId = senderId;
    }

    public void setSenderIdentity (int avatarRes, Drawable avatar, CharSequence title, CharSequence subtitle, int extraIconRes, Drawable extraIcon) {
      if (avatarRes != 0) {
        this.avatar.setImageResource(avatarRes);
      } else if (avatar != null) {
        this.avatar.setImageDrawable(avatar);
      } else {
        this.avatar.setImageResource(0);
        this.avatar.setScaleType(ImageView.ScaleType.CENTER);
        //this.avatar.setVisibility(INVISIBLE);
      }

      this.title.setText(title);

      if (subtitle != null) {
        this.subtitle.setText(subtitle);
        this.subtitle.setVisibility(VISIBLE);
      } else {
        this.subtitle.setVisibility(GONE);
      }

      if (extraIconRes != 0) {
        this.extraIcon.setImageResource(extraIconRes);
        this.extraIcon.setVisibility(VISIBLE);
      } else if (extraIcon != null) {
        this.extraIcon.setImageDrawable(extraIcon);
        this.extraIcon.setVisibility(VISIBLE);
      } else {
        this.extraIcon.setVisibility(INVISIBLE);
      }
    }

    public void setChecked (boolean isChecked) {
      this.isChecked = isChecked;
    }

    @Override
    protected void onDraw (Canvas canvas) {
      //avatar.invalidate();
      //extraIcon.invalidate();

      super.onDraw(canvas);

      if (drawBottomSeparator) {
        canvas.drawRect(titlesLayout.getLeft(), Screen.dp(62f), getWidth(), Screen.dp(63f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_separator)));
      }
    }
  }

  private static class LickView extends View {

    public LickView (Context context) {
      super(context);
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (factor > 0f) {
        int bottom = getMeasuredHeight();
        int top = bottom - (int) ((float) bottom * factor);
        c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
      }
    }
  }

  private static class NoSearchResultsView extends LinearLayout {

    public NoSearchResultsView (Context context) {
      super(context);

      setBackgroundColor(Theme.backgroundColor());
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_VERTICAL);

      var a = new ImageView(context);
      a.setImageResource(R.drawable.baseline_search_96);
      a.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_background_icon)));

      var b = new TextView(context);
      b.setTypeface(Fonts.getRobotoMedium());
      b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      b.setTextColor(Theme.textAccent2Color());
      b.setSingleLine(true);
      b.setEllipsize(TextUtils.TruncateAt.END);
      b.setGravity(Gravity.CENTER_HORIZONTAL);
      b.setText(Lang.getString(R.string.NoResultsToShow));

      var c = new TextView(context);
      c.setTypeface(Fonts.getRobotoRegular());
      c.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      c.setTextColor(Theme.textDecent2Color());
      c.setSingleLine(true);
      c.setEllipsize(TextUtils.TruncateAt.END);
      c.setGravity(Gravity.CENTER_HORIZONTAL);
      c.setText(Lang.getString(R.string.NoResultsToShowDesc));

      var lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = Screen.dp(17f);
      b.setLayoutParams(lp);

      lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = Screen.dp(20f);
      c.setLayoutParams(lp);

      addView(a);
      addView(b);
      addView(c);
    }
  }

  public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

    private List<TGUser> items = new ArrayList<>();

    //private long chatId;
    private View.OnClickListener onClickListener;

    public class MyViewHolder extends RecyclerView.ViewHolder {
      public MyViewHolder (@NonNull View itemView) {
        super(itemView);
      }
    };

    public MyAdapter(long chatId, View.OnClickListener onClickListener) {
      //this.chatId = chatId;
      this.onClickListener = onClickListener;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      var view = new SenderIdentityView(context());
      if (onClickListener != null) {
        view.setOnClickListener(onClickListener);
        Views.setClickable(view);
        RippleSupport.setTransparentSelector(view);
      }

      return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull MyViewHolder holder, int position) {
      var item = items.get(position);

      var senderAvatar = new AvatarDrawable(null, 25f, item.getAvatar(), item.getAvatarPlaceholderMetadata());
      String senderUsername = "";
      int senderPremiumLockIconRes = 0;
      boolean hasSmallSenderExtraIcon = false;
      if (tdlib().isSelfChat(item.getChatId())) {
        senderUsername = Lang.getString(R.string.SendAsYourAccount);
        senderPremiumLockIconRes = R.drawable.dot_baseline_acc_personal_24;
      } else if (item.getChatId() == chatId) {
        senderUsername = Lang.getString(R.string.SendAsAnonymousAdmin);
        senderPremiumLockIconRes = R.drawable.dot_baseline_acc_anon_24;
      } else {
        senderUsername = "@" + tdlib().chatUsername(item.getChatId());
        if (!tdlib().hasPremium()) {
          var info = tdlib().cache().supergroupFull(ChatId.toSupergroupId(chatId));
          if (info == null || item.getChatId() != info.linkedChatId) {
            senderPremiumLockIconRes = R.drawable.baseline_lock_24;
            hasSmallSenderExtraIcon = true;
          }
        }
      }

      ((SenderIdentityView) holder.itemView).setSenderId(item.getSenderId());
      ((SenderIdentityView) holder.itemView).setSenderIdentity(0, senderAvatar, item.getName(), senderUsername, senderPremiumLockIconRes, null);
      ((SenderIdentityView) holder.itemView).setChecked(item.getChatId() == Td.getSenderId(messageSenderId));
      ((SenderIdentityView) holder.itemView).drawBottomSeparator = position < getItemCount() - 1;

      ((SenderIdentityView) holder.itemView).setPadding(Screen.dp(11f), 0, Screen.dp(hasSmallSenderExtraIcon ? 18f : 16f), 0);
      var extraIcon = ((SenderIdentityView) holder.itemView).extraIcon;
      extraIcon.setColorFilter(Paints.getColorFilter(Theme.getColor(hasSmallSenderExtraIcon ? R.id.theme_color_text : R.id.theme_color_icon)));
      var lp = ((SenderIdentityView) holder.itemView).extraIcon.getLayoutParams();
      lp.width = Screen.dp(hasSmallSenderExtraIcon ? 16f : 24f);
    }

    @Override
    public int getItemCount () {
      return items.size();
    }

    public void setUsers(List<TGUser> items) {
      int oldItemCount = getItemCount();
      this.items = items;
      U.notifyItemsReplaced(this, oldItemCount);
    }
  }
}
