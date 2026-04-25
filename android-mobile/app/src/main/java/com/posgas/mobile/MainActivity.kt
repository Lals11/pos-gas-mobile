package com.posgas.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.posgas.mobile.data.ApiClient
import com.posgas.mobile.ui.MobileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Reemplazar con tu URL /exec de Apps Script
        val baseUrl = "https://script.google.com/macros/s/REEMPLAZA_CON_TU_DEPLOY_ID/exec"

        val vm = ViewModelProvider(this, Factory(baseUrl))[MobileViewModel::class.java]
        setContent { App(vm) }
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
private fun App(vm: MobileViewModel) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFFFF5500),
            secondary = Color(0xFFFF6A2A),
            background = Color(0xFF17181C),
            surface = Color(0xFF1E1F24),
            onSurface = Color(0xFFD3D3D3),
            onBackground = Color(0xFFD3D3D3)
        )
    ) {
        LaunchedEffect(vm.selectedTab) { vm.load() }

        Scaffold(
            topBar = { TopAppBar(title = { Text("POS Gas Mobile") }) }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(12.dp)
            ) {
                TabRow(selectedTabIndex = vm.selectedTab) {
                    listOf("Inventario", "Compras", "Facturas").forEachIndexed { idx, label ->
                        Tab(selected = vm.selectedTab == idx, onClick = { vm.selectedTab = idx }, text = { Text(label) })
                    }
                }

                CrudForm(
                    onCreate = vm::create,
                    onUpdate = vm::update,
                    onDelete = vm::delete
                )

                when {
                    vm.loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    vm.error != null -> Text("Error: ${vm.error}", color = Color.Red)
                    else -> DataList(vm.rows)
                }
            }
        }
    }
}

@Composable
private fun CrudForm(
    onCreate: (Map<String, Any?>) -> Unit,
    onUpdate: (String, Map<String, Any?>) -> Unit,
    onDelete: (String) -> Unit
) {
    val id = remember { mutableStateOf("") }
    val payload = remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CRUD rápido (JSON)")
            OutlinedTextField(value = id.value, onValueChange = { id.value = it }, label = { Text("ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = payload.value,
                onValueChange = { payload.value = it },
                label = { Text("JSON (ej. {\"nombre\":\"Arroz\"})") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCreate(parseJsonLike(payload.value)) }) { Text("Crear") }
                Button(onClick = { onUpdate(id.value, parseJsonLike(payload.value)) }) { Text("Actualizar") }
                Button(onClick = { onDelete(id.value) }) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun DataList(rows: List<Map<String, Any?>>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    item.entries.take(5).forEach {
                        Text("${it.key}: ${it.value}")
                    }
                }
            }
        }
    }
}

private fun parseJsonLike(raw: String): Map<String, Any?> {
    // parser simple para MVP: clave:valor separados por coma
    // ejemplo: nombre=Arroz,costos=120
    if (raw.trim().startsWith("{")) return emptyMap()
    return raw.split(",")
        .mapNotNull {
            val p = it.split("=")
            if (p.size == 2) p[0].trim() to p[1].trim() else null
        }
        .toMap()
}
