package com.posgas.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posgas.mobile.BuildConfig
import com.posgas.mobile.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

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
    val rate: Double = 36.15,
    val registeredBy: String = "",
    val referenceFund: String = ""
)

data class Supplier(
    val id: String,
    val name: String,
    val active: Boolean = true
)

data class AccountBalance(val cordobas: Double, val dollars: Double)

data class AccountMovement(
    val date: String,
    val type: String,
    val credit: Boolean,
    val amount: Double,
    val currency: String,
    val reference: String,
    val note: String,
    val status: String,
    val day: Int?,
    val amountCordobas: Double
)

data class InventoryItem(
    val id: String,
    val name: String,
    val brand: String = "",
    val unit: String = "",
    val stock: Double = 0.0,
    val minStock: Double = 0.0,
    val suggestedQty: Double = 0.0,
    val suggested: Boolean = false,
    val barcode: String = "",
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
    val rate: String = "36.15",
    val referenceFund: String = ""
)

class MobileViewModel(private val api: ApiClient, private val cache: MobileCache) : ViewModel() {
    var balances by mutableStateOf(emptyMap<String, AccountBalance>())
        private set
    var accountMovements by mutableStateOf(emptyList<AccountMovement>())
        private set
    var accountLoading by mutableStateOf(false)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set
    private var accountLoad: Job? = null
    private var historyQuery: Triple<String, Int, Int>? = null
    private var invoiceRequestId = UUID.randomUUID().toString()

    var catalogEntries by mutableStateOf(emptyMap<String, List<Supplier>>())
        private set
    var catalogErrors by mutableStateOf(emptyMap<String, String>())
        private set

    var invoices by mutableStateOf(emptyList<Invoice>())
        private set

    var form by mutableStateOf(InvoiceFormState())
        private set

    var search by mutableStateOf("")
        private set

    var supplierQuery by mutableStateOf("")
        private set

    var suppliers by mutableStateOf(emptyList<Supplier>())
        private set

    var inventory by mutableStateOf(emptyList<InventoryItem>())
        private set

    var inventorySearch by mutableStateOf("")
        private set

