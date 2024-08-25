package com.tpov.schoolquiz.presentation.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tpov.common.data.model.local.CategoryData
import com.tpov.schoolquiz.R

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
        private val textView: TextView = itemView.findViewById(R.id.tv_category)
        private val ratingBar: RatingBar = itemView.findViewById(R.id.rb_category_title)

        fun bind(category: CategoryData) {
            textView.text = category.nameQuiz
            ratingBar.rating = category.ratingLocal.toFloat() / category.starsMaxLocal
            category.picture?.let {
                Glide.with(imageView.context).load(it).into(imageView)
            } ?: imageView.setImageResource(R.drawable.db_design3_main)
        }
    }
}

interface OnItemClickListener {
    fun onItemClick(category: CategoryData)
}