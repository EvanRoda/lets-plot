/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package jetbrains.datalore.plot.builder.assemble

import org.jetbrains.letsPlot.commons.interval.DoubleSpan
import org.jetbrains.letsPlot.core.commons.typedKey.TypedKeyHashMap
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsDefaults
import org.jetbrains.letsPlot.core.plot.base.aes.GeomTheme
import org.jetbrains.letsPlot.core.plot.base.annotations.Annotations
import org.jetbrains.letsPlot.core.plot.base.data.DataFrameUtil
import org.jetbrains.letsPlot.core.plot.base.data.TransformVar
import org.jetbrains.letsPlot.core.plot.base.geom.GeomBase
import org.jetbrains.letsPlot.core.plot.base.geom.LiveMapGeom
import org.jetbrains.letsPlot.core.plot.base.geom.LiveMapProvider
import org.jetbrains.letsPlot.core.plot.base.tooltip.ContextualMapping
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetLocator.LookupSpec
import org.jetbrains.letsPlot.core.plot.base.tooltip.MappedDataAccess
import org.jetbrains.letsPlot.core.plot.base.pos.PositionAdjustments
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.stat.SimpleStatContext
import org.jetbrains.letsPlot.core.plot.base.stat.Stats
import org.jetbrains.letsPlot.core.plot.base.util.YOrientationBaseUtil
import org.jetbrains.letsPlot.core.plot.base.util.afterOrientation
import jetbrains.datalore.plot.builder.GeomLayer
import jetbrains.datalore.plot.builder.MarginSide
import jetbrains.datalore.plot.builder.VarBinding
import jetbrains.datalore.plot.builder.annotation.AnnotationLine
import jetbrains.datalore.plot.builder.annotation.AnnotationSpecification
import jetbrains.datalore.plot.builder.assemble.geom.GeomProvider
import jetbrains.datalore.plot.builder.assemble.geom.PointDataAccess
import jetbrains.datalore.plot.builder.data.DataProcessing
import jetbrains.datalore.plot.builder.data.GroupingContext
import jetbrains.datalore.plot.builder.data.StatInput
import org.jetbrains.letsPlot.core.plot.base.tooltip.ContextualMappingProvider
import jetbrains.datalore.plot.builder.presentation.DefaultFontFamilyRegistry
import jetbrains.datalore.plot.builder.presentation.FontFamilyRegistry
import jetbrains.datalore.plot.builder.scale.ScaleProvider
import org.jetbrains.letsPlot.core.plot.base.theme.ThemeTextStyle

