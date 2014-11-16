package com.github.ferdinandhuebner.jvminfogelfagent;

import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineValueExtractors.GcInfoExtractor.GcInformation;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineInformation.VirtualMachineInformationBuilder;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.jvmtop.openjdk.tools.ConnectionState;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;
import com.jvmtop.openjdk.tools.ProxyClient;
import com.sun.tools.attach.AttachNotSupportedException;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Convenience-Class to handle discovery and attachment to local virtual machines.
 * Wraps all com.sun classes into more convenient ones.
 */
public class VirtualMachineManager {

  private static final String LOCAL_CONNECTOR_ADDRESS_PROP = "com.sun.management.jmxremote.localConnectorAddress";

  /**
   * Basic descriptor for a virtual machine.
   */
  public static final class VirtualMachineDescriptor {
    private final com.sun.tools.attach.VirtualMachineDescriptor comSunDescriptor;
    private final int processId;
    private final String displayName;
    private final boolean attachable;

    private VirtualMachineDescriptor(com.sun.tools.attach.VirtualMachineDescriptor comSunDescriptor,
                                     int processId, String displayName, boolean attachable) {
      this.comSunDescriptor = comSunDescriptor;
      this.processId = processId;
      this.displayName = displayName;
      this.attachable = attachable;
    }

    /**
     * Returns the process ID of the virtual machine.
     */
    public int getProcessId() {
      return processId;
    }

    /**
     * Returns the display name of the virtual machine.
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Returns {@code true} if this virtual machine can be attached to, {@code false} otherwise.
     */
    public boolean isAttachable() {
      return attachable;
    }
  }

  /**
   * Representation of a virtual machine that can be monitored.
   */
  public static final class VirtualMachine {
    private final LocalVirtualMachine localVm;
    private final ProxyClient proxyClient;
    private final AtomicReference<VirtualMachineInformation> vmInfo = new AtomicReference<>();

    private final VirtualMachineValueExtractor<Optional<Long>> cpuTimeExtractor;
    private final VirtualMachineValueExtractor<Optional<GcInformation>> gcTimeExtractor;

    private VirtualMachine(LocalVirtualMachine localVm, ProxyClient proxyClient) {
      this.localVm = localVm;
      this.proxyClient = proxyClient;

      cpuTimeExtractor = VirtualMachineValueExtractors.totalCpuUsed();
      gcTimeExtractor = VirtualMachineValueExtractors.gcInfo();
    }

    private VirtualMachineInformation initialVirtualMachineInformation() {
      VirtualMachineInformationBuilder vmInfo = VirtualMachineInformationBuilder.create();
      vmInfo.withInformationTime(System.nanoTime());
      vmInfo.withTotalCpu(cpuTimeExtractor.apply(proxyClient).or(0L));
      Optional<GcInformation> gcInfo = gcTimeExtractor.apply(proxyClient);
      if (gcInfo.isPresent()) {
        GcInformation info = gcInfo.get();
        vmInfo.withTotalGcTime(info.getTotalGcTimeNanos());
      } else {
        vmInfo.withTotalGcTime(0L);
      }
      vmInfo.withCpuLoad(0);
      vmInfo.withGcLoad(0);
      return vmInfo.build();
    }

    private VirtualMachineInformation createVirtualMachineInformation() {
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

    public VirtualMachineInformation getVirtualMachineInformation() {
      VirtualMachineInformation info = createVirtualMachineInformation();
      vmInfo.set(info);
      return info;
    }

    /**
     * Tries to attach to this virtual machine.
     *
     * @throws IOException if the attachment-attempt was not successful
     */
    public void attach() throws IOException {
      try {
        proxyClient.connect();
        if (proxyClient.getConnectionState() == ConnectionState.DISCONNECTED) {
          throw new IOException("Connection to virtual machine with PID " + localVm.vmid() + " refused");
        }
        vmInfo.set(initialVirtualMachineInformation());
      } catch (Exception e) {
        throw new IOException("Unable to connect to virtual machine with PID " + localVm.vmid(), e);
      }
    }

    /**
     * Detaches from this virtual machine.
     *
     * @throws IOException if the detachment-attempt was not successful
     */
    public void detach() throws IOException {
      proxyClient.disconnect();
    }
  }

