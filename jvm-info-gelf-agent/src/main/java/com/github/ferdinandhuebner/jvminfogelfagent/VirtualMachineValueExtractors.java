package com.github.ferdinandhuebner.jvminfogelfagent;

import com.google.common.base.Optional;
import com.jvmtop.openjdk.tools.ProxyClient;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Collection of {@link com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractor} objects.
 */
public class VirtualMachineValueExtractors {

  public static TotalCpuTimeNanosUsedExtractor totalCpuUsed() {
    return new TotalCpuTimeNanosUsedExtractor();
  }
  public static TotalGcTimeUsedNanosExtractor totalGcTime() {
    return new TotalGcTimeUsedNanosExtractor();
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
   * Extracts the total CPU time used for garbage collection from a virtual machine.
   */
  public static final class TotalGcTimeUsedNanosExtractor implements VirtualMachineValueExtractor<Optional<Long>> {
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
    public Optional<Long> apply(ProxyClient proxyClient) {
      return getTotalGcTimeNanos(proxyClient);
    }
  }

}
