import sqlite3
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from datetime import datetime
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, Side
from openpyxl.worksheet.page import PageMargins

from reportlab.lib import colors
from reportlab.lib.pagesizes import A3, landscape
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from reportlab.lib.units import mm


APP_DATA_DIR = Path.home() / "AppData" / "Local" / "DataWargaKos"
APP_DATA_DIR.mkdir(parents=True, exist_ok=True)

DB_FILE = APP_DATA_DIR / "data_warga_kos.db"

JUMLAH_BARIS_LAPORAN = 40

KOLOM = [
    "pemilik",
    "nama",
    "no_kamar",
    "jumlah_orang",
    "alamat",
    "status_nikah",
    "status_belum_nikah",
    "keterangan",
]

LABEL = {
    "pemilik": "Nama Pemilik Kos",
    "nama": "Nama Warga Kos",
    "no_kamar": "No Kamar",
    "jumlah_orang": "Jumlah Orang",
    "alamat": "Alamat Lengkap",
    "status_nikah": "Nikah",
    "status_belum_nikah": "Belum Nikah",
    "keterangan": "Keterangan",
}


class AplikasiKos:
    def __init__(self, root):
        self.root = root
        self.root.title("Data Warga Kos")
        self.root.geometry("1250x720")
        self.root.minsize(1000, 600)

        self.conn = sqlite3.connect(DB_FILE)
        self.conn.row_factory = sqlite3.Row
        self.buat_tabel()

        self.selected_id = None
        self.entries = {}
        self.title_var = tk.StringVar(value="DATA WARGA KOS")

        self.buat_tampilan()
        self.muat_data()

        self.root.protocol("WM_DELETE_WINDOW", self.tutup)

    def buat_tabel(self):
        self.conn.execute(
            """
            CREATE TABLE IF NOT EXISTS warga_kos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pemilik TEXT NOT NULL DEFAULT '',
                nama TEXT NOT NULL DEFAULT '',
                no_kamar TEXT NOT NULL DEFAULT '',
                jumlah_orang TEXT NOT NULL DEFAULT '',
                alamat TEXT NOT NULL DEFAULT '',
                status_nikah TEXT NOT NULL DEFAULT '',
                status_belum_nikah TEXT NOT NULL DEFAULT '',
                keterangan TEXT NOT NULL DEFAULT ''
            )
            """
        )
        self.conn.commit()

    def buat_tampilan(self):
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        style.configure("Treeview", rowheight=27, font=("Segoe UI", 9))
        style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"))

        utama = ttk.Frame(self.root, padding=14)
        utama.pack(fill="both", expand=True)

        ttk.Label(
            utama,
            text="DATA WARGA KOS",
            font=("Segoe UI", 19, "bold"),
        ).pack(anchor="w")

        ttk.Label(
            utama,
            text="Kelola data, lalu cetak atau ekspor laporan.",
            foreground="#555",
        ).pack(anchor="w", pady=(2, 12))

        form = ttk.LabelFrame(utama, text="Formulir data", padding=10)
        form.pack(fill="x", pady=(0, 10))

        kolom_form = [
            ("pemilik", "Nama Pemilik Kos"),
            ("nama", "Nama Warga Kos"),
            ("no_kamar", "No Kamar"),
            ("jumlah_orang", "Jumlah Orang"),
            ("alamat", "Alamat Lengkap"),
            ("status_nikah", "Nikah"),
            ("status_belum_nikah", "Belum Nikah"),
            ("keterangan", "Keterangan"),
        ]

        for i, (key, label) in enumerate(kolom_form):
            baris, kol = divmod(i, 4)
            sel = ttk.Frame(form)
            sel.grid(row=baris, column=kol, sticky="ew", padx=5, pady=4)
            ttk.Label(sel, text=label).pack(anchor="w")

            if key in ("status_nikah", "status_belum_nikah"):
                widget = ttk.Combobox(
                    sel,
                    values=("", "✓", "Ya", "Tidak"),
                    state="readonly",
                )
            else:
                widget = ttk.Entry(sel)

            widget.pack(fill="x", pady=(2, 0))
            self.entries[key] = widget
            form.columnconfigure(kol, weight=1)

        tombol_form = ttk.Frame(form)
        tombol_form.grid(row=2, column=0, columnspan=4, sticky="w", padx=5, pady=(8, 0))
        ttk.Button(tombol_form, text="Simpan data", command=self.simpan).pack(side="left", padx=(0, 6))
        ttk.Button(tombol_form, text="Kosongkan form", command=self.kosongkan_form).pack(side="left", padx=6)
        ttk.Button(tombol_form, text="Hapus data terpilih", command=self.hapus).pack(side="left", padx=6)

        toolbar = ttk.Frame(utama)
        toolbar.pack(fill="x", pady=(0, 9))

        ttk.Label(toolbar, text="Judul laporan:").pack(side="left")
        ttk.Entry(toolbar, textvariable=self.title_var, width=32).pack(side="left", padx=(6, 14))

        ttk.Button(toolbar, text="Ekspor Excel", command=self.ekspor_excel).pack(side="right", padx=(6, 0))
        ttk.Button(toolbar, text="Cetak PDF", command=self.ekspor_pdf).pack(side="right")

        tabel_frame = ttk.Frame(utama)
        tabel_frame.pack(fill="both", expand=True)

        display_columns = ("no",) + tuple(KOLOM)
        self.tree = ttk.Treeview(
            tabel_frame,
            columns=display_columns,
            show="headings",
            selectmode="browse",
        )

        headings = {
            "no": "No",
            **LABEL,
        }
        widths = {
            "no": 48,
            "pemilik": 160,
            "nama": 150,
            "no_kamar": 82,
            "jumlah_orang": 95,
            "alamat": 190,
            "status_nikah": 90,
            "status_belum_nikah": 105,
            "keterangan": 150,
        }

        for col in display_columns:
            self.tree.heading(col, text=headings[col])
            self.tree.column(col, width=widths[col], minwidth=55, anchor="center")

        self.tree.column("pemilik", anchor="w")
        self.tree.column("nama", anchor="w")
        self.tree.column("alamat", anchor="w")
        self.tree.column("keterangan", anchor="w")

        y_scroll = ttk.Scrollbar(tabel_frame, orient="vertical", command=self.tree.yview)
        x_scroll = ttk.Scrollbar(tabel_frame, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=y_scroll.set, xscrollcommand=x_scroll.set)

        self.tree.grid(row=0, column=0, sticky="nsew")
        y_scroll.grid(row=0, column=1, sticky="ns")
        x_scroll.grid(row=1, column=0, sticky="ew")
        tabel_frame.rowconfigure(0, weight=1)
        tabel_frame.columnconfigure(0, weight=1)

        self.tree.bind("<<TreeviewSelect>>", self.pilih_data)
        self.tree.bind("<Double-1>", self.pilih_data)

    def ambil_form(self):
        return {
            key: widget.get().strip()
            for key, widget in self.entries.items()
        }

    def muat_data(self):
        for item in self.tree.get_children():
            self.tree.delete(item)

        rows = self.conn.execute(
            "SELECT * FROM warga_kos ORDER BY id"
        ).fetchall()

        for nomor, row in enumerate(rows, start=1):
            self.tree.insert(
                "",
                "end",
                iid=str(row["id"]),
                values=(nomor,) + tuple(row[key] for key in KOLOM),
            )

    def simpan(self):
        data = self.ambil_form()

        if not data["nama"]:
            messagebox.showwarning("Data belum lengkap", "Nama warga kos wajib diisi.")
            return

        if self.selected_id is None:
            self.conn.execute(
                """
                INSERT INTO warga_kos
                (pemilik, nama, no_kamar, jumlah_orang, alamat,
                 status_nikah, status_belum_nikah, keterangan)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tuple(data[key] for key in KOLOM),
            )
            pesan = "Data berhasil ditambahkan."
        else:
            self.conn.execute(
                """
                UPDATE warga_kos SET
                    pemilik = ?, nama = ?, no_kamar = ?, jumlah_orang = ?,
                    alamat = ?, status_nikah = ?, status_belum_nikah = ?,
                    keterangan = ?
                WHERE id = ?
                """,
                tuple(data[key] for key in KOLOM) + (self.selected_id,),
            )
            pesan = "Data berhasil diperbarui."

        self.conn.commit()
        self.muat_data()
        self.kosongkan_form()
        messagebox.showinfo("Berhasil", pesan)

    def pilih_data(self, _event=None):
        pilihan = self.tree.selection()
        if not pilihan:
            return

        self.selected_id = int(pilihan[0])
        row = self.conn.execute(
            "SELECT * FROM warga_kos WHERE id = ?",
            (self.selected_id,),
        ).fetchone()

        if row is None:
            return

        for key, widget in self.entries.items():
            widget.set(row[key]) if isinstance(widget, ttk.Combobox) else (
                widget.delete(0, "end"),
                widget.insert(0, row[key]),
            )

    def kosongkan_form(self):
        self.selected_id = None
        for widget in self.entries.values():
            if isinstance(widget, ttk.Combobox):
                widget.set("")
            else:
                widget.delete(0, "end")
        for item in self.tree.selection():
            self.tree.selection_remove(item)

    def hapus(self):
        if self.selected_id is None:
            messagebox.showwarning("Pilih data", "Pilih baris yang ingin dihapus.")
            return

        if not messagebox.askyesno("Konfirmasi", "Hapus data yang dipilih?"):
            return

        self.conn.execute("DELETE FROM warga_kos WHERE id = ?", (self.selected_id,))
        self.conn.commit()
        self.muat_data()
        self.kosongkan_form()
        messagebox.showinfo("Berhasil", "Data berhasil dihapus.")

    def data_laporan(self):
        rows = self.conn.execute(
            "SELECT * FROM warga_kos ORDER BY id"
        ).fetchall()

        jumlah_baris = max(JUMLAH_BARIS_LAPORAN, len(rows))
        hasil = []

        for i in range(jumlah_baris):
            if i < len(rows):
                row = rows[i]
                hasil.append([str(i + 1)] + [row[key] or "" for key in KOLOM])
            else:
                hasil.append([str(i + 1)] + [""] * len(KOLOM))

        return hasil

    def pilih_lokasi(self, ekstensi, tipe):
        return filedialog.asksaveasfilename(
            title=f"Simpan laporan {tipe}",
            defaultextension=ekstensi,
            filetypes=[(f"File {tipe}", f"*{ekstensi}")],
            initialfile=f"laporan_warga_kos{ekstensi}",
        )

    def ekspor_excel(self):
        lokasi = self.pilih_lokasi(".xlsx", "Excel")
        if not lokasi:
            return

        judul = self.title_var.get().strip() or "DATA WARGA KOS"
        waktu_cetak = datetime.now().strftime("%d-%m-%Y %H:%M:%S")

        wb = Workbook()
        ws = wb.active
        ws.title = "Data Warga Kos"

        ws.merge_cells("A1:I1")
        ws["A1"] = judul
        ws["A1"].font = Font(name="Arial", size=15, bold=True)
        ws["A1"].alignment = Alignment(horizontal="center", vertical="center")
        ws.merge_cells("A2:I2")
        ws["A2"] = f"Dicetak pada: {waktu_cetak}"
        ws["A2"].font = Font(name="Arial", size=9, italic=True)
        ws["A2"].alignment = Alignment(horizontal="right")

        ws.merge_cells("A4:A5")
        ws["A4"] = "No"
        ws.merge_cells("B4:B5")
        ws["B4"] = "Nama Pemilik Kos"
        ws.merge_cells("C4:E4")
        ws["C4"] = "Nama Warga Kos"
        ws["C5"] = "Nama"
        ws["D5"] = "No Kamar"
        ws["E5"] = "Jumlah Orang"
        ws.merge_cells("F4:F5")
        ws["F4"] = "Alamat Lengkap"
        ws.merge_cells("G4:H4")
        ws["G4"] = "Status"
        ws["G5"] = "Nikah"
        ws["H5"] = "Belum Nikah"
        ws.merge_cells("I4:I5")
        ws["I4"] = "Keterangan"

        border_tipis = Side(style="thin", color="000000")
        border = Border(
            left=border_tipis, right=border_tipis,
            top=border_tipis, bottom=border_tipis,
        )

        for row in ws.iter_rows(min_row=4, max_row=5, min_col=1, max_col=9):
            for cell in row:
                cell.font = Font(name="Arial", size=10, bold=True)
                cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
                cell.border = border

        for r, values in enumerate(self.data_laporan(), start=6):
            for c, value in enumerate(values, start=1):
                cell = ws.cell(row=r, column=c, value=value)
                cell.font = Font(name="Arial", size=10)
                cell.alignment = Alignment(
                    horizontal="center" if c in (1, 4, 5, 7, 8) else "left",
                    vertical="center",
                    wrap_text=True,
                )
                cell.border = border
            ws.row_dimensions[r].height = 22

        ws.row_dimensions[1].height = 25
        ws.row_dimensions[4].height = 25
        ws.row_dimensions[5].height = 23

        lebar = {
            "A": 6, "B": 23, "C": 23, "D": 13, "E": 15,
            "F": 28, "G": 13, "H": 16, "I": 23,
        }
        for kol, ukuran in lebar.items():
            ws.column_dimensions[kol].width = ukuran
        jumlah_baris_data = len(self.data_laporan())
        baris_ttd = max(48, 6 + jumlah_baris_data + 2)

        blok_ttd = [
            (1, 3, "Mengetahui,\nKetua RW. 01"),
            (4, 6, "Ketua RT. 01"),
            (7, 9, "Ketua RT. 02"),
        ]

        for kolom_awal, kolom_akhir, jabatan in blok_ttd:
            ws.merge_cells(
                start_row=baris_ttd,
                start_column=kolom_awal,
                end_row=baris_ttd,
                end_column=kolom_akhir,
            )

            sel_jabatan = ws.cell(baris_ttd, kolom_awal, jabatan)
            sel_jabatan.font = Font(name="Arial", size=10, bold=True)
            sel_jabatan.alignment = Alignment(
                horizontal="center",
                vertical="center",
                wrap_text=True,
            )

            ws.merge_cells(
                start_row=baris_ttd + 1,
                start_column=kolom_awal,
                end_row=baris_ttd + 3,
                end_column=kolom_akhir,
            )

            ws.merge_cells(
                start_row=baris_ttd + 4,
                start_column=kolom_awal,
                end_row=baris_ttd + 4,
                end_column=kolom_akhir,
            )

            sel_nama = ws.cell(
                baris_ttd + 4,
                kolom_awal,
                "( ____________________ )",
            )
            sel_nama.font = Font(name="Arial", size=10)
            sel_nama.alignment = Alignment(
                horizontal="center",
                vertical="center",
            )

        ws.row_dimensions[baris_ttd].height = 32
        ws.row_dimensions[baris_ttd + 1].height = 20
        ws.row_dimensions[baris_ttd + 2].height = 20
        ws.row_dimensions[baris_ttd + 3].height = 20
        ws.row_dimensions[baris_ttd + 4].height = 24

        ws.freeze_panes = "A6"
        ws.sheet_properties.pageSetUpPr.fitToPage = True
        ws.page_setup.orientation = "landscape"
        ws.page_setup.paperSize = ws.PAPERSIZE_A3
        ws.page_setup.fitToWidth = 1
        ws.page_setup.fitToHeight = 1
        ws.page_margins = PageMargins(
            left=0.25, right=0.25, top=0.35, bottom=0.35, header=0.15, footer=0.15
        )
        ws.print_area = f"A1:I{baris_ttd + 4}"
        ws.sheet_view.showGridLines = False

        try:
            wb.save(lokasi)
            messagebox.showinfo("Berhasil", f"File Excel tersimpan:\n{lokasi}")
        except Exception as exc:
            messagebox.showerror("Gagal menyimpan", str(exc))

    def ekspor_pdf(self):
        lokasi = self.pilih_lokasi(".pdf", "PDF")
        if not lokasi:
            return

        judul = self.title_var.get().strip() or "DATA WARGA KOS"
        waktu_cetak = datetime.now().strftime("%d-%m-%Y %H:%M:%S")

        try:
            halaman = landscape(A3)
            doc = SimpleDocTemplate(
                lokasi,
                pagesize=halaman,
                leftMargin=8 * mm,
                rightMargin=8 * mm,
                topMargin=8 * mm,
                bottomMargin=8 * mm,
            )

            styles = getSampleStyleSheet()
            judul_style = styles["Title"]
            judul_style.fontName = "Helvetica-Bold"
            judul_style.fontSize = 14
            judul_style.leading = 17

            waktu_style = styles["Normal"]
            waktu_style.fontSize = 8
            waktu_style.alignment = 2

            konten = [
                Paragraph(judul.replace("&", "&amp;"), judul_style),
                Paragraph(f"Dicetak pada: {waktu_cetak}", waktu_style),
                Spacer(1, 4 * mm),
            ]

            data = [
                [
                    "No",
                    "Nama Pemilik Kos",
                    "Nama Warga Kos",
                    "",
                    "",
                    "Alamat Lengkap",
                    "Status",
                    "",
                    "Keterangan",
                ],
                ["", "", "Nama", "No Kamar", "Jumlah Orang", "", "Nikah", "Belum Nikah", ""],
            ]
            data.extend(self.data_laporan())

            lebar_total = halaman[0] - doc.leftMargin - doc.rightMargin
            proporsi = [0.045, 0.15, 0.15, 0.085, 0.10, 0.18, 0.09, 0.105, 0.095]
            col_widths = [lebar_total * p for p in proporsi]

            tabel = Table(
                data,
                colWidths=col_widths,
                rowHeights=[7 * mm, 6 * mm] + [4.5 * mm] * len(self.data_laporan()),
                repeatRows=2,
            )

            tabel.setStyle(TableStyle([
                ("SPAN", (0, 0), (0, 1)),
                ("SPAN", (1, 0), (1, 1)),
                ("SPAN", (2, 0), (4, 0)),
                ("SPAN", (5, 0), (5, 1)),
                ("SPAN", (6, 0), (7, 0)),
                ("SPAN", (8, 0), (8, 1)),
                ("GRID", (0, 0), (-1, -1), 0.45, colors.black),
                ("FONTNAME", (0, 0), (-1, 1), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, 1), 8),
                ("FONTSIZE", (0, 2), (-1, -1), 7),
                ("ALIGN", (0, 0), (-1, 1), "CENTER"),
                ("ALIGN", (0, 2), (0, -1), "CENTER"),
                ("ALIGN", (3, 2), (4, -1), "CENTER"),
                ("ALIGN", (6, 2), (7, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 3),
                ("RIGHTPADDING", (0, 0), (-1, -1), 3),
                ("TOPPADDING", (0, 0), (-1, -1), 1),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 1),
            ]))

            konten.append(tabel)
            
            from reportlab.lib.enums import TA_CENTER
            from reportlab.lib.styles import ParagraphStyle

            gaya_ttd = ParagraphStyle(
                "GayaTandaTangan",
                parent=styles["Normal"],
                alignment=TA_CENTER,
                fontSize=9,
                leading=13,
            )

            ttd_data = [
                [
                    Paragraph("<b>Mengetahui,</b><br/>Ketua RW. 01", gaya_ttd),
                    Paragraph("<br/>Ketua RT. 01", gaya_ttd),
                    Paragraph("<br/>Ketua RT. 02", gaya_ttd),
                ],
                ["", "", ""],
                [
                    Paragraph("( ____________________ )", gaya_ttd),
                    Paragraph("( ____________________ )", gaya_ttd),
                    Paragraph("( ____________________ )", gaya_ttd),
                ],
            ]

            ttd = Table(
                ttd_data,
                colWidths=[lebar_total / 3] * 3,
                rowHeights=[14 * mm, 25 * mm, 8 * mm],
            )

            ttd.setStyle(TableStyle([
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 0),
                ("TOPPADDING", (0, 0), (-1, -1), 0),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
            ]))

            konten.append(ttd)

            doc.build(konten)
            messagebox.showinfo("Berhasil", f"File PDF tersimpan:\n{lokasi}")

        except Exception as exc:
            messagebox.showerror("Gagal membuat PDF", str(exc))

    def tutup(self):
        self.conn.close()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    aplikasi = AplikasiKos(root)
    root.mainloop()
