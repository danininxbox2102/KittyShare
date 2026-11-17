package ru.localshare.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.PluginMethod
import androidx.activity.result.ActivityResult
import java.net.Inet4Address
import java.net.NetworkInterface

@CapacitorPlugin(name = "LocalShare")
class LocalSharePlugin : Plugin() {
    private fun lanAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (Regex("^(wlan|ap|swlan|eth|en|bridge)").containsMatchIn(it.name)) 0 else 1 }
            for (network in interfaces) {
                // Do not advertise cellular or tunnel addresses as LAN URLs.
                if (!Regex("^(wlan|ap|swlan|eth|en|bridge)").containsMatchIn(network.name)) continue
                for (address in network.inetAddresses.toList()) {
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        addresses += "http://${address.hostAddress}:${LocalShareService.PORT}"
                    }
                }
            }
        } catch (_: Exception) { }
        return addresses.distinct()
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        try {
            val list = SharedFiles.list(context)
            val result = JSObject().apply {
                put("running", LocalShareService.running)
                put("starting", LocalShareService.starting)
                put("port", LocalShareService.PORT)
                put("addresses", com.getcapacitor.JSArray().also { arr -> lanAddresses().forEach { arr.put(it) } })
                put("directory", SharedFiles.root(context).absolutePath)
                put("count", list.size)
                put("totalBytes", list.sumOf { it.length() })
                put("code", LocalShareService.secret(context))
                put("error", LocalShareService.lastError)
            }
            call.resolve(result)
        } catch (e: Exception) { call.reject(e.message ?: "Status error") }
    }

    @PluginMethod
    fun startServer(call: PluginCall) {
        try {
            val intent = Intent(context, LocalShareService::class.java).setAction(LocalShareService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
            call.resolve()
        } catch (e: Exception) { call.reject(e.message ?: "Could not start service") }
    }

    @PluginMethod
    fun stopServer(call: PluginCall) {
        try {
            // Called from the visible Activity, so this explicit service action is allowed.
            context.startService(Intent(context, LocalShareService::class.java).setAction(LocalShareService.ACTION_STOP))
            call.resolve()
        } catch (e: Exception) { call.reject(e.message ?: "Could not stop service") }
    }

    @PluginMethod
    fun listFiles(call: PluginCall) {
        try { call.resolve(JSObject().put("files", SharedFiles.fileJson(context))) }
        catch (e: Exception) { call.reject(e.message ?: "Could not list files") }
    }

    @PluginMethod
    fun importFiles(call: PluginCall) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(call, intent, "onPickedFiles")
    }

    @ActivityCallback
    private fun onPickedFiles(call: PluginCall, result: ActivityResult) {
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            call.resolve(JSObject().put("count", 0))
            return
        }
        val data = result.data
        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        if (uris.isEmpty()) data?.data?.let { uris.add(it) }
        Thread {
            try {
                var count = 0
                for (uri in uris) { SharedFiles.importUri(context, uri); count++ }
                call.resolve(JSObject().put("count", count))
            } catch (e: Exception) { call.reject(e.message ?: "File import failed") }
        }.start()
    }

    private fun fileUri(name: String): Pair<Uri, String> {
        val file = SharedFiles.getFile(context, name)
        return Pair(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            SharedFiles.mime(file)
        )
    }

    @PluginMethod
    fun openFile(call: PluginCall) {
        val name = call.getString("name") ?: return call.reject("Missing name")
        try {
            val (uri, mime) = fileUri(name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
            call.resolve()
        } catch (e: Exception) { call.reject(e.message ?: "Cannot open this file") }
    }

    @PluginMethod
    fun shareFile(call: PluginCall) {
        val name = call.getString("name") ?: return call.reject("Missing name")
        try {
            val (uri, mime) = fileUri(name)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Поделиться файлом"))
            call.resolve()
        } catch (e: Exception) { call.reject(e.message ?: "Cannot share this file") }
    }

    @PluginMethod
    fun deleteFile(call: PluginCall) {
        val name = call.getString("name") ?: return call.reject("Missing name")
        try { SharedFiles.delete(context, name); call.resolve() }
        catch (e: Exception) { call.reject(e.message ?: "Delete failed") }
    }

    @PluginMethod
    fun regenerateCode(call: PluginCall) {
        try { call.resolve(JSObject().put("code", LocalShareService.rotateSecret(context))) }
        catch (e: Exception) { call.reject(e.message ?: "Could not change code") }
    }

    @PluginMethod
    fun copyText(call: PluginCall) {
        val text = call.getString("text") ?: return call.reject("Missing text")
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("LocalShare", text))
        call.resolve()
    }
}
