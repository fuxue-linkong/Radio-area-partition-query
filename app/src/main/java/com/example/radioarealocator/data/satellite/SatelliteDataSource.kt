package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.TLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 从 CelesTrak 获取业余卫星 TLE 数据源。
 */
class SatelliteDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取业余卫星 TLE 列表。
     */
    suspend fun fetchAmateurTLEs(): List<TLE> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(AMATEUR_TLE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TLE 下载失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("TLE 响应为空")
            parseTLEs(body)
        }
    }

    private fun parseTLEs(text: String): List<TLE> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<TLE>()
        var i = 0
        while (i < lines.size) {
            // TLE 格式：卫星名称（可选）、Line 1、Line 2
            val isNameLine = !lines[i].startsWith("1 ") && !lines[i].startsWith("2 ")
            val name = if (isNameLine) lines[i] else ""
            val line1Index = if (isNameLine) i + 1 else i
            val line2Index = line1Index + 1

            if (line2Index >= lines.size) break

            val line1 = lines[line1Index]
            val line2 = lines[line2Index]

            if (line1.startsWith("1 ") && line2.startsWith("2 ")) {
                try {
                    val tle = TLE(arrayOf(name, line1, line2))
                    result.add(tle)
                } catch (_: IllegalArgumentException) {
                    // 跳过解析失败的 TLE
                }
            }

            i = line2Index + 1
        }
        return result
    }

    companion object {
        private const val AMATEUR_TLE_URL = "https://celestrak.org/NORAD/elements/amateur.txt"
    }
}
