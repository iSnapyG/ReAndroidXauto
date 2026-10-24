# AutoX Store

An app marketplace that runs inside Android Auto. It is a second car app (`StoreCarAppService`,
POI category) next to AutoX itself, plus a settings dialog on the phone ("App store" in the
settings tab).

## What it does

- Lists apps in three groups: *Made for Android Auto* (media / navigation apps that already
  work in the car), *Run through AutoX* (ordinary phone apps you can show on the car screen
  with AutoX), and F-Droid categories.
- Search by name, package or summary; installed list; updates list with "Update all".
- Installs and updates silently with root (`pm install -r -S <size> < file`), removes apps
  the store installed (`pm uninstall`).
- "Use in AutoX" on an installed AutoX-kind app sets it as the AutoX target app (it does not
  turn AutoX on; AutoX still needs root and the LSPosed module).

## Catalog sources

1. **Bundled seed** (`app/src/main/assets/store/seed-catalog.json`): a short list of apps,
   each pinned to the signing key F-Droid uses for it.
2. **Your own catalog** (optional): an https URL in the phone settings. Same format as the seed.
3. **F-Droid index** (opt-in, about 21 MB compressed, about 67 MB of JSON): downloaded
   from `https://f-droid.org/repo/index-v2.json`, streamed, reduced to the newest version
   that fits the phone (Android version and CPU type), and cached as a compact file. It is
   refreshed about weekly. Apps in the seed that point at F-Droid are *unresolved* (shown but
   not installable) until the index has been downloaded.

### Catalog format (version 1)

```json
{ "version": 1, "apps": [
  { "name": "AntennaPod", "package": "de.danoeh.antennapod", "kind": "car",
    "summary": "optional", "categories": ["Podcast"],
    "signer": ["<sha256 of the signing certificate, hex>"],
    "fdroid": true },

  { "name": "Some app", "package": "org.example.app", "kind": "autox",
    "signer": ["<sha256>"],
    "github": "owner/repo", "asset": "arm64.*\\.apk" },

  { "name": "Fixed file", "package": "org.example.fixed", "kind": "other",
    "signer": ["<sha256>"],
    "url": "https://example.com/app.apk", "sha256": "<sha256 of the file>",
    "size": 123, "versionName": "1.2", "versionCode": 12 }
]}
```

Exactly one source per app (`fdroid`, `github` + `asset`, or `url` + `sha256`). `kind` is
`car`, `autox` or `other`. Entries that are not valid are skipped; a file that is not a
catalog is rejected whole. To get a signer fingerprint: `apksigner verify --print-certs app.apk`
(the SHA-256 digest line).

## Security model

- **https only.** Every request, and every redirect hop, must be https (`JdkHttpFetcher`).
- **Signer pinning is mandatory** for catalog entries. After the download, the APK is read
  with the platform parser and *every* signing certificate on it must be in the entry's pin
  list. Otherwise nothing is installed. F-Droid index entries use the signer the index lists
  for that exact file (and the seed pins it too, so a changed key drops the entry).
- **Hashes** are checked whenever they are known (F-Droid index, fixed URLs, GitHub's asset
  digest).
- The APK's package name must match the requested app; downgrades are refused.
- **Protected packages** (system, Google Play services, Android Auto, Play Store, the root
  manager, this app) can never be installed over or removed by the store.
- **Uninstall** only works for apps the store installed itself (a record is kept).
- Package names and file paths are validated against strict patterns before they appear in
  any shell command.
- Size caps on every download (catalog 2 MB, APK 400 MB, decompressed F-Droid index 256 MB).

### Known limits

- The F-Droid index is trusted because it is fetched over TLS from `f-droid.org`; its JAR
  signature (`entry.jar`) is **not** verified. A compromise of that server or of TLS would let
  an attacker choose which F-Droid-signed APK is offered, but an unpinned F-Droid app is then
  only as safe as the index. Seed apps stay protected by their pinned signer.
- Fixed-URL and GitHub entries from a catalog you add yourself are only as trustworthy as
  that catalog; the signer pin in the catalog is what protects you from a swapped file.
- Installing needs root and a working libsu shell. Nothing here asks Android's package
  installer for confirmation.
- Android Auto only shows sideloaded car apps when "Unknown sources" is on in its developer
  settings. The store tells you so on car-app detail pages.
- Not verified on a real car or head unit: the car screens use the Car App Library's
  `ListTemplate`, `MessageTemplate` and `SearchTemplate`, which the host may restrict
  differently than the library does.

## Code map

| Class | Role |
| --- | --- |
| `StoreApp`, `ApkSource`, `StoreValidation` | Validated app and source models |
| `StoreCatalogParser`, `FdroidIndexParser`, `GithubRelease` | Streaming JSON readers |
| `CatalogLoader`, `CatalogMerge` | Download, cache and merge the three sources |
| `ApkVerifier`, `PackagePolicy`, `PmCommands` | Install-time checks and shell commands |
| `StoreInstaller` | Resolve, download, verify, install, uninstall |
| `StoreController` | One-operation-at-a-time state shared by the car screens and the phone dialog |
| `StoreFilter`, `Page`, `UpdateCheck`, `InstalledView` | Browse, search, paging, update status |
| `store/android/*` | libsu shell, PackageManager inspector, wiring (excluded from coverage) |
| `store/car/*` | Car App Library service, session and screens (excluded from coverage) |
