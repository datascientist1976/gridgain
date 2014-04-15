/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.ipfinder.multicast;

import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.testframework.*;

import java.net.*;
import java.util.*;

/**
 * GridTcpDiscoveryMulticastIpFinder test.
 */
public class GridTcpDiscoveryMulticastIpFinderSelfTest
    extends GridTcpDiscoveryIpFinderAbstractSelfTest<GridTcpDiscoveryMulticastIpFinder> {
    /**
     * @throws Exception In case of error.
     */
    public GridTcpDiscoveryMulticastIpFinderSelfTest() throws Exception {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override protected GridTcpDiscoveryMulticastIpFinder ipFinder() throws Exception {
        GridTcpDiscoveryMulticastIpFinder ipFinder = new GridTcpDiscoveryMulticastIpFinder();

        ipFinder.setMulticastGroup(GridTestUtils.getNextMulticastGroup(getClass()));
        ipFinder.setMulticastPort(GridTestUtils.getNextMulticastPort(getClass()));

        return ipFinder;
    }

    /**
     * @throws Exception If failed.
     */
    public void testExchange() throws Exception {
        String locHost = null;

        GridTcpDiscoveryMulticastIpFinder ipFinder1 = null;
        GridTcpDiscoveryMulticastIpFinder ipFinder2 = null;
        GridTcpDiscoveryMulticastIpFinder ipFinder3 = null;

        try {
            ipFinder1 = ipFinder();

            ipFinder2 = new GridTcpDiscoveryMulticastIpFinder();

            ipFinder2.setMulticastGroup(ipFinder1.getMulticastGroup());
            ipFinder2.setMulticastPort(ipFinder1.getMulticastPort());

            ipFinder3 = new GridTcpDiscoveryMulticastIpFinder();

            ipFinder3.setMulticastGroup(ipFinder1.getMulticastGroup());
            ipFinder3.setMulticastPort(ipFinder1.getMulticastPort());

            injectLogger(ipFinder1);
            injectLogger(ipFinder2);
            injectLogger(ipFinder3);

            ipFinder1.setLocHost(locHost);
            ipFinder2.setLocHost(locHost);
            ipFinder3.setLocHost(locHost);

            ipFinder1.initializeLocalAddresses(Collections.singleton(new InetSocketAddress("host1", 1001)));
            ipFinder2.initializeLocalAddresses(Collections.singleton(new InetSocketAddress("host2", 1002)));
            ipFinder3.initializeLocalAddresses(Collections.singleton(new InetSocketAddress("host3", 1003)));

            for (int i = 0; i < 5; i++) {
                Collection<InetSocketAddress> addrs1 = ipFinder1.getRegisteredAddresses();
                Collection<InetSocketAddress> addrs2 = ipFinder2.getRegisteredAddresses();
                Collection<InetSocketAddress> addrs3 = ipFinder3.getRegisteredAddresses();

                if (addrs1.size() != 1 || addrs2.size() != 2 || addrs3.size() != 3) {
                    info("Addrs1: " + addrs1);
                    info("Addrs2: " + addrs2);
                    info("Addrs2: " + addrs3);

                    Thread.sleep(1000);
                }
            }

            assertEquals(1, ipFinder1.getRegisteredAddresses().size());
            assertEquals(2, ipFinder2.getRegisteredAddresses().size());
            assertEquals(3, ipFinder3.getRegisteredAddresses().size());
        }
        finally {
            if (ipFinder1 != null)
                ipFinder1.close();

            if (ipFinder2 != null)
                ipFinder2.close();

            if (ipFinder3 != null)
                ipFinder3.close();
        }
    }
}
