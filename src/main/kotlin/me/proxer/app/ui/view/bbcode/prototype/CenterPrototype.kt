package me.proxer.app.ui.view.bbcode.prototype

import android.content.Context
import android.text.Layout.Alignment.ALIGN_CENTER
import android.text.Spannable.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.AlignmentSpan
import android.view.Gravity.CENTER
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.applyToViews
import me.proxer.app.ui.view.bbcode.prototype.BBPrototype.Companion.REGEX_OPTIONS
import me.proxer.app.ui.view.bbcode.toSpannableStringBuilder

/**
 * @author Ruben Gees
 */
object CenterPrototype : BBPrototype {

    override val startRegex = Regex(" *center( .*?)?", REGEX_OPTIONS)
    override val endRegex = Regex("/ *center *", REGEX_OPTIONS)

    @Suppress("OptionalWhenBraces")
    override fun makeViews(context: Context, children: List<BBTree>, args: Map<String, Any?>): List<View> {
        val childViews = children.flatMap { it.makeViews(context) }

        return applyToViews(childViews) { view: View ->
            when (view) {
                is TextView -> {
                    view.text = view.text.toSpannableStringBuilder().apply {
                        setSpan(AlignmentSpan.Standard(ALIGN_CENTER), 0, view.length(), SPAN_INCLUSIVE_EXCLUSIVE)
                    }
                }
                is LinearLayout -> view.gravity = CENTER
                else -> (view.layoutParams as? LayoutParams)?.gravity = CENTER
            }
        }
    }
}