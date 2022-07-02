package org.thunderdog.challegram.component.reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager.widget.PagerAdapter;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class MessageOptionsLayout extends BottomSheet implements OptionDelegate,
        androidx.viewpager.widget.ViewPager.OnPageChangeListener,
        UserReactionsController.UserReactionsCallback,
        ViewPagerTopView.OnItemClickListener, ReactionsPanelLayout.Callback {

    private final ViewController<?> parent;
    private final ThemeDelegate themeDelegate;

    private final MessageOptionsController messageOptionsController;
    private final ReactionsPanelLayout availableReactionsLayout;
    private final ViewPagerHeaderViewCompact reactionsIndicators;
    private final ImageButton backButton;
    private final ViewPager viewPager;
    private final View expandedHeaderView;

    private final OptionDelegate optionDelegate;
    private final int reactionPanelHeight;
    private final int collapsedHeaderHeight;
    private final int expandedHeaderHeight;
    private final int expandedMaxOffset;
    private final int selectionColorId = R.id.theme_color_themeWhiteBlack;
    private final List<TGReaction> reactions = new ArrayList<>();
    private final List<ReactionDrawable> reactionDrawables = new ArrayList<>();
    private final ReactionsManager reactionsManager;
    private TGMessage message;


    public MessageOptionsLayout(BottomSheetLayout bottomSheetLayout, ViewController<?> parent, ThemeDelegate themeDelegate, ReactionsManager reactionsManager) {
        super(bottomSheetLayout);
        this.themeDelegate = themeDelegate;
        this.parent = parent;
        this.reactionsManager = reactionsManager;
        this.reactionPanelHeight = Screen.dp(54);
        this.collapsedHeaderHeight = Screen.dp(54);
        this.expandedHeaderHeight = HeaderView.getHeaderHeight(parent);
        this.expandedMaxOffset = (expandedHeaderHeight - collapsedHeaderHeight) / 2;
        this.setBackgroundColor(Theme.backgroundColor());
        this.optionDelegate = parent instanceof OptionDelegate ? (OptionDelegate) parent : null;
        this.availableReactionsLayout = createReactionsPanel(getContext(), parent, reactionsManager);
        this.backButton = createBackButton(getContext());
        this.expandedHeaderView = createExpandedHeaderView(getContext());
        this.reactionsIndicators = createReactionsHeader(getContext());
        this.messageOptionsController = new MessageOptionsController(getContext(), parent.tdlib(), reactionsManager);
        viewPager = new ViewPager(getContext());
        viewPager.addOnPageChangeListener(this);
        this.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom, int i4, int i5, int i6, int i7) {
                MessageOptionsLayout.this.scrollHeader(0, 0f);
                FrameLayout.LayoutParams availableReactionsLayoutLayoutParams = (LayoutParams) availableReactionsLayout.getLayoutParams();
                availableReactionsLayoutLayoutParams.rightMargin = reactionsIndicators.getTopView().getItemWidthAt(0);
                availableReactionsLayout.setLayoutParams(availableReactionsLayoutLayoutParams);
                MessageOptionsLayout.this.requestLayout();
                view.removeOnLayoutChangeListener(this);
            }
        });
    }

    public void setMessageOptions(TGMessage message, ViewController.Options options) {
        this.message = message;
        int optionsItemHeight = messageOptionsController.getOptionsItemHeight();
        if (options.items != null) {
            setCollapsedHeight(options.items.length * optionsItemHeight + reactionPanelHeight);
        }
        MessageOptionsController.Args msgOptionsArgs = new MessageOptionsController.Args(options, message.getId());
        msgOptionsArgs.setOptionsDelegate(this);
        messageOptionsController.setArguments(msgOptionsArgs);
        availableReactionsLayout.loadMessageAvailableReaction(message.getId());
        setReactionIndicators(message);

        MessagePopupAdapter adapter = new MessagePopupAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setPagingEnabled(true);
        viewPager.setCurrentItem(0);
    }

    @Override
    protected boolean canMove() {
        return viewPager.getCurrentItem() > 0;
    }

    @Override
    protected boolean canMinimizeHeight() {
        return false;
    }

    @Override
    public boolean onBackPressed(boolean fromTop) {
        if (canMove() && isExpanded()) {
            collapse();
            return true;
        } else if (viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0, true);
            return true;
        }
        return super.onBackPressed(fromTop);
    }

    @Override
    public boolean onOptionItemPressed(View optionItemView, int id) {
        if (optionDelegate != null && optionDelegate.onOptionItemPressed(optionItemView, id)) {
            dismiss();
            return true;
        }
        return false;
    }

    @Override
    public Object getTagForItem(int position) {
        return optionDelegate != null ? optionDelegate.getTagForItem(position) : null;
    }

    @Override
    public boolean disableCancelOnTouchdown() {
        return optionDelegate != null && optionDelegate.disableCancelOnTouchdown();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (position > 0) {
            reactionsIndicators.getTopView().setSelectionFactor((float) (position - 1) + positionOffset);
        } else {
            scrollHeader(position, positionOffset);
        }
    }

    @Override
    public void onPageSelected(int position) {}

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_IDLE && viewPager.getCurrentItem() == 0) {
            collapse();
        }
    }

    @Override
    public void onUserSelected(TGUser user) {
        this.dismiss();
        this.parent.tdlib().ui().openPrivateProfile(this.parent, user.getUserId(), null);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        super.onFactorChanged(id, factor, fraction, callee);
        float toFactor = callee.getToFactor();
        State state = getCurrentState();
        if (id == BottomSheet.REVEAL_ANIMATOR) {
            float offset = 0;
            if (state == BottomSheet.State.HIDING) {
                offset = collapsedHeaderHeight * fraction;
            } else if (state == BottomSheet.State.REVEALING){
                offset = collapsedHeaderHeight * (toFactor - fraction);
            }
            viewPager.setTranslationY(-offset);
        }
    }

    @Override
    protected void onHeightChange(int height, float factor, boolean byUser) {
        super.onHeightChange(height, factor, byUser);
        float offset = expandedMaxOffset * factor;
        expandedHeaderView.setBackgroundColor(adjustAlpha(Theme.getColor(R.id.theme_color_headerBackground), factor));
        viewPager.setTranslationY(offset + Screen.dp(1) * factor);
        backButton.setTranslationY(offset);
        reactionsIndicators.setTranslationY(offset);
        availableReactionsLayout.setTranslationY(offset);
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        super.onFactorChangeFinished(id, finalFactor, callee);
        messageOptionsController.onFactorChangeFinished(id, finalFactor, callee);
    }

    @Override
    public void onPagerItemClick(int index) {
        viewPager.setCurrentItem(index + 1);
    }

    @Override
    public void onReactionClicked(String reaction) {
        if (this.message != null) {
            reactionsManager.sendMessageReaction(this.message.getChatId(), this.message.getId(), reaction);
            dismiss();
        }
    }

    private void scrollHeader(int position, float offset) {
        if (reactionsIndicators == null || position > 0) return;
        int widthOffset =reactionsIndicators.getTopView().getItemWidthAt(0) + backButton.getWidth();
        this.backButton.setTranslationX((getWidth() - widthOffset) * (1f - offset));
        this.reactionsIndicators.setTranslationX((getWidth() - widthOffset) * (1f - offset));
        this.backButton.setAlpha(offset);
        this.availableReactionsLayout.setTranslationX(-getWidth() * offset);
        this.reactionsIndicators.getTopView().setSelectionColor(adjustAlpha(Theme.getColor(selectionColorId), offset));
    }

    @ColorInt
    private static int adjustAlpha(@ColorInt int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private void setReactionIndicators(TGMessage message) {
        int totalCount = 0;
        int size = Screen.dp(18);
        this.reactions.clear();
        this.reactionDrawables.clear();
        int chatType = message.getChat() != null ? message.getChat().type.getConstructor() : 0;
        boolean isReactionsIndicatorsSupported = chatType == TdApi.ChatTypeBasicGroup.CONSTRUCTOR
                || chatType == TdApi.ChatTypePrivate.CONSTRUCTOR
                || chatType == TdApi.ChatTypeSupergroup.CONSTRUCTOR;
        TGReaction[] messageReactions = message.getReactions();
        if (!isReactionsIndicatorsSupported || messageReactions == null) {
            configureLayout();
            return;
        }
        List<ViewPagerTopView.Item> items = new ArrayList<>(messageReactions.length + 1);
        for (TGReaction reaction : messageReactions) {
            totalCount += reaction.getTotalCount();
            if (reaction.getReactionSenders().length == 0) {
                continue;
            }
            this.reactions.add(reaction);
            ReactionDrawable.ReactionInfo info = new ReactionDrawable.ReactionInfo(reaction, reactionsIndicators.getTopView());
            info.setWidth(size);
            info.setHeight(size);
            ReactionDrawable reactionDrawable = new ReactionDrawable(info);
            reactionDrawables.add(reactionDrawable);
            items.add(new ViewPagerTopView.Item(String.valueOf(reaction.getTotalCount()), reactionDrawable));
        }
        Drawable heart = new WrappedDrawable(Drawables.get(R.drawable.baseline_heart_24), size, size);
        heart.setBounds(new Rect(0, 0, size, size));
        heart.setColorFilter(new PorterDuffColorFilter(Theme.getColor(selectionColorId), PorterDuff.Mode.SRC_ATOP));
        items.add(0, new ViewPagerTopView.Item(String.valueOf(totalCount), heart));
        if (items.size() == 1 && reactionsIndicators.getTopView() != null) {
            View topView = reactionsIndicators.getTopView();
            topView.setClickable(false);
            topView.setFocusable(false);
            topView.setFocusableInTouchMode(false);
        }
        reactionsIndicators.getTopView().setItems(items);
        configureLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (ReactionDrawable drawable : reactionDrawables) {
            drawable.performDestroy();
        }

    }

    private void configureLayout() {
        FrameLayout.LayoutParams reactionPanelLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, reactionPanelHeight);
        reactionPanelLp.rightMargin = reactionsIndicators.getTopView().getItemWidthAt(0);

        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(Screen.dp(56), collapsedHeaderHeight);
        buttonLp.gravity  = Gravity.START | Gravity.TOP;

        FrameLayout.LayoutParams reactionIndicatorsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, collapsedHeaderHeight);
        reactionIndicatorsLp.gravity  = Gravity.TOP | Gravity.START;
        reactionIndicatorsLp.setMargins(buttonLp.width, 0, 0, 0);

        FrameLayout.LayoutParams viewPagerLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        viewPagerLp.topMargin = reactionPanelHeight;

        FrameLayout.LayoutParams expandedHeaderLp = new LayoutParams(LayoutParams.MATCH_PARENT, expandedHeaderHeight);

        this.addView(expandedHeaderView, expandedHeaderLp);
        this.addView(availableReactionsLayout, reactionPanelLp);
        this.addView(reactionsIndicators, reactionIndicatorsLp);
        this.addView(backButton, buttonLp);
        this.addView(viewPager, viewPagerLp);
    }

    private View createExpandedHeaderView(Context context) {
        return new View(context);
    }

    private ReactionsPanelLayout createReactionsPanel(Context context, ViewController<?> parent, ReactionsManager reactionsManager) {
        ReactionsPanelLayout availableReactionsLayout = new ReactionsPanelLayout(context, parent, reactionsManager);
        availableReactionsLayout.setPadding(Screen.dp(8), 0, Screen.dp(8), 0);
        availableReactionsLayout.setCallback(this);
        return availableReactionsLayout;
    }

    private ViewPagerHeaderViewCompact createReactionsHeader(Context context) {
        ViewPagerHeaderViewCompact reactionIndicators = new ViewPagerHeaderViewCompact(context);
        ViewPagerTopView topView = reactionIndicators.getTopView();
        topView.setOnItemClickListener(this);
        topView.setUseDarkBackground();
        topView.setTouchDisabled(false);
        topView.setTextPadding(Screen.dp(8));
        if (Theme.isDark()) {
            topView.setTextFromToColorId(R.id.theme_color_white, R.id.theme_color_white);
            topView.setSelectionColorId(R.id.theme_color_themeWhiteBlack);
        } else {
            topView.setTextFromToColorId(R.id.theme_color_black, R.id.theme_color_black);
            topView.setSelectionColorId(R.id.theme_color_themeBlackWhite);

        }
        topView.setUseIconTint(false);
        reactionIndicators.getRecyclerView().getLayoutParams().height = collapsedHeaderHeight;
        reactionIndicators.getRecyclerView().setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl()) {
            @Override
            public boolean canScrollHorizontally() {
                return viewPager.getCurrentItem() > 0;
            }
        });
        topView.getLayoutParams().height = collapsedHeaderHeight;
        topView.setItems(new ArrayList<>());
        return reactionIndicators;
    }

    private ImageButton createBackButton(Context context) {
        ImageButton backButton = new ImageButton(context);

        int buttonPadding = Screen.dp(8);
        Drawable icon = Drawables.get(R.drawable.baseline_arrow_back_24);
        if (Theme.isDark()) {
            DrawableCompat.setTint(icon, Theme.getColor(R.id.theme_color_white));
        } else {
            DrawableCompat.setTint(icon,Theme.getColor(R.id.theme_color_black));
        }
        DrawableCompat.setTint(icon, Theme.headerBackColor());
        backButton.setBackground(Theme.transparentBlackSelector());
        backButton.setImageDrawable(icon);
        backButton.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
        backButton.setOnClickListener((v) -> onBackPressed(false));
        return backButton;
    }

    private class MessagePopupAdapter extends PagerAdapter {
        static final int DEFAULT_PAGE_SIZE = 1;
        private final int pageCount;
        private final UserReactionsController[] controllers;

        public MessagePopupAdapter() {
            this.pageCount = !reactions.isEmpty() ? reactions.size() + 2 : DEFAULT_PAGE_SIZE;
            controllers = new UserReactionsController[this.pageCount];
        }

        @Override
        public int getCount() {
            return this.pageCount;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view;
            container.setClipChildren(false);
            if (position == 0) {
                view = createOptionsLayout();
            } else if (position == 1) {
                view = createRecentReactions(position, new UserReactionsController.Args(reactions.toArray(new TGReaction[]{})));
            } else {
                view = createRecentReactions(position, new UserReactionsController.Args(reactions.get(position - 2)));
            }
            container.addView(view);
            return view != null ? view : new View(container.getContext());
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if (position == 0) {
                messageOptionsController.performDestroy();
            } else {
                UserReactionsController controller = controllers[position - 1];
                if (controller != null) controller.performDestroy();
            }
            container.removeView((View) object);
        }

        private View createOptionsLayout() {
            return messageOptionsController.get();
        }

        private View createRecentReactions(int index, UserReactionsController.Args args) {
            UserReactionsController userReactionsController = new UserReactionsController(getContext(), parent.tdlib());
            userReactionsController.setCallback(MessageOptionsLayout.this);
            userReactionsController.setArguments(args);
            View view = userReactionsController.get();
            setScrollableDelegate(userReactionsController);
            this.controllers[index] = userReactionsController;
            return view;
        }
    }

    private static class WrappedDrawable extends Drawable {

        private final Drawable _drawable;
        private int width;
        private int height;

        public WrappedDrawable(Drawable drawable, int width, int height) {
            super();
            _drawable = drawable;
            this.width = width;
            this.height = height;
            _drawable.setBounds(new Rect(0, 0, width, height));
        }

        protected Drawable getDrawable() {
            return _drawable;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            //update bounds to get correctly
            super.setBounds(left, top, right, bottom);
            Drawable drawable = getDrawable();
            if (drawable != null) {
                drawable.setBounds(left, top, left + width, top + height);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            Drawable drawable = getDrawable();
            if (drawable != null) {
                drawable.setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            Drawable drawable = getDrawable();
            if (drawable != null) {
                drawable.setColorFilter(colorFilter);
            }
        }

        @Override
        public int getOpacity() {
            Drawable drawable = getDrawable();
            return drawable != null
                    ? drawable.getOpacity()
                    : PixelFormat.UNKNOWN;
        }

        @Override
        public void draw(Canvas canvas) {
            Drawable drawable = getDrawable();
            if (drawable != null) {
                drawable.draw(canvas);
            }
        }

        @Override
        public int getIntrinsicWidth() {
            Drawable drawable = getDrawable();
            return drawable != null
                    ? drawable.getBounds().width()
                    : 0;
        }

        @Override
        public int getIntrinsicHeight() {
            Drawable drawable = getDrawable();
            return drawable != null ?
                    drawable.getBounds().height()
                    : 0;
        }

        @Override
        public int getMinimumWidth() {
            return getIntrinsicWidth();
        }

        @Override
        public int getMinimumHeight() {
            return getIntrinsicHeight();
        }
    }
}
