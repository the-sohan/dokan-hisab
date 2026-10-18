# dokan hisab

An offline Android ledger for grocery shops in Bangladesh. The interface is in Bangla and amounts are shown in Bangladeshi taka.

Current version: **0.3.0**. Requires Android 8.0 (API 26) or later; targets Android 15 (API 35).

## Features

- Add and edit customers with a required name and optional Bangladeshi mobile number.
- Record dated credit and repayment entries, with optional notes.
- Correct or delete entries and recalculate each customer's balance.
- Search customers and filter the list to customers with an outstanding balance.
- Store the ledger locally in SQLite, using integer poisha for monetary values.
- Activate Pro features with an offline license key.
- Export and restore versioned JSON backups, with checksum and data validation.
- Automatically update a selected Google Drive backup file approximately hourly when the ledger changes.
- Download a one-time backup file to the phone whenever needed.

Restoring a backup replaces the current ledger after confirmation. Import validation happens before replacement, and the database transaction rolls back if an insertion fails. Backup files do not contain the Pro license or account credentials.

## Backup behavior

Automatic Google Drive backups use Android's persisted `JobScheduler`. Battery management, device state, and background restrictions can delay a scheduled run, so one hour is an approximate interval rather than an exact wall-clock guarantee.

The app keeps an internal recovery copy for safe restore operations. Users can also download a one-time backup file manually. Internal app copies and manually downloaded files kept only on the same phone can be lost when the phone is reset.

Google Drive support uses Android's Storage Access Framework. The user selects a file through the installed Drive document provider; the app does not require its own OAuth client or a custom server. Drive must be installed and signed in to appear in the picker. A successful document write confirms that the provider accepted the file; it does not confirm that the provider finished uploading it. Verify the online copy in Drive before resetting a phone. Actual Google Drive synchronization still requires testing on a device with a signed-in Drive account.

After a reset or reinstall, activate the license again and select the saved backup file to restore the ledger.

## Offline licensing

`res/raw/license_hashes.txt` contains the SHA-256 verification hashes for the issued license batch. Original activation keys and the distribution inventory are kept privately and are not included in this repository.

Offline activation is stored on the device. It does not enforce one device per key, support remote revocation, or report activation to a spreadsheet or server. Pro gating is a convenience mechanism; publicly available client code can be modified.

## Build

The app uses native Android Java APIs and a small shell build script, without Gradle or third-party runtime libraries.

Install:

- JDK 17 or later.
- Android SDK platform `platforms;android-35`.
- Android SDK Build Tools `build-tools;35.0.0`.
- Android SDK Platform Tools for device tests.
- Standard shell utilities including `zip`.

Set the SDK and Java paths for your machine:

```sh
export JAVA_HOME="/path/to/jdk"
export ANDROID_SDK_ROOT="/path/to/android-sdk"
bash build.sh
```

The build script verifies the APK signature and runs the money/phone unit checks. The resulting APK is `build/dokan-hisab-0.3.0.apk`.

A local testing keystore is generated at `build/keys/local-test.jks` on the first build. Preserve that keystore for subsequent updates signed by the same identity. Signing keys and prebuilt APKs are excluded from Git. A fresh clone generates a different testing identity; use the original private keystore when building updates for an existing installation. The built-in testing keystore password is not intended for production release signing.

## Verification

Run the ledger integration checks against an Android emulator or test device:

```sh
bash test-device.sh
```

The script installs the app and a separate test APK, then runs 18 SQLite checks using a dedicated test database. It also compiles the backup/license instrumentation suite. To run that suite, supply a valid issued key privately to `bd.dokanhisab.tests.BackupInstrumentation` through its `license_key` instrumentation argument. Do not commit keys or use customer devices for instrumentation tests.

The current version was checked with:

- 24 money and phone validation checks.
- 18 SQLite ledger integration checks.
- 40 backup, restore, offline license, document-provider, and scheduling checks.
- Manual emulator checks for the Drive-only hourly auto-backup screen, one-time phone backup download, and backup selection with a restore confirmation.

`tests/ui_smoke.py` contains the earlier ledger UI smoke flow and expects a fresh, disposable emulator with an empty ledger. It is not a test of Google Drive synchronization.

## Source layout

- `src/bd/dokanhisab/app/`: app UI, ledger repository, monetary validation, licensing, backup format, and backup scheduling.
- `res/`: theme, icon, and license verification hashes.
- `tests/`: unit checks, instrumentation, and emulator UI smoke checks.
- `AndroidManifest.xml`: app identity, components, and Android requirements.
- `build.sh`, `test-device.sh`: build and device-check entry points.

There is no online ledger server or multi-device ledger synchronization in this version. Customer data, backup files, raw license keys, signing keys, and local preview tooling are not included in this repository.
