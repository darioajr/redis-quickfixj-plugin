# Infinispan Plugin for QuickFIX/J

This plugin enables using Infinispan as a persistence backend for QuickFIX/J sessions and messages, providing a distributed, scalable, and high-performance solution.

[![codecov](https://codecov.io/github/darioajr/infinispan-quickfixj-plugin/branch/main/graph/badge.svg?style=flat-square)](https://app.codecov.io/github/darioajr/infinispan-quickfixj-plugin) [![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fdarioajr%2Finfinispan-quickfixj-plugin.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fdarioajr%2Finfinispan-quickfixj-plugin?ref=badge_shield) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=darioajr_infinispan-quickfixj-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=darioajr_infinispan-quickfixj-plugin) [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.darioajr/quickfixj-infinispan)](https://central.sonatype.com/artifact/io.github.darioajr/quickfixj-infinispan)


## Features

- **Distributed Persistence**: Stores messages and session data in distributed Infinispan cache
- **High Availability**: Clustering and data replication support
- **Flexible Configuration**: Multiple cache modes (LOCAL, REPLICATED, DISTRIBUTED)
- **Transparent Integration**: Drop-in replacement for standard QuickFIX/J stores
- **Statistics and Monitoring**: Detailed performance metrics
- **Disk Persistence**: Optional for durability beyond memory

## Requirements

- Java 21 or higher
- QuickFIX/J 2.3.0+
- Infinispan 15.1+

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.darioajr</groupId>
    <artifactId>quickfixj-infinispan</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'io.github.darioajr:quickfixj-infinispan:1.0.0'
```

## Quick Configuration

### 1. Basic Configuration (Local)

```java
import io.github.darioajr.quickfixj.config.InfinispanQuickFixJConfig;
import io.github.darioajr.quickfixj.factory.InfinispanMessageStoreFactory;

// Create configuration
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .clusterName("my-cluster")
    .cacheMode(CacheMode.LOCAL)
    .expiration(1440) // 24 hours
    .maxEntries(10000);

// Create factory
InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
```

### 2. Configuration via Properties

```properties
# quickfixj.cfg
[DEFAULT]
ConnectionType=initiator
MessageStoreFactory=io.github.darioajr.quickfixj.factory.InfinispanMessageStoreFactory
InfinispanClusterName=my-cluster
InfinispanCacheMode=LOCAL
InfinispanExpirationMinutes=1440
InfinispanMaxEntries=10000

[SESSION]
BeginString=FIX.4.4
SenderCompID=SENDER
TargetCompID=TARGET
SocketConnectHost=localhost
SocketConnectPort=9876
```

### 3. Programmatic Usage

```java
// Create SessionSettings
SessionSettings settings = new SessionSettings("quickfixj.cfg");

// Create factory and configure
InfinispanMessageStoreFactory factory = new InfinispanMessageStoreFactory();
factory.configure(settings);

// Use with SocketInitiator or SocketAcceptor
SocketInitiator initiator = new SocketInitiator(application, factory, settings);
```

## Advanced Configurations

### Clustering and Distribution

```java
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .clusterName("quickfixj-production")
    .cacheMode(CacheMode.DIST_SYNC) // Synchronous distributed
    .expiration(2880) // 48 hours
    .maxEntries(100000)
    .enableStatistics(true)
    .enablePersistence("/data/infinispan"); // Disk persistence
```

### Custom Cache Configuration

```java
// For more advanced configurations, use XML file
InfinispanMessageStoreFactory factory = new InfinispanMessageStoreFactory();

Properties props = new Properties();
props.setProperty("InfinispanConfigFile", "infinispan-custom.xml");

SessionSettings settings = new SessionSettings();
settings.set("InfinispanConfigFile", "infinispan-custom.xml");

factory.configure(settings);
```

## Configuration Properties

| Property | Description | Default | Values |
|----------|-------------|---------|---------|
| `InfinispanClusterName` | Cluster name | `quickfixj-cluster` | String |
| `InfinispanCacheMode` | Cache mode | `LOCAL` | `LOCAL`, `REPLICATED`, `DIST_SYNC`, `DIST_ASYNC` |
| `InfinispanExpirationMinutes` | Expiration time (minutes) | `1440` | Number |
| `InfinispanMaxEntries` | Maximum entries | `10000` | Number |
| `InfinispanConfigFile` | XML configuration file | - | File path |

## Cache Modes

### LOCAL
- Cache only on local instance
- Ideal for development and testing
- No network overhead

### REPLICATED
- Data replicated on all nodes
- Fast reads, slower writes
- Ideal for small clusters

### DISTRIBUTED
- Data distributed across nodes
- Load balancing
- Ideal for large clusters

## Practical Examples

### Example 1: Simple Initiator

```java
public class SimpleInitiator {
    public static void main(String[] args) throws Exception {
        // Configuration
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("simple-cluster")
            .cacheMode(CacheMode.LOCAL);
        
        // Factory
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        // Settings
        SessionSettings settings = new SessionSettings("quickfixj.cfg");
        factory.configure(settings);
        
        // Application
        Application application = new MyApplication();
        
        // Initiator
        SocketInitiator initiator = new SocketInitiator(application, factory, settings);
        initiator.start();
        
        // Wait...
        Thread.sleep(10000);
        
        initiator.stop();
        factory.close();
    }
}
```

### Example 2: Distributed Acceptor

```java
public class DistributedAcceptor {
    public static void main(String[] args) throws Exception {
        // Distributed configuration
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("production-cluster")
            .cacheMode(CacheMode.DIST_SYNC)
            .expiration(2880) // 48 hours
            .maxEntries(1000000)
            .enableStatistics(true)
            .enablePersistence("/opt/quickfixj/data");
        
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        SessionSettings settings = new SessionSettings("acceptor.cfg");
        factory.configure(settings);
        
        Application application = new MyApplication();
        SocketAcceptor acceptor = new SocketAcceptor(application, factory, settings);
        
        acceptor.start();
        
        // Server running...
        System.in.read();
        
        acceptor.stop();
        factory.close();
    }
}
```

### Example 3: Monitoring

```java
// Get statistics
Properties stats = factory.getCacheStatistics();
System.out.println("Stored messages: " + stats.getProperty("messages.size"));
System.out.println("Active sessions: " + stats.getProperty("sessions.size"));
System.out.println("Cluster members: " + stats.getProperty("cluster.members"));
```

## Performance and Tuning

### Performance Settings

```java
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .maxEntries(1000000) // Increase for high load
    .expiration(0) // No expiration for maximum performance
    .enableStatistics(false); // Disable in production if not needed
```

### Heap and GC

```bash
# Recommended JVM flags
-Xms2g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+UseStringDeduplication
```

## Monitoring and Troubleshooting

### Logs

```xml
<!-- logback.xml -->
<logger name="io.github.darioajr.quickfixj" level="INFO"/>
<logger name="org.infinispan" level="WARN"/>
```

### JMX

Infinispan exposes metrics via JMX. To enable:

```java
GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
    .globalJmxStatistics().enable()
    .build();
```

### Connectivity Check

```java
// Check cluster status
EmbeddedCacheManager cacheManager = factory.getCacheManager();
System.out.println("Members: " + cacheManager.getMembers());
System.out.println("Status: " + cacheManager.getStatus());
```

## Migration

### From FileMessageStore

1. Backup existing files
2. Change configuration to use `InfinispanMessageStoreFactory`
3. Restart application
4. Old data is not migrated automatically

### From DatabaseMessageStore

Similar to FileMessageStore, requires manual reconfiguration.

## Known Limitations

- No automatic migration from existing stores
- Sequence numbers are kept in memory for performance
- Clustering requires proper network configuration

## Contributing

Contributions are welcome! Please:

1. Fork the project
2. Create a branch for your feature
3. Commit your changes
4. Open a Pull Request

## License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/darioajr/infinispan-quickfixj-plugin/issues)
- **Documentation**: [Wiki](https://github.com/darioajr/infinispan-quickfixj-plugin/wiki)
- **Community**: [Discussions](https://github.com/darioajr/infinispan-quickfixj-plugin/discussions)

## Changelog

### 1.0.0
- First stable version
- MessageStore support via Infinispan
- Configuration via Properties and programmatic
- Clustering support
- Optional disk persistence
