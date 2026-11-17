<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import {
  ArrowDownToLine, ArrowUpFromLine, Check, ChevronRight, CircleHelp,
  Clipboard, Copy, FileArchive, FileImage, FileText, FileVideo,
  File as FileIcon, FolderOpen, HardDrive, KeyRound, LoaderCircle,
  LockKeyhole, MoreVertical, RefreshCw, Search, Server, Share2,
  ShieldCheck, Smartphone, Trash2, Wifi, WifiOff, X,
} from 'lucide-vue-next';
import { isAndroid, LocalShare, type ServerStatus, type SharedFile } from './native';

type Page = 'home' | 'files';
const page = ref<Page>('home');
const status = ref<ServerStatus | null>(null);
const files = ref<SharedFile[]>([]);
const busy = ref(false);
const loading = ref(false);
const showCode = ref(false);
const query = ref('');
const toast = ref('');
const openMenu = ref<string | null>(null);
let interval: ReturnType<typeof setInterval> | undefined;
let toastTimeout: ReturnType<typeof setTimeout> | undefined;
const address = computed(() => status.value?.addresses[0] || '');
const filteredFiles = computed(() => files.value.filter(f => f.name.toLowerCase().includes(query.value.trim().toLowerCase())));

function notice(message: string) {
  toast.value = message;
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => (toast.value = ''), 4000);
}
function formatSize(value: number): string {
  if (value < 1024) return `${value} Б`;
  const units = ['КБ', 'МБ', 'ГБ', 'ТБ'];
  let n = value; let i = -1;
  do { n /= 1024; i++; } while (n >= 1024 && i < units.length - 1);
  return `${n.toLocaleString('ru-RU', { maximumFractionDigits: 1 })} ${units[i]}`;
}
function formatDate(value: number) {
  return new Date(value).toLocaleString('ru-RU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}
function category(name: string) {
  const ext = name.split('.').pop()?.toLowerCase();
  if (['jpg','png','jpeg','webp','gif','heic','svg'].includes(ext || '')) return FileImage;
  if (['mp4','mov','webm','mkv','avi'].includes(ext || '')) return FileVideo;
  if (['zip','rar','7z','tar','gz','apk'].includes(ext || '')) return FileArchive;
  if (['pdf','txt','md','docx','doc','json','csv'].includes(ext || '')) return FileText;
  return FileIcon;
}
async function refresh(silent = false) {
  if (!isAndroid) return;
  if (!silent) loading.value = true;
  try {
    const [next, listed] = await Promise.all([LocalShare.getStatus(), LocalShare.listFiles()]);
    status.value = next;
    files.value = listed.files;
  } catch (e) {
    if (!silent) notice(`Не удалось обновить данные: ${String(e)}`);
  } finally { loading.value = false; }
}
async function toggleServer() {
  if (!isAndroid || busy.value) return;
  busy.value = true;
  try {
    if (status.value?.running || status.value?.starting) await LocalShare.stopServer();
    else await LocalShare.startServer();
    await refresh(true);
    if (!status.value?.running && !status.value?.starting && status.value?.error) notice(status.value.error);
  } catch (e) { notice(`Ошибка сервера: ${String(e)}`); }
  finally { busy.value = false; }
}
async function addFiles() {
  if (!isAndroid || busy.value) return;
  busy.value = true;
  try {
    const result = await LocalShare.importFiles();
    if (result.count) notice(`Добавлено файлов: ${result.count}`);
    await refresh(true);
  } catch (e) { if (!String(e).toLowerCase().includes('cancel')) notice(`Ошибка импорта: ${String(e)}`); }
  finally { busy.value = false; }
}
async function deleteSelected(file: SharedFile) {
  openMenu.value = null;
  if (!confirm(`Удалить «${file.name}»?`)) return;
  try { await LocalShare.deleteFile({ name: file.name }); await refresh(true); notice('Файл удалён'); }
  catch (e) { notice(`Ошибка удаления: ${String(e)}`); }
}
async function openSelected(file: SharedFile) {
  openMenu.value = null;
  try { await LocalShare.openFile({ name: file.name }); }
  catch (e) { notice(`Не удалось открыть файл: ${String(e)}`); }
}
async function shareSelected(file: SharedFile) {
  openMenu.value = null;
  try { await LocalShare.shareFile({ name: file.name }); }
  catch (e) { notice(`Не удалось поделиться: ${String(e)}`); }
}
async function copy(value: string) {
  if (!value) return;
  try { await LocalShare.copyText({ text: value }); notice('Скопировано'); }
  catch { notice('Не удалось скопировать'); }
}
async function rotateCode() {
  if (!confirm('Изменить код доступа? Подключённые устройства должны будут авторизоваться заново.')) return;
  try { await LocalShare.regenerateCode(); await refresh(true); showCode.value = true; notice('Код доступа изменён'); }
  catch (e) { notice(String(e)); }
}
onMounted(() => { refresh(); interval = setInterval(() => refresh(true), 3500); });
onUnmounted(() => { if (interval) clearInterval(interval); if (toastTimeout) clearTimeout(toastTimeout); });
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand-mark"><Share2 :size="21" :stroke-width="2.4" /></div>
      <div class="brand-copy"><strong>LocalShare</strong><span>Локальное хранилище</span></div>
      <button class="icon-button" aria-label="Обновить" @click="refresh()"><RefreshCw :size="19" :class="{ 'spin': loading }" /></button>
    </header>

    <main>
      <section v-if="page === 'home'" class="page-content">
        <div class="heading-block">
          <div class="eyebrow">ТВОЯ СЕТЬ · ТВОИ ФАЙЛЫ</div>
          <h1>Файлы без<br /><span>лишних проводов.</span></h1>
          <p>Передавай файлы между смартфоном и любым устройством в одной локальной сети.</p>
        </div>

        <div class="server-panel">
          <div class="panel-top">
            <div class="server-icon"><Server :size="24" /></div>
            <span class="state-pill" :class="{ active: status?.running, pending: status?.starting }">
              <span class="state-dot"></span>
              {{ status?.running ? 'В сети' : status?.starting ? 'Запускается' : 'Выключен' }}
            </span>
          </div>
          <h2>HTTP-сервер</h2>
          <p class="panel-caption">Файлы доступны по IP-адресу смартфона</p>
          <div class="address-box">
            <div class="address-icon"><Wifi v-if="address" :size="19" /><WifiOff v-else :size="19" /></div>
            <div class="address-content"><span>АДРЕС В ЛОКАЛЬНОЙ СЕТИ</span><strong>{{ address || 'Нет Wi-Fi / LAN адреса' }}</strong></div>
            <button class="small-action" aria-label="Скопировать адрес" :disabled="!address" @click="copy(address)"><Copy :size="17" /></button>
          </div>
          <button class="main-action" :class="{ stop: status?.running || status?.starting }" :disabled="busy || !isAndroid" @click="toggleServer">
            <LoaderCircle v-if="busy" :size="19" class="spin" />
            <Server v-else :size="19" />
            {{ status?.running || status?.starting ? 'Остановить сервер' : 'Запустить сервер' }}
            <ChevronRight :size="19" />
          </button>
          <div v-if="status?.error" class="error-note">{{ status.error }}</div>
          <div v-if="!isAndroid" class="error-note">В браузере доступен только просмотр интерфейса. Сам HTTP-сервер работает в Android-приложении.</div>
        </div>

        <div class="section-heading"><h3>Хранилище</h3><button class="text-action" @click="page = 'files'">Все файлы <ChevronRight :size="16" /></button></div>
        <div class="stats-row">
          <div class="stat-card"><div class="stat-icon purple"><FolderOpen :size="19" /></div><strong>{{ status?.count ?? 0 }}</strong><span>Файлов</span></div>
          <div class="stat-card"><div class="stat-icon blue"><HardDrive :size="19" /></div><strong>{{ formatSize(status?.totalBytes ?? 0) }}</strong><span>Занято</span></div>
        </div>

        <div class="section-heading"><h3>Быстрые действия</h3></div>
        <button class="quick-action" :disabled="!isAndroid || busy" @click="addFiles"><div class="quick-icon"><ArrowUpFromLine :size="22" /></div><div><strong>Добавить файлы</strong><span>Выбрать из памяти телефона</span></div><ChevronRight :size="19" class="muted-icon" /></button>
        <div class="security-row"><ShieldCheck :size="20" /><span>Доступ по коду. Файлы не отправляются в облако.</span></div>
      </section>

      <section v-else class="page-content files-page">
        <div class="files-heading"><div><div class="eyebrow">ЛОКАЛЬНОЕ ХРАНИЛИЩЕ</div><h1>Мои файлы</h1></div><button class="circle-add" aria-label="Добавить файлы" :disabled="busy" @click="addFiles"><ArrowUpFromLine :size="21" /></button></div>
        <div class="search-box"><Search :size="19" /><input v-model="query" placeholder="Поиск файлов" autocomplete="off" /></div>
        <div class="files-tools"><span>{{ filteredFiles.length }} файлов · {{ formatSize(status?.totalBytes ?? 0) }}</span><button class="icon-button" aria-label="Обновить файлы" @click="refresh()"><RefreshCw :size="17" /></button></div>
        <div v-if="!filteredFiles.length" class="empty-state"><FolderOpen :size="38" /><h3>{{ files.length ? 'Ничего не найдено' : 'Здесь пока пусто' }}</h3><p>Добавь файлы с телефона или загрузи их через браузер с другого устройства.</p><button class="secondary-action" @click="addFiles">Добавить файлы</button></div>
        <div v-else class="file-list">
          <div v-for="file in filteredFiles" :key="file.name" class="file-row">
            <button class="file-main" @click="openSelected(file)"><div class="file-type"><component :is="category(file.name)" :size="22" /></div><div class="file-details"><strong :title="file.name">{{ file.name }}</strong><span>{{ formatSize(file.size) }} · {{ formatDate(file.modified) }}</span></div></button>
            <button class="file-menu-trigger" aria-label="Действия с файлом" @click="openMenu = openMenu === file.name ? null : file.name"><MoreVertical :size="21" /></button>
            <div v-if="openMenu === file.name" class="file-menu"><button @click="shareSelected(file)"><Share2 :size="17" /> Поделиться</button><button @click="deleteSelected(file)"><Trash2 :size="17" /> Удалить</button></div>
          </div>
        </div>
      </section>

      <section class="settings-panel" v-if="page === 'home' && status">
        <div class="section-heading"><h3>Защита соединения</h3><LockKeyhole :size="18" /></div>
        <p>На другом устройстве открой адрес сервера. Браузер запросит логин и пароль.</p>
        <div class="credential-label">Логин</div><div class="credential-box"><code>local</code><button @click="copy('local')"><Copy :size="17" /></button></div>
        <div class="credential-label">Пароль</div><div class="credential-box"><code>{{ showCode ? status.code : '•••• •••• •••• ••••' }}</code><button @click="showCode = !showCode">{{ showCode ? 'Скрыть' : 'Показать' }}</button><button @click="copy(status.code)"><Copy :size="17" /></button></div>
        <button class="reset-link" @click="rotateCode"><KeyRound :size="16" /> Сменить пароль</button>
        <p class="footnote">Подключайся только в доверенной сети. Обычный HTTP не шифрует передаваемые данные.</p>
      </section>
    </main>

    <nav class="bottom-nav"><button :class="{ selected: page === 'home' }" @click="page = 'home'"><Server :size="21" /><span>Сервер</span></button><button :class="{ selected: page === 'files' }" @click="page = 'files'; refresh(true)"><FolderOpen :size="21" /><span>Файлы</span></button></nav>
    <transition name="toast"><div v-if="toast" class="toast"><Check :size="17" />{{ toast }}<button aria-label="Скрыть" @click="toast = ''"><X :size="16" /></button></div></transition>
  </div>
</template>
