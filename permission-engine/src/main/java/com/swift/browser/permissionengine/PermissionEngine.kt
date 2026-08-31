package com.swift.browser.permissionengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

interface PermissionEngine {
    fun getDomain(urlStr: String): String
    fun getCanonicalHost(host: String): String
    fun getPermissionStatus(domain: String, permission: String): String
    fun setPermissionStatus(domain: String, permission: String, status: String)
    fun resetAllPermissionsForDomain(domain: String)
    fun addSessionPermission(domain: String, permission: String)
    fun logPermissionAction(domain: String, permission: String, action: String)
    fun getPermissionState(origin: String, permission: String): String // "Allow", "Block", "Ask"
    fun setPermissionState(origin: String, permission: String, state: String)
    fun clearPermissionState(origin: String, permission: String)
    fun getAllPermissions(): Map<String, Map<String, String>>
    fun observeAllPermissions(): Flow<List<PermissionEntity>>
    fun resetAllPermissions()
    fun handleRequest(
        requestModel: PermissionRequestModel,
        androidContext: Context,
        onComplete: ((String) -> Unit)? = null
    )
    fun handleUniversalRequest(
        universalRequest: UniversalCapabilityRequest,
        androidContext: Context,
        onComplete: ((String) -> Unit)? = null
    )
    fun requestAndroidPermissions(requestId: String, permissions: List<String>)
    fun cancelWebViewRequest(requestId: String)
    fun resumeTransaction(requestId: String, androidResult: AndroidPermissionResult)
    fun getExtensionPermissionRepository(): ExtensionPermissionRepository
}

class PermissionEngineImpl(private val context: Context) : PermissionEngine {
    
    private val memoryStore = OriginPermissionStore()
    private val db = PermissionDatabase.getDatabase(context)
    private val dao = db.permissionDao()
    internal val repository = SitePermissionRepository(dao)
    private val extensionDao = db.extensionPermissionDao()
    private val extPermissionRepository = ExtensionPermissionRepository(extensionDao)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sessionAllowedPermissions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    override fun getExtensionPermissionRepository(): ExtensionPermissionRepository {
        return extPermissionRepository
    }

    init {
        // Hydrate fast lookup cache from DB on startup
        scope.launch {
            try {
                val dbPermissions = repository.getAllPermissions()
                val now = System.currentTimeMillis()
                for (entity in dbPermissions) {
                    val isExpired = entity.expiresAt > 0 && entity.expiresAt < now
                    if (isExpired) {
                        repository.deletePermission(entity.origin, entity.permissionType)
                    } else {
                        memoryStore.setMemoryState(entity.origin, entity.permissionType, entity.decision)
                        PermissionCache.cachePersistentDecision(entity.origin, entity.permissionType, entity.decision)
                    }
                }
            } catch (e: Exception) {
                PermissionLogger.logFailure("init", "DB_LOAD", "Failed database cache initialization", e.toString())
            }
        }
    }

    override fun observeAllPermissions(): Flow<List<PermissionEntity>> {
        return repository.allPermissionsFlow
    }

    override fun resetAllPermissions() {
        PermissionRevocationEngine.revokeAllPermissions(repository)
    }

    override fun getDomain(urlStr: String): String {
        return DomainUtils.getDomain(urlStr)
    }

    override fun getCanonicalHost(host: String): String {
        return DomainUtils.getCanonicalHost(host)
    }

    override fun addSessionPermission(domain: String, permission: String) {
        val cleanDomain = getDomain(domain)
        sessionAllowedPermissions.add("$cleanDomain:$permission")
        logPermissionAction(cleanDomain, permission, "Permission Granted (Once)")
    }

    override fun getPermissionStatus(domain: String, permission: String): String {
        val cleanDomain = getDomain(domain)
        val state = getPermissionState(cleanDomain, permission)
        if (state != "Ask") {
            return state
        }
        if (sessionAllowedPermissions.contains("$cleanDomain:$permission")) {
            return "Allow"
        }
        val defaultForPerm = when (permission) {
            "javascript", "cookies", "sync" -> "Allow"
            "popups", "ads" -> "Block"
            else -> "Ask"
        }
        return defaultForPerm
    }

