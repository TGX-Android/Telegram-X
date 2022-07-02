package org.thunderdog.challegram.reactions;

import android.graphics.Canvas;
import android.view.Gravity;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;

import androidx.annotation.IdRes;

public class CompactReactionsRenderer implements Counter.Callback{
	private Counter counter;
	private TGMessage parent;
	private int totalReactionsCount;
	private ArrayList<TdApi.Reaction> iconizedReactions=new ArrayList<>(3);
	private boolean selfReacted;

	public CompactReactionsRenderer(TGMessage parent){
		this.parent=parent;
		counter=new Counter.Builder()
				.noBackground()
				.textSize(parent.useBubbles() ? 11f : 12f)
//				.textColor(getCounterDefaultColor())
				.allBold(false)
				.colorSet(parent::getTimePartTextColor)
				.build();
	}

	@IdRes
	private int getCounterDefaultColor(){
		if(parent.useBubbles())
			return parent.isOutgoingBubble() ? R.id.theme_color_bubbleOut_time : R.id.theme_color_bubbleIn_time;
		return R.id.theme_color_bubbleIn_time;
	}

	public void update(boolean animated){
		TdApi.MessageInteractionInfo info=parent.getMessageForReactions().interactionInfo;
		iconizedReactions.clear();
		totalReactionsCount=0;
		selfReacted=false;
		if(info==null || info.reactions==null){
			counter.setCount(0, animated);
			return;
		}
		for(TdApi.MessageReaction reaction:info.reactions){
			totalReactionsCount+=reaction.totalCount;
			selfReacted=selfReacted || reaction.isChosen;
			if(iconizedReactions.size()<3){
				iconizedReactions.add(parent.tdlib().getReaction(reaction.reaction));
			}
		}
		counter.setCount(totalReactionsCount>3 ? totalReactionsCount : 0, !selfReacted, animated);
	}

	public int getWidth(){
		if(iconizedReactions.isEmpty())
			return 0;
		return iconizedReactions.size()*Screen.dp(16)+Math.round(counter.getScaledWidth(Screen.dp(3)))+(parent.useBubbles() ? Screen.dp(6) : 0);
	}

	// Canvas must be translated as needed
	public void draw(Canvas c, ComplexReceiver receiver){
		int iconSize=Screen.dp(16);
		for(int i=0;i<iconizedReactions.size();i++){
			ImageReceiver icon=receiver.getImageReceiver(i);
			icon.setBounds(iconSize*i, -iconSize/2, iconSize*(i+1), iconSize/2);
			icon.draw(c);
		}
		counter.draw(c, iconSize*iconizedReactions.size()+Screen.dp(3), 0, Gravity.LEFT, 1f);
	}

	public Counter getCounter(){
		return counter;
	}

	public void loadIconsIntoReceiver(ComplexReceiver receiver){
		int i=0;
		for(TdApi.Reaction r:iconizedReactions){
			ImageReceiver icon=receiver.getImageReceiver(i);
			icon.requestFile(TD.toImageFile(parent.tdlib(), r.staticIcon.thumbnail));
			i++;
		}
	}

	@Override
	public void onCounterAppearanceChanged(Counter counter, boolean sizeChanged){
		parent.onCounterAppearanceChanged(counter, sizeChanged);
	}

	@Override
	public boolean needAnimateChanges(Counter counter){
		return parent.needAnimateChanges(counter);
	}
}
