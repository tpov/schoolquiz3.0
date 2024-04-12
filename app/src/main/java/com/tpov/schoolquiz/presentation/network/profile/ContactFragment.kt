package com.tpov.schoolquiz.presentation.network.profile

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.ContactItem
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import kotlinx.coroutines.InternalCoroutinesApi

class ContactFragment : BaseFragment() {

    companion object {
        @JvmStatic
        fun newInstance() = ContactFragment()

        val PERMISSION_REQUEST_CONTACTS = 1
        val permissions = arrayOf(Manifest.permission.READ_CONTACTS)
    }

    @OptIn(InternalCoroutinesApi::class)
    private lateinit var viewModel: ProfileViewModel
    private lateinit var adapter: ContactAdapter
    private val contactList = mutableListOf<ContactItem>()

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_contact, container, false)

        // Request permission if necessary
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, PERMISSION_REQUEST_CONTACTS)
        } else {
            // Load contacts if permission is already granted
            loadContacts()
        }

        return view
    }

    private fun loadContacts() {
        val contentResolver = requireContext().contentResolver
        val cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)

        if (cursor!!.count > 0) {
            while (cursor.moveToNext()) {
                contactList.add(
                    ContactItem(
                        getNameContact(cursor),
                        getNumbersContact(cursor, contentResolver),
                        getEmailContact(cursor),
                        getPhotoContact()
                    )
                )
            }
        }

        initAdapter()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Load contacts if permission is granted
                loadContacts()
            } else {
                // Show an error message if permission is denied
                Toast.makeText(requireContext(), "Permission to access contacts is denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getNameContact(cursor: Cursor): String? {

        try {
            val nameColumnIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (nameColumnIndex == -1) return null

            return cursor.getString(nameColumnIndex)
        } catch (e: Exception) {
            return null
        }
    }

    private fun getEmailContact(cursor: Cursor): String? {
        try {
            val emailColumnIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (emailColumnIndex == -1) return null

            return cursor.getString(emailColumnIndex)
        } catch (e: Exception) {
            return null
        }
    }

    private fun getPhotoContact(): Bitmap? {
        return try {
            val contactUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                id.toLong()
            )

            val inputStream = context?.contentResolver?.openInputStream(contactUri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }

    }

    private fun getNumbersContact(cursor: Cursor, contentResolver: ContentResolver): List<String> {
        try {
            val idColumnIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)

            val id = cursor.getString(idColumnIndex)
            val phoneCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(id),
                null
            )

            val phoneNumbers: MutableList<String> = mutableListOf()
            if (phoneCursor!!.count > 0) {
                while (phoneCursor.moveToNext()) {
                    val phoneNumberColumnIndex =
                        phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (phoneNumberColumnIndex != -1) {
                        phoneNumbers.add(phoneCursor.getString(phoneNumberColumnIndex))
                    } else return emptyList()
                }
            }
            phoneCursor.close()
            return phoneNumbers
        } catch (e: Exception) {
            return emptyList()
        }


    }

    private fun initAdapter() {

        val recyclerView = view?.findViewById<RecyclerView>(R.id.recycler_view_contacts)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = ContactAdapter(contactList)
    }

}