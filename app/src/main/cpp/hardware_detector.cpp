#include "hardware_detector.hpp"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>

#include <sched.h>
#include <unistd.h>

namespace hw {

namespace {

int page_size() {
    static const long ps = sysconf(_SC_PAGESIZE);
    return ps > 0 ? static_cast<int>(ps) : 4096;
}

std::vector<int> possible_cpus() {
    std::vector<int> out;
    std::ifstream f("/sys/devices/system/cpu/possible");
    std::string line;
    if (std::getline(f, line)) {
        // Format: "0-7" or "0-3,4-7"
        std::istringstream ss(line);
        std::string part;
        while (std::getline(ss, part, ',')) {
            int lo = 0, hi = 0;
            if (std::sscanf(part.c_str(), "%d-%d", &lo, &hi) == 2) {
                for (int c = lo; c <= hi; ++c) out.push_back(c);
            } else if (std::sscanf(part.c_str(), "%d", &lo) == 1) {
                out.push_back(lo);
            }
        }
    }
    return out;
}

}  // namespace

int cpuCount() {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    return n > 0 ? static_cast<int>(n) : 0;
}

std::vector<int> highCores() {
    const std::vector<int> all = possible_cpus();
    if (all.empty()) {
        return {};
    }
    // Highest half, ascending (e.g. {4,5,6,7} for 0-7).
    const int half = static_cast<int>(all.size()) / 2;
    if (half <= 0) {
        return {};
    }
    return std::vector<int>(all.end() - half, all.end());
}

uint64_t rssBytes() {
    // /proc/self/statm: size resident shared text lib data dt (pages).
    std::ifstream f("/proc/self/statm");
    unsigned long size = 0, resident = 0;
    if (f >> size >> resident) {
        return static_cast<uint64_t>(resident) * static_cast<uint64_t>(page_size());
    }
    return 0;
}

bool pinCurrentThread(const std::vector<int>& cores) {
    if (cores.empty()) {
        return false;
    }
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int c : cores) {
        CPU_SET(c, &set);
    }
    return sched_setaffinity(0, sizeof(set), &set) == 0;
}

std::string summaryJson() {
    const std::vector<int> cores = highCores();
    std::string cores_json = "[";
    for (size_t i = 0; i < cores.size(); ++i) {
        if (i > 0) cores_json += ",";
        cores_json += std::to_string(cores[i]);
    }
    cores_json += "]";
    char buf[192];
    std::snprintf(buf, sizeof(buf),
                  "{\"cpu_count\":%d,\"pin_cores\":%s,\"rss_bytes\":%llu}",
                  cpuCount(), cores_json.c_str(),
                  static_cast<unsigned long long>(rssBytes()));
    return buf;
}

}  // namespace hw
