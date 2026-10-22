package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean restore = Machine.interrupt().disable();
		waitQueue.add(KThread.currentThread());
		conditionLock.release();
		KThread.sleep();
		Machine.interrupt().restore(restore);
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean restore = Machine.interrupt().disable();
		if(!waitQueue.isEmpty()){
			KThread t = waitQueue.removeFirst();
			if (!ThreadedKernel.alarm.cancel(t)) {
				t.ready();
			}
		}
		Machine.interrupt().restore(restore);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		while(!waitQueue.isEmpty()) wake();
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.  A negative or 0
	 * timeout returns immediately.
	 */
    public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
			
		if(timeout <= 0) return;
		boolean restore = Machine.interrupt().disable();
		waitQueue.add(KThread.currentThread());
		conditionLock.release();

		long wakeTime = Machine.timer().getTime() + timeout;
		ThreadedKernel.alarm.setTimer(wakeTime);
		KThread.sleep();

		if(waitQueue.contains(KThread.currentThread())){
			waitQueue.remove(KThread.currentThread());
		}
		
		Machine.interrupt().restore(restore);
		conditionLock.acquire();
	}

    private Lock conditionLock;
	private LinkedList<KThread> waitQueue;



	

	private static void yieldMany(int n) {
		for (int i = 0; i < n; i++)
			KThread.yield();
	}
	
	private static KThread[] startSleepers(final Lock lock, final Condition2 cv,
			final int[] started, final int[] woke, int n) {
		KThread[] kids = new KThread[n];
		for (int i = 0; i < n; i++) {
			kids[i] = new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					started[0]++;
					cv.sleep();
					woke[0]++;
					lock.release();
				}
			});
			kids[i].fork();
		}
		return kids;
	}
	
	private static void waitForSleepers(Lock lock, int[] started, int n) {
		lock.acquire();
		while (started[0] < n) {
			lock.release();
			KThread.yield();
			lock.acquire();
		}
	}

	private static void condTestSleepBlocks() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		int[] started = new int[1];
		int[] woke = new int[1];
		KThread[] kids = startSleepers(lock, cv, started, woke, 1);
	
		waitForSleepers(lock, started, 1);
		lock.release();
		yieldMany(10);
		Lib.assertTrue(woke[0] == 0);          
	
		lock.acquire();
		cv.wake();
		lock.release();
		kids[0].join();
		Lib.assertTrue(woke[0] == 1);
		System.out.println("condTestSleepBlocks: passed");
	}

	private static void condTestWakeOne() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		int[] started = new int[1];
		int[] woke = new int[1];
		KThread[] kids = startSleepers(lock, cv, started, woke, 3);
	
		waitForSleepers(lock, started, 3);
		cv.wake();
		lock.release();
		yieldMany(10);
	
		lock.acquire();
		Lib.assertTrue(woke[0] == 1);          
		cv.wakeAll();                        
		lock.release();
		for (KThread k : kids)
			k.join();
		Lib.assertTrue(woke[0] == 3);
		System.out.println("condTestWakeOne: passed");
	}

	private static void condTestWakeAll() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		int[] started = new int[1];
		int[] woke = new int[1];
		KThread[] kids = startSleepers(lock, cv, started, woke, 3);
	
		waitForSleepers(lock, started, 3);
		cv.wakeAll();
		lock.release();
		for (KThread k : kids)
			k.join();
		Lib.assertTrue(woke[0] == 3);
		System.out.println("condTestWakeAll: passed");
	}

	private static void condTestNoLock() {
		Condition2 cv = new Condition2(new Lock());
		boolean threw;
	
		threw = false;
		try { cv.sleep(); } catch (Error e) { threw = true; }
		Lib.assertTrue(threw);
	
		threw = false;
		try { cv.wake(); } catch (Error e) { threw = true; }
		Lib.assertTrue(threw);
	
		threw = false;
		try { cv.wakeAll(); } catch (Error e) { threw = true; }
		Lib.assertTrue(threw);
	
		System.out.println("condTestNoLock: passed");
	}

	private static void condTestLostWake() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		int[] started = new int[1];
		int[] woke = new int[1];
	
		lock.acquire();
		cv.wake();                             
		cv.wakeAll();
		lock.release();
	
		KThread[] kids = startSleepers(lock, cv, started, woke, 1);
		waitForSleepers(lock, started, 1);
		lock.release();
		yieldMany(10);
		Lib.assertTrue(woke[0] == 0);        
	
		lock.acquire();
		cv.wake();
		lock.release();
		kids[0].join();
		Lib.assertTrue(woke[0] == 1);
		System.out.println("condTestLostWake: passed");
	}

	    // Place Condition2 test code inside of the Condition2 class.

    // Test programs should have exactly the same behavior with the
    // Condition and Condition2 classes.  You can first try a test with
    // Condition, which is already provided for you, and then try it
    // with Condition2, which you are implementing, and compare their
    // behavior.

    // Do not use this test program as your first Condition2 test.
    // First test it with more basic test programs to verify specific
    // functionality.

    public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

	private static KThread[] startTimedSleepers(final Lock lock, final Condition2 cv,
		final long[] timeouts, final int[] started, final long[] elapsed,
		final int[] order, final int[] finished) {
	KThread[] kids = new KThread[timeouts.length];
	for (int i = 0; i < timeouts.length; i++) {
		final int id = i;
		kids[i] = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				started[0]++;
				long start = Machine.timer().getTime();
				cv.sleepFor(timeouts[id]);
				elapsed[id] = Machine.timer().getTime() - start;
				order[id] = finished[0]++;
				lock.release();
			}
		});
		kids[i].fork();
	}
	return kids;
	}

	private static void condTestSleepForTimeout() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] timeouts = {2000};
		int[] started = new int[1], finished = new int[1], order = new int[1];
		long[] elapsed = new long[1];
	
		KThread[] kids = startTimedSleepers(lock, cv, timeouts, started, elapsed, order, finished);
		kids[0].join();
	
		Lib.assertTrue(elapsed[0] >= 2000);         
		Lib.assertTrue(elapsed[0] < 2000 + 1000);    
		System.out.println("condTestSleepForTimeout: passed (" + elapsed[0] + " ticks)");
	}

	private static void condTestSleepForEarlyWake() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] timeouts = {100000};
		int[] started = new int[1], finished = new int[1], order = new int[1];
		long[] elapsed = new long[1];
	
		KThread[] kids = startTimedSleepers(lock, cv, timeouts, started, elapsed, order, finished);
		waitForSleepers(lock, started, 1);           
		cv.wake();
		lock.release();
		kids[0].join();
		Lib.assertTrue(elapsed[0] < 100000);        
	
		ThreadedKernel.alarm.waitUntil(200000);
		System.out.println("condTestSleepForEarlyWake: passed (" + elapsed[0] + " ticks)");
	}

	private static void condTestSleepForMultiple() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] timeouts = {3000, 1000, 2000};
		int[] started = new int[1], finished = new int[1], order = new int[3];
		long[] elapsed = new long[3];
	
		KThread[] kids = startTimedSleepers(lock, cv, timeouts, started, elapsed, order, finished);
		for (KThread k : kids) k.join();
	
		for (int i = 0; i < 3; i++)
			Lib.assertTrue(elapsed[i] >= timeouts[i]);
		Lib.assertTrue(order[1] == 0);  
		Lib.assertTrue(order[2] == 1);   
		Lib.assertTrue(order[0] == 2);
		System.out.println("condTestSleepForMultiple: passed");
	}

	private static void condTestSleepForWakeAll() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] timeouts = {100000, 150000, 200000};
		int[] started = new int[1], finished = new int[1], order = new int[3];
		long[] elapsed = new long[3];
	
		KThread[] kids = startTimedSleepers(lock, cv, timeouts, started, elapsed, order, finished);
		waitForSleepers(lock, started, 3);
		cv.wakeAll();
		lock.release();
		for (KThread k : kids) k.join();
	
		for (int i = 0; i < 3; i++)
			Lib.assertTrue(elapsed[i] < timeouts[i]);
	
		ThreadedKernel.alarm.waitUntil(300000);      
		System.out.println("condTestSleepForWakeAll: passed");
	}

	public static void selfTest() {
		condTestSleepBlocks();
		condTestWakeOne();
		condTestWakeAll();
		condTestNoLock();
		condTestLostWake();
		cvTest5();
		condTestSleepForTimeout();
		condTestSleepForEarlyWake();
		condTestSleepForMultiple();
		condTestSleepForWakeAll();
	}
}
