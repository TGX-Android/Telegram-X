package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.loader.gif.GifState;

public class GifReceiverView extends View{
	private GifReceiver receiver;

	public GifReceiverView(Context context){
		super(context);
		receiver=new GifReceiver(this);
	}

	@Override
	protected void onAttachedToWindow(){
		super.onAttachedToWindow();
		receiver.attach();
	}

	@Override
	protected void onDetachedFromWindow(){
		receiver.detach();
		super.onDetachedFromWindow();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		super.onSizeChanged(w, h, oldw, oldh);
		receiver.setBounds(0, 0, w, h);
	}

	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);
//		if(receiver.needPlaceholder())
//			receiver.drawPlaceholder(canvas);
		receiver.draw(canvas);
	}

	public GifReceiver getReceiver(){
		return receiver;
	}
}
