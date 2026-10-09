package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.ForumTopicListDiff;
import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ForumTopicsController;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.*;

/** Focus-refresh notification and real RecyclerView end-gap regression, without account startup. */
final class ForumListRefreshChecks {
  static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    cases.add(new Stage8SyntheticInstrumentation.Case("topics_focus_refresh_reuses_opaque_holders", ForumListRefreshChecks::focusRefresh));
    for (boolean rtl : new boolean[] {false, true}) {
      for (int offset : new int[] {0, -17}) {
        cases.add(new Stage8SyntheticInstrumentation.Case("rail_paged_anchor_rtl" + rtl + "_offset" + offset,
          env -> pagedAnchor(env, rtl, offset)));
      }
    }
  }

  private static void focusRefresh (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    // Construct only the account-free adapter seam. onFocus itself owns real navigation/account work.
    ForumTopicsController controller = allocate(ForumTopicsController.class);
    Class<?> type = Class.forName(ForumTopicsController.class.getName() + "$TopicAdapter");
    Constructor<?> constructor = type.getDeclaredConstructor(ForumTopicsController.class);
    constructor.setAccessible(true);
    RecyclerView.Adapter<?> adapter = (RecyclerView.Adapter<?>) constructor.newInstance(controller);
    set(adapter, "topics", Arrays.asList(new TdApi.ForumTopic(), new TdApi.ForumTopic()));
    set(controller, "adapter", adapter);
    Object[] payload = {null};
    int[] notified = {0};
    adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override public void onItemRangeChanged (int position, int count, Object value) {
        equal(0, position, "Refresh starts with first row");
        notified[0] += count; payload[0] = value;
      }
    });
    invoke(controller, "rebindRows", new Class<?>[0]);
    equal(3, notified[0], "Focus refresh still updates drafts, metadata and footer");
    require(payload[0] == ForumTopicListDiff.CONTENT_PAYLOAD, "Focus must send a non-selection content payload");
    CustomItemAnimator animator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(new View(context)) { };
    require(!animator.canReuseUpdatedViewHolder(holder, Collections.emptyList()), "Control: no-payload refresh replaces the holder");
    require(animator.canReuseUpdatedViewHolder(holder, Collections.singletonList(payload[0])), "Content refresh reuses the visible holder");
    require(!animator.animateChange(holder, holder, 0, 0, 0, 0), "Unmoved refreshed row needs no secondary animation");
    require(holder.itemView.getAlpha() == 1f && !animator.isRunning(), "No alpha flash at navigation completion");
    require(animator.getMoveDuration() > 0 && animator.getAddDuration() > 0, "Real structural animations remain enabled");
    animator.endAnimations();
  }

  private static void pagedAnchor (SyntheticEnvironment env, boolean rtl, int offsetDp) throws Exception {
    Context context = env.configure(1f, rtl, ThemeId.BLUE);
    int rowHeight = Screen.dp(64), top = Screen.dp(80), bottom = Screen.dp(24);
    int width = Screen.dp(64), height = Screen.dp(768) + top + bottom, offset = Screen.dp(offsetDp);
    int anchor = 28;
    RecyclerView list = new RecyclerView(context);
    list.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    list.setPadding(0, top, 0, bottom);
    list.setClipToPadding(false);
    list.setItemAnimator(null);
    LinearLayoutManager layout = new LinearLayoutManager(context);
    list.setLayoutManager(layout);
    int[] count = {30};
    RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
      @Override public int getItemCount () { return count[0]; }
      @Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int type) {
        View row = new View(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(-1, rowHeight));
        return new RecyclerView.ViewHolder(row) { };
      }
      @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { }
    };
    list.setAdapter(adapter);
    try {
      measure(list, width, height);
      // Reproduce the bug first: only two loaded rows remain after the anchor.
      layout.scrollToPositionWithOffset(anchor, offset);
      measure(list, width, height);
      View oldAnchor = layout.findViewByPosition(anchor);
      require(oldAnchor != null && oldAnchor.getTop() > top + offset + rowHeight,
        "Control: an incomplete page pushes the anchor to the bottom");
      require(!ForumRailLayout.canRestoreScroll(count[0], anchor, offset, rowHeight, height-top-bottom, false),
        "Production readiness must reject this incomplete page");
      count[0] = 60;
      adapter.notifyItemRangeInserted(30, 30);
      require(ForumRailLayout.canRestoreScroll(count[0], anchor, offset, rowHeight, height-top-bottom, false),
        "Trailing page now covers the viewport");
      layout.scrollToPositionWithOffset(anchor, offset);
      measure(list, width, height);
      equal(top + offset, layout.findViewByPosition(anchor).getTop(), "Original visible anchor is restored exactly");
      count[0] = 90;
      adapter.notifyItemRangeInserted(60, 30);
      measure(list, width, height);
      equal(top + offset, layout.findViewByPosition(anchor).getTop(), "Later pages do not scroll the rail");
    } finally { list.setAdapter(null); }
  }
}
