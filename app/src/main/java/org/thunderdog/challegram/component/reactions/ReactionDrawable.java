package org.thunderdog.challegram.component.reactions;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import me.vkryl.core.lambda.Destroyable;

public class ReactionDrawable extends Drawable implements Destroyable {

    private final ImageReceiver imageReceiver;
    private final int width;
    private final int height;


    public static class ReactionInfo {
        private TGReaction reaction;
        private View boundView;
        private int width = Screen.dp(24);
        private int height = Screen.dp(24);

        public ReactionInfo(TGReaction reaction, View boundView) {
            this.reaction = reaction;
            this.boundView = boundView;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }


    public ReactionDrawable(ReactionInfo info) {
        TGStickerObj staticIcon = info.reaction.getStaticIcon();
        this.imageReceiver = new ImageReceiver(info.boundView, 0);
        if (staticIcon != null) {
            this.imageReceiver.requestFile(staticIcon.getFullImage());
        }
        this.width = info.width;
        this.height = info.height;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        DrawAlgorithms.drawReceiver(
                canvas,
                imageReceiver,
                imageReceiver,
                false,
                false,
                0,
                0,
                getIntrinsicWidth(),
                getIntrinsicHeight()
        );
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth () {
        return width;
    }

    @Override
    public int getIntrinsicHeight () {
        return height;
    }

    @Override
    public void performDestroy() {
        imageReceiver.destroy();
    }
}
