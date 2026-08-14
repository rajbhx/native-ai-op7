// Hardware topology + memory watchdog for the OnePlus 7 profile (blueprint
// Phase 2). Everything here is detected/measured — never fabricated. CPU
// affinity is applied only when explicitly enabled (never blindly).
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace hw {

// Number of online CPUs.
int cpuCount();

// Highest half of the available core ids (e.g. cores 4-7 on an 8-core OP7).
std::vector<int> highCores();

// Resident set size in bytes from /proc/self/statm (page count * page size).
uint64_t rssBytes();

// Pin the calling thread to the given cores. Returns true when applied.
bool pinCurrentThread(const std::vector<int>& cores);

// Honest summary: cpu count, pin candidate cores, current RSS.
std::string summaryJson();

}  // namespace hw
