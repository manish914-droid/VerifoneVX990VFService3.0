package com.example.verifonevx990app.emv.transactionprocess

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.example.verifonevx990app.R
import com.example.verifonevx990app.emv.VFEmv
import com.example.verifonevx990app.main.ConnectionError
import com.example.verifonevx990app.main.DetectCardType
import com.example.verifonevx990app.main.MainActivity
import com.example.verifonevx990app.main.PosEntryModeType
import com.example.verifonevx990app.nontransaction.CreateEMITransactionPacket
import com.example.verifonevx990app.nontransaction.EmiActivity
import com.example.verifonevx990app.offlinemanualsale.SyncOfflineSaleToHost
import com.example.verifonevx990app.realmtables.BatchFileDataTable
import com.example.verifonevx990app.realmtables.EmiBinTable
import com.example.verifonevx990app.realmtables.IssuerParameterTable
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.transactions.EmiCustomerDetails
import com.example.verifonevx990app.transactions.getMaskedPan
import com.example.verifonevx990app.utils.TransactionTypeValues
import com.example.verifonevx990app.utils.Utility
import com.example.verifonevx990app.utils.printerUtils.EPrintCopyType
import com.example.verifonevx990app.utils.printerUtils.PrintUtil
import com.example.verifonevx990app.utils.printerUtils.checkForPrintReversalReceipt
import com.example.verifonevx990app.vxUtils.*
import com.example.verifonevx990app.vxUtils.ROCProviderV2.refreshToolbarLogos
import com.google.gson.Gson
import com.vfi.smartpos.deviceservice.aidl.PinInputListener
import com.vfi.smartpos.deviceservice.constdefine.ConstIPBOC
import com.vfi.smartpos.deviceservice.constdefine.ConstIPinpad
import kotlinx.android.synthetic.main.activity_transaction.*
import kotlinx.android.synthetic.main.app_toolbar.*
import kotlinx.android.synthetic.main.enter_manual_detail_layout.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class VFTransactionActivity : BaseActivity() {
    companion object {
        val TAG = VFTransactionActivity::class.java.simpleName
    }
    private var userInactivity: Boolean = false
    private val pinHandler = Handler(Looper.getMainLooper())
    private var transactionalAmount: Long = 0L
    private var cashAmount: Long = 0L
    private val transactionAmountValue by lazy { intent.getStringExtra("amt") ?: "0" }
    private val mobileNumber by lazy { intent.getStringExtra("mobileNumber") ?: "" }
    private val billNumber by lazy { intent.getStringExtra("billNumber") ?: "0" }

    //used for other cash amount
    private val transactionCashAmountValue by lazy { intent.getStringExtra("cashAmt") ?: "0" }
    private val transactionProcessingCode by lazy {
        intent.getStringExtra("proc_code") ?: "92001"
    } //Just for checking purpose
    private val transactionType by lazy { intent.getIntExtra("type", -1947) }
    private val title by lazy { intent.getStringExtra("title") }

    //used in case of sale with cash
    private val saleAmt by lazy { intent.getStringExtra("saleAmt") ?: "0" }
    private var globalCardProcessedModel = CardProcessedDataModal()

    private var tpt: TerminalParameterTable? = null
    private var isManualEntryAllowed = false
    private val vfIEMV = VFService.vfIEMV
    private var emiCustomerDetails: EmiCustomerDetails? = null
    var hasInstaEmi = false
    private val cardView_l by lazy { findViewById<CardView>(R.id.cardView_l) }
    private val tv_card_number_heading by lazy { findViewById<BHTextView>(R.id.tv_card_number_heading) }
    private val tv_insert_card by lazy { findViewById<BHTextView>(R.id.tv_insert_card) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)
        main_toolbar_start.setBackgroundResource(R.drawable.ic_back_arrow)
        refreshToolbarLogos(this)
        main_toolbar_start.setOnClickListener {
            //  onBackPressed()
            declinedTransaction()
        }
        /*main_toolbar_tv.visibility = View.GONE
        main_toolbar_tv.text = title*/

        tpt = TerminalParameterTable.selectFromSchemeTable()
        isManualEntryAllowed = tpt?.fManEntry == "1"
        globalCardProcessedModel.setTransType(transactionType)
        initUI()

        if (!TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY))) {
            Log.d("Reversal:-", " Reversal Consist Data")
        } else {
            Log.d("Reversal:-", " Reversal Consist No Data")
        }
        //    doProcessCard()
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            doProcessCard()
        }, 100)

    }

    //Below method is used to call Process Card Class and callback the card related responses data:-
    fun doProcessCard() {
        try {
            val printer = VFService.vfPrinter
            logger("STATUS_P", printer?.status.toString(), "e")
            if (printer?.status != 0) {
                GlobalScope.launch(Dispatchers.Main) {
                    alertBoxWithAction(null, null, getString(R.string.printer_error), "Want to Proceed SALE with no charge slip", true, getString(R.string.yes), { alertPositiveCallback ->
                            if (alertPositiveCallback)
                                ProcessCard(this@VFTransactionActivity, pinHandler, globalCardProcessedModel, transactionalAmount) { localCardProcessedData ->
                                    localCardProcessedData.setProcessingCode(transactionProcessingCode)
                                    localCardProcessedData.setTransactionAmount(transactionalAmount)
                                    localCardProcessedData.setCashAmount(cashAmount)
                                    localCardProcessedData.setMobileBillExtraData(
                                        Pair(mobileNumber, billNumber)
                                    )
                                    //    localCardProcessedData.setTransType(transactionType)
                                    globalCardProcessedModel = localCardProcessedData
                                    Log.d("CardProcessedData:- ", Gson().toJson(localCardProcessedData))
                                    val maskedPan = localCardProcessedData.getPanNumberData()?.let {
                                        getMaskedPan(TerminalParameterTable.selectFromSchemeTable(), it)
                                    }
                                    runOnUiThread {
                                        at_card_no_tv.text = maskedPan
                                        cardView_l.visibility = View.VISIBLE
                                        tv_card_number_heading.visibility = View.VISIBLE
                                        tv_insert_card.visibility = View.INVISIBLE
                                        payment_gif.visibility=View.INVISIBLE
                                    }
                                    //Below Different Type of Transaction check Based ISO Packet Generation happening:-
                                    processAccordingToCardType(localCardProcessedData)
                                }


                        },
                        { cancelButtonCallback ->
                            if (cancelButtonCallback) {
                                finish()
                            }

                        })
                }
            } else {
                ProcessCard(this, pinHandler, globalCardProcessedModel, transactionalAmount) { localCardProcessedData ->
                    localCardProcessedData.setProcessingCode(transactionProcessingCode)
                    localCardProcessedData.setTransactionAmount(transactionalAmount)
                    localCardProcessedData.setCashAmount(cashAmount)
                    localCardProcessedData.setMobileBillExtraData(Pair(mobileNumber, billNumber))
                    //    localCardProcessedData.setTransType(transactionType)
                    globalCardProcessedModel = localCardProcessedData
                    Log.d("CardProcessedData:- ", Gson().toJson(localCardProcessedData))
                    val maskedPan = localCardProcessedData.getPanNumberData()?.let {
                        getMaskedPan(TerminalParameterTable.selectFromSchemeTable(), it)
                    }
                    runOnUiThread {
                        at_card_no_tv.text = maskedPan
                        cardView_l.visibility = View.VISIBLE
                        tv_card_number_heading.visibility = View.VISIBLE
                        tv_insert_card.visibility = View.INVISIBLE
                        payment_gif.visibility=View.INVISIBLE
                    }
                    //Below Different Type of Transaction check Based ISO Packet Generation happening:-
                    processAccordingToCardType(localCardProcessedData)
                }

            }
        }
        catch (ex: DeadObjectException){
            println("Process card error1"+ex.message)
            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                GlobalScope.launch {
                    VFService.connectToVFService(VerifoneApp.appContext)
                    delay(200)
                    doProcessCard()
                }
            }, 200)
        }
        catch (e: RemoteException) {
            e.printStackTrace()
            println("Process card error2"+e.message)
            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                GlobalScope.launch {
                    VFService.connectToVFService(VerifoneApp.appContext)
                    delay(200)
                    doProcessCard()
                }
            }, 200)
        }
        catch (ex: Exception) {
            println("Process card error3"+ex.message)
            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                GlobalScope.launch {
                    VFService.connectToVFService(VerifoneApp.appContext)
                    delay(200)
                    doProcessCard()
                }
            }, 200)

        }
    }

    private fun processAccordingToCardType(cardProcessedData: CardProcessedDataModal) {
        when (cardProcessedData.getReadCardType()) {
            DetectCardType.MAG_CARD_TYPE -> {
                if (cardProcessedData.getTransType() == TransactionType.SALE.type || cardProcessedData.getTransType() == TransactionType.PRE_AUTH.type || cardProcessedData.getTransType() == TransactionType.REFUND.type) {
                    emvProcessNext(cardProcessedData)
                } else {
                    /* val transactionEMIISO = CreateEMITransactionPacket(cardProcessedData, emiCustomerDetails).createTransactionPacket()
                     logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                     runOnUiThread { showProgress(getString(R.string.emi_data_sync)) }
                     GlobalScope.launch(Dispatchers.IO) {
                         checkReversal(transactionEMIISO, cardProcessedData)
                      }*/
                }
            }

            DetectCardType.EMV_CARD_TYPE -> {
                //  when(cardProcessedData.getTransType()==)

                if (cardProcessedData.getTransType() == TransactionType.SALE.type || cardProcessedData.getTransType() == TransactionType.PRE_AUTH.type || cardProcessedData.getTransType() == TransactionType.REFUND.type) {
                    emvProcessNext(cardProcessedData)
                } else {
                    val transactionEMIISO = CreateEMITransactionPacket(
                        cardProcessedData,
                        emiCustomerDetails
                    ).createTransactionPacket()
                    logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                  //  runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                    GlobalScope.launch(Dispatchers.IO) {
                        checkReversal(transactionEMIISO, cardProcessedData)
                    }

                }


            }
            DetectCardType.CONTACT_LESS_CARD_TYPE -> {

                if (cardProcessedData.getTransType() == TransactionType.SALE.type || cardProcessedData.getTransType() == TransactionType.PRE_AUTH.type || cardProcessedData.getTransType() == TransactionType.REFUND.type) {
                    emvProcessNext(cardProcessedData)
                } else {
                    val transactionEMIISO = CreateEMITransactionPacket(
                        cardProcessedData,
                        emiCustomerDetails
                    ).createTransactionPacket()
                    logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                   // runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                    GlobalScope.launch(Dispatchers.IO) {
                        checkReversal(transactionEMIISO, cardProcessedData)
                    }

                }

            }

            DetectCardType.MANUAL_ENTRY_TYPE -> {
                val transactionISO =
                    CreateTransactionPacket(cardProcessedData).createTransactionPacket()
                logger("Transaction REQUEST PACKET --->>", transactionISO.isoMap, "e")
                //runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                GlobalScope.launch(Dispatchers.IO) {
                    checkReversal(transactionISO, cardProcessedData)
                }
            }

            DetectCardType.CONTACT_LESS_CARD_WITH_MAG_TYPE -> {

                if (cardProcessedData.getTransType() == TransactionType.SALE.type || cardProcessedData.getTransType() == TransactionType.PRE_AUTH.type || cardProcessedData.getTransType() == TransactionType.REFUND.type) {
                    emvProcessNext(cardProcessedData)
                } else {
                    val transactionEMIISO = CreateEMITransactionPacket(
                        cardProcessedData,
                        emiCustomerDetails
                    ).createTransactionPacket()
                    logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                 //   runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                    GlobalScope.launch(Dispatchers.IO) {
                        checkReversal(transactionEMIISO, cardProcessedData)
                    }
                }
            }

            DetectCardType.CARD_ERROR_TYPE -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                //logger("TimeOut", "e")
            }

            else -> {
                showToast("Card Not Detected Correctly")
            }
        }
    }

    override fun onEvents(event: VxEvent) {}

    @SuppressLint("ClickableViewAccessibility")
    private fun initUI() {
        payment_gif.loadUrl("file:///android_asset/card_animation.html")
        payment_gif.setOnTouchListener { _, event -> event.action == MotionEvent.ACTION_MOVE }
        val amountValue = "${getString(R.string.rupees_symbol)} $transactionAmountValue"
        findViewById<BHTextView>(R.id.base_amt_tv).text = amountValue

        transactionalAmount = transactionAmountValue.replace(".", "").toLong()
        cashAmount = transactionCashAmountValue.replace(".", "").toLong()

        if (isManualEntryAllowed) manual_entry_button.visibility =
            View.VISIBLE else manual_entry_button.visibility = View.GONE

        manual_entry_button.setOnClickListener {
            //have to check
            try {
                vfIEMV?.stopCheckCard()
               }
            catch (ex: DeadObjectException){
                ex.printStackTrace()
            }
            catch (ex: RemoteException){
                ex.printStackTrace()
            }
            catch (ex: Exception){
                ex.printStackTrace()
            }
         //   vfIEMV?.stopCheckCard()
            showEnterManualDetailDialog()
        }
    }

    private fun showEnterManualDetailDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.enter_manual_detail_layout)
        val cardExpDate = dialog.findViewById<BHTextInputEditText>(R.id.card_exp_date)
        cardExpDate.isFocusable = false
        cardExpDate.setOnClickListener { openDatePicker(cardExpDate, this) }

        dialog.cancel_btnn.setOnClickListener {
            dialog.dismiss()
            doProcessCard()
        }
        dialog.ok_btnn.setOnClickListener {
            //showToast("Proceed..")
            if (dialog.card_no.text.isNullOrBlank() || dialog.card_no.text!!.length < 15) {
                showToast("Invalid CardNumber")
            } else if (!cardLuhnCheck(dialog.card_no.text.toString())) {
                VFService.showToast(getString(R.string.card_number_not_valid_as_per_luhn_check))
            } else if (dialog.card_exp_date.text.isNullOrBlank()) {
                showToast("Invalid Exp Date")
            } else {
                dialog.dismiss()
                createDataForManualEntry(
                    dialog.card_no.text.toString(),
                    cardExpDate?.text.toString().substring(0, 2) + cardExpDate?.text.toString()
                        .substring(3, 5)
                )
            }
        }
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()

    }

    private fun createDataForManualEntry(track2Data: String, expdate: String) {
        globalCardProcessedModel.setPanNumberData(track2Data)
        globalCardProcessedModel.setPosEntryMode(PosEntryModeType.POS_ENTRY_MANUAL_NO4DBC.posEntry.toString())
        globalCardProcessedModel.setProcessingCode(transactionProcessingCode)
        globalCardProcessedModel.setTransactionAmount(transactionalAmount)
        //In case of swipe no need to send ApplicationPanSequence set "00" here
        globalCardProcessedModel.setApplicationPanSequenceValue("00")
        globalCardProcessedModel.setCashAmount(cashAmount)
        globalCardProcessedModel.setTrack2Data(
            getEncryptedField57DataForManualSale(
                track2Data,
                expdate
            )
        )
        globalCardProcessedModel.setReadCardType(DetectCardType.MANUAL_ENTRY_TYPE)

        processAccordingToCardType(globalCardProcessedModel)

    }

    //Below method is used to Sync Transaction Data To Server:-
    private fun checkReversal(transactionISOByteArray: IsoDataWriter, cardProcessedDataModal: CardProcessedDataModal) {
        if (TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY))) {
            val msg: String = getString(R.string.sale_data_sync)
           /* when (cardProcessedDataModal.getTransType()) {
                TransactionType.PRE_AUTH.type -> getString(R.string.pre_auth_data_syn)
                TransactionType.SALE.type -> getString(R.string.sale_data_sync)
                TransactionType.REFUND.type -> getString(R.string.refund_data_sync)
                else -> getString(R.string.data_sync)
            }*/
            runOnUiThread { showProgress(msg) }
            SyncTransactionToHost(
                transactionISOByteArray,
                cardProcessedDataModal
            ) { syncStatus, responseCode, transactionMsg, printExtraData ->
                hideProgress()
                if(cardProcessedDataModal.getReadCardType()== DetectCardType.EMV_CARD_TYPE)
                logger("CHECKCALL","CALLED","e")
                if (syncStatus) {

                    val responseIsoData: IsoDataReader = readIso(transactionMsg.toString(), false)
                    val autoSettlementCheck = responseIsoData.isoMap[60]?.parseRaw2String().toString()
                    if (syncStatus && responseCode == "00" && !AppPreference.getBoolean(
                            AppPreference.ONLINE_EMV_DECLINED)) {
                        //Below we are saving batch data and print the receipt of transaction:-
                        GlobalScope.launch (Dispatchers.Main){
                            if(cardProcessedDataModal.getReadCardType()== DetectCardType.EMV_CARD_TYPE)
                            txnSuccessToast(this@VFTransactionActivity,getString(R.string.transaction_approved_successfully))
                            else
                                txnSuccessToast(this@VFTransactionActivity)
                           // delay(4000)
                        }
                        StubBatchData(
                            cardProcessedDataModal.getTransType(),
                            cardProcessedDataModal,
                            printExtraData
                        ) { stubbedData ->
                            if (cardProcessedDataModal.getTransType() == TransactionType.EMI_SALE.type) {
                                stubEMI(stubbedData, emiCustomerDetails) { data ->
                                    printSaveSaleEmiDataInBatch(data) { printCB ->
                                        if (!printCB) {
                                            //Here we are Syncing Offline Sale if we have any in Batch Table and also Check Sale Response has Auto Settlement enabled or not:-
                                            //If Auto Settlement Enabled Show Pop Up and User has choice whether he/she wants to settle or not:-
                                            if (!TextUtils.isEmpty(autoSettlementCheck))
                                                GlobalScope.launch(Dispatchers.Main) {
                                                    syncOfflineSaleAndAskAutoSettlement(autoSettlementCheck.substring(0, 1))
                                                }
                                        }
                                    }
                                }
                            } else {
                                printAndSaveBatchDataInDB(stubbedData) { printCB ->
                                    if (!printCB) {
                                        //Here we are Syncing Offline Sale if we have any in Batch Table and also Check Sale Response has Auto Settlement enabled or not:-
                                        //If Auto Settlement Enabled Show Pop Up and User has choice whether he/she wants to settle or not:-
                                        if (!TextUtils.isEmpty(autoSettlementCheck)) {
                                            GlobalScope.launch(Dispatchers.Main) {
                                                syncOfflineSaleAndAskAutoSettlement(
                                                    autoSettlementCheck.substring(0, 1)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (syncStatus && responseCode != "00") {
                        GlobalScope.launch(Dispatchers.Main) {
                            alertBoxWithAction(null,
                                null,
                                getString(R.string.transaction_delined_msg),
                                responseIsoData.isoMap[58]?.parseRaw2String().toString(),
                                false,
                                getString(R.string.positive_button_ok),
                                { alertPositiveCallback ->
                                    if (alertPositiveCallback) {
                                        if (!TextUtils.isEmpty(autoSettlementCheck)) {
                                            syncOfflineSaleAndAskAutoSettlement(
                                                autoSettlementCheck.substring(
                                                    0,
                                                    1
                                                )
                                            )
                                        } else {
                                            startActivity(
                                                Intent(
                                                    this@VFTransactionActivity,
                                                    MainActivity::class.java
                                                ).apply {
                                                    flags =
                                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                })
                                        }
                                    }
                                },
                                {})
                        }
                    }
                    //Condition for having a reversal(EMV CASE)
                    else if (!TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY))) {
                        checkForPrintReversalReceipt(this@VFTransactionActivity) {
                            GlobalScope.launch(Dispatchers.Main) {
                                alertBoxWithAction(null,
                                    null,
                                    getString(R.string.declined),
                                    getString(R.string.emv_declined),
                                    false,
                                    getString(R.string.positive_button_ok),
                                    { alertPositiveCallback ->
                                        if (alertPositiveCallback)
                                            syncOfflineSaleAndAskAutoSettlement(
                                                autoSettlementCheck.substring(
                                                    0,
                                                    1
                                                )
                                            )
                                    },
                                    {})
                            }
                        }
                    }
                } else {
                    runOnUiThread { hideProgress() }
                    //below condition is for print reversal receipt if reversal is generated
                    // and also check is need to printed or not(backend enable disable)
                    checkForPrintReversalReceipt(this) {
                        logger("ReversalReceipt", it, "e")
                    }

                    if (ConnectionError.NetworkError.errorCode.toString() == responseCode) {
                        GlobalScope.launch(Dispatchers.Main) {
                            alertBoxWithAction(null,
                                null,
                                getString(R.string.network),
                                getString(R.string.network_error),
                                false,
                                getString(R.string.positive_button_ok),
                                { alertPositiveCallback ->
                                    if (alertPositiveCallback)
                                        declinedTransaction()
                                },
                                {})
                        }
                    }
                    if (ConnectionError.ConnectionTimeout.errorCode.toString() == responseCode) {
                        GlobalScope.launch(Dispatchers.Main) {
                            alertBoxWithAction(null,
                                null,
                                getString(R.string.error_hint),
                                getString(R.string.connection_error),
                                false,
                                getString(R.string.positive_button_ok),
                                { alertPositiveCallback ->
                                    if (alertPositiveCallback)
                                        declinedTransaction()
                                },
                                {})
                        }
                    } else {
                        GlobalScope.launch(Dispatchers.Main) {
                            alertBoxWithAction(null,
                                null,
                                getString(R.string.declined),
                                getString(R.string.transaction_delined_msg),
                                false,
                                getString(R.string.positive_button_ok),
                                { alertPositiveCallback ->
                                    if (alertPositiveCallback)
                                        declinedTransaction()
                                },
                                {})


                        }
                    }
                }
            }
        }
        //Below else case is to Sync Reversal data Packet to Host:-
        else {
            if (!TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY))) {
                runOnUiThread { showProgress(getString(R.string.reversal_data_sync)) }
                SyncReversalToHost(AppPreference.getReversal()) { isSyncToHost, transMsg ->
                    hideProgress()
                    if (isSyncToHost) {
                        AppPreference.clearReversal()
                        checkReversal(transactionISOByteArray, cardProcessedDataModal)
                    } else {
                        GlobalScope.launch(Dispatchers.Main) {
                            //  VFService.showToast(transMsg)
                            alertBoxWithAction(null,
                                null,
                                getString(R.string.reversal_upload_fail),
                                getString(R.string.transaction_delined_msg),
                                false,
                                getString(R.string.positive_button_ok),
                                { alertPositiveCallback ->
                                    if (alertPositiveCallback)
                                        declinedTransaction()
                                },
                                {})


                        }
                    }
                }
            }
        }
    }

    fun declinedTransactions() {
        try {
          //  vfIEMV?.stopCheckCard()
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } catch (ex: java.lang.Exception) {
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    //Below method is used to handle Transaction Declined case:-
    fun declinedTransaction() {
        try {
           vfIEMV?.stopCheckCard()
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } catch (ex: java.lang.Exception) {
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    //Below method is used to save sale data in batch file data table and print the receipt of it:-
    private fun printAndSaveBatchDataInDB(stubbedData: BatchFileDataTable, cb: (Boolean) -> Unit) {
        // printerReceiptData will not be saved in Batch if transaction is pre auth
        if (transactionType != TransactionTypeValues.PRE_AUTH) {
            //Here we are saving printerReceiptData in BatchFileData Table:-
            saveTableInDB(stubbedData)
        }
        PrintUtil(this).startPrinting(
            stubbedData,
            EPrintCopyType.MERCHANT,
            this
        ) { dialogCB, printingFail ->
            Log.d("Sale Printer Status:- ", printingFail.toString())
            if (printingFail == 0)
                GlobalScope.launch(Dispatchers.Main) {
                    alertBoxWithAction(null,
                        null,
                        getString(R.string.printer_error),
                        getString(R.string.printing_roll_empty_msg),
                        false,
                        getString(R.string.positive_button_ok),
                        {
                            startActivity(
                                Intent(
                                    this@VFTransactionActivity,
                                    MainActivity::class.java
                                ).apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                        },
                        {})
                }
            else
                cb(dialogCB)
        }
    }

    //Below method is used to save sale Emi data in batch file data table and print the receipt of it:-
    private fun printSaveSaleEmiDataInBatch(
        stubbedData: BatchFileDataTable,
        emiCB: (Boolean) -> Unit
    ) {
        // printerReceiptData will not be saved in Batch if transaction is pre auth
        if (transactionType != TransactionTypeValues.PRE_AUTH) {
            //Here we are saving printerReceiptData in BatchFileData Table:-
            saveTableInDB(stubbedData)
        }
        PrintUtil(this).printEMISale(
            stubbedData,
            EPrintCopyType.MERCHANT,
            this
        ) { dialogCB, printingFail ->
            Log.d("Sale Printer Status:- ", printingFail.toString())
            if (printingFail == 0)
                GlobalScope.launch(Dispatchers.Main) {
                    alertBoxWithAction(null,
                        null,
                        getString(R.string.printer_error),
                        getString(R.string.printing_roll_empty_msg),
                        false,
                        getString(R.string.positive_button_ok),
                        {
                            startActivity(
                                Intent(
                                    this@VFTransactionActivity,
                                    MainActivity::class.java
                                ).apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                        },
                        {})
                }
            else
                emiCB(dialogCB)
        }
        /* val intent = Intent(this, PrinterActivity::class.java).apply {
             putExtra("printingType", EPrinting.START_EMI_SALE_PRINTING)
             putExtra("printingData", stubbedData)
         }
         startActivityForResult(intent, EIntentRequest.PRINTINGRECEIPT.code)*/
        //  }
    }

    //Method to show Merchant Copy Alert Box used for printing process:-
    fun showMerchantAlertBox(
        printerUtil: PrintUtil,
        batchData: BatchFileDataTable,
        isEMI: Boolean = false,
        dialogCB: (Boolean) -> Unit
    ) {
        alertBoxWithAction(
            printerUtil, batchData, getString(R.string.print_customer_copy),
            getString(R.string.do_you_want_to_print_customer_copy),
            true, getString(R.string.positive_button_yes), { status ->
                if (status) {
                    if (isEMI) {
                        printerUtil.printEMISale(
                            batchData,
                            EPrintCopyType.CUSTOMER,
                            this
                        ) { customerCopyPrintSuccess, printingFail ->
                            if (!customerCopyPrintSuccess) {
                                //  VFService.showToast(getString(R.string.customer_copy_print_success))
                                dialogCB(false)
                            }
                        }
                    } else {
                        printerUtil.startPrinting(
                            batchData,
                            EPrintCopyType.CUSTOMER,
                            this
                        ) { customerCopyPrintSuccess, printingFail ->
                            if (!customerCopyPrintSuccess) {
                                // VFService.showToast(getString(R.string.customer_copy_print_success))
                                dialogCB(false)
                            }
                        }
                    }
                }
            }, {
                dialogCB(false)
            })
    }

    //Below function is used to deal with EMV Card Fallback when we insert EMV Card from other side then chip side:-
    fun handleEMVFallbackFromError(title: String, msg: String, showCancelButton: Boolean, emvFromError: (Boolean) -> Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            alertBoxWithAction(
                null, null, title,
                msg, showCancelButton, getString(R.string.positive_button_ok), { alertCallback ->
                    if (alertCallback) {
                        emvFromError(true)
                    }
                }, {})
        }
    }

    override fun onResume() {
        super.onResume()
        userInactivity = true
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            GlobalScope.launch {
                VFService.connectToVFService(VerifoneApp.appContext)
            }
        }, 200)

    }

    override fun onBackPressed() {
        if (!userInactivity) {
            super.onBackPressed()
        }
        try {
            //      VFService.vfIEMV?.stopCheckCard()
        } catch (ex: Exception) {
            ex.printStackTrace()
            logger(
                "VFTransactionActivity",
                "---OnBackPressed----> Stopping Card Check Exception",
                "e"
            )
        }
    }

    fun checkEmiBankEmi(cardProcessedDataModal: CardProcessedDataModal, transactionCallback: (CardProcessedDataModal) -> Unit) {

        cardProcessedDataModal.setProcessingCode(transactionProcessingCode)
        cardProcessedDataModal.setTransactionAmount(transactionalAmount)
        cardProcessedDataModal.setCashAmount(cashAmount)
        cardProcessedDataModal.setTransType(transactionType)
        cardProcessedDataModal.setMobileBillExtraData(Pair(mobileNumber, billNumber))
        globalCardProcessedModel = cardProcessedDataModal
        Log.d("CardProcessedData:- ", Gson().toJson(cardProcessedDataModal))
        val maskedPan = cardProcessedDataModal.getPanNumberData()?.let {
            getMaskedPan(TerminalParameterTable.selectFromSchemeTable(), it)
        }
        runOnUiThread {
            at_card_no_tv.text = maskedPan
            cardView_l.visibility = View.VISIBLE
            tv_card_number_heading.visibility = View.VISIBLE
            tv_insert_card.visibility = View.INVISIBLE
            payment_gif.visibility=View.INVISIBLE
        }

        var iptList = IssuerParameterTable.selectFromIssuerParameterTable()
        iptList = iptList.filter { it.isActive == "1" }


        val temPan = cardProcessedDataModal.getPanNumberData()?.substring(0, 6)
        var emiBin: EmiBinTable?
        //  val temPan = track1.pan.substring(0, 6)
        try {
            emiBin = EmiBinTable.selectFromEmiBinTable().first { it.binValue == temPan }
        } catch (ex: Exception) {
            ex.printStackTrace()
            emiBin = null
            //println("No such element exception " + ex.message)
        }
        try {
            iptList = iptList.filter { it.issuerId == emiBin?.issuerId }
        } catch (ex: Exception) {
            ex.printStackTrace()
            iptList = emptyList()
            //println("No itp list " + ex.message)
        }

        if (cardProcessedDataModal.getTransType() == TransactionType.EMI_SALE.type) {
            if (null == emiBin || iptList.isEmpty()) {
                GlobalScope.launch(Dispatchers.Main) {
                    val iDialog = this@VFTransactionActivity as IDialog
                    iDialog.getMsgDialog("Bank EMI",
                        "Bank EMI is not available for this card.\nDo you want to continue as Sale?",
                        "Yes",
                        "No",
                        {
                            cardProcessedDataModal.setTransType(TransactionType.SALE.type)
                            transactionCallback(cardProcessedDataModal)
                           // cardProcessedDataModal.setEmiType(0)  //0 for Bank EMI
                           // emvProcessNext(cardProcessedDataModal)
                        },
                        {
                            declinedTransaction()
                        })
                }
            } else {
                doEmiEnquiry(cardProcessedDataModal)
            }
        }

    /*    startActivityForResult(Intent(this, EmiActivity::class.java).apply {
            putExtra("amount", transactionAmountValue.toDouble())
            putExtra("is_bank", true)
            putExtra("pan", cardProcessedDataModal.getPanNumberData())
            putExtra("cardprocess", cardProcessedDataModal)

        }, EIntentRequest.TRANSACTION.code)*/

    }

    fun checkEmiInstaEmi(cardProcessedDataModal: CardProcessedDataModal, transactionCallback: (CardProcessedDataModal) -> Unit) {

        cardProcessedDataModal.setProcessingCode(transactionProcessingCode)
        cardProcessedDataModal.setTransactionAmount(transactionalAmount)
        cardProcessedDataModal.setCashAmount(cashAmount)
        cardProcessedDataModal.setTransType(transactionType)
        cardProcessedDataModal.setMobileBillExtraData(Pair(mobileNumber, billNumber))
        globalCardProcessedModel = cardProcessedDataModal
        Log.d("CardProcessedData:- ", Gson().toJson(cardProcessedDataModal))
        val maskedPan = cardProcessedDataModal.getPanNumberData()?.let {
            getMaskedPan(TerminalParameterTable.selectFromSchemeTable(), it)
        }
        runOnUiThread {
            at_card_no_tv.text = maskedPan
            cardView_l.visibility = View.VISIBLE
            tv_card_number_heading.visibility = View.VISIBLE
            tv_insert_card.visibility = View.INVISIBLE
            payment_gif.visibility=View.INVISIBLE
        }
        //Below Different Type of Transaction check Based ISO Packet Generation happening:-
        // processAccordingToCardType(cardProcessedDataModal)

        var iptList = IssuerParameterTable.selectFromIssuerParameterTable()
        iptList = iptList.filter { it.isActive == "1" }


        val temPan = cardProcessedDataModal.getPanNumberData()?.substring(0, 6)
        var emiBin: EmiBinTable?
        //  val temPan = track1.pan.substring(0, 6)
        try {
            emiBin = EmiBinTable.selectFromEmiBinTable().first { it.binValue == temPan }
        }
        catch (ex: NoSuchElementException) {
            ex.printStackTrace()
            emiBin = null
            //println("No such element exception " + ex.message)
        }
        catch (ex: Exception) {
            ex.printStackTrace()
            emiBin = null
            //println("No such element exception " + ex.message)
        }

        try {
            iptList = iptList.filter { it.issuerId == emiBin?.issuerId }
        } catch (ex: Exception) {
            ex.printStackTrace()
            iptList = emptyList()
            //println("No itp list " + ex.message)
        }

        if (cardProcessedDataModal.getTransType() == TransactionType.SALE.type) {

            var limitAmt = 0f
            if (tpt?.surChargeValue?.isNotEmpty()!!) {
                limitAmt = try {
                    tpt?.surChargeValue?.toFloat()!! / 100
                } catch (ex: Exception) {
                    0f
                }
            }


            if (tpt?.surcharge?.isNotEmpty()!!) {
                hasInstaEmi = try {
                    tpt?.surcharge.equals("1")
                } catch (ex: Exception) {
                    0f
                } as Boolean
            }

            if (hasInstaEmi && transactionAmountValue.toFloat() >= limitAmt && null != emiBin && iptList.isNotEmpty()) {
                GlobalScope.launch(Dispatchers.Main) {
                    showEMISaleDialog(cardProcessedDataModal) {
                        transactionCallback(it)
                    }
                }
            } else {
                cardProcessedDataModal.setTransType(TransactionType.SALE.type)
                transactionCallback(cardProcessedDataModal)
                //  emvProcessNext(cardProcessedDataModal)
            }

        } ////////////////Bank_EMI == EMI_SALE
        else if (cardProcessedDataModal.getTransType() == TransactionType.EMI_SALE.type) {
            if (null == emiBin || iptList.isEmpty()) {
                GlobalScope.launch(Dispatchers.Main) {
                    val iDialog = this@VFTransactionActivity as IDialog
                    iDialog.getMsgDialog("Bank EMI",
                        "Bank EMI is not available for this card.\nDo you want to continue as Sale?",
                        "Yes",
                        "No",
                        {
                            cardProcessedDataModal.setTransType(TransactionType.SALE.type)
                            transactionCallback(cardProcessedDataModal)
                          //  cardProcessedDataModal.setEmiType(0)  //0 for Bank EMI
                          //  emvProcessNext(cardProcessedDataModal)
                        },
                        {
                            declinedTransaction()
                        })
                }
            } else {
                doEmiEnquiry(cardProcessedDataModal)
            }
        }
    }

    fun doEmiEnquiry(cardProcessedDataModal: CardProcessedDataModal) {
        startActivityForResult(Intent(this, EmiActivity::class.java).apply {
            putExtra("amount", transactionAmountValue.toDouble())
            putExtra("is_bank", true)
            putExtra("pan", cardProcessedDataModal.getPanNumberData())
            putExtra("cardprocess", cardProcessedDataModal)

        }, EIntentRequest.TRANSACTION.code)
    }

    //Below method is used to show Alert Dialog with EMI and SALE Option also there is cross sign to close dialog and navigate back:-
    //Below method is used to show confirmation pop up for Void Offline Sale:-
    private fun showEMISaleDialog(cardProcessedDataModal: CardProcessedDataModal, transactionCallback: (CardProcessedDataModal) -> Unit) {
        val dialog = Dialog(this)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.show_emi_sale_dialog_view)

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        dialog.findViewById<CardView>(R.id.cardsaleButton).setOnClickListener {
            dialog.dismiss()
            cardProcessedDataModal.setTransType(TransactionType.SALE.type)
            transactionCallback(cardProcessedDataModal)

        }

        dialog.findViewById<CardView>(R.id.cardemiButton).setOnClickListener {
            dialog.dismiss()
            cardProcessedDataModal.setTransType(TransactionType.EMI_SALE.type)
            cardProcessedDataModal.setEmiType(1)  //1 for insta emi
            transactionCallback(cardProcessedDataModal)
            //EMI Callback
            doEmiEnquiry(cardProcessedDataModal)
        }

        dialog.findViewById<ImageView>(R.id.closeDialog).setOnClickListener {
            dialog.dismiss()
            finish()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        try {
            if(null !=dialog && !dialog.isShowing && !(this as Activity).isFinishing) {
                dialog.show()
            }

        }
        catch (ex: WindowManager.BadTokenException) {
            ex.printStackTrace()
        }
        catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            EIntentRequest.TRANSACTION.code -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    emiCustomerDetails = data.getParcelableExtra<EmiCustomerDetails>("emi")
                    var cardProcessedData: CardProcessedDataModal =
                        data.getSerializableExtra("cardprocess") as CardProcessedDataModal
                    when (globalCardProcessedModel.getReadCardType()) {
                        DetectCardType.MAG_CARD_TYPE -> {
                            // for Mag
                            if (globalCardProcessedModel.getIsOnline() == 1) {
                                initializePinInputListener(globalCardProcessedModel,emiCustomerDetails)
                                val param = Bundle()
                                val globleparam = Bundle()
                                val panBlock: String? = globalCardProcessedModel.getPanNumberData()
                                val pinLimit = byteArrayOf(4, 5, 6) // byteArrayOf(Utility.HEX2DEC(retryTimes))
                                param.putByteArray(ConstIPinpad.startPinInput.param.KEY_pinLimit_ByteArray, pinLimit)
                                param.putInt(ConstIPinpad.startPinInput.param.KEY_timeout_int, 20)
                                when (globalCardProcessedModel.getIsOnline()) {
                                    1 -> param.putBoolean(ConstIPinpad.startPinInput.param.KEY_isOnline_boolean, true)
                                    2 -> param.putBoolean(ConstIPinpad.startPinInput.param.KEY_isOnline_boolean, false)
                                }

                                param.putString(ConstIPinpad.startPinInput.param.KEY_pan_String, panBlock)
                                param.putString(ConstIPinpad.startPinInput.param.KEY_promptString_String, "Enter PIN")
                                param.putInt(
                                    ConstIPinpad.startPinInput.param.KEY_desType_int,
                                    ConstIPinpad.startPinInput.param.Value_desType_3DES
                                )
                                try {
                                    VFService.vfPinPad?.startPinInput(
                                        VFEmv.workKeyId, param, globleparam,
                                        VFService.pinInputListener
                                    )
                                } catch (e: RemoteException) {
                                    e.printStackTrace()
                                }



                          /*      if (null != pinHandler) {
                                    pinHandler.postDelayed(Runnable {
                                        var dialog = PinpadPopUpWindow(
                                            this@VFTransactionActivity,
                                            R.style.popup_dialog,
                                            pinHandler,
                                            globalCardProcessedModel,
                                            this@VFTransactionActivity.getString(R.string.bank_card_password),
                                            ""
                                        )
                                        val pwdListener: PinpadPopUpWindow.OnPwdListener = object :
                                            PinpadPopUpWindow.OnPwdListener {
                                            override fun onErr() {
                                                dialog.dismiss()
                                                if (null != pinHandler)
                                                    pinHandler.removeCallbacksAndMessages(null)
                                                GlobalScope.launch(Dispatchers.Main) {
                                                    this@VFTransactionActivity.declinedTransaction()
                                                }
                                            }

                                            override fun onSucc(data: ByteArray?) {
                                                dialog.dismiss()
                                                if (null != pinHandler)
                                                    pinHandler.removeCallbacksAndMessages(null)
                                                // transactionCallback(cardProcessedDataModal)

                                                val transactionEMIISO = CreateEMITransactionPacket(globalCardProcessedModel, emiCustomerDetails).createTransactionPacket()
                                                logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                                                //runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                                                GlobalScope.launch(Dispatchers.IO) {
                                                    checkReversal(transactionEMIISO, globalCardProcessedModel)
                                                }
                                            }

                                        }

                                        dialog.listener = pwdListener
                                        dialog.show()


                                    }, 100)
                                }*/
                            } else {

                                val transactionEMIISO = CreateEMITransactionPacket(globalCardProcessedModel, emiCustomerDetails).createTransactionPacket()
                                logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                               // runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                                GlobalScope.launch(Dispatchers.IO) {
                                    checkReversal(transactionEMIISO, globalCardProcessedModel)
                                }
                            }

                        }
                        else -> {
                            VFService.vfIEMV?.importCardConfirmResult(ConstIPBOC.importCardConfirmResult.pass.allowed) // to import card detail data
                        }
                    }

                } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
                    var cardProcessedData: CardProcessedDataModal? =
                        data.getSerializableExtra("cardprocess") as? CardProcessedDataModal
                    when (globalCardProcessedModel.getReadCardType()) {
                        DetectCardType.MAG_CARD_TYPE -> {
                            // for Mag
                            val msg = "No EMI has been selected.\nDo you want to continue as Sale?"
                            val iDialog = this@VFTransactionActivity as IDialog
                            iDialog.getMsgDialog("EMI", msg, "Yes", "No", {
                                globalCardProcessedModel.setTransType(TransactionType.SALE.type)
                                if (globalCardProcessedModel.getIsOnline() == 1) {
                                    initializePinInputListenerSale(globalCardProcessedModel)
                                    val param = Bundle()
                                    val globleparam = Bundle()
                                    val panBlock: String? = globalCardProcessedModel.getPanNumberData()
                                    val pinLimit = byteArrayOf(4, 5, 6) // byteArrayOf(Utility.HEX2DEC(retryTimes))
                                    param.putByteArray(ConstIPinpad.startPinInput.param.KEY_pinLimit_ByteArray, pinLimit)
                                    param.putInt(ConstIPinpad.startPinInput.param.KEY_timeout_int, 20)
                                    when (globalCardProcessedModel.getIsOnline()) {
                                        1 -> param.putBoolean(ConstIPinpad.startPinInput.param.KEY_isOnline_boolean, true)
                                        2 -> param.putBoolean(ConstIPinpad.startPinInput.param.KEY_isOnline_boolean, false)
                                    }

                                    param.putString(ConstIPinpad.startPinInput.param.KEY_pan_String, panBlock)
                                    param.putString(ConstIPinpad.startPinInput.param.KEY_promptString_String, "Enter PIN")
                                    param.putInt(
                                        ConstIPinpad.startPinInput.param.KEY_desType_int,
                                        ConstIPinpad.startPinInput.param.Value_desType_3DES
                                    )
                                    try {
                                        VFService.vfPinPad?.startPinInput(
                                            VFEmv.workKeyId, param, globleparam,
                                            VFService.pinInputListener
                                        )
                                    } catch (e: RemoteException) {
                                        e.printStackTrace()
                                    }
                                  /*  if (null != pinHandler) {
                                        pinHandler.postDelayed(Runnable {
                                            var dialog = PinpadPopUpWindow(this@VFTransactionActivity, R.style.popup_dialog, pinHandler, globalCardProcessedModel, this@VFTransactionActivity.getString(R.string.bank_card_password), "")
                                            val pwdListener: PinpadPopUpWindow.OnPwdListener = object : PinpadPopUpWindow.OnPwdListener {
                                                    override fun onErr() {
                                                        dialog.dismiss()
                                                        if (null != pinHandler)
                                                            pinHandler.removeCallbacksAndMessages(null)
                                                    }

                                                    override fun onSucc(data: ByteArray?) {
                                                        dialog.dismiss()
                                                        if (null != pinHandler)
                                                            pinHandler.removeCallbacksAndMessages(
                                                                null
                                                            )
                                                        GlobalScope.launch(Dispatchers.Main) {
                                                            this@VFTransactionActivity.finish()
                                                        }

                                                        val transactionISO =
                                                            CreateTransactionPacket(globalCardProcessedModel).createTransactionPacket()
                                                        logger("Transaction REQUEST PACKET --->>", transactionISO.isoMap, "e")
                                                    //    runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                                                        GlobalScope.launch(Dispatchers.IO) {
                                                            checkReversal(transactionISO, globalCardProcessedModel)
                                                        }
                                                    }

                                                }

                                            dialog.listener = pwdListener
                                            dialog.show()


                                        }, 100)
                                    }*/
                                } else {

                                    val transactionISO = CreateTransactionPacket(globalCardProcessedModel).createTransactionPacket()
                                    logger("Transaction REQUEST PACKET --->>", transactionISO.isoMap, "e")
                                  //  runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                                    GlobalScope.launch(Dispatchers.IO) {
                                        checkReversal(transactionISO, globalCardProcessedModel)
                                    }
                                }

                            }, {
                                declinedTransaction()
                            })
                        }
                        else -> {
                            val msg = "No EMI has been selected.\nDo you want to continue as Sale?"
                            val iDialog = this@VFTransactionActivity as IDialog
                            iDialog.getMsgDialog("EMI", msg, "Yes", "No", {
                                globalCardProcessedModel.setTransType(TransactionType.SALE.type)
                                VFService.vfIEMV?.importCardConfirmResult(ConstIPBOC.importCardConfirmResult.pass.allowed) // to import card detail data
                                //  emvProcessNext(cardProcessedData)
                            }, {
                                declinedTransaction()
                            })

                        }
                    }
                }
            }
        }

    }

    private fun emvProcessNext(cardProcessedData: CardProcessedDataModal?) {
        val transactionISO = CreateTransactionPacket(cardProcessedData!!).createTransactionPacket()
        // logger("Transaction REQUEST PACKET --->>", transactionISO.isoMap, "e")
        //  runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
        GlobalScope.launch(Dispatchers.IO) {
            checkReversal(transactionISO, cardProcessedData)
        }
    }

    //Below method is used to Sync Offline Sale and Ask for Auto Settlement:-
    private fun syncOfflineSaleAndAskAutoSettlement(autoSettleCode: String) {
        val offlineSaleData = BatchFileDataTable.selectOfflineSaleBatchData()
        if (offlineSaleData.size > 0) {
            showProgress(getString(R.string.please_wait_offline_sale_sync))
            SyncOfflineSaleToHost(this@VFTransactionActivity, autoSettleCode) { offlineSaleStatus, validationMsg ->
                if (offlineSaleStatus == 1)
                    GlobalScope.launch(Dispatchers.Main) {
                        hideProgress()
                        delay(1000)
                        if (autoSettleCode == "1") {
                            alertBoxWithAction(null, null, getString(R.string.batch_settle),
                                getString(R.string.do_you_want_to_settle_batch),
                                true, getString(R.string.positive_button_yes), {
                                    startActivity(
                                        Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                            putExtra("appUpdateFromSale", true)
                                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                }, {
                                    startActivity(
                                        Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        })
                                })
                        } else {
                            startActivity(
                                Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                        }
                    }
                else
                    GlobalScope.launch(Dispatchers.Main) {
                        hideProgress()
                        //VFService.showToast(validationMsg)
                        alertBoxWithAction(null, null,
                            getString(R.string.offline_sale_uploading),
                            getString(R.string.fail)+validationMsg,
                            false, getString(R.string.positive_button_ok), {
                                startActivity(
                                    Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                        flags =
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    })
                            }, {

                            })


                    }
            }
        } else {
            GlobalScope.launch(Dispatchers.Main) {
            if (autoSettleCode == "1") {
                alertBoxWithAction(null, null,
                    getString(R.string.batch_settle),
                    getString(R.string.do_you_want_to_settle_batch),
                    true, getString(R.string.positive_button_yes), {
                        startActivity(
                            Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                putExtra("appUpdateFromSale", true)
                                flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                    }, {
                        startActivity(
                            Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                                flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                    })
            } else {
                GlobalScope.launch(Dispatchers.Main) {
                    startActivity(
                        Intent(this@VFTransactionActivity, MainActivity::class.java).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                }
            }
        }
        }
    }

    private fun initializePinInputListener(globalCardProcessedModel: CardProcessedDataModal, emiCustomerDetails: EmiCustomerDetails?) {
        VFService.pinInputListener = object : PinInputListener.Stub() {
            @Throws(RemoteException::class)
            override fun onInput(len: Int, key: Int) {
                Log.d("Data", "PinPad onInput, len:$len, key:$key")
            }

            @Throws(RemoteException::class)
            override fun onConfirm(data: ByteArray, isNonePin: Boolean) {
                Log.d("Data", "PinPad onConfirm")
                VFService.vfIEMV?.importPin(1, data)
                Log.d(MainActivity.TAG, "PinPad hex encrypted data ---> " + Utility.byte2HexStr(data))
                VFEmv.savedPinblock = data
                globalCardProcessedModel.setGeneratePinBlock(Utility.byte2HexStr(data))

                globalCardProcessedModel.setPosEntryMode(PosEntryModeType.POS_ENTRY_SWIPED_NO4DBC_PIN.posEntry.toString())
                globalCardProcessedModel.setApplicationPanSequenceValue("00")

                val transactionEMIISO = CreateEMITransactionPacket(globalCardProcessedModel, emiCustomerDetails).createTransactionPacket()
                logger("Transaction REQUEST PACKET --->>", transactionEMIISO.isoMap, "e")
                //runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                GlobalScope.launch(Dispatchers.IO) {
                    checkReversal(transactionEMIISO, globalCardProcessedModel)
                }

            }

            @Throws(RemoteException::class)
            override fun onCancel() {
                Log.d("Data", "PinPad onCancel")
                GlobalScope.launch(Dispatchers.Main) {
                    this@VFTransactionActivity.declinedTransaction()
                }
            }

            @Throws(RemoteException::class)
            override fun onError(errorCode: Int) {
                Log.d("Data", "PinPad onError, code:$errorCode")
                GlobalScope.launch(Dispatchers.Main) {
                    this@VFTransactionActivity.declinedTransaction()
                }
            }
        }
    }
    private fun initializePinInputListenerSale(globalCardProcessedModel: CardProcessedDataModal) {
        VFService.pinInputListener = object : PinInputListener.Stub() {
            @Throws(RemoteException::class)
            override fun onInput(len: Int, key: Int) {
                Log.d("Data", "PinPad onInput, len:$len, key:$key")
            }

            @Throws(RemoteException::class)
            override fun onConfirm(data: ByteArray, isNonePin: Boolean) {
                Log.d("Data", "PinPad onConfirm")
                VFService.vfIEMV?.importPin(1, data)
                Log.d(MainActivity.TAG, "PinPad hex encrypted data ---> " + Utility.byte2HexStr(data))
                VFEmv.savedPinblock = data
                globalCardProcessedModel.setGeneratePinBlock(Utility.byte2HexStr(data))

                globalCardProcessedModel.setPosEntryMode(PosEntryModeType.POS_ENTRY_SWIPED_NO4DBC_PIN.posEntry.toString())
                globalCardProcessedModel.setApplicationPanSequenceValue("00")

                val transactionISO = CreateTransactionPacket(globalCardProcessedModel).createTransactionPacket()
                logger("Transaction REQUEST PACKET --->>", transactionISO.isoMap, "e")
                //    runOnUiThread { showProgress(getString(R.string.sale_data_sync)) }
                GlobalScope.launch(Dispatchers.IO) {
                    checkReversal(transactionISO, globalCardProcessedModel)
                }

            }

            @Throws(RemoteException::class)
            override fun onCancel() {
                Log.d("Data", "PinPad onCancel")
                GlobalScope.launch(Dispatchers.Main) {
                    this@VFTransactionActivity.declinedTransaction()
                }
            }

            @Throws(RemoteException::class)
            override fun onError(errorCode: Int) {
                Log.d("Data", "PinPad onError, code:$errorCode")
                GlobalScope.launch(Dispatchers.Main) {
                    this@VFTransactionActivity.declinedTransaction()
                }
            }
        }
    }


    /*private fun txnSuccessToast(){
            val layout = layoutInflater.inflate(R.layout.success_toast, custom_toast_layout)
            val myToast = Toast(applicationContext)
            myToast.duration = Toast.LENGTH_LONG
            myToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            myToast.view = layout//setting the view of custom toast layout
            myToast.show()
    }*/
}
