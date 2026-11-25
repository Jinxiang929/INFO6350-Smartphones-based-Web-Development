package com.example.qrcode.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class QrRepository(private val contentResolver: ContentResolver) {
    suspend fun generateQr(text: String): Bitmap = withContext(Dispatchers.IO) {
        val connection = openPost("/qr/generate")

        try {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("text", text).toString()
            connection.outputStream.use { it.write(body.toByteArray()) }

            requireOk(connection)
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?: throw IOException("The server did not return a readable image.")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun decodeQr(imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        val connection = openPost("/qr/decode")
        val boundary = "qr-" + UUID.randomUUID()
        val header = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"upload\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        val footer = "\r\n--$boundary--\r\n"

        try {
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setChunkedStreamingMode(0)

            val imageStream = try {
                contentResolver.openInputStream(imageUri)
            } catch (error: SecurityException) {
                throw IOException("Could not access the selected image.", error)
            } ?: throw IOException("Could not open the selected image.")

            imageStream.use { image ->
                connection.outputStream.buffered().use { output ->
                    output.write(header.toByteArray())
                    image.copyTo(output)
                    output.write(footer.toByteArray())
                }
            }

            requireOk(connection)
            val body = connection.inputStream.bufferedReader().use { it.readText() }

            try {
                val texts = JSONObject(body).getJSONArray("texts")
                List(texts.length()) { index -> texts.getString(index) }
            } catch (error: JSONException) {
                throw IOException("The server returned an unreadable scan result.", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openPost(path: String): HttpURLConnection =
        (URL("http://127.0.0.1:5060$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 10_000
            doOutput = true
        }

    private fun requireOk(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_OK) return

        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
        val message = try {
            errorBody?.let { JSONObject(it).optString("error") }
        } catch (_: JSONException) {
            null
        }

        throw IOException(message?.takeIf { it.isNotBlank() } ?: "Server error ($status).")
    }
}