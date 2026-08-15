package com.engine.nativeai

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelLibraryTest {

    private fun tempDir(): File = Files.createTempDirectory("lml-test").toFile()

    private fun write(dir: File, name: String, bytes: Int = 4): File {
        val f = File(dir, name)
        f.writeBytes(ByteArray(bytes) { 1 })
        return f
    }

    private fun fakeProvider(descriptor: ModelDescriptor): ModelProvider = object : ModelProvider {
        override val descriptor: ModelDescriptor = descriptor
        override fun stream(request: ModelRequest) =
            flowOf(ModelStreamEvent.Token("ok") as ModelStreamEvent)
        override suspend fun complete(request: ModelRequest) =
            ModelResult("ok", 1, 1, descriptor.id)
        override suspend fun health() = ProviderHealth(true)
    }

    @Test
    fun scanFindsOnlyGgufSortedAndIgnoresTmp() {
        val dir = tempDir()
        write(dir, "b-model.gguf")
        write(dir, "a-model.gguf")
        write(dir, "skip.bin")
        write(dir, "model.gguf.tmp")

        val lib = LocalModelLibrary(dir)
        val names = lib.scan().map { it.file.name }

        assertEquals(listOf("a-model.gguf", "b-model.gguf"), names)
    }

    @Test
    fun stableIdSchemeKeepsModelGgufBackwardCompatible() {
        assertEquals("local-llama", LocalModelLibrary.stableId("model.gguf"))
        assertEquals("local-qwen2-0.5b", LocalModelLibrary.stableId("qwen2-0.5b.gguf"))
        assertEquals("local-llama", LocalModelLibrary.stableId("model.GGUF"))
    }

    @Test
    fun resolveReturnsFileForKnownIdAndNullOtherwise() {
        val dir = tempDir()
        val f = write(dir, "qwen.gguf")
        val lib = LocalModelLibrary(dir)

        assertEquals(f, lib.resolve("local-qwen"))
        assertNull(lib.resolve("missing"))
    }

    @Test
    fun deleteRemovesOnlyRequestedFile() {
        val dir = tempDir()
        write(dir, "a.gguf")
        write(dir, "b.gguf")
        val lib = LocalModelLibrary(dir)

        assertTrue(lib.delete("local-a"))
        assertFalse(File(dir, "a.gguf").exists())
        assertTrue(File(dir, "b.gguf").exists())
        assertFalse(lib.delete("missing"))
    }

    @Test
    fun syncIntoRegistersProviderPerEntry() {
        val dir = tempDir()
        write(dir, "qwen2.gguf")
        write(dir, "gemma3.gguf")
        val registry = ModelRegistry()
        val lib = LocalModelLibrary(dir)

        lib.syncInto(registry) { e -> fakeProvider(lib.descriptorFor(e)) }

        assertEquals(2, registry.list().size)
        assertNotNull(registry.provider("local-qwen2"))
        assertNotNull(registry.provider("local-gemma3"))
        assertEquals("qwen2", registry.get("local-qwen2")?.displayName)
    }

    @Test
    fun syncIntoDropsProvidersForDeletedFiles() {
        val dir = tempDir()
        write(dir, "a.gguf")
        write(dir, "b.gguf")
        val registry = ModelRegistry()
        val lib = LocalModelLibrary(dir)
        lib.syncInto(registry) { e -> fakeProvider(lib.descriptorFor(e)) }

        assertTrue(lib.delete("local-a"))
        lib.syncInto(registry) { e -> fakeProvider(lib.descriptorFor(e)) }

        assertEquals(1, registry.list().size)
        assertNull(registry.get("local-a"))
        assertNotNull(registry.get("local-b"))
    }

    @Test
    fun syncIntoAddsPlaceholderOnlyWhenEmpty() {
        val dir = tempDir()
        val registry = ModelRegistry()
        val lib = LocalModelLibrary(dir)

        lib.syncInto(registry) { e -> fakeProvider(lib.descriptorFor(e)) }

        assertEquals(1, registry.list().size)
        assertEquals(LocalModelProvider.LOCAL_MODEL_ID, registry.list().first().id)
        assertNull(registry.provider(LocalModelProvider.LOCAL_MODEL_ID))

        write(dir, "qwen.gguf")
        lib.syncInto(registry) { e -> fakeProvider(lib.descriptorFor(e)) }
        assertEquals(1, registry.list().size)
        assertEquals("local-qwen", registry.list().first().id)
        assertNotNull(registry.provider("local-qwen"))
    }
}
