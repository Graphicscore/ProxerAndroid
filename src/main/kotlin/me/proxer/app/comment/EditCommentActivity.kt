package me.proxer.app.comment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.R
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.extension.toast
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Ruben Gees
 */
class EditCommentActivity : BaseActivity() {
    companion object {
        const val COMMENT_EXTRA = "comment"

        private const val ID_ARGUMENT = "id"
        private const val ENTRY_ID_ARGUMENT = "entry_id"
        private const val NAME_ARGUMENT = "name_id"
    }

    private val id: String?
        get() = intent.getStringExtra(ID_ARGUMENT)

    private val entryId: String?
        get() = intent.getStringExtra(ENTRY_ID_ARGUMENT)

    private val name: String?
        get() = intent.getStringExtra(NAME_ARGUMENT)

    private val viewModel by viewModel<EditCommentViewModel> {
        parametersOf(id, entryId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                EditCommentScreen(
                    id = id,
                    entryId = entryId,
                    name = name,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        entryId?.also { safeEntryId ->
            val content = viewModel.data.value?.content ?: ""

            if (viewModel.isUpdate.value == false && content.isNotBlank()) {
                storageHelper.putCommentDraft(safeEntryId, content)

                toast(R.string.fragment_edit_comment_draft_saved)
            } else {
                storageHelper.deleteCommentDraft(safeEntryId)
            }
        }

        super.onDestroy()
    }

    class Contract : ActivityResultContract<Contract.Input, LocalComment?>() {
        override fun createIntent(context: Context, input: Input) = context.intentFor<EditCommentActivity>(
            ID_ARGUMENT to input.id,
            ENTRY_ID_ARGUMENT to input.entryId,
            NAME_ARGUMENT to input.name,
        )

        override fun parseResult(resultCode: Int, intent: Intent?): LocalComment? {
            if (resultCode != Activity.RESULT_OK) {
                return null
            }

            return intent?.let { IntentCompat.getParcelableExtra(it, COMMENT_EXTRA, LocalComment::class.java) }
        }

        data class Input(val id: String? = null, val entryId: String? = null, val name: String? = null)
    }
}
