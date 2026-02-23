package com.campus.arnav.ui.map.components

import org.osmdroid.util.GeoPoint
import kotlin.math.sqrt

object CampusPaths {

    data class Path(
        val id: String,
        val name: String,
        val points: List<GeoPoint>,
        val type: PathType,
        val bidirectional: Boolean = true
    )

    enum class PathType {
        MAIN_ROAD,
        WALKWAY
    }

    // ─── NODES (every coordinate pinned directly by the user) ────────────────

    private val nodeMainGate            = GeoPoint(9.847738179314176,  122.88731934502138)
    private val nodeRDEC                = GeoPoint(9.847079232227895,  122.88693058463876)
    private val nodeHallwayJunction     = GeoPoint(9.849023816985406,  122.88810074757524)
    private val nodeHallwayCoeJunction     = GeoPoint(9.849096366112185, 122.88791038088114)
    private val nodeHallwayToAdmin      = GeoPoint(9.852492023851111,  122.89027805372855)
    private val nodeAdminJunction       = GeoPoint(9.852820903662368,  122.88976217632987)
    private val nodeJunctionMuseum      = GeoPoint(9.853456238693987,  122.89013391151813)
    private val nodeGymJunction         = GeoPoint(9.853695423330025,  122.88819178482031)
    private val nodeGymToIT             = GeoPoint(9.854375603699514,  122.88858627931438)
    private val nodeMuseumBackAdmin     = GeoPoint(9.853134834079396,  122.89070289397206)
    private val nodeBackAdminCAS        = GeoPoint(9.85361320364412,   122.89080151759343)
    private val nodeCAStoCAF            = GeoPoint(9.854398027208536,  122.89110497488997)
    private val nodeCAF                 = GeoPoint(9.854599838680414,  122.89080151759343)
    private val nodeJavaJunction        = GeoPoint(9.85308998690033,   122.88781246321493)
    private val nodeJava                = GeoPoint(9.853516034885615,  122.88712968429775)
    private val nodeBangkalJunction     = GeoPoint(9.851258721751034,  122.88638621392124)
    private val nodeCOEJunction         = GeoPoint(9.850421568570734,  122.88562757066843)
    private val nodeLukay               = GeoPoint(9.849741380038656,  122.88495237818526)
    private val nodeCornerLeft          = GeoPoint(9.851714670324869,  122.88574136715656)
    private val nodeBackITJunction      = GeoPoint(9.854853971484717,  122.88780487680073)
    private val nodeOssaJunction      = GeoPoint(9.853593883279544, 122.88839783974763)
    private val nodeBackOssaJunction      = GeoPoint(9.854274492868056, 122.88874530840963)

    // ─── PATHS (each segment traced from user pins) ───────────────────────────

