package fi.hel.resilient_logger.targets;

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
import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.utils.Utils;

public class ElasticsearchLogTarget extends AbstractLogTarget {
    private static final Logger logger = System.getLogger(ElasticsearchLogTarget.class.getName());

    private String index;
    private ElasticsearchClient client;

    public ElasticsearchLogTarget(ComponentConfig config) {
        super(config);

        this.index = config.getValue("es_index", String.class);
        String username = config.getValue("es_username", String.class);
        String password = config.getValue("es_password", String.class);
        String url = config.getValue("es_url", String.class);
        String host = config.getValueOrDefault("es_host", "localhost");
        int port = config.getValueOrDefault("es_port", 9200);
        String scheme = config.getValueOrDefault("es_scheme", "https");

        String parsedScheme;
        String parsedHost;
        int parsedPort;

        if (url == null || url.isEmpty()) {
            parsedScheme = scheme;
            parsedHost = host;
            parsedPort = port;
        } else {
            // Prepend scheme if missing for URI.create to work correctly
            String urlWithScheme = url.contains("://") ? url : scheme + "://" + url;
            URI uri = URI.create(urlWithScheme);

            parsedScheme = (uri.getScheme() != null) ? uri.getScheme() : scheme;
            parsedHost = uri.getHost();
            // Fallback to the 'port' variable (9200 or config) if URI has no explicit port
            parsedPort = (uri.getPort() != -1) ? uri.getPort() : port;
        }

        this.client = this.createClient(username, password, parsedHost, parsedPort, parsedScheme);
    }

    private ElasticsearchClient createClient(String username, String password, String host, int port, String scheme) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(new HttpHost(host, port, scheme))
                .setHttpClientConfigCallback(
                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();

        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
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
            /**
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

            /**
             * Non-conflict ElasticsearchException, log it and keep going to avoid
             * transaction rollbacks.
             */
            return this.handleException(hash, document, e);
        } catch (Exception e) {
            /**
             * Unknown exception, log it and keep going to avoid transaction rollbacks.
             */
            return this.handleException(hash, document, e);
        }

        return false;
    }

    /**
     * Logs the exception and return always false.
     * 
     * @param string           hash
     * @param AuditLogDocument document
     * @param Exception        e
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
