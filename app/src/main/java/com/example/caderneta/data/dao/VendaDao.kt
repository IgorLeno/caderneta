package com.example.caderneta.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
    fun getVendasByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<List<Venda>>

    @Query("SELECT * FROM vendas WHERE clienteId = :clienteId AND data BETWEEN :startDate AND :endDate")
    fun getVendasByClienteAndDateRange(
        clienteId: Long,
        startDate: Date,
        endDate: Date,
    ): Flow<List<Venda>>

    @Query("SELECT SUM(valorCentavos) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalVendasByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Long?>

    @Query(
        "SELECT SUM(valorCentavos) FROM vendas " +
            "WHERE clienteId = :clienteId AND data BETWEEN :startDate AND :endDate",
    )
    fun getTotalVendasByClienteAndDateRange(
        clienteId: Long,
        startDate: Date,
        endDate: Date,
    ): Flow<Long?>

    @Query("SELECT SUM(quantidadeSalgados) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalSalgadosByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Int?>

    @Query("SELECT SUM(quantidadeSucos) FROM vendas WHERE data BETWEEN :startDate AND :endDate")
    fun getTotalSucosByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Int?>

    @Query(
        "SELECT COALESCE(SUM(CASE transacao " +
            "WHEN 'A_PRAZO' THEN valorCentavos " +
            "WHEN 'PAGAMENTO' THEN -valorCentavos " +
            "ELSE 0 END), 0) FROM vendas WHERE clienteId = :clienteId",
    )
    suspend fun calcularSaldoHistorico(clienteId: Long): Long

    @Query("SELECT DISTINCT clienteId FROM vendas")
    suspend fun getClienteIdsComHistorico(): List<Long>
}
