package com.campus.arnav.domain.graph

import android.util.Log
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.ui.map.components.CampusPaths
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * UnifiedGraphManager — fixed v2
 *
 * Bugs fixed vs. v1
 * ─────────────────
 * 1. findNearbyNode: spatial-cell loop was a dead stub that computed keys
 *    but never looked them up in spatialIndex.  Now actually reads the map.
 * 2. buildFromHardcodedPaths: added isBuilt guard so a concurrent second
 *    call (MapViewModel racing with FirestoreSyncManager) is a no-op and
 *    does NOT wipe admin roads already merged in.
 * 3. injectNodeOntoNearbyEdge: visited-key was asymmetric ("a_b" vs "b_a")
 *    causing duplicate edge splits.  Now uses canonical min|max key.
 */
@Singleton
class UnifiedGraphManager @Inject constructor() {

    companion object {
        private const val TAG = "UnifiedGraphManager"
        const val MERGE_THRESHOLD_METRES      = 12.0
        const val EDGE_SNAP_THRESHOLD_METRES  = 15.0
        private const val GRID_STEP           = 0.0001  // ≈ 11 m per cell
    }

    private val nodes     = mutableMapOf<String, UnifiedNode>()
    private val adjacency = mutableMapOf<String, MutableList<UnifiedEdge>>()

    /**
     * Spatial index: rounded "lat5dp_lon5dp" → nodeId.
     * Gives O(9) fast-path lookup before the O(n) scan.
     */
    private val spatialIndex     = mutableMapOf<String, String>()
    private val adminRoadIds     = mutableSetOf<String>()
    private val adminNodesByRoad = mutableMapOf<String, MutableSet<String>>()
    private val listeners        = mutableListOf<GraphChangeListener>()

    /**
     * Guard against double-build race between FirestoreSyncManager and
     * MapViewModel both calling buildFromHardcodedPaths().
     */
    @Volatile private var isBuilt = false
    val isReady: Boolean get() = isBuilt

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build the base graph from hardcoded [CampusPaths].
     * Second call is a no-op — admin roads already merged are preserved.
     * Call [reset] first if you genuinely need a full rebuild.
     */
    @Synchronized
    fun buildFromHardcodedPaths() {
        if (isBuilt) {
            Log.d(TAG, "Already built — skipping rebuild (admin roads preserved)")
            return
        }
        nodes.clear(); adjacency.clear(); spatialIndex.clear()
        adminRoadIds.clear(); adminNodesByRoad.clear()

        CampusPaths.campusPaths.forEach { path ->
            ingestPolyline(
                roadId   = path.id,
                points   = path.points,
                source   = NodeSource.HARDCODED,
                edgeSrc  = EdgeSource.HARDCODED,
                roadType = if (path.type == CampusPaths.PathType.MAIN_ROAD)
                    RoadType.MAIN_ROAD else RoadType.WALKWAY
            )
        }

        isBuilt = true
        Log.i(TAG, "Hardcoded graph ready — ${nodes.size} nodes, ${edgeCount()} edges")
        notifyListeners()
    }

    /** Force full rebuild on next [buildFromHardcodedPaths] call. */
    @Synchronized
    fun reset() {
        isBuilt = false
        nodes.clear(); adjacency.clear(); spatialIndex.clear()
        adminRoadIds.clear(); adminNodesByRoad.clear()
    }

    /**
     * Merge an admin-drawn road into the graph at runtime.
     *
     * Each waypoint either snaps onto a nearby hardcoded node (merge) or
     * becomes a new ADMIN node.  New ADMIN nodes that land close to an
     * existing hardcoded edge are injected onto that edge so the admin road
     * joins the main network automatically.
     */
    @Synchronized
    fun addAdminRoad(road: AdminRoad) {
        if (!isBuilt) {
            Log.w(TAG, "Graph not yet built — call buildFromHardcodedPaths() first. " +
                    "Road '${road.id}' skipped.")
            return
        }

        removeAdminRoadInternal(road.id)   // idempotent replace

        if (road.roadNodes.size < 2) {
            Log.w(TAG, "Admin road '${road.id}' has < 2 points — skipped")
            return
        }

        val newNodeIds  = mutableSetOf<String>()
        val insertedIds = ingestPolyline(
            roadId        = road.id,
            points        = road.roadNodes,
            source        = NodeSource.ADMIN,
            edgeSrc       = EdgeSource.ADMIN,
            roadType      = road.roadType,
            trackNewNodes = newNodeIds
        )

        // Snap purely-new ADMIN nodes onto nearby hardcoded edges
        newNodeIds.forEach { id ->
            val node = nodes[id] ?: return@forEach
            if (node.source == NodeSource.ADMIN) {
                injectNodeOntoNearbyEdge(node, excludeEdgeSource = EdgeSource.ADMIN)
            }
        }

        adminRoadIds.add(road.id)
        adminNodesByRoad[road.id] = insertedIds.toMutableSet()
        Log.i(TAG, "Admin road '${road.name}' added — ${nodes.size} nodes, ${edgeCount()} edges")
        notifyListeners()
    }

