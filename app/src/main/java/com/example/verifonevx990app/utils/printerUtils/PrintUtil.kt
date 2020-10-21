package com.example.verifonevx990app.utils.printerUtils

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.os.*
import android.text.TextUtils
import android.util.Log
import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.emv.transactionprocess.CardProcessedDataModal
import com.example.verifonevx990app.emv.transactionprocess.VFTransactionActivity
import com.example.verifonevx990app.main.MainActivity
import com.example.verifonevx990app.preAuth.PendingPreauthData
import com.example.verifonevx990app.realmtables.BatchFileDataTable
import com.example.verifonevx990app.realmtables.EmiSchemeTable
import com.example.verifonevx990app.realmtables.IssuerParameterTable
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.transactions.EBenefitCalculation
import com.example.verifonevx990app.transactions.IssuerDataModel
import com.example.verifonevx990app.transactions.TenureDataModel
import com.example.verifonevx990app.utils.printerUtils.PrinterFonts.initialize
import com.example.verifonevx990app.vxUtils.*
import com.vfi.smartpos.deviceservice.aidl.IPrinter
import com.vfi.smartpos.deviceservice.aidl.PrinterConfig
import com.vfi.smartpos.deviceservice.aidl.PrinterListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


const val HDFC_LOGO = ""
const val AMEX_LOGO = "amex_print.bmp"
private const val disclaimerEmiOpen = "~emi~"
private const val disclaimerEmiClose = "~!emi~"
private const val disclaimerIssuerOpen = "~iss~"
private const val disclaimerIssuerClose = "~!iss~"

class PrintUtil(context: Context?) {
    var printer: IPrinter? = null
    private var isTipAllowed = false
    private var contexT: Context? = null

    init {
        this.contexT = context
        try {
            printer = VFService.vfPrinter
            if (printer?.status == 0) {
                initializeFontFiles()
                printer?.cleanCache()
                logger("PrintInit->", "Called Printing", "e")
                logger("PrintUtil->", "Printer Status --->  ${printer?.status}", "e")
                val terminalData = TerminalParameterTable.selectFromSchemeTable()
                isTipAllowed = terminalData?.tipProcessing == "1"
            } else {
                //   throw Exception()
                logger("PrintUtil", "Error in printer status --->  ${printer?.status}", "e")
            }
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            logger("PrintUtil", "DEAD OBJECT EXCEPTION", "e")
            //  VFService.showToast(".... TRY AGAIN ....")


            failureImpl(
                contexT as Activity,
                "Printer Service stopped.",
                "Please take charge slip from the Report menu."
            )

        } catch (e: RemoteException) {
            e.printStackTrace()
            logger("PrintUtil", "REMOTE EXCEPTION", "e")
            failureImpl(
                contexT as Activity,
                "Printer Service stopped.",
                "Please take charge slip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            logger("PrintUtil", "EXCEPTION", "e")
            failureImpl(
                contexT as Activity,
                "Printer Service stopped.",
                "Please take charge slip from the Report menu."
            )
        } finally {

        }

    }

    // bundle format for addText
    private var signatureMsg: String? = null
    private var pinVerifyMsg: String? = null

    private val textFormatBundle by lazy { Bundle() }

    // bundle formate for AddTextInLine
    private val textInLineFormatBundle by lazy { Bundle() }

    private var footerText = arrayOf("*Thank You Visit Again*", "POWERED BY")
    /*var copyType = EPrintCopyType.MERCHANT*/

    private fun printLogo(logo: String) {
        // image
        val buffer: ByteArray?
        try {
            //
            val am: AssetManager = VerifoneApp.appContext.assets
            val ips: InputStream = am.open(logo)
            // get the size
            val size = ips.available()
            // crete the array of byte
            buffer = ByteArray(size)
            ips.read(buffer)
            // close the stream
            ips.close()
        } catch (e: IOException) {
            // Should never happen!
            throw RuntimeException(e)
        } catch (ex: Exception) {
            throw ex
        }
        try {
            val fmtImage = Bundle()
            fmtImage.putInt("offset", 0)
            fmtImage.putInt("width", 384) // bigger then actual, will print the actual
            fmtImage.putInt("height", 255) // bigger then actual, will print the actual//128 default
            //    logger("PS_IMG", (printer?.status).toString(), "e")
            printer?.addImage(fmtImage, buffer)
        } catch (ex: DeadObjectException) {
            throw  ex
        } catch (ex: RemoteException) {
            throw  ex
        } catch (ex: Exception) {
            throw  ex
        }
    }

    fun startPrinting(
        printerReceiptData: BatchFileDataTable,
        copyType: EPrintCopyType,
        context: Context?,
        printerCallback: (Boolean, Int) -> Unit
    ) {
        //  printer=null
        try {
            //  logger("PS_START", (printer?.status).toString(), "e")
            val signatureMsg = if (printerReceiptData.isPinverified) {
                "SIGNATURE NOT REQUIRED"
            } else {
                "SIGN ..............................................."
            }

            val pinVerifyMsg = if (printerReceiptData.isPinverified) {
                "PIN VERIFIED OK"
            } else {
                ""
            }
            // bundle format for addText
            val format = Bundle()
            // bundle formate for AddTextInLine
            val fmtAddTextInLine = Bundle()

            printLogo("amex_print.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            //  logger("PS_H1", (printer?.status).toString(), "e")
            printer?.addText(format, printerReceiptData.merchantName) // header1


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )

            //   logger("PS_H2", (printer?.status).toString(), "e")
            printer?.addText(format, printerReceiptData.merchantAddress1) //header2


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            //   logger("PS_H3", (printer?.status).toString(), "e")
            printer?.addText(format, printerReceiptData.merchantAddress2) //header3


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            val formatterdate = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val formattertime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            //val date = formatterdate.parse(printerReceiptData.transactionDate)
            //    val time = formattertime.parse(printerReceiptData.time)

            val time = printerReceiptData.time
            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            var formattedTime = ""
            try {
                val t1 = timeFormat.parse(time)
                formattedTime = timeFormat2.format(t1)
                Log.e("Time", formattedTime)
                //   logger("PS_DateTime", (printer?.status).toString(), "e")
                printer?.addTextInLine(
                    fmtAddTextInLine, "DATE : ${printerReceiptData.transactionDate}",
                    "", "TIME : $formattedTime", 0
                )
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //  logger("PS_MID_TID", (printer?.status).toString(), "e")
            printer?.addTextInLine(
                fmtAddTextInLine,
                "MID : ${printerReceiptData.mid}",
                "",
                "TID : ${printerReceiptData.tid}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);

            logger("PS_Bno_ROC", (printer?.status).toString(), "e")
            printer?.addTextInLine(
                fmtAddTextInLine,
                "BATCH NO : ${addPad(printerReceiptData.batchNumber, "0", 6)}",
                "",
                "ROC : ${invoiceWithPadding(printerReceiptData.roc)}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            logger("PS_invoice", (printer?.status).toString(), "e")
            printer?.addTextInLine(
                fmtAddTextInLine,
                "INVOICE : ${invoiceWithPadding(printerReceiptData.invoiceNumber)}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            /*  format.putInt(
                   PrinterConfig.addText.FontSize.BundleName,
                   PrinterConfig.addText.FontSize.LARGE_DH_32_64_IN_BOLD
               )
               format.putInt(
                   PrinterConfig.addText.Alignment.BundleName,
                   PrinterConfig.addText.Alignment.CENTER
               )
               logger("PS_transtype", (printer?.status).toString(), "e")
               printer?.addText(format, printerReceiptData.getTransactionType())*/

            printTransType(format, printerReceiptData.transactionType)

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            logger("PS_ct_exp", (printer?.status).toString(), "e")

            printer?.addTextInLine(
                fmtAddTextInLine,
                "CARD TYPE : ${printerReceiptData.cardType}",
                "",
                "EXP : XX/XX",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            logger("PS_ct_no", (printer?.status).toString(), "e")

            printer?.addTextInLine(
                fmtAddTextInLine,
                "CARD NO : ${printerReceiptData.cardNumber}",
                "",
                printerReceiptData.operationType,
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            if (printerReceiptData.merchantMobileNumber.isNotBlank())
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "MOBILE NO : ${printerReceiptData.merchantMobileNumber}",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            logger("PS_auth_rrn", (printer?.status).toString(), "e")

            if (printerReceiptData.authCode == "null") {
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "RRN : ${printerReceiptData.referenceNumber}",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            } else {
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "AUTH CODE : ${printerReceiptData.authCode.trim()}",
                    "",
                    "RRN : ${printerReceiptData.referenceNumber}",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            }

            if (printerReceiptData.operationType != "Mag") {
                fmtAddTextInLine.putInt(
                    PrinterConfig.addTextInLine.FontSize.BundleName,
                    PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                )
                fmtAddTextInLine.putString(
                    PrinterConfig.addTextInLine.GlobalFont.BundleName,
                    PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                )
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);

                //Condition nee to be here before inflating below tvr and tsi?

                if (printerReceiptData.operationType == "Chip") {
                    logger("PS_tvr_tsi", (printer?.status).toString(), "e")
                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "TVR : ${printerReceiptData.tvr}",
                        "",
                        "TSI : ${printerReceiptData.tsi}",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )
                }

                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                if (!printerReceiptData.aid.isBlank() && !printerReceiptData.tc.isBlank()) {
                    fmtAddTextInLine.putInt(
                        PrinterConfig.addTextInLine.FontSize.BundleName,
                        PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                    )
                    fmtAddTextInLine.putString(
                        PrinterConfig.addTextInLine.GlobalFont.BundleName,
                        PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                    )
                    logger("PS_aid", (printer?.status).toString(), "e")
                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "AID : ${printerReceiptData.aid}",
                        "",
                        "",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )

                    fmtAddTextInLine.putInt(
                        PrinterConfig.addTextInLine.FontSize.BundleName,
                        PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                    )
                    fmtAddTextInLine.putString(
                        PrinterConfig.addTextInLine.GlobalFont.BundleName,
                        PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                    )
                    //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                    //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                    logger("PS_Tc", (printer?.status).toString(), "e")
                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "TC : ${printerReceiptData.tc}",
                        "",
                        "",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )
                }

            }


            printSeperator(format)

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            val baseAmount = "%.2f".format(printerReceiptData.transactionalAmmount.toDouble() / 100)

            logger("PS_baseamt", (printer?.status).toString(), "e")
            printer?.feedLine(2)
            //  alignLeftRightText(fmtAddTextInLine, "", "")
            if (printerReceiptData.transactionType == TransactionType.VOID.type && printerReceiptData.tipAmmount != "") {
                //  printer?.feedLine(2)
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "BASE AMOUNT  :    Rs  ${"%.2f".format((printerReceiptData.totalAmmount.toFloat()) / 100)}",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )

            } else {
                //   printer?.feedLine(2)
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "BASE AMOUNT  :    Rs  ${printerReceiptData.baseAmmount}",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )

            }

            // alignLeftRightText(fmtAddTextInLine, "", "")
            //   val ttamount=(baseAmount.toFloat())+((printerReceiptData.tipAmmount.toFloat())/100)
            if (isTipAllowed && printerReceiptData.transactionType == TransactionType.TIP_SALE.type) {
                val tipamt = "%.2f".format((printerReceiptData.tipAmmount.toFloat()) / 100)
                printer?.feedLine(2)
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "TIP AMOUNT   :    Rs  $tipamt",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )

            } else if (isTipAllowed && printerReceiptData.transactionType == TransactionType.SALE.type) {
                printer?.feedLine(2)
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "TIP AMOUNT   :    ...............................",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            }


            //  val totalAmount = "%.2f".format(printerReceiptData.totalAmmount.toFloat() / 100)
            var tipAndTransAmount = 0.0
            /*   if(!printerReceiptData.tipAmmount.isBlank()){
                  val tipamt=(printerReceiptData.tipAmmount.toFloat())/100
                  tipAndTransAmount= tipamt+baseAmount.toFloat()
              }else{
                  tipAndTransAmount=baseAmount.toFloat()

              }*/

            if (printerReceiptData.transactionType == TransactionType.TIP_SALE.type) {
                tipAndTransAmount = printerReceiptData.totalAmmount.toDouble() / 100

            } else {
                tipAndTransAmount += baseAmount.toDouble()
            }

            printer?.feedLine(2)
            printer?.addTextInLine(
                fmtAddTextInLine,
                "TOTAL AMOUNT :    Rs  ${"%.2f".format(tipAndTransAmount)}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )
            //    centerText(format, "TOTAL AMOUNT :    Rs  $baseAmount")
            printSeperator(format)

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            if (printerReceiptData.isPinverified) {
                //  printer?.addText(format, pinVerifyMsg)
                centerText(format, pinVerifyMsg)
                centerText(format, signatureMsg)
            } else {
                printer?.feedLine(2)
                alignLeftRightText(format, pinVerifyMsg, "", "")
                alignLeftRightText(format, signatureMsg, "", "")
                printer?.feedLine(2)
                // printer?.addText(format, pinVerifyMsg)
                //  printer?.addText(format, signatureMsg)
            }

            centerText(format, printerReceiptData.cardHolderName)
            //  printer?.addText(format, printerReceiptData.cardHolderName)


            val ipt =
                IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)
            val chunks: List<String>? = ipt?.volletIssuerDisclammer?.let { chunkTnC(it) }
            if (chunks != null) {
                for (st in chunks) {
                    logger("TNC", st, "e")
                    //  printer?.addText(format,st)
                    alignLeftRightText(format, st, "", "")
                }
            }

            //   printer?.addText(format, ipt?.volletIssuerDisclammer)
            printer?.addText(format, copyType.pName)
            printer?.addText(format, footerText[0])
            printer?.addText(format, footerText[1])

            printLogo("BH.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, "App Version : ${BuildConfig.VERSION_NAME}")

            printer?.addText(format, "---------X-----------X----------")
            printer?.feedLine(4)

            // start print here
            printer?.startPrint(
                IPrintListener(
                    this,
                    context,
                    copyType,
                    printerReceiptData,
                    printerCallback
                )
            )
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } finally {
            //   VFService.connectToVFService(VerifoneApp.appContext)
        }
    }

