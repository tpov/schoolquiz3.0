package com.tpov.shop.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.tpov.shop.R // Assumes R file generation if layouts are present

class ShopFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Assumes shop_fragment.xml layout exists
        return inflater.inflate(R.layout.shop_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Assumes b_referrals button exists in shop_fragment.xml
        val referralsButton = view.findViewById<Button>(R.id.b_referrals)
        referralsButton.setOnClickListener {
            val containerId = resources.getIdentifier("title_fragment", "id", requireActivity().packageName)
            if (containerId != 0) {
                parentFragmentManager.beginTransaction()
                    .replace(containerId, ReferralFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            } else {
                // Fallback or error handling: replace with android.R.id.content if title_fragment is not found
                // This might indicate an issue with layout access or naming.
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, ReferralFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
                // Consider logging an error here in a real application
            }
        }
    }
}