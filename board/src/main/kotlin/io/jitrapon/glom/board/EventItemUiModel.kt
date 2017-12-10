package io.jitrapon.glom.board

import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.data.AndroidString
import io.jitrapon.glom.board.BoardItemUiModel.Companion.TYPE_EVENT

/**
 * @author Jitrapon Tiachunpun
 */
data class EventItemUiModel(override val itemId: String?,
                            val title: String,
                            val dateTime: String?,
                            var location: AndroidString?,
                            val mapLatLng: LatLng?,     // if not null, will show mini map at the specified lat lng
                            override val itemType: Int = TYPE_EVENT) : BoardItemUiModel