    var currentUser by mutableStateOf<String?>(null)
        private set
    val apiReady = api.isConfigured && BuildConfig.API_SCOPES.isNotBlank()
    var authorizing by mutableStateOf(false)
        private set
    var authorizationError by mutableStateOf<String?>(null)
        private set
    var products by mutableStateOf(emptyList<SaleProduct>())
        private set
    var productsLoading by mutableStateOf(false)
        private set
    var productsMore by mutableStateOf(false)
        private set
    var productsError by mutableStateOf<String?>(null)
        private set
    private var productSearch: Job? = null
    private var productOffset = 0
    var saleExchangeRate by mutableStateOf<Double?>(null)
        private set
    var pendingSale by mutableStateOf(cache.loadPendingSale(BuildConfig.API_DEPLOYMENT_ID))
        private set
    var saleResult by mutableStateOf<SaleResult?>(null)
        private set
    var saleError by mutableStateOf<String?>(null)
        private set
    var saleBusy by mutableStateOf(false)
        private set
    private var saleRejected = false
    val saleCanEdit: Boolean
        get() = saleRejected && saleResult?.state == "NO_ENCONTRADA"

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
            val q = search.searchKey()
            if (q.isBlank()) return invoices
            return invoices.filter {
                listOf(it.supplier, it.date)
                    .any { value -> value.searchKey().contains(q) }
            }
        }

    val currentMonthTotal: Double
        get() = invoices.filter { sameMonth(it.date, 0) && it.status != "Anulada" }.sumOf { it.amountInCordobas(it.rate) }

    val previousMonthTotal: Double
        get() = invoices.filter { sameMonth(it.date, -1) && it.status != "Anulada" }.sumOf { it.amountInCordobas(it.rate) }

    val searchSuggestions: List<String>
        get() {
            val q = search.searchKey()
            return invoices
                .flatMap { listOf(it.supplier, it.date) }
                .filter { it.isNotBlank() && (q.isBlank() || it.searchKey().contains(q)) }
                .distinct()
                .take(6)
        }

    val cashTotal: Double
        get() = form.cashDetail.entries.sumOf { (denomination, quantity) ->
            denomination.toDouble() * (quantity.toIntOrNull() ?: 0)
        }

    val supplierMatches: List<Supplier>
        get() {
            val q = supplierQuery.ifBlank { form.supplier }.searchKey()
            if (q.isBlank()) return suppliers.filter { it.active }.take(6)
            return suppliers
                .filter { it.active && it.name.searchKey().contains(q) }
                .take(6)
        }

    val filteredInventory: List<InventoryItem>
        get() {
            val q = inventorySearch.searchKey()
            val source = if (q.isBlank()) inventory else {
                inventory.filter {
                    listOf(it.name, it.brand, it.barcode)
                        .any { value -> value.searchKey().contains(q) }
                }
            }
            return source.sortedWith(compareByDescending<InventoryItem> { it.suggested }.thenBy { it.name.searchKey() })
        }

    val shoppingList: List<InventoryItem>
        get() = inventory.filter { it.suggested }.sortedBy { it.name.searchKey() }

    fun beginAuthorization(): Boolean {
        if (!apiReady || authorizing || saving || saleBusy) return false
        authorizing = true
        authorizationError = null
        saleRejected = false
        return true
    }

    fun authorizationFailed(message: String) {
        authorizing = false
        authorizationError = message
    }

    fun authorizeSession(token: String?) {
        if (token.isNullOrBlank()) {
            authorizationFailed("Google no devolvió una autorización.")
            return
        }
        api.accessToken = token
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { api.accountData() }
                updateBalances(result)
                currentUser = "Operador Google"
                authorizationError = null
                syncMessage = "Conectado a API"
                load()
                loadSuppliers()
                loadInventory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                api.accessToken = null
                currentUser = null
                authorizationError = failure.message
            } finally {
                authorizing = false
            }
        }
    }

    fun loadAccounts(account: String? = null, month: Int? = null, year: Int? = null) {
        accountLoad?.cancel()
        val query = if (account != null && month != null && year != null) Triple(account, month, year) else null
        if (historyQuery != query) accountMovements = emptyList()
        historyQuery = query
        accountLoading = true
        accountError = null
        accountLoad = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { api.accountData(account, month, year) }
                updateBalances(data)
                val bankAccount = account == "Banco"
                accountMovements = data.rows("movements").map {
                    val rawDate = it.text("FechaHora")
                    val date = parseAccountMovementDate(rawDate)
                    val originalCurrency = it.text("Moneda_origen").ifBlank { "C$" }
                    val amount = when {
                        !bankAccount -> it.number("Monto_origen")
                        originalCurrency == "C$" -> it.number("Monto_C", "Monto_origen")
                        else -> it.number("Monto_C")
                    }
                    AccountMovement(
                        date?.let(::formatAccountMovementDate) ?: rawDate,
                        it.text("Tipo_tx"), it.text("Operacion").equals("CREDITO", true),
                        amount, if (bankAccount) "C$" else originalCurrency,
                        it.text("ID_transferencia", "ID_venta"), it.text("Motivo"), it.text("Estado"),
                        date?.let { Calendar.getInstance(accountTimeZone).apply { time = it }.get(Calendar.DAY_OF_MONTH) },
                        if (originalCurrency == "C$") it.number("Monto_C", "Monto_origen") else it.number("Monto_C")
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                accountError = failure.message ?: "No se pudo consultar la cuenta."
            }
            accountLoading = false
        }
    }

    private fun updateBalances(data: Map<String, Any?>) {
        balances = data.rows("balances").associate {
            it.text("account") to AccountBalance(it.number("cs"), it.number("usd"))
        }
    }

    fun searchSaleProducts(search: String, more: Boolean = false) {
        productSearch?.cancel()
        if (!more) {
            products = emptyList()
            productOffset = 0
            productsMore = false
        }
        productsLoading = true
        productsError = null
        productSearch = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { api.searchProducts(search.trim(), productOffset) }
                val rows = result.rows("rows")
                products = (products + rows.map {
                    SaleProduct(
                        it.text("id"), it.text("nombre"), it.text("codigo"),
                        it.number("precio"), it.bool("disponible")
                    )
                }).distinctBy { it.id }
                productOffset += rows.size
                val total = (result["meta"] as? Map<*, *>)?.get("total") as? Number
                productsMore = rows.isNotEmpty() && productOffset < (total?.toInt() ?: productOffset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                productsError = failure.message ?: "No se pudo consultar el catálogo."
            }
            productsLoading = false
        }
    }

    fun loadSaleConfig() {
        viewModelScope.launch {
            saleExchangeRate = runCatching {
                withContext(Dispatchers.IO) { api.saleConfig().number("tasa_usd_cordoba") }
            }.getOrNull()?.takeIf { it.isFinite() && it > 0 }
        }
    }

    fun submitSale(data: Map<String, Any?>) {
        if (saleBusy || pendingSale != null) return
        val payload = api.encodePayload(mapOf(
            "action" to "mobile_sale",
            "request_id" to UUID.randomUUID().toString(),
            "data" to data
        ))
        if (!cache.savePendingSale(BuildConfig.API_DEPLOYMENT_ID, payload)) {
            saleError = "No se pudo guardar la solicitud en el dispositivo. La venta no se envió."
            return
        }
        pendingSale = payload
        saleRejected = false
        runSaleRequest(send = true)
    }

    fun checkSale() = runSaleRequest(send = false)

    fun retrySale() {
        if (saleResult?.state == "NO_ENCONTRADA" || saleResult?.state == "ERROR") {
            runSaleRequest(send = true)
        }
    }

    fun finishSale() {
        if (saleBusy || (saleResult?.state !in setOf("COMPLETADA", "CANCELADA") && !saleCanEdit)) return
        if (!cache.savePendingSale(BuildConfig.API_DEPLOYMENT_ID, null)) {
            saleError = "No se pudo cerrar la solicitud guardada en el dispositivo."
            return
        }
        pendingSale = null
        saleResult = null
        saleError = null
        saleRejected = false
    }

    private fun runSaleRequest(send: Boolean) {
        val json = pendingSale ?: return
        if (saleBusy) return
        saleBusy = true
        saleError = null
        saleResult = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val payload = api.decodePayload(json)
                    val requestId = payload.text("request_id")
                    val response = if (send) api.registerSale(payload) else api.saleStatus(requestId)
                    check(response.text("request_id") == requestId) { "La API devolvió una solicitud distinta." }
                    SaleResult(response.text("estado"), response.text("id_venta"), response.text("msg"))
                }
                saleResult = result
                if (send) saleRejected = result.state == "ERROR"
                if (result.state == "COMPLETADA") loadAccounts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                saleRejected = false
                saleError = failure.message ?: "No se confirmó la venta. Consulta la solicitud guardada."
            } finally {
                saleBusy = false
            }
        }
    }

    fun recordMovement(
        account: String,
        kind: String,
        destination: String,
        amount: Double,
        currency: String,
        note: String,
        denoms: Map<Int, Int>,
        requestId: String,
        onSaved: () -> Unit
    ) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.recordAccountMovement(buildMap {
                        put("cuenta", account)
                        put("tipo", kind)
                        put("destino", destination)
                        put("moneda", currency)
                        put(if (currency == "USD") "monto" else "monto_c", amount)
                        put("nota", note)
                        put("denoms", denoms.mapKeys { it.key.toString() })
                        put("request_id", requestId)
                    })
                }
            }.onSuccess {
                loadAccounts()
                onSaved()
            }.onFailure { error = it.message ?: "No se pudo registrar el movimiento." }
            saving = false
        }
    }

    fun loadCatalog(entity: String, onLoaded: () -> Unit = {}) {
        catalogErrors = catalogErrors - entity
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.listAll(entity).map(::supplierFromApi) } }
                .onSuccess {
                    updateCatalog(entity, it)
                    onLoaded()
                }
                .onFailure { catalogErrors = catalogErrors + (entity to (it.message ?: "No se pudo cargar el catálogo.")) }
        }
    }

    fun saveCatalogEntry(entity: String, entry: Supplier?, name: String, active: Boolean, onSaved: () -> Unit) {
        if (saving || name.isBlank()) return
        saving = true
        catalogErrors = catalogErrors - entity
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val data = mapOf("nombre" to name.trim(), "activo" to active)
                    val result = if (entry == null) api.create(entity, data) else api.update(entity, entry.id, data)
                    supplierFromApi(result.row("row"))
                }
            }.onSuccess { saved ->
                val entries = catalogEntries[entity].orEmpty().filterNot { it.id == saved.id } + saved
                updateCatalog(entity, entries)
                if (entry != null && entry.name == form.supplier && entity == "proveedores") {
                    updateSupplierQuery(if (active) saved.name else "")
                }
                onSaved()
            }.onFailure { catalogErrors = catalogErrors + (entity to (it.message ?: "No se pudo guardar el registro.")) }
            saving = false
        }
    }

    private fun updateCatalog(entity: String, entries: List<Supplier>) {
        val sorted = entries.sortedBy { it.name.searchKey() }
        catalogEntries = catalogEntries + (entity to sorted)
        if (entity == "proveedores") {
            suppliers = sorted
            cache.saveSuppliers(sorted)
        }
    }

    fun beginAccountInvoice(account: String) {
        clearForm()
        selectInvoiceAccount(account)
    }

    fun selectInvoiceAccount(account: String) {
        val changesCurrency = account == "Banco" && form.currency == "USD"
        form = form.copy(
            sourceFunds = account, payer = "Grano y Alma",
            paymentMethod = if (account == "Banco") "Transferencia" else "Efectivo",
            currency = if (changesCurrency) "C$" else form.currency,
            amount = if (changesCurrency) "" else form.amount,
            egresoCordobas = if (changesCurrency) "" else form.egresoCordobas,
            cashDetail = emptyMap()
        )
    }

    fun clearError() {
        error = null
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

    fun updateInventorySearch(next: String) {
        inventorySearch = next
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
        if (suppliers.any { it.name.searchKey() == name.searchKey() }) return

        if (!api.isConfigured) {
            suppliers = (suppliers + Supplier("LOCAL-PROV-${System.currentTimeMillis()}", name)).sortedBy { it.name }
            cache.saveSuppliers(suppliers)
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
                    .distinctBy { it.name.searchKey() }
                    .sortedBy { it.name }
                cache.saveSuppliers(suppliers)
                supplierQuery = name
                form = form.copy(supplier = name)
                syncMessage = "Proveedor registrado"
            }.onFailure {
                suppliers = (suppliers + Supplier("LOCAL-PROV-${System.currentTimeMillis()}", name)).sortedBy { it.name }
                cache.saveSuppliers(suppliers)
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
                cache.saveInvoices(it)
                syncMessage = "Sincronizado con API"
            }.onFailure {
                error = it.message
                syncMessage = "No se pudo sincronizar con la API"
            }
            downloadingHistory = false
        }
    }

    fun loadSuppliers() {
        if (!api.isConfigured) {
            suppliers = suppliersFromInvoices()
            cache.saveSuppliers(suppliers)
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.listAll("proveedores").map(::supplierFromApi) }
            }.onSuccess {
                suppliers = it.ifEmpty { suppliersFromInvoices() }
                cache.saveSuppliers(suppliers)
            }.onFailure {
                suppliers = suppliersFromInvoices()
                cache.saveSuppliers(suppliers)
            }
        }
    }

    fun loadInventory() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            downloadingHistory = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    api.list("lista_compras").map(::inventoryItemFromApi)
                }
            }.onSuccess {
                inventory = it
                cache.saveInventory(it)
                syncMessage = "Inventario sincronizado"
            }.onFailure {
                error = it.message
                syncMessage = "No se pudo sincronizar inventario"
            }
            downloadingHistory = false
        }
    }

    fun saveInvoice(compact: Boolean = false, onSaved: () -> Unit = {}) {
        if (saving) return
        val amount = form.amount.toDoubleOrNull()
        if (form.supplier.isBlank() || form.concept.isBlank()) {
            error = "Completa proveedor y concepto."
            return
        }
        if (compact && suppliers.none { it.active && it.name.equals(form.supplier.trim(), true) }) {
            error = "Selecciona un proveedor activo o créalo en Gestionar proveedores."
            return
        }
        if (compact && form.sourceFunds.isBlank()) {
            error = "Selecciona la cuenta de salida."
            return
        }
        if (form.payer.isBlank() || form.paymentMethod.isBlank()) {
            error = "Completa pagador y medio de pago."
            return
        }
        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            error = "El monto debe ser mayor a 0."
            return
        }
        val sourceFunds = form.normalizedSourceFunds()
        if (form.affectsBusinessFunds() && sourceFunds.isBlank()) {
            error = "Selecciona si se pago desde caja normal, banco o caja chica."
            return
        }
        if (sourceFunds == "Caja" && kotlin.math.abs(cashTotal - amount) > 0.009) {
            error = "El total de billetes debe coincidir con el monto."
            return
        }
        val rate = form.rate.toDoubleOrNull() ?: 36.15
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
            cashDetail = if (sourceFunds == "Caja") form.cashDenominationsAsInts() else emptyMap(),
            sourceFunds = sourceFunds,
            egresoCordobas = egreso,
            rate = rate,
            registeredBy = currentUser.orEmpty(),
            referenceFund = form.referenceFund.trim()
        )

        if (api.isConfigured) {
            saveRemote(invoice, compact, onSaved)
        } else {
            saveLocal(invoice.copy(id = invoice.id.ifBlank { "LOCAL-${System.currentTimeMillis()}" }))
            syncMessage = "Guardado localmente"
            onSaved()
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
            rate = invoice.rate.formatInput(),
            referenceFund = invoice.referenceFund
        )
        supplierQuery = invoice.supplier
        error = null
    }

    fun deleteInvoice(id: String) {
        if (id.isBlank() || !api.isConfigured) {
            invoices = invoices.filterNot { it.id == id }
            cache.saveInvoices(invoices)
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
                cache.saveInvoices(invoices)
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
        supplierQuery = ""
        invoiceRequestId = UUID.randomUUID().toString()
        error = null
    }

    private fun saveRemote(invoice: Invoice, compact: Boolean, onSaved: () -> Unit) {
        val requestId = invoiceRequestId
        viewModelScope.launch {
            saving = true
            error = null
            runCatching {
                val data = invoice.toApiPayload()
                withContext(Dispatchers.IO) {
                    if (invoice.id.isNotBlank()) {
                        api.update("facturas", invoice.id, data - "registrado_por")
                    } else if (compact) {
                        api.createAccountInvoice(
                            mapOf(
                                "proveedor" to invoice.supplier,
                                "numero_factura" to invoice.number,
                                "monto_total" to invoice.amount,
                                "moneda" to invoice.currency,
                                "concepto" to invoice.concept,
                                "comentario" to invoice.notes,
                                "establecimiento" to invoice.establishment,
                                "medio_pago" to invoice.paymentMethod,
                                "fuente_fondos" to invoice.sourceFunds,
                                "caja_detalle" to mapOf("denoms" to invoice.cashDetail.mapKeys { it.key.toString() })
                            ), requestId
                        )
                    } else {
                        api.create("facturas", data)
                    }
                }
            }.onSuccess {
                load()
                clearForm()
                loadAccounts()
                syncMessage = "Factura guardada en API"
                onSaved()
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
            .distinctBy { it.name.searchKey() }
            .sortedBy { it.name }
        cache.saveInvoices(invoices)
        cache.saveSuppliers(suppliers)
        clearForm()
    }

    private fun suppliersFromInvoices(): List<Supplier> {
        return invoices
            .mapNotNull { invoice -> invoice.supplier.takeIf { it.isNotBlank() } }
            .distinctBy { it.searchKey() }
            .sorted()
            .mapIndexed { index, name -> Supplier("LOCAL-$index", name) }
    }
}

fun Invoice.amountInCordobas(rate: Double = 36.15): Double {
    return if (currency.equals("USD", ignoreCase = true)) amount * rate else amount
}

private fun invoiceFromApi(row: Map<String, Any?>): Invoice {
    val cashDetail = row.text("caja_detalle", "Caja detalle")
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
        notes = row.text("comentario", "Comentario", "notas"),
        cashDetail = parseCashDetail(cashDetail),
        sourceFunds = row.text("fuente_fondos", "Fuente de fondos"),
        egresoCordobas = row.number("egreso_c", "Egreso (C$)"),
        rate = runCatching { JSONObject(cashDetail).optDouble("tasa", 36.15) }.getOrDefault(36.15),
        registeredBy = row.text("registrado_por", "Registrado por", "creado_por"),
        referenceFund = row.text("referencia_fondo", "Referencia fondo")
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
    "comentario" to notes,
    "caja_detalle" to cashDetailJson(),
    "registrado_por" to registeredBy,
    "referencia_fondo" to referenceFund
)

private fun supplierFromApi(row: Map<String, Any?>): Supplier {
    return Supplier(
        id = row.text("id", "ID_proveedor", "id_proveedor"),
        name = row.text("nombre", "Nombre"),
        active = row.bool("activo", "Activo", default = true)
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.rows(key: String): List<Map<String, Any?>> =
    this[key] as? List<Map<String, Any?>> ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.row(key: String): Map<String, Any?> =
    this[key] as? Map<String, Any?> ?: error("La API no devolvió el registro.")

private fun inventoryItemFromApi(row: Map<String, Any?>): InventoryItem {
    return InventoryItem(
        id = row.text("id", "id_insumos", "ID_Insumo"),
        name = row.text("nombre", "Nombre"),
        brand = row.text("marca", "Marca"),
        unit = row.text("unidad", "unidad_inventario", "Unidad"),
        stock = row.number("stock_actual_num", "stock_actual", "stock"),
        minStock = row.number("stock_minimo_num", "stock_minimo", "stockMinimo"),
        suggestedQty = row.number("cantidad_sugerida"),
        suggested = row.bool("comprar_sugerido"),
        barcode = row.text("codigo", "codigo de barra", "Codigo"),
        active = row.bool("activo_bool", default = true)
    )
}

private fun InvoiceFormState.affectsBusinessFunds(): Boolean {
    return payer.equals("Grano y alma", ignoreCase = true)
}

private fun InvoiceFormState.normalizedSourceFunds(): String {
    return normalizeSourceFunds(sourceFunds.ifBlank { defaultSourceFundsForPaymentMethod(paymentMethod) })
}

private fun normalizeSourceFunds(value: String): String {
    return when (value.searchKey()) {
        "banco", "transferencia", "tarjeta", "cuenta bancaria" -> "Banco"
        "caja", "caja normal", "efectivo", "caja registradora" -> "Caja"
        "caja chica", "caja casa", "chica" -> "Caja chica"
        else -> value.trim()
    }
}

private fun defaultSourceFundsForPaymentMethod(paymentMethod: String): String {
    return when (paymentMethod.searchKey()) {
        "banco", "transferencia", "tarjeta" -> "Banco"
        "efectivo", "caja" -> "Caja"
        else -> ""
    }
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
    if (cashDetail.isEmpty() && currency != "USD") return ""
    val denoms = cashDetail
        .filterValues { it > 0 }
        .entries
        .sortedBy { it.key }
        .joinToString(",") { "\"${it.key}\":${it.value}" }
    return "{\"moneda\":\"$currency\",\"denoms\":{$denoms},\"tasa\":${rate.formatInput()}}"
}

private fun today(): String = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date())

internal val accountTimeZone: TimeZone = TimeZone.getTimeZone("America/Managua")

private fun parseAccountMovementDate(raw: String): Date? {
    val value = raw.trim()
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "d/M/yyyy H:mm:ss",
        "d/M/yyyy H:mm", "yyyy-MM-dd", "d/M/yyyy"
    )
    for (pattern in formats) {
        val position = ParsePosition(0)
        val date = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = accountTimeZone
            isLenient = false
        }.parse(value, position)
        if (date != null && position.index == value.length) return date
    }
    return null
}

private fun formatAccountMovementDate(date: Date): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "NI")).apply {
        timeZone = accountTimeZone
    }.format(date)

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

private fun sameMonth(raw: String, monthOffset: Int): Boolean {
    val score = parseDateScore(raw)
    if (score <= 0L) return false
    val target = java.util.Calendar.getInstance()
    target.add(java.util.Calendar.MONTH, monthOffset)
    val date = java.util.Calendar.getInstance()
    date.time = Date(score)
    return target.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
        target.get(java.util.Calendar.MONTH) == date.get(java.util.Calendar.MONTH)
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

private fun Map<String, Any?>.bool(vararg keys: String, default: Boolean = false): Boolean {
    val raw = text(*keys).ifBlank { return default }.searchKey()
    return raw == "true" || raw == "si" || raw == "1" || raw == "yes"
}

private fun Double.formatInput(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else "%.2f".format(Locale.US, this)
}

private fun String.searchKey(): String {
    val normalized = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.US)
}
