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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.posgas.mobile.ui.Invoice
import com.posgas.mobile.ui.InvoiceFormState
import com.posgas.mobile.ui.InvoiceStatus
import com.posgas.mobile.ui.MobileViewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm = ViewModelProvider(this)[MobileViewModel::class.java]
        setContent { PosGasApp(vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosGasApp(vm: MobileViewModel) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Color(0xFF0D6B5D),
        secondary = Color(0xFFE46E2E),
        background = Color(0xFFF6F7F4),
        surface = Color.White,
        onSurface = Color(0xFF1C2421),
        onBackground = Color(0xFF1C2421)
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text("Control de Facturas") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.primary,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SummaryPanel(
                        total = vm.totalCost,
                        pending = vm.pendingCost,
                        paid = vm.paidCost
                    )
                }
                item {
                    InvoiceForm(
                        form = vm.form,
                        isEditing = vm.isEditing,
                        error = vm.error,
                        onChange = vm::updateForm,
                        onSave = vm::saveInvoice,
                        onClear = vm::clearForm
                    )
                }
                item {
                    Text(
                        text = "Facturas registradas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (vm.invoices.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(vm.invoices, key = { it.id }) { invoice ->
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

@Composable
private fun SummaryPanel(total: Double, pending: Double, paid: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard("Costo total", total, Modifier.weight(1f), Color(0xFF0D6B5D))
        MetricCard("Pendiente", pending, Modifier.weight(1f), Color(0xFFE46E2E))
        MetricCard("Pagado", paid, Modifier.weight(1f), Color(0xFF345995))
    }
}

@Composable
private fun MetricCard(label: String, value: Double, modifier: Modifier, accent: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .fillMaxWidth()
                    .background(accent, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF5E6964))
            Text(
                money(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceForm(
    form: InvoiceFormState,
    isEditing: Boolean,
    error: String?,
    onChange: (InvoiceFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (isEditing) "Editar factura" else "Nueva factura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.number,
                    onValueChange = { onChange(form.copy(number = it)) },
                    label = { Text("Numero") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.date,
                    onValueChange = { onChange(form.copy(date = it)) },
                    label = { Text("Fecha") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = form.supplier,
                onValueChange = { onChange(form.copy(supplier = it)) },
                label = { Text("Proveedor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.concept,
                onValueChange = { onChange(form.copy(concept = it)) },
                label = { Text("Concepto") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryField(
                    value = form.category,
                    onValueChange = { onChange(form.copy(category = it)) },
                    modifier = Modifier.weight(1f)
                )
                StatusField(
                    value = form.status,
                    onValueChange = { onChange(form.copy(status = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(
                    value = form.subtotal,
                    label = "Subtotal",
                    onValueChange = { onChange(form.copy(subtotal = it)) },
                    modifier = Modifier.weight(1f)
                )
                MoneyField(
                    value = form.tax,
                    label = "Impuesto",
                    onValueChange = { onChange(form.copy(tax = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave) {
                    Text(if (isEditing) "Guardar cambios" else "Crear factura")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onClear) { Text("Limpiar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    val categories = listOf("Combustible", "Mantenimiento", "Transporte", "Servicios", "General")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Categoria") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onValueChange(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusField(value: InvoiceStatus, onValueChange: (InvoiceStatus) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InvoiceStatus.values().forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label) },
                    onClick = {
                        onValueChange(status)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MoneyField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun InvoiceCard(invoice: Invoice, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.number, fontWeight = FontWeight.Bold)
                    Text(invoice.supplier, color = Color(0xFF5E6964))
                }
                StatusBadge(invoice.status)
            }
            Text(invoice.concept)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${invoice.category} - ${invoice.date}", color = Color(0xFF5E6964))
                Text(money(invoice.total), fontWeight = FontWeight.Bold)
            }
            Row {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InvoiceStatus) {
    val color = if (status == InvoiceStatus.Paid) Color(0xFF0D6B5D) else Color(0xFFE46E2E)

    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = status.label,
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = "Todavia no hay facturas registradas.",
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF5E6964)
        )
    }
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("es", "NI")).format(value)
