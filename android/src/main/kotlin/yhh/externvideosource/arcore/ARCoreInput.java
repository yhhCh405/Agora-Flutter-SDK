package yhh.externvideosource.arcore;

import android.util.Size;
import android.view.Surface;

import yhh.externvideosource.GLThreadContext;
import yhh.externvideosource.IExternalVideoInput;


public class ARCoreInput implements IExternalVideoInput {
    @Override
    public void onVideoInitialized(Surface target) {

    }

    @Override
    public void onVideoStopped(GLThreadContext context) {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void onFrameAvailable(GLThreadContext context, int textureId, float[] transform) {

    }

    @Override
    public Size onGetFrameSize() {
        return null;
    }

    @Override
    public int timeToWait() {
        return 0;
    }
}
