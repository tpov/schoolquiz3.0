package com.tpov.common.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
import com.tpov.common.domain.repository.RepositoryException
import javax.inject.Inject

open class RepositoryExceptionImpl @Inject constructor(
    private val structureDataDao: StructureDataDao,
    private val structureEditDataDao: StructureEditDataDao,
    private val firestore: FirebaseFirestore,
    private val context: Context
) : RepositoryException {
    override fun sendErrorRemote() {
        TODO("Not yet implemented")
    }

}
