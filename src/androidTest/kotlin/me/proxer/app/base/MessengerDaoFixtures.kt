package me.proxer.app.base

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import me.proxer.app.chat.prv.ConferenceWithMessage
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.library.enums.Device
import me.proxer.library.enums.MessageAction
import org.threeten.bp.Instant

fun localConference(
    id: Long,
    topic: String,
    unreadMessageAmount: Int = 0,
    localIsRead: Boolean = true,
) = LocalConference(
    id = id,
    topic = topic,
    customTopic = "",
    participantAmount = 2,
    image = "",
    imageType = "",
    isGroup = false,
    localIsRead = localIsRead,
    isRead = true,
    date = Instant.ofEpochMilli(0L),
    unreadMessageAmount = unreadMessageAmount,
    lastReadMessageId = "0",
    isFullyLoaded = true,
)

fun conferenceWithMessage(conference: LocalConference) = ConferenceWithMessage(conference, null)

fun localMessage(
    id: Long,
    conferenceId: Long,
    username: String,
    userId: String = "u$id",
    message: String = "Message body $id",
) = LocalMessage(
    id = id,
    conferenceId = conferenceId,
    userId = userId,
    username = username,
    message = message,
    action = MessageAction.NONE,
    date = Instant.ofEpochMilli(0L),
    device = Device.MOBILE,
)

/** getConferencesLiveData returns LiveData<List<ConferenceWithMessage>>; MutableLiveData satisfies it. */
fun stubConferences(dao: MessengerDao, conferences: List<ConferenceWithMessage>) {
    every { dao.getConferencesLiveData(any()) } returns MutableLiveData(conferences)
}

/** getConferenceLiveData(id) returns LiveData<LocalConference?> -- the MessengerScreen header. */
fun stubConference(dao: MessengerDao, conferenceId: Long, conference: LocalConference) {
    every { dao.getConferenceLiveData(conferenceId) } returns MutableLiveData(conference)
}

/**
 * getMessagesLiveDataForConference is an OPEN concrete method returning MediatorLiveData; a relaxed mock
 * replaces its body, so it must be stubbed directly (its abstract helpers are inert). The initial value is
 * seeded via the MediatorLiveData(value) constructor (not `.apply { value = ... }`): tests register this stub
 * from @Before, which runs on the instrumentation thread, and MutableLiveData.setValue asserts the main thread,
 * whereas the value constructor sets it directly. The observer receives it once the LiveData becomes active.
 */
fun stubMessages(dao: MessengerDao, conferenceId: Long, messages: List<LocalMessage>) {
    every { dao.getMessagesLiveDataForConference(conferenceId) } returns
        MediatorLiveData(messages)
}
