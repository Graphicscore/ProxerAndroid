package me.proxer.app.info.translatorgroup

import android.app.Application
import me.proxer.app.MainApplication.Companion.api
import me.proxer.app.base.BaseContentViewModel
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.info.TranslatorGroup

/**
 * @author Ruben Gees
 */
class TranslatorGroupInfoViewModel(application: Application) : BaseContentViewModel<TranslatorGroup>(application) {

    override val endpoint: Endpoint<TranslatorGroup>
        get() = api.info().translatorGroup(translatorGroupId)

    lateinit var translatorGroupId: String
}