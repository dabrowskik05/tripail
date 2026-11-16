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

    /**
     * Records how a place was unlocked, so "Cover" can refuse the ones that were earned.
     *
     * Existing rows become `MANUAL`: every place in the table before this migration came from
     * the search bar, because automatic city unlocks are the newer feature. Defaulting them to
     * `AUTO` would freeze choices the player is entitled to undo.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `unlocked_place` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'",
            )
        }
    }

    /**
     * The trail becomes a list of points instead of hexagon cells.
     *
     * Every distinct resolution-9 cell of the old trail turns into one `LEGACY` point at the
     * cell's centre — nothing the player discovered is lost. The old table is left in place on
     * purpose; it is dropped in a later version, once this one has proved itself.
     *
     * @param cellCenter `(latitude, longitude)` of a cell — the one thing SQL cannot compute.
     */
    fun migration6To7(cellCenter: (Long) -> Pair<Double, Double>) = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trail_point` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `cell9` INTEGER NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `mode` TEXT NOT NULL,
                    `segmentId` INTEGER NOT NULL,
                    `parentRes7` INTEGER NOT NULL,
                    `discoveredAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trail_point_cell9` ON `trail_point` (`cell9`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trail_point_parentRes7` ON `trail_point` (`parentRes7`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trail_point_segmentId` ON `trail_point` (`segmentId`)")

            val insert = db.compileStatement(
                "INSERT INTO `trail_point` (`cell9`,`latitude`,`longitude`,`mode`,`segmentId`,`parentRes7`,`discoveredAt`) " +
                    "VALUES (?,?,?,'LEGACY',0,?,?)",
            )
            db.query(
                "SELECT parentRes9, MIN(parentRes7), MIN(discoveredAt) FROM unlocked_hex GROUP BY parentRes9",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val cell = cursor.getLong(0)
                    val (latitude, longitude) = runCatching { cellCenter(cell) }.getOrNull() ?: continue
                    insert.clearBindings()
                    insert.bindLong(1, cell)
                    insert.bindDouble(2, latitude)
                    insert.bindDouble(3, longitude)
                    insert.bindLong(4, cursor.getLong(1))
                    insert.bindLong(5, cursor.getLong(2))
                    insert.executeInsert()
                }
            }
        }
    }

    fun all(cellCenter: (Long) -> Pair<Double, Double>): Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        migration6To7(cellCenter),
    )
}
