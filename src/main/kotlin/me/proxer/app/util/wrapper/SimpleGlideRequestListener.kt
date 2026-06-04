package me.proxer.app.util.wrapper

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/**
 * @author Ruben Gees
 */
abstract class SimpleGlideRequestListener<R> : RequestListener<R> {
    override fun onLoadFailed(
        error: GlideException?,
        model: Any?,
        target: Target<R>,
        isFirstResource: Boolean,
    ): Boolean = false

    override fun onResourceReady(
        resource: R & Any,
        model: Any,
        target: Target<R & Any>,
        dataSource: DataSource,
        isFirstResource: Boolean,
    ): Boolean = false
}
