package com.github.ferdinandhuebner.jvminfogelfagent;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.jvmtop.openjdk.tools.ProxyClient;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Collection of {@link com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractor} objects.
 */
public class VirtualMachineValueExtractors {

  public static TotalCpuTimeNanosUsedExtractor totalCpuUsed() {
    return new TotalCpuTimeNanosUsedExtractor();
  }

  public static GcInfoExtractor gcInfo() {
    return new GcInfoExtractor();
  }

  /**
   * Extracts the total CPU time used from a virtual machine.
   */
  public static final class TotalCpuTimeNanosUsedExtractor implements VirtualMachineValueExtractor<Optional<Long>> {
    private Optional<Long> getTotalCpuNanosOs(ProxyClient proxyClient) {
      try {
        OperatingSystemMXBean osBean = proxyClient.getOperatingSystemMXBean();
        Method method = osBean.getClass().getMethod("getProcessCpuTime");
        method.setAccessible(true);
        Long cpuTime = (Long) method.invoke(osBean);
        if (cpuTime == null)
          return Optional.absent();
        return Optional.of(cpuTime);
      } catch (Exception e) {
        return Optional.absent();
      }
    }

    private Optional<Long> getTotalCpuNanosThreads(ProxyClient proxyClient) {
      long totalCpu = 0L;
      try {
        ThreadMXBean threadBean = proxyClient.getThreadMXBean();
        // we're missing the terminated threads here..
        long[] threadIds = threadBean.getAllThreadIds();
        for (long threadId : threadIds)
          totalCpu += threadBean.getThreadCpuTime(threadId);
        return Optional.of(totalCpu);
      } catch (IOException | RuntimeException e) {
        return Optional.absent();
      }
    }

    @Override
    public Optional<Long> apply(ProxyClient proxyClient) {
      return getTotalCpuNanosOs(proxyClient).or(getTotalCpuNanosThreads(proxyClient));
    }
  }

  /**
   * Extracts garbage collector information from a virtual machine.
   */
  public static final class GcInfoExtractor implements VirtualMachineValueExtractor<Optional<GcInfoExtractor.GcInformation>> {
    private static final class GarbageCollectorInfo {
      private final String gcName;
      private final long gcCount;
      private final long gcTimeNanos;

      // TODO might be useful to classify young/old generation garbage collectors

      public GarbageCollectorInfo(String gcName, long gcCount, long gcTimeNanos) {
        this.gcName = gcName;
        this.gcCount = gcCount;
        this.gcTimeNanos = gcTimeNanos;
      }
    }

    /**
     * Information on garbage collectors.
     */
    public static final class GcInformation {
      private final Map<String, GarbageCollectorInfo> gcInfo = new HashMap<>();

      private void addGcInfo(GarbageCollectorInfo info) {
        gcInfo.put(info.gcName, info);
      }

      /**
       * Returns the total cpu time (in nanoseconds) used for garbage collection by all garbage collectors.
       */
      public long getTotalGcTimeNanos() {
        long totalGcTime = 0L;
        for (GarbageCollectorInfo info : gcInfo.values())
          totalGcTime += info.gcTimeNanos;
        return totalGcTime;
      }

      /**
       * Returns a list of the names of known garbage collectors.
       */
      public List<String> getGarbageCollectors() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (GarbageCollectorInfo info : gcInfo.values()) {
          builder.add(info.gcName);
        }
        return builder.build();
      }

      /**
       * Returns the cpu time (in nanoseconds) the given garbage collector used for garbage collection.
       *
       * @param garbageCollector the garbage collector name, not {@code null}
       */
      public Optional<Long> gcTimeFor(String garbageCollector) {
        Objects.requireNonNull(garbageCollector);
        return Optional.of(gcInfo.get(garbageCollector)).transform(new Function<GarbageCollectorInfo, Long>() {
          @Override
          public Long apply(GarbageCollectorInfo input) {
            return input.gcTimeNanos;
          }
        });
      }

      /**
       * Returns the number of garbage collection events for the given garbage collector.
       * @param garbageCollector the garbage collector name, not {@code null}
       */
      public Optional<Long> gcCountFor(String garbageCollector) {
        Objects.requireNonNull(garbageCollector);
        return Optional.of(gcInfo.get(garbageCollector)).transform(new Function<GarbageCollectorInfo, Long>() {
          @Override
          public Long apply(GarbageCollectorInfo input) {
            return input.gcCount;
          }
        });
      }
    }

    private Optional<Long> getTotalGcTimeNanos(ProxyClient proxyClient) {
      long totalGcTime = 0L;
      try {
        Collection<GarbageCollectorMXBean> gcBeans = proxyClient.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
          totalGcTime += gcBean.getCollectionTime() * 1000000L; // to ns
        }
        return Optional.of(totalGcTime);
      } catch (Exception e) {
        return Optional.absent();
      }
    }

    @Override
    public Optional<GcInfoExtractor.GcInformation> apply(ProxyClient proxyClient) {
      GcInformation gcInfo = new GcInformation();
      try {
        Collection<GarbageCollectorMXBean> gcBeans = proxyClient.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
          String gcName = gcBean.getName();
          long gcCount = gcBean.getCollectionCount();
          long gcTimeNanos = gcBean.getCollectionTime() * 1000000L; // to ns
          gcInfo.addGcInfo(new GarbageCollectorInfo(gcName, gcCount, gcTimeNanos));
        }
        return Optional.of(gcInfo);
      } catch (Exception e) {
        return Optional.absent();
      }
    }
  }

}
