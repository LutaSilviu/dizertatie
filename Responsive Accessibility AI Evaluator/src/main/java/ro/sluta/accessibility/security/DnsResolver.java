package ro.sluta.accessibility.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@FunctionalInterface
public interface DnsResolver {
    List<InetAddress> resolveAll(String host) throws UnknownHostException;
}
