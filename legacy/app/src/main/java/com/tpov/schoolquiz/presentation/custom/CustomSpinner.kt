package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.PopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R

class CustomSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var items: List<String> = emptyList()
    private var selectedPosition: Int = -1
    private var popupWindow: PopupWindow? = null
    private val textView: TextView
    private var onItemSelectedListener: ((String) -> Unit)? = null
    private var actionItemPosition: Int = -1
    private var onActionItemClickListener: (() -> Unit)? = null
    private var isProgrammaticSelection: Boolean = false

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.custom_spinner_item, this, true)
        textView = view.findViewById(R.id.spinner_text)
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        setOnClickListener {
            Log.d("CustomSpinner", "Clicked, items size: ${items.size}")
            showPopup()
        }
    }

    fun setItems(items: List<String>) {
        Log.d("CustomSpinner", "setItems called with ${items.size} items")
        this.items = items
        actionItemPosition = -1
        if (items.isNotEmpty()) {
            selectedPosition = 0
            textView.text = items[0]
        }
    }

    fun setItemsWithAction(actionItem: String) {
        Log.d("CustomSpinner", "setItemsWithAction called, current items size: ${items.size}")
        this.items = this.items + actionItem
        actionItemPosition = this.items.lastIndex
        Log.d("CustomSpinner", "After adding action item, total size: ${this.items.size}, action position: $actionItemPosition")
        if (this.items.size == 1) {
            selectedPosition = 0
            textView.text = this.items[0]
        }
    }

    fun setSelection(position: Int, notifyListener: Boolean = true) {
        Log.d("CustomSpinner", "setSelection called with position: $position, notifyListener: $notifyListener")
        selectedPosition = position
        textView.text = items.getOrNull(position) ?: ""
        if (position != actionItemPosition && notifyListener) {
            onItemSelectedListener?.invoke(items.getOrNull(position) ?: "")
        }
    }

    private fun showPopup() {
        Log.d("CustomSpinner", "showPopup called, items size: ${items.size}")
        if (items.isEmpty()) {
            Log.d("CustomSpinner", "Items list is empty, returning")
            return
        }

        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_spinner, null)
        val recyclerView = popupView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView.setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 0)
        })
        // Create and setup adapter
        val adapter = PopupSpinnerAdapter(items, { pos ->
            Log.d("CustomSpinner", "Item clicked at position: $pos")
            if (pos == actionItemPosition && onActionItemClickListener != null) {
                Log.d("CustomSpinner", "Action item clicked")
                onActionItemClickListener?.invoke()
                popupWindow?.dismiss()
            } else {
                Log.d("CustomSpinner", "Regular item clicked")
                setSelection(pos)
                popupWindow?.dismiss()
            }
        }, recyclerView, textView, actionItemPosition)
        adapter.selectedPosition = selectedPosition
        adapter.isClosing = false

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()

        // Create and show popup window
        popupWindow = PopupWindow(popupView, width, LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 8f
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            showAsDropDown(this@CustomSpinner)
            setOnDismissListener {
                adapter.animateClosing(selectedPosition)
            }
        }
        Log.d("CustomSpinner", "Popup window created and shown")
    }

    fun setOnItemSelectedListener(listener: (String) -> Unit) {
        onItemSelectedListener = listener
    }

    fun setOnActionItemClickListener(listener: () -> Unit) {
        onActionItemClickListener = listener
    }

    fun count(): Int {
        return items.size
    }
}
