package com.tripex.pose.data.tiles

import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.projection.WebMercator

/**
 * Minimal Mapbox Vector Tile (MVT) decoder for layers/features/polygon geometry.
 *
 * Decision (M1.7): hand-rolled protobuf wire reader for the subset we need —
 * avoids JTS and extra Maven deps. Only Polygon / MultiPolygon geometries are
 * materialized as [Ring] lists in lng/lat.
 */
internal object MvtDecoder {
    data class Feature(
        val id: Long?,
        val properties: Map<String, String>,
        val rings: List<Ring>,
    )

    data class Layer(
        val name: String,
        val extent: Int,
        val features: List<Feature>,
    )

    fun decode(tileBytes: ByteArray, z: Int, tileX: Int, tileY: Int): List<Layer> {
        val reader = ProtoReader(tileBytes)
        val layers = ArrayList<Layer>()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            when (tag.field) {
                3 -> { // Tile.layers
                    val layerBytes = reader.readBytes()
                    layers += decodeLayer(layerBytes, z, tileX, tileY)
                }
                else -> reader.skip(tag.wireType)
            }
        }
        return layers
    }

    private fun decodeLayer(bytes: ByteArray, z: Int, tileX: Int, tileY: Int): Layer {
        val reader = ProtoReader(bytes)
        var name = ""
        var extent = 4096
        val keys = ArrayList<String>()
        val values = ArrayList<String>()
        val featureBytes = ArrayList<ByteArray>()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> name = reader.readString()
                2 -> featureBytes += reader.readBytes()
                3 -> keys += reader.readString()
                4 -> values += decodeValue(reader.readBytes())
                5 -> extent = reader.readVarint().toInt()
                else -> reader.skip(tag.wireType)
            }
        }
        val features = featureBytes.map { decodeFeature(it, keys, values, z, tileX, tileY, extent) }
        return Layer(name = name, extent = extent, features = features)
    }

    private fun decodeFeature(
        bytes: ByteArray,
        keys: List<String>,
        values: List<String>,
        z: Int,
        tileX: Int,
        tileY: Int,
        extent: Int,
    ): Feature {
        val reader = ProtoReader(bytes)
        var id: Long? = null
        val props = LinkedHashMap<String, String>()
        var geometryType = 0
        var geometryCommands: IntArray? = null
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> id = reader.readVarint()
                2 -> {
                    val tags = reader.readPackedVarints()
                    var i = 0
                    while (i + 1 < tags.size) {
                        val k = tags[i]
                        val v = tags[i + 1]
                        if (k in keys.indices && v in values.indices) {
                            props[keys[k]] = values[v]
                        }
                        i += 2
                    }
                }
                3 -> geometryType = reader.readVarint().toInt()
                4 -> geometryCommands = reader.readPackedVarints()
                else -> reader.skip(tag.wireType)
            }
        }
        val rings = if (geometryType == GEOM_POLYGON && geometryCommands != null) {
            decodePolygonRings(geometryCommands, z, tileX, tileY, extent)
        } else {
            emptyList()
        }
        return Feature(id = id, properties = props, rings = rings)
    }

    private fun decodePolygonRings(
        commands: IntArray,
        z: Int,
        tileX: Int,
        tileY: Int,
        extent: Int,
    ): List<Ring> {
        val rings = ArrayList<Ring>()
        var i = 0
        var cursorX = 0
        var cursorY = 0
        var current = ArrayList<Pair<Double, Double>>()

        fun flush() {
            if (current.size >= 3) {
                if (current.first() != current.last()) {
                    current += current.first()
                }
                rings += current
            }
            current = ArrayList()
        }

        while (i < commands.size) {
            val cmdInt = commands[i++]
            val cmd = cmdInt and 0x7
            val count = cmdInt ushr 3
            when (cmd) {
                CMD_MOVE_TO -> {
                    flush()
                    repeat(count) {
                        cursorX += decodeZigZag(commands[i++])
                        cursorY += decodeZigZag(commands[i++])
                        current += WebMercator.tileLocalToLngLat(z, tileX, tileY, cursorX, cursorY, extent)
                    }
                }
                CMD_LINE_TO -> {
                    repeat(count) {
                        cursorX += decodeZigZag(commands[i++])
                        cursorY += decodeZigZag(commands[i++])
                        current += WebMercator.tileLocalToLngLat(z, tileX, tileY, cursorX, cursorY, extent)
                    }
                }
                CMD_CLOSE_PATH -> {
                    flush()
                }
                else -> error("Unknown MVT command $cmd")
            }
        }
        flush()
        return rings
    }

    private fun decodeValue(bytes: ByteArray): String {
        val reader = ProtoReader(bytes)
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            when (tag.field) {
                1 -> return reader.readString()
                2 -> return reader.readFloat().toString()
                3 -> return reader.readDouble().toString()
                4, 5, 6 -> return reader.readVarint().toString()
                7 -> return (reader.readVarint() != 0L).toString()
                else -> reader.skip(tag.wireType)
            }
        }
        return ""
    }

    private fun decodeZigZag(n: Int): Int = (n ushr 1) xor -(n and 1)

    private const val CMD_MOVE_TO = 1
    private const val CMD_LINE_TO = 2
    private const val CMD_CLOSE_PATH = 7
    private const val GEOM_POLYGON = 3

    private class ProtoReader(private val buf: ByteArray) {
        private var pos = 0

        fun hasRemaining(): Boolean = pos < buf.size

        fun readTag(): Tag {
            val v = readVarint().toInt()
            return Tag(field = v ushr 3, wireType = v and 0x7)
        }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
                check(shift < 64)
            }
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt()
            val out = buf.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun readString(): String = String(readBytes(), Charsets.UTF_8)

        fun readPackedVarints(): IntArray {
            val bytes = readBytes()
            val inner = ProtoReader(bytes)
            val values = ArrayList<Int>()
            while (inner.hasRemaining()) {
                values += inner.readVarint().toInt()
            }
            return values.toIntArray()
        }

        fun readFloat(): Float {
            val bits = (buf[pos].toInt() and 0xff) or
                ((buf[pos + 1].toInt() and 0xff) shl 8) or
                ((buf[pos + 2].toInt() and 0xff) shl 16) or
                ((buf[pos + 3].toInt() and 0xff) shl 24)
            pos += 4
            return Float.fromBits(bits)
        }

        fun readDouble(): Double {
            var bits = 0L
            for (i in 0 until 8) {
                bits = bits or ((buf[pos + i].toLong() and 0xff) shl (8 * i))
            }
            pos += 8
            return Double.fromBits(bits)
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> pos += 8
                2 -> pos += readVarint().toInt()
                5 -> pos += 4
                else -> error("Unsupported wire type $wireType")
            }
        }

        data class Tag(val field: Int, val wireType: Int)
    }
}
