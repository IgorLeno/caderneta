package com.example.caderneta.reporting

import org.junit.runner.Description

object ScenarioId {
    @Volatile
    private var current: String? = null

    fun start(description: Description): String {
        val className = description.className.substringAfterLast('.')
        val methodName = description.methodName ?: description.displayName
        val scenario = sanitize("$className.$methodName")
        current = scenario
        return scenario
    }

    fun clear() {
        current = null
    }

    fun currentOr(fallback: String): String = current ?: sanitize(fallback)

    fun current(): String = currentOr("unknown")

    fun stepName(
        legacyScenario: String,
        step: String,
    ): String {
        val scenario = current
        return if (scenario != null && legacyScenario != scenario) {
            sanitize("${legacyScenario}_$step")
        } else {
            sanitize(step)
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
