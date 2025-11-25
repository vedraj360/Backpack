package com.vdx.backpack.demo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors


@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class MyDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: MyDatabase? = null

        fun getInstance(context: Context): MyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MyDatabase::class.java,
                    "my_app_database"
                )
                    .addCallback(DatabaseCallback(context))
                    .fallbackToDestructiveMigration()
                    .build()

                Executors.newSingleThreadExecutor().execute {
                    try {
                        instance.openHelper.writableDatabase
                        instance.query("SELECT 1", null).close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(getInstance(context))
                }
            }
        }

        suspend fun populateDatabase(database: MyDatabase) {
            val dao = database.noteDao()

            val note1 = NoteEntity(title = "Welcome!", content = "This is your first note.")
            val note2 = NoteEntity(title = "Tips", content = "Swipe to delete notes.")

            dao.insertNote(note1)
            dao.insertNote(note2)
        }
    }
}