package yhh.externvideosource;

import android.opengl.EGLContext;

import yhh.component.gles.ProgramTextureOES;
import yhh.component.gles.core.EglCore;


public class GLThreadContext {
    public EglCore eglCore;
    public EGLContext context;
    public ProgramTextureOES program;
}
