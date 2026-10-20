package nachos.threads;
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
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		if(x <= 0){
			return;
		}
		long wakeTime = Machine.timer().getTime() + x;
		boolean restore = Machine.interrupt().disable();
		q.add(new timeQueue(KThread.currentThread(), wakeTime));
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

	public static void alarmZeroTest(){
		int durations[] = {0, 10*(-1), 100*(-20)};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmMultiple(){
		int durations[] = {1000, 1000, 1001};
		KThread[] threads = new KThread[durations.length];
		final Semaphore done = new Semaphore(0);
	
		for(int i = 0; i < durations.length; i++){
			final int d = durations[i];
			final int id = i;			
			threads[i] = new KThread(new Runnable() {
				public void run() {
					long start = Machine.timer().getTime();
					ThreadedKernel.alarm.waitUntil(d);
					long end = Machine.timer().getTime();
					System.out.println("thread " + id + ": ..." + start + " and finished at: " + end);
					done.V();                    
				}
			});
			threads[i].fork();                   

		}

		for (int i = 0; i < durations.length; i++) done.P();
	}

	public static void selfTest() {
		alarmTest1();
		alarmZeroTest();
		alarmMultiple();
		// Invoke your other test methods here ...
	}
}
