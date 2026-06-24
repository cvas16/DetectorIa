package com.securepass.vision.data.api

import com.securepass.vision.model.User
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("usuarios")
    suspend fun getAllUsers(): Response<List<User>>

    @POST("usuarios")
    suspend fun createUser(@Body user: User): Response<User>

    @PUT("usuarios/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body user: User): Response<User>

    @DELETE("usuarios/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    @POST("detecciones")
    suspend fun postDetection(@Body detection: com.securepass.vision.model.DetectionEvent): Response<com.securepass.vision.model.DetectionEvent>

    @GET("detecciones")
    suspend fun getAllDetections(): Response<List<com.securepass.vision.model.DetectionEvent>>

    @DELETE("detecciones/{id}")
    suspend fun deleteDetection(@Path("id") id: String): Response<Unit>

    @GET("eventos")
    suspend fun getAllGroups(): Response<List<com.securepass.vision.model.SecurityEventGroup>>

    @POST("eventos")
    suspend fun createGroup(@Body event: com.securepass.vision.model.SecurityEventGroup): Response<com.securepass.vision.model.SecurityEventGroup>

    @PUT("eventos/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body event: com.securepass.vision.model.SecurityEventGroup): Response<com.securepass.vision.model.SecurityEventGroup>

    @DELETE("eventos/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<Unit>
}
