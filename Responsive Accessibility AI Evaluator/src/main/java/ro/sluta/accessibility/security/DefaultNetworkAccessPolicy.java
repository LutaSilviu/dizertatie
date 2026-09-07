package ro.sluta.accessibility.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.SnapshotErrorCode;

@Component
public class DefaultNetworkAccessPolicy implements NetworkAccessPolicy {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> METADATA_HOSTS = Set.of(
            "metadata", "metadata.google.internal", "instance-data.ec2.internal");

    private final DnsResolver dnsResolver;
    private final boolean allowLoopbackFixtures;

    @Autowired
    public DefaultNetworkAccessPolicy(DnsResolver dnsResolver, Environment environment) {
        this(dnsResolver, environment.acceptsProfiles(Profiles.of("test")));
    }

    DefaultNetworkAccessPolicy(DnsResolver dnsResolver, boolean allowLoopbackFixtures) {
        this.dnsResolver = dnsResolver;
        this.allowLoopbackFixtures = allowLoopbackFixtures;
    }

    @Override
    public void verify(URI uri, boolean mainDocument) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw blocked(mainDocument, "Cererea nu conține o destinație HTTP/HTTPS validă.");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw blocked(mainDocument, "Protocolul cererii nu este permis: " + scheme);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (METADATA_HOSTS.contains(host) || host.endsWith(".localhost") && !allowLoopbackFixtures) {
            throw blocked(mainDocument, "Destinația de metadata sau locală este blocată.");
        }

        final List<InetAddress> addresses;
        try {
            addresses = dnsResolver.resolveAll(host);
        } catch (UnknownHostException exception) {
            throw new NetworkPolicyException(SnapshotErrorCode.DNS_RESOLUTION_FAILED,
                    "Rezoluția DNS a eșuat pentru " + host + '.');
        }
        if (addresses.isEmpty()) {
            throw new NetworkPolicyException(SnapshotErrorCode.DNS_RESOLUTION_FAILED,
                    "Rezoluția DNS nu a returnat adrese pentru " + host + '.');
        }

        boolean hasBlocked = addresses.stream().anyMatch(this::isBlockedAddress);
        boolean hasAllowed = addresses.stream().anyMatch(address -> !isBlockedAddress(address));
        if (hasBlocked && hasAllowed) {
            throw blocked(mainDocument, "Destinația are rezultate DNS mixte și este blocată.");
        }
        if (hasBlocked) {
            boolean loopbackOnly = addresses.stream().allMatch(InetAddress::isLoopbackAddress);
            if (!(allowLoopbackFixtures && loopbackOnly)) {
                throw blocked(mainDocument, "Destinația DNS indică o adresă nepublică sau rezervată.");
            }
        }
    }

    @Override
    public boolean allowsLoopbackFixtures() { return allowLoopbackFixtures; }

    private NetworkPolicyException blocked(boolean mainDocument, String message) {
        return new NetworkPolicyException(
                mainDocument ? SnapshotErrorCode.BLOCKED_ADDRESS : SnapshotErrorCode.BLOCKED_REQUEST, message);
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isBlockedIpv4(bytes);
        if (address instanceof Inet6Address) {
            if (isIpv4Mapped(bytes)) {
                return isBlockedIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;       // fc00::/7
            if (first == 0xfe && (second & 0xc0) == 0x80) return true; // fe80::/10
            if (first == 0xff) return true;               // multicast
            return first == 0x20 && second == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8; // documentation
        }
        return true;
    }

    private boolean isBlockedIpv4(byte[] bytes) {
        int a = bytes[0] & 0xff;
        int b = bytes[1] & 0xff;
        int c = bytes[2] & 0xff;
        return a == 0 || a == 10 || a == 127 || a >= 224
                || a == 100 && b >= 64 && b <= 127
                || a == 169 && b == 254
                || a == 172 && b >= 16 && b <= 31
                || a == 192 && b == 168
                || a == 192 && b == 0 && c == 0
                || a == 192 && b == 0 && c == 2
                || a == 198 && (b == 18 || b == 19)
                || a == 198 && b == 51 && c == 100
                || a == 203 && b == 0 && c == 113;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) return false;
        for (int index = 0; index < 10; index++) if (bytes[index] != 0) return false;
        return true;
    }
}
