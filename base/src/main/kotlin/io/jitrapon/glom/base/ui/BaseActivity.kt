package io.jitrapon.glom.base.ui

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import io.jitrapon.glom.base.data.Alert
import io.jitrapon.glom.base.data.Snackbar
import io.jitrapon.glom.base.data.Toast
import io.jitrapon.glom.base.data.UiActionModel
import io.jitrapon.glom.base.util.showAlertDialog
import io.jitrapon.glom.base.util.showSnackbar
import io.jitrapon.glom.base.util.showToast

/**
 * Wrapper around Android's AppCompatActivity. Contains convenience functions
 * relating to fragment transactions, activity transitions, Android's new runtime permission handling,
 * analytics, and more. All activities should extend from this class.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseActivity : AppCompatActivity() {

    /* subclass should overwrite this variable for naming the activity */
    var tag: String = "base"

    /* shared handler object */
    lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handler = Handler()
    }

    /**
     * Common UI Action handlers for all child activities
     */
    val uiActionHandler: ((UiActionModel?) -> Unit) = {
        it?.let {
            when (it) {
                is Toast -> showToastMessage(it.message)
                is Snackbar -> showSnackbarMessage(it.message, it.actionMessage, it.actionCallback)
                is Alert -> showAlertMessage(it.title, it.message, it.positiveOptionText, it.onPositiveOptionClicked,
                        it.negativeOptionText, it.onNegativeOptionClicked, it.isCancelable, it.onCancel)
            }
        }
    }

    /**
     * Shows a simple toast message. Override this function to make it behave differently
     */
    open fun showToastMessage(message: String?) {
        showToast(message)
    }

    /**
     * Shows a snackbar message. Override this function to make it behave differently
     */
    open fun showSnackbarMessage(message: String?, actionMessage: String?, actionCallback: (() -> Unit)? = null) {
        showSnackbar(message, actionMessage, actionCallback)
    }

    /**
     * Shows an alert message. Override this function to make it behave differently
     */
    open fun showAlertMessage(title: String?, message: String?, positiveOptionText: String?,
                              onPositiveOptionClicked: (() -> Unit)?, negativeOptionText: String?,
                              onNegativeOptionClicked: (() -> Unit)?, cancelable: Boolean, onCancel: (() -> Unit)?) {
        showAlertDialog(title, message, positiveOptionText, onPositiveOptionClicked,
                negativeOptionText, onNegativeOptionClicked, cancelable, onCancel)
    }
}