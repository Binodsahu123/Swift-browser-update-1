// Swift Desktop Mode Layout Probe
(function() {
    try {
        var viewport = document.querySelector('meta[name=viewport]');
        var viewportContent = viewport ? viewport.getAttribute('content') : 'none';
        var bodyWidth = document.body ? document.body.clientWidth : 0;
        var screenWidth = window.screen ? window.screen.width : 0;
        var devicePixelRatio = window.devicePixelRatio || 1;

        var diagnosticInfo = {
            viewportContent: viewportContent,
            bodyWidth: bodyWidth,
            screenWidth: screenWidth,
            devicePixelRatio: devicePixelRatio,
            isCompliant: bodyWidth >= 1000
        };

        if (window.SwiftDeveloperEngine) {
            window.SwiftDeveloperEngine.onDesktopLayoutProbed(JSON.stringify(diagnosticInfo));
        }
        return diagnosticInfo;
    } catch (e) {
        console.error("Swift Desktop Layout Probe Error: " + e.message);
    }
})();
