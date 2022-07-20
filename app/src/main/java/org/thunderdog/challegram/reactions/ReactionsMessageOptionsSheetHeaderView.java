package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vkryl.android.util.InvalidateDelegate;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerEntry;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ReactionsMessageOptionsSheetHeaderView extends LinearLayout {
  private MessagesController controller;
  private List<TdApi.Reaction> reactions;
  private Tdlib tdlib;
  private TGMessage message;
  private PopupLayout popupLayout;

  private HorizontalScrollView scrollView;
  private LinearLayout scrollContent;
  private LinearLayout countersView;
  private CounterView reactionsCounter, seenCounter;
  private TdApi.Users viewers;
  private ReactionListViewController currentReactionList;

  private ThemeListenerList themeListeners = new ThemeListenerList();
  private ArrayList<LottieAnimation> animations = new ArrayList<>();
  private HashMap<String, CancellableRunnable> loadingAnimations = new HashMap<>();

  private boolean reactionAlreadyClicked;

  public ReactionsMessageOptionsSheetHeaderView (Context context, MessagesController controller, TGMessage message, PopupLayout popupLayout, List<String> availableReactions) {
    super(context);
    this.popupLayout = popupLayout;
    setOrientation(HORIZONTAL);
    setBackgroundColor(Theme.getColor(R.id.theme_color_background));

    this.controller = controller;
    this.message = message;

    tdlib = controller.tdlib();
    reactions = availableReactions.stream().map(tdlib::getReaction).collect(Collectors.toList());

    scrollView = new HorizontalScrollView(context) {
      @Override
      protected float getLeftFadingEdgeStrength () {
        return 0f;
      }
    };
    scrollView.setHorizontalScrollBarEnabled(false);
    scrollView.setFadingEdgeLength(Screen.dp(36));
    addView(scrollView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    scrollContent = new LinearLayout(context);
    scrollContent.setOrientation(HORIZONTAL);
    scrollView.addView(scrollContent);

    int vpad = Screen.dp(12), hpad = Screen.dp(6);
    scrollContent.setPadding(hpad, 0, hpad, 0);
    String chosenReaction = null;
    HashMap<String, Integer> reactionCounts = new HashMap<>();
    for (TdApi.MessageReaction mr : message.getReactions()) {
      if (mr.isChosen) {
        chosenReaction = mr.reaction;
      }
      reactionCounts.put(mr.reaction, mr.totalCount);
    }
    View _chosenButton = null;
    boolean needCounters = message.needDrawReactionsWithTime() && !tdlib.isUserChat(controller.getChatId());
    for (TdApi.Reaction r : reactions) {
      LinearLayout btn = new LinearLayout(context);
      btn.setOrientation(HORIZONTAL);
      ImageView gifView = new ImageView(context);
      CancellableRunnable cr = LottieAnimationThreadPool.loadOneAnimation(tdlib, tdlib.getReaction(r.reaction).appearAnimation, la -> {
        loadingAnimations.remove(r.reaction);
        animations.add(la);
        LottieAnimationDrawable drawable = new LottieAnimationDrawable(la, Screen.dp(24), Screen.dp(24));
        gifView.setImageDrawable(drawable);
        drawable.start();
      }, Screen.dp(24), Screen.dp(24));
      loadingAnimations.put(r.reaction, cr);
      btn.setTag(r);
      btn.setOnClickListener(this::onReactionClick);
      btn.setOnLongClickListener(this::onReactionLongClick);
      btn.addView(gifView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 6, 0, 6, 0));
      boolean isChosen = r.reaction.equals(chosenReaction);
      if (isChosen) {
        btn.setBackground(new ChosenReactionBackgroundDrawable());
        themeListeners.addThemeInvalidateListener(btn);
        _chosenButton = btn;
      }

      int count = reactionCounts.containsKey(r.reaction) ? reactionCounts.get(r.reaction) : 0;
      if (needCounters && count > 0) {
        TextView counter = new TextView(context);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        counter.setTextColor(Theme.getColor(isChosen ? R.id.theme_color_fillingPositiveContent : R.id.theme_color_text));
        themeListeners.add(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, isChosen ? R.id.theme_color_fillingPositiveContent : R.id.theme_color_text, counter));
        counter.setTypeface(Fonts.getRobotoMedium());
        counter.setText(Strings.buildCounter(count));
        btn.addView(counter, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, -2, 0, 9, 0));
      }

      scrollContent.addView(btn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48)));
    }

    final View chosenButton = _chosenButton;
    if (chosenButton != null) {
      scrollView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw () {
          scrollView.getViewTreeObserver().removeOnPreDrawListener(this);

          int scrollX = chosenButton.getLeft() + chosenButton.getWidth() / 2 - scrollView.getWidth() / 2;
          scrollView.scrollTo(Math.max(0, scrollX), 0);

          return true;
        }
      });
    }

    countersView = new LinearLayout(context);
    countersView.setOrientation(HORIZONTAL);
    countersView.setPadding(Screen.dp(10), 0, Screen.dp(10), 0);
    countersView.setOnClickListener(v -> onCountersClick());
    RippleSupport.setTransparentSelector(countersView);
    addView(countersView, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

    reactionsCounter = makeAndAddCounterView(R.drawable.baseline_favorite_14);
    seenCounter = makeAndAddCounterView(R.drawable.baseline_visibility_14);
    seenCounter.setVisibility(GONE);

    if (message.canGetAddedReactions()) {
      int count = message.getTotalReactionCount();
      if (count > 0) {
        reactionsCounter.counter.setCount(count, false);
      } else {
        reactionsCounter.setVisibility(GONE);
      }
    } else {
      reactionsCounter.setVisibility(GONE);
    }
    updateCountersVisibility();
    if (message.canGetViewers()) {
      loadViewers();
    }

    popupLayout.setBackListener(new BackListener() {
      @Override
      public boolean onBackPressed (boolean fromTop) {
        if (currentReactionList != null) {
          dismissReactionList();
          return true;
        }
        return false;
      }
    });

    themeListeners.addThemeBackgroundColorListener(this, R.id.theme_color_background);
    themeListeners.addThemeInvalidateListener(reactionsCounter);
    themeListeners.addThemeInvalidateListener(seenCounter);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    controller.context().addGlobalThemeListeners(themeListeners);
  }

  @Override
  protected void onDetachedFromWindow () {
    controller.context().removeGlobalThemeListeners(themeListeners);
    for (CancellableRunnable cr : loadingAnimations.values()) {
      cr.cancel();
    }
    for (LottieAnimation anim : animations) {
      anim.release();
    }
    super.onDetachedFromWindow();
  }

  private CounterView makeAndAddCounterView (@DrawableRes int icon) {
    CounterView view = new CounterView(getContext(), new Counter.Builder()
//				.textSize(12.5f)
      .textColor(R.id.theme_color_text)
      .drawable(icon, 14f, 3f, Gravity.LEFT)
      .noBackground(), R.id.theme_color_icon);

    LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    lp.rightMargin = lp.leftMargin = Screen.dp(6);
    countersView.addView(view, lp);
    return view;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, Screen.dp(48) | MeasureSpec.EXACTLY);
  }

  private void updateCountersVisibility () {
    if (reactionsCounter.getVisibility() == VISIBLE || seenCounter.getVisibility() == VISIBLE) {
      countersView.setVisibility(VISIBLE);
      scrollView.setHorizontalFadingEdgeEnabled(true);
    } else {
      countersView.setVisibility(GONE);
      scrollView.setHorizontalFadingEdgeEnabled(false);
    }
  }

  private void loadViewers () {
    tdlib.send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), res -> {
      if (res instanceof TdApi.Users) {
        viewers = (TdApi.Users) res;
        post(() -> {
          if (viewers.totalCount > 0) {
            seenCounter.counter.setCount(viewers.totalCount, false);
            seenCounter.setVisibility(VISIBLE);
            updateCountersVisibility();
          }
        });
      }
    });
  }

  private void onCountersClick () {
    OptionsLayout parent = (OptionsLayout) getParent();

    ReactionListViewController rl = new ReactionListViewController(getContext(), popupLayout, message, controller, viewers, this::dismissReactionList);
    rl.showFromOptionsSheet(reactionsCounter, seenCounter, parent, countersView, () -> {
      currentReactionList = rl;
    });
  }

  private void dismissReactionList () {
    OptionsLayout parent = (OptionsLayout) getParent();
    currentReactionList.dismissFromOptionsSheet(reactionsCounter, seenCounter, parent, countersView);
    currentReactionList = null;
  }

  private void onReactionClick (View v) {
    if (reactionAlreadyClicked)
      return;
    reactionAlreadyClicked = true;
    TdApi.Reaction r = (TdApi.Reaction) v.getTag();
    controller.sendMessageReaction(message, r.reaction, (ImageView) ((ViewGroup) v).getChildAt(0), null, popupLayout, false);
  }

  private boolean onReactionLongClick (View v) {
    if (reactionAlreadyClicked)
      return false;
    reactionAlreadyClicked = true;
    TdApi.Reaction r = (TdApi.Reaction) v.getTag();
    controller.sendMessageReaction(message, r.reaction, (ImageView) ((ViewGroup) v).getChildAt(0), null, popupLayout, true);
    return true;
  }

  private static class ChosenReactionBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    @Override
    public void draw (@NonNull Canvas canvas) {
      paint.setColor(Theme.getColor(R.id.theme_color_file));
      int radius = Screen.dp(18);
      Rect bounds = getBounds();
      rect.set(bounds.left, bounds.centerY() - radius, bounds.right, bounds.centerY() + radius);
      canvas.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) {

    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
