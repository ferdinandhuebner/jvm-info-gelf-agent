package com.github.ferdinandhuebner.jvminfogelfagent;

import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineManager.VirtualMachine;
import com.github.ferdinandhuebner.jvminfogelfagent.VirtualMachineManager.VirtualMachineDescriptor;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.graylog2.gelfclient.*;
import org.graylog2.gelfclient.encoder.GelfMessageJsonEncoder;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class JvmGelfAgent implements Runnable {

  private static final class AppConfig {
    private final String gelfTarget;
    private final Optional<String> applicationName;
    private final Optional<String> deploymentUnit;
    private final int jvmProcessId;
    private final int monitorInterval;

    public AppConfig(int jvmProcessId, String gelfTarget, int monitorInterval, Optional<String> applicationName, Optional<String> deploymentUnit) {
      this.jvmProcessId = jvmProcessId;
      this.monitorInterval = monitorInterval;
      this.gelfTarget = gelfTarget;
      this.applicationName = applicationName;
      this.deploymentUnit = deploymentUnit;
    }

    public static AppConfig fromConfigFile(File configFile) {
      Config config = ConfigFactory.parseFile(configFile);
      if (!config.hasPath("pid")) {
        throw new IllegalArgumentException("JVM process-id not configured (pid)");
      }
      int pid = config.getInt("pid");

      if (!config.hasPath("gelf.target")) {
        throw new IllegalArgumentException("GELF target not configured (gelf.target)");
      }
      String gelfTarget = config.getString("gelf.target");

      if (!config.hasPath("monitor.interval")) {
        throw new IllegalArgumentException("Monitor interval not configured (monitor.interval)");
      }
      int monitorInterval = config.getInt("monitorInterval");

      Optional<String> applicationName;
      if (!config.hasPath("application.name")) {
        applicationName = Optional.absent();
      } else {
        applicationName = Optional.of(config.getString("application.name"));
      }
      Optional<String> deploymentUnit;
      if (!config.hasPath("application.deployment.unit")) {
        deploymentUnit = Optional.absent();
      } else {
        deploymentUnit = Optional.of(config.getString("application.deployment.unit"));
      }
      return new AppConfig(pid, gelfTarget, monitorInterval, applicationName, deploymentUnit);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(JvmGelfAgent.class);
  private final AtomicReference<AppConfig> config = new AtomicReference<>();

  private String asString(List<String> strings) {
    return Joiner.on(", ").join(strings);
  }

  private GelfMessage extractGelf(VirtualMachine vm) throws IOException {
    VirtualMachineInformation vmInfo = vm.getVirtualMachineInformation();
    GelfMessageBuilder gelf = new GelfMessageBuilder("jvm-information", vmInfo.getHostName());

    if (config != null) {
      if (config.get().applicationName.isPresent()) {
        gelf.additionalField("_application", config.get().applicationName.get());
      }
      if (config.get().deploymentUnit.isPresent()) {
        gelf.additionalField("_deployment_unit", config.get().deploymentUnit.get());
      }
    }

    gelf.level(GelfMessageLevel.INFO);
    gelf.timestamp(((double) System.currentTimeMillis()) / 1000);
    gelf.additionalField("_cpu_load", vmInfo.getCpuLoad());
    gelf.additionalField("_daemon_thread_count", vmInfo.getDaemonThreadCount());
    gelf.additionalField("_thread_count", vmInfo.getThreadCount());
    VirtualMachineInformation.GarbageCollectorInformation gcInfo = vmInfo.getGcInformation();
    if (gcInfo instanceof VirtualMachineInformation.DetailedGarbageCollectorInformation) {
      VirtualMachineInformation.DetailedGarbageCollectorInformation gc = (VirtualMachineInformation.DetailedGarbageCollectorInformation) gcInfo;
      gelf.additionalField("_gc_load", gc.getGcLoad());
      gelf.additionalField("_gc_count", gc.getGcCount());
      gelf.additionalField("_gc_old_gen_load", gc.getOldGenerationGcLoad());
      gelf.additionalField("_gc_old_gen_count", gc.getOldGenerationGcCount());
      gelf.additionalField("_gc_old_gen_collectors", asString(gc.getOldGenerationGarbageCollectors()));
      gelf.additionalField("_gc_young_gen_load", gc.getYoungGenerationGcLoad());
      gelf.additionalField("_gc_young_gen_count", gc.getYoungGenerationGcCount());
      gelf.additionalField("_gc_young_gen_collectors", asString(gc.getOldGenerationGarbageCollectors()));
    } else {
      gelf.additionalField("_gc_load", gcInfo.getGcLoad());
      gelf.additionalField("_gc_count", gcInfo.getGcCount());
    }
    VirtualMachineValueExtractors.HeapInformation heapInfo = vmInfo.getHeapInformation();
    gelf.additionalField("_heap_max", heapInfo.getHeapMax());
    gelf.additionalField("_heap_size", heapInfo.getHeapSize());
    gelf.additionalField("_heap_used", heapInfo.getHeapUsed());

    gelf.additionalField("_loaded_classes", vmInfo.getLoadedClasses());

    VirtualMachineValueExtractors.NonHeapInformation nonHeap = vmInfo.getNonHeapInformation();
    gelf.additionalField("_non_heap_max", nonHeap.getNonHeapMax());
    gelf.additionalField("_non_heap_size", nonHeap.getNonHeapSize());
    gelf.additionalField("_non_heap_used", nonHeap.getNonHeapUsed());

    return gelf.build();
  }

  private Optional<AppConfig> getConfig() {
    String appConfigFile = System.getProperty("appConfig");
    if (appConfigFile != null) {
      File configFile = new File(appConfigFile);
      if (!configFile.exists() || !configFile.canRead()) {
        LOG.error("Config-file at path " + appConfigFile + " does not exist or cannot be read");
        return Optional.absent();
      } else {
        try {
          return Optional.of(AppConfig.fromConfigFile(configFile));
        } catch (RuntimeException e) {
          LOG.error("Config-file at path " + appConfigFile + " is not valid: " + e.getMessage());
          return Optional.absent();
        }
      }
    }
    String pidString = System.getProperty("pid");
    if (pidString == null) {
      LOG.error("JVM pid not configured (-Dpid)");
      return Optional.absent();
    }
    int pid = 0;
    try {
      pid = Integer.parseInt(pidString);
    } catch (RuntimeException e) {
      LOG.error("JVM pid is not numeric (-Dpid)");
      return Optional.absent();
    }
    String gelfTarget = System.getProperty("gelf.target");
    if (gelfTarget == null) {
      LOG.error("GELF taret is not configured (-Dgelf.target)");
      return Optional.absent();
    }

    String intervalString = System.getProperty("monitor.interval");
    if (intervalString == null) {
      LOG.error("Monitor interval not configured (-Dmonitor.interval)");
      return Optional.absent();
    }
    int monitorInterval = 0;
    try {
      monitorInterval = Integer.parseInt(intervalString);
    } catch (RuntimeException e) {
      LOG.error("Monitor interval is not numeric (-Dmonitor.interval)");
      return Optional.absent();
    }

    String applicationNameProp = System.getProperty("application.name");
    String deploymentUnitProp = System.getProperty("application.deployment.unit");
    Optional<String> appName = Optional.absent();
    if (applicationNameProp != null)
      appName = Optional.of(applicationNameProp);
    Optional<String> deploymentUnit = Optional.absent();
    if (deploymentUnitProp != null)
      deploymentUnit = Optional.of(deploymentUnitProp);

    return Optional.of(new AppConfig(pid, gelfTarget, monitorInterval, appName, deploymentUnit));
  }

  private Optional<VirtualMachineDescriptor> getVmDescriptor(int pid, VirtualMachineManager vmManager) {
    for (VirtualMachineManager.VirtualMachineDescriptor desc : vmManager.listLocalVirtualMachines()) {
      if (desc.getProcessId() == pid) {
        return Optional.of(desc);
      }
    }
    return Optional.absent();
  }

  private Optional<GelfTransport> getTcpTransport(String gelfTarget) {
    String endpoint = gelfTarget.substring("tcp://".length() + 1);
    if (!endpoint.contains(":")) {
      LOG.error("Invalid TCP endpoint: " + gelfTarget + "(format: tcp://host:port");
      return Optional.absent();
    }
    String[] endpointParts = endpoint.split(":");
    String hostName = endpointParts[0];
    String portString = endpointParts[1];
    try {
      GelfConfiguration config = new GelfConfiguration(new InetSocketAddress(hostName, Integer.parseInt(portString)));
      config.transport(GelfTransports.TCP);
      config.connectTimeout(5000);
      config.reconnectDelay(1000);
      config.queueSize(512);
      config.tcpNoDelay(true);
      config.sendBufferSize(32768);
      GelfTransport transport = GelfTransports.create(config);
      return Optional.of(transport);
    } catch (Exception e) {
      LOG.error("Cannot parse GELF endpoint", e);
      return Optional.absent();
    }
  }

  private Optional<GelfTransport> getUdpTransport(String gelfTarget) {
    String endpoint = gelfTarget.substring("udp://".length() + 1);
    if (!endpoint.contains(":")) {
      LOG.error("Invalid UDP endpoint: " + gelfTarget + "(format: udp://host:port");
      return Optional.absent();
    }
    String[] endpointParts = endpoint.split(":");
    String hostName = endpointParts[0];
    String portString = endpointParts[1];
    try {
      GelfConfiguration config = new GelfConfiguration(new InetSocketAddress(hostName, Integer.parseInt(portString)));
      config.transport(GelfTransports.UDP);
      config.queueSize(512);
      GelfTransport transport = GelfTransports.create(config);
      return Optional.of(transport);
    } catch (Exception e) {
      LOG.error("Cannot parse GELF endpoint", e);
      return Optional.absent();
    }
  }

  private Optional<GelfTransport> getStdOutTransport() {
    GelfTransport transport = new GelfTransport() {
      GelfMessageJsonEncoder encoder = new GelfMessageJsonEncoder();

      private String asString(GelfMessage message) {
        try {
          Method method = encoder.getClass().getDeclaredMethod("toJson", GelfMessage.class);
          method.setAccessible(true);
          byte[] asBytes = (byte[]) method.invoke(encoder, message);
          return new String(asBytes, Charset.forName("UTF-8"));
        } catch (Exception e) {
          return "<error>";
        }
      }

      @Override
      public void send(GelfMessage message) throws InterruptedException {
        System.out.println(asString(message));
      }

      @Override
      public boolean trySend(GelfMessage message) {
        System.out.println(asString(message));
        return true;
      }

      @Override
      public void stop() {
        //
      }
    };
    return Optional.of(transport);
  }

  private Optional<GelfTransport> getTransport() {
    String gelfTarget = config.get().gelfTarget;
    if (gelfTarget.startsWith("tcp://")) {
      return getTcpTransport(gelfTarget);
    } else if (gelfTarget.startsWith("udp://")) {
      return getUdpTransport(gelfTarget);
    } else if (gelfTarget.startsWith("stdout://")) {
      return getStdOutTransport();
    }
    LOG.error("Unrecognized protocol in GELF transport: " + gelfTarget);
    return Optional.absent();
  }

  private void stopTransport(GelfTransport transport) {
    try {
      transport.stop();
    } catch (RuntimeException e) {
      LOG.warn("Cannot stop GELF transport", e);
    }
  }

  @Override
  public void run() {
    /*
    System.setProperty("pid", "27826");
    System.setProperty("gelf.target", "stdout://");
    System.setProperty("monitor.interval", "1");
    System.setProperty("application.name", "IntelliJ IDEA");
    System.setProperty("application.deployment.unit", "IDE");
    */

    Optional<AppConfig> appConfig = getConfig();
    if (!appConfig.isPresent()) {
      System.exit(1);
    }
    config.set(appConfig.get());

    Optional<GelfTransport> gelfTransportOptional = getTransport();
    if (!gelfTransportOptional.isPresent()) {
      System.exit(1);
    }
    GelfTransport transport = gelfTransportOptional.get();

    VirtualMachineManager vmManager = new VirtualMachineManager();
    Optional<VirtualMachineDescriptor> vmDescriptor = getVmDescriptor(config.get().jvmProcessId, vmManager);
    if (!vmDescriptor.isPresent()) {
      LOG.error("No JVM with PID " + config.get().jvmProcessId + " found.");
      System.exit(1);
    }

    VirtualMachine vm = null;
    try {
      vm = vmManager.getVirtualMachine(vmDescriptor.get());
      LOG.info("Attaching to JVM: " + vmDescriptor.get().getDisplayName());
      vm.attach();

      while (vm.isAttached()) {
        GelfMessage gelfMessage = extractGelf(vm);
        if (!transport.trySend(gelfMessage)) {
          LOG.warn("Discarded GELF message (queue limit reached)");
        }
        Thread.sleep(config.get().monitorInterval * 1000);
      }
      LOG.info("Connection to JVM lost!");
      vm.detach();
    } catch (IOException | InterruptedException e) {
      LOG.error("Connection to JVM lost!", e);
    } finally {
      stopTransport(transport);
      if (vm != null) {
        try {
          vm.detach();
        } catch (IOException e) {
          //
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    new JvmGelfAgent().run();
  }
}
