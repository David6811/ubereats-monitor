package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProjectionTest {

    private val square = SuburbShape(
        name = "Square",
        rings = listOf(
            listOf(
                GeoPoint(-38.0, 145.0),
                GeoPoint(-38.0, 145.2),
                GeoPoint(-37.8, 145.2),
                GeoPoint(-37.8, 145.0),
            )
        ),
    )

    @Test
    fun `given one shape, when its box is taken, then it spans the shape's corners`() {
        // arrange
        val shapes = listOf(square)

        // act
        val box = MapProjection.boxOf(shapes)

        // assert
        assertEquals(GeoBox(-38.0, -37.8, 145.0, 145.2), box)
    }

    @Test
    fun `given no shapes, when a box is taken, then there is none`() {
        // arrange
        val shapes = emptyList<SuburbShape>()

        // act
        val box = MapProjection.boxOf(shapes)

        // assert
        assertEquals(null, box)
    }

    @Test
    fun `given a box fitted to a canvas, when the north west corner is placed, then it lands on the drawing's edge`() {
        // arrange
        val box = GeoBox(-38.0, -37.8, 145.0, 145.2)

        // act
        val fit = MapProjection.fit(box, width = 400f, height = 400f, padding = 10f)

        // affirm  the drawing is squeezed sideways, so it cannot start at the left edge
        assertEquals(true, fit.left > 10f)

        // assert  north west is the top of the drawing
        assertEquals(fit.top, fit.place(GeoPoint(-37.8, 145.0)).y, 0.01f)
    }

    @Test
    fun `given a box fitted to a square canvas, when north and south are placed, then they use the full height`() {
        // arrange
        val box = GeoBox(-38.0, -37.8, 145.0, 145.2)

        // act
        val fit = MapProjection.fit(box, width = 400f, height = 400f, padding = 10f)

        // assert  400 wide less 10 padding each side
        assertEquals(
            380f,
            fit.place(GeoPoint(-38.0, 145.0)).y - fit.place(GeoPoint(-37.8, 145.0)).y,
            0.01f,
        )
    }

    @Test
    fun `given a shape this far south, when east and west are placed, then longitude is squeezed by the cosine`() {
        // arrange  0.2 degrees of longitude at -37.9 is cos(37.9) = 0.789 of 0.2 degrees of latitude
        val box = GeoBox(-38.0, -37.8, 145.0, 145.2)
        val fit = MapProjection.fit(box, width = 400f, height = 400f, padding = 10f)

        // act
        val across = fit.place(GeoPoint(-38.0, 145.2)).x - fit.place(GeoPoint(-38.0, 145.0)).x

        // assert
        assertEquals(380f * 0.789f, across, 1f)
    }
}
