package ru.localshare.mobile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection
import java.util.UUID

/** All files served by HTTP live inside the app-owned Shared folder. */
object SharedFiles {
    private val finalizeLock = Any()

    fun root(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Shared").apply { if (!exists() && !mkdirs()) error("Cannot create Shared folder") }
    }

    fun getFile(context: Context, relativeName: String): File {
        require(relativeName.isNotBlank() && !relativeName.contains('\u0000')) { "Invalid file name" }
        val base = root(context).canonicalFile
        val requested = File(base, relativeName).canonicalFile
        require(requested.path.startsWith(base.path + File.separator) && requested.isFile) { "File not found" }
        return requested
    }

    fun list(context: Context): List<File> {
        val base = root(context)
        return base.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".incoming-") && !it.name.startsWith(".import-") }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    fun relativeName(context: Context, file: File): String = file.relativeTo(root(context)).invariantSeparatorsPath

    fun fileJson(context: Context): JSONArray {
        val result = JSONArray()
        for (file in list(context)) {
            result.put(JSONObject().apply {
                put("name", relativeName(context, file))
                put("size", file.length())
                put("modified", file.lastModified())
                put("mime", mime(file))
            })
        }
        return result
    }

    fun mime(file: File): String = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"

    fun safeName(untrusted: String): String {
        val simple = untrusted.substringAfterLast('/').substringAfterLast('\\').trim()
            .replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), "_")
            .trim('.', ' ')
            .take(180)
        return simple.ifBlank { "file" }
    }

    private fun uniqueDestination(directory: File, filename: String): File {
        val name = safeName(filename)
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = File(directory, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$stem ($index)$ext")
            index++
        }
        return candidate
    }

    /** Copy first, then publish atomically. Never overwrite an existing file. */
    fun importStream(context: Context, originalName: String, input: java.io.InputStream): File {
        val directory = root(context)
        val staging = File(directory, ".import-${UUID.randomUUID()}.part")
        try {
            FileOutputStream(staging).use { out -> input.copyTo(out, 64 * 1024) }
            synchronized(finalizeLock) {
                val dest = uniqueDestination(directory, originalName)
                if (!staging.renameTo(dest)) error("Cannot finalize received file")
                return dest
            }
        } finally { if (staging.exists()) staging.delete() }
    }

    fun importUri(context: Context, uri: Uri): File {
        var title = "file"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) title = cursor.getString(0) ?: title
        }
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected file")
        return input.use { importStream(context, title, it) }
    }

    fun delete(context: Context, relativeName: String) {
        val file = getFile(context, relativeName)
        if (!file.delete()) error("Could not delete file")
    }
}