    @Synchronized
    fun removeAdminRoad(roadId: String) {
        removeAdminRoadInternal(roadId)
        notifyListeners()
    }

    @Synchronized
    fun snapshot() = UnifiedGraphSnapshot(
        nodes     = nodes.toMap(),
        adjacency = adjacency.mapValues { it.value.toList() }
    )

    fun addListener(l: GraphChangeListener)    { listeners.add(l)    }
    fun removeListener(l: GraphChangeListener) { listeners.remove(l) }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: road ingestion
    // ─────────────────────────────────────────────────────────────────────────

    private fun ingestPolyline(
        roadId: String,
        points: List<GeoPoint>,
        source: NodeSource,
        edgeSrc: EdgeSource,
        roadType: RoadType,
        trackNewNodes: MutableSet<String>? = null
    ): Set<String> {
        val segIds = mutableListOf<String>()

        points.forEach { geo ->
            val loc = CampusLocation("", geo.latitude, geo.longitude, geo.altitude)
            val id  = findOrCreateNode(loc, source)
            segIds.add(id)
            trackNewNodes?.add(id)
        }

        for (i in 0 until segIds.lastIndex) {
            val aId = segIds[i]; val bId = segIds[i + 1]
            if (aId == bId) continue
            val a = nodes[aId] ?: continue
            val b = nodes[bId] ?: continue
            addBidirectionalEdge(aId, bId, haversine(a.location, b.location), edgeSrc, roadType)
        }

        return segIds.toSet()
    }

