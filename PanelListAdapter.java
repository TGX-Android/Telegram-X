package org.thunderdog.challegram.custom;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
//import android.widget.ImageView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.CircleButton;

public class PanelListAdapter extends ArrayAdapter {
  ArrayList<ShowPanel.Item> objects;
  private int paramIcon;

  public PanelListAdapter(Context context,  ArrayList<ShowPanel.Item> objects) {
    super(context, 0, objects);
    this.objects = objects;
  //  DisplayMetrics dp = context.getResources().getDisplayMetrics();
    paramIcon =  Screen.dp(20f)       ;//(int) (dp.widthPixels * 0.12);
  }

  @Override
  public int getCount() {
    // TODO Auto-generated method stub
    return super.getCount();
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // TODO Auto-generated method stub
    LinearLayout root = new LinearLayout(getContext());
    root.setBackgroundColor(getContext().getResources().getColor(R.color.apps_list));
    root.setOrientation(LinearLayout.HORIZONTAL);
    root.setGravity(Gravity.CENTER_VERTICAL);
    ShowPanel.Item item = objects.get(position);
  //  ImageView icon = new ImageView(getContext());


    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(paramIcon ,paramIcon);
    lp.leftMargin = paramIcon;
    lp.rightMargin = paramIcon;
    lp.topMargin = paramIcon;
    lp.bottomMargin = paramIcon;
    if(item.user != null) {

       AvatarView tav = new AvatarView(root.getContext());
      tav.setUser( item.tdlib, item.chatId , false);
      item.drawable = tav;
   //   container.setReceiver(receiver ,avatarRadius, item.user);
   //   receiver.setBounds(avatarRadius -paramIcon / 2, avatarRadius-paramIcon / 4, avatarRadius-paramIcon / 2, avatarRadius-paramIcon / 4);
  //    receiver.requestFile(item.user.getAvatar());

      tav.setLayoutParams(lp);
      root.addView(tav);

    }else{
ImageView view = new ImageView(getContext());
     view.setLayoutParams(lp);
      view.setImageResource(R.drawable.more_3x);
     root.addView(view);

    }

    lp = new LinearLayout.LayoutParams(-1 ,-2);
    LinearLayout naming = new LinearLayout(getContext());
    naming.setOrientation(LinearLayout.VERTICAL);
    lp.topMargin = paramIcon/4;
    lp.bottomMargin = paramIcon/4;
    naming.setLayoutParams(lp);
    root.addView(naming);


    TextView title = new TextView(getContext());
    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    title.setTextColor(Color.GRAY);
    String name = item.title;
    if(name.length() > 15) name = name.substring(0,12) +" ...";
    title.setText(name);
    naming.addView(title);
if(item.descript != null){
    TextView descript = new TextView(getContext());
    descript.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    descript.setAlpha(0.6f);
    descript.setText(item.descript);
    naming.addView(descript); }
    return root;
  }

}