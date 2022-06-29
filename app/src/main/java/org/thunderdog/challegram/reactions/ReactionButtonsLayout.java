package org.thunderdog.challegram.reactions;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ReactionButtonsLayout extends ViewGroup{
	private TGMessage message;
	private int bottomRightIndentWidth;
	private Rect rect=new Rect();
	private LayoutTransition layoutTransition;
	private ReactionButtonClickListener clickListener;
	private MessageCellReactionButton.BackgroundStyle buttonStyle;

	private ArrayList<MessageCellReactionButton> buttons=new ArrayList<>(), activeButtons=new ArrayList<>(), disappearingButtons=new ArrayList<>();

//	private Paint paint=new Paint();

	public ReactionButtonsLayout(Context context){
		super(context);
//		setBackgroundColor(0x8800ff00);
		setPadding(Screen.dp(10), 0, Screen.dp(10), Screen.dp(10));
		setClipToPadding(false);

		layoutTransition=new LayoutTransition();
		layoutTransition.setAnimateParentHierarchy(false);
		layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
		layoutTransition.setDuration(220);
		layoutTransition.setInterpolator(LayoutTransition.CHANGING, CubicBezierInterpolator.DEFAULT);
		layoutTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
		layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0);
		layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);

		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(null, View.SCALE_X, .3f, 1f),
				ObjectAnimator.ofFloat(null, View.SCALE_Y, .3f, 1f),
				ObjectAnimator.ofFloat(null, View.ALPHA, 0f, 1f)
		);
		layoutTransition.setAnimator(LayoutTransition.APPEARING, set);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		int realWidth=MeasureSpec.getSize(widthMeasureSpec);
		int width=MeasureSpec.getSize(widthMeasureSpec)-getPaddingLeft()-getPaddingRight();
		int curX=getPaddingLeft();
		int maxX=0;
		int gap=Screen.dp(6);
		int rowHeight=Screen.dp(24);
		int curY=0;

		for(int i=0; i<getChildCount(); i++){
			View child=getChildAt(i);
			if(disappearingButtons.contains(child))
				continue;
			child.measure(width|MeasureSpec.AT_MOST, rowHeight|MeasureSpec.EXACTLY);
			if(curX+child.getMeasuredWidth()>width){
				curX=getPaddingLeft();
				curY+=rowHeight+gap;
			}
			maxX=Math.max(maxX, curX+child.getMeasuredWidth());
			curX+=child.getMeasuredWidth()+gap;
		}

		int height=curY+rowHeight+getPaddingTop();

		if(curX>realWidth-bottomRightIndentWidth)
			height+=Screen.dp(25);
		else
			height+=getPaddingBottom();

		int measuredWidth=maxX+getPaddingRight();
		if(curY==0){
			if(measuredWidth-getPaddingLeft()-getPaddingRight()+bottomRightIndentWidth<=MeasureSpec.getSize(widthMeasureSpec))
				measuredWidth+=bottomRightIndentWidth-getPaddingRight()-getPaddingLeft();
			else
				height+=Screen.dp(25)-getPaddingBottom();
		}

		setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec)==MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthMeasureSpec) : measuredWidth, height);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b){
		int width=r-l-getPaddingLeft()-getPaddingRight();
		int curX=getPaddingLeft();
		int gap=Screen.dp(6);
		int rowHeight=Screen.dp(24);
		int curY=getPaddingTop();

		if(message==null)
			return;

		for(int i=0; i<getChildCount(); i++){
			 View child=getChildAt(i);
			 if(disappearingButtons.contains(child))
				  continue;
			 if(curX+child.getMeasuredWidth()>width){
				  curX=getPaddingLeft();
				  curY+=rowHeight+gap;
			 }
			 child.layout(curX, curY, curX+child.getMeasuredWidth(), curY+child.getMeasuredHeight());
			 curX+=child.getMeasuredWidth()+gap;
		}
	}

	public void setMessage(TGMessage message){
		boolean sameMessage=this.message!=null && this.message.getChatId()==message.getChatId() && this.message.getId()==message.getId();
		this.message=message;
		if(message.useBubbles()){
			if(message.drawBubbleTimeOverContent())
				buttonStyle=MessageCellReactionButton.BackgroundStyle.BUBBLE_OUTSIDE;
			else if(message.isOutgoing())
				buttonStyle=MessageCellReactionButton.BackgroundStyle.BUBBLE_OUTGOING;
			else
				buttonStyle=MessageCellReactionButton.BackgroundStyle.BUBBLE_INCOMING;
		}else{
			buttonStyle=MessageCellReactionButton.BackgroundStyle.NO_BUBBLES;
		}

		if(sameMessage){
			if(getLayoutTransition()==null)
				setLayoutTransition(layoutTransition);
			HashMap<String, MessageCellReactionButton> existingButtons=new HashMap<>(activeButtons.size());
			for(MessageCellReactionButton btn : activeButtons){
				existingButtons.put(btn.getReaction().reaction, btn);
			}
			List<TdApi.MessageReaction> addedReactions=Arrays.stream(message.getReactions()).filter(r->{
				MessageCellReactionButton btn=existingButtons.remove(r.reaction);
				if(btn==null)
					return true;
				btn.setReactions(r, true);
				btn.setSelected(r.isChosen, true);
				return false;
			}).collect(Collectors.toList());

			if(!existingButtons.isEmpty()){
				for(MessageCellReactionButton btn : existingButtons.values()){
					recycleButton(btn);
				}
			}
			if(!addedReactions.isEmpty()){
				for(TdApi.MessageReaction count : addedReactions){
					MessageCellReactionButton button=obtainButton();
					button.setReactions(count, false);
					button.setSelected(count.isChosen, false);
				}
			}
		}else{
			if(getLayoutTransition()!=null)
				setLayoutTransition(null);
			clear();
			if(!message.hasReactions())
				return;
			for(TdApi.MessageReaction count : message.getReactions()){
				MessageCellReactionButton button=obtainButton();
				button.setReactions(count, false);
				button.setSelected(count.isChosen, false);
			}
		}
	}

	public void clear(){
		while(!activeButtons.isEmpty())
			recycleButton(activeButtons.remove(activeButtons.size()-1));
	}

	private MessageCellReactionButton obtainButton(){
		if(!buttons.isEmpty()){
			MessageCellReactionButton btn=buttons.remove(buttons.size()-1);
			addView(btn);
			activeButtons.add(btn);
//			btn.setForegroundColor(getColor(inBubble ? (message.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) : Theme.key_chat_serviceText));
			btn.setBackgroundStyle(buttonStyle);
			return btn;
		}
		MessageCellReactionButton btn=new MessageCellReactionButton(getContext(), message.tdlib());
		addView(btn);
		activeButtons.add(btn);
		btn.setOnClickListener(this::onChildClick);
		btn.setOnLongClickListener(this::onChildLongClick);
//		btn.setForegroundColor(getColor(inBubble ? (message.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) : Theme.key_chat_serviceText));
		btn.setBackgroundStyle(buttonStyle);
		return btn;
	}

	private void recycleButton(MessageCellReactionButton btn){
		activeButtons.remove(btn);
		if(getLayoutTransition()!=null){
			disappearingButtons.add(btn);
			btn.animate().scaleX(.3f).scaleY(.3f).alpha(0f).setDuration(220).withEndAction(()->{
				removeView(btn);
				disappearingButtons.remove(btn);
				buttons.add(btn);
				btn.setAlpha(1f);
				btn.setScaleX(1f);
				btn.setScaleY(1f);
			}).start();
		}else{
			removeView(btn);
			btn.animate().cancel();
			btn.setScaleX(1f);
			btn.setScaleY(1f);
			btn.setAlpha(1f);
			disappearingButtons.remove(btn);
			buttons.add(btn);
		}
	}

	public void setBottomRightIndentWidth(int bottomRightIndentWidth){
		this.bottomRightIndentWidth=bottomRightIndentWidth;
	}

