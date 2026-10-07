package com.example.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Person
import com.example.viewmodel.PersonViewModel

import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Color
import com.example.data.sync.SyncState
import kotlinx.coroutines.launch

@Composable
fun SyncStatusBar(syncState: SyncState) {
    val (text, color) = when (syncState) {
        is SyncState.Idle -> "ข้อมูลล่าสุด" to Color.Gray
        is SyncState.Syncing -> "กำลังซิงค์ข้อมูล..." to Color.Blue
        is SyncState.Success -> "ซิงค์ล่าสุด: ${java.time.format.DateTimeFormatter.ISO_LOCAL_TIME.format(java.time.LocalDateTime.now())}" to Color(0xFF388E3C)
        is SyncState.Error -> "ข้อผิดพลาดในการซิงค์" to Color.Red
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
fun RegistrationListScreen(
    viewModel: PersonViewModel,
    onPersonClick: (Person) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allPersons by viewModel.allPersons.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    val filteredPersons by remember(allPersons, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                allPersons
            } else {
                allPersons.filter {
                    it.fullName.contains(searchQuery, ignoreCase = true) ||
                            (it.nationalId?.contains(searchQuery) ?: false)
                }
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_bar"),
                        placeholder = { Text("ค้นหาชื่อ หรือ เลขบัตร...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        viewModel.exportToCsv(context) { success, uri, message ->
                            if (success && uri != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "แชร์ไฟล์ CSV"))
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV")
                    }
                }
                SyncStatusBar(syncState)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("person_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredPersons, key = { it.id }) { person ->
                PersonListItem(person = person, onClick = { onPersonClick(person) })
            }
        }
    }
}

@Composable
fun PersonListItem(
    person: Person,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_card"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = person.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "ID: ${person.nationalId ?: "ไม่มีข้อมูล"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
