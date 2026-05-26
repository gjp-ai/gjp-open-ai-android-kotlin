package com.ganjianping.ai

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun mediaUrlNormalizationDropsTransientQueryParameters() {
        val normalized = normalizeMediaUrl("https://example.com/image.png?token=abc&keep=yes&v=2&sig=no")

        assertEquals("https://example.com/image.png?keep=yes", normalized)
    }

    @Test
    fun tagsAreParsedFromCommaSeparatedSettingsValues() {
        assertEquals(listOf("AI", "Tools", "News"), "AI, Tools,News".tagList())
    }

    @Test
    fun htmlIsStrippedForSearchText() {
        assertEquals("Hello world & more", "<p>Hello&nbsp;<b>world</b> &amp; more</p>".stripHtml())
    }
}
