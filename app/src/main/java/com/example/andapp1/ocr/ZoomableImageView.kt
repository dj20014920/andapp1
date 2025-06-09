package com.example.andapp1.ocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 확대/축소/드래그가 가능한 ImageView
 * ROI 선택을 위한 이미지 뷰어
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    
    // 터치 모드
    private enum class Mode {
        NONE, DRAG, ZOOM
    }
    private var mode = Mode.NONE
    
    // 터치 포인트
    private val start = PointF()
    private val mid = PointF()
    private var oldDist = 1f
    private var minScale = 0.5f
    private var maxScale = 5.0f
    private var currentScale = 1.0f
    
    // 제스처 감지기
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    // 이미지 경계
    private val imageBounds = RectF()
    private val viewBounds = RectF()
    
    // 터치 슬롭 (터치 임계값)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    init {
        scaleType = ScaleType.MATRIX
        
        // 스케일 제스처 감지기
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newScale = currentScale * scaleFactor
                
                if (newScale in minScale..maxScale) {
                    currentScale = newScale
                    matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    checkBounds()
                    imageMatrix = matrix
                }
                return true
            }
        })
        
        // 일반 제스처 감지기 (더블 탭)
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 더블 탭으로 줌 토글
                if (currentScale > 1.5f) {
                    resetZoom()
                } else {
                    zoomToPoint(e.x, e.y, 2.0f)
                }
                return true
            }
        })
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 스케일 제스처 우선 처리
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start.set(event.x, event.y)
                mode = Mode.DRAG
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = Mode.ZOOM
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = Mode.NONE
            }
            
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.DRAG -> {
                        val dx = event.x - start.x
                        val dy = event.y - start.y
                        
                        // 터치 슬롭 체크
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            matrix.set(savedMatrix)
                            matrix.postTranslate(dx, dy)
                            checkBounds()
                            imageMatrix = matrix
                        }
                    }
                    
                    Mode.ZOOM -> {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            val scale = newDist / oldDist
                            val newScale = currentScale * scale
                            
                            if (newScale in minScale..maxScale) {
                                currentScale = newScale
                                matrix.set(savedMatrix)
                                matrix.postScale(scale, scale, mid.x, mid.y)
                                checkBounds()
                                imageMatrix = matrix
                            }
                        }
                    }
                    
                    Mode.NONE -> {}
                }
            }
        }
        
        return true
    }
    
    /**
     * 이미지가 뷰 경계를 벗어나지 않도록 제한
     */
    private fun checkBounds() {
        val drawable = drawable ?: return
        
        // 현재 이미지 경계 계산
        imageBounds.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        matrix.mapRect(imageBounds)
        
        // 뷰 경계
        viewBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        
        val deltaX = when {
            imageBounds.width() <= viewBounds.width() -> {
                // 이미지가 뷰보다 작으면 중앙 정렬
                viewBounds.centerX() - imageBounds.centerX()
            }
            imageBounds.left > viewBounds.left -> {
                // 왼쪽 여백이 생기면 왼쪽으로 이동
                viewBounds.left - imageBounds.left
            }
            imageBounds.right < viewBounds.right -> {
                // 오른쪽 여백이 생기면 오른쪽으로 이동
                viewBounds.right - imageBounds.right
            }
            else -> 0f
        }
        
        val deltaY = when {
            imageBounds.height() <= viewBounds.height() -> {
                // 이미지가 뷰보다 작으면 중앙 정렬
                viewBounds.centerY() - imageBounds.centerY()
            }
            imageBounds.top > viewBounds.top -> {
                // 위쪽 여백이 생기면 위로 이동
                viewBounds.top - imageBounds.top
            }
            imageBounds.bottom < viewBounds.bottom -> {
                // 아래쪽 여백이 생기면 아래로 이동
                viewBounds.bottom - imageBounds.bottom
            }
            else -> 0f
        }
        
        if (deltaX != 0f || deltaY != 0f) {
            matrix.postTranslate(deltaX, deltaY)
        }
    }
    
    /**
     * 특정 지점으로 줌
     */
    private fun zoomToPoint(x: Float, y: Float, scale: Float) {
        val targetScale = scale / currentScale
        currentScale = scale
        
        matrix.postScale(targetScale, targetScale, x, y)
        checkBounds()
        imageMatrix = matrix
    }
    
    /**
     * 줌 리셋
     */
    fun resetZoom() {
        currentScale = 1.0f
        matrix.reset()
        imageMatrix = matrix
        centerImage()
    }
    
    /**
     * 이미지 중앙 정렬
     */
    private fun centerImage() {
        val drawable = drawable ?: return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        
        val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        
        val dx = (viewWidth - drawableWidth * scale) * 0.5f
        val dy = (viewHeight - drawableHeight * scale) * 0.5f
        
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        
        currentScale = scale
        imageMatrix = matrix
    }
    
    /**
     * 현재 이미지 변환 매트릭스의 역변환을 반환
     * ROI 좌표를 원본 이미지 좌표로 변환할 때 사용
     */
    fun getInverseTransformMatrix(): Matrix {
        val inverse = Matrix()
        matrix.invert(inverse)
        return inverse
    }
    
    /**
     * 현재 이미지 경계 반환
     */
    fun getImageBounds(): RectF {
        val drawable = drawable ?: return RectF()
        val bounds = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        matrix.mapRect(bounds)
        return bounds
    }
    
    /**
     * 두 포인트 간 거리 계산
     */
    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }
    
    /**
     * 두 포인트의 중점 계산
     */
    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
    
    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (drawable != null) {
            centerImage()
        }
    }
} 