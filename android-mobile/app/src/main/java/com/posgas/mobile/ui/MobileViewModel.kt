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
    val cashDetail: Map<Int, Int> = emptyMap(),
    val sourceFunds: String = "",
    val egresoCordobas: Double = 0.0,
    val rate: Double = 37.0,
    val registeredBy: String = ""
)

data class Supplier(
    val id: String,
    val name: String,
    val active: Boolean = true
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
    val cashDetail: Map<Int, String> = emptyMap(),
    val sourceFunds: String = "",
    val egresoCordobas: String = "",
    val rate: String = "37"
)

class MobileViewModel(private val api: ApiClient) : ViewModel() {
    var invoices by mutableStateOf(seedInvoices())
        private set

    var form by mutableStateOf(InvoiceFormState())
        private set

    var search by mutableStateOf("")
        private set

    var supplierQuery by mutableStateOf("")
        private set

    var suppliers by mutableStateOf<List<Supplier>>(emptyList())
        private set

    var currentUser by mutableStateOf<String?>(null)
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

    val supplierMatches: List<Supplier>
        get() {
            val q = supplierQuery.ifBlank { form.supplier }.trim().lowercase(Locale.US)
            if (q.isBlank()) return suppliers.take(6)
            return suppliers
                .filter { it.active && it.name.lowercase(Locale.US).contains(q) }
                .take(6)
        }

    init {
        load()
        loadSuppliers()
    }

    fun selectUser(user: String) {
        currentUser = user
        syncMessage = "Usuario: $user"
    }

    fun updateForm(next: InvoiceFormState) {
        form = next
        error = null
    }

    fun updateSearch(next: String) {
        search = next
    }

    fun updateSupplierQuery(next: String) {
        supplierQuery = next
        form = form.copy(supplier = next)
        error = null
    }

    fun selectSupplier(supplier: Supplier) {
        supplierQuery = supplier.name
        form = form.copy(supplier = supplier.name)
        error = null
    }

