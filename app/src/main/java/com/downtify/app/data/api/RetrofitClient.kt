package com.downtify.app.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class RetrofitClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        const val DEFAULT_URL = "http://192.168.1.100:8000/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = client.newBuilder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var _baseUrl: String = DEFAULT_URL
    private var retrofit: Retrofit = createRetrofit(_baseUrl)

    val apiService: DowntifyApiService = retrofit.create(DowntifyApiService::class.java)

    val serverUrlFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SERVER_URL_KEY] ?: DEFAULT_URL
        }

    suspend fun updateServerUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = normalized
        }
        _baseUrl = normalized
        retrofit = createRetrofit(normalized)
    }

    fun getBaseUrlSync(): String {
        return runBlocking {
            serverUrlFlow.first()
        }
    }

    fun getApiServiceSync(): DowntifyApiService {
        return retrofit.create(DowntifyApiService::class.java)
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
