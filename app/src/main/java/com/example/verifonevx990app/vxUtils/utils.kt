package com.example.verifonevx990app.vxUtils

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.main.MainActivity
import com.example.verifonevx990app.realmtables.*
import com.example.verifonevx990app.utils.PaxUtils
import com.example.verifonevx990app.utils.Utility
import com.example.verifonevx990app.utils.printerUtils.PrinterFonts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vfi.smartpos.deviceservice.constdefine.ConstIPBOC
import io.realm.RealmObject
import io.realm.RealmResults
import kotlinx.android.synthetic.main.success_toast.*
import kotlinx.android.synthetic.main.success_toast.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.experimental.and

open class OnTextChange(private val cb: (String) -> Unit) : TextWatcher {

    override fun afterTextChanged(s: Editable?) {
        cb(s.toString())
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }

}


enum class UiAction(val tvalue: Int = 0, val title: String = "") {
    INIT, KEY_EXCHANGE, INIT_WITH_KEY_EXCHANGE, START_SALE(title = "Sale"), SETTLEMENT, APP_UPDATE, PRE_AUTH(
        title = "Pre-Auth"
    ),
    REFUND(title = "Refund"),
    BANK_EMI(title = "Bank EMI"), OFFLINE_SALE(title = "Offline Sale"), CASH_ADVANCE(title = "Cash Advance"), SALE_WITH_CASH(
        title = "Sale With Cash"
    ),
    PRE_AUTH_COMPLETE(title = "Pre Auth Complete")

}


//region=======Logging============
@JvmOverloads
fun logger(tag: String, msg: String, type: String = "d") {
    if (BuildConfig.DEBUG) {
        when (type) {
            "d", "D" -> Log.d(tag, msg)
            "i", "I" -> Log.i(tag, msg)
            "e", "E" -> Log.e(tag, msg)
            "v", "V" -> Log.v(tag, msg)
            else -> Log.i(tag, msg)
        }
    }
}

// For logging
fun logger(tag: String, msg: HashMap<Byte, IsoField>, type: String = "d") {
    if (BuildConfig.DEBUG) {
        for ((k, v) in msg) {
            logger(v.fieldName + "---->>", "$k = ${v.rawData}", type)
        }
    }
}

//region=========File Reading===============

suspend fun readInitFile(callback: suspend (Boolean, String) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        var reader: BufferedReader? = null
        try {
            reader =
                BufferedReader(InputStreamReader(VerifoneApp.appContext.assets.open("init_file.txt")))
            var mLine = reader.readLine()
            while (mLine != null) {
                logger("readInitFile", mLine)
                if (mLine.isNotEmpty()) {
                    val spilter = mLine.split("|")
                    if (spilter.isNotEmpty()) {
                        saveToDB(spilter)
                    }
                }
                // val sb = StringBuilder()
                mLine = reader.readLine()
            }
            GlobalScope.launch(Dispatchers.Main) {
                callback(true, "")
            }
        } catch (ex: Exception) {
            GlobalScope.launch(Dispatchers.Main) {
                logger("readInitFile", ex.message ?: "", "e")
                callback(false, ex.message ?: "")
            }
        } finally {

            reader?.close()
        }
    }

}

suspend fun readInitServer(data: ArrayList<ByteArray>, callback: (Boolean, String) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val filename = "init_file.txt"
            VerifoneApp.appContext.openFileOutput(filename, Context.MODE_PRIVATE).apply {
                for (each in data) write(each)
                flush()
            }.close()

            val fin =
                BufferedReader(InputStreamReader(VerifoneApp.appContext.openFileInput(filename)))

            var line: String? = fin.readLine()

            while (line != null) {
                if (line.isNotEmpty()) {
                    logger("readInitServer", line)
                    val spilter = line.split("|")
                    if (spilter.isNotEmpty()) {
                        if (AppPreference.getIntData("PcNo") <= Integer.parseInt(spilter[0])) {
                            AppPreference.setIntData("PcNo", Integer.parseInt(spilter[0]))
                        }
                        saveToDB(spilter)
                    }
                }
                line = fin.readLine()
            }
            fin.close()
            GlobalScope.launch(Dispatchers.Main) {
                callback(true, "Successful init")
            }
        } catch (ex: Exception) {
            GlobalScope.launch(Dispatchers.Main) {
                callback(false, ex.message ?: "")
            }
        }
    }
}

private val pc2Tables = arrayOf(108, 110, 111, 112, 113, 115, 116)

/**
 * savePcs takes take pc number and table id and as per table id
 * it save largest pc number 1 and 2 in the system.
 * */
private fun savePcs(pcNum: String, table: String) {
    try {
        val tn = table.toInt()
        if (tn in pc2Tables) {
            val ppc = AppPreference.getString(AppPreference.PC_NUMBER_KEY_2).toInt()
            if (pcNum.toInt() > ppc) {
                AppPreference.saveString(AppPreference.PC_NUMBER_KEY_2, pcNum)
            }

        } else {
            val ppc = AppPreference.getString(AppPreference.PC_NUMBER_KEY).toInt()
            if (pcNum.toInt() > ppc) {
                AppPreference.saveString(AppPreference.PC_NUMBER_KEY, pcNum)
            }
        }
    } catch (ex: Exception) {
        try {
            val tn = table.toInt()
            if (tn in pc2Tables) {
                AppPreference.saveString(AppPreference.PC_NUMBER_KEY_2, pcNum)
            } else {
                AppPreference.saveString(AppPreference.PC_NUMBER_KEY, pcNum)
            }
        } catch (ex: Exception) {
        }
    }
}

private fun parseData(table: Any, data: List<String>) {
    val tableClass = table::class.java
    for (e in tableClass.declaredFields) {
        val ann = e.getAnnotation(BHFieldParseIndex::class.java)
        if (ann != null) {
            val index = ann.index
            if (data.size > index) {
                e.isAccessible = true
                e.set(table, data[index])
            }
        }
    }
}

