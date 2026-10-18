package com.tmplayer.data

import com.tmplayer.i18n.L
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ChatJoinResult
import dev.g000sha256.tdl.dto.ChatJoinResultDeclined
import dev.g000sha256.tdl.dto.ChatJoinResultGuardBotApprovalRequired
import dev.g000sha256.tdl.dto.ChatJoinResultRequestSent
import dev.g000sha256.tdl.dto.ChatJoinResultSuccess
import dev.g000sha256.tdl.dto.InviteLinkChatTypeChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What opening a post link comes to (CP79). */
sealed interface PostOpen {
    /** The post is a video (or holds one): play it, from [startSec] when the link named a time. */
    data class Play(val item: MediaItem, val chat: ChatSummary?, val startSec: Int) : PostOpen

    /** The post is not a video, or could not be read: its chat opens instead, with [note] said. */
    data class OpenChat(val chat: ChatSummary, val note: String?) : PostOpen

    /** Nothing opens, for [reason]: not found, a private chat not joined, the kids lock. */
    data class Refused(val reason: String) : PostOpen
}

/** An invite link's chat as Telegram describes it before joining (checkChatInviteLink). */
data class InvitePreview(
    val link: TgLink.Invite,
    val title: String,
    val isChannel: Boolean,
    val photoFileId: Int,
    val bigPhotoFileId: Int,
    val miniThumbnail: ByteArray?,
    val memberCount: Int,
    val description: String,
    /** The admins approve each newcomer: the button asks to join rather than joining. */
    val createsJoinRequest: Boolean,
    /** A paid subscription: joining is left to Telegram, where the payment is. */
    val paid: Boolean,
    /** The chat, when the account is in it already (or may look in for a while); 0 otherwise. */
    val chatId: Long,
    val alreadyMember: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is InvitePreview && other.link == link && other.title == title &&
        other.chatId == chatId && other.alreadyMember == alreadyMember && other.memberCount == memberCount
    override fun hashCode(): Int = link.hashCode() * 31 + title.hashCode()
}

/** What a join came to, from a public chat's Join or an invite's confirm. */
sealed interface JoinOutcome {
    data class Joined(val chat: ChatSummary?) : JoinOutcome
    data object RequestSent : JoinOutcome
    data class Refused(val reason: String) : JoinOutcome
}

/**
 * The rules behind opening and joining, kept apart from TDLib so they are tested once: which
 * post opens what, which errors mean what, and that the kids lock stops every one of them.
 */
object LinkRules {

    /** Joining anything is off while the kids lock is on: its chats are the ones chosen for it. */
    fun mayJoin(kidsAllowed: Set<Long>?): Boolean = kidsAllowed == null

    /**
     * A resolved post: [chatId] 0 when Telegram would not say which chat (a private one the
     * account is not in), [video] the post's video if it has one, [chat] its chat as the list
     * draws it. [linkStart] is the link's own `t=`, [telegramStart] what TDLib read off it.
     */
    fun decide(
        kidsAllowed: Set<Long>?,
        chatId: Long,
        isPublic: Boolean,
        found: Boolean,
        video: MediaItem?,
        chat: ChatSummary?,
        linkStart: Int,
        telegramStart: Int,
    ): PostOpen {
        if (chatId == 0L) return PostOpen.Refused(if (isPublic) L.linkNotFound else L.linkPrivateNotMember)
        if (!KidsLock.allows(kidsAllowed, chatId)) return PostOpen.Refused(L.linkKidsLock)
        if (video != null) return PostOpen.Play(video, chat, (if (linkStart > 0) linkStart else telegramStart).coerceAtLeast(0))
        if (chat == null) return PostOpen.Refused(L.linkNotFound)
        return PostOpen.OpenChat(chat, if (found) L.linkNoVideo else L.linkPostGone)
    }

    /** A join's answer, as one of [JoinOutcome]. */
    fun outcome(result: ChatJoinResult): JoinOutcome = when (result) {
        is ChatJoinResultSuccess -> JoinOutcome.Joined(null)
        is ChatJoinResultRequestSent -> JoinOutcome.RequestSent
        is ChatJoinResultDeclined -> JoinOutcome.Refused(L.linkDeclined)
        is ChatJoinResultGuardBotApprovalRequired -> JoinOutcome.Refused(L.linkBotApproval)
        else -> JoinOutcome.Refused(L.linkJoinFailed(Failures.DEFAULT))
    }

    /** A join that came back as an error: some of Telegram's errors are answers, not failures. */
    fun failure(message: String?): JoinOutcome {
        val raw = message.orEmpty()
        return when {
            raw.contains("INVITE_REQUEST_SENT") -> JoinOutcome.RequestSent
            raw.contains("USER_ALREADY_PARTICIPANT") -> JoinOutcome.Joined(null)
            raw.contains("INVITE_HASH_EXPIRED") || raw.contains("INVITE_HASH_INVALID") || raw.contains("INVITE_HASH_EMPTY") ->
                JoinOutcome.Refused(L.linkInviteInvalid)
            raw.contains("CHANNELS_TOO_MUCH") -> JoinOutcome.Refused(L.linkTooMany)
            raw.contains("USERS_TOO_MUCH") -> JoinOutcome.Refused(L.linkChatFull)
            raw.contains("CHANNEL_PRIVATE") || raw.contains("USER_BANNED_IN_CHANNEL") -> JoinOutcome.Refused(L.linkBanned)
            else -> JoinOutcome.Refused(L.linkJoinFailed(Failures.humanise(raw)))
        }
    }