  private void detach(com.sun.tools.attach.VirtualMachine vm) {
    try {
      if (vm != null) {
        vm.detach();
      }
    } catch (IOException e) {
      //
    }
  }

  private boolean canAttach(com.sun.tools.attach.VirtualMachineDescriptor vmDescriptor) {
    com.sun.tools.attach.VirtualMachine vm = null;
    try {
      vm = com.sun.tools.attach.VirtualMachine.attach(vmDescriptor);
      return true;
    } catch (AttachNotSupportedException | IOException e) {
      return false;
    } finally {
      detach(vm);
    }
  }

  private LocalVirtualMachine asLocalVirtualMachine(VirtualMachineDescriptor vmDescriptor) {
    com.sun.tools.attach.VirtualMachine vm = null;
    try {
      vm = com.sun.tools.attach.VirtualMachine.attach(vmDescriptor.comSunDescriptor);
      Properties agentProps = vm.getAgentProperties();
      String address = (String) agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP);
      int processId = vmDescriptor.getProcessId();
      String displayName = vmDescriptor.comSunDescriptor.displayName();
      return new LocalVirtualMachine(processId, displayName, true, address);
    } catch (AttachNotSupportedException | IOException e) {
      throw new IllegalArgumentException("Unable to attach to virtual machine with PID "
              + vmDescriptor.getProcessId(), e);
    }
  }

  /**
   * Returns a list of locally running virtual machines.
   */
  public List<VirtualMachineDescriptor> listLocalVirtualMachines() {
    List<com.sun.tools.attach.VirtualMachineDescriptor> vms = com.sun.tools.attach.VirtualMachine.list();
    ImmutableList.Builder<VirtualMachineDescriptor> localVms = ImmutableList.builder();
    for (com.sun.tools.attach.VirtualMachineDescriptor vmDescriptor : vms) {
      int processId = Integer.parseInt(vmDescriptor.id());
      String displayName = vmDescriptor.displayName();
      boolean attachable = canAttach(vmDescriptor);
      localVms.add(new VirtualMachineDescriptor(vmDescriptor, processId, displayName, attachable));
    }
    return localVms.build();
  }

  /**
   * Returns a {@link VirtualMachine} object for the give {@link VirtualMachineDescriptor}.
   *
   * @param vmDescriptor the descriptor, must not be {@code null}.
   * @throws IOException the process of creating a {@code VirtualMachine} handle requires attaching to the virtual
   *                     machine which might fail. If it does, an {@code IOException} is thrown.
   */
  public VirtualMachine getVirtualMachine(VirtualMachineDescriptor vmDescriptor) throws IOException {
    LocalVirtualMachine localVm = asLocalVirtualMachine(vmDescriptor);
    ProxyClient proxyClient = ProxyClient.getProxyClient(localVm);
    return new VirtualMachine(localVm, proxyClient);
  }

  public static void main(String[] args) throws Exception {
    VirtualMachineManager vmManager = new VirtualMachineManager();
    VirtualMachineDescriptor vmDescriptor = vmManager.listLocalVirtualMachines().get(0);
    VirtualMachine vm = vmManager.getVirtualMachine(vmDescriptor);
    vm.attach();
    System.out.println(vmDescriptor.comSunDescriptor);
    for (int i = 0; i < 500000; i++) {
      VirtualMachineInformation vmInfo = vm.getVirtualMachineInformation();
      System.out.println("CPU-Load: " + vmInfo.getCpuLoad());
      System.out.println("GC-Load : " + vmInfo.getGcLoad());
      Thread.sleep(500);
    }
    vm.detach();
  }

}
