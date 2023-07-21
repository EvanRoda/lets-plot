/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.plotDemo.plotConfig

import jetbrains.datalore.plotDemo.model.plotConfig.ScaleBrewerWithContinuousData
import jetbrains.datalore.vis.demoUtils.PlotSpecsDemoWindowBatik

fun main() {
    with(ScaleBrewerWithContinuousData()) {
        PlotSpecsDemoWindowBatik(
            "Scale Brewer with discrete data",
            plotSpecList(),
            2
        ).open()
    }
}