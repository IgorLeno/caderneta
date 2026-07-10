package com.example.caderneta.reporting

import android.util.Log

object LogcatCollector {
    private const val TAG = "CadernetaAudit"

    fun mark(
        scenario: String,
        step: String,
    ) {
        Log.i(TAG, "$scenario:$step")
    }
}
