package com.example.gallery2

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewParent
import androidx.appcompat.widget.AppCompatImageView

class TouchImageView : AppCompatImageView {
    private val matrix = Matrix()
    private var mode = NONE

    private val last = PointF()
    private val start = PointF()
    private var minScale = 0.5f  // 允许缩小到原始大小的50%
    private var maxScale = 5f
    private lateinit var m: FloatArray

    private var redundantXSpace = 0f
    private var redundantYSpace = 0f
    private var width = 0f
    private var height = 0f
    private var saveScale = 1f
    private var right = 0f
    private var bottom = 0f
    private var origWidth = 0f
    private var origHeight = 0f
    private var bmWidth = 0f
    private var bmHeight = 0f

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    // 用于判断滑动方向
    private var isVerticalScrolling = false
    private val SCROLL_THRESHOLD = 20 // 滑动方向判断阈值

    constructor(context: Context) : super(context) {
        sharedConstructing(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        sharedConstructing(context)
    }

    private fun sharedConstructing(context: Context) {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        matrix.setTranslate(1f, 1f)
        m = FloatArray(9)
        imageMatrix = matrix
        scaleType = ScaleType.MATRIX

        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            matrix.getValues(m)
            val x = m[Matrix.MTRANS_X]
            val y = m[Matrix.MTRANS_Y]
            val curr = PointF(event.x, event.y)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    last.set(event.x, event.y)
                    start.set(last)
                    mode = DRAG
                    isVerticalScrolling = false // 重置垂直滑动标志
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    last.set(event.x, event.y)
                    start.set(last)
                    mode = ZOOM
                    isVerticalScrolling = false
                    // 禁止 ViewPager2 拦截触摸事件
                    disallowParentInterceptTouchEvent()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        var deltaX = curr.x - last.x
                        var deltaY = curr.y - last.y
                        val scaleWidth = (origWidth * saveScale).toInt().toFloat()
                        val scaleHeight = (origHeight * saveScale).toInt().toFloat()

                        // 检查图片是否需要水平或垂直滚动
                        val needHorizontalScroll = scaleWidth > width
                        val needVerticalScroll = scaleHeight > height

                        // 如果图片完全小于屏幕（缩小状态），不允许拖动
                        if (!needHorizontalScroll && !needVerticalScroll) {
                            // 图片完全小于屏幕，不需要拖动，允许ViewPager2翻页
                            allowParentInterceptTouchEvent()
                            last.set(curr.x, curr.y)
                        } else {
                            // 判断滑动方向（只在第一次移动时判断）
                            if (!isVerticalScrolling && needVerticalScroll) {
                                val totalDeltaX = Math.abs(curr.x - start.x)
                                val totalDeltaY = Math.abs(curr.y - start.y)

                                // 如果垂直滑动距离明显大于水平滑动，则认为是垂直滑动
                                if (totalDeltaY > SCROLL_THRESHOLD && totalDeltaY > totalDeltaX * 1.5) {
                                    isVerticalScrolling = true
                                    disallowParentInterceptTouchEvent()
                                }
                            }

                            // 如果是垂直滑动模式，禁止水平移动
                            if (isVerticalScrolling) {
                                deltaX = 0f
                                disallowParentInterceptTouchEvent()
                            } else if (needHorizontalScroll) {
                                // 只有在需要水平滚动且不是垂直滑动模式时才禁止ViewPager2拦截
                                disallowParentInterceptTouchEvent()
                            }

                            when {
                                scaleWidth < width -> {
                                    deltaX = 0f
                                    if (y + deltaY > 0)
                                        deltaY = -y
                                    else if (y + deltaY < -bottom)
                                        deltaY = -(y + bottom)
                                }
                                scaleHeight < height -> {
                                    deltaY = 0f
                                    if (x + deltaX > 0)
                                        deltaX = -x
                                    else if (x + deltaX < -right)
                                        deltaX = -(x + right)
                                }
                                else -> {
                                    // 图片同时需要水平和垂直滚动
                                    if (!isVerticalScrolling) {
                                        // 非垂直滑动模式时，允许水平移动
                                        if (x + deltaX > 0)
                                            deltaX = -x
                                        else if (x + deltaX < -right)
                                            deltaX = -(x + right)
                                    } else {
                                        // 垂直滑动模式时，禁止水平移动
                                        deltaX = 0f
                                    }

                                    if (y + deltaY > 0)
                                        deltaY = -y
                                    else if (y + deltaY < -bottom)
                                        deltaY = -(y + bottom)
                                }
                            }

                            matrix.postTranslate(deltaX, deltaY)
                            last.set(curr.x, curr.y)
                        }
                    } else if (mode == ZOOM) {
                        disallowParentInterceptTouchEvent()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    mode = NONE
                    isVerticalScrolling = false // 重置垂直滑动标志
                    allowParentInterceptTouchEvent()
                    val xDiff = Math.abs(curr.x - start.x).toInt()
                    val yDiff = Math.abs(curr.y - start.y).toInt()
                    if (xDiff < 3 && yDiff < 3)
                        performClick()
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                    isVerticalScrolling = false
                    allowParentInterceptTouchEvent()
                }
            }

