package com.tpov.userguide

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.google.android.material.bottomsheet.BottomSheetDialog


@UnstableApi //This is main dialog show information for user
internal class MainView {
    fun showDialog(
        context: Context,
        text: String,
        titleText: String? = null,
        image: Drawable? = null,
        video: String? = null,
        theme: Drawable?,
        item: View,
        clickButton: (Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(context, R.style.BottomSheetDialog).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            if (theme != null) window?.setBackgroundDrawable(theme)

            val dialogView = LayoutInflater.from(context).inflate(R.layout.userguide_dialog_layout, null)
            val textView = dialogView.findViewById<TextView>(R.id.tv_text)
            val titleView = dialogView.findViewById<TextView>(R.id.tv_titul)
            val imageView = dialogView.findViewById<ImageView>(R.id.imv_icon)
            val videoIcon = dialogView.findViewById<ImageView>(R.id.imv_video)

            textView.text = text
            titleView.text = titleText
            imageView.setImageDrawable(image)

            dialogView.findViewById<ImageView>(R.id.imv_ok).setOnClickListener {
                dismiss()
                clickButton(item.id)
            }

            setCancelable(false)
            setContentView(dialogView)

            if (video != null) {
                videoIcon.visibility = View.VISIBLE
                videoIcon.setOnClickListener {
                    showVideoDialog(video, context)
                }
            } else {
                videoIcon.visibility = View.GONE
            }
        }
        dialog.show()
    }

    private fun showVideoDialog(video: String, context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
