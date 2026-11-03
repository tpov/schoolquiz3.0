package com.tpov.schoolquiz.presentation.dowload

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R

// DownloadedResourcesAdapter.kt
class DownloadedResourcesAdapter(
    private val onDeleteResource: (DownloadedResource) -> Unit
) : RecyclerView.Adapter<DownloadedResourcesAdapter.DownloadedResourceViewHolder>() {

    private val downloadedResourcesList = mutableListOf<DownloadedResource>()

    fun submitList(list: List<DownloadedResource>) {
        downloadedResourcesList.clear()
        downloadedResourcesList.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadedResourceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_downloaded_resource, parent, false)
        return DownloadedResourceViewHolder(view, onDeleteResource)
    }

    override fun onBindViewHolder(holder: DownloadedResourceViewHolder, position: Int) {
        holder.bind(downloadedResourcesList[position])

    }

    override fun getItemCount(): Int = downloadedResourcesList.size

    class DownloadedResourceViewHolder(
        itemView: View,
        private val onDeleteResource: (DownloadedResource) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvResourceName = itemView.findViewById<TextView>(R.id.tv_resource_name)
        private val tvResourceSize = itemView.findViewById<TextView>(R.id.tv_resource_size)
        private val btnDeleteResource = itemView.findViewById<ImageButton>(R.id.btn_delete_resource)

        fun bind(downloadedResource: DownloadedResource) {
            tvResourceName.text = downloadedResource.fileName
            tvResourceSize.text = "${downloadedResource.fileSize} КБ"
            btnDeleteResource.setOnClickListener {
                onDeleteResource(downloadedResource)
            }
        }
    }
}
