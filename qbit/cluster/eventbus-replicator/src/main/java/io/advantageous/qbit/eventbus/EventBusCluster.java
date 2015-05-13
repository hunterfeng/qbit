package io.advantageous.qbit.eventbus;

import io.advantageous.consul.Consul;
import io.advantageous.consul.domain.ConsulResponse;
import io.advantageous.consul.domain.NotRegisteredException;
import io.advantageous.consul.domain.ServiceHealth;
import io.advantageous.consul.domain.option.Consistency;
import io.advantageous.consul.domain.option.RequestOptions;
import io.advantageous.consul.domain.option.RequestOptionsBuilder;
import io.advantageous.qbit.GlobalConstants;
import io.advantageous.qbit.QBit;
import io.advantageous.qbit.client.Client;
import io.advantageous.qbit.client.ClientProxy;
import io.advantageous.qbit.client.RemoteTCPClientProxy;
import io.advantageous.qbit.concurrent.PeriodicScheduler;
import io.advantageous.qbit.events.EventManager;
import io.advantageous.qbit.events.impl.EventConnectorHub;
import io.advantageous.qbit.events.spi.EventConnector;
import io.advantageous.qbit.server.EndpointServer;
import io.advantageous.qbit.service.ServiceQueue;
import io.advantageous.qbit.service.Startable;
import io.advantageous.qbit.service.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.advantageous.boon.core.IO.puts;
import static io.advantageous.qbit.eventbus.EventBusRemoteReplicatorBuilder.eventBusRemoteReplicatorBuilder;
import static io.advantageous.qbit.eventbus.EventBusReplicationClientBuilder.eventBusReplicationClientBuilder;
import static io.advantageous.qbit.events.EventManagerBuilder.eventManagerBuilder;
import static io.advantageous.qbit.service.ServiceBuilder.serviceBuilder;

public class EventBusCluster implements Startable, Stoppable {

    private final String eventBusName;
    private final EventConnectorHub eventConnectorHub;
    private final PeriodicScheduler periodicScheduler;
    private final int peerCheckTimeInterval;
    private final TimeUnit peerCheckTimeUnit;
    private final String consulHost;
    private final int consulPort;
    private final String datacenter;
    private final String tag;
    private final int longPollTimeSeconds;
    private final String localEventBusId;
    private final int replicationPortLocal;
    private final String replicationHostLocal;
    private final EventManager eventManager;
    private final int replicationServerCheckInIntervalInSeconds;

    private final Logger logger = LoggerFactory.getLogger(EventBusCluster.class);
    private final boolean debug = GlobalConstants.DEBUG || logger.isDebugEnabled();

    private AtomicInteger lastIndex = new AtomicInteger();
    private RequestOptions requestOptions;
    private AtomicReference<Consul> consul = new AtomicReference<>();
    private ScheduledFuture healthyNodeMonitor;
    private ScheduledFuture consulCheckInMonitor;
    private EndpointServer endpointServerForReplicator;
    private ServiceQueue eventServiceQueue;

    private EventManager eventManagerImpl;

    public EventBusCluster(final EventManager eventManager,
                           final String eventBusName,
                           final String localEventBusId,
                           final EventConnectorHub eventConnectorHub,
                           final PeriodicScheduler periodicScheduler,
                           final int peerCheckTimeInterval,
                           final TimeUnit timeunit,
                           final String consulHost,
                           final int consulPort,
                           final int longPollTimeSeconds,
                           final int replicationPortLocal,
                           final String replicationHostLocal,
                           final String datacenter,
                           final String tag,
                           final int replicationServerCheckInIntervalInSeconds) {

        this.eventBusName = eventBusName;
        this.eventConnectorHub = eventConnectorHub == null ? new EventConnectorHub() : eventConnectorHub;
        this.periodicScheduler = periodicScheduler == null ?
                QBit.factory().periodicScheduler() : periodicScheduler;
        this.peerCheckTimeInterval = peerCheckTimeInterval;
        this.peerCheckTimeUnit = timeunit;
        this.consulHost = consulHost;
        this.consulPort = consulPort;
        this.consul.set(Consul.consul(consulHost, consulPort));
        this.datacenter = datacenter;
        this.tag = tag;
        this.longPollTimeSeconds = longPollTimeSeconds;
        this.localEventBusId = localEventBusId;
        this.replicationPortLocal = replicationPortLocal;
        this.replicationHostLocal = replicationHostLocal;
        this.eventManager = eventManager == null ? createEventManager() : wrapEventManager(eventManager);
        this.replicationServerCheckInIntervalInSeconds = replicationServerCheckInIntervalInSeconds;

        buildRequestOptions();
    }

