package dev.petuska.spond.sync.core

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val sourceTime = Instant.parse("2001-01-01T00:00:00Z")
private val runtimeTime = Instant.parse("2001-01-11T00:00:00Z")
private val sinkTime = Instant.parse("2001-01-21T00:00:00Z")
private val sourceOffset = sourceTime - runtimeTime // 10.days
private val sinkOffset = sinkTime - runtimeTime // 10.days

val TimeSourceTest by testSuite {
  val target = TimeSource(sourceOffset = sourceOffset, sinkOffset = sinkOffset)

  test("fromSource") {
    val time = target.fromSource(sourceTime)

    assertEquals(sourceTime, time.atSource)
    assertEquals(runtimeTime, time.atRuntime)
    assertEquals(sinkTime, time.atSink)
  }

  test("fromRuntime") {
    val time = target.fromRuntime(runtimeTime)

    assertEquals(sourceTime, time.atSource)
    assertEquals(runtimeTime, time.atRuntime)
    assertEquals(sinkTime, time.atSink)
  }

  test("fromSink") {
    val time = target.fromSink(sinkTime)

    assertEquals(sourceTime, time.atSource)
    assertEquals(runtimeTime, time.atRuntime)
    assertEquals(sinkTime, time.atSink)
  }

  test("plus") {
    val time = target.fromSink(sinkTime) + 10.days

    assertEquals(sourceTime + 10.days, time.atSource)
    assertEquals(runtimeTime + 10.days, time.atRuntime)
    assertEquals(sinkTime + 10.days, time.atSink)
  }

  test("compareTo") {
    val time = target.fromSink(sinkTime)
    val time2 = target.fromSource(sourceTime) + 10.days

    assertTrue(time2 > time)
    assertTrue(time >= time)
    assertTrue(time < time2)
    assertTrue(time <= time)
    assertTrue(time in time..<time2)
  }
}