    val campusPaths = listOf(

        // 1. Main Gate → Hallway Junction  (entry road going north)
        Path(
            id   = "maingate-hallway",
            name = "MainGate-HallwayJunction",
            points = listOf(nodeMainGate, nodeHallwayJunction),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id   = "cas-to-studenthub",
            name = "CAS-to-StudentHub",
            points = listOf(nodeAdminJunction, nodeOssaJunction),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id   = "studenthub-gym",
            name = "StudentHub-to-Gym",
            points = listOf(nodeOssaJunction, nodeGymJunction),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id   = "ossa-backossa",
            name = "OssaJunction-to-BackOssaJunction",
            points = listOf(nodeOssaJunction, nodeBackOssaJunction),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id   = "backossa-museum",
            name = "BackOssaJunction-to-MuseumJunction",
            points = listOf(nodeBackOssaJunction, nodeJunctionMuseum),
            type = PathType.MAIN_ROAD
        ),

        // This gives the pathfinding engine a direct route from the bottom of the map!
        Path("coe-to-caf", "COE to CAF", listOf(
            nodeHallwayCoeJunction,
            GeoPoint(9.852623906075847, 122.89006538291648),
        ), PathType.WALKWAY),

        // 2. Main Gate → RDEC  (side road from gate going west)
        Path(
            id   = "maingate-rdec",
            name = "MainGate-RDEC",
            points = listOf(nodeMainGate, nodeRDEC),
            type = PathType.WALKWAY
        ),

        // 3. Hallway Junction → Hallway to Admin  (main road curving up to admin)
        Path(
            id   = "hallway-to-admin",
            name = "HallwayJunction-to-Admin",
            points = listOf(nodeHallwayJunction, nodeHallwayToAdmin),
            type = PathType.MAIN_ROAD
        ),

        // 4. Hallway to Admin → Admin Junction  (admin area inner road)
        Path(
            id   = "admin-to-junction",
            name = "Admin-to-AdminJunction",
            points = listOf(nodeHallwayToAdmin, nodeAdminJunction),
            type = PathType.MAIN_ROAD
        ),

        // 5. Admin Junction → Junction to Museum  (road going toward museum)
        Path(
            id   = "adminjunction-to-museum",
            name = "AdminJunction-to-Museum",
            points = listOf(nodeAdminJunction, nodeJunctionMuseum),
            type = PathType.MAIN_ROAD
        ),

        // 6. Admin Junction → Gym Junction  (main horizontal spine road)
        Path(
            id   = "adminjunction-to-gymjunction",
            name = "AdminJunction-to-GymJunction",
            points = listOf(nodeAdminJunction, nodeGymJunction),
            type = PathType.MAIN_ROAD
        ),

        // 7. Gym Junction → Gym-to-IT Junction  (road going north-east toward IT)
        Path(
            id   = "gym-to-itjunction",
            name = "GymJunction-to-ITJunction",
            points = listOf(nodeGymJunction, nodeGymToIT),
            type = PathType.MAIN_ROAD
        ),

        // 8. Gym-to-IT Junction → Admin Junction  (IT road connecting back to admin)
        Path(
            id   = "itjunction-to-adminjunction",
            name = "ITJunction-to-AdminJunction",
            points = listOf(nodeGymToIT, nodeBackOssaJunction),
            type = PathType.MAIN_ROAD
        ),

        // 9. Junction Museum → Museum Back Admin  (museum area road)
        Path(
            id   = "museum-to-backadmin",
            name = "Museum-to-BackAdmin",
            points = listOf(nodeJunctionMuseum, nodeMuseumBackAdmin),
            type = PathType.WALKWAY
        ),

        // 10. Museum Back Admin → Back Admin CAS  (north road toward CAS)
        Path(
            id   = "backadmin-to-cas",
            name = "BackAdmin-to-CAS",
            points = listOf(nodeMuseumBackAdmin, nodeBackAdminCAS),
            type = PathType.WALKWAY
        ),

        // 11. Back Admin CAS → Turn CAF → CAF  (road to cafeteria)
        Path(
            id   = "cas-to-caf",
            name = "CAS-to-CAF",
            points = listOf(nodeBackAdminCAS, nodeCAStoCAF, nodeCAF),
            type = PathType.WALKWAY
        ),

        // 12. Gym Junction → Java Junction  (road going south-west from gym)
        Path(
            id   = "gym-to-javajunction",
            name = "GymJunction-to-JavaJunction",
            points = listOf(nodeGymJunction, nodeJavaJunction),
            type = PathType.MAIN_ROAD
        ),

        // 13. Java Junction → Java  (spur road to Java building)
        Path(
            id   = "javajunction-to-java",
            name = "JavaJunction-to-Java",
            points = listOf(nodeJavaJunction, nodeJava),
            type = PathType.WALKWAY
        ),

        // 14. Java Junction → Bangkal Junction  (road going south)
        Path(
            id   = "javajunction-to-bangkal",
            name = "JavaJunction-to-BangkalJunction",
            points = listOf(nodeJavaJunction, nodeBangkalJunction),
            type = PathType.MAIN_ROAD
        ),

        // 15. Bangkal Junction → COE Junction  (road continuing south)
        Path(
            id   = "bangkal-to-coe",
            name = "BangkalJunction-to-COEJunction",
            points = listOf(nodeBangkalJunction, nodeCOEJunction),
            type = PathType.MAIN_ROAD
        ),

        // 16. COE Junction → Lukay  (road to Lukay building)
        Path(
            id   = "coe-to-lukay",
            name = "COEJunction-to-Lukay",
            points = listOf(nodeCOEJunction, nodeLukay),
            type = PathType.MAIN_ROAD
        ),

        // 17. COE Junction → Hallway Junction  (road connecting COE back to hallway)
        Path(
            id   = "coe-to-hallway",
            name = "COEJunction-to-HallwayJunction",
            points = listOf(nodeCOEJunction, nodeHallwayCoeJunction),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id   = "coe-junction-hallway",
            name = "COEJunction-to-HallwayJunction",
            points = listOf(nodeHallwayCoeJunction, nodeHallwayJunction),
            type = PathType.MAIN_ROAD
        ),

        // 18. Bangkal Junction → Corner Left  (west spur from Bangkal)
        Path(
            id   = "bangkal-to-cornerleft",
            name = "BangkalJunction-to-CornerLeft",
            points = listOf(nodeBangkalJunction, nodeCornerLeft),
            type = PathType.WALKWAY
        ),

        // 19. Corner Left → Java  (road going north from corner back to Java)
        Path(
            id   = "cornerleft-to-java",
            name = "CornerLeft-to-Java",
            points = listOf(nodeCornerLeft, nodeJava),
            type = PathType.WALKWAY
        ),

        // 20. Java → Back IT Junction  (road from Java going north-east)
        Path(
            id   = "java-to-backIT",
            name = "Java-to-BackITJunction",
            points = listOf(nodeJava, nodeBackITJunction),
            type = PathType.WALKWAY
        ),

        // 21. Back IT Junction → Gym-to-IT Junction  (connects back to IT road)
        Path(
            id   = "backIT-to-itjunction",
            name = "BackITJunction-to-ITJunction",
            points = listOf(nodeBackITJunction, nodeGymToIT),
            type = PathType.WALKWAY
        )
    )

