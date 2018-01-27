/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.amazonechocontrol.internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.openhab.binding.amazonechocontrol.internal.jsons.JsonBluetoothStates;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonDevices;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonDevices.Device;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonMediaState;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonNotificationRequest;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonNotificationSound;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonNotificationSounds;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonPlayerState;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonPlaylists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link Connection} is responsible for the connection to the amazon server and
 * handling of the commands
 *
 * @author Michael Geramb - Initial contribution
 */
public class Connection {
    private final Logger logger = LoggerFactory.getLogger(Connection.class);

    private java.net.CookieManager m_cookieManager = new java.net.CookieManager();
    private String m_email;
    private String m_password;
    private String m_amazonSite;
    private String m_sessionId;
    private Date m_loginTime;
    private String m_alexaServer;

    public Connection(String email, String password, String amazonSite) {
        m_email = email;
        m_password = password;

        m_amazonSite = amazonSite;
        if (m_amazonSite.toLowerCase().startsWith("http://")) {
            m_amazonSite = m_amazonSite.substring(7);
        }
        if (m_amazonSite.toLowerCase().startsWith("https://")) {
            m_amazonSite = m_amazonSite.substring(8);
        }
        if (m_amazonSite.toLowerCase().startsWith("www.")) {
            m_amazonSite = m_amazonSite.substring(4);
        }
        if (m_amazonSite.toLowerCase().startsWith("alexa.")) {
            m_amazonSite = m_amazonSite.substring(6);
        }
        m_alexaServer = "https://alexa." + amazonSite;

    }

    public String getEmail() {
        return m_email;
    }

    public String getPassword() {
        return m_password;
    }

    public String getAmazonSite() {
        return m_amazonSite;
    }

