# Third-party notices (native-ai-op7)

Binaries bundled into the APK by the CI build:

| Component | Version | License | Where |
|---|---|---|---|
| MNN (Alibaba) | 3.6.1 | Apache-2.0 | `lib/arm64-v8a/libMNN.so` (downloaded at build time from the MNN GitHub release) |

## MNN — Apache License 2.0

Copyright Alibaba Group Holding Limited.

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.

- Upstream: https://github.com/alibaba/MNN
- Our usage: S6 runtime probe only. The bundled library is present so the
  runtime can be probed honestly; no MNN model inference ships until an
  on-device benchmark gate validates it (no fake capabilities).
