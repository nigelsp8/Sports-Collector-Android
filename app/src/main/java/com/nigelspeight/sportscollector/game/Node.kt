package com.nigelspeight.sportscollector.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/// A minimal stand-in for SpriteKit's `SKNode` tree, so the iOS scene code ports
/// almost line-for-line. Coordinates differ from SpriteKit in one way: y grows
/// *downward* (Android canvas convention), with the scene's origin at its centre
/// exactly like the iOS scene's `anchorPoint = (0.5, 0.5)`.
open class Node {
    var x = 0f
    var y = 0f
    var xScale = 1f
    var yScale = 1f
    var alpha = 1f
    /// Radians, clockwise (y-down).
    var rotation = 0f
    var zPosition = 0f
    var isHidden = false

    var parent: Node? = null
        private set
    val children = ArrayList<Node>()

    private class RunningAction(val key: String?, val run: ActionRun)
    private val actions = ArrayList<RunningAction>()

    fun setScale(scale: Float) {
        xScale = scale
        yScale = scale
    }

    fun setPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun addChild(node: Node) {
        node.removeFromParent()
        node.parent = this
        children.add(node)
    }

    fun removeFromParent() {
        parent?.children?.remove(this)
        parent = null
    }

    fun removeAllChildren() {
        children.toList().forEach { it.removeFromParent() }
    }

    /// Runs `action`; a non-null `key` atomically replaces any action already
    /// running under that key, like `SKNode.run(_:withKey:)`.
    fun run(action: Action, key: String? = null) {
        if (key != null) actions.removeAll { it.key == key }
        actions.add(RunningAction(key, action.create()))
    }

    fun removeAllActions() {
        actions.clear()
    }

    /// Advances this node's actions, then its children's.
    open fun tick(dt: Double) {
        if (actions.isNotEmpty()) {
            for (entry in actions.toList()) {
                if (!actions.contains(entry)) continue // removed by an earlier action this tick
                if (entry.run.update(this, dt) >= 0) actions.remove(entry)
            }
        }
        if (children.isNotEmpty()) {
            for (child in children.toList()) child.tick(dt)
        }
    }

    fun draw(canvas: Canvas, parentAlpha: Float) {
        if (isHidden) return
        val a = parentAlpha * alpha
        if (a <= 0.004f) return
        val save = canvas.save()
        canvas.translate(x, y)
        if (rotation != 0f) canvas.rotate(Math.toDegrees(rotation.toDouble()).toFloat())
        if (xScale != 1f || yScale != 1f) canvas.scale(xScale, yScale)
        drawSelf(canvas, a)
        if (children.isNotEmpty()) {
            for (child in children.sortedBy { it.zPosition }) child.draw(canvas, a)
        }
        canvas.restoreToCount(save)
    }

    protected open fun drawSelf(canvas: Canvas, alpha: Float) {}

    /// Converts a point in this node's own coordinate space to the root's
    /// (rotation is ignored - nothing that needs converting is rotated).
    fun convertToRoot(px: Float, py: Float): PointF {
        var rx = px
        var ry = py
        var node: Node? = this
        while (node?.parent != null) {
            rx = node.x + rx * node.xScale
            ry = node.y + ry * node.yScale
            node = node.parent
        }
        return PointF(rx, ry)
    }

    /// Inverse of `convertToRoot`.
    fun convertFromRoot(px: Float, py: Float): PointF {
        val chain = generateSequence(this) { it.parent }.toList().dropLast(1).asReversed()
        var rx = px
        var ry = py
        for (node in chain) {
            rx = (rx - node.x) / node.xScale
            ry = (ry - node.y) / node.yScale
        }
        return PointF(rx, ry)
    }
}

/// A bitmap (optionally a sub-rect of one) at its natural pixel size.
class Texture(val bitmap: Bitmap, val src: Rect? = null) {
    val width: Int get() = src?.width() ?: bitmap.width
    val height: Int get() = src?.height() ?: bitmap.height
}

/// `SKSpriteNode` analogue: draws `texture` (or a flat `color`) centred on its
/// position at `width` x `height`.
open class SpriteNode(var texture: Texture? = null, var color: Int? = null) : Node() {
    var width = texture?.width?.toFloat() ?: 0f
    var height = texture?.height?.toFloat() ?: 0f

    /// Optional per-corner warp (like `SKWarpGeometryGrid` with a 1x1 grid):
    /// normalized destination positions for top-left, top-right, bottom-left,
    /// bottom-right, as 8 floats (x, y pairs, 0..1, y down).
    var warp: FloatArray? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val meshVerts = FloatArray(8)

    fun setSize(w: Float, h: Float) {
        width = w
        height = h
    }

    /// Hit test in this node's *parent's* coordinate space, like `SKNode.contains`.
    /// Unlike SpriteKit, hidden nodes never report a hit.
    fun contains(px: Float, py: Float): Boolean {
        if (isHidden) return false
        return abs(px - x) <= width * abs(xScale) / 2 && abs(py - y) <= height * abs(yScale) / 2
    }

    override fun drawSelf(canvas: Canvas, alpha: Float) {
        val tex = texture
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        dst.set(-width / 2, -height / 2, width / 2, height / 2)
        if (tex == null) {
            val c = color ?: return
            paint.color = c
            paint.alpha = ((c ushr 24) * alpha).toInt().coerceIn(0, 255)
            canvas.drawRect(dst, paint)
            return
        }
        val w = warp
        if (w != null && tex.src == null) {
            for (i in 0 until 4) {
                meshVerts[i * 2] = dst.left + w[i * 2] * width
                meshVerts[i * 2 + 1] = dst.top + w[i * 2 + 1] * height
            }
            canvas.drawBitmapMesh(tex.bitmap, 1, 1, meshVerts, 0, null, 0, paint)
        } else {
            canvas.drawBitmap(tex.bitmap, tex.src, dst, paint)
        }
    }
}

