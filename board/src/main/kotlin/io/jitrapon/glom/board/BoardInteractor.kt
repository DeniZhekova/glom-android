package io.jitrapon.glom.board

import android.os.Parcel
import android.util.SparseArray
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Interactor for dealing with Board logic
 *
 * @author Jitrapon Tiachunpun
 */
class BoardInteractor {

    /*
     * Repository that requests from network to retrieve data
     */
    private lateinit var repository: BoardRepository

    /*
     * Cached board state. Will be updated whenever loadBoard() function
     * is called
     */
    private var board: Board? = null

    /*
     * The number of items that was loaded
     */
    private var itemsLoaded: Int = 0

    init {
        repository = BoardRepository()
    }

    /**
     * Force reload of the board state
     */
    fun loadBoard(onComplete: (AsyncResult<Board>) -> Unit) {
        repository.load()
                .delay(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    board = it
                    itemsLoaded = board?.items?.size ?: 0
                    board?.let { onComplete(AsyncSuccessResult(it)) }
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    /**
     * Loads place info for board items that have placeId associated
     * On completed successfully, returns a sparse array containing the keys as the indices of board items required update,
     * and the value to be the Place Info
     */
    fun loadItemPlaceInfo(placeProvider: PlaceProvider?, onComplete: (AsyncResult<SparseArray<Place>>) -> Unit) {
        board?.let {
            val itemIndices = ArrayList<Int>()      // index array to keep track of items that contain location info
            val placeIds = it.items
                    .takeLast(itemsLoaded)          // only load place info for items that are loaded in the last page
                    .filterIndexed { index, item ->
                        (item.itemInfo is EventInfo && (item.itemInfo as? EventInfo)?.location?.googlePlaceId != null).let {
                            if (it) itemIndices.add(index)
                            it
                        }
                    }
                    .map { (it.itemInfo as EventInfo).location?.googlePlaceId!! }.toTypedArray()
            placeProvider?.retrievePlaces(placeIds)
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribeOn(Schedulers.io())
                    ?.subscribe({
                        if (itemIndices.size == it.size) {
                            onComplete(AsyncSuccessResult(SparseArray<Place>().apply {
                                for (i in itemIndices.indices) {
                                    put(itemIndices[i], it[i])
                                }
                            }))
                        }
                        else {
                            onComplete(AsyncErrorResult(Exception("Failed to process result because" +
                                    " returned places array size (${it.size}) does not match requested item array size (${itemIndices.size})")))
                        }
                    }, {
                        onComplete(AsyncErrorResult(it))
                    })
        }
    }

    private fun testParcelable(board: Board) {
        val parcel = Parcel.obtain()
        board.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val parceledBoard = Board.CREATOR.createFromParcel(parcel)
        for (i in board.items.indices) {
            val item = board.items[i]
            val parceledItem = parceledBoard.items[i]
            if (item == parceledItem) println("equal") else println("unequal")
        }
        parcel.recycle()
    }
}