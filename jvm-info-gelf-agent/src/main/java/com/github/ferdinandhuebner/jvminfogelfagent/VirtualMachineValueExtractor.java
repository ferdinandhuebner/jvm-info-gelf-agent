package com.github.ferdinandhuebner.jvminfogelfagent;

import com.jvmtop.openjdk.tools.ProxyClient;

import java.io.IOException;

/**
 * Interface for classes that extract values from a virtual machine using a {@link ProxyClient}.
 *
 * @param <T> type of the value that this extractor can extract.
 */
public interface VirtualMachineValueExtractor<T> {

  /**
   * Extracts a value from the given {@link ProxyClient}.
   * @param proxyClient the proxy client, never {@code null}
   */
  T apply(ProxyClient proxyClient);

}
