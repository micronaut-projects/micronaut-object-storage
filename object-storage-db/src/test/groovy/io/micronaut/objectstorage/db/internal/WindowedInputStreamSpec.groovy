package io.micronaut.objectstorage.db.internal

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class WindowedInputStreamSpec extends Specification {

    @Shared
    @AutoCleanup('shutdownNow')
    ExecutorService executor = Executors.newFixedThreadPool(2)

    static WindowedInputStream.WindowFetcher fetcherOf(byte[] content, int window, AtomicInteger calls = new AtomicInteger()) {
        return { long index ->
            calls.incrementAndGet()
            int from = (int) Math.min(content.length, index * window)
            int to = Math.min(content.length, from + window)
            Arrays.copyOfRange(content, from, to)
        } as WindowedInputStream.WindowFetcher
    }

    void 'it reads every window with prefetch and returns its budget on close'() {
        given:
        byte[] content = (0..<103).collect { (byte) it } as byte[]
        def budget = new ByteBudget(64)
        def calls = new AtomicInteger()
        def stream = new WindowedInputStream(fetcherOf(content, 10, calls), content.length, 10, budget, executor, 'test')

        expect:
        stream.readAllBytes() == content
        calls.get() == 11

        when:
        stream.close()

        then:
        budget.available() == 64
    }

    void 'with an unknown length a short window ends the stream'() {
        given:
        byte[] content = 'abcdefghijklmnopqrstuvw'.bytes
        def stream = new WindowedInputStream(fetcherOf(content, 5), -1, 5, new ByteBudget(100), null, 'test')

        expect:
        stream.readAllBytes() == content
    }

    void 'with an unknown length an exact multiple ends on an empty window'() {
        given:
        byte[] content = 'abcdefghij'.bytes
        def stream = new WindowedInputStream(fetcherOf(content, 5), -1, 5, new ByteBudget(100), executor, 'test')

        expect:
        stream.readAllBytes() == content
    }

    void 'a missing window means the object changed'() {
        given:
        def stream = new WindowedInputStream({ long index -> index == 0 ? 'abc'.bytes : null } as WindowedInputStream.WindowFetcher,
            6, 3, new ByteBudget(100), null, "object 'x'")

        when:
        stream.readAllBytes()

        then:
        def e = thrown(IOException)
        e.message.contains("object 'x'")
    }

    void 'fetch errors from the prefetch thread surface on read'() {
        given:
        def stream = new WindowedInputStream({ long index -> throw new IOException('boom') } as WindowedInputStream.WindowFetcher,
            6, 3, new ByteBudget(100), executor, 'test')

        when:
        stream.read()

        then:
        def e = thrown(IOException)
        e.message == 'boom'

        cleanup:
        stream.close()
    }

    void 'a download waits for budget held by another download'() {
        given: 'a budget of exactly one window'
        byte[] content = 'abcdef'.bytes
        def budget = new ByteBudget(3)
        def first = new WindowedInputStream(fetcherOf(content, 3), 6, 3, budget, null, 'first')
        def second = new WindowedInputStream(fetcherOf(content, 3), 6, 3, budget, null, 'second')

        when:
        first.read()
        def pending = CompletableFuture.supplyAsync({ second.readAllBytes() }, executor)
        pending.get(200, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)

        when:
        first.close()

        then:
        pending.get(5, TimeUnit.SECONDS) == content

        cleanup:
        second.close()
        budget.available() == 3
    }

    void 'reading a closed stream fails'() {
        given:
        def stream = new WindowedInputStream(fetcherOf('abc'.bytes, 3), 3, 3, new ByteBudget(10), null, 'test')
        stream.close()

        when:
        stream.read()

        then:
        thrown(IOException)
    }

    void 'an empty object needs no fetch'() {
        given:
        def calls = new AtomicInteger()
        def stream = new WindowedInputStream(fetcherOf(new byte[0], 3, calls), 0, 3, new ByteBudget(10), executor, 'test')

        expect:
        stream.read() == -1
        calls.get() == 0
    }
}
