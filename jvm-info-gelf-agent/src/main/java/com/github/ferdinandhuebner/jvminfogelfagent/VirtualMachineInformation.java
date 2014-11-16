package com.github.ferdinandhuebner.jvminfogelfagent;

import java.util.List;

public final class VirtualMachineInformation {

  static final class VirtualMachineInformationBuilder {
    private Double cpuLoad;
    private GarbageCollectorInformation gcInfo;
    private Long totalCpuTime;
    private Long informationTime;
    private VirtualMachineValueExtractors.HeapInformation heapInformation;
    private VirtualMachineValueExtractors.NonHeapInformation nonHeapInformation;
    private Integer loadedClasses;
    private Integer threadCount;
    private Integer daemonThreadCount;
    private String hostName;

    static VirtualMachineInformationBuilder create() {
      return new VirtualMachineInformationBuilder();
    }

    public VirtualMachineInformationBuilder withCpuLoad(double cpuLoad) {
      this.cpuLoad = cpuLoad;
      return this;
    }

    public VirtualMachineInformationBuilder withTotalCpu(long totalCpuTime) {
      this.totalCpuTime = totalCpuTime;
      return this;
    }

    public VirtualMachineInformationBuilder withInformationTime(long informationTime) {
      this.informationTime = informationTime;
      return this;
    }

    public VirtualMachineInformationBuilder withGcInformation(GarbageCollectorInformation gcInfo) {
      this.gcInfo = gcInfo;
      return this;
    }

    public VirtualMachineInformationBuilder withHeapInformation(VirtualMachineValueExtractors.HeapInformation heapInformation) {
      this.heapInformation = heapInformation;
      return this;
    }

    public VirtualMachineInformationBuilder withNonHeapInformation(VirtualMachineValueExtractors.NonHeapInformation nonHeapInformation) {
      this.nonHeapInformation = nonHeapInformation;
      return this;
    }

    public VirtualMachineInformationBuilder withLoadedClasses(int loadedClasses) {
      this.loadedClasses = loadedClasses;
      return this;
    }

    public VirtualMachineInformationBuilder withThreadCount(int threadCount) {
      this.threadCount = threadCount;
      return this;
    }

    public VirtualMachineInformationBuilder withDaemonThreadCount(int daemonThreadCount) {
      this.daemonThreadCount = daemonThreadCount;
      return this;
    }

    public VirtualMachineInformationBuilder withHostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    private <T> T validateNotNull(T toValidate, String name) {
      if (toValidate == null)
        throw new IllegalArgumentException(name + " is null");
      return toValidate;
    }

    public VirtualMachineInformation build() {
      return new VirtualMachineInformation(
              validateNotNull(informationTime, "informationTime"),
              validateNotNull(cpuLoad, "cpuLoad"),
              validateNotNull(gcInfo, "gcInfo"),
              validateNotNull(totalCpuTime, "totalCpuTime"),
              validateNotNull(heapInformation, "heapInformation"),
              validateNotNull(nonHeapInformation, "nonHeapInformation"),
              validateNotNull(loadedClasses, "loadedClasses"),
              validateNotNull(threadCount, "threadCount"),
              validateNotNull(daemonThreadCount, "daemonThreadCount"),
              validateNotNull(hostName, "hostName"));
    }
  }

  /**
   * Basic garbage collector information.
   */
  public static class GarbageCollectorInformation {
    final long totalGcCount;
    final long totalGcTime;
    private long gcCount;
    private final double gcLoad;

    public GarbageCollectorInformation(long totalGcCount, long totalGcTime, long gcCount, double gcLoad) {
      this.totalGcCount = totalGcCount;
      this.totalGcTime = totalGcTime;
      this.gcCount = gcCount;
      this.gcLoad = gcLoad;
    }

    /**
     * Returns the total (young and old) number of gc evens.
     */
    long getGcCount() {
      return gcCount;
    }

    /**
     * Returns the total (young and old) gc-load (number of cpus used).
     */
    double getGcLoad() {
      return gcLoad;
    }
  }

