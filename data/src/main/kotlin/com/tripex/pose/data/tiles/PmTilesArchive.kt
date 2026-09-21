package com.tripex.pose.data.tiles

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/**
 * Minimal PMTiles v3 reader for local files (byte-range via [RandomAccessFile]).
 *
 * Decision (M1.7): hand-rolled reader instead of a third-party PMTiles SDK —
 * we only need header + directory + gunzip tile fetch; keeps `:data` free of
 * MapLibre / JTS. Hilbert tile IDs match the Protomaps reference implementation.
 */
internal class PmTilesArchive(
    private val file: File,
) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    val header: Header = readHeader()

    data class Header(
        val rootDirectoryOffset: Long,
        val rootDirectoryLength: Long,
        val leafDirectoryOffset: Long,
        val leafDirectoryLength: Long,
        val tileDataOffset: Long,
        val internalCompression: Int,
        val tileCompression: Int,
        val minZoom: Int,
        val maxZoom: Int,
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double,
    )

    data class Entry(
        val tileId: Long,
        val offset: Long,
        val length: Int,
        val runLength: Int,
    )

    fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        val tileId = zxyToTileId(z, x, y)
        val root = rootDirectory()
        val entry = findTile(root, tileId) ?: return null
        if (entry.runLength == 0) {
            val leafBytes = decompress(
                readBytes(header.leafDirectoryOffset + entry.offset, entry.length),
                header.internalCompression,
            )
            val leafEntry = findTile(deserializeDirectory(leafBytes), tileId) ?: return null
            return readTileBytes(leafEntry)
        }
        return readTileBytes(entry)
    }

    private fun rootDirectory(): List<Entry> {
        val bytes = decompress(
            readBytes(header.rootDirectoryOffset, header.rootDirectoryLength.toInt()),
            header.internalCompression,
        )
        return deserializeDirectory(bytes)
    }

    private fun readTileBytes(entry: Entry): ByteArray {
        val raw = readBytes(header.tileDataOffset + entry.offset, entry.length)
        return decompress(raw, header.tileCompression)
    }

    private fun readHeader(): Header {
        val bytes = readBytes(0, HEADER_SIZE)
        val v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(7).also { v.get(it) }
        require(String(magic) == "PMTiles") { "Not a PMTiles archive" }
        val version = v.get().toInt() and 0xff
        require(version == 3) { "Unsupported PMTiles version: $version" }
        val rootDirectoryOffset = v.long
        val rootDirectoryLength = v.long
        v.long // jsonMetadataOffset
        v.long // jsonMetadataLength
        val leafDirectoryOffset = v.long
        val leafDirectoryLength = v.long
        val tileDataOffset = v.long
        v.long // tileDataLength
        v.long // numAddressedTiles
        v.long // numTileEntries
        v.long // numTileContents
        v.get() // clustered
        val internalCompression = v.get().toInt() and 0xff
        val tileCompression = v.get().toInt() and 0xff
        v.get() // tileType
        val minZoom = v.get().toInt() and 0xff
        val maxZoom = v.get().toInt() and 0xff
        val minLon = v.int / 10_000_000.0
        val minLat = v.int / 10_000_000.0
        val maxLon = v.int / 10_000_000.0
        val maxLat = v.int / 10_000_000.0
        return Header(
            rootDirectoryOffset = rootDirectoryOffset,
            rootDirectoryLength = rootDirectoryLength,
            leafDirectoryOffset = leafDirectoryOffset,
            leafDirectoryLength = leafDirectoryLength,
            tileDataOffset = tileDataOffset,
            internalCompression = internalCompression,
            tileCompression = tileCompression,
            minZoom = minZoom,
            maxZoom = maxZoom,
            minLon = minLon,
            minLat = minLat,
            maxLon = maxLon,
            maxLat = maxLat,
        )
    }

    private fun readBytes(offset: Long, length: Int): ByteArray {
        require(length >= 0) { "negative length" }
        val out = ByteArray(length)
        raf.seek(offset)
        raf.readFully(out)
        return out
    }

    override fun close() {
        raf.close()
    }

    companion object {
        private const val HEADER_SIZE = 127
        private const val COMPRESSION_NONE = 1
        private const val COMPRESSION_GZIP = 2

        fun zxyToTileId(z: Int, x: Int, y: Int): Long {
            require(z in 0..26)
            require(x in 0 until (1 shl z) && y in 0 until (1 shl z))
            var acc = ((1L shl z) * (1L shl z) - 1) / 3
            var a = z - 1
            var tx = x
            var ty = y
            var s = 1 shl a
            while (s > 0) {
                val rx = tx and s
                val ry = ty and s
                acc += ((3L * rx) xor ry.toLong()) * (1L shl a)
                val rotated = rotate(s, tx, ty, rx, ry)
                tx = rotated.first
                ty = rotated.second
                a--
                s = s shr 1
            }
            return acc
        }

        fun findTile(entries: List<Entry>, tileId: Long): Entry? {
            var m = 0
            var n = entries.lastIndex
            while (m <= n) {
                val k = (n + m) ushr 1
                val cmp = tileId - entries[k].tileId
                when {
                    cmp > 0 -> m = k + 1
                    cmp < 0 -> n = k - 1
                    else -> return entries[k]
                }
            }
            if (n >= 0) {
                val entry = entries[n]
                if (entry.runLength == 0) return entry
                if (tileId - entry.tileId < entry.runLength) return entry
            }
            return null
        }

        fun deserializeDirectory(bytes: ByteArray): List<Entry> {
            val p = VarintReader(bytes)
            val numEntries = p.readVarint().toInt()
            val entries = ArrayList<Entry>(numEntries)
            var lastId = 0L
            repeat(numEntries) {
                val delta = p.readVarint()
                lastId += delta
                entries += Entry(tileId = lastId, offset = 0, length = 0, runLength = 1)
            }
            repeat(numEntries) { i -> entries[i] = entries[i].copy(runLength = p.readVarint().toInt()) }
            repeat(numEntries) { i -> entries[i] = entries[i].copy(length = p.readVarint().toInt()) }
            repeat(numEntries) { i ->
                val v = p.readVarint()
                val offset = if (v == 0L && i > 0) {
                    entries[i - 1].offset + entries[i - 1].length
                } else {
                    v - 1
                }
                entries[i] = entries[i].copy(offset = offset)
            }
            return entries
        }

        fun decompress(bytes: ByteArray, compression: Int): ByteArray = when (compression) {
            COMPRESSION_NONE, 0 -> bytes
            COMPRESSION_GZIP -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            else -> error("Unsupported PMTiles compression: $compression")
        }

        private fun rotate(n: Int, x: Int, y: Int, rx: Int, ry: Int): Pair<Int, Int> {
            if (ry == 0) {
                return if (rx != 0) (n - 1 - y) to (n - 1 - x) else y to x
            }
            return x to y
        }
    }

    private class VarintReader(private val buf: ByteArray) {
        private var pos = 0

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
                check(shift < 64) { "varint too long" }
            }
        }
    }
}
