package no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog

import jakarta.xml.ws.BindingProvider
import no.kartverket.nibas.matrikkel.api.domain.*
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.*
import no.kartverket.nibas.matrikkel.api.domain.forretning.Forretning
import no.kartverket.nibas.matrikkel.api.domain.forretning.ForretningId
import no.kartverket.nibas.matrikkel.api.domain.forretning.MatrikkelenhetForretningId
import no.kartverket.nibas.matrikkel.api.domain.geometri.koder.KoordinatsystemKodeId
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrense
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrenseId
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrensepunkt
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrensepunktId
import no.kartverket.nibas.matrikkel.api.exception.ObjectsNotFoundFaultInfo
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggService
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggServiceWS
import no.kartverket.nibas.matrikkel.api.service.store.StoreService
import no.kartverket.nibas.matrikkel.api.service.store.StoreServiceWS
import java.net.URL
import java.time.Instant
import java.util.*
import kotlin.collections.iterator
import kotlin.system.exitProcess
import no.kartverket.nibas.matrikkel.api.service.store.ServiceException as StoreException


sealed class InterrimChangeSet<T> {
    data class ByInstant(
        val instant: Instant,
        override val changed: Set<MatrikkelBubbleId>,
        val deleted: Set<MatrikkelBubbleId>
    ) : InterrimChangeSet<MatrikkelBubbleId>()

    abstract val changed: Set<T>
}

data class ChangeSet(
    val instant: Instant,
    val changedBoundaryLines: Set<BubbleWrapper<Teiggrense>>,
    val changedBoundaryPoints: Map<TeiggrensepunktId, BubbleWrapper<Teiggrensepunkt>>,
    val affairs: Set<BubbleWrapper<Forretning>>
)

class BubbleWrapper<out T : MatrikkelBubbleObject>(val bubble: T) {
    inline val id: MatrikkelBubbleId get() = bubble.id
    override fun equals(other: Any?): Boolean = other is BubbleWrapper<*> && bubble.id == other.bubble.id
    override fun hashCode(): Int = bubble.id.hashCode()
    override fun toString(): String = bubble::class.simpleName + "(" + bubble.id.value + ")"
    inline fun <reified U : MatrikkelBubbleObject> asOrNull(): BubbleWrapper<U>? {
        val bubbleAsU = bubble as? U
        return if (bubbleAsU != null) {
            @Suppress("UNCHECKED_CAST")
            this as BubbleWrapper<U>
        } else {
            null
        }
    }
}

fun <T : MatrikkelBubbleObjectWithHistory> BubbleWrapper<T>.updateInstant(): Instant =
    bubble.oppdateringsdato.timestamp.toGregorianCalendar().toInstant()


