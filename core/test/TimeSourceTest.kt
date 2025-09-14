package core

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import core.util.Duration
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TimeSourceTest {
  private val sourceTime = Instant.parse("2001-01-01T00:00:00Z")
  private val runtimeTime = Instant.parse("2001-01-11T00:00:00Z")
  private val sinkTime = Instant.parse("2001-01-21T00:00:00Z")
  private val sourceOffset = sourceTime - runtimeTime // 10.days
  private val sinkOffset = sinkTime - runtimeTime // 10.days
  private val target =
    TimeSource(sourceOffset = Duration(sourceOffset), sinkOffset = Duration(sinkOffset))

  @Test
  fun sourceTime() {
    val time = target.fromSource(sourceTime)

    assertAll {
      assertThat(time::atSource).isEqualTo(sourceTime)
      assertThat(time::atRuntime).isEqualTo(runtimeTime)
      assertThat(time::atSink).isEqualTo(sinkTime)
    }
  }

  @Test
  fun runtimeTime() {
    val time = target.fromRuntime(runtimeTime)

    assertAll {
      assertThat(time::atSource).isEqualTo(sourceTime)
      assertThat(time::atRuntime).isEqualTo(runtimeTime)
      assertThat(time::atSink).isEqualTo(sinkTime)
    }
  }

  @Test
  fun sinkTime() {
    val time = target.fromSink(sinkTime)

    assertAll {
      assertThat(time::atSource).isEqualTo(sourceTime)
      assertThat(time::atRuntime).isEqualTo(runtimeTime)
      assertThat(time::atSink).isEqualTo(sinkTime)
    }
  }

  @Test
  fun plus() {
    val time = target.fromSink(sinkTime) + 10.days

    assertAll {
      assertThat(time::atSource).isEqualTo(sourceTime + 10.days)
      assertThat(time::atRuntime).isEqualTo(runtimeTime + 10.days)
      assertThat(time::atSink).isEqualTo(sinkTime + 10.days)
    }
  }

  @Test
  fun compareTo() {
    val time = target.fromSink(sinkTime)
    val time2 = target.fromSource(sourceTime) + 10.days

    assertAll {
      assertThat(time2).isGreaterThan(time)
      assertThat(time).isGreaterThanOrEqualTo(time)
      assertThat(time).isLessThan(time2)
      assertThat(time).isLessThanOrEqualTo(time)
      assertThat(time in time..<time2).isTrue()
    }
  }
}
