package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.lambda.Destroyable;

public class ReactionWithCheckbox extends BaseView implements Destroyable {

    private final ComplexReceiver iconReceiver;
    private DoubleImageReceiver preview;
    private GifReceiver gifReceiver;

    public ReactionWithCheckbox (Context context, Tdlib tdlib) {
        super(context, tdlib);
        Views.setClickable(this);
        RippleSupport.setTransparentSelector(this);
        iconReceiver = new ComplexReceiver(this);
        this.preview = new DoubleImageReceiver(this, 0);
        this.gifReceiver = new GifReceiver(this);
    }

    private View.OnClickListener onClickListener;
    private View.OnLongClickListener onLongClickListener;

    public void setClickListener (View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
        this.onClickListener = onClickListener;
        this.onLongClickListener = onLongClickListener;
    }

    @Override
    public void performDestroy() {
        iconReceiver.performDestroy();
        preview.destroy();
        gifReceiver.destroy();
    }

    public void attach () {
        iconReceiver.attach();
        preview.attach();
        gifReceiver.attach();
    }

    public void detach () {
        iconReceiver.detach();
        preview.detach();
        gifReceiver.detach();
    }
}
