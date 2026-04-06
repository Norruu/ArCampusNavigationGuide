package com.campus.arnav.domain.graph

import com.campus.arnav.data.model.CampusLocation
import org.osmdroid.util.GeoPoint
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────────────────────────────────────

enum class NodeSource { HARDCODED, ADMIN, SYNTHETIC }
enum class EdgeSource  { HARDCODED, ADMIN }
enum class RoadType    { MAIN_ROAD, WALKWAY }

// ─────────────────────────────────────────────────────────────────────────────
// NODE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single point in the unified campus graph.
 *
 * @param id          Unique stable identifier.
 * @param location    Geographic position.
 * @param source      Whether this was defined in code or added by an admin at runtime.
 * @param isMerged    True when this node was snapped onto/merged with another node.
 */
data class UnifiedNode(
    val id: String,
    val location: CampusLocation,
    val source: NodeSource = NodeSource.HARDCODED,
    val isMerged: Boolean = false
) {
    /** Convenience GeoPoint for OSMDroid. */
    fun toGeoPoint() = GeoPoint(location.latitude, location.longitude, location.altitude)

    /** Spatial hash key used for deduplication lookup (5-decimal precision ≈ 1 m). */
    val spatialKey: String
        get() = "${location.latitude.toFixed(5)}_${location.longitude.toFixed(5)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// EDGE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A directed connection between two [UnifiedNode]s.
 *
 * The graph is kept bidirectional: every road produces two edges (A→B and B→A).
 */
data class UnifiedEdge(
    val fromId: String,
    val toId: String,
    val distance: Double,         // metres (Haversine)
    val source: EdgeSource = EdgeSource.HARDCODED,
    val roadType: RoadType = RoadType.MAIN_ROAD,
    val isAccessible: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN ROAD INPUT  (mirrors FirestoreRoad / admin panel output)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a road that an admin drew on the map.
 * [roadNodes] is an ordered list of lat/lng waypoints along the road.
 */
data class AdminRoad(
    val id: String,
    val name: String,
    val roadNodes: List<GeoPoint>,
    val roadType: RoadType = RoadType.WALKWAY
)

// ─────────────────────────────────────────────────────────────────────────────
// GRAPH SNAPSHOT  (immutable view passed to pathfinder)
// ─────────────────────────────────────────────────────────────────────────────

data class UnifiedGraphSnapshot(
    val nodes: Map<String, UnifiedNode>,
    val adjacency: Map<String, List<UnifiedEdge>>
)

// ─────────────────────────────────────────────────────────────────────────────
// HELPER EXTENSIONS
// ─────────────────────────────────────────────────────────────────────────────

internal fun Double.toFixed(decimals: Int): String = String.format("%.${decimals}f", this)

internal fun haversine(a: GeoPoint, b: GeoPoint): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(b.latitude  - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(h), sqrt(1 - h))
}

internal fun haversine(a: CampusLocation, b: CampusLocation): Double =
    haversine(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude))