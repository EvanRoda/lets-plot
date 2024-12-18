/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.livemap.plotBatik.canvasDemo

import demo.livemap.canvasDemo.ScaleRotateTranslateDemoModel

class ScaleRotateTranslateDemoAwt {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            baseCanvasDemo { canvas, _ ->
                ScaleRotateTranslateDemoModel(canvas)
            }
        }
    }
}