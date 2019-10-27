package me.proxer.app.tv.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.editorActionEvents
import com.jakewharton.rxbinding3.widget.textChanges
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import kotterknife.bindView
import me.proxer.app.R
import me.proxer.app.auth.LoginViewModel
import me.proxer.app.tv.TVMainActivity
import me.proxer.app.util.extension.safeText
import me.proxer.app.util.extension.toast
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * @author Graphicscore (Dominik Louven)
 */
class AuthenticationActivity : AppCompatActivity() {

    private val username: TextInputEditText by bindView(R.id.username)
    private val password: TextInputEditText by bindView(R.id.password)
    private val secret: TextInputEditText by bindView(R.id.secret)
    private val usernameContainer: TextInputLayout by bindView(R.id.usernameContainer)
    private val passwordContainer: TextInputLayout by bindView(R.id.passwordContainer)
    private val secretContainer: ViewGroup by bindView(R.id.secretContainer)
    private val buttonLogin: Button by bindView(R.id.buttonLogin)
    private val progress: ProgressBar by bindView(R.id.progress)


    private val viewModel by viewModel<LoginViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_login)

        if (savedInstanceState == null) {
            username.requestFocus()
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }


        setupViews()
        setupListeners()
        setupViewModel()
    }

    private fun setupViews(){
        //hide MFA
        secretContainer.isVisible = false
    }

    private fun setupListeners(){
        buttonLogin.clicks()
            .autoDisposable(this.scope())
            .subscribe{
                validateAndLogin()
            }

        listOf(password, secret).forEach {
            it.editorActionEvents { event -> event.actionId == EditorInfo.IME_ACTION_GO }
                .filter { event -> event.actionId == EditorInfo.IME_ACTION_GO }
                .autoDisposable(this.scope())
                .subscribe { validateAndLogin() }
        }

        listOf(username to usernameContainer, password to passwordContainer).forEach { (input, container) ->
            input.textChanges()
                .skipInitialValue()
                .autoDisposable(this.scope())
                .subscribe { setError(container, null) }
        }
    }

    private fun validateAndLogin() {
        val username = username.safeText.trim().toString()
        val password = password.safeText.trim().toString()
        val secretKey = secret.safeText.trim().toString()

        if (validateInput(username, password)) {
            viewModel.login(username, password, secretKey)
        }
    }

    private fun validateInput(username: String, password: String) = when {
        username.isBlank() -> {
            setError(usernameContainer, getString(R.string.dialog_login_error_username))

            false
        }
        password.isBlank() -> {
            setError(passwordContainer, getString(R.string.dialog_login_error_password))

            false
        }
        else -> true
    }

    private fun setError(container: TextInputLayout, errorText: String?) {
        container.isErrorEnabled = errorText != null
        container.error = errorText
    }

    private fun setupViewModel() {
        viewModel.success.observe(this, Observer {
            it?.let {
                startActivity(Intent(this, TVMainActivity::class.java))
                finish()
            }
        })

        viewModel.error.observe(this, Observer {
            it?.let {
                viewModel.error.value = null

                toast(it.message)
            }
        })

        viewModel.isLoading.observe(this, Observer {
            listOf(usernameContainer,passwordContainer,secretContainer,buttonLogin).forEach { view ->
                view.isGone = it == true
                if(it == true){
                    view.clearFocus()
                }
            }
            progress.isVisible = it == true
            if(it == true){
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(progress.windowToken, 0)
            }
        })

        viewModel.isTwoFactorAuthenticationEnabled.observe(this, Observer {
            secretContainer.isVisible = it == true
            secret.imeOptions = if (it == true) EditorInfo.IME_ACTION_GO else EditorInfo.IME_ACTION_NEXT
            password.imeOptions = if (it == true) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_GO
        })
    }
}
