package fi.hel.resilientlogger.targets;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import fi.hel.resilientlogger.sources.AbstractLogSource;
import fi.hel.resilientlogger.types.AuditLogDocument;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.utils.Utils;

public class ElasticsearchLogTarget extends AbstractLogTarget {
    private static final Logger logger = System.getLogger(ElasticsearchLogTarget.class.getName());

    private final String index;
    private final RestClientTransport transport;
    private final ElasticsearchClient client;

    public ElasticsearchLogTarget(ComponentConfig config) {
        super(config);

        this.index = config.getValue("es_index", String.class);
        String username = config.getValue("es_username", String.class);
        assert username != null;
        String password = config.getValue("es_password", String.class);
        String url = config.getValue("es_url", String.class);
        String host = config.getValueOrDefault("es_host", "localhost");
        int port = config.getValueOrDefault("es_port", 9200);
        String scheme = config.getValueOrDefault("es_scheme", "https");

        HostInfo hostInfo = parseHostInfo(url, scheme, host, port);

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(new HttpHost(hostInfo.host(), hostInfo.port(), hostInfo.scheme()))
                .setHttpClientConfigCallback(
                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();

        this.transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    /**
     * Resolves the {@code (scheme, host, port)} tuple used to build the
     * Elasticsearch REST client. If {@code url} is set it takes precedence;
     * otherwise the discrete {@code scheme}/{@code host}/{@code port}
     * fallback is used. A path component on the URL is rejected so a
     * misconfigured {@code es_url: "localhost:9200/my-index"} fails fast
     * rather than producing a cryptic NPE inside {@code HttpHost}.
     */
    static HostInfo parseHostInfo(String url, String fallbackScheme, String fallbackHost, int fallbackPort) {
        if (url == null || url.isEmpty()) {
            return new HostInfo(fallbackScheme, fallbackHost, fallbackPort);
        }

        // Prepend scheme if missing for URI.create to work correctly
        String urlWithScheme = url.contains("://") ? url : fallbackScheme + "://" + url;
        URI uri = URI.create(urlWithScheme);

        if (uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "Configuration error: 'es_url' is not a valid host URL: " + url);
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration error: 'es_url' must not contain a path component: " + url);
        }

        String parsedScheme = (uri.getScheme() != null) ? uri.getScheme() : fallbackScheme;
        // Fallback to the 'port' variable (9200 or config) if URI has no explicit port
        int parsedPort = (uri.getPort() != -1) ? uri.getPort() : fallbackPort;

        return new HostInfo(parsedScheme, uri.getHost(), parsedPort);
    }

    record HostInfo(String scheme, String host, int port) {
    }

    @Override
    public boolean submit(AbstractLogSource.Entry entry) {
        AuditLogDocument document = entry.getDocument();
        Map<String, Object> documentMap = Utils.toMap(document);
        String hash = Utils.contentHash(documentMap);

        try {
            IndexResponse response = this.client.index(builder -> builder
                    .index(this.index)
                    .id(hash)
                    .document(documentMap)
                    .opType(OpType.Create));

             logger.log(Level.INFO, "Sending status: {0}", response);

            if (response.result() == Result.Created) {
                return true;
            }
        } catch (ElasticsearchException e) {
            /*
             * The document key used to store log entry is the hash of the contents.
             * If we receive conflict error, it means that the given entry is already
             * sent to the Elasticsearch.
             */
            if (e.status() == 409) {
                logger.log(
                    Level.WARNING, 
                    "Skipping the document with key {0}, it's already submitted.",
                    hash
                );
                // extra=document,
                return true;
            }

            /*
             * Non-conflict ElasticsearchException, log it and keep going to avoid
             * transaction rollbacks.
             */
            return this.handleException(hash, document, e);
        } catch (Exception e) {
            /*
             * Unknown exception, log it and keep going to avoid transaction rollbacks.
             */
            return this.handleException(hash, document, e);
        }

        return false;
    }

    @Override
    public void close() {
        // Closes the underlying RestClient transitively.
        try {
            transport.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to close Elasticsearch transport", e);
        }
    }

    /**
     * Logs the exception and return always false.
     *
     * @param hash String
     * @param document AuditLogDocument
     * @param e Exception
     * @return false
     */
    private boolean handleException(String hash, AuditLogDocument document, Exception e) {
        AuditLogEvent event = document.auditEvent();
        Map<String, Object> eventMap = Utils.toMap(event);

        // Don't log extra in here.
        eventMap.remove("extra");

        String message = String.format("Entry with key %s failed. [%s]", hash, eventMap);
        logger.log(Level.ERROR, message, e);

        return false;
    }
}
