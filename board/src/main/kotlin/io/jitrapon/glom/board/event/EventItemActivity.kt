package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemActivity
import io.jitrapon.glom.board.BoardItemViewModelStore
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Shows dialog-like UI for viewing and/or editing an event in a board.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    private lateinit var viewModel: EventItemViewModel

    /* animation delay time in ms before content of this view appears */
    private val SHOW_ANIM_DELAY = 700L

    /* auto-suggest array adapter */
    private val autoCompleteAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter<String>(this, R.layout.auto_suggest_item).apply {
            setNotifyOnChange(false)
        }
    }

    //region lifecycle

    override fun getLayout(): Int = R.layout.event_item_activity

    override fun onCreateViewModel() {
        viewModel = BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java) as EventItemViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "board_item_event"

        // setup all views
        viewModel.let {
            if (it.shouldShowNameAutocomplete()) {
                it.initializeAutoCompleter(GooglePlaceProvider(lifecycle, activity = this))
                addAutocompleteCallbacks(event_item_title)
            }
            it.setItem(getBoardItemFromIntent())
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservableName().observe(this, Observer {
            it?.let {
                event_item_title.setText(it)
            }
        })
    }

    /*
     * TextWatcher for smart auto-complete suggestions when the user
     * begins to type event name
     */
    private fun addAutocompleteCallbacks(textView: AutoCompleteTextView) {
        textView.apply {
            // add text watcher to forward typing events to ViewModel
            addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    viewModel.onNameChanged(s.toString())
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })

            // start auto-suggesting from first character
            threshold = 1

            // update the auto-complete list on change callback
            setAdapter(autoCompleteAdapter)
            viewModel.getObservableAutoSuggestions().observe(this@EventItemActivity, Observer {
                it?.let {
                    autoCompleteAdapter.apply {
                        clear()
                        addAll(it)
                        notifyDataSetChanged()
                    }
                }
            })
        }
    }

    //endregion
    //region other view callbacks

    override fun onSaveItem(): BoardItem? {
        return viewModel.saveAndGetItem(event_item_title.text.toString())
    }

    /**
     * When this event item is about to expand, we want to show only the title as part
     * of the transition animation
     */
    override fun onBeginTransitionAnimationStart() {
        event_item_title_til.hint = " "         // hide the hint above the text
        event_item_title.clearFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_right_icon.hide()
        event_item_end_time.hide()
        event_card_location_icon.hide()
        event_card_location.hide()
    }

    override fun onBeginTransitionAnimationEnd() {
        event_item_clock_icon.show(SHOW_ANIM_DELAY)
        event_item_start_time.show(SHOW_ANIM_DELAY)
        event_item_right_icon.show(SHOW_ANIM_DELAY)
        event_item_end_time.show(SHOW_ANIM_DELAY)
        event_card_location_icon.show(SHOW_ANIM_DELAY)
        event_card_location.show(SHOW_ANIM_DELAY)
    }

    override fun onFinishTransitionAnimationStart() {
        event_item_title.setText(viewModel.getPreviousName())
        event_item_root_layout.requestFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_right_icon.hide()
        event_item_end_time.hide()
        event_card_location_icon.hide()
        event_card_location.hide()
    }

    override fun onFinishTransitionAnimationEnd() {
        //nothing yet
    }

    //endregion
}
