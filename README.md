# LocalShare — Vue 3 + Capacitor + native Android HTTP server

An Android app that turns the **phone itself** into a LAN file server. No desktop backend, no cloud and no Node.js process on Android.

## Быстрый старт (русский)

Это **готовый исходный проект**, а не APK: нативный сервер реализован на Kotlin, интерфейс приложения — на Vue 3, вторая веб-страница встроена в Android-приложение. Сборка на реальном смартфоне ещё не проверена.

1. Установи **Node.js 22+** и **Android Studio 2025.2.1+** с Android SDK 36.
2. В каталоге проекта выполни:

   ```bash
   npm install
   npm run android:init
   npm run android:open
   ```

3. Подключи смартфон по USB, разреши отладку и нажми **Run** в Android Studio. Для ручной установки создай debug APK через **Build → Build APK(s)**.
4. На телефоне добавь файлы в приложение, нажми **Запустить сервер**. На ПК, подключённом к той же сети Wi-Fi, открой показанный адрес `http://IP_ТЕЛЕФОНА:8080`.
5. Введи логин `local` и пароль, который показывает приложение. Теперь можно скачивать и загружать файлы по локальной сети.

После изменения интерфейса или Kotlin-исходников выполняй `npm run android:sync`. Для проверки кода веб-интерфейса без Android можно запустить `npm run dev` — HTTP-сервер в обычном браузере не работает.

**Ограничения:** по HTTP логин, пароль и файлы передаются без шифрования; используй только доверенную локальную сеть. Веб-загрузка ограничена 512 МиБ на файл. Файлы находятся в каталоге приложения `Android/data/.../files/Shared` и могут удалиться при деинсталляции. На Samsung иногда нужно отключить ограничения батареи для стабильной фоновой работы сервера.

---

## Features

- Start/stop the HTTP server (TCP **8080**, bound to `0.0.0.0`).
- Android foreground service: keeps serving files after switching away from the app, with a notification and a **Stop** action.
- Import multiple phone files with the Android system document picker (Storage Access Framework).
- Browse, open, share and delete imported files directly in the mobile Vue interface.
- Browse, upload, download and delete files in a desktop/mobile browser at `http://PHONE_IP:8080`.
- HTTP Basic authentication (username `local`, randomly generated 16-character password stored in local preferences; can be rotated).
- Incoming transfers are copied in streams into temporary files and renamed on completion. Existing files are never overwritten (duplicate filenames get `(2)`, `(3)` etc.).
- Files can be nested inside the shared directory; nested names are visible in the file list. Uploads from the browser go to the root directory.
- File-path traversal prevention, same-origin check for write operations, password check for all endpoints, and web page escaping for user-controlled filenames.

## Requirements

- Node.js **22+** and npm.
- Android Studio **2025.2.1+**, Android SDK Platform **36**, JDK from Android Studio.
- Android device running API 24+ (Android 7+) with Wi-Fi/LAN support.
- An Internet connection on the *development machine* for the initial `npm install` and Gradle downloads. **No Internet is needed for file sharing**.

## Build the Android app

```bash
npm install
npm run android:init
npm run android:open
```

In Android Studio: wait for Gradle Sync, connect your phone with USB debugging, select it and click **Run**. Or build an APK using **Build > Build APK(s)**. The debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk` after a successful build.

Further Vue UI changes:

```bash
npm run android:sync
```

For a fresh Android project, `android:init` runs `cap add android`, injects the Kotlin plugin/server and manifest entries, and runs `cap sync`.

**Do not** run `android:init` a second time when `android/` already exists; use `android:sync` instead. The `native/` directory contains the canonical native sources; `scripts/install-native.mjs` copies them into the generated Android project. After modifying Kotlin code, rerun `npm run android:sync`.

### Run the Vue UI in a browser (nonfunctional preview)

```bash
npm install
npm run dev
```

The UI can be previewed in a browser, but **only an Android build hosts the HTTP server**.

## How to use

1. Connect the Android phone and a computer to the **same trusted Wi-Fi/LAN** (or a phone hotspot that allows client-to-host access).
2. Open LocalShare on the phone. Choose **Add files** and import the documents you wish to share.
3. Click **Start server** and check the URL shown in the app (for example `http://192.168.1.42:8080`).
4. On a computer, open the URL in a web browser. Authenticate using username `local` and the password displayed in the app.
5. Upload by drag-and-drop, download by clicking a file, or delete through the remote web interface.
6. Click **Stop server** in the app or notification to stop sharing.

If the browser cannot connect: check that the Wi-Fi router has no **AP/client isolation**, that both devices are on the same subnet, and that your phone's battery management is not stopping the foreground service. The URL shown is derived from the phone's network interfaces, not an invented fixed IP address.

## Storage

Files live in the app-specific directory:

```
/storage/emulated/0/Android/data/ru.localshare.mobile/files/Shared/
```

The exact path is returned by the native app. Some current Android file managers cannot directly browse `Android/data`; importing and exporting through the app still works. Android may delete this directory when the app is **uninstalled** — export important files beforehand. This version does **not** expose the entire phone filesystem.

## HTTP API

All routes require HTTP Basic auth (`local:<password>`).

| Method | Route | Function |
| --- | --- | --- |
| GET | `/` | Browser interface |
| GET | `/api/files` | JSON list of files |
| GET | `/download?name=<path>` | Stream file to browser |
| POST | `/api/upload` | Multipart upload (field `file`, **one file per request**) |
| DELETE | `/api/files?name=<path>` | Delete a file |

Example with curl (on LAN):

```bash
curl -u 'local:YOUR_PASSWORD' 'http://PHONE_IP:8080/api/files'
curl -u 'local:YOUR_PASSWORD' -F 'file=@document.pdf' 'http://PHONE_IP:8080/api/upload'
curl -u 'local:YOUR_PASSWORD' -OJ 'http://PHONE_IP:8080/download?name=document.pdf'
```

**Upload limit: 512 MiB per HTTP request** (NanoHTTPD multipart processing requires temporary storage and can use memory-mapping). The Android file picker uses streamed copying, but browser uploads are subject to that limit. For very large media, a future chunked/streaming upload protocol is recommended.

## Security & Android constraints

- This is an **HTTP** server, not HTTPS. Basic-auth credentials and file content are transmitted **without encryption**. Use only trusted LANs; **never forward TCP port 8080 to the Internet**. HTTP is not secure on public Wi-Fi.
- Browser access is protected by a randomly generated password. Rotating it invalidates previous credentials; browsers may require a new private/incognito window if Basic-auth credentials are cached.
- Android's foreground service may be stopped by the OS, user action or manufacturer battery optimization. It is not a guarantee of indefinite uptime.
- The connected-device foreground service is declared in the Android manifest with the required Android permissions. Capacitor 8 targets API 36; when upgrading to target SDK 37+, request the new Android 17 `ACCESS_LOCAL_NETWORK` runtime permission before accepting LAN sockets.
- The prototype hasn't been built on a physical Android device within the delivery environment. Run and integration-test it on the phone before relying on it for important data.

## Project layout

- `src/` — Vue mobile interface (Capacitor WebView)
- `native/java/ru/localshare/mobile/` — Kotlin native plugin, HTTP server, foreground service, file storage
- `native/assets/remote/index.html` — browser-based file manager hosted by the phone
- `native/res/xml/localshare_paths.xml` — Android FileProvider paths
- `scripts/install-native.mjs` — installs native code into the generated Capacitor Android project
