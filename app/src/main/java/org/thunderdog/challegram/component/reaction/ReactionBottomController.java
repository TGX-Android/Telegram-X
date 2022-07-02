package org.thunderdog.challegram.component.reaction;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.PagerHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.widget.ViewPager;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionBottomController extends MediaBottomBaseController<ReactionBottomController.Arguments> implements PeopleReactionsController.Callback {
    public static class Arguments {
        public String[] reactions;
        public long chatId;
        public long messageId;

        public Arguments (String[] reactions, long chatId, long messageId) {
            this.reactions = reactions;
            this.chatId = chatId;
            this.messageId = messageId;
        }
    }

    public ReactionBottomController(MediaLayout context) {
        super(context, R.string.Reactions);
    }

    private ViewPagerController<?> viewPager;
    private View viewPagerHeaderView;
    private boolean isFirstAttached;

    @Override
    public int getId () {
        return R.id.controller_reactionsView;
    }

    @Override
    public boolean onBackPressed (boolean fromTop) {
        mediaLayout.hide(false);
        return true;
    }

    @Override
    protected View onCreateView (Context context) {
        buildContentView(false);
        setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

        Arguments arguments = getArgumentsStrict();
        viewPager = new ViewPagerController<>(context, tdlib) {
            @Override
            public int getId() {
                return 1235672393;
            }

            @Override
            protected int getDrawerReplacementColorId() {
                return R.id.theme_color_messageSelection;
            }

            @Override
            protected int getPagerItemCount() {
                return arguments.reactions.length;
            }

            @Override
            protected void onCreateView(Context context, FrameLayoutFix contentView, ViewPager pager) {

            }

            @Override
            protected ViewController<?> onCreatePagerItemForPosition(Context context, int position) {
                PeopleReactionsController c = new PeopleReactionsController(context, tdlib, arguments.chatId, arguments.messageId, arguments.reactions[position], ReactionBottomController.this);
                if (position == 0 && !isFirstAttached) {
                    isFirstAttached = true;
                    c.get();
                    setRecyclerView(c.provideRecyclerView());
                    return c;
                }
                return new PeopleReactionsController(context, tdlib, arguments.chatId, arguments.messageId, arguments.reactions[position], ReactionBottomController.this);
            }

            @Override
            protected String[] getPagerSections() {
                return arguments.reactions;
            }

            @Override
            public void onPageSelected(int position, int actualPosition) {
                super.onPageSelected(position, actualPosition);
                contentView.removeView(recyclerView);
                setRecyclerView(((RecyclerViewController<?>) viewPager.getPagerItemForPosition(actualPosition)).provideRecyclerView());
            }

            @Override
            public boolean canSlideBackFrom(NavigationController navigationController, float x, float y) {
                return super.canSlideBackFrom(navigationController, x, y) && viewPager.getCurrentPagerItemPosition() == 0;
            }

            @Override
            protected int getTransformHeaderHeight() {
                return viewPagerHeaderView.getHeight();
            }
        };
        View view = viewPager.get();
        view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentView.addView(view);

        viewPagerHeaderView = ((PagerHeaderView) viewPager.getCustomHeaderCell()).getTopView();
        // TODO ask color for white theme
        ViewSupport.setThemedBackground(viewPagerHeaderView, R.id.theme_color_headerLightBackground, this);
        contentView.addView(viewPagerHeaderView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, viewPagerHeaderView.getLayoutParams().height));

        // TODO pass height from rv to calculate
        viewPagerHeaderView.setY(Screen.currentHeight() - Screen.dp(200f) - viewPagerHeaderView.getHeight());
        return contentView;
    }

    @Override
    protected int getInitialContentHeight () {
        // TODO pass height from rv to calculate
        return Screen.dp(200f);
    }

    @Override
    protected int getHeaderHeight () {
        if (viewPagerHeaderView != null) {
            return viewPagerHeaderView.getHeight();
        } else {
            return 0;
        }
    }

    @Override
    protected void onRecyclerTopUpdate (float top) {
        super.onRecyclerTopUpdate(top);
        if (viewPagerHeaderView != null) {
            viewPagerHeaderView.setY((int) top - viewPagerHeaderView.getMeasuredHeight());
        }
    }

    @Override
    public void onRecyclerFirstMovement () {
        super.onRecyclerFirstMovement();
        mediaLayout.getHeaderView().setVisibility(View.GONE);
    }

    @Override
    public void close() {
        runOnUiThread(() -> onBackPressed(true));
    }
}
