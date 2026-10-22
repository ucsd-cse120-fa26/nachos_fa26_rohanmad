package nachos.threads;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import nachos.machine.*;

class timeQueue implements Comparable<timeQueue>{
	public KThread thread;
	public long time;

	public timeQueue(KThread t, long ti){
		this.thread = t;
		this.time = ti;
	}

	@Override 
	public int compareTo(timeQueue other){
		if(other.time > this.time){
			return -1;
		}
		if(other.time < this.time){
			return 1;
		}
		return 0;
	}
}

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public static Queue<timeQueue> q = new PriorityQueue<>();

	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean restore = Machine.interrupt().disable();
		while(q.size() != 0 && q.peek().time <= Machine.timer().getTime()){
			q.peek().thread.ready();
			q.poll();
		}
		Machine.interrupt().restore(restore);

	        /* Do not remove this call to yield. */
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * A negative or 0 wait value <i>x</i> returns immediately and
	 * does not block the calling thread.
	 *
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */

	public void setTimer(long wakeTime) {
		Lib.assertTrue(Machine.interrupt().disabled());
		q.add(new timeQueue(KThread.currentThread(), wakeTime));
	}

	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		if(x <= 0){
			return;
		}
		long wakeTime = Machine.timer().getTime() + x;
		boolean restore = Machine.interrupt().disable();
		setTimer(wakeTime);
		KThread.sleep();
		Machine.interrupt().restore(restore);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
			
			boolean restore = Machine.interrupt().disable();
			Iterator<timeQueue> iterator = q.iterator();

			while (iterator.hasNext()) {
				timeQueue tq = iterator.next();
				if (tq.thread == thread) { 
					iterator.remove(); 
					thread.ready();
					Machine.interrupt().restore(restore);
					return true; 
				}
			}

			Machine.interrupt().restore(restore);
			return false;
	}

	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// the timer only fires about every 500 ticks, so a thread can wake up to roughly one interval late.
	private static final long SLACK = 1000;

	// 1. A thread waits (approximately) its requested duration.
	public static void alarmTestDuration() {
		int durations[] = {1000, 10*1000, 100*1000};

		for (int d : durations) {
			long t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			long elapsed = Machine.timer().getTime() - t0;

			Lib.assertTrue(elapsed >= d);           
			Lib.assertTrue(elapsed < d + SLACK);     
			System.out.println("alarmTestDuration: waited " + elapsed
					+ " ticks for " + d + ": passed");
		}
	}

	// 2. A zero or negative wait returns without waiting.
	public static void alarmTestZeroOrNegative() {
		int durations[] = {0, -10, -2000};

		for (int d : durations) {
			long t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			long elapsed = Machine.timer().getTime() - t0;

			Lib.assertTrue(elapsed < 500);
			System.out.println("alarmTestZeroOrNegative: wait(" + d
					+ ") returned after " + elapsed + " ticks: passed");
		}
	}

	// 3. Multiple threads wake at the proper times, in the proper order.
	public static void alarmTestMultiple() {
		final long durations[] = {3000, 1000, 2000, 5000, 4000};
		final int n = durations.length;
		final long[] start = new long[n];
		final long[] end = new long[n];
		KThread[] threads = new KThread[n];

		for (int i = 0; i < n; i++) {
			final int id = i;
			threads[i] = new KThread(new Runnable() {
				public void run() {
					start[id] = Machine.timer().getTime();
					ThreadedKernel.alarm.waitUntil(durations[id]);
					end[id] = Machine.timer().getTime();
				}
			});
			threads[i].fork();
		}
		for (KThread t : threads)
			t.join(); 

		for (int i = 0; i < n; i++) {
			long elapsed = end[i] - start[i];
			Lib.assertTrue(elapsed >= durations[i]);
			Lib.assertTrue(elapsed < durations[i] + SLACK);
		}

		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (durations[i] < durations[j])
					Lib.assertTrue(end[i] <= end[j]);
			}
		}
		System.out.println("alarmTestMultiple: passed");
	}

	// cancel on a thread that never set a timer returns false.
	public static void alarmTestCancelNoTimer() {
		KThread t = new KThread(new Runnable() {
			public void run() { }
		});
		t.fork();
		t.join();
		Lib.assertTrue(!ThreadedKernel.alarm.cancel(t));
		Lib.assertTrue(!ThreadedKernel.alarm.cancel(KThread.currentThread()));
		System.out.println("alarmTestCancelNoTimer: passed");
	}

	public static void selfTest() {
		alarmTest1();
		alarmTestDuration();
		alarmTestZeroOrNegative();
		alarmTestMultiple();
		alarmTestCancelNoTimer();
		// Invoke your other test methods here ...
	}
}