//Save Table in DB:-
fun saveTableInDB(tableInstance: RealmObject) = withRealm {
    it.executeTransaction { TempBatchFileDataTable ->
        TempBatchFileDataTable.insertOrUpdate(
            tableInstance
        )
    }
}

//Delete Batch File Table Data in DB:-
fun deleteBatchTableDataInDB() = withRealm {
    it.executeTransaction { realm ->
        val result: RealmResults<BatchFileDataTable> =
            realm.where(BatchFileDataTable::class.java).findAll()
        result.deleteAllFromRealm()
    }
}

//Delete Batch File Table Data with matching invoice number in DB:-
fun deleteBatchTableDataInDBWithInvoiceNumber(invoiceNo: String) = withRealm {
    it.executeTransaction { realm ->
        val result: RealmResults<BatchFileDataTable> =
            realm.where(BatchFileDataTable::class.java).equalTo("invoiceNumber", invoiceNo)
                .findAll()
        result.deleteAllFromRealm()
    }
}


suspend fun saveToDB(spliter: List<String>) {
    val funTag = "saveToDB"
    logger(funTag, spliter[2])
    savePcs(spliter[0], spliter[2])
    when {
        spliter[2] == "101" -> {
            val terminalCommunicationTable = TerminalCommunicationTable()
            parseData(terminalCommunicationTable, spliter)
            TerminalCommunicationTable.performOperation(terminalCommunicationTable) {
                logger("saveToDB", "tct")
            }

        }
        spliter[2] == "102" -> {
            val issuerParameterTable = IssuerParameterTable()
            parseData(issuerParameterTable, spliter)
            IssuerParameterTable.performOperation(issuerParameterTable) {
                logger("saveToDB", "ipt")
            }

        }
        spliter[2] == "106" -> {
            val terminalParameterTable = TerminalParameterTable()
            parseData(terminalParameterTable, spliter)
            terminalParameterTable.stan = "000001"
            TerminalParameterTable.performOperation(terminalParameterTable) {
                logger("saveToDB", "mTpt")
                // change the printer darkness
                GlobalScope.launch {
                    val tpt = TerminalParameterTable.selectFromSchemeTable()
                    /*if (tpt != null) {
                        val darkness =
                            if (tpt.printingImpact.isNotEmpty()) tpt.printingImpact else "0"
                        //setPrintDarkness(darkness.toInt())
                    }*/
                }
            }

        }
        spliter[2] == "108" -> {
            val emiBinTable = EmiBinTable()
            parseData(emiBinTable, spliter)
            EmiBinTable.performOperation(emiBinTable) {
                logger("saveToDB", "ebt")
            }
        }
        spliter[2] == "107" -> {
            val cardDataTable = CardDataTable()
            parseData(cardDataTable, spliter)
            CardDataTable.performOperation(cardDataTable) {
                logger("saveToDB", "cdt")
            }

        }

        spliter[2] == "109" -> {
            val brandDataTable = BrandDataTable()
            parseData(brandDataTable, spliter)
            BrandDataTable.performOperation(brandDataTable) {
                logger("saveToDB", "bdt")
            }
        }
        spliter[2] == "110" -> {
            val productCategoryTable = ProductCategoryTable()
            parseData(productCategoryTable, spliter)
            ProductCategoryTable.performOperation(productCategoryTable) {
                logger("saveToDB", "pct")
            }
        }
        spliter[2] == "111" -> {
            val productTable = ProductTable()
            parseData(productTable, spliter)
            ProductTable.performOperation(productTable) {
                logger("saveToDB", "pt")
            }
        }
        spliter[2] == "112" -> {
            val schemeTable = SchemeTable()
            parseData(schemeTable, spliter)
            SchemeTable.performOperation(schemeTable) {
                logger("saveToDB", "st")
            }
        }
        spliter[2] == "113" -> {
            val tenureTable = TenureTable()
            parseData(tenureTable, spliter)
            TenureTable.performOperation(tenureTable) {
                logger("saveToDB", "tt")
            }
        }

        spliter[2] == "114" -> {
            val emiSchemeTable = EmiSchemeTable()
            parseData(emiSchemeTable, spliter)
            EmiSchemeTable.performOperation(emiSchemeTable) {
                logger("saveToDB", "est")
            }
        }
        spliter[2] == "115" -> {
            val benifitSlabTable = BenifitSlabTable()
            parseData(benifitSlabTable, spliter)
            BenifitSlabTable.performOperation(benifitSlabTable) {
                logger("saveToDB", "bst")
            }
        }
        spliter[2] == "116" -> {
            val emiSchemeProductTable = EmiSchemeProductTable()
            parseData(emiSchemeProductTable, spliter)
            EmiSchemeProductTable.performOperation(emiSchemeProductTable) {
                logger("saveToDB", "bst")
            }
        }

        spliter[2] == "117" -> {
            val emiSchemeGroupTable = EmiSchemeGroupTable()
            parseData(emiSchemeGroupTable, spliter)
            EmiSchemeGroupTable.performOperation(emiSchemeGroupTable) {
                logger("saveToDB", "table117")
            }
          //  logger("Table 117", spliter.joinToString("|"))
        }

        //region=====New Tables added for HDFC=====
        spliter[2] == "201" -> {  // HDFC TPT
            val hdfcTpt = HdfcTpt()
            parseData(hdfcTpt, spliter)
            HdfcTpt.insertOrUpdate(hdfcTpt)
            logger(HdfcTpt.TAG, "====HdfcTpt has been updated====")
        }

        spliter[2] == "202" -> {   // HDFC CDT
            val hdfcCdt = HdfcCdt()
            parseData(hdfcCdt, spliter)
            HdfcCdt.insertOrUpdate(hdfcCdt)
            logger(HdfcTpt.TAG, "====HdfcCdt has been updated====")
        }
        //endregion

    }

}

