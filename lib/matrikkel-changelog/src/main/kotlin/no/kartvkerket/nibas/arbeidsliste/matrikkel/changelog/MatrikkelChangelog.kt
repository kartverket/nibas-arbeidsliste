package no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog

import no.kartverket.nibas.matrikkel.api.domain.MatrikkelBubbleId
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelBubbleIdList
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelBubbleObject
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelBubbleObjectList
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelBubbleObjectWithHistory
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelContext
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.Domainklasse
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.Endringer
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.Endringstype
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.MatrikkelEndringId
import no.kartverket.nibas.matrikkel.api.domain.endringslogg.ReturnerBobler
import no.kartverket.nibas.matrikkel.api.domain.forretning.Forretning
import no.kartverket.nibas.matrikkel.api.domain.forretning.ForretningId
import no.kartverket.nibas.matrikkel.api.domain.forretning.MatrikkelenhetForretningId
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrense
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrenseId
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrensepunkt
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrensepunktId
import no.kartverket.nibas.matrikkel.api.exception.ObjectsNotFoundFaultInfo
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggService
import no.kartverket.nibas.matrikkel.api.service.store.StoreService
import java.time.Instant
import java.util.*
import no.kartverket.nibas.matrikkel.api.service.store.ServiceException as StoreException


sealed class InterimChangeSet<T> {
    data class ByInstant(
        val instant: Instant,
        override val changed: Set<MatrikkelBubbleId>,
        val deleted: Set<MatrikkelBubbleId>
    ) : InterimChangeSet<MatrikkelBubbleId>()

    abstract val changed: Set<T>
}

/**
 * Represents a fully resolved and chronologically ordered set of changes for a single instant in time.
 *
 * This is the final output object yielded by the [MatrikkelChangeLog]. It contains all the relevant
 * administrative boundary changes (`Teiggrense`, `Teiggrensepunkt`) and the associated business
 * transactions (`Forretning`) that occurred at a specific moment.
 *
 * @param instant The timestamp from the changelog at which these changes occurred.
 * @param changedBoundaryLines A set of administrative boundary lines that were created or updated.
 * @param changedBoundaryPoints A map of administrative boundary points that were created or updated keyed by their ID for efficient access.
 * @param affairs The set of business transactions (affairs) that provide context for the geometric changes.
 */
data class ChangeSet(
    val instant: Instant,
    val changedBoundaryLines: Set<BubbleWrapper<Teiggrense>>,
    val changedBoundaryPoints: Map<TeiggrensepunktId, BubbleWrapper<Teiggrensepunkt>>,
    val affairs: Set<BubbleWrapper<Forretning>>
)

/**
 * A wrapper class for [MatrikkelBubbleObject] instances that redefines equality based on the object's ID.
 *
 * This is a crucial utility for use in collections like [Set] and [Map]. By implementing [equals] and [hashCode]
 * based solely on the [MatrikkelBubbleId], it ensures that collections treat two wrappers as identical if they
 * refer to the same Matrikkel object, regardless of the object's version or other properties.
 *
 * @param T The type of the [MatrikkelBubbleObject] being wrapped.
 * @property bubble The underlying [MatrikkelBubbleObject].
 */
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

/**
 * Gets the update timestamp of the wrapped bubble as a [java.time.Instant].
 *
 * This is an extension property on wrappers containing a [MatrikkelBubbleObjectWithHistory].
 */
fun <T : MatrikkelBubbleObjectWithHistory> BubbleWrapper<T>.updateInstant(): Instant =
    bubble.oppdateringsdato.timestamp.toGregorianCalendar().toInstant()


/**
 * Processes the Matrikkel changelog to produce a consistent and chronological ordered stream of changes.
 *
 * This class is specifically designed to find changes related to administrative boundaries, focusing on
 * `Teiggrense` and `Teiggrensepunkt` objects. It consumes events from the `EndringsloggService` to discover
 * *what* has changed, and then uses the `StoreService` to fetch the full state of those objects.
 *
 * A core feature is its robust state machine that correctly handles a critical edge case in the Matrikkel API:
 * an object fetch can fail (because the object was deleted) before the corresponding deletion event has been
 * read from the changelog. This implementation "pauses" processing of such change sets until the deletion is
 * confirmed, ensuring data integrity and correct chronological ordering of the final output.
 *
 * The main entry point is the [findAdminBorderChanges] function, which returns an iterator of [ChangeSet] objects.
 *
 * @param endringsloggService The service client for the Matrikkel `EndringsloggService`. It provides a stream of raw change events.
 * @param storeService The service client for the Matrikkel `StoreService`. It is used to fetch the full data for objects identified by the changelog.
 * @param matrikkelContext The required context object containing metadata (like client identification and coordinate system) for all API calls.
 * @param chunkSize The maximum number of change events to fetch from the `endringsloggService` in a single network request.
 * @see findAdminBorderChanges
 */