    override fun setPermissionStatus(domain: String, permission: String, status: String) {
        val cleanDomain = getDomain(domain)
        try {
            setPermissionState(cleanDomain, permission, status)
        } catch (e: Exception) {
            android.util.Log.e("Permissions", "Failed to sync to permissionEngine", e)
        }
        logPermissionAction(cleanDomain, permission, "Permission Persisted to $status")
    }

    override fun resetAllPermissionsForDomain(domain: String) {
        val cleanDomain = getDomain(domain)
        val permissions = PermissionDescriptorRegistry.getAllCapabilityIds()
        for (perm in permissions) {
            try {
                clearPermissionState(cleanDomain, perm)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sessionAllowedPermissions.removeAll { it.startsWith("$cleanDomain:") }
        logPermissionAction(cleanDomain, "all", "Reset all permissions")
    }

    override fun logPermissionAction(domain: String, permission: String, action: String) {
        PermissionLogger.logEvent(domain, permission, action, "")
    }

    override fun getPermissionState(origin: String, permission: String): String {
        val mappedPerm = mapLegacyPermission(permission)
        val cached = memoryStore.getMemoryState(origin, mappedPerm) ?: "ASK"
        return when (cached) {
            "ALLOW_ALWAYS" -> "Allow"
            "ALLOW_ONCE" -> "Allow"
            "BLOCK" -> "Block"
            else -> "Ask"
        }
    }

    override fun setPermissionState(origin: String, permission: String, state: String) {
        setPermissionState(origin, permission, state, isIncognito = false)
    }

    fun setPermissionState(origin: String, permission: String, state: String, isIncognito: Boolean) {
        val normOrigin = OriginNormalizer.normalize(origin)
        val mappedPerm = mapLegacyPermission(permission)
        val mappedState = when (state.lowercase()) {
            "allow" -> "ALLOW_ALWAYS"
            "allow_always" -> "ALLOW_ALWAYS"
            "allow_once" -> "ALLOW_ONCE"
            "block" -> "BLOCK"
            else -> "ASK"
        }
        
        PermissionCache.evictFromCache(normOrigin, mappedPerm)
        if (isIncognito || mappedState == "ALLOW_ONCE") {
            val sessionDecision = if (mappedState == "BLOCK") "BLOCK" else "ALLOW_ONCE"
            PermissionCache.cacheSessionDecision(normOrigin, mappedPerm, sessionDecision)
            memoryStore.setMemoryState(normOrigin, mappedPerm, sessionDecision)
        } else {
            if (mappedState == "ALLOW_ALWAYS" || mappedState == "BLOCK") {
                PermissionCache.cachePersistentDecision(normOrigin, mappedPerm, mappedState)
                memoryStore.setMemoryState(normOrigin, mappedPerm, mappedState)
                scope.launch {
                    repository.savePermission(normOrigin, mappedPerm, mappedState)
                }
            } else {
                scope.launch {
                    repository.deletePermission(normOrigin, mappedPerm)
                    memoryStore.clearMemoryState(normOrigin, mappedPerm)
                }
            }
        }
    }

    private suspend fun savePermissionStateDirectly(origin: String, permissionType: String, state: String) {
        val normOrigin = OriginNormalizer.normalize(origin)
        memoryStore.setMemoryState(normOrigin, permissionType, state)
        if (state == "ALLOW_ALWAYS" || state == "BLOCK") {
            repository.savePermission(normOrigin, permissionType, state)
            PermissionCache.cachePersistentDecision(normOrigin, permissionType, state)
        } else {
            PermissionCache.cacheSessionDecision(normOrigin, permissionType, state)
        }
    }

    override fun clearPermissionState(origin: String, permission: String) {
        val mappedPerm = mapLegacyPermission(permission)
        PermissionCache.evictFromCache(origin, mappedPerm)
        scope.launch {
            repository.deletePermission(origin, mappedPerm)
            memoryStore.clearMemoryState(origin, mappedPerm)
        }
    }

    override fun getAllPermissions(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        val cached = memoryStore.getAllCached()
        for ((origin, permMap) in cached) {
            val innerMap = mutableMapOf<String, String>()
            for ((perm, state) in permMap) {
                val legacyState = when (state) {
                    "ALLOW_ALWAYS" -> "Allow"
                    "ALLOW_ONCE" -> "Allow"
                    "BLOCK" -> "Block"
                    else -> "Ask"
                }
                innerMap[perm.lowercase()] = legacyState
            }
            result[origin] = innerMap
        }
        return result
    }

    override fun handleRequest(
        requestModel: PermissionRequestModel,
        androidContext: Context,
        onComplete: ((String) -> Unit)?
    ) {
        val universal = PermissionRequestNormalizer.normalize(requestModel)
        handleUniversalRequest(universal, androidContext, onComplete)
    }

    override fun handleUniversalRequest(
        universalRequest: UniversalCapabilityRequest,
        androidContext: Context,
        onComplete: ((String) -> Unit)?
    ) {
        val request = PermissionRequestNormalizer.normalize(universalRequest)
        val dynamicOrigin = request.toDynamicOrigin()
        val normOrigin = dynamicOrigin.canonicalOrigin
        val primaryType = request.capabilityId.uppercase()
        val requestId = request.requestId
        val requestedResources = request.requestedResources.ifEmpty { listOf(primaryType) }

        // Register pending transaction if not registered
        val existingTx = PermissionGrantEngine.getPendingTransaction(requestId)
        if (existingTx == null) {
            val tx = PendingPermissionTransaction(
                requestId = requestId,
                tabId = request.tabId,
                origin = normOrigin,
                resources = requestedResources,
                request = null,
                universalRequest = request,
                createdAt = System.currentTimeMillis(),
                expiration = System.currentTimeMillis() + 60000L,
                isIncognito = request.incognito
            )
            PermissionGrantEngine.registerPendingTransaction(tx)
        }

        // Evaluate website & policy state for EACH requested resource independently via CapabilityBroker
        val evaluations = requestedResources.map { res ->
            res to CapabilityBroker.evaluateCapability(res, dynamicOrigin, androidContext)
        }.toMap()

        val blockedResources = requestedResources.filter { res ->
            val ev = evaluations[res]
            ev?.capabilityState == CapabilityState.BLOCKED_BY_SECURITY ||
            ev?.capabilityState == CapabilityState.BLOCKED_BY_USER_POLICY ||
            ev?.capabilityState == CapabilityState.DENIED_BY_HARDWARE ||
            ev?.capabilityState == CapabilityState.UNSUPPORTED_BY_WEBVIEW ||
            ev?.capabilityState == CapabilityState.UNSUPPORTED_BY_ANDROID
        }

        val allowedResources = requestedResources.filter { res ->
            evaluations[res]?.isSiteAllowed == true
        }

        val askResources = requestedResources.filter { res ->
            !blockedResources.contains(res) && !allowedResources.contains(res)
        }

        // Scenario 1: All requested resources are BLOCKED
        if (blockedResources.size == requestedResources.size) {
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "EVALUATION_BLOCKED",
                status = "FAILED",
                reason = "All requested resources blocked by capability broker: $blockedResources",
                finalResult = "BLOCK"
            )
            PermissionGrantEngine.applyDeny(requestId, normOrigin, primaryType, "BLOCK", repository, request.incognito)
            onComplete?.invoke("BLOCK")
            return
        }

        // Scenario 2: Some resources require user prompt in Orion UI (ASK)
        if (askResources.isNotEmpty()) {
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "USER_PROMPT_REQUIRED",
                status = "PENDING",
                reason = "User decision required for resources: $askResources"
            )

            val promptTypes = askResources.map { res ->
                evaluations[res]?.capabilityId ?: PermissionDescriptorRegistry.mapResourceToPermissionType(res)
            }.distinct()
            val promptTypeForDialog = if (promptTypes.size > 1) "CAMERA_AND_MICROPHONE" else promptTypes.firstOrNull() ?: primaryType
            val isSecure = PermissionPolicyResolver.isSecureOrigin(normOrigin)
            val riskLevel = PermissionDescriptorRegistry.getDescriptor(primaryType)?.riskLevel ?: "Medium"

            val promptItems = askResources.map { res ->
                val desc = PermissionDescriptorRegistry.getDescriptorForResource(res)
                    ?: PermissionDescriptorRegistry.getDescriptor(res)
                val capId = desc?.capabilityId ?: PermissionDescriptorRegistry.mapResourceToPermissionType(res)
                CapabilityPromptItem(
                    capabilityId = capId,
                    displayName = desc?.displayName ?: res,
                    shortDescription = desc?.shortDescription ?: "",
                    userPromptText = desc?.userPromptText ?: "",
                    resourceName = res,
                    riskLevel = desc?.riskLevel ?: "Medium",
                    requiresAndroidRuntimePermission = desc?.requiresAndroidRuntimePermission ?: false,
                    androidPermissions = desc?.androidPermissions ?: emptyList(),
                    requiresHardware = desc?.requiresHardware ?: false,
                    hardwareFeature = desc?.hardwareFeature,
                    persistenceMode = desc?.persistenceMode ?: PersistenceMode.PERSISTENT,
                    availableActions = listOfNotNull(
                        if (desc?.allowAlwaysAvailable != false) "ALLOW_ALWAYS" else null,
                        if (desc?.allowOnceAvailable != false) "ALLOW_ONCE" else null,
                        if (desc?.blockAvailable != false) "BLOCK" else null,
                        "CANCEL"
                    )
                )
            }

            val capabilityPromptModel = CapabilityPromptModel(
                requestId = requestId,
                origin = normOrigin,
                capabilities = promptItems,
                resources = requestedResources,
                riskLevel = riskLevel,
                isSecure = isSecure,
                isIncognito = request.incognito,
                reason = "User authorization requested for ${promptItems.map { it.displayName }}",
                requestSource = request.requestSource ?: "website",
                onDecision = { decisions ->
                    PermissionDialogEngine.dismissPrompt(requestId)
                    val isAllCancel = decisions.values.all { it == "CANCEL" }
                    if (isAllCancel) {
                        PermissionGrantEngine.cancelPendingTransaction(requestId)
                        onComplete?.invoke("CANCEL")
                        return@CapabilityPromptModel
                    }

                    decisions.forEach { (capOrRes, userDec) ->
                        if (userDec != "CANCEL") {
                            val desc = PermissionDescriptorRegistry.getDescriptorForResource(capOrRes)
                                ?: PermissionDescriptorRegistry.getDescriptor(capOrRes)
                            val capId = desc?.capabilityId ?: PermissionPolicyResolver.mapResourceToPermissionType(capOrRes)
                            setPermissionState(normOrigin, capId, userDec, request.incognito)
                        }
                    }

                    val approvedFromAsk = askResources.filter { res ->
                        val desc = PermissionDescriptorRegistry.getDescriptorForResource(res)
                            ?: PermissionDescriptorRegistry.getDescriptor(res)
                        val capId = desc?.capabilityId ?: PermissionPolicyResolver.mapResourceToPermissionType(res)
                        val userDec = decisions[capId] ?: decisions[res]
                        userDec == "ALLOW_ALWAYS" || userDec == "ALLOW_ONCE"
                    }

                    val candidateAllowed = (allowedResources + approvedFromAsk).distinct()
                    evaluateAndDispatchFinalOrSystem(
                        requestId = requestId,
                        normOrigin = normOrigin,
                        primaryType = primaryType,
                        candidateAllowedResources = candidateAllowed,
                        allRequestedResources = requestedResources,
                        decisionToSave = "ALLOW_ONCE",
                        isIncognito = request.incognito,
                        androidContext = androidContext,
                        onComplete = onComplete
                    )
                }
            )

            PermissionDialogEngine.showCapabilityPrompt(capabilityPromptModel)
            return
        }

        // Scenario 3: No ASK resources, evaluate candidate allowed resources
        evaluateAndDispatchFinalOrSystem(
            requestId = requestId,
            normOrigin = normOrigin,
            primaryType = primaryType,
            candidateAllowedResources = allowedResources,
            allRequestedResources = requestedResources,
            decisionToSave = "ALLOW_ALWAYS",
            isIncognito = request.incognito,
            androidContext = androidContext,
            onComplete = onComplete
        )
    }

