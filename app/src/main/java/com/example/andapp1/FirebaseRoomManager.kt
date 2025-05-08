package com.example.andapp1

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*
import java.util.*

object FirebaseRoomManager {

    val roomsRef: DatabaseReference = FirebaseDatabase
        .getInstance("https://andapp1-bcb40-default-rtdb.firebaseio.com/") // 슬래시까지 정확히
        .getReference("rooms")
    // ✅ 전체 채팅방 실시간 감지
    fun getRooms(userId: String, callback: (List<Room>) -> Unit) {
        roomsRef.get().addOnSuccessListener { snapshot ->
            val userRooms = mutableListOf<Room>()

            for (roomSnapshot in snapshot.children) {
                val participantsSnapshot = roomSnapshot.child("participants")
                val isParticipant = participantsSnapshot.hasChild(userId)

                if (isParticipant) {
                    val roomCode = roomSnapshot.key ?: continue
                    val roomTitle = roomSnapshot.child("roomTitle").getValue(String::class.java) ?: "이름 없는 방"
                    val lastActivity = roomSnapshot.child("lastActivityTime").getValue(String::class.java) ?: ""

                    val room = Room(
                        roomCode = roomCode,
                        roomTitle = roomTitle,
                        lastActivityTime = lastActivity,
                        isFavorite = false // 즐겨찾기는 나중에 local에서 체크해서 대입
                    )

                    userRooms.add(room)
                }
            }

            callback(userRooms)
        }.addOnFailureListener {
            Log.e("FirebaseRoomManager", "❌ 방 목록 가져오기 실패", it)
            callback(emptyList())
        }
    }

    // ✅ 채팅방 생성
    fun createRoom(room: Room) {
        val roomRef = roomsRef.child(room.roomCode)

        Log.d("FirebaseRoomManager", "✅ createRoom 호출됨: ${room.roomCode}")

        roomRef.setValue(room)
            .addOnSuccessListener {
                Log.d("FirebaseRoomManager", "✅ Firebase에 방 생성 성공: ${room.roomCode}")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRoomManager", "❌ Firebase에 방 생성 실패: ${e.message}", e)
            }
    }

    // ✅ 채팅방 입장 시 존재하지 않으면 생성 (앱 튕김 방지용)
    fun ensureRoomExists(room: Room, onComplete: (() -> Unit)? = null) {
        val roomRef = roomsRef.child(room.roomCode)
        roomRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // 방이 없을 경우 생성
                    roomRef.setValue(room).addOnCompleteListener {
                        Log.d("FirebaseRoomManager", "새 채팅방 자동 생성됨: ${room.roomCode}")
                        onComplete?.invoke()
                    }
                } else {
                    onComplete?.invoke()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseRoomManager", "채팅방 존재 확인 실패: ${error.message}")
            }
        })
    }

    // ✅ 채팅방 이름 변경
    fun updateRoomName(roomCode: String, newName: String, sender: Author) {
        val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")

        // 1. 현재 방 이름 가져오기 (optional)
        roomsRef.child(roomCode).child("roomTitle").get().addOnSuccessListener { snapshot ->
            val oldName = snapshot.getValue(String::class.java) ?: "알 수 없음"

            // 2. 이름 업데이트
            roomsRef.child(roomCode).child("roomTitle").setValue(newName)

            // 3. 시스템 메시지 전송
            val message = ChatMessage(
                id = System.currentTimeMillis().toString(),
                text = "⚙️ ${sender.name}님이 채팅방 이름을 '${oldName}'에서 '${newName}'으로 변경했습니다.",
                user = sender,
                createdAt = Date()
            )

            val messageRef = FirebaseDatabase.getInstance()
                .getReference("messages")
                .child(roomCode)
                .push()

            messageRef.setValue(message)
        }.addOnFailureListener {
            Log.e("FirebaseRoomManager", "❌ 기존 방 이름 가져오기 실패", it)
        }
    }

    // ✅ 마지막 활동 시간 갱신
    fun updateLastActivityTime(roomCode: String, newTime: String) {
        roomsRef.child(roomCode).child("lastActivityTime").setValue(newTime)
    }

    // ✅ 채팅방 삭제
    fun deleteRoom(roomCode: String) {
        roomsRef.child(roomCode).removeValue()
    }

    // ✅ 참여자 추가 (선택 사항)
    fun addParticipant(roomCode: String, userId: String) {
        roomsRef.child(roomCode).child("participants").child(userId).setValue(true)
    }

    // ✅ 참여자 제거 (선택 사항)
    fun removeParticipant(roomCode: String, userId: String) {
        roomsRef.child(roomCode).child("participants").child(userId).removeValue()
    }

    fun sendLeaveMessage(roomCode: String, author: Author) {
        val message = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = "👋 ${author.name}님이 채팅방을 나갔습니다.",
            user = author,
            createdAt = Date()
        )

        val messageRef = FirebaseDatabase.getInstance()
            .getReference("messages")
            .child(roomCode)
            .push()

        messageRef.setValue(message)
    }
    fun getParticipants(roomCode: String, callback: (List<String>) -> Unit) {
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")

        participantsRef.get().addOnSuccessListener { snapshot ->
            val nicknameList = snapshot.children.mapNotNull {
                it.child("nickname").getValue(String::class.java)
            }
            callback(nicknameList)
        }.addOnFailureListener {
            callback(emptyList())
        }
    }
}