package com.example.caderneta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 6,
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

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                // Sem fallbackToDestructiveMigration geral: dados financeiros
                // nunca podem ser descartados por falta de migração — é
                // preferível falhar alto e corrigir. Exceção documentada:
                // v1-v4 não têm schema exportado nem instalação conhecida em
                // campo; apenas essas versões são recriadas.
                val database =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "caderneta_database",
                        ).addMigrations(MIGRATION_5_6)
                        .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
                        .build()
                instance = database
                database
            }
    }
}
