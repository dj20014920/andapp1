package com.example.andapp1

import android.util.Log
import android.content.Context
import com.google.firebase.database.*
import java.util.*

object FirebaseRoomManager {

    val roomsRef: DatabaseReference = FirebaseDatabase
        .getInstance("https://andapp1-bcb40-default-rtdb.firebaseio.com/")
        .getReference("rooms")

    private val usersRef: DatabaseReference = FirebaseDatabase
        .getInstance("https://andapp1-bcb40-default-rtdb.firebaseio.com/")
        .getReference("users")

    // ✅ 전체 채팅방 실시간 감지 - ValueEventListener 반환하도록 수정
    fun getRooms(userId: String, onComplete: (List<Room>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("FirebaseRoomManager", "✅ getRooms onDataChange 호출됨")

                val rooms = mutableListOf<Room>()
                for (roomSnapshot in snapshot.children) {
                    val participantsSnapshot = roomSnapshot.child("participants")
                    val isParticipant = participantsSnapshot.hasChild(userId)

                    if (isParticipant) {
                        val room = roomSnapshot.getValue(Room::class.java)
                        room?.let { rooms.add(it) }
                    }
                }
                onComplete(rooms)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseRoomManager", "❌ getRooms 실패: ${error.message}")
                onComplete(emptyList())
            }
        }

        roomsRef.addValueEventListener(listener)
        return listener
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

    // ✅ 채팅방 입장 시 존재하지 않으면 생성
    fun ensureRoomExists(room: Room, onComplete: (() -> Unit)? = null) {
        val roomRef = roomsRef.child(room.roomCode)
        roomRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
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
                onComplete?.invoke()
            }
        })
    }

    // ✅ 채팅방 이름 변경
    fun updateRoomName(roomCode: String, newName: String, sender: Author) {
        roomsRef.child(roomCode).child("roomTitle").get().addOnSuccessListener { snapshot ->
            val oldName = snapshot.getValue(String::class.java) ?: "알 수 없음"

            // 이름 업데이트
            roomsRef.child(roomCode).child("roomTitle").setValue(newName)

            // 시스템 메시지 전송
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

    // ✅ 참여자 추가 - 수정됨
    fun addParticipant(roomCode: String, userId: String) {
        // SharedPreferences에서 닉네임 가져오기
        val prefs = FirebaseDatabase.getInstance().app.applicationContext
            .getSharedPreferences("login", Context.MODE_PRIVATE)
        val nickname = prefs.getString("nickname", "Unknown") ?: "Unknown"

        // participants/userId/nickname 구조로 저장
        val participantData = mapOf("nickname" to nickname)
        roomsRef.child(roomCode).child("participants").child(userId).setValue(participantData)
            .addOnSuccessListener {
                Log.d("FirebaseRoomManager", "✅ 참여자 추가 성공: $userId($nickname) -> $roomCode")
            }
            .addOnFailureListener {
                Log.e("FirebaseRoomManager", "❌ 참여자 추가 실패", it)
            }
    }

    // ✅ 참여자 제거 - 수정됨
    fun removeParticipant(roomCode: String, userId: String) {
        roomsRef.child(roomCode).child("participants").child(userId).removeValue()
            .addOnSuccessListener {
                Log.d("FirebaseRoomManager", "✅ 참여자 제거 성공: $userId from $roomCode")

                // 참여자가 0명이 되면 방 삭제 검토
                checkAndDeleteEmptyRoom(roomCode)
            }
            .addOnFailureListener {
                Log.e("FirebaseRoomManager", "❌ 참여자 제거 실패", it)
            }
    }

    // ✅ 빈 방 삭제 검토
    private fun checkAndDeleteEmptyRoom(roomCode: String) {
        roomsRef.child(roomCode).child("participants").get().addOnSuccessListener { snapshot ->
            if (!snapshot.hasChildren()) {
                Log.d("FirebaseRoomManager", "참여자가 없는 방 삭제: $roomCode")
                deleteRoom(roomCode)

                // 메시지도 삭제
                FirebaseDatabase.getInstance()
                    .getReference("messages")
                    .child(roomCode)
                    .removeValue()
            }
        }
    }

    // ✅ 나가기 메시지 전송
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

    // ✅ 참여자 목록 가져오기 - 수정됨
    fun getParticipants(roomCode: String, callback: (List<String>) -> Unit) {
        roomsRef.child(roomCode).child("participants").get().addOnSuccessListener { snapshot ->
            val userIds = snapshot.children.mapNotNull { it.key }

            if (userIds.isEmpty()) {
                callback(emptyList())
                return@addOnSuccessListener
            }

            // 각 userId로 닉네임 조회
            val nicknames = mutableListOf<String>()
            var loadedCount = 0

            for (userId in userIds) {
                usersRef.child(userId).get().addOnSuccessListener { userSnapshot ->
                    val nickname = userSnapshot.child("nickname").getValue(String::class.java) ?: "알 수 없음"
                    nicknames.add(nickname)
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        callback(nicknames)
                    }
                }.addOnFailureListener {
                    nicknames.add("알 수 없음")
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        callback(nicknames)
                    }
                }
            }
        }.addOnFailureListener {
            Log.e("FirebaseRoomManager", "❌ 참여자 목록 조회 실패", it)
            callback(emptyList())
        }
    }

    // ✅ 방 정보 가져오기
    fun getRoomInfo(roomCode: String, callback: (Room?) -> Unit) {
        roomsRef.child(roomCode).get().addOnSuccessListener { snapshot ->
            val room = snapshot.getValue(Room::class.java)
            callback(room)
        }.addOnFailureListener {
            callback(null)
        }
    }

    // ✅ 사용자가 참여 중인지 확인
    fun isUserParticipant(roomCode: String, userId: String, callback: (Boolean) -> Unit) {
        roomsRef.child(roomCode).child("participants").child(userId).get()
            .addOnSuccessListener { snapshot ->
                callback(snapshot.exists())
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}