/// `SKLabelNode` analogue with optional outline (`NSAttributedString` stroke).
class LabelNode(fontSize: Float = 16f) : Node() {
    enum class HAlign { LEFT, CENTER, RIGHT }
    enum class VAlign { CENTER, BASELINE }

    var text: String = ""
    var fontSize: Float = fontSize
        set(value) {
            field = value
            fillPaint.textSize = value
            strokePaint.textSize = value
        }
    var color: Int = 0xFFFFFFFF.toInt()
    var strokeColor: Int? = null
    /// Stroke width as a percentage of font size, like `NSAttributedString.Key.strokeWidth`.
    var strokeWidthPercent = 0f
    var hAlign = HAlign.CENTER
    var vAlign = VAlign.CENTER

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    init {
        this.fontSize = fontSize
    }

    fun measureWidth(): Float = fillPaint.measureText(text)

    override fun drawSelf(canvas: Canvas, alpha: Float) {
        if (text.isEmpty()) return
        val width = measureWidth()
        val left = when (hAlign) {
            HAlign.LEFT -> 0f
            HAlign.CENTER -> -width / 2
            HAlign.RIGHT -> -width
        }
        val baseline = when (vAlign) {
            VAlign.BASELINE -> 0f
            VAlign.CENTER -> -(fillPaint.fontMetrics.ascent + fillPaint.fontMetrics.descent) / 2
        }
        val sc = strokeColor
        if (sc != null && strokeWidthPercent > 0) {
            // Drawn under the fill at double width, so the visible outline outside
            // the glyphs matches iOS's centred stroke.
            strokePaint.color = sc
            strokePaint.alpha = ((sc ushr 24) * alpha).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = fontSize * strokeWidthPercent / 100f * 2f
            canvas.drawText(text, left, baseline, strokePaint)
        }
        fillPaint.color = color
        fillPaint.alpha = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(text, left, baseline, fillPaint)
    }
}

/// `SKShapeNode(rectOf:cornerRadius:)` analogue.
class RoundedRectNode(var width: Float, var height: Float, var cornerRadius: Float) : Node() {
    var fillColor: Int? = null
    var strokeColor: Int? = null
    var lineWidth = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun contains(px: Float, py: Float): Boolean =
        !isHidden && abs(px - x) <= width / 2 && abs(py - y) <= height / 2

    override fun drawSelf(canvas: Canvas, alpha: Float) {
        rect.set(-width / 2, -height / 2, width / 2, height / 2)
        fillColor?.let {
            paint.style = Paint.Style.FILL
            paint.color = it
            paint.alpha = ((it ushr 24) * alpha).toInt()
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
        strokeColor?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = lineWidth
            paint.color = it
            paint.alpha = ((it ushr 24) * alpha).toInt()
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}

/// One-shot particle burst, standing in for the `SKEmitterNode` configuration
/// `GameScene.spawnCollectionBurst` uses (all particles emitted at once - the
/// original's 500/s birth rate emits its 10 particles within ~0.02s anyway).
class ParticleBurstNode(
    private val texture: Texture,
    count: Int,
    lifetime: Double,
    lifetimeRange: Double,
    speed: Float,
    speedRange: Float,
    private val baseSize: Float,
    scale: Float,
    scaleRange: Float,
    private val scaleSpeed: Float,
    private val alphaSpeed: Float,
    private val rotationSpeed: Float,
) : Node() {
    private class Particle(
        var px: Float, var py: Float, val vx: Float, val vy: Float,
        var scale: Float, var alpha: Float, var rotation: Float, val lifetime: Double, var age: Double = 0.0,
    )

    private val particles = List(count) {
        val angle = Math.random() * Math.PI * 2
        val s = speed + ((Math.random() - 0.5) * speedRange).toFloat()
        Particle(
            px = 0f, py = 0f,
            vx = (cos(angle) * s).toFloat(), vy = (sin(angle) * s).toFloat(),
            scale = scale + ((Math.random() - 0.5) * scaleRange).toFloat(),
            alpha = 1f,
            rotation = (Math.random() * Math.PI * 2).toFloat(),
            lifetime = lifetime + (Math.random() - 0.5) * lifetimeRange,
        )
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun tick(dt: Double) {
        super.tick(dt)
        val step = dt.toFloat()
        for (p in particles) {
            p.age += dt
            p.px += p.vx * step
            p.py += p.vy * step
            p.scale = (p.scale + scaleSpeed * step).coerceAtLeast(0f)
            p.alpha = (p.alpha + alphaSpeed * step).coerceAtLeast(0f)
            p.rotation += rotationSpeed * step
        }
    }

    override fun drawSelf(canvas: Canvas, alpha: Float) {
        for (p in particles) {
            if (p.age >= p.lifetime || p.alpha <= 0f || p.scale <= 0f) continue
            val half = baseSize * p.scale / 2
            paint.alpha = (p.alpha * alpha * 255).toInt().coerceIn(0, 255)
            val save = canvas.save()
            canvas.translate(p.px, p.py)
            canvas.rotate(Math.toDegrees(p.rotation.toDouble()).toFloat())
            dst.set(-half, -half, half, half)
            canvas.drawBitmap(texture.bitmap, texture.src, dst, paint)
            canvas.restoreToCount(save)
        }
    }
}
