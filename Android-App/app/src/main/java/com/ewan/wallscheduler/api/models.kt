// used for receiving generic success/failure messages from the backend
data class GenericResponse(
    val message: String
)

// represents a building on campus (e.g. great hall, cofo)
data class Building(
    val id: Int,        // primary key
    val name: String    // human-readable name
)

// represents a room within a building
data class Room(
    val id: Int,              // primary key
    val building_id: Int,     // foreign key to building
    val name: String,         // room label (e.g. GH104)
)

// used when requesting room details including building name
data class RoomDetails(
    val id: Int,
    val name: String,
    val building: String // building name included in detail payload
)

// represents a booking for a room
data class Booking(
    val id: Int,                 // booking id
    val room_id: Int,            // which room is booked
    val user_email: String,      // person who booked it
    val purpose: String,         // why the room is booked
    val attendees: Int,          // number of people
    val start_time: String,      // timestamp when it starts
    val end_time: String,        // timestamp when it ends
    val status: String,          // pending, approved, denied
    val risk_assessment: String  // details for safety evaluation
)

// payload sent to the backend when creating a new booking
data class BookingRequest(
    val room_id: Int,
    val user_email: String,
    val purpose: String,
    val attendees: Int,
    val start_time: String,
    val end_time: String,
    val risk_assessment: String
)

// request payload for admin login
data class AdminLogin(
    val username: String,
    val password: String
)

// response payload from admin login
data class LoginResponse(
    val success: Boolean
)

// settings for whether passcode protection is active, and what the code is
data class RoomLockSettings(
    val enabled: Boolean,  // true = locked behind passcode
    val passcode: String   // 4-digit pin code
)
