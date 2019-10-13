package rk.or.android;

import android.opengl.GLES20;
import android.util.Log;

class Check {

    public static void checkCompile(int shader) {
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Throwable t = new Throwable();
            Log.e("ORISIM", "Error : " + GLES20.glGetShaderInfoLog(shader), t);
        }
    }

    public static void checkLink(int program) {
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Throwable t = new Throwable();
            Log.e("ORISIM", "Error : " + GLES20.glGetProgramInfoLog(program), t);
        }
    }

    public static void checkError() {
        // GL_INVALID_OPERATION = 0x0502
        // GL_INVALID_VALUE = 0x0501;
        // GL_INVALID_ENUM = 0x0500;
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Throwable t = new Throwable();
            Log.e("ORISIM", "GL error: " + String.format("0x%04X", error), t);
        }
    }
}