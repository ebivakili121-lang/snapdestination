package com.example.snapscanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object BarcodeStore {

    private lateinit var helper: DatabaseHelper

    fun initialize(context: Context) {
        if (!::helper.isInitialized) {
            helper = DatabaseHelper(context.applicationContext)
        }
    }

    // تبدیل اعداد فارسی و عربی به انگلیسی
    fun normalize(barcode: String): String {
        return barcode
            .map { char ->
                when (char) {
                    in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                    in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                    else -> char
                }
            }
            .joinToString("")
            .filter { it.isDigit() }
    }

    // اختصاص شماره مقصد جدید به بارکد
    @Synchronized
    fun assign(barcode: String): Int {
        val db = helper.writableDatabase
        val code = normalize(barcode)

        if (code.isEmpty()) return -1

        val existing = getDestination(code)
        if (existing != null) return existing

        val nextNumber = getMaxDestination(db) + 1

        val values = ContentValues().apply {
            put("barcode", code)
            put("destination", nextNumber)
        }

        db.insertWithOnConflict(
            "barcodes",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return getDestination(code) ?: nextNumber
    }

    // پیدا کردن شماره مقصد بارکد
    @Synchronized
    fun getDestination(barcode: String): Int? {
        val code = normalize(barcode)
        if (code.isEmpty()) return null

        val cursor = helper.readableDatabase.query(
            "barcodes",
            arrayOf("destination"),
            "barcode = ?",
            arrayOf(code),
            null,
            null,
            null
        )

        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else null
        }
    }

    private fun getMaxDestination(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT MAX(destination) FROM barcodes",
            null
        )

        cursor.use {
            return if (it.moveToFirst() && !it.isNull(0)) {
                it.getInt(0)
            } else {
                0
            }
        }
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "snapdestination.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE barcodes (
                    barcode TEXT PRIMARY KEY,
                    destination INTEGER NOT NULL UNIQUE
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            // اطلاعات قبلی حذف نمی‌شوند.
        }
    }
}