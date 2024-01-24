/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.spec.front

import org.jetbrains.letsPlot.commons.interval.DoubleSpan
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.data.DataFrameUtil.variables
import org.jetbrains.letsPlot.core.plot.base.scale.transform.Transforms
import org.jetbrains.letsPlot.core.plot.base.theme.FontFamilyRegistry
import org.jetbrains.letsPlot.core.plot.base.theme.Theme
import org.jetbrains.letsPlot.core.plot.builder.GeomLayer
import org.jetbrains.letsPlot.core.plot.builder.MarginalLayerUtil
import org.jetbrains.letsPlot.core.plot.builder.VarBinding
import org.jetbrains.letsPlot.core.plot.builder.assemble.GeomLayerBuilder
import org.jetbrains.letsPlot.core.plot.builder.assemble.GuideOptions
import org.jetbrains.letsPlot.core.plot.builder.assemble.PlotAssembler
import org.jetbrains.letsPlot.core.plot.builder.assemble.PlotFacets
import org.jetbrains.letsPlot.core.plot.builder.coord.CoordProvider
import org.jetbrains.letsPlot.core.plot.builder.coord.CoordProviders
import org.jetbrains.letsPlot.core.plot.builder.tooltip.conf.GeomInteraction
import org.jetbrains.letsPlot.core.spec.Option
import org.jetbrains.letsPlot.core.spec.PlotConfigUtil
import org.jetbrains.letsPlot.core.spec.config.*

object PlotConfigFrontendUtil {
    internal fun createGuideOptionsMap(scaleConfigs: List<ScaleConfig<*>>): Map<Aes<*>, GuideOptions> {
        val guideOptionsByAes = HashMap<Aes<*>, GuideOptions>()
        for (scaleConfig in scaleConfigs) {
            if (scaleConfig.hasGuideOptions()) {
                val guideOptions = scaleConfig.getGuideOptions().createGuideOptions()
                guideOptionsByAes[scaleConfig.aes] = guideOptions
            }
        }
        return guideOptionsByAes
    }

    internal fun createGuideOptionsMap(guideOptionsList: Map<String, Any>): Map<Aes<*>, GuideOptions> {
        val guideOptionsByAes = HashMap<Aes<*>, GuideOptions>()
        for ((key, value) in guideOptionsList) {
            val aes = Option.Mapping.toAes(key)
            guideOptionsByAes[aes] = GuideConfig.create(value).createGuideOptions()
        }
        return guideOptionsByAes
    }

    internal fun createMappersAndScalesBeforeFacets(config: PlotConfigFrontend): Pair<Map<Aes<*>, ScaleMapper<*>>, Map<Aes<*>, Scale>> {
        val layerConfigs = config.layerConfigs

        val transformByAes = PlotConfigTransforms.createTransforms(
            layerConfigs,
            config.scaleProviderByAes,
            config.mapperProviderByAes,
            excludeStatVariables = false   // Frontend - take in account "stat" variables
        )

        val mappersByAes = PlotConfigScaleMappers.createMappers(
            layerConfigs,
            transformByAes,
            config.mapperProviderByAes,
        )

        val scaleByAes = PlotConfigScales.createScales(
            layerConfigs,
            transformByAes,
            mappersByAes,
            config.scaleProviderByAes
        )
        return Pair(mappersByAes, scaleByAes)
    }

