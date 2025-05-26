package no.kartverket.nibas.nibasarbeidsliste.dto

import no.kartverket.nibas.nibasarbeidsliste.model.MatrikkelGrenselinje
import java.math.BigDecimal
import java.math.RoundingMode

private const val UTM33_EXTENT = 2684354.56
private const val UTM33_CENTER_X = 500000.0
private const val UTM33_CENTER_Y = 7714626.0
private const val EXTENT = 536870912  // 2^29
private const val UTM33_RESOLUTION = UTM33_EXTENT / EXTENT
private const val UTM33_X_MIN_INCLUSIVE = UTM33_CENTER_X - UTM33_EXTENT / 2.0
private const val UTM33_Y_MIN_EXCLUSIVE = UTM33_CENTER_Y - UTM33_EXTENT / 2.0

/**
 * DTO for MatrikkelGrenselinje that converts to GeoJSON format.
 */
data class MatrikkelGrenselinjeDto(
    val type: String = "Feature",
    val geometry: GeometryDto,
    val properties: PropertiesDto
) {
    data class GeometryDto(
        val type: String = "LineString",
        val coordinates: List<List<Double>>
    )

    data class PropertiesDto(
        val id: Long,
        val kommunenr1: String?,
        val kommunenr2: String?,
        val hjelpelinjetypeId: Short?,
        val administrativgrensekodeId: Short?,
        val malemetodeId: Short?,
        val noyaktighet: Int?,
        val lagretNoyaktighetsklasse: Short?
    )

    companion object {
        /**
         * Converts a MatrikkelGrenselinje entity to a GeoJSON Feature DTO.
         */
        fun fromEntity(entity: MatrikkelGrenselinje): MatrikkelGrenselinjeDto {
            val coordinates = entity.geom?.coordinates?.map { coord ->
                // Convert from local coordinate system back to UTM33 with 2 decimal rounding
                val x = UTM33_X_MIN_INCLUSIVE + (coord.x * UTM33_RESOLUTION)
                val y = UTM33_Y_MIN_EXCLUSIVE + (EXTENT - coord.y) * UTM33_RESOLUTION
                listOf(
                    BigDecimal(x).setScale(2, RoundingMode.HALF_UP).toDouble(),
                    BigDecimal(y).setScale(2, RoundingMode.HALF_UP).toDouble()
                )
            } ?: emptyList()

            return MatrikkelGrenselinjeDto(
                geometry = GeometryDto(coordinates = coordinates),
                properties = PropertiesDto(
                    id = entity.id,
                    kommunenr1 = entity.kommunenr1,
                    kommunenr2 = entity.kommunenr2,
                    hjelpelinjetypeId = entity.hjelpelinjetypeId,
                    administrativgrensekodeId = entity.administrativgrensekodeId,
                    malemetodeId = entity.malemetodeId,
                    noyaktighet = entity.noyaktighet,
                    lagretNoyaktighetsklasse = entity.lagretNoyaktighetsklasse
                )
            )
        }
    }
}

/**
 * GeoJSON FeatureCollection wrapper for MatrikkelGrenselinjeDto
 */
data class MatrikkelGrenselinjeFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<MatrikkelGrenselinjeDto>
)
