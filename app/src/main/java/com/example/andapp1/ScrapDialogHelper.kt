package com.example.andapp1

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.*

object ScrapDialogHelper {

    fun showScrapListDialog(context: Context, roomCode: String) {
        val scrapsRef = FirebaseDatabase.getInstance()
            .getReference("scraps")
            .child(roomCode)

        scrapsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val scrapList = mutableListOf<ScrapItem>()

                for (child in snapshot.children) {
                    val item = child.getValue(ScrapItem::class.java)
                    if (item != null) {
                        scrapList.add(item)
                    }
                }

                showScrapDialog(context, scrapList, roomCode)
            }

            override fun onCancelled(error: DatabaseError) {
                DialogHelper.showStyledConfirmDialog(
                    context = context,
                    title = "오류 발생",
                    message = "스크랩 목록을 불러오는 중 오류가 발생했습니다.",
                    positiveText = "확인"
                )
            }
        })
    }

    private fun showScrapDialog(context: Context, scrapList: List<ScrapItem>, roomCode: String) {
        if (scrapList.isEmpty()) {
            DialogHelper.showStyledConfirmDialog(
                context = context,
                title = "스크랩 목록",
                message = "스크랩된 장소가 없습니다.\n지도에서 장소를 스크랩해보세요.",
                positiveText = "확인"
            )
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_scrap_list, null)
        
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.btn_close)
        
        titleView.text = "스크랩 목록"
        
        val adapter = ScrapAdapter(scrapList) { scrapItem ->
            openWebView(context, scrapItem.url, roomCode)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        val dialog = AlertDialog.Builder(context, R.style.AppDialog)
            .setView(dialogView)
            .create()
            
        closeButton.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    private fun openWebView(context: Context, url: String, roomCode: String) {
        val intent = Intent(context, MapActivity::class.java).apply {
            putExtra("mapUrl", url)
            putExtra("roomCode", roomCode)
        }
        context.startActivity(intent)
    }
}