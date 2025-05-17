package com.ewan

import com.ewan.wallscheduler.EmailService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.callloging.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import org.mindrot.jbcrypt.BCrypt
import java.sql.PreparedStatement

/***
 * launches the ktor server using the Netty engine.
 * checks for an environment variable named "PORT" to bind to.
 * if not set, defaults to port 8080.
 */
fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080 // fallback to 8080
    ) {
        module() // calls the main application module
    }.start(wait = true) // blocks until server is shut down
}

/***
 * configures the ktor application modules (plugins and routing).
 * sets up content negotiation, logging, database connection, and root route.
 */
fun Application.module() {
    // install plugin to handle json serialisation using gson
    install(ContentNegotiation) {
        gson() // enables automatic gson encoding/decoding
    }

    // install call logging for debugging and request traceability
    install(CallLogging)

    // establish reusable database connection
    val db = Database.connect()

    // define all application routes
    routing {
        // base route to verify server is alive
        get("/") {
            call.respond(mapOf("status" to "ok")) // return simple health check
        }

        // returns all rooms with their associated building id
        get("/rooms") {
            val results = mutableListOf<Map<String, Any>>() // container for all room records

            db.connection.use { conn ->
                // fetch room id, building association, and name for each room
                val stmt = conn.prepareStatement("SELECT id, building_id, name FROM rooms")
                val rs = stmt.executeQuery()

                // map each record into a result entry
                while (rs.next()) {
                    results.add(
                        mapOf(
                            "id" to rs.getInt("id"),
                            "building_id" to rs.getInt("building_id"),
                            "name" to rs.getString("name")
                        )
                    )
                }

                rs.close()
                stmt.close()
            }

            // return full room list
            call.respond(results)
        }

        // fetches a specific room’s details by id
        get("/rooms/{id}") {
            val roomId = call.parameters["id"]?.toIntOrNull()

            // ensure the id is valid
            if (roomId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@get
            }

            db.connection.use { conn ->
                // query room and associated building name
                val stmt = conn.prepareStatement(
                    """
                        SELECT r.id, r.name AS room_name, b.name AS building_name
                        FROM rooms r
                        JOIN buildings b ON r.building_id = b.id
                        WHERE r.id = ?
                    """.trimIndent()
                )

                stmt.setInt(1, roomId)
                val rs = stmt.executeQuery()

                // if a room is found, return its details
                if (rs.next()) {
                    val room = mapOf(
                        "id" to rs.getInt("id"),
                        "name" to rs.getString("room_name"),
                        "building" to rs.getString("building_name")
                    )
                    rs.close()
                    stmt.close()
                    call.respond(room)
                } else {
                    // no match found
                    rs.close()
                    stmt.close()
                    call.respond(HttpStatusCode.NotFound, "Room not found")
                }
            }
        }

        // endpoint: get schedule for a specific room, optionally filtered by ISO week (e.g., ?week=2025-W16)
        get("/rooms/{id}/schedule") {
            // parse room ID from path parameters, or null if not an integer
            val roomId = call.parameters["id"]?.toIntOrNull()

            // parse optional week filter (e.g., 2025-W16)
            val weekParam = call.request.queryParameters["week"]

            // if no valid room ID provided, return 400
            if (roomId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@get
            }

            // determine start and end dates for query
            val (startOfWeek, endOfWeek) = try {
                if (weekParam != null) {
                    // split on "-W" (e.g., "2025-W16" → ["2025", "16"])
                    val parts = weekParam.split("-W")
                    val year = parts[0].toInt()
                    val week = parts[1].toInt()

                    // get first day (monday) of specified ISO week
                    val monday = java.time.LocalDate
                        .ofYearDay(year, 1)
                        .with(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                        .with(java.time.DayOfWeek.MONDAY)

                    // sunday is 6 days after monday
                    val sunday = monday.plusDays(6)

                    monday to sunday
                } else {
                    // if no week specified, default to current week
                    val today = java.time.LocalDate.now()
                    val monday = today.with(java.time.DayOfWeek.MONDAY)
                    val sunday = monday.plusDays(6)
                    monday to sunday
                }
            } catch (e: Exception) {
                // if parsing fails, return 400
                call.respond(HttpStatusCode.BadRequest, "Invalid week format")
                return@get
            }

            // mutable list to collect bookings to be returned
            val bookings = mutableListOf<Map<String, Any>>()

            db.connection.use { conn ->
                val query: String
                val stmt: PreparedStatement

                if (weekParam != null) {
                    // query bookings within the specified week
                    query = """
                        SELECT id, user_email, purpose, attendees, start_time, end_time, status
                        FROM bookings
                        WHERE room_id = ? AND start_time BETWEEN ? AND ?
                        ORDER BY start_time
                    """.trimIndent()

                    stmt = conn.prepareStatement(query)
                    stmt.setInt(1, roomId)
                    stmt.setObject(2, startOfWeek.atStartOfDay())      // 00:00:00 on monday
                    stmt.setObject(3, endOfWeek.atTime(23, 59, 59))    // 23:59:59 on sunday
                } else {
                    // query all bookings for the room (no week filter)
                    query = """
                        SELECT id, user_email, purpose, attendees, start_time, end_time, status
                        FROM bookings
                        WHERE room_id = ?
                        ORDER BY start_time
                    """.trimIndent()

                    stmt = conn.prepareStatement(query)
                    stmt.setInt(1, roomId)
                }

                // execute query and extract rows
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    // each booking is stored as a map of column → value
                    bookings.add(
                        mapOf(
                            "id" to rs.getInt("id"),
                            "user_email" to rs.getString("user_email"),
                            "purpose" to rs.getString("purpose"),
                            "attendees" to rs.getInt("attendees"),
                            "start_time" to rs.getTimestamp("start_time").toString(),
                            "end_time" to rs.getTimestamp("end_time").toString(),
                            "status" to rs.getString("status")
                        )
                    )
                }

                // clean up jdbc objects
                rs.close()
                stmt.close()
            }

            // return the list of bookings as JSON
            call.respond(bookings)
        }

        // returns all buildings in the system
        get("/buildings") {
            val buildings = mutableListOf<Map<String, Any>>() // list to store each building as a map

            db.connection.use { conn ->
                // prepare and execute query to get id and name of all buildings
                val stmt = conn.prepareStatement("SELECT id, name FROM buildings")
                val rs = stmt.executeQuery()

                // map each row to a key-value pair
                while (rs.next()) {
                    buildings.add(
                        mapOf(
                            "id" to rs.getInt("id"),
                            "name" to rs.getString("name")
                        )
                    )
                }

                rs.close()
                stmt.close()
            }

            // respond with the list of buildings
            call.respond(buildings)
        }

        // returns all rooms belonging to a specific building
        get("/buildings/{id}/rooms") {
            // parse building id from path and validate it
            val buildingId = call.parameters["id"]?.toIntOrNull()

            // respond with 400 if no valid id provided
            if (buildingId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid building ID")
                return@get
            }

            // container for room results
            val rooms = mutableListOf<Map<String, Any>>()

            // fetch all rooms that belong to the building
            db.connection.use { conn ->
                val stmt = conn.prepareStatement(
                    "SELECT id, name FROM rooms WHERE building_id = ?"
                )
                stmt.setInt(1, buildingId)

                val rs = stmt.executeQuery()

                // map each room into a result entry
                while (rs.next()) {
                    rooms.add(
                        mapOf(
                            "id" to rs.getInt("id"),
                            "name" to rs.getString("name")
                        )
                    )
                }

                rs.close()
                stmt.close()
            }

            // return room list as response
            call.respond(rooms)
        }

        // posts a booking to the bookings table w/in db
        post("/bookings") {
            // attempt to receive and parse the request body as a map
            val booking = try {
                call.receive<Map<String, Any>>()
            } catch (e: Exception) {
                // respond with 400 if parsing fails
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            // extract and convert all expected fields from the map
            val roomId = (booking["room_id"] as? Double)?.toInt()
            val userEmail = booking["user_email"] as? String
            val purpose = booking["purpose"] as? String
            val attendees = (booking["attendees"] as? Double)?.toInt()
            val startTime = booking["start_time"] as? String
            val endTime = booking["end_time"] as? String
            val riskAssessment = booking["risk_assessment"] as? String

            // check for any missing or invalid fields
            if (roomId == null || userEmail == null || purpose == null || attendees == null
                || startTime == null || endTime == null || riskAssessment == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing or invalid fields")
                return@post
            }

            // manually convert time strings to sql timestamps, replacing "T" if present
            val start = java.sql.Timestamp.valueOf(startTime.replace("T", " "))
            val end = java.sql.Timestamp.valueOf(endTime.replace("T", " "))

            var roomName: String? = null

            db.connection.use { conn ->
                // fetch the room name from db to include in the confirmation email
                val roomQuery = conn.prepareStatement("SELECT name FROM rooms WHERE id = ?")
                roomQuery.setInt(1, roomId)
                val roomRs = roomQuery.executeQuery()
                if (roomRs.next()) {
                    roomName = roomRs.getString("name")
                }
                roomRs.close()
                roomQuery.close()

                // if the room doesn’t exist, respond with 404
                if (roomName == null) {
                    call.respond(HttpStatusCode.NotFound, "Room not found")
                    return@post
                }

                // check if the new booking overlaps any existing one
                val checkStmt = conn.prepareStatement("""
                    SELECT COUNT(*) FROM bookings
                    WHERE room_id = ? AND (
                        (start_time < ? AND end_time > ?) OR
                        (start_time >= ? AND start_time < ?)
                    )
                """.trimIndent())

                checkStmt.setInt(1, roomId)
                checkStmt.setTimestamp(2, end)
                checkStmt.setTimestamp(3, start)
                checkStmt.setTimestamp(4, start)
                checkStmt.setTimestamp(5, end)

                val rs = checkStmt.executeQuery()
                rs.next()
                val conflictCount = rs.getInt(1)
                rs.close()
                checkStmt.close()

                // if any conflict found, return 409 conflict response
                if (conflictCount > 0) {
                    call.respond(HttpStatusCode.Conflict, "Time slot is already booked")
                    return@post
                }

                // insert the new booking into the db with status set to pending
                val insertStmt = conn.prepareStatement("""
                    INSERT INTO bookings (room_id, user_email, purpose, attendees, start_time, end_time, risk_assessment, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')
                """.trimIndent())

                insertStmt.setInt(1, roomId)
                insertStmt.setString(2, userEmail)
                insertStmt.setString(3, purpose)
                insertStmt.setInt(4, attendees)
                insertStmt.setTimestamp(5, start)
                insertStmt.setTimestamp(6, end)
                insertStmt.setString(7, riskAssessment)

                insertStmt.executeUpdate()
                insertStmt.close()
            }

            // send a confirmation email to the user
            EmailService.send(
                to = userEmail,
                subject = "Room Booking Request Received",
                content = """
                    Hello!
        
                    Your booking request for room $roomName has been received and is currently pending approval.
                    You will receive another email when it has been reviewed.
        
                    Please do not reply to this email.
        
                    - Room Scheduler
                """.trimIndent()
            )

            // respond with success json
            call.respond(mapOf("message" to "Booking request submitted"))
        }

        // handles admin login by checking submitted username/password
        post("/admin/login") {
            // try to read credentials from request body
            val credentials = try {
                // expects a JSON body with keys: "username", "password"
                call.receive<Map<String, String>>()
            } catch (e: Exception) {
                // if parsing fails or body is invalid JSON, return 400
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@post
            }

            // extract submitted credentials
            val username = credentials["username"]
            val password = credentials["password"]

            // if either field is missing or null, return 400
            if (username == null || password == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing credentials")
                return@post
            }

            var valid = false

            // check database for matching user
            db.connection.use { conn ->
                // fetch hashed password from database by username
                val stmt = conn.prepareStatement(
                    "SELECT password_hash FROM admins WHERE username = ?"
                )
                stmt.setString(1, username)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val storedHash = rs.getString("password_hash")

                    // verify password using bcrypt
                    valid = BCrypt.checkpw(password, storedHash)
                }

                // cleanup JDBC resources
                rs.close()
                stmt.close()
            }

            if (valid) {
                // credentials matched: login successful
                call.respond(mapOf("success" to true))
            } else {
                // credentials invalid: respond with 401 Unauthorized
                call.respond(HttpStatusCode.Unauthorized, mapOf("success" to false))
            }
        }

        // returns all pending bookings to the admin panel
        get("/admin/bookings") {
            // list to hold each booking record as a map
            val bookings = mutableListOf<Map<String, Any>>()

            // use a db connection for the query
            db.connection.use { conn ->
                // prepare a query to fetch all pending bookings
                val stmt = conn.prepareStatement(
                    """
                        SELECT id, room_id, user_email, purpose, attendees, start_time, end_time, status, risk_assessment
                        FROM bookings
                        WHERE status = 'pending'
                        ORDER BY start_time ASC
                    """.trimIndent()
                )

                // execute the query
                val rs = stmt.executeQuery()

                // loop through the result set and map each row into a booking map
                while (rs.next()) {
                    bookings.add(
                        mapOf(
                            "id" to rs.getInt("id"),
                            "room_id" to rs.getInt("room_id"),
                            "user_email" to rs.getString("user_email"),
                            "purpose" to rs.getString("purpose"),
                            "attendees" to rs.getInt("attendees"),
                            "start_time" to rs.getTimestamp("start_time").toString(),
                            "end_time" to rs.getTimestamp("end_time").toString(),
                            "status" to rs.getString("status"),
                            "risk_assessment" to rs.getString("risk_assessment")
                        )
                    )
                }

                // close resources
                rs.close()
                stmt.close()
            }

            // respond with the full list of pending bookings
            call.respond(bookings)
        }

        // handles approval of a pending booking by booking ID
        post("/admin/bookings/{id}/approve") {
            // try to get the booking id from the path parameters
            val bookingId = call.parameters["id"]?.toIntOrNull()
            if (bookingId == null) {
                // if invalid or missing id, return 400
                call.respond(HttpStatusCode.BadRequest, "Invalid booking ID")
                return@post
            }

            // open database connection for booking lookup and update
            db.connection.use { conn ->
                // fetch details about the booking to approve (user email, room info)
                val query = conn.prepareStatement(
                    """
                        SELECT b.user_email, b.start_time, r.name AS room_name, bd.name AS building_name
                        FROM bookings b
                        JOIN rooms r ON b.room_id = r.id
                        JOIN buildings bd ON r.building_id = bd.id
                        WHERE b.id = ?
                    """.trimIndent()
                )
                query.setInt(1, bookingId)

                val rs = query.executeQuery()

                if (!rs.next()) {
                    // if no booking found, return 404 not found
                    rs.close()
                    query.close()
                    call.respond(HttpStatusCode.NotFound, "Booking not found")
                    return@post
                }

                // extract booking details from result set
                val userEmail = rs.getString("user_email")
                val startTime = rs.getTimestamp("start_time").toLocalDateTime()
                val roomName = rs.getString("room_name")
                val buildingName = rs.getString("building_name")

                // close result set and statement
                rs.close()
                query.close()

                // update the booking's status to approved
                val update = conn.prepareStatement(
                    "UPDATE bookings SET status = 'approved' WHERE id = ?"
                )
                update.setInt(1, bookingId)
                update.executeUpdate()
                update.close()

                // send an email to notify the user that their booking was approved
                EmailService.send(
                    to = userEmail,
                    subject = "Room Booking Update",
                    content = """
                        Hello!
            
                        Your booking for room $roomName in $buildingName at ${startTime} has been approved.
            
                        Please do not reply to this email.
            
                        - Room Scheduler
                    """.trimIndent()
                )

                // respond with a success message
                call.respond(mapOf("message" to "Booking approved"))
            }
        }

        // handles admin denial of a booking by its id
        post("/admin/bookings/{id}/deny") {
            // safely parses the booking id from url path
            val bookingId = call.parameters["id"]?.toIntOrNull()
            if (bookingId == null) {
                // if the id is missing or invalid, return 400
                call.respond(HttpStatusCode.BadRequest, "Invalid booking ID")
                return@post
            }

            // open a database connection for the operation
            db.connection.use { conn ->
                // fetch relevant booking and room details for the email
                val query = conn.prepareStatement(
                    """
                            SELECT b.user_email, b.start_time, r.name AS room_name, bd.name AS building_name
                            FROM bookings b
                            JOIN rooms r ON b.room_id = r.id
                            JOIN buildings bd ON r.building_id = bd.id
                            WHERE b.id = ?
                        """
                )
                query.setInt(1, bookingId)
                val rs = query.executeQuery()

                // if no matching booking found, return 404
                if (!rs.next()) {
                    rs.close()
                    query.close()
                    call.respond(HttpStatusCode.NotFound, "Booking not found")
                    return@post
                }

                // extract booking details from the result set
                val userEmail = rs.getString("user_email")
                val startTime = rs.getTimestamp("start_time").toLocalDateTime()
                val roomName = rs.getString("room_name")
                val buildingName = rs.getString("building_name")
                rs.close()
                query.close()

                // update the booking status to 'denied'
                val update = conn.prepareStatement("UPDATE bookings SET status = 'denied' WHERE id = ?")
                update.setInt(1, bookingId)
                update.executeUpdate()
                update.close()

                // send a denial email to the user
                EmailService.send(
                    to = userEmail,
                    subject = "Room Booking Update",
                    content = """
                        Hello!
        
                        Your booking for room $roomName in $buildingName at ${startTime} has been denied.
        
                        Please do not reply to this email.
        
                        - Room Scheduler
                    """.trimIndent()
                )

                // respond with success message
                call.respond(mapOf("message" to "Booking denied"))
            }
        }

        // update lock settings
        post("/admin/room-lock") {
            // try to parse the incoming json body as a map
            val request = try {
                call.receive<Map<String, Any>>()
            } catch (e: Exception) {
                // if parsing fails, return 400 bad request
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            }

            // extract 'enabled' and 'passcode' from the parsed body
            val enabled = request["enabled"]?.toString()?.toBooleanStrictOrNull()
            val passcode = request["passcode"]?.toString()

            // validate that both fields are present and valid
            if (enabled == null || passcode == null) {
                // if missing or invalid, return 400 bad request
                call.respond(HttpStatusCode.BadRequest, "Missing fields")
                return@post
            }

            // open a database connection
            db.connection.use { conn ->
                // upsert (insert or update) the 'room_lock_enabled' setting
                val insertEnabled = conn.prepareStatement(
                    """
                        INSERT INTO settings (`key`, `value`) VALUES ('room_lock_enabled', ?)
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                    """.trimIndent()
                )
                insertEnabled.setString(1, enabled.toString()) // store as string "true" or "false"
                insertEnabled.executeUpdate()
                insertEnabled.close()

                // upsert the 'room_lock_passcode' setting
                val insertPasscode = conn.prepareStatement(
                    """
                        INSERT INTO settings (`key`, `value`) VALUES ('room_lock_passcode', ?)
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                    """.trimIndent()
                )
                insertPasscode.setString(1, passcode)
                insertPasscode.executeUpdate()
                insertPasscode.close()
            }

            // respond with a success message
            call.respond(HttpStatusCode.OK, mapOf("message" to "Lock settings updated"))
        }

        // retrieves the current room lock settings (passcode + enabled status)
        get("/admin/room-lock") {
            val settings = mutableMapOf<String, String>() // to hold key-value pairs for settings

            db.connection.use { conn ->
                // query for two specific settings entries
                val stmt = conn.prepareStatement(
                    "SELECT `key`, `value` FROM settings WHERE `key` IN ('room_lock_enabled', 'room_lock_passcode')"
                )
                val rs = stmt.executeQuery()

                // store results in a map
                while (rs.next()) {
                    settings[rs.getString("key")] = rs.getString("value")
                }

                rs.close()
                stmt.close()
            }

            // respond with final formatted settings object
            call.respond(
                mapOf(
                    "enabled" to (settings["room_lock_enabled"] == "true"),
                    "passcode" to (settings["room_lock_passcode"] ?: "")
                )
            )
        }
    }
}
