package com.example.skydex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCaptureFilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `creates the captures directory and a jpg file inside it`() {
        val file = PhotoCaptureFiles.newCaptureFileIn(tempFolder.root)

        assertTrue("captures directory should exist", file.parentFile!!.isDirectory)
        assertEquals("captures", file.parentFile!!.name)
        assertTrue("expected a .jpg name, got ${file.name}", file.name.endsWith(".jpg"))
    }

    @Test
    fun `never returns the same name twice`() {
        val first = PhotoCaptureFiles.newCaptureFileIn(tempFolder.root)
        val second = PhotoCaptureFiles.newCaptureFileIn(tempFolder.root)

        assertNotEquals(first.name, second.name)
    }
}
