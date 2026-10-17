package org.cryptomator.presentation.presenter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SerialRequestsTest {

	private val runs = mutableListOf<Pair<String, () -> Unit>>()
	private val inTest = SerialRequests<String>({ waiting, next -> "$waiting+$next" }) { request, done -> runs.add(request to done) }

	@Test
	fun `a request runs right away if none is running`() {
		inTest.request("a")

		assertEquals(listOf("a"), runs.map { it.first })
	}

	@Test
	fun `the requests made while one runs are merged and run once it is done`() {
		inTest.request("a")
		inTest.request("b")
		inTest.request("c")
		assertEquals(listOf("a"), runs.map { it.first })

		runs[0].second()
		assertEquals(listOf("a", "b+c"), runs.map { it.first })

		runs[1].second()
		assertEquals(2, runs.size)
	}

	@Test
	fun `a request made while the merged one runs waits for it`() {
		inTest.request("a")
		inTest.request("b")
		runs[0].second()

		inTest.request("c")
		assertEquals(listOf("a", "b"), runs.map { it.first })

		runs[1].second()
		assertEquals(listOf("a", "b", "c"), runs.map { it.first })
	}

	@Test
	fun `a run that is done right away lets the next request run right away`() {
		val ran = mutableListOf<String>()
		val inTest = SerialRequests<String>({ _, next -> next }) { request, done ->
			ran.add(request)
			done()
		}

		inTest.request("a")
		inTest.request("b")

		assertEquals(listOf("a", "b"), ran)
	}
}
