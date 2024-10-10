package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Venda
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface VendaDao {
    @Query("SELECT * FROM vendas")
    fun getAllVendas(): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE id = :id")
    suspend fun getVendaById(id: Long): Venda?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenda(venda: Venda): Long

    @Update
    suspend fun updateVenda(venda: Venda)

    @Delete
    suspend fun deleteVenda(venda: Venda)

    @Query("SELECT * FROM vendas WHERE clienteId = :clienteId")
    fun getVendasByCliente(clienteId: Long): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE localId = :localId")
    fun getVendasByLocal(localId: Long): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getVendasByDateRange(startDate: Date, endDate: Date): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE clienteId = :clienteId AND data BETWEEN :startDate AND :endDate")
    fun getVendasByClienteAndDateRange(clienteId: Long, startDate: Date, endDate: Date): Flow<List<Venda>>

    @Query("SELECT SUM(valor) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalVendasByDateRange(startDate: Date, endDate: Date): Flow<Double?>

    @Query("SELECT SUM(valor) FROM vendas WHERE clienteId = :clienteId AND data BETWEEN :startDate AND :endDate")
    fun getTotalVendasByClienteAndDateRange(clienteId: Long, startDate: Date, endDate: Date): Flow<Double?>

    @Query("SELECT SUM(quantidadeSalgados) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalSalgadosByDateRange(startDate: Date, endDate: Date): Flow<Int?>

    @Query("SELECT SUM(quantidadeSucos) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalSucosByDateRange(startDate: Date, endDate: Date): Flow<Int?>
}