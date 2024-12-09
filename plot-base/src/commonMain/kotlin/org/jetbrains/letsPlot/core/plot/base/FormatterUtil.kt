/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base

import org.jetbrains.letsPlot.commons.formatting.string.StringFormat
import org.jetbrains.letsPlot.commons.formatting.string.StringFormat.ExponentFormat
import org.jetbrains.letsPlot.core.commons.data.DataType

object FormatterUtil{
    private const val FLOATING_PATTERN = ",~g"

    fun byDataType(dataType: DataType, expFormat: ExponentFormat): (Any) -> String {
        return when (dataType) {
            DataType.FLOATING -> StringFormat.forOneArg(FLOATING_PATTERN, expFormat = expFormat)::format
            DataType.INTEGER -> StringFormat.forOneArg("d")::format
            DataType.STRING -> StringFormat.forOneArg("{}")::format
            DataType.INSTANT -> StringFormat.forOneArg("%Y-%m-%dT%H:%M:%S")::format
            DataType.BOOLEAN -> StringFormat.forOneArg("{}")::format
            DataType.UNKNOWN -> {
                fun unknownFormatter(value: Any): String = when (value) {
                    is Number -> StringFormat.forOneArg(FLOATING_PATTERN, expFormat = expFormat).format(value)
                    else -> StringFormat.forOneArg("{}").format(value)
                }
                ::unknownFormatter
            }
        }
    }
}