fun unzipZipedBytes(ba: ByteArray) {

    val root = VerifoneApp.appContext.externalCacheDir.toString()
    val folder = File("$root/BonusHub")

    if (!folder.exists()) {
        folder.mkdir()
    }

    val bais = ByteArrayInputStream(ba)
    val zis: ZipInputStream? = ZipInputStream(bais)

    var ze: ZipEntry? = null
    ze = zis?.nextEntry

    while (ze != null) {
        val entryName = ze.name
        val f = File(folder, entryName)
        val out = FileOutputStream(f)

        val bf = ByteArray(4096)
        var byteRead = zis?.read(bf) ?: -1

        while (byteRead != -1) {
            out.write(bf, 0, byteRead)
            byteRead = zis?.read(bf) ?: -1
        }
        out.close()
        zis?.closeEntry()
        ze = zis?.nextEntry
    }
    zis?.close()

}

//Below method is used to show Invoice with Padding:-
fun invoiceWithPadding(invoiceNo: String) =
    addPad(input = invoiceNo, padChar = "0", totalLen = 6, toLeft = true)

object ConnectionTimeStamps {
    var identifier: String = ""
    var dialStart = ""
    var dialConnected = ""
    var startTransaction = ""
    var recieveTransaction = ""

    private var stamp = "~~~~"

    init {
        stamp = AppPreference.getString(AppPreference.F48_STAMP)
    }

    fun getFormattedStamp(): String =
        "$identifier~$startTransaction~$recieveTransaction~$dialStart~$dialConnected"

    fun reset() {
        identifier = ""
        dialStart = ""
        dialConnected = ""
        startTransaction = ""
        recieveTransaction = ""
    }

    fun saveStamp() {
        stamp = getFormattedStamp()
        AppPreference.saveString(AppPreference.F48_STAMP, stamp)
        reset()
    }

    fun saveStamp(f48: String) {
        identifier = f48.split("~")[0]
        saveStamp()
    }


    fun saveToTerminalParamTable() {
        /*   val dao = TerminalParameterDao()
           val tpt = dao.selectFromSchemeTable()
           if (tpt != null) {
               tpt.lastF48IdentifierTS = ConnectionTimeStamps.getFormattedStamp()
               dao.saveTable(tpt)
           }*/
    }

    fun getStamp(): String {
        val stamp=  if (stamp.isNotEmpty()) stamp else "~~~~"
        val otherInfo= getOtherInfo()
        return stamp+otherInfo

    }


    fun getOtherInfo(): String {
        return try {
            val imei = VFService.vfDeviceService?.deviceInfo?.imei
            val batteryStrength = VFService.vfDeviceService?.deviceInfo?.batteryLevel
            val simNo = VFService.vfDeviceService?.deviceInfo?.iccid
            Log.e("[1] iemi,battry,simNo", "$imei ,${batteryStrength} -----> ${simNo}  ")
            "~${VerifoneApp.networkStrength}~${batteryStrength}~${imei}~${simNo}~${VerifoneApp.operatorName}"
        } catch (ex: java.lang.Exception) {
            Log.e("[2]iemi,battry,simNo", "${VerifoneApp.imeiNo} ,${VerifoneApp.batteryStrength} -----> ${VerifoneApp.simNo}  ")
            "~${VerifoneApp.networkStrength}~${VerifoneApp.batteryStrength}~${VerifoneApp.imeiNo}~${VerifoneApp.simNo}~${VerifoneApp.operatorName}"
        }
    }
}


//region============= ROC, ConnectionTime========
fun getF48TimeStamp(): String {
    val currentTime = Calendar.getInstance().time
    val sdf = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
    return sdf.format(currentTime)
}

fun dateFormater(date: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)

fun timeFormater(date: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)

@Deprecated("User ROCproviderV2 for multiple bank related roc.")
object ROCProvider {

    private var roc = 1
    private val TAG = ROCProvider::class.java.simpleName

    init {
        roc = AppPreference.getIntData(AppPreference.ROC)
    }

    fun getRoc(): Int = roc

    fun resetRoc() {
        roc = 1
        AppPreference.setIntData(AppPreference.ROC, roc)
    }

    fun increment() {
        roc++
        check()
        AppPreference.setIntData(AppPreference.ROC, roc)
    }


    private fun check() {
        if (roc > 999999) roc = 1
    }

    fun incrementFromResponse(num: String) {
        try {
            roc = num.toInt() + 1
            check()
            AppPreference.setIntData(AppPreference.ROC, roc)
        } catch (ex: Exception) {
            logger(TAG, ex.message ?: "", "e")
            increment()
        }

    }

}


object ROCProviderV2 {

    private var mRocHash = mutableMapOf<String, Int>()
    private val mType = object : TypeToken<MutableMap<String, Int>>() {}.type
    private val mGson = Gson()
    private val TAG = ROCProviderV2::class.java.simpleName

    init {
        mRocHash = try {
            val str = AppPreference.getString(AppPreference.ROC_V2)
            mGson.fromJson<MutableMap<String, Int>>(
                str, mType
            )
        } catch (ex: Exception) {
            val tl = TerminalParameterTable.selectAll()
            val mm = mutableMapOf<String, Int>()
            for (e in tl) {  // Initializing all bank related tids if found zero
                mm[e.tidBankCode] = 1
            }
            mm
        }
    }

    fun getRoc(bankCode: String): Int = mRocHash[bankCode] ?: 1

    fun resetRoc(bankCode: String) {
        mRocHash[bankCode] = 1
        AppPreference.saveString(AppPreference.ROC_V2, mGson.toJson(mRocHash, mType))
    }

    fun increment(bankCode: String) {
        mRocHash[bankCode] = mRocHash[bankCode] ?: 0 + 1
        check(bankCode)
        AppPreference.saveString(AppPreference.ROC_V2, mGson.toJson(mRocHash, mType))
    }

    /**
     * checking if roc is greater than 999999 or not found
     * then roc is setted with value 1.
     * */
    private fun check(bankCode: String) {
        if (mRocHash[bankCode] ?: 1000000 > 999999) {
            mRocHash[bankCode] = 1
        }
    }

    fun incrementFromResponse(num: String, bankCode: String) {
        try {
            mRocHash[bankCode] = num.toInt() + 1
            check(bankCode)
            AppPreference.saveString(AppPreference.ROC_V2, mGson.toJson(mRocHash, mType))
        } catch (ex: Exception) {
            logger(TAG, ex.message ?: "", "e")
            increment(bankCode)
        }

    }