    fun createPlotAssembler(
        config: PlotConfigFrontend,
        sharedContinuousDomainX: DoubleSpan? = null,
        sharedContinuousDomainY: DoubleSpan? = null,
    ): PlotAssembler {

        // Scale "before facets".
        val (mappersByAesNP, scalesBeforeFacets) = createMappersAndScalesBeforeFacets(config).let { (mappers, scales) ->
            // Adjust domains of continuous scales when axis are shared between plots in a composite figure.
            val scalesAdjusted: Map<Aes<*>, Scale> = scales.mapValues { (aes, scale) ->
                if (aes == Aes.X && sharedContinuousDomainX != null) {
                    scale.with().continuousTransform(
                        Transforms.continuousWithLimits(
                            scale.transform as ContinuousTransform,
                            sharedContinuousDomainX.toPair()
                        )
                    ).build()
                } else if (aes == Aes.Y && sharedContinuousDomainY != null) {
                    scale.with().continuousTransform(
                        Transforms.continuousWithLimits(
                            scale.transform as ContinuousTransform,
                            sharedContinuousDomainY.toPair()
                        )
                    ).build()
                } else {
                    scale
                }
            }

            // Take only non-positional mappers
            Pair(
                mappers.filterKeys { !Aes.isPositional(it) },
                scalesAdjusted)
        }


        // Coord provider

        val preferredCoordProvider: CoordProvider? = config.layerConfigs.firstNotNullOfOrNull {
            it.geomProto.preferredCoordinateSystem(it)
        }

        val defaultCoordProvider = preferredCoordProvider ?: CoordProviders.cartesian()
        val coordProvider = CoordConfig.createCoordProvider(
            config.get(Option.Plot.COORD),
            scalesBeforeFacets.getValue(Aes.X).transform,
            scalesBeforeFacets.getValue(Aes.Y).transform,
            defaultCoordProvider
        )


        // Geom layers

        val layersByTile = buildPlotLayers(
            config.layerConfigs,
            config.facets,
            coordProvider,
            scalesBeforeFacets,
            mappersByAesNP,
            config.theme,
            config.theme.fontFamilyRegistry,
        )

        return PlotAssembler(
            layersByTile,
            scalesBeforeFacets,
            mappersByAesNP,
            config.facets,
            coordProvider,
            config.xAxisPosition,
            config.yAxisPosition,
            config.theme,
            title = config.title,
            subtitle = config.subtitle,
            caption = config.caption,
            guideOptionsMap = config.guideOptionsMap
        )
    }

    private fun buildPlotLayers(
        layerConfigs: List<LayerConfig>,
        facets: PlotFacets,
        coordProvider: CoordProvider,
        commonScaleMap: Map<Aes<*>, Scale>,
        mappersByAesNP: Map<Aes<*>, ScaleMapper<*>>, // all non-positional mappers
        theme: Theme,
        fontRegistry: FontFamilyRegistry
    ): List<List<GeomLayer>> {
        val isLiveMap = layerConfigs.any { it.geomProto.geomKind == GeomKind.LIVE_MAP }
        val geomLayerListByTile: MutableList<MutableList<GeomLayer>> = mutableListOf()

        for ((layerIndex, layerConfig) in layerConfigs.withIndex()) {
            //
            // Layer scales
            //
            val layerCommonScales = when (layerConfig.isMarginal) {
                true -> MarginalLayerUtil.toMarginalScaleMap(
                    commonScaleMap,
                    layerConfig.marginalSide,
                    flipOrientation = false    // Positional aes are already flipped in the "common scale map".
                )

                false -> commonScaleMap
            }

            val layerAddedScales = createScalesForStatPositionalBindings(
                layerConfig.varBindings,
                layerConfig.isYOrientation,
                commonScaleMap
            ).let { scaleByAes ->
                when (layerConfig.isMarginal) {
                    true -> MarginalLayerUtil.toMarginalScaleMap(
                        scaleByAes,
                        layerConfig.marginalSide,
                        flipOrientation = layerConfig.isYOrientation
                    )

                    false -> scaleByAes
                }
            }

            val layerScaleMap = layerCommonScales + layerAddedScales

            //
            // Layer geom interaction
            //
            val geomInteraction = if (layerConfig.isMarginal) {
                // marginal layer doesn't have interactions
                null
            } else {
                val otherLayerWithTooltips = layerConfigs
                    .filterIndexed { index, _ -> index != layerIndex }
                    .any { !it.tooltips.hideTooltips() }

                GeomInteractionUtil.configGeomTargets(
                    layerConfig,
                    layerScaleMap,
                    otherLayerWithTooltips,
                    isLiveMap,
                    coordProvider.isLinear,
                    theme
                )
            }

            //
            // Layer builder
            //
            val geomLayerBuilder = createLayerBuilder(
                layerConfig,
                fontRegistry,
                geomInteraction,
                theme
            )

            //
            // Layer tiles
            //
            val layerData = layerConfig.combinedData
            val layerDataByTile = PlotConfigUtil.splitLayerDataByTile(layerData, facets)

            val geomLayerByTile = layerDataByTile.map { layerTileData ->
                geomLayerBuilder.build(
                    layerTileData,
                    layerScaleMap,
                    mappersByAesNP,
                )
            }

            //
            // Stack geom layers by tile.
            //
            if (geomLayerListByTile.isEmpty()) {
                geomLayerByTile.forEach { _ -> geomLayerListByTile.add(ArrayList<GeomLayer>()) }
            }
            for ((tileIndex, geomLayer) in geomLayerByTile.withIndex()) {
                val tileGeomLayers = geomLayerListByTile[tileIndex]
                tileGeomLayers.add(geomLayer)
            }
        }

        return geomLayerListByTile
    }

