package org.thunderdog.challegram.voip;

import org.thunderdog.challegram.Log;

class VLog{
	public static void v(String msg) {
		Log.v(Log.TAG_VOIP, "%s", msg);
	}
	public static void d(String msg) {
		Log.d(Log.TAG_VOIP, "%s", msg);
	}
	public static void i(String msg) {
		Log.i(Log.TAG_VOIP, "%s", msg);
	}
	public static void w(String msg) {
		Log.w(Log.TAG_VOIP, "%s", msg);
	}
	public static void e(String msg) {
		Log.e(Log.TAG_VOIP, "%s", msg);
	}

	public static void e(Throwable x){
		Log.e(Log.TAG_VOIP, x);
		// e(null, x);
	}

	public static void e(String msg, Throwable x){
		Log.e(Log.TAG_VOIP, "%s", x, msg);
		/*StringWriter sw=new StringWriter();
		if(!TextUtils.isEmpty(msg)){
			sw.append(msg);
			sw.append(": ");
		}
		PrintWriter pw=new PrintWriter(sw);
		x.printStackTrace(pw);
		String[] lines=sw.toString().split("\n");
		for(String line:lines)
			e(line);*/
	}
}
