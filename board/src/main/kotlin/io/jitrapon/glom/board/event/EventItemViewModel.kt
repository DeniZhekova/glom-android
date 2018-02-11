package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import androidx.text.bold
import androidx.text.buildSpannedString
import com.google.android.gms.location.places.Place
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.toDateString
import io.jitrapon.glom.base.util.toTimeString
import io.jitrapon.glom.board.*
import java.util.*

/**
 * Manages all view states for event board items and detail screen
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BoardItemViewModel() {

    private lateinit var interactor: EventItemInteractor

    private var prevName: String? = null

    private val observableName = MutableLiveData<Pair<CharSequence, Boolean>>() //TODO add append vs replace

    private val observableStartDate = MutableLiveData<String>()

    private val observableEndDate = MutableLiveData<String>()

    /* indicates whether or not the view should display autocomplete */
    private var shouldShowNameAutocomplete: Boolean = true

    init {
        interactor = EventItemInteractor()
    }

    //region event board item

    override fun toUiModel(item: BoardItem, status: UiModel.Status): BoardItemUiModel {
        return (item as EventItem).let {
            EventItemUiModel(
                    it.itemId,
                    it.itemInfo.eventName,
                    getEventDate(it.itemInfo.startTime, it.itemInfo.endTime),
                    getEventLocation(it.itemInfo.location),
                    getEventLatLng(it.itemInfo.location),
                    getEventAttendees(it.itemInfo.attendees),
                    getEventAttendStatus(it.itemInfo.attendees),
                    status = status
            )
        }
    }

    /**
     * Returns a formatted date range
     */
    private fun getEventDate(start: Long?, end: Long?): String? {
        start ?: return null
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val currentDate = Calendar.getInstance()
        var showYear = startDate[Calendar.YEAR] < currentDate[Calendar.YEAR]

        // if end datetime is not present, only show start time
        if (end == null) return Date(start).let {
            "${it.toDateString(showYear)} (${it.toTimeString()})"
        }

        // if end datetime is present
        // show one date with time range if end datetime is within the same day
        // (i.e. 20 Jun, 2017 (10:30 AM - 3:00 PM), otherwise
        // show 20 Jun, 2017 (10:30 AM) - 21 Jun, 2017 (10:30 AM)
        val endDate = Calendar.getInstance().apply { time = Date(end) }
        val startYearLessThanEndYear = startDate[Calendar.YEAR] < endDate[Calendar.YEAR]
        return if (startYearLessThanEndYear || startDate[Calendar.DAY_OF_YEAR] < endDate[Calendar.DAY_OF_YEAR]) {
            showYear = showYear || startYearLessThanEndYear
            StringBuilder().apply {
                append(Date(start).let {
                    "${it.toDateString(showYear)} (${it.toTimeString()})"
                })
                append(" - ")
                append(Date(end).let {
                    "${it.toDateString(showYear)} (${it.toTimeString()})"
                })
            }.toString()
        }
        else {
            Date(start).let {
                "${it.toDateString(showYear)} (${it.toTimeString()} - ${Date(end).toTimeString()})"
            }
        }
    }

    /**
     * Returns location string from EventLocation. If the location has been loaded before,
     * retrieve it from the cache, otherwise asynchronously call the respective API
     * to retrieve location data
     */
    private fun getEventLocation(location: EventLocation?): AndroidString? {
        location ?: return null
        if (location.placeId == null && location.googlePlaceId == null) {
            return AndroidString(resId = R.string.event_card_location_latlng,
                    formatArgs = arrayOf(location.latitude?.toString() ?: "null", location.longitude?.toString() ?: "null"))
        }
        return AndroidString(R.string.event_card_location_placeholder)
    }

    /**
     * Returns a latlng corresponding to the location of the event
     */
    private fun getEventLatLng(location: EventLocation?): LatLng? {
        location ?: return null
        if (location.latitude != null && location.longitude != null) {
            return LatLng(location.latitude, location.longitude)
        }
        return null
    }

    /**
     * Returns the list of user avatars from user ids
     */
    private fun getEventAttendees(userIds: List<String>?): MutableList<String?>? {
        userIds ?: return null
        val users = interactor.getUsers(userIds) ?: return null
        return users.map { it?.avatar }.toMutableList()
    }

    /**
     * Returns a AttendStatus from the list of attending userIds
     */
    private fun getEventAttendStatus(userIds: List<String>): EventItemUiModel.AttendStatus {
        return if (userIds.any { it.equals(interactor.getCurrentUser()?.userId, true) })  EventItemUiModel.AttendStatus.GOING
        else EventItemUiModel.AttendStatus.MAYBE
    }

    /**
     * Change current attend status of the user id on a specified event
     */
    fun setEventAttendStatus(boardViewModel: BoardViewModel, position: Int, newStatus: EventItemUiModel.AttendStatus) {
        boardViewModel.boardUiModel.items?.let { items ->
            val item = items.getOrNull(position)
            if (item is EventItemUiModel) {
                val statusCode: Int
                var animationItem: AnimationItem? = null
                val message: AndroidString
                var level: Int = MessageLevel.INFO
                when (newStatus) {
                    EventItemUiModel.AttendStatus.GOING -> {
                        statusCode = 2
                        animationItem = AnimationItem.JOIN_EVENT
                        message = AndroidString(R.string.event_card_join_success, arrayOf(item.title))
                        level = MessageLevel.SUCCESS
                    }
                    EventItemUiModel.AttendStatus.MAYBE -> {
                        statusCode = 1
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                    EventItemUiModel.AttendStatus.DECLINED -> {
                        statusCode = 0
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                }
                item.apply {
                    attendStatus = newStatus
                }
                boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                    requestPlaceInfoItemIds = null
                    diffResult = null
                    itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to arrayListOf(EventItemUiModel.ATTENDSTATUS)) }
                }
                animationItem?.let {
                    boardViewModel.observableAnimation.value = it
                }
                interactor.markEventAttendStatusForCurrentUser(item.itemId, statusCode) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            item.apply {
                                attendeesAvatars = getEventAttendees(it.result)
                            }
                            boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                                requestPlaceInfoItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(EventItemUiModel.ATTENDSTATUS, EventItemUiModel.ATTENDEES)) }
                            }
                            boardViewModel.observableViewAction.value = Snackbar(message, level = level)
                        }
                        is AsyncErrorResult -> {
                            handleError(it.error)
                        }
                    }
                }
            }
        }
    }

    //endregion
    //region event detail item

    /**
     * Initializes this ViewModel with the event item to display
     */
    fun setItem(item: BoardItem?) {
        item.let {
            if (it == null) {
                AppLogger.w("Cannot set item because item is NULL")
            }
            else {
                if (item is EventItem) {
                    interactor.setItem(item)
                    prevName = item.itemInfo.eventName
                    observableName.value = item.itemInfo.eventName to false
                    observableStartDate.value = getEventDetailDate(item.itemInfo.startTime)
                    observableEndDate.value = getEventDetailDate(item.itemInfo.endTime)
                }
                else {
                    AppLogger.w("Cannot set item because item is not an instance of EventItem")
                }
            }
        }
    }

    /**
     * Returns whether or not autocomplete on the name text should be enabled
     */
    fun shouldShowNameAutocomplete(): Boolean = shouldShowNameAutocomplete

    /**
     * Saves the current state and returns a model object with the state
     */
    fun saveItem(onSuccess: (BoardItem?) -> Unit) {
        interactor.saveItem {
            when (it) {
                is AsyncSuccessResult -> onSuccess(it.result)
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    /**
     * Returns a formatted date in event detail
     */
    private fun getEventDetailDate(dateAsEpochMs: Long?): String? {
        dateAsEpochMs ?: return null
        return StringBuilder().apply {
            val date = Date(dateAsEpochMs)
            val thisDate = Calendar.getInstance().apply { time = date }
            val currentDate = Calendar.getInstance()
            val showYear = thisDate[Calendar.YEAR] != currentDate[Calendar.YEAR]
            append(date.toDateString(showYear)).append("\n")
            append(date.toTimeString())
        }.toString()
    }

    //region autocomplete

    /**
     * Converts the suggestion as a text to be displayed in the drop-down
     */
    fun getSuggestionText(suggestion: Suggestion): String {
        return suggestion.displayText ?: suggestion.selectData.let {
            when (it) {
                is Date -> getEventDetailDate(it.time)!!
                is Place -> it.name.toString()
                else -> it.toString()
            }
        }
    }

    /**
     * Converts the suggestion as a text to be displayed in the autocomplete textview
     */
    fun getDisplayText(suggestion: Suggestion): String {
        return suggestion.selectData.let {
            when (it) {
                is Date -> it.toString()
                is Place -> it.name.toString()
                else -> it.toString()
            }
        }
    }

    /**
     * Synchronously filter suggestions to display based on the specified query
     */
    fun filterSuggestions(text: String): List<Suggestion> {
        return interactor.filterSuggestions(text)
    }

    /**
     * Apply the current suggestion
     */
    fun selectSuggestion(currentText: Editable, suggestion: Suggestion) {
        val selectText = getDisplayText(suggestion)
        interactor.applySuggestion(currentText.toString(), suggestion, selectText)

        val builder = SpannableStringBuilder(currentText.trim())
        when (suggestion.selectData) {
            is String -> {
                if (suggestion.isConjunction) builder.append(" ").append(selectText)
                else builder.apply {
                    clear()
                    append(selectText)
                }
            }
            is Date -> {
                observableStartDate.value = getEventDetailDate(suggestion.selectData.time)
                builder.append(" ").append(buildSpannedString {
                    bold { append(selectText) }
                })
            }
            is Place -> {
                builder.append(" ").append(buildSpannedString {
                    bold { append(selectText) }
                })
            }
        }
        observableName.value = SpannedString(builder) to true
    }

    /**
     * Remove the suggestion
     */
    fun removeSuggestion(suggestion: Suggestion) {
        interactor.removeSuggestion(suggestion)
    }

    //endregion

    //endregion
    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = false

    /**
     * Returns the event name before change
     */
    fun getPreviousName(): String? = prevName

    //endregion
    //region observables

    fun getObservableName(): LiveData<Pair<CharSequence, Boolean>> = observableName

    fun getObservableStartDate(): LiveData<String> = observableStartDate

    fun getObservableEndDate(): LiveData<String> = observableEndDate

    //endregion

    override fun cleanUp() {
        //nothing yet
    }

    //endregion
}