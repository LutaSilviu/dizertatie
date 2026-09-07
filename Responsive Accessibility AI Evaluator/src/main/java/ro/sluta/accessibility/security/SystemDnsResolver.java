package ro.sluta.accessibility.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SystemDnsResolver implements DnsResolver {
    @Override
    public List<InetAddress> resolveAll(String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }
}
