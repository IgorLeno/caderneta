package com.example.caderneta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.caderneta.data.dao.*
import com.example.caderneta.data.entity.*
import com.example.caderneta.util.DateConverter

@Database(
    entities = [Cliente::class, Local::class, Produto::class, Venda::class, ItemVenda::class, Configuracoes::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clienteDao(): ClienteDao
    abstract fun localDao(): LocalDao
    abstract fun produtoDao(): ProdutoDao
    abstract fun vendaDao(): VendaDao
    abstract fun itemVendaDao(): ItemVendaDao
    abstract fun configuracoesDao(): ConfiguracoesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caderneta_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}