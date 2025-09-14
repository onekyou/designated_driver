package com.example.calldetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ContactSelectionActivity : AppCompatActivity() {

    private lateinit var excludeNumberManager: ExcludeNumberManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactSelectionAdapter
    private lateinit var searchEditText: EditText
    private lateinit var countTextView: TextView
    private lateinit var selectAllButton: Button
    private lateinit var confirmButton: Button

    private var allContacts = mutableListOf<ContactItem>()
    private var filteredContacts = mutableListOf<ContactItem>()
    private var selectedContacts = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_selection)

        excludeNumberManager = ExcludeNumberManager(this)

        setupViews()
        loadContacts()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.contactRecyclerView)
        searchEditText = findViewById(R.id.contactSearchEditText)
        countTextView = findViewById(R.id.contactCountTextView)
        selectAllButton = findViewById(R.id.selectAllButton)
        confirmButton = findViewById(R.id.confirmButton)

        adapter = ContactSelectionAdapter(
            onItemClick = { contact ->
                toggleContactSelection(contact)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        selectAllButton.setOnClickListener {
            val allNumbersSelected = filteredContacts.all { contact ->
                contact.phoneNumbers.all { selectedContacts.contains(it) }
            }

            if (allNumbersSelected && filteredContacts.isNotEmpty()) {
                selectedContacts.clear()
            } else {
                selectedContacts.clear()
                filteredContacts.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        selectedContacts.add(phoneNumber)
                    }
                }
            }
            updateUI()
        }

        confirmButton.setOnClickListener {
            confirmSelection()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "연락처 선택"
    }

    private fun loadContacts() {
        if (!hasContactPermission()) {
            Toast.makeText(this, "연락처 권한이 필요합니다", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        allContacts.clear()
        val existingNumbers = excludeNumberManager.getExcludeNumbers()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val contactsMap = mutableMapOf<String, ContactItem>()

            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: "이름 없음"
                val phoneNumber = cursor.getString(1)?.replace(Regex("[^0-9+]"), "") ?: continue
                val contactId = cursor.getString(2) ?: continue

                val normalizedNumber = excludeNumberManager.normalizePhoneNumber(phoneNumber)

                if (contactsMap.containsKey(contactId)) {
                    val existingContact = contactsMap[contactId]!!
                    if (!existingContact.phoneNumbers.contains(normalizedNumber)) {
                        existingContact.phoneNumbers.add(normalizedNumber)
                        existingContact.displayNumbers.add(formatPhoneNumber(normalizedNumber))
                    }
                } else {
                    contactsMap[contactId] = ContactItem(
                        id = contactId,
                        name = name,
                        phoneNumbers = mutableListOf(normalizedNumber),
                        displayNumbers = mutableListOf(formatPhoneNumber(normalizedNumber))
                    )
                }

                if (existingNumbers.contains(normalizedNumber)) {
                    selectedContacts.add(normalizedNumber)
                }
            }

            allContacts.addAll(contactsMap.values)
            allContacts.sortBy { it.name }
        }

        filteredContacts.clear()
        filteredContacts.addAll(allContacts)

        updateUI()
    }

    private fun filterContacts(query: String) {
        filteredContacts.clear()

        if (query.isEmpty()) {
            filteredContacts.addAll(allContacts)
        } else {
            val lowerQuery = query.lowercase()
            filteredContacts.addAll(allContacts.filter { contact ->
                contact.name.lowercase().contains(lowerQuery) ||
                contact.displayNumbers.any { it.contains(query) } ||
                contact.phoneNumbers.any { it.contains(query) }
            })
        }

        updateUI()
    }

    private fun toggleContactSelection(contact: ContactItem) {
        contact.phoneNumbers.forEach { phoneNumber ->
            if (selectedContacts.contains(phoneNumber)) {
                selectedContacts.remove(phoneNumber)
            } else {
                selectedContacts.add(phoneNumber)
            }
        }
        updateUI()
    }

    private fun updateUI() {
        recyclerView.post {
            adapter.submitList(filteredContacts.toList(), selectedContacts.toSet())
        }

        val totalContacts = filteredContacts.size
        val totalSelected = filteredContacts.count { contact ->
            contact.phoneNumbers.any { selectedContacts.contains(it) }
        }

        countTextView.text = "연락처 $totalContacts 개 중 $totalSelected 개 선택"
        confirmButton.text = "선택 완료 (${selectedContacts.size}개)"

        val allNumbersSelected = filteredContacts.all { contact ->
            contact.phoneNumbers.all { selectedContacts.contains(it) }
        }
        selectAllButton.text = if (allNumbersSelected && filteredContacts.isNotEmpty()) "전체 해제" else "전체 선택"
    }

    private fun confirmSelection() {
        if (selectedContacts.isEmpty()) {
            Toast.makeText(this, "선택된 연락처가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        selectedContacts.forEach { phoneNumber ->
            val contact = allContacts.find { contact ->
                contact.phoneNumbers.contains(phoneNumber)
            }
            excludeNumberManager.addExcludeNumber(phoneNumber, contact?.name)
        }

        Toast.makeText(this, "${selectedContacts.size}개의 번호가 개인번호로 추가되었습니다", Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK)
        finish()
    }

    private fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatPhoneNumber(number: String): String {
        return when (number.length) {
            11 -> "${number.substring(0, 3)}-${number.substring(3, 7)}-${number.substring(7)}"
            10 -> if (number.startsWith("02")) {
                "${number.substring(0, 2)}-${number.substring(2, 6)}-${number.substring(6)}"
            } else {
                "${number.substring(0, 3)}-${number.substring(3, 6)}-${number.substring(6)}"
            }
            else -> number
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumbers: MutableList<String>,
    val displayNumbers: MutableList<String>
)

class ContactSelectionAdapter(
    private val onItemClick: (ContactItem) -> Unit
) : RecyclerView.Adapter<ContactSelectionAdapter.ViewHolder>() {

    private var items = listOf<ContactItem>()
    private var selectedNumbers = setOf<String>()

    fun submitList(newItems: List<ContactItem>, selected: Set<String>) {
        items = newItems
        selectedNumbers = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.contactCheckBox)
        private val nameTextView: TextView = itemView.findViewById(R.id.contactNameTextView)
        private val phoneTextView: TextView = itemView.findViewById(R.id.contactPhoneTextView)

        fun bind(contact: ContactItem) {
            nameTextView.text = contact.name

            phoneTextView.text = contact.displayNumbers.joinToString("\n")

            val isSelected = contact.phoneNumbers.any { selectedNumbers.contains(it) }
            checkBox.isChecked = isSelected

            itemView.setOnClickListener {
                onItemClick(contact)
            }
        }
    }
}