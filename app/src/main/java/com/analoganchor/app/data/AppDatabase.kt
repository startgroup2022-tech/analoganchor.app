package com.analoganchor.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionLog::class,
        ProofMission::class,
        UnlockSession::class,
        RuleFlag::class,
        RecoveryRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun proofMissionDao(): ProofMissionDao
    abstract fun unlockSessionDao(): UnlockSessionDao
    abstract fun ruleFlagDao(): RuleFlagDao
    abstract fun recoveryRecordDao(): RecoveryRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addSessionLogColumnIfMissing(db, "startTime", "INTEGER")
                addSessionLogColumnIfMissing(db, "endTime", "INTEGER")
                addSessionLogColumnIfMissing(db, "duration", "INTEGER")
                addSessionLogColumnIfMissing(db, "nfcTagId", "TEXT")
                addSessionLogColumnIfMissing(db, "photoStatus", "TEXT")
                addSessionLogColumnIfMissing(db, "userNote", "TEXT")
                addSessionLogColumnIfMissing(db, "imagePath", "TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proof_missions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `proofType` TEXT NOT NULL,
                        `imagePath` TEXT,
                        `note` TEXT,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unlock_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `requestedAt` INTEGER NOT NULL,
                        `proofMissionId` INTEGER,
                        `beforeInsightsPath` TEXT,
                        `reviewCardPath` TEXT,
                        `sharedAt` INTEGER,
                        `returnDueAt` INTEGER,
                        `returnProofPath` TEXT,
                        `afterNote` TEXT,
                        `outcome` TEXT NOT NULL,
                        `completedAt` INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rule_flags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `flagType` TEXT NOT NULL,
                        `packageName` TEXT,
                        `severity` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `acknowledged` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recovery_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        `reason` TEXT NOT NULL,
                        `proofImagePath` TEXT,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addSessionLogColumnIfMissing(db, "imagePath", "TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "analog_anchor_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(context: Context): AppDatabase = getDatabase(context)

        private fun addSessionLogColumnIfMissing(
            db: SupportSQLiteDatabase,
            columnName: String,
            definition: String
        ) {
            val cursor = db.query("PRAGMA table_info(`session_logs`)")
            val exists = try {
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) {
                        found = true
                        break
                    }
                }
                found
            } finally {
                cursor.close()
            }

            if (!exists) {
                db.execSQL("ALTER TABLE `session_logs` ADD COLUMN `$columnName` $definition")
            }
        }
    }
}
