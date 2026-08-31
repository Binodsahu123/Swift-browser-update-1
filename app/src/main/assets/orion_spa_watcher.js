(function() {
    try {
        var lastUrl = window.location.href;
        
        // 1. Monitor URL changes (SPA route shifts)
        setInterval(function() {
            var currentUrl = window.location.href;
            if (currentUrl !== lastUrl) {
                lastUrl = currentUrl;
                console.log("SPA Route navigation detected, initiating Swift re-scan...");
                if (window.SwiftMediaBridge && window.SwiftMediaBridge.onRouteChanged) {
                    console.log("SPA Route navigation detected.");
                }
            }
        }, 1000);

        // 2. Observe large DOM additions (lazy loads, scroll-based media feeds)
        var scanTimeout = null;
        var observer = new MutationObserver(function(mutations) {
            var triggerScan = false;
            for (var i = 0; i < mutations.length; i++) {
                var addedNodes = mutations[i].addedNodes;
                for (var j = 0; j < addedNodes.length; j++) {
                    var node = addedNodes[j];
                    if (node.nodeType === 1) { // ELEMENT_NODE
                        var tag = node.tagName.toLowerCase();
                        if (tag === 'video' || tag === 'audio' || tag === 'source' || tag === 'iframe') {
                            triggerScan = true;
                            break;
                        }
                        if (node.querySelector && node.querySelector('video, audio, source, iframe')) {
                            triggerScan = true;
                            break;
                        }
                    }
                }
                if (triggerScan) break;
            }

            if (triggerScan) {
                if (scanTimeout) clearTimeout(scanTimeout);
                scanTimeout = setTimeout(function() {
                    console.log("Dynamic media additions observed in DOM. Re-triggering scans...");
                    if (window.SwiftMediaBridge && window.SwiftMediaBridge.triggerDomScan) {
                        window.SwiftMediaBridge.triggerDomScan();
                    }
                }, 1500); // Wait 1.5s to batch updates
            }
        });

        observer.observe(document.body, { childList: true, subtree: true });
    } catch(e) {
        console.error("Swift SPA observer injection failed: " + e.message);
    }
})();
