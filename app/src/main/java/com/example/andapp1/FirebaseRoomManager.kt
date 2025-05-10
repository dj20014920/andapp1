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
    fun createRoom(room: Room, creatorId: String) {
        val roomRef = roomsRef.child(room.roomCode)

        Log.d("FirebaseRoomManager", "✅ createRoom 호출됨: ${room.roomCode}")

        roomRef.setValue(room)
            .addOnSuccessListener {
                Log.d("FirebaseRoomManager", "✅ Firebase에 방 생성 성공: ${room.roomCode}")
                addParticipant(room.roomCode, creatorId) // ✅ 본인을 참여자로 추가
            }
            .addOnFailureListener {
                Log.e("FirebaseRoomManager", "❌ Firebase에 방 생성 실패", it)
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

    fun checkInactiveRooms() {
        roomsRef.get().addOnSuccessListener { snapshot ->
            val currentTimeMillis = System.currentTimeMillis()

            for (roomSnapshot in snapshot.children) {
                val roomCode = roomSnapshot.key ?: continue
                val lastActivityStr = roomSnapshot.child("lastActivityTime").getValue(String::class.java) ?: continue
                val lastActivityMillis = try {
                    Util.parseTimestampToMillis(lastActivityStr)
                } catch (e: Exception) {
                    Log.e("RoomCheck", "❌ 시간 파싱 실패: $roomCode", e)
                    continue
                }

                val elapsedDays = (currentTimeMillis - lastActivityMillis) / (1000 * 60 * 60 * 24)

                when (elapsedDays) {
                    6L -> {
                        // ✅ 6일 경과: 시스템 메시지 전송
                        sendSystemWarning(roomCode)
                    }
                    in 7L..Long.MAX_VALUE -> {
                        // ✅ 7일 이상 경과: Firebase에서 삭제
                        deleteRoom(roomCode)
                        Log.d("RoomCheck", "🗑 방 삭제됨: $roomCode")
                    }
                    else -> {
                        // 아직 삭제 조건 아님
                    }
                }
            }
        }.addOnFailureListener {
            Log.e("RoomCheck", "❌ 전체 방 확인 실패", it)
        }
    }
    fun sendSystemWarning(roomCode: String) {
        val message = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = "⚠️ 이 채팅방은 24시간 내에 삭제될 예정입니다.\n활동이 없으면 자동 삭제됩니다.",
            user = Author("system", "System"),
            createdAt = Date()
        )

        val messageRef = FirebaseDatabase.getInstance()
            .getReference("messages")
            .child(roomCode)
            .push()

        messageRef.setValue(message)
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
        val participantsRef = roomsRef.child(roomCode).child("participants")

        participantsRef.child(userId).removeValue().addOnSuccessListener {
            // 삭제 후 남은 인원 확인
            participantsRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.hasChildren()) {
                    // 아무도 없으면 방 삭제
                    deleteRoom(roomCode)
                    Log.d("FirebaseRoomManager", "🚨 모든 참여자가 나갔으므로 방 삭제됨: $roomCode")
                }
            }
        }
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