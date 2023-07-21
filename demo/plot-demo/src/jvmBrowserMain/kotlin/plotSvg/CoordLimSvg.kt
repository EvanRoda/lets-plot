/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.plotSvg

import jetbrains.datalore.plotDemo.model.plotConfig.CoordLim

object CoordLimSvg {
    @JvmStatic
    fun main(args: Array<String>) {
        with(CoordLim()) {
            @Suppress("UNCHECKED_CAST")
            (PlotSvgDemoUtil.show(
                "coord x/y limits",
                plotSpecList(),
//                demoComponentSize
            ))
        }
    }
}