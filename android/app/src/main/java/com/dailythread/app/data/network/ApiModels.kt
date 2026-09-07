package com.dailythread.app.data.network

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
data class UserDto(val id: String, val email: String?)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: UserDto
)

data class SyncMutationDto(
    @SerializedName("mutation_id") val mutationId: String,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: String,
    val operation: String,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("base_version") val baseVersion: Long,
    val payload: JsonObject
)

data class PushRequest(val action: String = "push", val mutations: List<SyncMutationDto>)
data class PullRequest(val action: String = "pull", val cursor: Long, @SerializedName("device_id") val deviceId: String?, val limit: Int = 200)
data class PushResult(
    @SerializedName("mutation_id") val mutationId: String,
    val status: String,
    val error: String? = null,
    @SerializedName("server_version") val serverVersion: Long? = null,
    @SerializedName("server_entity") val serverEntity: JsonObject? = null,
    val entity: JsonObject? = null
)
data class PushResponse(val ok: Boolean, val results: List<PushResult>)
data class ChangeDto(
    val cursor: Long,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: String,
    val operation: String,
    val version: Long,
    @SerializedName("origin_device_id") val originDeviceId: String?,
    @SerializedName("changed_at") val changedAt: String,
    val payload: JsonObject?
)
data class PullResponse(
    val ok: Boolean,
    val changes: List<ChangeDto>,
    @SerializedName("next_cursor") val nextCursor: Long,
    @SerializedName("has_more") val hasMore: Boolean
)
