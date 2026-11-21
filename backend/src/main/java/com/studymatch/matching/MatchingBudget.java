package com.studymatch.matching;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// how long an algorithm may search: a number of cost calculations (repeatable, for tests) or a time limit
public final class MatchingBudget {

  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

  private final long maxEvaluations;
  private final long limitNanos;
  // cpu time counts only this thread's work, so other programs on the machine don't eat the budget
  private final boolean cpuTime;
  private final long startNanos;
  private long used;

  private MatchingBudget(long maxEvaluations, long limitNanos, boolean cpuTime) {
    this.maxEvaluations = maxEvaluations;
    this.limitNanos = limitNanos;
    this.cpuTime = cpuTime;
    this.startNanos = now();
  }

  public static MatchingBudget evaluations(long maxEvaluations) {
    return new MatchingBudget(maxEvaluations, Long.MAX_VALUE, false);
  }

  // the same seed can give different groups, it depends on how fast the machine is
  public static MatchingBudget time(Duration limit) {
    return new MatchingBudget(Long.MAX_VALUE, limit.toNanos(), false);
  }

  // for comparing algorithms: the limit is the processor time this thread used, not the clock
  public static MatchingBudget cpuTime(Duration limit) {
    return new MatchingBudget(Long.MAX_VALUE, limit.toNanos(), true);
  }

  // call before each cost calculation; false once there's nothing left
  public boolean spend() {
    if (isExhausted()) {
      return false;
    }
    used++;
    return true;
  }

  public boolean isExhausted() {
    return used >= maxEvaluations || now() - startNanos >= limitNanos;
  }

  public long getUsed() {
    return used;
  }

  public boolean isCpuTime() {
    return cpuTime;
  }

  // one budget per parallel run: each gets the time that's left, or an equal share of the count that's left
  public List<MatchingBudget> split(int parts) {
    if (cpuTime) {
      throw new IllegalStateException("parallel runs are measured on the clock, not on processor time");
    }
    long nanosLeft = limitNanos == Long.MAX_VALUE ? Long.MAX_VALUE
        : Math.max(0, limitNanos - (now() - startNanos));
    long evaluationsLeft = maxEvaluations == Long.MAX_VALUE ? Long.MAX_VALUE : (maxEvaluations - used) / parts;
    List<MatchingBudget> budgets = new ArrayList<>();
    for (int part = 0; part < parts; part++) {
      budgets.add(new MatchingBudget(evaluationsLeft, nanosLeft, false));
    }
    return budgets;
  }

  // after the parallel runs finish, their calculations count as this budget's
  public void addUsed(MatchingBudget part) {
    used += part.used;
  }

  // 0 at the start, 1 at the end, by count or by time, whichever is further along
  public double progress() {
    double byCount = (double) used / maxEvaluations;
    double byTime = (double) (now() - startNanos) / limitNanos;
    return Math.min(1, Math.max(byCount, byTime));
  }

  private long now() {
    return cpuTime ? THREADS.getCurrentThreadCpuTime() : System.nanoTime();
  }

}
