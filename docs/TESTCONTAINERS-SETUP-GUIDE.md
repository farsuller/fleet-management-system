# Testcontainers & PostGIS Integration Test Setup Guide

Complete setup guide for running the PostGIS/Testcontainers integration tests
(`PostGISAdapterTest`) from scratch on a fresh machine — Windows or macOS.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Clone and Build the Project](#2-clone-and-build-the-project)
3. [macOS Setup](#3-macos-setup)
4. [Windows Setup](#4-windows-setup)
5. [Testcontainers Properties File](#5-testcontainers-properties-file)
6. [Running the Tests](#6-running-the-tests)
7. [Error Reference — Symptoms and Fixes](#7-error-reference--symptoms-and-fixes)
8. [How the Skip Guard Works](#8-how-the-skip-guard-works)
9. [CI / GitHub Actions](#9-ci--github-actions)

---

## 1. Prerequisites

| Tool | Required Version | Purpose |
|------|-----------------|---------|
| JDK | 21 (LTS) | Kotlin compilation target — **must be JDK 21** |
| Docker Desktop | Latest stable | Container runtime for Testcontainers |
| Git | Any | Clone the repo |

> **No local PostgreSQL installation is required.** Testcontainers pulls and manages
> a `postgis/postgis:15-3.3` container automatically each test run.

---

## 2. Clone and Build the Project

```bash
git clone <repo-url>
cd fleet-management
```

### Verify JDK 21

```bash
java -version
```

Expected output (version string must start with `21`):

```
openjdk version "21.0.x" ...
```

**If wrong version:**

- macOS: `brew install openjdk@21` then follow brew's caveats to symlink it
- Windows: Download from [adoptium.net](https://adoptium.net/) and update `JAVA_HOME`

### Verify Gradle wrapper resolves

```bash
# macOS / Linux
./gradlew --version

# Windows PowerShell
.\gradlew --version
```

Expected: Gradle 8.x, Kotlin 2.1.0.

---

## 3. macOS Setup

### 3.1 Install Docker Desktop

1. Download from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
2. Open the `.dmg`, drag Docker to Applications, launch it
3. Complete the onboarding wizard
4. Verify:

```bash
docker ps
```

Expected: empty table (no running containers) — no error.

### 3.2 Configure Testcontainers

Create `~/.testcontainers.properties`:

```bash
cat > ~/.testcontainers.properties << 'EOF'
ryuk.disabled=true
EOF
```

> `ryuk.disabled=true` disables the Ryuk reaper sidecar container. On macOS this is
> optional but avoids occasional "connection reset" noise on developer machines;
> it is the standard dev-machine setting.

### 3.3 Verify Docker daemon is reachable

```bash
docker info | grep "Server Version"
```

Expected: `Server Version: 27.x.x` (any recent version is fine).

### 3.4 macOS is done

Skip to [§ 6 Running the Tests](#6-running-the-tests).

---

## 4. Windows Setup

Windows requires a few extra steps because Docker Desktop on Windows routes API
calls through a proxy that is **incompatible with docker-java** (the library
Testcontainers uses) unless WSL 2 integration is active.

### 4.1 Verify WSL 2 is installed

Open **PowerShell as Administrator** and run:

```powershell
wsl --list --verbose
```

**Case A — you see a distro listed** (e.g. `Ubuntu` with `VERSION 2`):
WSL 2 is ready. Skip to [§ 4.3](#43-install-docker-desktop).

**Case B — the list is empty** (output has no entries under the header):
You need to install a WSL 2 distro. Continue with § 4.2.

### 4.2 Install WSL 2 + Ubuntu (if needed)

In **PowerShell as Administrator**:

```powershell
wsl --install -d Ubuntu
```

This installs WSL 2 and the Ubuntu distro in one step. When prompted, create a
Unix username and password (these are only used inside WSL — they can be
anything).

**Restart your computer** after the install completes.

After the restart, Ubuntu will open automatically to finish first-run setup.
When it shows a `$` prompt you can close it.

Confirm WSL 2 is now active:

```powershell
wsl --list --verbose
```

Expected:

```
  NAME      STATE           VERSION
* Ubuntu    Running         2
```

### 4.3 Install Docker Desktop

1. Download from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
2. Run the installer. When asked, keep **"Use WSL 2 instead of Hyper-V"** checked
   (it may be pre-checked if WSL 2 was already installed)
3. After installation, start Docker Desktop from the Start menu
4. Complete the onboarding

### 4.4 Enable WSL 2 integration in Docker Desktop

1. Open Docker Desktop → **Settings** (gear icon)
2. Go to **Resources → WSL Integration**
3. Toggle **"Enable integration with my default WSL distro"** ON
4. Also toggle **Ubuntu** (or whatever distro you installed) ON
5. Click **Apply & Restart**

Docker Desktop will restart. Wait for the whale icon in the taskbar to stop
animating.

### 4.5 Verify Docker is reachable from PowerShell

```powershell
docker ps
```

Expected: empty table, no error.

```powershell
docker info | Select-String "Server Version"
```

Expected: `Server Version: 27.x.x`

### 4.6 Configure Testcontainers

Create the properties file at `C:\Users\<your-username>\.testcontainers.properties`:

```powershell
@"
ryuk.disabled=true
"@ | Out-File -FilePath "$env:USERPROFILE\.testcontainers.properties" -Encoding ASCII
```

> **Why `ryuk.disabled=true`?** The Ryuk reaper container uses a separate TCP
> connection that can fail on some Windows network configurations. Disabling it
> means Testcontainers won't spin up the reaper — containers are still cleaned up
> by the JVM shutdown hook.

Verify the file was created:

```powershell
Get-Content "$env:USERPROFILE\.testcontainers.properties"
```

Expected:

```
ryuk.disabled=true
```

### 4.7 Remove stale DOCKER_HOST overrides (if any)

If you previously set `DOCKER_HOST` in your environment (e.g. while debugging),
clear it — Docker Desktop's WSL 2 integration registers the socket automatically:

```powershell
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", $null, "User")
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", $null, "Machine")
```

Restart your terminal after this.

---

## 5. Testcontainers Properties File

The project's `build.gradle.kts` already sets two environment variables in the
test task:

```kotlin
// build.gradle.kts
tasks.test {
    useJUnitPlatform()
    environment("DOCKER_HOST", "tcp://localhost:2375")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
```

The `DOCKER_HOST` fallback to `tcp://localhost:2375` is a secondary path used
when the auto-detected socket is not available (primarily for CI). Combined with
the `~/.testcontainers.properties` file above, this covers all environments.

Your final `~/.testcontainers.properties` should be:

```properties
ryuk.disabled=true
```

That single line is sufficient. The TCP fallback in `build.gradle.kts` handles
the rest.

---

## 6. Running the Tests

### Run only the PostGIS integration tests (entire class)

```bash
# macOS / Linux
./gradlew test --tests "com.solodev.fleet.PostGISAdapterTest"

# Windows PowerShell
.\gradlew test --tests "com.solodev.fleet.PostGISAdapterTest"
```

**Expected output (Docker available):**

```
> Task :test

PostGISAdapterTest > should snap location to route() PASSED
PostGISAdapterTest > should detect geofence entry() PASSED

BUILD SUCCESSFUL in 45s
```

### Run a single test method

Use a wildcard pattern with `*` to match part of the method name:

```bash
# macOS / Linux — snap test only
./gradlew test --tests "com.solodev.fleet.PostGISAdapterTest.*snap*"

# macOS / Linux — geofence test only
./gradlew test --tests "com.solodev.fleet.PostGISAdapterTest.*geofence*"

# Windows PowerShell — snap test only
.\gradlew test --tests "com.solodev.fleet.PostGISAdapterTest.*snap*"

# Windows PowerShell — geofence test only
.\gradlew test --tests "com.solodev.fleet.PostGISAdapterTest.*geofence*"
```

**Expected output (single test):**

```
> Task :test

PostGISAdapterTest > should snap location to route() PASSED

BUILD SUCCESSFUL in 38s
```

> **Tip — Kotlin backtick method names**: Gradle resolves Kotlin test method
> names (declared with `` `backtick syntax` ``) using the JVM method name, which
> replaces spaces with underscores internally. Using a `*wildcard*` pattern is the
> most reliable cross-platform approach and avoids quoting issues on any shell.

Testcontainers will print log lines similar to:

```
INFO  o.testcontainers.DockerClientFactory - Testcontainers version: 1.20.4
INFO  o.testcontainers.DockerClientFactory - Docker host: ...
INFO  o.testcontainers.DockerClientFactory - Docker version: ...
INFO  🐳 [postgis/postgis:15-3.3] - Container started in 12s
```

### Run the full test suite

```bash
.\gradlew test          # Windows
./gradlew test          # macOS / Linux
```

The PostGIS tests are gated by the Docker availability check. If Docker is not
reachable they are **skipped** (not failed), so the full suite always returns
`BUILD SUCCESSFUL` regardless of Docker state.

**Expected output when Docker is not reachable:**

```
PostGISAdapterTest > should snap location to route() SKIPPED
PostGISAdapterTest > should detect geofence entry() SKIPPED

BUILD SUCCESSFUL
```

---

## 7. Error Reference — Symptoms and Fixes

### Error 7.1 — `Could not find a valid Docker environment`

**Full error:**

```
ERROR o.t.d.DockerClientProviderStrategy - Could not find a valid Docker environment.
Please check configuration. Attempted configurations were:
    EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception ...
    NpipeSocketClientProviderStrategy: failed with exception ...
As no valid configuration was found, execution cannot continue.
```

**Cause:** Docker Desktop is not running, or WSL 2 integration is not enabled.

**Fix — Windows:**

1. Open Docker Desktop and wait for it to fully start (whale icon stops animating)
2. Go to **Settings → Resources → WSL Integration** and confirm your distro is
   toggled ON
3. Click **Apply & Restart** if you changed anything
4. Re-run the test

**Fix — macOS:**

1. Open Docker Desktop from Applications and wait for it to fully start
2. Re-run the test

---

### Error 7.2 — `Status 400` with empty `ServerVersion`

**Full error (in test output or `--info` log):**

```
BadRequestException (Status 400: {"ID":"","ServerVersion":"","KernelVersion":"", ...})
```

**Cause:** This is the Docker Desktop Windows proxy issue. Docker Desktop on
Windows uses a relay that returns `HTTP 400` to versioned Docker API calls
(`/v1.41/info`) when WSL 2 integration is not active. The docker-java library
(used by Testcontainers) validates `ServerVersion` and rejects empty strings.

This happens when:
- No WSL 2 distro is installed (`wsl --list --verbose` shows empty output)
- Docker Desktop is set to Hyper-V backend instead of WSL 2
- WSL integration is installed but not enabled for the distro in Docker Desktop settings

**Fix:**

Follow [§ 4.2](#42-install-wsl-2--ubuntu-if-needed) and [§ 4.4](#44-enable-wsl-2-integration-in-docker-desktop) exactly.

After enabling WSL 2 integration, verify:

```powershell
# Should return the real engine version, not empty string
docker info | Select-String "Server Version"
```

---

### Error 7.3 — `JAVA_HOME is set to an invalid directory`

**Full error:**

```
ERROR: JAVA_HOME is set to an invalid directory: C:\Program Files\Java\jdk-17
```

Or Gradle picks the wrong JDK and compilation fails with `--release 21` errors.

**Fix — Windows:**

1. Download JDK 21 from [adoptium.net](https://adoptium.net/)
2. Install it (note the install path, e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.x.x`)
3. Update `JAVA_HOME`:

```powershell
[System.Environment]::SetEnvironmentVariable(
    "JAVA_HOME",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot",
    "User"
)
```

4. Restart your terminal

**Fix — macOS:**

```bash
brew install openjdk@21
# Follow brew's "For the system Java wrappers to find this JDK" instructions
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

---

### Error 7.4 — `Connection refused` pulling `postgis/postgis:15-3.3`

**Full error:**

```
com.github.dockerjava.api.exception.DockerClientException: ...
java.net.ConnectException: Connection refused
```

Or:

```
org.testcontainers.containers.ContainerLaunchException:
Timed out waiting for container port to open
```

**Cause:** Docker Desktop is running but has no internet access, or
Docker Hub rate limiting.

**Fix:**

1. Check internet access: `ping 8.8.8.8`
2. Pull the image manually first to cache it:

```bash
docker pull postgis/postgis:15-3.3
```

3. Re-run the test. Once the image is cached locally it never needs to pull again.

---

### Error 7.5 — `Ryuk` container fails to start

**Full error:**

```
org.testcontainers.containers.ContainerLaunchException: Could not create/start Ryuk container
```

**Cause:** The Ryuk resource reaper container has a separate network handshake
that can fail on Windows Docker Desktop or restrictive firewalls.

**Fix:**

Add `ryuk.disabled=true` to `~/.testcontainers.properties` as described in
[§ 5](#5-testcontainers-properties-file).

---

### Error 7.6 — `Could not find driver class org.postgresql.Driver`

**Full error:**

```
org.jetbrains.exposed.exceptions.ExposedSQLException:
java.sql.SQLException: No suitable driver found for jdbc:postgresql://...
```

**Cause:** The test classpath is missing the PostgreSQL JDBC driver.

**Fix:** Verify `build.gradle.kts` has this in `dependencies`:

```kotlin
testImplementation(libs.testcontainers.postgresql)
```

And `libs.versions.toml` has:

```toml
[libraries]
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
```

Then run:

```bash
.\gradlew dependencies --configuration testRuntimeClasspath | Select-String "testcontainers"
```

---

### Error 7.7 — Tests are SKIPPED when Docker IS running

**Symptom:** Tests report `SKIPPED` even though Docker Desktop is open and
`docker ps` works.

**Cause:** Stale `DOCKER_HOST` pointing to a wrong socket, or Docker Desktop
is still starting.

**Diagnosis steps:**

```powershell
# 1. Check what DOCKER_HOST is set to
echo $env:DOCKER_HOST

# 2. Verify Docker daemon is fully up
docker info

# 3. Check if Testcontainers can reach it
# (look for "Docker host:" in --info test output)
.\gradlew test --tests "com.solodev.fleet.PostGISAdapterTest" --info 2>&1 |
    Select-String "Docker host|isDockerAvailable|valid"
```

**Fix:**

If `DOCKER_HOST` is set to a stale value:

```powershell
$env:DOCKER_HOST = ""
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", $null, "User")
```

Restart the terminal and re-run.

---

## 8. How the Skip Guard Works

`BaseSpatialTest` (the base class for `PostGISAdapterTest`) implements a
graceful skip mechanism so the full test suite never hard-fails when Docker is
absent:

```kotlin
// BaseSpatialTest.kt — simplified

fun isDockerAvailable(): Boolean =
    try { DockerClientFactory.instance().isDockerAvailable } catch (_: Exception) { false }

@BeforeAll
fun setup() {
    assumeTrue(
        isDockerAvailable(),
        "Skipping — Docker not reachable."
    )
    // ... start container and connect DB only if Docker is available
}
```

Key design decisions:

| Decision | Reason |
|----------|--------|
| `DockerClientFactory.instance().isDockerAvailable` | Uses the exact same probe Testcontainers would run — no custom socket logic |
| `try/catch` wrapper | `DockerClientFactory` throws instead of returning false in some failure modes |
| `container by lazy {}` | Container is never initialised if the `assumeTrue` aborts setup; avoids a hard crash before the skip |
| `assumeTrue` not `assertTrue` | JUnit 5 treats a failing `assumeTrue` as `ABORTED/SKIPPED`, not `FAILED` |

---

## 9. CI / GitHub Actions

No additional setup is required. The `ubuntu-latest` GitHub Actions runner has
Docker Engine pre-installed and the socket is available at `/var/run/docker.sock`.

Testcontainers auto-detects it via `UnixSocketClientProviderStrategy`.

Relevant `.github/workflows/` configuration (already in the project):

```yaml
- name: Run tests
  run: ./gradlew test
  env:
    TESTCONTAINERS_RYUK_DISABLED: "true"
```

`TESTCONTAINERS_RYUK_DISABLED` is also set as a project-level env in
`build.gradle.kts` as a fallback; both are consistent.

The PostGIS tests will execute (not skip) in CI because a real Docker socket is
available. `BUILD SUCCESSFUL` in CI means all tests — including PostGIS — passed.

---

## Quick-Reference Checklist

### macOS

- [ ] Docker Desktop installed and running (`docker ps` succeeds)
- [ ] `~/.testcontainers.properties` contains `ryuk.disabled=true`
- [ ] JDK 21 active (`java -version` shows `21.x.x`)
- [ ] `./gradlew test --tests "com.solodev.fleet.PostGISAdapterTest"` → PASSED

### Windows

- [ ] WSL 2 installed with at least one distro (`wsl --list --verbose` shows a `VERSION 2` entry)
- [ ] Docker Desktop installed and running
- [ ] Docker Desktop → Settings → Resources → WSL Integration → distro toggled ON → Apply & Restart
- [ ] `docker ps` and `docker info` work without errors from PowerShell
- [ ] `~/.testcontainers.properties` (`%USERPROFILE%\.testcontainers.properties`) contains `ryuk.disabled=true`
- [ ] No stale `DOCKER_HOST` env var set in user/system environment
- [ ] JDK 21 active (`java -version` shows `21.x.x`)
- [ ] `.\gradlew test --tests "com.solodev.fleet.PostGISAdapterTest"` → PASSED