    // ─── PATHFINDING ──────────────────────────────────────────────────────────

    fun findPath(start: GeoPoint, end: GeoPoint): List<GeoPoint>? {
        val startPaths = findNearbyPaths(start, 300.0)
        val endPaths   = findNearbyPaths(end,   300.0)

        android.util.Log.d("CampusPaths",
            "Start near ${startPaths.size} paths, End near ${endPaths.size} paths")

        if (startPaths.isEmpty() || endPaths.isEmpty()) {
            android.util.Log.d("CampusPaths", "No nearby paths — straight line fallback")
            return null
        }

        val route = buildRoute(start, end, startPaths, endPaths)
        return if (route != null && route.size >= 2) {
            android.util.Log.d("CampusPaths", "Route built: ${route.size} points")
            route
        } else {
            android.util.Log.d("CampusPaths", "Could not build route")
            null
        }
    }

    private fun findNearbyPaths(point: GeoPoint, radiusMeters: Double): List<PathWithDistance> {
        return campusPaths.mapNotNull { path ->
            val closest = path.points.minByOrNull { calculateDistance(point, it) }
                ?: return@mapNotNull null
            val distance = calculateDistance(point, closest)
            if (distance <= radiusMeters)
                PathWithDistance(path, closest, path.points.indexOf(closest), distance)
            else null
        }.sortedBy { it.distance }
    }

    private fun buildRoute(
        start: GeoPoint, end: GeoPoint,
        startPaths: List<PathWithDistance>, endPaths: List<PathWithDistance>
    ): List<GeoPoint>? {
        val commonPath = startPaths.find { sp ->
            endPaths.any { ep -> ep.path.id == sp.path.id }
        }
        return if (commonPath != null)
            buildSinglePathRoute(start, end, commonPath, endPaths)
        else
            buildMultiPathRoute(start, end, startPaths, endPaths)
    }

    private fun buildSinglePathRoute(
        start: GeoPoint, end: GeoPoint,
        startPathInfo: PathWithDistance, endPaths: List<PathWithDistance>
    ): List<GeoPoint> {
        val path        = startPathInfo.path
        val endPathInfo = endPaths.find { it.path.id == path.id } ?: return emptyList()
        val startIdx    = startPathInfo.pointIndex
        val endIdx      = endPathInfo.pointIndex
        val route       = mutableListOf(start)
        if (startIdx <= endIdx)
            route.addAll(path.points.subList(startIdx, endIdx + 1))
        else if (path.bidirectional)
            route.addAll(path.points.subList(endIdx, startIdx + 1).reversed())
        else return emptyList()
        route.add(end)
        return route
    }

    private fun buildMultiPathRoute(
        start: GeoPoint, end: GeoPoint,
        startPaths: List<PathWithDistance>, endPaths: List<PathWithDistance>
    ): List<GeoPoint>? {
        val startPath = startPaths.firstOrNull() ?: return null
        val endPath   = endPaths.firstOrNull()   ?: return null
        val route     = mutableListOf(start)
        val startIdx  = startPath.pointIndex
        if (startIdx < startPath.path.points.size - 1)
            route.addAll(startPath.path.points.subList(startIdx, startPath.path.points.size))
        val startPathEnd = startPath.path.points.last()
        val endPathStart = endPath.closestPoint
        if (calculateDistance(startPathEnd, endPathStart) > 10.0)
            route.add(endPathStart)
        val endIdx = endPath.pointIndex
        if (endIdx > 0)
            route.addAll(endPath.path.points.subList(0, endIdx + 1))
        route.add(end)
        return route.distinct()
    }

    fun getPathConnections(): List<PathConnection> {
        val connections = mutableListOf<PathConnection>()
        for (i in campusPaths.indices) {
            for (j in i + 1 until campusPaths.size) {
                val p1 = campusPaths[i]; val p2 = campusPaths[j]
                if (calculateDistance(p1.points.last(),  p2.points.first()) < 150.0)
                    connections.add(PathConnection(p1.id, p2.id, p1.points.last(),  "end-to-start"))
                if (calculateDistance(p1.points.last(),  p2.points.last())  < 150.0)
                    connections.add(PathConnection(p1.id, p2.id, p1.points.last(),  "end-to-end"))
                if (calculateDistance(p1.points.first(), p2.points.first()) < 150.0)
                    connections.add(PathConnection(p1.id, p2.id, p1.points.first(), "start-to-start"))
            }
        }
        return connections
    }

    private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(p2.latitude  - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(p1.latitude)) *
                kotlin.math.cos(Math.toRadians(p2.latitude)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadius * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    data class PathWithDistance(
        val path: Path,
        val closestPoint: GeoPoint,
        val pointIndex: Int,
        val distance: Double
    )

    data class PathConnection(
        val path1Id: String,
        val path2Id: String,
        val connectionPoint: GeoPoint,
        val connectionType: String
    )
}