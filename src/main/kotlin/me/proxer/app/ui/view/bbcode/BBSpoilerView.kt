package me.proxer.app.ui.view.bbcode

import android.content.Context
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import kotterknife.bindView
import me.proxer.app.R

/**
 * @author Ruben Gees
 */
internal class BBSpoilerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var spoilerTitle: String? = null
        set(value) {
            field = value

            handleExpansion()
        }

    var spoilerTextColor: Int
        get() = toggleText.currentTextColor
        set(value) {
            toggleText.setTextColor(value)

            updateToggleButtonIcon()
        }

    private var isExpanded = false
        set(value) {
            field = value

            handleExpansion()

            findHost()?.let { host ->
                if (value) {
                    host.maxHeight = Int.MAX_VALUE
                }

                host.heightChangedListener?.invoke()
            }
        }

    private val toggle by bindView<LinearLayout>(R.id.toggle)
    private val toggleText by bindView<TextView>(R.id.toggleText)
    private val toggleButton by bindView<ImageView>(R.id.toggleButton)

    private val decoration by bindView<LinearLayout>(R.id.decoration)
    private val container by bindView<LinearLayout>(R.id.container)

    init {
        layoutParams = ViewGroup.MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT)
        orientation = VERTICAL

        LayoutInflater.from(context).inflate(R.layout.view_bb_spoiler, this, true)

        toggle.setOnClickListener { isExpanded = !isExpanded }

        toggleText.setTextColor(spoilerTextColor)
        toggleText.setTag(R.id.ignore_tag, Unit)

        updateToggleButtonIcon()
        handleExpansion()
    }

    override fun addView(child: View?) {
        container.addView(child)
    }

    override fun removeAllViews() {
        container.removeAllViews()
    }

    private fun updateToggleButtonIcon() {
        toggleButton.setImageDrawable(IconicsDrawable(context, CommunityMaterial.Icon.cmd_chevron_down)
            .sizeDp(32)
            .paddingDp(8)
            .color(spoilerTextColor))
    }

    private fun handleExpansion() {
        decoration.visibility = if (isExpanded) View.VISIBLE else View.GONE

        toggleText.text = when {
            spoilerTitle != null -> spoilerTitle
            isExpanded -> context.getString(R.string.view_bbcode_hide_spoiler)
            else -> context.getString(R.string.view_bbcode_show_spoiler)
        }

        when (isExpanded) {
            true -> ViewCompat.animate(toggleButton).rotation(180f)
            false -> ViewCompat.animate(toggleButton).rotation(0f)
        }

        toggleText.requestLayout()
    }

    private fun findHost(): BBCodeView? {
        var current = parent

        while (current !is BBCodeView && current is ViewGroup) {
            current = current.parent
        }

        return current as? BBCodeView
    }
}
