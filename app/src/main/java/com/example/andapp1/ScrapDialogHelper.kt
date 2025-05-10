package com.example.andapp1

import android.content.Context
import android.content.Intent
import android.widget.ArrayAdapter
import android.app.AlertDialog
import android.widget.EditText
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
                    scrapList.map { it.name }
                )

                AlertDialog.Builder(context)
                    .setTitle("📌 스크랩 목록")
                    .setAdapter(adapter) { _, which ->
                        val selectedScrap = scrapList[which]

                        AlertDialog.Builder(context)
                            .setTitle("📌 ${selectedScrap.name}")
                            .setItems(arrayOf("열기", "이름 수정", "삭제")) { _, action ->
                                when (action) {
                                    0 -> openWebView(context, selectedScrap.url, roomCode)
                                    1 -> showRenameDialog(context, roomCode, selectedScrap)
                                    2 -> deleteScrap(roomCode, selectedScrap)
                                }
                            }
                            .setNegativeButton("닫기", null)
                            .show()
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

    private fun showRenameDialog(context: Context, roomCode: String, scrap: ScrapItem) {
        val input = EditText(context).apply {
            setText(scrap.name)
        }

        AlertDialog.Builder(context)
            .setTitle("이름 수정")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != scrap.name) {
                    val scrapRef = FirebaseDatabase.getInstance()
                        .getReference("scraps")
                        .child(roomCode)
                        .orderByChild("url")
                        .equalTo(scrap.url)

                    scrapRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (child in snapshot.children) {
                                child.ref.child("name").setValue(newName)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteScrap(roomCode: String, scrap: ScrapItem) {
        val scrapRef = FirebaseDatabase.getInstance()
            .getReference("scraps")
            .child(roomCode)
            .orderByChild("url")
            .equalTo(scrap.url)

        scrapRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    child.ref.removeValue()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
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