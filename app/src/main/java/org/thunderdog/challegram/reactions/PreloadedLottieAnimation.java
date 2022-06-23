package org.thunderdog.challegram.reactions;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;

import java.io.IOException;

public class PreloadedLottieAnimation{
	private long nativeHandle;
	private static final String TAG="PreloadedLottieAnimatio";
	private long frameCount;
	private double duration, frameRate;

	public PreloadedLottieAnimation(String filePath) throws IOException{
		String json=U.gzipFileToString(filePath);
		if(TextUtils.isEmpty(json))
			throw new IOException("Failed to preload lottie animation from "+filePath);
		double[] meta=new double[3];
		nativeHandle=N.createLottieDecoder(filePath, json, meta, 0);
		frameCount=(long)meta[0];
		frameRate=meta[1];
		duration=meta[2];
		if(BuildConfig.DEBUG)
			Log.d(TAG, "PreloadedLottieAnimation: loaded "+filePath+", "+frameCount+" frames, "+frameRate+" fps, "+duration+" seconds");
	}

	public void release(){
		N.destroyLottieDecoder(nativeHandle);
		nativeHandle=0;
	}

	public long getFrameCount(){
		return frameCount;
	}

	public double getFrameRate(){
		return frameRate;
	}

	public void getFrame(Bitmap bmp, long frame){
		if(nativeHandle==0){
			Log.w(TAG, "getFrame: called with released decoder");
			return;
		}
		N.getLottieFrame(nativeHandle, bmp, frame);
	}
}
