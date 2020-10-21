package com.example.verifonevx990app.appupdate

import android.content.Context
import android.net.Uri
import com.example.verifonevx990app.main.PrefConstant
import com.example.verifonevx990app.vxUtils.AppPreference
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class AppUpdateFTPClient(
    private var ftpIPAddress: String,
    private var ftpPort: Int,
    private var ftpUserName: String,
    private var ftpPassword: String,
    private var downloadAppFileName: String,
    var context: Context,
    var appUpdateDownloadCB: (Boolean, Uri?) -> Unit
) {
    private var fileUri: Uri? = null
    private val appName = "BonusHub.apk"

    init {
        startFTPAndDownloadFile()
    }

    //Below method is used to connect and download file from FTP Server:-
    private fun startFTPAndDownloadFile() {
        val client = FTPClient()
        var outputStream: OutputStream? = null
        val downloadedFilePath = File(context.externalCacheDir, appName)

        /* Testing Credentials of FTP App Update:-
        val fileToDownloadPath = "verfione(bonushub).apk"
        val ftpURL = "122.176.84.29"
        val ftpPort = 21
        val ftpUserName = "Admin"
        val ftpPassword = "Bh@ftp"
         */

        val fileToDownloadPath = downloadAppFileName
        val ftpURL = ftpIPAddress
        val ftpPort = ftpPort
        val ftpUserName = ftpUserName
        val ftpPassword = ftpPassword

        //Here we are saving FTP IP Address and PORT for future App update use:-
        AppPreference.saveString(PrefConstant.FTP_IP_ADDRESS.keyName.toString(), ftpIPAddress)
        AppPreference.setIntData(PrefConstant.FTP_IP_PORT.keyName.toString(), ftpPort)
        AppPreference.saveString(PrefConstant.FTP_USER_NAME.keyName.toString(), ftpUserName)
        AppPreference.saveString(PrefConstant.FTP_PASSWORD.keyName.toString(), ftpPassword)
        AppPreference.saveString(PrefConstant.FTP_FILE_NAME.keyName.toString(), downloadAppFileName)

        try {
            downloadedFilePath.createNewFile()
            client.connect(ftpURL, ftpPort)
            client.login(ftpUserName, ftpPassword)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            outputStream = FileOutputStream(downloadedFilePath)
            client.retrieveFile("/$fileToDownloadPath", outputStream)
            fileUri = Uri.fromFile(downloadedFilePath)
            appUpdateDownloadCB(true, Uri.fromFile(downloadedFilePath))
        } catch (e: IOException) {
            e.printStackTrace()
            appUpdateDownloadCB(false, fileUri)
        } finally {
            try {
                outputStream?.close()
                client.logout()
                client.disconnect()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}