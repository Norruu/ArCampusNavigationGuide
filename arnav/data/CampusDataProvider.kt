package com.campus.arnav.data

import com.campus.arnav.data.model.*
import com.campus.arnav.domain.pathfinding.CampusGraph
import com.campus.arnav.domain.pathfinding.CampusGraphBuilder

/**
 * Example campus data provider
 * Replace with your actual campus coordinates
 */
object CampusDataProvider {

    // Example: Stanford University area (replace with your campus)
    private val CAMPUS_CENTER = CampusLocation(
        id = "campus_center",
        latitude = 37.4275,
        longitude = -122.1697
    )

    fun createSampleCampusGraph(): CampusGraph {
        return CampusGraphBuilder()
            // Add buildings
            .addBuilding(Building(
                id = "library",
                name = "Main Library",
                shortName = "Library",
                description = "Central campus library",
                location = CampusLocation("lib", 9.854278907693391, 122.88931068697471),
                type = BuildingType.LIBRARY,
                entrances = listOf(
                    CampusLocation("lib_entrance_main", 9.854278907693391, 122.88931068697471),
                    CampusLocation("lib_entrance_side", 9.854278907693391, 122.88931068697471)
                )
            ))
            .addBuilding(Building(
                id = "science_hall",
                name = "Science Hall",
                shortName = "Science",
                description = "Science and engineering building",
                location = CampusLocation("sci", 37.4270, -122.1690),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("sci_entrance", 37.4272, -122.1692)
                )
            ))
            .addBuilding(Building(
                id = "student_center",
                name = "Student Center",
                shortName = "Student Ctr",
                description = "Student union and cafeteria",
                location = CampusLocation("stu", 37.4265, -122.1705),
                type = BuildingType.CAFETERIA,
                entrances = listOf(
                    CampusLocation("stu_entrance", 37.4267, -122.1703)
                )
            ))

            // Add outdoor path nodes (walkway intersections)
            .addPathNode("path_1", CampusLocation("p1", 37.4275, -122.1695))
            .addPathNode("path_2", CampusLocation("p2", 37.4275, -122.1702))
            .addPathNode("path_3", CampusLocation("p3", 37.4270, -122.1695))
            .addPathNode("path_4", CampusLocation("p4", 37.4268, -122.1700))

            // Connect paths (outdoor walkways)
            .connectPath("path_1", "path_2")
            .connectPath("path_1", "path_3")
            .connectPath("path_2", "path_4")
            .connectPath("path_3", "path_4")

            // Connect buildings to paths
            .connectBuildingToPath("library", 0, "path_2")
            .connectBuildingToPath("science_hall", 0, "path_3")
            .connectBuildingToPath("student_center", 0, "path_4")

            .build()
    }

    fun getSampleBuildings(): List<Building> = listOf(
        Building(
            id = "library",
            name = "Main Library",
            shortName = "Library",
            description = "Central campus library with study rooms and computer labs",
            location = CampusLocation("lib", 37.4280, -122.1700),
            type = BuildingType.LIBRARY,
            entrances = listOf(
                CampusLocation("lib_entrance_main", 37.4278, -122.1698)
            )
        ),
        Building(
            id = "science_hall",
            name = "Science Hall",
            shortName = "Science",
            description = "Home to Physics, Chemistry, and Biology departments",
            location = CampusLocation("sci", 37.4270, -122.1690),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("sci_entrance", 37.4272, -122.1692)
            )
        ),
        Building(
            id = "student_center",
            name = "Student Center",
            shortName = "Student Ctr",
            description = "Food court, bookstore, and student services",
            location = CampusLocation("stu", 37.4265, -122.1705),
            type = BuildingType.CAFETERIA,
            entrances = listOf(
                CampusLocation("stu_entrance", 37.4267, -122.1703)
            )
        )
    )
}