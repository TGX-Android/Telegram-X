package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  DataSavingOption.NEVER,
  DataSavingOption.MOBILE,
  DataSavingOption.ALWAYS,
  DataSavingOption.ROAMING
})
public @interface DataSavingOption {
  // enum from VoIPController.h:93
  int
    NEVER = 0,
    MOBILE = 1,
    ALWAYS = 2,
    ROAMING = 3 /*this field is not present in VoIPController.h and is converted to MOBILE or NEVER*/;
}
