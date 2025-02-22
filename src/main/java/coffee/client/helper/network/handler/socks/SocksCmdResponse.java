/*
 * Copyright (c) 2022 Coffee Client, 0x150 and contributors. All rights reserved.
 */
package coffee.client.helper.network.handler.socks;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import java.net.IDN;

/**
 * A socks cmd response.
 *
 * @see SocksCmdRequest
 * @see SocksCmdResponseDecoder
 */
public final class SocksCmdResponse extends SocksResponse {
    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] DOMAIN_ZEROED = { 0x00 };
    private static final byte[] IPv4_HOSTNAME_ZEROED = { 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] IPv6_HOSTNAME_ZEROED = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private final SocksCmdStatus cmdStatus;
    private final SocksAddressType addressType;
    private final String host;
    private final int port;

    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType) {
        this(cmdStatus, addressType, null, 0);
    }

    /**
     * Constructs new response and includes provided host and port as part of it.
     *
     * @param cmdStatus   status of the response
     * @param addressType type of host parameter
     * @param host        host (BND.ADDR field) is address that server used when connecting to the target host.
     *                    When null a value of 4/8 0x00 octets will be used for IPv4/IPv6 and a single 0x00 byte will be
     *                    used for domain addressType. Value is converted to ASCII using {@link IDN#toASCII(String)}.
     * @param port        port (BND.PORT field) that the server assigned to connect to the target host
     * @throws NullPointerException     in case cmdStatus or addressType are missing
     * @throws IllegalArgumentException in case host or port cannot be validated
     * @see IDN#toASCII(String)
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType, String host, int port) {
        super(SocksResponseType.CMD);
        String host1 = host;
        ObjectUtil.checkNotNull(cmdStatus, "cmdStatus");
        ObjectUtil.checkNotNull(addressType, "addressType");
        if (host1 != null) {
            switch (addressType) {
                case IPv4:
                    if (!NetUtil.isValidIpV4Address(host1)) {
                        throw new IllegalArgumentException(host1 + " is not a valid IPv4 address");
                    }
                    break;
                case DOMAIN:
                    String asciiHost = IDN.toASCII(host1);
                    if (asciiHost.length() > 255) {
                        throw new IllegalArgumentException(host1 + " IDN: " + asciiHost + " exceeds 255 char limit");
                    }
                    host1 = asciiHost;
                    break;
                case IPv6:
                    if (!NetUtil.isValidIpV6Address(host1)) {
                        throw new IllegalArgumentException(host1 + " is not a valid IPv6 address");
                    }
                    break;
                case UNKNOWN:
                    break;
            }
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(port + " is not in bounds 0 <= x <= 65535");
        }
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
        this.host = host1;
        this.port = port;
    }

    /**
     * Returns the {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     */
    public SocksCmdStatus cmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns the {@link SocksAddressType} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksAddressType} of this {@link SocksCmdResponse}
     */
    public SocksAddressType addressType() {
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksCmdType}.
     * Host (BND.ADDR field in response) is address that server used when connecting to the target host.
     * This is typically different from address which client uses to connect to the SOCKS server.
     *
     * @return host that is used as a parameter in {@link SocksCmdType}
     * or null when there was no host specified during response construction
     */
    public String host() {
        return host != null && addressType == SocksAddressType.DOMAIN ? IDN.toUnicode(host) : host;
    }

    /**
     * Returns port that is used as a parameter in {@link SocksCmdType}.
     * Port (BND.PORT field in response) is port that the server assigned to connect to the target host.
     *
     * @return port that is used as a parameter in {@link SocksCmdType}
     */
    public int port() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(cmdStatus.byteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.byteValue());
        switch (addressType) {
            case IPv4 -> {
                byte[] hostContent = host == null ? IPv4_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                byteBuf.writeBytes(hostContent);
                byteBuf.writeShort(port);
            }
            case DOMAIN -> {
                if (host != null) {
                    byteBuf.writeByte(host.length());
                    byteBuf.writeCharSequence(host, CharsetUtil.US_ASCII);
                } else {
                    byteBuf.writeByte(DOMAIN_ZEROED.length);
                    byteBuf.writeBytes(DOMAIN_ZEROED);
                }
                byteBuf.writeShort(port);
            }
            case IPv6 -> {
                byte[] hostContent = host == null ? IPv6_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                byteBuf.writeBytes(hostContent);
                byteBuf.writeShort(port);
            }
        }
    }
}
