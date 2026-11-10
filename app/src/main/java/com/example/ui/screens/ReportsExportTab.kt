package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.WargaKosResident
import com.example.export.ExportResult
import com.example.export.ReportExporter

@Composable
fun ReportsExportTab(
    reportTitle: String,
    residents: List<WargaKosResident>,
    lastExportResult: ExportResult?,
    onReportTitleChange: (String) -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = lastExportResult?.internalFile
        if (uri != null && file != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeToOutputStream(out, file)
                }
                onShowMessage("Salinan laporan berhasil disimpan ke folder pilihan.")
            } catch (e: Exception) {
                onShowMessage("Gagal menyimpan salinan: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Konfigurasi & Ekspor Laporan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Laporan otomatis mencakup format 40 baris standar, header gabungan, serta blok tanda tangan Ketua RW 01, RT 01, dan RT 02. File disimpan ke direktori internal perangkat & folder Downloads/WargaKos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = reportTitle,
                    onValueChange = onReportTitleChange,
                    label = { Text("Judul Laporan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_title_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onExportExcel,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("export_excel_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ekspor Excel")
                    }

                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("export_pdf_button"),
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak PDF")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val summary = ReportExporter.buildShareSummaryText(reportTitle, residents)
                        context.startActivity(
                            ReportExporter.shareTextIntent(summary, "Bagikan Data Warga Kos")
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_summary_button"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bagikan Ringkasan ke WhatsApp / Pesan")
                }
            }
        }

        if (lastExportResult != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Laporan Terakhir Dibuat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = "File: ${lastExportResult.fileName}\nLokasi: ${lastExportResult.publicDisplayPath}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    ReportExporter.shareFileIntent(
                                        context,
                                        lastExportResult,
                                        "Kirim Dokumen Laporan"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_exported_file_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan File")
                        }

                        OutlinedButton(
                            onClick = { saveAsLauncher.launch(lastExportResult.fileName) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_exported_copy_button"),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan ke...")
                        }
                    }
                }
            }
        }

        // Live Report Table Preview matching the Python openpyxl / reportlab structure
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pratinjau Format Laporan (${residents.size} data aktif)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        // Row 1 & 2 merged header
                        Row(
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            TableCell("No", 42.dp, bold = true, center = true)
                            TableCell("Nama Pemilik Kos", 130.dp, bold = true, center = true)
                            TableCell("Nama Warga Kos", 130.dp, bold = true, center = true)
                            TableCell("No Kamar", 80.dp, bold = true, center = true)
                            TableCell("Jml Orang", 80.dp, bold = true, center = true)
                            TableCell("Alamat Lengkap", 160.dp, bold = true, center = true)
                            TableCell("Nikah", 70.dp, bold = true, center = true)
                            TableCell("Belum Nikah", 85.dp, bold = true, center = true)
                            TableCell("Keterangan", 130.dp, bold = true, center = true)
                        }

                        val previewRows = residents.take(8)
                        if (previewRows.isEmpty()) {
                            Row(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Belum ada data penghuni. Ekspor tetap menghasilkan 40 baris template kosong.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            previewRows.forEachIndexed { idx, r ->
                                Row {
                                    TableCell("${idx + 1}", 42.dp, center = true)
                                    TableCell(r.pemilik, 130.dp)
                                    TableCell(r.nama, 130.dp)
                                    TableCell(r.noKamar, 80.dp, center = true)
                                    TableCell(r.jumlahOrang, 80.dp, center = true)
                                    TableCell(r.alamat, 160.dp)
                                    TableCell(r.statusNikah, 70.dp, center = true)
                                    TableCell(r.statusBelumNikah, 85.dp, center = true)
                                    TableCell(r.keterangan, 130.dp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text("Mengetahui,\nKetua RW. 01", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("\nKetua RT. 01", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("\nKetua RT. 02", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    bold: Boolean = false,
    center: Boolean = false,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(38.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        )
    }
}
