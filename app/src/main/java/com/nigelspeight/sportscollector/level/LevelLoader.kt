package com.nigelspeight.sportscollector.level

import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.StarThreshold
import com.nigelspeight.sportscollector.engine.TileType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class LevelLoaderException(message: String) : Exception(message)

/// Reads a bundled level file by resource name (e.g. "17" -> `Levels/17.txt`),
/// returning null if it doesn't exist. Production reads from app assets; tests
/// read straight from the source tree.
fun interface LevelBundle {
    fun read(resourceName: String): String?
}

/// The on-disk format: a one-element JSON array whose `levelXML` field is itself a
/// JSON string requiring a second decode pass. Other envelope fields (creator name,
/// aggregate win/loss stats, etc.) came from a now-defunct backend and are
/// intentionally not decoded.
object LevelLoader {
    /// Production entry point: an edit-store override wins over the bundled file.
    fun loadLevel(
        resourceName: String,
        level: Int,
        bundle: LevelBundle,
        editStore: LevelEditStore? = null,
    ): Level {
        editStore?.overrideData(resourceName)?.let { return decodeLevel(it, level) }
        val data = bundle.read(resourceName) ?: throw LevelLoaderException("Level resource $resourceName not found")
        return decodeLevel(data, level)
    }

    /// Pure decode entry point (no asset dependency) - used directly by tests.
    fun decodeLevel(data: String, level: Int): Level {
        val envelope = Json.parseToJsonElement(data).jsonArray.firstOrNull()?.jsonObject
            ?: throw LevelLoaderException("Empty level envelope")
        val id = envelope["levelID"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw LevelLoaderException("Missing levelID")
        val payloadString = envelope["levelXML"]?.jsonPrimitive?.content
            ?: throw LevelLoaderException("Missing levelXML")
        val payload = Json.parseToJsonElement(payloadString).jsonObject

        val metaJson = payload.getValue("meta").jsonObject
        val meta = LevelMeta(
            numberofmoves = metaJson.getValue("numberofmoves").jsonPrimitive.int,
            onestarscore = metaJson.getValue("onestarscore").jsonPrimitive.int,
            twostarscore = metaJson.getValue("twostarscore").jsonPrimitive.int,
            threestarscore = metaJson.getValue("threestarscore").jsonPrimitive.int,
            levelgridsize = metaJson.getValue("levelgridsize").jsonPrimitive.int,
            backgroundimagefilename = metaJson.getValue("backgroundimagefilename").jsonPrimitive.content,
        )

        val width = meta.levelgridsize
        val height = meta.levelgridsize
        val cells = MutableList(width * height) { LevelCellSpec.INACTIVE }
        for ((key, value) in payload.getValue("levelgrid").jsonObject) {
            val flatIndex = key.toIntOrNull() ?: continue
            if (flatIndex !in cells.indices) continue
            val raw = value.jsonObject
            // The JSON key is column-major (row = flatIndex % width, col = flatIndex
            // / width) - confirmed empirically against all 100 shipped levels. Remap
            // into standard row-major storage here, once, so every downstream
            // consumer can use plain row-major indexing without re-deriving this.
            //
            // `background`, `wallbelow`, `wallright`, and `exit` are omitted entirely
            // whenever they'd be at their default value - decode leniently.
            val row = flatIndex % width
            val col = flatIndex / width
            cells[row * width + col] = LevelCellSpec(
                isActive = raw.bool("active"),
                hasWallBelow = raw.bool("wallbelow"),
                hasWallRight = raw.bool("wallright"),
                isSpawnPoint = raw.bool("spawn"),
                isExit = raw.bool("exit"),
                hasJelly = raw.int("background", -1) != -1,
                fixedTile = TileType.fromBlockContained(raw.int("blockcontained", -1)),
            )
        }

        val activeBlocks = sortedMapOf<TileType, ActiveBlockSpec>(compareBy { it.rawValue })
        for ((key, value) in payload.getValue("activeblocks").jsonObject) {
            val type = key.toIntOrNull()?.let(TileType::fromBlockContained) ?: continue
            val raw = value.jsonObject
            activeBlocks[type] = ActiveBlockSpec(
                blocksToWin = raw.getValue("blockstowin").jsonPrimitive.int,
                spawnPercentage = raw.getValue("blockspawnpercentage").jsonPrimitive.int,
            )
        }

        return Level(
            mapNumber = level,
            id = id,
            width = width,
            height = height,
            movesAllowed = meta.numberofmoves,
            starThresholds = StarThreshold.resolve(meta),
            backgroundImageFilename = meta.backgroundimagefilename,
            cells = cells,
            activeBlocks = activeBlocks,
        )
    }

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.int(key: String, default: Int): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: default
}

/// Encodes a `Level` back into the same envelope/payload JSON shape `LevelLoader`
/// decodes - the inverse of `LevelLoader.decodeLevel`.
object LevelEncoder {
    /// Precondition: `level.width == level.height` (true of every shipped level;
    /// matching the loader's own `levelgridsize` assumption).
    fun encode(level: Level): String {
        val levelgrid = buildJsonObject {
            for (row in 0 until level.height) {
                for (col in 0 until level.width) {
                    val spec = level.cell(GridPoint(row, col))
                    // Inverse of LevelLoader's `row = flatIndex % width, col = flatIndex / width`.
                    val flatIndex = col * level.width + row
                    put(flatIndex.toString(), buildJsonObject {
                        put("background", if (spec.hasJelly) 0 else -1)
                        put("wallbelow", spec.hasWallBelow)
                        put("wallright", spec.hasWallRight)
                        put("blockcontained", spec.fixedTile?.let { it.rawValue - 1 } ?: -1)
                        put("spawn", spec.isSpawnPoint)
                        put("active", spec.isActive)
                        put("exit", spec.isExit)
                    })
                }
            }
        }
        val activeblocks = buildJsonObject {
            for ((type, spec) in level.activeBlocks) {
                put((type.rawValue - 1).toString(), buildJsonObject {
                    put("blockstowin", spec.blocksToWin)
                    put("blockspawnpercentage", spec.spawnPercentage)
                })
            }
        }
        val meta = buildJsonObject {
            put("numberofmoves", level.movesAllowed)
            put("onestarscore", level.starThresholds.one)
            put("twostarscore", level.starThresholds.two)
            put("threestarscore", level.starThresholds.three)
            put("levelgridsize", level.width)
            put("backgroundimagefilename", level.backgroundImageFilename)
        }
        val payload = buildJsonObject {
            put("meta", meta)
            put("levelgrid", levelgrid)
            put("activeblocks", activeblocks)
        }
        val envelope = buildJsonArray {
            add(buildJsonObject {
                put("levelID", level.id.toString())
                put("levelXML", payload.toString())
            })
        }
        return envelope.toString()
    }
}

/// Persists level editor saves as override files in the app's private storage,
/// keyed by the on-disk resource number `LevelLoader.loadLevel` is actually called
/// with (not the map slot - `LevelOrder` means those are different numbers).
/// `LevelLoader` checks here before falling back to the bundled resource, so a
/// save is picked up by normal gameplay for free.
class LevelEditStore(root: File) {
    private val directory = File(root, "Levels")

    private fun file(resourceName: String) = File(directory, "$resourceName.txt")

    fun hasOverride(resourceName: String): Boolean = file(resourceName).exists()

    fun overrideData(resourceName: String): String? =
        file(resourceName).takeIf { it.exists() }?.readText()

    fun save(level: Level, resourceName: String) {
        directory.mkdirs()
        val target = file(resourceName)
        val temp = File(directory, "$resourceName.txt.tmp")
        temp.writeText(LevelEncoder.encode(level))
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw LevelLoaderException("Could not write ${target.name}")
        }
    }

    fun resetToOriginal(resourceName: String) {
        val target = file(resourceName)
        if (target.exists() && !target.delete()) throw LevelLoaderException("Could not delete ${target.name}")
    }
}
