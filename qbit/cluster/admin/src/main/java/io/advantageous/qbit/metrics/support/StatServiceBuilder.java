/*
 * Copyright (c) 2015. Rick Hightower, Geoff Chandler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * QBit - The Microservice lib for Java : JSON, WebSocket, REST. Be The Web!
 */

package io.advantageous.qbit.metrics.support;


import io.advantageous.boon.core.reflection.BeanUtils;
import io.advantageous.qbit.QBit;
import io.advantageous.qbit.client.Client;
import io.advantageous.qbit.client.ClientBuilder;
import io.advantageous.qbit.config.PropertyResolver;
import io.advantageous.qbit.events.EventManager;
import io.advantageous.qbit.metrics.*;
import io.advantageous.qbit.metrics.StatServiceImpl;
import io.advantageous.qbit.queue.QueueBuilder;
import io.advantageous.qbit.server.EndpointServerBuilder;
import io.advantageous.qbit.server.EndpointServer;
import io.advantageous.qbit.service.ServiceBuilder;
import io.advantageous.qbit.service.ServiceQueue;
import io.advantageous.qbit.service.discovery.ServiceDiscovery;
import io.advantageous.qbit.util.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static io.advantageous.qbit.client.ClientBuilder.clientBuilder;

/**
 * Stat Service Builder
 * Created by rhightower on 1/28/15.
 */
public class StatServiceBuilder {


    private EventManager eventManager;

    public static StatServiceBuilder statServiceBuilder() {
        return new StatServiceBuilder();
    }

    private Timer timer = Timer.timer();
    private StatRecorder recorder = new NoOpRecorder();
    private StatReplicator replicator = new NoOpReplicator();
    private List<StatReplicator> replicators = new ArrayList<>();
    private ServiceDiscovery serviceDiscovery;
    private StatReplicatorProvider statReplicatorProvider;
    private ClientBuilder clientBuilder;
    private ServiceQueue serviceQueue;
    private ServiceBuilder serviceBuilder;
    private EndpointServerBuilder endpointServerBuilder;
    private QueueBuilder sendQueueBuilder;
    private StatServiceImpl statServiceImpl;



    private String serviceName;
    private String localServiceId;
    private int tallyInterval;
    private int flushInterval;
    private int timeToLiveCheckInterval;
    private int numStats;


    public static final String QBIT_STAT_SERVICE_BUILDER = "qbit.statservice.builder.";

    public StatServiceBuilder(PropertyResolver propertyResolver) {
        this.serviceName = propertyResolver.getStringProperty("serviceName", "statsService");
        this.localServiceId = propertyResolver.getStringProperty("localServiceId", "");
        this.tallyInterval = propertyResolver.getIntegerProperty("tallyInterval", 100);
        this.flushInterval = propertyResolver.getIntegerProperty("flushInterval", 333);
        this.timeToLiveCheckInterval = propertyResolver
                .getIntegerProperty("timeToLiveCheckInterval", 5_000);
        this.numStats = propertyResolver
                .getIntegerProperty("numStats", 100);

    }

    public StatServiceBuilder() {
        this(PropertyResolver.createSystemPropertyResolver(QBIT_STAT_SERVICE_BUILDER));
    }


    public StatServiceBuilder(final Properties properties) {
        this(PropertyResolver.createPropertiesPropertyResolver(
                QBIT_STAT_SERVICE_BUILDER, properties));
    }

    public int getTallyInterval() {
        return tallyInterval;
    }

    public StatServiceBuilder setTallyInterval(int tallyInterval) {
        this.tallyInterval = tallyInterval;
        return this;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public StatServiceBuilder setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
        return this;
    }

    public int getTimeToLiveCheckInterval() {
        return timeToLiveCheckInterval;
    }

    public StatServiceBuilder setTimeToLiveCheckInterval(int timeToLiveCheckInterval) {
        this.timeToLiveCheckInterval = timeToLiveCheckInterval;
        return this;
    }

    public int getNumStats() {
        return numStats;
    }

    public void setNumStats(int numStats) {
        this.numStats = numStats;
    }

    public QueueBuilder getSendQueueBuilder() {

        if (sendQueueBuilder==null) {
            sendQueueBuilder = QueueBuilder.queueBuilder().setLinkTransferQueue()
                    .setBatchSize(1_000).setPollWait(500);
        }
        return sendQueueBuilder;
    }
    public EndpointServerBuilder getEndpointServerBuilder() {

        if (endpointServerBuilder == null) {
            endpointServerBuilder = EndpointServerBuilder.endpointServerBuilder();
        }
        return endpointServerBuilder;
    }

    public StatServiceBuilder setEndpointServerBuilder(EndpointServerBuilder endpointServerBuilder) {
        this.endpointServerBuilder = endpointServerBuilder;
        return this;
    }


    public StatServiceBuilder setSendQueueBuilder(QueueBuilder sendQueueBuilder) {
        this.sendQueueBuilder = sendQueueBuilder;
        return this;
    }



    public ServiceQueue getServiceQueue() {

        if (serviceQueue==null) {
            buildServiceQueue();
        }
        return serviceQueue;
    }

    public StatServiceBuilder setServiceQueue(ServiceQueue serviceQueue) {
        this.serviceQueue = serviceQueue;
        return this;
    }

    public ServiceBuilder getServiceBuilder() {

        if (serviceBuilder == null) {
            serviceBuilder = ServiceBuilder.serviceBuilder();
        }
        return serviceBuilder;
    }

    public StatServiceBuilder setServiceBuilder(ServiceBuilder serviceBuilder) {
        this.serviceBuilder = serviceBuilder;
        return this;
    }



    public String getLocalServiceId() {
        return localServiceId;
    }

