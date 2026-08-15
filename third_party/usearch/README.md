# USearch (vendored, pinned)

Header-only HNSW vector index, Apache-2.0, upstream https://github.com/unum-cloud/usearch.

- Version: v2.11.3 (C++17; v3.x requires C++20, out of scope for the NDK r27 build)
- Vendored headers: `include/usearch/index.hpp`, `index_plugins.hpp`, `index_dense.hpp`
  from the `v2.11.3` tag (commit `v2.11.3`).
- On ARM the plugin disables FP16-lib and simsimd paths (`USEARCH_USE_FP16LIB=0`,
  `USEARCH_USE_SIMSIMD=0`), so only the three headers are required; no
  submodule sources are vendored.
- Usage: compiled into the dedicated `vector-lib` shared library (JNI). It is
  loaded lazily by `USearchVectorIndex` and reports unavailable when the
  platform cannot load it (honest capability reporting).
