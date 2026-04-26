package com.posgas.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posgas.mobile.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Invoice(
    val id: String = "",
    val number: String,
    val establishment: String,
    val supplier: String,
    val date: String,
    val concept: String,
    val amount: Double,
    val currency: String,
    val payer: String,
    val paymentMethod: String,
    val category: String,
    val status: String = "Activa",
    val notes: String = "",
    val cashDetail: Map<Int, Int> = emptyMap()
)

data class InvoiceFormState(
    val id: String? = null,
    val number: String = "",
    val establishment: String = "Villa Fontana",
    val supplier: String = "",
    val date: String = today(),
    val concept: String = "",
    val amount: String = "",
    val currency: String = "C$",
    val payer: String = "",
    val paymentMethod: String = "",
    val category: String = "",
    val status: String = "Activa",
    val notes: String = "",
    val cashDetail: Map<Int, String> = emptyMap()
)

class MobileViewModel(private val api: ApiClient) : ViewModel() {
    var invoices by mutableStateOf(seedInvoices())
        private set

    var form by mutableStateOf(InvoiceFormState())
        private set

    var search by mutableStateOf("")
        private set

    var downloadingHistory by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var syncMessage by mutableStateOf(if (api.isConfigured) "Conectado a API" else "Modo local")
        private set

    val isEditing: Boolean
        get() = form.id != null

    val filteredInvoices: List<Invoice>
        get() {
            val q = search.trim().lowercase(Locale.US)
            if (q.isBlank()) return invoices
            return invoices.filter {
                listOf(it.number, it.supplier, it.concept, it.category, it.payer, it.establishment)
                    .any { value -> value.lowercase(Locale.US).contains(q) }
            }
        }

    val totalCostCordobas: Double
        get() = invoices.filter { it.status != "Anulada" }.sumOf { it.amountInCordobas() }

    val activeCount: Int
        get() = invoices.count { it.status != "Anulada" }

    val usdCount: Int
        get() = invoices.count { it.currency.equals("USD", ignoreCase = true) }

    val cashTotal: Double
        get() = form.cashDetail.entries.sumOf { (denomination, quantity) ->
            denomination * (quantity.toIntOrNull() ?: 0)
        }.toDouble()

    init {
        load()
    }

    fun updateForm(next: InvoiceFormState) {
        form = next
        error = null
    }

    fun updateSearch(next: String) {
        search = next
    }

    fun load() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            downloadingHistory = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) { api.list("facturas").map(::invoiceFromApi) }
            }.onSuccess {
                invoices = it
                syncMessage = "Sincronizado con API"
            }.onFailure {
                error = it.message
                syncMessage = "API no disponible, usando datos locales"
            }
            downloadingHistory = false
        }
    }

    fun saveInvoice() {
        val amount = form.amount.toDoubleOrNull()
        if (form.number.isBlank() || form.supplier.isBlank() || form.concept.isBlank()) {
            error = "Completa numero, proveedor y concepto."
            return
        }
        if (form.category.isBlank() || form.payer.isBlank() || form.paymentMethod.isBlank()) {
            error = "Completa categoria, pagador y medio de pago."
            return
        }
        if (amount == null || amount <= 0.0) {
            error = "El monto debe ser mayor a 0."
            return
        }
        if (form.usesCash() && cashTotal != amount) {
            error = "El total de billetes debe coincidir con el monto."
            return
        }

        val invoice = Invoice(
            id = form.id.orEmpty(),
            number = form.number.trim(),
            establishment = form.establishment.trim().ifBlank { "Villa Fontana" },
            supplier = form.supplier.trim(),
            date = form.date.trim().ifBlank { today() },
            concept = form.concept.trim(),
            amount = amount,
            currency = form.currency.trim().ifBlank { "C$" },
            payer = form.payer.trim(),
            paymentMethod = form.paymentMethod.trim(),
            category = form.category.trim(),
            status = form.status.trim().ifBlank { "Activa" },
            notes = form.notes.trim(),
            cashDetail = form.cashDenominationsAsInts()
        )

        if (api.isConfigured) {
            saveRemote(invoice)
        } else {
            saveLocal(invoice.copy(id = invoice.id.ifBlank { "LOCAL-${System.currentTimeMillis()}" }))
            syncMessage = "Guardado localmente"
        }
    }

    fun editInvoice(invoice: Invoice) {
        form = InvoiceFormState(
            id = invoice.id,
            number = invoice.number,
            establishment = invoice.establishment,
            supplier = invoice.supplier,
            date = invoice.date,
            concept = invoice.concept,
            amount = invoice.amount.formatInput(),
            currency = invoice.currency,
            payer = invoice.payer,
            paymentMethod = invoice.paymentMethod,
            category = invoice.category,
            status = invoice.status,
            notes = invoice.notes,
            cashDetail = invoice.cashDetail.mapValues { it.value.toString() }
        )
        error = null
    }

    fun deleteInvoice(id: String) {
        if (id.isBlank() || !api.isConfigured) {
            invoices = invoices.filterNot { it.id == id }
            if (form.id == id) clearForm()
            return
        }

        viewModelScope.launch {
            saving = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) { api.delete("facturas", id) }
            }.onSuccess {
                invoices = invoices.filterNot { it.id == id }
                if (form.id == id) clearForm()
                syncMessage = "Factura eliminada"
            }.onFailure {
                error = it.message
            }
            saving = false
        }
    }

    fun clearForm() {
        form = InvoiceFormState()
        error = null
    }

    private fun saveRemote(invoice: Invoice) {
        viewModelScope.launch {
            saving = true
            error = null
            runCatching {
                val data = invoice.toApiPayload()
                withContext(Dispatchers.IO) {
                    if (isEditing && invoice.id.isNotBlank()) {
                        api.update("facturas", invoice.id, data)
                    } else {
                        api.create("facturas", data)
                    }
                }
            }.onSuccess {
                load()
                clearForm()
                syncMessage = "Factura guardada en API"
            }.onFailure {
                error = it.message
            }
            saving = false
        }
    }

    private fun saveLocal(invoice: Invoice) {
        invoices = if (!isEditing) {
            listOf(invoice) + invoices
        } else {
            invoices.map { current -> if (current.id == invoice.id) invoice else current }
        }
        clearForm()
    }
}

