package com.example.todolist

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

data class ChatMessage(
    val sender: String,
    val text: String
)

@Suppress("DEPRECATION")
class ChatbotFragment : Fragment() {


    private val chatHistory = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ArrayAdapter<ChatMessage>
    private var generativeModel: GenerativeModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chatbot, container, false)


        val apiKey = try {
            val ai = requireContext().packageManager.getApplicationInfo(
                requireContext().packageName,
                PackageManager.GET_META_DATA
            )
            ai.metaData["keyValue"]?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }

        // Initialize Gemini Model
        if (apiKey.isNotEmpty()) {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
        } else {
            Toast.makeText(context, "API Key missing in Manifest", Toast.LENGTH_SHORT).show()
        }

        val listView = view.findViewById<ListView>(R.id.chatListView)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseChat)
        val btnSend = view.findViewById<Button>(R.id.btnSend)
        val chatInput = view.findViewById<EditText>(R.id.chatInput)


        btnClose.setOnClickListener {
            parentFragmentManager.beginTransaction().remove(this).commit()
        }


        chatAdapter = object : ArrayAdapter<ChatMessage>(requireContext(), R.layout.item_chat_message, chatHistory) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false)
                val item = getItem(position)

                val containerLayout = v.findViewById<LinearLayout>(R.id.chatBubbleContainer)
                val bubble = v.findViewById<LinearLayout>(R.id.messageLayout)
                val msgText = v.findViewById<TextView>(R.id.messageText)
                val senderText = v.findViewById<TextView>(R.id.messageSender)

                msgText.text = item?.text
                senderText.text = item?.sender

                val params = bubble.layoutParams as LinearLayout.LayoutParams

                if (item?.sender == "You") {

                    containerLayout.gravity = Gravity.END
                    bubble.backgroundTintList = ColorStateList.valueOf("#DCF8C6".toColorInt())
                    params.marginStart = 100
                    params.marginEnd = 0
                } else {

                    containerLayout.gravity = Gravity.START
                    bubble.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    params.marginEnd = 100
                    params.marginStart = 0
                }
                bubble.layoutParams = params
                return v
            }
        }

        listView.adapter = chatAdapter

        // Send Button logic
        btnSend.setOnClickListener {
            val text = chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessage("You", text)
                chatInput.setText("")
                getAIResponse(text)
            }
        }

        return view
    }

    private fun addMessage(sender: String, text: String) {
        chatHistory.add(ChatMessage(sender, text))
        chatAdapter.notifyDataSetChanged()
    }

    private fun getAIResponse(userText: String) {
        if (generativeModel == null) return

        addMessage("Bot", "Typing...")

        // Use viewLifecycleOwner to avoid memory leaks
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Generate content using the Gemini library
                val response = generativeModel?.generateContent(content { text(userText) })

                // Remove the "Typing..." indicator
                if (chatHistory.isNotEmpty()) {
                    chatHistory.removeAt(chatHistory.size - 1)
                }

                addMessage("Bot", response?.text ?: "I couldn't process that.")
            } catch (e: Exception) {
                if (chatHistory.isNotEmpty()) {
                    chatHistory.removeAt(chatHistory.size - 1)
                }
                // Log actual error to Logcat for you to see
                android.util.Log.e("AI_ERROR", "Reason: ${e.message}")
                addMessage("Bot", "Error: Check connection or API key.")
            }
        }
    }
}
