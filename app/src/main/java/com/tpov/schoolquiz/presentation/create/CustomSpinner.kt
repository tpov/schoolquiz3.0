package com.tpov.schoolquiz.presentation.create

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
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

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.custom_spinner_item, this, true)
        textView = view.findViewById(R.id.spinner_text)
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        setOnClickListener { showPopup() }
    }

    fun setItems(items: List<String>) {
        this.items = items
        if (items.isNotEmpty()) {
            setSelection(0)
        }
    }

    fun setSelection(position: Int) {
        selectedPosition = position
        textView.text = items.getOrNull(position) ?: ""
        onItemSelectedListener?.invoke(items.getOrNull(position) ?: "")
    }

    private fun showPopup() {
        if (items.isEmpty()) return

        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_spinner, null)
        val recyclerView = popupView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView.setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 0) // Отключаем переработку для всех типов представлений
        })
        // Create and setup adapter
        val adapter = PopupSpinnerAdapter(items, { pos ->
            setSelection(pos)
            popupWindow?.dismiss()
        }, recyclerView, textView)
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
    }

    fun setOnItemSelectedListener(listener: (String) -> Unit) {
        onItemSelectedListener = listener
    }

    fun selectItem(position: Int) {
        if (position in items.indices) {
            selectedPosition = position
            textView.text = items[position]
            popupWindow?.dismiss()
            onItemSelectedListener?.invoke(items[position])
        }
    }

    fun count(): Int {
        return items.size
    }
}
