import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/arbeidsliste")
@Tag(
    name = "Arbeidsliste",
    description = "Endepunkter for å hente endringer fra matrikkelen sin arbeidsliste som er relevante for Nasjonal Inndelingsbase"
)

interface ArbeidslisteApi {

    @Operation(summary = "Hent arbeidsliste", description = "Henter alle endringer fra matrikkelen sin arbeidsliste som er relevante for Nasjonal Inndelingsbase")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful operation",
            content = [Content(
                mediaType = "text/plain",
            )]
        )
    )
    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getArbeidsliste(): String
}