    //Below code to check Bank Code and on basis of that it will inflate Both bank & bonushub logo or only bank logo on basis of condition:-
    fun refreshToolbarLogos(activity: Activity) {
        //Show Logo of Bank by checking Bank Code:-
        val tpt = TerminalParameterTable.selectFromSchemeTable()
        val bonushubLogo = activity.findViewById<ImageView>(R.id.main_toolbar_tv)
        val bankLogoImageView = activity.findViewById<ImageView>(R.id.bonushub_logo)
        var bankLogo = 0

        when (AppPreference.getBankCode()) {
            "07" -> bankLogo = R.drawable.amex_logo
            "01" -> bankLogo = R.drawable.ic_hdfcsvg
            else -> {
            }
        }

        //Show Both BonusHub and Bank Logo on base of condition check on tpt.reservedValues 10th Position:-
        if (tpt != null) {
            if (!TextUtils.isEmpty(tpt.reservedValues)) {
                if (tpt.reservedValues.length > 10) {
                    for (i in tpt.reservedValues.indices) {
                        if (i == 9) {
                            if (tpt.reservedValues[i].toString() == "1") {
                                bonushubLogo?.visibility = View.VISIBLE
                                bankLogoImageView?.setImageResource(bankLogo)
                                bankLogoImageView?.visibility = View.VISIBLE
                                break
                            } else {
                                bonushubLogo?.visibility = View.GONE
                                bankLogoImageView?.setImageResource(bankLogo)
                                bankLogoImageView?.visibility = View.VISIBLE
                            }
                        }
                    }
                } else {
                    bonushubLogo?.visibility = View.GONE
                    bankLogoImageView?.setImageResource(bankLogo)
                    bankLogoImageView?.visibility = View.VISIBLE
                }
            } else {
                bonushubLogo?.visibility = View.GONE
                bankLogoImageView?.setImageResource(bankLogo)
                bankLogoImageView?.visibility = View.VISIBLE
            }
        }
    }

    //Dialog Pop Up for Settle Batch Data:-
    fun settleMsgDialog(
        context: Context, title: String, msg: String, acceptCb: () -> Unit,
        titleAccept: String = "", titleCancel: String = ""
    ) {
        val dialog = Dialog(context)
        dialog.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.msg_dialog)
            setCancelable(false)

            findViewById<TextView>(R.id.msg_dialog_title).text = title
            findViewById<TextView>(R.id.msg_dialog_msg).text = msg

            with(findViewById<TextView>(R.id.msg_dialog_ok)) {
                setOnClickListener {
                    dismiss()
                    acceptCb()
                }
                if (titleAccept.isNotEmpty()) text = titleAccept

            }

            with(findViewById<TextView>(R.id.msg_dialog_cancel)) {
                setOnClickListener {
                    dismiss()
                }
                if (titleCancel.isNotEmpty()) text = titleCancel
            }
        }.show()

    }

    fun byte2HexStr(var0: ByteArray?, offset: Int, length: Int): String? {
        return if (var0 == null) {
            ""
        } else {
            var var1: String
            val var2 = StringBuilder("")
            for (var3 in offset until offset + length) {
                var1 = Integer.toHexString((var0[var3] and 255.toByte()).toInt())
                var2.append(if (var1.length == 1) "0$var1" else var1)
            }
            var2.toString().toUpperCase(Locale.ROOT).trim { it <= ' ' }
        }
    }

    fun byte2HexStr(var0: ByteArray?): String? {
        return if (var0 == null) {
            ""
        } else {
            var var1: String
            val var2 = StringBuilder("")
            for (b in var0) {
                var1 = Integer.toHexString((b and 255.toByte()).toInt())
                var2.append(if (var1.length == 1) "0$var1" else var1)
            }
            var2.toString().toUpperCase(Locale.ROOT).trim { it <= ' ' }
        }
    }


    fun hexStr2Byte(hexString: String?): ByteArray {
//        Log.d(TAG, "hexStr2Byte:" + hexString);
        if (hexString == null || hexString.isEmpty()) {
            return byteArrayOf(0)
        }
        val hexStrTrimed = hexString.replace(" ", "")
        //        Log.d(TAG, "hexStr2Byte:" + hexStrTrimed);
        run {
            var hexStr = hexStrTrimed
            var len = hexStrTrimed.length
            if (len % 2 == 1) {
                hexStr = hexStrTrimed + "0"
                ++len
            }
            var highChar: Char
            var lowChar: Char
            var high: Int
            var low: Int
            val result = ByteArray(len / 2)
            var s: String
            var i = 0
            while (i < hexStr.length) {

                // read 2 chars to convert to byte
                //                s = hexStr.substring(i,i+2);
                //                int v = Integer.parseInt(s, 16);
                //
                //                result[i/2] = (byte) v;
                //                i++;
                // read high byte and low byte to convert
                highChar = hexStr[i]
                lowChar = hexStr[i + 1]
                high = CHAR2INT(highChar)
                low = CHAR2INT(lowChar)
                result[i / 2] = (high * 16 + low).toByte()
                i++
                i++
            }
            return result
        }
    }

    //Below method is used to encrypt track2 data:-
    fun getEncryptedTrackData(track2Data: String?): String? {
        var encryptedbyteArrrays: ByteArray? = null
        if (null != track2Data) {
            var track21 = "35|" + track2Data.replace("D", "=").replace("F", "")
            //println("Track 2 data is$track21")
            val DIGIT_8 = 8

            val mod = track21.length % DIGIT_8
            if (mod!=0) {
                track21 = getEncryptedField57DataForVisa(track21.length,track21)
            }

            val byteArray = track21.toByteArray(StandardCharsets.ISO_8859_1)
            encryptedbyteArrrays = VFService.vfPinPad?.encryptTrackData(0, 2, byteArray)

            /*println(
                "Track 2 with encyption is --->" + Utility.byte2HexStr(encryptedbyteArrrays)
            )*/
        }

        return Utility.byte2HexStr(encryptedbyteArrrays)
    }

    //Below array is to get Field55 Data:-
    val mField55 = intArrayOf(
        0x9F26,
        0x9F10,
        0x9F37,
        0x9F36,
        0x95,
        0x9A,
        0x9C,
        0x9F02,
        0x5F2A,
        0x9F1A,
        0x82,
        0x5F34,
        0x9F27,
        0x9F33,
        0x9F34,
        0x9F35,
        0x9F03,
        0x9F47
    )
    //Below method is used to make and return Field55 Data:-
    fun getField55(isAmex: Boolean = true): String {
        val sb = StringBuilder()
        for (f in mField55) {
            val v = VFService.vfIEMV?.getCardData(Integer.toHexString(f).toUpperCase(Locale.ROOT))
            if(v !=null) {
                sb.append(Integer.toHexString(f))
                var l = Integer.toHexString(v.size)
                if (l.length < 2) {
                    l = "0$l"
                }
                if (f == 0x9F10 && isAmex) {
                    val c = l + PaxUtils.bcd2Str(v)
                    var le = Integer.toHexString(c.length / 2)
                    if (le.length < 2) {
                        le = "0$le"
                    }
                    sb.append(le)
                    sb.append(c)
                } else {
                    sb.append(l)
                    sb.append(PaxUtils.bcd2Str(v))
                }
            }
            // end of if null value check
            else if (f == 0x9F03) {
                sb.append(Integer.toHexString(f))
                sb.append("06")
                sb.append("000000000000")
            }
        }// end of for loop
        return sb.toString().toUpperCase(Locale.ROOT)
    }

    //Below method is used to Save Batch File Data in App Preference:-
    fun saveBatchInPreference(batchList: MutableList<BatchFileDataTable>) {
        val tempBatchDataList = Gson().toJson(
            batchList,
            object : TypeToken<List<BatchFileDataTable>>() {}.type
        ) ?: ""
        AppPreference.saveString(AppPreference.LAST_BATCH, tempBatchDataList)
    }

    private fun CHAR2INT(c: Char): Int {
        return if (c in '0'..'9'
            || c == '=' // for track2
        ) {
            c - '0'
        } else if (c in 'a'..'f') {
            c - 'a' + 10
        } else if (c in 'A'..'F') {
            c - 'A' + 10
        } else {
            0
        }
    }

    fun HEX2DEC(hex: Int): Byte {
        return (hex / 10 * 16 + hex % 10).toByte()
    }

    fun DEC2INT(dec: Byte): Int {
        var high = 0x007F and dec.toInt() shr 4
        if (0 != 0x0080 and dec.toInt()) {
            high += 8
        }
        return high * 10 + (dec and 0x0F)
    }
}

