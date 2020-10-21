package com.example.verifonevx990app.appupdate

import android.os.AsyncTask
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateDownloadManager(private var onDownloadCompleteListener: OnDownloadCompleteListener) :
    AsyncTask<String, Int, String>() {

    private val url = "https://testcallbh.bonushub.co.in/app/verfione(bonushub).zip"
    private val appName = "BonusHub.apk"

    override fun doInBackground(vararg params: String?): String? {
        var input: InputStream? = null
        var output: OutputStream? = null
        val PATH = Environment.getExternalStorageDirectory().path + "/Download/"
        val connection: HttpURLConnection
        val disposer: () -> Unit = {
            try {
                output?.close()
                input?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        try {
            val url = URL(url)
            connection = url.openConnection() as HttpURLConnection

            connection.readTimeout = 100000
            connection.connectTimeout = 150000
            connection.requestMethod = "GET"
            connection.connect()

            val fileLength = connection.contentLength
            val file = File(PATH)

            file.mkdirs()
            val outputFile = File(file, appName)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            input = connection.inputStream
            output = FileOutputStream(outputFile)
            val data = ByteArray(1024)
            var total: Long = 0
            //var count: Int
            while (true) {
                val length = input.read(data)
                total += length.toLong()
                if (length <= 0)
                    break

                publishProgress((total * 100 / fileLength).toInt())
                output.write(data, 0, length)
            }
            return PATH
        } catch (e: Exception) {
            e.printStackTrace()
            onDownloadCompleteListener.onError(e.message ?: "")
            return ""
        }

    }

    override fun onCancelled() {
        super.onCancelled()
        onDownloadCompleteListener.onDownloadComplete("", appName)
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        if (result?.isNotEmpty() == true) {
            onDownloadCompleteListener.onDownloadComplete(result, appName)
        }
    }
}

interface OnDownloadCompleteListener {
    fun onDownloadComplete(path: String, appName: String)
    fun onError(msg: String)
}