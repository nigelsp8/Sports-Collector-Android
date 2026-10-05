package com.nigelspeight.sportscollector.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nigelspeight.sportscollector.level.Level
import kotlin.math.min

/// Hosts a `GameScene` (the iOS `SKView`): drives one `update` + draw per display
/// frame and forwards touches in scene coordinates.
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val level: Level,
    private val onContinue: () -> Unit,
    private val onQuit: () -> Unit,
) : View(context) {
    private val dp = resources.displayMetrics.density
    private var scene: GameScene? = null
    private var lastFrameNanos = 0L

    private var insets = Insets.NONE

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            updateInsets(windowInsets)
            windowInsets
        }
    }

    /// The area the board and HUD must stay inside: clear of the status bar and
    /// any display cutout. The status bar is hidden while playing, so its
    /// *potential* height is used (it can still be swiped in on top of the game,
    /// and the cutout/rounded corners live there regardless). The bottom edge
    /// keeps the iOS layout, which runs the game view to the screen's bottom.
    private fun updateInsets(windowInsets: WindowInsetsCompat) {
        val statusBars = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val newInsets = Insets.of(cutout.left, maxOf(statusBars.top, cutout.top), cutout.right, cutout.bottom)
        if (newInsets == insets) return
        insets = newInsets
        scene?.resize(safeArea())
    }

    private fun safeArea() = SafeArea(
        screenWidth = width.toFloat(),
        screenHeight = height.toFloat(),
        insetLeft = insets.left.toFloat(),
        insetTop = insets.top.toFloat(),
        insetRight = insets.right.toFloat(),
        insetBottom = insets.bottom.toFloat(),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        ViewCompat.getRootWindowInsets(this)?.let(::updateInsets)
        val current = scene
        if (current == null) presentGameScene() else current.resize(safeArea())
    }

    /// Builds a fresh `GameScene` from the already-resolved `level` and presents
    /// it, replacing whatever scene is showing - called on first layout and again
    /// from the win overlay's Restart button, so restarting is just "do it again."
    private fun presentGameScene() {
        val newScene = GameScene(level, safeArea(), dp)
        newScene.onContinue = onContinue
        newScene.onQuit = onQuit
        // Deferred so the old scene finishes handling the touch that triggered it.
        newScene.onRestart = { post { presentGameScene() } }
        scene = newScene
        newScene.didMove()
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
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
