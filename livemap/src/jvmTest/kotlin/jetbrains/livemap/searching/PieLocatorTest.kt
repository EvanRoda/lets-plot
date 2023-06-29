/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.jetbrains.livemap.searching

import jetbrains.datalore.base.typedGeometry.Vec
import jetbrains.datalore.base.values.Color
import jetbrains.datalore.jetbrains.livemap.searching.SearchTestHelper.UNDEFINED_SECTOR
import jetbrains.datalore.jetbrains.livemap.searching.SearchTestHelper.getTargetUnderCoord
import jetbrains.livemap.Client
import jetbrains.livemap.ClientPoint
import jetbrains.livemap.World
import jetbrains.livemap.chart.ChartElementComponent
import jetbrains.livemap.chart.DonutChart
import jetbrains.livemap.chart.PieSpecComponent
import jetbrains.livemap.core.ecs.EcsComponentManager
import jetbrains.livemap.core.ecs.EcsEntity
import jetbrains.livemap.core.ecs.addComponents
import jetbrains.livemap.mapengine.viewport.Viewport
import jetbrains.livemap.mapengine.viewport.ViewportHelper
import jetbrains.livemap.searching.IndexComponent
import org.junit.Test
import java.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.assertEquals

class PieLocatorTest {
    private val viewport = Viewport(ViewportHelper(World.DOMAIN, true, myLoopY = false), ClientPoint(256, 256), 1, 15)
    private val manager = EcsComponentManager()
    private val locator = DonutChart.DonutLocator
    private val r = 10.0
    private val entities = createPie(listOf(2.0, 2.0, 2.0, 2.0))

    private fun createPie(vals: List<Double>): List<EcsEntity> {
        val pies = ArrayList<EcsEntity>()

        manager
            .createEntity("")
            .addComponents {
                + IndexComponent(1, 1)
                + ChartElementComponent()
                + PieSpecComponent().apply {
                    radius = r
                    indices = vals.indices.toList()
                    sliceValues = transformValues2Angles(vals)
                    colors = vals.indices.map { Color.BLACK }
                }
            }
            .let(pies::add)

        return pies
    }

    private fun checkMouseInPieSector(expectedSector: Int, mouseCoord: Vec<Client>) {
        assertEquals(
            expectedSector,
            getTargetUnderCoord(mouseCoord, locator, entities)
        )
    }

    @Test
    fun calculateAngles() {
        assertEquals(
            listOf(
                Math.toRadians(60.0),
                Math.toRadians(60.0),
                Math.toRadians(120.0),
                Math.toRadians(60.0),
                Math.toRadians(60.0)
            ),
            transformValues2Angles(listOf(3.0, -3.0, 6.0, 3.0, -3.0))
        )
    }

    @Test
    fun calculateAnglesForZeroValues() {
        val count = 5
        assertEquals(
            Collections.nCopies(count, 2 * Math.PI / count),
            transformValues2Angles(Collections.nCopies(count, 0.0))
        )
    }

    @Test
    fun mouseInFirstPieSector() {
        checkMouseInPieSector(0, Vec(-4, -4))
    }

    @Test
    fun mouseInSecondPieSector() {
        checkMouseInPieSector(1, Vec(4, -4))
        checkMouseInPieSector(1, Vec(9, -1))
    }

    @Test
    fun mouseInThirdPieSector() {
        checkMouseInPieSector(2, Vec(5, 2))
        checkMouseInPieSector(2, Vec(2, 7))
    }

    @Test
    fun mouseInFourthPieSector() {
        checkMouseInPieSector(3, Vec(-4, 4))
        checkMouseInPieSector(3, Vec(-9, 1))
    }

    @Test
    fun mouseOutOfPie() {
        checkMouseInPieSector(UNDEFINED_SECTOR, Vec(10, 7))
        checkMouseInPieSector(UNDEFINED_SECTOR, Vec(9, 14))
    }

    private fun transformValues2Angles(values: List<Double>): List<Double> {
        val sum = values.sumOf(::abs)
        return if (sum == 0.0) {
            MutableList(values.size) { 2 * PI / values.size }
        } else {
            values.map { 2 * PI * abs(it) / sum }
        }
    }
}