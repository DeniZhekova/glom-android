package io.jitrapon.glom.base.component

import android.arch.lifecycle.LifecycleObserver
import com.google.android.gms.location.places.Place
import io.reactivex.Single

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API, returning reactive streams as responses.
 *
 * Created by Jitrapon on 11/30/2017.
 */
interface PlaceProvider : LifecycleObserver {

    fun retrievePlaces(map: HashMap<String, Place>): Single<HashMap<String, Place>>
}