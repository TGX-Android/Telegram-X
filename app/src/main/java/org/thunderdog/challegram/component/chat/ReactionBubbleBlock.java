package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import java.util.ArrayList;

import me.vkryl.core.lambda.Destroyable;

public class ReactionBubbleBlock implements Client.ResultHandler, Destroyable {

    private TdApi.MessageReaction[] reactions;
    private int msgWidth;
    private Tdlib tdlib;
    private TGMessage context;
    private ArrayList<ReactionBubble> reactionBubbles = new ArrayList<ReactionBubble>();

    private Rect bounds;



    public ReactionBubbleBlock(int messageWidth, TdApi.MessageReaction[] reactions, Tdlib tdlib, @NonNull TGMessage context) {
        this.reactions = reactions;
        this.msgWidth = messageWidth;
        this.tdlib = tdlib;
        this.context = context;
        computeBounds();
    }

    public  boolean onTouchEvent(View view, @NonNull MotionEvent e){
        for(int i =0; i < reactionBubbles.stream().count(); i++) {
            if(reactionBubbles.get(i).onTouchEvent(view, e)){
                return true;
            }
        }
        return false;
    }

    private void computeBounds() {
        var rWidth = 0;

        int height = 0;
        int width = 0;
        int xMargin = getReactionRowMargin();

        for(int i = 0; i < reactions.length; i++) {
            var b = new ReactionBubble(reactions[i], tdlib, context, 0);
            height = b.computeHeight();
            rWidth += b.computeWidth() + xMargin;
            if(rWidth > width) {
                width = rWidth;
            }
        }

        int column = (int)Math.ceil((double)rWidth / (double)msgWidth);

        if(column ==0) column = 1;

        bounds = new Rect(0,0,rWidth, column * (height + getReactionColumnMargin()));
    }

    public int getReactionColumnMargin () {
        return Screen.dp(4f);
    }

    public int getReactionRowMargin(){
        return Screen.dp(4f);
    }

    public Rect getBounds() {
        return bounds;
    }

    public void draw(Canvas c, int x, int y) {
        reactionBubbles.clear();
        var rWidth = 0;
        int column = 0;

        int xMargin = getReactionRowMargin();
        int yMargin = getReactionColumnMargin();

        for(int i = 0; i < reactions.length; i++) {
            var bubble = new ReactionBubble(reactions[i], tdlib, context, i);
            reactionBubbles.add(bubble);
            if (rWidth + bubble.computeWidth() + xMargin > msgWidth) {
                rWidth = 0;
                column++;
            }
            bubble.draw(c, x + rWidth, y + (yMargin + bubble.computeHeight()) * column );
            rWidth += bubble.computeWidth() + xMargin;
        }
    }


    @Override
    public void onResult(TdApi.Object object) {
        if(isDestroyed)
            return;
    }


    private volatile boolean isDestroyed;
    @Override
    public void performDestroy() {
        this.isDestroyed = true;
    }
}