package com.designated.calldetector

import android.content.Context
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 개인번호(제외번호) 관리 클래스
 * SharedPreferences를 사용하여 로컬에 저장
 */
class ExcludeNumberManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "exclude_numbers"
        private const val KEY_NUMBERS = "numbers"
        private const val KEY_NUMBERS_WITH_NAMES = "numbers_with_names"
        private const val KEY_BACKUP_TIMESTAMP = "backup_timestamp"
        private const val BACKUP_FILE_NAME = "exclude_numbers_backup.txt"
        private const val TAG = "ExcludeNumberManager"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 코루틴 스코프 (Thread 대신 사용 - 메모리 누수 방지)
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 제외 번호 추가 (이름 없이)
     */
    fun addExcludeNumber(phoneNumber: String) {
        addExcludeNumber(phoneNumber, null)
    }
    
    /**
     * 제외 번호 추가 (이름과 함께)
     */
    fun addExcludeNumber(phoneNumber: String, name: String?) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        
        // 기존 Set 방식으로도 저장 (하위 호환성)
        val excludeSet = getExcludeNumbers().toMutableSet()
        excludeSet.add(normalizedNumber)
        saveExcludeNumbers(excludeSet)
        
        // 이름과 함께 JSON으로 저장
        saveNumberWithName(normalizedNumber, name)
        
        Log.d(TAG, "번호 추가됨: $normalizedNumber ${name?.let { "($it)" } ?: ""}")
        
        // 자동 백업 수행
        performAutoBackup()
    }
    
    /**
     * 제외 번호 제거
     */
    fun removeExcludeNumber(phoneNumber: String) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        
        // 기존 Set에서 제거
        val excludeSet = getExcludeNumbers().toMutableSet()
        excludeSet.remove(normalizedNumber)
        saveExcludeNumbers(excludeSet)
        
        // 이름 정보도 제거
        saveNumberWithName(normalizedNumber, null)
        
        Log.d(TAG, "번호 제거됨: $normalizedNumber")
        
        // 자동 백업 수행
        performAutoBackup()
    }
    
    /**
     * 모든 제외 번호 삭제 (테스트용)
     */
    fun clearAllNumbers() {
        prefs.edit()
            .remove(KEY_NUMBERS)
            .remove(KEY_NUMBERS_WITH_NAMES)
            .apply()
        Log.d(TAG, "모든 개인번호 삭제됨")
        
        // 자동 백업 수행
        performAutoBackup()
    }
    
    /**
     * 제외 번호인지 확인
     */
    fun isExcludedNumber(phoneNumber: String): Boolean {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val isExcluded = getExcludeNumbers().contains(normalizedNumber)
        if (isExcluded) {
            Log.d(TAG, "제외 번호로 확인됨: $normalizedNumber")
        }
        return isExcluded
    }
    
    /**
     * 모든 제외 번호 가져오기
     */
    fun getExcludeNumbers(): Set<String> {
        return prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()
    }
    
    /**
     * 제외 번호 개수 가져오기
     */
    fun getExcludeCount(): Int {
        return getExcludeNumbers().size
    }
    
    /**
     * 여러 번호를 한번에 추가
     */
    fun addMultipleNumbers(phoneNumbers: List<String>) {
        val excludeSet = getExcludeNumbers().toMutableSet()
        phoneNumbers.forEach { phoneNumber ->
            excludeSet.add(normalizePhoneNumber(phoneNumber))
        }
        saveExcludeNumbers(excludeSet)
        Log.d(TAG, "${phoneNumbers.size}개 번호 추가됨")
        
        // 자동 백업 수행
        performAutoBackup()
    }
    
    /**
     * 모든 제외 번호 삭제
     */
    fun clearAll() {
        saveExcludeNumbers(emptySet())
        Log.d(TAG, "모든 제외 번호 삭제됨")
        
        // 자동 백업 수행
        performAutoBackup()
    }
    
    /**
     * 제외 번호 저장
     */
    private fun saveExcludeNumbers(numbers: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_NUMBERS, numbers)
            .apply()
    }
    
    /**
     * 전화번호 정규화
     * 010-1234-5678 → 01012345678
     * +82-10-1234-5678 → 01012345678
     */
    fun normalizePhoneNumber(phoneNumber: String): String {
        var normalized = phoneNumber.replace(Regex("[^0-9]"), "")
        
        // 국제번호 처리
        if (normalized.startsWith("82")) {
            normalized = "0" + normalized.substring(2)
        }
        
        // +82 처리
        if (phoneNumber.startsWith("+82")) {
            normalized = "0" + normalized.substring(2)
        }
        
        return normalized
    }
    
    /**
     * 자동 백업 시스템 관련 메서드들
     */
    
    /**
     * 제외 번호 목록을 파일로 백업
     */
    fun backupToFile(): Boolean {
        return try {
            val excludeNumbers = getExcludeNumbers()
            if (excludeNumbers.isEmpty()) {
                Log.d(TAG, "백업할 번호가 없습니다.")
                return true
            }
            
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            
            backupFile.bufferedWriter().use { writer ->
                writer.write("# CallDetector 개인번호 백업 파일\n")
                writer.write("# 백업 일시: $timestamp\n")
                writer.write("# 총 ${excludeNumbers.size}개 번호\n")
                writer.write("\n")
                
                excludeNumbers.sorted().forEach { number ->
                    writer.write("$number\n")
                }
            }
            
            // 백업 완료 시간 저장
            prefs.edit()
                .putLong(KEY_BACKUP_TIMESTAMP, System.currentTimeMillis())
                .apply()
            
            Log.i(TAG, "백업 완료: ${excludeNumbers.size}개 번호가 백업됨")
            true
        } catch (e: Exception) {
            Log.e(TAG, "백업 실패", e)
            false
        }
    }
    
    /**
     * 백업 파일에서 제외 번호 목록 복원
     */
    fun restoreFromFile(): RestoreResult {
        return try {
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            if (!backupFile.exists()) {
                return RestoreResult.FileNotFound
            }
            
            val restoredNumbers = mutableSetOf<String>()
            var backupInfo = ""
            
            backupFile.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmedLine = line.trim()
                    when {
                        trimmedLine.startsWith("#") -> {
                            // 백업 정보 수집
                            if (trimmedLine.contains("백업 일시:") || trimmedLine.contains("총") && trimmedLine.contains("개 번호")) {
                                backupInfo += trimmedLine.removePrefix("#").trim() + "\n"
                            }
                        }
                        trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
                            // 전화번호 라인
                            val normalizedNumber = normalizePhoneNumber(trimmedLine)
                            if (normalizedNumber.isNotEmpty()) {
                                restoredNumbers.add(normalizedNumber)
                            }
                        }
                    }
                }
            }
            
            if (restoredNumbers.isEmpty()) {
                return RestoreResult.EmptyFile
            }
            
            // 현재 데이터와 비교
            val currentNumbers = getExcludeNumbers()
            val newNumbers = restoredNumbers - currentNumbers
            val totalRestored = restoredNumbers.size
            
            // 복원된 번호들로 교체
            saveExcludeNumbers(restoredNumbers)
            
            Log.i(TAG, "복원 완료: $totalRestored 개 번호 복원됨 (새로운 번호: ${newNumbers.size}개)")
            
            RestoreResult.Success(
                totalCount = totalRestored,
                newCount = newNumbers.size,
                backupInfo = backupInfo.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "복원 실패", e)
            RestoreResult.Error(e.message ?: "알 수 없는 오류")
        }
    }
    
    /**
     * 백업 파일 존재 여부 확인
     */
    fun hasBackupFile(): Boolean {
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
        return backupFile.exists()
    }
    
    /**
     * 마지막 백업 시간 가져오기
     */
    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_BACKUP_TIMESTAMP, 0)
    }
    
    /**
     * 자동 백업 수행 (데이터 변경시 호출)
     * 백업은 코루틴으로 비동기 수행하여 UI를 블록하지 않음
     * Thread 대신 코루틴 사용 (메모리 누수 방지)
     */
    fun performAutoBackup() {
        backupScope.launch {
            try {
                backupToFile()
            } catch (e: Exception) {
                Log.e(TAG, "자동 백업 실패", e)
            }
        }
    }
    
    /**
     * 이름과 함께 번호 저장 (JSON 형태)
     */
    private fun saveNumberWithName(normalizedNumber: String, name: String?) {
        try {
            val existingJson = prefs.getString(KEY_NUMBERS_WITH_NAMES, "{}") ?: "{}"
            val jsonObject = JSONObject(existingJson)
            
            if (name != null) {
                jsonObject.put(normalizedNumber, name)
            } else {
                // 이름이 null이면 해당 키 삭제
                jsonObject.remove(normalizedNumber)
            }
            
            prefs.edit()
                .putString(KEY_NUMBERS_WITH_NAMES, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "이름과 함께 번호 저장 실패", e)
        }
    }
    
    /**
     * 이름과 함께 모든 제외 번호 가져오기
     */
    fun getExcludeNumberItems(): List<ExcludeNumberItem> {
        val numbers = getExcludeNumbers()
        val namesJson = prefs.getString(KEY_NUMBERS_WITH_NAMES, "{}") ?: "{}"
        
        return try {
            val jsonObject = JSONObject(namesJson)
            numbers.map { phoneNumber ->
                val name = jsonObject.optString(phoneNumber, null)?.takeIf { it.isNotEmpty() }
                ExcludeNumberItem(
                    phoneNumber = phoneNumber,
                    displayNumber = formatPhoneNumber(phoneNumber),
                    name = name
                )
            }.sortedWith(compareBy({ it.name ?: "zzz" }, { it.phoneNumber }))
        } catch (e: Exception) {
            Log.e(TAG, "이름과 함께 번호 가져오기 실패", e)
            // 예외 발생 시 이름 없이 반환
            numbers.map { phoneNumber ->
                ExcludeNumberItem(
                    phoneNumber = phoneNumber,
                    displayNumber = formatPhoneNumber(phoneNumber),
                    name = null
                )
            }.sortedBy { it.phoneNumber }
        }
    }
    
    /**
     * 전화번호 포맷팅 (표시용)
     */
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
    
    /**
     * 백업 파일 삭제
     */
    fun deleteBackupFile(): Boolean {
        return try {
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            val deleted = if (backupFile.exists()) backupFile.delete() else true
            if (deleted) {
                prefs.edit().remove(KEY_BACKUP_TIMESTAMP).apply()
                Log.d(TAG, "백업 파일 삭제됨")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "백업 파일 삭제 실패", e)
            false
        }
    }
}

/**
 * 복원 결과를 나타내는 sealed class
 */
sealed class RestoreResult {
    object FileNotFound : RestoreResult()
    object EmptyFile : RestoreResult()
    data class Success(val totalCount: Int, val newCount: Int, val backupInfo: String) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

/**
 * 제외 번호 아이템 (번호 + 이름 정보)
 */
data class ExcludeNumberItem(
    val phoneNumber: String,      // 01012345678 (정규화된 번호)
    val displayNumber: String,    // 010-1234-5678 (표시용)
    val name: String? = null      // 연락처 이름 (있는 경우)
)