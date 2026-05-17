package com.akrapovic.soundkit.community.automation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ActiveGeofenceState @Inject constructor() {
    private val _activeZoneIds = MutableStateFlow<Set<String>>(emptySet())
    val activeZoneIds: StateFlow<Set<String>> = _activeZoneIds.asStateFlow()

    fun setInside(zoneId: String, inside: Boolean) {
        _activeZoneIds.value = if (inside) {
            _activeZoneIds.value + zoneId
        } else {
            _activeZoneIds.value - zoneId
        }
    }

    fun clear() {
        _activeZoneIds.value = emptySet()
    }
}
