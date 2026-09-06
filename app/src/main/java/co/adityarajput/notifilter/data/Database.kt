package co.adityarajput.notifilter.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import co.adityarajput.notifilter.data.models.Filter
import co.adityarajput.notifilter.data.models.Notification

@Database(
    entities = [Filter::class, Notification::class],
    version = 13,
    autoMigrations = [
        AutoMigration(1, 2), AutoMigration(2, 3), AutoMigration(3, 4),
        AutoMigration(4, 5), AutoMigration(5, 6),
        AutoMigration(6, 7, NotiFilterDatabase.DeleteTableAN::class),
        AutoMigration(7, 8), AutoMigration(8, 9), AutoMigration(10, 11),
        AutoMigration(11, 12),
    ],
)
@TypeConverters(Converters::class)
abstract class NotiFilterDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao
    abstract fun notificationDao(): NotificationDao

    @DeleteTable("active_notifications")
    class DeleteTableAN : AutoMigrationSpec

    companion object {
        @Volatile
        private var instance: NotiFilterDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications RENAME COLUMN packageName TO origin")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS filters_new (
                        app_name TEXT NOT NULL,
                        app_packageName TEXT NOT NULL,
                        regexPattern TEXT NOT NULL,
                        action TEXT NOT NULL,
                        regexTarget TEXT NOT NULL,
                        secondaryRegexPattern TEXT,
                        schedule_start INTEGER NOT NULL,
                        schedule_end INTEGER NOT NULL,
                        schedule_days TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        historyEnabled INTEGER NOT NULL,
                        hits INTEGER NOT NULL,
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO filters_new (
                        app_name, app_packageName, regexPattern, action, regexTarget,
                        secondaryRegexPattern, schedule_start, schedule_end, schedule_days,
                        enabled, historyEnabled, hits, id
                    )
                    SELECT 
                        packageName AS app_name,
                        packageName AS app_packageName,
                        queryPattern AS regexPattern,
                        CASE action
                            WHEN 'DISMISS' THEN 'DISMISS'
                            WHEN 'DELAY' THEN 'DELAY'
                            WHEN 'TAP' THEN 
                                CASE 
                                    WHEN buttonPattern IS NOT NULL THEN 'TAP_BUTTON(buttonRegex=' || buttonPattern || ')'
                                    ELSE 'TAP_BUTTON(buttonRegex=)'
                                END
                            WHEN 'BATCH' THEN 
                                CASE 
                                    WHEN batchLengthInHours IS NOT NULL THEN 'BATCH(batchLength=' || batchLengthInHours || ')'
                                    ELSE 'BATCH(batchLength=0)'
                                END
                            ELSE 'DISMISS'
                        END AS action,
                        regexTarget,
                        secondaryQueryPattern AS secondaryRegexPattern,
                        CAST(SUBSTR(activeTime, 1, INSTR(activeTime, ',')-1) AS INTEGER) AS schedule_start,
                        CAST(SUBSTR(activeTime, INSTR(activeTime, ',')+1) AS INTEGER) AS schedule_end,
                        activeDays AS schedule_days, enabled, historyEnabled, hits, id
                    FROM filters
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE filters")
                db.execSQL("ALTER TABLE filters_new RENAME TO filters")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS filters_new (
                        regexPattern TEXT NOT NULL,
                        action TEXT NOT NULL,
                        regexTarget TEXT NOT NULL,
                        secondaryRegexPattern TEXT,
                        enabled INTEGER NOT NULL,
                        historyEnabled INTEGER NOT NULL,
                        widgetEnabled INTEGER NOT NULL DEFAULT 0,
                        priority INTEGER NOT NULL DEFAULT 0,
                        hits INTEGER NOT NULL,
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        app_name TEXT NOT NULL,
                        app_packageName TEXT NOT NULL,
                        schedule TEXT NOT NULL
                    )
                    """.trimIndent(),
                )

                val cursor = db.query(
                    "SELECT id, schedule_start, schedule_end, schedule_days FROM filters"
                )

                val schedules = mutableMapOf<Int, String>()

                cursor.use {
                    val idIndex = it.getColumnIndexOrThrow("id")
                    val startIndex = it.getColumnIndexOrThrow("schedule_start")
                    val endIndex = it.getColumnIndexOrThrow("schedule_end")
                    val daysIndex = it.getColumnIndexOrThrow("schedule_days")

                    while (it.moveToNext()) {
                        val id = it.getInt(idIndex)
                        val start = it.getInt(startIndex)
                        val end = it.getInt(endIndex)
                        val days = it.getString(daysIndex)
                            .split(",")
                            .mapNotNull { day -> day.trim().toIntOrNull() }

                        val rangeJson = when {
                            start < end -> "[{\"start\":$start,\"end\":${end + 1}}]"
                            start > end -> "[{\"start\":0,\"end\":${end + 1}},{\"start\":$start,\"end\":1440}]"
                            else -> "[]"
                        }

                        val rangesJson = days.joinToString(",") { day ->
                            "\"$day\":$rangeJson"
                        }

                        schedules[id] = "{\"ranges\":{$rangesJson}}"
                    }
                }

                db.execSQL(
                    """
                    INSERT INTO filters_new (
                        regexPattern, action, regexTarget, secondaryRegexPattern,
                        enabled, historyEnabled, widgetEnabled, priority, hits, id,
                        app_name, app_packageName, schedule
                    )
                    SELECT
                        regexPattern, action, regexTarget, secondaryRegexPattern,
                        enabled, historyEnabled, widgetEnabled, priority, hits, id,
                        app_name, app_packageName, '{}'
                    FROM filters
                    """.trimIndent(),
                )

                schedules.forEach { (id, schedule) ->
                    db.execSQL(
                        "UPDATE filters_new SET schedule = ? WHERE id = ?",
                        arrayOf(schedule, id),
                    )
                }

                db.execSQL("DROP TABLE filters")
                db.execSQL("ALTER TABLE filters_new RENAME TO filters")
            }
        }

        fun getDatabase(context: Context): NotiFilterDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, NotiFilterDatabase::class.java, "notifilter_database")
                    .addMigrations(MIGRATION_9_10, MIGRATION_12_13)
                    .build().also { instance = it }
            }
        }
    }
}
