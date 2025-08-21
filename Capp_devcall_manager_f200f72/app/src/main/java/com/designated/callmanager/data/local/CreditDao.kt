package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditDao {
    @Query("SELECT * FROM credit_persons ORDER BY lastUpdated DESC")
    fun getAllCreditPersons(): Flow<List<CreditPersonEntity>>
    
    @Query("SELECT * FROM credit_entries WHERE personId = :personId ORDER BY createdAt DESC")
    suspend fun getCreditEntriesByPerson(personId: String): List<CreditEntryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditPerson(person: CreditPersonEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditEntry(entry: CreditEntryEntity)
    
    @Update
    suspend fun updateCreditPerson(person: CreditPersonEntity)
    
    @Delete
    suspend fun deleteCreditPerson(person: CreditPersonEntity)
    
    @Query("DELETE FROM credit_persons WHERE id = :personId")
    suspend fun deleteCreditPersonById(personId: String)
    
    @Query("UPDATE credit_persons SET totalAmount = totalAmount + :amount, lastUpdated = :timestamp WHERE id = :personId")
    suspend fun incrementCreditAmount(personId: String, amount: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE credit_persons SET totalAmount = CASE WHEN totalAmount - :amount >= 0 THEN totalAmount - :amount ELSE 0 END, lastUpdated = :timestamp WHERE id = :personId")
    suspend fun decrementCreditAmount(personId: String, amount: Int, timestamp: Long = System.currentTimeMillis())
    
    @Transaction
    suspend fun addOrIncrementCredit(
        name: String,
        phone: String,
        amount: Int,
        customerName: String = "",
        driverName: String = "",
        date: String = "",
        departure: String = "",
        destination: String = ""
    ) {
        // 기존 person 찾기
        val existingPerson = if (phone.isNotBlank()) {
            findByPhone(phone)
        } else {
            findByNameAndEmptyPhone(name)
        }
        
        val personId = if (existingPerson != null) {
            // 기존 person 업데이트
            incrementCreditAmount(existingPerson.id, amount)
            existingPerson.id
        } else {
            // 새 person 생성
            val newPersonId = "${System.currentTimeMillis()}_${name}_${phone}"
            insertCreditPerson(
                CreditPersonEntity(
                    id = newPersonId,
                    name = name,
                    phone = phone,
                    totalAmount = amount
                )
            )
            newPersonId
        }
        
        // 상세 내역 추가
        if (customerName.isNotEmpty() && driverName.isNotEmpty()) {
            insertCreditEntry(
                CreditEntryEntity(
                    id = "${System.currentTimeMillis()}_${personId}",
                    personId = personId,
                    customerName = customerName,
                    driverName = driverName,
                    date = date,
                    departure = departure,
                    destination = destination,
                    amount = amount
                )
            )
        }
    }
    
    @Query("SELECT * FROM credit_persons WHERE phone = :phone AND phone != '' LIMIT 1")
    suspend fun findByPhone(phone: String): CreditPersonEntity?
    
    @Query("SELECT * FROM credit_persons WHERE name = :name AND phone = '' LIMIT 1")
    suspend fun findByNameAndEmptyPhone(name: String): CreditPersonEntity?
}