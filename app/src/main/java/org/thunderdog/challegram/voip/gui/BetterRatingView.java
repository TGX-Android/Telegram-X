/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/06/2017
 */
package org.thunderdog.challegram.voip.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class BetterRatingView extends View {
  private Drawable filledStar, hollowStar;
  private Paint paint=new Paint();
  private int numStars=5;
  private int selectedRating=0;
  private OnRatingChangeListener listener;

  public BetterRatingView(Context context){
    super(context);
    filledStar = Drawables.get(R.drawable.baseline_star_24);
    hollowStar = Drawables.get(R.drawable.baseline_star_border_24);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
    setMeasuredDimension(numStars * Screen.dp(32f)+(numStars-1) * Screen.dp(16), Screen.dp(32));
  }

  @Override
  protected void onDraw(Canvas c){
    for(int i=0;i<numStars;i++){
      paint.setColor(Theme.iconColor());
      Drawables.draw(c, i<selectedRating ? filledStar : hollowStar, i*Screen.dp(32+16), 0, Paints.getIconGrayPorterDuffPaint());
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event){
    float offset=Screen.dp(-8);
    for(int i=0;i<numStars;i++){
      if(event.getX()>offset && event.getX()<offset+Screen.dp(32+16)){
        if(selectedRating!=i+1){
          selectedRating=i+1;
          if(listener!=null)
            listener.onRatingChanged(selectedRating);
          invalidate();
          break;
        }
      }
      offset+=Screen.dp(32+16);
    }
    return true;
  }

  public int getRating(){
    return selectedRating;
  }

  public void setOnRatingChangeListener(OnRatingChangeListener l){
    listener=l;
  }

  public interface OnRatingChangeListener{
    void onRatingChanged(int newRating);
  }
}
