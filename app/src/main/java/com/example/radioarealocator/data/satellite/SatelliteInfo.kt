package com.example.radioarealocator.data.satellite

import java.time.Instant

/**
 * 卫星过境信息，用于界面展示。
 */
data class SatelliteInfo(
    val name: String,
    val catalogNumber: Int,
    val modes: List<String>,
    val aosTime: Instant,
    val losTime: Instant,
    val maxElevation: Double,
    val aosAzimuth: Int,
    val losAzimuth: Int
)
