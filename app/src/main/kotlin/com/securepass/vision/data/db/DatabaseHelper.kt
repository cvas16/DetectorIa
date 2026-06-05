package com.securepass.vision.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.securepass.vision.model.DetectionEvent

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createDetectionsTable = ("CREATE TABLE $TABLE_DETECTIONS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_LABEL TEXT,"
                + "$COLUMN_CONFIDENCE REAL,"
                + "$COLUMN_TIMESTAMP INTEGER,"
                + "$COLUMN_ALERT_LEVEL TEXT,"
                + "$COLUMN_DETECTION_USER_ID TEXT,"
                + "$COLUMN_DETECTION_USER_NAME TEXT,"
                + "$COLUMN_DETECTION_EVENT_ID INTEGER,"
                + "$COLUMN_DETECTION_EVENT_NAME TEXT"
                + ")")
        db.execSQL(createDetectionsTable)

        val createUsersTable = ("CREATE TABLE $TABLE_USERS ("
                + "$COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_USER_NAME TEXT,"
                + "$COLUMN_USER_USERNAME TEXT UNIQUE,"
                + "$COLUMN_USER_PASSWORD TEXT,"
                + "$COLUMN_USER_LICENSE TEXT,"
                + "$COLUMN_USER_GROUP_ID INTEGER,"
                + "$COLUMN_USER_ROLE TEXT"
                + ")")
        db.execSQL(createUsersTable)

        val createGroupsTable = ("CREATE TABLE $TABLE_GROUPS ("
                + "$COLUMN_GROUP_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_GROUP_NAME TEXT,"
                + "$COLUMN_GROUP_LOCATION TEXT,"
                + "$COLUMN_GROUP_PROHIBITED TEXT"
                + ")")
        db.execSQL(createGroupsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DETECTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GROUPS")
        onCreate(db)
    }

    // --- Métodos para Usuarios ---
    fun insertUser(user: com.securepass.vision.model.User): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            // Usamos el ID de MockAPI para evitar duplicados
            val numericId = user.id.toLongOrNull()
            if (numericId != null) {
                put(COLUMN_USER_ID, numericId)
            }
            put(COLUMN_USER_NAME, user.name)
            put(COLUMN_USER_USERNAME, user.username)
            put(COLUMN_USER_PASSWORD, user.password)
            put(COLUMN_USER_LICENSE, user.licenseKey)
            put(COLUMN_USER_GROUP_ID, user.groupId)
            put(COLUMN_USER_ROLE, user.role)
        }
        return db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateUser(user: com.securepass.vision.model.User): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_NAME, user.name)
            put(COLUMN_USER_USERNAME, user.username)
            put(COLUMN_USER_PASSWORD, user.password)
            put(COLUMN_USER_LICENSE, user.licenseKey)
            put(COLUMN_USER_GROUP_ID, user.groupId)
            put(COLUMN_USER_ROLE, user.role)
        }
        return db.update(TABLE_USERS, values, "$COLUMN_USER_ID = ?", arrayOf(user.id))
    }

    fun updateUserGroup(userId: String, groupId: Long): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_GROUP_ID, groupId)
        }
        return db.update(TABLE_USERS, values, "$COLUMN_USER_ID = ?", arrayOf(userId))
    }

    fun getAllUsers(): List<com.securepass.vision.model.User> {
        val users = mutableListOf<com.securepass.vision.model.User>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, null)
        with(cursor) {
            while (moveToNext()) {
                users.add(com.securepass.vision.model.User(
                    id = getString(getColumnIndexOrThrow(COLUMN_USER_ID)),
                    name = getString(getColumnIndexOrThrow(COLUMN_USER_NAME)),
                    username = getString(getColumnIndexOrThrow(COLUMN_USER_USERNAME)),
                    password = getString(getColumnIndexOrThrow(COLUMN_USER_PASSWORD)),
                    licenseKey = getString(getColumnIndexOrThrow(COLUMN_USER_LICENSE)),
                    groupId = getLong(getColumnIndexOrThrow(COLUMN_USER_GROUP_ID)),
                    role = getString(getColumnIndexOrThrow(COLUMN_USER_ROLE))
                ))
            }
            close()
        }
        return users
    }

    fun deleteUser(userId: String): Int {
        val db = this.writableDatabase
        return db.delete(TABLE_USERS, "$COLUMN_USER_ID = ?", arrayOf(userId))
    }

    // --- Métodos para Grupos/Eventos ---
    fun insertGroup(group: com.securepass.vision.model.SecurityEventGroup): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_GROUP_NAME, group.name)
            put(COLUMN_GROUP_LOCATION, group.location)
            put(COLUMN_GROUP_PROHIBITED, group.prohibitedItems)
        }
        return db.insert(TABLE_GROUPS, null, values)
    }

    fun updateGroup(group: com.securepass.vision.model.SecurityEventGroup): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_GROUP_NAME, group.name)
            put(COLUMN_GROUP_LOCATION, group.location)
            put(COLUMN_GROUP_PROHIBITED, group.prohibitedItems)
        }
        return db.update(TABLE_GROUPS, values, "$COLUMN_GROUP_ID = ?", arrayOf(group.id.toString()))
    }

    fun getAllGroups(): List<com.securepass.vision.model.SecurityEventGroup> {
        val groups = mutableListOf<com.securepass.vision.model.SecurityEventGroup>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_GROUPS, null, null, null, null, null, null)
        with(cursor) {
            while (moveToNext()) {
                groups.add(com.securepass.vision.model.SecurityEventGroup(
                    id = getLong(getColumnIndexOrThrow(COLUMN_GROUP_ID)),
                    name = getString(getColumnIndexOrThrow(COLUMN_GROUP_NAME)),
                    location = getString(getColumnIndexOrThrow(COLUMN_GROUP_LOCATION)),
                    prohibitedItems = getString(getColumnIndexOrThrow(COLUMN_GROUP_PROHIBITED))
                ))
            }
            close()
        }
        return groups
    }

    fun deleteGroup(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_GROUPS, "$COLUMN_GROUP_ID = ?", arrayOf(id.toString()))
    }

    fun getGroupById(id: Long): com.securepass.vision.model.SecurityEventGroup? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_GROUPS, null, "$COLUMN_GROUP_ID = ?", arrayOf(id.toString()), null, null, null)
        var group: com.securepass.vision.model.SecurityEventGroup? = null
        if (cursor.moveToFirst()) {
            group = com.securepass.vision.model.SecurityEventGroup(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GROUP_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GROUP_NAME)),
                location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GROUP_LOCATION)),
                prohibitedItems = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GROUP_PROHIBITED))
            )
        }
        cursor.close()
        return group
    }

    fun getUserByUsername(username: String): com.securepass.vision.model.User? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_USER_USERNAME = ?",
            arrayOf(username),
            null, null, null
        )
        var user: com.securepass.vision.model.User? = null
        if (cursor.moveToFirst()) {
            user = com.securepass.vision.model.User(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_NAME)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_USERNAME)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_PASSWORD)),
                licenseKey = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_LICENSE)),
                groupId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_USER_GROUP_ID)),
                role = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ROLE))
            )
        }
        cursor.close()
        return user
    }

    fun insertDetection(event: DetectionEvent): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LABEL, event.objectLabel)
            put(COLUMN_CONFIDENCE, event.confidence)
            put(COLUMN_TIMESTAMP, event.timestamp)
            put(COLUMN_ALERT_LEVEL, event.alertLevel)
            put(COLUMN_DETECTION_USER_ID, event.userId)
            put(COLUMN_DETECTION_USER_NAME, event.userName)
            put(COLUMN_DETECTION_EVENT_ID, event.eventId)
            put(COLUMN_DETECTION_EVENT_NAME, event.eventName)
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
                        alertLevel = getString(getColumnIndexOrThrow(COLUMN_ALERT_LEVEL)),
                        userId = getString(getColumnIndexOrThrow(COLUMN_DETECTION_USER_ID)),
                        userName = getString(getColumnIndexOrThrow(COLUMN_DETECTION_USER_NAME)),
                        eventId = getLong(getColumnIndexOrThrow(COLUMN_DETECTION_EVENT_ID)),
                        eventName = getString(getColumnIndexOrThrow(COLUMN_DETECTION_EVENT_NAME))
                    )
                )
            }
            close()
        }
        return detections
    }

    fun getDetectionsByUserId(userId: String): List<DetectionEvent> {
        val detections = mutableListOf<DetectionEvent>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_DETECTIONS,
            null,
            "$COLUMN_DETECTION_USER_ID = ?",
            arrayOf(userId),
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
                        alertLevel = getString(getColumnIndexOrThrow(COLUMN_ALERT_LEVEL)),
                        userId = getString(getColumnIndexOrThrow(COLUMN_DETECTION_USER_ID)),
                        userName = getString(getColumnIndexOrThrow(COLUMN_DETECTION_USER_NAME)),
                        eventId = getLong(getColumnIndexOrThrow(COLUMN_DETECTION_EVENT_ID)),
                        eventName = getString(getColumnIndexOrThrow(COLUMN_DETECTION_EVENT_NAME))
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
        private const val DATABASE_VERSION = 5 

        const val TABLE_DETECTIONS = "detections"
        const val COLUMN_ID = "id"
        const val COLUMN_LABEL = "label"
        const val COLUMN_CONFIDENCE = "confidence"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_ALERT_LEVEL = "alert_level"
        const val COLUMN_DETECTION_USER_ID = "d_user_id"
        const val COLUMN_DETECTION_USER_NAME = "d_user_name"
        const val COLUMN_DETECTION_EVENT_ID = "d_event_id"
        const val COLUMN_DETECTION_EVENT_NAME = "d_event_name"

        // Tabla de Usuarios
        const val TABLE_USERS = "users"
        const val COLUMN_USER_ID = "u_id"
        const val COLUMN_USER_NAME = "u_name"
        const val COLUMN_USER_USERNAME = "u_username"
        const val COLUMN_USER_PASSWORD = "u_password"
        const val COLUMN_USER_LICENSE = "u_license"
        const val COLUMN_USER_GROUP_ID = "u_group_id"
        const val COLUMN_USER_ROLE = "u_role"

        // Tabla de Grupos (Eventos)
        const val TABLE_GROUPS = "groups_events"
        const val COLUMN_GROUP_ID = "g_id"
        const val COLUMN_GROUP_NAME = "g_name"
        const val COLUMN_GROUP_LOCATION = "g_location"
        const val COLUMN_GROUP_PROHIBITED = "g_prohibited"
    }
}
