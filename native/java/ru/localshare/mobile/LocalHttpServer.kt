package ru.localshare.mobile

import android.content.Context
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.FileInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HashMap

/** A tiny LAN-only file server. HTTP Basic auth is required on EVERY endpoint. */
class LocalHttpServer(
    private val context: Context,
    port: Int
) : NanoHTTPD("0.0.0.0", port) {
    companion object { private const val MAX_UPLOAD_BYTES = 512L * 1024 * 1024 }

    private fun reply(status: Response.Status, body: String, mime: String = "application/json; charset=utf-8"): Response =
        newFixedLengthResponse(status, mime, body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }

    private fun fail(status: Response.Status, message: String): Response =
        reply(status, JSONObject().put("error", message).toString())

    private fun authorized(session: IHTTPSession): Boolean {
        val observed = session.headers["authorization"] ?: return false
        val expected = "Basic " + Base64.encodeToString(
            "local:${LocalShareService.secret(context)}".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP
        )
        return MessageDigest.isEqual(
            observed.toByteArray(StandardCharsets.UTF_8), expected.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun safeOrigin(session: IHTTPSession): Boolean {
        if (session.method != Method.POST && session.method != Method.DELETE && session.method != Method.PUT) return true
        if (session.headers["sec-fetch-site"] == "cross-site") return false
        val origin = session.headers["origin"] ?: return true // non-browser clients
        val host = session.headers["host"] ?: return false
        return origin == "http://$host"
    }

    override fun serve(session: IHTTPSession): Response {
        if (!authorized(session)) {
            return fail(Response.Status.UNAUTHORIZED, "Enter LocalShare login and password").apply {
                addHeader("WWW-Authenticate", "Basic realm=\"LocalShare\", charset=\"UTF-8\"")
            }
        }
        if (!safeOrigin(session)) return fail(Response.Status.FORBIDDEN, "Cross-origin writes are blocked")
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> {
                    val html = context.assets.open("remote/index.html").bufferedReader().use { it.readText() }
                    reply(Response.Status.OK, html, "text/html; charset=utf-8").apply {
                        addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:")
                        addHeader("Referrer-Policy", "no-referrer")
                    }
                }
                session.method == Method.GET && session.uri == "/api/files" -> {
                    reply(Response.Status.OK, JSONObject().put("files", SharedFiles.fileJson(context)).toString())
                }
                session.method == Method.GET && session.uri == "/download" -> {
                    val name = session.parameters["name"]?.firstOrNull() ?: return fail(Response.Status.BAD_REQUEST, "Missing name")
                    val file = SharedFiles.getFile(context, name)
                    val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                    newFixedLengthResponse(Response.Status.OK, SharedFiles.mime(file), FileInputStream(file), file.length()).apply {
                        addHeader("Content-Disposition", "attachment; filename=\"download\"; filename*=UTF-8''$encodedName")
                        addHeader("X-Content-Type-Options", "nosniff")
                    }
                }
                session.method == Method.POST && session.uri == "/api/upload" -> {
                    val length = session.headers["content-length"]?.toLongOrNull()
                        ?: return fail(Response.Status.BAD_REQUEST, "Content-Length required")
                    if (length <= 0 || length > MAX_UPLOAD_BYTES) return fail(Response.Status.BAD_REQUEST, "Maximum upload: 512 MiB")
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val source = files["file"] ?: return fail(Response.Status.BAD_REQUEST, "Multipart field 'file' required")
                    val originalName = session.parameters["file"]?.firstOrNull() ?: "upload"
                    FileInputStream(source).use {
                        val imported = SharedFiles.importStream(context, originalName, it)
                        reply(Response.Status.CREATED, JSONObject().put("name", imported.name).toString())
                    }
                }
                session.method == Method.DELETE && session.uri == "/api/files" -> {
                    val name = session.parameters["name"]?.firstOrNull() ?: return fail(Response.Status.BAD_REQUEST, "Missing name")
                    SharedFiles.delete(context, name)
                    reply(Response.Status.OK, "{\"deleted\":true}")
                }
                else -> fail(Response.Status.NOT_FOUND, "Endpoint not found")
            }
        } catch (e: IllegalArgumentException) {
            fail(Response.Status.BAD_REQUEST, "Invalid file name or file not found")
        } catch (e: Exception) {
            // Never echo filesystem paths or stack traces to remote clients.
            android.util.Log.w("LocalShare", "Request failed", e)
            fail(Response.Status.INTERNAL_ERROR, "Operation failed")
        }
    }
}
