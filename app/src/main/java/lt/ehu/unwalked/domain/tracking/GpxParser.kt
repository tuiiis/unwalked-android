package lt.ehu.unwalked.domain.tracking

import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object GpxParser {
    fun parse(input: InputStream): List<Fix> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(input)
        val nodes = document.getElementsByTagName("trkpt")
        return (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as Element
            val time = element.getElementsByTagName("time").item(0)?.textContent?.trim() ?: return@mapNotNull null
            val accuracy = element.getElementsByTagName("hdop").item(0)?.textContent?.toFloatOrNull()?.times(5f) ?: 10f
            Fix(
                time = Instant.parse(time).toEpochMilli(),
                lat = element.getAttribute("lat").toDouble(),
                lon = element.getAttribute("lon").toDouble(),
                accuracy = accuracy,
            )
        }
    }
}
