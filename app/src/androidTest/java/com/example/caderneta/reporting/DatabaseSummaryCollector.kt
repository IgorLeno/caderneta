package com.example.caderneta.reporting

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.caderneta.AppContainer
import org.json.JSONArray
import org.json.JSONObject

object DatabaseSummaryCollector {
    suspend fun write(
        scenario: String,
        container: AppContainer,
        clienteId: Long? = null,
    ) {
        val scenarioId = ScenarioId.currentOr(scenario)
        val backupDao = container.database.backupDao()
        val contas = backupDao.getAllContas()
        val vendas = backupDao.getAllVendas()
        val saldoHistorico = clienteId?.let { container.database.vendaDao().calcularSaldoHistorico(it) }
        val violations = backupDao.foreignKeyCheck(SimpleSQLiteQuery("PRAGMA foreign_key_check"))
        val json =
            JSONObject()
                .put("scenario", scenarioId)
                .put("scenarioId", scenarioId)
                .put("legacyScenario", scenario)
                .put("locais", backupDao.getAllLocais().size)
                .put("clientes", backupDao.getAllClientes().size)
                .put("configuracoes", container.database.configuracoesDao().countConfiguracoes())
                .put("operacoes", backupDao.getAllOperacoes().size)
                .put("vendas", vendas.size)
                .put("contas", contas.size)
                .put("saldoHistoricoCentavos", saldoHistorico ?: JSONObject.NULL)
                .put(
                    "saldoCache",
                    JSONArray(
                        contas.map { conta ->
                            JSONObject().put("clienteId", conta.clienteId).put("saldoCentavos", conta.saldoCentavos)
                        },
                    ),
                ).put(
                    "foreignKeyViolations",
                    JSONArray(
                        violations.map { violation ->
                            JSONObject()
                                .put("table", violation.table)
                                .put("rowid", violation.rowid)
                                .put("parent", violation.parent)
                                .put("fkid", violation.fkid)
                        },
                    ),
                )
        TestOutput.writeText("database-summary/${scenarioId}_db.json", json.toString(2))
    }
}
