package lt.ehu.unwalked.core.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation.NavBackStackEntry

object Motion {
    const val SHORT = 160
    const val MEDIUM = 260

    private fun <T> standard(duration: Int = MEDIUM) = tween<T>(duration, easing = FastOutSlowInEasing)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(standard()) + slideInHorizontally(standard()) { it / 14 }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(standard(SHORT))
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(standard()) + slideInHorizontally(standard()) { -it / 14 }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(standard(SHORT)) + slideOutHorizontally(standard(SHORT)) { it / 14 }
    }

    val appear: EnterTransition = fadeIn(standard()) + expandVertically(standard())
    val disappear: ExitTransition = fadeOut(standard(SHORT)) + shrinkVertically(standard())

    val dropIn: EnterTransition = fadeIn(standard()) + slideInVertically(standard()) { -it / 2 }
    val liftOut: ExitTransition = fadeOut(standard(SHORT)) + slideOutVertically(standard(SHORT)) { -it / 2 }

    fun crossfade(): ContentTransform = fadeIn(standard()) togetherWith fadeOut(standard(SHORT))

    fun <T : Comparable<T>> counter(initial: T, target: T): ContentTransform {
        val up = target > initial
        return (fadeIn(standard()) + slideInVertically(standard()) { if (up) it / 2 else -it / 2 }) togetherWith
            (fadeOut(standard(SHORT)) + slideOutVertically(standard(SHORT)) { if (up) -it / 2 else it / 2 })
    }
}
