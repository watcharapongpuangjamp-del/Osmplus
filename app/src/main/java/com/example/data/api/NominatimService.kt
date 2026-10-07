package com.example.data.api

import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers

@Serializable
data class NominatimResponse(
    val place_id: Long,
    val lat: String,
    val lon: String,
    val display_name: String,
    val type: String? = null,
    val importance: Double? = null
)

interface NominatimService {
    @Headers("User-Agent: SmartOsm-Android-App")
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 5,
        @Query("accept-language") language: String = "th"
    ): List<NominatimResponse>

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/"
        
        fun create(): NominatimService {
            val contentType = "application/json".toMediaType()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(NominatimService::class.java)
        }
    }
}
