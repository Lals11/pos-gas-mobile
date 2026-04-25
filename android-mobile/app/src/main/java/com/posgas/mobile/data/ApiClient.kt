package com.posgas.mobile.data

import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter(Map::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun list(entity: String): List<Map<String, Any?>> {
        val req = Request.Builder()
            .url("$baseUrl?entity=$entity")
            .get()
            .build()
        client.newCall(req).execute().use { rsp ->
            val body = rsp.body?.string().orEmpty()
            val root = mapAdapter.fromJson(body) ?: emptyMap<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            return (root["rows"] as? List<Map<String, Any?>>) ?: emptyList()
        }
    }

    fun create(entity: String, data: Map<String, Any?>) {
        post(mapOf("action" to "create", "entity" to entity, "data" to data))
    }

    fun update(entity: String, id: String, data: Map<String, Any?>) {
        post(mapOf("action" to "update", "entity" to entity, "id" to id, "data" to data))
    }

    fun delete(entity: String, id: String) {
        post(mapOf("action" to "delete", "entity" to entity, "id" to id))
    }

    private fun post(payload: Map<String, Any?>) {
        val json = moshi.adapter(Map::class.java).toJson(payload)
        val req = Request.Builder()
            .url(baseUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().close()
    }
}
