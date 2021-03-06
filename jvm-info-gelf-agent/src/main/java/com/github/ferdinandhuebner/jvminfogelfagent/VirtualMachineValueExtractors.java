package com.github.ferdinandhuebner.jvminfogelfagent;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.jvmtop.openjdk.tools.ProxyClient;

import javax.management.MBeanServerConnection;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.lang.management.*;
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

  public static HeapInfoExtractor heapInfo() {
    return new HeapInfoExtractor();
  }

  public static NonHeapInfoExtractor nonHeapInfo() {
    return new NonHeapInfoExtractor();
  }

  public static LoadedClassesExtractor loadedClasses() {
    return new LoadedClassesExtractor();
  }

  public static ThreadInformationExtractor threadInformation() {
    return new ThreadInformationExtractor();
  }

  public static HostNameExtractor hostName() {
    return new HostNameExtractor();
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

      public long getTotalGcCount() {
        long totalGc = 0L;
        for (GarbageCollectorInfo info : gcInfo.values())
          totalGc += info.gcCount;
        return totalGc;
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
       *
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

  public static final class HeapInformation {
    private final long heapSize;
    private final long heapUsed;
    private final long heapMax;

    public HeapInformation(long heapSize, long heapUsed, long heapMax) {
      this.heapSize = heapSize;
      this.heapUsed = heapUsed;
      this.heapMax = heapMax;
    }

    /**
     * Returns the current size (amount of memory that is committed for the virtual machine to use) in bytes.
     */
    public long getHeapSize() {
      return heapSize;
    }

    /**
     * Returns the current amount of memory used (in bytes).
     */
    public long getHeapUsed() {
      return heapUsed;
    }

    /**
     * Returns the maximal heap size in bytes.
     */
    public long getHeapMax() {
      return heapMax;
    }
  }

  /**
   * Collector for heap information (size, used, max).
   * FIXME: For some reason, there is a difference in what JVisualVM shows (event though they access the same MXBean)
   */
  public static final class HeapInfoExtractor implements VirtualMachineValueExtractor<Optional<HeapInformation>> {
    @Override
    public Optional<HeapInformation> apply(ProxyClient proxyClient) {
      try {
        MemoryUsage heapUsage = proxyClient.getMemoryMXBean().getHeapMemoryUsage();
        return Optional.of(new HeapInformation(heapUsage.getCommitted(), heapUsage.getUsed(), heapUsage.getMax()));
      } catch (IOException e) {
        return Optional.absent();
      }
    }
  }

  public static final class NonHeapInformation {
    private final long nonHeapSize;
    private final long nonHeapUsed;
    private final long nonHeapMax;

    public NonHeapInformation(long nonHeapSize, long nonHeapUsed, long nonHeapMax) {
      this.nonHeapSize = nonHeapSize;
      this.nonHeapUsed = nonHeapUsed;
      this.nonHeapMax = nonHeapMax;
    }

    public long getNonHeapSize() {
      return nonHeapSize;
    }

    public long getNonHeapUsed() {
      return nonHeapUsed;
    }

    public long getNonHeapMax() {
      return nonHeapMax;
    }
  }

  /**
   * Collector for non-heap (permgen, metaspace) information (size, used, max).
   * FIXME: For some reason, there is a difference in what JVisualVM shows (event though they access the same MXBean)
   */
  public static final class NonHeapInfoExtractor implements VirtualMachineValueExtractor<Optional<NonHeapInformation>> {
    @Override
    public Optional<NonHeapInformation> apply(ProxyClient proxyClient) {
      try {
        MemoryUsage nonHeap = proxyClient.getMemoryMXBean().getNonHeapMemoryUsage();
        return Optional.of(new NonHeapInformation(nonHeap.getCommitted(), nonHeap.getUsed(), nonHeap.getMax()));
      } catch (IOException e) {
        return Optional.absent();
      }
    }
  }

  /**
   * Collects the number of loaded classes.
   */
  public static final class LoadedClassesExtractor implements VirtualMachineValueExtractor<Optional<Integer>> {
    @Override
    public Optional<Integer> apply(ProxyClient proxyClient) {
      try {
        return Optional.of(proxyClient.getClassLoadingMXBean().getLoadedClassCount());
      } catch (IOException e) {
        return Optional.absent();
      }
    }
  }

  public static final class ThreadInformation {
    private final int threadCount;
    private final int daemonCount;

    public ThreadInformation(int threadCount, int daemonCount) {
      this.threadCount = threadCount;
      this.daemonCount = daemonCount;
    }

    public int getThreadCount() {
      return threadCount;
    }

    public int getDaemonCount() {
      return daemonCount;
    }
  }

  public static final class ThreadInformationExtractor implements VirtualMachineValueExtractor<Optional<ThreadInformation>> {
    @Override
    public Optional<ThreadInformation> apply(ProxyClient proxyClient) {
      try {
        ThreadMXBean threadBean = proxyClient.getThreadMXBean();
        return Optional.of(new ThreadInformation(threadBean.getThreadCount(), threadBean.getDaemonThreadCount()));
      } catch (IOException e) {
        return Optional.absent();
      }
    }
  }

  public static final class HostNameExtractor implements VirtualMachineValueExtractor<Optional<String>> {
    @Override
    public Optional<String> apply(ProxyClient proxyClient) {
      try {
        String runtimeName = proxyClient.getRuntimeMXBean().getName();
        int indexOfAt = runtimeName.indexOf("@");
        if (indexOfAt == -1) {
          return Optional.absent();
        } else {
          return Optional.of(runtimeName.substring(indexOfAt + 1, runtimeName.length()));
        }
      } catch (IOException e) {
        return Optional.absent();
      }
    }
  }
}
