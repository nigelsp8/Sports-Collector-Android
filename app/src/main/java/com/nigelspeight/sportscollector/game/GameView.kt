package com.nigelspeight.sportscollector.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.nigelspeight.sportscollector.level.Level
import kotlin.math.min

/// Hosts a `GameScene` (the iOS `SKView`): drives one `update` + draw per display
/// frame and forwards touches in scene coordinates.
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val level: Level,
    private val onContinue: () -> Unit,
) : View(context) {
    private val dp = resources.displayMetrics.density
    private var scene: GameScene? = null
    private var lastFrameNanos = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val current = scene
        if (current == null) presentGameScene() else current.resize(w.toFloat(), h.toFloat())
    }

    /// Builds a fresh `GameScene` from the already-resolved `level` and presents
    /// it, replacing whatever scene is showing - called on first layout and again
    /// from the win overlay's Restart button, so restarting is just "do it again."
    private fun presentGameScene() {
        val newScene = GameScene(level, width.toFloat(), height.toFloat(), dp)
        newScene.onContinue = onContinue
        // Deferred so the old scene finishes handling the touch that triggered it.
        newScene.onRestart = { post { presentGameScene() } }
        scene = newScene
        newScene.didMove()
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(GameScene.BACKGROUND_COLOR)
        val scene = scene ?: return

        val now = System.nanoTime()
        // Clamped so a long gap (app backgrounded, debugger pause) doesn't make
        // the engine leap ahead.
        val deltaTime = if (lastFrameNanos == 0L) 0.0 else min((now - lastFrameNanos) / 1e9, 0.1)
        lastFrameNanos = now
        scene.update(deltaTime)

        val save = canvas.save()
        canvas.translate(width / 2f, height / 2f)
        scene.draw(canvas, 1f)
        canvas.restoreToCount(save)

        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scene = scene ?: return false
        val x = event.x - width / 2f
        val y = event.y - height / 2f
        val pointerId = event.getPointerId(0)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> scene.touchBegan(pointerId, x, y)
            MotionEvent.ACTION_MOVE -> scene.touchMoved(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scene.touchEnded(pointerId)
        }
        return true
    }
}