    private EventManager wrapEventManager(final EventManager eventManager) {
        if (eventManager instanceof ClientProxy) {
            return eventManager;
        } else {
            eventManagerImpl = eventManager;
            eventServiceQueue = serviceBuilder().setServiceObject(eventManager).build();
            return eventServiceQueue.createProxyWithAutoFlush(EventManager.class, periodicScheduler,
                    100, TimeUnit.MILLISECONDS);
        }
    }

    public EventManager eventManager() {
        return eventManager;
    }

    public EventManager eventManagerImpl() {
        return eventManagerImpl;
    }

    private EventManager createEventManager() {
        eventManagerImpl = eventManagerBuilder().setEventConnector(eventConnectorHub).build();
        eventServiceQueue = serviceBuilder().setServiceObject(eventManagerImpl).build();

        return eventServiceQueue.createProxyWithAutoFlush(
                EventManager.class, periodicScheduler, 100, TimeUnit.MILLISECONDS);
    }

    public ServiceQueue eventServiceQueue() {
        return eventServiceQueue;
    }

    @Override
    public void start() {

        consul.get().start();

        if (eventServiceQueue != null) {
            eventServiceQueue.start();
        }

        startServerReplicator();

        registerLocalBusInConsul();

        healthyNodeMonitor = periodicScheduler.repeat(
                this::healthyNodeMonitor, peerCheckTimeInterval, peerCheckTimeUnit);

        if (replicationServerCheckInIntervalInSeconds > 2) {
            consulCheckInMonitor = periodicScheduler.repeat(this::checkInWithConsul,
                    replicationServerCheckInIntervalInSeconds / 2, TimeUnit.SECONDS);
        } else {
            consulCheckInMonitor = periodicScheduler.repeat(this::checkInWithConsul, 100, TimeUnit.MILLISECONDS);
        }
    }

    private void checkInWithConsul() {
        try {
            consul.get().agent().pass(localEventBusId, "still running");
        } catch (NotRegisteredException ex) {
            registerLocalBusInConsul();
        } catch (Exception ex) {
            Consul oldConsul = consul.get();
            consul.compareAndSet(oldConsul, startNewConsul(oldConsul));
        }
    }

    private Consul startNewConsul(final Consul oldConsul) {

        if (oldConsul != null) {
            try {
                oldConsul.stop();
            } catch (Exception ex) {
                logger.debug("Unable to stop old consul", ex);
            }
        }

        final Consul consul = Consul.consul(consulHost, consulPort);
        consul.start();
        return consul;
    }

    private void registerLocalBusInConsul() {
        consul.get().agent().registerService(replicationPortLocal,
                replicationServerCheckInIntervalInSeconds, eventBusName, localEventBusId, tag);
    }

    private void startServerReplicator() {
        final List<ServiceHealth> healthyServices = getHealthyServices();

        List<ServiceHealth> newServices = findNewServices(healthyServices);
        addNewServicesToHub(newServices);


        EventBusRemoteReplicatorBuilder replicatorBuilder = eventBusRemoteReplicatorBuilder();
        replicatorBuilder.setName(this.eventBusName);
        replicatorBuilder.serviceServerBuilder().setPort(replicationPortLocal);

        if (replicationHostLocal != null) {
            replicatorBuilder.serviceServerBuilder().setHost(replicationHostLocal);
        }
        replicatorBuilder.setEventManager(eventManager);
        endpointServerForReplicator = replicatorBuilder.build();
        endpointServerForReplicator.start();
    }

    private void showHealthyServices(List<ServiceHealth> healthyServices) {
        puts("SHOW HEALTHY SERVICES");

        healthyServices.forEach(serviceHealth -> {
            puts("----------------------------");
            puts("node",eventBusName, serviceHealth.getService().getPort(), serviceHealth.getNode().getAddress());
            puts("----------------------------");

        });
    }

    private List<ServiceHealth> getHealthyServices() {
        final ConsulResponse<List<ServiceHealth>> consulResponse = consul.get().health()
                .getHealthyServices(eventBusName, datacenter, tag, requestOptions);
        this.lastIndex.set(consulResponse.getIndex());

        final List<ServiceHealth> healthyServices = consulResponse.getResponse();

        if (debug) {
            showHealthyServices(healthyServices);
        }
        buildRequestOptions();
        return healthyServices;
    }

    private void buildRequestOptions() {
        this.requestOptions = new RequestOptionsBuilder()
                .consistency(Consistency.CONSISTENT)
                .blockSeconds(longPollTimeSeconds, lastIndex.get()).build();
    }

