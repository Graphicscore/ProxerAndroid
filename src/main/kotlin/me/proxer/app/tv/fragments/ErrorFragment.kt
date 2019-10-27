package me.proxer.app.tv.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.jakewharton.rxbinding3.view.clicks
import com.rubengees.rxbus.RxBus
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import me.proxer.app.R
import me.proxer.app.base.BaseViewModel
import me.proxer.app.base.CaptchaSolvedEvent
import me.proxer.app.tv.TVMainActivity
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.safeInject
import me.proxer.library.enums.Device
import me.proxer.library.util.ProxerUrls

/**
 * @author Graphicscore (Dominik Louven)
 */
class ErrorFragment<T> : Fragment(){

    companion object {
        fun <T> newInstance(hostingActivity: TVMainActivity, viewModel: BaseViewModel<T>, action: ErrorUtils.ErrorAction): ErrorFragment<T>{
            val result = ErrorFragment<T>()
            result.viewModel = viewModel
            result.action = action
            result.hostingActivity = hostingActivity
            return result
        }
    }

    protected val bus by safeInject<RxBus>()

    private lateinit var viewModel: BaseViewModel<T>
    private lateinit var action: ErrorUtils.ErrorAction
    private lateinit var hostingActivity: TVMainActivity
    private var isSolvingCaptcha = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.tv_fragment_error, container, false)
        val errorText = view.findViewById<TextView>(R.id.errorMessage)
        val errorButton = view.findViewById<Button>(R.id.errorButton)
        errorText.text = getString(action.message)
        errorButton.text = when(action.buttonMessage){
            ErrorUtils.ErrorAction.ACTION_MESSAGE_DEFAULT -> getString(R.string.error_action_retry)
            else -> getString(action.buttonMessage)
        }
        errorButton.clicks()
            .autoDisposable(viewLifecycleOwner.scope(Lifecycle.Event.ON_DESTROY))
            .subscribe {
                when (action.message == R.string.error_captcha) {
                    true -> {
                        isSolvingCaptcha = true
                        hostingActivity.showPage(ProxerUrls.captchaWeb(Device.MOBILE))
                    }
                    false -> action.toTVClickListener(hostingActivity)?.onClick(errorButton) ?: viewModel.load()
                }
            }
        return view
    }

    override fun onResume() {
        super.onResume()
        if (isSolvingCaptcha) {
            isSolvingCaptcha = false

            bus.post(CaptchaSolvedEvent())
            activity?.supportFragmentManager?.popBackStack()
        }
    }
}
