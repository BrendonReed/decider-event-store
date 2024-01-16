package decider.event.store.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlResult;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Flux;

public class PubSubConnection {

    private PostgresqlConnectionFactory connectionFactory;

    // this structure is used for the listen/notify since it's not directly supported by r2dbc.
    // that means we have to use the postgresql driver more directly like this.
    // there's a good chance this doesn't properly dispose of connections currently.
    public static PubSubConnection create(String host, int port, String database, String username, String password) {
        return new PubSubConnection(host, port, database, username, password);
    }

    public PubSubConnection(PostgresqlConnectionFactory postgresqlConnectionFactory) {
        this.connectionFactory = postgresqlConnectionFactory;
    }

    private PubSubConnection(String host, int port, String database, String username, String password) {
        Map<String, String> options = new HashMap<>();
        options.put("lock_timeout", "10s");
        this.connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port) // optional, defaults to 5432
                .username(username)
                .password(password)
                .database(database) // optional
                .options(options) // optional
                .build());
    }

    public Flux<Notification> registerListener(String channel) {
        return connectionFactory.create().flatMapMany(receiver -> {
            var listen = receiver.createStatement("LISTEN " + channel)
                    .execute()
                    .flatMap(PostgresqlResult::getRowsUpdated)
                    .thenMany(receiver.getNotifications());
            return listen;
        });
    }
}