class MatrikkelChangeLog(
    // Endringslogg return "what's happened" since last time.
    // Gives Endring objects with ID, which are created, update or delete.
    private val endringsloggService: EndringsloggService,
    // Gets the data for the IDS with endring.
    // Matrikkelen only stores the current data.
    private val storeService: StoreService,
    // Metadata that needs to be sent with every call to the Matrikkel API.
    private val matrikkelContext: MatrikkelContext,
    // Max number of changes in a single Endringslogg request.
    private val chunkSize: Int = 4096
) {

    init {
        require(chunkSize > 0) { "chunkSize must be greater than 0" }
    }

    /**
     * Finds all administrative boundary changes from the Matrikkel changelog, starting from a given point.
     *
     * This is the main entry point for the class. It returns a lazy [Iterator] that yields [ChangeSet]
     * objects in strict chronological order. The function orchestrates the entire process of:
     * 1. Paging through the raw changelog from the `EndringsloggService`.
     * 2. Fetching the full object data from the `StoreService`.
     * 3. Handling out-of-order deletion events by temporarily staging change sets until all their
     *    dependencies are resolved. This is the core of the class's state machine.
     * 4. Filtering for relevant administrative boundaries ([Teiggrense], [Teiggrensepunkt]) and their
     *    associated [Forretning] objects.
     * 5. Grouping the final, resolved changes into [ChangeSet]s, one for each unique timestamp.
     *
     * Because it returns an `Iterator`, network calls and processing happen on-demand as the consumer
     * iterates through the results.
     *
     * @param matrikkelEndringId The ID in the changelog from which to start processing.
     * @return A lazy [Iterator] of [ChangeSet] objects, each representing a consistent snapshot of changes.
     */
    fun findAdminBorderChanges(matrikkelEndringId: MatrikkelEndringId): Iterator<ChangeSet> = iterator {
        // Finds missing IDs aka deleted IDs
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

    /**
     * Yields [ChangeSet] objects from pending changes in chronological order.
     *
     * This function makes sure changes come out in the right time order.
     * Why important: changes must be processed in a correct sequence or data get messed up.
     * Removes processed entries from headMap to prevent memory leak.
     */
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
            headMap.remove(instant)
        }
    }


    /**
     * Processes and adds successfully fetched geometric objects to the final, chronologically sorted processing queue.
     *
     * A key behavior of this function is that it groups the incoming geometric objects by their actual
     * update timestamp (`oppdateringsdato`), not by the timestamp of the changelog event that triggered their fetch.
     * This ensures that the final [ChangeSet]s are yielded in an order that reflects the true version history
     * of the data objects themselves.
     *
     * If the `pending` map already contains an entry for a given timestamp, the new affairs and geometric
     * objects are merged into the existing sets.
     *
     * @param pending The main, navigable map that holds fully resolved changes, sorted by their update `Instant`.
     * @param affairs The set of contextual [Forretning] objects associated with this batch of `geomObjs`.
     * @param geomObjs The list of fetched geometric objects (like [Teiggrense] or [Teiggrensepunkt]) to be added to the queue.
     */
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

    /**
     * Fetches a collection of Matrikkel objects from the `StoreService` given their IDs.
     *
     * This function is designed to be resilient to `ObjectsNotFoundFaultInfo` exceptions, which occur when
     * some of the requested objects have been deleted from the Matrikkel. It handles this by iteratively
     * calling the `StoreService`:
     * 1. It attempts to fetch all given IDs.
     * 2. If a "not found" exception occurs, it identifies the missing (deleted) IDs.
     * 3. It removes the missing IDs from the request list and retries the fetch for the remaining objects.
     * 4. This process repeats until the `StoreService` call succeeds for the subset of existing objects.
     *
     * The function ultimately separates the successfully fetched objects from the IDs of those that were deleted.
     *
     * @param matrikkelBubbleIds The collection of [MatrikkelBubbleId]s to fetch.
     * @return A [Pair] containing:
     *         - `first`: A mutable set of [MatrikkelBubbleId]s that were not found (and are thus considered deleted).
     *         - `second`: A list of the [MatrikkelBubbleObject]s that were successfully fetched.
     */
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

    /**
     * Fetches the raw changelog from the `EndringsloggService` and groups change events by their exact timestamp.
     *
     * This function acts as the primary data source for the main processing loop. It pages through all changes
     * starting from the given [matrikkelEndringId] and yields intermediate change sets. Each yielded
     * [InterimChangeSet.ByInstant] represents a batch of all object IDs that were created, updated, or deleted
     * at a single, unique `Instant`.
     *
     * It specifically filters for changes related to administrative boundaries ([TeiggrenseId], [TeiggrensepunktId])
     * and their associated business transactions ([ForretningId]).
     *
     * @param matrikkelEndringId The ID in the changelog from which to start fetching events.
     * @return An [Iterator] that lazily yields [InterimChangeSet.ByInstant] objects, each corresponding to a
     *         unique timestamp in the changelog.
     */
    private fun findBorderChangesGroupedByInstant(matrikkelEndringId: MatrikkelEndringId): Iterator<InterimChangeSet.ByInstant> =
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
                                InterimChangeSet.ByInstant(
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
                    InterimChangeSet.ByInstant(
                        currentInstant,
                        currentChangedBoundaryPoints,
                        currentDeletedBoundaryPoints
                    )
                )
            }
        }


    /**
     * Fetches a batch of changes from the endringsloggService, starting from the given MatrikkelEndringId.
     *
     * @param matrikkelEndringId The ID from which to start fetching changes.
     * @return An [Endringer] object containing the list of changes and metadata.
     *
     * Why important: need to get raw change events before can fetch actual data.
     * Parameters used in service call:
     * - Domainklasse.MATRIKKEL_BUBBLE_OBJECT: Fetch changes for all types in the matrikkel.
     * - null: No restriction on municipality; fetch for all.
     * - ReturnerBobler.ALDRI: Do not return the bubble objects themselves, only the change objects.
     * - chunkSize: Maximum number of changes to return in one call.
     * - matrikkelContext: Metadata required for the API call.
     */
    private fun findEndringer(matrikkelEndringId: MatrikkelEndringId): Endringer = endringsloggService.findEndringer(
        matrikkelEndringId,
        Domainklasse.MATRIKKEL_BUBBLE_OBJECT,
        null,
        ReturnerBobler.ALDRI,
        chunkSize,
        matrikkelContext
    )

}
