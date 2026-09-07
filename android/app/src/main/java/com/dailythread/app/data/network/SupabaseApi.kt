package com.dailythread.app.data.network

import com.dailythread.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface SupabaseAuthApi {
    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=password")
    suspend fun login(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Body request: LoginRequest): AuthResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    suspend fun signup(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Body request: SignupRequest): SignupResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    suspend fun signupAnonymous(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Body request: Map<String, String> = emptyMap()): AuthResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Body request: RefreshRequest): AuthResponse
}

interface SupabaseProfileApi {
    @GET("rest/v1/profiles")
    suspend fun getProfile(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Header("Authorization") authorization: String, @Query("id") idFilter: String, @Query("select") select: String = "display_name,target_focus_minutes"): List<ProfileDto>

    @Headers("Content-Type: application/json", "Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Header("Authorization") authorization: String, @Query("id") idFilter: String, @Body patch: ProfilePatch): List<ProfileDto>
}

interface SupabaseSyncApi {
    @Headers("Content-Type: application/json")
    @POST("functions/v1/sync")
    suspend fun push(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Header("Authorization") authorization: String, @Body request: PushRequest): PushResponse

    @Headers("Content-Type: application/json")
    @POST("functions/v1/sync")
    suspend fun pull(@Header("apikey") apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY, @Header("Authorization") authorization: String, @Body request: PullRequest): PullResponse
}

object ApiFactory {
    private val retrofit by lazy {
        Retrofit.Builder().baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/").client(OkHttpClient.Builder().build()).addConverterFactory(GsonConverterFactory.create()).build()
    }
    val auth: SupabaseAuthApi by lazy { retrofit.create(SupabaseAuthApi::class.java) }
    val profile: SupabaseProfileApi by lazy { retrofit.create(SupabaseProfileApi::class.java) }
    val sync: SupabaseSyncApi by lazy { retrofit.create(SupabaseSyncApi::class.java) }
}
