package com.tpov.common.data

import android.content.Context
import android.util.Log
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
        Log.w("RepositoryExceptionImpl", "Sending error to remote (not implemented)")
        // TODO: Implement remote error reporting when needed
    }

}
