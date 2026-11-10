package org.asyncttwiz.android

import java.io.Closeable
import org.asyncttwiz.protocol.*

interface Cubes : Closeable {
    suspend fun create(token: String, value: CreateCube): SavedCube
    suspend fun listCubes(token: String, after: String? = null): CubeList
    suspend fun readCube(token: String, id: String): SavedCube
    suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions): SubmittedDefinitions
    suspend fun update(token: String, id: String, value: UpdateCube): SavedCube
    suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted
    /** I-D51: stores the cube's Automatum support for its revision (not an edit). */
    suspend fun setCubeSupport(token: String, id: String, value: SetCardListSupport): CardListSupportStored = throw DeckFailure("unsupported")
}
