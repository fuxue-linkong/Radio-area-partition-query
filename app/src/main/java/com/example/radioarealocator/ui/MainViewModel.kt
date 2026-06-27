package com.example.radioarealocator.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.location.LocationHelper
import com.example.radioarealocator.data.satellite.SatelliteDataSource
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.satellite.SatellitePredictor
import com.example.radioarealocator.data.zone.ZoneResolver
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)
    private val satelliteDataSource = SatelliteDataSource()
    private val satellitePredictor = SatellitePredictor()

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    // 卫星数据来源设置："ALL" / "CT" / "SNOGS"
    private val _satelliteSource = mutableStateOf("ALL")
    val satelliteSource: State<String> = _satelliteSource

    fun setSatelliteSource(source: String) {
        _satelliteSource.value = source
    }

    val hasLocationPermission: Boolean
        get() = locationHelper.hasPermission()

    fun refreshLocation() {
        if (!locationHelper.hasPermission()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "需要定位权限"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            satelliteError = null
        )
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val zoneInfo = ZoneResolver.resolve(location.latitude, location.longitude)
                val address = locationHelper.getAddress(location.latitude, location.longitude)
                val result = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    cqZone = zoneInfo.cqZone,
                    ituZone = zoneInfo.ituZone,
                    maidenhead = zoneInfo.maidenhead,
                    address = address
                )

                // 同步刷新卫星信息
                val satellites = refreshSatellites(location.latitude, location.longitude)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = result,
                    satellites = satellites,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "定位失败"
                )
            }
        }
    }

    private suspend fun refreshSatellites(latitude: Double, longitude: Double): List<SatelliteInfo> {
        return try {
            val tles = satelliteDataSource.fetchAmateurTLEs(source = _satelliteSource.value)
            satellitePredictor.predictUpcomingPasses(
                sourcedTles = tles,
                latitude = latitude,
                longitude = longitude
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(satelliteError = e.message)
            emptyList()
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val result: LocationResult? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    val error: String? = null,
    val satelliteError: String? = null
)
