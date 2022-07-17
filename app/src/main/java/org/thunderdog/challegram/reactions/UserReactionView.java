package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.td.Td;

public class UserReactionView extends UserView{
   private ImageReceiver reactionReceiver;
   private Path outline=new Path();

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
      if(reactionReceiver.needPlaceholder()){
        c.drawPath(outline, Paints.getPlaceholderPaint());
      }
      reactionReceiver.draw(c);
      c.restore();
   }

   @Override
   protected int getContentPaddingRight(){
     return Screen.dp(54);
   }

   public void setReaction(String reaction){
     TdApi.Reaction r=tdlib.getReaction(reaction);
     reactionReceiver.requestFile(TD.toImageFile(tdlib, r.staticIcon.thumbnail));
     outline.rewind();
     int targetSize=Screen.dp(30);
     TdApi.Sticker sticker=r.staticIcon;
     Td.buildOutline(sticker.outline, Math.min((float) targetSize / (float) sticker.width, (float) targetSize / (float) sticker.height), outline);
   }
}
