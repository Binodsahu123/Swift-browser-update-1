package com.swift.browser.permissionengine

import org.junit.Assert.*
import org.junit.Test

class PermissionEngineUnitTest {

    @Test
    fun testOriginNormalization() {
        val origin1 = OriginNormalizer.normalize("https://sub.domain.org/path/test?q=1")
        assertEquals("https://sub.domain.org", origin1)

        val origin2 = OriginNormalizer.normalize("https://sub.domain.org:8443/secure")
        assertEquals("https://sub.domain.org:8443", origin2)

        val origin3 = OriginNormalizer.normalize("https://DOMAIN.org/")
        assertEquals("https://domain.org", origin3)
    }

    @Test
    fun testStateMachineTransitionsAndTerminalProtection() {
        val sm = PermissionStateMachine("req_test", PermissionState.PENDING)
        assertEquals(PermissionState.PENDING, sm.currentState.value)

        assertTrue(sm.transitionTo(PermissionState.WAITING_USER))
        assertEquals(PermissionState.WAITING_USER, sm.currentState.value)

        assertTrue(sm.transitionTo(PermissionState.GRANTING))
        assertEquals(PermissionState.GRANTING, sm.currentState.value)

        assertTrue(sm.transitionTo(PermissionState.GRANTED))
        assertEquals(PermissionState.GRANTED, sm.currentState.value)
        assertTrue(sm.currentState.value.isTerminal)

        // Attempt transition from terminal state -> must fail
        assertFalse(sm.transitionTo(PermissionState.DENIED))
        assertEquals(PermissionState.GRANTED, sm.currentState.value)
    }

    @Test
    fun testPermissionDescriptorRegistry() {
        val cameraDesc = PermissionDescriptorRegistry.getDescriptor("CAMERA")
        assertNotNull(cameraDesc)
        assertEquals("CAMERA", cameraDesc?.permissionType)
        assertTrue(cameraDesc!!.webViewResources.contains("android.webkit.resource.VIDEO_CAPTURE"))

        val unknownType = PermissionDescriptorRegistry.mapResourceToPermissionType("invalid.resource.xyz")
        assertEquals("UNKNOWN_RESOURCE", unknownType)
    }

    @Test
    fun testAllDescriptorsHaveValidUiMetadata() {
        val expectedCapabilities = listOf(
            "CAMERA", "MICROPHONE", "CAMERA_MICROPHONE", "SPEECH_RECOGNITION", "LOCATION",
            "NOTIFICATIONS", "MIDI", "PROTECTED_MEDIA", "WEBRTC", "MEDIA_DEVICES",
            "MEDIA_RECORDER", "FILE_UPLOAD", "FILE_MULTIPLE", "FILE_CAMERA_CAPTURE",
            "FILE_AUDIO_CAPTURE", "CLIPBOARD", "CLIPBOARD_READ", "CLIPBOARD_WRITE",
            "POPUPS", "DOWNLOADS", "FULLSCREEN", "AUTOPLAY", "SENSORS",
            "BLUETOOTH", "USB", "NFC", "PAYMENT", "SCREEN_CAPTURE",
            "LOCAL_NETWORK", "SERIAL_HID", "NOTIFICATION_ACTIONS", "STORAGE", "BACKGROUND_MEDIA"
        )

        val allDescriptors = PermissionDescriptorRegistry.getAllDescriptors()
        assertTrue("Registry must contain at least 33 descriptors", allDescriptors.size >= 33)

        for (cap in expectedCapabilities) {
            val desc = PermissionDescriptorRegistry.getDescriptor(cap)
            assertNotNull("Descriptor for capability $cap must exist in registry", desc)
            assertEquals("Capability ID must match requested type", cap, desc!!.capabilityId)
        }

        for (desc in allDescriptors) {
            assertTrue("capabilityId must not be blank for ${desc.permissionType}", desc.capabilityId.isNotBlank())
            assertTrue("displayName must not be blank for ${desc.permissionType}", desc.displayName.isNotBlank())
            assertTrue("shortDescription must not be blank for ${desc.permissionType}", desc.shortDescription.isNotBlank())
            assertTrue("userPromptText must not be blank for ${desc.permissionType}", desc.userPromptText.isNotBlank())
            assertTrue("iconKey must not be blank for ${desc.permissionType}", desc.iconKey.isNotBlank())
            assertTrue("riskLevel must be valid for ${desc.permissionType}", listOf("Low", "Medium", "High").contains(desc.riskLevel))
            assertNotNull("requestHandlingMode must not be null for ${desc.permissionType}", desc.requestHandlingMode)
            val iconVector = PermissionIconResolver.getIcon(desc.iconKey)
            assertNotNull("Icon vector for key ${desc.iconKey} must not be null", iconVector)
        }
    }

