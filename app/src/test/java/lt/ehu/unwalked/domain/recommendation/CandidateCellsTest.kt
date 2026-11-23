package lt.ehu.unwalked.domain.recommendation

import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateCellsTest {
    private val h3 = H3Core.newInstance()

    @Test
    fun includesParentsFirstThenTheirNeighbours() {
        val visited = h3.latLngToCell(54.6872, 25.2797, 11)
        val cells = CandidateCells(h3, 9).around(mapOf(visited to 3))
        assertEquals(h3.h3ToString(h3.cellToParent(visited, 9)), cells.first())
        assertEquals(7, cells.size)
        assertTrue(cells.all { h3.getResolution(it) == 9 })
    }

    @Test
    fun capsTheNumberOfCells() {
        val visited = h3.gridDisk(h3.latLngToCell(54.6872, 25.2797, 9), 15).associate { h3.cellToCenterChild(it, 11) to 1 }
        assertEquals(50, CandidateCells(h3, 9, maxCells = 50).around(visited).size)
    }

    @Test
    fun emptyHistoryGivesNoCells() {
        assertEquals(emptyList<String>(), CandidateCells(h3, 9).around(emptyMap()))
    }
}
