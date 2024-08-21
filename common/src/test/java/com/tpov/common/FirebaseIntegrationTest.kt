package com.tpov.common

import com.google.firebase.firestore.FirebaseFirestore
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // укажите версию SDK, если нужно
class FirebaseIntegrationTest {

    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        // Подключение к реальному Firebase
        firestore = FirebaseFirestore.getInstance()
    }

    @Test
    fun `test adding document to firestore`() {
        // Arrange
        val data = hashMapOf("name" to "John Doe", "age" to 29)
        val latch = CountDownLatch(1)
        var documentId: String? = null

        // Act
        firestore.collection("users").add(data)
            .addOnSuccessListener { documentReference ->
                documentId = documentReference.id
                latch.countDown()
            }
            .addOnFailureListener {
                latch.countDown()
            }

        // Wait for the operation to complete
        latch.await(10, TimeUnit.SECONDS)

        // Assert
        assertNotNull(documentId)
        assertTrue(documentId!!.isNotEmpty())
    }

    @Test
    fun `test retrieving document from firestore`() {
        // Arrange
        val expectedData = hashMapOf("name" to "Jane Doe", "age" to 30)
        val latch = CountDownLatch(1)
        var retrievedData: Map<String, Any>? = null

        firestore.collection("users").add(expectedData)
            .addOnSuccessListener { documentReference ->
                firestore.collection("users").document(documentReference.id).get()
                    .addOnSuccessListener { document ->
                        retrievedData = document.data
                        latch.countDown()
                    }
                    .addOnFailureListener {
                        latch.countDown()
                    }
            }

        // Wait for the operation to complete
        latch.await(10, TimeUnit.SECONDS)

        // Assert
        assertNotNull(retrievedData)
        assertTrue(retrievedData!!.isNotEmpty())
        assertTrue(retrievedData == expectedData)
    }
}
