package com.example.andapp1

import android.content.Context
import android.content.Intent
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
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

                if (scrapList.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("📌 스크랩 목록")
                        .setMessage("스크랩된 장소가 없습니다.")
                        .setPositiveButton("확인", null)
                        .show()
                    return
                }

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    scrapList.map { it.name })

                AlertDialog.Builder(context)
                    .setTitle("📌 스크랩 목록")
                    .setAdapter(adapter) { _, which ->
                        val url = scrapList[which].url
                        openWebView(context, url, roomCode)
                    }
                    .setNegativeButton("닫기", null)
                    .show()
            }

            override fun onCancelled(error: DatabaseError) {
                AlertDialog.Builder(context)
                    .setTitle("오류 발생")
                    .setMessage("스크랩 목록을 불러오는 중 오류가 발생했습니다.")
                    .setPositiveButton("확인", null)
                    .show()
            }
        })
    }

    private fun openWebView(context: Context, url: String, roomCode: String) {
        val intent = Intent(context, MapActivity::class.java).apply {
            putExtra("mapUrl", url)
            putExtra("roomCode", roomCode)
        }
        context.startActivity(intent)
    }
}