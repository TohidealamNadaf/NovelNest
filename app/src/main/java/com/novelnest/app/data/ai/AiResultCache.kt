package com.novelnest.app.data.ai

import com.novelnest.app.data.cache.MemoryLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

interface AiCache {
    val generation: Long
    suspend fun read(key: String, input: AiChapterInput, action: AiAction): AiResult?
    suspend fun write(key: String, result: AiResult, expectedGeneration: Long)
}

class AiResultCache(private val directory: File) : AiCache {
    private val lock = Mutex()
    private val epoch = AtomicLong()
    private val memory = MemoryLruCache<String, AiResult>(4)
    private var maintained = false
    override val generation: Long get() = epoch.get()

    override suspend fun read(key: String, input: AiChapterInput, action: AiAction): AiResult? = withContext(Dispatchers.IO) { lock.withLock {
        validKey(key)
        if (key != input.cacheKey(action)) return@withLock null
        val file = File(directory, "$key.json")
        try {
            maintain()
            memory[key]?.takeIf { it.action == action && it.sourceFingerprint == input.fingerprint() }?.let { return@withLock it }
            if (!file.isFile) return@withLock null
            if (file.length() > ENTRY_BYTES) { file.delete(); return@withLock null }
            val json = JSONObject(file.readText())
            require(json.getString("key") == key && json.getString("version") == AiPromptFactory.version(action))
            val route = AiRoute(AiProviderId.valueOf(json.getString("provider")), json.getString("model"))
            AiProviderCatalog.spec(route)
            val result = AiResponseValidator.parse(json.getJSONObject("output").toString(), action, input.fingerprint(), route,
                input.paragraphs.mapIndexed { index, text -> AiParagraph(index, text) }, input.extractionTarget).copy(createdAtMs = json.getLong("createdAt"))
            currentCoroutineContext().ensureActive()
            file.setLastModified(System.currentTimeMillis())
            memory[key] = result
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { runCatching { file.delete() }; null }
    } }

    override suspend fun write(key: String, result: AiResult, expectedGeneration: Long) = withContext(Dispatchers.IO) { lock.withLock {
        validKey(key)
        if (epoch.get() != expectedGeneration) return@withLock
        val temporary = File(directory, "$key.tmp")
        try {
            maintain()
            val bytes = JSONObject().put("key", key).put("version", AiPromptFactory.version(result.action))
                .put("provider", result.route.provider.name).put("model", result.route.model).put("createdAt", result.createdAtMs)
                .put("output", AiResponseValidator.outputJson(result)).toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > ENTRY_BYTES) return@withLock
            if (!directory.isDirectory && !directory.mkdirs()) return@withLock
            currentCoroutineContext().ensureActive()
            temporary.writeBytes(bytes)
            currentCoroutineContext().ensureActive()
            val target = File(directory, "$key.json")
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            memory[key] = result
            trim()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Disposable cache failure cannot turn successful generation into failure. */ }
        finally { runCatching { temporary.delete() } }
    } }

    /** Called under the cache lock, on IO, once per application-owned instance. Interrupted writes
     * leave disposable temporary files; recover them without a timer, service or main-thread scan. */
    private fun maintain() {
        if (maintained) return
        maintained = true
        directory.listFiles()?.filter { it.isFile && owned.matches(it.name) }?.forEach {
            if (it.extension == "tmp" || it.length() > ENTRY_BYTES) it.delete()
        }
        trim()
    }

    private fun trim() {
        val files = directory.listFiles { f -> f.isFile && owned.matches(f.name) && f.extension == "json" }
            .orEmpty().sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }; var count = files.size
        files.forEach { file ->
            if (size > 16L * 1024 * 1024 || count > 96) {
                val length = file.length()
                if (file.delete()) { size -= length; count--; memory.remove(file.nameWithoutExtension) }
            }
        }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) { lock.withLock {
        epoch.incrementAndGet(); memory.clear()
        var success = true
        directory.listFiles()?.forEach { if (!it.isFile || (!it.delete() && it.exists())) success = false }
        if (directory.exists() && directory.listFiles() == null) success = false
        success
    } }
    private fun validKey(key: String) { require(key.matches(Regex("[0-9a-f]{64}"))) }
    companion object {
        private const val ENTRY_BYTES = 384 * 1024
        private val owned = Regex("[0-9a-f]{64}\\.(json|tmp)")
    }
}
