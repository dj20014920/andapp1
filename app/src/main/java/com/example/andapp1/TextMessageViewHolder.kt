import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.example.andapp1.ChatMessage
import com.stfalcon.chatkit.messages.MessageHolders.BaseMessageViewHolder
import com.stfalcon.chatkit.utils.DateFormatter
class TextMessageViewHolder(itemView: View) :
    BaseMessageViewHolder<ChatMessage>(itemView) {

    private val textView: TextView = itemView.findViewById(com.stfalcon.chatkit.R.id.messageText)
    private val timeView: TextView = itemView.findViewById(com.stfalcon.chatkit.R.id.messageTime)

    override fun onBind(message: ChatMessage) {
        val rawText = message.text
        val spannable = SpannableString(rawText)

        Linkify.addLinks(spannable, Linkify.WEB_URLS)
        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.linksClickable = true
        textView.setOnTouchListener { v, event ->
            val textView = v as TextView
            val spannable = textView.text as? Spannable ?: return@setOnTouchListener false

            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
                val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY

                val layout = textView.layout
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())

                val link = spannable.getSpans(off, off, android.text.style.ClickableSpan::class.java)
                if (link.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) {
                        link[0].onClick(textView)
                    }
                    return@setOnTouchListener true // ✅ 클릭 처리되었으면 RecyclerView로 넘기지 않음
                }
            }
            return@setOnTouchListener false
        }
        timeView.text = DateFormatter.format(message.createdAt, DateFormatter.Template.TIME)
    }
    }