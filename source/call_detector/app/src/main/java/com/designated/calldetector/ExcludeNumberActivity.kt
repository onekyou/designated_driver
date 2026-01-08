package com.designated.calldetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class ExcludeNumberActivity : AppCompatActivity() {
    
    private lateinit var excludeNumberManager: ExcludeNumberManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExcludeNumberAdapter
    private lateinit var searchEditText: EditText
    private lateinit var countTextView: TextView
    private lateinit var addButton: FloatingActionButton
    private lateinit var contactsButton: Button
    
    private var allNumbers = mutableListOf<ExcludeNumberItem>()
    private var filteredNumbers = mutableListOf<ExcludeNumberItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exclude_number)
        
        excludeNumberManager = ExcludeNumberManager(this)
        
        setupViews()
        loadExcludeNumbers()
    }
    
    private fun setupViews() {
        // Initialize views
        recyclerView = findViewById(R.id.recyclerView)
        searchEditText = findViewById(R.id.searchEditText)
        countTextView = findViewById(R.id.countTextView)
        addButton = findViewById(R.id.addButton)
        contactsButton = findViewById(R.id.contactsButton)
        
        // Setup RecyclerView
        adapter = ExcludeNumberAdapter(
            onDeleteClick = { item ->
                showDeleteConfirmDialog(item)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Setup search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterNumbers(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Setup buttons
        addButton.setOnClickListener {
            showAddNumberDialog()
        }
        
        contactsButton.setOnClickListener {
            openContactSelection()
        }
        
        // Back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "개인번호 관리"
    }
    
    private fun loadExcludeNumbers() {
        allNumbers.clear()
        
        // ExcludeNumberManager에서 이미 이름 정보와 함께 가져옴
        allNumbers.addAll(excludeNumberManager.getExcludeNumberItems())
        
        filteredNumbers.clear()
        filteredNumbers.addAll(allNumbers)
        
        updateUI()
    }
    
    private fun filterNumbers(query: String) {
        filteredNumbers.clear()
        
        if (query.isEmpty()) {
            filteredNumbers.addAll(allNumbers)
        } else {
            val lowerQuery = query.lowercase()
            filteredNumbers.addAll(allNumbers.filter { item ->
                item.displayNumber.contains(query) ||
                item.phoneNumber.contains(query) ||
                item.name?.lowercase()?.contains(lowerQuery) == true
            })
        }
        
        updateUI()
    }
    
    private fun updateUI() {
        adapter.submitList(filteredNumbers.toList())
        countTextView.text = "총 ${allNumbers.size}개의 개인번호"
    }
    
    private fun showAddNumberDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_number, null)
        val editText = dialogView.findViewById<EditText>(R.id.phoneNumberEditText)
        
        AlertDialog.Builder(this)
            .setTitle("개인번호 추가")
            .setView(dialogView)
            .setPositiveButton("추가") { _, _ ->
                val phoneNumber = editText.text.toString().trim()
                if (phoneNumber.isNotEmpty()) {
                    addExcludeNumber(phoneNumber)
                } else {
                    Toast.makeText(this, "전화번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun showDeleteConfirmDialog(item: ExcludeNumberItem) {
        val displayText = if (item.name != null) {
            "${item.name} (${item.displayNumber})"
        } else {
            item.displayNumber
        }
        
        AlertDialog.Builder(this)
            .setTitle("삭제 확인")
            .setMessage("$displayText 를 개인번호에서 제거하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                removeExcludeNumber(item)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun addExcludeNumber(phoneNumber: String) {
        excludeNumberManager.addExcludeNumber(phoneNumber)
        Toast.makeText(this, "개인번호로 추가되었습니다", Toast.LENGTH_SHORT).show()
        loadExcludeNumbers()
    }
    
    private fun removeExcludeNumber(item: ExcludeNumberItem) {
        excludeNumberManager.removeExcludeNumber(item.phoneNumber)
        Toast.makeText(this, "개인번호에서 제거되었습니다", Toast.LENGTH_SHORT).show()
        loadExcludeNumbers()
    }
    
    private fun openContactSelection() {
        if (!hasContactPermission()) {
            requestContactPermission()
            return
        }
        
        val intent = Intent(this, ContactSelectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CONTACT_SELECTION)
    }
    
    private fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestContactPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS),
            PERMISSION_REQUEST_CONTACTS
        )
    }
    
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadExcludeNumbers() // 권한 획득 후 이름 다시 로드
                openContactSelection()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONTACT_SELECTION && resultCode == RESULT_OK) {
            // 연락처 선택 완료 - 목록 새로고침
            loadExcludeNumbers()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, MENU_BACKUP, 0, "백업")
        menu?.add(0, MENU_RESTORE, 0, "복원")
        menu?.add(0, MENU_BACKUP_INFO, 0, "백업 정보")
        menu?.add(0, MENU_DELETE_ALL, 0, "전체 삭제 (테스트)")
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_BACKUP -> {
                performManualBackup()
                return true
            }
            MENU_RESTORE -> {
                performRestore()
                return true
            }
            MENU_BACKUP_INFO -> {
                showBackupInfo()
                return true
            }
            MENU_DELETE_ALL -> {
                showDeleteAllConfirmDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun performManualBackup() {
        Thread {
            val success = excludeNumberManager.backupToFile()
            runOnUiThread {
                if (success) {
                    val count = excludeNumberManager.getExcludeCount()
                    Toast.makeText(this, "백업 완료: ${count}개 번호가 백업되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "백업 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun performRestore() {
        if (!excludeNumberManager.hasBackupFile()) {
            Toast.makeText(this, "백업 파일이 존재하지 않습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("데이터 복원")
            .setMessage("백업 파일에서 개인번호 목록을 복원하시겠습니까?\n현재 데이터와 병합됩니다.")
            .setPositiveButton("복원") { _, _ ->
                Thread {
                    val result = excludeNumberManager.restoreFromFile()
                    runOnUiThread {
                        when (result) {
                            is RestoreResult.Success -> {
                                Toast.makeText(
                                    this, 
                                    "복원 완료: ${result.totalCount}개 번호 (새로운 번호: ${result.newCount}개)", 
                                    Toast.LENGTH_LONG
                                ).show()
                                loadExcludeNumbers()
                            }
                            is RestoreResult.FileNotFound -> {
                                Toast.makeText(this, "백업 파일을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                            is RestoreResult.EmptyFile -> {
                                Toast.makeText(this, "백업 파일이 비어있습니다", Toast.LENGTH_SHORT).show()
                            }
                            is RestoreResult.Error -> {
                                Toast.makeText(this, "복원 실패: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }.start()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun showBackupInfo() {
        val hasBackup = excludeNumberManager.hasBackupFile()
        val lastBackupTime = excludeNumberManager.getLastBackupTime()
        
        val message = if (hasBackup && lastBackupTime > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val backupTimeStr = dateFormat.format(Date(lastBackupTime))
            
            "백업 파일: 존재함\n마지막 백업: $backupTimeStr\n현재 개인번호: ${excludeNumberManager.getExcludeCount()}개\n\n자동 백업은 개인번호 추가/삭제 시 자동으로 수행됩니다."
        } else {
            "백업 파일: 없음\n현재 개인번호: ${excludeNumberManager.getExcludeCount()}개\n\n개인번호를 추가하면 자동으로 백업이 생성됩니다."
        }
        
        AlertDialog.Builder(this)
            .setTitle("백업 정보")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }
    
    private fun showDeleteAllConfirmDialog() {
        val count = excludeNumberManager.getExcludeCount()
        AlertDialog.Builder(this)
            .setTitle("전체 삭제 확인")
            .setMessage("모든 개인번호($count 개)를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.\n(테스트용 기능)")
            .setPositiveButton("삭제") { _, _ ->
                excludeNumberManager.clearAllNumbers()
                loadExcludeNumbers()
                Toast.makeText(this, "모든 개인번호가 삭제되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CONTACTS = 100
        private const val REQUEST_CONTACT_SELECTION = 101
        private const val MENU_BACKUP = 200
        private const val MENU_RESTORE = 201
        private const val MENU_BACKUP_INFO = 202
        private const val MENU_DELETE_ALL = 203
    }
}

// RecyclerView Adapter
class ExcludeNumberAdapter(
    private val onDeleteClick: (ExcludeNumberItem) -> Unit
) : RecyclerView.Adapter<ExcludeNumberAdapter.ViewHolder>() {
    
    private var items = listOf<ExcludeNumberItem>()
    
    fun submitList(newItems: List<ExcludeNumberItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exclude_number, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount() = items.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val phoneTextView: TextView = itemView.findViewById(R.id.phoneTextView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(item: ExcludeNumberItem) {
            if (item.name != null) {
                nameTextView.text = item.name
                nameTextView.visibility = View.VISIBLE
                phoneTextView.text = item.displayNumber
            } else {
                nameTextView.visibility = View.GONE
                phoneTextView.text = item.displayNumber
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}