    fun testStartPrinting(
        printerReceiptData: BatchFileDataTable,
        copyType: EPrintCopyType,
        context: Context?,
        printerCallback: (Boolean, Int) -> Unit
    ) {
        logger("PRINT STATUS", (printer?.status).toString())
        val signatureMsg = if (printerReceiptData.isPinverified) {
            "SIGNATURE NOT REQUIRED"
        } else {
            "SIGN ..............................................."
        }
        val pinVerifyMsg = if (printerReceiptData.isPinverified) {
            "PIN VERIFIED OK"
        } else {
            ""
        }
        try {
            // bundle format for addText
            val format = Bundle()

            // bundle formate for AddTextInLine
            val fmtAddTextInLine = Bundle()

            printLogo("amex_print.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantName) // header1


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantAddress1) //header2


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantAddress2) //header3


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            val formatterdate = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val formattertime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            //val date = formatterdate.parse(printerReceiptData.transactionDate)
            //    val time = formattertime.parse(printerReceiptData.time)

            val time = printerReceiptData.time
            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            var formattedTime = ""
            try {
                val t1 = timeFormat.parse(time)
                formattedTime = timeFormat2.format(t1)
                Log.e("Time", formattedTime)
            } catch (e: ParseException) {
                e.printStackTrace()
            }


            printer?.addTextInLine(
                fmtAddTextInLine, "DATE : ${printerReceiptData.transactionDate}",
                "", "TIME : $formattedTime", 0
            )


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "MID : ${printerReceiptData.mid}",
                "",
                "TID : ${printerReceiptData.tid}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);


            printer?.addTextInLine(
                fmtAddTextInLine,
                "BATCH NO : ${addPad(printerReceiptData.batchNumber, "0", 6)}",
                "",
                "ROC : ${invoiceWithPadding(printerReceiptData.roc)}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "INVOICE : ${invoiceWithPadding(printerReceiptData.invoiceNumber)}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_DH_24_48_IN_BOLD
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.getTransactionType())

            /* format.putInt(
                 PrinterConfig.addText.FontSize.BundleName,
                 PrinterConfig.addText.FontSize.NORMAL_24_24
             )
             format.putInt(
                 PrinterConfig.addText.Alignment.BundleName,
                 PrinterConfig.addText.Alignment.CENTER
             )
             printer?.addText(
                 format,
                 printerReceiptData.cardType
             ) // Need to Discuss this field value to be print?
 */

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "CARD TYPE : ${printerReceiptData.cardType}",
                "",
                "EXP : XX/XX",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "CARD NO : ${
                getMaskedPan(
                    TerminalParameterTable.selectFromSchemeTable(),
                    printerReceiptData.cardNumber
                )
                }",
                "",
                printerReceiptData.operationType,
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "AUTH CODE : ${printerReceiptData.authCode.trim()}",
                "",
                "RRN : ${printerReceiptData.referenceNumber}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            if (printerReceiptData.operationType != "Mag") {
                fmtAddTextInLine.putInt(
                    PrinterConfig.addTextInLine.FontSize.BundleName,
                    PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                )
                fmtAddTextInLine.putString(
                    PrinterConfig.addTextInLine.GlobalFont.BundleName,
                    PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                )
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);

                //Condition nee to be here before inflating below tvr and tsi?

                if (printerReceiptData.operationType == "Chip") {
                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "TVR : ${printerReceiptData.tvr}",
                        "",
                        "TSI : ${printerReceiptData.tsi}",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )
                }

                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                if (!printerReceiptData.aid.isBlank() && !printerReceiptData.tc.isBlank()) {
                    fmtAddTextInLine.putInt(
                        PrinterConfig.addTextInLine.FontSize.BundleName,
                        PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                    )
                    fmtAddTextInLine.putString(
                        PrinterConfig.addTextInLine.GlobalFont.BundleName,
                        PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                    )

                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "AID : ${printerReceiptData.aid}",
                        "",
                        "",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )

                    fmtAddTextInLine.putInt(
                        PrinterConfig.addTextInLine.FontSize.BundleName,
                        PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
                    )
                    fmtAddTextInLine.putString(
                        PrinterConfig.addTextInLine.GlobalFont.BundleName,
                        PrinterFonts.path + PrinterFonts.FONT_AGENCYR
                    )
                    //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                    //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
                    printer?.addTextInLine(
                        fmtAddTextInLine,
                        "TC : ${printerReceiptData.tc}",
                        "",
                        "",
                        PrinterConfig.addTextInLine.mode.Devide_flexible
                    )
                }

            }


            printSeperator(format)

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            val baseAmount = "%.2f".format(printerReceiptData.transactionalAmmount.toFloat() / 100)


            printer?.addTextInLine(
                fmtAddTextInLine,
                "BASE AMOUNT  :    Rs  ${printerReceiptData.baseAmmount}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            //   val ttamount=(baseAmount.toFloat())+((printerReceiptData.tipAmmount.toFloat())/100)
            if (isTipAllowed && printerReceiptData.transactionType == TransactionType.TIP_SALE.type) {
                val tipamt = "%.2f".format((printerReceiptData.tipAmmount.toFloat()) / 100)
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "TIP AMOUNT  :    Rs  $tipamt",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )

            } else if (isTipAllowed && printerReceiptData.transactionType == TransactionType.SALE.type) {
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "TIP AMOUNT  :       ...............................",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            }
            //  val totalAmount = "%.2f".format(printerReceiptData.totalAmmount.toFloat() / 100)
            var tipAndTransAmount = 0f
            /*   if(!printerReceiptData.tipAmmount.isBlank()){
                  val tipamt=(printerReceiptData.tipAmmount.toFloat())/100
                  tipAndTransAmount= tipamt+baseAmount.toFloat()
              }else{
                  tipAndTransAmount=baseAmount.toFloat()

              }*/

            if (printerReceiptData.transactionType == TransactionType.TIP_SALE.type) {
                tipAndTransAmount = (printerReceiptData.totalAmmount.toFloat()) / 100

            } else {
                tipAndTransAmount = baseAmount.toFloat()
            }


            printer?.addTextInLine(
                fmtAddTextInLine,
                "TOTAL AMOUNT :       Rs  ${"%.2f".format(tipAndTransAmount)}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )
            //    centerText(format, "TOTAL AMOUNT :    Rs  $baseAmount")
            printSeperator(format)

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, pinVerifyMsg)
            printer?.addText(format, signatureMsg)
            printer?.addText(format, printerReceiptData.cardHolderName)
            printer?.addText(format, copyType.pName)

            val ipt =
                IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)
            val chunks: List<String>? = ipt?.volletIssuerDisclammer?.let { chunkTnC(it) }
            if (chunks != null) {
                for (st in chunks) {
                    logger("TNC", st, "e")
                    printer?.addText(format, st)
                }
            }

            //   printer?.addText(format, ipt?.volletIssuerDisclammer)
            printer?.addText(format, footerText[0])
            printer?.addText(format, footerText[1])

            printLogo("BH.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, "App Version : ${BuildConfig.VERSION_NAME}")

            printer?.addText(format, "---------X-----------X----------")
            printer?.feedLine(4)

            // start print here
            printer?.startPrint(
                IPrintListener(
                    this,
                    context,
                    copyType,
                    printerReceiptData,
                    printerCallback
                )
            )
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } finally {
            //   VFService.connectToVFService(VerifoneApp.appContext)
        }
    }

    fun printDetailReport(
        batch: List<BatchFileDataTable>,
        context: Context?,
        printCB: (Boolean) -> Unit
    ) {
        try {
            val pp = printer?.status
            Log.e("Printer Status", pp.toString())
            if (pp == 0) {
                //-----------------------------------------------
                setLogoAndHeader()
                //  ------------------------------------------
                val appVersion = BuildConfig.VERSION_NAME

                val td = System.currentTimeMillis()
                val formatdate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                val formattime = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

                val date = formatdate.format(td)
                val time = formattime.format(td)
                val tpt = TerminalParameterTable.selectFromSchemeTable()

                alignLeftRightText(textInLineFormatBundle, "DATE : $date", "TIME : $time")
                alignLeftRightText(
                    textInLineFormatBundle,
                    "MID : ${tpt?.merchantId}",
                    "TID : ${tpt?.terminalId}"
                )

                alignLeftRightText(textInLineFormatBundle, "BATCH NO : ${tpt?.batchNumber}", "")

                centerText(textFormatBundle, "DETAIL REPORT", true)

                if (batch.isEmpty()) {
                    alignLeftRightText(textInLineFormatBundle, "Total Transaction", "0")
                } else {
                    alignLeftRightText(textInLineFormatBundle, "TRANS-TYPE", "AMOUNT")
                    alignLeftRightText(textInLineFormatBundle, "ISSUER", "PAN/CID")
                    alignLeftRightText(textInLineFormatBundle, "DATE-TIME", "INVOICE")
                    printSeperator(textFormatBundle)
                    val totalMap = mutableMapOf<Int, SummeryTotalType>()
                    val deformatter = SimpleDateFormat("yyMMdd HHmmss", Locale.ENGLISH)
                    for (b in batch) {
                        //  || b.transactionType == TransactionType.VOID_PREAUTH.type
                        if (b.transactionType == TransactionType.PRE_AUTH.type) continue  // Do not add pre auth transactions

                        if (totalMap.containsKey(b.transactionType)) {
                            val x = totalMap[b.transactionType]
                            if (x != null) {
                                x.count += 1
                                x.total += b.transactionalAmmount.toLong()
                            }
                        } else {
                            totalMap[b.transactionType] =
                                SummeryTotalType(1, b.transactionalAmmount.toLong())
                        }
                        val transAmount = "%.2f".format(b.transactionalAmmount.toDouble() / 100)
                        alignLeftRightText(
                            textInLineFormatBundle,
                            transactionType2Name(b.transactionType),
                            transAmount
                        )
                        if (b.transactionType == TransactionType.VOID_PREAUTH.type) {
                            alignLeftRightText(
                                textInLineFormatBundle,
                                b.cardType,
                                panMasking(b.encryptPan, "0000********0000")
                            )
                        } else {
                            alignLeftRightText(
                                textInLineFormatBundle,
                                b.cardType,
                                panMasking(b.cardNumber, "0000********0000")
                            )
                        }
                        if (b.transactionType == TransactionType.OFFLINE_SALE.type || b.transactionType == TransactionType.VOID_OFFLINE_SALE.type) {
                            try {
                                val dat = "${b.printDate} - ${b.time}"
                                alignLeftRightText(
                                    textInLineFormatBundle,
                                    dat,
                                    invoiceWithPadding(b.invoiceNumber)
                                )
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }

                        } else {
                            val timee = b.time
                            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
                            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            var formattedTime = ""
                            try {
                                val t1 = timeFormat.parse(timee)
                                formattedTime = timeFormat2.format(t1)
                                Log.e("Time", formattedTime)
                                val dat = "${b.transactionDate} - $formattedTime"
                                alignLeftRightText(
                                    textInLineFormatBundle,
                                    dat,
                                    invoiceWithPadding(b.invoiceNumber)
                                )
                                //alignLeftRightText(textInLineFormatBundle," "," ")
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }

                        printSeperator(textFormatBundle)
                    }
                    printSeperator(textFormatBundle)
                    centerText(textFormatBundle, "***TOTAL TRANSACTIONS***")

                    val sortedMap = totalMap.toSortedMap(compareByDescending { it })
                    /* for ((k, v) in sortedMap) {
                         alignLeftRightText(
                             textInLineFormatBundle,
                             "${transactionType2Name(k)} = ${v.count}",
                             "Rs %.2f".format(v.total.toDouble() / 100)
                         )
                     }*/

                    for ((k, m) in sortedMap) {
                        /* alignLeftRightText(
                             textInLineFormatBundle,
                             "${transactionType2Name(k).toUpperCase(Locale.ROOT)}${"     =" + m.count}",
                             "Rs.     ${"%.2f".format(((m.total).toDouble() / 100))}"

                         )*/

                        alignLeftRightText(
                            textInLineFormatBundle,
                            transactionType2Name(k).toUpperCase(Locale.ROOT),
                            "Rs.     ${"%.2f".format(((m.total).toDouble() / 100))}",
                            "  =  " + m.count

                        )

                    }

                }
                printSeperator(textFormatBundle)
                printer?.addText(textFormatBundle, "--------------------------------")
                centerText(textFormatBundle, "App Version :$appVersion")
                centerText(textFormatBundle, "---------X-----------X----------")
                printer?.feedLine(4)
                // start print here
                printer?.startPrint(object : PrinterListener.Stub() {
                    override fun onFinish() {
                        Log.e("DEATIL REPORT", "SUCESS__")
                        printCB(true)
                    }

                    override fun onError(error: Int) {
                        Log.e("DEATIL REPORT", "FAIL__")
                        printCB(false)
                    }


                })
            }
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } finally {
            //   VFService.connectToVFService(VerifoneApp.appContext)
        }
    }

    fun printReversal(context: Context?, callback: (String) -> Unit) {
        val isoW = AppPreference.getReversal()

        if (isoW != null) {
            try {
                setLogoAndHeader()

                val cal = Calendar.getInstance()
                cal.timeInMillis = isoW.timeStamp
                val yr = cal.get(Calendar.YEAR).toString()
                val of12 = isoW.isoMap[12]?.rawData ?: ""
                val of13 = isoW.isoMap[13]?.rawData ?: ""

                val d = of13 + yr
                val roc = isoW.isoMap[11]?.rawData ?: ""
                val tid = isoW.isoMap[41]?.parseRaw2String() ?: ""
                val mid = isoW.isoMap[42]?.parseRaw2String() ?: ""
                val batchdata = isoW.isoMap[60]?.parseRaw2String() ?: ""


                val batch = batchdata.split("|")[0]

                var amountStr = isoW.isoMap[4]?.rawData ?: "0"
                val amt = amountStr.toFloat() / 100
                amountStr = "%.2f".format(amt)

                val date = "${d.substring(0, 2)}/${d.substring(2, 4)}/${d.substring(4, d.length)}"
                val time =
                    "${of12.substring(0, 2)}:${of12.substring(2, 4)}:${of12.substring(
                        4,
                        of12.length
                    )}"
                alignLeftRightText(textInLineFormatBundle, "DATE : ${date}", "TIME : ${time}")
                alignLeftRightText(textInLineFormatBundle, "MID : ${mid}", "TID : ${tid}")
                alignLeftRightText(
                    textInLineFormatBundle,
                    "BATCH NO  : ${batch}",
                    "ROC : ${invoiceWithPadding(roc)}"
                )

                centerText(textFormatBundle, "TRANSACTION FAILED")

                val cardType = isoW.additionalData["cardType"] ?: ""
                val card = isoW.additionalData["pan"] ?: ""
                if (card.isNotEmpty())
                    alignLeftRightText(
                        textInLineFormatBundle,
                        "CARD NO : $card",
                        cardType
                    )//chip,swipe,cls


                val tvr = isoW.additionalData["tvr"] ?: ""
                var tsi = isoW.additionalData["tsi"] ?: ""
                var aid = isoW.additionalData["aid"] ?: ""

                printer?.addText(textFormatBundle, "--------------------------------")

                if (tsi.isNotEmpty() && tvr.isNotEmpty()) {
                    alignLeftRightText(textInLineFormatBundle, "TVR : ${tvr}", "TSI : ${tsi}")
                }


                if (aid.isNotEmpty()) {
                    aid = "AID : $aid"
                    alignLeftRightText(textInLineFormatBundle, aid, "")
                }

                printSeperator(textFormatBundle)
                centerText(textFormatBundle, "TOTAL AMOUNT : Rs $amountStr")
                printSeperator(textFormatBundle)

                centerText(
                    textFormatBundle,
                    "Please contact your card issuer for reversal of debit if any."
                )
                centerText(textFormatBundle, "POWERED BY")
                printLogo("BH.bmp")

                centerText(textFormatBundle, "APP VER : ${BuildConfig.VERSION_NAME}")


                printer?.feedLine(4)

                // start print here
                printer?.startPrint(object : PrinterListener.Stub() {
                    override fun onFinish() {
                        Log.e("CANCEL RECEIPT", "SUCESS__")
                        callback("Printing Success")
                    }

                    override fun onError(error: Int) {
                        Log.e("CANCEL RECEIPT", "FAIL__")
                        callback("Error in Printing")
                    }
                })

            } catch (ex: DeadObjectException) {
                context?.getString(R.string.something_went_wrong)?.let { callback(it) }
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (e: RemoteException) {
                context?.getString(R.string.something_went_wrong)?.let { callback(it) }
                e.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (ex: Exception) {
                context?.getString(R.string.something_went_wrong)?.let { callback(it) }
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            }
        } else {
            callback("No Cancel Receipt Found")
        }
    }

    private fun setHeaderWithLogo(
        format: Bundle,
        img: String,
        headers: ArrayList<String>,
        context: Context? = null
    ) {
        printLogo(img)
        centerText(format, headers[0])
        centerText(format, headers[1])
        centerText(format, headers[2])
    }

    private fun centerText(format: Bundle, text: String, bold: Boolean = false) {
        if (!bold) {
            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, text)

        } else {
            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_DH_24_48_IN_BOLD
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, text)
        }

    }

    private fun alignLeftRightText(
        fmtAddTextInLine: Bundle,
        leftText: String,
        rightText: String,
        middleText: String = ""
    ) {
        try {
            val mode = if (middleText == "") {
                1
            } else {
                0
            }

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )

            printer?.addTextInLine(
                fmtAddTextInLine, leftText,
                middleText, rightText, mode
            )
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }
        // PrinterConfig.addTextInLine.mode.Devide_flexible
    }


    fun printSettlementReport(
        context: Context?,
        batch: MutableList<BatchFileDataTable>,
        isSettlementSuccess: Boolean = false,
        isLastSummary: Boolean = false,
        callBack: (Boolean) -> Unit
    ) {
        //  val format = Bundle()
        //   val fmtAddTextInLine = Bundle()

//below if condition is for zero settlement
        if (batch.size <= 0) {
            try {
                centerText(textFormatBundle, "SETTLEMENT SUCCESSFUL")

                val tpt = TerminalParameterTable.selectFromSchemeTable()
                tpt?.receiptHeaderOne?.let { centerText(textInLineFormatBundle, it) }
                tpt?.receiptHeaderTwo?.let { centerText(textInLineFormatBundle, it) }
                tpt?.receiptHeaderThree?.let { centerText(textInLineFormatBundle, it) }


                val td = System.currentTimeMillis()
                val formatdate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                val formattime = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

                val date = formatdate.format(td)
                val time = formattime.format(td)

                alignLeftRightText(textInLineFormatBundle, "DATE : $date", "TIME : $time")
                if (isLastSummary) {
                    centerText(textFormatBundle, "LAST SUMMARY REPORT")
                } else {
                    centerText(textFormatBundle, "SUMMARY REPORT")
                }

                alignLeftRightText(
                    textInLineFormatBundle,
                    "TID : ${tpt?.terminalId}",
                    "MID : ${tpt?.merchantId}"
                )
                alignLeftRightText(textInLineFormatBundle, "BATCH NO : ${tpt?.batchNumber}", "")
                printSeperator(textFormatBundle)
                alignLeftRightText(textInLineFormatBundle, "TOTAL TXN    =  0", "Rs.         0.00")

                centerText(textFormatBundle, "ZERO SETTLEMENT SUCCESSFUL")
                centerText(textFormatBundle, "BonusHub")
                centerText(textFormatBundle, "App Version : ${BuildConfig.VERSION_NAME}")
                printer?.feedLine(4)

                printer?.startPrint(object : PrinterListener.Stub() {
                    override fun onFinish() {
                        callBack(true)
                        Log.e("Settle_RECEIPT", "SUCESS__")
                    }

                    override fun onError(error: Int) {
                        callBack(false)
                        Log.e("Settle_RECEIPT", "FAIL__")
                    }


                })
            } catch (ex: DeadObjectException) {
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (e: RemoteException) {
                e.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            }
        }
        ////below if condition is for settlement(Other than zero settlement)
        else {
            try {
                val map = mutableMapOf<String, MutableMap<Int, SummeryModel>>()
                val tpt = TerminalParameterTable.selectFromSchemeTable()
                val headers = arrayListOf<String>()
                tpt?.receiptHeaderOne?.let { headers.add(it) }
                tpt?.receiptHeaderTwo?.let { headers.add(it) }
                tpt?.receiptHeaderThree?.let { headers.add(it) }

                setHeaderWithLogo(textFormatBundle, "amex_print.bmp", headers)

                val td = System.currentTimeMillis()
                val formatdate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                val formattime = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

                val date = formatdate.format(td)
                val time = formattime.format(td)

                alignLeftRightText(textInLineFormatBundle, "DATE : $date", "TIME : $time")

                //  alignLeftRightText(fmtAddTextInLine,"DATE : ${batch.date}","TIME : ${batch.time}")
                alignLeftRightText(
                    textInLineFormatBundle,
                    "MID : ${batch[0].mid}",
                    "TID : ${batch[0].tid}"
                )
                alignLeftRightText(
                    textInLineFormatBundle,
                    "BATCH NO  : ${batch[0].batchNumber}",
                    ""
                )

                if (isLastSummary) {
                    centerText(textFormatBundle, "LAST SUMMARY REPORT")
                } else {
                    centerText(textFormatBundle, "SUMMARY REPORT")
                }


                for (it in batch) {  // Do not count preauth transaction
// || it.transactionType == TransactionType.VOID_PREAUTH.type
                    if (it.transactionType == TransactionType.PRE_AUTH.type) continue

                    val transAmt = try {
                        it.transactionalAmmount.toLong()
                    } catch (ex: Exception) {
                        0L
                    }
                    if (map.containsKey(it.cardType)) {
                        val ma = map[it.cardType] as MutableMap<Int, SummeryModel>
                        if (ma.containsKey(it.transactionType)) {
                            val m = ma[it.transactionType] as SummeryModel
                            m.count += 1
                            m.total += transAmt
                        } else {
                            val sm =
                                SummeryModel(transactionType2Name(it.transactionType), 1, transAmt)
                            ma[it.transactionType] = sm
                        }
                    } else {
                        val hm = HashMap<Int, SummeryModel>().apply {
                            this[it.transactionType] =
                                SummeryModel(transactionType2Name(it.transactionType), 1, transAmt)
                        }
                        map[it.cardType] = hm
                    }
                }

                val totalMap = mutableMapOf<Int, SummeryTotalType>()

                for ((key, _map) in map) {
                    if (key.isNotBlank()) {
                        printSeperator(textFormatBundle)
                        alignLeftRightText(
                            textInLineFormatBundle,
                            "CARD ISSUER:  ",
                            "",
                            key.toUpperCase(Locale.ROOT)
                        )
                        // if(ind==0){
                        alignLeftRightText(textInLineFormatBundle, "TXN TYPE", "TOTAL", "COUNT")
                        //   ind=1
                        //  }
                    }
                    for ((k, m) in _map) {
                        val amt = "Rs  " + "%.2f".format(((m.total).toDouble() / 100))
                        if (k == TransactionType.PRE_AUTH_COMPLETE.type || k == TransactionType.VOID_PREAUTH.type) {
                            // need Not to show
                        } else {
                            alignLeftRightText(
                                textInLineFormatBundle,
                                m.type.toUpperCase(Locale.ROOT),
                                amt,
                                m.count.toString()
                            )
                        }
                        if (totalMap.containsKey(k)) {
                            val x = totalMap[k]
                            if (x != null) {
                                x.count += m.count
                                x.total += m.total
                            }
                        } else {
                            totalMap[k] = SummeryTotalType(m.count, m.total)
                        }

                    }
                    //  sb.appendln()
                }
                printSeperator(textFormatBundle)
                centerText(textInLineFormatBundle, "*** TOTAL TRANSACTION ***")
                val sortedMap = totalMap.toSortedMap(compareByDescending { it })
                for ((k, m) in sortedMap) {
                    /* alignLeftRightText(
                         textInLineFormatBundle,
                         "${transactionType2Name(k).toUpperCase(Locale.ROOT)}${"     =" + m.count}",
                         "Rs.     ${"%.2f".format(((m.total).toDouble() / 100))}"

                     )*/

                    alignLeftRightText(
                        textInLineFormatBundle,
                        transactionType2Name(k).toUpperCase(Locale.ROOT),
                        "Rs.     ${"%.2f".format(((m.total).toDouble() / 100))}", "  =  " + m.count

                    )

                }
                //    sb.appendln(getChar(LENGTH, '='))

                printSeperator(textFormatBundle)
                if (isSettlementSuccess) {
                    centerText(textInLineFormatBundle, "SETTLEMENT SUCCESSFUL")
                    centerText(textFormatBundle, "Bonushub")
                }
                centerText(textFormatBundle, "App Version : ${BuildConfig.VERSION_NAME}")

                centerText(textFormatBundle, "---------X-----------X----------")
                printer?.feedLine(4)

                // start print here
                printer?.startPrint(object : PrinterListener.Stub() {
                    override fun onFinish() {
                        callBack(true)
                        Log.e("Settle_RECEIPT", "SUCESS__")
                    }

                    override fun onError(error: Int) {
                        callBack(false)
                        Log.e("Settle_RECEIPT", "FAIL__")
                    }


                })
            } catch (ex: DeadObjectException) {
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (e: RemoteException) {
                e.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                failureImpl(
                    context as Activity,
                    "Printer Service stopped.",
                    "Please take chargeslip from the Report menu."
                )
            }
        }
    }

    fun printAuthCompleteChargeSlip(
        printerReceiptData: BatchFileDataTable,
        copyType: EPrintCopyType,
        context: Context?,
        printerCallback: (Boolean) -> Unit
    ) {
        val signatureMsg = "SIGN ..............................................."

        try {
            // bundle format for addText
            val format = Bundle()

            // bundle formate for AddTextInLine
            val fmtAddTextInLine = Bundle()

            printLogo("amex_print.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantName) // header1


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantAddress1) //header2


            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, printerReceiptData.merchantAddress2) //header3


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )

            val time = printerReceiptData.time
            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            var formattedTime = ""
            try {
                val t1 = timeFormat.parse(time)
                formattedTime = timeFormat2.format(t1)
                Log.e("Time", formattedTime)
            } catch (e: ParseException) {
                e.printStackTrace()
            }


            printer?.addTextInLine(
                fmtAddTextInLine, "DATE : ${printerReceiptData.transactionDate}",
                "", "TIME : $formattedTime", 0
            )


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "MID : ${printerReceiptData.mid}",
                "",
                "TID : ${printerReceiptData.tid}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )


            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "BATCH NO : ${printerReceiptData.batchNumber}",
                "",
                "ROC : ${invoiceWithPadding(printerReceiptData.roc)}",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "INVOICE : ${invoiceWithPadding(printerReceiptData.invoiceNumber)}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )
            // Seperator
            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )

            printer?.addText(format, "--------------------------------")
            centerText(fmtAddTextInLine, "ENTERED DETAILS")

            if (printerReceiptData.transactionType == TransactionType.PRE_AUTH_COMPLETE.type)
                alignLeftRightText(fmtAddTextInLine, "TID : ${printerReceiptData.authTID}", "")

            alignLeftRightText(
                fmtAddTextInLine,
                "BATCH NO : ${invoiceWithPadding(printerReceiptData.authBatchNO)}",
                "ROC : ${invoiceWithPadding(printerReceiptData.authROC)}"
            )

            printer?.addText(format, "--------------------------------")

            /*   format.putInt(
                   PrinterConfig.addText.FontSize.BundleName,
                   PrinterConfig.addText.FontSize.NORMAL_DH_24_48_IN_BOLD
               )
               format.putInt(
                   PrinterConfig.addText.Alignment.BundleName,
                   PrinterConfig.addText.Alignment.CENTER
               )
               printer?.addText(format, printerReceiptData.getTransactionType())*/
            printTransType(format, printerReceiptData.transactionType)

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            printer?.addTextInLine(
                fmtAddTextInLine,
                "CARD NO : ${printerReceiptData.encryptPan}",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);

            if (printerReceiptData.authCode == "null") {
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "RRN : ${printerReceiptData.referenceNumber}",
                    "",
                    "",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            } else {
                printer?.addTextInLine(
                    fmtAddTextInLine,
                    "AUTH CODE : ${printerReceiptData.authCode.trim()}",
                    "",
                    "RRN : ${printerReceiptData.referenceNumber}",
                    PrinterConfig.addTextInLine.mode.Devide_flexible
                )
            }

            printSeperator(format)

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            var baseAmount = "00"
            when (printerReceiptData.transactionType) {
                TransactionType.PRE_AUTH_COMPLETE.type -> {
                    baseAmount =
                        "%.2f".format(printerReceiptData.transactionalAmmount.toDouble() / 100)
                }
                TransactionType.VOID_PREAUTH.type -> {
                    baseAmount =
                        "%.2f".format(printerReceiptData.amountInResponse.toDouble() / 100)
                }
            }

            printer?.addTextInLine(
                fmtAddTextInLine,
                "BASE AMOUNT  :    Rs  $baseAmount",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )

            fmtAddTextInLine.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            fmtAddTextInLine.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            //   printer.addTextInLine( fmtAddTextInLine, "L & R", "", "Divide Equally", 0);
            val totalAmount = "%.2f".format(printerReceiptData.totalAmmount.toDouble() / 100)
            printer?.addTextInLine(
                fmtAddTextInLine,
                "TOTAL AMOUNT :    Rs  $baseAmount",
                "",
                "",
                PrinterConfig.addTextInLine.mode.Devide_flexible
            )
            printSeperator(format)

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.feedLine(2)
            alignLeftRightText(format, signatureMsg, "", "")
            printer?.feedLine(2)
            val ipt =
                IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)

            val chunks: List<String>? = ipt?.volletIssuerDisclammer?.let { chunkTnC(it) }
            if (chunks != null) {
                for (st in chunks) {
                    logger("TNC", st, "e")
                    alignLeftRightText(format, st, "", "")
                }
            }
            //   printer?.addText(format, ipt?.volletIssuerDisclammer)
            printer?.addText(format, copyType.pName)
            printer?.addText(format, footerText[0])
            printer?.addText(format, footerText[1])



            printLogo("BH.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, "App Version : ${BuildConfig.VERSION_NAME}")

            printer?.addText(format, "---------X-----------X----------")
            printer?.feedLine(4)

            // start print here
            printer?.startPrint(
                IAuthCompletePrintListener(
                    this,
                    context,
                    copyType,
                    printerReceiptData,
                    printerCallback
                )
            )


        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } finally {
            //   VFService.connectToVFService(VerifoneApp.appContext)
        }
    }

    fun printTenure(
        context: Context?,
        issuerDataModelList: ArrayList<IssuerDataModel>,
        amt: Float
    ) {
        try {
            //    var tenure= arrayListOf<TenureDataModel>()
            setLogoAndHeader()
            val terminalData = TerminalParameterTable.selectFromSchemeTable()
            val dateTime: Long = Calendar.getInstance().timeInMillis
            val time: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(dateTime)
            val date: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateTime)
            val year: String = SimpleDateFormat("YY", Locale.getDefault()).format(dateTime)
            logger("AUTH YEAR->", year, "e")
            alignLeftRightText(textInLineFormatBundle, "DATE : $date", "TIME : $time")
            alignLeftRightText(
                textInLineFormatBundle,
                "MID : ${terminalData?.merchantId}",
                "TID : ${terminalData?.terminalId}"
            )
            alignLeftRightText(
                textInLineFormatBundle,
                "BATCH NO : ${terminalData?.batchNumber}",
                ""
            )
            textFormatBundle.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.LARGE_DH_32_64_IN_BOLD
            )
            textFormatBundle.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(textFormatBundle, "EMI ENQUIRY")
            //  centerText(textFormatBundle, "EMI CATALOGUE", true)
            fun printt(tenureData: TenureDataModel) {

                val rateOfInterest = "%.2f".format(tenureData.roi.toFloat() / 100) + " %"
                centerText(
                    textInLineFormatBundle,
                    "Tenure :  ${tenureData.tenure} Month INTEREST RATE : $rateOfInterest"
                )

                val procFee =
                    ((tenureData.proccesingFee.toFloat() / 100) + ((tenureData.processingRate.toFloat() / 100) * ((amt - (tenureData.emiAmount?.discount
                        ?: 0f))) / 100))

                var procCodePrint = ""
                procCodePrint = if (procFee <= 0f) {
                    "%.2f".format(procFee) + " %"
                } else {
                    "%.2f".format(procFee)
                }

                alignLeftRightText(
                    textInLineFormatBundle,
                    "PROCESSING FEE",
                    procCodePrint,
                    " :  Rs"
                )
                val amtToPrint = "%.2f".format(amt)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "AMOUNT",
                    amtToPrint,
                    "  :  Rs"
                )
                val loanAmtToPrint = "%.2f".format(tenureData.emiAmount?.principleAmt)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "LOAN AMOUNT",
                    loanAmtToPrint,
                    "  :  Rs"
                )

                val monthlyemitoPrint = "%.2f".format(tenureData.emiAmount?.monthlyEmi)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "MONTHLY EMI",
                    monthlyemitoPrint,
                    "  :  Rs"
                )

                //format two decimal places //loanintrest
                val toi = "%.2f".format(tenureData.emiAmount?.totalInterest)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "TOTAL INTEREST",
                    toi,
                    "  :  Rs"
                )