class MatrikkelChangeLog(
    private val endringsloggService: EndringsloggService,
    private val storeService: StoreService,
    private val matrikkelContext: MatrikkelContext,
    private val chunkSize: Int = 4096
) {

    init {
        require(chunkSize > 0) { "chunkSize must be greater than 0" }
    }


    fun findAdminBorderChanges(matrikkelEndringId: MatrikkelEndringId): Iterator<ChangeSet> = iterator {
        val pendingDeleteIds = HashMap<MatrikkelBubbleId, MutableSet<Instant>>()
        val pendingDeleteInstants = HashMap<Instant, MutableSet<MatrikkelBubbleId>>()
        val pendingDeleteYields = HashMap<Instant, Pair<MutableSet<BubbleWrapper<Forretning>>, List<MatrikkelBubbleObject>>>()
        val pending =
            TreeMap<Instant, Pair<MutableSet<BubbleWrapper<Forretning>>, MutableSet<BubbleWrapper<MatrikkelBubbleObjectWithHistory>>>>()
        for (interimChangeSet in findBorderChangesGroupedByInstant(matrikkelEndringId)) {
            val (deletedIds, fetchedObjs) = fetchObjects(interimChangeSet.changed)
            deletedIds.removeIf { it is ForretningId }

            val (affairs, geomObjs) = fetchedObjs
                .partition { it is Forretning }
                .let { (affairList, geomList) ->
                    val validPoints = geomList.filterIsInstance<Teiggrense>().filter { it.administrativGrenseKodeId.value > 0L }.flatMapTo(HashSet()) { listOf(it.kurve.startpunktId, it.kurve.endpunktId) }
                    affairList.mapTo(HashSet()) { BubbleWrapper(it as Forretning) } to geomList.filter { it !is Teiggrense || it.administrativGrenseKodeId.value > 0L }.filter { it !is Teiggrensepunkt || validPoints.contains(it.id) }
                }

            if (geomObjs.isNotEmpty()) {
                if (deletedIds.isNotEmpty()) {
                    check(
                        pendingDeleteYields.putIfAbsent(
                            interimChangeSet.instant,
                            HashSet(affairs) to geomObjs
                        ) == null
                    )
                    for (deletedId in deletedIds) {
                        pendingDeleteIds.computeIfAbsent(deletedId) { HashSet(1) }.add(interimChangeSet.instant)
                    }
                    pendingDeleteInstants
                        .computeIfAbsent(interimChangeSet.instant) { HashSet(deletedIds.size) }
                            .addAll(deletedIds)
                } else {
                    addToPending(pending, affairs, geomObjs)
                }
            }

            for (deletedId in deletedIds) {
                pendingDeleteIds.remove(deletedId)?.let { instants ->
                    for (instant in instants) {
                        val compute = pendingDeleteInstants.compute(instant) { _, v ->
                            v?.apply { remove(deletedId) }?.takeUnless { it.isEmpty() }
                        }
                        if (compute == null) {
                            pendingDeleteYields.remove(instant)?.let { (affairs, geomObjs) ->
                                addToPending(pending, affairs, geomObjs)
                            }
                        }
                    }
                }
            }

            val instantLimit = minOf(pendingDeleteYields.keys.minOrNull() ?: Instant.MAX, interimChangeSet.instant)
            yieldChangeSets(pending.headMap(instantLimit))

        }
        yieldChangeSets(pending)
    }

    private suspend fun SequenceScope<ChangeSet>.yieldChangeSets(headMap: SortedMap<Instant, Pair<MutableSet<BubbleWrapper<Forretning>>, MutableSet<BubbleWrapper<MatrikkelBubbleObjectWithHistory>>>>) {
        while (headMap.isNotEmpty()) {
            val instant = headMap.firstKey()
            val yieldPair = headMap[instant]!!
            val (entryAffairs, geometry) = yieldPair
            val changeSetAffairs = HashSet<BubbleWrapper<Forretning>>()
            val affairIter = entryAffairs.iterator()
            while (affairIter.hasNext()) {
                val affair = affairIter.next()
                if (affair.updateInstant() <= instant) {
                    changeSetAffairs.add(affair)
                    affairIter.remove()
                }
            }
            val borderLines = HashSet<BubbleWrapper<Teiggrense>>()
            val borderPoints = HashMap<TeiggrensepunktId, BubbleWrapper<Teiggrensepunkt>>()
            for (geom in geometry) {
                when (geom.bubble) {
                    is Teiggrense -> borderLines.add(geom.asOrNull()!!)
                    is Teiggrensepunkt -> borderPoints[geom.id as TeiggrensepunktId] = geom.asOrNull()!!
                    else -> throw IllegalStateException()
                }
            }
            yield(ChangeSet(instant, borderLines, borderPoints, changeSetAffairs))
        }
    }

    private fun addToPending(
        pending: NavigableMap<Instant, Pair<MutableSet<BubbleWrapper<Forretning>>, MutableSet<BubbleWrapper<MatrikkelBubbleObjectWithHistory>>>>,
        affairs: MutableSet<BubbleWrapper<Forretning>>,
        geomObjs: List<MatrikkelBubbleObject>
    ) {
        geomObjs
            .map { it as MatrikkelBubbleObjectWithHistory }
            .groupingBy { it.oppdateringsdato.timestamp.toGregorianCalendar().toInstant() }
            .aggregateTo(HashMap<Instant, MutableSet<BubbleWrapper<MatrikkelBubbleObjectWithHistory>>>()) { _, acc, obj, _ ->
                (acc ?: HashSet()).apply {
                    add(BubbleWrapper(obj))
                }
            }
            .forEach { (instant, changedBubbles) ->
                pending.merge(instant, Pair(affairs, changedBubbles)) { a, b ->
                    a.apply {
                        first.addAll(b.first)
                        second.addAll(b.second)
                    }
                }
            }
    }

    private fun fetchObjects(matrikkelBubbleIds: Collection<MatrikkelBubbleId>): Pair<MutableSet<MatrikkelBubbleId>, List<MatrikkelBubbleObject>> {
        val allChangedIds = matrikkelBubbleIds.toCollection(HashSet())
        val deletedIds = HashSet<MatrikkelBubbleId>()
        var fetchedObjects: MatrikkelBubbleObjectList
        while (true) try {
            fetchedObjects =
                storeService.getObjects(MatrikkelBubbleIdList(allChangedIds.toList()), matrikkelContext)
            break
        } catch (e: StoreException) {
            when (val faultInfo = e.faultInfo) {
                is ObjectsNotFoundFaultInfo -> {
                    deletedIds.addAll(faultInfo.idsNotFound.item)
                    allChangedIds.removeAll(deletedIds)
                }

                else -> throw e
            }
        }
        return deletedIds to fetchedObjects.item
    }

    private fun findBorderChangesGroupedByInstant(matrikkelEndringId: MatrikkelEndringId): Iterator<InterrimChangeSet.ByInstant> =
        iterator {
            var currentInstant = Instant.MIN
            var currentChangedBoundaryPoints = mutableSetOf<MatrikkelBubbleId>()
            var currentDeletedBoundaryPoints = mutableSetOf<MatrikkelBubbleId>()

            @Suppress("NAME_SHADOWING")
            var matrikkelEndringId = matrikkelEndringId
            do {
                val changes = findEndringer(matrikkelEndringId)
                for (endring in changes.endringList.item) {
                    val endringInstant = endring.endringstidspunkt.timestamp.toGregorianCalendar().toInstant()
                    if (endringInstant != currentInstant) {
                        if (currentChangedBoundaryPoints.isNotEmpty()) {
                            yield(
                                InterrimChangeSet.ByInstant(
                                    currentInstant,
                                    currentChangedBoundaryPoints,
                                    currentDeletedBoundaryPoints
                                )
                            )
                        }
                        currentInstant = endringInstant
                        currentChangedBoundaryPoints = mutableSetOf()
                        currentDeletedBoundaryPoints = mutableSetOf()
                    }
                    when (val changedId = endring.endretBubbleId) {
                        is ForretningId -> if (endring.endringstype == Endringstype.NYOPPRETTING) {
                            currentChangedBoundaryPoints.add(MatrikkelenhetForretningId(changedId.value))
                        }

                        is TeiggrenseId, is TeiggrensepunktId -> if (endring.endringstype == Endringstype.SLETTING) {
                            currentDeletedBoundaryPoints.add(changedId)
                        } else {
                            currentChangedBoundaryPoints.add(changedId)
                        }
                    }
                }
                matrikkelEndringId = changes.sisteEndringIdProsessert
            } while (!changes.isAlleEndringerFunnet)

            if (currentChangedBoundaryPoints.isNotEmpty()) {
                yield(
                    InterrimChangeSet.ByInstant(
                        currentInstant,
                        currentChangedBoundaryPoints,
                        currentDeletedBoundaryPoints
                    )
                )
            }
        }


    private fun findEndringer(matrikkelEndringId: MatrikkelEndringId): Endringer = endringsloggService.findEndringer(
        matrikkelEndringId,
        Domainklasse.MATRIKKEL_BUBBLE_OBJECT,
        null,
        ReturnerBobler.ALDRI,
        chunkSize,
        matrikkelContext
    )


    fun findTeiggrenseEndringerOnly(firstEndringId: MatrikkelEndringId) {
        val insertUpdateIds = HashSet<TeiggrenseId>()
        val deleteIds = HashSet<TeiggrenseId>()
        val lastStatusUpdate = Instant.now()
        val lastEndringId = endringsloggService.findSisteEndringId(matrikkelContext);
        var matrikkelEndringId = firstEndringId
        var count = 0
        val startedAt = Instant.now()
        do {
            val endringer = endringsloggService.findEndringer(
                matrikkelEndringId,
                Domainklasse.TEIGGRENSE,
                null,
                ReturnerBobler.ALDRI,
                10_000,
                matrikkelContext
            )
            for (endring in endringer.endringList.item) {
                when (endring.endringstype) {
                    Endringstype.SLETTING -> deleteIds.add(endring.endretBubbleId as TeiggrenseId)
                    else -> insertUpdateIds.add(endring.endretBubbleId as TeiggrenseId)
                }
            }
            count += endringer.endringList.item.size
            if (lastStatusUpdate.plusSeconds(1) < Instant.now()) {
                val perCent = (count / 8408264.0) * 100.0
                val toEpochMilli = Instant.now().toEpochMilli()
                val remainingDuration = (toEpochMilli -  startedAt.toEpochMilli()) / (perCent / 100.0) - (toEpochMilli -  startedAt.toEpochMilli())
                println("Found ${insertUpdateIds.size} insert/update and ${deleteIds.size} delete changes ${perCent.toLong()}% done. Estimated remaining time: ${remainingDuration / 1000.0}s")
            }
            matrikkelEndringId = endringer.sisteEndringIdProsessert
        } while (!endringer.isAlleEndringerFunnet)
        insertUpdateIds.removeAll(deleteIds)
    }

}

