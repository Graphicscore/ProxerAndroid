package me.proxer.app

import cat.ereza.customactivityoncrash.config.CaocConfig

/**
 * @author Ruben Gees
 */
object FlavorInitializer {

    fun initialize(application: MainApplication) {
        CaocConfig.Builder.create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_CRASH)
            .apply()
    }
}