//total payment

                val tp = totalPaymentforTenure(amt, tenureData)
                val tpf = "%.2f".format(tp)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "TOTAL PAYMENT",
                    tpf,
                    "  :  Rs"
                )
                if (tenureData.emiAmount?.benefitCalc == EBenefitCalculation.FIXED_VALUE) {
                    tenureData.emiAmount?.cashBackpercent = tenureData.emiAmount?.cashBack!!
                }

                if (tenureData.emiAmount?.cashBackpercent!! > 0f) {
                    var percentSign = ""
                    if (tenureData.emiAmount?.benefitCalc == EBenefitCalculation.PERCENTAGE_VALUE)
                        percentSign = " %"
                    val cashBackPercentToPrint =
                        "%.2f".format(tenureData.emiAmount?.cashBackpercent)
                    alignLeftRightText(
                        textInLineFormatBundle,
                        "CASHBACK",
                        cashBackPercentToPrint + percentSign,
                        "  :  Rs"
                    )

                }
                if (tenureData.emiAmount?.cashBack!! > 0f) {
                    val cashBacktoPrint = "%.2f".format(tenureData.emiAmount?.cashBack)
                    alignLeftRightText(
                        textInLineFormatBundle,
                        "TOTAL CASHBACK",
                        cashBacktoPrint,
                        "  :  Rs"
                    )
                }
            }

            var schemesToPrint = ArrayList<TenureDataModel>()
            for (index in issuerDataModelList.indices) {
                centerText(
                    textFormatBundle,
                    "BANK NAME : ${issuerDataModelList.size}.${issuerDataModelList[index].issuerName}"
                )

                for (schemeIndex in issuerDataModelList[index].schemeDataModel.indices) {
                    centerText(textFormatBundle, "SCHEME : ${schemeIndex + 1}")

                    val schemesTenureData =
                        issuerDataModelList[index].schemeDataModel[schemeIndex].tenureDataModel.toCollection(
                            ArrayList()
                        )
                    //Added by Lucky
                    schemesToPrint = arrayListOf<TenureDataModel>()
                    for (ss in schemesTenureData) {
                        if (ss.isChecked) {
                            schemesToPrint.add(ss)
                        }
                    }
                    for (tenure in schemesToPrint) {
                        // attach the EMI Calculation with each tenure
                        printSeperator(textFormatBundle)
                        printt(tenure)

                    }

                }
            }

            printSeperator(textFormatBundle)
            printer?.feedLine(1)

            printer?.addText(textFormatBundle, footerText[1])

            printLogo("BH.bmp")
            printer?.addText(textFormatBundle, "App Version : ${BuildConfig.VERSION_NAME}")
            printer?.addText(textFormatBundle, "---------X-----------X----------")
            printer?.feedLine(4)

            //  if (schemesToPrint.size > 0) {
            printer?.startPrint(object : PrinterListener.Stub() {
                override fun onFinish() {
                    logger("Print", "Success")
                }

                override fun onError(error: Int) {
                    logger("Print", "Fail")
                }
            })
            //      } else {
            //    VFService.showToast("Select tenure")
            //    printer?.cleanCache()
            //    return

            //    }

        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        }

    }

    private fun totalPaymentforTenure(amount: Float, tenure: TenureDataModel): Float {
        /*return amount + tenure.proccesingFee.toFloat() - (tenure.emiAmount?.cashBack
            ?: 0f) - (tenure.emiAmount?.discount ?: 0f) + (tenure.emiAmount?.totalInterest ?: 0f)*/

        return amount + tenure.proccesingFee.toFloat() / 100 + ((tenure.processingRate.toFloat() / 100) * (amount - (tenure.emiAmount?.discount
            ?: 0f))) / 100 - (tenure.emiAmount?.cashBack
            ?: 0f) - (tenure.emiAmount?.discount ?: 0f) + (tenure.emiAmount?.totalInterest ?: 0f)
    }


    fun printEMISale(
        printerReceiptData: BatchFileDataTable,
        copyType: EPrintCopyType,
        context: Context?,
        printerCallback: (Boolean, Int) -> Unit
    ) {
        try {
            hasPin(printerReceiptData)
            setLogoAndHeader()
            printTransDatetime(printerReceiptData)
            //===========================
            alignLeftRightText(
                textInLineFormatBundle,
                "MID : ${printerReceiptData.mid}",
                "TID : ${printerReceiptData.tid}"
            )
            alignLeftRightText(
                textInLineFormatBundle,
                "BATCH NO : ${printerReceiptData.batchNumber}",
                "ROC : ${invoiceWithPadding(printerReceiptData.roc)}"
            )
            var mBillno = ""
            if (printerReceiptData.merchantBillNumber.isNotBlank()) {
                mBillno = "M.BILL NO : " + printerReceiptData.merchantBillNumber
            }

            alignLeftRightText(
                textInLineFormatBundle,
                "INVOICE : ${invoiceWithPadding(printerReceiptData.invoiceNumber)}",
                mBillno
            )
            // printer?.addText(textFormatBundle, printerReceiptData.getTransactionType())
            centerText(textFormatBundle, printerReceiptData.getTransactionType(), true)
            alignLeftRightText(
                textInLineFormatBundle,
                "CARD NO : ${printerReceiptData.cardNumber}",
                printerReceiptData.operationType
            )

            alignLeftRightText(
                textInLineFormatBundle,
                "CARD TYPE : ${printerReceiptData.cardType}",
                "EXP : XX/XX"
            )

            if (printerReceiptData.merchantMobileNumber.isNotBlank())
                alignLeftRightText(
                    textInLineFormatBundle,
                    "MOBILE NO : ${printerReceiptData.merchantMobileNumber}",
                    ""
                )

            alignLeftRightText(
                textInLineFormatBundle,
                "AUTH CODE : ${printerReceiptData.authCode.trim()}",
                "RRN : ${printerReceiptData.referenceNumber}"
            )

            if (printerReceiptData.operationType != "Mag") {
                //Condition nee to be here before inflating below tvr and tsi?
                if (printerReceiptData.operationType == "Chip") {
                    alignLeftRightText(
                        textInLineFormatBundle,
                        "TVR : ${printerReceiptData.tvr}",
                        "TSI : ${printerReceiptData.tsi}"
                    )
                }
                if (!printerReceiptData.aid.isBlank() && !printerReceiptData.tc.isBlank()) {
                    alignLeftRightText(
                        textInLineFormatBundle,
                        "AID : ${printerReceiptData.aid}",
                        ""
                    )
                    alignLeftRightText(textInLineFormatBundle, "TC : ${printerReceiptData.tc}", "")
                }
            }

            printSeperator(textFormatBundle)

            //  val txnAmount=(((printerReceiptData.transactionAmt).toFloat())%100)
            val txnAmount = "%.2f".format(printerReceiptData.transactionalAmmount.toFloat() / 100)
            alignLeftRightText(textInLineFormatBundle, "TXN AMOUNT", txnAmount, ":  Rs ")
            alignLeftRightText(
                textInLineFormatBundle,
                "CARD ISSUER",
                "",
                "  ${printerReceiptData.cardType}"
            )

            val rateOfInterest = "%.2f".format(printerReceiptData.roi.toFloat() / 100) + " %"
            alignLeftRightText(textInLineFormatBundle, "ROI (p.a)", "", "  $rateOfInterest")
            alignLeftRightText(
                textInLineFormatBundle,
                "TENURE",
                "",
                "  ${printerReceiptData.tenure}  months"
            )
//proc fee
            var procCodePrint = ""
            if (printerReceiptData.processingFee.toFloat() <= 0f) {
                procCodePrint = "%.2f".format(printerReceiptData.processingFee.toFloat()) + " %"
            } else {
                procCodePrint = "%.2f".format(printerReceiptData.processingFee.toFloat())
            }

            alignLeftRightText(
                textInLineFormatBundle,
                "PROCESSING FEE",
                procCodePrint,
                ":  Rs"
            )

            //cash back
            if (printerReceiptData.cashBackPercent.toFloat() > 0f) {
                var cashBackPercentSign = ""
                if (printerReceiptData.isCashBackInPercent) {
                    cashBackPercentSign = " %"
                }
                val cashPercentPrint = "%.2f".format(printerReceiptData.cashBackPercent.toFloat())
                alignLeftRightText(
                    textInLineFormatBundle,
                    "CASH BACK",
                    cashPercentPrint,
                    ":  Rs"
                )
            }
            if (printerReceiptData.cashback != "") {
                //cashback amt
                val cashBackPrint = "%.2f".format(printerReceiptData.cashback.toFloat() / 100)
                alignLeftRightText(
                    textInLineFormatBundle,
                    "TOTAL CASH BACK",
                    cashBackPrint,
                    ":  Rs"
                )
            }
            val loanAmount = "%.2f".format(printerReceiptData.loanAmt.toFloat() / 100)
            alignLeftRightText(textInLineFormatBundle, "LOAN AMOUNT", loanAmount, ":  Rs")

            val monthlyEMI = "%.2f".format(printerReceiptData.monthlyEmi.toFloat() / 100)
            alignLeftRightText(textInLineFormatBundle, "MONTHLY EMI", monthlyEMI, ":  Rs")
            alignLeftRightText(
                textInLineFormatBundle,
                "TOTAL INTEREST",
                printerReceiptData.totalInterest,
                ":  Rs"
            )
            val netPay = "%.2f".format(printerReceiptData.netPay.toFloat() / 100)

            alignLeftRightText(textInLineFormatBundle, "TOTAL PAYOUT", netPay, ":  Rs")

            printSeperator(textFormatBundle)
            printer?.setLineSpace(1)
            alignLeftRightText(textInLineFormatBundle, "CUSTOMER CONSENT FOR EMI", "")
            printer?.setLineSpace(1)
            val est = EmiSchemeTable.selectFromEmiSchemeTable()
                .first { it.emiSchemeId == printerReceiptData.emiSchemeId }

            val disclaimer = est.disclaimer
            var emiDis = ""
            var issDis = ""
            if (disclaimer.contains(disclaimerEmiOpen, true) && disclaimer.contains(
                    disclaimerEmiClose,
                    true
                )
            ) {
                emiDis = disclaimer.substring(
                    disclaimer.indexOf(disclaimerEmiOpen),
                    disclaimer.indexOf(disclaimerEmiClose)
                )
                val emiDisArr = emiDis.split("#")
                if (emiDisArr.size > 1) {
                    for (i in 1 until emiDisArr.size) {
                        val limit = 48
                        val emiTnc = "#" + emiDisArr[i]
                        val chunks: List<String> = chunkTnC(emiTnc, limit)
                        for (st in chunks) {
                            logger("TNC", st, "e")
                            alignLeftRightText(textInLineFormatBundle, st, "")
                        }
                    }
                }
            } else {
                emiDis = disclaimer
                alignLeftRightText(textInLineFormatBundle, emiDis, "")
            }

            printSeperator(textFormatBundle)

            centerText(textFormatBundle, "BASE AMOUNT  :     Rs  $txnAmount", true)
            printer?.feedLine(2)
            if (printerReceiptData.isPinverified) {
                //  printer?.addText(format, pinVerifyMsg)
                pinVerifyMsg?.let { centerText(textInLineFormatBundle, it) }
                signatureMsg?.let { centerText(textInLineFormatBundle, it) }
            } else {
                printer?.feedLine(2)
                pinVerifyMsg?.let { alignLeftRightText(textInLineFormatBundle, it, "", "") }
                signatureMsg?.let { alignLeftRightText(textInLineFormatBundle, it, "", "") }
                printer?.feedLine(2)
                // printer?.addText(format, pinVerifyMsg)
                //  printer?.addText(format, signatureMsg)
            }

            centerText(textInLineFormatBundle, printerReceiptData.cardHolderName)
            //  printer?.addText(format, printerReceiptData.cardHolderName)


            val ipt =
                IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)
            val chunks: List<String>? = ipt?.volletIssuerDisclammer?.let { chunkTnC(it) }
            if (chunks != null) {
                for (st in chunks) {
                    logger("TNC", st, "e")
                    //  printer?.addText(format,st)
                    alignLeftRightText(textInLineFormatBundle, st, "", "")
                }
            }
            //    printer?.addText(textInLineFormatBundle, ipt?.volletIssuerDisclammer)
            printer?.feedLine(2)
            centerText(textInLineFormatBundle, copyType.pName)
            printer?.addText(textInLineFormatBundle, footerText[0])
            printer?.addText(textInLineFormatBundle, footerText[1])

            printLogo("BH.bmp")

            printer?.addText(textInLineFormatBundle, "App Version : ${BuildConfig.VERSION_NAME}")

            printSeperator(textFormatBundle)

            if (disclaimer.contains(disclaimerIssuerOpen, true) && disclaimer.contains(
                    disclaimerIssuerClose,
                    true
                )
            ) {
                issDis = disclaimer.substring(
                    disclaimer.indexOf(disclaimerIssuerOpen),
                    disclaimer.indexOf(disclaimerIssuerClose)
                )
                val issDisArr = issDis.split("#")

                if (issDisArr.size > 1) {
                    for (i in 1 until issDisArr.size) {
                        val limit = 48
                        val emiTnc = "#" + issDisArr[i]
                        val chunks: List<String> = chunkTnC(emiTnc, limit)
                        for (st in chunks) {
                            logger("TNC_ISS", st, "e")
                            alignLeftRightText(textInLineFormatBundle, st, "")
                        }
                    }
                }


            } else {
                issDis = disclaimer
                alignLeftRightText(textInLineFormatBundle, issDis, "")
            }


            printer?.addText(textFormatBundle, "---------X-----------X----------")
            printer?.feedLine(4)


            // start print here and callback of printer:-
            printer?.startPrint(object : PrinterListener.Stub() {
                override fun onFinish() {
                    val msg = Message()
                    msg.data.putString("msg", "print finished")
                    //VFService.showToast("Printing Successfully")
                    when (copyType) {
                        EPrintCopyType.MERCHANT -> {
                            GlobalScope.launch(Dispatchers.Main) {
                                if (printerReceiptData.transactionType == TransactionType.EMI_SALE.type)
                                    (context as VFTransactionActivity).showMerchantAlertBox(
                                        this@PrintUtil,
                                        printerReceiptData,
                                        true
                                    ) { dialogCB ->
                                        printerCallback(dialogCB, 1)
                                    }
                            }

                        }
                        EPrintCopyType.CUSTOMER -> {
                            //VFService.showToast("Customer Transaction Slip Printed Successfully")
                            printerCallback(false, 1)
                        }
                        EPrintCopyType.DUPLICATE -> {
                            VFService.showToast("Success")
                            printerCallback(true, 1)
                        }
                    }
                }

                override fun onError(error: Int) {
                    if (error == 240) {
                        //VFService.showToast("Printing roll not available..")
                        printerCallback(false, 0)
                    } else {
                        //VFService.showToast("Printer Error------> $error")
                        printerCallback(false, 0)
                    }
                }
            })
            //====================
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        }
    }

    fun printPendingPreauth(
        cardProcessedDataModal: CardProcessedDataModal,
        context: Context?,
        pendingPreauthData: MutableList<PendingPreauthData>,
        printerCallback: (Boolean) -> Unit
    ) {
        try {
            val format = Bundle()
            val fmtAddTextInLine = Bundle()
            val tpt = TerminalParameterTable.selectFromSchemeTable()
            val headers = arrayListOf<String>()
            tpt?.receiptHeaderOne?.let { headers.add(it) }
            tpt?.receiptHeaderTwo?.let { headers.add(it) }
            tpt?.receiptHeaderThree?.let { headers.add(it) }
            setHeaderWithLogo(format, "amex_print.bmp", headers, context)

            val time = cardProcessedDataModal.getTime()
            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            var formattedTime = ""
            try {
                val t1 = timeFormat.parse(time)
                formattedTime = timeFormat2.format(t1)
                Log.e("Time", formattedTime)
            } catch (e: ParseException) {
                e.printStackTrace()
            }

            alignLeftRightText(
                fmtAddTextInLine,
                "DATE : ${dateFormater(cardProcessedDataModal.getTimeStamp()?.toLong() ?: 0L)}",
                "TIME : $formattedTime"
            )
            alignLeftRightText(
                fmtAddTextInLine,
                "MID : ${tpt?.merchantId}",
                "TID : ${tpt?.terminalId}"
            )
            printTransType(format, cardProcessedDataModal.getTransType())
            printSeperator(format)

            for (data in pendingPreauthData) {
                printPendingPreauthSingleRecord(fmtAddTextInLine, data)
                printSeperator(format)
            }
            printer?.addText(format, footerText[1])
            bHLogoWithAppVersion(format)

            printer?.startPrint(object : PrinterListener.Stub() {
                override fun onFinish() {
                    logger("PRINTING", "Printing Success in Pending PreAuth")
                    //VFService.showToast("Customer Transaction Slip Printed Successfully")
                    printerCallback(false)
                }

                override fun onError(error: Int) {
                    logger("PRINTING", "Printing Fail in Pending PreAuth")
                    if (error == 240)
                    //VFService.showToast("Printing roll not available..")
                    else
                    //VFService.showToast("Printer Error------> $error")
                        printerCallback(false)
                }
            })

        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            failureImpl(
                context as Activity,
                "Printer Service stopped.",
                "Please take chargeslip from the Report menu."
            )
        }
    }

    private fun getNameByTransactionType(transactionType: Int): String {
        var tTyp = ""
        for (e in TransactionType.values()) {
            if (e.type == transactionType) {
                tTyp = e.txnTitle
                break
            }
        }
        return tTyp
    }

    private fun printSeperator(format: Bundle) {
        try {
            // Seperator
            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )

            printer?.addText(format, "--------------------------------")
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }


    }

    private fun printPendingPreauthSingleRecord(
        format: Bundle,
        pendingPreauthData: PendingPreauthData
    ) {
        try {
            alignLeftRightText(
                format,
                "BATCH NO : ${invoiceWithPadding(pendingPreauthData.batch.toString())}",
                "ROC : ${invoiceWithPadding(pendingPreauthData.roc.toString())}"
            )

            alignLeftRightText(
                format,
                "PAN : ${pendingPreauthData.pan}",
                "AMT : ${"%.2f".format(pendingPreauthData.amount)}"
            )
            alignLeftRightText(
                format,
                "DATE : ${pendingPreauthData.date}",
                "TIME : ${pendingPreauthData.time}"
            )
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }

    }

    private fun bHLogoWithAppVersion(format: Bundle) {

        try {

            printLogo("BH.bmp")

            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.NORMAL_24_24
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, "App Version : ${BuildConfig.VERSION_NAME}")

            printer?.addText(format, "---------X-----------X----------")
            printer?.feedLine(4)
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }
    }

    private fun hasPin(printerReceiptData: BatchFileDataTable) {
        signatureMsg = if (printerReceiptData.isPinverified) {
            "SIGNATURE NOT REQUIRED"
        } else {
            "SIGN ..............................................."
        }
        pinVerifyMsg = if (printerReceiptData.isPinverified) {
            "PIN VERIFIED OK"
        } else {
            ""
        }
    }

    private fun printTransType(format: Bundle, transType: Int) {
        try {
            format.putInt(
                PrinterConfig.addText.FontSize.BundleName,
                PrinterConfig.addText.FontSize.LARGE_DH_32_64_IN_BOLD
            )
            format.putInt(
                PrinterConfig.addText.Alignment.BundleName,
                PrinterConfig.addText.Alignment.CENTER
            )
            printer?.addText(format, getNameByTransactionType(transType))
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }

    }

    private fun setLogoAndHeader() {
        try {
            val tpt = TerminalParameterTable.selectFromSchemeTable()
            val headers = arrayListOf<String>()
            tpt?.receiptHeaderOne?.let { headers.add(it) }
            tpt?.receiptHeaderTwo?.let { headers.add(it) }
            tpt?.receiptHeaderThree?.let { headers.add(it) }

            setHeaderWithLogo(textFormatBundle, AMEX_LOGO, headers)
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }
    }

    private fun printTransDatetime(printerReceiptData: BatchFileDataTable) {
        try {
            textInLineFormatBundle.putInt(
                PrinterConfig.addTextInLine.FontSize.BundleName,
                PrinterConfig.addTextInLine.FontSize.NORMAL_24_24
            )
            textInLineFormatBundle.putString(
                PrinterConfig.addTextInLine.GlobalFont.BundleName,
                PrinterFonts.path + PrinterFonts.FONT_AGENCYR
            )
            val time = printerReceiptData.time
            val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
            val timeFormat2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            var formattedTime = ""
            try {
                val t1 = timeFormat.parse(time)
                formattedTime = timeFormat2.format(t1 ?: Date())
                Log.e("Time", formattedTime)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            printer?.addTextInLine(
                textInLineFormatBundle, "DATE : ${printerReceiptData.transactionDate}",
                "", "TIME : $formattedTime", 0
            )
        } catch (ex: DeadObjectException) {
            throw ex
        } catch (ex: RemoteException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
        }
    }
}