// TODO: Fjern denne testmetoden
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("Usage: <serverurl> <username> <password>")
        System.err.println("eks: https://prodtest.matrikkel.no/matrikkelapi/wsapi/v1 bruker hemmelig")
        exitProcess(-1)
    }
    val addr = URL(args[0])
     val username = args[1]
    val password = args[2]
    val matrikkelApiPropertiesURL =
        MatrikkelContext::class.java.getResource("/META-INF/matrikkel-api.properties")
            ?: (throw IllegalStateException())
    val matrikkelApiVersion = matrikkelApiPropertiesURL.openStream().use { stream ->
        Properties().apply { load(stream) }
    }

    val matrikkelContext = MatrikkelContext(
        "nb_NO",
        true,
        KoordinatsystemKodeId(11), // EUREF89 UTM sone 33
        matrikkelApiVersion.getProperty("matrikkel.api.version") ?: (throw IllegalStateException()),
        "nibas_arbeidsliste",
        null
    )

    val endringsloggService = EndringsloggServiceWS().endringsloggServicePort.apply {
        this as BindingProvider
        requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] =
            "$addr/EndringsloggServiceWS"
        requestContext[BindingProvider.USERNAME_PROPERTY] = username
        requestContext[BindingProvider.PASSWORD_PROPERTY] = password
    }

    val storeService = StoreServiceWS().storeServicePort.apply {
        this as BindingProvider
        requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] =
            "$addr/StoreServiceWS"
        requestContext[BindingProvider.USERNAME_PROPERTY] = username
        requestContext[BindingProvider.PASSWORD_PROPERTY] = password
    }

    val matrikkelChangeLog = MatrikkelChangeLog(endringsloggService, storeService, matrikkelContext)

    val firstEndringId = MatrikkelEndringId(331421883L)
    matrikkelChangeLog.findTeiggrenseEndringerOnly(firstEndringId)
//    matrikkelChangeLog.findAdminBorderChanges(firstEndringId).forEach {
//        println(it)
//    }
}

