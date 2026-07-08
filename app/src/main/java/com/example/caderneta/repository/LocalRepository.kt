package com.example.caderneta.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.caderneta.data.dao.LocalDao
import com.example.caderneta.data.entity.Local
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalRepository(
    private val localDao: LocalDao,
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
                localDao.updateLocal(local.copy(arquivado = true))
                true
            }
        }

    suspend fun buscarLocais(query: String): List<Local> = localDao.buscarLocais("%$query%")
}