fun checkForPrintReversalReceipt(context: Context?, callback: (String) -> Unit) {
    if (!TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY))) {
        val tpt = TerminalParameterTable.selectFromSchemeTable()
        tpt?.cancledTransactionReceiptPrint?.let { logger("CancelPrinting", it, "e") }
        if (tpt?.cancledTransactionReceiptPrint == "01") {
            PrintUtil(context).printReversal(context) {
                callback(it)
            }
        } else {
            callback("")
        }
    } else {
        callback("")
    }
}

internal data class SummeryModel(val type: String, var count: Int = 0, var total: Long = 0)
internal data class SummeryTotalType(var count: Int = 0, var total: Long = 0)
internal open class IPrintListener(
    var printerUtil: PrintUtil,
    var context: Context?,
    var copyType: EPrintCopyType,
    var batch: BatchFileDataTable,
    var isSuccess: (Boolean, Int) -> Unit
) : PrinterListener.Stub() {
    @Throws(RemoteException::class)
    override fun onError(error: Int) {
        if (error == 240)
        //VFService.showToast("Printing roll not available..")
            isSuccess(true, 0)
        else
        //VFService.showToast("Printer Error------> $error")
            isSuccess(false, 0)
    }

    @Throws(RemoteException::class)
    override fun onFinish() {
        val msg = Message()
        msg.data.putString("msg", "print finished")
        // VFService.showToast("Printing Successfully")
        when (copyType) {
            EPrintCopyType.MERCHANT -> {
                GlobalScope.launch(Dispatchers.Main) {
                    /*  var toastMsg = ""
                      toastMsg = when (batch.operationType) {
                          DetectCardType.MAG_CARD_TYPE.cardTypeName -> {
                              context?.getString(R.string.transaction_approved_successfully_Mag)
                                  .toString()
                          }
                          else -> {
                              context?.getString(R.string.transaction_approved_successfully)
                                  .toString()
                          }
                      }
                      val toast = Toast.makeText(
                          context,
                          toastMsg,
                          Toast.LENGTH_LONG
                      )
                      toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL, 0, 0)
                      toast.show()*/
                    /*   context?.let { txnSuccessToast(it) }
                       delay(4000)*/
                    if (batch.transactionType == TransactionType.TIP_SALE.type || batch.transactionType == TransactionType.VOID.type) {
                        (context as BaseActivity).showMerchantAlertBoxForTipSale(
                            printerUtil,
                            batch
                        ) { dialogCB ->
                            isSuccess(dialogCB, 1)
                        }
                    } else {
                        (context as VFTransactionActivity).showMerchantAlertBox(
                            printerUtil,
                            batch
                        ) { dialogCB ->
                            isSuccess(dialogCB, 1)
                        }
                    }
                }

            }
            EPrintCopyType.CUSTOMER -> {
                //VFService.showToast("Customer Transaction Slip Printed Successfully")
                isSuccess(false, 1)
            }
            EPrintCopyType.DUPLICATE -> {
                isSuccess(true, 1)
            }
        }
    }
}

