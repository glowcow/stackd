package dev.glowcow.stackd.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: String): Flow<Card?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): Card?

    @Query("SELECT * FROM cards WHERE passTypeId = :passTypeId AND serial = :serial")
    suspend fun findPass(passTypeId: String, serial: String): Card?

    @Upsert
    suspend fun upsert(card: Card)

    @Query("UPDATE cards SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("UPDATE cards SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE cards SET updatedAt = :at WHERE id = :id")
    suspend fun markChecked(id: String, at: Long)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: String)
}