    private fun createScalesForStatPositionalBindings(
        layerVarBindings: List<VarBinding>,
        isYOrientation: Boolean,
        commonScaleMap: Map<Aes<*>, Scale>,
    ): Map<Aes<*>, Scale> {
        val statPositionalBindings =
            layerVarBindings.filter { it.variable.isStat }
                .filterNot { it.aes == Aes.X || it.aes == Aes.Y }
                .filter { Aes.isPositionalXY(it.aes) }

        return statPositionalBindings.associate { binding ->
            val positionalAes = when (isYOrientation) {
                true -> if (Aes.isPositionalX(binding.aes)) Aes.Y else Aes.X
                false -> if (Aes.isPositionalX(binding.aes)) Aes.X else Aes.Y
            }
            val scaleProto = commonScaleMap.getValue(positionalAes)
            val aesScale = scaleProto.with().name(binding.variable.label).build()
            binding.aes to aesScale
        }
    }

    private fun createLayerBuilder(
        layerConfig: LayerConfig,
        fontFamilyRegistry: FontFamilyRegistry,
        geomInteraction: GeomInteraction?,
        theme: Theme
    ): GeomLayerBuilder {
        val geomProvider =
            layerConfig.geomProto.geomProvider(layerConfig, layerConfig.aopConversion, theme.exponentFormat.superscript)

        val stat = layerConfig.stat
        val layerBuilder = GeomLayerBuilder(
            geomProvider = geomProvider,
            stat = stat,
            posProvider = layerConfig.posProvider,
            fontFamilyRegistry = fontFamilyRegistry
        )
            .yOrientation(layerConfig.isYOrientation)
            .marginal(layerConfig.isMarginal, layerConfig.marginalSide, layerConfig.marginalSize)

        // Color aesthetics
        layerBuilder
            .colorByAes(layerConfig.colorByAes)
            .fillByAes(layerConfig.fillByAes)

        // geomTheme
        layerBuilder.geomTheme(theme.geometries(layerConfig.geomProto.geomKind))

        layerBuilder.superscriptExponent(theme.exponentFormat.superscript)

        val constantAesMap = layerConfig.constantsMap
        for (aes in constantAesMap.keys) {
            @Suppress("UNCHECKED_CAST", "MapGetWithNotNullAssertionOperator")
            layerBuilder.addConstantAes(aes as Aes<Any>, constantAesMap[aes]!!)
        }

        if (layerConfig.hasExplicitGrouping()) {
            layerBuilder.groupingVarName(layerConfig.explicitGroupingVarName!!)
        }

        // no map_join, data=gdf or map=gdf - group values and geometries by GEO_ID
        variables(layerConfig.combinedData)[GeoConfig.GEO_ID]?.let {
            layerBuilder.pathIdVarName(GeoConfig.GEO_ID)
        }

        // variable bindings
        val bindings = layerConfig.varBindings
        for (binding in bindings) {
            layerBuilder.addBinding(binding)
        }

        layerBuilder.disableLegend(layerConfig.isLegendDisabled)

        geomInteraction?.let {
            layerBuilder
                .locatorLookupSpec(it.createLookupSpec())
                .contextualMappingProvider(it)
        }
        // annotations
        layerBuilder.annotationSpecification(layerConfig.annotations, theme.annotations().textStyle())

        return layerBuilder
    }
}
