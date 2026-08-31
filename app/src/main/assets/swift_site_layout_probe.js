// Swift Site Layout Structure Probe
(function() {
    try {
        var sidebarElements = document.querySelectorAll('aside, .sidebar, #sidebar, .navigation, nav');
        var gridContainers = document.querySelectorAll('.grid, .container-fluid, [class*="col-md"], [class*="col-lg"]');
        
        var structuralMetrics = {
            hasSidebar: sidebarElements.length > 0,
            sidebarCount: sidebarElements.length,
            hasWideGrids: gridContainers.length > 0,
            gridCount: gridContainers.length,
            timestamp: Date.now()
        };

        if (window.SwiftDeveloperEngine) {
            window.SwiftDeveloperEngine.onSiteStructureProbed(JSON.stringify(structuralMetrics));
        }
        return structuralMetrics;
    } catch(e) {
        console.error("Swift Site Layout Structure Probe Error: " + e.message);
    }
})();
