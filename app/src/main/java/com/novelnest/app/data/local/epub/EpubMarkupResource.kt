package com.novelnest.app.data.local.epub

import kotlinx.coroutines.CancellationException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.flatMap
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingResource

/** Errors stay in Readium's resource pipeline; no DOM or section bytes are retained between reads. */
internal class EpubMarkupResource(private val source: Resource) : TransformingResource(source, cacheBytes = false) {
    override suspend fun transform(data: Try<ByteArray, ReadError>): Try<ByteArray, ReadError> = data.flatMap { bytes ->
        try {
            if (bytes.size > EpubArchiveValidator.MAX_MARKUP_BYTES)
                Try.failure(ReadError.Decoding("This EPUB section exceeds the supported size."))
            else Try.success(EpubContentSanitizer.sanitize(bytes))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Try.failure(ReadError.Decoding(error)) }
        catch (error: OutOfMemoryError) { Try.failure(ReadError.OutOfMemory(error)) }
    }

    // In Readium 3.0.3, TransformingResource.read(null) transforms twice when cacheBytes=false.
    // Read once and slice the transformed bytes, without a permanent resource cache.
    override suspend fun read(range: LongRange?): Try<ByteArray, ReadError> = try {
        transform(source.read()).map { bytes ->
            if (range == null) bytes else {
                val start = range.first.coerceIn(0L, bytes.size.toLong()).toInt()
                val end = if (range.last >= bytes.size.toLong()) bytes.size
                    else (range.last + 1).coerceIn(0L, bytes.size.toLong()).toInt()
                if (range.isEmpty() || end <= start) byteArrayOf() else bytes.copyOfRange(start, end)
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Try.failure(ReadError.Decoding(error)) }
    catch (error: OutOfMemoryError) { Try.failure(ReadError.OutOfMemory(error)) }

    override suspend fun length(): Try<Long, ReadError> = read(null).map { it.size.toLong() }
}
