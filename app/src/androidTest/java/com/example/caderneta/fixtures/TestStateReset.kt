package com.example.caderneta.fixtures

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.CadernetaApplication

object TestStateReset {
    fun reset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.cacheDir.deleteRecursively()
        context.filesDir.walkBottomUp().forEach { file ->
            if (file != context.filesDir) {
                file.delete()
            }
        }
        DatabaseFixture.reset(app().container)
    }

    fun app(): CadernetaApplication = ApplicationProvider.getApplicationContext()
}
