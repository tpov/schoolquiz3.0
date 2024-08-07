package com.tpov.common.data.utils

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core

class FirestorePathBuilder(private val firestore: FirebaseFirestore, private val baseCollectionName: String) {
    private var collectionReference: CollectionReference = firestore.collection(baseCollectionName)

    fun addDocument(documentId: String): FirestorePathBuilder {
        collectionReference = collectionReference.document(documentId).collection(documentId)
        return this
    }

    fun build(): CollectionReference {
        return collectionReference
    }

    companion object {
        fun buildCollectionPath(firestore: FirebaseFirestore, vararg parts: String, useTpovId: Boolean = false): CollectionReference {
            val builder = FirestorePathBuilder(firestore, "quizzes")
            parts.forEach { builder.addDocument(it) }
            return if (useTpovId) builder.addDocument(Core.tpovId.toString()).build()
            else builder.build()
        }
    }
}