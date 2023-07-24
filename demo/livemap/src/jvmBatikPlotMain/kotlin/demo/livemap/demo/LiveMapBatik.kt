/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.livemap.demo

import demo.livemap.plotDemo.LiveMap
import jetbrains.datalore.vis.demoUtils.PlotSpecsDemoWindowBatik

object LiveMapBatik {
    @JvmStatic
    fun main(args: Array<String>) {
        with(LiveMap()) {
            PlotSpecsDemoWindowBatik(
                "LiveMap (Batik)",
                plotSpecList(),
                maxCol = 4
            ).open()
        }
    }
}