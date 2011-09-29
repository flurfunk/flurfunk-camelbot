/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.viaboxx.flurfunk.bots.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.irc.IrcMessage;
import org.apache.camel.component.mail.MailMessage;
import org.apache.camel.spring.Main;
import org.constretto.ConstrettoBuilder;
import org.constretto.ConstrettoConfiguration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/**
 * A Camel Router
 */
public class MyRouteBuilder extends RouteBuilder {

    /**
     * Launch the app.
     *
     * Camelbot will read a default camelbot.properties from the classpath. If you wish to override these settings,
     * either use staged properties (constretto.org style) - or create a camelbot-overrides.properties file.
     *
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
    }

    private ConstrettoConfiguration configureConstretto() {
        ConstrettoConfiguration config = new ConstrettoBuilder()
                .createPropertiesStore()
                .addResource(new DefaultResourceLoader().getResource("camelbot.properties"))
                .addResource(new DefaultResourceLoader().getResource("camelbot-overrides.properties"))
                .done()
                .getConfiguration();
        return config;
    }

    private void fromImapRoute(ConstrettoConfiguration config) {
        //TODO: Get a dedicated imap account for this!
        String username = config.evaluateToString("imapUserName");
        String password = config.evaluateToString("imapPassword");
        String imapFolder = config.evaluateToString("imapFolder"); //camelbot
        String pollingFreq = config.evaluateToString("imapPollingFrequency");

        from(String.format("imaps://imap.gmail.com?consumer.delay=%s&username=%s&password=%s&folderName=%s", pollingFreq, username, password, imapFolder)).
                process(new MailProcessor()).
                to(config.evaluateToString("flurfunkUrl"));
    }

    private void fromIrcRoute(ConstrettoConfiguration config) {
        String ircServer = config.evaluateToString("ircServer");  //irc.irccloud.com
        String ircChannel =  config.evaluateToString("ircChannel"); //#viaboxx
        String messagePrefix = config.evaluateToString("ircMessagePrefix"); //'camelbot: '

        from(String.format("irc:camelbot@%s?channels=%s", ircServer, ircChannel)).
                choice().
                when(body().startsWith(messagePrefix)).process(new IrcProcessor(messagePrefix)).
                to(config.evaluateToString("flurfunkUrl"));
    }

    private static class MailProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            MailMessage message = (MailMessage) exchange.getIn();

            String from = message.getMessage().getFrom()[0].toString();
            String subject = message.getMessage().getSubject();
//            String body = (String) message.getBody();
            exchange.getIn().setBody(messageString(from, subject, "", "mail"));
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
            String body = message.substring(messagePrefix.length()+1, message.length());

            exchange.getIn().setBody(messageString(user, subject, body, "irc"));
        }
    }

    /**
     * <pre>
     * POST http://flurfunk.viaboxx.de/message
     * Content-Type: application/xml
     * <message author="felix">
     * Hello, World!
     * </message>
     * </pre>
     */
    private static String messageString(String from, String subject, String body, String channel) {

        StringBuilder messageBuilder = new StringBuilder().
                append(escapeHtml4(subject)).
                append("\n").
                append(escapeHtml4(body));
        //TODO: Append urls!

        StringBuilder xmlBuilder = new StringBuilder().
                append("<message author='" + escapeHtml4(from) + " (" + escapeHtml4(channel) + ")'>").
                append(messageBuilder.toString()).
                append("</message>");
        return xmlBuilder.toString();
    }
}