    private void healthyNodeMonitor() {

        try {
            rebuildHub(getHealthyServices());
        } catch (Exception ex) {
            logger.error("unable to contact consul or problems rebuilding event hub", ex);
            Consul oldConsul = consul.get();
            consul.compareAndSet(oldConsul, startNewConsul(oldConsul));
        }

    }

    private void rebuildHub(List<ServiceHealth> services) {


        //look at stuff in the hub and see if it matches the healthy nodes
        //if not take them out of the hub

        if (debug) logger.debug("Number of services before remove bad service called " + eventConnectorHub.size() );
        removeBadServices(services);
        if (debug) logger.debug("Number of services AFTER remove bad service called " + eventConnectorHub.size() );
        List<ServiceHealth> newServices = findNewServices(services);

        if (debug) logger.debug("Number of services found " + newServices.size() );
        addNewServicesToHub(newServices);

        if (debug) logger.debug("Number of services AFTER addNewServicesToHub called " + eventConnectorHub.size() );


    }

    private void addNewServicesToHub(List<ServiceHealth> newServices) {
        for (ServiceHealth serviceHealth : newServices) {

            final int newPort = serviceHealth.getService().getPort();
            final String newHost = serviceHealth.getNode().getAddress();
            addEventConnector(newHost, newPort);

        }
    }

    private List<ServiceHealth> findNewServices(List<ServiceHealth> services) {
        List<ServiceHealth> newServices = new ArrayList<>();


        for (ServiceHealth serviceHealth : services) {
            final int healthyPort = serviceHealth.getService().getPort();
            final String healthyHost = serviceHealth.getNode().getAddress();

            /* Don't return yourself. */
            if (serviceHealth.getService().getId().equals(localEventBusId)) {
                continue;
            }

            boolean found = false;

            final ListIterator<EventConnector> listIterator = eventConnectorHub.listIterator();
            while (listIterator.hasNext()) {
                final EventConnector connector = listIterator.next();

                if (connector instanceof RemoteTCPClientProxy) {
                    final String host = ((RemoteTCPClientProxy) connector).host();
                    final int port = ((RemoteTCPClientProxy) connector).port();

                    if (healthyPort == port && healthyHost.equals(host)) {
                        found = true;
                        break;
                    }

                }
            }

            if (!found) {
                newServices.add(serviceHealth);
            }

        }
        return newServices;
    }

    private void addEventConnector(final String newHost, final int newPort) {
        /* A client replicator */
        EventBusReplicationClientBuilder clientReplicatorBuilder = eventBusReplicationClientBuilder();
        clientReplicatorBuilder.setName(this.eventBusName);
        clientReplicatorBuilder.clientBuilder().setPort(newPort).setHost(newHost);
        Client client = clientReplicatorBuilder.build();
        final EventConnector eventConnector = clientReplicatorBuilder.build(client);
        client.start();
        eventConnectorHub.add(eventConnector);
    }

    private void removeBadServices(List<ServiceHealth> services) {
        final ListIterator<EventConnector> listIterator = eventConnectorHub.listIterator();

        final List<EventConnector> badConnectors = new ArrayList<>();

        while (listIterator.hasNext()) {
            final EventConnector connector = listIterator.next();


            /** Remove bad ones. */
            if (connector instanceof RemoteTCPClientProxy) {

                if (!((RemoteTCPClientProxy) connector).connected()) {
                    badConnectors.add(connector);
                    continue;
                }

                final String host = ((RemoteTCPClientProxy) connector).host();
                final int port = ((RemoteTCPClientProxy) connector).port();
                boolean found = false;
                for (ServiceHealth serviceHealth : services) {
                    final int healthyPort = serviceHealth.getService().getPort();
                    final String healthyHost = serviceHealth.getNode().getAddress();

                    if (healthyPort == port && healthyHost.equals(host)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    badConnectors.add(connector);
                }
            }
        }

        badConnectors.forEach(eventConnectorHub::remove);
    }


    @Override
    public void stop() {

        try {
            consul.get().stop();
        } finally {
            try {
                this.endpointServerForReplicator.stop();
            } finally {

                try {
                    if (healthyNodeMonitor != null) {
                        healthyNodeMonitor.cancel(true);
                    }
                } finally {
                    try {
                        if (consulCheckInMonitor != null) {
                            consulCheckInMonitor.cancel(true);
                        }
                    } finally {
                        if (eventServiceQueue != null) {
                            eventServiceQueue.stop();
                        }
                    }
                }

            }
        }
    }
}
