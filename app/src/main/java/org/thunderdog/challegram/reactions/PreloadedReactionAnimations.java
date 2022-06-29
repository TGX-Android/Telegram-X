package org.thunderdog.challegram.reactions;

public class PreloadedReactionAnimations{
   public PreloadedLottieAnimation appear;
   public PreloadedLottieAnimation around;
   public PreloadedLottieAnimation center;

   public void release(){
      if(appear!=null)
         appear.release();
      if(around!=null)
         around.release();
      if(center!=null)
         center.release();
   }
}
