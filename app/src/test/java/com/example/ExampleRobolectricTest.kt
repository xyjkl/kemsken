package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.DocumentCorners
import com.example.domain.model.FilterType
import com.example.domain.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KemSken", appName)
    }

    @Test
    fun `test document corners serialization and deserialization`() {
        val corners = DocumentCorners.defaultInset(1000f, 800f, 0.1f)
        val serialized = corners.serialize()
        assertNotNull(serialized)

        val deserialized = DocumentCorners.deserialize(serialized, 1000f, 800f)
        assertEquals(corners.topLeft.x, deserialized.topLeft.x, 0.01f)
        assertEquals(corners.topLeft.y, deserialized.topLeft.y, 0.01f)
        assertEquals(corners.bottomRight.x, deserialized.bottomRight.x, 0.01f)
        assertEquals(corners.bottomRight.y, deserialized.bottomRight.y, 0.01f)
    }

    @Test
    fun `test filter types completeness`() {
        assertEquals(10, FilterType.values().size)
    }

    @Test
    fun `test sort options completeness`() {
        assertEquals(6, SortOption.values().size)
    }
}
