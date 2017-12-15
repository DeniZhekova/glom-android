package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.UiModel

/**
 * UiModel for the board screen
 *
 * @author Jitrapon Tiachunpun
 */
data class BoardUiModel(override var status: UiModel.Status = UiModel.Status.SUCCESS,
                        var items: List<BoardItemUiModel>? = null,
                        var shouldLoadPlaceInfo: Boolean = false,
                        var shouldLoadUserAvatars: Boolean = false,
                        var itemsChangedIndices: MutableList<Int>? = null) : UiModel