  /**
   * Detailed garbage collector information contains separate information for young and old generation events.
   */
  public static class DetailedGarbageCollectorInformation extends GarbageCollectorInformation {
    final long totalYoungGcCount;
    private final long youngGcCount;
    final long totalOldGcCount;
    private final long oldGcCount;
    final long totalYoungGctime;
    private final double youngGcLoad;
    final long totalOldGctime;
    private final double oldGcLoad;
    private final List<String> youngGcNames;
    private final List<String> oldGcNames;

    public DetailedGarbageCollectorInformation(long totalGcCount, long totalGcTime, long gcCount, double gcLoad,
                                               long totalYoungGcCount, long youngGcCount,
                                               long totalOldGcCount, long oldGcCount,
                                               long totalYoungGctime, double youngGcLoad,
                                               long totalOldGctime, double oldGcLoad,
                                               List<String> youngGcNames, List<String> oldGcNames) {
      super(totalGcCount, totalGcTime, gcCount, gcLoad);
      this.totalYoungGcCount = totalYoungGcCount;
      this.youngGcCount = youngGcCount;
      this.totalOldGcCount = totalOldGcCount;
      this.oldGcCount = oldGcCount;
      this.totalYoungGctime = totalYoungGctime;
      this.youngGcLoad = youngGcLoad;
      this.totalOldGctime = totalOldGctime;
      this.oldGcLoad = oldGcLoad;
      this.youngGcNames = youngGcNames;
      this.oldGcNames = oldGcNames;
    }

    /**
     * Returns the number of gc evens for the young generation.
     */
    long getYoungGenerationGcCount() {
      return youngGcCount;
    }

    /**
     * Returns the number of gc evens for the old generation.
     */
    long getOldGenerationGcCount() {
      return oldGcCount;
    }

    /**
     * Returns the gc-load (number of cpus used) for young generation gc events.
     */
    double getYoungGenerationGcLoad() {
      return youngGcLoad;
    }

    /**
     * Returns the gc-load (number of cpus used) for old generation gc events.
     */
    double getOldGenerationGcLoad() {
      return oldGcLoad;
    }

    /**
     * Returns the name of the young generation garabge collector in use.
     */
    List<String> getYoungGenerationGarbageCollectors() {
      return youngGcNames;
    }

    /**
     * Returns the name of the old generation garabge collector in use.
     */
    List<String> getOldGenerationGarbageCollectors() {
      return oldGcNames;
    }
  }

  private final double cpuLoad;
  private final GarbageCollectorInformation gcInformation;
  private final VirtualMachineValueExtractors.HeapInformation heapInformation;
  private final VirtualMachineValueExtractors.NonHeapInformation nonHeapInformation;
  private final int loadedClasses;
  private final int threadCount;
  private final int daemonThreadCount;
  private final String hostName;

  final long informationTime;
  final long totalCpu;

  VirtualMachineInformation(long informationTime, double cpuLoad,
                            GarbageCollectorInformation gcInformation, long totalCpu,
                            VirtualMachineValueExtractors.HeapInformation heapInformation,
                            VirtualMachineValueExtractors.NonHeapInformation nonHeapInformation,
                            int loadedClasses, int threadCount, int daemonThreadCount,
                            String hostName) {

    this.cpuLoad = cpuLoad;
    this.gcInformation = gcInformation;
    this.heapInformation = heapInformation;
    this.nonHeapInformation = nonHeapInformation;
    this.loadedClasses = loadedClasses;
    this.threadCount = threadCount;
    this.daemonThreadCount = daemonThreadCount;
    this.hostName = hostName;

    this.informationTime = informationTime;
    this.totalCpu = totalCpu;
  }

  public double getCpuLoad() {
    return cpuLoad;
  }

  public int getLoadedClasses() {
    return this.loadedClasses;
  }

  public GarbageCollectorInformation getGcInformation() {
    return gcInformation;
  }

  public VirtualMachineValueExtractors.HeapInformation getHeapInformation() {
    return heapInformation;
  }

  public VirtualMachineValueExtractors.NonHeapInformation getNonHeapInformation() {
    return nonHeapInformation;
  }

  public int getThreadCount() {
    return threadCount;
  }

  public int getDaemonThreadCount() {
    return daemonThreadCount;
  }

  public String getHostName() {
    return hostName;
  }
}
