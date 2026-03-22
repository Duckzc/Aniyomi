# Aniyomi Custom Extension Template

Build and self-host your own Aniyomi extensions **without Android Studio** — 
every APK is compiled and deployed by GitHub Actions.

---

## One-time setup

### 1. Create your repo

- Click **Use this template** (or push this folder to a new private/public GitHub repo).
- Enable **GitHub Pages** in repo Settings → Pages → Source: **GitHub Actions**.

### 2. Generate a signing keystore

Extensions must be signed consistently so Aniyomi can update them without 
uninstalling first.  Run this once on any machine with a JDK:

```bash
keytool -genkey -v \
  -keystore my-extensions.jks \
  -alias my-ext-key \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Keep the `.jks` file safe — **losing it means users must uninstall and 
reinstall to get updates**.

### 3. Add repository secrets

Go to **Settings → Secrets and variables → Actions → New repository secret**:

| Secret name          | Value                                   |
|---------------------|-----------------------------------------|
| `SIGNING_KEY`        | `base64 my-extensions.jks` (see below) |
| `KEY_STORE_PASSWORD` | The store password you chose            |
| `KEY_ALIAS`          | The alias you chose (`my-ext-key`)      |
| `KEY_PASSWORD`       | The key password you chose              |

To encode the keystore on macOS/Linux:
```bash
base64 -i my-extensions.jks | pbcopy   # macOS — copies to clipboard
base64 my-extensions.jks              # Linux — paste the output
```

---

## Adding a new extension

1. **Copy the template folder:**
   ```
   src/en/MySite/  →  src/en/NewSite/
   ```

2. **Edit `src/en/NewSite/build.gradle`** — 5 lines at the top:
   ```groovy
   extName       = 'NewSite'
   pkgNameSuffix = 'en.newsite'   // must be globally unique
   extClass      = '.NewSite'
   extVersionCode = 1
   isNsfw        = false
   ```

3. **Rename the Kotlin file and class:**
   ```
   src/en/NewSite/src/eu/kanade/tachiyomi/extension/en/newsite/NewSite.kt
   ```
   Update the `package` line and class name to match.

4. **Edit the CSS selectors** — every line marked `// ← EDIT` in `MySite.kt`.

5. **Push to `main`** — Actions builds, signs, and deploys automatically.

---

## Adding the repo to Aniyomi

After the first successful build, go to:

```
Aniyomi → Settings → Browse → Extension repos → +
```

Enter:
```
https://<your-username>.github.io/<your-repo-name>/index.min.json
```

Your extensions will appear in **Browse → Extensions** filtered by language.

---

## Local testing (optional)

You don't need Android Studio, but you do need a JDK:

```bash
# Debug build (uses ~/.android/debug.keystore automatically)
./gradlew :src:en:MySite:assembleDebug

# Install directly to a connected device/emulator
adb install -r src/en/MySite/build/outputs/apk/debug/*.apk
```

---

## Repo structure

```
.
├── .github/workflows/build.yml   # ← The entire CI/CD pipeline
├── common.gradle                 # Shared build config — rarely touch this
├── build.gradle                  # Root Gradle config
├── settings.gradle               # Auto-discovers extension modules
├── scripts/
│   └── generate_index.py         # Builds index.min.json from APK filenames
└── src/
    └── en/
        └── MySite/               # One folder per extension
            ├── build.gradle      # ← Edit these 5 lines per extension
            ├── AndroidManifest.xml
            └── src/eu/kanade/tachiyomi/extension/en/mysite/
                └── MySite.kt     # ← Main scraper logic
```

---

## Bumping a version

Edit `extVersionCode` in `build.gradle`, push — the Action re-deploys 
the new APK and updates `index.min.json`.  Aniyomi will show the update 
banner automatically.
