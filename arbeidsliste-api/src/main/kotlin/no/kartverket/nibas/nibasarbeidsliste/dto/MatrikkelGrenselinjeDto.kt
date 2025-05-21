package no.kartverket.nibas.nibasarbeidsliste.dto

import no.kartverket.nibas.nibasarbeidsliste.model.MatrikkelGrenselinje
import org.locationtech.jts.geom.Coordinate

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
         * Add two decimals since coordinates are stored as integer with two "missings" decimals in the database.
         */
        fun fromEntity(entity: MatrikkelGrenselinje): MatrikkelGrenselinjeDto {
            val coordinates = entity.geom?.coordinates?.map { coord ->
                listOf(coord.x / 100.0, coord.y / 100.0)
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
