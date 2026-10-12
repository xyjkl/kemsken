package com.example.feature.trash

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.DocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.ui.components.DocumentThumbnail
import com.example.ui.components.EmptyStateView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    repository: DocumentRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashDocs by repository.getRecycleBinDocuments().collectAsStateWithLifecycle(initialValue = emptyList())

    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var docToPermanentlyDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tempat Sampah",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (trashDocs.isNotEmpty()) {
                        IconButton(
                            onClick = { showEmptyTrashConfirm = true },
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Kosongkan Sampah",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (trashDocs.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.DeleteSweep,
                    title = "Tempat Sampah Kosong",
                    description = "Dokumen yang Anda hapus akan disimpan di sini sebelum dihapus secara permanen.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashDocs, key = { it.id }) { doc ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DocumentThumbnail(
                                    thumbnailPath = doc.thumbnailPath,
                                    modifier = Modifier.size(54.dp, 68.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Dihapus: ${doc.deletedAt?.let { dateFormat.format(Date(it)) } ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Restore Button
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            repository.restoreFromRecycleBin(doc.id)
                                            Toast.makeText(context, "Dokumen dipulihkan", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = "Pulihkan",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Permanently Button
                                IconButton(
                                    onClick = { docToPermanentlyDelete = doc }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = "Hapus Permanen",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Empty Trash Confirmation Dialog
    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Kosongkan Tempat Sampah?") },
            text = { Text("Semua dokumen di tempat sampah akan dihapus secara permanen dan tidak dapat dipulihkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.emptyRecycleBin()
                            showEmptyTrashConfirm = false
                            Toast.makeText(context, "Tempat sampah dikosongkan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kosongkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Single Document Permanent Delete Dialog
    if (docToPermanentlyDelete != null) {
        val d = docToPermanentlyDelete!!
        AlertDialog(
            onDismissRequest = { docToPermanentlyDelete = null },
            title = { Text("Hapus Permanen?") },
            text = { Text("Dokumen \"${d.title}\" akan dihapus selamanya dari perangkat Anda.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deletePermanently(d.id)
                            docToPermanentlyDelete = null
                            Toast.makeText(context, "Dokumen dihapus permanen", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToPermanentlyDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
