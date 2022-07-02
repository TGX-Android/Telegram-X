package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;
import me.vkryl.core.lambda.Destroyable;


public class ReactionBubble extends View implements Client.ResultHandler, Destroyable {

    public ReactionBubble(@NonNull TdApi.MessageReaction reaction, @NonNull Tdlib tdlib, @NonNull TGMessage context, int key) {
        super(context.context());
        this.reaction = reaction;
        this.tdlib = tdlib;
        this.context = context;
        this.key = key;
        text = new Text(getText(), 999, Paints.getTitleStyleProvider(), () -> getTextColor(), 1, 0, null);
        this.reactionInfo = findReactionInfo(reaction.reaction, tdlib);
    }
    private TdApi.MessageReaction reaction;
    private  TdApi.Reaction reactionInfo;
    private Tdlib tdlib;
    private TGMessage context;
    private RectF rect = new RectF();
    private int key;

    private int fontSize = 32;
    private int emojiMargin = Screen.dp(4f);

    @Override
    public void onResult(TdApi.Object object) {
        if(isDestroyed)
            return;
    }

    public static TdApi.Reaction findReactionInfo(String emoji, Tdlib tdlib) {
        final TdApi.Reaction[] reactions = tdlib.getSupportedReactions();

        for (int i = 0; i < reactions.length; i++) {
            if (reactions[i].reaction.equals(emoji)) {
                return reactions[i];
            }
        }

        return reactions[0];
    }

    public  boolean onTouchEvent(View view, @NonNull MotionEvent e){
        boolean result = rect.contains(e.getX(),e.getY());

        if(e.getAction() == MotionEvent.ACTION_UP && result) {
            tdlib.send(new TdApi.SetMessageReaction(context.getChatId(), context.getMessage().id, reaction.reaction, false), r -> {

                context.context().runOnUiThread(() -> {
                    if (r.getConstructor() != TdApi.Ok.CONSTRUCTOR) {
                        context.context()
                                .tooltipManager()
                                .builder(context.findCurrentView(), context.currentViews)
                                .locate((targetView, outRect) -> text.locatePart(outRect, new TextPart(text, getText(), 0, 0, 0, 0)))
                                .controller(context.controller())
                                .show(context.tdlib(), R.string.LaunchSubtitleExternalError)
                                .hideDelayed();
                    } else {
                        if(!reaction.isChosen) {
                            animateReaction();
                        }
                    }
                });
            });
        }

        return result;
    }


    private int getBackgroundColor() {
        if(!reaction.isChosen) return adjustAlpha(Theme.getColor(R.id.theme_color_file), 0.15f);
        if(context.isOutgoingBubble()) {
            return Theme.getColor(R.id.theme_color_bubbleOut_file);
        } else {
            return Theme.getColor(R.id.theme_color_file);
        }
    }

    private int getTextColor() {
        return reaction.isChosen ? Color.WHITE : Theme.getColor(R.id.theme_color_file);
    }

    public int getHorizontalPadding() {
        return Screen.dp(8f);
    }

    public  int getVerticalPadding() {
        return Screen.dp(6f);
    }

    public int computeWidth() {
        var value = fontSize + getTextBounds().width() + getHorizontalPadding() * 2 + emojiMargin ;
        return value;
    }

    public int computeHeight() {
        int textHeight = getTextBounds().height();
        int emojiHeight = fontSize;
        int result = emojiHeight > textHeight ? emojiHeight : textHeight;
        result += getVerticalPadding() * 2;
        return result - getVerticalPadding() / 2;
    }

    Rect getTextBounds() {
        return new Rect(0,0,text.getWidth(), text.getHeight());
    }

    String getText() {
        return Lang.formatNumber(reaction.totalCount);
    }

    Text text;

    private void animateReaction() {
        var sticker = reactionInfo.effectAnimation;
        GifFile gifFile;
        gifFile = new GifFile(tdlib, sticker);
        gifFile.setOptimize(true);
        gifFile.setScaleType(GifFile.CENTER_CROP);
        gifFile.setUnique(true);
        gifFile.setPlayOnce(true);


        Rect animRect = new Rect();
        int size = 120;


        Rect o = new Rect();
        text.locatePart(o, new TextPart(text, getText(), 0, 0, 0, 0));

        xEmojiCenter = o.centerX() - 14;
        yEmojiCenter = o.centerY();


        animRect.left = xEmojiCenter - size / 2;
        animRect.right = xEmojiCenter + size / 2;
        animRect.top = yEmojiCenter - size / 2;
        animRect.bottom = yEmojiCenter + size / 2;


        context.context().tooltipManager().builder(context.findCurrentView(), context.currentViews)
                .setGifRect(animRect)
                .locate((targetView, outRect) -> text.locatePart(outRect, new TextPart(text, getText(), 0, 0, 0, 0)))
                .gif(gifFile, null)//, TD.toImageFile(tdlib, reactionInfo.effectAnimation.thumbnail))
                .controller(context.controller())
                .setPopupView(false)
                .setAnimated(false)
                .show(tdlib, "");
    }


    int xEmojiCenter;
    int yEmojiCenter;

    public static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(255 * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green,blue);
    }

    public void draw(Canvas c, int x, int y) {
        var backPaint = new Paint();
        backPaint.setColor(getBackgroundColor());

        Path path = new Path();

        int right = x + computeWidth();
        int top = y;
        int width = computeWidth();
        int height = computeHeight();

        this.rect = new RectF(x, y, x + width, y + height);

        int ry = computeHeight() / 2;
        int rx = ry;

        //rounded rect
        path.moveTo(right, top + ry);
        path.rQuadTo(0, -ry, -rx, -ry);
        path.rLineTo(-(width - (2 * rx)), 0);
        path.rQuadTo(-rx, 0, -rx, ry);
        path.rLineTo(0, (height - (2 * ry)));
        path.rQuadTo(0, ry, rx, ry);
        path.rLineTo((width - (2 * rx)), 0);
        path.rQuadTo(rx, 0, rx, -ry);
        path.rLineTo(0, -(height - (2 * ry)));
        path.close();

        c.drawPath(path, backPaint);

        var eX = x + getHorizontalPadding();
        var eY = y + getVerticalPadding();
        // Emoji.instance().draw(c, Emoji.instance().getEmojiInfo(this.reaction.reaction), new Rect(eX, eY, eX+fontSize, eY + fontSize));

        int xText = x + getHorizontalPadding() + this.emojiMargin + fontSize;
        int yText = y + getVerticalPadding();
        text.draw(c, xText, yText);

        ImageFile imageFile = TD.toImageFile(tdlib, reactionInfo.staticIcon.thumbnail);
        var view = ((MessageView) context.currentViews.findAnyTarget());
        var receiver = view.getComplexReceiver();


        xEmojiCenter = x + eX + fontSize / 2;
        yEmojiCenter = y + eY + fontSize / 2;

        if (receiver == null) {
            view.setUseComplexReceiver();
            receiver = view.getComplexReceiver();
        }
        ImageReceiver picReceiver = receiver.getImageReceiver(this.key);
        picReceiver.requestFile(imageFile);
        picReceiver.setBounds(eX, eY, eX + fontSize, eY + fontSize);
        picReceiver.draw(c);
    }


    private volatile boolean isDestroyed;


    @Override
    public void performDestroy() {
        this.isDestroyed = true;
    }
}
