package com.example.andapp1.ocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.andapp1.R

/**
 * 카메라 위에 표시되는 오버레이 뷰
 * 영수증 촬영 가이드라인 제공
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private var overlayRect = RectF()
    private var cornerLength = 60f
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // 오버레이 사각형 계산 (화면 중앙 80% 크기)
        val margin = minOf(w, h) * 0.1f
        overlayRect = RectF(
            margin,
            h * 0.2f,
            w - margin,
            h * 0.8f
        )
        
        cornerLength = minOf(overlayRect.width(), overlayRect.height()) * 0.1f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 반투명 배경 (오버레이 영역 제외)
        drawDimmedBackground(canvas)
        
        // 촬영 가이드 박스
        drawGuideBox(canvas)
        
        // 모서리 표시
        drawCorners(canvas)
        
        // 안내 텍스트
        drawGuideText(canvas)
    }
    
    /**
     * 반투명 배경 그리기 (오버레이 영역 제외)
     */
    private fun drawDimmedBackground(canvas: Canvas) {
        fillPaint.color = Color.BLACK
        fillPaint.alpha = 150
        
        // 전체 배경
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        
        // 오버레이 영역은 투명하게
        fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawRect(overlayRect, fillPaint)
        fillPaint.xfermode = null
    }
    
    /**
     * 가이드 박스 그리기
     */
    private fun drawGuideBox(canvas: Canvas) {
        paint.color = ContextCompat.getColor(context, R.color.colorPrimary)
        paint.strokeWidth = 6f
        
        // 박스 테두리
        canvas.drawRect(overlayRect, paint)
    }
    
    /**
     * 모서리 표시 그리기
     */
    private fun drawCorners(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.strokeWidth = 8f
        
        // 왼쪽 위
        canvas.drawLine(overlayRect.left, overlayRect.top, overlayRect.left + cornerLength, overlayRect.top, paint)
        canvas.drawLine(overlayRect.left, overlayRect.top, overlayRect.left, overlayRect.top + cornerLength, paint)
        
        // 오른쪽 위
        canvas.drawLine(overlayRect.right - cornerLength, overlayRect.top, overlayRect.right, overlayRect.top, paint)
        canvas.drawLine(overlayRect.right, overlayRect.top, overlayRect.right, overlayRect.top + cornerLength, paint)
        
        // 왼쪽 아래
        canvas.drawLine(overlayRect.left, overlayRect.bottom - cornerLength, overlayRect.left, overlayRect.bottom, paint)
        canvas.drawLine(overlayRect.left, overlayRect.bottom, overlayRect.left + cornerLength, overlayRect.bottom, paint)
        
        // 오른쪽 아래
        canvas.drawLine(overlayRect.right, overlayRect.bottom - cornerLength, overlayRect.right, overlayRect.bottom, paint)
        canvas.drawLine(overlayRect.right - cornerLength, overlayRect.bottom, overlayRect.right, overlayRect.bottom, paint)
    }
    
    /**
     * 안내 텍스트 그리기
     */
    private fun drawGuideText(canvas: Canvas) {
        // 위쪽 안내 텍스트
        val topText = "영수증을 프레임 안에 맞춰주세요"
        canvas.drawText(
            topText,
            width / 2f,
            overlayRect.top - 40f,
            textPaint
        )
        
        // 아래쪽 안내 텍스트
        textPaint.textSize = 36f
        val bottomText = "촬영 버튼을 눌러 사진을 찍어주세요"
        canvas.drawText(
            bottomText,
            width / 2f,
            overlayRect.bottom + 80f,
            textPaint
        )
    }
    
    /**
     * 오버레이 영역 반환
     */
    fun getOverlayRect(): RectF {
        return RectF(overlayRect)
    }
    
    /**
     * 점이 오버레이 영역 안에 있는지 확인
     */
    fun isPointInOverlay(x: Float, y: Float): Boolean {
        return overlayRect.contains(x, y)
    }
} 