package dev.glowcow.stackd.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [Card::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class StackdDatabase : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        fun create(context: Context): StackdDatabase =
            Room.databaseBuilder(context, StackdDatabase::class.java, "stackd.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
