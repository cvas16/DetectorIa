package com.securepass.vision.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.securepass.vision.model.DetectionEvent

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_DETECTIONS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_LABEL TEXT,"
                + "$COLUMN_CONFIDENCE REAL,"
                + "$COLUMN_TIMESTAMP INTEGER,"
                + "$COLUMN_ALERT_LEVEL TEXT"
                + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DETECTIONS")
        onCreate(db)
    }

    fun insertDetection(event: DetectionEvent): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LABEL, event.objectLabel)
            put(COLUMN_CONFIDENCE, event.confidence)
            put(COLUMN_TIMESTAMP, event.timestamp)
            put(COLUMN_ALERT_LEVEL, event.alertLevel)
        }
        return db.insert(TABLE_DETECTIONS, null, values)
    }

    fun getAllDetections(): List<DetectionEvent> {
        val detections = mutableListOf<DetectionEvent>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_DETECTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        with(cursor) {
            while (moveToNext()) {
                detections.add(
                    DetectionEvent(
                        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
                        objectLabel = getString(getColumnIndexOrThrow(COLUMN_LABEL)),
                        confidence = getFloat(getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                        timestamp = getLong(getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        alertLevel = getString(getColumnIndexOrThrow(COLUMN_ALERT_LEVEL))
                    )
                )
            }
            close()
        }
        return detections
    }

    fun deleteDetection(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_DETECTIONS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun clearHistory() {
        val db = this.writableDatabase
        db.delete(TABLE_DETECTIONS, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "vigilante_ai.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_DETECTIONS = "detections"
        private const val COLUMN_ID = "id"
        private const val COLUMN_LABEL = "label"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_ALERT_LEVEL = "alert_level"
    }
}