fun setBitmaptoImageview(imgView: View, bmpFileName: String) {
    val isr = VerifoneApp.appContext.assets.open(bmpFileName)
    val promoBitMap = BitmapFactory.decodeStream(isr)

    (imgView as ImageView).setImageBitmap(promoBitMap)

}


@Synchronized
fun getMaskedPan(terminalParameterTable: TerminalParameterTable?, panNumber: String): String {
    return panMasking(
        panNumber,
        terminalParameterTable?.panMaskFormate ?: "0000********0000"
    ) //0000*0000
}

fun panMasking(input: String, maskFormat: String): String {
    if (input.isNotEmpty()) {
        val maskCharArr = maskFormat.toCharArray()
        val inputArr = input.toCharArray()

        // get all stars index
        val li = arrayListOf<Int>()
        for (e in maskCharArr.indices) {
            if (maskFormat[e] == '*') {
                li.add(e)
            }
        }
        when {
            inputArr.size == maskCharArr.size -> for (e in li) {
                inputArr[e] = '*'
            }
            inputArr.size > maskCharArr.size -> {
                for (e in li.first()..(inputArr.lastIndex - li.last())) {
                    inputArr[e] = '*'
                }
            }
            else -> for (e in 4..(inputArr.lastIndex - 4)) {
                inputArr[e] = '*'
            }
        }
        val sb = StringBuilder()

        var index = 0
        while (index < inputArr.size) {
            var endIndex = index + 3
            if (endIndex > inputArr.lastIndex) {
                endIndex = inputArr.lastIndex
            }
            val tempCh = inputArr.slice(index..endIndex)
            sb.append(tempCh.toCharArray())
            sb.append(" ")
            index += 4
        }

        return sb.toString().substring(0, sb.lastIndex)
    } else return ""
}


