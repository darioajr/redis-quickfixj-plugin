# Plugin Infinispan para QuickFIX/J

Este plugin permite usar o Infinispan como backend de persistência para sessões e mensagens do QuickFIX/J, fornecendo uma solução distribuída, escalável e de alto desempenho.

## Características

- **Persistência Distribuída**: Armazena mensagens e dados de sessão em cache distribuído Infinispan
- **Alta Disponibilidade**: Suporte a clustering e replicação de dados
- **Configuração Flexível**: Múltiplos modos de cache (LOCAL, REPLICATED, DISTRIBUTED)
- **Integração Transparente**: Drop-in replacement para stores padrão do QuickFIX/J
- **Estatísticas e Monitoramento**: Métricas detalhadas de performance
- **Persistência em Disco**: Opcional para durabilidade além da memória

## Requisitos

- Java 8 ou superior
- QuickFIX/J 2.3.0+
- Infinispan 14.0+

## Instalação

### Maven

```xml
<dependency>
    <groupId>com.infinispan.quickfixj</groupId>
    <artifactId>infinispan-quickfixj-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.infinispan.quickfixj:infinispan-quickfixj-plugin:1.0.0'
```

## Configuração Rápida

### 1. Configuração Básica (Local)

```java
import com.infinispan.quickfixj.config.InfinispanQuickFixJConfig;
import com.infinispan.quickfixj.factory.InfinispanMessageStoreFactory;

// Criar configuração
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .clusterName("meu-cluster")
    .cacheMode(CacheMode.LOCAL)
    .expiration(1440) // 24 horas
    .maxEntries(10000);

// Criar factory
InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
```

### 2. Configuração via Properties

```properties
# quickfixj.cfg
[DEFAULT]
ConnectionType=initiator
MessageStoreFactory=com.infinispan.quickfixj.factory.InfinispanMessageStoreFactory
InfinispanClusterName=meu-cluster
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

### 3. Uso Programático

```java
// Criar SessionSettings
SessionSettings settings = new SessionSettings("quickfixj.cfg");

// Criar factory e configurar
InfinispanMessageStoreFactory factory = new InfinispanMessageStoreFactory();
factory.configure(settings);

// Usar com SocketInitiator ou SocketAcceptor
SocketInitiator initiator = new SocketInitiator(application, factory, settings);
```

## Configurações Avançadas

### Clustering e Distribuição

```java
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .clusterName("quickfixj-production")
    .cacheMode(CacheMode.DIST_SYNC) // Distribuído síncrono
    .expiration(2880) // 48 horas
    .maxEntries(100000)
    .enableStatistics(true)
    .enablePersistence("/data/infinispan"); // Persistência em disco
```

### Configuração de Cache Customizada

```java
// Para configurações mais avançadas, use arquivo XML
InfinispanMessageStoreFactory factory = new InfinispanMessageStoreFactory();

Properties props = new Properties();
props.setProperty("InfinispanConfigFile", "infinispan-custom.xml");

SessionSettings settings = new SessionSettings();
settings.set("InfinispanConfigFile", "infinispan-custom.xml");

