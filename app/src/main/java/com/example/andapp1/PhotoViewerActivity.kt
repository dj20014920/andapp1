package com.example.andapp1

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var photoList: List<String>
    private var startPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewPager = ViewPager2(this)
        setContentView(viewPager)

        // 데이터 받기
        photoList = intent.getStringArrayListExtra("photoList") ?: emptyList()
        startPosition = intent.getIntExtra("startPosition", 0)

        viewPager.adapter = PhotoPagerAdapter(photoList)
        viewPager.setCurrentItem(startPosition, false)
    }

    private class PhotoPagerAdapter(private val photos: List<String>) :
        RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

        class PhotoViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            return PhotoViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            Glide.with(holder.imageView.context)
                .load(photos[position])
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = photos.size
    }
}
