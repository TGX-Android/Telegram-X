package org.thunderdog.challegram.reactions;

public class PreloadedReactionAnimations{
   public PreloadedLottieAnimation appear;

   public void release(){
      if(appear!=null)
         appear.release();
   }
}
