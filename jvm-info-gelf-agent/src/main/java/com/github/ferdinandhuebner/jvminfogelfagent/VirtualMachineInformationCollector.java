package com.github.ferdinandhuebner.jvminfogelfagent;

import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.VirtualMachineInformationBuilder;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.GcInfoExtractor.GcInformation;
import com.google.common.base.Optional;
import com.jvmtop.openjdk.tools.ProxyClient;

import java.util.concurrent.atomic.AtomicReference;

class VirtualMachineInformationCollector {

  private final ProxyClient proxyClient;
  private final AtomicReference<VirtualMachineInformation> vmInfo = new AtomicReference<>();

  private final VirtualMachineValueExtractor<Optional<Long>> cpuTimeExtractor;
  private final VirtualMachineValueExtractor<Optional<GcInformation>> gcTimeExtractor;

  public VirtualMachineInformationCollector(ProxyClient proxyClient) {
    this.proxyClient = proxyClient;

    cpuTimeExtractor = VirtualMachineValueExtractors.totalCpuUsed();
    gcTimeExtractor = VirtualMachineValueExtractors.gcInfo();
  }

  VirtualMachineInformation initialize() {
    VirtualMachineInformationBuilder vmInfoBuilder = VirtualMachineInformationBuilder.create();
    vmInfoBuilder.withInformationTime(System.nanoTime());
    vmInfoBuilder.withTotalCpu(cpuTimeExtractor.apply(proxyClient).or(0L));
    Optional<VirtualMachineValueExtractors.GcInfoExtractor.GcInformation> gcInfo = gcTimeExtractor.apply(proxyClient);
    if (gcInfo.isPresent()) {
      VirtualMachineValueExtractors.GcInfoExtractor.GcInformation info = gcInfo.get();
      vmInfoBuilder.withTotalGcTime(info.getTotalGcTimeNanos());
    } else {
      vmInfoBuilder.withTotalGcTime(0L);
    }
    vmInfoBuilder.withCpuLoad(0);
    vmInfoBuilder.withGcLoad(0);

    VirtualMachineInformation machineInformation = vmInfoBuilder.build();
    vmInfo.set(machineInformation);

    return machineInformation;
  }

  VirtualMachineInformation createVirtualMachineInformation() {
    VirtualMachineInformation last = vmInfo.get();
    VirtualMachineInformationBuilder vmInfo = VirtualMachineInformationBuilder.create();
    long now = System.nanoTime();
    long deltaT = now - last.informationTime;
    long totalCpuTimeNanos = cpuTimeExtractor.apply(proxyClient).or(0L);

    long totalGcTimeNanos = 0L;
    Optional<GcInformation> gcInfo = gcTimeExtractor.apply(proxyClient);
    if (gcInfo.isPresent()) {
      GcInformation info = gcInfo.get();
      totalGcTimeNanos = info.getTotalGcTimeNanos();
    }
    long deltaCpu = totalCpuTimeNanos - last.totalCpu;
    long deltaGc = totalGcTimeNanos - last.totalGcTime;

    vmInfo.withInformationTime(now);
    vmInfo.withTotalCpu(totalCpuTimeNanos);
    vmInfo.withTotalGcTime(totalGcTimeNanos);
    double cpuLoad = ((double) deltaCpu) / deltaT;
    double gcLoad = ((double) deltaGc) / deltaT;
    if (cpuLoad < 0d) { // strange things happen...
      cpuLoad = 0d;
    }
    if (gcLoad < 0d) { // strange things happen...
      gcLoad = 0d;
    }
    vmInfo.withCpuLoad(cpuLoad);
    vmInfo.withGcLoad(gcLoad);

    return vmInfo.build();
  }
}