internal open class IAuthCompletePrintListener(
    var printerUtil: PrintUtil,
    var context: Context?,
    var copyType: EPrintCopyType,
    var batch: BatchFileDataTable,
    var isSuccess: (Boolean) -> Unit
) : PrinterListener.Stub() {
    @Throws(RemoteException::class)
    override fun onError(error: Int) {

        if (error == 240) {

            VFService.showToast("Printing roll not available..")
            isSuccess(true)
        } else {
            VFService.showToast("Printer Error------> $error")
            isSuccess(false)
        }
    }

    @Throws(RemoteException::class)
    override fun onFinish() {
        val msg = Message()
        msg.data.putString("msg", "print finished")
        //VFService.showToast("Printing Successfully")
        when (copyType) {
            EPrintCopyType.MERCHANT -> {
                var toastMsg = ""
                when (batch.transactionType) {
                    TransactionType.PRE_AUTH_COMPLETE.type -> {
                        toastMsg = context?.getString(R.string.comp_preauth_success).toString()

                    }
                    TransactionType.VOID_PREAUTH.type -> {
                        toastMsg = context?.getString(R.string.void_preauth_success).toString()
                    }
                }
                GlobalScope.launch(Dispatchers.Main) {
                    /*  val toast = Toast.makeText(
                          context,
                          toastMsg,
                          Toast.LENGTH_LONG
                      )
                      toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL, 0, 0)
                      toast.show()
                      delay(4000)*/
                    (context as BaseActivity).showMerchantAlertBox1(
                        printerUtil,
                        batch
                    ) { dialogCB ->
                        isSuccess(dialogCB)
                    }
                }
            }
            EPrintCopyType.CUSTOMER -> {
                /*  context?.startActivity(Intent(context, MainActivity::class.java).apply {
                      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                  })*/
                isSuccess(false)
            }
            EPrintCopyType.DUPLICATE -> {
                isSuccess(true)
            }
        }

    }
}

fun initializeFontFiles() = initialize(VerifoneApp.appContext.assets)
internal open class ISettlementPrintListener(
    var context: Context?,
    var settlementByteArray: ByteArray
) : PrinterListener.Stub() {
    @Throws(RemoteException::class)
    override fun onError(error: Int) {
        Log.d("Failure:- ", "Settlement Print Failure Result.....")
    }

    @Throws(RemoteException::class)
    override fun onFinish() {
        Log.d("Success:- ", "Settlement Print Success Result.....")
        Handler(Looper.getMainLooper()).postDelayed({
            GlobalScope.launch {
                (context as MainActivity).settleBatch(settlementByteArray)
            }
        }, 400)

    }
}

enum class EPrintCopyType(val pName: String) {
    MERCHANT("**MERCHANT COPY**"), CUSTOMER("**CUSTOMER COPY**"), DUPLICATE("**DUPLICATE COPY**");
}

