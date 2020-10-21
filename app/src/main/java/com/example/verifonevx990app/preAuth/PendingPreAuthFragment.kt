package com.example.verifonevx990app.preAuth

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.verifonevx990app.R
import com.example.verifonevx990app.emv.transactionprocess.CardProcessedDataModal
import com.example.verifonevx990app.utils.printerUtils.PrintUtil
import com.example.verifonevx990app.vxUtils.invoiceWithPadding
import kotlinx.android.synthetic.main.fragment_pending_pre_auth.*
import kotlinx.android.synthetic.main.item_pending_preauth.view.*
import kotlinx.android.synthetic.main.sub_header_layout.*

class PendingPreAuthFragment : Fragment() {

    val preAuthDataList by lazy {
        arguments?.getSerializable("PreAuthData") as ArrayList<PendingPreauthData>
    }

    val cardProcessData by lazy {
        arguments?.getSerializable("CardProcessData") as CardProcessedDataModal
    }

    //creating our adapter
    val mAdapter by lazy {
        PendingPreauthAdapter(preAuthDataList)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pending_pre_auth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        back_image_button?.setOnClickListener {
            fragmentManager?.popBackStackImmediate()
        }
        sub_header_text?.text = getString(R.string.pending_pre_auth)

        pending_pre_auth_print_btn.setOnClickListener {
            PrintUtil(context).printPendingPreauth(
                cardProcessData,
                context,
                preAuthDataList
            ) { printCB ->
                if (!printCB) {
                    //Here we are Syncing Offline Sale if we have any in Batch Table and also Check Sale Response has Auto Settlement enabled or not:-
                    //If Auto Settlement Enabled Show Pop Up and User has choice whether he/she wants to settle or not:-
                    /*   if (!TextUtils.isEmpty(autoSettlementCheck))
                           syncOfflineSaleAndAskAutoSettlement(
                               autoSettlementCheck.substring(
                                   0,
                                   1
                               ), context as BaseActivity
                           )*/
                }

            }


        }

        pending_pre_rv.layoutManager = LinearLayoutManager(activity)

        //now adding the adapter to recyclerview
        pending_pre_rv.adapter = mAdapter
    }

}

class PendingPreauthAdapter(val pendPreauthData: ArrayList<PendingPreauthData>) :
    RecyclerView.Adapter<PendingPreauthAdapter.PendingPreAuthViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingPreAuthViewHolder {
        return PendingPreAuthViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_preauth, parent, false)
        )
    }

    override fun getItemCount(): Int = pendPreauthData.size

    override fun onBindViewHolder(holder: PendingPreAuthViewHolder, position: Int) {

        val batchNo = "BATCH NO : " + invoiceWithPadding(pendPreauthData[position].batch.toString())
        holder.view.batch_no_tv.text = batchNo
        val roc = "ROC : " + invoiceWithPadding(pendPreauthData[position].roc.toString())
        holder.view.roc_tv.text = roc

        val pan = "PAN : " + pendPreauthData[position].pan
        holder.view.pan_no_tv.text = pan
        val amt = "AMT : " + "%.2f".format(pendPreauthData[position].amount)
        holder.view.amt_tv.text = amt

        val date = "DATE : " + pendPreauthData[position].date
        holder.view.date_tv.text = date
        val time = "TIME : " + pendPreauthData[position].time
        holder.view.time_tv.text = time

    }

    class PendingPreAuthViewHolder(val view: View) : RecyclerView.ViewHolder(view)
}