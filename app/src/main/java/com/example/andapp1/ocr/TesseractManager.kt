// TesseractManager.kt
package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.Exception

/**
 * Tesseract OCR 엔진을 관리하는 클래스
 * 언어 데이터 초기화, OCR 수행, 자원 관리를 담당
 */
class TesseractManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: TesseractManager? = null
        
        private const val TAG = "TesseractManager"
        private const val TESSDATA_DIR = "tesseract"
        
        fun getInstance(context: Context): TesseractManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TesseractManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 지원하는 언어 목록
    private val SUPPORTED_LANGUAGES = listOf("kor", "eng")
    
    /**
     * tessdata 파일들을 앱 내부 저장소로 복사
     * 앱 최초 실행 시 또는 파일이 손상된 경우 실행
     */
    fun initializeTessData(): Boolean {
        return try {
            Log.d(TAG, "tessdata 초기화 시작")
            
            // tessdata 디렉토리 생성
            val tessDir = File(context.filesDir, TESSDATA_DIR).apply {
                if (!exists()) {
                    mkdirs()
                    Log.d(TAG, "Tesseract 디렉토리 생성: $absolutePath")
                }
            }
            
            val tessdataDir = File(tessDir, "tessdata").apply {
                if (!exists()) {
                    mkdirs()
                    Log.d(TAG, "tessdata 디렉토리 생성: $absolutePath")
                }
            }
            
            // 각 언어별 traineddata 파일 복사
            var allSuccess = true
            for (language in SUPPORTED_LANGUAGES) {
                if (!copyTrainedDataFile(tessdataDir, language)) {
                    allSuccess = false
                }
            }
            
            if (allSuccess) {
                Log.d(TAG, "tessdata 초기화 완료")
                // 디렉토리 내용 확인
                tessdataDir.listFiles()?.forEach { file ->
                    Log.d(TAG, "tessdata 파일: ${file.name} (${file.length()} bytes)")
                }
            } else {
                Log.e(TAG, "일부 tessdata 파일 복사 실패")
            }
            
            allSuccess
        } catch (e: Exception) {
            Log.e(TAG, "tessdata 초기화 중 오류 발생", e)
            false
        }
    }
    
    /**
     * 개별 언어 파일 복사
     */
    private fun copyTrainedDataFile(tessdataDir: File, language: String): Boolean {
        val fileName = "$language.traineddata"
        val targetFile = File(tessdataDir, fileName)
        
        try {
            // 파일이 이미 존재하고 크기가 정상적인 경우 건너뛰기
            if (targetFile.exists() && targetFile.length() > 1000) { // 최소 1KB 이상
                Log.d(TAG, "$fileName 이미 존재 (${targetFile.length()} bytes)")
                return true
            }
            
            Log.d(TAG, "$fileName 복사 시작")
            
            // assets에서 파일 복사
            context.assets.open("tessdata/$fileName").use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 복사 완료 검증
            if (targetFile.exists() && targetFile.length() > 1000) {
                Log.d(TAG, "$fileName 복사 완료 (${targetFile.length()} bytes)")
                return true
            } else {
                Log.e(TAG, "$fileName 복사 실패 또는 파일 크기 이상")
                targetFile.delete() // 손상된 파일 삭제
                return false
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "$fileName 복사 중 오류 발생", e)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return false
        }
    }
    
    /**
     * OCR 수행 메인 함수
     * @param bitmap 전처리된 이미지
     * @return 인식된 텍스트
     */
    fun performOcr(bitmap: Bitmap): String {
        // tessdata 초기화 확인
        if (!initializeTessData()) {
            throw RuntimeException("tessdata 초기화에 실패했습니다.")
        }
        
        val tessBaseDir = File(context.filesDir, TESSDATA_DIR)
        
        Log.d(TAG, "OCR 수행 시작")
        Log.d(TAG, "tessdata 경로: ${tessBaseDir.absolutePath}")
        Log.d(TAG, "이미지 크기: ${bitmap.width}x${bitmap.height}")
        
        var tessApi: TessBaseAPI? = null
        
        try {
            tessApi = TessBaseAPI()
            
            // 다중 언어로 초기화 시도
            val language = "kor+eng" // 한국어와 영어 동시 지원
            
            Log.d(TAG, "Tesseract 초기화 시도: $language")
            val initialized = tessApi.init(tessBaseDir.absolutePath, language)
            
            if (!initialized) {
                throw RuntimeException("Tesseract 초기화 실패: $language")
            }
            
            Log.d(TAG, "Tesseract 초기화 성공")
            
            // OCR 설정 최적화
            configureOcrSettings(tessApi)
            
            // 이미지 설정 및 OCR 수행
            tessApi.setImage(bitmap)
            Log.d(TAG, "이미지 설정 완료")
            
            val recognizedText = tessApi.getUTF8Text()
            Log.d(TAG, "OCR 완료, 텍스트 길이: ${recognizedText?.length ?: 0}")
            
            return recognizedText?.trim() ?: ""
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR 수행 중 오류 발생", e)
            throw e
        } finally {
            // 자원 해제
            tessApi?.let { api ->
                try {
                    api.recycle()
                    Log.d(TAG, "Tesseract 자원 해제 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "Tesseract 자원 해제 중 오류", e)
                }
            }
        }
    }
    
    /**
     * OCR 최적화 설정
     */
    private fun configureOcrSettings(tessApi: TessBaseAPI) {
        try {
            Log.d(TAG, "OCR 설정 최적화 시작 - 2024 최신 방법론 적용")
            
            // ⭐ 핵심 개선 1: 페이지 분할 모드 변경 (웹서핑 권장사항)
            // PSM_SINGLE_BLOCK → PSM_AUTO로 변경 (더 정확한 레이아웃 분석)
            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            Log.d(TAG, "페이지 분할 모드: PSM_AUTO (레이아웃 분석 개선)")
            
            // ⭐ 핵심 개선 2: Tesseract 4.0 LSTM 엔진 최적화 (웹서핑 권장)
            tessApi.setVariable("tessedit_ocr_engine_mode", "1") // LSTM only
            
            // ⭐ 핵심 개선 3: 딕셔너리 완전 비활성화 (숫자 오인식 방지)
            tessApi.setVariable("load_system_dawg", "false")
            tessApi.setVariable("load_freq_dawg", "false")
            tessApi.setVariable("load_unambig_dawg", "false")
            tessApi.setVariable("load_punc_dawg", "false")
            tessApi.setVariable("load_number_dawg", "false")
            tessApi.setVariable("load_bigram_dawg", "false")
            Log.d(TAG, "딕셔너리 완전 비활성화 - 숫자 오인식 방지")
            
            // ⭐ 핵심 개선 4: 문자 화이트리스트 최적화 (실용적 설정)
            tessApi.setVariable("tessedit_char_whitelist", 
                "0123456789" +          // 숫자
                "원" +                  // 원화 표시
                "," +                   // 천단위 구분자
                " " +                   // 공백
                "총액합계금액" +          // 영수증 핵심 키워드  
                "가격부가세" +            // 추가 키워드
                "계산" +                // 추가 키워드
                "." +                   // 소수점
                "-" +                   // 마이너스
                "(" +                   // 괄호
                ")" +                   // 괄호
                ":" +                   // 콜론
                "\\n")                  // 줄바꿈
            Log.d(TAG, "문자 화이트리스트: 숫자+한글키워드+구분자 허용")
            
            // ⭐ 핵심 개선 5: 모든 보정 기능 비활성화 (웹서핑 권장)
            tessApi.setVariable("tessedit_enable_dict_correction", "false")
            tessApi.setVariable("tessedit_enable_bigram_correction", "false")
            tessApi.setVariable("classify_enable_adaptive_matcher", "false")
            tessApi.setVariable("classify_enable_learning", "false")
            Log.d(TAG, "모든 자동 보정 기능 비활성화")
            
            // ⭐ 핵심 개선 6: 신뢰도 임계값 최적화 (웹서핑 연구 결과)
            tessApi.setVariable("tessedit_reject_mode", "0")
            tessApi.setVariable("tessedit_reject_bad_qual_wds", "false")
            tessApi.setVariable("classify_char_norm_adj_midpoint", "96")
            tessApi.setVariable("classify_char_norm_adj_curl", "2")
            
            // ⭐ 핵심 개선 7: 세그멘테이션 최적화 (영수증 특화)
            tessApi.setVariable("textord_really_old_xheight", "false")
            tessApi.setVariable("textord_min_linesize", "1.25")
            tessApi.setVariable("preserve_interword_spaces", "1")
            
            // ⭐ 핵심 개선 8: 숫자 인식 정확도 향상 (2024 최신)
            tessApi.setVariable("tessedit_preserve_min_wd_len", "1")
            tessApi.setVariable("tessedit_preserve_osd", "0")
            tessApi.setVariable("tessedit_preserve_blk_wd_gap", "0")
            
            Log.d(TAG, "OCR 설정 최적화 완료 - 59,500원 오인식 문제 해결 특화")
            
        } catch (e: Exception) {
            Log.w(TAG, "OCR 설정 중 일부 오류 발생 (계속 진행)", e)
        }
    }
    
    /**
     * tessdata 파일 존재 여부 확인
     */
    fun isTessDataReady(): Boolean {
        val tessdataDir = File(context.filesDir, "$TESSDATA_DIR/tessdata")
        
        if (!tessdataDir.exists()) {
            return false
        }
        
        // 모든 필요한 언어 파일이 존재하는지 확인
        return SUPPORTED_LANGUAGES.all { language ->
            val file = File(tessdataDir, "$language.traineddata")
            file.exists() && file.length() > 1000
        }
    }
    
    /**
     * tessdata 파일 정보 로깅
     */
    fun logTessDataInfo() {
        val tessdataDir = File(context.filesDir, "$TESSDATA_DIR/tessdata")
        
        Log.d(TAG, "=== tessdata 정보 ===")
        Log.d(TAG, "디렉토리 존재: ${tessdataDir.exists()}")
        
        if (tessdataDir.exists()) {
            tessdataDir.listFiles()?.forEach { file ->
                Log.d(TAG, "파일: ${file.name}, 크기: ${file.length()} bytes")
            } ?: Log.d(TAG, "디렉토리가 비어있음")
        }
        Log.d(TAG, "==================")
    }
    
    /**
     * 초기화 메서드 (OcrViewModel에서 호출)
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "TesseractManager 초기화 시작")
            val result = initializeTessData()
            if (result) {
                Log.d(TAG, "TesseractManager 초기화 성공")
            } else {
                Log.e(TAG, "TesseractManager 초기화 실패")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "TesseractManager 초기화 중 오류", e)
            false
        }
    }
    
    /**
     * 영수증에서 금액 정보 추출 (ROI 기반 최적화)
     */
    fun extractReceiptAmount(bitmap: Bitmap): ReceiptAmount? {
        return try {
            Log.d(TAG, "영수증 금액 추출 시작 (ROI 기반)")
            val rawText = performOcr(bitmap)
            Log.d(TAG, "OCR 원본 텍스트: $rawText")
            
            if (rawText.isBlank()) {
                Log.w(TAG, "OCR 결과가 비어있음")
                return null
            }
            
            // ROI 기반 금액 추출 (개선된 방식)
            val extractedAmount = ReceiptAmountROI.extractAmountWithROI(rawText)
            
            if (extractedAmount != null) {
                Log.d(TAG, "ROI 금액 추출 성공: ${String.format("%,d", extractedAmount)}원")
                
                // ReceiptAmount 객체 생성 및 반환
                val receiptAmount = ReceiptAmount()
                receiptAmount.setMainAmount(extractedAmount)
                return receiptAmount
            } else {
                Log.w(TAG, "ROI 기반 금액 추출 실패")
                
                // 기존 방식으로 fallback
                Log.d(TAG, "기존 방식으로 fallback 시도")
                val receiptAmount = ReceiptAmount()
                receiptAmount.parseReceiptText(rawText)
                
                if (receiptAmount.getMainAmount() != null) {
                    Log.d(TAG, "기존 방식 금액 추출 성공: ${receiptAmount.getFormattedAmount()}")
                    receiptAmount
                } else {
                    Log.w(TAG, "모든 방식에서 금액 정보를 찾을 수 없음")
                    null
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "영수증 금액 추출 중 오류", e)
            null
        }
    }
} 