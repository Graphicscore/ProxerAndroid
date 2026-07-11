package me.proxer.app.util.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ResettingMutableLiveDataTest {

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    private class AlwaysActiveLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `observer receives a value and the LiveData resets to null internally afterwards`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = "first"

        assertEquals(listOf("first"), received)
        assertNull(liveData.value)
    }

    @Test
    fun `two consecutive structurally equal values are both delivered to the observer`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = "same"
        liveData.value = "same"

        assertEquals(listOf("same", "same"), received)
    }

    @Test
    fun `setting value to null directly does not notify the observer`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = null

        assertEquals(emptyList<String?>(), received)
    }
}
