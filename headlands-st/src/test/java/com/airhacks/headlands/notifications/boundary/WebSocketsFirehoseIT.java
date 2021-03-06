package com.airhacks.headlands.notifications.boundary;

import com.airhacks.headlands.cache.boundary.CachesResourceIT;
import com.airhacks.headlands.cache.boundary.EntriesResourceIT;
import static com.airhacks.headlands.cache.boundary.EntriesResourceIT.createEntryOrUpdate;
import static com.airhacks.rulz.jaxrsclient.HttpMatchers.successful;
import com.airhacks.rulz.jaxrsclient.JAXRSClientProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import javax.ws.rs.core.Response;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author airhacks.com
 */
public class WebSocketsFirehoseIT {

    private NotificationsReceiver messagesEndpoint;

    @Rule
    public JAXRSClientProvider tut = JAXRSClientProvider.buildWithURI("http://localhost:8080/headlands/resources/caches");
    private String cacheName;

    final static long EXPIRY = 4242;
    private WebSocketContainer containerProvider;

    @Before
    public void init() {
        this.containerProvider = ContainerProvider.getWebSocketContainer();
        this.messagesEndpoint = new NotificationsReceiver();
        this.cacheName = "cache-" + System.currentTimeMillis();
        Response response = CachesResourceIT.createCacheWithExpiration(tut.target(), EXPIRY, cacheName);
        assertThat(response.getStatusInfo().getFamily(), is(Response.Status.Family.SUCCESSFUL));
    }

    @Test
    public void creationEventsAreDeliveredViaWildCardChannel() throws DeploymentException, IOException, URISyntaxException {
        this.creationEventsAreDeliveredVia("*");
    }

    @Test
    public void creationEventsAreDeliveredViaDedicatedChannel() throws DeploymentException, IOException, URISyntaxException {
        this.creationEventsAreDeliveredVia(this.cacheName);
    }

    void creationEventsAreDeliveredVia(String channel) throws DeploymentException, IOException, URISyntaxException {
        containerProvider.connectToServer(this.messagesEndpoint, new URI("ws://localhost:8080/headlands/firehose/" + channel));
        String expectedValue = "java rocks " + System.currentTimeMillis();
        String expectedKey = "status" + System.currentTimeMillis();
        Response response = createEntryOrUpdate(this.tut.target(), this.cacheName, expectedKey, expectedValue);
        assertThat(response, successful());
        String message = this.messagesEndpoint.getMessage();
        assertNotNull(message);
        JsonReader reader = Json.createReader(new StringReader(message));
        JsonArray eventsArray = reader.readArray();
        assertThat(eventsArray.size(), is(1));
        JsonObject event = eventsArray.getJsonObject(0);
        String actualCacheName = event.getString("cacheName");
        assertThat(actualCacheName, is(this.cacheName));

        String eventType = event.getString("eventType");
        assertThat(eventType, is("CREATED"));

        String actualKey = event.getString("key");
        assertThat(actualKey, is(expectedKey));

        JsonObject changeSet = event.getJsonObject(actualKey);
        String actualValue = changeSet.getString("newValue");
        assertThat(actualValue, is(expectedValue));
    }

    void updateEventsAreDeliveredVia(String channel) throws DeploymentException, IOException, URISyntaxException {
        String expectedValue = "java rocks " + System.currentTimeMillis();
        String expectedKey = "status" + System.currentTimeMillis();
        //initial creation
        createEntryOrUpdate(this.tut.target(), this.cacheName, expectedKey, expectedValue);
        //update
        String expectedUpdatedValue = expectedValue + "-UPDATED";
        containerProvider.connectToServer(this.messagesEndpoint, new URI("ws://localhost:8080/headlands/firehose/" + channel));
        Response response = createEntryOrUpdate(this.tut.target(), this.cacheName, expectedKey, expectedUpdatedValue);
        assertThat(response, successful());
        String message = this.messagesEndpoint.getMessage();
        assertNotNull(message);
        JsonReader reader = Json.createReader(new StringReader(message));
        JsonArray eventsArray = reader.readArray();
        assertThat(eventsArray.size(), is(1));
        JsonObject event = eventsArray.getJsonObject(0);
        System.out.println("event = " + event);
        String actualCacheName = event.getString("cacheName");
        assertThat(actualCacheName, is(this.cacheName));

        String eventType = event.getString("eventType");
        assertThat(eventType, is("UPDATED"));

        String actualKey = event.getString("key");
        assertThat(actualKey, is(expectedKey));

        JsonObject changeSet = event.getJsonObject(actualKey);
        String actualValue = changeSet.getString("newValue");
        assertThat(actualValue, is(expectedUpdatedValue));
    }

    void removalEventsAreDeliveredVia(String channel) throws DeploymentException, IOException, URISyntaxException {
        String expectedValue = "java rocks " + System.currentTimeMillis();
        String expectedKey = "status" + System.currentTimeMillis();
        Response response = createEntryOrUpdate(this.tut.target(), this.cacheName, expectedKey, expectedValue);
        assertThat(response, successful());
        containerProvider.connectToServer(this.messagesEndpoint, new URI("ws://localhost:8080/headlands/firehose/" + channel));
        EntriesResourceIT.removeEntry(this.tut.target(), this.cacheName, expectedKey);
        String message = this.messagesEndpoint.getMessage();
        assertNotNull(message);
        JsonReader reader = Json.createReader(new StringReader(message));
        JsonArray eventsArray = reader.readArray();
        assertThat(eventsArray.size(), is(1));
        JsonObject event = eventsArray.getJsonObject(0);

        System.out.println("event = " + event);

        String actualCacheName = event.getString("cacheName");
        assertThat(actualCacheName, is(this.cacheName));

        String eventType = event.getString("eventType");
        assertThat(eventType, is("REMOVED"));

        String actualKey = event.getString("key");
        assertThat(actualKey, is(expectedKey));

        JsonObject changeSet = event.getJsonObject(actualKey);
        String actualValue = changeSet.getString("newValue");
        assertThat(actualValue, is(expectedValue));
    }

    @Test
    public void updateEventsAreDeliveredViaWildcard() throws DeploymentException, IOException, URISyntaxException {
        this.updateEventsAreDeliveredVia("*");
    }

    @Test
    public void updateEventsAreDeliveredViaDedicatedChannel() throws DeploymentException, IOException, URISyntaxException {
        this.updateEventsAreDeliveredVia(this.cacheName);
    }

    @Test
    public void removalEventsAreDeliveredViaWildcard() throws DeploymentException, IOException, URISyntaxException {
        this.removalEventsAreDeliveredVia("*");
    }

    @Test
    public void removalEventsAreDeliveredViaDedicatedChannel() throws DeploymentException, IOException, URISyntaxException {
        this.removalEventsAreDeliveredVia(this.cacheName);
    }

}
