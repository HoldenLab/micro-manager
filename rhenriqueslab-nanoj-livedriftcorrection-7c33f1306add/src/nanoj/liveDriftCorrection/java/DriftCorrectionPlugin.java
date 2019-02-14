package nanoj.liveDriftCorrection.java;

import org.micromanager.MMStudio;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class DriftCorrectionPlugin implements MMPlugin{
    static DriftCorrectionGUI driftGui = DriftCorrectionGUI.INSTANCE;

    public static final String menuName = "NanoJ Online Drift Correction";
    public static final String tooltipDescription = "Asynchronous Image-Correlation-based 3D drift correction.";
    public static final String version = "0.5";
    public static final String copyright = "Copyright University College London, 2017";

    @Override
    public void dispose() {}

    @Override
    public void setApp(ScriptInterface app) {
        driftGui.setApp((MMStudio) app);
    }

    @Override
    public void show() {
        driftGui.initiateThreads();
        driftGui.show();
    }

    @Override
    public String getDescription() { return tooltipDescription; }

    @Override
    public String getInfo() { return tooltipDescription; }

    @Override
    public String getVersion() { return version; }

    @Override
    public String getCopyright() { return copyright; }
}
