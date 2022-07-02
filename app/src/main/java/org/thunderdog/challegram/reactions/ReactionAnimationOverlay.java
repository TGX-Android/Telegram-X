package org.thunderdog.challegram.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.jetbrains.annotations.NotNull;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;

import androidx.annotation.Nullable;

public class ReactionAnimationOverlay{
	private Activity activity;
	private WindowManager wm;
	private FrameLayout windowView;
	private int runningAnimationsCount;

	public ReactionAnimationOverlay(ViewController<?> chat){
		activity=chat.context();
		wm=activity.getWindowManager();
	}

	private void createAndShowWindow(){
		if(windowView!=null)
			return;
		windowView=new FrameLayout(activity);
		WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
		lp.width=lp.height=WindowManager.LayoutParams.MATCH_PARENT;
		lp.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
		lp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		lp.format=PixelFormat.TRANSLUCENT;
		lp.token=activity.getWindow().getDecorView().getWindowToken();
		wm.addView(windowView, lp);
	}

	private void removeWindow(){
		if(windowView==null)
			return;
		wm.removeView(windowView);
		windowView=null;
	}

	public void playLottieAnimation(@NotNull ViewBoundsProvider pos, @NotNull PreloadedLottieAnimation animation, @Nullable Runnable onStarting, @Nullable AnimationEndCallback onDone){
		createAndShowWindow();
		Rect rect=new Rect();
		if(!pos.getBounds(rect) || rect.isEmpty())
			return;
		LottieAnimationDrawable drawable=new LottieAnimationDrawable(animation, rect.width(), rect.height());
		ImageView img=new ImageView(activity);
		img.setImageDrawable(drawable);
		img.setTranslationX(rect.left);
		img.setTranslationY(rect.top);
		windowView.addView(img, new FrameLayout.LayoutParams(rect.width(), rect.height(), Gravity.TOP | Gravity.LEFT));
		runningAnimationsCount++;
		img.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			private boolean started;
			@Override
			public boolean onPreDraw(){

				if(!started){
					if(onStarting!=null)
						onStarting.run();
					drawable.start();
					started=true;
				}

				if(!drawable.isRunning() || !pos.getBounds(rect)){
					img.getViewTreeObserver().removeOnPreDrawListener(this);
					Runnable remover=()->{
						windowView.removeView(img);
						runningAnimationsCount--;
						if(runningAnimationsCount==0){
							removeWindow();
						}
					};
					if(onDone!=null)
						onDone.onAnimationEnd(img, remover);
					else
						remover.run();
					return false;
				}
				img.setTranslationX(rect.left);
				img.setTranslationY(rect.top);

				return true;
			}
		});
	}

	public void playFlyingReactionAnimation(@NotNull ViewBoundsProvider dstPos, @NotNull Rect srcPos, @NotNull Drawable drawable, @Nullable Runnable onStarting, @Nullable Runnable onDone){
		createAndShowWindow();
		FrameLayout wrapper=new FrameLayout(activity); // Needed to move the whole thing around in response to scrolling
		ImageView iv=new ImageView(activity);
		iv.setImageDrawable(drawable);
		FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(srcPos.width(), srcPos.height(), Gravity.LEFT | Gravity.TOP);
		lp.topMargin=-srcPos.height()/2;
		lp.leftMargin=-srcPos.width()/2;
		wrapper.addView(iv, lp);
		windowView.addView(wrapper);
		runningAnimationsCount++;

		iv.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			private boolean started;
			private int startX, startY;
			private Rect rect=new Rect();

			@Override
			public boolean onPreDraw(){
				if(!dstPos.getBounds(rect)){
					iv.getViewTreeObserver().removeOnPreDrawListener(this);
					windowView.removeView(wrapper);
					if(onDone!=null)
						onDone.run();
					runningAnimationsCount--;
					if(runningAnimationsCount==0){
						removeWindow();
					}
					return false;
				}
				if(!started){
					started=true;
					if(onStarting!=null)
						onStarting.run();

					float srcX=srcPos.centerX(), srcY=srcPos.centerY();
					float dstX=rect.centerX(), dstY=rect.centerY();
					float slopeHeight=Math.min(Screen.dp(100), Math.abs(srcY-dstY)*0.25f);
					startX=rect.centerX();
					startY=rect.centerY();

					AnimatorSet set=new AnimatorSet();
					ArrayList<Animator> anims=new ArrayList<>();
					if(Build.VERSION.SDK_INT>=21){
						Path path=new Path();
						path.moveTo(srcX, srcY);
						path.cubicTo(srcX, srcY, interpolate(srcX, dstX, .25f), Math.min(dstY, srcY)-slopeHeight, interpolate(srcX, dstX, .5f), Math.min(dstY, srcY)-slopeHeight);
						path.cubicTo(interpolate(srcX, dstX, .75f), Math.min(dstY, srcY)-slopeHeight, dstX, dstY, dstX, dstY);
						anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, View.TRANSLATION_Y, path));
					}else{
						anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, srcX, dstX));
						anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, srcY, dstY));
					}
					float scale=rect.width()/(float)srcPos.width();
					anims.add(ObjectAnimator.ofFloat(iv, View.SCALE_X, scale));
					anims.add(ObjectAnimator.ofFloat(iv, View.SCALE_Y, scale));
					set.playTogether(anims);
					set.setDuration(400);
					set.setInterpolator(new AccelerateInterpolator());
					set.addListener(new AnimatorListenerAdapter(){
						@Override
						public void onAnimationEnd(Animator animation){
							windowView.removeView(wrapper);
							if(onDone!=null)
								onDone.run();
							runningAnimationsCount--;
							if(runningAnimationsCount==0){
								removeWindow();
							}
						}
					});
					set.start();
				}else{
					wrapper.setTranslationX(rect.centerX()-startX);
					wrapper.setTranslationY(rect.centerY()-startY);
				}

				return true;
			}
		});
	}

	private static float interpolate(float x1, float x2, float k){
		return x1*(1f-k)+x2*k;
	}

	@FunctionalInterface
	public interface ViewBoundsProvider{
		boolean getBounds(Rect outRect);
	}

	@FunctionalInterface
	public interface AnimationEndCallback{
		void onAnimationEnd(View view, Runnable remove);
	}
}
