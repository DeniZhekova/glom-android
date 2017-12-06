package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.data.AndroidString
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.base.util.Format
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.*

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    /* live data for the board items */
    private val observableBoard = MutableLiveData<BoardUiModel>()
    private val boardUiModel = BoardUiModel()

    /* interactor for the observable board */
    private lateinit var interactor: BoardInteractor

    /* date time formatter */
    private val format: Format = Format()

    /* default grouping of event items */
    private val groupType: GroupType = GroupType.WEEK

    companion object {

        const val NUM_WEEK_IN_YEAR = 52
    }

    init {
        interactor = BoardInteractor()
        loadBoard()
    }

    //region view actions

    /**
     * Loads board data and items asynchronously
     */
    fun loadBoard() {
        observableBoard.value = boardUiModel.apply {
            status = UiModel.Status.LOADING
        }

        runBlockingIO(interactor::loadBoard) {
            when (it) {
                is AsyncSuccessResult -> {
                    it.result.items.let {
                        observableBoard.value = boardUiModel.apply {
                            status = if (it.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
                            items = it.toUiModel()
                            shouldLoadPlaceInfo = false
                            itemsChangedIndices = null
                        }
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                    observableBoard.value = boardUiModel.apply {
                        status = UiModel.Status.ERROR
                        items = null
                    }
                }
            }
        }
    }

    /**
     * Retrieves place information for board items. We need this call from the View because
     * PlaceProvider can only be instantiated using the Android.view context
     */
    fun loadPlaceInfo(placeProvider: PlaceProvider?) {
        interactor.loadItemPlaceInfo(placeProvider) {
            when (it) {
                is AsyncSuccessResult -> {
                    observableBoard.value = boardUiModel.apply {
                        shouldLoadPlaceInfo = false
                        itemsChangedIndices = ArrayList()
                        val places = it.result
                        for (i in 0 until places.size()) {
                            val indexToNotify = places.keyAt(i)
                            val placeName = places.valueAt(i)

                            itemsChangedIndices?.add(indexToNotify)
                            val item = items?.get(indexToNotify)
                            if (item is EventItemUiModel) {
                                item.location = AndroidString(text = placeName.name.toString())
                            }
                        }
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                }
            }
        }
    }

    //endregion
    //region util functions

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun List<BoardItem>.toUiModel(): List<BoardItemUiModel> {
        if (isEmpty()) return Collections.emptyList<BoardItemUiModel>()

        // get all the event items out of the original list
        val currTime = Calendar.getInstance().apply { time = Date() }
        var items = filter { it is EventItem }
                .sortedBy { (it as EventItem).itemInfo.startTime }
                .groupBy { item ->
                    Calendar.getInstance().let {
                        val startTime = (item as EventItem).itemInfo.startTime
                        if (startTime == null) null else {
                            it.time = Date(startTime)
                            when {
                                currTime[Calendar.YEAR] > it[Calendar.YEAR] -> {
                                    (currTime[Calendar.WEEK_OF_YEAR] + ((NUM_WEEK_IN_YEAR
                                            * (currTime[Calendar.YEAR] - it[Calendar.YEAR])) - it[Calendar.WEEK_OF_YEAR])) * -1
                                }
                                currTime[Calendar.YEAR] < it[Calendar.YEAR] -> {
                                    ((NUM_WEEK_IN_YEAR * (it[Calendar.YEAR] - currTime[Calendar.YEAR]))
                                            + it[Calendar.WEEK_OF_YEAR]) - currTime[Calendar.WEEK_OF_YEAR]
                                }
                                else -> {
                                    if (it[Calendar.WEEK_OF_YEAR] < currTime[Calendar.WEEK_OF_YEAR]) {
                                        (NUM_WEEK_IN_YEAR + it[Calendar.WEEK_OF_YEAR]) - currTime[Calendar.WEEK_OF_YEAR]
                                    }
                                    else it[Calendar.WEEK_OF_YEAR] - currTime[Calendar.WEEK_OF_YEAR]
                                }
                            }
                        }
                    }
                }
                .forEach {
                    val result = ArrayList<BoardItem>().apply {
                        for (event in it.value) {
                            val time = (event as EventItem).itemInfo.startTime
                            val startTime = if (time == null) null else Date(time)
                            Log.d("DEBUG", "${it.key}: $startTime")
                        }
                    }
                }

//        return map {
//            when (it) {
//                is EventItem -> {
//                    EventItemUiModel(null,
//                            it.itemInfo.eventName,
//                            getDateRangeString(it.itemInfo.startTime, it.itemInfo.endTime),
//                            getOrLoadLocationString(it.itemInfo.location))
//                }
//                else -> {
//                    ErrorItemUiModel()
//                }
//            }
//        }
        return Collections.emptyList<BoardItemUiModel>()
    }

    /**
     * Returns a formatted date range
     */
    private fun getDateRangeString(start: Long?, end: Long?): String? {
        start ?: return null

        // if end datetime is not present, only show start time (i.e. 20 Jun, 2017 (10:30 AM))
        if (end == null) return "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)})"

        // if end datetime is present
        // show one date with time range if end datetime is within the same day
        // (i.e. 20 Jun, 2017 (10:30 AM - 3:00 PM), otherwise
        // show 20 Jun, 2017 (10:30 AM) - 21 Jun, 2017 (10:30 AM)
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val endDate = Calendar.getInstance().apply { time = Date(end) }
        return if (startDate[Calendar.YEAR] < endDate[Calendar.YEAR] ||
                startDate[Calendar.DAY_OF_YEAR] < endDate[Calendar.DAY_OF_YEAR]) {
            "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)}) " +
                    "- ${format.date(end, Format.DEFAULT_DATE_FORMAT)} (${format.time(end)})"
        }
        else {
            "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)} - ${format.time(end)})"
        }
    }

    /**
     * Returns location string from EventLocation. If the location has been loaded before,
     * retrieve it from the cache, otherwise asynchronously call the respective API
     * to retrieve location data
     */
    private fun getOrLoadLocationString(location: EventLocation?): AndroidString? {
        location ?: return null
        return AndroidString(R.string.event_card_location_placeholder)
    }

    /**
     * Clean up any resources
     */
    override fun onCleared() {
        //nothing yet
    }

    //endregion
    //region observables

    /**
     * Returns an observable board item live data for the view
     */
    fun getObservableBoard(): LiveData<BoardUiModel> = observableBoard

    //endregion
    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = boardUiModel.items?.isEmpty() != false

    /**
     * Returns the number of board items
     */
    fun getBoardItemCount(): Int = boardUiModel.items?.size ?: 0

    /**
     * Returns the item type based on its position
     */
    fun getBoardItemType(position: Int): Int {
        return when (boardUiModel.items.get(position, null)) {
            is EventItemUiModel -> BoardItemUiModel.TYPE_EVENT
            else -> BoardItemUiModel.TYPE_ERROR
        }
    }

    /**
     * Returns a specific Board UI item model
     */
    fun getBoardItemUiModel(position: Int): BoardItemUiModel? = boardUiModel.items.get(position, null)

    //endregion
}