package me.proxer.app.util

import me.proxer.app.R
import me.proxer.app.comment.CommentInvalidProgressException
import me.proxer.app.comment.CommentTooLongException
import me.proxer.app.exception.AgeConfirmationRequiredException
import me.proxer.app.exception.NotConnectedException
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.exception.PartialException
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.manga.MangaLinkException
import me.proxer.app.manga.MangaNotAvailableException
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE
import me.proxer.library.ProxerException
import me.proxer.library.ProxerException.ErrorType
import me.proxer.library.ProxerException.ServerErrorType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLPeerUnverifiedException

class ErrorUtilsTest {

    // ── getMessage ──────────────────────────────────────────────────────────

    @Test fun `IO errorType maps to error_io`() {
        val ex = ProxerException(ErrorType.IO)
        assertEquals(R.string.error_io, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `TIMEOUT errorType maps to error_timeout`() {
        val ex = ProxerException(ErrorType.TIMEOUT)
        assertEquals(R.string.error_timeout, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `PARSING errorType maps to error_parsing`() {
        val ex = ProxerException(ErrorType.PARSING)
        assertEquals(R.string.error_parsing, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `UNKNOWN errorType maps to error_unknown`() {
        val ex = ProxerException(ErrorType.UNKNOWN)
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `CANCELLED errorType maps to error_unknown`() {
        val ex = ProxerException(ErrorType.CANCELLED)
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER IP_BLOCKED maps to error_captcha`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.IP_BLOCKED)
        assertEquals(R.string.error_captcha, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER RATE_LIMIT maps to error_rate_limit`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.RATE_LIMIT)
        assertEquals(R.string.error_rate_limit, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER INVALID_TOKEN maps to error_invalid_token`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.INVALID_TOKEN)
        assertEquals(R.string.error_invalid_token, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER LOGIN_INVALID_CREDENTIALS maps to error_login_credentials`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.LOGIN_INVALID_CREDENTIALS)
        assertEquals(R.string.error_login_credentials, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER USER_INSUFFICIENT_PERMISSIONS logged in maps to logged_in variant`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.USER_INSUFFICIENT_PERMISSIONS)
        assertEquals(R.string.error_insufficient_permissions_logged_in, ErrorUtils.getMessage(ex, isLoggedIn = true))
    }

    @Test fun `SERVER USER_INSUFFICIENT_PERMISSIONS logged out maps to base variant`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.USER_INSUFFICIENT_PERMISSIONS)
        assertEquals(R.string.error_insufficient_permissions, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SERVER USER_2FA_SECRET_REQUIRED maps to error_login_two_factor_authentication`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.USER_2FA_SECRET_REQUIRED)
        assertEquals(R.string.error_login_two_factor_authentication, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `SocketTimeoutException maps to error_timeout`() {
        assertEquals(R.string.error_timeout, ErrorUtils.getMessage(SocketTimeoutException(), isLoggedIn = false))
    }

    @Test fun `SSLPeerUnverifiedException maps to error_ssl`() {
        assertEquals(R.string.error_ssl, ErrorUtils.getMessage(SSLPeerUnverifiedException("x"), isLoggedIn = false))
    }

    @Test fun `NotConnectedException maps to error_no_network`() {
        assertEquals(R.string.error_no_network, ErrorUtils.getMessage(NotConnectedException(), isLoggedIn = false))
    }

    @Test fun `IOException maps to error_io`() {
        assertEquals(R.string.error_io, ErrorUtils.getMessage(IOException("test"), isLoggedIn = false))
    }

    @Test fun `NotLoggedInException maps to error_login_required`() {
        assertEquals(R.string.error_login_required, ErrorUtils.getMessage(NotLoggedInException(), isLoggedIn = false))
    }

    @Test fun `AgeConfirmationRequiredException maps to error_age_confirmation_needed`() {
        assertEquals(
            R.string.error_age_confirmation_needed,
            ErrorUtils.getMessage(AgeConfirmationRequiredException(), isLoggedIn = false),
        )
    }

    @Test fun `StreamResolutionException maps to error_stream_resolution`() {
        assertEquals(
            R.string.error_stream_resolution,
            ErrorUtils.getMessage(StreamResolutionException(), isLoggedIn = false),
        )
    }

    @Test fun `MangaNotAvailableException maps to error_manga_not_available`() {
        assertEquals(
            R.string.error_manga_not_available,
            ErrorUtils.getMessage(MangaNotAvailableException(), isLoggedIn = false),
        )
    }

    @Test fun `CommentTooLongException maps to error_comment_too_long`() {
        assertEquals(
            R.string.error_comment_too_long,
            ErrorUtils.getMessage(CommentTooLongException(), isLoggedIn = false),
        )
    }

    @Test fun `CommentInvalidProgressException maps to error_comment_invalid_progress`() {
        assertEquals(
            R.string.error_comment_invalid_progress,
            ErrorUtils.getMessage(CommentInvalidProgressException(), isLoggedIn = false),
        )
    }

    @Test fun `MangaLinkException maps to error_manga_link`() {
        val ex = MangaLinkException("Chapter 1", "https://example.com/manga".toHttpUrl())
        assertEquals(R.string.error_manga_link, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    @Test fun `unknown exception maps to error_unknown`() {
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(RuntimeException("unexpected"), isLoggedIn = false))
    }

    @Test fun `PartialException unwraps to inner error message`() {
        val inner = NotLoggedInException()
        val ex = PartialException(inner, "some partial data")
        assertEquals(R.string.error_login_required, ErrorUtils.getMessage(ex, isLoggedIn = false))
    }

    // ── handle ──────────────────────────────────────────────────────────────

    @Test fun `handle NotLoggedInException sets LOGIN buttonAction`() {
        val action = ErrorUtils.handle(NotLoggedInException(), isLoggedIn = false)
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle NotConnectedException sets NETWORK_SETTINGS buttonAction`() {
        val action = ErrorUtils.handle(NotConnectedException(), isLoggedIn = false)
        assertEquals(ButtonAction.NETWORK_SETTINGS, action.buttonAction)
    }

    @Test fun `handle AgeConfirmationRequiredException sets AGE_CONFIRMATION buttonAction`() {
        val action = ErrorUtils.handle(AgeConfirmationRequiredException(), isLoggedIn = false)
        assertEquals(ButtonAction.AGE_CONFIRMATION, action.buttonAction)
    }

    @Test fun `handle IP_BLOCKED sets CAPTCHA buttonAction`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.IP_BLOCKED),
            isLoggedIn = false,
        )
        assertEquals(ButtonAction.CAPTCHA, action.buttonAction)
    }

    @Test fun `handle INVALID_TOKEN sets LOGIN buttonAction`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.INVALID_TOKEN),
            isLoggedIn = false,
        )
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle MEDIA_REMOVED_DUE_TO_COPYRIGHT hides button`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.MEDIA_REMOVED_DUE_TO_COPYRIGHT),
            isLoggedIn = false,
        )
        assertNull(action.buttonAction)
        assertEquals(ACTION_MESSAGE_HIDE, action.buttonMessage)
    }

    @Test fun `handle generic server error has default button`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.INTERNAL),
            isLoggedIn = false,
        )
        assertNull(action.buttonAction)
        assertEquals(ACTION_MESSAGE_DEFAULT, action.buttonMessage)
    }

    @Test fun `handle PartialException passes partialData through`() {
        val inner = NotLoggedInException()
        val ex = PartialException(inner, "entry_42")
        val action = ErrorUtils.handle(ex, isLoggedIn = false)
        assertEquals("entry_42", action.data[ErrorUtils.ENTRY_DATA_KEY])
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle MangaLinkException sets OPEN_LINK and passes link data`() {
        val url = "https://example.com/manga".toHttpUrl()
        val ex = MangaLinkException("Chapter 1", url)
        val action = ErrorUtils.handle(ex, isLoggedIn = false)
        assertEquals(ButtonAction.OPEN_LINK, action.buttonAction)
        assertEquals(url, action.data[ErrorUtils.LINK_DATA_KEY])
        assertEquals("Chapter 1", action.data[ErrorUtils.CHAPTER_TITLE_DATA_KEY])
    }
}
