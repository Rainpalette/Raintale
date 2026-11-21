package io.github.rainpalette.raintale.data.character

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCharacterDao {

    @Query("SELECT * FROM custom_character ORDER BY createdAt ASC")
    fun all(): Flow<List<CustomCharacter>>

    @Query("SELECT * FROM custom_character WHERE id = :id")
    suspend fun byId(id: Long): CustomCharacter?

    @Query("SELECT COUNT(*) FROM custom_character")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(c: CustomCharacter): Long

    @Delete
    suspend fun delete(c: CustomCharacter)
}