fun Invoice.amountInCordobas(rate: Double = 37.0): Double {
    return if (currency.equals("USD", ignoreCase = true)) amount * rate else amount
}

private fun invoiceFromApi(row: Map<String, Any?>): Invoice {
    return Invoice(
        id = row.text("id_factura", "id"),
        number = row.text("numero_factura", "numero", "Numeor de factura"),
        establishment = row.text("establecimiento", "Establecimiento").ifBlank { "Villa Fontana" },
        supplier = row.text("proveedor", "Proveedor"),
        date = row.text("fecha", "Fecha").ifBlank { today() },
        concept = row.text("concepto", "Concepto", "descripcion"),
        amount = row.number("monto_total", "total", "Monto total"),
        currency = row.text("moneda", "Moneda").ifBlank { "C$" },
        payer = row.text("pagador", "Pagador"),
        paymentMethod = row.text("medio_pago", "metodo_pago", "Medio de pago"),
        category = row.text("categoria", "Categoria", "categoría"),
        status = row.text("estado", "Estado").ifBlank { "Activa" },
        notes = row.text("notas"),
        cashDetail = parseCashDetail(row.text("caja_detalle"))
    )
}

private fun Invoice.toApiPayload(): Map<String, Any?> = mapOf(
    "numero_factura" to number,
    "establecimiento" to establishment,
    "proveedor" to supplier,
    "fecha" to date,
    "concepto" to concept,
    "monto_total" to amount,
    "total" to amount,
    "moneda" to currency,
    "pagador" to payer,
    "medio_pago" to paymentMethod,
    "categoria" to category,
    "estado" to status,
    "notas" to notes,
    "caja_detalle" to cashDetail
        .filterValues { it > 0 }
        .entries
        .joinToString(",") { "${it.key}x${it.value}" }
)

private fun seedInvoices(): List<Invoice> = listOf(
    Invoice(
        id = "LOCAL-1",
        number = "ML-2026-0001",
        establishment = "Villa Fontana",
        supplier = "Gasolinera Central",
        date = today(),
        concept = "Combustible",
        amount = 2116.0,
        currency = "C$",
        payer = "Luis",
        paymentMethod = "Transferencia",
        category = "Transporte"
    ),
    Invoice(
        id = "LOCAL-2",
        number = "ML-2026-0002",
        establishment = "Villa Fontana",
        supplier = "Taller Norte",
        date = today(),
        concept = "Mantenimiento",
        amount = 125.0,
        currency = "USD",
        payer = "Grano y alma",
        paymentMethod = "Banco",
        category = "Mantenimiento"
    )
)

private fun InvoiceFormState.usesCash(): Boolean {
    val method = paymentMethod.lowercase(Locale.US)
    return method == "efectivo" || method == "caja"
}

private fun InvoiceFormState.cashDenominationsAsInts(): Map<Int, Int> {
    return cashDetail.mapNotNull { (denomination, quantity) ->
        val count = quantity.toIntOrNull() ?: 0
        if (count > 0) denomination to count else null
    }.toMap()
}

private fun parseCashDetail(raw: String): Map<Int, Int> {
    return raw.split(",")
        .mapNotNull { part ->
            val pieces = part.trim().split("x")
            if (pieces.size != 2) return@mapNotNull null
            val denomination = pieces[0].toIntOrNull() ?: return@mapNotNull null
            val count = pieces[1].toIntOrNull() ?: return@mapNotNull null
            denomination to count
        }
        .toMap()
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun Map<String, Any?>.text(vararg keys: String): String {
    for (key in keys) {
        val value = this[key]
        if (value != null && value.toString().isNotBlank()) return value.toString()
    }
    return ""
}

private fun Map<String, Any?>.number(vararg keys: String): Double {
    return text(*keys).replace(",", "").toDoubleOrNull() ?: 0.0
}

private fun Double.formatInput(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else "%.2f".format(Locale.US, this)
}
