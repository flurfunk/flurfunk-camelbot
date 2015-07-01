/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.viaboxx.flurfunk.bots.camel;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.irc.IrcMessage;
import org.apache.camel.component.mail.MailMessage;
import org.apache.camel.spring.Main;
import org.constretto.ConstrettoBuilder;
import org.constretto.ConstrettoConfiguration;
import org.constretto.model.Resource;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/**
 * A Camel Router
 */
public class CamelBot extends RouteBuilder {

    private static final String DIRECT_HIPCHAT_PROCESSOR = "direct:hipchatProcessor";

    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();


    /**
     * Launch the app.
     * <p/>
     * Camelbot will read a default camelbot.properties from the classpath. If you wish to override these settings,
     * either use staged properties (constretto.org style) - or create a camelbot-overrides.properties file.
     */
    public static void main(String... args) throws Exception {
        Main.main(args);
    }

    /**
     * Lets configure the Camel routing rules using Java code...
     */
    public void configure() {
        ConstrettoConfiguration config = configureConstretto();
        fromIrcRoute(config);
        fromImapRoute(config);
        toHipChat(config);
    }

    private ConstrettoConfiguration configureConstretto() {

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("META-INF/maven/de.viaboxx.flurfunk/flurfunk-camelbot/pom.properties");
        if (inputStream != null) {
            System.out.println("==========================");
            System.out.println("Flurfunk C A M E L B O T !");
            System.out.println("==========================");
            System.out.println("http://flurfunk.github.com");
            System.out.println("==========================");
            Properties prop = new Properties();
            try {
                prop.load(inputStream);
                String version = prop.getProperty("version");
                System.out.println("Starting flurfunk-camelbot:" + version);
            } catch (IOException e) {
                System.out.println("WARNING: No meta information found in this camelbot distribution. Could be it's broken!");
            }
        }

        String propFile = System.getProperty("camelbotProps");
        if (propFile == null) {
            System.out.println("No camelbotProps system property specified. Will look in /etc/camelbot.properties for configuration.");
            propFile = "file:/etc/camelbot.properties";
        } else propFile = "file:" + propFile;

        ConstrettoConfiguration config = new ConstrettoBuilder()
                .createPropertiesStore()
                .addResource(Resource.create("classpath:camelbot.properties"))
                .addResource(Resource.create("classpath:camelbot-overrides.properties"))
                .addResource(Resource.create(propFile))
                .done()
                .getConfiguration();
        return config;
    }

    private void fromImapRoute(ConstrettoConfiguration config) {
        String username = config.evaluateToString("imapUserName");
        String password = config.evaluateToString("imapPassword");
        String imapFolder = config.evaluateToString("imapFolder"); //camelbot
        String pollingFreq = config.evaluateToString("imapPollingFrequency");

        from(String.format("imaps://imap.gmail.com?consumer.delay=%s&username=%s&password=%s&folderName=%s", pollingFreq, username, password, imapFolder)).
                process(new MailProcessor()).
                to(DIRECT_HIPCHAT_PROCESSOR);
    }


    private void fromIrcRoute(ConstrettoConfiguration config) {
        String ircServer = config.evaluateToString("ircServer");  //irc.irccloud.com
        String ircChannel = config.evaluateToString("ircChannel"); //#flurfunk
        String messagePrefix = config.evaluateToString("ircMessagePrefix"); //'camelbot: '

        from(String.format("irc:camelbot@%s?channels=%s", ircServer, ircChannel)).
                choice().
                when(body().startsWith(messagePrefix)).process(new IrcProcessor(messagePrefix)).
                to(DIRECT_HIPCHAT_PROCESSOR);
    }

    private void toHipChat(ConstrettoConfiguration config) {
        from("direct:hipchatProcessor")
                .process(new HipChatProcessor(config));
    }

    private static class HipChatProcessor implements Processor {
        private final ConstrettoConfiguration config;

        public HipChatProcessor(ConstrettoConfiguration config) {
            this.config = config;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            String message = (String) exchange.getIn().getBody();
            String authToken = config.evaluateToString("hipchatAuthToken");
            String roomId = config.evaluateToString("hipchatRoomId");
            String from = config.evaluateToString("hipchatBotName");
            sendMessage(message, from, "green", roomId, true, authToken);
        }

