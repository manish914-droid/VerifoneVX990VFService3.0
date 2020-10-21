package com.example.verifonevx990app.disputetransaction

import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.main.PrefConstant
import com.example.verifonevx990app.realmtables.BatchFileDataTable
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.vxUtils.*

class CreateSettlementPacket(private var settlementProcessingCode: String? = null
                             , private var batchList: MutableList<BatchFileDataTable>) :
    ISettlementPacketExchange {

    override fun createSettlementISOPacket(): IWriter = IsoDataWriter().apply {
        val tpt = TerminalParameterTable.selectFromSchemeTable()
        if (tpt != null) {
            mti = Mti.SETTLEMENT_MTI.mti

            //Processing Code:-
            addField(3, settlementProcessingCode ?: ProcessingCode.SETTLEMENT.code)

            //adding stan (padding of stan is internally handled by iso)
            var settlementRoc = 0
            var batchNumber = 0
            if (AppPreference.getIntData(PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString()) == 0) {
                settlementRoc = if (batchList.size > 0)
                    (batchList[batchList.size - 1].roc).toInt()
                else
                    ROCProviderV2.getRoc(AppPreference.getBankCode()).toInt()
                AppPreference.setIntData(
                    PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString(),
                    settlementRoc
                )

            } else {
                settlementRoc =
                    AppPreference.getIntData(PrefConstant.SETTLEMENT_ROC_INCREMENT.keyName.toString())
            }

            if (AppPreference.getIntData(PrefConstant.SETTLEMENT_BATCH_INCREMENT.keyName.toString()) == 0) {
                batchNumber = if (batchList.size > 0)
                    (batchList[0].batchNumber).toInt()
                else
                    tpt.batchNumber.toInt()
                AppPreference.setIntData(
                    PrefConstant.SETTLEMENT_BATCH_INCREMENT.keyName.toString(), batchNumber
                )
            } else {
                //Incremented Batch Number from Previous Batch:-
                batchNumber =
                    AppPreference.getIntData(PrefConstant.SETTLEMENT_BATCH_INCREMENT.keyName.toString())
            }

            //ROC will not go in case of AMEX on all PORT:-
            //addField(11, settlementRoc.toString())

            //adding nii
            addField(24, Nii.DEFAULT.nii)

            //adding tid
            addFieldByHex(41, tpt.terminalId)

            //adding mid
            addFieldByHex(42, tpt.merchantId)

            //adding field 48
            addFieldByHex(48, ConnectionTimeStamps.getStamp())

            //Batch Number
            addFieldByHex(60, addPad(batchNumber, "0", 6, true))

            //adding field 61
            addFieldByHex(
                61,
                addPad(
                    AppPreference.getString("serialNumber"),
                    " ",
                    15,
                    false
                ) + AppPreference.getBankCode()
            )

            val version =
                addPad("${BuildConfig.VERSION_NAME}.${BuildConfig.REVISION_ID}", "0", 15, false)

            //adding field 62
            addFieldByHex(
                62, ConnectionType.GPRS.code + addPad(
                    AppPreference.getString("deviceModel"), " ", 6, false
                ) +
                        addPad(
                            VerifoneApp.appContext.getString(R.string.app_name),
                            " ",
                            10,
                            false
                        ) +
                        version + addPad("0", "0", 9)
            )

            //adding field 63
            var saleCount = 0
            var saleAmount = "0"
            var refundCount = 0
            var refundAmount = "0"

            //Manipulating Data based on condition for Field 63:-
            if (batchList.size > 0) {
                for (i in 0 until batchList.size) {
                    if (batchList[i].getTransactionType() == TransactionType.SALE.name) {
                        saleCount = saleCount.plus(1)
                        saleAmount = saleAmount.plus(batchList[i].transactionalAmmount)
                    } else if (batchList[i].getTransactionType() == TransactionType.REFUND.name) {
                        refundCount = refundCount.plus(1)
                        refundAmount = refundAmount.plus(batchList[i].transactionalAmmount)
                    }
                }
                val sCount = addPad(saleCount, "0", 3, true)
                val sAmount = addPad(saleAmount, "0", 12, true)
                val rCount = addPad(refundCount, "0", 3, true)
                val rAmount = addPad(refundAmount, "0", 12, true)
                addFieldByHex(
                    63,
                    addPad(sCount + sAmount + rCount + rAmount, "0", 90, toLeft = false)
                )
            } else {
                addFieldByHex(63, addPad(0, "0", 90, toLeft = false))
            }
        }
    }
}