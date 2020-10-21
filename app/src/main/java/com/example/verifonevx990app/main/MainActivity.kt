package com.example.verifonevx990app.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.Button
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.appupdate.*
import com.example.verifonevx990app.disputetransaction.CreateSettlementPacket
import com.example.verifonevx990app.disputetransaction.SettlementFragment
import com.example.verifonevx990app.disputetransaction.VoidTransactionFragment
import com.example.verifonevx990app.emv.transactionprocess.CardProcessedDataModal
import com.example.verifonevx990app.emv.transactionprocess.VFTransactionActivity
import com.example.verifonevx990app.init.*
import com.example.verifonevx990app.nontransaction.EmiActivity
import com.example.verifonevx990app.offlinemanualsale.OfflineManualSaleInputFragment
import com.example.verifonevx990app.preAuth.PendingPreAuthFragment
import com.example.verifonevx990app.preAuth.PendingPreauth
import com.example.verifonevx990app.preAuth.PreAuthCompleteInputDetailFragment
import com.example.verifonevx990app.preAuth.VoidPreAuthFragment
import com.example.verifonevx990app.realmtables.*
import com.example.verifonevx990app.tipAdjust.TipAdjustFragment
import com.example.verifonevx990app.transactions.InputAmountFragment
import com.example.verifonevx990app.transactions.TransactionActivity
import com.example.verifonevx990app.utils.printerUtils.PrintUtil
import com.example.verifonevx990app.voidofflinesale.VoidOfflineSale
import com.example.verifonevx990app.voidrefund.VoidOfRefund
import com.example.verifonevx990app.vxUtils.*
import com.example.verifonevx990app.vxUtils.ROCProviderV2.refreshToolbarLogos
import com.example.verifonevx990app.vxUtils.ROCProviderV2.saveBatchInPreference
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_toolbar.*
import kotlinx.android.synthetic.main.main_drawer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : BaseActivity(), IFragmentRequest,
    BottomNavigationView.OnNavigationItemSelectedListener {
    private var isToExit = false
    private val initFragment by lazy { InitFragment() }
    private val dashBoardFragment by lazy { DashboardFragment() }
    private val bottomNavigationView by lazy { findViewById<BottomNavigationView>(R.id.ma_bnv) }
    var merchantName = "X990 EMV Demo"
    private var appUpdateProcCode = ProcessingCode.APP_UPDATE.code
    private var totalAppUpdateBytes = 0
    private var APP_UPDATE_REQUEST = 200
    private var tempSettlementByteArray: ByteArray? = null
    private var settlementServerHitCount: Int = 0
    private var offlineTransactionAmountLimit: Double? = 0.0

    companion object {
        val TAG = MainActivity::class.java.simpleName
        const val KEY_RESULT_int = "RESULT"
        const val KEY_SIGNATURE_boolean = "SIGNATURE"
        const val VALUE_RESULT_QPBOC_ARQC =
            201 //) - QPBOC_ARQC, online request, part of PBOC standard<br>
        const val VALUE_RESULT_AARESULT_ARQC = 2 //, the action analysis result<br>
        const val VALUE_RESULT_PAYPASS_MAG_ARQC =
            302 // -the mode of magnetic card on paypass request<br>
        const val VALUE_RESULT_PAYPASS_EMV_ARQC = 303 //- the mode of EMV on paypass request<br>
        const val KEY_ARQC_DATA_String = "ARQC_DATA"
        const val KEY_REVERSAL_DATA_String = "REVERSAL_DATA"

        //Below Const String are SubHeader Keys:-
        const val VOID_SUB_HEADER = "void_sub_header"
        const val INPUT_SUB_HEADING = "input_amount"

    }

    //Below Key is only we get in case of Auto Settlement == 1 after Sale:-
    private val appUpdateFromSale by lazy { intent.getBooleanExtra("appUpdateFromSale", false) }
    private val changeTID by lazy { intent.getBooleanExtra("changeTID", false) }
    private var alert: androidx.appcompat.app.AlertDialog? = null
    private val builder by lazy { androidx.appcompat.app.AlertDialog.Builder(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUI()
        decideHome()
        refreshToolbarLogos(this)

        //Settle Batch When Auto Settle == 1 After Sale:-
        if (appUpdateFromSale) {
            autoSettleBatchData()
        }

        //If ChangeTId is True then auto fresh init must be performed:-
        if (changeTID) {
            startFullInitProcess()
        }
    }

    //Below Alert Dialog is to show Correct Time pop-up:-
    private fun showCorrectTimePopUp() {
        builder.setTitle(getString(R.string.alert))
        builder.setMessage(getString(R.string.incorrect_time_hint))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.positive_button_ok)) { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
            }
        alert = builder.create()
        alert?.show()
    }

    private fun onInitResponse(res: String, success: Boolean, progress: Boolean) {
        if (progress) {
            progressTitleMsg.text = res
        } else {
            hideProgress()
            if (success) {
                GlobalScope.launch {
                    AppPreference.saveLogin(true)
                    UserProvider.refresh()
                }
                getInfoDialog("Info", res) {
                    decideHome()
                    UserProvider.refresh()
                    refreshSide()
                }
            } else {
                GlobalScope.launch { AppPreference.saveLogin(false) }
                getInfoDialog("Error", res) {}
            }
        }
    }

    override fun onEvents(event: VxEvent) {
        when (event) {
            is VxEvent.ChangeTitle -> {
                //     main_toolbar_tv.text = event.titleName
                //    main_toolbar_tv.text = VFService.fromHtml(
                "<font color=\"#683992\">bonushub</font>"
                //     )
                /*   if (event.titleName == "Init") {
                       ma_bnv.visibility = View.GONE
                   } else {
                       ma_bnv.visibility = View.VISIBLE

                   }*/

            }

            //EmiActivity

            is VxEvent.Emi -> {
                if (event.type == EDashboardItem.EMI_ENQUIRY) {
                    var cardProcessedData = CardProcessedDataModal()
                    cardProcessedData.setEmiType(EIntentRequest.EMI_ENQUIRY.code)
                    startActivityForResult(Intent(this, EmiActivity::class.java).apply {
                        val transAmt = "%.2f".format(event.amt)
                        putExtra("amount", event.amt)
                        putExtra("cardprocess", cardProcessedData)
                    }, EIntentRequest.EMI_ENQUIRY.code)
                } else {
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        val transAmt = "%.2f".format(event.amt)
                        putExtra("amt", transAmt)
                        putExtra("type", TransactionType.EMI_SALE.type) //EMI //UiAction.BANK_EMI
                        putExtra("proc_code", ProcessingCode.SALE.code)
                    }, EIntentRequest.TRANSACTION.code)
                }
            }

            is VxEvent.ReplaceFragment -> {
                transactFragment(event.fragment, isBackStackAdded = true)
            }

            is VxEvent.InitTerminal -> {
                if (checkInternetConnection()) {
                    val batchData = BatchFileDataTable.selectBatchData()
                    when {
                        !AppPreference.getBoolean(AppPreference.LOGIN) -> showEnterTIDPopUp()

                        AppPreference.getBoolean(PrefConstant.SERVER_HIT_STATUS.keyName.toString()) ->
                            VFService.showToast(getString(R.string.please_settle_batch_first_before_init))

                        !TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY)) ->
                            VFService.showToast(getString(R.string.please_settle_batch_first_before_init))

                        batchData.size > 0 -> VFService.showToast(getString(R.string.please_settle_batch_first_before_init))

                        else -> startFullInitProcess()
                    }
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }

            }

            is VxEvent.AppUpdate -> {
                if (checkInternetConnection()) {
                    //Check if we have FTP IP Address and Port in Preference or not:-
                    val ftpIPAddress =
                        AppPreference.getString(PrefConstant.FTP_IP_ADDRESS.keyName.toString())
                    val ftpIPPort =
                        AppPreference.getIntData(PrefConstant.FTP_IP_PORT.keyName.toString())
                    val ftpUserName =
                        AppPreference.getString(PrefConstant.FTP_USER_NAME.keyName.toString())
                    val ftpPassword =
                        AppPreference.getString(PrefConstant.FTP_PASSWORD.keyName.toString())
                    val ftpFileName =
                        AppPreference.getString(PrefConstant.FTP_FILE_NAME.keyName.toString())
                    if (!TextUtils.isEmpty(ftpIPAddress) && ftpIPPort != 0 && !TextUtils.isEmpty(
                            ftpUserName
                        )
                        && !TextUtils.isEmpty(ftpPassword) && !TextUtils.isEmpty(ftpFileName)
                    ) {
                        val batchData = BatchFileDataTable.selectBatchData()
                        if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString())) {
                            when {
                                AppPreference.getBoolean(PrefConstant.SERVER_HIT_STATUS.keyName.toString()) ->
                                    VFService.showToast(getString(R.string.please_settle_batch_before_app_update))

                                !TextUtils.isEmpty(AppPreference.getString(AppPreference.GENERIC_REVERSAL_KEY)) ->
                                    VFService.showToast(getString(R.string.please_settle_batch_before_app_update))

                                batchData.size > 0 -> VFService.showToast(getString(R.string.please_settle_batch_before_app_update))

                                else -> {
                                    startFTPAppUpdate(
                                        ftpIPAddress,
                                        ftpIPPort,
                                        ftpUserName,
                                        ftpPassword,
                                        ftpFileName
                                    )
                                }
                            }
                        } else {
                            checkAndPerformOperation()
                        }

                    } else {
                        alertBoxWithAction(null,
                            null,
                            getString(R.string.app_update),
                            getString(R.string.update_not_available),
                            false,
                            getString(R.string.positive_button_ok),
                            {},
                            {})
                    }
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }
        }
    }

    //Below method is used to show enter TID Pop up when in case :-
    // When merchant tries to INIT from bank functions directly without Changing TID or doing init from INIT Screen
    private fun showEnterTIDPopUp() {
        Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.item_get_invoice_no)
            setCancelable(false)

            val enterTID = this.findViewById<BHEditText>(R.id.invoice_no_et)
            val titleText = this.findViewById<BHTextView>(R.id.title_tv)
            titleText.text = getString(R.string.enter_tid)
            enterTID.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
            enterTID.inputType = InputType.TYPE_CLASS_NUMBER


            enterTID.apply {
                setOnFocusChangeListener { _, _ -> error = null }
                hint = getString(R.string.enter_tid)
            }

            val okBtn = findViewById<Button>(R.id.invoice_ok_btn)

            findViewById<Button>(R.id.invoice_cancel_btn).setOnClickListener {
                dismiss()
            }

            okBtn.setOnClickListener {
                if (enterTID.text?.length == 8) {
                    dismiss()
                    startFullInitProcess(enterTID.text.toString())
                } else {
                    enterTID.error = getString(R.string.please_enter_a_valid_8digit_tid)
                }
            }
        }.show()
    }


    @SuppressLint("SetTextI18n")
    private fun initUI() {
        main_toolbar_start.setOnClickListener { toggleDrawer() }
        arrayOf<View>(
            // app_update_ll,
            report_ll,
            bank_fun_ll
        ).forEach { _ -> }

        //Displaying the Version Name of App:-
        findViewById<BHTextView>(R.id.version_name).text =
            "App Version: v${BuildConfig.VERSION_NAME}"


        bottomNavigationView.setOnNavigationItemSelectedListener(this)
        setDrawerClick()
        UserProvider.refresh()
        refreshSide()
    }

    //Below method is used to update App Through FTP:-
    private fun startFTPAppUpdate(
        ftpIPAddress: String? = null, ftpIPPort: Int? = null,
        ftpUserName: String, ftpPassword: String, downloadAppFileName: String
    ) {
        showProgress(getString(R.string.please_wait_downloading_application_update))
        GlobalScope.launch(Dispatchers.IO) {
            if (ftpIPAddress != null && ftpIPPort != null) {
                AppUpdateFTPClient(
                    ftpIPAddress, ftpIPPort,
                    ftpUserName, ftpPassword,
                    downloadAppFileName, this@MainActivity
                ) { appUpdateCB, fileUri ->
                    if (appUpdateCB) {
                        GlobalScope.launch(Dispatchers.Main) {
                            hideProgress()
                            VFService.showToast(getString(R.string.app_update_downloaded_successfully_please_install_updates))
                            if (!TextUtils.isEmpty(fileUri.toString())) {
                                startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        fileUri,
                                        "application/vnd.android.package-archive"
                                    )
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            } else {
                                hideProgress()
                                VFService.showToast(getString(R.string.something_went_wrong))
                            }
                        }
                    } else {
                        GlobalScope.launch(Dispatchers.Main) {
                            hideProgress()
                            VFService.showToast(getString(R.string.something_went_wrong))
                        }
                    }
                }
            }
        }
    }

    //Below method is used to update App through HTTP/HTTPs:-
    private fun startHTTPSAppUpdate() {
        AppUpdateDownloadManager(object : OnDownloadCompleteListener {
            override fun onError(msg: String) {
                GlobalScope.launch(Dispatchers.Main) {
                    hideProgress()
                    getInfoDialog(getString(R.string.connection_error), "No update available") {}
                }
            }

            override fun onDownloadComplete(path: String, appName: String) {
                if (!TextUtils.isEmpty(path)) {
                    startActivityForResult(Intent().apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setDataAndType(
                            Uri.fromFile(File(path + appName)),
                            "application/vnd.android.package-archive"
                        )
                    }, APP_UPDATE_REQUEST)
                } else {
                    hideProgress()
                    VFService.showToast(getString(R.string.something_went_wrong))
                }
            }
        }).execute()
    }

    //Below method is used to perform full init process for terminal:-
    private fun startFullInitProcess(tid: String? = TerminalParameterTable.selectFromSchemeTable()?.terminalId) {
        if (TerminalParameterTable.selectFromSchemeTable() != null) {
            if (tid?.length == 8) {
                //toggleDrawer()
                showProgress()
                KeyExchanger(tid, ::onInitResponse).apply {
                    keWithInit = true
                }.startExchange()
            }
        } else {
            if (tid?.length == 8) {
                //toggleDrawer()
                showProgress()
                KeyExchanger(tid, ::onInitResponse).apply {
                    keWithInit = true
                }.startExchange()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            APP_UPDATE_REQUEST -> {
                hideProgress()
                VFService.showToast(getString(R.string.app_update_downloaded_successfully_please_install_updates))
            }
            EIntentRequest.EMI_ENQUIRY.code -> decideHome()
        }
    }

    //Below method is used to Connect Host For App Update process:-
    private fun startTCPIPAppUpdate(
        appUpdateProccessingCode: String,
        chunkValue: String,
        partialName: String
    ) {
        val isoAppData = CreatePacketForAppUpdate(
            appUpdateProccessingCode,
            chunkValue,
            partialName
        ).createAppUpdatePacket()
        val isoAppByteArray = isoAppData.generateIsoByteRequest()
        SyncAppUpdateToHost(isoAppByteArray) { successResponseCode, responseProcessingCode, responseField60Value ->
            if (!TextUtils.isEmpty(successResponseCode) && successResponseCode == "00" &&
                responseField60Value.substring(0, 4) == AppUpdate.APP_UPDATE_AVAILABLE.updateCode
            ) {
                when (responseProcessingCode.toString()) {
                    ProcessingCode.APP_UPDATE_CONTINUE.code -> saveAndContinueUpdateApplication(
                        responseField60Value,
                        responseProcessingCode
                    )
                    ProcessingCode.APP_UPDATE.code -> {
                        hideProgress()
                        VFService.showToast(getString(R.string.app_updated_successfully))
                    }
                    else -> {
                        hideProgress()
                        VFService.showToast(getString(R.string.app_update_failed))
                    }
                }
            } else {
                hideProgress()
                VFService.showToast(getString(R.string.app_update_failed))
            }
        }
    }

    //Below method is used to save application update data in DB ApplicationUpdate Table and Continue Application Update:-
    private fun saveAndContinueUpdateApplication(
        responseField60Value: String,
        responseProcessingCode: String
    ) {
        val appPartialName = responseField60Value.substring(24, 36)
        val apkData = responseField60Value.substring(96, responseField60Value.length)
        startTCPIPAppUpdate(
            appUpdateProccessingCode = responseProcessingCode,
            chunkValue = responseField60Value.substring(8, 24),
            partialName = appPartialName
        )
    }

    private fun refreshSide() {
        //region==========Setting for sidebar details==========
        md_shop_tv.text = UserProvider.name
        val tid = "TID : ${UserProvider.tid}"
        md_tid_tv.text = tid
        val mid = "MID : ${UserProvider.mid}"
        md_mid_tv.text = mid
        //endregion
    }

    private fun toggleDrawer() {
        if (main_dl.isDrawerOpen(GravityCompat.START)) {
            main_dl.closeDrawer(GravityCompat.START, true)
        } else {
            main_dl.openDrawer(GravityCompat.START, true)
        }
    }

    private fun decideHome() {//AppPreference.getLogin()

        if (AppPreference.getLogin()) {
          //  init_ll.visibility = View.VISIBLE
           // key_exchange_ll.visibility = View.VISIBLE
            report_ll.visibility = View.VISIBLE
           // app_update_ll.visibility = View.VISIBLE
          //  key_exchange_hdfc_ll.visibility = View.VISIBLE
            transactFragment(DashboardFragment(), isBackStackAdded = true)
        } else {
            GlobalScope.launch {
                val tct = TerminalCommunicationTable.selectFromSchemeTable()

                if (tct == null) {
                    readInitFile { _, msg ->
                        logger(TAG, msg)
                        UserProvider.refresh()
                        refreshSide()
                    }
                }

            }

          //  init_ll.visibility = View.GONE
         //   key_exchange_ll.visibility = View.GONE
          //  key_exchange_hdfc_ll.visibility = View.GONE
            report_ll.visibility = View.GONE
         //   app_update_ll.visibility = View.GONE
            //initFragment
            transactFragment(initFragment, isBackStackAdded = false)
        }

    }

    override fun onFragmentRequest(action: UiAction, data: Any , extraPairData: Pair<String , String>?) {
        when (action) {
            UiAction.INIT_WITH_KEY_EXCHANGE -> {
                if (checkInternetConnection()) {
                    val tid = data as String
                    showProgress()
                    KeyExchanger(tid, ::onInitResponse).apply {
                        keWithInit = true
                    }.startExchange()
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.START_SALE -> {
                if (checkInternetConnection()) {
                    val amt = data as String
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        putExtra("amt", amt)
                        putExtra("type", TransactionType.SALE.type)
                        putExtra("proc_code", ProcessingCode.SALE.code)
                        putExtra("mobileNumber", extraPairData?.first)
                        putExtra("billNumber", extraPairData?.second)
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.BANK_EMI -> {
                if (checkInternetConnection()) {
                    val amt = data as String
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        putExtra("amt", amt)
                        putExtra("type", TransactionType.EMI_SALE.type) //EMI //UiAction.BANK_EMI
                        putExtra("proc_code", ProcessingCode.SALE.code)
                        putExtra("mobileNumber", extraPairData?.first)
                        putExtra("billNumber", extraPairData?.second)
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.CASH_ADVANCE -> {
                if (checkInternetConnection()) {
                    val amt = data as String
                    startActivityForResult(Intent(this, TransactionActivity::class.java).apply {
                        putExtra("amt", amt)
                        putExtra("type", action)
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.SALE_WITH_CASH -> {
                if (checkInternetConnection()) {
                    val amts = data as ArrayList<String>
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        val totalAmt = (amts[0].toFloat() + amts[1].toFloat()).toString()
                        val formattedTransAmount = "%.2f".format(totalAmt.toDouble())
                        putExtra("amt", formattedTransAmount)
                        putExtra("type", TransactionType.SALE_WITH_CASH.type)
                        putExtra("proc_code", ProcessingCode.SALE_WITH_CASH.code)
                        putExtra("title", TransactionType.SALE_WITH_CASH.txnTitle)
                        putExtra("saleAmt", amts[0])
                        putExtra("cashAmt", amts[1])
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.PRE_AUTH -> {
                if (checkInternetConnection()) {
                    val amt = data as String
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        putExtra("amt", amt)
                        putExtra("type", TransactionType.PRE_AUTH.type)
                        putExtra("proc_code", ProcessingCode.PRE_AUTH.code)
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            UiAction.REFUND -> {
                if (checkInternetConnection()) {
                    val amt = data as String
                    startActivityForResult(Intent(this, VFTransactionActivity::class.java).apply {
                        putExtra("amt", amt)
                        putExtra("type", TransactionType.REFUND.type)
                        putExtra("proc_code", ProcessingCode.REFUND.code)
                    }, EIntentRequest.TRANSACTION.code)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }


            else -> {
            }
        }

    }

    override fun onTransactionRequest(action: EDashboardItem) {
        when (action) {

            EDashboardItem.NONE -> {
                //AppPreference.saveBoolean(AppPreference.LOGIN, false)
                decideHome()
            }

            EDashboardItem.SALE -> {
                if (checkInternetConnection()) {
                    inflateInputFragment(
                        InputAmountFragment(),
                        SubHeaderTitle.SALE_SUBHEADER_VALUE.title,
                        action
                    )
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }
            EDashboardItem.SALE_WITH_CASH -> {
                if (checkInternetConnection()) {
                    inflateInputFragment(
                        InputAmountFragment(),
                        SubHeaderTitle.SALE_WITH_CASH_SUBHEADER_VALUE.title,
                        action
                    )
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }
            EDashboardItem.PREAUTH -> {
                if (checkInternetConnection()) {
                    inflateInputFragment(
                        InputAmountFragment(),
                        SubHeaderTitle.PREAUTH_SUBHEADER_VALUE.title,
                        action
                    )
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }
            EDashboardItem.OFFLINE_SALE -> {
                offlineTransactionAmountLimit =
                    IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)?.transactionAmountLimit?.toDouble()
                        ?.div(100)
                if (offlineTransactionAmountLimit!! > 0.0) {
                    inflateInputFragment(
                        OfflineManualSaleInputFragment(),
                        SubHeaderTitle.OFFLINE_SALE_SUBHEADER_VALUE.title,
                        action
                    )
                } else
                    VFService.showToast(getString(R.string.transaction_limit_exceeds))
            }

            EDashboardItem.VOID_OFFLINE_SALE -> {
                if (checkInternetConnection()) {
                    inflateInputFragment(
                        VoidOfflineSale(),
                        SubHeaderTitle.VOID_OFFLINE_SALE_SUBHEADER_VALUE.title,
                        action
                    )
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }

            EDashboardItem.CASH_ADVANCE -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(InputAmountFragment().apply {
                            arguments = Bundle().apply {
                                putSerializable("type", action)
                                putString(INPUT_SUB_HEADING, SubHeaderTitle.CASH_ADVANCE.title)
                            }
                        })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.VOID_SALE ->
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(VoidTransactionFragment().apply {
                            arguments = Bundle().apply {
                                putSerializable("type", action)
                                putString(
                                    INPUT_SUB_HEADING,
                                    SubHeaderTitle.VOID_SUBHEADER_VALUE.title
                                )
                            }
                        })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }


            EDashboardItem.REFUND -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        verifyAdminPasswordDialog(this) {
                            if (it) {
                                transactFragment(InputAmountFragment().apply {
                                    arguments = Bundle().apply {
                                        putSerializable("type", action)
                                        putString(
                                            INPUT_SUB_HEADING,
                                            SubHeaderTitle.REFUND_SUBHEADER_VALUE.title
                                        )
                                    }
                                })
                            }
                        }
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.PREAUTH_COMPLETE -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(PreAuthCompleteInputDetailFragment()
                            .apply {
                                arguments = Bundle().apply {
                                    putSerializable("type", action)
                                    putString(
                                        INPUT_SUB_HEADING,
                                        SubHeaderTitle.PRE_AUTH_COMPLETE.title
                                    )
                                }
                            })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.EMI_ENQUIRY, EDashboardItem.BANK_EMI -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(InputAmountFragment().apply {
                            arguments = Bundle().apply {
                                putSerializable("type", action)
                                if (action == EDashboardItem.EMI_ENQUIRY)
                                    putString(INPUT_SUB_HEADING, SubHeaderTitle.EMI_CATALOG.title)
                                if (action == EDashboardItem.BANK_EMI)
                                    putString(INPUT_SUB_HEADING, SubHeaderTitle.BANK_EMI.title)
                            }
                        })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.VOID_REFUND -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(VoidOfRefund()
                            .apply {
                                arguments = Bundle().apply {
                                    putSerializable("trans_type", TransactionType.VOID_REFUND)
                                    putSerializable("type", action)
                                    putString(
                                        INPUT_SUB_HEADING,
                                        SubHeaderTitle.VOID_REFUND_SUBHEADER_VALUE.title
                                    )
                                }
                            })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.PENDING_PREAUTH -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                )
                    if (checkInternetConnection()) {
                        PendingPreauth(this).confirmationAlert(
                            getString(R.string.confirmation),
                            getString(R.string.pending_preauth_alert_msg)
                        )
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                else
                    checkAndPerformOperation()
            }

            EDashboardItem.VOID_PREAUTH -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        transactFragment(
                            VoidPreAuthFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putSerializable("type", TransactionType.VOID_PREAUTH)
                                        putString(
                                            INPUT_SUB_HEADING,
                                            SubHeaderTitle.VOID_PRE_AUTH.title
                                        )
                                    }
                                })
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }

                } else {
                    checkAndPerformOperation()
                }
            }

            EDashboardItem.PENDING_OFFLINE_SALE -> {
                /* if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                     !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                     !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString()))
                     transactFragment(PreAuthCompleteInputDetailFragment())
                 else
                     checkAndPerformOperation()*/
            }

            EDashboardItem.SALE_TIP -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if (checkInternetConnection()) {
                        (transactFragment(TipAdjustFragment()
                            .apply {
                                arguments = Bundle().apply {
                                    putSerializable("type", TransactionType.TIP_SALE)
                                    putString(
                                        INPUT_SUB_HEADING,
                                        SubHeaderTitle.TIP_SALE.title
                                    )
                                }
                            }))
                    } else {
                        VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                    }
                } else {
                    checkAndPerformOperation()
                }
            }

            else -> {
            }
        }
    }

    //Below Method is to Handle the Input Fragment Inflate with the Sub Heading it belongs to:-
    private fun inflateInputFragment(
        fragment: Fragment,
        subHeading: String,
        action: EDashboardItem
    ) {
        if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
            !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
            !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())) {
            transactFragment(fragment.apply {
                arguments = Bundle().apply {
                    putSerializable("type", action)
                    putString(INPUT_SUB_HEADING, subHeading)
                }
            }, false)
        }else{
            if (checkInternetConnection())
                checkAndPerformOperation()
            else VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
        }
    }

    private fun setDrawerClick() {
        fun send(e: EOptionGroup, isReport: Boolean = true) {
            toggleDrawer()
            transactFragment(SubMenuFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("option", e)
                    // putSerializable("iDialog",key_exchange_ll.context)
                }
            }, isBackStackAdded = isReport)
        }

        bank_fun_ll.setOnClickListener {
            verifyAdminPasswordDialog(this) {
                if (it) {
                    send(EOptionGroup.FUNCTIONS)
                }
            }
        }

        report_ll.setOnClickListener {
            send(EOptionGroup.REPORT, false)
        }

    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                if (!AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString()) &&
                    !AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())
                ) {
                    if ((AppPreference.getLogin()))
                        transactFragment(dashBoardFragment)
                    else transactFragment(initFragment)
                } else {
                    if (checkInternetConnection())
                        checkAndPerformOperation()
                    else VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
            }
            R.id.settlement ->
                if (checkInternetConnection()) {
                    transactFragment(SettlementFragment().apply {
                        arguments = Bundle().apply {
                            putSerializable("trans_type", TransactionType.VOID_REFUND)
                            putString(
                                INPUT_SUB_HEADING,
                                SubHeaderTitle.SETTLEMENT_SUBHEADER_VALUE.title
                            )
                        }
                    }, true)
                } else {
                    VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
                }
        }

        return true
    }

    override fun onBackPressed() {

        if (main_dl.isDrawerOpen(GravityCompat.START)) {
            main_dl.closeDrawer(GravityCompat.START, true)
        } else {
            when (supportFragmentManager.findFragmentById(R.id.ma_fl)) {

                is InitFragment -> exitApp()

                is DashboardFragment -> exitApp()

                is SubMenuFragment -> supportFragmentManager.popBackStackImmediate()

                is InputAmountFragment -> supportFragmentManager.popBackStackImmediate()

                is VoidTransactionFragment -> supportFragmentManager.popBackStackImmediate()

                is VoidOfRefund -> supportFragmentManager.popBackStackImmediate()

                is SettlementFragment -> supportFragmentManager.popBackStackImmediate()

                is TableEditFragment -> supportFragmentManager.popBackStackImmediate()

                is PreAuthCompleteInputDetailFragment -> supportFragmentManager.popBackStackImmediate()

                is VoidPreAuthFragment -> supportFragmentManager.popBackStackImmediate()

                is OfflineManualSaleInputFragment -> supportFragmentManager.popBackStackImmediate()

                is VoidOfflineSale -> supportFragmentManager.popBackStackImmediate()

                is TipAdjustFragment -> {
                }
                is PendingPreAuthFragment -> supportFragmentManager.popBackStackImmediate()
            }
        }
    }

    //method to exitApp:-
    private fun exitApp() {
        if (isToExit) {
            super.finishAffinity()
        } else {
            isToExit = true
            Handler().postDelayed({
                isToExit = false
                showToast("Double click back button to exit.")
            }, 500)
        }
    }

    //Auto Settle Batch:-
    private fun autoSettleBatchData() {
        val settlementBatchData = BatchFileDataTable.selectBatchData()
        var processingCode: String? = null
        processingCode = if (appUpdateFromSale) {
            ProcessingCode.SETTLEMENT.code
        } else {
            ProcessingCode.FORCE_SETTLEMENT.code
        }
        PrintUtil(this).printDetailReport(
            settlementBatchData,
            this
        ) { detailPrintStatus ->
            if (detailPrintStatus) {
                val settlementPacket = CreateSettlementPacket(
                    processingCode,
                    settlementBatchData
                ).createSettlementISOPacket()
                val isoByteArray = settlementPacket.generateIsoByteRequest()
                GlobalScope.launch(Dispatchers.IO) {
                    settleBatch(isoByteArray)
                }
            } else
                alertBoxWithAction(null, null, getString(R.string.printing_error),
                    getString(R.string.failed_to_print_settlement_detail_report),
                    false, getString(R.string.positive_button_ok),
                    {}, {})
        }
    }

    //Below method is used to check which action to perform on click of any module in app whether Force Settlement  , Init or Logon:-
    private fun checkAndPerformOperation() {
        if (checkInternetConnection()) {
            if (AppPreference.getBoolean(PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString())) {
                alertBoxWithAction(null, null,
                    getString(R.string.batch_settle),
                    getString(R.string.please_settle_batch),
                    false, getString(R.string.positive_button_ok),
                    {
                        autoSettleBatchData()
                    },
                    {})

            } else if (AppPreference.getBoolean(PrefConstant.INSERT_PPK_DPK.keyName.toString())) {
                val tpt = TerminalParameterTable.selectFromSchemeTable()
                if (tpt != null) {
                    val tid = tpt.terminalId.toLong().toString()
                    toggleDrawer()
                    showProgress()
                    KeyExchanger(tid, ::onInitResponse).apply {
                        keWithInit = true
                    }.insertPPKDPKAfterSettlement()
                }
            } else if (AppPreference.getBoolean(PrefConstant.INIT_AFTER_SETTLEMENT.keyName.toString())) {
                val tpt = TerminalParameterTable.selectFromSchemeTable()
                if (tpt != null) {
                    val tid = tpt.terminalId.toLong().toString()
                    toggleDrawer()
                    showProgress()
                    KeyExchanger(tid, ::onInitResponse).apply {
                        keWithInit = true
                    }.startExchange()
                }
            } else {
                VFService.showToast(getString(R.string.something_went_wrong))
            }
        } else {
            VFService.showToast(getString(R.string.no_internet_available_please_check_your_internet))
        }
    }
    //Settle Batch and Do the Init:-
    suspend fun settleBatch(settlementByteArray: ByteArray?) {
        runOnUiThread {
            showProgress()
        }
        if (settlementByteArray != null) {
            HitServer.hitServer(settlementByteArray, { result, success ->
                if (success && !TextUtils.isEmpty(result)) {
                    hideProgress()
                    tempSettlementByteArray = settlementByteArray
                    /* Note:- If responseCode is "00" then delete Batch File Data Table happens and Navigate to MainActivity
                              else responseCode is "95" then Batch Upload will Happens and then delete Batch File Data Table happens
                              and Navigate to MainActivity */
                    val responseIsoData: IsoDataReader = readIso(result, false)
                    logger("Transaction RESPONSE ", "---", "e")
                    logger("Transaction RESPONSE --->>", responseIsoData.isoMap, "e")
                    Log.e(
                        "Success 39-->  ",
                        responseIsoData.isoMap[39]?.parseRaw2String().toString() + "---->" +
                                responseIsoData.isoMap[58]?.parseRaw2String().toString()
                    )

                    val responseCode = responseIsoData.isoMap[39]?.parseRaw2String().toString()
                    val hostFailureValidationMsg =
                        responseIsoData.isoMap[58]?.parseRaw2String().toString()
                    if (responseCode == "00") {
                        settlementServerHitCount = 0
                        AppPreference.saveBoolean(
                            PrefConstant.SERVER_HIT_STATUS.keyName.toString(),
                            false
                        )
                        AppPreference.saveBoolean(
                            PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString(),
                            false
                        )
                        val terminalParameterTable = TerminalParameterTable.selectFromSchemeTable()
                        val isAppUpdateAvailableData = responseIsoData.isoMap[63]?.parseRaw2String()

                        Log.d("Success Data:- ", result)
                        Log.d("isAppUpdate:- ", isAppUpdateAvailableData.toString())

                        //Below we are placing values in preference for the use to know whether batch is settled or not:-
                        AppPreference.saveString(
                            PrefConstant.SETTLEMENT_PROCESSING_CODE.keyName.toString(),
                            ProcessingCode.SETTLEMENT.code
                        )

                        AppPreference.saveBoolean(
                            PrefConstant.SETTLE_BATCH_SUCCESS.keyName.toString(), false
                        )

                        Log.d("Success Data:- ", result)

                        //Below we are placing values in preference for the use to know whether batch is settled or not:-
                        AppPreference.saveString(
                            PrefConstant.SETTLEMENT_PROCESSING_CODE.keyName.toString(),
                            ProcessingCode.SETTLEMENT.code
                        )

                        AppPreference.saveBoolean(
                            PrefConstant.SETTLE_BATCH_SUCCESS.keyName.toString(),
                            false
                        )

                        val batchList = BatchFileDataTable.selectBatchData()

                        //Batch and Roc Increment for Settlement:-
                        val batchNumber =
                            AppPreference.getIntData(PrefConstant.SETTLEMENT_BATCH_INCREMENT.keyName.toString()) + 1

                        AppPreference.setIntData(
                            PrefConstant.SETTLEMENT_BATCH_INCREMENT.keyName.toString(), batchNumber
                        )

                        val settlement_roc =
                            AppPreference.getIntData(PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString()) + 1

                        AppPreference.setIntData(
                            PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString(), settlement_roc
                        )

                        PrintUtil(this).printSettlementReport(this, batchList, true) {
                            if (it) {
                                //Added by Ajay Thakur
                                //Saving Batch Data For Last Summary Report
                                saveBatchInPreference(batchList)
                                //Delete All BatchFile Data from Table after Settlement:-
                                deleteBatchTableDataInDB()

                                //Added by Lucky Singh.
                                //Delete Last Success Receipt From App Preference.
                                AppPreference.saveString(AppPreference.LAST_SUCCESS_RECEIPT_KEY,"")

                                //Added by Manish Kumar
                                //Reset Roc and Invoice by 1.
                                ROCProviderV2.resetRoc(AppPreference.getBankCode())

                                //Added by Ajay Thakur
                                TerminalParameterTable.updateTerminalDataInvoiceNumber("0")

                                //Here we are incrementing sale batch number also for next sale:-
                                TerminalParameterTable.updateSaleBatchNumber(batchNumber.toString())
                                GlobalScope.launch(Dispatchers.Main) {
                                    alertBoxWithAction(null,
                                        null,
                                        getString(R.string.settlement_success),
                                        getString(R.string.settlement_successfully_done),
                                        false,
                                        getString(R.string.positive_button_ok),
                                        {
                                            runOnUiThread {
                                                if (!TextUtils.isEmpty(isAppUpdateAvailableData) && isAppUpdateAvailableData != "00" && isAppUpdateAvailableData != "01") {
                                                    val dataList =
                                                        isAppUpdateAvailableData?.split("|") as MutableList<String>
                                                    if (dataList.size > 1) {
                                                        onBackPressed()
                                                        writeAppVersionNameInFile(this@MainActivity)
                                                        when (dataList[0]) {
                                                            AppUpdate.MANDATORY_APP_UPDATE.updateCode -> {
                                                                if (terminalParameterTable?.reservedValues?.length == 20 &&
                                                                    terminalParameterTable.reservedValues.endsWith(
                                                                        "1"
                                                                    )
                                                                )
                                                                    startFTPAppUpdate(
                                                                        dataList[2],
                                                                        dataList[3].toInt(),
                                                                        dataList[4],
                                                                        dataList[5],
                                                                        dataList[7]
                                                                    )
                                                                /*else
                                                            startHTTPSAppUpdate()*/ //------------>HTTPS App Update not in use currently
                                                            }
                                                            AppUpdate.OPTIONAL_APP_UPDATE.updateCode -> {
                                                                alertBoxWithAction(
                                                                    null,
                                                                    null,
                                                                    getString(R.string.app_update),
                                                                    getString(R.string.app_update_available_do_you_want_to_update),
                                                                    true,
                                                                    getString(R.string.yes),
                                                                    {
                                                                        if (terminalParameterTable?.reservedValues?.length == 20 &&
                                                                            terminalParameterTable.reservedValues.endsWith(
                                                                                "1"
                                                                            )
                                                                        )
                                                                            startFTPAppUpdate(
                                                                                dataList[2],
                                                                                dataList[3].toInt(),
                                                                                dataList[4],
                                                                                dataList[5],
                                                                                dataList[7]
                                                                            )
                                                                        /*else
                                                                startHTTPSAppUpdate()*/ //------------>HTTPS App Update not in use currently
                                                                    },
                                                                    {})
                                                            }
                                                            else -> {
                                                                onBackPressed()
                                                            }
                                                        }
                                                    } else {
                                                        VFService.showToast(getString(R.string.something_went_wrong_in_app_update))
                                                        onBackPressed()
                                                    }
                                                } else {
                                                    onBackPressed()
                                                    when (isAppUpdateAvailableData) {
                                                        "00" -> {
                                                            if (terminalParameterTable != null) {
                                                                val tid =
                                                                    terminalParameterTable.terminalId.toLong()
                                                                        .toString()
                                                                showProgress()
                                                                KeyExchanger(
                                                                    tid,
                                                                    ::onInitResponse
                                                                ).apply {
                                                                    keWithInit = true
                                                                }.insertPPKDPKAfterSettlement()
                                                            }
                                                        }

                                                        "01" -> {
                                                            if (terminalParameterTable != null) {
                                                                val tid =
                                                                    terminalParameterTable.terminalId.toLong()
                                                                        .toString()
                                                                showProgress()
                                                                KeyExchanger(
                                                                    tid,
                                                                    ::onInitResponse
                                                                ).apply {
                                                                    keWithInit = true
                                                                }.startExchange()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        {})
                                }
                            } else
                                VFService.showToast(getString(R.string.printing_error))
                        }
                    } else {
                        runOnUiThread {
                            VFService.showToast(hostFailureValidationMsg)
                            AppPreference.saveBoolean(
                                PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString(),
                                true
                            )
                        }
                    }

                } else {
                    hideProgress()
                    runOnUiThread {
                        AppPreference.saveBoolean(
                            PrefConstant.BLOCK_MENU_OPTIONS.keyName.toString(),
                            true
                        )
                    }
                    VFService.showToast("Settlement Failure")
                    Log.d("Failure Data:- ", result)
                    AppPreference.saveString(
                        PrefConstant.SETTLEMENT_PROCESSING_CODE.keyName.toString(),
                        ProcessingCode.FORCE_SETTLEMENT.code
                    )

                    //Added by Ajay Thakur
                    val settlement_roc =
                        AppPreference.getIntData(PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString()) + 1
                    AppPreference.setIntData(
                        PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString(), settlement_roc
                    )

                    AppPreference.saveString(
                        PrefConstant.SETTLEMENT_PROCESSING_CODE.keyName.toString(),
                        ProcessingCode.FORCE_SETTLEMENT.code
                    )

                    AppPreference.saveBoolean(
                        PrefConstant.SETTLE_BATCH_SUCCESS.keyName.toString(), true
                    )
                }
            }, {
                //backToCalled(it, false, true)
            })
        }
    }
}


enum class TransType {
    T_BANLANCE, T_PURCHASE, M_SIGNIN
}

//Below Enum Class is used for SubHeading intent extra when we transact Fragment:-
enum class SubHeaderTitle(val title: String) {
    VOID_SUBHEADER_VALUE("Void Sale"),
    SALE_SUBHEADER_VALUE("Sale"),
    SALE_WITH_CASH_SUBHEADER_VALUE("Sale with Cash"),
    PREAUTH_SUBHEADER_VALUE("PreAuth"),
    OFFLINE_SALE_SUBHEADER_VALUE("Offline Sale"),
    VOID_OFFLINE_SALE_SUBHEADER_VALUE("Void Offline Sale"),
    SETTLEMENT_SUBHEADER_VALUE("Settlement"),
    CASH_ADVANCE("Cash Advance"),
    PRE_AUTH_COMPLETE("Auth Complete"),
    VOID_PRE_AUTH("Void Preauth"),
    TIP_SALE("Tip Adjust"),
    VOID_REFUND_SUBHEADER_VALUE("Void Refund"),
    REFUND_SUBHEADER_VALUE("Refund"),
    EMI_CATALOG("EMI Enquiry"),
    BANK_EMI("Bank EMI")

}

//Below Enum Class is used for Preference Save [String , Int and Boolean] Keys Constant:-
enum class PrefConstant(val keyName: Any) {
    AID_RID_INSERT_STATUS("aid_rid_status"),
    SALE_INVOICE_INCREMENT("sale_invoice_increment"),
    SETTLEMENT_ROC_INCREMENT("settlement_roc_increment"),
    OFFLINE_ROC_INCREMENT("offline_roc_increment"),
    SETTLEMENT_BATCH_INCREMENT("settlement_batch_number"),
    SETTLEMENT_PROCESSING_CODE("settlement_processing_code"),
    SETTLE_BATCH_SUCCESS("settle_batch_success"),
    INIT_AFTER_SETTLE_BATCH_SUCCESS("init_after_settle_batch_success"),
    FTP_IP_ADDRESS("ftp_ip_address"),
    FTP_IP_PORT("ftp_ip_port"),
    FTP_USER_NAME("ftp_user_name"),
    FTP_PASSWORD("ftp_password"),
    FTP_FILE_NAME("ftp_file_name"),
    BLOCK_MENU_OPTIONS("block_menu_options"),
    INSERT_PPK_DPK("insert_ppk_dpk"),
    INIT_AFTER_SETTLEMENT("init_after_settlement"),
    VOID_ROC_INCREMENT("void_roc_increment"),
    SERVER_HIT_STATUS("server_hit_status"),
    APP_UPDATE_CONFIRMATION_TO_HOST("app_update_confirmation_to_host")
}

//Below Enum Class is used to detect different card Types:-
enum class DetectCardType(val cardType : Int,val cardTypeName:String="") {
    CARD_ERROR_TYPE(0),
    MAG_CARD_TYPE(1, "Mag"),
    EMV_CARD_TYPE(2, "Chip"),
    CONTACT_LESS_CARD_TYPE(3, "CTLS"),
    CONTACT_LESS_CARD_WITH_MAG_TYPE(4, "CTLS"),
    MANUAL_ENTRY_TYPE(5, "MAN")
}

enum class DetectError(val errorCode: Int) {
    SeePhone(150),
    DynamicLimit(208),
    RefuseTrans(202),
    Terminalcapability(204),
    EMVFallBack(12),
    NoCoOwnedApp(8),
    NeedContact(205),
    ReadCardFail(24),
    Muticarderror(26),
    IncorrectPAN(2007),
    CTLS_CARD_READ_FAILED_ERROR(29),
}

//Below Enum class is used to handle Host Response Codes:-
enum class HostResponseCode(val code: String) {
    DENY_PICK_UP_CARD("69"),
    INTERNAL_SERVER_ERROR("99"),
    SERVER_DOWN("99"),
    INTEGRATED_PACKET_ERROR("99"),
    SAME_ROC_EXIST("17"),
    BATCH_NOT_FOUND("14"),
    ISO_PACKET_FORMAT_ERROR("65"),
    SUCCESS("00")
}

//Below Enum Class is used to check which Pos Entry Type:-
enum class PosEntryModeType(val posEntry: Int) {
    //insert with pin
    EMV_POS_ENTRY_PIN(553),
    EMV_POS_ENTRY_NO_PIN(552),

    //off line pin
    EMV_POS_ENTRY_OFFLINE_PIN(554),

    //Used POS ENTRY Code for Offline Sale:-
    OFFLINE_SALE_POS_ENTRY_CODE(513),

    //used for fall back
    EMV_POS_ENTRY_FALL_MAGPIN(623),
    EMV_POS_ENTRY_FALL_MAGNOPIN(620),

    /*Below EMV Fallback case is of no USE:-
    EMV_POS_ENTRY_FALL_4DBCPIN(663),
    EMV_POS_ENTRY_FALL_4DBCNOPIN(660),*/

    ///swipe with cvv and pin
    POS_ENTRY_SWIPED_4DBC(563),

    //swipe with out cvv  with out pin
    POS_ENTRY_SWIPED_NO4DBC(523),

    //swipe pin with out cvv
    POS_ENTRY_SWIPED_NO4DBC_PIN(524),

    //Manual with cvv
    POS_ENTRY_MANUAL_4DBC(573),
    //Manual without cvv
    POS_ENTRY_MANUAL_NO4DBC(513),

    //contact less swipe data with out pin
    CTLS_MSD_POS_ENTRY_CODE(921),

    //contact less with  swipe data and pin
    CTLS_MSD_POS_WITH_PIN(923),

    // contact less  insert data with out pin
    CTLS_EMV_POS_ENTRY_CODE(911),

    // contact less insert data with pin
    CTLS_EMV_POS_WITH_PIN(913)
}


//Below ENUM class for the Error Response Code we get from Host:-
enum class ServerResponseCode(val code : Int){
    SAME_ROC_EXIST(17),
    DENY_PICK_UP_CARD(69),
    INTERNAL_SERVER_ERROR(99),
    SERVER_DOWN(99),
    INVALID_RESPONSE(99),
    ISO_PACKET_FORMAT_ERROR(65),
    BATCH_DOES_NOT_EXIST(14),
}

enum class AppUpdate(var updateCode: String) {
    APP_UPDATE_AVAILABLE("0103"),
    MANDATORY_APP_UPDATE("0220"),
    OPTIONAL_APP_UPDATE("0210")
}

enum class ConnectionError(val errorCode: Int) {
    ReadTimeout(500),
    ConnectionTimeout(408),
    ConnectionRefusedorOtherError(409),
    NetworkError(504),
    Success(200)
}

interface IFragmentRequest {
    fun onFragmentRequest(action: UiAction, data: Any , extraPairData: Pair<String , String>? = null)
    fun onTransactionRequest(action: EDashboardItem)
}