package me.proxer.app.chat.pub.message

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.intentFor

/**
 * @author Ruben Gees
 */
class ChatActivity : BaseActivity() {
    companion object {
        private const val CHAT_ROOM_ID_EXTRA = "chat_room_id"
        private const val CHAT_ROOM_NAME_EXTRA = "chat_room_name"
        private const val CHAT_ROOM_IS_READ_ONLY_EXTRA = "chat_room_is_read_only"

        fun navigateTo(context: Activity, chatRoomId: String, chatRoomName: String, chatRoomIsReadOnly: Boolean) {
            context.startActivity(getIntent(context, chatRoomId, chatRoomName, chatRoomIsReadOnly))
        }

        fun getIntent(
            context: Context,
            chatRoomId: String,
            chatRoomName: String,
            chatRoomIsReadOnly: Boolean = false,
        ): Intent =
            context.intentFor<ChatActivity>(
                CHAT_ROOM_ID_EXTRA to chatRoomId,
                CHAT_ROOM_NAME_EXTRA to chatRoomName,
                CHAT_ROOM_IS_READ_ONLY_EXTRA to chatRoomIsReadOnly,
            )
    }

    private val chatRoomId: String
        get() = intent.getSafeStringExtra(CHAT_ROOM_ID_EXTRA)

    private val chatRoomName: String
        get() = intent.getSafeStringExtra(CHAT_ROOM_NAME_EXTRA)

    private val chatRoomIsReadOnly: Boolean
        get() = intent.getBooleanExtra(CHAT_ROOM_IS_READ_ONLY_EXTRA, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ChatScreen(
                    chatRoomId = chatRoomId,
                    chatRoomName = chatRoomName,
                    chatRoomIsReadOnly = chatRoomIsReadOnly,
                    onBack = { finish() },
                )
            }
        }
    }
}