class GeomLayerBuilder(
    private val geomProvider: GeomProvider,
    private val stat: Stat,
    private val posProvider: PosProvider,
    private val fontFamilyRegistry: FontFamilyRegistry,
) {

    private val myBindings = ArrayList<VarBinding>()
    private val myConstantByAes = TypedKeyHashMap()
    private var myGroupingVarName: String? = null
    private var myPathIdVarName: String? = null
    private val myScaleProviderByAes = HashMap<org.jetbrains.letsPlot.core.plot.base.Aes<*>, ScaleProvider>()

    private var myDataPreprocessor: ((DataFrame, Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, Transform>) -> DataFrame)? = null
    private var myLocatorLookupSpec: LookupSpec = LookupSpec.NONE
    private var myContextualMappingProvider: ContextualMappingProvider = ContextualMappingProvider.NONE

    private var myIsLegendDisabled: Boolean = false
    private var isYOrientation: Boolean = false

    private var isMarginal: Boolean = false
    private var marginalSide: MarginSide = MarginSide.LEFT
    private var marginalSize: Double = Double.NaN

    private var colorByAes: org.jetbrains.letsPlot.core.plot.base.Aes<Color> = org.jetbrains.letsPlot.core.plot.base.Aes.COLOR
    private var fillByAes: org.jetbrains.letsPlot.core.plot.base.Aes<Color> = org.jetbrains.letsPlot.core.plot.base.Aes.FILL

    private var myAnnotationsProvider: ((MappedDataAccess, DataFrame) -> Annotations?)? = null

    private lateinit var myGeomTheme: GeomTheme

    fun addBinding(v: VarBinding): GeomLayerBuilder {
        myBindings.add(v)
        return this
    }

    fun groupingVar(v: DataFrame.Variable): GeomLayerBuilder {
        myGroupingVarName = v.name
        return this
    }

    fun groupingVarName(v: String): GeomLayerBuilder {
        myGroupingVarName = v
        return this
    }

    fun pathIdVarName(v: String): GeomLayerBuilder {
        myPathIdVarName = v
        return this
    }

    fun <T> addConstantAes(aes: org.jetbrains.letsPlot.core.plot.base.Aes<T>, v: T): GeomLayerBuilder {
        myConstantByAes.put(aes, v)
        return this
    }

    fun <T> addScaleProvider(aes: org.jetbrains.letsPlot.core.plot.base.Aes<T>, scaleProvider: ScaleProvider): GeomLayerBuilder {
        myScaleProviderByAes[aes] = scaleProvider
        return this
    }

    fun locatorLookupSpec(v: LookupSpec): GeomLayerBuilder {
        myLocatorLookupSpec = v
        return this
    }

    fun contextualMappingProvider(v: ContextualMappingProvider): GeomLayerBuilder {
        myContextualMappingProvider = v
        return this
    }

    fun disableLegend(v: Boolean): GeomLayerBuilder {
        myIsLegendDisabled = v
        return this
    }


    fun yOrientation(v: Boolean): GeomLayerBuilder {
        isYOrientation = v
        return this
    }

    fun marginal(
        isMarginal: Boolean,
        marginalSide: MarginSide,
        marginalSize: Double
    ): GeomLayerBuilder {
        this.isMarginal = isMarginal
        this.marginalSide = marginalSide
        this.marginalSize = marginalSize
        return this
    }

    fun annotationSpecification(
        annotationSpec: AnnotationSpecification,
        themeTextStyle: ThemeTextStyle
    ): GeomLayerBuilder {
        myAnnotationsProvider = { dataAccess, dataFrame ->
            AnnotationLine.createAnnotations(annotationSpec, dataAccess, dataFrame, themeTextStyle)
        }
        return this
    }

    fun colorByAes(aes: org.jetbrains.letsPlot.core.plot.base.Aes<Color>): GeomLayerBuilder {
        colorByAes = aes
        return this
    }

    fun fillByAes(aes: org.jetbrains.letsPlot.core.plot.base.Aes<Color>): GeomLayerBuilder {
        fillByAes = aes
        return this
    }

    fun geomTheme(geomTheme: GeomTheme): GeomLayerBuilder {
        myGeomTheme = geomTheme
        return this
    }

    fun build(
        data: DataFrame,
        scaleMap: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, Scale>,
        scaleMapppersNP: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, ScaleMapper<*>>,
    ): GeomLayer {
        val transformByAes: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, Transform> = scaleMap.keys.associateWith {
            scaleMap.getValue(it).transform
        }

        @Suppress("NAME_SHADOWING")
        var data = data
        if (myDataPreprocessor != null) {
            // Test and Demo
            data = myDataPreprocessor!!(data, transformByAes)
        }

        // make sure 'original' series are transformed
        data = DataProcessing.transformOriginals(data, myBindings, transformByAes)

        val replacementBindings = HashMap(
            // No 'origin' variables beyond this point.
            // Replace all 'origin' variables in bindings with 'transform' variables
            myBindings.associate {
                it.aes to if (it.variable.isOrigin) {
                    val transformVar = DataFrameUtil.transformVarFor(it.aes)
                    VarBinding(transformVar, it.aes)
                } else {
                    it
                }
            }
        )

        // add 'transform' variable for each 'stat' variable
        val bindingsToPut = ArrayList<VarBinding>()
        for (binding in replacementBindings.values) {
            val variable = binding.variable
            if (variable.isStat) {
                val aes = binding.aes
                val transform = transformByAes.getValue(aes)
                val transformVar = TransformVar.forAes(aes)
                data = DataFrameUtil.applyTransform(data, variable, transformVar, transform)
                bindingsToPut.add(VarBinding(transformVar, aes))
            }
        }

        // replace 'stat' vars with 'transform' vars in bindings
        for (binding in bindingsToPut) {
            replacementBindings[binding.aes] = binding
        }

        // (!) Positional aes scales have undefined `mapper` at this time because
        // dimensions of plot are not yet known.
        // Data Access shouldn't use aes mapper (!)
//        val dataAccess = PointDataAccess(data, replacementBindings, scaleMap)

        val groupingVariables = DataProcessing.defaultGroupingVariables(
            data,
            myBindings,
            myPathIdVarName
        )

        val groupingContext = GroupingContext(data, groupingVariables, myGroupingVarName, handlesGroups())
        return MyGeomLayer(
            data,
            geomProvider,
            myGeomTheme,
            posProvider,
            groupingContext.groupMapper,
            replacementBindings,
            myConstantByAes,
            scaleMap,
            scaleMapppersNP,
            myLocatorLookupSpec,
            myContextualMappingProvider,
            myIsLegendDisabled,
            isYOrientation = isYOrientation,
            isMarginal = isMarginal,
            marginalSide = marginalSide,
            marginalSize = marginalSize,
            fontFamilyRegistry = fontFamilyRegistry,
            colorByAes = colorByAes,
            fillByAes = fillByAes,
            annotationsProvider = myAnnotationsProvider
        )
    }

    private fun handlesGroups(): Boolean {
        return geomProvider.handlesGroups || posProvider.handlesGroups()
    }


    private class MyGeomLayer(
        override val dataFrame: DataFrame,
        geomProvider: GeomProvider,
        geomTheme: GeomTheme,
        override val posProvider: PosProvider,
        override val group: (Int) -> Int,
        private val varBindings: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, VarBinding>,
        private val constantByAes: TypedKeyHashMap,
        override val scaleMap: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, Scale>,
        override val scaleMappersNP: Map<org.jetbrains.letsPlot.core.plot.base.Aes<*>, ScaleMapper<*>>,
        override val locatorLookupSpec: LookupSpec,
        private val contextualMappingProvider: ContextualMappingProvider,
        override val isLegendDisabled: Boolean,
        override val isYOrientation: Boolean,
        override val isMarginal: Boolean,
        override val marginalSide: MarginSide,
        override val marginalSize: Double,
        override val fontFamilyRegistry: FontFamilyRegistry,
        override val colorByAes: org.jetbrains.letsPlot.core.plot.base.Aes<Color>,
        override val fillByAes: org.jetbrains.letsPlot.core.plot.base.Aes<Color>,
        private val annotationsProvider: ((MappedDataAccess, DataFrame) -> Annotations?)?
    ) : GeomLayer {

        override val geom: Geom = geomProvider.createGeom(
            object : GeomProvider.Context() {
                override fun hasBinding(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): Boolean = varBindings.containsKey(aes)
                override fun hasConstant(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): Boolean = constantByAes.containsKey(aes)
            }
        )
        override val geomKind: GeomKind = geomProvider.geomKind
        override val aestheticsDefaults: AestheticsDefaults = AestheticsDefaults.create(geomKind, geomTheme)

        private val myRenderedAes: List<org.jetbrains.letsPlot.core.plot.base.Aes<*>> = GeomMeta.renders(
            geomProvider.geomKind,
            colorByAes, fillByAes,
            exclude = geom.wontRender
        )

        override val legendKeyElementFactory: LegendKeyElementFactory
            get() = geom.legendKeyElementFactory

        override val isLiveMap: Boolean
            get() = geom is LiveMapGeom


        override fun renderedAes(considerOrientation: Boolean): List<org.jetbrains.letsPlot.core.plot.base.Aes<*>> {
            return if (considerOrientation && isYOrientation) {
                myRenderedAes.map { YOrientationBaseUtil.flipAes(it) }
            } else {
                myRenderedAes
            }
        }

        override fun hasBinding(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): Boolean {
            return varBindings.containsKey(aes)
        }

        override fun <T> getBinding(aes: org.jetbrains.letsPlot.core.plot.base.Aes<T>): VarBinding {
            return varBindings[aes]!!
        }

        override fun hasConstant(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): Boolean {
            return constantByAes.containsKey(aes)
        }

        override fun <T> getConstant(aes: org.jetbrains.letsPlot.core.plot.base.Aes<T>): T {
            require(hasConstant(aes)) { "Constant value is not defined for aes $aes" }
            return constantByAes[aes]
        }

        override fun <T> getDefault(aes: org.jetbrains.letsPlot.core.plot.base.Aes<T>): T {
            return aestheticsDefaults.defaultValue(aes)
        }

        override fun preferableNullDomain(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): DoubleSpan {
            @Suppress("NAME_SHADOWING")
            val aes = aes.afterOrientation(isYOrientation)
            return (geom as GeomBase).preferableNullDomain(aes)
        }

        override fun rangeIncludesZero(aes: org.jetbrains.letsPlot.core.plot.base.Aes<*>): Boolean {
            @Suppress("NAME_SHADOWING")
            val aes = aes.afterOrientation(isYOrientation)
            return geom.rangeIncludesZero(aes)
        }

        override fun setLiveMapProvider(liveMapProvider: LiveMapProvider) {
            if (geom is LiveMapGeom) {
                geom.setLiveMapProvider(liveMapProvider)
            } else {
                throw IllegalStateException("Not Livemap: " + geom::class.simpleName)
            }
        }

        override fun createContextualMapping(): ContextualMapping {
            val dataAccess = PointDataAccess(dataFrame, varBindings, scaleMap, isYOrientation)
            return contextualMappingProvider.createContextualMapping(dataAccess, dataFrame)
        }

        override fun createAnnotations(): Annotations? {
            return annotationsProvider?.let { provider ->
                val dataAccess = PointDataAccess(dataFrame, varBindings, scaleMap, isYOrientation)
                provider(dataAccess, dataFrame)
            }
        }
    }

    companion object {

        fun demoAndTest(
            geomProvider: GeomProvider,
            stat: Stat,
            posProvider: PosProvider = PosProvider.wrap(PositionAdjustments.identity()),
        ): GeomLayerBuilder {
            val builder = GeomLayerBuilder(geomProvider, stat, posProvider, DefaultFontFamilyRegistry())
            builder.myDataPreprocessor = { data, transformByAes ->
                val transformedData = DataProcessing.transformOriginals(data, builder.myBindings, transformByAes)
                when (builder.stat) {
                    Stats.IDENTITY -> transformedData
                    else -> {
                        val mappedStatVariables = builder.myBindings.map(VarBinding::variable).filter(DataFrame.Variable::isStat)
                        val statCtx = SimpleStatContext(transformedData, mappedStatVariables)
                        val groupingVariables = DataProcessing.defaultGroupingVariables(
                            data,
                            builder.myBindings,
                            builder.myPathIdVarName
                        )
                        val groupingCtx = GroupingContext(
                            transformedData,
                            groupingVariables,
                            builder.myGroupingVarName,
                            expectMultiple = true
                        )
                        val statInput = StatInput(
                            transformedData,
                            builder.myBindings,
                            transformByAes,
                            statCtx,
                            flipXY = false
                        )
                        val dataAndGroupingContext = DataProcessing.buildStatData(
                            statInput,
                            builder.stat,
                            groupingCtx,
                            facetVariables = emptyList(),
                            varsWithoutBinding = emptyList(),
                            orderOptions = emptyList(),
                            aggregateOperation = null,
                            ::println
                        )

                        dataAndGroupingContext.data
                    }
                }
            }

            return builder
        }
    }
}
