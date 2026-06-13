package me.proxer.app.util.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UniqueQueueTest {
    private lateinit var queue: UniqueQueue<String>

    @Before fun setup() {
        queue = UniqueQueue()
    }

    @Test fun `starts empty`() {
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size)
    }

    @Test fun `add increases size`() {
        queue.add("a")
        assertEquals(1, queue.size)
        assertFalse(queue.isEmpty())
    }

    @Test fun `add duplicate does not increase size`() {
        queue.add("a")
        queue.add("a")
        assertEquals(1, queue.size)
    }

    @Test fun `offer returns true for new element`() {
        val result = queue.offer("a")
        assertTrue(result)
    }

    @Test fun `offer returns false for duplicate`() {
        queue.offer("a")
        val result = queue.offer("a")
        assertFalse(result)
    }

    @Test fun `poll returns elements in insertion order`() {
        queue.add("a")
        queue.add("b")
        queue.add("c")
        assertEquals("a", queue.poll())
        assertEquals("b", queue.poll())
        assertEquals("c", queue.poll())
    }

    @Test fun `poll returns null when empty`() {
        assertNull(queue.poll())
    }

    @Test fun `peek returns head without removing`() {
        queue.add("a")
        queue.add("b")
        assertEquals("a", queue.peek())
        assertEquals(2, queue.size)
    }

    @Test fun `peek returns null when empty`() {
        assertNull(queue.peek())
    }

    @Test fun `element returns head without removing`() {
        queue.add("a")
        assertEquals("a", queue.element())
        assertEquals(1, queue.size)
    }

    @Test fun `element throws when empty`() {
        try {
            queue.element()
            error("Expected NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // expected
        }
    }

    @Test fun `remove pops head`() {
        queue.add("a")
        queue.add("b")
        assertEquals("a", queue.remove())
        assertEquals(1, queue.size)
    }

    @Test fun `remove throws when empty`() {
        try {
            queue.remove()
            error("Expected NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // expected
        }
    }

    @Test fun `remove(element) removes specific element`() {
        queue.add("a")
        queue.add("b")
        queue.remove("a")
        assertEquals(1, queue.size)
        assertEquals("b", queue.peek())
    }

    @Test fun `contains returns true for present element`() {
        queue.add("a")
        assertTrue(queue.contains("a"))
    }

    @Test fun `contains returns false for absent element`() {
        assertFalse(queue.contains("x"))
    }

    @Test fun `clear empties the queue`() {
        queue.add("a")
        queue.add("b")
        queue.clear()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size)
    }

    @Test fun `addAll adds multiple elements`() {
        queue.addAll(listOf("a", "b", "c"))
        assertEquals(3, queue.size)
    }

    @Test fun `addAll deduplicates against existing`() {
        queue.add("a")
        queue.addAll(listOf("a", "b"))
        assertEquals(2, queue.size)
    }

    @Test fun `iterator visits elements in insertion order`() {
        queue.add("x")
        queue.add("y")
        assertEquals(listOf("x", "y"), queue.toList())
    }
}
