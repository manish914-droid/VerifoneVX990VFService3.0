package com.example.verifonevx990app.nontransaction

import android.text.TextUtils
import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.emv.transactionprocess.CardProcessedDataModal
import com.example.verifonevx990app.main.DetectCardType
import com.example.verifonevx990app.realmtables.CardDataTable
import com.example.verifonevx990app.realmtables.IssuerParameterTable
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.transactions.EmiCustomerDetails
import com.example.verifonevx990app.utils.HexStringConverter
import com.example.verifonevx990app.vxUtils.*
import java.text.SimpleDateFormat
import java.util.*

class CreateEMIEnquiryTransactionPacket(var data: HashMap<String, String>) : ITransactionPacketExchange {


    //Below method is used to create Transaction Packet in all cases:-
    init {
        createTransactionPacket()
    }

    override fun createTransactionPacket(): IsoDataWriter = IsoDataWriter().apply {
        val tpt = TerminalParameterTable.selectFromSchemeTable()
        val issuer = data["issuer"] ?: ""
        val ipt = IssuerParameterTable.selectFromIssuerParameterTable(issuer)
        if (tpt != null && ipt != null) {
            val idw = IsoDataWriter()

            mti = Mti.PRE_AUTH_MTI.mti

            addField(3, ProcessingCode.EMI_ENQUIRY.code)
            addField(4, data["amount"] ?: "")

            // adding ROC (11) time(12) and date(13)
            addField(11, ROCProviderV2.getRoc(AppPreference.getBankCode()).toString())
            addIsoDateTime(this)

            addField(24, Nii.DEFAULT.nii)

            addFieldByHex(41, tpt.terminalId)
            addFieldByHex(42, tpt.merchantId)

            addFieldByHex(48, ConnectionTimeStamps.getStamp())

            val f58 = "${data["mobile"]}|$issuer|"
            addFieldByHex(58, f58)

            addFieldByHex(60, addPad(tpt.batchNumber, "0", 6))



            //adding field 61
            val buildDate: String = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(BuildConfig.TIMESTAMP))
            val issuerParameterTable = IssuerParameterTable.selectFromIssuerParameterTable(AppPreference.WALLET_ISSUER_ID)
            val version = addPad("${BuildConfig.VERSION_NAME}.$buildDate", "0", 15, false)
            val pcNumber = addPad(AppPreference.getString(AppPreference.PC_NUMBER_KEY), "0", 9)
            val data = ConnectionType.GPRS.code + addPad(
                AppPreference.getString("deviceModel"),
                " ",
                6,
                false
            ) +
                    addPad(VerifoneApp.appContext.getString(R.string.app_name), " ", 10, false) +
                    version + addPad("0", "0", 9) + pcNumber
            val customerID = HexStringConverter.addPreFixer(
                issuerParameterTable?.customerIdentifierFiledType,
                2
            )

            val walletIssuerID = HexStringConverter.addPreFixer(issuerParameterTable?.issuerId, 2)
            addFieldByHex(
                61, addPad(
                    AppPreference.getString("serialNumber"), " ", 15, false
                ) + AppPreference.getBankCode() + customerID + walletIssuerID + data
            )

            //adding field 62
            addFieldByHex(62, tpt.invoiceNumber)

            }
        }

}