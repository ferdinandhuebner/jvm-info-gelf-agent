package com.github.ferdinandhuebner.jvminfogelfagent;

import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.DetailedGarbageCollectorInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.GarbageCollectorInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.VirtualMachineInformationBuilder;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.GcInfoExtractor.GcInformation;
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

  public VirtualMachineInformationCollector(ProxyClient proxyClient) {
    this.proxyClient = proxyClient;

    cpuTimeExtractor = VirtualMachineValueExtractors.totalCpuUsed();
    gcInfoExtractor = VirtualMachineValueExtractors.gcInfo();
  }

  VirtualMachineInformation initialize() {
    VirtualMachineInformationBuilder vmInfoBuilder = VirtualMachineInformationBuilder.create();
    vmInfoBuilder.withInformationTime(System.nanoTime());
    vmInfoBuilder.withTotalCpu(cpuTimeExtractor.apply(proxyClient).or(0L));
    vmInfoBuilder.withGcInformation(getInitialGcInfo());
    vmInfoBuilder.withCpuLoad(0);

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
      garbageCollectorInformation = new DetailedGarbageCollectorInformation(gcInfo.getTotalGcCount(),
              gcInfo.getTotalGcTimeNanos(),
              0L, 0.0d, totalGcYoungEvents, 0L, totalGcOldEvents, 0L,
              totalGcYoungNanos, 0d, totalGcOldNanos, 0d, youngGenCollectors, oldGenCollectors);
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
      long newYoungGenGcEvents = totalGcYoungEvents - previousDetailed.getYoungGenerationGcCount();
      long newOldGenGcEvents = totalGcOldEvents - previousDetailed.getOldGenerationGcCount();
      long newGcCounts = (totalGcOldEvents + totalGcYoungNanos) - previousDetailed.totalOldGcCount;

      long newGcTime = (totalGcYoungNanos + totalGcOldNanos) - previous.totalGcTime;
      long newYoungGcTime = totalGcYoungNanos - previousDetailed.totalYoungGctime;
      long newOldGcTime = totalGcOldNanos - previousDetailed.totalOldGctime;
      double gcLoad = ((double) newGcTime) / deltaT;
      double youngGcLoad = ((double) newYoungGcTime) / deltaT;
      double oldGcLoad = ((double) newOldGcTime) / deltaT;

      garbageCollectorInformation = new DetailedGarbageCollectorInformation(gcInfo.getTotalGcCount(),
              gcInfo.getTotalGcTimeNanos(),
              newGcCounts, gcLoad, totalGcYoungEvents, newYoungGenGcEvents, totalGcOldEvents, newOldGenGcEvents,
              totalGcYoungNanos, youngGcLoad, totalGcOldNanos, oldGcLoad, youngGenCollectors, oldGenCollectors);
    } else {
      garbageCollectorInformation = new GarbageCollectorInformation(gcInfo.getTotalGcCount(), gcInfo.getTotalGcTimeNanos(), 0L, 0d);
    }
    return garbageCollectorInformation;
  }

  VirtualMachineInformation createVirtualMachineInformation() {
    VirtualMachineInformation last = vmInfo.get();
    VirtualMachineInformationBuilder vmInfo = VirtualMachineInformationBuilder.create();
    long now = System.nanoTime();
    long deltaT = now - last.informationTime;
    long totalCpuTimeNanos = cpuTimeExtractor.apply(proxyClient).or(0L);
    long deltaCpu = totalCpuTimeNanos - last.totalCpu;

    vmInfo.withInformationTime(now);
    vmInfo.withTotalCpu(totalCpuTimeNanos);
    double cpuLoad = ((double) deltaCpu) / deltaT;
    if (cpuLoad < 0d) { // strange things happen...
      cpuLoad = 0d;
    }
    vmInfo.withCpuLoad(cpuLoad);
    vmInfo.withGcInformation(getGcInfo(deltaT, last.getGcInformation()));

    return vmInfo.build();
  }
}
