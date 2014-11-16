package com.github.ferdinandhuebner.jvminfogelfagent;

import java.util.List;

public final class VirtualMachineInformation {

  static final class VirtualMachineInformationBuilder {
    private Double cpuLoad;
    private GarbageCollectorInformation gcInfo;
    private Long totalCpuTime;
    private Long informationTime;

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
              validateNotNull(totalCpuTime, "totalCpuTime"));
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

  final long informationTime;
  final long totalCpu;

  VirtualMachineInformation(long informationTime, double cpuLoad,
                            GarbageCollectorInformation gcInformation, long totalCpu) {

    this.cpuLoad = cpuLoad;
    this.gcInformation = gcInformation;

    this.informationTime = informationTime;
    this.totalCpu = totalCpu;
  }

  public double getCpuLoad() {
    return cpuLoad;
  }

  public GarbageCollectorInformation getGcInformation() {
    return gcInformation;
  }
}
