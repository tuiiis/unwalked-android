package lt.ehu.unwalked.domain.tracking

import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellAggregatorTest {
    private val h3 = H3Core.newInstance()

    private fun run(aggregator: CellAggregator, fixes: List<Fix>) = fixes.flatMap { aggregator.accept(it) }

    @Test
    fun walkingOneKilometerCoversContiguousCells() {
        for (resolution in listOf(11, 12)) {
            val passes = run(CellAggregator(h3, resolution), Tracks.line(0, Tracks.LAT, Tracks.LON, 1_000.0, 1.4))
            val cells = passes.map { it.cell }
            assertEquals(cells.size, cells.toSet().size)
            cells.zipWithNext().forEach { (a, b) -> assertTrue(h3.areNeighborCells(a, b)) }
            println("r$resolution: 1 km walk -> ${cells.size} cells, edge ${h3.getHexagonEdgeLengthAvg(resolution, com.uber.h3core.LengthUnit.m)} m")
        }
    }

    @Test
    fun sparseFixesAreFilledWithGridPath() {
        val passes = run(CellAggregator(h3, 12), Tracks.line(0, Tracks.LAT, Tracks.LON, 1_000.0, 1.4, stepMillis = 60_000))
        passes.map { it.cell }.zipWithNext().forEach { (a, b) -> assertTrue(h3.areNeighborCells(a, b)) }
    }

    @Test
    fun busSegmentIsNotCounted() {
        val aggregator = CellAggregator(h3, 11)
        val passes = run(aggregator, Tracks.line(0, Tracks.LAT, Tracks.LON, 3_000.0, 10.0))
        assertTrue(passes.size <= 1)
        assertTrue(aggregator.vehicleSegments > 10)
    }

    @Test
    fun repeatWithinCooldownIsCountedOnceAndLaterAgain() {
        val aggregator = CellAggregator(h3, 11)
        val first = run(aggregator, Tracks.line(0, Tracks.LAT, Tracks.LON, 500.0, 1.4))
        val back = run(aggregator, Tracks.line(first.last().time + 60_000, Tracks.LAT, Tracks.LON, 500.0, 1.4))
        val later = run(aggregator, Tracks.line(3 * 3_600_000L, Tracks.LAT, Tracks.LON, 500.0, 1.4))
        assertTrue(back.size <= 1)
        assertTrue(later.size >= first.size - 1)
    }

    @Test
    fun standingWithJitterDoesNotInflatePasses() {
        val passes = run(CellAggregator(h3, 12), Tracks.jitter(0, Tracks.LAT, Tracks.LON, 60, 20.0))
        val cells = passes.map { it.cell }.toSet()
        println("r12: 60 min standing with 20 m jitter -> ${passes.size} passes in ${cells.size} cells")
        assertEquals(cells.size, passes.size)
    }

    @Test
    fun noisyWalkingIsNotMistakenForVehicle() {
        val aggregator = CellAggregator(h3, 11)
        run(aggregator, Tracks.noisyLine(0, 2_000.0, 1.3, 4.0))
        val share = aggregator.vehicleSegments.toDouble() / (aggregator.vehicleSegments + aggregator.walkingSegments)
        println("noisy walk 4 m: vehicle share ${"%.2f".format(share)}")
        assertTrue(share < 0.05)
    }

    @Test
    fun noisyBusIsStillVehicle() {
        val aggregator = CellAggregator(h3, 11)
        val passes = run(aggregator, Tracks.noisyLine(0, 4_000.0, 8.0, 4.0))
        println("noisy bus: ${passes.size} passes, vehicle=${aggregator.vehicleSegments} walking=${aggregator.walkingSegments}")
        assertTrue(passes.size <= 3)
    }
}