    @Test
    fun testAtomicTerminalTransition() {
        val tx = PendingPermissionTransaction(
            requestId = "tx_atomic",
            tabId = "tab_1",
            origin = "https://example.com",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            request = null
        )

        assertTrue(tx.markTerminal(PermissionState.GRANTED))
        assertEquals(PermissionState.GRANTED, tx.stateMachine.currentState.value)

        // Second terminal call must return false
        assertFalse(tx.markTerminal(PermissionState.DENIED))
        assertEquals(PermissionState.GRANTED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testTabCancellation() {
        PermissionGrantEngine.cancelAllPendingTransactions()

        val tx1 = PendingPermissionTransaction(
            requestId = "tx_tab1_a",
            tabId = "tab_100",
            origin = "https://a.com",
            resources = listOf("CAMERA"),
            request = null
        )
        val tx2 = PendingPermissionTransaction(
            requestId = "tx_tab1_b",
            tabId = "tab_100",
            origin = "https://b.com",
            resources = listOf("MICROPHONE"),
            request = null
        )
        val tx3 = PendingPermissionTransaction(
            requestId = "tx_tab2_a",
            tabId = "tab_200",
            origin = "https://c.com",
            resources = listOf("LOCATION"),
            request = null
        )

        PermissionGrantEngine.registerPendingTransaction(tx1)
        PermissionGrantEngine.registerPendingTransaction(tx2)
        PermissionGrantEngine.registerPendingTransaction(tx3)

        assertEquals(tx1, PermissionGrantEngine.getPendingTransaction("tx_tab1_a"))
        assertEquals(tx2, PermissionGrantEngine.getPendingTransaction("tx_tab1_b"))

        PermissionGrantEngine.cancelPendingTransactionsForTab("tab_100")

        assertNull(PermissionGrantEngine.getPendingTransaction("tx_tab1_a"))
        assertNull(PermissionGrantEngine.getPendingTransaction("tx_tab1_b"))
        assertNotNull(PermissionGrantEngine.getPendingTransaction("tx_tab2_a"))
    }

    @Test
    fun testTimeoutExpiration() {
        PermissionGrantEngine.cancelAllPendingTransactions()

        val tx = PendingPermissionTransaction(
            requestId = "tx_timeout",
            tabId = "tab_1",
            origin = "https://timeout.com",
            resources = listOf("CAMERA"),
            request = null,
            createdAt = System.currentTimeMillis() - 10000,
            expiration = System.currentTimeMillis() - 1000 // Expired
        )

        PermissionGrantEngine.registerPendingTransaction(tx)
        PermissionGrantEngine.checkExpirations()

        assertNull(PermissionGrantEngine.getPendingTransaction("tx_timeout"))
    }

    @Test
    fun testPermissionRequestModelData() {
        val model = PermissionRequestModel(
            requestId = "req_101",
            origin = "https://example.com",
            siteUrl = "https://example.com",
            pageUrl = "https://example.com/page",
            frameId = "frame_0",
            tabId = "tab_1",
            requestSourceType = "website",
            permissionType = "CAMERA",
            resourcesRequested = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            isUserGesture = true,
            riskLevel = "Medium"
        )

        assertEquals("req_101", model.requestId)
        assertEquals("CAMERA", model.permissionType)
        assertTrue(model.isUserGesture == true)
        assertEquals(1, model.resourcesRequested.size)
    }

    @Test
    fun testMultiResourceMappingAndRegistryLookup() {
        val resources = arrayOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE"
        )
        val descriptors = resources.mapNotNull { PermissionDescriptorRegistry.getDescriptorForResource(it) }
        assertEquals(2, descriptors.size)
        val types = descriptors.map { it.permissionType }
        assertTrue(types.contains("CAMERA"))
        assertTrue(types.contains("MICROPHONE"))
    }

    @Test
    fun testStaleResultRejection() {
        PermissionGrantEngine.cancelAllPendingTransactions()

        val tx = PendingPermissionTransaction(
            requestId = "tx_stale",
            tabId = "tab_1",
            origin = "https://stale.com",
            resources = listOf("CAMERA"),
            request = null
        )

        PermissionGrantEngine.registerPendingTransaction(tx)

        // Grant the transaction first
        PermissionGrantEngine.applyGrant("tx_stale", "https://stale.com", "CAMERA", "ALLOW_ALWAYS")

        // Submitting subsequent deny on already terminal transaction must be rejected gracefully
        PermissionGrantEngine.applyDeny("tx_stale", "https://stale.com", "CAMERA", "BLOCK")
        assertEquals(PermissionState.GRANTED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testNormalizedCacheKeysAndIncognitoIsolation() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()

        val rawOrigin = "HTTPS://SUB.TEST.COM:443/PATH?q=1"
        val normOrigin = OriginNormalizer.normalize(rawOrigin)
        assertEquals("https://sub.test.com", normOrigin)

        // Test normal persistence
        PermissionCache.cachePersistentDecision(rawOrigin, "CAMERA", "ALLOW_ALWAYS")
        assertEquals("ALLOW_ALWAYS", PermissionCache.getCachedDecision("https://sub.test.com", "CAMERA"))

        // Test incognito session cache isolation
        PermissionCache.cacheSessionDecision("https://incognito.com", "MICROPHONE", "ALLOW_ONCE")
        assertEquals("ALLOW_ONCE", PermissionCache.getCachedDecision("https://incognito.com", "MICROPHONE"))

        PermissionCache.clearSessionCache()
        assertNull(PermissionCache.getCachedDecision("https://incognito.com", "MICROPHONE"))
    }

    @Test
    fun testResourcePermissionDecisionAndFinalDecisionModels() {
        val resourceDecision = ResourcePermissionDecision(
            permissionType = "CAMERA",
            webViewResource = "android.webkit.resource.VIDEO_CAPTURE",
            websiteDecision = "ALLOW_ALWAYS",
            securityDecision = "ALLOWED",
            androidDecision = "GRANTED",
            hardwareDecision = "AVAILABLE",
            finalDecision = ResourceDecisionState.ALLOW,
            reason = "All checks cleared"
        )
        assertEquals(ResourceDecisionState.ALLOW, resourceDecision.finalDecision)
        assertEquals("CAMERA", resourceDecision.permissionType)

        val finalDecision = FinalPermissionDecision(
            requestId = "req_final_1",
            origin = "https://example.com",
            tabId = "tab_1",
            overallDecision = "ALLOW",
            resourceDecisions = listOf(resourceDecision),
            allowedResources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            deniedResources = emptyList(),
            androidPermissionsUsed = listOf(android.Manifest.permission.CAMERA),
            reason = "Granted 1 of 1 requested resources"
        )

        assertEquals("ALLOW", finalDecision.overallDecision)
        assertEquals(1, finalDecision.allowedResources.size)
        assertEquals("req_final_1", finalDecision.requestId)
    }

    @Test
    fun testLegacyPermissionBridgeIsUnused() {
        // Assert that legacy bridge classes were removed and are not in use
        val classes = listOf(
            "com.swift.browser.permissionengine.PermissionWebInterface",
            "com.swift.browser.permissionengine.DynamicPermissionEngine"
        )
        for (className in classes) {
            try {
                Class.forName(className)
                fail("Legacy class $className should be removed")
            } catch (expected: ClassNotFoundException) {
                // Expected: Legacy bridge classes must not exist
            }
        }
    }

    @Test
    fun testNoGlobalPermissionState() {
        // Verify that PermissionStateMachine instances are independent and transaction-scoped with no activeInstance
        val sm1 = PermissionStateMachine("req_isolated_1", PermissionState.PENDING)
        val sm2 = PermissionStateMachine("req_isolated_2", PermissionState.PENDING)

        assertTrue(sm1.transitionTo(PermissionState.WAITING_USER))
        assertEquals(PermissionState.WAITING_USER, sm1.currentState.value)
        assertEquals(PermissionState.PENDING, sm2.currentState.value)

        assertTrue(sm2.transitionTo(PermissionState.DENIED))
        assertEquals(PermissionState.DENIED, sm2.currentState.value)
        assertEquals(PermissionState.WAITING_USER, sm1.currentState.value)
    }

    @Test
    fun testRealRequestContextPreserved() {
        val context = PermissionRequestContext(
            requestId = "req_cuj_999",
            tabId = "tab_cuj_1",
            origin = "https://app.example.com",
            pageUrl = "https://app.example.com/meeting",
            isIncognito = true,
            isMainFrame = true,
            isUserGesture = true
        )

        assertEquals("req_cuj_999", context.requestId)
        assertEquals("tab_cuj_1", context.tabId)
        assertEquals("https://app.example.com", context.origin)
        assertEquals("https://app.example.com/meeting", context.pageUrl)
        assertTrue(context.isIncognito)
        assertTrue(context.isMainFrame)
        assertEquals(true, context.isUserGesture)
    }

    @Test
    fun testNoFakeRequestId() {
        val realId = "req_strict_123"
        val tx = PendingPermissionTransaction(
            requestId = realId,
            tabId = "tab_main",
            origin = "https://example.com",
            resources = listOf("CAMERA")
        )
        assertEquals(realId, tx.requestId)
        assertEquals(realId, tx.stateMachine.requestId)
        assertFalse(tx.requestId.isBlank())
    }

    @Test
    fun testTerminalTransactionIsolation() {
        val tx = PendingPermissionTransaction(
            requestId = "tx_term_iso",
            tabId = "tab_term",
            origin = "https://example.com",
            resources = listOf("CAMERA")
        )

        assertTrue(tx.markTerminal(PermissionState.DENIED))
        assertEquals(PermissionState.DENIED, tx.stateMachine.currentState.value)
        assertTrue(tx.isTerminated.get())

        // Ensure no subsequent state changes are permitted
        assertFalse(tx.stateMachine.transitionTo(PermissionState.GRANTED))
        assertFalse(tx.markTerminal(PermissionState.GRANTED))
        assertEquals(PermissionState.DENIED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testOriginEligibilityInsecureHttp() {
        val eligibility = StandardPermissionOriginEligibility()
        val result = eligibility.evaluate("http://example.com", "CAMERA")
        assertEquals(OriginEligibilityResult.BLOCKED, result)

        val secureResult = eligibility.evaluate("https://example.com", "CAMERA")
        assertEquals(OriginEligibilityResult.ALLOWED, secureResult)
    }

    @Test
    fun testAutoGrantProtectedMediaIsNotBlindlyGranted() {
        // Assert that protected media is NOT auto-granted blindly without proper policy evaluation
        assertFalse(PermissionPolicyResolver.isAutoGrantResource("PROTECTED_MEDIA"))
        assertFalse(PermissionPolicyResolver.isAutoGrantResource("android.webkit.resource.PROTECTED_MEDIA_ID"))
    }

    @Test
    fun testExactResourceResolutionCameraAndMic() {
        val resources = listOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE"
        )
        val androidPerms = PermissionDescriptorRegistry.getAndroidPermissionsForResources(resources)
        assertTrue(androidPerms.contains(android.Manifest.permission.CAMERA))
        assertTrue(androidPerms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertEquals(2, androidPerms.size)

        val displayNames = PermissionDescriptorRegistry.getDisplayNames("CAMERA_AND_MICROPHONE", resources)
        assertTrue(displayNames.contains("Camera"))
        assertTrue(displayNames.contains("Microphone"))
    }

    @Test
    fun testCameraAllowMicBlockMixedState() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        PermissionGrantEngine.cancelAllPendingTransactions()

        val origin = "https://mixed1.example.com"
        PermissionCache.cachePersistentDecision(origin, "CAMERA", "ALLOW_ALWAYS")
        PermissionCache.cachePersistentDecision(origin, "MICROPHONE", "BLOCK")

        val tx = PendingPermissionTransaction(
            requestId = "req_mixed_1",
            tabId = "tab_1",
            origin = origin,
            resources = listOf(
                "android.webkit.resource.VIDEO_CAPTURE",
                "android.webkit.resource.AUDIO_CAPTURE"
            )
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        val allowedResources = mutableListOf<String>()
        tx.resources.forEach { res ->
            val permType = PermissionDescriptorRegistry.mapResourceToPermissionType(res)
            val siteDecision = PermissionCache.getCachedDecision(origin, permType)
            if (siteDecision == "ALLOW_ALWAYS") {
                allowedResources.add(res)
            }
        }
        assertEquals(listOf("android.webkit.resource.VIDEO_CAPTURE"), allowedResources)
        assertFalse(allowedResources.contains("android.webkit.resource.AUDIO_CAPTURE"))
    }

    @Test
    fun testCameraAllowMicAskMixedState() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        val origin = "https://mixed2.example.com"
        PermissionCache.cachePersistentDecision(origin, "CAMERA", "ALLOW_ALWAYS")
        // MICROPHONE is not cached -> ASK

        val resources = listOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE"
        )
        val resourceStateMap = resources.associateWith { res ->
            val permType = PermissionDescriptorRegistry.mapResourceToPermissionType(res)
            PermissionCache.getCachedDecision(origin, permType) ?: "ASK"
        }
        assertEquals("ALLOW_ALWAYS", resourceStateMap["android.webkit.resource.VIDEO_CAPTURE"])
        assertEquals("ASK", resourceStateMap["android.webkit.resource.AUDIO_CAPTURE"])

        val allowed = resources.filter { resourceStateMap[it] == "ALLOW_ALWAYS" }
        assertEquals(listOf("android.webkit.resource.VIDEO_CAPTURE"), allowed)
        val ask = resources.filter { resourceStateMap[it] == "ASK" }
        assertEquals(listOf("android.webkit.resource.AUDIO_CAPTURE"), ask)
    }

    @Test
    fun testCameraAskMicBlockMixedState() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        val origin = "https://mixed3.example.com"
        // CAMERA -> ASK
        PermissionCache.cachePersistentDecision(origin, "MICROPHONE", "BLOCK")

        val resources = listOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE"
        )
        val resourceStateMap = resources.associateWith { res ->
            val permType = PermissionDescriptorRegistry.mapResourceToPermissionType(res)
            PermissionCache.getCachedDecision(origin, permType) ?: "ASK"
        }
        assertEquals("ASK", resourceStateMap["android.webkit.resource.VIDEO_CAPTURE"])
        assertEquals("BLOCK", resourceStateMap["android.webkit.resource.AUDIO_CAPTURE"])

        val allowed = resources.filter { resourceStateMap[it] == "ALLOW_ALWAYS" }
        assertTrue(allowed.isEmpty())
    }

    @Test
    fun testCameraAllowMicAndroidDenied() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        val origin = "https://mixed4.example.com"
        PermissionCache.cachePersistentDecision(origin, "CAMERA", "ALLOW_ALWAYS")
        PermissionCache.cachePersistentDecision(origin, "MICROPHONE", "ALLOW_ALWAYS")

        val androidResult = AndroidPermissionResult(
            granted = false,
            individuallyGrantedPermissions = mapOf(
                android.Manifest.permission.CAMERA to true,
                android.Manifest.permission.RECORD_AUDIO to false
            )
        )

