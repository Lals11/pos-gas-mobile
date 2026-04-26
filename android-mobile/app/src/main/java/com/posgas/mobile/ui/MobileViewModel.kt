package com.posgas.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val number: String,
    val supplier: String,
    val concept: String,
    val category: String,
    val date: String,
    val subtotal: Double,
    val tax: Double,
    val status: InvoiceStatus
) {
    val total: Double
        get() = subtotal + tax
}

enum class InvoiceStatus(val label: String) {
    Pending("Pendiente"),
    Paid("Pagada")
}

data class InvoiceFormState(
    val id: String? = null,
    val number: String = "",
    val supplier: String = "",
    val concept: String = "",
    val category: String = "Combustible",
    val date: String = today(),
    val subtotal: String = "",
    val tax: String = "",
    val status: InvoiceStatus = InvoiceStatus.Pending
)

class MobileViewModel : ViewModel() {
    var invoices by mutableStateOf(seedInvoices())
        private set

    var form by mutableStateOf(InvoiceFormState())
        private set

    var error by mutableStateOf<String?>(null)
        private set

    val isEditing: Boolean
        get() = form.id != null

    val totalCost: Double
        get() = invoices.sumOf { it.total }

    val pendingCost: Double
        get() = invoices.filter { it.status == InvoiceStatus.Pending }.sumOf { it.total }

    val paidCost: Double
        get() = invoices.filter { it.status == InvoiceStatus.Paid }.sumOf { it.total }

    fun updateForm(next: InvoiceFormState) {
        form = next
        error = null
    }

    fun saveInvoice() {
        val subtotal = form.subtotal.toDoubleOrNull()
        val tax = form.tax.ifBlank { "0" }.toDoubleOrNull()

        if (form.number.isBlank() || form.supplier.isBlank() || form.concept.isBlank()) {
            error = "Completa numero, proveedor y concepto."
            return
        }

        if (subtotal == null || tax == null) {
            error = "Subtotal e impuesto deben ser numeros validos."
            return
        }

        val invoice = Invoice(
            id = form.id ?: UUID.randomUUID().toString(),
            number = form.number.trim(),
            supplier = form.supplier.trim(),
            concept = form.concept.trim(),
            category = form.category.trim().ifBlank { "General" },
            date = form.date.trim().ifBlank { today() },
            subtotal = subtotal,
            tax = tax,
            status = form.status
        )

        invoices = if (form.id == null) {
            listOf(invoice) + invoices
        } else {
            invoices.map { current -> if (current.id == invoice.id) invoice else current }
        }
        clearForm()
    }

    fun editInvoice(invoice: Invoice) {
        form = InvoiceFormState(
            id = invoice.id,
            number = invoice.number,
            supplier = invoice.supplier,
            concept = invoice.concept,
            category = invoice.category,
            date = invoice.date,
            subtotal = invoice.subtotal.formatMoneyInput(),
            tax = invoice.tax.formatMoneyInput(),
            status = invoice.status
        )
        error = null
    }

    fun deleteInvoice(id: String) {
        invoices = invoices.filterNot { it.id == id }
        if (form.id == id) clearForm()
    }

    fun clearForm() {
        form = InvoiceFormState()
        error = null
    }
}

private fun seedInvoices(): List<Invoice> = listOf(
    Invoice(
        number = "FAC-001",
        supplier = "Gasolinera Central",
        concept = "Compra de combustible regular",
        category = "Combustible",
        date = today(),
        subtotal = 1840.0,
        tax = 276.0,
        status = InvoiceStatus.Pending
    ),
    Invoice(
        number = "FAC-002",
        supplier = "Taller Norte",
        concept = "Mantenimiento de bomba",
        category = "Mantenimiento",
        date = today(),
        subtotal = 620.0,
        tax = 93.0,
        status = InvoiceStatus.Paid
    )
)

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun Double.formatMoneyInput(): String {
    val asLong = toLong()
    return if (this == asLong.toDouble()) asLong.toString() else "%.2f".format(Locale.US, this)
}
