package com.posgas.mobile.data

import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val deploymentId: String) {
    val isConfigured: Boolean
        get() = deploymentId.matches(Regex("[A-Za-z0-9_-]+"))

    @Volatile
    var accessToken: String? = null

    private val mapAdapter = Moshi.Builder().build().adapter(Map::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    fun list(entity: String): List<Map<String, Any?>> =
        query(mapOf("entity" to entity, "sort" to "fecha", "dir" to "desc")).rows()

    fun listAll(entity: String): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        do {
            val page = query(mapOf("entity" to entity, "offset" to rows.size))
            val batch = page.rows()
            rows.addAll(batch)
            val total = ((page["meta"] as? Map<*, *>)?.get("total") as? Number)?.toInt() ?: rows.size
        } while (batch.isNotEmpty() && rows.size < total)
        return rows
    }

    fun create(entity: String, data: Map<String, Any?>) =
        execute(mapOf("action" to "create", "entity" to entity, "data" to data))

    fun update(entity: String, id: String, data: Map<String, Any?>) =
        execute(mapOf("action" to "update", "entity" to entity, "id" to id, "data" to data))

    fun delete(entity: String, id: String): Boolean =
        execute(mapOf("action" to "delete", "entity" to entity, "id" to id))["deleted"] == true

    fun accountData(account: String? = null, month: Int? = null, year: Int? = null): Map<String, Any?> {
        val query = buildMap {
            put("entity", "cuentas")
            account?.let { put("cuenta", it) }
            if (month != null && year != null) {
                put("mes", month)
                put("anio", year)
            }
        }
        val result = query(query)
        check(result["balances"] is List<*>) { "La API de cuentas aún no está disponible." }
        return result
    }

    fun recordAccountMovement(data: Map<String, Any?>) =
        execute(mapOf("action" to "mobile_account_movement", "data" to data))

    fun createAccountInvoice(data: Map<String, Any?>, requestId: String) =
        execute(mapOf("action" to "mobile_account_invoice", "request_id" to requestId, "data" to data))

    fun searchProducts(search: String, offset: Int) =
        query(mapOf("entity" to "productos", "q" to search, "limit" to 25, "offset" to offset))

    fun saleConfig() = query(mapOf("entity" to "config_venta"))

    fun saleStatus(requestId: String) =
        query(mapOf("entity" to "ventas", "request_id" to requestId), allowSaleStatus = true)

    fun registerSale(payload: Map<String, Any?>) = execute(payload, allowSaleStatus = true)

    fun encodePayload(payload: Map<String, Any?>): String = mapAdapter.toJson(payload)

    @Suppress("UNCHECKED_CAST")
    fun decodePayload(json: String): Map<String, Any?> =
        mapAdapter.fromJson(json) as? Map<String, Any?> ?: error("La respuesta de la API está vacía.")

    private fun query(params: Map<String, Any?>, allowSaleStatus: Boolean = false) =
        execute(mapOf("consulta" to params), allowSaleStatus)

    @Suppress("UNCHECKED_CAST")
    private fun execute(payload: Map<String, Any?>, allowSaleStatus: Boolean = false): Map<String, Any?> {
        check(isConfigured) { "Falta configurar el despliegue de Apps Script." }
        val token = accessToken ?: error("Autoriza tu cuenta Google para continuar.")
        val body = encodePayload(mapOf("function" to "mobileApi", "parameters" to listOf(payload)))
        val request = Request.Builder()
            .url("https://script.googleapis.com/v1/scripts/$deploymentId:run")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> {
                    if (accessToken == token) accessToken = null
                    error("La sesión de Google venció. Vuelve a autorizarla.")
                }
                403 -> error("Google rechazó el acceso. Revisa los permisos del operador y el proyecto OAuth.")
                404 -> error("Apps Script no encontró el despliegue de la API.")
            }
            check(response.isSuccessful) { "Apps Script respondió HTTP ${response.code}." }
            val operation = decodePayload(response.body?.string().orEmpty())
            val failure = operation["error"] as? Map<*, *>
            if (failure != null) {
                val detail = (failure["details"] as? List<*>)?.firstOrNull() as? Map<*, *>
                error((detail?.get("errorMessage") ?: failure["message"] ?: "Apps Script no pudo ejecutar la solicitud.").toString())
            }
            check(operation["done"] == true) { "Apps Script no confirmó la ejecución." }
            val result = (operation["response"] as? Map<*, *>)?.get("result") as? Map<String, Any?>
                ?: error("Apps Script no devolvió el resultado de la operación.")
            check(result["ok"] == true || (allowSaleStatus && result["estado"] is String)) {
                result["error"]?.toString() ?: "La API rechazó la solicitud."
            }
            return result
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.rows(): List<Map<String, Any?>> =
        this["rows"] as? List<Map<String, Any?>> ?: error("La API no devolvió los registros.")
}
