package com.tripex.pose.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hexagon trail must survive the switch to points: losing it is losing the player's history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Exactly as in schemas/…/6.json.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `unlocked_hex` (`h3Index` INTEGER NOT NULL, " +
                                "`parentRes9` INTEGER NOT NULL, `parentRes7` INTEGER NOT NULL, " +
                                "`discoveredAt` INTEGER NOT NULL, `resolution` INTEGER NOT NULL, PRIMARY KEY(`h3Index`))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun hex(index: Long, parent9: Long, discoveredAt: Long) {
        val values = ContentValues().apply {
            put("h3Index", index)
            put("parentRes9", parent9)
            put("parentRes7", parent9 / 10)
            put("discoveredAt", discoveredAt)
            put("resolution", 11)
        }
        db.insert("unlocked_hex", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    @Test
    fun `each old resolution-9 cell becomes one legacy point and the old table stays`() {
        hex(index = 1, parent9 = 900, discoveredAt = 30)
        hex(index = 2, parent9 = 900, discoveredAt = 10)
        hex(index = 3, parent9 = 910, discoveredAt = 20)

        Migrations.migration6To7 { cell -> cell / 100.0 to cell / 10.0 }.migrate(db)

        val rows = ArrayList<List<Any>>()
        db.query(
            "SELECT cell9, latitude, longitude, mode, segmentId, parentRes7, discoveredAt FROM trail_point ORDER BY cell9",
        ).use { c ->
            while (c.moveToNext()) {
                rows += listOf(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getString(3), c.getLong(4), c.getLong(5), c.getLong(6))
            }
        }
        assertEquals(
            listOf(
                listOf(900L, 9.0, 90.0, "LEGACY", 0L, 90L, 10L),
                listOf(910L, 9.1, 91.0, "LEGACY", 0L, 91L, 20L),
            ),
            rows,
        )
        db.query("SELECT COUNT(*) FROM unlocked_hex").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
    }

    @Test
    fun `a cell whose centre cannot be computed is skipped, not fatal`() {
        hex(index = 1, parent9 = 900, discoveredAt = 1)
        hex(index = 2, parent9 = 910, discoveredAt = 1)

        Migrations.migration6To7 { cell -> if (cell == 900L) error("bad cell") else 1.0 to 2.0 }.migrate(db)

        db.query("SELECT cell9 FROM trail_point").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(910L, c.getLong(0))
        }
    }
}
