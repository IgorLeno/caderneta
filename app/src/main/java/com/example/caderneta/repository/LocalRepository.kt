package com.example.caderneta.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.dao.ClienteDao
import com.example.caderneta.data.dao.LocalDao
import com.example.caderneta.data.entity.Local
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalRepository(
    private val localDao: LocalDao,
    private val clienteDao: ClienteDao,
    private val db: AppDatabase,
) {
    fun getAllLocais(): Flow<List<Local>> = localDao.getAllLocais()

    suspend fun getLocalById(id: Long): Local? =
        withContext(Dispatchers.IO) {
            localDao.getLocalById(id)
        }

    suspend fun insertLocal(local: Local): Long =
        withContext(Dispatchers.IO) {
            localDao.insertLocal(local)
        }

    suspend fun updateLocal(local: Local) =
        withContext(Dispatchers.IO) {
            localDao.updateLocal(local)
        }

    suspend fun deleteLocal(local: Local): Boolean =
        withContext(Dispatchers.IO) {
            try {
                localDao.deleteLocal(local)
                false
            } catch (_: SQLiteConstraintException) {
                db.withTransaction {
                    val ids = subtreeAtiva(local.id, localDao.getAllLocaisList())
                    if (ids.isNotEmpty()) {
                        localDao.arquivarLocais(ids)
                        clienteDao.arquivarClientesVinculadosAosLocais(ids)
                    }
                }
                true
            }
        }

    suspend fun buscarLocais(query: String): List<Local> = localDao.buscarLocais("%$query%")

    private fun subtreeAtiva(
        rootId: Long,
        locais: List<Local>,
    ): List<Long> {
        val filhosPorPai = locais.groupBy { it.parentId }
        val resultado = mutableListOf<Long>()
        val fila = ArrayDeque<Long>().apply { add(rootId) }
        while (fila.isNotEmpty()) {
            val atual = fila.removeFirst()
            val local = locais.firstOrNull { it.id == atual } ?: continue
            if (!local.arquivado) resultado += atual
            filhosPorPai[atual].orEmpty().forEach { filho -> fila.add(filho.id) }
        }
        return resultado
    }
}
