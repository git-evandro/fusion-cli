package com.example.fusioncli.repository

import android.content.Context
import android.os.Build
import com.example.fusioncli.data.CommandOutput
import com.example.fusioncli.data.ExecutionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Repository responsible for executing the Kilo CLI installer and streaming its output.
 *
 * On Android the stock shell is mksh (`/system/bin/sh`) and there is no `bash` or `curl`, so
 * the installer's own download stage cannot run. This repository fetches the installer
 * script, adapts the single bash-only construct it contains, downloads the platform artifact
 * itself, and runs the official script in its `--binary` mode.
 *
 * @param context The application context.
 * @param homeDir Writable directory exposed to the child process as $HOME.
 * @param tmpDir Writable directory used for the downloaded archive and extracted binary.
 */
class CommandRepository(
    private val context: Context,
    private val homeDir: File,
    private val tmpDir: File
) {

    private val _executionState = MutableStateFlow(ExecutionState.IDLE)

    /**
     * The current state of the command execution.
     */
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()

    /**
     * Executes the Kilo CLI installation, streaming stdout, stderr and error messages.
     *
     * @return A Flow of [CommandOutput].
     */
    fun executeKiloInstall(): Flow<CommandOutput> = callbackFlow {
        _executionState.value = ExecutionState.IN_PROGRESS

        fun emit(message: String) {
            trySend(CommandOutput.Stdout(message))
        }

        val workJob = launch(Dispatchers.IO) {
            try {
                // 1. Fetch the official installer script.
                emit("Downloading installation script from $INSTALL_SCRIPT_URL...")
                val scriptContent = httpGetText(INSTALL_SCRIPT_URL)
                emit("Download complete. Starting installation...")

                // 2. Make the script runnable under mksh. Android parses a whole if/else
                //    chain up-front, so the bash-only `[[ ... =~ ... ]]` is a syntax error
                //    even when its branch never executes.
                val preparedScript = patchBashOnlyConstructs(scriptContent)
                if (preparedScript.contains("=~")) {
                    emit("Warning: the installer still contains unsupported bash syntax; it may fail.")
                }

                // 3. Select an interpreter. We strictly use 'sh' as it is the standard
                //    Android shell (mksh) and the installer is patched for POSIX compliance.
                val interpreter = "sh"
                if (!isCommandAvailable(interpreter)) {
                    _executionState.value = ExecutionState.FAILED
                    trySend(CommandOutput.Error("Dependency missing: 'sh' is not available."))
                    close()
                    return@launch
                }

                // 4. Skip entirely when the current release is already installed.
                homeDir.mkdirs()
                tmpDir.mkdirs()

                val installedBinary = File(homeDir, INSTALLED_BINARY_RELATIVE_PATH)
                val installedVersion = readInstalledVersion()
                val latestVersion = try {
                    resolveLatestVersion()
                } catch (e: Exception) {
                    if (installedBinary.isFile) {
                        emit("Could not check for updates: ${e.message ?: "Unknown error"}")
                        emit(
                            "Kilo Code ${installedVersion ?: "(unknown version)"} is already " +
                                "installed at ${installedBinary.absolutePath}."
                        )
                        _executionState.value = ExecutionState.SUCCESS
                        close()
                        return@launch
                    }
                    throw e
                }

                if (installedBinary.isFile && installedVersion == latestVersion) {
                    emit("Kilo Code $latestVersion is already installed at ${installedBinary.absolutePath}.")
                    emit("Nothing to do — skipping download and installation.")
                    _executionState.value = ExecutionState.SUCCESS
                    close()
                    return@launch
                }

                if (installedBinary.isFile) {
                    emit("Updating Kilo Code from ${installedVersion ?: "(unknown)"} to $latestVersion...")
                }

                // 5. Provide the platform artifact ourselves and install it in --binary mode.
                val binary = prepareBinary(latestVersion, ::emit)

                val processBuilder = ProcessBuilder(
                    interpreter, "-s", "--", "--binary", binary.absolutePath
                )
                processBuilder.directory(homeDir)

                val environment = processBuilder.environment()
                environment["HOME"] = context.filesDir.absolutePath
                environment["TMPDIR"] = tmpDir.absolutePath
                environment["SHELL"] = interpreter
                environment["TERM"] = "xterm"
                environment["PATH"] = buildSearchPath(environment["PATH"])

                emit("Running installer with '$interpreter'...")
                emit("HOME=${context.filesDir.absolutePath}")
                val process = processBuilder.start()

                val stdoutJob = launch(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = try { reader.readLine() } catch (e: Exception) { null }
                            if (line == null) break
                            trySend(CommandOutput.Stdout(sanitize(line)))
                        }
                    }
                }

                val stderrJob = launch(Dispatchers.IO) {
                    process.errorStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = try { reader.readLine() } catch (e: Exception) { null }
                            if (line == null) break
                            trySend(CommandOutput.Stderr(sanitize(line)))
                        }
                    }
                }

                // Write the installer script to the process's stdin.
                try {
                    process.outputStream.bufferedWriter().use { writer ->
                        writer.write(preparedScript)
                        writer.flush()
                    }
                } catch (e: IOException) {
                    // The process may exit before consuming all of stdin; output is still read below.
                }

                val exitCode = process.waitFor()
                stdoutJob.join()
                stderrJob.join()

                binary.delete()

                if (exitCode == 0) {
                    writeInstalledVersion(latestVersion)
                    _executionState.value = ExecutionState.SUCCESS
                } else {
                    _executionState.value = ExecutionState.FAILED
                    trySend(CommandOutput.Error("Command failed with exit code $exitCode"))
                }
                close()

            } catch (e: Exception) {
                _executionState.value = ExecutionState.FAILED
                trySend(CommandOutput.Error("Installation failed: ${e.message ?: "Unknown error"}"))
                close(e)
            }
        }

        awaitClose {
            workJob.cancel()
        }
    }

    /**
     * Resolves the latest release, downloads the platform archive and extracts the `kilo`
     * executable, emitting progress along the way.
     */
    private fun prepareBinary(version: String, emit: (String) -> Unit): File {
        val target = resolveTarget()
            ?: throw IOException("Unsupported device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")

        val fileName = "kilo-$target.tar.gz"
        val url = "$RELEASE_BASE_URL/v$version/$fileName"

        emit("Installing Kilo Code version: $version")
        emit("Platform: $target")
        emit("Downloading $fileName...")

        val tarball = File(tmpDir, fileName)
        downloadToFile(url, tarball, emit)
        emit("Download complete (${tarball.length() / MEBIBYTE} MB). Extracting...")

        val binary = File(tmpDir, "kilo")
        extractTarGzMember(tarball, "kilo", binary)
        if (!binary.isFile || binary.length() == 0L) {
            throw IOException("Failed to extract 'kilo' from $fileName")
        }
        tarball.delete()

        emit("Extracted kilo (${binary.length() / MEBIBYTE} MB). Starting installation...")
        return binary
    }

    /**
     * Reads the `latest` dist-tag published for `@kilocode/cli` on npm, matching what the
     * official installer does to pick a release.
     */
    private fun resolveLatestVersion(): String {
        val json = httpGetText(NPM_DIST_TAGS_URL)
        return LATEST_TAG_REGEX.find(json)?.groupValues?.get(1)
            ?: throw IOException("Could not resolve the latest Kilo version.")
    }

    /**
     * Maps the device ABI to a release target. Only 64-bit targets are published.
     */
    private fun resolveTarget(): String? {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it == "arm64-v8a" } -> "linux-arm64"
            abis.any { it == "x86_64" } -> "linux-x64"
            else -> null
        }
    }

    private fun httpGetText(url: String): String {
        val connection = openConnection(url)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadToFile(url: String, destination: File, emit: (String) -> Unit) {
        val connection = openConnection(url)
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var nextMark = PROGRESS_STEP
                var read = input.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    total += read
                    if (total >= nextMark) {
                        emit("...${total / MEBIBYTE} MB")
                        nextMark += PROGRESS_STEP
                    }
                    read = input.read(buffer)
                }
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("HTTP $code for $url")
        }
        return connection
    }

    /**
     * Extracts a single regular-file member from a `.tar.gz` archive without relying on the
     * system `tar` (toybox refuses `chown` on Android and exits non-zero).
     */
    private fun extractTarGzMember(tarball: File, member: String, destination: File) {
        GZIPInputStream(tarball.inputStream().buffered()).use { input ->
            val header = ByteArray(TAR_BLOCK)
            while (true) {
                if (!readFully(input, header, TAR_BLOCK)) break
                if (header.all { it == 0.toByte() }) break

                val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
                val size = String(header, 124, 12, Charsets.US_ASCII)
                    .trim()
                    .substringBefore('\u0000')
                    .toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val isRegularFile = type == '0' || type.code == 0

                if (isRegularFile && (name == member || name == "./$member")) {
                    destination.outputStream().use { output -> copyExactly(input, output, size) }
                    return
                }

                skipExactly(input, size)
                skipExactly(input, paddingFor(size))
            }
        }
        throw IOException("'$member' was not found in the archive.")
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) return offset != 0
            offset += read
        }
        return true
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read < 0) throw IOException("Unexpected end of archive.")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read < 0) throw IOException("Unexpected end of archive.")
            remaining -= read
        }
    }

    private fun paddingFor(size: Long): Long = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK

    /**
     * Rewrites the installer's single bash-only regex test to a POSIX-compatible one so the
     * script parses under mksh.
     */
    private fun patchBashOnlyConstructs(script: String): String {
        if (!script.contains(BASH_REGEX_TEST_LINE)) return script
        return script.replace(BASH_REGEX_TEST_LINE, POSIX_REGEX_TEST_LINE)
    }

    /**
     * Returns the version recorded for the currently installed binary, or `null` if Kilo has
     * not been installed by this app yet.
     */
    fun installedVersion(): String? = readInstalledVersion()

    private fun readInstalledVersion(): String? =
        File(homeDir, INSTALLED_VERSION_RELATIVE_PATH)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.filter { it.code >= 0x20 }
            ?.ifBlank { null }
            ?.takeIf { it != "null" }

    private fun writeInstalledVersion(version: String) {
        val marker = File(homeDir, INSTALLED_VERSION_RELATIVE_PATH)
        marker.parentFile?.mkdirs()
        marker.writeText(version)
    }

    /**
     * Strips ANSI/VT control sequences (e.g. the installer's `\033[0;2m` color codes) and
     * other control characters so the log streamer shows readable text.
     */
    private fun sanitize(text: String): String {
        val withoutAnsi = ANSI_ESCAPE_REGEX.replace(text, "")
        return withoutAnsi.filter { it == '\t' || it.code >= 0x20 }
    }

    /**
     * Checks if a command is available in the system. We set HOME and use -c exit
     * to avoid "HOME: parameter not set" errors if the shell evaluates profiles.
     */
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val pb = ProcessBuilder(command, "-c", "exit")
            pb.environment()["HOME"] = context.filesDir.absolutePath
            val process = pb.start()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ensures the standard Android binary directories are present on PATH so that tools
     * invoked by the installer (grep, sed, cp, chmod, ...) can be resolved.
     */
    private fun buildSearchPath(existing: String?): String {
        val required = listOf("/system/bin", "/system/xbin", "/vendor/bin", "/sbin")
        val entries = (existing?.split(':').orEmpty() + required)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return entries.joinToString(":")
    }

    private companion object {
        const val INSTALL_SCRIPT_URL = "https://kilo.ai/cli/install"
        const val NPM_DIST_TAGS_URL =
            "https://registry.npmjs.org/-/package/%40kilocode%2Fcli/dist-tags"
        const val RELEASE_BASE_URL = "https://github.com/Kilo-Org/kilocode/releases/download"

        const val INSTALLED_BINARY_RELATIVE_PATH = ".kilo/bin/kilo"
        const val INSTALLED_VERSION_RELATIVE_PATH = ".kilo/version"

        const val TAR_BLOCK = 512
        const val MEBIBYTE = 1024L * 1024L
        const val PROGRESS_STEP = 16L * 1024L * 1024L

        val LATEST_TAG_REGEX = Regex("\"latest\"\\s*:\\s*\"([^\"]+)\"")

        /** Matches ANSI CSI escape sequences such as color codes and cursor movement. */
        val ANSI_ESCAPE_REGEX = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        const val BASH_REGEX_TEST_LINE =
            "if [[ ! \"\$specific_version\" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\$ ]]; then"
        const val POSIX_REGEX_TEST_LINE =
            "if ! echo \"\$specific_version\" | grep -Eq '^[0-9]+\\.[0-9]+\\.[0-9]+\$'; then"
    }
}