//Below method is used to save Init Request Packet:-
fun writeInitPacketLog(
    requestPacket: String,
    responsePacket: String,
    fos: FileOutputStream,
    fileName: String
) {
    try {
        fos.write(requestPacket.str2ByteArr())
        fos.write(responsePacket.str2ByteArr())
        //VFService.showToast("Saved to " + VerifoneApp.appContext.filesDir + "/" + fileName)
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        try {
            //fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

//Read file from assets
fun readFile(): InputStream? {
    try {
        return VerifoneApp.appContext.assets.open("hhh")
    } catch (e: IOException) {
        e.printStackTrace()

    }
    return null
}

fun readFile(context: Context, filename: String?): String? {
    return try {
        val fis = context.openFileInput(filename)
        val isr = InputStreamReader(fis, "UTF-8")
        val bufferedReader = BufferedReader(isr)
        val sb = java.lang.StringBuilder()
        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            sb.append((line?.split("||")?.plus("\n")))
        }
        sb.toString()
        Log.d("Logs:- ", sb.toString()).toString()
    } catch (e: FileNotFoundException) {
        ""
    } catch (e: UnsupportedEncodingException) {
        ""
    } catch (e: IOException) {
        ""
    }
}


fun getWakeLock(): PowerManager.WakeLock {

    val wakeLock: PowerManager.WakeLock =
        (VerifoneApp.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VerifoneApp::MyWakelockTag").apply {
                acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        }
    return wakeLock
}

fun releaseWakeLock(wakeLock: PowerManager.WakeLock) {
    wakeLock.release()
}


fun transactionType2Name(code: Int): String {
    return when (code) {
        TransactionType.SALE.type -> "Sale"
        TransactionType.VOID.type -> "Void Sale"
        TransactionType.VOID_REFUND.type -> "Void Refund"
        TransactionType.REFUND.type -> "Refund"
        TransactionType.PRE_AUTH.type -> "Pre-Auth"
        TransactionType.PRE_AUTH_COMPLETE.type -> "Auth Complete"
        TransactionType.EMI_SALE.type -> "EMI Sale"
        TransactionType.VOID_PREAUTH.type -> "Void Pre-Auth"
        TransactionType.OFFLINE_SALE.type -> "Offline Sale"
        TransactionType.TIP_SALE.type -> "Tip Sale"
        TransactionType.SALE_WITH_CASH.type -> "Sale Cash"
        TransactionType.TIP_ADJUSTMENT.type -> "Tip Adjust"
        TransactionType.VOID_OFFLINE_SALE.type -> "Void Offline Sale"
        else -> "Unknown"
    }
}

fun getCardOptionBundle(): Bundle {
    val cardOption = Bundle()
    cardOption.putBoolean(
        ConstIPBOC.checkCard.cardOption.KEY_Contactless_boolean,
        ConstIPBOC.checkCard.cardOption.VALUE_supported
    )
    cardOption.putBoolean(
        ConstIPBOC.checkCard.cardOption.KEY_SmartCard_boolean,
        ConstIPBOC.checkCard.cardOption.VALUE_supported
    )
    cardOption.putBoolean(
        ConstIPBOC.checkCard.cardOption.KEY_MagneticCard_boolean,
        ConstIPBOC.checkCard.cardOption.VALUE_supported
    )

    return cardOption
}


//Below code is to get EncryptedField57 Data for Manual Sale:-
fun getEncryptedField57DataForManualSale(panNumber: String, expDate: String): String {
    val encryptedByteArray: ByteArray?
    val expMonth = expDate.substring(0, 2)
    val expYear = expDate.substring(2, 4)
    var dataDescription = "02,14|$panNumber|$expYear$expMonth"
    val dataLength = dataDescription.length
    val DIGIT_8 = 8
    if (dataLength > DIGIT_8) {
        val mod = dataLength % DIGIT_8
        if (mod!=0) {
          val padding = DIGIT_8 - mod
          val totalLength = dataLength + padding
            dataDescription = addPad(dataDescription, " ", totalLength, false)
        }
        logger("Field57_Manual", " -->$dataDescription", "e")
        val byteArray = dataDescription.toByteArray(StandardCharsets.ISO_8859_1)
        encryptedByteArray = VFService.vfPinPad?.encryptTrackData(0, 2, byteArray)
        /*println(
            "Track 2 with encryption in manual sale is --->" + Utility.byte2HexStr(
                encryptedByteArray
            )
        )*/
        return Utility.byte2HexStr(encryptedByteArray)
    } else return "TRACK57_LENGTH<8"

}

fun getEncryptedField57DataForVisa(dataLength: Int,dataDescription: String): String{
    var dataDescription = dataDescription
    val encryptedByteArray: ByteArray?
    val DIGIT_8 = 8
    if (dataLength > DIGIT_8) {
        val mod = dataLength % DIGIT_8
        if (mod!=0) {
            val padding = DIGIT_8 - mod
            val totalLength = dataLength + padding
            dataDescription = addPad(dataDescription, " ", totalLength, false)
        }
        //     logger("Field57_Visa", " -->$dataDescription", "e")
        //      val byteArray = dataDescription.toByteArray(StandardCharsets.ISO_8859_1)
        //     encryptedByteArray = VFService.vfPinPad?.encryptTrackData(0, 2, byteArray)
        //    println("Track 2 with encryption in Visa sale is --->" + Utility.byte2HexStr(encryptedByteArray))
        return dataDescription/*Utility.byte2HexStr(encryptedByteArray)*/
    } else return "TRACK57_LENGTH<8"
}

fun getEncryptedField57DataForOfflineSale(
    panNumber: String,
    expDate: String,
    approvalCode: String
): String {
    val encryptedByteArrray: ByteArray?
    val expMonth = expDate.substring(0, 2)
    val expYear = expDate.substring(2, 4)
    var dataDescription = "02,14,38|$panNumber|$expYear$expMonth|$approvalCode"
    val dataLength = dataDescription.length
    val DIGIT_8 = 8
    if (dataLength > DIGIT_8) {
        val mod = dataLength % DIGIT_8
        if (mod != 0) {
            val padding = DIGIT_8 - mod
            val totalLength = dataLength + padding
            dataDescription = addPad(dataDescription, " ", totalLength, false)
        }
        logger("Field57_Manual", " -->$dataDescription", "e")
        val byteArray = dataDescription.toByteArray(StandardCharsets.ISO_8859_1)
        encryptedByteArrray = VFService.vfPinPad?.encryptTrackData(0, 2, byteArray)
        //println("Track 2 with encryption is --->" + Utility.byte2HexStr(encryptedByteArrray))
        return Utility.byte2HexStr(encryptedByteArrray)
    } else return "TRACK57_LENGTH<8"
}

//Method to Handle Force start after App crashes:
fun forceStart(context: Context?) {
    (context as Activity).finish()
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })

    Handler(Looper.getMainLooper()).postDelayed(Runnable {
        GlobalScope.launch {
            VFService.connectToVFService(VerifoneApp.appContext)

        }

    }, 200)

}

