package com.heyzeusv.androidutilitieslibrary.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.heyzeusv.androidutilitieslibrary.database.models.DefaultItem
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DefaultItemDao : BaseDao<DefaultItem>("a_default_item_table_name") {

    @Query("SELECT * " +
            "FROM a_default_item_table_name")
    abstract fun getAllDefaultItems(): Flow<List<DefaultItem>>

    @Query("SELECT * " +
            "FROM a_default_item_table_name " +
            "JOIN DefaultItemFts ON a_default_item_table_name.name = DefaultItemFts.name " +
            "WHERE DefaultItemFts MATCH :query")
    abstract fun searchDefaultItems(query: String): Flow<List<DefaultItem>>
}