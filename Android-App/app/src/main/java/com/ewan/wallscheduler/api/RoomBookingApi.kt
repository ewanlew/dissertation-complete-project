package com.ewan.wallscheduler.api

import AdminLogin
import Booking
import BookingRequest
import Building
import GenericResponse
import RoomDetails
import LoginResponse
import Room
import RoomLockSettings
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/*** defines all the backend endpoints the app can talk to using retrofit ***/
interface RoomBookingApi {
    @GET("buildings")
    suspend fun getBuildings(): Response<List<Building>> // fetches all buildings

    @GET("buildings/{id}/rooms")
    suspend fun getRooms(@Path("id") id: Int): Response<List<Room>> // fetches rooms in a specific building

    @GET("rooms")
    suspend fun getAllRooms(): Response<List<Room>> // gets all rooms

    @GET("rooms/{id}")
    suspend fun getRoom(@Path("id") id: Int): Response<RoomDetails> // fetches details for a single room

    @GET("rooms/{id}/schedule")
    suspend fun getSchedule(
        @Path("id") roomId: Int,
        @Query("week") week: String?
    ): Response<List<Booking>> // gets the room's schedule for a week

    @POST("bookings")
    suspend fun makeBooking(@Body bookingRequest: BookingRequest): Response<GenericResponse> // submits a new booking

    @POST("admin/login")
    suspend fun login(@Body credentials: AdminLogin): Response<LoginResponse> // logs in an admin

    @GET("admin/bookings")
    suspend fun getPendingBookings(): Response<List<Booking>> // fetches all pending bookings

    @POST("admin/bookings/{id}/approve")
    suspend fun approveBooking(@Path("id") bookingId: Int): Response<GenericResponse> // approves a booking by id

    @POST("admin/bookings/{id}/deny")
    suspend fun denyBooking(@Path("id") bookingId: Int): Response<GenericResponse> // denies a booking by id

    @GET("admin/room-lock")
    suspend fun getRoomLockSettings(): Response<RoomLockSettings> // gets room lock settings

    @POST("admin/room-lock")
    suspend fun setRoomLockSettings(@Body settings: RoomLockSettings): Response<GenericResponse> // updates lock settings
}