fun failureImpl(
    context: Context,
    servicemsg: String,
    msg: String,
    exception: Exception = Exception()
) {
    val builder = AlertDialog.Builder(context)
    object : Thread() {
        override fun run() {
            Looper.prepare()
            builder.setTitle("Alert...!!")
            builder.setCancelable(false)
            builder.setMessage(servicemsg + msg)
                .setCancelable(false)
                .setPositiveButton("Start") { _, _ ->
                    forceStart(context)
                }
                .setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    (context as Activity).finishAffinity()
                }
            val alert: AlertDialog = builder.create()
            try {
                if(null !=alert && !alert.isShowing) {
                    alert.show()
                    Looper.loop()
                }
            }
            catch (ex: WindowManager.BadTokenException) {
                ex.printStackTrace()
                Handler(Looper.getMainLooper()).postDelayed(Runnable {
                    GlobalScope.launch {
                        VFService.connectToVFService(VerifoneApp.appContext)
                    }
                }, 200)
            }
            catch (ex: Exception) {
                ex.printStackTrace()
                Handler(Looper.getMainLooper()).postDelayed(Runnable {
                    GlobalScope.launch {
                        VFService.connectToVFService(VerifoneApp.appContext)
                    }
                }, 200)
            }

        }
    }.start()
 }

fun initializeFontFiles() = PrinterFonts.initialize(VerifoneApp.appContext.assets)

//Below method is used to show Pop-Up in case of Sale and Bank EMI to enter either Mobile Number or Bill Number on Condition Base:-
fun showMobileBillDialog(
    context: Context?,
    transactionType: Int,
    dialogCB: (Pair<String, String>) -> Unit
) {
    val dialog = context?.let { Dialog(it) }
    val inflate = LayoutInflater.from(context).inflate(R.layout.mobile_bill_dialog_view, null)
    dialog?.setContentView(inflate)
    dialog?.setCancelable(false)
    dialog?.window?.attributes?.windowAnimations = R.style.DialogAnimation
    val window = dialog?.window
    window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    val mobileNumberET: BHTextInputEditText? = dialog?.findViewById(R.id.mobileNumberET)
    val billNumberET: BHTextInputEditText? = dialog?.findViewById(R.id.billNumberET)
    val billNumberTil: BHTextInputLayout? = dialog?.findViewById(R.id.bill_number_til)
    val cancelButton: Button? = dialog?.findViewById(R.id.cancel_btn)
    val okButton: Button? = dialog?.findViewById(R.id.ok_btn)

    val tpt = TerminalParameterTable.selectFromSchemeTable()
    if (tpt?.reservedValues?.substring(
            2,
            3
        ) == "1" && transactionType == TransactionType.EMI_SALE.type
    )
        billNumberTil?.visibility = View.VISIBLE
    else
        billNumberTil?.visibility = View.GONE

    //Cancel Button OnClick:-
    cancelButton?.setOnClickListener {
        dialog.dismiss()
        dialogCB(Pair("", ""))
    }

    //Ok Button OnClick:-
    okButton?.setOnClickListener {
        if (transactionType == TransactionType.SALE.type && tpt?.reservedValues?.substring(
                0,
                1
            ) == "1"
        ) {
            when {
                !TextUtils.isEmpty(mobileNumberET?.text.toString()) -> if (mobileNumberET?.text.toString().length in 10..13) {
                    dialog.dismiss()
                    dialogCB(Pair(mobileNumberET?.text.toString(), ""))
                } else
                    VFService.showToast(context.getString(R.string.enter_valid_mobile_number))
                TextUtils.isEmpty(mobileNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", ""))
                }
            }
        } else if (transactionType == TransactionType.EMI_SALE.type && tpt?.reservedValues?.substring(
                1,
                2
            ) == "1"
            && tpt.reservedValues.substring(2, 3) == "1"
        ) {
            when {
                !TextUtils.isEmpty(mobileNumberET?.text.toString())
                        && !TextUtils.isEmpty(billNumberET?.text.toString()) -> if (mobileNumberET?.text.toString().length in 10..13) {
                    dialog.dismiss()
                    dialogCB(Pair(mobileNumberET?.text.toString(), billNumberET?.text.toString()))
                } else
                    VFService.showToast(context.getString(R.string.enter_valid_mobile_number))

                !TextUtils.isEmpty(mobileNumberET?.text.toString()) -> if (mobileNumberET?.text.toString().length in 10..13) {
                    dialog.dismiss()
                    dialogCB(Pair(mobileNumberET?.text.toString(), ""))
                } else
                    VFService.showToast(context.getString(R.string.enter_valid_mobile_number))

                !TextUtils.isEmpty(billNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", billNumberET?.text.toString()))
                }

                TextUtils.isEmpty(mobileNumberET?.text.toString()) &&
                        TextUtils.isEmpty(mobileNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", ""))
                }
            }

        } else if (transactionType == TransactionType.EMI_SALE.type && tpt?.reservedValues?.substring(
                1,
                2
            ) == "1"
        ) {
            when {
                !TextUtils.isEmpty(mobileNumberET?.text.toString()) -> if (mobileNumberET?.text.toString().length in 10..13) {
                    dialog.dismiss()
                    dialogCB(Pair(mobileNumberET?.text.toString(), ""))
                } else
                    VFService.showToast(context.getString(R.string.enter_valid_mobile_number))
                TextUtils.isEmpty(mobileNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", ""))
                }
            }

        } else if (transactionType == TransactionType.EMI_SALE.type && tpt?.reservedValues?.substring(
                2,
                3
            ) == "1"
        ) {
            when {
                !TextUtils.isEmpty(billNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", billNumberET?.text.toString()))
                }
                TextUtils.isEmpty(billNumberET?.text.toString()) -> {
                    dialog.dismiss()
                    dialogCB(Pair("", ""))
                }
            }
        } else {
            dialog.dismiss()
            dialogCB(Pair("", ""))
        }
    }
    dialog?.show()
}

fun checkInternetConnection(): Boolean {
    val cm =
        VerifoneApp.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetworkInfo
    return activeNetwork != null && activeNetwork.isConnectedOrConnecting
}

//Below method is used to convert String2NibbleArray and then NibbleArray2String for our CVM && CTLS Max Limit use case:-
fun convertStr2Nibble2Str(data: String): String {
    var tempData = ""
    val splitData = data.chunked(2)
    for (i in splitData.indices) {
        if (splitData[i].toInt() != 0) {
            val convertData = str2NibbleArr(splitData[i])[0].toString()
            if (convertData.length == 1)
                tempData = "${tempData}0${convertData}"
            else
                tempData += convertData
            continue
        }
        tempData += splitData[i]
    }
    return tempData
}

