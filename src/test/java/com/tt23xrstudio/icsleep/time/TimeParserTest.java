package com.tt23xrstudio.icsleep.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 时间解析器单元测试。
 */
class TimeParserTest {

    private static final long MAX = 72000L;

    @Test
    void testPlainTicks() throws TimeParseException {
        assertEquals(20L, TimeParser.parse("20", "tick", MAX).ticks());
        assertEquals(0L, TimeParser.parse("0", "tick", MAX).ticks());
    }

    @Test
    void testSuffixes() throws TimeParseException {
        assertEquals(20L, TimeParser.parse("20t", "tick", MAX).ticks());
        assertEquals(20L, TimeParser.parse("1s", "tick", MAX).ticks());
        assertEquals(1200L, TimeParser.parse("1m", "tick", MAX).ticks());
        assertEquals(72000L, TimeParser.parse("1h", "tick", MAX).ticks());
        assertEquals(24000L, TimeParser.parse("1d", "tick", MAX).ticks());
    }

    @Test
    void testDefaultUnitSeconds() throws TimeParseException {
        assertEquals(40L, TimeParser.parse("2", "second", MAX).ticks());
    }

    @Test
    void testInvalidFormat() {
        assertThrows(TimeParseException.class, () -> TimeParser.parse("abc", "tick", MAX));
        assertThrows(TimeParseException.class, () -> TimeParser.parse("", "tick", MAX));
        assertThrows(TimeParseException.class, () -> TimeParser.parse("1x", "tick", MAX));
        assertThrows(TimeParseException.class, () -> TimeParser.parse("1.5s", "tick", MAX));
    }

    @Test
    void testNegative() {
        assertThrows(TimeParseException.class, () -> TimeParser.parse("-1", "tick", MAX));
    }

    @Test
    void testOutOfRange() throws TimeParseException {
        assertThrows(TimeParseException.class, () -> TimeParser.parse("72001", "tick", MAX));
        assertThrows(TimeParseException.class, () -> TimeParser.parse("2h", "tick", MAX));
        assertEquals(MAX, TimeParser.parse("72000", "tick", MAX).ticks());
    }

    @Test
    void testCustomMax() throws TimeParseException {
        assertEquals(100L, TimeParser.parse("100", "tick", 100L).ticks());
        assertThrows(TimeParseException.class, () -> TimeParser.parse("101", "tick", 100L));
    }
}
