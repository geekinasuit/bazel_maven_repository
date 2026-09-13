/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package kramer.integration

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.nio.file.Files
import kramer.Kontext
import okhttp3.Protocol.HTTP_2
import okhttp3.Request
import org.junit.Test

/**
 * Checks [Kontext.shutdownHttp] against a real connection. Depends on the network and on Maven
 * Central negotiating HTTP/2: only an HTTP/2 connection has the non-daemon reader thread that
 * keeps a JVM alive, so the test first proves that thread exists, then that shutdown ends it. If
 * the protocol assertion ever fails, the thread-release tests can no longer observe the leak they
 * guard against and need a different setup, not a relaxed assertion.
 */
class HttpShutdownIntegrationTest {
  private val url = "https://repo.maven.apache.org/maven2/org/ow2/asm/asm/7.1/asm-7.1.pom"

  @Test fun shutdownHttpEndsConnectionReaderThread() {
    val kontext = Kontext(localRepository = Files.createTempDirectory("unused-cache-"))
    val client = kontext.httpClient(url)
    val protocol = client.newCall(Request.Builder().url(url).build()).execute().use { it.protocol }
    assertThat(protocol).isEqualTo(HTTP_2)
    assertWithMessage("Expected a live HTTP/2 connection reader thread before shutdown")
      .that(liveOkHttpThreads()).isNotEmpty()

    kontext.shutdownHttp()

    assertWithMessage("Non-daemon OkHttp threads survived shutdownHttp()")
      .that(survivingOkHttpThreads()).isEmpty()
  }
}
