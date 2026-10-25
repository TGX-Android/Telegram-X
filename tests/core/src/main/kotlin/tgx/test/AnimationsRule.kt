package tgx.test

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Animations are on by default, because some bugs are visible only with them,
 * e.g. stutters during navigation between screens.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DisableAnimations

private val ANIMATION_SCALES = listOf(
  "window_animation_scale",
  "transition_animation_scale",
  // Also turns off animators of the app, as they check ValueAnimator.areAnimatorsEnabled()
  "animator_duration_scale"
)

class AnimationsRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    val disableAnimations = description.getAnnotation(DisableAnimations::class.java) != null ||
      description.testClass?.getAnnotation(DisableAnimations::class.java) != null
    if (!disableAnimations) {
      return base
    }
    return object : Statement() {
      override fun evaluate() = withAnimationScale(0f) {
        base.evaluate()
      }
    }
  }
}

fun withAnimationScale(scale: Float, block: () -> Unit) {
  val scales = ANIMATION_SCALES.associateWith {
    runShellCommand("settings get global $it").trim()
  }
  scales.keys.forEach {
    runShellCommand("settings put global $it $scale")
  }
  try {
    block()
  } finally {
    scales.forEach { (name, value) ->
      if (value == "null") {
        runShellCommand("settings delete global $name")
      } else {
        runShellCommand("settings put global $name $value")
      }
    }
  }
}
