package com.tpov.schoolquiz.presentation.main

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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.schoolquiz.R
import java.io.File

class MainAdapter(private val items: List<StructureDataLocal?>?,
                  private val listener: OnItemClickListener) : RecyclerView.Adapter<MainAdapter.MainViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_main_fragment, parent, false)
        return MainViewHolder(view)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        items?.get(position)?.let { holder.bind(it) }
        holder.itemView.setOnClickListener {
            items?.get(position)?.nameItem?.let { it1 -> listener.onItemClick(it1) }
        }
    }

    override fun getItemCount(): Int = items?.size ?: 0

    class MainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imv_category_title)
        private val textView: TextView = itemView.findViewById(R.id.tv_category_title)
        private val ratingBar: RatingBar = itemView.findViewById(R.id.rb_category_title)

        fun bind(category: StructureDataLocal) {
            textView.text = category.nameItem
            ratingBar.rating = category.ratingLocal.toFloat() / category.starsMaxLocal

            category.picture?.let { picturePath ->
                if (picturePath.isBlank()) {
                    Log.w("MainAdapter", "⚠️ Picture path is blank for category: ${category.nameItem}")
                    imageView.setImageResource(R.drawable.db_design3_main)
                    return@let
                }

                Log.d("MainAdapter", "📸 Loading picture: '$picturePath' for category: ${category.nameItem}")
                val file = File(imageView.context.applicationContext.filesDir, picturePath)
                Log.d("MainAdapter", "📁 Full file path: ${file.absolutePath}")

                if (file.exists()) {
                    Log.d("MainAdapter", "✅ File exists: ${file.absolutePath} (${file.length()} bytes)")

                    Glide.with(imageView.context)
                        .load(file)
                        .placeholder(R.drawable.db_design3_main)
                        .error(R.drawable.db_design3_main)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                Log.w("MainAdapter", "⚠️ Glide failed to load image: $picturePath")
                                return false // Let Glide handle the error drawable
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                Log.d("MainAdapter", "✅ Image loaded successfully: $picturePath")
                                return false
                            }
                        })
                        .into(imageView)
                } else {
                    Log.w("MainAdapter", "⚠️ File does not exist: ${file.absolutePath}")
                    imageView.setImageResource(R.drawable.db_design3_main)
                }
            } ?: imageView.setImageResource(R.drawable.db_design3_main)
        }
    }
}

interface OnItemClickListener {
    fun onItemClick(category: String)
}