        /**
         * See https://www.hipchat.com/docs/api/method/rooms/message for possible options
         *
         * @param message
         * @param from
         * @param color
         * @param roomId
         * @param notify
         * @param authToken
         */
        public void sendMessage(String message, String from, String color, String roomId, boolean notify, String authToken) throws IOException {
            String query = String.format("?format=%s&auth_token=%s", "json", authToken);

            StringBuilder params = new StringBuilder();

            Preconditions.checkNotNull(message, "Cannot send null message");
            Preconditions.checkNotNull(from, "Cannot send message without from-field");

            params.append("room_id=");
            params.append(roomId);
            params.append("&from=");
            params.append(URLEncoder.encode(from, "UTF-8"));
            params.append("&message=");
            params.append(URLEncoder.encode(message, "UTF-8"));
            params.append("&format=");
            params.append("html");


            if (notify) {
                params.append("&notify=1");
            }

            if (color != null) {
                params.append("&color=");
                params.append(color);
            }

            final String paramsToSend = params.toString();

            HttpRequestFactory requestFactory =
                    HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                        @Override
                        public void initialize(HttpRequest httpRequest) throws IOException {
                            //Nothing to do here
                        }
                    });
            GenericUrl url = new GenericUrl("https://api.hipchat.com/v1/rooms/message" + query);
            HttpContent content = new ByteArrayContent("application/x-www-form-urlencoded",paramsToSend.getBytes());
            HttpRequest request = requestFactory.buildPostRequest(url, content);
            HttpResponse response = request.execute();
            System.out.println("Sent parameters: " + paramsToSend + " - Received response from hipchat: " + response.getStatusMessage());
            System.out.println("Response content: " + CharStreams.toString(new InputStreamReader(response.getContent())));

        }

    }

    private static class MailProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            MailMessage mailMessage = (MailMessage) exchange.getIn();

            String from = mailMessage.getMessage().getFrom()[0].toString();
            String subject = mailMessage.getMessage().getSubject();
            String body;
            if(mailMessage.getBody() instanceof MimeMultipart) {
                body = Ascii.truncate(mailMessage.getMessage().toString(), 1100, " [... truncated]");
            } else body = mailMessage.getBody().toString();

            List<String> channels = new ArrayList<String>();

            //TODO: Not very portable to have these set of channels.. Make it configurable at some point.
            if (subject.contains("[commits]")) channels.add("commits");
            if (subject.contains("[ci]")) channels.add("ci");
            if (subject.contains("Service Alert")) channels.add("nagios");

            String channelsCommaSeparated = Joiner.on(',').join(channels);
            exchange.getIn().setBody(messageString(from, subject, body, channelsCommaSeparated));
        }


        private boolean textIsHtml = false;

        /**
         * From http://www.oracle.com/technetwork/java/javamail/faq/index.html#mainbody
         * Return the primary text content of the message.
         */
        private String getText(Part p) throws
                MessagingException, IOException {
            if (p.isMimeType("text/*")) {
                String s = (String)p.getContent();
                textIsHtml = p.isMimeType("text/html");
                return s;
            }

            if (p.isMimeType("multipart/alternative")) {
                // prefer html text over plain text
                Multipart mp = (Multipart)p.getContent();
                String text = null;
                for (int i = 0; i < mp.getCount(); i++) {
                    Part bp = mp.getBodyPart(i);
                    if (bp.isMimeType("text/plain")) {
                        if (text == null)
                            text = getText(bp);
                        continue;
                    } else if (bp.isMimeType("text/html")) {
                        String s = getText(bp);
                        if (s != null)
                            return s;
                    } else {
                        return getText(bp);
                    }
                }
                return text;
            } else if (p.isMimeType("multipart/*")) {
                Multipart mp = (Multipart)p.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    String s = getText(mp.getBodyPart(i));
                    if (s != null)
                        return s;
                }
            }

            return null;
        }


    }

    private static class IrcProcessor implements Processor {
        private final String messagePrefix;

        public IrcProcessor(String messagePrefix) {

            this.messagePrefix = messagePrefix;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            IrcMessage ircMsg = (IrcMessage) exchange.getIn();
            String message = ircMsg.getMessage();
            String user = ircMsg.getUser().toString();
            String ircChannel = ircMsg.getTarget();
            String ircServer = ircMsg.getUser().getServername();

            String subject = String.format("Chatted on %s", ircChannel);
            //ircMessage starts with 'camelbot: ' - cut away that part
            String body = message.substring(messagePrefix.length() + 1, message.length());

            exchange.getIn().setBody(messageString(user, subject, body, "irc"));
        }
    }

    /**
     * <pre>
     * POST http://flurfunk/message
     * Content-Type: application/xml
     * <message author="felix">
     * Hello, World!
     * </message>
     * </pre>
     */
    private static String messageString(String from, String subject, String body, String channels) {

        StringBuilder messageBuilder = new StringBuilder().
                append("<b>").
                append(subject).
                append("</b>").
                append("\n").
                append("<br/>").
                append("<pre>").
                append(body).
                append("</pre>");
        return messageBuilder.toString();
    }


}
