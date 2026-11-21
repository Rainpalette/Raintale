package io.github.rainpalette.raintale.data.character

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 自定义（纯 prompt）角色的 Room 库。单例。 */
@Database(entities = [CustomCharacter::class], version = 1, exportSchema = false)
abstract class CustomCharacterDatabase : RoomDatabase() {

    abstract fun dao(): CustomCharacterDao

    companion object {
        @Volatile
        private var INSTANCE: CustomCharacterDatabase? = null

        fun get(context: Context): CustomCharacterDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CustomCharacterDatabase::class.java,
                    "custom_character.db",
                ).build().also { INSTANCE = it }
            }
    }
}
