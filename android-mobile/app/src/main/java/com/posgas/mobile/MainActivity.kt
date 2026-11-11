package com.posgas.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.posgas.mobile.data.ApiClient
import com.posgas.mobile.ui.Invoice
import com.posgas.mobile.ui.AccountBalance
import com.posgas.mobile.ui.AccountMovement
import com.posgas.mobile.ui.InvoiceFormState
import com.posgas.mobile.ui.InventoryItem
import com.posgas.mobile.ui.MobileCache
import com.posgas.mobile.ui.MobileViewModel
import com.posgas.mobile.ui.Supplier
import com.posgas.mobile.ui.amountInCordobas
import com.posgas.mobile.ui.accountTimeZone
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private val Bg = Color(0xFF17181C)
private val Panel = Color(0xFF1E1F24)
private val Panel2 = Color(0xFF27282E)
private val Panel3 = Color(0xFF2E2F36)
private val Brand = Color(0xFFFF5500)
private val Brand2 = Color(0xFFFF4500)
private val TextMain = Color(0xFFD3D3D3)
private val TextSoft = Color(0xFFA9A9A9)
private val Ok = Color(0xFF10B981)
private val Err = Color(0xFFB91C1C)
private val Accounts = listOf("Banco", "Caja chica", "Caja")

class MainActivity : ComponentActivity() {
    private lateinit var vm: MobileViewModel
    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data).accessToken }
            .onSuccess(vm::authorizeSession)
            .onFailure { vm.authorizationFailed("No se completó la autorización de Google.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, Factory(BuildConfig.API_DEPLOYMENT_ID, MobileCache(this)))[MobileViewModel::class.java]
        val snapshot = if (BuildConfig.DEBUG) readAccountSnapshot(filesDir) else null
        setContent { PosGasApp(vm, ::authorizeGoogle, snapshot) }
    }

    private fun authorizeGoogle() {
        val scopes = BuildConfig.API_SCOPES.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::Scope)
        if (!vm.beginAuthorization()) return
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(this) { result ->
                val resolution = result.pendingIntent
                if (result.hasResolution() && resolution != null) {
                    authorization.launch(IntentSenderRequest.Builder(resolution.intentSender).build())
                } else {
                    vm.authorizeSession(result.accessToken)
                }
            }
            .addOnFailureListener(this) { vm.authorizationFailed("Google no pudo autorizar la cuenta. Revisa la configuración OAuth.") }
    }
}

private data class AccountSnapshot(val capturedAt: String, val balances: Map<String, AccountBalance>)

private fun readAccountSnapshot(filesDir: File): AccountSnapshot? = runCatching {
    val data = JSONObject(File(filesDir, "account-preview.json").readText())
    val accounts = data.getJSONObject("balances")
    val balances = Accounts.associateWith { account ->
        val balance = accounts.getJSONObject(account)
        val cs = balance.getDouble("cs")
        val usd = balance.getDouble("usd")
        require(cs.isFinite() && usd.isFinite())
        AccountBalance(cs, usd)
    }
    AccountSnapshot(data.getString("capturedAt"), balances)
}.getOrNull()

