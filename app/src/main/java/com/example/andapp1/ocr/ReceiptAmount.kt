package com.example.andapp1.ocr

import android.util.Log
import java.util.regex.Pattern

/**
 * 영수증 금액 정보 데이터 클래스
 */
class ReceiptAmount {
    var totalAmount: Int? = null           // 총 금액
    var subtotal: Int? = null              // 소계
    var items: MutableList<Pair<String, Int>> = mutableListOf() // 개별 항목들
    var rawText: String = ""                // 원본 텍스트
    
    companion object {
        private const val TAG = "ReceiptAmount"
        
        // 금액 패턴들 (우선순위 순서로 정렬)
        private val AMOUNT_PATTERNS = listOf(
            // 1순위: 명확한 합계/총액 표현
            Pattern.compile("합\\s*계\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("총\\s*액\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("합계\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("총액\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Total\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)", Pattern.CASE_INSENSITIVE),
            
            // 2순위: 일반적인 계 표현
            Pattern.compile("계\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("소계\\s*[:\\-]?\\s*(\\d{1,3}(?:[,.]?\\d{3})*)원?", Pattern.CASE_INSENSITIVE),
            
            // 3순위: 마지막 라인의 큰 금액 (50,000원 이상)
            Pattern.compile("\\b(5\\d{4,}|[6-9]\\d{4,}|\\d{6,})원?\\b"),
            
            // 4순위: 일반적인 금액 표현 (10,000원 이상)
            Pattern.compile("\\b([1-9]\\d{4,})원?\\b"),
            
            // 5순위: 더 관대한 숫자 패턴 (콤마 없이)
            Pattern.compile("\\b(\\d{5,})\\b"),
            
            // 6순위: 매우 관대한 패턴 (공백 허용)
            Pattern.compile("(\\d\\s*\\d\\s*\\d\\s*\\d\\s*\\d+)")
        )
    }
    /**
     * 주요 금액 반환 (총액 > 소계 우선순위)
     */
    fun getMainAmount(): Int? = totalAmount ?: subtotal
    
    /**
     * 주요 금액 설정하기 (ROI 방식에서 사용)
     */
    fun setMainAmount(amount: Int) {
        this.totalAmount = amount
    }
    
    /**
     * 포맷된 금액 문자열 반환
     */
    fun getFormattedAmount(): String {
        val mainAmount = getMainAmount()
        return if (mainAmount != null) {
            "${String.format("%,d", mainAmount)}원"
        } else {
            "금액 정보 없음"
        }
    }
    
    /**
     * 상세 정보 문자열 반환
     */
    fun getDetailedInfo(): String {
        val result = StringBuilder()
        
        getMainAmount()?.let { amount ->
            result.append("💰 총 금액: ${String.format("%,d", amount)}원\n")
        }
        
        if (items.isNotEmpty()) {
            result.append("\n📝 항목별 내역:\n")
            items.forEachIndexed { index, (name, amount) ->
                result.append("${index + 1}. $name: ${String.format("%,d", amount)}원\n")
            }
        }
        
        return result.toString().trim()
    }
    
    /**
     * OCR 텍스트에서 금액 정보 파싱
     */
    fun parseReceiptText(text: String) {
        rawText = text
        Log.d(TAG, "영수증 텍스트 파싱 시작")
        Log.d(TAG, "원본 텍스트: $text")
        
        // 텍스트 전처리 (공백 정리, 라인 정리)
        val cleanText = text.replace("\\s+".toRegex(), " ").trim()
        val lines = text.split("\\n".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        
        Log.d(TAG, "정리된 텍스트: $cleanText")
        Log.d(TAG, "라인 개수: ${lines.size}")
        
        // 1. 주요 금액 추출 (합계, 총액 등)
        extractMainAmounts(cleanText, lines)
        
        // 2. 개별 항목 추출
        extractItems(lines)
        
        Log.d(TAG, "파싱 완료 - 총액: $totalAmount, 소계: $subtotal, 항목 수: ${items.size}")
    }
    
    /**
     * 주요 금액 추출 (개선된 로직)
     */
    private fun extractMainAmounts(cleanText: String, lines: List<String>) {
        Log.d(TAG, "금액 추출 시작 - 라인 수: ${lines.size}")
        
        // 1. 패턴별로 금액 검색 (우선순위 순)
        for ((index, pattern) in AMOUNT_PATTERNS.withIndex()) {
            val allMatches = mutableListOf<Pair<Int, String>>()
            
            // 전체 텍스트에서 검색
            val matcher = pattern.matcher(cleanText)
            while (matcher.find()) {
                val amountStr = matcher.group(1)?.replace("[,.]".toRegex(), "") ?: continue
                val amount = amountStr.toIntOrNull() ?: continue
                
                // 유효한 금액 범위 확인 (100원 ~ 1,000,000원)
                if (amount in 100..1000000) {
                    allMatches.add(Pair(amount, matcher.group(0)))
                    Log.d(TAG, "패턴 ${index}: ${amount}원 발견 (${matcher.group(0)})")
                }
            }
            
            // 가장 큰 금액 선택
            val bestMatch = allMatches.maxByOrNull { it.first }
            if (bestMatch != null) {
                when {
                    index <= 4 -> { // 명확한 합계/총액 패턴
                        totalAmount = bestMatch.first
                        Log.d(TAG, "총액 확정: ${bestMatch.first}원 (패턴: ${bestMatch.second})")
                        return // 명확한 패턴을 찾았으므로 종료
                    }
                    index <= 6 -> { // 일반적인 계 표현
                        if (totalAmount == null) {
                            totalAmount = bestMatch.first
                            Log.d(TAG, "총액 (계): ${bestMatch.first}원 (패턴: ${bestMatch.second})")
                        }
                    }
                    else -> { // 일반 금액
                        if (totalAmount == null && subtotal == null) {
                            totalAmount = bestMatch.first
                            Log.d(TAG, "총액 (일반): ${bestMatch.first}원 (패턴: ${bestMatch.second})")
                        }
                    }
                }
            }
        }
        
        // 2. 패턴으로 찾지 못한 경우 라인별 분석
        if (totalAmount == null && subtotal == null) {
            extractFromLastLines(lines)
        }
    }
    
    /**
     * 마지막 라인들에서 금액 추출 (패턴 매칭 실패 시 fallback)
     */
    private fun extractFromLastLines(lines: List<String>) {
        Log.d(TAG, "마지막 라인들에서 금액 추출 시도")
        
        // 마지막 5개 라인에서 큰 금액 찾기
        val lastLines = lines.takeLast(5)
        val candidates = mutableListOf<Pair<Int, String>>()
        
        for (line in lastLines) {
            Log.d(TAG, "라인 분석: $line")
            
            // 5자리 이상 숫자 찾기 (10,000원 이상)
            val amountPattern = Pattern.compile("\\b(\\d{5,})\\b")
            val matcher = amountPattern.matcher(line)
            
            while (matcher.find()) {
                val amountStr = matcher.group(1) ?: continue
                val amount = amountStr.toIntOrNull() ?: continue
                
                if (amount in 1000..1000000) { // 1,000원 ~ 1,000,000원
                    candidates.add(Pair(amount, line))
                    Log.d(TAG, "후보 금액: ${amount}원 (라인: $line)")
                }
            }
        }
        
        // 가장 큰 금액 선택
        val bestCandidate = candidates.maxByOrNull { it.first }
        if (bestCandidate != null) {
            totalAmount = bestCandidate.first
            Log.d(TAG, "마지막 라인에서 총액 추출: ${bestCandidate.first}원")
        } else {
            Log.w(TAG, "마지막 라인에서도 유효한 금액을 찾지 못함")
        }
    }
    
    /**
     * 개별 항목 추출
     */
    private fun extractItems(lines: List<String>) {
        val itemPattern = Pattern.compile("(.+?)\\s+(\\d{1,3}(?:,\\d{3})*)원?\\s*$")
        
        for (line in lines) {
            // 합계, 총액 등 제외
            if (line.contains("합계") || line.contains("총액") || 
                line.contains("계") || line.contains("Total") || 
                line.contains("소계")) {
                continue
            }
            
            val matcher = itemPattern.matcher(line.trim())
            if (matcher.find()) {
                val itemName = matcher.group(1)?.trim() ?: continue
                val amountStr = matcher.group(2)?.replace(",", "") ?: continue
                val amount = amountStr.toIntOrNull() ?: continue
                
                // 유효한 항목명과 금액인지 확인
                if (itemName.length >= 2 && amount >= 100) {
                    items.add(Pair(itemName, amount))
                    Log.d(TAG, "항목 추출: $itemName = ${amount}원")
                }
            }
        }
        
        // 금액 순으로 정렬 (큰 금액부터)
        items.sortByDescending { it.second }
        
        // 최대 10개 항목만 유지
        if (items.size > 10) {
            items = items.take(10).toMutableList()
        }
    }
}

/**
 * ROI 기반 금액 추출 시스템
 * 영수증에서 금액 관련 키워드 주변 영역만 분석하여 정확도 향상
 */
class ReceiptAmountROI {
    companion object {
        private const val TAG = "ReceiptAmountROI"
        
        // 금액 관련 핵심 키워드 (우선순위 순)
        private val AMOUNT_KEYWORDS = listOf(
            "합계", "총합계", "총액", "계", "소계", "결제금액", "받을금액", "total", "amount", "원"
        )
        
        // 제외할 키워드들 (카드번호, 승인번호 등)
        private val EXCLUDE_KEYWORDS = listOf(
            "카드", "card", "승인", "approval", "거래", "transaction", "번호", "number",
            "사업자", "business", "전화", "tel", "phone", "주소", "address"
        )
        
        /**
         * ROI 기반 금액 추출 (메인 메서드)
         */
        fun extractAmountWithROI(ocrText: String): Int? {
            Log.d(TAG, "ROI 기반 금액 추출 시작")
            Log.d(TAG, "입력 텍스트 길이: ${ocrText.length}")
            
            // 1단계: 텍스트를 라인별로 분리
            val lines = ocrText.split("\n").filter { it.trim().isNotEmpty() }
            Log.d(TAG, "총 라인 수: ${lines.size}")
            
            // 2단계: 금액 관련 ROI 라인들 추출
            val amountROILines = extractAmountROILines(lines)
            Log.d(TAG, "금액 ROI 라인들: $amountROILines")
            
            // 3단계: ROI 라인들에서 금액 추출
            for (roiLine in amountROILines) {
                val amount = extractAmountFromROILine(roiLine)
                if (amount != null && isValidAmountRange(amount)) {
                    Log.d(TAG, "ROI에서 추출된 최종 금액: ${amount}원")
                    return amount
                }
            }
            
            // 4단계: ROI에서 실패시 기존 방식으로 fallback
            Log.d(TAG, "ROI 방식 실패, 기존 방식으로 fallback")
            val receiptAmount = ReceiptAmount()
            receiptAmount.parseReceiptText(ocrText)
            return receiptAmount.getMainAmount()
        }
        
        /**
         * 금액 관련 ROI 라인들 추출
         */
        private fun extractAmountROILines(lines: List<String>): List<String> {
            val roiLines = mutableListOf<String>()
            
            for (line in lines) {
                val cleanLine = line.trim()
                
                // 제외할 키워드가 포함된 라인은 스킵
                if (EXCLUDE_KEYWORDS.any { keyword -> 
                    cleanLine.contains(keyword, ignoreCase = true) 
                }) {
                    Log.d(TAG, "제외된 라인: $cleanLine")
                    continue
                }
                
                // 금액 키워드가 포함되고 숫자가 있는 라인 우선
                val hasAmountKeyword = AMOUNT_KEYWORDS.any { keyword ->
                    cleanLine.contains(keyword, ignoreCase = true)
                }
                
                val hasNumbers = cleanLine.contains(Regex("\\d"))
                
                if (hasAmountKeyword && hasNumbers) {
                    Log.d(TAG, "금액 키워드 ROI 라인: $cleanLine")
                    roiLines.add(cleanLine)
                }
            }
            
            // 키워드 ROI가 없으면 숫자가 많은 라인들 중에서 찾기
            if (roiLines.isEmpty()) {
                Log.d(TAG, "키워드 ROI 없음, 숫자 밀도 높은 라인 검색")
                
                for (line in lines) {
                    val cleanLine = line.trim()
                    val numberDensity = countNumbers(cleanLine)
                    
                    // 숫자가 5개 이상이고 금액 패턴이 있는 라인
                    if (numberDensity >= 5 && containsAmountPattern(cleanLine)) {
                        Log.d(TAG, "숫자 밀도 ROI 라인: $cleanLine")
                        roiLines.add(cleanLine)
                    }
                }
            }
            
            return roiLines.distinct()
        }
        
        /**
         * ROI 라인에서 금액 추출
         */
        private fun extractAmountFromROILine(line: String): Int? {
            Log.d(TAG, "ROI 라인 분석: $line")
            
            // 다양한 금액 패턴들 (우선순위 순)
            val patterns = listOf(
                // 키워드 + 금액 패턴들
                Regex("(?:합계|총합계|총액|계|소계|결제금액|받을금액|total|amount)\\s*[:\\-]?\\s*([\\d,]+)\\s*원?"),
                Regex("([\\d,]+)\\s*원\\s*(?:합계|총합계|총액|계|소계)"),
                
                // 일반 금액 패턴들 (콤마 포함)
                Regex("([1-9]\\d{2,2}(?:,\\d{3})+)\\s*원?"),  // 000,000 형태
                Regex("([1-9]\\d{4,6})\\s*원?"),             // 00000 형태 (콤마 없음)
                
                // 스페이스로 분리된 숫자들
                Regex("([1-9]\\d?)\\s+(\\d{3})\\s+(\\d{3})"),  // 5 9 500 형태
                Regex("([1-9]\\d{1,3})\\s+(\\d{3})")          // 59 500 형태
            )
            
            for ((index, pattern) in patterns.withIndex()) {
                val matches = pattern.findAll(line)
                
                for (match in matches) {
                    val amountStr = when {
                        match.groupValues.size > 2 -> {
                            // 스페이스 분리된 경우 (패턴 5, 6)
                            match.groupValues.drop(1).joinToString("")
                        }
                        else -> {
                            match.groupValues[1]
                        }
                    }
                    
                    val cleanAmount = amountStr.replace(Regex("[,\\s]"), "")
                    val amount = cleanAmount.toIntOrNull()
                    
                    if (amount != null && isValidAmountRange(amount)) {
                        Log.d(TAG, "패턴 ${index + 1}에서 추출: ${amountStr} → ${amount}원")
                        return amount
                    }
                }
            }
            
            return null
        }
        
        /**
         * 라인의 숫자 개수 세기
         */
        private fun countNumbers(text: String): Int {
            return text.count { it.isDigit() }
        }
        
        /**
         * 금액 패턴 포함 여부 확인
         */
        private fun containsAmountPattern(text: String): Boolean {
            // 원화 표시나 콤마가 있는 숫자 패턴
            return text.contains(Regex("[\\d,]+\\s*원")) || 
                   text.contains(Regex("\\d{1,3}(?:,\\d{3})+")) ||
                   text.contains("원")
        }
        
        /**
         * 유효한 금액 범위 확인
         */
        private fun isValidAmountRange(amount: Int): Boolean {
            return amount in 100..1000000  // 100원 ~ 100만원
        }
    }
} 