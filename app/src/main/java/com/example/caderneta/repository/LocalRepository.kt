package com.example.caderneta.repository

import android.util.Log
import com.example.caderneta.data.dao.LocalDao
import com.example.caderneta.data.entity.Local
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalRepository(private val localDao: LocalDao) {

    fun getAllLocais(): Flow<List<Local>> = localDao.getAllLocais()

    suspend fun getLocalById(id: Long): Local? = withContext(Dispatchers.IO) {
        localDao.getLocalById(id)
    }

    suspend fun insertLocal(local: Local): Long = withContext(Dispatchers.IO) {
        try {
            val newId = localDao.insertLocal(local)
            Log.d("LocalRepository", "Local inserido com sucesso. ID: $newId")
            newId
        } catch (e: Exception) {
            Log.e("LocalRepository", "Erro ao inserir local", e)
            throw e
        }
    }

    suspend fun updateLocal(local: Local) = withContext(Dispatchers.IO) {
        localDao.updateLocal(local)
    }

    suspend fun deleteLocal(local: Local) = withContext(Dispatchers.IO) {
        localDao.deleteLocal(local)
    }

    suspend fun updateLocalExpansionState(id: Long, isExpanded: Boolean) {
        localDao.updateExpansionState(id, isExpanded)
    }

    suspend fun buscarLocais(query: String): List<Local> {
        return localDao.buscarLocais("%$query%")
    }
}
