package me.proxer.app.chat.pub.room.info

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class ChatRoomInfoActivity : BaseActivity() {
    companion object {
        private const val CHAT_ROOM_ID_EXTRA = "chat_room_id"
        private const val CHAT_ROOM_NAME_EXTRA = "chat_room_name"

        fun navigateTo(context: Activity, chatRoomId: String, chatRoomName: String) {
            context.startActivity<ChatRoomInfoActivity>(
                CHAT_ROOM_ID_EXTRA to chatRoomId,
                CHAT_ROOM_NAME_EXTRA to chatRoomName,
            )
        }
    }

    private val chatRoomId: String
        get() = intent.getSafeStringExtra(CHAT_ROOM_ID_EXTRA)

    private val chatRoomName: String
        get() = intent.getSafeStringExtra(CHAT_ROOM_NAME_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ChatRoomInfoScreen(
                    chatRoomId = chatRoomId,
                    chatRoomName = chatRoomName,
                    onBack = { finish() },
                )
            }
        }
    }
}
