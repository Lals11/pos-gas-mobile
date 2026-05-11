package com.posgas.mobile.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MobileCache(context: Context) {
    private val prefs = context.getSharedPreferences("pos_gas_mobile_cache", Context.MODE_PRIVATE)

    fun loadInvoices(): List<Invoice> = runCatching {
        val raw = prefs.getString("invoices", "[]").orEmpty()
        val arr = JSONArray(raw)
        List(arr.length()) { index -> arr.getJSONObject(index).toInvoice() }
    }.getOrDefault(emptyList())

    fun saveInvoices(invoices: List<Invoice>) {
        prefs.edit().putString("invoices", JSONArray(invoices.map { it.toJson() }).toString()).apply()
    }

    fun loadSuppliers(): List<Supplier> = runCatching {
        val raw = prefs.getString("suppliers", "[]").orEmpty()
        val arr = JSONArray(raw)
        List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            Supplier(
                id = obj.optString("id"),
                name = obj.optString("name"),
                active = obj.optBoolean("active", true)
            )
        }
    }.getOrDefault(emptyList())

    fun saveSuppliers(suppliers: List<Supplier>) {
        val arr = JSONArray(suppliers.map {
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("active", it.active)
        })
        prefs.edit().putString("suppliers", arr.toString()).apply()
    }

    fun loadInventory(): List<InventoryItem> = runCatching {
        val raw = prefs.getString("inventory", "[]").orEmpty()
        val arr = JSONArray(raw)
        List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            InventoryItem(
                id = obj.optString("id"),
                name = obj.optString("name"),
                brand = obj.optString("brand"),
                unit = obj.optString("unit"),
                stock = obj.optDouble("stock", 0.0),
                minStock = obj.optDouble("minStock", 0.0),
                suggestedQty = obj.optDouble("suggestedQty", 0.0),
                suggested = obj.optBoolean("suggested", false),
                barcode = obj.optString("barcode"),
                active = obj.optBoolean("active", true)
            )
        }
    }.getOrDefault(emptyList())

    fun saveInventory(items: List<InventoryItem>) {
        val arr = JSONArray(items.map {
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("brand", it.brand)
                .put("unit", it.unit)
                .put("stock", it.stock)
                .put("minStock", it.minStock)
                .put("suggestedQty", it.suggestedQty)
                .put("suggested", it.suggested)
                .put("barcode", it.barcode)
                .put("active", it.active)
        })
        prefs.edit().putString("inventory", arr.toString()).apply()
    }
}

private fun Invoice.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("number", number)
        .put("establishment", establishment)
        .put("supplier", supplier)
        .put("date", date)
        .put("concept", concept)
        .put("amount", amount)
        .put("currency", currency)
        .put("payer", payer)
        .put("paymentMethod", paymentMethod)
        .put("category", category)
        .put("status", status)
        .put("notes", notes)
        .put("sourceFunds", sourceFunds)
        .put("egresoCordobas", egresoCordobas)
        .put("rate", rate)
        .put("registeredBy", registeredBy)
        .put("referenceFund", referenceFund)
}

private fun JSONObject.toInvoice(): Invoice {
    return Invoice(
        id = optString("id"),
        number = optString("number"),
        establishment = optString("establishment"),
        supplier = optString("supplier"),
        date = optString("date"),
        concept = optString("concept"),
        amount = optDouble("amount", 0.0),
        currency = optString("currency", "C$"),
        payer = optString("payer"),
        paymentMethod = optString("paymentMethod"),
        category = optString("category"),
        status = optString("status", "Activa"),
        notes = optString("notes"),
        sourceFunds = optString("sourceFunds"),
        egresoCordobas = optDouble("egresoCordobas", 0.0),
        rate = optDouble("rate", 36.15),
        registeredBy = optString("registeredBy"),
        referenceFund = optString("referenceFund")
    )
}
