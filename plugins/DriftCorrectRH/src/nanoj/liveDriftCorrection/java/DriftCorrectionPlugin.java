package nanoj.liveDriftCorrection.java;

import org.micromanager.internal.MMStudio;
import org.scijava.plugin.SciJavaPlugin;
//import org.micromanager.api.MMPlugin;
//import org.micromanager.api.ScriptInterface;

public class DriftCorrectionPlugin implements SciJavaPlugin {
    static DriftCorrectionGUI driftGui = DriftCorrectionGUI.INSTANCE;

    public static final String menuName = "NanoJ Online Drift Correction";
    public static final String tooltipDescription = "Asynchronous Image-Correlation-based 3D drift correction.";
    public static final String version = "0.5";
    public static final String copyright = "Copyright University College London, 2017";

    public void dispose() {}

    /* commented out because ScriptInterface is deprecated in 2.0 -kw 190226
    public void setApp(ScriptInterface app) {
        driftGui.setApp((MMStudio) app);
    }*/

    public void show() {
        driftGui.initiateThreads();
        driftGui.show();
    }

    public String getDescription() { return tooltipDescription; }

    public String getInfo() { return tooltipDescription; }

    public String getVersion() { return version; }

    public String getCopyright() { return copyright; }
}
