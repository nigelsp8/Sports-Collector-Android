package com.nigelspeight.sportscollector.game

/// `SKActionTimingMode` analogue.
enum class Timing {
    LINEAR, EASE_IN, EASE_OUT;

    fun apply(t: Float): Float = when (this) {
        LINEAR -> t
        EASE_IN -> t * t
        EASE_OUT -> 1 - (1 - t) * (1 - t)
    }
}

/// A reusable action template, like `SKAction`: `create()` makes a fresh running
/// instance, so one template can be run on many nodes (or repeated).
fun interface Action {
    fun create(): ActionRun

    companion object {
        fun moveTo(x: Float, y: Float, duration: Double, timing: Timing = Timing.LINEAR): Action = Action {
            var sx = 0f
            var sy = 0f
            TimedRun(duration, timing, onStart = { sx = it.x; sy = it.y }) { node, t ->
                node.x = sx + (x - sx) * t
                node.y = sy + (y - sy) * t
            }
        }

        fun scaleTo(scale: Float, duration: Double, timing: Timing = Timing.LINEAR): Action = Action {
            var sx = 1f
            var sy = 1f
            TimedRun(duration, timing, onStart = { sx = it.xScale; sy = it.yScale }) { node, t ->
                node.xScale = sx + (scale - sx) * t
                node.yScale = sy + (scale - sy) * t
            }
        }

        fun fadeAlphaTo(alpha: Float, duration: Double): Action = Action {
            var start = 1f
            TimedRun(duration, Timing.LINEAR, onStart = { start = it.alpha }) { node, t ->
                node.alpha = start + (alpha - start) * t
            }
        }

        fun fadeIn(duration: Double): Action = fadeAlphaTo(1f, duration)
        fun fadeOut(duration: Double): Action = fadeAlphaTo(0f, duration)

        fun rotateBy(angle: Float, duration: Double): Action = Action {
            var start = 0f
            TimedRun(duration, Timing.LINEAR, onStart = { start = it.rotation }) { node, t ->
                node.rotation = start + angle * t
            }
        }

        fun wait(duration: Double): Action = Action { TimedRun(duration, Timing.LINEAR) { _, _ -> } }

        fun run(block: () -> Unit): Action = Action {
            ActionRun { _, dt -> block(); dt }
        }

        fun removeFromParent(): Action = Action {
            ActionRun { node, dt -> node.removeFromParent(); dt }
        }

        fun sequence(vararg actions: Action): Action = sequence(actions.toList())

        fun sequence(actions: List<Action>): Action = Action {
            val runs = actions.map { it.create() }
            var index = 0
            ActionRun { node, dt ->
                var remaining = dt
                while (index < runs.size) {
                    val leftover = runs[index].update(node, remaining)
                    if (leftover < 0) return@ActionRun -1.0
                    remaining = leftover
                    index++
                }
                remaining
            }
        }

        fun group(vararg actions: Action): Action = Action {
            val runs = actions.map { it.create() }
            val done = BooleanArray(runs.size)
            val leftovers = DoubleArray(runs.size)
            ActionRun { node, dt ->
                for (i in runs.indices) {
                    if (done[i]) continue
                    val leftover = runs[i].update(node, dt)
                    if (leftover >= 0) {
                        done[i] = true
                        leftovers[i] = leftover
                    }
                }
                if (done.all { it }) leftovers.min() else -1.0
            }
        }

        fun repeatForever(action: Action): Action = Action {
            var current = action.create()
            ActionRun { node, dt ->
                var remaining = dt
                var guard = 0
                while (guard++ < 100) {
                    val leftover = current.update(node, remaining)
                    if (leftover < 0) break
                    current = action.create()
                    remaining = leftover
                    if (remaining <= 0) break
                }
                -1.0
            }
        }
    }
}

/// A running action instance. `update` returns the unused part of `dt` once the
/// action has finished (>= 0), or a negative number while it's still running.
fun interface ActionRun {
    fun update(node: Node, dt: Double): Double
}

private class TimedRun(
    private val duration: Double,
    private val timing: Timing,
    private val onStart: (Node) -> Unit = {},
    private val apply: (Node, Float) -> Unit,
) : ActionRun {
    private var elapsed = 0.0
    private var started = false

    override fun update(node: Node, dt: Double): Double {
        if (!started) {
            started = true
            onStart(node)
        }
        elapsed += dt
        val t = if (duration <= 0) 1f else (elapsed / duration).coerceAtMost(1.0).toFloat()
        apply(node, timing.apply(t))
        return if (elapsed >= duration) elapsed - duration else -1.0
    }
}