    public StatServiceBuilder setLocalServiceId(String localServiceId) {
        this.localServiceId = localServiceId;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public StatServiceBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    private ClientBuilder getClientBuilder() {

        if (clientBuilder==null) {
            clientBuilder = clientBuilder();
        }

        return BeanUtils.copy(clientBuilder);
    }
    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    public StatServiceBuilder setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        return this;
    }

    public Timer getTimer() {
        return timer;
    }

    public StatServiceBuilder setTimer(Timer timer) {
        this.timer = timer;
        return this;
    }

    public StatServiceBuilder addReplicator(StatReplicator replicator) {
        replicators.add(replicator);
        return this;
    }

    public StatRecorder getRecorder() {
        return recorder;
    }

    public StatServiceBuilder setRecorder(StatRecorder recorder) {
        this.recorder = recorder;
        return this;
    }

    public StatReplicator getReplicator() {
        return replicator;
    }

    public StatServiceBuilder setReplicator(StatReplicator replicator) {
        this.replicator = replicator;
        return this;
    }

    public ServiceQueue buildServiceQueue() {
        ServiceBuilder serviceBuilder = getServiceBuilder()
                .setRequestQueueBuilder(getSendQueueBuilder())
                .setServiceObject(getStatServiceImpl());
        serviceQueue = serviceBuilder.build();

        if (serviceDiscovery!=null) {

            if (eventManager!=null && eventManager!=QBit.factory().systemEventManager()) {

                eventManager.joinService(serviceQueue);
            }
        }
        return serviceQueue;
    }


    public EndpointServer buildServiceServer() {


        final EndpointServerBuilder endpointServerBuilder = getEndpointServerBuilder();




        if (serviceDiscovery!=null) {

            if (localServiceId == null || "".equals(localServiceId.trim())){
                    localServiceId =
                            serviceName + "-" + ServiceDiscovery.uniqueString(endpointServerBuilder.getPort());

            }


            serviceDiscovery.registerWithIdAndTimeToLive(this.getServiceName(), localServiceId,
                                    endpointServerBuilder.getPort(), timeToLiveCheckInterval);


        }
        final ServiceQueue serviceQueue = getServiceQueue();
        final EndpointServer endpointServer = endpointServerBuilder.build();
        endpointServer.addServiceObject(this.getServiceName(), serviceQueue.service());

        return endpointServer;
    }



    public StatServiceImpl getStatServiceImpl() {

        if (statServiceImpl == null) {
            statServiceImpl = build();
        }
        return statServiceImpl;
    }

    public StatServiceBuilder setStatServiceImpl(StatServiceImpl statServiceImpl) {
        this.statServiceImpl = statServiceImpl;
        return this;
    }

    public StatServiceImpl build() {

        if (serviceDiscovery!=null) {
            return new StatServiceImpl(this.getRecorder(), buildReplicator(), getTimer(), getServiceDiscovery(),
                    getLocalServiceId(), getNumStats(), getTimeToLiveCheckInterval());
        } else if (replicators.size() == 0) {
            return new StatServiceImpl(this.getRecorder(), this.getReplicator(), getTimer(),
                    null, getLocalServiceId(), getNumStats(), getTimeToLiveCheckInterval());
        } else {
            return new StatServiceImpl(this.getRecorder(), new ReplicatorHub(replicators), getTimer(),
                    null, getLocalServiceId(), getNumStats(), getTimeToLiveCheckInterval());
        }
    }

    public StatReplicatorProvider getStatsReplicatorProvider() {

        if (statReplicatorProvider == null) {
            statReplicatorProvider = buildStatsReplicatorProvider();
        }

        return statReplicatorProvider;
    }

    public StatServiceBuilder setStatReplicatorProvider(StatReplicatorProvider statReplicatorProvider) {
        this.statReplicatorProvider = statReplicatorProvider;
        return this;
    }

    private StatReplicator buildReplicator() {

        return new ClusteredStatReplicator(getServiceName(), getServiceDiscovery(),
                getStatsReplicatorProvider(), getLocalServiceId(), getTimer(),
                getTallyInterval(), getFlushInterval());
    }

    public StatReplicatorProvider buildStatsReplicatorProvider() {
        return serviceDefinition -> {

            final ClientBuilder clientBuilder1 = getClientBuilder();
            final Client client = clientBuilder1.setPort(serviceDefinition.getPort())
                    .setRequestBatchSize(1_000)
                    .setHost(serviceDefinition.getHost()).build();

            final StatReplicator proxy = client.createProxy(StatReplicator.class, serviceName);

            client.start();

            return new StatReplicator() {

                private final Client theClient = client;

                @Override
                protected void finalize() throws Throwable {
                    if (theClient!=null) {
                        theClient.stop();
                    }
                }

                @Override
                public void replicateCount(String name, int count, long now) {

                    proxy.replicateCount(name, count, now);
                }

                @Override
                public void replicateLevel(String name, int level, long time) {
                    proxy.replicateLevel(name, level, time);
                }

                @Override
                public void clientProxyFlush() {
                    proxy.clientProxyFlush();
                }

                @Override
                public void stop() {
                    proxy.stop();
                    theClient.stop();
                }

                @Override
                public void flush() {
                    proxy.flush();
                    theClient.flush();
                }

                public String toString() {
                    return "StatServiceReplicator " + proxy;
                }

                @Override
                public int port() {
                    return proxy.port();
                }

                @Override
                public String host() {
                    return proxy.host();
                }

                @Override
                public boolean connected() {
                    return proxy.connected();
                }

                @Override
                public boolean remote() {
                    return proxy.remote();
                }
            };
        };
    }

    public StatServiceBuilder setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
        return this;
    }

    public EventManager getEventManager() {
        if (eventManager==null) {
            eventManager = QBit.factory().systemEventManager();
        }
        return eventManager;
    }
}
