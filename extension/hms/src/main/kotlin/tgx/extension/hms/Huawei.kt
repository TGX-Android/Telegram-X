package tgx.extension.hms

import android.content.Context
import com.huawei.agconnect.AGConnectOptionsBuilder

fun obtainHuaweiAppId(context: Context): String =
  AGConnectOptionsBuilder().build(context).getString("/client/app_id") ?: ""