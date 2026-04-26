package com.posgas.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.posgas.mobile.data.ApiClient
import com.posgas.mobile.ui.Invoice
import com.posgas.mobile.ui.InvoiceFormState
import com.posgas.mobile.ui.MobileViewModel
import com.posgas.mobile.ui.amountInCordobas
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private const val MOBILE_API_URL = "https://script.google.com/macros/s/AKfycbx7N-TOr333K7GSrpbERZ-STzWUfRbYKpkXIT3ryJC5zqBOC-N5XVDJYpXmL0QaTOw/exec"

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm = ViewModelProvider(this, Factory(MOBILE_API_URL))[MobileViewModel::class.java]
        setContent { PosGasApp(vm) }
    }
}

class Factory(private val baseUrl: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MobileViewModel(ApiClient(baseUrl)) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosGasApp(vm: MobileViewModel) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Brand,
        secondary = Brand2,
        background = Bg,
        surface = Panel,
        onSurface = TextMain,
        onBackground = TextMain
    )

    MaterialTheme(colorScheme = colors) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    selected = "Facturas",
                    onSelect = { scope.launch { drawerState.close() } }
                )
            }
        ) {
            Scaffold(
                containerColor = Bg,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("Menu", color = Brand, fontWeight = FontWeight.Bold)
                            }
                        },
                        title = {
                            Column {
                                Text("Registro de Facturas", fontWeight = FontWeight.Bold)
                                Text(vm.syncMessage, style = MaterialTheme.typography.labelSmall, color = TextSoft)
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
                    item {
                        Toolbar(
                            search = vm.search,
                            loading = vm.downloadingHistory,
                            onSearch = vm::updateSearch,
                            onRefresh = vm::load
                        )
                    }
                    item {
                        SummaryPanel(
                            total = vm.totalCostCordobas,
                            count = vm.activeCount,
                            usdCount = vm.usdCount
                        )
                    }
                    item {
                        InvoiceForm(
                            form = vm.form,
                            isEditing = vm.isEditing,
                            saving = vm.saving,
                            cashTotal = vm.cashTotal,
                            error = vm.error,
                            onChange = vm::updateForm,
                            onSave = vm::saveInvoice,
                            onClear = vm::clearForm
                        )
                    }
                    item {
                        Text(
                            text = "Listado de Facturas",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMain,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (vm.filteredInvoices.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(vm.filteredInvoices, key = { it.id.ifBlank { it.number } }) { invoice ->
                            InvoiceCard(
                                invoice = invoice,
                                onEdit = { vm.editInvoice(invoice) },
                                onDelete = { vm.deleteInvoice(invoice.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(selected: String, onSelect: (String) -> Unit) {
    val sections = listOf(
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
private fun Toolbar(search: String, loading: Boolean, onSearch: (String) -> Unit, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "Cargando" else "Actualizar")
            }
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Buscar por concepto, pagador o categoria") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryPanel(total: Double, count: Int, usdCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Total C$", money(total), Modifier.weight(1f))
        MetricCard("Activas", count.toString(), Modifier.weight(1f))
        MetricCard("USD", usdCount.toString(), Modifier.weight(1f))
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
    error: String?,
    onChange: (InvoiceFormState) -> Unit,
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
                Field(form.supplier, "Proveedor", { onChange(form.copy(supplier = it)) }, Modifier.weight(1f))
            }
            SelectField(
                value = form.concept,
                label = "Concepto",
                options = listOf("Insumos", "Inversion inicial", "Equipos", "Mantenimiento", "Servicios publicos", "Renta", "Marketing", "Transporte", "Honorarios", "Impuestos", "Otros"),
                onValueChange = { onChange(form.copy(concept = it)) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(form.amount, "Monto total", { onChange(form.copy(amount = it)) }, Modifier.weight(1f))
                SelectField(
                    value = form.currency,
                    label = "Moneda",
                    options = listOf("C$", "USD"),
                    onValueChange = { onChange(form.copy(currency = it)) },
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
                    onValueChange = { onChange(form.copy(paymentMethod = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.category, "Categoria", { onChange(form.copy(category = it)) }, Modifier.weight(1f))
                SelectField(
                    value = form.status,
                    label = "Estado",
                    options = listOf("Activa", "Anulada"),
                    onValueChange = { onChange(form.copy(status = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
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
private fun SelectField(
    value: String,
    label: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
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
private fun InvoiceCard(invoice: Invoice, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.number, color = TextMain, fontWeight = FontWeight.Bold)
                    Text(invoice.supplier, color = TextSoft)
                }
                StatusBadge(invoice.status, invoice.currency)
            }
            Text(invoice.concept, color = TextMain)
            Text("${invoice.establishment} - ${invoice.date}", color = TextSoft)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${invoice.payer} - ${invoice.paymentMethod}", color = TextSoft)
                Text(invoice.displayAmount(), color = TextMain, fontWeight = FontWeight.Bold)
            }
            Text(invoice.category, color = Brand)
            Row {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFFF8A65)) }
            }
        }
    }
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
    val method = paymentMethod.lowercase(Locale.US)
    return method == "efectivo" || method == "caja"
}

private fun cashLabel(currency: String, value: Double): String {
    return if (currency.equals("USD", ignoreCase = true)) {
        "$ %.2f".format(Locale.US, value)
    } else {
        money(value)
    }
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("es", "NI")).format(value)
