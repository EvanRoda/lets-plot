/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.plotDemo.plotConfig

import demo.plot.common.model.plotConfig.ScaleLimitsDiscrete
import demo.common.batik.demoUtils.PlotSpecsDemoWindowBatik
import java.awt.Dimension

fun main() {
    with(ScaleLimitsDiscrete()) {
        PlotSpecsDemoWindowBatik(
            "Scale limits (discrete)",
            plotSpecList(),
            2,
            Dimension(600, 200)
        ).open()
    }
}