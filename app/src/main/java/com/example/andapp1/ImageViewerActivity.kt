package com.example.andapp1

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.FileOutputStream

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var photoList: List<String>
    private var currentIndex = 0
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ImageViewerActivity","▶︎ 전달된 extras=${intent.extras}")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        photoList = intent.getStringArrayListExtra("photoList") ?: emptyList()
        currentIndex = intent.getIntExtra("startPosition", 0)

        Log.d("ImageViewerActivity", "photoList = $photoList")
        Log.d("ImageViewerActivity", "startPosition = $currentIndex")

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = PhotoPagerAdapter(photoList)
        viewPager.setCurrentItem(currentIndex, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentIndex = position
            }
        })

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url = photoList.getOrNull(currentIndex) ?: return@setOnClickListener
            downloadAndSaveImage(url)
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val url = photoList.getOrNull(currentIndex) ?: return@setOnClickListener
            downloadAndShareImage(url)
        }
    }

    private fun downloadAndSaveImage(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val savedUri = saveToGallery(resource)
                    Toast.makeText(this@ImageViewerActivity, if (savedUri != null) "이미지가 저장되었습니다" else "저장 실패", Toast.LENGTH_SHORT).show()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun downloadAndShareImage(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val uri = saveToCache(resource)
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "이미지 공유"))
                    } else {
                        Toast.makeText(this@ImageViewerActivity, "공유 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun saveToGallery(bitmap: Bitmap): String? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyChatApp")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        }

        return uri?.toString()
    }

    private fun saveToCache(bitmap: Bitmap): android.net.Uri? {
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "shared_image.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        return FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
    }
}