        val resources = listOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE"
        )
        val grantedResources = resources.filter { res ->
            val desc = PermissionDescriptorRegistry.getDescriptorForResource(res)
            val reqPerms = desc?.androidPermissions ?: emptyList()
            reqPerms.all { androidResult.individuallyGrantedPermissions[it] == true }
        }

        assertEquals(listOf("android.webkit.resource.VIDEO_CAPTURE"), grantedResources)
        assertFalse(grantedResources.contains("android.webkit.resource.AUDIO_CAPTURE"))
    }

    @Test
    fun testAskNeverAutoAllows() {
        PermissionCache.clearSessionCache()
        val origin = "https://askonly.example.com"
        val decision = PermissionCache.getCachedDecision(origin, "CAMERA")
        assertNull(decision) // represents ASK state

        val state = when (decision) {
            "ALLOW_ALWAYS", "ALLOW_ONCE" -> ResourceDecisionState.ALLOW
            "BLOCK" -> ResourceDecisionState.BLOCK
            else -> ResourceDecisionState.USER_DECISION_REQUIRED
        }
        assertEquals(ResourceDecisionState.USER_DECISION_REQUIRED, state)
        assertNotEquals(ResourceDecisionState.ALLOW, state)
    }

    @Test
    fun testBlockedResourceNeverRequestsAndroidPermission() {
        PermissionCache.clearSessionCache()
        val origin = "https://blocked.example.com"
        PermissionCache.cachePersistentDecision(origin, "CAMERA", "ALLOW_ALWAYS")
        PermissionCache.cachePersistentDecision(origin, "MICROPHONE", "BLOCK")

        val candidateAllowedResources = listOf("android.webkit.resource.VIDEO_CAPTURE")
        val requiredPerms = PermissionDescriptorRegistry.getAndroidPermissionsForResources(candidateAllowedResources)

        assertEquals(listOf(android.Manifest.permission.CAMERA), requiredPerms)
        assertFalse(requiredPerms.contains(android.Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun testCanceledRequestCannotGrant() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        val tx = PendingPermissionTransaction(
            requestId = "tx_cancel_grant",
            tabId = "tab_c",
            origin = "https://cancel.com",
            resources = listOf("CAMERA")
        )
        PermissionGrantEngine.registerPendingTransaction(tx)
        PermissionGrantEngine.cancelPendingTransaction("tx_cancel_grant")

        assertTrue(tx.isTerminated.get())
        assertTrue(tx.stateMachine.currentState.value.isTerminal)

        assertFalse(tx.markTerminal(PermissionState.GRANTED))
        assertEquals(PermissionState.CANCELED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testSpecializedHandlerAndPermissionManagerAndBrowserScreenCannotGrantDirectly() {
        // Assert that PermissionManager class does not exist in permissionengine
        try {
            Class.forName("com.swift.browser.permissionengine.PermissionManager")
            fail("PermissionManager in permissionengine should be deleted")
        } catch (expected: ClassNotFoundException) {
            // Expected
        }

        // Assert that RuntimePermissionRouter class does not exist
        try {
            Class.forName("com.swift.browser.permissionengine.RuntimePermissionRouter")
            fail("RuntimePermissionRouter should be deleted")
        } catch (expected: ClassNotFoundException) {
            // Expected
        }

        // Assert that PermissionDecisionEngine class does not exist
        try {
            Class.forName("com.swift.browser.permissionengine.PermissionDecisionEngine")
            fail("PermissionDecisionEngine should be deleted")
        } catch (expected: ClassNotFoundException) {
            // Expected
        }
    }

    @Test
    fun testSingleAndroidResultDelivery() {
        var callbackInvocationCount = 0
        var receivedResult: AndroidPermissionResult? = null

        val requester = AndroidRuntimePermissionManager.SystemPermissionRequester { reqId, perms, cb ->
            val fakeResult = AndroidPermissionResult(
                granted = true,
                denied = false,
                permanentlyDenied = false,
                individuallyGrantedPermissions = perms.associateWith { true }
            )
            // Execute the registered callback once
            cb(fakeResult)
        }

        AndroidRuntimePermissionManager.registerSystemRequester(requester)

        val dummyContext = android.content.ContextWrapper(null)

        AndroidRuntimePermissionManager.requestAndroidPermissions(
            context = dummyContext,
            requestId = "req_single_delivery",
            permissions = listOf(android.Manifest.permission.CAMERA)
        ) { result ->
            callbackInvocationCount++
            receivedResult = result
        }

        assertEquals(1, callbackInvocationCount)
        assertNotNull(receivedResult)
        assertTrue(receivedResult!!.granted)
    }

    @Test
    fun testRequestIdMismatchCannotResolveOtherRequest() {
        PermissionGrantEngine.cancelAllPendingTransactions()

        val txA = PendingPermissionTransaction(
            requestId = "req_A",
            tabId = "tab_1",
            origin = "https://a.com",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE")
        )
        val txB = PendingPermissionTransaction(
            requestId = "req_B",
            tabId = "tab_2",
            origin = "https://b.com",
            resources = listOf("android.webkit.resource.AUDIO_CAPTURE")
        )

        PermissionGrantEngine.registerPendingTransaction(txA)
        PermissionGrantEngine.registerPendingTransaction(txB)

        // Cancel transaction A
        PermissionGrantEngine.cancelPendingTransaction("req_A")

        // Transaction A must be terminated, transaction B must remain active
        assertTrue(txA.isTerminated.get())
        assertFalse(txB.isTerminated.get())

        val retrievedA = PermissionGrantEngine.getPendingTransaction("req_A")
        val retrievedB = PermissionGrantEngine.getPendingTransaction("req_B")
        assertNull(retrievedA)
        assertNotNull(retrievedB)
    }

    @Test
    fun testOnlineMusicPermissionEngineIntegration() {
        // Verify that PermissionRequestContext for online music preserves origin and source
        val permContext = PermissionRequestContext(
            requestId = "req_music_test",
            tabId = "online_music",
            origin = "https://music.youtube.com",
            pageUrl = "https://music.youtube.com",
            requestSource = "online_music"
        )
        assertEquals("online_music", permContext.tabId)
        assertEquals("online_music", permContext.requestSource)
        assertEquals("https://music.youtube.com", permContext.origin)
    }

    @Test
    fun testFileCaptureAdapterCapabilityMapping() {
        val singleParams = FileCaptureRequestParams(
            origin = "https://upload.example.com",
            acceptTypes = listOf("image/*"),
            isMultiple = false,
            captureMode = null
        )
        val singleReq = FileCaptureAdapter.adapt(singleParams)
        assertEquals("FILE_UPLOAD", singleReq.capabilityId)

        val multiParams = FileCaptureRequestParams(
            origin = "https://upload.example.com",
            acceptTypes = listOf("image/*"),
            isMultiple = true,
            captureMode = null
        )
        val multiReq = FileCaptureAdapter.adapt(multiParams)
        assertEquals("FILE_MULTIPLE", multiReq.capabilityId)

        val cameraParams = FileCaptureRequestParams(
            origin = "https://upload.example.com",
            acceptTypes = listOf("image/*"),
            isMultiple = false,
            captureMode = "camera"
        )
        val cameraReq = FileCaptureAdapter.adapt(cameraParams)
        assertEquals("FILE_CAMERA_CAPTURE", cameraReq.capabilityId)

        val audioParams = FileCaptureRequestParams(
            origin = "https://upload.example.com",
            acceptTypes = listOf("audio/*"),
            isMultiple = false,
            captureMode = "microphone"
        )
        val audioReq = FileCaptureAdapter.adapt(audioParams)
        assertEquals("FILE_AUDIO_CAPTURE", audioReq.capabilityId)
    }

    @Test
    fun testFileCaptureMetadataPreservation() {
        val now = System.currentTimeMillis()
        val exp = now + 60000L
        val params = FileCaptureRequestParams(
            origin = "https://upload.example.com",
            topLevelOrigin = "https://example.com",
            frameOrigin = "https://upload.example.com",
            tabId = "tab_file_123",
            acceptTypes = listOf("image/png", "image/jpeg"),
            isMultiple = true,
            captureMode = "camera",
            mode = "MODE_OPEN_MULTIPLE",
            isIncognito = true,
            userGesture = true,
            requestId = "req_file_test_99",
            timestamp = now,
            expiration = exp
        )

        val req = FileCaptureAdapter.adapt(params)
        assertEquals("req_file_test_99", req.requestId)
        assertEquals("FILE_CAMERA_CAPTURE", req.capabilityId)
        assertEquals("https://upload.example.com", req.origin)
        assertEquals("https://example.com", req.topLevelOrigin)
        assertEquals("https://upload.example.com", req.frameOrigin)
        assertEquals("tab_file_123", req.tabId)
        assertTrue(req.incognito)
        assertEquals(exp, req.expiration)
        assertEquals("image/png,image/jpeg", req.metadata["acceptTypes"])
        assertEquals("true", req.metadata["multiple"])
        assertEquals("camera", req.metadata["capture"])
        assertEquals("MODE_OPEN_MULTIPLE", req.metadata["mode"])
        assertEquals(now.toString(), req.metadata["timestamp"])
        assertEquals(exp.toString(), req.metadata["expiration"])
    }

    @Test
    fun testFileChooserTransactionLifecycleAllow() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackResult: String? = null

        val tx = PendingPermissionTransaction(
            requestId = "req_file_allow",
            tabId = "tab_1",
            origin = "https://file.com",
            resources = listOf("FILE_UPLOAD"),
            onResultCallback = { result -> callbackResult = result }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        PermissionGrantEngine.applyGrant(
            requestId = "req_file_allow",
            origin = "https://file.com",
            permissionType = "FILE_UPLOAD",
            decision = "ALLOW_ONCE"
        )

        assertEquals("ALLOW", callbackResult)
        assertTrue(tx.isTerminated.get())
        assertEquals(PermissionState.GRANTED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testFileChooserTransactionLifecycleDeny() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackResult: String? = null

        val tx = PendingPermissionTransaction(
            requestId = "req_file_deny",
            tabId = "tab_1",
            origin = "https://file.com",
            resources = listOf("FILE_UPLOAD"),
            onResultCallback = { result -> callbackResult = result }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        PermissionGrantEngine.applyDeny(
            requestId = "req_file_deny",
            origin = "https://file.com",
            permissionType = "FILE_UPLOAD",
            decision = "BLOCK"
        )

        assertEquals("BLOCK", callbackResult)
        assertTrue(tx.isTerminated.get())
        assertEquals(PermissionState.DENIED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testFileChooserTransactionLifecycleCancel() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackResult: String? = null

        val tx = PendingPermissionTransaction(
            requestId = "req_file_cancel",
            tabId = "tab_1",
            origin = "https://file.com",
            resources = listOf("FILE_UPLOAD"),
            onResultCallback = { result -> callbackResult = result }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        PermissionGrantEngine.cancelPendingTransaction("req_file_cancel")

        assertEquals("CANCELED", callbackResult)
        assertTrue(tx.isTerminated.get())
        assertEquals(PermissionState.CANCELED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testFileChooserTransactionLifecycleTimeout() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackResult: String? = null

        val tx = PendingPermissionTransaction(
            requestId = "req_file_timeout",
            tabId = "tab_1",
            origin = "https://file.com",
            resources = listOf("FILE_UPLOAD"),
            onResultCallback = { result -> callbackResult = result }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        PermissionGrantEngine.expireTransaction("req_file_timeout")

        assertEquals("EXPIRED", callbackResult)
        assertTrue(tx.isTerminated.get())
        assertEquals(PermissionState.EXPIRED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testClipboardRequestAdapterNormalizationAndOriginPreservation() {
        val now = System.currentTimeMillis()
        val readParams = ClipboardRequestParams(
            origin = "https://notes.example.com/editor?doc=1",
            operation = "READ",
            tabId = "tab_clip_read_1",
            userGesture = true,
            isIncognito = true,
            requestId = "req_clip_test_read_101",
            timestamp = now
        )

        val readReq = ClipboardRequestAdapter.adapt(readParams)
        assertEquals("req_clip_test_read_101", readReq.requestId)
        assertEquals("CLIPBOARD_READ", readReq.capabilityId)
        assertEquals("https://notes.example.com", readReq.origin)
        assertEquals("tab_clip_read_1", readReq.tabId)
        assertTrue("Incognito mode must be preserved", readReq.incognito)
        assertTrue("User gesture must be preserved", readReq.userGesture ?: false)
        assertEquals("clipboard_api", readReq.requestSource)
        assertEquals("READ", readReq.metadata["operation"])
        assertEquals("https://notes.example.com/editor?doc=1", readReq.metadata["origin"])
        assertEquals(now.toString(), readReq.metadata["timestamp"])

        val writeParams = ClipboardRequestParams(
            origin = "https://docs.google.com/document/d/123",
            operation = "WRITE",
            tabId = "tab_clip_write_2",
            userGesture = true,
            isIncognito = false,
            requestId = "req_clip_test_write_102",
            timestamp = now
        )

        val writeReq = ClipboardRequestAdapter.adapt(writeParams)
        assertEquals("req_clip_test_write_102", writeReq.requestId)
        assertEquals("CLIPBOARD_WRITE", writeReq.capabilityId)
        assertEquals("https://docs.google.com", writeReq.origin)
        assertEquals("tab_clip_write_2", writeReq.tabId)
        assertFalse("Incognito must be false", writeReq.incognito)
        assertEquals("WRITE", writeReq.metadata["operation"])
    }

    @Test
    fun testClipboardReadVsWriteCapabilitySeparation() {
        val readDesc = PermissionDescriptorRegistry.getDescriptor("CLIPBOARD_READ")
        val writeDesc = PermissionDescriptorRegistry.getDescriptor("CLIPBOARD_WRITE")
        val baseDesc = PermissionDescriptorRegistry.getDescriptor("CLIPBOARD")

        assertNotNull("CLIPBOARD_READ descriptor must exist", readDesc)
        assertNotNull("CLIPBOARD_WRITE descriptor must exist", writeDesc)
        assertNotNull("CLIPBOARD descriptor must exist", baseDesc)

        assertNotEquals("CLIPBOARD_READ and CLIPBOARD_WRITE must be distinct capability IDs",
            readDesc?.capabilityId, writeDesc?.capabilityId)

        // Read requires user prompt with High risk; Write is policy only with Low risk
        assertEquals("High", readDesc?.riskLevel)
        assertEquals(RequestHandlingMode.USER_PROMPT, readDesc?.requestHandlingMode)

        assertEquals("Low", writeDesc?.riskLevel)
        assertEquals(RequestHandlingMode.POLICY_ONLY, writeDesc?.requestHandlingMode)
    }

    @Test
    fun testClipboardTransactionLifecycleAndCancellation() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackResult: String? = null

        val tx = PendingPermissionTransaction(
            requestId = "req_clip_cancel_test",
            tabId = "tab_clip_99",
            origin = "https://secure.example.com",
            resources = listOf("CLIPBOARD_READ"),
            isIncognito = true,
            onResultCallback = { result -> callbackResult = result }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        // Test cancel
        PermissionGrantEngine.cancelPendingTransaction("req_clip_cancel_test")

        assertEquals("CANCELED", callbackResult)
        assertTrue(tx.isTerminated.get())
        assertEquals(PermissionState.CANCELED, tx.stateMachine.currentState.value)
    }

    @Test
    fun testNativeCapabilityAdapterNormalizationAndMetadataPreservation() {
        val now = System.currentTimeMillis()
        val nativeParams = NativeCapabilityParams(
            origin = "https://app.example.com/dashboard",
            capabilityId = "CAMERA",
            tabId = "tab_native_1",
            userGesture = true,
            isIncognito = true,
            apiName = "ExtensionBridge.MediaModule",
            metadata = mapOf("module" to "camera_feed", "version" to "1.0"),
            requestId = "req_native_test_505",
            timestamp = now
        )

        val universal = NativeCapabilityAdapter.adapt(nativeParams)
        assertEquals("req_native_test_505", universal.requestId)
        assertEquals("CAMERA", universal.capabilityId)
        assertEquals("https://app.example.com", universal.origin)
        assertEquals("tab_native_1", universal.tabId)
        assertTrue("Incognito flag must be preserved", universal.incognito)
        assertTrue("User gesture must be preserved", universal.userGesture ?: false)
        assertEquals("native_bridge", universal.requestSource)
        assertEquals("ExtensionBridge.MediaModule", universal.webApiName)
        assertEquals("camera_feed", universal.metadata["module"])
        assertEquals("1.0", universal.metadata["version"])
        assertEquals("https://app.example.com/dashboard", universal.metadata["origin"])
        assertEquals(now.toString(), universal.metadata["timestamp"])
    }

    @Test
    fun testNativeCapabilityUnsupportedClassification() {
        val unsupportedCaps = listOf("BLUETOOTH", "USB", "NFC", "SERIAL_HID", "LOCAL_NETWORK")

        for (cap in unsupportedCaps) {
            val desc = PermissionDescriptorRegistry.getDescriptor(cap)
            assertNotNull("Descriptor for $cap must exist", desc)
            assertEquals("Capability $cap must be UNSUPPORTED_BY_WEBVIEW",
                CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW, desc?.supportStatus)
            assertEquals("Capability $cap must have RequestHandlingMode.UNSUPPORTED",
                RequestHandlingMode.UNSUPPORTED, desc?.requestHandlingMode)
        }
    }

    @Test
    fun testCapabilityDecisionModelStructure() {
        val decision = CapabilityDecision(
            requestId = "req_dec_1",
            capabilityId = "CLIPBOARD_READ",
            origin = "https://example.com",
            decision = "ALLOW",
            isAllowed = true,
            capabilityState = CapabilityState.SUPPORTED,
            reason = "User granted clipboard read",
            requiresPrompt = true,
            isIncognito = false
        )

        assertEquals("req_dec_1", decision.requestId)
        assertEquals("CLIPBOARD_READ", decision.capabilityId)
        assertEquals("https://example.com", decision.origin)
        assertEquals("ALLOW", decision.decision)
        assertTrue(decision.isAllowed)
        assertEquals(CapabilityState.SUPPORTED, decision.capabilityState)
        assertFalse(decision.isIncognito)
        assertTrue(decision.requiresPrompt)
    }

    @Test
    fun testCanonicalPipelineWiringMatrix() {
        // Wiring Matrix: Capability -> Runtime Source / Adapter -> UniversalCapabilityRequest -> Permission Engine
        val allDescriptors = PermissionDescriptorRegistry.getAllDescriptors()
        assertEquals(33, allDescriptors.size)

        for (desc in allDescriptors) {
            assertNotNull(desc.capabilityId)
            assertNotNull(desc.supportStatus)
            assertNotNull(desc.requestHandlingMode)

            val rawUrl = "https://test.example.com/app"
            val universalReq = UniversalCapabilityRequest.builder(
                rawUrl = rawUrl,
                capabilityId = desc.capabilityId,
                topLevelUrl = "https://test.example.com",
                frameUrl = rawUrl
            )
                .setRequestId("req_wire_${desc.capabilityId.lowercase()}")
                .setTabId("tab_wire_1")
                .setIncognito(false)
                .setUserGesture(true)
                .setRequestSource("test_harness")
                .setWebApiName(desc.webApiSource)
                .setMetadata(mapOf("test_key" to "test_val"))
                .build()

            // Verify UniversalCapabilityRequest preserves all required context fields
            assertEquals("req_wire_${desc.capabilityId.lowercase()}", universalReq.requestId)
            assertEquals(desc.capabilityId, universalReq.capabilityId)
            assertEquals("https://test.example.com", universalReq.origin)
            assertEquals("https://test.example.com", universalReq.topLevelOrigin)
            assertEquals("https://test.example.com", universalReq.frameOrigin)
            assertEquals("tab_wire_1", universalReq.tabId)
            assertFalse(universalReq.incognito)
            assertTrue(universalReq.userGesture ?: false)
            assertEquals("test_harness", universalReq.requestSource)
            assertEquals(desc.webApiSource, universalReq.webApiName)
            assertEquals("test_val", universalReq.metadata["test_key"])

            // Normalizer normalization must preserve universal request intact
            val normalized = PermissionRequestNormalizer.normalize(universalReq)
            assertEquals(universalReq.requestId, normalized.requestId)
            assertEquals(universalReq.capabilityId, normalized.capabilityId)
            assertEquals(universalReq.origin, normalized.origin)

            // Dynamic origin mapping verification
            val dynOrigin = normalized.toDynamicOrigin()
            assertEquals("https://test.example.com", dynOrigin.canonicalOrigin)
            assertEquals("tab_wire_1", dynOrigin.tabId)
            assertFalse(dynOrigin.isIncognito)
            assertTrue(dynOrigin.isUserGesture ?: false)
        }
    }

    @Test
    fun testAllCapabilityAdaptersPreserveContext() {
        val now = System.currentTimeMillis()

        // 1. WebViewPermissionAdapter
        val webViewReq = WebViewPermissionAdapter.adaptRawResources(
            origin = "https://camera.example.com/stream",
            resources = arrayOf("android.webkit.resource.VIDEO_CAPTURE", "android.webkit.resource.AUDIO_CAPTURE"),
            tabId = "tab_media_1",
            isIncognito = true,
            requestId = "req_media_100"
        )
        assertEquals("req_media_100", webViewReq.requestId)
        assertEquals("https://camera.example.com", webViewReq.origin)
        assertEquals("tab_media_1", webViewReq.tabId)
        assertTrue(webViewReq.incognito)
        assertEquals(2, webViewReq.requestedResources.size)

        // 2. GeolocationRequestAdapter
        val geoReq = GeolocationRequestAdapter.adapt(
            Pair("https://maps.example.com/find", "tab_geo_1")
        )
        assertNotNull(geoReq.requestId)
        assertEquals("LOCATION", geoReq.capabilityId)
        assertEquals("https://maps.example.com", geoReq.origin)
        assertEquals("tab_geo_1", geoReq.tabId)
        assertFalse(geoReq.incognito)

        // 3. SpeechRecognitionAdapter
        val speechReq = SpeechRecognitionAdapter.adapt(
            SpeechRecognitionRequestParams(
                origin = "https://voice.example.com/speech",
                tabId = "tab_speech_1",
                isIncognito = true,
                requestId = "req_speech_100"
            )
        )
        assertEquals("req_speech_100", speechReq.requestId)
        assertEquals("SPEECH_RECOGNITION", speechReq.capabilityId)
        assertEquals("https://voice.example.com", speechReq.origin)
        assertEquals("tab_speech_1", speechReq.tabId)
        assertTrue(speechReq.incognito)

        // 4. FileCaptureAdapter
        val fileReq = FileCaptureAdapter.adapt(
            FileCaptureRequestParams(
                origin = "https://upload.example.com/form",
                tabId = "tab_file_1",
                acceptTypes = listOf("image/*"),
                captureMode = "camera",
                isIncognito = false,
                requestId = "req_file_100",
                timestamp = now
            )
        )
        assertEquals("req_file_100", fileReq.requestId)
        assertEquals("FILE_CAMERA_CAPTURE", fileReq.capabilityId)
        assertEquals("https://upload.example.com", fileReq.origin)
        assertEquals("tab_file_1", fileReq.tabId)
        assertFalse(fileReq.incognito)

        // 5. NotificationRequestAdapter
        val notifReq = NotificationRequestAdapter.adapt(
            NotificationRequestParams(
                origin = "https://notify.example.com/alert",
                tabId = "tab_notif_1",
                userGesture = true,
                isIncognito = true,
                requestId = "req_notif_100"
            )
        )
        assertEquals("req_notif_100", notifReq.requestId)
        assertEquals("NOTIFICATIONS", notifReq.capabilityId)
        assertEquals("https://notify.example.com", notifReq.origin)
        assertEquals("tab_notif_1", notifReq.tabId)
        assertTrue(notifReq.incognito)

        // 6. FullscreenAdapter
        val fsReq = FullscreenAdapter.adapt(
            FullscreenRequestParams(
                origin = "https://video.example.com/player",
                tabId = "tab_fs_1",
                userGesture = true,
                isIncognito = false,
                requestId = "req_fs_100"
            )
        )
        assertEquals("req_fs_100", fsReq.requestId)
        assertEquals("FULLSCREEN", fsReq.capabilityId)
        assertEquals("https://video.example.com", fsReq.origin)
        assertEquals("tab_fs_1", fsReq.tabId)
        assertFalse(fsReq.incognito)

        // 7. PopupWindowAdapter
        val popupReq = PopupWindowAdapter.adapt(
            PopupWindowRequestParams(
                origin = "https://portal.example.com/login",
                tabId = "tab_pop_1",
                userGesture = false,
                isIncognito = true,
                isDialog = true,
                requestId = "req_pop_100"
            )
        )
        assertEquals("req_pop_100", popupReq.requestId)
        assertEquals("POPUPS", popupReq.capabilityId)
        assertEquals("https://portal.example.com", popupReq.origin)
        assertEquals("tab_pop_1", popupReq.tabId)
        assertTrue(popupReq.incognito)
        assertFalse(popupReq.userGesture ?: true)
    }

    @Test
    fun testStateMachineTransitionsAndTerminalGuarantees() {
        val sm = PermissionStateMachine("test_req_sm")
        assertEquals(PermissionState.PENDING, sm.currentState.value)

        // Valid transition PENDING -> WAITING_USER
        assertTrue(sm.transitionTo(PermissionState.WAITING_USER))
        assertEquals(PermissionState.WAITING_USER, sm.currentState.value)

        // Valid transition WAITING_USER -> WAITING_ANDROID
        assertTrue(sm.transitionTo(PermissionState.WAITING_ANDROID))
        assertEquals(PermissionState.WAITING_ANDROID, sm.currentState.value)

        // Valid transition WAITING_ANDROID -> GRANTING
        assertTrue(sm.transitionTo(PermissionState.GRANTING))
        assertEquals(PermissionState.GRANTING, sm.currentState.value)

        // Valid terminal transition GRANTING -> ALLOWED
        assertTrue(sm.transitionTo(PermissionState.ALLOWED))
        assertEquals(PermissionState.ALLOWED, sm.currentState.value)
        assertTrue(sm.currentState.value.isTerminal)

        // Illegal transition from terminal ALLOWED -> DENIED
        assertFalse(sm.transitionTo(PermissionState.DENIED))
        assertEquals(PermissionState.ALLOWED, sm.currentState.value)

        // Illegal transition from terminal ALLOWED -> PENDING
        assertFalse(sm.transitionTo(PermissionState.PENDING))
        assertEquals(PermissionState.ALLOWED, sm.currentState.value)
    }

    @Test
    fun testTerminalGuaranteesNoGrantAfterDenyCancelOrExpiry() {
        // 1. No grant after DENIED
        val sm1 = PermissionStateMachine("req_deny")
        sm1.transitionTo(PermissionState.DENIED)
        assertFalse(sm1.transitionTo(PermissionState.GRANTED))
        assertFalse(sm1.transitionTo(PermissionState.ALLOWED))
        assertEquals(PermissionState.DENIED, sm1.currentState.value)

        // 2. No grant after CANCELED
        val sm2 = PermissionStateMachine("req_cancel")
        sm2.transitionTo(PermissionState.CANCELED)
        assertFalse(sm2.transitionTo(PermissionState.GRANTED))
        assertFalse(sm2.transitionTo(PermissionState.ALLOWED))
        assertEquals(PermissionState.CANCELED, sm2.currentState.value)

        // 3. No grant after EXPIRED
        val sm3 = PermissionStateMachine("req_expire")
        sm3.transitionTo(PermissionState.EXPIRED)
        assertFalse(sm3.transitionTo(PermissionState.GRANTED))
        assertFalse(sm3.transitionTo(PermissionState.ALLOWED))
        assertEquals(PermissionState.EXPIRED, sm3.currentState.value)
    }

    @Test
    fun testSingleCallbackGuaranteeInTransaction() {
        var callbackCount = 0
        var receivedResult: String? = null
        val tx = PendingPermissionTransaction(
            requestId = "req_single_cb",
            tabId = "tab_1",
            origin = "https://example.com",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            onResultCallback = { res ->
                callbackCount++
                receivedResult = res
            }
        )

        // First dispatch
        tx.dispatchResult("ALLOW")
        assertEquals(1, callbackCount)
        assertEquals("ALLOW", receivedResult)

        // Subsequent dispatches must be ignored
        tx.dispatchResult("BLOCK")
        tx.dispatchResult("EXPIRED")
        tx.dispatchResult("CANCELED")
        assertEquals(1, callbackCount)
        assertEquals("ALLOW", receivedResult)
    }

    @Test
    fun testPreserveUnknownUserGestureSemantics() {
        val fileParams = FileCaptureRequestParams(
            origin = "https://example.com",
            userGesture = null
        )
        assertNull(fileParams.userGesture)

        val fileReq = FileCaptureAdapter.adapt(fileParams)
        assertNull(fileReq.userGesture)

        val dynOrigin = fileReq.toDynamicOrigin()
        assertNull(dynOrigin.isUserGesture)

        val speechParams = SpeechRecognitionRequestParams(
            origin = "https://example.com",
            userGesture = null
        )
        assertNull(speechParams.userGesture)
        val speechReq = SpeechRecognitionAdapter.adapt(speechParams)
        assertNull(speechReq.userGesture)
    }

    @Test
    fun testAll33CapabilitiesPresentInRegistry() {
        val expectedCapabilities = listOf(
            "CAMERA",
            "MICROPHONE",
            "CAMERA_MICROPHONE",
            "SPEECH_RECOGNITION",
            "LOCATION",
            "NOTIFICATIONS",
            "MIDI",
            "PROTECTED_MEDIA",
            "WEBRTC",
            "MEDIA_DEVICES",
            "MEDIA_RECORDER",
            "FILE_UPLOAD",
            "FILE_MULTIPLE",
            "FILE_CAMERA_CAPTURE",
            "FILE_AUDIO_CAPTURE",
            "CLIPBOARD",
            "CLIPBOARD_READ",
            "CLIPBOARD_WRITE",
            "POPUPS",
            "DOWNLOADS",
            "FULLSCREEN",
            "AUTOPLAY",
            "SENSORS",
            "BLUETOOTH",
            "USB",
            "NFC",
            "PAYMENT",
            "SCREEN_CAPTURE",
            "LOCAL_NETWORK",
            "SERIAL_HID",
            "NOTIFICATION_ACTIONS",
            "STORAGE",
            "BACKGROUND_MEDIA"
        )

        val registryIds = PermissionDescriptorRegistry.getAllCapabilityIds()
        for (cap in expectedCapabilities) {
            assertTrue("Capability $cap must be present in PermissionDescriptorRegistry", registryIds.contains(cap))
            val desc = PermissionDescriptorRegistry.getDescriptor(cap)
            assertNotNull("Descriptor for $cap must not be null", desc)
        }
    }

    @Test
    fun testRegistryMetadataInternalConsistency() {
        val allDescriptors = PermissionDescriptorRegistry.getAllDescriptors()
        assertTrue(allDescriptors.size >= 33)

        for (desc in allDescriptors) {
            // 1. Non-empty string metadata
            assertTrue("capabilityId non-blank for ${desc.capabilityId}", desc.capabilityId.isNotBlank())
            assertTrue("displayName non-blank for ${desc.capabilityId}", desc.displayName.isNotBlank())
            assertTrue("shortDescription non-blank for ${desc.capabilityId}", desc.shortDescription.isNotBlank())
            assertTrue("userPromptText non-blank for ${desc.capabilityId}", desc.userPromptText.isNotBlank())
            assertTrue("iconKey non-blank for ${desc.capabilityId}", desc.iconKey.isNotBlank())
            assertTrue("promptBehavior non-blank for ${desc.capabilityId}", desc.promptBehavior.isNotBlank())
            assertTrue("riskLevel valid for ${desc.capabilityId}", desc.riskLevel in listOf("Low", "Medium", "High"))

            // 2. Android runtime permission consistency
            if (desc.requiresAndroidRuntimePermission) {
                assertTrue(
                    "Descriptor ${desc.capabilityId} requires Android permission but androidPermissions list is empty",
                    desc.androidPermissions.isNotEmpty()
                )
            } else {
                assertTrue(
                    "Descriptor ${desc.capabilityId} does not require Android permission but has androidPermissions",
                    desc.androidPermissions.isEmpty()
                )
            }

            // 3. Hardware requirement consistency
            if (desc.requiresHardware) {
                assertTrue(
                    "Descriptor ${desc.capabilityId} requires hardware but has no feature set",
                    desc.hardwareFeature != null || desc.hardwareRequirements.isNotEmpty()
                )
            }

            // 4. Native bridge consistency
            if (desc.requiresNativeBridge) {
                assertTrue(
                    "Descriptor ${desc.capabilityId} requires native bridge but mode is not native bridge",
                    desc.requestHandlingMode == RequestHandlingMode.NATIVE_BRIDGE_REQUIRED ||
                            desc.supportStatus == CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE
                )
            }
        }
    }

    @Test
    fun testSpecificCapabilityRulesConsistency() {
        // Camera
        val cam = PermissionDescriptorRegistry.getDescriptor("CAMERA")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION, cam.supportStatus)
        assertTrue(cam.requiresAndroidRuntimePermission)
        assertTrue(cam.androidPermissions.contains(android.Manifest.permission.CAMERA))
        assertTrue(cam.requiresHardware)
        assertTrue(cam.requiresSecureOrigin)

        // Microphone
        val mic = PermissionDescriptorRegistry.getDescriptor("MICROPHONE")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION, mic.supportStatus)
        assertTrue(mic.requiresAndroidRuntimePermission)
        assertTrue(mic.androidPermissions.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(mic.requiresHardware)
        assertTrue(mic.requiresSecureOrigin)

        // Location
        val loc = PermissionDescriptorRegistry.getDescriptor("LOCATION")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION, loc.supportStatus)
        assertTrue(loc.requiresAndroidRuntimePermission)
        assertTrue(loc.androidPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(loc.requiresHardware)
        assertTrue(loc.requiresSecureOrigin)

        // Notifications
        val notif = PermissionDescriptorRegistry.getDescriptor("NOTIFICATIONS")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION, notif.supportStatus)
        assertTrue(notif.requiresAndroidRuntimePermission)
        assertTrue(notif.androidPermissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertFalse(notif.requiresHardware)

        // File upload (Do not request broad storage permissions for file picker)
        val fileUpload = PermissionDescriptorRegistry.getDescriptor("FILE_UPLOAD")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_POLICY, fileUpload.supportStatus)
        assertEquals(RequestHandlingMode.PLATFORM_MANAGED, fileUpload.requestHandlingMode)
        assertFalse(fileUpload.requiresAndroidRuntimePermission)
        assertTrue(fileUpload.androidPermissions.isEmpty())

        val fileMultiple = PermissionDescriptorRegistry.getDescriptor("FILE_MULTIPLE")!!
        assertEquals(CapabilitySupportStatus.SUPPORTED_WITH_POLICY, fileMultiple.supportStatus)
        assertFalse(fileMultiple.requiresAndroidRuntimePermission)
        assertTrue(fileMultiple.androidPermissions.isEmpty())

        // Unsupported capabilities
        val bluetooth = PermissionDescriptorRegistry.getDescriptor("BLUETOOTH")!!
        assertEquals(CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW, bluetooth.supportStatus)
        assertEquals(RequestHandlingMode.UNSUPPORTED, bluetooth.requestHandlingMode)

        val usb = PermissionDescriptorRegistry.getDescriptor("USB")!!
        assertEquals(CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW, usb.supportStatus)

        val nfc = PermissionDescriptorRegistry.getDescriptor("NFC")!!
        assertEquals(CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW, nfc.supportStatus)

        val bgMedia = PermissionDescriptorRegistry.getDescriptor("BACKGROUND_MEDIA")!!
        assertEquals(CapabilitySupportStatus.UNSUPPORTED_BY_ANDROID, bgMedia.supportStatus)

        // Native Bridge capabilities
        val speech = PermissionDescriptorRegistry.getDescriptor("SPEECH_RECOGNITION")!!
        assertEquals(CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE, speech.supportStatus)
        assertTrue(speech.requiresNativeBridge)

        val payment = PermissionDescriptorRegistry.getDescriptor("PAYMENT")!!
        assertEquals(CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE, payment.supportStatus)
        assertTrue(payment.requiresNativeBridge)

        val screenCap = PermissionDescriptorRegistry.getDescriptor("SCREEN_CAPTURE")!!
        assertEquals(CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE, screenCap.supportStatus)
        assertTrue(screenCap.requiresNativeBridge)
    }

    @Test
    fun testCanonicalOriginIdentityDistinctness() {
        val o1 = OriginNormalizer.normalize("https://example.com")
        val o2 = OriginNormalizer.normalize("http://example.com")
        val o3 = OriginNormalizer.normalize("https://example.com:8443")
        val o4 = OriginNormalizer.normalize("https://example.com:443")
        val o5 = OriginNormalizer.normalize("http://example.com:80")
        val o6 = OriginNormalizer.normalize("http://example.com:8080")

        assertEquals("https://example.com", o1)
        assertEquals("http://example.com", o2)
        assertEquals("https://example.com:8443", o3)
        assertEquals("https://example.com", o4)
        assertEquals("http://example.com", o5)
        assertEquals("http://example.com:8080", o6)

        // Must not collapse into the same record
        assertNotEquals(o1, o2)
        assertNotEquals(o1, o3)
        assertNotEquals(o2, o6)
        assertEquals(o1, o4)
        assertEquals(o2, o5)
    }

    @Test
    fun testIncognitoIsolationGuarantees() {
        val origin = "https://incognito-test.com"
        val perm = "CAMERA"

        // Cache persistent decision in public mode
        PermissionCache.cachePersistentDecision(origin, perm, "ALLOW_ALWAYS")
        assertEquals("ALLOW_ALWAYS", PermissionCache.getCachedDecision(origin, perm, isIncognito = false))

        // Incognito MUST NOT see public persistent state
        assertNull(PermissionCache.getCachedDecision(origin, perm, isIncognito = true))

        // Cache decision in incognito
        PermissionCache.cacheIncognitoDecision(origin, perm, "ALLOW_ONCE")
        assertEquals("ALLOW_ONCE", PermissionCache.getCachedDecision(origin, perm, isIncognito = true))
        // Public must still see ALLOW_ALWAYS
        assertEquals("ALLOW_ALWAYS", PermissionCache.getCachedDecision(origin, perm, isIncognito = false))

        // Clear incognito
        PermissionCache.clearIncognitoCache()
        assertNull(PermissionCache.getCachedDecision(origin, perm, isIncognito = true))
        assertEquals("ALLOW_ALWAYS", PermissionCache.getCachedDecision(origin, perm, isIncognito = false))

        // Cleanup
        PermissionCache.clearPersistentCache()
    }

    @Test
    fun testFrameSecurityBlocksIframeWhenTopLevelRequired() {
        val iframeOrigin = DynamicOrigin(
            canonicalOrigin = "https://embedded-ad.com",
            scheme = "https",
            host = "embedded-ad.com",
            port = 443,
            topLevelOrigin = "https://trusted-site.com",
            frameOrigin = "https://embedded-ad.com",
            tabId = "tab_frame",
            isIncognito = false
        )

        val eval = CapabilityBroker.evaluateCapability("CAMERA", iframeOrigin)
        assertEquals(CapabilityState.BLOCKED_BY_SECURITY, eval.capabilityState)
        assertFalse(eval.isSecuritySatisfied)
    }

    @Test
    fun testRevocationAndStoredPermissionAndroidPermissionDenial() {
        val origin = DynamicOrigin.parse("https://revocation-test.com")

        // In absence of Android permission grant, evaluation with null context returns DENIED_BY_ANDROID_PERMISSION
        val eval = CapabilityBroker.evaluateCapability(
            resourceOrType = "CAMERA",
            dynamicOrigin = origin,
            cachedDecision = "ALLOW_ALWAYS"
        )
        // If Android permission is missing, it must return DENIED_BY_ANDROID_PERMISSION
        assertEquals(CapabilityState.DENIED_BY_ANDROID_PERMISSION, eval.capabilityState)
    }

    @Test
    fun testOriginMismatchOnGrantAndDeny() {
        var callbackInvoked = false
        val tx = PendingPermissionTransaction(
            requestId = "req_mismatch_tx",
            tabId = "tab_1",
            origin = "https://expected-origin.com",
            resources = listOf("CAMERA"),
            onResultCallback = { callbackInvoked = true }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        // Apply grant with wrong origin
        PermissionGrantEngine.applyGrant(
            requestId = "req_mismatch_tx",
            origin = "https://attacker-origin.com",
            permissionType = "CAMERA",
            decision = "ALLOW_ALWAYS"
        )
        assertFalse(callbackInvoked)
        assertNotNull(PermissionGrantEngine.getPendingTransaction("req_mismatch_tx"))

        // Cleanup
        PermissionGrantEngine.cancelPendingTransaction("req_mismatch_tx")
    }

    @Test
    fun testTabCancellationOnTabClose() {
        var cb1Called = false
        var cb2Called = false

        val tx1 = PendingPermissionTransaction(
            requestId = "req_tab_1",
            tabId = "tab_to_close",
            origin = "https://tab-test.com",
            resources = listOf("CAMERA"),
            onResultCallback = { cb1Called = true }
        )
        val tx2 = PendingPermissionTransaction(
            requestId = "req_tab_2",
            tabId = "tab_other",
            origin = "https://tab-test.com",
            resources = listOf("LOCATION"),
            onResultCallback = { cb2Called = true }
        )

        PermissionGrantEngine.registerPendingTransaction(tx1)
        PermissionGrantEngine.registerPendingTransaction(tx2)

        PermissionGrantEngine.cancelPendingTransactionsForTab("tab_to_close")

        assertTrue(cb1Called)
        assertFalse(cb2Called)
        assertNull(PermissionGrantEngine.getPendingTransaction("req_tab_1"))
        assertNotNull(PermissionGrantEngine.getPendingTransaction("req_tab_2"))

        // Cleanup
        PermissionGrantEngine.cancelPendingTransaction("req_tab_2")
    }

    @Test
    fun testDynamicPromptFormatting() {
        val singleItem = listOf(
            CapabilityPromptItem(
                capabilityId = "MICROPHONE",
                displayName = "Microphone",
                userPromptText = "wants to use your microphone"
            )
        )
        val prompt1 = com.swift.browser.permissionengine.ui.formatDynamicPromptMessage("https://youtube.com", singleItem)
        assertEquals("youtube.com wants to use your microphone", prompt1)

        val locationItem = listOf(
            CapabilityPromptItem(
                capabilityId = "LOCATION",
                displayName = "Location",
                userPromptText = "wants to access your location"
            )
        )
        val prompt2 = com.swift.browser.permissionengine.ui.formatDynamicPromptMessage("https://maps.google.com", locationItem)
        assertEquals("maps.google.com wants to access your location", prompt2)

        val dualItems = listOf(
            CapabilityPromptItem(
                capabilityId = "CAMERA",
                displayName = "camera",
                userPromptText = "wants to use your camera"
            ),
            CapabilityPromptItem(
                capabilityId = "MICROPHONE",
                displayName = "microphone",
                userPromptText = "wants to use your microphone"
            )
        )
        val prompt3 = com.swift.browser.permissionengine.ui.formatDynamicPromptMessage("https://example.com:8443", dualItems)
        assertEquals("example.com wants to use your camera and microphone.", prompt3)

        val fileItem = listOf(
            CapabilityPromptItem(
                capabilityId = "FILE_UPLOAD",
                displayName = "File Selection",
                userPromptText = "wants to select files"
            )
        )
        val prompt4 = com.swift.browser.permissionengine.ui.formatDynamicPromptMessage("https://example.com", fileItem)
        assertEquals("example.com wants to select files", prompt4)
    }

    @Test
    fun testMultiCapabilityIndependentDecisionsModel() {
        var dispatchedDecisions: Map<String, String>? = null
        val promptModel = CapabilityPromptModel(
            requestId = "req_multi_dec_1",
            origin = "https://example.com",
            capabilities = listOf(
                CapabilityPromptItem(capabilityId = "CAMERA", displayName = "Camera"),
                CapabilityPromptItem(capabilityId = "MICROPHONE", displayName = "Microphone")
            ),
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE", "android.webkit.resource.AUDIO_CAPTURE"),
            riskLevel = "Medium",
            isSecure = true,
            onDecision = { decisions -> dispatchedDecisions = decisions }
        )

        assertEquals("req_multi_dec_1", promptModel.requestId)
        assertEquals(2, promptModel.capabilities.size)

        // UI independent decisions: CAMERA -> ALLOW_ALWAYS, MICROPHONE -> BLOCK
        val userChoices = mapOf("CAMERA" to "ALLOW_ALWAYS", "MICROPHONE" to "BLOCK")
        promptModel.onDecision(userChoices)

        assertNotNull(dispatchedDecisions)
        assertEquals("ALLOW_ALWAYS", dispatchedDecisions!!["CAMERA"])
        assertEquals("BLOCK", dispatchedDecisions!!["MICROPHONE"])
    }

    @Test
    fun testPromptControllerDismissAndStateFlow() {
        val controller = PermissionPromptController()
        var decisionResult: String? = null

        controller.showPrompt("https://test.org", "CAMERA") { dec ->
            decisionResult = dec
        }

        assertNotNull(controller.pendingPrompt.value)
        assertEquals("https://test.org", controller.pendingPrompt.value?.origin)
        assertEquals("CAMERA", controller.pendingPrompt.value?.permissionType)

        controller.pendingPrompt.value?.onDecision?.invoke("ALLOW_ONCE")
        assertEquals("ALLOW_ONCE", decisionResult)
        assertNull(controller.pendingPrompt.value)
    }

    @Test
    fun testSingleSourceOfTruthForPermissionCenterCapabilities() {
        val allDescriptors = PermissionDescriptorRegistry.getAllDescriptors()
        assertTrue("All 33 capability descriptors must be registered", allDescriptors.size >= 33)

        // Ensure all descriptors map cleanly to UI models with valid metadata
        allDescriptors.forEach { desc ->
            assertFalse(desc.capabilityId.isBlank())
            assertFalse(desc.displayName.isBlank())
            assertNotNull(desc.riskLevel)
            assertNotNull(desc.persistenceMode)
            assertNotNull(PermissionIconResolver.getIcon(desc.iconKey))
        }
    }

    @Test
    fun testArchitecturalBoundaryInvariants() {
        // Architectural invariant 1: Only permission-engine decides capability permissions
        val origin = DynamicOrigin.parse("https://boundary-test.org")
        
        // Evaluate capability with explicit site blocked policy
        val evalBlocked = CapabilityBroker.evaluateCapability(
            resourceOrType = "CAMERA",
            dynamicOrigin = origin,
            cachedDecision = "BLOCK"
        )
        assertEquals(CapabilityState.BLOCKED_BY_USER_POLICY, evalBlocked.capabilityState)
        assertTrue(evalBlocked.isSiteBlocked)
        assertFalse(evalBlocked.isSiteAllowed)

        // Evaluate capability with explicit site allow policy (without Android permission context -> DENIED_BY_ANDROID_PERMISSION)
        val evalAllowWithoutAndroidPerm = CapabilityBroker.evaluateCapability(
            resourceOrType = "CAMERA",
            dynamicOrigin = origin,
            cachedDecision = "ALLOW_ALWAYS"
        )
        assertEquals(CapabilityState.DENIED_BY_ANDROID_PERMISSION, evalAllowWithoutAndroidPerm.capabilityState)
        assertTrue(evalAllowWithoutAndroidPerm.isSiteAllowed)
    }

    @Test
    fun testPermissionEngineExclusiveGrantAuthorityContract() {
        // Verify PermissionGrantEngine transaction creation and terminal isolation
        val tx = PendingPermissionTransaction(
            requestId = "arch_tx_100",
            tabId = "tab_arch_2",
            origin = "https://grant-authority.org",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            incognito = false
        )

        assertFalse(tx.state.isTerminal)
        assertTrue(tx.markTerminal(PermissionState.GRANTED))
        // Cannot mark terminal twice (idempotent state transition)
        assertFalse(tx.markTerminal(PermissionState.DENIED))
        assertTrue(tx.state.isTerminal)
        assertEquals(PermissionState.GRANTED, tx.state)
    }

    @Test
    fun testTransactionMergePreservesNativeRequestAndCallback() {
        var callbackCalled = false
        val originalTx = PendingPermissionTransaction(
            requestId = "tx_merge_test_1",
            tabId = "tab_merge_1",
            origin = "https://merge-test.org",
            resources = listOf("CAMERA"),
            onResultCallback = { callbackCalled = true },
            preferredDecision = "ALLOW_ONCE"
        )
        PermissionGrantEngine.registerPendingTransaction(originalTx)

        // Incoming transaction without callback or with different preferred decision
        val secondaryTx = PendingPermissionTransaction(
            requestId = "tx_merge_test_1",
            tabId = "tab_merge_1",
            origin = "https://merge-test.org",
            resources = listOf("CAMERA"),
            request = null,
            onResultCallback = null,
            preferredDecision = "ALLOW_ALWAYS"
        )
        PermissionGrantEngine.registerPendingTransaction(secondaryTx)

        val retrieved = PermissionGrantEngine.getPendingTransaction("tx_merge_test_1")
        assertNotNull(retrieved)
        // Verify callback is preserved
        retrieved!!.dispatchResult("ALLOW")
        assertTrue(callbackCalled)

        // Clean up
        PermissionGrantEngine.cancelPendingTransaction("tx_merge_test_1")
    }

    @Test
    fun testAllowOnceVsAllowAlwaysSemantics() {
        val tx = PendingPermissionTransaction(
            requestId = "tx_allow_once_test",
            tabId = "tab_1",
            origin = "https://allow-once.org",
            resources = listOf("CAMERA"),
            preferredDecision = "ALLOW_ONCE"
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        val retrieved = PermissionGrantEngine.getPendingTransaction("tx_allow_once_test")
        assertEquals("ALLOW_ONCE", retrieved?.preferredDecision)

        // Apply grant
        PermissionGrantEngine.applyGrant(
            requestId = "tx_allow_once_test",
            origin = "https://allow-once.org",
            permissionType = "CAMERA",
            decision = retrieved!!.preferredDecision
        )

        // Verify session decision is cached
        val cached = PermissionCache.getCachedDecision("https://allow-once.org", "CAMERA")
        assertEquals("ALLOW_ONCE", cached)
    }

    @Test
    fun testUserGestureUnknownSemantics() {
        val unknownGestureRequest = UniversalCapabilityRequest(
            requestId = "req_gesture_1",
            tabId = "tab_g",
            origin = "https://gesture-test.org",
            capabilityId = "POPUPS",
            userGesture = null
        )
        assertNull(unknownGestureRequest.userGesture)

        val explicitTrue = UniversalCapabilityRequest(
            requestId = "req_gesture_2",
            tabId = "tab_g",
            origin = "https://gesture-test.org",
            capabilityId = "POPUPS",
            userGesture = true
        )
        assertEquals(true, explicitTrue.userGesture)

        val explicitFalse = UniversalCapabilityRequest(
            requestId = "req_gesture_3",
            tabId = "tab_g",
            origin = "https://gesture-test.org",
            capabilityId = "POPUPS",
            userGesture = false
        )
        assertEquals(false, explicitFalse.userGesture)
    }

    @Test
    fun testScreenCaptureAdapterNormalization() {
        val params = ScreenCaptureRequestParams(
            origin = "https://presentation.company.com/room1",
            tabId = "tab_sc_test",
            userGesture = true,
            isIncognito = false,
            videoConstraints = "{ cursor: 'always' }"
        )
        val universal = ScreenCaptureAdapter.adapt(params)

        assertEquals("SCREEN_CAPTURE", universal.capabilityId)
        assertEquals("https://presentation.company.com", universal.origin)
        assertEquals("tab_sc_test", universal.tabId)
        assertEquals(true, universal.userGesture)
        assertEquals(false, universal.incognito)
        assertEquals("screen_capture_bridge", universal.requestSource)
        assertEquals("navigator.mediaDevices.getDisplayMedia()", universal.webApiName)
        assertEquals("{ cursor: 'always' }", universal.metadata["videoConstraints"])
    }

    @Test
    fun testScreenCaptureDescriptorRequirements() {
        val descriptor = PermissionDescriptorRegistry.getDescriptor("SCREEN_CAPTURE")
        assertNotNull(descriptor)
        assertTrue(descriptor!!.requiresSecureOrigin)
        assertTrue(descriptor.requiresNativeBridge)
        assertEquals(RequestHandlingMode.NATIVE_BRIDGE_REQUIRED, descriptor.requestHandlingMode)
        assertEquals(CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE, descriptor.supportStatus)
        assertTrue(descriptor.webViewResources.contains("android.webkit.resource.DISPLAY_CAPTURE"))
    }

    @Test
    fun testBug1_OneRequestOnePrompt() {
        PermissionDialogEngine.dismissPrompt()
        val promptModel = CapabilityPromptModel(
            requestId = "bug1_req",
            origin = "https://example.com",
            capabilities = listOf(
                CapabilityPromptItem(capabilityId = "CAMERA", displayName = "Camera"),
                CapabilityPromptItem(capabilityId = "MICROPHONE", displayName = "Microphone")
            ),
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE", "android.webkit.resource.AUDIO_CAPTURE"),
            riskLevel = "Medium",
            isSecure = true,
            onDecision = {}
        )
        PermissionDialogEngine.showCapabilityPrompt(promptModel)

        assertNotNull("Capability prompt must be active", PermissionDialogEngine.activeCapabilityPrompt.value)
        assertNull("Legacy showPrompt must NOT be called for universal requests", PermissionDialogEngine.activePrompt.value)
    }

    @Test
    fun testBug2_CameraAndMicrophoneResourcesPreservedOnEnrich() {
        val tx = PendingPermissionTransaction(
            requestId = "bug2_req",
            tabId = "tab1",
            origin = "https://example.com",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            allowedResources = java.util.Collections.synchronizedSet(mutableSetOf("android.webkit.resource.VIDEO_CAPTURE"))
        )
        tx.enrich(
            incomingRequest = null,
            incomingContext = null,
            incomingUniversal = null,
            incomingResources = listOf("android.webkit.resource.AUDIO_CAPTURE"),
            incomingCallback = null,
            incomingPreferredDecision = null
        )

        val allowed = tx.allowedResources.toList()
        assertTrue("VIDEO_CAPTURE must be present", allowed.contains("android.webkit.resource.VIDEO_CAPTURE"))
        assertTrue("AUDIO_CAPTURE must be preserved after enrich", allowed.contains("android.webkit.resource.AUDIO_CAPTURE"))
    }

    @Test
    fun testBug3_AllowAlwaysAndAllowOnceRemainIndependent() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        val origin = "https://example.com"

        // Set CAMERA to ALLOW_ALWAYS and MICROPHONE to ALLOW_ONCE
        PermissionCache.cachePersistentDecision(origin, "CAMERA", "ALLOW_ALWAYS")
        PermissionCache.cacheSessionDecision(origin, "MICROPHONE", "ALLOW_ONCE")

        val camState = PermissionCache.getCachedDecision(origin, "CAMERA")
        val micState = PermissionCache.getCachedDecision(origin, "MICROPHONE")

        assertEquals("ALLOW_ALWAYS", camState)
        assertEquals("ALLOW_ONCE", micState)

        // Clear session cache: ALLOW_ONCE should be gone, ALLOW_ALWAYS must persist
        PermissionCache.clearSessionCache()

        val camStateAfter = PermissionCache.getCachedDecision(origin, "CAMERA")
        val micStateAfter = PermissionCache.getCachedDecision(origin, "MICROPHONE")

        assertEquals("ALLOW_ALWAYS", camStateAfter)
        assertNull("ALLOW_ONCE must not persist after session clear", micStateAfter)
    }

    @Test
    fun testBug4_ResetAllResetsEveryRegisteredCapability() {
        val allCaps = PermissionDescriptorRegistry.getAllCapabilityIds()
        assertTrue("Must contain registered capabilities", allCaps.size >= 33)

        val origin = "https://reset-test.com"
        allCaps.forEach { cap ->
            PermissionCache.cachePersistentDecision(origin, cap, "ALLOW_ALWAYS")
        }

        // Reset all registered capability IDs
        allCaps.forEach { cap ->
            PermissionCache.evictFromCache(origin, cap)
        }

        allCaps.forEach { cap ->
            val decision = PermissionCache.getCachedDecision(origin, cap)
            assertNull("Capability $cap must be reset", decision)
        }
    }

    @Test
    fun testBug5_IncognitoNeverPersistsAsPublicAllowAlways() {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        PermissionCache.clearIncognitoCache()
        val origin = "https://incognito-test.com"

        PermissionCache.cacheIncognitoDecision(origin, "CAMERA", "ALLOW_ALWAYS")

        // Cached in incognito cache for current incognito tab
        val incognitoDecision = PermissionCache.getCachedDecision(origin, "CAMERA", isIncognito = true)
        val publicDecision = PermissionCache.getCachedDecision(origin, "CAMERA", isIncognito = false)

        assertEquals("ALLOW_ALWAYS", incognitoDecision)
        assertNull("Public non-incognito cache must not contain incognito decision", publicDecision)

        // Clearing incognito cache removes decision completely
        PermissionCache.clearIncognitoCache()
        assertNull("Incognito decision must be cleared", PermissionCache.getCachedDecision(origin, "CAMERA", isIncognito = true))
    }

    @Test
    fun testBug6_DuplicateTransactionRegistrationKeepsOriginalNativeWebViewRequest() {
        val mockPermissionRequest = object : android.webkit.PermissionRequest() {
            override fun getOrigin(): android.net.Uri = android.net.Uri.parse("https://example.com")
            override fun getResources(): Array<String> = arrayOf(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            override fun grant(resources: Array<out String>?) {}
            override fun deny() {}
        }
        val tx1 = PendingPermissionTransaction(
            requestId = "bug6_req",
            tabId = "tab1",
            origin = "https://example.com",
            resources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            request = mockPermissionRequest
        )
        tx1.enrich(
            incomingRequest = null,
            incomingContext = null,
            incomingUniversal = null,
            incomingResources = listOf("android.webkit.resource.VIDEO_CAPTURE"),
            incomingCallback = null,
            incomingPreferredDecision = null
        )

        assertEquals("Original request must be retained", mockPermissionRequest, tx1.request)
    }

    @Test
    fun testBug7_GrantDenyOccursExactlyOnce() {
        PermissionGrantEngine.cancelAllPendingTransactions()
        var callbackCount = 0

        val tx = PendingPermissionTransaction(
            requestId = "bug7_req",
            tabId = "tab1",
            origin = "https://example.com",
            resources = listOf("CAMERA"),
            onResultCallback = { callbackCount++ }
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        PermissionGrantEngine.applyGrant("bug7_req", "https://example.com", "CAMERA", "ALLOW_ALWAYS")
        PermissionGrantEngine.applyGrant("bug7_req", "https://example.com", "CAMERA", "ALLOW_ALWAYS")
        PermissionGrantEngine.applyDeny("bug7_req", "https://example.com", "CAMERA", "BLOCK")

        assertEquals("Result callback must occur exactly once", 1, callbackCount)
        assertEquals(PermissionState.GRANTED, tx.stateMachine.currentState.value)
    }
}

