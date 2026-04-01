package com.aman.bastion.data.inapp.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.inapp.entity.InAppSignatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InAppSignatureDao {

    @Query("DELETE FROM inapp_signatures")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(signatures: List<InAppSignatureEntity>)

    @Query("SELECT * FROM inapp_signatures WHERE rule_id = :ruleId")
    fun getByRuleId(ruleId: String): Flow<List<InAppSignatureEntity>>

    @Query(
        """
        SELECT * FROM inapp_signatures
        WHERE rule_id IN (
            SELECT id FROM inapp_rules WHERE package_name = :packageName
        )
        """
    )
    suspend fun getByPackage(packageName: String): List<InAppSignatureEntity>
}
