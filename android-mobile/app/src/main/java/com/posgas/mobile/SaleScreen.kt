package com.posgas.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.posgas.mobile.ui.MobileViewModel
import com.posgas.mobile.ui.SaleProduct
import com.posgas.mobile.ui.saleCashPayment
import com.posgas.mobile.ui.saleCents
import kotlinx.coroutines.delay
import java.math.BigDecimal

private val CordobaBills = listOf(1000, 500, 200, 100, 50, 20, 10, 5, 1)
private val DollarBills = listOf(20, 10, 5, 1)
private val Methods = linkedMapOf(
    "Efectivo" to "efectivo", "Tarjeta" to "tarjeta", "Transferencia" to "transferencia",
    "Crédito" to "credito", "Mixto" to "efectivo"
)

@Composable
internal fun SaleScreen(vm: MobileViewModel, account: String, onClose: () -> Unit, onAuthorize: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var cart by remember { mutableStateOf(emptyMap<SaleProduct, Int>()) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf("Efectivo") }
    var consumption by rememberSaveable { mutableStateOf("Local") }
    var client by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var split by remember { mutableStateOf(emptyMap<String, String>()) }
    var received by remember { mutableStateOf(emptyMap<String, Int>()) }
    var change by remember { mutableStateOf(emptyMap<String, Int>()) }
    var receivedUsd by remember { mutableStateOf(emptyMap<String, Int>()) }
    var changeUsd by remember { mutableStateOf(emptyMap<String, Int>()) }
    var showDollars by rememberSaveable { mutableStateOf(false) }
    val pending = vm.pendingSale != null
    val gross = saleCents(cart.entries.fold(BigDecimal.ZERO) { sum, (product, quantity) ->
        sum + BigDecimal.valueOf(product.price) * BigDecimal.valueOf(quantity.toLong())
    }.toPlainString())
    val discountCents = saleCents(discount.ifBlank { "0" })
    val total = if (gross != null && discountCents != null) gross - discountCents else 0L
    val splitAmounts = listOf("efectivo", "tarjeta", "transferencia").associateWith {
        saleCents(split[it].orEmpty().ifBlank { "0" })
    }
    val splitValid = splitAmounts.values.none { it == null } && splitAmounts.values.filterNotNull().sum() == total
    val cashAmount = when (method) {
        "Mixto" -> splitAmounts["efectivo"] ?: 0
        "Efectivo" -> total
        else -> 0
    }
    val cashRequired = account == "Caja" && cashAmount > 0
    val cashPayment = if (cashRequired) saleCashPayment(
        cashAmount, received, change, receivedUsd, changeUsd, vm.saleExchangeRate
    ) else null
    val canConfirm = account in listOf("Caja", "Caja chica") && cart.isNotEmpty() && total > 0 &&
        (method != "Mixto" || splitValid) && (!cashRequired || cashPayment != null) &&
        (method != "Crédito" || client.isNotBlank()) && (account == "Caja" || method == "Efectivo")

    LaunchedEffect(search, checkout, pending) {
        if (!checkout && !pending && account != "Pendiente") {
            delay(250)
            vm.searchSaleProducts(search)
        }
    }
    LaunchedEffect(pending) {
        if (pending) vm.checkSale()
        else if (account == "Pendiente") onClose()
    }

    Dialog(
        onDismissRequest = { if (!vm.saleBusy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (pending) "Venta pendiente" else "Venta · $account",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClose, enabled = !vm.saleBusy) { Text("Volver") }
                    }
                }
                if (pending) {
                    item { PendingSalePanel(vm, onAuthorize, onClose) }
                } else if (checkout) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Productos · ${cart.values.sumOf(Int::toLong)} unidades")
                            SaleText(discount, "Descuento en C$", { discount = it }, KeyboardType.Decimal)
                            SelectField(consumption, "Consumo", listOf("Local", "Llevar"), { consumption = it }, readOnly = true)
                            SelectField(
                                method, "Medio de pago", if (account == "Caja") Methods.keys.toList() else listOf("Efectivo"),
                                { method = it }, readOnly = true
                            )
                            if (method == "Crédito") SaleText(client, "ID del cliente", { client = it })
                            if (method == "Mixto") {
                                listOf("efectivo", "tarjeta", "transferencia").forEach { key ->
                                    SaleText(split[key].orEmpty(), "${key.replaceFirstChar(Char::uppercase)} en C$",
                                        { split = split + (key to it) }, KeyboardType.Decimal)
                                }
                            }
                            if (cashRequired) {
                                Text("Efectivo a registrar · ${money(cashAmount / 100.0)}")
                                BillFields("Recibido en C$", CordobaBills, received) { received = it }
                                BillFields("Cambio en C$", CordobaBills, change) { change = it }
                                if (showDollars) {
                                    val rate = vm.saleExchangeRate
                                    if (rate == null) {
                                        Text("Tasa de cambio no disponible.")
                                        TextButton(onClick = vm::loadSaleConfig) { Text("Consultar tasa") }
                                    } else Text("Tasa vigente · $rate C$ por USD")
                                    BillFields("Recibido en USD", DollarBills, receivedUsd) { receivedUsd = it }
                                    BillFields("Cambio en USD", DollarBills, changeUsd) { changeUsd = it }
                                }
                                TextButton(onClick = {
                                    showDollars = !showDollars
                                    if (showDollars) vm.loadSaleConfig() else {
                                        receivedUsd = emptyMap()
                                        changeUsd = emptyMap()
                                    }
                                }) { Text(if (showDollars) "Quitar dólares" else "Incluir dólares") }
                                val rounding = cashPayment?.get("redondeo_favor_negocio") as? Double ?: 0.0
                                if (rounding > 0) Text("Redondeo a favor del negocio · ${money(rounding)}")
                            }
                            Text("Total · ${money(total / 100.0)}", style = MaterialTheme.typography.titleLarge)
                            vm.saleError?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                            Button(onClick = { confirming = true }, enabled = canConfirm) { Text("Revisar cobro") }
                            TextButton(onClick = { checkout = false }) { Text("Editar productos") }
                        }
                    }
                } else {
                    item {
                        SaleText(search, "Buscar producto por nombre o código", { search = it })
                    }
                    items(cart.entries.toList(), key = { "cart-${it.key.id}" }) { (product, quantity) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name)
                                Text("${money(product.price)} por unidad", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = {
                                cart = if (quantity == 1) cart - product else cart + (product to quantity - 1)
                            }) { Text("−") }
                            Text(quantity.toString())
                            TextButton(onClick = { cart = cart + (product to quantity + 1) }, enabled = quantity < Int.MAX_VALUE) {
                                Text("+")
                            }
                        }
                    }
                    item {
                        Button(onClick = { checkout = true }, enabled = gross != null && gross > 0) {
                            Text("Cobrar · ${money((gross ?: 0) / 100.0)}")
                        }
                        HorizontalDivider()
                    }
                    items(vm.products, key = { "product-${it.id}" }) { product ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name)
                                Text(listOf(product.code, money(product.price)).filter { it.isNotBlank() }.joinToString(" · "))
                            }
                            val existing = cart.keys.firstOrNull { it.id == product.id }
                            val quantity = cart[existing] ?: 0
                            OutlinedButton(
                                enabled = product.available && (existing != null || cart.size < 100) && quantity < Int.MAX_VALUE,
                                onClick = {
                                    if (existing != null) cart = cart - existing
                                    cart = cart + (product to quantity + 1)
                                }
                            ) { Text(if (product.available) "Agregar" else "No disponible") }
                        }
                    }
                    item {
                        if (vm.productsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.productsError?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { vm.searchSaleProducts(search) }) { Text("Reintentar búsqueda") }
                        }
                        if (vm.productsMore && !vm.productsLoading) {
                            TextButton(onClick = { vm.searchSaleProducts(search, more = true) }) { Text("Ver más productos") }
                        }
                    }
                }
            }
        }
    }

    if (confirming && !pending && canConfirm) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Registrar cobro") },
            text = { Text("${money(total / 100.0)} · $method\nConfirma que el cobro ya se realizó.") },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    vm.submitSale(buildMap {
                        put("cuenta", account)
                        put("tipo", Methods.getValue(method))
                        put("total", total / 100.0)
                        put("descuento", (discountCents ?: 0) / 100.0)
                        put("consumo", if (consumption == "Local") "local" else "llevar")
                        put("items", cart.map { (product, quantity) ->
                            mapOf("id_producto" to product.id, "cantidad" to quantity, "precio_unitario" to product.price)
                        })
                        if (method == "Crédito") put("id_cliente", client.trim())
                        if (method == "Mixto") put("pago_parcial", splitAmounts.mapValues { (it.value ?: 0) / 100.0 })
                        if (cashPayment != null) put("pago", cashPayment)
                    })
                }) { Text("Confirmar registro") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Volver") } }
        )
    }
}

