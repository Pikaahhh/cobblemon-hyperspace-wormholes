package dev.hyperspacewormholes.wormholes.client;

/**
 * A curve through given points (x, y, x, y, ... in order of x) that has no corners and never goes past a point before it
 * turns: the monotone cubic of Fritsch and Butland, flat at both ends and beyond them.
 */
public final class SmoothCurve {
    private final float[] x, y, slope;

    public SmoothCurve(float... points) {
        int n = points.length / 2;
        x = new float[n];
        y = new float[n];
        slope = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = points[2 * i];
            y[i] = points[2 * i + 1];
        }
        for (int i = 1; i < n - 1; i++) {
            float before = x[i] - x[i - 1], after = x[i + 1] - x[i];
            float in = (y[i] - y[i - 1]) / before, out = (y[i + 1] - y[i]) / after;
            if (in * out <= 0) continue;
            float a = 2 * after + before, b = after + 2 * before;
            slope[i] = (a + b) / (a / in + b / out);
        }
    }

    public float at(float v) {
        int last = x.length - 1;
        if (v <= x[0]) return y[0];
        if (v >= x[last]) return y[last];
        int i = 0;
        while (v > x[i + 1]) i++;
        float wide = x[i + 1] - x[i], t = (v - x[i]) / wide, u = 1 - t;
        return u * u * ((1 + 2 * t) * y[i] + t * wide * slope[i]) + t * t * ((3 - 2 * t) * y[i + 1] - u * wide * slope[i + 1]);
    }
}
