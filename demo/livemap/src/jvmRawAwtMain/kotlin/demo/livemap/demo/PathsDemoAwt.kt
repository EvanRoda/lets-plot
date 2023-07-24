/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.livemap.demo

object PathsDemoAwt {
    @JvmStatic
    fun main(args: Array<String>) {
        RawAwtDemo(::PathsDemoModel).start()
    }
}