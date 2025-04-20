import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
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

        timeView.text = DateFormatter.format(message.createdAt, DateFormatter.Template.TIME)
    }
    }