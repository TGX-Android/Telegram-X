package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.reactions.LottieAnimationDrawable;
import org.thunderdog.challegram.reactions.LottieAnimation;
import org.thunderdog.challegram.reactions.LottieAnimationThreadPool;
import org.thunderdog.challegram.reactions.ReactionAnimationOverlay;
import org.thunderdog.challegram.reactions.SimplestCheckboxView;
import org.thunderdog.challegram.reactions.StickerReceiverView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class ReactionListBaseController<T> extends RecyclerViewController<T> {
  protected List<TdApi.Reaction> regularReactions, premiumReactions;
  protected SettingsAdapter topAdapter, premiumSectionHeaderAdapter;
  protected ReactionsAdapter reactionsAdapter, premiumReactionsAdapter;
  protected ConcatAdapter actualAdapter;
  protected ArrayList<String> selectedReactions = new ArrayList<>();
  protected ReactionAnimationOverlay animationOverlay;
  protected boolean useCounter, allowPremiumReactionsAnyway;

  public ReactionListBaseController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    List<TdApi.Reaction> reactions = tdlib.getSupportedReactions();
    regularReactions = reactions.stream().filter(r -> !r.isPremium).collect(Collectors.toList());
    premiumReactions = reactions.stream().filter(r -> r.isPremium).collect(Collectors.toList());
    animationOverlay = new ReactionAnimationOverlay(this);
    reactionsAdapter = new ReactionsAdapter(regularReactions);
    premiumReactionsAdapter = new ReactionsAdapter(premiumReactions);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    topAdapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        ReactionListBaseController.this.setValuedSetting(item, view, isUpdate);
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    onPopulateTopItems(items);
    topAdapter.setItems(items, false);

    premiumSectionHeaderAdapter = new SettingsAdapter(this) {
      @Override
      protected void setHeaderText (ListItem item, TextView view, boolean isUpdate) {
        super.setHeaderText(item, view, isUpdate);
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), Screen.dp(48), view.getPaddingBottom());
      }
    };
    ListItem premiumHeader;
    premiumSectionHeaderAdapter.setItems(Arrays.asList(
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      premiumHeader = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PremiumReactions),
      new ListItem(ListItem.TYPE_SHADOW_TOP)
    ), false);

    GridLayoutManager glm = new GridLayoutManager(context, 4);
    glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return actualAdapter.getItemViewType(position) >= 0 ? glm.getSpanCount() : 1;
      }
    });
    recyclerView.setLayoutManager(glm);
    actualAdapter = new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(), topAdapter, reactionsAdapter, premiumSectionHeaderAdapter, premiumReactionsAdapter);
    recyclerView.setAdapter(actualAdapter);

    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      private Paint paint = new Paint();
      private Rect rect = new Rect();

      @Override
      public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        paint.setColor(Theme.fillingColor());
        RecyclerView.ViewHolder top = parent.findViewHolderForAdapterPosition(topAdapter.getItemCount());
        RecyclerView.ViewHolder bottom = parent.findViewHolderForAdapterPosition(topAdapter.getItemCount() + reactionsAdapter.getItemCount() - 1);
        if (top != null || bottom != null) {
          if (bottom != null)
            parent.getDecoratedBoundsWithMargins(bottom.itemView, rect);
          c.drawRect(0, top == null ? 0 : top.itemView.getTop(), parent.getWidth(), bottom == null ? parent.getHeight() : rect.bottom, paint);
        }
        top = parent.findViewHolderForAdapterPosition(topAdapter.getItemCount() + reactionsAdapter.getItemCount() + premiumSectionHeaderAdapter.getItemCount());
        bottom = parent.findViewHolderForAdapterPosition(actualAdapter.getItemCount() - 1);
        if (top != null || bottom != null) {
          c.drawRect(0, top == null ? 0 : top.itemView.getTop(), parent.getWidth(), parent.getHeight(), paint);
        }
      }

      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos > topAdapter.getItemCount() && pos < topAdapter.getItemCount() + reactionsAdapter.getItemCount()) {
          pos -= topAdapter.getItemCount();
          if (pos >= reactionsAdapter.getItemCount() / 4 * 4) {
            outRect.bottom = Screen.dp(16);
          }
        }
      }
    });

    if (!tdlib.hasPremium() && !allowPremiumReactionsAnyway) {
      recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
        private Drawable lockIcon = Drawables.get(R.drawable.baseline_lock_16);

        @Override
        public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
          RecyclerView.ViewHolder holder = parent.findViewHolderForAdapterPosition(topAdapter.getItemCount() + reactionsAdapter.getItemCount() + 1);
          if (holder != null) {
            Paint paint = PorterDuffPaint.get(R.id.theme_color_text);
            Drawables.drawCentered(c, lockIcon, parent.getWidth() - Screen.dp(24), holder.itemView.getTop() + holder.itemView.getHeight() / 2f, paint);
          }
        }
      });
    }

    recyclerView.setPadding(0, 0, 0, Screen.dp(16));
    recyclerView.setClipToPadding(false);

    addThemeInvalidateListener(recyclerView);
  }

  protected abstract void onPopulateTopItems (List<ListItem> outItems);

  protected abstract boolean onReactionSelected (String reaction);

  protected abstract void onSelectedReactionsChanged ();

  protected void onReactionUnselected (String reaction) {}

  protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
    view.setDrawModifier(item.getDrawModifier());
  }

  protected class ReactionsAdapter extends RecyclerView.Adapter<ReactionCellViewHolder> {
    private final List<TdApi.Reaction> reactions;

    public ReactionsAdapter (List<TdApi.Reaction> reactions) {
      this.reactions = reactions;
    }

    @NonNull
    @Override
    public ReactionCellViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return new ReactionCellViewHolder();
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionCellViewHolder holder, int position) {
      holder.bind(reactions.get(position));
    }

    @Override
    public int getItemCount () {
      return reactions.size();
    }

    @Override
    public int getItemViewType (int position) {
      return -1000;
    }
  }

  protected class ReactionCellViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnAttachStateChangeListener {
    private StickerReceiverView icon;
    private TextView text;
    private SimplestCheckboxView check;
    private View animation;
    private boolean animating;

    private TdApi.Reaction reaction;
    private boolean isAttached;

    public ReactionCellViewHolder () {
      super(View.inflate(context, R.layout.item_reaction_settings, null));
      icon = itemView.findViewById(R.id.reaction);
      text = itemView.findViewById(R.id.text);
      check = itemView.findViewById(R.id.checkbox);
      animation = itemView.findViewById(R.id.reaction_animation);
      animation.setVisibility(View.INVISIBLE);
      itemView.setOnClickListener(this);
      text.setTextColor(Theme.getColor(R.id.theme_color_text));
      addThemeTextColorListener(text, R.id.theme_color_text);
      addThemeInvalidateListener(check);
      itemView.addOnAttachStateChangeListener(this);
    }

    public void bind (TdApi.Reaction reaction) {
      this.reaction = reaction;
      icon.loadSticker(tdlib, reaction.staticIcon, false);
      text.setText(reaction.title);
      endAnimation();
      updateState(false);
    }

    protected void updateState (boolean animated) {
      boolean selected = selectedReactions.contains(reaction.reaction);
      if (animated) {
        icon.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        animation.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        text.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
      } else {
        icon.setAlpha(selected ? 1f : .45f);
        text.setAlpha(selected ? 1f : .45f);
      }
      if (selected && useCounter) {
        check.setCounter(Strings.buildCounter(selectedReactions.indexOf(reaction.reaction) + 1));
      }
      check.setChecked(selected, animated);
    }

    @Override
    public void onClick (View v) {
      if (reaction.isPremium && !tdlib.hasPremium() && !allowPremiumReactionsAnyway) {
        RecyclerView.ViewHolder headerHolder = getRecyclerView().findViewHolderForAdapterPosition(topAdapter.getItemCount() + reactionsAdapter.getItemCount() + 1);
        CharSequence str = Strings.buildMarkdown(ReactionListBaseController.this, Lang.getString(R.string.PremiumReactionsTooltip), null);
        if (headerHolder != null) {
          context.tooltipManager().builder(headerHolder.itemView).locate((targetView, outRect) -> {
            int x = targetView.getWidth() - Screen.dp(32);
            int y = targetView.getHeight() / 2 - Screen.dp(14);
            outRect.set(x, y, x + Screen.dp(16), y + Screen.dp(16));
          }).show(tdlib, str).hideDelayed();
        } else {
          Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
        }
        return;
      }
      if (selectedReactions.contains(reaction.reaction)) {
        selectedReactions.remove(reaction.reaction);
        onSelectedReactionsChanged();
        updateState(true);
        endAnimation();

        if (useCounter) {
          RecyclerView list = getRecyclerView();
          for (int i = 0; i < list.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = list.getChildViewHolder(list.getChildAt(i));
            if (holder instanceof ReactionListBaseController.ReactionCellViewHolder) {
              ReactionCellViewHolder vh = (ReactionCellViewHolder) holder;
              if (selectedReactions.contains(vh.reaction.reaction)) {
                vh.updateState(false);
              }
            }
          }
        }
      } else if (onReactionSelected(reaction.reaction)) {
        selectedReactions.add(reaction.reaction);
        onSelectedReactionsChanged();
        updateState(true);

        LottieAnimationThreadPool.loadMultipleAnimations(tdlib, anims -> {
          LottieAnimation center = anims[0];
          if (center != null) {
            icon.setVisibility(View.INVISIBLE);
            animation.setVisibility(View.VISIBLE);
            LottieAnimationDrawable anim = new LottieAnimationDrawable(center, 500, 500);
            anim.setOnEnd(this::endAnimation);
            animation.setBackground(anim);
            anim.start();
          }
          LottieAnimation effect = anims[1];
          if (effect != null && !animating) {
            int[] loc = {0, 0};
            animationOverlay.playLottieAnimation(outRect -> {
              if (!isAttached)
                return false;
              icon.getLocationOnScreen(loc);
              outRect.set(loc[0], loc[1], loc[0] + icon.getWidth(), loc[1] + icon.getHeight());
              int width = outRect.width();
              int centerX = outRect.centerX();
              int centerY = outRect.centerY();
              int size = Math.round(width * 2f);
              outRect.set(centerX - size, centerY - size, centerX + size, centerY + size);
              return true;
            }, effect, () -> animating = true, (_v, remove) -> {
              animating = false;
              remove.run();
            });
          }
        }, 1000, reaction.centerAnimation, reaction.aroundAnimation);
      }
    }

    private void endAnimation () {
      animation.setBackground(null);
      animation.setVisibility(View.INVISIBLE);
      icon.setVisibility(View.VISIBLE);
    }

    @Override
    public void onViewAttachedToWindow (View v) {
      isAttached = true;
    }

    @Override
    public void onViewDetachedFromWindow (View v) {
      isAttached = false;
    }
  }
}
