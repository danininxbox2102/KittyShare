import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const android = path.join(root, 'android');
if (!fs.existsSync(path.join(android, 'app', 'build.gradle'))) {
  console.error('No Capacitor Android project. First run: npx cap add android');
  process.exit(1);
}
const read = p => fs.readFileSync(p, 'utf8');
const write = (p, data) => fs.writeFileSync(p, data);
const modify = (p, change) => write(p, change(read(p)));
const destJava = path.join(android, 'app', 'src', 'main', 'java', 'ru', 'localshare', 'mobile');
fs.mkdirSync(destJava, { recursive: true });
for (const source of fs.readdirSync(path.join(root, 'native', 'java', 'ru', 'localshare', 'mobile'))) {
  fs.copyFileSync(path.join(root, 'native', 'java', 'ru', 'localshare', 'mobile', source), path.join(destJava, source));
}
const oldMainActivity = path.join(destJava, 'MainActivity.java');
if (fs.existsSync(oldMainActivity)) fs.unlinkSync(oldMainActivity);
const resXml = path.join(android, 'app', 'src', 'main', 'res', 'xml');
fs.mkdirSync(resXml, { recursive: true });
fs.copyFileSync(path.join(root, 'native', 'res', 'xml', 'localshare_paths.xml'), path.join(resXml, 'localshare_paths.xml'));
const assets = path.join(android, 'app', 'src', 'main', 'assets', 'remote');
fs.mkdirSync(assets, { recursive: true });
fs.copyFileSync(path.join(root, 'native', 'assets', 'remote', 'index.html'), path.join(assets, 'index.html'));

const rootGradle = path.join(android, 'build.gradle');
modify(rootGradle, source => {
  if (!source.includes('org.jetbrains.kotlin:kotlin-gradle-plugin')) {
    const marker = /classpath\s+['"]com\.android\.tools\.build:gradle:[^'\"]+['"]/;
    if (!marker.test(source)) throw Error('Unexpected root build.gradle layout: missing Android Gradle plugin classpath');
    source = source.replace(marker, match => `${match}\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20'`);
  }
  return source;
});
const appGradle = path.join(android, 'app', 'build.gradle');
modify(appGradle, source => {
  if (!source.includes("apply plugin: 'org.jetbrains.kotlin.android'")) {
    const marker = /apply plugin:\s*['"]com\.android\.application['"]/;
    if (!marker.test(source)) throw Error('Unexpected app/build.gradle layout: missing app plugin');
    source = source.replace(marker, match => `${match}\napply plugin: 'org.jetbrains.kotlin.android'`);
  }
  if (!source.includes("jvmTarget = '21'")) {
    const marker = /android\s*\{/;
    if (!marker.test(source)) throw Error('Unexpected app/build.gradle layout: no android block');
    source = source.replace(marker, match => `${match}\n    kotlinOptions { jvmTarget = '21' }`);
  }
  if (!source.includes('org.nanohttpd:nanohttpd:2.3.1')) {
    const marker = /dependencies\s*\{/;
    if (!marker.test(source)) throw Error('Unexpected app/build.gradle layout: no dependencies block');
    source = source.replace(marker, match => `${match}\n    implementation 'org.nanohttpd:nanohttpd:2.3.1'`);
  }
  return source;
});
const manifestFile = path.join(android, 'app', 'src', 'main', 'AndroidManifest.xml');
modify(manifestFile, source => {
  const permissions = [
    'android.permission.INTERNET',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE',
    'android.permission.CHANGE_NETWORK_STATE'
  ];
  let toAdd = '';
  for (const permission of permissions) {
    if (!source.includes(`android:name="${permission}"`)) {
      toAdd += `    <uses-permission android:name="${permission}" />\n`;
    }
  }
  source = source.replace(/\s*<application\b/, `\n${toAdd}    <application`);
  if (!source.includes('android:name=".LocalShareService"')) {
    source = source.replace('</application>', `    <service
            android:name=".LocalShareService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="\${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/localshare_paths" />
        </provider>
    </application>`);
  }
  return source;
});
console.log('Native Kotlin HTTP server, foreground service, FileProvider and Gradle configuration installed.');
