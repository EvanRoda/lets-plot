/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.scale.breaks

import org.jetbrains.letsPlot.commons.formatting.datetime.DateTimeFormatUtil.createInstantFormatter
import org.jetbrains.letsPlot.commons.formatting.string.StringFormat.ExponentFormat.Companion.DEF_EXPONENT_FORMAT
import org.jetbrains.letsPlot.commons.intern.datetime.TimeZone
import org.jetbrains.letsPlot.core.commons.time.TimeUtil
import org.jetbrains.letsPlot.core.commons.time.interval.NiceTimeInterval
import org.jetbrains.letsPlot.core.commons.time.interval.TimeInterval
import org.jetbrains.letsPlot.core.commons.time.interval.YearInterval
import kotlin.math.round

class DateTimeBreaksHelper(
    rangeStart: Double,
    rangeEnd: Double,
    count: Int,
    private val providedFormatter: ((Any) -> String)?,
    minInterval: TimeInterval? = null,
) : BreaksHelperBase(rangeStart, rangeEnd, count) {

    override val breaks: List<Double>
    val formatter: (Any) -> String
    val pattern: String

    init {
        val step = targetStep

        pattern = if (step < 1000) {        // milliseconds
            val formatterFactory = TimeScaleTickFormatterFactory(minInterval)
            // compute a step so that it is multiple of automatic time steps
            breaks = LinearBreaksHelper(rangeStart, rangeEnd, count, DUMMY_FORMATTER, DEF_EXPONENT_FORMAT).breaks
            formatterFactory.formatPattern(step)

        } else {

            val start = normalStart
            val end = normalEnd

            var ticks: MutableList<Double>? = null
            if (minInterval != null) {
                ticks = minInterval.range(start, end).toMutableList()
            }

            val pattern = if (ticks != null && ticks.size <= count) {
                // same or smaller interval requested -> stay with the min interval
                minInterval!!.tickFormatPattern
                // otherwise - larger step requested -> compute ticks
            } else if (step > YearInterval.MS) {        // years
                ticks = ArrayList()
                val startDateTime = TimeUtil.asDateTimeUTC(start)
                var startYear = startDateTime.year
                if (startDateTime > TimeUtil.yearStart(startYear)) {
                    startYear++
                }
                val endYear = TimeUtil.asDateTimeUTC(end).year
                val helper = LinearBreaksHelper(
                    startYear.toDouble(),
                    endYear.toDouble(),
                    count,
                    expFormat = DEF_EXPONENT_FORMAT,
                    providedFormatter = DUMMY_FORMATTER
                )
                for (tickYear in helper.breaks) {
                    val tickDate = TimeUtil.yearStart(round(tickYear).toInt())
                    ticks.add(TimeUtil.asInstantUTC(tickDate).toDouble())
                }
                YearInterval.TICK_FORMAT
            } else {
                val interval = NiceTimeInterval.forMillis(step)
                ticks = interval.range(start, end).toMutableList()
                interval.tickFormatPattern
            }

            if (isReversed) {
                ticks.reverse()
            }
            breaks = ticks

            pattern
        }

        formatter = providedFormatter ?: createInstantFormatter(pattern, TimeZone.UTC)
    }
}
