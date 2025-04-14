// ScrapDialogHelper.kt
package com.example.andapp1

import android.content.Context
import android.widget.Toast

object ScrapDialogHelper {
    fun showScrapListDialog(context: Context, roomCode: String) {
        // 임시: 토스트 메시지로 구현 확인
        Toast.makeText(context, "📌 [$roomCode]의 스크랩 목록 보기!", Toast.LENGTH_SHORT).show()

        // TODO: 실제 스크랩 목록 Dialog 또는 Activity 구현
    }
}