            imageMatrix = matrix
            true
        }
    }

    private fun disallowParentInterceptTouchEvent() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun allowParentInterceptTouchEvent() {
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        width = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        height = MeasureSpec.getSize(heightMeasureSpec).toFloat()

        // 检查图片尺寸是否已设置，避免除以0
        if (bmWidth <= 0f || bmHeight <= 0f) {
            android.util.Log.d("TouchImageView", "onMeasure: bmWidth或bmHeight为0，跳过测量 bmWidth=$bmWidth, bmHeight=$bmHeight")
            return
        }

        android.util.Log.d("TouchImageView", "onMeasure: width=$width, height=$height, bmWidth=$bmWidth, bmHeight=$bmHeight")

        // 宽度铺满屏幕，按宽度缩放
        val scale = width / bmWidth
        matrix.setScale(scale, scale)

        // 计算图片缩放后的实际尺寸
        origWidth = scale * bmWidth
        origHeight = scale * bmHeight

        // 宽度铺满，水平无冗余；垂直方向居中显示
        redundantXSpace = 0f
        redundantYSpace = if (origHeight < height) {
            (height - origHeight) / 2  // 图片高度小于屏幕，垂直居中
        } else {
            0f  // 图片高度大于等于屏幕，顶部对齐
        }

        matrix.postTranslate(redundantXSpace, redundantYSpace)

        // 计算边界（用于拖动限制）
        right = origWidth * saveScale - width
        bottom = origHeight * saveScale - height

        imageMatrix = matrix
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= scaleFactor

            if (saveScale > maxScale) {
                saveScale = maxScale
                scaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                scaleFactor = minScale / origScale
            }

            val scaledWidth = origWidth * saveScale
            val scaledHeight = origHeight * saveScale
            right = scaledWidth - width
            bottom = scaledHeight - height

            if (scaledWidth <= width || scaledHeight <= height) {
                // 图片缩小到比屏幕小时，以屏幕中心为缩放中心
                matrix.postScale(scaleFactor, scaleFactor, width / 2, height / 2)

                // 缩小后需要调整位置使图片居中
                matrix.getValues(m)
                val x = m[Matrix.MTRANS_X]
                val y = m[Matrix.MTRANS_Y]

                // 计算居中所需的偏移量
                val targetX: Float
                val targetY: Float

                if (scaledWidth < width) {
                    // 图片宽度小于屏幕宽度，水平居中
                    targetX = (width - scaledWidth) / 2
                } else {
                    // 图片宽度大于等于屏幕宽度，限制在边界内
                    targetX = when {
                        x > 0 -> 0f
                        x < -right -> -right
                        else -> x
                    }
                }

                if (scaledHeight < height) {
                    // 图片高度小于屏幕高度，垂直居中
                    targetY = (height - scaledHeight) / 2
                } else {
                    // 图片高度大于等于屏幕高度，限制在边界内
                    targetY = when {
                        y > 0 -> 0f
                        y < -bottom -> -bottom
                        else -> y
                    }
                }

                // 移动到目标位置
                matrix.postTranslate(targetX - x, targetY - y)
            } else {
                // 图片比屏幕大时，以手指焦点为缩放中心
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                matrix.getValues(m)
                val x = m[Matrix.MTRANS_X]
                val y = m[Matrix.MTRANS_Y]

                // 限制在边界内
                if (scaleFactor < 1) {
                    if (x < -right)
                        matrix.postTranslate(-(x + right), 0f)
                    else if (x > 0)
                        matrix.postTranslate(-x, 0f)

                    if (y < -bottom)
                        matrix.postTranslate(0f, -(y + bottom))
                    else if (y > 0)
                        matrix.postTranslate(0f, -y)
                }
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // 使用实时的 View 尺寸，而不是缓存的值（解决折叠屏展开时尺寸不同步问题）
            val viewWidth = getWidth().toFloat()
            val viewHeight = getHeight().toFloat()

            // Double tap to zoom
            if (saveScale == 1f) {
                // 计算当前图片在 View 中的实际位置
                matrix.getValues(m)
                val currentTransX = m[Matrix.MTRANS_X]
                val currentTransY = m[Matrix.MTRANS_Y]
                val currentScaleX = m[Matrix.MSCALE_X]

                // 将触摸点坐标转换为相对于图片的坐标
                // 触摸点在图片坐标系中的位置
                val touchXInImage = (e.x - currentTransX) / currentScaleX
                val touchYInImage = (e.y - currentTransY) / currentScaleX

                // 使用正确的坐标进行缩放
                matrix.postScale(2f, 2f, e.x, e.y)
                saveScale = 2f

                // 更新边界
                right = origWidth * saveScale - viewWidth
                bottom = origHeight * saveScale - viewHeight

                // 确保放大后不超出边界
                matrix.getValues(m)
                val x = m[Matrix.MTRANS_X]
                val y = m[Matrix.MTRANS_Y]
                val scaledWidth = origWidth * saveScale
                val scaledHeight = origHeight * saveScale

                var deltaX = 0f
                var deltaY = 0f

                if (scaledWidth > viewWidth) {
                    if (x > 0) deltaX = -x
                    else if (x < -right) deltaX = -(x + right)
                }

                if (scaledHeight > viewHeight) {
                    if (y > 0) deltaY = -y
                    else if (y < -bottom) deltaY = -(y + bottom)
                }

                if (deltaX != 0f || deltaY != 0f) {
                    matrix.postTranslate(deltaX, deltaY)
                }
            } else {
                // 重置到初始状态：宽度铺满，垂直居中
                val baseScale = viewWidth / bmWidth
                matrix.setScale(baseScale, baseScale)
                val scaledHeight = baseScale * bmHeight
                val yOffset = if (scaledHeight < viewHeight) {
                    (viewHeight - scaledHeight) / 2  // 垂直居中
                } else {
                    0f  // 顶部对齐
                }
                matrix.postTranslate(0f, yOffset)
                saveScale = 1f

                // 更新缓存的尺寸和边界
                width = viewWidth
                height = viewHeight
                origWidth = baseScale * bmWidth
                origHeight = baseScale * bmHeight
                right = origWidth * saveScale - width
                bottom = origHeight * saveScale - height
            }

            imageMatrix = matrix
            invalidate()
            return true
        }
    }

    fun setImageBitmap(bmWidth: Int, bmHeight: Int) {
        android.util.Log.d("TouchImageView", "setImageBitmap(尺寸): bmWidth=$bmWidth, bmHeight=$bmHeight")
        this.bmWidth = bmWidth.toFloat()
        this.bmHeight = bmHeight.toFloat()
        // 重置缩放状态
        saveScale = 1f
        matrix.reset()
        matrix.setTranslate(1f, 1f)
        imageMatrix = matrix
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        android.util.Log.d("TouchImageView", "setImageBitmap(图片): bitmap=${if (bm != null) "${bm.width}x${bm.height}" else "null"}, bmWidth=$bmWidth, bmHeight=$bmHeight")
        super.setImageBitmap(bm)
        if (bm != null && bmWidth > 0 && bmHeight > 0) {
            android.util.Log.d("TouchImageView", "调用requestLayout和invalidate")
            // 图片设置后强制重新测量和布局
            requestLayout()
            invalidate()
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
