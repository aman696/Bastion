package com.aman.bastion.data.blocking.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.blocking.entity.AppCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCategoryDao {

    @Upsert
    suspend fun upsert(category: AppCategoryEntity)

    @Query("DELETE FROM app_categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM app_categories")
    fun getAll(): Flow<List<AppCategoryEntity>>

    @Query("SELECT * FROM app_categories WHERE id = :id")
    fun getById(id: String): Flow<AppCategoryEntity?>
}
