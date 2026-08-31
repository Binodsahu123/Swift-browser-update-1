(function() {
    try {
        var pageUrl = window.location.href;
        var reported = {};

        function checkResource(entry) {
            var url = entry.name;
            if (!url || url.substring(0, 4) !== 'http' || reported[url]) return;

            var lowerUrl = url.split('?')[0].split('#')[0].toLowerCase();
            var isMedia = false;
            var type = 'video';
            var mime = 'application/octet-stream';

            if (lowerUrl.endsWith('.m3u8')) {
                isMedia = true;
                type = 'playlist';
                mime = 'application/x-mpegURL';
            } else if (lowerUrl.endsWith('.mpd')) {
                isMedia = true;
                type = 'playlist';
                mime = 'application/dash+xml';
            } else if (lowerUrl.endsWith('.mp4') || lowerUrl.indexOf('/videoplayback') >= 0 || lowerUrl.indexOf('.m4s') >= 0) {
                isMedia = true;
                type = 'video';
                mime = 'video/mp4';
            } else if (lowerUrl.endsWith('.mp3') || lowerUrl.endsWith('.m4a') || lowerUrl.endsWith('.aac') || lowerUrl.endsWith('.wav')) {
                isMedia = true;
                type = 'audio';
                mime = 'audio/mpeg';
            } else if (lowerUrl.endsWith('.pdf')) {
                isMedia = true;
                type = 'document';
                mime = 'application/pdf';
            } else if (lowerUrl.endsWith('.zip') || lowerUrl.endsWith('.rar')) {
                isMedia = true;
                type = 'archive';
                mime = 'application/zip';
            }

            if (isMedia) {
                reported[url] = true;
                var candidates = [{
                    url: url,
                    title: document.title + " (" + type.toUpperCase() + ")",
                    type: type,
                    mimeType: mime,
                    sourcePage: pageUrl,
                    quality: 'Network Stream',
                    sourceElement: 'performance_entry',
                    confidence: 75
                }];

                if (window.SwiftMediaBridge && window.SwiftMediaBridge.onCandidatesDetected) {
                    window.SwiftMediaBridge.onCandidatesDetected(JSON.stringify(candidates));
                }
            }
        }

        // 1. Scan existing loaded resources
        var resources = performance.getEntriesByType('resource');
        for (var i = 0; i < resources.length; i++) {
            checkResource(resources[i]);
        }

        // 2. Observe dynamic fetch requests using PerformanceObserver
        if (window.PerformanceObserver) {
            var observer = new PerformanceObserver(function(list) {
                var entries = list.getEntries();
                for (var i = 0; i < entries.length; i++) {
                    checkResource(entries[i]);
                }
            });
            observer.observe({ entryTypes: ['resource'] });
        }
    } catch(e) {
        console.error("Swift Network scanning failed: " + e.message);
    }
})();