    private fun evaluateAndDispatchFinalOrSystem(
        requestId: String,
        normOrigin: String,
        primaryType: String,
        candidateAllowedResources: List<String>,
        allRequestedResources: List<String>,
        decisionToSave: String,
        isIncognito: Boolean,
        androidContext: Context,
        onComplete: ((String) -> Unit)?
    ) {
        if (candidateAllowedResources.isEmpty()) {
            PermissionGrantEngine.applyDeny(requestId, normOrigin, primaryType, "BLOCK", repository, isIncognito)
            onComplete?.invoke("BLOCK")
            return
        }

        val verifiedHwResources = candidateAllowedResources.filter { res ->
            val desc = PermissionDescriptorRegistry.getDescriptorForResource(res)
            desc == null || HardwareValidationEngine.validateHardwareForDescriptor(androidContext, desc)
        }

        if (verifiedHwResources.isEmpty()) {
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "HARDWARE_UNAVAILABLE",
                status = "FAILED",
                reason = "Hardware feature missing or disabled for all candidate resources.",
                finalResult = "BLOCK"
            )
            PermissionGrantEngine.applyDeny(requestId, normOrigin, primaryType, "BLOCK", repository, isIncognito)
            onComplete?.invoke("BLOCK")
            return
        }

        // Only request Android permissions for resources that are website-allowed and verified!
        val requiredAndroidPerms = PermissionDescriptorRegistry.getAndroidPermissionsForResources(verifiedHwResources)
        val missingAndroidPerms = requiredAndroidPerms.filter { perm ->
            !AndroidRuntimePermissionManager.hasPermission(androidContext, perm)
        }

