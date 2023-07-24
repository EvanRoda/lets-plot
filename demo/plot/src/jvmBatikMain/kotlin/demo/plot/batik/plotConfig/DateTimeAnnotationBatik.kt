/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.plotDemo.plotConfig

import demo.common.batik.demoUtils.PlotSpecsDemoWindowBatik
import demo.plot.shared.model.scale.DateTimeAnnotation
import java.awt.Dimension

fun main() {
    with(DateTimeAnnotation()) {
        PlotSpecsDemoWindowBatik(
            "DateTime annotation",
            plotSpecList(),
            2,
            Dimension(600, 400)
        ).open()
    }
}