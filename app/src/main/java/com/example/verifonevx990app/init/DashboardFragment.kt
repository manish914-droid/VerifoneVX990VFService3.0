package com.example.verifonevx990app.init


import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.verifonevx990app.BuildConfig
import com.example.verifonevx990app.R
import com.example.verifonevx990app.appupdate.SendAppUpdateConfirmationPacket
import com.example.verifonevx990app.appupdate.SyncAppUpdateConfirmation
import com.example.verifonevx990app.main.IFragmentRequest
import com.example.verifonevx990app.main.MainActivity
import com.example.verifonevx990app.realmtables.BHDashboardItem
import com.example.verifonevx990app.realmtables.EDashboardItem
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.vxUtils.*
import com.example.verifonevx990app.vxUtils.ROCProviderV2.refreshToolbarLogos
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.fragment_dashboard.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class DashboardFragment : Fragment() {

    companion object{
        var toRefresh = true
    }

    private var iFragmentRequest: IFragmentRequest?= null
    private val itemList = mutableListOf<EDashboardItem>()
    private val mAdapter by lazy { DashboardAdapter(itemList,iFragmentRequest) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false).apply { initUI(this) }
    }


    private fun initUI(v:View) {
        val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.ma_bnv)
        if (bottomNavigationView is BottomNavigationView) {
            bottomNavigationView.visibility = View.VISIBLE
            bottomNavigationView.menu.findItem(R.id.home)?.isChecked = true
        }

        //Below method is only called once after App is Updated to newer version:-
        sendConfirmationToHost()

        activity?.let { hideSoftKeyboard(it) }
        activity?.let { refreshToolbarLogos(it) }
        if (toRefresh || itemList.isEmpty()) {
            GlobalScope.launch {
                itemList.clear()
                val tpt = TerminalParameterTable.selectFromSchemeTable()
                if (tpt != null) {
                    val tableClass =
                        tpt::class.java //Class Name (class com.bonushub.pax.utilss.TerminalParameterTable)
                    for (e in tableClass.declaredFields) {
                        val ann = e.getAnnotation(BHDashboardItem::class.java)
                    //If table's field  having the particular annotation as @BHDasboardItem then it returns the value ,If not then return null
                        if (ann != null) {
                            e.isAccessible = true
                            val t = e.get(tpt) as String
                            if (t == "1") {
                                itemList.add(ann.item)
                                if (ann.childItem != EDashboardItem.NONE) {
                                    itemList.add(ann.childItem)
                                }
                            }
                        }
                    }
                } else {
                    itemList.add(EDashboardItem.NONE)
                }

                itemList.sortWith(compareBy{it.rank})

                launch(Dispatchers.Main) {
                    v.dash_frag_rv.apply {
                        layoutManager = GridLayoutManager(context, 2)
                        adapter = mAdapter
                    }
                    mAdapter.notifyDataSetChanged()
                }
            }
            toRefresh = false
        }else{
            v.dash_frag_rv.apply {
                layoutManager = GridLayoutManager(context, 2)
                adapter = mAdapter
            }
            mAdapter.notifyDataSetChanged()
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is IFragmentRequest){
            iFragmentRequest = context
        }
        if (context is IDialog) {
            context.onEvents(VxEvent.ChangeTitle(getString(R.string.app_name)))
        }
    }

    override fun onDetach() {
        super.onDetach()
        iFragmentRequest = null
    }

    //Below method only executed when the app is updated to newer version and there is mismatch
    // in previous store version in file and new app version:-
    private fun sendConfirmationToHost() {
        try {
            context?.let {
                readAppVersionNameFromFile(it) { fileStoredAppVersionName ->
                    if (!TextUtils.isEmpty(fileStoredAppVersionName)) {
                        if (fileStoredAppVersionName < BuildConfig.VERSION_NAME) {
                            sendConfirmation()
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    //Sync App Confirmation to Host:-
    private fun sendConfirmation() {
        val appUpdateConfirmationISOData =
            SendAppUpdateConfirmationPacket().createAppUpdateConfirmationPacket()
        val isoByteArray = appUpdateConfirmationISOData.generateIsoByteRequest()
        (activity as MainActivity).showProgress(getString(R.string.please_wait))
        SyncAppUpdateConfirmation(isoByteArray) { syncStatus ->
            GlobalScope.launch(Dispatchers.Main) {
                (activity as MainActivity).hideProgress()
                if (syncStatus) {
                    (activity as MainActivity).alertBoxWithAction(null,
                        null,
                        getString(R.string.app_update_confirmation),
                        getString(R.string.app_updated_successfully),
                        false,
                        getString(R.string.positive_button_ok),
                        {
                            context?.let { it1 ->
                                writeAppVersionNameInFile(it1)
                            }
                        },
                        {})
                } else {
                    (activity as MainActivity).alertBoxWithAction(null,
                        null,
                        getString(R.string.app_update_confirmation),
                        getString(R.string.click_on_ok_button_to_confirm_app_update),
                        true,
                        getString(R.string.yes),
                        { sendConfirmation() },
                        {})
                }
            }
        }
    }
}


class DashboardAdapter(var mList: List<EDashboardItem>, private val fragReq: IFragmentRequest?) : RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder>() {

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): DashboardViewHolder {
        val v = LayoutInflater.from(p0.context).inflate(R.layout.item_dashboard, p0, false)
        return DashboardViewHolder(v)
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    override fun onBindViewHolder(p0: DashboardViewHolder, p1: Int) {
        p0.logoIV.background = ContextCompat.getDrawable(p0.vw.context, mList[p1].res)
        p0.titleTV.text = mList[p1].title
    }

   inner class DashboardViewHolder(val vw: View) : RecyclerView.ViewHolder(vw) {

        val logoIV: ImageView = vw.findViewById(R.id.item_logo_iv)
        val titleTV: TextView = vw.findViewById(R.id.item_title_tv)

        init {
            vw.findViewById<View>(R.id.item_parent_rv).setOnClickListener { fragReq?.onTransactionRequest(mList[adapterPosition]) }
        }

    }

}

