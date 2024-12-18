/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.livemap.plotJfx.canvasDemo

import demo.livemap.canvasDemo.TextStyleDemoModel
import javafx.application.Application
import javafx.stage.Stage

class TextStyleDemoJfx : Application() {

    override fun start(theStage: Stage) {
        BaseCanvasDemoJfx { canvas, _ ->
            TextStyleDemoModel(canvas)
        }.start(theStage)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(TextStyleDemoJfx::class.java, *args)
        }
    }
}