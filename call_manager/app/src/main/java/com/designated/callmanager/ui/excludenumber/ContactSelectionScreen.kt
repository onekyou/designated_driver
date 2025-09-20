package com.designated.callmanager.ui.excludenumber

import android.Manifest
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.ExcludeNumberManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumbers: List<String>,      // 정규화된 번호들
    val displayNumbers: List<String>     // 표시용 번호들
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val excludeNumberManager = remember { ExcludeNumberManager(context) }
    
    var allContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var filteredContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var selectedNumbers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                loadContacts(context, excludeNumberManager) { contacts, preSelected ->
                    allContacts = contacts
                    filteredContacts = contacts
                    selectedNumbers = preSelected
                    isLoading = false
                }
            }
        } else {
            Toast.makeText(context, "연락처 권한이 필요합니다", Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    LaunchedEffect(searchQuery) {
        filteredContacts = if (searchQuery.isEmpty()) {
            allContacts
        } else {
            val lowerQuery = searchQuery.lowercase()
            allContacts.filter { contact ->
                contact.name.lowercase().contains(lowerQuery) ||
                contact.displayNumbers.any { it.contains(searchQuery) } ||
                contact.phoneNumbers.any { it.contains(searchQuery) }
            }
        }
    }
    
    val totalContacts = filteredContacts.size
    val totalSelected = filteredContacts.count { contact ->
        contact.phoneNumbers.any { selectedNumbers.contains(it) }
    }
    
    val allNumbersSelected = filteredContacts.all { contact ->
        contact.phoneNumbers.all { selectedNumbers.contains(it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("연락처 선택") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("연락처 이름 또는 번호로 검색", color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "연락처 $totalContacts 개 중 $totalSelected 개 선택",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    
                    Button(
                        onClick = {
                            selectedNumbers = if (allNumbersSelected && filteredContacts.isNotEmpty()) {
                                emptySet()
                            } else {
                                filteredContacts.flatMap { it.phoneNumbers }.toSet()
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (allNumbersSelected && filteredContacts.isNotEmpty()) "전체 해제" else "전체 선택",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f) // 남은 공간을 차지하도록
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredContacts) { contact ->
                        ContactSelectionItem(
                            contact = contact,
                            isSelected = contact.phoneNumbers.any { selectedNumbers.contains(it) },
                            onToggle = {
                                val numbersToToggle = contact.phoneNumbers.toSet()
                                selectedNumbers = if (contact.phoneNumbers.all { selectedNumbers.contains(it) }) {
                                    selectedNumbers - numbersToToggle
                                } else {
                                    selectedNumbers + numbersToToggle
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (selectedNumbers.isEmpty()) {
                        Toast.makeText(context, "선택된 연락처가 없습니다", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            saveSelectedContacts(
                                context,
                                excludeNumberManager,
                                selectedNumbers,
                                allContacts
                            )
                            Toast.makeText(
                                context,
                                "${selectedNumbers.size}개의 번호가 개인번호로 추가되었습니다",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFAB00)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "선택 완료 (${selectedNumbers.size}개)",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun ContactSelectionItem(
    contact: ContactItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggle() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF3A3A3A) else Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = contact.displayNumbers.joinToString("\n"),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

suspend fun loadContacts(
    context: android.content.Context,
    excludeNumberManager: ExcludeNumberManager,
    onLoaded: (List<ContactItem>, Set<String>) -> Unit
) = withContext(Dispatchers.IO) {
    val contacts = mutableListOf<ContactItem>()
    val existingNumbers = excludeNumberManager.getExcludeNumbers()
    val selectedNumbers = mutableSetOf<String>()
    
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
    )
    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    
    context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
        val contactsMap = mutableMapOf<String, ContactItem>()
        
        while (cursor.moveToNext()) {
            val name = cursor.getString(0) ?: "이름 없음"
            val phoneNumber = cursor.getString(1)?.replace(Regex("[^0-9+]"), "") ?: continue
            val contactId = cursor.getString(2) ?: continue

            val normalizedNumber = excludeNumberManager.normalizePhoneNumber(phoneNumber)
            
            if (contactsMap.containsKey(contactId)) {

                val existingContact = contactsMap[contactId]!!
                val updatedNumbers = existingContact.phoneNumbers + normalizedNumber
                val updatedDisplayNumbers = existingContact.displayNumbers + formatPhoneNumber(normalizedNumber)
                contactsMap[contactId] = existingContact.copy(
                    phoneNumbers = updatedNumbers.distinct(),
                    displayNumbers = updatedDisplayNumbers.distinct()
                )
            } else {

                contactsMap[contactId] = ContactItem(
                    id = contactId,
                    name = name,
                    phoneNumbers = listOf(normalizedNumber),
                    displayNumbers = listOf(formatPhoneNumber(normalizedNumber))
                )
            }

            if (existingNumbers.contains(normalizedNumber)) {
                selectedNumbers.add(normalizedNumber)
            }
        }
        
        contacts.addAll(contactsMap.values)
        contacts.sortBy { it.name }
    }
    
    withContext(Dispatchers.Main) {
        onLoaded(contacts, selectedNumbers)
    }
}

suspend fun saveSelectedContacts(
    context: android.content.Context,
    excludeNumberManager: ExcludeNumberManager,
    selectedNumbers: Set<String>,
    allContacts: List<ContactItem>
) = withContext(Dispatchers.IO) {
    selectedNumbers.forEach { phoneNumber ->

        val contact = allContacts.find { contact -> 
            contact.phoneNumbers.contains(phoneNumber) 
        }
        excludeNumberManager.addExcludeNumber(phoneNumber, contact?.name)
    }
}

fun formatPhoneNumber(number: String): String {
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