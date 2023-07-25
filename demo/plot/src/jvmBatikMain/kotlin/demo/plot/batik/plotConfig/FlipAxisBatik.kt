/*
 * Copyright (c) 2021. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.plot.batik.plotConfig

import demo.plot.common.model.plotConfig.FlipAxis
import demo.common.batik.demoUtils.PlotSpecsDemoWindowBatik

fun main() {
    with(FlipAxis()) {
        PlotSpecsDemoWindowBatik(
            "Flip axis.",
            plotSpecList()
        ).open()
    }
}
