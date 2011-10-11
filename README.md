Flurfunk - CamelBot
===================

Camelbot is a generic Flurfunk-bot.


Run it like this:

mvn camel:run


Generating a standalone camelbot application/daemon
---------------------------------------------------

mvn package

Zip and copy out target/appassembler/camelbot

Running camelbot
----------------
sh camelbot/bin/camelbot

Configuration
-------------

You can either specify -DcamelProps=path-to-file in JAVA_OPTS before running, or a properties 
file in /etc/camelbot.properties will be assumed.

See src/main/config/camelbot.properties for available properties you can override.
