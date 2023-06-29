/*
 * Copyright (c) 2019. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.livemap.chart

import jetbrains.datalore.base.function.Consumer
import jetbrains.datalore.base.geometry.DoubleRectangle
import jetbrains.datalore.base.typedGeometry.*
import jetbrains.datalore.base.values.Color
import jetbrains.datalore.vis.canvas.Context2d
import jetbrains.datalore.vis.canvas.LineJoin
import jetbrains.livemap.Client
import jetbrains.livemap.World
import jetbrains.livemap.WorldPoint
import jetbrains.livemap.chart.Utils.changeAlphaWithMin
import jetbrains.livemap.chart.Utils.drawPath
import jetbrains.livemap.core.ecs.EcsEntity
import jetbrains.livemap.geometry.WorldGeometryComponent
import jetbrains.livemap.mapengine.*
import jetbrains.livemap.mapengine.placement.WorldOriginComponent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Renderers {


    fun <T> drawMultiPolygon(geometry: MultiPolygon<T>, ctx: Context2d, afterPolygon: Consumer<Context2d>) {
        for (polygon in geometry) {
            for (ring in polygon) {
                ring[0].let(ctx::moveTo)
                ring.drop(1).forEach(ctx::lineTo)
            }
        }
        afterPolygon(ctx)
    }

    class PointRenderer(
        private val shape: Int
    ) : Renderer {

        override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
            val chartElement = entity.get<ChartElementComponent>()
            val pointData = entity.get<PointComponent>()
            val radius = pointData.size * chartElement.scalingSizeFactor / 2.0
            val stroke = if (!chartElement.strokeWidth.isNaN())
                chartElement.strokeWidth * chartElement.scalingSizeFactor
            else
                0.0

            ctx.translate(renderHelper.toScreen(entity.get<WorldOriginComponent>().origin))

            ctx.beginPath()
            drawPath(ctx, radius, stroke, shape)
            if (chartElement.fillColor != null) {
                ctx.setFillStyle(changeAlphaWithMin(chartElement.fillColor!!, chartElement.scalingAlphaValue))
                ctx.fill()
            }
            if (chartElement.strokeColor != null && stroke > 0.0) {
                ctx.setStrokeStyle(changeAlphaWithMin(chartElement.strokeColor!!, chartElement.scalingAlphaValue))
                ctx.setLineWidth(stroke)
                ctx.stroke()
            }
        }
    }

    class PolygonRenderer : Renderer {
        override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
            val chartElement = entity.get<ChartElementComponent>()
            val geometry = entity.get<WorldGeometryComponent>().geometry.multiPolygon

            ctx.setLineJoin(LineJoin.ROUND)
            ctx.beginPath()

            ctx.save()
            ctx.scale(renderHelper.zoomFactor)
            drawMultiPolygon(geometry, ctx, Context2d::closePath)
            ctx.restore()

            if (chartElement.fillColor != null) {
                ctx.setFillStyle(changeAlphaWithMin(chartElement.fillColor!!, chartElement.scalingAlphaValue))
                ctx.fill()
            }

            if (chartElement.strokeColor != null && chartElement.strokeWidth != 0.0) {
                ctx.setStrokeStyle(changeAlphaWithMin(chartElement.strokeColor!!, chartElement.scalingAlphaValue))
                ctx.setLineWidth(chartElement.strokeWidth * chartElement.scalingSizeFactor)
                ctx.stroke()
            }
        }
    }

    class PathRenderer : Renderer {
        override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
            val geometry = entity.get<WorldGeometryComponent>().geometry.multiLineString
            val chartElement = entity.get<ChartElementComponent>()
            val color = changeAlphaWithMin(chartElement.strokeColor!!, chartElement.scalingAlphaValue)

            ctx.save()
            ctx.scale(renderHelper.zoomFactor)
            ctx.beginPath()

            for (lineString in geometry) {
                lineString[0].let(ctx::moveTo)
                lineString.drop(1).forEach(ctx::lineTo)
            }
            ctx.restore()

            ctx.setStrokeStyle(color)
            ctx.setLineDash(chartElement.lineDash!!.map { it * chartElement.scalingSizeFactor }.toDoubleArray())
            ctx.setLineWidth(chartElement.strokeWidth * chartElement.scalingSizeFactor)
            ctx.stroke()

            chartElement.arrowSpec?.let { arrowSpec ->
                drawArrows(arrowSpec, geometry, color, chartElement.scalingSizeFactor, ctx, renderHelper)
            }
        }

        class ArrowSpec private constructor(
            val angle: Double,
            val length: Double,
            val end: End,
            val type: Type
        ) {
            val isOnFirstEnd: Boolean
                get() = end == End.FIRST || end == End.BOTH

            val isOnLastEnd: Boolean
                get() = end == End.LAST || end == End.BOTH

            fun createGeometry(
                polarAngle: Double,
                x: Double,
                y: Double,
                l: Scalar<World>,
                scalingFactor: Double
            ): Pair<DoubleArray, DoubleArray> {
                val xs = doubleArrayOf(
                    x - l.value * scalingFactor * cos(polarAngle - angle),
                    x,
                    x - l.value * scalingFactor * cos(polarAngle + angle)
                )
                val ys = doubleArrayOf(
                    y - l.value * scalingFactor * sin(polarAngle - angle),
                    y,
                    y - l.value * scalingFactor * sin(polarAngle + angle)
                )
                return xs to ys
            }

            enum class End {
                LAST, FIRST, BOTH
            }

            enum class Type {
                OPEN, CLOSED
            }

            companion object {
                fun create(
                    arrowAngle: Double?,
                    arrowLength: Double?,
                    arrowAtEnds: String?,
                    arrowType: String?
                ): ArrowSpec? {
                    if (arrowAngle == null || arrowLength == null) {
                        return null
                    }
                    val ends = when (arrowAtEnds) {
                        "last" -> End.LAST
                        "first" -> End.FIRST
                        "both" -> End.BOTH
                        else -> throw IllegalArgumentException("Expected: first|last|both")
                    }
                    val type = when (arrowType) {
                        "open" -> Type.OPEN
                        "closed" -> Type.CLOSED
                        else -> throw IllegalArgumentException("Expected: open|closed")
                    }
                    return ArrowSpec(arrowAngle, arrowLength, ends, type)
                }
            }
        }

        private fun drawArrows(
            arrowSpec: ArrowSpec,
            geometry: MultiLineString<World>,
            color: Color,
            scalingSizeFactor: Double,
            ctx: Context2d,
            renderHelper: RenderHelper
        ) {

            fun drawArrowAtEnd(start: WorldPoint, end: WorldPoint, arrowSpec: ArrowSpec) {
                val abscissa = end.x - start.x
                val ordinate = end.y - start.y
                if (abscissa != 0.0 || ordinate != 0.0) {
                    val polarAngle = atan2(ordinate, abscissa)
                    val worldLength = renderHelper.toWorld(Scalar(arrowSpec.length))
                    val (xs, ys) = arrowSpec.createGeometry(polarAngle, end.x, end.y, worldLength, scalingSizeFactor)

                    ctx.save()
                    ctx.scale(renderHelper.zoomFactor)
                    ctx.beginPath()
                    ctx.moveTo(xs[0], ys[0])
                    for (i in 1..2) {
                        ctx.lineTo(xs[i], ys[i])
                    }
                    ctx.restore()

                    ctx.setLineDash(doubleArrayOf())
                    if (arrowSpec.type == ArrowSpec.Type.CLOSED) {
                        ctx.closePath()
                        ctx.setFillStyle(color)
                        ctx.fill()
                    }
                    ctx.stroke()
                }
            }

            for (lineString in geometry) {
                if (arrowSpec.isOnFirstEnd) {
                    val (start, end) = lineString.take(2).reversed()

                    drawArrowAtEnd(start, end, arrowSpec)
                }
                if (arrowSpec.isOnLastEnd) {
                    val (start, end) = lineString.takeLast(2)
                    drawArrowAtEnd(start, end, arrowSpec)
                }
            }
        }
    }

    class TextRenderer : Renderer {
        override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
            val chartElementComponent = entity.get<ChartElementComponent>()
            val textSpec = entity.get<TextSpecComponent>().textSpec

            val textPosition: Vec<Client>

            ctx.translate(renderHelper.toScreen(entity.get<WorldOriginComponent>().origin))
            ctx.rotate(textSpec.angle)

            if (textSpec.drawBorder) {
                val rectangle = textSpec.rectangle
                drawRoundedRectangle(rectangle, textSpec.labelRadius * rectangle.height, ctx)

                if (chartElementComponent.fillColor != null) {
                    ctx.setFillStyle(
                        changeAlphaWithMin(chartElementComponent.fillColor!!, chartElementComponent.scalingAlphaValue)
                    )
                    ctx.fill()
                }
                if (chartElementComponent.strokeColor != null && textSpec.labelSize != 0.0) {
                    ctx.setStrokeStyle(chartElementComponent.strokeColor)
                    ctx.setLineWidth(textSpec.labelSize)
                    ctx.stroke()
                }

                val xPosition = when (textSpec.hjust) {
                    0.0 -> textSpec.padding
                    1.0 -> -textSpec.padding
                    else -> 0.0
                }
                textPosition = explicitVec(
                    xPosition,
                    rectangle.origin.y + textSpec.padding + textSpec.font.fontSize * 0.8 // top-align the first line
                )
            } else {
                val yPosition = with(textSpec) {
                    when (vjust) {
                        1.0 -> font.fontSize * 0.7
                        0.0 -> -textSize.y + font.fontSize
                        else -> -textSize.y / 2 + font.fontSize * 0.8
                    }
                }
                textPosition = explicitVec(0.0, yPosition)
            }

            ctx.setFont(textSpec.font)
            ctx.setFillStyle(chartElementComponent.strokeColor)

            ctx.setTextAlign(textSpec.textAlign)
            textSpec.lines.forEachIndexed { index, line ->
                ctx.fillText(line, textPosition.x, textPosition.y + textSpec.lineHeight * index)
            }
        }

        private fun drawRoundedRectangle(rect: DoubleRectangle, radius: Double, ctx: Context2d) {
            ctx.apply {
                beginPath()
                with(rect) {
                    // Ensure normal radius
                    val r = minOf(radius, rect.width / 2, rect.height / 2)

                    moveTo(right - r, bottom)
                    bezierCurveTo(
                        right - r, bottom,
                        right, bottom,
                        right, bottom - r
                    )

                    lineTo(right, top + r)
                    bezierCurveTo(
                        right, top + r,
                        right, top,
                        right - r, top
                    )

                    lineTo(left + r, top)
                    bezierCurveTo(
                        left + r, top,
                        left, top,
                        left, top + r
                    )

                    lineTo(left, bottom - r)
                    bezierCurveTo(
                        left, bottom - r,
                        left, bottom,
                        left + r, bottom
                    )
                }
                closePath()
            }
        }
    }
}
