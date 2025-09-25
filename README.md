# Redis Plugin for QuickFIX/J

This plugin enables using Redis as a persistence backend for QuickFIX/J sessions and messages, providing a distributed, scalable, and high-performance solution.

[![codecov](https://codecov.io/github/darioajr/redis-quickfixj-plugin/branch/main/graph/badge.svg?style=flat-square)](https://app.codecov.io/github/darioajr/redis-quickfixj-plugin) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=darioajr_redis-quickfixj-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=darioajr_redis-quickfixj-plugin) [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.darioajr/quickfixj-redis)](https://central.sonatype.com/artifact/io.github.darioajr/quickfixj-redis)

[![FOSSA Status](https://app.fossa.com/api/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fredis-quickfixj-plugin.git.svg?type=large&issueType=license)](https://app.fossa.com/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fredis-quickfixj-plugin.git?ref=badge_large&issueType=license)


## Features

- **Distributed Persistence**: Stores messages and session data in Redis with high performance
- **High Availability**: Redis clustering and replication support
- **Flexible Configuration**: Support for authentication, SSL, and multiple databases
- **Transparent Integration**: Drop-in replacement for standard QuickFIX/J stores
- **Statistics and Monitoring**: Detailed performance metrics via Redis commands
- **Persistent Storage**: Redis persistence options (RDB, AOF) for durability

## Requirements

- Java 21 or higher
- QuickFIX/J 2.3.0+
- Redis 6.0+

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.darioajr</groupId>
    <artifactId>quickfixj-redis</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'io.github.darioajr:quickfixj-redis:1.0.0'
```

## Quick Configuration

### 1. Basic Configuration (Local)

```java
import io.github.darioajr.quickfixj.config.RedisQuickFixJConfig;
import io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory;

// Create configuration
RedisQuickFixJConfig config = new RedisQuickFixJConfig()
    .host("localhost")
    .port(6379)
    .database(0)
    .timeout(5000);

// Create factory
RedisMessageStoreFactory factory = config.createMessageStoreFactory();
```

### 2. Configuration via Properties

```properties
# quickfixj.cfg
[DEFAULT]
ConnectionType=initiator
MessageStoreFactory=io.github.darioajr.quickfixj.factory.RedisMessageStoreFactory
redis.host=localhost
redis.port=6379
redis.database=0
redis.timeout=5000

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

// Create factory
RedisMessageStoreFactory factory = new RedisMessageStoreFactory();

// Use with SocketInitiator or SocketAcceptor
SocketInitiator initiator = new SocketInitiator(application, factory, settings);
```

## Advanced Configurations

### Redis with Authentication and SSL

```java
RedisQuickFixJConfig config = new RedisQuickFixJConfig()
    .host("redis.example.com")
    .port(6380)
    .password("mypassword")
    .database(1)
    .timeout(10000)
    .enableSSL(true);

RedisMessageStoreFactory factory = config.createMessageStoreFactory();
```

### Redis Cluster Configuration

```properties
# For Redis cluster, configure multiple nodes
redis.host=redis-node1.example.com,redis-node2.example.com,redis-node3.example.com
redis.port=6379
redis.timeout=5000
redis.password=clusterpassword
```

### Session Settings with Redis Backend

```java
// Create Redis-backed session settings
RedisQuickFixJConfig config = new RedisQuickFixJConfig()
    .host("localhost")
    .port(6379);

RedisSessionSettings sessionSettings = config.createSessionSettings();

// Set default configurations
sessionSettings.setString("ConnectionType", "initiator");
sessionSettings.setString("HeartBtInt", "30");

// Set session-specific configurations
SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
sessionSettings.setString(sessionID, "SocketConnectHost", "broker.example.com");
```

## Configuration Properties

| Property | Description | Default | Values |
|----------|-------------|---------|---------|
| `redis.host` | Redis server hostname | `localhost` | String |
| `redis.port` | Redis server port | `6379` | Number (1-65535) |
| `redis.password` | Authentication password | - | String |
| `redis.database` | Database number | `0` | Number (0-15) |
| `redis.timeout` | Connection timeout (ms) | `2000` | Number |
| `redis.ssl` | Enable SSL connection | `false` | Boolean |

## Redis Storage Structure

### Key Namespaces
- `quickfixj:messages:{sessionId}` - FIX message storage
- `quickfixj:sequences:{sessionId}` - Sequence number tracking  
- `quickfixj:sessions:{sessionId}` - Session metadata
- `quickfixj:settings:{sessionId}` - Session-specific settings

### Data Types
- **Hash**: Used for storing messages, sequences, and settings
- **String**: Used for session timestamps and metadata
- **Expiration**: Optional TTL for automatic cleanup

## Practical Examples

### Example 1: Simple Initiator

```java
public class SimpleInitiator {
    public static void main(String[] args) throws Exception {
        // Configuration
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("localhost")
            .port(6379);
        
        // Factory
        RedisMessageStoreFactory factory = config.createMessageStoreFactory();
        
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
        // Redis configuration for production
        RedisQuickFixJConfig config = new RedisQuickFixJConfig()
            .host("redis-cluster.example.com")
            .port(6379)
            .password("your-redis-password")
            .database(0)
            .timeout(5000);
        
        RedisMessageStoreFactory factory = config.createMessageStoreFactory();
        
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
RedisQuickFixJConfig config = new RedisQuickFixJConfig()
    .host("redis-server.example.com")
    .port(6379)
    .timeout(2000) // Lower timeout for better responsiveness
    .database(1); // Use different database for isolation
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
<logger name="redis.clients.jedis" level="WARN"/>
```

### Redis Monitoring

Redis provides built-in monitoring commands. Common commands:

```bash
# Monitor real-time commands
redis-cli MONITOR

# Get server info
redis-cli INFO

# Memory usage
redis-cli MEMORY USAGE key-name
```

### Connectivity Check

```java
// Check cluster status
EmbeddedCacheManager cacheManager = factory.getCacheManager();
System.out.println("Members: " + cacheManager.getMembers());
System.out.println("Redis connection: " + jedis.ping());
```

## Migration

### From FileMessageStore

1. Backup existing files
2. Change configuration to use `RedisMessageStoreFactory`
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

- **Issues**: [GitHub Issues](https://github.com/darioajr/redis-quickfixj-plugin/issues)
- **Documentation**: [Wiki](https://github.com/darioajr/redis-quickfixj-plugin/wiki)
- **Community**: [Discussions](https://github.com/darioajr/redis-quickfixj-plugin/discussions)

## Changelog

### 1.0.0
- First stable version
- MessageStore support via Redis
- Configuration via Properties and programmatic
- Clustering support
- Optional disk persistence
