package org.thunderdog.challegram.unsorted;

import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.widget.RootFrameLayout;

import java.util.List;

@RequiresApi(Build.VERSION_CODES.R)
public class WindowInsetsAnimationCallback extends WindowInsetsAnimation.Callback {
    private final RootFrameLayout rootFrameLayout;

    public WindowInsetsAnimationCallback(RootFrameLayout rootFrameLayout) {
        super(WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP);
        this.rootFrameLayout = rootFrameLayout;
    }

    @NonNull
    @Override
    public WindowInsetsAnimation.Bounds onStart(@NonNull WindowInsetsAnimation animation, @NonNull WindowInsetsAnimation.Bounds bounds) {
        return super.onStart(animation, bounds);
    }

    @NonNull
    @Override
    public WindowInsets onProgress(@NonNull WindowInsets insets, @NonNull List<WindowInsetsAnimation> runningAnimations) {
        Insets navInset = insets.getInsets(WindowInsets.Type.navigationBars());
        Insets imeInset = insets.getInsets(WindowInsets.Type.ime());
        rootFrameLayout.partialBottomInset = Math.max(0, imeInset.bottom - navInset.bottom);
        rootFrameLayout.requestLayout();
        return insets;
    }

    @Override
    public void onEnd(@NonNull WindowInsetsAnimation animation) {
        super.onEnd(animation);
    }
}
