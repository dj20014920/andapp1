package com.example.andapp1.ocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ROI(Region of Interest) 선택을 위한 카메라 오버레이 뷰
 * 2025년 최신 CameraX 방법론 적용 - 실시간 영역 선택
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CameraOverlayView"
        private const val MIN_CROP_SIZE = 100f // 최소 선택 영역 크기
        private const val CORNER_RADIUS = 20f   // 모서리 둥글기
        private const val STROKE_WIDTH = 4f     // 테두리 두께
    }

    // ROI 영역 좌표
    private var roiRect = RectF()
    
    // 터치 상태 관리
    private var isDrawing = false
    private var startX = 0f
    private var startY = 0f
    
    // 그리기 도구들
    private val roiPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // 초록색
        strokeWidth = STROKE_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#80000000") // 반투명 검정
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // ROI 영역 변경 리스너
    private var onRoiChangedListener: ((RectF) -> Unit)? = null
    
    // 기본 ROI 영역 설정 (화면 중앙 60% 영역)
    private var defaultRoiInitialized = false
    
    init {
        // 하드웨어 가속 활성화
        setLayerType(LAYER_TYPE_HARDWARE, null)
        Log.d(TAG, "CameraOverlayView 초기화 완료")
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        if (!defaultRoiInitialized && w > 0 && h > 0) {
            // 영수증 금액 영역에 최적화된 기본 ROI 설정
            val centerX = w * 0.5f
            val centerY = h * 0.7f  // 화면 하단 70% 지점 (영수증 금액 위치)
            val roiWidth = w * 0.8f   // 가로 80%
            val roiHeight = h * 0.15f // 세로 15% (금액 영역 높이)
            
            roiRect.set(
                centerX - roiWidth / 2,
                centerY - roiHeight / 2,
                centerX + roiWidth / 2,
                centerY + roiHeight / 2
            )
            
            defaultRoiInitialized = true
            onRoiChangedListener?.invoke(roiRect)
            invalidate()
            
            Log.d(TAG, "기본 ROI 영역 설정: ${roiRect.toShortString()}")
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (roiRect.isEmpty) return
        
        // 1. 전체 화면을 어둡게 오버레이
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        
        // 2. ROI 영역을 투명하게 만들기 (PorterDuff.Mode.CLEAR)
        val roiPath = Path().apply {
            addRoundRect(roiRect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        }
        
        // 3. ROI 영역 클리어 (투명하게)
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawPath(roiPath, clearPaint)
        
        // 4. ROI 테두리 그리기
        canvas.drawRoundRect(roiRect, CORNER_RADIUS, CORNER_RADIUS, roiPaint)
        
        // 5. 모서리 핸들 그리기 (터치 포인트)
        drawCornerHandles(canvas)
        
        // 6. 가이드 텍스트
        drawGuideText(canvas)
    }
    
    /**
     * 모서리 핸들 그리기 (ROI 크기 조정용)
     */
    private fun drawCornerHandles(canvas: Canvas) {
        val handleSize = 24f
        
        // 네 모서리에 원형 핸들
        val corners = arrayOf(
            PointF(roiRect.left, roiRect.top),      // 좌상단
            PointF(roiRect.right, roiRect.top),     // 우상단
            PointF(roiRect.left, roiRect.bottom),   // 좌하단
            PointF(roiRect.right, roiRect.bottom)   // 우하단
        )
        
        corners.forEach { corner ->
            canvas.drawCircle(corner.x, corner.y, handleSize, cornerPaint)
            // 내부 작은 원 (시각적 효과)
            canvas.drawCircle(corner.x, corner.y, handleSize * 0.5f, Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            })
        }
    }
    
    /**
     * 사용자 가이드 텍스트
     */
    private fun drawGuideText(canvas: Canvas) {
        val guideText = "💰 금액 영역을 선택하세요"
        val textY = roiRect.top - 60f
        
        if (textY > textPaint.textSize) {
            canvas.drawText(guideText, width * 0.5f, textY, textPaint)
        }
        
        // ROI 크기 정보
        val sizeText = "${roiRect.width().toInt()} × ${roiRect.height().toInt()}"
        val sizeY = roiRect.bottom + 80f
        
        if (sizeY < height - textPaint.textSize) {
            val smallTextPaint = Paint(textPaint).apply {
                textSize = 32f
                alpha = 180
            }
            canvas.drawText(sizeText, width * 0.5f, sizeY, smallTextPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isDrawing = true
                Log.d(TAG, "터치 시작: ($startX, $startY)")
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    updateRoiRect(event.x, event.y)
                    invalidate()
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    updateRoiRect(event.x, event.y)
                    isDrawing = false
                    onRoiChangedListener?.invoke(roiRect)
                    Log.d(TAG, "ROI 영역 확정: ${roiRect.toShortString()}")
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    /**
     * 터치 좌표를 기반으로 ROI 영역 업데이트
     */
    private fun updateRoiRect(currentX: Float, currentY: Float) {
        val left = min(startX, currentX)
        val top = min(startY, currentY)
        val right = max(startX, currentX)
        val bottom = max(startY, currentY)
        
        // 최소 크기 보장
        val newWidth = max(right - left, MIN_CROP_SIZE)
        val newHeight = max(bottom - top, MIN_CROP_SIZE)
        
        // 화면 경계 내로 제한
        val constrainedLeft = max(0f, min(left, width - newWidth))
        val constrainedTop = max(0f, min(top, height - newHeight))
        val constrainedRight = min(width.toFloat(), constrainedLeft + newWidth)
        val constrainedBottom = min(height.toFloat(), constrainedTop + newHeight)
        
        roiRect.set(constrainedLeft, constrainedTop, constrainedRight, constrainedBottom)
    }
    
    /**
     * ROI 영역 변경 리스너 설정
     */
    fun setOnRoiChangedListener(listener: (RectF) -> Unit) {
        this.onRoiChangedListener = listener
    }
    
    /**
     * ROI 변경 리스너 설정 (유효성 포함)
     */
    fun setOnRoiChangeListener(listener: (Boolean) -> Unit) {
        this.onRoiChangedListener = { rect ->
            val isValid = rect.width() >= MIN_CROP_SIZE && rect.height() >= MIN_CROP_SIZE
            listener(isValid)
        }
    }
    
    /**
     * 현재 ROI 영역 반환
     */
    fun getCurrentRoi(): RectF = RectF(roiRect)
    
    /**
     * ROI 영역을 화면 비율로 변환 (0.0 ~ 1.0)
     */
    fun getRoiRatio(): RectF {
        return RectF(
            roiRect.left / width,
            roiRect.top / height,
            roiRect.right / width,
            roiRect.bottom / height
        )
    }
    
    /**
     * 영수증 금액 영역 추천 (하단 70% 지점)
     */
    fun setRecommendedAmountRoi() {
        if (width > 0 && height > 0) {
            val centerX = width * 0.5f
            val centerY = height * 0.75f  // 영수증 하단 금액 위치
            val roiWidth = width * 0.85f
            val roiHeight = height * 0.12f
            
            roiRect.set(
                centerX - roiWidth / 2,
                centerY - roiHeight / 2,
                centerX + roiWidth / 2,
                centerY + roiHeight / 2
            )
            
            onRoiChangedListener?.invoke(roiRect)
            invalidate()
            
            Log.d(TAG, "영수증 금액 영역 추천 적용")
        }
    }
    
    /**
     * ROI 영역 리셋
     */
    fun resetRoi() {
        defaultRoiInitialized = false
        onSizeChanged(width, height, 0, 0)
    }
} 