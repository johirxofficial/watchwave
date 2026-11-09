package com.watchwave.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeParserTest {

    private val size = 1_000L

    @Test
    fun `missing header means full response`() {
        assertEquals(RangeParseResult.Full, RangeParser.parse(null, size))
        assertEquals(RangeParseResult.Full, RangeParser.parse("  ", size))
    }

    @Test
    fun `bounded range is parsed`() {
        val result = RangeParser.parse("bytes=0-99", size)
        assertEquals(RangeParseResult.Partial(ByteRange(0, 99)), result)
        assertEquals(100L, (result as RangeParseResult.Partial).range.length)
    }

    @Test
    fun `open ended range runs to the end`() {
        assertEquals(RangeParseResult.Partial(ByteRange(900, 999)), RangeParser.parse("bytes=900-", size))
    }

    @Test
    fun `suffix range returns the tail`() {
        assertEquals(RangeParseResult.Partial(ByteRange(900, 999)), RangeParser.parse("bytes=-100", size))
    }

    @Test
    fun `suffix larger than file returns the whole file`() {
        assertEquals(RangeParseResult.Partial(ByteRange(0, 999)), RangeParser.parse("bytes=-5000", size))
    }

    @Test
    fun `end past file is clamped`() {
        assertEquals(RangeParseResult.Partial(ByteRange(900, 999)), RangeParser.parse("bytes=900-5000", size))
    }

    @Test
    fun `invalid ranges are not satisfiable`() {
        val invalid = listOf(
            "bytes=1000-1200",
            "bytes=999999-",
            "bytes=50-10",
            "bytes=abc-def",
            "items=0-10",
            "bytes=-0",
            "bytes=-",
            "bytes=0-10,20-30",
        )
        invalid.forEach { header ->
            assertEquals("header=$header", RangeParseResult.NotSatisfiable, RangeParser.parse(header, size))
        }
    }

    @Test
    fun `zero sized resource is not rangeable`() {
        assertEquals(RangeParseResult.NotSatisfiable, RangeParser.parse("bytes=0-10", 0L))
        assertEquals(RangeParseResult.Full, RangeParser.parse(null, 0L))
    }

    @Test
    fun `content range strings are RFC shaped`() {
        assertEquals("bytes 10-19/100", RangeParser.contentRange(10, 19, 100))
        assertEquals("bytes */100", RangeParser.unsatisfiedContentRange(100))
        assertTrue(RangeParser.parse("Bytes=0-0", size) is RangeParseResult.Partial)
    }
}
