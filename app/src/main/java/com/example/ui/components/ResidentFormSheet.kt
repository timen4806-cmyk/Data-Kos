package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.WargaKosResident

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentFormSheet(
    initialResident: WargaKosResident?,
    onDismiss: () -> Unit,
    onSave: (WargaKosResident) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pemilik by remember(initialResident) { mutableStateOf(initialResident?.pemilik ?: "") }
    var nama by remember(initialResident) { mutableStateOf(initialResident?.nama ?: "") }
    var noKamar by remember(initialResident) { mutableStateOf(initialResident?.noKamar ?: "") }
    var jumlahOrang by remember(initialResident) { mutableStateOf(initialResident?.jumlahOrang ?: "") }
    var alamat by remember(initialResident) { mutableStateOf(initialResident?.alamat ?: "") }
    var statusNikah by remember(initialResident) { mutableStateOf(initialResident?.statusNikah ?: "") }
    var statusBelumNikah by remember(initialResident) { mutableStateOf(initialResident?.statusBelumNikah ?: "") }
    var keterangan by remember(initialResident) { mutableStateOf(initialResident?.keterangan ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initialResident == null) "Tambah Warga Kos Baru" else "Edit Data Warga Kos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Seluruh isian dienkripsi end-to-end sebelum disimpan ke SQLite offline & Firestore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = pemilik,
                onValueChange = { pemilik = it },
                label = { Text("Nama Pemilik Kos") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_pemilik_input"),
            )

            OutlinedTextField(
                value = nama,
                onValueChange = {
                    nama = it
                    validationError = null
                },
                label = { Text("Nama Warga Kos *") },
                isError = validationError != null,
                supportingText = validationError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_nama_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = noKamar,
                    onValueChange = { noKamar = it },
                    label = { Text("No Kamar") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_kamar_input"),
                )
                OutlinedTextField(
                    value = jumlahOrang,
                    onValueChange = { jumlahOrang = it },
                    label = { Text("Jumlah Orang") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_jumlah_orang_input"),
                )
            }

            OutlinedTextField(
                value = alamat,
                onValueChange = { alamat = it },
                label = { Text("Alamat Lengkap") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_alamat_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusDropdownField(
                    label = "Status: Nikah",
                    value = statusNikah,
                    onValueChange = { statusNikah = it },
                    modifier = Modifier.weight(1f),
                    testTag = "form_status_nikah_dropdown",
                )
                StatusDropdownField(
                    label = "Belum Nikah",
                    value = statusBelumNikah,
                    onValueChange = { statusBelumNikah = it },
                    modifier = Modifier.weight(1f),
                    testTag = "form_status_belum_nikah_dropdown",
                )
            }

            OutlinedTextField(
                value = keterangan,
                onValueChange = { keterangan = it },
                label = { Text("Keterangan") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_keterangan_input"),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        pemilik = ""
                        nama = ""
                        noKamar = ""
                        jumlahOrang = ""
                        alamat = ""
                        statusNikah = ""
                        statusBelumNikah = ""
                        keterangan = ""
                        validationError = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_clear_button"),
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kosongkan")
                }

                Button(
                    onClick = {
                        if (nama.trim().isEmpty()) {
                            validationError = "Nama warga kos wajib diisi."
                        } else {
                            onSave(
                                WargaKosResident(
                                    id = initialResident?.id ?: "",
                                    pemilik = pemilik.trim(),
                                    nama = nama.trim(),
                                    noKamar = noKamar.trim(),
                                    jumlahOrang = jumlahOrang.trim(),
                                    alamat = alamat.trim(),
                                    statusNikah = statusNikah,
                                    statusBelumNikah = statusBelumNikah,
                                    keterangan = keterangan.trim(),
                                    createdAtMillis = initialResident?.createdAtMillis ?: 0L,
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("form_save_button"),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan Data")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdownField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value.ifEmpty { "(Kosong)" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(testTag),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            WargaKosResident.STATUS_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { "(Kosong)" }) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
