package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.tool.Screen;

public class ReactionsContextMenu extends View {
    private TGMessage message;
    private TdApi.Reaction[] reactions;
    private LinearLayout scroll;
    private ComplexReceiver receiver;
    private int offset = 0;
    private int downItemIndex = -1;
    private float lastX = 0;
    private int width;
    private int canvasWidth;
    private int hoverEvents = 0;

    int reactionSize = Screen.dp(36f);
    final int reactionMarginX = Screen.dp(10f);
    final int reactionMarginY = Screen.dp(12f);

    private RectF[] objectsPos = new RectF[]{};

    public ReactionsContextMenu(Context context, TGMessage message, TdApi.Reaction[] reactions,  int sizeY) {
        super(context);
        this.message = message;
        this.reactions = reactions;
        this.scroll = new LinearLayout(context);
        this.receiver = new ComplexReceiver(this);
        reactionSize = sizeY -  reactionMarginY * 2;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void normalizeOffset() {
        if(offset < 0) {
            offset = 0;
        }

        if(offset > width - canvasWidth){
            offset = width - canvasWidth;
        }
    }

    public int onClick(MotionEvent event) {
        var action = event.getAction();
        var x = event.getX();
        var y = event.getY();
        var click = new RectF(x, y, x+1,y+1);
        int index = 0;

        if(event.getAction() == MotionEvent.ACTION_MOVE && width > canvasWidth) {
            if(lastX != 0) {
                offset += lastX - event.getX();
                normalizeOffset();
                invalidate();
                hoverEvents++;
            }
            lastX = event.getX();
            return 1;
        }

        int result = 0;

        for(var r : this.objectsPos) {
            if (r.contains(click)) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downItemIndex = index;
                }

                if (downItemIndex == index && action == MotionEvent.ACTION_UP && hoverEvents < 10) {
                    onClick(index);
                    result = 2;
                } else {
                  result =   1;
                }
            }
            index++;
        }


        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastX = 0;
            hoverEvents = 0;
        }


        return result;
    }

    private void onClick(int index) {
        message.tdlib().send(new TdApi.SetMessageReaction(message.getChatId(), message.getMessage().id, reactions[index].reaction, false), null);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas c) {
        objectsPos = new RectF[this.reactions.length];
        int top = c.getHeight() - reactionSize - reactionMarginY;
        int left = -offset + reactionMarginX;

        long id = 0;
        for(var r : this.reactions) {
            ImageFile imageFile = TD.toImageFile(message.tdlib(), r.staticIcon.thumbnail);
            var imgReceiver = receiver.getImageReceiver(id);
            RectF rectF = new RectF(left, top, left + reactionSize, top + reactionSize);
            objectsPos[(int)id] = rectF;
            imgReceiver.requestFile(imageFile);
            imgReceiver.setBounds(left, top, left + reactionSize, top + reactionSize);
            imgReceiver.draw(c);
            id++;
            left += reactionSize + reactionMarginX;
        }
        width = (reactions.length * reactionSize) + ((reactions.length + 1) * reactionMarginX);
        canvasWidth = c.getWidth();
        super.onDraw(c);
    }
}
