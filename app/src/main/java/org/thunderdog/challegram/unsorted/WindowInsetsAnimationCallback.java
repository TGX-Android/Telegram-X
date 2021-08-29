package org.thunderdog.challegram.unsorted;

import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.util.List;

@RequiresApi(Build.VERSION_CODES.R)
public class WindowInsetsAnimationCallback extends WindowInsetsAnimation.Callback {
    private final RootFrameLayout rootFrameLayout;
    private int bottomInset;

    public WindowInsetsAnimationCallback (RootFrameLayout rootFrameLayout) {
        super(WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP);
        this.rootFrameLayout = rootFrameLayout;
    }

    private void setRootPadding (int padding) {
        rootFrameLayout.setPadding(rootFrameLayout.getPaddingLeft(), rootFrameLayout.getPaddingTop(), rootFrameLayout.getPaddingRight(), padding);
    }

    @NonNull
    @Override
    public WindowInsetsAnimation.Bounds onStart (@NonNull WindowInsetsAnimation animation, @NonNull WindowInsetsAnimation.Bounds bounds) {
        if (!Settings.instance().needKeyboardAnimation()) {
            setRootPadding(0);
            return super.onStart(animation, bounds);
        }

        if (Keyboard.shouldSkipKeyboardAnimation) {
            // manually set insets because we skipped the animation
            if (bottomInset > 0) {
                // probably hiding keyboard
                setRootPadding(0);
                Keyboard.notifyHeightChanged(0);
            } else {
                // probably showing keyboard
                setRootPadding(Keyboard.getSize());
                Keyboard.notifyHeightChanged(Keyboard.getSize());
            }
        }

        return super.onStart(animation, bounds);
    }

    @NonNull
    @Override
    public WindowInsets onProgress (@NonNull WindowInsets insets, @NonNull List<WindowInsetsAnimation> runningAnimations) {
        Insets navInset = insets.getInsets(WindowInsets.Type.navigationBars());
        Insets imeInset = insets.getInsets(WindowInsets.Type.ime());
        bottomInset = Math.max(0, imeInset.bottom - navInset.bottom);

        if (!Keyboard.shouldSkipKeyboardAnimation && Settings.instance().needKeyboardAnimation()) {
            Keyboard.notifyHeightChanged(bottomInset);
            if (!Keyboard.shouldIgnoreKeyboardPadding) {
                setRootPadding(bottomInset);
            }
        }

        return insets;
    }

    @Override
    public void onEnd (@NonNull WindowInsetsAnimation animation) {
        Keyboard.shouldSkipKeyboardAnimation = false;
    }
}
