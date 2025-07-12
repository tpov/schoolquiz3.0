package com.tpov.schoolquiz.domain

import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ProfileUseCaseImprovedTest {

    @Mock
    private lateinit var repositoryProfile: RepositoryProfile

    @Mock
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var profileUseCase: ProfileUseCaseImproved

    @Before
    fun setUp() {
        profileUseCase = ProfileUseCaseImproved(repositoryProfile)
    }

    @Test
    fun `getProfileFlow should return flow when successful`() = runTest {
        // Given
        val mockProfile = ProfileEntity()
        whenever(repositoryProfile.getProfileFlow()).thenReturn(flowOf(mockProfile))

        // When
        val result = profileUseCase.getProfileFlow()

        // Then
        assertNotNull(result)
        verify(repositoryProfile).getProfileFlow()
    }

    @Test
    fun `insertAndPushProfile should succeed when repository operations succeed`() = runTest {
        // Given
        val profile = ProfileEntity()
        doNothing().whenever(repositoryProfile).insertProfile(profile)
        doNothing().whenever(repositoryProfile).pushProfile(any())

        // When
        val result = profileUseCase.insertAndPushProfile(profile)

        // Then
        assertTrue(result.isSuccess)
        verify(repositoryProfile).insertProfile(profile)
        verify(repositoryProfile).pushProfile(any())
    }

    @Test
    fun `insertAndPushProfile should fail when repository throws exception`() = runTest {
        // Given
        val profile = ProfileEntity()
        val exception = RuntimeException("Database error")
        doThrow(exception).whenever(repositoryProfile).insertProfile(profile)

        // When
        val result = profileUseCase.insertAndPushProfile(profile)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProfileException)
        assertEquals("Failed to insert and push profile", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateProfile should succeed when repository operation succeeds`() = runTest {
        // Given
        val profile = ProfileEntity()
        doNothing().whenever(repositoryProfile).updateProfile(profile)

        // When
        val result = profileUseCase.updateProfile(profile)

        // Then
        assertTrue(result.isSuccess)
        verify(repositoryProfile).updateProfile(profile)
    }

    @Test
    fun `pushProfile should succeed when repository operation succeeds`() = runTest {
        // Given
        val profileRemote = ProfileRemote()
        doNothing().whenever(repositoryProfile).pushProfile(profileRemote)

        // When
        val result = profileUseCase.pushProfile(profileRemote)

        // Then
        assertTrue(result.isSuccess)
        verify(repositoryProfile).pushProfile(profileRemote)
    }

    @Test
    fun `syncProfile should create new profile when current profile is null`() = runTest {
        // Given
        whenever(repositoryProfile.getProfile()).thenReturn(null)
        whenever(repositoryProfile.getNewTpovId()).thenReturn(123)

        // When
        val result = profileUseCase.syncProfile()

        // Then
        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals(123, profile?.tpovId)
        verify(repositoryProfile).insertProfile(any())
    }

    @Test
    fun `syncProfile should handle authentication failure gracefully`() = runTest {
        // Given
        val existingProfile = ProfileEntity()
        whenever(repositoryProfile.getProfile()).thenReturn(existingProfile)
        whenever(repositoryProfile.getNewTpovId()).thenReturn(456)

        // When
        val result = profileUseCase.syncProfile()

        // Then
        assertTrue(result.isSuccess)
        verify(repositoryProfile).insertProfile(any())
    }

    @Test
    fun `checkProfileStatus should return NOT_CREATED when profile is null`() = runTest {
        // Given
        whenever(repositoryProfile.getProfile()).thenReturn(null)

        // When
        val result = profileUseCase.checkProfileStatus()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(ProfileStatus.NOT_CREATED, result.getOrNull())
    }

    @Test
    fun `checkProfileStatus should return OFFLINE when profile status is offline`() = runTest {
        // Given
        val profile = ProfileEntity().apply { status = ProfileStatus.OFFLINE.statusCode }
        whenever(repositoryProfile.getProfile()).thenReturn(profile)

        // When
        val result = profileUseCase.checkProfileStatus()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(ProfileStatus.OFFLINE, result.getOrNull())
    }

    @Test
    fun `clearProfile should succeed`() = runTest {
        // When
        val result = profileUseCase.clearProfile()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ProfileException should contain correct message and cause`() {
        // Given
        val originalException = RuntimeException("Original error")
        val profileException = ProfileException("Test error", originalException)

        // Then
        assertEquals("Test error", profileException.message)
        assertEquals(originalException, profileException.cause)
    }

    @Test
    fun `ProfileStatus enum should have correct values`() {
        // Then
        assertEquals(4, ProfileStatus.values().size)
        assertTrue(ProfileStatus.values().contains(ProfileStatus.NOT_CREATED))
        assertTrue(ProfileStatus.values().contains(ProfileStatus.ANONYMOUS))
        assertTrue(ProfileStatus.values().contains(ProfileStatus.OFFLINE))
        assertTrue(ProfileStatus.values().contains(ProfileStatus.ONLINE))
    }
}