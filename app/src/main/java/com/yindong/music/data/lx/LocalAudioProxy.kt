package com.yindong.music.data.lx

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalAudioProxy(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "LocalAudioProxy"
    }

    data class ProxySession(
        val url: String,
        val headers: Map<String, String>,
    )

    private var serverSocket: ServerSocket? = null
    private var port: Int = 0
    private var running = false
    private val sessions = ConcurrentHashMap<String, ProxySession>()

    fun start(): Boolean {
        if (running) return true
        return try {
            serverSocket = ServerSocket(0)
            port = serverSocket!!.localPort
            running = true
            Thread({ acceptLoop() }, "AudioProxy").start()
            Log.d(TAG, "proxy started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "proxy start failed: ${e.message}")
            false
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        sessions.clear()
        port = 0
    }

    fun isRunning(): Boolean = running && port > 0

    fun register(url: String, headers: Map<String, String>): String {
        val id = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        sessions[id] = ProxySession(url, headers)
        return "http://127.0.0.1:$port/$id"
    }

    fun unregister(proxyUrl: String) {
        try {
            val path = URI(proxyUrl).path.removePrefix("/")
            sessions.remove(path)
        } catch (_: Exception) {}
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept() ?: break
                socket.soTimeout = 30000
                Thread({ handleConnection(socket) }, "ProxyConn").start()
            } catch (e: Exception) {
                if (running) Log.e(TAG, "accept error: ${e.message}")
                break
            }
        }
    }

    private fun handleConnection(socket: java.net.Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1].removePrefix("/")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val sessionId = path.substringBefore("?").substringBefore("/")
            val session = sessions[sessionId]
            if (session == null) {
                sendError(output, 404, "Session not found")
                return
            }

            val rangeHeader = headers["Range"]

            val requestBuilder = Request.Builder()
                .url(session.url)
                .method(method, null)

            for ((k, v) in session.headers) {
                requestBuilder.header(k, v)
            }

            if (rangeHeader != null) {
                requestBuilder.header("Range", rangeHeader)
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            val response = call.execute()

            val statusCode = response.code
            val respHeaders = response.headers

            val sb = StringBuilder()
            if (statusCode == 206) {
                sb.append("HTTP/1.1 206 Partial Content\r\n")
            } else {
                sb.append("HTTP/1.1 $statusCode OK\r\n")
            }

            val contentType = respHeaders["Content-Type"]
            if (contentType != null) {
                sb.append("Content-Type: $contentType\r\n")
            }

            val contentLength = respHeaders["Content-Length"]
            if (contentLength != null) {
                sb.append("Content-Length: $contentLength\r\n")
            }

            val contentRange = respHeaders["Content-Range"]
            if (contentRange != null) {
                sb.append("Content-Range: $contentRange\r\n")
            }

            val acceptRanges = respHeaders["Accept-Ranges"]
            if (acceptRanges != null) {
                sb.append("Accept-Ranges: $acceptRanges\r\n")
            }

            sb.append("Connection: close\r\n")
            sb.append("\r\n")

            output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            output.flush()

            response.body?.byteStream()?.use { bodyStream ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (bodyStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            if (running) Log.d(TAG, "connection error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendError(output: OutputStream, code: Int, msg: String) {
        val body = "{\"error\":\"$msg\"}"
        val resp = "HTTP/1.1 $code $msg\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
        try {
            output.write(resp.toByteArray())
            output.flush()
        } catch (_: Exception) {}
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isNotEmpty()) sb.toString() else null
            if (b == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) return sb.toString()
                sb.append(b.toChar())
                if (next != -1) sb.append(next.toChar())
                continue
            }
            if (b == '\n'.code) return sb.toString()
            sb.append(b.toChar())
        }
    }
}
