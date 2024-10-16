package com.tpov.schoolquiz.presentation.main

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.tpov.common.data.model.local.CategoryData
import com.tpov.schoolquiz.R
import java.io.File

class MainAdapter(private val items: List<CategoryData>,
                  private val listener: OnItemClickListener) : RecyclerView.Adapter<MainAdapter.MainViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_main_fragment, parent, false)
        return MainViewHolder(view)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            listener.onItemClick(items[position])
        }
    }

    override fun getItemCount(): Int = items.size

    class MainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imv_category_title)
        private val textView: TextView = itemView.findViewById(R.id.tv_category_title)
        private val ratingBar: RatingBar = itemView.findViewById(R.id.rb_category_title)

        fun bind(category: CategoryData) {
            textView.text = category.nameQuiz
            ratingBar.rating = category.ratingLocal.toFloat() / category.starsMaxLocal

            category.picture?.let { picturePath ->
                val file = File(imageView.context.applicationContext.filesDir, picturePath)

                if (file.exists()) {
                    println("Файл существует: ${file.absolutePath}")
                    println("Размер файла: ${file.length()} байт")

                    // Декодируем изображение с использованием полного пути
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        println("Не удалось декодировать изображение из файла")
                        imageView.setImageResource(R.drawable.db_design3_main)
                    }

                    // Можно продолжить использовать Glide, если хотите
                    Glide.with(imageView.context)
                        .load(file)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                Log.e("Glide", "Ошибка загрузки изображения", e)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }
                        })
                        .into(imageView)
                } else {
                    println("Файл не существует: ${file.absolutePath}")
                    imageView.setImageResource(R.drawable.db_design3_main)
                }
            } ?: imageView.setImageResource(R.drawable.db_design3_main)
        }
    }
}

interface OnItemClickListener {
    fun onItemClick(category: CategoryData)
}