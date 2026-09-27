# MyExpenses — Native Kotlin Android

This project is a native Android rewrite of the uploaded `MyExpenses_v4_LedgerExport.apk`.

## What changed
- No WebView
- No HTML/CSS/JavaScript
- No Cordova
- Native Kotlin + Jetpack Compose UI
- Native SQLite local database
- Native Android file picker for backup/restore
- Native CSV export
- Native PDF generation
- Android PrintManager for printing
- Native dark mode
- Native calculator

## Main features
- Expense is the default entry type
- Income / Expense switch
- Amount + calculator button
- Optional note
- Categories and category management
- Today's summary
- Current-month history
- Edit and delete entries
- Carry-forward balance from previous months
- Summary reports with expense percentages
- Ledger with running balance
- Print summary / ledger
- CSV export
- PDF save
- JSON backup / restore using Android's document picker

## Build on GitHub
1. Create/open a GitHub repository.
2. Upload all files from this folder to the repository root.
3. Commit to `main`.
4. Open **Actions → Build MyExpenses Native APK**.
5. Wait for the workflow to finish.
6. Open the completed run and download **MyExpenses-Native-debug** from Artifacts.

The app stores data in the Android app's private SQLite database. Backup/export uses Android's native file picker, so it does not depend on browser downloads.
