package com.github.ferdinandhuebner.jvminfogelfagent;

public final class VirtualMachineInformation {

  static final class VirtualMachineInformationBuilder {
    private Double cpuLoad;
    private Double gcLoad;
    private Long totalCpuTime;
    private Long totalGcTime;
    private Long informationTime;

    static VirtualMachineInformationBuilder create() {
      return new VirtualMachineInformationBuilder();
    }

    public VirtualMachineInformationBuilder withCpuLoad(double cpuLoad) {
      this.cpuLoad = cpuLoad;
      return this;
    }

    public VirtualMachineInformationBuilder withGcLoad(double gcLoad) {
      this.gcLoad = gcLoad;
      return this;
    }

    public VirtualMachineInformationBuilder withTotalCpu(long totalCpuTime) {
      this.totalCpuTime = totalCpuTime;
      return this;
    }
    public VirtualMachineInformationBuilder withTotalGcTime(long totalGcTime) {
      this.totalGcTime = totalGcTime;
      return this;
    }

    public VirtualMachineInformationBuilder withInformationTime(long informationTime) {
      this.informationTime = informationTime;
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
              validateNotNull(gcLoad, "gcLoad"),
              validateNotNull(totalCpuTime, "totalCpuTime"),
              validateNotNull(totalGcTime, "totalGcTime"));
    }

  }

  private final double cpuLoad;
  private final double gcLoad;
  final long informationTime;
  final long totalCpu;
  final long totalGcTime;

  VirtualMachineInformation(long informationTime, double cpuLoad, double gcLoad, long totalCpu, long totalGcTime) {
    this.informationTime = informationTime;
    this.cpuLoad = cpuLoad;
    this.gcLoad = gcLoad;
    this.totalCpu = totalCpu;
    this.totalGcTime = totalGcTime;
  }

  public double getCpuLoad() {
    return cpuLoad;
  }

  public double getGcLoad() {
    return gcLoad;
  }
}
