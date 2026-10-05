package io.github.zli2038.musicstreaming.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.zli2038.musicstreaming.datamodel.Album

@Database(entities = [Album::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao
}
