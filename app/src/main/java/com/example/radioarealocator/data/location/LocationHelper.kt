package com.example.radioarealocator.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): Location {
        if (!hasPermission()) {
            throw SecurityException("缺少定位权限")
        }

        // 优先尝试获取实时定位，最多等 10 秒
        val current = try {
            withTimeout(10_000) { requestCurrentLocation() }
        } catch (e: Exception) {
            null
        }

        if (current != null) return current

        //  fallback 到最近一次已知定位，最多等 5 秒
        val last = try {
            withTimeout(5_000) { requestLastLocation() }
        } catch (e: Exception) {
            null
        }

        return last ?: throw Exception("无法获取定位，请检查手机 GPS 或网络定位是否开启")
    }

    private suspend fun requestCurrentLocation(): Location = suspendCancellableCoroutine { continuation ->
        val token = CancellationTokenSource()
        try {
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                token.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(location)
                } else {
                    continuation.resumeWithException(NullPointerException("定位返回空值"))
                }
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }.addOnCanceledListener {
                continuation.cancel()
            }
        } catch (e: SecurityException) {
            continuation.resumeWithException(e)
        }

        continuation.invokeOnCancellation {
            token.cancel()
        }
    }

    private suspend fun requestLastLocation(): Location = suspendCancellableCoroutine { continuation ->
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(NullPointerException("没有最近一次定位记录"))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        } catch (e: SecurityException) {
            continuation.resumeWithException(e)
        }
    }
}
