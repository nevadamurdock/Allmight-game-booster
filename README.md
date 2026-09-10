# All Might Game Booster

Xposed/LSPosed module untuk optimasi performa Android secara generik per-app.

## Fitur

| # | Fitur | Deskripsi |
|---|---|---|
| 1 | Override Thermal & CPU/GPU Governor | Kunci governor ke performance, adaptive thermal |
| 2 | Unlock FPS & Render Limiter | Buka batas frame rate |
| 3 | Prioritaskan Proses & Cegah Background Kill | Set oom_adj rendah, cegah kill |
| 4 | Kurangi Input & Render Latency | Optimasi touch response & rendering |
| 5 | CPU Core Affinity (big.LITTLE) | Arahkan ke core performa |
| 7 | Freeze Background Apps | Bekukan app lain, RAM booster sungguhan |
| 8 | Network QoS per App | Prioritaskan bandwidth per-app |
| 9 | Auto Do-Not-Disturb | Sembunyikan notifikasi saat boost |
| 10 | Overlay Monitor Real-Time | Suhu, FPS, clock di atas game |
| 11 | Quick Settings Tile Toggle | Toggle dari notification shade |
| 12 | Battery-Aware Auto Revert | Matikan boost saat baterai rendah |
| 13 | Profil/Preset Terpisah | Performance / Balanced / Battery Saver |
| 14 | I/O Priority Boost | Prioritaskan akses storage |
| 15 | Adaptive Governor | Closed-loop suhu/FPS |
| 16 | Low-Latency Audio Path | Kurangi delay suara |
| 17 | Kunci Refresh Rate & Brightness | Paksa rate tertinggi |
| 18 | Pre-Launch RAM Cleaner | Bersihkan RAM sebelum boost |
| 19 | Anti Keluar Tidak Sengaja | Screen pinning |
| 20 | Riwayat & Grafik Statistik | Log sesi suhu/FPS |
| 21 | Sembunyikan Jejak Root | Root hide per-app |

## Requirements

- Android 8.0+ (API 26)
- Root access (Magisk / KernelSU)
- LSPosed with modern Xposed API 102 support
- Module diaktifkan di LSPosed scope

## Build

Push ke branch `main` — APK akan otomatis ter-build via GitHub Actions.
Hasilnya bisa di-download di tab **Actions** → pilih workflow run → **Artifacts**.

## Instalasi

1. Download APK dari Artifacts atau build lokal
2. Install APK
3. Buka LSPosed → Module → All Might Game Booster
4. Aktifkan module
5. Pilih scope (apps yang ingin di-boost)
6. Reboot
7. Buka app, selesaikan onboarding
8. Pilih app → atur preset → mulai boost

## Desain

Konsep visual: **konsol tuning** — bukan dashboard gamer RGB.
- Warna: graphite base, copper-signal accent, redline-alert kritis
- Tipografi: sans grotesque untuk UI, monospace untuk angka data
- Gauge radial analog untuk suhu & clock
- Overlay minimal: satu baris monospace transparan

## Lisensi

MIT License
