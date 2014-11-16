package com.github.ferdinandhuebner.jvminfogelfagent;

import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.DetailedGarbageCollectorInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.GarbageCollectorInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.VirtualMachineInformationBuilder;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.GcInfoExtractor.GcInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.HeapInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.NonHeapInformation;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jvmtop.openjdk.tools.ProxyClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

class VirtualMachineInformationCollector {

  private static final Set<String> YOUNG_GC_NAMES;
  private static final Set<String> OLD_GC_NAMES;

  static {
    ImmutableSet.Builder<String> youngGcNamesBuilder = ImmutableSet.builder();
    youngGcNamesBuilder.add("Copy");
    youngGcNamesBuilder.add("PS Scavenge");
    youngGcNamesBuilder.add("ParNew");
    youngGcNamesBuilder.add("G1 Young Generation");
    YOUNG_GC_NAMES = youngGcNamesBuilder.build();
    ImmutableSet.Builder<String> oldGcNamesBuilder = ImmutableSet.builder();
    oldGcNamesBuilder.add("MarkSweepCompact");
    oldGcNamesBuilder.add("PS MarkSweep");
    oldGcNamesBuilder.add("ConcurrentMarkSweep");
    oldGcNamesBuilder.add("G1 Old Generation");
    OLD_GC_NAMES = oldGcNamesBuilder.build();
  }

  private final ProxyClient proxyClient;
  private final AtomicReference<VirtualMachineInformation> vmInfo = new AtomicReference<>();

  private final VirtualMachineValueExtractor<Optional<Long>> cpuTimeExtractor;
  private final VirtualMachineValueExtractor<Optional<GcInformation>> gcInfoExtractor;
  private final VirtualMachineValueExtractor<Optional<HeapInformation>> heapInfoExtractor;
  private final VirtualMachineValueExtractor<Optional<NonHeapInformation>> nonHeapInfoExtractor;
  private final VirtualMachineValueExtractor<Optional<Integer>> loadedClassesExtractor;
  private final VirtualMachineValueExtractor<Optional<VirtualMachineValueExtractors.ThreadInformation>> threadInfoExtractor;
  private final VirtualMachineValueExtractor<Optional<String>> hostNameExtractor;

  public VirtualMachineInformationCollector(ProxyClient proxyClient) {
    this.proxyClient = proxyClient;

    cpuTimeExtractor = VirtualMachineValueExtractors.totalCpuUsed();
    gcInfoExtractor = VirtualMachineValueExtractors.gcInfo();
    heapInfoExtractor = VirtualMachineValueExtractors.heapInfo();
    nonHeapInfoExtractor = VirtualMachineValueExtractors.nonHeapInfo();
    loadedClassesExtractor = VirtualMachineValueExtractors.loadedClasses();
    threadInfoExtractor = VirtualMachineValueExtractors.threadInformation();
    hostNameExtractor = VirtualMachineValueExtractors.hostName();
  }

  VirtualMachineInformation initialize() {
    VirtualMachineInformationBuilder vmInfoBuilder = VirtualMachineInformationBuilder.create();
    vmInfoBuilder.withInformationTime(System.nanoTime());
    vmInfoBuilder.withTotalCpu(cpuTimeExtractor.apply(proxyClient).or(0L));
    vmInfoBuilder.withGcInformation(getInitialGcInfo());
    vmInfoBuilder.withCpuLoad(0);
    HeapInformation heapInfo = heapInfoExtractor.apply(proxyClient).or(new HeapInformation(0, 0, 0));
    vmInfoBuilder.withHeapInformation(heapInfo);
    NonHeapInformation nonHeapInfo = nonHeapInfoExtractor.apply(proxyClient).or(new NonHeapInformation(0, 0, 0));
    vmInfoBuilder.withNonHeapInformation(nonHeapInfo);
    vmInfoBuilder.withLoadedClasses(loadedClassesExtractor.apply(proxyClient).or(0));

    Optional<VirtualMachineValueExtractors.ThreadInformation> threadInfo = threadInfoExtractor.apply(proxyClient);
    if (threadInfo.isPresent()) {
      vmInfoBuilder.withThreadCount(threadInfo.get().getThreadCount());
      vmInfoBuilder.withDaemonThreadCount(threadInfo.get().getDaemonCount());
    } else {
      vmInfoBuilder.withThreadCount(0);
      vmInfoBuilder.withDaemonThreadCount(0);
    }
    vmInfoBuilder.withHostName(hostNameExtractor.apply(proxyClient).or("<unknown>"));

    VirtualMachineInformation machineInformation = vmInfoBuilder.build();
    vmInfo.set(machineInformation);

    return machineInformation;
  }

