package com.github.madzdns.clusterlet;

import com.github.madzdns.clusterlet.Member.ClusterAddress;
import com.github.madzdns.clusterlet.config.Bind;
import com.github.madzdns.clusterlet.config.Socket;
import lombok.extern.slf4j.Slf4j;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.io.IOException;
import java.net.*;
import java.util.*;

@Slf4j
public class SynchServer {
    private Bind synchBindings;
    private SynchHandler handler;
    private SynchContext synchContext;
    private NioSocketAcceptor socket;

    public SynchServer(SynchHandler handler, Bind synchBindings) {
        this.synchBindings = Objects.requireNonNull(synchBindings);
        this.handler = Objects.requireNonNull(handler);
        if (handler.isSender) {
            throw new IllegalStateException("SynchHandler should be of type server");
        }
        this.synchContext = Objects.requireNonNull(handler.synchContext);
    }

    public synchronized void start() throws IOException {
        if (socket != null) {
            throw new IllegalStateException("socket is already activated");
        }
        socket = new NioSocketAcceptor();
        socket.setHandler(handler);
        socket.setReuseAddress(true);
        List<SocketAddress> addz = new ArrayList<>();
        for (Socket s : synchBindings.getSockets()) {
            if (s.getIp().equals(Socket.ANY)) {
                for (InetAddress ia : NetHelper.getAllAddresses()) {
                    addz.add(new InetSocketAddress(ia, s.getPort()));
                }
            } else {
                addz.add(new InetSocketAddress(s.getIp(), s.getPort()));
            }
        }

        Set<ClusterAddress> myAddrzForSynch = new HashSet<>();
        for (SocketAddress so : addz) {
            myAddrzForSynch.add(new ClusterAddress(((InetSocketAddress) so).getAddress(), ((InetSocketAddress) so).getPort()));
        }

        if (addz.size() == 0) {
            return;
        }

        Member me = synchContext.getMyInfo();
        if (!me.isValid()) {
            log.error("I was disabled");
            return;
        }

        boolean changed = false;
        long lastModified = new Date().getTime();

        if (me.getSynchAddresses() == null) {
            changed = prepareSyncAddresses(myAddrzForSynch, me, lastModified);
        } else if (!me.getSynchAddresses().equals(myAddrzForSynch)) {
            changed = prepareSyncAddresses(myAddrzForSynch, me, lastModified);
        }
        if (changed) {
            synchContext.updateMember(me);
            synchContext.setVirtualLastModified(lastModified);
        }
        socket.bind(addz);
        log.debug("Clusterlet is listning on {} ", addz);
        new StartupManager(synchContext).startClusterSyncing();
    }

    private boolean prepareSyncAddresses(Set<ClusterAddress> myAddrzForSynch, Member me, long lastModified) {
        boolean changed;
        final Set<Short> awareIds = new HashSet<>();
        awareIds.add(synchContext.myId);
        me.setSynchAddresses(myAddrzForSynch);
        me.setLastModified(lastModified);
        me.setAwareIds(awareIds);
        changed = true;
        return changed;
    }

    private static class NetHelper {
        private static List<InetAddress> addresses = null;

        public static List<InetAddress> getAllAddresses() throws SocketException {
            if (addresses != null) {
                return addresses;
            }
            addresses = new ArrayList<>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                addresses.addAll(Collections.list(inetAddresses));
            }
            if (addresses.size() == 0) {
                addresses = null;
            }
            return addresses;
        }
    }

    public synchronized void stop() {
        if (socket == null) {
            return;
        }
        socket.unbind();
        socket.dispose();
    }
}
