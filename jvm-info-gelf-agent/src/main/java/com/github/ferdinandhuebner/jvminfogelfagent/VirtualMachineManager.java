package com.github.ferdinandhuebner.jvminfogelfagent;

import com.google.common.collect.ImmutableList;
import com.jvmtop.openjdk.tools.ConnectionState;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;
import com.jvmtop.openjdk.tools.ProxyClient;
import com.sun.tools.attach.AttachNotSupportedException;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Properties;

/**
 * Convenience-Class to handle discovery and attachment to local virtual machines.
 * Wraps all com.sun classes into more convenient ones.
 *
 * TODO: Use JVisualVM code instead of jvmtop/jconsole
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
    private final VirtualMachineInformationCollector vmInfoCollector;

    private VirtualMachine(LocalVirtualMachine localVm, ProxyClient proxyClient) {
      this.localVm = localVm;
      this.proxyClient = proxyClient;
      vmInfoCollector = new VirtualMachineInformationCollector(proxyClient);
    }


    public VirtualMachineInformation getVirtualMachineInformation() {
      proxyClient.flush();
      return vmInfoCollector.createVirtualMachineInformation();
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
        proxyClient.flush();
        vmInfoCollector.initialize();
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

    DecimalFormat formatter = new DecimalFormat("#0.00");
    DecimalFormat longFormatter = new DecimalFormat("#0");
    longFormatter.setGroupingSize(3);
    longFormatter.setGroupingUsed(true);


    VirtualMachineManager vmManager = new VirtualMachineManager();
    VirtualMachineDescriptor vmDescriptor = null;
    for (VirtualMachineDescriptor desc : vmManager.listLocalVirtualMachines()) {
      if (desc.getDisplayName().contains("ntelli")) {
        vmDescriptor = desc;
      }
    }
    VirtualMachine vm = vmManager.getVirtualMachine(vmDescriptor);
    vm.attach();
    System.out.println(vmDescriptor.comSunDescriptor);
    for (int i = 0; i < 500000; i++) {
      VirtualMachineInformation vmInfo = vm.getVirtualMachineInformation();
      String out = "CPU: " + formatter.format(vmInfo.getCpuLoad()) + ", ";
      VirtualMachineInformation.GarbageCollectorInformation gcInfo = vmInfo.getGcInformation();
      if (gcInfo instanceof VirtualMachineInformation.DetailedGarbageCollectorInformation) {
        VirtualMachineInformation.DetailedGarbageCollectorInformation detailedGcInfo = (VirtualMachineInformation.DetailedGarbageCollectorInformation) gcInfo;
        out += "GC: " + formatter.format(detailedGcInfo.getGcLoad()) + " (";
        out += "young: " + formatter.format(detailedGcInfo.getYoungGenerationGcLoad()) + ", " + detailedGcInfo.getYoungGenerationGcCount() + " events, ";
        out += "old: " + formatter.format(detailedGcInfo.getOldGenerationGcLoad()) + ", " + detailedGcInfo.getOldGenerationGcCount() + " events)";
        out += " young gc: " + detailedGcInfo.getYoungGenerationGarbageCollectors() + ", old gc: " + detailedGcInfo.getOldGenerationGarbageCollectors();
      } else {
        out += "GC: " + formatter.format(gcInfo.getGcLoad()) + " (" + gcInfo.getGcCount() + " gc events)";
      }
      VirtualMachineValueExtractors.HeapInformation heap = vmInfo.getHeapInformation();
      out += "\nHeap: " + longFormatter.format(heap.getHeapUsed()) + " / " + longFormatter.format(heap.getHeapSize());
      out += " (" + longFormatter.format(heap.getHeapMax()) + " max)";
      VirtualMachineValueExtractors.NonHeapInformation nonHeap = vmInfo.getNonHeapInformation();
      out += "\nNon-Heap: " + longFormatter.format(nonHeap.getNonHeapUsed()) + " / " + longFormatter.format(nonHeap.getNonHeapSize());
      out += " (" + longFormatter.format(nonHeap.getNonHeapMax()) + " max)\n";
      out += "Loaded classes: " + vmInfo.getLoadedClasses() + "\n";
      out += "Live threads: " + vmInfo.getThreadCount() + " (" + vmInfo.getDaemonThreadCount() + " daemon threads)";
      System.out.println(out);
      Thread.sleep(5000);
    }
    vm.detach();
  }

}
