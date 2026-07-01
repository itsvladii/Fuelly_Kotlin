package com.example.fuelly

import com.example.fuelly.data.LoginDataSource
import com.example.fuelly.data.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for login logic using fixed values.
 */
class LoginUnitTest {

    @Test
    fun testLogin_returnsSuccessWithCorrectDisplayName() {
        // Arrange: prepare the data source and fixed input values
        val loginDataSource = LoginDataSource()
        val username = "testUser@fuelly.com"
        val password = "fixedPassword123"

        // Act: call the login method
        val result = loginDataSource.login(username, password)

        // Assert: verify that the result is a success and contains the expected data
        assertTrue("The result should be an instance of Result.Success", result is Result.Success)
        
        val successResult = result as Result.Success
        assertEquals("The display name should be 'Jane Doe'", "Jane Doe", successResult.data.displayName)
        assertTrue("The user ID should not be empty", successResult.data.userId.isNotEmpty())
    }
}