//	@Override
//	protected void onDraw(Canvas canvas){
//		super.onDraw(canvas);
//
//		paint.setColor(0x88ff0000);
//		if(bottomRightIndentWidth>0){
//			canvas.drawRect(getWidth()-bottomRightIndentWidth, getHeight()-Screen.dp(14), getWidth(), getHeight(), paint);
//		}
//	}

	private void onChildClick(View v){
		MessageCellReactionButton btn=(MessageCellReactionButton) v;
		clickListener.onReactionClick(btn);
	}

	private boolean onChildLongClick(View v){
		MessageCellReactionButton btn=(MessageCellReactionButton) v;
		return clickListener.onReactionLongClick(btn);
	}

	public boolean willHandleTouchEventBecauseOfFuckedUpCustomEventDispatch(MotionEvent ev){
		for(MessageCellReactionButton child : activeButtons){
			rect.set(0, 0, child.getWidth(), child.getHeight());
			rect.offset(getLeft()+child.getLeft(), getTop()+child.getTop());
			if(rect.contains((int) ev.getX(), (int) ev.getY()))
				return true;
		}
		return false;
	}

	public void invalidateButtons(){
		for(MessageCellReactionButton btn : activeButtons)
			btn.invalidate();
	}

	public void setButtonClickListener(ReactionButtonClickListener clickListener){
		this.clickListener=clickListener;
	}

	public MessageCellReactionButton getReactionButton(String reaction){
		for(MessageCellReactionButton btn : activeButtons){
			if(btn.getReaction().reaction.equals(reaction))
				return btn;
		}
		return null;
	}

	public interface ReactionButtonClickListener{
		void onReactionClick(MessageCellReactionButton btn);

		boolean onReactionLongClick(MessageCellReactionButton btn);
	}
}
