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
    ): ModelDescriptor? {
        val candidates = registry.list().filter { d ->
            d.id !in excludeIds &&
                d.availability != ModelAvailability.UNAVAILABLE &&
                registry.provider(d.id) != null
        }
        if (candidates.isEmpty()) return null

        // A user-picked model wins only when the current mode allows it
        // (offline mode forbids remote; free-only forbids paid).
        preferredId?.let { id ->
            candidates.firstOrNull { it.id == id }?.let { d ->
                val allowed = when (mode) {
                    RoutingMode.OFFLINE_ONLY -> d.kind == ModelKind.LOCAL
                    RoutingMode.FREE_ONLY ->
                        d.kind == ModelKind.LOCAL ||
                            (d.kind == ModelKind.REMOTE && d.costTier == ModelCostTier.FREE)
                    else -> true
                }
                if (allowed && healthMonitor.isHealthy(d.id)) return d
            }
        }

        val local = candidates.filter { it.kind == ModelKind.LOCAL }
        val freeRemote = candidates.filter { it.kind == ModelKind.REMOTE && it.costTier == ModelCostTier.FREE }
        val paidRemote = candidates.filter { it.kind == ModelKind.REMOTE && it.costTier == ModelCostTier.PAID }

        fun pick(descs: List<ModelDescriptor>): ModelDescriptor? =
            descs.filter { healthMonitor.isHealthy(it.id) }
                .sortedWith(compareByDescending<ModelDescriptor> { it.reliabilityScore ?: 0 }
                    .thenByDescending { it.speedScore ?: 0 })
                .firstOrNull()

        when (mode) {
            RoutingMode.OFFLINE_ONLY ->
                return pick(local)

            RoutingMode.FREE_ONLY -> {
                // Free remote models first, then local. Paid models are
                // never selected automatically in this mode.
                if (task.networkAvailable) {
                    pick(freeRemote)?.let { return it }
                }
                pick(local)?.let { return it }
                return null
            }

            RoutingMode.LOCAL_FIRST -> {
                pick(local)?.let { return it }
                pick(freeRemote)?.let { return it }
                if (task.allowPaid) pick(paidRemote)?.let { return it }
                return null
            }

            RoutingMode.FREE_FIRST -> {
                if (task.networkAvailable) {
                    pick(freeRemote)?.let { return it }
                }
                pick(local)?.let { return it }
                if (task.allowPaid) pick(paidRemote)?.let { return it }
                return null
            }

            RoutingMode.HYBRID -> {
                val localMaxCtx = local.maxOfOrNull { it.contextLength ?: 0 } ?: 0
                val localCapable = localMaxCtx >= task.contextLength &&
                    task.requiredCapabilities.none { it == ModelCapability.VISION }
                val simpleEnough = task.taskType in simpleTasks ||
                    task.taskType == TaskType.CHAT ||
                    !task.networkAvailable
                if (task.preferLocal || simpleEnough || localCapable) {
                    pick(local)?.let { return it }
                }
                if (task.networkAvailable) {
                    pick(freeRemote)?.let { return it }
                }
                pick(local)?.let { return it }
                if (task.allowPaid) pick(paidRemote)?.let { return it }
                return null
            }
        }
    }

    fun reportSuccess(providerId: String) = healthMonitor.reportSuccess(providerId)

    fun reportFailure(providerId: String, error: String = "") =
        healthMonitor.reportFailure(providerId, error)
}