    private fun removeAdminRoadInternal(roadId: String) {
        if (roadId !in adminRoadIds) return
        val owned = adminNodesByRoad[roadId] ?: return

        owned.forEach { id -> adjacency[id]?.removeAll { it.source == EdgeSource.ADMIN } }
        adjacency.values.forEach { list ->
            list.removeAll { it.toId in owned && it.source == EdgeSource.ADMIN }
        }
        owned.forEach { id ->
            val n = nodes[id] ?: return@forEach
            if (n.source == NodeSource.ADMIN) {
                nodes.remove(id); adjacency.remove(id); spatialIndex.remove(n.spatialKey)
            }
        }
        adminRoadIds.remove(roadId); adminNodesByRoad.remove(roadId)
        Log.i(TAG, "Admin road '$roadId' removed — ${nodes.size} nodes, ${edgeCount()} edges")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: node management
    // ─────────────────────────────────────────────────────────────────────────

    private fun findOrCreateNode(loc: CampusLocation, source: NodeSource): String {
        val existing = findNearbyNode(loc)
        if (existing != null) {
            val node = nodes[existing]!!
            if (node.source != source && node.source != NodeSource.SYNTHETIC)
                nodes[existing] = node.copy(source = NodeSource.SYNTHETIC, isMerged = true)
            return existing
        }

        val id   = generateNodeId(loc, source)
        val node = UnifiedNode(id = id, location = loc.copy(id = id), source = source)
        nodes[id] = node
        adjacency[id] = mutableListOf()
        spatialIndex[node.spatialKey] = id          // register in spatial index
        return id
    }

    /**
     * FIX: previously the 3×3 loop computed `key` but never read from
     * spatialIndex — the variable was computed and immediately discarded.
     * Now we do: spatialIndex[key] ?: continue — actually reading the map.
     */
    private fun findNearbyNode(loc: CampusLocation): String? {
        // Fast path — O(9) spatial-index lookup
        for (dLat in listOf(-GRID_STEP, 0.0, GRID_STEP)) {
            for (dLon in listOf(-GRID_STEP, 0.0, GRID_STEP)) {
                val key = "${(loc.latitude + dLat).toFixed(5)}_" +
                        "${(loc.longitude + dLon).toFixed(5)}"
                val candidateId = spatialIndex[key] ?: continue     // ← THE FIX
                val candidate   = nodes[candidateId] ?: continue
                if (haversine(loc, candidate.location) <= MERGE_THRESHOLD_METRES)
                    return candidateId
            }
        }

        // Slow path — linear scan for nodes near grid-cell boundaries
        for ((_, node) in nodes) {
            if (haversine(loc, node.location) <= MERGE_THRESHOLD_METRES)
                return node.id
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal: edge injection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX: old visited key was "${fromId}_${toId}" which is asymmetric —
     * "a_b" and "b_a" are different strings, so the same undirected edge was
     * tested twice and could be split twice.
     * Now uses canonical "${min}|${max}" so each undirected edge is visited once.
     */
    private fun injectNodeOntoNearbyEdge(
        newNode: UnifiedNode,
        excludeEdgeSource: EdgeSource
    ) {
        val visited = mutableSetOf<String>()

        outer@ for ((fromId, edges) in adjacency.toMap()) {
            val fromNode = nodes[fromId] ?: continue
            for (edge in edges.toList()) {
                if (edge.source == excludeEdgeSource) continue

                val key = "${minOf(fromId, edge.toId)}|${maxOf(fromId, edge.toId)}"  // ← FIX
                if (!visited.add(key)) continue

                val toNode   = nodes[edge.toId] ?: continue
                val proj     = projectOnSegment(newNode.location, fromNode.location, toNode.location)
                val snapDist = haversine(newNode.location, proj)

                if (snapDist <= EDGE_SNAP_THRESHOLD_METRES) {
                    adjacency[fromId]?.removeAll   { it.toId == edge.toId && it.source == edge.source }
                    adjacency[edge.toId]?.removeAll { it.toId == fromId   && it.source == edge.source }

                    val dA = haversine(fromNode.location, newNode.location)
                    val dB = haversine(newNode.location,  toNode.location)
                    addBidirectionalEdge(fromId,     newNode.id, dA, edge.source, edge.roadType)
                    addBidirectionalEdge(newNode.id, edge.toId,  dB, edge.source, edge.roadType)

                    Log.d(TAG, "Injected '${newNode.id}' onto $fromId→${edge.toId} (snap=${snapDist.toInt()}m)")
                    break@outer
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun addBidirectionalEdge(
        fromId: String, toId: String, dist: Double,
        src: EdgeSource, roadType: RoadType, accessible: Boolean = true
    ) {
        fun addIfAbsent(list: MutableList<UnifiedEdge>, e: UnifiedEdge) {
            if (list.none { it.toId == e.toId && it.source == e.source }) list.add(e)
        }
        adjacency.getOrPut(fromId) { mutableListOf() }
            .let { addIfAbsent(it, UnifiedEdge(fromId, toId,   dist, src, roadType, accessible)) }
        adjacency.getOrPut(toId)   { mutableListOf() }
            .let { addIfAbsent(it, UnifiedEdge(toId,   fromId, dist, src, roadType, accessible)) }
    }

    private fun projectOnSegment(p: CampusLocation, a: CampusLocation, b: CampusLocation): CampusLocation {
        val cosLat = cos(Math.toRadians(a.latitude))
        val px = (p.longitude - a.longitude) * cosLat;  val py = p.latitude  - a.latitude
        val bx = (b.longitude - a.longitude) * cosLat;  val by = b.latitude  - a.latitude
        val lenSq = bx * bx + by * by
        if (lenSq == 0.0) return a
        val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
        return CampusLocation("proj",
            a.latitude  + t * (b.latitude  - a.latitude),
            a.longitude + t * (b.longitude - a.longitude))
    }

    private fun generateNodeId(loc: CampusLocation, source: NodeSource) =
        "${when(source){ NodeSource.HARDCODED->"hc"; NodeSource.ADMIN->"adm"; NodeSource.SYNTHETIC->"syn" }}" +
                "_${loc.latitude.toFixed(6)}_${loc.longitude.toFixed(6)}"

    private fun edgeCount() = adjacency.values.sumOf { it.size }
    private fun notifyListeners() = listeners.forEach { it.onGraphChanged(snapshot()) }

    // Debug helpers
    @Synchronized fun nodesBy(source: NodeSource? = null) =
        if (source == null) nodes.values.toList() else nodes.values.filter { it.source == source }
}

fun interface GraphChangeListener {
    fun onGraphChanged(snapshot: UnifiedGraphSnapshot)
}