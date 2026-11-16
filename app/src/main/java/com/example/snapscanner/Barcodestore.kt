package com.example.snapscanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object BarcodeStore {

    private lateinit var dbHelper: DatabaseHelper
    private val lock = Any()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (!::dbHelper.isInitialized) {
                dbHelper = DatabaseHelper(context.applicationContext)
            }
        }
    }

    fun normalize(value: String): String {
        val result = StringBuilder()

        for (char in value.trim()) {
            val digit = when (char) {
                in '۰'..'۹' -> char.code - '۰'.code
                in '٠'..'٩' -> char.code - '٠'.code
                else -> -1
            }

            if (digit >= 0) {
                result.append(('0'.code + digit).toChar())
            } else {
                result.append(char)
            }
        }

        return result.toString()
    }

    fun getDestination(barcode: String): Int? {
        synchronized(lock) {
            check(::dbHelper.isInitialized) {
                "BarcodeStore is not initialized"
            }

            val code = normalize(barcode)
            val db = dbHelper.readableDatabase

            db.rawQuery(
                "SELECT destination FROM barcodes WHERE barcode = ?",
                arrayOf(code)
            ).use { cursor ->
                return if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    null
                }
            }
        }
    }

    fun assign(barcode: String): Int {
        synchronized(lock) {
            check(::dbHelper.isInitialized) {
                "BarcodeStore is not initialized"
            }

            val code = normalize(barcode)
            val db = dbHelper.writableDatabase

            db.beginTransaction()

            try {
                db.rawQuery(
                    "SELECT destination FROM barcodes WHERE barcode = ?",
                    arrayOf(code)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val existing = cursor.getInt(0)
                        db.setTransactionSuccessful()
                        return existing
                    }
                }

                val nextDestination = db.rawQuery(
                    "SELECT COALESCE(MAX(destination), 0) + 1 FROM barcodes",
                    null
                ).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }

                val values = ContentValues().apply {
                    put("barcode", code)
                    put("destination", nextDestination)
                }

                db.insertOrThrow("barcodes", null, values)
                db.setTransactionSuccessful()

                return nextDestination

            } finally {
                db.endTransaction()
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
