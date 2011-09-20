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
import org.apache.commons.lang3.StringEscapeUtils;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/**
 * A Camel Router
 */
public class MyRouteBuilder extends RouteBuilder {
    private static final String FLURFUNK_ENDPOINT = "http://127.0.0.1:3000/message";

    /**
     * A main() so we can easily run these routing rules in our IDE
     */
    public static void main(String... args) throws Exception {
        Main.main(args);
    }

    /**
     * Lets configure the Camel routing rules using Java code...
     */
    public void configure() {
        fromIrcRoute();
        fromImapRoute();
    }

    private void fromImapRoute() {
        //TODO: Get a dedicated imap account for this!
        String username = "thomas.nicolaisen@viaboxx.de";
        String password = "XXXXXXX";
        String imapFolder = "camelbot";

        from(String.format("imaps://imap.gmail.com?consumer.delay=5000&username=%s&password=%s&folderName=%s", username, password, imapFolder)).
                process(new MailProcessor()).
                to(FLURFUNK_ENDPOINT);
    }           

    private void fromIrcRoute() {
        String ircChannel = "#viaboxx";
        String messagePrefix = "camelbot";

        from("irc:camelbot@irc.irccloud.com?channels=" + ircChannel).
                choice().
                when(body().startsWith(messagePrefix)).process(new IrcProcessor()).
                to(FLURFUNK_ENDPOINT);
    }

    private static class MailProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            MailMessage message = (MailMessage) exchange.getIn();

            String subject = message.getMessage().getSubject();
            String from = message.getMessage().getFrom()[0].toString();
            String body = (String) message.getBody();

            exchange.getIn().setBody(messageString(from, subject, body, "mail"));
        }

    }

    private static class IrcProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            IrcMessage ircMsg = (IrcMessage) exchange.getIn();
            String message = ircMsg.getMessage();
            String user = ircMsg.getUser().toString();
            String ircChannel = ircMsg.getTarget();
            String ircServer = ircMsg.getUser().getServername();

            String subject = String.format("Chatted on %s", ircChannel);
            //ircMessage starts with 'camelbot: ' - cut away that part
            String body = message.substring("camelbot: ".length(), message.length());

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
                append(subject).
                append(" --- ").
                append(body);
        //TODO: Append urls!

        StringBuilder xmlBuilder = new StringBuilder().
                append("<message author='" + escapeHtml4(from) + " (" + escapeHtml4(channel) + ")'>").
                append(escapeHtml4(messageBuilder.toString())).
                append("</message>");
        return xmlBuilder.toString();
    }
}
