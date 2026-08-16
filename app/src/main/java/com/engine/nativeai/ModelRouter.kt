package com.engine.nativeai

/**
 * Task-aware router (spec §6, §8-10, §14). Never selects a provider that is
 * unavailable, unhealthy, or unauthorized (paid without permission). Fallback
 * order is configurable via RoutingMode; offline mode forbids the network.
 */
class ModelRouter(
    val mode: RoutingMode = RoutingMode.HYBRID,
    private val healthMonitor: ProviderHealthMonitor = ProviderHealthMonitor(),
) {
    private val simpleTasks = setOf(
        TaskType.CHAT, TaskType.SUMMARIZATION, TaskType.OFFLINE_TASK, TaskType.TOOL_EXECUTION,
    )

    fun route(
        task: AgentTask,
        registry: ModelRegistry,
        excludeIds: Set<String> = emptySet(),
        preferredId: String? = null,
        preferKind: ModelKind? = null,
    ): ModelDescriptor? {
        val candidates = registry.list().filter { d ->
            d.id !in excludeIds &&
                d.availability != ModelAvailability.UNAVAILABLE &&
                registry.provider(d.id) != null
        }
        if (candidates.isEmpty()) return null

        // A user-picked model wins only when the current mode allows it
        // (offline mode forbids remote; free-only forbids paid). Explicit
        // selections are attempted even under transient failure marks so the
        // agent never silently substitutes the local model; real failures
        // surface in the trace and the agent's fallback loop handles them.
        preferredId?.let { id ->
            candidates.firstOrNull { it.id == id }?.let { d ->
                val allowed = when (mode) {
                    RoutingMode.OFFLINE_ONLY -> d.kind == ModelKind.LOCAL
                    RoutingMode.FREE_ONLY ->
                        d.kind == ModelKind.LOCAL ||
                            (d.kind == ModelKind.REMOTE && d.costTier == ModelCostTier.FREE)
                    else -> true
                }
                if (allowed) return d
            }
        }

        val local = candidates.filter { it.kind == ModelKind.LOCAL }
        val freeRemote = candidates.filter { it.kind == ModelKind.REMOTE && it.costTier == ModelCostTier.FREE }
        val paidRemote = candidates.filter { it.kind == ModelKind.REMOTE && it.costTier == ModelCostTier.PAID }

        fun pick(descs: List<ModelDescriptor>): ModelDescriptor? =
            descs.filter { healthMonitor.isHealthy(it.id) }
                .sortedWith(compareByDescending<ModelDescriptor> { it.reliabilityScore ?: 0 }
                    .thenByDescending { it.speedScore ?: 0 }
                    // Measured latency breaks ties: prefer the fastest healthy
                    // candidate (never fabricated — nulls sort last).
                    .thenBy { healthMonitor.latencyMs(it.id) ?: Long.MAX_VALUE })
                .firstOrNull()

        fun pickFrom(
            l: List<ModelDescriptor>,
            fr: List<ModelDescriptor>,
            pr: List<ModelDescriptor>,
        ): ModelDescriptor? = when (mode) {
            RoutingMode.OFFLINE_ONLY ->
                pick(l)

            RoutingMode.FREE_ONLY -> {
                // Free remote models first, then local. Paid models are
                // never selected automatically in this mode.
                if (task.networkAvailable) {
                    pick(fr)?.let { return it }
                }
                pick(l)?.let { return it }
                null
            }

            RoutingMode.LOCAL_FIRST -> {
                pick(l)?.let { return it }
                pick(fr)?.let { return it }
                if (task.allowPaid) pick(pr)?.let { return it }
                null
            }

            RoutingMode.FREE_FIRST -> {
                if (task.networkAvailable) {
                    pick(fr)?.let { return it }
                }
                pick(l)?.let { return it }
                if (task.allowPaid) pick(pr)?.let { return it }
                null
            }

            RoutingMode.HYBRID -> {
                val localMaxCtx = l.maxOfOrNull { it.contextLength ?: 0 } ?: 0
                val localCapable = localMaxCtx >= task.contextLength &&
                    task.requiredCapabilities.none { it == ModelCapability.VISION }
                val simpleEnough = task.taskType in simpleTasks ||
                    task.taskType == TaskType.CHAT ||
                    !task.networkAvailable
                if (task.preferLocal || simpleEnough || localCapable) {
                    pick(l)?.let { return it }
                }
                if (task.networkAvailable) {
                    pick(fr)?.let { return it }
                }
                pick(l)?.let { return it }
                if (task.allowPaid) pick(pr)?.let { return it }
                null
            }
        }

        // Fallback from a failed model must preserve the user's intent: the
        // agent passes the failed model's kind so a local pick stays local
        // and a remote pick stays remote. Only when no same-kind candidate
        // survives does the router open the full pool — and the agent always
        // emits an honest Routed(reason) when a substitution happens.
        if (preferKind != null) {
            pickFrom(
                l = if (preferKind == ModelKind.LOCAL) local else emptyList(),
                fr = if (preferKind == ModelKind.REMOTE) freeRemote else emptyList(),
                pr = if (preferKind == ModelKind.REMOTE) paidRemote else emptyList(),
            )?.let { return it }
        }
        return pickFrom(local, freeRemote, paidRemote)
    }

    fun reportSuccess(providerId: String) = healthMonitor.reportSuccess(providerId)

    fun reportFailure(providerId: String, error: String = "") =
        healthMonitor.reportFailure(providerId, error)

    /** Last recorded failure reason for a provider ("" when none/healthy). */
    fun lastError(providerId: String): String = healthMonitor.lastError(providerId)
}
