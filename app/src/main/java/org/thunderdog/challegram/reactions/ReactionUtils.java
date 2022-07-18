package org.thunderdog.challegram.reactions;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;

public class ReactionUtils {
  public static ObjectAnimator animateColor (ObjectAnimator anim) {
    anim.setEvaluator(new ArgbEvaluator());
    return anim;
  }
}
