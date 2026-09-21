package com.tripex.pose.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations. Discovery data is not reproducible — a user cannot re-walk a year of
 * trips — so `fallbackToDestructiveMigration` must never appear in this project.
 */
internal object Migrations {

    /** Adds the M3.4 area coverage cache. Existing `unlocked_hex` rows are untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `area_stats` (
                    `areaKey` TEXT NOT NULL,
                    `resolution` INTEGER NOT NULL,
                    `cellCount` INTEGER NOT NULL,
                    `boundariesVersion` INTEGER NOT NULL,
                    PRIMARY KEY(`areaKey`)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Adds the macro-scale unlock table (hybrid model). Nothing existing is touched — H3 cells
     * and whole regions coexist from here on.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `unlocked_region` (
                    `adminLevel` TEXT NOT NULL,
                    `featureId` TEXT NOT NULL,
                    `unlockedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`adminLevel`, `featureId`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Adds the geocoder suggestion cache (M4.7). Pure cache — safe to drop, never user data. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `geocode_cache` (
                    `query` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`query`)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Adds circular macro unlocks (searched cities). Replaces rasterising a city into H3, which
     * produced ~770 000 rows for a capital and aborted with "area too large".
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `unlocked_place` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `radiusMeters` REAL NOT NULL,
                    `unlockedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
