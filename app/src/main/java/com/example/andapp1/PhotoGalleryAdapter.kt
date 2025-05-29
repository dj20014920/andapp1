package com.example.andapp1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class PhotoGalleryAdapter(
    private val onPhotoClick: (photoUrl: String, position: Int, allPhotos: List<String>) -> Unit
) : ListAdapter<PhotoGalleryItem, RecyclerView.ViewHolder>(PhotoGalleryDiffCallback()) {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_PHOTO_ROW = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PhotoGalleryItem.DateHeader -> TYPE_DATE_HEADER
            is PhotoGalleryItem.PhotoRow -> TYPE_PHOTO_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_photo_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_PHOTO_ROW -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_photo_row, parent, false)
                PhotoRowViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PhotoGalleryItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item)
            }
            is PhotoGalleryItem.PhotoRow -> {
                (holder as PhotoRowViewHolder).bind(item, getAllPhotos())
            }
        }
    }

    private fun getAllPhotos(): List<String> {
        return currentList.filterIsInstance<PhotoGalleryItem.PhotoRow>()
            .flatMap { it.photos }
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.tvDate)
        private val countText: TextView = itemView.findViewById(R.id.tvCount)

        fun bind(item: PhotoGalleryItem.DateHeader) {
            dateText.text = item.date
            countText.text = "${item.count}장"
        }
    }

    inner class PhotoRowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photo1: ImageView = itemView.findViewById(R.id.ivPhoto1)
        private val photo2: ImageView = itemView.findViewById(R.id.ivPhoto2)
        private val photo3: ImageView = itemView.findViewById(R.id.ivPhoto3)

        private val photoViews = listOf(photo1, photo2, photo3)

        fun bind(item: PhotoGalleryItem.PhotoRow, allPhotos: List<String>) {
            // 모든 이미지뷰 초기화 - 빈 상태로 설정
            photoViews.forEach { imageView ->
                imageView.visibility = View.VISIBLE // ✅ 항상 보이게 유지
                imageView.setImageDrawable(null) // 이미지 클리어
                imageView.setOnClickListener(null)
                imageView.background = null // 배경 제거
            }

            // 실제 사진들을 이미지뷰에 설정
            item.photos.forEachIndexed { index, photoUrl ->
                if (index < photoViews.size) {
                    val imageView = photoViews[index]

                    // Glide로 이미지 로드
                    Glide.with(itemView.context)
                        .load(photoUrl)
                        .transform(
                            CenterCrop(),
                            RoundedCorners(16) // 라운드 코너 조금 더 둥글게
                        )
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .into(imageView)

                    // 클릭 리스너 설정
                    imageView.setOnClickListener {
                        val positionInAllPhotos = allPhotos.indexOf(photoUrl)
                        onPhotoClick(photoUrl, positionInAllPhotos, allPhotos)
                    }
                }
            }
        }
    }
}

class PhotoGalleryDiffCallback : DiffUtil.ItemCallback<PhotoGalleryItem>() {
    override fun areItemsTheSame(oldItem: PhotoGalleryItem, newItem: PhotoGalleryItem): Boolean {
        return when {
            oldItem is PhotoGalleryItem.DateHeader && newItem is PhotoGalleryItem.DateHeader -> {
                oldItem.date == newItem.date
            }
            oldItem is PhotoGalleryItem.PhotoRow && newItem is PhotoGalleryItem.PhotoRow -> {
                oldItem.photos == newItem.photos
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: PhotoGalleryItem, newItem: PhotoGalleryItem): Boolean {
        return oldItem == newItem
    }
}