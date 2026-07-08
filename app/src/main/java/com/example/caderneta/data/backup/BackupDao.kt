package com.example.caderneta.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.Venda

@Dao
interface BackupDao {
    @Query("SELECT * FROM locais")
    suspend fun getAllLocais(): List<Local>

    @Query("SELECT * FROM clientes")
    suspend fun getAllClientes(): List<Cliente>

    @Query("SELECT * FROM operacoes")
    suspend fun getAllOperacoes(): List<Operacao>

    @Query("SELECT * FROM vendas")
    suspend fun getAllVendas(): List<Venda>

    @Query("SELECT * FROM contas")
    suspend fun getAllContas(): List<Conta>

    @Query("SELECT * FROM configuracoes")
    suspend fun getAllConfiguracoes(): List<Configuracoes>

    @Query("DELETE FROM vendas")
    suspend fun deleteVendas()

    @Query("DELETE FROM operacoes")
    suspend fun deleteOperacoes()

    @Query("DELETE FROM contas")
    suspend fun deleteContas()

    @Query("DELETE FROM clientes")
    suspend fun deleteClientes()

    @Query("DELETE FROM locais")
    suspend fun deleteLocais()

    @Query("DELETE FROM configuracoes")
    suspend fun deleteConfiguracoes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocais(locais: List<Local>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClientes(clientes: List<Cliente>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperacoes(operacoes: List<Operacao>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendas(vendas: List<Venda>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContas(contas: List<Conta>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracoes(configuracoes: List<Configuracoes>)
}
