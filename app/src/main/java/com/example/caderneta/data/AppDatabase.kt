package com.example.caderneta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.caderneta.data.backup.BackupDao
import com.example.caderneta.data.dao.ClienteDao
import com.example.caderneta.data.dao.ConfiguracoesDao
import com.example.caderneta.data.dao.ContaDao
import com.example.caderneta.data.dao.LocalDao
import com.example.caderneta.data.dao.OperacaoDao
import com.example.caderneta.data.dao.VendaDao
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.util.DateConverter

@Database(
    entities = [
        Cliente::class,
        Local::class,
        Venda::class,
        Configuracoes::class,
        Operacao::class,
        Conta::class,
    ],
    version = AppDatabase.DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clienteDao(): ClienteDao

    abstract fun localDao(): LocalDao

    abstract fun vendaDao(): VendaDao

    abstract fun configuracoesDao(): ConfiguracoesDao

    abstract fun operacaoDao(): OperacaoDao

    abstract fun contaDao(): ContaDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val DATABASE_VERSION = 2

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE clientes ADD COLUMN fotoNome TEXT DEFAULT NULL")
                }
            }

        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                val database =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "caderneta_database",
                        ).addMigrations(MIGRATION_1_2)
                        .build()
                instance = database
                database
            }
    }
}
