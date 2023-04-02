package com.tpov.schoolquiz.presentation.network.profile

import android.provider.Settings.Global.getString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.ContactItem
import com.tpov.schoolquiz.databinding.ContactItemBinding

class ContactAdapter(private val contacts: List<ContactItem>) :
    RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    private lateinit var binding: ContactItemBinding


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        binding = ContactItemBinding.inflate(LayoutInflater.from(holder.itemView.context), holder.itemView as ViewGroup, false)
        val contact = contacts[position]
        holder.bind(contact)
    }

    override fun getItemCount(): Int = contacts.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(contact: ContactItem) {
            binding.name.text = contact.name ?: ""
            binding.mail.text = contact.mail ?: ""
            var contactList = ""
            contact.number?.forEach {
                contactList += if (contactList == "") it
                else ", $it"
            }
            binding.number.text = contactList
            if (contact.photo != null) binding.photo.setImageBitmap(contact.photo)
        }
    }
}