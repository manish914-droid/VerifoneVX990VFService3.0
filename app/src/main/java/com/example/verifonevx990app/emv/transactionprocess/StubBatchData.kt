package com.example.verifonevx990app.emv.transactionprocess

import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.main.DetectCardType
import com.example.verifonevx990app.main.PrefConstant
import com.example.verifonevx990app.realmtables.BatchFileDataTable
import com.example.verifonevx990app.realmtables.CardDataTable
import com.example.verifonevx990app.realmtables.IssuerParameterTable
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.transactions.EmiCustomerDetails
import com.example.verifonevx990app.utils.MoneyUtil
import com.example.verifonevx990app.utils.TransactionTypeValues
import com.example.verifonevx990app.vxUtils.*
import com.google.gson.Gson
import com.vfi.smartpos.deviceservice.aidl.IEMV
import java.text.SimpleDateFormat
import java.util.*

class StubBatchData(
    var transactionType: Int,
    var cardProcessedDataModal: CardProcessedDataModal,
    private var printExtraData: Triple<String, String, String>?,
    var batchStubCallback: (BatchFileDataTable) -> Unit
) {

    var vfIEMV: IEMV? = null

    init {
        vfIEMV = VFService.vfIEMV
        batchStubCallback(stubbingData())
    }

    //Below method is used to save Batch Data in BatchFileDataTable in DB and print the Transaction Slip:-
    private fun stubbingData(): BatchFileDataTable {
        val terminalData = TerminalParameterTable.selectFromSchemeTable()
        val issuerParameterTable =
            IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)
        val cardDataTable = CardDataTable.selectFromCardDataTable(
            cardProcessedDataModal.getPanNumberData().toString()
        )
        val batchFileData = BatchFileDataTable()
        //Auto Increment Invoice Number in BatchFileData Table:-
        var invoiceIncrementValue = 0
        if (AppPreference.getIntData(PrefConstant.SALE_INVOICE_INCREMENT.keyName.toString()) == 0) {
            invoiceIncrementValue = terminalData?.invoiceNumber?.toInt() ?: 0
            AppPreference.setIntData(
                PrefConstant.SALE_INVOICE_INCREMENT.keyName.toString(),
                invoiceIncrementValue
            )

        } else {

                invoiceIncrementValue =
                    AppPreference.getIntData(PrefConstant.SALE_INVOICE_INCREMENT.keyName.toString()) + 1
                AppPreference.setIntData(
                    PrefConstant.SALE_INVOICE_INCREMENT.keyName.toString(),
                    invoiceIncrementValue
                )

        }

        //Below we are saving Transaction related CardProcessedDataModal Data in BatchFileDataTable object to save in DB:-
        batchFileData.serialNumber = AppPreference.getString("serialNumber")
        batchFileData.sourceNII = Nii.SOURCE.nii
        batchFileData.destinationNII = Nii.DEFAULT.nii
        batchFileData.mti = Mti.DEFAULT_MTI.mti
        batchFileData.transactionType = transactionType
        batchFileData.transactionalAmmount =
            cardProcessedDataModal.getTransactionAmount().toString()
        batchFileData.nii = Nii.DEFAULT.nii
        batchFileData.applicationPanSequenceNumber =
            cardProcessedDataModal.getApplicationPanSequenceValue() ?: ""
        batchFileData.merchantName = terminalData?.receiptHeaderOne ?: ""
        batchFileData.panMask = terminalData?.panMask ?: ""
        batchFileData.panMaskConfig = terminalData?.panMaskConfig ?: ""
        batchFileData.panMaskFormate = terminalData?.panMaskFormate ?: ""
        batchFileData.merchantAddress1 = terminalData?.receiptHeaderTwo ?: ""
        batchFileData.merchantAddress2 = terminalData?.receiptHeaderThree ?: ""
        batchFileData.timeStamp = cardProcessedDataModal.getTimeStamp()?.toLong() ?: 0L
        batchFileData.transactionDate =
            dateFormater(cardProcessedDataModal.getTimeStamp()?.toLong() ?: 0L)
        batchFileData.transactionTime =
            timeFormater(cardProcessedDataModal.getTime()?.toLong() ?: 0L)
        batchFileData.time = cardProcessedDataModal.getTime() ?: ""
        batchFileData.date = cardProcessedDataModal.getDate() ?: ""
        batchFileData.mid = terminalData?.merchantId ?: ""
        batchFileData.posEntryValue = cardProcessedDataModal.getPosEntryMode() ?: ""
        batchFileData.batchNumber = invoiceWithPadding(terminalData?.batchNumber?: "")
        val roc = ROCProviderV2.getRoc(AppPreference.getBankCode()) - 1
        batchFileData.roc =
            roc.toString()//ROCProviderV2.getRoc(AppPreference.getBankCode()).toString()
        batchFileData.invoiceNumber = invoiceIncrementValue.toString()

        batchFileData.track2Data =
            if (transactionType != TransactionTypeValues.PRE_AUTH_COMPLETE) {
                cardProcessedDataModal.getTrack2Data() ?: ""
            } else {
                ""//isoPackageReader.field57 (Need to Check by Ajay)
            }

        batchFileData.terminalSerialNumber = AppPreference.getString("serialNumber")
        batchFileData.bankCode = AppPreference.getBankCode()
        batchFileData.customerId = issuerParameterTable?.customerIdentifierFiledType ?: ""
        batchFileData.walletIssuerId = AppPreference.WALLET_ISSUER_ID
        batchFileData.connectionType = ConnectionType.GPRS.code
        batchFileData.modelName = AppPreference.getString("deviceModel")
        batchFileData.appName = VerifoneApp.appContext.getString(R.string.app_name)
        val buildDate: String =
            SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(BuildConfig.TIMESTAMP))
        batchFileData.appVersion = "${BuildConfig.VERSION_NAME}.$buildDate"
        batchFileData.pcNumber = AppPreference.getString(AppPreference.PC_NUMBER_KEY)
        //batchFileData.operationType = isoPackageWriter.operationType(Need to Discuss by Ajay)
        batchFileData.transationName =
            TransactionTypeValues.getTransactionStringType(transactionType)
        batchFileData.cardType = cardDataTable?.cardLabel ?: ""
        batchFileData.isPinverified = true
        //Saving card number in mask form because we don't save the pan number in Plain text.
        batchFileData.cardNumber =
            if (transactionType != TransactionType.PRE_AUTH_COMPLETE.type) {
                getMaskedPan(
                    TerminalParameterTable.selectFromSchemeTable(),
                    cardProcessedDataModal.getPanNumberData() ?: ""
                )
            } else {
                getMaskedPan(
                    TerminalParameterTable.selectFromSchemeTable(),
                    cardProcessedDataModal.getTrack2Data() ?: ""
                )
            }
        //batchFileData.detectedCardType=cardProcessedDataModal.getReadCardType()?:DetectCardType.CARD_ERROR_TYPE
        batchFileData.operationType =
            cardProcessedDataModal.getReadCardType()?.cardTypeName.toString()
        //batchFileData.expiry = isoPackageWriter.expiryDate (Need to Discuss by Ajay)
        if (AppPreference.getBankCode() == "07")
            batchFileData.cardHolderName = cardProcessedDataModal.getCardHolderName() ?: "Amex"
        else
            batchFileData.cardHolderName = VerifoneApp.appContext.getString(R.string.hdfc)
        //batchFileData.indicator = isoPackageWriter.indicator (Need to Discuss by Ajay)
        batchFileData.field55Data = cardProcessedDataModal.getFiled55() ?: ""

        batchFileData.baseAmmount =
            MoneyUtil.fen2yuan(cardProcessedDataModal.getTransactionAmount()?.toLong() ?: 0L)
                .toString()
        val cashBackAmount = 0L
        if (cashBackAmount.toString().isNotEmpty() && cashBackAmount.toString() != "0") {
            batchFileData.cashBackAmount =
                MoneyUtil.fen2yuan(cashBackAmount).toString()
            if (transactionType != TransactionTypeValues.CASH_AT_POS)
                batchFileData.totalAmmount = MoneyUtil.fen2yuan(
                    cardProcessedDataModal.getTransactionAmount()?.toLong()
                        ?: 0L + cashBackAmount
                ).toString()
            else
                batchFileData.totalAmmount =
                    MoneyUtil.fen2yuan(
                        cardProcessedDataModal.getTransactionAmount()?.toLong() ?: 0L
                    )
                        .toString()
        } else
            batchFileData.totalAmmount =
                MoneyUtil.fen2yuan(cardProcessedDataModal.getTransactionAmount()?.toLong() ?: 0L)
                    .toString()

        /*batchFileData.referenceNumber = isoPackageReader.retrievalReferenceNumber
        //batchFileData.responseCode = isoPackageReader.reasionCode ?: "" (Need to Discuss by Ajay)  */

        batchFileData.authCode = cardProcessedDataModal.getAuthCode() ?: ""
        batchFileData.invoiceNumber = invoiceIncrementValue.toString()
        batchFileData.tid = terminalData?.terminalId ?: ""
        batchFileData.discaimerMessage = issuerParameterTable?.volletIssuerDisclammer ?: ""
        batchFileData.isTimeOut = false

        batchFileData.f48IdentifierWithTS = ConnectionTimeStamps.getFormattedStamp()

        //Setting AID , TVR and TSI into BatchFileDataTable here:-

        when (cardProcessedDataModal.getReadCardType()) {

            DetectCardType.EMV_CARD_TYPE -> {
                batchFileData.tvr = printExtraData?.first ?: ""
                batchFileData.aid = printExtraData?.second ?: ""
                batchFileData.tsi = printExtraData?.third ?: ""
            }

            DetectCardType.CONTACT_LESS_CARD_TYPE -> {
                /*   val aidArray = arrayOf("0x9F06")
               val aidData = vfIEMV?.getAppTLVList(aidArray)*/
                var aidData = cardProcessedDataModal.getAID() ?: ""
                //println("Aid Data is ----> $aidData")
                //val formattedAid = aidData?.subSequence(6, aidData.length)
                batchFileData.aid = cardProcessedDataModal.getAID() ?: ""
            }
            else -> {
            }
        }

        batchFileData.isPinverified =
            cardProcessedDataModal.getIsOnline() == 1 || cardProcessedDataModal.getIsOnline() == 2



        batchFileData.referenceNumber =
            hexString2String(cardProcessedDataModal.getRetrievalReferenceNumber() ?: "")
        batchFileData.tc = cardProcessedDataModal.getTC() ?: ""
        //  batchFileData.track2Data = cardProcessedDataModal.getTrack2Data() ?: "0000000"

        batchFileData.authBatchNO = cardProcessedDataModal.getAuthBatch().toString()
        batchFileData.authROC = cardProcessedDataModal.getAuthRoc().toString()
        batchFileData.authTID = cardProcessedDataModal.getAuthTid().toString()
        batchFileData.encryptPan = cardProcessedDataModal.getEncryptedPan() ?: ""
        batchFileData.amountInResponse = cardProcessedDataModal.getAmountInResponse().toString()
        batchFileData.aqrRefNo = cardProcessedDataModal.getAcqReferalNumber().toString()


        val cardIndFirst = "0"
        val firstTwoDigitFoCard = cardProcessedDataModal.getPanNumberData()?.substring(0, 2)
        //  val cardDataTable = CardDataTable.selectFromCardDataTable(cardProcessedData.getTrack2Data()!!)
        val cdtIndex = cardDataTable?.cardTableIndex ?: ""
        val accSellection =
            addPad(
                AppPreference.getString(AppPreference.ACC_SEL_KEY),
                "0",
                2
            ) //cardDataTable.getA//"00"

        val mIndicator =
            "$cardIndFirst|$firstTwoDigitFoCard|$cdtIndex|$accSellection"//used for visa// used for ruppay//"0|54|2|00"
        batchFileData.indicator = mIndicator

        var innvoice = terminalData?.invoiceNumber?.toInt()
        if (innvoice != null) {
            innvoice += 1
        }

        batchFileData.invoiceNumber = terminalData?.invoiceNumber.toString()

        terminalData?.invoiceNumber = innvoice?.let { addPad(it, "0", 6, true) }.toString()
        TerminalParameterTable.performOperation(terminalData!!) {
            logger("Invoice", terminalData.invoiceNumber + "  update")
        }

        //Here we are putting Refund Transaction Status in Batch Table:-
        if (cardProcessedDataModal.getProcessingCode() == ProcessingCode.REFUND.code) {
            batchFileData.isRefundSale = true
        }

        val calender = Calendar.getInstance()
        val currentYearData = calender.get(Calendar.YEAR)
        batchFileData.currentYear = currentYearData.toString().substring(2, 4)

        //Mobile Number and Bill Number Save in BatchTable here:-
        batchFileData.merchantMobileNumber =
            cardProcessedDataModal.getMobileBillExtraData()?.first ?: ""
        batchFileData.merchantBillNumber =
            cardProcessedDataModal.getMobileBillExtraData()?.second ?: ""

        if(batchFileData.transactionType!=TransactionType.PRE_AUTH.type) {
            val lastSuccessReceiptData = Gson().toJson(batchFileData)
            AppPreference.saveString(AppPreference.LAST_SUCCESS_RECEIPT_KEY, lastSuccessReceiptData)
        }


        return batchFileData
    }
}
// Here We are stubbing emi data into batch record and save it in BatchFile.
fun stubEMI(
    batchData: BatchFileDataTable,
    emiCustomerDetails: EmiCustomerDetails?,
    batchStubCallback: (BatchFileDataTable) -> Unit
) {
    //For emi find the details from EMI

    batchData.accountType = emiCustomerDetails?.accountType.toString()
    batchData.customerName = emiCustomerDetails?.customerName.toString()
    batchData.email = emiCustomerDetails?.email.toString()
    batchData.merchantBillNo = emiCustomerDetails?.merchantBillNo.toString()
    batchData.phoneNo = emiCustomerDetails?.phoneNo.toString()
    batchData.serialNo = emiCustomerDetails?.serialNo.toString()
    batchData.tenure = emiCustomerDetails?.tenure.toString()
    batchData.emiBin = emiCustomerDetails?.emiBin.toString()
    batchData.issuerId = emiCustomerDetails?.issuerId.toString()
    batchData.emiSchemeId = emiCustomerDetails?.emiSchemeId.toString()
    batchData.transactionAmt = emiCustomerDetails?.transactionAmt.toString()
    batchData.cashDiscountAmt = emiCustomerDetails?.cashDiscountAmt.toString()
    batchData.loanAmt = emiCustomerDetails?.loanAmt.toString()
    batchData.tenure = emiCustomerDetails?.tenure.toString()
    batchData.roi = emiCustomerDetails?.roi.toString()
    batchData.monthlyEmi = emiCustomerDetails?.monthlyEmi.toString()
    batchData.cashback = emiCustomerDetails?.cashback.toString()
    batchData.netPay = emiCustomerDetails?.netPay.toString()
    batchData.processingFee = emiCustomerDetails?.processingFee.toString()
    batchData.totalInterest = emiCustomerDetails?.totalInterest.toString()
    batchData.cashBackPercent= emiCustomerDetails?.cashBackPercent.toString()
    if (emiCustomerDetails != null) {
        batchData.isCashBackInPercent=emiCustomerDetails.isCashBackInPercent
    }
    //MI BrandDetail
    batchData.brandId = emiCustomerDetails?.brandId.toString()//"01"
    batchData.productId = emiCustomerDetails?.productId.toString()//"0"

    //println("EMI tenure " + emiCustomerDetails?.tenure)
    val lastSuccessReceiptData = Gson().toJson(batchData)
    AppPreference.saveString(AppPreference.LAST_SUCCESS_RECEIPT_KEY, lastSuccessReceiptData)

    batchStubCallback(batchData)

}