class Factory(private val deploymentId: String, private val cache: MobileCache) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MobileViewModel(ApiClient(deploymentId), cache) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosGasApp(vm: MobileViewModel, onAuthorize: () -> Unit, snapshot: AccountSnapshot?) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Brand,
        secondary = Brand2,
        background = Bg,
        surface = Panel,
        onSurface = TextMain,
        onBackground = TextMain
    )

    MaterialTheme(colorScheme = colors) {
        if (vm.currentUser == null) {
            var preview by rememberSaveable { mutableStateOf(false) }
            if (BuildConfig.DEBUG && preview) {
                AccountsDesignPreview(snapshot, onBack = { preview = false })
            } else {
                LoginScreen(
                    vm, onAuthorize,
                    previewLabel = if (snapshot != null) "Ver saldos reales" else "Probar diseño",
                    onPreview = if (BuildConfig.DEBUG) ({ preview = true }) else null
                )
            }
            return@MaterialTheme
        }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var detailInvoice by remember { mutableStateOf<Invoice?>(null) }
        var deleteInvoice by remember { mutableStateOf<Invoice?>(null) }
        var selectedSection by rememberSaveable { mutableStateOf("Cuentas") }
        var expandedAccount by rememberSaveable { mutableStateOf<String?>(null) }
        var accountAction by rememberSaveable { mutableStateOf<String?>(null) }
        var movementType by rememberSaveable { mutableStateOf<String?>(null) }
        var saleAccount by rememberSaveable { mutableStateOf<String?>(null) }
        var catalog by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(vm.currentUser) {
            vm.loadCatalog("conceptos")
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    selected = selectedSection,
                    onAuthorize = onAuthorize,
                    onSelect = {
                        selectedSection = it
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = Bg,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰", color = Brand, fontWeight = FontWeight.Bold)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    when (selectedSection) {
                                        "Cuentas" -> "Cuentas"
                                        "Inventario" -> "Inventario"
                                        else -> "Registro de Facturas"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${vm.syncMessage} · Usuario: ${vm.currentUser.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Brand
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Panel,
                            titleContentColor = TextMain
                        )
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Bg),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (vm.downloadingHistory) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Brand,
                                trackColor = Panel3
                            )
                        }
                    }
                    if (selectedSection == "Cuentas") {
                        if (vm.pendingSale != null) {
                            item {
                                OutlinedButton(onClick = { saleAccount = "Pendiente" }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Consultar venta pendiente")
                                }
                            }
                        }
                        items(Accounts, key = { it }) { account ->
                            AccountCard(
                                account = account,
                                balance = vm.balances[account],
                                expanded = expandedAccount == account,
                                selectedAction = accountAction.takeIf { expandedAccount == account },
                                onToggle = {
                                    expandedAccount = if (expandedAccount == account) null else account
                                    accountAction = null
                                },
                                onAction = { accountAction = it }
                            )
                        }
                        if (vm.accountLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        vm.accountError?.let { message ->
                            item {
                                Text(message, color = TextSoft)
                                TextButton(onClick = { vm.loadAccounts() }) { Text("Reintentar") }
                            }
                        }
                    } else if (selectedSection == "Inventario") {
                        item { InventorySummaryPanel(total = vm.inventory.size, suggested = vm.shoppingList.size) }
                        item {
                            InventorySearchBar(
                                search = vm.inventorySearch,
                                loading = vm.downloadingHistory,
                                onSearch = vm::updateInventorySearch,
                                onRefresh = vm::loadInventory
                            )
                        }
                        if (vm.filteredInventory.isEmpty()) {
                            item { EmptyState() }
                        } else {
                            items(
                                items = vm.filteredInventory,
                                key = { it.id.ifBlank { it.name } },
                                contentType = { "inventory-row" }
                            ) { item ->
                                InventoryCard(item)
                            }
                        }
                    } else {
                        item {
                            SummaryPanel(
                                currentMonth = vm.currentMonthTotal,
                                previousMonth = vm.previousMonthTotal
                            )
                        }
                        item {
                            if (vm.isEditing) InvoiceForm(
                                form = vm.form,
                                isEditing = vm.isEditing,
                                saving = vm.saving,
                                cashTotal = vm.cashTotal,
                                supplierMatches = vm.supplierMatches,
                                error = vm.error,
                                onChange = vm::updateForm,
                                onSupplierQuery = vm::updateSupplierQuery,
                                onSupplierSelect = vm::selectSupplier,
                                onSupplierCreate = vm::createSupplier,
                                onSave = { vm.saveInvoice() },
                                onClear = vm::clearForm
                            ) else CompactInvoiceForm(vm, null, onCatalog = { catalog = it })
                        }
                        item {
                            InvoiceSearchBar(
                                search = vm.search,
                                loading = vm.downloadingHistory,
                                suggestions = vm.searchSuggestions,
                                onSearch = vm::updateSearch,
                                onRefresh = vm::load
                            )
                        }
                        if (vm.filteredInvoices.isEmpty()) {
                            item { EmptyState() }
                        } else {
                            items(
                                items = vm.filteredInvoices,
                                contentType = { "invoice-row" }
                            ) { invoice ->
                                InvoiceCard(
                                    invoice = invoice,
                                    onOpen = { detailInvoice = invoice }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedSection == "Cuentas" && accountAction == "Detalles") {
            expandedAccount?.let { account ->
                AccountHistoryDialog(vm, account) { accountAction = null }
            }
        }

        if (selectedSection == "Cuentas" && accountAction == "Factura") {
            expandedAccount?.let { account ->
                AccountInvoiceDialog(vm, account, onCatalog = { catalog = it }, onClose = { accountAction = null }) {
                    accountAction = "Detalles"
                }
            }
        }

        if (selectedSection == "Cuentas" && movementType == null && accountAction in listOf("Salida", "Ingresos")) {
            val isIncome = accountAction == "Ingresos"
            AlertDialog(
                onDismissRequest = { accountAction = null },
                title = { Text("${accountAction.orEmpty()} · ${expandedAccount.orEmpty()}") },
                containerColor = Panel,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = if (isIncome) listOf("Venta", "Devolución")
                        else listOf("Transferencia entre cuentas", "Registro de factura")
                        options.forEachIndexed { index, option ->
                            AccountActionButton(
                                label = option,
                                icon = if (index == 0 && !isIncome) Icons.AutoMirrored.Filled.ExitToApp
                                    else if (index == 0) Icons.Default.AddCircle else Icons.AutoMirrored.Filled.List,
                                selected = false,
                                minHeight = 76.dp,
                                onClick = {
                                    vm.clearError()
                                    if (option == "Registro de factura") {
                                        vm.beginAccountInvoice(expandedAccount.orEmpty())
                                        accountAction = "Factura"
                                    } else if (option == "Venta") {
                                        saleAccount = expandedAccount
                                        accountAction = null
                                    } else movementType = option
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { accountAction = null }) { Text("Cerrar") }
                }
            )
        }

        saleAccount?.let { account ->
            SaleScreen(vm, account, onClose = { saleAccount = null }, onAuthorize = onAuthorize)
        }

        movementType?.takeIf { it == "Devolución" || it == "Transferencia entre cuentas" }?.let { type ->
            expandedAccount?.let { account ->
                MovementDialog(vm, account, type, onDismiss = {
                    movementType = null
                    accountAction = null
                }, onSaved = {
                    movementType = null
                    accountAction = "Detalles"
                })
            }
        }

        catalog?.let { entity -> CatalogDialog(vm, entity) { catalog = null } }

        detailInvoice?.let { invoice ->
            InvoiceDetailDialog(
                invoice = invoice,
                onDismiss = { detailInvoice = null },
                onEdit = {
                    vm.editInvoice(invoice)
                    selectedSection = "Facturas"
                    detailInvoice = null
                },
                onDelete = { deleteInvoice = invoice }
            )
        }

        deleteInvoice?.let { invoice ->
            AlertDialog(
                onDismissRequest = { deleteInvoice = null },
                title = { Text("Eliminar factura") },
                text = { Text("Estas seguro que quieres eliminar la factura de ${invoice.supplier}?") },
                confirmButton = {
                    Button(onClick = {
                        vm.deleteInvoice(invoice.id)
                        deleteInvoice = null
                        detailInvoice = null
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { deleteInvoice = null }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun AccountHistoryDialog(vm: MobileViewModel, account: String, onClose: () -> Unit) {
    val today = remember { Calendar.getInstance(accountTimeZone) }
    val currentYear = today.get(Calendar.YEAR)
    var year by rememberSaveable(account) { mutableStateOf(currentYear.toString()) }
    val months = remember {
        val locale = Locale("es", "NI")
        DateFormatSymbols.getInstance(locale).months.take(12).map { name ->
            name.replaceFirstChar { it.titlecase(locale) }
        }
    }
    var month by rememberSaveable(account) { mutableStateOf(months[today.get(Calendar.MONTH)]) }
    var queriedYear by rememberSaveable(account) { mutableStateOf(currentYear) }
    var queriedMonth by rememberSaveable(account) { mutableStateOf(today.get(Calendar.MONTH) + 1) }
    var recentLimit by rememberSaveable(account, queriedYear, queriedMonth) { mutableStateOf(10) }
    val monthDays = remember(queriedYear, queriedMonth) {
        Calendar.getInstance(accountTimeZone).apply { set(queriedYear, queriedMonth - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val movements = vm.accountMovements
    val recent = remember(account, movements) { if (account == "Caja") summarizeDailySales(movements) else movements }
    val validYear = year.toIntOrNull()?.takeIf { it in 1900..9999 }
    LaunchedEffect(account, queriedYear, queriedMonth) {
        vm.loadAccounts(account, queriedMonth, queriedYear)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg, contentColor = TextMain) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Movimientos · $account", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onClose) { Text("Volver") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField(month, "Mes", months, { month = it }, Modifier.weight(1f), readOnly = true)
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it.filter(Char::isDigit).take(4) },
                            label = { Text("Año") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(enabled = validYear != null && !vm.accountLoading, onClick = {
                        if (validYear != null) {
                            val selectedMonth = months.indexOf(month) + 1
                            if (queriedMonth == selectedMonth && queriedYear == validYear) {
                                vm.loadAccounts(account, queriedMonth, queriedYear)
                            } else {
                                queriedMonth = selectedMonth
                                queriedYear = validYear
                            }
                        }
                    }) {
                        Text("Consultar")
                    }
                }
                if (vm.accountLoading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                vm.accountError?.let { message -> item { Text(message, color = TextSoft) } }
                if (!vm.accountLoading && vm.accountError == null && movements.isEmpty()) {
                    item { Text("Sin movimientos en ${months[queriedMonth - 1]} de $queriedYear", color = TextSoft) }
                }
                if (movements.isNotEmpty() && vm.accountError == null) {
                    item { AccountChangeChart(movements, monthDays) }
                    item { Text("Movimientos recientes", style = MaterialTheme.typography.titleMedium) }
                    items(recent.take(recentLimit)) { movement -> AccountMovementCard(movement) }
                    if (recent.size > recentLimit) {
                        item { TextButton(onClick = { recentLimit += 10 }) { Text("Ver más movimientos") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChangeChart(movements: List<AccountMovement>, days: Int) {
    val active = remember(movements) { movements.filter { it.isActive } }
    val hasDollars = active.any { it.currency.equals("USD", true) }
    var selected by rememberSaveable { mutableStateOf("C$") }
    val currency = if (hasDollars) selected else "C$"
    val values = remember(active, days, currency) {
        DoubleArray(days).apply {
            active.filter { it.currency.equals("USD", true) == (currency == "USD") }.forEach { movement ->
                val day = movement.day
                if (day != null && day in 1..days) this[day - 1] += if (movement.credit) movement.amount else -movement.amount
            }
        }
    }
    val scale = values.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
    val total = values.sum()
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cambio neto por día", style = MaterialTheme.typography.titleMedium, color = TextMain)
            Text("Neto del mes · ${cashLabel(currency, total)}", color = TextSoft)
            if (hasDollars) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("C$", "USD").forEach { unit ->
                        FilterChip(selected = currency == unit, onClick = { selected = unit }, label = { Text(unit) })
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(116.dp).semantics {
                contentDescription = "Cambios diarios en $currency. Verde: subió. Naranja: bajó."
            }) {
                val zero = size.height / 2
                val slot = size.width / days
                drawLine(TextSoft.copy(alpha = 0.5f), Offset(0f, zero), Offset(size.width, zero))
                values.forEachIndexed { index, value ->
                    if (value == 0.0) return@forEachIndexed
                    val height = (kotlin.math.abs(value) / scale * (zero - 2.dp.toPx())).toFloat().coerceAtLeast(1f)
                    drawRect(
                        color = if (value > 0) Ok else Brand,
                        topLeft = Offset(slot * (index + 0.2f), if (value > 0) zero - height else zero),
                        size = Size(slot * 0.6f, height)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Día 1", color = TextSoft, style = MaterialTheme.typography.labelSmall)
                Text("Día $days", color = TextSoft, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val AccountMovement.isActive: Boolean
    get() = status.isBlank() || status.equals("ACTIVA", true)

private fun summarizeDailySales(movements: List<AccountMovement>): List<AccountMovement> {
    val sales = movements.filter { it.credit && it.type.equals("VENTA", true) && it.day != null }
        .groupBy { it.day }
    val shown = mutableSetOf<Int>()
    return movements.mapNotNull { movement ->
        val day = movement.day
        if (day == null || !movement.credit || !movement.type.equals("VENTA", true)) return@mapNotNull movement
        if (!shown.add(day)) return@mapNotNull null
        val active = sales.getValue(day).filter { it.isActive }
        if (active.isEmpty()) return@mapNotNull null
        val total = active.sumOf { it.amountCordobas }
        movement.copy(
            date = movement.date.substringBefore(' '), type = "Ventas del día", amount = total,
            amountCordobas = total, currency = "C$", reference = "", status = "ACTIVA",
            note = if (active.size == 1) "1 venta" else "${active.size} ventas"
        )
    }
}

@Composable
private fun AccountMovementCard(movement: AccountMovement) {
    val sign = if (movement.credit) "+" else "−"
    val amount = if (movement.currency.equals("USD", true)) "USD %.2f".format(Locale.US, movement.amount)
        else money(movement.amount)
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(movement.type.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                Text(movement.date, color = TextSoft, style = MaterialTheme.typography.bodySmall)
                if (movement.note.isNotBlank()) Text(movement.note)
                if (movement.reference.isNotBlank()) Text(movement.reference, color = TextSoft)
                if (movement.status.isNotBlank() && !movement.status.equals("ACTIVA", true)) {
                    Text(movement.status, color = TextSoft)
                }
            }
            Text("$sign$amount", color = if (movement.credit) Ok else Brand, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MovementDialog(
    vm: MobileViewModel,
    account: String,
    type: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val transfer = type == "Transferencia entre cuentas"
    val kind = if (transfer) "TRANSFERENCIA" else "DEVOLUCION"
    var amount by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("C$") }
    var note by rememberSaveable { mutableStateOf("") }
    var cash by remember { mutableStateOf(InvoiceFormState()) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val cashRequired = account == "Caja" || destination == "Caja"
    val denominations = cash.cashDetail.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }
    val cashTotal = denominations.entries.sumOf { (value, count) -> value.toDouble() * count }
    val parsedAmount = amount.replace(',', '.').toDoubleOrNull()
    val canSave = parsedAmount != null && parsedAmount.isFinite() && parsedAmount > 0 &&
        (!transfer || destination.isNotBlank()) &&
        (!cashRequired || kotlin.math.abs(cashTotal - parsedAmount) < 0.005)
    AlertDialog(
        onDismissRequest = { if (!vm.saving) onDismiss() },
        title = { Text("$type · $account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transfer) {
                    Text("Cuenta destino", style = MaterialTheme.typography.labelLarge)
                    Accounts.filterNot { it == account }.forEach { target ->
                        AccountActionButton(
                            label = target,
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            selected = destination == target,
                            onClick = {
                                if (!vm.saving && target != destination) {
                                    destination = target
                                    if (target == "Banco" && currency == "USD") {
                                        currency = "C$"
                                        amount = ""
                                    }
                                    cash = InvoiceFormState(currency = currency)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (transfer && account != "Banco" && destination != "Banco") {
                    SelectField(currency, "Moneda", listOf("C$", "USD"), { unit ->
                        if (unit != currency) {
                            currency = unit
                            amount = ""
                            cash = InvoiceFormState(currency = unit)
                        }
                    }, readOnly = true)
                }
                MoneyField(amount, "Monto en $currency", { amount = it }, Modifier.fillMaxWidth())
                Field(note, "Referencia (opcional)", { note = it }, Modifier.fillMaxWidth())
                if (cashRequired) CashDenominationPanel(cash, cashTotal) { cash = it }
                vm.error?.let { Text(it, color = Brand) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !vm.saving,
                onClick = {
                    parsedAmount?.let { vm.recordMovement(account, kind, destination, it, currency, note, denominations, requestId, onSaved) }
                }
            ) { Text(if (vm.saving) "Guardando…" else "Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.saving) { Text("Cancelar") } }
    )
}

@Composable
private fun AccountInvoiceDialog(
    vm: MobileViewModel,
    account: String,
    onCatalog: (String) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    Dialog(
        onDismissRequest = { if (!vm.saving) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(22.dp), color = Panel, contentColor = TextMain,
            border = BorderStroke(1.5.dp, Brand.copy(alpha = 0.85f))
        ) {
            Column {
                Text(
                    "Registrar factura · $account", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 4.dp)
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    CompactInvoiceForm(vm, account, onCatalog, showSaveButton = false)
                }
                HorizontalDivider(color = Brand.copy(alpha = 0.35f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose, enabled = !vm.saving) { Text("Cancelar") }
                    CompactInvoiceSaveButton(vm, onSaved)
                }
            }
        }
    }
}

@Composable
private fun CompactInvoiceForm(
    vm: MobileViewModel,
    account: String?,
    onCatalog: (String) -> Unit,
    showSaveButton: Boolean = true,
    onSaved: () -> Unit = {}
) {
    val form = vm.form
    val concepts = vm.catalogEntries["conceptos"].orEmpty().filter { it.active }.map { it.name }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (account == null) {
                Text("Registrar factura", style = MaterialTheme.typography.titleMedium)
                SelectField(form.sourceFunds, "Cuenta de salida", Accounts, vm::selectInvoiceAccount, readOnly = true)
            }
            Field(form.supplier, "Proveedor", vm::updateSupplierQuery, Modifier.fillMaxWidth())
            vm.supplierMatches.takeIf { form.supplier.isNotBlank() }.orEmpty().forEach { supplier ->
                if (supplier.name != form.supplier) {
                    TextButton(onClick = { vm.selectSupplier(supplier) }) { Text(supplier.name) }
                }
            }
            TextButton(onClick = { onCatalog("proveedores") }, enabled = !vm.saving) { Text("Gestionar proveedores") }
            Field(form.number, "Número de factura (opcional)", { vm.updateForm(form.copy(number = it)) }, Modifier.fillMaxWidth())
            if (form.sourceFunds != "Banco") {
                SelectField(form.currency, "Moneda", listOf("C$", "USD"), { unit ->
                    if (unit != form.currency) vm.updateForm(form.copy(
                        currency = unit, amount = "", cashDetail = emptyMap(), egresoCordobas = ""
                    ))
                }, readOnly = true)
            }
            MoneyField(form.amount, "Monto en ${form.currency}", { vm.updateForm(form.copy(amount = it.replace(',', '.'))) }, Modifier.fillMaxWidth())
            SelectField(form.concept, "Concepto", concepts, { vm.updateForm(form.copy(concept = it)) }, readOnly = true)
            TextButton(onClick = { onCatalog("conceptos") }, enabled = !vm.saving) { Text("Gestionar conceptos") }
            Field(form.notes, "Referencia o comentario (opcional)", { vm.updateForm(form.copy(notes = it)) }, Modifier.fillMaxWidth())
            if (form.sourceFunds == "Caja") CashDenominationPanel(form, vm.cashTotal, vm::updateForm)
            vm.error?.let { Text(it, color = Brand) }
            if (showSaveButton) CompactInvoiceSaveButton(vm, onSaved)
        }
    }
}

@Composable
private fun CompactInvoiceSaveButton(vm: MobileViewModel, onSaved: () -> Unit) {
    val validConcept = vm.catalogEntries["conceptos"].orEmpty().any { it.active && it.name == vm.form.concept }
    Button(enabled = !vm.saving && validConcept, onClick = { vm.saveInvoice(compact = true, onSaved = onSaved) }) {
        Text(if (vm.saving) "Guardando…" else "Guardar factura")
    }
}

@Composable
private fun CatalogDialog(vm: MobileViewModel, entity: String, onDismiss: () -> Unit) {
    var entry by remember { mutableStateOf<Supplier?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(entity) { vm.loadCatalog(entity) { loaded = true } }
    val error = vm.catalogErrors[entity]
    val entries = vm.catalogEntries[entity].orEmpty()
    val selected = entry
    val label = if (entity == "proveedores") "Proveedores" else "Conceptos"
    AlertDialog(
        onDismissRequest = { if (!vm.saving) onDismiss() },
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loaded) {
                    entries.forEach { item ->
                        OutlinedButton(
                            onClick = { entry = item; name = item.name },
                            enabled = !vm.saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (item.active) item.name else "${item.name} (inactivo)") }
                    }
                    Field(name, if (selected == null) "Nuevo nombre" else "Editar nombre", { name = it }, Modifier.fillMaxWidth())
                    if (selected != null) {
                        TextButton(onClick = { entry = null; name = "" }, enabled = !vm.saving) { Text("Nuevo registro") }
                        OutlinedButton(
                            onClick = {
                                vm.saveCatalogEntry(entity, selected, selected.name, !selected.active) {
                                    entry = null
                                    name = ""
                                }
                            },
                            enabled = !vm.saving
                        ) { Text(if (selected.active) "Desactivar" else "Reactivar") }
                    }
                } else if (error == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = Brand) }
            }
        },
        confirmButton = {
            Button(enabled = loaded && !vm.saving && name.isNotBlank(), onClick = {
                vm.saveCatalogEntry(entity, selected, name, selected?.active ?: true) {
                    entry = null
                    name = ""
                }
            }) { Text(if (selected == null) "Crear" else "Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.saving) { Text("Cerrar") } }
    )
}

@Composable
private fun AccountCard(
    account: String,
    balance: AccountBalance?,
    expanded: Boolean,
    selectedAction: String?,
    onToggle: () -> Unit,
    onAction: (String) -> Unit
) {
    val window = LocalConfiguration.current
    val wide = window.screenWidthDp >= 600
    val heightFraction = if (wide) 0.22f else 0.185f
    val minHeaderHeight = (window.screenHeightDp.dp * heightFraction)
        .coerceIn(if (wide) 136.dp else 104.dp, 240.dp)
    val horizontalPadding = if (wide) 28.dp else 16.dp
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val headerColor = when {
        pressed -> lerp(Panel, Brand, 0.36f)
        hovered || focused -> lerp(Panel, Brand, 0.20f)
        expanded -> lerp(Panel, Brand, 0.12f)
        else -> Panel
    }
    val accountIcon = when (account) {
        "Banco" -> R.drawable.ic_account_bank
        "Caja chica" -> R.drawable.ic_account_wallet
        else -> R.drawable.ic_account_register
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Brand.copy(alpha = if (expanded || hovered || focused || pressed) 1f else 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeaderHeight)
                .background(Brush.horizontalGradient(listOf(lerp(headerColor, Brand, 0.07f), headerColor)))
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Ocultar opciones" else "Mostrar opciones",
                    onClick = onToggle
                )
                .semantics { stateDescription = if (expanded) "Expandida" else "Contraída" }
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(if (wide) 72.dp else 54.dp)
                    .background(lerp(Panel, Brand, 0.19f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(accountIcon), contentDescription = null,
                    modifier = Modifier.size(if (wide) 38.dp else 28.dp), tint = Brand
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account, color = TextSoft, style = MaterialTheme.typography.titleMedium)
                Text(
                    balance?.let { money(it.cordobas) } ?: "Saldo no disponible",
                    color = if (balance == null) TextSoft else TextMain,
                    style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (account != "Banco" && balance != null && balance.dollars != 0.0) {
                    Text(
                        "USD %,.2f".format(Locale.US, balance.dollars),
                        color = TextSoft, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(if (wide) 32.dp else 24.dp).rotate(if (expanded) 180f else 0f),
                tint = Brand
            )
        }
        if (expanded) {
            HorizontalDivider(color = Brand.copy(alpha = 0.30f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(if (wide) 16.dp else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val actions = buildList {
                    add("Detalles" to Icons.AutoMirrored.Filled.List)
                    if (account != "Banco") add("Ingresos" to Icons.Default.AddCircle)
                    add("Salida" to Icons.AutoMirrored.Filled.ExitToApp)
                }
                actions.forEach { (label, icon) ->
                    AccountActionButton(
                        label = label,
                        icon = icon,
                        selected = selectedAction == label,
                        onClick = { onAction(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountActionButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val filled = pressed || selected
    val background = when {
        pressed -> Brand2
        selected -> Brand
        hovered || focused -> lerp(Panel2, Brand, 0.25f)
        else -> Panel2
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, Brand.copy(alpha = if (filled || hovered || focused) 1f else 0.80f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = if (filled) Bg else TextMain
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (filled) Bg else Brand)
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AccountsDesignPreview(snapshot: AccountSnapshot?, onBack: () -> Unit) {
    var expandedAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAction by rememberSaveable { mutableStateOf<String?>(null) }
    val balances = snapshot?.balances ?: remember {
        mapOf(
            "Banco" to AccountBalance(24500.0, 0.0),
            "Caja chica" to AccountBalance(1350.0, 0.0),
            "Caja" to AccountBalance(4260.0, 20.0)
        )
    }
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Volver al inicio") }
            Text("Cuentas", style = MaterialTheme.typography.titleLarge, color = TextMain)
            Text(snapshot?.let { "Saldos reales · Lectura del ${it.capturedAt}" }
                ?: "Vista de prueba · Saldos de ejemplo", color = Brand)
        }
        items(Accounts, key = { it }) { account ->
            AccountCard(
                account = account,
                balance = balances.getValue(account),
                expanded = expandedAccount == account,
                selectedAction = selectedAction.takeIf { expandedAccount == account },
                onToggle = {
                    expandedAccount = if (expandedAccount == account) null else account
                    selectedAction = null
                },
                onAction = { selectedAction = it }
            )
        }
        if (selectedAction != null) {
            item {
                val message = if (snapshot != null) "Vista de saldos de solo lectura."
                    else "Conecta tu cuenta para usar esta opción."
                Text("$selectedAction · $expandedAccount. $message", color = TextSoft)
            }
        }
    }
}

@Composable
private fun LoginScreen(
    vm: MobileViewModel,
    onAuthorize: () -> Unit,
    previewLabel: String,
    onPreview: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Grano y Alma", color = TextMain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Usa la cuenta Google de un operador activo.", color = TextSoft)
                if (!vm.apiReady) Text("Falta configurar el despliegue y los permisos de la API.", color = TextSoft)
                vm.authorizationError?.let { Text(it, color = Brand) }
                Button(onClick = onAuthorize, enabled = vm.apiReady && !vm.authorizing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.authorizing) "Verificando…" else "Continuar con Google")
                }
                if (onPreview != null) {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                        Text(previewLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(selected: String, onAuthorize: () -> Unit, onSelect: (String) -> Unit) {
    val sections = listOf(
        "Cuentas",
        "Facturacion",
        "Inventario",
        "Dashboard",
        "Caja",
        "Recursos Humanos",
        "Facturas",
        "Configuraciones",
        "Usuarios",
        "Menu",
        "Credito",
        "Recetas"
    )

    ModalDrawerSheet(
        drawerContainerColor = Panel,
        drawerContentColor = TextMain
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Grano y Alma", color = TextMain, fontWeight = FontWeight.Bold)
            Text("POS Gas Mobile", color = Brand, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onAuthorize) { Text("Volver a autorizar Google") }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Panel3)
            sections.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item) },
                    selected = item == selected,
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun InvoiceSearchBar(
    search: String,
    loading: Boolean,
    suggestions: List<String>,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        onSearch(it)
                        expanded = true
                    },
                    placeholder = { Text("Buscar proveedor o fecha") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "..." else "↻")
                }
            }
            DropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.background(Panel3)
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion, color = TextMain) },
                        onClick = {
                            onSearch(suggestion)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InventorySearchBar(
    search: String,
    loading: Boolean,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Buscar insumo, marca o codigo") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "..." else "â†»")
            }
        }
    }
}

@Composable
private fun SummaryPanel(currentMonth: Double, previousMonth: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Total mes actual", money(currentMonth), Modifier.weight(1f))
        MetricCard("Mes anterior", money(previousMonth), Modifier.weight(1f))
    }
}

@Composable
private fun InventorySummaryPanel(total: Int, suggested: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Insumos", total.toString(), Modifier.weight(1f))
        MetricCard("Por comprar", suggested.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .fillMaxWidth()
                    .background(Brand, RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSoft)
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextMain, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceForm(
    form: InvoiceFormState,
    isEditing: Boolean,
    saving: Boolean,
    cashTotal: Double,
    supplierMatches: List<Supplier>,
    error: String?,
    onChange: (InvoiceFormState) -> Unit,
    onSupplierQuery: (String) -> Unit,
    onSupplierSelect: (Supplier) -> Unit,
    onSupplierCreate: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isEditing) "Editar factura" else "Nueva factura",
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.number, "Numero", { onChange(form.copy(number = it)) }, Modifier.weight(1f))
                Field(form.date, "Fecha", { onChange(form.copy(date = it)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.establishment, "Establecimiento", { onChange(form.copy(establishment = it)) }, Modifier.weight(1f))
                SupplierField(
                    value = form.supplier,
                    matches = supplierMatches,
                    onValueChange = onSupplierQuery,
                    onSelect = onSupplierSelect,
                    onCreate = onSupplierCreate,
                    modifier = Modifier.weight(1f)
                )
            }
            SelectField(
                value = form.concept,
                label = "Concepto",
                options = listOf("Insumos", "Inversion inicial", "Equipos", "Mantenimiento", "Servicios publicos", "Renta", "Marketing", "Transporte", "Honorarios", "Impuestos", "Otros"),
                onValueChange = { onChange(form.copy(concept = it)) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(
                    form.amount,
                    "Monto total",
                    { raw ->
                        val amount = raw.filter { it.isDigit() || it == '.' }
                        onChange(form.copy(amount = amount, egresoCordobas = estimatedEgreso(amount, form.currency, form.rate)))
                    },
                    Modifier.weight(1f)
                )
                SelectField(
                    value = form.currency,
                    label = "Moneda",
                    options = listOf("C$", "USD"),
                    onValueChange = { currency ->
                        onChange(form.copy(currency = currency, egresoCordobas = estimatedEgreso(form.amount, currency, form.rate)))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectField(
                    value = form.payer,
                    label = "Pagador",
                    options = listOf("Ruth", "Luis", "Iris", "Grano y alma"),
                    onValueChange = { onChange(form.copy(payer = it)) },
                    modifier = Modifier.weight(1f)
                )
                SelectField(
                    value = form.paymentMethod,
                    label = "Medio de pago",
                    options = listOf("Efectivo", "Transferencia", "Tarjeta", "Banco", "Caja"),
                    onValueChange = {
                        val defaultSource = fundingSourceForPaymentMethod(it)
                        onChange(
                            form.copy(
                                paymentMethod = it,
                                sourceFunds = form.sourceFunds.ifBlank { defaultSource }
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectField(
                    value = form.sourceFunds,
                    label = "Se paga desde",
                    options = listOf("Caja", "Banco", "Caja chica"),
                    onValueChange = {
                        val source = normalizeFundingSource(it)
                        onChange(
                            form.copy(
                                sourceFunds = source,
                                cashDetail = if (source == "Caja") form.cashDetail else emptyMap()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                MoneyField(
                    value = form.rate,
                    label = "Tasa USD",
                    onValueChange = { raw ->
                        val rate = raw.filter { it.isDigit() || it == '.' }
                        val amount = form.amount.toDoubleOrNull() ?: 0.0
                        val egreso = if (form.currency.equals("USD", true)) {
                            ((rate.toDoubleOrNull() ?: 36.15) * amount).formatUi()
                        } else {
                            amount.formatUi()
                        }
                        onChange(form.copy(rate = rate, egresoCordobas = egreso))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            MoneyField(
                value = form.egresoCordobas,
                label = "Egreso C$",
                onValueChange = { onChange(form.copy(egresoCordobas = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Field(
                form.referenceFund,
                "Referencia fondo",
                { onChange(form.copy(referenceFund = it)) },
                Modifier.fillMaxWidth()
            )
            SelectField(
                value = form.status,
                label = "Estado",
                options = listOf("Activa", "Anulada"),
                onValueChange = { onChange(form.copy(status = it)) }
            )
            if (form.usesCash()) {
                CashDenominationPanel(
                    form = form,
                    cashTotal = cashTotal,
                    onChange = onChange
                )
            }
            Field(form.notes, "Notas", { onChange(form.copy(notes = it)) }, Modifier.fillMaxWidth(), singleLine = false)
            if (error != null) Text(error, color = Color(0xFFFF8A65))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = !saving) {
                    Text(
                        when {
                            saving -> "Guardando..."
                            isEditing -> "Guardar cambios"
                            else -> "Crear factura"
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onClear) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun SupplierField(
    value: String,
    matches: List<Supplier>,
    onValueChange: (String) -> Unit,
    onSelect: (Supplier) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = true
                },
                label = { Text("Proveedor") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    onCreate()
                    expanded = false
                },
                enabled = value.isNotBlank()
            ) { Text("+") }
        }
        DropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .background(Panel3)
                .fillMaxWidth()
        ) {
            matches.forEach { supplier ->
                DropdownMenuItem(
                    text = { Text(supplier.name, color = TextMain) },
                    onClick = {
                        onSelect(supplier)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CashDenominationPanel(
    form: InvoiceFormState,
    cashTotal: Double,
    onChange: (InvoiceFormState) -> Unit
) {
    val denominations = if (form.currency.equals("USD", ignoreCase = true)) {
        listOf(100, 50, 20, 10, 5, 1)
    } else {
        listOf(1000, 500, 200, 100, 50, 20, 10, 5, 1)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Detalle de billetes", color = TextMain, fontWeight = FontWeight.SemiBold)
                Text("Total: ${cashLabel(form.currency, cashTotal)}", color = Brand, fontWeight = FontWeight.Bold)
            }
            denominations.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { denomination ->
                        OutlinedTextField(
                            value = form.cashDetail[denomination].orEmpty(),
                            onValueChange = { raw ->
                                val cleaned = raw.filter { it.isDigit() }
                                onChange(form.copy(cashDetail = form.cashDetail + (denomination to cleaned)))
                            },
                            label = { Text("${form.currency} $denomination") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}

@Composable
private fun MoneyField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectField(
    value: String,
    label: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    readOnly: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun InvoiceCard(invoice: Invoice, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(invoice.supplier.ifBlank { "Sin proveedor" }, color = TextMain, fontWeight = FontWeight.SemiBold)
                Text(invoice.date, color = TextSoft, style = MaterialTheme.typography.bodySmall)
            }
            Text(invoice.displayAmount(), color = TextMain, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InventoryCard(item: InventoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.suggested) Panel3 else Panel2)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "Sin nombre" }, color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text(listOf(item.brand, item.unit).filter { it.isNotBlank() }.joinToString(" - "), color = TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                if (item.suggested) {
                    StatusBadge("Comprar", item.suggestedQty.formatStock())
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stock: ${item.stock.formatStock()}", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                Text("Min: ${item.minStock.formatStock()}", color = TextSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InvoiceDetailDialog(
    invoice: Invoice,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(invoice.supplier.ifBlank { "Detalle de factura" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailLine("Fecha", invoice.date)
                DetailLine("Monto", invoice.displayAmount())
                DetailLine("Numero", invoice.number)
                DetailLine("Establecimiento", invoice.establishment)
                DetailLine("Concepto", invoice.concept)
                DetailLine("Pagador", invoice.payer)
                DetailLine("Medio", invoice.paymentMethod)
                if (invoice.category.isNotBlank()) DetailLine("Categoría", invoice.category)
                DetailLine("Fuente", invoice.sourceFunds)
                DetailLine("Egreso C$", money(invoice.egresoCordobas))
                DetailLine("Referencia", invoice.referenceFund)
                if (invoice.notes.isNotBlank()) DetailLine("Comentario", invoice.notes)
                DetailLine("Registrado por", invoice.registeredBy)
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("Modificar") } },
        dismissButton = { OutlinedButton(onClick = onDelete) { Text("Eliminar") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Text("$label: $value", color = TextMain)
}

@Composable
private fun StatusBadge(status: String, currency: String) {
    val color = if (status.equals("Anulada", ignoreCase = true)) Err else Ok
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "$status - $currency",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Text("Sin resultados", modifier = Modifier.padding(16.dp), color = TextSoft)
    }
}

private fun Invoice.displayAmount(): String {
    val raw = if (currency.equals("USD", ignoreCase = true)) "$ %.2f".format(Locale.US, amount) else money(amount)
    val cordobas = if (currency.equals("USD", ignoreCase = true)) " (${money(amountInCordobas())})" else ""
    return raw + cordobas
}

private fun InvoiceFormState.usesCash(): Boolean {
    return normalizeFundingSource(sourceFunds.ifBlank { fundingSourceForPaymentMethod(paymentMethod) }) == "Caja"
}

private fun fundingSourceForPaymentMethod(paymentMethod: String): String {
    return when (paymentMethod.lowercase(Locale.US)) {
        "banco", "transferencia", "tarjeta" -> "Banco"
        "efectivo", "caja" -> "Caja"
        else -> ""
    }
}

private fun normalizeFundingSource(source: String): String {
    return when (source.lowercase(Locale.US)) {
        "caja casa", "caja chica" -> "Caja chica"
        "caja normal", "caja registradora" -> "Caja"
        else -> source
    }
}

private fun cashLabel(currency: String, value: Double): String {
    return if (currency.equals("USD", ignoreCase = true)) {
        "$ %.2f".format(Locale.US, value)
    } else {
        money(value)
    }
}

private fun estimatedEgreso(amountRaw: String, currency: String, rateRaw: String): String {
    val amount = amountRaw.toDoubleOrNull() ?: 0.0
    val rate = rateRaw.toDoubleOrNull() ?: 36.15
    val egreso = if (currency.equals("USD", true)) amount * rate else amount
    return egreso.formatUi()
}

private fun Double.formatUi(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else "%.2f".format(Locale.US, this)
}

private fun Double.formatStock(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else "%.2f".format(Locale.US, this)
}

private val cordobaFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "NI"))

internal fun money(value: Double): String = cordobaFormat.format(value)
