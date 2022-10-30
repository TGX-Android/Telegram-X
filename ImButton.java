package org.thunderdog.challegram.custom;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.TextView;

import me.vkryl.android.animator.FactorAnimator;


public class ImButton extends AvatarView {
 protected final MessagesController c;
  TdApi.Chat chat;
  int strategy = 0;
  public ImButton (Context context , MessagesController c
  ) {
    super(context);
    this.c = c;

    new Thread(()->{
      while(c.getChat() == null ){
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      new Handler(Looper.getMainLooper()).post(()->{
        chat = c.getChat();
        setScaleX(0.55f);
        setScaleY(0.55f);
        setUser(c.tdlib() ,chat.id , false);
      });
    }).start();

  }
//public void getBitma

  public void ShowMenu(View view){
    //   TdApi.ChatList chlst = c.chatList();

      chat = c.getChat();

    new ShowPanel(this);
setClickable(false);
  }
  CTextView doptext;
public void ChangeAvatar(ShowPanel.Item chatItem ) {
  setClickable(true);

  // c.getBottomWrap().setBackgroundColor(getResources().getColor(R.color.status_bar));
  RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) c.emojiButton.getLayoutParams();
  // Log.e("ffff" , " "  + (c.emojiButton.getHeight() + c.emojiButton.getPaddingRight() + c.emojiButton.getPaddingLeft()) +" "  );

  if(chatItem.chatId != chat.id){
  if (c.getBottomWrap().getChildCount() == 1) {
    doptext = new CTextView(getContext());
    doptext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
    doptext.setTranslationX(c.emojiButton.getHeight() + Screen.dp(20));
    doptext.setBackgroundColor(getResources().getColor(R.color.status_bar));

    c.getBottomWrap().addView(doptext, -1, -2);
  } else doptext = (CTextView) c.getBottomWrap().getChildAt(1);
  if (chatItem != null && doptext != null) {
    doptext.setHint("as " + chatItem.title);
    doptext.setTranslationY(0);
  }
}
  if(chatItem.chatId != chat.id)
  new Thread(()->{ 

  final  float y = getY();
    int end = 0;
    while(end < getHeight()){
      try {Thread.sleep(10);} catch (InterruptedException e) {}
      if(getHandler() == null) return;
      load(y ,end  );
      load(y ,end , chatItem);
end +=4;
    }


    getHandler().post(()-> {
      setUser(c.tdlib() , chatItem.chatId, true);
    });
    end = -getHeight();
    while(end < 4){
      try {Thread.sleep(10);} catch (InterruptedException e) {}
      if(getHandler() == null) return;
      load(y ,end );
      end+=4;

    }

    if(chat.id != chatItem.chatId) strategy = 1;
    else strategy = 0;
  //  for(int i=0;i<c.getBottomWrap().getChildCount();i++)
   // Log.e("wwwwwww" , " " + c.getBottomWrap().getChildAt(i));
   // c.getLi
  }).start();
else
  new Thread(()->{

  final  int y = (int) getY();
  int end = 0;
  while(end < getHeight()){
    try {Thread.sleep(10);} catch (InterruptedException e) {}
    if(getHandler() == null) return;
    load(end , y  );

    end +=4;
  }


  getHandler().post(()-> {
    setUser(c.tdlib() , chatItem.chatId, true);
    setY(0);
   if(getCTextView() != null){
     c.getBottomWrap().removeView(doptext);
     c.inputView.setTranslationY(0);
   }
  });




  if(chat.id != chatItem.chatId) strategy = 1;
  else strategy = 0;
  //  for(int i=0;i<c.getBottomWrap().getChildCount();i++)
  // Log.e("wwwwwww" , " " + c.getBottomWrap().getChildAt(i));
  // c.getLi
}).start();

}
  private void load (float y, int end ,ShowPanel.Item chatItem) {
    getHandler().post(()-> {
    if( doptext != null)
      if(  chatItem.chatId != chat.id && doptext.getHeight()*2 > end/4) {doptext.setTranslationY( - end/4);
        c.inputView.setTranslationY(-doptext.getHeight()/3);
      }
    });
  }
  private void load (float y, int end ) {

    getHandler().post(()-> {
      setY(y - end);

  //   c.inputView.setY(y - end);
    });
  }
  CTextView getCTextView(){
return doptext;
  }

}
