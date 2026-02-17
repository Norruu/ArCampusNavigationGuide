package com.campus.arnav.data

import com.campus.arnav.data.model.*
import com.campus.arnav.domain.pathfinding.CampusGraph
import com.campus.arnav.domain.pathfinding.CampusGraphBuilder

/**
 * Campus Data Provider with real campus coordinates
 */
object CampusDataProvider {

    // Campus center (New Administrative Building - ADMIN)
    private val CAMPUS_CENTER = CampusLocation(
        id = "campus_center",
        latitude = 9.85282437919445,
        longitude = 122.89086729646579
    )

    fun createSampleCampusGraph(): CampusGraph {
        return CampusGraphBuilder()
            // --- BUILDINGS ---
            .addBuilding(Building(
                id = "library",
                name = "Library",
                shortName = "Library",
                description = "Main library",
                location = CampusLocation("lib", 9.854278907693391, 122.88931068697471),
                type = BuildingType.LIBRARY,
                entrances = listOf(
                    CampusLocation("lib_entrance_main", 9.854278907693391, 122.88931068697471)
                )
            ))
            .addBuilding(Building(
                id = "cas",
                name = "CAS",
                shortName = "CAS",
                description = "College of Arts and Sciences",
                location = CampusLocation("cas", 9.853393750047964, 122.88944237254098),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("cas_entrance", 9.853393750047964, 122.88944237254098)
                )
            ))
            .addBuilding(Building(
                id = "coe",
                name = "COE",
                shortName = "COE",
                description = "College of Engineering",
                location = CampusLocation("coe", 9.849504528210602, 122.88779694312122),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("coe_entrance", 9.849504528210602, 122.88779694312122)
                )
            ))
            .addBuilding(Building(
                id = "coted",
                name = "COTED",
                shortName = "COTED",
                description = "College of Teacher Education",
                location = CampusLocation("coted", 9.853941591408363, 122.89044466095176),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("coted_entrance", 9.853941591408363, 122.89044466095176)
                )
            ))
            .addBuilding(Building(
                id = "caf",
                name = "CAF",
                shortName = "CAF",
                description = "College of Agriculture and Forestry",
                location = CampusLocation("caf", 9.850113664121954, 122.88818988673896),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("caf_entrance", 9.850113664121954, 122.88818988673896)
                )
            ))
            .addBuilding(Building(
                id = "crim",
                name = "CRIM",
                shortName = "CRIM",
                description = "College of Criminal Justice",
                location = CampusLocation("crim", 9.850355468306018, 122.88925472370697),
                type = BuildingType.ACADEMIC,
                entrances = listOf(
                    CampusLocation("crim_entrance", 9.850355468306018, 122.88925472370697)
                )
            ))
            .addBuilding(Building(
                id = "admin_new",
                name = "Admin Building",
                shortName = "Admin",
                description = "New Administrative Building",
                location = CampusLocation("admin_new", 9.85282437919445, 122.89086729646579),
                type = BuildingType.ADMINISTRATIVE,
                entrances = listOf(
                    CampusLocation("admin_entrance", 9.85282437919445, 122.89086729646579)
                )
            ))
            .addBuilding(Building(
                id = "admin_old",
                name = "Admin Building (Old)",
                shortName = "Admin",
                description = "Administrative offices",
                location = CampusLocation("admin_old", 9.852863662387657, 122.89044785066042),
                type = BuildingType.ADMINISTRATIVE,
                entrances = listOf(
                    CampusLocation("admin_old_entrance", 9.852863662387657, 122.89044785066042)
                )
            ))
            .addBuilding(Building(
                id = "registrar",
                name = "Registrar's Office",
                shortName = "Registrar",
                description = "Registrar's Office and Student Records",
                location = CampusLocation("registrar", 9.853254215951052, 122.88982102069603),
                type = BuildingType.ADMINISTRATIVE,
                entrances = listOf(
                    CampusLocation("registrar_entrance", 9.853254215951052, 122.88982102069603)
                )
            ))
            .addBuilding(Building(
                id = "student_hub",
                name = "Students Hub",
                shortName = "Hub",
                description = "Students Lounge",
                location = CampusLocation("hub", 9.853590668411169, 122.88866477231646),
                type = BuildingType.CAFETERIA,
                entrances = listOf(
                    CampusLocation("hub_entrance", 9.853590668411169, 122.88866477231646)
                )
            ))
            .addBuilding(Building(
                id = "cafeteria",
                name = "Cafeteria",
                shortName = "Cafeteria",
                description = "Canteens and Cafeteria",
                location = CampusLocation("cafeteria", 9.853325632397336, 122.89097574633368),
                type = BuildingType.CAFETERIA,
                entrances = listOf(
                    CampusLocation("cafeteria_entrance", 9.853325632397336, 122.89097574633368)
                )
            ))
            .addBuilding(Building(
                id = "gym",
                name = "Gymnasium",
                shortName = "Gym",
                description = "Gymnasium",
                location = CampusLocation("gym", 9.853530434736115, 122.88780706137088),
                type = BuildingType.SPORTS,
                entrances = listOf(
                    CampusLocation("gym_entrance", 9.853530434736115, 122.88780706137088)
                )
            ))
            .addBuilding(Building(
                id = "swimming_pool",
                name = "Swimming Pool",
                shortName = "Pool",
                description = "Swimming Pool",
                location = CampusLocation("pool", 9.853471765633156, 122.89153553902976),
                type = BuildingType.SPORTS,
                entrances = listOf(
                    CampusLocation("pool_entrance", 9.853471765633156, 122.89153553902976)
                )
            ))
            .addBuilding(Building(
                id = "clinic",
                name = "Clinic",
                shortName = "Clinic",
                description = "Clinic",
                location = CampusLocation("clinic", 9.851165444957411, 122.88894090526546),
                type = BuildingType.ADMINISTRATIVE,
                entrances = listOf(
                    CampusLocation("clinic_entrance", 9.851165444957411, 122.88894090526546)
                )
            ))
            .addBuilding(Building(
                id = "museum",
                name = "Museum",
                shortName = "Museum",
                description = "Museum",
                location = CampusLocation("museum", 9.853806457644907, 122.89035534929693),
                type = BuildingType.LANDMARK,
                entrances = listOf(
                    CampusLocation("museum_entrance", 9.853806457644907, 122.89035534929693)
                )
            ))

            // --- GATES (CRITICAL FOR HYBRID NAV) ---
            // Replaced 'addPathNode' with 'addGate' so the system knows this is an entrance
            .addGate("main_gate", CampusLocation("main_gate", 9.847875487714289, 122.88741459467961))
            .addGate("house_gate", CampusLocation("House_gate", 9.991429451735037, 122.81142868461329))

            // --- PATH INTERSECTIONS ---
            .addPathNode("path_center", CampusLocation("path_center", 9.852824, 122.889867))
            .addPathNode("path_north", CampusLocation("path_north", 9.854278, 122.889310))
            .addPathNode("path_south", CampusLocation("path_south", 9.850113, 122.888189))
            .addPathNode("path_east", CampusLocation("path_east", 9.853325, 122.890975))
            .addPathNode("path_west", CampusLocation("path_west", 9.853530, 122.887807))

            // --- CONNECTIONS ---
            // Connect main pathways
            .connectPath("main_gate", "path_south")
            .connectPath("house_gate", "path_south")// Connect the Gate to the internal road
            .connectPath("path_south", "path_center")
            .connectPath("path_center", "path_north")
            .connectPath("path_center", "path_east")
            .connectPath("path_center", "path_west")

            // Connect buildings to nearest paths
            .connectBuildingToPath("library", 0, "path_north")
            .connectBuildingToPath("cas", 0, "path_center")
            .connectBuildingToPath("coe", 0, "path_south")
            .connectBuildingToPath("admin_new", 0, "path_east")
            .connectBuildingToPath("gym", 0, "path_west")
            .connectBuildingToPath("cafeteria", 0, "path_east")
            .connectBuildingToPath("student_hub", 0, "path_center")

            .build()
    }

    fun getSampleBuildings(): List<Building> = listOf(
        // --- ADDED MAIN GATE HERE TOO ---
        Building(
            id = "main_gate",
            name = "Main Gate",
            shortName = "Gate",
            description = "Main Gate/Entrance",
            location = CampusLocation("gate", 9.847875487714289, 122.88741459467961),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("gate_entrance", 9.847875487714289, 122.88741459467961)
            )
        ),

        // Academic Buildings
        Building(
            id = "caf",
            name = "CAF",
            shortName = "CAF",
            description = "College of Agriculture and Forestry",
            location = CampusLocation("caf", 9.850113664121954, 122.88818988673896),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("caf_entrance", 9.850113664121954, 122.88818988673896)
            )
        ),
        Building(
            id = "crim",
            name = "CRIM",
            shortName = "CRIM",
            description = "College of Criminal Justice",
            location = CampusLocation("crim", 9.850355468306018, 122.88925472370697),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("crim_entrance", 9.850355468306018, 122.88925472370697)
            )
        ),
        Building(
            id = "cas",
            name = "CAS",
            shortName = "CAS",
            description = "College of Arts and Sciences",
            location = CampusLocation("cas", 9.853393750047964, 122.88944237254098),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("cas_entrance", 9.853393750047964, 122.88944237254098)
            )
        ),
        Building(
            id = "coted",
            name = "COTED",
            shortName = "COTED",
            description = "College of Teacher Education",
            location = CampusLocation("coted", 9.853941591408363, 122.89044466095176),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("coted_entrance", 9.853941591408363, 122.89044466095176)
            )
        ),
        Building(
            id = "coe",
            name = "COE",
            shortName = "COE",
            description = "College of Engineering",
            location = CampusLocation("coe", 9.849504528210602, 122.88779694312122),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("coe_entrance", 9.849504528210602, 122.88779694312122)
            )
        ),
        Building(
            id = "nib",
            name = "NIB",
            shortName = "NIB",
            description = "New IT Building",
            location = CampusLocation("nib", 9.854059440574803, 122.89011771650271),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("nib_entrance", 9.854059440574803, 122.89011771650271)
            )
        ),
        Building(
            id = "it_building",
            name = "IT Building",
            shortName = "IT",
            description = "IT Building",
            location = CampusLocation("it", 9.85443603974652, 122.88823894709127),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("it_entrance", 9.85443603974652, 122.88823894709127)
            )
        ),
        Building(
            id = "ansi",
            name = "Animal Science",
            shortName = "ANSI",
            description = "Animal Science Building",
            location = CampusLocation("ansi", 9.853983760853357, 122.88819274501164),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("ansi_entrance", 9.853983760853357, 122.88819274501164)
            )
        ),
        Building(
            id = "caf_2",
            name = "CAF Building 2",
            shortName = "CAF 2",
            description = "College of Agriculture and Forestry",
            location = CampusLocation("caf2", 9.85465339974468, 122.8908194509482),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("caf2_entrance", 9.85465339974468, 122.8908194509482)
            )
        ),
        Building(
            id = "crim_2",
            name = "CRIM Building 2",
            shortName = "CRIM 2",
            description = "College of Criminal Justice",
            location = CampusLocation("crim2", 9.849557381700073, 122.88873973958049),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("crim2_entrance", 9.849557381700073, 122.88873973958049)
            )
        ),
        Building(
            id = "hm",
            name = "Hotel Management",
            shortName = "HM",
            description = "Hotel and Management Building",
            location = CampusLocation("hm", 9.85212076560046, 122.89042819014846),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("hm_entrance", 9.85212076560046, 122.89042819014846)
            )
        ),

        // Library
        Building(
            id = "library",
            name = "Library",
            shortName = "Library",
            description = "Main library",
            location = CampusLocation("lib", 9.854278907693391, 122.88931068697471),
            type = BuildingType.LIBRARY,
            entrances = listOf(
                CampusLocation("lib_entrance", 9.854278907693391, 122.88931068697471)
            )
        ),

        // Administrative Offices
        Building(
            id = "admin_new",
            name = "Admin Building",
            shortName = "Admin",
            description = "New Administrative Building",
            location = CampusLocation("admin_new", 9.85282437919445, 122.89086729646579),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("admin_entrance", 9.85282437919445, 122.89086729646579)
            )
        ),
        Building(
            id = "admin_old",
            name = "Admin Building (Old)",
            shortName = "Admin",
            description = "Administrative offices",
            location = CampusLocation("admin_old", 9.852863662387657, 122.89044785066042),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("admin_old_entrance", 9.852863662387657, 122.89044785066042)
            )
        ),
        Building(
            id = "registrar",
            name = "Registrar's Office",
            shortName = "Registrar",
            description = "Registrar's Office and Student Records",
            location = CampusLocation("registrar", 9.853254215951052, 122.88982102069603),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("registrar_entrance", 9.853254215951052, 122.88982102069603)
            )
        ),
        Building(
            id = "ossa",
            name = "OSSA",
            shortName = "OSSA",
            description = "Office of Student Services and Affairs",
            location = CampusLocation("ossa", 9.85407306458513, 122.8888194728647),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("ossa_entrance", 9.85407306458513, 122.8888194728647)
            )
        ),
        Building(
            id = "mis",
            name = "MIS Building",
            shortName = "MIS",
            description = "MIS Office",
            location = CampusLocation("mis", 9.853168, 122.889431),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("mis_entrance", 9.853168, 122.889431)
            )
        ),
        Building(
            id = "accreditation",
            name = "Accreditation",
            shortName = "Accreditation",
            description = "Accreditation Building",
            location = CampusLocation("accred", 9.853522048029461, 122.89067910404592),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("accred_entrance", 9.853522048029461, 122.89067910404592)
            )
        ),
        Building(
            id = "kscd",
            name = "KSCD",
            shortName = "KSCD",
            description = "PE and Sports Equipment",
            location = CampusLocation("kscd", 9.853403769975246, 122.88817695679008),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("kscd_entrance", 9.853403769975246, 122.88817695679008)
            )
        ),
        Building(
            id = "yearbook",
            name = "Yearbook",
            shortName = "Yearbook",
            description = "Yearbook Building",
            location = CampusLocation("yearbook", 9.850599914981576, 122.88940090408893),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("yearbook_entrance", 9.850599914981576, 122.88940090408893)
            )
        ),
        Building(
            id = "drrm",
            name = "DRRM",
            shortName = "DRRM",
            description = "Disaster Risk Reduction and Management Building",
            location = CampusLocation("drrm", 9.853732087559674, 122.88833942271212),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("drrm_entrance", 9.853732087559674, 122.88833942271212)
            )
        ),
        Building(
            id = "nstp",
            name = "NSTP",
            shortName = "NSTP",
            description = "National Service Training Program Building",
            location = CampusLocation("nstp", 9.853465991267354, 122.8882333432869),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("nstp_entrance", 9.853465991267354, 122.8882333432869)
            )
        ),
        Building(
            id = "rotc",
            name = "ROTC",
            shortName = "ROTC",
            description = "Reserve Officers Training Corps Building",
            location = CampusLocation("rotc", 9.853201550694834, 122.88836415995955),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("rotc_entrance", 9.853201550694834, 122.88836415995955)
            )
        ),

        // Sports Facilities
        Building(
            id = "gym",
            name = "Gymnasium",
            shortName = "Gym",
            description = "Gymnasium",
            location = CampusLocation("gym", 9.853530434736115, 122.88780706137088),
            type = BuildingType.SPORTS,
            entrances = listOf(
                CampusLocation("gym_entrance", 9.853530434736115, 122.88780706137088)
            )
        ),
        Building(
            id = "swimming_pool",
            name = "Swimming Pool",
            shortName = "Pool",
            description = "Swimming Pool",
            location = CampusLocation("pool", 9.853471765633156, 122.89153553902976),
            type = BuildingType.SPORTS,
            entrances = listOf(
                CampusLocation("pool_entrance", 9.853471765633156, 122.89153553902976)
            )
        ),

        // Student Facilities
        Building(
            id = "student_hub",
            name = "Students Hub",
            shortName = "Hub",
            description = "Students Lounge",
            location = CampusLocation("hub", 9.853590668411169, 122.88866477231646),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("hub_entrance", 9.853590668411169, 122.88866477231646)
            )
        ),
        Building(
            id = "cafeteria",
            name = "Cafeteria",
            shortName = "Cafeteria",
            description = "Canteens and Cafeteria",
            location = CampusLocation("cafeteria", 9.853325632397336, 122.89097574633368),
            type = BuildingType.CAFETERIA,
            entrances = listOf(
                CampusLocation("cafeteria_entrance", 9.853325632397336, 122.89097574633368)
            )
        ),
        Building(
            id = "canteen_new",
            name = "New Canteen",
            shortName = "Canteen",
            description = "New Canteen Area",
            location = CampusLocation("canteen", 9.85408737916657, 122.8890180392841),
            type = BuildingType.CAFETERIA,
            entrances = listOf(
                CampusLocation("canteen_entrance", 9.85408737916657, 122.8890180392841)
            )
        ),
        Building(
            id = "clinic",
            name = "Clinic",
            shortName = "Clinic",
            description = "Clinic",
            location = CampusLocation("clinic", 9.851165444957411, 122.88894090526546),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("clinic_entrance", 9.851165444957411, 122.88894090526546)
            )
        ),
        Building(
            id = "safe_center",
            name = "Safe Center",
            shortName = "Safe Center",
            description = "Guidance Center",
            location = CampusLocation("safe", 9.85427576504456, 122.8886264958972),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("safe_entrance", 9.85427576504456, 122.8886264958972)
            )
        ),

        Building(
            id = "mini_hotel",
            name = "Mini Hotel",
            shortName = "Hotel",
            description = "Guest House",
            location = CampusLocation("hotel", 9.851318719269928, 122.88906428687646),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("hotel_entrance", 9.851318719269928, 122.88906428687646)
            )
        ),
        Building(
            id = "mt_ballo_hall",
            name = "Mt. Ballo Hall",
            shortName = "Hall",
            description = "Multi-Purpose Hall",
            location = CampusLocation("hall", 9.851894818646322, 122.89019081465135),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("hall_entrance", 9.851894818646322, 122.89019081465135)
            )
        ),
        Building(
            id = "new_white_building",
            name = "New White Building",
            shortName = "NWB",
            description = "Open Building",
            location = CampusLocation("nwb", 9.854503606500575, 122.88897576825885),
            type = BuildingType.ACADEMIC,
            entrances = listOf(
                CampusLocation("nwb_entrance", 9.854503606500575, 122.88897576825885)
            )
        ),
        Building(
            id = "museum",
            name = "Museum",
            shortName = "Museum",
            description = "Museum",
            location = CampusLocation("museum", 9.853806457644907, 122.89035534929693),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("museum_entrance", 9.853806457644907, 122.89035534929693)
            )
        ),
        Building(
            id = "rdec",
            name = "RDEC",
            shortName = "RDEC",
            description = "Research and Development Extension Center",
            location = CampusLocation("rdec", 9.846988410971413, 122.88673311649),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("rdec_entrance", 9.846988410971413, 122.88673311649)
            )
        ),
        Building(
            id = "pedo",
            name = "PEDO",
            shortName = "PEDO",
            description = "Planning and Development Office",
            location = CampusLocation("pedo", 9.847510802295275, 122.8872694345796),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("pedo_entrance", 9.847510802295275, 122.8872694345796)
            )
        ),
        Building(
            id = "alumni",
            name = "Alumni",
            shortName = "Alumni",
            description = "Alumni Building",
            location = CampusLocation("alumni", 9.847958066385061, 122.88792594570222),
            type = BuildingType.ADMINISTRATIVE,
            entrances = listOf(
                CampusLocation("alumni_entrance", 9.847958066385061, 122.88792594570222)
            )
        ),
        Building(
            id = "Bh",
            name = "Boarding House",
            shortName = "Boarding House",
            description = "Boarding House",
            location = CampusLocation("alumni", 9.852121, 122.886243),
            type = BuildingType.LANDMARK,
            entrances = listOf(
                CampusLocation("alumni_entrance", 9.852121, 122.886243)
            )
        )
    )

    /**
     * Get campus center coordinates
     */
    fun getCampusCenter(): CampusLocation = CAMPUS_CENTER

    /**
     * Get campus bounds for initial map view
     * Returns: CampusLocation with min/max coordinates
     */
    fun getCampusBounds(): BoundingBox {
        return BoundingBox(
            north = 9.855,  // Max latitude
            south = 9.846,  // Min latitude
            east = 122.892, // Max longitude
            west = 122.886  // Min longitude
        )
    }
}

/**
 * Bounding box for map display
 */
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)