    fun createSupplier() {
        val name = form.supplier.trim()
        if (name.isBlank()) {
            error = "Escribe el nombre del proveedor."
            return
        }
        if (suppliers.any { it.name.equals(name, ignoreCase = true) }) return

        if (!api.isConfigured) {
            suppliers = (suppliers + Supplier("LOCAL-PROV-${System.currentTimeMillis()}", name)).sortedBy { it.name }
            supplierQuery = name
            form = form.copy(supplier = name)
            syncMessage = "Proveedor registrado localmente"
            return
        }

        viewModelScope.launch {
            saving = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    api.create("proveedores", mapOf("nombre" to name, "activo" to true))
                }
            }.onSuccess { row ->
                suppliers = (suppliers + supplierFromApi(row["row"] as? Map<String, Any?> ?: row))
                    .distinctBy { it.name.lowercase(Locale.US) }
                    .sortedBy { it.name }
                supplierQuery = name
                form = form.copy(supplier = name)
                syncMessage = "Proveedor registrado"
            }.onFailure {
                suppliers = (suppliers + Supplier("LOCAL-PROV-${System.currentTimeMillis()}", name)).sortedBy { it.name }
                supplierQuery = name
                form = form.copy(supplier = name)
                syncMessage = "Proveedor guardado localmente; falta redeploy de API"
            }
            saving = false
        }
    }

    fun load() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            downloadingHistory = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    api.list("facturas").map(::invoiceFromApi).sortedWith(recentInvoicesFirst())
                }
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

    fun loadSuppliers() {
        if (!api.isConfigured) {
            suppliers = suppliersFromInvoices()
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.list("proveedores").map(::supplierFromApi) }
            }.onSuccess {
                suppliers = it.ifEmpty { suppliersFromInvoices() }
            }.onFailure {
                suppliers = suppliersFromInvoices()
            }
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
        val rate = form.rate.toDoubleOrNull() ?: 37.0
        val egreso = form.egresoCordobas.toDoubleOrNull()
            ?: if (form.currency.equals("USD", ignoreCase = true)) amount * rate else amount

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
            cashDetail = form.cashDenominationsAsInts(),
            sourceFunds = form.sourceFunds,
            egresoCordobas = egreso,
            rate = rate,
            registeredBy = currentUser.orEmpty()
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
            cashDetail = invoice.cashDetail.mapValues { it.value.toString() },
            sourceFunds = invoice.sourceFunds,
            egresoCordobas = invoice.egresoCordobas.formatInput(),
            rate = invoice.rate.formatInput()
        )
        supplierQuery = invoice.supplier
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
        suppliers = (suppliers + Supplier("LOCAL-PROV-${System.currentTimeMillis()}", invoice.supplier))
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.lowercase(Locale.US) }
            .sortedBy { it.name }
        clearForm()
    }

    private fun suppliersFromInvoices(): List<Supplier> {
        return invoices
            .mapNotNull { invoice -> invoice.supplier.takeIf { it.isNotBlank() } }
            .distinctBy { it.lowercase(Locale.US) }
            .sorted()
            .mapIndexed { index, name -> Supplier("LOCAL-$index", name) }
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
        date = normalizeDisplayDate(row.text("fecha", "Fecha").ifBlank { today() }),
        concept = row.text("concepto", "Concepto", "descripcion"),
        amount = row.number("monto_total", "total", "Monto total"),
        currency = row.text("moneda", "Moneda").ifBlank { "C$" },
        payer = row.text("pagador", "Pagador"),
        paymentMethod = row.text("medio_pago", "metodo_pago", "Medio de pago"),
        category = row.text("categoria", "Categoria", "categoría"),
        status = row.text("estado", "Estado").ifBlank { "Activa" },
        notes = row.text("notas"),
        cashDetail = parseCashDetail(row.text("caja_detalle", "Caja detalle")),
        sourceFunds = row.text("fuente_fondos", "Fuente de fondos"),
        egresoCordobas = row.number("egreso_c", "Egreso (C$)"),
        rate = 37.0,
        registeredBy = row.text("registrado_por", "Registrado por", "creado_por")
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
    "fuente_fondos" to sourceFunds,
    "egreso_c" to egresoCordobas,
    "estado" to status,
    "notas" to notes,
    "caja_detalle" to cashDetailJson(),
    "registrado_por" to registeredBy
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

private fun supplierFromApi(row: Map<String, Any?>): Supplier {
    return Supplier(
        id = row.text("id", "ID_proveedor", "id_proveedor"),
        name = row.text("nombre", "Nombre"),
        active = row.text("activo", "Activo").let { it.isBlank() || it.equals("true", true) || it.equals("si", true) }
    )
}

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
    val denomsStart = raw.indexOf("\"denoms\"")
    if (denomsStart >= 0) {
        val body = raw.substring(denomsStart)
        return Regex("\"(\\d+)\"\\s*:\\s*(\\d+)")
            .findAll(body)
            .associate { match -> match.groupValues[1].toInt() to match.groupValues[2].toInt() }
    }
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

private fun Invoice.cashDetailJson(): String {
    if (cashDetail.isEmpty()) return ""
    val denoms = cashDetail
        .filterValues { it > 0 }
        .entries
        .sortedBy { it.key }
        .joinToString(",") { "\"${it.key}\":${it.value}" }
    return "{\"moneda\":\"$currency\",\"denoms\":{$denoms},\"tasa\":${rate.formatInput()}}"
}

private fun today(): String = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date())

private fun recentInvoicesFirst(): Comparator<Invoice> {
    return compareByDescending<Invoice> { parseDateScore(it.date) }
        .thenByDescending { it.id.toLongOrNull() ?: 0L }
}

private fun parseDateScore(raw: String): Long {
    val value = raw.trim()
    val formats = listOf("yyyy-MM-dd", "d/M/yyyy", "M/d/yyyy")
    for (pattern in formats) {
        runCatching {
            return SimpleDateFormat(pattern, Locale.US).parse(value)?.time ?: 0L
        }
    }
    return 0L
}

private fun normalizeDisplayDate(raw: String): String {
    val score = parseDateScore(raw)
    if (score <= 0L) return raw
    return SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(score))
}

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
