package org.asyncttwiz.android

import java.io.Closeable
import org.asyncttwiz.protocol.*

interface Cubes : Closeable {
    suspend fun create(token: String, value: CreateCube): SavedCube
    suspend fun listCubes(token: String, after: String? = null): CubeList
    suspend fun readCube(token: String, id: String): SavedCube
    suspend fun update(token: String, id: String, value: UpdateCube): SavedCube
    suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted
}
