/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.org.scadabr.timer;

import br.org.scadabr.ImplementMeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author aploese
 */
public class CronTimerPool extends AbstractTimer {

    ThreadPoolExecutor tpe;
class TimerThread extends Thread {

    boolean newTasksMayBeScheduled = true;

    private TaskQueue queue;

    private final GregorianCalendar calendar = new GregorianCalendar();

    TimerThread(TaskQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            mainLoop();
        } finally {
            // Someone killed this Thread, behave as if Timer cancelled
            synchronized (queue) {
                newTasksMayBeScheduled = false;
                queue.clear();  // Eliminate obsolete references
            }
        }
    }

    /**
     * The main timer loop. (See class comment.)
     */
    private void mainLoop() {
        while (true) {
            try {
                CronTask task;
                boolean taskFired;
                synchronized (queue) {
                    // Wait for queue to become non-empty
                    while (queue.isEmpty() && newTasksMayBeScheduled) {
                        queue.wait();
                    }
                    if (queue.isEmpty()) {
                        break; // Queue is empty and will forever remain; die
                    }
                    // Queue nonempty; look at first evt and do the right thing
                    long currentTime, executionTime;
                    task = queue.getMin();
                    synchronized (task.lock) {
                        if (task.state == CronTask.CANCELLED) {
                            queue.removeMin();
                            continue;  // No action required, poll queue again
                        }
                        currentTime = System.currentTimeMillis();
                        executionTime = task.nextExecutionTime;
                        if (taskFired = (executionTime <= currentTime)) {
                            queue.rescheduleMin(
                                    task.calcNextExecutionTimeAfter(calendar));
                        }
                    }
                    if (!taskFired) // Task hasn't yet fired; wait
                    {
                        queue.wait(executionTime - currentTime);
                    }
                }
                if (taskFired) // Task fired; run it, holding no locks
                {
                    task.currentTimeInMillis = task.nextExecutionTime;
                    tpe.execute(thread);
                }
            } catch (InterruptedException e) {
            }
        }
    }
}
    public void execute(Runnable r) {
        tpe.execute(r);
    }

    public void init(ThreadPoolExecutor threadPoolExecutor) {
        this.tpe = threadPoolExecutor;
    }

    public int size() {
        throw new ImplementMeException();
    }

    public void shutdown() {
        tpe.shutdown();
    }

    @Deprecated
    public ExecutorService getExecutorService() {
        return tpe;
    }

    private final TaskQueue queue = new TaskQueue();

    private final TimerThread thread = new TimerThread(queue);

    private final Object threadReaper = new Object() {
        protected void finalize() throws Throwable {
            synchronized (queue) {
                thread.newTasksMayBeScheduled = false;
                queue.notify(); // In case queue is empty.
            }
        }
    };

    private final static AtomicInteger nextSerialNumber = new AtomicInteger(0);

    private static int serialNumber() {
        return nextSerialNumber.getAndIncrement();
    }

    public CronTimerPool() {
        this("CronTimerPool-" + serialNumber());
    }

    public CronTimerPool(boolean isDaemon) {
        this("CronTimerPool-" + serialNumber(), isDaemon);
    }

    public CronTimerPool(String name) {
        thread.setName(name);
        thread.start();
    }

    public CronTimerPool(String name, boolean isDaemon) {
        thread.setName(name);
        thread.setDaemon(isDaemon);
        thread.start();
    }

    public void schedule(CronTask task) {

        synchronized (queue) {
            if (!thread.newTasksMayBeScheduled) {
                throw new IllegalStateException("Timer already cancelled.");
            }

            synchronized (task.lock) {
                if (task.state != CronTask.VIRGIN) {
                    throw new IllegalStateException(
                            "Task already scheduled or cancelled");
                }
                task.state = CronTask.SCHEDULED;
            }

            queue.add(task);
            if (queue.getMin() == task) {
                queue.notify();
            }
        }
    }

    public void cancel() {
        synchronized (queue) {
            thread.newTasksMayBeScheduled = false;
            queue.clear();
            queue.notify();  // In case queue was already empty.
        }
    }

    public int purge() {
        int result = 0;

        synchronized (queue) {
            for (int i = queue.size(); i > 0; i--) {
                if (queue.get(i).state == CronTask.CANCELLED) {
                    queue.quickRemove(i);
                    result++;
                }
            }

            if (result != 0) {
                queue.heapify();
            }
        }

        return result;
    }
}

class TaskQueue {

    private CronTask[] queue = new CronTask[128];

    private int size = 0;

    int size() {
        return size;
    }

    void add(CronTask task) {
        // Grow backing store if necessary
        if (size + 1 == queue.length) {
            queue = Arrays.copyOf(queue, 2 * queue.length);
        }

        queue[++size] = task;
        fixUp(size);
    }

    CronTask getMin() {
        return queue[1];
    }

    CronTask get(int i) {
        return queue[i];
    }

    void removeMin() {
        queue[1] = queue[size];
        queue[size--] = null;  // Drop extra reference to prevent memory leak
        fixDown(1);
    }

    void quickRemove(int i) {
        assert i <= size;

        queue[i] = queue[size];
        queue[size--] = null;  // Drop extra ref to prevent memory leak
    }

    void rescheduleMin(long newTime) {
        queue[1].nextExecutionTime = newTime;
        fixDown(1);
    }

    boolean isEmpty() {
        return size == 0;
    }

    void clear() {
        // Null out task references to prevent memory leak
        for (int i = 1; i <= size; i++) {
            queue[i] = null;
        }

        size = 0;
    }

    private void fixUp(int k) {
        while (k > 1) {
            int j = k >> 1;
            if (queue[j].nextExecutionTime <= queue[k].nextExecutionTime) {
                break;
            }
            CronTask tmp = queue[j];
            queue[j] = queue[k];
            queue[k] = tmp;
            k = j;
        }
    }

    private void fixDown(int k) {
        int j;
        while ((j = k << 1) <= size && j > 0) {
            if (j < size
                    && queue[j].nextExecutionTime > queue[j + 1].nextExecutionTime) {
                j++; // j indexes smallest kid
            }
            if (queue[k].nextExecutionTime <= queue[j].nextExecutionTime) {
                break;
            }
            CronTask tmp = queue[j];
            queue[j] = queue[k];
            queue[k] = tmp;
            k = j;
        }
    }

    void heapify() {
        for (int i = size / 2; i >= 1; i--) {
            fixDown(i);
        }
    }
}