        val tx = PermissionGrantEngine.getPendingTransaction(requestId)
        if (tx != null) {
            tx.preferredDecision = decisionToSave
        }

        if (missingAndroidPerms.isEmpty()) {
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "FAST_PATH_GRANT",
                status = "SUCCESS",
                reason = "Granted exact verified resources: $verifiedHwResources",
                finalResult = "ALLOW"
            )
            PermissionGrantEngine.applyGrant(
                requestId = requestId,
                origin = normOrigin,
                permissionType = primaryType,
                decision = decisionToSave,
                repository = repository,
                allowedResources = verifiedHwResources,
                isIncognito = isIncognito
            )
            onComplete?.invoke("ALLOW")
        } else {
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "ANDROID_PERMISSION_REQUESTED",
                status = "PENDING",
                reason = "Android runtime permissions required: $missingAndroidPerms"
            )
            requestAndroidPermissions(requestId, missingAndroidPerms)
            onComplete?.invoke("RESOLVED")
        }
    }

    override fun requestAndroidPermissions(requestId: String, permissions: List<String>) {
        AndroidRuntimePermissionManager.requestAndroidPermissions(context, requestId, permissions) { result ->
            resumeTransaction(requestId, result)
        }
    }

    override fun cancelWebViewRequest(requestId: String) {
        PermissionGrantEngine.cancelPendingTransaction(requestId)
    }

    override fun resumeTransaction(requestId: String, androidResult: AndroidPermissionResult) {
        val transaction = PermissionGrantEngine.getPendingTransaction(requestId)
        if (transaction == null || transaction.isTerminated.get() || transaction.stateMachine.currentState.value.isTerminal) {
            PermissionLogger.logEvent("PermissionEngine", "RESUME", "STALE_ANDROID_PERMISSION_RESULT", "resumeTransaction called for non-active or terminal transaction: $requestId")
            PermissionDiagnostics.updateTraceStage(
                requestId = requestId,
                stage = "STALE_ANDROID_PERMISSION_RESULT",
                status = "DENIED",
                reason = "Android permission result received after transaction became terminal/canceled",
                finalResult = "CANCELED"
            )
            return
        }

        val dynamicOrigin = DynamicOrigin.parse(
            rawUrl = transaction.origin,
            tabId = transaction.tabId,
            isIncognito = transaction.isIncognito,
            requestId = requestId
        )
        val normOrigin = dynamicOrigin.canonicalOrigin
        val resourceDecisions = mutableListOf<ResourcePermissionDecision>()
        val allowedResources = mutableListOf<String>()
        val deniedResources = mutableListOf<String>()
        val androidPermissionsUsed = mutableSetOf<String>()

        transaction.resources.forEach { res ->
            val descriptor = PermissionDescriptorRegistry.getDescriptorForResource(res)
            val permType = descriptor?.permissionType ?: PermissionPolicyResolver.mapResourceToPermissionType(res)
            val cachedSiteDecision = PermissionCache.getCachedDecision(normOrigin, permType) ?: "ASK"

            val capEval = CapabilityBroker.evaluateCapability(res, dynamicOrigin, context, cachedSiteDecision)

            val reqAndroidPerms = capEval.androidPermissionsRequired
            androidPermissionsUsed.addAll(reqAndroidPerms)

            val areAndroidPermsOk = reqAndroidPerms.isEmpty() || reqAndroidPerms.all { perm ->
                androidResult.individuallyGrantedPermissions[perm] == true ||
                        AndroidRuntimePermissionManager.hasPermission(context, perm)
            }
            val androidDecisionStr = if (areAndroidPermsOk) "GRANTED" else "DENIED"
            val hwDecisionStr = if (capEval.isHardwareAvailable) "AVAILABLE" else "UNAVAILABLE"
            val secDecisionStr = if (capEval.isSecuritySatisfied) "ALLOWED" else "BLOCKED"

            val finalState: ResourceDecisionState
            val reasonText: String

            if (!capEval.isSecuritySatisfied) {
                finalState = ResourceDecisionState.SECURITY_BLOCKED
                reasonText = "Security policy violated for $res: ${capEval.reason}"
            } else if (!capEval.isHardwareAvailable) {
                finalState = ResourceDecisionState.HARDWARE_UNAVAILABLE
                reasonText = "Hardware unavailable for $res"
            } else if (!areAndroidPermsOk) {
                finalState = ResourceDecisionState.SYSTEM_PERMISSION_BLOCKED
                reasonText = "Android runtime permission denied for $res"
            } else if (capEval.isSiteBlocked) {
                finalState = ResourceDecisionState.BLOCK
                reasonText = "Website decision is BLOCK for $permType"
            } else {
                finalState = ResourceDecisionState.ALLOW
                reasonText = "All checks cleared for $res (Site decision & Android permissions granted)"
            }

            val decisionObj = ResourcePermissionDecision(
                permissionType = permType,
                webViewResource = res,
                websiteDecision = cachedSiteDecision,
                securityDecision = secDecisionStr,
                androidDecision = androidDecisionStr,
                hardwareDecision = hwDecisionStr,
                finalDecision = finalState,
                reason = reasonText
            )
            resourceDecisions.add(decisionObj)

            if (finalState == ResourceDecisionState.ALLOW) {
                allowedResources.add(res)
            } else {
                deniedResources.add(res)
            }
        }

        val primaryType = transaction.resources.firstOrNull()?.let {
            PermissionDescriptorRegistry.mapResourceToPermissionType(it)
        } ?: "UNKNOWN"

        val finalDecisionObj = FinalPermissionDecision(
            requestId = requestId,
            origin = normOrigin,
            tabId = transaction.tabId,
            overallDecision = if (allowedResources.isNotEmpty()) "ALLOW" else "BLOCK",
            resourceDecisions = resourceDecisions,
            allowedResources = allowedResources,
            deniedResources = deniedResources,
            androidPermissionsUsed = androidPermissionsUsed.toList(),
            reason = if (allowedResources.isNotEmpty()) "Granted ${allowedResources.size} of ${transaction.resources.size} requested resources: $allowedResources" else "Denied all requested resources"
        )

        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "FINAL_EVALUATION",
            status = if (allowedResources.isNotEmpty()) "SUCCESS" else "DENIED",
            reason = finalDecisionObj.reason,
            finalResult = finalDecisionObj.overallDecision
        )

        val decisionToApply = if (transaction.isIncognito) "ALLOW_ONCE" else transaction.preferredDecision
        if (allowedResources.isNotEmpty()) {
            PermissionGrantEngine.applyGrant(
                requestId = requestId,
                origin = normOrigin,
                permissionType = primaryType,
                decision = decisionToApply,
                repository = repository,
                allowedResources = allowedResources,
                isIncognito = transaction.isIncognito
            )
        } else {
            PermissionGrantEngine.applyDeny(
                requestId = requestId,
                origin = normOrigin,
                permissionType = primaryType,
                decision = "BLOCK",
                repository = repository,
                isIncognito = transaction.isIncognito
            )
        }
    }

    private fun mapLegacyPermission(perm: String): String {
        return when (perm.lowercase()) {
            "camera" -> "CAMERA"
            "microphone" -> "MICROPHONE"
            "location" -> "LOCATION"
            "storage" -> "STORAGE"
            "notifications" -> "NOTIFICATIONS"
            else -> perm.uppercase()
        }
    }
}
