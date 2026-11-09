package com.savio.tvlauncher;

import java.io.BufferedReader;
import java.io.FileReader;

// Whole-TV readings an ordinary app is allowed to see. Each returns -1 / null when Android denies the read, so the UI hides the chip.
// CPU usage % is not among them: /proc/stat and the cpuinfo service are blocked for apps by SELinux, with or without permissions.
class Stats {
    // Average current and maximum clock of the CPU cores in MHz: {current, max}, or null if unreadable.
    int[] cpuClock() {
        long cur = 0, max = 0;
        int n = 0;
        for (int i = 0; i < 8; i++) {
            long c = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            long m = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (c > 0 && m > 0) {
                cur += c;
                max += m;
                n++;
            }
        }
        return n == 0 ? null : new int[]{(int) (cur / n / 1000), (int) (max / n / 1000)};
    }

    // Swap in use, 0-100 percent of swap space.
    int swapPercent() {
        long total = -1, free = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (l.startsWith("SwapTotal:")) total = kb(l);
                else if (l.startsWith("SwapFree:")) free = kb(l);
            }
        } catch (Exception e) {
            return -1;
        }
        return total <= 0 || free < 0 ? -1 : (int) Math.round(100.0 * (total - free) / total);
    }

    private static long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return Long.parseLong(r.readLine().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static long kb(String line) {
        return Long.parseLong(line.replaceAll("\\D+", ""));
    }
}
