package com.example.caderneta.reporting

import androidx.test.services.storage.TestStorage

object TestOutput {
    private val storage = TestStorage()

    fun writeText(
        path: String,
        content: String,
    ) {
        storage.openOutputFile(path).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    fun writeBytes(
        path: String,
        content: ByteArray,
    ) {
        storage.openOutputFile(path).use { output -> output.write(content) }
    }
}
