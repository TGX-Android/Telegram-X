package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ReactionsMessageOptionsSheetHeaderView extends LinearLayout{
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

	private ThemeListenerList themeListeners=new ThemeListenerList();
  private ArrayList<LottieAnimation> animations=new ArrayList<>();
  private HashMap<String, CancellableRunnable> loadingAnimations=new HashMap<>();

  private boolean reactionAlreadyClicked;

	public ReactionsMessageOptionsSheetHeaderView(Context context, MessagesController controller, TGMessage message, PopupLayout popupLayout, List<String> availableReactions){
		super(context);
		this.popupLayout=popupLayout;
		setOrientation(HORIZONTAL);
		setBackgroundColor(Theme.getColor(R.id.theme_color_background));

		this.controller=controller;
		this.message=message;

		tdlib=controller.tdlib();
		reactions=availableReactions.stream().map(tdlib::getReaction).collect(Collectors.toList());

		scrollView=new HorizontalScrollView(context){
			@Override
			protected float getLeftFadingEdgeStrength(){
				return 0f;
			}
		};
		scrollView.setHorizontalScrollBarEnabled(false);
		scrollView.setFadingEdgeLength(Screen.dp(36));
		addView(scrollView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		scrollContent=new LinearLayout(context);
		scrollContent.setOrientation(HORIZONTAL);
		scrollView.addView(scrollContent);

		int vpad=Screen.dp(12), hpad=Screen.dp(6);
		scrollContent.setPadding(hpad, 0, hpad, 0);
		String chosenReaction=null;
		for(TdApi.MessageReaction mr:message.getReactions()){
			if(mr.isChosen){
				chosenReaction=mr.reaction;
				break;
			}
		}
		for(TdApi.Reaction r:reactions){
			FrameLayout btn=new FrameLayout(context);
			ImageView gifView=new ImageView(context);
      CancellableRunnable cr=LottieAnimationThreadPool.loadOneAnimation(tdlib, tdlib.getReaction(r.reaction).appearAnimation, la->{
        loadingAnimations.remove(r.reaction);
        animations.add(la);
        LottieAnimationDrawable drawable=new LottieAnimationDrawable(la, Screen.dp(24), Screen.dp(24));
        gifView.setImageDrawable(drawable);
        drawable.start();
      }, Screen.dp(24), Screen.dp(24));
      loadingAnimations.put(r.reaction, cr);
			btn.setTag(r);
			btn.setOnClickListener(this::onReactionClick);
			btn.setOnLongClickListener(this::onReactionLongClick);
			btn.addView(gifView, new FrameLayout.LayoutParams(Screen.dp(24), Screen.dp(24), Gravity.CENTER));
			if(r.reaction.equals(chosenReaction)){
				btn.setBackground(new ChosenReactionBackgroundDrawable());
				themeListeners.addThemeInvalidateListener(btn);
			}

			scrollContent.addView(btn, new LinearLayout.LayoutParams(Screen.dp(36), Screen.dp(48)));
		}

		countersView=new LinearLayout(context);
		countersView.setOrientation(HORIZONTAL);
		countersView.setPadding(Screen.dp(10), 0, Screen.dp(10), 0);
		countersView.setOnClickListener(v->onCountersClick());
		RippleSupport.setTransparentSelector(countersView);
		addView(countersView, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

		reactionsCounter=makeAndAddCounterView(R.drawable.baseline_favorite_14);
		seenCounter=makeAndAddCounterView(R.drawable.baseline_visibility_14);
		seenCounter.setVisibility(GONE);

		if(message.canGetAddedReactions()){
			int count=message.getTotalReactionCount();
			if(count>0){
				reactionsCounter.counter.setCount(count, false);
			}else{
				reactionsCounter.setVisibility(GONE);
			}
		}else{
			reactionsCounter.setVisibility(GONE);
		}
		updateCountersVisibility();
		if(message.canGetViewers()){
			loadViewers();
		}

		popupLayout.setBackListener(new BackListener(){
			@Override
			public boolean onBackPressed(boolean fromTop){
				if(currentReactionList!=null){
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
	protected void onAttachedToWindow(){
		super.onAttachedToWindow();
		controller.context().addGlobalThemeListeners(themeListeners);
	}

	@Override
	protected void onDetachedFromWindow(){
		controller.context().removeGlobalThemeListeners(themeListeners);
    for(CancellableRunnable cr : loadingAnimations.values()){
      cr.cancel();
    }
    for(LottieAnimation anim:animations){
      anim.release();
    }
		super.onDetachedFromWindow();
	}

	private CounterView makeAndAddCounterView(@DrawableRes int icon){
		CounterView view=new CounterView(getContext(), new Counter.Builder()
//				.textSize(12.5f)
				.textColor(R.id.theme_color_text)
				.drawable(icon, 14f, 3f, Gravity.LEFT)
				.noBackground(), R.id.theme_color_icon);

		LayoutParams lp=new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
		lp.rightMargin=lp.leftMargin=Screen.dp(6);
		countersView.addView(view, lp);
		return view;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		super.onMeasure(widthMeasureSpec, Screen.dp(48) | MeasureSpec.EXACTLY);
	}

	private void updateCountersVisibility(){
		if(reactionsCounter.getVisibility()==VISIBLE || seenCounter.getVisibility()==VISIBLE){
			countersView.setVisibility(VISIBLE);
			scrollView.setHorizontalFadingEdgeEnabled(true);
		}else{
			countersView.setVisibility(GONE);
			scrollView.setHorizontalFadingEdgeEnabled(false);
		}
	}

	private void loadViewers(){
		tdlib.send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), res->{
			if(res instanceof TdApi.Users){
				viewers=(TdApi.Users) res;
				post(()->{
					if(viewers.totalCount>0){
						seenCounter.counter.setCount(viewers.totalCount, false);
						seenCounter.setVisibility(VISIBLE);
						updateCountersVisibility();
					}
				});
			}
		});
	}

	private void onCountersClick(){
		OptionsLayout parent=(OptionsLayout) getParent();

		ReactionListViewController rl=new ReactionListViewController(getContext(), popupLayout, message, controller, viewers, this::dismissReactionList);
		rl.showFromOptionsSheet(reactionsCounter, seenCounter, parent, countersView, ()->{
			currentReactionList=rl;
		});
	}

	private void dismissReactionList(){
		OptionsLayout parent=(OptionsLayout) getParent();
		currentReactionList.dismissFromOptionsSheet(reactionsCounter, seenCounter, parent, countersView);
		currentReactionList=null;
	}

	private void onReactionClick(View v){
    if(reactionAlreadyClicked)
      return;
    reactionAlreadyClicked=true;
		TdApi.Reaction r=(TdApi.Reaction) v.getTag();
		controller.sendMessageReaction(message, r.reaction, (ImageView) ((ViewGroup)v).getChildAt(0), null, popupLayout, false);
	}

	private boolean onReactionLongClick(View v){
    if(reactionAlreadyClicked)
      return false;
    reactionAlreadyClicked=true;
		TdApi.Reaction r=(TdApi.Reaction) v.getTag();
		controller.sendMessageReaction(message, r.reaction, (ImageView) ((ViewGroup)v).getChildAt(0), null, popupLayout, true);
		return true;
	}

	private static class ChosenReactionBackgroundDrawable extends Drawable{
		private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

		@Override
		public void draw(@NonNull Canvas canvas){
			paint.setColor(Theme.getColor(R.id.theme_color_file));
			canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), Screen.dp(17.25f), paint);
		}

		@Override
		public void setAlpha(int alpha){

		}

		@Override
		public void setColorFilter(@Nullable ColorFilter colorFilter){

		}

		@Override
		public int getOpacity(){
			return PixelFormat.TRANSLUCENT;
		}
	}
}
