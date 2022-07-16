package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.reactions.LottieAnimationDrawable;
import org.thunderdog.challegram.reactions.LottieAnimation;
import org.thunderdog.challegram.reactions.LottieAnimationThreadPool;
import org.thunderdog.challegram.reactions.ReactionAnimationOverlay;
import org.thunderdog.challegram.reactions.SimplestCheckboxView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ImageReceiverView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class ReactionListBaseController<T> extends RecyclerViewController<T>{
	protected List<TdApi.Reaction> allReactions;
	protected SettingsAdapter topAdapter;
	protected ReactionsAdapter reactionsAdapter=new ReactionsAdapter();
	protected ConcatAdapter actualAdapter;
	protected HashSet<String> selectedReactions=new HashSet<>();
	protected ReactionAnimationOverlay animationOverlay;

	public ReactionListBaseController(Context context, Tdlib tdlib){
		super(context, tdlib);
		allReactions=tdlib.getSupportedReactions();
		animationOverlay=new ReactionAnimationOverlay(this);
	}

	@Override
	protected void onCreateView(Context context, CustomRecyclerView recyclerView){
		topAdapter=new SettingsAdapter(this){
			@Override
			protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate){
				ReactionListBaseController.this.setValuedSetting(item, view, isUpdate);
			}
		};
		ArrayList<ListItem> items=new ArrayList<>();
		onPopulateTopItems(items);
		topAdapter.setItems(items, false);

		GridLayoutManager glm=new GridLayoutManager(context, 4);
		glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){
			@Override
			public int getSpanSize(int position){
				return position<topAdapter.getItemCount() ? glm.getSpanCount() : 1;
			}
		});
		recyclerView.setLayoutManager(glm);
		actualAdapter=new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(), topAdapter, reactionsAdapter);
		recyclerView.setAdapter(actualAdapter);

		recyclerView.addItemDecoration(new RecyclerView.ItemDecoration(){
			private Paint paint=new Paint();
			@Override
			public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
				paint.setColor(Theme.fillingColor());
				RecyclerView.ViewHolder holder=parent.findViewHolderForAdapterPosition(topAdapter.getItemCount());
				c.drawRect(0, holder==null ? 0 : holder.itemView.getY(), parent.getWidth(), parent.getHeight(), paint);
			}
		});

		recyclerView.setPadding(0, 0, 0, Screen.dp(16));
		recyclerView.setClipToPadding(false);

		addThemeInvalidateListener(recyclerView);
	}

	protected abstract void onPopulateTopItems(List<ListItem> outItems);
	protected abstract boolean onReactionSelected(String reaction);
	protected abstract void onSelectedReactionsChanged();
	protected void onReactionUnselected(String reaction){ }

	protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate){ }

	protected class ReactionsAdapter extends RecyclerView.Adapter<ReactionCellViewHolder>{

		@NonNull
		@Override
		public ReactionCellViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new ReactionCellViewHolder();
		}

		@Override
		public void onBindViewHolder(@NonNull ReactionCellViewHolder holder, int position){
			holder.bind(allReactions.get(position));
		}

		@Override
		public int getItemCount(){
			return allReactions.size();
		}

		@Override
		public int getItemViewType(int position){
			return -1000;
		}
	}

	protected class ReactionCellViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnAttachStateChangeListener{
		private ImageReceiverView icon;
		private TextView text;
		private SimplestCheckboxView check;
		private View animation;
		private boolean animating;

		private TdApi.Reaction reaction;
    private boolean isAttached;

		public ReactionCellViewHolder(){
			super(View.inflate(context, R.layout.item_reaction_settings, null));
			icon=itemView.findViewById(R.id.reaction);
			text=itemView.findViewById(R.id.text);
			check=itemView.findViewById(R.id.checkbox);
			animation=itemView.findViewById(R.id.reaction_animation);
			animation.setVisibility(View.INVISIBLE);
			itemView.setOnClickListener(this);
			text.setTextColor(Theme.getColor(R.id.theme_color_text));
			addThemeTextColorListener(text, R.id.theme_color_text);
			addThemeInvalidateListener(check);
      itemView.addOnAttachStateChangeListener(this);
		}

		public void bind(TdApi.Reaction reaction){
			this.reaction=reaction;
			TGStickerObj sticker=new TGStickerObj(tdlib, reaction.staticIcon, null, reaction.staticIcon.type);
			icon.getReceiver().requestFile(sticker.getFullImage());
			text.setText(reaction.title);
			endAnimation();
			updateState(false);
		}

		protected void updateState(boolean animated){
			boolean selected=selectedReactions.contains(reaction.reaction);
			if(animated){
				icon.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
				animation.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
				text.animate().alpha(selected ? 1f : .45f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
			}else{
				icon.setAlpha(selected ? 1f : .45f);
				text.setAlpha(selected ? 1f : .45f);
			}
			check.setChecked(selected, animated);
		}

		@Override
		public void onClick(View v){
			if(selectedReactions.contains(reaction.reaction)){
				selectedReactions.remove(reaction.reaction);
				onSelectedReactionsChanged();
				updateState(true);
				endAnimation();
			}else if(onReactionSelected(reaction.reaction)){
				selectedReactions.add(reaction.reaction);
				onSelectedReactionsChanged();
				updateState(true);

        LottieAnimationThreadPool.loadMultipleAnimations(tdlib, anims->{
          LottieAnimation center=anims[0];
          if(center!=null){
            icon.setVisibility(View.INVISIBLE);
            animation.setVisibility(View.VISIBLE);
            LottieAnimationDrawable anim=new LottieAnimationDrawable(center, 500, 500);
            anim.setOnEnd(this::endAnimation);
            animation.setBackground(anim);
            anim.start();
          }
          LottieAnimation effect=anims[1];
          if(effect!=null && !animating){
            int[] loc={0, 0};
            animationOverlay.playLottieAnimation(outRect->{
              if(!isAttached)
                return false;
              icon.getLocationOnScreen(loc);
              outRect.set(loc[0], loc[1], loc[0]+icon.getWidth(), loc[1]+icon.getHeight());
              int width=outRect.width();
              int centerX=outRect.centerX();
              int centerY=outRect.centerY();
              int size=Math.round(width*2f);
              outRect.set(centerX-size, centerY-size, centerX+size, centerY+size);
              return true;
            }, effect, ()->animating=true, (_v, remove)->{
              animating=false;
              remove.run();
            });
          }
        }, 1000, reaction.centerAnimation, reaction.aroundAnimation);
			}
		}

		private void endAnimation(){
			animation.setBackground(null);
			animation.setVisibility(View.INVISIBLE);
			icon.setVisibility(View.VISIBLE);
		}

    @Override
    public void onViewAttachedToWindow (View v){
      isAttached=true;
    }

    @Override
    public void onViewDetachedFromWindow (View v){
      isAttached=false;
    }
  }
}
