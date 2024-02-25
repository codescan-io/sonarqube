/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.process.cluster.hz;

import com.hazelcast.aws.AwsDiscoveryStrategyFactory;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.process.ProcessId;
import org.sonar.process.cluster.hz.HazelcastMember.Attribute;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.sonar.process.ProcessProperties.Property.CLUSTER_NODE_HZ_PORT;
import static org.sonar.process.cluster.hz.JoinConfigurationType.KUBERNETES;


public class HazelcastMemberBuilder {
  private static final Logger LOGGER = Loggers.get(HazelcastMemberBuilder.class);
  private String nodeName;
  private int port;
  private ProcessId processId;
  private String networkInterface;
  private String members;
  private final JoinConfigurationType type;

  public HazelcastMemberBuilder(JoinConfigurationType type) {
    this.type = type;
  }

  public HazelcastMemberBuilder setNodeName(String s) {
    this.nodeName = s;
    return this;
  }

  public HazelcastMemberBuilder setProcessId(ProcessId p) {
    if (p == ProcessId.ELASTICSEARCH) {
      throw new IllegalArgumentException("Hazelcast must not be enabled on Elasticsearch node");
    }
    this.processId = p;
    return this;
  }

  public HazelcastMemberBuilder setPort(int i) {
    this.port = i;
    return this;
  }

  public HazelcastMemberBuilder setNetworkInterface(String s) {
    this.networkInterface = s;
    return this;
  }

  /**
   * Adds references to cluster members
   */
  public HazelcastMemberBuilder setMembers(String members) {
    this.members = members;
    return this;
  }

  public HazelcastMember build() {
    Config config = new Config();
    // do not use the value defined by property sonar.cluster.name.
    // Hazelcast does not fail when joining a cluster with different name.
    // Apparently this behavior exists since Hazelcast 3.8.2 (see note
    // at http://docs.hazelcast.org/docs/3.8.6/manual/html-single/index.html#creating-cluster-groups)
    config.setClusterName("SonarQube");
    config.setProperty("service-name", "web-amazon-dev-infra");
    LOGGER.info("networkInterface : {}", networkInterface);
    LOGGER.info("service-name {}", "web-amazon-dev-infra");
    LOGGER.info("type : {}", type);
    LOGGER.info("port : {}", port);


    // Configure network
    NetworkConfig netConfig = config.getNetworkConfig();
    netConfig
            .setPort(port)
            .setPortAutoIncrement(false)
            .setReuseAddress(true);

    netConfig.getInterfaces()
            .setEnabled(true)
            .addInterface(networkInterface);

    JoinConfig joinConfig = netConfig.getJoin();
    joinConfig.getAwsConfig().setEnabled(true).setProperty("hz-port", String.valueOf(port));
    joinConfig.getMulticastConfig().setEnabled(false);
    joinConfig.getTcpIpConfig().setEnabled(false);
    /*if (KUBERNETES.equals(type)) {
      joinConfig.getKubernetesConfig().setEnabled(true)
              .setProperty("service-dns", requireNonNull(members, "Service DNS is missing"))
              .setProperty("service-port", CLUSTER_NODE_HZ_PORT.getDefaultValue());
    } else {
      List<String> addressesWithDefaultPorts = Stream.of(this.members.split(","))
              .filter(host -> !host.isBlank())
              .map(String::trim)
              .map(HazelcastMemberBuilder::applyDefaultPortToHost)
              .toList();
      joinConfig.getTcpIpConfig().setEnabled(true);
      joinConfig.getTcpIpConfig().setMembers(requireNonNull(addressesWithDefaultPorts, "Members are missing"));
    }*/

    // We are not using the partition group of Hazelcast, so disabling it
    config.getPartitionGroupConfig().setEnabled(false);
    LOGGER.info("joinConfig.getDiscoveryConfig : {}",joinConfig.getDiscoveryConfig().toString());

    // Tweak HazelCast configuration
    config
            // Increase the number of tries
            .setProperty("hazelcast.tcp.join.port.try.count", "10")
            // Don't bind on all interfaces
            .setProperty("hazelcast.socket.bind.any", "false")
            // Don't phone home
            .setProperty("hazelcast.phone.home.enabled", "false")
            // Use slf4j for logging
            .setProperty("hazelcast.logging.type", "slf4j");

    AwsDiscoveryStrategyFactory awsDiscoveryStrategyFactory = new AwsDiscoveryStrategyFactory();
    Map<String, Comparable> properties = new HashMap<>();
    properties.put("region","eu-central-1");
    properties.put("hz-port","9003");
    properties.put("service-name", "web-amazon-dev-infra");
    properties.put("family", "web-amazon-dev-infra");
    properties.put("cluster", "codescan-ecs-amazon-dev-infra");
    DiscoveryStrategyConfig discoveryStrategyConfig = new DiscoveryStrategyConfig(awsDiscoveryStrategyFactory, properties);
    joinConfig.getDiscoveryConfig().addDiscoveryStrategyConfig(discoveryStrategyConfig);


    LOGGER.info("config after Update : {}", config);
    MemberAttributeConfig attributes = config.getMemberAttributeConfig();
    attributes.setAttribute(Attribute.NODE_NAME.getKey(), requireNonNull(nodeName, "Node name is missing"));
    attributes.setAttribute(Attribute.PROCESS_KEY.getKey(), requireNonNull(processId, "Process key is missing").getKey());

    return new HazelcastMemberImpl(Hazelcast.newHazelcastInstance(config));
  }

  private static String applyDefaultPortToHost(String host) {
    return host.contains(":") ? host : format("%s:%s", host, CLUSTER_NODE_HZ_PORT.getDefaultValue());
  }

}
