package com.example.andapp1

import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.stfalcon.chatkit.messages.MessageHolders.BaseMessageViewHolder
import com.stfalcon.chatkit.utils.DateFormatter
import java.util.regex.Pattern

class TextMessageViewHolder(itemView: View) :
    BaseMessageViewHolder<ChatMessage>(itemView) {

    private val textView: TextView = itemView.findViewById(com.stfalcon.chatkit.R.id.messageText)
    private val timeView: TextView = itemView.findViewById(com.stfalcon.chatkit.R.id.messageTime)

    override fun onBind(message: ChatMessage) {
        val rawText = message.text
        val spannable = SpannableString(rawText)

        // 🔥 먼저 기본 웹 링크 추가 (기존 기능 보존)
        Linkify.addLinks(spannable, Linkify.WEB_URLS)

        // 🗺️ 지도 URL에 대해서만 커스텀 처리 추가
        processMapUrls(spannable, rawText)

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.linksClickable = true

        // 기존 터치 리스너 유지
        textView.setOnTouchListener { v, event ->
            val textView = v as TextView
            val spannable = textView.text as? Spannable ?: return@setOnTouchListener false

            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
                val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY

                val layout = textView.layout ?: return@setOnTouchListener false
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())

                val links = spannable.getSpans(off, off, ClickableSpan::class.java)
                if (links.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) {
                        links[0].onClick(textView)
                    }
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener false
        }

        timeView.text = DateFormatter.format(message.createdAt, DateFormatter.Template.TIME)
    }

    private fun processMapUrls(spannable: Spannable, text: String) {
        // 지도 URL 패턴
        val mapPatterns = arrayOf(
            "https://m\\.map\\.naver\\.com[^\\s]*",
            "https://map\\.naver\\.com[^\\s]*",
            "https://map\\.kakao\\.com[^\\s]*",
            "https://maps\\.google\\.com[^\\s]*",
            "https://www\\.google\\.com/maps[^\\s]*"
        )

        for (patternStr in mapPatterns) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val mapUrl = text.substring(start, end)

                Log.d("TextMessageViewHolder", "🗺️ 지도 URL 발견: $mapUrl")

                // 기존 URL 링크 제거하고 커스텀 링크로 교체
                val existingSpans = spannable.getSpans(start, end, ClickableSpan::class.java)
                for (span in existingSpans) {
                    spannable.removeSpan(span)
                }

                // 커스텀 지도 링크 적용
                val mapClickSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        Log.d("TextMessageViewHolder", "🗺️ 지도 링크 클릭: $mapUrl")
                        try {
                            val intent = Intent(widget.context, MapActivity::class.java)
                            intent.putExtra("mapUrl", mapUrl)
                            widget.context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("TextMessageViewHolder", "지도 액티비티 실행 실패", e)
                        }
                    }
                }

                spannable.setSpan(
                    mapClickSpan,
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}