    /** The invite check's error, said: an expired or mistyped invite is the usual one. */
    fun inviteFailure(message: String?): String {
        val raw = message.orEmpty()
        return if (raw.contains("INVITE_HASH") || raw.contains("Wrong invite link") || raw.contains("400")) {
            L.linkInviteInvalid
        } else {
            Failures.humanise(raw)
        }
    }
}

/** Opening and joining through TDLib, for the shared link flow on every device. */
class TelegramLinkActions(private val td: TdlClient) {

    /** Where [link] leads: a video to play, a chat to open, or why neither. */
    suspend fun openPost(link: TgLink.Post): PostOpen = withContext(Dispatchers.IO) {
        val allowed = ChatGate.awaitReady()
        val answer = td.getMessageLinkInfo(link.url)
        val info = when (answer) {
            is TdlResult.Success -> answer.result
            is TdlResult.Failure -> return@withContext PostOpen.Refused(
                if (answer.message.contains("CHANNEL_PRIVATE")) L.linkPrivateNotMember else Failures.humanise(answer.message).let {
                    if (answer.code == 400) L.linkNotFound else it
                },
            )
        }
        val chatId = info.chatId
        if (!KidsLock.allows(allowed, chatId)) return@withContext PostOpen.Refused(L.linkKidsLock)
        val chat = if (chatId != 0L) ChatRepository(td).summary(chatId) else null
        val message = info.message
        val chatProtected = if (chatId != 0L) td.getChat(chatId).valueOrNull?.hasProtectedContent ?: false else false
        val video = message?.let { MediaMapper.fromMessage(it, chatProtected) }
        LinkRules.decide(
            kidsAllowed = allowed,
            chatId = chatId,
            isPublic = info.isPublic,
            found = message != null,
            video = video,
            chat = chat,
            linkStart = link.startSec,
            telegramStart = info.mediaTimestamp,
        )
    }

    /** The invite's chat, or why it cannot be shown (expired, wrong, the kids lock). */
    suspend fun previewInvite(link: TgLink.Invite): Result<InvitePreview> = withContext(Dispatchers.IO) {
        if (!LinkRules.mayJoin(ChatGate.awaitReady())) return@withContext Result.failure(LinkRefusal(L.linkJoinKidsLock))
        when (val answer = td.checkChatInviteLink(link.url)) {
            is TdlResult.Failure -> Result.failure(LinkRefusal(LinkRules.inviteFailure(answer.message)))
            is TdlResult.Success -> {
                val info = answer.result
                Result.success(
                    InvitePreview(
                        link = link,
                        title = info.title,
                        isChannel = info.type is InviteLinkChatTypeChannel,
                        photoFileId = info.photo?.small?.id ?: 0,
                        bigPhotoFileId = info.photo?.big?.id ?: 0,
                        miniThumbnail = info.photo?.minithumbnail?.data,
                        memberCount = info.memberCount,
                        description = info.description.trim(),
                        createsJoinRequest = info.createsJoinRequest,
                        paid = info.subscriptionInfo != null,
                        chatId = info.chatId,
                        alreadyMember = info.chatId != 0L && info.accessibleFor == 0,
                    ),
                )
            }
        }
    }

    /** Joins through an invite, once the viewer has confirmed it. */
    suspend fun joinInvite(link: TgLink.Invite): JoinOutcome = withContext(Dispatchers.IO) {
        if (!LinkRules.mayJoin(ChatGate.awaitReady())) return@withContext JoinOutcome.Refused(L.linkJoinKidsLock)
        when (val answer = td.joinChatByInviteLink(link.url)) {
            is TdlResult.Failure -> LinkRules.failure(answer.message)
            is TdlResult.Success -> withChat(LinkRules.outcome(answer.result), (answer.result as? ChatJoinResultSuccess)?.chatId)
        }
    }

    /** Joins a public group or channel seen through its @username. */
    suspend fun joinPublic(chatId: Long): JoinOutcome = withContext(Dispatchers.IO) {
        if (!LinkRules.mayJoin(ChatGate.awaitReady())) return@withContext JoinOutcome.Refused(L.linkJoinKidsLock)
        when (val answer = td.joinChat(chatId)) {
            is TdlResult.Failure -> LinkRules.failure(answer.message)
            is TdlResult.Success -> withChat(LinkRules.outcome(answer.result), chatId)
        }
    }

    private suspend fun withChat(outcome: JoinOutcome, chatId: Long?): JoinOutcome =
        if (outcome is JoinOutcome.Joined && chatId != null && chatId != 0L) {
            JoinOutcome.Joined(ChatRepository(td).summary(chatId))
        } else {
            outcome
        }
}

/** A refusal already worded for the viewer. */
class LinkRefusal(override val message: String) : Exception(message)
