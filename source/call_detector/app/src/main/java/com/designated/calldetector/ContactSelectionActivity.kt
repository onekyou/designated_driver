package com.designated.calldetector

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
        
        // Setup RecyclerView
        adapter = ContactSelectionAdapter(
            onItemClick = { contact ->
                toggleContactSelection(contact)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Setup search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Setup buttons
        selectAllButton.setOnClickListener {
            val allNumbersSelected = filteredContacts.all { contact ->
                contact.phoneNumbers.all { selectedContacts.contains(it) }
            }
            
            if (allNumbersSelected && filteredContacts.isNotEmpty()) {
                // 전체 해제
                selectedContacts.clear()
            } else {
                // 전체 선택
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
        
        // Back button
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
                
                // 전화번호 정규화
                val normalizedNumber = excludeNumberManager.normalizePhoneNumber(phoneNumber)
                
                if (contactsMap.containsKey(contactId)) {
                    // 기존 연락처에 번호 추가
                    val existingContact = contactsMap[contactId]!!
                    if (!existingContact.phoneNumbers.contains(normalizedNumber)) {
                        existingContact.phoneNumbers.add(normalizedNumber)
                        existingContact.displayNumbers.add(formatPhoneNumber(normalizedNumber))
                    }
                } else {
                    // 새 연락처 생성
                    contactsMap[contactId] = ContactItem(
                        id = contactId,
                        name = name,
                        phoneNumbers = mutableListOf(normalizedNumber),
                        displayNumbers = mutableListOf(formatPhoneNumber(normalizedNumber))
                    )
                }
                
                // 이미 제외목록에 있는 번호는 선택상태로 표시
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
        // 이 연락처의 모든 번호가 선택되어 있는지 확인
        val allSelected = contact.phoneNumbers.all { selectedContacts.contains(it) }

        if (allSelected) {
            // 모든 번호가 선택되어 있으면 모두 해제
            contact.phoneNumbers.forEach { phoneNumber ->
                selectedContacts.remove(phoneNumber)
            }
        } else {
            // 하나라도 선택되지 않았으면 모두 선택
            contact.phoneNumbers.forEach { phoneNumber ->
                selectedContacts.add(phoneNumber)
            }
        }
        updateUI()
    }
    
    private fun updateUI() {
        // UI 업데이트를 메인 스레드에서 즉시 실행
        runOnUiThread {
            adapter.submitList(filteredContacts.toList(), selectedContacts.toSet())

            val totalContacts = filteredContacts.size
            val totalSelected = filteredContacts.count { contact ->
                contact.phoneNumbers.any { selectedContacts.contains(it) }
            }

            countTextView.text = "연락처 $totalContacts 개 중 $totalSelected 개 선택"
            confirmButton.text = "선택 완료 (${selectedContacts.size}개)"

            // 전체 선택/해제 버튼 상태 업데이트
            val allNumbersSelected = filteredContacts.all { contact ->
                contact.phoneNumbers.all { selectedContacts.contains(it) }
            }
            selectAllButton.text = if (allNumbersSelected && filteredContacts.isNotEmpty()) "전체 해제" else "전체 선택"
        }
    }
    
    private fun confirmSelection() {
        if (selectedContacts.isEmpty()) {
            Toast.makeText(this, "선택된 연락처가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 선택된 번호들을 제외목록에 이름과 함께 추가
        selectedContacts.forEach { phoneNumber ->
            // 해당 번호가 속한 연락처의 이름 찾기
            val contact = allContacts.find { contact -> 
                contact.phoneNumbers.contains(phoneNumber) 
            }
            excludeNumberManager.addExcludeNumber(phoneNumber, contact?.name)
        }
        
        Toast.makeText(this, "${selectedContacts.size}개의 번호가 개인번호로 추가되었습니다", Toast.LENGTH_SHORT).show()
        
        // 결과와 함께 종료
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

// 연락처 데이터 클래스
data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumbers: MutableList<String>,      // 정규화된 번호들
    val displayNumbers: MutableList<String>    // 표시용 번호들
)

// 연락처 선택 RecyclerView Adapter
class ContactSelectionAdapter(
    private val onItemClick: (ContactItem) -> Unit
) : RecyclerView.Adapter<ContactSelectionAdapter.ViewHolder>() {
    
    private var items = listOf<ContactItem>()
    private var selectedNumbers = setOf<String>()
    
    fun submitList(newItems: List<ContactItem>, selected: Set<String>) {
        val oldSelectedNumbers = selectedNumbers
        items = newItems
        selectedNumbers = selected

        // 선택 상태가 변경된 경우에만 전체 업데이트
        if (oldSelectedNumbers != selected) {
            notifyDataSetChanged()
        } else if (items != newItems) {
            notifyDataSetChanged()
        }
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
        private val cardView: androidx.cardview.widget.CardView = itemView as androidx.cardview.widget.CardView
        private val checkBox: CheckBox = itemView.findViewById(R.id.contactCheckBox)
        private val nameTextView: TextView = itemView.findViewById(R.id.contactNameTextView)
        private val phoneTextView: TextView = itemView.findViewById(R.id.contactPhoneTextView)

        fun bind(contact: ContactItem) {
            nameTextView.text = contact.name

            // 여러 번호가 있는 경우 줄바꿈으로 표시
            phoneTextView.text = contact.displayNumbers.joinToString("\n")

            // 이 연락처의 번호 중 하나라도 선택되어 있으면 체크
            val isSelected = contact.phoneNumbers.any { selectedNumbers.contains(it) }
            checkBox.isChecked = isSelected

            // 선택 상태에 따라 카드 색상 변경
            if (isSelected) {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FF3A3A3A"))
            } else {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FF2A2A2A"))
            }

            itemView.setOnClickListener {
                onItemClick(contact)
            }

            // 체크박스 클릭도 같은 동작
            checkBox.setOnClickListener {
                onItemClick(contact)
            }
        }
    }
}