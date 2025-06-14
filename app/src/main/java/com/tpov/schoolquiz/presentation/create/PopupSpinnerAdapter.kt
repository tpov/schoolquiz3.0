package com.tpov.schoolquiz.presentation.create

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R

class PopupSpinnerAdapter(
    private val items: List<String>,
    private val onItemClick: (Int) -> Unit,
    private val recyclerView: RecyclerView,
    private val spinnerTextView: TextView,
    private val actionItemPosition: Int = -1
) : RecyclerView.Adapter<PopupSpinnerAdapter.ViewHolder>() {

    var selectedPosition: Int = -1
    var isClosing: Boolean = false

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tvItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spinner_popup, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
        holder.textView.isSelected = (position == selectedPosition)
        
        val isActionItem = position == actionItemPosition
        
        // Style differently if it's an action item
        if (isActionItem) {
            holder.textView.setTextColor(Color.GREEN)
            holder.view.setBackgroundColor(
                holder.view.context.getColor(R.color.spinner_dropdown_background)
            )
        } else {
            holder.textView.setTextColor(Color.WHITE)
            holder.view.setBackgroundColor(
                if (position == selectedPosition)
                    holder.view.context.getColor(R.color.spinner_selected_item)
                else
                    holder.view.context.getColor(R.color.spinner_dropdown_background)
            )
        }

        // Анимация выдвигания при открытии - каждый элемент "вырастает" из предыдущего
        if (!isClosing) {
            // Устанавливаем начальное состояние - элемент "сжат" по высоте
            holder.view.scaleY = 0f
            holder.view.pivotY = 0f // Точка масштабирования - верх элемента

            // Анимируем "выращивание" элемента
            holder.view.animate()
                .scaleY(1f) // Растягиваем до полной высоты
                .setDuration(75)
                .setStartDelay(position * 75L) // Каждый следующий элемент появляется с задержкой
                .start()
        } else {
            // При закрытии сразу показываем элемент полностью
            holder.view.scaleY = 1f
            holder.view.visibility = View.VISIBLE
        }

        holder.view.setOnClickListener {
            if (!isClosing) {
                if (position != actionItemPosition) {
                    selectedPosition = position
                }
                onItemClick(position)
            }
        }
    }

    fun animateClosing(selectedPosition: Int) {
        isClosing = true

        // Анимируем закрытие всех элементов кроме выбранного
        for (i in itemCount - 1 downTo 0) { // Идем в обратном порядке - снизу вверх
            if (i != selectedPosition || i == actionItemPosition) {
                val holder = recyclerView.findViewHolderForAdapterPosition(i) as? ViewHolder
                holder?.view?.let { itemView ->
                    // Устанавливаем точку масштабирования вверх элемента
                    itemView.pivotY = 0f

                    // Анимируем "сжатие" элемента
                    itemView.animate()
                        .scaleY(0f) // Сжимаем до нулевой высоты
                        .setDuration(200)
                        .setStartDelay((itemCount - 1 - i) * 30L) // Задержка для каждого элемента
                        .start()
                }
            }
        }

        // Если выбранный элемент - не action item, анимируем его
        if (selectedPosition != actionItemPosition) {
            val selectedHolder = recyclerView.findViewHolderForAdapterPosition(selectedPosition) as? ViewHolder
            selectedHolder?.view?.let { selectedView ->
                // Небольшая задержка, чтобы дождаться закрытия остальных элементов
                selectedView.postDelayed({
                    // Вычисляем позицию spinnerTextView относительно выбранного элемента
                    val spinnerLocation = IntArray(2)
                    spinnerTextView.getLocationOnScreen(spinnerLocation)
                    val selectedItemLocation = IntArray(2)
                    selectedView.getLocationOnScreen(selectedItemLocation)

                    val deltaY = spinnerLocation[1] - selectedItemLocation[1].toFloat()

                    // Анимируем перемещение выбранного элемента на место спиннера
                    selectedView.animate()
                        .translationY(deltaY)
                        .setDuration(250)
                        .withEndAction {
                            onItemClick(selectedPosition) // Закрываем спиннер после анимации
                        }
                        .start()
                }, (itemCount * 30L) + 100) // Ждем завершения анимации закрытия + небольшой запас
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
