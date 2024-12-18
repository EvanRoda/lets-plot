/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.livemap.demo

import demo.livemap.LiveMapBrowserDemoUtil


private const val CALL_FUN = "demo.livemap.demo.vectorTilesPerfDemo"

fun main() {
    LiveMapBrowserDemoUtil.openInBrowser {
        LiveMapBrowserDemoUtil.mapperDemoHtml(CALL_FUN, "Vector Tiles Perf Demo")
    }
}