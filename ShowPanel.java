package org.thunderdog.challegram.custom;


import java.util.ArrayList;

import android.graphics.Bitmap;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;

import org.thunderdog.challegram.data.TGUser;

import org.thunderdog.challegram.telegram.Tdlib;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;

public class ShowPanel extends ListView implements AdapterView.OnItemClickListener{
  RelativeLayout b;
  ImButton context;
   ArrayList<Item> objects;
  private PanelListAdapter adapter;
  public ShowPanel(ImButton context ) {
    super(context.getContext());
    this.context = context;
    b= context.c.contentView;
    setBackground(getResources().getDrawable(R.drawable.popup_top_dark));

    DisplayMetrics dp = context.getResources().getDisplayMetrics();
    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams((int) (dp.widthPixels * 0.6),-2);
    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    lp.rightMargin =  Screen.dp(10f);
    lp.bottomMargin = Screen.dp(50f);
    setLayoutParams(lp);
    b.addView( this );
    AnimationSet animation = new AnimationSet(false);
    ScaleAnimation scale = new ScaleAnimation(0.7f, 1,
      0.7f, 1,
      ScaleAnimation.RELATIVE_TO_SELF,
      0.5f,
      ScaleAnimation.RELATIVE_TO_SELF,
      1f);
    scale.setFillAfter(true);
    scale.setDuration(400);
    scale.setStartOffset(200);
    animation.addAnimation(scale);
    CaseStrategy();
    AlphaAnimation alpha = new AlphaAnimation(0, 1);
    alpha.setFillAfter(true);
    alpha.setDuration(600);
    alpha.setStartOffset(200);
    animation.addAnimation(alpha);
    setAnimation(animation);
    animation.start();

  }
  private void CaseStrategy() {
    // TODO Auto-generated method stub
    switch(context.strategy ) {
      case 0:{


//        Bitmap b = ((BitmapDrawable) getResources().getDrawable(R.drawable.baseline_adjust_24)).getBitmap();

      //  if( userIds == null){
          objects = new ArrayList<>();
          Item e = new Item("More" ,null, null);
          e.chatId = -1;
          objects.add(e);
          int rules = context.c.tdlib().contacts().getAvailableRegisteredCount();
          //     TdApi.UserPrivacySettingRuleAllowUsers rule;
          //    LinkedList<TdlibAccount> accounts = new LinkedList<>();

          //  final TdApi.Users users = (TdApi.Users) object;
          context.c.tdlib().searchContacts(null, rules, newHandler(5));
     //   }


      }break;
      case 1:{

          objects = new ArrayList<>();
          Item e = new Item("More" ,null, null);
          e.chatId = -1;
          objects.add(e);
          int rules = context.c.tdlib().contacts().getAvailableRegisteredCount();
          context.c.tdlib().searchContacts(null, rules, newHandler(4));

      }break;
    }
  }
  private Client.ResultHandler newHandler (int count) {
    return object -> {
      final TdApi.Users users = (TdApi.Users) object;
      long[] userIds = users.userIds;
      if (userIds.length > 0) {
        for(int i =0;i<userIds.length ;i++){
          TdApi.Chat chat = context.c.tdlib().chat(userIds[i]);
          if(chat != null){
            if(chat.photo  != null){
              //   ImageFile avatar = TD.getAvatar(context.c.tdlib(), chat.photo);
              //   b = BitmapFactory.decodeFile(avatar.getFilePath());
              //   b =  BitmapDrawable.createFromPath( avatar.getFilePath()).getBitmap();
            }
            if(chat.id != context.chat.id) {
              Item e = new Item(chat.title, null, null);
              e.user = new TGUser(context.c.tdlib(), context.c.tdlib().chatUser(chat));
            e.descript ="@" +  chat.title;
              e.tdlib = context.c.tdlib();
              e.chatId = chat.id;
              objects.add(e);
            }
          }
        }
        //  context.c.chatList().
        // context.c.tdlib().canDisablePinnedMessageNotifications()
        //    List<TdApi.Chat> cc1 = c.tdlib().chats(userIds);//.get(0);


      //
      }

      context.getHandler().post(()->{
        ArrayList<Item> ob = new ArrayList<>();
      for(int i =0;i<count;i++){
        ob.add(objects.get(i));
      }
        if(count == 4){
          Item e = new Item(context.chat.title, null, null);
          e.user = new TGUser(context.c.tdlib(), context.c.tdlib().chatUser(context.chat));
          e.descript ="@" +  context.chat.title;
          e.tdlib = context.c.tdlib();
          e.chatId = context.chat.id;
          ob.add(e);
        }
        adapter = new PanelListAdapter(getContext(), ob);
        setAdapter(adapter);
        setOnItemClickListener(this);
      });

    };
  }
  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    int it = (int) id;


if(objects.get(it).chatId == -1){
  objects.remove(0);
  if(context.strategy == 1){
    Item e = new Item(context.chat.title, null, "@" + context.chat.title);
    e.user = new TGUser(context.c.tdlib(), context.c.tdlib().chatUser(context.chat));
    //
    e.tdlib = context.c.tdlib();
    e.chatId = context.chat.id;
    objects.add(e);
  }
  adapter = new PanelListAdapter(getContext(), objects);
  setAdapter(adapter);
  return;
}
    PanelListAdapter panel = (PanelListAdapter) parent.getAdapter();

    AnimationSet animation = new AnimationSet(true);
    ScaleAnimation scale = new ScaleAnimation(1, 0.7f,
      1, 0.7f,
      ScaleAnimation.RELATIVE_TO_SELF,
      0.5f,
      ScaleAnimation.RELATIVE_TO_SELF,
      1f);
    scale.setFillAfter(true);
    scale.setDuration(400);
  //  scale.setStartOffset(500);
    animation.addAnimation(scale);

    AlphaAnimation alpha = new AlphaAnimation(1, 0);
    alpha.setFillAfter(true);
    alpha.setDuration(600);
  //  alpha.setStartOffset(500);
    animation.addAnimation(alpha);
    animation.setAnimationListener(new Animation.AnimationListener() {

      @Override
      public void onAnimationStart(Animation animation) {
        // TODO Auto-generated method stub

      }

      @Override
      public void onAnimationRepeat(Animation animation) {
        // TODO Auto-generated method stub

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        // TODO Auto-generated method stub
        b.removeView(ShowPanel.this);
        context.setClickable(true);
      }
    });
b.getHandler().post(()->{
  setAnimation(animation);
  animation.start();
});


context.ChangeAvatar( panel.objects.get(it));
    context.c.tdlib().searchContacts(null, 1, newHandler(5));
  }
  class Item{

    public Item(String title,Bitmap icon,String descript) {
      super();
      this.title = title ; this.icon = icon; this.descript = descript;
    }
    String title = "";
    Bitmap icon;
    String descript ;
    TGUser user;
    Tdlib  tdlib;
   long chatId;
    AvatarView drawable;
  }
}