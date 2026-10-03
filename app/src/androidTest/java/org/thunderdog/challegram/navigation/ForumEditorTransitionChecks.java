package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation;
import org.thunderdog.challegram.stage8.SyntheticEnvironment;
import org.thunderdog.challegram.telegram.Stage8DeferredUi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.ui.ForumTopicEditController;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceList;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Real editor cleanup + NavigationProcessor + NavigationStack; never finishIfReady/openChat.
 * Activity hooks are inert and views belong to an unattached Android FrameLayout. A constructor-
 * free Tdlib shell exposes ONLY an in-memory Handler. No client/account is created, and that
 * reference is removed before destruction so no TDLib UI reference-count path can execute.
 * This verifies transition ordering/captured-stack cleanup, not a real animated Activity.
 */
public final class ForumEditorTransitionChecks {
  public static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    cases.add(new Stage8SyntheticInstrumentation.Case("editor_cleanup_waits_for_commit_and_uses_captured_navigation",
      ForumEditorTransitionChecks::afterCommit));
    cases.add(new Stage8SyntheticInstrumentation.Case("editor_cleanup_does_not_remove_current_controller",
      ForumEditorTransitionChecks::currentEditor));
  }

  private static void afterCommit (SyntheticEnvironment environment) throws Exception {
    try (Fixture fixture = new Fixture(environment)) {
      fixture.navigation.getStack().push(fixture.destination, true);
      fixture.host.addView(fixture.destination.getValue());
      fixture.navigation.animating = true;
      fixture.cleanup(); // Actual production animation-readiness cleanup callback.
      equal(1, fixture.ui.pendingCount(), "Busy navigation must defer editor removal");
      fixture.assertPendingEditor();
      fixture.ui.runNext(); // Still busy: one bounded retry, no stack/view mutation.
      equal(1, fixture.ui.pendingCount(), "Busy retry must remain deferred");
      fixture.assertPendingEditor();

      NavigationProcessor processor = (NavigationProcessor) get(fixture.navigation, "processor");
      processor.setNextAsCurrent();
      require(!fixture.navigation.isAnimating(), "Processor must finish the transition");
      require(fixture.navigation.detached.size() == 1 && fixture.navigation.detached.get(0) == fixture.editor,
        "Processor must detach the editor, not the earlier topics controller");
      require(fixture.editor.getValue().getParent() == null, "Editor view must not remain above the destination");
      require(fixture.navigation.getStack().getPrevious() == fixture.editor, "Editor remains in stack until deferred cleanup");
      equal(0, fixture.editor.stackSize(), "Detached editor's own navigation reference must be gone");
      equal(1, fixture.destination.focusCalls, "Destination receives focus at transition commit");

      set(fixture.editor, "tdlib", null); // No account/refcount operations during real destroy().
      fixture.ui.runNext();
      require(fixture.editor.isDestroyed(), "Deferred cleanup must destroy the completed editor");
      equal(2, fixture.navigation.getStackSize(), "Captured navigation must remove the detached editor");
      require(fixture.navigation.getStack().getCurrent() == fixture.destination &&
        fixture.navigation.getStack().getPrevious() == fixture.topics, "Back history must skip only the completed editor");
      require(fixture.destination.getValue().getParent() == fixture.host && fixture.host.getChildCount() == 1,
        "Only the destination view may remain in the content host");
      equal(1, fixture.navigation.detached.size(), "Cleanup must not detach an already-detached wrapper twice");
      equal(0, fixture.ui.pendingCount(), "Completed cleanup leaves no scheduled retry");
      fixture.cleanup(); // Delayed duplicate completion must be harmless.
      equal(2, fixture.navigation.getStackSize(), "Duplicate completion preserves navigation history");
    }
  }

  private static void currentEditor (SyntheticEnvironment environment) throws Exception {
    try (Fixture fixture = new Fixture(environment)) {
      fixture.cleanup();
      require(!fixture.editor.isDestroyed() && fixture.navigation.getStack().getCurrent() == fixture.editor,
        "Cleanup must not destroy an editor that is still the current destination");
      require(fixture.editor.getValue().getParent() == fixture.host, "Current editor view must remain attached");
      equal(2, fixture.navigation.getStackSize(), "Current-editor guard preserves the stack");
      equal(0, fixture.ui.pendingCount(), "Idle current-editor guard must not schedule a retry");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final TransitionNavigation navigation;
    final FrameLayout host;
    final ForumTopicEditController editor;
    final InertController topics, destination;
    final Stage8DeferredUi ui = new Stage8DeferredUi();

    Fixture (SyntheticEnvironment environment) throws Exception {
      Context context = environment.configure(1f, false, ThemeId.BLUE);
      // ThemeManager is already a scoped, account-free shell owned by SyntheticEnvironment.
      set(ThemeManager.instance(), "themeChangeListeners", new ReferenceList<>());
      host = new FrameLayout(context);
      navigation = new TransitionNavigation(context);
      topics = controller(context);
      destination = controller(context);
      editor = allocate(ForumTopicEditController.class);
      set(editor, "context", allocate(InertActivity.class));
      set(editor, "contentView", new View(context));
      set(editor, "navigationController", navigation);
      set(editor, "cells", new ArrayList<>());
      Tdlib handlerOnlyShell = allocate(Tdlib.class);
      set(handlerOnlyShell, "_handler", ui);
      set(editor, "tdlib", handlerOnlyShell);
      navigation.getStack().push(topics, true);
      navigation.getStack().push(editor, true);
      host.addView(editor.getValue());
    }

    void cleanup () throws Exception {
      invoke(editor, "removeCompletedEditor", new Class<?>[] {NavigationController.class}, navigation);
    }

    void assertPendingEditor () {
      require(navigation.getStack().getPrevious() == editor && !editor.isDestroyed(),
        "Animation-readiness callback must preserve the processor's previous controller");
      require(editor.getValue().getParent() == host && navigation.detached.isEmpty(),
        "Editor wrapper must remain for the navigation processor to detach");
      equal(3, navigation.getStackSize(), "Busy callback must not shorten the stack");
    }

    @Override public void close () {
      ui.clear();
      try {
        java.lang.reflect.Field tdlibField = ViewController.class.getDeclaredField("tdlib");
        tdlibField.setAccessible(true);
        tdlibField.set(editor, null);
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError("Cannot detach handler-only TDLib shell during fixture cleanup", failure);
      }
      if (!editor.isDestroyed()) editor.destroy();
      navigation.getStack().set(new ViewController<?>[0]);
      host.removeAllViews();
    }
  }

  private static InertController controller (Context context) throws Exception {
    InertController controller = allocate(InertController.class);
    set(controller, "contentView", new View(context));
    return controller;
  }

  private static final class TransitionNavigation extends NavigationController {
    boolean animating;
    final List<ViewController<?>> detached = new ArrayList<>();
    TransitionNavigation (Context context) { super(context); }
    @Override public boolean isAnimating () { return animating; }
    @Override void setIsAnimating (boolean value) { animating = value; }
    @Override public void setShadowsVisibility (int visibility) { /* No Activity shadows. */ }
    @Override public void removeChildWrapper (ViewController<?> controller) {
      detached.add(controller);
      View view = controller.getValue();
      if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
      controller.detachNavigationController();
    }
  }

  private static final class InertController extends ViewController<Void> {
    int focusCalls;
    private InertController (Context context) { super(context, null); }
    @Override protected View onCreateView (Context context) { throw new AssertionError("No controller view creation"); }
    @Override public int getId () { return 0; }
    @Override public void onFocus () { focusCalls++; }
  }

  /** Constructor-free fixture only; never attached, launched, resumed or used as a Context. */
  private static final class InertActivity extends BaseActivity {
    @Override protected boolean needDrawer () { return false; }
    @Override public void removeActivityListener (ActivityListener listener) { /* No Activity lifecycle. */ }
  }
}
