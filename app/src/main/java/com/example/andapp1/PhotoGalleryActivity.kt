package com.example.andapp1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PhotoGalleryActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: PhotoGalleryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "사진첩"

        // 뷰 초기화
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)

        // 툴바 설정
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "$roomName 사진첩"
            setDisplayHomeAsUpEnabled(true)
        }

        // ViewModel 초기화
        viewModel = ViewModelProvider(
            this,
            ChatViewModelFactory(roomCode, applicationContext)
        )[ChatViewModel::class.java]

        // RecyclerView 설정
        adapter = PhotoGalleryAdapter { photoUrl, position, photoList ->
            // 사진 클릭 시 ImageViewerActivity로 이동
            val intent = Intent(this, ImageViewerActivity::class.java).apply {
                putStringArrayListExtra("photoList", ArrayList(photoList))
                putExtra("startPosition", position)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 메시지 관찰하여 이미지만 필터링
        observeMessages()
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            // 이미지가 있는 메시지만 필터링
            val imageMessages = messages.filter { !it.imageUrlValue.isNullOrEmpty() }

            Log.d("PhotoGallery", "총 이미지 메시지 수: ${imageMessages.size}")

            // 날짜별로 그룹핑 (최신 날짜가 위로)
            val groupedMessages = imageMessages
                .sortedByDescending { it.createdAt.time } // 최신순 정렬
                .groupBy { message ->
                    val calendar = Calendar.getInstance()
                    calendar.time = message.createdAt
                    // 날짜만 추출 (시간 제거)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
                .toSortedMap(compareByDescending { it }) // 최신 날짜가 먼저

            // PhotoGalleryItem 리스트 생성
            val galleryItems = mutableListOf<PhotoGalleryItem>()

            groupedMessages.forEach { (date, messagesForDate) ->
                // 날짜 헤더 추가
                galleryItems.add(
                    PhotoGalleryItem.DateHeader(
                        date = dateFormat.format(date),
                        count = messagesForDate.size
                    )
                )

                // 해당 날짜의 이미지들을 시간순으로 정렬 (최신이 먼저)
                val sortedMessages = messagesForDate.sortedByDescending { it.createdAt.time }

                // 3개씩 그룹핑하여 Row로 만들기
                sortedMessages.chunked(3).forEach { rowMessages ->
                    val photoUrls = rowMessages.map { it.imageUrlValue!! }
                    galleryItems.add(
                        PhotoGalleryItem.PhotoRow(
                            photos = photoUrls,
                            messages = rowMessages
                        )
                    )
                }
            }

            adapter.submitList(galleryItems)

            // 사진이 없는 경우 처리
            emptyView.visibility = if (galleryItems.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// 갤러리 아이템 타입들
sealed class PhotoGalleryItem {
    data class DateHeader(
        val date: String,
        val count: Int
    ) : PhotoGalleryItem()

    data class PhotoRow(
        val photos: List<String>, // 최대 3개
        val messages: List<ChatMessage>
    ) : PhotoGalleryItem()
}