fun getCurrentDate(): String{
    val sdf = SimpleDateFormat("yyyyMMdd")
    val d = Date()
    val currDt = sdf.format(d)

    return currDt
}
fun getCurrentDateforMag(): String{
    val sdf = SimpleDateFormat("yyMM")
    val d = Date()
    val currDt = sdf.format(d)

    return currDt
}

//Below method is used to show DatePicker to select Date:-
fun openDatePicker(inputEditText: BHTextInputEditText, context: Context) {
    try {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(
            context,
            android.R.style.Theme_Holo_Dialog,
            { _, calenderYear, monthOfYear, _ ->
                // Display Selected date in textView:-
                val selectedMonth = monthOfYear + 1
                var fetchedDate: String? = null
                fetchedDate = if (selectedMonth < 10) {
                    "0$selectedMonth/${calenderYear.toString().substring(2, 4)}"
                } else {
                    "$selectedMonth/${calenderYear.toString().substring(2, 4)}"
                }
                inputEditText.setText(fetchedDate)
            },
            year,
            month,
            day
        )
        dpd.datePicker.minDate = c.timeInMillis
        dpd.setCancelable(false)
        dpd.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dpd.datePicker.findViewById<View>(
            Resources.getSystem().getIdentifier(
                "day",
                "id",
                "android"
            )
        ).visibility =
            View.GONE
        dpd.show()
    } catch (ex: java.lang.Exception) {
        ex.printStackTrace()
    }
}

//Below code is to check and validate Credit Card Number using Luhn Check Algorithm:-
fun cardLuhnCheck(cardNo: String): Boolean {
    try {
        val nDigits = cardNo.length
        var nSum = 0
        var isSecond = false
        for (i in nDigits - 1 downTo 0) {
            var d = cardNo[i] - '0'
            if (isSecond) d *= 2

            // We add two digits to handle
            // cases that make two digits
            // after doubling
            nSum += d / 10
            nSum += d % 10
            isSecond = !isSecond
        }
        return nSum % 10 == 0
    } catch (ex: java.lang.Exception) {
        ex.printStackTrace()
        return false
    }
}

//Below method is used to check Terminal Date and Time is Correct or not:-
fun isTimeAutomatic(c: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        (Settings.Global.getInt(
            c.contentResolver,
            Settings.Global.AUTO_TIME,
            0
        ) === 1 && Settings.Global.getInt(
            c.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0
        ) === 1)
    } else {
        true
    }
}

//Below method is used to Time out user from App Screen:-
fun screenTimeout(context: Context) {
    Handler(Looper.getMainLooper()).postDelayed({
        navigateToMain(context)
    }, 30000)
}

//Below method to Navigate merchant to MainActivity:-
private fun navigateToMain(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
}

fun txnSuccessToast(context: Context,msg: String="Transaction Approved"){
    try {
        GlobalScope.launch(Dispatchers.Main) {
            VFService.vfBeeper?.startBeep(200)
            val layout = (context as Activity).layoutInflater.inflate(
                R.layout.success_toast,
                context.custom_toast_layout)
            layout.txtvw.text=msg
            val myToast = Toast(context)
            myToast.duration = Toast.LENGTH_LONG
            myToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            myToast.view = layout//setting the view of custom toast layout
            myToast.show()
        }
    }
    catch (ex:java.lang.Exception){
        VFService.showToast(context.getString(R.string.transaction_approved_successfully))
        VFService.connectToVFService(context)
    }
}

//Below method is used to write TCT Data and TID of user so that after App Update we can get data from file and do AutoInit:-
fun writeAppVersionNameInFile(context: Context) {
    val tct = TerminalCommunicationTable.selectFromSchemeTable()
    try {
        val saveFile = File(context.externalCacheDir, "version.txt")
        val writer = BufferedWriter(FileWriter(saveFile))
        writer.write(BuildConfig.VERSION_NAME)
        writer.flush()
        writer.close()
        //VFService.showToast("Successfully write data to the file.")
        Log.d("FilePath:- ", Uri.fromFile(saveFile).toString())
    } catch (e: IOException) {
        VFService.showToast("An error occurred.")
        e.printStackTrace()
    }
}

//Below method is used to read IPAddress , IPPort and Merchant TID from saved File in Terminal:-
fun readAppVersionNameFromFile(context: Context, cb: (String) -> Unit) {
    var versionName: String? = null
    try {
        val file = File(context.externalCacheDir, "version.txt")
        val text: StringBuilder? = null
        val br = BufferedReader(FileReader(file))
        var line: String?
        while (br.readLine().also { line = it } != null) {
            text?.append(line)
            text?.append('\n')
            versionName = line.toString()
        }
        Log.d("DataList:- ", versionName.toString())
        br.close().toString()
        cb(versionName ?: "")
    } catch (ex: IOException) {
        ex.printStackTrace()
        cb(versionName ?: "")
    }
}

//Below method is used to chunk the TNC's text(words's are not slitted in between) which was printed on EMI sale :-
 fun chunkTnC(s: String, limit: Int=48): List<String> {
    var str = s
    val parts: MutableList<String> = ArrayList()
    while (str.length > limit) {
        var splitAt = limit - 1
        while (splitAt > 0 && !Character.isWhitespace(str[splitAt])) {
            splitAt--
        }
        if (splitAt == 0) return parts // can't be split
        parts.add(str.substring(0, splitAt))
        str = str.substring(splitAt + 1)
    }
    parts.add(str)
    return parts
}

/*
App Update Through FTP Steps:-
1.Make Signing apk Build by using Verifone Signing USB and Signing Tool in Windows System.
2.Deploy Signed apk Build to FTP Path.
3.Make settlement from Amex Android Pos App and save current app version name into txt file at app specific external cache dir
after that auto Download Signed apk build from FTP Server.
4.After Download make app specific external cache dir and saved it there.
5.Obtain app specific external cache dir URI path and Send Intent to Android Package Installer for Installation process.
6.After App Update Successfully , when app open again for the first time send server app update confirmation packet by checking
condition - stored file app version name < updated app version name.
 */


