package io.jitrapon.glom.base.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.support.annotation.ColorRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity

/**
 * Various extension functions for Fragment and wrapper around ContextExt to handle nullable
 * Context object
 *
 * @author Jitrapon Tiachunpun
 */

fun Fragment.finish() {
    activity?.finish()
}

fun Fragment.showToast(message: String?) {
    activity?.showToast(message)
}

fun Fragment.showSnackbar(message: String?, resId: Int?, actionMessage: String? = null, actionCallback: (() -> Unit)? = null) {
    activity?.let {
        view?.showSnackbar(message, resId, actionMessage, actionCallback)
    }
}

fun Fragment.showAlertDialog(title: String?, message: String?, positiveOptionText: String? = null,
                             onPositiveOptionClicked: (() -> Unit)? = null, negativeOptionText: String? = null,
                             onNegativeOptionClicked: (() -> Unit)? = null, isCancelable: Boolean = true,
                             onCancel: (() -> Unit)? = null) {
    activity?.showAlertDialog(title, message, positiveOptionText, onPositiveOptionClicked, negativeOptionText, onNegativeOptionClicked,
            isCancelable, onCancel)
}

/**
 * Convenient function for retrieving the ViewModel based on its class name
 */
fun <T : ViewModel> Fragment.obtainViewModel(viewModelClass: Class<T>, activity: FragmentActivity? = null): T {
    return if (activity != null) ViewModelProviders.of(activity, ViewModelProvider.NewInstanceFactory()).get(viewModelClass)
    else ViewModelProviders.of(this, ViewModelProvider.NewInstanceFactory()).get(viewModelClass)
}

fun Fragment.color(@ColorRes colorRes: Int) = context?.color(colorRes)