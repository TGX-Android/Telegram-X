package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionsSelectorRecyclerView extends RecyclerView {

  public ReactionsSelectorRecyclerView (@NonNull Context context) {
    super(context);
  }

  public void setMessage (TGMessage message) {
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false) {
      @Override
      protected boolean isLayoutRTL () {
        return false;
      }
    };

    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    setPadding(Screen.dp(9), Screen.dp(7), Screen.dp(9), Screen.dp(7));
    setClipToPadding(false);
    setHasFixedSize(true);
    addItemDecoration(new ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
        outRect.set(Screen.dp(-1), 0, Screen.dp(-1), 0);
      }
    });

    setLayoutManager(linearLayoutManager);
    setAdapter(adapter = new ReactionsAdapter(getContext(), message));
  }

  ReactionsAdapter adapter;
  public void setDelegate (ReactionSelectDelegate delegate) {
    adapter.setDelegate(delegate);
  }

  private static class ReactionView extends FrameLayoutFix {
    public StickerSmallView stickerView;
    private Counter counter;
    private boolean chosen;
    private boolean useCounter;
    private TGStickerObj centerAnimationSicker;
    private RectF rectF;

    public ReactionView (Context context) {
      super(context);
      stickerView = new StickerSmallView(context, 0) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent event) {
          return false;
        }
      };
      stickerView.setLayoutParams(newParams(Screen.dp(40), Screen.dp(40), Gravity.LEFT | Gravity.CENTER_VERTICAL));
      addView(stickerView);

      rectF = new RectF();
      counter = new Counter.Builder()
        .noBackground()
        .textColor(ColorId.fillingPositiveContent, ColorId.text, ColorId.text)
        .textSize(13f)
        .allBold(false)
        .callback(this)
        .build();
    }

    public void setReaction (TGReaction reaction, TdApi.MessageReaction messageReaction, boolean useCounter) {
      this.useCounter = useCounter;
      this.chosen = messageReaction.isChosen;
      this.centerAnimationSicker = reaction.newCenterAnimationSicker();
      this.playAnimation();
      stickerView.setSticker(centerAnimationSicker);
      if (useCounter) {
        counter.setCount(messageReaction.totalCount, !messageReaction.isChosen, false);
      }
      requestLayout();
    }

    public void playAnimation () {
      if (centerAnimationSicker != null && centerAnimationSicker.getPreviewAnimation() != null && !centerAnimationSicker.isCustomReaction()) {
        centerAnimationSicker.getPreviewAnimation().setPlayOnce(true);
        centerAnimationSicker.getPreviewAnimation().setLooped(false);
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = Screen.dp(40f);
      int padding = Screen.dp(1);
      if (useCounter) {
        width += counter.getScaledWidth(Screen.dp(6));
      }

      rectF.set(0, Screen.dp(1), width, Screen.dp(39));

      super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(40), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (chosen) {
        c.drawRoundRect(rectF, Screen.dp(19), Screen.dp(19), Paints.fillingPaint(Theme.getColor(ColorId.fillingPositive)));
      }
      if (useCounter) {
        counter.draw(c, Screen.dp(36), getMeasuredHeight() / 2f, Gravity.LEFT, 1f);
      }
      super.dispatchDraw(c);
    }
  }

  private static class ReactionHolder extends RecyclerView.ViewHolder {
    public ReactionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static ReactionHolder create (Context context, Tdlib tdlib) {
      ReactionView reactionView = new ReactionView(context);
      reactionView.stickerView.init(tdlib);
      reactionView.setId(R.id.btn_reactionSelector);
      //reactionView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
      return new ReactionHolder(reactionView);
    }
  }

  private static class ReactionsAdapter extends RecyclerView.Adapter<ReactionHolder> {
    private final Tdlib tdlib;
    private final Context context;
    private final TGMessage message;
    private final TdApi.AvailableReaction[] reactions;
    private final Set<String> chosen;
    private ReactionSelectDelegate delegate;

    ReactionsAdapter (Context context, TGMessage message) {
      this.context = context;
      this.tdlib = message.tdlib();
      this.message = message;
      this.chosen = message.getMessageReactions().getChosen();
      this.reactions = prioritizeElements(message.getMessageAvailableReactions(), chosen);
    }

    public void setDelegate (ReactionSelectDelegate delegate) {
      this.delegate = delegate;
    }

    @NonNull
    @Override
    public ReactionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return ReactionHolder.create(context, tdlib);
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionHolder holder, int position) {
      TdApi.ReactionType reactionType = reactions[position].type;
      TGReaction reaction = tdlib.getReaction(reactionType);
      TdApi.MessageReaction tdReaction = message.getMessageReactions().getTdMessageReaction(reactionType);
      ReactionView view = (ReactionView) holder.itemView;
      if (reaction == null) return;

      final boolean needUseCounter = (message.isChannel() || !message.canGetAddedReactions()) && !message.useReactionBubbles();
      view.setReaction(reaction, tdReaction, needUseCounter);
      view.setOnClickListener((v) -> {
        if (delegate != null) {
          delegate.onClick(v, reaction);
        }
      });
      view.setOnLongClickListener((v) -> {
        if (delegate != null) {
          delegate.onLongClick(v, reaction);
        }
        return true;
      });
    }

    @Override
    public int getItemCount () {
      return reactions != null ? reactions.length : 0;
    }

    @Override
    public void onViewAttachedToWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.attach();
      ((ReactionView) holder.itemView).playAnimation();
    }

    @Override
    public void onViewDetachedFromWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.detach();
    }

    @Override
    public void onViewRecycled (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.performDestroy();
    }
  }

  public interface ReactionSelectDelegate {
    void onClick (View v, TGReaction reaction);
    void onLongClick (View v, TGReaction reaction);
  }

  public static TdApi.AvailableReaction[] prioritizeElements(TdApi.AvailableReaction[] inputArray, Set<String> set) {
    if (inputArray == null) {
      return null;
    }

    List<TdApi.AvailableReaction> resultList = new ArrayList<>();

    for (TdApi.AvailableReaction element : inputArray) {
      if (set.contains(TD.makeReactionKey(element.type))) {
        resultList.add(element);
      }
    }

    for (TdApi.AvailableReaction element : inputArray) {
      if (!set.contains(TD.makeReactionKey(element.type))) {
        resultList.add(element);
      }
    }

    TdApi.AvailableReaction[] resultArray = new TdApi.AvailableReaction[resultList.size()];
    resultList.toArray(resultArray);

    return resultArray;
  }
}
