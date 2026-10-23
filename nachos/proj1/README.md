# Proj 1 

Group Members: Rohan Madan

I used the textbook, slides, and podcasts for every method I wrote. 
I also wrote the implementations myself and drew out test cases and scenarios by
hand to understand the flow of the methods.


## Methods/Functions I implemented
- **Alarm**: `waitUntil` stores an absolute wake time in a PriorityQueue and sleeps.
  `timerInterrupt` readies every thread whose time has passed. A wait of 0 or less
  returns immediately.
- **KThread.join / finish**: `join` returns right away if the target has finished.
  Otherwise the caller records itself and sleeps (no busy waiting), and `finish`
  readies it. Self-join and a second join assert.
- **Condition2**: `sleep`, `wake`, and `wakeAll` use interrupt disable/restore. `wake`
  readies at most one waiter, and a wake with no waiters is lost.
- **sleepFor / Alarm.cancel**: `sleepFor` adds the thread to the wait queue, registers a
  timer entry with the alarm, and sleeps once. After waking, if the thread is still in the
  wait queue (the timer woke it), it removes itself. `Alarm.cancel` removes a thread's
  timer entry and readies it, returning true, or returns false if it has no timer. `wake()`
  calls `cancel` on the thread it dequeues, and readies the thread itself only if `cancel`
  returned false (a plain `sleep()` thread).
- **Rendezvous**: one lock per instance and a map from tag to the open pairing (both
  values, a "partner arrived" flag, and its own CV). The first thread on a tag creates
  the pairing and sleeps. The second removes it from the map, swaps values, sets the
  flag, and wakes the first. No interrupt disabling is used.

## How I tested them
I implemented the tests from the Testing section of the Project 1 document and used the
provided example tests. Each class's `selfTest()` runs them:

- **Alarm**: waits are at least the requested time, zero and negative waits return
  immediately, and threads with different durations wake in time order.
- **Join**: the caller waits while the target runs and doesn't wait if it has
  finished. Self-join and a second join assert, and one thread can join several
  others, with independent pairs not interfering.
- **Condition2**: `sleep` blocks, `wake` wakes only one waiter, `wakeAll` wakes all,
  calls without the lock assert, and a wake with no waiters is lost.
- **sleepFor**: it times out when nobody wakes it, returns early on `wake`, and handles
  multiple threads with different timeouts and `wakeAll`.
- **Rendezvous**: a thread blocks until a partner arrives and values are swapped. Many
  threads on one tag pair up, and different tags and different instances don't
  interact.