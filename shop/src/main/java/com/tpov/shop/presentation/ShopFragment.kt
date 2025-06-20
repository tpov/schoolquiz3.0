package com.tpov.shop.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.tpov.shop.R

class ShopFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.shop_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val referralsButton = view.findViewById<Button>(R.id.b_referrals)
        referralsButton.setOnClickListener {
            // Assuming R.id.title_fragment is accessible from the app module.
            // If not, this would require a different way to specify the container,
            // e.g., passing the container ID to ShopFragment or using an interface.
            val containerId = resources.getIdentifier("title_fragment", "id", requireActivity().packageName)
            if (containerId != 0) {
                parentFragmentManager.beginTransaction()
                    .replace(containerId, ReferralFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            } else {
                // Fallback or error handling if the ID isn't found
                // For now, let's try the old way if specific one not found, or log an error
                // This indicates a potential issue with cross-module resource access or setup
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, ReferralFragment.newInstance()) // Default to android.R.id.content as a last resort
                    .addToBackStack(null)
                    .commit()
                // It would be better to log an error here or communicate this issue
            }
        }
    }
}