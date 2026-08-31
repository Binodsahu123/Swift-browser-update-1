(function() {
    try {
        var candidates = [];
        var pageUrl = window.location.href;
        var pageTitle = document.title || 'Webpage';

        // Helper to guess MIME type
        function guessMimeType(url) {
            var cleanUrl = url.split('?')[0].split('#')[0].toLowerCase();
            if (cleanUrl.endsWith('.mp4')) return 'video/mp4';
            if (cleanUrl.endsWith('.m3u8')) return 'application/x-mpegURL';
            if (cleanUrl.endsWith('.mpd')) return 'application/dash+xml';
            if (cleanUrl.endsWith('.mp3')) return 'audio/mpeg';
            if (cleanUrl.endsWith('.webm')) return 'video/webm';
            if (cleanUrl.endsWith('.mkv')) return 'video/x-matroska';
            if (cleanUrl.endsWith('.pdf')) return 'application/pdf';
            if (cleanUrl.endsWith('.zip')) return 'application/zip';
            if (cleanUrl.endsWith('.rar')) return 'application/vnd.rar';
            if (cleanUrl.endsWith('.apk')) return 'application/vnd.android.package-archive';
            if (cleanUrl.endsWith('.png')) return 'image/png';
            if (cleanUrl.endsWith('.jpg') || cleanUrl.endsWith('.jpeg')) return 'image/jpeg';
            if (cleanUrl.endsWith('.webp')) return 'image/webp';
            if (cleanUrl.endsWith('.gif')) return 'image/gif';
            return 'application/octet-stream';
        }

        // Helper to extract file name
        function getFileName(url) {
            var parts = url.split('?')[0].split('#')[0].split('/');
            var name = parts[parts.length - 1];
            return name ? decodeURIComponent(name) : 'download';
        }

        // 1. Scan Video Elements
        var videos = document.getElementsByTagName('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            var src = v.src || v.currentSrc;
            if (src && src.substring(0, 4) === 'http') {
                candidates.push({
                    url: src,
                    title: pageTitle + " (Video)",
                    type: 'video',
                    mimeType: guessMimeType(src),
                    sourcePage: pageUrl,
                    quality: v.videoWidth ? (v.videoWidth + 'x' + v.videoHeight) : 'Auto',
                    sourceElement: 'video',
                    confidence: 95
                });
            }
            // Check nested source elements
            var sources = v.getElementsByTagName('source');
            for (var j = 0; j < sources.length; j++) {
                var s = sources[j];
                if (s.src && s.src.substring(0, 4) === 'http') {
                    candidates.push({
                        url: s.src,
                        title: pageTitle + " (Source Video)",
                        type: 'video',
                        mimeType: s.type || guessMimeType(s.src),
                        sourcePage: pageUrl,
                        quality: 'Auto',
                        sourceElement: 'source',
                        confidence: 90
                    });
                }
            }
        }

        // 2. Scan Audio Elements
        var audios = document.getElementsByTagName('audio');
        for (var i = 0; i < audios.length; i++) {
            var a = audios[i];
            var src = a.src || a.currentSrc;
            if (src && src.substring(0, 4) === 'http') {
                candidates.push({
                    url: src,
                    title: pageTitle + " (Audio)",
                    type: 'audio',
                    mimeType: guessMimeType(src),
                    sourcePage: pageUrl,
                    quality: 'Original',
                    sourceElement: 'audio',
                    confidence: 95
                });
            }
        }

        // 3. Scan Anchor Links pointing to direct download formats
        var anchors = document.getElementsByTagName('a');
        var mediaExtensions = ['.mp4', '.mkv', '.webm', '.mp3', '.m4a', '.wav', '.pdf', '.zip', '.rar', '.apk', '.jpg', '.png', '.webp'];
        for (var i = 0; i < anchors.length; i++) {
            var href = anchors[i].href;
            if (href && href.substring(0, 4) === 'http') {
                var cleanHref = href.split('?')[0].split('#')[0].toLowerCase();
                for (var j = 0; j < mediaExtensions.length; j++) {
                    if (cleanHref.endsWith(mediaExtensions[j])) {
                        candidates.push({
                            url: href,
                            title: (anchors[i].innerText || anchors[i].title || getFileName(href)).trim(),
                            type: mediaExtensions[j] === '.pdf' ? 'document' : (mediaExtensions[j] === '.zip' || mediaExtensions[j] === '.rar') ? 'archive' : (mediaExtensions[j] === '.jpg' || mediaExtensions[j] === '.png' || mediaExtensions[j] === '.webp') ? 'image' : 'video',
                            mimeType: guessMimeType(href),
                            sourcePage: pageUrl,
                            quality: 'Direct File',
                            sourceElement: 'a_anchor',
                            confidence: 85
                        });
                        break;
                    }
                }
            }
        }

        // 4. Scan Meta tags for Video/Audio pointers
        var metas = document.getElementsByTagName('meta');
        for (var i = 0; i < metas.length; i++) {
            var prop = metas[i].getAttribute('property') || metas[i].getAttribute('name');
            var val = metas[i].getAttribute('content');
            if (prop && val && val.substring(0, 4) === 'http') {
                if (prop === 'og:video' || prop === 'og:video:url' || prop === 'og:video:secure_url') {
                    candidates.push({
                        url: val,
                        title: pageTitle + " (Metadata Video)",
                        type: 'video',
                        mimeType: guessMimeType(val),
                        sourcePage: pageUrl,
                        quality: 'Metadata',
                        sourceElement: 'meta_tag',
                        confidence: 80
                    });
                } else if (prop === 'og:audio' || prop === 'og:audio:secure_url') {
                    candidates.push({
                        url: val,
                        title: pageTitle + " (Metadata Audio)",
                        type: 'audio',
                        mimeType: guessMimeType(val),
                        sourcePage: pageUrl,
                        quality: 'Metadata',
                        sourceElement: 'meta_tag',
                        confidence: 80
                    });
                }
            }
        }

        // Send back to Swift bridge if available
        if (window.SwiftMediaBridge && window.SwiftMediaBridge.onCandidatesDetected) {
            window.SwiftMediaBridge.onCandidatesDetected(JSON.stringify(candidates));
        } else {
            console.log("SwiftMediaBridge not available yet. Found: " + candidates.length + " media objects.");
        }
    } catch(e) {
        console.error("Swift DOM media scanning failed: " + e.message);
    }
})();
