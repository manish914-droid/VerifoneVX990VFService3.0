package com.example.verifonevx990app.transactions

import android.os.RemoteException
import android.util.Log
import com.example.verifonevx990app.utils.DRLUtil
import com.example.verifonevx990app.vxUtils.AppPreference
import com.example.verifonevx990app.vxUtils.convertStr2Nibble2Str
import com.vfi.smartpos.deviceservice.aidl.IEMV
import com.vfi.smartpos.deviceservice.constdefine.ConstIPBOC


class EmvSetAidRid(private var ipboc: IEMV? , private var updateCVMValue : String , private var ctlsUpdateTransLimit : String) {
    private val TAG = "EMV-SetAidRid"
    private var ctlsVal: String? = null
    private var cvmVal: String? = null

    init {
        Log.d("CTLS:- ", ctlsUpdateTransLimit)
        ctlsVal = convertStr2Nibble2Str(ctlsUpdateTransLimit)
        cvmVal = convertStr2Nibble2Str(updateCVMValue)
    }

    /**
     * @brief set, update the AID
     *
     * In this demo, there're 2 way to set the AID
     * 1#, set each tag & value
     * 2#, set one tlv string
     * in the EMVParamAppCaseA, you can reset the tag or value in EMVParamAppCaseA.append
     * \code{.java}
     * \endcode
     * @version
     * @see EMVParamAppCaseA
     */
    fun setAID(type: Int) {
        var isSuccess: Boolean
        if (type == ConstIPBOC.updateRID.operation.clear) {
            // clear all AID
            isSuccess = false
            try {
                isSuccess = ipboc!!.updateAID(3, 1, null)
                Log.d("TAG", "Clear AID (smart AID):$isSuccess")
                isSuccess = ipboc!!.updateAID(3, 2, null)
                Log.d("TAG", "Clear AID (CTLS):$isSuccess")
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            return
        }


        try {
            val drlDataList = DRLUtil(updateCVMValue, ctlsUpdateTransLimit).amexdrl
            for (drlData in drlDataList) {
                isSuccess = ipboc!!.updateVisaAPID(2,drlData)
                Log.d("TAG", "Clear UpdateVisAPIDAID:$isSuccess")
            }
            for (drlData in drlDataList) {
                isSuccess = ipboc!!.updateVisaAPID(1,drlData )
                Log.d("TAG", "Add UpdateVisAPIDAID:$isSuccess")
            }
        }
        catch (ex : Exception){
            ex.printStackTrace()
        }

        // 2# way of setting the AID
        // given the AID string to set. You can change the EMVParamAppCaseA to check each Tag & Value, and modify the tag or value if need
        // hardcoding the AID string
        val AID_SmartCard =
            arrayOf(
                "9F0606F00000002501" +
                        "DF2006009999999999DF010100DF14039F3704DF1701009F09020001DF180100DF1205CC000000009F1B0400000000DF160101DF150400000000DF1105CC00000000DF19060000000000009F7B06000000100000DF130500000000009F1A020356",
                "9F0606A00000002501" +
                        "DF2006009999999999DF010100DF14039F3704DF1701999F09020001DF180101DF1205CC000080009F1B0400000000DF160199DF150400000000DF1105CC00000000DF19060000000000009F7B06000010000000DF130500000000009F1A020356",
                "9F0607A0000003241010" +
                        "DF2006009999999999DF010100DF14039F3704DF1701999F09020001DF180101DF1205FCE09CF8009F1B0400000000DF160199DF150400000000DF1105DC00002000DF19060000000000009F7B06000000000000DF130500100000009F1A020356",
                "9F0605A000000000" +
                        "DF2006009999999999DF010100DF140111DF1701019F09020001DF180100DF120500000000009F1B0400000064DF160101DF150400000001DF11050000000000DF19060000000000009F7B06000000000100DF130500000000009F1A020356"
            )
        val AID_CTLS_Card = arrayOf(


            //American express Aid's
            "9F0608A000000333010106" +
                    "DF0306009999999999DF2006009999999999DF010100DF14039F37049F6601265F2A020356DF1701999F09020020DF180101DF1205DC4004F8009F1B04000000649F1A020356DF2106000000100000DF160199DF150400000000DF1105DC4000A800DF0406000000000000DF1906000000000000DF13050010000000",  // American Express
            "9F0606F00000002501" +
                    "DF0306009999999999DF2006009999999999DF010100DF14039F37049F6601225F2A020356DF1701009F09020001DF180100DF1205CC000000009F1B04000000009F1A020356DF2106000000100000DF160101DF150400000000DF1105CC00000000DF0406000000000501DF1906000000000000DF13050000000000",
            "9F0606A00000002501" +     // AID
                    "DF0306009999999999" +
                    "DF2006${ctlsVal}" + //Contact less Maximum Transaction Limit
                    "DF010100" +     //Application id
                    "DF14039F3704" + //DDol //Dynamic Data
                    "9F6604A6004000" + ///terminal transaction attribute 86004000  // "9F660426000080 //TTQ
                    "5F2A020356" +   //  Transaction currency code
                    "DF170199" +    //The target percentage randomly choosen
                    "9F09020001" +  // Application version
                    "DF180101" +    //Online pin
                    "DF1205C400000000" +  //TAC Online
                    "9F1B0400000000" +    //Minimum Limit
                    "9F1A020356" +        //   Terminal Country code
                    "DF2106${cvmVal}" +  //Terminal cvm(cardholder verification methods) quota
                    "DF160199" +              //Bias select the maximum percentage of target
                    "DF150400000000" +       //offset Randomly selected thresold
                    "DF11050000000000" +     //TAC Default
                    "DF0406000000000000" +   //
                    "DF1906000000000000" +   //Contact less offline minimum
                    "DF13050000000000"    //TAC Refuse
/*
            "9F0607A0000003241010" +
                    "DF0306009999999999DF2006009999999999DF010100DF14039F37049F6601265F2A020356DF1701999F09020001DF180101DF1205FCE09CF8009F1B04000000009F1A020356DF2106000000000000DF160199DF150400000000DF1105DC00002000DF0406000000000000DF1906000000000000DF13050010000000",
            "DF1A06000000012000" +
                    "9F0607A00000000" +
                    "32010DF0306009999999999DF2006009999999999DF010100DF14039F37049F6601265F2A020356DF1B06000000000000DF1701019F09020140DF180101DF1205D84004F8009F1B04000000009F1A0203569F5A054001560156DF2106000000000000DF160101DF150400000000DF1105D84004A800DF0406000000000000DF1906000000000000DF13050010000000"*/
        )

        try {
            if (ipboc != null) {
                isSuccess = ipboc!!.updateAID(3, 1, null)
                Log.d("TAG", "Clear AID (smart AID):$isSuccess")
                isSuccess = ipboc!!.updateAID(3, 2, null)
                Log.d("TAG", "Clear AID (CTLS):$isSuccess")
          //      VFService.showToast("AID & RID Configured Successfully!!!")
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }


        val smartCardAidType = ConstIPBOC.updateAID.aidType.smart_card
        val ctlsAidType = ConstIPBOC.updateAID.aidType.contactless
        val smartCardDataList = AID_SmartCard.toMutableList()
        val ctlsCardDataList = AID_CTLS_Card.toMutableList()
        val dataList = mutableListOf<String>()
        var aidType  = smartCardAidType
        dataList.addAll(smartCardDataList)
        dataList.addAll(ctlsCardDataList)
        isSuccess = false

        //Below For Loop is to set All AID of AID_SmartCard && CTLS_SmartCard by changing aidType:-
        for (i in 0 until dataList.size) {
            if (dataList[i].isEmpty()) {
                continue
            }

            if(i == AID_SmartCard.size){
                aidType = ctlsAidType
            }

            try {
                val emvParamAppUMS = EMVParamAppCaseA(AppPreference.getBankCode())
                emvParamAppUMS.setFlagAppendRemoveClear(ConstIPBOC.updateAID.operation.append)
                emvParamAppUMS.setAidType(aidType)
                emvParamAppUMS.append(dataList[i])
                isSuccess = ipboc!!.updateAID(
                    emvParamAppUMS.getFlagAppendRemoveClear(),
                    emvParamAppUMS.getAidType(),
                    emvParamAppUMS.tlvString
                )
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            Log.d("TAG", "" + isSuccess)
            if (isSuccess) {
                Log.d("TAG", "update AID success")
            } else {
                Log.e("TAG", "updateAID false")
            }
        }
    }

    /**
     * @brief set, update the RID
     *
     * In this demo, there're 2 way to set the AID
     * 1#, set each tag & value
     * 2#, set one tlv string
     * in the EMVParamKeyCaseA.append, you can reset the Tag or Value
     * \code{.java}
     * \endcode
     * @version
     * @see EMVParamKeyCaseA
     */
    fun setRID(type: Int) {
        var isSuccess: Boolean
        if (type == 3) {
            // clear RID
            isSuccess = false
            try {
                isSuccess = ipboc!!.updateRID(3, null)
                Log.d("TAG", "Clear RID :$isSuccess")
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            return
        }

        // way 2#, set one tlv string
        // hardcoding some rid
        val ridList = arrayOf(
            //Amex Test Cap keys
         /*   "9F0605A0000000259F2201C8DF0503201231DF060101DF070101DF028190BF0CFCED708FB6B048E3014336EA24AA007D7967B8AA4E613D26D015C4FE7805D9DB131CED0D2A8ED504C3B5CCD48C33199E5A5BF644DA043B54DBF60276F05B1750FAB39098C7511D04BABC649482DDCF7CC42C8C435BAB8DD0EB1A620C31111D1AAAF9AF6571EEBD4CF5A08496D57E7ABDBB5180E0A42DA869AB95FB620EFF2641C3702AF3BE0B0C138EAEF202E21DDF040103DF031433BD7A059FAB094939B90A8F35845C9DC779BD50", //c8
            "9F0605A0000000259F2201C9DF0503201231DF060101DF070101DF0281B0B362DB5733C15B8797B8ECEE55CB1A371F760E0BEDD3715BB270424FD4EA26062C38C3F4AAA3732A83D36EA8E9602F6683EECC6BAFF63DD2D49014BDE4D6D603CD744206B05B4BAD0C64C63AB3976B5C8CAAF8539549F5921C0B700D5B0F83C4E7E946068BAAAB5463544DB18C63801118F2182EFCC8A1E85E53C2A7AE839A5C6A3CABE73762B70D170AB64AFC6CA482944902611FB0061E09A67ACB77E493D998A0CCF93D81A4F6C0DC6B7DF22E62DBDF040103DF03148E8DFF443D78CD91DE88821D70C98F0638E51E49", //c9
            "9F0605A0000000259F2201CADF0503201231DF060101DF070101DF0281F8C23ECBD7119F479C2EE546C123A585D697A7D10B55C2D28BEF0D299C01DC65420A03FE5227ECDECB8025FBC86EEBC1935298C1753AB849936749719591758C315FA150400789BB14FADD6EAE2AD617DA38163199D1BAD5D3F8F6A7A20AEF420ADFE2404D30B219359C6A4952565CCCA6F11EC5BE564B49B0EA5BF5B3DC8C5C6401208D0029C3957A8C5922CBDE39D3A564C6DEBB6BD2AEF91FC27BB3D3892BEB9646DCE2E1EF8581EFFA712158AAEC541C0BBB4B3E279D7DA54E45A0ACC3570E712C9F7CDF985CFAFD382AE13A3B214A9E8E1E71AB1EA707895112ABC3A97D0FCB0AE2EE5C85492B6CFD54885CDD6337E895CC70FB3255E3DF040103DF03146BDA32B1AA171444C7E8F88075A74FBFE845765F", //CA,*/

            //Amex Live cap Keys
            "9F0605A000000025" + //Aid
                    "9F22010F" + //Key Id
                    "DF0503221231" + //Expiry Date
                    "DF060101" + // Hash Ind
                    "DF070101" + // ARITH_IND
                    "DF0281B0" + "C8D5AC27A5E1FB89978C7C6479AF993AB3800EB243996FBB2AE26B67B23AC482C4B746005A51AFA7D2D83E894F591A2357B30F85B85627FF15DA12290F70F05766552BA11AD34B7109FA49DE29DCB0109670875A17EA95549E92347B948AA1F045756DE56B707E3863E59A6CBE99C1272EF65FB66CBB4CFF070F36029DD76218B21242645B51CA752AF37E70BE1A84FF31079DC0048E928883EC4FADD497A719385C2BBBEBC5A66AA5E5655D18034EC5" + //Module
                    "DF040103" + //exponent
                    "DF0314A73472B3AB557493A9BC2179CC8014053B12BAB4",  //CheckSum

            //Amex Live cap Keys
            "9F0605A000000025" + //Aid
                    "9F220110" + //Key Id
                    "DF0503251231" + //Expiry Datef
                    "DF060101" + // Hash Ind
                    "DF070101" + // ARITH_IND
                    "DF0281F8" + "CF98DFEDB3D3727965EE7797723355E0751C81D2D3DF4D18EBAB9FB9D49F38C8C4A826B99DC9DEA3F01043D4BF22AC3550E2962A59639B1332156422F788B9C16D40135EFD1BA94147750575E636B6EBC618734C91C1D1BF3EDC2A46A43901668E0FFC136774080E888044F6A1E65DC9AAA8928DACBEB0DB55EA3514686C6A732CEF55EE27CF877F110652694A0E3484C855D882AE191674E25C296205BBB599455176FDD7BBC549F27BA5FE35336F7E29E68D783973199436633C67EE5A680F05160ED12D1665EC83D1997F10FD05BBDBF9433E8F797AEE3E9F02A34228ACE927ABE62B8B9281AD08D3DF5C7379685045D7BA5FCDE58637" + //Module
                    "DF040103" + //exponent
                    "DF0314C729CF2FD262394ABC4CC173506502446AA9B9FD"  //CheckSum
        )
        try {
            isSuccess = ipboc!!.updateRID(3, null)
            Log.d("TAG", "Clear RID :$isSuccess")
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        for (rid in ridList) {
            try {
                if (rid.isEmpty()) {
                    continue
                }
                val emvParamKeyUMS = EMVParamKeyCaseA()
                emvParamKeyUMS.append(rid)
                val bRet =
                    ipboc!!.updateRID(ConstIPBOC.updateRID.operation.append, rid)
                if (bRet) {
                    Log.d(TAG, "update RID success ")
                } else {
                    Log.e(TAG, "update RID fails ")
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "update RID exception!")
                e.printStackTrace()
            }
        }
    }
}