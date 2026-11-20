package org.bbnors.motion;

import java.util.Arrays;
import java.util.Locale;

/**
 * Struct-of-arrays buffer of sampled motion states. Reusable: {@link MotionSampler} grows the
 * arrays only when needed, so sampling many trajectories into the same buffer does not allocate
 * after warm-up.
 */
public final class TrajectorySamples {

    public double[] t = new double[0];
    public double[] x = new double[0];
    public double[] y = new double[0];
    public double[] vx = new double[0];
    public double[] vy = new double[0];
    public double[] speed = new double[0];
    public double[] accTangential = new double[0];
    public double[] accNormal = new double[0];
    public double[] acc = new double[0];
    public int[] segment = new int[0];
    private int size;

    public int size() {
        return size;
    }

    void ensureCapacity(int n) {
        if (t.length < n) {
            int cap = Math.max(n, t.length + (t.length >> 1));
            t = Arrays.copyOf(t, cap);
            x = Arrays.copyOf(x, cap);
            y = Arrays.copyOf(y, cap);
            vx = Arrays.copyOf(vx, cap);
            vy = Arrays.copyOf(vy, cap);
            speed = Arrays.copyOf(speed, cap);
            accTangential = Arrays.copyOf(accTangential, cap);
            accNormal = Arrays.copyOf(accNormal, cap);
            acc = Arrays.copyOf(acc, cap);
            segment = Arrays.copyOf(segment, cap);
        }
    }

    void setSize(int n) {
        size = n;
    }

    void put(int i, MotionState s) {
        t[i] = s.t;
        x[i] = s.x;
        y[i] = s.y;
        vx[i] = s.vx;
        vy[i] = s.vy;
        speed[i] = s.speed;
        accTangential[i] = s.accTangential;
        accNormal[i] = s.accNormal;
        acc[i] = s.accMagnitude();
        segment[i] = s.segment;
    }

    /** CSV with header; one row per sample. */
    public String toCsv() {
        StringBuilder sb = new StringBuilder(size * 96 + 128);
        sb.append("t_s,x_px,y_px,vx_px_s,vy_px_s,speed_px_s,acc_tan_px_s2,acc_norm_px_s2,acc_px_s2,segment\n");
        for (int i = 0; i < size; i++) {
            sb.append(String.format(Locale.ROOT, "%.6f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d%n",
                    t[i], x[i], y[i], vx[i], vy[i], speed[i], accTangential[i], accNormal[i], acc[i], segment[i]));
        }
        return sb.toString();
    }
}
