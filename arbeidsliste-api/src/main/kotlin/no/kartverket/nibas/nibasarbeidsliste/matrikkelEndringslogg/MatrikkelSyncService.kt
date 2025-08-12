package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import no.kartverket.nibas.matrikkel.api.domain.MatrikkelContext
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.MatrikkelEndringId
import no.kartverket.nibas.matrikkel.api.domain.kommune.Kommune
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggService
import no.kartverket.nibas.matrikkel.api.service.kommune.KommuneService
import no.kartverket.nibas.matrikkel.api.service.store.StoreService
import no.kartverket.nibas.nibasarbeidsliste.service.GrenseSammenlignerService
import no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog.ChangeSet
import no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog.MatrikkelChangeLog
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Synchronizes administrative boundary changes from Matrikkel changelog.
 */
@Service
class MatrikkelSyncService(
    @param:Value($$"${matrikkelen.sync-on-startup:false}") private val syncOnStartup: Boolean,
    private val endringsnummerService: EndringsnummerService,
    private val changeSetProcessor: ChangeSetProcessor,
    private val endringsloggService: EndringsloggService,
    private val storeService: StoreService,
    private val kommuneService: KommuneService,
    private val matrikkelContext: MatrikkelContext,
    private val rawDataConverterService: RawDataConverterService,
    private val polygonValidationTest: PolygonValidationTest,
    private val grenseSammenlignerService: GrenseSammenlignerService
) : ApplicationRunner {
    private val chunkSize = 4096
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var kommuneMap: Map<Int, Kommune>

    override fun run(args: ApplicationArguments?) {
        if (syncOnStartup) {
            log.info("=== Starting Matrikkel Sync on Startup ===")
            sync()
            log.info("=== Matrikkel Sync Completed ===")
        } else {
            log.info("Matrikkel sync on startup is DISABLED")
        }
    }

    @Scheduled(cron = "0 0 2 * * * ", zone = "Europe/Oslo")
    fun sync() {
        if (!endringsnummerService.tryAcquireSyncLock()) {
            return
        }

        log.info("Matrikkel sync - starting processing")
        val startTime = System.currentTimeMillis()

        try {

            val startEndringsnummer = endringsnummerService.getLastProcessedEndringsnummer()
            if (startEndringsnummer == null) {
                log.error("Cannot start Matrikkel sync: No starting 'endringsnummer' found in the database. " +
                    "Please perform the initial bulk import first.")
                return
            }

            kommuneMap = createKommuneCache()

            val latestRemoteEndringsnummer = endringsloggService.findSisteEndringId(matrikkelContext).value

            log.info("Syncing from local endringsnummer: {} up to remote endringsnummer: {}", startEndringsnummer, latestRemoteEndringsnummer)

            if (startEndringsnummer >= latestRemoteEndringsnummer) {
                log.info("Database is already up-to-date. No sync needed.")
                return
            }

            val changeLog = MatrikkelChangeLog(endringsloggService, storeService, matrikkelContext, chunkSize)

            var changeSetCount = 0
            var totalProcessed = 0

            changeLog.findAdminBorderChanges(MatrikkelEndringId(startEndringsnummer))
                .forEach { changeSet: ChangeSet ->
                    changeSetCount++
                    log.info("Processing ChangeSet {} with {} boundary lines and {} boundary points at instant {}",
                        changeSetCount, changeSet.changedBoundaryLines.size, changeSet.changedBoundaryPoints.size, changeSet.instant)

                    val processed = changeSetProcessor.processChangeSet(changeSet, matrikkelContext, kommuneMap)
                    totalProcessed += processed
                }

            log.info("Converting raw data to NIBAS format...")
            val convertedCount = rawDataConverterService.rebuildAfterSync()
            log.info("Converted {} grenselinjer to NIBAS format", convertedCount)

            log.info("Running polygon validation test after sync...")
            polygonValidationTest.testAllKommuner()
            log.info("Polygon validation test completed")

            log.info("All changes processed successfully. Updating bookmark to endringsnummer: {}", latestRemoteEndringsnummer)
            endringsnummerService.saveProcessedEndringsnummer(latestRemoteEndringsnummer)

            try {
                log.info("Running deviation detection after successful sync...")
                val avvikResultat = grenseSammenlignerService.finnAvvik(toleranseMeter = 0.1)
                log.info("Deviation detection completed: {} grenser sjekket, {} avvik funnet, {} avvik lagret",
                    avvikResultat.antallGrenserSjekket, avvikResultat.totaltAntallAvvik, avvikResultat.antallAvvikLagret)
            } catch (e: Exception) {
                log.error("Deviation detection failed after sync, but sync itself was successful. Error: {}", e.message, e)
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Matrikkel sync completed successfully! {} changeSets, {} boundaries processed, {} converted to NIBAS format in {}ms",
                changeSetCount, totalProcessed, convertedCount, duration)

        } catch (e: Exception) {
            log.error("Matrikkel sync FAILED. Raw data sync or NIBAS conversion failed. Bookmark not updated, will retry from last successful position. Error: {}", e.message, e)
            throw e
        } finally {
            endringsnummerService.releaseSyncLock()
        }
    }

    fun createKommuneCache(): Map<Int, Kommune> {
        log.info("Initializing Kommune cache...")
        val kommuneIdList = kommuneService.findAlleKommuner(matrikkelContext)

        kommuneMap = kommuneIdList.item?.mapNotNull { kommuneId ->
            try {
                val kommune = storeService.getObject(kommuneId, matrikkelContext) as Kommune
                kommuneId.value.toInt() to kommune
            } catch (e: Exception) {
                log.error("Failed to retrieve Kommune for ID ${kommuneId.value}", e)
                null
            }
        }?.toMap() ?: emptyMap()
        return kommuneMap
    }

}
