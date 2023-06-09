package org.thunderdog.challegram.voip;

import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;

/**
 * Created by grishka on 01.03.17.
 */

public class VoIPServerConfig{

	private static JSONObject config;

	public static void setConfig(String json){
		try{
			config=new JSONObject(json);
			nativeSetConfig(json);
		}catch(JSONException x){
			Log.e(Log.TAG_VOIP, "Error parsing VoIP config", x);
		}
	}

	public static int getInt(String key, int fallback){
		return config.optInt(key, fallback);
	}

	public static double getDouble(String key, double fallback){
		return config != null ? config.optDouble(key, fallback) : fallback;
	}

	public static String getString(String key, String fallback){
		return config != null ? config.optString(key, fallback) : fallback;
	}

	public static boolean getBoolean(String key, boolean fallback){
		return config != null ? config.optBoolean(key, fallback) : fallback;
	}

	private static native void nativeSetConfig(String json);
}
