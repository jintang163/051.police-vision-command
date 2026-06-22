package com.police.vision.control.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class SarimaMathUtils {

    private SarimaMathUtils() {
    }

    public static double[] diff(double[] data, int d) {
        if (d <= 0) {
            return Arrays.copyOf(data, data.length);
        }
        double[] result = data;
        for (int i = 0; i < d; i++) {
            result = diffOnce(result);
        }
        return result;
    }

    private static double[] diffOnce(double[] data) {
        if (data.length < 2) {
            return new double[0];
        }
        double[] result = new double[data.length - 1];
        for (int i = 1; i < data.length; i++) {
            result[i - 1] = data[i] - data[i - 1];
        }
        return result;
    }

    public static double[] inverseDiff(double[] diffed, double[] originals, int d) {
        if (d <= 0) {
            return Arrays.copyOf(diffed, diffed.length);
        }
        double[] result = diffed;
        double[] currentOriginals = originals;
        for (int i = 0; i < d; i++) {
            int order = d - i;
            double[] initValues = new double[order];
            int idx = originals.length - 1;
            for (int j = order - 1; j >= 0 && idx >= 0; j--) {
                initValues[j] = originals[idx];
                idx--;
            }
            if (idx >= 0) {
                currentOriginals = Arrays.copyOfRange(originals, 0, originals.length - (d - order));
            }
            result = inverseDiffOnce(result, initValues);
        }
        return result;
    }

    private static double[] inverseDiffOnce(double[] diffed, double[] initValues) {
        double[] result = new double[diffed.length + 1];
        if (initValues.length > 0) {
            result[0] = initValues[initValues.length - 1];
        } else {
            result[0] = 0;
        }
        for (int i = 0; i < diffed.length; i++) {
            result[i + 1] = result[i] + diffed[i];
        }
        return result;
    }

    public static double autocorr(double[] data, int lag) {
        if (lag >= data.length || lag < 0) {
            return 0.0;
        }
        double mean = mean(data);
        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < data.length; i++) {
            double dev = data[i] - mean;
            den += dev * dev;
            if (i >= lag) {
                num += dev * (data[i - lag] - mean);
            }
        }
        if (den == 0.0) {
            return 0.0;
        }
        return num / den;
    }

    public static double pacf(double[] data, int lag) {
        if (lag <= 0 || lag >= data.length) {
            return lag == 0 ? 1.0 : 0.0;
        }
        double[] pacf = durbinLevinson(data, lag);
        return pacf[lag];
    }

    private static double[] durbinLevinson(double[] data, int maxLag) {
        double[] r = new double[maxLag + 1];
        for (int k = 0; k <= maxLag; k++) {
            r[k] = autocorr(data, k);
        }
        double[][] phi = new double[maxLag + 1][maxLag + 1];
        phi[0][0] = 1.0;
        phi[1][1] = r[1] / (r[0] != 0 ? r[0] : 1.0);
        for (int n = 2; n <= maxLag; n++) {
            double numSum = 0.0;
            double denSum = 0.0;
            for (int k = 1; k < n; k++) {
                numSum += phi[n - 1][k] * r[n - k];
                denSum += phi[n - 1][k] * r[k];
            }
            double denom = 1.0 - denSum;
            if (Math.abs(denom) < 1e-10) {
                phi[n][n] = 0.0;
            } else {
                phi[n][n] = (r[n] - numSum) / denom;
            }
            for (int k = 1; k < n; k++) {
                phi[n][k] = phi[n - 1][k] - phi[n][n] * phi[n - 1][n - k];
            }
        }
        double[] pacf = new double[maxLag + 1];
        pacf[0] = 1.0;
        for (int i = 1; i <= maxLag; i++) {
            pacf[i] = phi[i][i];
        }
        return pacf;
    }

    public static double[] arFit(double[] data, int p) {
        if (p <= 0) {
            return new double[0];
        }
        int n = data.length;
        if (n <= p) {
            double[] coeffs = new double[p];
            Arrays.fill(coeffs, 0.0);
            return coeffs;
        }
        double mean = mean(data);
        double[] centered = new double[n];
        for (int i = 0; i < n; i++) {
            centered[i] = data[i] - mean;
        }
        double[] r = new double[p + 1];
        for (int k = 0; k <= p; k++) {
            double sum = 0.0;
            for (int i = k; i < n; i++) {
                sum += centered[i] * centered[i - k];
            }
            r[k] = sum / n;
        }
        double[][] R = new double[p][p];
        double[] B = new double[p];
        for (int i = 0; i < p; i++) {
            B[i] = r[i + 1];
            for (int j = 0; j < p; j++) {
                R[i][j] = r[Math.abs(i - j)];
            }
        }
        return solveLinearSystem(R, B);
    }

    private static double[] solveLinearSystem(double[][] A, double[] B) {
        int n = A.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = B[i];
        }
        for (int col = 0; col < n; col++) {
            int pivotRow = col;
            double maxVal = Math.abs(aug[col][col]);
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > maxVal) {
                    maxVal = Math.abs(aug[row][col]);
                    pivotRow = row;
                }
            }
            if (pivotRow != col) {
                double[] temp = aug[col];
                aug[col] = aug[pivotRow];
                aug[pivotRow] = temp;
            }
            double pivot = aug[col][col];
            if (Math.abs(pivot) < 1e-12) {
                double[] result = new double[n];
                Arrays.fill(result, 0.0);
                return result;
            }
            for (int j = col; j <= n; j++) {
                aug[col][j] /= pivot;
            }
            for (int row = 0; row < n; row++) {
                if (row != col && Math.abs(aug[row][col]) > 1e-12) {
                    double factor = aug[row][col];
                    for (int j = col; j <= n; j++) {
                        aug[row][j] -= factor * aug[col][j];
                    }
                }
            }
        }
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = aug[i][n];
        }
        return result;
    }

    public static double[] maFit(double[] residuals, int q) {
        if (q <= 0) {
            return new double[0];
        }
        int n = residuals.length;
        if (n <= q + 1) {
            double[] coeffs = new double[q];
            Arrays.fill(coeffs, 0.0);
            return coeffs;
        }
        double[] autoCorr = new double[q + 1];
        for (int k = 0; k <= q; k++) {
            double num = 0.0;
            double den = 0.0;
            double mean = mean(residuals);
            for (int i = 0; i < n; i++) {
                double dev = residuals[i] - mean;
                den += dev * dev;
                if (i >= k) {
                    num += dev * (residuals[i - k] - mean);
                }
            }
            autoCorr[k] = den != 0 ? num / den : 0.0;
        }
        double[] ma = new double[q];
        double[] tempTheta = new double[q];
        int maxIter = 50;
        double tol = 1e-6;
        for (int iter = 0; iter < maxIter; iter++) {
            double[] innov = new double[n];
            Arrays.fill(innov, 0.0);
            double sigma2 = 0.0;
            for (int t = 0; t < n; t++) {
                double pred = 0.0;
                for (int k = 0; k < q && (t - 1 - k) >= 0; k++) {
                    pred += tempTheta[k] * innov[t - 1 - k];
                }
                innov[t] = residuals[t] - pred;
                sigma2 += innov[t] * innov[t];
            }
            sigma2 /= n;
            double[] newTheta = new double[q];
            for (int k = 1; k <= q; k++) {
                double num = 0.0;
                for (int t = k; t < n; t++) {
                    num += innov[t] * innov[t - k];
                }
                num /= n;
                double denom = (1.0 + dotProduct(tempTheta, tempTheta)) * sigma2;
                if (Math.abs(denom) < 1e-12) {
                    newTheta[k - 1] = 0.0;
                } else {
                    newTheta[k - 1] = num / denom;
                }
                newTheta[k - 1] = Math.max(-0.99, Math.min(0.99, newTheta[k - 1]));
            }
            double maxDiff = 0.0;
            for (int k = 0; k < q; k++) {
                maxDiff = Math.max(maxDiff, Math.abs(newTheta[k] - tempTheta[k]));
                tempTheta[k] = newTheta[k];
            }
            if (maxDiff < tol) {
                break;
            }
        }
        System.arraycopy(tempTheta, 0, ma, 0, q);
        return ma;
    }

    private static double dotProduct(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static double[] sarimaForecast(double[] history, int p, int d, int q,
                                           int P, int D, int Q, int s, int steps) {
        if (steps <= 0) {
            return new double[0];
        }
        if (history == null || history.length == 0) {
            double[] result = new double[steps];
            Arrays.fill(result, 0.0);
            return result;
        }
        try {
            int minRequired = Math.max(p + d + q + 1, P * s + D * s + Q * s + 1);
            if (history.length < minRequired) {
                return ewmaForecast(history, steps, 0.3);
            }
            double[] processed = Arrays.copyOf(history, history.length);
            for (int i = 0; i < D; i++) {
                processed = seasonalDiff(processed, s);
            }
            for (int i = 0; i < d; i++) {
                processed = diffOnce(processed);
            }
            if (processed.length < Math.max(p + q + 1, P * s + Q * s + 1)) {
                return ewmaForecast(history, steps, 0.3);
            }
            double[] arCoeffs = new double[0];
            double[] seasonalArCoeffs = new double[0];
            double[] maCoeffs = new double[0];
            double[] seasonalMaCoeffs = new double[0];
            if (p > 0) {
                arCoeffs = arFit(processed, p);
            }
            if (P > 0 && processed.length > P * s) {
                seasonalArCoeffs = arFit(processed, Math.min(P, processed.length / s - 1));
            }
            double[] residuals = computeResiduals(processed, arCoeffs, seasonalArCoeffs, p, P, s);
            if (q > 0 && residuals.length > q + 1) {
                maCoeffs = maFit(residuals, Math.min(q, residuals.length / 3));
            }
            if (Q > 0 && residuals.length > Q * s + 1) {
                seasonalMaCoeffs = maFit(residuals, Math.min(Q, residuals.length / (s + 1)));
            }
            double meanProc = mean(processed);
            double[] centered = new double[processed.length];
            for (int i = 0; i < processed.length; i++) {
                centered[i] = processed[i] - meanProc;
            }
            double[] extended = Arrays.copyOf(centered, centered.length + steps);
            double[] extResiduals = Arrays.copyOf(residuals, Math.max(residuals.length, centered.length + steps));
            if (extResiduals.length < centered.length + steps) {
                extResiduals = Arrays.copyOf(extResiduals, centered.length + steps);
                Arrays.fill(extResiduals, residuals.length, extResiduals.length, 0.0);
            }
            for (int t = centered.length; t < extended.length; t++) {
                double arPart = 0.0;
                for (int k = 1; k <= p && t - k >= 0; k++) {
                    if (k - 1 < arCoeffs.length) {
                        arPart += arCoeffs[k - 1] * extended[t - k];
                    }
                }
                double sarPart = 0.0;
                for (int k = 1; k <= P && t - k * s >= 0; k++) {
                    if (k - 1 < seasonalArCoeffs.length) {
                        sarPart += seasonalArCoeffs[k - 1] * extended[t - k * s];
                    }
                }
                double sarArPart = 0.0;
                for (int k1 = 1; k1 <= p && k1 - 1 < arCoeffs.length; k1++) {
                    for (int k2 = 1; k2 <= P && k2 - 1 < seasonalArCoeffs.length; k2++) {
                        int idx = t - k1 - k2 * s;
                        if (idx >= 0) {
                            sarArPart += -arCoeffs[k1 - 1] * seasonalArCoeffs[k2 - 1] * extended[idx];
                        }
                    }
                }
                double maPart = 0.0;
                for (int k = 1; k <= q && t - k >= 0; k++) {
                    if (k - 1 < maCoeffs.length && t - k < extResiduals.length) {
                        maPart += maCoeffs[k - 1] * extResiduals[t - k];
                    }
                }
                double smaPart = 0.0;
                for (int k = 1; k <= Q && t - k * s >= 0; k++) {
                    if (k - 1 < seasonalMaCoeffs.length && t - k * s < extResiduals.length) {
                        smaPart += seasonalMaCoeffs[k - 1] * extResiduals[t - k * s];
                    }
                }
                double smaMaPart = 0.0;
                for (int k1 = 1; k1 <= q && k1 - 1 < maCoeffs.length; k1++) {
                    for (int k2 = 1; k2 <= Q && k2 - 1 < seasonalMaCoeffs.length; k2++) {
                        int idx = t - k1 - k2 * s;
                        if (idx >= 0 && idx < extResiduals.length) {
                            smaMaPart += maCoeffs[k1 - 1] * seasonalMaCoeffs[k2 - 1] * extResiduals[idx];
                        }
                    }
                }
                extended[t] = arPart + sarPart + sarArPart + maPart + smaPart + smaMaPart;
                extResiduals[t] = 0.0;
            }
            double[] diffedForecast = new double[steps];
            for (int i = 0; i < steps; i++) {
                diffedForecast[i] = extended[centered.length + i] + meanProc;
            }
            double[] result = diffedForecast;
            for (int i = 0; i < d; i++) {
                double lastVal = history[history.length - 1 - i];
                double[] temp = new double[result.length + 1];
                temp[0] = lastVal;
                for (int j = 0; j < result.length; j++) {
                    temp[j + 1] = temp[j] + result[j];
                }
                result = Arrays.copyOfRange(temp, 1, temp.length);
            }
            for (int i = 0; i < D; i++) {
                double[] seasonalValues = new double[s];
                for (int k = 0; k < s; k++) {
                    int idx = history.length - s + k - i * s;
                    if (idx >= 0) {
                        seasonalValues[k] = history[idx];
                    }
                }
                double[] temp = new double[result.length];
                for (int j = 0; j < result.length; j++) {
                    double seasonalBase = seasonalValues[j % s];
                    temp[j] = result[j] + seasonalBase;
                }
                result = temp;
            }
            for (int i = 0; i < result.length; i++) {
                if (result[i] < 0) {
                    result[i] = 0.0;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("SARIMA预测失败，回退到EWMA：{}", e.getMessage());
            return ewmaForecast(history, steps, 0.3);
        }
    }

    private static double[] seasonalDiff(double[] data, int s) {
        if (s <= 0 || data.length <= s) {
            return Arrays.copyOf(data, Math.max(0, data.length - s));
        }
        double[] result = new double[data.length - s];
        for (int i = s; i < data.length; i++) {
            result[i - s] = data[i] - data[i - s];
        }
        return result;
    }

    private static double[] computeResiduals(double[] data, double[] arCoeffs,
                                              double[] seasonalArCoeffs, int p, int P, int s) {
        int n = data.length;
        double[] residuals = new double[n];
        double mean = mean(data);
        for (int t = 0; t < n; t++) {
            double pred = mean;
            for (int k = 1; k <= p && t - k >= 0; k++) {
                if (k - 1 < arCoeffs.length) {
                    pred += arCoeffs[k - 1] * (data[t - k] - mean);
                }
            }
            for (int k = 1; k <= P && t - k * s >= 0; k++) {
                if (k - 1 < seasonalArCoeffs.length) {
                    pred += seasonalArCoeffs[k - 1] * (data[t - k * s] - mean);
                }
            }
            residuals[t] = (data[t] - mean) - (pred - mean);
        }
        return residuals;
    }

    public static double[] ewmaForecast(double[] history, int steps, double alpha) {
        if (history == null || history.length == 0) {
            double[] result = new double[steps];
            Arrays.fill(result, 0.0);
            return result;
        }
        if (alpha <= 0.0) alpha = 0.3;
        if (alpha >= 1.0) alpha = 0.3;
        double[] ewma = new double[history.length];
        ewma[0] = history[0];
        for (int i = 1; i < history.length; i++) {
            ewma[i] = alpha * history[i] + (1 - alpha) * ewma[i - 1];
        }
        double level = ewma[history.length - 1];
        if (history.length >= 2) {
            double prevLevel = ewma[history.length - 2];
            double trend = alpha * (level - prevLevel) + (1 - alpha) * 0.0;
            double[] result = new double[steps];
            for (int i = 0; i < steps; i++) {
                result[i] = Math.max(0.0, level + (i + 1) * trend);
            }
            return result;
        }
        double[] result = new double[steps];
        Arrays.fill(result, Math.max(0.0, level));
        return result;
    }

    public static double aic(double[] residuals, int k) {
        int n = residuals.length;
        if (n <= k) {
            return Double.MAX_VALUE;
        }
        double rss = 0.0;
        for (double r : residuals) {
            rss += r * r;
        }
        if (rss <= 0) {
            rss = 1e-10;
        }
        double sigma2 = rss / n;
        return 2 * k + n * Math.log(sigma2);
    }

    private static double mean(double[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : data) {
            sum += v;
        }
        return sum / data.length;
    }

    public static double[] selectSarimaParams(double[] data, int s) {
        int bestP = 1, bestD = 0, bestQ = 1;
        int bestP_seasonal = 1, bestD_seasonal = 0, bestQ_seasonal = 1;
        double bestAIC = Double.MAX_VALUE;
        int maxP = 3, maxD = 1, maxQ = 3;
        int maxP_seasonal = 2, maxD_seasonal = 1, maxQ_seasonal = 2;
        if (data.length < 2 * s + 10) {
            maxP_seasonal = Math.min(1, maxP_seasonal);
            maxQ_seasonal = Math.min(1, maxQ_seasonal);
            maxD_seasonal = 0;
        }
        if (data.length < 30) {
            maxP = 2;
            maxQ = 2;
        }
        if (data.length < 15) {
            maxP = 1;
            maxD = 0;
            maxQ = 1;
            maxP_seasonal = 0;
            maxQ_seasonal = 0;
        }
        for (int p = 0; p <= maxP; p++) {
            for (int d = 0; d <= maxD; d++) {
                for (int q = 0; q <= maxQ; q++) {
                    for (int P_s = 0; P_s <= maxP_seasonal; P_s++) {
                        for (int D_s = 0; D_s <= maxD_seasonal; D_s++) {
                            for (int Q_s = 0; Q_s <= maxQ_seasonal; Q_s++) {
                                if (p == 0 && q == 0 && P_s == 0 && Q_s == 0) {
                                    continue;
                                }
                                try {
                                    int k = p + q + P_s + Q_s;
                                    int testSteps = Math.min(5, data.length / 10);
                                    if (testSteps < 1) testSteps = 1;
                                    int trainLen = Math.max(data.length - testSteps,
                                            p + d + q + P_s * s + D_s * s + Q_s * s + 5);
                                    if (trainLen < 10 || trainLen >= data.length) {
                                        continue;
                                    }
                                    double[] trainData = Arrays.copyOfRange(data, 0, trainLen);
                                    double[] forecast = sarimaForecast(trainData, p, d, q, P_s, D_s, Q_s, s, testSteps);
                                    double[] residuals = new double[testSteps];
                                    int validCount = 0;
                                    for (int i = 0; i < testSteps && trainLen + i < data.length; i++) {
                                        residuals[i] = data[trainLen + i] - forecast[i];
                                        validCount++;
                                    }
                                    if (validCount < 1 || k <= 0) continue;
                                    double[] validResiduals = Arrays.copyOf(residuals, validCount);
                                    double aicVal = aic(validResiduals, k);
                                    if (aicVal < bestAIC) {
                                        bestAIC = aicVal;
                                        bestP = p;
                                        bestD = d;
                                        bestQ = q;
                                        bestP_seasonal = P_s;
                                        bestD_seasonal = D_s;
                                        bestQ_seasonal = Q_s;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }
        return new double[]{bestP, bestD, bestQ, bestP_seasonal, bestD_seasonal, bestQ_seasonal, s, bestAIC};
    }
}