  private List<String> getYoungGcNames(List<String> gcNames) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String gcName : gcNames) {
      if (YOUNG_GC_NAMES.contains(gcName))
        builder.add(gcName);
    }
    return builder.build();
  }

  private List<String> getOldGcNames(List<String> gcNames) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String gcName : gcNames) {
      if (OLD_GC_NAMES.contains(gcName))
        builder.add(gcName);
    }
    return builder.build();
  }

  private GarbageCollectorInformation getInitialGcInfo() {
    Optional<GcInformation> gcInfoOptional = gcInfoExtractor.apply(proxyClient);
    if (!gcInfoOptional.isPresent()) {
      return new GarbageCollectorInformation(0L, 0L, 0L, 0d);
    }
    GcInformation gcInfo = gcInfoOptional.get();
    List<String> garbageCollectors = gcInfo.getGarbageCollectors();
    List<String> youngGenCollectors = getYoungGcNames(garbageCollectors);
    List<String> oldGenCollectors = getOldGcNames(garbageCollectors);

    GarbageCollectorInformation garbageCollectorInformation;
    if (youngGenCollectors.size() + oldGenCollectors.size() == garbageCollectors.size()) {
      long totalGcYoungNanos = 0L;
      long totalGcYoungEvents = 0L;
      for (String youngGc : youngGenCollectors) {
        totalGcYoungNanos += gcInfo.gcTimeFor(youngGc).or(0L);
        totalGcYoungEvents += gcInfo.gcCountFor(youngGc).or(0L);
      }
      long totalGcOldNanos = 0L;
      long totalGcOldEvents = 0L;
      for (String oldGc : oldGenCollectors) {
        totalGcOldNanos += gcInfo.gcTimeFor(oldGc).or(0L);
        totalGcOldEvents += gcInfo.gcCountFor(oldGc).or(0L);
      }
      long newYoungGenGcEvents = 0L;
      long newOldGenGcEvents = 0L;
      long newGcCounts = 0L;

      double gcLoad = 0.0d;
      double youngGcLoad = 0.0d;
      double oldGcLoad = 0.0d;

      garbageCollectorInformation = new DetailedGarbageCollectorInformation(gcInfo.getTotalGcCount(),
              gcInfo.getTotalGcTimeNanos(),
              newGcCounts, gcLoad, totalGcYoungEvents, newYoungGenGcEvents,
              totalGcOldEvents, newOldGenGcEvents,
              totalGcYoungNanos, youngGcLoad,
              totalGcOldNanos, oldGcLoad,
              youngGenCollectors, oldGenCollectors);
    } else {
      garbageCollectorInformation = new GarbageCollectorInformation(gcInfo.getTotalGcCount(), gcInfo.getTotalGcTimeNanos(), 0L, 0d);
    }
    return garbageCollectorInformation;
  }

  private GarbageCollectorInformation getGcInfo(long deltaT, GarbageCollectorInformation previous) {
    Optional<GcInformation> gcInfoOptional = gcInfoExtractor.apply(proxyClient);
    if (!gcInfoOptional.isPresent()) {
      return new GarbageCollectorInformation(0L, 0L, 0L, 0d);
    }
    GcInformation gcInfo = gcInfoOptional.get();
    List<String> garbageCollectors = gcInfo.getGarbageCollectors();
    List<String> youngGenCollectors = getYoungGcNames(garbageCollectors);
    List<String> oldGenCollectors = getOldGcNames(garbageCollectors);

    GarbageCollectorInformation garbageCollectorInformation;
    if (youngGenCollectors.size() + oldGenCollectors.size() == garbageCollectors.size()
            && previous instanceof DetailedGarbageCollectorInformation) {
      DetailedGarbageCollectorInformation previousDetailed = (DetailedGarbageCollectorInformation) previous;
      long totalGcYoungNanos = 0L;
      long totalGcYoungEvents = 0L;
      for (String youngGc : youngGenCollectors) {
        totalGcYoungNanos += gcInfo.gcTimeFor(youngGc).or(0L);
        totalGcYoungEvents += gcInfo.gcCountFor(youngGc).or(0L);
      }
      long totalGcOldNanos = 0L;
      long totalGcOldEvents = 0L;
      for (String oldGc : oldGenCollectors) {
        totalGcOldNanos += gcInfo.gcTimeFor(oldGc).or(0L);
        totalGcOldEvents += gcInfo.gcCountFor(oldGc).or(0L);
      }
      long newYoungGenGcEvents = totalGcYoungEvents - previousDetailed.totalYoungGcCount;
      long newOldGenGcEvents = totalGcOldEvents - previousDetailed.totalOldGcCount;
      long newGcCounts = (totalGcOldEvents + totalGcYoungEvents) - previousDetailed.totalGcCount;

      long newGcTime = (totalGcYoungNanos + totalGcOldNanos) - previous.totalGcTime;
      long newYoungGcTime = totalGcYoungNanos - previousDetailed.totalYoungGctime;
      long newOldGcTime = totalGcOldNanos - previousDetailed.totalOldGctime;
      double gcLoad = ((double) newGcTime) / deltaT;
      double youngGcLoad = ((double) newYoungGcTime) / deltaT;
      double oldGcLoad = ((double) newOldGcTime) / deltaT;

      garbageCollectorInformation = new DetailedGarbageCollectorInformation(gcInfo.getTotalGcCount(),
              gcInfo.getTotalGcTimeNanos(),
              newGcCounts, gcLoad < 0d ? 0d : gcLoad, totalGcYoungEvents, newYoungGenGcEvents,
              totalGcOldEvents, newOldGenGcEvents,
              totalGcYoungNanos, youngGcLoad < 0d ? 0d : youngGcLoad,
              totalGcOldNanos, oldGcLoad < 0d ? 0d : oldGcLoad,
              youngGenCollectors, oldGenCollectors);
    } else {
      garbageCollectorInformation = new GarbageCollectorInformation(gcInfo.getTotalGcCount(), gcInfo.getTotalGcTimeNanos(), 0L, 0d);
    }
    return garbageCollectorInformation;
  }

  VirtualMachineInformation createVirtualMachineInformation() {
    VirtualMachineInformation last = vmInfo.get();
    VirtualMachineInformationBuilder vmInfoBuilder = VirtualMachineInformationBuilder.create();
    long now = System.nanoTime();
    long deltaT = now - last.informationTime;
    long totalCpuTimeNanos = cpuTimeExtractor.apply(proxyClient).or(0L);
    long deltaCpu = totalCpuTimeNanos - last.totalCpu;

    vmInfoBuilder.withInformationTime(now);
    vmInfoBuilder.withTotalCpu(totalCpuTimeNanos);
    double cpuLoad = ((double) deltaCpu) / deltaT;
    if (cpuLoad < 0d) { // strange things happen...
      cpuLoad = 0d;
    }
    vmInfoBuilder.withCpuLoad(cpuLoad);
    vmInfoBuilder.withGcInformation(getGcInfo(deltaT, last.getGcInformation()));
    HeapInformation heapInfo = heapInfoExtractor.apply(proxyClient).or(new HeapInformation(0, 0, 0));
    vmInfoBuilder.withHeapInformation(heapInfo);
    NonHeapInformation nonHeapInfo = nonHeapInfoExtractor.apply(proxyClient).or(new NonHeapInformation(0, 0, 0));
    vmInfoBuilder.withNonHeapInformation(nonHeapInfo);
    vmInfoBuilder.withLoadedClasses(loadedClassesExtractor.apply(proxyClient).or(0));
    Optional<VirtualMachineValueExtractors.ThreadInformation> threadInfo = threadInfoExtractor.apply(proxyClient);
    if (threadInfo.isPresent()) {
      vmInfoBuilder.withThreadCount(threadInfo.get().getThreadCount());
      vmInfoBuilder.withDaemonThreadCount(threadInfo.get().getDaemonCount());
    } else {
      vmInfoBuilder.withThreadCount(0);
      vmInfoBuilder.withDaemonThreadCount(0);
    }
    vmInfoBuilder.withHostName(hostNameExtractor.apply(proxyClient).or("<unknown>"));

    VirtualMachineInformation value = vmInfoBuilder.build();
    this.vmInfo.set(value);
    return value;
  }
}