@Composable
private fun PendingSalePanel(vm: MobileViewModel, onAuthorize: () -> Unit, onClose: () -> Unit) {
    val result = vm.saleResult
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.saleBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (result?.state) {
            "COMPLETADA" -> Text("Venta registrada · Ticket ${result.ticket}", style = MaterialTheme.typography.titleMedium)
            "CANCELADA" -> Text("La solicitud se canceló en Facturación.")
            "REVISION" -> Text("La venta requiere revisión en Facturación antes de volver a cobrar.")
            "PROCESANDO" -> Text("Facturación está procesando la venta.")
            else -> Text("Consulta el registro guardado antes de volver a cobrar.")
        }
        result?.message?.takeIf { it.isNotBlank() }?.let { Text(it) }
        vm.saleError?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        vm.authorizationError?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (result?.state == "COMPLETADA" || result?.state == "CANCELADA") {
            Button(enabled = !vm.saleBusy, onClick = { vm.finishSale(); if (vm.pendingSale == null) onClose() }) { Text("Cerrar ticket") }
        } else {
            Button(enabled = !vm.saleBusy, onClick = vm::checkSale) { Text("Consultar estado") }
            if (result?.state in listOf("NO_ENCONTRADA", "ERROR")) {
                OutlinedButton(enabled = !vm.saleBusy, onClick = vm::retrySale) { Text("Reenviar el mismo registro") }
            }
            if (vm.saleCanEdit) TextButton(enabled = !vm.saleBusy, onClick = vm::finishSale) { Text("Editar venta rechazada") }
            TextButton(enabled = !vm.saleBusy, onClick = onAuthorize) { Text("Volver a autorizar Google") }
        }
    }
}

@Composable
private fun SaleText(value: String, label: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BillFields(label: String, values: List<Int>, bills: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    Text(label)
    values.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { value ->
                val key = value.toString()
                OutlinedTextField(
                    bills[key]?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val quantity = raw.filter(Char::isDigit).toIntOrNull() ?: 0
                        onChange(if (quantity == 0) bills - key else bills + (key to quantity))
                    },
                    label = { Text(key) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
