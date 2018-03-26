## LMAX Disruptor

A High Performance Inter-Thread Messaging Library

## Maintainer

[Michael Barker](https://github.com/mikeb01)

[![Build Status](https://semaphoreci.com/api/v1/mikeb01/disruptor/branches/master/badge.svg)](https://semaphoreci.com/mikeb01/disruptor)

## Support

[Google Group](https://groups.google.com/group/lmax-disruptor)

## Documentation

* [Introduction](https://github.com/LMAX-Exchange/disruptor/wiki/Introduction)
* [Getting Started](https://github.com/LMAX-Exchange/disruptor/wiki/Getting-Started)

## Changelog

### 3.4.2

- Fix race condition in BatchEventProcessor with 3 or more starting/halting concurrently.

### 3.4.1

 - Fix race between run() and halt() on BatchEventProcessor.

### 3.4.0

 - Drop support for JDK6, support JDK7 and above only.
 - Add `ThreadHints.onSpinWait` to all busy spins within Disruptor.
 - Increase default sleep time for LockSupport.parkNanos to prevent busy spinning.

### 3.3.8

- Revert belt and braces WaitStrategy signalling.

### 3.3.7

- Add batch size to `BatchStartAware.onBatchStart()`
- Upgrade to newer versions of gradle, checkstyle and JUnit
- Deprecate classes & methods for later release
- Remove JMock and rewrite tests accordingly

### 3.3.6

- Support adding gating sequences before calling Disruptor.start()
- Fix minor concurrency race when dynamically adding sequences
- Fix wrapping problem when adding work handlers to the Disruptor

### 3.3.5

- Fix NPE in TimeoutBlockingWaitStrategy when used with WorkProcessor
- Add LiteTimeoutBlockingWaitStrategy
- Resignal any waiting threads when trying to publish to a full ring buffer

### 3.3.4

- Small build fixes and refactorings
- Removed unused MutableLong class

### 3.3.3

- Support ThreadFactory in Disruptor DSL
- Make use of the Executor deprecated

### 3.3.2

- Minor Javadoc fixes, example code and file renames.
- Additional generics for parametrised ExceptionHandler.

### 3.3.1

- Minor Javadoc fixes, example code and file renames.
- Make ExceptionHandler type parametrised.

### 3.3.0

- Inheritance based Padding for RingBuffer and Sequencers.
- Better DSL support for adding custom EventProcessors.
- Remove deprecated methods (slightly breaking change)
- Experimental LiteBlockingWaitStrategy
- Experimental EventPoller for polling for data instead of waiting.

### 3.2.1 Released (10-Mar-2014)

- Minor build and IDE updates
- Rewrite of performance tests to run without JUnit and separate Queues in to their own tests
- Remove old throttled performance test
- Add performance tests for immutable message example
- Add performance tests for off-heap example
- Remove old Caliper tests
- Remove some stray yield() calls

### 3.2.0 Released (13-Aug-2013)

- Fix performance bug in WorkProcessor with MultiProducerSequencer
- Add EventRelease support to WorkProcessor (experimental)
- Add PingPongLatencyTest
- Switch to HdrHistogram for latency measurement

### 3.1.1 Released (9-Jul-2013)

- Fix bug in WorkProcessor where consumers could get ahead of publishers

### 3.1.0 Released (17-Jun-2013)

- Fix bug in Disruptor DSL where some consumers wouldn't be included in the gating sequences.
- Add support for using the EventTranslator with batch publication.
- Support timeouts when shutting down the Disruptor using the DSL.

### 3.0.1 Released (16-Apr-2013)

- Remove Sequencer.ensureAvailable() and move functionality into the ProcessingSequenceBarrier.
- Add get() method and deprecate getPublished() and getPreallocated() from the RingBuffer.
- Add TimeoutException to SequenceBarrier.waitFor().
- Fix off by one bug in MultiProducerSequencer.publish(lo, hi).
- Improve testing for Sequencers.

### 3.0.0 Released (10-Apr-2013)

- Add remaining capacity to RingBuffer
- Add batch publish methods to Sequencer
- Add DataProvider interface to decouple the RingBuffer and BatchEventProcessor
- Upgrade to gradle 1.5

### 3.0.0.beta5 Released (08-Apr-2013)

- Make Sequencer public

### 3.0.0.beta4 Released (08-Apr-2013)

- Refactoring, merge Publisher back into Sequencer and some of the gating sequence responsibilities up to the sequencer.

### 3.0.0.beta3 Released (20-Feb-2013)

- Significant Javadoc updates (thanks Jason Koch)
- DSL support for WorkerPool
- Small performance tweaks
- Add TimeoutHandler and TimeoutBlockingWaitStrategy and support timeouts in BatchEventProcessor

### 3.0.0.beta2 Released (7-Jan-2013)

- Remove millisecond wakeup from BlockingWaitStrategy
- Add RingBuffer.claimAndGetPreallocated
- Add RingBuffer.isPublished

### 3.0.0.beta1 Released (3-Jan-2013)

- Remove claim strategies and replace with Publishers/Sequences, remove pluggability of claim strategies.
- Introduce new multi-producer publisher algorithm (faster and more scalable).
- Introduce more flexible EventPublisher interface that allow for static definition of translators
that can handle local values.
- Allow for dynamic addition of gating sequences to ring buffer.  Default it to empty, will allow
messages to be sent and the ring buffer to wrap if there are no gating sequences defined.
- Remove batch writes to the ring buffer.
- Remove timeout read methods.
- Switch to gradle build and layout the source maven style.
- API change, remove RingBuffer.get, add RingBuffer.getPreallocated for producers and
RingBuffer.getPublished for consumers.
- Change maven dependency group id to com.lmax.
- Added PhasedBackoffStrategy.
- Remove explicit claim/forcePublish and supply a resetTo method.
- Added better handling of cases when the gating sequence is ahead of the cursor value.

### 2.10.3 Released (22-Aug-2012)

- Bug fix, race condition in SequenceGroup when removing Sequences and getting current value

### 2.10.2 Released (21-Aug-2012)

- Bug fix, potential race condition in BlockingWaitStrategy.
- Bug fix set initial SequenceGroup value to -1 (Issue #27).
- Deprecate timeout methods that will be removed in version 3.

### 2.10.1 (6-June-2012)

- Bug fix, correct OSGI metadata.
- Remove unnecessary code in wait strategies.

### 2.10 (13-May-2012)

- Remove deprecated timeout methods.
- Added OSGI metadata to jar file.
- Removed PaddedAtomicLong and use Sequence in all places.
- Fix various generics warnings.
- Change Sequence implementation to work around IBM JDK bug and improve performance by ~10%.
- Add a remainingCapacity() call to the Sequencer class.

### 2.9 (8-Apr-2012)

- Deprecate timeout methods for publishing.
- Add tryNext and tryPublishEvent for events that shouldn't block during delivery.
- Small performance enhancement for MultithreadClaimStrategy.

### 2.8 (6-Feb-2012)

- Create new MultithreadClaimStrategy that works between when threads are highly contended. Previous implementation is now called MultithreadLowContentionClaimStrategy
- Fix for bug where EventProcessors weren't being added as gating sequences to the ring buffer.
- Fix range tracking bug in Histogram

### 2.7.1  (21-Dec-2011)

- Artefacts made available via maven central repository. (groupId:com.googlecode.disruptor, artifactId:disruptor) See UsingDisruptorInYourProject for details.

### 2.7 (12-Nov-2011)

- Changed construction API to allow user supplied claim and wait strategies
- Added AggregateEventHandler to support multiple EventHandlers from a single BatchEventProcessor
- Support exception handling from LifecycleAware
- Added timeout API calls for claiming a sequence when publishing
- Use LockSupport.parkNanos() instead of Thread.sleep() to reduce latency
- Reworked performance tests to better support profiling and use LinkedBlockingQueue for comparison because it performs better on the latest processors
- Minor bugfixes

### 2.6

- Introduced WorkerPool to allow the one time consumption of events by a worker in a pool of EventProcessors.
- New internal implementation of SequenceGroup which is lock free at all times and garbage free for get and set operations.
- SequenceBarrier now checks alert status on every call whether it is blocking or not.
- Added scripts in preparation for publishing binaries to maven repository.

### 2.5.1

- Bugfix for supporting SequenceReportingEventHandler from DSL. ([issue 9](https://github.com/LMAX-Exchange/disruptor/issues#issue/9))
- Bugfix for multi-threaded publishing to multiple ring buffers ([issue 10](https://github.com/LMAX-Exchange/disruptor/issues#issue/10))
- Change SequenceBarrier to always check alert status before entering waitFor cycle.  Previously this was only checked when the requested sequence was not available.
- Change ClaimStrategy to not spin when the buffer has no available capacity, instead go straight to yielding to allow event processors to catch up.

### 2.5

- Changed RingBuffer and publisher API so any mutable object can be placed in the RingBuffer without having to extend AbstractEvent
- Added EventPublisher implementation to allow the publishing steps to be combined into one action
- DisruptorWizard has been renamed to Disruptor with added support for any subtype of EventProcessor
- Introduced a new Sequencer class that allows the Disruptor to be applied to other data structures such as multiple arrays.  This can be a very useful pattern when multiple event processors work on the same event and you want to avoid false sharing.  The Diamond for FizzBuzz is a good example of the issue.  It is also higher performance by avoiding the pointer indirection when arrays of primitives are used.
- Further increased performance and scalability by reducing false sharing.
- Added progressive backoff strategy to the MultiThreadedClaimStrategy to prevent publisher getting into the claim cycle when the buffer is full because of a slow EventProcessor.
- Significantly improved performance to WaitStrategy.Option.BLOCKING
- Introduced SequenceGroup to allow dynamic registration of EventProcessors.

### 2.0.2

- Rework of "False Sharing" prevention which makes the performance much more predictable across all platforms. Special thanks to Jeff Hain for helping focus in on a solution.

### 2.0.1

- Renaming mistake for publishEventAtSequence should have been claimEventAtSequence
- Fixed bug in YieldingStrategy that was busy spinning more than yielding and introduced SleepingStrategy
- Removed code duplication in Unicast perf tests for expected result

### 2.0.0

- New API to reflect naming changes
- Producer -> Publisher
- Entry -> Event
- Consumer -> EventProcessor
- ConsumerBarrier -> DependencyBarrier
- ProducerBarrier has been incorporated into the RingBuffer for ease of use
- DisruptorWizard integrated for fluent API dependency graph construction
- Rework of sequence tracking to avoid false sharing on Java 7, plus avoid mega-morphic calls to make better use of the instruction cache
- Reduced usage of memory barriers where possible
- WaitStrategy.YIELDING initially spins for a short period to reduce latency
- Major performance improvement giving more than a 2X increase for throughput across most use cases.

### 1.2.2

- ProducerBarrier change to yield after busy spinning for a while.  This may help the situation when the the number of producers exceeds the number of cores.

### 1.2.1

- Bug fix for setting the sequence in the ForceFillProducerBarrier.
- Code syntax tidy up.

### 1.2.0

- Bug fix for regression introduced inlining multi-thread producer commit tracking code.  This was a critical bug for the multi-threaded producer scenario.
- Added new ProducerBarrier method for claiming a batch of sequences.  This feature can give a significant throughput increase.

### 1.1.0

- Off by one regression bug in ProducerBarrier introduced in 1.0.9.
- Clarified the algorithm for initial cursor value in the ClaimStrategy.

### 1.0.9

- Added Apache 2.0 licence and comments.
- Small performance improvements to producers barriers and BatchConsumer.

### 1.0.8

- Bugfix for BatchConsumer sequence update when using SequenceTrackingHandler to ensure sequence is always updated at the end of a batch regardless.

### 1.0.7

- Factored out LifecycleAware interface to allowing consumers handlers to be notified when their thread starts and shuts down.

### 1.0.6

- Cache minimum consumer sequence in producer barriers.  This helps make the performance more predictable on Nehalem processors and greater on earlier Core 2 processors.

### 1.0.5

- Removed Entry interface.  All Entries must now extend AbstractEntry.
- Made setSequence package private on AbstractEntry for encapsulation.