    public String serializeLoginData() {
        if (m_sessionId == null || m_loginTime == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("4\n"); // version
        builder.append(m_email);
        builder.append("\n");
        builder.append(m_password.hashCode());
        builder.append("\n");
        builder.append(m_sessionId);
        builder.append("\n");
        builder.append(m_loginTime.getTime());
        builder.append("\n");
        List<HttpCookie> cookies = m_cookieManager.getCookieStore().getCookies();
        builder.append(cookies.size());
        builder.append("\n");
        for (HttpCookie cookie : cookies) {

            writeValue(builder, cookie.getName());
            writeValue(builder, cookie.getValue());
            writeValue(builder, cookie.getComment());
            writeValue(builder, cookie.getCommentURL());
            writeValue(builder, cookie.getDomain());
            writeValue(builder, cookie.getMaxAge());
            writeValue(builder, cookie.getPath());
            writeValue(builder, cookie.getPortlist());
            writeValue(builder, cookie.getVersion());
            writeValue(builder, cookie.getSecure());
            writeValue(builder, cookie.getDiscard());

        }
        return builder.toString();
    }

    private void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append('0');
        } else {
            builder.append('1');
            builder.append("\n");
            builder.append(value.toString());
        }
        builder.append("\n");
    }

    private String readValue(Scanner scanner) {
        if (scanner.nextLine().equals("1")) {
            return scanner.nextLine();
        }
        return null;
    }

    public Boolean tryRestoreLogin(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        Scanner scanner = new Scanner(data);
        String version = scanner.nextLine();
        if (!version.equals("4")) {
            scanner.close();
            return false;
        }

        String email = scanner.nextLine();
        if (!email.equals(this.m_email)) {
            scanner.close();
            return false;
        }

        int passwordHash = Integer.parseInt(scanner.nextLine());
        if (passwordHash != this.m_password.hashCode()) {
            scanner.close();
            return false;
        }
        m_sessionId = scanner.nextLine();
        Date loginTime = new Date(Long.parseLong(scanner.nextLine()));

        CookieStore cookieStore = m_cookieManager.getCookieStore();

        cookieStore.removeAll();

        Integer numberOfCookies = Integer.parseInt(scanner.nextLine());
        for (Integer i = 0; i < numberOfCookies; i++) {
            String name = readValue(scanner);

            String value = readValue(scanner);

            HttpCookie clientCookie = new HttpCookie(name, value);

            clientCookie.setComment(readValue(scanner));

            clientCookie.setCommentURL(readValue(scanner));

            clientCookie.setDomain(readValue(scanner));

            clientCookie.setMaxAge(Long.parseLong(readValue(scanner)));

            clientCookie.setPath(readValue(scanner));

            clientCookie.setPortlist(readValue(scanner));

            clientCookie.setVersion(Integer.parseInt(readValue(scanner)));

            clientCookie.setSecure(Boolean.parseBoolean(readValue(scanner)));

            clientCookie.setDiscard(Boolean.parseBoolean(readValue(scanner)));
            cookieStore.add(null, clientCookie);
        }

        scanner.close();
        try {
            if (verifyLogin()) {
                m_loginTime = loginTime;
                return true;

            }
        } catch (Exception e) {
        }
        cookieStore.removeAll();
        m_sessionId = null;
        m_loginTime = null;
        return false;

    }

    public Date tryGetLoginTime() {
        return m_loginTime;
    }

    private HttpsURLConnection makeRequest(String verb, String url, String referer, String postData, Boolean json)
            throws Exception {
        String currentUrl = url;
        for (int i = 0; i < 30; i++) // loop for handling redirect, using automatic redirect is not possible, because
                                     // all response headers must be catched
        {
            int code;
            HttpsURLConnection connection;
            try {

                logger.debug("Make request to {}", url);
                connection = (HttpsURLConnection) new URL(currentUrl).openConnection();
                connection.setRequestMethod(verb);
                connection.setRequestProperty("Accept-Language", "en-US");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("DNT", "1");
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
                connection.setInstanceFollowRedirects(false);
                if (referer != null) {
                    connection.setRequestProperty("Referer", referer);
                }

                // add cookies
                URI uri = connection.getURL().toURI();

                StringBuilder cookieHeaderBuilder = new StringBuilder();
                for (HttpCookie cookie : m_cookieManager.getCookieStore().get(uri)) {
                    if (cookieHeaderBuilder.length() > 0) {
                        cookieHeaderBuilder.insert(0, "; ");
                    }
                    cookieHeaderBuilder.insert(0, cookie);
                    if (cookie.getName().equals("csrf")) {
                        connection.setRequestProperty("csrf", cookie.getValue());
                    }

                }
                if (cookieHeaderBuilder.length() > 0) {
                    connection.setRequestProperty("Cookie", cookieHeaderBuilder.toString());
                }
                if (postData != null) {

                    // post data
                    byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
                    int postDataLength = postDataBytes.length;

                    connection.setFixedLengthStreamingMode(postDataLength);

                    if (json) {
                        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    } else {
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    }
                    connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                    if (verb == "POST") {
                        connection.setRequestProperty("Expect", "100-continue");
                    }

                    connection.setDoOutput(true);
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(postDataBytes);
                    outputStream.close();
                }
                // handle result
                code = connection.getResponseCode();
                String location = null;

                // handle response headers
                Map<String, List<String>> headerFields = connection.getHeaderFields();
                for (Map.Entry<String, List<String>> header : headerFields.entrySet()) {
                    String key = header.getKey();
                    if (key != null) {
                        if (key.equalsIgnoreCase("Set-Cookie")) {
                            // store cookie
                            for (String cookieHeader : header.getValue()) {
                                List<HttpCookie> cookies = HttpCookie.parse(cookieHeader);
                                for (HttpCookie cookie : cookies) {
                                    m_cookieManager.getCookieStore().add(uri, cookie);
                                }
                            }
                        }
                        if (key.equalsIgnoreCase("Location")) {
                            // get redirect location
                            location = header.getValue().get(0);
                            if (location != null) {
                                if (code == 302) {
                                    logger.debug("Redirected to {}", location);
                                }
                                // check for https
                                if (location.toLowerCase().startsWith("http://")) {
                                    // always use https
                                    location = "https://" + location.substring(7);
                                    logger.debug("Redirect corrected to {}", location);
                                }
                            }
                        }
                    }
                }
                if (code == 200) {
                    logger.debug("Call to {} succeeded", url);
                    return connection;
                }
                if (code == 302 && location != null) {
                    currentUrl = location;
                    continue;
                }
            } catch (Exception e) {
                logger.warn("Request to url '{}' fails with unkown error: {}", url, e);
                throw e;
            }
            if (code != 200) {
                throw new HttpException(code, connection.getResponseMessage());
            }
        }
        throw new ConnectionException("To many redirects");
    }

    public boolean getIsLoggedIn() {
        return m_loginTime != null;
    }

    public void makeLogin() throws Exception {
        try {
            // clear session data
            m_cookieManager.getCookieStore().removeAll();
            m_sessionId = null;
            m_loginTime = null;

            logger.debug("Start Login to {}", m_alexaServer);
            // get login form
            String loginFormHtml = makeRequestAndReturnString(m_alexaServer);

            logger.debug("Received login form {}", loginFormHtml);

            // get session id from cookies
            for (HttpCookie cookie : m_cookieManager.getCookieStore().getCookies()) {
                if (cookie.getName().equalsIgnoreCase("session-id")) {
                    m_sessionId = cookie.getValue();
                    break;
                }
            }
            if (m_sessionId == null) {
                throw new ConnectionException("No session id received");
            }

            // read hidden form inputs, the will be used later in the url and for posting
            Pattern inputPattern = Pattern
                    .compile("<input\\s+type=\"hidden\"\\s+name=\"(?<name>[^\"]+)\"\\s+value=\"(?<value>[^\"]*)\"");
            Matcher matcher = inputPattern.matcher(loginFormHtml);

            StringBuilder postDataBuilder = new StringBuilder();
            while (matcher.find()) {

                postDataBuilder.append(URLEncoder.encode(matcher.group("name"), "UTF-8"));
                postDataBuilder.append('=');
                postDataBuilder.append(URLEncoder.encode(matcher.group("value"), "UTF-8"));
                postDataBuilder.append('&');
            }

            String queryParameters = postDataBuilder.toString() + "session-id="
                    + URLEncoder.encode(m_sessionId, "UTF-8");

            logger.debug("Login query String: {}", queryParameters);

            postDataBuilder.append("email");
            postDataBuilder.append('=');
            postDataBuilder.append(URLEncoder.encode(m_email, "UTF-8"));
            postDataBuilder.append('&');
            postDataBuilder.append("password");
            postDataBuilder.append('=');
            postDataBuilder.append(URLEncoder.encode(m_password, "UTF-8"));

            String postData = postDataBuilder.toString();

            // post login data

            String referer = "https://www." + m_amazonSite + "/ap/signin?" + queryParameters;
            m_cookieManager.getCookieStore().add(new URL("https://www." + m_amazonSite).toURI(),
                    HttpCookie.parse("session-id=" + m_sessionId).get(0));
            String response = makeRequestAndReturnString("POST", "https://www." + m_amazonSite + "/ap/signin", referer,
                    postData, false);
            if (response.contains("<title>Amazon Alexa</title>")) {
                logger.debug("Response seems to be alexa app");
            } else {
                logger.info("Response maybe not valid");
            }

            logger.debug("Received content after login {}", response);

            // get CSRF
            // makeRequest(m_alexaServer + "/api/language", m_alexaServer + "/spa/index.html", null, false);

            // verify login
            if (!verifyLogin()) {
                throw new ConnectionException("Login fails.");
            }
            m_loginTime = new Date();
            logger.debug("Login succeeded");
        } catch (Exception e) {
            // clear session data
            m_cookieManager.getCookieStore().removeAll();
            m_sessionId = null;
            m_loginTime = null;
            logger.info("Login failed:{} ", e);
            throw e;
        }

    }

    private String makeRequestAndReturnString(String url) throws Exception {
        return makeRequestAndReturnString("GET", url, null, null, false);
    }

    private String makeRequestAndReturnString(String verb, String url, String referer, String postData, Boolean json)
            throws Exception {
        HttpsURLConnection connection = makeRequest(verb, url, referer, postData, json);
        return getResponse(connection);
    }

    public boolean verifyLogin() throws Exception {
        String response = makeRequestAndReturnString(m_alexaServer + "/api/bootstrap?version=0");
        Boolean result = response.contains("\"authenticated\":true");
        return result;
    }

    private String getResponse(HttpsURLConnection request) throws Exception {
        InputStream input = request.getInputStream();
        Scanner inputScanner = new Scanner(input);
        Scanner scannerWithoutDelimiter = inputScanner.useDelimiter("\\A");
        String result = scannerWithoutDelimiter.hasNext() ? scannerWithoutDelimiter.next() : "";
        inputScanner.close();
        scannerWithoutDelimiter.close();
        input.close();
        return result;
    }

    public void logout() {
        m_cookieManager.getCookieStore().removeAll();
        m_sessionId = null;
        m_loginTime = null;
    }

    // parser
    private <T> T parseJson(String json, Class<T> type) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            logger.warn("Parsing json failed {}", e);
            logger.warn("{}", json);
            throw e;
        }
    }

    // commands and states

    public Device[] getDeviceList() throws Exception {
        String json = makeRequestAndReturnString(m_alexaServer + "/api/devices-v2/device?cached=false");
        JsonDevices devices = parseJson(json, JsonDevices.class);
        return devices.devices;
    }

    public JsonPlayerState getPlayer(Device device) throws Exception {
        String json = makeRequestAndReturnString(m_alexaServer + "/api/np/player?deviceSerialNumber="
                + device.serialNumber + "&deviceType=" + device.deviceType + "&screenWidth=1440");
        JsonPlayerState playerState = parseJson(json, JsonPlayerState.class);
        return playerState;
    }

    public JsonMediaState getMediaState(Device device) throws Exception {
        String json = makeRequestAndReturnString(m_alexaServer + "/api/media/state?deviceSerialNumber="
                + device.serialNumber + "&deviceType=" + device.deviceType);
        JsonMediaState mediaState = parseJson(json, JsonMediaState.class);
        return mediaState;
    }

    public JsonBluetoothStates getBluetoothConnectionStates() throws Exception {
        String json = makeRequestAndReturnString(m_alexaServer + "/api/bluetooth?cached=true");
        JsonBluetoothStates bluetoothStates = parseJson(json, JsonBluetoothStates.class);
        return bluetoothStates;
    }

    public JsonPlaylists getPlaylists(Device device) throws Exception {
        String json = makeRequestAndReturnString(
                m_alexaServer + "/api/cloudplayer/playlists?deviceSerialNumber=" + device.serialNumber + "&deviceType="
                        + device.deviceType + "&mediaOwnerCustomerId=" + device.deviceOwnerCustomerId);
        JsonPlaylists playlists = parseJson(json, JsonPlaylists.class);
        return playlists;
    }

    public void command(Device device, String command) throws Exception {
        String url = m_alexaServer + "/api/np/command?deviceSerialNumber=" + device.serialNumber + "&deviceType="
                + device.deviceType;
        makeRequest("POST", url, null, command, true);

    }

    public void bluetooth(Device device, String address) throws Exception {
        if (address == null || address.isEmpty()) {
            // disconnect
            makeRequest("POST",
                    m_alexaServer + "/api/bluetooth/disconnect-sink/" + device.deviceType + "/" + device.serialNumber,
                    null, "", true);
        } else {
            makeRequest("POST",
                    m_alexaServer + "/api/bluetooth/pair-sink/" + device.deviceType + "/" + device.serialNumber, null,
                    "{\"bluetoothDeviceAddress\":\"" + address + "\"}", true);

        }
    }

    public void playRadio(Device device, String stationId) throws Exception {
        if (stationId == null || stationId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            makeRequest("POST",
                    m_alexaServer + "/api/tunein/queue-and-play?deviceSerialNumber=" + device.serialNumber
                            + "&deviceType=" + device.deviceType + "&guideId=" + stationId
                            + "&contentType=station&callSign=&mediaOwnerCustomerId=" + device.deviceOwnerCustomerId,
                    null, "", true);
        }
    }

    public void playAmazonMusicTrack(Device device, String trackId) throws Exception {
        if (trackId == null || trackId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            String command = "{\"trackId\":\"" + trackId + "\",\"playQueuePrime\":true}";
            makeRequest("POST",
                    m_alexaServer + "/api/cloudplayer/queue-and-play?deviceSerialNumber=" + device.serialNumber
                            + "&deviceType=" + device.deviceType + "&mediaOwnerCustomerId="
                            + device.deviceOwnerCustomerId + "&shuffle=false",
                    null, command, true);
        }
    }

    public void playAmazonMusicPlayList(Device device, String playListId) throws Exception {
        if (playListId == null || playListId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            String command = "{\"playlistId\":\"" + playListId + "\",\"playQueuePrime\":true}";
            makeRequest("POST",
                    m_alexaServer + "/api/cloudplayer/queue-and-play?deviceSerialNumber=" + device.serialNumber
                            + "&deviceType=" + device.deviceType + "&mediaOwnerCustomerId="
                            + device.deviceOwnerCustomerId + "&shuffle=false",
                    null, command, true);
        }
    }

    public JsonNotificationSound[] getNotificationSounds(Device device) throws Exception {
        String json = makeRequestAndReturnString(
                "GET", m_alexaServer + "/api/notification/sounds?deviceSerialNumber=" + device.serialNumber
                        + "&deviceType=" + device.deviceType + "&softwareVersion=" + device.softwareVersion,
                null, null, true);
        JsonNotificationSounds result = parseJson(json, JsonNotificationSounds.class);
        if (result.notificationSounds != null) {
            return result.notificationSounds;
        }
        return new JsonNotificationSound[0];
    }

    public void notification(Device device, String type, String label, JsonNotificationSound sound) throws Exception {

        Date date = new Date(new Date().getTime());
        long createdDate = date.getTime();
        Date alarm = new Date(createdDate + 5000); // add 5 seconds, because amazon does not except calls for times in
                                                   // the past (compared with the server time)
        long alarmTime = alarm.getTime();

        JsonNotificationRequest request = new JsonNotificationRequest();
        request.type = type;
        request.deviceSerialNumber = device.serialNumber;
        request.deviceType = device.deviceType;
        request.createdDate = createdDate;
        request.alarmTime = alarmTime;
        request.reminderLabel = label;
        request.sound = sound;
        request.originalDate = new SimpleDateFormat("yyyy-MM-dd").format(alarm);
        request.originalTime = new SimpleDateFormat("HH:mm:ss.SSSS").format(alarm);
        request.type = type;
        request.id = "create" + type;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.serializeNulls();
        Gson gson = gsonBuilder.create();

        String data = gson.toJson(request);
        makeRequestAndReturnString("PUT", m_alexaServer + "/api/notifications/createReminder", null, data, true);

    }

}