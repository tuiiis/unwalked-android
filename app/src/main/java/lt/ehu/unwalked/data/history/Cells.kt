package lt.ehu.unwalked.data.history

import com.uber.h3core.H3Core

object Cells {
    const val RESOLUTION = 11
    const val AREA_RESOLUTION = 8
    const val QUERY_RESOLUTION = 9
    const val NEAR_QUERY_RESOLUTION = 10

    val h3: H3Core by lazy {
        if (System.getProperty("java.vendor").orEmpty().contains("Android")) H3Core.newSystemInstance() else H3Core.newInstance()
    }
}
