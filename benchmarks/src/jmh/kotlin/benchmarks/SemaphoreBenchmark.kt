/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks

import benchmarks.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MICROSECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MICROSECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
open class SemaphoreBenchmark {
    @Param("FORK_JOIN", "KOTLIN_DEFAULT", "GO_BASED")
    private var _1_dispatcher: SemaphoreBenchDispatcherCreator = SemaphoreBenchDispatcherCreator.FORK_JOIN

    @Param("0", "1000")
    private var _2_coroutines: Int = 0

    @Param("1", "2", "4", "8", "32", "128", "100000")
    private var _3_maxPermits: Int = 0

    @Param("1", "2", "4") // local machine
//    @Param("1", "2", "4", "8", "16", "32", "64", "128", "144") // dasquad
//    @Param("1", "2", "4", "8", "16", "32", "64", "96") // Google Cloud
//    @Param("1", "2", "4", "8", "16", "32", "64", "128") // dasquad
    private var _4_parallelism: Int = 0

    private lateinit var dispatcher: CoroutineDispatcher
    private var coroutines = 0

    @InternalCoroutinesApi
    @Setup
    fun setup() {
        dispatcher = _1_dispatcher.create(_4_parallelism)
        coroutines = if (_2_coroutines == 0) _4_parallelism else _2_coroutines
    }

    @Benchmark
    fun semaphore() = runBlocking {
        val n = BATCH_SIZE / coroutines
        val semaphore = Semaphore(_3_maxPermits)
        val jobs = ArrayList<Job>(coroutines)
        repeat(coroutines) {
            jobs += GlobalScope.launch {
                repeat(n) {
                    semaphore.withPermit {
                        doGeomDistrWork(WORK_INSIDE)
                    }
                    doGeomDistrWork(WORK_OUTSIDE)
                }
            }
        }
        jobs.forEach { it.join() }
    }

    @Benchmark
    fun channelAsSemaphore() = runBlocking {
        val n = BATCH_SIZE / coroutines
        val semaphore = Channel<Unit>(_3_maxPermits)
        val jobs = ArrayList<Job>(coroutines)
        repeat(coroutines) {
            jobs += GlobalScope.launch {
                repeat(n) {
                    semaphore.send(Unit) // acquire
                    doGeomDistrWork(WORK_INSIDE)
                    semaphore.receive() // release
                    doGeomDistrWork(WORK_OUTSIDE)
                }
            }
        }
        jobs.forEach { it.join() }
    }
}

enum class SemaphoreBenchDispatcherCreator(val create: (parallelism: Int) -> CoroutineDispatcher) {
    FORK_JOIN({ parallelism -> ForkJoinPool(parallelism).asCoroutineDispatcher() }),
    KOTLIN_DEFAULT({ parallelism -> kotlinx.coroutines.Dispatchers.KotlinDefault }),
    GO_BASED({ parallelism -> Dispatchers.Default }) // TODO doesn't take parallelism into account
}

private const val WORK_INSIDE = 80
private const val WORK_OUTSIDE = 40
private const val BATCH_SIZE = 1000000
