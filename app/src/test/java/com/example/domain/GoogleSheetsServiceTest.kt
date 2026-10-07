package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSheetsServiceTest {

    private val service = GoogleSheetsService()

    @Test
    fun testExtractSpreadsheetIdFromUrl() {
        val url = "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBipkAIA7aeT678jn1dB96gGc/edit#gid=0"
        val expected = "1BxiMVs0XRA5nFMdKvBdBipkAIA7aeT678jn1dB96gGc"
        val actual = service.extractSpreadsheetId(url)
        assertEquals(expected, actual)
    }

    @Test
    fun testExtractSpreadsheetIdFromRawId() {
        val rawId = "1BxiMVs0XRA5nFMdKvBdBipkAIA7aeT678jn1dB96gGc"
        val actual = service.extractSpreadsheetId(rawId)
        assertEquals(rawId, actual)
    }

    @Test
    fun testExtractSpreadsheetIdWithTrailingSlash() {
        val url = "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBipkAIA7aeT678jn1dB96gGc/"
        val expected = "1BxiMVs0XRA5nFMdKvBdBipkAIA7aeT678jn1dB96gGc"
        val actual = service.extractSpreadsheetId(url)
        assertEquals(expected, actual)
    }
}