factory.configure(settings);
```

## Propriedades de Configuração

| Propriedade | Descrição | Padrão | Valores |
|-------------|-----------|---------|---------|
| `InfinispanClusterName` | Nome do cluster | `quickfixj-cluster` | String |
| `InfinispanCacheMode` | Modo do cache | `LOCAL` | `LOCAL`, `REPLICATED`, `DIST_SYNC`, `DIST_ASYNC` |
| `InfinispanExpirationMinutes` | Tempo de expiração (minutos) | `1440` | Número |
| `InfinispanMaxEntries` | Máximo de entradas | `10000` | Número |
| `InfinispanConfigFile` | Arquivo de configuração XML | - | Caminho do arquivo |

## Modos de Cache

### LOCAL
- Cache apenas na instância local
- Ideal para desenvolvimento e testes
- Sem overhead de rede

### REPLICATED
- Dados replicados em todos os nós
- Leitura rápida, escrita mais lenta
- Ideal para clusters pequenos

### DISTRIBUTED
- Dados distribuídos entre nós
- Balanceamento de carga
- Ideal para clusters grandes

## Exemplos Práticos

### Exemplo 1: Iniciador Simples

```java
public class SimpleInitiator {
    public static void main(String[] args) throws Exception {
        // Configuração
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
        
        // Aguardar...
        Thread.sleep(10000);
        
        initiator.stop();
        factory.close();
    }
}
```

### Exemplo 2: Aceitador Distribuído

```java
public class DistributedAcceptor {
    public static void main(String[] args) throws Exception {
        // Configuração distribuída
        InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
            .clusterName("production-cluster")
            .cacheMode(CacheMode.DIST_SYNC)
            .expiration(2880) // 48 horas
            .maxEntries(1000000)
            .enableStatistics(true)
            .enablePersistence("/opt/quickfixj/data");
        
        InfinispanMessageStoreFactory factory = config.createMessageStoreFactory();
        
        SessionSettings settings = new SessionSettings("acceptor.cfg");
        factory.configure(settings);
        
        Application application = new MyApplication();
        SocketAcceptor acceptor = new SocketAcceptor(application, factory, settings);
        
        acceptor.start();
        
        // Servidor rodando...
        System.in.read();
        
        acceptor.stop();
        factory.close();
    }
}
```

### Exemplo 3: Monitoramento

```java
// Obter estatísticas
Properties stats = factory.getCacheStatistics();
System.out.println("Mensagens armazenadas: " + stats.getProperty("messages.size"));
System.out.println("Sessões ativas: " + stats.getProperty("sessions.size"));
System.out.println("Membros do cluster: " + stats.getProperty("cluster.members"));
```

## Performance e Tuning

### Configurações de Performance

```java
InfinispanQuickFixJConfig config = new InfinispanQuickFixJConfig()
    .maxEntries(1000000) // Aumentar para alta carga
    .expiration(0) // Sem expiração para máxima performance
    .enableStatistics(false); // Desabilitar em produção se não necessário
```

### Heap e GC

```bash
# JVM flags recomendadas
-Xms2g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+UseStringDeduplication
```

## Monitoramento e Troubleshooting

### Logs

```xml
<!-- logback.xml -->
<logger name="com.infinispan.quickfixj" level="INFO"/>
<logger name="org.infinispan" level="WARN"/>
```

### JMX

O Infinispan expõe métricas via JMX. Para habilitar:

```java
GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
    .globalJmxStatistics().enable()
    .build();
```

### Verificação de Conectividade

```java
// Verificar status do cluster
EmbeddedCacheManager cacheManager = factory.getCacheManager();
System.out.println("Membros: " + cacheManager.getMembers());
System.out.println("Status: " + cacheManager.getStatus());
```

## Migração

### De FileMessageStore

1. Backup dos arquivos existentes
2. Alterar configuração para usar `InfinispanMessageStoreFactory`
3. Reiniciar aplicação
4. Dados antigos não são migrados automaticamente

### De DatabaseMessageStore

Similar ao FileMessageStore, requer reconfiguração manual.

## Limitações Conhecidas

- Não há migração automática de stores existentes
- Sequence numbers são mantidos em memória para performance
- Clustering requer configuração de rede adequada

## Contribuição

Contribuições são bem-vindas! Por favor:

1. Fork o projeto
2. Crie uma branch para sua feature
3. Commit suas mudanças
4. Abra um Pull Request

## Licença

Este projeto está licenciado sob a Apache License 2.0. Veja o arquivo LICENSE para detalhes.

## Suporte

- **Issues**: [GitHub Issues](https://github.com/seu-repo/issues)
- **Documentação**: [Wiki](https://github.com/seu-repo/wiki)
- **Community**: [Discussions](https://github.com/seu-repo/discussions)

## Changelog

### 1.0.0
- Primeira versão estável
- Suporte a MessageStore via Infinispan
- Configuração via Properties e programática
- Suporte a clustering
- Persistência opcional em disco
