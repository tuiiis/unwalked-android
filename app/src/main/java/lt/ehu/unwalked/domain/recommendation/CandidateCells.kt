package lt.ehu.unwalked.domain.recommendation

import com.uber.h3core.H3Core
import com.uber.h3core.LengthUnit
import kotlin.math.ceil

class CandidateCells(private val h3: H3Core, private val queryResolution: Int, private val maxCells: Int = 500) {
    private val ringMetres = h3.getHexagonEdgeLengthAvg(queryResolution, LengthUnit.m) * 1.5

    fun around(visited: Map<Long, Int>, radiusMetres: Double = ringMetres): List<String> {
        val rings = ceil(radiusMetres / ringMetres).toInt().coerceAtLeast(1)
        if (visited.isEmpty()) return emptyList()
        val weight = mutableMapOf<Long, Int>()
        visited.forEach { (cell, passes) ->
            val parent = h3.cellToParent(cell, queryResolution)
            weight[parent] = (weight[parent] ?: 0) + passes
        }
        val ordered = weight.entries.sortedByDescending { it.value }.map { it.key }
        val result = LinkedHashSet<Long>()
        ordered.forEach { result += it }
        ordered.forEach { parent -> h3.gridDisk(parent, rings).forEach { result += it } }
        return result.take(maxCells).map { h3.h3ToString(it) }
    }
}
