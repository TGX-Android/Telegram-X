package org.thunderdog.challegram.reactions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LottieAnimationDrawable extends Drawable implements Animatable{
	private static ThreadPoolExecutor rendererThreadPool=new ThreadPoolExecutor(1, 4, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

	private PreloadedLottieAnimation anim;
	// [0] is drawn onto the canvas, [1] is rendered into in a background thread
	private Bitmap[] bitmaps=new Bitmap[2];
	private int frame;
	private long startTime;
	private boolean running;
	private boolean drawing;
	private boolean loop;
	private long frameDelay;

	public LottieAnimationDrawable(PreloadedLottieAnimation anim, int width, int height){
		this.anim=anim;
		for(int i=0;i<2;i++){
			bitmaps[i]=Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		}
		frameDelay=Math.round(1000/anim.getFrameRate());
	}

	@Override
	public void start(){
		if(running)
			return;
		startTime=SystemClock.uptimeMillis();
		running=true;
		advance();
	}

	@Override
	public void stop(){
		running=false;
	}

	@Override
	public boolean isRunning(){
		return running;
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		synchronized(this){
			canvas.drawBitmap(bitmaps[0], null, getBounds(), null);
		}
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

	@Override
	public int getIntrinsicWidth(){
		return bitmaps[0].getWidth();
	}

	@Override
	public int getIntrinsicHeight(){
		return bitmaps[0].getHeight();
	}

	public void setLoop(boolean loop){
		this.loop=loop;
	}

	private synchronized void swapBuffers(){
		Bitmap tmp=bitmaps[0];
		bitmaps[0]=bitmaps[1];
		bitmaps[1]=tmp;
	}

	private void advance(){
		if(running){
			frame++;
			if(frame==anim.getFrameCount()){
				if(loop){
					frame=0;
				}else{
					frame--;
					running=false;
				}
			}
			if(!drawing){
				drawing=true;
				rendererThreadPool.submit(this::getNextFrame);
			}
		}
		invalidateSelf();
		if(drawing || running)
			scheduleSelf(this::advance, frameDelay);
	}

	private void getNextFrame(){
		anim.getFrame(bitmaps[1], frame);
		swapBuffers();
		drawing=false;
	}
}
