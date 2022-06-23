package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;

import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

public class UserReactionView extends UserView{
   private ImageReceiver reactionReceiver;

   public UserReactionView(Context context, Tdlib tdlib){
      super(context, tdlib);
      reactionReceiver=new ImageReceiver(this, 0);
   }

   @Override
   protected void onDraw(Canvas c){
      super.onDraw(c);
      int size=Screen.dp(30);
      reactionReceiver.setBounds(0, 0, size, size);
      c.save();
      c.translate(getWidth()-getHeight()/2-size/2, getHeight()/2-size/2);
      reactionReceiver.draw(c);
      c.restore();
   }

   @Override
   protected int getContentPaddingRight(){
      return Screen.dp(54);
   }

   public void setReaction(String reaction){
      reactionReceiver.requestFile(TD.toImageFile(tdlib, tdlib.getReaction(reaction).staticIcon.thumbnail));
   }
}
