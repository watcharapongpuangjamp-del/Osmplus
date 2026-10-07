package com.example.domain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GoogleSheetsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads a Google Sheet as an XLSX workbook InputStream.
     * Supports both public spreadsheets (no token required) and private spreadsheets (using OAuth accessToken).
     */
    suspend fun downloadSpreadsheetAsXlsx(
        spreadsheetId: String,
        accessToken: String? = null
    ): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val cleanId = extractSpreadsheetId(spreadsheetId)
            val url = "https://docs.google.com/spreadsheets/d/$cleanId/export?format=xlsx"
            
            Log.d("GoogleSheetsService", "Downloading spreadsheet from: $url")
            
            val requestBuilder = Request.Builder().url(url)
            
            if (!accessToken.isNullOrBlank()) {
                val token = accessToken.trim().removePrefix("Bearer ").trim()
                requestBuilder.addHeader("Authorization", "Bearer $token")
                Log.d("GoogleSheetsService", "Added Authorization token header for private sheet access")
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorMsg = when (code) {
                        401 -> "ไม่ได้รับอนุญาต (Unauthorized): โปรดตรวจสอบความถูกต้องของ Access Token"
                        403 -> "ไม่มีสิทธิ์เข้าถึง (Forbidden): โปรดตรวจสอบสิทธิ์การแชร์ไฟล์ Google Sheets หรือใช้ Access Token"
                        404 -> "ไม่พบไฟล์ (Not Found): โปรดตรวจสอบความถูกต้องของ Spreadsheet ID"
                        else -> "HTTP $code: ${response.message}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("ดึงข้อมูลสำเร็จแต่ไม่พบเนื้อหา (Empty Body)"))
                val bytes = body.bytes()
                Log.d("GoogleSheetsService", "Successfully downloaded ${bytes.size} bytes from Google Sheets")
                Result.success(ByteArrayInputStream(bytes))
            }
        } catch (e: Exception) {
            Log.e("GoogleSheetsService", "Failed to download Google Sheet", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts Spreadsheet ID from Google Sheets URL or returns the input if it's already an ID.
     */
    fun extractSpreadsheetId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("docs.google.com/spreadsheets")) {
            val parts = trimmed.split("/d/")
            if (parts.size > 1) {
                return parts[1].split("/")[0]
            }
        }
        return trimmed
    }
}
