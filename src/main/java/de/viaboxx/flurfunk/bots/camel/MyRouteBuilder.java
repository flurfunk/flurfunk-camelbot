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
import org.apache.camel.spring.Main;

/**
 * A Camel Router
 */
public class MyRouteBuilder extends RouteBuilder {

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

        Processor postMessage = new PostIrcMessage();
        from("irc:camelbot@irc.irccloud.com?channels=#viaboxx").
                choice().
                when(body().startsWith("camelbot")).process(postMessage).
                to("http://127.0.0.1:3000/message");
    }

    private static class PostIrcMessage implements Processor {

        /**
         * <pre>
         * POST http://flurfunk.viaboxx.de/message
         * Content-Type: application/xml
         * <message author="felix">
         * Hello, World!
         * </message>
         * </pre>
         *
         * @param exchange
         * @throws Exception
         */
        @Override
        public void process(Exchange exchange) throws Exception {
            IrcMessage ircMsg = (IrcMessage) exchange.getIn();
            String message = ircMsg.getMessage();
            String user = ircMsg.getUser().toString();
            StringBuilder xmlBuilder = new StringBuilder();
            xmlBuilder.append("<message author='camelbot (irc)'>" + user + ": " + message.substring(10, message.length()) + "</message>");
            exchange.getIn().setBody(xmlBuilder.toString());
        }
    }
}
